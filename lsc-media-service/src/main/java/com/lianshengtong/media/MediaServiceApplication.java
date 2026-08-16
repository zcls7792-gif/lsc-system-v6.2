package com.lianshengtong.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 媒体资源服务启动类
 * <p>
 * 职责：图片上传(阿里云OSS+腾讯云COS双备份)、视频上传(MP4/MOV/AVI, <100MB, 15-180秒)三档转码(720P/480P/360P)、
 * CDN加速URL获取、单一云服务商故障自动切换。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.lianshengtong.media", "com.lianshengtong.common"})
@EnableDiscoveryClient
@EnableAsync
public class MediaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaServiceApplication.class, args);
    }
}
