package com.lianshengtong.release.alert;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AlertChannel 接口单元测试")
class AlertChannelTest {

    @Test
    @DisplayName("AlertChannel: 默认name返回类简名")
    void alertChannel_defaultName_returnsSimpleName() {
        AlertChannel channel = new TestAlertChannel();
        assertEquals("TestAlertChannel", channel.name());
    }

    @Test
    @DisplayName("AlertChannel: 自定义name覆盖默认实现")
    void alertChannel_customName_overridesDefault() {
        AlertChannel channel = new AlertChannel() {
            @Override
            public void send(String receivers, String title, String content) {
            }

            @Override
            public String name() {
                return "DingTalkChannel";
            }
        };
        assertEquals("DingTalkChannel", channel.name());
    }

    @Test
    @DisplayName("AlertChannel: send 正确传递参数")
    void alertChannel_send_receivesParams() {
        List<String[]> sentMessages = new ArrayList<>();
        AlertChannel channel = new AlertChannel() {
            @Override
            public void send(String receivers, String title, String content) {
                sentMessages.add(new String[]{receivers, title, content});
            }
        };

        channel.send("admin1,admin2", "告警标题", "告警内容");
        assertEquals(1, sentMessages.size());
        assertEquals("admin1,admin2", sentMessages.get(0)[0]);
        assertEquals("告警标题", sentMessages.get(0)[1]);
        assertEquals("告警内容", sentMessages.get(0)[2]);
    }

    @Test
    @DisplayName("AlertChannel: send 多接收人逗号分隔")
    void alertChannel_send_multipleReceivers() {
        List<String> receiversList = new ArrayList<>();
        AlertChannel channel = new AlertChannel() {
            @Override
            public void send(String receivers, String title, String content) {
                receiversList.add(receivers);
            }
        };

        channel.send("13800000000,13900000000,13700000000", "标题", "内容");
        assertEquals(1, receiversList.size());
        assertTrue(receiversList.get(0).contains(","));
    }

    @Test
    @DisplayName("AlertChannel: send 空接收人也能处理")
    void alertChannel_send_emptyReceivers() {
        AlertChannel channel = new AlertChannel() {
            @Override
            public void send(String receivers, String title, String content) {
                assertNotNull(receivers);
            }
        };
        assertDoesNotThrow(() -> channel.send("", "标题", "内容"));
    }

    @Test
    @DisplayName("AlertChannel: send 长内容正确传递")
    void alertChannel_send_longContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("告警内容片段");
        }
        String longContent = sb.toString();

        AlertChannel channel = new AlertChannel() {
            @Override
            public void send(String receivers, String title, String content) {
                assertEquals(longContent, content);
            }
        };
        assertDoesNotThrow(() -> channel.send("admin", "标题", longContent));
    }

    @Test
    @DisplayName("AlertChannel: 匿名实现类name能正常返回（可能为空字符串）")
    void alertChannel_anonymousImpl_nameReturns() {
        AlertChannel channel = new AlertChannel() {
            @Override
            public void send(String receivers, String title, String content) {
            }
        };
        String name = channel.name();
        assertNotNull(name);
    }

    @Test
    @DisplayName("AlertChannel: 两个不同实现name不同")
    void alertChannel_twoImpls_namesDiffer() {
        AlertChannel channel1 = new SmsAlertChannel();
        AlertChannel channel2 = new EmailAlertChannel();
        assertNotEquals(channel1.name(), channel2.name());
    }

    static class TestAlertChannel implements AlertChannel {
        @Override
        public void send(String receivers, String title, String content) {
        }
    }

    static class SmsAlertChannel implements AlertChannel {
        @Override
        public void send(String receivers, String title, String content) {
        }
    }

    static class EmailAlertChannel implements AlertChannel {
        @Override
        public void send(String receivers, String title, String content) {
        }
    }
}