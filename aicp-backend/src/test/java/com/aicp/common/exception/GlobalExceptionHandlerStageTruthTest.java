package com.aicp.common.exception;

import com.aicp.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerStageTruthTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void stageGateBlockedMapsTo422WithDetails() {
        Map<String, Object> details = Map.of(
                "blockers", List.of(Map.of("code", "X", "message", "阻断")),
                "warnings", List.of());
        BizException ex = new BizException(ErrorCode.STAGE_GATE_BLOCKED, "门禁存在阻断项", details);

        ResponseEntity<ApiResponse<Object>> response = handler.handleBizException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(43010);
        assertThat(response.getBody().getData()).isEqualTo(details);
    }

    @Test
    void artifactNotPersistedMapsTo422() {
        BizException ex = new BizException(ErrorCode.ARTIFACT_NOT_PERSISTED);
        ResponseEntity<ApiResponse<Object>> response = handler.handleBizException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void stageNotFoundMapsTo404() {
        BizException ex = new BizException(ErrorCode.STAGE_NOT_FOUND);
        ResponseEntity<ApiResponse<Object>> response = handler.handleBizException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
