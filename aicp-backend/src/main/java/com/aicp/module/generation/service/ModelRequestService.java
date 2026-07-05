package com.aicp.module.generation.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.generation.adapter.ModelAdapter;
import com.aicp.module.generation.adapter.ModelAdapter.*;
import com.aicp.module.generation.adapter.ModelCapabilityProfile;
import com.aicp.module.generation.adapter.SeedanceAdapter;
import com.aicp.module.generation.capability.CapabilityCompiler;
import com.aicp.module.generation.capability.CapabilityRequest;
import com.aicp.module.generation.entity.GenerationCandidate;
import com.aicp.module.generation.entity.GenerationRequestSnapshot;
import com.aicp.module.generation.mapper.GenerationCandidateMapper;
import com.aicp.module.generation.mapper.GenerationRequestSnapshotMapper;
import com.aicp.module.canvas.entity.CanvasNode;
import com.aicp.module.canvas.mapper.CanvasNodeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 模型请求预览与提交服务。
 * 集成 CapabilityCompiler → ModelAdapter → AiRouter/new-api(3001) 调用链。
 * 确认后冻结不可变 GenerationRequestSnapshot，通过现有 AiRouter 创建 GenerationTask。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRequestService {

    private final CapabilityCompiler compiler;
    private final SeedanceAdapter seedanceAdapter;
    private final GenerationService generationService;
    private final GenerationRequestSnapshotMapper snapshotMapper;
    private final GenerationCandidateMapper candidateMapper;
    private final CanvasNodeMapper nodeMapper;
    private final ObjectMapper objectMapper;

    /**
     * 预览模型请求：展示推荐模型、费用、参考角色和警告。
     * 不创建任何持久化记录。
     */
    public AdapterPreview preview(Long nodeId, CapabilityCompiler.CompileInput input) {
        CanvasNode node = nodeMapper.selectById(nodeId);
        if (node == null) throw new BizException(ErrorCode.CANVAS_NODE_NOT_FOUND);

        CapabilityRequest request = compiler.compile(input);
        ModelCapabilityProfile profile = loadProfile(request);

        return seedanceAdapter.preview(request, profile);
    }

    /**
     * 确认后提交：冻结请求快照，通过 AiRouter 创建任务。
     * 校验预览指纹一致性和模型可用性。
     */
    @Transactional
    public SubmitResult submit(Long nodeId, CapabilityCompiler.CompileInput input,
                                String confirmedFingerprint, String idempotencyKey, Long actorId) {
        CanvasNode node = nodeMapper.selectById(nodeId);
        if (node == null) throw new BizException(ErrorCode.CANVAS_NODE_NOT_FOUND);

        CapabilityRequest request = compiler.compile(input);
        ModelCapabilityProfile profile = loadProfile(request);

        AdapterPreview preview = seedanceAdapter.preview(request, profile);
        if (!preview.previewFingerprint().equals(confirmedFingerprint)) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(),
                    "预览已过期，模型或参数已变更，请重新预览确认");
        }

        // 冻结不可变快照
        GenerationRequestSnapshot snapshot = new GenerationRequestSnapshot();
        snapshot.setUuid(UUID.randomUUID().toString());
        snapshot.setNodeId(nodeId);
        snapshot.setShotUnitId(node.getShotUnitId() != null ? node.getShotUnitId() : 0L);
        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("request", request);
            payload.put("preview", preview);
            snapshot.setPayloadJson(objectMapper.writeValueAsString(payload));
            snapshot.setPayloadHash(sha256(snapshot.getPayloadJson()));
        } catch (Exception e) {
            throw new RuntimeException("快照序列化失败", e);
        }
        snapshot.setResolvedModelId(preview.modelId());
        snapshot.setResolvedModelVersion(preview.modelVersion());
        snapshot.setAdapterVersion(preview.adapterVersion());
        snapshot.setEstimatedCredits(preview.estimatedCredits());
        snapshotMapper.insert(snapshot);

        // 通过现有 GenerationService 创建任务（接入 3001 new-api 网关）
        var params = new HashMap<String, Object>();
        params.put("snapshot_id", snapshot.getId());
        var task = generationService.createTask(
                node.getProjectId(), nodeId, node.getShotUnitId(),
                "video", preview.modelId(), snapshot.getUuid(), params);
        Long taskId = task.getId();

        log.info("模型请求已提交: node={}, snapshot={}, task={}, model={}, credits={}",
                nodeId, snapshot.getUuid(), taskId, preview.modelId(), preview.estimatedCredits());

        return new SubmitResult(snapshot.getUuid(), taskId, preview.modelId(), preview.estimatedCredits());
    }

    /**
     * 查询节点的候选列表。
     */
    public List<GenerationCandidate> listCandidates(Long nodeId) {
        // 通过 snapshot 关联查询候选
        var snapshots = snapshotMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GenerationRequestSnapshot>()
                        .eq(GenerationRequestSnapshot::getNodeId, nodeId));
        if (snapshots.isEmpty()) return Collections.emptyList();

        List<GenerationCandidate> all = new ArrayList<>();
        for (var snap : snapshots) {
            var candidates = candidateMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GenerationCandidate>()
                            .eq(GenerationCandidate::getRequestSnapshotId, snap.getId())
                            .ne(GenerationCandidate::getSafetyStatus, "REJECTED"));
            all.addAll(candidates);
        }
        return all;
    }

    /**
     * 更新节点的当前候选选择。
     */
    @Transactional
    public void selectCandidate(Long nodeId, Long candidateId) {
        // 取消该节点所有现有选择
        var allCandidates = listCandidates(nodeId);
        for (var c : allCandidates) {
            if (c.getIsSelected() && !c.getId().equals(candidateId)) {
                c.setIsSelected(false);
                candidateMapper.updateById(c);
            }
        }
        // 设置新选择
        GenerationCandidate target = candidateMapper.selectById(candidateId);
        if (target != null) {
            target.setIsSelected(true);
            candidateMapper.updateById(target);
        }
    }

    private ModelCapabilityProfile loadProfile(CapabilityRequest request) {
        // 当前 R3 基础实现：使用 Seedance profile
        // 后续从 AiModelRegistry/model_capability_profiles 表动态加载
        return new ModelCapabilityProfile(
                "seedance-2.0",
                SeedanceAdapter.adapterVersion(),
                false, // production_verified = false 直到 G0 Gate 通过
                new ModelCapabilityProfile.Limits(9, 3, 3, 15, List.of("16:9", "9:16", "1:1", "4:3")),
                List.of("mp4", "mov", "png", "jpg", "wav", "mp3"),
                new ModelCapabilityProfile.RateLimits(2, 5, 50)
        );
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public record SubmitResult(String snapshotUuid, Long taskId, String modelId, int estimatedCredits) {}
}
