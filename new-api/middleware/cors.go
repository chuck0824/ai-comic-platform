package middleware

import (
	"os"
	"strings"

	"github.com/QuantumNous/new-api/common"
	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
)

// CORS returns a CORS middleware configured for API endpoints that use
// Bearer-token authentication (relay, usage, token-log). Credentials are
// not needed for Bearer-token flows — the browser attaches the Authorization
// header cross-origin without cookies. Allowed origins are controlled via
// the CORS_ALLOWED_ORIGINS env var; when empty the default allowlist below
// is used.
func CORS() gin.HandlerFunc {
	config := cors.DefaultConfig()
	config.AllowAllOrigins = false
	config.AllowCredentials = false // Bearer-token auth, cookies not required
	config.AllowMethods = []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"}
	config.AllowHeaders = []string{"*"}
	config.AllowOrigins = getAllowedOrigins()
	return cors.New(config)
}

// CORSCredentials returns a CORS middleware for endpoints that rely on
// session-cookie authentication (admin dashboard, web frontend).
// AllowAllOrigins is always false; allowed origins must be explicitly
// listed. This combination is safe per the CORS specification.
func CORSCredentials() gin.HandlerFunc {
	config := cors.DefaultConfig()
	config.AllowAllOrigins = false
	config.AllowCredentials = true
	config.AllowMethods = []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"}
	config.AllowHeaders = []string{"*"}
	config.AllowOrigins = getAllowedOrigins()
	return cors.New(config)
}

// getAllowedOrigins reads the CORS_ALLOWED_ORIGINS env var (comma-separated
// URLs) and falls back to a safe localhost-only list when it is empty.
func getAllowedOrigins() []string {
	raw := os.Getenv("CORS_ALLOWED_ORIGINS")
	if raw == "" {
		return []string{
			"http://localhost:8080",
			"http://localhost:5173",
			"http://localhost:3001",
		}
	}
	parts := strings.Split(raw, ",")
	origins := make([]string, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p != "" {
			origins = append(origins, p)
		}
	}
	return origins
}

func PoweredBy() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("X-New-Api-Version", common.Version)
		c.Next()
	}
}
