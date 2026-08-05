package com.aicp.common.storage;

import java.io.InputStream;

/**
 * Upload payload. {@code key} may be null to let the active provider generate one.
 */
public record StorageUploadRequest(
        String key,
        InputStream content,
        long contentLength,
        String contentType
) {
    public StorageUploadRequest {
        if (content == null) {
            throw new IllegalArgumentException("content is required");
        }
        if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength must be >= 0");
        }
    }
}
