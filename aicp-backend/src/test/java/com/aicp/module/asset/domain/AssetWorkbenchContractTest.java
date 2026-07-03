package com.aicp.module.asset.domain;

import com.aicp.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test that locks the workbench enum values and error-code mappings
 * before any implementation begins.
 */
class AssetWorkbenchContractTest {

    @Test
    void exposesStableAssetCategoriesAndStatuses() {
        assertThat(AssetWorkbenchEnums.AssetType.values()).extracting(Enum::name)
                .containsExactly("CHECKPOINT", "LORA", "STYLE_PACK", "PROMPT", "CHARACTER",
                        "SCENE", "PROP", "STORYBOARD", "VOICE", "MUSIC", "OTHER");
        assertThat(AssetWorkbenchEnums.RecordStatus.values()).extracting(Enum::name)
                .containsExactly("PENDING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELED");
    }

    @Test
    void exposesMediaTypesAndRecordKinds() {
        assertThat(AssetWorkbenchEnums.MediaType.values()).extracting(Enum::name)
                .containsExactly("IMAGE", "VIDEO", "AUDIO", "DATA", "OTHER");
        assertThat(AssetWorkbenchEnums.RecordKind.values()).extracting(Enum::name)
                .containsExactly("TASK", "ASSET");
    }

    @Test
    void exposesCollectionsAndBatchOperations() {
        assertThat(AssetWorkbenchEnums.Collection.values()).extracting(Enum::name)
                .containsExactly("UNFILED", "FAVORITES", "PUBLISHED", "TRASH");
        assertThat(AssetWorkbenchEnums.BatchOperation.values()).extracting(Enum::name)
                .containsExactly("MOVE", "SET_TYPE", "ADD_TAGS", "REMOVE_TAGS", "TRASH", "RESTORE");
    }

    @Test
    void exposesAllowedActions() {
        assertThat(AssetWorkbenchEnums.AllowedAction.values()).extracting(Enum::name)
                .containsExactly("PREVIEW", "EDIT", "FAVORITE", "DOWNLOAD", "SEND_TO_CANVAS",
                        "REGENERATE", "PUBLISH", "TRASH", "RESTORE", "CANCEL_TASK", "RETRY_TASK");
    }

    @Test
    void assetLifecycleStatusesAreUppercase() {
        assertThat(AssetWorkbenchEnums.AssetStatus.values()).extracting(Enum::name)
                .containsExactly("ACTIVE", "ARCHIVED", "TRASHED");
    }

    // ── Error-code contracts ──────────────────────────────────────────

    @Test
    void reservesAssetFileMissing() {
        assertThat(ErrorCode.ASSET_FILE_MISSING.getCode()).isEqualTo(48008);
    }

    @Test
    void reservesAssetLifecycleConflict() {
        assertThat(ErrorCode.ASSET_LIFECYCLE_CONFLICT.getCode()).isEqualTo(48009);
    }

    @Test
    void reservesAssetCategoryInvalid() {
        assertThat(ErrorCode.ASSET_CATEGORY_INVALID.getCode()).isEqualTo(48010);
    }

    @Test
    void reservesAssetPurged() {
        assertThat(ErrorCode.ASSET_PURGED.getCode()).isEqualTo(48011);
    }

    @Test
    void reservesAssetBatchLimit() {
        assertThat(ErrorCode.ASSET_BATCH_LIMIT.getCode()).isEqualTo(48012);
    }

    @Test
    void reservesAssetIdempotencyConflict() {
        assertThat(ErrorCode.ASSET_IDEMPOTENCY_CONFLICT.getCode()).isEqualTo(48013);
    }

    @Test
    void reservesAssetCanvasTargetInvalid() {
        assertThat(ErrorCode.ASSET_CANVAS_TARGET_INVALID.getCode()).isEqualTo(48014);
    }

    @Test
    void reservesAssetDownloadSignFailed() {
        assertThat(ErrorCode.ASSET_DOWNLOAD_SIGN_FAILED.getCode()).isEqualTo(48015);
    }

    @Test
    void reservesAssetSettlementFailed() {
        assertThat(ErrorCode.ASSET_SETTLEMENT_FAILED.getCode()).isEqualTo(48016);
    }

    @Test
    void reservesAssetCompensationExhausted() {
        assertThat(ErrorCode.ASSET_COMPENSATION_EXHAUSTED.getCode()).isEqualTo(48017);
    }

    @Test
    void reservesGenerationTaskNotFound() {
        assertThat(ErrorCode.GENERATION_TASK_NOT_FOUND.getCode()).isEqualTo(46020);
    }

    @Test
    void reservesGenerationTaskStateConflict() {
        assertThat(ErrorCode.GENERATION_TASK_STATE_CONFLICT.getCode()).isEqualTo(46021);
    }

    @Test
    void noOverlappingErrorCodesIn48xxx() {
        long distinct = java.util.Arrays.stream(ErrorCode.values())
                .filter(e -> e.getCode() >= 48000 && e.getCode() < 49000)
                .map(ErrorCode::getCode)
                .distinct()
                .count();
        long total = java.util.Arrays.stream(ErrorCode.values())
                .filter(e -> e.getCode() >= 48000 && e.getCode() < 49000)
                .count();
        assertThat(distinct).isEqualTo(total);
    }

    @Test
    void noOverlappingErrorCodesIn46xxx() {
        long distinct = java.util.Arrays.stream(ErrorCode.values())
                .filter(e -> e.getCode() >= 46000 && e.getCode() < 47000)
                .map(ErrorCode::getCode)
                .distinct()
                .count();
        long total = java.util.Arrays.stream(ErrorCode.values())
                .filter(e -> e.getCode() >= 46000 && e.getCode() < 47000)
                .count();
        assertThat(distinct).isEqualTo(total);
    }
}
