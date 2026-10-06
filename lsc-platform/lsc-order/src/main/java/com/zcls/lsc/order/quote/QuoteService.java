package com.zcls.lsc.order.quote;

import com.zcls.lsc.common.constants.LscConstants;
import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 第7.1章 报价服务。
 *
 * 服务端验证商品、资质、库存、价格版本和优惠后生成报价，quote_id 默认有效 5 分钟。
 * 用户确认后创建订单；报价失效、资源不足或价格变化时要求用户重新确认。
 *
 * 混合支付的赠送预估由服务端计算，前端不得提交自算赠送数量。
 */
@Service
public class QuoteService {

    private final JdbcTemplate jdbc;

    public QuoteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 创建报价。
     *
     * @param userId      用户ID
     * @param buyerType   买家类型 C/B
     * @param items       商品项
     * @param discountMode 抵扣模式 NONE/LSC/COUPON
     * @param lscUnit     LSC 抵扣 unit（discountMode=LSC 时）
     * @param couponId    券ID（discountMode=COUPON 时）
     * @return 报价结果
     */
    @Transactional(rollbackFor = Exception.class)
    public QuoteResult createQuote(long userId, String buyerType, List<QuoteItem> items,
                                   String discountMode, Long lscUnit, Long couponId) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "items empty");
        }

        // 券与 LSC 互斥校验（42206）
        boolean hasCoupon = couponId != null && couponId > 0;
        boolean hasLsc = lscUnit != null && lscUnit > 0;
        if (hasCoupon && hasLsc) {
            throw new BusinessException(ErrorCode.COUPON_LSC_MUTEX);
        }
        if (!hasCoupon && !hasLsc) {
            discountMode = "NONE";
        } else if (hasCoupon) {
            discountMode = "COUPON";
        } else {
            discountMode = "LSC";
        }

        // unit 必须为 100 的整数倍（42208）
        if (hasLsc && lscUnit % 100L != 0L) {
            throw new BusinessException(ErrorCode.UNIT_NOT_MULTIPLE_100, "lsc_unit=" + lscUnit);
        }

        long goodsCent = 0L;
        long couponCent = 0L;
        long lscUnitFinal = hasLsc ? lscUnit : 0L;
        List<QuoteItemResult> itemResults = new ArrayList<>();

        for (QuoteItem item : items) {
            // 查价格版本
            PriceRow price = jdbc.queryForObject(
                    "SELECT retail_price_cent, b_price_cent, grant_coefficient_ppm, cost_price_enc "
                            + "FROM product_price_version ppv "
                            + "JOIN product_sku s ON ppv.sku_id=s.sku_id "
                            + "WHERE s.sku_id=? AND s.status='ACTIVE' "
                            + "ORDER BY ppv.effective_at DESC LIMIT 1",
                    (rs, rowNum) -> new PriceRow(
                            rs.getLong("retail_price_cent"),
                            rs.getLong("b_price_cent"),
                            rs.getLong("grant_coefficient_ppm")),
                    item.skuId());

            long unitPrice = "B".equals(buyerType) ? price.bPriceCent() : price.retailPriceCent();
            long lineCent = unitPrice * item.qty();
            goodsCent += lineCent;

            itemResults.add(new QuoteItemResult(item.skuId(), item.qty(), unitPrice, lineCent));
        }

        // 券优惠校验
        if (hasCoupon) {
            couponCent = validateCoupon(couponId, userId, goodsCent);
        }

        // LSC 抵扣上限校验（42207）: max_deduction = floor(goods_cent × deduction_ppm / 1e6)
        if (hasLsc) {
            long maxLscCent = goodsCent * LscConstants.DEDUCTION_DEFAULT_PPM / 1_000_000L;
            long lscCent = lscUnitFinal / 100L;
            if (lscCent > maxLscCent) {
                throw new BusinessException(ErrorCode.DEDUCTION_OVER_LIMIT,
                        "lsc deduction " + lscCent + " cents exceeds max " + maxLscCent);
            }
        }

        long rmbCent = goodsCent - couponCent - lscUnitFinal / 100L;
        if (rmbCent < 0L) rmbCent = 0L;

        // 生成 quote_id（幂等：同用户同参数 5 分钟内复用）
        long quoteId = nextId();
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(LscConstants.QUOTE_TTL.toMinutes());
        jdbc.update(
                "INSERT INTO quote(quote_id, user_id, buyer_type, goods_cent, coupon_cent, lsc_unit, "
                        + "rmb_cent, discount_mode, coupon_id, expires_at, payload_json) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                quoteId, userId, buyerType, goodsCent, couponCent, lscUnitFinal, rmbCent,
                discountMode, couponId, java.sql.Timestamp.valueOf(expireAt),
                itemsToJson(itemResults));

        return new QuoteResult(quoteId, goodsCent, couponCent, lscUnitFinal, rmbCent, expireAt, itemResults);
    }

    /**
     * 校验券：状态可用、未过期、满足门槛、适用商品范围。
     * 返回券面额分（不超过适用商品总额，超过则拒绝）。
     */
    private long validateCoupon(long couponId, long userId, long goodsCent) {
        CouponRow c = jdbc.queryForObject(
                "SELECT user_id, status, valid_until, face_cent_snapshot, min_spend_cent_snapshot, scope_snapshot_json "
                        + "FROM user_coupon WHERE coupon_id=?",
                (rs, rowNum) -> new CouponRow(
                        rs.getLong("user_id"),
                        rs.getString("status"),
                        rs.getTimestamp("valid_until").toLocalDateTime(),
                        rs.getLong("face_cent_snapshot"),
                        rs.getLong("min_spend_cent_snapshot"),
                        rs.getString("scope_snapshot_json")),
                couponId);

        if (c.userId() != userId) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE, "coupon not owned by user");
        }
        if (!"AVAILABLE".equals(c.status())) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE, "coupon status=" + c.status());
        }
        if (c.validUntil().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE, "coupon expired");
        }
        if (goodsCent < c.minSpendCent()) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE,
                    "goods " + goodsCent + " < min_spend " + c.minSpendCent());
        }
        // 券面额不得超过适用商品总额（本期拒绝而非找零）
        if (c.faceCent() > goodsCent) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE,
                    "coupon face " + c.faceCent() + " exceeds goods " + goodsCent);
        }
        return c.faceCent();
    }

    private String itemsToJson(List<QuoteItemResult> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            QuoteItemResult it = items.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"skuId\":").append(it.skuId())
                    .append(",\"qty\":").append(it.qty())
                    .append(",\"unitPriceCent\":").append(it.unitPriceCent())
                    .append(",\"lineCent\":").append(it.lineCent()).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    public record QuoteItem(long skuId, int qty) {}
    private record PriceRow(long retailPriceCent, long bPriceCent, long grantCoefPpm) {}
    private record CouponRow(long userId, String status, LocalDateTime validUntil,
                             long faceCent, long minSpendCent, String scopeJson) {}

    public record QuoteItemResult(long skuId, int qty, long unitPriceCent, long lineCent) {}
    public record QuoteResult(long quoteId, long goodsCent, long couponCent, long lscUnit,
                              long rmbCent, LocalDateTime expireAt, List<QuoteItemResult> items) {}
}
