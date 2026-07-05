package com.aicp.module.sop.domain;

import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.storyboard.entity.*;

import java.util.List;
import java.util.Map;

public record SopCheckContext(
        Long projectId,
        Long contentUnitId,
        Long canvasProjectId,
        ContentProject project,
        ContentUnit contentUnit,
        Storyboard storyboard,
        StoryboardVersion lockedVersion,
        List<StoryboardScene> scenes,
        List<StoryboardShot> shots,
        List<StoryboardShotVisualBinding> visualBindings,
        Map<String, Boolean> sourceAvailability,
        Map<String, Integer> sourceRevisions,
        String scopeHash,
        String snapshotHash) {

    public boolean hasLockedVersion() {
        return lockedVersion != null;
    }

    public boolean hasScenes() {
        return scenes != null && !scenes.isEmpty();
    }

    public boolean hasShots() {
        return shots != null && !shots.isEmpty();
    }

    public List<StoryboardShot> shotsForScene(Long sceneId) {
        if (shots == null) return List.of();
        return shots.stream().filter(s -> s.getSceneId().equals(sceneId)).toList();
    }

    public List<StoryboardShotVisualBinding> bindingsForShot(Long shotId) {
        if (visualBindings == null) return List.of();
        return visualBindings.stream().filter(b -> b.getShotId().equals(shotId)).toList();
    }
}
