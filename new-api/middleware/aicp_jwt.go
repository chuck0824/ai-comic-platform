package middleware

import (
	"os"
	"strings"

	"github.com/QuantumNous/new-api/common"
	"github.com/QuantumNous/new-api/model"
	"github.com/gin-contrib/sessions"
	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
)

// AicpJwtAuth validates an AICP platform JWT token and auto-authenticates
// the user, bridging the gap between aicp-backend (Spring Boot, JWT) and
// new-api (Go, Session). When a valid AICP JWT is present in the
// Authorization header, the user is authenticated without needing to log
// into new-api separately.
//
// This middleware should run BEFORE UserAuth/AdminAuth in the chain.
// If the JWT is valid:
//   - The corresponding new-api user is loaded or created
//   - Session values (id, role, status, username, group) are set
//   - The request proceeds to the next handler
//
// If the JWT is missing or invalid, the request passes through (next
// middleware in chain — typically UserAuth — will handle rejection).

func AicpJwtAuth() gin.HandlerFunc {
	return func(c *gin.Context) {
		// Only attempt AICP JWT auth if no session already exists
		session := sessions.Default(c)
		if id := session.Get("id"); id != nil {
			c.Next()
			return
		}

		token := extractAicpJWT(c)
		if token == "" {
			c.Next()
			return
		}

		claims, err := validateAicpJWT(token)
		if err != nil {
			c.Next()
			return
		}

		// Extract user identity from AICP JWT claims.
		// The 8080 JwtUtil writes claims["uid"] (Long), not claims["userId"].
		// Read "uid" first, fall back to "userId" for backward compatibility.
		userIDFloat, ok := claims["uid"].(float64)
		if !ok {
			userIDFloat, ok = claims["userId"].(float64)
		}
		if !ok {
			c.Next()
			return
		}
		userID := int64(userIDFloat)
		userUUID, _ := claims["uuid"].(string)

		// Look up or create shadow user in new-api
		user, err := model.GetOrCreateShadowUser(userID, userUUID, claims)
		if err != nil {
			common.SysError("AICP JWT auth failed: " + err.Error())
			c.Next()
			return
		}

		if user == nil || user.Status != common.UserStatusEnabled {
			c.Next()
			return
		}

		// Set session values (same pattern as authHelper)
		session.Set("id", user.Id)
		session.Set("role", user.Role)
		session.Set("status", user.Status)
		session.Set("username", user.Username)
		session.Set("group", user.Group)
		_ = session.Save()

		c.Set("id", user.Id)
		c.Set("role", user.Role)
		c.Set("username", user.Username)
		c.Set("group", user.Group)
		c.Set("aicp_user_id", userID)

		c.Next()
	}
}

// extractAicpJWT extracts the JWT from the Authorization header.
// Recognizes both "Bearer <jwt>" and the AICP-specific prefix.
func extractAicpJWT(c *gin.Context) string {
	auth := c.GetHeader("Authorization")
	if auth == "" {
		return ""
	}
	if strings.HasPrefix(auth, "Bearer ") {
		return auth[7:]
	}
	return ""
}

// validateAicpJWT validates an AICP-issued JWT using the shared secret.
// AICP_SECRET env var must match aicp-backend's JWT_SECRET.
func validateAicpJWT(tokenString string) (jwt.MapClaims, error) {
	secret := os.Getenv("AICP_JWT_SECRET")
	if secret == "" {
		// Fall back to CRYPTO_SECRET or SESSION_SECRET for dev
		secret = common.CryptoSecret
	}

	token, err := jwt.Parse(tokenString, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, jwt.ErrSignatureInvalid
		}
		return []byte(secret), nil
	})
	if err != nil {
		return nil, err
	}

	if claims, ok := token.Claims.(jwt.MapClaims); ok && token.Valid {
		return claims, nil
	}
	return nil, jwt.ErrSignatureInvalid
}

// AicpJwtOptional is a lighter variant that sets aicp_user_id in context
// but does NOT create a full session. Use for endpoints that accept both
// authenticated and anonymous access (e.g., public model lists).
func AicpJwtOptional() gin.HandlerFunc {
	return func(c *gin.Context) {
		token := extractAicpJWT(c)
		if token == "" {
			c.Next()
			return
		}
		claims, err := validateAicpJWT(token)
		if err != nil {
			c.Next()
			return
		}
		if userID, ok := claims["userId"].(float64); ok {
			c.Set("aicp_user_id", int64(userID))
		}
		c.Next()
	}
}

