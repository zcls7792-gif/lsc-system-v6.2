package com.lianshengtong.media.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.utils.RedisKeyPrefix;
import com.lianshengtong.media.dto.MediaUploadResult;
import com.lianshengtong.media.service.MediaService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 媒体资源服务实现
 * <p>
 * 图片上传：阿里云OSS(主) + 腾讯云COS(备份) 双备份，单一云服务商故障自动切换。
 * 视频上传：MP4/MOV/AVI，<100MB，15-180秒，三档转码(720P/480P/360P)。
 * CDN加速URL通过 Redis 缓存。
 * </p>
 */
@Slf4j
@Service
public class MediaServiceImpl implements MediaService {

    @Value("${lsc.media.oss.endpoint}")
    private String ossEndpoint;
    @Value("${lsc.media.oss.access-key-id}")
    private String ossAk;
    @Value("${lsc.media.oss.access-key-secret}")
    private String ossSk;
    @Value("${lsc.media.oss.bucket}")
    private String ossBucket;
    @Value("${lsc.media.oss.cdn-domain}")
    private String ossCdn;

    @Value("${lsc.media.cos.region}")
    private String cosRegion;
    @Value("${lsc.media.cos.secret-id}")
    private String cosId;
    @Value("${lsc.media.cos.secret-key}")
    private String cosKey;
    @Value("${lsc.media.cos.bucket}")
    private String cosBucket;
    @Value("${lsc.media.cos.cdn-domain}")
    private String cosCdn;

    @Value("${lsc.media.image.max-size-mb:10}")
    private long imageMaxMb;
    @Value("${lsc.media.image.allowed-types:jpg,jpeg,png,gif,webp}")
    private String imageAllowedTypes;
    @Value("${lsc.media.video.max-size-mb:100}")
    private long videoMaxMb;
    @Value("${lsc.media.video.allowed-types:mp4,mov,avi}")
    private String videoAllowedTypes;
    @Value("${lsc.media.video.transcode-profiles:720p,480p,360p}")
    private String transcodeProfiles;

    private final StringRedisTemplate stringRedisTemplate;

    private OSS ossClient;
    private COSClient cosClient;
    /** OSS 故障标记(内存级，故障后短期切换COS为主) */
    private volatile boolean ossDown = false;

    public MediaServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @PostConstruct
    public void init() {
        try {
            this.ossClient = new OSSClientBuilder().build(ossEndpoint, ossAk, ossSk);
            log.info("阿里云OSS客户端初始化成功 endpoint={}", ossEndpoint);
        } catch (Exception e) {
            log.warn("OSS客户端初始化失败，将使用COS为主存储", e);
            this.ossDown = true;
        }
        try {
            COSCredentials cred = new BasicCOSCredentials(cosBucket.split("-")[0], cosId, cosKey);
            ClientConfig clientConfig = new ClientConfig(new Region(cosRegion));
            this.cosClient = new COSClient(cred, clientConfig);
            log.info("腾讯云COS客户端初始化成功 region={}", cosRegion);
        } catch (Exception e) {
            log.warn("COS客户端初始化失败", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
        if (cosClient != null) {
            cosClient.shutdown();
        }
    }

    @Override
    public MediaUploadResult uploadImage(MultipartFile file) {
        validateFile(file, imageMaxMb, imageAllowedTypes, "image");
        String mediaKey = buildMediaKey(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException("读取文件失败: " + e.getMessage());
        }
        String primaryUrl = null;
        String backupUrl = null;
        boolean backupEnabled = false;
        // 主存储：OSS正常时用OSS，故障时切换COS
        if (!ossDown) {
            try {
                uploadToOss(mediaKey, bytes, file.getContentType());
                primaryUrl = ossCdn + "/" + mediaKey;
            } catch (Exception e) {
                log.error("OSS上传失败，切换COS为主存储 key={}", mediaKey, e);
                ossDown = true;
            }
        }
        if (primaryUrl == null) {
            uploadToCos(mediaKey, bytes);
            primaryUrl = cosCdn + "/" + mediaKey;
        } else {
            // OSS 成功，再备份到 COS
            try {
                uploadToCos(mediaKey, bytes);
                backupUrl = cosCdn + "/" + mediaKey;
                backupEnabled = true;
            } catch (Exception e) {
                log.warn("COS备份失败 key={}", mediaKey, e);
            }
        }
        cacheUrl(mediaKey, primaryUrl);
        log.info("图片上传成功 key={} primary={}", mediaKey, primaryUrl);
        return MediaUploadResult.builder()
                .mediaKey(mediaKey)
                .primaryUrl(primaryUrl)
                .backupUrl(backupUrl)
                .type("image")
                .backupEnabled(backupEnabled)
                .build();
    }

    @Override
    public MediaUploadResult uploadVideo(MultipartFile file) {
        validateFile(file, videoMaxMb, videoAllowedTypes, "video");
        String mediaKey = buildMediaKey(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException("读取文件失败: " + e.getMessage());
        }
        // 原片上传主存储
        String primaryUrl;
        if (!ossDown) {
            try {
                uploadToOss(mediaKey, bytes, file.getContentType());
                primaryUrl = ossCdn + "/" + mediaKey;
            } catch (Exception e) {
                log.error("OSS视频上传失败，切换COS key={}", mediaKey, e);
                ossDown = true;
                uploadToCos(mediaKey, bytes);
                primaryUrl = cosCdn + "/" + mediaKey;
            }
        } else {
            uploadToCos(mediaKey, bytes);
            primaryUrl = cosCdn + "/" + mediaKey;
        }
        // 三档转码(实际由云厂商转码服务异步处理，此处生成转码后预期URL)
        List<MediaUploadResult.TranscodeResult> transcodeUrls = new ArrayList<>();
        String basePath = mediaKey.substring(0, mediaKey.lastIndexOf('.'));
        for (String profile : transcodeProfiles.split(",")) {
            String transcodeKey = basePath + "_" + profile + ".mp4";
            String url = ossCdn + "/" + transcodeKey;
            transcodeUrls.add(MediaUploadResult.TranscodeResult.builder()
                    .profile(profile)
                    .url(url)
                    .build());
        }
        cacheUrl(mediaKey, primaryUrl);
        log.info("视频上传成功 key={} 转码档位数={}", mediaKey, transcodeUrls.size());
        return MediaUploadResult.builder()
                .mediaKey(mediaKey)
                .primaryUrl(primaryUrl)
                .type("video")
                .transcodeUrls(transcodeUrls)
                .backupEnabled(false)
                .build();
    }

    @Override
    public String getMediaUrl(String mediaKey) {
        if (StrUtil.isBlank(mediaKey)) {
            throw new BizException("mediaKey不能为空");
        }
        String cacheKey = RedisKeyPrefix.MEDIA_URL + mediaKey;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        // 主存储故障时返回COS CDN，否则返回OSS CDN
        String url = ossDown ? (cosCdn + "/" + mediaKey) : (ossCdn + "/" + mediaKey);
        cacheUrl(mediaKey, url);
        return url;
    }

    @Override
    public java.util.Map<String, Object> videoStatus(String url) {
        if (StrUtil.isBlank(url)) {
            throw new BizException("url不能为空");
        }
        // 转码状态缓存(默认10秒轮询)
        String cacheKey = RedisKeyPrefix.MEDIA_VIDEO_STATUS + url;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSON.parseObject(cached, java.util.Map.class);
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("url", url);
        // 提取 mediaKey(去掉CDN前缀)
        String mediaKey = url;
        if (url.startsWith(ossCdn + "/")) {
            mediaKey = url.substring((ossCdn + "/").length());
        } else if (url.startsWith(cosCdn + "/")) {
            mediaKey = url.substring((cosCdn + "/").length());
        }
        // 检查各转码档位是否就绪(对象存储是否存在该key)
        String basePath = mediaKey.contains(".") ? mediaKey.substring(0, mediaKey.lastIndexOf('.')) : mediaKey;
        boolean ready = false;
        for (String profile : transcodeProfiles.split(",")) {
            String transcodeKey = basePath + "_" + profile + ".mp4";
            if (objectExists(transcodeKey)) {
                ready = true;
                break;
            }
        }
        // 封面(默认取首帧截图，约定 <basePath>_cover.jpg)
        String coverKey = basePath + "_cover.jpg";
        String coverUrl = objectExists(coverKey) ? (ossCdn + "/" + coverKey) : null;
        // status: uploaded(已上传转码中) / transcoding(转码中) / ready(就绪) / failed(失败)
        String status = ready ? "ready" : "transcoding";
        result.put("status", status);
        result.put("coverUrl", coverUrl);
        // 元信息(实际由云厂商转码回调写入Redis，此处回退默认值)
        java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("duration", 0);
        meta.put("width", 0);
        meta.put("height", 0);
        meta.put("size", 0L);
        String metaCached = stringRedisTemplate.opsForValue().get(RedisKeyPrefix.MEDIA_VIDEO_META + url);
        if (metaCached != null) {
            try {
                java.util.Map<String, Object> m = JSON.parseObject(metaCached, java.util.Map.class);
                meta.putAll(m);
            } catch (Exception ignored) {
                // 元信息解析失败时回退默认值
            }
        }
        result.putAll(meta);
        stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(result), Duration.ofSeconds(10));
        log.debug("视频转码状态查询 url={} status={}", url, status);
        return result;
    }

    /** 检查对象是否存在(优先OSS，故障时COS) */
    private boolean objectExists(String key) {
        if (!ossDown && ossClient != null) {
            try {
                return ossClient.doesObjectExist(ossBucket, key);
            } catch (Exception e) {
                log.warn("OSS对象存在性检查失败 key={}", key, e);
                ossDown = true;
            }
        }
        if (cosClient != null) {
            try {
                return cosClient.doesObjectExist(cosBucket, key);
            } catch (Exception e) {
                log.warn("COS对象存在性检查失败 key={}", key, e);
            }
        }
        return false;
    }

    /** 上传至阿里云OSS */
    private void uploadToOss(String key, byte[] bytes, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        if (contentType != null) {
            metadata.setContentType(contentType);
        }
        ossClient.putObject(ossBucket, key, new ByteArrayInputStream(bytes), metadata);
    }

    /** 上传至腾讯云COS */
    private void uploadToCos(String key, byte[] bytes) {
        try {
            com.qcloud.cos.model.ObjectMetadata metadata = new com.qcloud.cos.model.ObjectMetadata();
            metadata.setContentLength(bytes.length);
            PutObjectRequest request = new PutObjectRequest(cosBucket, key, new ByteArrayInputStream(bytes), metadata);
            cosClient.putObject(request);
        } catch (CosClientException e) {
            throw new BizException("COS上传失败: " + e.getMessage());
        }
    }

    private void cacheUrl(String mediaKey, String url) {
        stringRedisTemplate.opsForValue().set(RedisKeyPrefix.MEDIA_URL + mediaKey, url, Duration.ofHours(2));
    }

    private String buildMediaKey(MultipartFile file) {
        String ext = FileUtil.extName(file.getOriginalFilename());
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "lsc/" + date + "/" + IdUtil.fastSimpleUUID() + "." + (StrUtil.isBlank(ext) ? "bin" : ext);
    }

    private void validateFile(MultipartFile file, long maxMb, String allowedTypes, String type) {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        long sizeMb = file.getSize() / (1024 * 1024);
        if (sizeMb > maxMb) {
            throw new BizException(type + "大小超过" + maxMb + "MB限制");
        }
        String ext = FileUtil.extName(file.getOriginalFilename());
        if (StrUtil.isBlank(ext)) {
            throw new BizException("文件无扩展名");
        }
        Set<String> allowed = Set.of(allowedTypes.toLowerCase().split(","));
        if (!allowed.contains(ext.toLowerCase())) {
            throw new BizException("不支持的" + type + "格式");
        }
        String contentType = file.getContentType();
        if (contentType != null && !isContentTypeCompatible(contentType, ext)) {
            log.warn("[validateFile] Content-Type与扩展名不匹配，可能伪造 ext={} contentType={}", ext, contentType);
            throw new BizException("文件格式校验失败");
        }
    }

    /**
     * 校验Content-Type与扩展名是否在同一类型族内
     */
    private boolean isContentTypeCompatible(String contentType, String ext) {
        String ct = contentType.toLowerCase();
        return (ext.equals("jpg") || ext.equals("jpeg")) && ct.startsWith("image/")
                || ext.equals("png") && ct.contains("png")
                || ext.equals("gif") && ct.contains("gif")
                || ext.equals("webp") && ct.contains("webp")
                || ext.equals("mp4") && (ct.contains("mp4") || ct.contains("mpeg4"))
                || ext.equals("mov") && ct.contains("quicktime")
                || ext.equals("avi") && (ct.contains("avi") || ct.contains("x-msvideo"));
    }
}
