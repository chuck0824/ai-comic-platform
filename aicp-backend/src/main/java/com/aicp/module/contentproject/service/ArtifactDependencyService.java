package com.aicp.module.contentproject.service;

import com.aicp.module.contentproject.entity.ArtifactDependency;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ArtifactDependencyMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtifactDependencyService {

    private final ArtifactDependencyMapper dependencyMapper;
    private final ContentVersionMapper versionMapper;

    /**
     * When a new source version is created, mark dependent targets stale
     * when the stored source_hash differs from the new version's hash.
     */
    @Transactional
    public void invalidateStaleDependencies(Long sourceVersionId, String newHash) {
        List<ArtifactDependency> deps = dependencyMapper.selectList(
                new LambdaQueryWrapper<ArtifactDependency>()
                        .eq(ArtifactDependency::getSourceVersionId, sourceVersionId));

        for (ArtifactDependency dep : deps) {
            if (!dep.getSourceHash().equals(newHash)) {
                dep.setSyncStatus("needs_sync");
                dependencyMapper.updateById(dep);
            }
        }
    }

    /**
     * Record a dependency between source and target versions.
     */
    @Transactional
    public void recordDependency(Long projectId, String sourceType, Long sourceVersionId,
                                  String targetType, Long targetVersionId,
                                  String dependencyType) {
        ContentVersion source = versionMapper.selectById(sourceVersionId);
        if (source == null) return;

        // check for existing dependency
        ArtifactDependency existing = dependencyMapper.selectOne(
                new LambdaQueryWrapper<ArtifactDependency>()
                        .eq(ArtifactDependency::getSourceVersionId, sourceVersionId)
                        .eq(ArtifactDependency::getTargetVersionId, targetVersionId)
                        .eq(ArtifactDependency::getDependencyType, dependencyType));
        if (existing != null) return;

        ArtifactDependency dep = new ArtifactDependency();
        dep.setProjectId(projectId);
        dep.setSourceType(sourceType);
        dep.setSourceVersionId(sourceVersionId);
        dep.setTargetType(targetType);
        dep.setTargetVersionId(targetVersionId);
        dep.setDependencyType(dependencyType);
        dep.setSourceHash(source.getContentHash());
        dep.setSyncStatus("current");
        dependencyMapper.insert(dep);
    }
}
