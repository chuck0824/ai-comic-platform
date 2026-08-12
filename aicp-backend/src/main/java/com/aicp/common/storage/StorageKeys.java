package com.aicp.common.storage;

import java.time.LocalDate;
import java.util.UUID;

public final class StorageKeys {

    private StorageKeys() {
    }

    static String resolve(String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested.replace('\\', '/').replaceAll("^/+", "");
        }
        LocalDate today = LocalDate.now();
        return "uploads/%04d/%02d/%02d/%s".formatted(
                today.getYear(), today.getMonthValue(), today.getDayOfMonth(),
                UUID.randomUUID().toString().replace("-", ""));
    }
}
