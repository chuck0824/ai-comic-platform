package com.aicp.module.notify.controller;

import com.aicp.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/notify")
public class NotifyController {

    @GetMapping("/in-app")
    public ApiResponse<List<Map<String, Object>>> getNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(List.of(
                Map.of("id", 1, "type", "script_generated", "title", "剧本生成完成",
                        "content", "《霸道总裁的替身新娘》已生成完毕，点击查看",
                        "target_url", "/scripts/12345", "is_read", false,
                        "created_at", LocalDateTime.now().minusMinutes(30).toString()),
                Map.of("id", 2, "type", "order_paid", "title", "剧本售出",
                        "content", "《重生之商业帝国》已被购买，收入 ¥23.92 已到账",
                        "target_url", "/trade/sales", "is_read", true,
                        "created_at", LocalDateTime.now().minusHours(2).toString()),
                Map.of("id", 3, "type", "export_completed", "title", "视频导出完成",
                        "content", "《霸道总裁的替身新娘》第1集已导出，点击下载",
                        "target_url", "/exports/123", "is_read", false,
                        "created_at", LocalDateTime.now().minusHours(5).toString())
        ));
    }

    @PutMapping("/in-app/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        return ApiResponse.success();
    }

    @PutMapping("/in-app/read-all")
    public ApiResponse<Void> markAllRead() {
        return ApiResponse.success();
    }

    @GetMapping("/preferences")
    public ApiResponse<Map<String, Object>> getPreferences() {
        return ApiResponse.success(Map.of(
                "script_generated", Map.of("in_app", true, "email", false, "push", true),
                "order_paid", Map.of("in_app", true, "email", true, "sms", true),
                "export_completed", Map.of("in_app", true, "email", true, "push", true)));
    }

    @PutMapping("/preferences")
    public ApiResponse<Void> updatePreferences(@RequestBody Map<String, Object> body) {
        return ApiResponse.success();
    }
}
