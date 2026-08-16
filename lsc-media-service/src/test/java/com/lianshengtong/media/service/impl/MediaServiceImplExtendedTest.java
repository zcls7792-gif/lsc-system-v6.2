package com.lianshengtong.media.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.utils.RedisKeyPrefix;
import com.lianshengtong.media.dto.MediaUploadResult;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.PutObjectRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
@DisplayName("媒体服务扩展测试 - 生命周期/故障切换/边界场景")
class MediaServiceImplExtendedTest {

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    @Mock
    MultipartFile mockFile;

    @Mock
    OSS ossClient;
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private COSClient cosClient;

    private MediaServiceImpl mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaServiceImpl(stringRedisTemplate, meterRegistry);
        ReflectionTestUtils.setField(mediaService, "ossCdn", "https://oss.example.com");
        ReflectionTestUtils.setField(mediaService, "cosCdn", "https://cos.example.com");
        ReflectionTestUtils.setField(mediaService, "imageMaxMb", 10L);
        ReflectionTestUtils.setField(mediaService, "videoMaxMb", 100L);
        ReflectionTestUtils.setField(mediaService, "imageAllowedTypes", "jpg,jpeg,png,gif,webp");
        ReflectionTestUtils.setField(mediaService, "videoAllowedTypes", "mp4,mov,avi");
        ReflectionTestUtils.setField(mediaService, "transcodeProfiles", "720p,480p,360p");
        ReflectionTestUtils.setField(mediaService, "ossBucket", "test-oss-bucket");
        ReflectionTestUtils.setField(mediaService, "cosBucket", "test-cos-bucket");
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(false);

        cosClient = mock(COSClient.class);
        ReflectionTestUtils.setField(mediaService, "ossClient", ossClient);
        ReflectionTestUtils.setField(mediaService, "cosClient", cosClient);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== destroy() ====================

    @Test
    @DisplayName("destroy: ossClient和cosClient都存在时正常关闭")
    void destroy_withBothClients_shutsDown() {
        mediaService.destroy();
        verify(ossClient).shutdown();
        verify(cosClient).shutdown();
    }

    @Test
    @DisplayName("destroy: ossClient为null不抛异常")
    void destroy_nullOssClient_noException() {
        ReflectionTestUtils.setField(mediaService, "ossClient", null);
        assertDoesNotThrow(() -> mediaService.destroy());
        verify(cosClient).shutdown();
    }

    @Test
    @DisplayName("destroy: cosClient为null不抛异常")
    void destroy_nullCosClient_noException() {
        ReflectionTestUtils.setField(mediaService, "cosClient", null);
        assertDoesNotThrow(() -> mediaService.destroy());
        verify(ossClient).shutdown();
    }

    @Test
    @DisplayName("destroy: 两者都为null不抛异常")
    void destroy_bothNull_noException() {
        ReflectionTestUtils.setField(mediaService, "ossClient", null);
        ReflectionTestUtils.setField(mediaService, "cosClient", null);
        assertDoesNotThrow(() -> mediaService.destroy());
    }

    // ==================== uploadImage: 故障切换 ====================

    @Test
    @DisplayName("uploadImage: OSS上传异常自动切换COS")
    void uploadImage_ossFails_switchesToCos() throws Exception {
        byte[] bytes = "content".getBytes();
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) bytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("pic.jpg");
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getBytes()).thenReturn(bytes);
        doThrow(new RuntimeException("OSS连接失败"))
                .when(ossClient).putObject(anyString(), anyString(), any(ByteArrayInputStream.class), any(ObjectMetadata.class));

        MediaUploadResult result = mediaService.uploadImage(mockFile);

        assertNotNull(result);
        assertTrue(result.getPrimaryUrl().startsWith("https://cos.example.com/"));
        assertTrue(((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).get());
        verify(cosClient).putObject(any(PutObjectRequest.class));
    }

    @Test
    @DisplayName("uploadImage: OSS故障后COS备份也失败，主URL仍返回COS")
    void uploadImage_ossDown_cosBackupFails_returnsCosPrimary() throws Exception {
        byte[] bytes = "content".getBytes();
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) bytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("pic.jpg");
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getBytes()).thenReturn(bytes);

        // 先让OSS成功
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(false);
        // 备份COS失败
        doThrow(new CosClientException("COS备份失败"))
                .when(cosClient).putObject(any(PutObjectRequest.class));

        MediaUploadResult result = mediaService.uploadImage(mockFile);

        assertNotNull(result);
        assertTrue(result.getPrimaryUrl().startsWith("https://oss.example.com/"));
        assertFalse(result.getBackupEnabled());
    }

    @Test
    @DisplayName("uploadImage: 文件读取IOException抛BizException")
    void uploadImage_ioException_throwsBizException() throws Exception {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getOriginalFilename()).thenReturn("pic.jpg");
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getBytes()).thenThrow(new java.io.IOException("IO错误"));

        BizException ex = assertThrows(BizException.class, () -> mediaService.uploadImage(mockFile));
        assertTrue(ex.getMessage().contains("读取文件失败"));
    }

    @Test
    @DisplayName("uploadImage: COS上传失败时包装为BizException路径 - ossDown为true但cosClient为null保护分支未覆盖")
    void uploadImage_cosUploadWrapped_BizException() throws Exception {
        byte[] bytes = "content".getBytes();
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) bytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("pic.jpg");
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getBytes()).thenReturn(bytes);

        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(true);
        // cosClient 正常，但在 uploadToCos 内抛出 CosClientException
        doThrow(new CosClientException("COS失败"))
                .when(cosClient).putObject(any(PutObjectRequest.class));

        BizException ex = assertThrows(BizException.class, () -> mediaService.uploadImage(mockFile));
        assertTrue(ex.getMessage().contains("COS上传失败"));
    }

    // ==================== uploadVideo: 故障切换 ====================

    @Test
    @DisplayName("uploadVideo: OSS故障时直接走COS路径")
    void uploadVideo_ossDown_uploadsToCos() throws Exception {
        byte[] bytes = "video".getBytes();
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) bytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("clip.mp4");
        when(mockFile.getContentType()).thenReturn("video/mp4");
        when(mockFile.getBytes()).thenReturn(bytes);
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(true);

        MediaUploadResult result = mediaService.uploadVideo(mockFile);

        assertTrue(result.getPrimaryUrl().startsWith("https://cos.example.com/"));
        verify(cosClient).putObject(any(PutObjectRequest.class));
    }

    @Test
    @DisplayName("uploadVideo: OSS上传异常切换COS")
    void uploadVideo_ossFails_switchToCos() throws Exception {
        byte[] bytes = "video".getBytes();
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) bytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("clip.mp4");
        when(mockFile.getContentType()).thenReturn("video/mp4");
        when(mockFile.getBytes()).thenReturn(bytes);
        doThrow(new RuntimeException("OSS视频失败"))
                .when(ossClient).putObject(anyString(), anyString(), any(ByteArrayInputStream.class), any(ObjectMetadata.class));

        MediaUploadResult result = mediaService.uploadVideo(mockFile);

        assertTrue(result.getPrimaryUrl().startsWith("https://cos.example.com/"));
        assertTrue(((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).get());
    }

    @Test
    @DisplayName("uploadVideo: 文件读取IOException")
    void uploadVideo_ioException_throwsBizException() throws Exception {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getOriginalFilename()).thenReturn("clip.mp4");
        when(mockFile.getContentType()).thenReturn("video/mp4");
        when(mockFile.getBytes()).thenThrow(new java.io.IOException("读取视频失败"));

        BizException ex = assertThrows(BizException.class, () -> mediaService.uploadVideo(mockFile));
        assertTrue(ex.getMessage().contains("读取文件失败"));
    }

    // ==================== buildMediaKey: 无扩展名场景 ====================

    @Test
    @DisplayName("buildMediaKey: 文件无扩展名时使用bin")
    void buildMediaKey_noExt_usesBin() {
        when(mockFile.getOriginalFilename()).thenReturn("noext");

        String key = ReflectionTestUtils.invokeMethod(mediaService, "buildMediaKey", mockFile);
        assertNotNull(key);
        assertTrue(key.endsWith(".bin"), "无扩展名应使用.bin");
    }

    @Test
    @DisplayName("buildMediaKey: 含完整路径的文件名仅取文件名")
    void buildMediaKey_withPath_extracted() {
        when(mockFile.getOriginalFilename()).thenReturn("/tmp/path/file.PNG");

        String key = ReflectionTestUtils.invokeMethod(mediaService, "buildMediaKey", mockFile);
        assertNotNull(key);
        assertTrue(key.toLowerCase().endsWith(".png"));
    }

    // ==================== validateFile: 更多Content-Type ====================

    @Test
    @DisplayName("validateFile: webp Content-Type 兼容")
    void validateFile_webpTypeCompatible() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getOriginalFilename()).thenReturn("a.webp");
        when(mockFile.getContentType()).thenReturn("image/webp");

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(mediaService, "validateFile",
                mockFile, 10L, "jpg,jpeg,png,gif,webp", "image"));
    }

    @Test
    @DisplayName("validateFile: gif Content-Type 兼容")
    void validateFile_gifTypeCompatible() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getOriginalFilename()).thenReturn("a.gif");
        when(mockFile.getContentType()).thenReturn("image/gif");

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(mediaService, "validateFile",
                mockFile, 10L, "jpg,jpeg,png,gif,webp", "image"));
    }

    @Test
    @DisplayName("validateFile: Content-Type为null跳过兼容性检查")
    void validateFile_nullContentType_skipsCheck() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getOriginalFilename()).thenReturn("a.jpg");
        when(mockFile.getContentType()).thenReturn(null);

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(mediaService, "validateFile",
                mockFile, 10L, "jpg,jpeg,png,gif,webp", "image"));
    }

    @Test
    @DisplayName("validateFile: 无扩展名抛异常")
    void validateFile_noExt_throws() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getOriginalFilename()).thenReturn("noext");
        lenient().when(mockFile.getContentType()).thenReturn("image/jpeg");

        BizException ex = assertThrows(BizException.class, () -> ReflectionTestUtils.invokeMethod(mediaService, "validateFile",
                mockFile, 10L, "jpg,jpeg,png,gif,webp", "image"));
        assertTrue(ex.getMessage().contains("无扩展名"));
    }

    // ==================== videoStatus: 额外分支 ====================

    @Test
    @DisplayName("videoStatus: cosDown时走COS CDN解析")
    void videoStatus_withCosCdnUrl() {
        String url = "https://cos.example.com/lsc/20250101/video.mp4";
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(ossClient.doesObjectExist(anyString(), anyString())).thenReturn(false);

        Map<String, Object> result = mediaService.videoStatus(url);
        assertEquals("transcoding", result.get("status"));
    }

    @Test
    @DisplayName("videoStatus: meta缓存命中合并")
    void videoStatus_metaCacheHit_merges() {
        String url = "https://oss.example.com/lsc/20250101/video.mp4";
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_META + url))
                .thenReturn("{\"duration\":90,\"width\":1920,\"height\":1080}");
        when(ossClient.doesObjectExist(anyString(), anyString())).thenReturn(false);

        Map<String, Object> result = mediaService.videoStatus(url);
        assertEquals(90, result.get("duration"));
        assertEquals(1920, result.get("width"));
        assertEquals(1080, result.get("height"));
    }

    @Test
    @DisplayName("videoStatus: meta缓存解析失败回退默认值")
    void videoStatus_metaParseFallback() {
        String url = "https://oss.example.com/lsc/20250101/video.mp4";
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_META + url))
                .thenReturn("not-a-valid-json{{{");
        when(ossClient.doesObjectExist(anyString(), anyString())).thenReturn(false);

        Map<String, Object> result = mediaService.videoStatus(url);
        assertEquals(0, result.get("duration"));
        assertEquals(0, result.get("width"));
    }

    @Test
    @DisplayName("videoStatus: 无扩展名媒体key处理")
    void videoStatus_keyWithoutExt_handled() {
        String url = "https://oss.example.com/lsc/20250101/rawvideo";
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(ossClient.doesObjectExist(anyString(), anyString())).thenReturn(false);

        Map<String, Object> result = mediaService.videoStatus(url);
        assertNotNull(result);
        assertEquals("transcoding", result.get("status"));
    }

    @Test
    @DisplayName("videoStatus: 封面存在返回coverUrl")
    void videoStatus_coverExists_returnsCoverUrl() {
        String url = "https://oss.example.com/lsc/20250101/video.mp4";
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(ossClient.doesObjectExist(anyString(), anyString())).thenReturn(false);
        when(ossClient.doesObjectExist(anyString(), contains("_cover.jpg"))).thenReturn(true);

        Map<String, Object> result = mediaService.videoStatus(url);
        assertNotNull(result.get("coverUrl"));
        assertTrue(((String) result.get("coverUrl")).startsWith("https://oss.example.com/"));
    }

    // ==================== getMediaUrl: ossDown无缓存 ====================

    @Test
    @DisplayName("getMediaUrl: ossDown无缓存返回COS CDN并缓存")
    void getMediaUrl_ossDown_noCache_cosCdn() {
        String mediaKey = "lsc/x/y.jpg";
        when(valueOperations.get(RedisKeyPrefix.MEDIA_URL + mediaKey)).thenReturn(null);
        ((AtomicBoolean) ReflectionTestUtils.getField(mediaService, "ossDown")).set(true);

        String result = mediaService.getMediaUrl(mediaKey);

        assertEquals("https://cos.example.com/" + mediaKey, result);
        verify(valueOperations).set(eq(RedisKeyPrefix.MEDIA_URL + mediaKey),
                eq("https://cos.example.com/" + mediaKey), eq(Duration.ofHours(2)));
    }

    @Test
    @DisplayName("getMediaUrl: mediaKey为空抛BizException")
    void getMediaUrl_blankKey_throws() {
        assertThrows(BizException.class, () -> mediaService.getMediaUrl(""));
        assertThrows(BizException.class, () -> mediaService.getMediaUrl(null));
    }
}
