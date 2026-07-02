# Agent 会话模块完善设计

> 日期：2026-07-02
>
> 状态：已评审
>
> 范围：P0 可用闭环
>
> 产品定位：绑定项目上下文的生产副驾

## 1. 背景与结论

现有 Agent 会话模块只能用于演示，不能承担生产任务：前端不加载会话和历史消息；后端以进程内 `Map` 保存数据，通过关键词生成 Mock 计划；`AgentSession`、`AgentMessage`、`AgentExecution` 实体和 Mapper 未进入业务链路；没有真实 AI Router 规划、计划审批、异步执行、事件流、项目权限、计费结算和审计恢复。

首期采用“现有单体内垂直闭环”：在当前 Spring Boot 应用内建立独立 Agent 领域层，复用认证、Workspace、内容项目、画布、生成任务、资产、AI Router 和积分能力。暂不拆分独立 `agent-svc`，但通过应用服务和 Tool Adapter 保持未来可拆分边界。

## 2. 目标与非目标

### 2.1 P0 目标

1. 会话、消息、计划、步骤、执行和事件全部持久化，刷新或重新登录后可恢复。
2. 会话绑定 Workspace 和项目，可选绑定内容单元、画布及选中节点。
3. 通过 `AiRouter` 调用 new-api，生成用户可见回复和结构化执行计划。
4. 只读工具可直接执行；修改项目、触发生成和产生费用的工具必须先审批。
5. 执行支持异步运行、进度事件、暂停、取消、失败重试和部分失败恢复。
6. 每次执行可追溯到用户指令、上下文版本、计划版本、审批、工具调用、产物和积分。
7. 前端提供会话列表、消息流、计划审批卡、上下文面板和执行时间线。

### 2.2 非目标

P0 不建设跨项目长期向量记忆、多 Agent 自主协商、Skill 在线编辑器、开放式 Tool Router、独立消息总线或独立微服务。P0 只预留扩展字段和接口边界，不实现这些能力。

## 3. 产品原则

- **项目优先**：Agent 是项目生产副驾，不是无上下文的通用聊天机器人。
- **模型决策、平台执行**：模型只能输出回复、计划和受限工具调用；业务读写只能通过平台 Tool Adapter。
- **分级确认**：查询和分析可即时执行；写入、生成、扣费必须展示影响、风险和成本后确认。
- **可恢复**：页面断线不终止服务端任务；所有写步骤具备幂等键和检查点。
- **可解释但不泄露隐藏推理**：展示上下文来源、选择依据、工具摘要、结果和错误，不展示模型隐藏思维链。
- **最小权限**：每次读取和执行都重新校验 Workspace、项目和动作权限，不能信任前端传入的身份信息。

## 4. 现状缺口

| 层级 | 当前状态 | P0 改造 |
|---|---|---|
| 前端 | 只创建会话和发送消息；不加载列表、详情、历史或执行记录 | 完整三栏工作台和可恢复状态 |
| API | `GET /sessions` 未被前端调用；返回结构依赖松散 `Map` | 强类型 DTO、游标分页、幂等请求和统一错误码 |
| 会话 | 进程内 `Map`，重启即丢失 | 数据库作为唯一事实源 |
| 规划 | 关键词匹配生成固定步骤 | AI Router 输出受 JSON Schema 约束的计划 |
| 执行 | `executions` 永远返回空列表 | 异步编排器、步骤状态机和 Tool Adapter |
| 权限 | 未绑定当前用户或 Workspace | `SecurityUtil` + `WorkspaceAccessService` + 项目权限校验 |
| 成本 | 按步骤数返回 Mock 积分 | 工具级预估、额度预占、结算和释放 |
| 实时性 | 一次性 HTTP 响应 | SSE 业务事件流和断点续传 |
| 安全审计 | 无审批证据、无幂等、无完整审计 | 版本化审批、事件表、执行摘要和资源引用 |

## 5. 总体架构

```text
Vue Agent 工作台
  ├─ REST：会话、消息、计划审批、运行控制
  └─ SSE：消息增量、计划、步骤、成本和错误事件
              │
AgentController（仅处理协议与校验）
              │
Agent 应用层
  ├─ AgentSessionService
  ├─ AgentContextAssembler
  ├─ AgentPlanner
  ├─ AgentApprovalPolicy
  ├─ AgentExecutionOrchestrator
  └─ AgentEventPublisher
       │                 │
       │                 └─ AiRouter → new-api
       │
AgentToolRegistry
  ├─ READ adapters：项目、写作版本、画布、节点、资产
  └─ WRITE/BILLABLE adapters：应用写作 Patch、更新节点、创建生成任务、质量检查、产物回写
       │
现有项目 / 写作 / 画布 / 生成 / 资产 / 积分 / 通知模块
```

### 5.1 模块职责

- `AgentSessionService`：会话生命周期、归属校验、标题、历史消息和归档。
- `AgentContextAssembler`：按会话绑定和用户权限组装最小必要项目上下文，生成来源引用与上下文版本哈希。
- `AgentPlanner`：调用 `AiRouter.chatCompletion`，校验结构化计划，最多进行一次格式修复；不能直接执行工具。
- `AgentApprovalPolicy`：根据工具的 `READ`、`WRITE`、`BILLABLE` 风险级别判断是否需要审批，并验证审批是否仍有效。
- `AgentExecutionOrchestrator`：按依赖顺序调度步骤、写入检查点、处理暂停/取消/重试和部分失败。
- `AgentToolRegistry`：维护工具白名单、输入 Schema、风险、权限、估价器、超时和重试策略。
- `AgentEventPublisher`：事务提交后写入事件，并向 SSE 订阅者推送；数据库事件是事实源，内存连接不是事实源。

### 5.2 与写作模块、画布模块的依赖边界

Agent 与写作、画布是业务强联动，但必须保持代码级松耦合。依赖方向固定为：

```text
写作模块公开 Facade / Command ── WritingToolAdapter ──┐
                                                      ├── AgentToolRegistry ── Agent 应用层
画布模块公开 Facade / Command ── CanvasToolAdapter ───┘
```

约束如下：

1. Agent 模块不得注入写作或画布的 Mapper，不得直接读写对方数据表。
2. Agent 通过稳定的 `WritingAgentFacade`、`CanvasAgentFacade` 读取上下文和提交命令；Facade 内部复用现有领域服务并完成权限、revision、资源归属和状态校验。
3. 写作与画布后端不反向依赖 Agent 领域代码。前端入口只负责携带上下文打开会话；业务结果通过资源引用和领域事件回写。
4. Tool Adapter 负责把 Agent 的通用工具协议转换为领域 Command，不把写作或画布内部实体暴露给模型。
5. 写作与画布模块可以独立演进；只要 Facade 契约不变，内部表结构、Service 或页面改造不应影响 Agent。

### 5.3 写作联动

写作联动覆盖项目资料、创作圣经、内容单元、当前草稿、命名版本和用户选中文本。P0 使用现有 `WorkEditorService`、`ContentUnitService` 和版本模型提供能力，但新增面向 Agent 的窄 Facade，禁止直接复用面向页面的聚合 DTO 作为长期工具契约。

- **读取**：按用户授权范围返回项目资料、内容单元元数据、当前草稿摘要、指定版本、选中文本及前后有限窗口，并附 `content_unit_id`、`version_id`、`revision` 和 `content_hash`。
- **建议**：Agent 生成结构化 Patch，只包含目标范围、原文哈希、替换内容、修改原因和影响提示；建议阶段不修改正文。
- **预览**：服务端基于当前 revision 应用 Patch 到临时副本，返回统一 diff。前端展示逐段接受/拒绝，但一次审批最终形成一个确定的 Patch 集合。
- **应用**：审批后调用写作 Facade，以 `base_revision + base_content_hash` 做乐观锁；成功后保存草稿并立即创建来源为 `agent_edit` 的命名版本，记录 `agent_plan_id` 和操作者。
- **冲突**：用户在预览后继续编辑导致 revision/hash 变化时，旧审批失效。系统重新生成 diff，不得自动覆盖或尝试模糊合并。
- **锁稿边界**：已审核或锁定版本不可被 Agent 原地修改。Agent 只能从该版本创建新草稿/修订版本，再走现有审核与锁稿流程。

### 5.4 画布联动

画布联动覆盖画布项目、节点、连线、分组、分镜、选中范围、生成参数、任务状态和产物引用。P0 通过 `CanvasAgentFacade` 包装现有 `CanvasService`、`CanvasProjectManagementService` 和 `GenerationService`。

- **读取范围**：默认只读取当前画布和用户选中节点；扩大到全画布或其他画布时必须在计划中明确范围。
- **参数更新**：Agent 只能更新工具 Schema 白名单中的生成参数，不能修改节点归属、采用版本或其他未声明字段。
- **结构变更**：创建/复制/连接/删除节点属于 WRITE，计划必须展示将新增、修改或删除的节点和连线数量。P0 默认不提供删除工具；确需删除时使用归档或软删除命令。
- **生成执行**：图片、视频、音频和质量检查统一创建 `generation_task`，Agent 步骤保存任务引用，不在 Agent 线程内等待供应商长任务。
- **产物回写**：生成结果可挂接到节点或项目资产，但默认只作为候选版本；设置采用版本仍由现有画布业务动作或用户单独确认完成。
- **冲突**：节点 revision、画布 source snapshot 或选中范围变化时暂停计划，重新计算影响和成本后再审批。

## 6. 数据模型

### 6.1 `agent_sessions` 扩展

保留现有主键和 UUID，增加或统一以下字段：

- `workspace_id`、`workspace_type`：租户边界。
- `project_id`：必填，P0 会话必须绑定一个用户可访问项目。
- `content_unit_id`、`canvas_project_id`：可空的默认上下文。
- `context_scope_json`：记录选中文本范围、节点 UUID 集合、分镜 UUID 集合等显式作用域；只存稳定标识和范围，不复制业务正文。
- `owner_user_id`：会话创建者。
- `status`：`ACTIVE | ARCHIVED`；执行状态不混入会话状态。
- `last_message_at`、`updated_at`、`row_version`。

删除 H2 中仅存在、实体未映射的 `skill_id` 口径，MySQL/H2/实体保持一致。旧 `user_id` 迁移为 `owner_user_id`，不得长期并存两个事实字段。

### 6.2 `agent_messages` 扩展

- `uuid`、`session_id`、`parent_message_id`。
- `role`：`USER | ASSISTANT | SYSTEM | TOOL`。
- `content_type`：`TEXT | PLAN | TOOL_SUMMARY | ERROR | NOTICE`。
- `content`、`content_json`、`status`。
- `model_id`、`prompt_tokens`、`completion_tokens`、`credit_cost`。
- `client_request_id`：消息提交幂等键。
- `created_by`、`created_at`。

### 6.3 新增 `agent_plans`

- `uuid`、`session_id`、`source_message_id`、`version`。
- `status`：`DRAFT | AWAITING_APPROVAL | QUEUED | RUNNING | PAUSED | SUCCEEDED | PARTIAL_FAILED | FAILED | CANCELED`。
- `summary`、`risk_level`、`context_hash`、`inputs_hash`。
- `estimated_credits_min`、`estimated_credits_max`、`estimated_seconds`。
- `approval_status`、`approved_by`、`approved_at`、`approval_snapshot`。
- `created_at`、`updated_at`、`row_version`。

审批快照必须包含 `plan_id`、`version`、`inputs_hash`、成本区间、影响对象摘要和用户确认时间。计划内容或估价变化后，原审批失效并回到 `AWAITING_APPROVAL`。

### 6.4 新增 `agent_plan_steps`

- `uuid`、`plan_id`、`sequence_no`、`depends_on`。
- `tool_name`、`operation_mode`、`risk_level`。
- `title`、`input_json`、`precondition_json`。
- `status`：`PENDING | BLOCKED | RUNNING | PAUSED | SUCCEEDED | FAILED | SKIPPED | CANCELED`。
- `idempotency_key`、`result_ref_type`、`result_ref_id`。
- `estimated_credits`、`actual_credits`、`error_code`、`error_message`。
- `started_at`、`completed_at`、`row_version`。

### 6.5 `agent_executions` 调整

`agent_executions` 表示某一步的单次执行尝试，而不是整个计划。增加 `plan_step_id`、`attempt_no`、`generation_task_id`、`input_summary`、`output_summary` 和 `error_code`。原始敏感输入、密钥和模型隐藏推理不写入日志。

### 6.6 新增 `agent_events`

- `id` 作为全局递增序号，另存 `session_id`、`plan_id`、`step_id`。
- `event_type`、`payload_json`、`created_at`。
- SSE 使用 `id` 作为事件 ID；客户端通过 `Last-Event-ID` 或 `after` 补传。

事件保留策略与审计要求一致。P0 不引入独立消息队列；高并发拆服务时可用 Outbox 将同一事件转发到消息总线。

## 7. 核心流程

### 7.1 创建和恢复会话

1. 用户从项目、内容单元或画布入口创建会话。
2. 服务端从认证上下文获取用户和 Workspace，校验项目访问权限。
3. 写入会话并返回上下文摘要。
4. 打开已有会话时，前端并行加载会话详情、最近消息、当前计划和执行快照，再连接 SSE。

### 7.2 发送消息和生成计划

1. 前端提交消息并携带 `Idempotency-Key`。
2. 服务端持久化用户消息，发布 `MESSAGE_ACCEPTED`。
3. `AgentContextAssembler` 读取当前项目数据，记录来源引用和 `context_hash`。
4. 先执行计划中允许的只读工具，补充上下文；只读工具仍受资源权限约束。
5. `AgentPlanner` 通过 AI Router 生成用户可见回复和 JSON Schema 计划。
6. 无写入或扣费动作时可直接返回分析结果；否则计算影响、风险、时间和成本，计划进入 `AWAITING_APPROVAL`。

### 7.3 审批和执行

1. 前端计划卡展示步骤、影响对象、是否覆盖已有产物、成本区间、预计时间和风险。
2. 用户可“要求调整”或“确认并执行”。调整会产生新计划版本。
3. 确认时服务端校验计划版本、输入哈希、权限、当前资源版本和最新价格。
4. 对可计费步骤预占额度，写入审批快照，将计划置为 `QUEUED`。
5. 编排器逐步执行 Tool Adapter，状态和结果通过事件表与 SSE 更新。
6. 成功后结算实际积分并释放剩余额度；失败或取消时释放未使用额度。

### 7.4 写作 Patch 应用

1. 写作页以 `project_id + content_unit_id + version_id + revision + selection_range` 打开或恢复 Agent 会话。
2. Agent 读取选中文本和有限上下文，生成结构化 Patch 与修改说明。
3. 写作 Facade 在临时副本上验证原文哈希并返回 diff；计划卡展示变更段落和影响。
4. 用户逐段接受/拒绝后形成新计划版本并审批。
5. 执行阶段再次校验 revision/hash，保存草稿并创建 `agent_edit` 命名版本；审计记录关联计划、步骤和新版本。
6. 如校验冲突，步骤进入 `PAUSED` 并要求重新生成 diff，不能静默覆盖。

### 7.5 暂停、取消和重试

- 暂停只阻止尚未开始的步骤；正在运行且供应商不支持取消的生成任务继续到安全检查点。
- 取消将未开始步骤标为 `CANCELED`；已产生的成功产物保留并在结果摘要中明确列出。
- 部分失败时计划为 `PARTIAL_FAILED`。用户重试只创建失败步骤的新执行尝试，并在依赖结果仍有效时复用成功步骤。
- 写步骤使用确定性幂等键：`plan_uuid + step_uuid + target_resource + operation`。重试不得重复创建节点、任务或积分交易。

## 8. Tool Registry P0 白名单

| 工具 | 风险 | 权限 | P0 行为 |
|---|---|---|---|
| `project.get_context` | READ | 项目查看 | 读取项目、内容单元和当前阶段摘要 |
| `writing.get_context` | READ | 项目查看 | 读取创作圣经、内容单元、指定版本、当前草稿或选中文本的有限上下文 |
| `writing.preview_patch` | READ | 项目查看 | 校验 Agent 生成的结构化 Patch，并在临时副本上返回 diff，不落库 |
| `writing.apply_patch` | WRITE | 项目编辑 | 基于 revision/hash 应用已审批 Patch，保存草稿并创建 `agent_edit` 版本 |
| `writing.create_version` | WRITE | 项目编辑 | 在无正文 Patch 的场景为当前草稿创建可审计命名版本 |
| `canvas.list_nodes` | READ | 画布查看 | 按类型、状态和选中范围读取节点 |
| `asset.list_project_assets` | READ | 资产查看 | 读取项目可用角色、场景、道具和参考资产 |
| `canvas.create_nodes` | WRITE | 项目编辑 | 批量创建白名单节点类型，返回节点 UUID；不得自动运行 |
| `canvas.update_node_params` | WRITE | 项目编辑 | 更新允许的生成参数，不覆盖已采用产物 |
| `canvas.connect_nodes` | WRITE | 项目编辑 | 在计划声明的节点间创建合法连线 |
| `generation.create_batch` | BILLABLE | 项目编辑 + 生成 | 创建批量生成任务并返回任务引用 |
| `quality.create_check` | BILLABLE | 项目查看 + 生成 | 创建质量检查任务并返回评分引用 |
| `asset.attach_generation_result` | WRITE | 项目编辑 | 将成功产物挂接到节点或项目资产，默认不设为采用版本 |

每个 Adapter 必须声明输入 Schema、输出 Schema、风险等级、权限、估价方法、超时、重试和幂等策略。模型无法动态注册工具或提升工具权限。

## 9. API 契约

所有接口沿用 `/api/v1` 和 `ApiResponse<T>`，资源 ID 对外使用 UUID。列表采用游标分页，创建和控制接口支持 `Idempotency-Key`。

### 9.1 会话与消息

- `GET /agent/sessions?project_id=&status=&cursor=&limit=`
- `POST /agent/sessions`
- `GET /agent/sessions/{sessionId}`
- `PATCH /agent/sessions/{sessionId}`：重命名或归档。
- `GET /agent/sessions/{sessionId}/messages?cursor=&limit=`
- `POST /agent/sessions/{sessionId}/messages`

消息提交立即返回 `202` 语义的数据体：`message_id`、`turn_id`、`accepted_at`。后续内容通过查询或 SSE 获取，HTTP 请求不等待完整模型和执行结果。

### 9.2 计划和运行控制

- `GET /agent/plans/{planId}`
- `POST /agent/plans/{planId}/approval`，请求含 `plan_version` 和 `decision=APPROVE|REJECT`。
- `POST /agent/runs/{planId}/actions`，`action=PAUSE|RESUME|CANCEL`。
- `POST /agent/steps/{stepId}/retries`。
- `GET /agent/sessions/{sessionId}/events?after=`，响应为 `text/event-stream`。

### 9.3 核心错误码

- `AGENT_SESSION_NOT_FOUND`
- `AGENT_SESSION_ACCESS_DENIED`
- `AGENT_PROJECT_CONTEXT_INVALID`
- `AGENT_PLAN_SCHEMA_INVALID`
- `AGENT_PLAN_VERSION_CONFLICT`
- `AGENT_APPROVAL_REQUIRED`
- `AGENT_APPROVAL_EXPIRED`
- `AGENT_INSUFFICIENT_CREDITS`
- `AGENT_TOOL_NOT_ALLOWED`
- `AGENT_TOOL_EXECUTION_FAILED`
- `AGENT_IDEMPOTENCY_CONFLICT`

错误响应必须包含稳定错误码、用户可理解消息、`request_id`；工具错误可附 `retryable` 和失败步骤 UUID，不返回供应商密钥或完整内部请求。

## 10. 前端交互

### 10.1 三栏工作台

- **左栏**：当前项目的会话列表、搜索、状态、更新时间、新建和归档。
- **中栏**：消息流、来源引用、计划审批卡、调整计划、输入区和上下文标签。
- **右栏**：项目/内容单元/画布上下文、当前权限、执行时间线、步骤详情、成本和控制按钮。

入口既保留全局 `/agent`，也支持项目内深链：`/agent?project_id=&content_unit_id=&canvas_project_id=`。没有项目上下文时先选择项目，不允许创建游离的 P0 生产会话。

### 10.2 模块内嵌入口与上下文交接

- **写作页**：正文工具栏和选中文本浮层提供“交给 Agent”入口。入口传递内容单元、当前版本、revision 和选区；返回时在写作页打开 diff 抽屉，不直接刷新或替换编辑器正文。
- **画布页**：复用节点浮动编辑器中的 Agent 入口，并增加多选节点的“交给 Agent”。节点内轻量 Agent 与全局会话共用同一后端会话/计划协议，不能继续维护两套规划和应用逻辑。
- **全局会话页**：展示完整历史、跨步骤计划和执行时间线；点击消息或步骤中的资源引用可回到写作版本、画布节点或生成任务。
- **上下文快照**：页面只提交稳定 ID、revision/hash 和选区，不提交可信权限或完整项目副本。服务端重新读取并校验实际数据。

### 10.3 关键状态

- 空状态、会话加载、历史分页、消息提交中、模型流式输出。
- 等待审批、计划已过期、额度不足、权限变化。
- 排队、执行中、暂停中、部分失败、成功、失败、取消。
- SSE 断线、重连、补传完成。

计划卡必须明确“将修改什么、将创建什么、是否覆盖、预计积分、预计时间、风险和可撤销边界”。不得只显示抽象工具名。

## 11. 权限、安全与审计

1. Controller 不接受可信 `user_id` 或 `workspace_id` 请求字段；身份来自 Spring Security，Workspace 来自可信上下文。
2. 会话查询按 `workspace_id + project_id` 过滤，并再次使用项目访问服务校验，防止通过会话 UUID 越权。
3. Tool Adapter 按动作校验项目查看、编辑和生成权限；审批不能替代权限。
4. 项目文本、上传内容和工具结果都视为不可信数据，不能覆盖系统策略、审批策略或工具白名单。
5. API 密钥只存在于服务端配置或凭证服务，不进入 Prompt、消息、事件和日志。
6. 日志仅保存输入/输出摘要和资源引用；对用户文本、URL、供应商错误和个人信息执行脱敏。
7. 通过 `row_version` 和步骤前置条件处理并发修改；目标资源版本变化时暂停并重新规划，而不是静默覆盖。
8. 审计链必须能从消息追溯到上下文哈希、计划、审批、步骤、执行尝试、生成任务、资产和积分交易。
9. 写作 Patch 必须记录修改前后哈希、新内容版本 ID 和 diff 摘要；画布写操作必须记录目标节点/连线 UUID、修改字段白名单和修改前后 revision。

## 12. 异常与恢复

- **模型超时或格式错误**：保留用户消息；格式修复最多一次；仍失败则记录可重试错误，不创建可执行计划。
- **执行前权限或价格变化**：计划暂停，释放未使用预占额度，生成新估价并要求重新审批。
- **瞬时工具错误**：按工具策略有限重试；生成类工具优先查询既有任务状态，不盲目重复提交。
- **部分失败**：保存成功产物和失败边界，允许仅重试失败步骤及受影响下游。
- **SSE 断线**：服务端继续执行；客户端重连后按最后事件 ID 补传，随后获取一次运行快照校准。
- **服务重启**：启动恢复任务扫描 `QUEUED`、`RUNNING`、`PAUSED` 计划。无法证明完成的步骤先对账，不直接重放写操作。
- **额度异常**：预占失败不进入执行；结算失败将计划标记为需人工处理并报警，不重复执行工具。

## 13. 测试与验收

### 13.1 测试结构

- 单元测试：状态迁移、审批策略、计划 Schema、上下文裁剪、风险分类、估价和幂等键。
- 集成测试：数据库迁移、Workspace/项目权限、会话隔离、审批版本、额度预占结算、SSE 补传。
- 契约测试：AI Router 结构化响应、Tool Adapter 输入/输出、生成任务关联和稳定错误码。
- E2E：新建会话、历史恢复、只读问答、审批执行、暂停/恢复、断线重连、部分失败重试、越权拒绝。
- 模块联动测试：写作选区 Patch 预览与应用、编辑冲突暂停、锁定版本保护；画布多选节点上下文、批量创建/更新、生成任务关联和候选产物回写。

### 13.2 发布验收标准

1. 刷新或重新登录后，会话、消息、计划和执行进度完整恢复。
2. 未审批时，任何 WRITE/BILLABLE 工具均无法执行。
3. 计划内容、成本或目标资源版本变化后，旧审批自动失效。
4. 重复提交、网络重试和服务恢复不会重复创建资源或重复扣费。
5. SSE 断开后重连不丢失事件，最终状态与数据库快照一致。
6. 部分失败可仅重试失败步骤，已有成功结果不被覆盖。
7. 跨 Workspace、无项目权限或缺少生成权限的访问全部拒绝。
8. 任一生产结果可追溯到用户、指令、上下文、计划审批、工具执行和账单。
9. 写作修改必须先显示 diff，应用后生成 `agent_edit` 版本；revision 冲突时不得覆盖用户最新内容。
10. 画布修改必须限定在审批声明的节点/连线范围；生成结果默认不得替换采用版本。

## 14. 实施分期

### M0：领域基础

- 数据库迁移与 H2/MySQL Schema 对齐。
- 强类型 DTO、Mapper、Repository 和 Service 分层。
- 会话/计划/步骤状态机、权限模型和统一错误码。
- 移除内存 `Map` 作为事实源，保留兼容路由但切换到数据库服务。

### M1：会话与只读副驾

- 历史会话和消息分页、重命名、归档。
- 项目上下文组装和来源引用。
- `WritingAgentFacade`、`CanvasAgentFacade` 的只读上下文接口，以及写作页/画布页的会话深链入口。
- AI Router 对话、结构化输出、Token 与积分记录。
- SSE 消息和事件流、前端三栏基础界面。

### M2：计划、审批和受控执行

- 计划 Schema、版本、风险和成本预估。
- 审批、额度预占、Tool Registry 和 P0 Adapter。
- 写作 Patch 预览/应用/版本化，画布节点创建/参数更新/连线和生成任务 Adapter。
- 异步执行、暂停、恢复、取消、失败重试和结果回写。
- 计划卡、执行时间线、步骤详情和人工控制。

### M3：可靠性与发布

- 事件断点续传、服务重启恢复、幂等和并发冲突处理。
- 部分失败恢复、额度结算、日志脱敏、限流和审计查询。
- 单元、集成、契约和 E2E 测试全部通过后灰度发布。

## 15. 后续演进边界

当 P0 数据表、状态机和 Tool Adapter 稳定后，可在不改变前端核心协议的情况下增加长期记忆、Skill 版本、QualityAgent 自动复核和多 Agent 分工。只有当 Agent 执行需要独立扩缩容、故障隔离或跨服务消息吞吐时，才将应用层和执行器拆为独立 `agent-svc`；数据库事件可通过 Outbox 迁移到消息总线。
