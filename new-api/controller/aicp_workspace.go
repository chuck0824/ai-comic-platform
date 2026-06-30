package controller

import (
	"net/http"

	"github.com/QuantumNous/new-api/model"
	"github.com/gin-gonic/gin"
)

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

	// User identity must come from the auth context, NOT from query parameters
	userIDVal, exists := c.Get("aicp_user_id")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{
			"success": false,
			"message": "authentication required",
		})
		return
	}

	userID, ok := userIDVal.(int64)
	if !ok {
		// Handle int case (set by UserAuth middleware as int, not int64)
		if id, ok := userIDVal.(int); ok {
			userID = int64(id)
		} else {
			c.JSON(http.StatusInternalServerError, gin.H{
				"success": false,
				"message": "invalid user identity",
			})
			return
		}
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
