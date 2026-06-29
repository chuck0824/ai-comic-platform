package com.aicp.module.contentproject.service;

import com.aicp.common.ai.AiRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M1: Validate AI output against versioned JSON Schemas (stored as JSON files).
 * Uses lightweight validation: checks required fields, types, and array sizes.
 * On first failure, attempts one repair retry via AiRouter.
 * Never returns mock data — real validation errors are surfaced.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaValidationService {

    private final AiRouter aiRouter;
    private final ObjectMapper objectMapper;

    private final Map<String, Map<String, Object>> schemaCache = new ConcurrentHashMap<>();

    /**
     * Validate parsed AI output. Returns validated map or throws.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> validate(String jobType, Map<String, Object> parsed) {
        String schemaName = jobType + ".json";
        Map<String, Object> schema = loadSchema(schemaName);
        if (schema == null) {
            return parsed; // No schema → pass through
        }

        List<String> errors = validateAgainst(parsed, schema, "");
        if (errors.isEmpty()) {
            return parsed;
        }

        String errorSummary = String.join("; ", errors);
        log.warn("Schema validation failed for {}: {}", jobType, errorSummary);

        // Attempt repair
        String rawJson;
        try { rawJson = objectMapper.writeValueAsString(parsed); }
        catch (Exception e) { throw new SchemaValidationException("Cannot serialize: " + e.getMessage(), errors); }

        Map<String, Object> repaired = attemptRepair(schemaName, rawJson, errorSummary);
        if (repaired == null) {
            throw new SchemaValidationException("Repair failed: " + errorSummary, errors);
        }

        List<String> remaining = validateAgainst(repaired, schema, "");
        if (!remaining.isEmpty()) {
            throw new SchemaValidationException(
                    "Still invalid after repair: " + String.join("; ", remaining), remaining);
        }

        log.info("Schema repair successful for {}", jobType);
        return repaired;
    }

    private List<String> validateAgainst(Map<String, Object> data, Map<String, Object> schema, String path) {
        List<String> errors = new ArrayList<>();

        // Check required fields
        Object requiredObj = schema.get("required");
        if (requiredObj instanceof List<?> required) {
            for (Object r : required) {
                String field = String.valueOf(r);
                if (!data.containsKey(field)) {
                    errors.add(path + "." + field + ": required field missing");
                }
            }
        }

        // Check property types
        Object propsObj = schema.get("properties");
        if (propsObj instanceof Map<?, ?> props) {
            for (Map.Entry<?, ?> entry : props.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = data.get(key);
                if (value == null) continue;

                Object propSchema = entry.getValue();
                if (propSchema instanceof Map<?, ?> ps) {
                    Object typeObj = ps.get("type");
                    String type = typeObj != null ? String.valueOf(typeObj) : "string";
                    String currentPath = path + "." + key;

                    if ("array".equals(type) && !(value instanceof List)) {
                        errors.add(currentPath + ": expected array, got " + value.getClass().getSimpleName());
                    } else if ("integer".equals(type) && value instanceof Number) {
                        Object min = ps.get("minimum");
                        if (min instanceof Number && ((Number) value).doubleValue() < ((Number) min).doubleValue()) {
                            errors.add(currentPath + ": value " + value + " < min " + min);
                        }
                    } else if ("string".equals(type)) {
                        String sv = String.valueOf(value);
                        Object minLenObj = ps.get("minLength");
                        if (minLenObj instanceof Number && sv.length() < ((Number) minLenObj).intValue()) {
                            errors.add(currentPath + ": length " + sv.length() + " < min " + minLenObj);
                        }
                    }

                    // Check nested array items
                    if ("array".equals(type) && value instanceof List<?> list) {
                        Object itemsSchema = ps.get("items");
                        if (itemsSchema instanceof Map) {
                            Object minItems = ps.get("minItems");
                            if (minItems instanceof Number && list.size() < ((Number) minItems).intValue()) {
                                errors.add(currentPath + ": array size " + list.size() + " < min " + minItems);
                            }
                            for (int i = 0; i < list.size(); i++) {
                                Object item = list.get(i);
                                if (item instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> itemMap = (Map<String, Object>) item;
                                    errors.addAll(validateAgainst(itemMap,
                                            (Map<String, Object>) itemsSchema, currentPath + "[" + i + "]"));
                                }
                            }
                        }
                    }
                }
            }
        }

        // Check enum constraints
        Object enumObj = schema.get("enum");
        if (enumObj instanceof List<?> enumVals && data instanceof Map) {
            // For root-level enum check on specific fields
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) data).entrySet()) {
                // Check if any property has enum constraint
                Object fieldSchema = ((Map<?, ?>) propsObj).get(entry.getKey());
                if (fieldSchema instanceof Map<?, ?> fs) {
                    Object fieldEnum = fs.get("enum");
                    if (fieldEnum instanceof List<?> fe) {
                        String val = String.valueOf(entry.getValue());
                        if (!fe.contains(val)) {
                            errors.add(path + "." + entry.getKey() + ": '" + val + "' not in " + fe);
                        }
                    }
                }
            }
        }

        return errors;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadSchema(String schemaName) {
        return schemaCache.computeIfAbsent(schemaName, name -> {
            try {
                ClassPathResource resource = new ClassPathResource("schemas/generation/" + name);
                if (!resource.exists()) return null;
                try (InputStream is = resource.getInputStream()) {
                    return objectMapper.readValue(is, Map.class);
                }
            } catch (Exception e) {
                log.warn("Failed to load schema {}: {}", name, e.getMessage());
                return null;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> attemptRepair(String schemaName, String rawJson, String errors) {
        try {
            Map<String, Object> schema = loadSchema(schemaName);
            String schemaStr = schema != null ? objectMapper.writeValueAsString(schema) : "{}";

            String prompt = "以下是AI生成的JSON，验证失败。请修复JSON以符合以下Schema：\n\n"
                    + "Schema:\n" + schemaStr + "\n\n"
                    + "验证错误:\n" + errors + "\n\n"
                    + "原始JSON:\n" + rawJson + "\n\n"
                    + "只返回修复后的JSON，不要包含任何解释。";

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("system_prompt", "你是JSON修复专家。只输出修复后的有效JSON，不包含任何其他文字。");
            params.put("prompt", prompt);
            params.put("temperature", 0.1);
            params.put("max_tokens", 4096);

            Map<String, Object> result = aiRouter.chatCompletion(params);
            String text = extractText(result);
            String json = extractJson(text);
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Schema repair failed for {}: {}", schemaName, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> result) {
        Object choices = result.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map) {
                Object message = ((Map<String, Object>) first).get("message");
                if (message instanceof Map) {
                    Object content = ((Map<String, Object>) message).get("content");
                    if (content != null) return String.valueOf(content);
                }
            }
        }
        return result.toString();
    }

    private String extractJson(String text) {
        if (text.contains("```json")) {
            int s = text.indexOf("```json") + 7;
            int e = text.indexOf("```", s);
            if (e > s) return text.substring(s, e).trim();
        }
        if (text.contains("```")) {
            int s = text.indexOf("```") + 3;
            int e = text.indexOf("```", s);
            if (e > s) return text.substring(s, e).trim();
        }
        return text.trim();
    }

    public static class SchemaValidationException extends RuntimeException {
        private final List<String> errors;
        public SchemaValidationException(String message, List<String> errors) {
            super(message);
            this.errors = errors;
        }
        public List<String> getErrors() { return errors; }
    }
}
