package com.aicp.common.storage;

/**
 * Immutable pointer to an object in a storage backend.
 * Persist these fields on assets; never persist temporary signed URLs.
 */
public record StorageObjectRef(
        StorageProvider provider,
        String bucket,
        String key
) {
    public StorageObjectRef {
        if (provider == null) {
            throw new IllegalArgumentException("provider is required");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket is required");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
    }

    public static StorageObjectRef of(String provider, String bucket, String key) {
        return new StorageObjectRef(StorageProvider.from(provider), bucket, key);
    }
}
