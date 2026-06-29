package com.aicp.module.contentproject.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * M1: A-tier storyboard generation — scene dramatic goal cards, beats,
 * lightweight master storyboard, shot/duration budget.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoryboardService {

    private final ContentStoryboardMasterMapper masterMapper;
    private final ContentStoryboardSceneMapper sceneMapper;
    private final ContentStoryboardShotMapper shotMapper;
    private final ContentUnitMapper unitMapper;
    private final ContentVersionMapper versionMapper;
    private final AiRouter aiRouter;
    private final AiResponseParser parser;

    static final String STORYBOARD_A_SYSTEM_PROMPT = """
        你是一位资深导演，擅长将剧本转化为分镜脚本（A-tier）。
        请根据提供的剧本内容生成分镜，输出JSON格式：

        {
          "scenes": [
            {
              "scene_no": 1,
              "dramatic_goal": "本场景的戏剧目标",
              "beat_description": "节拍描述",
              "characters": ["角色1", "角色2"],
              "estimated_duration_sec": 30,
              "shots": [
                {
                  "shot_no": 1,
                  "shot_type": "medium_shot|close_up|wide_shot|over_shoulder|pov|establishing|two_shot",
                  "duration_sec": 5,
                  "description": "画面描述",
                  "camera_action": "推/拉/摇/移/跟/升/降",
                  "dialogue_ref": "对白关键词"
                }
              ]
            }
          ],
          "total_shots": 20,
          "estimated_duration_sec": 120
        }

        要求：
        - 每个场景2-5个镜头
        - 镜头类型多样化
        - 时长估算合理（短剧每集60-180秒）
        """;

    @Transactional
    public StoryboardMaster generateATier(Long userId, Long projectId, Long contentUnitId) {
        ContentUnit unit = unitMapper.selectById(contentUnitId);
        if (unit == null || !unit.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        // Get source content
        ContentVersion version = versionMapper.selectOne(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, contentUnitId)
                        .gt(ContentVersion::getVersionNo, 0)
                        .orderByDesc(ContentVersion::getVersionNo)
                        .last("limit 1"));
        if (version == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "没有可用的内容版本");
        }

        String content = version.getPlainText();
        if (content == null || content.isBlank()) {
            content = version.getContentJson();
        }

        // Check for existing master — reuse to avoid race condition
        StoryboardMaster existing = masterMapper.selectOne(
                new LambdaQueryWrapper<StoryboardMaster>()
                        .eq(StoryboardMaster::getProjectId, projectId)
                        .eq(StoryboardMaster::getContentUnitId, contentUnitId)
                        .eq(StoryboardMaster::getTier, "A")
                        .eq(StoryboardMaster::getIsDeleted, 0));
        if (existing != null) {
            // Clear old scenes/shots for regeneration (keep master row to avoid race)
            sceneMapper.delete(new LambdaQueryWrapper<StoryboardScene>()
                    .eq(StoryboardScene::getMasterId, existing.getId()));
            shotMapper.delete(new LambdaQueryWrapper<StoryboardShot>()
                    .eq(StoryboardShot::getMasterId, existing.getId()));
            // Reset lock since regeneration invalidates previous lock
            existing.setLockedBy(null);
            existing.setLockedAt(null);
        }

        // Call AI
        Map<String, Object> aiParams = new LinkedHashMap<>();
        aiParams.put("system_prompt", STORYBOARD_A_SYSTEM_PROMPT);
        aiParams.put("prompt", "请为以下剧本生成A-tier分镜：\n\n" + parser.ellipsis(content, 4000));
        aiParams.put("temperature", 0.7);
        aiParams.put("max_tokens", 4096);

        log.info("Generating A-tier storyboard for unit {}", contentUnitId);
        Map<String, Object> aiResult = aiRouter.chatCompletion(aiParams);
        String resultText = parser.extractText(aiResult);
        Map<String, Object> parsed = parser.parseJson(resultText);

        // Reuse existing master or create new one
        StoryboardMaster master;
        if (existing != null) {
            master = existing;
            master.setSourceVersionId(version.getId());
            master.setRevision(existing.getRevision() + 1);
        } else {
            master = new StoryboardMaster();
            master.setUuid(UUID.randomUUID().toString());
            master.setProjectId(projectId);
            master.setContentUnitId(contentUnitId);
            master.setTier("A");
            master.setStatus("draft");
            master.setSourceVersionId(version.getId());
            master.setRevision(0);
            master.setIsDeleted(0);
            masterMapper.insert(master);
        }

        // Parse scenes and shots
        int totalShots = 0;
        int totalDuration = 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenes = (List<Map<String, Object>>) parsed.getOrDefault("scenes", List.of());

        int sceneOrder = 0;
        for (Map<String, Object> sceneData : scenes) {
            sceneOrder++;
            StoryboardScene scene = new StoryboardScene();
            scene.setMasterId(master.getId());
            scene.setSceneNo(parser.toInt(sceneData.get("scene_no"), sceneOrder));
            scene.setDramaticGoal(parser.str(sceneData.get("dramatic_goal")));
            scene.setBeatDescription(parser.str(sceneData.get("beat_description")));
            scene.setCharacterIds(parser.toJson(sceneData.get("characters")));
            scene.setDurationSec(parser.toInt(sceneData.get("estimated_duration_sec"), 30));
            scene.setSortOrder(sceneOrder);
            sceneMapper.insert(scene);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> shots = (List<Map<String, Object>>) sceneData.getOrDefault("shots", List.of());
            int shotOrder = 0;
            for (Map<String, Object> shotData : shots) {
                shotOrder++;
                StoryboardShot shot = new StoryboardShot();
                shot.setUuid(UUID.randomUUID().toString());
                shot.setSceneId(scene.getId());
                shot.setMasterId(master.getId());
                shot.setShotNo(parser.toInt(shotData.get("shot_no"), shotOrder));
                shot.setShotType(parser.str(shotData.get("shot_type")));
                shot.setDurationSec(parser.toInt(shotData.get("duration_sec"), 5));
                shot.setDescription(parser.str(shotData.get("description")));
                shot.setCameraAction(parser.str(shotData.get("camera_action")));
                shot.setDialogueRef(parser.str(shotData.get("dialogue_ref")));
                shot.setStatus("draft");
                shot.setSortOrder(shotOrder);
                shotMapper.insert(shot);
                totalShots++;
                totalDuration += shot.getDurationSec();
            }
        }

        // Update master totals
        master.setTotalShots(totalShots);
        master.setEstimatedDurationSec(totalDuration > 0 ? totalDuration
                : parser.toInt(parsed.get("estimated_duration_sec"), 120));
        masterMapper.updateById(master);

        log.info("Generated A-tier storyboard: master={}, scenes={}, shots={}",
                master.getId(), scenes.size(), totalShots);
        return master;
    }

    public StoryboardMaster getMaster(Long masterId) {
        StoryboardMaster master = masterMapper.selectById(masterId);
        if (master == null || master.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return master;
    }

    public List<StoryboardMaster> listMasters(Long projectId) {
        return masterMapper.selectList(
                new LambdaQueryWrapper<StoryboardMaster>()
                        .eq(StoryboardMaster::getProjectId, projectId)
                        .eq(StoryboardMaster::getIsDeleted, 0)
                        .orderByDesc(StoryboardMaster::getCreatedAt));
    }

    public List<StoryboardScene> listScenes(Long masterId) {
        return sceneMapper.selectList(
                new LambdaQueryWrapper<StoryboardScene>()
                        .eq(StoryboardScene::getMasterId, masterId)
                        .orderByAsc(StoryboardScene::getSortOrder));
    }

    public List<StoryboardShot> listShots(Long masterId) {
        return shotMapper.selectList(
                new LambdaQueryWrapper<StoryboardShot>()
                        .eq(StoryboardShot::getMasterId, masterId)
                        .orderByAsc(StoryboardShot::getSortOrder));
    }

    @Transactional
    public void lockMaster(Long masterId, Long userId) {
        StoryboardMaster master = masterMapper.selectById(masterId);
        if (master == null) throw new BizException(ErrorCode.NOT_FOUND);
        master.setStatus("locked");
        master.setLockedBy(userId);
        master.setLockedAt(LocalDateTime.now());
        masterMapper.updateById(master);
    }

    // ===== M5: B/C-tier upgrade =====

    @Transactional
    public Map<String, Object> upgradeToBTier(Long masterId, Long userId) {
        StoryboardMaster master = masterMapper.selectById(masterId);
        if (master == null || !"A".equals(master.getTier())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅 A 档分镜可升为 B 档");
        }

        // Use AI to enrich with director-level detail
        String systemPrompt = """
            你是资深导演。请为每个镜头添加导演意图、动作动机、关系调度、信息差、声画关系和剪辑点。
            输出JSON：{"shots":[{"shot_no":1,"director_intention":"","action_motivation":"",
            "relationship_blocking":"","information_gap":"","audio_visual":"","edit_point":""}]}
            """;

        List<StoryboardShot> existingShots = shotMapper.selectList(
                new LambdaQueryWrapper<StoryboardShot>().eq(StoryboardShot::getMasterId, masterId));
        StringBuilder ctx = new StringBuilder("现有镜头：\n");
        for (StoryboardShot s : existingShots) {
            ctx.append("镜头").append(s.getShotNo()).append(": ").append(s.getDescription()).append("\n");
        }

        Map<String, Object> result = aiRouter.chatCompletion(Map.of(
                "system_prompt", systemPrompt, "prompt", ctx.toString(),
                "temperature", 0.6, "max_tokens", 4096));
        Map<String, Object> parsed = parser.parseJson(parser.extractText(result));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shots = (List<Map<String, Object>>) parsed.getOrDefault("shots", List.of());
        for (Map<String, Object> s : shots) {
            int shotNo = parser.toInt(s.get("shot_no"), 0);
            if (shotNo > 0 && shotNo <= existingShots.size()) {
                StoryboardShot shot = existingShots.get(shotNo - 1);
                shot.setDescription(shot.getDescription() + "\n[导演意图] " + parser.str(s.get("director_intention")));
                shot.setCameraAction(parser.str(s.get("audio_visual")));
                shotMapper.updateById(shot);
            }
        }

        master.setTier("B");
        masterMapper.updateById(master);
        return Map.of("master_id", masterId, "upgraded_to", "B", "shots_enriched", shots.size());
    }

    @Transactional
    public Map<String, Object> upgradeToCTier(Long masterId, Long userId) {
        StoryboardMaster master = masterMapper.selectById(masterId);
        if (master == null || !"B".equals(master.getTier())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅 B 档分镜可升为 C 档");
        }

        String systemPrompt = """
            你是资深制片。请为批量生产准备：AI抽卡表、AI视频表、配音字幕表、失败策略。
            输出JSON：{"shots":[{"shot_no":1,"image_prompt":"","video_prompt":"",
            "dub_text":"","subtitle":"","failure_strategy":""}]}
            """;

        List<StoryboardShot> existingShots = shotMapper.selectList(
                new LambdaQueryWrapper<StoryboardShot>().eq(StoryboardShot::getMasterId, masterId));
        StringBuilder ctx = new StringBuilder();
        for (StoryboardShot s : existingShots) {
            ctx.append("镜头").append(s.getShotNo()).append(": ").append(s.getDescription()).append("\n");
        }

        Map<String, Object> result = aiRouter.chatCompletion(Map.of(
                "system_prompt", systemPrompt, "prompt", ctx.toString(),
                "temperature", 0.6, "max_tokens", 4096));
        Map<String, Object> parsed = parser.parseJson(parser.extractText(result));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shots = (List<Map<String, Object>>) parsed.getOrDefault("shots", List.of());
        for (Map<String, Object> s : shots) {
            int shotNo = parser.toInt(s.get("shot_no"), 0);
            if (shotNo > 0 && shotNo <= existingShots.size()) {
                StoryboardShot shot = existingShots.get(shotNo - 1);
                shot.setVisualRefUrl(parser.str(s.get("image_prompt")));
                shot.setDialogueRef(parser.str(s.get("dub_text")));
                shotMapper.updateById(shot);
            }
        }

        master.setTier("C");
        master.setStatus("locked");
        master.setLockedBy(userId);
        master.setLockedAt(LocalDateTime.now());
        masterMapper.updateById(master);
        return Map.of("master_id", masterId, "upgraded_to", "C", "locked", true, "shots_processed", shots.size());
    }
}
