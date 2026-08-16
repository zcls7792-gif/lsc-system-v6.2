package com.lianshengtong.map.service;

import com.lianshengtong.map.dto.GeoResult;
import com.lianshengtong.map.dto.NavigateResult;

import java.util.List;

/**
 * 地图定位服务接口
 * <p>地理编码(结果缓存)、逆地理编码、导航唤起；双服务商(高德+百度)故障自动切换。</p>
 */
public interface MapService {

    /**
     * 地理编码(地址 -> 经纬度)
     * <p>结果缓存，主服务商高德，故障自动切换百度。</p>
     *
     * @param address 地址
     * @param city    城市(可空)
     * @return 地理编码结果
     */
    GeoResult geocode(String address, String city);

    /**
     * 逆地理编码(经纬度 -> 地址)
     *
     * @param longitude 经度
     * @param latitude  纬度
     * @return 地理编码结果
     */
    GeoResult reverseGeocode(Double longitude, Double latitude);

    /**
     * 导航唤起
     * <p>优先级：高德 > 百度 > 腾讯 > 苹果 > 浏览器。</p>
     *
     * @param originLon      起点经度(可空，空则用户当前位置)
     * @param originLat      起点纬度
     * @param destLon        终点经度
     * @param destLat        终点纬度
     * @param destName       终点名称
     * @return 导航唤起结果
     */
    NavigateResult navigate(Double originLon, Double originLat,
                            Double destLon, Double destLat, String destName);

    /**
     * 关键字搜索 POI
     *
     * @param keyword 关键字
     * @param city    城市(可空)
     * @return POI 列表
     */
    List<GeoResult> searchPois(String keyword, String city);

    /**
     * IP 定位(粗略城市定位)
     *
     * @param ip IP 地址
     * @return 地理编码结果
     */
    GeoResult ipLocate(String ip);
}
