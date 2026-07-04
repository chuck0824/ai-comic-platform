package com.aicp.common.workspace;

import java.util.Set;

/**
 * A scoped permission grant. Each grant ties a permission to a data scope
 * (WORKSPACE, DEPARTMENT, or SELF) and an optional set of scope target IDs.
 */
public record PermissionGrant(
        String permission,
        String scope,
        Set<String> scopeIds) {
}
