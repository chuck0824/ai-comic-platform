package com.aicp.module.contentproject.service;

import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** One authoritative visibility and selection policy for downstream content consumers. */
@Component
@RequiredArgsConstructor
public class ContentVersionSelector {

    private final ContentVersionMapper versionMapper;

    public ContentVersion resolvePublic(ContentUnit unit) {
        if (unit == null || unit.getId() == null) return null;
        if (unit.getCurrentVersionId() != null) {
            ContentVersion current = versionMapper.selectById(unit.getCurrentVersionId());
            return belongsTo(current, unit.getId()) && isPublic(current) ? current : null;
        }
        return versionMapper.selectList(new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unit.getId())
                        .notIn(ContentVersion::getStatus, "candidate", "discarded")
                        .orderByDesc(ContentVersion::getVersionNo))
                .stream()
                .filter(ContentVersionSelector::isPublic)
                .findFirst()
                .orElse(null);
    }

    public static boolean isPublic(ContentVersion version) {
        if (version == null) return false;
        return !"candidate".equals(version.getStatus()) && !"discarded".equals(version.getStatus());
    }

    private static boolean belongsTo(ContentVersion version, Long unitId) {
        return version != null && unitId.equals(version.getContentUnitId());
    }
}
