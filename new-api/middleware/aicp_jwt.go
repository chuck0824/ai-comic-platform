package middleware

import (
	"encoding/json"
	"os"
	"strconv"
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
			// Still try to attach aicp_user_id from Bearer JWT for BFF calls
			if token := extractAicpJWT(c); token != "" {
				if claims, err := validateAicpJWT(token); err == nil {
					if userID, ok := claimInt64(claims, "uid", "userId"); ok {
						c.Set("aicp_user_id", userID)
					}
				}
			}
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
		// Prefer string / json.Number to avoid float64 precision loss on snowflake IDs.
		userID, ok := claimInt64(claims, "uid", "userId")
		if !ok {
			c.Next()
			return
		}
		userUUID, _ := claims["uuid"].(string)
		if userUUID == "" {
			// JwtUtil puts uuid in subject when "uuid" claim is absent (older tokens).
			if sub, ok := claims["sub"].(string); ok {
				userUUID = sub
			}
		}

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

		// Ensure personal workspace exists for AICP BFF membership checks
		if _, err := model.EnsurePersonalWorkspace(userID, user.DisplayName); err != nil {
			common.SysError("ensure personal workspace failed: " + err.Error())
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
		// UserAuth requires New-Api-User; BFF/API clients often omit it when
		// authenticating solely via AICP JWT — inject the shadow user id.
		if c.Request.Header.Get("New-Api-User") == "" {
			c.Request.Header.Set("New-Api-User", strconv.Itoa(user.Id))
		}

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
// AICP_JWT_SECRET env var must match aicp-backend's JWT_SECRET.
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
	}, jwt.WithJSONNumber())
	if err != nil {
		return nil, err
	}

	if claims, ok := token.Claims.(jwt.MapClaims); ok && token.Valid {
		return claims, nil
	}
	return nil, jwt.ErrSignatureInvalid
}

// claimInt64 reads a JWT claim as int64 without float64 precision loss.
func claimInt64(claims jwt.MapClaims, keys ...string) (int64, bool) {
	for _, key := range keys {
		v, ok := claims[key]
		if !ok || v == nil {
			continue
		}
		switch t := v.(type) {
		case json.Number:
			i, err := t.Int64()
			if err == nil {
				return i, true
			}
		case string:
			i, err := strconv.ParseInt(strings.TrimSpace(t), 10, 64)
			if err == nil {
				return i, true
			}
		case float64:
			return int64(t), true
		case int64:
			return t, true
		case int:
			return int64(t), true
		}
	}
	return 0, false
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
		userID, ok := claimInt64(claims, "uid", "userId")
		if !ok {
			c.Next()
			return
		}
		c.Set("aicp_user_id", userID)
		c.Next()
	}
}
