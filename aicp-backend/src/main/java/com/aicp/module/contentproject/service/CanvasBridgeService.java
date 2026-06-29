package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.canvas.entity.CanvasNode;
import com.aicp.module.canvas.entity.CanvasProject;
import com.aicp.module.canvas.mapper.*;
import com.aicp.module.canvas.service.CanvasService;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.mapper.*;
import com.aicp.module.contentproject.entity.StoryboardMaster;
import com.aicp.module.contentproject.entity.StoryboardScene;
import com.aicp.module.contentproject.entity.StoryboardShot;
import com.aicp.module.contentproject.mapper.ContentStoryboardMasterMapper;
import com.aicp.module.contentproject.mapper.ContentStoryboardSceneMapper;
import com.aicp.module.contentproject.mapper.ContentStoryboardShotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * M5: Bridge between content-project storyboards and canvas module.
 * Imports cp_storyboard_shots as canvas nodes, creates canvas projects.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanvasBridgeService {

    private final CanvasProjectMapper canvasProjectMapper;
    private final CanvasNodeMapper canvasNodeMapper;
    private final CanvasEdgeMapper canvasEdgeMapper;
    private final ContentStoryboardMasterMapper sbMasterMapper;
    private final ContentStoryboardSceneMapper sbSceneMapper;
    private final ContentStoryboardShotMapper sbShotMapper;
    private final CanvasService canvasService;
    private final com.aicp.module.canvas.mapper.StoryboardShotMapper canvasShotMapper;

    /**
     * Import A-tier storyboard into canvas as concept verification project.
     * Creates canvas project, nodes from shots, and edges between sequential shots.
     */
    @Transactional
    public Map<String, Object> importToCanvas(Long projectId, Long masterId, Long userId) {
        StoryboardMaster master = sbMasterMapper.selectById(masterId);
        if (master == null || !master.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!"A".equals(master.getTier()) && !"B".equals(master.getTier())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅 A/B 档分镜可导入画布概念验证");
        }

        // Create canvas project
        CanvasProject cp = new CanvasProject();
        cp.setUuid("canvas_" + UUID.randomUUID().toString().replace("-", "").substring(0, 7));
        cp.setUserId(userId);
        cp.setName("分镜导入 - " + master.getTier() + "档");
        cp.setScriptId(projectId);
        cp.setStatus("editing");
        cp.setCanvasVersion(1);
        canvasProjectMapper.insert(cp);

        // Load scenes and shots
        List<StoryboardScene> scenes = sbSceneMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StoryboardScene>()
                        .eq(StoryboardScene::getMasterId, masterId)
                        .orderByAsc(StoryboardScene::getSortOrder));

        List<StoryboardShot> shots = sbShotMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StoryboardShot>()
                        .eq(StoryboardShot::getMasterId, masterId)
                        .orderByAsc(StoryboardShot::getSortOrder));

        // Create nodes: one per shot
        Map<Long, Long> shotToNode = new LinkedHashMap<>();
        int col = 0;
        for (StoryboardShot shot : shots) {
            Map<String, Object> nodeData = new LinkedHashMap<>();
            nodeData.put("shot_no", shot.getShotNo());
            nodeData.put("shot_type", shot.getShotType());
            nodeData.put("description", shot.getDescription());
            nodeData.put("camera_action", shot.getCameraAction());
            nodeData.put("duration_sec", shot.getDurationSec());
            nodeData.put("visual_ref_url", shot.getVisualRefUrl());

            CanvasNode node = new CanvasNode();
            node.setUuid("node_" + UUID.randomUUID().toString().replace("-", "").substring(0, 7));
            node.setProjectId(cp.getId());
            node.setType("shot");
            node.setName("镜头 " + shot.getShotNo());
            node.setX(80 + col * 220);
            node.setY(80);
            node.setWidth(200);
            node.setHeight(180);
            node.setInputData(toJson(nodeData));
            node.setStatus("ready");
            canvasNodeMapper.insert(node);

            shotToNode.put(shot.getId(), node.getId());
            col++;
        }

        // Create edges between sequential shots
        Long prevNodeId = null;
        for (Long shotId : shotToNode.keySet()) {
            Long nodeId = shotToNode.get(shotId);
            if (prevNodeId != null) {
                com.aicp.module.canvas.entity.CanvasEdge edge = new com.aicp.module.canvas.entity.CanvasEdge();
                edge.setUuid("edge_" + UUID.randomUUID().toString().replace("-", "").substring(0, 7));
                edge.setProjectId(cp.getId());
                edge.setSourceNodeId(prevNodeId);
                edge.setTargetNodeId(nodeId);
                edge.setSourcePort("out");
                edge.setTargetPort("in");
                edge.setEdgeType("sequence");
                canvasEdgeMapper.insert(edge);
            }
            prevNodeId = nodeId;
        }

        // Also sync to canvas storyboard_shots table
        for (StoryboardShot shot : shots) {
            com.aicp.module.canvas.entity.StoryboardShot canvasShot = new com.aicp.module.canvas.entity.StoryboardShot();
            canvasShot.setUuid(UUID.randomUUID().toString());
            canvasShot.setProjectId(cp.getId());
            canvasShot.setShotNo(shot.getShotNo());
            canvasShot.setSceneNo(1);
            canvasShot.setDuration(shorterDuration(shot.getDurationSec()));
            canvasShot.setShotSize(shot.getShotType());
            canvasShot.setVisualDescription(shot.getDescription());
            canvasShot.setImageStatus("pending");
            canvasShot.setVideoStatus("pending");
            canvasShotMapper.insert(canvasShot);
        }

        log.info("Imported master {} to canvas project {}: {} nodes, {} edges",
                masterId, cp.getId(), shotToNode.size(), shotToNode.size() - 1);

        return Map.of(
                "canvas_project_id", cp.getUuid(),
                "canvas_project_pk", cp.getId(),
                "nodes_created", shotToNode.size(),
                "edges_created", Math.max(0, shotToNode.size() - 1));
    }

    private String toJson(Object obj) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }

    private int shorterDuration(Integer sec) {
        return sec != null ? sec * 1000 : 3000; // seconds → ms for canvas
    }
}
