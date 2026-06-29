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
    private final AiRouter aiRouter;
    private final ObjectMapper objectMapper;

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
        String text = extractText(result);
        Map<String, Object> parsed = parseJson(text);

        CharacterProfile cp = new CharacterProfile();
        cp.setProjectId(projectId);
        cp.setName((String) parsed.getOrDefault("name", name));
        cp.setRole(str(parsed.get("role")));
        cp.setArchetype(str(parsed.get("archetype")));
        cp.setAppearance(str(parsed.get("appearance")));
        cp.setPersonality(str(parsed.get("personality")));
        cp.setMotivation(str(parsed.get("motivation")));
        cp.setLongTermGoal(str(parsed.get("long_term_goal")));
        cp.setKnowledgeBoundary(str(parsed.get("knowledge_boundary")));
        cp.setDialogueStyle(str(parsed.get("dialogue_style")));
        cp.setBackstory(str(parsed.get("backstory")));
        cp.setRelationshipsJson(toJson(parsed.get("relationships")));
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
             "stage_tasks":[{"title":"","goal":"","obstacle":"","character":"","type":"main|sub|character"}],
             "subplots":[{"title":"","description":"","characters":[]}]}
            """;
        Map<String, Object> result = aiRouter.chatCompletion(Map.of(
                "system_prompt", prompt, "prompt", synopsisContext, "temperature", 0.7, "max_tokens", 4096));
        String text = extractText(result);
        Map<String, Object> parsed = parseJson(text);

        int count = 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) parsed.getOrDefault("stage_tasks", List.of());
        for (Map<String, Object> t : tasks) {
            PlotTask pt = new PlotTask();
            pt.setProjectId(projectId);
            pt.setTaskType("stage");
            pt.setTitle(str(t.get("title")));
            pt.setDescription(str(t.get("goal")));
            pt.setStageGoals(toJson(t.get("goals")));
            pt.setObstacles(str(t.get("obstacle")));
            pt.setCharacterIds(str(t.get("character")));
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
        String prompt = "请为以下故事生成" + count + "卷大纲。每卷包含目标、转折、卷末钩子和角色变化。输出JSON：{\"volumes\":[...]}";
        Map<String, Object> result = aiRouter.chatCompletion(Map.of(
                "system_prompt", "你是资深故事架构师。", "prompt", prompt + "\n" + context, "temperature", 0.7, "max_tokens", 4096));
        Map<String, Object> parsed = parseJson(extractText(result));

        int created = 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vols = (List<Map<String, Object>>) parsed.getOrDefault("volumes", List.of());
        for (Map<String, Object> v : vols) {
            VolumeOutline vo = new VolumeOutline();
            vo.setProjectId(projectId);
            vo.setVolumeNo(toInt(v.get("volume_no"), created + 1));
            vo.setTitle(str(v.get("title")));
            vo.setGoal(str(v.get("goal")));
            vo.setTurns(str(v.get("turns")));
            vo.setVolumeEndHook(str(v.get("volume_end_hook")));
            vo.setCharacterChanges(str(v.get("character_changes")));
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
            请从以下文本提取所有地点，输出JSON：{"locations":[{"name":"","type":"","description":"","parent":""}]}
            """;
        Map<String, Object> result = aiRouter.chatCompletion(Map.of(
                "system_prompt", prompt, "prompt", ellipsis(content, 6000), "temperature", 0.3, "max_tokens", 2048));
        Map<String, Object> parsed = parseJson(extractText(result));

        int count = 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> locs = (List<Map<String, Object>>) parsed.getOrDefault("locations", List.of());
        for (Map<String, Object> l : locs) {
            WorldLocation wl = new WorldLocation();
            wl.setProjectId(projectId);
            wl.setName(str(l.get("name")));
            wl.setTier("L0");
            wl.setDescription(str(l.get("description")));
            wl.setAreaType(str(l.get("type")));
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
        String prompt = "请为以下故事建立时间线，输出JSON：{\"events\":[{\"name\":\"\",\"time\":\"\",\"description\":\"\",\"characters\":[]}],\"foreshadowing\":[{\"description\":\"\",\"category\":\"\",\"planted_in\":\"\"}]}";
        Map<String, Object> result = aiRouter.chatCompletion(Map.of(
                "system_prompt", prompt, "prompt", ellipsis(context, 6000), "temperature", 0.5, "max_tokens", 4096));
        Map<String, Object> parsed = parseJson(extractText(result));

        int events = 0, foreshadows = 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evts = (List<Map<String, Object>>) parsed.getOrDefault("events", List.of());
        for (Map<String, Object> e : evts) {
            StoryTimeline st = new StoryTimeline();
            st.setProjectId(projectId);
            st.setEventName(str(e.get("name")));
            st.setDescription(str(e.get("description")));
            st.setRelativeTime(str(e.get("time")));
            st.setInvolvedCharacters(toJson(e.get("characters")));
            st.setSortOrder(events + 1);
            timelineMapper.insert(st);
            events++;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fs = (List<Map<String, Object>>) parsed.getOrDefault("foreshadowing", List.of());
        for (Map<String, Object> f : fs) {
            ForeshadowingItem fi = new ForeshadowingItem();
            fi.setProjectId(projectId);
            fi.setDescription(str(f.get("description")));
            fi.setCategory(str(f.get("category")));
            fi.setStatus("planted");
            foreshadowMapper.insert(fi);
            foreshadows++;
        }

        return Map.of("events_created", events, "foreshadows_created", foreshadows);
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

    // ===== Helpers =====
    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> r) {
        Object choices = r.get("choices");
        if (choices instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map m) {
            Object msg = m.get("message");
            if (msg instanceof Map mm) { Object c = mm.get("content"); if (c != null) return String.valueOf(c); }
        }
        return r.toString();
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String text) {
        try { String j = text; if (text.contains("```json")) { int s=text.indexOf("```json")+7,e=text.indexOf("```",s); if(e>s)j=text.substring(s,e).trim(); }
            return objectMapper.readValue(j, new TypeReference<Map<String,Object>>() {}); } catch(Exception e) { return Map.of(); }
    }
    private String str(Object v) { return v!=null?String.valueOf(v):""; }
    private int toInt(Object v, int d) { if(v instanceof Number n) return n.intValue(); return d; }
    private String toJson(Object v) { try{return objectMapper.writeValueAsString(v);}catch(Exception e){return"[]";} }
    private String ellipsis(String s, int m) { return s!=null&&s.length()>m?s.substring(0,m)+"...":s; }
    private int getNextSort(com.baomidou.mybatisplus.core.mapper.BaseMapper<?> mapper, Long projectId) {
        return 0; // simplified
    }
}
