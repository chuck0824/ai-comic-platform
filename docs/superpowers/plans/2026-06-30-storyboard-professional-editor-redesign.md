# Storyboard Professional Editor Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace both incomplete storyboard implementations with one versioned professional editor that supports 13-dimensional shot editing, A/B/C derived versions, seven-sheet XLSX round-tripping, review gates, exports, and immutable canvas snapshots.

**Architecture:** Build a new `com.aicp.module.storyboard` bounded domain inside the existing Spring Boot application and a dedicated Vue route under content projects. The new domain owns storyboard assets, immutable version lineage, editable draft scenes/shots, professional modules, jobs, import/export, review, and canvas snapshot contracts; content projects only expose summaries and entry points.

**Tech Stack:** Java 17, Spring Boot 3.2, MyBatis-Plus 3.5, H2/MySQL, Apache POI 5.2, Vue 3, Vue Router, Element Plus, Pinia-compatible composables, vuedraggable, ECharts, Vitest, Vue Test Utils.

**Design spec:** `docs/superpowers/specs/2026-06-30-storyboard-professional-editor-redesign.md`

---

## Delivery slices

1. Domain schema and resource isolation.
2. Version lifecycle and 13-dimensional editing API.
3. Professional modules, review issues, and production gates.
4. XLSX/PDF exchange and async job infrastructure.
5. AI generation, derived upgrades, and immutable canvas snapshots.
6. Independent Vue professional editor.
7. Legacy removal, golden-file E2E, security, and performance verification.

Each slice ends in a focused commit and leaves the application buildable.

## File map

### Backend files to create

```text
aicp-backend/src/main/java/com/aicp/module/storyboard/
├── controller/
│   ├── StoryboardController.java
│   ├── StoryboardVersionController.java
│   ├── StoryboardEditingController.java
│   ├── StoryboardProfessionalController.java
│   └── StoryboardJobController.java
├── domain/
│   ├── StoryboardEnums.java
│   ├── StoryboardStateMachine.java
│   └── ProductionGate.java
├── dto/
│   ├── StoryboardRequests.java
│   └── StoryboardViews.java
├── entity/
│   ├── Storyboard.java
│   ├── StoryboardVersion.java
│   ├── StoryboardScene.java
│   ├── StoryboardShot.java
│   ├── StoryboardEmotionSegment.java
│   ├── StoryboardPromptTemplate.java
│   ├── StoryboardCreativeRule.java
│   ├── StoryboardCharacterVisual.java
│   ├── StoryboardShotVisualBinding.java
│   ├── StoryboardReviewIssue.java
│   ├── StoryboardJob.java
│   ├── StoryboardAuditLog.java
│   └── StoryboardCanvasSnapshot.java
├── mapper/
│   ├── StoryboardMapper.java
│   ├── StoryboardVersionMapper.java
│   ├── StoryboardSceneMapper.java
│   ├── StoryboardShotMapper.java
│   ├── StoryboardEmotionSegmentMapper.java
│   ├── StoryboardPromptTemplateMapper.java
│   ├── StoryboardCreativeRuleMapper.java
│   ├── StoryboardCharacterVisualMapper.java
│   ├── StoryboardShotVisualBindingMapper.java
│   ├── StoryboardReviewIssueMapper.java
│   ├── StoryboardJobMapper.java
│   ├── StoryboardAuditLogMapper.java
│   └── StoryboardCanvasSnapshotMapper.java
├── service/
│   ├── StoryboardAccessService.java
│   ├── StoryboardQueryService.java
│   ├── StoryboardVersionService.java
│   ├── StoryboardEditingService.java
│   ├── StoryboardProfessionalService.java
│   ├── StoryboardReviewService.java
│   ├── StoryboardJobService.java
│   ├── StoryboardGenerationService.java
│   └── StoryboardCanvasSnapshotService.java
└── exchange/
    ├── StoryboardWorkbookSchema.java
    ├── StoryboardWorkbookImporter.java
    ├── StoryboardWorkbookExporter.java
    └── StoryboardPdfExporter.java
```

### Frontend files to create

```text
aicp-frontend/src/api/storyboardV2.js
aicp-frontend/src/views/storyboard/StoryboardEditor.vue
aicp-frontend/src/views/storyboard/storyboardData.js
aicp-frontend/src/views/storyboard/composables/useStoryboardEditor.js
aicp-frontend/src/views/storyboard/composables/useStoryboardJobs.js
aicp-frontend/src/views/storyboard/components/StoryboardTopbar.vue
aicp-frontend/src/views/storyboard/components/StoryboardModuleTabs.vue
aicp-frontend/src/views/storyboard/components/SceneNavigator.vue
aicp-frontend/src/views/storyboard/components/ShotGrid.vue
aicp-frontend/src/views/storyboard/components/ShotInspector.vue
aicp-frontend/src/views/storyboard/components/ShotCardView.vue
aicp-frontend/src/views/storyboard/components/ShotTimelineView.vue
aicp-frontend/src/views/storyboard/components/EmotionRhythmPanel.vue
aicp-frontend/src/views/storyboard/components/PromptTemplatePanel.vue
aicp-frontend/src/views/storyboard/components/CreativeRulePanel.vue
aicp-frontend/src/views/storyboard/components/CharacterVisualPanel.vue
aicp-frontend/src/views/storyboard/components/VersionReviewPanel.vue
aicp-frontend/src/views/storyboard/components/WorkbookExchangeDrawer.vue
```

### Existing files to modify or remove

```text
aicp-backend/src/main/resources/db/schema-h2.sql
aicp-backend/src/main/resources/db/schema-mysql.sql
aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java
aicp-backend/src/main/java/com/aicp/common/exception/GlobalExceptionHandler.java
aicp-backend/src/main/java/com/aicp/module/contentproject/service/CanvasBridgeService.java
aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectWorkflowService.java
aicp-frontend/src/router/index.js
aicp-frontend/src/api/contentProject.js
aicp-frontend/src/views/content-project/ContentProjectWorkspace.vue
aicp-frontend/package.json
```

Legacy files removed only in Task 12 after all callers move:

```text
aicp-frontend/src/views/Storyboard.vue
aicp-frontend/src/api/storyboard.js
aicp-frontend/src/views/content-project/components/StoryboardPanel.vue
aicp-frontend/src/views/content-project/components/ShotCard.vue
aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentStoryboardController.java
aicp-backend/src/main/java/com/aicp/module/contentproject/service/StoryboardService.java
aicp-backend/src/main/java/com/aicp/module/contentproject/entity/StoryboardMaster.java
aicp-backend/src/main/java/com/aicp/module/contentproject/entity/StoryboardScene.java
aicp-backend/src/main/java/com/aicp/module/contentproject/entity/StoryboardShot.java
aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/ContentStoryboardMasterMapper.java
aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/ContentStoryboardSceneMapper.java
aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/ContentStoryboardShotMapper.java
```

---

### Task 1: Add the new storyboard schema and error contract

**Files:**
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Modify: `aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java`
- Modify: `aicp-backend/src/main/java/com/aicp/common/exception/GlobalExceptionHandler.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/storyboard/schema/StoryboardSchemaTest.java`

- [ ] **Step 1: Write the failing schema test**

```java
@SpringBootTest
@ActiveProfiles("dev")
class StoryboardSchemaTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsAllStoryboardDomainTables() {
        var expected = List.of(
            "storyboards", "storyboard_versions", "storyboard_version_scenes",
            "storyboard_version_shots", "storyboard_emotion_segments",
            "storyboard_prompt_templates", "storyboard_creative_rules",
            "storyboard_character_visuals", "storyboard_shot_visual_bindings",
            "storyboard_review_issues", "storyboard_jobs", "storyboard_audit_logs",
            "storyboard_canvas_snapshots"
        );
        for (String table : expected) {
            Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = ?",
                Integer.class, table.toUpperCase());
            assertThat(count).isEqualTo(1);
        }
    }
}
```

- [ ] **Step 2: Run the schema test and verify failure**

Run:

```bash
cd aicp-backend
mvn -Dtest=StoryboardSchemaTest test
```

Expected: FAIL because `STORYBOARDS` does not exist.

- [ ] **Step 3: Add the H2 tables and indexes**

Append a `Storyboard Professional Domain` section with these core columns:

```sql
CREATE TABLE IF NOT EXISTS storyboards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    content_unit_id BIGINT NOT NULL,
    source_content_version_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    purpose VARCHAR(30) NOT NULL DEFAULT 'default',
    current_draft_version_id BIGINT,
    current_locked_version_id BIGINT,
    production_status VARCHAR(30) NOT NULL DEFAULT 'not_ready',
    created_by BIGINT NOT NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sb_source UNIQUE(project_id, content_unit_id, source_content_version_id, purpose)
);

CREATE TABLE IF NOT EXISTS storyboard_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    storyboard_id BIGINT NOT NULL,
    parent_version_id BIGINT,
    source_content_version_id BIGINT NOT NULL,
    tier VARCHAR(1) NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    revision INT NOT NULL DEFAULT 0,
    schema_version INT NOT NULL DEFAULT 1,
    total_scenes INT NOT NULL DEFAULT 0,
    total_shots INT NOT NULL DEFAULT 0,
    total_duration_ms BIGINT NOT NULL DEFAULT 0,
    created_from VARCHAR(20) NOT NULL,
    locked_by BIGINT,
    locked_at TIMESTAMP,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sb_version UNIQUE(storyboard_id, tier, version_no)
);

CREATE TABLE IF NOT EXISTS storyboard_version_scenes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    scene_key VARCHAR(36) NOT NULL,
    scene_no INT NOT NULL,
    title VARCHAR(255),
    dramatic_goal TEXT,
    beat_description TEXT,
    location_ref_id BIGINT,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    emotion_label VARCHAR(100),
    emotion_intensity INT,
    sort_order INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sb_scene_key UNIQUE(version_id, scene_key),
    CONSTRAINT uk_sb_scene_no UNIQUE(version_id, scene_no)
);

CREATE TABLE IF NOT EXISTS storyboard_version_shots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    version_id BIGINT NOT NULL,
    scene_id BIGINT NOT NULL,
    shot_key VARCHAR(36) NOT NULL,
    shot_code VARCHAR(30) NOT NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    shot_size VARCHAR(50),
    visual_description TEXT,
    lighting_atmosphere TEXT,
    character_action TEXT,
    emotion_description TEXT,
    dialogue_text TEXT,
    scene_tags_json TEXT,
    sound_effect TEXT,
    reference_text TEXT,
    image_prompt CLOB,
    video_motion_prompt CLOB,
    director_intention TEXT,
    action_motivation TEXT,
    relationship_blocking TEXT,
    information_gap TEXT,
    audio_visual_relation TEXT,
    edit_point TEXT,
    dub_text TEXT,
    subtitle_text TEXT,
    failure_strategy VARCHAR(30),
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    sort_order INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sb_shot_key UNIQUE(version_id, shot_key),
    CONSTRAINT uk_sb_shot_code UNIQUE(version_id, shot_code)
);

CREATE INDEX IF NOT EXISTS idx_sb_project ON storyboards(project_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_sbv_master ON storyboard_versions(storyboard_id, tier, version_no);
CREATE INDEX IF NOT EXISTS idx_sbscene_version ON storyboard_version_scenes(version_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_sbshot_version ON storyboard_version_shots(version_id, scene_id, sort_order);
```

Create these eight auxiliary tables in H2:

```sql
CREATE TABLE IF NOT EXISTS storyboard_emotion_segments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, version_id BIGINT NOT NULL,
    emotion_type VARCHAR(100) NOT NULL, shot_range VARCHAR(255) NOT NULL,
    intensity INT NOT NULL, core_expression TEXT, sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sbemotion_version ON storyboard_emotion_segments(version_id, sort_order);

CREATE TABLE IF NOT EXISTS storyboard_prompt_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, version_id BIGINT NOT NULL,
    template_code VARCHAR(50) NOT NULL, emotion_name VARCHAR(100), shot_refs_json TEXT,
    image_prompt CLOB, video_motion_prompt CLOB, sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbprompt_code UNIQUE(version_id, template_code)
);

CREATE TABLE IF NOT EXISTS storyboard_creative_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, version_id BIGINT NOT NULL,
    rule_type VARCHAR(30) NOT NULL, dimension_name VARCHAR(100) NOT NULL,
    principle TEXT, implementation_text TEXT, target_refs_json TEXT,
    effect_text TEXT, status VARCHAR(20) NOT NULL DEFAULT 'active', sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sbrule_version ON storyboard_creative_rules(version_id, rule_type, sort_order);

CREATE TABLE IF NOT EXISTS storyboard_character_visuals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, version_id BIGINT NOT NULL,
    character_ref_id BIGINT, character_name VARCHAR(100) NOT NULL,
    core_identity TEXT, daily_look TEXT, task_look TEXT,
    performance_anchor TEXT, prompt_lock CLOB, sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbvisual_character UNIQUE(version_id, character_name)
);

CREATE TABLE IF NOT EXISTS storyboard_shot_visual_bindings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, version_id BIGINT NOT NULL,
    shot_id BIGINT NOT NULL, character_visual_id BIGINT NOT NULL,
    application_note TEXT, anti_drift_requirement TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbbinding UNIQUE(version_id, shot_id, character_visual_id)
);

CREATE TABLE IF NOT EXISTS storyboard_review_issues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, version_id BIGINT NOT NULL,
    fingerprint VARCHAR(64) NOT NULL, issue_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL, shot_id BIGINT, message TEXT NOT NULL,
    evidence TEXT, suggestion TEXT, status VARCHAR(20) NOT NULL DEFAULT 'open',
    resolution_note TEXT, resolved_by BIGINT, resolved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbissue_fingerprint UNIQUE(version_id, fingerprint)
);
CREATE INDEX IF NOT EXISTS idx_sbissue_status ON storyboard_review_issues(version_id, status, severity);

CREATE TABLE IF NOT EXISTS storyboard_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL, storyboard_id BIGINT NOT NULL, version_id BIGINT,
    job_type VARCHAR(30) NOT NULL, status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL, progress_percent INT NOT NULL DEFAULT 0,
    current_stage VARCHAR(100), request_json CLOB, result_json CLOB,
    error_code VARCHAR(100), error_message TEXT, created_by BIGINT NOT NULL,
    started_at TIMESTAMP, finished_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbjob_idem UNIQUE(project_id, job_type, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_sbjob_status ON storyboard_jobs(project_id, status, created_at);

CREATE TABLE IF NOT EXISTS storyboard_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, version_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL, action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50), target_id BIGINT, operation_id VARCHAR(100),
    before_json CLOB, after_json CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sbaudit_version ON storyboard_audit_logs(version_id, created_at);

CREATE TABLE IF NOT EXISTS storyboard_canvas_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL, storyboard_id BIGINT NOT NULL, version_id BIGINT NOT NULL,
    snapshot_type VARCHAR(20) NOT NULL, idempotency_key VARCHAR(100) NOT NULL,
    parameter_version_id BIGINT, source_content_version_id BIGINT NOT NULL,
    snapshot_json CLOB NOT NULL, snapshot_hash VARCHAR(64) NOT NULL,
    gate_report_json CLOB, created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbsnapshot_idem UNIQUE(project_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_sbsnapshot_version ON storyboard_canvas_snapshots(version_id, created_at);
```

Use `TEXT` for H2 JSON payloads. The MySQL mirror uses `JSON` for `*_json` columns and `LONGTEXT` for prompts, request/result bodies, and audit values.

- [ ] **Step 4: Mirror the schema in MySQL syntax**

Use `BIGINT AUTO_INCREMENT`, `DATETIME`, `LONGTEXT`, inline `INDEX`, and `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`. Keep names and constraints identical to H2 so mapper entities remain portable.

- [ ] **Step 5: Add storyboard error codes and HTTP mapping**

Add to `ErrorCode`:

```java
STORYBOARD_NOT_FOUND(45001, "分镜不存在"),
STORYBOARD_VERSION_NOT_FOUND(45002, "分镜版本不存在"),
STORYBOARD_VERSION_LOCKED(45003, "分镜版本已锁定"),
STORYBOARD_REVISION_CONFLICT(45004, "分镜版本已被他人修改"),
SOURCE_CONTENT_VERSION_STALE(45005, "源正文版本已更新"),
INVALID_TIER_TRANSITION(45006, "分镜升档路径无效"),
REVIEW_ISSUES_UNRESOLVED(45007, "仍有未处理的审核问题"),
PRODUCTION_GATE_FAILED(45008, "生产准入未通过"),
XLSX_TEMPLATE_UNSUPPORTED(45009, "不支持的分镜工作簿模板"),
XLSX_VALIDATION_FAILED(45010, "分镜工作簿校验失败"),
STORYBOARD_JOB_CONFLICT(45011, "同类分镜任务正在运行");
```

Map 45001/45002 to 404, 45003–45011 to 409 except 45009/45010 to 400.

- [ ] **Step 6: Run schema and full backend tests**

```bash
cd aicp-backend
mvn -Dtest=StoryboardSchemaTest test
mvn test
```

Expected: both commands PASS.

- [ ] **Step 7: Commit**

```bash
git add aicp-backend/src/main/resources/db/schema-h2.sql \
  aicp-backend/src/main/resources/db/schema-mysql.sql \
  aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java \
  aicp-backend/src/main/java/com/aicp/common/exception/GlobalExceptionHandler.java \
  aicp-backend/src/test/java/com/aicp/module/storyboard/schema/StoryboardSchemaTest.java
git commit -m "feat: add professional storyboard schema"
```

---

### Task 2: Add entities, mappers, DTOs, and project-scoped access

**Files:**
- Create: the thirteen entity files and thirteen mapper files enumerated in the file map
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/domain/StoryboardEnums.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/dto/StoryboardRequests.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/dto/StoryboardViews.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/service/StoryboardAccessService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/service/StoryboardQueryService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/controller/StoryboardController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/storyboard/service/StoryboardAccessServiceTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/storyboard/service/StoryboardQueryServiceTest.java`

- [ ] **Step 1: Write failing access tests**

```java
@ExtendWith(MockitoExtension.class)
class StoryboardAccessServiceTest {
    @Mock StoryboardMapper storyboardMapper;
    @Mock StoryboardVersionMapper versionMapper;
    @Mock ProjectAccessService projectAccessService;
    @InjectMocks StoryboardAccessService service;

    @Test
    void rejectsVersionFromAnotherProject() {
        Storyboard sb = new Storyboard();
        sb.setId(9L); sb.setProjectId(2L);
        StoryboardVersion version = new StoryboardVersion();
        version.setId(7L); version.setStoryboardId(9L);
        when(versionMapper.selectById(7L)).thenReturn(version);
        when(storyboardMapper.selectById(9L)).thenReturn(sb);

        assertThatThrownBy(() -> service.requireVersion(1L, 7L, 3L, Action.VIEW))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("分镜版本不存在");
    }
}
```

- [ ] **Step 2: Run the test and verify failure**

```bash
cd aicp-backend
mvn -Dtest=StoryboardAccessServiceTest test
```

Expected: compilation fails because the new types do not exist.

- [ ] **Step 3: Implement enums and core entity shape**

```java
public final class StoryboardEnums {
    public enum Tier { A, B, C }
    public enum VersionStatus { DRAFT, REVIEWING, LOCKED, SUPERSEDED }
    public enum CreatedFrom { MANUAL, AI, IMPORT, FORK, UPGRADE }
    public enum ShotStatus { DRAFT, CONFIRMED, NEEDS_REVIEW }
    public enum JobType { GENERATE, UPGRADE, CHECK, IMPORT, EXPORT, CANVAS_SNAPSHOT }
    public enum JobStatus { QUEUED, RUNNING, SUCCEEDED, FAILED, PARTIAL, CANCELLED }
    private StoryboardEnums() {}
}
```

Map every entity with `@TableName`, `@TableId(type = IdType.AUTO)`, snake-case-compatible field names, and timestamp fills matching existing project entities. Keep each mapper as:

```java
@Mapper
public interface StoryboardVersionMapper extends BaseMapper<StoryboardVersion> {}
```

- [ ] **Step 4: Define request records with validation**

```java
public final class StoryboardRequests {
    public record CreateStoryboardRequest(@NotNull Long contentUnitId,
        @NotNull Long sourceContentVersionId, @NotBlank String title,
        @NotBlank String purpose) {}
    public record PatchShotRequest(Integer revision, Long durationMs, String shotSize,
        String visualDescription, String lightingAtmosphere, String characterAction,
        String emotionDescription, String dialogueText, List<String> sceneTags,
        String soundEffect, String referenceText, String imagePrompt,
        String videoMotionPrompt, String status) {}
    public record ReorderShotItem(@NotNull Long shotId, @NotNull Long sceneId,
        @NotNull Integer sortOrder) {}
    public record ReorderShotsRequest(@NotNull Integer revision,
        @NotEmpty List<ReorderShotItem> items) {}
    public record UpgradeRequest(@NotNull String targetTier, @NotBlank String idempotencyKey) {}
    private StoryboardRequests() {}
}
```

Define views as records and never return entities directly from controllers.

- [ ] **Step 5: Implement strict access resolution**

```java
public StoryboardVersion requireVersion(Long projectId, Long versionId,
        Long userId, Action action) {
    projectAccessService.require(projectId, userId, action);
    StoryboardVersion version = versionMapper.selectById(versionId);
    if (version == null) throw new BizException(ErrorCode.STORYBOARD_VERSION_NOT_FOUND);
    Storyboard storyboard = storyboardMapper.selectById(version.getStoryboardId());
    if (storyboard == null || !projectId.equals(storyboard.getProjectId())) {
        throw new BizException(ErrorCode.STORYBOARD_VERSION_NOT_FOUND);
    }
    return version;
}
```

Add `requireStoryboard(projectId, storyboardId, userId, action)`, `requireScene(projectId, versionId, sceneId, userId, action)`, and `requireShot(projectId, versionId, shotId, userId, action)`. The scene query checks `scene.version_id = versionId`; the shot query checks both `shot.version_id = versionId` and its scene belongs to that version.

- [ ] **Step 6: Implement master create/list/detail**

`POST /content-projects/{projectId}/storyboards` validates content-unit and source-version ownership, inserts the Master and initial A draft in one transaction, and returns `editorPath=/content-projects/{projectId}/storyboards/{storyboardId}`. `GET` list/detail returns view records with current draft/locked summaries and never exposes entities.

- [ ] **Step 7: Run tests**

```bash
cd aicp-backend
mvn -Dtest=StoryboardAccessServiceTest,StoryboardQueryServiceTest test
mvn test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/storyboard \
  aicp-backend/src/test/java/com/aicp/module/storyboard/service/StoryboardAccessServiceTest.java
git commit -m "feat: add storyboard domain model and access isolation"
```

---

### Task 3: Implement version lifecycle, locking, forking, and derived upgrades

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/domain/StoryboardStateMachine.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/service/StoryboardVersionService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/controller/StoryboardVersionController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/storyboard/service/StoryboardVersionServiceTest.java`

- [ ] **Step 1: Write failing state transition tests**

```java
@Test
void lockMakesVersionImmutable() {
    StoryboardVersion version = draft(Tier.A, 4);
    when(versionMapper.selectById(10L)).thenReturn(version);
    service.lock(1L, 10L, 7L, 4, "lock-10");
    assertThat(version.getStatus()).isEqualTo("locked");
    assertThat(version.getLockedBy()).isEqualTo(7L);
}

@Test
void upgradeCreatesChildAndKeepsParentLocked() {
    StoryboardVersion parent = locked(Tier.A, 2);
    when(versionMapper.selectById(10L)).thenReturn(parent);
    StoryboardVersion child = service.createDerivedDraft(1L, 10L, 7L, Tier.B, "up-10-b");
    assertThat(child.getParentVersionId()).isEqualTo(10L);
    assertThat(child.getTier()).isEqualTo("B");
    assertThat(parent.getStatus()).isEqualTo("locked");
}
```

- [ ] **Step 2: Verify failure**

```bash
cd aicp-backend
mvn -Dtest=StoryboardVersionServiceTest test
```

Expected: FAIL because lifecycle service does not exist.

- [ ] **Step 3: Implement the state machine**

```java
public void requireTransition(VersionStatus from, VersionStatus to) {
    boolean allowed = switch (from) {
        case DRAFT -> to == VersionStatus.REVIEWING || to == VersionStatus.LOCKED;
        case REVIEWING -> to == VersionStatus.DRAFT || to == VersionStatus.LOCKED;
        case LOCKED -> to == VersionStatus.SUPERSEDED;
        case SUPERSEDED -> false;
    };
    if (!allowed) throw new BizException(ErrorCode.STORYBOARD_VERSION_LOCKED,
        "不允许从 " + from + " 转为 " + to);
}

public void requireTierUpgrade(Tier from, Tier to) {
    if (!((from == Tier.A && to == Tier.B) || (from == Tier.B && to == Tier.C))) {
        throw new BizException(ErrorCode.INVALID_TIER_TRANSITION);
    }
}
```

- [ ] **Step 4: Implement transactional fork and upgrade copying**

Within one `@Transactional` method:

1. validate parent is locked;
2. lock the `storyboards` row with `SELECT ... FOR UPDATE` so concurrent forks cannot allocate the same version number;
3. reserve the idempotency key in `storyboard_jobs`;
4. insert child version with next tier-local `version_no`;
5. copy scenes with the same `scene_key`;
6. map old scene IDs to new scene IDs;
7. copy shots with the same `shot_key` and new scene IDs;
8. copy all professional module rows;
9. update `storyboards.current_draft_version_id`.

Locking sets `current_locked_version_id` to the locked version and clears `current_draft_version_id` only when it points to that same version. Forking or upgrading immediately sets the new child as `current_draft_version_id`.

The shot copy must keep manual fields and clear only target-tier AI fields before generation:

```java
if (targetTier == Tier.B) {
    child.setDirectorIntention(null);
    child.setActionMotivation(null);
    child.setRelationshipBlocking(null);
    child.setInformationGap(null);
    child.setAudioVisualRelation(null);
    child.setEditPoint(null);
}
if (targetTier == Tier.C) {
    child.setDubText(null);
    child.setSubtitleText(null);
    child.setFailureStrategy(null);
}
```

- [ ] **Step 5: Add version endpoints**

Implement these endpoints: `GET /versions`, `GET /versions/{versionId}`, `GET /versions/{versionId}/diff?against={otherVersionId}`, `POST /submit-review`, `POST /lock`, `POST /fork`, and `POST /upgrade`. Require `revision` for review/lock and `Idempotency-Key` for fork/upgrade.

- [ ] **Step 6: Run tests and commit**

```bash
cd aicp-backend
mvn -Dtest=StoryboardVersionServiceTest test
mvn test
git add src/main/java/com/aicp/module/storyboard src/test/java/com/aicp/module/storyboard
git commit -m "feat: add storyboard version lifecycle"
```

---

### Task 4: Implement 13-dimensional scene and shot editing

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/service/StoryboardEditingService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/controller/StoryboardEditingController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/storyboard/service/StoryboardEditingServiceTest.java`

- [ ] **Step 1: Write failing edit, conflict, lock, and reorder tests**

```java
@Test
void patchShotIncrementsRevisionAndUpdatesTotals() {
    StoryboardVersion version = draft(Tier.A, 8);
    StoryboardShot shot = shot(100L, 3000L);
    when(access.requireVersion(1L, 10L, 7L, Action.EDIT_CONTENT)).thenReturn(version);
    when(access.requireShot(1L, 10L, 100L, 7L, Action.EDIT_CONTENT)).thenReturn(shot);
    var request = new PatchShotRequest(8, 4500L, "特写", "闭眼半笑",
        "侧逆光", "嘴角上扬", "不甘", "你最好祈祷", List.of("雨夜"),
        "暴雨", "《小丑》", "image", "video", "confirmed");
    service.patchShot(1L, 10L, 100L, 7L, request);
    assertThat(version.getRevision()).isEqualTo(9);
    assertThat(shot.getDurationMs()).isEqualTo(4500L);
}

@Test
void rejectsStaleRevision() {
    StoryboardVersion version = draft(Tier.A, 8);
    assertThatThrownBy(() -> service.requireEditable(version, 7))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("他人修改");
}
```

- [ ] **Step 2: Verify failure**

```bash
cd aicp-backend
mvn -Dtest=StoryboardEditingServiceTest test
```

- [ ] **Step 3: Implement edit guards and atomic revision update**

```java
void requireEditable(StoryboardVersion version, int expectedRevision) {
    if ("locked".equals(version.getStatus())) {
        throw new BizException(ErrorCode.STORYBOARD_VERSION_LOCKED);
    }
    if (!Objects.equals(version.getRevision(), expectedRevision)) {
        throw new BizException(ErrorCode.STORYBOARD_REVISION_CONFLICT);
    }
}
```

Use a mapper compare-and-set update:

```java
int updated = versionMapper.update(null,
    Wrappers.<StoryboardVersion>lambdaUpdate()
        .eq(StoryboardVersion::getId, versionId)
        .eq(StoryboardVersion::getRevision, expectedRevision)
        .set(StoryboardVersion::getRevision, expectedRevision + 1)
        .set(StoryboardVersion::getUpdatedAt, LocalDateTime.now()));
if (updated != 1) throw new BizException(ErrorCode.STORYBOARD_REVISION_CONFLICT);
```

- [ ] **Step 4: Implement CRUD and structural commands**

Implement scene/shot create, patch, delete, copy, split, merge, and reorder. Structural operations must:

- lock rows in one transaction;
- keep `scene_key`/`shot_key` stable except for newly created units;
- regenerate display `scene_no` and `shot_code` after ordering;
- update version scene count, shot count, and total duration;
- append an audit log with operation type and before/after JSON.

For split, divide duration and text explicitly:

```java
long firstDuration = request.firstDurationMs();
long secondDuration = original.getDurationMs() - firstDuration;
if (firstDuration <= 0 || secondDuration <= 0) {
    throw new BizException(ErrorCode.PARAM_INVALID, "拆分时长必须位于镜头时长范围内");
}
```

- [ ] **Step 5: Add paged list and summary/detail separation**

`GET /shots` returns prompt lengths and 200-character summaries by default. `GET /shots/{shotId}` returns full long prompts. Support scene, status, character, tag, issue type, page, and size filters.

- [ ] **Step 6: Run tests and commit**

```bash
cd aicp-backend
mvn -Dtest=StoryboardEditingServiceTest test
mvn test
git add src/main/java/com/aicp/module/storyboard src/test/java/com/aicp/module/storyboard
git commit -m "feat: add storyboard scene and shot editing"
```

---

### Task 5: Add professional modules, review checks, and production gates

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/domain/ProductionGate.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/service/StoryboardProfessionalService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/service/StoryboardReviewService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/controller/StoryboardProfessionalController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/storyboard/service/StoryboardReviewServiceTest.java`

- [ ] **Step 1: Write failing gate tests**

```java
@Test
void cTierFailsWhenProductionFieldsAreMissing() {
    StoryboardVersion version = locked(Tier.C, 3);
    StoryboardShot shot = shot(1L, 3000L);
    shot.setImagePrompt("image prompt");
    shot.setVideoMotionPrompt(null);
    var result = gate.evaluate(version, List.of(shot), List.of(), List.of());
    assertThat(result.allowed()).isFalse();
    assertThat(result.violations()).contains("S01-C01 缺少视频动作提示词");
}
```

- [ ] **Step 2: Implement professional module replacement APIs**

For each module, accept the version `revision`, validate all referenced shot codes/keys, replace rows in one transaction, increment revision once, and write an audit record. Do not increment revision per row.

```java
public record ReplacePromptTemplatesRequest(int revision,
    List<PromptTemplateInput> items) {}
public record PromptTemplateInput(String templateCode, String emotionName,
    List<String> shotCodes, String imagePrompt, String videoMotionPrompt) {}
```

- [ ] **Step 3: Implement deterministic checks**

Create issues for:

- duration <= 0 or above 30 seconds;
- six consecutive shots with identical shot size;
- dialogue estimated speaking time greater than shot duration;
- missing visual identity locks for referenced characters;
- inconsistent visual lock values across adjacent bindings;
- missing image/video prompts;
- missing C-tier dub/subtitle/failure strategy;
- unresolved creative-rule target ranges.

Use stable issue fingerprints:

```java
static String sha256(String value) {
    try {
        byte[] bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 unavailable", e);
    }
}
String fingerprint = sha256(versionId + "|" + type + "|" + shotKey + "|" + message);
```

Upsert by `(version_id, fingerprint)` so reruns do not duplicate issues.

- [ ] **Step 4: Implement review issue workflow**

Allowed states: `open`, `resolved`, `ignored`. Resolve and ignore require a note and actor ID. Locking calls `requireNoBlockingIssues(versionId)`.

- [ ] **Step 5: Implement gates**

- A/B concept snapshot: version locked, no open blocking issue.
- C production snapshot: version locked, every shot confirmed, full production fields, full visual bindings, no open error issue.

- [ ] **Step 6: Run tests and commit**

```bash
cd aicp-backend
mvn -Dtest=StoryboardReviewServiceTest test
mvn test
git add src/main/java/com/aicp/module/storyboard src/test/java/com/aicp/module/storyboard
git commit -m "feat: add storyboard professional review gates"
```

---

### Task 6: Add job orchestration and seven-sheet XLSX round-tripping

**Files:**
- Modify: `aicp-backend/pom.xml`
- Modify: `aicp-backend/src/main/resources/application.yml`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/service/StoryboardJobService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/service/StoryboardExportStorageService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/controller/StoryboardJobController.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/exchange/StoryboardWorkbookSchema.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/exchange/StoryboardWorkbookImporter.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/exchange/StoryboardWorkbookExporter.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/exchange/StoryboardPdfExporter.java`
- Create: `aicp-backend/src/test/resources/fixtures/storyboard-13d.xlsx`
- Create: `aicp-backend/src/main/resources/fonts/NotoSansCJKsc-VF.ttf`
- Create: `aicp-backend/src/main/resources/fonts/OFL.txt`
- Create: `aicp-backend/src/test/java/com/aicp/module/storyboard/exchange/StoryboardWorkbookRoundTripTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/storyboard/exchange/StoryboardPdfExporterTest.java`

- [ ] **Step 1: Add the approved workbook as a golden fixture**

```bash
cp '/Users/apple/Desktop/漫剧/小说/第一章分镜头脚本_13维细化版_人物三视图优化终版.xlsx' \
  aicp-backend/src/test/resources/fixtures/storyboard-13d.xlsx
```

- [ ] **Step 2: Write the failing golden import test**

```java
@Test
void importsReferenceWorkbookExactly() throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/fixtures/storyboard-13d.xlsx")) {
        WorkbookImportModel model = importer.parse(in);
        assertThat(model.sheetNames()).containsExactly(
            "分镜头脚本", "情绪强度总览", "提示词模板", "奥斯卡三线修订表",
            "设定一致性修订表", "人物三视图视觉规范", "三视图分镜应用表");
        assertThat(model.scenes()).hasSize(6);
        assertThat(model.shots()).hasSize(45);
        assertThat(model.totalDurationMs()).isEqualTo(119_500L);
        assertThat(model.shots()).allSatisfy(shot ->
            assertThat(shot.dimensionCount()).isEqualTo(13));
    }
}
```

- [ ] **Step 3: Define the workbook schema explicitly**

```java
public static final List<String> SHOT_HEADERS = List.of(
    "镜号", "时长(s)", "景别", "画面描述", "光影氛围", "角色动作", "情绪",
    "对白", "场景标签", "音效", "参考", "分镜提示词", "视频动作提示词");
public static final List<String> REQUIRED_SHEETS = List.of(
    "分镜头脚本", "情绪强度总览", "提示词模板", "奥斯卡三线修订表",
    "设定一致性修订表", "人物三视图视觉规范", "三视图分镜应用表");
public static final int SCHEMA_VERSION = 1;
```

- [ ] **Step 4: Implement preflight parsing and cell errors**

Use Apache POI. Read with `DataFormatter`, normalize Unicode whitespace, parse duration values `3`, `3s`, `3.5s`, and reject formulas with cached errors. Return:

```java
public record WorkbookCellError(String sheet, String cell, String originalValue,
    String code, String message, String suggestion) {}
public record WorkbookPreflightResult(boolean valid, WorkbookImportModel model,
    List<WorkbookCellError> errors, WorkbookDiff diff) {}
```

Validate all shot ranges and visual bindings against imported shot codes. If errors exist, the apply method must reject before opening a write transaction.

- [ ] **Step 5: Implement transactional apply**

Create a new draft version with `created_from=import`, insert all seven module datasets, store source SHA-256 and schema version on the job result, and set `current_draft_version_id`. Never modify the version selected for comparison.

- [ ] **Step 6: Implement seven-sheet export and re-import equality test**

Exporter requirements:

- same visible sheet order and headers;
- dark header fill, frozen first row, autofilter, bounded column widths, wrapped long text;
- hidden `_schema` sheet with `schema_version`, storyboard/version UUIDs, scene keys, and shot keys;
- numeric duration stored as seconds with `0.0` number format;
- no clipped header cells.

Expose `ExportMode.FULL_WORKBOOK` and `ExportMode.PRODUCTION_WORKBOOK`. The production workbook contains `镜头索引`, `AI抽卡表`, `AI视频表`, and `配音字幕表`; it is rejected unless the selected version is locked C tier and passes the production gate.

Test semantic equality:

```java
byte[] exported = exporter.export(model);
WorkbookImportModel importedAgain = importer.parse(new ByteArrayInputStream(exported));
assertThat(importedAgain.semanticDigest()).isEqualTo(model.semanticDigest());
```

- [ ] **Step 7: Implement director PDF export**

Before implementing PDF export, add PDFBox and the redistributable CJK font:

```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
</dependency>
```

```bash
curl -L 'https://github.com/notofonts/noto-cjk/raw/main/Sans/Variable/TTF/NotoSansCJKsc-VF.ttf' \
  -o aicp-backend/src/main/resources/fonts/NotoSansCJKsc-VF.ttf
curl -L 'https://github.com/notofonts/noto-cjk/raw/main/LICENSE' \
  -o aicp-backend/src/main/resources/fonts/OFL.txt
```

Implement `StoryboardPdfExporter` with PDFBox, embedding the font, an A4 landscape shot table, repeated headers, wrapped Chinese text, page numbers, version metadata, and review summary. The test extracts text with `PDFTextStripper` and asserts it contains `分镜专业编辑器`, `S01-C04`, and the locked version number.

- [ ] **Step 8: Add job state, SSE, and short-lived downloads**

`POST` creates a queued job using `(project_id, job_type, idempotency_key)` uniqueness. `GET /events` emits progress objects:

```java
public record JobProgress(String jobId, String status, int percent,
    String stage, String message) {}
```

Keep polling `GET /storyboard-jobs/{jobId}` as fallback.

Write export bytes below `${storyboard.export-root:./data/storyboard-exports}` using generated server-side names. `StoryboardExportStorageService` returns a five-minute URL with `jobId`, expiry epoch, and HMAC-SHA256 signature using `${storyboard.export-signing-secret}`. The download endpoint validates signature, expiry, current-user project access, and stored SHA-256 before streaming; API responses never expose filesystem paths.

Add configuration:

```yaml
storyboard:
  export-root: ${STORYBOARD_EXPORT_ROOT:./data/storyboard-exports}
  export-signing-secret: ${STORYBOARD_EXPORT_SIGNING_SECRET:${jwt.secret}}
  download-expiry-seconds: 300
```

- [ ] **Step 9: Run tests and commit**

```bash
cd aicp-backend
mvn -Dtest=StoryboardWorkbookRoundTripTest,StoryboardPdfExporterTest test
mvn test
git add pom.xml src/main/resources/application.yml src/main/resources/fonts \
  src/main/java/com/aicp/module/storyboard \
  src/test/java/com/aicp/module/storyboard \
  src/test/resources/fixtures/storyboard-13d.xlsx
git commit -m "feat: add storyboard workbook round trip"
```

---

### Task 7: Add AI generation, safe candidate application, and canvas snapshots

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/service/StoryboardGenerationService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/storyboard/service/StoryboardCanvasSnapshotService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/storyboard/service/StoryboardGenerationServiceTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/storyboard/service/StoryboardCanvasSnapshotServiceTest.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/CanvasBridgeService.java`

- [ ] **Step 1: Write failing tests for non-destructive AI behavior**

```java
@Test
void localRewriteCreatesCandidateWithoutChangingShot() {
    StoryboardShot shot = shot(4L, 2000L);
    shot.setVisualDescription("人工描述");
    RewriteCandidate candidate = service.rewriteShot(1L, 10L, 4L, 7L,
        Set.of("lightingAtmosphere"), "rewrite-4");
    assertThat(shot.getVisualDescription()).isEqualTo("人工描述");
    assertThat(candidate.patch()).doesNotContainKey("visualDescription");
}
```

- [ ] **Step 2: Implement structured generation contracts**

Define JSON schemas for A generation, B enrichment, C production fields, and local patch candidates under:

```text
aicp-backend/src/main/resources/schemas/storyboard/
```

Parse through the existing `AiResponseParser`, validate every returned `shot_key`, and persist raw output on the job. Invalid AI JSON marks the job failed and writes no storyboard rows.

- [ ] **Step 3: Implement candidate preview and explicit apply**

Candidates contain base revision, changed fields, old value, new value, and model metadata. Apply only selected fields if the revision still matches; otherwise return `STORYBOARD_REVISION_CONFLICT`.

- [ ] **Step 4: Implement derived upgrade jobs**

Call Task 3 to create B/C child drafts, run AI enrichment against the child, preserve parent, and mark incomplete shots `needs_review`. A partial model response yields job `partial`, never auto-locks.

- [ ] **Step 5: Write failing snapshot gate tests**

```java
@Test
void cSnapshotRequiresProductionGate() {
    when(gate.evaluate(any(), anyList(), anyList(), anyList()))
        .thenReturn(new GateResult(false, List.of("S01-C04 缺少失败策略")));
    assertThatThrownBy(() -> service.createSnapshot(1L, 10L, 7L, "snap-10"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("生产准入");
}
```

- [ ] **Step 6: Implement immutable snapshot payload**

Serialize version metadata, scenes, shots, professional modules, project parameter version, source content version, gate report, and SHA-256. Pass only the snapshot ID to canvas creation. The same idempotency key returns the existing snapshot.

- [ ] **Step 7: Run tests and commit**

```bash
cd aicp-backend
mvn -Dtest=StoryboardGenerationServiceTest,StoryboardCanvasSnapshotServiceTest test
mvn test
git add src/main/java/com/aicp/module/storyboard \
  src/main/java/com/aicp/module/contentproject/service/CanvasBridgeService.java \
  src/main/resources/schemas/storyboard \
  src/test/java/com/aicp/module/storyboard
git commit -m "feat: add storyboard generation and canvas snapshots"
```

---

### Task 8: Add frontend data normalization, save queue, API, and route shell

**Files:**
- Modify: `aicp-frontend/package.json`
- Create: `aicp-frontend/src/api/storyboardV2.js`
- Create: `aicp-frontend/src/views/storyboard/storyboardData.js`
- Create: `aicp-frontend/src/views/storyboard/composables/useStoryboardEditor.js`
- Create: `aicp-frontend/src/views/storyboard/composables/useStoryboardJobs.js`
- Create: `aicp-frontend/src/views/storyboard/StoryboardEditor.vue`
- Modify: `aicp-frontend/src/router/index.js`
- Create: `aicp-frontend/tests/storyboard-data.test.js`

- [ ] **Step 1: Add frontend test dependencies and script**

```json
"scripts": {
  "dev": "vite",
  "build": "vite build",
  "preview": "vite preview",
  "test": "vitest run"
},
"devDependencies": {
  "@vitejs/plugin-vue": "^5.0.0",
  "@vue/test-utils": "^2.4.6",
  "jsdom": "^24.1.3",
  "vite": "^5.4.22",
  "vitest": "^2.1.9"
}
```

Run `npm install` to update the lockfile.

- [ ] **Step 2: Write failing pure data tests**

```javascript
import { describe, it, expect } from 'vitest'
import { createRevisionSaveQueue, normalizeShot, applyOptimisticPatch } from '../src/views/storyboard/storyboardData'

it('serializes saves and advances revision from responses', async () => {
  const queue = createRevisionSaveQueue(3)
  const seen = []
  await Promise.all([
    queue.enqueue(revision => { seen.push(revision); return { revision: 4 } }),
    queue.enqueue(revision => { seen.push(revision); return { revision: 5 } })
  ])
  expect(seen).toEqual([3, 4])
})
```

- [ ] **Step 3: Implement data helpers**

```javascript
export function createRevisionSaveQueue(initialRevision) {
  let revision = initialRevision
  let tail = Promise.resolve()
  return {
    enqueue(save) {
      const run = tail.then(() => save(revision)).then(result => {
        revision = result.revision
        return result
      })
      tail = run.catch(() => undefined)
      return run
    },
    getRevision: () => revision
  }
}
```

Add snake_case normalization, optimistic patch/rollback, shot-code sorting, scene grouping, duration totals, field presets, and conflict diff helpers.

- [ ] **Step 4: Implement API methods**

Cover master/version list and detail, scene/shot CRUD, batch/reorder, professional modules, review, jobs, import/export, upgrade, lock, and snapshot. Keep all calls in `storyboardV2.js`; components do not call `request` directly.

- [ ] **Step 5: Add route and shell**

```javascript
{
  path: 'content-projects/:projectId/storyboards/:storyboardId',
  name: 'StoryboardEditorV2',
  component: () => import('@/views/storyboard/StoryboardEditor.vue'),
  meta: { title: '分镜专业编辑器' }
}
```

The shell loads master, versions, active version, scenes, and first shot page; it renders explicit loading, error, empty, and locked states.

- [ ] **Step 6: Run tests/build and commit**

```bash
cd aicp-frontend
npm test -- tests/storyboard-data.test.js
npm run build
git add package.json package-lock.json src/api/storyboardV2.js \
  src/views/storyboard src/router/index.js tests/storyboard-data.test.js
git commit -m "feat: add storyboard editor frontend foundation"
```

---

### Task 9: Build the core three-column editor and autosave UX

**Files:**
- Create: core editor components from the frontend file map
- Create: `aicp-frontend/tests/storyboard-editor.test.js`

- [ ] **Step 1: Write failing component tests**

```javascript
import { mount } from '@vue/test-utils'
import { describe, it, expect, vi } from 'vitest'
import ShotInspector from '../src/views/storyboard/components/ShotInspector.vue'

it('emits a field patch without mutating props', async () => {
  const shot = { id: 4, visualDescription: '原描述', locked: false }
  const wrapper = mount(ShotInspector, { props: { shot } })
  await wrapper.get('[data-testid="visual-description"]').setValue('新描述')
  expect(wrapper.emitted('patch')[0][0]).toEqual({ visualDescription: '新描述' })
  expect(shot.visualDescription).toBe('原描述')
})
```

- [ ] **Step 2: Implement the editor layout**

Use CSS grid `180px minmax(580px, 1fr) 300px`, collapse the left/right panes below 1150px, and keep the selected shot in route query state. Add `data-testid` contracts for tests.

- [ ] **Step 3: Implement ShotGrid and structural editing**

Columns in the default preset: selection, shot code, duration, shot size, visual summary, dialogue, status. Add draggable scene/shot order, multi-select, batch field patch, create, duplicate, split, merge, and delete confirmations.

- [ ] **Step 4: Implement the five inspector groups**

- content: visual description, dialogue, scene tags;
- director: shot size, lighting, action, emotion, reference;
- sound: sound effect, dialogue/dub preview;
- prompts: image and video motion prompts with character counters;
- continuity: visual locks, linked rules, and review issues.

All edits emit patches into the 800ms debounced revision queue. Locked versions render inputs as read-only and show “复制为新草稿”.

- [ ] **Step 5: Implement card and timeline views**

Card view reuses the same shot objects and patch events. Timeline renders duration-proportional blocks, total duration, scene boundaries, and selection; it does not maintain separate state.

- [ ] **Step 6: Implement save states and leave protection**

Show `等待保存`, `保存中`, `已保存`, `保存失败`, `版本冲突`. Register `onBeforeRouteLeave` and `beforeunload` only while dirty/failed. Conflict drawer shows local/server values and offers keep-local, use-server, and manual merge.

- [ ] **Step 7: Run tests/build and commit**

```bash
cd aicp-frontend
npm test -- tests/storyboard-editor.test.js tests/storyboard-data.test.js
npm run build
git add src/views/storyboard tests/storyboard-editor.test.js
git commit -m "feat: build storyboard professional editor core"
```

---

### Task 10: Build professional modules, version review, and workbook exchange UI

**Files:**
- Create: professional panel components from the frontend file map
- Create: `aicp-frontend/tests/storyboard-professional-panels.test.js`

- [ ] **Step 1: Write failing module mapping tests**

```javascript
it('maps every reference workbook sheet to a professional module', () => {
  expect(WORKBOOK_MODULES.map(item => item.sheet)).toEqual([
    '分镜头脚本', '情绪强度总览', '提示词模板', '奥斯卡三线修订表',
    '设定一致性修订表', '人物三视图视觉规范', '三视图分镜应用表'
  ])
})
```

- [ ] **Step 2: Implement module panels**

- Emotion: ECharts intensity line, range table, scene rhythm warnings.
- Prompt templates: CRUD, shot-range picker, apply preview.
- Creative rules: type/consistency modes, affected shot links, status.
- Character visuals: identity, daily/task look, performance anchors, prompt lock, shot bindings.
- Version review: lineage, side-by-side field diff, issues, submit/reopen/resolve/lock/fork/upgrade.

- [ ] **Step 3: Implement workbook exchange drawer**

Import states: upload, preflight, cell error list, semantic diff, confirm apply, job progress, success. Export options: seven-sheet XLSX, director PDF, C-tier production XLSX. Never mark import complete until the new draft version ID loads successfully.

- [ ] **Step 4: Implement job progress with fallback**

Use `EventSource` for `/events`; on error close it and poll every two seconds until terminal status. Cancel polling on unmount.

- [ ] **Step 5: Run tests/build and commit**

```bash
cd aicp-frontend
npm test -- tests/storyboard-professional-panels.test.js tests/storyboard-editor.test.js
npm run build
git add src/views/storyboard tests/storyboard-professional-panels.test.js
git commit -m "feat: add storyboard professional modules and exchange UI"
```

---

### Task 11: Integrate content projects and replace the old storyboard entry

**Files:**
- Modify: `aicp-frontend/src/views/content-project/ContentProjectWorkspace.vue`
- Modify: `aicp-frontend/src/api/contentProject.js`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectWorkflowService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/storyboard/controller/StoryboardController.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/storyboard/service/StoryboardQueryService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/storyboard/StoryboardSummaryIntegrationTest.java`

- [ ] **Step 1: Write failing summary integration test**

```java
@Test
void projectSummaryExposesLatestStoryboardState() {
    StoryboardSummary view = queryService.summary(projectId, userId);
    assertThat(view.currentTier()).isEqualTo("A");
    assertThat(view.totalShots()).isEqualTo(45);
    assertThat(view.openIssueCount()).isEqualTo(2);
    assertThat(view.editorPath()).isEqualTo(
        "/content-projects/1/storyboards/10");
}
```

- [ ] **Step 2: Extend master queries with project summary**

Return current tier, version, source version, counts, open issues, last update, active job, and editor path in one aggregate query, not multiple per-card calls.

- [ ] **Step 3: Replace embedded editor with summary card**

The workspace storyboard stage shows status, tier, version, source version, counts, last updated time, generate/open actions, and current job. It no longer fetches scenes/shots or renders an editor.

- [ ] **Step 4: Update workflow status events**

- first draft created: storyboard intent `in_progress`;
- version locked: storyboard intent `completed`;
- skipped remains independent and does not delete existing versions.

- [ ] **Step 5: Run tests/build and commit**

```bash
cd aicp-backend
mvn -Dtest=StoryboardSummaryIntegrationTest,ProjectWorkflowServiceTest test
cd ../aicp-frontend
npm test
npm run build
git add ../aicp-backend/src/main/java/com/aicp/module/storyboard \
  ../aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectWorkflowService.java \
  src/views/content-project/ContentProjectWorkspace.vue src/api/contentProject.js
git commit -m "feat: integrate professional storyboard editor"
```

---

### Task 12: Remove legacy implementations and enforce the single domain

**Files:**
- Delete: the twelve legacy frontend/backend files listed under “Legacy files removed only in Task 12”
- Modify: `aicp-frontend/src/router/index.js`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Create: `aicp-backend/src/test/java/com/aicp/module/storyboard/schema/NoLegacyStoryboardDomainTest.java`

- [ ] **Step 1: Write a failing legacy absence test**

```java
@Test
void legacyContentProjectStoryboardTablesAreAbsent() {
    for (String table : List.of("cp_storyboard_masters", "cp_storyboard_scenes", "cp_storyboard_shots")) {
        Integer count = jdbc.queryForObject(
            "select count(*) from information_schema.tables where table_name = ?",
            Integer.class, table.toUpperCase());
        assertThat(count).isZero();
    }
}
```

- [ ] **Step 2: Delete old frontend route/API/components**

Remove `/storyboard/:scriptId`, `Storyboard.vue`, `storyboard.js`, `StoryboardPanel.vue`, and `ShotCard.vue`. Search for imports and route names before deletion:

```bash
rg -n "StoryboardPanel|ShotCard|@/api/storyboard|name: 'Storyboard'|/storyboard/" aicp-frontend/src
```

Expected after deletion: no matches except the new plural `/storyboards/` route.

- [ ] **Step 3: Delete old content-project backend implementation**

Delete controller, service, entities, and mappers listed above. Remove `cp_storyboard_*` DDL. Keep the canvas legacy `storyboard_shots` table because the current canvas editor still reads it, but remove every content-project write to that table. `CanvasBridgeService` must create canvas nodes from the immutable snapshot payload and must not insert canvas storyboard rows.

- [ ] **Step 4: Add static architecture guards**

Add tests or `rg` assertions in CI that forbid imports from deleted classes and direct content-project writes to canvas `StoryboardShotMapper`.

- [ ] **Step 5: Run all tests/build and commit**

```bash
cd aicp-backend
mvn test
cd ../aicp-frontend
npm test
npm run build
git add -A
git commit -m "refactor: remove legacy storyboard implementations"
```

---

### Task 13: Add security, performance, visual, and full E2E acceptance

**Files:**
- Create: `aicp-backend/src/test/java/com/aicp/module/storyboard/StoryboardSecurityIntegrationTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/storyboard/StoryboardPerformanceTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/storyboard/StoryboardLifecycleE2ETest.java`
- Create: `aicp-frontend/tests/storyboard-route.spec.js`
- Modify: `README.md`

- [ ] **Step 1: Add cross-project security tests**

For Master, Version, Scene, Shot, professional module, job, export, and snapshot endpoints, request project A with resource IDs from project B and assert 403 or 404 without leaking the foreign resource title or ID.

- [ ] **Step 2: Add 2,000-shot performance test**

Seed 100 scenes × 20 shots. Assert paged query returns 100 rows without loading long prompts and completes under the agreed local-test threshold of two seconds. Assert a 200-shot batch patch increments revision once and updates all rows transactionally.

- [ ] **Step 3: Add lifecycle E2E**

Exercise:

```text
locked content version
→ create storyboard
→ generate/import A draft
→ edit/reorder
→ run checks
→ resolve issues
→ lock A
→ derive/lock B
→ derive C
→ pass production gate
→ export XLSX
→ create canvas snapshot
```

Assert parent versions remain unchanged and snapshot hash is stable under idempotent retries.

- [ ] **Step 4: Add browser route smoke test**

Mock APIs and verify 1366×768 renders topbar, module tabs, scene navigator, shot grid, and inspector without horizontal page overflow. Verify locked state disables inputs and offers fork.

- [ ] **Step 5: Verify exported artifacts visually**

Render every exported XLSX sheet and the director PDF. Check headers, wrapping, frozen logical structure, all 13 columns, all 7 sheets, and no clipped critical cells. Keep rendered QA files outside git.

- [ ] **Step 6: Update README**

Document the new route, API area, version lifecycle, workbook template, commands, and removal of the old editor.

- [ ] **Step 7: Run final verification**

```bash
cd aicp-backend
mvn test
cd ../aicp-frontend
npm test
npm run build
```

Expected: all tests PASS and Vite production build completes.

- [ ] **Step 8: Commit**

```bash
git add aicp-backend/src/test aicp-frontend/tests README.md
git commit -m "test: verify storyboard professional workflow"
```

---

## Final acceptance checklist

- [ ] One storyboard domain and one editor route remain.
- [ ] All child resources enforce project and parent ownership.
- [ ] Draft autosave uses compare-and-set revision updates.
- [ ] Locked versions reject every mutation path.
- [ ] A/B/C upgrades create child versions and preserve parents.
- [ ] All 13 shot dimensions are editable and exportable.
- [ ] All six professional modules are version-scoped.
- [ ] The approved workbook imports as 6 scenes, 45 shots, and 119.5 seconds.
- [ ] Seven-sheet export re-imports to the same semantic digest.
- [ ] AI output never overwrites manual data without explicit apply.
- [ ] A/B concept and C production gates enforce their distinct requirements.
- [ ] Canvas consumes immutable snapshots only.
- [ ] 2,000-shot paging and 200-shot batch editing meet the test baseline.
- [ ] Backend tests, frontend tests, and production build pass.
