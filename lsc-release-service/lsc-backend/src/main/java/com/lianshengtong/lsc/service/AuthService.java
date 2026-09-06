package com.lianshengtong.lsc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.lsc.common.BusinessException;
import com.lianshengtong.lsc.common.ErrorCode;
import com.lianshengtong.lsc.entity.LscAccount;
import com.lianshengtong.lsc.entity.SysUser;
import com.lianshengtong.lsc.mapper.LscAccountMapper;
import com.lianshengtong.lsc.mapper.SysUserMapper;
import com.lianshengtong.lsc.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final LscAccountMapper lscAccountMapper;
    private final JwtUtil jwtUtil;

    public Map<String, Object> register(String mobile, String password, Integer userType, Long referrerId) {
        // 手机号查重
        Long cnt = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getMobile, mobile));
        if (cnt > 0) throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "手机号已注册");

        SysUser user = new SysUser();
        user.setMobile(mobile);
        user.setPassword(password); // 生产环境需加密
        user.setUserType(userType);
        user.setReferrerId(referrerId);
        sysUserMapper.insert(user);

        // 创建 LSC 账户
        LscAccount account = new LscAccount();
        account.setUserId(user.getId());
        account.setTotalLocked(0L);
        account.setTotalAvailable(0L);
        lscAccountMapper.insert(account);

        String token = jwtUtil.generateToken(user.getId(), String.valueOf(userType));
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("userType", userType);
        result.put("token", token);
        return result;
    }

    public Map<String, Object> login(String mobile, String password) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getMobile, mobile));
        if (user == null || !user.getPassword().equals(password)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "手机号或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), String.valueOf(user.getUserType()));
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("userType", user.getUserType());
        result.put("token", token);
        return result;
    }
}
