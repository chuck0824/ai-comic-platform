package com.aicp.module.enterprise.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.enterprise.service.EnterpriseAccountFacade;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Enterprise BFF controller. All endpoints require a valid enterprise
 * WorkspaceContext (resolved by WorkspaceContextFilter from the
 * X-Workspace-Id header). The Workspace ID in the request body is never
 * trusted — it is only accepted as a consistency hint.
 */
@RestController
@RequestMapping("/api/v1/enterprise")
@RequiredArgsConstructor
public class EnterpriseController {

    private final EnterpriseAccountFacade facade;

    private WorkspaceContext ctx(HttpServletRequest request) {
        return WorkspaceContext.get(request);
    }

    private String bearer(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.AUTHORIZATION);
    }

    // ─── Context ────────────────────────────────────────────────────────────

    @GetMapping("/context")
    public ApiResponse<?> getContext(HttpServletRequest request) {
        return ApiResponse.success(facade.getContext(ctx(request), bearer(request)));
    }

    // ─── Departments ────────────────────────────────────────────────────────

    @GetMapping("/departments")
    public ResponseEntity<String> listDepartments(HttpServletRequest request) {
        return toResponse(facade.listDepartments(ctx(request), bearer(request)));
    }

    @PostMapping("/departments")
    public ResponseEntity<String> createDepartment(@RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        return toResponse(facade.createDepartment(ctx(request), body, bearer(request)));
    }

    @PatchMapping("/departments/{departmentId}")
    public ResponseEntity<String> updateDepartment(@PathVariable String departmentId,
                                                    @RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        return toResponse(facade.updateDepartment(ctx(request), departmentId, body, bearer(request)));
    }

    @DeleteMapping("/departments/{departmentId}")
    public ResponseEntity<String> deleteDepartment(@PathVariable String departmentId,
                                                    HttpServletRequest request) {
        return toResponse(facade.deleteDepartment(ctx(request), departmentId, bearer(request)));
    }

    // ─── Members ────────────────────────────────────────────────────────────

    @GetMapping("/members")
    public ResponseEntity<String> listMembers(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               HttpServletRequest request) {
        return toResponse(facade.listMembers(ctx(request), page, size, bearer(request)));
    }

    @PatchMapping("/members/{memberId}")
    public ResponseEntity<String> updateMember(@PathVariable Long memberId,
                                                @RequestBody Map<String, Object> body,
                                                HttpServletRequest request) {
        return toResponse(facade.updateMember(ctx(request), memberId, body, bearer(request)));
    }

    // ─── Invitations ────────────────────────────────────────────────────────

    @PostMapping("/invitations")
    public ResponseEntity<String> createInvitation(@RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        return toResponse(facade.createInvitation(ctx(request), body, bearer(request)));
    }

    // ─── Roles ──────────────────────────────────────────────────────────────

    @GetMapping("/roles")
    public ResponseEntity<String> listRoles(HttpServletRequest request) {
        return toResponse(facade.listRoles(ctx(request), bearer(request)));
    }

    @PostMapping("/roles")
    public ResponseEntity<String> createRole(@RequestBody Map<String, Object> body,
                                              HttpServletRequest request) {
        return toResponse(facade.createRole(ctx(request), body, bearer(request)));
    }

    @PatchMapping("/roles/{roleId}")
    public ResponseEntity<String> updateRole(@PathVariable String roleId,
                                              @RequestBody Object body,
                                              HttpServletRequest request) {
        return toResponse(facade.updateRole(ctx(request), roleId, body, bearer(request)));
    }

    // ─── Billing ────────────────────────────────────────────────────────────

    @GetMapping("/billing-summary")
    public ResponseEntity<String> getBillingSummary(HttpServletRequest request) {
        return toResponse(facade.getBillingSummary(ctx(request), bearer(request)));
    }

    // ─── Helper ─────────────────────────────────────────────────────────────

    private ResponseEntity<String> toResponse(JsonNode node) {
        if (node == null) {
            return ResponseEntity.status(502).body("{\"success\":false,\"message\":\"upstream unavailable\"}");
        }
        if (node.has("success") && !node.path("success").asBoolean()) {
            String message = node.has("message") ? node.path("message").asText() : "request failed";
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + message + "\"}");
        }
        return ResponseEntity.ok(node.toString());
    }
}
