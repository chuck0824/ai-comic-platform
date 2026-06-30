package com.aicp.module.contentproject.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 作品编辑中心请求 DTO。
 */
public final class WorkEditorRequests {

    private WorkEditorRequests() {}

    public record UpdateTagsRequest(
            String genre,
            @Size(max = 3) List<String> plot,
            @Size(max = 3) List<String> tone,
            String setting,
            Integer revision
    ) {}

    public record UpdateProfileRequest(
            String synopsis,
            String outline,
            Integer revision
    ) {}
}
