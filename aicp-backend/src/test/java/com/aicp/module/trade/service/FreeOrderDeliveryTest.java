package com.aicp.module.trade.service;

import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.trade.domain.TradeEnums.OrderStatus;
import com.aicp.module.trade.dto.TradeRequests.CreateOrder;
import com.aicp.module.trade.dto.TradeViews.OrderView;
import com.aicp.module.trade.entity.ScriptListing;
import com.aicp.module.trade.entity.ListingLicenseOption;
import com.aicp.module.trade.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class FreeOrderDeliveryTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ScriptListingMapper listingMapper;

    @Autowired
    private ListingLicenseOptionMapper licenseOptionMapper;

    @Autowired
    private ScriptEntitlementMapper entitlementMapper;

    @Autowired
    private PurchasedScriptCopyMapper copyMapper;

    @Autowired
    private TradeOrderMapper orderMapper;

    private WorkspaceContext buyerCtx;
    private Long listingId;

    @BeforeEach
    void setUp() {
        buyerCtx = new WorkspaceContext("ws_buyer_1", "PERSONAL", 200L, Set.of("trade.purchase"));

        // Seed a LISTED listing with a FREE license option
        ScriptListing listing = new ScriptListing();
        listing.setWorkspaceId("ws_seller_1");
        listing.setSellerUserId(100L);
        listing.setScriptId(10L);
        listing.setScriptVersionId(1L);
        listing.setTitle("Free Test Script");
        listing.setSynopsis("A free test script");
        listing.setReviewStatus("LISTED");
        listing.setListingStatus("LISTED");
        listing.setPreviewEpisodeCount(1);
        listingMapper.insert(listing);
        listingId = listing.getId();

        ListingLicenseOption option = new ListingLicenseOption();
        option.setListingId(listingId);
        option.setLicenseType("FREE");
        option.setPriceCents(0L);
        option.setAgreementText("Free license terms");
        option.setAgreementVersion("1.0");
        option.setEnabled(1);
        licenseOptionMapper.insert(option);
    }

    @Test
    void freeClaimCreatesOneOrderEntitlementAndCopy() {
        CreateOrder request = new CreateOrder(listingId, "FREE", "claim-001");

        OrderView first = orderService.create(buyerCtx, request);
        OrderView retry = orderService.create(buyerCtx, request);

        // Idempotent: same orderNo returned
        assertThat(retry.orderNo()).isEqualTo(first.orderNo());
        assertThat(first.status()).isEqualTo(OrderStatus.FULFILLED.name());

        // One order for this buyer
        long orderCount = orderMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                        com.aicp.module.trade.entity.TradeOrder>()
                        .eq(com.aicp.module.trade.entity.TradeOrder::getBuyerWorkspaceId,
                                buyerCtx.workspaceId()));
        assertThat(orderCount).isEqualTo(1);

        // One entitlement for this workspace
        long entCount = entitlementMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                        com.aicp.module.trade.entity.ScriptEntitlement>()
                        .eq(com.aicp.module.trade.entity.ScriptEntitlement::getBeneficiaryWorkspaceId,
                                buyerCtx.workspaceId()));
        assertThat(entCount).isEqualTo(1);

        // One copy for this workspace
        long copyCount = copyMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                        com.aicp.module.trade.entity.PurchasedScriptCopy>()
                        .eq(com.aicp.module.trade.entity.PurchasedScriptCopy::getWorkspaceId,
                                buyerCtx.workspaceId()));
        assertThat(copyCount).isEqualTo(1);
    }

    @Test
    void freeOrderAutoFulfillsImmediately() {
        CreateOrder request = new CreateOrder(listingId, "FREE", "claim-002");
        OrderView result = orderService.create(buyerCtx, request);

        assertThat(result.status()).isEqualTo(OrderStatus.FULFILLED.name());
        assertThat(result.totalAmountCents()).isEqualTo(0L);
        assertThat(result.fulfilledAt()).isNotNull();
    }

    @Test
    void differentIdempotencyKeyCreatesSeparateOrder() {
        OrderView first = orderService.create(buyerCtx,
                new CreateOrder(listingId, "FREE", "claim-a"));
        OrderView second = orderService.create(buyerCtx,
                new CreateOrder(listingId, "FREE", "claim-b"));

        assertThat(second.orderNo()).isNotEqualTo(first.orderNo());
    }
}
