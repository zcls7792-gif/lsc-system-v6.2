package com.lianshengtong.release.service;

import com.lianshengtong.release.entity.ParamChangeApproval;
import com.lianshengtong.release.entity.ReleaseConfig;

import java.math.BigDecimal;
import java.util.List;

/**
 * 释放配置服务
 * <p>
 * 读取 release_config 表，区分：
 * <ul>
 *   <li>硬常量 (editable=0)：rate_max=0.05%、rate_min=0.03%，编译后不可修改</li>
 *   <li>可配置参数 (editable=1)：k_min=0.50%、k_max=1.0%、alpha=0.05，修改需双重管理员签名审批+链上存证</li>
 * </ul>
 */
public interface ReleaseConfigService {

    /** 释放速率硬上限 0.05% (editable=0) */
    BigDecimal getRateMax();

    /** 释放速率硬下限 0.03% (editable=0) */
    BigDecimal getRateMin();

    /** 调节起点 0.50% (editable=1) */
    BigDecimal getKMin();

    /** 调节终点 1.0% (editable=1) */
    BigDecimal getKMax();

    /** 调节因子 alpha=0.05 (editable=1) */
    BigDecimal getAlpha();

    /** 查询全部配置 */
    List<ReleaseConfig> listAll();

    /** 按key查询 */
    ReleaseConfig getByKey(String configKey);

    /**
     * 修改可配置参数(editable=1)，硬常量(editable=0)拒绝修改。
     * 修改需双重管理员签名审批 + 链上存证。
     *
     * @param configKey         配置键
     * @param configValue       新值
     * @param operator          操作人
     * @param approverSignatures 双重管理员签名(至少2名)
     * @param evidenceTxHash     链上存证交易哈希
     */
    ReleaseConfig updateConfig(String configKey, String configValue, String operator,
                               List<String> approverSignatures, String evidenceTxHash);

    /** 是否可编辑(editable=1) */
    boolean isEditable(String configKey);

    /** 刷新本地缓存(配置变更后调用) */
    void refresh();

    /**
     * 参数变更申请(editable=1)，创建审批记录(param_change_approval, status=0待审批)，不立即修改配置。
     * 硬常量(editable=0)拒绝申请。
     *
     * @param configKey      配置键
     * @param configValue    新值
     * @param operator       申请人
     * @param evidenceTxHash 链上存证交易哈希
     * @return 审批记录
     */
    ParamChangeApproval applyParamChange(String configKey, String configValue, String operator, String evidenceTxHash);

    /**
     * 参数变更审批(双人审批)：校验双重管理员签名(>=2)，通过后更新配置并刷新缓存。
     *
     * @param approvalId          审批记录ID
     * @param approver            审批人
     * @param approverSignatures  双重管理员签名(至少2名)
     * @param approveComment      审批意见
     * @param approved            true通过 false拒绝
     * @return 更新后的配置(拒绝时返回原配置)
     */
    ReleaseConfig approveParamChange(Long approvalId, String approver, List<String> approverSignatures,
                                     String approveComment, boolean approved);
}
