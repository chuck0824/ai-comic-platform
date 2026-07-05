package com.aicp.module.agent.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.agent.dto.AgentConfigRequests.BindVersionRequest;
import com.aicp.module.agent.dto.AgentConfigViews;
import com.aicp.module.agent.service.AgentBindingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectAgentBindingController {

    private final AgentBindingService bindingService;

    @GetMapping("/{projectId}/agent-bindings")
    public ApiResponse<List<AgentConfigViews.BindingView>> listProjectBindings(
            @PathVariable Long projectId) {
        return ApiResponse.success(bindingService.listProjectBindings(projectId));
    }

    @PutMapping("/{projectId}/agent-bindings/{roleType}")
    public ApiResponse<AgentConfigViews.BindingView> setProjectBinding(
            @PathVariable Long projectId, @PathVariable String roleType,
            @Valid @RequestBody BindVersionRequest request) {
        return ApiResponse.success(bindingService.setProjectBinding(
                SecurityUtil.requireCurrentUserId(), projectId, roleType, request));
    }

    @DeleteMapping("/{projectId}/agent-bindings/{roleType}")
    public ApiResponse<Void> deleteProjectBinding(
            @PathVariable Long projectId, @PathVariable String roleType) {
        bindingService.deleteProjectBinding(projectId, roleType);
        return ApiResponse.success(null);
    }
}
