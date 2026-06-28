package com.aicp.module.canvas.controller;

import com.aicp.common.ai.AiModelRegistry;
import com.aicp.common.dto.ApiResponse;
import com.aicp.module.canvas.entity.CanvasNode;
import com.aicp.module.canvas.service.CanvasService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/canvas/projects/{projectId}/text-node-agent")
@RequiredArgsConstructor
public class TextNodeAgentController {

    private final CanvasService canvasService;
    private final AiModelRegistry modelRegistry;

    @PostMapping("/plan")
    public ApiResponse<Map<String, Object>> plan(@PathVariable String projectId,
                                                 @RequestBody Map<String, Object> body) {
        String nodeId = stringValue(body.get("node_id"));
        String modelId = stringValue(body.get("model_id"));
        String instruction = stringValue(body.get("instruction"));
        String currentContent = stringValue(body.get("current_content"));

        if (nodeId.isBlank()) return ApiResponse.error(40002, "文本节点不能为空");
        if (instruction.isBlank()) return ApiResponse.error(40002, "请输入你想如何修改当前文本");

        Map<String, Object> selectedModel = modelRegistry.findModel(modelId);
        Map<String, Object> usageEstimate = modelRegistry.estimateUsage(selectedModel, instruction, currentContent);
        String revisedContent = reviseContent(instruction, currentContent);

        return ApiResponse.success(Map.of(
                "agent_type", "text_agent",
                "selected_model", selectedModel,
                "usage_estimate", usageEstimate,
                "original_content", currentContent,
                "revised_content", revisedContent,
                "need_confirm", true
        ));
    }

    @PostMapping("/apply")
    public ApiResponse<?> apply(@PathVariable String projectId,
                                @RequestBody Map<String, Object> body) {
        String nodeId = stringValue(body.get("node_id"));
        String revisedContent = stringValue(body.get("revised_content"));
        if (nodeId.isBlank()) return ApiResponse.error(40002, "文本节点不能为空");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text_mode", "prompt");
        data.put("prompt", revisedContent);
        data.put("content", revisedContent);
        data.put("source", "text_node_agent");
        data.put("agent_type", "text_agent");
        data.put("model_id", stringValue(body.get("model_id")));

        CanvasNode node = canvasService.updateNode(projectId, nodeId, Map.of(
                "data", data,
                "status", "ready",
                "width", 520,
                "height", 300
        ));
        return node == null ? ApiResponse.error(46011, "画布节点不存在") : ApiResponse.success(node);
    }

    /**
     * 文本内容修订。
     * TODO: 当前为规则引擎 mock 实现，尚未接入 AI 模型。
     * 在接入 LLM API 后，应替换为实际的模型调用。
     * 参见: docs/02-derived/漫剧自由画布二期增强_PRD.md
     */
    private String reviseContent(String instruction, String currentContent) {
        String base = currentContent == null ? "" : currentContent.trim();
        String command = instruction == null ? "" : instruction.trim();
        if (base.isBlank()) {
            return command;
        }
        if (containsAny(command, "缩写", "简短", "精简")) {
            return base.length() <= 120 ? base : base.substring(0, 120) + "。";
        }
        if (containsAny(command, "扩写", "丰富", "详细")) {
            return base + "\n\n" + "补充方向：" + command + "。在保留原有设定的基础上，增加场景细节、人物动作、情绪变化和叙事节奏。";
        }
        if (containsAny(command, "悬念", "冲突", "紧张")) {
            return "在更强的悬念氛围中，" + base + "\n\n" + "画面需要突出未知风险、人物犹豫与即将爆发的冲突，让观众产生继续观看的期待。";
        }
        if (containsAny(command, "分镜", "镜头")) {
            return "镜头描述：" + base + "\n\n" + "镜头要求：" + command + "。请保持画面主体清晰，动作连贯，情绪递进。";
        }
        if (containsAny(command, "视频提示词", "提示词")) {
            return base + "\n\n" + "视频提示词优化：" + command + "，电影级光影，清晰主体，稳定构图，细节丰富，情绪明确。";
        }
        return base + "\n\n" + "修改要求：" + command;
    }

    private boolean containsAny(String text, String... words) {
        if (text == null) return false;
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
