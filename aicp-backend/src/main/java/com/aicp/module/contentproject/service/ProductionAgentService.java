package com.aicp.module.contentproject.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.module.canvas.entity.CanvasNode;
import com.aicp.module.canvas.mapper.CanvasNodeMapper;
import com.aicp.module.canvas.service.CanvasService;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.mapper.*;
import com.aicp.module.contentproject.entity.StoryboardShot;
import com.aicp.module.contentproject.mapper.ContentStoryboardShotMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * M5: ProductionAgent — batch production via canvas pipeline.
 * Creates GenerationTasks for image/video/audio from storyboard shots.
 * QualityAgent — binds quality issues to nodes and asset versions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionAgentService {

    private final CanvasService canvasService;
    private final CanvasNodeMapper canvasNodeMapper;
    private final AiRouter aiRouter;
    private final ObjectMapper objectMapper;
    private final com.aicp.module.canvas.mapper.StoryboardShotMapper canvasShotMapper;
    private final ContentStoryboardShotMapper cpShotMapper;

    /**
     * Submit batch image generation for all shots in a canvas project.
     * Returns task IDs for tracking.
     */
    @Transactional
    public Map<String, Object> batchGenerateImages(String canvasProjectId) {
        // Find canvas project by uuid
        com.aicp.module.canvas.entity.CanvasProject cp = canvasService.getProject(canvasProjectId);
        if (cp == null) return Map.of("error", "canvas_project_not_found");

        List<CanvasNode> nodes = canvasNodeMapper.selectList(
                new LambdaQueryWrapper<CanvasNode>()
                        .eq(CanvasNode::getProjectId, cp.getId())
                        .eq(CanvasNode::getType, "shot"));

        List<Map<String, Object>> tasks = new ArrayList<>();
        for (CanvasNode node : nodes) {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("prompt", extractPrompt(node));
                payload.put("width", 1080);
                payload.put("height", 1920);

                var task = canvasService.enqueueTask(canvasProjectId, "image", payload);
                tasks.add(Map.of("node_id", node.getUuid(), "task_id", task.getId(), "status", task.getStatus()));
            } catch (Exception e) {
                log.warn("Failed to enqueue image task for node {}", node.getUuid(), e);
                tasks.add(Map.of("node_id", node.getUuid(), "error", e.getMessage()));
            }
        }
        return Map.of("total", nodes.size(), "tasks", tasks);
    }

    /**
     * Quality check: review a batch of generated assets.
     */
    public Map<String, Object> qualityCheck(String canvasProjectId) {
        com.aicp.module.canvas.entity.CanvasProject cp = canvasService.getProject(canvasProjectId);
        if (cp == null) return Map.of("error", "canvas_project_not_found");

        List<CanvasNode> nodes = canvasNodeMapper.selectList(
                new LambdaQueryWrapper<CanvasNode>()
                        .eq(CanvasNode::getProjectId, cp.getId())
                        .eq(CanvasNode::getType, "shot"));

        String prompt = "请审核以下画布节点的输出质量。输出JSON：{\"reviews\":[{\"node_id\":\"\",\"score\":0-100,\"issues\":[],\"recommendation\":\"approve|retry|reject\"}]}";
        StringBuilder context = new StringBuilder("节点列表：\n");
        for (CanvasNode n : nodes) {
            context.append("节点 ").append(n.getUuid()).append(": ").append(n.getName()).append("\n");
            String output = n.getOutputData();
            if (output != null && output.length() > 100) {
                context.append("输出: ").append(output, 0, 100).append("...\n");
            }
        }

        Map<String, Object> result = aiRouter.chatCompletion(Map.of(
                "system_prompt", prompt, "prompt", context.toString(),
                "temperature", 0.3, "max_tokens", 2048));
        String text = extractText(result);
        try {
            return objectMapper.readValue(extractJson(text),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("reviews", List.of(), "raw", text);
        }
    }

    /**
     * Adopt: mark specific nodes as adopted (user confirmed).
     */
    @Transactional
    public int adoptNodes(String canvasProjectId, List<String> nodeUuids) {
        com.aicp.module.canvas.entity.CanvasProject cp = canvasService.getProject(canvasProjectId);
        if (cp == null) return 0;

        int count = 0;
        for (String uuid : nodeUuids) {
            CanvasNode node = canvasNodeMapper.selectOne(
                    new LambdaQueryWrapper<CanvasNode>()
                            .eq(CanvasNode::getProjectId, cp.getId())
                            .eq(CanvasNode::getUuid, uuid));
            if (node != null) {
                node.setStatus("adopted");
                canvasNodeMapper.updateById(node);
                count++;
            }
        }
        return count;
    }

    /**
     * Sync: compare storyboard master with canvas, return diff.
     */
    public Map<String, Object> syncDiff(Long masterId, String canvasProjectId) {
        com.aicp.module.canvas.entity.CanvasProject cp = canvasService.getProject(canvasProjectId);
        if (cp == null) return Map.of("error", "not_found");

        List<com.aicp.module.canvas.entity.StoryboardShot> canvasShots = canvasShotMapper.selectList(
                new LambdaQueryWrapper<com.aicp.module.canvas.entity.StoryboardShot>()
                        .eq(com.aicp.module.canvas.entity.StoryboardShot::getProjectId, cp.getId()));

        List<StoryboardShot> cpShots = cpShotMapper.selectList(
                new LambdaQueryWrapper<StoryboardShot>()
                        .eq(StoryboardShot::getMasterId, masterId));

        List<Map<String, Object>> diffs = new ArrayList<>();
        // Compare shot counts
        if (canvasShots.size() != cpShots.size()) {
            diffs.add(Map.of("type", "count_mismatch",
                    "canvas", canvasShots.size(), "storyboard", cpShots.size()));
        }
        // Compare per-shot descriptions
        int max = Math.min(canvasShots.size(), cpShots.size());
        for (int i = 0; i < max; i++) {
            String canvasDesc = canvasShots.get(i).getVisualDescription();
            String sbDesc = cpShots.get(i).getDescription();
            if (canvasDesc != null && sbDesc != null && !canvasDesc.equals(sbDesc)) {
                diffs.add(Map.of("type", "description_changed", "shot_no", i + 1,
                        "canvas", canvasDesc, "storyboard", sbDesc));
            }
        }

        return Map.of("canvas_project_id", canvasProjectId, "master_id", masterId,
                "canvas_shots", canvasShots.size(), "storyboard_shots", cpShots.size(),
                "diffs", diffs, "status", diffs.isEmpty() ? "in_sync" : "needs_review");
    }

    /** Export manifest: list all adopted assets */
    public Map<String, Object> exportManifest(String canvasProjectId) {
        com.aicp.module.canvas.entity.CanvasProject cp = canvasService.getProject(canvasProjectId);
        if (cp == null) return Map.of("error", "not_found");

        List<CanvasNode> adopted = canvasNodeMapper.selectList(
                new LambdaQueryWrapper<CanvasNode>()
                        .eq(CanvasNode::getProjectId, cp.getId())
                        .eq(CanvasNode::getStatus, "adopted"));

        List<Map<String, Object>> assets = new ArrayList<>();
        for (CanvasNode n : adopted) {
            assets.add(Map.of(
                    "node_id", n.getUuid(),
                    "type", n.getType(),
                    "name", n.getName(),
                    "output", n.getOutputData() != null ? n.getOutputData() : ""));
        }

        return Map.of(
                "canvas_project_id", canvasProjectId,
                "total_adopted", adopted.size(),
                "assets", assets,
                "exported_at", java.time.LocalDateTime.now().toString());
    }

    private String extractPrompt(CanvasNode node) {
        String input = node.getInputData();
        if (input != null && input.length() > 10) {
            try {
                Map<?, ?> data = objectMapper.readValue(input, Map.class);
                Object desc = data.get("description");
                if (desc != null) return String.valueOf(desc);
            } catch (Exception ignored) {}
            return input;
        }
        return "cinematic shot, high quality";
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> r) {
        Object choices = r.get("choices");
        if (choices instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map m) {
            Object msg = m.get("message");
            if (msg instanceof Map mm) { Object c = mm.get("content"); if (c != null) return String.valueOf(c); }
        }
        return r.toString();
    }
    private String extractJson(String text) {
        if (text.contains("```json")) { int s=text.indexOf("```json")+7,e=text.indexOf("```",s); if(e>s) return text.substring(s,e).trim(); }
        return text.trim();
    }
}
