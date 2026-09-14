package com.aicp.module.contentproject.service.stage;

import com.aicp.module.contentproject.domain.ScriptStageKey;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.entity.StoryboardHandoffSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 文字分镜门禁在 COMPLETE_HANDOFF 前执行；交接快照是完成产物，不能作为前置硬依赖。
 * 以工作台草稿中的连续性结果、镜头/场景数与审核正文版本引用为准。
 */
@Component
@RequiredArgsConstructor
public class TextStoryboardGateEvaluator implements StageGateEvaluator {

    private static final Set<String> APPROVED = Set.of("approved", "locked", "accepted");

    private final StageGateSupport support;

    @Override
    public ScriptStageKey stageKey() {
        return ScriptStageKey.TEXT_STORYBOARD;
    }

    @Override
    public StageGateResult evaluate(ContentProject project) {
        Map<String, Object> payload = support.loadUnitPayload(project.getId(), ScriptStageKey.TEXT_STORYBOARD.value());
        StoryboardHandoffSnapshot handoff = support.latestHandoff(project.getId());
        ContentUnit scriptBody = support.findUnit(project.getId(), ScriptStageKey.SCRIPT_BODY.value());
        ContentVersion scriptVersion = support.loadCurrentVersion(scriptBody);

        StageGateSupport.Builder b = support.builder()
                .evidence("handoffSnapshotId", handoff == null ? null : handoff.getId())
                .evidence("archived", payload.get("archived"));

        String continuity = stringOrNull(support.firstNonBlank(payload,
                "continuityCheckResult", "continuity_check_result"));
        if (continuity == null && handoff != null) {
            continuity = handoff.getContinuityCheckResult();
        }
        Object continuityObj = payload.get("continuity");
        if (continuity == null && continuityObj instanceof Map<?, ?> map) {
            if (Boolean.TRUE.equals(map.get("passed"))) {
                continuity = "PASS";
            } else if (map.get("status") != null) {
                String status = String.valueOf(map.get("status"));
                if ("PASSED".equalsIgnoreCase(status)) {
                    continuity = "PASS";
                } else if ("FAILED".equalsIgnoreCase(status) || "FAIL".equalsIgnoreCase(status)) {
                    continuity = "FAIL";
                }
            } else if (map.get("result") != null) {
                continuity = String.valueOf(map.get("result"));
            }
        }
        if (continuity == null) {
            Object nested = payload.get("continuityCheck");
            if (nested instanceof Map<?, ?> map && map.get("result") != null) {
                continuity = String.valueOf(map.get("result"));
            }
        }

        int sceneCount = 0;
        List<Map<String, Object>> shots = support.asObjectList(payload.get("shots"));
        sceneCount += shots.size();
        for (Map<String, Object> episode : support.asObjectList(payload.get("episodes"))) {
            sceneCount += support.asObjectList(episode.get("scenes")).size();
        }
        if (sceneCount == 0 && handoff != null && handoff.getSceneCount() != null) {
            sceneCount = handoff.getSceneCount();
        }

        Long reviewedId = null;
        Object raw = support.firstNonBlank(payload, "reviewedScriptBodyVersionId",
                "reviewed_script_body_version_id", "contentVersionId", "content_version_id");
        if (raw != null) {
            try {
                reviewedId = Long.valueOf(String.valueOf(raw));
            } catch (Exception ignored) {
                reviewedId = null;
            }
        }
        if (reviewedId == null && handoff != null) {
            reviewedId = handoff.getReviewedScriptBodyVersionId();
        }

        b.evidence("continuityCheckResult", continuity)
                .evidence("sceneCount", sceneCount)
                .evidence("reviewedScriptBodyVersionId", reviewedId)
                .require(continuity != null && "PASS".equalsIgnoreCase(continuity),
                        "CONTINUITY_NOT_PASS", "连续性检查未通过")
                .require(sceneCount > 0, "STORYBOARD_SCENE_REQUIRED", "分镜场景数为 0")
                .require(reviewedId != null, "REVIEWED_SCRIPT_REQUIRED", "未引用审核通过的正文版本");

        if (scriptVersion != null) {
            boolean approved = APPROVED.contains(String.valueOf(scriptVersion.getStatus()).toLowerCase(Locale.ROOT));
            b.evidence("scriptBodyStatus", scriptVersion.getStatus());
            if (reviewedId != null) {
                b.require(approved || reviewedId.equals(scriptVersion.getId()),
                        "REVIEWED_SCRIPT_MISMATCH", "未指向审核通过正文版本");
            }
        }

        boolean archived = Boolean.TRUE.equals(payload.get("archived"));
        b.warn(!archived, "STORYBOARD_NOT_ARCHIVED", "文字分镜尚未归档");
        return b.build();
    }

    private String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
