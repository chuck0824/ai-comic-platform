package com.aicp.common.workspace;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Resolves a trusted WorkspaceContext by combining the authenticated user
 * identity with the 3001 membership verification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceAccessService {

    private final AccountCenterPermissionClient client;

    /**
     * Resolve the workspace context for an authenticated user.
     *
     * @param workspaceId         the workspace ID from the X-Workspace-Id header
     * @param bearerToken         the original Authorization header
     * @param authenticatedUserId the user ID from JWT (SecurityContextHolder)
     * @return the resolved WorkspaceContext
     * @throws BizException if membership cannot be confirmed or user mismatch
     */
    public WorkspaceContext resolve(String workspaceId, String bearerToken, Long authenticatedUserId) {
        // Step 1: call 3001 to verify workspace membership
        AccountCenterPermissionClient.MembershipResponse membership;
        try {
            membership = client.membership(workspaceId, bearerToken);
        } catch (AccountCenterPermissionClient.UpstreamUnavailableException e) {
            log.warn("账户中心不可用: workspace={}, user={}", workspaceId, authenticatedUserId);
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "账户中心暂不可用，请稍后重试");
        }

        // Step 2: workspace not found or user is not an active member → unified 404
        if (membership == null) {
            log.debug("Workspace not found or no membership: workspace={}, user={}", workspaceId, authenticatedUserId);
            throw new BizException(ErrorCode.ASSET_NOT_FOUND);
        }

        // Step 3: verify the returned user_id matches the authenticated user
        if (membership.userId() != authenticatedUserId) {
            log.warn("用户身份不匹配: workspace={}, authenticated={}, returned={}",
                    workspaceId, authenticatedUserId, membership.userId());
            throw new BizException(ErrorCode.ASSET_PERMISSION_DENIED);
        }

        // Step 4: build the trusted context with enriched fields
        Set<String> permissions = membership.permissions() != null
                ? new LinkedHashSet<>(membership.permissions())
                : Collections.emptySet();

        List<PermissionGrant> grants = membership.permissionGrants() != null
                ? membership.permissionGrants()
                : Collections.emptyList();

        log.debug("Workspace context resolved: workspace={}, type={}, user={}, dept={}, roles={}, permissions={}",
                workspaceId, membership.workspaceType(), authenticatedUserId,
                membership.departmentId(), membership.roles(), permissions);

        return new WorkspaceContext(
                membership.workspaceId(),
                membership.workspaceType(),
                authenticatedUserId,
                membership.departmentId() != null ? membership.departmentId() : "",
                permissions,
                grants);
    }
}
