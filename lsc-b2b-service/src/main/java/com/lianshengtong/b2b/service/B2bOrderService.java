package com.lianshengtong.b2b.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.b2b.dto.B2bOrderCancelDTO;
import com.lianshengtong.b2b.dto.B2bOrderConfirmDTO;
import com.lianshengtong.b2b.dto.B2bOrderCreateDTO;
import com.lianshengtong.b2b.dto.B2bOrderTransferDTO;
import com.lianshengtong.b2b.dto.B2bOrderVoidDTO;
import com.lianshengtong.b2b.entity.B2bOrder;

import java.util.Map;

/**
 * B2B 交易订单核心服务接口
 * <p>
 * 定义 B2B 订单全生命周期操作：创建、对手方确认、流转执行、取消、作废、AI 核验。
 * 所有写操作保证：幂等性(基于 b2b_orders.idempotent_key 唯一索引)、
 * 并发安全(Redisson 分布式锁 + 乐观锁 version)、跨服务一致性(Seata AT)。
 * </p>
 */
public interface B2bOrderService {

    /**
     * 创建 B2B 订单
     * <p>生成订单号 + 幂等键 + version，设置7天过期时间，初始状态为待确认。</p>
     *
     * @param dto 创建请求
     * @return 创建成功的订单
     */
    B2bOrder createOrder(B2bOrderCreateDTO dto);

    /**
     * 对手方确认订单
     * <p>校验确认人身份是否为订单接收方，校验订单处于待确认状态。</p>
     *
     * @param dto 确认请求
     * @return 确认后的订单
     */
    B2bOrder confirmOrder(B2bOrderConfirmDTO dto);

    /**
     * 执行 LSC 流转
     * <p>校验订单已确认且未过期；校验 lsc_amount == total_amount_rmb(1:1)；
     * 调用账本服务执行 1:1 流转；更新订单状态为已流转。</p>
     *
     * @param dto 流转请求
     * @return 流转后的订单
     */
    B2bOrder executeTransfer(B2bOrderTransferDTO dto);

    /**
     * 双方取消订单
     * <p>仅发起方或接收方可取消，订单须处于待确认或已确认状态。</p>
     *
     * @param dto 取消请求
     * @return 取消后的订单
     */
    B2bOrder cancelOrder(B2bOrderCancelDTO dto);

    /**
     * 作废订单
     * <p>AI 核验可疑或人工判定虚假贸易后作废，冻结后续流转权限。</p>
     *
     * @param dto 作废请求
     * @return 作废后的订单
     */
    B2bOrder voidOrder(B2bOrderVoidDTO dto);

    /**
     * 获取 AI 核验结果
     * <p>调用 AI 网关 Feign 接口对订单贸易真实性进行核验，回写订单核验字段。</p>
     *
     * @param orderNo 订单号
     * @return 核验结果(result/score/riskTags)
     */
    Map<String, Object> getAiVerification(String orderNo);

    /**
     * 根据订单号查询订单
     *
     * @param orderNo 订单号
     * @return 订单详情
     */
    B2bOrder getByOrderNo(String orderNo);

    /**
     * 分页查询订单
     *
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @param userId    操作人ID(发起方或接收方，可空)
     * @param status    订单状态(可空)
     * @return 分页结果
     */
    IPage<B2bOrder> listOrders(Integer pageNum, Integer pageSize, Long userId, Integer status);

    /**
     * 分页查询订单(管理后台扩展过滤)
     *
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @param userId    操作人ID(可空)
     * @param status    订单状态(可空)
     * @param orderNo   订单号模糊匹配(可空)
     * @param startDate 起始日期 yyyy-MM-dd(可空)
     * @param endDate   结束日期 yyyy-MM-dd(可空)
     * @return 分页结果
     */
    IPage<B2bOrder> listOrders(Integer pageNum, Integer pageSize, Long userId, Integer status,
                                String orderNo, String startDate, String endDate);

    /**
     * 人工核验确认 B2B 订单
     * <p>管理后台人工复核 AI 核验可疑订单：标记为人工真实(3)或人工虚假(4)。</p>
     *
     * @param orderNo 订单号
     * @param pass    true=人工真实(3), false=人工虚假(4)
     * @param remark  人工备注(可空)
     * @return 更新后的订单
     */
    B2bOrder manualVerifyConfirm(String orderNo, Boolean pass, String remark);
}
