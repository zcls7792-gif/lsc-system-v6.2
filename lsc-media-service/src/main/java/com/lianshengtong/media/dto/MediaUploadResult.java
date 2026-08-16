package com.lianshengtong.media.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 媒体上传结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 媒体key(对象存储路径) */
    private String mediaKey;

    /** 主存储URL(阿里云OSS CDN) */
    private String primaryUrl;

    /** 备份存储URL(腾讯云COS CDN) */
    private String backupUrl;

    /** 媒体类型 image/video */
    private String type;

    /** 视频转码结果URL(图片为null) */
    private List<TranscodeResult> transcodeUrls;

    /** 是否启用备份(主存储故障时仅主存储) */
    private Boolean backupEnabled;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TranscodeResult implements Serializable {
        /** 档位 720p/480p/360p */
        private String profile;
        /** 转码后URL */
        private String url;
    }
}
