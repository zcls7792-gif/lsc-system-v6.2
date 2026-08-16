package com.lianshengtong.aigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * AI客服问答DTO
 * <p>
 * 智能客服问答，支持多轮对话与知识库检索。
 * </p>
 */
public class AiCustomerServiceDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 会话ID */
        private String sessionId;

        /** 用户ID */
        private Long userId;

        /** 用户问题 */
        private String question;

        /** 历史对话上下文(最近N轮) */
        private List<Round> context;

        /** 业务场景(订单/核销/释放/退款) */
        private String scene;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 会话ID */
        private String sessionId;

        /** AI回答 */
        private String answer;

        /** 命中知识库条目ID列表 */
        private List<String> hitKnowledgeIds;

        /** 是否需转人工 */
        private Boolean needHuman;

        /** 置信度 0~1 */
        private Double confidence;

        /** 是否降级(降级时建议转人工) */
        private Boolean fallback;

        /** 回答说明 */
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Round implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 角色 user/assistant */
        private String role;

        /** 内容 */
        private String content;
    }
}
