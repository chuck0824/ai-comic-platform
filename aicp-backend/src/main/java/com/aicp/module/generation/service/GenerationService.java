package com.aicp.module.generation.service;

import com.aicp.module.generation.entity.*;
import com.aicp.module.generation.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GenerationService {

    private final GenerationTaskMapper taskMapper;
    private final GenerationVariantMapper variantMapper;
    private final CreditTransactionMapper creditMapper;
    private final PlatformAssetMapper platformAssetMapper;

    // ===== Task Management =====
    public GenerationTask createTask(Long projectId, Long nodeId, Long shotId,
                                     String type, String subType, String modelId,
                                     Map<String, Object> params) {
        GenerationTask task = new GenerationTask();
        task.setUuid(type + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        task.setProjectId(projectId);
        task.setNodeId(nodeId);
        task.setShotId(shotId);
        task.setType(type);
        task.setSubType(subType);
        task.setModelId(modelId);
        task.setParameters(toJson(params));
        task.setStatus("pending");
        task.setProgress(0);
        task.setCreditCost(estimateCredits(type, modelId, params));
        taskMapper.insert(task);
        return task;
    }

    public GenerationTask getTask(String uuid) {
        return taskMapper.selectOne(
                new LambdaQueryWrapper<GenerationTask>().eq(GenerationTask::getUuid, uuid));
    }

    public List<GenerationTask> getTasksByProject(Long projectId) {
        return taskMapper.selectList(
                new LambdaQueryWrapper<GenerationTask>()
                        .eq(GenerationTask::getProjectId, projectId)
                        .orderByDesc(GenerationTask::getCreatedAt));
    }

    public void cancelTask(String uuid) {
        GenerationTask task = getTask(uuid);
        if (task != null && "pending".equals(task.getStatus())) {
            task.setStatus("canceled");
            taskMapper.updateById(task);
        }
    }

    public GenerationTask retryTask(String uuid) {
        GenerationTask original = getTask(uuid);
        if (original == null) return null;
        GenerationTask retry = new GenerationTask();
        retry.setUuid(original.getType() + "_retry_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        retry.setProjectId(original.getProjectId());
        retry.setNodeId(original.getNodeId());
        retry.setShotId(original.getShotId());
        retry.setType(original.getType());
        retry.setSubType(original.getSubType());
        retry.setModelId(original.getModelId());
        retry.setParameters(original.getParameters());
        retry.setStatus("pending");
        retry.setProgress(0);
        retry.setCreditCost(original.getCreditCost());
        taskMapper.insert(retry);
        return retry;
    }

    // ===== Multi-Copy Variants =====
    public List<GenerationVariant> createVariants(Long parentTaskId, int count, Map<String, Object> baseParams) {
        List<GenerationVariant> variants = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            GenerationVariant variant = new GenerationVariant();
            variant.setParentTaskId(parentTaskId);
            variant.setVariantIndex(i);
            Map<String, Object> variedParams = new LinkedHashMap<>(baseParams);
            variedParams.put("seed", System.currentTimeMillis() + i);
            variant.setParameters(toJson(variedParams));
            variant.setSelected(0);
            variantMapper.insert(variant);
            variants.add(variant);
        }
        return variants;
    }

    public List<GenerationVariant> getVariants(Long parentTaskId) {
        return variantMapper.selectList(
                new LambdaQueryWrapper<GenerationVariant>()
                        .eq(GenerationVariant::getParentTaskId, parentTaskId)
                        .orderByAsc(GenerationVariant::getVariantIndex));
    }

    public void selectVariant(Long variantId) {
        GenerationVariant variant = variantMapper.selectById(variantId);
        if (variant != null) {
            variant.setSelected(1);
            variantMapper.updateById(variant);
        }
    }

    // ===== Credit / Cost =====
    public int estimateCredits(String type, String modelId, Map<String, Object> params) {
        return switch (type) {
            case "image" -> 10;
            case "video" -> 50;
            case "audio" -> 5;
            case "compose" -> 20;
            case "export" -> 30;
            case "quality" -> 2;
            case "agent", "skill" -> 25;
            default -> 10;
        };
    }

    public Map<String, Object> estimateCost(String type, String modelId, Map<String, Object> params) {
        int credits = estimateCredits(type, modelId, params);
        return Map.of("credits", credits, "type", type, "model_id", modelId);
    }

    // ===== Platform Assets =====
    public List<PlatformAsset> getAssetHistory(Long userId, String type, Long projectId, String keyword) {
        LambdaQueryWrapper<PlatformAsset> query = new LambdaQueryWrapper<>();
        query.eq(PlatformAsset::getOwnerUserId, userId);
        if (type != null) query.eq(PlatformAsset::getType, type);
        if (projectId != null) query.eq(PlatformAsset::getProjectId, projectId);
        if (keyword != null) query.like(PlatformAsset::getName, keyword);
        query.orderByDesc(PlatformAsset::getCreatedAt);
        return platformAssetMapper.selectList(query);
    }

    // ===== Utility =====
    private String toJson(Object value) {
        if (value == null) return null;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value); }
        catch (Exception e) { return String.valueOf(value); }
    }
}
