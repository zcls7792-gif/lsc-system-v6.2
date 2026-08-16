package com.lianshengtong.user.service;

import com.lianshengtong.user.dto.LoginDTO;
import com.lianshengtong.user.dto.RegisterDTO;
import com.lianshengtong.user.dto.VerifyDTO;
import com.lianshengtong.user.entity.User;

/**
 * 用户服务接口
 * <p>
 * 覆盖用户注册（手机号查重、密码 BCrypt 加密、雪花算法 ID、推荐关系仅一级绑定）、
 * 登录（JWT 签发）、实名认证（身份证 AES 加密）、用户信息查询。
 * </p>
 *
 * @author lsc
 */
public interface UserService {

    /**
     * 用户注册
     *
     * @param dto 注册参数
     * @return 脱敏后的用户信息
     */
    User register(RegisterDTO dto);

    /**
     * 用户登录
     *
     * @param dto 登录参数
     * @return JWT Token
     */
    String login(LoginDTO dto);

    /**
     * 实名认证（身份证号 AES-256 加密存储）
     *
     * @param dto 实名认证参数
     */
    void verify(VerifyDTO dto);

    /**
     * 用户信息查询（脱敏返回）
     *
     * @param userId 用户ID
     * @return 脱敏后的用户信息
     */
    User getUserInfo(Long userId);

    /**
     * 修改密码
     *
     * @param userId       用户ID
     * @param oldPassword  旧密码(明文)
     * @param newPassword  新密码(明文)
     */
    void changePassword(Long userId, String oldPassword, String newPassword);
}
