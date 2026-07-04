package com.aicp.module.enterprise.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.enterprise.entity.EnterprisePurchaseBudget;
import com.aicp.module.enterprise.entity.EnterprisePurchaseBudgetEntry;
import com.aicp.module.enterprise.mapper.EnterprisePurchaseBudgetEntryMapper;
import com.aicp.module.enterprise.mapper.EnterprisePurchaseBudgetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/enterprise")
@RequiredArgsConstructor
public class EnterpriseBudgetController {

    private final EnterprisePurchaseBudgetMapper budgetMapper;
    private final EnterprisePurchaseBudgetEntryMapper entryMapper;

    @GetMapping("/budgets")
    public ApiResponse<?> listBudgets(HttpServletRequest request) {
        var ctx = WorkspaceContext.get(request);
        var budgets = budgetMapper.selectList(new LambdaQueryWrapper<EnterprisePurchaseBudget>()
                .eq(EnterprisePurchaseBudget::getWorkspaceId, ctx.workspaceId())
                .orderByDesc(EnterprisePurchaseBudget::getPeriodMonth));
        return ApiResponse.success(budgets);
    }

    @GetMapping("/budget-entries")
    public ApiResponse<?> listEntries(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       HttpServletRequest request) {
        var ctx = WorkspaceContext.get(request);
        var result = entryMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EnterprisePurchaseBudgetEntry>()
                        .eq(EnterprisePurchaseBudgetEntry::getWorkspaceId, ctx.workspaceId())
                        .orderByDesc(EnterprisePurchaseBudgetEntry::getCreatedAt));
        return ApiResponse.success(result);
    }
}
