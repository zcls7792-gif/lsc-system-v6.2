package com.lianshengtong.ledger.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.ledger.entity.AvailableLscDetail;
import com.lianshengtong.ledger.entity.LscAccount;
import com.lianshengtong.ledger.entity.LscTransaction;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * LSC 账本核心服务接口
 * <p>
 * 定义账务原子化操作：发行、释放、支付、B2B流转、核销、退款退回、过期转回、余额查询。
 * 所有写操作均保证：幂等性(基于 lsc_transactions.idempotent_key 唯一索引)、
 * 并发安全(Redisson 分布式锁 + 乐观锁 version)、跨服务一致性(Seata AT)。
 * </p>
 */
public interface LscLedgerService {

    /**
     * 消费发行 LSC(锁定)
     * <p>消费者下单后，按订单金额发行等量 LSC 到锁定余额。</p>
     *
     * @param userId  消费者用户ID
     * @param amount  发行数量(正数)
     * @param orderNo 关联订单号
     * @return 操作后的账户快照
     */
    LscAccount issueLsc(Long userId, Long amount, String orderNo);

    /**
     * 每日释放 LSC(锁定 -&gt; 可用)
     * <p>按释放比例将锁定余额转为可用余额，并写入可用明细。</p>
     *
     * @param userId  用户ID
     * @param amount  本次释放数量(正数)
     * @param orderNo 关联释放任务单号
     * @return 操作后的账户快照
     */
    LscAccount releaseLsc(Long userId, Long amount, String orderNo);

    /**
     * 消费支付 LSC(消费者可用余额扣减并转入商家可用余额)
     * <p>消费者用可用 LSC 向商家支付，扣减消费者可用、增加商家可用。
     * 商家接收方写入可用明细(有效期365天)。</p>
     *
     * @param consumerId 消费者用户ID
     * @param merchantId 商家用户ID
     * @param amount     支付数量(正数)
     * @param orderNo    关联订单号
     * @return 消费者操作后账户快照
     */
    LscAccount payLsc(Long consumerId, Long merchantId, Long amount, String orderNo);

    /**
     * B2B 流转(商家 -&gt; 商家)
     * <p>校验双方均为商家会员；扣减发起方可用、增加接收方可用；
     * 接收方 LSC 有效期重置365天。</p>
     *
     * @param fromMerchantId 发起方商家ID
     * @param toMerchantId   接收方商家ID
     * @param amount         流转数量(正数)
     * @param orderNo        关联B2B订单号
     * @return 发起方操作后账户快照
     */
    LscAccount b2bTransfer(Long fromMerchantId, Long toMerchantId, Long amount, String orderNo);

    /**
     * 商家核销销毁 LSC
     * <p>商家将可用 LSC 核销兑换现金，可用余额扣减并销毁。</p>
     *
     * @param merchantId 商家用户ID
     * @param amount     核销数量(正数)
     * @param orderNo    关联核销订单号
     * @return 操作后的账户快照
     */
    LscAccount writeOffLsc(Long merchantId, Long amount, String orderNo);

    /**
     * 退款退回 LSC
     * <p>商家将可用 LSC 退回给消费者(转入消费者可用余额)。</p>
     *
     * @param userId  消费者用户ID(接收退款方)
     * @param amount  退回数量(正数)
     * @param orderNo 关联退款订单号
     * @return 操作后的账户快照
     */
    LscAccount refundLsc(Long userId, Long amount, String orderNo);

    /**
     * 过期转回(可用 -&gt; 锁定)
     * <p>扫描指定用户 status=1 且 expire_date &lt; 今天 的可用明细，
     * 汇总金额后扣减可用余额、增加锁定余额，并将明细状态更新为2(已过期转回)。</p>
     *
     * @param userId 用户ID
     * @return 转回总数量(无过期记录返回0)
     */
    long expireTransfer(Long userId);

    /**
     * 查询用户余额
     *
     * @param userId 用户ID
     * @return 账户快照(不存在则返回余额为0的空账户)
     */
    LscAccount getBalance(Long userId);

    /**
     * 按日期 + 流水类型聚合统计(对账场景使用)
     * <p>对指定自然日(00:00~次日00:00)的流水按类型聚合 SUM(amount) 与 COUNT(*)。
     * 跨分片由 ShardingSphere 自动汇总。</p>
     *
     * @param date  目标日期(自然日)
     * @param types 流水类型集合(可空表示全部类型)
     * @return Map: {totalAmount, totalCount}，无数据返回 0/0
     */
    Map<String, Object> dailySummary(LocalDate date, List<Integer> types);

    /**
     * 查询全网锁定余额汇总(每日释放任务加载待释放明细使用)
     * <p>跨分片广播查询所有 total_locked > 0 的账户。</p>
     *
     * @return Map: {totalLocked, accounts: List<{userId, totalLocked}>}
     */
    Map<String, Object> lockedSummary();

    /**
     * 批量释放(锁定 -> 可用)
     * <p>逐条调用 {@link #releaseLsc(Long, Long, String)}，逐条幂等校验，
     * 汇总成功/失败笔数与释放总量。</p>
     *
     * @param opList 批量操作列表
     * @return Map: {total, successCount, failedCount, releasedAmount}
     */
    Map<String, Object> releaseBatch(List<com.lianshengtong.common.dto.LscLedgerOpDTO> opList);

    /**
     * 全网过期转回(可用 -> 锁定)
     * <p>扫描所有存在 status=1(可用) 明细的用户，逐个执行 {@link #expireTransfer(Long)}。</p>
     *
     * @return Map: {userCount, transferAmount}
     */
    Map<String, Object> expireTransferAll();

    /**
     * 用户流水分页查询(商家/管理后台)
     *
     * @param userId    用户ID
     * @param page      页码
     * @param size      每页条数
     * @param type      流水类型(可空)
     * @param startDate 起始日期(可空)
     * @param endDate   截止日期(可空)
     * @param orderNo   关联订单号(可空)
     * @return 分页结果
     */
    IPage<LscTransaction> transactionList(Long userId, Integer page, Integer size, Integer type,
                                          String startDate, String endDate, String orderNo);

    /**
     * 用户可用LSC明细分页查询
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页条数
     * @param status 状态(可空)
     * @return 分页结果
     */
    IPage<AvailableLscDetail> availableDetails(Long userId, Integer page, Integer size, Integer status);

    /**
     * 近N天交易趋势(按日聚合)
     *
     * @param userId 用户ID
     * @param days   天数(默认7)
     * @return 趋势数据列表
     */
    List<Map<String, Object>> recentTrend(Long userId, Integer days);

    /**
     * 用户LSC概览(锁定/可用/已核销/月收入等)
     *
     * @param userId 用户ID
     * @return 概览数据
     */
    Map<String, Object> overview(Long userId);
}
