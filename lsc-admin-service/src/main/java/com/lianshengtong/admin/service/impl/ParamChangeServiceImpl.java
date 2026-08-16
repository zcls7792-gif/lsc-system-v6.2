package com.lianshengtong.admin.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.admin.config.ConfigCenterAccessor;
import com.lianshengtong.admin.entity.ParamChangeApproval;
import com.lianshengtong.admin.mapper.ParamChangeApprovalMapper;
import com.lianshengtong.admin.service.ParamChangeService;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 参数变更审批服务实现
 * <p>
 * 关键参数(如释放比例 editable=1)变更双人审批流程：
 * 发起人提交 -> 至少 2 名管理员签名 -> 链上存证 -> 配置生效。
 * Redisson 分布式锁防止并发签名。
 * 配置原值通过 {@link ConfigCenterAccessor} 读取，默认为 Stub 实现，
 * 生产环境注入 Nacos/Jdbc 实现后自动覆盖。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParamChangeServiceImpl implements ParamChangeService {

    private final ParamChangeApprovalMapper paramChangeApprovalMapper;
    private final RedissonClient redissonClient;
    private final com.lianshengtong.admin.feign.EvidenceFeignClient evidenceFeignClient;
    private final ConfigCenterAccessor configCenterAccessor;

    @Value("${lsc.admin.param-approval.required-signatures:2}")
    private int requiredSignatures;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParamChangeApproval submit(String configKey, String newValue, Long initiatorId, String remark) {
        // 查询原值：通过 ConfigCenterAccessor 读取（默认 Stub 返回空，注入具体实现后读取真实值）
        String oldValue = configCenterAccessor.getOriginalValue(configKey);
        ParamChangeApproval approval = new ParamChangeApproval();
        approval.setConfigKey(configKey);
        approval.setOldValue(oldValue == null ? "" : oldValue);
        approval.setNewValue(newValue);
        approval.setInitiatorId(initiatorId);
        approval.setStatus(0);
        approval.setApproverSignatures("[]");
        approval.setSignedAdminIds("[]");
        approval.setRemark(remark);
        paramChangeApprovalMapper.insert(approval);
        log.info("参数变更审批提交 id={} key={} oldValue={} initiator={}",
                approval.getId(), configKey, approval.getOldValue(), initiatorId);
        return approval;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParamChangeApproval approve(Long approvalId, Long adminId, String signature) {
        String lockKey = "lsc:admin:param-approve:" + approvalId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BizException("审批签名并发，请稍后重试");
            }
            ParamChangeApproval approval = paramChangeApprovalMapper.selectById(approvalId);
            if (approval == null) {
                throw new BizException(404, "审批单不存在");
            }
            if (approval.getStatus() != 0) {
                throw new BizException("审批单已处理，无法重复签名");
            }
            // 校验签名管理员是否已签
            List<Long> signedIds = JSON.parseArray(approval.getSignedAdminIds(), Long.class);
            if (signedIds == null) {
                signedIds = new ArrayList<>();
            }
            if (signedIds.contains(adminId)) {
                throw new BizException("该管理员已签名");
            }
            // 发起人不可签名
            if (approval.getInitiatorId().equals(adminId)) {
                throw new BizException("发起人不可参与签名审批");
            }
            signedIds.add(adminId);
            approval.setSignedAdminIds(JSON.toJSONString(signedIds));
            // 追加签名
            List<String> signatures = JSON.parseArray(approval.getApproverSignatures(), String.class);
            if (signatures == null) {
                signatures = new ArrayList<>();
            }
            signatures.add(signature);
            approval.setApproverSignatures(JSON.toJSONString(signatures));

            // 达到双人签名 -> 进入"通过待生效"状态(status=1)
            if (signedIds.size() >= requiredSignatures) {
                approval.setStatus(1);
                // 链上存证(失败仅记录，不阻塞状态推进；运维通过存证字段为空识别失败案例并补传)
                String evidenceHash = com.lianshengtong.common.utils.EvidenceHashUtil.sha256Hex(approval);
                try {
                    R<String> resp = evidenceFeignClient.saveEvidence("PARAM_CHANGE",
                            String.valueOf(approvalId), evidenceHash);
                    if (resp != null && resp.isSuccess()) {
                        approval.setEvidenceTxHash(resp.getData());
                    } else {
                        log.warn("参数变更存证未成功 approvalId={} resp={}", approvalId, resp);
                    }
                } catch (RuntimeException e) {
                    log.error("参数变更存证异常 approvalId={}", approvalId, e);
                }
                // 配置生效：调用 release-service 更新配置后才能置为 3(已生效)。
                // 当前 release-service 远程调用尚未接入，标记为 1(通过待生效)，
                // 由 release-service 的定时任务或运维人工触发后回写为 3。
                log.info("参数变更审批双签通过 id={} key={} 待 release-service 回写生效",
                        approvalId, approval.getConfigKey());
            }
            paramChangeApprovalMapper.updateById(approval);
            return approval;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("审批签名被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long approvalId, Long adminId, String reason) {
        ParamChangeApproval approval = paramChangeApprovalMapper.selectById(approvalId);
        if (approval == null) {
            throw new BizException(404, "审批单不存在");
        }
        if (approval.getStatus() != 0) {
            throw new BizException("审批单已处理");
        }
        approval.setStatus(2);
        approval.setRemark("拒绝人adminId=" + adminId + " 原因:" + reason);
        paramChangeApprovalMapper.updateById(approval);
        log.info("参数变更审批拒绝 id={}", approvalId);
    }

    @Override
    public IPage<ParamChangeApproval> list(Integer page, Integer size, Integer status) {
        Page<ParamChangeApproval> p = new Page<>(page == null ? 1 : page, size == null ? 20 : size);
        LambdaQueryWrapper<ParamChangeApproval> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(ParamChangeApproval::getStatus, status);
        }
        wrapper.orderByDesc(ParamChangeApproval::getCreatedAt);
        return paramChangeApprovalMapper.selectPage(p, wrapper);
    }
}
