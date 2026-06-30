package com.aicp.module.storyboard.domain;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.storyboard.domain.StoryboardEnums.Tier;
import com.aicp.module.storyboard.domain.StoryboardEnums.VersionStatus;

public final class StoryboardStateMachine {

    private StoryboardStateMachine() {}

    public static void requireTransition(VersionStatus from, VersionStatus to) {
        boolean allowed = switch (from) {
            case DRAFT -> to == VersionStatus.REVIEWING || to == VersionStatus.LOCKED;
            case REVIEWING -> to == VersionStatus.DRAFT || to == VersionStatus.LOCKED;
            case LOCKED -> to == VersionStatus.SUPERSEDED;
            case SUPERSEDED -> false;
        };
        if (!allowed) {
            throw new BizException(ErrorCode.STORYBOARD_VERSION_LOCKED,
                    "不允许从 " + from.value() + " 转为 " + to.value());
        }
    }

    public static void requireTierUpgrade(Tier from, Tier to) {
        if (!((from == Tier.A && to == Tier.B) || (from == Tier.B && to == Tier.C))) {
            throw new BizException(ErrorCode.INVALID_TIER_TRANSITION,
                    "不允许从 " + from.value() + " 档升到 " + to.value() + " 档");
        }
    }

    public static boolean isEditable(VersionStatus status) {
        return status == VersionStatus.DRAFT;
    }

    public static boolean isLocked(VersionStatus status) {
        return status == VersionStatus.LOCKED || status == VersionStatus.SUPERSEDED;
    }
}
