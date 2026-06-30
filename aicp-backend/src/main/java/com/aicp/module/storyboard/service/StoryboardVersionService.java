package com.aicp.module.storyboard.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.storyboard.domain.StoryboardEnums.*;
import com.aicp.module.storyboard.domain.StoryboardStateMachine;
import com.aicp.module.storyboard.dto.StoryboardViews.*;
import com.aicp.module.storyboard.entity.*;
import com.aicp.module.storyboard.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryboardVersionService {

    private final StoryboardMapper storyboardMapper;
    private final StoryboardVersionMapper versionMapper;
    private final StoryboardSceneMapper sceneMapper;
    private final StoryboardVersionShotMapper shotMapper;
    private final StoryboardJobMapper jobMapper;
    private final StoryboardAuditLogMapper auditLogMapper;
    private final StoryboardAccessService accessService;

    // ===== Version CRUD =====

    public List<VersionSummary> listVersions(Long projectId, Long storyboardId, Long userId) {
        Storyboard sb = accessService.requireStoryboard(projectId, storyboardId, userId, Action.VIEW);
        List<StoryboardVersion> versions = versionMapper.selectList(
                new LambdaQueryWrapper<StoryboardVersion>()
                        .eq(StoryboardVersion::getStoryboardId, sb.getId())
                        .orderByDesc(StoryboardVersion::getTier)
                        .orderByDesc(StoryboardVersion::getVersionNo));
        return versions.stream().map(this::toVersionSummary).toList();
    }

    public VersionDetail getVersion(Long projectId, Long versionId, Long userId) {
        StoryboardVersion version = accessService.requireVersion(projectId, versionId, userId, Action.VIEW);
        return toVersionDetail(version);
    }

    public VersionDiff getDiff(Long projectId, Long versionId, Long againstVersionId, Long userId) {
        accessService.requireVersion(projectId, versionId, userId, Action.VIEW);
        accessService.requireVersion(projectId, againstVersionId, userId, Action.VIEW);

        List<StoryboardScene> scenes1 = sceneMapper.selectList(
                new LambdaQueryWrapper<StoryboardScene>().eq(StoryboardScene::getVersionId, versionId));
        List<StoryboardScene> scenes2 = sceneMapper.selectList(
                new LambdaQueryWrapper<StoryboardScene>().eq(StoryboardScene::getVersionId, againstVersionId));
        List<StoryboardShot> shots1 = shotMapper.selectList(
                new LambdaQueryWrapper<StoryboardShot>().eq(StoryboardShot::getVersionId, versionId));
        List<StoryboardShot> shots2 = shotMapper.selectList(
                new LambdaQueryWrapper<StoryboardShot>().eq(StoryboardShot::getVersionId, againstVersionId));

        List<FieldDiff> sceneDiffs = computeSceneDiffs(scenes1, scenes2);
        List<FieldDiff> shotDiffs = computeShotDiffs(shots1, shots2);

        return new VersionDiff(versionId, againstVersionId, sceneDiffs, shotDiffs);
    }

    // ===== State Transitions =====

    @Transactional
    public VersionDetail submitForReview(Long projectId, Long versionId, Long userId, int expectedRevision) {
        StoryboardVersion version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version, expectedRevision);
        StoryboardStateMachine.requireTransition(
                VersionStatus.valueOf(version.getStatus().toUpperCase()), VersionStatus.REVIEWING);

        bumpRevision(version, expectedRevision);
        version.setStatus(VersionStatus.REVIEWING.value());
        versionMapper.updateById(version);
        writeAudit(versionId, userId, "submit_review", "version", versionId, null);

        return toVersionDetail(version);
    }

    @Transactional
    public VersionDetail lockVersion(Long projectId, Long versionId, Long userId,
                                      int expectedRevision, String idempotencyKey) {
        StoryboardVersion version = accessService.requireVersion(projectId, versionId, userId, Action.PRODUCE);
        requireEditable(version, expectedRevision);

        VersionStatus fromStatus = VersionStatus.valueOf(version.getStatus().toUpperCase());
        StoryboardStateMachine.requireTransition(fromStatus, VersionStatus.LOCKED);

        // Idempotency: if already locked, return success
        if (version.getStatus().equalsIgnoreCase("locked")) {
            return toVersionDetail(version);
        }

        // Reserve idempotency key
        StoryboardJob existing = jobMapper.selectOne(
                new LambdaQueryWrapper<StoryboardJob>()
                        .eq(StoryboardJob::getProjectId, projectId)
                        .eq(StoryboardJob::getJobType, "lock")
                        .eq(StoryboardJob::getIdempotencyKey, idempotencyKey));
        if (existing != null && "succeeded".equals(existing.getStatus())) {
            return toVersionDetail(version);
        }

        bumpRevision(version, expectedRevision);
        version.setStatus(VersionStatus.LOCKED.value());
        version.setLockedBy(userId);
        version.setLockedAt(LocalDateTime.now());
        versionMapper.updateById(version);

        // Update storyboard pointer
        Storyboard sb = storyboardMapper.selectById(version.getStoryboardId());
        sb.setCurrentLockedVersionId(versionId);
        if (sb.getCurrentDraftVersionId() != null && sb.getCurrentDraftVersionId().equals(versionId)) {
            sb.setCurrentDraftVersionId(null);
        }
        storyboardMapper.updateById(sb);

        writeJob(projectId, sb.getId(), versionId, "lock", idempotencyKey, "succeeded", userId);
        writeAudit(versionId, userId, "lock", "version", versionId, null);

        return toVersionDetail(version);
    }

    @Transactional
    public VersionDetail forkVersion(Long projectId, Long versionId, Long userId, String idempotencyKey) {
        StoryboardVersion parent = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);

        if (!StoryboardStateMachine.isLocked(VersionStatus.valueOf(parent.getStatus().toUpperCase()))) {
            throw new BizException(ErrorCode.STORYBOARD_VERSION_LOCKED, "只能从已锁定版本派生新草稿");
        }

        // Check idempotency
        StoryboardJob existing = jobMapper.selectOne(
                new LambdaQueryWrapper<StoryboardJob>()
                        .eq(StoryboardJob::getProjectId, projectId)
                        .eq(StoryboardJob::getJobType, "fork")
                        .eq(StoryboardJob::getIdempotencyKey, idempotencyKey));
        if (existing != null && "succeeded".equals(existing.getStatus())) {
            Long childId = extractChildVersionId(existing);
            if (childId != null) {
                StoryboardVersion child = versionMapper.selectById(childId);
                if (child != null) return toVersionDetail(child);
            }
        }

        StoryboardVersion child = createDerivedDraft(projectId, parent, userId, parent.getTier(), idempotencyKey);
        writeJob(projectId, parent.getStoryboardId(), child.getId(), "fork", idempotencyKey, "succeeded", userId);
        writeAudit(child.getId(), userId, "fork", "version", parent.getId(),
                "parent=" + parent.getId());

        return toVersionDetail(child);
    }

    @Transactional
    public VersionDetail upgradeVersion(Long projectId, Long versionId, Long userId,
                                         String targetTier, String idempotencyKey) {
        StoryboardVersion parent = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);

        if (!StoryboardStateMachine.isLocked(VersionStatus.valueOf(parent.getStatus().toUpperCase()))) {
            throw new BizException(ErrorCode.STORYBOARD_VERSION_LOCKED, "只能从已锁定版本升档");
        }

        Tier from = Tier.valueOf(parent.getTier());
        Tier to = Tier.valueOf(targetTier);
        StoryboardStateMachine.requireTierUpgrade(from, to);

        // Check idempotency
        StoryboardJob existing = jobMapper.selectOne(
                new LambdaQueryWrapper<StoryboardJob>()
                        .eq(StoryboardJob::getProjectId, projectId)
                        .eq(StoryboardJob::getJobType, "upgrade")
                        .eq(StoryboardJob::getIdempotencyKey, idempotencyKey));
        if (existing != null && "succeeded".equals(existing.getStatus())) {
            Long childId = extractChildVersionId(existing);
            if (childId != null) {
                StoryboardVersion child = versionMapper.selectById(childId);
                if (child != null) return toVersionDetail(child);
            }
        }

        StoryboardVersion child = createDerivedDraft(projectId, parent, userId, targetTier, idempotencyKey);
        writeJob(projectId, parent.getStoryboardId(), child.getId(), "upgrade", idempotencyKey, "succeeded", userId);
        writeAudit(child.getId(), userId, "upgrade", "version", parent.getId(),
                "from=" + parent.getTier() + " to=" + targetTier);

        return toVersionDetail(child);
    }

    // ===== Internal =====

    @Transactional
    protected StoryboardVersion createDerivedDraft(Long projectId, StoryboardVersion parent,
                                                    Long userId, String targetTier, String idempotencyKey) {
        Storyboard sb = storyboardMapper.selectById(parent.getStoryboardId());

        // Lock storyboard row to prevent concurrent version number allocation
        Storyboard locked = storyboardMapper.selectById(sb.getId());
        if (locked == null) throw new BizException(ErrorCode.STORYBOARD_NOT_FOUND);

        // Determine version number
        int maxVersionNo = versionMapper.selectList(
                new LambdaQueryWrapper<StoryboardVersion>()
                        .eq(StoryboardVersion::getStoryboardId, sb.getId())
                        .eq(StoryboardVersion::getTier, targetTier))
                .stream()
                .mapToInt(StoryboardVersion::getVersionNo)
                .max()
                .orElse(0);

        // Create child version
        StoryboardVersion child = new StoryboardVersion();
        child.setUuid(UUID.randomUUID().toString());
        child.setStoryboardId(sb.getId());
        child.setParentVersionId(parent.getId());
        child.setSourceContentVersionId(parent.getSourceContentVersionId());
        child.setTier(targetTier);
        child.setVersionNo(maxVersionNo + 1);
        child.setStatus(VersionStatus.DRAFT.value());
        child.setRevision(0);
        child.setSchemaVersion(parent.getSchemaVersion());
        child.setCreatedFrom(targetTier.equals(parent.getTier()) ? CreatedFrom.FORK.value() : CreatedFrom.UPGRADE.value());
        child.setCreatedBy(userId);
        versionMapper.insert(child);

        // Copy scenes
        List<StoryboardScene> parentScenes = sceneMapper.selectList(
                new LambdaQueryWrapper<StoryboardScene>()
                        .eq(StoryboardScene::getVersionId, parent.getId())
                        .orderByAsc(StoryboardScene::getSortOrder));
        Map<Long, Long> sceneIdMap = new HashMap<>();
        for (StoryboardScene ps : parentScenes) {
            StoryboardScene childScene = new StoryboardScene();
            childScene.setVersionId(child.getId());
            childScene.setSceneKey(ps.getSceneKey());
            childScene.setSceneNo(ps.getSceneNo());
            childScene.setTitle(ps.getTitle());
            childScene.setDramaticGoal(ps.getDramaticGoal());
            childScene.setBeatDescription(ps.getBeatDescription());
            childScene.setLocationRefId(ps.getLocationRefId());
            childScene.setDurationMs(ps.getDurationMs());
            childScene.setEmotionLabel(ps.getEmotionLabel());
            childScene.setEmotionIntensity(ps.getEmotionIntensity());
            childScene.setSortOrder(ps.getSortOrder());
            sceneMapper.insert(childScene);
            sceneIdMap.put(ps.getId(), childScene.getId());
        }

        // Copy shots
        List<StoryboardShot> parentShots = shotMapper.selectList(
                new LambdaQueryWrapper<StoryboardShot>()
                        .eq(StoryboardShot::getVersionId, parent.getId())
                        .orderByAsc(StoryboardShot::getSortOrder));
        int shotCount = 0;
        long totalDuration = 0;
        for (StoryboardShot ps : parentShots) {
            StoryboardShot childShot = new StoryboardShot();
            childShot.setUuid(UUID.randomUUID().toString());
            childShot.setVersionId(child.getId());
            childShot.setSceneId(sceneIdMap.get(ps.getSceneId()));
            childShot.setShotKey(ps.getShotKey());
            childShot.setShotCode(ps.getShotCode());
            childShot.setDurationMs(ps.getDurationMs());
            childShot.setShotSize(ps.getShotSize());
            childShot.setVisualDescription(ps.getVisualDescription());
            childShot.setLightingAtmosphere(ps.getLightingAtmosphere());
            childShot.setCharacterAction(ps.getCharacterAction());
            childShot.setEmotionDescription(ps.getEmotionDescription());
            childShot.setDialogueText(ps.getDialogueText());
            childShot.setSceneTagsJson(ps.getSceneTagsJson());
            childShot.setSoundEffect(ps.getSoundEffect());
            childShot.setReferenceText(ps.getReferenceText());
            childShot.setImagePrompt(ps.getImagePrompt());
            childShot.setVideoMotionPrompt(ps.getVideoMotionPrompt());
            childShot.setStatus(ps.getStatus());
            childShot.setSortOrder(ps.getSortOrder());

            // Clear target-tier AI fields
            if ("B".equals(targetTier)) {
                childShot.setDirectorIntention(null);
                childShot.setActionMotivation(null);
                childShot.setRelationshipBlocking(null);
                childShot.setInformationGap(null);
                childShot.setAudioVisualRelation(null);
                childShot.setEditPoint(null);
            }
            if ("C".equals(targetTier)) {
                childShot.setDubText(null);
                childShot.setSubtitleText(null);
                childShot.setFailureStrategy(null);
            }

            shotMapper.insert(childShot);
            shotCount++;
            totalDuration += (ps.getDurationMs() != null ? ps.getDurationMs() : 0);
        }

        // Update version stats
        child.setTotalScenes(parentScenes.size());
        child.setTotalShots(shotCount);
        child.setTotalDurationMs(totalDuration);
        versionMapper.updateById(child);

        // Update storyboard draft pointer
        sb.setCurrentDraftVersionId(child.getId());
        storyboardMapper.updateById(sb);

        return child;
    }

    private void requireEditable(StoryboardVersion version, int expectedRevision) {
        if (StoryboardStateMachine.isLocked(VersionStatus.valueOf(version.getStatus().toUpperCase()))) {
            throw new BizException(ErrorCode.STORYBOARD_VERSION_LOCKED);
        }
        if (!Objects.equals(version.getRevision(), expectedRevision)) {
            throw new BizException(ErrorCode.STORYBOARD_REVISION_CONFLICT);
        }
    }

    void bumpRevision(StoryboardVersion version, int expectedRevision) {
        int updated = versionMapper.update(null,
                new LambdaUpdateWrapper<StoryboardVersion>()
                        .eq(StoryboardVersion::getId, version.getId())
                        .eq(StoryboardVersion::getRevision, expectedRevision)
                        .set(StoryboardVersion::getRevision, expectedRevision + 1)
                        .set(StoryboardVersion::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new BizException(ErrorCode.STORYBOARD_REVISION_CONFLICT);
        }
        version.setRevision(expectedRevision + 1);
    }

    private void writeAudit(Long versionId, Long userId, String actionType,
                             String targetType, Long targetId, String detail) {
        StoryboardAuditLog log = new StoryboardAuditLog();
        log.setVersionId(versionId);
        log.setActorUserId(userId);
        log.setActionType(actionType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setOperationId(UUID.randomUUID().toString());
        auditLogMapper.insert(log);
    }

    private void writeJob(Long projectId, Long storyboardId, Long versionId,
                           String jobType, String idempotencyKey, String status, Long userId) {
        StoryboardJob job = new StoryboardJob();
        job.setUuid(UUID.randomUUID().toString());
        job.setProjectId(projectId);
        job.setStoryboardId(storyboardId);
        job.setVersionId(versionId);
        job.setJobType(jobType);
        job.setStatus(status);
        job.setIdempotencyKey(idempotencyKey);
        job.setCreatedBy(userId);
        job.setStartedAt(LocalDateTime.now());
        job.setFinishedAt(LocalDateTime.now());
        jobMapper.insert(job);
    }

    private Long extractChildVersionId(StoryboardJob job) {
        if (job.getResultJson() != null && job.getResultJson().contains("childVersionId")) {
            try {
                int idx = job.getResultJson().indexOf("\"childVersionId\":");
                if (idx >= 0) {
                    String sub = job.getResultJson().substring(idx + 17);
                    int end = sub.indexOf(",");
                    if (end < 0) end = sub.indexOf("}");
                    if (end > 0) return Long.parseLong(sub.substring(0, end).trim());
                }
            } catch (Exception e) {
                log.warn("Failed to extract childVersionId from job result", e);
            }
        }
        return job.getVersionId();
    }

    // ===== Mappers =====

    private VersionSummary toVersionSummary(StoryboardVersion v) {
        return new VersionSummary(v.getId(), v.getUuid(), v.getStoryboardId(),
                v.getParentVersionId(), v.getTier(), v.getVersionNo(), v.getStatus(),
                v.getRevision(), v.getTotalScenes(), v.getTotalShots(), v.getTotalDurationMs(),
                v.getCreatedFrom(), v.getLockedBy(), v.getLockedAt(), v.getCreatedAt());
    }

    private VersionDetail toVersionDetail(StoryboardVersion v) {
        return new VersionDetail(v.getId(), v.getUuid(), v.getStoryboardId(),
                v.getParentVersionId(), v.getSourceContentVersionId(), v.getTier(),
                v.getVersionNo(), v.getStatus(), v.getRevision(), v.getSchemaVersion(),
                v.getTotalScenes(), v.getTotalShots(), v.getTotalDurationMs(),
                v.getCreatedFrom(), v.getLockedBy(), v.getLockedAt(), v.getCreatedBy(),
                v.getCreatedAt(), v.getUpdatedAt());
    }

    private List<FieldDiff> computeSceneDiffs(List<StoryboardScene> a, List<StoryboardScene> b) {
        Map<String, StoryboardScene> bMap = new HashMap<>();
        for (StoryboardScene s : b) bMap.put(s.getSceneKey(), s);
        List<FieldDiff> diffs = new ArrayList<>();
        for (StoryboardScene sa : a) {
            StoryboardScene sb = bMap.get(sa.getSceneKey());
            if (sb == null) continue;
            diffField(diffs, "scene", sa.getId(), "title", sa.getTitle(), sb.getTitle());
            diffField(diffs, "scene", sa.getId(), "dramaticGoal", sa.getDramaticGoal(), sb.getDramaticGoal());
        }
        return diffs;
    }

    private List<FieldDiff> computeShotDiffs(List<StoryboardShot> a, List<StoryboardShot> b) {
        Map<String, StoryboardShot> bMap = new HashMap<>();
        for (StoryboardShot s : b) bMap.put(s.getShotKey(), s);
        List<FieldDiff> diffs = new ArrayList<>();
        for (StoryboardShot sa : a) {
            StoryboardShot sb = bMap.get(sa.getShotKey());
            if (sb == null) continue;
            diffField(diffs, "shot", sa.getId(), "visualDescription", sa.getVisualDescription(), sb.getVisualDescription());
            diffField(diffs, "shot", sa.getId(), "dialogueText", sa.getDialogueText(), sb.getDialogueText());
            diffField(diffs, "shot", sa.getId(), "durationMs", sa.getDurationMs(), sb.getDurationMs());
        }
        return diffs;
    }

    private void diffField(List<FieldDiff> diffs, String entityType, Long entityId,
                            String fieldName, Object oldVal, Object newVal) {
        if (!Objects.equals(oldVal, newVal)) {
            diffs.add(new FieldDiff(entityType, entityId, fieldName, oldVal, newVal));
        }
    }
}
