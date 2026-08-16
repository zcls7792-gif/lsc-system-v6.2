package com.lianshengtong.reconciliation.feign;

import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 存证服务 Feign 客户端 - 对账结果哈希上链
 */
@FeignClient(name = "lsc-evidence-service", contextId = "reconciliationEvidenceClient")
public interface EvidenceFeignClient {

    /**
     * 提交存证(哈希上链)
     *
     * @param bizType    业务类型
     * @param bizId      业务ID
     * @param dataHash   数据哈希(SHA-256)
     * @return 链上交易哈希
     */
    @PostMapping("/api/evidence/save")
    R<String> saveEvidence(@RequestParam("bizType") String bizType,
                           @RequestParam("bizId") String bizId,
                           @RequestParam("dataHash") String dataHash);
}
