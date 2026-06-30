package com.aicp.module.contentproject.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 作品编辑中心视图 DTO。
 */
public final class WorkEditorViews {

    private WorkEditorViews() {}

    /**
     * 编辑器首屏聚合视图。
     */
    public record EditorView(
            Long projectId,
            String title,
            Integer totalWords,
            String permissions,       // "owner", "editor", "viewer"
            ProfileView profile,
            Integer revision,
            Map<String, Integer> settingCounts,   // type -> count
            Integer pendingExtractionCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    /**
     * 资料视图。
     */
    public record ProfileView(
            String genreTag,
            List<String> plotTags,
            List<String> toneTags,
            String settingTag,
            String synopsis,
            String outline,
            Integer revision,
            Long updatedBy,
            LocalDateTime updatedAt
    ) {}
}
