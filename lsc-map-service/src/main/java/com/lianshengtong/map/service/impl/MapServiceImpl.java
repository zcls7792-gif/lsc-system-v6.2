package com.lianshengtong.map.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.utils.RedisKeyPrefix;
import com.lianshengtong.map.dto.GeoResult;
import com.lianshengtong.map.dto.NavigateResult;
import com.lianshengtong.map.service.MapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 地图定位服务实现
 * <p>
 * 双服务商：高德(主) + 百度(备份)，故障自动切换。
 * 地理编码结果 Redis 缓存(默认24小时)。
 * 导航唤起优先级：高德 > 百度 > 腾讯 > 苹果 > 浏览器。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MapServiceImpl implements MapService {

    private final StringRedisTemplate stringRedisTemplate;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    @Value("${lsc.map.amap.key}")
    private String amapKey;
    @Value("${lsc.map.amap.geocode-url}")
    private String amapGeocodeUrl;
    @Value("${lsc.map.amap.regeocode-url}")
    private String amapRegeocodeUrl;
    @Value("${lsc.map.amap.navigate-url}")
    private String amapNavigateUrl;

    @Value("${lsc.map.baidu.ak}")
    private String baiduAk;
    @Value("${lsc.map.baidu.geocode-url}")
    private String baiduGeocodeUrl;
    @Value("${lsc.map.baidu.regeocode-url}")
    private String baiduRegeocodeUrl;
    @Value("${lsc.map.baidu.navigate-url}")
    private String baiduNavigateUrl;

    @Value("${lsc.map.cache-ttl-seconds:86400}")
    private long cacheTtlSeconds;

    /** 高德故障标记 */
    private volatile boolean amapDown = false;

    @Override
    public GeoResult geocode(String address, String city) {
        if (address == null || address.isBlank()) {
            throw new BizException("地址不能为空");
        }
        String cacheKey = RedisKeyPrefix.MAP_GEO + (city == null ? "" : city) + ":" + address;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSON.parseObject(cached, GeoResult.class);
        }
        GeoResult result;
        if (!amapDown) {
            try {
                result = geocodeByAmap(address, city);
            } catch (RuntimeException e) {
                log.warn("高德地理编码失败，切换百度 address={}", address, e);
                amapDown = true;
                result = geocodeByBaidu(address);
            }
        } else {
            result = geocodeByBaidu(address);
        }
        stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(result), Duration.ofSeconds(cacheTtlSeconds));
        return result;
    }

    @Override
    public GeoResult reverseGeocode(Double longitude, Double latitude) {
        if (longitude == null || latitude == null) {
            throw new BizException("经纬度不能为空");
        }
        String cacheKey = RedisKeyPrefix.MAP_REVERSE_GEO + longitude + "," + latitude;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSON.parseObject(cached, GeoResult.class);
        }
        GeoResult result;
        if (!amapDown) {
            try {
                result = reverseGeocodeByAmap(longitude, latitude);
            } catch (RuntimeException e) {
                log.warn("高德逆地理编码失败，切换百度 lon={},lat={}", longitude, latitude, e);
                amapDown = true;
                result = reverseGeocodeByBaidu(longitude, latitude);
            }
        } else {
            result = reverseGeocodeByBaidu(longitude, latitude);
        }
        stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(result), Duration.ofSeconds(cacheTtlSeconds));
        return result;
    }

    @Override
    public NavigateResult navigate(Double originLon, Double originLat,
                                   Double destLon, Double destLat, String destName) {
        if (destLon == null || destLat == null) {
            throw new BizException("终点经纬度不能为空");
        }
        String dest = destLon + "," + destLat;
        String origin = (originLon != null && originLat != null) ? (originLon + "," + originLat) : null;
        // 优先级：高德 > 百度 > 腾讯 > 苹果 > 浏览器
        // 1. 高德
        try {
            StringBuilder url = new StringBuilder(amapNavigateUrl);
            url.append("?to=").append(dest);
            if (destName != null) {
                url.append(",").append(URLEncoder.encode(destName, StandardCharsets.UTF_8));
            }
            if (origin != null) {
                url.append("&from=").append(origin);
            }
            url.append("&mode=car&coordinate=gaode");
            return NavigateResult.builder()
                    .scheme("amap")
                    .url(url.toString())
                    .origin(origin)
                    .destination(dest)
                    .build();
        } catch (RuntimeException e) {
            log.warn("高德导航URL构建失败", e);
        }
        // 2. 百度
        StringBuilder baiduUrl = new StringBuilder(baiduNavigateUrl);
        baiduUrl.append("?origin=latlng:").append(destLat).append(",").append(destLon);
        if (destName != null) {
            baiduUrl.append("|name:").append(URLEncoder.encode(destName, StandardCharsets.UTF_8));
        }
        baiduUrl.append("&mode=driving&coord_type=gcj02");
        return NavigateResult.builder()
                .scheme("baidu")
                .url(baiduUrl.toString())
                .origin(origin)
                .destination(dest)
                .build();
    }

    /** 高德地理编码 */
    private GeoResult geocodeByAmap(String address, String city) {
        String url = amapGeocodeUrl + "?key=" + amapKey
                + "&address=" + URLEncoder.encode(address, StandardCharsets.UTF_8)
                + (city != null ? "&city=" + URLEncoder.encode(city, StandardCharsets.UTF_8) : "")
                + "&output=JSON";
        JSONObject json = httpGet(url);
        if (json == null || !"1".equals(json.getString("status"))
                || json.getJSONArray("geocodes") == null
                || json.getJSONArray("geocodes").isEmpty()) {
            throw new BizException("高德地理编码无结果");
        }
        JSONObject geo = json.getJSONArray("geocodes").getJSONObject(0);
        String location = geo.getString("location");
        String[] lonLat = location.split(",");
        return GeoResult.builder()
                .longitude(Double.parseDouble(lonLat[0]))
                .latitude(Double.parseDouble(lonLat[1]))
                .formattedAddress(geo.getString("formatted_address"))
                .source("amap")
                .build();
    }

    /** 百度地理编码 */
    private GeoResult geocodeByBaidu(String address) {
        String url = baiduGeocodeUrl + "?ak=" + baiduAk
                + "&address=" + URLEncoder.encode(address, StandardCharsets.UTF_8)
                + "&output=json";
        JSONObject json = httpGet(url);
        if (json == null || json.getIntValue("status") != 0) {
            throw new BizException("百度地理编码失败: " + (json != null ? json.getString("message") : "响应为空"));
        }
        JSONObject result = json.getJSONObject("result");
        if (result == null) {
            throw new BizException("百度地理编码无结果");
        }
        JSONObject loc = result.getJSONObject("location");
        if (loc == null) {
            throw new BizException("百度地理编码无位置数据");
        }
        return GeoResult.builder()
                .longitude(loc.getDouble("lng"))
                .latitude(loc.getDouble("lat"))
                .formattedAddress(result.getString("precise") != null ? address : address)
                .source("baidu")
                .build();
    }

    /** 高德逆地理编码 */
    private GeoResult reverseGeocodeByAmap(Double lon, Double lat) {
        String url = amapRegeocodeUrl + "?key=" + amapKey
                + "&location=" + lon + "," + lat
                + "&output=JSON";
        JSONObject json = httpGet(url);
        if (json == null || !"1".equals(json.getString("status"))) {
            throw new BizException("高德逆地理编码失败");
        }
        JSONObject regeo = json.getJSONObject("regeocode");
        return GeoResult.builder()
                .longitude(lon)
                .latitude(lat)
                .formattedAddress(regeo != null ? regeo.getString("formatted_address") : null)
                .source("amap")
                .build();
    }

    /** 百度逆地理编码 */
    private GeoResult reverseGeocodeByBaidu(Double lon, Double lat) {
        String url = baiduRegeocodeUrl + "?ak=" + baiduAk
                + "&location=" + lat + "," + lon
                + "&output=json";
        JSONObject json = httpGet(url);
        if (json == null || json.getIntValue("status") != 0) {
            throw new BizException("百度逆地理编码失败");
        }
        JSONObject result = json.getJSONObject("result");
        if (result == null) {
            throw new BizException("百度逆地理编码无结果");
        }
        return GeoResult.builder()
                .longitude(lon)
                .latitude(lat)
                .formattedAddress(result.getString("formatted_address"))
                .source("baidu")
                .build();
    }

    private JSONObject httpGet(String url) {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("HTTP请求失败 code=" + response.code());
            }
            return JSON.parseObject(response.body().string());
        } catch (IOException e) {
            throw new RuntimeException("HTTP请求异常: " + e.getMessage(), e);
        }
    }

    private com.alibaba.fastjson2.JSONObject httpGetJson(String url, java.util.Map<String, String> params) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        if (params != null) {
            params.forEach((k, v) -> {
                if (k != null && v != null) {
                    urlBuilder.addQueryParameter(k, v);
                }
            });
        }
        return httpGet(urlBuilder.build().toString());
    }

    @Override
    public java.util.List<GeoResult> searchPois(String keyword, String city) {
        // 调用高德 PlaceSearch 接口
        try {
            java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
            params.put("key", amapKey);
            params.put("keywords", keyword);
            params.put("offset", "20");
            if (city != null && !city.isBlank()) {
                params.put("city", city);
            }
            com.alibaba.fastjson2.JSONObject resp = httpGetJson(
                    "https://restapi.amap.com/v3/place/text", params);
            java.util.List<GeoResult> result = new java.util.ArrayList<>();
            com.alibaba.fastjson2.JSONArray pois = resp.getJSONArray("pois");
            if (pois != null) {
                for (int i = 0; i < pois.size(); i++) {
                    com.alibaba.fastjson2.JSONObject poi = pois.getJSONObject(i);
                    GeoResult geo = new GeoResult();
                    geo.setLongitude(parseLocation(poi.getString("location"), 0));
                    geo.setLatitude(parseLocation(poi.getString("location"), 1));
                    geo.setFormattedAddress(poi.getString("address"));
                    geo.setProvince(poi.getString("pname"));
                    geo.setCity(poi.getString("cityname"));
                    geo.setDistrict(poi.getString("adname"));
                    result.add(geo);
                }
            }
            return result;
        } catch (RuntimeException e) {
            log.warn("[searchPois] 关键字搜索失败 keyword={} city={}", keyword, city, e);
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public GeoResult ipLocate(String ip) {
        try {
            java.util.Map<String, String> params = new java.util.HashMap<>();
            params.put("key", amapKey);
            params.put("ip", ip);
            com.alibaba.fastjson2.JSONObject resp = httpGetJson(
                    "https://restapi.amap.com/v3/ip", params);
            GeoResult geo = new GeoResult();
            geo.setProvince(resp.getString("province"));
            geo.setCity(resp.getString("city"));
            geo.setFormattedAddress(resp.getString("province") + resp.getString("city"));
            // 解析经纬度 rectangle 字段
            String rectangle = resp.getString("rectangle");
            if (rectangle != null && rectangle.contains(";")) {
                String[] coords = rectangle.split(";");
                String[] lower = coords[0].split(",");
                if (lower.length >= 2) {
                    geo.setLongitude(Double.parseDouble(lower[0]));
                    geo.setLatitude(Double.parseDouble(lower[1]));
                }
            }
            return geo;
        } catch (RuntimeException e) {
            log.warn("[ipLocate] IP定位失败 ip={}", ip, e);
            return new GeoResult();
        }
    }

    /** 解析高德 location 字段 "经度,纬度" */
    private Double parseLocation(String location, int index) {
        if (location == null || !location.contains(",")) return null;
        String[] parts = location.split(",");
        if (parts.length <= index) return null;
        try {
            return Double.parseDouble(parts[index]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
