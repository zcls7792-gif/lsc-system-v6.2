package com.lianshengtong.media.service.impl;

import com.aliyun.oss.OSS;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.utils.RedisKeyPrefix;
import com.lianshengtong.media.dto.MediaUploadResult;
import com.qcloud.cos.COSClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("媒体服务单元测试")
class MediaServiceImplTest {

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    @Mock
    MultipartFile mockFile;

    @Mock
    OSS ossClient;

    private COSClient cosClient;

    @InjectMocks
    MediaServiceImpl mediaService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mediaService, "ossCdn", "https://oss.example.com");
        ReflectionTestUtils.setField(mediaService, "cosCdn", "https://cos.example.com");
        ReflectionTestUtils.setField(mediaService, "imageMaxMb", 10L);
        ReflectionTestUtils.setField(mediaService, "videoMaxMb", 100L);
        ReflectionTestUtils.setField(mediaService, "imageAllowedTypes", "jpg,jpeg,png,gif,webp");
        ReflectionTestUtils.setField(mediaService, "videoAllowedTypes", "mp4,mov,avi");
        ReflectionTestUtils.setField(mediaService, "transcodeProfiles", "720p,480p,360p");
        ReflectionTestUtils.setField(mediaService, "ossBucket", "test-oss-bucket");
        ReflectionTestUtils.setField(mediaService, "cosBucket", "test-cos-bucket");

        ReflectionTestUtils.setField(mediaService, "ossDown", false);

        cosClient = mock(COSClient.class);
        ReflectionTestUtils.setField(mediaService, "ossClient", ossClient);
        ReflectionTestUtils.setField(mediaService, "cosClient", cosClient);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== 原有10个测试 ====================

    @Test
    @DisplayName("构建媒体key - 验证key格式包含日期前缀和UUID")
    void testBuildMediaKey() {
        lenient().when(mockFile.isEmpty()).thenReturn(false);
        lenient().when(mockFile.getSize()).thenReturn(1024L * 1024L);
        when(mockFile.getOriginalFilename()).thenReturn("test.jpg");
        lenient().when(mockFile.getContentType()).thenReturn("image/jpeg");

        String key = ReflectionTestUtils.invokeMethod(mediaService, "buildMediaKey", mockFile);

        assertNotNull(key);
        assertTrue(key.startsWith("lsc/"), "key应以 lsc/ 开头");
        assertTrue(key.contains("test") || key.matches(".*\\d{8}.*"), "key应包含日期路径");
        assertTrue(key.endsWith(".jpg"), "key应以 .jpg 结尾");
        String[] parts = key.split("/");
        assertTrue(parts.length >= 3, "key应包含至少3段路径");
        assertTrue(parts[1].matches("\\d{8}"), "第二段应为yyyyMMdd日期格式");
    }

    @Test
    @DisplayName("校验合法JPG文件 - 不抛异常")
    void testValidateFile_ValidJpg() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L * 1024L);
        when(mockFile.getOriginalFilename()).thenReturn("test.jpg");
        when(mockFile.getContentType()).thenReturn("image/jpeg");

        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(mediaService, "validateFile", mockFile, 10L, "jpg,jpeg,png,gif,webp", "image")
        );
    }

    @Test
    @DisplayName("校验空文件 - 抛出BizException")
    void testValidateFile_EmptyFile() {
        when(mockFile.isEmpty()).thenReturn(true);

        BizException exception = assertThrows(BizException.class, () ->
                ReflectionTestUtils.invokeMethod(mediaService, "validateFile", mockFile, 10L, "jpg,jpeg,png,gif,webp", "image")
        );

        assertTrue(exception.getMessage().contains("文件不能为空"));
    }

    @Test
    @DisplayName("校验超大文件 - 抛出BizException")
    void testValidateFile_TooLarge() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(11L * 1024L * 1024L);
        lenient().when(mockFile.getOriginalFilename()).thenReturn("test.jpg");
        lenient().when(mockFile.getContentType()).thenReturn("image/jpeg");

        BizException exception = assertThrows(BizException.class, () ->
                ReflectionTestUtils.invokeMethod(mediaService, "validateFile", mockFile, 10L, "jpg,jpeg,png,gif,webp", "image")
        );

        assertTrue(exception.getMessage().contains("大小超过"));
    }

    @Test
    @DisplayName("校验非法扩展名 - 抛出BizException")
    void testValidateFile_InvalidExt() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L * 1024L);
        when(mockFile.getOriginalFilename()).thenReturn("test.bmp");
        lenient().when(mockFile.getContentType()).thenReturn("image/bmp");

        BizException exception = assertThrows(BizException.class, () ->
                ReflectionTestUtils.invokeMethod(mediaService, "validateFile", mockFile, 10L, "jpg,jpeg,png,gif,webp", "image")
        );

        assertTrue(exception.getMessage().contains("不支持的"));
    }

    @Test
    @DisplayName("校验Content-Type与扩展名不匹配 - 抛出BizException")
    void testValidateFile_ContentMismatch() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L * 1024L);
        when(mockFile.getOriginalFilename()).thenReturn("test.jpg");
        when(mockFile.getContentType()).thenReturn("text/plain");

        BizException exception = assertThrows(BizException.class, () ->
                ReflectionTestUtils.invokeMethod(mediaService, "validateFile", mockFile, 10L, "jpg,jpeg,png,gif,webp", "image")
        );

        assertTrue(exception.getMessage().contains("格式校验失败"));
    }

    @Test
    @DisplayName("Content-Type兼容性校验 - JPG匹配image/jpeg返回true")
    void testIsContentTypeCompatible_JpgImage() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/jpeg", "jpg");
        assertTrue(result);
    }

    @Test
    @DisplayName("Content-Type兼容性校验 - PNG匹配image/png返回true")
    void testIsContentTypeCompatible_PngImage() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "image/png", "png");
        assertTrue(result);
    }

    @Test
    @DisplayName("Content-Type兼容性校验 - 扩展名与Content-Type不匹配返回false")
    void testIsContentTypeCompatible_Mismatch() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "text/plain", "jpg");
        assertFalse(result);
    }

    @Test
    @DisplayName("缓存URL - 验证Redis set被正确调用")
    void testCacheUrl() {
        String mediaKey = "lsc/20250101/abc123.jpg";
        String url = "https://oss.example.com/lsc/20250101/abc123.jpg";

        ReflectionTestUtils.invokeMethod(mediaService, "cacheUrl", mediaKey, url);

        verify(valueOperations).set(
                eq(RedisKeyPrefix.MEDIA_URL + mediaKey),
                eq(url),
                eq(Duration.ofHours(2))
        );
    }

    // ==================== 新增测试: uploadImage ====================

    @Test
    @DisplayName("uploadImage - 合法JPG文件上传成功")
    void testUploadImage_ValidJpg_Success() throws Exception {
        byte[] fileBytes = "test-image-content".getBytes();
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) fileBytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("photo.jpg");
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getBytes()).thenReturn(fileBytes);

        MediaUploadResult result = mediaService.uploadImage(mockFile);

        assertNotNull(result);
        assertEquals("image", result.getType());
        assertNotNull(result.getMediaKey());
        assertTrue(result.getMediaKey().startsWith("lsc/"));
        assertTrue(result.getMediaKey().endsWith(".jpg"));
        assertNotNull(result.getPrimaryUrl());
        assertTrue(result.getPrimaryUrl().startsWith("https://oss.example.com/"));
        verify(ossClient).putObject(eq("test-oss-bucket"), anyString(), any(java.io.InputStream.class), any(com.aliyun.oss.model.ObjectMetadata.class));
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofHours(2)));
    }

    @Test
    @DisplayName("uploadImage - 文件超过大小限制抛出异常")
    void testUploadImage_FileTooLarge_ThrowsException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(11L * 1024L * 1024L);
        lenient().when(mockFile.getOriginalFilename()).thenReturn("big.jpg");
        lenient().when(mockFile.getContentType()).thenReturn("image/jpeg");

        BizException exception = assertThrows(BizException.class,
                () -> mediaService.uploadImage(mockFile));

        assertTrue(exception.getMessage().contains("大小超过"));
        verify(ossClient, never()).putObject(anyString(), anyString(), any(java.io.InputStream.class), any(com.aliyun.oss.model.ObjectMetadata.class));
    }

    @Test
    @DisplayName("uploadImage - 不支持的文件类型抛出异常")
    void testUploadImage_UnsupportedType_ThrowsException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L * 1024L);
        when(mockFile.getOriginalFilename()).thenReturn("test.bmp");
        lenient().when(mockFile.getContentType()).thenReturn("image/bmp");

        BizException exception = assertThrows(BizException.class,
                () -> mediaService.uploadImage(mockFile));

        assertTrue(exception.getMessage().contains("不支持的"));
    }

    @Test
    @DisplayName("uploadImage - null文件抛出异常")
    void testUploadImage_NullFile_ThrowsException() {
        BizException exception = assertThrows(BizException.class,
                () -> mediaService.uploadImage(null));

        assertTrue(exception.getMessage().contains("文件不能为空"));
    }

    @Test
    @DisplayName("uploadImage - 空文件抛出异常")
    void testUploadImage_EmptyFile_ThrowsException() {
        when(mockFile.isEmpty()).thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> mediaService.uploadImage(mockFile));

        assertTrue(exception.getMessage().contains("文件不能为空"));
    }

    @Test
    @DisplayName("uploadImage - Content-Type与扩展名不匹配抛出异常")
    void testUploadImage_ContentTypeMismatch_ThrowsException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L * 1024L);
        when(mockFile.getOriginalFilename()).thenReturn("test.jpg");
        when(mockFile.getContentType()).thenReturn("text/plain");

        BizException exception = assertThrows(BizException.class,
                () -> mediaService.uploadImage(mockFile));

        assertTrue(exception.getMessage().contains("格式校验失败"));
    }

    @Test
    @DisplayName("uploadImage - OSS故障时切换COS为主存储")
    void testUploadImage_OssDown_SwitchesToCos() throws Exception {
        byte[] fileBytes = "oss-fallback-content".getBytes();
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) fileBytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("photo.jpg");
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getBytes()).thenReturn(fileBytes);

        ReflectionTestUtils.setField(mediaService, "ossDown", true);

        MediaUploadResult result = mediaService.uploadImage(mockFile);

        assertNotNull(result);
        assertEquals("image", result.getType());
        assertTrue(result.getPrimaryUrl().startsWith("https://cos.example.com/"),
                "主URL应以COS CDN开头");
        verify(ossClient, never()).putObject(anyString(), anyString(), any(java.io.InputStream.class), any(com.aliyun.oss.model.ObjectMetadata.class));
        verify(cosClient).putObject(any(com.qcloud.cos.model.PutObjectRequest.class));
    }

    // ==================== 新增测试: uploadVideo ====================

    @Test
    @DisplayName("uploadVideo - 合法MP4文件上传成功")
    void testUploadVideo_ValidMp4_Success() throws Exception {
        byte[] fileBytes = "video-content".getBytes();
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) fileBytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("clip.mp4");
        when(mockFile.getContentType()).thenReturn("video/mp4");
        when(mockFile.getBytes()).thenReturn(fileBytes);

        MediaUploadResult result = mediaService.uploadVideo(mockFile);

        assertNotNull(result);
        assertEquals("video", result.getType());
        assertNotNull(result.getMediaKey());
        assertTrue(result.getMediaKey().endsWith(".mp4"));
        assertNotNull(result.getPrimaryUrl());
        assertTrue(result.getPrimaryUrl().startsWith("https://oss.example.com/"));
        assertNotNull(result.getTranscodeUrls());
        assertFalse(result.getTranscodeUrls().isEmpty());
        verify(ossClient).putObject(eq("test-oss-bucket"), anyString(), any(java.io.InputStream.class), any(com.aliyun.oss.model.ObjectMetadata.class));
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofHours(2)));
    }

    @Test
    @DisplayName("uploadVideo - 视频超过大小限制抛出异常")
    void testUploadVideo_VideoTooLarge_ThrowsException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(101L * 1024L * 1024L);
        lenient().when(mockFile.getOriginalFilename()).thenReturn("big.mp4");
        lenient().when(mockFile.getContentType()).thenReturn("video/mp4");

        BizException exception = assertThrows(BizException.class,
                () -> mediaService.uploadVideo(mockFile));

        assertTrue(exception.getMessage().contains("大小超过"));
    }

    @Test
    @DisplayName("uploadVideo - 不支持的视频类型抛出异常")
    void testUploadVideo_UnsupportedType_ThrowsException() {
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(1024L * 1024L);
        when(mockFile.getOriginalFilename()).thenReturn("video.wmv");
        lenient().when(mockFile.getContentType()).thenReturn("video/x-ms-wmv");

        BizException exception = assertThrows(BizException.class,
                () -> mediaService.uploadVideo(mockFile));

        assertTrue(exception.getMessage().contains("不支持的"));
    }

    @Test
    @DisplayName("uploadVideo - 生成三档转码URL")
    void testUploadVideo_GeneratesTranscodeUrls() throws Exception {
        byte[] fileBytes = "transcode-video".getBytes();
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) fileBytes.length);
        when(mockFile.getOriginalFilename()).thenReturn("clip.mp4");
        when(mockFile.getContentType()).thenReturn("video/mp4");
        when(mockFile.getBytes()).thenReturn(fileBytes);

        MediaUploadResult result = mediaService.uploadVideo(mockFile);

        assertNotNull(result.getTranscodeUrls());
        assertEquals(3, result.getTranscodeUrls().size());
        assertEquals("720p", result.getTranscodeUrls().get(0).getProfile());
        assertEquals("480p", result.getTranscodeUrls().get(1).getProfile());
        assertEquals("360p", result.getTranscodeUrls().get(2).getProfile());
        for (MediaUploadResult.TranscodeResult tr : result.getTranscodeUrls()) {
            assertNotNull(tr.getUrl());
            assertTrue(tr.getUrl().startsWith("https://oss.example.com/"));
            assertTrue(tr.getUrl().endsWith("_" + tr.getProfile() + ".mp4"));
        }
    }

    // ==================== 新增测试: getMediaUrl ====================

    @Test
    @DisplayName("getMediaUrl - 缓存命中直接返回不调用OSS")
    void testGetMediaUrl_CachedUrl_ReturnsWithoutCallingOss() {
        String mediaKey = "lsc/20250101/abc123.jpg";
        String cachedUrl = "https://oss.example.com/lsc/20250101/abc123.jpg";
        when(valueOperations.get(RedisKeyPrefix.MEDIA_URL + mediaKey)).thenReturn(cachedUrl);

        String result = mediaService.getMediaUrl(mediaKey);

        assertEquals(cachedUrl, result);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        verify(ossClient, never()).putObject(anyString(), anyString(), any(java.io.InputStream.class), any(com.aliyun.oss.model.ObjectMetadata.class));
    }

    @Test
    @DisplayName("getMediaUrl - 缓存未命中返回OSS CDN URL")
    void testGetMediaUrl_CacheMiss_ReturnsOssUrl() {
        String mediaKey = "lsc/20250101/abc123.jpg";
        when(valueOperations.get(RedisKeyPrefix.MEDIA_URL + mediaKey)).thenReturn(null);

        String result = mediaService.getMediaUrl(mediaKey);

        assertEquals("https://oss.example.com/" + mediaKey, result);
        verify(valueOperations).set(
                eq(RedisKeyPrefix.MEDIA_URL + mediaKey),
                eq("https://oss.example.com/" + mediaKey),
                eq(Duration.ofHours(2))
        );
    }

    @Test
    @DisplayName("getMediaUrl - ossDown时返回COS CDN URL")
    void testGetMediaUrl_OssDown_ReturnsCosUrl() {
        String mediaKey = "lsc/20250101/abc123.jpg";
        when(valueOperations.get(RedisKeyPrefix.MEDIA_URL + mediaKey)).thenReturn(null);
        ReflectionTestUtils.setField(mediaService, "ossDown", true);

        String result = mediaService.getMediaUrl(mediaKey);

        assertEquals("https://cos.example.com/" + mediaKey, result);
    }

    // ==================== 新增测试: videoStatus ====================

    @Test
    @DisplayName("videoStatus - 转码完成返回ready状态")
    void testVideoStatus_ReturnsReadyWhenTranscodingComplete() {
        String url = "https://oss.example.com/lsc/20250101/video.mp4";
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(ossClient.doesObjectExist(eq("test-oss-bucket"), contains("720p"))).thenReturn(true);

        Map<String, Object> result = mediaService.videoStatus(url);

        assertEquals("ready", result.get("status"));
        assertEquals(url, result.get("url"));
    }

    @Test
    @DisplayName("videoStatus - 转码中返回transcoding状态")
    void testVideoStatus_ReturnsTranscodingWhenNotReady() {
        String url = "https://oss.example.com/lsc/20250101/video.mp4";
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(null);
        when(ossClient.doesObjectExist(anyString(), anyString())).thenReturn(false);

        Map<String, Object> result = mediaService.videoStatus(url);

        assertEquals("transcoding", result.get("status"));
        assertEquals(url, result.get("url"));
    }

    @Test
    @DisplayName("videoStatus - 空/null URL抛出异常")
    void testVideoStatus_HandlesEmptyUrl() {
        assertThrows(BizException.class, () -> mediaService.videoStatus(""));
        assertThrows(BizException.class, () -> mediaService.videoStatus(null));
    }

    @Test
    @DisplayName("videoStatus - 缓存命中直接返回结果")
    void testVideoStatus_CacheHit_ReturnsCachedResult() {
        String url = "https://oss.example.com/lsc/20250101/video.mp4";
        String cachedJson = "{\"url\":\"" + url + "\",\"status\":\"ready\"}";
        when(valueOperations.get(RedisKeyPrefix.MEDIA_VIDEO_STATUS + url)).thenReturn(cachedJson);

        Map<String, Object> result = mediaService.videoStatus(url);

        assertEquals("ready", result.get("status"));
        assertEquals(url, result.get("url"));
    }

    // ==================== 新增测试: isContentTypeCompatible 视频类型 ====================

    @Test
    @DisplayName("Content-Type兼容性校验 - MP4匹配video/mp4返回true")
    void testIsContentTypeCompatible_Mp4Video() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/mp4", "mp4");
        assertTrue(result);
    }

    @Test
    @DisplayName("Content-Type兼容性校验 - MOV匹配video/quicktime返回true")
    void testIsContentTypeCompatible_MovVideo() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/quicktime", "mov");
        assertTrue(result);
    }

    @Test
    @DisplayName("Content-Type兼容性校验 - AVI匹配video/x-msvideo返回true")
    void testIsContentTypeCompatible_AviVideo() {
        Boolean result = ReflectionTestUtils.invokeMethod(mediaService, "isContentTypeCompatible", "video/x-msvideo", "avi");
        assertTrue(result);
    }
}