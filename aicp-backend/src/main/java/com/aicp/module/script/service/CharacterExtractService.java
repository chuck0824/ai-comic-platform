package com.aicp.module.script.service;

import com.aicp.common.util.SecurityUtil;
import com.aicp.module.script.entity.*;
import com.aicp.module.script.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI 角色提取服务
 * 从剧本中自动提取角色，生成结构化卡片：
 * 名称/外貌/性格/成长弧/台词风格
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterExtractService {

    private final ScriptMapper scriptMapper;
    private final RepoAssetMapper repoAssetMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 从剧本提取角色 */
    public List<Map<String, Object>> extractCharacters(Long scriptId) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) return List.of();

        String text = script.getSynopsis();
        if (text == null || text.isBlank()) {
            text = "剧本 #" + scriptId;
        }

        // 从文本中提取角色名称（简单 NLP：匹配中文名、英文名模式）
        List<String> names = extractNames(text);
        if (names.isEmpty()) {
            names = List.of("主角", "配角A", "配角B");
        }

        // 查找已存在的角色资产
        List<RepoAsset> existing = repoAssetMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RepoAsset>()
                        .eq(RepoAsset::getAssetType, "character")
                        .eq(RepoAsset::getOwnerUserId, SecurityUtil.requireCurrentUserId()));

        List<Map<String, Object>> characters = new ArrayList<>();
        for (String name : names) {
            // 检查是否已存在
            RepoAsset existingChar = existing.stream()
                    .filter(a -> name.equals(a.getName()))
                    .findFirst().orElse(null);

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("name", name);
            card.put("asset_id", existingChar != null ? existingChar.getAssetId() : "CH_" + System.currentTimeMillis());
            card.put("is_existing", existingChar != null);

            // 生成角色描述（mock AI 输出）
            card.put("appearance", generateAppearance(name, script.getGenreTag()));
            card.put("personality", generatePersonality(name));
            card.put("growth_arc", generateGrowthArc(name));
            card.put("dialogue_style", generateDialogueStyle(name));
            card.put("role_type", classifyRole(name, names));

            characters.add(card);
        }

        return characters;
    }

    /** 保存提取的角色为仓库资产 */
    public RepoAsset saveAsAsset(Map<String, Object> characterData) {
        RepoAsset asset = new RepoAsset();
        asset.setAssetId((String) characterData.getOrDefault("asset_id", "CH_" + System.currentTimeMillis()));
        asset.setAssetType("character");
        asset.setName((String) characterData.getOrDefault("name", "未命名角色"));
        asset.setDescription(buildAssetDescription(characterData));
        asset.setOwnerUserId(SecurityUtil.requireCurrentUserId());
        asset.setMaturityLevel("L1");
        repoAssetMapper.insert(asset);
        return asset;
    }

    /** 保存所有角色到仓库 */
    public List<RepoAsset> saveAllToWarehouse(Long scriptId) {
        List<Map<String, Object>> characters = extractCharacters(scriptId);
        List<RepoAsset> saved = new ArrayList<>();
        for (Map<String, Object> c : characters) {
            try {
                saved.add(saveAsAsset(c));
            } catch (Exception e) {
                log.warn("保存角色资产失败: {}", c.get("name"), e);
            }
        }
        return saved;
    }

    // ===== 内部方法 =====

    /** 简单中文名提取 */
    private List<String> extractNames(String text) {
        // 匹配 2-4 字中文名（简化版 NLP）
        Set<String> names = new LinkedHashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "([\\u4e00-\\u9fa5]{2,4})(?:走进|说道|问道|拿出|看着|转身|离开)").matcher(text);
        while (m.find()) {
            String name = m.group(1);
            if (!name.matches(".*[的了是的不在一这那人他她它].*")
                    && name.length() >= 2) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    private String generateAppearance(String name, String genre) {
        Map<String, String[]> appearances = Map.of(
                "言情", new String[]{"清秀面容，五官精致", "高挑身材，气质出众", "温婉端庄，明眸善睐"},
                "悬疑", new String[]{"目光锐利，线条分明", "低调内敛，存在感强", "神秘气质，难以捉摸"},
                "科幻", new String[]{"未来感轮廓，冷峻面容", "机械义肢，赛博改造", "基因优化，完美比例"},
                "仙侠", new String[]{"仙风道骨，飘逸出尘", "眉宇英气，手持长剑", "灵动俏皮，衣袂飘飘"});
        String[] pool = appearances.getOrDefault(genre, new String[]{"五官端正，气质独特", "身材匀称，举止得体"});
        return pool[Math.abs(name.hashCode()) % pool.length];
    }

    private String generatePersonality(String name) {
        String[] traits = {"沉稳内敛，善于观察", "活泼开朗，行动力强", "心思缜密，城府深沉",
                "善良温柔，外柔内刚", "果断刚毅，不怒自威", "聪慧狡黠，随机应变"};
        return traits[Math.abs(name.hashCode()) % traits.length];
    }

    private String generateGrowthArc(String name) {
        String[] arcs = {"从自卑到自信，经历挫折后找到自我价值",
                "从冷漠到温暖，在伙伴帮助下学会信任",
                "从弱小到强大，一步步突破自我极限",
                "从偏执到豁达，经历重大转折后转变"};
        return arcs[Math.abs(name.hashCode()) % arcs.length];
    }

    private String generateDialogueStyle(String name) {
        String[] styles = {"简洁直接，话少但句句关键", "幽默风趣，擅长化解尴尬",
                "温文尔雅，措辞考究", "犀利尖锐，一语中的"};
        return styles[Math.abs(name.hashCode()) % styles.length];
    }

    private String classifyRole(String name, List<String> allNames) {
        if (allNames.indexOf(name) == 0) return "主角";
        if (allNames.indexOf(name) <= 2) return "重要角色";
        return "配角";
    }

    private String buildAssetDescription(Map<String, Object> data) {
        return String.format("角色: %s\n外貌: %s\n性格: %s\n成长弧: %s\n台词风格: %s\n类型: %s",
                data.get("name"), data.get("appearance"), data.get("personality"),
                data.get("growth_arc"), data.get("dialogue_style"), data.get("role_type"));
    }
}
