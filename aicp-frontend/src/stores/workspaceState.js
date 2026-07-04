/**
 * Framework-free workspace transition rules.
 * Pure functions with no side effects — callers own localStorage and routing.
 */

/**
 * Commit a workspace selection. Returns the new workspace identity to persist.
 * Returns the candidate on success, current on failure (preserve old workspace).
 */
export function commitWorkspaceSelection(current, candidate, membership) {
  if (!membership || !membership.workspace_id) {
    return current // membership loading failed — keep old workspace
  }
  return {
    workspaceId: membership.workspace_id,
    workspaceType: membership.workspace_type,
    departmentId: membership.department_id || '',
    roles: membership.roles || [],
    permissions: membership.permissions || [],
    permissionGrants: membership.permission_grants || []
  }
}

/**
 * Build a personal workspace fallback from a user ID.
 */
export function personalFallback(userId) {
  return {
    workspaceId: `personal_${userId}`,
    workspaceType: 'personal',
    departmentId: '',
    roles: [],
    permissions: [],
    permissionGrants: []
  }
}

/**
 * Keys that must be cleared when switching workspace.
 */
export const WORKSPACE_SCOPED_CACHE_KEYS = [
  'enterprise_context',
  'enterprise_departments',
  'enterprise_members',
  'enterprise_roles'
]

/**
 * Clear all workspace-scoped cache entries.
 */
export function clearWorkspaceCache() {
  WORKSPACE_SCOPED_CACHE_KEYS.forEach(k => localStorage.removeItem(k))
}
