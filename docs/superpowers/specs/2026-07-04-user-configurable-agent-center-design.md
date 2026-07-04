# 用户可配置 Agent 中心设计

> 日期：2026-07-04
>
> 状态：待用户评审
>
> 范围：首期产品与技术设计
>
> 核心角色：钩子 Agent、编剧 Agent、分镜 Agent、导演 Agent

## 1. 结论

平台新增独立的 Agent 配置中心。用户可以新增、修改、复制、试跑、发布和回滚 Agent，但每个新 Agent 必须选择一个系统基础角色框架。系统框架固定该角色的能力边界、工具权限、安全规则、上下文策略和输出协议；用户可以修改名称、用途、结构化业务参数、创作方法 Prompt、示例、适用题材和适用平台。

配置按以下顺序解析：

```text
单次临时参数
  > 项目绑定的发布版本
  > 用户默认发布版本
  > 系统默认发布版本
```

每次正式执行都冻结最终配置快照。发布新版 Agent 不改变历史任务和历史内容；用户选择重跑时必须生成新的剧本、钩子、审核或分镜版本，不得覆盖旧版本。

首期不建设任意工具编排器，也不允许用户脱离四类系统框架创建新的能力类型。

## 2. 当前平台现状与缺口

平台已有零散能力，但尚未形成可用的用户自定义 Agent 系统：

1. `aicp-frontend/src/views/script-gen/components/PromptManager.vue` 已提供 12 个 Prompt 槽位的编辑组件，但没有挂载到当前页面入口。
2. `PromptTemplateController` 能保存用户 Prompt，`prompt_templates` 也有基础版本字段，但剧本、钩子、审核和分镜业务服务没有统一读取这些配置，保存后不能稳定影响实际生成。
3. `ContentHookService`、`ContentReviewService`、`EpisodeReviewService`、`StoryboardService` 等服务仍包含硬编码创作 Prompt。
4. `SkillController` 提供 Skill 增删改查和执行接口，但没有完整前端管理闭环，其松散内容模型不适合承载角色框架、版本发布、作用域绑定和不可变执行快照。
5. `AgentSession.vue` 已有会话界面骨架，但当前 `AgentController` 仍主要使用进程内 Map 和关键词 Mock 计划，未提供用户角色配置入口。
6. 现有设计文档已经规划生产级 Agent 会话、审批和执行，但明确未将 Skill 在线编辑器纳入该会话模块 P0；Agent 配置中心应作为独立领域能力，与会话模块通过版本和执行快照关联。

因此，本方案不继续扩张现有 `prompt_templates`，而是建立独立 Agent 配置模型，并逐步迁移现有四类 Prompt 和业务调用。

## 3. 目标与非目标

### 3.1 首期目标

1. 用户可以基于四个系统基础框架新增任意数量的个人 Agent。
2. 普通用户通过结构化业务参数配置 Agent，专业用户可以编辑高级 Prompt。
3. Agent 支持草稿、校验、试跑对比、发布、归档和回滚。
4. 支持用户默认、项目默认和单次临时调整。
5. 项目管理员或导演可以发布项目级绑定；普通成员只能使用或进行不留存的单次调整。
6. 剧本、钩子、分镜和导演四条业务链路真实读取已解析的 Agent 配置。
7. 每次执行均可追溯到系统框架、用户 Agent、发布版本、临时参数、项目上下文、模型和最终 Prompt 快照。
8. 所有 AI 正文或分镜修改先产生预览或 Diff，经用户确认后创建新业务版本。

### 3.2 首期非目标

1. 不支持用户定义新工具、外部 API、数据库权限或上下文读取范围。
2. 不支持脱离四类基础框架创建完全空白的能力类型。
3. 不建设任意节点式多 Agent 工作流。
4. 不允许用户覆盖安全规则、工具白名单和结构化输出协议。
5. 不将图片、视频、配音模型市场纳入本期 Agent 配置。
6. 不让新版 Agent 自动重跑或覆盖历史内容与资产。
7. 不用 Agent 配置中心替代生产级 Agent 会话、审批和长任务编排模块。

## 4. 领域术语和对象模型

### 4.1 系统基础框架 `AgentBlueprint`

系统维护的角色能力合同。首期内置：

- `HOOK`：钩子生成、钩子分析和钩子审核。
- `SCREENWRITER`：大纲、分集、正文生成和编剧修订。
- `STORYBOARD`：A/B/C 档分镜生成和镜头策略。
- `DIRECTOR`：节奏、画面、可拍性和导演审核。

Blueprint 定义：

- 角色类型与能力说明。
- 结构化参数 Schema 和默认值。
- 平台锁定 System Prompt。
- 用户可编辑 Prompt 的允许区块和变量。
- 输入、上下文和输出 Schema。
- 工具白名单与风险等级。
- 支持的模型策略和长度限制。
- Blueprint 版本。

### 4.2 用户 Agent `UserAgent`

用户点击“新增 Agent”后创建的独立对象。每个 UserAgent 必须绑定一个 Blueprint，但具有自己的：

- 名称、图标、描述和用途。
- 适用题材、平台和项目类型。
- 所有者和可见性。
- 多个草稿与已发布版本。
- 用户默认或项目默认绑定关系。

用户可以复制已有 UserAgent，但复制后产生新的独立 UserAgent，而不是共享可变配置。

### 4.3 Agent 版本 `AgentVersion`

AgentVersion 保存一次完整配置。状态为：

```text
DRAFT -> PUBLISHED -> ARCHIVED
```

约束：

- 已发布版本不可原地修改。
- 编辑已发布 Agent 时创建新的 DRAFT。
- 发布前必须通过校验，并至少完成一次成功试跑。
- 回滚不修改历史版本，而是重新激活指定历史发布版本。
- 已被项目或执行快照引用的版本只能归档，不能物理删除。

### 4.4 绑定 `AgentBinding`

绑定用于声明某个作用域内某个角色的默认发布版本：

- 用户默认：用户所有项目的缺省选择。
- 项目默认：覆盖用户默认，由项目管理员或导演维护。
- 单次调整：只存在于当前任务请求和执行快照中，不修改默认绑定。

同一角色、同一作用域只能有一个当前生效绑定。

“发布 Agent 版本”和“设为项目正式 Agent”是两个动作：Agent 所有者负责把个人草稿发布成不可变版本；项目管理员或导演负责审查并将某个发布版本绑定为项目正式配置。项目绑定会授予项目成员对该固定版本的只读使用权，但不会授予编辑权。

### 4.5 执行快照 `AgentExecutionSnapshot`

正式任务开始前，由服务端解析继承关系并冻结：

- Blueprint ID 和版本。
- UserAgent ID 和 AgentVersion ID。
- 结构化参数和临时覆盖参数。
- 平台锁定 Prompt、用户 Prompt 和最终编译 Prompt。
- 项目上下文版本与哈希。
- 输出 Schema 版本、工具集合和模型策略。
- 发起用户、任务、Token、成本和时间。

执行重试必须复用原快照，不能在重试中自动切换到新版 Agent。

## 5. 四类 Agent 的用户可配置能力

### 5.1 钩子 Agent

结构化参数包括：

- 开场抓人时间。
- 钩子密度。
- 信息差、秘密、危机和身份反差偏好。
- 中段升级强度。
- 反转频率和强度。
- 章尾悬念强度。
- 下一集承诺强度。
- 钩子最低通过分。
- 平台和受众偏好。

接管功能：重新下钩子、开场/中段/结尾钩子生成、钩子评分、联合审核中的钩子部分。

### 5.2 编剧 Agent

结构化参数包括：

- 修订强度：保守、平衡、重写。
- 单集目标长度和时长。
- 对白密度和语言风格。
- 冲突升级速度。
- 情绪曲线和关系变化强度。
- 人物一致性要求。
- 信息密度和留白比例。
- 禁改项和必须保留项。

接管功能：大纲、分集、正文初稿、选中文本润色、压缩、扩写、对白重写、冲突增强及按审核意见修订。

### 5.3 分镜 Agent

结构化参数包括：

- 分镜档位：A、B、C。
- 镜头密度和平均镜头时长。
- 景别分布、构图和视线规则。
- 运镜复杂度和切镜节奏。
- 动作拆分粒度。
- 人物、场景和道具连续性要求。
- 场景合并与低成本策略。
- 竖屏或横屏画面约束。

接管功能：A 档拆镜、B 档导演意图、C 档生产字段，以及从锁定剧本生成新分镜版本。

### 5.4 导演 Agent

结构化参数包括：

- 视觉风格和镜头语言偏好。
- 节奏与情绪判断标准。
- 可拍性和预算约束。
- 场景、群演、特效和复杂运镜限制。
- 审核通过阈值。
- 问题严重度分级。
- 建议模式或生成修订 Patch 模式。

接管功能：锁稿前导演审核、视觉化检查、可拍性判断、导演修改建议，以及面向编剧或分镜 Agent 的结构化问题清单。

## 6. Prompt 分层与安全边界

最终 Prompt 由服务端编译，分为三层：

```text
平台锁定层
  - 工具权限
  - 安全规则
  - 输入与输出 Schema
  - 审计与数据边界

用户可编辑层
  - 角色方法
  - 判断标准
  - 创作风格
  - 任务指令
  - 变量和 Few-shot 示例

运行时上下文层
  - 创作圣经
  - 当前剧本或分镜
  - 用户选中范围
  - 项目约束
  - 单次临时参数
```

前端不能提交最终 System Prompt。前端只提交 Agent、版本、允许的临时参数和业务上下文标识；后端负责解析、编译、校验和冻结快照。

用户 Prompt 中若出现未声明变量、试图调用未授权工具、突破上下文范围或覆盖输出协议的内容，则校验失败，不能发布。

## 7. Agent 配置中心

### 7.1 页面布局

配置中心采用三栏结构：

- 左栏：Blueprint 角色筛选、状态筛选和“新增 Agent”。
- 中栏：用户 Agent 列表，展示角色、当前发布版本、草稿状态、使用项目和更新时间。
- 右栏：Agent 详情及编辑区。

详情区包含：

- 基础设置。
- 方法参数。
- 高级 Prompt。
- 示例输入与理想输出。
- 试跑对比。
- 版本记录和版本 Diff。
- 使用项目和默认绑定。
- 系统锁定内容的只读说明。

### 7.2 新增 Agent 向导

新增流程固定为四步：

1. 选择钩子、编剧、分镜或导演基础框架。
2. 定义名称、用途、题材、平台和说明。
3. 配置结构化参数、高级 Prompt 和示例。
4. 运行试跑、填写版本说明、发布并选择默认作用域。

### 7.3 双层编辑

- 普通模式：仅展示业务参数、滑杆、选项和解释，由平台转换成用户 Prompt 区块。
- 高级模式：允许直接编辑角色方法、判断标准、风格、变量和示例；平台锁定层只读。

两种模式操作同一个 AgentVersion，不能形成两套互相覆盖的配置源。

### 7.4 业务页面轻量入口

剧本、钩子、分镜和导演页面只显示：

- 当前 Agent 名称和发布版本。
- 配置来源：系统、用户或项目。
- 切换 Agent。
- 单次参数调整。
- 查看执行快照。
- 前往配置中心管理。

业务页面不复制完整 Agent 编辑器。

## 8. 剧本创作模块的新增和改造

### 8.1 改造后的协作流程

```text
创作圣经与项目约束
  -> 编剧 Agent 生成大纲、分集或正文
  -> 钩子 Agent 生成或审核钩子
  -> 导演 Agent 检查节奏、视觉化和可拍性
  -> 用户查看 Diff 或候选结果
  -> 用户确认
  -> 创建新剧本版本
  -> 锁稿后选择分镜 Agent
```

平台为每类任务规定固定协作顺序。用户可以跳过非必需审核，但不能自定义工具编排。

### 8.2 新增前端功能

1. 项目 Agent 设置：为项目绑定编剧、钩子和导演 Agent。
2. 任务级 Agent 选择器：在生成、重做、修订或审核前切换 Agent。
3. Agent 协作侧栏：展示角色、版本、上下文来源、执行步骤、成本和结果摘要。
4. 修订 Diff：显示原文、建议稿、原因和影响范围，支持逐段接受或拒绝。
5. Agent 结果对比：同一输入试跑两个 Agent 或版本，比较结果、评分、成本和耗时。
6. 执行来源：内容版本可查看所用 Agent、发布版本、临时参数和执行快照。

### 8.3 现有功能改造

#### 项目创建与创作首页

保留当前创作模式和源文本选择，新增项目默认 Agent 摘要。完整配置放在项目设置中，避免创建首屏过载。

#### 创作圣经、设定与写作指南

继续作为项目事实源，新增 Agent 可见范围和锁定字段。Agent 能读取，但不能未经确认修改人物核心设定、世界观规则和锁定字段。

#### 大纲和分集

编剧 Agent 负责结构，钩子 Agent 负责每集开场、升级点、章尾和下一集承诺。结果保存两个 Agent 的执行快照引用。

#### 正文编辑器

新增选中文本操作：润色、压缩、扩写、重写对白、增强冲突、增强钩子、按导演意见修订。所有输出统一进入 Patch 预览，不能直接覆盖正文。

#### 钩子面板

从只展示分析结果升级为操作工作台：选择钩子 Agent、锁定满意字段、单独重做某类钩子、查看版本对比、将钩子建议转换为正文 Patch。

#### 联合审核

钩子和导演的 Prompt、阈值和重点由 AgentVersion 决定；总流程、权重上限和输出 Schema 由平台控制。审核问题可以创建编剧修订任务。

#### 版本和锁稿

应用 Agent Patch 后立即创建新内容版本，记录 `base_revision`、执行快照和差异摘要。已锁版本不能原地修改，只能派生新草稿。

#### 进入分镜

剧本审核通过后增加分镜 Agent 选择步骤。锁定剧本版本、创作圣经和导演意见作为只读输入，生成新的分镜版本。

### 8.4 典型任务参与角色

| 用户操作 | 主 Agent | 辅助 Agent | 产物 |
|---|---|---|---|
| 生成分集大纲 | 编剧 | 钩子 | 大纲草稿和每集钩子结构 |
| 生成单集正文 | 编剧 | 钩子，可选 | 正文草稿和钩子评分 |
| 重新下钩子 | 钩子 | 编剧在应用时参与 | 候选钩子和正文 Patch |
| 按审核意见修订 | 编剧 | 导演提供问题清单 | 逐段 Diff 和新正文版本 |
| 锁稿前审核 | 导演 | 钩子 | 审核报告和可执行修订项 |

## 9. 其他现有功能改造范围

### 9.1 专业分镜

- A/B/C 档生成读取项目或任务选择的分镜 Agent。
- 镜头密度、时长、构图、运镜、连续性和成本策略来自 AgentVersion。
- 分镜任务保存锁定剧本版本和 AgentExecutionSnapshot。
- Agent 建议生成新分镜版本，不能覆盖锁定版本。

### 9.2 导演审核

- 导演审核读取导演 Agent 的已发布版本。
- 输出保持平台 Schema：评分、问题、严重度、建议和可执行修订项。
- 用户选择“应用建议”时，根据目标创建编剧或分镜修订任务。

### 9.3 Agent 会话

- 会话上下文显示当前项目四类 Agent 绑定。
- 计划生成和任务执行保存 AgentVersion 和执行快照。
- 会话模块不负责编辑 Agent，只跳转到配置中心。

### 9.4 旧 Prompt 管理器

- 四类角色相关 Prompt 迁移到 UserAgent 和 AgentVersion。
- 图片、视频、配音等非本期模板继续保留在原 Prompt 模板体系。
- 四条业务链路完成切换后，下线旧 PromptManager 的角色配置入口。
- 历史 `prompt_templates` 数据不物理删除。

### 9.5 Skill 管理

Skill 继续作为可执行能力或未来市场化单元，不作为首期用户 Agent 的配置事实源。Blueprint 可以在未来引用受控 Skill，但 UserAgent 不能直接注册新工具。

## 10. 总体架构

```text
Agent 配置中心 / 项目 Agent 设置 / 业务任务选择器
                         |
                  AgentConfig API
                         |
  +----------------------+-----------------------+
  |                      |                       |
BlueprintService   AgentVersionService    AgentBindingService
  |                      |                       |
  +----------------------+-----------------------+
                         |
                AgentConfigResolver
                         |
                 PromptCompiler
                         |
             AgentExecutionSnapshotService
                         |
                     AiRouter
                         |
          Schema 校验 / 预览 / Diff / 审批
                         |
     剧本 / 钩子 / 分镜 / 导演领域服务与版本系统
```

### 10.1 模块职责

- `BlueprintService`：读取系统角色框架和版本，禁止用户修改锁定字段。
- `UserAgentService`：新增、修改元数据、复制和归档用户 Agent。
- `AgentVersionService`：草稿、校验、发布、归档、版本 Diff 和回滚。
- `AgentBindingService`：用户和项目默认绑定、权限、唯一性和乐观锁。
- `AgentTestRunService`：试跑、A/B 对比、成本和结果记录。
- `AgentConfigResolver`：根据用户、项目、角色和单次参数解析最终配置来源。
- `PromptCompiler`：组合锁定层、用户层和运行时上下文，校验变量与长度。
- `AgentExecutionSnapshotService`：在执行前保存不可变快照并向业务任务返回引用。

业务领域服务只能传入角色类型、资源标识和业务上下文，不能继续自行维护用户可调的创作 Prompt。

## 11. 数据模型

### 11.1 `agent_blueprints`

- `id`, `uuid`, `role_type`, `name`, `description`。
- `parameter_schema_json`, `default_parameters_json`。
- `locked_system_prompt`, `editable_prompt_template`。
- `input_schema_json`, `output_schema_json`。
- `allowed_tools_json`, `context_policy_json`, `model_policy_json`。
- `blueprint_version`, `status`, `created_at`, `updated_at`。

`role_type + blueprint_version` 唯一。已被引用的 Blueprint 版本不可覆盖。

### 11.2 `user_agents`

- `id`, `uuid`, `blueprint_id`, `owner_user_id`。
- `name`, `description`, `icon`, `applicable_genres_json`, `platforms_json`。
- `visibility`：首期为 `PRIVATE`，预留 `TEAM`。
- `lifecycle_status`：`ACTIVE | ARCHIVED`。
- `current_published_version_id`, `row_version`。
- `created_at`, `updated_at`。

### 11.3 `agent_versions`

- `id`, `uuid`, `user_agent_id`, `version_no`。
- `parameters_json`, `editable_prompt`, `examples_json`, `model_policy_json`。
- `status`：`DRAFT | PUBLISHED | ARCHIVED`。
- `change_summary`, `content_hash`。
- `created_by`, `published_by`, `published_at`。
- `row_version`, `created_at`, `updated_at`。

`user_agent_id + version_no` 唯一。

### 11.4 `agent_bindings`

- `id`, `uuid`, `scope_type`：`USER | PROJECT`。
- `scope_id`, `role_type`, `user_agent_id`, `agent_version_id`。
- `created_by`, `updated_by`, `row_version`。
- `created_at`, `updated_at`。

`scope_type + scope_id + role_type` 唯一。

### 11.5 `agent_test_runs`

- `id`, `uuid`, `agent_version_id`。
- `input_snapshot_json`, `context_snapshot_json`。
- `output_json`, `output_schema_valid`。
- `model_id`, `prompt_tokens`, `completion_tokens`, `credit_cost`, `duration_ms`。
- `status`, `error_code`, `error_message`, `created_by`, `created_at`。

### 11.6 `agent_execution_snapshots`

- `id`, `uuid`, `blueprint_id`, `blueprint_version`。
- `user_agent_id`, `agent_version_id`, `binding_source`。
- `resolved_parameters_json`, `temporary_overrides_json`。
- `resolved_prompt`, `prompt_hash`, `output_schema_version`。
- `project_id`, `context_hash`, `context_refs_json`。
- `business_task_type`, `business_task_id`, `model_id`。
- `created_by`, `created_at`。

历史快照保存复现所需内容，但不得保存密钥、完整模型隐藏推理或无关个人数据。

## 12. API 设计

### 12.1 Blueprint 和 UserAgent

- `GET /api/v1/agent/blueprints`
- `GET /api/v1/agent/blueprints/{id}`
- `POST /api/v1/agent/definitions`
- `GET /api/v1/agent/definitions`
- `GET /api/v1/agent/definitions/{id}`
- `PATCH /api/v1/agent/definitions/{id}`
- `POST /api/v1/agent/definitions/{id}/copies`
- `POST /api/v1/agent/definitions/{id}/archive`

`definitions` 表示用户新增的 Agent，避免与现有会话 `/agent/sessions` 混淆。

### 12.2 版本和试跑

- `GET /api/v1/agent/definitions/{id}/versions`
- `POST /api/v1/agent/definitions/{id}/drafts`
- `GET /api/v1/agent/versions/{versionId}`
- `PUT /api/v1/agent/versions/{versionId}`
- `POST /api/v1/agent/versions/{versionId}/validate`
- `POST /api/v1/agent/versions/{versionId}/test-runs`
- `POST /api/v1/agent/versions/{versionId}/publish`
- `POST /api/v1/agent/versions/{versionId}/activate`
- `GET /api/v1/agent/test-runs/{id}`

### 12.3 绑定和解析预览

- `PUT /api/v1/agent/user-bindings/{roleType}`
- `DELETE /api/v1/agent/user-bindings/{roleType}`
- `GET /api/v1/projects/{projectId}/agent-bindings`
- `PUT /api/v1/projects/{projectId}/agent-bindings/{roleType}`
- `DELETE /api/v1/projects/{projectId}/agent-bindings/{roleType}`
- `POST /api/v1/agent/resolve-preview`
- `GET /api/v1/agent/execution-snapshots/{id}`

### 12.4 业务 API 调整

剧本、钩子、审核和分镜任务请求增加可选字段：

```json
{
  "agent_id": "agent_xxx",
  "agent_version_id": "agent_ver_xxx",
  "temporary_overrides": {}
}
```

如果未传，服务端根据项目、用户和角色解析。前端不得提交 `resolved_prompt`。

## 13. 核心流程

### 13.1 新增和发布 Agent

1. 用户选择 Blueprint。
2. 服务端创建 UserAgent 和第一个 DRAFT。
3. 用户编辑参数、Prompt 和示例。
4. 服务端校验参数 Schema、变量、长度、安全边界和输出样例。
5. 用户使用真实或脱敏样例试跑。
6. 至少一次试跑成功后，用户填写版本说明并发布。
7. 用户可将该发布版本设为用户默认；项目管理员或导演可将其设为项目默认。

### 13.2 正式业务执行

1. 业务服务提交角色类型、用户、项目、业务资源和可选临时参数。
2. Resolver 按优先级选择 AgentVersion。
3. 校验用户能否使用该 Agent、项目能否读取、版本是否可用。
4. PromptCompiler 合并三层 Prompt 并校验。
5. 写入 AgentExecutionSnapshot。
6. AiRouter 使用快照调用模型。
7. 输出按 Blueprint Schema 校验；格式错误最多修复一次。
8. 返回预览、Diff、候选结果或审核报告。
9. 用户确认后，领域服务以 revision/hash 校验目标资源并创建新业务版本。

### 13.3 回滚

1. 用户选择一个历史 PUBLISHED 版本。
2. 服务端校验该版本仍兼容当前 Blueprint 和模型策略。
3. 将其设为 UserAgent 当前发布版本。
4. 相关用户或项目绑定继续指向明确版本时保持不变；选择“同步更新绑定”必须单独确认。
5. 历史执行不变。

## 14. 权限和审计

### 14.1 权限

- UserAgent 所有者可新增、编辑草稿、试跑、发布、回滚和归档自己的 Agent。
- 项目管理员或导演可审查发布版本并设置项目 Agent 绑定，使其成为项目正式配置。
- 普通项目成员可使用项目绑定或做允许范围内的单次调整，不能发布或改项目默认。
- 项目成员因项目绑定获得对该固定 AgentVersion 的只读执行权限；Agent 的所有权和编辑权限不随绑定转移。
- 使用他人 Agent 的共享与市场能力不属于首期；数据模型预留可见性字段。
- 每次读取业务上下文和执行工具时重新校验 Workspace、项目和资源权限。

### 14.2 审计

必须记录：

- UserAgent 新增、复制、重命名和归档。
- AgentVersion 校验、发布、激活和归档。
- 用户默认和项目默认绑定变化。
- 试跑输入摘要、结果、成本和操作者。
- 正式执行快照、业务结果引用、审批和版本应用。

审计不保存供应商密钥、完整敏感正文副本或模型隐藏推理。

## 15. 错误处理

### 15.1 可自动处理

- 没有用户或项目绑定时使用系统默认发布版本。
- 模型输出不符合 Schema 时，使用同一执行快照自动修复一次。
- 可重试网络错误复用原执行快照和幂等键重试。

### 15.2 必须停止并提示

- UserAgent 或 AgentVersion 无访问权限。
- 已明确绑定的 AgentVersion 被归档、损坏或与 Blueprint 不兼容。
- Prompt 使用非法变量、越权工具或突破上下文范围。
- 参数或输出 Schema 校验失败。
- 项目绑定发生 row_version 冲突。
- 预览后正文或分镜 revision/hash 已变化。
- 自动修复一次后输出仍不合格。

当用户或项目已明确绑定 Agent 时，系统不能静默切换到其他 Agent，否则结果不可解释。此时必须失败并要求重新选择或修复绑定。

## 16. 现有代码和功能影响清单

### 16.1 前端新增

- Agent 配置中心页面与路由。
- Agent 新增四步向导。
- 结构化参数与高级 Prompt 编辑器。
- 试跑对比、版本 Diff、发布和回滚页面。
- 项目 Agent 设置组件。
- 业务任务 Agent 选择器。
- 执行快照查看器。
- 剧本修订 Diff 和逐段确认。

### 16.2 前端改造

- `aicp-frontend/src/views/content-project/ScriptCreationHome.vue`
- `aicp-frontend/src/views/content-project/ContentProjectWorkspace.vue`
- `aicp-frontend/src/views/content-project/components/ContextPanel.vue`
- 工作编辑器和钩子相关面板。
- 专业分镜编辑器和生成入口。
- `aicp-frontend/src/views/agent/AgentSession.vue`
- `aicp-frontend/src/api/agent.js`
- `aicp-frontend/src/router/index.js` 与侧边栏入口。
- 旧 `PromptManager.vue` 的角色配置入口和数据迁移提示。

### 16.3 后端新增

- Blueprint、UserAgent、AgentVersion、AgentBinding、AgentTestRun、AgentExecutionSnapshot 实体、Mapper、Service 和 Controller。
- AgentConfigResolver。
- PromptCompiler。
- Agent 发布状态机和版本 Diff。
- 试跑与 A/B 对比服务。
- 项目绑定权限和审计。
- 数据库迁移脚本及四类 Blueprint 种子数据。

### 16.4 后端改造

- `ScriptGenService` 和当前内容生成执行器：使用编剧 Agent 快照。
- `HookService`、`ContentHookService`：使用钩子 Agent 快照。
- `EpisodeReviewService`、`ContentReviewService`：使用钩子和导演 Agent 快照。
- `StoryboardService` 和专业分镜生成链路：使用分镜 Agent 快照。
- Agent 会话计划和执行：引用项目绑定与执行快照。
- 生成任务：增加 Agent、版本和执行快照引用。
- 内容版本和分镜版本：增加 Agent 来源和 base revision/hash。
- PromptTemplateController：迁移后只保留非四类角色的旧模板用途。

### 16.5 首期不改

- 仓库归档、交易市场、版权和授权流程。
- 源文件上传格式。
- 图片、视频和配音模型市场。
- 视频剪辑或多轨时间线。
- 任意工具和第三方 API 注册。

## 17. 迁移策略

### M1：基础能力

- 新建六张核心表和服务。
- 写入四类 Blueprint 和系统默认版本。
- 完成配置中心、新增向导、版本发布和用户默认绑定。
- Resolver 在没有用户配置时返回系统默认版本。

### M2：剧本与钩子

- 接入剧本生成、正文修订和钩子链路。
- 新增项目 Agent 设置、任务选择器和 Diff 审批。
- 将旧 `prompt_templates` 中对应的用户内容迁为 UserAgent v1 草稿。
- 迁移后保留旧数据，只停止新写入对应角色槽位。

### M3：分镜与导演

- 接入 A/B/C 分镜和导演审核。
- 增加分镜版本与执行快照关联。
- 完成锁稿到分镜的 Agent 选择流程。

### M4：收口与灰度

- 下线旧 PromptManager 的四类角色入口。
- 检查代码中四条核心链路的硬编码创作 Prompt。
- 灰度启用项目 Agent 绑定。
- 演练发布失败、版本回滚、绑定冲突和历史任务重试。

迁移期间允许 Resolver 使用系统 Blueprint 兼容默认行为，但不允许存在两个可同时修改且优先级不明确的配置源。

## 18. 测试方案

### 18.1 单元测试

- 配置继承优先级。
- 参数 Schema、变量和 Prompt 编译。
- 锁定字段不能被覆盖。
- 版本状态机和发布前置条件。
- 用户与项目绑定唯一性。
- 权限和作用域策略。
- 执行快照不可变性。

### 18.2 集成测试

- 新增 Agent -> 编辑草稿 -> 试跑 -> 发布 -> 绑定 -> 正式执行 -> 回滚。
- 并发发布和 row_version 冲突。
- 项目越权和使用他人 Agent。
- 已归档版本、损坏绑定和 Blueprint 不兼容。
- 正文或分镜 revision 冲突。
- 输出 Schema 自动修复一次后成功或失败。

### 18.3 业务契约测试

- 四类 Agent 的输入与输出符合 Blueprint Schema。
- 切换 AgentVersion 后，业务服务使用新的执行快照。
- 没有绑定时使用系统默认版本。
- 明确绑定失效时停止，不静默降级。
- 新版 Agent 不改变历史任务结果。

### 18.4 端到端测试

- Agent 配置中心和新增向导。
- 普通参数与高级 Prompt 双向一致。
- 试跑对比、发布、回滚和版本 Diff。
- 项目绑定权限。
- 剧本修订 Diff 和逐段确认。
- 钩子单项重做。
- 分镜生成 Agent 选择。
- 执行快照和历史追溯。

## 19. 验收标准

1. 用户能够从四类 Blueprint 新增任意数量的独立 Agent。
2. 每个 Agent 支持多个不可变发布版本和一个可编辑草稿。
3. 普通参数和高级 Prompt 修改的是同一个 AgentVersion。
4. 草稿未通过校验或没有成功试跑时不能发布。
5. 用户默认、项目默认和单次覆盖按固定优先级生效。
6. 普通项目成员不能修改或发布项目默认 Agent。
7. 剧本、大纲、钩子、导演审核和分镜任务真实使用选中的 AgentVersion。
8. 任何 AI 正文或分镜修改都先预览，用户确认后创建新版本。
9. 任务和内容版本可以查看完整 Agent 来源与执行快照。
10. 发布新版 Agent 不影响历史任务；重跑生成新业务版本。
11. 四条核心业务链路不再直接使用用户可调的硬编码创作 Prompt。
12. 旧 Prompt 数据迁移后可追溯，且平台不存在两个同时生效的角色配置源。

## 20. 后续扩展边界

首期稳定后可以按新的 Blueprint 增加：

- 连续性质检 Agent。
- 对白润色 Agent。
- 小说改编 Agent。
- 制片成本 Agent。
- 平台合规 Agent。
- 投流素材 Agent。

这些扩展仍先由平台定义 Blueprint，再允许用户新增该类型的 Agent。只有在工具权限、租户隔离、审批和审计能力成熟后，才评估开放通用空白框架或第三方工具注册。
