package com.aicp.module.sop.domain;

import java.util.Map;

public record SopRuleEvaluation(
        String ruleCode,
        SopEnums.SopResult result,
        SopEnums.Severity severity,
        boolean critical,
        String targetType,
        String targetId,
        String issueFingerprint,
        Map<String, Object> evidence,
        String suggestion,
        SopEnums.FixPolicy fixPolicy) {

    public boolean isBlocking() {
        return result == SopEnums.SopResult.BLOCKED
                || (result == SopEnums.SopResult.NOT_READY && critical)
                || (result == SopEnums.SopResult.ERROR && critical);
    }
}
