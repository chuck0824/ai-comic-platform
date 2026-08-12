package com.aicp.module.generation.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.module.canvas.entity.CanvasNode;
import com.aicp.module.canvas.entity.CanvasProject;
import com.aicp.module.canvas.entity.StoryboardShot;
import com.aicp.module.canvas.mapper.CanvasNodeMapper;
import com.aicp.module.canvas.mapper.CanvasProjectMapper;
import com.aicp.module.canvas.mapper.StoryboardShotMapper;
import com.aicp.module.generation.entity.GenerationTask;
import com.aicp.module.generation.entity.PlatformAsset;
import com.aicp.module.generation.mapper.GenerationTaskMapper;
import com.aicp.module.generation.mapper.PlatformAssetMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 异步生成任务执行器
 * 负责：调用AI → 更新任务状态 → 回写节点 → 资产入库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationExecutor {

    private final AiRouter aiRouter;
    private final GenerationTaskMapper taskMapper;
    private final CanvasNodeMapper nodeMapper;
    private final CanvasProjectMapper projectMapper;
    private final StoryboardShotMapper shotMapper;
    private final PlatformAssetMapper platformAssetMapper;
    private final GenerationSettlementService settlementService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async("genTaskExecutor")
    public void execute(GenerationTask task) {
        try {
            log.info("开始执行生成任务: type={}, uuid={}", task.getType(), task.getUuid());
            task.setStartedAt(LocalDateTime.now());
            task.setStatus("running");
            taskMapper.updateById(task);

            aiRouter.executeTask(task.getId());

            GenerationTask updated = taskMapper.selectById(task.getId());
            if (updated == null || !"succeeded".equals(updated.getStatus())) {
                throw new RuntimeException(updated != null ? updated.getErrorMessage() : "任务执行失败");
            }

            writebackNode(updated);
            writebackShot(updated);
            registerAssets(updated);

            log.info("生成任务完成: type={}, uuid={}", task.getType(), task.getUuid());

        } catch (Exception e) {
            log.error("生成任务失败: uuid={}, error={}", task.getUuid(), e.getMessage());
            task.setStatus("failed");
            task.setErrorCode("EXECUTION_ERROR");
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    private void writebackNode(GenerationTask task) {
        if (task.getNodeId() == null) return;
        CanvasNode node = nodeMapper.selectById(task.getNodeId());
        if (node != null) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("task_id", task.getUuid());
            output.put("status", task.getStatus());
            output.put("output_assets", task.getOutputAssets());
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(
                        task.getOutputAssets() == null ? "{}" : task.getOutputAssets(), Map.class);
                if (parsed.get("url") != null) {
                    output.put("preview_url", parsed.get("url"));
                }
                if (parsed.get("preview_url") != null) {
                    output.put("preview_url", parsed.get("preview_url"));
                }
                if (parsed.get("storage_provider") != null) {
                    output.put("storage_provider", parsed.get("storage_provider"));
                    output.put("storage_bucket", parsed.get("storage_bucket"));
                    output.put("storage_key", parsed.get("storage_key"));
                }
                node.setOutputData(objectMapper.writeValueAsString(output));
                node.setStatus("completed");
                nodeMapper.updateById(node);
            } catch (Exception e) {
                log.warn("节点回写失败: nodeId={}", node.getId());
            }
        }
    }

    private void writebackShot(GenerationTask task) {
        if (task.getShotId() == null) return;
        StoryboardShot shot = shotMapper.selectById(task.getShotId());
        if (shot != null) {
            if ("image".equals(task.getType())) {
                shot.setImageStatus("succeeded".equals(task.getStatus()) ? "completed" : "failed");
            } else if ("video".equals(task.getType())) {
                shot.setVideoStatus("succeeded".equals(task.getStatus()) ? "completed" : "failed");
            }
            shotMapper.updateById(shot);
        }
    }

    private void registerAssets(GenerationTask task) {
        try {
            GenerationSettlementService.SettlementInput input = buildSettlementInput(task);
            if (input != null) {
                ensureWorkspaceFields(task);
                settlementService.settle(task, input);
                return;
            }

            PlatformAsset asset = new PlatformAsset();
            asset.setUuid("asset_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
            asset.setProjectId(task.getProjectId());
            asset.setSourceNodeId(task.getNodeId());
            asset.setSourceTaskId(task.getId());
            asset.setType(mapAssetType(task.getType()));
            asset.setName("生成结果 - " + task.getType());
            asset.setPrompt(task.getParameters());
            asset.setModelId(task.getModelId());
            asset.setOwnerUserId(resolveOwnerId(task.getProjectId()));
            platformAssetMapper.insert(asset);
        } catch (Exception e) {
            log.warn("资产入库失败: taskId={}, err={}", task.getId(), e.getMessage());
        }
    }

    private void ensureWorkspaceFields(GenerationTask task) {
        if (task.getCreatedBy() == null || task.getCreatedBy() == 0L) {
            task.setCreatedBy(resolveOwnerId(task.getProjectId()));
        }
        if (task.getWorkspaceId() == null || task.getWorkspaceId().isBlank()) {
            task.setWorkspaceId("personal_" + task.getCreatedBy());
        }
    }

    @SuppressWarnings("unchecked")
    private GenerationSettlementService.SettlementInput buildSettlementInput(GenerationTask task) {
        try {
            String json = task.getOutputAssets();
            if (json == null || json.isBlank()) return null;
            Map<String, Object> output = objectMapper.readValue(json, Map.class);
            String storageKey = asString(output.get("storage_key"));
            String provider = asString(output.get("storage_provider"));
            String bucket = asString(output.get("storage_bucket"));
            if (storageKey == null || provider == null || bucket == null) {
                return null;
            }
            Long fileSize = output.get("size") instanceof Number n ? n.longValue()
                    : output.get("file_size") instanceof Number n2 ? n2.longValue() : null;
            return new GenerationSettlementService.SettlementInput(
                    provider,
                    bucket,
                    storageKey,
                    asString(output.get("content_type")) != null
                            ? asString(output.get("content_type"))
                            : asString(output.get("mime_type")),
                    fileSize,
                    null,
                    null,
                    null,
                    asString(output.get("preview_url")) != null
                            ? asString(output.get("preview_url"))
                            : asString(output.get("url")),
                    asString(output.get("checksum")));
        } catch (Exception e) {
            return null;
        }
    }

    private static String asString(Object value) {
        if (value instanceof String s && !s.isBlank()) return s;
        return null;
    }

    private Long resolveOwnerId(Long projectId) {
        if (projectId == null) return 1L;
        try {
            CanvasProject project = projectMapper.selectById(projectId);
            return project != null && project.getUserId() != null ? project.getUserId() : 1L;
        } catch (Exception e) {
            log.warn("无法解析项目所有者: projectId={}", projectId);
            return 1L;
        }
    }

    private String mapAssetType(String taskType) {
        return switch (taskType) {
            case "image" -> "image";
            case "video", "compose", "export" -> "video";
            case "audio" -> "audio";
            default -> "other";
        };
    }
}
