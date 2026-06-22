package com.aicp.module.script.service;

import com.aicp.common.util.SecurityUtil;
import com.aicp.module.script.entity.*;
import com.aicp.module.script.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScriptService {

    private final ScriptMapper scriptMapper;
    private final ScriptEpisodeMapper episodeMapper;
    private final ScriptVersionMapper versionMapper;
    private final RepoAssetMapper repoAssetMapper;

    // ===== Script CRUD =====
    @Transactional
    public Script createScript(Map<String, Object> body) {
        Long userId = SecurityUtil.requireCurrentUserId();
        Script script = new Script();
        script.setUuid("scr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        script.setTitle((String) body.getOrDefault("title", "未命名剧本"));
        script.setAuthorUserId(userId);
        script.setOwnerUserId(userId);
        script.setEpisodeCount(toInt(body.get("episode_count"), 0));
        script.setSynopsis((String) body.get("synopsis"));
        script.setGenreTag((String) body.get("genre_tag"));
        script.setPlotTags(toJson(body.get("plot_tags")));
        script.setToneTags(toJson(body.get("tone_tags")));
        script.setSettingTag((String) body.get("setting_tag"));
        script.setSource((String) body.getOrDefault("source", "ai_generated"));
        script.setStatus((String) body.getOrDefault("status", "draft"));
        script.setCurrentVersion("v0.1");
        scriptMapper.insert(script);

        List<Map<String, Object>> episodes = normalizeEpisodes(body);
        if (!episodes.isEmpty()) {
            script.setEpisodeCount(episodes.size());
            script.setTotalWords(episodes.stream()
                    .mapToInt(ep -> String.valueOf(ep.getOrDefault("content", "")).length())
                    .sum());
            scriptMapper.updateById(script);
            saveEpisodes(script.getId(), episodes);
        }
        saveInitialVersion(script.getId(), body);
        return script;
    }

    public Page<Script> getScripts(int page, int pageSize, String status, String genre, String keyword) {
        Long userId = SecurityUtil.requireCurrentUserId();
        LambdaQueryWrapper<Script> query = new LambdaQueryWrapper<>();
        query.eq(Script::getOwnerUserId, userId);
        if (status != null) query.eq(Script::getStatus, status);
        if (genre != null) query.eq(Script::getGenreTag, genre);
        if (keyword != null) query.like(Script::getTitle, keyword);
        query.orderByDesc(Script::getUpdatedAt);
        return scriptMapper.selectPage(new Page<>(page, pageSize), query);
    }

    public Script getScript(Long id) {
        return scriptMapper.selectById(id);
    }

    public Script updateScript(Long id, Map<String, Object> body) {
        Script script = scriptMapper.selectById(id);
        if (script == null) return null;
        if (body.containsKey("title")) script.setTitle((String) body.get("title"));
        if (body.containsKey("synopsis")) script.setSynopsis((String) body.get("synopsis"));
        if (body.containsKey("episode_count")) script.setEpisodeCount((Integer) body.get("episode_count"));
        if (body.containsKey("total_words")) script.setTotalWords((Integer) body.get("total_words"));
        if (body.containsKey("status")) script.setStatus((String) body.get("status"));
        if (body.containsKey("genre_tag")) script.setGenreTag((String) body.get("genre_tag"));
        if (body.containsKey("plot_tags")) script.setPlotTags(toJson(body.get("plot_tags")));
        if (body.containsKey("tone_tags")) script.setToneTags(toJson(body.get("tone_tags")));
        if (body.containsKey("setting_tag")) script.setSettingTag((String) body.get("setting_tag"));
        scriptMapper.updateById(script);
        return script;
    }

    public void deleteScript(Long id) {
        scriptMapper.deleteById(id); // MyBatis-Plus 软删除
    }

    // ===== Tags =====
    public void updateTags(Long id, Map<String, Object> body) {
        Script script = scriptMapper.selectById(id);
        if (script == null) return;
        if (body.containsKey("genre")) script.setGenreTag((String) body.get("genre"));
        if (body.containsKey("plot")) script.setPlotTags(toJson(body.get("plot")));
        if (body.containsKey("tone")) script.setToneTags(toJson(body.get("tone")));
        if (body.containsKey("setting")) script.setSettingTag((String) body.get("setting"));
        scriptMapper.updateById(script);
    }

    public void updateStatus(Long id, String status) {
        Script script = scriptMapper.selectById(id);
        if (script != null) {
            script.setStatus(status);
            scriptMapper.updateById(script);
        }
    }

    // ===== Versions =====
    public List<ScriptVersion> getVersions(Long scriptId) {
        return versionMapper.selectList(
                new LambdaQueryWrapper<ScriptVersion>()
                        .eq(ScriptVersion::getScriptId, scriptId)
                        .orderByDesc(ScriptVersion::getCreatedAt));
    }

    public void createVersion(Long scriptId, Map<String, Object> body) {
        ScriptVersion version = new ScriptVersion();
        version.setScriptId(scriptId);
        version.setVersion((String) body.get("version"));
        version.setContent((String) body.get("content"));
        version.setChangeSummary((String) body.get("change_summary"));
        version.setCreatedBy(SecurityUtil.requireCurrentUserId());
        versionMapper.insert(version);
    }

    // ===== Assets =====
    public List<RepoAsset> getAssets(String type, String maturity) {
        LambdaQueryWrapper<RepoAsset> query = new LambdaQueryWrapper<>();
        if (type != null) query.eq(RepoAsset::getAssetType, type);
        if (maturity != null) query.eq(RepoAsset::getMaturityLevel, maturity);
        return repoAssetMapper.selectList(query);
    }

    public RepoAsset createCharacter(Map<String, Object> body) {
        Long userId = SecurityUtil.requireCurrentUserId();
        RepoAsset asset = new RepoAsset();
        asset.setAssetId("CH_" + System.currentTimeMillis());
        asset.setAssetType("character");
        asset.setName((String) body.getOrDefault("name", "未命名角色"));
        asset.setDescription((String) body.get("description"));
        asset.setOwnerUserId(userId);
        asset.setMaturityLevel("L0");
        repoAssetMapper.insert(asset);
        return asset;
    }

    public RepoAsset createScene(Map<String, Object> body) {
        Long userId = SecurityUtil.requireCurrentUserId();
        RepoAsset asset = new RepoAsset();
        asset.setAssetId("LOC_" + System.currentTimeMillis());
        asset.setAssetType("scene");
        asset.setName((String) body.getOrDefault("name", "未命名场景"));
        asset.setDescription((String) body.get("description"));
        asset.setOwnerUserId(userId);
        asset.setMaturityLevel("L0");
        repoAssetMapper.insert(asset);
        return asset;
    }

    // ===== Utility =====
    private String toJson(Object value) {
        if (value == null) return null;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value); }
        catch (Exception e) { return String.valueOf(value); }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeEpisodes(Map<String, Object> body) {
        Object episodesObj = body.get("episodes");
        if (episodesObj instanceof List<?> list) {
            List<Map<String, Object>> episodes = new ArrayList<>();
            int index = 1;
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) {
                    Map<String, Object> ep = new LinkedHashMap<>();
                    raw.forEach((key, value) -> ep.put(String.valueOf(key), value));
                    ep.putIfAbsent("number", index);
                    episodes.add(ep);
                    index++;
                } else if (item != null) {
                    episodes.add(Map.of("number", index++, "title", "第" + (index - 1) + "集",
                            "content", String.valueOf(item)));
                }
            }
            return episodes;
        }

        Object contentObj = body.get("content");
        if (contentObj == null) contentObj = body.get("script_text");
        if (contentObj != null && !String.valueOf(contentObj).isBlank()) {
            return List.of(Map.of("number", 1, "title", "第1集", "content", String.valueOf(contentObj)));
        }
        return List.of();
    }

    private void saveEpisodes(Long scriptId, List<Map<String, Object>> episodes) {
        int index = 1;
        for (Map<String, Object> epData : episodes) {
            String content = String.valueOf(epData.getOrDefault("content", ""));
            ScriptEpisode episode = new ScriptEpisode();
            episode.setScriptId(scriptId);
            episode.setEpisodeNumber(toInt(epData.get("number"), index));
            episode.setTitle(String.valueOf(epData.getOrDefault("title", "第" + index + "集")));
            episode.setContent(content);
            episode.setWordCount(content.length());
            episode.setOpeningHook(asString(epData.get("opening_hook"), epData.get("openingHook")));
            episode.setClosingHook(asString(epData.get("closing_hook"), epData.get("closingHook")));
            episode.setStatus(String.valueOf(epData.getOrDefault("status", "draft")));
            episodeMapper.insert(episode);
            index++;
        }
    }

    private void saveInitialVersion(Long scriptId, Map<String, Object> body) {
        ScriptVersion version = new ScriptVersion();
        version.setScriptId(scriptId);
        version.setVersion(String.valueOf(body.getOrDefault("version", "v0.1")));
        version.setContent(toJson(body));
        version.setChangeSummary(String.valueOf(body.getOrDefault("change_summary", "创建剧本资产快照")));
        version.setCreatedBy(SecurityUtil.requireCurrentUserId());
        versionMapper.insert(version);
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { return fallback; }
    }

    private String asString(Object primary, Object fallback) {
        Object value = primary != null ? primary : fallback;
        return value == null ? null : String.valueOf(value);
    }
}
