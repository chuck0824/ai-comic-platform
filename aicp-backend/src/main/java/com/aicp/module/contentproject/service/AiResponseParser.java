package com.aicp.module.contentproject.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Shared AI response parsing utilities.
 *
 * Eliminates code duplication across services: every service previously
 * had its own private copy of extractText/parseJson/ellipsis/str/toInt/toDouble/toJson/sha256.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiResponseParser {

    private final ObjectMapper objectMapper;

    /** Extract text content from an OpenAI-style chat completion response. */
    public String extractText(Map<String, Object> result) {
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

    /**
     * Parse JSON from AI-generated text, handling ```json fences.
     * Logs a warning on parse failure so operators can detect AI output issues.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseJson(String text) {
        try {
            String json = extractJsonBlock(text);
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse AI JSON output ({} chars, starts with: {}): {}",
                    text != null ? text.length() : 0,
                    text != null ? text.substring(0, Math.min(200, text.length())) : "null",
                    e.getMessage());
            return Map.of("_parse_error", e.getMessage(),
                    "_raw", text != null ? text : "");
        }
    }

    /**
     * Parse JSON with a custom default value when parsing fails.
     * Prefer {@link #parseJson(String)} with _parse_error inspection instead.
     */
    public Map<String, Object> parseJsonOrDefault(String text, Map<String, Object> defaultVal) {
        Map<String, Object> result = parseJson(text);
        if (result.containsKey("_parse_error")) {
            return defaultVal;
        }
        return result;
    }

    /** Extract the JSON content from a markdown-fenced block. */
    public String extractJsonBlock(String text) {
        if (text == null) return "";
        if (text.contains("```json")) {
            int s = text.indexOf("```json") + 7;
            int e = text.indexOf("```", s);
            if (e > s) return text.substring(s, e).trim();
        } else if (text.contains("```")) {
            int s = text.indexOf("```") + 3;
            int e = text.indexOf("```", s);
            if (e > s) return text.substring(s, e).trim();
        }
        return text;
    }

    /** Safe string conversion. */
    public String str(Object v) {
        return v != null ? String.valueOf(v) : "";
    }

    /** Safe int conversion with default. */
    public int toInt(Object v, int def) {
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try { return Integer.parseInt(v.toString()); } catch (Exception ignored) {}
        }
        return def;
    }

    /** Safe double conversion with default. */
    public double toDouble(Object v, double def) {
        if (v instanceof Number n) return n.doubleValue();
        if (v != null) {
            try { return Double.parseDouble(v.toString()); } catch (Exception ignored) {}
        }
        return def;
    }

    /** Serialize an object to JSON string. */
    public String toJson(Object v) {
        try { return objectMapper.writeValueAsString(v); } catch (Exception e) {
            log.warn("Failed to serialize to JSON", e);
            return "[]";
        }
    }

    /** Truncate text to max chars, appending "..." if truncated. */
    public String ellipsis(String text, int max) {
        return text != null && text.length() > max ? text.substring(0, max) + "..." : text;
    }

    /** SHA-256 hash of input string. */
    public String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            log.warn("SHA-256 failed, falling back to hashCode", e);
            return Integer.toHexString(input.hashCode());
        }
    }
}
