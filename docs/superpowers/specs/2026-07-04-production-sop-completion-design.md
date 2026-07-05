# 生产 SOP 模块完善设计

日期：2026-07-04

状态：已完成方案评审，待实施计划

范围：项目级 SOP 控制台、画布质检联动、生产 Gate、返工闭环、版本、失败恢复与产能

## 1. 背景与现状

当前 `Sop.vue` 是固定项目、固定集和固定检查结果的静态演示页。前端没有 SOP API 层，也不能从检查项定位到项目、集、画布、节点或镜头。侧边栏入口写死为 `/sop/1`。

后端 `SopController` 直接返回硬编码结果，没有调用现有 `SopService`。`SopService` 的准入、版本、失败策略和产能同样是硬编码数据。现有 `sop_audits` 表、`SopAudit` 实体与控制器返回结构之间也不一致。画布的生图、转视频、采用版本和导出动作尚未统一接入 SOP Gate。

因此，当前模块只能展示预设结果，不能承担生产准入、问题定位、返工复核或生产拦截职责。

## 2. 设计目标

1. 建立项目级 SOP 控制台和画布质检侧栏，并共享同一业务内核。
2. 基于真实剧本、分镜、画布、资产和生成任务数据执行检查。
3. 形成“检查—拦截—派单—修复—复核—重检—放行”闭环。
4. 对 P0/P1 强制拦截，对 P2/P3 告警但允许继续。
5. 数据源缺失时返回 `NOT_READY`，不得将项目显示为绿灯。
6. 检查报告绑定规则版本和业务数据快照，可追溯且不可直接篡改。
7. 按风险分级自动修复，高风险修改始终需要人工决策。
8. 分三期交付，每期具有可独立验收的退出条件。

## 3. 非目标

- SOP 不复制剧本、分镜、画布节点、资产或生成任务的主数据。
- AI 不直接决定生产 Gate 是否放行。
- 首期不提供用户自定义规则编辑器，规则由系统版本化发布。
- 不允许任何角色直接修改检查结果或绕过 P0/P1 Gate。
- SOP 不承担视频剪辑、合成或多轨时间轴能力。

## 4. 核心产品结构

采用“双界面、单内核”方案。

### 4.1 项目级 SOP 控制台

前端路由：`/content-projects/:projectId/sop`。

项目是业务根对象，内容单元/集和画布是可选检查范围。控制台包含：

- 总览：总体灯色、通过/告警/阻断/待配置数量、最新检查时间、报告是否过期。
- 生产准入：13 项规则、证据、影响目标、修复建议和当前 Gate 影响。
- 返工工单：严重等级、责任岗位、处理人、状态、复核记录和期限。
- 生产版本：版本晋级、绑定快照、审批记录和当前生产基线。
- 失败恢复：失败历史、已执行动作、当前策略和人工介入点。
- 产能估算：镜头复杂度、风险镜头、预计工时及估算依据。
- 检查历史：不同检查快照和差异。

全局“生产 SOP”菜单进入有权限的项目列表和最近风险项目，不再写死项目 ID。

### 4.2 画布质检侧栏

画布顶部提供“SOP 质检”入口，打开右侧质检侧栏。画布节点以绿、黄、红、灰标识 `PASS`、`WARNING`、`BLOCKED`、`NOT_READY/ERROR`。

侧栏支持：

- 按严重等级、规则、节点和责任岗位筛选。
- 从问题定位到具体节点或镜头。
- 查看证据、修复建议和受影响的生产动作。
- 执行安全修复、确认中风险修改、创建高风险返工单。
- 修复后重新检查并刷新 Gate 状态。

### 4.3 生产动作 Gate

以下业务动作必须在服务端调用统一 Gate，不能依赖用户主动打开 SOP 页面：

| Gate | 触发动作 | 主要检查 |
|---|---|---|
| `PRODUCTION_ADMISSION` | 项目进入批量生产 | 13 项生产准入规则 |
| `BEFORE_IMAGE_GENERATE` | 批量生图 | 分镜字段、角色/场景资产、Prompt、复杂镜头 |
| `BEFORE_VIDEO_GENERATE` | 图生视频 | 首帧、资产一致性、动作复杂度、画幅参数 |
| `BEFORE_ADOPT` | 设为采用版本 | 结果元数据、画幅、时长、清晰度、连续性 |
| `BEFORE_EXPORT` | 素材包导出 | 镜头顺序、采用版本、配音、字幕、音频和文件可用性 |
| `BEFORE_ASSET_UNLOCK` | 解锁 L4 资产 | 下游影响范围和连续性风险 |

Gate 根据当前业务快照执行适用规则。存在 P0/P1、关键规则 `NOT_READY` 或关键规则 `ERROR` 时拒绝动作，并返回阻断项、责任人和修复入口。P2/P3 只记录告警。

## 5. 端到端业务流程

1. 用户主动检查或生产动作触发 Gate。
2. `SopContextAssembler` 读取当前内容项目、内容单元、分镜、画布、资产及任务事实。
3. 系统生成范围快照引用与 `snapshot_hash`。
4. 规则引擎按已发布的 `rule_set_version` 执行适用规则。
5. 系统持久化检查运行和逐规则结果，报告一经完成不可修改。
6. Gate 根据结果生成放行或拒绝决定。
7. 阻断结果可创建返工工单；同一问题指纹下的未关闭工单不得重复创建。
8. 责任人修改源业务数据，而不是修改 SOP 结果。
9. 审核人复核返工结果；复核失败时工单重新打开。
10. 系统基于新快照重检，通过后关闭问题并重新计算 Gate。

源业务数据变化且哈希不一致时，旧报告标记为 `STALE`。过期报告可查看，但不可用于放行。

## 6. 规则引擎

### 6.1 规则定义

每条规则包含：

- `rule_code`：稳定唯一编码。
- `rule_set_version`：所属规则集版本。
- `name` 和 `category`。
- `applicable_gates`：适用 Gate。
- `default_severity`：P0、P1、P2 或 P3。
- `required_sources`：执行所需数据源。
- `evaluator`：确定性校验器。
- `fix_policy`：`AUTO_SAFE`、`CONFIRM_REQUIRED` 或 `MANUAL_ONLY`。
- `enabled`：是否在当前发布版本启用。

首期 13 项准入规则沿用核心 PRD 定义，但每项必须补齐数据来源、适用范围、结果阈值和证据结构。无法从当前系统事实判断的规则返回 `NOT_READY`，不得使用模拟数据补足。

**问题指纹 (`issue_fingerprint`) 计算算法**：

```
issue_fingerprint = SHA256(rule_code + ":" + target_type + ":" + target_id)
```

指纹用于去重：同一项目、同一规则、同一目标的问题视为同一个问题。若规则不绑定具体目标（全局规则如 `PLOT_FIDELITY`），`target_type` 使用 `"project"`，`target_id` 使用 `project_id`。指纹不包含严重等级或结果值——即使严重等级在规则集版本间调整，同一问题仍被视为同一工单。

### 6.2 结果枚举

| 结果 | 含义 | Gate 处理 |
|---|---|---|
| `PASS` | 满足规则 | 放行 |
| `WARNING` | P2/P3 风险 | 放行并记录告警 |
| `BLOCKED` | P0/P1 问题 | 拒绝对应动作 |
| `NOT_READY` | 所需数据缺失 | 关键规则拒绝；总体不得为绿灯 |
| `ERROR` | 规则执行异常 | 关键规则失败保护并拒绝 |

总体灯色规则：全部适用规则 `PASS` 为绿灯；无阻断但存在 `WARNING` 或非关键 `NOT_READY` 为黄灯；存在 `BLOCKED`、关键 `NOT_READY` 或关键 `ERROR` 为红灯。

### 6.3 AI 使用边界

结构化字段完整性、枚举、长度、引用关系、状态和文件可用性由确定性规则判断。AI 可生成解释、修复建议和 Prompt 候选，但 AI 输出不能直接改变规则结果或 Gate 决定。

## 7. 自动修复边界

| 风险 | 示例 | 行为 |
|---|---|---|
| 低风险 | 格式归一、命名修正、补充可推导标记 | 可一键执行，完整留痕 |
| 中风险 | Prompt 改写、模型参数调整 | 展示差异，用户确认后执行 |
| 高风险 | 拆镜、剧情修改、采用版本切换、资产解锁 | 仅生成建议或返工单，不自动修改 |

所有修复命令携带幂等键、操作者、前后快照和来源检查结果。修复成功不等于检查通过，仍必须重检。

## 8. 数据模型

SOP 统一使用 `content_projects.id` 作为 `project_id`，可选关联 `content_unit_id` 和 `canvas_project_id`。节点、镜头和资产使用 `target_type + target_id` 表示，避免复制主数据。

### 8.1 `sop_rule_set_versions`

记录已发布的规则集版本，确保每次检查绑定的规则集可追溯、不可变。

- `id`：主键。
- `version`：版本标识（如 `production-readiness-v1`），唯一。
- `name`：版本名称。
- `description`：变更说明。
- `rule_count`：包含的规则数量。
- `published_at`：发布时间。
- `published_by`：发布人（系统管理员）。
- `is_active`：是否为当前激活版本。同一时刻仅一个版本激活。

规则集版本发布后不可修改或删除。新检查始终使用当前激活版本；历史报告保留当时绑定的版本，不受后续发布影响。

### 8.2 `sop_check_runs`

记录一次检查：范围、Gate、触发方式、规则集版本、快照引用与哈希、运行状态、总体灯色、各结果计数、操作者和时间。运行完成后业务字段不可更新，只允许追加过期标记。

### 8.3 `sop_check_results`

记录逐规则结果：运行 ID、规则编码、结果、严重等级、目标类型/ID、问题指纹、证据 JSON、建议、修复策略和数据依赖状态。

`issue_fingerprint` 计算方式见 6.1 节指纹算法。

### 8.4 `sop_work_orders` 与 `sop_work_order_events`

工单关联来源结果并保存责任岗位、处理人、状态、期限和解决说明。事件表追加记录每次认领、修改、提交复核、通过、驳回和重新打开。

状态机固定为：

`OPEN -> ASSIGNED -> FIXING -> PENDING_REVIEW -> PASSED`

复核失败：`PENDING_REVIEW -> REOPENED -> FIXING`。仅重复问题被证明无效时允许 `CANCELED`，并要求原因和审核人。

### 8.5 `sop_gate_decisions`

保存 Gate 类型、目标、检查运行、快照哈希、是否放行、阻断数量和请求幂等键。业务动作必须引用一次仍有效的允许决定。

### 8.6 `sop_production_releases`

保存生产版本阶段、内容版本、分镜版本、画布版本、资产清单快照、准入运行、审批人和审批时间。阶段为 `DRAFT`、`EDITOR_CONFIRMED`、`DIRECTOR_CONFIRMED`、`PRODUCTION`、`FINAL`。

> **Phase 2 实现**：`sop_production_releases` 与 `SopReleaseService` 推迟到第二期交付。第一期仅完成准入检查和返工闭环，生产版本晋级在画布质检联动完成后才有完整的业务链条。

### 8.7 三期增强表

- `sop_failure_attempts`：失败类型、次数、恢复动作、执行结果和人工升级状态。
- `sop_capacity_snapshots`：估算输入、复杂度、预计工时、风险和口径版本。

现有 `sop_audits` 视为遗留数据。迁移时转换为工单及事件；转换完成后停止写入，不让旧表同时承担“检查报告”和“返工工单”两种职责。

## 9. 服务边界

- `SopApplicationService`：用例编排和事务边界。
- `SopContextAssembler`：从业务模块组装只读检查上下文。
- `SopRuleEngine`：选择和执行规则，生成结果。
- `SopGateService`：按 Gate 矩阵进行放行判断。
- `SopWorkOrderService`：工单状态机、分派与复核。
- `SopReleaseService`：生产版本晋级和基线快照。
- `SopRecoveryService`：失败恢复状态机，第三期启用。
- `SopCapacityService`：可解释的产能估算，第三期启用。

规则校验器按领域拆分，禁止继续在单个 Controller 或 Service 中硬编码 13 项结果。

## 10. API 设计

保留 `/api/v1/sop` 前缀，并以项目作为主要资源：

### Phase 1 端点

| 方法 | 路径 | 用途 | 阶段 |
|---|---|---|---|
| `GET` | `/projects` | 当前用户可访问的 SOP 项目及风险摘要 | 1 |
| `GET` | `/projects/{projectId}/summary` | 控制台总览 | 1 |
| `POST` | `/projects/{projectId}/checks` | 主动发起检查 | 1 |
| `GET` | `/projects/{projectId}/checks` | 检查历史 | 1 |
| `GET` | `/projects/{projectId}/checks/{runId}` | 检查报告与逐项结果 | 1 |
| `GET` | `/projects/{projectId}/work-orders` | 查询返工工单 | 1 |
| `POST` | `/projects/{projectId}/work-orders` | 创建返工工单 | 1 |
| `PATCH` | `/projects/{projectId}/work-orders/{id}` | 认领、处理或提交复核 | 1 |
| `POST` | `/projects/{projectId}/work-orders/{id}/review` | 复核通过或驳回 | 1 |
| `POST` | `/projects/{projectId}/gates/production-admission/evaluate` | 执行生产准入 Gate | 1 |
| `POST` | `/projects/{projectId}/fixes/{resultId}` | 执行允许的修复命令 | 1 |

### Phase 2 端点

| 方法 | 路径 | 用途 | 阶段 |
|---|---|---|---|
| `GET` | `/projects/{projectId}/canvas/summary` | 画布风险摘要 | 2 |
| `GET` | `/projects/{projectId}/canvas/nodes` | 画布节点问题查询 | 2 |
| `GET` | `/projects/{projectId}/releases` | 生产版本历史 | 2 |
| `POST` | `/projects/{projectId}/releases/promote` | 版本晋级 | 2 |

### Phase 3 端点

| 方法 | 路径 | 用途 | 阶段 |
|---|---|---|---|
| `GET` | `/projects/{projectId}/failures` | 失败恢复历史 | 3 |
| `GET` | `/projects/{projectId}/capacity` | 产能估算 | 3 |
| `GET` | `/projects/{projectId}/reports/export` | 报告导出 | 3 |

现有 `/check/production-readiness` 在迁移期作为兼容入口，内部调用新检查用例，不再维护第二套逻辑。

### 分页参数

所有列表接口（`GET /projects`、`GET /checks`、`GET /work-orders`）统一使用以下查询参数：

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `page` | int | 1 | 页码（从 1 开始） |
| `size` | int | 20 | 每页条数（最大 100） |
| `sort` | string | `created_at` | 排序字段 |
| `order` | string | `desc` | `asc` 或 `desc` |

响应体包含 `page`、`size`、`total`、`totalPages` 和 `items` 字段。

### 统一错误响应格式

```json
{
  "code": 72003,
  "message": "生产准入 Gate 未通过：存在 3 项阻断问题",
  "details": [
    {
      "ruleCode": "ASSET_BINDING",
      "result": "BLOCKED",
      "targetType": "scene",
      "targetId": "42",
      "suggestion": "请为第 3 场绑定场景资产"
    }
  ],
  "timestamp": "2026-07-04T10:30:00Z"
}
```

`details` 为可选字段，仅在阻断类错误（如 Gate 拒绝、工单冲突）时返回具体原因。`code` 使用 72xxx 段 SOP 专用错误码。

所有写接口使用 DTO、权限校验和幂等键；禁止继续接收或返回无约束的 `Map<String,Object>`。

## 11. 权限与审计

### 11.1 角色权限矩阵

| 角色 | 对应 Action | SOP 权限 |
|---|---|---|
| 制片/PM | `Action.PRODUCE` | 发起检查、分派返工、查看项目全局、提交版本晋级(Phase 2) |
| 导演/审核人 | `Action.REVIEW` | 复核 P0/P1、确认高风险修改、批准生产版本(Phase 2) |
| 生产岗位(AI画师/配音等) | `Action.PRODUCE` | 查看和处理本人问题、执行安全修复、申请复核 |
| 普通成员 | `Action.VIEW` | 只读查看检查报告和工单状态 |
| 系统管理员 | 系统级权限 | 发布规则集版本，不参与业务放行 |

### 11.2 Action 到接口映射

| Action | 允许的接口 |
|---|---|
| `Action.VIEW` | `GET /projects`、`GET /summary`、`GET /checks`、`GET /checks/{runId}`、`GET /work-orders` |
| `Action.PRODUCE` | `VIEW` + `POST /checks`、`POST /work-orders`、`PATCH /work-orders/{id}`、`POST /fixes/{resultId}` |
| `Action.REVIEW` | `VIEW` + `POST /work-orders/{id}/review` |

### 11.3 审计要求

权限建立在现有项目成员和企业角色上。跨租户访问必须拒绝。检查、Gate、修复、工单和版本操作全部写入审计事件，至少记录：

- 操作时间、操作者 ID、操作者角色。
- 目标资源类型与 ID。
- 操作类型（如 `SOP_CHECK_RUN`、`SOP_GATE_EVALUATE`、`SOP_WORK_ORDER_TRANSITION`）。
- 操作前后状态（关键字段变更）。
- 请求幂等键。

## 12. 异常与并发处理

- 数据源不可用或字段缺失：返回 `NOT_READY`，并指出缺失来源。
- 单条规则异常：该规则记为 `ERROR`；其他规则继续执行，报告保留部分结果。
- 关键规则异常：Gate 失败保护并拒绝动作。
- 业务数据在检查期间发生变化：比较 revision/hash；不生成可放行决定，提示重试。
- 重复检查：相同范围、规则版本和快照哈希可复用已完成报告。
- 重复工单：通过问题指纹和活动状态唯一约束防重。
- 重复 Gate/修复请求：通过幂等键返回原决定或原执行结果。
- 返工并发更新：使用行版本进行乐观锁控制。
- 限流保护：单项目 30 秒内最多发起 1 次检查；单用户每分钟最多 60 次 SOP API 调用。超限返回 429 并提示 Retry-After。

## 13. 通知机制

> **Phase 2 实现**。第一期通过轮询接口查看状态变化；通知机制在画布质检联动完成后统一建设。

### 设计要点

- **通知触发事件**：工单创建、工单分派、复核驳回、Gate 拒绝。
- **通知渠道**：站内消息中心 + 可选企业微信/飞书推送。
- **通知内容**：项目名称、问题摘要、责任岗位、期限、直达链接。
- **免打扰**：同一工单同一状态 24 小时内不重复通知；批量检查产生的批量工单合并为一条汇总通知。

第一期前端通过在工单列表和项目列表中展示未处理数量和红色角标作为替代方案。

## 14. 分期交付

### 第一期：准入检查与返工闭环

交付项目列表、SOP 控制台、真实数据上下文、13 项规则、检查历史、不可变报告、返工状态机、生产准入 Gate、权限与审计。

**关于 P0/NOT_READY 规则的处理**：第一期存在部分规则因上游数据源尚未建设而永远返回 `NOT_READY`（如 `PLOT_FIDELITY`、`CONTINUITY_INHERITANCE`、`VOICE_BINDING` 等）。这些规则在 `production-readiness-v1` 中设为 `enabled=false`，检查引擎跳过它们，既不参与 Gate 放行判断，也不影响总体灯色。规则定义和校验器代码仍然完整实现，待上游数据源就绪后通过发布新规则集版本 `production-readiness-v2` 将 `enabled` 改为 `true` 即可激活。此设计确保：
- 第一期能产生有意义的 Gate 放行决定，而非永远被拒。
- 规则逻辑经过完整测试，只是不参与执行。
- 数据源就绪后无需修改代码，仅发布新规则版本。

退出条件：真实项目能够完成“发现问题—派单—修复—复核—重检—放行”，且不存在硬编码检查结果。

### 第二期：画布质检与生产 Gate

交付画布侧栏、节点风险染色、问题定位、生图/转视频/采用/导出/资产解锁 Gate、分级修复、报告过期和增量重检。

退出条件：所有受管生产入口均不能绕过 P0/P1，控制台与画布对同一报告呈现一致状态。

### 第三期：失败恢复、产能与质量报表

交付失败恢复状态机、安全自动重试、人工升级、可解释产能估算、质量趋势、返工统计、报告导出和规则效果监控。

退出条件：恢复动作可追溯，估算展示输入与口径，报表只使用真实生产记录。

## 15. 测试与验收

- 规则单元测试：每条规则覆盖 `PASS`、`BLOCKED/WARNING`、`NOT_READY` 和 `ERROR`。
- 服务集成测试：快照过期、Gate 矩阵、工单状态、版本晋级、权限、幂等和并发冲突。
- API 契约测试：字段、枚举、错误码和兼容入口。
- 前端状态测试：加载、空态、无权限、过期、部分规则失败和 Gate 拒绝。
- E2E：从真实问题发现到定位、修复、复核、重检和放行。
- 安全测试：跨项目/跨租户拒绝、高风险修复无授权拒绝、结果不可篡改。

每期必须同时完成前端、后端、数据库迁移、契约测试和 E2E，不以“页面可点击”作为完成功能的标准。

## 16. 现有代码改造清单

- 移除 `Sop.vue` 的静态项目名、检查项和返工数据，拆分为项目控制台组件与状态层。
- 新增前端 `sop` API 模块、路由参数解析、项目/集/画布范围选择和错误状态。
- 将侧边栏 `/sop/1` 改为 SOP 项目入口。
- 将 `SopController` 改为 DTO + 应用服务调用。
- 拆除 `SopService` 中的硬编码规则、版本、失败策略和产能数据。
- 删除现有 `SopService` 的硬编码数据测试，替换为基于真实规则引擎和上下文组装器的服务测试。
- 新增规则引擎、上下文组装器、Gate、工单、版本和审计服务。
- 新增数据库迁移，并处理旧 `sop_audits` 数据。
- 在画布相关生产命令的服务端入口接入 Gate。
- 补齐前后端契约、权限、集成和 E2E 测试。

## 17. 关键决策汇总

1. 采用项目级控制台与画布侧栏完整打通。
2. P0/P1 强制拦截，P2/P3 只告警。
3. 数据缺失为 `NOT_READY`，不得获得绿灯。
4. 自动修复按风险分级，高风险操作不自动执行。
5. 采用双界面、单内核，共享规则、报告、工单和审计数据。
6. 采用三期交付，先建立真实闭环，再增加画布联动和运营增强。
