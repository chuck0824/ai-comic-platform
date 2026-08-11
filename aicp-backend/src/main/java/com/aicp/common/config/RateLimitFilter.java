package com.aicp.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 简易内存速率限制过滤器。
 * 生产环境建议替换为 Redis 方案（如 bucket4j-redis）以支持分布式部署。
 *
 * 限制规则（可通过 aicp.rate-limit.* 配置；dev 默认关闭）：
 * - 登录/注册: authMaxPerMinute 次/分钟/IP
 * - AI生成端点: generationMaxPerMinute 次/分钟
 * - 通用API: generalMaxPerMinute 次/分钟
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${aicp.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${aicp.rate-limit.auth-max-per-minute:5}")
    private int authMaxPerMinute;

    @Value("${aicp.rate-limit.generation-max-per-minute:30}")
    private int generationMaxPerMinute;

    @Value("${aicp.rate-limit.general-max-per-minute:120}")
    private int generalMaxPerMinute;

    /** IP → (窗口起始毫秒, 计数) */
    private final ConcurrentHashMap<String, RateWindow> ipCounters = new ConcurrentHashMap<>();
    /** 用户ID → (窗口起始毫秒, 计数) */
    private final ConcurrentHashMap<String, RateWindow> userCounters = new ConcurrentHashMap<>();

    /** 清理过期窗口的间隔 */
    private static final long CLEANUP_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);
    private volatile long lastCleanup = System.currentTimeMillis();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // 仅限制 API 端点
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 获取客户端标识（优先用户ID，其次IP）
        String clientKey = getClientKey(request);

        // 确定限制
        RateLimit limit = getLimitForPath(path);

        if (isRateLimited(clientKey, limit, path)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                "{\"code\":42901,\"message\":\"请求过于频繁，请稍后再试\"}");
            log.warn("速率限制触发: client={}, path={}, limit={}/min", clientKey, path, limit.maxPerMinute());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientKey(HttpServletRequest request) {
        // 优先使用认证用户ID
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Long userId) {
                return "user:" + userId;
            }
        } catch (Exception ignored) {
            // 未认证时回退到 IP
        }
        return "ip:" + getClientIp(request);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xrip = request.getHeader("X-Real-IP");
        if (xrip != null && !xrip.isBlank()) {
            return xrip.trim();
        }
        return request.getRemoteAddr();
    }

    private RateLimit getLimitForPath(String path) {
        if (path.contains("/auth/login") || path.contains("/auth/register")
                || path.contains("/auth/sms")) {
            return new RateLimit(authMaxPerMinute, TimeUnit.MINUTES.toMillis(1));
        }
        if (path.contains("/generation") || path.contains("/generate")
                || path.contains("/shots/") && (path.contains("image") || path.contains("video"))) {
            return new RateLimit(generationMaxPerMinute, TimeUnit.MINUTES.toMillis(1));
        }
        return new RateLimit(generalMaxPerMinute, TimeUnit.MINUTES.toMillis(1));
    }

    private boolean isRateLimited(String key, RateLimit limit, String path) {
        long now = System.currentTimeMillis();

        // 定期清理过期条目
        if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
            cleanup(now - TimeUnit.MINUTES.toMillis(2));
            lastCleanup = now;
        }

        ConcurrentHashMap<String, RateWindow> counters = key.startsWith("user:")
                ? userCounters : ipCounters;

        RateWindow window = counters.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart > limit.windowMs()) {
                return new RateWindow(now, 1);
            }
            return new RateWindow(existing.windowStart, existing.count + 1);
        });

        return window != null && window.count > limit.maxPerMinute();
    }

    private void cleanup(long beforeMs) {
        ipCounters.entrySet().removeIf(e -> e.getValue().windowStart < beforeMs);
        userCounters.entrySet().removeIf(e -> e.getValue().windowStart < beforeMs);
        log.debug("速率限制器清理: ip={}, user={}", ipCounters.size(), userCounters.size());
    }

    /** 速率限制配置 */
    private record RateLimit(int maxPerMinute, long windowMs) {
        RateLimit {
            if (maxPerMinute <= 0) throw new IllegalArgumentException("maxPerMinute must be positive");
        }
    }

    /** 滑动窗口计数器 */
    private record RateWindow(long windowStart, int count) {
    }
}
