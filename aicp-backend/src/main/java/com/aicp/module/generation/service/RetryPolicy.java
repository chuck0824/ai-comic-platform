package com.aicp.module.generation.service;

import java.util.Set;

/**
 * 生成任务重试策略。
 * 指数退避：2s → 4s → 8s → 16s → 32s → 60s（上限）。
 * 总超时 15 分钟。
 */
public record RetryPolicy(
        Set<String> retryableErrors,
        int maxAutoRetries,
        long baseDelayMs,
        long maxDelayMs,
        long totalTimeoutMs
) {
    public static final Set<String> DEFAULT_RETRYABLE = Set.of(
            "PROVIDER_TIMEOUT", "PROVIDER_500", "RATE_LIMITED", "NETWORK_ERROR",
            "WORKER_OOM", "WORKER_TIMEOUT", "ASSET_DOWNLOAD_FAILURE"
    );

    public static final Set<String> DEFAULT_NON_RETRYABLE = Set.of(
            "CONTENT_SAFETY_REJECT", "INVALID_PROMPT", "ASSET_LICENSE_DENIED",
            "PORTRAIT_UNAUTHORIZED", "PROVIDER_400", "PROVIDER_401", "PROVIDER_403"
    );

    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(DEFAULT_RETRYABLE, 3, 2000, 60000, 900_000);
    }

    public boolean isRetryable(String errorCode) {
        return retryableErrors.contains(errorCode);
    }

    /** 计算第 attemptNo 次重试（0-based）的延迟 */
    public long nextDelayMs(int attemptNo) {
        return Math.min(baseDelayMs * (1L << attemptNo), maxDelayMs);
    }
}
