package com.aicp.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;
    private final Object details;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.details = null;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.details = null;
    }

    public BizException(ErrorCode errorCode, String message, Object details) {
        super(message);
        this.code = errorCode.getCode();
        this.details = details;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.details = null;
    }

    public BizException(int code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }
}
