package com.lianshengtong.common.mq;

import com.lianshengtong.common.mq.MessageProducer.EvidenceMessage;
import com.lianshengtong.common.mq.MessageProducer.FirstOrderMessage;
import com.lianshengtong.common.mq.MessageProducer.RiskAlertMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("消息生产者单元测试")
class MessageProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private MessageProducer messageProducer;

    @Test
    @DisplayName("sendEvidenceMessage: 成功发送存证消息")
    void sendEvidenceMessage_success() {
        EvidenceMessage msg = new EvidenceMessage("batch-001", "CREATE", "biz-1", "hash-abc", "raw-data");
        messageProducer.sendEvidenceMessage(msg);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_EVIDENCE),
                eq(RabbitMQConfig.RK_EVIDENCE),
                anyString(),
                any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("sendReleaseMessage: 成功发送释放任务消息")
    void sendReleaseMessage_success() {
        Object payload = new Object();
        messageProducer.sendReleaseMessage("lsc.release.daily", payload);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_RELEASE),
                eq("lsc.release.daily"),
                anyString(),
                any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("sendFirstOrderNotify: 成功发送首单通知")
    void sendFirstOrderNotify_success() {
        FirstOrderMessage msg = new FirstOrderMessage("ORD-001", 1001L, 2002L, 5000L);
        messageProducer.sendFirstOrderNotify(msg);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_ORDER),
                eq(RabbitMQConfig.RK_FIRST_ORDER),
                anyString(),
                any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("sendRiskAlert: 成功发送风控预警")
    void sendRiskAlert_success() {
        RiskAlertMessage msg = new RiskAlertMessage(1L, "SUSPICIOUS", "detail", 3, "BLOCKED");
        messageProducer.sendRiskAlert(msg);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_RISK),
                eq(RabbitMQConfig.RK_RISK_ALERT),
                anyString(),
                any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("sendProductAiReview: 成功发送商品AI审核任务")
    void sendProductAiReview_success() {
        messageProducer.sendProductAiReview(100L, "{\"productId\":100}");
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_AI_REVIEW),
                eq(RabbitMQConfig.RK_PRODUCT_REVIEW),
                anyString(),
                any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("sendB2bAiVerify: 成功发送B2B AI核验任务")
    void sendB2bAiVerify_success() {
        messageProducer.sendB2bAiVerify(200L, "{\"orderId\":200}");
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_AI_REVIEW),
                eq(RabbitMQConfig.RK_B2B_VERIFY),
                anyString(),
                any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("send: 消息发送使用持久化模式")
    void send_persistentDeliveryMode() {
        MessagePostProcessor processor = mock(MessagePostProcessor.class);
        Message message = mock(Message.class);
        MessageProperties props = new MessageProperties();
        when(message.getMessageProperties()).thenReturn(props);
        processor.postProcessMessage(message);

        messageProducer.sendEvidenceMessage(new EvidenceMessage("b", "t", "id", "h", "r"));

        verify(rabbitTemplate).convertAndSend(
                anyString(), anyString(), anyString(), any(MessagePostProcessor.class));
    }

    @Nested
    @DisplayName("EvidenceMessage 测试")
    class EvidenceMessageTests {

        @Test
        @DisplayName("构造器: 全参构造正确")
        void constructor_full() {
            EvidenceMessage msg = new EvidenceMessage("b1", "CREATE", "biz1", "hash1", "raw1");
            assertEquals("b1", msg.getBatchNo());
            assertEquals("CREATE", msg.getOperationType());
            assertEquals("biz1", msg.getBusinessId());
            assertEquals("hash1", msg.getDataHash());
            assertEquals("raw1", msg.getRawData());
        }

        @Test
        @DisplayName("构造器: 无参构造默认值为null")
        void constructor_noArg() {
            EvidenceMessage msg = new EvidenceMessage();
            assertNull(msg.getBatchNo());
            assertNull(msg.getOperationType());
            assertNull(msg.getBusinessId());
            assertNull(msg.getDataHash());
            assertNull(msg.getRawData());
        }

        @Test
        @DisplayName("Setter/Getter: 所有字段可设置和获取")
        void setterGetter_works() {
            EvidenceMessage msg = new EvidenceMessage();
            msg.setBatchNo("b");
            msg.setOperationType("op");
            msg.setBusinessId("bid");
            msg.setDataHash("hash");
            msg.setRawData("raw");

            assertEquals("b", msg.getBatchNo());
            assertEquals("op", msg.getOperationType());
            assertEquals("bid", msg.getBusinessId());
            assertEquals("hash", msg.getDataHash());
            assertEquals("raw", msg.getRawData());
        }
    }

    @Nested
    @DisplayName("FirstOrderMessage 测试")
    class FirstOrderMessageTests {

        @Test
        @DisplayName("构造器: 全参构造正确")
        void constructor_full() {
            FirstOrderMessage msg = new FirstOrderMessage("ORD-1", 100L, 200L, 3000L);
            assertEquals("ORD-1", msg.getOrderNo());
            assertEquals(100L, msg.getConsumerId());
            assertEquals(200L, msg.getReferrerId());
            assertEquals(3000L, msg.getOrderAmount());
        }

        @Test
        @DisplayName("构造器: 无参构造默认值为null")
        void constructor_noArg() {
            FirstOrderMessage msg = new FirstOrderMessage();
            assertNull(msg.getOrderNo());
            assertNull(msg.getConsumerId());
            assertNull(msg.getReferrerId());
            assertNull(msg.getOrderAmount());
        }

        @Test
        @DisplayName("Setter/Getter: 所有字段可设置和获取")
        void setterGetter_works() {
            FirstOrderMessage msg = new FirstOrderMessage();
            msg.setOrderNo("O");
            msg.setConsumerId(1L);
            msg.setReferrerId(2L);
            msg.setOrderAmount(3L);

            assertEquals("O", msg.getOrderNo());
            assertEquals(1L, msg.getConsumerId());
            assertEquals(2L, msg.getReferrerId());
            assertEquals(3L, msg.getOrderAmount());
        }
    }

    @Nested
    @DisplayName("RiskAlertMessage 测试")
    class RiskAlertMessageTests {

        @Test
        @DisplayName("构造器: 全参构造正确")
        void constructor_full() {
            RiskAlertMessage msg = new RiskAlertMessage(1L, "FRAUD", "detail-info", 5, "BLOCKED");
            assertEquals(1L, msg.getUserId());
            assertEquals("FRAUD", msg.getRiskType());
            assertEquals("detail-info", msg.getRiskDetail());
            assertEquals(5, msg.getAiRiskLevel());
            assertEquals("BLOCKED", msg.getActionTaken());
        }

        @Test
        @DisplayName("构造器: 无参构造默认值为null")
        void constructor_noArg() {
            RiskAlertMessage msg = new RiskAlertMessage();
            assertNull(msg.getUserId());
            assertNull(msg.getRiskType());
            assertNull(msg.getRiskDetail());
            assertNull(msg.getAiRiskLevel());
            assertNull(msg.getActionTaken());
        }

        @Test
        @DisplayName("Setter/Getter: 所有字段可设置和获取")
        void setterGetter_works() {
            RiskAlertMessage msg = new RiskAlertMessage();
            msg.setUserId(100L);
            msg.setRiskType("R");
            msg.setRiskDetail("D");
            msg.setAiRiskLevel(3);
            msg.setActionTaken("A");

            assertEquals(100L, msg.getUserId());
            assertEquals("R", msg.getRiskType());
            assertEquals("D", msg.getRiskDetail());
            assertEquals(3, msg.getAiRiskLevel());
            assertEquals("A", msg.getActionTaken());
        }
    }
}