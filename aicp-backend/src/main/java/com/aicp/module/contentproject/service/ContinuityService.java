package com.aicp.module.contentproject.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContinuitySnapshot;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.aicp.module.contentproject.mapper.ContinuitySnapshotMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AiResponseParser parser;

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
        params.put("prompt", "请分析以下剧本的连续性：\n\n" + parser.ellipsis(content, 4000));
        params.put("temperature", 0.2);
        params.put("max_tokens", 2048);

        Map<String, Object> result = aiRouter.chatCompletion(params);
        String text = parser.extractText(result);
        Map<String, Object> parsed = parser.parseJson(text);

        String snapshotJson;
        try { snapshotJson = objectMapper.writeValueAsString(parsed); }
        catch (Exception e) { snapshotJson = "{}"; }
        String hash = parser.sha256(snapshotJson);

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
        ContinuitySnapshot prevSnapshot = null;
        ContentUnit prevUnit = null;

        for (ContentUnit u : units) {
            ContinuitySnapshot curr = snapshotMapper.selectOne(
                    new LambdaQueryWrapper<ContinuitySnapshot>()
                            .eq(ContinuitySnapshot::getContentUnitId, u.getId()));
            if (prevSnapshot != null && curr != null) {
                Map<String, Object> diff = compareSnapshots(prevSnapshot, curr);
                if (!diff.isEmpty()) {
                    conflicts.add(Map.of(
                            "from_unit", prevSnapshot.getContentUnitId(),
                            "to_unit", curr.getContentUnitId(),
                            "from_display", prevUnit != null ? prevUnit.getDisplayNo() : 0,
                            "to_display", u.getDisplayNo(),
                            "conflicts", diff));
                }
            }
            prevSnapshot = curr;
            prevUnit = u;
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

            // Compare relationship changes
            List<Map<String, Object>> relsA = (List<Map<String, Object>>) sa.getOrDefault("relationships", List.of());
            List<Map<String, Object>> relsB = (List<Map<String, Object>>) sb.getOrDefault("relationships", List.of());
            for (Map<String, Object> ra : relsA) {
                String pair = (String) ra.get("pair");
                String stateA = (String) ra.get("state");
                for (Map<String, Object> rb : relsB) {
                    if (pair != null && pair.equals(rb.get("pair"))) {
                        String stateB = (String) rb.get("state");
                        if (stateA != null && stateB != null && !stateA.equals(stateB)) {
                            diffs.put("relationship_" + pair, Map.of("from", stateA, "to", stateB));
                        }
                    }
                }
            }

            // Compare props
            List<Map<String, Object>> propsA = (List<Map<String, Object>>) sa.getOrDefault("props", List.of());
            List<Map<String, Object>> propsB = (List<Map<String, Object>>) sb.getOrDefault("props", List.of());
            for (Map<String, Object> pa : propsA) {
                String pName = (String) pa.get("name");
                String statusA = (String) pa.get("status");
                for (Map<String, Object> pb : propsB) {
                    if (pName != null && pName.equals(pb.get("name"))) {
                        String statusB = (String) pb.get("status");
                        if (statusA != null && statusB != null && !statusA.equals(statusB)) {
                            diffs.put("prop_" + pName, Map.of("from", statusA, "to", statusB));
                        }
                    }
                }
            }

            // Compare foreshadowing state transitions
            List<Map<String, Object>> foresA = (List<Map<String, Object>>) sa.getOrDefault("foreshadowing", List.of());
            List<Map<String, Object>> foresB = (List<Map<String, Object>>) sb.getOrDefault("foreshadowing", List.of());
            for (Map<String, Object> fa : foresA) {
                String desc = (String) fa.get("description");
                String stateA = (String) fa.get("state");
                for (Map<String, Object> fb : foresB) {
                    if (desc != null && desc.equals(fb.get("description"))) {
                        String stateB = (String) fb.get("state");
                        if (stateA != null && stateB != null && !stateA.equals(stateB)) {
                            diffs.put("foreshadowing_" + desc.substring(0, Math.min(desc.length(), 30)),
                                    Map.of("from", stateA, "to", stateB));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to compare continuity snapshots", e);
        }
        return diffs;
    }
}