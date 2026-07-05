package com.aicp.module.contentproject.service;

import com.aicp.module.contentproject.entity.ContentProject;

import java.util.function.Function;

/**
 * Pure mapping from stored detailed state to three public axes and one primary action.
 * Does NOT mutate project state — this is a read-only projection.
 */
public final class ProjectStatusProjection {

    public record StatusView(
            String contentStatus,
            String productionStatus,
            String commercialStatus,
            String lifecycleStatus,
            String primaryAction,
            String blockedReason) {}

    /** Projection without production-gate check (blockedReason is always null). */
    public static StatusView from(ContentProject p) {
        return from(p, null);
    }

    /** Projection with optional production-gate check for locked/creating states. */
    public static StatusView from(ContentProject p,
            Function<Long, String> productionGate) {
        String lifecycle = valueOr(p.getLifecycleStatus(), "active");
        if ("archived".equals(lifecycle)) {
            return new StatusView(p.getContentStatus(), publicProduction(p),
                    publicCommercial(p.getMarketStatus()), lifecycle, "restore", null);
        }
        String action = switch (valueOr(p.getContentStatus(), "draft")) {
            case "reviewing" -> "view_review";
            case "needs_revision" -> "resolve_review";
            case "approved" -> "lock_version";
            case "locked" -> lockedAction(p);
            default -> "continue_creation";
        };
        String blocked = null;
        if (productionGate != null && ("locked".equals(p.getContentStatus())
                || "create_storyboard".equals(action)
                || "view_production".equals(action))) {
            blocked = productionGate.apply(p.getId());
        }
        return new StatusView(p.getContentStatus(), publicProduction(p),
                publicCommercial(p.getMarketStatus()), lifecycle, action, blocked);
    }

    private static String publicProduction(ContentProject p) {
        if ("requested".equals(p.getStoryboardIntentStatus())
                || "in_progress".equals(p.getStoryboardIntentStatus())) return "storyboarding";
        return switch (valueOr(p.getProductionStatus(), "not_started")) {
            case "preflight", "canvas_ready", "generating", "quality_review" -> "canvas_producing";
            case "deliverable" -> "completed";
            default -> "not_started";
        };
    }

    private static String publicCommercial(String stored) {
        return switch (valueOr(stored, "private")) {
            case "pending_review" -> "listing_review";
            case "listed", "sold" -> "listed";
            case "delisted" -> "delisted";
            default -> "not_listed";
        };
    }

    private static String lockedAction(ContentProject p) {
        return switch (publicProduction(p)) {
            case "storyboarding", "canvas_producing" -> "view_production";
            case "completed" -> "view_result";
            default -> "create_storyboard";
        };
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private ProjectStatusProjection() {}
}
