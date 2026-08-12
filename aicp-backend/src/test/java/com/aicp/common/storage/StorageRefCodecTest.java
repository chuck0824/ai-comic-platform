package com.aicp.common.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StorageRefCodecTest {

    @Test
    void encodeDecodeRoundTrip() {
        StorageObjectRef ref = StorageObjectRef.of("oss", "aicp-assets", "uploads/a/b.png");
        String encoded = StorageRefCodec.encode(ref);
        assertEquals("storage://oss/aicp-assets/uploads/a/b.png", encoded);
        assertTrue(StorageRefCodec.isEncoded(encoded));
        assertEquals(ref, StorageRefCodec.decode(encoded));
    }

    @Test
    void rejectsPlainPath() {
        assertFalse(StorageRefCodec.isEncoded("/tmp/file.txt"));
        assertThrows(IllegalArgumentException.class, () -> StorageRefCodec.decode("/tmp/file.txt"));
    }
}
