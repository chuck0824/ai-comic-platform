package com.aicp.module.asset.service;

import com.aicp.module.asset.entity.WorkspaceAsset;
import com.aicp.module.asset.mapper.WorkspaceAssetMapper;
import com.aicp.module.generation.entity.PlatformAsset;
import com.aicp.module.generation.mapper.PlatformAssetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class AssetMigrationServiceTest {

    @Autowired
    private AssetMigrationService migrationService;

    @Autowired
    private PlatformAssetMapper platformAssetMapper;

    @Autowired
    private WorkspaceAssetMapper workspaceAssetMapper;

    @Test
    void migratesPlatformAssetToWorkspaceAsset() {
        PlatformAsset pa = new PlatformAsset();
        pa.setUuid("test-migrate-uuid");
        pa.setName("测试角色");
        pa.setType("image");
        pa.setFileUrl("https://storage.example.com/test.png");
        pa.setOwnerUserId(42L);
        pa.setFavorite(1);
        pa.setFileSize(1024L);
        pa.setWidth(512);
        pa.setHeight(512);
        platformAssetMapper.insert(pa);

        var result = migrationService.migrateAfter(pa.getId() - 1, 10);
        assertThat(result.migrated()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);

        WorkspaceAsset asset = workspaceAssetMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceAsset>()
                        .eq(WorkspaceAsset::getLegacyPlatformAssetId, pa.getId()));
        assertThat(asset).isNotNull();
        assertThat(asset.getWorkspaceId()).isEqualTo("personal_42");
        assertThat(asset.getName()).isEqualTo("测试角色");
        assertThat(asset.getAssetType()).isEqualTo("CHARACTER");
        assertThat(asset.getMediaType()).isEqualTo("IMAGE");
        assertThat(asset.getCurrentVersionId()).isNotNull();
    }

    @Test
    void migrationIsIdempotent() {
        PlatformAsset pa = new PlatformAsset();
        pa.setUuid("test-idempotent-uuid");
        pa.setName("重复迁移测试");
        pa.setType("video");
        pa.setOwnerUserId(7L);
        platformAssetMapper.insert(pa);

        // First run
        var r1 = migrationService.migrateAfter(pa.getId() - 1, 10);
        assertThat(r1.migrated()).isEqualTo(1);

        // Second run — same batch should skip already-migrated rows
        var r2 = migrationService.migrateAfter(pa.getId() - 1, 10);
        assertThat(r2.migrated()).isEqualTo(0);
        assertThat(r2.skipped()).isEqualTo(1);

        Long count = workspaceAssetMapper.selectCount(
                new LambdaQueryWrapper<WorkspaceAsset>()
                        .eq(WorkspaceAsset::getLegacyPlatformAssetId, pa.getId()));
        assertThat(count).isEqualTo(1);
    }

    @Test
    void preservesOriginalUuid() {
        PlatformAsset pa = new PlatformAsset();
        pa.setUuid("preserved-uuid-12345");
        pa.setName("UUID保留测试");
        pa.setType("audio");
        pa.setOwnerUserId(99L);
        platformAssetMapper.insert(pa);

        migrationService.migrateAfter(pa.getId() - 1, 10);

        WorkspaceAsset asset = workspaceAssetMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceAsset>()
                        .eq(WorkspaceAsset::getLegacyPlatformAssetId, pa.getId()));
        assertThat(asset).isNotNull();
        assertThat(asset.getUuid()).isEqualTo("preserved-uuid-12345");
    }

    @Test
    void returnsEmptyResultForNoNewRows() {
        var result = migrationService.migrateAfter(Long.MAX_VALUE, 10);
        assertThat(result.scanned()).isEqualTo(0);
        assertThat(result.migrated()).isEqualTo(0);
    }
}
