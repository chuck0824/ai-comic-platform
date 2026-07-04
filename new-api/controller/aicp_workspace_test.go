package controller

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/QuantumNous/new-api/common"
	"github.com/QuantumNous/new-api/model"
	"github.com/gin-gonic/gin"
	"github.com/glebarez/sqlite"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

type workspaceMembershipResponse struct {
	Success bool                    `json:"success"`
	Message string                  `json:"message"`
	Data    *workspaceMembershipData `json:"data"`
}

type workspaceMembershipData struct {
	WorkspaceID      string                  `json:"workspace_id"`
	WorkspaceType    string                  `json:"workspace_type"`
	UserID           int64                   `json:"user_id"`
	DepartmentID     string                  `json:"department_id"`
	Roles            []string                `json:"roles"`
	Permissions      []string                `json:"permissions"`
	PermissionGrants []permissionGrantRecord `json:"permission_grants"`
}

type permissionGrantRecord struct {
	Permission string   `json:"permission"`
	Scope      string   `json:"scope"`
	ScopeIDs   []string `json:"scope_ids"`
}

type workspaceListResponse struct {
	Success bool `json:"success"`
	Data    struct {
		Items []workspaceMembershipData `json:"items"`
	} `json:"data"`
}

func openWorkspaceTestDB(t *testing.T) *gorm.DB {
	t.Helper()

	gin.SetMode(gin.TestMode)
	common.SetDatabaseTypes(common.DatabaseTypeSQLite, common.DatabaseTypeSQLite)
	common.RedisEnabled = false

	db, err := gorm.Open(sqlite.Open("file:workspace_test?mode=memory&cache=shared"), &gorm.Config{})
	require.NoError(t, err)

	err = db.AutoMigrate(
		&model.AicpWorkspace{}, &model.AicpWorkspaceMember{},
		&model.AicpDepartment{}, &model.AicpWorkspaceRole{},
		&model.AicpRolePermissionGrant{}, &model.AicpWorkspaceInvitation{},
	)
	require.NoError(t, err)

	model.DB = db
	model.LOG_DB = db

	t.Cleanup(func() {
		sqlDB, _ := db.DB()
		if sqlDB != nil {
			sqlDB.Close()
		}
		model.DB = nil
		model.LOG_DB = nil
	})

	return db
}

func seedWorkspaceMember(t *testing.T, db *gorm.DB, workspaceID string, userID int64, permissions []string) {
	t.Helper()
	seedWorkspaceMemberWithOrg(t, db, workspaceID, userID, permissions, "", "")
}

func seedWorkspaceMemberWithOrg(t *testing.T, db *gorm.DB, workspaceID string, userID int64, permissions []string, departmentID, roleID string) {
	t.Helper()
	perms, err := json.Marshal(permissions)
	require.NoError(t, err)

	ws := &model.AicpWorkspace{
		ID:          workspaceID,
		Type:        "enterprise",
		Name:        "Test Workspace " + workspaceID,
		Status:      "active",
		OwnerUserID: 7,
	}
	if workspaceID == "personal_7" {
		ws.Type = "personal"
		ws.OwnerUserID = userID
	}
	db.Where("id = ?", workspaceID).FirstOrCreate(ws)

	member := &model.AicpWorkspaceMember{
		WorkspaceID:  workspaceID,
		UserID:       userID,
		DepartmentID: departmentID,
		RoleID:       roleID,
		Status:       "active",
		Permissions:  string(perms),
	}
	db.Create(member)
}

func seedWorkspaceRole(t *testing.T, db *gorm.DB, roleID, workspaceID, name string, grants []model.AicpRolePermissionGrant) {
	t.Helper()
	role := &model.AicpWorkspaceRole{
		ID:          roleID,
		WorkspaceID: workspaceID,
		Name:        name,
		Status:      "active",
	}
	db.Where("id = ?", roleID).FirstOrCreate(role)
	for _, g := range grants {
		g.RoleID = roleID
		db.Create(&g)
	}
}

func setAicpUser(c *gin.Context, userID int64) {
	c.Set("aicp_user_id", userID)
	c.Set("id", int(userID))
	c.Set("role", common.RoleCommonUser)
	c.Set("status", common.UserStatusEnabled)
	c.Set("username", "testuser")
	c.Set("group", "default")
}

// Helper to create a test Gin router with AICP JWT-like auth set
func newAicpWorkspaceRouter() *gin.Engine {
	r := gin.New()
	return r
}

func performRequest(router *gin.Engine, method, path string) *httptest.ResponseRecorder {
	req := httptest.NewRequest(method, path, nil)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	return w
}

// Test: GET /api/aicp/workspaces/:id/membership returns membership for valid user
func TestGetAicpWorkspaceMembership(t *testing.T) {
	db := openWorkspaceTestDB(t)
	seedWorkspaceMember(t, db, "ent_100", 9, []string{"asset.view", "asset.use"})

	router := gin.New()
	router.GET("/api/aicp/workspaces/:id/membership", func(c *gin.Context) {
		setAicpUser(c, 9)
		c.Next()
	}, GetAicpWorkspaceMembership)

	response := performRequest(router, "GET", "/api/aicp/workspaces/ent_100/membership")

	require.Equal(t, http.StatusOK, response.Code)

	var resp workspaceMembershipResponse
	err := json.Unmarshal(response.Body.Bytes(), &resp)
	require.NoError(t, err)

	assert.True(t, resp.Success)
	require.NotNil(t, resp.Data)
	assert.Equal(t, "ent_100", resp.Data.WorkspaceID)
	assert.Equal(t, "enterprise", resp.Data.WorkspaceType)
	assert.Equal(t, int64(9), resp.Data.UserID)
	assert.Contains(t, resp.Data.Permissions, "asset.view")
	assert.Contains(t, resp.Data.Permissions, "asset.use")
}

// Test: membership endpoint hides data from users not in the workspace (404 to prevent ID enumeration)
func TestGetAicpWorkspaceMembershipHidesOtherTenant(t *testing.T) {
	db := openWorkspaceTestDB(t)
	seedWorkspaceMember(t, db, "ent_100", 9, []string{"asset.view"})

	router := gin.New()
	router.GET("/api/aicp/workspaces/:id/membership", func(c *gin.Context) {
		setAicpUser(c, 10) // User 10 is NOT a member of ent_100
		c.Next()
	}, GetAicpWorkspaceMembership)

	response := performRequest(router, "GET", "/api/aicp/workspaces/ent_100/membership")

	require.Equal(t, http.StatusNotFound, response.Code)
}

// Test: inactive membership returns 404
func TestGetAicpWorkspaceMembershipRejectsInactive(t *testing.T) {
	db := openWorkspaceTestDB(t)
	seedWorkspaceMember(t, db, "ent_100", 9, []string{"asset.view"})
	// Mark member as inactive
	db.Model(&model.AicpWorkspaceMember{}).
		Where("workspace_id = ? AND user_id = ?", "ent_100", 9).
		Update("status", "inactive")

	router := gin.New()
	router.GET("/api/aicp/workspaces/:id/membership", func(c *gin.Context) {
		setAicpUser(c, 9)
		c.Next()
	}, GetAicpWorkspaceMembership)

	response := performRequest(router, "GET", "/api/aicp/workspaces/ent_100/membership")

	require.Equal(t, http.StatusNotFound, response.Code)
}

// Test: non-existent workspace returns 404
func TestGetAicpWorkspaceMembershipNonexistentWorkspace(t *testing.T) {
	_ = openWorkspaceTestDB(t)

	router := gin.New()
	router.GET("/api/aicp/workspaces/:id/membership", func(c *gin.Context) {
		setAicpUser(c, 9)
		c.Next()
	}, GetAicpWorkspaceMembership)

	response := performRequest(router, "GET", "/api/aicp/workspaces/nonexistent/membership")

	require.Equal(t, http.StatusNotFound, response.Code)
}

// Test: user_id from context is used, not from query params (security)
func TestGetAicpWorkspaceMembershipIgnoresQueryParam(t *testing.T) {
	db := openWorkspaceTestDB(t)
	seedWorkspaceMember(t, db, "ent_100", 9, []string{"asset.view"})
	seedWorkspaceMember(t, db, "ent_100", 10, []string{"asset.manage"})

	router := gin.New()
	router.GET("/api/aicp/workspaces/:id/membership", func(c *gin.Context) {
		setAicpUser(c, 9) // Auth context says user 9
		c.Next()
	}, GetAicpWorkspaceMembership)

	// Even if someone tries to pass user_id as query param, it must be ignored
	response := performRequest(router, "GET", "/api/aicp/workspaces/ent_100/membership?user_id=10")

	require.Equal(t, http.StatusOK, response.Code)

	var resp workspaceMembershipResponse
	err := json.Unmarshal(response.Body.Bytes(), &resp)
	require.NoError(t, err)

	assert.Equal(t, int64(9), resp.Data.UserID) // Must return user 9's membership, not 10
}

// Test: personal workspace owner has full permissions
func TestGetAicpWorkspaceMembershipPersonalWorkspace(t *testing.T) {
	db := openWorkspaceTestDB(t)
	seedWorkspaceMember(t, db, "personal_7", 7, []string{"asset.view", "asset.use", "asset.manage"})

	router := gin.New()
	router.GET("/api/aicp/workspaces/:id/membership", func(c *gin.Context) {
		setAicpUser(c, 7)
		c.Next()
	}, GetAicpWorkspaceMembership)

	response := performRequest(router, "GET", "/api/aicp/workspaces/personal_7/membership")

	require.Equal(t, http.StatusOK, response.Code)

	var resp workspaceMembershipResponse
	err := json.Unmarshal(response.Body.Bytes(), &resp)
	require.NoError(t, err)

	assert.True(t, resp.Success)
	assert.Equal(t, "personal_7", resp.Data.WorkspaceID)
	assert.Equal(t, "personal", resp.Data.WorkspaceType)
}

// Test: GET /api/aicp/workspaces returns all active memberships for the authenticated user
func TestListAicpWorkspaces(t *testing.T) {
	db := openWorkspaceTestDB(t)
	seedWorkspaceMember(t, db, "ent_100", 9, []string{"asset.view"})
	seedWorkspaceMember(t, db, "personal_9", 9, []string{"asset.view", "asset.use"})
	// Another user's workspace should not appear
	seedWorkspaceMember(t, db, "ent_200", 10, []string{"asset.manage"})

	router := gin.New()
	router.GET("/api/aicp/workspaces", func(c *gin.Context) {
		setAicpUser(c, 9)
		c.Next()
	}, ListAicpWorkspaces)

	response := performRequest(router, "GET", "/api/aicp/workspaces")

	require.Equal(t, http.StatusOK, response.Code)

	var resp workspaceListResponse
	err := json.Unmarshal(response.Body.Bytes(), &resp)
	require.NoError(t, err)

	assert.True(t, resp.Success)
	assert.Len(t, resp.Data.Items, 2) // only ent_100 and personal_9

	ids := make([]string, len(resp.Data.Items))
	for i, item := range resp.Data.Items {
		ids[i] = item.WorkspaceID
	}
	assert.Contains(t, ids, "ent_100")
	assert.Contains(t, ids, "personal_9")
	assert.NotContains(t, ids, "ent_200") // user 9 is not a member
}

// Test: list excludes inactive memberships
func TestListAicpWorkspacesExcludesInactive(t *testing.T) {
	db := openWorkspaceTestDB(t)
	seedWorkspaceMember(t, db, "ent_100", 9, []string{"asset.view"})
	// Create an inactive membership
	seedWorkspaceMember(t, db, "ent_200", 9, []string{"asset.use"})
	db.Model(&model.AicpWorkspaceMember{}).
		Where("workspace_id = ? AND user_id = ?", "ent_200", 9).
		Update("status", "inactive")

	router := gin.New()
	router.GET("/api/aicp/workspaces", func(c *gin.Context) {
		setAicpUser(c, 9)
		c.Next()
	}, ListAicpWorkspaces)

	response := performRequest(router, "GET", "/api/aicp/workspaces")

	require.Equal(t, http.StatusOK, response.Code)

	var resp workspaceListResponse
	err := json.Unmarshal(response.Body.Bytes(), &resp)
	require.NoError(t, err)

	assert.True(t, resp.Success)
	assert.Len(t, resp.Data.Items, 1)
	assert.Equal(t, "ent_100", resp.Data.Items[0].WorkspaceID)
}

// Test: membership returns department, roles, and scoped permission grants
func TestGetAicpWorkspaceMembershipReturnsGrants(t *testing.T) {
	db := openWorkspaceTestDB(t)
	seedWorkspaceRole(t, db, "role_head", "ent_100", "部门负责人", []model.AicpRolePermissionGrant{
		{Permission: "trade.purchase.approve", Scope: "DEPARTMENT", ScopeIDs: `["dept_content"]`},
	})
	seedWorkspaceMemberWithOrg(t, db, "ent_100", 9, []string{"enterprise.dashboard.view"}, "dept_content", "role_head")

	router := gin.New()
	router.GET("/api/aicp/workspaces/:id/membership", func(c *gin.Context) {
		setAicpUser(c, 9)
		c.Next()
	}, GetAicpWorkspaceMembership)

	response := performRequest(router, "GET", "/api/aicp/workspaces/ent_100/membership")

	require.Equal(t, http.StatusOK, response.Code)

	var resp workspaceMembershipResponse
	err := json.Unmarshal(response.Body.Bytes(), &resp)
	require.NoError(t, err)

	assert.True(t, resp.Success)
	assert.Equal(t, "dept_content", resp.Data.DepartmentID)
	assert.Contains(t, resp.Data.Roles, "部门负责人")
	assert.Contains(t, resp.Data.Permissions, "enterprise.dashboard.view")

	require.Len(t, resp.Data.PermissionGrants, 1)
	assert.Equal(t, "trade.purchase.approve", resp.Data.PermissionGrants[0].Permission)
	assert.Equal(t, "DEPARTMENT", resp.Data.PermissionGrants[0].Scope)
	assert.Contains(t, resp.Data.PermissionGrants[0].ScopeIDs, "dept_content")
}
