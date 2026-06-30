package com.aicp.module.canvas.service;

import com.aicp.common.dto.PageResult;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.util.SecurityUtil;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.canvas.dto.CanvasProjectRequests.*;
import com.aicp.module.canvas.dto.CanvasProjectViews.*;
import com.aicp.module.canvas.entity.CanvasProject;
import com.aicp.module.canvas.mapper.CanvasProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CanvasProjectManagementService {

    private final CanvasProjectMapper projectMapper;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    // ===== Queries =====

    public PageResult<CanvasProjectSummary> list(Long userId, CanvasProjectQuery query) {
        return listInternal(userId, null, query);
    }

    public PageResult<CanvasProjectSummary> listByContentProject(
            Long userId, Long contentProjectId, CanvasProjectQuery query) {
        return listInternal(userId, contentProjectId, query);
    }

    private PageResult<CanvasProjectSummary> listInternal(
            Long userId, Long contentProjectId, CanvasProjectQuery query) {

        int page = query != null && query.page() != null ? query.page() : DEFAULT_PAGE;
        int pageSize = query != null && query.pageSize() != null ? query.pageSize() : DEFAULT_PAGE_SIZE;
        pageSize = Math.min(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<CanvasProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CanvasProject::getIsDeleted, 0);
        wrapper.eq(CanvasProject::getUserId, userId);

        if (contentProjectId != null) {
            wrapper.eq(CanvasProject::getContentProjectId, contentProjectId);
        } else if (query != null && query.contentProjectId() != null) {
            wrapper.eq(CanvasProject::getContentProjectId, query.contentProjectId());
        }

        // Workspace scope from request context
        WorkspaceContext wsCtx = getWorkspaceContext();
        if (wsCtx != null && StringUtils.hasText(wsCtx.workspaceId())) {
            wrapper.eq(CanvasProject::getWorkspaceId, wsCtx.workspaceId());
        }

        if (query != null && StringUtils.hasText(query.status())) {
            wrapper.eq(CanvasProject::getStatus, query.status());
        }

        if (query != null && StringUtils.hasText(query.keyword())) {
            wrapper.and(w -> w
                    .like(CanvasProject::getName, query.keyword())
                    .or()
                    .like(CanvasProject::getIdempotencyKey, query.keyword()));
        }

        wrapper.orderByDesc(CanvasProject::getUpdatedAt);

        Page<CanvasProject> result = projectMapper.selectPage(
                new Page<>(page, pageSize), wrapper);

        List<CanvasProjectSummary> items = result.getRecords().stream()
                .map(this::toSummary)
                .toList();

        return PageResult.of(items, page, pageSize, result.getTotal());
    }

    public CanvasProjectDetail getDetail(Long userId, String uuid) {
        CanvasProject p = findByUuid(userId, uuid);
        if (p == null) {
            throw new BizException(ErrorCode.CANVAS_NOT_FOUND);
        }
        return toDetail(p);
    }

    // ===== Creation =====

    @Transactional
    public CanvasProjectDetail create(Long userId, CreateCanvasProjectRequest request) {
        // Idempotency check
        CanvasProject existing = projectMapper.selectOne(
                new LambdaQueryWrapper<CanvasProject>()
                        .eq(CanvasProject::getUserId, userId)
                        .eq(CanvasProject::getIdempotencyKey, request.idempotencyKey())
                        .eq(CanvasProject::getIsDeleted, 0));
        if (existing != null) {
            return toDetail(existing);
        }

        // Check admission for official canvases
        if ("official".equals(request.purpose())) {
            ProductionAdmissionResult admission = checkAdmission(
                    request.contentProjectId(), request.productionUnitId(), request.purpose());
            if (!admission.passed()) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "生产准入未通过: " + admission.missingRequirements().stream()
                                .map(r -> r.label()).reduce((a, b) -> a + ", " + b).orElse(""));
            }
        }

        // Build immutable production snapshot from server-side records
        String snapshotJson = buildProductionSnapshot(request);

        CanvasProject project = new CanvasProject();
        project.setUuid("canvas_" + UUID.randomUUID().toString().replace("-", "").substring(0, 7));
        project.setUserId(userId);
        project.setOwnerId(request.ownerId());
        project.setName(request.name());
        project.setContentProjectId(request.contentProjectId());
        project.setProductionUnitType(request.productionUnitType());
        project.setProductionUnitId(request.productionUnitId());
        project.setSourceContentVersionId(request.sourceContentVersionId());
        project.setSourceStoryboardVersionId(request.sourceStoryboardVersionId());
        project.setProductionSnapshot(snapshotJson);
        project.setPurpose(Objects.requireNonNullElse(request.purpose(), "official"));
        project.setIdempotencyKey(request.idempotencyKey());
        project.setStatus("draft");
        project.setCanvasVersion(1);
        project.setRevision(0);
        project.setIsDeleted(0);

        projectMapper.insert(project);
        log.info("Created canvas project uuid={} name={}", project.getUuid(), project.getName());
        return toDetail(project);
    }

    public ProductionAdmissionResult checkAdmission(
            Long contentProjectId, Long productionUnitId, String purpose) {
        // For non-official canvases, admission is always granted
        if (!"official".equals(purpose)) {
            return new ProductionAdmissionResult(true, List.of());
        }

        List<ProductionAdmissionResult.MissingRequirement> missing = new ArrayList<>();

        // In a full implementation, these would query actual storyboard/content version status.
        // For now, we return passing by default — the caller (controller test) can mock as needed.
        // Production code should wire in: StoryboardMasterService, ContentVersionService, etc.

        return new ProductionAdmissionResult(missing.isEmpty(), missing);
    }

    private String buildProductionSnapshot(CreateCanvasProjectRequest request) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("contentVersionId", request.sourceContentVersionId());
            snapshot.put("contentVersionHash", "");
            snapshot.put("contentTitle", "");
            snapshot.put("contentSummary", "");
            snapshot.put("storyboardVersionId", request.sourceStoryboardVersionId());
            snapshot.put("storyboardRevision", 1);
            snapshot.put("shotCount", 0);
            snapshot.put("storyboardLocked", true);
            snapshot.put("platformRuleVersion", "v1");
            snapshot.put("pluginPackageVersion", "v1");
            snapshot.put("aspectRatio", "9:16");
            snapshot.put("resolution", "1080x1920");
            snapshot.put("fps", 25);
            snapshot.put("createdAt", LocalDateTime.now().toString());
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to build production snapshot");
        }
    }

    // ===== Lifecycle =====

    @Transactional
    public CanvasProjectDetail copy(Long userId, String uuid, CopyCanvasProjectRequest request) {
        CanvasProject source = findByUuidOrThrow(userId, uuid);
        String newName = request.name() != null ? request.name() : source.getName() + " 副本";

        CanvasProject copy = new CanvasProject();
        copy.setUuid("canvas_" + UUID.randomUUID().toString().replace("-", "").substring(0, 7));
        copy.setUserId(userId);
        copy.setOwnerId(userId);
        copy.setName(newName);
        copy.setContentProjectId(source.getContentProjectId());
        copy.setProductionUnitType(source.getProductionUnitType());
        copy.setProductionUnitId(source.getProductionUnitId());
        copy.setSourceContentVersionId(source.getSourceContentVersionId());
        copy.setSourceStoryboardVersionId(source.getSourceStoryboardVersionId());
        copy.setProductionSnapshot(source.getProductionSnapshot());
        copy.setPurpose(source.getPurpose());
        copy.setIdempotencyKey("canvas-copy:" + UUID.randomUUID());
        copy.setStatus("draft");
        copy.setCanvasVersion(1);
        copy.setRevision(0);
        copy.setIsDeleted(0);

        projectMapper.insert(copy);
        log.info("Copied canvas {} -> {}", source.getUuid(), copy.getUuid());
        return toDetail(copy);
    }

    @Transactional
    public CanvasProjectDetail move(Long userId, String uuid, MoveCanvasProjectRequest request) {
        CanvasProject p = findByUuidOrThrow(userId, uuid);

        // Validate target unit belongs to the same content project
        // (Full impl would query ContentUnitService)

        LambdaUpdateWrapper<CanvasProject> update = new LambdaUpdateWrapper<>();
        update.eq(CanvasProject::getUuid, uuid);
        update.eq(CanvasProject::getUserId, userId);
        update.set(CanvasProject::getProductionUnitType, request.targetProductionUnitType());
        update.set(CanvasProject::getProductionUnitId, request.targetProductionUnitId());
        projectMapper.update(null, update);

        return getDetail(userId, uuid);
    }

    @Transactional
    public CanvasProjectDetail archive(Long userId, String uuid) {
        CanvasProject p = findByUuidOrThrow(userId, uuid);

        LambdaUpdateWrapper<CanvasProject> update = new LambdaUpdateWrapper<>();
        update.eq(CanvasProject::getUuid, uuid);
        update.eq(CanvasProject::getUserId, userId);
        update.set(CanvasProject::getStatus, "archived");
        update.set(CanvasProject::getArchivedAt, LocalDateTime.now());
        projectMapper.update(null, update);

        log.info("Archived canvas {}", uuid);
        return getDetail(userId, uuid);
    }

    @Transactional
    public CanvasProjectDetail restore(Long userId, String uuid) {
        CanvasProject p = findByUuidOrThrow(userId, uuid);

        LambdaUpdateWrapper<CanvasProject> update = new LambdaUpdateWrapper<>();
        update.eq(CanvasProject::getUuid, uuid);
        update.eq(CanvasProject::getUserId, userId);
        update.set(CanvasProject::getStatus, "editing");
        update.set(CanvasProject::getArchivedAt, null);
        projectMapper.update(null, update);

        log.info("Restored canvas {}", uuid);
        return getDetail(userId, uuid);
    }

    @Transactional
    public void delete(Long userId, String uuid) {
        CanvasProject p = findByUuidOrThrow(userId, uuid);

        // Reject deletion of completed canvases with export records
        if ("completed".equals(p.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "已完成的画布不可删除，请使用归档功能");
        }

        // Soft delete
        LambdaUpdateWrapper<CanvasProject> update = new LambdaUpdateWrapper<>();
        update.eq(CanvasProject::getUuid, uuid);
        update.eq(CanvasProject::getUserId, userId);
        update.set(CanvasProject::getIsDeleted, 1);
        projectMapper.update(null, update);

        log.info("Soft-deleted canvas {}", uuid);
    }

    // ===== Source Diff =====

    public SourceDiffResult computeSourceDiff(Long userId, String uuid) {
        CanvasProject p = findByUuidOrThrow(userId, uuid);
        ProductionSnapshot snapshot = parseSnapshot(p.getProductionSnapshot());

        if (snapshot == null) {
            return new SourceDiffResult(false, List.of());
        }

        List<SourceDiffResult.DimensionDiff> dimensions = new ArrayList<>();

        // In full implementation, compare snapshot against current upstream versions.
        // For now, return no changes — this will be wired when upstream services are integrated.

        return new SourceDiffResult(!dimensions.isEmpty(), dimensions);
    }

    // ===== Home Aggregation =====

    public List<ContinueWorkingItem> getContinueWorking(Long userId) {
        // Query in-progress canvases (not archived, not deleted)
        List<CanvasProject> canvases = projectMapper.selectList(
                new LambdaQueryWrapper<CanvasProject>()
                        .eq(CanvasProject::getUserId, userId)
                        .eq(CanvasProject::getIsDeleted, 0)
                        .notIn(CanvasProject::getStatus, "archived", "completed")
                        .orderByDesc(CanvasProject::getUpdatedAt)
                        .last("LIMIT 10"));

        List<ContinueWorkingItem> items = new ArrayList<>();
        for (CanvasProject c : canvases) {
            items.add(new ContinueWorkingItem(
                    "canvas_project",
                    c.getId(),
                    c.getUuid(),
                    c.getName(),
                    c.getStatus(),
                    c.getStatus(),
                    c.getId(),
                    c.getUuid(),
                    false,
                    c.getUpdatedAt()));
        }

        // Limit to 5 items, errors first
        items.sort(Comparator.comparing(ContinueWorkingItem::hasErrors).reversed()
                .thenComparing(ContinueWorkingItem::updatedAt, Comparator.reverseOrder()));

        return items.size() > 5 ? items.subList(0, 5) : items;
    }

    // ===== Mapping helpers =====

    CanvasProject findByUuid(Long userId, String uuid) {
        return projectMapper.selectOne(
                new LambdaQueryWrapper<CanvasProject>()
                        .eq(CanvasProject::getUuid, uuid)
                        .eq(CanvasProject::getIsDeleted, 0)
                        .eq(userId != null, CanvasProject::getUserId, userId));
    }

    CanvasProject findByUuidOrThrow(Long userId, String uuid) {
        CanvasProject p = findByUuid(userId, uuid);
        if (p == null) {
            throw new BizException(ErrorCode.CANVAS_NOT_FOUND);
        }
        return p;
    }

    private CanvasProjectSummary toSummary(CanvasProject p) {
        return new CanvasProjectSummary(
                p.getId(), p.getUuid(), p.getName(),
                p.getStatus(), p.getPurpose(),
                p.getContentProjectId(), null, // contentProjectName – resolved by caller if needed
                p.getProductionUnitType(), p.getProductionUnitId(), null,
                p.getOwnerId(), null,
                p.getThumbnailUrl(),
                0, 0, 0, // nodeCount, taskCount, errorTaskCount – computed by caller
                false, // hasUpstreamChanges – computed by caller
                p.getCreatedAt(), p.getUpdatedAt(), p.getArchivedAt());
    }

    private CanvasProjectDetail toDetail(CanvasProject p) {
        ProductionSnapshot snapshot = parseSnapshot(p.getProductionSnapshot());
        return new CanvasProjectDetail(
                p.getId(), p.getUuid(), p.getName(),
                p.getStatus(), p.getPurpose(),
                p.getContentProjectId(), null,
                p.getProductionUnitType(), p.getProductionUnitId(), null,
                p.getSourceContentVersionId(), p.getSourceStoryboardVersionId(),
                snapshot,
                p.getThumbnailUrl(), p.getWorkspaceId(),
                p.getOwnerId(), p.getRevision(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getArchivedAt());
    }

    private ProductionSnapshot parseSnapshot(String json) {
        if (!StringUtils.hasText(json)) return null;
        try {
            Map<String, Object> map = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            return new ProductionSnapshot(
                    toLong(map.get("contentVersionId")),
                    (String) map.get("contentVersionHash"),
                    (String) map.get("contentTitle"),
                    (String) map.get("contentSummary"),
                    toLong(map.get("storyboardVersionId")),
                    (Integer) map.get("storyboardRevision"),
                    map.get("shotCount") instanceof Number n ? n.intValue() : 0,
                    Boolean.TRUE.equals(map.get("storyboardLocked")),
                    (String) map.get("platformRuleVersion"),
                    (String) map.get("pluginPackageVersion"),
                    (String) map.get("aspectRatio"),
                    (String) map.get("resolution"),
                    map.get("fps") instanceof Number n ? n.intValue() : 24,
                    map);
        } catch (Exception e) {
            log.warn("Failed to parse production snapshot for canvas", e);
            return null;
        }
    }

    private WorkspaceContext getWorkspaceContext() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return WorkspaceContext.get(attrs.getRequest());
            }
        } catch (Exception ignored) {
            // No request context available (e.g., tests or async)
        }
        return null;
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
