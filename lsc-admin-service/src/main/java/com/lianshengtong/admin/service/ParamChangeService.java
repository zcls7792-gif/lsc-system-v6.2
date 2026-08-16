package com.lianshengtong.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.admin.entity.ParamChangeApproval;

/**
 * 参数变更审批服务接口
 * <p>关键参数变更需双人审批：发起人提交后，至少2名管理员签名方可生效，并链上存证。</p>
 */
public interface ParamChangeService {

    /**
     * 发起参数变更审批
     *
     * @param configKey   配置键
     * @param newValue    新值
     * @param initiatorId 发起人(管理员ID)
     * @param remark      备注
     * @return 审批单
     */
    ParamChangeApproval submit(String configKey, String newValue, Long initiatorId, String remark);

    /**
     * 审批签名
     * <p>累计签名达 required-signatures(默认2) 后置为已通过，并触发链上存证与配置生效。</p>
     *
     * @param approvalId 审批单ID
     * @param adminId    签名管理员ID
     * @param signature  管理员签名
     * @return 审批单
     */
    ParamChangeApproval approve(Long approvalId, Long adminId, String signature);

    /**
     * 拒绝变更
     *
     * @param approvalId 审批单ID
     * @param adminId    管理员ID
     * @param reason     拒绝原因
     */
    void reject(Long approvalId, Long adminId, String reason);

    /**
     * 分页查询审批列表
     *
     * @param page   页码
     * @param size   每页条数
     * @param status 状态(可空)
     * @return 分页结果
     */
    IPage<ParamChangeApproval> list(Integer page, Integer size, Integer status);
}
