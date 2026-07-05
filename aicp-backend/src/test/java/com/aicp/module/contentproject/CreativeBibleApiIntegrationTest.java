package com.aicp.module.contentproject;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.dto.CreativeBibleRequests.*;
import com.aicp.module.contentproject.dto.CreativeBibleViews.*;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.mapper.*;
import com.aicp.module.contentproject.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("P0 创作圣经 API 集成测试")
class CreativeBibleApiIntegrationTest {

    @Mock CreativeBibleVersionMapper bibleMapper;
    @Mock EcosystemRuleMapper ecosystemMapper;
    @Mock ProjectWritingGuideMapper guideMapper;
    @Mock ProjectSettingEntityMapper settingMapper;
    @Mock ProjectSettingVersionMapper settingVersionMapper;
    @Mock ProjectAccessService accessService;
    @Mock OutboxService outboxService;
    @Mock ObjectMapper objectMapper;

    private CreativeBibleService bibleService;

    private static final Long USER_ID = 7L;
    private static final Long PROJECT_ID = 3L;

    @BeforeEach
    void setUp() {
        bibleService = new CreativeBibleService(
                bibleMapper, ecosystemMapper, guideMapper,
                settingMapper, settingVersionMapper,
                accessService, outboxService, objectMapper);
    }

    // ── helpers ──

    private CreativeBibleVersion draftVersion(Long id, int versionNo) {
        CreativeBibleVersion v = new CreativeBibleVersion();
        v.setId(id);
        v.setProjectId(PROJECT_ID);
        v.setVersionNo(versionNo);
        v.setStatus("draft");
        v.setSnapshotJson("{}");
        v.setCreatedBy(USER_ID);
        v.setCreatedAt(LocalDateTime.now());
        return v;
    }

    private EcosystemRule rule(Long id, String ruleType, String name) {
        EcosystemRule r = new EcosystemRule();
        r.setId(id);
        r.setProjectId(PROJECT_ID);
        r.setBibleVersionId(10L);
        r.setRuleType(ruleType);
        r.setName(name);
        r.setStatus("draft");
        r.setRevision(0);
        r.setCreatedBy(USER_ID);
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        return r;
    }

    private ProjectSettingEntity confirmedSetting(Long id, String type) {
        ProjectSettingEntity s = new ProjectSettingEntity();
        s.setId(id);
        s.setProjectId(PROJECT_ID);
        s.setSettingType(type);
        s.setCanonicalName("测试设定" + id);
        s.setStatus("confirmed");
        s.setCurrentVersionNo(1);
        s.setRevision(0);
        s.setCreatedBy(USER_ID);
        return s;
    }

    private ProjectSettingVersion versionFor(Long entityId, int versionNo) {
        ProjectSettingVersion ver = new ProjectSettingVersion();
        ver.setId(entityId * 10 + versionNo);
        ver.setEntityId(entityId);
        ver.setVersionNo(versionNo);
        ver.setSnapshotJson("{\"name\":\"测试设定" + entityId + "\"}");
        ver.setSourceType("manual");
        ver.setOperatedBy(USER_ID);
        ver.setCreatedAt(LocalDateTime.now());
        return ver;
    }

    // ── tests ──

    @Test
    @DisplayName("GET health 返回 missing 状态当无确认圣经时")
    void healthReturnsMissingWhenNoConfirmedBible() {
        when(bibleMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = bibleService.health(USER_ID, PROJECT_ID);

        assertThat(result).containsEntry("status", "missing");
        assertThat(result).containsEntry("ready_for_generation", false);
        verify(accessService).require(PROJECT_ID, USER_ID, Action.VIEW);
    }

    @Test
    @DisplayName("GET health 返回 ready 当有确认圣经时")
    void healthReturnsReadyWhenConfirmedBibleExists() {
        CreativeBibleVersion confirmed = draftVersion(11L, 2);
        confirmed.setStatus("confirmed");
        confirmed.setSnapshotHash("abc123");
        when(bibleMapper.selectOne(any())).thenReturn(confirmed);
        when(ecosystemMapper.selectCount(any())).thenReturn(3L);
        when(settingMapper.selectCount(any())).thenReturn(5L);

        Map<String, Object> result = bibleService.health(USER_ID, PROJECT_ID);

        assertThat(result).containsEntry("status", "confirmed");
        assertThat(result).containsEntry("ready_for_generation", true);
        assertThat(result).containsEntry("confirmed_fact_count", 8L);
    }

    @Test
    @DisplayName("POST createDraft 创建新草稿版本")
    void createDraftCreatesNewVersion() {
        when(bibleMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> { ((CreativeBibleVersion) inv.getArgument(0)).setId(10L); return 1; })
                .when(bibleMapper).insert(any());

        BibleSummaryView result = bibleService.createDraft(USER_ID, PROJECT_ID,
                new CreateBibleDraftRequest("首个圣经版本", null));

        assertThat(result.versionNo()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("draft");
        verify(accessService).require(PROJECT_ID, USER_ID, Action.EDIT_CONTENT);
    }

    @Test
    @DisplayName("POST confirm 在无事实时报错")
    void confirmRejectsWhenNoFacts() {
        CreativeBibleVersion draft = draftVersion(9L, 1);
        when(bibleMapper.selectById(9L)).thenReturn(draft);
        when(ecosystemMapper.selectCount(any())).thenReturn(0L);
        when(settingMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> bibleService.confirm(USER_ID, PROJECT_ID, 9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("至少确认一项生态或实体设定");
    }

    @Test
    @DisplayName("POST confirm 在有生态规则时成功")
    void confirmSucceedsWithEcosystemRules() throws Exception {
        CreativeBibleVersion draft = draftVersion(9L, 1);
        when(bibleMapper.selectById(9L)).thenReturn(draft);
        when(ecosystemMapper.selectCount(any())).thenReturn(2L);
        when(settingMapper.selectCount(any())).thenReturn(0L);
        when(ecosystemMapper.selectList(any())).thenReturn(List.of(
                rule(1L, "world_rule", "能力有代价"),
                rule(2L, "key_history", "大洪水")));
        when(settingMapper.selectList(any())).thenReturn(List.of());
        when(guideMapper.selectList(any())).thenReturn(List.of());
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ecosystem_rules\":[],\"confirmed_settings\":[],\"writing_guides\":[]}");

        BibleSummaryView result = bibleService.confirm(USER_ID, PROJECT_ID, 9L);

        assertThat(result.status()).isEqualTo("confirmed");
        verify(bibleMapper).updateById(argThat(v -> "confirmed".equals(v.getStatus())));
        verify(outboxService).append(eq("CREATIVE_BIBLE_CONFIRMED"), eq(PROJECT_ID), anyInt(), any());
    }

    @Test
    @DisplayName("Viewer 无权创建草稿")
    void viewerCannotCreateDraft() {
        doThrow(new BizException(ErrorCode.PROJECT_ACCESS_DENIED))
                .when(accessService).require(PROJECT_ID, USER_ID, Action.EDIT_CONTENT);

        assertThatThrownBy(() -> bibleService.createDraft(USER_ID, PROJECT_ID,
                new CreateBibleDraftRequest("尝试", null)))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED.getCode());
    }

    @Test
    @DisplayName("已确认圣经不可修改生态规则")
    void cannotModifyConfirmedBible() {
        CreativeBibleVersion confirmed = draftVersion(11L, 2);
        confirmed.setStatus("confirmed");
        when(bibleMapper.selectById(11L)).thenReturn(confirmed);

        assertThatThrownBy(() -> bibleService.upsertEcosystem(USER_ID, PROJECT_ID, 11L, null,
                new UpsertEcosystemRuleRequest("world_rule", "测试", null, null, null, null, "manual", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已确认的创作圣经不可修改");
    }

    @Nested
    @DisplayName("生命周期")
    class Lifecycle {

        @Test
        @DisplayName("draft → submitReview → reviewable")
        void submitReviewTransitionsToReviewable() {
            CreativeBibleVersion draft = draftVersion(10L, 1);
            when(bibleMapper.selectById(10L)).thenReturn(draft);

            BibleSummaryView result = bibleService.submitReview(USER_ID, PROJECT_ID, 10L);

            assertThat(result.status()).isEqualTo("reviewable");
            verify(bibleMapper).updateById(argThat(v -> "reviewable".equals(v.getStatus())));
        }

        @Test
        @DisplayName("confirmed 不可提交审核")
        void confirmedCannotSubmitReview() {
            CreativeBibleVersion confirmed = draftVersion(11L, 2);
            confirmed.setStatus("confirmed");
            when(bibleMapper.selectById(11L)).thenReturn(confirmed);

            assertThatThrownBy(() -> bibleService.submitReview(USER_ID, PROJECT_ID, 11L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("只有草稿可以提交审核");
        }

        @Test
        @DisplayName("superseded 版本可归档")
        void supersededCanBeArchived() {
            CreativeBibleVersion superseded = draftVersion(5L, 1);
            superseded.setStatus("superseded");
            when(bibleMapper.selectById(5L)).thenReturn(superseded);

            assertThatCode(() -> bibleService.archive(USER_ID, PROJECT_ID, 5L))
                    .doesNotThrowAnyException();
            verify(bibleMapper).updateById(argThat(v -> "archived".equals(v.getStatus())));
        }

        @Test
        @DisplayName("draft 版本不可归档")
        void draftCannotBeArchived() {
            CreativeBibleVersion draft = draftVersion(10L, 1);
            when(bibleMapper.selectById(10L)).thenReturn(draft);

            assertThatThrownBy(() -> bibleService.archive(USER_ID, PROJECT_ID, 10L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("只有已替代或已确认");
        }
    }

    @Nested
    @DisplayName("写作口径")
    class WritingGuides {

        @Test
        @DisplayName("保存项目级口径")
        void saveProjectLevelGuide() throws Exception {
            CreativeBibleVersion draft = draftVersion(10L, 1);
            when(bibleMapper.selectById(10L)).thenReturn(draft);
            when(guideMapper.selectOne(any())).thenReturn(null);
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"pov\":\"third\"}");
            doAnswer(inv -> { ((ProjectWritingGuide) inv.getArgument(0)).setId(20L); return 1; })
                    .when(guideMapper).insert(any());

            WritingGuideView result = bibleService.saveWritingGuide(USER_ID, PROJECT_ID, 10L,
                    new UpsertWritingGuideRequest("project", 0L, Map.of("pov", "third"), null));

            assertThat(result.scopeType()).isEqualTo("project");
            assertThat(result.scopeId()).isEqualTo(0L);
            assertThat(result.status()).isEqualTo("draft");
        }

        @Test
        @DisplayName("无效 scope_type 被拒绝")
        void rejectsInvalidScopeType() {
            CreativeBibleVersion draft = draftVersion(10L, 1);
            when(bibleMapper.selectById(10L)).thenReturn(draft);

            assertThatThrownBy(() -> bibleService.saveWritingGuide(USER_ID, PROJECT_ID, 10L,
                    new UpsertWritingGuideRequest("invalid", 1L, Map.of(), null)))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("无效的 scope_type");
        }

        @Test
        @DisplayName("项目级口径 scope_id 必须为 0")
        void projectGuideRequiresScopeIdZero() {
            CreativeBibleVersion draft = draftVersion(10L, 1);
            when(bibleMapper.selectById(10L)).thenReturn(draft);

            assertThatThrownBy(() -> bibleService.saveWritingGuide(USER_ID, PROJECT_ID, 10L,
                    new UpsertWritingGuideRequest("project", 5L, Map.of(), null)))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("scope_id 必须为 0");
        }
    }
}
