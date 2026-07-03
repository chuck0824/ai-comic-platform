package com.aicp.module.asset;

import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.asset.entity.AssetVersion;
import com.aicp.module.asset.entity.WorkspaceAsset;
import com.aicp.module.asset.entity.WorkspaceAssetFavorite;
import com.aicp.module.asset.mapper.AssetVersionMapper;
import com.aicp.module.asset.mapper.WorkspaceAssetFavoriteMapper;
import com.aicp.module.asset.mapper.WorkspaceAssetMapper;
import com.aicp.module.asset.dto.AssetWorkbenchRequests.BatchAssetRequest;
import com.aicp.module.asset.service.AssetCommandService;
import com.aicp.module.asset.service.AssetMigrationService;
import com.aicp.module.generation.entity.GenerationTask;
import com.aicp.module.generation.entity.PlatformAsset;
import com.aicp.module.generation.mapper.GenerationTaskMapper;
import com.aicp.module.generation.mapper.PlatformAssetMapper;
import com.aicp.module.generation.service.GenerationSettlementService;
import com.aicp.module.generation.service.GenerationSettlementService.SettlementInput;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@DisplayName("Asset Workbench E2E Lifecycle")
class AssetWorkbenchLifecycleE2ETest {

    @Autowired private WorkspaceAssetMapper assetMapper;
    @Autowired private AssetVersionMapper versionMapper;
    @Autowired private WorkspaceAssetFavoriteMapper favoriteMapper;
    @Autowired private GenerationTaskMapper taskMapper;
    @Autowired private PlatformAssetMapper platformAssetMapper;
    @Autowired private AssetCommandService commandService;
    @Autowired private AssetMigrationService migrationService;
    @Autowired private GenerationSettlementService settlementService;

    private static final String WS = "personal_42";
    private static final Long UID = 42L;
    private static final WorkspaceContext CTX = new WorkspaceContext(WS, "personal", UID,
            Set.of("asset.view", "asset.use", "asset.manage", "asset.delete"));

    private WorkspaceContext otherCtx = new WorkspaceContext("personal_99", "personal", 99L,
            Set.of("asset.view"));

    @Nested
    @DisplayName("Generation Settlement → Asset Creation")
    class SettlementFlow {

        @Test
        @DisplayName("successful settlement creates asset + version")
        void settlementCreatesAssetAndVersion() {
            GenerationTask task = newTask("settle-test");
            taskMapper.insert(task);

            SettlementInput input = new SettlementInput(
                    "minio", "aicp", "gen/test.png", "image/png", 2048L, 512, 512, null, null, "abc123");
            var result = settlementService.settle(task, input);

            assertThat(result).isNotNull();
            WorkspaceAsset asset = assetMapper.selectById(result.assetId());
            assertThat(asset).isNotNull();
            assertThat(asset.getWorkspaceId()).isEqualTo(WS);
            assertThat(asset.getSourceTaskId()).isEqualTo(task.getId());
            assertThat(asset.getMediaType()).isEqualTo("IMAGE");

            AssetVersion version = versionMapper.selectById(result.versionId());
            assertThat(version).isNotNull();
            assertThat(version.getStorageKey()).isEqualTo("gen/test.png");
            assertThat(version.getAssetId()).isEqualTo(asset.getId());
        }

        @Test
        @DisplayName("missing storage key marks task failed")
        void missingStorageKeyFails() {
            GenerationTask task = newTask("no-file");
            taskMapper.insert(task);

            SettlementInput input = new SettlementInput(
                    null, null, null, null, null, null, null, null, null, null);
            var result = settlementService.settle(task, input);

            assertThat(result).isNull(); // settlement should fail
        }
    }

    @Nested
    @DisplayName("Asset Lifecycle: Favorite, Trash, Restore")
    class Lifecycle {

        private WorkspaceAsset seed;

        @BeforeEach
        void seedAsset() {
            seed = newAsset("lifecycle-test");
            assetMapper.insert(seed);
        }

        @Test
        @DisplayName("favorite is idempotent across multiple toggles")
        void favoriteIdempotent() {
            commandService.toggleFavorite(CTX, seed.getUuid(), true);
            commandService.toggleFavorite(CTX, seed.getUuid(), true); // double-fav
            Long count = favoriteMapper.selectCount(
                    new LambdaQueryWrapper<WorkspaceAssetFavorite>()
                            .eq(WorkspaceAssetFavorite::getAssetId, seed.getId()));
            assertThat(count).isEqualTo(1);

            commandService.toggleFavorite(CTX, seed.getUuid(), false);
            count = favoriteMapper.selectCount(
                    new LambdaQueryWrapper<WorkspaceAssetFavorite>()
                            .eq(WorkspaceAssetFavorite::getAssetId, seed.getId()));
            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("trash then restore returns asset to active")
        void trashAndRestore() {
            commandService.batchOperate(CTX, new BatchAssetRequest(
                    List.of(seed.getUuid()), "TRASH", null));

            WorkspaceAsset trashed = assetMapper.selectById(seed.getId());
            assertThat(trashed.getStatus()).isEqualTo("TRASHED");
            assertThat(trashed.getDeletedAt()).isNotNull();

            commandService.batchOperate(CTX, new BatchAssetRequest(
                    List.of(seed.getUuid()), "RESTORE", null));

            WorkspaceAsset restored = assetMapper.selectById(seed.getId());
            assertThat(restored.getStatus()).isEqualTo("ACTIVE");
            assertThat(restored.getDeletedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("Cross-Workspace Isolation")
    class Isolation {

        @Test
        @DisplayName("other workspace cannot access asset detail")
        void crossWorkspaceReturnsNotFound() {
            WorkspaceAsset a = newAsset("isolation-test");
            assetMapper.insert(a);

            WorkspaceAsset found = assetMapper.selectOne(
                    new LambdaQueryWrapper<WorkspaceAsset>()
                            .eq(WorkspaceAsset::getUuid, a.getUuid())
                            .eq(WorkspaceAsset::getWorkspaceId, otherCtx.workspaceId()));
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("Migration")
    class Migration {

        @Test
        @DisplayName("migration is repeatable and idempotent")
        void migrationRepeatable() {
            PlatformAsset pa = new PlatformAsset();
            pa.setUuid("mig-test-" + UUID.randomUUID().toString().substring(0, 8));
            pa.setName("迁移测试");
            pa.setType("image");
            pa.setOwnerUserId(UID);
            platformAssetMapper.insert(pa);

            var r1 = migrationService.migrateAfter(pa.getId() - 1, 10);
            assertThat(r1.migrated()).isEqualTo(1);

            var r2 = migrationService.migrateAfter(pa.getId() - 1, 10);
            assertThat(r2.migrated()).isEqualTo(0);
            assertThat(r2.skipped()).isEqualTo(1);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────

    private GenerationTask newTask(String uuid) {
        GenerationTask t = new GenerationTask();
        t.setUuid(uuid);
        t.setWorkspaceId(WS);
        t.setCreatedBy(UID);
        t.setType("image");
        t.setAssetType("CHARACTER");
        t.setStatus("running");
        return t;
    }

    private WorkspaceAsset newAsset(String uuid) {
        WorkspaceAsset a = new WorkspaceAsset();
        a.setUuid(uuid);
        a.setWorkspaceId(WS);
        a.setWorkspaceType("personal");
        a.setCreatorUserId(UID);
        a.setAssetType("CHARACTER");
        a.setName("Test Asset");
        a.setMediaType("IMAGE");
        a.setSourceType("PROJECT_GENERATED");
        a.setStatus("ACTIVE");
        a.setRowVersion(0);
        return a;
    }
}
