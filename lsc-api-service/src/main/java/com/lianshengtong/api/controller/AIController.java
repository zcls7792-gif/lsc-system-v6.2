package com.lianshengtong.api.controller;

import com.lianshengtong.api.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    @GetMapping("/quick-questions")
    public ApiResponse<List<Map<String, Object>>> quickQuestions() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(Map.of("id", 1, "question", "什么是LSC消费权益？"));
        list.add(Map.of("id", 2, "question", "如何申请核销？"));
        list.add(Map.of("id", 3, "question", "LSC和人民币的兑换比例？"));
        list.add(Map.of("id", 4, "question", "如何提升商家信用分？"));
        list.add(Map.of("id", 5, "question", "B2B交易流程是什么？"));
        return ApiResponse.success(list);
    }

    @PostMapping("/customer-service")
    public ApiResponse<Map<String, Object>> customerService(@RequestBody Map<String, Object> body) {
        String question = (String) body.getOrDefault("question", "");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("reply", "您好！关于「" + question + "」，LSC消费权益系统是一个基于区块链存证的消费权益平台，"
                + "支持人民币与LSC 1:1混合支付。商家可通过核销获得LSC释放，消费者可用LSC抵扣消费。");
        r.put("suggestions", List.of("查看LSC账户", "了解核销流程", "联系人工客服"));
        return ApiResponse.success(r);
    }
}
