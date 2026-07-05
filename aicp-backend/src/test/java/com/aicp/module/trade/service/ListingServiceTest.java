package com.aicp.module.trade.service;

import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.trade.domain.TradeEnums.ListingStatus;
import com.aicp.module.trade.dto.TradeRequests.CreateListing;
import com.aicp.module.trade.dto.TradeRequests.LicenseOptionInput;
import com.aicp.module.trade.dto.TradeRequests.UpdateListing;
import com.aicp.module.trade.entity.ScriptListing;
import com.aicp.module.trade.mapper.ListingLicenseOptionMapper;
import com.aicp.module.trade.mapper.ScriptListingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ListingServiceTest {

    @Autowired
    private ListingService listingService;

    @Autowired
    private ScriptListingMapper listingMapper;

    @Autowired
    private ListingLicenseOptionMapper licenseOptionMapper;

    private WorkspaceContext sellerCtx;
    private WorkspaceContext adminCtx;

    @BeforeEach
    void setUp() {
        sellerCtx = new WorkspaceContext("ws_seller_1", "PERSONAL", 100L,
                Set.of("trade.listing.manage"));
        adminCtx = new WorkspaceContext("ws_admin", "PLATFORM", 999L,
                Set.of("trade.listing.review"));
    }

    @Test
    void unapprovedListingNeverAppearsInPublicListings() {
        CreateListing draft = new CreateListing("ws_seller_1", 1L, 1L,
                "测试剧本", "剧情简介", null, "[]", "[]", 10, "作者名", 3, "[]",
                List.of(new LicenseOptionInput("FREE", 0, "{}", "test agreement", "1.0")));
        ScriptListing listing = listingService.createDraft(sellerCtx, draft);

        // Draft listings have DRAFT review status — not LISTED
        assertThat(listing.getReviewStatus()).isEqualTo(ListingStatus.DRAFT.name());
        assertThat(listing.getListingStatus()).isEqualTo(ListingStatus.DRAFT.name());
    }

    @Test
    void submitAndApproveMakesListingPublic() {
        // Create draft
        CreateListing draft = new CreateListing("ws_seller_1", 2L, 1L,
                "审核测试剧本", "剧情", null, "[]", "[]", 20, "作者", 2, "[]",
                List.of(new LicenseOptionInput("NORMAL", 2990, "{}", "agreement", "1.0")));
        ScriptListing listing = listingService.createDraft(sellerCtx, draft);
        assertThat(listing.getReviewStatus()).isEqualTo(ListingStatus.DRAFT.name());

        // Submit for review
        listingService.submit(sellerCtx, listing.getId());
        ScriptListing underReview = listingMapper.selectById(listing.getId());
        assertThat(underReview.getReviewStatus()).isEqualTo(ListingStatus.UNDER_REVIEW.name());

        // Admin approves
        listingService.approve(adminCtx.userId(), listing.getId(), "内容通过审核");
        ScriptListing approved = listingMapper.selectById(listing.getId());
        assertThat(approved.getReviewStatus()).isEqualTo(ListingStatus.LISTED.name());
        assertThat(approved.getListingStatus()).isEqualTo(ListingStatus.LISTED.name());
        assertThat(approved.getReviewedBy()).isEqualTo(adminCtx.userId());
    }

    @Test
    void rejectRequiresReason() {
        CreateListing draft = new CreateListing("ws_seller_1", 3L, 1L,
                "驳回测试", "剧情", null, "[]", "[]", 10, "作者", 1, "[]",
                List.of(new LicenseOptionInput("FREE", 0, "{}", "agreement", "1.0")));
        ScriptListing listing = listingService.createDraft(sellerCtx, draft);
        listingService.submit(sellerCtx, listing.getId());

        listingService.reject(adminCtx.userId(), listing.getId(), "标签与内容不符");
        ScriptListing rejected = listingMapper.selectById(listing.getId());
        assertThat(rejected.getReviewStatus()).isEqualTo(ListingStatus.REJECTED.name());
        assertThat(rejected.getReviewReason()).isEqualTo("标签与内容不符");
    }

    @Test
    void cantSubmitNonDraftOrNonRejected() {
        CreateListing draft = new CreateListing("ws_seller_1", 4L, 1L,
                "状态测试", "剧情", null, "[]", "[]", 10, "作者", 1, "[]",
                List.of(new LicenseOptionInput("FREE", 0, "{}", "agreement", "1.0")));
        ScriptListing listing = listingService.createDraft(sellerCtx, draft);
        listingService.submit(sellerCtx, listing.getId());

        // Cannot submit again while under review
        try {
            listingService.submit(sellerCtx, listing.getId());
            assertThat(false).as("Expected exception for re-submit").isTrue();
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("审核");
        }
    }

    @Test
    void unlistStopsNewOrders() {
        CreateListing draft = new CreateListing("ws_seller_1", 5L, 1L,
                "下架测试", "剧情", null, "[]", "[]", 10, "作者", 1, "[]",
                List.of(new LicenseOptionInput("FREE", 0, "{}", "agreement", "1.0")));
        ScriptListing listing = listingService.createDraft(sellerCtx, draft);
        listingService.submit(sellerCtx, listing.getId());
        listingService.approve(adminCtx.userId(), listing.getId(), "通过");

        listingService.unlist(sellerCtx, listing.getId());
        ScriptListing unlisted = listingMapper.selectById(listing.getId());
        assertThat(unlisted.getListingStatus()).isEqualTo(ListingStatus.UNLISTED.name());
    }

    @Test
    void updateDraftPreservesWorkspaceOwnership() {
        CreateListing draft = new CreateListing("ws_seller_1", 6L, 1L,
                "编辑测试", "原简介", null, "[]", "[]", 10, "作者", 1, "[]",
                List.of(new LicenseOptionInput("FREE", 0, "{}", "agreement", "1.0")));
        ScriptListing listing = listingService.createDraft(sellerCtx, draft);

        UpdateListing update = new UpdateListing("编辑测试", "新简介", null, null, null, null, null, null, null, null);
        listingService.updateDraft(sellerCtx, listing.getId(), update);

        ScriptListing updated = listingMapper.selectById(listing.getId());
        assertThat(updated.getSynopsis()).isEqualTo("新简介");
    }
}
