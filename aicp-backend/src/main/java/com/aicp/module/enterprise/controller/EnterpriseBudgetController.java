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

import java.time.LocalDateTime;
import java.util.Map;

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

    @PutMapping("/budgets")
    public ApiResponse<?> setBudget(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        var ctx = WorkspaceContext.get(request);
        ctx.require("enterprise.budget.manage");

        String subjectType = (String) body.getOrDefault("subjectType", "WORKSPACE");
        String subjectId = (String) body.getOrDefault("subjectId", ctx.workspaceId());
        String periodMonth = (String) body.get("periodMonth");
        Long amountCents = body.get("amountCents") != null
                ? ((Number) body.get("amountCents")).longValue() : 0L;
        Long singleLimitCents = body.get("singleLimitCents") != null
                ? ((Number) body.get("singleLimitCents")).longValue() : 0L;

        if (periodMonth == null) {
            return ApiResponse.error(400, "periodMonth is required (YYYY-MM)");
        }

        var existing = budgetMapper.selectOne(new LambdaQueryWrapper<EnterprisePurchaseBudget>()
                .eq(EnterprisePurchaseBudget::getWorkspaceId, ctx.workspaceId())
                .eq(EnterprisePurchaseBudget::getSubjectType, subjectType)
                .eq(EnterprisePurchaseBudget::getSubjectId, subjectId)
                .eq(EnterprisePurchaseBudget::getPeriodMonth, periodMonth));

        if (existing != null) {
            existing.setAmountCents(amountCents);
            existing.setSingleLimitCents(singleLimitCents);
            existing.setUpdatedAt(LocalDateTime.now());
            budgetMapper.updateById(existing);
            return ApiResponse.success(existing);
        }

        var budget = new EnterprisePurchaseBudget();
        budget.setWorkspaceId(ctx.workspaceId());
        budget.setSubjectType(subjectType);
        budget.setSubjectId(subjectId);
        budget.setPeriodMonth(periodMonth);
        budget.setAmountCents(amountCents);
        budget.setSingleLimitCents(singleLimitCents);
        budget.setReservedCents(0L);
        budget.setConsumedCents(0L);
        budget.setRowVersion(0);
        budgetMapper.insert(budget);
        return ApiResponse.success(budget);
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
