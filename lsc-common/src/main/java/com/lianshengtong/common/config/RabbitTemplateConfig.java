package com.lianshengtong.common.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RabbitMQ模板配置
 * 开启生产者confirm确认机制 + 消费者手动ACK
 */
@Configuration
public class RabbitTemplateConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitTemplateConfig.class);

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // 使用JSON序列化
        template.setMessageConverter(jackson2JsonMessageConverter());
        // 开启confirm确认机制
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                // 消息未到达交换机，记录日志(业务层有故障表兜底)
                log.error("[MQ Confirm] 消息发送失败 cause={}", cause);
            }
        });
        // 开启Returns机制(消息到达交换机但无队列接收时触发)
        template.setReturnsCallback(returned -> {
            log.error("[MQ Return] 消息被退回 exchange={} routingKey={} replyCode={} replyText={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText());
        });
        return template;
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
