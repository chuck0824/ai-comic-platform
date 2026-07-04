package model

import (
	"encoding/json"
	"errors"
	"time"

	"gorm.io/gorm"
)

// AicpWorkspace represents a workspace (personal or enterprise) in the AICP platform.
// Workspace records are the authoritative ownership boundary for all assets.
type AicpWorkspace struct {
	ID           string `json:"id" gorm:"primaryKey;size:64"`
	Type         string `json:"type" gorm:"size:16;not null"`          // "personal" or "enterprise"
	Name         string `json:"name" gorm:"size:128;not null"`         // display name
	Status       string `json:"status" gorm:"size:16;not null;default:active"` // "active" or "inactive"
	VerifyStatus string `json:"verify_status" gorm:"size:32;default:unverified"`
	OwnerUserID  int64  `json:"owner_user_id" gorm:"index;not null"`  // user who created/owns the workspace
	MemberLimit  int    `json:"member_limit" gorm:"default:0"`        // max members (0 = unlimited)
}

func (AicpWorkspace) TableName() string {
	return "aicp_workspaces"
}

// AicpWorkspaceMember records a user's membership in a workspace with their permissions.
type AicpWorkspaceMember struct {
	ID           uint      `json:"id" gorm:"primaryKey"`
	WorkspaceID  string    `json:"workspace_id" gorm:"uniqueIndex:uk_workspace_user;size:64;not null"`
	UserID       int64     `json:"user_id" gorm:"uniqueIndex:uk_workspace_user;index;not null"`
	DepartmentID string    `json:"department_id" gorm:"size:64;default:''"`
	RoleID       string    `json:"role_id" gorm:"size:64;default:''"`
	Status       string    `json:"status" gorm:"size:16;not null;default:active"` // "active" or "inactive"
	Permissions  string    `json:"permissions" gorm:"type:text;not null"`          // JSON array of permission strings
	JoinedAt     time.Time `json:"joined_at"`
}

func (AicpWorkspaceMember) TableName() string {
	return "aicp_workspace_members"
}

// PermissionGrant represents a scoped permission grant for a membership.
type PermissionGrant struct {
	Permission string   `json:"permission"`
	Scope      string   `json:"scope"`
	ScopeIDs   []string `json:"scope_ids"`
}

// MembershipResult holds the combined workspace and member data for a membership lookup.
type MembershipResult struct {
	WorkspaceID      string            `json:"workspace_id"`
	WorkspaceType    string            `json:"workspace_type"`
	UserID           int64             `json:"user_id"`
	DepartmentID     string            `json:"department_id"`
	Roles            []string          `json:"roles"`
	Permissions      []string          `json:"permissions"`
	PermissionGrants []PermissionGrant `json:"permission_grants"`
}

// HasPermission checks whether the membership includes a specific permission.
func (m *MembershipResult) HasPermission(permission string) bool {
	for _, p := range m.Permissions {
		if p == permission {
			return true
		}
	}
	return false
}

// AicpDepartment represents a department within an enterprise workspace.
type AicpDepartment struct {
	ID               string `json:"id" gorm:"primaryKey;size:64"`
	WorkspaceID      string `json:"workspace_id" gorm:"index;size:64;not null"`
	ParentID         string `json:"parent_id" gorm:"size:64;default:''"`
	Name             string `json:"name" gorm:"size:128;not null"`
	ManagerMemberID  uint   `json:"manager_member_id" gorm:"default:0"`
	Status           string `json:"status" gorm:"size:16;not null;default:active"` // "active" or "inactive"
	SortOrder        int    `json:"sort_order" gorm:"default:0"`
}

func (AicpDepartment) TableName() string {
	return "aicp_departments"
}

// AicpWorkspaceRole represents a role within an enterprise workspace.
type AicpWorkspaceRole struct {
	ID             string `json:"id" gorm:"primaryKey;size:64"`
	WorkspaceID    string `json:"workspace_id" gorm:"index;size:64;not null"`
	Name           string `json:"name" gorm:"size:128;not null"`
	SystemTemplate bool   `json:"system_template" gorm:"default:false"`
	Status         string `json:"status" gorm:"size:16;not null;default:active"` // "active" or "inactive"
}

func (AicpWorkspaceRole) TableName() string {
	return "aicp_workspace_roles"
}

// AicpRolePermissionGrant maps a permission to a role with an optional data scope.
type AicpRolePermissionGrant struct {
	ID         uint   `json:"id" gorm:"primaryKey"`
	RoleID     string `json:"role_id" gorm:"index;size:64;not null"`
	Permission string `json:"permission" gorm:"size:128;not null"`
	Scope      string `json:"scope" gorm:"size:16;default:WORKSPACE"` // "WORKSPACE", "DEPARTMENT", or "SELF"
	ScopeIDs   string `json:"scope_ids" gorm:"type:text"`             // JSON array of scope target IDs
}

func (AicpRolePermissionGrant) TableName() string {
	return "aicp_role_permission_grants"
}

// AicpWorkspaceInvitation records an invitation to join a workspace.
type AicpWorkspaceInvitation struct {
	ID           string    `json:"id" gorm:"primaryKey;size:64"`
	WorkspaceID  string    `json:"workspace_id" gorm:"index;size:64;not null"`
	Target       string    `json:"target" gorm:"size:256;not null"` // email or phone
	DepartmentID string    `json:"department_id" gorm:"size:64;default:''"`
	RoleID       string    `json:"role_id" gorm:"size:64;default:''"`
	TokenDigest  string    `json:"token_digest" gorm:"size:128;not null"`
	Status       string    `json:"status" gorm:"size:16;not null;default:pending"` // "pending", "accepted", "expired", "revoked"
	ExpiresAt    time.Time `json:"expires_at"`
	InvitedBy    int64     `json:"invited_by" gorm:"not null"`
}

func (AicpWorkspaceInvitation) TableName() string {
	return "aicp_workspace_invitations"
}

// FindActiveWorkspaceMembership looks up a workspace and active membership for the given user.
// Returns (nil, nil) when workspace exists but user has no active membership (unified not-found).
// Returns (nil, error) when workspace does not exist (gorm.ErrRecordNotFound) or on DB errors.
func FindActiveWorkspaceMembership(workspaceID string, userID int64) (*MembershipResult, error) {
	var workspace AicpWorkspace
	if err := DB.Where("id = ?", workspaceID).First(&workspace).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil // workspace not found
		}
		return nil, err
	}

	var member AicpWorkspaceMember
	if err := DB.Where("workspace_id = ? AND user_id = ? AND status = ?",
		workspaceID, userID, "active").First(&member).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil // no active membership
		}
		return nil, err
	}

	// Decode permissions from JSON string
	var permissions []string
	if member.Permissions != "" {
		if err := json.Unmarshal([]byte(member.Permissions), &permissions); err != nil {
			permissions = []string{}
		}
	}

	// Load role names for this member
	roles := loadMemberRoles(member.RoleID)

	// Load scoped permission grants for this member's role
	grants := loadRolePermissionGrants(member.RoleID)

	return &MembershipResult{
		WorkspaceID:      workspace.ID,
		WorkspaceType:    workspace.Type,
		UserID:           member.UserID,
		DepartmentID:     member.DepartmentID,
		Roles:            roles,
		Permissions:      permissions,
		PermissionGrants: grants,
	}, nil
}

// loadMemberRoles returns role names for the given role ID.
func loadMemberRoles(roleID string) []string {
	if roleID == "" {
		return []string{}
	}
	var role AicpWorkspaceRole
	if err := DB.Where("id = ? AND status = ?", roleID, "active").First(&role).Error; err != nil {
		return []string{}
	}
	return []string{role.Name}
}

// loadRolePermissionGrants returns scoped permission grants for the given role ID.
func loadRolePermissionGrants(roleID string) []PermissionGrant {
	if roleID == "" {
		return []PermissionGrant{}
	}
	var grantRows []AicpRolePermissionGrant
	if err := DB.Where("role_id = ?", roleID).Find(&grantRows).Error; err != nil {
		return []PermissionGrant{}
	}

	grants := make([]PermissionGrant, 0, len(grantRows))
	for _, g := range grantRows {
		var scopeIDs []string
		if g.ScopeIDs != "" {
			if err := json.Unmarshal([]byte(g.ScopeIDs), &scopeIDs); err != nil {
				scopeIDs = []string{}
			}
		}
		grants = append(grants, PermissionGrant{
			Permission: g.Permission,
			Scope:      g.Scope,
			ScopeIDs:   scopeIDs,
		})
	}
	return grants
}

// ListActiveWorkspacesForUser returns all workspaces where the user has an active membership.
func ListActiveWorkspacesForUser(userID int64) ([]MembershipResult, error) {
	var members []AicpWorkspaceMember
	if err := DB.Where("user_id = ? AND status = ?", userID, "active").Find(&members).Error; err != nil {
		return nil, err
	}

	results := make([]MembershipResult, 0, len(members))
	for _, m := range members {
		var ws AicpWorkspace
		if err := DB.Where("id = ?", m.WorkspaceID).First(&ws).Error; err != nil {
			continue // skip if workspace was deleted
		}

		var permissions []string
		if m.Permissions != "" {
			if err := json.Unmarshal([]byte(m.Permissions), &permissions); err != nil {
				permissions = []string{}
			}
		}

		roles := loadMemberRoles(m.RoleID)
		grants := loadRolePermissionGrants(m.RoleID)

		results = append(results, MembershipResult{
			WorkspaceID:      ws.ID,
			WorkspaceType:    ws.Type,
			UserID:           m.UserID,
			DepartmentID:     m.DepartmentID,
			Roles:            roles,
			Permissions:      permissions,
			PermissionGrants: grants,
		})
	}
	return results, nil
}
