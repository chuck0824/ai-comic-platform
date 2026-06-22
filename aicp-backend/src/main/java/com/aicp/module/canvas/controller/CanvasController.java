package com.aicp.module.canvas.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.module.canvas.entity.*;
import com.aicp.module.canvas.service.CanvasService;
import com.aicp.module.generation.entity.GenerationTask;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/canvas")
@RequiredArgsConstructor
public class CanvasController {

    private final CanvasService canvasService;

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
}
