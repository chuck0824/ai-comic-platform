package com.aicp.common.storage;

/**
 * Encode / decode storage refs for legacy string columns (e.g. script_upload_files.storage_path).
 * Format: {@code storage://{provider}/{bucket}/{key}}
 */
public final class StorageRefCodec {

    public static final String PREFIX = "storage://";

    private StorageRefCodec() {
    }

    public static String encode(StorageObjectRef ref) {
        return PREFIX + ref.provider().code() + "/" + ref.bucket() + "/" + ref.key();
    }

    public static boolean isEncoded(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public static StorageObjectRef decode(String value) {
        if (!isEncoded(value)) {
            throw new IllegalArgumentException("not a storage ref: " + value);
        }
        String rest = value.substring(PREFIX.length());
        int first = rest.indexOf('/');
        int second = rest.indexOf('/', first + 1);
        if (first <= 0 || second <= first + 1 || second >= rest.length() - 1) {
            throw new IllegalArgumentException("invalid storage ref: " + value);
        }
        String provider = rest.substring(0, first);
        String bucket = rest.substring(first + 1, second);
        String key = rest.substring(second + 1);
        return StorageObjectRef.of(provider, bucket, key);
    }
}
