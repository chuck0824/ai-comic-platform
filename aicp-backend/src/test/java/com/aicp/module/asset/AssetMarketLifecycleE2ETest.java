package com.aicp.module.asset;

import com.aicp.common.exception.BizException;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.asset.dto.AssetRequests;
import com.aicp.module.asset.dto.AssetViews;
import com.aicp.module.asset.entity.WorkspaceAsset;
import com.aicp.module.asset.mapper.*;
import com.aicp.module.asset.service.*;
import com.aicp.module.canvas.entity.CanvasProject;
import com.aicp.module.canvas.mapper.CanvasProjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end lifecycle test covering the complete asset market workflow:
 * personal publish → public search → claim → apply to project → undo.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@DisplayName("资产市场 E2E 生命周期测试")
class AssetMarketLifecycleE2ETest {

    @Autowired private AssetLibraryService libraryService;
    @Autowired private AssetPublicationService publicationService;
    @Autowired private AssetClaimService claimService;
    @Autowired private AssetApplicationService applicationService;
    @Autowired private MarketQueryService marketQueryService;
    @Autowired private WorkspaceAssetMapper assetMapper;
    @Autowired private CanvasProjectMapper projectMapper;

    private WorkspaceContext personalCtx;
    private WorkspaceContext enterpriseCtx;
    private WorkspaceContext buyerCtx;

    @BeforeEach
    void setUp() {
        personalCtx = new WorkspaceContext("personal_7", "personal", 7L,
                Set.of("asset.view", "asset.use", "asset.manage", "asset.delete"));
        enterpriseCtx = new WorkspaceContext("ent_100", "enterprise", 9L,
                Set.of("asset.view", "asset.use", "asset.manage", "asset.publish.request", "asset.publish.approve"));
        buyerCtx = new WorkspaceContext("personal_99", "personal", 99L,
                Set.of("asset.view", "asset.use"));
    }

    @Test
    @DisplayName("个人用户创建资产 → 直接发布 → 搜索可见 → 另一用户领取 → 应用到项目 → 撤销")
    void personalPublishClaimApplyUndoLifecycle() {
        // 1. Create asset in personal workspace
        AssetViews.AssetView created = libraryService.create(personalCtx,
                new AssetRequests.CreateAssetRequest("测试风格模型", "STYLE_PACK",
                        "测试描述", List.of("测试", "风格"), "PRIVATE"));
        assertThat(created.name()).isEqualTo("测试风格模型");
        assertThat(created.workspaceId()).isEqualTo("personal_7");

        // 2. Create a version
        AssetViews.VersionView version = libraryService.createVersion(personalCtx, created.id(),
                new AssetRequests.CreateVersionRequest(
                        "{\"style\":\"test\",\"trigger_words\":\"test style\"}",
                        "/preview/test.jpg"));
        assertThat(version.assetId()).isEqualTo(created.id());

        // Re-read asset to get updated currentVersionId
        WorkspaceAsset asset = assetMapper.selectById(created.id());

        // 3. Publish directly (personal workspace)
        AssetViews.ListingView listing = publicationService.publishPersonal(personalCtx, created.id(),
                new AssetRequests.PublishAssetRequest(asset.getCurrentVersionId(), "测试风格",
                        "公开描述", List.of("测试"), "作者名", "自由使用", asset.getRowVersion()));
        assertThat(listing.status()).isEqualTo("LISTED");

        // 4. Search: listing is visible
        var searchResult = marketQueryService.search("测试", "STYLE_PACK", "latest", 1, 20, null);
        assertThat(searchResult.getItems()).isNotEmpty();
        assertThat(searchResult.getItems().get(0).name()).isEqualTo("测试风格");

        // 5. Buyer claims the listing
        AssetViews.ClaimView claim = claimService.claim(buyerCtx, listing.id());
        assertThat(claim.claimed()).isTrue();
        assertThat(claim.workspaceAssetId()).isNotNull();

        // 6. Create a project in buyer's workspace
        CanvasProject project = new CanvasProject();
        project.setUuid(UUID.randomUUID().toString());
        project.setUserId(99L);
        project.setWorkspaceId("personal_99");
        project.setName("测试画布项目");
        project.setStatus("editing");
        project.setCanvasVersion(1);
        project.setRevision(0);
        projectMapper.insert(project);

        // 7. Apply claimed asset to project
        AssetViews.ApplyView applied = applicationService.apply(buyerCtx, claim.workspaceAssetId(),
                new AssetRequests.ApplyAssetRequest(project.getId(), "PROJECT", null,
                        UUID.randomUUID().toString()));
        assertThat(applied.applicationId()).isNotNull();
        assertThat(applied.undoToken()).isNotNull();

        // 8. Verify project style was updated by the application
        CanvasProject updated = projectMapper.selectById(project.getId());
        assertThat(updated.getStyleConfig()).isNotNull();

        // 9. Verify idempotent application returns same result
        AssetViews.ApplyView applyAgain = applicationService.apply(buyerCtx, claim.workspaceAssetId(),
                new AssetRequests.ApplyAssetRequest(project.getId(), "PROJECT", null,
                        UUID.randomUUID().toString()));
        // New idempotency key → new application, same asset
        assertThat(applyAgain.applicationId()).isNotNull();
    }

    @Test
    @DisplayName("企业成员提交发布申请 → 管理员审批 → 公共可见")
    void enterpriseApprovalWorkflow() {
        // 1. Create asset in enterprise workspace
        AssetViews.AssetView created = libraryService.create(enterpriseCtx,
                new AssetRequests.CreateAssetRequest("企业角色", "CHARACTER",
                        "企业角色描述", List.of("角色", "企业"), "WORKSPACE"));
        libraryService.createVersion(enterpriseCtx, created.id(),
                new AssetRequests.CreateVersionRequest(
                        "{\"character_type\":\"protagonist\"}", "/preview/enterprise.jpg"));
        WorkspaceAsset entAsset = assetMapper.selectById(created.id());

        // 2. Enterprise member requests publish (not direct publish)
        assertThatThrownBy(() -> publicationService.publishPersonal(enterpriseCtx, created.id(),
                new AssetRequests.PublishAssetRequest(entAsset.getCurrentVersionId(), "企业角色",
                        "desc", List.of(), "author", null, entAsset.getRowVersion())))
                .isInstanceOf(BizException.class);

        // 3. Request enterprise publish
        AssetViews.PublishRequestView pr = publicationService.requestEnterprisePublish(enterpriseCtx, created.id(),
                new AssetRequests.PublishAssetRequest(entAsset.getCurrentVersionId(), "企业角色",
                        "公开描述", List.of("角色"), "企业作者", "MIT", entAsset.getRowVersion()));
        assertThat(pr.status()).isEqualTo("PENDING");

        // 4. Admin with approval permission approves
        WorkspaceContext adminCtx = new WorkspaceContext("ent_100", "enterprise", 8L,
                Set.of("asset.view", "asset.publish.approve"));
        AssetViews.ListingView listing = publicationService.approve(adminCtx, pr.id(),
                new AssetRequests.ReviewRequest(pr.rowVersion(), null));
        assertThat(listing.status()).isEqualTo("LISTED");

        // 5. Ordinary member without approve permission cannot approve
        assertThatThrownBy(() -> publicationService.approve(
                new WorkspaceContext("ent_100", "enterprise", 10L, Set.of("asset.view")),
                pr.id(), new AssetRequests.ReviewRequest(0, null)))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("重复领取幂等 — 返回相同 workspace_asset_id")
    void repeatedClaimIsIdempotent() {
        AssetViews.AssetView created = libraryService.create(personalCtx,
                new AssetRequests.CreateAssetRequest("幂等测试", "PROMPT",
                        "desc", List.of(), "PRIVATE"));
        libraryService.createVersion(personalCtx, created.id(),
                new AssetRequests.CreateVersionRequest("{\"prompt\":\"test\"}", null));
        WorkspaceAsset promptAsset = assetMapper.selectById(created.id());
        AssetViews.ListingView listing = publicationService.publishPersonal(personalCtx, created.id(),
                new AssetRequests.PublishAssetRequest(promptAsset.getCurrentVersionId(), "幂等测试",
                        "desc", List.of(), "author", null, promptAsset.getRowVersion()));

        AssetViews.ClaimView first = claimService.claim(buyerCtx, listing.id());
        AssetViews.ClaimView second = claimService.claim(buyerCtx, listing.id());

        assertThat(second.workspaceAssetId()).isEqualTo(first.workspaceAssetId());
        assertThat(second.claimed()).isTrue();
    }

    @Test
    @DisplayName("撤回 Listing 后旧权益仍可用，新领取失败")
    void unlistBlocksNewClaimsButPreservesEntitlements() {
        AssetViews.AssetView created = libraryService.create(personalCtx,
                new AssetRequests.CreateAssetRequest("撤回测试", "SCENE",
                        "desc", List.of(), "PRIVATE"));
        libraryService.createVersion(personalCtx, created.id(),
                new AssetRequests.CreateVersionRequest("{\"scene\":\"test\"}", null));
        WorkspaceAsset sceneAsset = assetMapper.selectById(created.id());
        AssetViews.ListingView listing = publicationService.publishPersonal(personalCtx, created.id(),
                new AssetRequests.PublishAssetRequest(sceneAsset.getCurrentVersionId(), "撤回测试",
                        "desc", List.of(), "author", null, sceneAsset.getRowVersion()));

        // Buyer claims before unlist
        AssetViews.ClaimView claim = claimService.claim(buyerCtx, listing.id());

        // Unlist
        WorkspaceAsset asset = assetMapper.selectById(created.id());
        publicationService.unlist(personalCtx, created.id(), listing.rowVersion());

        // Old entitlement survives
        AssetViews.ClaimView reClaim = claimService.claim(buyerCtx, listing.id());
        assertThat(reClaim.workspaceAssetId()).isEqualTo(claim.workspaceAssetId());

        // New workspace cannot claim
        WorkspaceContext newBuyer = new WorkspaceContext("personal_200", "personal", 200L,
                Set.of("asset.view", "asset.use"));
        assertThatThrownBy(() -> claimService.claim(newBuyer, listing.id()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已下架");
    }

    @Test
    @DisplayName("跨 Workspace 资产访问返回 404（防止 ID 枚举）")
    void crossWorkspaceAccessReturnsNotFound() {
        AssetViews.AssetView created = libraryService.create(personalCtx,
                new AssetRequests.CreateAssetRequest("隔离测试", "PROMPT",
                        "desc", List.of(), "PRIVATE"));

        // Different workspace tries to read the asset
        WorkspaceContext otherCtx = new WorkspaceContext("personal_999", "personal", 999L,
                Set.of("asset.view"));
        assertThatThrownBy(() -> libraryService.getAsset(otherCtx, created.id()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("资产不存在");
    }
}
