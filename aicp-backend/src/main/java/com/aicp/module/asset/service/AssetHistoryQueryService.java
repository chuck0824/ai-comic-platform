package com.aicp.module.asset.service;

import com.aicp.module.asset.dto.AssetWorkbenchRequests.RecordQuery;
import com.aicp.module.asset.dto.AssetWorkbenchViews.*;
import com.aicp.module.asset.entity.WorkspaceAsset;
import com.aicp.module.asset.entity.WorkspaceAssetFavorite;
import com.aicp.module.asset.mapper.AssetVersionMapper;
import com.aicp.module.asset.mapper.WorkspaceAssetFavoriteMapper;
import com.aicp.module.asset.mapper.WorkspaceAssetMapper;
import com.aicp.module.generation.entity.GenerationTask;
import com.aicp.module.generation.mapper.GenerationTaskMapper;
import com.aicp.common.workspace.WorkspaceContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetHistoryQueryService {

    private final WorkspaceAssetMapper assetMapper;
    private final AssetVersionMapper versionMapper;
    private final WorkspaceAssetFavoriteMapper favoriteMapper;
    private final GenerationTaskMapper taskMapper;

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "created_at:desc", "created_at:asc", "updated_at:desc", "name:asc");

    /**
     * Unified projection: tasks (non-succeeded) + canonical assets (succeeded).
     * Fetches all matching records without server-side pagination, then
     * deduplicates, sorts, and paginates in memory. Capped at 1000 total
     * records to prevent memory pressure.
     */
    @Transactional(readOnly = true)
    public PageResult<RecordSummary> queryRecords(WorkspaceContext ctx, RecordQuery query) {
        String wsId = ctx.workspaceId();
        Long userId = ctx.userId();

        // ── Collect matching asset records (uncapped for accurate pagination) ──
        List<WorkspaceAsset> assets = queryAssets(wsId, query);
        List<RecordSummary> summaries = assets.stream()
                .map(a -> toSummary(a, wsId, userId))
                .collect(Collectors.toCollection(ArrayList::new));

        // ── Dedup: collect source task IDs from settled assets ──
        Set<Long> settledTaskIds = assets.stream()
                .map(WorkspaceAsset::getSourceTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // ── Collect matching task records (non-succeeded), skip settled ──
        List<GenerationTask> tasks = queryTasks(wsId, query);
        for (GenerationTask t : tasks) {
            if (settledTaskIds.contains(t.getId())) {
                continue; // already shown as an asset
            }
            summaries.add(toTaskSummary(t));
        }

        // ── Cap to prevent unbounded in-memory sort ──
        int total = Math.min(summaries.size(), 1000);

        // ── Sort & paginate ──
        summaries.sort(comparatorFor(query.sort()));
        int page = query.page() != null ? query.page() : 1;
        int pageSize = query.pageSize() != null ? query.pageSize() : 24;
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<RecordSummary> pageItems = fromIndex < total
                ? new ArrayList<>(summaries.subList(fromIndex, toIndex))
                : List.of();

        // ── Facets ──
        RecordFacets facets = computeFacets(wsId);

        return new PageResult<>(pageItems, page, pageSize, total,
                (int) Math.ceil((double) total / pageSize),
                toIndex < total, facets);
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> queryProjects(WorkspaceContext ctx) {
        // Simplified: return empty until content_project integration
        return List.of();
    }

    @Transactional(readOnly = true)
    public RecordDetail queryDetail(WorkspaceContext ctx, String recordKind, String recordUuid) {
        if ("TASK".equalsIgnoreCase(recordKind)) {
            GenerationTask task = taskMapper.selectOne(
                    new LambdaQueryWrapper<GenerationTask>()
                            .eq(GenerationTask::getUuid, recordUuid)
                            .eq(GenerationTask::getWorkspaceId, ctx.workspaceId()));
            if (task == null) return null;
            return toTaskDetail(task);
        }

        WorkspaceAsset asset = assetMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceAsset>()
                        .eq(WorkspaceAsset::getUuid, recordUuid)
                        .eq(WorkspaceAsset::getWorkspaceId, ctx.workspaceId()));
        if (asset == null) return null;
        return toDetail(asset, ctx);
    }

    // ── Private helpers ───────────────────────────────────────────────

    private List<WorkspaceAsset> queryAssets(String wsId, RecordQuery query) {
        LambdaQueryWrapper<WorkspaceAsset> qw = new LambdaQueryWrapper<>();
        qw.eq(WorkspaceAsset::getWorkspaceId, wsId);

        // Collection filter
        if ("TRASH".equalsIgnoreCase(query.collection())) {
            qw.eq(WorkspaceAsset::getStatus, "TRASHED");
        } else if ("FAVORITES".equalsIgnoreCase(query.collection())) {
            // Handled via favorites join — simplified for now
            qw.ne(WorkspaceAsset::getStatus, "TRASHED");
        } else {
            qw.ne(WorkspaceAsset::getStatus, "TRASHED");
        }

        // Type filter
        if (query.assetType() != null && !query.assetType().isBlank()) {
            qw.eq(WorkspaceAsset::getAssetType, query.assetType().toUpperCase());
        }

        // Keyword
        if (query.keyword() != null && !query.keyword().isBlank()) {
            qw.like(WorkspaceAsset::getName, query.keyword());
        }

        // Sorting
        String sortCol = "created_at";
        boolean asc = false;
        if (query.sort() != null && ALLOWED_SORTS.contains(query.sort())) {
            String[] parts = query.sort().split(":");
            sortCol = parts[0].equals("updated_at") ? "updated_at" :
                      parts[0].equals("name") ? "name" : "created_at";
            asc = parts.length > 1 && "asc".equals(parts[1]);
        }
        if (asc) {
            qw.orderByAsc(sortCol.equals("name") ? WorkspaceAsset::getName :
                    sortCol.equals("updated_at") ? WorkspaceAsset::getUpdatedAt :
                            WorkspaceAsset::getCreatedAt);
        } else {
            qw.orderByDesc(sortCol.equals("name") ? WorkspaceAsset::getName :
                    sortCol.equals("updated_at") ? WorkspaceAsset::getUpdatedAt :
                            WorkspaceAsset::getCreatedAt);
        }

        // Fetch all matching assets (paginated in-memory after task merge)
        int page = query.page() != null ? query.page() : 1;
        int size = query.pageSize() != null ? query.pageSize() : 24;
        Page<WorkspaceAsset> mpPage = new Page<>(1, 1000); // fetch all, capped
        mpPage = assetMapper.selectPage(mpPage, qw);
        return mpPage.getRecords();
    }

    private List<GenerationTask> queryTasks(String wsId, RecordQuery query) {
        LambdaQueryWrapper<GenerationTask> qw = new LambdaQueryWrapper<>();
        qw.eq(GenerationTask::getWorkspaceId, wsId);
        qw.in(GenerationTask::getStatus, "pending", "running", "failed", "canceled");

        if (query.assetType() != null && !query.assetType().isBlank()) {
            qw.eq(GenerationTask::getAssetType, query.assetType().toUpperCase());
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            qw.and(w -> w.like(GenerationTask::getSubType, query.keyword()));
        }
        qw.orderByDesc(GenerationTask::getCreatedAt);
        qw.last("LIMIT 100"); // Tasks always capped

        return taskMapper.selectList(qw);
    }

    private RecordSummary toSummary(WorkspaceAsset a, String wsId, Long userId) {
        boolean isFav = favoriteMapper.selectCount(
                new LambdaQueryWrapper<WorkspaceAssetFavorite>()
                        .eq(WorkspaceAssetFavorite::getWorkspaceId, wsId)
                        .eq(WorkspaceAssetFavorite::getUserId, userId)
                        .eq(WorkspaceAssetFavorite::getAssetId, a.getId())) > 0;

        return new RecordSummary(
                "ASSET", "asset-" + a.getUuid(),
                a.getName(), a.getAssetType(), a.getMediaType(),
                a.getStatus(), null, null, null, null,
                a.getCreatorUserId(), a.getCreatedAt(), a.getUpdatedAt(),
                null, null, null, null, null, null, null, null, null,
                isFav, false, null, null,
                computeAllowedActions(a, true));
    }

    private RecordSummary toTaskSummary(GenerationTask t) {
        return new RecordSummary(
                "TASK", "task-" + t.getUuid(),
                t.getSubType() != null ? t.getSubType() : t.getType(),
                t.getAssetType(), null,
                t.getStatus(), t.getModelId(), t.getProvider(),
                null, null, t.getCreatedBy(),
                t.getCreatedAt(), t.getUpdatedAt(),
                t.getProgress(), t.getCreditCost(),
                t.getErrorCode(), t.getErrorMessage(),
                null, null, null, null, null,
                false, false, null, null,
                computeTaskActions(t));
    }

    private List<String> computeAllowedActions(WorkspaceAsset a, boolean isActive) {
        List<String> actions = new ArrayList<>();
        actions.add("PREVIEW");
        if (isActive) {
            actions.add("EDIT");
            actions.add("FAVORITE");
            actions.add("DOWNLOAD");
            actions.add("SEND_TO_CANVAS");
            actions.add("REGENERATE");
            actions.add("PUBLISH");
            actions.add("TRASH");
        } else {
            actions.add("RESTORE");
        }
        return actions;
    }

    private List<String> computeTaskActions(GenerationTask t) {
        List<String> actions = new ArrayList<>();
        switch (t.getStatus()) {
            case "pending", "running" -> actions.add("CANCEL_TASK");
            case "failed", "canceled" -> {
                actions.add("RETRY_TASK");
                actions.add("TRASH");
            }
        }
        return actions;
    }

    private RecordFacets computeFacets(String wsId) {
        long total = assetMapper.selectCount(
                new LambdaQueryWrapper<WorkspaceAsset>()
                        .eq(WorkspaceAsset::getWorkspaceId, wsId)
                        .ne(WorkspaceAsset::getStatus, "TRASHED"));

        long running = taskMapper.selectCount(
                new LambdaQueryWrapper<GenerationTask>()
                        .eq(GenerationTask::getWorkspaceId, wsId)
                        .eq(GenerationTask::getStatus, "running"));

        long failed = taskMapper.selectCount(
                new LambdaQueryWrapper<GenerationTask>()
                        .eq(GenerationTask::getWorkspaceId, wsId)
                        .eq(GenerationTask::getStatus, "failed"));

        long pending = taskMapper.selectCount(
                new LambdaQueryWrapper<GenerationTask>()
                        .eq(GenerationTask::getWorkspaceId, wsId)
                        .eq(GenerationTask::getStatus, "pending"));

        long canceled = taskMapper.selectCount(
                new LambdaQueryWrapper<GenerationTask>()
                        .eq(GenerationTask::getWorkspaceId, wsId)
                        .eq(GenerationTask::getStatus, "canceled"));

        long trashed = assetMapper.selectCount(
                new LambdaQueryWrapper<WorkspaceAsset>()
                        .eq(WorkspaceAsset::getWorkspaceId, wsId)
                        .eq(WorkspaceAsset::getStatus, "TRASHED"));

        return new RecordFacets(total, pending, running, total - pending - running - failed - canceled,
                failed, canceled, trashed, Map.of(), Map.of());
    }

    private RecordDetail toDetail(WorkspaceAsset a, WorkspaceContext ctx) {
        boolean isFav = favoriteMapper.selectCount(
                new LambdaQueryWrapper<WorkspaceAssetFavorite>()
                        .eq(WorkspaceAssetFavorite::getWorkspaceId, ctx.workspaceId())
                        .eq(WorkspaceAssetFavorite::getUserId, ctx.userId())
                        .eq(WorkspaceAssetFavorite::getAssetId, a.getId())) > 0;

        String provider = null;
        String modelId = null;
        String prompt = null;
        String negativePrompt = null;
        Long seed = null;
        Integer w = null, h = null, dur = null;
        Long fs = null;
        String mime = null, sk = null, chk = null;

        return new RecordDetail(
                "ASSET", "asset-" + a.getUuid(),
                a.getName(), a.getAssetType(), a.getMediaType(),
                a.getStatus(), a.getDescription(), parseTags(a.getTags()),
                provider, modelId, prompt, negativePrompt, seed,
                w, h, dur, fs, mime, sk, chk,
                a.getCreatorUserId(), null, a.getCreatedAt(), a.getUpdatedAt(),
                isFav, false, a.getRowVersion(),
                null, null,
                null, null,
                List.of(), List.of());
    }

    private RecordDetail toTaskDetail(GenerationTask t) {
        String desc = null;
        List<String> emptyTags = List.of();
        String provider = t.getProvider();
        String modelId = t.getModelId();
        String prompt = null;
        String negativePrompt = null;
        Long seed = null;
        Integer w = null, h = null, dur = null;
        Long fs = null;
        String mime = null, sk = null, chk = null;
        Integer rv = null;
        String pu = null, pn = null;
        ReferenceView src = null;
        List<ReferenceView> refs = null;

        return new RecordDetail(
                "TASK", "task-" + t.getUuid(),
                t.getSubType(), t.getAssetType(), null,
                t.getStatus(), desc, emptyTags,
                provider, modelId, prompt, negativePrompt, seed,
                w, h, dur, fs, mime, sk, chk,
                t.getCreatedBy(), null, t.getCreatedAt(), t.getUpdatedAt(),
                false, false, rv,
                pu, pn,
                src, refs,
                List.of(), List.of());
    }

    private Comparator<RecordSummary> comparatorFor(String sort) {
        Comparator<RecordSummary> c = Comparator.comparing(RecordSummary::createdAt);
        if (sort != null && sort.contains("asc")) c = c.reversed();
        return c.reversed(); // default newest first
    }

    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank() || "[]".equals(tagsJson)) return List.of();
        try {
            return java.util.Arrays.asList(
                    tagsJson.replace("[", "").replace("]", "").replace("\"", "").split(","));
        } catch (Exception e) {
            return List.of();
        }
    }
}
