# 作品编辑中心迭代设计

日期：2026-06-30  
状态：已完成产品设计确认，待规格审阅  
实施策略：保留旧版体验，按纵向切片迭代

## 1. 背景与现状

当前 `TagEditor.vue` 已具备四轴标签选择、数量限制、清空、自动保存、简介编辑和离开保护，但它仍是旧版 `script` 数据模型上的孤立页面，无法支撑新版内容项目的完整编辑流程。

已确认的主要缺口：

1. 新版内容项目列表没有作品编辑入口；仓库页已退化为提示页，且按钮指向不存在的 `/content-projects` 路由。
2. 标签编辑器只接受旧版 `scriptId`，没有接入新版 `content_projects` 主链路。
3. 左侧总纲、角色、背景、势力、地点、物品均处于禁用状态。
4. 标签列表硬编码在前端；后端未统一校验标签合法值和 1/3/3/1 数量限制。
5. 旧标签写接口缺少完整的资源归属校验，剧本不存在时仍可能返回成功。
6. 仓库没有真实标签筛选闭环，市场仍使用静态示例数据。
7. 新版内容项目已经具备参数版本与上下文组装能力，但作品资料和结构化设定尚未纳入该链路。
8. 缺少标签保存竞态、设定 CRUD、AI 提取审核、旧数据兼容和下游上下文生效的专项测试。

## 2. 目标

本次迭代将旧版标签编辑器扩展为“作品编辑中心”，目标如下：

1. 保留旧版左侧导航、标签选择和保存反馈等成熟交互。
2. 接入新版内容项目主链路，同时保持旧 `/tag-editor/:scriptId` 链接可用。
3. 完成标签、简介、总纲的真实编辑和保存闭环。
4. 完成角色、背景、势力、地点、物品五类设定的创建、查询、编辑、复制、归档和版本追踪。
5. 支持从正文全文、指定章节或已锁定版本中异步提取结构化设定。
6. AI 提取结果先作为候选，用户逐字段选择合并、保留原值或采用新值后才能写入正式设定库。
7. 经确认的资料与设定生成新的参数快照，供大纲、正文、分镜和投流生成使用。
8. 恢复仓库真实列表、标签筛选和作品编辑入口。

## 3. 非目标

本次不包含以下内容：

1. 不重新设计整套内容项目创作工作台。
2. 不让 AI 未经审核直接覆盖人工资料或设定。
3. 不直接根据提取结果改写原正文。
4. 不在本期重做交易市场；P0 只保证标签数据具备下游消费能力，市场真实上架联动单独排期。
5. 不物理删除已被正文、分镜或资产引用的正式设定。
6. 不在单个发布批次内同时交付 P0、P1、P2；每一期必须独立验收。

## 4. 核心设计决策

### 4.1 采用纵向切片迭代

选择“旧版体验 + 新项目主链路 + 兼容层”的方案，不在旧单文件中继续堆叠全部功能，也不整体推翻重做。

每一期都同时覆盖前端、接口、权限、数据、测试和下游联动，避免出现只有页面没有数据的待接入入口。

### 4.2 新版内容项目作为主链路

新版入口为：

```text
/script-gen/:projectId/edit/:section?
```

旧入口继续保留：

```text
/tag-editor/:scriptId
```

旧入口通过 `content_projects.legacy_script_id` 解析或创建兼容项目，然后进入同一个作品编辑中心。旧入口不得维护第二套页面或业务规则。

### 4.3 人工确认结果优先

AI 只产生候选数据。候选未经用户确认时：

- 不进入正式设定库；
- 不进入生成上下文；
- 不影响仓库筛选；
- 不覆盖现有人工字段。

当候选与已有设定冲突时，用户必须逐字段选择：

- 合并：按字段类型执行去重合并；
- 保留原值：丢弃该候选字段；
- 采用新值：用候选值创建正式新版本。

### 4.4 可筛选数据结构化存储

四轴标签必须使用可索引字段或规范化关联存储，不能只写入不可查询 JSON。五类设定共用实体骨架，通过类型和结构化属性区分，避免复制五套 CRUD。

## 5. 模块架构

作品编辑中心由以下前端单元组成：

1. `WorkEditorShell`：解析项目、权限、路由区块、全局保存状态和离开保护。
2. `WorkInfoNav`：展示资料与设定导航、数量、完善状态和只读状态。
3. `TagPanel`：复用并抽离旧版四轴标签交互。
4. `SynopsisPanel`：编辑简介。
5. `OutlinePanel`：编辑总纲。
6. `SettingListPanel`：五类设定共用的搜索、筛选、创建、复制和归档列表。
7. `SettingDetailPanel`：按设定类型渲染字段并处理版本冲突。
8. `ExtractionReviewDrawer`：展示 AI 候选、原文证据、置信度和逐字段决策。

后端按领域职责拆分：

1. `LegacyWorkResolver`：将旧 `scriptId` 映射到内容项目，确保旧入口和新入口进入同一套业务逻辑。
2. `ProjectProfileService`（即计划中的 `WorkEditorService`）：作品资料、标签校验、保存和旧接口委托。标签字典由此服务提供。
3. `ProjectSettingService`：正式设定 CRUD、复制、关系管理、版本追踪和归档恢复。
4. `SettingExtractionService`：提取任务、候选匹配、审核决策草稿、事务回写应用和幂等控制。
5. `ProjectContextPublisher`：基于已确认资料与正式设定，调用 `ProjectWorkflowService.appendParameters()` 生成新参数快照，并向 `outbox_events` 写入 `CONTEXT_REFRESH` 事件供下游消费。

**注意**：前端组件在设计层面列出 8 个单元，实施计划出于效率将 `SynopsisPanel` + `OutlinePanel` 合并为 `TextProfilePanel`，`SettingListPanel` + `SettingDetailPanel` 合并为 `SettingPanel`，`WorkEditorShell` 由重构后的 `TagEditor.vue` 承担。此简化为有意设计，不改变功能覆盖。

## 6. 页面与交互

### 6.1 页面结构

保留旧版左侧导航和右侧工作区：

- 作品信息：标签、简介、总纲；
- 设定：角色、背景、势力、地点、物品。

导航项显示：

- 标签已选数量；
- 资料是否完善；
- 每类正式设定数量；
- 只读或无权限状态；
- 待审核 AI 候选数量。

资料类页面使用单工作区。设定类页面使用列表与详情分栏：左侧为搜索、状态筛选和实体列表，右侧为类型化表单、来源、引用、版本和保存状态。

### 6.2 保存策略

- 标签：保持 800ms 防抖自动保存，并保留手动保存按钮。
- 简介和总纲：自动保存草稿，页面持续展示“等待保存、保存中、已保存、保存失败、版本冲突”状态。
- 设定详情：自动保存草稿；用户执行“确认设定”后创建正式版本并进入生成上下文。
- 页面离开：仍有未提交草稿或保存失败时阻止离开并给出明确选择。
- 保存竞态：采用串行保存队列，服务端最终值必须等于用户最后一次修改。

### 6.3 五类设定能力

每类设定支持：

- 创建；
- 搜索与状态筛选；
- 查看与编辑；
- 复制；
- 归档与恢复；
- 来源和引用查看；
- 历史版本查看；
- AI 提取；
- 待确认、已确认、待补充状态。

角色、背景、势力、地点、物品使用共用基础字段：名称、别名、摘要、详细描述、状态、来源、结构化属性、关系、引用、修订号。每种类型可增加自己的属性定义，但 API 和版本机制保持一致。

## 7. AI 提取与回写流程

### 7.1 创建任务

用户选择提取来源：全文、指定章节或已锁定内容版本。请求必须记录：

- `source_version_id` 或明确的章节版本集合；
- 目标设定类型；
- 模型标识；
- Prompt 版本；
- 提取配置；
- 项目修订号。

同一项目、来源版本、类型和配置产生稳定幂等键。重复提交返回已有任务，不创建重复批次。

### 7.2 候选生成

每个候选包含：

- 设定类型；
- 规范名和别名；
- 字段值；
- 原文证据位置与片段；
- 字段级置信度；
- 建议匹配的正式设定；
- 匹配原因；
- 新增、重复或冲突状态。

系统按类型、规范名、别名和已有关系匹配，不能仅凭名称直接覆盖。

### 7.3 人工审核

无冲突候选可批量接受。存在冲突的候选必须逐字段决策。审核抽屉同时展示当前正式值、AI 新值、证据和置信度。

审核决策仅保存为批次草稿，不立即改变正式设定。用户点击“确认回写”后统一应用。

### 7.4 事务回写

确认回写在单个事务内完成：

1. 校验项目权限与当前修订号；
2. 校验批次状态和幂等键；
3. 创建或更新正式设定；
4. 创建字段级设定版本；
5. 保存实体关系；
6. 写入操作者、模型、Prompt、证据和前后值审计；
7. 生成新的项目参数快照；
8. 追加上下文刷新事件；
9. 标记批次为已应用。

任一步骤失败时全部回滚。重复应用同一批次返回已应用结果，不重复创建版本。

### 7.5 下游生效

只有已确认资料和正式设定进入项目参数快照。后续大纲、正文、分镜和投流生成从当前参数版本读取上下文，并记录所使用的参数版本 ID，以便复现。

## 8. 数据模型

### 8.1 `content_project_profiles`

建议字段：

- `project_id`，唯一；
- `genre_tag`；
- `plot_tags`；
- `tone_tags`；
- `setting_tag`；
- `synopsis`；
- `outline`；
- `revision`；
- `updated_by`；
- 创建和更新时间。

题材和时空为单值；情节和情绪为受控多值。实现时可根据数据库能力选择 JSON 数组加辅助索引或规范化关联表，但查询接口必须支持四轴组合筛选。

**DDL 参考（H2/MySQL）：**

```sql
CREATE TABLE IF NOT EXISTS content_project_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL UNIQUE,
    genre_tag VARCHAR(50),
    plot_tags JSON,
    tone_tags JSON,
    setting_tag VARCHAR(50),
    synopsis TEXT,
    outline TEXT,
    revision INT DEFAULT 0,
    updated_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cpp_genre ON content_project_profiles(genre_tag);
CREATE INDEX IF NOT EXISTS idx_cpp_setting ON content_project_profiles(setting_tag);
```

### 8.2 `project_setting_entities`

建议字段：

- `id`；
- `project_id`；
- `setting_type`：`character`、`background`、`faction`、`location`、`item`；
- `canonical_name`；
- `aliases_json`；
- `summary`；
- `details_json`；
- `relationships_json`：存储与其他设定实体、内容单元的引用关系；
- `status`：`draft`、`confirmed`、`needs_enrichment`、`archived`；
- `source_type`：`manual`、`ai_extracted`、`merged`；
- `current_version_no`；
- `revision`；
- 创建、更新和归档信息。

项目、类型、规范名和未归档状态建立唯一约束，避免同类正式设定重复。

**DDL 参考（H2/MySQL）：**

```sql
CREATE TABLE IF NOT EXISTS project_setting_entities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    setting_type VARCHAR(20) NOT NULL,
    canonical_name VARCHAR(200) NOT NULL,
    aliases_json JSON,
    summary TEXT,
    details_json JSON,
    relationships_json JSON,
    status VARCHAR(20) DEFAULT 'draft',
    source_type VARCHAR(20) DEFAULT 'manual',
    current_version_no INT DEFAULT 0,
    revision INT DEFAULT 0,
    created_by BIGINT,
    updated_by BIGINT,
    archived_at TIMESTAMP NULL,
    archived_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_setting_entity UNIQUE (project_id, setting_type, canonical_name, status)
);
CREATE INDEX IF NOT EXISTS idx_pse_project_type ON project_setting_entities(project_id, setting_type);
CREATE INDEX IF NOT EXISTS idx_pse_status ON project_setting_entities(status);
```

**类型化属性约定**：`details_json` 按 `setting_type` 存储不同结构：

- `character`：`{ "role", "archetype", "appearance", "personality", "motivation", "backstory", ... }`
- `background`：`{ "era", "world_type", "rules", "history", ... }`
- `faction`：`{ "scale", "structure", "goal", "members", ... }`
- `location`：`{ "type", "climate", "features", "inhabitants", ... }`
- `item`：`{ "category", "origin", "abilities", "restrictions", ... }`

每种类型的详情面板按此结构渲染对应表单字段。API 和版本机制保持统一。

### 8.3 `project_setting_versions`

保存实体完整快照、字段级变更、来源、操作者、证据和创建时间。正式确认、AI 合并和人工回退都会创建版本。

**DDL 参考：**

```sql
CREATE TABLE IF NOT EXISTS project_setting_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    snapshot_json JSON NOT NULL,
    field_changes_json JSON,
    source_type VARCHAR(20),
    operated_by BIGINT,
    evidence_json JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_setting_version UNIQUE (entity_id, version_no)
);
CREATE INDEX IF NOT EXISTS idx_psv_entity ON project_setting_versions(entity_id);
```

### 8.4 `setting_extraction_batches`

保存项目、来源版本、提取范围、目标类型、任务状态、幂等键、模型、Prompt 版本、配置、错误信息和应用时间。

任务状态为：`queued`、`running`、`review_ready`、`partially_failed`、`failed`、`applied`、`cancelled`。

**DDL 参考：**

```sql
CREATE TABLE IF NOT EXISTS setting_extraction_batches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    source_version_id BIGINT,
    chapter_version_ids_json JSON,
    target_setting_types JSON NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(20) DEFAULT 'queued',
    model_id VARCHAR(50),
    prompt_version VARCHAR(20),
    extraction_config_json JSON,
    error_message TEXT,
    applied_at TIMESTAMP NULL,
    applied_by BIGINT,
    revision INT DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_extraction_idempotent UNIQUE (project_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_seb_project ON setting_extraction_batches(project_id);
```

### 8.5 `setting_extraction_candidates`

保存候选数据、证据、置信度、匹配目标、差异状态、用户逐字段决策和审核状态。

**DDL 参考：**

```sql
CREATE TABLE IF NOT EXISTS setting_extraction_candidates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    setting_type VARCHAR(20) NOT NULL,
    canonical_name VARCHAR(200) NOT NULL,
    aliases_json JSON,
    field_values_json JSON NOT NULL,
    evidence_text TEXT,
    evidence_position_json JSON,
    confidence DECIMAL(3,2),
    matched_entity_id BIGINT,
    match_reason TEXT,
    match_status VARCHAR(20) DEFAULT 'new',
    field_decisions_json JSON,
    review_status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_candidate_batch FOREIGN KEY (batch_id) REFERENCES setting_extraction_batches(id)
);
CREATE INDEX IF NOT EXISTS idx_sec_batch ON setting_extraction_candidates(batch_id);
```

### 8.6 现有 `character_profiles` 迁移策略

当前代码库已有 [`character_profiles`](../../../aicp-backend/src/main/resources/db/schema-h2.sql) 表，字段为 `name`、`role`、`archetype`、`appearance`、`personality`、`motivation`、`long_term_goal`、`knowledge_boundary`、`dialogue_style`、`backstory`、`relationships_json`、`status`，**无版本追踪、无 revision 乐观锁、无 uuid**。

**策略**：

1. **新建 `project_setting_entities` 表**，不修改 `character_profiles` 结构。
2. **P1 阶段**：五类设定统一读写 `project_setting_entities`。`character_profiles` 保留但不再写入。
3. **P2 阶段**：提供一次性迁移脚本，将 `character_profiles` 数据转为 `setting_type='character'` 的 `project_setting_entities` 记录，映射规则：
   - `name` → `canonical_name`
   - 各专属字段（`appearance`、`personality` 等）→ `details_json`
   - `relationships_json` → 直接复制
   - `status` → 映射为 `draft`（原 `draft`）或 `confirmed`（其他状态）
4. **迁移后可重复执行**（按 `project_id` 去重，已有记录则跳过）。
5. **`character_profiles` 表保留为只读**，供旧版世界构建视图查询；不再新增或更新。
6. **世界构建功能**（`WorldbuildingService`）逐步切换读取源到 `project_setting_entities`。

## 9. API 设计

### 9.1 编辑器聚合

```http
GET /api/v1/content-projects/{id}/editor
```

返回项目基础信息、权限、资料、各类设定数量、待审核候选数量和修订号，供页面首屏加载。

### 9.2 资料与标签

```http
PUT   /api/v1/content-projects/{id}/tags
PATCH /api/v1/content-projects/{id}/profile
```

标签请求体保持现有语义：

```json
{
  "genre": "言情",
  "plot": ["重生", "先婚后爱"],
  "tone": ["甜宠", "爽文"],
  "setting": "现代",
  "revision": 4
}
```

旧接口继续保留：

```http
PUT /api/v1/script/repo/scripts/{scriptId}/tags
```

旧接口先解析兼容项目，再委托 `ProjectProfileService`；在过渡期同步旧 `scripts` 投影字段。接口不得复制标签校验规则。

**标签字典接口**：

```http
GET /api/v1/tag-dictionary
```

返回四轴标签的有效选项，供前端渲染选择器和后端校验使用。字典数据来源于数据库配置表或受版本控制的配置文件，前后端共享同一来源。响应格式：

```json
{
  "genres": [
    { "value": "言情", "label": "言情" },
    { "value": "悬疑", "label": "悬疑" }
  ],
  "plots": [
    { "value": "重生", "label": "重生" },
    { "value": "先婚后爱", "label": "先婚后爱" }
  ],
  "tones": [
    { "value": "甜宠", "label": "甜宠" },
    { "value": "爽文", "label": "爽文" }
  ],
  "settings": [
    { "value": "现代", "label": "现代" },
    { "value": "古代", "label": "古代" }
  ],
  "version": 1
}
```

前端启动时加载此字典，不再硬编码标签选项。字典 `version` 变更时前端可提示用户刷新。

### 9.3 设定 CRUD

```http
GET    /api/v1/content-projects/{id}/settings
POST   /api/v1/content-projects/{id}/settings
GET    /api/v1/content-projects/{id}/settings/{settingId}
PATCH  /api/v1/content-projects/{id}/settings/{settingId}
DELETE /api/v1/content-projects/{id}/settings/{settingId}
POST   /api/v1/content-projects/{id}/settings/{settingId}/restore
POST   /api/v1/content-projects/{id}/settings/{settingId}/copy
GET    /api/v1/content-projects/{id}/settings/{settingId}/versions
```

列表接口支持 `type`、`status`、`keyword`、分页和更新时间排序。

`POST .../copy` 创建当前设定的完整副本，规范名追加"（副本）"后缀，状态重置为 `draft`，`source_type` 设为 `manual`，版本号从 1 重新计数。

### 9.4 AI 提取

```http
POST /api/v1/content-projects/{id}/setting-extractions
GET  /api/v1/content-projects/{id}/setting-extractions/{batchId}
PUT  /api/v1/content-projects/{id}/setting-extractions/{batchId}/decisions
POST /api/v1/content-projects/{id}/setting-extractions/{batchId}/apply
POST /api/v1/content-projects/{id}/setting-extractions/{batchId}/retry
```

## 10. 校验、权限与错误处理

### 10.1 标签校验

- 题材最多 1 个且必须来自当前有效字典；
- 情节最多 3 个，去重后校验；
- 情绪最多 3 个，去重后校验；
- 时空最多 1 个且必须来自当前有效字典；
- 空数组和空字符串表示清空；
- 前后端共享同一份标签字典：后端通过 `GET /api/v1/tag-dictionary` 提供，前端启动时加载，不再硬编码标签选项。字典 `version` 字段变更时前端提示用户刷新，避免使用过期标签值。

### 10.2 权限

- 查看者：只读；
- 编辑者：编辑资料和设定草稿、创建提取任务、保存审核决策；
- 负责人或所有者：确认批量回写、归档被引用设定、处理版本冲突；
- 所有接口必须先通过 `ProjectAccessService`，旧接口也不得绕过。

### 10.3 并发与冲突

资料、设定和批次决策均使用 `revision` 乐观锁。修订号不匹配返回 HTTP 409 和当前服务端快照，前端重新计算差异，不静默覆盖。

### 10.4 失败处理

- 资源不存在返回明确 404；
- 非法标签或无效决策返回 400；
- 无权限返回 403；
- 编辑冲突返回 409；
- 提取部分失败保留成功候选，并允许按失败部分重试；
- 自动保存失败保留本地修改并提供重试，不将失败状态显示为已保存；
- 已被引用的设定执行归档而非物理删除。

## 11. 仓库联动

P0 恢复仓库真实内容项目列表，至少支持：

- 关键词；
- 四轴标签组合；
- 内容状态；
- 更新时间排序；
- 从项目卡片进入作品编辑中心；
- 编辑标签后刷新或返回仓库可立即命中筛选。

列表接口读取新版项目资料。旧 scripts 数据通过兼容项目投影参与同一列表，不在前端拼接两个来源。

## 12. 分期交付

### P0：作品资料闭环

1. 抽离旧标签组件并建立作品编辑中心外壳。
2. 增加新版编辑路由和旧入口兼容解析。
3. 完成标签、简介、总纲接口与页面。
4. 增加标签字典、合法性校验、权限、404 和 revision。
5. 恢复仓库真实列表、筛选和编辑入口。
6. 为旧 tags 接口增加统一服务委托和安全校验。

P0 通过后，左侧资料区不得再有“待接入”。

### P1：五类设定管理

1. 建立统一设定实体、版本和关系模型。
2. 完成五类设定的列表、详情、创建、复制、归档和恢复。
3. 完成搜索、状态筛选、导航数量和完善状态。
4. 完成权限、乐观锁、引用保护和审计。

P1 通过后，五类设定入口不得再有“待接入”。

### P2：AI 提取与上下文回写

1. 完成按全文、章节和已锁定版本创建异步提取任务。
2. 完成候选、证据、置信度和智能匹配。
3. 完成逐字段三选一、批量接受无冲突项和决策草稿。
4. 完成幂等重试、事务回写、版本与审计。
5. 完成参数快照、上下文刷新事件和下游生成读取。

## 13. 测试与验收

### 13.1 前端单元测试

- 四轴选择和 1/3/3/1 上限；
- 旧值不在当前字典时仍可显示并提示迁移；
- 自动保存队列在连续修改下只提交必要请求；
- 较早请求后返回时不能覆盖较新状态；
- 保存失败与重试；
- 离开保护；
- 五类设定共用列表与类型化详情；
- 差异审核三种决策；
- 409 后重新对比。

### 13.2 后端单元与接口测试

- 所有资料、设定和提取接口的项目权限；
- 标签合法值、数量、去重和清空；
- 不存在资源返回 404；
- revision 冲突返回 409；
- 设定唯一约束和归档恢复；
- 提取任务幂等；
- 部分失败重试；
- 同一批次重复应用不重复创建版本；
- 回写任一步失败时事务回滚；
- 审计信息完整。

### 13.3 集成测试

- 旧 `scriptId` 正确解析到内容项目；
- 旧 tags 接口和新接口读取到同一结果；
- 标签修改后仓库筛选命中；
- 确认设定后生成新的参数版本；
- 上下文组装读取当前正式资料和设定；
- 归档设定不再进入新上下文，但历史生成仍能按版本复现。

### 13.4 端到端验收

1. 从新版项目卡片进入作品编辑中心，完成标签、简介和总纲编辑，刷新后数据不丢失。
2. 从旧标签链接进入同一编辑能力，修改后新版仓库显示一致。
3. 创建五类设定，搜索、复制、归档和恢复均正常。
4. 从指定正文版本提取角色和地点，查看原文证据，完成三种字段决策并确认回写。
5. 回写后重新进入页面，正式设定、版本、审计和参数快照一致。
6. 后续生成请求记录新的参数版本 ID，并能读取已确认设定。
7. 模拟无权限、保存失败、提取失败和并发冲突，页面均给出可恢复路径。

## 14. 迁移与兼容

1. 部署 P0 数据表和接口，但默认不改变旧路由行为。
2. 为已有 scripts 批量建立或复用 `legacy_script_id` 内容项目映射。此步骤通过**可重复执行的迁移脚本**完成：遍历 `scripts` 表，对尚无对应 `content_projects` 记录（按 `legacy_script_id` 判断）的脚本创建项目和资料，已有映射则跳过。
3. 将旧标签、简介和可用总纲投影到项目资料；迁移脚本必须可重复执行。标签值在迁移时不做校验（历史数据可能包含已废弃的标签），仅在用户下次编辑时提示更新。
4. **旧 tags 接口双写策略**：过渡期内，`PUT /scripts/{scriptId}/tags` 同时写入 `scripts` 表（兼容旧字段）和 `content_project_profiles` 表（新真相来源）。灰度验证数据一致后，`scripts` 表标签字段降级为只读投影，不再作为业务真相来源。
5. 新入口灰度到内部用户，比较新旧读取结果。
6. 打开新版入口后，旧 tags 接口改为委托新服务并同步旧字段投影。
7. 数据稳定后，旧字段只保留兼容读取，不再作为业务真相来源。

回滚时关闭新版入口和新写路径，保留旧字段投影；已创建的新表和版本数据不删除，以便恢复发布。

## 15. 完成定义

本方案只有在以下条件全部满足时才视为完成：

- P0、P1、P2 分别通过自己的自动化测试与端到端验收；
- 页面左侧九个入口均可用且无“待接入”；
- 旧链接和旧标签接口仍可工作，并与新项目数据一致；
- AI 不会未经用户确认修改正式设定；
- 确认后的资料和设定可被后续生成链路按版本读取；
- 权限、404、409、幂等、事务回滚和审计均有测试覆盖；
- 仓库标签筛选与作品编辑形成可验证闭环。
