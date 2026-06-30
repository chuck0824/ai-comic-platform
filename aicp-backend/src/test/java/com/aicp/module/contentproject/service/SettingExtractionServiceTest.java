package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.module.contentproject.entity.SettingExtractionBatch;
import com.aicp.module.contentproject.entity.SettingExtractionCandidate;
import com.aicp.module.contentproject.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettingExtractionService 单元测试")
class SettingExtractionServiceTest {

    @Mock SettingExtractionBatchMapper batchMapper;
    @Mock SettingExtractionCandidateMapper candidateMapper;
    @Mock ProjectSettingEntityMapper settingEntityMapper;
    @Mock ProjectSettingVersionMapper settingVersionMapper;
    @Mock ProjectSettingService settingService;
    @Mock ProjectAccessService accessService;
    @Mock ProjectContextPublisher contextPublisher;

    @InjectMocks
    SettingExtractionService service;

    @Nested
    @DisplayName("createExtraction")
    class CreateTests {

        @Test
        @DisplayName("缺少 idempotency_key 抛出 PARAM_INVALID")
        void missingIdempotencyKeyThrows() {
            assertThatThrownBy(() -> service.createExtraction(1L, 10L, Map.of()))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("idempotency_key");
        }

        @Test
        @DisplayName("重复 idempotency_key 返回已有批次")
        void duplicateKeyReturnsExisting() {
            SettingExtractionBatch existing = new SettingExtractionBatch();
            existing.setId(5L);
            existing.setProjectId(10L);
            existing.setIdempotencyKey("key-1");
            existing.setStatus("review_ready");
            when(batchMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

            var result = service.createExtraction(1L, 10L, Map.of("idempotency_key", "key-1"));
            assertThat(result.get("id")).isEqualTo(5L);
            verify(batchMapper, never()).insert(any());
        }

        @Test
        @DisplayName("新批次创建成功")
        void createsNewBatch() {
            when(batchMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            doAnswer(inv -> { SettingExtractionBatch b = inv.getArgument(0); b.setId(10L); return 1; })
                    .when(batchMapper).insert(any(SettingExtractionBatch.class));

            var result = service.createExtraction(1L, 10L, Map.of("idempotency_key", "key-new"));
            assertThat(result.get("status")).isEqualTo("queued");
        }
    }

    @Nested
    @DisplayName("applyExtraction")
    class ApplyTests {

        @Test
        @DisplayName("非 review_ready 状态拒绝应用")
        void rejectsNonReviewReadyStatus() {
            SettingExtractionBatch batch = new SettingExtractionBatch();
            batch.setId(1L);
            batch.setProjectId(10L);
            batch.setStatus("queued");
            when(batchMapper.selectById(1L)).thenReturn(batch);

            assertThatThrownBy(() -> service.applyExtraction(1L, 10L, 1L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("不允许应用");
        }
    }

    @Nested
    @DisplayName("retryExtraction")
    class RetryTests {

        @Test
        @DisplayName("只有失败状态可重试")
        void onlyFailedCanRetry() {
            SettingExtractionBatch batch = new SettingExtractionBatch();
            batch.setId(1L);
            batch.setProjectId(10L);
            batch.setStatus("applied");
            when(batchMapper.selectById(1L)).thenReturn(batch);

            assertThatThrownBy(() -> service.retryExtraction(1L, 10L, 1L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("可以重试");
        }
    }
}
