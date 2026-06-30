package com.aicp.module.canvas.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.canvas.dto.CanvasProjectViews.ContinueWorkingItem;
import com.aicp.module.canvas.service.CanvasProjectManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final CanvasProjectManagementService managementService;

    @GetMapping("/continue-working")
    public ApiResponse<List<ContinueWorkingItem>> getContinueWorking() {
        return ApiResponse.success(managementService.getContinueWorking(
                SecurityUtil.requireCurrentUserId()));
    }
}
