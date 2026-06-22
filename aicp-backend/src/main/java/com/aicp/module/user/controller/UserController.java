package com.aicp.module.user.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> getProfile() {
        return ApiResponse.success(userService.getProfile());
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(userService.updateProfile(body));
    }

    @PostMapping("/verify/real-name")
    public ApiResponse<Map<String, Object>> verifyRealName(@RequestBody Map<String, String> body) {
        return ApiResponse.success(userService.verifyRealName(body));
    }

    @GetMapping("/membership")
    public ApiResponse<Map<String, Object>> getMembership() {
        return ApiResponse.success(userService.getMembership());
    }

    @PostMapping("/membership/upgrade")
    public ApiResponse<Map<String, Object>> upgradeMembership(@RequestBody Map<String, String> body) {
        return ApiResponse.success(userService.upgradeMembership(body));
    }

    @GetMapping("/api-keys")
    public ApiResponse<?> getApiKeys() { return ApiResponse.success(java.util.List.of()); }

    @PostMapping("/api-keys")
    public ApiResponse<?> createApiKey(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(Map.of("message", "V1.2功能"));
    }

    @DeleteMapping("/api-keys/{id}")
    public ApiResponse<?> deleteApiKey(@PathVariable Long id) { return ApiResponse.success(); }
}
