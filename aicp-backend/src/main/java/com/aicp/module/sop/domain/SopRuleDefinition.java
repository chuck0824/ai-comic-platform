package com.aicp.module.sop.domain;

import java.util.Set;

public record SopRuleDefinition(
        String code,
        String name,
        String category,
        SopEnums.Severity severity,
        boolean critical,
        Set<SopEnums.GateType> gates,
        SopEnums.FixPolicy fixPolicy,
        boolean enabled) {
}
