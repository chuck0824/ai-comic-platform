package com.aicp.module.contentproject.service;

import com.aicp.module.contentproject.domain.ContentProjectEnums.ContentStatus;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentStatusService {

    private final ContentProjectMapper projectMapper;
    private final ContentUnitMapper unitMapper;

    /**
     * Aggregate content status following V7.1 precedence:
     * needs_revision > reviewing > draft > approved > locked
     */
    public ContentStatus aggregate(List<ContentStatus> unitStatuses) {
        if (unitStatuses.isEmpty()) {
            return ContentStatus.DRAFT;
        }
        if (unitStatuses.contains(ContentStatus.NEEDS_REVISION)) return ContentStatus.NEEDS_REVISION;
        if (unitStatuses.contains(ContentStatus.REVIEWING)) return ContentStatus.REVIEWING;
        if (unitStatuses.contains(ContentStatus.DRAFT)) return ContentStatus.DRAFT;
        if (unitStatuses.contains(ContentStatus.APPROVED)) return ContentStatus.APPROVED;
        return ContentStatus.LOCKED;
    }

    public void recalculateProjectStatus(Long projectId) {
        ContentProject project = projectMapper.selectById(projectId);
        if (project == null || project.getIsDeleted() == 1) return;

        List<ContentUnit> units = unitMapper.selectList(
                new LambdaQueryWrapper<ContentUnit>()
                        .eq(ContentUnit::getProjectId, projectId)
                        .eq(ContentUnit::getIsDeleted, 0));

        List<ContentStatus> statuses = units.stream()
                .map(u -> {
                    try {
                        return ContentStatus.valueOf(u.getStatus().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return ContentStatus.DRAFT;
                    }
                })
                .toList();

        ContentStatus aggregated = aggregate(statuses);
        project.setContentStatus(aggregated.value());
        projectMapper.updateById(project);
    }
}
