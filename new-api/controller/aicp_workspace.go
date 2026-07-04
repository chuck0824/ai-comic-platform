package controller

import (
	"net/http"

	"github.com/QuantumNous/new-api/model"
	"github.com/gin-gonic/gin"
)

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
