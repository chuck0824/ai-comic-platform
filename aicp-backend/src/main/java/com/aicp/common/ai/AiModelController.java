package com.aicp.common.ai;

import com.aicp.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AiModelController {

    private final AiModelRegistry modelRegistry;

    @GetMapping("/api/v1/ai/models")
    public ApiResponse<Map<String, Object>> listModels(@RequestParam(required = false) String node_type,
                                                       @RequestParam(required = false) String agent_type) {
        return ApiResponse.success(Map.of("models", modelRegistry.listModels(node_type, agent_type)));
    }
}
