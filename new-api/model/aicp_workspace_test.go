package model

import (
	"testing"
	"time"

	"github.com/glebarez/sqlite"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// openWorkspaceModelTestDB opens an in-memory SQLite database for workspace model tests.
// It assigns the global DB variable so that model-level queries (e.g. FindActiveWorkspaceMembership) work.
func openWorkspaceModelTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	db, err := gorm.Open(sqlite.Open("file:"+t.Name()+"?mode=memory&cache=shared"), &gorm.Config{})
	require.NoError(t, err)
	DB = db
	return db
}

func TestWorkspaceOrganizationModelsPersistScopedGrant(t *testing.T) {
	db := openWorkspaceModelTestDB(t)
	require.NoError(t, db.AutoMigrate(
		&AicpWorkspace{}, &AicpDepartment{}, &AicpWorkspaceRole{},
		&AicpRolePermissionGrant{}, &AicpWorkspaceMember{}, &AicpWorkspaceInvitation{},
	))

	ws := AicpWorkspace{ID: "ent_100", Type: "enterprise", Name: "星辰动漫", Status: "active", OwnerUserID: 9}
	require.NoError(t, db.Create(&ws).Error)

	dept := AicpDepartment{ID: "dept_content", WorkspaceID: ws.ID, Name: "内容一部", Status: "active"}
	require.NoError(t, db.Create(&dept).Error)

	role := AicpWorkspaceRole{ID: "role_head", WorkspaceID: ws.ID, Name: "部门负责人", Status: "active"}
	require.NoError(t, db.Create(&role).Error)

	grant := AicpRolePermissionGrant{RoleID: role.ID, Permission: "trade.purchase.approve", Scope: "DEPARTMENT", ScopeIDs: `["dept_content"]`}
	require.NoError(t, db.Create(&grant).Error)

	assert.Equal(t, "DEPARTMENT", grant.Scope)
	assert.NotZero(t, grant.ID)
}

func TestAicpWorkspaceMemberHasOrganizationFields(t *testing.T) {
	db := openWorkspaceModelTestDB(t)
	require.NoError(t, db.AutoMigrate(
		&AicpWorkspace{}, &AicpDepartment{}, &AicpWorkspaceRole{},
		&AicpWorkspaceMember{}, &AicpWorkspaceInvitation{},
	))

	ws := AicpWorkspace{ID: "ent_200", Type: "enterprise", Name: "测试企业", Status: "active", OwnerUserID: 10}
	require.NoError(t, db.Create(&ws).Error)

	dept := AicpDepartment{ID: "dept_art", WorkspaceID: ws.ID, Name: "美术部", Status: "active"}
	require.NoError(t, db.Create(&dept).Error)

	member := AicpWorkspaceMember{
		WorkspaceID:  ws.ID,
		UserID:       20,
		Status:       "active",
		DepartmentID: "dept_art",
		RoleID:       "role_head",
		Permissions:  `["enterprise.dashboard.view"]`,
		JoinedAt:     time.Now(),
	}
	require.NoError(t, db.Create(&member).Error)

	// Verify the member was persisted with organization fields
	var found AicpWorkspaceMember
	require.NoError(t, db.Where("workspace_id = ? AND user_id = ?", ws.ID, member.UserID).First(&found).Error)
	assert.Equal(t, "dept_art", found.DepartmentID)
	assert.Equal(t, "role_head", found.RoleID)
	assert.Equal(t, "active", found.Status)
	assert.False(t, found.JoinedAt.IsZero())
}

func TestAicpWorkspaceInvitationExpiry(t *testing.T) {
	db := openWorkspaceModelTestDB(t)
	require.NoError(t, db.AutoMigrate(&AicpWorkspace{}, &AicpWorkspaceInvitation{}))

	ws := AicpWorkspace{ID: "ent_300", Type: "enterprise", Name: "第三企业", Status: "active", OwnerUserID: 30}
	require.NoError(t, db.Create(&ws).Error)

	expires := time.Now().Add(48 * time.Hour).Truncate(time.Second)
	invitation := AicpWorkspaceInvitation{
		ID:           "inv_001",
		WorkspaceID:  ws.ID,
		Target:       "user@example.com",
		DepartmentID: "dept_content",
		RoleID:       "role_member",
		TokenDigest:  "sha256:abc123",
		Status:       "pending",
		ExpiresAt:    expires,
		InvitedBy:    9,
	}
	require.NoError(t, db.Create(&invitation).Error)

	var found AicpWorkspaceInvitation
	require.NoError(t, db.Where("id = ?", invitation.ID).First(&found).Error)
	assert.Equal(t, "pending", found.Status)
	assert.Equal(t, expires.Unix(), found.ExpiresAt.Unix())
	assert.Equal(t, int64(9), found.InvitedBy)
}
