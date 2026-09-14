package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.config.ScriptStageTruthProperties;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.domain.ScriptStageKey;
import com.aicp.module.contentproject.domain.ScriptStageState;
import com.aicp.module.contentproject.dto.StageCheckpointRequests.StageForkRequest;
import com.aicp.module.contentproject.dto.StageCheckpointRequests.StageTransitionRequest;
import com.aicp.module.contentproject.dto.StageCheckpointRequests.StalenessResolutionRequest;
import com.aicp.module.contentproject.dto.StageCheckpointViews.StageProjection;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentStageCheckpoint;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.entity.StoryboardHandoffSnapshot;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ContentStageCheckpointMapper;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.aicp.module.contentproject.mapper.StoryboardHandoffSnapshotMapper;
import com.aicp.module.contentproject.dto.ContentProjectRequests.GenerationJobRequest;
import com.aicp.module.contentproject.service.stage.StageGateRegistry;
import com.aicp.module.contentproject.service.stage.StageGateResult;
import com.aicp.module.contentproject.service.stage.StageStructureImpact;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ContentStageOrchestrator {

    private final ScriptStageTruthProperties truthProperties;
    private final ProjectAccessService accessService;
    private final ContentStageCheckpointService checkpointService;
    private final ContentStageCheckpointMapper checkpointMapper;
    private final ContentProjectMapper projectMapper;
    private final ContentUnitMapper contentUnitMapper;
    private final ContentVersionMapper contentVersionMapper;
    private final StoryboardHandoffSnapshotMapper handoffSnapshotMapper;
    private final ContentGenerationJobService generationJobService;
    private final StageGateRegistry gateRegistry;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final StageStructureImpact structureImpact;

    public ContentStageOrchestrator(
            ScriptStageTruthProperties truthProperties,
            ProjectAccessService accessService,
            ContentStageCheckpointService checkpointService,
            ContentStageCheckpointMapper checkpointMapper,
            ContentProjectMapper projectMapper,
            ContentUnitMapper contentUnitMapper,
            ContentVersionMapper contentVersionMapper,
            StoryboardHandoffSnapshotMapper handoffSnapshotMapper,
            ContentGenerationJobService generationJobService,
            StageGateRegistry gateRegistry,
            OutboxService outboxService,
            ObjectMapper objectMapper) {
        this.truthProperties = truthProperties;
        this.accessService = accessService;
        this.checkpointService = checkpointService;
        this.checkpointMapper = checkpointMapper;
        this.projectMapper = projectMapper;
        this.contentUnitMapper = contentUnitMapper;
        this.contentVersionMapper = contentVersionMapper;
        this.handoffSnapshotMapper = handoffSnapshotMapper;
        this.generationJobService = generationJobService;
        this.gateRegistry = gateRegistry;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.structureImpact = new StageStructureImpact(objectMapper);
    }

    @Transactional
    public StageProjection transition(Long userId, Long projectId, StageTransitionRequest request) {
        requireTruthEnabled();
        accessService.require(projectId, userId, Action.EDIT_CONTENT);

        ContentProject project = checkpointService.requireProject(projectId);
        checkpointService.ensureEight(project, userId);

        ScriptStageKey sourceKey = ScriptStageKey.parse(request.sourceStageKey());
        boolean handoff = "COMPLETE_HANDOFF".equalsIgnoreCase(request.action())
                || request.targetStageKey() == null
                || request.targetStageKey().isBlank();
        ScriptStageKey targetKey = handoff ? null : ScriptStageKey.parse(request.targetStageKey());

        if (!handoff) {
            if (targetKey.order() != sourceKey.order() + 1) {
                throw new BizException(ErrorCode.STAGE_TRANSITION_INVALID, "只能推进到相邻下一阶段");
            }
        } else if (sourceKey != ScriptStageKey.TEXT_STORYBOARD) {
            throw new BizException(ErrorCode.STAGE_TRANSITION_INVALID, "仅文字分镜阶段可 COMPLETE_HANDOFF");
        }

        if (request.projectRevision() != null && !request.projectRevision().equals(project.getRevision())) {
            throw new BizException(ErrorCode.STAGE_TRANSITION_INVALID, "项目 revision 已变化");
        }

        ContentStageCheckpoint source = checkpointService.requireCheckpoint(projectId, sourceKey);
        if (request.sourceCheckpointRevision() != null
                && !request.sourceCheckpointRevision().equals(source.getRevision())) {
            throw new BizException(ErrorCode.STAGE_REVISION_CONFLICT);
        }

        ContentStageCheckpoint target = null;
        if (targetKey != null) {
            target = checkpointService.requireCheckpoint(projectId, targetKey);
            if (request.targetCheckpointRevision() != null
                    && !request.targetCheckpointRevision().equals(target.getRevision())) {
                throw new BizException(ErrorCode.STAGE_REVISION_CONFLICT);
            }
        }

        if (request.contentUnitId() != null && request.contentUnitRevision() != null) {
            ContentUnit unit = contentUnitMapper.selectById(request.contentUnitId());
            if (unit == null || !projectId.equals(unit.getProjectId())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "content_unit 不属于该项目");
            }
            if (!request.contentUnitRevision().equals(unit.getRevision())) {
                ContentVersion draft = contentVersionMapper.selectOne(
                        new LambdaQueryWrapper<ContentVersion>()
                                .eq(ContentVersion::getContentUnitId, unit.getId())
                                .eq(ContentVersion::getStatus, "draft")
                                .last("limit 1"));
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("code", "EDIT_CONFLICT");
                details.put("server_revision", unit.getRevision());
                details.put("server_draft_id", draft == null ? null : draft.getId());
                details.put("base_revision", request.contentUnitRevision());
                details.put("server_content_hash", draft == null ? null : draft.getContentHash());
                details.put("comparison_url",
                        "/api/v1/content-units/" + unit.getId() + "/conflicts?base_revision="
                                + request.contentUnitRevision());
                throw new BizException(ErrorCode.EDIT_CONFLICT, ErrorCode.EDIT_CONFLICT.getMessage(), details);
            }
        }

        ScriptStageState sourceState = ScriptStageState.parse(source.getState());
        if (sourceState != ScriptStageState.IN_PROGRESS && sourceState != ScriptStageState.BLOCKED) {
            throw new BizException(ErrorCode.STAGE_TRANSITION_INVALID, "来源阶段状态不允许流转: " + sourceState);
        }

        StageGateResult gate = gateRegistry.evaluate(sourceKey, project);
        List<String> acks = request.warningAcknowledgements() == null
                ? List.of()
                : request.warningAcknowledgements();
        source.setGateResultJson(toJson(gate.toJsonMap()));

        if (gate.blocked()) {
            source.setState(ScriptStageState.BLOCKED.name());
            bumpRevision(source, userId);
            checkpointMapper.updateById(source);
            outboxService.append("GateBlocked", projectId, project.getRevision() == null ? 0 : project.getRevision(),
                    Map.of(
                            "projectId", projectId,
                            "stageKey", sourceKey.value(),
                            "blockers", gate.blockers(),
                            "warnings", gate.warnings()));
            throw new BizException(ErrorCode.STAGE_GATE_BLOCKED, "门禁存在阻断项", gateDetails(gate));
        }

        if (!gate.warnings().isEmpty()) {
            for (Map<String, Object> warning : gate.warnings()) {
                Object code = warning.get("code");
                if (code != null && !acks.contains(String.valueOf(code))) {
                    throw new BizException(ErrorCode.STAGE_GATE_BLOCKED, "存在未确认警告: " + code, gateDetails(gate));
                }
            }
        }

        Map<String, Object> snapshot = buildInputSnapshot(project, sourceKey);
        source.setInputSnapshotJson(toJson(snapshot));
        source.setInputSnapshotHash(structureImpact.canonicalSha256(snapshot));

        Long previousAdopted = source.getAdoptedContentVersionId();
        Long adoptedVersionId = adoptStageArtifact(userId, project, sourceKey, source);
        if (adoptedVersionId != null && previousAdopted != null
                && !adoptedVersionId.equals(previousAdopted)) {
            applyStructureImpactAfterAdopt(userId, projectId, sourceKey, previousAdopted, adoptedVersionId);
        }

        source.setState(ScriptStageState.COMPLETED.name());
        source.setCompletedAt(LocalDateTime.now());
        bumpRevision(source, userId);
        checkpointMapper.updateById(source);

        String resumeKey = sourceKey.value();
        if (target != null) {
            ScriptStageState targetState = ScriptStageState.parse(target.getState());
            if (targetState == ScriptStageState.NOT_STARTED
                    || targetState == ScriptStageState.POSSIBLY_STALE
                    || targetState == ScriptStageState.REGEN_REQUIRED) {
                target.setState(ScriptStageState.IN_PROGRESS.name());
                bumpRevision(target, userId);
                checkpointMapper.updateById(target);
            }
            resumeKey = targetKey.value();
        }

        Long handoffId = null;
        Long reviewedScriptBodyVersionId = null;
        if (handoff) {
            StoryboardHandoffSnapshot handoffSnapshot = createHandoffSnapshot(userId, project, source);
            handoffId = handoffSnapshot.getId();
            reviewedScriptBodyVersionId = handoffSnapshot.getReviewedScriptBodyVersionId();
            source.setPrimaryArtifactType("STORYBOARD_HANDOFF");
            source.setPrimaryArtifactId(handoffId);
            bumpRevision(source, userId);
            checkpointMapper.updateById(source);
        }

        project.setLastStageKey(resumeKey);
        project.setRevision((project.getRevision() == null ? 0 : project.getRevision()) + 1);
        projectMapper.updateById(project);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId);
        payload.put("stageKey", sourceKey.value());
        payload.put("adoptedVersionId", source.getAdoptedContentVersionId());
        payload.put("inputSnapshotHash", source.getInputSnapshotHash());
        payload.put("targetStageKey", targetKey == null ? null : targetKey.value());
        if (handoffId != null) {
            payload.put("handoffSnapshotId", handoffId);
            payload.put("reviewedScriptBodyVersionId", reviewedScriptBodyVersionId);
        }
        outboxService.append("StageCompleted", projectId, project.getRevision(), payload);
        if (handoffId != null) {
            Map<String, Object> handoffPayload = new LinkedHashMap<>();
            handoffPayload.put("projectId", projectId);
            handoffPayload.put("handoffSnapshotId", handoffId);
            handoffPayload.put("reviewedScriptBodyVersionId", reviewedScriptBodyVersionId);
            handoffPayload.put("checkpointId", source.getId());
            outboxService.append("StoryboardHandoffCaptured", projectId, project.getRevision(), handoffPayload);
        }

        return checkpointService.listProjection(userId, projectId);
    }

    @Transactional
    public StageProjection fork(Long userId, Long projectId, String stageKeyRaw, StageForkRequest request) {
        requireTruthEnabled();
        accessService.require(projectId, userId, Action.EDIT_CONTENT);

        ContentProject project = checkpointService.requireProject(projectId);
        checkpointService.ensureEight(project, userId);
        ScriptStageKey stageKey = ScriptStageKey.parse(stageKeyRaw);
        ContentStageCheckpoint checkpoint = checkpointService.requireCheckpoint(projectId, stageKey);

        if (request != null && request.projectRevision() != null
                && !request.projectRevision().equals(project.getRevision())) {
            throw new BizException(ErrorCode.STAGE_TRANSITION_INVALID, "项目 revision 已变化");
        }
        if (request != null && request.checkpointRevision() != null
                && !request.checkpointRevision().equals(checkpoint.getRevision())) {
            throw new BizException(ErrorCode.STAGE_REVISION_CONFLICT);
        }

        ScriptStageState state = ScriptStageState.parse(checkpoint.getState());
        if (state != ScriptStageState.COMPLETED && state != ScriptStageState.LOCKED) {
            if (state == ScriptStageState.IN_PROGRESS || state == ScriptStageState.BLOCKED) {
                return checkpointService.listProjection(userId, projectId);
            }
            throw new BizException(ErrorCode.STAGE_TRANSITION_INVALID, "仅 COMPLETED/LOCKED 阶段可 Fork");
        }

        Long baseVersionId = request != null && request.baseAdoptedContentVersionId() != null
                ? request.baseAdoptedContentVersionId()
                : checkpoint.getAdoptedContentVersionId();
        if (baseVersionId != null && checkpoint.getAdoptedContentVersionId() != null
                && !baseVersionId.equals(checkpoint.getAdoptedContentVersionId())) {
            throw new BizException(ErrorCode.STAGE_REVISION_CONFLICT, "基准采用版本已变化");
        }

        boolean wasLocked = state == ScriptStageState.LOCKED;
        Long draftId = createDraftFromAdopted(userId, projectId, stageKey, baseVersionId);
        String causeForkId = UUID.randomUUID().toString();

        checkpoint.setState(ScriptStageState.IN_PROGRESS.name());
        bumpRevision(checkpoint, userId);
        checkpointMapper.updateById(checkpoint);

        List<String> marked = markDownstreamPossiblyStale(
                projectId, stageKey, causeForkId, baseVersionId, draftId, wasLocked, userId);

        project.setRevision((project.getRevision() == null ? 0 : project.getRevision()) + 1);
        projectMapper.updateById(project);

        Map<String, Object> forkPayload = new LinkedHashMap<>();
        forkPayload.put("projectId", projectId);
        forkPayload.put("stageKey", stageKey.value());
        forkPayload.put("baseVersionId", baseVersionId);
        forkPayload.put("newDraftId", draftId);
        forkPayload.put("causeForkId", causeForkId);
        forkPayload.put("wasLocked", wasLocked);
        outboxService.append("StageForked", projectId, project.getRevision(), forkPayload);

        if (!marked.isEmpty()) {
            outboxService.append("StageMarkedStale", projectId, project.getRevision(), Map.of(
                    "projectId", projectId,
                    "triggerStage", stageKey.value(),
                    "causeForkId", causeForkId,
                    "downstreamStages", marked,
                    "impactLevel", "POSSIBLY_STALE"));
        }

        return checkpointService.listProjection(userId, projectId);
    }

    @Transactional
    public StageProjection resolveStaleness(
            Long userId, Long projectId, String stageKeyRaw, StalenessResolutionRequest request) {
        requireTruthEnabled();
        accessService.require(projectId, userId, Action.EDIT_CONTENT);

        ContentProject project = checkpointService.requireProject(projectId);
        checkpointService.ensureEight(project, userId);
        ScriptStageKey stageKey = ScriptStageKey.parse(stageKeyRaw);
        ContentStageCheckpoint checkpoint = checkpointService.requireCheckpoint(projectId, stageKey);

        if (request.checkpointRevision() != null
                && !request.checkpointRevision().equals(checkpoint.getRevision())) {
            throw new BizException(ErrorCode.STAGE_REVISION_CONFLICT);
        }

        String action = request.action() == null ? "" : request.action().trim().toUpperCase();
        List<Map<String, Object>> reasons = parseReasons(checkpoint.getStaleReasonJson());
        String causeForkId = reasonFilterCauseForkId(request.reasonFilter());

        switch (action) {
            case "CONFIRM_CURRENT" -> {
                markReasonsResolved(reasons, causeForkId, causeForkId != null && !causeForkId.isBlank());
                checkpoint.setStaleReasonJson(toJson(reasons));
                if (activeReasons(reasons).isEmpty()) {
                    checkpoint.setState(wasLocked(reasons)
                            ? ScriptStageState.LOCKED.name()
                            : ScriptStageState.COMPLETED.name());
                } else {
                    checkpoint.setState(ScriptStageState.POSSIBLY_STALE.name());
                }
            }
            case "CREATE_DRAFT" -> {
                createDraftFromAdopted(userId, projectId, stageKey, checkpoint.getAdoptedContentVersionId());
                checkpoint.setState(ScriptStageState.IN_PROGRESS.name());
            }
            case "REGENERATE" -> {
                Long draftId = createDraftFromAdopted(
                        userId, projectId, stageKey, checkpoint.getAdoptedContentVersionId());
                checkpoint.setState(ScriptStageState.IN_PROGRESS.name());
                startRegenJob(userId, projectId, stageKey, draftId);
            }
            case "DISCARD_FORK" -> {
                if (causeForkId == null || causeForkId.isBlank()) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "DISCARD_FORK 需要 reason_filter.cause_fork_id");
                }
                boolean matched = reasons.stream()
                        .anyMatch(r -> causeForkId.equals(String.valueOf(r.get("causeForkId"))));
                if (!matched) {
                    throw new BizException(ErrorCode.STALENESS_REASON_MISMATCH);
                }
                reasons.removeIf(r -> causeForkId.equals(String.valueOf(r.get("causeForkId"))));
                checkpoint.setStaleReasonJson(toJson(reasons));
                if (activeReasons(reasons).isEmpty()) {
                    checkpoint.setState(wasLocked(reasons)
                            ? ScriptStageState.LOCKED.name()
                            : ScriptStageState.COMPLETED.name());
                }
            }
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "未知过期处理动作: " + action);
        }

        bumpRevision(checkpoint, userId);
        checkpointMapper.updateById(checkpoint);
        project.setRevision((project.getRevision() == null ? 0 : project.getRevision()) + 1);
        projectMapper.updateById(project);

        outboxService.append("StalenessResolved", projectId, project.getRevision(), Map.of(
                "projectId", projectId,
                "stageKey", stageKey.value(),
                "action", action,
                "operatorId", userId,
                "note", request.note() == null ? "" : request.note()));

        return checkpointService.listProjection(userId, projectId);
    }

    /**
     * 审核通过后写入 adopted；lock=true 时进入 LOCKED。
     */
    @Transactional
    public void lockStageAfterApproval(Long userId, Long projectId, ScriptStageKey stageKey, Long adoptedVersionId) {
        adoptAndOptionallyLock(userId, projectId, stageKey, adoptedVersionId, true);
    }

    @Transactional
    public void adoptAndOptionallyLock(
            Long userId, Long projectId, ScriptStageKey stageKey, Long adoptedVersionId, boolean lock) {
        if (!truthProperties.isStageTruthEnabled()) {
            return;
        }
        ContentProject project = checkpointService.requireProject(projectId);
        checkpointService.ensureEight(project, userId);
        ContentStageCheckpoint checkpoint = checkpointService.requireCheckpoint(projectId, stageKey);
        Long previousAdopted = checkpoint.getAdoptedContentVersionId();
        if (adoptedVersionId != null) {
            checkpoint.setAdoptedContentVersionId(adoptedVersionId);
            checkpoint.setPrimaryArtifactType("CONTENT_VERSION");
            checkpoint.setPrimaryArtifactId(adoptedVersionId);
        }
        checkpoint.setState(lock ? ScriptStageState.LOCKED.name() : ScriptStageState.COMPLETED.name());
        checkpoint.setCompletedAt(LocalDateTime.now());
        bumpRevision(checkpoint, userId);
        checkpointMapper.updateById(checkpoint);
        project.setRevision((project.getRevision() == null ? 0 : project.getRevision()) + 1);
        projectMapper.updateById(project);
        if (adoptedVersionId != null && previousAdopted != null
                && !adoptedVersionId.equals(previousAdopted)) {
            applyStructureImpactAfterAdopt(userId, projectId, stageKey, previousAdopted, adoptedVersionId);
        }
        outboxService.append("StageCompleted", projectId, project.getRevision(), Map.of(
                "projectId", projectId,
                "stageKey", stageKey.value(),
                "adoptedVersionId", adoptedVersionId == null ? 0L : adoptedVersionId,
                "locked", lock));
    }

    private StoryboardHandoffSnapshot createHandoffSnapshot(
            Long userId, ContentProject project, ContentStageCheckpoint textStoryboard) {
        Long reviewedVersionId = resolveReviewedScriptBodyVersionId(project.getId());
        if (reviewedVersionId == null) {
            throw new BizException(ErrorCode.ARTIFACT_NOT_PERSISTED, "缺少审核通过的剧本正文版本，无法交接");
        }
        ContentUnit storyboardUnit = contentUnitMapper.selectOne(
                new LambdaQueryWrapper<ContentUnit>()
                        .eq(ContentUnit::getProjectId, project.getId())
                        .eq(ContentUnit::getUnitType, ScriptStageKey.TEXT_STORYBOARD.value())
                        .eq(ContentUnit::getIsDeleted, 0)
                        .last("limit 1"));
        int sceneCount = 0;
        String continuity = "UNKNOWN";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("reviewedScriptBodyVersionId", reviewedVersionId);
        if (storyboardUnit != null) {
            ContentVersion draft = contentVersionMapper.selectOne(
                    new LambdaQueryWrapper<ContentVersion>()
                            .eq(ContentVersion::getContentUnitId, storyboardUnit.getId())
                            .eq(ContentVersion::getStatus, "draft")
                            .last("limit 1"));
            ContentVersion source = draft;
            if (source == null && storyboardUnit.getCurrentVersionId() != null) {
                source = contentVersionMapper.selectById(storyboardUnit.getCurrentVersionId());
            }
            if (source != null && source.getContentJson() != null) {
                payload.put("storyboardContentHash", source.getContentHash());
                sceneCount = estimateSceneCount(source.getContentJson());
                continuity = resolveContinuityResult(source.getContentJson(), sceneCount);
            }
        }
        if (!"PASS".equalsIgnoreCase(continuity)) {
            throw new BizException(
                    ErrorCode.STAGE_GATE_BLOCKED,
                    "文字分镜连续性检查未通过或场景为空",
                    Map.of(
                            "blockers", List.of(StageGateResult.issue(
                                    "CONTINUITY_CHECK_REQUIRED",
                                    "文字分镜连续性检查未通过或场景为空")),
                            "warnings", List.of(),
                            "evidence", Map.of("continuityCheckResult", continuity, "sceneCount", sceneCount)));
        }
        payload.put("sceneCount", sceneCount);
        payload.put("continuityCheckResult", continuity);
        payload.put("capturedAt", LocalDateTime.now().toString());

        StoryboardHandoffSnapshot snapshot = new StoryboardHandoffSnapshot();
        snapshot.setProjectId(project.getId());
        snapshot.setCheckpointId(textStoryboard.getId());
        snapshot.setReviewedScriptBodyVersionId(reviewedVersionId);
        snapshot.setContinuityCheckResult(continuity);
        snapshot.setSceneCount(sceneCount);
        snapshot.setPayloadJson(toJson(payload));
        snapshot.setContentHash(structureImpact.canonicalSha256(payload));
        snapshot.setCreatedBy(userId);
        snapshot.setCapturedAt(LocalDateTime.now());
        handoffSnapshotMapper.insert(snapshot);
        return snapshot;
    }

    private Long resolveReviewedScriptBodyVersionId(Long projectId) {
        ContentStageCheckpoint review = checkpointMapper.selectOne(
                new LambdaQueryWrapper<ContentStageCheckpoint>()
                        .eq(ContentStageCheckpoint::getProjectId, projectId)
                        .eq(ContentStageCheckpoint::getStageKey, ScriptStageKey.REVIEW_REVISION.value()));
        if (review != null && review.getAdoptedContentVersionId() != null
                && isApprovedOrLockedVersion(review.getAdoptedContentVersionId())) {
            return review.getAdoptedContentVersionId();
        }
        ContentUnit scriptBody = contentUnitMapper.selectOne(
                new LambdaQueryWrapper<ContentUnit>()
                        .eq(ContentUnit::getProjectId, projectId)
                        .eq(ContentUnit::getUnitType, ScriptStageKey.SCRIPT_BODY.value())
                        .eq(ContentUnit::getIsDeleted, 0)
                        .last("limit 1"));
        if (scriptBody == null) {
            return null;
        }
        ContentVersion approved = contentVersionMapper.selectOne(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, scriptBody.getId())
                        .in(ContentVersion::getStatus, List.of("approved", "locked", "accepted"))
                        .orderByDesc(ContentVersion::getVersionNo)
                        .last("limit 1"));
        return approved != null ? approved.getId() : null;
    }

    private boolean isApprovedOrLockedVersion(Long versionId) {
        ContentVersion version = contentVersionMapper.selectById(versionId);
        if (version == null || version.getStatus() == null) {
            return false;
        }
        String status = version.getStatus().trim().toLowerCase();
        return "approved".equals(status) || "locked".equals(status) || "accepted".equals(status);
    }

    private int estimateSceneCount(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return 0;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(contentJson, new TypeReference<>() {});
            Object shots = map.get("shots");
            if (shots instanceof List<?> list) {
                return list.size();
            }
            Object episodes = map.get("episodes");
            if (episodes instanceof List<?> eps) {
                int count = 0;
                for (Object ep : eps) {
                    if (ep instanceof Map<?, ?> m && m.get("scenes") instanceof List<?> scenes) {
                        count += scenes.size();
                    }
                }
                return count;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return contentJson.contains("scene") || contentJson.contains("shot") ? 1 : 0;
    }

    private void startRegenJob(Long userId, Long projectId, ScriptStageKey stageKey, Long draftId) {
        ContentUnit unit = contentUnitMapper.selectOne(
                new LambdaQueryWrapper<ContentUnit>()
                        .eq(ContentUnit::getProjectId, projectId)
                        .eq(ContentUnit::getUnitType, stageKey.value())
                        .eq(ContentUnit::getIsDeleted, 0)
                        .last("limit 1"));
        if (unit == null) {
            log.warn("REGENERATE skipped: no content unit for project={} stage={}", projectId, stageKey.value());
            return;
        }
        ContentStageCheckpoint checkpoint = checkpointService.requireCheckpoint(projectId, stageKey);
        Map<String, Object> strategy = new LinkedHashMap<>();
        strategy.put("mode", "stage_regen");
        strategy.put("stageKey", stageKey.value());
        strategy.put("draftId", draftId);
        strategy.put("allow_unconfirmed_bible", true);
        if (checkpoint.getInputSnapshotJson() != null && !checkpoint.getInputSnapshotJson().isBlank()) {
            try {
                strategy.put("inputSnapshot", objectMapper.readValue(
                        checkpoint.getInputSnapshotJson(), new TypeReference<Map<String, Object>>() {}));
            } catch (Exception e) {
                strategy.put("inputSnapshotRaw", checkpoint.getInputSnapshotJson());
            }
        }
        if (checkpoint.getInputSnapshotHash() != null) {
            strategy.put("inputSnapshotHash", checkpoint.getInputSnapshotHash());
        }
        GenerationJobRequest request = new GenerationJobRequest(
                stageKey.value() + "_generate",
                "content_unit",
                unit.getId(),
                Map.of(),
                toJson(strategy),
                "v1-stage-regen");
        String idempotencyKey = "stage-regen-" + projectId + "-" + stageKey.value() + "-" + UUID.randomUUID();
        generationJobService.createJob(userId, projectId, request, idempotencyKey);
        log.info("Started regen job for project={} stage={} draftId={} inputHash={}",
                projectId, stageKey.value(), draftId, checkpoint.getInputSnapshotHash());
    }

    private List<String> markDownstreamPossiblyStale(
            Long projectId,
            ScriptStageKey trigger,
            String causeForkId,
            Long baseVersionId,
            Long draftId,
            boolean triggerWasLocked,
            Long userId) {
        List<String> marked = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (ScriptStageKey key : ScriptStageKey.ordered()) {
            if (key.order() <= trigger.order()) {
                continue;
            }
            ContentStageCheckpoint downstream = checkpointService.requireCheckpoint(projectId, key);
            ScriptStageState state = ScriptStageState.parse(downstream.getState());
            if (state == ScriptStageState.NOT_STARTED) {
                continue;
            }
            List<Map<String, Object>> reasons = parseReasons(downstream.getStaleReasonJson());
            Map<String, Object> reason = new LinkedHashMap<>();
            reason.put("triggerStage", trigger.value());
            reason.put("causeForkId", causeForkId);
            reason.put("impactLevel", "POSSIBLY_STALE");
            reason.put("status", "OPEN");
            reason.put("baseVersionId", baseVersionId);
            reason.put("newDraftId", draftId);
            reason.put("wasLocked", state == ScriptStageState.LOCKED || triggerWasLocked);
            reason.put("capturedAt", now.toString());
            reasons.add(reason);
            downstream.setStaleReasonJson(toJson(reasons));
            if (state == ScriptStageState.COMPLETED || state == ScriptStageState.LOCKED
                    || state == ScriptStageState.IN_PROGRESS) {
                downstream.setState(ScriptStageState.POSSIBLY_STALE.name());
            }
            bumpRevision(downstream, userId);
            checkpointMapper.updateById(downstream);
            marked.add(key.value());
        }
        return marked;
    }

    private Long createDraftFromAdopted(Long userId, Long projectId, ScriptStageKey stageKey, Long baseVersionId) {
        ContentUnit unit = contentUnitMapper.selectOne(
                new LambdaQueryWrapper<ContentUnit>()
                        .eq(ContentUnit::getProjectId, projectId)
                        .eq(ContentUnit::getUnitType, stageKey.value())
                        .eq(ContentUnit::getIsDeleted, 0)
                        .last("limit 1"));
        if (unit == null) {
            return null;
        }

        ContentVersion source = null;
        if (baseVersionId != null) {
            source = contentVersionMapper.selectById(baseVersionId);
        }
        if (source == null && unit.getCurrentVersionId() != null) {
            source = contentVersionMapper.selectById(unit.getCurrentVersionId());
        }

        ContentVersion draft = contentVersionMapper.selectOne(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unit.getId())
                        .eq(ContentVersion::getStatus, "draft")
                        .eq(ContentVersion::getSource, "manual_edit")
                        .last("limit 1"));

        String contentJson = source != null && source.getContentJson() != null ? source.getContentJson() : "{}";
        String plainText = source != null && source.getPlainText() != null ? source.getPlainText() : "";
        String hash = source != null ? source.getContentHash() : null;

        if (draft == null) {
            draft = new ContentVersion();
            draft.setProjectId(projectId);
            draft.setContentUnitId(unit.getId());
            draft.setVersionNo(0);
            draft.setStatus("draft");
            draft.setSource("manual_edit");
            draft.setContentJson(contentJson);
            draft.setPlainText(plainText);
            draft.setContentHash(hash);
            draft.setCreatedBy(userId);
            contentVersionMapper.insert(draft);
        } else {
            draft.setContentJson(contentJson);
            draft.setPlainText(plainText);
            draft.setContentHash(hash);
            contentVersionMapper.updateById(draft);
        }
        return draft.getId();
    }

    private void markReasonsResolved(List<Map<String, Object>> reasons, String causeForkId, boolean requireMatch) {
        boolean matched = false;
        for (Map<String, Object> reason : reasons) {
            if (causeForkId == null || causeForkId.isBlank()
                    || causeForkId.equals(String.valueOf(reason.get("causeForkId")))) {
                reason.put("status", "RESOLVED");
                matched = true;
            }
        }
        if (requireMatch && !matched) {
            throw new BizException(ErrorCode.STALENESS_REASON_MISMATCH);
        }
    }

    private List<Map<String, Object>> activeReasons(List<Map<String, Object>> reasons) {
        List<Map<String, Object>> active = new ArrayList<>();
        for (Map<String, Object> reason : reasons) {
            if (!"RESOLVED".equals(String.valueOf(reason.get("status")))) {
                active.add(reason);
            }
        }
        return active;
    }

    private boolean wasLocked(List<Map<String, Object>> reasons) {
        for (Map<String, Object> reason : reasons) {
            Object value = reason.get("wasLocked");
            if (Boolean.TRUE.equals(value) || "true".equals(String.valueOf(value))) {
                return true;
            }
        }
        return false;
    }

    private String reasonFilterCauseForkId(Map<String, Object> filter) {
        if (filter == null) {
            return null;
        }
        Object value = filter.get("cause_fork_id");
        if (value == null) {
            value = filter.get("causeForkId");
        }
        return value == null ? null : String.valueOf(value);
    }

    private List<Map<String, Object>> parseReasons(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(json, new TypeReference<>() {}));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void requireTruthEnabled() {
        if (!truthProperties.isStageTruthEnabled()) {
            throw new BizException(ErrorCode.SCRIPT_STAGE_TRUTH_DISABLED);
        }
    }

    private void bumpRevision(ContentStageCheckpoint row, Long userId) {
        row.setRevision((row.getRevision() == null ? 0 : row.getRevision()) + 1);
        row.setUpdatedBy(userId);
    }

    private Map<String, Object> gateDetails(StageGateResult gate) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("blockers", gate.blockers());
        details.put("warnings", gate.warnings());
        details.put("evidence", gate.evidence());
        return details;
    }

    private Map<String, Object> buildInputSnapshot(ContentProject project, ScriptStageKey stageKey) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("projectParameterVersionId", project.getCurrentParameterVersionId());
        snapshot.put("stageKey", stageKey.value());
        snapshot.put("capturedAt", LocalDateTime.now().toString());

        List<Map<String, Object>> upstream = new ArrayList<>();
        List<Long> settingVersions = new ArrayList<>();
        if (project.getCurrentParameterVersionId() != null) {
            settingVersions.add(project.getCurrentParameterVersionId());
        }
        for (ScriptStageKey key : ScriptStageKey.ordered()) {
            if (key.order() >= stageKey.order()) {
                break;
            }
            ContentStageCheckpoint cp = checkpointMapper.selectOne(
                    new LambdaQueryWrapper<ContentStageCheckpoint>()
                            .eq(ContentStageCheckpoint::getProjectId, project.getId())
                            .eq(ContentStageCheckpoint::getStageKey, key.value())
                            .last("limit 1"));
            if (cp == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stageKey", key.value());
            row.put("checkpointRevision", cp.getRevision());
            row.put("artifactType", cp.getPrimaryArtifactType());
            row.put("artifactId", cp.getPrimaryArtifactId());
            row.put("adoptedContentVersionId", cp.getAdoptedContentVersionId());
            String contentHash = null;
            if (cp.getAdoptedContentVersionId() != null) {
                ContentVersion adopted = contentVersionMapper.selectById(cp.getAdoptedContentVersionId());
                if (adopted != null) {
                    contentHash = adopted.getContentHash();
                }
            }
            row.put("contentHash", contentHash);
            upstream.add(row);
        }
        snapshot.put("upstreamStages", upstream);
        snapshot.put("settingVersions", settingVersions);
        return snapshot;
    }

    /**
     * 门禁通过后：从草稿固化不可变采用版本，并写入检查点成果字段。
     */
    private Long adoptStageArtifact(
            Long userId, ContentProject project, ScriptStageKey stageKey, ContentStageCheckpoint source) {
        if (stageKey == ScriptStageKey.CREATION_SETTINGS) {
            source.setPrimaryArtifactType("PARAMETER_VERSION");
            source.setPrimaryArtifactId(project.getCurrentParameterVersionId());
            if (source.getAdoptedContentVersionId() == null) {
                // 创作设置无 ContentVersion 成果
            }
            return null;
        }

        ContentUnit unit = contentUnitMapper.selectOne(
                new LambdaQueryWrapper<ContentUnit>()
                        .eq(ContentUnit::getProjectId, project.getId())
                        .eq(ContentUnit::getUnitType, stageKey.value())
                        .eq(ContentUnit::getIsDeleted, 0)
                        .last("limit 1"));
        if (unit == null) {
            return source.getAdoptedContentVersionId();
        }

        ContentVersion draft = contentVersionMapper.selectOne(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unit.getId())
                        .eq(ContentVersion::getStatus, "draft")
                        .last("limit 1"));
        String contentJson = draft != null && draft.getContentJson() != null ? draft.getContentJson() : "{}";
        String plainText = draft != null && draft.getPlainText() != null ? draft.getPlainText() : "";
        String hash = draft != null && draft.getContentHash() != null
                ? draft.getContentHash()
                : structureImpact.canonicalSha256(contentJson);

        // 若当前已有采用版本且内容哈希未变，复用，避免重复版本
        if (source.getAdoptedContentVersionId() != null) {
            ContentVersion existing = contentVersionMapper.selectById(source.getAdoptedContentVersionId());
            if (existing != null && hash.equals(existing.getContentHash())) {
                source.setPrimaryArtifactType("CONTENT_VERSION");
                source.setPrimaryArtifactId(existing.getId());
                unit.setCurrentVersionId(existing.getId());
                contentUnitMapper.updateById(unit);
                return existing.getId();
            }
        }

        List<ContentVersion> numbered = contentVersionMapper.selectList(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unit.getId())
                        .gt(ContentVersion::getVersionNo, 0)
                        .orderByDesc(ContentVersion::getVersionNo)
                        .last("limit 1"));
        int nextVersion = numbered.isEmpty() ? 1 : numbered.get(0).getVersionNo() + 1;

        ContentVersion adopted = new ContentVersion();
        adopted.setProjectId(project.getId());
        adopted.setContentUnitId(unit.getId());
        adopted.setVersionNo(nextVersion);
        adopted.setStatus(stageKey == ScriptStageKey.REVIEW_REVISION ? "approved" : "accepted");
        adopted.setContentJson(contentJson);
        adopted.setPlainText(plainText);
        adopted.setSource("stage_transition");
        adopted.setContentHash(hash);
        adopted.setCreatedBy(userId);
        contentVersionMapper.insert(adopted);

        unit.setCurrentVersionId(adopted.getId());
        contentUnitMapper.updateById(unit);

        source.setAdoptedContentVersionId(adopted.getId());
        source.setPrimaryArtifactType("CONTENT_VERSION");
        source.setPrimaryArtifactId(adopted.getId());

        if (stageKey == ScriptStageKey.NOVEL_UPLOAD) {
            try {
                Map<String, Object> payload = objectMapper.readValue(contentJson, new TypeReference<>() {});
                Object uploadId = payload.get("uploadId");
                if (uploadId == null) {
                    uploadId = payload.get("upload_id");
                }
                if (uploadId != null) {
                    source.setPrimaryArtifactType("UPLOAD_IMPORT");
                    source.setPrimaryArtifactId(Long.valueOf(String.valueOf(uploadId)));
                }
            } catch (Exception ignored) {
                // keep CONTENT_VERSION
            }
        }
        return adopted.getId();
    }

    private void applyStructureImpactAfterAdopt(
            Long userId, Long projectId, ScriptStageKey trigger, Long oldVersionId, Long newVersionId) {
        ContentVersion oldVersion = contentVersionMapper.selectById(oldVersionId);
        ContentVersion newVersion = contentVersionMapper.selectById(newVersionId);
        if (oldVersion == null || newVersion == null) {
            return;
        }
        StageStructureImpact.Delta delta = structureImpact.compare(
                oldVersion.getContentJson(), oldVersion.getContentHash(),
                newVersion.getContentJson(), newVersion.getContentHash());
        if (delta.level() == StageStructureImpact.Level.NONE
                || delta.level() == StageStructureImpact.Level.TEXT_ONLY) {
            return;
        }

        String causeForkId = "adopt-structure-" + newVersionId;
        List<String> upgraded = new ArrayList<>();
        for (ScriptStageKey key : ScriptStageKey.ordered()) {
            if (key.order() <= trigger.order()) {
                continue;
            }
            ContentStageCheckpoint downstream = checkpointService.requireCheckpoint(projectId, key);
            ScriptStageState state = ScriptStageState.parse(downstream.getState());
            if (state == ScriptStageState.NOT_STARTED) {
                continue;
            }
            List<Map<String, Object>> reasons = parseReasons(downstream.getStaleReasonJson());
            Map<String, Object> reason = new LinkedHashMap<>();
            reason.put("triggerStage", trigger.value());
            reason.put("causeForkId", causeForkId);
            reason.put("impactLevel", "REGEN_REQUIRED");
            reason.put("status", "OPEN");
            reason.put("oldVersionId", oldVersionId);
            reason.put("newVersionId", newVersionId);
            reason.put("structuralHashBefore", delta.before().structuralHash());
            reason.put("structuralHashAfter", delta.after().structuralHash());
            reason.put("capturedAt", LocalDateTime.now().toString());
            reasons.add(reason);
            downstream.setStaleReasonJson(toJson(reasons));
            downstream.setState(ScriptStageState.REGEN_REQUIRED.name());
            bumpRevision(downstream, userId);
            checkpointMapper.updateById(downstream);
            upgraded.add(key.value());
        }
        if (!upgraded.isEmpty()) {
            outboxService.append("StageMarkedStale", projectId, 0, Map.of(
                    "projectId", projectId,
                    "triggerStage", trigger.value(),
                    "causeForkId", causeForkId,
                    "downstreamStages", upgraded,
                    "impactLevel", "REGEN_REQUIRED"));
        }
    }

    @SuppressWarnings("unchecked")
    private String resolveContinuityResult(String contentJson, int sceneCount) {
        if (sceneCount <= 0) {
            return "FAIL";
        }
        try {
            Map<String, Object> map = objectMapper.readValue(contentJson, new TypeReference<>() {});
            Object direct = map.get("continuityCheckResult");
            if (direct == null) {
                direct = map.get("continuity_check_result");
            }
            if (direct != null && "PASS".equalsIgnoreCase(String.valueOf(direct))) {
                return "PASS";
            }
            Object continuity = map.get("continuity");
            if (continuity instanceof Map<?, ?> c) {
                if (Boolean.TRUE.equals(c.get("passed"))) {
                    return "PASS";
                }
                Object status = c.get("status");
                if (status != null && "PASSED".equalsIgnoreCase(String.valueOf(status))) {
                    return "PASS";
                }
                if (status != null && ("FAILED".equalsIgnoreCase(String.valueOf(status))
                        || "FAIL".equalsIgnoreCase(String.valueOf(status)))) {
                    return "FAIL";
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        // 无显式结果时：有场景则保守 PASS（与门禁草稿约定一致需用户先跑连续性）
        return "FAIL";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return value instanceof List ? "[]" : "{}";
        }
    }
}
