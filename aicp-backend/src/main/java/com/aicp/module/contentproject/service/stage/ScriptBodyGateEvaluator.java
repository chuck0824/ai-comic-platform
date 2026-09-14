package com.aicp.module.contentproject.service.stage;

import com.aicp.module.contentproject.domain.ScriptStageKey;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ScriptBodyGateEvaluator implements StageGateEvaluator {

    private final StageGateSupport support;

    @Override
    public ScriptStageKey stageKey() {
        return ScriptStageKey.SCRIPT_BODY;
    }

    @Override
    public StageGateResult evaluate(ContentProject project) {
        ContentUnit unit = support.findUnit(project.getId(), ScriptStageKey.SCRIPT_BODY.value());
        Map<String, Object> payload = support.loadUnitPayload(project.getId(), ScriptStageKey.SCRIPT_BODY.value());
        StageGateSupport.Builder b = support.builder()
                .evidence("contentUnitId", unit == null ? null : unit.getId())
                .evidence("revision", unit == null ? null : unit.getRevision());

        b.require(unit != null, "CONTENT_UNIT_MISSING", "剧本正文内容单元缺失");
        List<Map<String, Object>> episodes = support.asObjectList(payload.get("episodes"));
        int sceneCount = 0;
        int bodyBlocks = 0;
        int unbound = 0;
        int staleRefs = 0;
        for (Map<String, Object> episode : episodes) {
            for (Map<String, Object> scene : support.asObjectList(episode.get("scenes"))) {
                sceneCount++;
                Object body = support.firstNonBlank(scene, "body", "text");
                if (!support.blank(body)) {
                    bodyBlocks++;
                }
                for (Map<String, Object> block : support.asObjectList(scene.get("blocks"))) {
                    if (!support.blank(block.get("text"))) {
                        bodyBlocks++;
                    }
                }
                Object bindingState = scene.get("bindingState");
                Object assetBinding = scene.get("assetBinding");
                boolean deferred = "DEFERRED".equalsIgnoreCase(String.valueOf(bindingState));
                if (assetBinding == null && !deferred) {
                    unbound++;
                }
                if (assetBinding instanceof Map<?, ?> binding) {
                    Object status = binding.get("status");
                    if (status != null && List.of("STALE", "INVALID", "ARCHIVED", "DISABLED")
                            .contains(String.valueOf(status).toUpperCase())) {
                        staleRefs++;
                    }
                }
            }
        }
        // 顶层 scenes（兼容扁平结构）
        if (sceneCount == 0) {
            for (Map<String, Object> scene : support.asObjectList(payload.get("scenes"))) {
                sceneCount++;
                if (!support.blank(support.firstNonBlank(scene, "body", "text"))) {
                    bodyBlocks++;
                }
            }
        }

        b.evidence("sceneCount", sceneCount)
                .evidence("bodyBlockCount", bodyBlocks)
                .evidence("unboundScenes", unbound)
                .require(sceneCount > 0, "SCRIPT_SCENE_REQUIRED", "剧本正文至少需要一个场景")
                .require(bodyBlocks > 0, "SCRIPT_BODY_REQUIRED", "场景正文不能为空")
                .require(unbound == 0, "SCENE_ASSET_BINDING_REQUIRED", "存在未绑定且未延期的场景资产")
                .require(staleRefs == 0, "ASSET_REF_INVALID", "存在失效的场景资产引用");

        // saveStatus：有草稿 revision 即视为已同步；显式 UNSYNCED 才阻断
        Object saveStatus = support.firstNonBlank(payload, "saveStatus", "save_status");
        if (saveStatus != null && "UNSYNCED".equalsIgnoreCase(String.valueOf(saveStatus))) {
            b.require(false, "SAVE_STATUS_UNSYNCED", "正文尚未同步保存");
        } else {
            b.evidence("saveStatus", saveStatus == null ? "SYNCED" : saveStatus);
        }
        return b.build();
    }
}
