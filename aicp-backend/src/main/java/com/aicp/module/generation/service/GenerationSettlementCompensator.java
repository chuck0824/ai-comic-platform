package com.aicp.module.generation.service;

import com.aicp.module.generation.entity.GenerationSettlementOutbox;
import com.aicp.module.generation.entity.GenerationTask;
import com.aicp.module.generation.mapper.GenerationSettlementOutboxMapper;
import com.aicp.module.generation.mapper.GenerationTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Periodically retries failed settlement outbox rows.
 *
 * Retry schedule: 1 min, 5 min, 30 min, 2 hours.
 * After 4 failures the row is marked EXHAUSTED and requires manual intervention.
 *
 * For ASSET_CREATE stage: re-loads the task and retries settlement using
 * output metadata from the task record.
 * For NODE_WRITEBACK stage: re-attempts canvas node write-back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationSettlementCompensator {

    private final GenerationSettlementOutboxMapper outboxMapper;
    private final GenerationTaskMapper taskMapper;
    private final GenerationSettlementService settlementService;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 4;
    private static final long[] RETRY_DELAY_MINUTES = {1, 5, 30, 120};

    @Scheduled(fixedDelay = 60_000)
    public void compensate() {
        List<GenerationSettlementOutbox> pending = outboxMapper.selectList(
                new LambdaQueryWrapper<GenerationSettlementOutbox>()
                        .eq(GenerationSettlementOutbox::getStatus, "PENDING")
                        .le(GenerationSettlementOutbox::getNextRetryAt, LocalDateTime.now())
                        .last("LIMIT 20"));

        for (GenerationSettlementOutbox row : pending) {
            try {
                int retries = row.getRetryCount() + 1;
                if (retries > MAX_RETRIES) {
                    row.setStatus("EXHAUSTED");
                    row.setLastError("Max retries (" + MAX_RETRIES + ") exceeded");
                    row.setRetryCount(retries);
                    outboxMapper.updateById(row);
                    log.error("Settlement outbox {} exhausted for task {}", row.getId(), row.getTaskId());
                    continue;
                }

                GenerationTask task = taskMapper.selectById(row.getTaskId());
                if (task == null) {
                    row.setStatus("EXHAUSTED");
                    row.setLastError("Task " + row.getTaskId() + " not found");
                    row.setRetryCount(retries);
                    outboxMapper.updateById(row);
                    continue;
                }

                boolean retried = switch (row.getStage()) {
                    case "ASSET_CREATE" -> retryAssetCreate(task, row);
                    case "NODE_WRITEBACK" -> retryNodeWriteback(task, row);
                    default -> {
                        log.warn("Unknown outbox stage {} for task {}", row.getStage(), row.getTaskId());
                        yield false;
                    }
                };

                if (retried) {
                    row.setStatus("PROCESSING");
                    row.setLastError(null);
                } else {
                    row.setLastError(row.getLastError() != null ? row.getLastError() : "Retry not applicable");
                    scheduleNextRetry(row, retries);
                }
                row.setRetryCount(retries);
                outboxMapper.updateById(row);
            } catch (Exception e) {
                log.error("Compensation failed for outbox {}: {}", row.getId(), e.getMessage());
                scheduleNextRetry(row, row.getRetryCount() + 1);
                row.setLastError(truncate(e.getMessage(), 500));
                outboxMapper.updateById(row);
            }
        }
    }

    /**
     * Retry ASSET_CREATE: rebuild SettlementInput from task outputAssets and retry.
     */
    private boolean retryAssetCreate(GenerationTask task, GenerationSettlementOutbox row) {
        if (!"succeeded".equals(task.getStatus())) {
            row.setLastError("Task status is '" + task.getStatus() + "', not 'succeeded'");
            return false;
        }

        try {
            GenerationSettlementService.SettlementInput input = buildSettlementInput(task);
            if (input == null) {
                row.setLastError("Cannot reconstruct settlement input from task outputAssets");
                return false;
            }
            settlementService.settle(task, input);
            log.info("Outbox {} ASSET_CREATE retry succeeded for task {}", row.getId(), row.getTaskId());
            return true;
        } catch (Exception e) {
            log.warn("ASSET_CREATE retry failed for task {}: {}", row.getTaskId(), e.getMessage());
            row.setLastError(truncate(e.getMessage(), 500));
            return false;
        }
    }

    /**
     * Retry NODE_WRITEBACK: write settled asset back to canvas node.
     */
    private boolean retryNodeWriteback(GenerationTask task, GenerationSettlementOutbox row) {
        // Node write-back is delegated to GenerationExecutor.onTaskComplete.
        // The compensator re-triggers by re-processing the completed task.
        if (task.getNodeId() == null) {
            row.setLastError("Task has no associated node for write-back");
            return false;
        }
        try {
            // Re-settle first if needed, then node write-back happens in executor
            GenerationSettlementService.SettlementInput input = buildSettlementInput(task);
            if (input != null) {
                settlementService.settle(task, input);
            }
            log.info("Outbox {} NODE_WRITEBACK retry triggered for task {}", row.getId(), row.getTaskId());
            return true;
        } catch (Exception e) {
            log.warn("NODE_WRITEBACK retry failed for task {}: {}", row.getTaskId(), e.getMessage());
            row.setLastError(truncate(e.getMessage(), 500));
            return false;
        }
    }

    /**
     * Reconstruct SettlementInput from task outputAssets JSON.
     */
    private GenerationSettlementService.SettlementInput buildSettlementInput(GenerationTask task) {
        try {
            String json = task.getOutputAssets();
            if (json == null || json.isBlank()) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> output = objectMapper.readValue(json, Map.class);

            String storageKey = stringField(output, "storage_key", "key", "url", "path");
            if (storageKey == null) return null;

            return new GenerationSettlementService.SettlementInput(
                    stringField(output, "storage_provider", "provider"),
                    stringField(output, "storage_bucket", "bucket"),
                    storageKey,
                    stringField(output, "mime_type", "mimeType", "content_type"),
                    longField(output, "file_size", "fileSize", "size"),
                    intField(output, "width"),
                    intField(output, "height"),
                    intField(output, "duration_ms", "durationMs", "duration"),
                    stringField(output, "preview_url", "previewUrl", "thumbnail_url"),
                    stringField(output, "checksum", "sha256", "md5"));
        } catch (Exception e) {
            log.warn("Failed to parse outputAssets for task {}: {}", task.getId(), e.getMessage());
            return null;
        }
    }

    private void scheduleNextRetry(GenerationSettlementOutbox row, int retries) {
        int idx = Math.min(retries - 1, RETRY_DELAY_MINUTES.length - 1);
        row.setNextRetryAt(LocalDateTime.now().plusMinutes(RETRY_DELAY_MINUTES[Math.max(0, idx)]));
    }

    // -- JSON helpers --

    private String stringField(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }

    private Long longField(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v instanceof Number n) return n.longValue();
            if (v instanceof String s) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private Integer intField(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v instanceof Number n) return n.intValue();
            if (v instanceof String s) {
                try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
    }
}
