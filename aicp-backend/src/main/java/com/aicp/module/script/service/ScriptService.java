package com.aicp.module.script.service;

import com.aicp.common.util.SecurityUtil;
import com.aicp.module.script.entity.*;
import com.aicp.module.script.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptService {

    private final ScriptMapper scriptMapper;
    private final ScriptEpisodeMapper episodeMapper;
    private final ScriptVersionMapper versionMapper;
    private final RepoAssetMapper repoAssetMapper;
    private final ChapterVersionMapper chapterVersionMapper;
    private final AdaptationVersionMapper adaptationVersionMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ===== Script CRUD =====
    @Transactional
    public Script createScript(Map<String, Object> body) {
        Long userId = SecurityUtil.requireCurrentUserId();
        Script script = new Script();
        script.setUuid("scr_" + UUID.randomUUID().toString().replace("-", ""));
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
        Script script = scriptMapper.selectById(id);
        if (script != null) {
            verifyAccess(script);
        }
        return script;
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

    @Transactional
    public void restoreVersion(Long scriptId, Long versionId) {
        ScriptVersion version = versionMapper.selectById(versionId);
        if (version == null || !version.getScriptId().equals(scriptId)) {
            throw new RuntimeException("版本不存在或不属于该剧本");
        }
        // 更新剧本的 current_version 字段
        Script script = scriptMapper.selectById(scriptId);
        if (script != null) {
            script.setCurrentVersion(version.getVersion());
            scriptMapper.updateById(script);
        }
        log.info("剧本版本已恢复: scriptId={}, versionId={}, version={}", scriptId, versionId, version.getVersion());
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

    // ===== Chapter text versions =====
    public List<ScriptEpisode> getChapters(Long scriptId) {
        return episodeMapper.selectList(new LambdaQueryWrapper<ScriptEpisode>()
                .eq(ScriptEpisode::getScriptId, scriptId)
                .orderByAsc(ScriptEpisode::getEpisodeNumber));
    }

    @Transactional
    public ScriptEpisode updateChapter(Long chapterId, Map<String, Object> body) {
        ScriptEpisode episode = episodeMapper.selectById(chapterId);
        if (episode == null) return null;
        if (body.containsKey("title")) episode.setTitle(String.valueOf(body.get("title")));
        if (body.containsKey("content")) {
            String content = String.valueOf(body.getOrDefault("content", ""));
            episode.setContent(content);
            episode.setWordCount(content.length());
        }
        if (body.containsKey("opening_hook")) episode.setOpeningHook(String.valueOf(body.get("opening_hook")));
        if (body.containsKey("closing_hook")) episode.setClosingHook(String.valueOf(body.get("closing_hook")));
        if (body.containsKey("status")) episode.setStatus(String.valueOf(body.get("status")));
        episodeMapper.updateById(episode);

        if (Boolean.TRUE.equals(body.get("create_version"))) {
            createChapterVersion(episode.getScriptId(), chapterId, body);
        }
        return episode;
    }

    public List<ChapterVersion> getChapterVersions(Long chapterId) {
        return chapterVersionMapper.selectList(new LambdaQueryWrapper<ChapterVersion>()
                .eq(ChapterVersion::getEpisodeId, chapterId)
                .orderByDesc(ChapterVersion::getCreatedAt));
    }

    public ChapterVersion createChapterVersion(Long scriptId, Long chapterId, Map<String, Object> body) {
        // 处理无效 chapterId（0 或 null）：通过 script_id + chapter_number 查找实际 episode
        ScriptEpisode episode = (chapterId != null && chapterId > 0) ? episodeMapper.selectById(chapterId) : null;
        Long bodyScriptId = toLong(body.get("script_id"));
        Long resolvedScriptId = scriptId != null ? scriptId : bodyScriptId;

        // 如果通过 ID 找不到 episode，尝试通过 script_id + chapter_number 定位
        if (episode == null && resolvedScriptId != null && body.containsKey("chapter_number")) {
            int chapterNum = toInt(body.get("chapter_number"), 0);
            if (chapterNum > 0) {
                episode = episodeMapper.selectOne(new LambdaQueryWrapper<ScriptEpisode>()
                        .eq(ScriptEpisode::getScriptId, resolvedScriptId)
                        .eq(ScriptEpisode::getEpisodeNumber, chapterNum));
            }
        }

        ChapterVersion version = new ChapterVersion();
        version.setScriptId(resolvedScriptId != null ? resolvedScriptId : (episode != null ? episode.getScriptId() : null));
        version.setEpisodeId(episode != null ? episode.getId() : (chapterId != null && chapterId > 0 ? chapterId : null));
        version.setChapterNumber(toInt(body.get("chapter_number"), episode == null ? 1 : episode.getEpisodeNumber()));
        version.setTitle(String.valueOf(body.getOrDefault("title", episode == null ? "未命名章节" : episode.getTitle())));
        version.setContent(String.valueOf(body.getOrDefault("content", episode == null ? "" : episode.getContent())));
        version.setContentFormat(String.valueOf(body.getOrDefault("content_format", "novel")));
        version.setVersionNo(String.valueOf(body.getOrDefault("version_no", "v" + System.currentTimeMillis())));
        version.setChangeSummary(String.valueOf(body.getOrDefault("change_summary", "保存单章正文版本")));
        version.setSource(String.valueOf(body.getOrDefault("source", "manual_edit")));
        version.setCreatedBy(SecurityUtil.requireCurrentUserId());
        chapterVersionMapper.insert(version);
        return version;
    }

    // ===== Adaptation versions =====
    public List<AdaptationVersion> getAdaptations(Long scriptId, Long chapterVersionId, String targetType) {
        LambdaQueryWrapper<AdaptationVersion> query = new LambdaQueryWrapper<>();
        if (scriptId != null) query.eq(AdaptationVersion::getScriptId, scriptId);
        if (chapterVersionId != null) query.eq(AdaptationVersion::getSourceChapterVersionId, chapterVersionId);
        if (targetType != null && !targetType.isBlank()) query.eq(AdaptationVersion::getTargetType, targetType);
        query.orderByDesc(AdaptationVersion::getCreatedAt);
        return adaptationVersionMapper.selectList(query);
    }

    public AdaptationVersion getAdaptation(Long id) {
        AdaptationVersion adaptation = adaptationVersionMapper.selectById(id);
        if (adaptation != null && adaptation.getScriptId() != null) {
            Script script = scriptMapper.selectById(adaptation.getScriptId());
            if (script != null) {
                verifyAccess(script);
            }
        }
        return adaptation;
    }

    public AdaptationVersion createAdaptation(Map<String, Object> body) {
        AdaptationVersion version = new AdaptationVersion();
        version.setScriptId(toLong(body.get("script_id")));
        version.setSourceChapterVersionId(toLong(body.get("source_chapter_version_id")));
        version.setSourceProjectVersionId(toLong(body.get("source_project_version_id")));
        version.setTargetType(String.valueOf(body.getOrDefault("target_type", "ai_comic")));
        version.setVersionNo(String.valueOf(body.getOrDefault("version_no", "v" + System.currentTimeMillis())));
        version.setTitle(String.valueOf(body.getOrDefault("title", defaultAdaptationTitle(version.getTargetType()))));
        version.setContent(String.valueOf(body.getOrDefault("content", buildAdaptationContent(body, version.getTargetType()))));
        version.setHookStrategyJson(toJson(body.getOrDefault("hook_strategy", Map.of(
                "inherit_source_hook", true,
                "opening_hook", body.getOrDefault("opening_hook", ""),
                "closing_hook", body.getOrDefault("closing_hook", "")
        ))));
        version.setStatus(String.valueOf(body.getOrDefault("status", "draft")));
        version.setCreatedBy(SecurityUtil.requireCurrentUserId());
        adaptationVersionMapper.insert(version);
        return version;
    }

    public AdaptationVersion updateAdaptation(Long id, Map<String, Object> body) {
        AdaptationVersion version = adaptationVersionMapper.selectById(id);
        if (version == null) return null;
        if (body.containsKey("title")) version.setTitle(String.valueOf(body.get("title")));
        if (body.containsKey("content")) version.setContent(String.valueOf(body.get("content")));
        if (body.containsKey("hook_strategy")) version.setHookStrategyJson(toJson(body.get("hook_strategy")));
        if (body.containsKey("status")) version.setStatus(String.valueOf(body.get("status")));
        adaptationVersionMapper.updateById(version);
        return version;
    }

    public AdaptationVersion lockAdaptation(Long id) {
        AdaptationVersion version = adaptationVersionMapper.selectById(id);
        if (version == null) return null;
        version.setStatus("locked");
        adaptationVersionMapper.updateById(version);
        return version;
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

    // ===== Asset maturity & lock =====
    public void updateMaturity(String type, String assetId, String maturityLevel) {
        RepoAsset asset = repoAssetMapper.selectOne(new LambdaQueryWrapper<RepoAsset>()
                .eq(RepoAsset::getAssetType, type)
                .eq(RepoAsset::getAssetId, assetId));
        if (asset != null) {
            asset.setMaturityLevel(maturityLevel);
            repoAssetMapper.updateById(asset);
        }
    }

    public void lockAsset(String type, String assetId) {
        RepoAsset asset = repoAssetMapper.selectOne(new LambdaQueryWrapper<RepoAsset>()
                .eq(RepoAsset::getAssetType, type)
                .eq(RepoAsset::getAssetId, assetId));
        if (asset != null) {
            asset.setMaturityLevel("locked");
            repoAssetMapper.updateById(asset);
        }
    }

    // ===== Utility =====
    private String toJson(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
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

    private Long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return value == null || String.valueOf(value).isBlank() ? null : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException e) { return null; }
    }

    private String asString(Object primary, Object fallback) {
        Object value = primary != null ? primary : fallback;
        return value == null ? null : String.valueOf(value);
    }

    private String defaultAdaptationTitle(String targetType) {
        return switch (targetType == null ? "" : targetType) {
            case "short_drama" -> "短剧改编脚本";
            case "web_drama" -> "网剧改编脚本";
            case "tvc" -> "TVC改编脚本";
            default -> "AI漫剧改编脚本";
        };
    }

    private String buildAdaptationContent(Map<String, Object> body, String targetType) {
        String source = String.valueOf(body.getOrDefault("source_text", body.getOrDefault("content", "")));
        String label = defaultAdaptationTitle(targetType);
        return """
                # %s

                ## 来源文本摘要
                %s

                ## 改编结构
                - 开场钩子：继承源头文本的强冲突或强情绪点。
                - 主体推进：按目标媒介拆成场次、对白、动作和转场。
                - 结尾留白：保留下一集/下一镜头/转化动作。
                """.formatted(label, source.isBlank() ? "待补充源头文本。" : source);
    }

    /**
     * 校验当前用户是否有权访问该剧本（作者或拥有者均可访问）。
     * 管理员权限由上层 SecurityUtil / 拦截器统一处理。
     */
    private void verifyAccess(Script script) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new RuntimeException("未登录");
        }
        boolean isAuthor = currentUserId.equals(script.getAuthorUserId());
        boolean isOwner = currentUserId.equals(script.getOwnerUserId());
        if (!isAuthor && !isOwner) {
            throw new RuntimeException("无权访问该剧本");
        }
    }
}
