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
    character_guide_ids_json JSON NULL,
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

**H2/MySQL JSON compatibility note:**

H2's `JSON` type is an alias for `CLOB` and does not support MySQL-specific JSON functions (`JSON_EXTRACT`, `JSON_CONTAINS`, `JSON_ARRAY_APPEND`, etc.). To keep behavior consistent across both databases:

- **DAO 层禁止使用数据库特有的 JSON 函数**。All JSON manipulation (`details_json`, `guide_json`, `snapshot_json`, etc.) must happen in Java via `ObjectMapper`, never in SQL `WHERE` or `SELECT` clauses.
- `ecosystem_rules.details_json` 的查询过滤通过 Java 端 `rule_type` + 分页实现，不在 SQL 中按 JSON 内部字段筛选。
- If a future query must filter inside JSON (e.g., P1 impact reports), add a dedicated indexed column rather than using `JSON_EXTRACT` in a `WHERE` clause.
- Add a CI-only integration test that inserts and reads back a row with non-ASCII JSON content (e.g., `{"名称":"测试规则","描述":"包含中文和emoji: 🎬"}`) and asserts the round-trip is byte-identical on both H2 and MySQL profiles.

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

`GenerationContextSnapshot` entity must include the character guide list:

```java
@Data
@TableName("generation_context_snapshots")
public class GenerationContextSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long generationJobId;
    private Long projectId;
    private Long bibleVersionId;
    private Long projectGuideId;
    private String characterGuideIdsJson;
    private Long unitGuideId;
    private String selectedVersionsJson;
    private String resolvedGuideJson;
    private String payloadJson;
    private String payloadHash;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
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

Cover access, versioning, confirmation, superseded transition, and health:

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
    void createDraftFromSourceCopiesEcosystemRulesAndWritingGuides() {
        // source bible v2 has 2 rules and 1 confirmed guide
        CreativeBibleVersion source = version(2, "confirmed");
        source.setId(2L);
        when(bibleMapper.selectById(2L)).thenReturn(source);
        when(bibleMapper.selectList(any())).thenReturn(List.of(source));
        when(ecosystemMapper.selectList(argThat(q ->
            q.toString().contains("bible_version_id=2"))))
            .thenReturn(List.of(rule("world_rule", "能力有代价"), rule("key_history", "大洪水")));
        when(guideMapper.selectList(argThat(q ->
            q.toString().contains("bible_version_id=2"))))
            .thenReturn(List.of(confirmedGuide("project", 0L, Map.of("pov", "third"))));
        doAnswer(inv -> { ((CreativeBibleVersion) inv.getArgument(0)).setId(10L); return 1; })
                .when(bibleMapper).insert(any());

        var result = service.createDraft(7L, 3L, new CreateBibleDraftRequest("基于v2修改", 2L));

        assertThat(result.versionNo()).isEqualTo(3);
        // verify copied rules belong to new draft bible
        verify(ecosystemMapper, times(2)).insert(argThat(r ->
            r.getBibleVersionId().equals(10L) && "draft".equals(r.getStatus())));
        // verify copied guide belongs to new draft bible
        verify(guideMapper).insert(argThat(g ->
            g.getBibleVersionId().equals(10L) && "draft".equals(g.getStatus())));
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
        CreativeBibleVersion oldConfirmed = version(2, "confirmed");
        oldConfirmed.setId(5L);
        when(bibleMapper.selectById(9L)).thenReturn(draft);
        when(ecosystemMapper.selectCount(any())).thenReturn(1L);
        when(settingMapper.selectCount(any())).thenReturn(0L);
        when(bibleMapper.selectList(any())).thenReturn(List.of(oldConfirmed));

        var result = service.confirm(7L, 3L, 9L);

        assertThat(result.status()).isEqualTo("confirmed");
        verify(outboxService).append(eq("CREATIVE_BIBLE_CONFIRMED"), eq(3L), anyInt(), any());
        // verify old confirmed version was superseded
        verify(bibleMapper).updateById(argThat(v ->
            "superseded".equals(v.getStatus()) && v.getId().equals(5L)));
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
@Transactional public BibleSummaryView submitReview(Long userId, Long projectId, Long bibleVersionId)
@Transactional public void archive(Long userId, Long projectId, Long bibleVersionId)
public Map<String, Object> health(Long userId, Long projectId)
```

`submitReview` transitions `draft → reviewable` (allowed only from `draft`). `archive` requires `superseded` or `confirmed` status; if `confirmed`, must verify no active downstream dependencies (generation jobs referencing this bible version). P0 stubs the dependency check — P1 adds the full check against `storyboard_masters` and `canvas_import_snapshots`.

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

**`snapshotJson` construction (inside confirm, before saving the version):**

```java
Map<String, Object> snapshot = new LinkedHashMap<>();

// 1. Canonicalize ecosystem rules (sorted by rule_type then id for determinism)
List<EcosystemRule> rules = ecosystemMapper.selectList(
    new LambdaQueryWrapper<EcosystemRule>()
        .eq(EcosystemRule::getProjectId, projectId)
        .eq(EcosystemRule::getBibleVersionId, bibleVersionId)
        .eq(EcosystemRule::getStatus, "draft")
        .orderByAsc(EcosystemRule::getRuleType, EcosystemRule::getId));
snapshot.put("ecosystem_rules", rules.stream().map(r -> Map.of(
    "id", r.getId(), "rule_type", r.getRuleType(),
    "name", r.getName(), "summary", r.getSummary(),
    "details", parseJson(r.getDetailsJson()),
    "scope", parseJson(r.getScopeJson()),
    "exceptions", parseJson(r.getExceptionsJson()),
    "revision", r.getRevision()
)).toList());

// 2. Snapshot confirmed settings via their version rows
List<ProjectSettingEntity> confirmedSettings = settingMapper.selectList(
    new LambdaQueryWrapper<ProjectSettingEntity>()
        .eq(ProjectSettingEntity::getProjectId, projectId)
        .eq(ProjectSettingEntity::getStatus, "confirmed"));
List<Map<String, Object>> settingSnapshots = new ArrayList<>();
for (ProjectSettingEntity s : confirmedSettings) {
    ProjectSettingVersion ver = settingVersionMapper.selectOne(
        new LambdaQueryWrapper<ProjectSettingVersion>()
            .eq(ProjectSettingVersion::getEntityId, s.getId())
            .eq(ProjectSettingVersion::getVersionNo, s.getCurrentVersionNo()));
    if (ver == null) {
        throw new BizException(ErrorCode.DATA_NOT_FOUND,
            "实体设定版本缺失: entity=" + s.getId() + " v" + s.getCurrentVersionNo());
    }
    settingSnapshots.add(Map.of(
        "entity_id", s.getId(), "entity_type", s.getEntityType(),
        "name", s.getName(), "version_no", ver.getVersionNo(),
        "details", parseJson(ver.getDetailsJson())));
}
snapshot.put("confirmed_settings", settingSnapshots);

// 3. Confirmed writing guides for this bible version
List<ProjectWritingGuide> guides = guideMapper.selectList(
    new LambdaQueryWrapper<ProjectWritingGuide>()
        .eq(ProjectWritingGuide::getProjectId, projectId)
        .eq(ProjectWritingGuide::getBibleVersionId, bibleVersionId)
        .eq(ProjectWritingGuide::getStatus, "confirmed"));
snapshot.put("writing_guides", guides.stream().map(g -> Map.of(
    "id", g.getId(), "scope_type", g.getScopeType(),
    "scope_id", g.getScopeId(), "version_no", g.getVersionNo(),
    "guide", parseJson(g.getGuideJson())
)).toList());

String snapshotJson = objectMapper.writeValueAsString(snapshot);
version.setSnapshotJson(snapshotJson);
version.setSnapshotHash(DigestUtils.sha256Hex(snapshotJson));
```

The confirm test must assert `snapshotJson` is not `"{}"` and contains keys `ecosystem_rules`, `confirmed_settings`, and `writing_guides`. Add a dedicated test:

```java
@Test
void confirmMaterializesFullSnapshotWithAllThreeSections() {
    // setup: draft bible with 1 ecosystem rule, 1 confirmed setting with version, 1 confirmed guide
    when(bibleMapper.selectById(9L)).thenReturn(draft);
    when(ecosystemMapper.selectList(any())).thenReturn(List.of(rule));
    when(settingMapper.selectList(any())).thenReturn(List.of(setting));
    when(settingVersionMapper.selectOne(any())).thenReturn(settingVersion);
    when(guideMapper.selectList(any())).thenReturn(List.of(guide));

    service.confirm(7L, 3L, 9L);

    ArgumentCaptor<CreativeBibleVersion> captor = ArgumentCaptor.forClass(CreativeBibleVersion.class);
    verify(bibleMapper).updateById(captor.capture());
    CreativeBibleVersion saved = captor.getValue();
    assertThat(saved.getSnapshotJson()).contains("ecosystem_rules", "confirmed_settings", "writing_guides");
    assertThat(saved.getSnapshotHash()).isNotBlank().hasSize(64);
}
```

When `createDraft` has `sourceVersionId`, copy the source bible's ecosystem rules and confirmed writing guides into draft rows for the new bible version. Implementation:

```java
@Transactional
public BibleSummaryView createDraft(Long userId, Long projectId, CreateBibleDraftRequest request) {
    accessService.require(userId, projectId, Action.EDIT_CONTENT);

    // calculate next version number
    int maxVersion = bibleMapper.selectList(
        new LambdaQueryWrapper<CreativeBibleVersion>()
            .eq(CreativeBibleVersion::getProjectId, projectId))
        .stream().mapToInt(CreativeBibleVersion::getVersionNo).max().orElse(0);
    int nextVersion = maxVersion + 1;

    CreativeBibleVersion draft = new CreativeBibleVersion();
    draft.setProjectId(projectId);
    draft.setVersionNo(nextVersion);
    draft.setStatus("draft");
    draft.setSummary(request.summary());
    draft.setSnapshotJson("{}");
    draft.setCreatedBy(userId);
    bibleMapper.insert(draft);

    // copy from source bible if specified
    if (request.sourceVersionId() != null) {
        CreativeBibleVersion source = bibleMapper.selectById(request.sourceVersionId());
        if (source == null || !source.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "源圣经版本不存在或不属于当前项目");
        }
        draft.setSourceVersionId(source.getId());
        bibleMapper.updateById(draft);

        // copy ecosystem rules: new rows with new bible_version_id, status='draft'
        List<EcosystemRule> sourceRules = ecosystemMapper.selectList(
            new LambdaQueryWrapper<EcosystemRule>()
                .eq(EcosystemRule::getBibleVersionId, source.getId())
                .eq(EcosystemRule::getStatus, "draft"));
        for (EcosystemRule src : sourceRules) {
            EcosystemRule copy = new EcosystemRule();
            BeanUtils.copyProperties(src, copy, "id", "bibleVersionId", "status",
                "createdBy", "createdAt", "updatedBy", "updatedAt");
            copy.setBibleVersionId(draft.getId());
            copy.setStatus("draft");
            copy.setCreatedBy(userId);
            copy.setUpdatedBy(null);
            copy.setCreatedAt(LocalDateTime.now());
            copy.setUpdatedAt(LocalDateTime.now());
            ecosystemMapper.insert(copy);
        }

        // copy confirmed writing guides: new rows with new bible_version_id, status='draft'
        List<ProjectWritingGuide> sourceGuides = guideMapper.selectList(
            new LambdaQueryWrapper<ProjectWritingGuide>()
                .eq(ProjectWritingGuide::getBibleVersionId, source.getId())
                .eq(ProjectWritingGuide::getStatus, "confirmed"));
        for (ProjectWritingGuide src : sourceGuides) {
            ProjectWritingGuide copy = new ProjectWritingGuide();
            BeanUtils.copyProperties(src, copy, "id", "bibleVersionId", "status", "versionNo",
                "confirmedBy", "confirmedAt", "createdBy", "createdAt");
            copy.setBibleVersionId(draft.getId());
            copy.setStatus("draft");
            copy.setCreatedBy(userId);
            copy.setCreatedAt(LocalDateTime.now());
            guideMapper.insert(copy);
        }
    }

    return toSummary(draft);
}
```

After a bible version is confirmed, all ecosystem rules and writing guides that were ingested into its `snapshotJson` must be marked `confirmed` in the same transaction—they are no longer editable within that bible version. New edits must go through a new draft bible. The source bible version remains immutable.

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

    @Test
    void characterGuideCannotOverridePlatformRules() {
        when(guideMapper.selectList(any())).thenReturn(List.of(
                guide(1L, "project", 0L, "{\"platform_rules\":[\"禁止色情描写\"]}"),
                guide(3L, "character", 9L, "{\"platform_rules\":[\"允许擦边\"]}")
        ));
        var result = resolver.resolve(4L, 3L, null, List.of(9L));
        assertThat(result.resolved().toString()).contains("禁止色情描写");
        assertThat(result.conflicts()).contains("characters.9.platform_rules");
    }

    @Test
    void characterGuideCannotOverrideComplianceRules() {
        when(guideMapper.selectList(any())).thenReturn(List.of(
                guide(1L, "project", 0L, "{\"compliance_rules\":[\"禁止未成年人饮酒\"]}"),
                guide(3L, "character", 9L, "{\"compliance_rules\":[]}")
        ));
        var result = resolver.resolve(4L, 3L, null, List.of(9L));
        assertThat(result.resolved().toString()).contains("禁止未成年人饮酒");
        assertThat(result.conflicts()).contains("characters.9.compliance_rules");
    }
}
```

All three NON_OVERRIDABLE keys (`hard_bans`, `platform_rules`, `compliance_rules`) must have dedicated tests. Expand each test with project-level values that character guides attempt to clear or override; assert the resolved result retains the project-level values and conflicts contain the field path.

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

/**
 * @param projectId     content project ID
 * @param bibleVersionId  selected bible version
 * @param unitId        current content unit ID (nullable for maintenance jobs)
 * @param characterIds  ordered list of character entity IDs for L2 guides
 */
public ResolvedWritingGuideView resolve(
        Long projectId, Long bibleVersionId, Long unitId, List<Long> characterIds) {
    // merge order: project -> each requested character -> content_unit
    // character values live under resolved.characters.<characterId>
    // unit values override project scalar fields only when explicitly present
    // sort characterIds before resolution for deterministic output and hash
    List<Long> sorted = new ArrayList<>(characterIds);
    Collections.sort(sorted);
    // ...
}
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
        List<Long> characterGuideIds,
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
persisted.setCharacterGuideIdsJson(objectMapper.writeValueAsString(snapshot.characterGuideIds()));
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

**Attention — `ContentProjectM1IntegrationTest` modification required:**

The existing M1 integration test creates generation jobs without a confirmed bible. After P0, `ContextAssembler` throws `BizException("创作圣经尚未确认")` when no confirmed bible exists. Modify the test setup to include:

```java
@BeforeEach
void ensureConfirmedBible() {
    // create and confirm a minimal bible so the existing generation tests pass
    CreativeBibleVersion bible = new CreativeBibleVersion();
    bible.setProjectId(testProjectId);
    bible.setVersionNo(1);
    bible.setStatus("confirmed");
    bible.setSnapshotJson("{\"ecosystem_rules\":[],\"confirmed_settings\":[],\"writing_guides\":[]}");
    bible.setSnapshotHash(DigestUtils.sha256Hex(bible.getSnapshotJson()));
    bible.setCreatedBy(testUserId);
    bibleMapper.insert(bible);
    // add at least one ecosystem rule to satisfy the confirmation gate
    EcosystemRule rule = new EcosystemRule();
    rule.setProjectId(testProjectId);
    rule.setBibleVersionId(bible.getId());
    rule.setRuleType("world_rule");
    rule.setName("测试规则");
    rule.setStatus("confirmed");
    rule.setCreatedBy(testUserId);
    ecosystemRuleMapper.insert(rule);
}
```

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

Also assert a Viewer receives 403 on POST, invalid `scope_type` receives 400, and **`allow_unconfirmed_bible` is rejected from normal HTTP requests**:

```java
@Test
void generationRejectsAllowUnconfirmedBibleFromHttp() throws Exception {
    // attempt to bypass bible requirement via strategy field
    mockMvc.perform(post("/api/v1/content-projects/{id}/generation-jobs", projectId)
            .header("Authorization", ownerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"type\":\"synopsis\",\"target_id\":1,"
                    + "\"strategy\":\"{\\\"allow_unconfirmed_bible\\\":true}\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                containsString("allow_unconfirmed_bible 不可通过 API 设置")));
}
```

This guard lives in `ContentGenerationJobController.createJob`: parse strategy JSON and reject if the key is present.

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
POST /{id}/creative-bible/versions/{versionId}/submit-review
POST /{id}/creative-bible/versions/{versionId}/archive
GET  /{id}/creative-bible/versions/{versionId}/ecosystem-rules
POST /{id}/creative-bible/versions/{versionId}/ecosystem-rules
PATCH /{id}/creative-bible/versions/{versionId}/ecosystem-rules/{ruleId}
GET  /{id}/creative-bible/versions/{versionId}/writing-guides
POST /{id}/creative-bible/versions/{versionId}/writing-guides
POST /{id}/creative-bible/versions/{versionId}/writing-guides/resolve
```

`submit-review` transitions `draft → reviewable`. `archive` transitions `superseded` or `confirmed` (with no downstream dependencies) → `archived`. P0 provides both endpoints; UI for `submit-review` is delivered in P1 as part of the candidate confirmation flow.

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

**State management approach:**

Creative bible state is shared across multiple panels (overview, ecosystem, writing guides) inside the work-editor shell. Use a single composable as the state owner to avoid duplicate requests and inconsistent UI:

- `useWorkEditor.js` already owns the editor aggregate response. Extend it with a `bibleHealth` ref and a `currentBible` ref, loaded once when the editor mounts and refreshed after confirm/draft/create actions.
- `CreativeBibleOverview.vue`, `EcosystemPanel.vue`, and `WritingGuidePanel.vue` receive state via props from the parent `TagEditor.vue`, which reads from `useWorkEditor`. Do not create a separate Pinia store for P0 — the composable + props pattern is sufficient for three panels within the same route.
- `ContextPanel.vue` and `ContentProjectWorkspace.vue` read bible health independently via `getCreativeBibleHealth` on mount and when the generation job changes, because they live outside the work-editor route.

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

test('three-level merge: unit overrides explicit field, character values live under characters key, hard bans cannot be cleared', () => {
  const projectGuide = { pace: 'fast', hard_bans: ['辱骂'], tone: '严肃' }
  const characterGuides = [
    { characterId: 9, guide: { catchphrases: ['老夫聊发少年狂'], tone: '豪迈' } },
    { characterId: 12, guide: { forbidden_words: ['死'] } }
  ]
  const unitGuide = { pace: 'slow', special_form: '书信体', hard_bans: [] }

  const result = mergeWritingGuide(projectGuide, characterGuides, unitGuide)

  // L3 overrides L1 pace
  assert.equal(result.resolved.pace, 'slow')
  // L2 character tone overrides L1 tone for that character
  assert.equal(result.resolved.characters['9'].tone, '豪迈')
  // L1 tone preserved for characters without L2 override
  assert.equal(result.resolved.tone, '严肃')
  // hard_bans cannot be overridden by L2 or L3
  assert.deepEqual(result.resolved.hard_bans, ['辱骂'])
  // L3 special_form added
  assert.equal(result.resolved.special_form, '书信体')
  // conflicts recorded for non-overridable attempts
  assert.deepEqual(result.conflicts, ['unit.hard_bans'])
  // character guides recorded
  assert.deepEqual(result.resolved.characters['9'].catchphrases, ['老夫聊发少年狂'])
  assert.deepEqual(result.resolved.characters['12'].forbidden_words, ['死'])
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

Implement `mergeWritingGuide(projectGuide, characterGuides, unitGuide)` with immutable object copies and the same `NON_OVERRIDABLE` keys as the backend (`hard_bans`, `platform_rules`, `compliance_rules`). Resolution order:

```
1. Start with projectGuide (L1) as base
2. For each characterGuide (L2): merge into resolved.characters.<characterId>
   — character fields only affect that character's entry, not project-level fields
3. Merge unitGuide (L3) on top: only explicitly present fields override
   — attempt to override NON_OVERRIDABLE keys → recorded as conflict, value from L1 kept
```

The `mergeWritingGuide` function is for client-side preview only. The authoritative resolution happens server-side via `WritingGuideResolver`. The frontend function must match the server's precedence exactly so the preview is accurate.

`normalizeEditorResponse` must preserve old responses by normalizing absent `bible_health` through `normalizeBibleHealth`.

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

**Loading, empty, and error states (required for both components):**

| State | `CreativeBibleOverview.vue` | `EcosystemPanel.vue` |
|---|---|---|
| Loading | Skeleton placeholder: version card + stats cards (3 rectangles) | Skeleton: type filter bar + 3 list item placeholders |
| Empty (no bible) | 引导文案：“尚未创建创作圣经。创作圣经是项目的正式事实源，确认后 AI 生成将使用其中的设定。” + “创建首个版本”按钮 | 引导文案：“当前版本暂无生态规则。从类型筛选器中选择类型并添加第一条规则。” + “添加规则”按钮 |
| Empty (no rules) | N/A | 每种 rule_type 筛选下若无结果，显示：“暂无该类型的生态规则” |
| API error | `el-alert` type="error" 展示错误消息 + “重试”按钮；不展示假数据 | 同上；新建/编辑操作失败时保留表单输入不清空 |
| Network offline | `el-alert` type="warning"：“网络连接已断开，编辑内容暂存在本地” | 同上 |

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

**Backend `rule_type` validation:** In `CreativeBibleService.upsertEcosystem`, validate `rule_type` against the allowed whitelist before insert/update:

```java
private static final Set<String> ALLOWED_RULE_TYPES = Set.of(
    "era_world", "world_rule", "social_structure", "institution_taboo",
    "faction_organization", "resource_system", "ability_system",
    "location_system", "key_history",
    // TVC mode
    "brand", "product_service", "target_audience", "competitor",
    "core_selling_point", "brand_taboo", "character_expression");

private void validateRuleType(String ruleType) {
    if (ruleType == null || !ALLOWED_RULE_TYPES.contains(ruleType)) {
        throw new BizException(ErrorCode.PARAM_INVALID,
            "非法的生态规则类型: " + ruleType);
    }
}
```

Similarly validate `scope_type` in writing guide operations: only `project`, `character`, `content_unit` are allowed. `scope_type=project` must have `scopeId=0` (enforced server-side; the DTO's `scopeId` default of `0L` covers the JSON case).

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

The scope selector requires a character ID for character scope and content-unit ID for unit scope. **Character list data source:** P0 reads characters from the confirmed bible's `snapshotJson.confirmed_settings` (filtered to `entity_type='character'`), which is returned by `GET /creative-bible`. Since P0 does not provide a new character CRUD API, the existing `ProjectSettingService` endpoints remain the source for managing character entities. The writing guide panel only references existing characters; it does not create them.

“解析预览” calls `resolveWritingGuide` and displays final values, source per field and conflicts. Save creates a new guide version; it never patches a confirmed guide.

**Loading, empty, and error states:**

| State | `WritingGuidePanel.vue` |
|---|---|
| Loading | Skeleton: scope tabs + 6 field placeholders |
| Empty (no guides) | 引导文案：“尚未配置写作口径。项目级口径控制整体叙事风格，角色级口吻控制角色对白和表达，单集/单章覆盖用于局部调整。” + “创建项目级口径”按钮 |
| Empty (resolution) | 解析预览区域显示：“选择口径版本后点击'解析预览'查看最终生效的口径” |
| API error | `el-alert` type="error" + 重试；保存失败保留表单内容 |
| Non-overridable warning | 角色/单元口径中 `hard_bans`、`platform_rules`、`compliance_rules` 字段旁显示黄色锁图标 + tooltip：“此字段由项目级口径锁定，不可覆盖” |
| Conflict display | 解析预览中冲突字段红色高亮，标注来源和优先级 |

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

**Loading and error states for workspace integration:**

| State | `ContentProjectWorkspace.vue` | `ContextPanel.vue` |
|---|---|---|
| Bible health loading | Generation button 显示 loading 状态，tooltip：“正在检查创作圣经状态…” | Bible/guide section 显示骨架占位 |
| Bible not confirmed | Generation button disabled + tooltip：“创作圣经尚未确认” + “前往确认”链接 | 显示：“圣经状态：未确认”，不展示口径信息 |
| Health API error | Generation button 保持可用（降级：不阻断），但显示 warning badge | 显示：“圣经状态获取失败” + retry 按钮 |
| Context snapshot loading | N/A | 骨架占位：版本列表 + hash |
| No context yet | N/A | 显示：“当前无生成记录” |

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
- [ ] **Performance baseline:** Context assembly + snapshot persistence completes < 500ms P95 on a 100-chapter project with 20 ecosystem rules. Bible confirmation (with snapshot materialization) completes < 3s with 20 ecosystem rules + 50 confirmed settings. Verify with `ContextAssemblerCreativeBibleTest` (unit-level timing assertions) and a dedicated `CreativeBiblePerformanceTest` (SpringBootTest with `@Timeout`).

## 5. P1 planning gate

Create the P1 executable plan only after the P0 acceptance checklist passes and the persisted snapshot schema is stable. P1 must consume `creative_bible_versions.id`, `content_versions.id`, and `generation_context_snapshots.id`; it must not add a second facts store or change the three-level guide precedence.

## 6. Known P0 limitations

These capabilities are intentionally deferred to P1 or later. Do not implement them in P0:

| Limitation | Reason | Mitigation |
|---|---|---|
| 实体设定（角色/背景/势力/地点/物品）仍走旧 `ProjectSettingService`，圣经内不提供独立的实体 CRUD API | P0 聚焦生态规则和写作口径；实体设定已有完整的版本和 CRUD 链路 | 确认圣经时通过 `settingMapper` 读取已确认实体并写入快照；`ContextAssembler` 从快照读取而非查当前表 |
| 无关系图谱、时间轴、连续性台账 | 属于 P1 范围 | P0 仅确保 `project_entity_relations` 和 `continuity_snapshots` 表已创建（DDL 在 P1 migration 中交付） |
| 无影响分析和差异报告 | 属于 P1 范围 | P0 事件已包含版本 ID，P1 消费者可直接消费 |
| 无画布导入快照 | 属于 P2 范围 | P0 API 中 `/canvas-import-snapshots` 端点不实现 |
| 旧 `scripts` 数据不回填为正式圣经版本 | 回填仅创建草稿，需用户手动确认 | 旧项目只读可用，新生成强制要求确认圣经 |
| 圣经不提供全文搜索 | P3 治理范围 | P0 按 `rule_type` 筛选 + 分页足够 |
| 无批量确认候选 | P1 候选增强 | P0 逐字段确认 |
| 回填触发方式 | 不自动触发；用户打开已有项目的创作工作台时，前端检测 `health.status === 'missing'` 后展示引导横幅“升级项目以使用创作圣经”，用户点击后调用 `POST /creative-bible/versions`（不带 `sourceVersionId`），后端自动从已有 `project_setting_entities` 和 `project_setting_versions` 回填首个草稿 | 旧项目用户主动升级，不静默迁移 |

## 7. Rollback plan

If a P0 deployment must be reversed:

### Database
- **不执行反向迁移**。四张新表 (`creative_bible_versions`, `ecosystem_rules`, `project_writing_guides`, `generation_context_snapshots`) 仅被新代码引用，旧代码不感知它们。保留表不动，不删除数据。
- H2 开发库可直接 drop 四张表后重新运行旧版 schema。

### Service layer — feature flag
在 `application.yml` 中增加开关：

```yaml
aicp:
  creative-bible:
    enabled: true
    require-for-generation: true
```

紧急回滚时设置 `require-for-generation: false`：
- `ContextAssembler` 在 `require-for-generation=false` 时跳过圣经检查，不强制要求已确认圣经。
- `CreativeBibleController` 在 `enabled=false` 时返回 503 + `{"message": "创作圣经功能暂不可用"}`。
- 已创建的 `generation_context_snapshots` 行保留但不再被新生成消费。

### Frontend
- 前端通过 health API 的 `ready_for_generation` 字段控制阻断逻辑。
- 后端 `CreativeBibleService.health()` 在 `require-for-generation=false` 时的行为：

```java
public Map<String, Object> health(Long userId, Long projectId) {
    // ...existing checks...
    boolean requireForGeneration = configService.getBoolean(
        "aicp.creative-bible.require-for-generation", true);
    boolean ready;
    if (!requireForGeneration) {
        // 紧急回滚模式：强制返回 ready，不阻断任何生成
        ready = true;
    } else {
        ready = current != null && "confirmed".equals(current.getStatus());
    }
    return Map.of(
        // ...other fields...
        "ready_for_generation", ready,
        "require_for_generation", requireForGeneration  // 前端可据此判断是否为降级模式
    );
}
```

- 前端在 `ready_for_generation=true` 时不阻断生成，但仍展示圣经状态供参考。
- 导航中“创作圣经”入口可通过后端 `enabled` 字段隐藏：在 `CreativeBibleController` 中，`enabled=false` 时所有端点返回 503。

### Verification
回滚后执行：
```bash
cd aicp-backend && mvn test
cd ../aicp-frontend && node --test tests/*.test.js && npm run build
```
确保旧功能（创作工作台、梗概/大纲/正文生成、审核、锁稿）不受影响。
