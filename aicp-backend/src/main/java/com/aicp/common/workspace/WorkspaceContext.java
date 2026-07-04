package com.aicp.common.workspace;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Request-scoped workspace identity resolved by {@link WorkspaceContextFilter}.
 * Stored as a request attribute and accessible throughout the request lifecycle.
 */
public record WorkspaceContext(
        String workspaceId,
        String workspaceType,
        Long userId,
        String departmentId,
        Set<String> permissions,
        List<PermissionGrant> grants) {

    public static final String REQUEST_ATTRIBUTE = "workspaceContext";

    /**
     * Compact constructor for backward compatibility when grants are not needed.
     */
    public WorkspaceContext(String workspaceId, String workspaceType, Long userId,
                            Set<String> permissions) {
        this(workspaceId, workspaceType, userId, "", permissions, Collections.emptyList());
    }

    /**
     * Require that the current context holds a specific permission.
     *
     * @throws BizException with {@link ErrorCode#ASSET_PERMISSION_DENIED} if the
     *                      permission is not present.
     */
    public void require(String permission) {
        if (permissions == null || !permissions.contains(permission)) {
            throw new BizException(ErrorCode.ASSET_PERMISSION_DENIED);
        }
    }

    /**
     * Check whether the current context holds a specific permission.
     */
    public boolean has(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    /**
     * Check whether the current context can perform the given permission on the
     * specified target. Respects WORKSPACE, DEPARTMENT, and SELF scope levels.
     *
     * @param permission         the permission to check
     * @param targetDepartmentId the department the target belongs to (empty for workspace-level)
     * @param targetUserId       the user ID of the target (for SELF scope)
     * @return true if the permission is granted with sufficient scope
     */
    public boolean canAccess(String permission, String targetDepartmentId, Long targetUserId) {
        if (!has(permission)) {
            return false;
        }
        if (grants == null || grants.isEmpty()) {
            // No scoped grants defined — full workspace access
            return true;
        }
        for (PermissionGrant grant : grants) {
            if (!permission.equals(grant.permission())) {
                continue;
            }
            return switch (grant.scope()) {
                case "WORKSPACE" -> true;
                case "DEPARTMENT" -> grant.scopeIds() != null
                        && targetDepartmentId != null
                        && grant.scopeIds().contains(targetDepartmentId);
                case "SELF" -> targetUserId != null && targetUserId.equals(userId);
                default -> false;
            };
        }
        return false;
    }

    /**
     * Retrieve the WorkspaceContext from the current request, or null if not set.
     */
    public static WorkspaceContext get(HttpServletRequest request) {
        return (WorkspaceContext) request.getAttribute(REQUEST_ATTRIBUTE);
    }
}
