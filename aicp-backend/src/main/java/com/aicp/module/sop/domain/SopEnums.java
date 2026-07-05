package com.aicp.module.sop.domain;

import java.util.Map;
import java.util.Set;

public final class SopEnums {

    public enum SopResult {
        PASS, WARNING, BLOCKED, NOT_READY, ERROR;

        public String value() {
            return name().toLowerCase();
        }
    }

    public enum Severity {
        P0, P1, P2, P3;

        public String value() {
            return name();
        }

        public boolean isBlocking() {
            return this == P0 || this == P1;
        }
    }

    public enum RunStatus {
        RUNNING, COMPLETED, STALE;

        public String value() {
            return name().toLowerCase();
        }
    }

    public enum OverallStatus {
        GREEN, YELLOW, RED;

        public String value() {
            return name().toLowerCase();
        }
    }

    public enum GateType {
        PRODUCTION_ADMISSION;

        public String value() {
            return name().toLowerCase();
        }
    }

    public enum TriggerType {
        MANUAL, GATE;

        public String value() {
            return name().toLowerCase();
        }
    }

    public enum FixPolicy {
        AUTO_SAFE, CONFIRM_REQUIRED, MANUAL_ONLY;

        public String value() {
            return name().toLowerCase();
        }
    }

    public enum WorkOrderStatus {
        OPEN, ASSIGNED, FIXING, PENDING_REVIEW, PASSED, REOPENED, CANCELED;

        public String value() {
            return name().toLowerCase();
        }

        private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> ALLOWED_TRANSITIONS = Map.of(
                OPEN, Set.of(ASSIGNED, CANCELED),
                ASSIGNED, Set.of(FIXING, CANCELED),
                FIXING, Set.of(PENDING_REVIEW),
                PENDING_REVIEW, Set.of(PASSED, REOPENED),
                REOPENED, Set.of(FIXING, CANCELED),
                PASSED, Set.of(),
                CANCELED, Set.of()
        );

        public boolean canTransitionTo(WorkOrderStatus target) {
            Set<WorkOrderStatus> allowed = ALLOWED_TRANSITIONS.get(this);
            return allowed != null && allowed.contains(target);
        }
    }

    private SopEnums() {}
}
