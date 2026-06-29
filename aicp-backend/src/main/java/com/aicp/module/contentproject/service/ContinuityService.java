package com.aicp.module.contentproject.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContinuitySnapshot;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.aicp.module.contentproject.mapper.ContinuitySnapshotMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * M2: Continuity snapshots — track character state, relationships, props,
 * and foreshadowing across episodes. Detects conflicts between adjacent units.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContinuityService {

    private final ContinuitySnapshotMapper snapshotMapper;
    private final ContentUnitMapper unitMapper;
    private final ContentVersionMapper versionMapper;
    private final AiRouter aiRouter;
    private final ObjectMapper objectMapper;

    static final String SNAPSHOT_PROMPT = """
        你是一位剧本连续性分析师。请从以下剧本内容提取连续性信息，输出JSON：
        {
          "characters": [{"name": "", "location": "", "status": "", "inventory": []}],
          "relationships": [{"from": "", "to": "", "state": ""}],
          "props": [{"name": "", "location": "", "status": ""}],
          "foreshadowing": [{"id": "", "description": "", "status": "planted|paid_off"}],
          "timeline_marker": ""
        }
        """;

    @Transactional
    public ContinuitySnapshot captureSnapshot(Long unitId) {
        ContentUnit unit = unitMapper.selectById(unitId);
        if (unit == null) return null;

        ContentVersion version = versionMapper.selectOne(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unitId)
                        .gt(ContentVersion::getVersionNo, 0)
                        .orderByDesc(ContentVersion::getVersionNo)
                        .last("limit 1"));
        if (version == null) return null;

        String content = version.getPlainText();
        if (content == null || content.isBlank()) return null;

        // Delete old snapshot
        ContinuitySnapshot existing = snapshotMapper.selectOne(
                new LambdaQueryWrapper<ContinuitySnapshot>()
                        .eq(ContinuitySnapshot::getContentUnitId, unitId));
        if (existing != null) snapshotMapper.deleteById(existing.getId());

        // AI extract
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("system_prompt", SNAPSHOT_PROMPT);
        params.put("prompt", "请分析以下剧本的连续性：\n\n" + ellipsis(content, 4000));
        params.put("temperature", 0.2);
        params.put("max_tokens", 2048);

        Map<String, Object> result = aiRouter.chatCompletion(params);
        String text = extractText(result);
        Map<String, Object> parsed = parseJson(text);

        String snapshotJson;
        try { snapshotJson = objectMapper.writeValueAsString(parsed); }
        catch (Exception e) { snapshotJson = "{}"; }
        String hash = sha256(snapshotJson);

        ContinuitySnapshot snap = new ContinuitySnapshot();
        snap.setProjectId(unit.getProjectId());
        snap.setContentUnitId(unitId);
        snap.setSnapshotJson(snapshotJson);
        snap.setContentHash(hash);
        snapshotMapper.insert(snap);

        return snap;
    }

    @Transactional
    public int captureAllSnapshots(Long projectId) {
        List<ContentUnit> units = unitMapper.selectList(
                new LambdaQueryWrapper<ContentUnit>()
                        .eq(ContentUnit::getProjectId, projectId)
                        .eq(ContentUnit::getUnitType, "episode")
                        .eq(ContentUnit::getIsDeleted, 0)
                        .orderByAsc(ContentUnit::getDisplayNo));
        int count = 0;
        for (ContentUnit u : units) {
            try { if (captureSnapshot(u.getId()) != null) count++; }
            catch (Exception e) { log.warn("Snapshot failed for unit {}", u.getId(), e); }
        }
        return count;
    }

    /**
     * Check continuity between adjacent units for conflicts.
     */
    public List<Map<String, Object>> checkConflicts(Long projectId) {
        List<ContentUnit> units = unitMapper.selectList(
                new LambdaQueryWrapper<ContentUnit>()
                        .eq(ContentUnit::getProjectId, projectId)
                        .eq(ContentUnit::getUnitType, "episode")
                        .eq(ContentUnit::getIsDeleted, 0)
                        .orderByAsc(ContentUnit::getDisplayNo));

        List<Map<String, Object>> conflicts = new ArrayList<>();
        ContinuitySnapshot prev = null;

        for (ContentUnit u : units) {
            ContinuitySnapshot curr = snapshotMapper.selectOne(
                    new LambdaQueryWrapper<ContinuitySnapshot>()
                            .eq(ContinuitySnapshot::getContentUnitId, u.getId()));
            if (prev != null && curr != null) {
                Map<String, Object> diff = compareSnapshots(prev, curr);
                if (!diff.isEmpty()) {
                    conflicts.add(Map.of(
                            "from_unit", prev.getContentUnitId(),
                            "to_unit", curr.getContentUnitId(),
                            "from_display", units.indexOf(u) > 0 ? units.get(units.indexOf(u)-1).getDisplayNo() : 0,
                            "to_display", u.getDisplayNo(),
                            "conflicts", diff));
                }
            }
            prev = curr;
        }
        return conflicts;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> compareSnapshots(ContinuitySnapshot a, ContinuitySnapshot b) {
        Map<String, Object> diffs = new LinkedHashMap<>();
        try {
            Map<String, Object> sa = objectMapper.readValue(a.getSnapshotJson(), Map.class);
            Map<String, Object> sb = objectMapper.readValue(b.getSnapshotJson(), Map.class);

            // Compare character locations
            List<Map<String, Object>> charsA = (List<Map<String, Object>>) sa.getOrDefault("characters", List.of());
            List<Map<String, Object>> charsB = (List<Map<String, Object>>) sb.getOrDefault("characters", List.of());
            for (Map<String, Object> ca : charsA) {
                String name = (String) ca.get("name");
                String locA = (String) ca.get("location");
                for (Map<String, Object> cb : charsB) {
                    if (name != null && name.equals(cb.get("name"))) {
                        String locB = (String) cb.get("location");
                        if (locA != null && locB != null && !locA.equals(locB)) {
                            diffs.put("character_" + name, Map.of("from", locA, "to", locB));
                        }
                    }
                }
            }
        } catch (Exception e) { /* ignore parse errors */ }
        return diffs;
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> result) {
        Object choices = result.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map) {
                Object message = ((Map<String, Object>) first).get("message");
                if (message instanceof Map) {
                    Object c = ((Map<String, Object>) message).get("content");
                    if (c != null) return String.valueOf(c);
                }
            }
        }
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String text) {
        try {
            String json = text;
            if (text.contains("```json")) {
                int s = text.indexOf("```json") + 7;
                int e = text.indexOf("```", s);
                if (e > s) json = text.substring(s, e).trim();
            }
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) { return Map.of(); }
    }

    private String ellipsis(String s, int max) { return s != null && s.length() > max ? s.substring(0, max) + "..." : s; }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) { return "" + input.hashCode(); }
    }
}
