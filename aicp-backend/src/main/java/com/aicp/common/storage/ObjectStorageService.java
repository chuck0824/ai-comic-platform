package com.aicp.common.storage;

import com.aicp.common.storage.local.LocalObjectStorage;
import com.aicp.common.storage.minio.MinioObjectStorage;
import com.aicp.common.storage.oss.OssObjectStorage;
import com.aicp.common.storage.qiniu.QiniuObjectStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Facade: new uploads go to the active provider; existing objects are routed by stored provider.
 */
@Slf4j
@Service
public class ObjectStorageService {

    private final StorageProperties properties;
    private final Map<StorageProvider, ObjectStorage> backends;

    public ObjectStorageService(StorageProperties properties,
                                LocalObjectStorage localObjectStorage,
                                MinioObjectStorage minioObjectStorage,
                                OssObjectStorage ossObjectStorage,
                                QiniuObjectStorage qiniuObjectStorage) {
        this.properties = properties;
        Map<StorageProvider, ObjectStorage> map = new EnumMap<>(StorageProvider.class);
        map.put(StorageProvider.LOCAL, localObjectStorage);
        map.put(StorageProvider.MINIO, minioObjectStorage);
        map.put(StorageProvider.OSS, ossObjectStorage);
        map.put(StorageProvider.QINIU, qiniuObjectStorage);
        this.backends = Map.copyOf(map);
        log.info("Object storage ready, active provider={}", properties.getProvider());
    }

    public StorageProvider activeProvider() {
        return StorageProvider.from(properties.getProvider());
    }

    public Duration signedUrlExpiry() {
        return properties.getSignedUrlExpiry() == null
                ? Duration.ofSeconds(300)
                : properties.getSignedUrlExpiry();
    }

    public ObjectStorage active() {
        return require(activeProvider());
    }

    public ObjectStorage require(StorageProvider provider) {
        ObjectStorage storage = backends.get(provider);
        if (storage == null) {
            throw StorageExceptions.notConfigured(provider);
        }
        return storage;
    }

    public ObjectStorage require(String providerCode) {
        return require(StorageProvider.from(providerCode));
    }

    /** Upload to the currently active provider/default bucket. */
    public StorageObjectRef upload(StorageUploadRequest request) {
        return active().upload(request);
    }

    public StorageObjectRef upload(String bucket, StorageUploadRequest request) {
        return active().upload(bucket, request);
    }

    public SignedUrl signDownloadUrl(StorageObjectRef ref) {
        return signDownloadUrl(ref, signedUrlExpiry());
    }

    public SignedUrl signDownloadUrl(StorageObjectRef ref, Duration ttl) {
        return require(ref.provider()).signDownloadUrl(ref, ttl);
    }

    public SignedUrl signDownloadUrl(String provider, String bucket, String key) {
        return signDownloadUrl(StorageObjectRef.of(provider, bucket, key));
    }

    public void delete(StorageObjectRef ref) {
        require(ref.provider()).delete(ref);
    }

    public boolean exists(StorageObjectRef ref) {
        return require(ref.provider()).exists(ref);
    }

    public InputStream openStream(StorageObjectRef ref) {
        return require(ref.provider()).openStream(ref);
    }
}
