package com.aicp.common.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

@Component
public class AiModelRegistry {

    private final List<Map<String, Object>> models;

    public AiModelRegistry() {
        this.models = loadModels();
    }

    public List<Map<String, Object>> listModels(String nodeType, String agentType) {
        List<Map<String, Object>> available = models.stream()
                .filter(model -> supportsTextAgent(model, nodeType, agentType))
                .toList();
        return available.isEmpty() ? models : available;
    }

    public Map<String, Object> findModel(String modelId) {
        return models.stream()
                .filter(model -> Objects.equals(model.get("model_id"), modelId))
                .findFirst()
                .orElseGet(() -> models.isEmpty() ? fallbackModel() : models.get(0));
    }

    public Map<String, Object> estimateUsage(Map<String, Object> model, String instruction, String currentContent) {
        int inputTokens = estimateTokens((instruction == null ? "" : instruction) + "\n" + (currentContent == null ? "" : currentContent));
        int outputTokens = Math.max(180, Math.min(1800, estimateTokens(currentContent) + 240));
        double inputPrice = toDouble(model.get("input_token_price"), 0.001);
        double outputPrice = toDouble(model.get("output_token_price"), inputPrice);
        double estimatedCost = inputTokens * inputPrice / 1000.0 + outputTokens * outputPrice / 1000.0;
        return Map.of(
                "input_tokens_estimated", inputTokens,
                "output_tokens_estimated", outputTokens,
                "estimated_cost", Math.round(estimatedCost * 10000.0) / 10000.0,
                "estimated_credits", Math.max(1, (int) Math.ceil(estimatedCost * 100)),
                "billing_mode", "token");
    }

    private boolean supportsTextAgent(Map<String, Object> model, String nodeType, String agentType) {
        if (nodeType != null && !nodeType.isBlank() && !"text".equals(nodeType)) return false;
        if (agentType != null && !agentType.isBlank() && !"text_agent".equals(agentType)) return false;
        Object capabilities = model.get("capabilities");
        return capabilities instanceof List<?> list && list.contains("text");
    }

    private List<Map<String, Object>> loadModels() {
        try (InputStream input = new ClassPathResource("ai-models.yml").getInputStream()) {
            Map<String, Object> root = new Yaml().load(input);
            Object modelsRoot = root.get("models");
            if (!(modelsRoot instanceof Map<?, ?> groups)) return List.of(fallbackModel());

            List<Map<String, Object>> result = new ArrayList<>();
            Object textModels = groups.get("text");
            if (textModels instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> raw) {
                        result.add(normalizeTextModel(raw));
                    }
                }
            }
            return result.isEmpty() ? List.of(fallbackModel()) : result;
        } catch (Exception e) {
            return List.of(fallbackModel());
        }
    }

    private Map<String, Object> normalizeTextModel(Map<?, ?> raw) {
        String id = stringValue(raw.get("id"), "deepseek-v3");
        double price = toDouble(raw.get("cost_per_1k_tokens"), 0.001);
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("model_id", id);
        model.put("model_name", displayName(id));
        model.put("description", descriptionFor(id));
        model.put("provider", stringValue(raw.get("provider"), "new-api"));
        model.put("capabilities", capabilitiesFor(id));
        model.put("estimated_latency", latencyFor(id));
        model.put("context_window", intValue(raw.get("max_tokens"), 128000));
        model.put("input_token_price", price);
        model.put("output_token_price", price);
        model.put("status", "available");
        model.put("priority", intValue(raw.get("priority"), 999));
        return model;
    }

    private Map<String, Object> fallbackModel() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("model_id", "deepseek-v3");
        model.put("model_name", "DeepSeek V3");
        model.put("description", "文本模型");
        model.put("provider", "deepseek");
        model.put("capabilities", List.of("text", "json_output", "long_context"));
        model.put("estimated_latency", "15s");
        model.put("context_window", 128000);
        model.put("input_token_price", 0.001);
        model.put("output_token_price", 0.001);
        model.put("status", "available");
        model.put("priority", 1);
        return model;
    }

    private List<String> capabilitiesFor(String id) {
        List<String> caps = new ArrayList<>(List.of("text", "json_output"));
        if (id.contains("claude") || id.contains("deepseek")) caps.add("long_context");
        return caps;
    }

    private String descriptionFor(String id) {
        if (id.contains("claude")) return "长上下文文本模型";
        if (id.contains("gpt")) return "通用文本模型";
        if (id.contains("deepseek")) return "快速文本模型";
        return "文本模型";
    }

    private String latencyFor(String id) {
        if (id.contains("claude")) return "20s";
        if (id.contains("gpt")) return "15s";
        return "10s";
    }

    private String displayName(String id) {
        return switch (id) {
            case "deepseek-v3" -> "DeepSeek V3";
            case "gpt-4o" -> "GPT-4o";
            case "claude-sonnet-4" -> "Claude Sonnet 4";
            default -> id;
        };
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return Math.max(1, (int) Math.ceil(text.length() / 1.8));
    }

    private String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { return fallback; }
    }

    private double toDouble(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        try { return value == null ? fallback : Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException e) { return fallback; }
    }
}
