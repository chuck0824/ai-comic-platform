# 企业工作台核心可用版完善设计

日期：2026-07-04

状态：已确认

范围：企业工作区切换、角色化概览、组织成员管理、采购预算、统一审批、企业资产入口和跨域审计

## 1. 结论

企业工作台采用“领域化渐进完善”方案。它是当前 Workspace 下的企业控制台、聚合查询 BFF 和权限化命令入口，不成为新的账户、余额、资产或审批事实源。

- `3001` 是用户、个人/企业 Workspace、部门、成员、角色权限、共享余额、AI 用量和成员限额的唯一事实源。
- `8080` 保存漫剧业务数据、采购预算治理规则和各业务域状态；不复制企业主数据或权威余额。
- 企业工作台统一展示采购、企业资产发布和项目导出三类审批，但审批命令必须回到原业务域执行。
- 管理员、部门负责人和普通成员共用同一入口，后端根据 Membership、数据范围和业务状态返回菜单及 `allowed_actions`。
- 同一自然人始终保有个人 Workspace，并可加入和切换多个企业 Workspace。

本期不建设 SSO、API Key 管理、复杂经营报表、通用 BPM、批量审批或独立企业微服务。

## 2. 架构基线与口径优先级

本设计以 `docs/superpowers/specs/2026-06-28-unified-account-model-billing-design.md` 为账户与计费基线。该设计明确：

1. `3001` 是账户、组织权限、余额和账单唯一事实源。
2. `8080` 通过账户中心 API 提供相同操作界面，不写本地账户表。
3. 用户可以拥有个人 Workspace 并加入多个企业 Workspace。
4. 企业成员共享企业钱包，但可以配置个人用量限额和模型权限。

现有核心文档中仍存在“本地 user-svc 是账户事实源”“8080 保存企业成员副本”等旧口径。发生冲突时以统一账户设计和本设计为准，并在实施阶段同步修订旧文档。

## 3. 现状与缺口

### 3.1 前端

- `aicp-frontend/src/views/Enterprise.vue` 是硬编码静态页面，按钮没有业务闭环。
- 企业名称、指标、审批和成员列表均为演示数据，没有 API 层、加载态、错误态或权限态。
- `auth.js` 登录后固定写入 `personal_{uid}`，无法发现或切换企业 Workspace。
- 企业入口对所有登录用户显示，没有根据当前 Workspace 类型、Membership 或权限调整。
- 现有 `EnterprisePurchaseCenter.vue` 将创建采购申请接口误用于列表查询，审批中心不可用。

### 3.2 8080

- `EnterpriseController` 全部返回静态数据，邀请、角色变更、删除成员和资料更新没有持久化。
- 本地 `enterprises`、`enterprise_members` 表与“3001 唯一事实源”冲突。
- `/api/v1/enterprise/**` 统一要求 `ent_admin` 或 `dept_head`，但登录令牌实际只包含旧 `can_*` 权限，普通企业成员也无法使用角色化工作台。
- `WorkspaceContextFilter` 尚未覆盖企业工作台路径。
- 开发环境的 Workspace Membership 使用宽松 Mock，并存在解析失败回退固定用户的风险，不能作为企业授权依据。

### 3.3 3001

- 已有 `aicp_workspaces`、`aicp_workspace_members` 和单 Workspace Membership 查询。
- 尚缺用户可加入 Workspace 列表、企业资料、部门、邀请、角色、权限范围和成员管理 API。
- Membership 仅返回权限字符串，无法表达 Workspace、部门和本人三级数据范围。

### 3.4 可复用业务能力

- 资产域已具备企业资产发布申请与审批模型，应直接复用。
- 交易域已具备企业采购申请、审批、企业钱包付款和审计基础。
- 任务事件中心设计已定义跨域事件投影、命令回源和用户待办原则。
- 项目导出审批目前仅存在产品要求，缺少正式领域状态机、接口和测试；必须先补齐后再接入统一审批。

## 4. 目标与非目标

### 4.1 目标

- 让企业管理员、部门负责人和普通成员获得与权限匹配的企业首页。
- 建立个人/多企业 Workspace 发现、切换、隔离和失效回退闭环。
- 在 `8080` 完成组织、部门、成员和角色管理，但所有写入通过 BFF 落到 `3001`。
- 建立采购业务预算、单笔阈值、预占、释放、消费和退款冲回闭环。
- 提供采购、资产发布和项目导出统一审批收件箱，同时保持业务域状态机独立。
- 复用现有企业 Workspace 资产库，不建设第二套企业资产模型。
- 提供可检索的企业审计入口，并保留每条记录的原始领域引用。
- 保证 Workspace 隔离、细粒度授权、幂等、并发控制和故障关闭。

### 4.2 非目标

- 企业注册认证流程、企业 SSO、MFA 强制策略。
- API Key 创建、轮换和调用管理。
- 自定义经营 BI、利润分析或报表导出。
- 通用流程编排器、可视化审批流设计器或任意多级 BPM。
- 三类审批的批量通过或批量驳回。
- 重建资产库、任务中心、交易市场或钱包账本。

## 5. 总体架构

```text
企业工作台 UI
  │  Workspace 切换 / 角色化概览 / 审批 / 组织 / 预算 / 审计
  ▼
8080 Enterprise Workspace Facade
  │  聚合查询 / 错误转换 / allowed_actions / 命令路由 / 审计关联
  ├───────────────────┬──────────────────────┐
  ▼                   ▼                      ▼
3001 账户中心       8080 各业务域          任务事件中心
Workspace           交易采购申请            交易与生成过程投影
部门/成员/角色       资产发布申请            待办与异常摘要
权限范围             项目导出申请            费用与时间线
余额/AI 限额
```

### 5.1 边界规则

- Enterprise Facade 可以短时缓存只读展示数据，但缓存不能用于鉴权、预算占用、余额判断或命令决策。
- 组织、成员和权限写操作必须调用 `3001` API，禁止写入 8080 本地企业表。
- 审批列表使用可重建投影；批准、驳回、撤回和付款必须回源业务服务。
- 任务事件中心不接管审批状态机；它提供过程、异常和时间线摘要。
- 采购预算是业务治理额度，不是钱包。可用余额、扣款、退款和账本仍由 `3001` 负责。
- 企业资产页面复用资产市场的 Workspace 资产库；企业工作台只提供摘要和深链。

## 6. 页面信息架构

### 6.1 路由

```text
/enterprise                   → /enterprise/overview
/enterprise/overview          企业概览
/enterprise/approvals         统一审批
/enterprise/organization      组织与成员
/enterprise/budgets           预算与用量
/enterprise/audit             审计记录
```

“企业资产”不新增事实页面。入口跳转到 `/asset-market` 的当前 Workspace 资产频道；待发布审批同时出现在统一审批和资产市场审批频道中，二者读取同一业务事实。

### 6.2 Workspace 切换器

- 顶栏始终展示当前 Workspace 名称和类型。
- 下拉列表分为“个人空间”和“企业空间”，展示企业内角色及成员状态。
- 切换后刷新 Membership，清空旧 Workspace 的查询缓存、分页状态、选择项和 SSE 订阅，再携带新的 `X-Workspace-Id` 重载页面。
- 当前为个人 Workspace 时访问 `/enterprise`：若用户已加入企业，展示企业选择器；否则展示无企业状态和返回个人首页入口。
- 当前企业 Membership 失效时立即清理企业缓存并回退个人 Workspace，不自动选择另一个企业。

### 6.3 企业概览

管理员或部门负责人视图：

- 有效成员数与席位上限。
- 共享余额摘要和本月 AI 实际用量，数据来自 `3001`。
- 采购预算可用、已预占和已消费，数据来自 8080 采购预算域。
- 待我审批、异常任务和进行中项目。
- 部门预算使用率、最近企业动态和常用管理入口。

普通成员视图：

- 我的 AI 用量与成员限额。
- 我的采购额度和当前预占。
- 我的申请、待补充事项、参与项目和可用企业资产。
- 不展示全企业成员、全局预算、全企业审计或无权限管理入口。

概览卡片必须显示统计周期和来源更新时间。不得将采购预算、共享余额和 AI 用量合并为一个“本月支出”数字。

### 6.4 统一审批

页签：

- 待我处理。
- 我发起的。
- 已处理。

筛选：审批类型、状态、部门、申请人和提交时间。列表按服务端分页，不在前端截取后统计。

统一字段：类型、摘要、申请人、部门、提交时间、当前状态、风险提示和下一动作。

专属证据：

- 采购：授权条款、历史授权披露、采购预算预占、共享余额摘要和付款责任人。
- 资产发布：资产预览、版本、来源、公开说明和授权声明。
- 项目导出：项目版本、导出范围、品牌合规结果、水印策略和交付目标。

驳回必须填写原因。核心版不支持批量审批，因为三类申请的证据和后续动作不同。

### 6.5 组织与成员

- 左侧部门树，右侧成员列表。
- 支持创建、改名、移动和停用空部门；包含有效成员的部门不能直接删除。
- 支持手机号或邮箱邀请、撤回邀请和重新发送。
- 支持调整部门、角色、状态和权限范围。
- Workspace 管理员管理全企业；部门负责人只管理授权部门范围。
- 禁止操作者提升自己到无权授予的权限。
- 禁止移除、禁用或降级最后一名有效 Workspace 管理员。
- 成员移除后企业 API Key 和企业 Membership 立即失效，个人 Workspace 不受影响。

### 6.6 预算与用量

页面分为两个明确区域：

1. 采购预算：Workspace、部门或成员的月度预算、单笔上限、已预占、已消费和可用额度。
2. AI 用量：共享余额、成员日/月限额、实际调用用量和超限状态，全部来自 `3001`。

页面只提供 `3001` 充值入口，不在 8080 创建独立钱包或余额字段。

### 6.7 审计记录

- 支持按操作者、动作、对象类型、结果和时间筛选。
- 默认只展示当前 Workspace；部门级审计权限只返回授权部门数据。
- 审计索引保存来源域、来源记录 ID、请求关联 ID 和脱敏摘要。
- 查看详情时回源原业务审计；原始记录不可从企业工作台修改或删除。

## 7. 权限模型

### 7.1 权限码

| 权限 | 能力 |
|---|---|
| `enterprise.dashboard.view` | 查看权限范围内企业概览 |
| `org.department.manage` | 管理部门 |
| `org.member.manage` | 邀请、调整或停用成员 |
| `org.role.manage` | 管理角色与权限 |
| `enterprise.budget.view` | 查看采购预算和用量 |
| `enterprise.budget.manage` | 设置采购预算和阈值 |
| `trade.purchase.request` | 发起企业采购申请 |
| `trade.purchase.approve` | 审批企业采购 |
| `trade.purchase.pay` | 对已批准订单确认付款 |
| `asset.publish.request` | 提交企业资产发布申请 |
| `asset.publish.approve` | 审批企业资产发布 |
| `project.export.request` | 提交项目导出申请 |
| `project.export.approve` | 审批项目导出 |
| `enterprise.audit.view` | 查看权限范围内审计 |

资产浏览、使用和管理继续使用现有 `asset.view`、`asset.use` 和 `asset.manage`。

旧的 `can_*`、`ent_admin` 和 `dept_head` 仅作为迁移映射，不再作为新企业接口的授权契约。

### 7.2 数据范围

每项权限附带数据范围：

- `WORKSPACE`：当前企业全部数据。
- `DEPARTMENT`：指定部门及经明确授权的子部门。
- `SELF`：本人创建、参与或负责的数据。

Membership 响应保持 `permissions: string[]` 兼容现有调用方，并新增：

```json
{
  "workspace_id": "ent_100",
  "workspace_type": "enterprise",
  "user_id": 9,
  "member_id": "member_9",
  "department_id": "dept_content_1",
  "roles": ["dept_head"],
  "permissions": ["enterprise.dashboard.view", "trade.purchase.approve"],
  "permission_grants": [
    {
      "permission": "trade.purchase.approve",
      "scope": "DEPARTMENT",
      "scope_ids": ["dept_content_1"]
    }
  ]
}
```

`WorkspaceContext` 必须携带部门和权限范围。前端隐藏或禁用按钮只用于体验，后端仍根据可信 WorkspaceContext、目标对象部门和当前状态执行最终鉴权。

## 8. 数据模型

### 8.1 3001 账户中心

在现有 Workspace 模型上补充：

- `aicp_workspaces`：名称、类型、所有者、状态、认证状态、成员上限和审计时间。
- `aicp_departments`：Workspace、父部门、名称、负责人、状态、排序和审计时间。
- `aicp_workspace_roles`：Workspace、自定义角色名称、是否系统模板和状态。
- `aicp_role_permission_grants`：角色、权限码、范围类型和范围目标。
- `aicp_workspace_members`：用户、Workspace、部门、角色、状态和加入时间。
- `aicp_workspace_invitations`：邀请目标、预设部门/角色、令牌摘要、状态和过期时间。

余额、用量、成员 AI 限额和模型权限继续使用 3001 权威模型，不在 8080 复制。

### 8.2 8080 采购预算

`enterprise_purchase_budgets`：

- `workspace_id`。
- `subject_type`：`WORKSPACE`、`DEPARTMENT` 或 `MEMBER`。
- `subject_id`。
- `period_month`。
- `amount_cents`、`single_limit_cents`。
- `reserved_cents`、`consumed_cents`，作为事务内维护的当前投影。
- `row_version` 和审计字段。

唯一约束：`(workspace_id, subject_type, subject_id, period_month)`。

`enterprise_purchase_budget_entries` 使用不可变流水记录：

- `RESERVE`：提交采购申请时预占。
- `RELEASE`：驳回、撤回、过期或付款前取消时释放。
- `CONSUME`：3001 钱包确认扣款成功后转为消费。
- `REVERSE`：退款或冲正确认成功后冲回。

每条流水保存采购申请、订单、钱包转账引用和幂等键。金额统一使用最小货币单位 `long`，禁止浮点金额。

### 8.3 统一审批投影

`enterprise_approval_items` 是可重建读模型：

- `workspace_id`、`department_id`。
- `source_type`：`PURCHASE`、`ASSET_PUBLISH`、`PROJECT_EXPORT`。
- `source_id`、`source_version`。
- 申请人、摘要、金额、状态、提交时间、截止时间和最近事件时间。
- 来源域允许动作摘要，不保存审批权威状态。

唯一约束：`(source_type, source_id)`。投影消费各领域 Outbox 事件；事件重复投递必须幂等。

`asset_outbox_events`（资产域 Outbox，与 `trade_outbox_events` 共同驱动审批投影）：

- `id`、`event_id`（唯一约束）。
- `aggregate_type`、`aggregate_id`。
- `event_type`、`payload`（JSON）、`status`、`attempts`。
- `created_at`、`processed_at`。
- 资产状态变更时在源事务内写入；审批投影器同时消费交易和资产两个 Outbox。

### 8.4 项目导出审批

新增 `project_export_requests`，由项目/导出域持有：

- Workspace、项目、项目版本和申请人。
- 导出范围、格式、水印策略、交付目标和内容快照摘要。
- 合规检查结果和证据引用。
- `PENDING`、`APPROVED`、`REJECTED`、`CANCELLED`、`EXPIRED` 状态。
- 审批人、审批意见、审批时间、行版本和审计字段。

批准后创建异步导出任务。`APPROVED` 只表示审批完成，不表示文件已经生成；导出过程和失败恢复由任务中心展示。

### 8.5 审计索引

`enterprise_audit_index` 保存 Workspace、部门、操作者、动作、对象、结果、来源域、来源记录 ID、`request_id`、脱敏摘要和时间。该表用于统一查询，可以从各域审计事件重建，不替代原始日志。

## 9. 核心流程

### 9.1 Workspace 切换

1. 前端通过 8080 BFF 查询 3001 的可用 Workspace。
2. 用户选择个人或企业 Workspace。
3. 前端查询目标 Membership 和权限范围。
4. 成功后保存当前 Workspace，清理旧 Workspace 缓存与订阅。
5. 后续请求携带 `X-Workspace-Id`；8080 每次受保护操作实时验证 Membership。
6. 切换失败时保持原 Workspace，不留下部分切换状态。

### 9.2 企业概览

1. `EnterpriseDashboardService` 先验证企业 Membership 和概览权限。
2. 并行读取 3001 账户/用量摘要、采购预算、审批投影、任务投影和资产数量。
3. 各来源返回独立 `updated_at` 和数据状态。
4. Membership 或 Workspace 校验失败时整页失败关闭；非授权类指标失败时仅对应卡片降级。
5. 返回后端计算的可见模块和 `allowed_actions`。

### 9.3 企业采购

1. 成员选择当前企业 Workspace、授权方案并填写理由。
2. 交易域校验 `trade.purchase.request`、对象范围和金额快照。
3. 采购预算域按月、范围和单笔上限原子校验并写 `RESERVE`。
4. 创建 `PENDING_APPROVAL` 采购申请并发布 Outbox 事件。
5. 审批人查看预算、共享余额摘要、授权条款和历史授权后批准或驳回。
6. 驳回写入原因并 `RELEASE`；批准后进入待付款，不自动扣款。
7. 申请人或具有 `trade.purchase.pay` 的成员明确确认付款。
8. 3001 钱包扣款成功后写 `CONSUME`；失败时保留可恢复状态，不重复扣款。
9. 退款或冲正确认成功后写 `REVERSE`。

### 9.4 企业资产发布

1. 资产域校验 `asset.publish.request` 并创建发布申请。
2. Outbox 事件更新统一审批投影。
3. 审批人以 `asset.publish.approve` 查看资产版本、公开信息和授权声明。
4. 企业工作台把幂等审批命令路由回资产域。
5. 资产域在自身事务中更新申请并创建或更新 Listing，再发布结果事件。

### 9.5 项目导出

1. 成员以 `project.export.request` 提交固定项目版本的导出申请。
2. 项目域完成必要的合规检查并创建 `PENDING` 请求。
3. 审批人以 `project.export.approve` 查看范围、水印、合规结果和交付目标。
4. 批准后项目域创建导出任务；驳回保存原因。
5. 任务中心跟踪排队、处理、成功和失败；企业审批收件箱只显示审批终态。

### 9.6 统一审批命令

1. 前端提交 `source_type`、`source_id`、决策、原因、`expected_version` 和幂等键。
2. Enterprise Facade 回源读取最新业务事实并重新鉴权。
3. `ApprovalCommandRouter` 根据来源类型调用交易、资产或项目域服务。
4. 命令响应只表示业务域已接受并返回最新状态。
5. 领域事件更新审批投影；投影延迟不能导致重复审批。

## 10. API 设计

### 10.1 3001

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/aicp/workspaces` | 当前用户可用个人/企业 Workspace |
| GET | `/api/aicp/workspaces/{id}` | Workspace 资料 |
| GET | `/api/aicp/workspaces/{id}/membership` | 当前用户 Membership 与权限范围 |
| GET/POST | `/api/aicp/workspaces/{id}/departments` | 部门查询与创建 |
| PATCH/DELETE | `/api/aicp/workspaces/{id}/departments/{departmentId}` | 部门变更与停用 |
| GET | `/api/aicp/workspaces/{id}/members` | 成员分页查询 |
| POST | `/api/aicp/workspaces/{id}/invitations` | 邀请成员 |
| PATCH | `/api/aicp/workspaces/{id}/members/{memberId}` | 部门、角色或状态变更 |
| GET/POST | `/api/aicp/workspaces/{id}/roles` | 角色查询与创建 |
| PATCH | `/api/aicp/workspaces/{id}/roles/{roleId}` | 角色权限变更 |
| GET | `/api/aicp/workspaces/{id}/billing-summary` | 余额与 AI 用量摘要 |
| GET/PATCH | `/api/aicp/workspaces/{id}/member-limits/{memberId}` | 成员 AI 限额 |

### 10.2 8080 BFF 与企业业务

所有路径使用当前可信 `X-Workspace-Id`，请求体中的 Workspace 仅可作为一致性提示。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/enterprise/context` | 当前企业上下文、菜单和能力 |
| GET | `/api/v1/enterprise/dashboard` | 角色化企业概览 |
| GET | `/api/v1/enterprise/approvals` | 统一审批分页查询 |
| GET | `/api/v1/enterprise/approvals/{type}/{id}` | 回源审批详情 |
| POST | `/api/v1/enterprise/approvals/{type}/{id}/decisions` | 幂等批准或驳回 |
| GET/PUT | `/api/v1/enterprise/budgets` | 采购预算查询与设置 |
| GET | `/api/v1/enterprise/budget-entries` | 采购预算流水 |
| GET | `/api/v1/enterprise/departments` | 3001 部门 BFF |
| POST/PATCH/DELETE | `/api/v1/enterprise/departments/**` | 3001 部门写操作 BFF |
| GET | `/api/v1/enterprise/members` | 3001 成员 BFF |
| POST | `/api/v1/enterprise/invitations` | 3001 邀请 BFF |
| PATCH | `/api/v1/enterprise/members/{memberId}` | 3001 成员写操作 BFF |
| GET/POST/PATCH | `/api/v1/enterprise/roles/**` | 3001 角色 BFF |
| GET | `/api/v1/enterprise/audit-events` | 跨域审计索引查询 |

写命令必须携带 `Idempotency-Key`。状态敏感命令必须同时携带 `expected_version`；版本不一致返回 `409`。

## 11. 错误与降级

- `3001` 不可用：企业 Membership、组织、权限、余额和写操作全部 fail-closed；公共市场等非企业公开能力不受影响。
- Membership 失效：返回统一无权错误，前端重新获取 Workspace 列表并回退个人 Workspace。
- 概览部分来源失败：保留成功卡片，失败卡片显示“数据暂不可用”和重试，不使用旧数据伪装实时数据。
- 审批投影延迟：详情和命令必须回源业务域；收件箱显示“状态同步中”。
- 并发审批或预算变更：返回 `409` 和最新版本摘要，前端刷新后重新确认。
- 预算不足：返回采购预算错误；钱包余额不足：返回 3001 余额错误。两类错误码和引导必须分开。
- 钱包结果未知：保持付款处理中并按原业务订单号查单，禁止换幂等键重复付款。
- Outbox 投递失败：业务事实保持有效，事件进入重试和告警；不得直接修改投影冒充业务完成。
- 无权查看对象：私有资源统一返回 `404`，避免跨 Workspace 枚举。

## 12. 安全与审计

- `/api/v1/enterprise/**` 纳入 `WorkspaceContextFilter`，不再使用整段路径的角色名称门禁。
- 后端依据权限码、权限范围、目标对象部门和当前状态计算 `allowed_actions`。
- 不能从请求体信任用户 ID、Workspace、部门、金额、余额或审批人。
- 邀请令牌只保存摘要并设置过期时间；敏感联系方式按既有安全规范存储和脱敏。
- 组织写操作、预算变更、审批、付款、导出和敏感审计查看必须记录操作者、Workspace、原因、前后摘要、幂等键、`request_id` 和结果。
- 开发环境不得使用“任意企业 Workspace 均授权”的宽松 Membership Mock。测试数据必须显式创建 Workspace 与成员关系。

## 13. 迁移方案

1. 盘点 8080 `enterprises`、`enterprise_members` 与现有业务表引用。
2. 在 3001 建立 Workspace、部门、成员、角色和权限数据，生成旧 ID 到 Workspace ID 的迁移映射。
3. 将 8080 项目、剧本、画布、资产和交易记录转换为稳定 `workspace_id` 引用。
4. 将旧 `can_*`、`ent_admin` 和 `dept_head` 映射为新权限码及数据范围。
5. 对 Workspace、成员状态、数量和业务归属执行离线核对与双读比对；不进行双写。
6. 切换 8080 登录后的 Workspace 发现、Membership 和企业 BFF 到 3001。
7. 将 8080 本地企业主数据表设为只读迁移归档，停止所有写接口。
8. 完成稳定期核对后删除静态企业 Controller 和本地主数据依赖。

迁移过程中无法确认 Workspace 归属的数据进入隔离清单，不得回退固定用户或默认企业。

## 14. 测试策略

### 14.1 3001

- Workspace 列表只返回当前用户有效 Membership。
- 部门树循环、跨 Workspace 父部门和非空部门删除均被拒绝。
- 邀请过期、重复接受、已存在成员和席位上限。
- 最后一名管理员保护和越权授予权限拒绝。
- Membership 权限码、部门和范围合约测试。

### 14.2 8080

- 3001 BFF 请求、错误码、超时和响应映射契约测试。
- `WorkspaceContext` 的 WORKSPACE、DEPARTMENT 和 SELF 范围测试。
- 企业接口缺失 Header、无 Membership、跨 Workspace 和权限不足测试。
- 预算并发预占、单笔上限、月度上限、释放、消费和冲回测试。
- 三类审批的批准、驳回、撤回、过期、版本冲突和幂等重放。
- 采购审批通过不自动付款；未知钱包结果不重复扣款。
- 审批投影重复事件、乱序事件和重建测试。
- 项目导出审批与导出任务状态分离测试。

### 14.3 前端与端到端

- 个人、多个企业 Workspace 的切换与缓存隔离。
- 管理员、部门负责人和普通成员的菜单、指标、数据范围和操作差异。
- 无企业、无权限、数据为空、部分指标失败、3001 不可用和 Membership 被移除。
- 采购申请到审批、付款、交付和预算消费闭环。
- 资产发布申请到 Listing 创建闭环。
- 项目导出申请到审批、导出任务和结果查看闭环。
- 浏览器刷新、返回、深链和过期会话行为。

## 15. 分期交付

实施分为三个里程碑：**M0**（阶段 1–2，Workspace 底座与企业外壳）、**M1**（阶段 3–4，预算与统一审批）、**M2**（阶段 5–6，导出审计与验收收尾）。

### 阶段 1：Workspace 与权限底座

- 3001 Workspace 列表、部门、成员、角色和范围能力。
- 8080 Account Center Adapter 与增强 WorkspaceContext。
- 前端 Workspace 切换和缓存隔离。
- 企业本地主数据盘点与迁移映射（写入冻结在阶段 6 执行）。

### 阶段 2：组织与角色化概览

- Enterprise Shell、组织成员页面和角色化概览。
- 余额、AI 用量、项目、任务和资产摘要聚合。
- 最后一名管理员保护及组织审计。

### 阶段 3：采购预算与采购审批

- 采购预算策略、不可变预算流水和并发控制。
- 统一审批投影的采购适配器。
- 审批、明确付款、预算消费和退款冲回闭环。

### 阶段 4：资产发布审批

- 统一审批投影的资产适配器。
- 企业工作台审批详情与资产市场深链。
- 发布、驳回、撤回和审计闭环。

> **实施说明：** 在 M1 中与阶段 3 合并实施——统一审批投影、命令路由和审批收件箱同时覆盖采购和资产发布两类审批，不单独拆分里程碑。

### 阶段 5：项目导出审批

- 项目导出申请状态机、合规证据和导出任务创建。
- 统一审批投影的导出适配器。
- 审批与异步导出过程分离展示。

### 阶段 6：跨域审计与验收

- 企业审计索引、来源回查和数据范围过滤。
- 故障降级、Outbox 告警、投影重建和端到端回归。
- 企业本地主数据写入冻结、旧静态页面、接口和本地主数据依赖清理。

## 16. 验收标准

- 用户可以在个人 Workspace 和多个企业 Workspace 间切换，数据、权限、余额和缓存不会串空间。
- 3001 修改成员、角色或状态后，8080 下一次受保护操作立即使用最新结果。
- 8080 不再写企业、部门、成员、角色、共享余额或 AI 限额副本。
- 管理员、部门负责人和普通成员只看到权限范围内的菜单、数据和动作。
- 采购申请并发提交不会突破单笔或月度预算；驳回、过期、付款和退款后的额度正确。
- 采购审批通过不会自动扣款，企业订单只使用当前企业 Workspace 钱包。
- 采购、资产发布和项目导出均可在统一审批中心处理，且原业务域仍是状态事实源。
- 企业资产入口复用现有 Workspace 资产库，没有第二套资产数据。
- 所有组织写操作、预算变更、审批、付款和导出具备完整审计与 `request_id`。
- 3001 不可用、权限撤销、投影延迟、版本冲突和钱包未知状态均不会造成越权、重复审批或重复扣款。
- 跨 Workspace 读取、部门范围越权和审批绕过测试全部拒绝。

## 17. 文档同步范围

实施时必须同步更新：

- `docs/01-core/用户端产品功能设计.md`：企业中心页面、权限码和 Workspace 切换。
- `docs/01-core/后端产品功能设计_V1.5.md`：3001/8080 边界、本地企业表下线和 Enterprise Facade。
- `docs/01-core/API接口文档_V1.5.md`：3001 账户中心与 8080 BFF 接口。
- `docs/01-core/new-api对接技术规划_V1.5.md`：清除旧 user-svc/影子用户口径。
- `docs/02-derived/流程图文档.md`：Workspace 切换、企业采购、资产发布和导出审批流程。

同步后全库检查并解释或删除以下旧口径：`user-svc 是账号事实源`、`new-api 影子用户`、`8080 企业成员主表`、`ent_admin/dept_head 统一门禁`、`审批通过自动扣款`。
