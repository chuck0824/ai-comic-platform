package com.aicp.module.contentproject.domain;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class ContentProjectEnums {

    public enum CreationMode {
        SHORT_DRAMA, LONG_FORM, TVC;

        public static CreationMode parse(String value) {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        public String value() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum SourceMode {
        AI_MANUAL, UPLOADED;

        public static SourceMode parse(String value) {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        public String value() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum StoryboardIntent {
        NOT_DECIDED, SKIPPED, REQUESTED, IN_PROGRESS, COMPLETED;

        public String value() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum ContentStatus {
        DRAFT, REVIEWING, NEEDS_REVISION, APPROVED, LOCKED;

        public String value() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum ProductionStatus {
        NOT_STARTED, PREFLIGHT, CANVAS_READY, GENERATING, QUALITY_REVIEW, DELIVERABLE;

        public String value() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum MarketStatus {
        PRIVATE, PENDING_REVIEW, LISTED, SOLD, DELISTED;

        public String value() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Role {
        OWNER, EDITOR, REVIEWER, PRODUCER, VIEWER;

        public boolean allows(Action action) {
            return action.allowedRoles.contains(this);
        }
    }

    public enum Action {
        VIEW(EnumSet.allOf(Role.class)),
        EDIT_CONTENT(EnumSet.of(Role.OWNER, Role.EDITOR)),
        RUN_CONTENT_AI(EnumSet.of(Role.OWNER, Role.EDITOR)),
        REVIEW(EnumSet.of(Role.OWNER, Role.REVIEWER)),
        PRODUCE(EnumSet.of(Role.OWNER, Role.PRODUCER)),
        MANAGE_MEMBERS(EnumSet.of(Role.OWNER)),
        DELETE_PROJECT(EnumSet.of(Role.OWNER));

        private final Set<Role> allowedRoles;

        Action(Set<Role> roles) {
            this.allowedRoles = roles;
        }
    }

    private ContentProjectEnums() {}
}
