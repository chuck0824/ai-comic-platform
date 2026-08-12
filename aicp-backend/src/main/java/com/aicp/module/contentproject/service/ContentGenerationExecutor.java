package com.aicp.module.contentproject.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.module.contentproject.entity.ContentGenerationJob;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentGenerationJobMapper;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * M1: Async executor that processes pending content_generation_jobs
 * by calling AiRouter and saving results as content versions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentGenerationExecutor {

    private final ContentGenerationJobMapper jobMapper;
    private final ContentVersionMapper versionMapper;
    private final ContentUnitMapper unitMapper;
    private final AiRouter aiRouter;
    private final ObjectMapper objectMapper;
    private final SchemaValidationService schemaValidation;

    /**
     * Execute a generation job asynchronously.
     * Called after job creation to actually run the AI call.
     */
    @Async("genTaskExecutor")
    public void execute(Long jobId) {
        ContentGenerationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            log.warn("Generation job not found: {}", jobId);
            return;
        }

        ContentVersion candidate = null;
        try {
            if (!"pending".equals(job.getStatus())) return;
            int started = jobMapper.update(null, new UpdateWrapper<ContentGenerationJob>()
                    .eq("id", jobId)
                    .eq("status", "pending")
                    .set("status", "processing"));
            if (started == 0) return;
            job.setStatus("processing");
            if (isCancelled(jobId)) return;

            // Parse input snapshot to extract system prompt and user prompt
            String snapshotJson = job.getInputSnapshotJson();
            Map<String, Object> snapshot;
            try {
                snapshot = objectMapper.readValue(snapshotJson,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                snapshot = Map.of();
            }

            // Build AI params from snapshot
            String systemPrompt = buildSystemPrompt(job.getJobType(), snapshot);
            String userPrompt = buildUserPrompt(job.getJobType(), snapshot);

            Map<String, Object> aiParams = new java.util.LinkedHashMap<>();
            aiParams.put("model", job.getModel() != null ? job.getModel() : "deepseek-v3");
            aiParams.put("system_prompt", systemPrompt);
            aiParams.put("prompt", userPrompt);
            aiParams.put("temperature", 0.7);
            aiParams.put("max_tokens", 4096);

            // Call AI
            log.info("Executing generation job {}: type={}, model={}", jobId, job.getJobType(), aiParams.get("model"));
            Map<String, Object> aiResult = aiRouter.chatCompletion(aiParams);
            if (isCancelled(jobId)) return;
            String generatedText = extractContent(aiResult);

            // Parse structured output if the job expects JSON
            Map<String, Object> parsedResult = tryParseJson(generatedText);

            // M1: Schema validation with repair retry
            if (parsedResult != null) {
                try {
                    parsedResult = schemaValidation.validate(job.getJobType(), parsedResult);
                } catch (SchemaValidationService.SchemaValidationException e) {
                    log.error("Schema validation failed for job {}: {}", jobId, e.getMessage());
                    markFailedIfProcessing(jobId, "SCHEMA_VALIDATION_FAILED");
                    return;
                }
            }

            if (isCancelled(jobId)) return;
            // Save as a content version if target_id is specified
            if (job.getTargetId() != null && job.getTargetType() != null) {
                candidate = saveGeneratedContent(job, parsedResult != null ? toJson(parsedResult) : generatedText);
            }
            if (isCancelled(jobId)) {
                deleteCandidate(candidate);
                return;
            }

            int completed = jobMapper.update(null, new UpdateWrapper<ContentGenerationJob>()
                    .eq("id", jobId)
                    .eq("status", "processing")
                    .set("status", "completed")
                    .set("actual_credits", estimateTokens(generatedText))
                    .set("finished_at", LocalDateTime.now()));
            if (completed == 0) {
                deleteCandidate(candidate);
                return;
            }
            job.setStatus("completed");
            job.setActualCredits(estimateTokens(generatedText));

            log.info("Generation job {} completed: {} chars", jobId, generatedText.length());
        } catch (Exception e) {
            log.error("Generation job {} failed", jobId, e);
            deleteCandidate(candidate);
            markFailedIfProcessing(jobId, "AI_ERROR");
        }
    }

    private void deleteCandidate(ContentVersion candidate) {
        if (candidate == null || candidate.getId() == null) return;
        try {
            versionMapper.deleteById(candidate.getId());
        } catch (Exception cleanupError) {
            // Candidate/discarded versions are excluded from every public version path even if physical cleanup fails.
            log.warn("Failed to clean candidate version {}", candidate.getId(), cleanupError);
        }
    }

    private boolean isCancelled(Long jobId) {
        ContentGenerationJob current = jobMapper.selectById(jobId);
        return current == null || "cancelled".equals(current.getStatus());
    }

    private void markFailedIfProcessing(Long jobId, String errorCode) {
        jobMapper.update(null, new UpdateWrapper<ContentGenerationJob>()
                .eq("id", jobId)
                .eq("status", "processing")
                .set("status", "failed")
                .set("error_code", errorCode)
                .set("finished_at", LocalDateTime.now()));
    }

    private ContentVersion saveGeneratedContent(ContentGenerationJob job, String contentJson) {
        // Create a named version for the target content unit
        ContentUnit unit = unitMapper.selectById(job.getTargetId());
        if (unit == null) return null;

        // Get next version_no
        List<ContentVersion> existing = versionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, job.getTargetId())
                        .gt(ContentVersion::getVersionNo, 0)
                        .orderByDesc(ContentVersion::getVersionNo)
                        .last("limit 1"));
        int nextVersion = existing.isEmpty() ? 1 : existing.get(0).getVersionNo() + 1;

        String hash = sha256(contentJson);

        ContentVersion cv = new ContentVersion();
        cv.setProjectId(job.getProjectId());
        cv.setContentUnitId(job.getTargetId());
        cv.setVersionNo(nextVersion);
        cv.setStatus("candidate");
        cv.setContentJson(contentJson);
        cv.setPlainText(extractPlainText(contentJson));
        cv.setSource("ai_generated");
        cv.setGenerationJobId(job.getId());
        cv.setContentHash(hash);
        cv.setCreatedBy(job.getCreatedBy());
        versionMapper.insert(cv);
        return cv;
    }

    // ===== Prompt Builders =====

    private String buildSystemPrompt(String jobType, Map<String, Object> snapshot) {
        return switch (jobType) {
            case "story_seed_generate" ->
                "你是一位资深的短剧编剧。请根据用户提供的创意，扩展成一个完整的故事种子，包括核心冲突、主要人物和故事走向。";
            case "characters_generate" ->
                "你是一位资深的短剧编剧，擅长塑造鲜明的人物形象。请根据故事种子生成详细的人物设定。";
            case "synopsis_generate" ->
                "你是一位资深的短剧编剧。请根据故事种子和人物设定生成500字故事梗概。";
            case "outline_generate" ->
                "你是一位资深的短剧编剧。请根据梗概生成分集大纲，每集包含核心冲突和悬念钩子。";
            case "content_generate" ->
                "你是一位资深的短剧编剧。请根据大纲生成单集完整剧本，包含对白、动作描述和分场。";
            case "review_generate" ->
                "你是一位资深的剧本审核专家。请从钩子强度、剧情逻辑、导演可行性三个维度审核剧本。";
            default ->
                "你是一位资深的短剧编剧。请根据提供的上下文生成内容。";
        };
    }

    @SuppressWarnings("unchecked")
    private String buildUserPrompt(String jobType, Map<String, Object> snapshot) {
        StringBuilder sb = new StringBuilder();

        // Extract parameter context
        Object paramObj = snapshot.get("parameter");
        if (paramObj instanceof Map<?, ?> params) {
            Object startContent = params.get("start_content");
            if (startContent != null) sb.append("起始内容：").append(startContent).append("\n");
            Object goal = params.get("content_goal");
            if (goal != null) sb.append("内容目标：").append(goal).append("\n");
        }

        // Extract strategy if present
        Object strategy = snapshot.get("strategy");
        if (strategy != null) sb.append("策略：").append(strategy).append("\n");

        // Extract content unit context
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            if ("parameter".equals(entry.getKey()) || "strategy".equals(entry.getKey())
                    || entry.getKey().startsWith("_")) continue;
            sb.append("\n--- ").append(entry.getKey()).append(" ---\n");
            if (entry.getValue() instanceof Map<?, ?> m) {
                Object content = m.get("content");
                if (content != null) {
                    sb.append(content);
                } else {
                    sb.append(toJson(entry.getValue()));
                }
            } else if (entry.getValue() instanceof String s) {
                sb.append(s);
            }
        }

        if (sb.isEmpty()) {
            sb.append("请根据系统提示生成内容。");
        }

        return sb.toString();
    }

    // ===== Helpers =====

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> aiResult) {
        Object choices = aiResult.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> choice) {
                Object message = choice.get("message");
                if (message instanceof Map<?, ?> msg) {
                    Object content = msg.get("content");
                    if (content != null) return String.valueOf(content);
                }
            }
        }
        Object content = aiResult.get("content");
        if (content != null) return String.valueOf(content);
        Object text = aiResult.get("text");
        if (text != null) return String.valueOf(text);
        return aiResult.toString();
    }

    private Map<String, Object> tryParseJson(String text) {
        try {
            // Try to extract JSON block from markdown
            String json = text;
            if (text.contains("```json")) {
                int start = text.indexOf("```json") + 7;
                int end = text.indexOf("```", start);
                if (end > start) json = text.substring(start, end).trim();
            } else if (text.contains("```")) {
                int start = text.indexOf("```") + 3;
                int end = text.indexOf("```", start);
                if (end > start) json = text.substring(start, end).trim();
            }
            return objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private String extractPlainText(String contentJson) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(contentJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            Object content = parsed.get("content");
            if (content instanceof String s) return s;
            Object blocks = parsed.get("blocks");
            if (blocks instanceof List<?> list) {
                StringBuilder sb = new StringBuilder();
                for (Object b : list) {
                    if (b != null) sb.append(b.toString()).append("\n");
                }
                return sb.toString().trim();
            }
        } catch (Exception e) {
            // not JSON, return as-is
        }
        return contentJson;
    }

    private int estimateTokens(String text) {
        // Rough estimate: 1 token ≈ 2 Chinese chars or 4 English chars
        return text != null ? text.length() / 2 : 0;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return input.hashCode() + "";
        }
    }
}
