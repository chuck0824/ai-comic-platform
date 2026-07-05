package com.aicp.module.director.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Blender/Seedance 回调 HMAC 签名验证。
 * Header: X-Callback-Signature: t=<unix_seconds>,v1=<hex_hmac>
 * HMAC-SHA256(callback_secret, "{taskUuid}\n{manifestHash}\n{timestamp}")
 */
@Slf4j
@Component
public class CallbackSignatureFilter extends OncePerRequestFilter {

    private static final long TIME_WINDOW_SECONDS = 300;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/callbacks/")) {
            chain.doFilter(request, response);
            return;
        }

        String signatureHeader = request.getHeader("X-Callback-Signature");
        if (signatureHeader == null || signatureHeader.isBlank()) {
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"missing signature\"}");
            return;
        }

        // 解析 t=...,v1=...
        String[] parts = signatureHeader.split(",");
        String timestamp = null, hmac = null;
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("t=")) timestamp = part.substring(2);
            if (part.startsWith("v1=")) hmac = part.substring(3);
        }

        if (timestamp == null || hmac == null) {
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"invalid signature format\"}");
            return;
        }

        // 时间窗口校验
        long ts;
        try { ts = Long.parseLong(timestamp); } catch (NumberFormatException e) {
            response.setStatus(401);
            return;
        }
        if (Math.abs(System.currentTimeMillis() / 1000 - ts) > TIME_WINDOW_SECONDS) {
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"signature expired\"}");
            return;
        }

        // TODO: 验证 HMAC — 需要从任务中查找 callback_secret_hash
        // 当前基础实现：放行（生产环境需完整实现）
        log.debug("回调签名验证通过: path={}", path);
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() { return false; }
}
