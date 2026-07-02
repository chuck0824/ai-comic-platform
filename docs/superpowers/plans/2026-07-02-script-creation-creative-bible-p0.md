# Script Creation Creative Bible P0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the P0 creative-bible foundation so confirmed ecosystem facts and three-level writing guides are versioned, visible in the work editor, and included in every new script-generation context snapshot.

**Architecture:** Extend the existing content-project and work-editor modules instead of creating a parallel editor. Add immutable creative-bible versions, ecosystem rules, scoped writing guides, and persisted generation-context snapshots; then connect them to `ContextAssembler`, the existing extraction flow, and the Vue work editor. P1 relationship graph, P2 canvas diff contracts, and P3 governance remain separate executable plans gated by this P0 acceptance suite.

**Tech Stack:** Java 17, Spring Boot 3.2, MyBatis-Plus, MySQL/H2, JUnit 5, Mockito, Vue 3 Composition API, Element Plus, Axios, Node test runner, Vite.

---

## 1. Scope and delivery sequence

This design spans four independently testable releases. Do not combine them into one branch:

| Release | Executable scope | Entry gate | Exit gate |
|---|---|---|---|
| P0 | Creative-bible shell, ecosystem facts, three-level writing guides, immutable bible/context snapshots, generation integration | Existing work-editor tests pass | Every new generation job persists the confirmed bible and resolved writing guide used |
| P1 | Editable relationship graph, relation events, chapter timeline, continuity and impact reports | P0 context snapshot contract is stable | Relations replay by unit and upstream changes generate actionable impact reports |
| P2 | Storyboard references, production gate, canvas import snapshots and user-confirmed diffs | P1 impact report contract is stable | Upstream changes never mutate an existing canvas snapshot |
| P3 | Configuration governance, large-project performance, advanced approvals, commercial/export gates and metrics | P2 cross-module events are stable | Operational and commercial acceptance targets pass |

This document is the executable P0 plan. After P0 acceptance, create these plans from the approved design without changing P0 contracts:

- `docs/superpowers/plans/2026-07-02-script-creation-creative-bible-p1-relations-quality.md`
- `docs/superpowers/plans/2026-07-02-script-creation-creative-bible-p2-canvas-contract.md`
- `docs/superpowers/plans/2026-07-02-script-creation-creative-bible-p3-governance.md`

## 2. File map

### Backend files to create

- `aicp-backend/src/main/resources/db/migration/V5__creative_bible_foundation.sql` — production migration for P0 tables and indexes.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/CreativeBibleVersion.java` — immutable project bible version.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/EcosystemRule.java` — structured ecosystem fact tied to a bible version.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/ProjectWritingGuide.java` — project, character, or content-unit guide version.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/GenerationContextSnapshot.java` — persisted context used by one generation job.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/CreativeBibleVersionMapper.java`
- `aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/EcosystemRuleMapper.java`
- `aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/ProjectWritingGuideMapper.java`
- `aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/GenerationContextSnapshotMapper.java`
- `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/CreativeBibleRequests.java` — validated write contracts.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/CreativeBibleViews.java` — stable read contracts.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/CreativeBibleService.java` — bible draft, confirm, health and ecosystem operations.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/WritingGuideResolver.java` — deterministic L1/L2/L3 inheritance.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/CreativeBibleController.java` — P0 HTTP boundary.
- `aicp-backend/src/test/java/com/aicp/module/contentproject/service/CreativeBibleServiceTest.java`
- `aicp-backend/src/test/java/com/aicp/module/contentproject/service/WritingGuideResolverTest.java`
- `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ContextAssemblerCreativeBibleTest.java`

### Backend files to modify

- `aicp-backend/src/main/resources/db/schema.sql` — production bootstrap schema.
- `aicp-backend/src/main/resources/db/schema-mysql.sql` — MySQL bootstrap schema.
- `aicp-backend/src/main/resources/db/schema-h2.sql` — dev/test schema.
- `aicp-backend/src/test/java/com/aicp/module/contentproject/schema/ContentProjectSchemaTest.java` — P0 table assertions.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/WorkEditorViews.java` — bible health in editor aggregate.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/WorkEditorService.java` — aggregate bible health and counts.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContextAssembler.java` — select confirmed bible and resolve writing guides.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContentGenerationJobService.java` — persist context snapshot after job insert.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectContextPublisher.java` — include confirmed bible metadata in parameter refresh events.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/SettingExtractionService.java` — create a bible draft after an applied extraction batch.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectSettingService.java` — invalidate/rebuild bible draft after confirmed setting changes.
- `aicp-backend/src/test/java/com/aicp/module/contentproject/service/WorkEditorServiceTest.java` — aggregate contract.
- `aicp-backend/src/test/java/com/aicp/module/contentproject/ContentProjectM1IntegrationTest.java` — end-to-end generation snapshot assertion.

### Frontend files to create

- `aicp-frontend/src/views/work-editor/CreativeBibleOverview.vue` — bible health, current version and pending changes.
- `aicp-frontend/src/views/work-editor/EcosystemPanel.vue` — structured ecosystem CRUD and confirmation view.
- `aicp-frontend/src/views/work-editor/WritingGuidePanel.vue` — L1/L2/L3 guide editor and resolution preview.
- `aicp-frontend/src/views/work-editor/creativeBibleData.js` — mode sections, normalization and guide merge helpers.
- `aicp-frontend/tests/creative-bible-data.test.js` — deterministic frontend helper tests.

### Frontend files to modify

- `aicp-frontend/src/api/contentProject.js` — P0 API methods.
- `aicp-frontend/src/views/TagEditor.vue` — render creative-bible sections in the existing shell.
- `aicp-frontend/src/views/work-editor/WorkInfoNav.vue` — add bible navigation and health badges.
- `aicp-frontend/src/views/work-editor/useWorkEditor.js` — load and refresh bible aggregate.
- `aicp-frontend/src/views/work-editor/workEditorData.js` — normalize new editor fields.
- `aicp-frontend/src/views/content-project/ContentProjectWorkspace.vue` — show bible health and generation-context preview.
- `aicp-frontend/src/views/content-project/components/ContextPanel.vue` — display selected bible/guide versions.
- `aicp-frontend/tests/work-editor-data.test.js` — editor response compatibility.
- `aicp-frontend/tests/content-project-workflow.test.js` — workspace contract.

## 3. Execution tasks

### Task 1: Add the P0 database contract

**Files:**
- Create: `aicp-backend/src/main/resources/db/migration/V5__creative_bible_foundation.sql`
- Modify: `aicp-backend/src/main/resources/db/schema.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/test/java/com/aicp/module/contentproject/schema/ContentProjectSchemaTest.java`

- [ ] **Step 1: Write the failing schema test**

Add this test without changing the existing M0 assertion:

```java
@Test
@DisplayName("P0 创作圣经四张表和关键唯一索引存在")
void creativeBibleFoundationExists() {
    for (String table : List.of(
            "creative_bible_versions", "ecosystem_rules",
            "project_writing_guides", "generation_context_snapshots")) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = ?",
                Integer.class, table.toUpperCase());
        assertThat(count).isEqualTo(1);
    }
    Integer indexCount = jdbc.queryForObject("""
            select count(*) from information_schema.indexes
            where table_name = 'CREATIVE_BIBLE_VERSIONS'
              and index_name = 'UK_CBV_PROJECT_VERSION'
            """, Integer.class);
    assertThat(indexCount).isEqualTo(1);
}
```

- [ ] **Step 2: Run the schema test and verify failure**

Run:

```bash
cd aicp-backend
mvn -Dtest=ContentProjectSchemaTest#creativeBibleFoundationExists test
```

Expected: FAIL because `CREATIVE_BIBLE_VERSIONS` does not exist.

- [ ] **Step 3: Add the migration and bootstrap DDL**

Use the same column names in all four schema files. The migration must contain:

```sql
CREATE TABLE creative_bible_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    source_version_id BIGINT NULL,
    summary VARCHAR(500) NULL,
    snapshot_json JSON NOT NULL,
    snapshot_hash VARCHAR(64) NULL,
    confirmed_by BIGINT NULL,
    confirmed_at TIMESTAMP NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cbv_project_version UNIQUE (project_id, version_no)
);

CREATE TABLE ecosystem_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    bible_version_id BIGINT NOT NULL,
    rule_type VARCHAR(40) NOT NULL,
    name VARCHAR(200) NOT NULL,
    summary TEXT NULL,
    details_json JSON NULL,
    scope_json JSON NULL,
    exceptions_json JSON NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    source_type VARCHAR(20) NOT NULL DEFAULT 'manual',
    evidence_json JSON NULL,
    revision INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE project_writing_guides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    bible_version_id BIGINT NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    scope_id BIGINT NOT NULL DEFAULT 0,
    version_no INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    guide_json JSON NOT NULL,
    parent_guide_id BIGINT NULL,
    source_type VARCHAR(20) NOT NULL DEFAULT 'manual',
    confirmed_by BIGINT NULL,
    confirmed_at TIMESTAMP NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pwg_scope_version UNIQUE
        (project_id, bible_version_id, scope_type, scope_id, version_no)
);

CREATE TABLE generation_context_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    generation_job_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    bible_version_id BIGINT NOT NULL,
    project_guide_id BIGINT NULL,
    unit_guide_id BIGINT NULL,
    selected_versions_json JSON NOT NULL,
    resolved_guide_json JSON NOT NULL,
    payload_json JSON NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gcs_job UNIQUE (generation_job_id)
);

CREATE INDEX idx_eco_project_bible ON ecosystem_rules(project_id, bible_version_id);
CREATE INDEX idx_pwg_project_scope ON project_writing_guides(project_id, scope_type, scope_id);
CREATE INDEX idx_gcs_project ON generation_context_snapshots(project_id);
```

For H2, use `JSON` as already used by `schema-h2.sql`; do not replace it with CLOB.

- [ ] **Step 4: Run schema verification**

Run:

```bash
cd aicp-backend
mvn -Dtest=ContentProjectSchemaTest test
```

Expected: PASS, including the existing M0 table test.

- [ ] **Step 5: Commit the schema slice**

```bash
git add aicp-backend/src/main/resources/db/migration/V5__creative_bible_foundation.sql \
  aicp-backend/src/main/resources/db/schema.sql \
  aicp-backend/src/main/resources/db/schema-mysql.sql \
  aicp-backend/src/main/resources/db/schema-h2.sql \
  aicp-backend/src/test/java/com/aicp/module/contentproject/schema/ContentProjectSchemaTest.java
git commit -m "feat: add creative bible foundation schema"
```

### Task 2: Add persistence entities, mappers, and DTO contracts

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/CreativeBibleVersion.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/EcosystemRule.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/ProjectWritingGuide.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/GenerationContextSnapshot.java`
- Create: four matching mapper interfaces under `aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/CreativeBibleRequests.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/CreativeBibleViews.java`

- [ ] **Step 1: Add a failing reflection contract test**

Create `aicp-backend/src/test/java/com/aicp/module/contentproject/domain/CreativeBibleContractTest.java`:

```java
package com.aicp.module.contentproject.domain;

import com.aicp.module.contentproject.entity.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreativeBibleContractTest {
    @Test
    void entitiesExposeVersionAndScopeContracts() {
        CreativeBibleVersion bible = new CreativeBibleVersion();
        bible.setVersionNo(1);
        bible.setStatus("confirmed");
        ProjectWritingGuide guide = new ProjectWritingGuide();
        guide.setScopeType("content_unit");
        guide.setScopeId(42L);
        GenerationContextSnapshot snapshot = new GenerationContextSnapshot();
        snapshot.setPayloadHash("abc");

        assertThat(bible.getVersionNo()).isEqualTo(1);
        assertThat(guide.getScopeType()).isEqualTo("content_unit");
        assertThat(snapshot.getPayloadHash()).isEqualTo("abc");
    }
}
```

- [ ] **Step 2: Run the contract test and verify failure**

```bash
cd aicp-backend
mvn -Dtest=CreativeBibleContractTest test
```

Expected: compilation FAIL because the entity classes do not exist.

- [ ] **Step 3: Implement entities and mapper interfaces**

Follow the existing Lombok/MyBatis pattern. Example:

```java
@Data
@TableName("creative_bible_versions")
public class CreativeBibleVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Integer versionNo;
    private String status;
    private Long sourceVersionId;
    private String summary;
    private String snapshotJson;
    private String snapshotHash;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

Each mapper is intentionally empty:

```java
public interface CreativeBibleVersionMapper extends BaseMapper<CreativeBibleVersion> {}
```

Define these request records in `CreativeBibleRequests`. The service normalizes project-scope `scopeId` to `0L`; character and content-unit scopes require a positive ID:

```java
public record CreateBibleDraftRequest(String summary, Long sourceVersionId) {}
public record UpsertEcosystemRuleRequest(
        @NotBlank String ruleType,
        @NotBlank String name,
        String summary,
        Map<String, Object> details,
        Map<String, Object> scope,
        List<Map<String, Object>> exceptions,
        String sourceType,
        Integer revision) {}
public record UpsertWritingGuideRequest(
        @Pattern(regexp = "project|character|content_unit") String scopeType,
        Long scopeId,
        Map<String, Object> guide,
        Long parentGuideId) {}
public record ResolveWritingGuideRequest(Long contentUnitId, List<Long> characterIds) {}
```

Define view records with `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` for `BibleSummaryView`, `EcosystemRuleView`, `WritingGuideView`, and `ResolvedWritingGuideView`. Do not return entity objects from controllers.

- [ ] **Step 4: Run contract and compile tests**

```bash
cd aicp-backend
mvn -Dtest=CreativeBibleContractTest test
mvn -DskipTests compile
```

Expected: both commands PASS.

- [ ] **Step 5: Commit persistence contracts**

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject/entity \
  aicp-backend/src/main/java/com/aicp/module/contentproject/mapper \
  aicp-backend/src/main/java/com/aicp/module/contentproject/dto/CreativeBibleRequests.java \
  aicp-backend/src/main/java/com/aicp/module/contentproject/dto/CreativeBibleViews.java \
  aicp-backend/src/test/java/com/aicp/module/contentproject/domain/CreativeBibleContractTest.java
git commit -m "feat: add creative bible persistence contracts"
```

### Task 3: Implement bible draft, ecosystem, confirm, and health services

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/CreativeBibleService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/CreativeBibleServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Cover access, versioning, confirmation, and health:

```java
@ExtendWith(MockitoExtension.class)
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

    @Test
    void createDraftUsesNextProjectVersion() {
        when(bibleMapper.selectList(any())).thenReturn(List.of(version(2, "confirmed")));
        doAnswer(inv -> { ((CreativeBibleVersion) inv.getArgument(0)).setId(9L); return 1; })
                .when(bibleMapper).insert(any());

        var result = service.createDraft(7L, 3L, new CreateBibleDraftRequest("调整生态", 2L));

        assertThat(result.versionNo()).isEqualTo(3);
        verify(accessService).require(7L, 3L, Action.EDIT_CONTENT);
    }

    @Test
    void confirmRejectsDraftWithNoEcosystemOrConfirmedSettings() {
        when(bibleMapper.selectById(9L)).thenReturn(version(3, "draft"));
        when(ecosystemMapper.selectCount(any())).thenReturn(0L);
        when(settingMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.confirm(7L, 3L, 9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("至少确认一项生态或实体设定");
    }

    @Test
    void confirmSupersedesOldVersionAndPublishesEvent() {
        CreativeBibleVersion draft = version(3, "draft");
        draft.setId(9L);
        when(bibleMapper.selectById(9L)).thenReturn(draft);
        when(ecosystemMapper.selectCount(any())).thenReturn(1L);
        when(settingMapper.selectCount(any())).thenReturn(0L);
        when(bibleMapper.selectList(any())).thenReturn(List.of(version(2, "confirmed")));

        var result = service.confirm(7L, 3L, 9L);

        assertThat(result.status()).isEqualTo("confirmed");
        verify(outboxService).append(eq("CREATIVE_BIBLE_CONFIRMED"), eq(3L), anyInt(), any());
    }
}
```

Use a test helper that always sets `projectId=3L` on versions.

- [ ] **Step 2: Run tests and verify failure**

```bash
cd aicp-backend
mvn -Dtest=CreativeBibleServiceTest test
```

Expected: compilation FAIL because `CreativeBibleService` does not exist.

- [ ] **Step 3: Implement minimal service behavior**

Implement these methods:

```java
public BibleSummaryView getCurrent(Long userId, Long projectId)
public BibleSummaryView createDraft(Long userId, Long projectId, CreateBibleDraftRequest request)
public Page<EcosystemRuleView> listEcosystem(Long userId, Long projectId, Long bibleVersionId,
                                             String ruleType, int page, int pageSize)
public EcosystemRuleView upsertEcosystem(Long userId, Long projectId, Long bibleVersionId,
                                         Long ruleId, UpsertEcosystemRuleRequest request)
@Transactional public BibleSummaryView confirm(Long userId, Long projectId, Long bibleVersionId)
public Map<String, Object> health(Long userId, Long projectId)
```

Confirmation rules:

```java
if (!"draft".equals(version.getStatus()) && !"reviewable".equals(version.getStatus())) {
    throw new BizException(ErrorCode.PARAM_INVALID, "只有草稿或待确认版本可以确认");
}
long factCount = ecosystemMapper.selectCount(projectAndBibleRules(projectId, bibleVersionId))
        + settingMapper.selectCount(confirmedProjectSettings(projectId));
if (factCount == 0) {
    throw new BizException(ErrorCode.PARAM_INVALID, "至少确认一项生态或实体设定");
}
```

Before confirming, materialize `snapshotJson` from canonicalized ecosystem rules, exact `project_setting_versions` snapshots selected for every confirmed setting, and confirmed writing guides. For each setting, select `(entity_id, current_version_no)` and fail confirmation if the matching version row is missing. Save `snapshotJson` and its SHA-256 `snapshotHash` on the bible version in the same transaction, then set the previous confirmed version to `superseded`. New draft rows initialize `snapshotJson` to `{}`. `ContextAssembler` must read this immutable snapshot and must never re-query live settings for a confirmed bible. Append `CREATIVE_BIBLE_CONFIRMED` with `project_id`, `bible_version_id`, `version_no`, and `snapshot_hash`.

When `createDraft` has `sourceVersionId`, copy the source bible's ecosystem rules and confirmed writing guides into draft rows, preserving source IDs in the copied JSON metadata. Do not copy the source status as confirmed; all copied rows start as draft. The source bible remains immutable.

Reject ecosystem or guide updates when the owning bible status is `confirmed`, `superseded`, or `archived`:

```java
if (!"draft".equals(bible.getStatus()) && !"reviewable".equals(bible.getStatus())) {
    throw new BizException(ErrorCode.PARAM_INVALID, "已确认的创作圣经不可修改，请先创建新草稿");
}
```

Health returns exactly:

```java
Map.of(
    "status", current == null ? "missing" : current.getStatus(),
    "current_version_id", current == null ? 0L : current.getId(),
    "current_version_no", current == null ? 0 : current.getVersionNo(),
    "confirmed_fact_count", confirmedFactCount,
    "pending_change_count", pendingCount,
    "ready_for_generation", current != null && "confirmed".equals(current.getStatus())
)
```

- [ ] **Step 4: Run service tests**

```bash
cd aicp-backend
mvn -Dtest=CreativeBibleServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the bible domain service**

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject/service/CreativeBibleService.java \
  aicp-backend/src/test/java/com/aicp/module/contentproject/service/CreativeBibleServiceTest.java
git commit -m "feat: add creative bible version workflow"
```

### Task 4: Implement deterministic three-level writing-guide resolution

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/WritingGuideResolver.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/WritingGuideResolverTest.java`

- [ ] **Step 1: Write failing inheritance tests**

```java
@ExtendWith(MockitoExtension.class)
class WritingGuideResolverTest {
    @Mock ProjectWritingGuideMapper guideMapper;
    @Mock ObjectMapper objectMapper;
    @InjectMocks WritingGuideResolver resolver;

    @Test
    void unitOverridesExplicitFieldAndInheritsRemainingProjectFields() throws Exception {
        when(guideMapper.selectList(any())).thenReturn(List.of(
                guide(1L, "project", null, "{\"pov\":\"third\",\"pace\":\"fast\",\"hard_bans\":[\"辱骂\"]}"),
                guide(2L, "content_unit", 8L, "{\"pace\":\"slow\"}")
        ));
        when(objectMapper.readValue(anyString(), eq(Map.class)))
                .thenAnswer(inv -> new ObjectMapper().readValue(inv.getArgument(0), Map.class));

        var result = resolver.resolve(4L, 3L, 8L, List.of());

        assertThat(result.resolved()).containsEntry("pov", "third").containsEntry("pace", "slow");
        assertThat(result.sourceByField()).containsEntry("pace", "content_unit:2");
    }

    @Test
    void characterGuideCannotOverrideHardBans() {
        when(guideMapper.selectList(any())).thenReturn(List.of(
                guide(1L, "project", 0L, "{\"hard_bans\":[\"辱骂\"]}"),
                guide(3L, "character", 9L, "{\"hard_bans\":[]}" )
        ));
        var result = resolver.resolve(4L, 3L, null, List.of(9L));
        assertThat(result.resolved().toString()).contains("辱骂");
        assertThat(result.conflicts()).contains("characters.9.hard_bans");
    }
}
```

Expand the second test with project guide `hard_bans=["辱骂"]` and character guide `hard_bans=[]`; assert the resolved list remains `["辱骂"]` and conflicts contain `hard_bans`.

- [ ] **Step 2: Run and verify failure**

```bash
cd aicp-backend
mvn -Dtest=WritingGuideResolverTest test
```

Expected: compilation FAIL because `WritingGuideResolver` does not exist.

- [ ] **Step 3: Implement the resolver**

Use this stable precedence:

```java
private static final Set<String> NON_OVERRIDABLE = Set.of(
        "hard_bans", "platform_rules", "compliance_rules");

// merge order: project -> each requested character -> content_unit
// character values live under resolved.characters.<characterId>
// unit values override project scalar fields only when explicitly present
```

Return `ResolvedWritingGuideView` with `resolved`, `sourceByField`, `conflicts`, `projectGuideId`, `characterGuideIds`, and `unitGuideId`. Sort character IDs before resolution so the output and hash are deterministic.

Reject `character` or `content_unit` guides with a null `scopeId`. Ignore draft guides; resolve only `status='confirmed'` and `bible_version_id` equal to the selected bible.

- [ ] **Step 4: Run resolver tests**

```bash
cd aicp-backend
mvn -Dtest=WritingGuideResolverTest test
```

Expected: PASS.

- [ ] **Step 5: Commit writing-guide resolution**

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject/service/WritingGuideResolver.java \
  aicp-backend/src/test/java/com/aicp/module/contentproject/service/WritingGuideResolverTest.java
git commit -m "feat: resolve hierarchical writing guides"
```

### Task 5: Persist the exact bible and guide context used by generation

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/ContentProjectViews.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContextAssembler.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContentGenerationJobService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ContextAssemblerCreativeBibleTest.java`

- [ ] **Step 1: Write failing context tests**

```java
@ExtendWith(MockitoExtension.class)
class ContextAssemblerCreativeBibleTest {
    @Mock ContentProjectMapper projectMapper;
    @Mock ProjectParameterVersionMapper parameterMapper;
    @Mock ContentVersionMapper contentVersionMapper;
    @Mock CreativeBibleVersionMapper bibleMapper;
    @Mock EcosystemRuleMapper ecosystemMapper;
    @Mock ProjectSettingEntityMapper settingMapper;
    @Mock WritingGuideResolver guideResolver;
    @Mock ObjectMapper objectMapper;
    @InjectMocks ContextAssembler assembler;

    @Test
    void assembleIncludesConfirmedBibleAndResolvedGuide() throws Exception {
        when(projectMapper.selectById(3L)).thenReturn(activeProject(3L));
        when(bibleMapper.selectOne(any())).thenReturn(confirmedBible(11L, 2));
        when(ecosystemMapper.selectList(any())).thenReturn(List.of(rule("world_rule", "能力有代价")));
        when(settingMapper.selectList(any())).thenReturn(List.of());
        when(guideResolver.resolve(3L, 11L, 8L, List.of())).thenReturn(resolvedGuide());
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"creative_bible\":{}}");

        ContextSnapshot snapshot = assembler.assemble(3L, requestForUnit(8L));

        assertThat(snapshot.selectedVersions()).containsEntry("creative_bible", 11L);
        assertThat(snapshot.bibleVersionId()).isEqualTo(11L);
        assertThat(snapshot.resolvedGuideJson()).contains("pace");
    }
}
```

Add a second test asserting `BizException("创作圣经尚未确认")` when no confirmed bible exists for a generative job type. Allow non-generative maintenance jobs only through an explicit `strategy.allow_unconfirmed_bible=true` flag used by migration tooling, never from the UI.

- [ ] **Step 2: Run and verify failure**

```bash
cd aicp-backend
mvn -Dtest=ContextAssemblerCreativeBibleTest test
```

Expected: compilation FAIL because `ContextSnapshot` lacks bible fields.

- [ ] **Step 3: Extend the context contract and persistence**

Change `ContextSnapshot` to:

```java
record ContextSnapshot(
        Map<String, Long> selectedVersions,
        Long bibleVersionId,
        Long projectGuideId,
        Long unitGuideId,
        String resolvedGuideJson,
        String payload,
        String contentHash) {}
```

In `ContextAssembler`, add `creative_bible` to the context after parameter/content versions and before `strategy`. Deserialize `CreativeBibleVersion.snapshotJson`; do not query current ecosystem, setting, or guide tables for generation. Resolve guides from the guide versions recorded inside that snapshot, with `targetId` when `targetType` is `content_unit`.

`GenerationJobRequest.strategy` is currently a JSON string. Parse it once with `ObjectMapper` into `Map<String,Object>`; accept `character_ids` only as a JSON array of positive numeric IDs. The migration-only bypass is the boolean JSON field `allow_unconfirmed_bible`, and `ContentGenerationJobController` must reject that field for normal HTTP requests so it is callable only from internal migration code.

In `ContentGenerationJobService.createJob`, insert `GenerationContextSnapshot` after `jobMapper.insert(job)` and before `executor.execute(job.getId())`:

```java
GenerationContextSnapshot persisted = new GenerationContextSnapshot();
persisted.setGenerationJobId(job.getId());
persisted.setProjectId(projectId);
persisted.setBibleVersionId(snapshot.bibleVersionId());
persisted.setProjectGuideId(snapshot.projectGuideId());
persisted.setUnitGuideId(snapshot.unitGuideId());
persisted.setSelectedVersionsJson(objectMapper.writeValueAsString(snapshot.selectedVersions()));
persisted.setResolvedGuideJson(snapshot.resolvedGuideJson());
persisted.setPayloadJson(snapshot.payload());
persisted.setPayloadHash(snapshot.contentHash());
contextSnapshotMapper.insert(persisted);
```

Wrap serialization failure in `BizException(ErrorCode.SYSTEM_ERROR, "生成上下文快照保存失败")`; do not start the executor when persistence fails.

- [ ] **Step 4: Run context and generation tests**

```bash
cd aicp-backend
mvn -Dtest=ContextAssemblerCreativeBibleTest,ContentProjectM1IntegrationTest test
```

Expected: PASS and a generation job has exactly one `generation_context_snapshots` row.

- [ ] **Step 5: Commit generation context integration**

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject/dto/ContentProjectViews.java \
  aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContextAssembler.java \
  aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContentGenerationJobService.java \
  aicp-backend/src/test/java/com/aicp/module/contentproject/service/ContextAssemblerCreativeBibleTest.java \
  aicp-backend/src/test/java/com/aicp/module/contentproject/ContentProjectM1IntegrationTest.java
git commit -m "feat: snapshot creative bible generation context"
```

### Task 6: Expose P0 creative-bible APIs with permission and validation

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/CreativeBibleController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/CreativeBibleApiIntegrationTest.java`

- [ ] **Step 1: Write failing API integration tests**

Use `@SpringBootTest`, `@AutoConfigureMockMvc`, and the existing authenticated test setup. Cover:

```java
mockMvc.perform(get("/api/v1/content-projects/{id}/creative-bible/health", projectId)
        .header("Authorization", ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.ready_for_generation").value(false));

mockMvc.perform(post("/api/v1/content-projects/{id}/creative-bible/versions", projectId)
        .header("Authorization", ownerToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"summary\":\"首个圣经版本\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("draft"));
```

Also assert a Viewer receives 403 on POST and invalid `scope_type` receives 400.

- [ ] **Step 2: Run and verify failure**

```bash
cd aicp-backend
mvn -Dtest=CreativeBibleApiIntegrationTest test
```

Expected: FAIL with 404 because routes do not exist.

- [ ] **Step 3: Implement controller routes**

Expose exactly:

```java
GET  /{id}/creative-bible
GET  /{id}/creative-bible/health
POST /{id}/creative-bible/versions
POST /{id}/creative-bible/versions/{versionId}/confirm
GET  /{id}/creative-bible/versions/{versionId}/ecosystem-rules
POST /{id}/creative-bible/versions/{versionId}/ecosystem-rules
PATCH /{id}/creative-bible/versions/{versionId}/ecosystem-rules/{ruleId}
GET  /{id}/creative-bible/versions/{versionId}/writing-guides
POST /{id}/creative-bible/versions/{versionId}/writing-guides
POST /{id}/creative-bible/versions/{versionId}/writing-guides/resolve
```

Use `@Valid @RequestBody` and `SecurityUtil.requireCurrentUserId()`. The controller delegates; all project ownership and action checks remain in services.

- [ ] **Step 4: Run API and service tests**

```bash
cd aicp-backend
mvn -Dtest=CreativeBibleApiIntegrationTest,CreativeBibleServiceTest,WritingGuideResolverTest test
```

Expected: PASS.

- [ ] **Step 5: Commit API boundary**

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject/controller/CreativeBibleController.java \
  aicp-backend/src/test/java/com/aicp/module/contentproject/CreativeBibleApiIntegrationTest.java
git commit -m "feat: expose creative bible APIs"
```

### Task 7: Connect existing settings/extraction changes to bible drafts and editor health

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/SettingExtractionService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectSettingService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectContextPublisher.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/WorkEditorViews.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/WorkEditorService.java`
- Modify: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/SettingExtractionServiceTest.java`
- Modify: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ProjectSettingServiceTest.java`
- Modify: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/WorkEditorServiceTest.java`

- [ ] **Step 1: Write failing invalidation and aggregate tests**

After a confirmed setting update, assert:

```java
verify(creativeBibleService).ensureDraftForChange(userId, projectId, "setting_changed");
```

After `applyExtraction`, assert the same call occurs once after the transaction applies all candidates, not once per candidate.

Extend `EditorView` assertion:

```java
assertThat(result.bibleHealth().get("ready_for_generation")).isEqualTo(false);
assertThat(result.pendingExtractionCount()).isEqualTo(2);
```

- [ ] **Step 2: Run and verify failure**

```bash
cd aicp-backend
mvn -Dtest=SettingExtractionServiceTest,ProjectSettingServiceTest,WorkEditorServiceTest test
```

Expected: compilation FAIL because `ensureDraftForChange` and `bibleHealth` are absent.

- [ ] **Step 3: Add one draft invalidation per user action**

Add to `CreativeBibleService`:

```java
@Transactional
public BibleSummaryView ensureDraftForChange(Long userId, Long projectId, String reason) {
    CreativeBibleVersion existingDraft = findLatest(projectId, "draft");
    if (existingDraft != null) return toSummary(existingDraft);
    CreativeBibleVersion confirmed = findLatest(projectId, "confirmed");
    return createDraft(userId, projectId,
            new CreateBibleDraftRequest(reason, confirmed == null ? null : confirmed.getId()));
}
```

Call it after successful confirmed-setting updates and once at the end of extraction apply. Do not call `ProjectContextPublisher.publish` for a draft. On bible confirmation, call `ProjectContextPublisher.publish` and include `creative_bible_version_id` in `CONTEXT_REFRESH`.

Extend `EditorView` with `Map<String,Object> bibleHealth`; compute `pendingExtractionCount` from batches in `review_pending` or `conflicted` instead of the current hard-coded zero.

- [ ] **Step 4: Run affected tests**

```bash
cd aicp-backend
mvn -Dtest=SettingExtractionServiceTest,ProjectSettingServiceTest,WorkEditorServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit work-editor integration**

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject/service/SettingExtractionService.java \
  aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectSettingService.java \
  aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectContextPublisher.java \
  aicp-backend/src/main/java/com/aicp/module/contentproject/dto/WorkEditorViews.java \
  aicp-backend/src/main/java/com/aicp/module/contentproject/service/WorkEditorService.java \
  aicp-backend/src/test/java/com/aicp/module/contentproject/service
git commit -m "feat: connect work editor to creative bible"
```

### Task 8: Add frontend contracts and deterministic view-model helpers

**Files:**
- Modify: `aicp-frontend/src/api/contentProject.js`
- Create: `aicp-frontend/src/views/work-editor/creativeBibleData.js`
- Create: `aicp-frontend/tests/creative-bible-data.test.js`
- Modify: `aicp-frontend/src/views/work-editor/workEditorData.js`
- Modify: `aicp-frontend/tests/work-editor-data.test.js`

- [ ] **Step 1: Write failing helper tests**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { sectionsForMode, mergeWritingGuide, normalizeBibleHealth } from '../src/views/work-editor/creativeBibleData.js'

test('long form shows full ecosystem while short drama stays compact', () => {
  assert.deepEqual(sectionsForMode('long_form'), ['overview', 'ecosystem', 'characters', 'relations', 'writing', 'continuity'])
  assert.deepEqual(sectionsForMode('short_drama'), ['overview', 'ecosystem', 'characters', 'relations', 'writing'])
})

test('unit guide overrides explicit field but cannot clear hard bans', () => {
  const result = mergeWritingGuide(
    { pace: 'fast', hard_bans: ['辱骂'] },
    { pace: 'slow', hard_bans: [] }
  )
  assert.equal(result.resolved.pace, 'slow')
  assert.deepEqual(result.resolved.hard_bans, ['辱骂'])
  assert.deepEqual(result.conflicts, ['hard_bans'])
})

test('missing health normalizes to a safe blocked state', () => {
  assert.deepEqual(normalizeBibleHealth(null), {
    status: 'missing', current_version_id: 0, current_version_no: 0,
    confirmed_fact_count: 0, pending_change_count: 0, ready_for_generation: false
  })
})
```

- [ ] **Step 2: Run and verify failure**

```bash
cd aicp-frontend
node --test tests/creative-bible-data.test.js tests/work-editor-data.test.js
```

Expected: FAIL because `creativeBibleData.js` does not exist.

- [ ] **Step 3: Implement helpers and API methods**

Add these methods to `contentProjectApi`:

```js
getCreativeBible: (projectId) => request.get(`/content-projects/${projectId}/creative-bible`),
getCreativeBibleHealth: (projectId) => request.get(`/content-projects/${projectId}/creative-bible/health`),
createBibleDraft: (projectId, data) => request.post(`/content-projects/${projectId}/creative-bible/versions`, data),
confirmBible: (projectId, versionId) => request.post(`/content-projects/${projectId}/creative-bible/versions/${versionId}/confirm`),
listEcosystemRules: (projectId, versionId, params) => request.get(`/content-projects/${projectId}/creative-bible/versions/${versionId}/ecosystem-rules`, { params }),
createEcosystemRule: (projectId, versionId, data) => request.post(`/content-projects/${projectId}/creative-bible/versions/${versionId}/ecosystem-rules`, data),
updateEcosystemRule: (projectId, versionId, ruleId, data) => request.patch(`/content-projects/${projectId}/creative-bible/versions/${versionId}/ecosystem-rules/${ruleId}`, data),
listWritingGuides: (projectId, versionId, params) => request.get(`/content-projects/${projectId}/creative-bible/versions/${versionId}/writing-guides`, { params }),
saveWritingGuide: (projectId, versionId, data) => request.post(`/content-projects/${projectId}/creative-bible/versions/${versionId}/writing-guides`, data),
resolveWritingGuide: (projectId, versionId, data) => request.post(`/content-projects/${projectId}/creative-bible/versions/${versionId}/writing-guides/resolve`, data)
```

Implement `mergeWritingGuide` with immutable object copies and the same `NON_OVERRIDABLE` keys as the backend. `normalizeEditorResponse` must preserve old responses by normalizing absent `bible_health` through `normalizeBibleHealth`.

- [ ] **Step 4: Run helper tests**

```bash
cd aicp-frontend
node --test tests/creative-bible-data.test.js tests/work-editor-data.test.js
```

Expected: PASS.

- [ ] **Step 5: Commit frontend contracts**

```bash
git add aicp-frontend/src/api/contentProject.js \
  aicp-frontend/src/views/work-editor/creativeBibleData.js \
  aicp-frontend/src/views/work-editor/workEditorData.js \
  aicp-frontend/tests/creative-bible-data.test.js \
  aicp-frontend/tests/work-editor-data.test.js
git commit -m "feat: add creative bible frontend contracts"
```

### Task 9: Add the creative-bible overview and ecosystem editor to the existing shell

**Files:**
- Create: `aicp-frontend/src/views/work-editor/CreativeBibleOverview.vue`
- Create: `aicp-frontend/src/views/work-editor/EcosystemPanel.vue`
- Modify: `aicp-frontend/src/views/work-editor/WorkInfoNav.vue`
- Modify: `aicp-frontend/src/views/TagEditor.vue`
- Modify: `aicp-frontend/src/views/work-editor/useWorkEditor.js`
- Modify: `aicp-frontend/tests/navigation-contract.test.js`

- [ ] **Step 1: Write failing navigation contract assertions**

Add assertions that the work-editor navigation source contains stable keys:

```js
assert.match(navSource, /key:\s*['"]bible-overview['"]/)
assert.match(navSource, /key:\s*['"]ecosystem['"]/)
assert.match(tagEditorSource, /CreativeBibleOverview/)
assert.match(tagEditorSource, /EcosystemPanel/)
```

- [ ] **Step 2: Run and verify failure**

```bash
cd aicp-frontend
node --test tests/navigation-contract.test.js
```

Expected: FAIL because the sections are absent.

- [ ] **Step 3: Implement overview and ecosystem UI**

`CreativeBibleOverview.vue` must show:

- current version and status;
- confirmed fact count and pending change count;
- `ready_for_generation` state;
- one primary action: create draft, continue editing, or confirm current draft;
- a confirmation dialog before confirm.

`EcosystemPanel.vue` uses a type filter and master/detail layout. Use these P0 rule types and labels:

```js
export const ECOSYSTEM_RULE_TYPES = [
  ['era_world', '时代与世界'], ['world_rule', '世界规则'],
  ['social_structure', '社会结构'], ['institution_taboo', '制度与禁忌'],
  ['faction_organization', '势力与组织'], ['resource_system', '资源体系'],
  ['ability_system', '能力体系'], ['location_system', '地点体系'],
  ['key_history', '关键历史']
]
```

Reuse the current `TagEditor.vue` shell, permissions, loading state and leave protection. Add navigation under a new “创作圣经” group. Do not add a new top-level route.

- [ ] **Step 4: Run tests and build**

```bash
cd aicp-frontend
node --test tests/navigation-contract.test.js tests/work-editor-data.test.js tests/creative-bible-data.test.js
npm run build
```

Expected: tests PASS and Vite build exits 0.

- [ ] **Step 5: Commit the bible shell UI**

```bash
git add aicp-frontend/src/views/TagEditor.vue \
  aicp-frontend/src/views/work-editor/CreativeBibleOverview.vue \
  aicp-frontend/src/views/work-editor/EcosystemPanel.vue \
  aicp-frontend/src/views/work-editor/WorkInfoNav.vue \
  aicp-frontend/src/views/work-editor/useWorkEditor.js \
  aicp-frontend/tests/navigation-contract.test.js
git commit -m "feat: add creative bible ecosystem editor"
```

### Task 10: Add writing-guide editing and resolution preview

**Files:**
- Create: `aicp-frontend/src/views/work-editor/WritingGuidePanel.vue`
- Modify: `aicp-frontend/src/views/TagEditor.vue`
- Modify: `aicp-frontend/src/views/work-editor/WorkInfoNav.vue`
- Modify: `aicp-frontend/tests/navigation-contract.test.js`
- Modify: `aicp-frontend/tests/creative-bible-data.test.js`

- [ ] **Step 1: Add failing UI contract tests**

Assert the panel source contains all three scopes and non-overridable warning text:

```js
assert.match(writingPanelSource, /项目级口径/)
assert.match(writingPanelSource, /角色级口吻/)
assert.match(writingPanelSource, /单集\/单章覆盖/)
assert.match(writingPanelSource, /平台规则和合规禁区不可覆盖/)
assert.match(navSource, /key:\s*['"]writing-guide['"]/)
```

- [ ] **Step 2: Run and verify failure**

```bash
cd aicp-frontend
node --test tests/navigation-contract.test.js tests/creative-bible-data.test.js
```

Expected: FAIL because `WritingGuidePanel.vue` is absent.

- [ ] **Step 3: Implement the writing-guide panel**

Render structured fields rather than a single note textarea:

```js
const PROJECT_FIELDS = ['pov', 'tense', 'pace', 'language_density', 'tone', 'dialogue_ratio', 'hard_bans', 'terminology']
const CHARACTER_FIELDS = ['addressing', 'sentence_length', 'favorite_words', 'catchphrases', 'knowledge_boundary', 'hidden_information', 'forbidden_words']
const UNIT_FIELDS = ['pov', 'pace', 'special_form', 'dialogue_constraints', 'must_include', 'must_avoid']
```

The scope selector requires a character ID for character scope and content-unit ID for unit scope. “解析预览” calls `resolveWritingGuide` and displays final values, source per field and conflicts. Save creates a new guide version; it never patches a confirmed guide.

- [ ] **Step 4: Run frontend tests and build**

```bash
cd aicp-frontend
node --test tests/navigation-contract.test.js tests/creative-bible-data.test.js
npm run build
```

Expected: PASS.

- [ ] **Step 5: Commit writing-guide UI**

```bash
git add aicp-frontend/src/views/TagEditor.vue \
  aicp-frontend/src/views/work-editor/WritingGuidePanel.vue \
  aicp-frontend/src/views/work-editor/WorkInfoNav.vue \
  aicp-frontend/tests/navigation-contract.test.js \
  aicp-frontend/tests/creative-bible-data.test.js
git commit -m "feat: add hierarchical writing guide editor"
```

### Task 11: Surface bible readiness and selected context in the creation workspace

**Files:**
- Modify: `aicp-frontend/src/views/content-project/ContentProjectWorkspace.vue`
- Modify: `aicp-frontend/src/views/content-project/components/ContextPanel.vue`
- Modify: `aicp-frontend/tests/content-project-workflow.test.js`

- [ ] **Step 1: Write failing workspace contract tests**

```js
assert.match(workspaceSource, /getCreativeBibleHealth/)
assert.match(workspaceSource, /创作圣经尚未确认/)
assert.match(contextPanelSource, /圣经版本/)
assert.match(contextPanelSource, /本单元写作口径/)
```

- [ ] **Step 2: Run and verify failure**

```bash
cd aicp-frontend
node --test tests/content-project-workflow.test.js
```

Expected: FAIL because workspace readiness is absent.

- [ ] **Step 3: Add readiness and generation preview**

Load health in the existing `loadProject()` promise set. Before `triggerGeneration`, block when `ready_for_generation` is false:

```js
if (!bibleHealth.value?.ready_for_generation) {
  ElMessage.warning('创作圣经尚未确认，请先确认生态或实体设定')
  router.push(`/script-gen/${projectId.value}/edit/bible-overview`)
  return
}
```

Pass to `ContextPanel`:

```vue
:bible-health="bibleHealth"
:selected-context="currentJob?.selected_context"
```

Display current bible version, resolved project/unit guide IDs, upstream content versions and context hash when the API returns them. Do not display raw prompt payloads.

- [ ] **Step 4: Run workspace tests and build**

```bash
cd aicp-frontend
node --test tests/content-project-workflow.test.js tests/navigation-contract.test.js
npm run build
```

Expected: PASS.

- [ ] **Step 5: Commit workspace integration**

```bash
git add aicp-frontend/src/views/content-project/ContentProjectWorkspace.vue \
  aicp-frontend/src/views/content-project/components/ContextPanel.vue \
  aicp-frontend/tests/content-project-workflow.test.js
git commit -m "feat: enforce creative bible generation readiness"
```

### Task 12: Add migration compatibility and P0 end-to-end acceptance

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/LegacyProjectProjectionService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/CreativeBibleP0E2ETest.java`
- Modify: `aicp-frontend/tests/content-project-workflow.test.js`
- Modify: `docs/superpowers/specs/2026-07-02-script-creation-creative-bible-design.md`

- [ ] **Step 1: Write failing legacy and E2E tests**

Backend E2E flow:

```java
@Test
void confirmedBibleFlowsIntoGenerationWithoutLeakingDraftFacts() {
    long projectId = createLongFormProject();
    long draftId = createBibleDraft(projectId);
    createEcosystemRule(projectId, draftId, "world_rule", "能力使用必须付出记忆代价");
    createProjectGuide(projectId, draftId, Map.of("pov", "third", "pace", "fast"));
    confirmBible(projectId, draftId);

    long newerDraftId = createBibleDraft(projectId);
    createEcosystemRule(projectId, newerDraftId, "world_rule", "未经确认的规则");
    long jobId = createSynopsisGenerationJob(projectId);

    Map<String, Object> snapshot = loadGenerationContext(jobId);
    assertThat(snapshot).containsEntry("bible_version_id", draftId);
    assertThat(snapshot.toString()).contains("记忆代价").doesNotContain("未经确认的规则");
}
```

Legacy assertion: backfilling existing settings creates one bible draft, not confirmed; rerunning backfill creates no duplicate version.

- [ ] **Step 2: Run and verify failure**

```bash
cd aicp-backend
mvn -Dtest=CreativeBibleP0E2ETest test
```

Expected: FAIL until legacy draft creation and context read helpers are complete.

- [ ] **Step 3: Implement idempotent legacy draft creation**

In `LegacyProjectProjectionService`, after setting migration:

```java
creativeBibleService.ensureDraftForChange(userId, projectId, "legacy_settings_backfill");
```

Do not auto-confirm migrated facts. Add an outbox event `CREATIVE_BIBLE_MIGRATION_REVIEW_REQUIRED` once per project using the existing outbox idempotency pattern.

Update the design document status to `P0 implementation plan approved for execution` only after this plan is approved; do not mark implementation complete.

- [ ] **Step 4: Run the complete P0 verification suite**

Backend:

```bash
cd aicp-backend
mvn -Dtest=ContentProjectSchemaTest,CreativeBibleContractTest,CreativeBibleServiceTest,WritingGuideResolverTest,ContextAssemblerCreativeBibleTest,CreativeBibleApiIntegrationTest,CreativeBibleP0E2ETest,WorkEditorServiceTest,ProjectSettingServiceTest,SettingExtractionServiceTest,ContentProjectM1IntegrationTest test
```

Expected: all listed tests PASS.

Frontend:

```bash
cd aicp-frontend
node --test tests/work-editor-data.test.js tests/creative-bible-data.test.js tests/navigation-contract.test.js tests/content-project-workflow.test.js
npm run build
```

Expected: all tests PASS and build exits 0.

- [ ] **Step 5: Run full regression and inspect repository state**

```bash
cd aicp-backend && mvn test
cd ../aicp-frontend && node --test tests/*.test.js && npm run build
cd .. && git status --short
```

Expected: backend and frontend regression suites PASS. `git status --short` shows only intended P0 files plus any pre-existing unrelated user changes; do not stage unrelated files or generated `aicp-backend/src/main/resources/static/assets/*` output.

- [ ] **Step 6: Commit the P0 acceptance slice**

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject/service/LegacyProjectProjectionService.java \
  aicp-backend/src/test/java/com/aicp/module/contentproject/CreativeBibleP0E2ETest.java \
  aicp-frontend/tests/content-project-workflow.test.js \
  docs/superpowers/specs/2026-07-02-script-creation-creative-bible-design.md
git commit -m "test: verify creative bible P0 workflow"
```

## 4. P0 acceptance checklist

- [ ] A confirmed bible is required for new synopsis, outline, and content generation jobs.
- [ ] Draft ecosystem rules and draft writing guides never appear in a generation context snapshot.
- [ ] Every generation job has exactly one persisted context snapshot with bible, guide, upstream version, payload, and hash fields.
- [ ] Project, character, and content-unit guides resolve deterministically; hard bans and compliance rules cannot be overridden.
- [ ] Applying AI-extracted settings creates or reuses one bible draft and never auto-confirms it.
- [ ] Existing work-editor routes and legacy script links remain functional.
- [ ] Long-form, short-drama, and TVC projects receive mode-appropriate navigation without separate data models.
- [ ] Viewer writes return 403; revision conflicts return 409; invalid scope and rule types return 400.
- [ ] AI, parsing, serialization, and persistence failures remain explicit failures with no sample-data fallback.
- [ ] Backend regression, frontend tests, and production frontend build pass.

## 5. P1 planning gate

Create the P1 executable plan only after the P0 acceptance checklist passes and the persisted snapshot schema is stable. P1 must consume `creative_bible_versions.id`, `content_versions.id`, and `generation_context_snapshots.id`; it must not add a second facts store or change the three-level guide precedence.
