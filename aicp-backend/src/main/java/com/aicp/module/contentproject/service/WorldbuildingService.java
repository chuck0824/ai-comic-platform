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

import java.util.*;

/**
 * M3: Long-form worldbuilding service.
 * Character profiles, plot tasks, volume outlines, locations, timeline, foreshadowing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorldbuildingService {

    private final CharacterProfileMapper charMapper;
    private final PlotTaskMapper taskMapper;
    private final VolumeOutlineMapper volumeMapper;
    private final WorldLocationMapper locationMapper;
    private final StoryTimelineMapper timelineMapper;
    private final ForeshadowingItemMapper foreshadowMapper;
    private final ContentUnitMapper unitMapper;
    private final AiRouter aiRouter;
    private final AiResponseParser parser;

    // ===== Character Profiles =====

    @Transactional
    public CharacterProfile createCharacter(Long projectId, String name, String role) {
        CharacterProfile cp = new CharacterProfile();
        cp.setProjectId(projectId);
        cp.setName(name);
        cp.setRole(role);
        cp.setStatus("draft");
        cp.setRelationshipsJson("[]");
        charMapper.insert(cp);
        return cp;
    }

    @Transactional
    public Map<String, Object> aiGenerateCharacter(Long projectId, String name, String context) {
        String prompt = """
            你是一位资深角色设计师。请为以下角色生成详细设定。输出JSON：
            {"name":"","role":"","archetype":"","appearance":"","personality":"",
             "motivation":"","long_term_goal":"","knowledge_boundary":"",
             "dialogue_style":"","backstory":"","relationships":[{"name":"","relation":"","dynamic":""}]}
            """;
        Map<String, Object> params = Map.of("system_prompt", prompt,
                "prompt", "角色：" + name + "\n上下文：" + context, "temperature", 0.7, "max_tokens", 2048);
        Map<String, Object> result = aiRouter.chatCompletion(params);
        String text = parser.extractText(result);
        Map<String, Object> parsed = parser.parseJson(text);

        CharacterProfile cp = new CharacterProfile();
        cp.setProjectId(projectId);
        cp.setName((String) parsed.getOrDefault("name", name));
        cp.setRole(parser.str(parsed.get("role")));
        cp.setArchetype(parser.str(parsed.get("archetype")));
        cp.setAppearance(parser.str(parsed.get("appearance")));
        cp.setPersonality(parser.str(parsed.get("personality")));
        cp.setMotivation(parser.str(parsed.get("motivation")));
        cp.setLongTermGoal(parser.str(parsed.get("long_term_goal")));
        cp.setKnowledgeBoundary(parser.str(parsed.get("knowledge_boundary")));
        cp.setDialogueStyle(parser.str(parsed.get("dialogue_style")));
        cp.setBackstory(parser.str(parsed.get("backstory")));
        cp.setRelationshipsJson(parser.toJson(parsed.get("relationships")));
        cp.setStatus("draft");
        charMapper.insert(cp);
        return parsed;
    }

    public List<CharacterProfile> listCharacters(Long projectId) {
        return charMapper.selectList(new LambdaQueryWrapper<CharacterProfile>()
                .eq(CharacterProfile::getProjectId, projectId));
    }

    // ===== Plot Tasks =====

    @Transactional
    public PlotTask createTask(Long projectId, String type, String title, String desc) {
        PlotTask pt = new PlotTask();
        pt.setProjectId(projectId);
        pt.setTaskType(type);
        pt.setTitle(title);
        pt.setDescription(desc);
        pt.setStatus("planned");
        pt.setSortOrder(getNextSort(taskMapper, projectId));
        taskMapper.insert(pt);
        return pt;
    }

    @Transactional
    public int aiGeneratePlotTasks(Long projectId, String synopsisContext) {
        String prompt = """
            你是一位资深故事策划。请根据故事上下文生成情节任务体系。输出JSON：
            {"main_quest":{"title":"","goals":[],"obstacles":[],"cost":""},
             "stage_tasks":[{"title":"","goal":"","obstacle":"","character":"","type":"main|sub|character","cost":"","parent_title":""}],
             "subplots":[{"title":"","description":"","characters":[]}]}
            """;
        Map<String, Object> result = aiRouter.chatCompletion(Map.of(
                "system_prompt", prompt, "prompt", synopsisContext, "temperature", 0.7, "max_tokens", 4096));
        String text = parser.extractText(result);
        Map<String, Object> parsed = parser.parseJson(text);

        // Extract main quest cost for use as default task cost
        @SuppressWarnings("unchecked")
        Map<String, Object> mainQuest = (Map<String, Object>) parsed.getOrDefault("main_quest", Map.of());
        String mainCost = parser.str(mainQuest.get("cost"));

        int count = 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) parsed.getOrDefault("stage_tasks", List.of());
        for (Map<String, Object> t : tasks) {
            PlotTask pt = new PlotTask();
            pt.setProjectId(projectId);
            pt.setTaskType(parser.str(t.get("type")));
            pt.setTitle(parser.str(t.get("title")));
            pt.setDescription(parser.str(t.get("goal")));
            pt.setStageGoals(parser.toJson(t.get("goal")));
            pt.setObstacles(parser.str(t.get("obstacle")));
            pt.setCost(parser.str(t.get("cost")));
            if (pt.getCost().isEmpty() && !mainCost.isEmpty()) {
                pt.setCost(mainCost);
            }
            pt.setCharacterIds(parser.str(t.get("character")));
            pt.setStatus("planned");
            pt.setSortOrder(count + 1);
            taskMapper.insert(pt);
            count++;
        }
        return count;
    }

    public List<PlotTask> listTasks(Long projectId) {
        return taskMapper.selectList(new LambdaQueryWrapper<PlotTask>()
                .eq(PlotTask::getProjectId, projectId).orderByAsc(PlotTask::getSortOrder));
    }

    // ===== Volume Outlines =====

    @Transactional
    public VolumeOutline createVolume(Long projectId, int volNo, String title) {
        VolumeOutline vo = new VolumeOutline();
        vo.setProjectId(projectId);
        vo.setVolumeNo(volNo);
        vo.setTitle(title);
        vo.setStatus("draft");
        vo.setSortOrder(volNo);
        volumeMapper.insert(vo);
        return vo;
    }

    @Transactional
    public int aiGenerateVolumes(Long projectId, int count, String context) {
        // Clear old volumes before regeneration to prevent duplicates
        volumeMapper.delete(new LambdaQueryWrapper<VolumeOutline>()
                .eq(VolumeOutline::getProjectId, projectId));

        String prompt = "请为以下故事生成" + count + "卷大纲。每卷包含目标、转折、卷末钩子、预估章节数和角色变化。输出JSON：{\"volumes\":[{\"volume_no\":1,\"title\":\"\",\"goal\":\"\",\"turns\":\"\",\"volume_end_hook\":\"\",\"character_changes\":\"\",\"chapter_count\":10}]}";
        Map<String, Object> result = aiRouter.chatCompletion(Map.of(
                "system_prompt", "你是资深故事架构师。", "prompt", prompt + "\n" + context, "temperature", 0.7, "max_tokens", 4096));
        Map<String, Object> parsed = parser.parseJson(parser.extractText(result));

        int created = 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vols = (List<Map<String, Object>>) parsed.getOrDefault("volumes", List.of());
        for (Map<String, Object> v : vols) {
            VolumeOutline vo = new VolumeOutline();
            vo.setProjectId(projectId);
            vo.setVolumeNo(parser.toInt(v.get("volume_no"), created + 1));
            vo.setTitle(parser.str(v.get("title")));
            vo.setGoal(parser.str(v.get("goal")));
            vo.setTurns(parser.str(v.get("turns")));
            vo.setVolumeEndHook(parser.str(v.get("volume_end_hook")));
            vo.setCharacterChanges(parser.str(v.get("character_changes")));
            vo.setChapterCount(parser.toInt(v.get("chapter_count"), 0));
            vo.setStatus("draft");
            vo.setSortOrder(created + 1);
            volumeMapper.insert(vo);
            created++;
        }
        return created;
    }

    public List<VolumeOutline> listVolumes(Long projectId) {
        return volumeMapper.selectList(new LambdaQueryWrapper<VolumeOutline>()
                .eq(VolumeOutline::getProjectId, projectId).orderByAsc(VolumeOutline::getSortOrder));
    }

    // ===== World Locations =====

    @Transactional
    public WorldLocation createLocation(Long projectId, String name, String tier, Long parentId) {
        WorldLocation wl = new WorldLocation();
        wl.setProjectId(projectId);
        wl.setName(name);
        wl.setTier(tier != null ? tier : "L0");
        wl.setParentLocationId(parentId);
        locationMapper.insert(wl);
        return wl;
    }

    @Transactional
    public int aiExtractLocations(Long projectId, String content) {
        String prompt = """
            请从以下文本提取所有地点，输出JSON：
            {"locations":[{"name":"","type":"","description":"","parent":"","tier":"L0|L1","distance_from_origin":"","transportation":"","faction_territory":"","visual_reference":""}]}
            tier: L0=AI提取的基础地点卡片, L1=区域层级关系(含距离/交通/势力范围)
            """;
        Map<String, Object> result = aiRouter.chatCompletion(Map.of(
                "system_prompt", prompt, "prompt", parser.ellipsis(content, 6000), "temperature", 0.3, "max_tokens", 2048));
        Map<String, Object> parsed = parser.parseJson(parser.extractText(result));

        int count = 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> locs = (List<Map<String, Object>>) parsed.getOrDefault("locations", List.of());
        for (Map<String, Object> l : locs) {
            WorldLocation wl = new WorldLocation();
            wl.setProjectId(projectId);
            wl.setName(parser.str(l.get("name")));
            wl.setTier(parser.str(l.get("tier")));
            if (wl.getTier().isEmpty()) wl.setTier("L0");
            wl.setDescription(parser.str(l.get("description")));
            wl.setAreaType(parser.str(l.get("type")));
            wl.setDistanceFromOrigin(parser.str(l.get("distance_from_origin")));
            wl.setTransportation(parser.str(l.get("transportation")));
            wl.setFactionTerritory(parser.str(l.get("faction_territory")));
            wl.setVisualReference(parser.str(l.get("visual_reference")));
            // Resolve parent location by name if provided
            String parentName = parser.str(l.get("parent"));
            if (!parentName.isEmpty()) {
                WorldLocation parent = locationMapper.selectOne(
                        new LambdaQueryWrapper<WorldLocation>()
                                .eq(WorldLocation::getProjectId, projectId)
                                .eq(WorldLocation::getName, parentName));
                if (parent != null) wl.setParentLocationId(parent.getId());
            }
            locationMapper.insert(wl);
            count++;
        }
        return count;
    }

    public List<WorldLocation> listLocations(Long projectId) {
        return locationMapper.selectList(new LambdaQueryWrapper<WorldLocation>()
                .eq(WorldLocation::getProjectId, projectId));
    }

    // ===== Timeline + Foreshadowing =====

    @Transactional
    public Map<String, Object> aiGenerateTimeline(Long projectId, String context) {
        // Clear old timeline and foreshadowing before regeneration
        timelineMapper.delete(new LambdaQueryWrapper<StoryTimeline>()
                .eq(StoryTimeline::getProjectId, projectId));
        foreshadowMapper.delete(new LambdaQueryWrapper<ForeshadowingItem>()
                .eq(ForeshadowingItem::getProjectId, projectId));

        String prompt = "请为以下故事建立时间线，输出JSON：{\"events\":[{\"name\":\"\",\"time\":\"\",\"description\":\"\",\"characters\":[],\"location_name\":\"\"}],\"foreshadowing\":[{\"description\":\"\",\"category\":\"\",\"planted_in\":\"\",\"payoff_in\":\"\",\"characters\":[]}]}";
        Map<String, Object> result = aiRouter.chatCompletion(Map.of(
                "system_prompt", prompt, "prompt", parser.ellipsis(context, 6000), "temperature", 0.5, "max_tokens", 4096));
        Map<String, Object> parsed = parser.parseJson(parser.extractText(result));

        int events = 0, foreshadows = 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evts = (List<Map<String, Object>>) parsed.getOrDefault("events", List.of());
        for (Map<String, Object> e : evts) {
            StoryTimeline st = new StoryTimeline();
            st.setProjectId(projectId);
            st.setEventName(parser.str(e.get("name")));
            st.setDescription(parser.str(e.get("description")));
            st.setRelativeTime(parser.str(e.get("time")));
            st.setInvolvedCharacters(parser.toJson(e.get("characters")));
            // Resolve location by name
            String locName = parser.str(e.get("location_name"));
            if (!locName.isEmpty()) {
                WorldLocation loc = locationMapper.selectOne(
                        new LambdaQueryWrapper<WorldLocation>()
                                .eq(WorldLocation::getProjectId, projectId)
                                .eq(WorldLocation::getName, locName));
                if (loc != null) st.setLocationId(loc.getId());
            }
            st.setSortOrder(events + 1);
            timelineMapper.insert(st);
            events++;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fs = (List<Map<String, Object>>) parsed.getOrDefault("foreshadowing", List.of());
        for (Map<String, Object> f : fs) {
            ForeshadowingItem fi = new ForeshadowingItem();
            fi.setProjectId(projectId);
            fi.setDescription(parser.str(f.get("description")));
            fi.setCategory(parser.str(f.get("category")));
            fi.setCharacterIds(parser.toJson(f.get("characters")));
            fi.setPlantedInUnitId(parseUnitRef(projectId, parser.str(f.get("planted_in"))));
            fi.setPayoffInUnitId(parseUnitRef(projectId, parser.str(f.get("payoff_in"))));
            fi.setStatus("planted");
            foreshadowMapper.insert(fi);
            foreshadows++;
        }

        return Map.of("events_created", events, "foreshadows_created", foreshadows);
    }

    /** Resolve a unit reference string (display_no or title) to a unit ID. */
    private Long parseUnitRef(Long projectId, String ref) {
        if (ref == null || ref.isBlank()) return null;
        try {
            int displayNo = Integer.parseInt(ref.trim());
            ContentUnit unit = unitMapper.selectOne(new LambdaQueryWrapper<ContentUnit>()
                    .eq(ContentUnit::getProjectId, projectId)
                    .eq(ContentUnit::getDisplayNo, displayNo)
                    .eq(ContentUnit::getIsDeleted, 0));
            if (unit != null) return unit.getId();
        } catch (NumberFormatException ignored) {}
        return null;
    }

    public Map<String, Object> getWorldSummary(Long projectId) {
        return Map.of(
                "characters", charMapper.selectCount(new LambdaQueryWrapper<CharacterProfile>().eq(CharacterProfile::getProjectId, projectId)),
                "plot_tasks", taskMapper.selectCount(new LambdaQueryWrapper<PlotTask>().eq(PlotTask::getProjectId, projectId)),
                "volumes", volumeMapper.selectCount(new LambdaQueryWrapper<VolumeOutline>().eq(VolumeOutline::getProjectId, projectId)),
                "locations", locationMapper.selectCount(new LambdaQueryWrapper<WorldLocation>().eq(WorldLocation::getProjectId, projectId)),
                "timeline_events", timelineMapper.selectCount(new LambdaQueryWrapper<StoryTimeline>().eq(StoryTimeline::getProjectId, projectId)),
                "foreshadowing", foreshadowMapper.selectCount(new LambdaQueryWrapper<ForeshadowingItem>().eq(ForeshadowingItem::getProjectId, projectId))
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int getNextSort(com.baomidou.mybatisplus.core.mapper.BaseMapper<?> mapper, Long projectId) {
        // Query max sort_order for this project to maintain ordering
        try {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper qw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper();
            qw.select("COALESCE(MAX(sort_order), -1) + 1 as next_sort");
            qw.eq("project_id", projectId);
            List<Map<String, Object>> result = mapper.selectMaps(qw);
            if (result != null && !result.isEmpty()) {
                Object next = result.get(0).get("next_sort");
                if (next instanceof Number n) return n.intValue();
            }
        } catch (Exception e) {
            log.warn("Failed to get next sort order, returning 0", e);
        }
        return 0;
    }
}
