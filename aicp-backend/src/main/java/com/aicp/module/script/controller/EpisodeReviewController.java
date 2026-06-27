package com.aicp.module.script.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.module.script.service.EpisodeReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/script/review")
@RequiredArgsConstructor
public class EpisodeReviewController {

    private final EpisodeReviewService reviewService;

    @PostMapping("/preview")
    public ApiResponse<Map<String, Object>> reviewPreview(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(reviewService.reviewPreview(body));
    }

    @PostMapping("/episodes/{episodeId}")
    public ApiResponse<Map<String, Object>> reviewEpisode(@PathVariable Long episodeId,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> result = reviewService.reviewEpisode(episodeId, body == null ? Map.of() : body);
        if (result.containsKey("error")) {
            return ApiResponse.error(47020, String.valueOf(result.get("message")));
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/episodes/{episodeId}")
    public ApiResponse<Map<String, Object>> getLatestReport(@PathVariable Long episodeId) {
        return ApiResponse.success(reviewService.getLatestReport(episodeId));
    }

    @PostMapping("/episodes/{episodeId}/approve")
    public ApiResponse<Map<String, Object>> approveEpisode(@PathVariable Long episodeId) {
        return ApiResponse.success(reviewService.approveEpisode(episodeId));
    }
}
