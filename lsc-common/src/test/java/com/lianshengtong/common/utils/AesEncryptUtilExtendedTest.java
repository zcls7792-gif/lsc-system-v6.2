package com.lianshengtong.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AES加密工具增强测试
 * 覆盖率提升目标: 87.2% → 95%+
 */
class AesEncryptUtilExtendedTest {

    @Nested
    @DisplayName("encrypt 加密")
    class EncryptTests {

        @Test
        @DisplayName("正常字符串加密")
        void encryptNormalString() {
            String result = AesEncryptUtil.encrypt("Hello World");
            assertNotNull(result);
            assertNotEquals("Hello World", result);
        }

        @Test
        @DisplayName("加密结果Base64编码")
        void encryptResultIsBase64() {
            String result = AesEncryptUtil.encrypt("test");
            assertTrue(result.matches("^[A-Za-z0-9+/=]+$"));
        }

        @Test
        @DisplayName("空字符串加密")
        void encryptEmptyString() {
            String result = AesEncryptUtil.encrypt("");
            assertNotNull(result);
        }

        @Test
        @DisplayName("null输入返回null")
        void encryptNullReturnsNull() {
            assertNull(AesEncryptUtil.encrypt(null));
        }

        @Test
        @DisplayName("中文字符加密")
        void encryptChineseChars() {
            String plaintext = "你好世界";
            String encrypted = AesEncryptUtil.encrypt(plaintext);
            assertNotNull(encrypted);
        }

        @Test
        @DisplayName("特殊字符加密")
        void encryptSpecialChars() {
            String plaintext = "!@#$%^&*()_+-=[]{}|;':\",./<>?";
            String encrypted = AesEncryptUtil.encrypt(plaintext);
            assertNotNull(encrypted);
        }

        @Test
        @DisplayName("长文本加密")
        void encryptLongText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) sb.append("A");
            String encrypted = AesEncryptUtil.encrypt(sb.toString());
            assertNotNull(encrypted);
        }

        @Test
        @DisplayName("加密结果每次不同(语义安全)")
        void encryptIsSemanticallySecure() {
            String plaintext = "same text";
            String enc1 = AesEncryptUtil.encrypt(plaintext);
            String enc2 = AesEncryptUtil.encrypt(plaintext);
            assertNotEquals(enc1, enc2, "相同明文应产生不同密文");
        }
    }

    @Nested
    @DisplayName("decrypt 解密")
    class DecryptTests {

        @Test
        @DisplayName("解密正常密文")
        void decryptNormalCipher() {
            String encrypted = AesEncryptUtil.encrypt("Hello World");
            String decrypted = AesEncryptUtil.decrypt(encrypted);
            assertEquals("Hello World", decrypted);
        }

        @Test
        @DisplayName("解密中文字符")
        void decryptChineseChars() {
            String plaintext = "你好世界测试加密";
            String encrypted = AesEncryptUtil.encrypt(plaintext);
            String decrypted = AesEncryptUtil.decrypt(encrypted);
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("解密特殊字符")
        void decryptSpecialChars() {
            String plaintext = "email@domain.com|phone:138-0000-0000";
            String encrypted = AesEncryptUtil.encrypt(plaintext);
            String decrypted = AesEncryptUtil.decrypt(encrypted);
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("解密空字符串")
        void decryptEmptyString() {
            String encrypted = AesEncryptUtil.encrypt("");
            String decrypted = AesEncryptUtil.decrypt(encrypted);
            assertEquals("", decrypted);
        }

        @Test
        @DisplayName("null输入返回null")
        void decryptNullReturnsNull() {
            assertNull(AesEncryptUtil.decrypt(null));
        }

        @Test
        @DisplayName("无效Base64抛异常")
        void decryptInvalidBase64() {
            assertThrows(RuntimeException.class,
                () -> AesEncryptUtil.decrypt("not-valid-base64!!!"));
        }

        @Test
        @DisplayName("密文长度不足抛异常")
        void decryptTooShortCipher() {
            assertThrows(IllegalArgumentException.class,
                () -> AesEncryptUtil.decrypt("a"));
        }

        @Test
        @DisplayName("加密解密往返一致")
        void encryptDecryptRoundTrip() {
            String[] testCases = {
                "13800138000",
                "110101199001011234",
                "test@example.com",
                "user:admin;role:super",
                "{\"key\":\"value\"}",
                "SELECT * FROM users WHERE id=1; DROP TABLE users;"
            };

            for (String plaintext : testCases) {
                String encrypted = AesEncryptUtil.encrypt(plaintext);
                String decrypted = AesEncryptUtil.decrypt(encrypted);
                assertEquals(plaintext, decrypted, "往返失败: " + plaintext);
            }
        }
    }

    @Nested
    @DisplayName("脱敏方法")
    class MaskTests {

        @Nested
        @DisplayName("maskMobile 手机号脱敏")
        class MaskMobileTests {

            @Test
            void maskNormalMobile() {
                assertEquals("138****5678", AesEncryptUtil.maskMobile("13800135678"));
            }

            @Test
            void maskShortMobile() {
                assertEquals("138", AesEncryptUtil.maskMobile("138"));
            }

            @Test
            void maskNullMobile() {
                assertNull(AesEncryptUtil.maskMobile(null));
            }

            @Test
            void maskEmptyMobile() {
                assertEquals("", AesEncryptUtil.maskMobile(""));
            }

            @Test
            void maskBoundaryMobile() {
                assertEquals("138****0001", AesEncryptUtil.maskMobile("1380001"));
            }
        }

        @Nested
        @DisplayName("maskIdCard 身份证脱敏")
        class MaskIdCardTests {

            @Test
            void maskNormalIdCard() {
                String result = AesEncryptUtil.maskIdCard("110101199001011234");
                assertEquals("110***********1234", result);
            }

            @Test
            void maskShortIdCard() {
                String input = "123456789";
                assertEquals(input, AesEncryptUtil.maskIdCard(input));
            }

            @Test
            void maskNullIdCard() {
                assertNull(AesEncryptUtil.maskIdCard(null));
            }

            @Test
            void maskEmptyIdCard() {
                assertEquals("", AesEncryptUtil.maskIdCard(""));
            }

            @Test
            void mask18DigitIdCard() {
                String result = AesEncryptUtil.maskIdCard("110101199001011234");
                assertTrue(result.contains("*"));
                assertTrue(result.endsWith("1234"));
            }
        }

        @Nested
        @DisplayName("maskName 姓名脱敏")
        class MaskNameTests {

            @Test
            void maskTwoCharName() {
                assertEquals("张*", AesEncryptUtil.maskName("张三"));
            }

            @Test
            void maskThreeCharName() {
                assertEquals("张*明", AesEncryptUtil.maskName("张小明"));
            }

            @Test
            void maskFourCharName() {
                assertEquals("张***四", AesEncryptUtil.maskName("张一二三四"));
            }

            @Test
            void maskSingleName() {
                assertEquals("张", AesEncryptUtil.maskName("张"));
            }

            @Test
            void maskNullName() {
                assertNull(AesEncryptUtil.maskName(null));
            }

            @Test
            void maskEmptyName() {
                assertEquals("", AesEncryptUtil.maskName(""));
            }
        }
    }

    @Nested
    @DisplayName("安全性测试")
    class SecurityTests {

        @Test
        @DisplayName("相同明文产生不同密文(IV随机)")
        void randomIvProducesDifferentOutput() {
            String plaintext = "sensitive data";
            String[] results = new String[10];
            for (int i = 0; i < 10; i++) {
                results[i] = AesEncryptUtil.encrypt(plaintext);
            }
            for (int i = 0; i < results.length; i++) {
                for (int j = i + 1; j < results.length; j++) {
                    assertNotEquals(results[i], results[j],
                        "第" + i + "次和第" + j + "次加密结果不应相同");
                }
            }
        }

        @Test
        @DisplayName("密钥派生一致性")
        void keyDerivationIsConsistent() {
            String plaintext = "test";
            String enc1 = AesEncryptUtil.encrypt(plaintext);
            String dec1 = AesEncryptUtil.decrypt(enc1);
            assertEquals(plaintext, dec1);
        }
    }
}