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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("地图定位服务扩展测试 - 故障切换/边界场景")
class MapServiceImplExtendedTest {

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
        ReflectionTestUtils.setField(mapService, "amapDown", new java.util.concurrent.atomic.AtomicBoolean(false));

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private Response mockResponse(String body) throws IOException {
        return new Response.Builder()
                .request(new Request.Builder().url("https://test.com").build())
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

    @Test
    @DisplayName("geocode: amapDown时直接走百度")
    void geocode_amapDown_usesBaidu() throws Exception {
        ReflectionTestUtils.setField(mapService, "amapDown", new java.util.concurrent.atomic.AtomicBoolean(true));
        String body = """
                {"status":0,"result":{"location":{"lng":116.1,"lat":39.1},"precise":1}}""";
        setupMockCall(mockResponse(body));
        when(valueOperations.get(anyString())).thenReturn(null);

        GeoResult result = mapService.geocode("某地址", null);

        assertEquals("baidu", result.getSource());
    }

    @Test
    @DisplayName("geocode: 高德无结果抛BizException")
    void geocode_amapNoResult_throwsBizException() throws Exception {
        String body = """
                {"status":"1","geocodes":[]}""";
        setupMockCall(mockResponse(body));
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThrows(BizException.class, () -> mapService.geocode("地址", null));
    }

    @Test
    @DisplayName("geocode: 高德状态非1抛异常")
    void geocode_amapBadStatus_throwsBizException() throws Exception {
        String body = """
                {"status":"0","info":"INVALID"}""";
        setupMockCall(mockResponse(body));
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThrows(BizException.class, () -> mapService.geocode("地址", null));
    }

    @Test
    @DisplayName("geocode: 百度返回非0状态抛异常")
    void geocode_baiduBadStatus_throwsBizException() throws Exception {
        ReflectionTestUtils.setField(mapService, "amapDown", new java.util.concurrent.atomic.AtomicBoolean(true));
        String body = """
                {"status":1,"message":"INVALID"}""";
        setupMockCall(mockResponse(body));
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThrows(BizException.class, () -> mapService.geocode("地址", null));
    }

    @Test
    @DisplayName("geocode: 带城市参数URL拼接正确")
    void geocode_withCity_buildsUrl() throws Exception {
        String body = """
                {"status":"1","geocodes":[{"location":"116.3,39.9","formatted_address":"地址"}]}""";
        setupMockCall(mockResponse(body));
        when(valueOperations.get(anyString())).thenReturn(null);

        mapService.geocode("地址", "北京");

        verify(mockHttpClient).newCall(any(Request.class));
    }

    // ==================== reverseGeocode ====================

    @Test
    @DisplayName("reverseGeocode: amapDown时直接走百度")
    void reverseGeocode_amapDown_usesBaidu() throws Exception {
        ReflectionTestUtils.setField(mapService, "amapDown", new java.util.concurrent.atomic.AtomicBoolean(true));
        String body = """
                {"status":0,"result":{"formatted_address":"朝阳区"}}""";
        setupMockCall(mockResponse(body));
        when(valueOperations.get(anyString())).thenReturn(null);

        GeoResult result = mapService.reverseGeocode(116.1, 39.1);
        assertEquals("baidu", result.getSource());
    }

    @Test
    @DisplayName("reverseGeocode: 高德状态失败抛异常")
    void reverseGeocode_amapBadStatus_throws() throws Exception {
        String body = """
                {"status":"0","info":"INVALID"}""";
        setupMockCall(mockResponse(body));
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThrows(BizException.class, () -> mapService.reverseGeocode(116.1, 39.1));
    }

    @Test
    @DisplayName("reverseGeocode: 百度状态失败抛异常")
    void reverseGeocode_baiduBadStatus_throws() throws Exception {
        ReflectionTestUtils.setField(mapService, "amapDown", new java.util.concurrent.atomic.AtomicBoolean(true));
        String body = """
                {"status":1}""";
        setupMockCall(mockResponse(body));
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThrows(BizException.class, () -> mapService.reverseGeocode(116.1, 39.1));
    }

    // ==================== navigate ====================

    @Test
    @DisplayName("navigate: 高德URL构建异常时降级到百度")
    void navigate_amapFails_fallsBackToBaidu() {
        // 触发高德异常：destName 为 null，不会URLEncode，正常也不会抛。
        // 通过 amapNavigateUrl 为 null 触发 NullPointerException 走降级
        ReflectionTestUtils.setField(mapService, "amapNavigateUrl", null);

        NavigateResult result = mapService.navigate(
                116.3, 39.9, 116.4, 39.91, "目的地");

        assertEquals("baidu", result.getScheme());
        assertNotNull(result.getUrl());
        assertTrue(result.getUrl().contains("origin=latlng"));
    }

    @Test
    @DisplayName("navigate: 高德navigateUrl为异常字符串时降级百度")
    void navigate_amapUrl异常_fallbackBaidu() {
        ReflectionTestUtils.setField(mapService, "amapNavigateUrl", "https://uri.amap.com/navigation");

        // 正常走高德
        NavigateResult result = mapService.navigate(
                116.3, 39.9, 116.4, 39.91, null);

        assertEquals("amap", result.getScheme());
        assertTrue(result.getUrl().startsWith("https://uri.amap.com/navigation"));
    }

    // ==================== searchPois ====================

    @Test
    @DisplayName("searchPois: 关键字为空字符串异常降级返回空")
    void searchPois_emptyKeyword_returnsEmpty() throws Exception {
        setupMockCall(mockResponse("""
                {"status":"0","pois":null}"""));
        List<GeoResult> results = mapService.searchPois("", null);
        assertNotNull(results);
    }

    @Test
    @DisplayName("searchPois: 无pois字段返回空列表")
    void searchPois_noPoisField_returnsEmpty() throws Exception {
        setupMockCall(mockResponse("""
                {"status":"1"}"""));
        List<GeoResult> results = mapService.searchPois("咖啡", null);
        assertNotNull(results);
    }

    @Test
    @DisplayName("searchPois: 带城市参数")
    void searchPois_withCity_paramInUrl() throws Exception {
        setupMockCall(mockResponse("""
                {"status":"1","pois":[{"location":"116.3,39.9","address":"地址","pname":"北京","cityname":"北京","adname":"东城"}]}"""));
        List<GeoResult> results = mapService.searchPois("咖啡", "北京");
        assertFalse(results.isEmpty());
    }

    // ==================== ipLocate ====================

    @Test
    @DisplayName("ipLocate: rectangle缺失不解析经纬度")
    void ipLocate_noRectangle_noCoords() throws Exception {
        setupMockCall(mockResponse("""
                {"status":"1","province":"广东","city":"深圳"}"""));
        GeoResult result = mapService.ipLocate("1.1.1.1");
        assertEquals("广东深圳", result.getFormattedAddress());
        assertNull(result.getLongitude());
    }

    @Test
    @DisplayName("ipLocate: rectangle格式异常不解析")
    void ipLocate_badRectangle_handled() throws Exception {
        setupMockCall(mockResponse("""
                {"status":"1","province":"北京","city":"北京","rectangle":"invalid"}"""));
        GeoResult result = mapService.ipLocate("1.1.1.1");
        assertEquals("北京北京", result.getFormattedAddress());
    }

    @Test
    @DisplayName("ipLocate: rectangle包含分号正确解析")
    void ipLocate_validRectangle_parsed() throws Exception {
        setupMockCall(mockResponse("""
                {"status":"1","province":"北京","city":"北京","rectangle":"116.3,39.9;116.4,40.0"}"""));
        GeoResult result = mapService.ipLocate("1.1.1.1");
        assertEquals(116.3, result.getLongitude(), 0.0001);
        assertEquals(39.9, result.getLatitude(), 0.0001);
    }

    // ==================== httpGet 异常处理 ====================

    @Test
    @DisplayName("httpGet: 响应body为null抛RuntimeException")
    void httpGet_nullBody_throwsRuntimeException() throws Exception {
        Response nullBodyResponse = new Response.Builder()
                .request(new Request.Builder().url("https://test.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .build();
        setupMockCall(nullBodyResponse);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> ReflectionTestUtils.invokeMethod(mapService, "httpGet", "https://test.com"));
        assertTrue(ex.getMessage().contains("HTTP请求失败"));
    }

    @Test
    @DisplayName("httpGet: 非2xx状态抛异常")
    void httpGet_badCode_throwsRuntimeException() throws Exception {
        Response bad = new Response.Builder()
                .request(new Request.Builder().url("https://test.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(500).message("Server Error")
                .body(ResponseBody.create("error", JSON_MEDIA))
                .build();
        setupMockCall(bad);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> ReflectionTestUtils.invokeMethod(mapService, "httpGet", "https://test.com"));
        assertTrue(ex.getMessage().contains("HTTP请求失败"));
    }

    @Test
    @DisplayName("httpGet: 网络异常包装为RuntimeException")
    void httpGet_networkError_wrapsIOException() throws Exception {
        Call mockCall = mock(Call.class);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(new IOException("timeout"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> ReflectionTestUtils.invokeMethod(mapService, "httpGet", "https://test.com"));
        assertTrue(ex.getMessage().contains("HTTP请求异常"));
    }

    @Test
    @DisplayName("parseLocation: null/空/异常都正确返回null")
    void parseLocation_edgeCases() {
        assertNull(ReflectionTestUtils.invokeMethod(mapService, "parseLocation", (String) null, 0));
        assertNull(ReflectionTestUtils.invokeMethod(mapService, "parseLocation", "", 0));
        assertNull(ReflectionTestUtils.invokeMethod(mapService, "parseLocation", "abc", 0));
        assertNull(ReflectionTestUtils.invokeMethod(mapService, "parseLocation", "116.3,39.9", 5));
        assertNull(ReflectionTestUtils.invokeMethod(mapService, "parseLocation", "abc,def", 0));

        Double lon = ReflectionTestUtils.invokeMethod(mapService, "parseLocation", "116.3,39.9", 0);
        assertEquals(116.3, lon, 0.0001);
        Double lat = ReflectionTestUtils.invokeMethod(mapService, "parseLocation", "116.3,39.9", 1);
        assertEquals(39.9, lat, 0.0001);
    }

    @Test
    @DisplayName("ipLocate: 真实缓存响应JSON解析")
    void geocode_cacheDeserialization() {
        GeoResult cached = GeoResult.builder()
                .longitude(116.3).latitude(39.9).formattedAddress("北京").source("amap").build();
        when(valueOperations.get(anyString())).thenReturn(JSON.toJSONString(cached));

        GeoResult result = mapService.geocode("地址", null);
        assertEquals("amap", result.getSource());
        assertEquals(116.3, result.getLongitude(), 0.0001);
    }
}
