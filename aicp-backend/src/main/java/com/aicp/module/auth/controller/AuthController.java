package com.aicp.module.auth.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.module.auth.dto.*;
import com.aicp.module.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/send-code")
    public ApiResponse<Map<String, Object>> sendCode(@RequestBody Map<String, String> body) {
        authService.sendCode(body.get("target"), body.get("type"), body.get("scene"));
        return ApiResponse.success(Map.of(
                "expire_seconds", 300,
                "retry_after_seconds", 60
        ));
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.success(authService.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        Map<String, Object> result;
        if (req.getPassword() != null) {
            result = authService.login(req.getAccount(), req.getAccountType(), req.getPassword());
        } else {
            result = authService.loginBySms(req.getPhone(), req.getVerifyCode());
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/login/sms")
    public ApiResponse<Map<String, Object>> loginBySms(@RequestBody Map<String, String> body) {
        return ApiResponse.success(
                authService.loginBySms(body.get("phone"), body.get("verify_code")));
    }

    @PostMapping("/login/wechat")
    public ApiResponse<Map<String, Object>> loginByWechat(@RequestBody Map<String, String> body) {
        return ApiResponse.success(
                authService.loginByWechat(body.get("code"), body.get("state")));
    }

    @PostMapping("/login/sso")
    public ApiResponse<Map<String, Object>> loginBySso(@RequestBody Map<String, Object> body) {
        // SSO登录 (V1.2)
        return ApiResponse.success(Map.of("message", "SSO登录功能将在V1.2上线"));
    }

    @PostMapping("/refresh-token")
    public ApiResponse<Map<String, Object>> refreshToken(@RequestBody Map<String, String> body) {
        return ApiResponse.success(
                authService.refreshToken(body.get("refresh_token")));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String authHeader,
                                     @RequestBody(required = false) Map<String, String> body) {
        String accessToken = authHeader.replace("Bearer ", "");
        String refreshToken = body != null ? body.get("refresh_token") : null;
        authService.logout(accessToken, refreshToken);
        return ApiResponse.success();
    }

    /** [DEV] 初始化测试账号（仅 dev 环境使用） */
    @PostMapping("/dev/init")
    public ApiResponse<Map<String, Object>> devInit(@RequestBody Map<String, String> body) {
        return ApiResponse.success(authService.devInit(
                body.getOrDefault("account", "admin"),
                body.getOrDefault("password", "admin123")));
    }
}
