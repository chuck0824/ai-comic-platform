package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.dto.PageResult;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.canvas.dto.CanvasProjectRequests.CanvasProjectQuery;
import com.aicp.module.canvas.dto.CanvasProjectViews.CanvasProjectSummary;
import com.aicp.module.canvas.service.CanvasProjectManagementService;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.service.ContentProjectService;
import com.aicp.module.contentproject.service.ContentUnitService;
import com.aicp.module.contentproject.service.LegacyProjectProjectionService;
import com.aicp.module.contentproject.service.ProjectWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/content-projects")
@RequiredArgsConstructor
public class ContentProjectController {

    private final ContentProjectService projects;
    private final ProjectWorkflowService workflow;
    private final ContentUnitService unitService;
    private final LegacyProjectProjectionService legacy;
    private final CanvasProjectManagementService canvasProjects;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectDetail>> create(@Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(projects.create(SecurityUtil.requireCurrentUserId(), request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectDetail> get(@PathVariable Long id) {
        return ApiResponse.success(projects.get(SecurityUtil.requireCurrentUserId(), id));
    }

    @GetMapping
    public ApiResponse<PageResult<ProjectSummary>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        var result = projects.list(SecurityUtil.requireCurrentUserId(), page, pageSize);
        return ApiResponse.success(PageResult.of(result.items(), page, pageSize, result.total()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ProjectDetail> update(@PathVariable Long id, @RequestBody UpdateProjectRequest request) {
        return ApiResponse.success(projects.update(SecurityUtil.requireCurrentUserId(), id, request));
    }

    @PutMapping("/{id}/resume-position")
    public ApiResponse<ProjectDetail> saveResumePosition(@PathVariable Long id, @RequestBody ResumePositionRequest request) {
        return ApiResponse.success(projects.saveResumePosition(SecurityUtil.requireCurrentUserId(), id, request));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<MemberView>> listMembers(@PathVariable Long id) {
        return ApiResponse.success(projects.listMembers(SecurityUtil.requireCurrentUserId(), id));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ApiResponse<MemberView>> addMember(@PathVariable Long id, @RequestBody CreateMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(projects.addMember(SecurityUtil.requireCurrentUserId(), id, request)));
    }

    @PatchMapping("/{id}/members/{memberId}")
    public ApiResponse<MemberView> updateMember(@PathVariable Long id, @PathVariable Long memberId, @RequestBody UpdateMemberRequest request) {
        return ApiResponse.success(projects.updateMember(SecurityUtil.requireCurrentUserId(), id, memberId, request));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    public ApiResponse<Void> removeMember(@PathVariable Long id, @PathVariable Long memberId) {
        projects.removeMember(SecurityUtil.requireCurrentUserId(), id, memberId);
        return ApiResponse.success();
    }

    // ===== Workflow =====

    @GetMapping("/{id}/workflow")
    public ApiResponse<WorkflowView> getWorkflow(@PathVariable Long id) {
        return ApiResponse.success(workflow.calculate(id));
    }

    @PostMapping("/{id}/parameter-versions")
    public ApiResponse<ParameterVersionView> appendParameters(@PathVariable Long id,
                                                              @RequestBody AppendParameterRequest request) {
        return ApiResponse.success(workflow.appendParameters(
                SecurityUtil.requireCurrentUserId(), id, request));
    }

    @GetMapping("/{id}/parameter-versions")
    public ApiResponse<List<ParameterVersionView>> listParameterVersions(@PathVariable Long id) {
        return ApiResponse.success(workflow.listParameterVersions(id));
    }

    @PutMapping("/{id}/storyboard-intent")
    public ApiResponse<Void> setStoryboardIntent(@PathVariable Long id,
                                                  @RequestBody StoryboardIntentRequest request) {
        workflow.setStoryboardIntent(id, request);
        return ApiResponse.success();
    }

    // ===== Canvas Projects (under content project) =====

    @GetMapping("/{projectId}/canvas-projects")
    public ApiResponse<PageResult<CanvasProjectSummary>> listCanvasProjects(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        var query = new CanvasProjectQuery(page, pageSize, status, null, projectId, keyword);
        return ApiResponse.success(canvasProjects.listByContentProject(
                SecurityUtil.requireCurrentUserId(), projectId, query));
    }

    // ===== Content Units (under project) =====

    @GetMapping("/{projectId}/content-units")
    public ApiResponse<List<ContentUnitView>> listUnits(@PathVariable Long projectId) {
        return ApiResponse.success(unitService.listUnits(projectId));
    }

    @PostMapping("/{projectId}/content-units")
    public ResponseEntity<ApiResponse<ContentUnitView>> createUnit(@PathVariable Long projectId,
                                                                    @RequestBody Map<String, Object> body) {
        String unitType = (String) body.get("unit_type");
        int displayNo = body.containsKey("display_no") ? ((Number) body.get("display_no")).intValue() : 1;
        String title = (String) body.getOrDefault("title", "");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(unitService.createUnit(
                        SecurityUtil.requireCurrentUserId(), projectId, unitType, displayNo, title)));
    }

    // ===== Legacy Backfill =====

    @PostMapping("/backfill-legacy")
    public ApiResponse<Map<String, Object>> backfillLegacy() {
        var result = legacy.backfill(SecurityUtil.requireCurrentUserId());
        return ApiResponse.success(Map.of(
                "projects", result.projects(),
                "units", result.units(),
                "versions", result.versions(),
                "skipped", result.skipped()));
    }
}
