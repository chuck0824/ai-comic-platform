package com.aicp.common.storage.oss;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aicp.common.exception.BizException;
import com.aicp.common.storage.*;
import lombok.RequiredArgsConstructor;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@RequiredArgsConstructor
public class OssObjectStorage implements ObjectStorage {

    private final StorageProperties.Oss props;
    private volatile OSS client;

    @Override
    public StorageProvider provider() {
        return StorageProvider.OSS;
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
        requireConfigured();
        String key = StorageKeys.resolve(request.key());
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(request.contentLength());
            if (request.contentType() != null && !request.contentType().isBlank()) {
                metadata.setContentType(request.contentType());
            }
            client().putObject(new PutObjectRequest(bucket, key, request.content(), metadata));
            return new StorageObjectRef(StorageProvider.OSS, bucket, key);
        } catch (Exception e) {
            throw StorageExceptions.wrap("oss upload", e);
        }
    }

    @Override
    public SignedUrl signDownloadUrl(StorageObjectRef ref, Duration ttl) {
        requireConfigured();
        if (!exists(ref)) {
            throw StorageExceptions.missing(ref);
        }
        try {
            Date expiration = Date.from(Instant.now().plus(ttl));
            GeneratePresignedUrlRequest request =
                    new GeneratePresignedUrlRequest(ref.bucket(), ref.key(), HttpMethod.GET);
            request.setExpiration(expiration);
            URL signed = client().generatePresignedUrl(request);
            String url = signed.toString();
            if (props.getPublicBaseUrl() != null && !props.getPublicBaseUrl().isBlank()) {
                // Replace host with CDN/custom domain while keeping query signature.
                URL original = signed;
                String base = trimSlash(props.getPublicBaseUrl());
                url = base + original.getFile();
            }
            return new SignedUrl(url, expiration.toInstant());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw StorageExceptions.wrap("oss sign", e);
        }
    }

    @Override
    public void delete(StorageObjectRef ref) {
        requireConfigured();
        try {
            client().deleteObject(ref.bucket(), ref.key());
        } catch (Exception e) {
            throw StorageExceptions.wrap("oss delete", e);
        }
    }

    @Override
    public boolean exists(StorageObjectRef ref) {
        requireConfigured();
        try {
            return client().doesObjectExist(ref.bucket(), ref.key());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public InputStream openStream(StorageObjectRef ref) {
        requireConfigured();
        try {
            return client().getObject(ref.bucket(), ref.key()).getObjectContent();
        } catch (Exception e) {
            throw StorageExceptions.wrap("oss open", e);
        }
    }

    private void requireConfigured() {
        if (isBlank(props.getAccessKeyId()) || isBlank(props.getAccessKeySecret())
                || isBlank(props.getEndpoint()) || isBlank(props.getBucket())) {
            throw StorageExceptions.notConfigured(StorageProvider.OSS);
        }
    }

    private OSS client() {
        OSS local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = new OSSClientBuilder().build(
                            props.getEndpoint(),
                            props.getAccessKeyId(),
                            props.getAccessKeySecret());
                    client = local;
                }
            }
        }
        return local;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
