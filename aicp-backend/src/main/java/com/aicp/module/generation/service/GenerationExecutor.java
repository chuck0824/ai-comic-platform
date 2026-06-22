package com.aicp.module.generation.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.module.generation.entity.GenerationTask;
import com.aicp.module.generation.entity.PlatformAsset;
import com.aicp.module.generation.mapper.GenerationTaskMapper;
import com.aicp.module.generation.mapper.PlatformAssetMapper;
import com.aicp.module.canvas.entity.CanvasNode;
import com.aicp.module.canvas.entity.CanvasProject;
import com.aicp.module.canvas.entity.StoryboardShot;
import com.aicp.module.canvas.mapper.CanvasNodeMapper;
import com.aicp.module.canvas.mapper.CanvasProjectMapper;
import com.aicp.module.canvas.mapper.StoryboardShotMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async("genTaskExecutor")
    public void execute(GenerationTask task) {
        try {
            log.info("开始执行生成任务: type={}, uuid={}", task.getType(), task.getUuid());
            task.setStartedAt(LocalDateTime.now());
            task.setStatus("running");
            taskMapper.updateById(task);

            // 调用 AI Router
            aiRouter.executeTask(task.getId());

            // 重新加载，获取AI Router写入的结果
            GenerationTask updated = taskMapper.selectById(task.getId());
            if (updated == null || !"succeeded".equals(updated.getStatus())) {
                throw new RuntimeException(updated != null ? updated.getErrorMessage() : "任务执行失败");
            }

            // 回写节点状态
            writebackNode(updated);

            // 回写分镜状态
            writebackShot(updated);

            // 资产入库
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
            PlatformAsset asset = new PlatformAsset();
            asset.setUuid("asset_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
            asset.setProjectId(task.getProjectId());
            asset.setSourceNodeId(task.getNodeId());
            asset.setSourceTaskId(task.getId());
            asset.setType(mapAssetType(task.getType()));
            asset.setName("生成结果 - " + task.getType());
            asset.setPrompt(task.getParameters());
            asset.setModelId(task.getModelId());
            // 异步上下文：通过 projectId 反查项目所有者
            Long ownerId = resolveOwnerId(task.getProjectId());
            asset.setOwnerUserId(ownerId);
            platformAssetMapper.insert(asset);
        } catch (Exception e) {
            log.warn("资产入库失败: taskId={}", task.getId());
        }
    }

    /** 异步上下文中无法使用 SecurityContext，通过项目反查所有者 */
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
