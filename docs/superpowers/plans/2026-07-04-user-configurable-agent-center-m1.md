# User-Configurable Agent Center M1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a production-usable Agent configuration foundation where users create one of four Blueprint-backed Agents, edit and validate drafts, run a test, publish immutable versions, bind user/project defaults, and inspect the resolved configuration.

**Architecture:** Add an independent configuration domain under the existing Spring Boot `agent` module. A server-side resolver composes Blueprint, published version, binding, and temporary overrides; the frontend never sends a final system prompt. The Vue configuration center uses pure view-model helpers for state transitions and calls typed REST endpoints through the existing Axios wrapper.

**Tech Stack:** Java 17, Spring Boot 3.2, MyBatis-Plus, H2/MySQL, JUnit 5/Mockito/AssertJ, Vue 3, Vue Router, Element Plus, Node test runner, Vite.

---

## Scope boundary and follow-up plans

This plan implements M1 only. It intentionally does not replace prompts in the production content services yet. After M1 passes, create and execute these separate plans in order:

1. `script-hook-agent-integration`: connect `ScriptGenService`, content generation, hooks, review, Patch preview, and content-version provenance.
2. `storyboard-director-agent-integration`: connect A/B/C storyboard generation, director review, and storyboard-version provenance.
3. `agent-prompt-migration-rollout`: migrate four-role `prompt_templates`, remove the old role entry, add audits, and run gray-release checks.

M1 is independently usable: a user can configure, test, publish, bind, and resolve Agents before business generators consume them. The M1 “end-user deliverable” is the full Agent 配置中心 UI plus the `resolve-preview` API — an authenticated user can create an Agent from a Blueprint, edit and validate a draft, run a test against the real model, publish, bind it as their default, and inspect the resolved configuration. What M1 intentionally does NOT do is feed that resolved configuration into `ScriptGenService`, `ContentHookService`, or any other business service; those integrations are delivered by the follow-up plans below.

## File structure

### Backend files to create

- `aicp-backend/src/main/resources/db/migration/V7__agent_configuration_center.sql`: MySQL migration for six configuration tables and four Blueprint seeds.
- `aicp-backend/src/main/java/com/aicp/module/agent/domain/AgentConfigEnums.java`: role, version, lifecycle, scope, and test-run enums.
- `aicp-backend/src/main/java/com/aicp/module/agent/entity/AgentBlueprint.java`
- `aicp-backend/src/main/java/com/aicp/module/agent/entity/UserAgentDefinition.java`
- `aicp-backend/src/main/java/com/aicp/module/agent/entity/AgentVersion.java`
- `aicp-backend/src/main/java/com/aicp/module/agent/entity/AgentBinding.java`
- `aicp-backend/src/main/java/com/aicp/module/agent/entity/AgentTestRun.java`
- `aicp-backend/src/main/java/com/aicp/module/agent/entity/AgentExecutionSnapshot.java`
- Matching Mapper interfaces under `aicp-backend/src/main/java/com/aicp/module/agent/mapper/`.
- `aicp-backend/src/main/java/com/aicp/module/agent/dto/AgentConfigRequests.java`: validated request records.
- `aicp-backend/src/main/java/com/aicp/module/agent/dto/AgentConfigViews.java`: stable response records.
- `aicp-backend/src/main/java/com/aicp/module/agent/service/AgentBlueprintService.java`: Blueprint queries and seed-contract checks.
- `aicp-backend/src/main/java/com/aicp/module/agent/service/UserAgentDefinitionService.java`: ownership, CRUD, copy, and archive.
- `aicp-backend/src/main/java/com/aicp/module/agent/service/AgentVersionService.java`: draft state machine, validation, publish, and activate.
- `aicp-backend/src/main/java/com/aicp/module/agent/service/AgentBindingService.java`: user/project default bindings and authorization.
- `aicp-backend/src/main/java/com/aicp/module/agent/service/AgentPromptCompiler.java`: locked/user/runtime composition and variable validation.
- `aicp-backend/src/main/java/com/aicp/module/agent/service/AgentConfigResolver.java`: precedence resolution and immutable result.
- `aicp-backend/src/main/java/com/aicp/module/agent/service/AgentTestRunService.java`: test execution through `AiRouter`.
- `aicp-backend/src/main/java/com/aicp/module/agent/service/AgentExecutionSnapshotService.java`: persist resolved configuration.
- `aicp-backend/src/main/java/com/aicp/module/agent/controller/AgentConfigController.java`: REST protocol only.
- `aicp-backend/src/main/java/com/aicp/module/agent/controller/ProjectAgentBindingController.java`: project-scoped binding protocol.

### Backend files to modify

- `aicp-backend/src/main/resources/db/schema-h2.sql`: H2 schema and Blueprint seeds used by dev/tests.
- `aicp-backend/src/main/resources/db/schema-mysql.sql`: canonical MySQL bootstrap schema.
- `aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java`: stable 49xxx configuration errors.
- `aicp-backend/src/main/java/com/aicp/module/contentproject/domain/ContentProjectEnums.java`: add the explicit project Agent-management permission.

### Backend tests to create

- `aicp-backend/src/test/java/com/aicp/module/agent/schema/AgentConfigurationSchemaTest.java`
- `aicp-backend/src/test/java/com/aicp/module/agent/service/AgentBlueprintServiceTest.java`
- `aicp-backend/src/test/java/com/aicp/module/agent/service/UserAgentDefinitionServiceTest.java`
- `aicp-backend/src/test/java/com/aicp/module/agent/service/AgentVersionServiceTest.java`
- `aicp-backend/src/test/java/com/aicp/module/agent/service/AgentConfigResolverTest.java`
- `aicp-backend/src/test/java/com/aicp/module/agent/service/AgentTestRunServiceTest.java`

### Frontend files to create

- `aicp-frontend/src/utils/agentConfigHelpers.js`: pure mapping, filtering, inheritance labels, and wizard state — shared by config center and future business-page lightweight entries (design §7.4).
- `aicp-frontend/src/views/agent-config/useAgentConfig.js`: API-backed orchestration composable.
- `aicp-frontend/src/views/agent-config/AgentConfigCenter.vue`: three-column configuration center.
- `aicp-frontend/src/views/agent-config/components/AgentDefinitionList.vue`
- `aicp-frontend/src/views/agent-config/components/AgentEditorPanel.vue`（含项目绑定管理 tab）
- `aicp-frontend/src/views/agent-config/components/CreateAgentWizard.vue`
- `aicp-frontend/src/views/agent-config/components/AgentTestRunPanel.vue`
- `aicp-frontend/src/views/agent-config/components/AgentVersionPanel.vue`
- `aicp-frontend/tests/agent-config-state.test.js`

### Frontend files to modify

- `aicp-frontend/src/api/agent.js`: configuration endpoints.
- `aicp-frontend/src/router/index.js`: `/agent-config` route.
- `aicp-frontend/src/components/Sidebar.vue`: “Agent 配置” navigation item.
- `aicp-frontend/package.json`: add an explicit `test` script using the existing Node test runner.

## Task 1: Add schema, seeds, and stable errors

**Files:**
- Create: `aicp-backend/src/test/java/com/aicp/module/agent/schema/AgentConfigurationSchemaTest.java`
- Create: `aicp-backend/src/main/resources/db/migration/V7__agent_configuration_center.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Modify: `aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java`

- [ ] **Step 1: Write the failing schema test**

```java
@SpringBootTest
@ActiveProfiles("dev")
class AgentConfigurationSchemaTest {
    @Autowired JdbcTemplate jdbc;

    @Test void sixTablesAndFourBlueprintsExist() {
        for (String table : List.of("agent_blueprints", "user_agent_definitions",
                "agent_versions", "agent_bindings", "agent_test_runs",
                "agent_execution_snapshots")) {
            Integer count = jdbc.queryForObject(
                    "select count(*) from information_schema.tables where table_name = ?",
                    Integer.class, table.toUpperCase());
            assertThat(count).isEqualTo(1);
        }
        Integer roles = jdbc.queryForObject(
                "select count(*) from agent_blueprints where status = 'ACTIVE'", Integer.class);
        assertThat(roles).isEqualTo(4);
    }

    @Test void bindingScopeAndRoleAreUnique() {
        jdbc.update("insert into agent_bindings(uuid,scope_type,scope_id,role_type,user_agent_id,agent_version_id,created_by) values ('b1','USER',1,'HOOK',1,1,1)");
        assertThatThrownBy(() -> jdbc.update("insert into agent_bindings(uuid,scope_type,scope_id,role_type,user_agent_id,agent_version_id,created_by) values ('b2','USER',1,'HOOK',1,1,1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

- [ ] **Step 2: Run the schema test and verify failure**

Run: `cd aicp-backend && mvn -Dtest=AgentConfigurationSchemaTest test`

Expected: FAIL because `agent_blueprints` does not exist.

- [ ] **Step 3: Add the six tables and four deterministic Blueprint seeds**

Use the field set and unique constraints from design sections 11.1–11.6. Seed UUIDs must be stable:

```sql
INSERT INTO agent_blueprints
(uuid, role_type, name, parameter_schema_json, default_parameters_json,
 locked_system_prompt, editable_prompt_template, input_schema_json,
 output_schema_json, allowed_tools_json, context_policy_json,
 model_policy_json, blueprint_version, status)
VALUES
('bp-hook-v1','HOOK','钩子 Agent','{"type":"object"}','{}','平台锁定：仅生成钩子结构。','{{user_method}}','{"type":"object"}','{"type":"object"}','[]','{}','{"default_model":"deepseek-v3"}',1,'ACTIVE'),
('bp-screenwriter-v1','SCREENWRITER','编剧 Agent','{"type":"object"}','{}','平台锁定：仅执行编剧任务。','{{user_method}}','{"type":"object"}','{"type":"object"}','[]','{}','{"default_model":"deepseek-v3"}',1,'ACTIVE'),
('bp-storyboard-v1','STORYBOARD','分镜 Agent','{"type":"object"}','{}','平台锁定：输出专业分镜结构。','{{user_method}}','{"type":"object"}','{"type":"object"}','[]','{}','{"default_model":"deepseek-v3"}',1,'ACTIVE'),
('bp-director-v1','DIRECTOR','导演 Agent','{"type":"object"}','{}','平台锁定：输出导演审核结构。','{{user_method}}','{"type":"object"}','{"type":"object"}','[]','{}','{"default_model":"deepseek-v3"}',1,'ACTIVE');
```

Add equivalent H2 `MERGE`/`WHERE NOT EXISTS` seeds and MySQL `INSERT IGNORE` seeds so repeated bootstrap is idempotent.

Replace the abbreviated `{\"type\":\"object\"}` parameter schemas in the illustrative SQL above with these required Blueprint properties:

- `HOOK`: `opening_seconds`, `hook_density`, `reversal_strength`, `closing_hook_strength`, `minimum_score`.
- `SCREENWRITER`: `revision_mode`, `target_duration_seconds`, `dialogue_density`, `conflict_pace`, `character_consistency`.
- `STORYBOARD`: `tier`, `average_shot_seconds`, `shot_density`, `camera_complexity`, `continuity_level`, `production_cost_mode`.
- `DIRECTOR`: `visual_style`, `pacing_mode`, `feasibility_level`, `budget_mode`, `approval_threshold`, `output_mode`.

Each JSON Schema must set `additionalProperties` to `false`, provide defaults, and constrain numeric ranges and enum values so temporary overrides can be validated server-side.

- [ ] **Step 4: Add 49xxx errors**

```java
AGENT_BLUEPRINT_NOT_FOUND(49020, "Agent基础框架不存在"),
AGENT_DEFINITION_NOT_FOUND(49021, "Agent不存在"),
AGENT_VERSION_NOT_FOUND(49022, "Agent版本不存在"),
AGENT_CONFIG_ACCESS_DENIED(49023, "无Agent配置权限"),
AGENT_CONFIG_INVALID(49024, "Agent配置校验失败"),
AGENT_VERSION_STATE_CONFLICT(49025, "Agent版本状态冲突"),
AGENT_BINDING_CONFLICT(49026, "Agent绑定版本冲突"),
AGENT_TEST_RUN_REQUIRED(49027, "发布前必须完成成功试跑");
```

- [ ] **Step 5: Run schema and existing schema tests**

Run: `cd aicp-backend && mvn -Dtest=AgentConfigurationSchemaTest,ContentProjectSchemaTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/main/resources/db aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java aicp-backend/src/test/java/com/aicp/module/agent/schema
git commit -m "feat: add agent configuration schema"
```

## Task 2: Add domain types, entities, mappers, and DTOs

**Files:**
- Create: all backend domain, entity, mapper, and DTO files listed in File structure.
- Test: `aicp-backend/src/test/java/com/aicp/module/agent/service/AgentBlueprintServiceTest.java`

- [ ] **Step 1: Write a failing Blueprint mapping test**

```java
@ExtendWith(MockitoExtension.class)
class AgentBlueprintServiceTest {
    @Mock AgentBlueprintMapper mapper;
    @InjectMocks AgentBlueprintService service;

    @Test void listsOnlyActiveBlueprintsInRoleOrder() {
        AgentBlueprint hook = blueprint(1L, "HOOK", "bp-hook-v1");
        when(mapper.selectList(any())).thenReturn(List.of(hook));
        assertThat(service.listActive()).extracting(AgentConfigViews.BlueprintView::roleType)
                .containsExactly("HOOK");
    }
}
```

- [ ] **Step 2: Run the test and verify compile failure**

Run: `cd aicp-backend && mvn -Dtest=AgentBlueprintServiceTest test`

Expected: FAIL to compile because the domain classes do not exist.

- [ ] **Step 3: Add enums and focused entity classes**

```java
public final class AgentConfigEnums {
    public enum RoleType { HOOK, SCREENWRITER, STORYBOARD, DIRECTOR }
    public enum VersionStatus { DRAFT, PUBLISHED, ARCHIVED }
    public enum LifecycleStatus { ACTIVE, ARCHIVED }
    public enum ScopeType { USER, PROJECT }
    public enum TestRunStatus { PENDING, RUNNING, SUCCEEDED, FAILED }
    private AgentConfigEnums() {}
}
```

Each entity maps one table and uses `@TableId(type = IdType.AUTO)`. Store JSON as `String` in M1, matching current project conventions. Add Mapper interfaces extending `BaseMapper<T>`.

- [ ] **Step 4: Add request and view records**

```java
public record CreateDefinitionRequest(
        @NotBlank String blueprintId,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description) {}

public record UpdateDraftRequest(
        @NotNull Integer rowVersion,
        @NotNull Map<String,Object> parameters,
        @NotBlank @Size(max = 16000) String editablePrompt,
        List<Map<String,Object>> examples,
        Map<String,Object> modelPolicy) {}

public record BlueprintView(String id, String roleType, String name,
        Map<String,Object> parameterSchema, Map<String,Object> defaults,
        int blueprintVersion) {}
```

Keep JSON parsing in one DTO mapper/helper rather than controllers.

- [ ] **Step 5: Implement `AgentBlueprintService.listActive()` and rerun test**

```java
public List<BlueprintView> listActive() {
    return mapper.selectList(new LambdaQueryWrapper<AgentBlueprint>()
            .eq(AgentBlueprint::getStatus, "ACTIVE")
            .orderByAsc(AgentBlueprint::getId))
            .stream().map(viewMapper::blueprint).toList();
}
```

Run: `cd aicp-backend && mvn -Dtest=AgentBlueprintServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/agent aicp-backend/src/test/java/com/aicp/module/agent/service/AgentBlueprintServiceTest.java
git commit -m "feat: add agent configuration domain model"
```

## Task 3: Implement user Agent creation, ownership, copy, and archive

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/agent/service/UserAgentDefinitionService.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/agent/service/UserAgentDefinitionServiceTest.java`

- [ ] **Step 1: Write failing ownership and creation tests**

```java
@Test void createMakesDefinitionAndFirstDraft() {
    when(blueprintMapper.selectOne(any())).thenReturn(activeBlueprint());
    doAnswer(i -> { ((UserAgentDefinition)i.getArgument(0)).setId(10L); return 1; })
            .when(definitionMapper).insert(any());
    DefinitionView view = service.create(7L,
            new CreateDefinitionRequest("bp-hook-v1", "女频复仇强钩子", "前三秒强冲突"));
    assertThat(view.name()).isEqualTo("女频复仇强钩子");
    verify(versionMapper).insert(argThat(v -> v.getVersionNo() == 1
            && "DRAFT".equals(v.getStatus())));
}

@Test void anotherUserCannotArchive() {
    when(definitionMapper.selectOne(any())).thenReturn(definitionOwnedBy(8L));
    assertThatThrownBy(() -> service.archive(7L, "agent-1"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("权限");
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd aicp-backend && mvn -Dtest=UserAgentDefinitionServiceTest test`

Expected: FAIL because the service does not exist.

- [ ] **Step 3: Implement create/list/get/copy/archive with owner filters**

```java
@Transactional
public DefinitionView create(Long userId, CreateDefinitionRequest request) {
    AgentBlueprint blueprint = blueprints.requireActive(request.blueprintId());
    UserAgentDefinition definition = new UserAgentDefinition();
    definition.setUuid("agent_" + UUID.randomUUID().toString().replace("-", ""));
    definition.setBlueprintId(blueprint.getId());
    definition.setOwnerUserId(userId);
    definition.setName(request.name());
    definition.setDescription(request.description());
    definition.setVisibility("PRIVATE");
    definition.setLifecycleStatus("ACTIVE");
    definition.setRowVersion(0);
    definitionMapper.insert(definition);
    versions.createInitialDraft(definition, blueprint, userId);
    return views.definition(definition, blueprint);
}
```

All read/update queries must include `owner_user_id = userId`; never load by UUID and authorize afterward when a filtered query can do both.

- [ ] **Step 4: Run tests**

Run: `cd aicp-backend && mvn -Dtest=UserAgentDefinitionServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/agent/service/UserAgentDefinitionService.java aicp-backend/src/test/java/com/aicp/module/agent/service/UserAgentDefinitionServiceTest.java
git commit -m "feat: add user agent definition lifecycle"
```

## Task 4: Implement draft validation, successful-test gate, publish, and activate

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/agent/service/AgentPromptCompiler.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/agent/service/AgentVersionService.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/agent/service/AgentVersionServiceTest.java`

- [ ] **Step 1: Write failing state-machine tests**

```java
@Test void publishedVersionCannotBeEdited() {
    when(versionMapper.selectOne(any())).thenReturn(version("PUBLISHED", 2));
    assertThatThrownBy(() -> service.updateDraft(7L, "ver-1", update(2)))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("状态冲突");
}

@Test void publishRequiresSuccessfulTestRun() {
    when(versionMapper.selectOne(any())).thenReturn(version("DRAFT", 2));
    when(testRunMapper.selectCount(any())).thenReturn(0L);
    assertThatThrownBy(() -> service.publish(7L, "ver-1", 2, "调高钩子强度"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("成功试跑");
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd aicp-backend && mvn -Dtest=AgentVersionServiceTest test`

Expected: FAIL because draft validation and publishing are absent.

- [ ] **Step 3: Implement compiler validation**

```java
public CompiledPrompt compile(AgentBlueprint blueprint, AgentVersion version,
                              Map<String,Object> runtimeContext) {
    Set<String> allowed = Set.of("user_method", "project_context", "task_input");
    Set<String> used = variablePattern.matcher(version.getEditablePrompt()).results()
            .map(m -> m.group(1)).collect(Collectors.toSet());
    if (!allowed.containsAll(used)) {
        Set<String> illegal = new LinkedHashSet<>(used);
        illegal.removeAll(allowed);
        throw new BizException(ErrorCode.AGENT_CONFIG_INVALID,
                "包含未声明变量: " + illegal);
    }
    String finalPrompt = blueprint.getLockedSystemPrompt() + "\n\n"
            + version.getEditablePrompt() + "\n\n"
            + runtimeContext.getOrDefault("project_context", "");
    return new CompiledPrompt(finalPrompt, sha256(finalPrompt));
}
```

- [ ] **Step 4: Implement optimistic update and publish transaction**

Use `UPDATE ... WHERE id = ? AND row_version = ? AND status = 'DRAFT'`. On zero updated rows throw `AGENT_VERSION_STATE_CONFLICT`. Publishing archives no history; it sets the draft to `PUBLISHED` and updates `user_agent_definitions.current_published_version_id` in the same transaction.

- [ ] **Step 5: Run tests**

Run: `cd aicp-backend && mvn -Dtest=AgentVersionServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/agent/service/AgentPromptCompiler.java aicp-backend/src/main/java/com/aicp/module/agent/service/AgentVersionService.java aicp-backend/src/test/java/com/aicp/module/agent/service/AgentVersionServiceTest.java
git commit -m "feat: add agent version publishing"
```

## Task 5: Implement bindings, precedence resolution, and immutable snapshots

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/agent/service/AgentBindingService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/agent/service/AgentConfigResolver.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/agent/service/AgentExecutionSnapshotService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/domain/ContentProjectEnums.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/agent/service/AgentConfigResolverTest.java`

- [ ] **Step 1: Write precedence and permission tests**

```java
@Test void projectBindingWinsOverUserBinding() {
    when(bindingMapper.selectOne(argThat(q -> queryTargets(q, "PROJECT"))))
            .thenReturn(projectBinding(22L));
    ResolvedAgentConfig result = resolver.resolve(7L, 100L, RoleType.HOOK, Map.of());
    assertThat(result.versionId()).isEqualTo(22L);
    assertThat(result.bindingSource()).isEqualTo("PROJECT");
}

@Test void systemDefaultIsUsedOnlyWhenNoExplicitBindingExists() {
    when(bindingMapper.selectOne(any())).thenReturn(null);
    when(blueprintMapper.selectOne(any())).thenReturn(defaultHookBlueprint());
    ResolvedAgentConfig result = resolver.resolve(7L, 100L, RoleType.HOOK, Map.of());
    assertThat(result.bindingSource()).isEqualTo("SYSTEM");
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd aicp-backend && mvn -Dtest=AgentConfigResolverTest test`

Expected: FAIL because resolver services do not exist.

- [ ] **Step 3: Implement binding authorization**

Add a project permission that represents the agreed “项目管理员/导演” rule:

```java
MANAGE_AGENT_CONFIG(EnumSet.of(Role.OWNER, Role.REVIEWER))
```

```java
public BindingView bindProject(Long userId, Long projectId, RoleType role,
                               BindVersionRequest request) {
    access.require(projectId, userId, Action.MANAGE_AGENT_CONFIG);
    AgentVersion version = versions.requirePublishedAndRole(request.versionId(), role);
    return upsert("PROJECT", projectId, role.name(), version, userId);
}
```

The binding upsert uses `INSERT ... ON DUPLICATE KEY UPDATE` with a `row_version` check in the `WHERE` clause. On zero updated rows, throw `AGENT_BINDING_CONFLICT` — another admin changed the binding concurrently.

- [ ] **Step 3b: Handle edge cases**

- When an Agent is archived while still bound to a project, the resolver fails with `AGENT_VERSION_STATE_CONFLICT` — explicit bindings must not silently fall back.
- User deletion of their own binding (via `DELETE /api/v1/agent/user-bindings/{roleType}`) is allowed; project binding deletion requires `MANAGE_AGENT_CONFIG`.
- A `GET /api/v1/projects/{projectId}/agent-bindings` returns all four role bindings (or empty if none set), so the project settings page can display the full picture.

- [ ] **Step 4: Implement strict resolver precedence**

```java
public ResolvedAgentConfig resolve(Long userId, Long projectId, RoleType role,
                                   Map<String,Object> overrides) {
    access.require(projectId, userId, Action.VIEW);
    AgentBinding binding = findProject(projectId, role)
            .or(() -> findUser(userId, role)).orElse(null);
    if (binding != null) return resolveExplicit(binding, overrides);
    return resolveSystemDefault(role, overrides);
}
```

`resolveExplicit` must throw if its version is missing/archived/incompatible. It must not fall back to another binding.

- [ ] **Step 5: Persist and reload an immutable snapshot**

```java
@Transactional
public AgentExecutionSnapshot freeze(ResolvedAgentConfig config, SnapshotCommand command) {
    AgentExecutionSnapshot snapshot = new AgentExecutionSnapshot();
    snapshot.setUuid("ags_" + UUID.randomUUID().toString().replace("-", ""));
    snapshot.setAgentVersionId(config.versionId());
    snapshot.setResolvedParametersJson(json.write(config.parameters()));
    snapshot.setResolvedPrompt(config.prompt());
    snapshot.setPromptHash(config.promptHash());
    snapshot.setProjectId(command.projectId());
    snapshot.setBusinessTaskType(command.taskType());
    snapshot.setCreatedBy(command.userId());
    mapper.insert(snapshot);
    return snapshot;
}
```

- [ ] **Step 6: Run tests**

Run: `cd aicp-backend && mvn -Dtest=AgentConfigResolverTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/agent/service/AgentBindingService.java aicp-backend/src/main/java/com/aicp/module/agent/service/AgentConfigResolver.java aicp-backend/src/main/java/com/aicp/module/agent/service/AgentExecutionSnapshotService.java aicp-backend/src/test/java/com/aicp/module/agent/service/AgentConfigResolverTest.java
git commit -m "feat: resolve and snapshot agent configurations"
```

## Task 6: Implement test runs through AiRouter and expose REST APIs

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/agent/service/AgentTestRunService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/agent/controller/AgentConfigController.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/agent/service/AgentTestRunServiceTest.java`

- [ ] **Step 1: Write failing successful/failed test-run tests**

```java
@Test void successfulRunStoresSucceededStatus() {
    when(aiRouter.chatCompletion(any())).thenReturn(Map.of("choices", List.of(
            Map.of("message", Map.of("content", "{\"score\":88}")))));
    TestRunView view = service.run(7L, "ver-1", new TestRunRequest(
            Map.of("task_input", "测试剧本"), null));
    assertThat(view.status()).isEqualTo("SUCCEEDED");
    verify(testRunMapper).updateById(argThat(r -> "SUCCEEDED".equals(r.getStatus())));
}

@Test void upstreamFailureStoresFailedStatusAndRethrows() {
    when(aiRouter.chatCompletion(any())).thenThrow(new RuntimeException("timeout"));
    assertThatThrownBy(() -> service.run(7L, "ver-1", request()))
            .isInstanceOf(BizException.class);
    verify(testRunMapper).updateById(argThat(r -> "FAILED".equals(r.getStatus())));
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd aicp-backend && mvn -Dtest=AgentTestRunServiceTest test`

Expected: FAIL because the service does not exist.

- [ ] **Step 3: Implement synchronous M1 test runs**

`AgentTestRunService` depends on the existing `com.aicp.common.ai.AiRouter` (the same router used by `ScriptGenService` and other generation services). Create the test-run row as `RUNNING`, call `aiRouter.chatCompletion(messages, modelPolicy)` with the compiled prompt, validate that returned text is nonblank JSON that satisfies the Blueprint `output_schema_json`, then set `SUCCEEDED` or `FAILED`. If the call throws or times out, store `FAILED` with the error details and rethrow as `BizException`. Never mark a failed/invalid-output run successful.

`AiRouter` timeout is 60s for test runs; a timed-out run stores `FAILED` with `error_code = 'TIMEOUT'`.

- [ ] **Step 4: Add thin validated controller methods**

```java
@PostMapping("/definitions")
public ApiResponse<DefinitionView> create(@Valid @RequestBody CreateDefinitionRequest request) {
    return ApiResponse.success(definitions.create(SecurityUtil.requireCurrentUserId(), request));
}

@PostMapping("/versions/{versionId}/test-runs")
public ApiResponse<TestRunView> test(@PathVariable String versionId,
        @Valid @RequestBody TestRunRequest request) {
    return ApiResponse.success(testRuns.run(SecurityUtil.requireCurrentUserId(), versionId, request));
}

@PutMapping("/projects/{projectId}/bindings/{role}")
public ApiResponse<BindingView> bindProject(@PathVariable Long projectId,
        @PathVariable RoleType role, @Valid @RequestBody BindVersionRequest request) {
    return ApiResponse.success(bindings.bindProject(
            SecurityUtil.requireCurrentUserId(), projectId, role, request));
}
```

Use these exact controller bases and cover every M1 endpoint in controller methods:

- `AgentConfigController` at `/api/v1/agent`: Blueprint list/detail, definition CRUD/copy/archive, draft list/create/update/validate/test/publish/activate, versions list, test-run detail, user bindings CRUD, resolve preview, and snapshot detail.
- `ProjectAgentBindingController` at `/api/v1/projects`: project binding list/put/delete at `/{projectId}/agent-bindings[/{role}]`.

Full M1 endpoint inventory (all implemented in this task):

| Method | Path | Handler |
|---|---|---|
| `GET` | `/api/v1/agent/blueprints` | Blueprint list |
| `GET` | `/api/v1/agent/blueprints/{id}` | Blueprint detail |
| `POST` | `/api/v1/agent/definitions` | Create definition |
| `GET` | `/api/v1/agent/definitions` | List user definitions |
| `GET` | `/api/v1/agent/definitions/{id}` | Definition detail |
| `PATCH` | `/api/v1/agent/definitions/{id}` | Update metadata |
| `POST` | `/api/v1/agent/definitions/{id}/copies` | Copy definition |
| `POST` | `/api/v1/agent/definitions/{id}/archive` | Archive definition |
| `GET` | `/api/v1/agent/definitions/{id}/versions` | List versions |
| `POST` | `/api/v1/agent/definitions/{id}/drafts` | Create draft |
| `GET` | `/api/v1/agent/versions/{versionId}` | Version detail |
| `PUT` | `/api/v1/agent/versions/{versionId}` | Update draft |
| `POST` | `/api/v1/agent/versions/{versionId}/validate` | Validate draft |
| `POST` | `/api/v1/agent/versions/{versionId}/test-runs` | Run test |
| `GET` | `/api/v1/agent/test-runs/{id}` | Test run detail |
| `POST` | `/api/v1/agent/versions/{versionId}/publish` | Publish version |
| `POST` | `/api/v1/agent/versions/{versionId}/activate` | Activate/rollback |
| `GET` | `/api/v1/agent/user-bindings` | List user bindings |
| `PUT` | `/api/v1/agent/user-bindings/{roleType}` | Set user binding |
| `DELETE` | `/api/v1/agent/user-bindings/{roleType}` | Delete user binding |
| `POST` | `/api/v1/agent/resolve-preview` | Resolve preview |
| `GET` | `/api/v1/agent/execution-snapshots/{id}` | Snapshot detail |
| `GET` | `/api/v1/projects/{projectId}/agent-bindings` | List project bindings |
| `PUT` | `/api/v1/projects/{projectId}/agent-bindings/{roleType}` | Set project binding |
| `DELETE` | `/api/v1/projects/{projectId}/agent-bindings/{roleType}` | Delete project binding |

- [ ] **Step 5: Run all Agent backend tests**

Run: `cd aicp-backend && mvn -Dtest='Agent*Test' test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/agent/controller aicp-backend/src/main/java/com/aicp/module/agent/service/AgentTestRunService.java aicp-backend/src/test/java/com/aicp/module/agent/service/AgentTestRunServiceTest.java
git commit -m "feat: add agent configuration APIs"
```

## Task 7: Add frontend API and pure configuration state

**Files:**
- Modify: `aicp-frontend/src/api/agent.js`
- Create: `aicp-frontend/src/views/agent-config/agentConfigHelpers.js`
- Create: `aicp-frontend/tests/agent-config-state.test.js`
- Modify: `aicp-frontend/package.json`

- [ ] **Step 1: Write failing pure-state tests**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { createWizardState, canPublish, bindingSourceLabel } from '../src/utils/agentConfigHelpers.js'

test('new agent wizard starts by selecting a blueprint', () => {
  assert.deepEqual(createWizardState(), {
    step: 'blueprint', blueprintId: null, identity: null,
    parameters: {}, editablePrompt: '', successfulTestRunId: null
  })
})

test('draft cannot publish without valid config and successful test', () => {
  assert.equal(canPublish({ valid: true, successfulTestRunId: null }), false)
  assert.equal(canPublish({ valid: true, successfulTestRunId: 'run_1' }), true)
})

test('binding labels are explicit', () => {
  assert.equal(bindingSourceLabel('PROJECT'), '项目默认')
  assert.equal(bindingSourceLabel('SYSTEM'), '系统默认')
})
```

- [ ] **Step 2: Add the test script and verify failure**

```json
"scripts": {
  "test": "node --test tests/*.test.js",
  "dev": "vite",
  "build": "vite build",
  "preview": "vite preview"
}
```

Run: `cd aicp-frontend && npm test -- --test-name-pattern='agent'`

Expected: FAIL because `agentConfigHelpers.js` does not exist.

- [ ] **Step 3: Implement pure state helpers**

```js
export const createWizardState = () => ({
  step: 'blueprint', blueprintId: null, identity: null,
  parameters: {}, editablePrompt: '', successfulTestRunId: null
})

export const canPublish = draft => draft?.valid === true && Boolean(draft.successfulTestRunId)

export const bindingSourceLabel = source => ({
  PROJECT: '项目默认', USER: '用户默认', SYSTEM: '系统默认', TEMPORARY: '单次调整'
})[source] || '未解析'
```

- [ ] **Step 4: Add API methods**

```js
// Blueprint 与 Agent 定义
getBlueprints: () => request.get('/agent/blueprints'),
getBlueprint: id => request.get(`/agent/blueprints/${id}`),
getDefinitions: (params) => request.get('/agent/definitions', { params }),
getDefinition: id => request.get(`/agent/definitions/${id}`),
createDefinition: data => request.post('/agent/definitions', data),
updateDefinition: (id, data) => request.patch(`/agent/definitions/${id}`, data),
copyDefinition: id => request.post(`/agent/definitions/${id}/copies`),
archiveDefinition: id => request.post(`/agent/definitions/${id}/archive`),
// 版本与试跑
getVersions: id => request.get(`/agent/definitions/${id}/versions`),
createDraft: id => request.post(`/agent/definitions/${id}/drafts`),
getVersion: id => request.get(`/agent/versions/${id}`),
updateVersion: (id, data) => request.put(`/agent/versions/${id}`, data),
validateVersion: id => request.post(`/agent/versions/${id}/validate`),
testVersion: (id, data) => request.post(`/agent/versions/${id}/test-runs`, data),
getTestRun: id => request.get(`/agent/test-runs/${id}`),
publishVersion: (id, data) => request.post(`/agent/versions/${id}/publish`, data),
activateVersion: id => request.post(`/agent/versions/${id}/activate`),
// 绑定与解析
getUserBindings: () => request.get('/agent/user-bindings'),
setUserBinding: (roleType, data) => request.put(`/agent/user-bindings/${roleType}`, data),
deleteUserBinding: roleType => request.delete(`/agent/user-bindings/${roleType}`),
getProjectBindings: projectId => request.get(`/projects/${projectId}/agent-bindings`),
bindProjectAgent: (projectId, role, data) => request.put(`/projects/${projectId}/agent-bindings/${role}`, data),
deleteProjectBinding: (projectId, role) => request.delete(`/projects/${projectId}/agent-bindings/${role}`),
resolvePreview: data => request.post('/agent/resolve-preview', data),
getSnapshot: id => request.get(`/agent/execution-snapshots/${id}`)
```

- [ ] **Step 5: Run frontend tests**

Run: `cd aicp-frontend && npm test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add aicp-frontend/package.json aicp-frontend/src/api/agent.js aicp-frontend/src/utils/agentConfigHelpers.js aicp-frontend/tests/agent-config-state.test.js
git commit -m "feat: add agent configuration frontend state"
```

## Task 8: Build the configuration center and creation wizard

**Files:**
- Create: frontend composable, page, and component files listed in File structure.
- Test: `aicp-frontend/tests/agent-config-state.test.js`

- [ ] **Step 1: Extend tests with filtering and selection contracts**

```js
test('filterDefinitions matches role and lifecycle status', () => {
  const rows = [
    { id: 'a', roleType: 'HOOK', lifecycleStatus: 'ACTIVE' },
    { id: 'b', roleType: 'DIRECTOR', lifecycleStatus: 'ARCHIVED' }
  ]
  assert.deepEqual(filterDefinitions(rows, { roleType: 'HOOK', status: 'ACTIVE' }).map(x => x.id), ['a'])
})
```

- [ ] **Step 2: Run the focused test and verify failure**

Run: `cd aicp-frontend && node --test --test-name-pattern='filterDefinitions' tests/agent-config-state.test.js`

Expected: FAIL because `filterDefinitions` is not exported.

- [ ] **Step 3: Implement the composable as the only API orchestration layer**

```js
export function useAgentConfig() {
  const blueprints = ref([]), definitions = ref([]), selected = ref(null)
  const loading = ref(false), error = ref('')
  const load = async () => {
    loading.value = true; error.value = ''
    try {
      const [bp, defs] = await Promise.all([agentApi.getBlueprints(), agentApi.getDefinitions()])
      blueprints.value = bp.data || []
      definitions.value = defs.data?.items || defs.data || []
      selected.value ||= definitions.value[0] || null
    } catch (e) { error.value = e.message || '加载 Agent 配置失败' }
    finally { loading.value = false }
  }
  return { blueprints, definitions, selected, loading, error, load }
}
```

- [ ] **Step 4: Build the three-column page and four-step wizard**

`AgentConfigCenter.vue` owns layout only. `CreateAgentWizard.vue` emits `created`, `close`, and never calls unrelated project APIs. `AgentEditorPanel.vue` renders tabs for base, parameters, advanced prompt, versions, test runs, and usage/bindings. Locked Blueprint content is read-only. The “使用项目和默认绑定” tab displays project assignments and lets the owner set a user default; project-level binding management (add/edit/delete) is also accessible from this tab when the user has `MANAGE_AGENT_CONFIG` permission on the selected project.

Use this event contract:

```vue
<CreateAgentWizard
  v-model="showCreate"
  :blueprints="blueprints"
  @created="handleCreated"
/>
<AgentEditorPanel
  v-if="selected"
  :definition="selected"
  @saved="reloadSelected"
  @test="openTestPanel"
  @publish="publishDraft"
/>
```

- [ ] **Step 5: Run pure-function tests and production build**

Run: `cd aicp-frontend && npm test && npm run build`

Expected: all tests PASS and Vite build exits 0.

> **组件测试策略：** M1 阶段通过 `agentConfigHelpers.js` 的纯函数测试覆盖状态转换、过滤、校验和绑定标签逻辑。Vue 组件的交互测试（wizard 步骤流转、editor tab 切换、试跑结果渲染）在 M2 中随业务集成端到端测试一同补充。M1 的生产构建 (`npm run build`) 已确保模板编译无误。

- [ ] **Step 6: Commit**

```bash
git add aicp-frontend/src/views/agent-config aicp-frontend/tests/agent-config-state.test.js
git commit -m "feat: build agent configuration center"
```

## Task 9: Add navigation and verify the M1 vertical slice

**Files:**
- Modify: `aicp-frontend/src/router/index.js`
- Modify: `aicp-frontend/src/components/Sidebar.vue`
- Modify: `aicp-frontend/tests/navigation-contract.test.js`

- [ ] **Step 1: Write a failing navigation contract test**

```js
test('agent configuration has a stable route and sidebar entry', () => {
  const router = readFileSync(new URL('../src/router/index.js', import.meta.url), 'utf8')
  const sidebar = readFileSync(new URL('../src/components/Sidebar.vue', import.meta.url), 'utf8')
  assert.match(router, /path:\s*'agent-config'/)
  assert.match(sidebar, /to="\/agent-config"/)
  assert.match(sidebar, /Agent 配置/)
})
```

- [ ] **Step 2: Run test and verify failure**

Run: `cd aicp-frontend && node --test --test-name-pattern='agent configuration' tests/navigation-contract.test.js`

Expected: FAIL because the route and link are absent.

- [ ] **Step 3: Add route and sidebar link**

```js
{
  path: 'agent-config',
  name: 'AgentConfig',
  component: () => import('@/views/agent-config/AgentConfigCenter.vue'),
  meta: { title: 'Agent 配置' }
}
```

Place “Agent 配置” next to “Agent 会话”; keep them as separate concepts.

- [ ] **Step 4: Run the complete verification set**

Run:

```bash
cd aicp-backend && mvn test
cd ../aicp-frontend && npm test && npm run build
```

Expected: Maven BUILD SUCCESS; Node tests PASS; Vite build succeeds.

- [ ] **Step 5: Perform API smoke verification with the dev profile**

Start backend with `cd aicp-backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev`, authenticate using the existing local flow, then verify:

1. `GET /api/v1/agent/blueprints` returns four roles.
2. Create a HOOK Agent and receive v1 DRAFT.
3. Update and validate the draft.
4. Confirm publish fails before a successful test run.
5. Run a successful test, then publish.
6. Bind it as user default.
7. `POST /api/v1/agent/resolve-preview` reports source `USER`.
8. Bind a project default and confirm source becomes `PROJECT`.

- [ ] **Step 6: Commit**

```bash
git add aicp-frontend/src/router/index.js aicp-frontend/src/components/Sidebar.vue aicp-frontend/tests/navigation-contract.test.js
git commit -m "feat: expose agent configuration center"
```

## Task 10: Update contracts and run final M1 acceptance

**Files:**
- Modify: `docs/01-core/API接口文档_V1.5.md`
- Modify: `docs/01-core/用户端产品功能设计.md`
- Test: all backend and frontend suites.

- [ ] **Step 1: Document exact M1 endpoints and state rules**

Add the endpoint list from design section 12, request/response examples for create, update draft, test, publish, bind, and resolve preview, plus 49020–49027 errors. State explicitly that business generation integration is delivered by later plans.

- [ ] **Step 2: Run placeholder and contract scans**

Run:

```bash
rg -n "T[B]D|T[O]DO|implement[ ]later|fill[ ]in" aicp-backend/src/main/java/com/aicp/module/agent aicp-frontend/src/views/agent-config docs/01-core
rg -n "resolved_prompt" aicp-frontend/src
```

Expected: no M1 placeholders; frontend does not send `resolved_prompt`.

- [ ] **Step 3: Run final verification**

Run:

```bash
cd aicp-backend && mvn clean test
cd ../aicp-frontend && npm test && npm run build
```

Expected: all commands exit 0.

- [ ] **Step 4: Review the final diff for scope**

Run: `git diff --stat HEAD~9..HEAD && git status --short`

Expected: only M1 configuration-center code, tests, schema, and contract docs are part of these commits; unrelated pre-existing working-tree changes remain unstaged.

- [ ] **Step 5: Commit documentation**

```bash
git add docs/01-core/API接口文档_V1.5.md docs/01-core/用户端产品功能设计.md
git commit -m "docs: document agent configuration center APIs"
```

## M1 definition of done

- Four active Blueprints exist in H2 and MySQL bootstrap paths, each with complete JSON Schema (parameters, input, output, tools, context, model policy).
- A user can create, copy, archive, and list only their own Agent definitions. Agent names are unique per user.
- Drafts validate variables and locked boundaries; published versions are immutable.
- Publishing requires at least one successful test run (model returns valid JSON that satisfies the output schema).
- User and project bindings are authorized and use optimistic concurrency (`row_version` on both versions and bindings).
- Resolver precedence is PROJECT > USER > SYSTEM; explicit invalid bindings fail closed (no silent fallback).
- A resolved configuration can be frozen as an immutable snapshot via `POST /api/v1/agent/resolve-preview`; snapshots are retrievable by ID.
- All 25 M1 REST endpoints respond correctly under the dev profile.
- The configuration center and four-step wizard are reachable from navigation; the editor panel includes a bindings tab for user-default and project-level management.
- Backend tests, frontend pure-function tests, and production build all pass. Vue component interaction tests deferred to M2.
- No production script/hook/storyboard/director service is changed in M1; those integrations are separate plans.
