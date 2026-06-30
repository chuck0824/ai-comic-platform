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
	Success bool                   `json:"success"`
	Message string                 `json:"message"`
	Data    *workspaceMembershipData `json:"data"`
}

type workspaceMembershipData struct {
	WorkspaceID   string   `json:"workspace_id"`
	WorkspaceType string   `json:"workspace_type"`
	UserID        int64    `json:"user_id"`
	Permissions   []string `json:"permissions"`
}

func openWorkspaceTestDB(t *testing.T) *gorm.DB {
	t.Helper()

	gin.SetMode(gin.TestMode)
	common.SetDatabaseTypes(common.DatabaseTypeSQLite, common.DatabaseTypeSQLite)
	common.RedisEnabled = false

	db, err := gorm.Open(sqlite.Open("file:workspace_test?mode=memory&cache=shared"), &gorm.Config{})
	require.NoError(t, err)

	err = db.AutoMigrate(&model.AicpWorkspace{}, &model.AicpWorkspaceMember{})
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
	perms, err := json.Marshal(permissions)
	require.NoError(t, err)

	ws := &model.AicpWorkspace{
		ID:          workspaceID,
		Type:        "enterprise",
		OwnerUserID: 7,
	}
	if workspaceID == "personal_7" {
		ws.Type = "personal"
		ws.OwnerUserID = userID
	}
	// Use FirstOrCreate to avoid constraint violations on repeated seeds
	db.Where("id = ?", workspaceID).FirstOrCreate(ws)

	member := &model.AicpWorkspaceMember{
		WorkspaceID: workspaceID,
		UserID:      userID,
		Status:      "active",
		Permissions: string(perms),
	}
	db.Create(member)
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
