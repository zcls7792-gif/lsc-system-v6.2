package com.lianshengtong.media.service;

import com.lianshengtong.media.dto.MediaUploadResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 媒体资源服务接口
 * <p>图片上传(OSS+COS双备份)、视频上传(三档转码)、CDN加速URL、单云故障自动切换。</p>
 */
public interface MediaService {

    /**
     * 图片上传
     * <p>阿里云OSS(主) + 腾讯云COS(备份) 双备份，OSS故障自动切换至COS为主。</p>
     *
     * @param file 图片文件
     * @return 上传结果(含主备CDN URL)
     */
    MediaUploadResult uploadImage(MultipartFile file);

    /**
     * 视频上传
     * <p>支持 MP4/MOV/AVI，大小 <100MB，时长 15-180秒，三档转码(720P/480P/360P)。</p>
     *
     * @param file 视频文件
     * @return 上传结果(含三档转码URL)
     */
    MediaUploadResult uploadVideo(MultipartFile file);

    /**
     * 获取CDN加速URL
     *
     * @param mediaKey 媒体key
     * @return CDN加速URL
     */
    String getMediaUrl(String mediaKey);

    /**
     * 查询视频转码状态
     * <p>根据视频主URL查询云厂商异步转码进度，返回状态、封面、时长、分辨率等元信息。</p>
     *
     * @param url 视频主URL
     * @return 状态信息(status/coverUrl/duration/width/height/size)
     */
    Map<String, Object> videoStatus(String url);
}
