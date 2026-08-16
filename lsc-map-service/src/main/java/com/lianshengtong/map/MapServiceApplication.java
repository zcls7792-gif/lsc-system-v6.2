package com.lianshengtong.map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 地图定位服务启动类
 * <p>
 * 职责：地理编码(地址->经纬度，结果缓存)、逆地理编码、导航唤起(高德>百度>腾讯>苹果>浏览器)。
 * 双服务商(高德+百度)，故障自动切换。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.lianshengtong.map", "com.lianshengtong.common"})
@EnableDiscoveryClient
public class MapServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MapServiceApplication.class, args);
    }
}
