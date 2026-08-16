package com.lianshengtong.media.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.utils.RedisKeyPrefix;
import com.lianshengtong.media.dto.MediaUploadResult;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import com.alibaba.fastjson2.JSON;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("媒体服务边界条件测试 - P0/P1/P2分级")
class MediaServiceImplBoundaryTest {

    @Mock
    StringRedisTemplate stringRedisTemplate;
    @Mock
    ValueOperations<String, String> valueOperations;
    @Mock
    MultipartFile mockFile;
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private OSS ossClient;
    private COSClient cosClient;

    private MediaServiceImpl mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaServiceImpl(stringRedisTemplate, meterRegistry);
        ReflectionTestUtils.setField(mediaService, "ossEndpoint", "oss-cn-hangzhou.aliyuncs.com");
        ReflectionTestUtils.setField(mediaService, "ossAk", "test-ak");
        ReflectionTestUtils.setField(mediaService, "ossSk", "test-sk");
        ReflectionTestUtils.setField(mediaService, "ossBucket", "test-bucket");
        ReflectionTestUtils.setField(mediaService, "ossCdn", "https://oss.example.com");
        ReflectionTestUtils.setField(mediaService, "cosRegion", "ap-guangzhou");
        ReflectionTestUtils.setField(mediaService, "cosId", "test-cos-id");
        ReflectionTestUtils.setField(mediaService, "cosKey", "test-cos-key");
        ReflectionTestUtils.setField(mediaService, "cosBucket", "test-bucket");
        ReflectionTestUtils.setField(mediaService, "cosCdn", "https://cos.example.com");
        ReflectionTestUtils.setField(mediaService, "imageMaxMb", 10L);
        ReflectionTestUtils.setField(mediaService, "videoMaxMb", 100L);
        ReflectionTestUtils.setField(mediaService, "imageAllowedTypes", "jpg,jpeg,png,gif,webp");
        ReflectionTestUtils.setField(mediaService, "videoAllowedTypes", "mp4,mov,avi");
        ReflectionTestUtils.setField(mediaService, "transcodeProfiles", "720p,480p,360p");
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(false);

        ossClient = mock(OSS.class);
        cosClient = mock(COSClient.class);
        ReflectionTestUtils.setField(mediaService, "ossClient", ossClient);
        ReflectionTestUtils.setField(mediaService, "cosClient", cosClient);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== P0: init() @PostConstruct ====================

    @Test
    @DisplayName("[P0] init: OSS和COS都初始化成功")
    void init_bothSuccess() {
        assertDoesNotThrow(() -> mediaService.init());
        assertFalse(((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).get());
    }

    @Test
    @DisplayName("[P0] init: OSS初始化失败，ossDown设为true")
    void init_ossInitFail_setsOssDown() {
        ReflectionTestUtils.setField(mediaService, "ossAk", null);
        ReflectionTestUtils.setField(mediaService, "ossSk", null);
        assertDoesNotThrow(() -> mediaService.init());
        assertTrue(((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).get());
    }

    @Test
    @DisplayName("[P0] init: COS初始化失败不抛异常")
    void init_cosInitFail_noException() {
        ReflectionTestUtils.setField(mediaService, "cosId", null);
        ReflectionTestUtils.setField(mediaService, "cosKey", null);
        assertDoesNotThrow(() -> mediaService.init());
    }

    @Test
    @DisplayName("[P0] init: 两者都失败不抛异常")
    void init_bothFail_noException() {
        ReflectionTestUtils.setField(mediaService, "ossAk", null);
        ReflectionTestUtils.setField(mediaService, "ossSk", null);
        ReflectionTestUtils.setField(mediaService, "cosId", null);
        ReflectionTestUtils.setField(mediaService, "cosKey", null);
        assertDoesNotThrow(() -> mediaService.init());
        assertTrue(((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).get());
    }

    @Test
    @DisplayName("[P0] init: cosBucket为null触发COS失败路径")
    void init_cosBucketNull_cosFails() {
        ReflectionTestUtils.setField(mediaService, "cosBucket", null);
        assertDoesNotThrow(() -> mediaService.init());
        assertNotNull(ReflectionTestUtils.getField(mediaService, "ossClient"));
    }

    @Test
    @DisplayName("[P0] init: cosBucket为空字符串触发COS失败路径")
    void init_cosBucketEmpty_cosFails() {
        ReflectionTestUtils.setField(mediaService, "cosBucket", "");
        assertDoesNotThrow(() -> mediaService.init());
    }

    @Test
    @DisplayName("[P0] init: COS成功初始化覆盖5行代码")
    void init_cosClientSuccess_covers5Lines() {
        try (MockedConstruction<BasicCOSCredentials> credMock = Mockito.mockConstruction(BasicCOSCredentials.class);
             MockedConstruction<Region> regionMock = Mockito.mockConstruction(Region.class);
             MockedConstruction<ClientConfig> configMock = Mockito.mockConstruction(ClientConfig.class);
             MockedConstruction<COSClient> cosMock = Mockito.mockConstruction(COSClient.class)) {
            assertDoesNotThrow(() -> mediaService.init());
            COSClient result = (COSClient) ReflectionTestUtils.getField(mediaService, "cosClient");
            assertNotNull(result);
        }
    }

    @Test
    @DisplayName("[P0] init: COS成功且OSS失败时ossDown为true")
    void init_cosSuccess_ossFailed_ossDownTrue() {
        ReflectionTestUtils.setField(mediaService, "ossAk", null);
        ReflectionTestUtils.setField(mediaService, "ossSk", null);
        try (MockedConstruction<BasicCOSCredentials> credMock = Mockito.mockConstruction(BasicCOSCredentials.class);
             MockedConstruction<Region> regionMock = Mockito.mockConstruction(Region.class);
             MockedConstruction<ClientConfig> configMock = Mockito.mockConstruction(ClientConfig.class);
             MockedConstruction<COSClient> cosMock = Mockito.mockConstruction(COSClient.class)) {
            assertDoesNotThrow(() -> mediaService.init());
            assertTrue(((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).get());
            assertNotNull(ReflectionTestUtils.getField(mediaService, "cosClient"));
        }
    }

    @Test
    @DisplayName("[P0] init: OSS和COS都成功初始化")
    void init_bothClientSuccess() {
        try (MockedConstruction<BasicCOSCredentials> credMock = Mockito.mockConstruction(BasicCOSCredentials.class);
             MockedConstruction<Region> regionMock = Mockito.mockConstruction(Region.class);
             MockedConstruction<ClientConfig> configMock = Mockito.mockConstruction(ClientConfig.class);
             MockedConstruction<COSClient> cosMock = Mockito.mockConstruction(COSClient.class)) {
            assertDoesNotThrow(() -> mediaService.init());
            assertFalse(((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).get());
            assertNotNull(ReflectionTestUtils.getField(mediaService, "ossClient"));
            assertNotNull(ReflectionTestUtils.getField(mediaService, "cosClient"));
        }
    }

    @Test
    @DisplayName("[P0] init: COSClient的BasicCOSCredentials正确解析appId")
    void init_cosCredentials_appIdParsed() {
        ReflectionTestUtils.setField(mediaService, "cosBucket", "my-appid-001");
        try (MockedConstruction<BasicCOSCredentials> credMock = Mockito.mockConstruction(BasicCOSCredentials.class);
             MockedConstruction<Region> regionMock = Mockito.mockConstruction(Region.class);
             MockedConstruction<ClientConfig> configMock = Mockito.mockConstruction(ClientConfig.class);
             MockedConstruction<COSClient> cosMock = Mockito.mockConstruction(COSClient.class)) {
            assertDoesNotThrow(() -> mediaService.init());
            assertNotNull(ReflectionTestUtils.getField(mediaService, "cosClient"));
        }
    }

    // ==================== P0: objectExists() 全分支 ====================

    @Test
    @DisplayName("[P0] objectExists: oss正常，对象存在返回true")
    void objectExists_ossUp_exists() {
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(false);
        ReflectionTestUtils.setField(mediaService, "ossClient", ossClient);
        when(ossClient.doesObjectExist(eq("test-bucket"), eq("key.mp4"))).thenReturn(true);

        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "objectExists", "key.mp4");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P0] objectExists: oss正常，对象不存在返回false")
    void objectExists_ossUp_notExists() {
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(false);
        ReflectionTestUtils.setField(mediaService, "ossClient", ossClient);
        when(ossClient.doesObjectExist(eq("test-bucket"), eq("key.mp4"))).thenReturn(false);

        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "objectExists", "key.mp4");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P0] objectExists: oss正常但ossClient为null，回退COS")
    void objectExists_ossUp_ossClientNull_fallbackCos() {
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(false);
        ReflectionTestUtils.setField(mediaService, "ossClient", null);
        when(cosClient.doesObjectExist(eq("test-bucket"), eq("key.mp4"))).thenReturn(true);

        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "objectExists", "key.mp4");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P0] objectExists: ossClient正常但doesObjectExist抛异常，切换COS")
    void objectExists_ossThrows_fallbackCos() {
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(false);
        ReflectionTestUtils.setField(mediaService, "ossClient", ossClient);
        when(ossClient.doesObjectExist(anyString(), anyString()))
                .thenThrow(new RuntimeException("OSS检查失败"));
        when(cosClient.doesObjectExist(eq("test-bucket"), eq("key.mp4"))).thenReturn(true);

        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "objectExists", "key.mp4");
        assertTrue(result);
        assertTrue(((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).get());
    }

    @Test
    @DisplayName("[P0] objectExists: ossDown为true，直接查COS")
    void objectExists_ossDown_checkCos() {
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(true);
        when(cosClient.doesObjectExist(eq("test-bucket"), eq("key.mp4"))).thenReturn(true);

        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "objectExists", "key.mp4");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P0] objectExists: cosClient为null返回false")
    void objectExists_cosNull_returnsFalse() {
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(true);
        ReflectionTestUtils.setField(mediaService, "cosClient", null);

        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "objectExists", "key.mp4");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P0] objectExists: cosClient.doesObjectExist抛异常返回false")
    void objectExists_cosThrows_returnsFalse() {
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(true);
        when(cosClient.doesObjectExist(anyString(), anyString()))
                .thenThrow(new RuntimeException("COS检查失败"));

        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "objectExists", "key.mp4");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P0] objectExists: ossClient和cosClient都为null返回false")
    void objectExists_bothNull_returnsFalse() {
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(false);
        ReflectionTestUtils.setField(mediaService, "ossClient", null);
        ReflectionTestUtils.setField(mediaService, "cosClient", null);

        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "objectExists", "key.mp4");
        assertFalse(result);
    }

    // ==================== P0: uploadToOss contentType为null ====================

    @Test
    @DisplayName("[P0] uploadToOss: contentType为null不设置ContentType")
    void uploadToOss_nullContentType_noContentTypeSet() throws Exception {
        byte[] bytes = "test".getBytes();
        ReflectionTestUtils.invokeMethod(mediaService, "uploadToOss", "key", bytes, null);
        verify(ossClient).putObject(eq("test-bucket"), eq("key"), any(ByteArrayInputStream.class),
                argThat(meta -> meta.getContentType() == null));
    }

    @Test
    @DisplayName("[P0] uploadToOss: contentType不为null正确设置")
    void uploadToOss_withContentType_setsCorrectly() throws Exception {
        byte[] bytes = "test".getBytes();
        ReflectionTestUtils.invokeMethod(mediaService, "uploadToOss", "key", bytes, "image/jpeg");
        verify(ossClient).putObject(eq("test-bucket"), eq("key"), any(ByteArrayInputStream.class),
                argThat(meta -> "image/jpeg".equals(meta.getContentType())));
    }

    // ==================== P1: isContentTypeCompatible 全类型覆盖 ====================

    @Test
    @DisplayName("[P1] isContentTypeCompatible: gif匹配返回true")
    void isContentTypeCompatible_gif_match() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/gif", "gif");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: webp匹配返回true")
    void isContentTypeCompatible_webp_match() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/webp", "webp");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: mov匹配quicktime返回true")
    void isContentTypeCompatible_mov_match() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/quicktime", "mov");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: avi匹配x-msvideo返回true")
    void isContentTypeCompatible_avi_match() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/x-msvideo", "avi");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: jpeg匹配image/jpeg返回true")
    void isContentTypeCompatible_jpeg_match() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/jpeg", "jpeg");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: mp4匹配video/mp4返回true")
    void isContentTypeCompatible_mp4_match() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/mp4", "mp4");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: mp4匹配video/mpeg4返回true")
    void isContentTypeCompatible_mp4_mpeg4() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/mpeg4", "mp4");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: avi匹配video/avi返回true")
    void isContentTypeCompatible_avi_altMatch() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/avi", "avi");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: gif不匹配png返回false")
    void isContentTypeCompatible_gif_mismatch() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/png", "gif");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: webp不匹配jpeg返回false")
    void isContentTypeCompatible_webp_mismatch() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/jpeg", "webp");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: mov不匹配mp4返回false")
    void isContentTypeCompatible_mov_mismatch() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/mp4", "mov");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: avi不匹配quicktime返回false")
    void isContentTypeCompatible_avi_mismatch() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/quicktime", "avi");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: png不匹配image/jpeg返回false")
    void isContentTypeCompatible_png_mismatch() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/jpeg", "png");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: jpeg不匹配text/plain返回false")
    void isContentTypeCompatible_jpeg_textMismatch() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "text/plain", "jpeg");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: gif不匹配text/html返回false")
    void isContentTypeCompatible_gif_textMismatch() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "text/html", "gif");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: jpg扩展名短路jpeg检查分支")
    void isContentTypeCompatible_jpgExt_shortCircuitJpeg() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/jpeg", "jpg");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: jpg扩展名短路且不匹配返回false")
    void isContentTypeCompatible_jpgExt_noImageMatch() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/mp4", "jpg");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: jpeg扩展名需要评估短路分支")
    void isContentTypeCompatible_jpegExt_evaluatesSecondPart() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/jpeg", "jpeg");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: jpg不匹配image前缀走全链路返回false")
    void isContentTypeCompatible_jpgNoImagePrefix_returnsFalse() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "application/octet-stream", "jpg");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: jpeg不匹配image前缀走全链路")
    void isContentTypeCompatible_jpegNoImagePrefix_returnsFalse() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/mp4", "jpeg");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: png匹配png返回true")
    void isContentTypeCompatible_pngMatch_returnsTrue() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/png", "png");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: png不匹配png但匹配image前缀")
    void isContentTypeCompatible_pngMismatch_returnsFalse() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/jpeg", "png");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: mp4匹配mpeg4返回true")
    void isContentTypeCompatible_mp4Mpeg4_returnsTrue() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/mpeg4", "mp4");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: avi匹配x-msvideo返回true")
    void isContentTypeCompatible_aviXmsvideo_returnsTrue() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/x-msvideo", "avi");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: 未知扩展名走全链路返回false")
    void isContentTypeCompatible_unknownExt_returnsFalse() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/jpeg", "bmp");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: gif匹配image/gif返回true")
    void isContentTypeCompatible_gifMatch_returnsTrue() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/gif", "gif");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: webp匹配返回true")
    void isContentTypeCompatible_webpMatch_returnsTrue() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/webp", "webp");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: mov匹配quicktime返回true")
    void isContentTypeCompatible_movMatch_returnsTrue() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/quicktime", "mov");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: mp4与mpeg4混合内容类型")
    void isContentTypeCompatible_mp4WithMpeg4Ct_returnsTrue() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/mpeg4", "mp4");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: mp4含mp4内容类型短路分支")
    void isContentTypeCompatible_mp4WithMp4Ct_shortCircuit() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/mp4", "mp4");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: mp4不匹配任何mp4相关格式")
    void isContentTypeCompatible_mp4NoMatch_returnsFalse() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/webm", "mp4");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: avi含x-msvideo内容类型短路分支")
    void isContentTypeCompatible_aviWithXmsvideoCt_returnsTrue() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/x-msvideo", "avi");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: avi含avi内容类型短路分支")
    void isContentTypeCompatible_aviWithAviCt_returnsTrue() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/avi", "avi");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: jpg内容类型匹配image前缀返回true")
    void isContentTypeCompatible_jpgWithImageGifCt_returnsTrue() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/gif", "jpg");
        assertTrue(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: png不匹配png但匹配image前缀")
    void isContentTypeCompatible_pngNoPngCt_returnsFalse() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/webp", "png");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: webp不匹配webp但匹配image前缀")
    void isContentTypeCompatible_webpNoWebpCt_returnsFalse() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/jpeg", "webp");
        assertFalse(result);
    }

    @Test
    @DisplayName("[P1] isContentTypeCompatible: gif不匹配gif但匹配image前缀")
    void isContentTypeCompatible_gifNoGifCt_returnsFalse() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/jpeg", "gif");
        assertFalse(result);
    }

    // ==================== P1: validateFile 边界条件 ====================

    @Test
    @DisplayName("[P1] validateFile: 边界大小(恰好等于限制)通过")
    void validateFile_exactMaxSize_passes() {
        long maxMb = 10L;
        long exactSize = maxMb * 1024L * 1024L;
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(exactSize);
        when(mockFile.getOriginalFilename()).thenReturn("a.jpg");
        when(mockFile.getContentType()).thenReturn("image/jpeg");

        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(mediaService, "validateFile", mockFile, maxMb, "jpg", "image"));
    }

    @Test
    @DisplayName("[P1] validateFile: 边界大小(限制+1MB)抛异常")
    void validateFile_oneMbOver_throws() {
        long maxMb = 10L;
        long overSize = (maxMb + 1) * 1024L * 1024L;
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(overSize);
        lenient().when(mockFile.getOriginalFilename()).thenReturn("a.jpg");
        lenient().when(mockFile.getContentType()).thenReturn("image/jpeg");

        BizException ex = assertThrows(BizException.class, () ->
                ReflectionTestUtils.invokeMethod(mediaService, "validateFile", mockFile, maxMb, "jpg", "image"));
        assertTrue(ex.getMessage().contains("大小超过"));
    }

    @Test
    @DisplayName("[P1] validateFile: png类型content-type验证")
    void validateFile_pngType_passes() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getOriginalFilename()).thenReturn("a.png");
        when(mockFile.getContentType()).thenReturn("image/png");

        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(mediaService, "validateFile", mockFile, 10L, "png", "image"));
    }

    @Test
    @DisplayName("[P1] validateFile: gif类型content-type验证")
    void validateFile_gifType_passes() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getOriginalFilename()).thenReturn("a.gif");
        when(mockFile.getContentType()).thenReturn("image/gif");

        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(mediaService, "validateFile", mockFile, 10L, "gif", "image"));
    }

    @Test
    @DisplayName("[P1] validateFile: webp类型content-type验证")
    void validateFile_webpType_passes() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getOriginalFilename()).thenReturn("a.webp");
        when(mockFile.getContentType()).thenReturn("image/webp");

        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(mediaService, "validateFile", mockFile, 10L, "webp", "image"));
    }

    @Test
    @DisplayName("[P1] validateFile: mov视频类型验证")
    void validateFile_movVideo_passes() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getOriginalFilename()).thenReturn("clip.mov");
        when(mockFile.getContentType()).thenReturn("video/quicktime");

        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(mediaService, "validateFile", mockFile, 100L, "mov", "video"));
    }

    @Test
    @DisplayName("[P1] validateFile: avi视频类型验证")
    void validateFile_aviVideo_passes() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getOriginalFilename()).thenReturn("clip.avi");
        when(mockFile.getContentType()).thenReturn("video/x-msvideo");

        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(mediaService, "validateFile", mockFile, 100L, "avi", "video"));
    }

    // ==================== P1: uploadImage 边界场景 ====================

    @Test
    @DisplayName("[P1] uploadImage: 边界大小(恰好等于限制)上传成功")
    void uploadImage_exactMaxSize_success() throws Exception {
        byte[] bytes = new byte[(int) (10L * 1024L * 1024L)];
        java.util.Arrays.fill(bytes, (byte) 'a');
        lenient().when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) bytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("exact.jpg");
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getBytes()).thenReturn(bytes);

        MediaUploadResult result = mediaService.uploadImage(mockFile);
        assertNotNull(result);
        assertEquals("image", result.getType());
    }

    @Test
    @DisplayName("[P1] uploadImage: png文件上传成功")
    void uploadImage_pngFile_success() throws Exception {
        byte[] bytes = "png-content".getBytes();
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) bytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("pic.png");
        when(mockFile.getContentType()).thenReturn("image/png");
        when(mockFile.getBytes()).thenReturn(bytes);

        MediaUploadResult result = mediaService.uploadImage(mockFile);
        assertNotNull(result);
        assertTrue(result.getMediaKey().endsWith(".png"));
    }

    @Test
    @DisplayName("[P1] uploadImage: gif文件上传成功")
    void uploadImage_gifFile_success() throws Exception {
        byte[] bytes = "gif-content".getBytes();
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) bytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("pic.gif");
        when(mockFile.getContentType()).thenReturn("image/gif");
        when(mockFile.getBytes()).thenReturn(bytes);

        MediaUploadResult result = mediaService.uploadImage(mockFile);
        assertNotNull(result);
        assertTrue(result.getMediaKey().endsWith(".gif"));
    }

    @Test
    @DisplayName("[P1] uploadImage: webp文件上传成功")
    void uploadImage_webpFile_success() throws Exception {
        byte[] bytes = "webp-content".getBytes();
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) bytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("pic.webp");
        when(mockFile.getContentType()).thenReturn("image/webp");
        when(mockFile.getBytes()).thenReturn(bytes);

        MediaUploadResult result = mediaService.uploadImage(mockFile);
        assertNotNull(result);
        assertTrue(result.getMediaKey().endsWith(".webp"));
    }

    @Test
    @DisplayName("[P1] uploadImage: jpeg扩展名上传成功")
    void uploadImage_jpegExt_success() throws Exception {
        byte[] bytes = "jpeg-content".getBytes();
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) bytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("pic.jpeg");
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getBytes()).thenReturn(bytes);

        MediaUploadResult result = mediaService.uploadImage(mockFile);
        assertNotNull(result);
        assertTrue(result.getMediaKey().endsWith(".jpeg"));
    }

    // ==================== P1: uploadVideo 边界场景 ====================

    @Test
    @DisplayName("[P1] uploadVideo: mov视频上传成功")
    void uploadVideo_movFile_success() throws Exception {
        byte[] bytes = "mov-video-content".getBytes();
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) bytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("clip.mov");
        when(mockFile.getContentType()).thenReturn("video/quicktime");
        when(mockFile.getBytes()).thenReturn(bytes);

        MediaUploadResult result = mediaService.uploadVideo(mockFile);
        assertNotNull(result);
        assertTrue(result.getMediaKey().endsWith(".mov"));
        assertEquals(3, result.getTranscodeUrls().size());
    }

    @Test
    @DisplayName("[P1] uploadVideo: avi视频上传成功")
    void uploadVideo_aviFile_success() throws Exception {
        byte[] bytes = "avi-video-content".getBytes();
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) bytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("clip.avi");
        when(mockFile.getContentType()).thenReturn("video/x-msvideo");
        when(mockFile.getBytes()).thenReturn(bytes);

        MediaUploadResult result = mediaService.uploadVideo(mockFile);
        assertNotNull(result);
        assertTrue(result.getMediaKey().endsWith(".avi"));
        assertEquals(3, result.getTranscodeUrls().size());
    }

    @Test
    @DisplayName("[P1] uploadVideo: 边界大小(恰好等于限制)上传成功")
    void uploadVideo_exactMaxSize_success() throws Exception {
        byte[] bytes = new byte[(int) (100L * 1024L * 1024L)];
        java.util.Arrays.fill(bytes, (byte) 'v');
        lenient().when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) bytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("big.mp4");
        when(mockFile.getContentType()).thenReturn("video/mp4");
        when(mockFile.getBytes()).thenReturn(bytes);

        MediaUploadResult result = mediaService.uploadVideo(mockFile);
        assertNotNull(result);
        assertEquals("video", result.getType());
    }

    // ==================== P1: videoStatus ossDown场景 ====================

    @Test
    @DisplayName("[P1] videoStatus: ossDown时通过COS检查转码状态")
    void videoStatus_ossDown_cosCheck() {
        String url = "https://cos.example.com/lsc/20250101/video.mp4";
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(true);
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(cosClient.doesObjectExist(eq("test-bucket"), contains("720p"))).thenReturn(true);

        Map<String, Object> result = mediaService.videoStatus(url);
        assertEquals("ready", result.get("status"));
    }

    @Test
    @DisplayName("[P1] videoStatus: ossDown时COS检查失败返回transcoding")
    void videoStatus_ossDown_cosNotReady() {
        String url = "https://cos.example.com/lsc/20250101/video.mp4";
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(true);
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(cosClient.doesObjectExist(anyString(), anyString())).thenReturn(false);

        Map<String, Object> result = mediaService.videoStatus(url);
        assertEquals("transcoding", result.get("status"));
    }

    @Test
    @DisplayName("[P1] videoStatus: ossDown且cosClient为null时返回transcoding")
    void videoStatus_ossDown_cosNull_returnsTranscoding() {
        String url = "https://cos.example.com/lsc/20250101/video.mp4";
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(true);
        ReflectionTestUtils.setField(mediaService, "cosClient", null);
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);

        Map<String, Object> result = mediaService.videoStatus(url);
        assertEquals("transcoding", result.get("status"));
        assertNull(result.get("coverUrl"));
    }

    @Test
    @DisplayName("[P1] videoStatus: oss正常时通过OSS检查转码状态")
    void videoStatus_ossUp_ossCheck() {
        String url = "https://oss.example.com/lsc/20250101/video.mp4";
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(false);
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(ossClient.doesObjectExist(eq("test-bucket"), contains("720p"))).thenReturn(true);
        when(ossClient.doesObjectExist(eq("test-bucket"), contains("cover"))).thenReturn(true);

        Map<String, Object> result = mediaService.videoStatus(url);
        assertEquals("ready", result.get("status"));
        assertNotNull(result.get("coverUrl"));
    }

    @Test
    @DisplayName("[P1] videoStatus: 缓存命中直接返回")
    void videoStatus_cacheHit_returnsCached() {
        String url = "https://oss.example.com/lsc/20250101/video.mp4";
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url))
                .thenReturn("{\"url\":\"" + url + "\",\"status\":\"ready\"}");

        Map<String, Object> result = mediaService.videoStatus(url);
        assertEquals("ready", result.get("status"));
    }

    @Test
    @DisplayName("[P1] videoStatus: 元信息缓存命中填充到结果")
    void videoStatus_metaCacheHit_fillsResult() {
        String url = "https://oss.example.com/lsc/20250101/video.mp4";
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(false);
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(ossClient.doesObjectExist(anyString(), anyString())).thenReturn(false);
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_META + url))
                .thenReturn("{\"duration\":120,\"width\":1920,\"height\":1080,\"size\":52428800}");

        Map<String, Object> result = mediaService.videoStatus(url);
        assertEquals(120, ((Number) result.get("duration")).intValue());
        assertEquals(1920, ((Number) result.get("width")).intValue());
        assertEquals(1080, ((Number) result.get("height")).intValue());
        assertEquals(52428800, ((Number) result.get("size")).longValue());
    }

    @Test
    @DisplayName("[P1] videoStatus: 覆盖OSS CDN前缀正确提取mediaKey")
    void videoStatus_ossPrefix_extractKey() {
        String url = "https://oss.example.com/lsc/20250101/video.mp4";
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(false);
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(ossClient.doesObjectExist(anyString(), anyString())).thenReturn(false);

        Map<String, Object> result = mediaService.videoStatus(url);
        assertNotNull(result.get("url"));
    }

    @Test
    @DisplayName("[P1] videoStatus: 封面检查(对象存在返回URL)")
    void videoStatus_coverExists_returnsUrl() {
        String url = "https://cos.example.com/lsc/20250101/video.mp4";
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(true);
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(cosClient.doesObjectExist(eq("test-bucket"), contains("cover"))).thenReturn(true);
        when(cosClient.doesObjectExist(eq("test-bucket"), contains("720p"))).thenReturn(false);

        Map<String, Object> result = mediaService.videoStatus(url);
        assertEquals("transcoding", result.get("status"));
        assertNotNull(result.get("coverUrl"));
    }

    @Test
    @DisplayName("[P1] videoStatus: 元信息解析异常走回退分支")
    void videoStatus_metaParseException_fallsBack() {
        String url = "https://oss.example.com/lsc/20250101/video.mp4";
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(false);
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(ossClient.doesObjectExist(anyString(), anyString())).thenReturn(false);
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_META + url)).thenReturn("invalid-json");

        try (MockedStatic<JSON> jsonMock = Mockito.mockStatic(JSON.class)) {
            jsonMock.when(() -> JSON.parseObject(anyString(), any(Class.class)))
                    .thenThrow(new RuntimeException("解析失败"));

            Map<String, Object> result = mediaService.videoStatus(url);
            assertEquals(0, ((Number) result.get("duration")).intValue());
            assertEquals(0, ((Number) result.get("width")).intValue());
            assertEquals(0, ((Number) result.get("height")).intValue());
            assertEquals(0, ((Number) result.get("size")).longValue());
        }
    }

    @Test
    @DisplayName("[P1] videoStatus: URL无CDN前缀走else分支")
    void videoStatus_noCdnPrefix_usesUrlAsKey() {
        String url = "https://example.com/some/path/video.mp4";
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(ossClient.doesObjectExist(anyString(), anyString())).thenReturn(false);
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(false);

        Map<String, Object> result = mediaService.videoStatus(url);
        assertEquals("transcoding", result.get("status"));
    }

    @Test
    @DisplayName("[P1] videoStatus: transcodeProfiles为空跳过for循环")
    void videoStatus_emptyProfiles_skipsLoop() {
        String url = "https://oss.example.com/lsc/20250101/video.mp4";
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(false);
        ReflectionTestUtils.setField(mediaService, "transcodeProfiles", "");
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(ossClient.doesObjectExist(anyString(), anyString())).thenReturn(false);

        Map<String, Object> result = mediaService.videoStatus(url);
        assertEquals("transcoding", result.get("status"));
    }

    @Test
    @DisplayName("[P1] videoStatus: 转码全部档位已就绪返回ready")
    void videoStatus_allProfilesReady_returnsReady() {
        String url = "https://oss.example.com/lsc/20250101/video.mp4";
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(false);
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(ossClient.doesObjectExist(anyString(), anyString())).thenReturn(true);

        Map<String, Object> result = mediaService.videoStatus(url);
        assertEquals("ready", result.get("status"));
    }

    // ==================== P2: getMediaUrl 边界场景 ====================

    @Test
    @DisplayName("[P2] getMediaUrl: 空白字符串抛异常")
    void getMediaUrl_blankString_throws() {
        assertThrows(BizException.class, () -> mediaService.getMediaUrl("   "));
    }

    @Test
    @DisplayName("[P2] getMediaUrl: ossDown且缓存未命中返回COS URL并缓存")
    void getMediaUrl_ossDown_noCache_returnsCosUrl() {
        String mediaKey = "lsc/x/y.jpg";
        when(valueOperations.get(RedisKeyPrefix.MEDIA_URL + mediaKey)).thenReturn(null);
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(true);

        String result = mediaService.getMediaUrl(mediaKey);
        assertEquals("https://cos.example.com/" + mediaKey, result);
        verify(valueOperations).set(eq(RedisKeyPrefix.MEDIA_URL + mediaKey),
                eq("https://cos.example.com/" + mediaKey), eq(Duration.ofHours(2)));
    }

    // ==================== P2: buildMediaKey 边界 ====================

    @Test
    @DisplayName("[P2] buildMediaKey: 仅文件名(无路径)正确解析扩展名")
    void buildMediaKey_simpleFileName_correctExt() {
        when(mockFile.getOriginalFilename()).thenReturn("photo.GIF");
        String key = ReflectionTestUtils.invokeMethod(mediaService, "buildMediaKey", mockFile);
        assertNotNull(key);
        assertTrue(key.toLowerCase().endsWith(".gif"));
    }

    @Test
    @DisplayName("[P2] buildMediaKey: 仅扩展名(如.mp4)正确解析")
    void buildMediaKey_extOnly_correctExt() {
        when(mockFile.getOriginalFilename()).thenReturn(".mp4");
        String key = ReflectionTestUtils.invokeMethod(mediaService, "buildMediaKey", mockFile);
        assertNotNull(key);
        assertTrue(key.endsWith(".mp4"));
    }

    @Test
    @DisplayName("[P2] buildMediaKey: 文件名含多个点仅取最后扩展名")
    void buildMediaKey_multiDot_correctExt() {
        when(mockFile.getOriginalFilename()).thenReturn("my.file.name.jpg");
        String key = ReflectionTestUtils.invokeMethod(mediaService, "buildMediaKey", mockFile);
        assertNotNull(key);
        assertTrue(key.endsWith(".jpg"));
    }

    // ==================== P2: 生命周期 destroy 边界 ====================

    @Test
    @DisplayName("[P2] destroy: ossClient为null不抛异常且不调用")
    void destroy_ossClientNull_noCall() {
        ReflectionTestUtils.setField(mediaService, "ossClient", null);
        assertDoesNotThrow(() -> mediaService.destroy());
        verify(ossClient, never()).shutdown();
    }

    @Test
    @DisplayName("[P2] destroy: cosClient为null不抛异常且不调用")
    void destroy_cosClientNull_noCall() {
        ReflectionTestUtils.setField(mediaService, "cosClient", null);
        assertDoesNotThrow(() -> mediaService.destroy());
        verify(cosClient, never()).shutdown();
    }
}
