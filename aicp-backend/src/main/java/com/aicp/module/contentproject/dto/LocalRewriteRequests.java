package com.aicp.module.contentproject.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

public final class LocalRewriteRequests {

    private LocalRewriteRequests() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record LocalRewriteRequest(
            Long baseVersionId,
            Integer contentUnitRevision,
            String contentHash,
            Integer startOffset,
            Integer endOffset,
            String selectedTextHash,
            String operation,
            String model,
            String prompt
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PatchOp(
            Integer startOffset,
            Integer endOffset,
            String expectedTextHash,
            String replacement,
            String reason
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record LocalRewriteAdoptRequest(
            Integer contentUnitRevision,
            String contentHash,
            List<PatchOp> patches,
            Long candidateVersionId
    ) {}
}
