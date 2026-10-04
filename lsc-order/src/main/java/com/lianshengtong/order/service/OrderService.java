package com.lianshengtong.order.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.common.enums.LscEventType;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.lsc.*;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.ledger.dto.*;
import com.lianshengtong.ledger.entity.LscConsumptionAllocation;
import com.lianshengtong.ledger.service.LscLedgerService;
import com.lianshengtong.order.dto.*;
import com.lianshengtong.order.entity.*;
import com.lianshengtong.order.mapper.*;
import com.lianshengtong.product.entity.ProductPriceVersion;
import com.lianshengtong.product.mapper.ProductPriceVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单服务（V7.7.2 第三、四、七章）
 * <p>
 * 核心流程：报价 -> 下单(占用) -> 支付回调(核销+赠送) -> 退款(返还+撤回)
 * 分摊按商品单元，优惠先按金额比例向下取整，剩余分用最大余数法。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderUnitAllocationMapper allocMapper;
    private final RefundOrderMapper refundMapper;
    private final ProductPriceVersionMapper priceMapper;
    private final LscLedgerService ledgerService;
    private final com.lianshengtong.coupon.service.ReferralRewardService referralService;
    private final com.lianshengtong.user.mapper.UserMapper userMapper;

    /**
     * 1. 创建报价：计算商品金额、抵扣上限、分摊、赠送预估。
     */
    public QuoteResult createQuote(QuoteRequest req) {
        long goodsCent = 0;
        List<QuoteResult.QuoteItemResult> items = new ArrayList<>();
        long deductionPpm = req.getDeductionPpm() != null ? req.getDeductionPpm()
                : LscUnitConstants.DEDUCTION_DEFAULT_PPM;

        for (QuoteRequest.SkuItem si : req.getSkuItems()) {
            ProductPriceVersion pv = priceMapper.selectLatestEffective(si.getSkuId());
            if (pv == null) throw new BizException(ResultCode.NOT_FOUND, "SKU价格不存在");
            long priceCent = "B".equals(req.getBuyerType()) ? pv.getBPriceCent() : pv.getRetailPriceCent();
            long lineGoods = priceCent * si.getQty();
            goodsCent += lineGoods;

            // 逐单元计算赠送预估（全额人民币基准）
            long grantUnitPerPiece = GrantCalculator.calcFullRmbGrant(
                    priceCent, decryptCost(pv.getCostPriceEnc()), pv.getGrantCoefficientPpm());

            items.add(QuoteResult.QuoteItemResult.builder()
                    .skuId(si.getSkuId())
                    .qty(si.getQty())
                    .unitPriceCent(priceCent)
                    .lineGoodsCent(lineGoods)
                    .grantCoefficientPpm(pv.getGrantCoefficientPpm())
                    .costCent(decryptCost(pv.getCostPriceEnc()))
                    .grantUnitPerPiece(grantUnitPerPiece)
                    .build());
        }

        // 最大抵扣
        long maxDeductionCent = GrantCalculator.calcMaxDeductionCent(goodsCent, deductionPpm);
        long maxDeductionUnit = LscMathUtil.centToUnit(maxDeductionCent);

        return QuoteResult.builder()
                .quoteId(IdUtil.fastSimpleUUID())
                .goodsCent(goodsCent)
                .shippingCent(req.getShippingCent() != null ? req.getShippingCent() : 0L)
                .maxDeductionUnit(maxDeductionUnit)
                .maxDeductionCent(maxDeductionCent)
                .items(items)
                .expiresAt(LocalDateTime.now().plusMinutes(LscUnitConstants.QUOTE_TTL_MINUTES))
                .build();
    }

    /**
     * 2. 创建订单：快照价格、分摊优惠、占用LSC或券。
     */
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(CreateOrderRequest req) {
        // 校验券与LSC互斥
        boolean useLsc = req.getLscUnit() != null && req.getLscUnit() > 0;
        boolean useCoupon = req.getCouponId() != null;
        if (useLsc && useCoupon) throw new BizException(ResultCode.COUPON_LSC_MUTEX);
        if (useLsc && !LscMathUtil.isUnitMultipleOf100(req.getLscUnit())) {
            throw new BizException(ResultCode.UNIT_NOT_MULTIPLE_OF_100);
        }

        // 重新计算金额（不信任客户端）
        long goodsCent = 0;
        List<OrderItem> orderItems = new ArrayList<>();
        List<OrderUnitAllocation> allocs = new ArrayList<>();
        int itemSeq = 0;

        for (CreateOrderRequest.SkuItem si : req.getSkuItems()) {
            ProductPriceVersion pv = priceMapper.selectLatestEffective(si.getSkuId());
            long priceCent = "B".equals(req.getBuyerType()) ? pv.getBPriceCent() : pv.getRetailPriceCent();
            long lineGoods = priceCent * si.getQty();
            goodsCent += lineGoods;

            OrderItem item = new OrderItem();
            item.setItemId(IdUtil.getSnowflakeNextId());
            item.setSkuId(si.getSkuId());
            item.setItemSeq(itemSeq++);
            item.setQty(si.getQty());
            item.setPriceVersion(pv.getPriceVersion());
            item.setUnitPriceCent(priceCent);
            item.setLineGoodsCent(lineGoods);
            item.setGrantCoefficientPpm(pv.getGrantCoefficientPpm());
            item.setCostSnapshotEnc(pv.getCostPriceEnc());
            item.setGrantedUnit(0L);
            item.setClawbackRequiredUnit(0L);
            item.setClawbackCompletedUnit(0L);
            item.setClawbackPendingUnit(0L);
            item.setVersion(0);
            orderItems.add(item);

            // 按单元分摊
            for (int u = 0; u < si.getQty(); u++) {
                OrderUnitAllocation alloc = new OrderUnitAllocation();
                alloc.setAllocationId(IdUtil.getSnowflakeNextId());
                alloc.setItemId(item.getItemId());
                alloc.setUnitIndex(u);
                alloc.setSaleCent(priceCent);
                alloc.setCouponCent(0L);
                alloc.setLscUnit(0L);
                alloc.setRmbCent(priceCent);
                alloc.setGrantUnit(0L);
                alloc.setRefundedRmbCent(0L);
                alloc.setRefundedLscUnit(0L);
                alloc.setClawbackTargetUnit(0L);
                alloc.setReturnStatus("NONE");
                alloc.setVersion(0);
                allocs.add(alloc);
            }
        }

        // 优惠分摊（按商品金额比例向下取整，剩余分最大余数法）
        long discountCent = useCoupon ? req.getCouponCent() : 0L;
        long lscCent = useLsc ? req.getLscUnit() / 100 : 0L;
        allocateDiscount(allocs, goodsCent, discountCent, lscCent);

        // 计算每单元赠送
        for (OrderUnitAllocation alloc : allocs) {
            OrderItem item = orderItems.stream()
                    .filter(i -> i.getItemId().equals(alloc.getItemId())).findFirst().orElse(null);
            if (item == null) continue;
            long grant = GrantCalculator.calcHybridGrant(
                    alloc.getSaleCent(), decryptCost(item.getCostSnapshotEnc()),
                    alloc.getRmbCent(), item.getGrantCoefficientPpm());
            alloc.setGrantUnit(grant);
            item.setGrantedUnit(item.getGrantedUnit() + grant);
        }

        // 汇总行
        for (OrderItem item : orderItems) {
            long lineLsc = allocs.stream().filter(a -> a.getItemId().equals(item.getItemId()))
                    .mapToLong(OrderUnitAllocation::getLscUnit).sum();
            long lineCoupon = allocs.stream().filter(a -> a.getItemId().equals(item.getItemId()))
                    .mapToLong(OrderUnitAllocation::getCouponCent).sum();
            long lineRmb = allocs.stream().filter(a -> a.getItemId().equals(item.getItemId()))
                    .mapToLong(OrderUnitAllocation::getRmbCent).sum();
            item.setLscShareUnit(lineLsc);
            item.setCouponShareCent(lineCoupon);
            item.setRmbShareCent(lineRmb);
        }

        long totalLscUnit = allocs.stream().mapToLong(OrderUnitAllocation::getLscUnit).sum();
        long totalCouponCent = allocs.stream().mapToLong(OrderUnitAllocation::getCouponCent).sum();
        long shippingCent = req.getShippingCent() != null ? req.getShippingCent() : 0L;
        long rmbCent = goodsCent + shippingCent - totalCouponCent - totalLscUnit / 100;

        // 创建订单
        Order order = new Order();
        order.setOrderId(IdUtil.getSnowflakeNextId());
        order.setOrderNo("ORD" + System.currentTimeMillis());
        order.setUserId(req.getUserId());
        order.setBuyerTypeSnapshot(req.getBuyerType());
        order.setSellerEntityId(req.getSellerEntityId());
        order.setPayeeEntityId(req.getSellerEntityId());
        order.setInvoiceEntityId(req.getSellerEntityId());
        order.setBenefitObligorEntityId(req.getSellerEntityId());
        order.setDiscountMode(useLsc ? "LSC" : (useCoupon ? "COUPON" : "NONE"));
        order.setGoodsCent(goodsCent);
        order.setShippingCent(shippingCent);
        order.setCouponCent(totalCouponCent);
        order.setLscUnit(totalLscUnit);
        order.setRmbCent(rmbCent);
        order.setPaymentStatus("UNPAID");
        order.setFulfillmentStatus("CREATED");
        order.setRefundStatus("NONE");
        order.setExpiresAt(LocalDateTime.now().plusMinutes(LscUnitConstants.PAYMENT_TTL_MINUTES));
        order.setVersion(0);
        orderMapper.insert(order);

        for (OrderItem item : orderItems) {
            item.setOrderId(order.getOrderId());
            itemMapper.insert(item);
        }
        for (OrderUnitAllocation alloc : allocs) {
            allocMapper.insert(alloc);
        }

        // 占用 LSC
        if (useLsc && totalLscUnit > 0) {
            ReserveCommand rc = new ReserveCommand();
            rc.setUserId(req.getUserId());
            rc.setOrderId(order.getOrderId());
            rc.setReserveUnit(totalLscUnit);
            rc.setBusinessKey("RESERVE_" + order.getOrderId());
            rc.setExpiresAt(order.getExpiresAt());
            ledgerService.reserveForPayment(rc);
        }

        return order;
    }

    /**
     * 3. 支付成功回调：核销LSC占用 + 赠送LSC。
     */
    @Transactional(rollbackFor = Exception.class)
    public void onPaymentSuccess(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) return;
        if ("PAID".equals(order.getPaymentStatus())) return;

        // 核销 LSC 占用
        if (order.getLscUnit() != null && order.getLscUnit() > 0) {
            ledgerService.capturePayment(orderId, "CAPTURE_" + orderId);
        }

        order.setPaymentStatus("PAID");
        orderMapper.updateById(order);
    }

    /**
     * 4. 订单完成：发放赠送LSC（建立 GrantLot）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !"PAID".equals(order.getPaymentStatus())) return;
        if ("COMPLETED".equals(order.getFulfillmentStatus())) return;

        List<OrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        for (OrderItem item : items) {
            if (item.getGrantedUnit() != null && item.getGrantedUnit() > 0) {
                // 按行建立一个 GrantLot
                GrantCommand gc = new GrantCommand();
                gc.setUserId(order.getUserId());
                gc.setSourceItemId(item.getItemId());
                gc.setGrantUnit(item.getGrantedUnit());
                gc.setBusinessKey("GRANT_" + item.getItemId());
                gc.setRuleVersion("7.7.2");
                var event = ledgerService.grantLsc(gc);
                // 回写 grant_lot_id（需查询刚创建的lot，简化处理）
            }
        }

        order.setFulfillmentStatus("COMPLETED");
        order.setCompletedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // 触发推荐奖励（首单）
        triggerReferralRewardIfFirst(order);
    }

    /**
     * 5. 退款：人民币退款 + LSC返还 + 赠送撤回。
     */
    @Transactional(rollbackFor = Exception.class)
    public void createRefund(RefundRequest req) {
        Order order = orderMapper.selectById(req.getOrderId());
        if (order == null) return;

        // 创建退款单
        RefundOrder refund = new RefundOrder();
        refund.setRefundId(IdUtil.getSnowflakeNextId());
        refund.setRefundNo("REF" + System.currentTimeMillis());
        refund.setOrderId(req.getOrderId());
        refund.setReason(req.getReason());
        refund.setStatus("APPROVED");
        refund.setBenefitStatus("PROCESSING");
        refund.setRmbCent(req.getRmbCent());
        refund.setShippingRefundCent(0L);
        refund.setVersion(0);
        refundMapper.insert(refund);

        // LSC 返还 + 赠送撤回（按订单单元）
        if (order.getLscUnit() != null && order.getLscUnit() > 0) {
            RefundRestoreCommand rrc = new RefundRestoreCommand();
            rrc.setUserId(order.getUserId());
            rrc.setRefundId(refund.getRefundId());
            rrc.setOrderId(order.getOrderId());
            rrc.setBusinessKey("REFUND_RESTORE_" + refund.getRefundId());
            // 简化：全额返还
            List<OrderUnitAllocation> allocs = allocMapper.selectList(
                    new LambdaQueryWrapper<OrderUnitAllocation>()
                            .in(OrderUnitAllocation::getItemId,
                                    itemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                                            .eq(OrderItem::getOrderId, req.getOrderId()))
                                            .stream().map(OrderItem::getItemId).toList()));
            List<RefundRestoreCommand.RestoreItem> items = new ArrayList<>();
            for (OrderUnitAllocation alloc : allocs) {
                if (alloc.getLscUnit() != null && alloc.getLscUnit() > 0) {
                    RefundRestoreCommand.RestoreItem ri = new RefundRestoreCommand.RestoreItem();
                    ri.setConsumptionId(alloc.getAllocationId());
                    ri.setAvailableLotId(null); // 实际需查消费分配
                    ri.setRestoreUnit(alloc.getLscUnit());
                    ri.setOriginalExpired(false);
                    items.add(ri);
                }
            }
            rrc.setItems(items);
            if (!items.isEmpty()) ledgerService.refundRestore(rrc);
        }

        // 赠送撤回
        List<OrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, req.getOrderId()));
        for (OrderItem item : items) {
            if (item.getGrantedUnit() != null && item.getGrantedUnit() > 0) {
                ClawbackCommand cc = new ClawbackCommand();
                cc.setUserId(order.getUserId());
                cc.setRefundId(refund.getRefundId());
                cc.setSourceItemId(item.getItemId());
                cc.setGrantLotId(item.getGrantLotId());
                cc.setClawbackUnit(item.getGrantedUnit());
                cc.setBusinessKey("CLAWBACK_" + item.getItemId() + "_" + refund.getRefundId());
                ledgerService.clawbackGrant(cc);
            }
        }

        refund.setBenefitStatus("DONE");
        refund.setStatus("SUCCEEDED");
        refund.setSucceededAt(LocalDateTime.now());
        refundMapper.updateById(refund);

        order.setRefundStatus("REFUNDED");
        orderMapper.updateById(order);
    }

    // ============================ 内部工具 ============================

    /**
     * 按商品金额比例分摊优惠（向下取整 + 最大余数法分配剩余分）。
     */
    private void allocateDiscount(List<OrderUnitAllocation> allocs, long totalGoodsCent,
                                   long couponCent, long lscCent) {
        if (allocs.isEmpty() || (couponCent == 0 && lscCent == 0)) return;
        long totalDiscount = couponCent + lscCent;
        if (totalDiscount <= 0) return;

        long[] remainders = new long[allocs.size()];
        long[] ids = new long[allocs.size()];
        long allocated = 0;
        for (int i = 0; i < allocs.size(); i++) {
            OrderUnitAllocation a = allocs.get(i);
            // 按比例向下取整
            long share = LscMathUtil.mulDivFloor(totalDiscount, a.getSaleCent(), totalGoodsCent);
            a.setLscUnit(0L);
            a.setCouponCent(0L);
            // 先抵扣LSC再券（简化：混合优惠按比例）
            a.setRmbCent(a.getSaleCent() - share);
            allocated += share;
            // 余数 = (totalDiscount * saleCent) % totalGoodsCent
            remainders[i] = (totalDiscount * a.getSaleCent()) % totalGoodsCent;
            ids[i] = a.getAllocationId();
        }
        long extra = totalDiscount - allocated;
        if (extra > 0) {
            long[] extraAlloc = LscMathUtil.largestRemainderAllocate(remainders, ids, extra);
            for (int i = 0; i < allocs.size(); i++) {
                if (extraAlloc[i] > 0) {
                    allocs.get(i).setRmbCent(allocs.get(i).getRmbCent() - extraAlloc[i]);
                }
            }
        }
    }

    private void triggerReferralRewardIfFirst(Order order) {
        var user = userMapper.selectById(order.getUserId());
        if (user == null || user.getFirstQualifiedOrderId() != null) return;
        // 固化首单
        user.setFirstQualifiedOrderId(order.getOrderId());
        userMapper.updateById(user);
        // 查找推荐关系并触发（简化）
    }

    private long decryptCost(String costEnc) {
        // 简化：实际使用KMS解密
        return 0L;
    }
}
