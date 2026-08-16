package com.lianshengtong.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.promotion.dto.FirstOrderCheckDTO;
import com.lianshengtong.promotion.dto.RewardResultDTO;
import com.lianshengtong.promotion.dto.RollbackRewardDTO;
import com.lianshengtong.promotion.entity.PromotionPending;

/**
 * 推广服务接口
 * <p>
 * 严格限定一级推荐(users.referrer_id 单一外键约束，禁止链式)。
 * 首单定义：实名后第一笔金额 >= 1 元的有效消费(已完成且未全额退款)。
 * 奖励 = 首单消费金额 × 10%，从推荐人锁定池划转至可用池。
 * </p>
 */
public interface PromotionService {

    /**
     * 首单判定
     * <p>判定被推荐人是否为实名后第一笔金额 >= 1 元的有效消费(已完成且未全额退款)。</p>
     *
     * @param dto 首单判定请求
     * @return 奖励计算结果(含是否首单、推荐人ID、奖励金额)
     */
    RewardResultDTO checkFirstOrder(FirstOrderCheckDTO dto);

    /**
     * 奖励计算与划转
     * <p>奖励 = 首单消费金额 × 10%，从推荐人锁定池划转至可用池(交易类型 PROMOTION_REWARD=3)。
     * 划转失败写入挂账表，由每日定时任务补发。基于 Seata AT 保障一致性。</p>
     *
     * @param dto 首单判定请求
     * @return 奖励计算结果(含划转状态)
     */
    RewardResultDTO calcReward(FirstOrderCheckDTO dto);

    /**
     * 奖励回滚
     * <p>首单全额退款时，扣回已划转给推荐人的奖励(可用池 -> 锁定池)。
     * 若奖励仍挂在待补发状态，则直接将挂账记录置为废弃。</p>
     *
     * @param dto 回滚请求
     */
    void rollbackReward(RollbackRewardDTO dto);

    /**
     * 挂账自动补发(每日定时扫描)
     * <p>扫描 status=0(待补发) 的挂账记录，批量调用账本服务补发奖励划转。</p>
     *
     * @return 本次补发成功条数
     */
    int pendingAutoFill();

    /**
     * 首单完成通知(order-service 在订单完成时调用)
     * <p>由 order-service 在订单完成(COMPLETED)时通过 Feign 通知，本服务内部
     * 构造首单判定请求并触发奖励计算与划转；推荐人ID由本服务通过 user-service
     * 反查获取。失败不阻断调用方主流程(已在 order-service 内 try-catch 兜底)。</p>
     *
     * @param consumerId   消费者用户ID
     * @param orderNo      订单号
     * @param orderAmount  订单实付金额(元)
     * @param orderStatus  订单状态(2=已完成)
     * @param refundAmount 累计退款金额(元，无退款传0/null)
     */
    void notifyFirstOrder(Long consumerId, String orderNo, java.math.BigDecimal orderAmount,
                          Integer orderStatus, java.math.BigDecimal refundAmount);

    /**
     * 分页查询挂账列表
     *
     * @param page     页码
     * @param size     每页条数
     * @param status   状态(可空: 全部)
     * @return 分页结果
     */
    IPage<PromotionPending> pendingList(Integer page, Integer size, Integer status);
}
