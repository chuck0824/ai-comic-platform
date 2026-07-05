package com.aicp.module.agent.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.agent.entity.AgentBlueprint;
import com.aicp.module.agent.entity.AgentVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AgentPromptCompiler {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    private static final Set<String> ALLOWED_VARIABLES = Set.of(
            "user_method", "project_context", "task_input");

    public record CompiledPrompt(String systemPrompt, String userPrompt, String promptHash) {}

    public CompiledPrompt compile(AgentBlueprint blueprint, AgentVersion version,
                                   Map<String, Object> runtimeContext) {
        String editablePrompt = version.getEditablePrompt();
        if (editablePrompt == null || editablePrompt.isBlank()) {
            editablePrompt = blueprint.getEditablePromptTemplate();
        }

        Set<String> used = extractVariables(editablePrompt);
        if (!ALLOWED_VARIABLES.containsAll(used)) {
            Set<String> illegal = new LinkedHashSet<>(used);
            illegal.removeAll(ALLOWED_VARIABLES);
            throw new BizException(ErrorCode.AGENT_CONFIG_INVALID,
                    "包含未声明变量: " + illegal);
        }

        String resolvedPrompt = editablePrompt;
        for (Map.Entry<String, Object> entry : runtimeContext.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            resolvedPrompt = resolvedPrompt.replace(placeholder, value);
        }

        String systemPrompt = blueprint.getLockedSystemPrompt();
        String userPrompt = resolvedPrompt
                + "\n\n" + runtimeContext.getOrDefault("project_context", "");

        String fullPrompt = systemPrompt + "\n\n" + userPrompt;
        String hash = sha256(fullPrompt);

        return new CompiledPrompt(systemPrompt, userPrompt, hash);
    }

    public Set<String> extractVariables(String text) {
        Matcher m = VARIABLE_PATTERN.matcher(text);
        Set<String> vars = new LinkedHashSet<>();
        while (m.find()) {
            vars.add(m.group(1));
        }
        return vars;
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "sha256:none";
        }
    }
}
