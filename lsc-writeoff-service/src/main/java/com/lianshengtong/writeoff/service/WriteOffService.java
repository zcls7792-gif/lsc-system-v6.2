package com.lianshengtong.writeoff.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.writeoff.dto.WriteOffApplyDTO;
import com.lianshengtong.writeoff.entity.MerchantNhRecord;

import java.util.Map;

/**
 * 商家核销核心服务接口
 * <p>
 * 核销流程：资格校验 -&gt; 次数校验 -&gt; 限额校验 -&gt; 余额校验 -&gt; 现金计算(100:87)
 * -&gt; 资金划拨 -&gt; LSC扣减销毁 -&gt; 流水记录与最近核销日期更新。
 * 幂等通过 order_no 唯一索引 + version 乐观锁双重校验保障。
 * </p>
 */
public interface WriteOffService {

    /**
     * 申请核销
     * <p>执行完整核销流程，成功后记录流水并更新商家最近核销日期。</p>
     *
     * @param dto 核销申请
     * @return 核销记录
     */
    MerchantNhRecord applyWriteOff(WriteOffApplyDTO dto);

    /**
     * 根据核销订单号查询核销记录
     *
     * @param orderNo 核销订单号
     * @return 核销记录
     */
    MerchantNhRecord getByOrderNo(String orderNo);

    /**
     * 根据主键ID查询核销记录
     *
     * @param id 主键ID
     * @return 核销记录
     */
    MerchantNhRecord getById(Long id);

    /**
     * 分页查询核销记录
     *
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @param merchantId 商家ID(可空)
     * @param status     核销状态(可空)
     * @return 分页结果
     */
    IPage<MerchantNhRecord> listRecords(Integer pageNum, Integer pageSize, Long merchantId, Integer status);

    /**
     * 分页查询核销记录(管理后台扩展过滤)
     *
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @param merchantId 商家ID(可空)
     * @param status     核销状态(可空)
     * @param batchNo    核销订单号模糊匹配(可空)
     * @param startDate  起始日期 yyyy-MM-dd(可空)
     * @param endDate    结束日期 yyyy-MM-dd(可空)
     * @return 分页结果
     */
    IPage<MerchantNhRecord> listRecords(Integer pageNum, Integer pageSize, Long merchantId, Integer status,
                                        String batchNo, String startDate, String endDate);

    /**
     * 核销统计
     *
     * @param merchantId 商家ID(可空, 空表示全网)
     * @param startDate  起始日期(可空)
     * @param endDate    结束日期(可空)
     * @return 统计结果 {totalCount, totalLscAmount, totalCashAmount, byStatus: {...}}
     */
    Map<String, Object> stats(Long merchantId, String startDate, String endDate);

    /**
     * 核销限额预览
     *
     * @param merchantId 商家ID
     * @return {dailyLimit, todayUsed, todayRemaining, nhLimitLevel, cashRate, regulatoryBalance, lastNhDate}
     */
    Map<String, Object> quota(Long merchantId);

    /**
     * 标记核销记录为失败(REQUIRES_NEW 新事务，避免主事务回滚导致失败记录丢失)
     * <p>调用方需通过 Spring 代理对象调用，自调用不会触发 AOP。</p>
     *
     * @param recordId   核销记录ID
     * @param version    乐观锁版本
     * @param failReason 失败原因
     */
    void markRecordFailed(Long recordId, Integer version, String failReason);
}
