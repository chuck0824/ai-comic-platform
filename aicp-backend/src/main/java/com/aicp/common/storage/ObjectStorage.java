package com.aicp.common.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * Unified object-storage contract for MinIO / Aliyun OSS / Qiniu / local.
 */
public interface ObjectStorage {

    StorageProvider provider();

    /** Default bucket for this backend when callers omit one. */
    String defaultBucket();

    StorageObjectRef upload(StorageUploadRequest request);

    StorageObjectRef upload(String bucket, StorageUploadRequest request);

    SignedUrl signDownloadUrl(StorageObjectRef ref, Duration ttl);

    void delete(StorageObjectRef ref);

    boolean exists(StorageObjectRef ref);

    InputStream openStream(StorageObjectRef ref);
}
