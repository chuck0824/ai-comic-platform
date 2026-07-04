package com.aicp.module.trade.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.enterprise.service.PurchaseBudgetService;
import com.aicp.module.enterprise.service.PurchaseBudgetService.BudgetSubject;
import com.aicp.module.trade.domain.TradeEnums.*;
import com.aicp.module.trade.dto.TradeRequests.*;
import com.aicp.module.trade.dto.TradeViews.PurchaseRequestView;
import com.aicp.module.trade.entity.*;
import com.aicp.module.trade.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/** Enterprise purchase request submission, approval, and rejection. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseApprovalService {

    private final PurchaseRequestMapper requestMapper;
    private final ScriptListingMapper listingMapper;
    private final ListingLicenseOptionMapper licenseOptionMapper;
    private final TradeOrderMapper orderMapper;
    private final TradeOrderItemMapper orderItemMapper;
    private final TradeOutboxEventMapper outboxMapper;
    private final PurchaseBudgetService budgetService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public PurchaseRequestView submit(WorkspaceContext ctx, CreatePurchaseRequest req) {
        ctx.require("trade.purchase.request");

        var listing = listingMapper.selectById(req.listingId());
        if (listing == null || !listing.getListingStatus().equals(ListingStatus.LISTED.name())) {
            throw new BizException(ErrorCode.LISTING_NOT_AVAILABLE);
        }

        var option = licenseOptionMapper.selectOne(new LambdaQueryWrapper<ListingLicenseOption>()
                .eq(ListingLicenseOption::getListingId, req.listingId())
                .eq(ListingLicenseOption::getLicenseType, req.licenseType())
                .eq(ListingLicenseOption::getEnabled, 1));
        if (option == null) throw new BizException(ErrorCode.LICENSE_OPTION_NOT_AVAILABLE);

        long amountCents = option.getPriceCents();

        // Budget reservation (fail-fast before creating order)
        String budgetSourceId = "purchase-" + UUID.randomUUID().toString().substring(0, 12);
        var budgetSubject = new BudgetSubject("WORKSPACE", ctx.workspaceId());
        var budgetSnapshot = budgetService.reserve(ctx, budgetSubject, YearMonth.now(),
                amountCents, budgetSourceId, "purchase:" + budgetSourceId + ":reserve");

        // Create pending order
        TradeOrder order = new TradeOrder();
        order.setOrderNo("ORD-ENT-" + System.currentTimeMillis());
        order.setStatus(OrderStatus.PENDING_APPROVAL.name());
        order.setBuyerUserId(ctx.userId());
        order.setBuyerWorkspaceId(req.workspaceId());
        order.setBuyerWorkspaceType("ENTERPRISE");
        order.setSellerUserId(listing.getSellerUserId());
        order.setSellerWorkspaceId(listing.getWorkspaceId());
        order.setTotalAmountCents(amountCents);
        order.setPlatformFeeCents(amountCents > 0 ? amountCents / 10 : 0);
        order.setSellerIncomeCents(amountCents - order.getPlatformFeeCents());
        order.setCreateIdempotencyKey("ent-" + ctx.workspaceId() + "-" + req.listingId() + "-" + System.currentTimeMillis());
        orderMapper.insert(order);

        PurchaseRequest entity = new PurchaseRequest();
        entity.setWorkspaceId(req.workspaceId());
        entity.setRequesterUserId(ctx.userId());
        entity.setListingId(req.listingId());
        entity.setLicenseType(req.licenseType());
        entity.setAmountCents(amountCents);
        entity.setReason(req.reason());
        entity.setStatus(PurchaseRequestStatus.PENDING_APPROVAL.name());
        entity.setOrderNo(order.getOrderNo());
        entity.setBudgetSubjectType(budgetSubject.type());
        entity.setBudgetSubjectId(budgetSubject.id());
        entity.setBudgetReservationEntryId(budgetSourceId);
        requestMapper.insert(entity);

        // Emit Outbox for approval projection
        emitOutbox("PURCHASE", "purchase-" + entity.getId(), "SUBMITTED",
                Map.of("source_id", "purchase-" + entity.getId(),
                       "workspace_id", ctx.workspaceId(),
                       "department_id", ctx.departmentId(),
                       "requester_user_id", ctx.userId(),
                       "summary", "采购申请: #" + entity.getId(),
                       "amount_cents", amountCents,
                       "status", "PENDING"));

        log.info("Purchase submitted: id={}, budgetReserved={}", entity.getId(), amountCents);
        return toView(entity);
    }

    @Transactional
    public PurchaseRequestView approve(WorkspaceContext ctx, Long requestId, ApprovalDecision decision) {
        ctx.require("trade.purchase.approve");
        if (!decision.approved()) throw new BizException(ErrorCode.PARAM_INVALID);

        var pr = requestMapper.selectById(requestId);
        if (pr == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (!pr.getStatus().equals(PurchaseRequestStatus.PENDING_APPROVAL.name()))
            throw new BizException(ErrorCode.ORDER_STATE_CONFLICT);

        pr.setApproverUserId(ctx.userId());
        pr.setApprovalComment(decision.comment());
        pr.setStatus(PurchaseRequestStatus.APPROVED.name());
        requestMapper.updateById(pr);

        // Activate the order for payment (no auto-charge)
        var order = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, pr.getOrderNo()));
        if (order != null) {
            order.setStatus(OrderStatus.PENDING_PAYMENT.name());
            orderMapper.updateById(order);
        }

        emitOutbox("PURCHASE", "purchase-" + pr.getId(), "APPROVED",
                Map.of("source_id", "purchase-" + pr.getId(),
                       "workspace_id", pr.getWorkspaceId(),
                       "department_id", "",
                       "requester_user_id", pr.getRequesterUserId(),
                       "summary", "采购已批准 #" + pr.getId(),
                       "amount_cents", pr.getAmountCents(),
                       "status", "APPROVED"));

        log.info("Enterprise purchase approved: id={}, approver={}", requestId, ctx.userId());
        return toView(pr);
    }

    @Transactional
    public PurchaseRequestView reject(WorkspaceContext ctx, Long requestId, ApprovalDecision decision) {
        ctx.require("trade.purchase.approve");
        var pr = requestMapper.selectById(requestId);
        if (pr == null) throw new BizException(ErrorCode.NOT_FOUND);

        pr.setApproverUserId(ctx.userId());
        pr.setApprovalComment(decision.comment());
        pr.setStatus(PurchaseRequestStatus.REJECTED.name());
        requestMapper.updateById(pr);

        // Cancel order
        var order = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, pr.getOrderNo()));
        if (order != null) {
            order.setStatus(OrderStatus.REJECTED.name());
            orderMapper.updateById(order);
        }

        // Release budget reservation
        if (pr.getBudgetReservationEntryId() != null) {
            try {
                budgetService.release(ctx, pr.getBudgetReservationEntryId(),
                        pr.getAmountCents(), "purchase:" + pr.getBudgetReservationEntryId() + ":release");
            } catch (Exception e) {
                log.error("Failed to release budget for purchase {}: {}", pr.getId(), e.getMessage());
            }
        }

        emitOutbox("PURCHASE", "purchase-" + pr.getId(), "REJECTED",
                Map.of("source_id", "purchase-" + pr.getId(),
                       "workspace_id", pr.getWorkspaceId(),
                       "department_id", "",
                       "requester_user_id", pr.getRequesterUserId(),
                       "summary", "采购已驳回 #" + pr.getId(),
                       "amount_cents", pr.getAmountCents(),
                       "status", "REJECTED"));

        return toView(pr);
    }

    public List<PurchaseRequestView> listByWorkspace(String workspaceId) {
        return requestMapper.selectList(new LambdaQueryWrapper<PurchaseRequest>()
                .eq(PurchaseRequest::getWorkspaceId, workspaceId)
                .orderByDesc(PurchaseRequest::getCreatedAt))
                .stream().map(this::toView).toList();
    }

    private void emitOutbox(String aggregateType, String aggregateId,
                             String eventType, Map<String, Object> payload) {
        try {
            var event = new TradeOutboxEvent();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setIdempotencyKey(aggregateType + ":" + aggregateId + ":" + eventType);
            event.setStatus("PENDING");
            event.setRetryCount(0);
            event.setMaxRetries(10);
            event.setNextRetryAt(LocalDateTime.now());
            outboxMapper.insert(event);
        } catch (Exception e) {
            log.error("Failed to emit Outbox event {}:{}: {}", aggregateType, aggregateId, eventType, e);
        }
    }

    private PurchaseRequestView toView(PurchaseRequest pr) {
        return new PurchaseRequestView(pr.getId(), pr.getWorkspaceId(), pr.getRequesterUserId(),
                pr.getListingId(), pr.getLicenseType(), pr.getAmountCents(), pr.getReason(),
                pr.getStatus(), pr.getApproverUserId(), pr.getApprovalComment(), pr.getOrderNo(),
                pr.getCreatedAt());
    }
}
