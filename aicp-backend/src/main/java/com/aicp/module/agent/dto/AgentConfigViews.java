package com.aicp.module.agent.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AgentConfigViews {

    public record BlueprintView(
            String id,
            String roleType,
            String name,
            String description,
            Map<String, Object> parameterSchema,
            Map<String, Object> defaults,
            int blueprintVersion) {}

    public record DefinitionView(
            String id,
            String blueprintId,
            String roleType,
            String name,
            String description,
            String lifecycleStatus,
            String currentPublishedVersionId,
            Integer rowVersion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record DefinitionListItem(
            String id,
            String blueprintId,
            String roleType,
            String name,
            String lifecycleStatus,
            String currentPublishedVersionId,
            Integer currentVersionNo,
            LocalDateTime updatedAt) {}

    public record VersionView(
            String id,
            String userAgentId,
            int versionNo,
            Map<String, Object> parameters,
            String editablePrompt,
            List<Map<String, Object>> examples,
            Map<String, Object> modelPolicy,
            String status,
            String changeSummary,
            Integer rowVersion,
            Long createdBy,
            LocalDateTime createdAt,
            LocalDateTime publishedAt) {}

    public record ValidateResult(
            boolean valid,
            List<String> errors) {}

    public record TestRunView(
            String id,
            String agentVersionId,
            String status,
            Map<String, Object> outputJson,
            Boolean outputSchemaValid,
            String modelId,
            Integer promptTokens,
            Integer completionTokens,
            Double creditCost,
            Integer durationMs,
            String errorCode,
            String errorMessage,
            LocalDateTime createdAt) {}

    public record BindingView(
            String id,
            String scopeType,
            String scopeId,
            String roleType,
            String userAgentName,
            String agentVersionId,
            Integer rowVersion,
            LocalDateTime createdAt) {}

    public record ResolvedConfigView(
            String bindingSource,
            String userAgentId,
            String userAgentName,
            String versionId,
            int versionNo,
            String blueprintId,
            String roleType,
            Map<String, Object> resolvedParameters,
            String compiledPrompt,
            String promptHash) {}

    public record SnapshotView(
            String id,
            String blueprintId,
            int blueprintVersion,
            String userAgentId,
            String agentVersionId,
            String bindingSource,
            String resolvedPrompt,
            String promptHash,
            Long projectId,
            String businessTaskType,
            LocalDateTime createdAt) {}

    private AgentConfigViews() {}
}
