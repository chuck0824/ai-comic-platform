package com.aicp.module.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public final class AgentConfigRequests {

    public record CreateDefinitionRequest(
            @NotBlank String blueprintId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description) {}

    public record UpdateDefinitionRequest(
            @Size(max = 120) String name,
            @Size(max = 1000) String description) {}

    public record CreateDraftRequest() {}

    public record UpdateDraftRequest(
            @NotNull Integer rowVersion,
            @NotNull Map<String, Object> parameters,
            @NotBlank @Size(max = 16000) String editablePrompt,
            List<Map<String, Object>> examples,
            Map<String, Object> modelPolicy) {}

    public record TestRunRequest(
            String taskInput,
            Map<String, Object> contextRefs) {}

    public record PublishVersionRequest(
            @NotNull Integer rowVersion,
            @Size(max = 500) String changeSummary) {}

    public record BindVersionRequest(
            @NotBlank String versionId) {}

    public record ResolvePreviewRequest(
            Long projectId,
            @NotBlank String roleType,
            Map<String, Object> temporaryOverrides) {}

    public record SnapshotCommand(
            @NotNull Long projectId,
            String taskType,
            @NotNull Long userId) {}

    private AgentConfigRequests() {}
}
