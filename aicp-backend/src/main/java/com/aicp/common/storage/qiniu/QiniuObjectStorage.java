package com.aicp.common.storage.qiniu;

import com.aicp.common.exception.BizException;
import com.aicp.common.storage.*;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.DownloadUrl;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.Region;
import com.qiniu.util.Auth;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
public class QiniuObjectStorage implements ObjectStorage {

    private final StorageProperties.Qiniu props;
    private volatile Auth auth;
    private volatile UploadManager uploadManager;
    private volatile BucketManager bucketManager;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public StorageProvider provider() {
        return StorageProvider.QINIU;
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
            byte[] bytes = readAll(request.content(), request.contentLength());
            String token = auth().uploadToken(bucket, key);
            Response response = uploadManager().put(bytes, key, token, null,
                    request.contentType(), false);
            if (!response.isOK()) {
                throw new IllegalStateException("qiniu upload status=" + response.statusCode
                        + " body=" + response.bodyString());
            }
            return new StorageObjectRef(StorageProvider.QINIU, bucket, key);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw StorageExceptions.wrap("qiniu upload", e);
        }
    }

    @Override
    public SignedUrl signDownloadUrl(StorageObjectRef ref, Duration ttl) {
        requireConfigured();
        try {
            Instant expiresAt = Instant.now().plus(ttl);
            long deadline = expiresAt.getEpochSecond();
            String domain = trimSlash(props.getDomain());
            if (domain.isBlank()) {
                throw StorageExceptions.notConfigured(StorageProvider.QINIU);
            }
            // Private space signed URL; also works for public if auth is applied.
            DownloadUrl downloadUrl = new DownloadUrl(domain, props.isUseHttps(), ref.key());
            String url = downloadUrl.buildURL(auth(), deadline);
            return new SignedUrl(url, expiresAt);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw StorageExceptions.wrap("qiniu sign", e);
        }
    }

    @Override
    public void delete(StorageObjectRef ref) {
        requireConfigured();
        try {
            Response response = bucketManager().delete(ref.bucket(), ref.key());
            // 612 = no such file; treat as idempotent success
            if (!response.isOK() && response.statusCode != 612) {
                throw new IllegalStateException("qiniu delete status=" + response.statusCode
                        + " body=" + response.bodyString());
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw StorageExceptions.wrap("qiniu delete", e);
        }
    }

    @Override
    public boolean exists(StorageObjectRef ref) {
        requireConfigured();
        try {
            bucketManager().stat(ref.bucket(), ref.key());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public InputStream openStream(StorageObjectRef ref) {
        SignedUrl signed = signDownloadUrl(ref, Duration.ofMinutes(5));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(signed.url()))
                    .GET()
                    .timeout(Duration.ofMinutes(2))
                    .build();
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("qiniu open status=" + response.statusCode());
            }
            return response.body();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw StorageExceptions.wrap("qiniu open", e);
        }
    }

    private void requireConfigured() {
        if (isBlank(props.getAccessKey()) || isBlank(props.getSecretKey())
                || isBlank(props.getBucket()) || isBlank(props.getDomain())) {
            throw StorageExceptions.notConfigured(StorageProvider.QINIU);
        }
    }

    private Auth auth() {
        Auth local = auth;
        if (local == null) {
            synchronized (this) {
                local = auth;
                if (local == null) {
                    local = Auth.create(props.getAccessKey(), props.getSecretKey());
                    auth = local;
                }
            }
        }
        return local;
    }

    private UploadManager uploadManager() {
        UploadManager local = uploadManager;
        if (local == null) {
            synchronized (this) {
                local = uploadManager;
                if (local == null) {
                    local = new UploadManager(configuration());
                    uploadManager = local;
                }
            }
        }
        return local;
    }

    private BucketManager bucketManager() {
        BucketManager local = bucketManager;
        if (local == null) {
            synchronized (this) {
                local = bucketManager;
                if (local == null) {
                    local = new BucketManager(auth(), configuration());
                    bucketManager = local;
                }
            }
        }
        return local;
    }

    private Configuration configuration() {
        // Region.autoRegion() lets SDK pick; uploadUrl kept for documentation/ops.
        Configuration cfg = new Configuration(Region.autoRegion());
        if (!isBlank(props.getUploadUrl())) {
            // Keep default; callers can switch region via env in future if needed.
            cfg.useHttpsDomains = props.isUseHttps();
        }
        return cfg;
    }

    private static byte[] readAll(InputStream in, long contentLength) throws Exception {
        if (contentLength > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("qiniu upload payload too large for buffered upload");
        }
        if (contentLength >= 0) {
            byte[] buf = in.readNBytes((int) contentLength);
            if (buf.length != contentLength) {
                throw new IllegalStateException("unexpected EOF while reading upload stream");
            }
            return buf;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toByteArray();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimSlash(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        // DownloadUrl expects bare host or host with scheme depending on useHttps flag.
        if (v.startsWith("https://")) {
            v = v.substring("https://".length());
        } else if (v.startsWith("http://")) {
            v = v.substring("http://".length());
        }
        return v;
    }
}
