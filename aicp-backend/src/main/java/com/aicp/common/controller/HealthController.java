package com.aicp.common.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.net.HttpURLConnection;
import java.net.URI;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kubernetes 风格健康检查端点。
 * /health/live  — liveness probe（进程存活）
 * /health/ready — readiness probe（DB + Redis + new-api 全部可达才就绪）
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;

    /** Aggregate health: delegates to readiness for a quick overall check */
    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ready();
    }

    /** Liveness：进程是否存活（始终返回 OK） */
    @GetMapping("/api/health/live")
    public ResponseEntity<Map<String, Object>> live() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /** Readiness：关键依赖是否就绪 */
    @GetMapping("/api/health/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> checks = new LinkedHashMap<>();
        boolean healthy = true;

        // DB check
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("SELECT 1");
            checks.put("database", "UP");
        } catch (Exception e) {
            checks.put("database", "DOWN: " + e.getMessage());
            healthy = false;
        }

        // Redis check
        try {
            redisTemplate.opsForValue().get("health:check");
            checks.put("redis", "UP");
        } catch (Exception e) {
            checks.put("redis", "DOWN: " + e.getMessage());
            healthy = false;
        }

        // new-api check (best-effort, won't fail readiness)
        try {
            String newApiUrl = System.getenv().getOrDefault("NEW_API_URL", "http://localhost:3001");
            HttpURLConnection conn = (HttpURLConnection) URI.create(newApiUrl + "/api/status").toURL().openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            checks.put("new-api", code == 200 ? "UP" : "status=" + code);
        } catch (Exception e) {
            checks.put("new-api", "UNREACHABLE: " + e.getMessage());
            // new-api unreachable doesn't block readiness
        }

        result.put("status", healthy ? "UP" : "DOWN");
        result.put("checks", checks);
        result.put("timestamp", LocalDateTime.now().toString());

        HttpStatus httpStatus = healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(result);
    }
}
