package com.aicp.module.agent.domain;

public final class AgentConfigEnums {

    public enum RoleType {
        HOOK, SCREENWRITER, STORYBOARD, DIRECTOR
    }

    public enum VersionStatus {
        DRAFT, PUBLISHED, ARCHIVED
    }

    public enum LifecycleStatus {
        ACTIVE, ARCHIVED
    }

    public enum ScopeType {
        USER, PROJECT
    }

    public enum TestRunStatus {
        PENDING, RUNNING, SUCCEEDED, FAILED
    }

    private AgentConfigEnums() {}
}
