package middleware

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
)

const (
	headerService     = "X-AICP-Service"
	headerTimestamp   = "X-AICP-Timestamp"
	headerSignature   = "X-AICP-Signature"
	headerIdempotency = "Idempotency-Key"
	headerCorrelation = "X-Correlation-Id"

	maxTimestampSkew = 300 // seconds
	contextKeyService = "aicp_service_name"
)

// AicpServiceAuth validates HMAC-SHA256 signed requests from the 8080 backend.
// Canonical string: METHOD\nPATH\nTIMESTAMP\nIDEMPOTENCY_KEY\nSHA256_HEX(BODY)
//
// This middleware is for machine-to-machine calls only. It does NOT use
// browser sessions or JWT. Place it on a separate Gin group from UserAuth.
func AicpServiceAuth() gin.HandlerFunc {
	secret := os.Getenv("AICP_SERVICE_SECRET")
	if secret == "" {
		panic("AICP_SERVICE_SECRET environment variable is required for service auth")
	}

	return func(c *gin.Context) {
		// Read required headers
		serviceName := c.GetHeader(headerService)
		timestampStr := c.GetHeader(headerTimestamp)
		signature := c.GetHeader(headerSignature)
		idempotencyKey := c.GetHeader(headerIdempotency)

		if serviceName == "" || timestampStr == "" || signature == "" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{
				"success": false,
				"message": "missing required headers: X-AICP-Service, X-AICP-Timestamp, X-AICP-Signature",
			})
			return
		}

		// Validate timestamp (prevent replay)
		ts, err := strconv.ParseInt(timestampStr, 10, 64)
		if err != nil {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{
				"success": false, "message": "invalid timestamp",
			})
			return
		}
		now := time.Now().Unix()
		if abs(now-ts) > maxTimestampSkew {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{
				"success": false, "message": "timestamp skew too large",
			})
			return
		}

		// Read and restore body for hashing
		bodyBytes, err := io.ReadAll(c.Request.Body)
		if err != nil {
			c.AbortWithStatusJSON(http.StatusBadRequest, gin.H{
				"success": false, "message": "cannot read request body",
			})
			return
		}
		c.Request.Body = io.NopCloser(strings.NewReader(string(bodyBytes)))

		// Build canonical string
		bodyHash := sha256Hex(bodyBytes)
		canonical := fmt.Sprintf("%s\n%s\n%s\n%s\n%s",
			c.Request.Method,
			c.Request.URL.Path,
			timestampStr,
			idempotencyKey,
			bodyHash,
		)

		// Verify HMAC signature
		expectedSig := hmacHex(secret, canonical)
		if !hmac.Equal([]byte(signature), []byte(expectedSig)) {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{
				"success": false, "message": "signature verification failed",
			})
			return
		}

		// Store verified service identity
		c.Set(contextKeyService, serviceName)
		c.Next()
	}
}

// GetServiceName extracts the verified service name from context.
func GetServiceName(c *gin.Context) string {
	if v, ok := c.Get(contextKeyService); ok {
		return v.(string)
	}
	return ""
}

func sha256Hex(data []byte) string {
	h := sha256.Sum256(data)
	return hex.EncodeToString(h[:])
}

func hmacHex(key, message string) string {
	mac := hmac.New(sha256.New, []byte(key))
	mac.Write([]byte(message))
	return hex.EncodeToString(mac.Sum(nil))
}

func abs(x int64) int64 {
	if x < 0 {
		return -x
	}
	return x
}
