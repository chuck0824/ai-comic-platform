package com.aicp.module.sop.controller;

import com.aicp.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/sop")
public class SopController {

    @PostMapping("/check/production-readiness")
    public ApiResponse<Map<String, Object>> checkReadiness(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> checks = new ArrayList<>();
        String[] names = {"剧情事实无偏移", "场景目标明确", "Beat完整", "人物关系变化明确",
                "关键对白已锁定", "资产ID完整", "高风险镜头已标记", "AI提示词不过长",
                "D/E级镜头已拆分", "抽卡表/视频表已区分", "Voice ID明确", "配音字幕表就绪", "上一章状态已继承"};
        String[] results = {"pass", "pass", "pass", "pass", "pass", "warning", "pass", "fail",
                "pass", "warning", "pass", "pass", "pass"};

        int passed = 0, failed = 0;
        for (int i = 0; i < names.length; i++) {
            Map<String, Object> check = new LinkedHashMap<>();
            check.put("id", i + 1);
            check.put("name", names[i]);
            check.put("result", results[i]);
            if (results[i].equals("pass")) passed++;
            else if (results[i].equals("fail")) failed++;
            checks.add(check);
        }

        String overall = failed >= 3 ? "red" : (failed > 0 ? "yellow" : "green");
        return ApiResponse.success(Map.of(
                "overall", overall, "passed", passed, "failed", failed,
                "checks", checks,
                "recommendation", failed > 0 ? (failed + "项未通过，建议修复后进入生产") : "可以进入生产"));
    }

    @GetMapping("/projects/{id}/audit-list")
    public ApiResponse<List<Map<String, Object>>> getAuditList(@PathVariable String id) {
        return ApiResponse.success(List.of(
                Map.of("id", 1, "shot_id", "EP01_SC03_SH008", "check_item", "场景连续性",
                        "issue_type", "空间跳变", "severity", "P0", "quality_grade", "C",
                        "description", "SH008角色在室内门口，SH009无转场出现在街道",
                        "status", "open"),
                Map.of("id", 2, "shot_id", "EP01_SC05_SH003", "check_item", "AI提示词长度",
                        "issue_type", "Prompt过长", "severity", "P1", "quality_grade", "B",
                        "description", "Prompt超500字符", "status", "fixing"),
                Map.of("id", 3, "shot_id", "EP01_SC01_SH005", "check_item", "道具一致性",
                        "issue_type", "颜色不一致", "severity", "P2", "quality_grade", "A",
                        "description", "道具颜色不一致", "status", "fixed")
        ));
    }

    @PostMapping("/projects/{id}/audit")
    public ApiResponse<Map<String, Object>> submitAudit(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(Map.of("audit_id", System.currentTimeMillis(), "status", "open"));
    }

    @PutMapping("/projects/{id}/audit/{auditId}")
    public ApiResponse<Void> updateAudit(@PathVariable String id, @PathVariable Long auditId,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.success();
    }

    @GetMapping("/versions/{projectId}")
    public ApiResponse<List<Map<String, Object>>> getVersionHistory(@PathVariable String projectId) {
        return ApiResponse.success(List.of(
                Map.of("version", "V0.1", "status", "草稿", "created_at", "2026-06-01"),
                Map.of("version", "V0.5", "status", "编导确认", "created_at", "2026-06-05"),
                Map.of("version", "V0.8", "status", "导演确认", "created_at", "2026-06-08")
        ));
    }

    @PostMapping("/versions/{projectId}/promote")
    public ApiResponse<Void> promoteVersion(@PathVariable String projectId, @RequestBody Map<String, String> body) {
        return ApiResponse.success();
    }

    @PostMapping("/assets/{type}/{id}/lock")
    public ApiResponse<Void> lockAsset(@PathVariable String type, @PathVariable String id) {
        return ApiResponse.success();
    }

    @PostMapping("/assets/{type}/{id}/unlock")
    public ApiResponse<Void> unlockAsset(@PathVariable String type, @PathVariable String id) {
        return ApiResponse.success();
    }

    @PostMapping("/failure/record")
    public ApiResponse<Map<String, Object>> recordFailure(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(Map.of("failure_id", System.currentTimeMillis()));
    }

    @GetMapping("/failure/strategy")
    public ApiResponse<Map<String, Object>> getFailureStrategy(@RequestParam String shotId) {
        return ApiResponse.success(Map.of(
                "shot_id", shotId, "failure_count", 3,
                "recommended_action", "检查资产与参考图",
                "suggestions", List.of("强化 Face_ID 的参考图", "减少动作复杂度：当前单镜包含3个动作")));
    }

    @GetMapping("/projects/{id}/capacity")
    public ApiResponse<Map<String, Object>> getCapacity(@PathVariable String id) {
        return ApiResponse.success(Map.of(
                "estimated_hours", 12.5, "complexity", "B",
                "shot_count", 18, "risk_shots", 3));
    }
}
