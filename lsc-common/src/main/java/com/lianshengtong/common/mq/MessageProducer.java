package com.lianshengtong.common.mq;

import com.alibaba.fastjson2.JSON;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class MessageProducer {

    private static final Logger log = LoggerFactory.getLogger(MessageProducer.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendEvidenceMessage(EvidenceMessage message) {
        send(RabbitMQConfig.EXCHANGE_EVIDENCE, RabbitMQConfig.RK_EVIDENCE, message);
        log.info("[MQ] 存证消息已发送 batchNo={} type={}", message.getBatchNo(), message.getOperationType());
    }

    public void sendReleaseMessage(String routingKey, Object message) {
        send(RabbitMQConfig.EXCHANGE_RELEASE, routingKey, message);
        log.info("[MQ] 释放任务消息已发送 routingKey={}", routingKey);
    }

    public void sendFirstOrderNotify(FirstOrderMessage message) {
        send(RabbitMQConfig.EXCHANGE_ORDER, RabbitMQConfig.RK_FIRST_ORDER, message);
        log.info("[MQ] 首单通知已发送 orderNo={} consumerId={}", message.getOrderNo(), message.getConsumerId());
    }

    public void sendRiskAlert(RiskAlertMessage message) {
        send(RabbitMQConfig.EXCHANGE_RISK, RabbitMQConfig.RK_RISK_ALERT, message);
        log.info("[MQ] 风控预警已发送 userId={} type={}", message.getUserId(), message.getRiskType());
    }

    public void sendProductAiReview(Long productId, String productJson) {
        send(RabbitMQConfig.EXCHANGE_AI_REVIEW, RabbitMQConfig.RK_PRODUCT_REVIEW, productJson);
        log.info("[MQ] 商品AI审核任务已发送 productId={}", productId);
    }

    public void sendB2bAiVerify(Long orderId, String orderJson) {
        send(RabbitMQConfig.EXCHANGE_AI_REVIEW, RabbitMQConfig.RK_B2B_VERIFY, orderJson);
        log.info("[MQ] B2B AI核验任务已发送 orderId={}", orderId);
    }

    private void send(String exchange, String routingKey, Object message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, JSON.toJSONString(message), msg -> {
            msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return msg;
        });
    }

    // ========== 消息体定义 ==========

    public static class EvidenceMessage implements Serializable {
        private String batchNo;
        private String operationType;
        private String businessId;
        private String dataHash;
        private String rawData;

        public EvidenceMessage() {}
        public EvidenceMessage(String batchNo, String operationType, String businessId, String dataHash, String rawData) {
            this.batchNo = batchNo;
            this.operationType = operationType;
            this.businessId = businessId;
            this.dataHash = dataHash;
            this.rawData = rawData;
        }

        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String v) { this.batchNo = v; }
        public String getOperationType() { return operationType; }
        public void setOperationType(String v) { this.operationType = v; }
        public String getBusinessId() { return businessId; }
        public void setBusinessId(String v) { this.businessId = v; }
        public String getDataHash() { return dataHash; }
        public void setDataHash(String v) { this.dataHash = v; }
        public String getRawData() { return rawData; }
        public void setRawData(String v) { this.rawData = v; }
    }

    public static class FirstOrderMessage implements Serializable {
        private String orderNo;
        private Long consumerId;
        private Long referrerId;
        private Long orderAmount;

        public FirstOrderMessage() {}
        public FirstOrderMessage(String orderNo, Long consumerId, Long referrerId, Long orderAmount) {
            this.orderNo = orderNo;
            this.consumerId = consumerId;
            this.referrerId = referrerId;
            this.orderAmount = orderAmount;
        }

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String v) { this.orderNo = v; }
        public Long getConsumerId() { return consumerId; }
        public void setConsumerId(Long v) { this.consumerId = v; }
        public Long getReferrerId() { return referrerId; }
        public void setReferrerId(Long v) { this.referrerId = v; }
        public Long getOrderAmount() { return orderAmount; }
        public void setOrderAmount(Long v) { this.orderAmount = v; }
    }

    public static class RiskAlertMessage implements Serializable {
        private Long userId;
        private String riskType;
        private String riskDetail;
        private Integer aiRiskLevel;
        private String actionTaken;

        public RiskAlertMessage() {}
        public RiskAlertMessage(Long userId, String riskType, String riskDetail, Integer aiRiskLevel, String actionTaken) {
            this.userId = userId;
            this.riskType = riskType;
            this.riskDetail = riskDetail;
            this.aiRiskLevel = aiRiskLevel;
            this.actionTaken = actionTaken;
        }

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public String getRiskType() { return riskType; }
        public void setRiskType(String v) { this.riskType = v; }
        public String getRiskDetail() { return riskDetail; }
        public void setRiskDetail(String v) { this.riskDetail = v; }
        public Integer getAiRiskLevel() { return aiRiskLevel; }
        public void setAiRiskLevel(Integer v) { this.aiRiskLevel = v; }
        public String getActionTaken() { return actionTaken; }
        public void setActionTaken(String v) { this.actionTaken = v; }
    }
}
