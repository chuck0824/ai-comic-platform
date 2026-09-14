package com.aicp.module.contentproject.service;

import com.aicp.module.contentproject.dto.ContentProjectViews.StageView;
import com.aicp.module.contentproject.dto.ContentProjectViews.WorkflowView;
import com.aicp.module.contentproject.entity.StageTruthDiffLog;
import com.aicp.module.contentproject.mapper.StageTruthDiffLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * R2-A §12：灰度期对比旧 workflow 推断与检查点投影差异，只记不覆盖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StageTruthDiffLogService {

    private final StageTruthDiffLogMapper diffLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * @return true 若写入了新差异行；一致或与最近指纹相同则 false
     */
    public boolean recordIfDifferent(Long projectId, String triggerSource, WorkflowView legacy, WorkflowView truth) {
        if (projectId == null || legacy == null || truth == null) {
            return false;
        }
        try {
            Map<String, Object> legacySnap = compact(legacy);
            Map<String, Object> truthSnap = compact(truth);
            Map<String, Object> diff = buildDiff(legacySnap, truthSnap);
            if (Boolean.TRUE.equals(diff.get("identical"))) {
                return false;
            }

            String fingerprint = sha256(toJson(Map.of("legacy", legacySnap, "truth", truthSnap)));
            StageTruthDiffLog recent = diffLogMapper.selectOne(
                    new LambdaQueryWrapper<StageTruthDiffLog>()
                            .eq(StageTruthDiffLog::getProjectId, projectId)
                            .eq(StageTruthDiffLog::getFingerprint, fingerprint)
                            .orderByDesc(StageTruthDiffLog::getId)
                            .last("limit 1"));
            if (recent != null) {
                return false;
            }

            StageTruthDiffLog row = new StageTruthDiffLog();
            row.setProjectId(projectId);
            row.setTriggerSource(triggerSource == null ? "workflow" : triggerSource);
            row.setLegacyCurrentStage(asString(legacySnap.get("currentStageKey")));
            row.setTruthCurrentStage(asString(truthSnap.get("currentStageKey")));
            row.setLegacyProgress(asInt(legacySnap.get("progress")));
            row.setTruthProgress(asInt(truthSnap.get("progress")));
            row.setLegacySnapshotJson(toJson(legacySnap));
            row.setTruthSnapshotJson(toJson(truthSnap));
            row.setDiffSummaryJson(toJson(diff));
            row.setFingerprint(fingerprint);
            row.setCreatedAt(LocalDateTime.now());
            diffLogMapper.insert(row);
            log.info("stage_truth_diff logged projectId={} source={} currentLegacy={} currentTruth={}",
                    projectId, row.getTriggerSource(), row.getLegacyCurrentStage(), row.getTruthCurrentStage());
            return true;
        } catch (Exception e) {
            log.warn("stage_truth_diff_log failed for project {}: {}", projectId, e.getMessage());
            return false;
        }
    }

    Map<String, Object> compact(WorkflowView view) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("currentStageKey", view.currentStageKey());
        snap.put("currentTaskKey", view.currentTaskKey());
        snap.put("progress", view.progress());
        List<Map<String, Object>> stages = new ArrayList<>();
        if (view.stages() != null) {
            for (StageView stage : view.stages()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", stage.key());
                item.put("status", stage.status());
                stages.add(item);
            }
        }
        snap.put("stages", stages);
        return snap;
    }

    Map<String, Object> buildDiff(Map<String, Object> legacy, Map<String, Object> truth) {
        Map<String, Object> diff = new LinkedHashMap<>();
        boolean currentMismatch = !Objects.equals(legacy.get("currentStageKey"), truth.get("currentStageKey"));
        int legacyProgress = asInt(legacy.get("progress")) == null ? 0 : asInt(legacy.get("progress"));
        int truthProgress = asInt(truth.get("progress")) == null ? 0 : asInt(truth.get("progress"));
        boolean progressMismatch = legacyProgress != truthProgress;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> legacyStages = (List<Map<String, Object>>) legacy.getOrDefault("stages", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> truthStages = (List<Map<String, Object>>) truth.getOrDefault("stages", List.of());

        Map<String, String> legacyStatus = indexStatus(legacyStages);
        Map<String, String> truthStatus = indexStatus(truthStages);
        List<Map<String, Object>> stageDiffs = new ArrayList<>();
        for (String key : unionKeys(legacyStatus, truthStatus)) {
            String left = legacyStatus.get(key);
            String right = truthStatus.get(key);
            if (!Objects.equals(left, right)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", key);
                item.put("legacyStatus", left);
                item.put("truthStatus", right);
                stageDiffs.add(item);
            }
        }

        boolean identical = !currentMismatch && !progressMismatch && stageDiffs.isEmpty();
        diff.put("identical", identical);
        diff.put("currentStageMismatch", currentMismatch);
        diff.put("progressDelta", truthProgress - legacyProgress);
        diff.put("stageStatusDiffs", stageDiffs);
        return diff;
    }

    private Map<String, String> indexStatus(List<Map<String, Object>> stages) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, Object> stage : stages) {
            Object key = stage.get("key");
            if (key != null) {
                out.put(String.valueOf(key), stage.get("status") == null ? null : String.valueOf(stage.get("status")));
            }
        }
        return out;
    }

    private List<String> unionKeys(Map<String, String> left, Map<String, String> right) {
        LinkedHashMap<String, Boolean> keys = new LinkedHashMap<>();
        left.keySet().forEach(k -> keys.put(k, Boolean.TRUE));
        right.keySet().forEach(k -> keys.put(k, Boolean.TRUE));
        return new ArrayList<>(keys.keySet());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer asInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
