package com.aicp.module.canvas.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.module.canvas.entity.*;
import com.aicp.module.canvas.service.CanvasService;
import com.aicp.module.generation.entity.GenerationTask;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/canvas")
@RequiredArgsConstructor
public class CanvasController {

    private final CanvasService canvasService;
    private final ObjectMapper objectMapper;

    // ===== Projects =====
    @PostMapping("/projects")
    public ApiResponse<CanvasProject> createProject(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(canvasService.createProject(body));
    }

    @GetMapping("/projects/{id}")
    public ApiResponse<?> getProject(@PathVariable String id) {
        CanvasProject p = canvasService.getProject(id);
        return p == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(p);
    }

    @PutMapping("/projects/{id}")
    public ApiResponse<?> updateProject(@PathVariable String id, @RequestBody Map<String, Object> body) {
        CanvasProject p = canvasService.updateProject(id, body);
        return p == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(p);
    }

    @PostMapping("/projects/{id}/import-script")
    public ApiResponse<?> importScript(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = canvasService.importScript(id, body);
        return result == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(result);
    }

    // ===== Nodes =====
    @GetMapping("/projects/{id}/nodes")
    public ApiResponse<Map<String, Object>> getNodes(@PathVariable String id) {
        CanvasProject p = canvasService.getProject(id);
        if (p == null) return ApiResponse.error(46001, "画布项目不存在");
        return ApiResponse.success(Map.of(
                "nodes", canvasService.getProjectNodes(id),
                "connections", canvasService.getProjectConnections(id)));
    }

    @PostMapping("/projects/{id}/nodes")
    public ApiResponse<?> createNode(@PathVariable String id, @RequestBody Map<String, Object> body) {
        CanvasNode node = canvasService.createNode(id, body);
        return node == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(node);
    }

    @PutMapping("/projects/{id}/nodes/{nodeId}")
    public ApiResponse<?> updateNode(@PathVariable String id, @PathVariable String nodeId,
                                     @RequestBody Map<String, Object> body) {
        CanvasNode node = canvasService.updateNode(id, nodeId, body);
        return node == null ? ApiResponse.error(46011, "画布节点不存在") : ApiResponse.success(node);
    }

    @DeleteMapping("/projects/{id}/nodes/{nodeId}")
    public ApiResponse<Void> deleteNode(@PathVariable String id, @PathVariable String nodeId) {
        canvasService.deleteNode(id, nodeId);
        return ApiResponse.success();
    }

    @PostMapping("/projects/{id}/nodes/{nodeId}/duplicate")
    public ApiResponse<?> duplicateNode(@PathVariable String id, @PathVariable String nodeId) {
        CanvasNode node = canvasService.duplicateNode(id, nodeId);
        return node == null ? ApiResponse.error(46011, "画布节点不存在") : ApiResponse.success(node);
    }

    @PatchMapping("/projects/{id}/nodes/positions")
    public ApiResponse<Void> updateNodePositions(@PathVariable String id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> positions = (List<Map<String, Object>>) body.get("positions");
        if (positions != null) {
            for (Map<String, Object> pos : positions) {
                Map<String, Object> update = new LinkedHashMap<>();
                if (pos.containsKey("x")) update.put("x", pos.get("x"));
                if (pos.containsKey("y")) update.put("y", pos.get("y"));
                canvasService.updateNode(id, (String) pos.get("node_id"), update);
            }
        }
        return ApiResponse.success();
    }

    // ===== Connections =====
    @PostMapping("/projects/{id}/nodes/connect")
    public ApiResponse<?> connectNodes(@PathVariable String id, @RequestBody Map<String, Object> body) {
        CanvasEdge edge = canvasService.connectNodes(id, body);
        return edge == null ? ApiResponse.error(46012, "连线节点不存在") : ApiResponse.success(edge);
    }

    @DeleteMapping("/projects/{id}/connections/{connId}")
    public ApiResponse<Void> deleteConnection(@PathVariable String id, @PathVariable String connId) {
        canvasService.deleteConnection(id, connId);
        return ApiResponse.success();
    }

    // ===== Groups =====
    @PostMapping("/projects/{id}/groups")
    public ApiResponse<?> groupNodes(@PathVariable String id, @RequestBody Map<String, Object> body) {
        CanvasGroup group = canvasService.groupNodes(id, body);
        return group == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(group);
    }

    // ===== Shots =====
    @GetMapping("/projects/{id}/shots")
    public ApiResponse<?> getShots(@PathVariable String id) {
        if (canvasService.getProject(id) == null) return ApiResponse.error(46001, "画布项目不存在");
        return ApiResponse.success(canvasService.getProjectShots(id));
    }

    @PostMapping("/projects/{id}/shots")
    public ApiResponse<?> createShot(@PathVariable String id, @RequestBody Map<String, Object> body) {
        StoryboardShot shot = canvasService.createShot(id, body);
        return shot == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(shot);
    }

    @PutMapping("/projects/{id}/shots/{shotId}")
    public ApiResponse<Void> updateShot(@PathVariable String id, @PathVariable String shotId,
                                        @RequestBody Map<String, Object> body) {
        canvasService.updateShot(id, shotId, body);
        return ApiResponse.success();
    }

    @DeleteMapping("/projects/{id}/shots/{shotId}")
    public ApiResponse<Void> deleteShot(@PathVariable String id, @PathVariable String shotId) {
        canvasService.deleteShot(id, shotId);
        return ApiResponse.success();
    }

    @PutMapping("/projects/{id}/shots/reorder")
    public ApiResponse<Void> reorderShots(@PathVariable String id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> shotIds = (List<String>) body.get("shot_ids");
        if (shotIds != null) canvasService.reorderShots(id, shotIds);
        return ApiResponse.success();
    }

    @PutMapping("/projects/{id}/shots/{shotId}/keyframe")
    public ApiResponse<Void> updateKeyframe(@PathVariable String id, @PathVariable String shotId,
                                            @RequestBody Map<String, Object> body) {
        canvasService.updateShot(id, shotId, body);
        return ApiResponse.success();
    }

    // ===== Generation (via task queue) =====
    @PostMapping("/projects/{id}/shots/{shotId}/generate")
    public ApiResponse<?> generateShot(@PathVariable String id, @PathVariable String shotId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        GenerationTask task = canvasService.enqueueTask(id, "shot_generate",
                Map.of("shot_id", shotId, "params", body == null ? Map.of() : body));
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    @PostMapping("/projects/{id}/shots/batch-generate")
    public ApiResponse<?> batchGenerateShots(@PathVariable String id, @RequestBody Map<String, Object> body) {
        GenerationTask task = canvasService.enqueueTask(id, "batch_generate", body);
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    @PostMapping("/projects/{id}/shots/{shotId}/inpaint")
    public ApiResponse<?> inpaint(@PathVariable String id, @PathVariable String shotId,
                                  @RequestBody Map<String, Object> body) {
        GenerationTask task = canvasService.enqueueTask(id, "inpaint",
                Map.of("shot_id", shotId, "params", body));
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    @PostMapping("/projects/{id}/shots/{shotId}/outpaint")
    public ApiResponse<?> outpaint(@PathVariable String id, @PathVariable String shotId,
                                   @RequestBody Map<String, Object> body) {
        GenerationTask task = canvasService.enqueueTask(id, "outpaint",
                Map.of("shot_id", shotId, "params", body));
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    @PostMapping("/projects/{id}/shots/{shotId}/generate-multimodal")
    public ApiResponse<?> generateMultimodalVideo(@PathVariable String id, @PathVariable String shotId,
                                                  @RequestBody Map<String, Object> body) {
        GenerationTask task = canvasService.enqueueTask(id, "multimodal_video",
                Map.of("shot_id", shotId, "params", body));
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    // ===== Script Node Operations =====
    @PostMapping("/projects/{id}/nodes/{nodeId}/script/generate-storyboard")
    public ApiResponse<?> generateStoryboard(@PathVariable String id, @PathVariable String nodeId,
                                             @RequestBody Map<String, Object> body) {
        GenerationTask task = canvasService.enqueueTask(id, "storyboard_generate",
                Map.of("node_id", nodeId, "params", body));
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    @PostMapping("/projects/{id}/nodes/{nodeId}/script/batch-image")
    public ApiResponse<?> batchImage(@PathVariable String id, @PathVariable String nodeId,
                                     @RequestBody Map<String, Object> body) {
        GenerationTask task = canvasService.enqueueTask(id, "script_batch_image",
                Map.of("node_id", nodeId, "params", body));
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    @PostMapping("/projects/{id}/nodes/{nodeId}/script/batch-video")
    public ApiResponse<?> batchVideo(@PathVariable String id, @PathVariable String nodeId,
                                     @RequestBody Map<String, Object> body) {
        GenerationTask task = canvasService.enqueueTask(id, "script_batch_video",
                Map.of("node_id", nodeId, "params", body));
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    @PutMapping("/projects/{id}/nodes/{nodeId}/script/cell")
    public ApiResponse<?> updateScriptCell(@PathVariable String id, @PathVariable String nodeId,
                                           @RequestBody Map<String, Object> body) {
        CanvasNode node = canvasService.updateNode(id, nodeId, Map.of("data", body));
        return node == null ? ApiResponse.error(46011, "画布节点不存在") : ApiResponse.success(node);
    }

    // ===== Timeline =====
    @GetMapping("/projects/{id}/timeline/full")
    public ApiResponse<?> getFullTimeline(@PathVariable String id) {
        if (canvasService.getProject(id) == null) return ApiResponse.error(46001, "画布项目不存在");
        return ApiResponse.success(canvasService.getFullTimeline(id));
    }

    @PutMapping("/projects/{id}/timeline/full")
    public ApiResponse<?> updateFullTimeline(@PathVariable String id, @RequestBody Map<String, Object> body) {
        canvasService.updateFullTimeline(id, body);
        return ApiResponse.success(canvasService.getFullTimeline(id));
    }

    @PostMapping("/projects/{id}/timeline/dub")
    public ApiResponse<?> generateDub(@PathVariable String id, @RequestBody Map<String, Object> body) {
        GenerationTask task = canvasService.enqueueTask(id, "dub", body);
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    @PostMapping("/projects/{id}/timeline/subtitle")
    public ApiResponse<?> generateSubtitle(@PathVariable String id, @RequestBody Map<String, Object> body) {
        GenerationTask task = canvasService.enqueueTask(id, "subtitle", body);
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    @PostMapping("/projects/{id}/timeline/clip")
    public ApiResponse<?> clipTimeline(@PathVariable String id, @RequestBody Map<String, Object> body) {
        GenerationTask task = canvasService.enqueueTask(id, "timeline_clip", body);
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    @PostMapping("/projects/{id}/timeline/splice")
    public ApiResponse<?> spliceTimeline(@PathVariable String id, @RequestBody Map<String, Object> body) {
        GenerationTask task = canvasService.enqueueTask(id, "timeline_splice", body);
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    // ===== Compose & Export =====
    @PostMapping("/projects/{id}/compose")
    public ApiResponse<?> compose(@PathVariable String id) {
        GenerationTask task = canvasService.enqueueTask(id, "compose", Map.of("mode", "preview"));
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    @GetMapping("/projects/{id}/compose/{taskId}")
    public ApiResponse<?> getComposeStatus(@PathVariable String id, @PathVariable String taskId) {
        GenerationTask task = canvasService.getTaskStatus(taskId);
        return task == null ? ApiResponse.error(46020, "任务不存在") : ApiResponse.success(task);
    }

    @PostMapping("/projects/{id}/export")
    public ApiResponse<?> exportVideo(@PathVariable String id, @RequestBody Map<String, Object> body) {
        GenerationTask task = canvasService.enqueueTask(id, "export", body);
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    @GetMapping("/export/{taskId}")
    public ApiResponse<?> getExportStatus(@PathVariable String taskId) {
        GenerationTask task = canvasService.getTaskStatus(taskId);
        return task == null ? ApiResponse.error(46020, "任务不存在") : ApiResponse.success(task);
    }

    @GetMapping("/export/{taskId}/download")
    public ApiResponse<Map<String, String>> download(@PathVariable String taskId) {
        return ApiResponse.success(Map.of("download_url",
                "https://cdn.example.com/exports/" + taskId + ".mp4?sign=mock&expires=9999999999"));
    }

    // ===== Workflows =====
    @PostMapping("/projects/{id}/workflows")
    public ApiResponse<?> createWorkflow(@PathVariable String id, @RequestBody Map<String, Object> body) {
        CanvasWorkflow wf = canvasService.createWorkflow(id, body);
        return wf == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(wf);
    }

    @GetMapping("/projects/{id}/workflows")
    public ApiResponse<?> getWorkflows(@PathVariable String id) {
        if (canvasService.getProject(id) == null) return ApiResponse.error(46001, "画布项目不存在");
        return ApiResponse.success(canvasService.getWorkflows(id));
    }

    @PostMapping("/projects/{id}/workflows/{wfId}/apply")
    public ApiResponse<?> applyWorkflow(@PathVariable String id, @PathVariable String wfId) {
        GenerationTask task = canvasService.enqueueTask(id, "workflow_apply",
                Map.of("workflow_id", wfId));
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    @PostMapping("/projects/{id}/workflows/{wfId}/execute-all")
    public ApiResponse<?> executeWorkflow(@PathVariable String id, @PathVariable String wfId) {
        GenerationTask task = canvasService.enqueueTask(id, "workflow_execute",
                Map.of("workflow_id", wfId));
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    // ===== Slash Commands =====
    @PostMapping("/projects/{id}/slash/{command}")
    public ApiResponse<?> slash(@PathVariable String id, @PathVariable String command,
                                @RequestBody(required = false) Map<String, Object> body) {
        GenerationTask task = canvasService.enqueueTask(id, "slash_" + command,
                body == null ? Map.of() : body);
        return task == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(task);
    }

    // ===== Director Desk =====
    @PostMapping("/projects/{id}/director-desk")
    public ApiResponse<?> createDirectorDesk(@PathVariable String id,
                                              @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("label", "导演台 · 3D构图");
        data.put("scene", body == null ? Map.of() : body);
        data.put("captures", List.of());
        CanvasNode node = canvasService.createNode(id, Map.of("type", "reference", "x", 240, "y", 180, "data", data));
        return node == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(node);
    }

    @GetMapping("/projects/{id}/director-desk/{deskId}")
    public ApiResponse<?> getDirectorDesk(@PathVariable String id, @PathVariable String deskId) {
        return ApiResponse.success(Map.of("captures", List.of(
                Map.of("angle", "front", "image_url", "/mock/captures/" + deskId + "_front.png"),
                Map.of("angle", "side", "image_url", "/mock/captures/" + deskId + "_side.png"),
                Map.of("angle", "top", "image_url", "/mock/captures/" + deskId + "_top.png"))));
    }

    @PutMapping("/projects/{id}/director-desk/{deskId}")
    public ApiResponse<?> updateDirectorDesk(@PathVariable String id, @PathVariable String deskId,
                                             @RequestBody Map<String, Object> body) {
        CanvasNode node = canvasService.updateNode(id, deskId, Map.of("data", body));
        return node == null ? ApiResponse.error(46011, "导演台不存在") : ApiResponse.success(node);
    }

    // TODO: 当前为 mock 实现，文件未实际存储。待接入 MinIO/OSS 后实现真实上传与模型解析。
    @PostMapping("/projects/{id}/director-desk/{deskId}/assets/model")
    public ApiResponse<?> uploadDirectorModel(@PathVariable String id,
                                              @PathVariable String deskId,
                                              @RequestParam(value = "file", required = false) MultipartFile file,
                                              @RequestParam(value = "name", required = false) String name) {
        if (findProjectNode(id, deskId) == null) return ApiResponse.error(46011, "导演台不存在");
        String fileName = file == null ? Optional.ofNullable(name).orElse("local-model.glb") : file.getOriginalFilename();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("asset_id", "asset_model_" + shortUuid());
        data.put("model_url", "/mock/models/" + fileName);
        data.put("metadata", Map.of(
                "format", fileName != null && fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "glb",
                "size", file == null ? 0 : file.getSize(),
                "triangle_count", 0));
        if (file != null && !file.isEmpty()) {
            log.warn("3D model upload is stub — file '{}' ({} bytes) not persisted to storage", fileName, file.getSize());
        }
        return ApiResponse.success(data);
    }

    // TODO: 当前返回 mock 截图 URL。待接入服务端 3D 渲染管线后替换为真实截图。
    @PostMapping("/projects/{id}/director-desk/{deskId}/capture")
    public ApiResponse<?> captureDirectorDesk(@PathVariable String id,
                                              @PathVariable String deskId,
                                              @RequestBody(required = false) Map<String, Object> body) {
        CanvasNode node = findProjectNode(id, deskId);
        if (node == null) return ApiResponse.error(46011, "导演台不存在");

        Map<String, Object> data = readNodeData(node);
        // 防止 JSON 解析失败导致的静默数据覆盖
        if (data.isEmpty() && node.getInputData() != null && !node.getInputData().isBlank()) {
            return ApiResponse.error(46030, "节点数据解析失败，请检查数据格式");
        }
        Map<String, Object> director = ensureMap(data, "director");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> screenshots = (List<Map<String, Object>>) director.computeIfAbsent("shots", k -> new ArrayList<>());

        String shotId = "shot_" + shortUuid();
        String aspect = String.valueOf((body == null ? Map.of() : body).getOrDefault("aspect_ratio",
                director.getOrDefault("aspect", data.getOrDefault("aspect_ratio", "16:9"))));
        Map<String, Object> shot = new LinkedHashMap<>();
        shot.put("id", shotId);
        shot.put("name", "机位 " + String.format("%02d", screenshots.size() + 1));
        shot.put("camera_id", "camera_1");
        shot.put("aspect", aspect);
        shot.put("aspect_ratio", aspect);
        shot.put("view", "服务端截图");
        shot.put("fov", 50);
        shot.put("image_url", "/mock/captures/" + deskId + "_" + shotId + ".png");
        shot.put("preview_url", shot.get("image_url"));
        shot.put("created_at", new Date().toString());
        screenshots.add(shot);
        canvasService.updateNode(id, deskId, Map.of("data", data, "status", "ready"));
        return ApiResponse.success(Map.of("screenshots", List.of(shot)));
    }

    @PostMapping("/projects/{id}/director-desk/{deskId}/screenshots/{screenshotId}/send-to-canvas")
    public ApiResponse<?> sendDirectorScreenshotToCanvas(@PathVariable String id,
                                                         @PathVariable String deskId,
                                                         @PathVariable String screenshotId,
                                                         @RequestBody(required = false) Map<String, Object> body) {
        CanvasNode source = findProjectNode(id, deskId);
        if (source == null) return ApiResponse.error(46011, "导演台不存在");
        Map<String, Object> sourceData = readNodeData(source);
        // 防止 JSON 解析失败导致的静默数据覆盖
        if (sourceData.isEmpty() && source.getInputData() != null && !source.getInputData().isBlank()) {
            return ApiResponse.error(46030, "节点数据解析失败，请检查数据格式");
        }
        Map<String, Object> director = ensureMap(sourceData, "director");
        Map<String, Object> shot = findDirectorShot(director, screenshotId);
        if (shot == null) return ApiResponse.error(46021, "截图不存在");

        int x = source.getX() == null ? 360 : source.getX() + Optional.ofNullable(source.getWidth()).orElse(280) + 120;
        int y = source.getY() == null ? 160 : source.getY();
        if (body != null && body.get("target_position") instanceof Map<?, ?> pos) {
            x = toInt(pos.get("x"), x);
            y = toInt(pos.get("y"), y);
        }
        Map<String, Object> imageData = new LinkedHashMap<>();
        imageData.put("prompt", shot.getOrDefault("name", "导演台截图") + "：作为构图和站位参考");
        imageData.put("source", "director");
        imageData.put("director_node_id", deskId);
        imageData.put("director_shot_id", screenshotId);
        imageData.put("aspect_ratio", shot.getOrDefault("aspect_ratio", shot.get("aspect")));
        imageData.put("preview_url", shot.getOrDefault("preview_url", shot.get("image_url")));
        CanvasNode imageNode = canvasService.createNode(id, Map.of("type", "image", "x", x, "y", y, "data", imageData));
        if (imageNode == null) return ApiResponse.error(46001, "画布项目不存在");
        CanvasEdge edge = canvasService.connectNodes(id, Map.of(
                "source_node_id", source.getUuid(),
                "target_node_id", imageNode.getUuid(),
                "source_port", "out",
                "target_port", "in"));
        shot.put("sent_to_canvas", true);
        shot.put("target_node_id", imageNode.getUuid());
        canvasService.updateNode(id, deskId, Map.of("data", sourceData));
        return ApiResponse.success(Map.of("image_node_id", imageNode.getUuid(), "edge_id", edge == null ? "" : edge.getUuid()));
    }

    // TODO: 当前为 mock 实现，直接返回成功。待接入 AI 识图服务后改为异步任务模式（enqueueTask）。
    @PostMapping("/projects/{id}/director-desk/{deskId}/ai-import")
    public ApiResponse<?> aiImportDirectorDesk(@PathVariable String id,
                                               @PathVariable String deskId,
                                               @RequestBody(required = false) Map<String, Object> body) {
        CanvasNode node = findProjectNode(id, deskId);
        if (node == null) return ApiResponse.error(46011, "导演台不存在");
        // 防御：required=false 时 body 可能为 null
        Map<String, Object> safeBody = body == null ? Map.of() : body;
        Map<String, Object> data = readNodeData(node);
        // 防止 JSON 解析失败导致的静默数据覆盖
        if (data.isEmpty() && node.getInputData() != null && !node.getInputData().isBlank()) {
            return ApiResponse.error(46030, "节点数据解析失败，请检查数据格式");
        }
        Map<String, Object> director = ensureMap(data, "director");
        Map<String, Object> scene = ensureMap(director, "scene");
        scene.put("panoramaStatus", "AI 识图导入完成，已生成全景背景候选");
        scene.put("panoramaAssetId", "asset_panorama_" + shortUuid());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> imports = (List<Map<String, Object>>) director.computeIfAbsent("ai_imports", k -> new ArrayList<>());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "import_" + shortUuid());
        item.put("source_asset_id", safeBody.get("source_asset_id"));
        item.put("mode", safeBody.getOrDefault("mode", "insert"));
        item.put("status", "succeeded");
        item.put("result_panorama_asset_id", scene.get("panoramaAssetId"));
        item.put("recognized_scene", Map.of("description", "AI recognized scene", "horizon", 0.48, "perspective", "one_point"));
        imports.add(item);
        canvasService.updateNode(id, deskId, Map.of("data", data, "status", "ready"));
        return ApiResponse.success(Map.of("task_id", "task_ai_import_" + shortUuid(), "import_id", item.get("id"), "status", "succeeded"));
    }

    // ===== Material Drop =====
    @PostMapping("/projects/{id}/assets/drop")
    public ApiResponse<?> dropMaterial(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String assetType = (String) body.getOrDefault("asset_type", "image");
        String type = switch (assetType) {
            case "video" -> "video"; case "audio" -> "audio"; case "text" -> "text";
            default -> "image";
        };
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("label", "素材 · " + body.getOrDefault("name", "拖入素材"));
        data.put("file_url", body.get("file_url"));
        CanvasNode node = canvasService.createNode(id, Map.of(
                "type", type, "x", body.getOrDefault("x", 200),
                "y", body.getOrDefault("y", 200), "data", data));
        return node == null ? ApiResponse.error(46001, "画布项目不存在") : ApiResponse.success(node);
    }

    private CanvasNode findProjectNode(String projectId, String nodeId) {
        return canvasService.getProjectNodes(projectId).stream()
                .filter(node -> Objects.equals(node.getUuid(), nodeId) || Objects.equals(String.valueOf(node.getId()), nodeId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 从节点的 inputData JSON 字段反序列化为 Map。
     * 解析失败时记录 WARN 日志并返回空 Map —— 调用方需注意：
     * 若后续调用 updateNode 将空 Map 写回，将覆盖原始数据。
     * 建议调用方在解析失败时返回错误响应而非静默覆盖。
     */
    private Map<String, Object> readNodeData(CanvasNode node) {
        if (node == null || node.getInputData() == null || node.getInputData().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(node.getInputData(), new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse node {} inputData JSON — returning empty map to prevent data corruption cascade. Error: {}",
                    node.getUuid(), e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * 确保 parent Map 中 key 对应的值为 Map&lt;String, Object&gt; 类型。
     * 若已存在 Map 但其 key 非 String（如 Integer/UUID），会通过 String.valueOf 转换，
     * 此为有损操作 —— 假定上游数据始终使用字符串 key。
     * 若 key 不存在或值为其他类型，则创建新的 LinkedHashMap。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> ensureMap(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            map.forEach((k, v) -> typed.put(String.valueOf(k), v));
            parent.put(key, typed);
            return typed;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        parent.put(key, created);
        return created;
    }

    /**
     * 从导演台数据中按 screenshotId 查找截图。
     * 优先查找 "shots" 字段（当前规范），
     * 回退到 "screenshots" 字段（旧版命名，向后兼容 —— TODO: 数据迁移完成后移除回退逻辑）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> findDirectorShot(Map<String, Object> director, String screenshotId) {
        Object shots = director.get("shots");
        List<?> list;
        if (shots instanceof List<?> current) {
            list = current;
        } else {
            // 向后兼容旧版 "screenshots" key，数据迁移完成后移除
            shots = director.get("screenshots");
            if (!(shots instanceof List<?> fallback)) return null;
            list = fallback;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map && Objects.equals(String.valueOf(map.get("id")), screenshotId)) {
                return (Map<String, Object>) item;
            }
        }
        return null;
    }

    private int toInt(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
