package com.aicp.common.ai;

import com.aicp.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelControllerTest {

    @Mock
    private AiModelRegistry modelRegistry;

    @Test
    void localRegistryResponseDeclaresNonAuthoritativeCatalogAndBillingProvenance() {
        when(modelRegistry.listModels("text", "text_agent"))
                .thenReturn(List.of(Map.of("model_id", "deepseek-v3")));

        ApiResponse<Map<String, Object>> response =
                new AiModelController(modelRegistry).listModels("text", "text_agent");

        assertThat(response.getData())
                .containsEntry("catalog_provenance", "local_registry")
                .containsEntry("billing_provenance", "local_estimate")
                .containsEntry("accounting_authoritative", false);
    }
}
