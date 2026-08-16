package com.lianshengtong.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.admin.entity.Admin;
import com.lianshengtong.admin.mapper.AdminMapper;
import com.lianshengtong.admin.service.AdminService;
import com.lianshengtong.admin.util.AdminJwtUtil;
import com.lianshengtong.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理员服务实现
 * <p>登录校验(BCrypt) + JWT 签发 + 权限校验(超管拥有全部权限)。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminMapper adminMapper;
    private final AdminJwtUtil adminJwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> login(String username, String password, String clientIp) {
        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Admin::getUsername, username);
        Admin admin = adminMapper.selectOne(wrapper);
        if (admin == null || !passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new BizException(401, "账号或密码错误");
        }
        if (admin.getStatus() != 1) {
            throw new BizException(403, "管理员账号已被禁用");
        }
        // 更新登录信息
        admin.setLastLoginIp(clientIp);
        admin.setLastLoginAt(LocalDateTime.now());
        adminMapper.updateById(admin);

        String token = adminJwtUtil.generateToken(admin.getAdminId(), admin.getRole());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("adminId", admin.getAdminId());
        result.put("username", admin.getUsername());
        result.put("realName", admin.getRealName());
        result.put("role", admin.getRole());
        log.info("管理员登录成功 adminId={} username={}", admin.getAdminId(), username);
        return result;
    }

    @Override
    public Admin getAdminInfo(Long adminId) {
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new BizException(404, "管理员不存在");
        }
        admin.setPasswordHash(null);
        return admin;
    }

    @Override
    public boolean checkPermission(Long adminId, Integer requiredRole) {
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null || admin.getStatus() != 1) {
            return false;
        }
        // 超管(0)拥有全部权限
        return admin.getRole() == 0 || admin.getRole().equals(requiredRole);
    }

    @Override
    public IPage<Admin> listAdmins(Integer page, Integer size, String keyword, Integer role) {
        Page<Admin> p = new Page<>(page == null ? 1 : page, size == null ? 20 : size);
        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Admin::getUsername, keyword)
                    .or().like(Admin::getRealName, keyword));
        }
        if (role != null) {
            wrapper.eq(Admin::getRole, role);
        }
        wrapper.orderByDesc(Admin::getCreatedAt);
        IPage<Admin> result = adminMapper.selectPage(p, wrapper);
        // 脱敏：不返回密码哈希
        result.getRecords().forEach(a -> a.setPasswordHash(null));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Admin addAdmin(Admin admin) {
        if (admin.getUsername() == null || admin.getUsername().isBlank()) {
            throw new BizException(400, "用户名不能为空");
        }
        // 用户名唯一性校验
        LambdaQueryWrapper<Admin> exists = new LambdaQueryWrapper<>();
        exists.eq(Admin::getUsername, admin.getUsername());
        if (adminMapper.selectCount(exists) > 0) {
            throw new BizException(409, "用户名已存在");
        }
        if (admin.getPassword() == null || admin.getPassword().isBlank()) {
            throw new BizException(400, "密码不能为空");
        }
        admin.setPasswordHash(passwordEncoder.encode(admin.getPassword()));
        if (admin.getRole() == null) {
            admin.setRole(1); // 默认运营
        }
        if (admin.getStatus() == null) {
            admin.setStatus(1);
        }
        adminMapper.insert(admin);
        admin.setPasswordHash(null);
        admin.setPassword(null);
        log.info("新增管理员 adminId={} username={}", admin.getAdminId(), admin.getUsername());
        return admin;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Admin updateAdmin(Long adminId, Admin admin) {
        Admin exists = adminMapper.selectById(adminId);
        if (exists == null) {
            throw new BizException(404, "管理员不存在");
        }
        exists.setRealName(admin.getRealName());
        if (admin.getRole() != null) {
            exists.setRole(admin.getRole());
        }
        if (admin.getStatus() != null) {
            exists.setStatus(admin.getStatus());
        }
        // 密码非空才更新
        if (admin.getPassword() != null && !admin.getPassword().isBlank()) {
            exists.setPasswordHash(passwordEncoder.encode(admin.getPassword()));
        }
        adminMapper.updateById(exists);
        exists.setPasswordHash(null);
        exists.setPassword(null);
        log.info("修改管理员 adminId={}", adminId);
        return exists;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAdmin(Long adminId) {
        Admin exists = adminMapper.selectById(adminId);
        if (exists == null) {
            throw new BizException(404, "管理员不存在");
        }
        if (exists.getRole() != null && exists.getRole() == 0) {
            throw new BizException(400, "超级管理员不可删除");
        }
        // 软删除：状态置 0
        exists.setStatus(0);
        adminMapper.updateById(exists);
        log.info("删除(禁用)管理员 adminId={}", adminId);
    }
}
