package com.aicp.module.storyboard.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.service.ProjectSceneAssetService;
import com.aicp.module.storyboard.domain.StoryboardEnums.ShotStatus;
import com.aicp.module.storyboard.domain.StoryboardStateMachine;
import com.aicp.module.storyboard.dto.StoryboardRequests.*;
import com.aicp.module.storyboard.dto.StoryboardViews.*;
import com.aicp.module.storyboard.entity.*;
import com.aicp.module.storyboard.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryboardEditingService {

    private final StoryboardVersionMapper versionMapper;
    private final StoryboardSceneMapper sceneMapper;
    private final StoryboardVersionShotMapper shotMapper;
    private final StoryboardAuditLogMapper auditLogMapper;
    private final StoryboardAccessService accessService;
    private final StoryboardVersionService versionService;
    private final ProjectSceneAssetService sceneAssetService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int SUMMARY_MAX_LENGTH = 200;

    // ===== Scene CRUD =====

    public List<SceneView> listScenes(Long projectId, Long versionId, Long userId) {
        accessService.requireVersion(projectId, versionId, userId, Action.VIEW);
        List<StoryboardScene> scenes = sceneMapper.selectList(
                new LambdaQueryWrapper<StoryboardScene>()
                        .eq(StoryboardScene::getVersionId, versionId)
                        .orderByAsc(StoryboardScene::getSortOrder));
        return scenes.stream().map(s -> {
            long shotCount = shotMapper.selectCount(
                    new LambdaQueryWrapper<StoryboardShot>()
                            .eq(StoryboardShot::getVersionId, versionId)
                            .eq(StoryboardShot::getSceneId, s.getId()));
            return new SceneView(s.getId(), s.getSceneKey(), s.getSceneNo(),
                    s.getTitle(), s.getDramaticGoal(), s.getBeatDescription(),
                    s.getLocationRefId(), s.getDurationMs(), s.getEmotionLabel(),
                    s.getEmotionIntensity(), s.getSortOrder(), (int) shotCount);
        }).toList();
    }

    @Transactional
    public SceneView createScene(Long projectId, Long versionId, Long userId,
                                  CreateSceneRequest request) {
        StoryboardVersion version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);

        int maxNo = sceneMapper.selectList(
                new LambdaQueryWrapper<StoryboardScene>()
                        .eq(StoryboardScene::getVersionId, versionId))
                .stream().mapToInt(StoryboardScene::getSceneNo).max().orElse(0);
        int maxSort = sceneMapper.selectList(
                new LambdaQueryWrapper<StoryboardScene>()
                        .eq(StoryboardScene::getVersionId, versionId))
                .stream().mapToInt(StoryboardScene::getSortOrder).max().orElse(-1);

        StoryboardScene scene = new StoryboardScene();
        scene.setVersionId(versionId);
        scene.setSceneKey(UUID.randomUUID().toString());
        scene.setSceneNo(maxNo + 1);
        scene.setTitle(request.title());
        scene.setDramaticGoal(request.dramaticGoal());
        scene.setBeatDescription(request.beatDescription());
        scene.setLocationRefId(request.locationRefId());
        scene.setEmotionLabel(request.emotionLabel());
        scene.setEmotionIntensity(request.emotionIntensity());
        scene.setSortOrder(maxSort + 1);
        sceneMapper.insert(scene);

        updateVersionCounts(version);
        versionService.bumpRevision(version, version.getRevision());
        writeAudit(versionId, userId, "create_scene", "scene", scene.getId(), null);

        return new SceneView(scene.getId(), scene.getSceneKey(), scene.getSceneNo(),
                scene.getTitle(), scene.getDramaticGoal(), scene.getBeatDescription(),
                scene.getLocationRefId(), scene.getDurationMs(), scene.getEmotionLabel(),
                scene.getEmotionIntensity(), scene.getSortOrder(), 0);
    }

    @Transactional
    public SceneView patchScene(Long projectId, Long versionId, Long sceneId, Long userId,
                                 PatchSceneRequest request) {
        StoryboardVersion version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);
        StoryboardScene scene = accessService.requireScene(projectId, versionId, sceneId, userId, Action.EDIT_CONTENT);

        if (request.revision() != null) {
            versionService.bumpRevision(version, request.revision());
        }
        if (request.title() != null) scene.setTitle(request.title());
        if (request.dramaticGoal() != null) scene.setDramaticGoal(request.dramaticGoal());
        if (request.beatDescription() != null) scene.setBeatDescription(request.beatDescription());
        if (request.locationRefId() != null) scene.setLocationRefId(request.locationRefId());
        if (request.emotionLabel() != null) scene.setEmotionLabel(request.emotionLabel());
        if (request.emotionIntensity() != null) scene.setEmotionIntensity(request.emotionIntensity());
        sceneMapper.updateById(scene);

        long shotCount = shotMapper.selectCount(
                new LambdaQueryWrapper<StoryboardShot>()
                        .eq(StoryboardShot::getVersionId, versionId)
                        .eq(StoryboardShot::getSceneId, sceneId));
        return new SceneView(scene.getId(), scene.getSceneKey(), scene.getSceneNo(),
                scene.getTitle(), scene.getDramaticGoal(), scene.getBeatDescription(),
                scene.getLocationRefId(), scene.getDurationMs(), scene.getEmotionLabel(),
                scene.getEmotionIntensity(), scene.getSortOrder(), (int) shotCount);
    }

    @Transactional
    public void deleteScene(Long projectId, Long versionId, Long sceneId, Long userId) {
        StoryboardVersion version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);
        StoryboardScene scene = accessService.requireScene(projectId, versionId, sceneId, userId, Action.EDIT_CONTENT);

        // Delete all shots in scene
        List<StoryboardShot> shots = shotMapper.selectList(
                new LambdaQueryWrapper<StoryboardShot>()
                        .eq(StoryboardShot::getVersionId, versionId)
                        .eq(StoryboardShot::getSceneId, sceneId));
        for (StoryboardShot shot : shots) {
            shotMapper.deleteById(shot.getId());
        }

        sceneMapper.deleteById(sceneId);
        regenerateSceneNumbers(versionId);
        updateVersionCounts(version);
        versionService.bumpRevision(version, version.getRevision());
        writeAudit(versionId, userId, "delete_scene", "scene", sceneId, null);
    }

    @Transactional
    public void reorderScenes(Long projectId, Long versionId, Long userId,
                               ReorderScenesRequest request) {
        StoryboardVersion version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);
        versionService.bumpRevision(version, request.revision());

        for (ReorderItem item : request.items()) {
            StoryboardScene scene = sceneMapper.selectById(item.id());
            if (scene != null && scene.getVersionId().equals(versionId)) {
                scene.setSortOrder(item.sortOrder());
                sceneMapper.updateById(scene);
            }
        }
        regenerateSceneNumbers(versionId);
        writeAudit(versionId, userId, "reorder_scenes", "version", versionId, null);
    }

    // ===== Shot CRUD =====

    public List<ShotSummary> listShots(Long projectId, Long versionId, Long userId,
                                        Long sceneId, String status, int page, int size) {
        accessService.requireVersion(projectId, versionId, userId, Action.VIEW);
        var qw = new LambdaQueryWrapper<StoryboardShot>()
                .eq(StoryboardShot::getVersionId, versionId);
        if (sceneId != null) qw.eq(StoryboardShot::getSceneId, sceneId);
        if (status != null) qw.eq(StoryboardShot::getStatus, status);

        // Pagination using MyBatis-Plus Page
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<StoryboardShot> mpPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        mpPage = shotMapper.selectPage(mpPage, qw.orderByAsc(StoryboardShot::getSortOrder));

        return mpPage.getRecords().stream().map(s -> new ShotSummary(
                s.getId(), s.getUuid(), s.getVersionId(), s.getSceneId(),
                s.getShotKey(), s.getShotCode(), s.getDurationMs(), s.getShotSize(),
                summarize(s.getVisualDescription()), s.getDialogueText(),
                s.getStatus(), s.getSortOrder(), s.getSceneAssetId(), s.getSceneAssetVersionId(),
                s.getSceneVariantId(), s.getSceneVariantVersion(), parseJsonMap(s.getSceneAssetSnapshot()))).toList();
    }

    public ShotDetail getShotDetail(Long projectId, Long versionId, Long shotId, Long userId) {
        StoryboardShot shot = accessService.requireShot(projectId, versionId, shotId, userId, Action.VIEW);
        return toShotDetail(shot);
    }

    @Transactional
    public ShotDetail createShot(Long projectId, Long versionId, Long userId,
                                  CreateShotRequest request) {
        StoryboardVersion version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);
        StoryboardScene scene = accessService.requireScene(projectId, versionId, request.sceneId(), userId, Action.EDIT_CONTENT);

        int maxSort = shotMapper.selectList(
                new LambdaQueryWrapper<StoryboardShot>()
                        .eq(StoryboardShot::getVersionId, versionId)
                        .eq(StoryboardShot::getSceneId, request.sceneId()))
                .stream().mapToInt(StoryboardShot::getSortOrder).max().orElse(-1);

        StoryboardShot shot = new StoryboardShot();
        shot.setUuid(UUID.randomUUID().toString());
        shot.setVersionId(versionId);
        shot.setSceneId(request.sceneId());
        shot.setShotKey(UUID.randomUUID().toString());
        shot.setDurationMs(request.durationMs() != null ? request.durationMs() : 3000L);
        shot.setShotSize(request.shotSize());
        shot.setVisualDescription(request.visualDescription());
        shot.setLightingAtmosphere(request.lightingAtmosphere());
        shot.setCharacterAction(request.characterAction());
        shot.setEmotionDescription(request.emotionDescription());
        shot.setDialogueText(request.dialogueText());
        shot.setSceneTagsJson(toJson(request.sceneTags()));
        shot.setSoundEffect(request.soundEffect());
        shot.setReferenceText(request.referenceText());
        shot.setImagePrompt(request.imagePrompt());
        shot.setVideoMotionPrompt(request.videoMotionPrompt());
        shot.setStatus(ShotStatus.DRAFT.value());
        shot.setSortOrder(maxSort + 1);
        shotMapper.insert(shot);

        regenerateShotCodes(versionId);
        updateVersionCounts(version);
        versionService.bumpRevision(version, version.getRevision());
        writeAudit(versionId, userId, "create_shot", "shot", shot.getId(), null);

        return toShotDetail(shot);
    }

    @Transactional
    public ShotDetail patchShot(Long projectId, Long versionId, Long shotId, Long userId,
                                 PatchShotRequest request) {
        StoryboardVersion version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);
        StoryboardShot shot = accessService.requireShot(projectId, versionId, shotId, userId, Action.EDIT_CONTENT);

        if (request.revision() != null) {
            versionService.bumpRevision(version, request.revision());
        }
        applyPatch(shot, request);
        shotMapper.updateById(shot);

        updateVersionCounts(version);
        writeAudit(versionId, userId, "patch_shot", "shot", shotId, null);

        return toShotDetail(shot);
    }

    @Transactional
    public List<ShotDetail> batchPatchShots(Long projectId, Long versionId, Long userId,
                                              BatchPatchShotsRequest request) {
        StoryboardVersion version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);
        versionService.bumpRevision(version, request.revision());

        List<ShotDetail> results = new ArrayList<>();
        for (ShotFieldPatch patch : request.patches()) {
            StoryboardShot shot = shotMapper.selectById(patch.shotId());
            if (shot == null || !versionId.equals(shot.getVersionId())) continue;

            if (patch.durationMs() != null) shot.setDurationMs(patch.durationMs());
            if (patch.shotSize() != null) shot.setShotSize(patch.shotSize());
            if (patch.visualDescription() != null) shot.setVisualDescription(patch.visualDescription());
            if (patch.lightingAtmosphere() != null) shot.setLightingAtmosphere(patch.lightingAtmosphere());
            if (patch.characterAction() != null) shot.setCharacterAction(patch.characterAction());
            if (patch.emotionDescription() != null) shot.setEmotionDescription(patch.emotionDescription());
            if (patch.dialogueText() != null) shot.setDialogueText(patch.dialogueText());
            if (patch.sceneTags() != null) shot.setSceneTagsJson(toJson(patch.sceneTags()));
            if (patch.soundEffect() != null) shot.setSoundEffect(patch.soundEffect());
            if (patch.referenceText() != null) shot.setReferenceText(patch.referenceText());
            if (patch.imagePrompt() != null) shot.setImagePrompt(patch.imagePrompt());
            if (patch.videoMotionPrompt() != null) shot.setVideoMotionPrompt(patch.videoMotionPrompt());
            if (patch.status() != null) shot.setStatus(patch.status());

            shotMapper.updateById(shot);
            results.add(toShotDetail(shot));
        }

        updateVersionCounts(version);
        writeAudit(versionId, userId, "batch_patch_shots", "version", versionId, null);

        return results;
    }

    @Transactional
    public void deleteShot(Long projectId, Long versionId, Long shotId, Long userId) {
        StoryboardVersion version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);
        StoryboardShot shot = accessService.requireShot(projectId, versionId, shotId, userId, Action.EDIT_CONTENT);

        shotMapper.deleteById(shotId);
        regenerateShotCodes(versionId);
        updateVersionCounts(version);
        versionService.bumpRevision(version, version.getRevision());
        writeAudit(versionId, userId, "delete_shot", "shot", shotId, null);
    }

    @Transactional
    public ShotDetail copyShot(Long projectId, Long versionId, Long shotId, Long userId) {
        StoryboardVersion version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);
        StoryboardShot original = accessService.requireShot(projectId, versionId, shotId, userId, Action.EDIT_CONTENT);

        StoryboardShot copy = new StoryboardShot();
        copy.setUuid(UUID.randomUUID().toString());
        copy.setVersionId(versionId);
        copy.setSceneId(original.getSceneId());
        copy.setShotKey(UUID.randomUUID().toString());
        copy.setDurationMs(original.getDurationMs());
        copy.setShotSize(original.getShotSize());
        copy.setVisualDescription(original.getVisualDescription());
        copy.setLightingAtmosphere(original.getLightingAtmosphere());
        copy.setCharacterAction(original.getCharacterAction());
        copy.setEmotionDescription(original.getEmotionDescription());
        copy.setDialogueText(original.getDialogueText());
        copy.setSceneTagsJson(original.getSceneTagsJson());
        copy.setSoundEffect(original.getSoundEffect());
        copy.setReferenceText(original.getReferenceText());
        copy.setImagePrompt(original.getImagePrompt());
        copy.setVideoMotionPrompt(original.getVideoMotionPrompt());
        copy.setSceneAssetId(original.getSceneAssetId());
        copy.setSceneAssetVersionId(original.getSceneAssetVersionId());
        copy.setSceneVariantId(original.getSceneVariantId());
        copy.setSceneVariantVersion(original.getSceneVariantVersion());
        copy.setSceneAssetSnapshot(original.getSceneAssetSnapshot());
        copy.setStatus(ShotStatus.DRAFT.value());
        copy.setSortOrder(original.getSortOrder() + 1);
        shotMapper.insert(copy);

        regenerateShotCodes(versionId);
        updateVersionCounts(version);
        versionService.bumpRevision(version, version.getRevision());

        return toShotDetail(copy);
    }

    // ===== Structural Operations =====

    @Transactional
    public List<ShotDetail> splitShot(Long projectId, Long versionId, Long shotId, Long userId,
                                        SplitShotRequest request) {
        StoryboardVersion version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);
        StoryboardShot original = accessService.requireShot(projectId, versionId, shotId, userId, Action.EDIT_CONTENT);

        long firstDuration = request.firstDurationMs();
        long totalDuration = original.getDurationMs() != null ? original.getDurationMs() : 0;
        long secondDuration = totalDuration - firstDuration;
        if (firstDuration <= 0 || secondDuration <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "拆分时长必须位于镜头时长范围内");
        }

        original.setDurationMs(firstDuration);
        shotMapper.updateById(original);

        StoryboardShot second = new StoryboardShot();
        second.setUuid(UUID.randomUUID().toString());
        second.setVersionId(versionId);
        second.setSceneId(original.getSceneId());
        second.setShotKey(UUID.randomUUID().toString());
        second.setDurationMs(secondDuration);
        second.setShotSize(original.getShotSize());
        second.setSceneAssetId(original.getSceneAssetId());
        second.setSceneAssetVersionId(original.getSceneAssetVersionId());
        second.setSceneVariantId(original.getSceneVariantId());
        second.setSceneVariantVersion(original.getSceneVariantVersion());
        second.setSceneAssetSnapshot(original.getSceneAssetSnapshot());
        second.setStatus(ShotStatus.DRAFT.value());
        second.setSortOrder(original.getSortOrder() + 1);
        shotMapper.insert(second);

        // Shift subsequent shots
        shiftSortOrders(versionId, original.getSceneId(), original.getSortOrder() + 1, 1);

        regenerateShotCodes(versionId);
        updateVersionCounts(version);
        versionService.bumpRevision(version, version.getRevision());

        return List.of(toShotDetail(original), toShotDetail(second));
    }

    @Transactional
    public ShotDetail mergeShots(Long projectId, Long versionId, Long userId,
                                  MergeShotsRequest request) {
        StoryboardVersion version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);

        List<Long> shotIds = new ArrayList<>(new LinkedHashSet<>(request.shotIds()));
        if (shotIds.size() < 2) {
            throw new BizException(ErrorCode.PARAM_INVALID, "合并至少需要2个不同镜头");
        }
        if (shotIds.stream().anyMatch(Objects::isNull)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "镜头ID不能为空");
        }

        List<StoryboardShot> shots = shotIds.stream()
                .map(shotId -> accessService.requireShot(
                        projectId, versionId, shotId, userId, Action.EDIT_CONTENT))
                .sorted(Comparator.comparingInt(StoryboardShot::getSortOrder))
                .collect(Collectors.toList());
        Long sceneId = shots.get(0).getSceneId();
        if (shots.stream().anyMatch(shot -> !Objects.equals(sceneId, shot.getSceneId()))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只能合并同一场景内的镜头");
        }
        StoryboardShot bindingReference = shots.get(0);
        if (shots.stream().anyMatch(shot -> !sameSceneAssetBinding(bindingReference, shot))) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "镜头场景资产绑定不一致，无法合并；请先统一镜头 "
                            + shotIds + " 的场景资产/变体快照");
        }
        versionService.bumpRevision(version, request.revision());

        StoryboardShot first = shots.get(0);
        long totalDuration = shots.stream().mapToLong(s -> s.getDurationMs() != null ? s.getDurationMs() : 0).sum();
        String mergedDesc = shots.stream()
                .map(StoryboardShot::getVisualDescription)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        String mergedDialogue = shots.stream()
                .map(StoryboardShot::getDialogueText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));

        first.setDurationMs(totalDuration);
        first.setVisualDescription(mergedDesc);
        first.setDialogueText(mergedDialogue);
        shotMapper.updateById(first);

        // Delete remaining shots
        for (int i = 1; i < shots.size(); i++) {
            shotMapper.deleteById(shots.get(i).getId());
        }

        regenerateShotCodes(versionId);
        updateVersionCounts(version);
        writeAudit(versionId, userId, "merge_shots", "version", versionId, null);

        return toShotDetail(first);
    }

    private boolean sameSceneAssetBinding(StoryboardShot left, StoryboardShot right) {
        return Objects.equals(left.getSceneAssetId(), right.getSceneAssetId())
                && Objects.equals(left.getSceneAssetVersionId(), right.getSceneAssetVersionId())
                && Objects.equals(left.getSceneVariantId(), right.getSceneVariantId())
                && Objects.equals(left.getSceneVariantVersion(), right.getSceneVariantVersion())
                && sameJsonContent(left.getSceneAssetSnapshot(), right.getSceneAssetSnapshot());
    }

    private boolean sameJsonContent(String left, String right) {
        if (Objects.equals(left, right)) return true;
        if (left == null || right == null) return false;
        try {
            return objectMapper.readTree(left).equals(objectMapper.readTree(right));
        } catch (JsonProcessingException ex) {
            return false;
        }
    }

    @Transactional
    public void reorderShots(Long projectId, Long versionId, Long userId,
                              ReorderShotsRequest request) {
        StoryboardVersion version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);
        versionService.bumpRevision(version, request.revision());

        for (ReorderShotItem item : request.items()) {
            StoryboardShot shot = shotMapper.selectById(item.shotId());
            if (shot != null && versionId.equals(shot.getVersionId())) {
                shot.setSceneId(item.sceneId());
                shot.setSortOrder(item.sortOrder());
                shotMapper.updateById(shot);
            }
        }
        regenerateShotCodes(versionId);
        writeAudit(versionId, userId, "reorder_shots", "version", versionId, null);
    }

    @Transactional
    public ShotDetail bindSceneAsset(Long projectId, Long storyboardId, Long versionId, Long shotId,
                                     Long userId, BindSceneAssetRequest request) {
        StoryboardVersion version = requireVersionInStoryboard(
                projectId, storyboardId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);
        StoryboardShot shot = accessService.requireShot(projectId, versionId, shotId, userId, Action.EDIT_CONTENT);
        ProjectSceneAssetService.ResolvedSceneBinding binding = sceneAssetService.resolveStoryboardSnapshot(
                userId, projectId, request.sceneAssetId(), request.sceneAssetVersionId(),
                request.sceneVariantId(), request.sceneVariantVersion(), request.sceneOverride());
        shot.setSceneAssetId(binding.assetId());
        shot.setSceneAssetVersionId(binding.assetVersionId());
        shot.setSceneVariantId(binding.variantId());
        shot.setSceneVariantVersion(binding.variantVersion());
        shot.setSceneAssetSnapshot(binding.snapshotJson());
        shotMapper.updateById(shot);
        versionService.bumpRevision(version, version.getRevision());
        writeAudit(versionId, userId, "bind_scene_asset", "shot", shotId,
                "asset=" + binding.assetId() + ",version=" + binding.assetVersionId());
        return toShotDetail(shot);
    }

    public ContinuityCheckView continuityCheck(Long projectId, Long storyboardId, Long versionId, Long userId) {
        requireVersionInStoryboard(projectId, storyboardId, versionId, userId, Action.VIEW);
        List<StoryboardShot> shots = shotMapper.selectList(new LambdaQueryWrapper<StoryboardShot>()
                .eq(StoryboardShot::getVersionId, versionId).orderByAsc(StoryboardShot::getSortOrder));
        List<ContinuityIssueView> issues = sceneAssetService.storyboardContinuityIssues(projectId, shots).stream()
                .map(issue -> new ContinuityIssueView(issue.code(), issue.shotId(), issue.shotCode(),
                        issue.message(), issue.repairAction())).toList();
        return new ContinuityCheckView(issues.isEmpty(), issues);
    }

    // ===== Helpers =====

    private void requireEditable(StoryboardVersion version) {
        if (!StoryboardStateMachine.isEditable(
                com.aicp.module.storyboard.domain.StoryboardEnums.VersionStatus.valueOf(
                        version.getStatus().toUpperCase()))) {
            throw new BizException(ErrorCode.STORYBOARD_VERSION_LOCKED);
        }
    }

    private StoryboardVersion requireVersionInStoryboard(Long projectId, Long storyboardId, Long versionId,
                                                          Long userId, Action action) {
        accessService.requireStoryboard(projectId, storyboardId, userId, action);
        StoryboardVersion version = accessService.requireVersion(projectId, versionId, userId, action);
        if (!storyboardId.equals(version.getStoryboardId())) {
            throw new BizException(ErrorCode.STORYBOARD_VERSION_NOT_FOUND);
        }
        return version;
    }

    private void applyPatch(StoryboardShot shot, PatchShotRequest r) {
        if (r.durationMs() != null) shot.setDurationMs(r.durationMs());
        if (r.shotSize() != null) shot.setShotSize(r.shotSize());
        if (r.visualDescription() != null) shot.setVisualDescription(r.visualDescription());
        if (r.lightingAtmosphere() != null) shot.setLightingAtmosphere(r.lightingAtmosphere());
        if (r.characterAction() != null) shot.setCharacterAction(r.characterAction());
        if (r.emotionDescription() != null) shot.setEmotionDescription(r.emotionDescription());
        if (r.dialogueText() != null) shot.setDialogueText(r.dialogueText());
        if (r.sceneTags() != null) shot.setSceneTagsJson(toJson(r.sceneTags()));
        if (r.soundEffect() != null) shot.setSoundEffect(r.soundEffect());
        if (r.referenceText() != null) shot.setReferenceText(r.referenceText());
        if (r.imagePrompt() != null) shot.setImagePrompt(r.imagePrompt());
        if (r.videoMotionPrompt() != null) shot.setVideoMotionPrompt(r.videoMotionPrompt());
        if (r.status() != null) shot.setStatus(r.status());
    }

    private void regenerateShotCodes(Long versionId) {
        List<StoryboardScene> scenes = sceneMapper.selectList(
                new LambdaQueryWrapper<StoryboardScene>()
                        .eq(StoryboardScene::getVersionId, versionId)
                        .orderByAsc(StoryboardScene::getSortOrder));
        for (StoryboardScene scene : scenes) {
            List<StoryboardShot> shots = shotMapper.selectList(
                    new LambdaQueryWrapper<StoryboardShot>()
                            .eq(StoryboardShot::getVersionId, versionId)
                            .eq(StoryboardShot::getSceneId, scene.getId())
                            .orderByAsc(StoryboardShot::getSortOrder));
            for (int i = 0; i < shots.size(); i++) {
                StoryboardShot shot = shots.get(i);
                String code = String.format("S%02d-C%02d", scene.getSceneNo(), i + 1);
                if (!code.equals(shot.getShotCode())) {
                    shot.setShotCode(code);
                    shotMapper.updateById(shot);
                }
            }
        }
    }

    private void regenerateSceneNumbers(Long versionId) {
        List<StoryboardScene> scenes = sceneMapper.selectList(
                new LambdaQueryWrapper<StoryboardScene>()
                        .eq(StoryboardScene::getVersionId, versionId)
                        .orderByAsc(StoryboardScene::getSortOrder));
        for (int i = 0; i < scenes.size(); i++) {
            StoryboardScene scene = scenes.get(i);
            if (scene.getSceneNo() != i + 1) {
                scene.setSceneNo(i + 1);
                sceneMapper.updateById(scene);
            }
        }
        regenerateShotCodes(versionId);
    }

    private void shiftSortOrders(Long versionId, Long sceneId, int fromSort, int delta) {
        List<StoryboardShot> shots = shotMapper.selectList(
                new LambdaQueryWrapper<StoryboardShot>()
                        .eq(StoryboardShot::getVersionId, versionId)
                        .eq(StoryboardShot::getSceneId, sceneId)
                        .ge(StoryboardShot::getSortOrder, fromSort)
                        .orderByAsc(StoryboardShot::getSortOrder));
        for (StoryboardShot s : shots) {
            s.setSortOrder(s.getSortOrder() + delta);
            shotMapper.updateById(s);
        }
    }

    private void updateVersionCounts(StoryboardVersion version) {
        long sceneCount = sceneMapper.selectCount(
                new LambdaQueryWrapper<StoryboardScene>()
                        .eq(StoryboardScene::getVersionId, version.getId()));
        long shotCount = shotMapper.selectCount(
                new LambdaQueryWrapper<StoryboardShot>()
                        .eq(StoryboardShot::getVersionId, version.getId()));
        List<StoryboardShot> allShots = shotMapper.selectList(
                new LambdaQueryWrapper<StoryboardShot>()
                        .eq(StoryboardShot::getVersionId, version.getId()));
        long totalDuration = allShots.stream()
                .mapToLong(s -> s.getDurationMs() != null ? s.getDurationMs() : 0).sum();

        version.setTotalScenes((int) sceneCount);
        version.setTotalShots((int) shotCount);
        version.setTotalDurationMs(totalDuration);
        versionMapper.updateById(version);
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

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String summarize(String text) {
        if (text == null) return null;
        return text.length() <= SUMMARY_MAX_LENGTH ? text : text.substring(0, SUMMARY_MAX_LENGTH) + "...";
    }

    private ShotDetail toShotDetail(StoryboardShot s) {
        List<String> tags = parseJsonList(s.getSceneTagsJson());
        return new ShotDetail(s.getId(), s.getUuid(), s.getVersionId(), s.getSceneId(),
                s.getShotKey(), s.getShotCode(), s.getDurationMs(), s.getShotSize(),
                s.getVisualDescription(), s.getLightingAtmosphere(), s.getCharacterAction(),
                s.getEmotionDescription(), s.getDialogueText(), tags,
                s.getSoundEffect(), s.getReferenceText(), s.getImagePrompt(),
                s.getVideoMotionPrompt(), s.getDirectorIntention(), s.getActionMotivation(),
                s.getRelationshipBlocking(), s.getInformationGap(), s.getAudioVisualRelation(),
                s.getEditPoint(), s.getDubText(), s.getSubtitleText(), s.getFailureStrategy(),
                s.getStatus(), s.getSortOrder(), s.getSceneAssetId(), s.getSceneAssetVersionId(),
                s.getSceneVariantId(), s.getSceneVariantVersion(), parseJsonMap(s.getSceneAssetSnapshot()));
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, LinkedHashMap.class);
        } catch (Exception e) {
            return null;
        }
    }
}
