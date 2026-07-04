package com.aicp.module.enterprise.controller;

import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.enterprise.entity.EnterpriseAuditIndex;
import com.aicp.module.enterprise.mapper.EnterpriseAuditIndexMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/enterprise")
@RequiredArgsConstructor
public class EnterpriseAuditController {

    private final EnterpriseAuditIndexMapper mapper;

    @GetMapping("/audit-events")
    public Page<EnterpriseAuditIndex> listAuditEvents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String objectType,
            HttpServletRequest request) {
        var ctx = WorkspaceContext.get(request);
        var qw = new LambdaQueryWrapper<EnterpriseAuditIndex>()
                .eq(EnterpriseAuditIndex::getWorkspaceId, ctx.workspaceId());
        if (actorUserId != null) qw.eq(EnterpriseAuditIndex::getActorUserId, actorUserId);
        if (action != null) qw.eq(EnterpriseAuditIndex::getAction, action);
        if (objectType != null) qw.eq(EnterpriseAuditIndex::getObjectType, objectType);
        qw.orderByDesc(EnterpriseAuditIndex::getCreatedAt);
        return mapper.selectPage(new Page<>(page, size), qw);
    }
}
