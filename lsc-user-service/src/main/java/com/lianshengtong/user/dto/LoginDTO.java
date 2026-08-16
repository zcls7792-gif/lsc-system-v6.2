package com.lianshengtong.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录 DTO (用户/管理员通用)
 *
 * @author lsc
 */
@Data
public class LoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户名/手机号 */
    @NotBlank(message = "账号不能为空")
    private String account;

    /** 密码 (明文) */
    @NotBlank(message = "密码不能为空")
    private String password;
}
