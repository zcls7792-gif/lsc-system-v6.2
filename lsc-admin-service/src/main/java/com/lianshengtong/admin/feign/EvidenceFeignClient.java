package com.lianshengtong.admin.feign;

import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 存证服务 Feign 客户端 - 管理后台存证查询与存证
 */
@FeignClient(name = "lsc-evidence-service", contextId = "adminEvidenceClient")
public interface EvidenceFeignClient {

    /**
     * 保存存证(参数变更上链)
     */
    @PostMapping("/api/evidence/save")
    R<String> saveEvidence(@RequestParam("bizType") String bizType,
                           @RequestParam("bizId") String bizId,
                           @RequestParam("dataHash") String dataHash);

    /**
     * 查询存证记录
     */
    @GetMapping("/api/evidence/query")
    R<List<Map<String, Object>>> query(@RequestParam(value = "bizType", required = false) String bizType,
                                       @RequestParam(value = "bizId", required = false) String bizId);
}
