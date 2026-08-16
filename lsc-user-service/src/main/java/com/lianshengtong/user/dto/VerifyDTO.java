package com.lianshengtong.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 实名认证 DTO
 *
 * @author lsc
 */
@Data
public class VerifyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID (由Token解析注入, 前端无需传) */
    private Long userId;

    /** 真实姓名 */
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 64, message = "姓名过长")
    private String realName;

    /** 身份证号 (明文, 服务端AES-256加密存储) */
    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$",
            message = "身份证号格式不正确")
    private String idCardNo;
}
