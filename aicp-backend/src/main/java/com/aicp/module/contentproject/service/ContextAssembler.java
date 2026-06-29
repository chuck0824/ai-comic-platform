package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.entity.ProjectParameterVersion;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.aicp.module.contentproject.mapper.ProjectParameterVersionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContextAssembler {

    private final ContentProjectMapper projectMapper;
    private final ProjectParameterVersionMapper parameterVersionMapper;
    private final ContentVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public ContextSnapshot assemble(Long projectId, GenerationJobRequest request) {
        ContentProject project = projectMapper.selectById(projectId);
        if (project == null || project.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND);
        }

        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, Long> selected = new LinkedHashMap<>();

        // load selected parameter version
        Long paramVersionId = request.selectedVersions() != null
                ? request.selectedVersions().get("parameter")
                : project.getCurrentParameterVersionId();
        if (paramVersionId != null) {
            ProjectParameterVersion pv = parameterVersionMapper.selectById(paramVersionId);
            if (pv == null || !pv.getProjectId().equals(projectId)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "参数版本不属于当前项目");
            }
            try {
                Map<String, Object> payload = objectMapper.readValue(pv.getPayloadJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                context.put("parameter", payload);
            } catch (JsonProcessingException e) {
                context.put("parameter", Map.of());
            }
            selected.put("parameter", paramVersionId);
        }

        // load selected content versions
        if (request.selectedVersions() != null) {
            for (Map.Entry<String, Long> entry : request.selectedVersions().entrySet()) {
                if ("parameter".equals(entry.getKey())) continue;
                Long versionId = entry.getValue();
                ContentVersion cv = versionMapper.selectById(versionId);
                if (cv == null || !cv.getProjectId().equals(projectId)) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "内容版本 " + entry.getKey() + " 不属于当前项目");
                }
                try {
                    Map<String, Object> content = objectMapper.readValue(cv.getContentJson(),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    context.put(entry.getKey(), content);
                } catch (JsonProcessingException e) {
                    context.put(entry.getKey(), cv.getPlainText());
                }
                selected.put(entry.getKey(), versionId);
            }
        }

        // add strategy
        if (request.strategy() != null) {
            context.put("strategy", request.strategy());
        }

        String payloadJson;
        try {
            // canonicalize: sorted keys
            TreeMap<String, Object> sorted = new TreeMap<>(context);
            payloadJson = objectMapper.writeValueAsString(sorted);
        } catch (JsonProcessingException e) {
            payloadJson = "{}";
        }
        String hash = sha256(payloadJson);

        return new ContextSnapshot(selected, payloadJson, hash);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
