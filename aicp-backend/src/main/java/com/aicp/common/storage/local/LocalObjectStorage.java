package com.aicp.common.storage.local;

import com.aicp.common.storage.*;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
public class LocalObjectStorage implements ObjectStorage {

    private final StorageProperties.Local props;

    @Override
    public StorageProvider provider() {
        return StorageProvider.LOCAL;
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
        String key = StorageKeys.resolve(request.key());
        Path target = resolvePath(bucket, key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(request.content(), target, StandardCopyOption.REPLACE_EXISTING);
            return new StorageObjectRef(StorageProvider.LOCAL, bucket, key);
        } catch (IOException e) {
            throw StorageExceptions.wrap("local upload", e);
        }
    }

    @Override
    public SignedUrl signDownloadUrl(StorageObjectRef ref, Duration ttl) {
        requireExists(ref);
        Instant expiresAt = Instant.now().plus(ttl);
        String base = trimSlash(props.getPublicBaseUrl());
        String url = base + "/" + encodePath(ref.bucket()) + "/" + encodePath(ref.key())
                + "?expires=" + expiresAt.getEpochSecond();
        return new SignedUrl(url, expiresAt);
    }

    @Override
    public void delete(StorageObjectRef ref) {
        try {
            Files.deleteIfExists(resolvePath(ref.bucket(), ref.key()));
        } catch (IOException e) {
            throw StorageExceptions.wrap("local delete", e);
        }
    }

    @Override
    public boolean exists(StorageObjectRef ref) {
        return Files.isRegularFile(resolvePath(ref.bucket(), ref.key()));
    }

    @Override
    public InputStream openStream(StorageObjectRef ref) {
        requireExists(ref);
        try {
            return Files.newInputStream(resolvePath(ref.bucket(), ref.key()));
        } catch (IOException e) {
            throw StorageExceptions.wrap("local open", e);
        }
    }

    private void requireExists(StorageObjectRef ref) {
        if (!exists(ref)) {
            throw StorageExceptions.missing(ref);
        }
    }

    private Path resolvePath(String bucket, String key) {
        Path root = Path.of(props.getRoot()).toAbsolutePath().normalize();
        Path resolved = root.resolve(bucket).resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("invalid storage key");
        }
        return resolved;
    }

    private static String trimSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String encodePath(String value) {
        String[] parts = value.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
