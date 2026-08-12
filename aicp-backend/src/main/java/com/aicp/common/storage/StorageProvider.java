package com.aicp.common.storage;

import java.util.Locale;

/**
 * Supported object-storage backends.
 */
public enum StorageProvider {
    MINIO,
    OSS,
    QINIU,
    LOCAL;

    public static StorageProvider from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("storage provider is required");
        }
        return StorageProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
