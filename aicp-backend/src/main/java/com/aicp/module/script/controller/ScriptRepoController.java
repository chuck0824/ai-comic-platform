package com.aicp.module.script.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.dto.PageResult;
import com.aicp.module.script.service.CharacterExtractService;
import com.aicp.module.script.service.ScriptService;
import com.aicp.module.script.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/v1/script/repo")
@RequiredArgsConstructor
public class ScriptRepoController {

    private final ScriptService scriptService;
    private final UploadService uploadService;
    private final CharacterExtractService characterExtractService;

    @PostMapping("/scripts")
    public ApiResponse<?> createScript(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(scriptService.createScript(body));
    }

    @GetMapping("/scripts")
    public ApiResponse<PageResult<?>> getScripts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String keyword) {
        var result = scriptService.getScripts(page, pageSize, status, genre, keyword);
        return ApiResponse.success(PageResult.of(result.getRecords(), page, pageSize, result.getTotal()));
    }

    @GetMapping("/scripts/{id}")
    public ApiResponse<?> getScript(@PathVariable Long id) {
        var script = scriptService.getScript(id);
        return script == null ? ApiResponse.error(40005, "剧本不存在") : ApiResponse.success(script);
    }

    @PutMapping("/scripts/{id}")
    public ApiResponse<?> updateScript(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        var script = scriptService.updateScript(id, body);
        return script == null ? ApiResponse.error(40005, "剧本不存在") : ApiResponse.success(script);
    }

    @DeleteMapping("/scripts/{id}")
    public ApiResponse<Void> deleteScript(@PathVariable Long id) {
        scriptService.deleteScript(id);
        return ApiResponse.success();
    }

    @PutMapping("/scripts/{id}/tags")
    public ApiResponse<Void> updateTags(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        scriptService.updateTags(id, body);
        return ApiResponse.success();
    }

    @PutMapping("/scripts/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        scriptService.updateStatus(id, body.get("status"));
        return ApiResponse.success();
    }

    @GetMapping("/scripts/{id}/versions")
    public ApiResponse<?> getVersions(@PathVariable Long id) {
        return ApiResponse.success(scriptService.getVersions(id));
    }

    @PostMapping("/scripts/{id}/versions")
    public ApiResponse<Void> createVersion(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        scriptService.createVersion(id, body);
        return ApiResponse.success();
    }

    @PostMapping("/scripts/{id}/versions/{vid}/restore")
    public ApiResponse<Void> restoreVersion(@PathVariable Long id, @PathVariable Long vid) {
        return ApiResponse.success();
    }

    // ===== 章节正文与版本 =====
    @GetMapping("/scripts/{id}/chapters")
    public ApiResponse<?> getChapters(@PathVariable Long id) {
        return ApiResponse.success(scriptService.getChapters(id));
    }

    @PatchMapping("/chapters/{chapterId}")
    public ApiResponse<?> updateChapter(@PathVariable Long chapterId, @RequestBody Map<String, Object> body) {
        var chapter = scriptService.updateChapter(chapterId, body);
        return chapter == null ? ApiResponse.error(47020, "章节不存在") : ApiResponse.success(chapter);
    }

    @GetMapping("/chapters/{chapterId}/versions")
    public ApiResponse<?> getChapterVersions(@PathVariable Long chapterId) {
        return ApiResponse.success(scriptService.getChapterVersions(chapterId));
    }

    @PostMapping("/chapters/{chapterId}/versions")
    public ApiResponse<?> createChapterVersion(@PathVariable Long chapterId, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(scriptService.createChapterVersion(null, chapterId, body));
    }

    // ===== 改编脚本版本 =====
    @GetMapping("/adaptations")
    public ApiResponse<?> getAdaptations(@RequestParam(required = false) Long script_id,
                                         @RequestParam(required = false) Long chapter_version_id,
                                         @RequestParam(required = false) String target_type) {
        return ApiResponse.success(scriptService.getAdaptations(script_id, chapter_version_id, target_type));
    }

    @PostMapping("/adaptations")
    public ApiResponse<?> createAdaptation(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(scriptService.createAdaptation(body));
    }

    @GetMapping("/adaptations/{id}")
    public ApiResponse<?> getAdaptation(@PathVariable Long id) {
        var adaptation = scriptService.getAdaptation(id);
        return adaptation == null ? ApiResponse.error(47030, "改编脚本不存在") : ApiResponse.success(adaptation);
    }

    @PatchMapping("/adaptations/{id}")
    public ApiResponse<?> updateAdaptation(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        var adaptation = scriptService.updateAdaptation(id, body);
        return adaptation == null ? ApiResponse.error(47030, "改编脚本不存在") : ApiResponse.success(adaptation);
    }

    @PostMapping("/adaptations/{id}/lock")
    public ApiResponse<?> lockAdaptation(@PathVariable Long id) {
        var adaptation = scriptService.lockAdaptation(id);
        return adaptation == null ? ApiResponse.error(47030, "改编脚本不存在") : ApiResponse.success(adaptation);
    }

    @GetMapping("/assets")
    public ApiResponse<?> getAssets(@RequestParam(required = false) String type,
                                    @RequestParam(required = false) String maturity) {
        return ApiResponse.success(scriptService.getAssets(type, maturity));
    }

    @PostMapping("/assets/character")
    public ApiResponse<?> createCharacter(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(scriptService.createCharacter(body));
    }

    @PostMapping("/assets/scene")
    public ApiResponse<?> createScene(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(scriptService.createScene(body));
    }

    @PutMapping("/assets/{type}/{id}/maturity")
    public ApiResponse<Void> updateMaturity(@PathVariable String type, @PathVariable String id,
                                             @RequestBody Map<String, String> body) {
        return ApiResponse.success();
    }

    @PutMapping("/assets/{type}/{id}/lock")
    public ApiResponse<Void> lockAsset(@PathVariable String type, @PathVariable String id) {
        return ApiResponse.success();
    }

    // ===== 文件上传 =====
    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> uploadScript(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) {
        try {
            var result = uploadService.handleUpload(file, title);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(40001, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(50001, "文件上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/upload/{uploadId}/status")
    public ApiResponse<Map<String, Object>> getUploadStatus(@PathVariable Long uploadId) {
        return ApiResponse.success(uploadService.getUploadStatus(uploadId));
    }

    // ===== 角色提取 =====
    @GetMapping("/scripts/{id}/characters")
    public ApiResponse<List<Map<String, Object>>> extractCharacters(@PathVariable Long id) {
        return ApiResponse.success(characterExtractService.extractCharacters(id));
    }

    @PostMapping("/scripts/{id}/characters/save")
    public ApiResponse<Map<String, Object>> saveCharactersToWarehouse(@PathVariable Long id) {
        var saved = characterExtractService.saveAllToWarehouse(id);
        return ApiResponse.success(Map.of("saved_count", saved.size(), "characters", saved.stream().map(a -> Map.of("asset_id", a.getAssetId(), "name", a.getName())).toList()));
    }
}
