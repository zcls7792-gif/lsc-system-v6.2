package com.lianshengtong.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册 DTO
 * - 消费者注册: user_type=0
 * - 商家注册:   user_type=1
 *
 * @author lsc
 */
@Data
public class RegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户类型 0消费者 1商家 (默认0) */
    private Integer userType = 0;

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    /** 密码 (明文, 服务端BCrypt加密) */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度6-32位")
    private String password;

    /** 昵称 (可选) */
    private String nickname;

    /** 推荐码 (推荐人user_id或推荐码, 严格一级) */
    private String referralCode;
}
