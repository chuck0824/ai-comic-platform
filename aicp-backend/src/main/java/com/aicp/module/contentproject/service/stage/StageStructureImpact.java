package com.aicp.module.contentproject.service.stage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * R2-A §7.2：比较旧/新采用版本的内容哈希与结构指纹，决定下游过期升级。
 */
public final class StageStructureImpact {

    public enum Level {
        NONE,
        TEXT_ONLY,
        STRUCTURE
    }

    public record Fingerprint(
            String contentHash,
            String structuralHash,
            int sceneCount,
            int beatCount,
            int characterCount,
            int episodeCount
    ) {}

    public record Delta(Level level, Fingerprint before, Fingerprint after) {}

    private final ObjectMapper objectMapper;

    public StageStructureImpact(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Fingerprint fingerprint(String contentJson, String contentHash) {
        Map<String, Object> root = parse(contentJson);
        List<String> structuralTokens = new ArrayList<>();
        int scenes = 0;
        int beats = 0;
        int characters = 0;
        int episodes = 0;

        List<Map<String, Object>> episodeList = asObjectList(root.get("episodes"));
        episodes = episodeList.size();
        for (Map<String, Object> episode : episodeList) {
            structuralTokens.add("ep:" + String.valueOf(episode.get("id")));
            for (Map<String, Object> beat : asObjectList(episode.get("beats"))) {
                beats++;
                structuralTokens.add("beat:" + String.valueOf(beat.get("id")));
            }
            for (Map<String, Object> scene : asObjectList(episode.get("scenes"))) {
                scenes++;
                structuralTokens.add("scene:" + String.valueOf(scene.get("id")));
                Object binding = scene.get("assetBinding");
                if (binding instanceof Map<?, ?> b) {
                    structuralTokens.add("bind:" + b.get("sceneAssetId") + ":" + b.get("sceneAssetVersionId"));
                }
                for (Map<String, Object> beat : asObjectList(scene.get("beats"))) {
                    beats++;
                    structuralTokens.add("sbeat:" + String.valueOf(beat.get("id")));
                }
                // 正文块仅计入数量，文本变化不算结构
                structuralTokens.add("blocks:" + asObjectList(scene.get("blocks")).size());
            }
        }
        for (Map<String, Object> scene : asObjectList(root.get("scenes"))) {
            scenes++;
            structuralTokens.add("topScene:" + String.valueOf(scene.get("id")));
        }
        for (Map<String, Object> shot : asObjectList(root.get("shots"))) {
            scenes++;
            structuralTokens.add("shot:" + String.valueOf(shot.get("id")) + ":" + shot.get("sceneId"));
        }
        for (Map<String, Object> character : asObjectList(root.get("characters"))) {
            characters++;
            structuralTokens.add("char:" + String.valueOf(first(character, "id", "name")));
        }
        Object events = root.get("events");
        if (events instanceof List<?> list) {
            for (Object event : list) {
                if (event instanceof Map<?, ?> m) {
                    structuralTokens.add("event:" + m.get("id") + ":" + m.get("title"));
                }
            }
        }
        Collections.sort(structuralTokens);
        String structuralHash = sha256(String.join("|", structuralTokens));
        String hash = contentHash != null && !contentHash.isBlank()
                ? contentHash
                : sha256(contentJson == null ? "" : contentJson);
        return new Fingerprint(hash, structuralHash, scenes, beats, characters, episodes);
    }

    public Delta compare(String oldJson, String oldHash, String newJson, String newHash) {
        Fingerprint before = fingerprint(oldJson, oldHash);
        Fingerprint after = fingerprint(newJson, newHash);
        if (Objects.equals(before.contentHash(), after.contentHash())
                && Objects.equals(before.structuralHash(), after.structuralHash())) {
            return new Delta(Level.NONE, before, after);
        }
        if (!Objects.equals(before.structuralHash(), after.structuralHash())) {
            return new Delta(Level.STRUCTURE, before, after);
        }
        return new Delta(Level.TEXT_ONLY, before, after);
    }

    /** 规范化 JSON（键排序）后算 SHA-256，供 input_snapshot_hash 使用。 */
    public String canonicalSha256(Object value) {
        try {
            Object normalized = normalize(value);
            return sha256(objectMapper.writeValueAsString(normalized));
        } catch (Exception e) {
            return sha256(String.valueOf(value));
        }
    }

    @SuppressWarnings("unchecked")
    private Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    sorted.put(String.valueOf(entry.getKey()), normalize(entry.getValue()));
                }
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(normalize(item));
            }
            return out;
        }
        return value;
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("_raw", json);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asObjectList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }

    private Object first(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input == null ? 0 : input.hashCode());
        }
    }
}
