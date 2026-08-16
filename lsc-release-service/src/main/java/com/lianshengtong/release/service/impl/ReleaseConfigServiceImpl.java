package com.lianshengtong.release.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.release.entity.ParamChangeApproval;
import com.lianshengtong.release.entity.ReleaseConfig;
import com.lianshengtong.release.mapper.ParamChangeApprovalMapper;
import com.lianshengtong.release.mapper.ReleaseConfigMapper;
import com.lianshengtong.release.service.ReleaseConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 释放配置服务实现
 * <p>
 * 读取 release_config 表，区分硬常量(editable=0)与可配置参数(editable=1)。
 * 硬常量(rate_max/rate_min)编译后不可修改；可配置参数变更需双重管理员签名审批 + 链上存证。
 * 配置加载到本地缓存，变更后刷新。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseConfigServiceImpl implements ReleaseConfigService {

    private final ReleaseConfigMapper releaseConfigMapper;
    private final ParamChangeApprovalMapper paramChangeApprovalMapper;

    /** 配置键常量 */
    public static final String KEY_RATE_MAX = "rate_max";
    public static final String KEY_RATE_MIN = "rate_min";
    public static final String KEY_K_MIN = "k_min";
    public static final String KEY_K_MAX = "k_max";
    public static final String KEY_ALPHA = "alpha";

    /** 双重管理员签名最少人数 */
    private static final int MIN_APPROVERS = 2;

    /** 审批状态：0待审批 1已通过 2已拒绝 */
    private static final int APPROVAL_PENDING = 0;
    private static final int APPROVAL_APPROVED = 1;
    private static final int APPROVAL_REJECTED = 2;

    /** 本地缓存 configKey -> ReleaseConfig */
    private final Map<String, ReleaseConfig> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refresh();
    }

    @Override
    public BigDecimal getRateMax() {
        return new BigDecimal(getValue(KEY_RATE_MAX, "0.0005"));
    }

    @Override
    public BigDecimal getRateMin() {
        return new BigDecimal(getValue(KEY_RATE_MIN, "0.0003"));
    }

    @Override
    public BigDecimal getKMin() {
        return new BigDecimal(getValue(KEY_K_MIN, "0.005"));
    }

    @Override
    public BigDecimal getKMax() {
        return new BigDecimal(getValue(KEY_K_MAX, "0.01"));
    }

    @Override
    public BigDecimal getAlpha() {
        return new BigDecimal(getValue(KEY_ALPHA, "0.05"));
    }

    @Override
    public List<ReleaseConfig> listAll() {
        return releaseConfigMapper.selectList(new LambdaQueryWrapper<ReleaseConfig>()
                .orderByAsc(ReleaseConfig::getId));
    }

    @Override
    public ReleaseConfig getByKey(String configKey) {
        return cache.computeIfAbsent(configKey, k -> releaseConfigMapper.findByKey(configKey));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReleaseConfig updateConfig(String configKey, String configValue, String operator,
                                      List<String> approverSignatures, String evidenceTxHash) {
        ReleaseConfig config = getByKey(configKey);
        assertEditable(config, configKey);
        assertDualSignatures(approverSignatures);

        String oldValue = config.getConfigValue();
        config.setConfigValue(configValue);
        config.setUpdatedBy(operator);
        config.setUpdatedAt(LocalDateTime.now());
        releaseConfigMapper.updateById(config);

        // 写入审批记录(已通过)
        ParamChangeApproval approval = new ParamChangeApproval();
        approval.setConfigKey(configKey);
        approval.setOldValue(oldValue);
        approval.setNewValue(configValue);
        approval.setOperator(operator);
        approval.setApproverSignatures(JSONUtil.toJsonStr(approverSignatures));
        approval.setEvidenceTxHash(evidenceTxHash);
        approval.setStatus(APPROVAL_APPROVED);
        approval.setApprover(operator);
        approval.setApproveComment("updateConfig 直通审批");
        paramChangeApprovalMapper.insert(approval);

        cache.put(configKey, config);
        log.info("[ReleaseConfig] 配置变更完成 key={} {} -> {} operator={}", configKey, oldValue, configValue, operator);
        return config;
    }

    @Override
    public boolean isEditable(String configKey) {
        ReleaseConfig config = getByKey(configKey);
        return config != null && Integer.valueOf(1).equals(config.getEditable());
    }

    @Override
    public void refresh() {
        List<ReleaseConfig> all = releaseConfigMapper.selectList(null);
        cache.clear();
        for (ReleaseConfig c : all) {
            cache.put(c.getConfigKey(), c);
        }
        log.info("[ReleaseConfig] 缓存刷新，共加载 {} 条配置", all.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParamChangeApproval applyParamChange(String configKey, String configValue, String operator, String evidenceTxHash) {
        ReleaseConfig config = getByKey(configKey);
        assertEditable(config, configKey);

        ParamChangeApproval approval = new ParamChangeApproval();
        approval.setConfigKey(configKey);
        approval.setOldValue(config.getConfigValue());
        approval.setNewValue(configValue);
        approval.setOperator(operator);
        approval.setEvidenceTxHash(evidenceTxHash);
        approval.setStatus(APPROVAL_PENDING);
        paramChangeApprovalMapper.insert(approval);
        log.info("[ReleaseConfig] 参数变更申请已提交 approvalId={} key={} {} -> {} operator={}",
                approval.getId(), configKey, config.getConfigValue(), configValue, operator);
        return approval;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReleaseConfig approveParamChange(Long approvalId, String approver, List<String> approverSignatures,
                                            String approveComment, boolean approved) {
        ParamChangeApproval approval = paramChangeApprovalMapper.selectById(approvalId);
        if (approval == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "审批记录不存在 id=" + approvalId);
        }
        if (!Integer.valueOf(APPROVAL_PENDING).equals(approval.getStatus())) {
            throw new BizException("审批记录已处理，不可重复审批 id=" + approvalId);
        }
        if (approved) {
            // 双重管理员签名校验
            assertDualSignatures(approverSignatures);
            ReleaseConfig config = getByKey(approval.getConfigKey());
            assertEditable(config, approval.getConfigKey());

            config.setConfigValue(approval.getNewValue());
            config.setUpdatedBy(approver);
            config.setUpdatedAt(LocalDateTime.now());
            releaseConfigMapper.updateById(config);
            cache.put(approval.getConfigKey(), config);

            approval.setStatus(APPROVAL_APPROVED);
            approval.setApprover(approver);
            approval.setApproverSignatures(JSONUtil.toJsonStr(approverSignatures));
            approval.setApproveComment(approveComment);
            paramChangeApprovalMapper.updateById(approval);
            log.info("[ReleaseConfig] 参数变更审批通过 approvalId={} key={} -> {}", approvalId, approval.getConfigKey(), approval.getNewValue());
            return config;
        } else {
            approval.setStatus(APPROVAL_REJECTED);
            approval.setApprover(approver);
            approval.setApproveComment(approveComment);
            paramChangeApprovalMapper.updateById(approval);
            log.info("[ReleaseConfig] 参数变更审批拒绝 approvalId={} key={}", approvalId, approval.getConfigKey());
            return getByKey(approval.getConfigKey());
        }
    }

    private String getValue(String key, String defaultValue) {
        ReleaseConfig c = getByKey(key);
        return c == null || c.getConfigValue() == null ? defaultValue : c.getConfigValue();
    }

    private void assertEditable(ReleaseConfig config, String configKey) {
        if (config == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "配置项不存在：" + configKey);
        }
        if (!Integer.valueOf(1).equals(config.getEditable())) {
            throw new BizException("配置项不可修改(硬常量)：" + configKey);
        }
    }

    private void assertDualSignatures(List<String> approverSignatures) {
        if (approverSignatures == null || approverSignatures.size() < MIN_APPROVERS) {
            throw new BizException("参数变更需双重管理员签名审批(至少" + MIN_APPROVERS + "名)");
        }
    }
}
