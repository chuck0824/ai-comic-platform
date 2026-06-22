package com.aicp.module.enterprise.controller;

import com.aicp.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/enterprise")
public class EnterpriseController {

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(Map.of(
                "enterprise_id", 100,
                "name", body.get("name"),
                "verify_status", "pending",
                "estimated_review_hours", 72));
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> getProfile() {
        return ApiResponse.success(Map.of(
                "id", 100, "name", "XX文化传媒有限公司",
                "verify_status", "verified", "owner_user_id", 1));
    }

    @PutMapping("/profile")
    public ApiResponse<Void> updateProfile(@RequestBody Map<String, Object> body) {
        return ApiResponse.success();
    }

    @GetMapping("/members")
    public ApiResponse<List<Map<String, Object>>> getMembers() {
        return ApiResponse.success(List.of(
                Map.of("user_id", 1, "nickname", "张三", "role", "admin", "department", "管理", "status", "active",
                        "permissions", List.of("can_generate_script", "can_manage_members")),
                Map.of("user_id", 2, "nickname", "李四", "role", "dept_head", "department", "内容一部", "status", "active"),
                Map.of("user_id", 3, "nickname", "王五", "role", "writer", "department", "内容一部", "status", "active"),
                Map.of("user_id", 4, "nickname", "赵六", "role", "artist", "department", "美术组", "status", "active")
        ));
    }

    @PostMapping("/members/invite")
    public ApiResponse<Map<String, String>> inviteMembers(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(Map.of("message", "邀请已发送"));
    }

    @PutMapping("/members/{uid}/role")
    public ApiResponse<Void> setMemberRole(@PathVariable Long uid, @RequestBody Map<String, Object> body) {
        return ApiResponse.success();
    }

    @DeleteMapping("/members/{uid}")
    public ApiResponse<Void> removeMember(@PathVariable Long uid) {
        return ApiResponse.success();
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboard() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("member_count", 12);
        overview.put("scripts_generated_this_month", 45);
        overview.put("videos_exported_this_month", 28);
        overview.put("total_assets", 86);

        Map<String, Object> financial = new LinkedHashMap<>();
        financial.put("monthly_spending", 3280.00);
        financial.put("purchase_orders", 15);
        financial.put("pending_approvals", 3);
        financial.put("api_calls_this_month", 1280);

        return ApiResponse.success(Map.of(
                "overview", overview,
                "financial", financial,
                "pending_items", List.of(Map.of(
                        "type", "purchase_request", "from_user", "张三",
                        "script_title", "霸道总裁的替身新娘", "amount", 29.90)),
                "recent_activity", List.of(Map.of(
                        "user", "王五", "action", "export_video",
                        "target", "重生之商业帝国 第3集", "time", "2026-06-08T14:30:00+08:00"))));
    }
}
