package com.aicp.module.storyboard.domain;

public final class StoryboardEnums {

    public enum Tier {
        A, B, C;

        public String value() {
            return name();
        }
    }

    public enum VersionStatus {
        DRAFT, REVIEWING, LOCKED, SUPERSEDED;

        public String value() {
            return name().toLowerCase();
        }
    }

    public enum CreatedFrom {
        MANUAL, AI, IMPORT, FORK, UPGRADE;

        public String value() {
            return name().toLowerCase();
        }
    }

    public enum ShotStatus {
        DRAFT, CONFIRMED, NEEDS_REVIEW;

        public String value() {
            return name().toLowerCase();
        }
    }

    public enum JobType {
        GENERATE, UPGRADE, CHECK, IMPORT, EXPORT, CANVAS_SNAPSHOT;

        public String value() {
            return name().toLowerCase();
        }
    }

    public enum JobStatus {
        QUEUED, RUNNING, SUCCEEDED, FAILED, PARTIAL, CANCELLED;

        public String value() {
            return name().toLowerCase();
        }
    }

    public enum IssueStatus {
        OPEN, RESOLVED, IGNORED;

        public String value() {
            return name().toLowerCase();
        }
    }

    public enum IssueSeverity {
        ERROR, WARNING, INFO;

        public String value() {
            return name().toLowerCase();
        }
    }

    public enum SnapshotType {
        CONCEPT, PRODUCTION;

        public String value() {
            return name().toLowerCase();
        }
    }

    public enum ProductionStatus {
        NOT_READY, PREFLIGHT, READY, SNAPSHOT_CREATED;

        public String value() {
            return name().toLowerCase();
        }
    }

    private StoryboardEnums() {}
}
