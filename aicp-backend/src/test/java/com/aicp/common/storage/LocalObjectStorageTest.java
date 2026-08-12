package com.aicp.common.storage;

import com.aicp.common.storage.local.LocalObjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class LocalObjectStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadSignExistsDeleteRoundTrip() {
        StorageProperties.Local props = new StorageProperties.Local();
        props.setRoot(tempDir.toString());
        props.setBucket("aicp-assets");
        props.setPublicBaseUrl("http://localhost:8080/api/v1/storage/local");

        LocalObjectStorage storage = new LocalObjectStorage(props);
        byte[] payload = "hello-storage".getBytes(StandardCharsets.UTF_8);

        StorageObjectRef ref = storage.upload(new StorageUploadRequest(
                "demo/hello.txt",
                new ByteArrayInputStream(payload),
                payload.length,
                "text/plain"));

        assertEquals(StorageProvider.LOCAL, ref.provider());
        assertEquals("aicp-assets", ref.bucket());
        assertEquals("demo/hello.txt", ref.key());
        assertTrue(storage.exists(ref));

        SignedUrl signed = storage.signDownloadUrl(ref, Duration.ofMinutes(5));
        assertTrue(signed.url().contains("/api/v1/storage/local/aicp-assets/demo/hello.txt"));
        assertNotNull(signed.expiresAt());

        storage.delete(ref);
        assertFalse(storage.exists(ref));
    }
}
