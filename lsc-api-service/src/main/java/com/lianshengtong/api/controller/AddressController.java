package com.lianshengtong.api.controller;

import com.lianshengtong.api.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/user/address")
public class AddressController {

    private static final List<Map<String, Object>> ADDRESSES = new ArrayList<>(List.of(
            Map.of("id", 1, "userId", 10001, "name", "张三", "phone", "13800138000",
                    "province", "浙江省", "city", "杭州市", "district", "西湖区",
                    "detail", "文三路100号", "isDefault", 1),
            Map.of("id", 2, "userId", 10001, "name", "李四", "phone", "13900139000",
                    "province", "广东省", "city", "深圳市", "district", "南山区",
                    "detail", "科技园路200号", "isDefault", 0)
    ));

    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(ADDRESSES);
    }

    @GetMapping("/detail")
    public ApiResponse<Map<String, Object>> detail(@RequestParam int id) {
        return ADDRESSES.stream().filter(a -> ((Integer) a.get("id")) == id)
                .findFirst().map(ApiResponse::success).orElse(ApiResponse.fail("地址不存在"));
    }

    @GetMapping("/default")
    public ApiResponse<Map<String, Object>> defaultAddress() {
        return ADDRESSES.stream().filter(a -> a.get("isDefault").equals(1))
                .findFirst().map(ApiResponse::success).orElse(ApiResponse.success(null));
    }

    @PostMapping("/save")
    public ApiResponse<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        if (body.containsKey("id")) {
            return ApiResponse.success(body);
        }
        body.put("id", ADDRESSES.size() + 1);
        ADDRESSES.add(body);
        return ApiResponse.success(body);
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody Map<String, Object> body) {
        int id = (Integer) body.get("id");
        ADDRESSES.removeIf(a -> ((Integer) a.get("id")) == id);
        return ApiResponse.success(null);
    }
}
