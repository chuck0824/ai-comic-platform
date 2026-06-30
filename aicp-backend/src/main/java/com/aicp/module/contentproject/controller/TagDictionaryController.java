package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.module.contentproject.entity.TagDictionary;
import com.aicp.module.contentproject.mapper.TagDictionaryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TagDictionaryController {

    private final TagDictionaryMapper tagDictionaryMapper;

    @GetMapping("/tag-dictionary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDictionary() {
        List<TagDictionary> all = tagDictionaryMapper.selectList(
                new LambdaQueryWrapper<TagDictionary>()
                        .eq(TagDictionary::getIsActive, 1)
                        .orderByAsc(TagDictionary::getAxis, TagDictionary::getSortOrder));

        Map<String, List<Map<String, String>>> axes = new LinkedHashMap<>();
        for (TagDictionary entry : all) {
            axes.computeIfAbsent(entry.getAxis(), k -> new java.util.ArrayList<>())
                    .add(Map.of("value", entry.getTagValue(), "label", entry.getTagLabel()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("genres", axes.getOrDefault("genre", List.of()));
        result.put("plots", axes.getOrDefault("plot", List.of()));
        result.put("tones", axes.getOrDefault("tone", List.of()));
        result.put("settings", axes.getOrDefault("setting", List.of()));
        // version 通过数据行数简单派生，后续可改为显式版本字段
        result.put("version", all.size());

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
