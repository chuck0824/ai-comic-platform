package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.dto.PageResult;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.service.ContentProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/content-projects")
@RequiredArgsConstructor
public class ContentProjectController {

    private final ContentProjectService projects;

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
}
