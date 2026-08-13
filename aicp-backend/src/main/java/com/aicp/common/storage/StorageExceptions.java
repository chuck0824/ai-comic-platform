package com.aicp.common.storage;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;

public final class StorageExceptions {

    private StorageExceptions() {
    }

    public static BizException wrap(String action, Exception e) {
        return new BizException(ErrorCode.ASSET_DOWNLOAD_SIGN_FAILED,
                action + " failed: " + e.getMessage());
    }

    public static BizException missing(StorageObjectRef ref) {
        return new BizException(ErrorCode.ASSET_FILE_MISSING,
                "object not found: " + ref.provider().code() + "/" + ref.bucket() + "/" + ref.key());
    }

    public static BizException notConfigured(StorageProvider provider) {
        return new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                "storage provider not configured: " + provider.code());
    }
}
