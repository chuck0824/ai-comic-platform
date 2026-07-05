package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.module.contentproject.dto.CreativeBibleRequests.CreateBibleDraftRequest;
import com.aicp.module.contentproject.dto.CreativeBibleViews.BibleSummaryView;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreativeBibleService 单元测试")
class CreativeBibleServiceTest {

    @Mock CreativeBibleVersionMapper bibleMapper;
    @Mock EcosystemRuleMapper ecosystemMapper;
    @Mock ProjectWritingGuideMapper guideMapper;
    @Mock ProjectSettingEntityMapper settingMapper;
    @Mock ProjectSettingVersionMapper settingVersionMapper;
    @Mock ProjectAccessService accessService;
    @Mock OutboxService outboxService;
    @Mock ObjectMapper objectMapper;
    @InjectMocks CreativeBibleService service;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(accessService.require(anyLong(), anyLong(), any())).thenReturn(null);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        lenient().when(objectMapper.readValue(anyString(), eq(Object.class))).thenReturn(null);
    }

    @Test
    @DisplayName("创建草稿使用项目中下一个版本号")
    void createDraftUsesNextProjectVersion() {
        when(bibleMapper.selectOne(any())).thenReturn(version(2, "confirmed"));
        doAnswer(inv -> { ((CreativeBibleVersion) inv.getArgument(0)).setId(9L); return 1; })
                .when(bibleMapper).insert(any());

        BibleSummaryView result = service.createDraft(7L, 3L, new CreateBibleDraftRequest("调整生态", null));

        assertThat(result.versionNo()).isEqualTo(3);
        verify(accessService).require(3L, 7L, com.aicp.module.contentproject.domain.ContentProjectEnums.Action.EDIT_CONTENT);
    }

    @Test
    @DisplayName("确认拒绝无生态规则且无已确认设定的版本")
    void confirmRejectsDraftWithNoEcosystemOrConfirmedSettings() {
        when(bibleMapper.selectById(9L)).thenReturn(version(3, "draft"));
        when(ecosystemMapper.selectCount(any())).thenReturn(0L);
        when(settingMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.confirm(7L, 3L, 9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("至少确认一项生态或实体设定");
    }

    @Test
    @DisplayName("确认替代旧版本并发布事件")
    void confirmSupersedesOldVersionAndPublishesEvent() {
        CreativeBibleVersion draft = version(3, "draft");
        draft.setId(9L);
        when(bibleMapper.selectById(9L)).thenReturn(draft);
        when(ecosystemMapper.selectCount(any())).thenReturn(1L);
        when(settingMapper.selectCount(any())).thenReturn(0L);
        when(ecosystemMapper.selectList(any())).thenReturn(List.of());
        when(guideMapper.selectList(any())).thenReturn(List.of());
        when(settingMapper.selectList(any())).thenReturn(List.of());
        CreativeBibleVersion prevConfirmed = version(2, "confirmed");
        prevConfirmed.setId(5L);
        when(bibleMapper.selectOne(any())).thenReturn(prevConfirmed);

        BibleSummaryView result = service.confirm(7L, 3L, 9L);

        assertThat(result.status()).isEqualTo("confirmed");
        ArgumentCaptor<CreativeBibleVersion> captor = ArgumentCaptor.forClass(CreativeBibleVersion.class);
        verify(bibleMapper, atLeastOnce()).updateById(captor.capture());
        verify(outboxService).append(eq("CREATIVE_BIBLE_CONFIRMED"), eq(3L), anyInt(), any());
    }

    @Test
    @DisplayName("确认已确认版本抛出异常")
    void confirmAlreadyConfirmedThrows() {
        CreativeBibleVersion confirmed = version(3, "confirmed");
        confirmed.setId(9L);
        when(bibleMapper.selectById(9L)).thenReturn(confirmed);

        assertThatThrownBy(() -> service.confirm(7L, 3L, 9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只有草稿或待确认版本可以确认");
    }

    @Test
    @DisplayName("健康检查返回正确状态无圣经时")
    void healthReturnsMissingWhenNoBible() {
        when(bibleMapper.selectOne(any())).thenReturn(null);

        var result = service.health(7L, 3L);

        assertThat(result.get("status")).isEqualTo("missing");
        assertThat(result.get("ready_for_generation")).isEqualTo(false);
    }

    @Test
    @DisplayName("健康检查返回就绪状态")
    void healthReturnsReadyWhenConfirmed() {
        when(bibleMapper.selectOne(any())).thenReturn(version(1, "confirmed"));
        when(ecosystemMapper.selectCount(any())).thenReturn(3L);
        when(settingMapper.selectCount(any())).thenReturn(2L);

        var result = service.health(7L, 3L);

        assertThat(result.get("status")).isEqualTo("confirmed");
        assertThat(result.get("ready_for_generation")).isEqualTo(true);
        assertThat(result.get("confirmed_fact_count")).isEqualTo(5L);
    }

    @Test
    @DisplayName("ensureDraftForChange 复用已有草稿")
    void ensureDraftForChangeReusesExistingDraft() {
        CreativeBibleVersion draft = version(3, "draft");
        draft.setId(9L);
        when(bibleMapper.selectOne(any())).thenReturn(draft);

        BibleSummaryView result = service.ensureDraftForChange(7L, 3L, "setting_changed");

        assertThat(result.id()).isEqualTo(9L);
        verify(bibleMapper, never()).insert(any());
    }

    @Test
    @DisplayName("确认后生态规则和写作口径状态变为 confirmed")
    void confirmTransitionsRulesAndGuidesToConfirmed() {
        CreativeBibleVersion draft = version(3, "draft");
        draft.setId(9L);
        when(bibleMapper.selectById(9L)).thenReturn(draft);
        when(ecosystemMapper.selectCount(any())).thenReturn(1L);
        when(settingMapper.selectCount(any())).thenReturn(0L);
        when(settingMapper.selectList(any())).thenReturn(List.of());

        EcosystemRule rule = new EcosystemRule();
        rule.setId(10L);
        rule.setStatus("draft");
        when(ecosystemMapper.selectList(any())).thenReturn(List.of(rule));

        ProjectWritingGuide guide = new ProjectWritingGuide();
        guide.setId(20L);
        guide.setStatus("draft");
        when(guideMapper.selectList(any())).thenReturn(List.of(guide));

        service.confirm(7L, 3L, 9L);

        verify(ecosystemMapper).updateById(argThat(r -> "confirmed".equals(r.getStatus())));
        verify(guideMapper).updateById(argThat(g -> "confirmed".equals(g.getStatus())));
    }

    // ── test helpers ──

    private CreativeBibleVersion version(int versionNo, String status) {
        CreativeBibleVersion v = new CreativeBibleVersion();
        v.setId(1L);
        v.setProjectId(3L);
        v.setVersionNo(versionNo);
        v.setStatus(status);
        v.setSnapshotJson("{}");
        v.setCreatedAt(LocalDateTime.now());
        v.setCreatedBy(1L);
        return v;
    }
}
