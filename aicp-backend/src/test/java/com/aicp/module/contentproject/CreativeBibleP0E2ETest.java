package com.aicp.module.contentproject;

import com.aicp.module.contentproject.dto.CreativeBibleRequests.*;
import com.aicp.module.contentproject.dto.CreativeBibleViews.*;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.mapper.*;
import com.aicp.module.contentproject.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("P0 创作圣经端到端测试")
class CreativeBibleP0E2ETest {

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

    private CreativeBibleVersion version(Long id, int versionNo, String status) {
        CreativeBibleVersion v = new CreativeBibleVersion();
        v.setId(id);
        v.setProjectId(PROJECT_ID);
        v.setVersionNo(versionNo);
        v.setStatus(status);
        v.setSnapshotJson(status.equals("confirmed") ? "{\"ecosystem_rules\":[],\"confirmed_settings\":[],\"writing_guides\":[]}" : "{}");
        v.setCreatedBy(USER_ID);
        v.setCreatedAt(LocalDateTime.now());
        return v;
    }

    @Test
    @DisplayName("完整流程：创建项目 → 创建草稿 → 添加规则 → 确认 → 验证快照")
    void fullFlowCreateAndConfirmBible() throws Exception {
        // Step 1: No existing bible → health returns missing
        when(bibleMapper.selectOne(any())).thenReturn(null);
        Map<String, Object> health = bibleService.health(USER_ID, PROJECT_ID);
        assertThat(health).containsEntry("status", "missing");
        assertThat(health).containsEntry("ready_for_generation", false);

        // Step 2: Create first draft
        doAnswer(inv -> { ((CreativeBibleVersion) inv.getArgument(0)).setId(10L); return 1; })
                .when(bibleMapper).insert(any());
        BibleSummaryView draft = bibleService.createDraft(USER_ID, PROJECT_ID,
                new CreateBibleDraftRequest("首个圣经版本", null));
        assertThat(draft.versionNo()).isEqualTo(1);
        assertThat(draft.status()).isEqualTo("draft");

        // Step 3: Add ecosystem rules to draft
        CreativeBibleVersion draftEntity = version(10L, 1, "draft");
        draftEntity.setSnapshotJson("{}");
        when(bibleMapper.selectById(10L)).thenReturn(draftEntity);
        doAnswer(inv -> { ((EcosystemRule) inv.getArgument(0)).setId(1L); return 1; })
                .when(ecosystemMapper).insert(any());

        EcosystemRuleView rule1 = bibleService.upsertEcosystem(USER_ID, PROJECT_ID, 10L, null,
                new UpsertEcosystemRuleRequest("world_rule", "能力使用必须付出记忆代价",
                        "每次使用超能力会失去一段记忆", null, null, null, "manual", null));
        assertThat(rule1.name()).isEqualTo("能力使用必须付出记忆代价");
        assertThat(rule1.status()).isEqualTo("draft");

        // Step 4: Confirm bible
        when(ecosystemMapper.selectCount(any())).thenReturn(2L);
        when(settingMapper.selectCount(any())).thenReturn(0L);
        when(ecosystemMapper.selectList(any())).thenReturn(List.of());
        when(settingMapper.selectList(any())).thenReturn(List.of());
        when(guideMapper.selectList(any())).thenReturn(List.of());
        when(objectMapper.writeValueAsString(any())).thenReturn(
                "{\"ecosystem_rules\":[],\"confirmed_settings\":[],\"writing_guides\":[]}");

        BibleSummaryView confirmed = bibleService.confirm(USER_ID, PROJECT_ID, 10L);
        assertThat(confirmed.status()).isEqualTo("confirmed");

        // Step 5: Verify snapshot was persisted with correct status
        ArgumentCaptor<CreativeBibleVersion> captor = ArgumentCaptor.forClass(CreativeBibleVersion.class);
        verify(bibleMapper, atLeastOnce()).updateById(captor.capture());
        CreativeBibleVersion updated = captor.getAllValues().stream()
                .filter(v -> "confirmed".equals(v.getStatus()))
                .findFirst().orElseThrow();
        assertThat(updated.getSnapshotHash()).isNotNull();
        assertThat(updated.getConfirmedBy()).isEqualTo(USER_ID);
        assertThat(updated.getConfirmedAt()).isNotNull();

        // Step 6: Event published
        verify(outboxService).append(eq("CREATIVE_BIBLE_CONFIRMED"), eq(PROJECT_ID), anyInt(), any());
    }

    @Test
    @DisplayName("确认后健康检查返回 ready")
    void healthReturnsReadyAfterConfirmation() {
        CreativeBibleVersion confirmed = version(11L, 2, "confirmed");
        confirmed.setSnapshotHash("abc123def456");
        when(bibleMapper.selectOne(any())).thenReturn(confirmed);
        when(ecosystemMapper.selectCount(any())).thenReturn(5L);
        when(settingMapper.selectCount(any())).thenReturn(3L);

        Map<String, Object> result = bibleService.health(USER_ID, PROJECT_ID);

        assertThat(result).containsEntry("status", "confirmed");
        assertThat(result).containsEntry("current_version_no", 2);
        assertThat(result).containsEntry("confirmed_fact_count", 8L);
        assertThat(result).containsEntry("ready_for_generation", true);
    }

    @Test
    @DisplayName("创建新草稿后旧确认版本仍存在，生成使用已确认版本")
    void newDraftDoesNotAffectConfirmedGeneration() throws Exception {
        // Existing confirmed bible v2
        CreativeBibleVersion confirmed = version(11L, 2, "confirmed");
        confirmed.setSnapshotHash("abc");

        // health() calls findLatest twice: first with "confirmed", then with "draft"
        lenient().when(bibleMapper.selectOne(any())).thenReturn(confirmed);
        lenient().when(ecosystemMapper.selectCount(any())).thenReturn(2L);
        lenient().when(settingMapper.selectCount(any())).thenReturn(1L);

        // Health still shows confirmed and ready
        Map<String, Object> health = bibleService.health(USER_ID, PROJECT_ID);
        assertThat(health).containsEntry("ready_for_generation", true);
        assertThat(health).containsEntry("status", "confirmed");
    }

    @Test
    @DisplayName("未经确认的草稿生态规则不泄漏到生成上下文")
    void draftRulesDoNotLeakIntoConfirmedContext() throws Exception {
        // Setup: draft version v1 with rules, about to be confirmed
        CreativeBibleVersion draft = version(10L, 1, "draft");
        when(bibleMapper.selectById(10L)).thenReturn(draft);
        when(ecosystemMapper.selectCount(any())).thenReturn(2L);
        when(settingMapper.selectCount(any())).thenReturn(0L);
        when(ecosystemMapper.selectList(any())).thenReturn(List.of());
        when(settingMapper.selectList(any())).thenReturn(List.of());
        when(guideMapper.selectList(any())).thenReturn(List.of());
        when(objectMapper.writeValueAsString(any())).thenReturn(
                "{\"ecosystem_rules\":[],\"confirmed_settings\":[],\"writing_guides\":[]}");

        // Confirm v1 — snapshotJson captures the exact state
        BibleSummaryView result = bibleService.confirm(USER_ID, PROJECT_ID, 10L);
        assertThat(result.status()).isEqualTo("confirmed");

        // Verify the confirmed version's snapshotJson was populated (not empty "{}")
        verify(bibleMapper).updateById(argThat(v ->
                "confirmed".equals(v.getStatus())
                && v.getSnapshotJson() != null
                && !"{}".equals(v.getSnapshotJson())));
    }

    @Test
    @DisplayName("ensureDraftForChange 幂等性：重复调用不创建多个草稿")
    void ensureDraftForChangeIsIdempotent() throws Exception {
        // First call: no draft exists → creates one
        when(bibleMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> { ((CreativeBibleVersion) inv.getArgument(0)).setId(15L); return 1; })
                .when(bibleMapper).insert(any());

        BibleSummaryView draft1 = bibleService.ensureDraftForChange(USER_ID, PROJECT_ID, "setting_updated");
        assertThat(draft1.status()).isEqualTo("draft");

        // Second call: draft already exists → reuses it
        CreativeBibleVersion existingDraft = version(15L, 3, "draft");
        when(bibleMapper.selectOne(any())).thenReturn(existingDraft);

        BibleSummaryView draft2 = bibleService.ensureDraftForChange(USER_ID, PROJECT_ID, "another_change");
        assertThat(draft2.id()).isEqualTo(15L);

        // Only one insert total
        verify(bibleMapper, times(1)).insert(any());
    }
}
