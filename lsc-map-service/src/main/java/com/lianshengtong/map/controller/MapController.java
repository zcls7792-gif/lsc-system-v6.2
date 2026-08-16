package com.lianshengtong.map.controller;

import com.lianshengtong.common.result.R;
import com.lianshengtong.map.dto.GeoResult;
import com.lianshengtong.map.dto.NavigateResult;
import com.lianshengtong.map.service.MapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "地图定位", description = "地理编码/逆地理编码/导航唤起(高德+百度双服务商)")
@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
@Validated
public class MapController {

    private final MapService mapService;

    @Operation(summary = "地理编码(地址->经纬度)")
    @GetMapping("/geocode")
    public R<GeoResult> geocode(@RequestParam("address") @NotBlank(message = "地址不能为空")
                                @Size(max = 200, message = "地址长度不能超过200字符") String address,
                                @RequestParam(value = "city", required = false)
                                @Size(max = 50, message = "城市名长度不能超过50字符") String city) {
        return R.ok(mapService.geocode(address, city));
    }

    @Operation(summary = "逆地理编码(经纬度->地址)")
    @GetMapping("/reverse-geocode")
    public R<GeoResult> reverseGeocode(
            @RequestParam("longitude") @NotNull(message = "经度不能为空")
            @DecimalMin(value = "-180", message = "经度范围-180~180")
            @DecimalMax(value = "180", message = "经度范围-180~180") Double longitude,
            @RequestParam("latitude") @NotNull(message = "纬度不能为空")
            @DecimalMin(value = "-90", message = "纬度范围-90~90")
            @DecimalMax(value = "90", message = "纬度范围-90~90") Double latitude) {
        return R.ok(mapService.reverseGeocode(longitude, latitude));
    }

    @Operation(summary = "导航唤起(高德>百度>腾讯>苹果>浏览器)")
    @GetMapping("/navigate")
    public R<NavigateResult> navigate(
            @RequestParam(value = "originLon", required = false)
            @DecimalMin(value = "-180", message = "经度范围-180~180")
            @DecimalMax(value = "180", message = "经度范围-180~180") Double originLon,
            @RequestParam(value = "originLat", required = false)
            @DecimalMin(value = "-90", message = "纬度范围-90~90")
            @DecimalMax(value = "90", message = "纬度范围-90~90") Double originLat,
            @RequestParam("destLon") @NotNull(message = "目标经度不能为空")
            @DecimalMin(value = "-180", message = "经度范围-180~180")
            @DecimalMax(value = "180", message = "经度范围-180~180") Double destLon,
            @RequestParam("destLat") @NotNull(message = "目标纬度不能为空")
            @DecimalMin(value = "-90", message = "纬度范围-90~90")
            @DecimalMax(value = "90", message = "纬度范围-90~90") Double destLat,
            @RequestParam(value = "destName", required = false)
            @Size(max = 100, message = "目的地名称长度不能超过100字符") String destName) {
        return R.ok(mapService.navigate(originLon, originLat, destLon, destLat, destName));
    }

    @Operation(summary = "关键字搜索 POI(代理高德 PlaceSearch)")
    @GetMapping("/pois")
    public R<java.util.List<GeoResult>> searchPois(
            @RequestParam("keyword") @NotBlank(message = "关键字不能为空")
            @Size(max = 100, message = "关键字长度不能超过100字符") String keyword,
            @RequestParam(value = "city", required = false)
            @Size(max = 50, message = "城市名长度不能超过50字符") String city) {
        return R.ok(mapService.searchPois(keyword, city));
    }

    @Operation(summary = "IP 定位(粗略城市定位)")
    @GetMapping("/ip-locate")
    public R<GeoResult> ipLocate(jakarta.servlet.http.HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return R.ok(mapService.ipLocate(ip));
    }
}
