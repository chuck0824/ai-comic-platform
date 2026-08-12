package com.aicp.common.storage.minio;

import com.aicp.common.exception.BizException;
import com.aicp.common.storage.*;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class MinioObjectStorage implements ObjectStorage {

    private final StorageProperties.Minio props;
    private volatile MinioClient client;

    @Override
    public StorageProvider provider() {
        return StorageProvider.MINIO;
    }

    @Override
    public String defaultBucket() {
        return props.getBucket();
    }

    @Override
    public StorageObjectRef upload(StorageUploadRequest request) {
        return upload(defaultBucket(), request);
    }

    @Override
    public StorageObjectRef upload(String bucket, StorageUploadRequest request) {
        ensureBucket(bucket);
        String key = StorageKeys.resolve(request.key());
        try {
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(request.content(), request.contentLength(), -1);
            if (request.contentType() != null && !request.contentType().isBlank()) {
                builder.contentType(request.contentType());
            }
            client().putObject(builder.build());
            return new StorageObjectRef(StorageProvider.MINIO, bucket, key);
        } catch (Exception e) {
            throw StorageExceptions.wrap("minio upload", e);
        }
    }

    @Override
    public SignedUrl signDownloadUrl(StorageObjectRef ref, Duration ttl) {
        if (!exists(ref)) {
            throw StorageExceptions.missing(ref);
        }
        try {
            int seconds = Math.max(1, (int) ttl.getSeconds());
            String url = client().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(ref.bucket())
                            .object(ref.key())
                            .expiry(seconds, TimeUnit.SECONDS)
                            .build());
            if (props.getPublicBaseUrl() != null && !props.getPublicBaseUrl().isBlank()
                    && props.getEndpoint() != null) {
                url = url.replace(trimSlash(props.getEndpoint()), trimSlash(props.getPublicBaseUrl()));
            }
            return new SignedUrl(url, Instant.now().plus(ttl));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw StorageExceptions.wrap("minio sign", e);
        }
    }

    @Override
    public void delete(StorageObjectRef ref) {
        try {
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(ref.bucket())
                    .object(ref.key())
                    .build());
        } catch (Exception e) {
            throw StorageExceptions.wrap("minio delete", e);
        }
    }

    @Override
    public boolean exists(StorageObjectRef ref) {
        try {
            client().statObject(StatObjectArgs.builder()
                    .bucket(ref.bucket())
                    .object(ref.key())
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public InputStream openStream(StorageObjectRef ref) {
        try {
            return client().getObject(GetObjectArgs.builder()
                    .bucket(ref.bucket())
                    .object(ref.key())
                    .build());
        } catch (Exception e) {
            throw StorageExceptions.wrap("minio open", e);
        }
    }

    private void ensureBucket(String bucket) {
        try {
            boolean found = client().bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!found) {
                client().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw StorageExceptions.wrap("minio ensureBucket", e);
        }
    }

    private MinioClient client() {
        MinioClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = MinioClient.builder()
                            .endpoint(props.getEndpoint())
                            .credentials(props.getAccessKey(), props.getSecretKey())
                            .build();
                    client = local;
                }
            }
        }
        return local;
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
