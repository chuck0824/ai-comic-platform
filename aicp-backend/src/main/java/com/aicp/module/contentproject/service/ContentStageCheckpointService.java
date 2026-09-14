package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.config.ScriptStageTruthProperties;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.domain.ScriptStageKey;
import com.aicp.module.contentproject.domain.ScriptStageState;
import com.aicp.module.contentproject.dto.ContentProjectViews.StageView;
import com.aicp.module.contentproject.dto.ContentProjectViews.WorkflowView;
import com.aicp.module.contentproject.dto.StageCheckpointViews.GateView;
import com.aicp.module.contentproject.dto.StageCheckpointViews.StageCheckpointView;
import com.aicp.module.contentproject.dto.StageCheckpointViews.StageProjection;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentStageCheckpoint;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ContentStageCheckpointMapper;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.aicp.module.contentproject.service.stage.StageGateRegistry;
import com.aicp.module.contentproject.service.stage.StageGateResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 阶段检查点投影与历史迁移（§11）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentStageCheckpointService {

    private static final Set<String> ADOPTED_STATUSES = Set.of("accepted", "approved", "locked");

    private final ContentStageCheckpointMapper checkpointMapper;
    private final ContentProjectMapper projectMapper;
    private final ContentUnitMapper contentUnitMapper;
    private final ContentVersionMapper contentVersionMapper;
    private final ProjectAccessService accessService;
    private final StageGateRegistry gateRegistry;
    private final ScriptStageTruthProperties truthProperties;
    private final ObjectMapper objectMapper;

    @Transactional
    public StageProjection listProjection(Long userId, Long projectId) {
        accessService.require(projectId, userId, Action.VIEW);
        ContentProject project = requireProject(projectId);
        ensureEight(project, userId);
        List<ContentStageCheckpoint> rows = checkpointMapper.selectList(
                new LambdaQueryWrapper<ContentStageCheckpoint>()
                        .eq(ContentStageCheckpoint::getProjectId, projectId)
                        .orderByAsc(ContentStageCheckpoint::getId));
        Map<String, ContentStageCheckpoint> byKey = rows.stream()
                .collect(Collectors.toMap(ContentStageCheckpoint::getStageKey, r -> r, (a, b) -> a, LinkedHashMap::new));

        List<StageCheckpointView> stages = new ArrayList<>();
        for (ScriptStageKey key : ScriptStageKey.ordered()) {
            ContentStageCheckpoint row = byKey.get(key.value());
            if (row == null) {
                continue;
            }
            stages.add(toView(row));
        }
        return new StageProjection(
                project.getId(),
                project.getRevision(),
                stages,
                project.getLastStageKey(),
                truthProperties.isStageTruthEnabled());
    }

    public GateView previewGate(Long userId, Long projectId, String stageKeyRaw) {
        accessService.require(projectId, userId, Action.VIEW);
        ContentProject project = requireProject(projectId);
        ScriptStageKey stageKey = ScriptStageKey.parse(stageKeyRaw);
        StageGateResult result = gateRegistry.evaluate(stageKey, project);
        return new GateView(stageKey.value(), result.blockers(), result.warnings(), result.evidence());
    }

    /**
     * 列表/详情共用：将检查点投影转为旧 WorkflowView 形状（状态映射到 completed/current/pending）。
     */
    @Transactional
    public WorkflowView workflowFromCheckpoints(Long userId, Long projectId) {
        StageProjection projection = listProjection(userId, projectId);
        return toWorkflowView(projection);
    }

    public WorkflowView toWorkflowView(StageProjection projection) {
        String resumeKey = projection.lastStageKey();
        List<StageCheckpointView> rows = projection.stages() == null ? List.of() : projection.stages();

        String activeCurrent = null;
        for (StageCheckpointView row : rows) {
            ScriptStageState state = ScriptStageState.parse(row.state());
            if (state == ScriptStageState.IN_PROGRESS
                    || state == ScriptStageState.BLOCKED
                    || state == ScriptStageState.POSSIBLY_STALE
                    || state == ScriptStageState.REGEN_REQUIRED) {
                if (row.stageKey() != null && row.stageKey().equals(resumeKey)) {
                    activeCurrent = row.stageKey();
                    break;
                }
                if (activeCurrent == null) {
                    activeCurrent = row.stageKey();
                }
            }
        }
        if (activeCurrent == null) {
            activeCurrent = resumeKey;
        }

        List<StageView> stages = new ArrayList<>();
        int completed = 0;
        for (StageCheckpointView row : rows) {
            ScriptStageState state = ScriptStageState.parse(row.state());
            String rail;
            if (state == ScriptStageState.COMPLETED || state == ScriptStageState.LOCKED) {
                rail = "completed";
                completed++;
            } else if (row.stageKey() != null && row.stageKey().equals(activeCurrent)) {
                rail = "current";
            } else if (state == ScriptStageState.NOT_STARTED) {
                rail = "pending";
            } else {
                rail = "pending";
            }
            ScriptStageKey key = ScriptStageKey.normalizeOrDefault(row.stageKey());
            stages.add(new StageView(
                    row.stageKey(),
                    key.label(),
                    rail,
                    true,
                    List.of(),
                    "current".equals(rail) ? "continue" : null,
                    "/script-gen/" + projection.projectId() + "/workspace?stage=" + row.stageKey()));
        }
        if (stages.stream().noneMatch(s -> "current".equals(s.status()))) {
            for (int i = 0; i < stages.size(); i++) {
                if ("pending".equals(stages.get(i).status())) {
                    StageView old = stages.get(i);
                    stages.set(i, new StageView(old.key(), old.label(), "current", old.required(),
                            List.of(), "continue", old.route()));
                    activeCurrent = old.key();
                    break;
                }
            }
        }
        int progress = stages.isEmpty() ? 0 : (int) Math.round(completed * 100.0 / stages.size());
        return new WorkflowView(activeCurrent, activeCurrent, progress, stages);
    }

    /**
     * 列表批量摘要：不强制 ensureEight；无检查点时回退 last_stage_key。
     */
    public Map<Long, ListWorkflowSummary> summarizeForProjects(List<ContentProject> projects) {
        Map<Long, ListWorkflowSummary> out = new LinkedHashMap<>();
        if (projects == null || projects.isEmpty()) {
            return out;
        }
        List<Long> ids = projects.stream().map(ContentProject::getId).toList();
        List<ContentStageCheckpoint> rows = checkpointMapper.selectList(
                new LambdaQueryWrapper<ContentStageCheckpoint>()
                        .in(ContentStageCheckpoint::getProjectId, ids));
        Map<Long, List<ContentStageCheckpoint>> byProject = rows.stream()
                .collect(Collectors.groupingBy(ContentStageCheckpoint::getProjectId));

        for (ContentProject project : projects) {
            List<ContentStageCheckpoint> cps = byProject.getOrDefault(project.getId(), List.of());
            out.put(project.getId(), summarizeOne(project, cps));
        }
        return out;
    }

    private ListWorkflowSummary summarizeOne(ContentProject project, List<ContentStageCheckpoint> cps) {
        ScriptStageKey resume = ScriptStageKey.normalizeOrDefault(project.getLastStageKey());
        if (cps.isEmpty()) {
            return new ListWorkflowSummary(
                    true,
                    resume.value(),
                    resume.label(),
                    0,
                    ScriptStageState.IN_PROGRESS.name());
        }
        Map<String, ContentStageCheckpoint> byKey = cps.stream()
                .collect(Collectors.toMap(ContentStageCheckpoint::getStageKey, r -> r, (a, b) -> a));
        int completed = 0;
        String currentKey = resume.value();
        String currentState = ScriptStageState.IN_PROGRESS.name();
        for (ScriptStageKey key : ScriptStageKey.ordered()) {
            ContentStageCheckpoint row = byKey.get(key.value());
            if (row == null) {
                continue;
            }
            ScriptStageState state = ScriptStageState.parse(row.getState());
            if (state == ScriptStageState.COMPLETED || state == ScriptStageState.LOCKED) {
                completed++;
            }
            if (state == ScriptStageState.IN_PROGRESS
                    || state == ScriptStageState.BLOCKED
                    || state == ScriptStageState.POSSIBLY_STALE
                    || state == ScriptStageState.REGEN_REQUIRED) {
                currentKey = key.value();
                currentState = state.name();
            }
        }
        int progress = (int) Math.round(completed * 100.0 / ScriptStageKey.ordered().size());
        ScriptStageKey current = ScriptStageKey.normalizeOrDefault(currentKey);
        return new ListWorkflowSummary(true, current.value(), current.label(), progress, currentState);
    }

    public record ListWorkflowSummary(
            boolean stageTruthEnabled,
            String currentStageKey,
            String currentStageLabel,
            int progress,
            String stageState
    ) {}

    @Transactional
    public void ensureEight(ContentProject project, Long userId) {
        List<ContentStageCheckpoint> existing = checkpointMapper.selectList(
                new LambdaQueryWrapper<ContentStageCheckpoint>()
                        .eq(ContentStageCheckpoint::getProjectId, project.getId()));
        Map<String, ContentStageCheckpoint> byKey = existing.stream()
                .collect(Collectors.toMap(ContentStageCheckpoint::getStageKey, r -> r, (a, b) -> a));

        boolean created = false;
        for (ScriptStageKey key : ScriptStageKey.ordered()) {
            if (byKey.containsKey(key.value())) {
                continue;
            }
            ContentStageCheckpoint row = new ContentStageCheckpoint();
            row.setProjectId(project.getId());
            row.setStageKey(key.value());
            row.setState(ScriptStageState.NOT_STARTED.name());
            row.setRevision(0);
            row.setStaleReasonJson("[]");
            row.setUpdatedBy(userId);
            checkpointMapper.insert(row);
            byKey.put(key.value(), row);
            created = true;
        }

        boolean needsEvidencePass = created || existing.isEmpty() || isConservativeOnly(byKey);
        if (needsEvidencePass) {
            MigrationStats stats = applyEvidenceMigration(project, byKey, userId);
            log.info(
                    "stage-truth migrate projectId={} createdRows={} completed={} locked={} inProgress={} possiblyStale={} notStarted={} skipped={}",
                    project.getId(), created, stats.completed, stats.locked, stats.inProgress,
                    stats.possiblyStale, stats.notStarted, stats.skipped);
        }
    }

    /**
     * 仅存在「无成果的保守态」时允许重复证据迁移，避免覆盖运行时已写入的事实。
     */
    private boolean isConservativeOnly(Map<String, ContentStageCheckpoint> byKey) {
        for (ContentStageCheckpoint row : byKey.values()) {
            if (hasArtifact(row)) {
                return false;
            }
            ScriptStageState state = ScriptStageState.parse(row.getState());
            if (state == ScriptStageState.COMPLETED
                    || state == ScriptStageState.LOCKED
                    || state == ScriptStageState.REGEN_REQUIRED
                    || state == ScriptStageState.BLOCKED) {
                return false;
            }
        }
        return true;
    }

    private MigrationStats applyEvidenceMigration(
            ContentProject project,
            Map<String, ContentStageCheckpoint> byKey,
            Long userId) {
        MigrationStats stats = new MigrationStats();
        String rawResume = project.getLastStageKey();
        ScriptStageKey resume = ScriptStageKey.normalizeOrDefault(rawResume);
        if (rawResume != null && !rawResume.isBlank() && !resume.value().equals(rawResume)) {
            project.setLastStageKey(resume.value());
            projectMapper.updateById(project);
        } else if (rawResume == null || rawResume.isBlank()) {
            project.setLastStageKey(ScriptStageKey.CREATION_SETTINGS.value());
            projectMapper.updateById(project);
            resume = ScriptStageKey.CREATION_SETTINGS;
        }

        for (ScriptStageKey key : ScriptStageKey.ordered()) {
            ContentStageCheckpoint row = byKey.get(key.value());
            if (row == null) {
                continue;
            }
            ScriptStageState current = ScriptStageState.parse(row.getState());
            if (hasArtifact(row) && (current == ScriptStageState.COMPLETED || current == ScriptStageState.LOCKED)) {
                if (current == ScriptStageState.LOCKED) {
                    stats.locked++;
                } else {
                    stats.completed++;
                }
                stats.skipped++;
                continue;
            }

            StageEvidence evidence = collectEvidence(project, key);
            ScriptStageState decided = decideState(key, resume, evidence);
            applyEvidenceToRow(row, evidence, decided, userId);
            checkpointMapper.updateById(row);
            tally(stats, decided);
        }
        return stats;
    }

    private ScriptStageState decideState(ScriptStageKey key, ScriptStageKey resume, StageEvidence evidence) {
        if (key.order() > resume.order()) {
            return ScriptStageState.NOT_STARTED;
        }
        if (key.order() == resume.order()) {
            return ScriptStageState.IN_PROGRESS;
        }
        // key < resume
        if (evidence.locked()) {
            return ScriptStageState.LOCKED;
        }
        if (evidence.adoptedVersionId() != null || evidence.primaryArtifactId() != null) {
            return ScriptStageState.COMPLETED;
        }
        if (evidence.hasDraft()) {
            // 有草稿无采用成果：按 §11 标 IN_PROGRESS；相对恢复点偏旧时仍保守 POSSIBLY_STALE
            return ScriptStageState.POSSIBLY_STALE;
        }
        return ScriptStageState.POSSIBLY_STALE;
    }

    private void applyEvidenceToRow(
            ContentStageCheckpoint row,
            StageEvidence evidence,
            ScriptStageState decided,
            Long userId) {
        row.setState(decided.name());
        row.setUpdatedBy(userId);
        if (evidence.adoptedVersionId() != null) {
            row.setAdoptedContentVersionId(evidence.adoptedVersionId());
        }
        if (evidence.primaryArtifactType() != null) {
            row.setPrimaryArtifactType(evidence.primaryArtifactType());
            row.setPrimaryArtifactId(evidence.primaryArtifactId());
        }
        if (decided == ScriptStageState.COMPLETED || decided == ScriptStageState.LOCKED) {
            if (row.getCompletedAt() == null) {
                row.setCompletedAt(LocalDateTime.now());
            }
        }
        if (decided == ScriptStageState.POSSIBLY_STALE
                && (row.getStaleReasonJson() == null || "[]".equals(row.getStaleReasonJson()))) {
            Map<String, Object> reason = new LinkedHashMap<>();
            reason.put("triggerStage", "migration");
            reason.put("causeForkId", "legacy-migrate");
            reason.put("impactLevel", "POSSIBLY_STALE");
            reason.put("status", "OPEN");
            reason.put("message", "历史迁移证据不足，保守标记");
            reason.put("capturedAt", LocalDateTime.now().toString());
            try {
                row.setStaleReasonJson(objectMapper.writeValueAsString(List.of(reason)));
            } catch (Exception e) {
                row.setStaleReasonJson("[]");
            }
        }
        row.setRevision((row.getRevision() == null ? 0 : row.getRevision()) + 1);
    }

    private StageEvidence collectEvidence(ContentProject project, ScriptStageKey key) {
        if (key == ScriptStageKey.CREATION_SETTINGS) {
            Long parameterId = project.getCurrentParameterVersionId();
            if (parameterId != null) {
                return new StageEvidence(null, "PARAMETER_VERSION", parameterId, false, false);
            }
            return StageEvidence.empty();
        }

        ContentUnit unit = contentUnitMapper.selectOne(
                new LambdaQueryWrapper<ContentUnit>()
                        .eq(ContentUnit::getProjectId, project.getId())
                        .eq(ContentUnit::getUnitType, key.value())
                        .eq(ContentUnit::getIsDeleted, 0)
                        .orderByAsc(ContentUnit::getDisplayNo)
                        .last("limit 1"));
        if (unit == null) {
            return StageEvidence.empty();
        }

        boolean hasDraft = contentVersionMapper.selectCount(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unit.getId())
                        .eq(ContentVersion::getStatus, "draft")) > 0;

        ContentVersion adopted = contentVersionMapper.selectOne(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unit.getId())
                        .in(ContentVersion::getStatus, ADOPTED_STATUSES)
                        .gt(ContentVersion::getVersionNo, 0)
                        .orderByDesc(ContentVersion::getVersionNo)
                        .last("limit 1"));
        if (adopted == null && unit.getCurrentVersionId() != null) {
            ContentVersion current = contentVersionMapper.selectById(unit.getCurrentVersionId());
            if (current != null && ADOPTED_STATUSES.contains(
                    String.valueOf(current.getStatus()).toLowerCase(Locale.ROOT))) {
                adopted = current;
            }
        }

        boolean locked = adopted != null
                && ("locked".equalsIgnoreCase(adopted.getStatus())
                || (key == ScriptStageKey.REVIEW_REVISION
                && "approved".equalsIgnoreCase(adopted.getStatus())));

        if (adopted != null) {
            return new StageEvidence(
                    adopted.getId(),
                    "CONTENT_VERSION",
                    adopted.getId(),
                    hasDraft,
                    locked);
        }
        return new StageEvidence(null, null, null, hasDraft, false);
    }

    private boolean hasArtifact(ContentStageCheckpoint row) {
        return row.getAdoptedContentVersionId() != null
                || row.getPrimaryArtifactId() != null;
    }

    private void tally(MigrationStats stats, ScriptStageState state) {
        switch (state) {
            case COMPLETED -> stats.completed++;
            case LOCKED -> stats.locked++;
            case IN_PROGRESS -> stats.inProgress++;
            case POSSIBLY_STALE -> stats.possiblyStale++;
            case NOT_STARTED -> stats.notStarted++;
            default -> stats.skipped++;
        }
    }

    public ContentStageCheckpoint requireCheckpoint(Long projectId, ScriptStageKey stageKey) {
        ContentStageCheckpoint row = checkpointMapper.selectOne(
                new LambdaQueryWrapper<ContentStageCheckpoint>()
                        .eq(ContentStageCheckpoint::getProjectId, projectId)
                        .eq(ContentStageCheckpoint::getStageKey, stageKey.value()));
        if (row == null) {
            throw new BizException(ErrorCode.STAGE_NOT_FOUND);
        }
        return row;
    }

    public ContentProject requireProject(Long projectId) {
        ContentProject project = projectMapper.selectById(projectId);
        if (project == null || Integer.valueOf(1).equals(project.getIsDeleted())) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return project;
    }

    private StageCheckpointView toView(ContentStageCheckpoint row) {
        ScriptStageState state = ScriptStageState.parse(row.getState());
        Map<String, Object> gateSummary = parseJsonMap(row.getGateResultJson());
        List<Map<String, Object>> staleReasons = parseJsonList(row.getStaleReasonJson());
        return new StageCheckpointView(
                row.getStageKey(),
                row.getState(),
                row.getRevision(),
                row.getPrimaryArtifactType(),
                row.getPrimaryArtifactId(),
                row.getAdoptedContentVersionId(),
                Map.of(
                        "blockers", gateSummary.getOrDefault("blockers", List.of()),
                        "warnings", gateSummary.getOrDefault("warnings", List.of())),
                staleReasons,
                allowedActions(state));
    }

    private List<String> allowedActions(ScriptStageState state) {
        return switch (state) {
            case NOT_STARTED -> List.of();
            case IN_PROGRESS, BLOCKED -> List.of("edit", "preview_gate", "transition");
            case COMPLETED, LOCKED -> List.of("view", "fork");
            case POSSIBLY_STALE -> List.of("view", "resolve_staleness", "fork");
            case REGEN_REQUIRED -> List.of("edit", "resolve_staleness", "regenerate");
        };
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private record StageEvidence(
            Long adoptedVersionId,
            String primaryArtifactType,
            Long primaryArtifactId,
            boolean hasDraft,
            boolean locked
    ) {
        static StageEvidence empty() {
            return new StageEvidence(null, null, null, false, false);
        }
    }

    static final class MigrationStats {
        int completed;
        int locked;
        int inProgress;
        int possiblyStale;
        int notStarted;
        int skipped;
    }
}
