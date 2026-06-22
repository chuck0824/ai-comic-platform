package com.aicp.common.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 安全上下文工具类
 * 从 Spring Security SecurityContextHolder 中提取当前认证用户信息
 *
 * 使用方式：
 *   Long userId = SecurityUtil.getCurrentUserId();
 *   // 如果未认证则抛出异常
 *   Long userId = SecurityUtil.requireCurrentUserId();
 */
@Component
public class SecurityUtil {

    private SecurityUtil() {
        // 工具类，禁止实例化
    }

    /**
     * 获取当前登录用户ID。未认证时返回 null。
     */
    public static Long getCurrentUserId() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(auth -> {
                    Object principal = auth.getPrincipal();
                    if (principal instanceof Long userId) {
                        return userId;
                    }
                    // 兼容 Map 类型的 details
                    if (auth.getDetails() instanceof Map<?, ?> details) {
                        Object uid = details.get("userId");
                        if (uid instanceof Long l) return l;
                        if (uid instanceof Integer i) return i.longValue();
                    }
                    return null;
                })
                .orElse(null);
    }

    /**
     * 获取当前登录用户ID。未认证时抛出异常。
     */
    public static Long requireCurrentUserId() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("未登录或Token已过期，请重新登录");
        }
        return userId;
    }

    /**
     * 获取当前用户UUID（JWT subject）。
     */
    public static String getCurrentUserUuid() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(auth -> {
                    if (auth.getDetails() instanceof Map<?, ?> details) {
                        Object uuid = details.get("uuid");
                        return uuid != null ? uuid.toString() : null;
                    }
                    return null;
                })
                .orElse(null);
    }

    /**
     * 获取当前用户角色。
     */
    public static String getCurrentUserRole() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .flatMap(auth -> auth.getAuthorities().stream()
                        .map(Object::toString)
                        .findFirst())
                .orElse(null);
    }

    /**
     * 获取当前用户权限列表。
     */
    public static List<String> getCurrentUserPermissions() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(auth -> auth.getAuthorities().stream()
                        .map(Object::toString)
                        .toList())
                .orElse(List.of());
    }

    /**
     * 检查当前用户是否已认证。
     */
    public static boolean isAuthenticated() {
        return getCurrentUserId() != null;
    }

    /**
     * 获取当前用户账户类型（从 JWT details 中提取）。
     */
    public static String getCurrentAccountType() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(auth -> {
                    if (auth.getDetails() instanceof Map<?, ?> details) {
                        Object type = details.get("type");
                        return type != null ? type.toString() : "free_user";
                    }
                    return "free_user";
                })
                .orElse("free_user");
    }
}
