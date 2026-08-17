package com.aicp.module.canvas.service;

import com.aicp.common.util.SecurityUtil;
import com.aicp.module.canvas.entity.*;
import com.aicp.module.canvas.mapper.*;
import com.aicp.module.generation.entity.GenerationTask;
import com.aicp.module.generation.mapper.GenerationTaskMapper;
import com.aicp.module.generation.service.GenerationExecutor;
import com.aicp.module.generation.service.GenerationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CanvasService {

    private final CanvasProjectMapper projectMapper;
    private final CanvasNodeMapper nodeMapper;
    private final CanvasEdgeMapper edgeMapper;
    private final CanvasGroupMapper groupMapper;
    private final StoryboardShotMapper shotMapper;
    private final CanvasTimelineMapper timelineMapper;
    private final CanvasWorkflowMapper workflowMapper;
    private final GenerationTaskMapper generationTaskMapper;
    private final GenerationService generationService;
    private final GenerationExecutor generationExecutor;
    private final ObjectMapper objectMapper;

    // ===== Project CRUD =====
    public CanvasProject createProject(Map<String, Object> body) {
        CanvasProject project = new CanvasProject();
        project.setUuid("canvas_" + UUID.randomUUID().toString().replace("-", "").substring(0, 7));
        project.setName((String) body.getOrDefault("name", "未命名画布项目"));
        project.setUserId(SecurityUtil.requireCurrentUserId());
        project.setScriptId(toLong(body.get("script_id"), null));
        project.setEpisodeIndex((Integer) body.getOrDefault("episode_index", 1));
        project.setStyleConfig(toJson(body.get("style_config")));
        project.setStatus("editing");
        project.setCanvasVersion(1);
        projectMapper.insert(project);
        return project;
    }

    public CanvasProject getProject(String id) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        // 数据隔离：在 SQL 层面过滤，避免通过响应时间差异推断项目存在性
        CanvasProject byUuid = projectMapper.selectOne(
                new LambdaQueryWrapper<CanvasProject>()
                        .eq(CanvasProject::getUuid, id)
                        .eq(currentUserId != null, CanvasProject::getUserId, currentUserId));
        if (byUuid != null) return byUuid;
        // 回退：尝试按自增 ID + 用户过滤查询
        try {
            long numericId = Long.parseLong(id);
            return projectMapper.selectOne(
                    new LambdaQueryWrapper<CanvasProject>()
                            .eq(CanvasProject::getId, numericId)
                            .eq(currentUserId != null, CanvasProject::getUserId, currentUserId));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 获取当前用户的所有项目 */
    public List<CanvasProject> getUserProjects() {
        Long userId = SecurityUtil.requireCurrentUserId();
        return projectMapper.selectList(
                new LambdaQueryWrapper<CanvasProject>()
                        .eq(CanvasProject::getUserId, userId)
                        .orderByDesc(CanvasProject::getCreatedAt));
    }

    public CanvasProject updateProject(String id, Map<String, Object> body) {
        CanvasProject project = getProject(id);
        if (project == null) return null;
        if (body.containsKey("name")) project.setName((String) body.get("name"));
        if (body.containsKey("style_config")) project.setStyleConfig(toJson(body.get("style_config")));
        if (body.containsKey("status")) project.setStatus((String) body.get("status"));
        if (body.containsKey("episode_index")) project.setEpisodeIndex((Integer) body.get("episode_index"));
        project.setCanvasVersion(project.getCanvasVersion() + 1);
        projectMapper.updateById(project);
        return project;
    }

    // ===== Node CRUD =====
    public CanvasNode createNode(String projectId, Map<String, Object> body) {
        CanvasProject project = getProject(projectId);
        if (project == null) return null;
        String type = String.valueOf(body.getOrDefault("type", "text"));
        CanvasNode node = new CanvasNode();
        node.setUuid("node_" + shortId());
        node.setProjectId(project.getId());
        node.setType(type);
        node.setName((String) body.getOrDefault("name", nodeLabel(type)));
        node.setX(toInt(body.get("x"), 80));
        node.setY(toInt(body.get("y"), 80));
        node.setWidth(toInt(body.get("width"), nodeWidth(type)));
        node.setHeight(toInt(body.get("height"), nodeHeight(type)));
        node.setInputData(toJson(body.get("data")));
        node.setStatus("ready");
        nodeMapper.insert(node);
        return node;
    }

    public List<CanvasNode> getProjectNodes(String projectId) {
        CanvasProject project = getProject(projectId);
        if (project == null) return List.of();
        return nodeMapper.selectList(
                new LambdaQueryWrapper<CanvasNode>().eq(CanvasNode::getProjectId, project.getId()));
    }

    public CanvasNode updateNode(String projectId, String nodeId, Map<String, Object> body) {
        CanvasNode node = findNodeByUuid(projectId, nodeId);
        if (node == null) return null;
        if (body.containsKey("name")) node.setName((String) body.get("name"));
        if (body.containsKey("x")) node.setX(toInt(body.get("x"), node.getX()));
        if (body.containsKey("y")) node.setY(toInt(body.get("y"), node.getY()));
        if (body.containsKey("width")) node.setWidth(toInt(body.get("width"), node.getWidth()));
        if (body.containsKey("height")) node.setHeight(toInt(body.get("height"), node.getHeight()));
        if (body.containsKey("data")) node.setInputData(toJson(body.get("data")));
        if (body.containsKey("status")) node.setStatus((String) body.get("status"));
        nodeMapper.updateById(node);
        return node;
    }

    @Transactional
    public void batchUpdateNodePositions(String projectId, List<Map<String, Object>> positions) {
        CanvasProject project = getProject(projectId);
        if (project == null || positions == null || positions.isEmpty()) return;
        for (Map<String, Object> pos : positions) {
            CanvasNode node = findNodeByUuid(projectId, String.valueOf(pos.get("node_id")));
            if (node == null) continue;
            if (pos.containsKey("x")) node.setX(toInt(pos.get("x"), node.getX()));
            if (pos.containsKey("y")) node.setY(toInt(pos.get("y"), node.getY()));
            nodeMapper.updateById(node);
        }
    }

    @Transactional
    public void deleteNode(String projectId, String nodeId) {
        CanvasNode node = findNodeByUuid(projectId, nodeId);
        if (node == null) return;
        // Remove connected edges
        edgeMapper.delete(new LambdaQueryWrapper<CanvasEdge>()
                .eq(CanvasEdge::getProjectId, node.getProjectId())
                .and(w -> w.eq(CanvasEdge::getSourceNodeId, node.getId())
                        .or().eq(CanvasEdge::getTargetNodeId, node.getId())));
        nodeMapper.deleteById(node.getId());
    }

    @Transactional
    public CanvasNode duplicateNode(String projectId, String nodeId) {
        CanvasNode source = findNodeByUuid(projectId, nodeId);
        if (source == null) return null;
        CanvasNode copy = new CanvasNode();
        copy.setUuid("node_" + shortId());
        copy.setProjectId(source.getProjectId());
        copy.setType(source.getType());
        copy.setName(source.getName() + " 副本");
        copy.setX(source.getX() + 40);
        copy.setY(source.getY() + 40);
        copy.setWidth(source.getWidth());
        copy.setHeight(source.getHeight());
        copy.setInputData(source.getInputData());
        copy.setOutputData(source.getOutputData());
        copy.setStatus("ready");
        nodeMapper.insert(copy);
        duplicateConnectedEdges(source, copy);
        return copy;
    }

    // ===== Connections =====
    public CanvasEdge connectNodes(String projectId, Map<String, Object> body) {
        CanvasProject project = getProject(projectId);
        if (project == null) return null;
        String sourceId = String.valueOf(body.get("source_node_id"));
        String targetId = String.valueOf(body.get("target_node_id"));
        CanvasNode sourceNode = findNodeByUuid(projectId, sourceId);
        CanvasNode targetNode = findNodeByUuid(projectId, targetId);
        if (sourceNode == null || targetNode == null) return null;

        CanvasEdge edge = new CanvasEdge();
        edge.setUuid("conn_" + shortId());
        edge.setProjectId(project.getId());
        edge.setSourceNodeId(sourceNode.getId());
        edge.setSourcePort((String) body.getOrDefault("source_port", "out"));
        edge.setTargetNodeId(targetNode.getId());
        edge.setTargetPort((String) body.getOrDefault("target_port", "in"));
        edge.setEdgeType((String) body.getOrDefault("edge_type", "data"));
        edgeMapper.insert(edge);
        return edge;
    }

    public void deleteConnection(String projectId, String connId) {
        CanvasProject project = getProject(projectId);
        if (project == null) return;
        edgeMapper.delete(new LambdaQueryWrapper<CanvasEdge>()
                .eq(CanvasEdge::getProjectId, project.getId())
                .eq(CanvasEdge::getUuid, connId));
    }

    public List<CanvasEdge> getProjectConnections(String projectId) {
        CanvasProject project = getProject(projectId);
        if (project == null) return List.of();
        return edgeMapper.selectList(
                new LambdaQueryWrapper<CanvasEdge>().eq(CanvasEdge::getProjectId, project.getId()));
    }

    // ===== Groups =====
    @Transactional
    public CanvasGroup groupNodes(String projectId, Map<String, Object> body) {
        CanvasProject project = getProject(projectId);
        if (project == null) return null;
        CanvasGroup group = new CanvasGroup();
        group.setUuid("group_" + shortId());
        group.setProjectId(project.getId());
        group.setName((String) body.getOrDefault("name", "未命名分组"));
        group.setNodeIds(toJson(body.get("node_ids")));
        groupMapper.insert(group);

        @SuppressWarnings("unchecked")
        List<String> nodeIds = (List<String>) body.getOrDefault("node_ids", List.of());
        for (String nid : nodeIds) {
            CanvasNode node = findNodeByUuid(projectId, nid);
            if (node != null) {
                node.setGroupId(group.getId());
                nodeMapper.updateById(node);
            }
        }
        return group;
    }

    // ===== Storyboard Shots =====
    public List<StoryboardShot> getProjectShots(String projectId) {
        CanvasProject project = getProject(projectId);
        if (project == null) return List.of();
        return shotMapper.selectList(
                new LambdaQueryWrapper<StoryboardShot>()
                        .eq(StoryboardShot::getProjectId, project.getId())
                        .orderByAsc(StoryboardShot::getShotNo));
    }

    public StoryboardShot createShot(String projectId, Map<String, Object> body) {
        CanvasProject project = getProject(projectId);
        if (project == null) return null;
        StoryboardShot shot = new StoryboardShot();
        shot.setUuid("SH" + System.currentTimeMillis());
        shot.setProjectId(project.getId());
        shot.setShotNo((Integer) body.getOrDefault("order", getProjectShots(projectId).size() + 1));
        shot.setSceneNo((Integer) body.getOrDefault("scene_no", 1));
        shot.setDuration((Integer) body.getOrDefault("duration", 3000));
        shot.setShotSize((String) body.get("shot_size"));
        shot.setCameraMotion((String) body.get("camera_motion"));
        shot.setImageStatus("pending");
        shot.setVideoStatus("pending");
        shotMapper.insert(shot);
        return shot;
    }

    public void updateShot(String projectId, String shotId, Map<String, Object> body) {
        StoryboardShot shot = findShotByUuid(projectId, shotId);
        if (shot == null) return;
        body.forEach((key, value) -> {
            switch (key) {
                case "shot_no" -> shot.setShotNo(toInt(value, shot.getShotNo()));
                case "scene_no" -> shot.setSceneNo(toInt(value, shot.getSceneNo()));
                case "duration" -> shot.setDuration(toInt(value, shot.getDuration()));
                case "shot_size" -> shot.setShotSize((String) value);
                case "camera_motion" -> shot.setCameraMotion((String) value);
                case "visual_description" -> shot.setVisualDescription((String) value);
                case "characters" -> shot.setCharacters(toJson(value));
                case "dialogue" -> shot.setDialogue(toJson(value));
                case "image_prompt" -> shot.setImagePrompt((String) value);
                case "video_prompt" -> shot.setVideoPrompt((String) value);
                case "image_status" -> shot.setImageStatus((String) value);
                case "video_status" -> shot.setVideoStatus((String) value);
                case "keyframe_start" -> shot.setKeyframeStart(toJson(value));
                case "keyframe_end" -> shot.setKeyframeEnd(toJson(value));
            }
        });
        shotMapper.updateById(shot);
    }

    public void deleteShot(String projectId, String shotId) {
        StoryboardShot shot = findShotByUuid(projectId, shotId);
        if (shot != null) shotMapper.deleteById(shot.getId());
    }

    @Transactional
    public void reorderShots(String projectId, List<String> shotUuids) {
        int order = 1;
        for (String uuid : shotUuids) {
            StoryboardShot shot = findShotByUuid(projectId, uuid);
            if (shot != null) {
                shot.setShotNo(order++);
                shotMapper.updateById(shot);
            }
        }
    }

    // ===== Timeline =====
    public Map<String, Object> getFullTimeline(String projectId) {
        CanvasProject project = getProject(projectId);
        if (project == null) return defaultTimeline();
        CanvasTimeline timeline = timelineMapper.selectOne(
                new LambdaQueryWrapper<CanvasTimeline>().eq(CanvasTimeline::getProjectId, project.getId()));
        if (timeline == null) return defaultTimeline();
        return parseJson(timeline.getData());
    }

    public void updateFullTimeline(String projectId, Map<String, Object> body) {
        CanvasProject project = getProject(projectId);
        if (project == null) return;
        CanvasTimeline existing = timelineMapper.selectOne(
                new LambdaQueryWrapper<CanvasTimeline>().eq(CanvasTimeline::getProjectId, project.getId()));
        if (existing == null) {
            CanvasTimeline timeline = new CanvasTimeline();
            timeline.setProjectId(project.getId());
            timeline.setData(toJson(body));
            timelineMapper.insert(timeline);
        } else {
            existing.setData(toJson(body));
            timelineMapper.updateById(existing);
        }
    }

    // ===== Workflow =====
    public CanvasWorkflow createWorkflow(String projectId, Map<String, Object> body) {
        CanvasProject project = getProject(projectId);
        if (project == null) return null;
        CanvasWorkflow wf = new CanvasWorkflow();
        wf.setUuid("wf_" + shortId());
        wf.setProjectId(project.getId());
        wf.setName((String) body.getOrDefault("name", "未命名工作流"));
        wf.setDescription((String) body.getOrDefault("description", ""));
        wf.setNodeIds(toJson(body.get("node_ids")));
        wf.setStatus("draft");
        workflowMapper.insert(wf);
        return wf;
    }

    public List<CanvasWorkflow> getWorkflows(String projectId) {
        CanvasProject project = getProject(projectId);
        if (project == null) return List.of();
        return workflowMapper.selectList(
                new LambdaQueryWrapper<CanvasWorkflow>().eq(CanvasWorkflow::getProjectId, project.getId()));
    }

    // ===== Generation Task =====
    public GenerationTask enqueueTask(String projectId, String type, Map<String, Object> payload) {
        CanvasProject project = getProject(projectId);
        if (project == null) return null;
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        String executionType = normalizeTaskType(type);
        String modelId = String.valueOf(safePayload.getOrDefault("model_id", ""));
        if (modelId.isBlank() || "null".equals(modelId)) modelId = null;

        GenerationTask task = new GenerationTask();
        task.setUuid(type + "_" + shortId());
        task.setProjectId(project.getId());
        task.setNodeId(resolveNodeDbId(projectId, safePayload.get("node_id")));
        task.setShotId(resolveShotDbId(projectId, safePayload.get("shot_id")));
        task.setType(executionType);
        task.setSubType(type.equals(executionType) ? null : type);
        task.setProvider("new-api");
        task.setModelId(modelId);
        task.setParameters(toJson(flattenTaskParams(safePayload)));
        task.setStatus("pending");
        task.setProgress(0);
        task.setCreditCost(generationService.estimateCredits(executionType, modelId, flattenTaskParams(safePayload)));
        generationTaskMapper.insert(task);
        generationExecutor.execute(task);
        return task;
    }

    public GenerationTask getTaskStatus(String taskUuid) {
        return generationTaskMapper.selectOne(
                new LambdaQueryWrapper<GenerationTask>().eq(GenerationTask::getUuid, taskUuid));
    }

    // ===== Import Script =====
    @Transactional
    public Map<String, Object> importScript(String projectId, Map<String, Object> body) {
        CanvasProject project = getProject(projectId);
        if (project == null) return null;
        Long scriptId = toLong(body.get("script_id"), null);
        if (scriptId != null) {
            project.setScriptId(scriptId);
            projectMapper.updateById(project);
        }

        // Create script node
        CanvasNode scriptNode = new CanvasNode();
        scriptNode.setUuid("node_" + shortId());
        scriptNode.setProjectId(project.getId());
        scriptNode.setType("script");
        scriptNode.setName("脚本 · 已导入剧本");
        scriptNode.setX(80);
        scriptNode.setY(60);
        scriptNode.setWidth(340);
        scriptNode.setHeight(280);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("label", "脚本 · 已导入剧本");
        data.put("script_id", body.getOrDefault("script_id", "manual"));
        data.put("coupling_mode", body.getOrDefault("coupling_mode", "semi"));
        data.put("source_version", body.getOrDefault("source_version", "v0.1"));
        data.put("shots", normalizeImportedShots(body.get("shots")));
        scriptNode.setInputData(toJson(data));
        scriptNode.setStatus("completed");
        nodeMapper.insert(scriptNode);

        // Create storyboard shots from the script node
        int shotNo = 1;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shots = (List<Map<String, Object>>) data.get("shots");
        for (Map<String, Object> shotData : shots) {
            StoryboardShot shot = new StoryboardShot();
            shot.setUuid("SH" + System.currentTimeMillis() + "_" + shotNo);
            shot.setStoryboardId(scriptNode.getId());
            shot.setProjectId(project.getId());
            shot.setShotNo(shotNo++);
            shot.setSceneNo(1);
            shot.setDuration(toDurationMs(shotData.get("duration"), 3000));
            shot.setShotSize(String.valueOf(shotData.getOrDefault("shotSize", shotData.getOrDefault("shot_type", shotData.getOrDefault("shotType", "MS")))));
            shot.setCameraMotion(String.valueOf(shotData.getOrDefault("cameraMove", shotData.getOrDefault("camera_motion", ""))));
            shot.setVisualDescription(String.valueOf(shotData.getOrDefault("visual", shotData.getOrDefault("visualDescription", shotData.getOrDefault("content", "")))));
            shot.setDialogue(toJson(shotData.get("dialogue")));
            shot.setImagePrompt(String.valueOf(shotData.getOrDefault("imagePrompt", shotData.getOrDefault("image_prompt", ""))));
            shot.setVideoPrompt(String.valueOf(shotData.getOrDefault("videoPrompt", shotData.getOrDefault("video_prompt", ""))));
            shot.setImageStatus("pending");
            shot.setVideoStatus("pending");
            shotMapper.insert(shot);
        }

        return Map.of("message", "剧本导入成功", "node_id", scriptNode.getUuid(),
                "shot_count", shots.size());
    }

    // ===== Helper methods =====
    private CanvasNode findNodeByUuid(String projectId, String nodeUuid) {
        CanvasProject project = getProject(projectId);
        if (project == null) return null;
        return nodeMapper.selectOne(new LambdaQueryWrapper<CanvasNode>()
                .eq(CanvasNode::getProjectId, project.getId())
                .eq(CanvasNode::getUuid, nodeUuid));
    }

    private StoryboardShot findShotByUuid(String projectId, String shotUuid) {
        CanvasProject project = getProject(projectId);
        if (project == null) return null;
        return shotMapper.selectOne(new LambdaQueryWrapper<StoryboardShot>()
                .eq(StoryboardShot::getProjectId, project.getId())
                .eq(StoryboardShot::getUuid, shotUuid));
    }

    private void duplicateConnectedEdges(CanvasNode source, CanvasNode copy) {
        List<CanvasEdge> edges = edgeMapper.selectList(new LambdaQueryWrapper<CanvasEdge>()
                .eq(CanvasEdge::getProjectId, source.getProjectId())
                .and(w -> w.eq(CanvasEdge::getSourceNodeId, source.getId())
                        .or().eq(CanvasEdge::getTargetNodeId, source.getId())));
        for (CanvasEdge edge : edges) {
            CanvasEdge edgeCopy = new CanvasEdge();
            edgeCopy.setUuid("conn_" + shortId());
            edgeCopy.setProjectId(edge.getProjectId());
            edgeCopy.setSourceNodeId(Objects.equals(edge.getSourceNodeId(), source.getId())
                    ? copy.getId() : edge.getSourceNodeId());
            edgeCopy.setSourcePort(edge.getSourcePort());
            edgeCopy.setTargetNodeId(Objects.equals(edge.getTargetNodeId(), source.getId())
                    ? copy.getId() : edge.getTargetNodeId());
            edgeCopy.setTargetPort(edge.getTargetPort());
            edgeCopy.setEdgeType(edge.getEdgeType());
            edgeCopy.setMetadata(edge.getMetadata());
            edgeMapper.insert(edgeCopy);
        }
    }

    private Long resolveNodeDbId(String projectId, Object nodeRef) {
        if (nodeRef == null) return null;
        CanvasNode node = findNodeByUuid(projectId, String.valueOf(nodeRef));
        return node == null ? null : node.getId();
    }

    private Long resolveShotDbId(String projectId, Object shotRef) {
        if (shotRef == null) return null;
        StoryboardShot shot = findShotByUuid(projectId, String.valueOf(shotRef));
        return shot == null ? null : shot.getId();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenTaskParams(Map<String, Object> payload) {
        Map<String, Object> params = new LinkedHashMap<>(payload);
        Object nested = payload.get("params");
        if (nested instanceof Map<?, ?> nestedMap) {
            nestedMap.forEach((key, value) -> params.put(String.valueOf(key), value));
        }
        return params;
    }

    private String normalizeTaskType(String type) {
        if (type == null) return "text";
        // compose/export 直接透传，不做归类
        if ("compose".equals(type) || "export".equals(type)) return type;
        String lower = type.toLowerCase(Locale.ROOT);
        if (lower.contains("image") || lower.contains("inpaint") || lower.contains("outpaint")
                || type.contains("图片") || type.contains("图像") || type.contains("出图")) return "image";
        if (lower.contains("video") || lower.contains("clip") || lower.contains("splice")
                || type.contains("视频")) return "video";
        if (lower.contains("audio") || lower.contains("dub") || lower.contains("subtitle")
                || type.contains("音频") || type.contains("配音") || type.contains("语音")) return "audio";
        if (lower.contains("workflow")) return "agent";
        return "text";
    }

    // ===== Utility =====
    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { return fallback; }
    }

    private Long toLong(Object value, Long fallback) {
        if (value instanceof Number n) return n.longValue();
        if (value == null) return fallback;
        try { return Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException e) { return fallback; }
    }

    private int toDurationMs(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return fallback;
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        try {
            if (text.endsWith("s")) return Math.round(Float.parseFloat(text.substring(0, text.length() - 1)) * 1000);
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeImportedShots(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> shots = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) {
                    Map<String, Object> shot = new LinkedHashMap<>();
                    raw.forEach((key, val) -> shot.put(String.valueOf(key), val));
                    shots.add(shot);
                }
            }
            if (!shots.isEmpty()) return shots;
        }
        return defaultStoryboardRows();
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null) return new LinkedHashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(json, Map.class);
            return result;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String nodeLabel(String type) {
        return switch (type) {
            case "image" -> "图片节点";
            case "video" -> "视频节点";
            case "audio" -> "音频节点";
            case "script" -> "剧本节点";
            case "storyboard" -> "分镜节点";
            case "character" -> "角色节点";
            case "scene" -> "场景节点";
            case "prompt" -> "Prompt 节点";
            case "model" -> "模型节点";
            case "output" -> "输出节点";
            case "director" -> "导演台节点";
            case "reference" -> "参考视频节点";
            case "workflow" -> "工作流模板";
            default -> "文本节点";
        };
    }

    /** 节点默认宽度，与前端 nodeRegistry 保持一致 */
    private int nodeWidth(String type) {
        return switch (type) {
            case "script" -> 340;
            case "director" -> 280;
            case "text" -> 480;
            case "prompt" -> 300;
            case "character", "scene", "model" -> 280;
            case "output" -> 260;
            case "image", "video" -> 420;
            case "audio" -> 300;
            default -> 240;
        };
    }

    /** 节点默认高度，与前端 nodeRegistry 保持一致 */
    private int nodeHeight(String type) {
        return switch (type) {
            case "script" -> 280;
            case "director" -> 220;
            case "text" -> 360;
            case "prompt" -> 200;
            case "character", "scene" -> 220;
            case "model" -> 190;
            case "output" -> 170;
            case "image", "video" -> 300;
            case "audio" -> 240;
            default -> 180;
        };
    }

    private List<Map<String, Object>> defaultStoryboardRows() {
        return List.of(
                Map.of("id", "SH001", "shotType", "MS 跟拍", "content", "端咖啡进办公室", "dialogue", "—"),
                Map.of("id", "SH002", "shotType", "MCU 固定", "content", "林默抬头看", "dialogue", "之前在哪工作？"),
                Map.of("id", "SH003", "shotType", "CU 特写", "content", "手指紧握咖啡杯", "dialogue", "一家小公司"),
                Map.of("id", "SH004", "shotType", "MS 缓推", "content", "林默看手腕", "dialogue", "手腕上的伤…"));
    }

    private Map<String, Object> defaultTimeline() {
        Map<String, Object> timeline = new LinkedHashMap<>();
        timeline.put("video_track", List.of());
        timeline.put("audio_track", List.of());
        timeline.put("subtitle_track", List.of());
        timeline.put("bgm_track", List.of());
        timeline.put("sfx_track", List.of());
        timeline.put("effect_track", List.of());
        timeline.put("overlay_track", List.of());
        return timeline;
    }
}
