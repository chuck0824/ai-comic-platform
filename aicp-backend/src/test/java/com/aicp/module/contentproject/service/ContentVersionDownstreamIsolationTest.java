package com.aicp.module.contentproject.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentVersionDownstreamIsolationTest {

    @Mock ContentStoryboardMasterMapper masterMapper;
    @Mock ContentStoryboardSceneMapper sceneMapper;
    @Mock ContentStoryboardShotMapper shotMapper;
    @Mock ContentUnitMapper unitMapper;
    @Mock ContentVersionMapper versionMapper;
    @Mock ContentUnitHookMapper hookMapper;
    @Mock AiRouter aiRouter;

    private AiResponseParser parser;
    private ContentVersionSelector versionSelector;

    @BeforeEach
    void setUp() {
        parser = new AiResponseParser(new ObjectMapper());
        versionSelector = new ContentVersionSelector(versionMapper);
    }

    @Test
    void storyboardUsesAuthoritativeCurrentVersionInsteadOfHigherCandidate() {
        stubCurrentAndCandidate();
        when(masterMapper.selectOne(any())).thenReturn(null);
        when(aiRouter.chatCompletion(any())).thenReturn(aiResponse("{\"scenes\":[]}"));
        StoryboardService service = new StoryboardService(
                masterMapper, sceneMapper, shotMapper, unitMapper, versionSelector, aiRouter, parser);

        service.generateATier(501L, 9L, 17L);

        assertOnlyPublicContentWasPrompted();
    }

    @Test
    void reviewUsesAuthoritativeCurrentVersionInsteadOfHigherCandidate() {
        stubCurrentAndCandidate();
        when(aiRouter.chatCompletion(any())).thenReturn(aiResponse(
                "{\"hook_score\":80,\"director_score\":80,\"production_score\":80}"));
        ContentReviewService service = new ContentReviewService(unitMapper, versionSelector, aiRouter);

        service.reviewUnit(17L);

        assertOnlyPublicContentWasPrompted();
    }

    @Test
    void hookUsesAuthoritativeCurrentVersionInsteadOfHigherCandidate() {
        stubCurrentAndCandidate();
        when(hookMapper.selectOne(any())).thenReturn(null);
        when(aiRouter.chatCompletion(any())).thenReturn(aiResponse("{\"hook_score\":80}"));
        ContentHookService service = new ContentHookService(
                hookMapper, unitMapper, versionSelector, aiRouter, parser);

        service.generateHooks(17L);

        assertOnlyPublicContentWasPrompted();
    }

    @Test
    void promotionUsesAuthoritativeCurrentVersionInsteadOfHigherCandidate() {
        stubCurrentAndCandidate();
        when(aiRouter.chatCompletion(any())).thenReturn(aiResponse("{\"cover_copy\":\"公开宣传文案\"}"));
        PromotionService service = new PromotionService(unitMapper, versionMapper, versionSelector, aiRouter, parser);

        service.generatePromotion(9L, 17L);

        assertOnlyPublicContentWasPrompted();
    }

    @Test
    void fallbackWithoutCurrentVersionExcludesCandidateAndDiscardedStatuses() {
        ContentUnit unit = new ContentUnit();
        unit.setId(17L);
        when(versionMapper.selectList(any())).thenReturn(List.of(
                version(103L, "candidate", "candidate-secret"),
                version(102L, "discarded", "discarded-secret"),
                version(101L, "draft", "manual-public")));

        assertThat(versionSelector.resolvePublic(unit).getPlainText()).isEqualTo("manual-public");
    }

    private void stubCurrentAndCandidate() {
        ContentUnit unit = new ContentUnit();
        unit.setId(17L);
        unit.setProjectId(9L);
        unit.setCurrentVersionId(101L);
        when(unitMapper.selectById(17L)).thenReturn(unit);
        when(versionMapper.selectById(101L)).thenReturn(version(101L, "accepted", "accepted-public"));
    }

    private ContentVersion version(Long id, String status, String text) {
        ContentVersion version = new ContentVersion();
        version.setId(id);
        version.setProjectId(9L);
        version.setContentUnitId(17L);
        version.setVersionNo(id.intValue());
        version.setStatus(status);
        version.setPlainText(text);
        version.setContentJson("{\"text\":\"" + text + "\"}");
        return version;
    }

    private Map<String, Object> aiResponse(String json) {
        return Map.of("choices", List.of(Map.of("message", Map.of("content", json))));
    }

    @SuppressWarnings("unchecked")
    private void assertOnlyPublicContentWasPrompted() {
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(aiRouter, org.mockito.Mockito.atLeastOnce()).chatCompletion(params.capture());
        assertThat(params.getAllValues())
                .allSatisfy(value -> assertThat(String.valueOf(value.get("prompt")))
                        .contains("accepted-public")
                        .doesNotContain("candidate-secret"));
    }
}
