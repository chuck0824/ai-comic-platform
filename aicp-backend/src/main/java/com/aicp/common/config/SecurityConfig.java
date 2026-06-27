package com.aicp.common.config;

import com.aicp.common.util.JwtUtil;
import com.aicp.common.util.RedisUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final Environment environment;

    /** 生产环境 CORS 允许的前端域名（可通过环境变量覆盖） */
    @Value("${app.cors.allowed-origins:http://localhost:8080,http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 判断是否为 dev 环境
        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");

        http
            // CSRF 禁用：JWT 无状态 API 的标准做法
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // === 安全响应头 ===
            .headers(headers -> {
                // 内容安全策略：防止 XSS
                headers.contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' data: https: blob:; " +
                        "font-src 'self' data:; " +
                        "connect-src 'self' https: wss:; " +
                        "media-src 'self' blob: https:; " +
                        "frame-ancestors 'none'; " +
                        "base-uri 'self'; " +
                        "form-action 'self'"));

                // 防止 MIME 类型嗅探
                headers.contentTypeOptions(withDefaults -> {});

                // XSS 过滤器
                headers.xssProtection(xss -> xss
                    .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK));

                // Referrer 策略
                headers.referrerPolicy(rp -> rp
                    .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));

                // 禁止被 iframe 嵌入（防止 clickjacking）
                if (isDev) {
                    // dev 环境允许 H2 控制台同源 iframe
                    headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin);
                } else {
                    headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny);
                }

                // 权限策略
                headers.permissionsPolicy(pp -> pp
                    .policy("camera=(), microphone=(), geolocation=(), payment=()"));
            })

            // === 访问控制 ===
            .authorizeHttpRequests(auth -> {
                // 公开端点（无需认证）
                auth.requestMatchers(
                    "/api/v1/auth/**",          // 登录/注册/刷新Token/短信
                    "/api/v1/callback/**",       // 第三方回调（微信/SSO）
                    "/api/health/**",            // 健康检查（K8s probes）
                    "/error"                      // 错误页面
                ).permitAll();

                // 静态资源（无需认证）
                auth.requestMatchers(
                    "/",
                    "/index.html",
                    "/assets/**",
                    "/favicon.ico"
                ).permitAll();

                // H2 控制台（仅 dev 环境开放，无需认证）
                if (isDev) {
                    auth.requestMatchers("/h2-console/**").permitAll();
                }

                // OpenAPI（需要 api_access 权限）
                auth.requestMatchers("/openapi/v1/**").hasAuthority("api_access");

                // 企业工作台 API（需要企业管理员或部门主管权限）
                auth.requestMatchers("/api/v1/enterprise/**").hasAnyAuthority("ent_admin", "dept_head");

                // === 所有 API 请求需要认证 ===
                auth.requestMatchers("/api/**").authenticated();

                // === SPA 前端路由：全部放行 ===
                // 前端 Vue Router 的 beforeEach 守卫负责页面级认证
                auth.anyRequest().permitAll();
            })

            // 速率限制（先执行，Ordered 确保在 JWT 之前）
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            // JWT 认证过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Slf4j
    @Component
    @RequiredArgsConstructor
    public static class JwtAuthenticationFilter extends OncePerRequestFilter {

        private final JwtUtil jwtUtil;
        private final RedisUtil redisUtil;

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                         HttpServletResponse response,
                                         FilterChain filterChain)
                throws ServletException, IOException {
            String token = extractToken(request);

            // 无 token：放行，由 Spring Security 按 URL 规则处理（公开端点允许，受保护端点拒绝）
            if (token == null) {
                filterChain.doFilter(request, response);
                return;
            }

            try {
                // token 在黑名单中（已登出）：返回 401，提示重新登录
                if (redisUtil.isTokenBlacklisted(token)) {
                    sendUnauthorizedResponse(response, "Token已失效，请重新登录");
                    return;
                }

                // token 无效或已过期：返回 401，前端自动跳转登录页
                if (!jwtUtil.validateToken(token)) {
                    sendUnauthorizedResponse(response, "登录已过期，请重新登录");
                    return;
                }

                Long userId = jwtUtil.getUserId(token);
                String role = jwtUtil.getRole(token);
                List<String> permissions = jwtUtil.getPermissions(token);

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                if (role != null) {
                    authorities.add(new SimpleGrantedAuthority(role));
                }
                if (permissions != null) {
                    authorities.addAll(permissions.stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList());
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, token, authorities);
                authentication.setDetails(Map.of(
                        "userId", userId,
                        "uuid", jwtUtil.getUserUuid(token),
                        "type", jwtUtil.getAccountType(token)
                ));

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                log.debug("JWT认证失败: {}", e.getMessage());
                SecurityContextHolder.clearContext();
                sendUnauthorizedResponse(response, "认证失败，请重新登录");
                return;
            }

            filterChain.doFilter(request, response);
        }

        /** 返回 401 JSON 响应，前端拦截器自动跳转登录页 */
        private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":40100,\"message\":\"" + message + "\"}");
        }

        private String extractToken(HttpServletRequest request) {
            String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
                return bearer.substring(7);
            }
            return null;
        }
    }
}
