package com.aicp.module.agent.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.agent.dto.AgentConfigRequests.*;
import com.aicp.module.agent.dto.AgentConfigViews;
import com.aicp.module.agent.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentConfigController {

    private final AgentBlueprintService blueprintService;
    private final UserAgentDefinitionService definitionService;
    private final AgentVersionService versionService;
    private final AgentTestRunService testRunService;
    private final AgentBindingService bindingService;
    private final AgentConfigResolver resolver;
    private final AgentExecutionSnapshotService snapshotService;

    // ── Blueprint ──────────────────────────────────────────────

    @GetMapping("/blueprints")
    public ApiResponse<List<AgentConfigViews.BlueprintView>> listBlueprints() {
        return ApiResponse.success(blueprintService.listActive());
    }

    @GetMapping("/blueprints/{id}")
    public ApiResponse<AgentConfigViews.BlueprintView> getBlueprint(@PathVariable String id) {
        return ApiResponse.success(blueprintService.getByUuid(id));
    }

    // ── User Agent Definitions ─────────────────────────────────

    @PostMapping("/definitions")
    public ApiResponse<AgentConfigViews.DefinitionView> createDefinition(
            @Valid @RequestBody CreateDefinitionRequest request) {
        return ApiResponse.success(definitionService.create(
                SecurityUtil.requireCurrentUserId(), request));
    }

    @GetMapping("/definitions")
    public ApiResponse<List<AgentConfigViews.DefinitionListItem>> listDefinitions() {
        return ApiResponse.success(definitionService.list(
                SecurityUtil.requireCurrentUserId()));
    }

    @GetMapping("/definitions/{id}")
    public ApiResponse<AgentConfigViews.DefinitionView> getDefinition(@PathVariable String id) {
        return ApiResponse.success(definitionService.get(
                SecurityUtil.requireCurrentUserId(), id));
    }

    @PatchMapping("/definitions/{id}")
    public ApiResponse<AgentConfigViews.DefinitionView> updateDefinition(
            @PathVariable String id, @Valid @RequestBody UpdateDefinitionRequest request) {
        return ApiResponse.success(definitionService.update(
                SecurityUtil.requireCurrentUserId(), id, request));
    }

    @PostMapping("/definitions/{id}/copies")
    public ApiResponse<AgentConfigViews.DefinitionView> copyDefinition(@PathVariable String id) {
        return ApiResponse.success(definitionService.copy(
                SecurityUtil.requireCurrentUserId(), id));
    }

    @PostMapping("/definitions/{id}/archive")
    public ApiResponse<AgentConfigViews.DefinitionView> archiveDefinition(@PathVariable String id) {
        return ApiResponse.success(definitionService.archive(
                SecurityUtil.requireCurrentUserId(), id));
    }

    // ── Versions ───────────────────────────────────────────────

    @GetMapping("/definitions/{id}/versions")
    public ApiResponse<List<AgentConfigViews.VersionView>> listVersions(@PathVariable String id) {
        return ApiResponse.success(versionService.listVersions(id));
    }

    @PostMapping("/definitions/{id}/drafts")
    public ApiResponse<AgentConfigViews.VersionView> createDraft(@PathVariable String id) {
        return ApiResponse.success(versionService.createDraft(
                SecurityUtil.requireCurrentUserId(), id, new CreateDraftRequest()));
    }

    @GetMapping("/versions/{versionId}")
    public ApiResponse<AgentConfigViews.VersionView> getVersion(@PathVariable String versionId) {
        return ApiResponse.success(versionService.getVersion(versionId));
    }

    @PutMapping("/versions/{versionId}")
    public ApiResponse<AgentConfigViews.VersionView> updateVersion(
            @PathVariable String versionId, @Valid @RequestBody UpdateDraftRequest request) {
        return ApiResponse.success(versionService.updateDraft(
                SecurityUtil.requireCurrentUserId(), versionId, request));
    }

    @PostMapping("/versions/{versionId}/validate")
    public ApiResponse<AgentConfigViews.ValidateResult> validateVersion(
            @PathVariable String versionId) {
        return ApiResponse.success(versionService.validate(
                SecurityUtil.requireCurrentUserId(), versionId));
    }

    @PostMapping("/versions/{versionId}/test-runs")
    public ApiResponse<AgentConfigViews.TestRunView> testVersion(
            @PathVariable String versionId, @Valid @RequestBody TestRunRequest request) {
        return ApiResponse.success(testRunService.run(
                SecurityUtil.requireCurrentUserId(), versionId, request));
    }

    @PostMapping("/versions/{versionId}/publish")
    public ApiResponse<AgentConfigViews.VersionView> publishVersion(
            @PathVariable String versionId, @Valid @RequestBody PublishVersionRequest request) {
        return ApiResponse.success(versionService.publish(
                SecurityUtil.requireCurrentUserId(), versionId, request));
    }

    @PostMapping("/versions/{versionId}/activate")
    public ApiResponse<AgentConfigViews.VersionView> activateVersion(
            @PathVariable String versionId) {
        return ApiResponse.success(versionService.activate(
                SecurityUtil.requireCurrentUserId(), versionId));
    }

    // ── Test Runs ──────────────────────────────────────────────

    @GetMapping("/test-runs/{id}")
    public ApiResponse<AgentConfigViews.TestRunView> getTestRun(@PathVariable String id) {
        return ApiResponse.success(testRunService.getTestRun(id));
    }

    // ── User Bindings ──────────────────────────────────────────

    @GetMapping("/user-bindings")
    public ApiResponse<List<AgentConfigViews.BindingView>> listUserBindings() {
        return ApiResponse.success(bindingService.listUserBindings(
                SecurityUtil.requireCurrentUserId()));
    }

    @PutMapping("/user-bindings/{roleType}")
    public ApiResponse<AgentConfigViews.BindingView> setUserBinding(
            @PathVariable String roleType, @Valid @RequestBody BindVersionRequest request) {
        return ApiResponse.success(bindingService.setUserBinding(
                SecurityUtil.requireCurrentUserId(), roleType, request));
    }

    @DeleteMapping("/user-bindings/{roleType}")
    public ApiResponse<Void> deleteUserBinding(@PathVariable String roleType) {
        bindingService.deleteUserBinding(SecurityUtil.requireCurrentUserId(), roleType);
        return ApiResponse.success(null);
    }

    // ── Resolve Preview ────────────────────────────────────────

    @PostMapping("/resolve-preview")
    public ApiResponse<AgentConfigViews.ResolvedConfigView> resolvePreview(
            @Valid @RequestBody ResolvePreviewRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(resolver.resolve(
                userId, request.projectId(), request.roleType(),
                request.temporaryOverrides() != null ? request.temporaryOverrides() : Map.of()));
    }

    // ── Execution Snapshots ────────────────────────────────────

    @GetMapping("/execution-snapshots/{id}")
    public ApiResponse<AgentConfigViews.SnapshotView> getSnapshot(@PathVariable String id) {
        return ApiResponse.success(snapshotService.getSnapshot(id));
    }
}
