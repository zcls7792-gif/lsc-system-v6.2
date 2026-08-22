package com.lianshengtong.map.service.impl;

import com.lianshengtong.map.dto.GeoResult;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 补齐 MapServiceImpl 分支覆盖率 (I-05)，见 LSC_V6.2_Reports/LSC_V6.2_Code_Quality_Completeness_Audit_20260822.md
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MapServiceImpl 分支覆盖率补齐测试")
class MapServiceImplBranchCoverageTest {

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

    private Response mockResponse(String body) {
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

    // ==================== searchPois 分支补齐 ====================

    @Test
    @DisplayName("searchPois: httpGet 返回 null(响应体为 'null') 时返回空列表")
    void searchPois_nullResponse_returnsEmpty() throws Exception {
        setupMockCall(mockResponse("null"));

        List<GeoResult> results = mapService.searchPois("咖啡", null);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("searchPois: 响应无 pois 字段时返回空列表")
    void searchPois_noPoisField_returnsEmpty() throws Exception {
        setupMockCall(mockResponse("{\"status\":\"1\"}"));

        List<GeoResult> results = mapService.searchPois("咖啡", null);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("searchPois: 带城市参数时正常返回结果")
    void searchPois_withCityParam_returnsResults() throws Exception {
        String body = "{\"status\":\"1\",\"pois\":[{\"location\":\"116.397428,39.90923\",\"address\":\"北京市东城区\",\"pname\":\"北京市\",\"cityname\":\"北京市\",\"adname\":\"东城区\"}]}";
        setupMockCall(mockResponse(body));

        List<GeoResult> results = mapService.searchPois("咖啡", "北京");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(116.397428, results.get(0).getLongitude(), 0.0001);
        assertEquals(39.90923, results.get(0).getLatitude(), 0.0001);
    }

    // ==================== ipLocate 分支补齐 ====================

    @Test
    @DisplayName("ipLocate: httpGet 返回 null(响应体为 'null') 时返回空 GeoResult")
    void ipLocate_nullResponse_returnsEmptyGeoResult() throws Exception {
        setupMockCall(mockResponse("null"));

        GeoResult result = mapService.ipLocate("114.114.114.114");

        assertNotNull(result);
        assertNull(result.getProvince());
        assertNull(result.getCity());
        assertNull(result.getLongitude());
        assertNull(result.getLatitude());
    }

    @Test
    @DisplayName("ipLocate: 有效响应设置 province/city/rectangle 经纬度")
    void ipLocate_validResponse_setsFields() throws Exception {
        String body = "{\"province\":\"北京市\",\"city\":\"北京市\",\"rectangle\":\"116.0,39.0;117.0,40.0\"}";
        setupMockCall(mockResponse(body));

        GeoResult result = mapService.ipLocate("114.114.114.114");

        assertNotNull(result);
        assertEquals("北京市", result.getProvince());
        assertEquals("北京市", result.getCity());
        assertNotNull(result.getFormattedAddress());
        assertTrue(result.getFormattedAddress().contains("北京市"));
        assertEquals(116.0, result.getLongitude(), 0.0001);
        assertEquals(39.0, result.getLatitude(), 0.0001);
    }

    @Test
    @DisplayName("ipLocate: 响应无 rectangle 字段时跳过经纬度解析")
    void ipLocate_noRectangle_skipsCoords() throws Exception {
        String body = "{\"province\":\"北京市\",\"city\":\"北京市\"}";
        setupMockCall(mockResponse(body));

        GeoResult result = mapService.ipLocate("114.114.114.114");

        assertNotNull(result);
        assertEquals("北京市", result.getProvince());
        assertEquals("北京市", result.getCity());
        assertNotNull(result.getFormattedAddress());
        assertNull(result.getLongitude());
        assertNull(result.getLatitude());
    }

    @Test
    @DisplayName("ipLocate: rectangle 不含分号时跳过经纬度解析")
    void ipLocate_badRectangle_skipsCoords() throws Exception {
        String body = "{\"province\":\"北京市\",\"city\":\"北京市\",\"rectangle\":\"116.0,39.0\"}";
        setupMockCall(mockResponse(body));

        GeoResult result = mapService.ipLocate("114.114.114.114");

        assertNotNull(result);
        assertEquals("北京市", result.getProvince());
        assertNull(result.getLongitude());
        assertNull(result.getLatitude());
    }

    @Test
    @DisplayName("ipLocate: province/city 均为 null 时不设置 formattedAddress")
    void ipLocate_noProvinceCity_skipsFormattedAddress() throws Exception {
        String body = "{\"status\":\"1\"}";
        setupMockCall(mockResponse(body));

        GeoResult result = mapService.ipLocate("114.114.114.114");

        assertNotNull(result);
        assertNull(result.getProvince());
        assertNull(result.getCity());
        assertNull(result.getFormattedAddress());
        assertNull(result.getLongitude());
        assertNull(result.getLatitude());
    }
}
