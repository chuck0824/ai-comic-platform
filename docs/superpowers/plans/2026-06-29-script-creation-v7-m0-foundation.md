# Script Creation V7.1 M0 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the V7.1 content-project foundation so users can create, list, resume, edit, version, and safely route a short-drama project through an adaptive workflow without breaking legacy scripts.

**Architecture:** Add a new `contentproject` bounded module beside the legacy `script` module. Store project metadata, members, immutable parameter versions, stable content units, draft/version history, dependencies, generation jobs, and Outbox events in new tables; expose `/api/v1/content-projects` APIs and keep legacy script APIs read-compatible through a projection adapter. The Vue application receives a new content-project list, create page, and adaptive workspace shell; M1 will plug short-drama generation and Agent review into these contracts.

**Tech Stack:** Java 17 bytecode on Spring Boot 3.2.5, MyBatis-Plus 3.5.6, H2/MySQL, JUnit 5, Mockito, Vue 3 Composition API, Vue Router, Element Plus, Axios, Vite, Node.js built-in test runner.

---

## Scope and delivery boundary

M0 delivers these working slices:

- Content-project create/get/list/update with owner membership and action-level access checks.
- Immutable parameter versions and adaptive workflow state/resume position.
- Stable content units, autosaved drafts, named content versions, optimistic revision conflicts.
- Optional storyboard intent without creating or charging a storyboard task.
- Content-status aggregation, artifact dependencies, generation-job input snapshots, Outbox events.
- Legacy script backfill/projection compatibility.
- Frontend project list, minimal create flow, adaptive workspace shell, refresh/resume.

M0 does not generate synopsis, episodes, Agent reports, storyboards, TVC scripts, or canvas assets. Those are M1–M5 consumers of the M0 contracts.

## File structure

### Backend files created

- `aicp-backend/src/main/java/com/aicp/module/contentproject/domain/ContentProjectEnums.java`: allowed modes, roles, statuses, unit types, and storyboard intents.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/*.java`: one MyBatis entity for each M0 table.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/*.java`: one `BaseMapper` per entity.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/ContentProjectRequests.java`: validated request records.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/ContentProjectViews.java`: stable API response records.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectAccessService.java`: role/action authorization.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContentProjectService.java`: project lifecycle and list/resume.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectWorkflowService.java`: adaptive stage calculation.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContentUnitService.java`: draft and version operations.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContentStatusService.java`: project status aggregation.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ArtifactDependencyService.java`: stale dependency records.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContextAssembler.java`: immutable generation input snapshots.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContentGenerationJobService.java`: idempotent job creation/status.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/OutboxService.java`: transaction-bound event persistence.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/service/LegacyProjectProjectionService.java`: scripts-to-project backfill and legacy read projection.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentProjectController.java`: V7.1 REST endpoints.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentUnitController.java`: unit draft/version endpoints under `/api/v1/content-units`.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentGenerationJobController.java`: generation-job endpoints under `/api/v1/generation-jobs`.
- `aicp-backend/src/main/resources/schemas/content-generation-job-v1.schema.json`: machine-readable M0 job result envelope.
- `aicp-backend/src/test/java/com/aicp/module/contentproject/**/*.java`: service, schema, access, workflow, conflict, and controller coverage.

### Backend files modified

- `aicp-backend/pom.xml`: deterministic test runtime on the locally installed Java 26 JVM while retaining Java 17 bytecode.
- `aicp-backend/src/test/java/com/aicp/module/auth/service/AuthServiceTest.java`: repair stale Redis mock signatures and exception-field assertions.
- `aicp-backend/src/main/resources/db/schema-h2.sql`: M0 H2 tables and indexes.
- `aicp-backend/src/main/resources/db/schema-mysql.sql`: M0 MySQL tables and indexes.
- `aicp-backend/src/main/resources/db/schema.sql`: keep the generic bootstrap schema aligned.
- `aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java`: V7.1 project/workflow/conflict codes.
- `aicp-backend/src/main/java/com/aicp/common/exception/GlobalExceptionHandler.java`: map project conflicts and validation details consistently.

### Frontend files created

- `aicp-frontend/src/api/contentProject.js`: V7.1 project/workflow/unit API client.
- `aicp-frontend/src/views/content-project/ContentProjectList.vue`: project cards and recovery entry.
- `aicp-frontend/src/views/content-project/ContentProjectCreate.vue`: four-field start flow.
- `aicp-frontend/src/views/content-project/ContentProjectWorkspace.vue`: adaptive workspace shell.
- `aicp-frontend/src/views/content-project/components/WorkflowRail.vue`: stage/status navigation.
- `aicp-frontend/src/views/content-project/components/ContextPanel.vue`: selected versions, locked facts, and impact summary.
- `aicp-frontend/src/views/content-project/utils/workflowPath.js`: pure stage and primary-action selection.
- `aicp-frontend/tests/content-project-workflow.test.js`: deterministic adaptive-path tests.

### Frontend files modified

- `aicp-frontend/src/router/index.js`: new list/create/workspace routes and legacy redirects.
- `aicp-frontend/src/views/Warehouse.vue`: delegate to the new content-project list instead of rendering a second repository.
- `aicp-frontend/src/views/ScriptGen.vue`: accept a project route and remain a temporary legacy compatibility entry only.

---

### Task 1: Repair and freeze the test baseline

**Files:**
- Modify: `aicp-backend/pom.xml`
- Modify: `aicp-backend/src/test/java/com/aicp/module/auth/service/AuthServiceTest.java`

- [ ] **Step 1: Reproduce both baseline failures**

Run:

```bash
cd aicp-backend
mvn test
mvn -Dnet.bytebuddy.experimental=true test
```

Expected: the first command fails because Byte Buddy rejects Java 26; the second reaches the tests and fails on `RedisUtil.get(key, String.class)` and the nonexistent `errorCode` property.

- [ ] **Step 2: Configure Surefire for the local JVM without changing production bytecode**

Add to `aicp-backend/pom.xml` under `<plugins>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.1.2</version>
    <configuration>
        <systemPropertyVariables>
            <net.bytebuddy.experimental>true</net.bytebuddy.experimental>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

Keep `<java.version>17</java.version>` unchanged.

- [ ] **Step 3: Repair the existing AuthService test contract**

Replace all Redis stubs such as:

```java
when(redisUtil.get("code:register:13800000001")).thenReturn("123456");
```

with:

```java
when(redisUtil.get("code:register:13800000001", String.class)).thenReturn("123456");
```

Replace exception assertions that extract `errorCode` with:

```java
assertThatThrownBy(() -> authService.devInit("admin", "admin123"))
        .isInstanceOf(BizException.class)
        .extracting("code")
        .isEqualTo(ErrorCode.FORBIDDEN.getCode());
```

- [ ] **Step 4: Verify the baseline**

Run: `cd aicp-backend && mvn test`

Expected: `Tests run: 4, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 5: Commit the baseline repair**

```bash
git add aicp-backend/pom.xml aicp-backend/src/test/java/com/aicp/module/auth/service/AuthServiceTest.java
git commit -m "test: restore backend test baseline"
```

### Task 2: Add the M0 database schema

**Files:**
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Modify: `aicp-backend/src/main/resources/db/schema.sql`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/schema/ContentProjectSchemaTest.java`

- [ ] **Step 1: Write a failing schema test**

```java
@SpringBootTest
@ActiveProfiles("dev")
class ContentProjectSchemaTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void m0TablesExist() {
        for (String table : List.of(
                "content_projects", "project_members", "project_parameter_versions",
                "content_units", "content_versions", "artifact_dependencies",
                "content_generation_jobs", "outbox_events")) {
            Integer count = jdbc.queryForObject(
                    "select count(*) from information_schema.tables where table_name = ?",
                    Integer.class, table.toUpperCase());
            assertThat(count).isEqualTo(1);
        }
    }
}
```

- [ ] **Step 2: Run the schema test and confirm failure**

Run: `cd aicp-backend && mvn -Dtest=ContentProjectSchemaTest test`

Expected: FAIL because `content_projects` does not exist.

- [ ] **Step 3: Add the eight M0 tables to all three schema files**

Use the following column contract. Use `TEXT` for JSON payloads in all profiles so H2 and MySQL behave consistently.

```sql
CREATE TABLE IF NOT EXISTS content_projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    tenant_type VARCHAR(20) NOT NULL,
    tenant_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    creation_mode VARCHAR(30) NOT NULL,
    source_mode VARCHAR(30) NOT NULL,
    storyboard_intent_status VARCHAR(20) NOT NULL DEFAULT 'not_decided',
    content_status VARCHAR(20) NOT NULL DEFAULT 'draft',
    production_status VARCHAR(20) NOT NULL DEFAULT 'not_started',
    market_status VARCHAR(20) NOT NULL DEFAULT 'private',
    last_stage_key VARCHAR(50),
    last_task_key VARCHAR(50),
    last_content_unit_id BIGINT,
    current_parameter_version_id BIGINT,
    legacy_script_id BIGINT,
    converted_from_project_id BIGINT,
    copied_from_project_id BIGINT,
    revision INT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS project_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_member UNIQUE (project_id, user_id)
);

CREATE TABLE IF NOT EXISTS project_parameter_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    payload_json TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_parameter_version UNIQUE (project_id, version_no)
);

CREATE TABLE IF NOT EXISTS content_units (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stable_key VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    unit_type VARCHAR(20) NOT NULL,
    display_no INT NOT NULL,
    title VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    current_version_id BIGINT,
    revision INT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_unit_display UNIQUE (project_id, unit_type, display_no)
);

CREATE TABLE IF NOT EXISTS content_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    content_unit_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    content_json TEXT NOT NULL,
    plain_text TEXT,
    source VARCHAR(30) NOT NULL,
    generation_job_id BIGINT,
    content_hash VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_content_unit_version UNIQUE (content_unit_id, version_no)
);

CREATE TABLE IF NOT EXISTS artifact_dependencies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_version_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_version_id BIGINT NOT NULL,
    dependency_type VARCHAR(30) NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    sync_status VARCHAR(20) NOT NULL DEFAULT 'current',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_artifact_dependency UNIQUE (source_version_id, target_version_id, dependency_type)
);

CREATE TABLE IF NOT EXISTS content_generation_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    job_type VARCHAR(40) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    input_snapshot_json TEXT NOT NULL,
    input_snapshot_hash VARCHAR(64) NOT NULL,
    schema_version VARCHAR(30) NOT NULL,
    model VARCHAR(100),
    prompt_version VARCHAR(50),
    estimated_credits INT NOT NULL DEFAULT 0,
    actual_credits INT NOT NULL DEFAULT 0,
    error_code VARCHAR(50),
    retry_of_job_id BIGINT,
    idempotency_key VARCHAR(120) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    CONSTRAINT uk_project_job_idempotency UNIQUE (project_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    aggregate_type VARCHAR(30) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    aggregate_revision INT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP,
    occurred_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);
```

Add indexes on `content_projects(tenant_type, tenant_id, updated_at)`, `content_projects(owner_user_id, updated_at)`, `project_members(user_id, project_id)`, `content_units(project_id, display_no)`, `content_versions(project_id, created_at)`, `content_generation_jobs(project_id, status, created_at)`, and `outbox_events(status, next_attempt_at)`.

- [ ] **Step 4: Verify schema startup**

Run: `cd aicp-backend && mvn -Dtest=ContentProjectSchemaTest test`

Expected: PASS and all eight tables found.

- [ ] **Step 5: Commit the schema**

```bash
git add aicp-backend/src/main/resources/db aicp-backend/src/test/java/com/aicp/module/contentproject/schema/ContentProjectSchemaTest.java
git commit -m "feat: add content project foundation schema"
```

### Task 3: Add domain enums, entities, and mappers

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/domain/ContentProjectEnums.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/ContentProject.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/ProjectMember.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/ProjectParameterVersion.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/ContentUnit.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/ContentVersion.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/ArtifactDependency.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/ContentGenerationJob.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/OutboxEvent.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/*.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/domain/ContentProjectEnumsTest.java`

- [ ] **Step 1: Write failing enum validation tests**

```java
class ContentProjectEnumsTest {
    @Test void acceptsOnlySupportedCreationModes() {
        assertThat(ContentProjectEnums.CreationMode.parse("short_drama"))
                .isEqualTo(ContentProjectEnums.CreationMode.SHORT_DRAMA);
        assertThatThrownBy(() -> ContentProjectEnums.CreationMode.parse("movie"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void ownerCanPerformEveryProjectAction() {
        assertThat(ContentProjectEnums.Role.OWNER.allows(ContentProjectEnums.Action.DELETE_PROJECT)).isTrue();
        assertThat(ContentProjectEnums.Role.VIEWER.allows(ContentProjectEnums.Action.EDIT_CONTENT)).isFalse();
    }
}
```

- [ ] **Step 2: Add closed enums and the role matrix**

```java
public final class ContentProjectEnums {
    public enum CreationMode { SHORT_DRAMA, LONG_FORM, TVC;
        public static CreationMode parse(String value) {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        public String value() { return name().toLowerCase(Locale.ROOT); }
    }
    public enum SourceMode { AI_MANUAL, UPLOADED }
    public enum StoryboardIntent { NOT_DECIDED, SKIPPED, REQUESTED, IN_PROGRESS, COMPLETED }
    public enum ContentStatus { DRAFT, REVIEWING, NEEDS_REVISION, APPROVED, LOCKED }
    public enum ProductionStatus { NOT_STARTED, PREFLIGHT, CANVAS_READY, GENERATING, QUALITY_REVIEW, DELIVERABLE }
    public enum MarketStatus { PRIVATE, PENDING_REVIEW, LISTED, SOLD, DELISTED }
    public enum Role { OWNER, EDITOR, REVIEWER, PRODUCER, VIEWER;
        public boolean allows(Action action) { return action.allowedRoles.contains(this); }
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
        Action(Set<Role> roles) { this.allowedRoles = roles; }
    }
    private ContentProjectEnums() {}
}
```

- [ ] **Step 3: Add MyBatis entities matching every schema column**

`ContentProject` must use `@TableName("content_projects")`, `@TableId(type = IdType.AUTO)`, `@TableLogic` on `isDeleted`, and `@TableField(fill = ...)` on timestamps. Other entities use the same explicit table names and `IdType.AUTO`. Store payload fields as `String`; JSON serialization remains in services.

- [ ] **Step 4: Add one `BaseMapper<Entity>` interface per entity**

```java
@Mapper
public interface ContentProjectMapper extends BaseMapper<ContentProject> {}
```

Create equivalent mapper files for the other seven entities.

- [ ] **Step 5: Run domain tests and compile**

Run: `cd aicp-backend && mvn -Dtest=ContentProjectEnumsTest test`

Expected: PASS and no MyBatis entity compilation errors.

- [ ] **Step 6: Commit domain persistence**

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject aicp-backend/src/test/java/com/aicp/module/contentproject/domain
git commit -m "feat: add content project domain model"
```

### Task 4: Implement project creation, access, list, and resume APIs

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/ContentProjectRequests.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/ContentProjectViews.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectAccessService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContentProjectService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/OutboxService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentProjectController.java`
- Modify: `aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java`
- Modify: `aicp-backend/src/main/java/com/aicp/common/exception/GlobalExceptionHandler.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ContentProjectServiceTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ProjectAccessServiceTest.java`

- [ ] **Step 1: Write failing create/access tests**

```java
@ExtendWith(MockitoExtension.class)
class ContentProjectServiceTest {
    @Mock ContentProjectMapper projectMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock OutboxService outboxService;
    @InjectMocks ContentProjectService service;

    @Test void createAddsOwnerMembershipAndResumeDefaults() {
        var request = new CreateProjectRequest("测试短剧", "short_drama", "ai_manual", "人物林夏发现账本被篡改", "追更", "personal", null);
        var result = service.create(7L, request);
        assertThat(result.lastStageKey()).isEqualTo("story_seed");
        verify(memberMapper).insert(argThat(m -> m.getUserId().equals(7L) && m.getRole().equals("owner")));
    }
}
```

Access tests must cover Owner all actions, Editor content edits only, Reviewer approvals only, Producer production only, Viewer read only, and nonmember denial.

- [ ] **Step 2: Add validated requests and stable views**

```java
public record CreateProjectRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank String creationMode,
        @NotBlank String sourceMode,
        @Size(max = 20000) String startContent,
        @NotBlank @Size(max = 50) String contentGoal,
        @NotBlank String tenantType,
        Long tenantId) {}

public record UpdateProjectRequest(@Size(max = 200) String name, Integer revision) {}
public record ResumePositionRequest(String stageKey, String taskKey, Long contentUnitId, Integer revision) {}
```

Annotate every request and response record with `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` so Java uses camelCase while the HTTP contract remains snake_case. For `sourceMode=ai_manual`, reject blank `startContent` in the service. For `tenantType=personal`, force `tenantId=currentUserId`. For `tenantType=enterprise`, require `ent_admin` or `dept_head` in `SecurityUtil.getCurrentUserPermissions()` and require a positive `tenantId`.

- [ ] **Step 3: Implement role/action authorization**

`ProjectAccessService.require(projectId, userId, action)` loads `project_members`, parses the role, and throws `BizException(ErrorCode.PROJECT_ACCESS_DENIED)` when no matching member/action exists. Every read and write service method must call it; controllers never trust a user ID from the request.

- [ ] **Step 4: Implement transactional create/list/get/update/resume**

Create must insert project, Owner membership, initial parameter version, update `current_parameter_version_id`, and persist `content_project.created` through `OutboxService` in one transaction. List uses an `(updated_at,id)` cursor and returns at most 100 records. Update/resume uses a conditional mapper update with `where id=? and revision=?`, increments revision, and throws `EDIT_CONFLICT` when update count is zero.

Implement `OutboxService.append(...)` here so project creation compiles and persists the event in the same transaction. Task 7 expands its use to versions, dependencies, and status changes.

- [ ] **Step 5: Add controller routes**

```java
@RestController
@RequestMapping("/api/v1/content-projects")
@RequiredArgsConstructor
public class ContentProjectController {
    private final ContentProjectService projects;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectDetail>> create(@Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(projects.create(SecurityUtil.requireCurrentUserId(), request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectDetail> get(@PathVariable Long id) {
        return ApiResponse.success(projects.get(SecurityUtil.requireCurrentUserId(), id));
    }
}
```

Also add `GET /content-projects`, `PATCH /{id}`, and `PUT /{id}/resume-position`.

- [ ] **Step 6: Add project-member management**

Add `GET|POST /content-projects/{id}/members` and `PATCH|DELETE /content-projects/{id}/members/{memberId}`. Only Owner can mutate members. Reject removal or role downgrade of the last Owner. Member responses expose `user_id`, `role`, `created_at`, and no credential fields.

- [ ] **Step 7: Add V7.1 error codes**

Add `PROJECT_NOT_FOUND`, `PROJECT_ACCESS_DENIED`, `EDIT_CONFLICT`, `WORKFLOW_STAGE_LOCKED`, `ARTIFACT_LOCKED`, `DEPENDENCY_STALE`, and `IDEMPOTENCY_CONFLICT` with nonoverlapping 43xxx numeric codes. Map not-found to 404, denied to 403, and conflicts to 409.

- [ ] **Step 8: Run focused tests**

Run:

```bash
cd aicp-backend
mvn -Dtest=ContentProjectServiceTest,ProjectAccessServiceTest test
```

Expected: all project lifecycle, member management, last-Owner protection, and role matrix tests PASS.

- [ ] **Step 9: Commit project APIs**

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject aicp-backend/src/main/java/com/aicp/common/exception aicp-backend/src/test/java/com/aicp/module/contentproject
git commit -m "feat: add content project lifecycle APIs"
```

### Task 5: Implement immutable parameter versions and adaptive workflow

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectWorkflowService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ProjectWorkflowServiceTest.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContentProjectService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentProjectController.java`

- [ ] **Step 1: Write failing adaptive-path tests**

```java
@Test void uploadedProjectSkipsSatisfiedSeedAndStartsAtImportReview() {
    WorkflowView view = workflow.calculate(project("short_drama", "uploaded"),
            facts("story_seed", true, "characters", true, "structure", false));
    assertThat(view.currentStageKey()).isEqualTo("import_review");
}

@Test void skippedStoryboardDoesNotReduceCompletion() {
    WorkflowView view = workflow.calculate(lockedProjectWithStoryboardIntent("skipped"), Map.of());
    assertThat(view.progress()).isEqualTo(100);
    assertThat(view.stages().stream().filter(s -> s.key().equals("storyboard")).findFirst().orElseThrow().status())
            .isEqualTo("skipped");
}
```

- [ ] **Step 2: Implement immutable parameter append**

`POST /content-projects/{id}/parameter-versions` accepts the full payload, canonicalizes JSON with sorted keys, hashes SHA-256, calculates `max(version_no)+1`, inserts a new row, and updates only the project pointer. Never update an existing parameter row.

- [ ] **Step 3: Implement server-owned stage contracts**

Return these stage keys for M0: `story_seed`, `import_review` (uploaded only), `characters`, `synopsis`, `outline`, `content`, `review`, `destination`, `storyboard`. Each stage response contains `required`, `status`, `missingConditions`, `primaryAction`, and `route`. Source mode and confirmed facts determine whether a stage is completed, current, pending, optional, skipped, or locked.

- [ ] **Step 4: Implement storyboard intent without side effects**

`PUT /content-projects/{id}/storyboard-intent` accepts only `skipped` or `requested`. `requested` returns route `/content-projects/{id}/storyboard/setup`; neither value creates a generation job. `skipped` leaves content and production status unchanged.

- [ ] **Step 5: Add workflow routes**

Add `GET /content-projects/{id}/workflow`, `POST /{id}/parameter-versions`, and `PUT /{id}/storyboard-intent`.

- [ ] **Step 6: Verify workflow behavior**

Run: `cd aicp-backend && mvn -Dtest=ProjectWorkflowServiceTest test`

Expected: manual, uploaded, skipped-storyboard, and requested-storyboard paths PASS.

- [ ] **Step 7: Commit workflow contracts**

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject aicp-backend/src/test/java/com/aicp/module/contentproject/service/ProjectWorkflowServiceTest.java
git commit -m "feat: add adaptive project workflow"
```

### Task 6: Implement stable units, drafts, and named versions

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContentUnitService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentUnitController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ContentUnitServiceTest.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/ContentProjectRequests.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentProjectController.java`

- [ ] **Step 1: Write failing independence and conflict tests**

```java
@Test void episodeDraftsNeverShareContent() {
    ContentUnit first = service.createUnit(userId, projectId, "episode", 1, "第一集");
    ContentUnit second = service.createUnit(userId, projectId, "episode", 2, "第二集");
    service.saveDraft(userId, first.getId(), new SaveDraftRequest(0, "{\"blocks\":[\"A\"]}", "A"));
    assertThat(service.getDraft(userId, second.getId()).plainText()).isEmpty();
}

@Test void staleRevisionPreservesServerDraft() {
    assertThatThrownBy(() -> service.saveDraft(userId, unitId,
            new SaveDraftRequest(3, "{}", "local")))
            .isInstanceOf(BizException.class)
            .extracting("code").isEqualTo(ErrorCode.EDIT_CONFLICT.getCode());
}
```

- [ ] **Step 2: Create stable units**

Generate `stable_key` as `CU_` plus a UUID without dashes. `display_no` is mutable for reordering; stable keys and IDs never change. Reject duplicate `(project_id, unit_type, display_no)`.

- [ ] **Step 3: Implement autosave as revisioned draft state**

Use `content_units.current_version_id` only for named versions. Store the current draft as a `content_versions` row with status `draft` and source `manual_edit`; subsequent autosaves update that draft row only when `content_units.revision` matches, then increment revision. Do not create a visible version for every keystroke.

- [ ] **Step 4: Implement named-version creation**

`POST /content-units/{id}/versions` copies the latest draft into a new immutable row with the next `version_no`, status `draft|reviewing|approved|locked`, hash, and actor. Reject modification of locked rows; restore creates a new draft from the selected immutable version.

- [ ] **Step 5: Add unit routes**

`ContentProjectController` owns create/list/reorder under `/content-projects/{projectId}/content-units`. `ContentUnitController` owns `GET|PUT /api/v1/content-units/{id}/draft`, `GET|POST /api/v1/content-units/{id}/versions`, and `POST /api/v1/content-units/{id}/versions/{versionId}/restore`.

- [ ] **Step 6: Verify focused tests**

Run: `cd aicp-backend && mvn -Dtest=ContentUnitServiceTest test`

Expected: unit independence, stable IDs, optimistic conflicts, lock protection, and restore-as-new-draft PASS.

- [ ] **Step 7: Commit content units**

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject aicp-backend/src/test/java/com/aicp/module/contentproject/service/ContentUnitServiceTest.java
git commit -m "feat: add versioned content units"
```

### Task 7: Add status aggregation, dependency invalidation, and Outbox persistence

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContentStatusService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ArtifactDependencyService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/OutboxService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ContentStatusServiceTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ArtifactDependencyServiceTest.java`

- [ ] **Step 1: Write failing aggregation tests**

Cover the V7.1 precedence exactly: `needs_revision` > `reviewing` > `draft` > `approved` > `locked`. Exclude optional units, skipped storyboard stages, and units outside the active delivery scope.

```java
assertThat(status.aggregate(List.of(LOCKED, APPROVED))).isEqualTo(APPROVED);
assertThat(status.aggregate(List.of(LOCKED, NEEDS_REVISION))).isEqualTo(NEEDS_REVISION);
assertThat(status.aggregate(List.of(LOCKED, LOCKED))).isEqualTo(LOCKED);
```

- [ ] **Step 2: Implement dependency invalidation**

When a new source version is created, update dependencies whose stored `source_hash` differs to `needs_sync`; do not update or delete their target artifacts. Persist `dependency.stale` once per affected target.

- [ ] **Step 3: Implement transaction-bound Outbox writes**

```java
@Transactional(propagation = Propagation.MANDATORY)
public void append(String type, Long aggregateId, int revision, Object payload) {
    OutboxEvent event = new OutboxEvent();
    event.setEventId(UUID.randomUUID().toString());
    event.setAggregateType("content_project");
    event.setAggregateId(aggregateId);
    event.setAggregateRevision(revision);
    event.setEventType(type);
    event.setPayloadJson(toJson(payload));
    event.setStatus("pending");
    event.setOccurredAt(LocalDateTime.now());
    outboxMapper.insert(event);
}
```

- [ ] **Step 4: Trigger recalculation and events from version operations**

Named version creation, status changes, storyboard intent changes, and project creation append events in the same transaction. Add a scheduled reconciliation method that recalculates project content status from the active scope; do not publish externally in M0.

- [ ] **Step 5: Run focused tests**

Run:

```bash
cd aicp-backend
mvn -Dtest=ContentStatusServiceTest,ArtifactDependencyServiceTest test
```

Expected: precedence, exclusions, stale marking, and one-event-per-target tests PASS.

- [ ] **Step 6: Commit status and dependency behavior**

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject aicp-backend/src/test/java/com/aicp/module/contentproject/service
git commit -m "feat: add content status and dependency events"
```

### Task 8: Add Context Assembler and idempotent generation-job scaffold

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContextAssembler.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContentGenerationJobService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentGenerationJobController.java`
- Create: `aicp-backend/src/main/resources/schemas/content-generation-job-v1.schema.json`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ContextAssemblerTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ContentGenerationJobServiceTest.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentProjectController.java`

- [ ] **Step 1: Write a failing revised-version context test**

```java
@Test void snapshotUsesSelectedRevisedSynopsisInsteadOfInitialIdea() {
    ContextSnapshot snapshot = assembler.assemble(projectId,
            new GenerationJobRequest("outline_generate", "project", projectId,
                    Map.of("parameter", 31L, "synopsis", 82L), "加强冲突", "project_default"));
    assertThat(snapshot.selectedVersions()).containsEntry("synopsis", 82L);
    assertThat(snapshot.payload()).contains("用户修订梗概");
    assertThat(snapshot.payload()).doesNotContain("仅初始 idea");
}
```

- [ ] **Step 2: Implement project-owned version validation and snapshot assembly**

The assembler loads the selected parameter/content versions, verifies every ID belongs to the project, sorts the serialized keys, writes selected IDs and hashes, and returns immutable JSON plus SHA-256. Frontend requests never send raw trusted context.

- [ ] **Step 3: Implement idempotent job creation**

`POST /generation-jobs` requires `Idempotency-Key`. On an existing `(project_id,key)`, return the existing job when request hash matches; throw `IDEMPOTENCY_CONFLICT` when it differs. M0 leaves jobs in `pending`; M1 adds the executor.

Add `content-generation-job-v1.schema.json` with required envelope fields `schema_version`, `job_id`, `status`, and `result`; `status` is limited to `pending`, `processing`, `completed`, `partial_completed`, `failed`, and `cancelled`. `result` accepts an object in M0; M1 adds task-specific schemas before execution is enabled.

- [ ] **Step 4: Add create/get/cancel routes**

Implement these routes in `ContentGenerationJobController`, not `ContentProjectController`. Return `202 Accepted` with `job_id`, `status`, `estimated_credits`, `estimated_duration_sec`, and `poll_after_ms`. Cancel only `pending` jobs and record a job event.

- [ ] **Step 5: Run focused tests**

Run:

```bash
cd aicp-backend
mvn -Dtest=ContextAssemblerTest,ContentGenerationJobServiceTest test
```

Expected: revised-version selection, cross-project rejection, same-key replay, and conflicting-key rejection PASS.

- [ ] **Step 6: Commit generation contracts**

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject aicp-backend/src/test/java/com/aicp/module/contentproject/service
git commit -m "feat: add generation context contracts"
```

### Task 9: Backfill legacy scripts and keep rollback reads safe

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/LegacyProjectProjectionService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/LegacyProjectProjectionServiceTest.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/script/service/ScriptService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/script/controller/ScriptRepoController.java`

- [ ] **Step 1: Write failing idempotent backfill tests**

```java
@Test void runningBackfillTwiceCreatesOneProjectPerScript() {
    projection.backfill(ownerId);
    projection.backfill(ownerId);
    verify(projectMapper, times(1)).insert(argThat(p -> p.getLegacyScriptId().equals(11L)));
}
```

Also cover episode-to-unit creation, `source=uploaded` mapping, unclassified legacy mode, and a V7 project rendered into a read-only legacy summary.

- [ ] **Step 2: Implement idempotent backfill by `legacy_script_id`**

For each accessible legacy script without a project, create a project, owner member, parameter v1, stable units for episodes, and content version v1. Preserve legacy rows and IDs. Do not fabricate canvas snapshots.

- [ ] **Step 3: Implement the compatibility read projection**

Legacy list/get first return legacy scripts. Append V7-only projects as read-only projected script summaries with `compat_read_only=true`; do not write projected data back to `scripts`. Legacy update endpoints reject projected V7 IDs with a message directing clients to `/content-projects/{id}`.

- [ ] **Step 4: Add a dev/admin backfill endpoint guarded by permission**

Expose `POST /api/v1/content-projects/backfill-legacy` only to authenticated users for their own projects in dev, and only to `ent_admin` in non-dev profiles. Return counts for created projects, units, versions, and skipped records.

- [ ] **Step 5: Run compatibility tests**

Run: `cd aicp-backend && mvn -Dtest=LegacyProjectProjectionServiceTest test`

Expected: idempotency and projection tests PASS.

- [ ] **Step 6: Commit compatibility layer**

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject aicp-backend/src/main/java/com/aicp/module/script aicp-backend/src/test/java/com/aicp/module/contentproject/service/LegacyProjectProjectionServiceTest.java
git commit -m "feat: add legacy content project compatibility"
```

### Task 10: Add the frontend API and pure adaptive-path utility

**Files:**
- Create: `aicp-frontend/src/api/contentProject.js`
- Create: `aicp-frontend/src/views/content-project/utils/workflowPath.js`
- Create: `aicp-frontend/tests/content-project-workflow.test.js`

- [ ] **Step 1: Write failing pure workflow tests**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { currentStage, primaryAction } from '../src/views/content-project/utils/workflowPath.js'

test('uses the first current stage and ignores skipped storyboard', () => {
  const stages = [
    { key: 'content', status: 'completed' },
    { key: 'destination', status: 'current', primary_action: '选择去向' },
    { key: 'storyboard', status: 'skipped' }
  ]
  assert.equal(currentStage(stages).key, 'destination')
  assert.equal(primaryAction(stages), '选择去向')
})
```

- [ ] **Step 2: Run the test and verify missing-module failure**

Run: `node --test aicp-frontend/tests/content-project-workflow.test.js`

Expected: FAIL because `workflowPath.js` is absent.

- [ ] **Step 3: Implement pure selection helpers**

```js
export function currentStage(stages = []) {
  return stages.find(stage => stage.status === 'current') ||
    stages.find(stage => !['completed', 'skipped', 'optional'].includes(stage.status)) || null
}

export function primaryAction(stages = []) {
  return currentStage(stages)?.primary_action || '返回项目'
}
```

- [ ] **Step 4: Add the V7.1 API client**

```js
import request from './request'

export const contentProjectApi = {
  list: params => request.get('/content-projects', { params }),
  create: data => request.post('/content-projects', data),
  get: id => request.get(`/content-projects/${id}`),
  update: (id, data) => request.patch(`/content-projects/${id}`, data),
  workflow: id => request.get(`/content-projects/${id}/workflow`),
  saveResume: (id, data) => request.put(`/content-projects/${id}/resume-position`, data),
  addParameters: (id, data) => request.post(`/content-projects/${id}/parameter-versions`, data),
  setStoryboardIntent: (id, intent, sourceVersionId) => request.put(
    `/content-projects/${id}/storyboard-intent`,
    { intent, source_version_id: sourceVersionId }
  ),
  listUnits: id => request.get(`/content-projects/${id}/content-units`),
  getDraft: unitId => request.get(`/content-units/${unitId}/draft`),
  saveDraft: (unitId, data) => request.put(`/content-units/${unitId}/draft`, data)
}
```

- [ ] **Step 5: Verify the utility and build**

Run:

```bash
node --test aicp-frontend/tests/content-project-workflow.test.js
cd aicp-frontend && npm run build
```

Expected: workflow tests PASS and Vite build succeeds.

- [ ] **Step 6: Commit frontend contracts**

```bash
git add aicp-frontend/src/api/contentProject.js aicp-frontend/src/views/content-project/utils aicp-frontend/tests/content-project-workflow.test.js
git commit -m "feat: add content project frontend contracts"
```

### Task 11: Build the project list and minimal create flow

**Files:**
- Create: `aicp-frontend/src/views/content-project/ContentProjectList.vue`
- Create: `aicp-frontend/src/views/content-project/ContentProjectCreate.vue`
- Modify: `aicp-frontend/src/views/Warehouse.vue`
- Modify: `aicp-frontend/src/router/index.js`

- [ ] **Step 1: Add routes before changing the old warehouse entry**

```js
{
  path: 'content-projects',
  name: 'ContentProjects',
  component: () => import('@/views/content-project/ContentProjectList.vue'),
  meta: { title: '内容项目' }
},
{
  path: 'content-projects/new',
  name: 'ContentProjectCreate',
  component: () => import('@/views/content-project/ContentProjectCreate.vue'),
  meta: { title: '新建内容项目' }
},
{
  path: 'content-projects/:projectId/workspace',
  name: 'ContentProjectWorkspace',
  component: () => import('@/views/content-project/ContentProjectWorkspace.vue'),
  meta: { title: '流程化创作台' }
}
```

- [ ] **Step 2: Implement the project list states**

Render loading skeleton, empty state, error with retry, project cards, and cursor “加载更多”. Cards show mode, source, content/production/storyboard state, current stage, progress, current version, updated time, and one primary `继续创作` action. The action routes with the actual project ID; do not route to a blank `/script-gen`.

- [ ] **Step 3: Implement the four-field create page**

Require only mode, source, start content, and goal. Use explicit mode cards for short drama, long form, and TVC. M0 enables manual creation; the upload card is visibly disabled with its M2 milestone label, while legacy uploaded scripts enter through backfill. Submit creates the project and navigates to its workspace. Do not render platform, audience, length, or advanced fields on the first screen.

- [ ] **Step 4: Make Warehouse a compatibility redirect**

Replace duplicate repository markup with a route redirect or a small migration notice that immediately routes to `/content-projects`. Preserve `/warehouse` so bookmarks do not break.

- [ ] **Step 5: Build and manually inspect route imports**

Run: `cd aicp-frontend && npm run build`

Expected: build succeeds and emits chunks for all three content-project views.

- [ ] **Step 6: Commit list/create UI**

```bash
git add aicp-frontend/src/router/index.js aicp-frontend/src/views/Warehouse.vue aicp-frontend/src/views/content-project
git commit -m "feat: add content project entry flow"
```

### Task 12: Build the adaptive workspace shell and resume behavior

**Files:**
- Create: `aicp-frontend/src/views/content-project/ContentProjectWorkspace.vue`
- Create: `aicp-frontend/src/views/content-project/components/WorkflowRail.vue`
- Create: `aicp-frontend/src/views/content-project/components/ContextPanel.vue`
- Modify: `aicp-frontend/src/views/ScriptGen.vue`

- [ ] **Step 1: Implement the three-column shell**

The workspace header shows project, mode, current parameter version, autosave state, and current revision. Left rail shows completed/current/pending/optional/skipped/locked/risk states. Center renders exactly one current task and one primary button. Right panel shows selected versions, locked facts, impact range, and collapsed professional settings.

- [ ] **Step 2: Load project, workflow, units, and resume position together**

```js
const [projectRes, workflowRes, unitsRes] = await Promise.all([
  contentProjectApi.get(projectId.value),
  contentProjectApi.workflow(projectId.value),
  contentProjectApi.listUnits(projectId.value)
])
project.value = projectRes.data
workflow.value = workflowRes.data
units.value = unitsRes.data?.items || []
```

After load, select `last_content_unit_id` when present, otherwise the first current-stage unit. Save resume position after stage/unit navigation; debounce only position writes, not content autosaves.

- [ ] **Step 3: Implement optional storyboard choice**

At destination, show `完成并返回项目` as the primary action and `制作分镜` as secondary when the project goal is content delivery. Choosing skip calls `setStoryboardIntent(..., 'skipped')`; choosing now calls `requested` and shows route/estimate setup. Never call a storyboard generation API from this shell.

- [ ] **Step 4: Implement safe draft autosave**

Stop typing for two seconds, unit switch, route leave, and Cmd/Ctrl+S call `saveDraft` with the current revision. On 409, preserve local text and show a conflict panel with server revision, server draft ID, and local base revision. Do not replace local text automatically.

- [ ] **Step 5: Keep ScriptGen as a temporary compatibility entry**

When `projectId` exists, route to `/content-projects/{projectId}/workspace`. Without it, show a notice and route to `/content-projects/new`. Do not delete old step components until M1 migration completes.

- [ ] **Step 6: Build and run pure tests**

Run:

```bash
node --test aicp-frontend/tests/content-project-workflow.test.js
cd aicp-frontend && npm run build
```

Expected: workflow tests PASS and production build succeeds.

- [ ] **Step 7: Commit workspace shell**

```bash
git add aicp-frontend/src/views/content-project aicp-frontend/src/views/ScriptGen.vue
git commit -m "feat: add adaptive content workspace"
```

### Task 13: End-to-end verification and M0 handoff

**Files:**
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/ContentProjectM0IntegrationTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/OpenApiContractTest.java`
- Create: `docs/02-derived/script-creation-v7-m0-api-contract.md`
- Create: `docs/02-derived/openapi/script-creation-v7-m0.yaml`
- Modify: `docs/superpowers/specs/2026-06-29-script-creation-v7-prd.md` only if implementation exposes an approved-contract correction.

- [ ] **Step 1: Add the M0 integration scenario**

The test must authenticate a user, create a short-drama project, verify owner membership, add and role-check an Editor, append parameters, create two independent units, autosave each, create a named version, update resume position, skip storyboard, reload the project/workflow, and assert no storyboard or canvas row exists.

- [ ] **Step 2: Run all backend tests**

Run: `cd aicp-backend && mvn test`

Expected: all tests PASS with zero failures and zero errors.

- [ ] **Step 3: Run all focused frontend checks**

Run:

```bash
node --test aicp-frontend/tests/*.test.js
cd aicp-frontend && npm run build
```

Expected: all Node tests PASS and Vite build succeeds. Existing large-chunk warnings may remain; no new unresolved import or template error is allowed.

- [ ] **Step 4: Verify H2 startup and HTTP contracts**

Run backend in dev, authenticate with the existing dev login flow, and execute:

```text
POST /api/v1/content-projects
GET /api/v1/content-projects
GET /api/v1/content-projects/{id}
GET /api/v1/content-projects/{id}/workflow
PUT /api/v1/content-projects/{id}/storyboard-intent
PUT /api/v1/content-units/{unitId}/draft
```

Expected: create returns 201, reads return only accessible projects, skip-storyboard completes without a generation job, and stale revision returns HTTP 409.

- [ ] **Step 5: Browser-check the adaptive experience**

Verify desktop list/create/workspace, loading/empty/error/conflict/no-permission states, keyboard save, refresh/resume, uploaded-project stage skipping, high-contrast text, and no horizontal overflow. Confirm there is one visual primary action per screen.

- [ ] **Step 6: Write the machine-readable OpenAPI contract**

Create OpenAPI 3.1 paths and component schemas for projects, members, parameter versions, workflow, storyboard intent, content units, drafts, versions, generation jobs, cursors, the success envelope, and every M0 error response. Validate that every implemented controller route appears exactly once in the YAML.

Add `OpenApiContractTest` using the SnakeYAML version already brought by Spring Boot:

```java
@Test
void openApiContainsEveryM0Route() throws IOException {
    Map<String, Object> root = new Yaml().load(Files.readString(
            Path.of("../docs/02-derived/openapi/script-creation-v7-m0.yaml")));
    Map<String, Object> paths = (Map<String, Object>) root.get("paths");
    assertThat(paths.keySet()).contains(
            "/api/v1/content-projects",
            "/api/v1/content-projects/{id}",
            "/api/v1/content-projects/{id}/members",
            "/api/v1/content-projects/{id}/workflow",
            "/api/v1/content-projects/{id}/storyboard-intent",
            "/api/v1/content-units/{id}/draft",
            "/api/v1/content-units/{id}/versions",
            "/api/v1/generation-jobs");
}
```

- [ ] **Step 7: Write the M0 API handoff**

Document the implemented endpoints, request/response examples, enum values, error codes, table ownership, compatibility behavior, OpenAPI/JSON Schema locations, known M1 extension points, and exact verification commands in `script-creation-v7-m0-api-contract.md`.

- [ ] **Step 8: Final commit**

```bash
git add aicp-backend aicp-frontend docs/02-derived/script-creation-v7-m0-api-contract.md docs/02-derived/openapi/script-creation-v7-m0.yaml
git commit -m "feat: complete script creation V7 M0 foundation"
```

---

## V7.1 requirement mapping

| V7.1 requirement | Implemented by |
|---|---|
| Content-project root object and personal/enterprise tenant boundary | Tasks 2–4 |
| Owner/Editor/Reviewer/Producer/Viewer permission matrix | Task 4 |
| Immutable parameter versions | Task 5 |
| Adaptive guided stages, upload-stage skipping, resume position | Tasks 5 and 12 |
| Stable content units and independent episode/chapter drafts | Task 6 |
| Autosave revision conflict and restore-as-new-draft | Tasks 6 and 12 |
| Optional storyboard with no silent generation or charge | Tasks 5, 12, and 13 |
| Project content-status aggregation | Task 7 |
| Dependency stale detection without downstream overwrite | Task 7 |
| Revised-version Context Assembler and idempotent generation jobs | Task 8 |
| Legacy backfill and rollback-safe read projection | Task 9 |
| Project list, four-field entry, adaptive workspace | Tasks 10–12 |
| OpenAPI, JSON Schema, integration test, and handoff | Tasks 8 and 13 |

---

## M0 exit checklist

- Existing scripts remain readable and are not destructively migrated.
- New projects can be created, listed, resumed, and access-checked.
- Parameter versions and named content versions are immutable.
- Autosave conflicts preserve both server and local data.
- Two content units never share one draft field.
- Generation context references selected revised versions.
- Optional storyboard never blocks content completion or creates unconfirmed costs.
- Project content status follows the V7.1 aggregation precedence.
- Outbox events are transaction-bound and idempotency keys prevent duplicate jobs.
- Backend tests, frontend Node tests, and frontend production build pass.
