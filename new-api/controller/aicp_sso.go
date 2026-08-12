package controller

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/QuantumNous/new-api/common"
	"github.com/QuantumNous/new-api/model"
	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

// LoginAicpSso exchanges a short-lived AICP SSO ticket (issued by 8080) for a
// new-api browser session via shadow user + setupLogin.
func LoginAicpSso(c *gin.Context) {
	var req struct {
		Ticket string `json:"ticket"`
	}
	if err := c.ShouldBindJSON(&req); err != nil || strings.TrimSpace(req.Ticket) == "" {
		c.JSON(http.StatusOK, gin.H{"success": false, "message": "ticket is required"})
		return
	}

	claims, err := parseAicpSsoTicket(req.Ticket)
	if err != nil {
		c.JSON(http.StatusOK, gin.H{"success": false, "message": "invalid or expired SSO ticket"})
		return
	}

	jti, _ := claims["jti"].(string)
	if jti == "" {
		c.JSON(http.StatusOK, gin.H{"success": false, "message": "invalid SSO ticket"})
		return
	}
	if !consumeAicpSsoJTI(jti) {
		c.JSON(http.StatusOK, gin.H{"success": false, "message": "SSO ticket already used or expired"})
		return
	}

	userIDFloat, ok := claims["uid"].(float64)
	if !ok {
		c.JSON(http.StatusOK, gin.H{"success": false, "message": "invalid SSO ticket claims"})
		return
	}
	aicpUserID := int64(userIDFloat)
	userUUID, _ := claims["uuid"].(string)
	if userUUID == "" {
		userUUID, _ = claims["sub"].(string)
	}

	user, err := model.GetOrCreateShadowUser(aicpUserID, userUUID, claims)
	if err != nil || user == nil {
		common.SysError("AICP SSO login failed: " + errString(err))
		c.JSON(http.StatusOK, gin.H{"success": false, "message": "failed to resolve platform user"})
		return
	}
	if user.Status != common.UserStatusEnabled {
		c.JSON(http.StatusOK, gin.H{"success": false, "message": "user is disabled"})
		return
	}

	setupLogin(user, c)
}

// IssueAicpSsoTicket issues a short-lived SSO ticket for opening the 8080
// workbench while already logged into new-api. Requires an existing shadow
// link (aicp_user_id) on the current user.
func IssueAicpSsoTicket(c *gin.Context) {
	id := c.GetInt("id")
	user, err := model.GetUserById(id, false)
	if err != nil || user == nil {
		c.JSON(http.StatusOK, gin.H{"success": false, "message": "user not found"})
		return
	}
	if user.AicpUserId == nil || *user.AicpUserId <= 0 {
		c.JSON(http.StatusOK, gin.H{
			"success": false,
			"message": "current account is not linked to the AICP workbench; sign in on 8080 first",
		})
		return
	}

	nickname := user.DisplayName
	if nickname == "" {
		nickname = user.Username
	}
	ticket, err := signAicpSsoTicket(*user.AicpUserId, fmt.Sprintf("aicp_%d", *user.AicpUserId), nickname)
	if err != nil {
		common.SysError("issue AICP SSO ticket failed: " + err.Error())
		c.JSON(http.StatusOK, gin.H{"success": false, "message": "failed to issue SSO ticket"})
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "",
		"data": map[string]any{
			"ticket":     ticket,
			"expires_in": 60,
		},
	})
}

func parseAicpSsoTicket(tokenString string) (jwt.MapClaims, error) {
	secret := aicpJWTSecret()
	token, err := jwt.Parse(tokenString, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, jwt.ErrSignatureInvalid
		}
		return []byte(secret), nil
	})
	if err != nil {
		return nil, err
	}
	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok || !token.Valid {
		return nil, jwt.ErrSignatureInvalid
	}
	purpose, _ := claims["purpose"].(string)
	if purpose != "sso" {
		return nil, fmt.Errorf("not an SSO ticket")
	}
	return claims, nil
}

func signAicpSsoTicket(aicpUserID int64, userUUID, nickname string) (string, error) {
	now := time.Now()
	claims := jwt.MapClaims{
		"jti":      strings.ReplaceAll(uuid.NewString(), "-", ""),
		"sub":      userUUID,
		"uid":      aicpUserID,
		"uuid":     userUUID,
		"nickname": nickname,
		"purpose":  "sso",
		"iat":      now.Unix(),
		"exp":      now.Add(60 * time.Second).Unix(),
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString([]byte(aicpJWTSecret()))
}

func aicpJWTSecret() string {
	secret := os.Getenv("AICP_JWT_SECRET")
	if secret == "" {
		secret = common.CryptoSecret
	}
	return secret
}

func consumeAicpSsoJTI(jti string) bool {
	key := "aicp_sso:jti:" + jti
	if !common.RedisEnabled || common.RDB == nil {
		// No Redis: rely on short TTL only (still safer than infinite reuse).
		return true
	}
	ok, err := common.RDB.SetNX(context.Background(), key, "1", 2*time.Minute).Result()
	if err != nil {
		common.SysError("SSO jti SetNX failed: " + err.Error())
		return true // fail open on redis errors after JWT already validated
	}
	return ok
}

func errString(err error) string {
	if err == nil {
		return "unknown error"
	}
	return err.Error()
}
