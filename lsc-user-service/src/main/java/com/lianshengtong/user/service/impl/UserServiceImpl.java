package com.lianshengtong.user.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.common.enums.UserTypeEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.common.utils.SnowflakeIdUtil;
import com.lianshengtong.user.dto.LoginDTO;
import com.lianshengtong.user.dto.RegisterDTO;
import com.lianshengtong.user.dto.VerifyDTO;
import com.lianshengtong.user.entity.User;
import com.lianshengtong.user.mapper.UserMapper;
import com.lianshengtong.user.service.UserService;
import com.lianshengtong.user.util.JwtUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户服务实现
 *
 * @author lsc
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${lsc.aes.id-card-key:LscIdCardAesKey2026!!}")
    private String aesRawKey;

    /** AES 派生密钥(16字节)，由配置密钥 MD5 派生，规避原始密钥长度不满足 16/24/32 字节问题 */
    private byte[] aesKey;

    public UserServiceImpl(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    @PostConstruct
    public void initAesKey() {
        this.aesKey = DigestUtil.md5(aesRawKey.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public User register(RegisterDTO dto) {
        // 1. 手机号查重
        Long existCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getMobile, dto.getMobile()));
        if (existCount != null && existCount > 0) {
            throw new BizException(400, "该手机号已注册");
        }

        // 2. 推荐关系绑定（严格一级：仅绑定直接推荐人，不形成链式继承）
        Long referrerId = null;
        if (StrUtil.isNotBlank(dto.getReferralCode())) {
            User referrer = resolveReferrer(dto.getReferralCode());
            if (referrer == null) {
                throw new BizException(400, "推荐码无效");
            }
            referrerId = referrer.getUserId();
        }

        // 3. 构建用户：雪花算法 ID + BCrypt 加密密码
        long userId = SnowflakeIdUtil.id();
        User user = new User();
        user.setUserId(userId);
        user.setUserType(dto.getUserType() == null ? UserTypeEnum.CONSUMER.getCode() : dto.getUserType());
        user.setMobile(dto.getMobile());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StrUtil.isNotBlank(dto.getNickname()) ? dto.getNickname() : "用户" + userId % 100000);
        user.setIsVerified(0);
        user.setReferrerId(referrerId);
        user.setStatus(1);
        // 推荐码默认取 user_id
        user.setReferralCode(String.valueOf(userId));

        userMapper.insert(user);
        log.info("[注册] userId={}, userType={}, referrerId={}", userId, user.getUserType(), referrerId);
        return mask(user);
    }

    @Override
    public String login(LoginDTO dto) {
        // 账号视为手机号
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getMobile, dto.getAccount()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        Map<String, Object> extra = new HashMap<>();
        extra.put("mobile", user.getMobile());
        String token = jwtUtil.generateToken(String.valueOf(user.getUserId()),
                String.valueOf(user.getUserType()), extra);
        log.info("[登录] userId={}, userType={}", user.getUserId(), user.getUserType());
        return token;
    }

    @Override
    public void verify(VerifyDTO dto) {
        User user = userMapper.selectById(dto.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 已实名认证幂等返回
        if (user.getIsVerified() != null && user.getIsVerified() == 1) {
            return;
        }
        // 身份证号 AES-256 加密存储
        User update = new User();
        update.setUserId(user.getUserId());
        update.setRealName(dto.getRealName());
        update.setIdCardNo(SecureUtil.aes(aesKey).encryptHex(dto.getIdCardNo()));
        update.setIsVerified(1);
        userMapper.updateById(update);
        log.info("[实名认证] userId={}", user.getUserId());
    }

    @Override
    public User getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return mask(user);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "旧密码错误");
        }
        if (StrUtil.isBlank(newPassword) || newPassword.length() < 6) {
            throw new BizException(400, "新密码长度不能少于6位");
        }
        User update = new User();
        update.setUserId(userId);
        update.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
        log.info("[修改密码] userId={}", userId);
    }

    /**
     * 解析推荐码：优先按 referral_code 字段查询，其次按 user_id 解析
     */
    private User resolveReferrer(String referralCode) {
        User byCode = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getReferralCode, referralCode).last("LIMIT 1"));
        if (byCode != null) {
            return byCode;
        }
        try {
            long refId = Long.parseLong(referralCode);
            return userMapper.selectById(refId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 脱敏：清除密码哈希，身份证号解密后仅保留后4位
     */
    private User mask(User user) {
        user.setPasswordHash(null);
        if (StrUtil.isNotBlank(user.getIdCardNo())) {
            try {
                String plain = SecureUtil.aes(aesKey).decryptStr(user.getIdCardNo());
                user.setIdCardNo(maskIdCard(plain));
            } catch (Exception e) {
                user.setIdCardNo("**************");
            }
        }
        return user;
    }

    private String maskIdCard(String plain) {
        if (StrUtil.isBlank(plain) || plain.length() <= 4) {
            return "****";
        }
        return "**************" + plain.substring(plain.length() - 4);
    }
}
