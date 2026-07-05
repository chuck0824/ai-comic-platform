package com.aicp.module.sop.service;

import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.sop.domain.SopCheckContext;
import com.aicp.module.storyboard.entity.*;
import com.aicp.module.storyboard.mapper.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SopContextAssembler {

    private final ContentProjectMapper projectMapper;
    private final ContentUnitMapper contentUnitMapper;
    private final StoryboardMapper storyboardMapper;
    private final StoryboardVersionMapper versionMapper;
    private final StoryboardSceneMapper sceneMapper;
    private final StoryboardVersionShotMapper shotMapper;
    private final StoryboardShotVisualBindingMapper visualBindingMapper;
    private final ObjectMapper objectMapper;

    public SopCheckContext assemble(Long projectId, Long contentUnitId, Long canvasProjectId) {
        Map<String, Boolean> sourceAvailability = new LinkedHashMap<>();
        Map<String, Integer> sourceRevisions = new LinkedHashMap<>();

        // Project
        ContentProject project = projectMapper.selectById(projectId);
        sourceAvailability.put("project", project != null);
        if (project != null) {
            sourceRevisions.put("project:" + projectId, project.getRevision());
        }

        // Content unit
        ContentUnit contentUnit = null;
        if (contentUnitId != null) {
            contentUnit = contentUnitMapper.selectById(contentUnitId);
            if (contentUnit != null && !contentUnit.getProjectId().equals(projectId)) {
                log.warn("Content unit {} does not belong to project {}", contentUnitId, projectId);
                contentUnit = null;
            }
        }
        sourceAvailability.put("content_unit", contentUnit != null);
        if (contentUnit != null) {
            sourceRevisions.put("content-unit:" + contentUnit.getId(), contentUnit.getRevision());
        }

        // Storyboard — find the one belonging to this project+unit
        Storyboard storyboard = null;
        StoryboardVersion lockedVersion = null;
        List<StoryboardScene> scenes = List.of();
        List<StoryboardShot> shots = List.of();
        List<StoryboardShotVisualBinding> visualBindings = List.of();

        if (project != null) {
            List<Storyboard> storyboards = storyboardMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Storyboard>()
                            .eq(Storyboard::getProjectId, projectId)
                            .eq(contentUnitId != null, Storyboard::getContentUnitId, contentUnitId)
            );

            if (!storyboards.isEmpty()) {
                storyboard = storyboards.get(0);
                sourceAvailability.put("storyboard", true);
                sourceRevisions.put("storyboard:" + storyboard.getId(), 1);

                // Locked version
                if (storyboard.getCurrentLockedVersionId() != null) {
                    lockedVersion = versionMapper.selectById(storyboard.getCurrentLockedVersionId());
                }
                sourceAvailability.put("locked_storyboard_version", lockedVersion != null);

                if (lockedVersion != null) {
                    sourceRevisions.put("storyboard-version:" + lockedVersion.getId(), lockedVersion.getVersionNo());

                    // Scenes
                    scenes = sceneMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StoryboardScene>()
                                    .eq(StoryboardScene::getVersionId, lockedVersion.getId())
                                    .orderByAsc(StoryboardScene::getSortOrder)
                    );
                    sourceRevisions.put("scenes-count", scenes.size());

                    // Shots
                    shots = shotMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StoryboardShot>()
                                    .eq(StoryboardShot::getVersionId, lockedVersion.getId())
                                    .orderByAsc(StoryboardShot::getSortOrder)
                    );
                    sourceRevisions.put("shots-count", shots.size());

                    // Visual bindings
                    if (!shots.isEmpty()) {
                        List<Long> shotIds = shots.stream().map(StoryboardShot::getId).toList();
                        visualBindings = visualBindingMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StoryboardShotVisualBinding>()
                                        .in(StoryboardShotVisualBinding::getShotId, shotIds)
                        );
                    }
                    sourceRevisions.put("visual-bindings-count", visualBindings.size());
                }
            } else {
                sourceAvailability.put("storyboard", false);
                sourceAvailability.put("locked_storyboard_version", false);
            }
        }

        // Compute hashes
        String scopeHash = computeScopeHash(projectId, contentUnitId, canvasProjectId);
        String snapshotHash = computeSnapshotHash(sourceRevisions);

        return new SopCheckContext(
                projectId, contentUnitId, canvasProjectId,
                project, contentUnit, storyboard, lockedVersion,
                scenes, shots, visualBindings,
                Collections.unmodifiableMap(sourceAvailability),
                Collections.unmodifiableMap(sourceRevisions),
                scopeHash, snapshotHash
        );
    }

    private String computeScopeHash(Long projectId, Long contentUnitId, Long canvasProjectId) {
        String input = "project:" + projectId
                + "|unit:" + (contentUnitId != null ? contentUnitId : "null")
                + "|canvas:" + (canvasProjectId != null ? canvasProjectId : "null");
        return sha256(input);
    }

    private String computeSnapshotHash(Map<String, Integer> revisions) {
        String json;
        try {
            // Sort keys for deterministic output
            TreeMap<String, Integer> sorted = new TreeMap<>(revisions);
            json = objectMapper.writeValueAsString(sorted);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize source revisions for snapshot hash", e);
            json = revisions.toString();
        }
        return sha256(json);
    }

    static String sha256(String input) {
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
}
