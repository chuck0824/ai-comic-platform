package com.aicp.module.asset.domain;

/**
 * Stable asset-market enumeration types.
 * Database values use the enum name strings (e.g. "CHECKPOINT", "LISTED").
 */
public class AssetEnums {

    public enum AssetType { CHECKPOINT, LORA, STYLE_PACK, CHARACTER, SCENE, PROMPT }

    public enum AccessScope { PRIVATE, WORKSPACE }

    public enum AssetSource { CREATED, MARKET_CLAIMED, PROJECT_GENERATED, IMPORTED }

    /** Shared lifecycle. DISABLED preserves existing references while rejecting new bindings. */
    public enum AssetStatus { ACTIVE, DISABLED, ARCHIVED }

    public enum ListingStatus { LISTED, UNLISTED, REMOVED }

    public enum PublishStatus { PENDING, APPROVED, REJECTED, CANCELLED }

    private AssetEnums() {}
}
