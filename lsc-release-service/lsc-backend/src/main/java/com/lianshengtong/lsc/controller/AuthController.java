package com.lianshengtong.lsc.controller;

import com.lianshengtong.lsc.common.R;
import com.lianshengtong.lsc.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/user/register")
    public R<Map<String, Object>> userRegister(@RequestBody Map<String, Object> body) {
        String mobile = (String) body.get("mobile");
        String password = (String) body.get("password");
        Long referrerId = body.get("referrerId") != null ? Long.valueOf(body.get("referrerId").toString()) : null;
        return R.ok(authService.register(mobile, password, 0, referrerId));
    }

    @PostMapping("/user/login")
    public R<Map<String, Object>> userLogin(@RequestBody Map<String, Object> body) {
        return R.ok(authService.login((String) body.get("mobile"), (String) body.get("password")));
    }

    @PostMapping("/merchant/login")
    public R<Map<String, Object>> merchantLogin(@RequestBody Map<String, Object> body) {
        return R.ok(authService.login((String) body.get("mobile"), (String) body.get("password")));
    }

    @PostMapping("/admin/login")
    public R<Map<String, Object>> adminLogin(@RequestBody Map<String, Object> body) {
        if ("admin".equals(body.get("username")) && "123456".equals(body.get("password"))) {
            return R.ok(authService.login("13800138000", "123456"));
        }
        return R.fail(401, "管理员账号或密码错误");
    }
}
