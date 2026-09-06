package com.lianshengtong.api.controller;

import com.lianshengtong.api.data.MockData;
import com.lianshengtong.api.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Map<String, Object> TOKENS = new HashMap<>();

    /** 平台管理员登录 */
    @PostMapping("/admin/login")
    public ApiResponse<Map<String, Object>> adminLogin(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        for (Map<String, Object> a : MockData.admins) {
            if (a.get("username").equals(username) && a.get("password").equals(password)) {
                String token = "admin-token-" + UUID.randomUUID();
                TOKENS.put(token, a);
                Map<String, Object> data = new HashMap<>();
                data.put("token", token);
                data.put("userInfo", a);
                return ApiResponse.success(data);
            }
        }
        return ApiResponse.fail("用户名或密码错误");
    }

    @PostMapping("/admin/logout")
    public ApiResponse<Void> adminLogout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            TOKENS.remove(auth.substring(7));
        }
        return ApiResponse.success(null);
    }

    @GetMapping("/admin/info")
    public ApiResponse<Map<String, Object>> adminInfo() {
        return ApiResponse.success(MockData.admins.get(0));
    }

    @GetMapping("/admin/list")
    public ApiResponse<List<Map<String, Object>>> adminList() {
        return ApiResponse.success(MockData.admins);
    }

    /** 商家登录 */
    @PostMapping("/merchant/login")
    public ApiResponse<Map<String, Object>> merchantLogin(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String password = body.get("password");
        for (Map<String, Object> m : MockData.merchants) {
            if (m.get("phone").equals(phone) && "123456".equals(password)) {
                String token = "merchant-token-" + UUID.randomUUID();
                TOKENS.put(token, m);
                Map<String, Object> data = new HashMap<>();
                data.put("token", token);
                data.put("profile", m);
                return ApiResponse.success(data);
            }
        }
        return ApiResponse.fail("手机号或密码错误");
    }

    @GetMapping("/merchant/info")
    public ApiResponse<Map<String, Object>> merchantInfo() {
        return ApiResponse.success(MockData.merchants.get(0));
    }

    /** 移动端用户登录 */
    @PostMapping("/user/login")
    public ApiResponse<Map<String, Object>> userLogin(@RequestBody Map<String, String> body) {
        String phone = body.getOrDefault("phone", "13800138000");
        String token = "user-token-" + UUID.randomUUID();
        Map<String, Object> user = new HashMap<>();
        user.put("id", 10001);
        user.put("phone", phone);
        user.put("nickname", "LSC用户");
        user.put("avatar", "");
        TOKENS.put(token, user);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userInfo", user);
        return ApiResponse.success(data);
    }

    @PostMapping("/user/register")
    public ApiResponse<Map<String, Object>> userRegister(@RequestBody Map<String, String> body) {
        String token = "user-token-" + UUID.randomUUID();
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        return ApiResponse.success(data);
    }

    @PostMapping("/user/logout")
    public ApiResponse<Void> userLogout() {
        return ApiResponse.success(null);
    }

    @GetMapping("/user/profile")
    public ApiResponse<Map<String, Object>> userProfile() {
        Map<String, Object> u = new HashMap<>();
        u.put("id", 10001);
        u.put("phone", "13800138000");
        u.put("nickname", "LSC用户");
        u.put("avatar", "");
        u.put("availableLsc", 12850);
        u.put("creditScore", 100);
        return ApiResponse.success(u);
    }

    @PostMapping("/user/sms/send")
    public ApiResponse<Void> smsSend(@RequestBody Map<String, String> body) {
        return ApiResponse.success(null);
    }

    @PostMapping("/user/login/sms")
    public ApiResponse<Map<String, Object>> smsLogin(@RequestBody Map<String, String> body) {
        return userLogin(body);
    }

    @PostMapping("/user/verify")
    public ApiResponse<Void> userVerify(@RequestBody Map<String, String> body) {
        return ApiResponse.success(null);
    }

    @PostMapping("/user/password/change")
    public ApiResponse<Void> pwdChange(@RequestBody Map<String, String> body) {
        return ApiResponse.success(null);
    }

    @PostMapping("/user/password/reset")
    public ApiResponse<Void> pwdReset(@RequestBody Map<String, String> body) {
        return ApiResponse.success(null);
    }
}
