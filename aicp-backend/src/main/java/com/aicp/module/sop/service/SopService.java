package com.aicp.module.sop.service;

import com.aicp.module.sop.entity.SopAudit;
import com.aicp.module.sop.mapper.SopAuditMapper;
import com.aicp.module.canvas.entity.StoryboardShot;
import com.aicp.module.canvas.mapper.StoryboardShotMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SopService {

    private final SopAuditMapper auditMapper;
    private final StoryboardShotMapper shotMapper;

    public Map<String, Object> checkProductionReadiness(String projectId) {
        List<Map<String, Object>> checks = new ArrayList<>();
        String[][] items = {
            {"剧情事实无偏移", "pass"}, {"场景目标明确", "pass"}, {"Beat完整", "pass"},
            {"人物关系变化明确", "pass"}, {"关键对白已锁定", "pass"}, {"资产ID完整", "warning"},
            {"高风险镜头已标记", "pass"}, {"AI提示词不过长", "fail"}, {"D/E级镜头已拆分", "pass"},
            {"抽卡表/视频表已区分", "warning"}, {"Voice ID明确", "pass"},
            {"配音字幕表就绪", "pass"}, {"上一章状态已继承", "pass"}
        };

        int passed = 0, failed = 0;
        for (int i = 0; i < items.length; i++) {
            Map<String, Object> check = new LinkedHashMap<>();
            check.put("id", i + 1); check.put("name", items[i][0]); check.put("result", items[i][1]);
            if ("pass".equals(items[i][1])) passed++;
            else if ("fail".equals(items[i][1])) failed++;
            checks.add(check);
        }
        String overall = failed >= 3 ? "red" : (failed > 0 ? "yellow" : "green");
        return Map.of("overall", overall, "passed", passed, "failed", failed, "checks", checks,
                "recommendation", failed > 0 ? failed + "项未通过，建议修复后进入生产" : "可以进入生产");
    }

    public List<SopAudit> getAuditList(String projectId) {
        return auditMapper.selectList(
                new LambdaQueryWrapper<SopAudit>().eq(SopAudit::getProjectId, projectId));
    }

    public SopAudit submitAudit(String projectId, Map<String, Object> body) {
        SopAudit audit = new SopAudit();
        audit.setProjectId(projectId);
        audit.setCheckItem((String) body.get("check_item"));
        audit.setSeverity((String) body.getOrDefault("severity", "P2"));
        audit.setDescription((String) body.get("description"));
        audit.setStatus("open");
        auditMapper.insert(audit);
        return audit;
    }

    public void updateAudit(Long auditId, Map<String, Object> body) {
        SopAudit audit = auditMapper.selectById(auditId);
        if (audit != null) {
            if (body.containsKey("status")) audit.setStatus((String) body.get("status"));
            if (body.containsKey("fix_suggestion")) audit.setFixSuggestion((String) body.get("fix_suggestion"));
            auditMapper.updateById(audit);
        }
    }

    public List<Map<String, Object>> getVersionHistory(String projectId) {
        return List.of(
                Map.of("version", "V0.1", "status", "草稿", "created_at", "2026-06-01"),
                Map.of("version", "V0.5", "status", "编导确认", "created_at", "2026-06-05"));
    }

    public Map<String, Object> getFailureStrategy(String shotId) {
        return Map.of("shot_id", shotId, "failure_count", 3,
                "recommended_action", "检查资产与参考图",
                "suggestions", List.of("强化Face_ID参考图", "减少动作复杂度"));
    }

    public Map<String, Object> getCapacity(String projectId) {
        return Map.of("estimated_hours", 12.5, "complexity", "B", "shot_count", 18, "risk_shots", 3);
    }
}
