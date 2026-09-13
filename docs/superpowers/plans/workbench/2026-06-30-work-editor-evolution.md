# Work Editor Evolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `/tag-editor/:scriptId` into the complete work editor defined by the approved specification while preserving legacy script compatibility and integrating content-project data.

**Architecture:** Keep the existing route and visual shell, but move business logic into focused Vue components and a content-project editor API. A legacy resolver maps `scriptId` to `content_projects.legacy_script_id`; project profiles, generic setting entities, versions, and extraction candidates provide the persistent model. Existing worldbuilding and AI router code is reused behind a candidate-review boundary.

**Tech Stack:** Vue 3, Element Plus, Vue Router, Axios, Vite, Spring Boot 3, MyBatis-Plus, H2/MySQL, JUnit 5, Mockito.

---

### Task 1: Add profile schema, tag dictionary, and legacy project resolution

**Files:**
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/ContentProjectProfile.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/ContentProjectProfileMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/WorkEditorService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/TagDictionaryController.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/WorkEditorServiceTest.java`

- [ ] Write a failing test proving `resolveLegacy(userId, scriptId)` returns the mapped project, rejects another user's script, and creates a project/profile when no mapping exists.
- [ ] Run `cd aicp-backend && mvn -Dtest=WorkEditorServiceTest test`; expect failures because `WorkEditorService` does not exist.
- [ ] Add `content_project_profiles` table DDL（参见设计文档 8.1 节完整 DDL，包含 `genre_tag`、`plot_tags`/`tone_tags` JSON、`setting_tag`、`synopsis`、`outline`、`revision`、索引）；add the entity and mapper.
- [ ] Implement `WorkEditorService.resolveLegacy` using `ScriptMapper`、`ContentProjectMapper`、`ProjectMemberMapper`、and `ContentProjectProfileMapper`；enforce script ownership before resolving. 新建项目时同时创建 `project_members`（OWNER）和 `project_parameter_versions` v1 记录。
- [ ] 实现 `GET /api/v1/tag-dictionary` 端点（`TagDictionaryController`），返回四轴有效选项及 `version`，数据源为数据库配置表 `tag_dictionary`（DDL 同步加入 schema 文件）。
- [ ] Re-run the focused test; expect PASS.
- [ ] Commit only Task 1 files with `feat: add work editor profile, tag dictionary, and legacy resolver`.

### Task 2: Implement validated editor/profile APIs

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/WorkEditorController.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/WorkEditorRequests.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/WorkEditorViews.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/WorkEditorService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/script/service/ScriptService.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/WorkEditorServiceTest.java`

- [ ] Add failing tests for 1/3/3/1 tag limits, unknown labels, optimistic-lock conflicts, and missing resources.
- [ ] Run the focused tests; expect tag validation and conflict cases to fail.
- [ ] Define `GET /content-projects/legacy-scripts/{scriptId}/editor`、`GET /content-projects/{id}/editor`、`PUT /content-projects/{id}/tags`、and `PATCH /content-projects/{id}/profile`。
- [ ] Implement a single tag validator（读取 `tag_dictionary` 表校验合法值和 1/3/3/1 数量限制）and make **旧 `ScriptService.updateTags()` 双写**：同时更新 `scripts` 表（兼容旧字段投影）和委托 `WorkEditorService` 写入 `content_project_profiles`（新真相来源）。标签校验规则不重复——`ScriptService` 调用同一校验器。双写期间任一写入失败均返回错误并回滚。
- [ ] Return editor title, word count, permissions, profile, revision, setting counts, and pending extraction count.
- [ ] Re-run focused tests and `mvn -DskipTests compile`; expect PASS.
- [ ] Commit with `feat: add work editor profile APIs`.

### Task 3: Add generic setting CRUD, versions, copy, and relation storage

**Files:**
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/ProjectSettingEntity.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/ProjectSettingVersion.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/ProjectSettingEntityMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/ProjectSettingVersionMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectSettingService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/WorkEditorController.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ProjectSettingServiceTest.java`

- [ ] Write failing tests for create/list/update/copy/archive/restore、type validation、duplicate canonical names、version creation、access control、and revision conflict.
- [ ] Run `mvn -Dtest=ProjectSettingServiceTest test`; expect failure.
- [ ] Add DDL for `project_setting_entities` 和 `project_setting_versions`（参见设计文档 8.2、8.3 节完整 DDL）。`project_setting_entities` 包含 `setting_type`（`character`/`background`/`faction`/`location`/`item`）、`canonical_name`、`aliases_json`、`summary`、`details_json`、`relationships_json`、`status`、`source_type`、`current_version_no`、`revision`、归档字段和 `uk_setting_entity` 唯一约束。`details_json` 按类型存储结构化属性（设计文档 8.2 节类型化属性约定）。
- [ ] Implement list filtering by type/status/keyword and mutation methods that always create audit versions.
- [ ] 实现 `POST .../settings/{settingId}/copy`：创建完整副本（规范名追加"（副本）"、状态 `draft`、`source_type=manual`、版本号重置为 1）。
- [ ] Expose the CRUD、restore、copy、and versions endpoints from the approved spec.
- [ ] Re-run focused tests; expect PASS.
- [ ] Commit with `feat: add project setting management`.

### Task 4: Add extraction candidates, reviewed apply, context publisher, and character_profiles migration

**Files:**
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/SettingExtractionBatch.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/SettingExtractionCandidate.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/SettingExtractionBatchMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/SettingExtractionCandidateMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/SettingExtractionService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectContextPublisher.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/WorkEditorController.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContextAssembler.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/SettingExtractionServiceTest.java`

- [ ] Write failing tests for stable idempotency keys, candidate-only AI output, evidence/confidence persistence, decision validation, transaction rollback, duplicate apply, and parameter-version publication.
- [ ] Run the focused test; expect failure.
- [ ] Add DDL for `setting_extraction_batches` 和 `setting_extraction_candidates`（参见设计文档 8.4、8.5 节完整 DDL），含外键、幂等键唯一约束和索引。
- [ ] Implement extraction with `AiRouter`、structured JSON parsing、canonical-name matching、and candidates that never mutate formal settings.
- [ ] Implement decision drafts (`merge`、`keep`、`replace`) and transactional apply that creates setting versions plus a new parameter version.
- [ ] **实现 `ProjectContextPublisher`**：在资料保存（Task 2）和设定确认回写（本 Task）后调用，组装当前全部 `confirmed` 状态设定 + profile 数据 → 调用 `ProjectWorkflowService.appendParameters()` 生成新参数快照 → 向 `outbox_events` 写入 `CONTEXT_REFRESH` 事件（复用现有 `outbox_events` 表，事件类型 `CONTEXT_REFRESH`，payload 含 `project_id` 和 `parameter_version_id`）。下游生成链路通过 `ContextAssembler` 读取最新参数版本。
- [ ] **实现 `character_profiles` → `project_setting_entities` 迁移**：在 `LegacyProjectProjectionService`（或独立 `CharacterProfileMigrationService`）中添加可重复执行方法——按 `project_id` 遍历 `character_profiles`，对尚无对应 `project_setting_entities` 记录（`setting_type='character'`）的行创建统一实体。映射规则见设计文档 8.6 节。
- [ ] Add create/get/decision/apply/retry endpoints.
- [ ] Re-run focused and context-assembler tests; expect PASS.
- [ ] Commit with `feat: add reviewed setting extraction, context publisher, and migration`.

### Task 5: Add frontend editor API, state utilities, and tag dictionary loader

**Files:**
- Modify: `aicp-frontend/src/api/contentProject.js`
- Create: `aicp-frontend/src/views/work-editor/workEditorData.js`
- Create: `aicp-frontend/src/views/work-editor/useWorkEditor.js`
- Test: `aicp-frontend/tests/work-editor-data.test.js`

- [ ] Write failing Node tests for legacy editor response normalization, 1/3/3/1 selection, serialized save queue ordering, setting type definitions, and extraction-decision payloads.
- [ ] Run `cd aicp-frontend && node --test tests/work-editor-data.test.js`; expect module-not-found failure.
- [ ] Add API methods for editor/profile/tags/settings/extractions。新增 `fetchTagDictionary()` 调用 `GET /api/v1/tag-dictionary`，前端启动时加载并缓存（`workEditorData.js` 提供 `getTagDictionary()` 纯函数，带版本比较）。
- [ ] Implement the composable for load/save/error/dirty/revision state while keeping view rendering separate.
- [ ] **评估 Pinia store**：若多个面板（TagPanel、SettingPanel、ExtractionReviewDrawer）需要共享 `currentProject`、`revision`、`dirtyFlags` 状态，创建 `useWorkEditorStore`（Pinia composition API，参照现有 `stores/auth.js` 模式）；否则仅靠 composable 级状态共享。当前阶段选择 composable 优先，store 在需要跨组件共享时提取。
- [ ] 移除前端硬编码标签列表，改为从 `fetchTagDictionary()` 动态加载。字典 `version` 变更时（与上次加载值比较）通过 `ElMessage` 提示用户刷新页面。
- [ ] Re-run the focused test; expect PASS.
- [ ] Commit with `feat: add work editor frontend state`.

### Task 6: Refactor the legacy page into the complete editor UI

**Files:**
- Modify: `aicp-frontend/src/views/TagEditor.vue`
- Create: `aicp-frontend/src/views/work-editor/WorkInfoNav.vue`
- Create: `aicp-frontend/src/views/work-editor/TagPanel.vue`
- Create: `aicp-frontend/src/views/work-editor/TextProfilePanel.vue`
- Create: `aicp-frontend/src/views/work-editor/SettingPanel.vue`
- Create: `aicp-frontend/src/views/work-editor/ExtractionReviewDrawer.vue`

**组件简化说明**：设计文档定义了 8 个前端单元（`WorkEditorShell`、`WorkInfoNav`、`TagPanel`、`SynopsisPanel`、`OutlinePanel`、`SettingListPanel`、`SettingDetailPanel`、`ExtractionReviewDrawer`）。实施中做以下合并：
- `SynopsisPanel` + `OutlinePanel` → `TextProfilePanel.vue`（两份资料共用一个文本编辑面板，通过 prop 区分类型）
- `SettingListPanel` + `SettingDetailPanel` → `SettingPanel.vue`（列表与详情分栏在同一组件内管理）
- `WorkEditorShell` 由重构后的 `TagEditor.vue` 承担（保留旧路由，内部替换为新架构）
此简化不改变功能覆盖——所有九个导航入口和对应的增删改查能力保持不变。

- [ ] Add failing rendering/data tests for all nine enabled navigation items, counts, save states, type-specific fields, and extraction review choices.
- [ ] Run the focused frontend tests; expect failure.
- [ ] Keep `/tag-editor/:scriptId`, load through the legacy editor endpoint, and render the approved navigation/workspace layout.
- [ ] Preserve tag auto-save; add synopsis/outline draft auto-save; implement settings list/detail CRUD、search、copy、archive、restore、and AI extraction review.
- [ ] `SettingPanel.vue` 根据 `setting_type` 渲染不同的 `details_json` 表单字段（参见设计文档 8.2 节类型化属性约定）。
- [ ] Ensure loading, empty, read-only, 403, 404, 409, retry, and unsaved-leave states have visible recovery actions.
- [ ] Run `node --test tests/work-editor-data.test.js` and `npm run build`; expect PASS.
- [ ] Commit with `feat: complete work editor interface`.

### Task 7: Restore warehouse entry and project route compatibility

**Files:**
- Modify: `aicp-frontend/src/router/index.js`
- Modify: `aicp-frontend/src/views/Warehouse.vue`
- Modify: `aicp-frontend/src/views/content-project/ContentProjectList.vue`
- Modify: `aicp-frontend/src/api/contentProject.js`
- Test: `aicp-frontend/tests/work-editor-routing.test.js`

- [ ] Write failing tests for `/script-gen/:projectId/edit/:section?`, legacy route retention, project-card edit links, and warehouse filtering payloads.
- [ ] Run the focused test; expect failure.
- [ ] 新增 `/script-gen/:projectId/edit/:section?` 路由（指向重构后的 `TagEditor.vue`），同时保留 `/tag-editor/:scriptId` 旧路由。确保两个路由加载同一组件实例（通过 `props: true` 传递参数，组件内部根据 `scriptId` 或 `projectId` 选择数据加载路径）。
- [ ] Restore real warehouse/project cards with keyword, status, and four-axis filter controls. 项目卡片"编辑"按钮指向 `/script-gen/:projectId/edit`，旧脚本兼容卡片指向 `/tag-editor/:scriptId`。
- [ ] Point every edit action to the correct project or legacy route without using nonexistent `/content-projects` paths.
- [ ] Re-run focused tests and frontend build; expect PASS.
- [ ] Commit with `feat: connect work editor to warehouse`.

### Task 8: Full verification and localhost deployment

**Files:**
- Modify generated backend static assets only through the existing frontend build/deployment workflow.

- [ ] Run `cd aicp-backend && mvn test`; expect all tests PASS.
- [ ] Run all frontend Node tests and `npm run build`; expect PASS and no Vue compilation errors.
- [ ] Restart the 8080 application with the updated backend and frontend assets using the repository's current run workflow.
- [ ] Sign in with the local development account and verify `http://localhost:8080/tag-editor/1` loads the editor rather than redirecting or showing disabled items.
- [ ] Exercise tag save, synopsis/outline save, all five setting sections, extraction review, refresh persistence, and warehouse return/filter behavior.
- [ ] Inspect browser console and network failures; expect no uncaught errors and no failed editor API requests.
- [ ] Commit final deployment/test adjustments with `test: verify complete work editor flow`.
