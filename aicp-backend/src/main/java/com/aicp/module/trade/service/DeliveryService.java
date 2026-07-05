package com.aicp.module.trade.service;

import com.aicp.module.trade.domain.TradeEnums.EntitlementStatus;
import com.aicp.module.trade.entity.*;
import com.aicp.module.trade.mapper.PurchasedScriptCopyMapper;
import com.aicp.module.trade.mapper.ScriptEntitlementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent delivery: creates exactly one entitlement and one purchased
 * script copy per order item, guarded by unique database constraints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final ScriptEntitlementMapper entitlementMapper;
    private final PurchasedScriptCopyMapper copyMapper;

    /**
     * Deliver entitlement and warehouse copy for an order.
     * Must be called within the same transaction as order status update.
     * Unique constraints on order_item_id prevent duplicate delivery.
     */
    @Transactional
    public void deliver(TradeOrder order, TradeOrderItem item) {
        // Create entitlement (unique on order_item_id)
        ScriptEntitlement entitlement = new ScriptEntitlement();
        entitlement.setOrderItemId(item.getId());
        entitlement.setBeneficiaryWorkspaceId(order.getBuyerWorkspaceId());
        entitlement.setListingId(item.getListingId());
        entitlement.setScriptVersionId(item.getScriptVersionId());
        entitlement.setLicenseType(item.getLicenseType());
        entitlement.setStatus(EntitlementStatus.ACTIVE.name());
        entitlement.setEffectiveFrom(java.time.LocalDateTime.now());
        entitlement.setAllowCommercial("BUYOUT".equals(item.getLicenseType()) ? 1 : 0);
        entitlement.setAllowAdaptation("EXCLUSIVE".equals(item.getLicenseType())
                || "BUYOUT".equals(item.getLicenseType()) ? 1 : 0);
        try {
            entitlementMapper.insert(entitlement);
        } catch (Exception e) {
            // Unique constraint violation = already delivered, skip
            log.info("Entitlement already exists for orderItemId={}", item.getId());
        }

        // Create purchased script copy (unique on order_item_id)
        PurchasedScriptCopy copy = new PurchasedScriptCopy();
        copy.setOrderItemId(item.getId());
        copy.setWorkspaceId(order.getBuyerWorkspaceId());
        copy.setListingId(item.getListingId());
        copy.setSourceVersionId(item.getScriptVersionId());
        copy.setTitle(item.getTitleSnapshot());
        copy.setCreatedByUserId(order.getBuyerUserId());
        copy.setSourceListingId(item.getListingId());
        copy.setSourceAuthorName(item.getAuthorSnapshot());
        copy.setStatus("AVAILABLE");
        try {
            copyMapper.insert(copy);
        } catch (Exception e) {
            // Unique constraint violation = already delivered, skip
            log.info("Copy already exists for orderItemId={}", item.getId());
        }

        log.info("Delivered order: orderNo={}, orderItemId={}", order.getOrderNo(), item.getId());
    }
}
