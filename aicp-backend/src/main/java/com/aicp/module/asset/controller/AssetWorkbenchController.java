package com.aicp.module.asset.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.asset.dto.AssetWorkbenchRequests.*;
import com.aicp.module.asset.dto.AssetWorkbenchViews.*;
import com.aicp.module.asset.service.AssetHistoryQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetWorkbenchController {

    private final AssetHistoryQueryService queryService;

    /**
     * List content projects with asset counts for the project tree sidebar.
     */
    @GetMapping("/workbench/projects")
    public ApiResponse<List<ProjectSummary>> listProjects(HttpServletRequest request) {
        WorkspaceContext ctx = requireContext(request);
        return ApiResponse.success(queryService.queryProjects(ctx));
    }

    /**
     * Unified task + asset query with facets and pagination.
     */
    @GetMapping("/history/records")
    public ApiResponse<PageResult<RecordSummary>> queryRecords(
            HttpServletRequest request,
            @Valid RecordQuery query) {
        WorkspaceContext ctx = requireContext(request);
        return ApiResponse.success(queryService.queryRecords(ctx, query));
    }

    /**
     * Detail for a single task or asset identified by kind + UUID.
     */
    @GetMapping("/history/records/{recordKind}/{recordUuid}")
    public ApiResponse<RecordDetail> getRecordDetail(
            HttpServletRequest request,
            @PathVariable String recordKind,
            @PathVariable String recordUuid) {
        WorkspaceContext ctx = requireContext(request);
        RecordDetail detail = queryService.queryDetail(ctx, recordKind, recordUuid);
        if (detail == null) {
            throw new BizException(ErrorCode.ASSET_NOT_FOUND);
        }
        return ApiResponse.success(detail);
    }

    private WorkspaceContext requireContext(HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        if (ctx == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少Workspace上下文");
        }
        return ctx;
    }
}
