package com.aicp.module.script.service;

import com.aicp.common.storage.ObjectStorageService;
import com.aicp.common.storage.StorageObjectRef;
import com.aicp.common.storage.StorageRefCodec;
import com.aicp.common.storage.StorageUploadRequest;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.script.entity.*;
import com.aicp.module.script.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {

    private final ScriptUploadFileMapper uploadFileMapper;
    private final ScriptMapper scriptMapper;
    private final ScriptEpisodeMapper episodeMapper;
    private final ObjectStorageService objectStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 集数分隔正则 */
    private static final Pattern EPISODE_PATTERN = Pattern.compile(
            "^[\\s]*第[\\s]*([\\d一二三四五六七八九十百]+)[\\s]*[集章节][\\s]*(.*)$",
            Pattern.MULTILINE);

    /**
     * 接收上传文件，保存到对象存储，异步解析
     */
    @Transactional
    public Map<String, Object> handleUpload(MultipartFile file, String title) throws IOException {
        Long userId = SecurityUtil.requireCurrentUserId();

        String originalName = file.getOriginalFilename();
        String fileType = getFileType(originalName);
        if (!"txt".equals(fileType) && !"docx".equals(fileType)) {
            throw new IllegalArgumentException("仅支持 .txt 和 .docx 格式");
        }

        String key = "scripts/" + userId + "/" + UUID.randomUUID().toString().replace("-", "")
                + "_" + (originalName == null ? "script." + fileType : originalName.replaceAll("[^a-zA-Z0-9._-]", "_"));
        StorageObjectRef ref = objectStorageService.upload(new StorageUploadRequest(
                key,
                file.getInputStream(),
                file.getSize(),
                file.getContentType() == null ? "application/octet-stream" : file.getContentType()));

        ScriptUploadFile uploadFile = new ScriptUploadFile();
        uploadFile.setUserId(userId);
        uploadFile.setFileName(originalName);
        uploadFile.setFileType(fileType);
        uploadFile.setFileSize(file.getSize());
        uploadFile.setStoragePath(StorageRefCodec.encode(ref));
        uploadFile.setParseStatus("pending");
        uploadFileMapper.insert(uploadFile);

        parseFile(uploadFile.getId(), title);

        return Map.of(
                "upload_id", uploadFile.getId(),
                "file_name", originalName,
                "parse_status", "pending",
                "storage_uri", uploadFile.getStoragePath(),
                "message", "文件已上传，正在解析"
        );
    }

    /**
     * 异步解析文件：检测集数边界 → 创建 Script + ScriptEpisode
     */
    @Async("genTaskExecutor")
    @Transactional
    public void parseFile(Long uploadId, String title) {
        ScriptUploadFile upload = uploadFileMapper.selectById(uploadId);
        if (upload == null) return;
        upload.setParseStatus("parsing");
        uploadFileMapper.updateById(upload);

        try {
            String content = readFileContent(upload.getStoragePath(), upload.getFileType());
            List<EpisodeBlock> episodes = splitEpisodes(content);

            Script script = new Script();
            script.setUuid("scr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            script.setTitle(title != null && !title.isBlank() ? title : extractTitle(upload.getFileName()));
            script.setAuthorUserId(upload.getUserId());
            script.setOwnerUserId(upload.getUserId());
            script.setEpisodeCount(episodes.size());
            script.setTotalWords(content.length());
            script.setSynopsis(episodes.isEmpty() ? content.substring(0, Math.min(500, content.length())) : episodes.get(0).content.substring(0, Math.min(200, episodes.get(0).content.length())));
            script.setSource("uploaded");
            script.setStatus("draft");
            scriptMapper.insert(script);

            int epNum = 1;
            for (EpisodeBlock block : episodes) {
                ScriptEpisode episode = new ScriptEpisode();
                episode.setScriptId(script.getId());
                episode.setEpisodeNumber(epNum);
                episode.setTitle(block.title != null && !block.title.isBlank() ? block.title : "第" + epNum + "集");
                episode.setContent(block.content);
                episode.setWordCount(block.content.length());
                episode.setStatus("draft");
                episodeMapper.insert(episode);
                epNum++;
            }

            upload.setScriptId(script.getId());
            upload.setParseStatus("completed");
            upload.setEpisodeCount(episodes.size());
            upload.setTotalWords(content.length());
            upload.setParseResult(objectMapper.writeValueAsString(Map.of(
                    "title", script.getTitle(),
                    "episodes", episodes.size(),
                    "total_words", content.length()
            )));
            uploadFileMapper.updateById(upload);

            log.info("剧本解析完成: scriptId={}, episodes={}", script.getId(), episodes.size());

        } catch (Exception e) {
            log.error("剧本解析失败: uploadId={}", uploadId, e);
            upload.setParseStatus("failed");
            upload.setErrorMsg(e.getMessage());
            uploadFileMapper.updateById(upload);
        }
    }

    public Map<String, Object> getUploadStatus(Long uploadId) {
        ScriptUploadFile upload = uploadFileMapper.selectById(uploadId);
        if (upload == null) return Map.of("error", "上传记录不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("upload_id", upload.getId());
        result.put("script_id", upload.getScriptId());
        result.put("parse_status", upload.getParseStatus());
        result.put("episode_count", upload.getEpisodeCount());
        result.put("total_words", upload.getTotalWords());
        result.put("error_msg", upload.getErrorMsg());
        return result;
    }

    private String getFileType(String fileName) {
        if (fileName == null) return null;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".txt")) return "txt";
        if (lower.endsWith(".docx")) return "docx";
        return lower.substring(lower.lastIndexOf('.') + 1);
    }

    private String readFileContent(String path, String fileType) throws IOException {
        if (StorageRefCodec.isEncoded(path)) {
            StorageObjectRef ref = StorageRefCodec.decode(path);
            if ("txt".equals(fileType)) {
                try (InputStream in = objectStorageService.openStream(ref)) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            if ("docx".equals(fileType)) {
                Path temp = Files.createTempFile("aicp-script-", ".docx");
                try (InputStream in = objectStorageService.openStream(ref)) {
                    Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                    return readDocxPlainText(temp.toString());
                } finally {
                    Files.deleteIfExists(temp);
                }
            }
            return "";
        }

        // 兼容旧本地路径
        if ("txt".equals(fileType)) {
            return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
        }
        if ("docx".equals(fileType)) {
            return readDocxPlainText(path);
        }
        return "";
    }

    private String readDocxPlainText(String path) throws IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(path)) {
            var entry = zip.getEntry("word/document.xml");
            if (entry == null) throw new IOException("无效的 .docx 文件：缺少 word/document.xml");
            try (InputStream is = zip.getInputStream(entry)) {
                String xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return xml.replaceAll("<[^>]+>", " ")
                          .replaceAll("\\s+", " ")
                          .trim();
            }
        } catch (Exception e) {
            throw new IOException("无法解析 .docx 文件: " + e.getMessage());
        }
    }

    private List<EpisodeBlock> splitEpisodes(String content) {
        List<EpisodeBlock> episodes = new ArrayList<>();
        String[] lines = content.split("\\n");
        EpisodeBlock current = null;

        for (String line : lines) {
            var matcher = EPISODE_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                if (current != null) episodes.add(current);
                current = new EpisodeBlock();
                current.title = matcher.group(2).trim();
                current.content = line + "\n";
            } else if (current != null) {
                current.content += line + "\n";
            } else {
                if (current == null) {
                    current = new EpisodeBlock();
                    current.title = "序言";
                    current.content = "";
                }
                current.content += line + "\n";
            }
        }
        if (current != null && !current.content.isBlank()) {
            episodes.add(current);
        }

        if (episodes.isEmpty()) {
            EpisodeBlock single = new EpisodeBlock();
            single.title = "完整剧本";
            single.content = content;
            episodes.add(single);
        }

        return episodes;
    }

    private String extractTitle(String fileName) {
        return fileName.replaceAll("\\.(txt|docx)$", "").replaceAll("[_\\-]", " ");
    }

    private static class EpisodeBlock {
        String title;
        String content = "";
    }
}
