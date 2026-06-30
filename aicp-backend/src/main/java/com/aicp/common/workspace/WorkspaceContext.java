package com.aicp.common.workspace;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * Request-scoped workspace identity resolved by {@link WorkspaceContextFilter}.
 * Stored as a request attribute and accessible throughout the request lifecycle.
 */
public record WorkspaceContext(
        String workspaceId,
        String workspaceType,
        Long userId,
        Set<String> permissions) {

    public static final String REQUEST_ATTRIBUTE = "workspaceContext";

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
     * Retrieve the WorkspaceContext from the current request, or null if not set.
     */
    public static WorkspaceContext get(HttpServletRequest request) {
        return (WorkspaceContext) request.getAttribute(REQUEST_ATTRIBUTE);
    }
}
