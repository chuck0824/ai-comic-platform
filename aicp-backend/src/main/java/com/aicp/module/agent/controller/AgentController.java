package com.aicp.module.agent.controller;

import com.aicp.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private final Map<String, Map<String, Object>> sessions = new LinkedHashMap<>();
    private final Map<String, List<Map<String, Object>>> messagesStore = new LinkedHashMap<>();

    @PostMapping("/sessions")
    public ApiResponse<Map<String, Object>> createSession(@RequestBody Map<String, Object> body) {
        String uuid = "agent_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("uuid", uuid);
        session.put("title", body.getOrDefault("title", "新会话"));
        session.put("project_id", body.get("project_id"));
        session.put("agent_config", body.getOrDefault("agent_config", Map.of(
            "script_agent", true, "production_agent", true, "quality_agent", true)));
        session.put("status", "active");
        session.put("created_at", LocalDateTime.now());
        sessions.put(uuid, session);
        messagesStore.put(uuid, new ArrayList<>());
        return ApiResponse.success(session);
    }

    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> listSessions() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : sessions.entrySet()) {
            Map<String, Object> s = new LinkedHashMap<>(entry.getValue());
            s.put("id", entry.getKey());
            s.put("message_count", messagesStore.getOrDefault(entry.getKey(), List.of()).size());
            list.add(s);
        }
        return ApiResponse.success(list);
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<Map<String, Object>> getSession(@PathVariable String sessionId) {
        Map<String, Object> session = sessions.get(sessionId);
        if (session == null) return ApiResponse.error(47001, "会话不存在");
        session.put("messages", messagesStore.getOrDefault(sessionId, List.of()));
        return ApiResponse.success(session);
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<Map<String, Object>> sendMessage(@PathVariable String sessionId,
                                                         @RequestBody Map<String, Object> body) {
        if (!sessions.containsKey(sessionId)) return ApiResponse.error(47001, "会话不存在");

        // 用户消息
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", body.get("content"));
        userMsg.put("created_at", LocalDateTime.now());
        messagesStore.get(sessionId).add(userMsg);

        // 生成执行计划 (mock - 实际由AI Router处理)
        Map<String, Object> plan = generatePlan((String) body.get("content"));

        // Agent 响应
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "我已解析你的任务，生成了执行计划。请确认后开始执行。");
        assistantMsg.put("plan", plan);
        assistantMsg.put("confidence", 0.85);
        assistantMsg.put("created_at", LocalDateTime.now());
        messagesStore.get(sessionId).add(assistantMsg);

        return ApiResponse.success(Map.of(
            "messages", List.of(assistantMsg),
            "plan", plan,
            "estimated_credits", estimateCredits(plan)));
    }

    @GetMapping("/sessions/{sessionId}/executions")
    public ApiResponse<List<Map<String, Object>>> getExecutions(@PathVariable String sessionId) {
        return ApiResponse.success(List.of());
    }

    @PostMapping("/orchestrate")
    public ApiResponse<Map<String, Object>> orchestrate(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(Map.of(
            "task_id", "orchestrate_" + System.currentTimeMillis(),
            "status", "pending",
            "plan", generatePlan((String) body.getOrDefault("prompt", ""))));
    }

    @PostMapping("/orchestrate/canvas")
    public ApiResponse<Map<String, Object>> orchestrateCanvas(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(Map.of(
            "task_id", "orch_canvas_" + System.currentTimeMillis(),
            "project_id", body.get("project_id"),
            "status", "pending",
            "message", "画布编排任务已创建，Agent将自动创建节点、连线并执行生成"));
    }

    private Map<String, Object> generatePlan(String prompt) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (prompt.contains("剧本") || prompt.contains("脚本")) {
            steps.add(step(1, "generate_script", "生成剧本", 30));
        }
        if (prompt.contains("分镜") || prompt.contains("镜头")) {
            steps.add(step(steps.size() + 1, "generate_storyboard", "解析分镜", 20));
        }
        if (prompt.contains("图") || prompt.contains("生图")) {
            steps.add(step(steps.size() + 1, "generate_image", "批量生图", 60));
        }
        if (prompt.contains("视频") || prompt.contains("生视频")) {
            steps.add(step(steps.size() + 1, "generate_video", "生成视频", 90));
        }
        if (prompt.contains("合成") || prompt.contains("导出")) {
            steps.add(step(steps.size() + 1, "compose_video", "合成导出", 45));
        }
        steps.add(step(steps.size() + 1, "quality_check", "质量检查", 15));
        return Map.of("steps", steps, "total_estimated_seconds", steps.stream().mapToInt(s -> (int)s.get("estimated_seconds")).sum());
    }

    private Map<String, Object> step(int order, String toolName, String title, int estimatedSeconds) {
        return Map.of("order", order, "tool_name", toolName, "title", title,
            "status", "pending", "estimated_seconds", estimatedSeconds);
    }

    private int estimateCredits(Map<String, Object> plan) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) plan.get("steps");
        return steps != null ? steps.size() * 20 : 100;
    }
}
