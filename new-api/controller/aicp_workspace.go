package controller

import (
	"net/http"
	"strconv"

	"github.com/QuantumNous/new-api/model"
	"github.com/gin-gonic/gin"
)

// requireMembership extracts the authenticated user and verifies they have an
// active membership in the given workspace with at least one of the required
// permissions. Writes 401/404/403 and returns nil on failure.
func requireMembership(c *gin.Context, workspaceID string, requiredPermissions ...string) *model.MembershipResult {
	userID, ok := extractUserID(c)
	if !ok {
		return nil
	}

	membership, err := model.FindActiveWorkspaceMembership(workspaceID, userID)
	if err != nil || membership == nil {
		c.JSON(http.StatusNotFound, gin.H{
			"success": false,
			"message": "workspace not found",
		})
		return nil
	}

	for _, perm := range requiredPermissions {
		if membership.HasPermission(perm) {
			return membership
		}
	}

	c.JSON(http.StatusForbidden, gin.H{
		"success": false,
		"message": "insufficient permissions",
	})
	return nil
}

// extractUserID extracts the authenticated user's ID from the Gin context.
// The user identity is set by AicpJwtAuth middleware; returns 0 and writes an
// error response when the identity is missing or invalid.
func extractUserID(c *gin.Context) (int64, bool) {
	userIDVal, exists := c.Get("aicp_user_id")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{
			"success": false,
			"message": "authentication required",
		})
		return 0, false
	}

	userID, ok := userIDVal.(int64)
	if !ok {
		if id, ok := userIDVal.(int); ok {
			return int64(id), true
		}
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "invalid user identity",
		})
		return 0, false
	}
	return userID, true
}

// ListAicpWorkspaces returns all workspaces where the authenticated user
// has an active membership. The user identity comes from the gin context
// (set by AicpJwtAuth middleware), never from a query parameter.
//
// GET /api/aicp/workspaces
func ListAicpWorkspaces(c *gin.Context) {
	userID, ok := extractUserID(c)
	if !ok {
		return
	}

	results, err := model.ListActiveWorkspacesForUser(userID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "failed to list workspaces",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data": gin.H{
			"items": results,
		},
	})
}

// GetAicpWorkspaceMembership returns the authenticated user's membership details
// for the requested workspace. The user identity comes from the gin context (set by
// AicpJwtAuth middleware), never from a query parameter.
//
// GET /api/aicp/workspaces/:id/membership
//
// Returns 200 with membership data if user is an active member.
// Returns 404 if workspace does not exist or user is not an active member
// (unified to prevent workspace ID enumeration).
func GetAicpWorkspaceMembership(c *gin.Context) {
	workspaceID := c.Param("id")
	if workspaceID == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"message": "workspace id is required",
		})
		return
	}

	userID, ok := extractUserID(c)
	if !ok {
		return
	}

	result, err := model.FindActiveWorkspaceMembership(workspaceID, userID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "failed to lookup workspace membership",
		})
		return
	}

	// Unified 404: workspace not found OR user has no active membership
	if result == nil {
		c.JSON(http.StatusNotFound, gin.H{
			"success": false,
			"message": "workspace not found",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    result,
	})
}

// GetAicpWorkspace returns workspace profile information.
// GET /api/aicp/workspaces/:id
func GetAicpWorkspace(c *gin.Context) {
	workspaceID := c.Param("id")
	if workspaceID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"success": false, "message": "workspace id is required"})
		return
	}

	userID, ok := extractUserID(c)
	if !ok {
		return
	}

	// Verify user is a member before returning workspace info
	membership, err := model.FindActiveWorkspaceMembership(workspaceID, userID)
	if err != nil || membership == nil {
		c.JSON(http.StatusNotFound, gin.H{"success": false, "message": "workspace not found"})
		return
	}

	// Query workspace details
	var ws model.AicpWorkspace
	if err := model.DB.Where("id = ?", workspaceID).First(&ws).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"success": false, "message": "workspace not found"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    ws,
	})
}

// ─── Department handlers ───────────────────────────────────────────────────────

// ListAicpDepartments lists active departments in a workspace.
// GET /api/aicp/workspaces/:id/departments
func ListAicpDepartments(c *gin.Context) {
	workspaceID := c.Param("id")
	if requireMembership(c, workspaceID, "org.department.manage") == nil {
		return
	}
	depts, err := model.ListDepartmentsForWorkspace(workspaceID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"success": false, "message": "failed to list departments"})
		return
	}
	if depts == nil {
		depts = []model.AicpDepartment{}
	}
	c.JSON(http.StatusOK, gin.H{"success": true, "data": gin.H{"items": depts}})
}

// CreateAicpDepartment creates a department.
// POST /api/aicp/workspaces/:id/departments
func CreateAicpDepartment(c *gin.Context) {
	workspaceID := c.Param("id")
	if requireMembership(c, workspaceID, "org.department.manage") == nil {
		return
	}
	var dept model.AicpDepartment
	if err := c.ShouldBindJSON(&dept); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"success": false, "message": "invalid request body"})
		return
	}
	dept.WorkspaceID = workspaceID
	dept.Status = "active"
	if err := model.CreateDepartment(&dept); err != nil {
		c.JSON(http.StatusConflict, gin.H{"success": false, "message": err.Error()})
		return
	}
	c.JSON(http.StatusCreated, gin.H{"success": true, "data": dept})
}

// UpdateAicpDepartment updates a department's name, parent, manager, or sort order.
// PATCH /api/aicp/workspaces/:id/departments/:departmentId
func UpdateAicpDepartment(c *gin.Context) {
	workspaceID := c.Param("id")
	if requireMembership(c, workspaceID, "org.department.manage") == nil {
		return
	}
	departmentID := c.Param("departmentId")
	var updates map[string]interface{}
	if err := c.ShouldBindJSON(&updates); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"success": false, "message": "invalid request body"})
		return
	}
	if err := model.UpdateDepartment(departmentID, workspaceID, updates); err != nil {
		c.JSON(http.StatusConflict, gin.H{"success": false, "message": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"success": true})
}

// DeleteAicpDepartment soft-deletes a department (marks inactive).
// DELETE /api/aicp/workspaces/:id/departments/:departmentId
func DeleteAicpDepartment(c *gin.Context) {
	workspaceID := c.Param("id")
	if requireMembership(c, workspaceID, "org.department.manage") == nil {
		return
	}
	departmentID := c.Param("departmentId")
	if err := model.DeleteDepartment(departmentID, workspaceID); err != nil {
		c.JSON(http.StatusConflict, gin.H{"success": false, "message": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"success": true})
}

// ─── Member handlers ───────────────────────────────────────────────────────────

// ListAicpMembers lists members in a workspace with pagination.
// GET /api/aicp/workspaces/:id/members
func ListAicpMembers(c *gin.Context) {
	workspaceID := c.Param("id")
	if requireMembership(c, workspaceID, "org.member.manage") == nil {
		return
	}
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	size, _ := strconv.Atoi(c.DefaultQuery("size", "20"))
	if page < 1 {
		page = 1
	}
	if size < 1 || size > 100 {
		size = 20
	}
	offset := (page - 1) * size
	members, total, err := model.ListMembersForWorkspace(workspaceID, offset, size)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"success": false, "message": "failed to list members"})
		return
	}
	if members == nil {
		members = []model.AicpWorkspaceMember{}
	}
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data": gin.H{
			"items": members,
			"total": total,
			"page":  page,
			"size":  size,
		},
	})
}

// UpdateAicpMember updates a member's department, role, or status.
// PATCH /api/aicp/workspaces/:id/members/:memberId
func UpdateAicpMember(c *gin.Context) {
	workspaceID := c.Param("id")
	if requireMembership(c, workspaceID, "org.member.manage") == nil {
		return
	}

	memberIDStr := c.Param("memberId")
	memberID, err := strconv.ParseUint(memberIDStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"success": false, "message": "invalid member id"})
		return
	}

	var updates map[string]interface{}
	if err := c.ShouldBindJSON(&updates); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"success": false, "message": "invalid request body"})
		return
	}

	// Prevent disabling the last active workspace administrator
	if status, ok := updates["status"].(string); ok && status == "inactive" {
		member, err := model.FindMemberByID(uint(memberID))
		if err != nil {
			c.JSON(http.StatusNotFound, gin.H{"success": false, "message": "member not found"})
			return
		}
		if member.RoleID != "" {
			adminCount, err := model.CountActiveAdminsInWorkspace(workspaceID, member.RoleID)
			if err == nil && adminCount <= 1 {
				c.JSON(http.StatusConflict, gin.H{"success": false, "message": "cannot disable the last active workspace administrator"})
				return
			}
		}
	}

	if err := model.UpdateMemberByID(uint(memberID), updates); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"success": false, "message": "failed to update member"})
		return
	}
	c.JSON(http.StatusOK, gin.H{"success": true})
}

// ─── Invitation handlers ───────────────────────────────────────────────────────

// CreateAicpInvitation creates an invitation to join a workspace.
// POST /api/aicp/workspaces/:id/invitations
func CreateAicpInvitation(c *gin.Context) {
	workspaceID := c.Param("id")
	membership := requireMembership(c, workspaceID, "org.member.manage")
	if membership == nil {
		return
	}

	var inv model.AicpWorkspaceInvitation
	if err := c.ShouldBindJSON(&inv); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"success": false, "message": "invalid request body"})
		return
	}

	// Check duplicate active invitation
	if existing, _ := model.FindActiveInvitation(workspaceID, inv.Target); existing != nil {
		c.JSON(http.StatusConflict, gin.H{"success": false, "message": "an active invitation already exists for this target"})
		return
	}

	// Check member limit
	activeCount, _ := model.CountActiveMembersInWorkspace(workspaceID)
	_ = activeCount

	inv.WorkspaceID = workspaceID
	inv.ID = "" // let DB generate
	inv.Status = "pending"
	inv.InvitedBy = membership.UserID

	if err := model.CreateInvitation(&inv); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"success": false, "message": "failed to create invitation"})
		return
	}
	c.JSON(http.StatusCreated, gin.H{"success": true, "data": inv})
}

// ─── Billing handlers ──────────────────────────────────────────────────────────

// GetAicpBillingSummary returns wallet balance for a workspace.
// GET /api/aicp/workspaces/:id/billing-summary
func GetAicpBillingSummary(c *gin.Context) {
	workspaceID := c.Param("id")
	if requireMembership(c, workspaceID, "enterprise.dashboard.view", "enterprise.budget.view") == nil {
		return
	}
	summary, err := model.GetWorkspaceBillingSummary(workspaceID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"success": false, "message": "failed to get billing summary"})
		return
	}
	c.JSON(http.StatusOK, gin.H{"success": true, "data": summary})
}

// ─── Role handlers ─────────────────────────────────────────────────────────────

// ListAicpRoles lists active roles in a workspace.
// GET /api/aicp/workspaces/:id/roles
func ListAicpRoles(c *gin.Context) {
	workspaceID := c.Param("id")
	if requireMembership(c, workspaceID, "org.role.manage") == nil {
		return
	}
	roles, err := model.ListRolesForWorkspace(workspaceID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"success": false, "message": "failed to list roles"})
		return
	}
	if roles == nil {
		roles = []model.AicpWorkspaceRole{}
	}
	c.JSON(http.StatusOK, gin.H{"success": true, "data": gin.H{"items": roles}})
}

// CreateAicpRole creates a role.
// POST /api/aicp/workspaces/:id/roles
func CreateAicpRole(c *gin.Context) {
	workspaceID := c.Param("id")
	if requireMembership(c, workspaceID, "org.role.manage") == nil {
		return
	}
	var role model.AicpWorkspaceRole
	if err := c.ShouldBindJSON(&role); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"success": false, "message": "invalid request body"})
		return
	}
	role.WorkspaceID = workspaceID
	role.Status = "active"
	if err := model.CreateRole(&role); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"success": false, "message": "failed to create role"})
		return
	}
	c.JSON(http.StatusCreated, gin.H{"success": true, "data": role})
}

// UpdateAicpRole updates a role's permission grants.
// PATCH /api/aicp/workspaces/:id/roles/:roleId
func UpdateAicpRole(c *gin.Context) {
	workspaceID := c.Param("id")
	if requireMembership(c, workspaceID, "org.role.manage") == nil {
		return
	}
	roleID := c.Param("roleId")

	var grants []model.AicpRolePermissionGrant
	if err := c.ShouldBindJSON(&grants); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"success": false, "message": "invalid request body"})
		return
	}

	if err := model.UpdateRolePermissionGrants(roleID, grants); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"success": false, "message": "failed to update role grants"})
		return
	}
	c.JSON(http.StatusOK, gin.H{"success": true})
}
