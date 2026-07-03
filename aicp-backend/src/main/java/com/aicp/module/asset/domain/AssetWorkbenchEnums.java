package com.aicp.module.asset.domain;

/**
 * Stable asset-workbench enumeration types.
 * Database values use the enum name strings (e.g. "CHARACTER", "PENDING").
 */
public final class AssetWorkbenchEnums {
    private AssetWorkbenchEnums() {}

    /** Asset business category — maps to workspace_assets.asset_type. */
    public enum AssetType { CHECKPOINT, LORA, STYLE_PACK, PROMPT, CHARACTER, SCENE, PROP, STORYBOARD, VOICE, MUSIC, OTHER }

    /** Media format — maps to workspace_assets.media_type. */
    public enum MediaType { IMAGE, VIDEO, AUDIO, DATA, OTHER }

    /** Record discriminator in the unified workbench projection. */
    public enum RecordKind { TASK, ASSET }

    /** Generation-task lifecycle status — database stores lowercase. */
    public enum RecordStatus { PENDING, RUNNING, SUCCEEDED, FAILED, CANCELED }

    /** Virtual collections shown in the project-tree sidebar. */
    public enum Collection { UNFILED, FAVORITES, PUBLISHED, TRASH }

    /** Workspace-asset lifecycle status — database stores uppercase. */
    public enum AssetStatus { ACTIVE, ARCHIVED, TRASHED }

    /** Batch operation types. */
    public enum BatchOperation { MOVE, SET_TYPE, ADD_TAGS, REMOVE_TAGS, TRASH, RESTORE }

    /** Actions the backend computes per record; frontend MUST NOT infer from role. */
    public enum AllowedAction {
        PREVIEW, EDIT, FAVORITE, DOWNLOAD, SEND_TO_CANVAS,
        REGENERATE, PUBLISH, TRASH, RESTORE, CANCEL_TASK, RETRY_TASK
    }
}
