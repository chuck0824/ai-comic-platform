package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.SourceMode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.StoryboardIntent;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ProjectParameterVersion;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ProjectParameterVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectWorkflowService {

    private final ContentProjectMapper projectMapper;
    private final ProjectParameterVersionMapper parameterVersionMapper;
    private final ObjectMapper objectMapper;

    static final List<StageDef> SHORT_DRAMA_STAGES = List.of(
            new StageDef("story_seed", "故事种子", true),
            new StageDef("import_review", "导入审核", false),
            new StageDef("characters", "角色设定", true),
            new StageDef("synopsis", "梗概", true),
            new StageDef("outline", "大纲", true),
            new StageDef("content", "正文", true),
            new StageDef("review", "审核", true),
            new StageDef("destination", "内容去向", true),
            new StageDef("storyboard", "分镜", false)
    );

    public WorkflowView calculate(Long projectId) {
        ContentProject project = projectMapper.selectById(projectId);
        if (project == null || project.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return calculate(project, Map.of());
    }

    WorkflowView calculate(ContentProject project, Map<String, Boolean> facts) {
        String sourceMode = project.getSourceMode();
        String storyboardIntent = project.getStoryboardIntentStatus();
        String lastStage = project.getLastStageKey();
        boolean isUploaded = SourceMode.UPLOADED.value().equals(sourceMode);
        boolean storyboardSkipped = StoryboardIntent.SKIPPED.value().equals(storyboardIntent);

        List<StageView> stages = new ArrayList<>();
        int completedCount = 0;
        int totalRequired = 0;

        for (StageDef def : SHORT_DRAMA_STAGES) {
            // uploaded projects skip story_seed; it's satisfied by upload
            if (isUploaded && "story_seed".equals(def.key)) {
                stages.add(new StageView(def.key, def.label, "completed", def.required,
                        List.of(), null, null));
                completedCount++;
                if (def.required) totalRequired++;
                continue;
            }

            // import_review: only for uploaded
            if ("import_review".equals(def.key) && !isUploaded) {
                continue;
            }

            // storyboard: optional, can be skipped
            if ("storyboard".equals(def.key) && storyboardSkipped) {
                stages.add(new StageView(def.key, def.label, "skipped", false,
                        List.of(), null, null));
                continue;
            }

            String status = resolveStatus(def.key, lastStage, facts);
            if ("completed".equals(status)) {
                completedCount++;
                if (def.required) totalRequired++;
            } else if ("current".equals(status)) {
                if (def.required) totalRequired++;
            } else if ("pending".equals(status)) {
                if (def.required) totalRequired++;
            }

            String primaryAction = buildPrimaryAction(def.key, "current".equals(status) ? "current" : status);
            String route = buildRoute(def.key, project.getId());

            stages.add(new StageView(def.key, def.label, status, def.required,
                    status.equals("pending") ? List.of("previous_stage_not_completed") : List.of(),
                    primaryAction, route));
        }

        // If no stage is "current", advance to first pending stage
        StageView current = stages.stream()
                .filter(s -> "current".equals(s.status()))
                .findFirst()
                .orElse(null);

        if (current == null) {
            // find first pending stage and make it current
            for (int i = 0; i < stages.size(); i++) {
                if ("pending".equals(stages.get(i).status())) {
                    StageView old = stages.get(i);
                    stages.set(i, new StageView(old.key(), old.label(), "current", old.required(),
                            List.of(), buildPrimaryAction(old.key(), "current"), buildRoute(old.key(), project.getId())));
                    current = stages.get(i);
                    break;
                }
            }
        }

        // progress: when last current stage is destination and storyboard skipped → 100%
        int progress;
        if (current != null && "destination".equals(current.key()) && storyboardSkipped) {
            progress = 100;
        } else {
            // count current stage as completed for progress if it's the last stage
            int effectiveCompleted = completedCount;
            if (current != null && totalRequired > 0) {
                effectiveCompleted = completedCount + 1;
            }
            progress = totalRequired > 0 ? Math.min(100, effectiveCompleted * 100 / totalRequired) : 0;
        }

        return new WorkflowView(
                current != null ? current.key() : null,
                current != null ? current.primaryAction() : null,
                progress,
                stages);
    }

    private String resolveStatus(String key, String lastStage, Map<String, Boolean> facts) {
        Boolean factCompleted = facts.get(key);
        if (factCompleted != null && factCompleted) return "completed";
        if (key.equals(lastStage)) return "current";
        if (lastStage != null && stageOrder(lastStage) > stageOrder(key)) return "completed";
        return "pending";
    }

    private int stageOrder(String key) {
        for (int i = 0; i < SHORT_DRAMA_STAGES.size(); i++) {
            if (SHORT_DRAMA_STAGES.get(i).key.equals(key)) return i;
        }
        return -1;
    }

    private String buildPrimaryAction(String key, String status) {
        if (!"current".equals(status)) return null;
        return switch (key) {
            case "story_seed" -> "输入故事种子";
            case "import_review" -> "审核导入内容";
            case "characters" -> "生成角色设定";
            case "synopsis" -> "生成梗概";
            case "outline" -> "生成大纲";
            case "content" -> "生成正文";
            case "review" -> "提交审核";
            case "destination" -> "选择去向";
            case "storyboard" -> "制作分镜";
            default -> "继续";
        };
    }

    private String buildRoute(String key, Long projectId) {
        return "/content-projects/" + projectId + "/workspace?stage=" + key;
    }

    // ===== Parameter Versions =====

    @Transactional
    public ParameterVersionView appendParameters(Long userId, Long projectId, AppendParameterRequest request) {
        ContentProject project = projectMapper.selectById(projectId);
        if (project == null || project.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND);
        }

        // optimistic lock
        int currentRevision = project.getRevision();
        if (request.revision() != null && !request.revision().equals(currentRevision)) {
            throw new BizException(ErrorCode.EDIT_CONFLICT);
        }

        // canonicalize JSON with sorted keys
        String payloadJson;
        try {
            // parse and re-serialize for canonical ordering
            Map<String, Object> parsed = objectMapper.readValue(
                    objectMapper.writeValueAsString(request.payload()),
                    new TypeReference<LinkedHashMap<String, Object>>() {});
            // sort keys
            TreeMap<String, Object> sorted = new TreeMap<>(parsed);
            payloadJson = objectMapper.writeValueAsString(sorted);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "参数JSON格式无效");
        }
        String hash = sha256(payloadJson);

        // calculate next version_no
        List<ProjectParameterVersion> existing = parameterVersionMapper.selectList(
                new LambdaQueryWrapper<ProjectParameterVersion>()
                        .eq(ProjectParameterVersion::getProjectId, projectId)
                        .orderByDesc(ProjectParameterVersion::getVersionNo)
                        .last("limit 1"));
        int nextVersion = existing.isEmpty() ? 1 : existing.get(0).getVersionNo() + 1;

        ProjectParameterVersion pv = new ProjectParameterVersion();
        pv.setProjectId(projectId);
        pv.setVersionNo(nextVersion);
        pv.setPayloadJson(payloadJson);
        pv.setContentHash(hash);
        pv.setCreatedBy(userId);
        parameterVersionMapper.insert(pv);

        // update project pointer and revision
        ProjectParameterVersion update = new ProjectParameterVersion();
        update.setId(projectId); // reuse entity for LambdaUpdateWrapper...
        // Use direct update
        project.setCurrentParameterVersionId(pv.getId());
        project.setRevision(currentRevision + 1);
        projectMapper.updateById(project);

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            payload = Map.of();
        }
        return new ParameterVersionView(pv.getId(), pv.getVersionNo(), payload, pv.getContentHash(),
                pv.getCreatedBy(), pv.getCreatedAt());
    }

    public List<ParameterVersionView> listParameterVersions(Long projectId) {
        return parameterVersionMapper.selectList(
                        new LambdaQueryWrapper<ProjectParameterVersion>()
                                .eq(ProjectParameterVersion::getProjectId, projectId)
                                .orderByDesc(ProjectParameterVersion::getVersionNo))
                .stream()
                .map(pv -> {
                    Map<String, Object> payload;
                    try {
                        payload = objectMapper.readValue(pv.getPayloadJson(),
                                new TypeReference<Map<String, Object>>() {});
                    } catch (JsonProcessingException e) {
                        payload = Map.of();
                    }
                    return new ParameterVersionView(pv.getId(), pv.getVersionNo(), payload,
                            pv.getContentHash(), pv.getCreatedBy(), pv.getCreatedAt());
                })
                .toList();
    }

    // ===== Storyboard Intent =====

    @Transactional
    public void setStoryboardIntent(Long projectId, StoryboardIntentRequest request) {
        ContentProject project = projectMapper.selectById(projectId);
        if (project == null || project.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND);
        }

        String intent = request.intent();
        if (!"skipped".equals(intent) && !"requested".equals(intent)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "storyboard intent 仅支持 skipped 或 requested");
        }

        project.setStoryboardIntentStatus(intent);
        projectMapper.updateById(project);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    record StageDef(String key, String label, boolean required) {}
}
