package com.lianshengtong.map.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.map.dto.GeoResult;
import com.lianshengtong.map.dto.NavigateResult;
import okhttp3.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("地图定位服务单元测试")
class MapServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private MapServiceImpl mapService;

    private final MediaType JSON_MEDIA = MediaType.parse("application/json");

    private OkHttpClient mockHttpClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mapService, "amapKey", "test_amap_key");
        ReflectionTestUtils.setField(mapService, "amapGeocodeUrl", "https://restapi.amap.com/v3/geocode/geo");
        ReflectionTestUtils.setField(mapService, "amapRegeocodeUrl", "https://restapi.amap.com/v3/geocode/regeo");
        ReflectionTestUtils.setField(mapService, "amapNavigateUrl", "https://uri.amap.com/navigation");
        ReflectionTestUtils.setField(mapService, "baiduAk", "test_baidu_ak");
        ReflectionTestUtils.setField(mapService, "baiduGeocodeUrl", "https://api.map.baidu.com/geocoding/v3");
        ReflectionTestUtils.setField(mapService, "baiduRegeocodeUrl", "https://api.map.baidu.com/reverse_geocoding/v3");
        ReflectionTestUtils.setField(mapService, "baiduNavigateUrl", "https://api.map.baidu.com/direction");
        ReflectionTestUtils.setField(mapService, "cacheTtlSeconds", 86400L);

        mockHttpClient = mock(OkHttpClient.class);
        ReflectionTestUtils.setField(mapService, "httpClient", mockHttpClient);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private Response mockAmapGeocodeResponse() throws IOException {
        String body = """
                {"status":"1","geocodes":[{"location":"116.397428,39.90923","formatted_address":"北京市东城区"}]}""";
        return new Response.Builder()
                .request(new Request.Builder().url("https://restapi.amap.com/v3/geocode/geo").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(ResponseBody.create(body, JSON_MEDIA))
                .build();
    }

    private Response mockBaiduGeocodeResponse() throws IOException {
        String body = """
                {"status":0,"result":{"location":{"lng":116.397428,"lat":39.90923},"precise":1}}""";
        return new Response.Builder()
                .request(new Request.Builder().url("https://api.map.baidu.com/geocoding/v3").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(ResponseBody.create(body, JSON_MEDIA))
                .build();
    }

    private Response mockAmapRegeocodeResponse() throws IOException {
        String body = """
                {"status":"1","regeocode":{"formatted_address":"北京市东城区天安门"}}""";
        return new Response.Builder()
                .request(new Request.Builder().url("https://restapi.amap.com/v3/geocode/regeo").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(ResponseBody.create(body, JSON_MEDIA))
                .build();
    }

    private Response mockAmapIpResponse() throws IOException {
        String body = """
                {"status":"1","province":"北京市","city":"北京市","rectangle":"116.397428,39.90923;116.397428,39.90923"}""";
        return new Response.Builder()
                .request(new Request.Builder().url("https://restapi.amap.com/v3/ip").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(ResponseBody.create(body, JSON_MEDIA))
                .build();
    }

    private Response mockAmapPoiResponse() throws IOException {
        String body = """
                {"status":"1","pois":[{"location":"116.397428,39.90923","address":"北京市东城区","pname":"北京市","cityname":"北京市","adname":"东城区"}]}""";
        return new Response.Builder()
                .request(new Request.Builder().url("https://restapi.amap.com/v3/place/text").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(ResponseBody.create(body, JSON_MEDIA))
                .build();
    }

    private Response mockBaiduRegeocodeResponse() throws IOException {
        String body = """
                {"status":0,"result":{"formatted_address":"北京市朝阳区建国路"}}""";
        return new Response.Builder()
                .request(new Request.Builder().url("https://api.map.baidu.com/reverse_geocoding/v3").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(ResponseBody.create(body, JSON_MEDIA))
                .build();
    }

    private void setupMockCall(Response response) throws Exception {
        Call mockCall = mock(Call.class);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(response);
    }

    private void setupMockCallThrows(Exception ex) throws Exception {
        Call mockCall = mock(Call.class);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(ex);
    }

    // ============== geocode 测试 ==============

    @Test
    @DisplayName("geocode: 地址为空抛异常")
    void geocode_nullAddress_throws() {
        assertThrows(BizException.class, () -> mapService.geocode(null, null));
        assertThrows(BizException.class, () -> mapService.geocode("", "北京"));
        assertThrows(BizException.class, () -> mapService.geocode("   ", null));
    }

    @Test
    @DisplayName("geocode: 有效地址返回坐标")
    void geocode_validAddress_returnsCoordinates() throws Exception {
        setupMockCall(mockAmapGeocodeResponse());
        when(valueOperations.get(anyString())).thenReturn(null);

        GeoResult result = mapService.geocode("北京市东城区", "北京");

        assertNotNull(result);
        assertEquals("amap", result.getSource());
        assertEquals(116.397428, result.getLongitude(), 0.0001);
        assertEquals(39.90923, result.getLatitude(), 0.0001);
        assertEquals("北京市东城区", result.getFormattedAddress());
    }

    @Test
    @DisplayName("geocode: Redis缓存命中直接返回")
    void geocode_cacheHit_returnsCached() {
        GeoResult cached = GeoResult.builder()
                .longitude(116.397428).latitude(39.90923)
                .formattedAddress("北京市东城区").source("amap").build();
        when(valueOperations.get(anyString())).thenReturn(JSON.toJSONString(cached));

        GeoResult result = mapService.geocode("北京市东城区", "北京");

        assertEquals("amap", result.getSource());
        assertEquals(116.397428, result.getLongitude(), 0.0001);
    }

    @Test
    @DisplayName("geocode: 高德成功返回并缓存")
    void geocode_amapSuccess_caches() throws Exception {
        setupMockCall(mockAmapGeocodeResponse());
        when(valueOperations.get(anyString())).thenReturn(null);

        GeoResult result = mapService.geocode("天安门", "北京");

        assertEquals("amap", result.getSource());
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("geocode: 高德失败切换百度")
    void geocode_amapFallbackToBaidu() throws Exception {
        Call mockCall = mock(Call.class);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute())
                .thenThrow(new IOException("高德连接超时"))
                .thenReturn(mockBaiduGeocodeResponse());
        when(valueOperations.get(anyString())).thenReturn(null);

        GeoResult result = mapService.geocode("朝阳区", "北京");

        assertEquals("baidu", result.getSource());
    }

    // ============== reverseGeocode 测试 ==============

    @Test
    @DisplayName("reverseGeocode: 经纬度为空抛异常")
    void reverseGeocode_nullLonLat_throws() {
        assertThrows(BizException.class, () -> mapService.reverseGeocode(null, 39.9));
        assertThrows(BizException.class, () -> mapService.reverseGeocode(116.3, null));
    }

    @Test
    @DisplayName("reverseGeocode: 有效坐标返回地址")
    void reverseGeocode_validCoordinates_returnsAddress() throws Exception {
        setupMockCall(mockAmapRegeocodeResponse());
        when(valueOperations.get(anyString())).thenReturn(null);

        GeoResult result = mapService.reverseGeocode(116.397, 39.909);

        assertNotNull(result);
        assertEquals("amap", result.getSource());
        assertNotNull(result.getFormattedAddress());
        assertTrue(result.getFormattedAddress().contains("天安门"));
    }

    @Test
    @DisplayName("reverseGeocode: Redis缓存命中返回")
    void reverseGeocode_cacheHit_returnsCached() {
        GeoResult cached = GeoResult.builder()
                .longitude(116.397).latitude(39.909)
                .formattedAddress("北京市东城区").source("amap").build();
        when(valueOperations.get(anyString())).thenReturn(JSON.toJSONString(cached));

        GeoResult result = mapService.reverseGeocode(116.397, 39.909);

        assertEquals("amap", result.getSource());
    }

    @Test
    @DisplayName("reverseGeocode: 高德逆地理编码成功")
    void reverseGeocode_amapSuccess() throws Exception {
        setupMockCall(mockAmapRegeocodeResponse());
        when(valueOperations.get(anyString())).thenReturn(null);

        GeoResult result = mapService.reverseGeocode(116.397, 39.909);

        assertEquals("amap", result.getSource());
        assertNotNull(result.getFormattedAddress());
    }

    // ============== navigate (routePlan) 测试 ==============

    @Test
    @DisplayName("navigate: 终点为空抛异常")
    void navigate_nullDest_throws() {
        assertThrows(BizException.class, () ->
                mapService.navigate(null, null, null, null, null));
    }

    @Test
    @DisplayName("navigate: 有效路线返回 scheme 和 URL")
    void navigate_validRoute_returnsSchemeAndUrl() {
        NavigateResult result = mapService.navigate(
                116.397, 39.909, 116.400, 39.910, "天安门");

        assertEquals("amap", result.getScheme());
        assertNotNull(result.getUrl());
        assertTrue(result.getUrl().contains("to="), "URL should contain to param");
        assertTrue(result.getUrl().contains("from="), "URL should contain from param");
        assertTrue(result.getUrl().startsWith("https://uri.amap.com/navigation"), "URL should start with amap nav endpoint");
    }

    @Test
    @DisplayName("navigate: 相同原点目的地仍返回有效URL")
    void navigate_sameOriginDest_returnsUrl() {
        NavigateResult result = mapService.navigate(
                116.400, 39.910, 116.400, 39.910, "同一地点");

        assertEquals("amap", result.getScheme());
        assertNotNull(result.getUrl());
    }

    @Test
    @DisplayName("navigate: 无起点时不拼接from参数")
    void navigate_noOrigin_noFromParam() {
        NavigateResult result = mapService.navigate(
                null, null, 116.400, 39.910, "国贸");

        assertEquals("amap", result.getScheme());
        assertFalse(result.getUrl().contains("&from="));
    }

    @Test
    @DisplayName("navigate: 有起点时拼接from参数")
    void navigate_withOrigin_hasFromParam() {
        NavigateResult result = mapService.navigate(
                116.397, 39.909, 116.400, 39.910, "国贸");

        assertTrue(result.getUrl().contains("&from="));
    }

    // ============== searchPois (poiSearch) 测试 ==============

    @Test
    @DisplayName("searchPois: 有效关键字返回结果")
    void searchPois_validKeyword_returnsResults() throws Exception {
        setupMockCall(mockAmapPoiResponse());

        List<GeoResult> results = mapService.searchPois("咖啡", "北京");

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals(1, results.size());
        assertEquals(116.397428, results.get(0).getLongitude(), 0.0001);
        assertEquals(39.90923, results.get(0).getLatitude(), 0.0001);
    }

    @Test
    @DisplayName("searchPois: HTTP异常返回空列表")
    void searchPois_httpException_returnsEmpty() throws Exception {
        setupMockCallThrows(new IOException("HTTP连接失败"));

        List<GeoResult> results = mapService.searchPois("咖啡", null);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("searchPois: 空关键字搜索返回空列表(异常降级)")
    void searchPois_emptyKeyword_returnsEmpty() throws Exception {
        setupMockCallThrows(new IOException("无结果"));

        List<GeoResult> results = mapService.searchPois("", null);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // ============== ipLocate 测试 ==============

    @Test
    @DisplayName("ipLocate: 有效IP返回位置信息")
    void ipLocate_validIp_returnsLocation() throws Exception {
        setupMockCall(mockAmapIpResponse());

        GeoResult result = mapService.ipLocate("120.38.103.123");

        assertNotNull(result);
        assertEquals("北京市", result.getProvince());
        assertEquals("北京市", result.getCity());
        assertNotNull(result.getFormattedAddress());
    }

    @Test
    @DisplayName("ipLocate: 无效IP异常返回新对象不抛异常")
    void ipLocate_invalidIp_returnsNewObject() throws Exception {
        setupMockCallThrows(new IOException("IP无效"));

        GeoResult result = mapService.ipLocate("invalid-ip");

        assertNotNull(result);
    }

    @Test
    @DisplayName("ipLocate: HTTP异常返回新对象不抛异常")
    void ipLocate_httpException_returnsNewObject() throws Exception {
        setupMockCallThrows(new IOException("HTTP连接失败"));

        GeoResult result = mapService.ipLocate("127.0.0.1");

        assertNotNull(result);
    }

    @Test
    @DisplayName("geocode: 带城市参数地理编码正确拼接URL")
    void geocode_withCityParam_usesCity() throws Exception {
        setupMockCall(mockAmapGeocodeResponse());
        when(valueOperations.get(anyString())).thenReturn(null);

        GeoResult result = mapService.geocode("东城区", "北京");

        assertNotNull(result);
        assertEquals("amap", result.getSource());
        verify(mockHttpClient).newCall(any(Request.class));
    }

    @Test
    @DisplayName("reverseGeocode: 高德异常切换百度返回结果")
    void reverseGeocode_amapFallbackToBaidu() throws Exception {
        Call mockCall = mock(Call.class);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute())
                .thenThrow(new IOException("高德连接超时"))
                .thenReturn(mockBaiduRegeocodeResponse());
        when(valueOperations.get(anyString())).thenReturn(null);

        GeoResult result = mapService.reverseGeocode(116.397, 39.909);

        assertNotNull(result);
        assertEquals("baidu", result.getSource());
        assertNotNull(result.getFormattedAddress());
    }
}