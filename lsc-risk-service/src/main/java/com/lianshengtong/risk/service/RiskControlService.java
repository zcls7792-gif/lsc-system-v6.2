package com.lianshengtong.risk.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.risk.dto.RiskCheckDTO;
import com.lianshengtong.risk.entity.RiskLog;

import java.util.Map;

/**
 * 风控服务接口
 * <p>
 * 固定规则风控 + AI动态风控评分。
 * 高风险自动限制(暂停LSC支付、冻结账户) + 推送人工审核；中低风险仅记录日志。
 * </p>
 */
public interface RiskControlService {

    /**
     * 风控检测
     * <p>依次执行固定规则(批量下单/异常混合支付/高频套利/异地操作) + AI动态评分，取最高风险等级。</p>
     *
     * @param dto 检测请求
     * @return 风控日志(含风险等级与处理状态)
     */
    RiskLog check(RiskCheckDTO dto);

    /**
     * 风控日志分页查询
     *
     * @param page         页码
     * @param size         每页条数
     * @param userId       用户ID(可空)
     * @param riskLevel    风险等级(可空)
     * @param handleStatus 处理状态(可空)
     * @return 分页结果
     */
    IPage<RiskLog> logs(Integer page, Integer size, Long userId, Integer riskLevel, Integer handleStatus);

    /**
     * 人工处理风控事件
     *
     * @param id            风控日志ID
     * @param handleStatus  处理状态(2已推送人工审核/3已忽略/4已解封)
     * @param handleRemark  处理备注
     */
    void handle(Long id, Integer handleStatus, String handleRemark);

    /**
     * 风控仪表盘统计(各风险等级/处理状态计数)
     *
     * @return 统计数据
     */
    Map<String, Object> dashboard();

    /**
     * 根据ID查询风控日志详情
     *
     * @param id 风控日志ID
     * @return 风控日志
     */
    RiskLog getById(Long id);
}
