package com.aicp.module.contentproject.service.stage;

import com.aicp.module.contentproject.domain.ScriptStageKey;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ReviewRevisionGateEvaluator implements StageGateEvaluator {

    private static final Set<String> APPROVED = Set.of("approved", "locked", "accepted");

    private final StageGateSupport support;

    @Override
    public ScriptStageKey stageKey() {
        return ScriptStageKey.REVIEW_REVISION;
    }

    @Override
    public StageGateResult evaluate(ContentProject project) {
        Map<String, Object> payload = support.loadUnitPayload(project.getId(), ScriptStageKey.REVIEW_REVISION.value());
        ContentUnit scriptBody = support.findUnit(project.getId(), ScriptStageKey.SCRIPT_BODY.value());
        ContentVersion scriptVersion = support.loadCurrentVersion(scriptBody);

        StageGateSupport.Builder b = support.builder()
                .evidence("scriptBodyUnitId", scriptBody == null ? null : scriptBody.getId())
                .evidence("currentScriptBodyVersionId", scriptVersion == null ? null : scriptVersion.getId());

        for (Map<String, Object> issue : support.asObjectList(payload.get("issues"))) {
            String severity = String.valueOf(issue.getOrDefault("severity", "")).toUpperCase(Locale.ROOT);
            String status = String.valueOf(issue.getOrDefault("status", "OPEN")).toUpperCase(Locale.ROOT);
            if ("BLOCKER".equals(severity) && "OPEN".equals(status)) {
                b.require(false, "REVIEW_BLOCKER_OPEN",
                        "存在未关闭的审核阻断项：" + issue.getOrDefault("title", issue.getOrDefault("code", "")));
            }
        }

        List<?> approved = payload.get("approvedEpisodeIds") instanceof List<?> list
                ? list
                : support.asObjectList(payload.get("approved_episode_ids"));
        Object reviewedVersionId = support.firstNonBlank(payload, "reviewedContentVersionId",
                "reviewed_content_version_id", "versionId", "version_id");
        boolean hasApproval = (approved != null && !approved.isEmpty())
                || reviewedVersionId != null
                || (scriptVersion != null && APPROVED.contains(
                String.valueOf(scriptVersion.getStatus()).toLowerCase(Locale.ROOT)));

        b.evidence("approvedEpisodeCount", approved == null ? 0 : approved.size())
                .evidence("reviewedContentVersionId", reviewedVersionId)
                .require(hasApproval, "REVIEW_APPROVAL_REQUIRED", "尚未审核通过正文版本");

        if (reviewedVersionId != null && scriptVersion != null) {
            String expected = String.valueOf(scriptVersion.getId());
            String actual = String.valueOf(reviewedVersionId);
            b.require(expected.equals(actual), "REVIEWED_VERSION_MISMATCH",
                    "审核通过版本与当前正文版本不一致");
        }
        return b.build();
    }
}
