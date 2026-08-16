package com.lianshengtong.common.mq;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ队列/交换机/绑定配置
 * 开启消息持久化、生产者confirm确认机制、消费者手动ACK模式
 */
@Configuration
public class RabbitMQConfig {

    // ========== 交换机 ==========

    /** 存证交换机 - 用于关键操作流水上链 */
    public static final String EXCHANGE_EVIDENCE = "lsc.evidence.exchange";
    /** 释放任务交换机 */
    public static final String EXCHANGE_RELEASE = "lsc.release.exchange";
    /** 订单事件交换机 */
    public static final String EXCHANGE_ORDER = "lsc.order.exchange";
    /** 风控事件交换机 */
    public static final String EXCHANGE_RISK = "lsc.risk.exchange";
    /** AI审核任务交换机 */
    public static final String EXCHANGE_AI_REVIEW = "lsc.ai.review.exchange";

    // ========== 队列 ==========

    /** 存证队列 */
    public static final String QUEUE_EVIDENCE = "lsc.evidence.queue";
    /** 存证故障补偿队列 */
    public static final String QUEUE_EVIDENCE_FAILOVER = "lsc.evidence.failover.queue";
    /** 每日释放队列 */
    public static final String QUEUE_DAILY_RELEASE = "lsc.daily.release.queue";
    /** 过期转回队列 */
    public static final String QUEUE_EXPIRE_TRANSFER = "lsc.expire.transfer.queue";
    /** 推广奖励补发队列 */
    public static final String QUEUE_PROMOTION_FILL = "lsc.promotion.fill.queue";
    /** 首单通知队列 */
    public static final String QUEUE_FIRST_ORDER_NOTIFY = "lsc.first.order.notify.queue";
    /** 风控预警队列 */
    public static final String QUEUE_RISK_ALERT = "lsc.risk.alert.queue";
    /** 商品AI审核队列 */
    public static final String QUEUE_PRODUCT_AI_REVIEW = "lsc.product.ai.review.queue";
    /** B2B订单AI核验队列 */
    public static final String QUEUE_B2B_AI_VERIFY = "lsc.b2b.ai.verify.queue";

    // ========== 路由键 ==========

    public static final String RK_EVIDENCE = "lsc.evidence";
    public static final String RK_EVIDENCE_FAILOVER = "lsc.evidence.failover";
    public static final String RK_DAILY_RELEASE = "lsc.release.daily";
    public static final String RK_EXPIRE_TRANSFER = "lsc.release.expire";
    public static final String RK_PROMOTION_FILL = "lsc.promotion.fill";
    public static final String RK_FIRST_ORDER = "lsc.order.first";
    public static final String RK_RISK_ALERT = "lsc.risk.alert";
    public static final String RK_PRODUCT_REVIEW = "lsc.ai.product.review";
    public static final String RK_B2B_VERIFY = "lsc.ai.b2b.verify";

    // ========== 存证交换机 ==========

    @Bean
    public DirectExchange evidenceExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_EVIDENCE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue evidenceQueue() {
        return QueueBuilder.durable(QUEUE_EVIDENCE)
                .withArgument("x-message-ttl", 86400000) // 24小时TTL
                .withArgument("x-dead-letter-exchange", EXCHANGE_EVIDENCE)
                .withArgument("x-dead-letter-routing-key", RK_EVIDENCE_FAILOVER)
                .build();
    }

    @Bean
    public Binding evidenceBinding() {
        return BindingBuilder.bind(evidenceQueue())
                .to(evidenceExchange())
                .with(RK_EVIDENCE);
    }

    // ========== 存证故障补偿队列 ==========

    @Bean
    public Queue evidenceFailoverQueue() {
        return QueueBuilder.durable(QUEUE_EVIDENCE_FAILOVER).build();
    }

    @Bean
    public Binding evidenceFailoverBinding() {
        return BindingBuilder.bind(evidenceFailoverQueue())
                .to(evidenceExchange())
                .with(RK_EVIDENCE_FAILOVER);
    }

    // ========== 释放任务交换机 ==========

    @Bean
    public DirectExchange releaseExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_RELEASE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue dailyReleaseQueue() {
        return QueueBuilder.durable(QUEUE_DAILY_RELEASE).build();
    }

    @Bean
    public Binding dailyReleaseBinding() {
        return BindingBuilder.bind(dailyReleaseQueue())
                .to(releaseExchange())
                .with(RK_DAILY_RELEASE);
    }

    @Bean
    public Queue expireTransferQueue() {
        return QueueBuilder.durable(QUEUE_EXPIRE_TRANSFER).build();
    }

    @Bean
    public Binding expireTransferBinding() {
        return BindingBuilder.bind(expireTransferQueue())
                .to(releaseExchange())
                .with(RK_EXPIRE_TRANSFER);
    }

    // ========== 订单事件交换机 ==========

    @Bean
    public DirectExchange orderExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_ORDER)
                .durable(true)
                .build();
    }

    @Bean
    public Queue firstOrderNotifyQueue() {
        return QueueBuilder.durable(QUEUE_FIRST_ORDER_NOTIFY).build();
    }

    @Bean
    public Binding firstOrderBinding() {
        return BindingBuilder.bind(firstOrderNotifyQueue())
                .to(orderExchange())
                .with(RK_FIRST_ORDER);
    }

    @Bean
    public Queue promotionFillQueue() {
        return QueueBuilder.durable(QUEUE_PROMOTION_FILL).build();
    }

    @Bean
    public Binding promotionFillBinding() {
        return BindingBuilder.bind(promotionFillQueue())
                .to(orderExchange())
                .with(RK_PROMOTION_FILL);
    }

    // ========== 风控事件交换机 ==========

    @Bean
    public DirectExchange riskExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_RISK)
                .durable(true)
                .build();
    }

    @Bean
    public Queue riskAlertQueue() {
        return QueueBuilder.durable(QUEUE_RISK_ALERT).build();
    }

    @Bean
    public Binding riskAlertBinding() {
        return BindingBuilder.bind(riskAlertQueue())
                .to(riskExchange())
                .with(RK_RISK_ALERT);
    }

    // ========== AI审核任务交换机 ==========

    @Bean
    public DirectExchange aiReviewExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_AI_REVIEW)
                .durable(true)
                .build();
    }

    @Bean
    public Queue productAiReviewQueue() {
        return QueueBuilder.durable(QUEUE_PRODUCT_AI_REVIEW).build();
    }

    @Bean
    public Binding productAiReviewBinding() {
        return BindingBuilder.bind(productAiReviewQueue())
                .to(aiReviewExchange())
                .with(RK_PRODUCT_REVIEW);
    }

    @Bean
    public Queue b2bAiVerifyQueue() {
        return QueueBuilder.durable(QUEUE_B2B_AI_VERIFY).build();
    }

    @Bean
    public Binding b2bAiVerifyBinding() {
        return BindingBuilder.bind(b2bAiVerifyQueue())
                .to(aiReviewExchange())
                .with(RK_B2B_VERIFY);
    }
}
