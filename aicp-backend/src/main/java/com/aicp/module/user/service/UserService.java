package com.aicp.module.user.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.auth.entity.User;
import com.aicp.module.auth.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    public Map<String, Object> getProfile() {
        User user = userMapper.selectById(getCurrentUserId());
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uuid", user.getUuid());
        result.put("nickname", user.getNickname());
        result.put("avatar_url", user.getAvatarUrl());
        result.put("account_type", user.getAccountType());
        result.put("phone", maskPhone(user.getPhone()));
        result.put("email", maskEmail(user.getEmail()));
        result.put("real_name_status", user.getRealNameStatus());
        result.put("member_level", user.getMemberLevel());
        result.put("member_expire_at", user.getMemberExpireAt());
        result.put("status", user.getStatus());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("scripts_generated", 0);
        stats.put("videos_exported", 0);
        stats.put("scripts_in_repo", 0);
        stats.put("storage_used_mb", 0);
        result.put("stats", stats);

        result.put("created_at", user.getCreateTime());
        result.put("last_login_at", user.getLastLoginAt());
        return result;
    }

    public Map<String, Object> updateProfile(Map<String, Object> body) {
        User user = userMapper.selectById(getCurrentUserId());
        if (body.containsKey("nickname")) user.setNickname((String) body.get("nickname"));
        if (body.containsKey("avatar_url")) user.setAvatarUrl((String) body.get("avatar_url"));
        userMapper.updateById(user);
        return getProfile();
    }

    public Map<String, Object> verifyRealName(Map<String, String> body) {
        return Map.of("real_name_status", "pending", "estimated_review_hours", 24);
    }

    public Map<String, Object> getMembership() {
        User user = userMapper.selectById(getCurrentUserId());
        Map<String, Object> benefits = new LinkedHashMap<>();
        if ("creator".equals(user.getMemberLevel()) || "enterprise".equals(user.getMemberLevel())) {
            benefits.put("daily_gen_quota", -1);
            benefits.put("repo_capacity", -1);
            benefits.put("can_list_script", true);
            benefits.put("export_no_watermark", true);
            benefits.put("batch_generate", true);
            benefits.put("max_resolution", "1080p");
        } else {
            benefits.put("daily_gen_quota", 3);
            benefits.put("repo_capacity", 5);
            benefits.put("can_list_script", false);
            benefits.put("export_no_watermark", false);
            benefits.put("batch_generate", false);
            benefits.put("max_resolution", "720p");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", user.getMemberLevel());
        result.put("expire_at", user.getMemberExpireAt());
        result.put("auto_renew", false);
        result.put("benefits", benefits);
        return result;
    }

    public Map<String, Object> upgradeMembership(Map<String, String> body) {
        return Map.of("message", "会员升级功能将在V1.1上线",
                "payment_params", Map.of("method", body.get("payment_method")));
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@");
        String name = parts[0];
        if (name.length() <= 2) return name + "***@" + parts[1];
        return name.substring(0, 2) + "***@" + parts[1];
    }
}
