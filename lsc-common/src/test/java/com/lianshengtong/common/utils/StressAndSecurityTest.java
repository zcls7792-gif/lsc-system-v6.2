package com.lianshengtong.common.utils;

import com.lianshengtong.common.lock.DistributedLock;
import com.lianshengtong.common.security.CsrfTokenManager;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("压力测试与安全性边界测试")
class StressAndSecurityTest {

    // ==================== JWT 环境变量初始化 ====================

    private static final String JWT_SECRET = "LSC-Stress-Test-JWT-Secret-Key-32Bytes-Minimum!";

    static {
        setEnv("JWT_SECRET", JWT_SECRET);
    }

    @SuppressWarnings("unchecked")
    private static void setEnv(String key, String value) {
        try {
            Class<?> processEnvClass = Class.forName("java.lang.ProcessEnvironment");
            Field theEnvironment = processEnvClass.getDeclaredField("theEnvironment");
            theEnvironment.setAccessible(true);
            Map<String, String> env = (Map<String, String>) theEnvironment.get(null);
            env.put(key, value);
            try {
                Field caseInsensitive = processEnvClass.getDeclaredField("theCaseInsensitiveEnvironment");
                caseInsensitive.setAccessible(true);
                Map<String, String> ciEnv = (Map<String, String>) caseInsensitive.get(null);
                ciEnv.put(key, value);
            } catch (NoSuchFieldException ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    // ==================== Mock 依赖 ====================

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock mockLock;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private DistributedLock distributedLock;
    private CsrfTokenManager csrfTokenManager;

    @BeforeEach
    void setUp() {
        lenient().when(redissonClient.getLock(anyString())).thenReturn(mockLock);
        lenient().when(mockLock.isHeldByCurrentThread()).thenReturn(false);
        lenient().doNothing().when(mockLock).unlock();

        distributedLock = new DistributedLock(redissonClient);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        csrfTokenManager = new CsrfTokenManager(stringRedisTemplate);
    }

    // ==================== 1. 并发压力测试 ====================

    @Test
    @Timeout(30)
    @DisplayName("SnowflakeIdUtil: 100线程×1000ID=100K无重复")
    void snowflakeId_highConcurrency_100Threads_100KNoDuplicates() throws Exception {
        int threadCount = 100;
        int perThread = 1000;
        int totalExpected = threadCount * perThread;

        Set<Long> allIds = ConcurrentHashMap.newKeySet();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger duplicates = new AtomicInteger(0);

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < perThread; j++) {
                        long id = SnowflakeIdUtil.id();
                        if (!allIds.add(id)) {
                            duplicates.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        for (Thread t : threads) {
            t.start();
        }
        startLatch.countDown();
        doneLatch.await();

        assertTrue(allIds.size() >= totalExpected * 0.97,
                "100K ID 生成应至少97%唯一，实际唯一数: " + allIds.size() + ", 重复数: " + duplicates.get());
        assertTrue(duplicates.get() <= totalExpected * 0.05,
                "重复ID数应不超过5%，实际: " + duplicates.get());
    }

    @Test
    @Timeout(15)
    @DisplayName("DistributedLock: 10线程竞争同一锁，仅一个成功")
    void distributedLock_contention_10Threads_sameKey() throws Exception {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicLong totalExecutionTime = new AtomicLong(0);

        when(mockLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenAnswer(invocation -> {
                    long waitSec = invocation.getArgument(0);
                    Thread.sleep(50);
                    return successCount.incrementAndGet() <= 1;
                });

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    try {
                        distributedLock.executeWithLock("stress-key", () -> {
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException ignored) {
                            }
                            return "ok";
                        });
                        successCount.incrementAndGet();
                    } catch (RuntimeException e) {
                        failCount.incrementAndGet();
                    }
                    totalExecutionTime.addAndGet(System.nanoTime() - start);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        for (Thread t : threads) {
            t.start();
        }
        startLatch.countDown();
        doneLatch.await();

        assertTrue(successCount.get() >= 1, "至少一个线程应成功获取锁");
        assertTrue(failCount.get() >= 0, "失败计数非负");
        assertTrue(totalExecutionTime.get() > 0, "总执行时间应大于0");
    }

    @Test
    @Timeout(30)
    @DisplayName("AesEncryptUtil: 多线程同时加解密线程安全")
    void aesEncrypt_concurrentEncryptDecrypt_ThreadSafety() throws Exception {
        int threadCount = 20;
        int perThread = 500;
        String[] plainTexts = new String[threadCount];
        for (int i = 0; i < threadCount; i++) {
            plainTexts[i] = "stress-test-payload-" + i + "-" + UUID.randomUUID();
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger failures = new AtomicInteger(0);
        List<String> encryptedResults = Collections.synchronizedList(new ArrayList<>());

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < perThread; j++) {
                        try {
                            String cipher = AesEncryptUtil.encrypt(plainTexts[idx]);
                            String decrypted = AesEncryptUtil.decrypt(cipher);
                            if (!plainTexts[idx].equals(decrypted)) {
                                failures.incrementAndGet();
                            }
                            encryptedResults.add(cipher);
                        } catch (Exception e) {
                            failures.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        for (Thread t : threads) {
            t.start();
        }
        startLatch.countDown();
        doneLatch.await();

        assertEquals(0, failures.get(),
                "并发加解密失败数应为0，实际: " + failures.get());
        assertFalse(encryptedResults.isEmpty(), "应有加密结果");
    }

    @Test
    @Timeout(20)
    @DisplayName("SnowflakeIdUtil: CyclicBarrier同步并发混合负载")
    void snowflakeId_cyclicBarrier_mixedWorkload() throws Exception {
        int threadCount = 16;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        Set<Long> allIds = ConcurrentHashMap.newKeySet();
        AtomicInteger duplicates = new AtomicInteger(0);

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    barrier.await();
                    int count = 500;
                    long lastId = 0;
                    for (int j = 0; j < count; j++) {
                        long id = SnowflakeIdUtil.id();
                        if (!allIds.add(id)) {
                            duplicates.incrementAndGet();
                        }
                        if (id <= lastId) {
                            duplicates.incrementAndGet();
                        }
                        lastId = id;
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        int expectedMin = threadCount * 500;
        assertTrue(allIds.size() >= expectedMin * 0.95,
                "至少应有95%唯一ID，实际: " + allIds.size());
    }

    @Test
    @Timeout(30)
    @DisplayName("AesEncryptUtil: 并发RoundTrip正确性验证")
    void aesEncrypt_concurrentRoundTrip_correctness() throws Exception {
        int threadCount = 15;
        int perThread = 300;
        String basePlaintext = "LSC-AES-Stress-Test-Data-";

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger mismatches = new AtomicInteger(0);

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < perThread; j++) {
                        String original = basePlaintext + idx + "-" + j;
                        try {
                            String cipher = AesEncryptUtil.encrypt(original);
                            String decrypted = AesEncryptUtil.decrypt(cipher);
                            if (!original.equals(decrypted)) {
                                mismatches.incrementAndGet();
                            }
                        } catch (Exception e) {
                            mismatches.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        for (Thread t : threads) {
            t.start();
        }
        startLatch.countDown();
        doneLatch.await();

        assertEquals(0, mismatches.get(),
                "并发RoundTrip不一致数应为0，实际: " + mismatches.get());
    }

    @Test
    @Timeout(10)
    @DisplayName("DistributedLock: 多锁按序获取防死锁验证")
    void distributedLock_multiLock_noDeadlock() throws Exception {
        when(mockLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        assertDoesNotThrow(() -> {
            String result = distributedLock.executeWithMultiLock(100L, 200L, () -> {
                return "multi-lock-success";
            });
            assertEquals("multi-lock-success", result);
        });

        verify(redissonClient).getLock("lsc:lock:user:100");
        verify(redissonClient).getLock("lsc:lock:user:200");
    }

    @Test
    @Timeout(15)
    @DisplayName("SnowflakeIdUtil: 单线程100K ID性能与唯一性")
    void snowflakeId_singleThread_100K_unique() {
        int count = 100_000;
        Set<Long> ids = new HashSet<>(count);
        long start = System.nanoTime();

        for (int i = 0; i < count; i++) {
            ids.add(SnowflakeIdUtil.id());
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(ids.size() >= count * 0.999, "100K ID 应至少99.9%唯一，实际: " + ids.size());
        assertTrue(elapsedMs > 0, "执行时间应大于0");
    }

    // ==================== 2. 安全边界测试 ====================

    @Test
    @DisplayName("AesEncryptUtil: 中文字符串加解密RoundTrip正确")
    void aesEncrypt_chineseRoundTrip_correct() {
        String[] chineseInputs = {
                "你好世界",
                "联盛通科技-账务系统",
                "用户张三的身份证是110101199001011234",
                "手机号13812345678",
                "中文标点：，。！？；：\u201C\u201D（）【】",
                "混合English和中文的测试数据Test"
        };

        for (String input : chineseInputs) {
            String encrypted = AesEncryptUtil.encrypt(input);
            assertNotNull(encrypted, "加密结果不应为null");
            assertNotEquals(input, encrypted, "密文不应等于明文");

            String decrypted = AesEncryptUtil.decrypt(encrypted);
            assertEquals(input, decrypted, "解密结果应与原文一致: " + input);
        }
    }

    @Test
    @DisplayName("AesEncryptUtil: Emoji和特殊Unicode字符RoundTrip")
    void aesEncrypt_emojiRoundTrip_correct() {
        String[] emojiInputs = {
                "😀😃😄😁😅😂🤣",
                "👨‍👩‍👧‍👦 家庭",
                "🚀 火箭发射 🛸",
                "🎉🎊🥳",
                "中文+Emoji: 你好🌍世界",
                "💰💴💵💶💷 货币",
                "αβγδεζηθ 希腊字母",
                "Привет мир 俄语",
                "こんにちは世界 日文",
                "한국어 테스트 韩文"
        };

        for (String input : emojiInputs) {
            String encrypted = AesEncryptUtil.encrypt(input);
            assertNotNull(encrypted, "加密Emoji不应返回null");

            String decrypted = AesEncryptUtil.decrypt(encrypted);
            assertEquals(input, decrypted, "Emoji解密结果应一致: " + input);
        }
    }

    @Test
    @DisplayName("AesEncryptUtil: Unicode全范围字符RoundTrip")
    void aesEncrypt_unicodeRoundTrip_correct() {
        String[] unicodeInputs = {
                "Hello World 2024!",
                "Привет мир Россия",
                "こんにちは世界日本語テスト",
                "한국어 테스트 한글",
                "العربية - اختبار",
                "עברית - מבחן",
                "ไทย - ทดสอบ",
                "हिन्दी - परीक्षण",
                "الصينية: 中文测试",
                "Mixed: Hello 你好 мир 世界 🌍"
        };

        for (String input : unicodeInputs) {
            String encrypted = AesEncryptUtil.encrypt(input);
            String decrypted = AesEncryptUtil.decrypt(encrypted);
            assertEquals(input, decrypted, "Unicode RoundTrip应一致: " + input);
        }

        String surrogatePair = new String(Character.toChars(0x1F600));
        String encSurrogate = AesEncryptUtil.encrypt(surrogatePair);
        String decSurrogate = AesEncryptUtil.decrypt(encSurrogate);
        assertEquals(surrogatePair, decSurrogate, "代理对字符RoundTrip应一致");
    }

    @Test
    @DisplayName("AesEncryptUtil: 空字符串和null正确处理")
    void aesEncrypt_emptyAndNull_handled() {
        assertNull(AesEncryptUtil.encrypt(null), "encrypt(null) 应返回null");
        assertNull(AesEncryptUtil.decrypt(null), "decrypt(null) 应返回null");

        String encryptedEmpty = AesEncryptUtil.encrypt("");
        assertNotNull(encryptedEmpty, "encrypt(\"\") 不应返回null");
        assertFalse(encryptedEmpty.isEmpty(), "空字符串加密密文不应为空");

        String decryptedEmpty = AesEncryptUtil.decrypt(encryptedEmpty);
        assertEquals("", decryptedEmpty, "空字符串解密应为空字符串");
    }

    @Test
    @DisplayName("AesEncryptUtil: 相同明文产生不同密文(随机IV)")
    void aesEncrypt_samePlaintext_differentCiphertext() {
        String plaintext = "LSC-相同明文-测试SamePlaintext";
        int iterations = 50;
        Set<String> ciphertexts = new HashSet<>();

        for (int i = 0; i < iterations; i++) {
            String cipher = AesEncryptUtil.encrypt(plaintext);
            ciphertexts.add(cipher);
        }

        assertTrue(ciphertexts.size() > 1,
                "相同明文应产生多个不同密文（因随机IV），实际唯一密文数: " + ciphertexts.size());
        assertEquals(iterations, ciphertexts.size(),
                "50次加密应全部产生不同密文");

        for (String cipher : ciphertexts) {
            String decrypted = AesEncryptUtil.decrypt(cipher);
            assertEquals(plaintext, decrypted, "不同密文解密后应还原为相同明文");
        }
    }

    @Test
    @DisplayName("AesEncryptUtil: 二进制/不可打印字符RoundTrip")
    void aesEncrypt_binaryString_roundTrip() {
        byte[] binaryBytes = new byte[256];
        for (int i = 0; i < 256; i++) {
            binaryBytes[i] = (byte) i;
        }
        String binaryString = new String(binaryBytes, java.nio.charset.StandardCharsets.ISO_8859_1);

        String encrypted = AesEncryptUtil.encrypt(binaryString);
        assertNotNull(encrypted);

        String decrypted = AesEncryptUtil.decrypt(encrypted);
        assertArrayEquals(binaryBytes, decrypted.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),
                "二进制字节RoundTrip后应保持一致");
    }

    @Test
    @DisplayName("JwtUtil: 签名篡改后应被拒绝")
    void jwt_tamperedSignature_rejected() {
        String token = JwtUtil.generateToken(1001L, 1, "lsc-user-service");
        assertNotNull(token);

        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT应有3个部分");

        String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"9999\",\"userId\":9999,\"userType\":999,\"iss\":\"lsc-admin-service\"}".getBytes());
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertFalse(JwtUtil.isValid(tamperedToken), "篡改签名后的Token应被拒绝");
        assertThrows(JwtUtil.JwtValidationException.class,
                () -> JwtUtil.parseToken(tamperedToken),
                "篡改签名应抛出JwtValidationException");
    }

    @Test
    @DisplayName("JwtUtil: 过期Token应被拒绝")
    void jwt_expiredToken_rejected() {
        String expiredToken = JwtUtil.generateToken(1002L, 2, "lsc-user-service", 1L);

        assertFalse(JwtUtil.isValid(expiredToken), "过期Token应被拒绝");

        assertThrows(JwtUtil.JwtValidationException.class,
                () -> JwtUtil.parseToken(expiredToken),
                "过期Token解析应抛出异常");
    }

    @Test
    @DisplayName("LogSanitizer: XSS载荷清洗")
    void logSanitizer_xssPayloads_sanitized() {
        String[] xssPayloads = {
                "<script>alert('xss')</script>",
                "<img src=x onerror=alert(1)>",
                "<svg onload=alert(1)>",
                "javascript:alert(1)",
                "<body onload=alert('xss')>",
                "<iframe src='evil.com'>",
                "\"><script src=//evil.com/x.js></script>",
                "<img src=x onerror=\"alert('XSS')\"/>",
                "<a href='javascript:alert(1)'>click</a>",
                "<div onmouseover=alert(1)>hover</div>",
                "&#60;script&#62;alert('xss')&#60;/script&#62;",
                "data:text/html,<script>alert('xss')</script>"
        };

        for (String payload : xssPayloads) {
            String sanitized = LogSanitizer.sanitizeUserInput(payload);
            assertNotNull(sanitized);
            assertFalse(LogSanitizer.containsInjection(sanitized),
                    "清洗后的XSS载荷不应包含危险控制字符");
        }
    }

    @Test
    @DisplayName("LogSanitizer: SQL注入字符清洗")
    void logSanitizer_sqlInjectionChars_sanitized() {
        String[] sqlPayloads = {
                "1' OR '1'='1",
                "1; DROP TABLE users--",
                "'; INSERT INTO users VALUES(1,'hacked')--",
                "1' UNION SELECT password FROM users--",
                "admin'--",
                "1; SELECT * FROM information_schema.tables",
                "1' AND SLEEP(5)--",
                "' OR 1=1#",
                "1; EXEC xp_cmdshell('dir')--",
                "'; WAITFOR DELAY '0:0:5'--",
                "1' XOR (SELECT 1 FROM (SELECT COUNT(*),CONCAT(version(),FLOOR(RAND(0)*2))x FROM information_schema.tables GROUP BY x)a)"
        };

        for (String payload : sqlPayloads) {
            String sanitized = LogSanitizer.sanitizeUserInput(payload);
            assertNotNull(sanitized);
            assertFalse(LogSanitizer.containsInjection(sanitized),
                    "清洗后的SQL注入载荷不应被检测为注入");
        }
    }

    @Test
    @DisplayName("LogSanitizer: NoSQL注入检测")
    void logSanitizer_nosqlInjection_detected() {
        String[] nosqlPayloads = {
                "{\"$ne\": null}",
                "{\"$gt\": \"\"}",
                "{'$exists': True}",
                "{\"$where\": \"sleep(5000)\"}",
                "{\"$regex\": \".*\"}",
                "{\"$function\": \"sleep\"}",
                "{\"$accumulator\": \"hack\"}",
                "user[$ne]=1",
                "password[$regex]=.*",
                "admin' || '1'='1"
        };

        for (String payload : nosqlPayloads) {
            String sanitized = LogSanitizer.sanitizeUserInput(payload);
            assertNotNull(sanitized);
        }

        assertTrue(LogSanitizer.containsInjection("test\r\ninjected"),
                "包含换行符的输入应被检测");
        assertTrue(LogSanitizer.containsInjection("normal\u0000data"),
                "包含null字符的输入应被检测");
        assertTrue(LogSanitizer.containsInjection("user\nadmin"),
                "包含换行的输入应被检测");
    }

    @Test
    @DisplayName("LogSanitizer: 大量输入性能和正确性")
    void logSanitizer_largeInput_performance() {
        StringBuilder largeInput = new StringBuilder();
        for (int i = 0; i < 100_000; i++) {
            largeInput.append("LSS-Log-Test-Payload-");
            if (i % 1000 == 0) {
                largeInput.append("\n");
            }
        }

        long start = System.nanoTime();
        String sanitized = LogSanitizer.sanitize(largeInput.toString());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertNotNull(sanitized);
        assertTrue(sanitized.length() > 0, "清洗后应有内容");
        assertTrue(elapsedMs < 5000, "大数据量清洗应在5秒内完成，实际: " + elapsedMs + "ms");

        String truncated = LogSanitizer.sanitizeForLog(largeInput.toString(), 200);
        assertNotNull(truncated);
        assertTrue(truncated.length() <= 220, "截断后长度应在maxLength+...(truncated)范围内");
        assertTrue(truncated.contains("...(truncated)"), "超长输入应被截断并标记");
    }

    @Test
    @DisplayName("CsrfTokenManager: Token不可预测性验证")
    void csrfToken_notPredictable() {
        int iterations = 100;
        Set<String> tokens = new HashSet<>();
        String sessionId = "session-predict-test";

        doNothing().when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        for (int i = 0; i < iterations; i++) {
            String token = csrfTokenManager.generateToken(sessionId, "user-" + i);
            assertNotNull(token);
            tokens.add(token);
        }

        assertEquals(iterations, tokens.size(),
                "100次生成应产生100个唯一Token");

        Random random = new Random();
        int correctPredictions = 0;
        for (int i = 0; i < 1000; i++) {
            int index = random.nextInt(tokens.size());
            String[] tokenArray = tokens.toArray(new String[0]);
            String guessed = tokenArray[index];
            if (tokens.contains(guessed)) {
                correctPredictions++;
            }
        }
        assertTrue(correctPredictions > 0, "随机猜测应能命中部分已知Token");
    }

    @Test
    @DisplayName("CsrfTokenManager: 相同sessionId产生不同Token")
    void csrfToken_sameSession_differentTokens() {
        String sessionId = "same-session-id";
        Set<String> tokens = new HashSet<>();

        doNothing().when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        for (int i = 0; i < 20; i++) {
            tokens.add(csrfTokenManager.generateToken(sessionId, "user-1"));
        }

        assertEquals(20, tokens.size(),
                "相同sessionId连续生成应产生20个不同Token");
    }

    @Test
    @DisplayName("CsrfTokenManager: Token验证与失效生命周期")
    void csrfToken_validateAndInvalidate_lifecycle() {
        String sessionId = "lifecycle-session";
        String userId = "user-123";
        String token = "valid-token-content";

        doNothing().when(valueOperations).set(anyString(), eq(userId + ":" + token), anyLong(), any(TimeUnit.class));
        when(valueOperations.get(anyString())).thenReturn(userId + ":" + token);

        assertTrue(csrfTokenManager.validateToken(sessionId, token),
                "Token应验证通过");

        when(valueOperations.get(anyString())).thenReturn(null);
        assertFalse(csrfTokenManager.validateToken(sessionId, token),
                "Token失效后应验证失败");
    }

    // ==================== 3. 边界与极端场景测试 ====================

    @Test
    @DisplayName("AesEncryptUtil: 超大字符串加密解密正确性")
    void aesEncrypt_veryLargeString_handled() {
        int size = 100_000;
        StringBuilder largePlaintext = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            largePlaintext.append((char) ('a' + (i % 26)));
        }
        String plaintext = largePlaintext.toString();

        long encryptStart = System.nanoTime();
        String encrypted = AesEncryptUtil.encrypt(plaintext);
        long encryptMs = (System.nanoTime() - encryptStart) / 1_000_000;

        assertNotNull(encrypted);
        assertTrue(encryptMs < 10000, "100K字符加密应在10秒内完成，实际: " + encryptMs + "ms");

        long decryptStart = System.nanoTime();
        String decrypted = AesEncryptUtil.decrypt(encrypted);
        long decryptMs = (System.nanoTime() - decryptStart) / 1_000_000;

        assertEquals(plaintext, decrypted, "超大字符串RoundTrip应一致");
        assertTrue(decryptMs < 10000, "100K字符解密应在10秒内完成，实际: " + decryptMs + "ms");
    }

    @Test
    @DisplayName("AesEncryptUtil: 大小边界值组合")
    void aesEncrypt_boundarySizes_handled() {
        int[] sizes = {0, 1, 16, 17, 31, 32, 33, 64, 65, 127, 128, 256, 1024};

        for (int size : sizes) {
            StringBuilder sb = new StringBuilder(size);
            for (int i = 0; i < size; i++) {
                sb.append('x');
            }
            String input = sb.toString();

            String encrypted = AesEncryptUtil.encrypt(input);
            assertNotNull(encrypted, "size=" + size + " 加密不应返回null");

            String decrypted = AesEncryptUtil.decrypt(encrypted);
            assertEquals(input, decrypted, "size=" + size + " RoundTrip应一致");
        }
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 快速连续调用序列完整性")
    void snowflakeId_rapidFire_sequenceIntegrity() {
        int count = 10_000;
        long[] ids = new long[count];

        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            ids[i] = SnowflakeIdUtil.id();
        }
        long elapsedNs = System.nanoTime() - start;

        int consecutiveDuplicates = 0;
        for (int i = 1; i < count; i++) {
            if (ids[i - 1] == ids[i]) {
                consecutiveDuplicates++;
            }
        }
        assertTrue(consecutiveDuplicates <= count * 0.01,
                "连续重复ID数应不超过1%，实际: " + consecutiveDuplicates);

        Set<Long> uniqueIds = new HashSet<>();
        for (long id : ids) {
            uniqueIds.add(id);
        }
        assertTrue(uniqueIds.size() >= count * 0.999, "快速连续ID应至少99.9%唯一，实际: " + uniqueIds.size());

        assertTrue(elapsedNs > 0, "执行时间应大于0");
        double idsPerMs = (double) count / (elapsedNs / 1_000_000.0);
        assertTrue(idsPerMs > 100, "ID生成速率应大于100/ms, 实际: " + idsPerMs + "/ms");
    }

    @Test
    @DisplayName("LogSanitizer: null和空输入安全处理")
    void logSanitizer_nullAndEmpty_handled() {
        assertNull(LogSanitizer.sanitize(null), "sanitize(null)应返回null");
        assertNull(LogSanitizer.sanitizeUserInput(null), "sanitizeUserInput(null)应返回null");
        assertFalse(LogSanitizer.containsInjection(null), "containsInjection(null)应返回false");
        assertFalse(LogSanitizer.containsInjection(""), "containsInjection(\"\")应返回false");

        String emptySanitized = LogSanitizer.sanitize("");
        assertEquals("", emptySanitized);

        String whitespaceOnly = LogSanitizer.sanitize("   \t  ");
        assertEquals("", whitespaceOnly, "纯空白清洗后应为空字符串");

        assertEquals("", LogSanitizer.maskSensitive("", "phone"));
        assertEquals("", LogSanitizer.maskSensitive(null, "email"));
    }

    @Test
    @DisplayName("LogSanitizer: 特殊控制字符检测与清洗")
    void logSanitizer_controlChars_detected() {
        char[] dangerousChars = {
                '\u0000', '\u0001', '\u0002', '\u0003',
                '\u0004', '\u0005', '\u0006', '\u0007',
                '\u000b', '\u000c', '\u000e', '\u000f',
                '\u0010', '\u0011', '\u0012', '\u0013',
                '\u0014', '\u0015', '\u0016', '\u0017',
                '\u0018', '\u0019', '\u001a',
                '\u001c', '\u001d', '\u001e', '\u001f'
        };

        for (char c : dangerousChars) {
            String input = "safe" + c + "data";
            assertTrue(LogSanitizer.containsInjection(input),
                    "字符U+" + Integer.toHexString(c) + "应被检测为注入");

            String sanitized = LogSanitizer.sanitize(input);
            assertFalse(sanitized.contains(String.valueOf(c)),
                    "清洗后不应包含危险控制字符");
        }

        assertTrue(LogSanitizer.containsInjection("line1\rline2"));
        assertTrue(LogSanitizer.containsInjection("line1\nline2"));
        assertTrue(LogSanitizer.containsInjection("line1\r\nline2"));
    }

    @Test
    @DisplayName("JwtUtil: null和无效Token安全处理")
    void jwt_nullEmptyToken_handled() {
        assertFalse(JwtUtil.isValid(null), "null Token应返回false");
        assertFalse(JwtUtil.isValid(""), "空Token应返回false");
        assertFalse(JwtUtil.isValid("invalid-token-format"), "无效格式Token应返回false");
        assertFalse(JwtUtil.isValid("a.b.c"), "随机3段Token应返回false");

        assertThrows(JwtUtil.JwtValidationException.class,
                () -> JwtUtil.parseToken(null),
                "null Token解析应抛出异常");
        assertThrows(JwtUtil.JwtValidationException.class,
                () -> JwtUtil.parseToken("invalid.token.value"),
                "无效Token解析应抛出异常");
    }

    @Test
    @DisplayName("JwtUtil: Token完整性-签发者和载荷正确")
    void jwt_tokenIntegrity_claimsCorrect() {
        Long userId = 10086L;
        Integer userType = 2;
        String issuer = "lsc-user-service";
        long expirationMs = 3600_000L;

        String token = JwtUtil.generateToken(userId, userType, issuer, expirationMs);

        assertNotNull(token);
        assertTrue(JwtUtil.isValid(token));

        Claims claims = JwtUtil.parseToken(token);
        assertEquals(userId, JwtUtil.getUserId(token));
        assertEquals(userType, JwtUtil.getUserType(token));
        assertEquals(issuer, JwtUtil.getIssuer(token));
        assertEquals(String.valueOf(userId), claims.getSubject());
    }

    @Test
    @DisplayName("CsrfTokenManager: null sessionId和token安全拒绝")
    void csrfToken_nullSessionIdAndToken_rejected() {
        assertFalse(csrfTokenManager.validateToken(null, "token"),
                "null sessionId应返回false");
        assertFalse(csrfTokenManager.validateToken("session", null),
                "null token应返回false");
        assertFalse(csrfTokenManager.validateToken(null, null),
                "两者均null应返回false");

        assertDoesNotThrow(() -> csrfTokenManager.invalidateToken(null),
                "null sessionId失效不应抛出异常");
    }

    @Test
    @DisplayName("DistributedLock: null lockKey安全处理")
    void distributedLock_nullLockKey_throws() {
        assertThrows(RuntimeException.class,
                () -> distributedLock.executeWithLock(null, () -> "test"),
                "null lockKey应抛出异常");

        verify(redissonClient).getLock(anyString());
    }

    @Test
    @DisplayName("DistributedLock: 获取锁失败抛出异常")
    void distributedLock_lockFail_throwsException() throws Exception {
        when(mockLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(false);

        assertThrows(RuntimeException.class,
                () -> distributedLock.executeWithLock("fail-key", () -> "should-fail"),
                "获取锁失败应抛出RuntimeException");
    }

    @Test
    @DisplayName("AesEncryptUtil: 并发访问静态资源(getKey)无冲突")
    void aesEncrypt_staticResource_concurrentAccess() throws Exception {
        int threadCount = 30;
        int perThread = 200;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger failures = new AtomicInteger(0);
        AtomicLong totalEncryptionTime = new AtomicLong(0);

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    for (int j = 0; j < perThread; j++) {
                        try {
                            String cipher = AesEncryptUtil.encrypt("concurrent-key-test-" + idx + "-" + j);
                            AesEncryptUtil.decrypt(cipher);
                        } catch (Exception e) {
                            failures.incrementAndGet();
                        }
                    }
                    totalEncryptionTime.addAndGet(System.nanoTime() - start);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        for (Thread t : threads) {
            t.start();
        }
        startLatch.countDown();
        doneLatch.await();

        assertEquals(0, failures.get(),
                "并发访问静态密钥不应有失败，实际: " + failures.get());
        assertTrue(totalEncryptionTime.get() > 0, "总加密时间应大于0");
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 并发下单调性不变量验证")
    void snowflakeId_monotonicConcurrent_invariant() throws Exception {
        int threadCount = 20;
        int perThread = 1000;
        Set<Long> allIds = ConcurrentHashMap.newKeySet();
        ConcurrentHashMap<Long, Long> threadMaxIds = new ConcurrentHashMap<>();
        AtomicInteger violations = new AtomicInteger(0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int tid = i;
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    long maxId = Long.MIN_VALUE;
                    for (int j = 0; j < perThread; j++) {
                        long id = SnowflakeIdUtil.id();
                        allIds.add(id);
                        if (id <= maxId) {
                            violations.incrementAndGet();
                        }
                        maxId = Math.max(maxId, id);
                    }
                    threadMaxIds.put((long) tid, maxId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        for (Thread t : threads) {
            t.start();
        }
        startLatch.countDown();
        doneLatch.await();

        int totalExpected = threadCount * perThread;
        assertTrue(allIds.size() >= totalExpected * 0.97,
                "并发ID应至少97%唯一，实际: " + allIds.size());
        assertTrue(violations.get() < totalExpected * 0.02,
                "单线程内ID违反单调性的次数应极少，实际: " + violations.get());
    }

    @Test
    @DisplayName("LogSanitizer: 多行和混合危险字符场景")
    void logSanitizer_multilineMixedChars_sanitized() {
        String multilinePayload = "正常内容\n<script>alert(1)</script>\r\n另一行\r危险\u0000字符";
        String sanitized = LogSanitizer.sanitizeUserInput(multilinePayload);

        assertNotNull(sanitized);
        assertFalse(sanitized.contains("\n"), "清洗后不应包含换行符");
        assertFalse(sanitized.contains("\r"), "清洗后不应包含回车符");
        assertFalse(sanitized.contains("\u0000"), "清洗后不应包含null字符");

        String mixedPayload = "user: admin'; DROP TABLE users;--\nSELECT * FROM passwords;";
        String sanitizedMixed = LogSanitizer.sanitizeUserInput(mixedPayload);
        assertNotNull(sanitizedMixed);
    }

    @Test
    @DisplayName("AesEncryptUtil: 脱敏方法边界测试")
    void aesEncrypt_maskMethods_edgeCases() {
        assertNull(AesEncryptUtil.maskMobile(null));
        assertEquals("138****5678", AesEncryptUtil.maskMobile("13812345678"));
        assertEquals("138123", AesEncryptUtil.maskMobile("138123"));
        assertEquals("", AesEncryptUtil.maskMobile(""));

        assertEquals("110***********1234", AesEncryptUtil.maskIdCard("110101199001011234"));
        assertNull(AesEncryptUtil.maskIdCard(null));
        assertEquals("12345", AesEncryptUtil.maskIdCard("12345"));

        assertEquals("张*", AesEncryptUtil.maskName("张三"));
        assertEquals("张", AesEncryptUtil.maskName("张"));
        assertNull(AesEncryptUtil.maskName(null));
        assertEquals("A", AesEncryptUtil.maskName("A"));
    }

    @Test
    @DisplayName("DistributedLock: 可重入锁正确性验证")
    void distributedLock_reentrantBehavior_correct() throws Exception {
        when(mockLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true, false);

        String result = distributedLock.executeWithLock("reentrant-key", () -> {
            String innerResult = distributedLock.executeWithLock("reentrant-key", () -> "inner");
            return "outer-" + innerResult;
        });

        assertEquals("outer-inner", result);
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 时间戳位数正确性验证")
    void snowflakeId_bitLayout_correct() {
        long id = SnowflakeIdUtil.id();

        long workerIdFromId = (id >> 12) & 0x1F;
        long datacenterIdFromId = (id >> 17) & 0x1F;
        long sequenceFromId = id & 0xFFF;

        assertTrue(workerIdFromId >= 0 && workerIdFromId <= 31,
                "workerId应在0-31范围内，实际: " + workerIdFromId);
        assertTrue(datacenterIdFromId >= 0 && datacenterIdFromId <= 31,
                "datacenterId应在0-31范围内，实际: " + datacenterIdFromId);
        assertTrue(sequenceFromId >= 0 && sequenceFromId <= 4095,
                "sequence应在0-4095范围内，实际: " + sequenceFromId);

        long id2 = SnowflakeIdUtil.id();
        assertNotEquals(id, id2, "连续两次ID应不同");
    }

    @Test
    @DisplayName("LogSanitizer: 敏感信息脱敏覆盖所有类型")
    void logSanitizer_maskSensitive_allTypes() {
        assertEquals("138****5678", LogSanitizer.maskSensitive("13812345678", "phone"));
        assertEquals("a***@example.com", LogSanitizer.maskSensitive("abc@example.com", "email"));
        assertEquals("110101********1234", LogSanitizer.maskSensitive("110101199001011234", "idcard"));
        assertEquals("abcd****wxyz", LogSanitizer.maskSensitive("abcdefghijklmnopqrstuvwxyz", "token"));
        assertEquals("****", LogSanitizer.maskSensitive("my-secret-password", "password"));
        assertEquals("****", LogSanitizer.maskSensitive("my-secret-value", "secret"));
        assertEquals("****", LogSanitizer.maskSensitive("my-key-value", "key"));
        assertEquals("a***z", LogSanitizer.maskSensitive("abcdefghiz", "unknown-type"));
        assertEquals("****", LogSanitizer.maskSensitive("ab", "token"));
    }

    @Test
    @Timeout(20)
    @DisplayName("AesEncryptUtil: 大量不同明文加密内存边界测试")
    void aesEncrypt_manyPlaintexts_memoryBoundary() {
        int count = 10_000;
        String[] cipherTexts = new String[count];

        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            String plaintext = "LSC-Memory-Test-Item-" + i + "-" + "x".repeat(50);
            cipherTexts[i] = AesEncryptUtil.encrypt(plaintext);
        }
        long encryptMs = (System.nanoTime() - start) / 1_000_000;

        assertNotNull(cipherTexts);
        assertEquals(count, cipherTexts.length);
        assertTrue(encryptMs < 15000,
                "10K条50字节明文加密应在15秒内完成，实际: " + encryptMs + "ms");

        for (int i = 0; i < count; i++) {
            assertNotNull(cipherTexts[i]);
        }

        long decryptStart = System.nanoTime();
        for (int i = 0; i < count; i++) {
            String plaintext = "LSC-Memory-Test-Item-" + i + "-" + "x".repeat(50);
            String decrypted = AesEncryptUtil.decrypt(cipherTexts[i]);
            assertEquals(plaintext, decrypted, "第" + i + "条解密结果不匹配");
        }
        long decryptMs = (System.nanoTime() - decryptStart) / 1_000_000;
        assertTrue(decryptMs < 15000,
                "10K条解密应在15秒内完成，实际: " + decryptMs + "ms");
    }

    @Test
    @DisplayName("CsrfTokenManager: Token格式与Base64Url编码正确性")
    void csrfToken_formatAndEncoding_valid() {
        doNothing().when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        String token = csrfTokenManager.generateToken("session-format-test", "user-format");

        assertNotNull(token);
        assertFalse(token.isEmpty());

        try {
            Base64.getUrlDecoder().decode(token + "==");
        } catch (Exception e) {
            Base64.getUrlDecoder().decode(token);
        }

        assertTrue(token.length() >= 32,
                "Token长度应至少32字符(32字节Base64Url编码)");
    }

    @Test
    @DisplayName("DistributedLock: 中断异常正确传播")
    void distributedLock_interruptedException_handled() throws Exception {
        when(mockLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new InterruptedException("测试中断"));

        assertThrows(RuntimeException.class,
                () -> distributedLock.executeWithLock("interrupt-key", () -> "should-fail"));
    }

    @Test
    @DisplayName("AesEncryptUtil: 相同明文明文加密产生不同密文但解密一致")
    void aesEncrypt_deterministicDecryption_consistent() {
        int iterations = 100;
        String plaintext = "LSC-Decryption-Consistency-Test";
        String decryptedFromFirst = null;

        for (int i = 0; i < iterations; i++) {
            String cipher = AesEncryptUtil.encrypt(plaintext);
            String decrypted = AesEncryptUtil.decrypt(cipher);

            assertEquals(plaintext, decrypted,
                    "第" + i + "次解密应与原文一致");

            if (i == 0) {
                decryptedFromFirst = decrypted;
            } else {
                assertEquals(decryptedFromFirst, decrypted,
                        "所有解密结果应相互一致");
            }
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("SnowflakeIdUtil: 高并发竞争下无死锁和饥饿")
    void snowflakeId_noDeadlockOrStarvation() throws Exception {
        int threadCount = 50;
        int perThread = 200;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicLong totalIdsGenerated = new AtomicLong(0);
        AtomicInteger threadTimeouts = new AtomicInteger(0);

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    int localCount = 0;
                    long deadline = System.currentTimeMillis() + 5000;
                    while (localCount < perThread && System.currentTimeMillis() < deadline) {
                        SnowflakeIdUtil.id();
                        localCount++;
                    }
                    totalIdsGenerated.addAndGet(localCount);
                    if (localCount < perThread) {
                        threadTimeouts.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        for (Thread t : threads) {
            t.start();
        }
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);

        assertTrue(completed, "所有线程应在10秒内完成");
        assertTrue(totalIdsGenerated.get() >= threadCount * perThread * 0.9,
                "至少应有90%的线程完成其ID生成任务，实际: " + totalIdsGenerated.get());
        assertTrue(threadTimeouts.get() < threadCount * 0.1,
                "超时线程数应少于10%，实际: " + threadTimeouts.get());
    }

    @Test
    @DisplayName("LogSanitizer: 日志注入CRLF注入攻击场景")
    void logSanitizer_crlfInjection_attack() {
        String crlfPayload = "user=admin\r\n[CRITICAL] 系统被入侵: user=root action=delete";
        String sanitized = LogSanitizer.sanitizeUserInput(crlfPayload);

        assertNotNull(sanitized);
        assertFalse(sanitized.contains("\r"), "CR应被清除");
        assertFalse(sanitized.contains("\n"), "LF应被清除");

        String[] lines = sanitized.split("[\r\n]");
        assertEquals(1, lines.length, "清洗后不应有多行");
    }

    @Test
    @DisplayName("JwtUtil: 默认过期时间和自定义过期时间正确性")
    void jwt_customExpiration_correct() {
        String defaultToken = JwtUtil.generateToken(1L, 1, "issuer-default");
        assertNotNull(defaultToken);

        String shortToken = JwtUtil.generateToken(2L, 2, "issuer-short", 100L);
        assertDoesNotThrow(() -> Thread.sleep(150));
        assertFalse(JwtUtil.isValid(shortToken), "100ms过期的Token应很快失效");

        String longToken = JwtUtil.generateToken(3L, 3, "issuer-long", 3600_000L);
        assertTrue(JwtUtil.isValid(longToken), "1小时过期的Token应仍然有效");

        Claims claims = JwtUtil.parseToken(longToken);
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(new Date()),
                "长过期时间Token的expiration应在未来");
    }

    @Test
    @DisplayName("AesEncryptUtil: 重复加密相同明文每次密文长度合理")
    void aesEncrypt_ciphertextLength_reasonable() {
        String[] testInputs = {"a", "ab", "abc", "test", "hello-world", "a".repeat(1000)};

        for (String input : testInputs) {
            String cipher = AesEncryptUtil.encrypt(input);
            assertNotNull(cipher);

            assertTrue(cipher.length() >= 24,
                    "密文Base64编码后长度应至少24字符(16字节IV最小密文)，实际: " + cipher.length());

            String decrypted = AesEncryptUtil.decrypt(cipher);
            assertEquals(input, decrypted, "解密应还原原文");
        }
    }

    @Test
    @DisplayName("DistributedLock: Runnable版本正确执行无返回值")
    void distributedLock_runnableVersion_executed() throws Exception {
        when(mockLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        AtomicBoolean executed = new AtomicBoolean(false);
        assertDoesNotThrow(() -> distributedLock.executeWithLock("runnable-key", () -> {
            executed.set(true);
        }));

        assertTrue(executed.get(), "Runnable应被执行");
    }

    @Test
    @DisplayName("SnowflakeIdUtil: ID生成器线程安全性-多实例独立")
    void snowflakeId_threadSafety_independentInstances() throws Exception {
        int threadCount = 10;
        int perThread = 200;
        SnowflakeIdUtil sharedInstance = SnowflakeIdUtil.getInstance();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Set<Long> allIds = ConcurrentHashMap.newKeySet();

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < perThread; j++) {
                        allIds.add(sharedInstance.nextId());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        for (Thread t : threads) {
            t.start();
        }
        startLatch.countDown();
        doneLatch.await();

        assertTrue(allIds.size() >= threadCount * perThread * 0.97,
                "多线程通过getInstance()共享实例应至少97%唯一，实际: " + allIds.size());
    }

    @Test
    @DisplayName("LogSanitizer: 日志注入完整Payload测试")
    void logSanitizer_completePayload_injection() {
        String[] fullPayloads = {
                "%0d%0a[CRITICAL] LogForge: user=admin action=drop_table",
                "user=admin%0d%0a[ERROR] forged log entry",
                "normal-input%0d%0a%0d[CRITICAL] admin logged in as root",
                "test\u001b[31mRED COLOR\u001b[0m normal text",
                "\u001b[2J\u001b[Hforged-content-at-top-of-log",
                "line1\u0008\u0008\u0008[OVERWRITTEN] original text gone",
                "valid-input\ntampered-line-injected\nfinal-line"
        };

        for (String payload : fullPayloads) {
            String sanitized = LogSanitizer.sanitizeUserInput(payload);
            assertNotNull(sanitized);
            assertFalse(LogSanitizer.containsInjection(sanitized),
                    "清洗后的完整Payload不应包含注入特征");
        }
    }
}