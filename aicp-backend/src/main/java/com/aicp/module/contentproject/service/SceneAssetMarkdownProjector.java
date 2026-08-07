package com.aicp.module.contentproject.service;

import com.aicp.module.asset.entity.AssetVersion;
import com.aicp.module.asset.entity.WorkspaceAsset;
import com.aicp.module.contentproject.dto.ProjectSceneAssetViews.SceneAssetMarkdownView;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Builds a deterministic Obsidian projection; it deliberately has no filesystem side effects. */
@Component
public class SceneAssetMarkdownProjector {

    private static final String ROOT = "04-场景资产/";
    private static final String WORLD_LOCATIONS = "03-小说分析/世界观/主要地点";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public SceneAssetMarkdownView project(Long projectId, WorkspaceAsset asset, AssetVersion version,
                                          Map<String, Object> metadata) {
        Map<String, Object> master = map(metadata.get("master"));
        List<Map<String, Object>> variants = list(metadata.get("variants"));
        String stableId = safeText(text(master.get("stable_id"), "SCENE-ASSET-%03d".formatted(asset.getId())));
        String name = safeText(asset.getName());
        String path = ROOT + stableId + "-" + filename(name) + ".md";
        String updated = asset.getUpdatedAt() == null ? "" : TIME.format(asset.getUpdatedAt());
        StringBuilder content = new StringBuilder();
        content.append("---\n")
                .append("project_id: ").append(projectId).append('\n')
                .append("asset_stable_id: \"").append(yaml(stableId)).append("\"\n")
                .append("asset_version: ").append(version.getVersionNumber()).append('\n')
                .append("status: \"").append(yaml(asset.getStatus())).append("\"\n")
                .append("space_type: \"").append(yaml(text(master.get("space_type"), ""))).append("\"\n")
                .append("reuse_level: \"").append(yaml(text(master.get("reusability"), ""))).append("\"\n")
                .append("source_location: \"").append(yaml(text(master.get("world_location_ref"), ""))).append("\"\n")
                .append("updated_at: \"").append(yaml(updated)).append("\"\n")
                .append("---\n\n")
                .append("# ").append(name).append("\n\n")
                .append("## 主场景设定\n\n");
        master.forEach((key, value) -> {
            if (!"stable_id".equals(key)) content.append("- ").append(safeText(key)).append(": ")
                    .append(render(value)).append('\n');
        });
        content.append("\n## 场景变体\n\n");
        if (variants.isEmpty()) content.append("- 无\n");
        for (Map<String, Object> variant : variants) {
            content.append("### ").append(safeText(text(variant.get("id"), "VAR"))).append(" · ")
                    .append(safeText(text(variant.get("name"), ""))).append('\n');
            variant.forEach((key, value) -> {
                if (!"id".equals(key) && !"name".equals(key)) content.append("- ").append(safeText(key))
                        .append(": ").append(render(value)).append('\n');
            });
            content.append('\n');
        }
        content.append("## 连戏规则\n\n");
        Object continuity = master.get("continuity_rules");
        if (continuity instanceof List<?> rules && !rules.isEmpty()) {
            rules.forEach(rule -> content.append("- ").append(render(rule)).append('\n'));
        } else content.append("- 无\n");
        content.append("\n## 引用\n\n")
                .append("- 来源地点：[[").append(WORLD_LOCATIONS).append("]]\n")
                .append("- 场景资产目录：[[04-场景资产]]\n");
        appendReferences(content, master.get("references"));
        variants.forEach(variant -> appendReferences(content, variant.get("references")));
        return new SceneAssetMarkdownView(path, content.toString());
    }

    private void appendReferences(StringBuilder content, Object references) {
        if (references instanceof List<?> items) {
            items.forEach(item -> content.append("- ").append(render(item)).append('\n'));
        } else if (references != null) content.append("- ").append(render(references)).append('\n');
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }

    private String render(Object value) {
        if (value instanceof List<?> values) return values.stream().map(this::render).reduce((a, b) -> a + ", " + b).orElse("");
        if (value instanceof Map<?, ?> values) return values.entrySet().stream()
                .map(entry -> safeText(String.valueOf(entry.getKey())) + "=" + render(entry.getValue()))
                .reduce((a, b) -> a + ", " + b).orElse("");
        return safeText(String.valueOf(value));
    }

    private String filename(String value) {
        String normalized = safeText(value).replaceAll("[\\r\\n]+", " ").trim();
        return normalized.isEmpty() ? "unnamed-scene" : normalized;
    }

    private String yaml(String value) {
        return safeText(value).replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    private String text(Object value, String fallback) {
        return value instanceof String string ? string : fallback;
    }

    private String safeText(String value) {
        if (value == null) return "";
        return value.replace("..", "·").replace("/", "／").replace("\\", "＼")
                .replace("#", "＃").replace("[", "［").replace("]", "］");
    }
}
