package com.aicp.common.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * new-api 网关 HTTP 客户端
 * 所有 AI 模型调用统一经过此客户端，不直连供应商
 */
@Component
public class NewApiClient {

    @Value("${new-api.base-url:http://localhost:3000}")
    private String baseUrl;

    @Value("${new-api.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public NewApiClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    // ===== LLM Chat =====
    public Map<String, Object> chatCompletions(Map<String, Object> request) {
        return post("/v1/chat/completions", request);
    }

    // ===== Image Generation =====
    public Map<String, Object> imageGeneration(Map<String, Object> request) {
        return post("/v1/images/generations", request);
    }

    // ===== Video Generation =====
    public Map<String, Object> videoGeneration(Map<String, Object> request) {
        return post("/v1/video/generations", request);
    }

    // ===== Audio / TTS =====
    public Map<String, Object> audioSpeech(Map<String, Object> request) {
        return post("/v1/audio/speech", request);
    }

    // ===== Generic POST =====
    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(request), headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + path, HttpMethod.POST, entity, Map.class);
            if (response.getBody() != null) {
                return response.getBody();
            }
            return Map.of("error", "Empty response from new-api");
        } catch (Exception e) {
            // 降级：返回模拟结果，保证开发环境可用
            return mockResponse(path, request);
        }
    }

    /**
     * 开发环境 mock：new-api 不可用时返回模拟数据，保证前端开发不中断
     */
    private Map<String, Object> mockResponse(String path, Map<String, Object> request) {
        return switch (path) {
            case "/v1/chat/completions" -> Map.of(
                "choices", List.of(Map.of("message", Map.of("content",
                    "[MOCK] AI响应 - new-api未连接。请求模型: " + request.getOrDefault("model", "unknown")))));
            case "/v1/images/generations" -> Map.of(
                "data", List.of(Map.of("url", "https://placehold.co/1080x1920/png?text=AI+Generated")));
            case "/v1/video/generations" -> Map.of(
                "data", List.of(Map.of("url", "https://placehold.co/1080x1920/mp4?text=AI+Video")));
            case "/v1/audio/speech" -> Map.of(
                "data", List.of(Map.of("url", "https://placehold.co/audio.wav?text=TTS")));
            default -> Map.of("mock", true, "message", "new-api未连接，返回mock数据");
        };
    }
}
