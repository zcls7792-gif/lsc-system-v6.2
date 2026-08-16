package com.lianshengtong.media.controller;

import com.lianshengtong.common.result.R;
import com.lianshengtong.media.dto.MediaUploadResult;
import com.lianshengtong.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 媒体资源服务接口
 */
@Tag(name = "媒体资源", description = "图片/视频上传(双备份)、CDN加速URL")
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @Operation(summary = "图片上传(阿里云OSS+腾讯云COS双备份)")
    @PostMapping("/upload-image")
    public R<MediaUploadResult> uploadImage(@RequestParam("file") MultipartFile file) {
        return R.ok(mediaService.uploadImage(file));
    }

    @Operation(summary = "视频上传(MP4/MOV/AVI, <100MB, 三档转码720P/480P/360P)")
    @PostMapping("/upload-video")
    public R<MediaUploadResult> uploadVideo(@RequestParam("file") MultipartFile file) {
        return R.ok(mediaService.uploadVideo(file));
    }

    @Operation(summary = "获取CDN加速URL")
    @GetMapping("/url")
    public R<String> getMediaUrl(@RequestParam("mediaKey") String mediaKey) {
        return R.ok(mediaService.getMediaUrl(mediaKey));
    }

    @Operation(summary = "查询视频转码状态(异步轮询)")
    @PostMapping("/video-status")
    public R<Map<String, Object>> videoStatus(@RequestBody Map<String, String> body) {
        String url = body == null ? null : body.get("url");
        return R.ok(mediaService.videoStatus(url));
    }
}
