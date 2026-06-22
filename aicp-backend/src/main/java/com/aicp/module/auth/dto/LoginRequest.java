package com.aicp.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "账号不能为空")
    private String account;

    private String accountType; // phone / email

    private String password;

    private String verifyCode;

    private String phone;
}
