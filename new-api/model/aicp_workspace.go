package model

import (
	"encoding/json"
	"errors"

	"gorm.io/gorm"
)

// AicpWorkspace represents a workspace (personal or enterprise) in the AICP platform.
// Workspace records are the authoritative ownership boundary for all assets.
type AicpWorkspace struct {
	ID          string `json:"id" gorm:"primaryKey;size:64"`
	Type        string `json:"type" gorm:"size:16;not null"`         // "personal" or "enterprise"
	OwnerUserID int64  `json:"owner_user_id" gorm:"index;not null"` // user who created/owns the workspace
}

func (AicpWorkspace) TableName() string {
	return "aicp_workspaces"
}

// AicpWorkspaceMember records a user's membership in a workspace with their permissions.
type AicpWorkspaceMember struct {
	ID          uint   `json:"id" gorm:"primaryKey"`
	WorkspaceID string `json:"workspace_id" gorm:"uniqueIndex:uk_workspace_user;size:64;not null"`
	UserID      int64  `json:"user_id" gorm:"uniqueIndex:uk_workspace_user;index;not null"`
	Status      string `json:"status" gorm:"size:16;not null;default:active"` // "active" or "inactive"
	Permissions string `json:"permissions" gorm:"type:text;not null"`         // JSON array of permission strings
}

func (AicpWorkspaceMember) TableName() string {
	return "aicp_workspace_members"
}

// MembershipResult holds the combined workspace and member data for a membership lookup.
type MembershipResult struct {
	WorkspaceID   string   `json:"workspace_id"`
	WorkspaceType string   `json:"workspace_type"`
	UserID        int64    `json:"user_id"`
	Permissions   []string `json:"permissions"`
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

	return &MembershipResult{
		WorkspaceID:   workspace.ID,
		WorkspaceType: workspace.Type,
		UserID:        member.UserID,
		Permissions:   permissions,
	}, nil
}
