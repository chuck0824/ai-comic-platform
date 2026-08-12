package com.aicp.common.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StorageProviderTest {

    @Test
    void parsesCaseInsensitiveCodes() {
        assertEquals(StorageProvider.MINIO, StorageProvider.from("minio"));
        assertEquals(StorageProvider.OSS, StorageProvider.from("OSS"));
        assertEquals(StorageProvider.QINIU, StorageProvider.from("QiNiu"));
        assertEquals(StorageProvider.LOCAL, StorageProvider.from("local"));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> StorageProvider.from(" "));
    }
}
