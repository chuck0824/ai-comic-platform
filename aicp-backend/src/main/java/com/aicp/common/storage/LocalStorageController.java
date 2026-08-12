package com.aicp.common.storage;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.storage.local.LocalObjectStorage;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves files for {@code storage.provider=local} signed download URLs.
 */
@RestController
@RequestMapping("/api/v1/storage/local")
@RequiredArgsConstructor
public class LocalStorageController {

    private final LocalObjectStorage localObjectStorage;

    @GetMapping("/{bucket}/{*key}")
    public void download(@PathVariable String bucket,
                         @PathVariable("key") String key,
                         HttpServletResponse response) throws Exception {
        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        if (normalizedKey.isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "invalid local storage path");
        }
        StorageObjectRef ref = new StorageObjectRef(StorageProvider.LOCAL, bucket, normalizedKey);
        if (!localObjectStorage.exists(ref)) {
            throw StorageExceptions.missing(ref);
        }

        String contentType = Files.probeContentType(Path.of(normalizedKey));
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        response.setHeader(HttpHeaders.CONTENT_TYPE, contentType);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=60");

        try (InputStream in = localObjectStorage.openStream(ref);
             OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        }
    }
}
