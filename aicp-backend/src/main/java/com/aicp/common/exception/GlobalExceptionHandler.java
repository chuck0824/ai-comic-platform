package com.aicp.common.exception;

import com.aicp.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity
                .status(mapHttpStatus(e.getCode()))
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<String>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.PARAM_INVALID.getCode(), detail));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        if (e.getMessage() != null && e.getMessage().contains("未登录")) {
            log.warn("认证状态异常: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(40003, e.getMessage()));
        }
        log.warn("状态异常: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.PARAM_INVALID.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("未预期异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(), "服务器繁忙，请稍后重试"));
    }

    private HttpStatus mapHttpStatus(int code) {
        // 5xxxx → server error (500), not client error (400)
        if (code >= 50000) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        // 43xxx → content project errors
        if (code >= 43000 && code < 44000) {
            return switch (code) {
                case 43001 -> HttpStatus.NOT_FOUND;
                case 43002 -> HttpStatus.FORBIDDEN;
                default -> HttpStatus.CONFLICT; // 43003–43007 → 409
            };
        }
        // 45xxx → storyboard professional editor errors
        if (code >= 45000 && code < 46000) {
            return switch (code) {
                case 45001, 45002 -> HttpStatus.NOT_FOUND;
                case 45009, 45010 -> HttpStatus.BAD_REQUEST;
                default -> HttpStatus.CONFLICT; // 45003–45008, 45011 → 409
            };
        }
        // 47xxx → SOP service errors (legacy)
        if (code >= 47000 && code < 48000) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        // 72xxx → SOP production check errors
        if (code >= 72000 && code < 73000) {
            return switch (code) {
                case 72001 -> HttpStatus.NOT_FOUND;
                case 72002 -> HttpStatus.GONE;
                case 72003 -> HttpStatus.UNPROCESSABLE_ENTITY;
                case 72005 -> HttpStatus.UNPROCESSABLE_ENTITY;
                case 72006 -> HttpStatus.SERVICE_UNAVAILABLE;
                default -> HttpStatus.CONFLICT; // 72004 → 409
            };
        }
        // 46xxx → canvas / generation-task errors
        if (code >= 46000 && code < 47000) {
            return switch (code) {
                case 46020 -> HttpStatus.NOT_FOUND;                // GENERATION_TASK_NOT_FOUND → 404
                case 46021 -> HttpStatus.CONFLICT;                 // GENERATION_TASK_STATE_CONFLICT → 409
                default -> HttpStatus.BAD_REQUEST;
            };
        }
        // 48xxx → asset market / workbench errors
        if (code >= 48000 && code < 49000) {
            return switch (code) {
                case 48001 -> HttpStatus.NOT_FOUND;               // ASSET_NOT_FOUND → 404
                case 48002 -> HttpStatus.FORBIDDEN;               // ASSET_PERMISSION_DENIED → 403
                case 48011 -> HttpStatus.GONE;                    // ASSET_PURGED → 410
                case 48003, 48004, 48006, 48009, 48013, 46021 -> HttpStatus.CONFLICT;
                case 48005, 48007, 48008, 48010, 48014 -> HttpStatus.UNPROCESSABLE_ENTITY;
                case 48015 -> HttpStatus.SERVICE_UNAVAILABLE;     // DOWNLOAD_SIGN_FAILED → 503
                case 48016 -> HttpStatus.CONFLICT;                // SETTLEMENT_FAILED → 409
                case 48017 -> HttpStatus.INTERNAL_SERVER_ERROR;   // COMPENSATION_EXHAUSTED → 500
                default -> HttpStatus.BAD_REQUEST;
            };
        }
        // 49xxx → agent configuration errors
        if (code >= 49000 && code < 50000) {
            return switch (code) {
                case 49020, 49021, 49022 -> HttpStatus.NOT_FOUND;
                case 49023 -> HttpStatus.FORBIDDEN;
                case 49024 -> HttpStatus.BAD_REQUEST;
                case 49025, 49026 -> HttpStatus.CONFLICT;
                case 49027 -> HttpStatus.UNPROCESSABLE_ENTITY;
                default -> HttpStatus.BAD_REQUEST;
            };
        }
        return switch (code) {
            case 40003 -> HttpStatus.UNAUTHORIZED;
            case 40004 -> HttpStatus.FORBIDDEN;
            case 40005 -> HttpStatus.NOT_FOUND;
            case 40006 -> HttpStatus.CONFLICT;
            case 40001 -> HttpStatus.TOO_MANY_REQUESTS;
            case 41012 -> HttpStatus.SERVICE_UNAVAILABLE; // WORKSPACE_UPSTREAM_UNAVAILABLE
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
