package com.aicp.module.contentproject.service;

import com.aicp.module.contentproject.entity.ContentProject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectStatusProjectionTest {

    @Test
    void mapsDetailedStatesToPublicAxesAndOnePrimaryAction() {
        ContentProject project = new ContentProject();
        project.setContentStatus("locked");
        project.setStoryboardIntentStatus("requested");
        project.setProductionStatus("not_started");
        project.setMarketStatus("private");
        project.setLifecycleStatus("active");

        ProjectStatusProjection.StatusView result = ProjectStatusProjection.from(project);

        assertThat(result.productionStatus()).isEqualTo("storyboarding");
        assertThat(result.commercialStatus()).isEqualTo("not_listed");
        // storyboardIntentStatus="requested" means production is "storyboarding",
        // so lockedAction returns "view_production" (not "create_storyboard")
        assertThat(result.primaryAction()).isEqualTo("view_production");
    }

    @Test
    void archivedProjectAlwaysUsesRestoreAsPrimaryAction() {
        ContentProject project = new ContentProject();
        project.setContentStatus("draft");
        project.setProductionStatus("not_started");
        project.setMarketStatus("private");
        project.setLifecycleStatus("archived");

        assertThat(ProjectStatusProjection.from(project).primaryAction()).isEqualTo("restore");
    }

    @Test
    void lockedProjectWithoutLockedVersionReceivesBlockedReason() {
        ContentProject project = new ContentProject();
        project.setId(1L);
        project.setContentStatus("locked");
        project.setProductionStatus("not_started");
        project.setMarketStatus("private");
        project.setLifecycleStatus("active");

        ProjectStatusProjection.StatusView result = ProjectStatusProjection.from(project,
                id -> "请先锁定一个审核通过的内容版本");

        assertThat(result.primaryAction()).isEqualTo("create_storyboard");
        assertThat(result.blockedReason()).isEqualTo("请先锁定一个审核通过的内容版本");
    }

    @Test
    void activeDraftSkipsGateCheck() {
        ContentProject project = new ContentProject();
        project.setId(1L);
        project.setContentStatus("draft");
        project.setProductionStatus("not_started");
        project.setMarketStatus("private");
        project.setLifecycleStatus("active");

        ProjectStatusProjection.StatusView result = ProjectStatusProjection.from(project,
                id -> "should not be called");

        assertThat(result.blockedReason()).isNull();
    }

    @Test
    void reviewingMapsToViewReview() {
        ContentProject project = new ContentProject();
        project.setContentStatus("reviewing");
        project.setProductionStatus("not_started");
        project.setMarketStatus("private");
        project.setLifecycleStatus("active");

        ProjectStatusProjection.StatusView result = ProjectStatusProjection.from(project);

        assertThat(result.primaryAction()).isEqualTo("view_review");
    }

    @Test
    void soldMarketStatusMapsToListed() {
        ContentProject project = new ContentProject();
        project.setContentStatus("draft");
        project.setProductionStatus("not_started");
        project.setMarketStatus("sold");
        project.setLifecycleStatus("active");

        ProjectStatusProjection.StatusView result = ProjectStatusProjection.from(project);

        assertThat(result.commercialStatus()).isEqualTo("listed");
    }

    @Test
    void nullLifecycleStatusDefaultsToActive() {
        ContentProject project = new ContentProject();
        project.setContentStatus("draft");
        project.setProductionStatus("not_started");
        project.setMarketStatus("private");
        project.setLifecycleStatus(null);

        ProjectStatusProjection.StatusView result = ProjectStatusProjection.from(project);

        assertThat(result.lifecycleStatus()).isEqualTo("active");
        assertThat(result.primaryAction()).isEqualTo("continue_creation");
    }
}
