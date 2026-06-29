package com.aicp.module.contentproject.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.entity.UploadFile;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.aicp.module.contentproject.mapper.UploadFileMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentUploadService {

    private final UploadFileMapper uploadMapper;
    private final ContentUnitMapper unitMapper;
    private final ContentVersionMapper versionMapper;
    private final AiRouter aiRouter;
    private final ObjectMapper objectMapper;

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "text/plain", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    @Transactional
    public UploadFile upload(Long userId, MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件大小不能超过20MB");
        }
        String contentType = file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅支持TXT和DOCX格式");
        }

        UploadFile uf = new UploadFile();
        uf.setUuid(UUID.randomUUID().toString());
        uf.setUserId(userId);
        uf.setOriginalName(file.getOriginalFilename());
        uf.setFileType(contentType.contains("officedocument") ? "docx" : "txt");
        uf.setFileSize(file.getSize());
        uf.setParseStatus("pending");
        uploadMapper.insert(uf);

        // Async parsing
        try {
            String text = parseFile(file);
            if (text.length() > 2_000_000) {
                uf.setParseStatus("failed");
                uf.setErrorMessage("文本超过200万字符上限");
            } else {
                uf.setParsedText(text);
                uf.setParseStatus("completed");
            }
        } catch (Exception e) {
            log.error("File parse failed: {}", uf.getId(), e);
            uf.setParseStatus("failed");
            uf.setErrorMessage(e.getMessage());
        }
        uploadMapper.updateById(uf);
        return uf;
    }

    private String parseFile(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (contentType != null && contentType.contains("officedocument")) {
            return parseDocx(file);
        } else {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
    }

    private String parseDocx(MultipartFile file) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            StringBuilder sb = new StringBuilder();
            doc.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));
            return sb.toString().trim();
        }
    }

    public UploadFile getUpload(Long uploadId) {
        UploadFile uf = uploadMapper.selectById(uploadId);
        if (uf == null) throw new BizException(ErrorCode.NOT_FOUND);
        return uf;
    }

    /**
     * AI extract: characters, structure from parsed text.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> aiExtract(Long uploadId) {
        UploadFile uf = uploadMapper.selectById(uploadId);
        if (uf == null || !"completed".equals(uf.getParseStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件尚未解析完成");
        }

        String systemPrompt = """
            你是一位资深剧本分析师。请从以下文本中提取：
            1. 主要人物（姓名、角色定位、关系）
            2. 章节/集数划分建议
            3. 核心冲突和故事线
            输出JSON格式：
            {"characters": [{"name":"", "role":"", "traits":[]}], "chapters": [{"title":"", "summary":""}], "core_conflict": ""}
            """;

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("system_prompt", systemPrompt);
        params.put("prompt", ellipsis(uf.getParsedText(), 8000));
        params.put("temperature", 0.3);
        params.put("max_tokens", 4096);

        Map<String, Object> result = aiRouter.chatCompletion(params);
        String text = extractText(result);
        try {
            String json = text;
            if (text.contains("```json")) {
                int s = text.indexOf("```json") + 7;
                int e = text.indexOf("```", s);
                if (e > s) json = text.substring(s, e).trim();
            }
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("raw", text);
        }
    }

    /**
     * Confirm import: create content units + versions from parsed chapters.
     */
    @Transactional
    public int confirmImport(Long userId, Long projectId, Long uploadId, List<Map<String, Object>> chapters) {
        UploadFile uf = uploadMapper.selectById(uploadId);
        if (uf == null) throw new BizException(ErrorCode.NOT_FOUND);

        int created = 0;
        for (Map<String, Object> ch : chapters) {
            String title = (String) ch.getOrDefault("title", "未命名");
            String content = (String) ch.getOrDefault("content",
                    (String) ch.getOrDefault("summary", ""));
            int displayNo = chapters.indexOf(ch) + 1;

            ContentUnit unit = new ContentUnit();
            unit.setStableKey("CU_" + UUID.randomUUID().toString().replace("-", ""));
            unit.setProjectId(projectId);
            unit.setUnitType("episode");
            unit.setDisplayNo(displayNo);
            unit.setTitle(title);
            unit.setStatus("draft");
            unit.setRevision(0);
            unit.setIsDeleted(0);
            unitMapper.insert(unit);

            ContentVersion cv = new ContentVersion();
            cv.setProjectId(projectId);
            cv.setContentUnitId(unit.getId());
            cv.setVersionNo(1);
            cv.setStatus("draft");
            cv.setContentJson(objectMapper.createObjectNode().put("content", content).toString());
            cv.setPlainText(content);
            cv.setSource("uploaded");
            cv.setContentHash(sha256(content));
            cv.setCreatedBy(userId);
            versionMapper.insert(cv);

            unit.setCurrentVersionId(cv.getId());
            unitMapper.updateById(unit);
            created++;
        }
        return created;
    }

    private String extractText(Map<String, Object> result) {
        Object choices = result.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map) {
                Object message = ((Map<String, Object>) first).get("message");
                if (message instanceof Map) {
                    Object c = ((Map<String, Object>) message).get("content");
                    if (c != null) return String.valueOf(c);
                }
            }
        }
        return result.toString();
    }

    private String ellipsis(String s, int max) { return s != null && s.length() > max ? s.substring(0, max) + "..." : s; }

    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) { return "" + input.hashCode(); }
    }
}
