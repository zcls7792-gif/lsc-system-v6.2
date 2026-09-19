package com.lianshengtong.ledger.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.ledger.entity.AvailableLscDetail;
import com.lianshengtong.ledger.entity.LscAccount;
import com.lianshengtong.ledger.entity.LscTransaction;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * LSC 账本核心服务接口 (V7.3)
 * <p>
 * V7.3 合规基线：LSC 不可转让、兑现、提现，仅限自营体系内抵扣消费。
 * V6.2 的 B2B 流转、商家核销、消费者→商家支付、过期转回 接口已删除。
 * 当前保留原子操作：消费赠送入锁定、每日释放、退款退回。
 * Task4 新增：订单抵扣、退款回扣、到期作废、风控冻结/解冻、推荐奖励入锁定。
 * </p>
 * <p>
 * 所有写操作均保证：幂等性(基于 lsc_transactions.idempotent_key 唯一索引)、
 * 并发安全(Redisson 分布式锁 + 乐观锁 version)、跨服务一致性(Seata AT)。
 * </p>
 */
public interface LscLedgerService {

    /**
     * 消费赠送 LSC(入锁定)
     * <p>V7.3 spec 3.1：消费者下单后，按订单人民币实付部分计算赠送 LSC，进入消费者本人锁定池。</p>
     *
     * @param userId  消费者用户ID
     * @param amount  赠送数量(正数)
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
     * 退款退回 LSC(消费者可用余额入账)
     * <p>退款时将原订单抵扣的 LSC 退回消费者可用余额。</p>
     *
     * @param userId  消费者用户ID(接收退款方)
     * @param amount  退回数量(正数)
     * @param orderNo 关联退款订单号
     * @return 操作后的账户快照
     */
    LscAccount refundLsc(Long userId, Long amount, String orderNo);

    /**
     * 订单抵扣 LSC(消费者可用余额扣减)
     * <p>V7.3 spec：消费者下单时使用可用 LSC 抵扣订单金额，LSC 直接销毁(不流转给商家)。
     * 对应流水类型 ORDER_DEDUCT(2)。</p>
     *
     * @param userId  消费者用户ID
     * @param amount  抵扣数量(正数)
     * @param orderNo 关联订单号
     * @return 操作后的账户快照
     */
    LscAccount deductLsc(Long userId, Long amount, String orderNo);

    /**
     * 退款回扣 LSC(扣回订单获赠的锁定 LSC)
     * <p>订单退款时，将该订单已赠送的 LSC 从消费者锁定池中扣回(销毁)，与 refundLsc 配对保证账务平衡。
     * 对应流水类型 REFUND_DEDUCT(4)。</p>
     *
     * @param userId  消费者用户ID
     * @param amount  扣回数量(正数)
     * @param orderNo 关联退款订单号
     * @return 操作后的账户快照
     */
    LscAccount refundDeductLsc(Long userId, Long amount, String orderNo);

    /**
     * 到期作废 LSC(可用明细过期销毁)
     * <p>可用 LSC 明细超过有效期后作废，扣减消费者可用余额并更新明细状态为 EXPIRED_WRITEOFF。
     * 对应流水类型 EXPIRE_WRITEOFF(5)。</p>
     *
     * @param userId  消费者用户ID
     * @param amount  作废数量(正数)
     * @param orderNo 关联作废任务单号
     * @return 操作后的账户快照
     */
    LscAccount expireWriteoff(Long userId, Long amount, String orderNo);

    /**
     * 风控冻结 LSC(可用 -> 冻结)
     * <p>风控场景下将消费者可用余额转入冻结池，冻结期间不可用于抵扣。
     * 对应流水类型 RISK_FREEZE(8)。</p>
     *
     * @param userId  消费者用户ID
     * @param amount  冻结数量(正数)
     * @param orderNo 关联风控单号
     * @return 操作后的账户快照
     */
    LscAccount freezeLsc(Long userId, Long amount, String orderNo);

    /**
     * 风控解冻 LSC(冻结 -> 可用)
     * <p>风控解除后将冻结余额转回可用余额。
     * 对应流水类型 RISK_UNFREEZE(9)。</p>
     *
     * @param userId  消费者用户ID
     * @param amount  解冻数量(正数)
     * @param orderNo 关联风控单号
     * @return 操作后的账户快照
     */
    LscAccount unfreezeLsc(Long userId, Long amount, String orderNo);

    /**
     * 推荐奖励 LSC(入锁定)
     * <p>推荐/推广奖励的 LSC 进入消费者锁定池，按日释放规则转为可用。
     * 对应流水类型 PROMOTION_REWARD_LOCKED(6)。</p>
     *
     * @param userId  消费者用户ID
     * @param amount  奖励数量(正数)
     * @param orderNo 关联奖励单号
     * @return 操作后的账户快照
     */
    LscAccount promotionRewardLsc(Long userId, Long amount, String orderNo);

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
     * 用户LSC概览(锁定/可用/冻结/已使用/月收入等)
     *
     * @param userId 用户ID
     * @return 概览数据
     */
    Map<String, Object> overview(Long userId);
}
