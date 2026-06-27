package com.aicp.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "账号类型不能为空")
    private String accountType; // phone / email

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度8-20位")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,20}$",
             message = "密码需包含大小写字母和数字")
    private String password;

    @NotBlank(message = "验证码不能为空")
    private String verifyCode;

    @NotBlank(message = "账户类型不能为空")
    private String accountCategory; // personal / enterprise

    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 20, message = "昵称2-20字符")
    private String nickname;

    private String inviteCode;
}
