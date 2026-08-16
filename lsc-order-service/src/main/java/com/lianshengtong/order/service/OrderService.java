package com.lianshengtong.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.order.dto.OrderCreateDTO;
import com.lianshengtong.order.dto.OrderPayDTO;
import com.lianshengtong.order.dto.OrderRefundDTO;
import com.lianshengtong.order.entity.Order;

import java.time.LocalDate;
import java.util.Map;

/**
 * 订单核心服务接口
 * <p>
 * 定义订单全生命周期操作：创建（混合支付计算）、支付、完成、取消、全额退款、部分退款。
 * 支付/退款通过 Feign 调用账本服务完成 LSC 原子化操作，人民币支付/退款由支付机构处理。
 * 跨服务一致性由 Seata AT 保障。
 * </p>
 */
public interface OrderService {

    /**
     * 创建订单（线上/线下）
     * <p>计算混合支付拆分(LSC + 人民币)，订单初始状态为待支付。</p>
     *
     * @param dto 创建请求
     * @return 创建成功的订单
     */
    Order createOrder(OrderCreateDTO dto);

    /**
     * 支付订单
     * <p>调用账本服务扣减消费者可用 LSC 并转入商家；唤起人民币支付。</p>
     *
     * @param dto 支付请求
     * @return 支付后的订单
     */
    Order payOrder(OrderPayDTO dto);

    /**
     * 完成订单
     *
     * @param orderNo   订单号
     * @param operatorId 操作人ID(商家)
     * @return 完成后的订单
     */
    Order completeOrder(String orderNo, Long operatorId);

    /**
     * 取消订单
     * <p>仅待支付状态可取消。</p>
     *
     * @param orderNo   订单号
     * @param operatorId 操作人ID
     * @return 取消后的订单
     */
    Order cancelOrder(String orderNo, Long operatorId);

    /**
     * 全额退款
     * <p>LSC 退回消费者(触发发行回滚) + 人民币全额退回。</p>
     *
     * @param dto 退款请求
     * @return 退款后的订单
     */
    Order refundOrder(OrderRefundDTO dto);

    /**
     * 部分退款
     * <p>按本次退款金额部分退回 LSC + 人民币，累计已退金额。</p>
     *
     * @param dto 退款请求(需携带本次退款金额)
     * @return 退款后的订单
     */
    Order partialRefund(OrderRefundDTO dto);

    /**
     * 根据订单号查询订单
     *
     * @param orderNo 订单号
     * @return 订单详情
     */
    Order getByOrderNo(String orderNo);

    /**
     * 分页查询订单
     *
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @param userId    消费者或商家ID(可空)
     * @param status    订单状态(可空)
     * @return 分页结果
     */
    IPage<Order> listOrders(Integer pageNum, Integer pageSize, Long userId, Integer status);

    /**
     * 分页查询订单(管理后台扩展过滤)
     *
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @param userId    消费者或商家ID(可空)
     * @param status    订单状态(可空)
     * @param orderNo   订单号模糊匹配(可空)
     * @param orderType 订单类型 0线上 1线下(可空)
     * @param startDate 起始日期 yyyy-MM-dd(可空)
     * @param endDate   结束日期 yyyy-MM-dd(可空)
     * @return 分页结果
     */
    IPage<Order> listOrders(Integer pageNum, Integer pageSize, Long userId, Integer status,
                            String orderNo, Integer orderType, String startDate, String endDate);

    /**
     * 按日期汇总订单支付金额(对账支付侧)
     * <p>统计指定自然日已支付/已完成/已退款/部分退款的订单 totalPrice 总和与笔数。</p>
     *
     * @param date 目标日期(可空表示当天)
     * @return Map: {totalAmount(BigDecimal), totalCount(Long)}
     */
    Map<String, Object> dailySummary(LocalDate date);

    /**
     * 拒绝退款
     * <p>将退款中的订单恢复为已完成状态。</p>
     *
     * @param orderNo   订单号
     * @param operatorId 操作人ID
     * @param reason    拒绝原因
     */
    void rejectRefund(String orderNo, Long operatorId, String reason);

    /**
     * 商家今日订单/收入统计
     *
     * @param merchantId 商家ID(可空)
     * @return Map: {todayOrderCount, todayRevenue, pendingShipCount, pendingRefundCount}
     */
    Map<String, Object> statsToday(Long merchantId);
}
