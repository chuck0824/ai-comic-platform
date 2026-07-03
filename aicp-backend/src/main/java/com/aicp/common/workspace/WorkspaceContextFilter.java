package com.aicp.common.workspace;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.util.SecurityUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Resolves a trusted {@link WorkspaceContext} for every request that targets a
 * workspace-scoped endpoint. Runs AFTER JWT authentication, BEFORE controllers.
 *
 * <h3>Protected paths (require X-Workspace-Id)</h3>
 * <ul>
 *   <li>{@code /api/v1/asset/library/**}</li>
 *   <li>{@code /api/v1/asset/market/listings/*\/claim}</li>
 *   <li>{@code /api/v1/asset/market/listings/*\/favorite}</li>
 *   <li>{@code /api/v1/asset/publish-requests/**}</li>
 *   <li>{@code /api/v1/asset/applications/**}</li>
 *   <li>{@code /api/v1/assets/**} — asset workbench (queries + commands)</li>
 *   <li>{@code /api/v1/generation/tasks/**} — task cancel / retry / detail</li>
 *   <li>{@code /api/v1/canvas/projects/**}</li>
 *   <li>{@code /api/v1/trade/listings/**}</li>
 *   <li>{@code /api/v1/trade/orders/**}</li>
 *   <li>{@code /api/v1/trade/entitlements/**}</li>
 *   <li>{@code /api/v1/trade/seller/**}</li>
 *   <li>{@code /api/v1/trade/purchase-requests/**}</li>
 *   <li>{@code /api/v1/trade/refund-requests/**}</li>
 * </ul>
 *
 * Public market read endpoints ({@code GET /api/v1/asset/market/listings/**})
 * are NOT protected and may proceed without workspace context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1) // After JWT filter (Order(0)) but before controllers
public class WorkspaceContextFilter extends OncePerRequestFilter {

    private final WorkspaceAccessService workspaceAccessService;

    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** Paths that require a valid workspace context. */
    private static final List<PathPattern> PROTECTED_PATTERNS = List.of(
            new PathPattern("/api/v1/asset/library/**", true),
            new PathPattern("/api/v1/asset/market/listings/*/claim", true),
            new PathPattern("/api/v1/asset/market/listings/*/favorite", true),
            new PathPattern("/api/v1/asset/publish-requests/**", true),
            new PathPattern("/api/v1/asset/applications/**", true),
            // ── Asset workbench ──
            new PathPattern("/api/v1/assets/**", true),
            new PathPattern("/api/v1/generation/tasks/**", true),
            new PathPattern("/api/v1/canvas/projects/**", true),
            new PathPattern("/api/v1/trade/listings/**", true),
            new PathPattern("/api/v1/trade/orders/**", true),
            new PathPattern("/api/v1/trade/entitlements/**", true),
            new PathPattern("/api/v1/trade/seller/**", true),
            new PathPattern("/api/v1/trade/purchase-requests/**", true),
            new PathPattern("/api/v1/trade/refund-requests/**", true)
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!isProtectedPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // This endpoint requires a workspace context — resolve it
        String workspaceId = request.getHeader("X-Workspace-Id");
        if (!StringUtils.hasText(workspaceId)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    ErrorCode.PARAM_INVALID.getCode(), "缺少 X-Workspace-Id 请求头");
            return;
        }

        // Extract the authenticated user from SecurityContext
        Long userId;
        try {
            userId = SecurityUtil.requireCurrentUserId();
        } catch (IllegalStateException e) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    40003, "请先登录");
            return;
        }

        // Forward the original Authorization header to 3001
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);

        try {
            WorkspaceContext context = workspaceAccessService.resolve(
                    workspaceId, bearerToken, userId);
            request.setAttribute(WorkspaceContext.REQUEST_ATTRIBUTE, context);
            filterChain.doFilter(request, response);

        } catch (BizException e) {
            sendError(response,
                    mapHttpStatus(e.getCode()),
                    e.getCode(),
                    e.getMessage());
        }
    }

    private boolean isProtectedPath(String path) {
        return PROTECTED_PATTERNS.stream()
                .anyMatch(pp -> pathMatcher.match(pp.pattern, path));
    }

    private int mapHttpStatus(int bizCode) {
        // 5xxxx → server error (503 for upstream unavailability)
        if (bizCode >= 50000) {
            return HttpServletResponse.SC_SERVICE_UNAVAILABLE;
        }
        // Use the same mapping as GlobalExceptionHandler
        if (bizCode >= 48000 && bizCode < 49000) {
            return switch (bizCode) {
                case 48001 -> HttpServletResponse.SC_NOT_FOUND;
                case 48002 -> HttpServletResponse.SC_FORBIDDEN;
                default -> HttpServletResponse.SC_CONFLICT;
            };
        }
        return HttpServletResponse.SC_BAD_REQUEST;
    }

    private void sendError(HttpServletResponse response, int httpStatus,
                           int bizCode, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = String.format(
                "{\"code\":%d,\"message\":\"%s\"}", bizCode, message);
        response.getWriter().write(body);
    }

    /**
     * Internal holder for a path pattern and whether it requires workspace context.
     */
    private record PathPattern(String pattern, boolean requiresWorkspace) {}
}
