# 企业工作台开发任务安排

> 基于 [设计文档](../specs/2026-07-04-enterprise-workbench-completion-design.md) 和 M0/M1/M2 实施计划，按依赖关系排列可执行开发任务。
>
> 日期：2026-07-04

---

## 总体里程碑

| 里程碑 | 设计阶段 | 目标 | 预估范围 |
|--------|----------|------|----------|
| **M0** | 阶段 1–2 | Workspace 底座、组织管理、企业外壳、角色化概览 | 7 个任务，~21 个子任务 |
| **M1** | 阶段 3–4 | 采购预算、统一审批（采购+资产发布） | 7 个任务，~21 个子任务 |
| **M2** | 阶段 5–6 | 项目导出审批、跨域审计、迁移冻结、验收 | 7 个任务，~21 个子任务 |

---

## 代码库分工

| 代码库 | 技术栈 | 角色 |
|--------|--------|------|
| `new-api/` | Go + Gin + GORM + SQLite | 3001 账户中心：Workspace、部门、成员、角色、权限事实源 |
| `aicp-backend/` | Java 17 + Spring Boot 3 + MyBatis-Plus + H2/MySQL | 8080 业务域：BFF、WorkspaceContext、预算、审批投影、审计索引 |
| `aicp-frontend/` | Vue 3 + Pinia + Vue Router + Element Plus | 前端：Workspace 切换、企业外壳、审批收件箱、组织管理 |

---

## M0：Workspace 底座与企业外壳

### 任务依赖图

```text
Task 1 (3001 模型) ──→ Task 2 (3001 发现+Membership) ──→ Task 3 (3001 组织CRUD)
                │
                └──→ Task 4 (8080 WorkspaceContext) ──→ Task 5 (8080 BFF)
                                                                        │
                                                                        └──→ Task 6 (前端 切换+外壳) ──→ Task 7 (E2E)
```

### Task 1：3001 组织模型与迁移

- **代码库**：`new-api/`
- **依赖**：无
- **并行**：可独立启动

| 步骤 | 内容 | 文件 |
|------|------|------|
| 1.1 | 写模型测试（SQLite 内存库，验证 6 张新表建表+外键） | `model/aicp_workspace_test.go`（修改） |
| 1.2 | 跑测试确认失败 | — |
| 1.3 | 添加 `AicpWorkspace`、`AicpDepartment`、`AicpWorkspaceRole`、`AicpRolePermissionGrant`、`AicpWorkspaceInvitation` 模型；扩展 `AicpWorkspaceMember` 增加 `DepartmentID`/`RoleID`/`JoinedAt` | `model/aicp_workspace.go`（修改） |
| 1.4 | 在 `model/main.go` 双迁移列表注册 6 个模型 | `model/main.go`（修改） |
| 1.5 | 跑模型测试确认 PASS | — |
| 1.6 | 提交：`feat: model account-center organizations` | — |

### Task 2：3001 Workspace 发现与 Membership

- **代码库**：`new-api/`
- **依赖**：Task 1（模型就绪）
- **并行**：完成 Task 1 后启动

| 步骤 | 内容 | 文件 |
|------|------|------|
| 2.1 | 添加失败测试：`GET /api/aicp/workspaces` 返回活动 Workspace + 角色；Membership 含 `department_id`/`roles`/`permissions`/`permission_grants` | `controller/aicp_workspace_test.go`（修改） |
| 2.2 | 跑测试确认失败 | — |
| 2.3 | 实现 `ListActiveWorkspacesForUser`、丰富 `FindActiveWorkspaceMembership`，实现 `ListAicpWorkspaces`/`GetAicpWorkspaceMembership` handler | `controller/aicp_workspace.go`（修改） |
| 2.4 | 注册路由 `GET /workspaces`、`GET /workspaces/:id/membership` | `router/api-router.go`（修改） |
| 2.5 | 跑 controller 测试确认 PASS（含已有租户隔离测试） | — |
| 2.6 | 提交：`feat: expose workspace discovery and scoped membership` | — |

### Task 3：3001 组织管理 API（部门/成员/邀请/角色）

- **代码库**：`new-api/`
- **依赖**：Task 2（Membership 就绪）
- **并行**：完成 Task 2 后启动

| 步骤 | 内容 | 文件 |
|------|------|------|
| 3.1 | 添加失败测试：跨 Workspace 父部门拒绝、非空部门删除拒绝、重复邀请拒绝、席位上限拒绝、最后管理员保护、越权授予拒绝 | `controller/aicp_workspace_test.go`（修改） |
| 3.2 | 跑测试确认失败 | — |
| 3.3 | 实现部门 CRUD、成员列表/邀请/变更、角色 CRUD handler；所有 handler 从 Gin context 取 `aicp_user_id`，调 `FindActiveWorkspaceMembership`，按 `workspace_id` 约束 | `controller/aicp_workspace.go`（修改） |
| 3.4 | 注册路由（设计 Section 10.1 全部端点）；409 用于业务冲突、404 统一用于不可访问资源 | `router/api-router.go`（修改） |
| 3.5 | 跑 workspace controller 全量测试确认 PASS | — |
| 3.6 | 提交：`feat: manage workspace organizations` | — |

> **⚠️ 补充任务 3a（上一轮审查发现的缺口）：3001 账单摘要与成员限额端点**
>
> | 步骤 | 内容 | 文件 |
> |------|------|------|
> | 3a.1 | 添加失败测试：`GET /api/aicp/workspaces/{id}/billing-summary` 返回余额+AI 用量；`GET/PATCH /api/aicp/workspaces/{id}/member-limits/{memberId}` 读写成员限额 | `controller/aicp_workspace_test.go`（修改） |
> | 3a.2 | 实现 handler，复用 3001 现有余额/用量查询 | `controller/aicp_workspace.go`（修改） |
> | 3a.3 | 注册路由 | `router/api-router.go`（修改） |
> | 3a.4 | 提交：`feat: expose billing summary and member limits` | — |

### Task 4：8080 WorkspaceContext 增强

- **代码库**：`aicp-backend/`
- **依赖**：Task 2（3001 Membership 契约确定）
- **并行**：完成 Task 2 后启动，可与 Task 3 并行

| 步骤 | 内容 | 文件 |
|------|------|------|
| 4.1 | 添加失败测试：`canAccess(permission, dept, userId)` 验证 WORKSPACE/DEPARTMENT/SELF 三级范围 | `WorkspaceAccessServiceTest.java`（修改） |
| 4.2 | 跑测试确认编译失败 | — |
| 4.3 | 创建 `PermissionGrant` record；`WorkspaceContext` 加 `departmentId`/`grants`；实现 `canAccess`；解析 3001 丰富 Membership 响应 | `WorkspaceContext.java`（修改）、`AccountCenterPermissionClient.java`（修改） |
| 4.4 | 移除开发环境宽松 Membership fallback；`/api/v1/enterprise/**` 加入 `WorkspaceContextFilter`；替换 `ent_admin/dept_head` 门禁为认证+权限检查 | `WorkspaceContextFilter.java`（修改）、`SecurityConfig.java`（修改） |
| 4.5 | 跑 workspace + security 测试确认 PASS | — |
| 4.6 | 提交：`feat: enforce scoped workspace authorization` | — |

### Task 5：8080 Enterprise BFF

- **代码库**：`aicp-backend/`
- **依赖**：Task 3（3001 组织 API 可用）、Task 4（WorkspaceContext 就绪）
- **并行**：完成 Task 3+4 后启动

| 步骤 | 内容 | 文件 |
|------|------|------|
| 5.1 | 添加失败 facade 测试：context 返回 workspace 身份/菜单/`allowedActions`；组织变更转发 3001；404/409/503 错误透传 | `EnterpriseAccountFacadeTest.java`（新建） |
| 5.2 | 跑测试确认失败 | — |
| 5.3 | 创建 `EnterpriseViews`（`EnterpriseContextView`/`DepartmentView`/`MemberView`/`RoleView`）；`AccountCenterEnterpriseClient` 类型化 HTTP 客户端（透传 bearer token）；`EnterpriseAccountFacade` 聚合 context + 部门/成员/邀请/角色操作 | `dto/EnterpriseViews.java`（新建）、`service/AccountCenterEnterpriseClient.java`（新建）、`service/EnterpriseAccountFacade.java`（新建） |
| 5.4 | 替换 `EnterpriseController`：从 `WorkspaceContext` 取身份，删除所有硬编码 map | `controller/EnterpriseController.java`（替换） |
| 5.5 | 跑 enterprise 后端测试确认 PASS | — |
| 5.6 | 提交：`feat: proxy enterprise organization management` | — |

### Task 6：前端 Workspace 切换与企业外壳

- **代码库**：`aicp-frontend/`
- **依赖**：Task 5（8080 BFF 可用）
- **并行**：完成 Task 5 后启动

| 步骤 | 内容 | 文件 |
|------|------|------|
| 6.1 | 添加失败状态测试：`selectWorkspace` 失败保留旧 Workspace、成功清缓存、Membership 失效回退 `personal_{uid}` | `tests/enterprise-workspace-state.test.js`（新建） |
| 6.2 | 跑测试确认失败 | — |
| 6.3 | 实现 `workspaceState.js`（纯函数：`commitWorkspaceSelection`/`personalFallback`）；`workspace.js`（Pinia：`loadWorkspaces`/`selectWorkspace`/`fallbackToPersonal`） | `stores/workspaceState.js`（新建）、`stores/workspace.js`（新建） |
| 6.4 | 实现 `EnterpriseShell.vue`（子导航）、`EnterpriseOverview.vue`（角色化概览）、`EnterpriseOrganization.vue`（部门树+成员表） | `views/enterprise/EnterpriseShell.vue`（新建）、`views/enterprise/EnterpriseOverview.vue`（新建）、`views/enterprise/EnterpriseOrganization.vue`（新建） |
| 6.5 | 修改 `Topbar.vue`（Workspace 切换器）、`router/index.js`（嵌套 enterprise 路由）、`auth.js`（登录后用 workspace store 初始化替代 `deriveAndStoreWorkspace`） | `components/Topbar.vue`（修改）、`router/index.js`（修改）、`stores/auth.js`（修改） |
| 6.6 | 创建 `api/enterprise.js`（BFF 客户端） | `api/enterprise.js`（新建） |
| 6.7 | 跑测试 + Vite build 确认 PASS | — |
| 6.8 | 提交：`feat: add enterprise workspace shell` | — |

### Task 7：M0 端到端验证

- **代码库**：`aicp-backend/` + `aicp-frontend/`
- **依赖**：Task 6（前端就绪）
- **并行**：M0 收尾

| 步骤 | 内容 | 文件 |
|------|------|------|
| 7.1 | 添加后端 E2E：个人+两个企业 Workspace、管理员/部门负责人上下文、跨 Workspace 拒绝、最后管理员保护、Membership 失效回退 | `EnterpriseFoundationE2ETest.java`（新建） |
| 7.2 | 完善前端状态测试：覆盖个人/企业切换全路径 | `tests/enterprise-workspace-state.test.js`（修改） |
| 7.3 | 跑全部 M0 测试套件确认 PASS | — |
| 7.4 | 提交：`test: verify enterprise workspace foundation` | — |

---

## M1：预算与统一审批

### 任务依赖图

```text
Task 1 (预算 Schema) ──→ Task 2 (预算服务) ──→ Task 3 (采购生命周期)
                                                    │
Task 1 (预算 Schema) ──→ Task 4 (审批投影+路由) ──→ Task 5 (审批 API)
                                                    │
                                                    └──→ Task 6 (前端 预算+审批页) ──→ Task 7 (E2E)
```

### Task 1：采购预算与审批投影 Schema

- **代码库**：`aicp-backend/`
- **依赖**：M0 完成
- **并行**：可独立启动

| 步骤 | 内容 | 文件 |
|------|------|------|
| 1.1 | 添加失败 schema 测试：验证 `enterprise_purchase_budgets`/`enterprise_purchase_budget_entries`/`enterprise_approval_items` 三表；金额列 `BIGINT`；预算 `(workspace, subject, month)` 唯一；审批 `(source_type, source_id)` 唯一 | `EnterpriseGovernanceSchemaTest.java`（新建） |
| 1.2 | 跑测试确认失败 | — |
| 1.3 | 创建 V7 迁移：budgets（`amount_cents`/`single_limit_cents`/`reserved_cents`/`consumed_cents`/`row_version`）、budget_entries（不可变流水+`idempotency_key` 唯一）、approval_items（source 信息+状态+`row_version`） | `db/migration/V7__enterprise_budget_and_approval_projection.sql`（新建）；同步 H2/MySQL schema | 1.5 | 跑 schema 测试确认 PASS | — |
| 1.6 | 提交：`feat: add enterprise governance schema` | — |

### Task 2：采购预算原子记账

- **代码库**：`aicp-backend/`
- **依赖**：Task 1（表就绪）
- **并行**：完成 Task 1 后启动

| 步骤 | 内容 | 文件 |
|------|------|------|
| 2.1 | 添加失败测试：`reserve`/`release`/`consume`/`reverse`；单笔/月度上限拒绝；幂等键重放；并发预留不超限 | `PurchaseBudgetServiceTest.java`（新建） |
| 2.2 | 跑测试确认失败 | — |
| 2.3 | 实现 `PurchaseBudgetService`：条件 SQL 更新（`reserved+consumed+amount <= amount_cents` + `row_version`）；同事务写不可变 entry | `entity/EnterprisePurchaseBudget.java`（新建）、`entity/EnterprisePurchaseBudgetEntry.java`（新建）、`mapper/`（新建）、`service/PurchaseBudgetService.java`（新建） |
| 2.4 | 跑预算测试确认 PASS | — |
| 2.5 | 提交：`feat: account for procurement budgets` | — |

### Task 3：采购生命周期接入预算

- **代码库**：`aicp-backend/`
- **依赖**：Task 2（预算服务可用）、M0（WorkspaceContext 可用）
- **并行**：完成 Task 2 后启动

| 步骤 | 内容 | 文件 |
|------|------|------|
| 3.1 | 添加失败测试：提交=预留、拒绝/取消/过期=释放、批准=仅改状态不扣款、钱包成功=消费、退款=冲回、钱包未知=不重复操作 | `EnterprisePurchaseBudgetIntegrationTest.java`（新建） |
| 3.2 | 跑测试确认失败 | — |
| 3.3 | `PurchaseRequest` 加 `budgetSubjectType`/`budgetSubjectId`/`budgetReservationEntryId`；从 `WorkspaceContext` 取身份（不取 `req.workspaceId()`）；用稳定幂等键 `purchase:{id}:reserve` 等 | `trade/entity/PurchaseRequest.java`（修改）、`trade/service/PurchaseApprovalService.java`（修改）、`trade/service/OrderService.java`（修改）、`trade/service/RefundService.java`（修改） |
| 3.4 | 跑 trade 测试确认 PASS | — |
| 3.5 | 提交：`feat: enforce enterprise purchase budgets` | — |

### Task 4：统一审批投影与命令路由

- **代码库**：`aicp-backend/`
- **依赖**：Task 1（approval_items 表就绪）、M0（WorkspaceContext 可用）
- **并行**：完成 Task 1 后启动，可与 Task 2–3 并行

| 步骤 | 内容 | 文件 |
|------|------|------|
| 4.1 | 添加失败测试：重复事件幂等、乱序事件仅高版本覆盖、部门范围过滤、PURCHASE+ASSET_PUBLISH 命令路由 | `ApprovalProjectionTest.java`（新建） |
| 4.2 | 跑测试确认失败 | — |
| 4.3 | 定义 `ApprovalType { PURCHASE, ASSET_PUBLISH, PROJECT_EXPORT }`（PROJECT_EXPORT 预留 M2）；`ApprovalDecisionCommand`；**修改 V7 迁移**追加 `asset_outbox_events` 表（参见设计 Section 8.3） | `entity/EnterpriseApprovalItem.java`（新建）、`mapper/EnterpriseApprovalItemMapper.java`（新建） |
| 4.4 | 实现 `ApprovalProjector`（只写 `enterprise_approval_items`）；`ApprovalCommandRouter`（回源读事实→鉴权→调业务服务）；采购 Outbox→`TradeOutboxEvent`；资产 Outbox→`AssetOutboxEvent` | `service/ApprovalProjector.java`（新建）、`service/ApprovalCommandRouter.java`（新建）、`asset/entity/AssetOutboxEvent.java`（新建） |
| 4.5 | 跑投影测试确认 PASS | — |
| 4.6 | 提交：`feat: project unified enterprise approvals` | — |

### Task 5：预算与审批 API

- **代码库**：`aicp-backend/`
- **依赖**：Task 2（预算）、Task 4（审批投影）
- **并行**：完成 Task 2+4 后启动

| 步骤 | 内容 | 文件 |
|------|------|------|
| 5.1 | 添加失败 API 测试：服务端分页、`mine/submitted/processed` 桶、部门过滤、来源详情、驳回原因必填、`Idempotency-Key`、`expected-version` 冲突、无权限拒绝 | `EnterpriseGovernanceApiTest.java`（新建） |
| 5.2 | 跑测试确认失败 | — |
| 5.3 | 实现 `EnterpriseBudgetController`（`/api/v1/enterprise/budgets`、`/budget-entries`）、`EnterpriseApprovalController`（`/approvals`、`/approvals/{type}/{id}`、`/approvals/{type}/{id}/decisions`）；返回 `allowed_actions` | `controller/EnterpriseBudgetController.java`（新建）、`controller/EnterpriseApprovalController.java`（新建）、`dto/ApprovalViews.java`（新建） |
| 5.4 | 跑 enterprise API 测试确认 PASS | — |
| 5.5 | 提交：`feat: expose enterprise budgets and approvals` | — |

### Task 6：前端预算与审批页面

- **代码库**：`aicp-frontend/`
- **依赖**：Task 5（API 可用）、M0 Task 6（企业外壳就绪）
- **并行**：完成 Task 5 后启动

| 步骤 | 内容 | 文件 |
|------|------|------|
| 6.1 | 添加失败状态测试：独立 loading/error 态、服务端分页序列化、驳回原因必填、冲突触发刷新、预算/钱包错误区分、Workspace 切换重置 | `tests/enterprise-governance-state.test.js`（新建） |
| 6.2 | 跑测试确认失败 | — |
| 6.3 | 实现 `EnterpriseBudgets.vue`（采购预算策略 vs 3001 钱包/AI 用量分开展示）、`EnterpriseApprovals.vue`（待我处理/我发起/已处理 tab + 筛选 + 来源专属证据）、`ApprovalDetailDrawer.vue` | `views/enterprise/EnterpriseBudgets.vue`（新建）、`views/enterprise/EnterpriseApprovals.vue`（新建）、`views/enterprise/components/ApprovalDetailDrawer.vue`（新建） |
| 6.4 | 创建 `enterpriseState.js`（治理相关前端状态） | `views/enterprise/enterpriseState.js`（新建） |
| 6.5 | 更新 `api/enterprise.js`（预算+审批端点）、`router/index.js`（新路由） | 修改已有文件 |
| 6.6 | 跑测试 + build 确认 PASS | — |
| 6.7 | 提交：`feat: add enterprise budget and approval UI` | — |

### Task 7：M1 端到端验证

- **代码库**：`aicp-backend/` + `aicp-frontend/`
- **依赖**：Task 6
- **并行**：M1 收尾

| 步骤 | 内容 | 文件 |
|------|------|------|
| 7.1 | 添加 E2E：采购提交→预留→批准→付款→消费；拒绝→释放；资产发布→批准→上架；跨部门拒绝；重复决策幂等 | `EnterpriseApprovalE2ETest.java`（新建） |
| 7.2 | 跑全部 M0+M1 测试确认 PASS | — |
| 7.3 | 提交：`test: verify enterprise governance journeys` | — |

---

## M2：导出审计与验收

### 任务依赖图

```text
Task 1 (导出状态机) ──→ Task 2 (导出审批适配器)
                            │
Task 3 (审计索引) ──────────┤
                            │
                            └──→ Task 4 (前端 导出+审计页)
                                        │
Task 5 (迁移冻结) ──────────────────────┤
                                        │
                                        └──→ Task 6 (文档同步) ──→ Task 7 (验收)
```

### Task 1：项目导出审批状态机

- **代码库**：`aicp-backend/`
- **依赖**：M1 完成
- **并行**：可独立启动

| 步骤 | 内容 | 文件 |
|------|------|------|
| 1.1 | 添加失败测试：提交固定版本+导出范围；批准建一个导出任务；拒绝需原因；仅 PENDING 可取消；过期不可批准；APPROVED ≠ 导出成功 | `ProjectExportApprovalServiceTest.java`（新建） |
| 1.2 | 跑测试确认失败 | — |
| 1.3 | 创建 V8 迁移（`project_export_requests`）；状态 `PENDING/APPROVED/REJECTED/CANCELLED/EXPIRED`；存 workspace/dept/project/version/scope/format/watermark/delivery/compliance/row_version | `db/migration/V8__project_export_approval.sql`（新建）；同步 H2/MySQL |
| 1.4 | 实现 `ProjectExportRequest` entity、mapper、`ProjectExportApprovalService`；需要 `project.export.request` 或 `project.export.approve` 权限 | `entity/ProjectExportRequest.java`（新建）、`mapper/`、`service/ProjectExportApprovalService.java`（新建） |
| 1.5 | 跑导出测试确认 PASS | — |
| 1.6 | 提交：`feat: add project export approval` | — |

### Task 2：导出审批接入统一收件箱

- **代码库**：`aicp-backend/`
- **依赖**：Task 1（导出状态机）、M1 Task 4（审批投影+路由）
- **并行**：完成 Task 1 后启动

| 步骤 | 内容 | 文件 |
|------|------|------|
| 2.1 | 添加失败测试：导出事件→PROJECT_EXPORT 收件箱行；详情含合规证据+水印策略；审批路由回项目服务；审批后展示任务链接；任务状态不投射到审批状态 | `ProjectExportApprovalAdapterTest.java`（新建） |
| 2.2 | 跑测试确认失败 | — |
| 2.3 | 导出状态变更时发 Outbox 事件；审批投影器消费并写 `enterprise_approval_items`；`ApprovalCommandRouter` 加 `PROJECT_EXPORT` case 路由到 `ProjectExportApprovalService`（确认 M1 已预留枚举值） | `service/ApprovalProjector.java`（修改）、`service/ApprovalCommandRouter.java`（修改）、`service/ProjectExportApprovalService.java`（修改） |
| 2.4 | 跑审批回归测试确认 PASS | — |
| 2.5 | 提交：`feat: surface export approvals in enterprise inbox` | — |

### Task 3：企业审计索引

- **代码库**：`aicp-backend/`
- **依赖**：M1 完成
- **并行**：可与 Task 1–2 并行

| 步骤 | 内容 | 文件 |
|------|------|------|
| 3.1 | 添加失败测试：重复事件幂等、Workspace/部门过滤、脱敏摘要、来源引用保留、源记录 404 处理 | `EnterpriseAuditProjectorTest.java`（新建） |
| 3.2 | 跑测试确认失败 | — |
| 3.3 | 创建 V9 迁移（`enterprise_audit_index`：workspace/dept/actor/action/object/source_domain/source_record_id/request_id/redacted_summary/event_id） | `db/migration/V9__enterprise_audit_index.sql`（新建） |
| 3.4 | 实现 `EnterpriseAuditIndex` entity、mapper、`EnterpriseAuditProjector`（消费各域事件写索引）、`EnterpriseAuditController`（`/api/v1/enterprise/audit-events` 分页+筛选，需要 `enterprise.audit.view` + grant scope） | `entity/EnterpriseAuditIndex.java`（新建）、`mapper/`、`service/EnterpriseAuditProjector.java`（新建）、`controller/EnterpriseAuditController.java`（新建） |
| 3.5 | 跑审计测试确认 PASS | — |
| 3.6 | 提交：`feat: index enterprise audit events` | — |

### Task 4：前端导出与审计页面 + 降级态

- **代码库**：`aicp-frontend/`
- **依赖**：Task 2（导出收件箱）、Task 3（审计 API）、M1 Task 6（审批页就绪）
- **并行**：完成 Task 2+3 后启动

| 步骤 | 内容 | 文件 |
|------|------|------|
| 4.1 | 添加失败状态测试：导出专属证据展示、审批→任务链接、审计筛选序列化、概览卡片部分失败降级、投影同步中标签、Membership 被移除回退、预算 vs 钱包错误区分 | `tests/enterprise-export-audit-state.test.js`（新建） |
| 4.2 | 跑测试确认失败 | — |
| 4.3 | 实现 `EnterpriseAudit.vue`（筛选+列表）；更新 `EnterpriseApprovals.vue`/`ApprovalDetailDrawer.vue`（导出专属证据+任务链接）；更新 `EnterpriseOverview.vue`（卡片独立降级+来源更新时间+重试） | `views/enterprise/EnterpriseAudit.vue`（新建）；修改已有文件 |
| 4.4 | 3001 Membership 错误→整页 fail-closed；非授权指标失败→仅对应卡片降级；409→刷新源详情后再启用操作 | 在现有组件中实现 |
| 4.5 | 更新 `api/enterprise.js`（审计端点） | `api/enterprise.js`（修改） |
| 4.6 | 跑测试 + build 确认 PASS | — |
| 4.7 | 提交：`feat: complete enterprise export and audit UI` | — |

### Task 5：企业主数据迁移与写入冻结

- **代码库**：`aicp-backend/`
- **依赖**：M0+M1 全部功能就绪（所有读写已切换到 3001）
- **并行**：M2 最后执行，不可提前

> **背景说明：** 设计文档阶段 1 已规划“企业本地主数据盘点与迁移映射”，阶段 6 执行“写入冻结”。本任务覆盖完整迁移执行——映射核对、业务引用转换、写入冻结和旧表归档。迁移映射和双读验证应在 M0/M1 期间提前完成，M2 执行最终切换。

| 步骤 | 内容 | 文件 |
|------|------|------|
| 5.1 | 添加失败测试：稳定 legacy→Workspace 映射、业务引用转换、数量/状态核对、重跑幂等、无法确认归属的数据隔离、切换后拒绝本地写入 | `EnterpriseMasterDataMigrationServiceTest.java`（新建） |
| 5.2 | 跑测试确认失败 | — |
| 5.3 | 实现 `EnterpriseMasterDataMigrationService`：输出报告（migrated/matched/quarantined/failed）；无法映射的数据不得回退到 user `1` 或默认 Workspace | `service/EnterpriseMasterDataMigrationService.java`（新建） |
| 5.4 | 移除 `/enterprise/register`、本地 profile/member 写操作；旧表保留只读至稳定期结束 | `controller/EnterpriseController.java`（修改）、`db/schema.sql`（修改） |
| 5.5 | 跑迁移+enterprise 全量测试确认 PASS | — |
| 5.6 | 提交：`refactor: retire local enterprise master writes` | — |

### Task 6：架构文档同步

- **代码库**：`docs/`
- **依赖**：Task 5（迁移完成，架构定型）
- **并行**：M2 收尾

| 步骤 | 内容 | 文件 |
|------|------|------|
| 6.1 | 以设计文档为权威口径，更新 5 份核心文档：3001 主权、8080 BFF 职责、Workspace 切换、范围权限、采购预算、三类审批、审批后显式付款 | `docs/01-core/用户端产品功能设计.md`、`后端产品功能设计_V1.5.md`、`API接口文档_V1.5.md`、`new-api对接技术规划_V1.5.md`、`docs/02-derived/流程图文档.md` |
| 6.2 | 全库扫描旧口径并清理/标注废止：`user-svc 是账号事实源`、`new-api 影子用户`、`8080 企业成员主表`、`ent_admin/dept_head 统一门禁`、`审批通过自动扣款` | — |
| 6.3 | 提交：`docs: align enterprise workspace architecture` | — |

### Task 7：最终验收

- **代码库**：全部
- **依赖**：Task 1–6 全部完成
- **并行**：M2 收尾

| 步骤 | 内容 | 文件 |
|------|------|------|
| 7.1 | 添加验收场景：多 Workspace 隔离、即时权限撤销、管理员/部门负责人/成员三视图、预算并发、采购/资产/导出三类审批、显式付款、审计关联、3001 不可用、投影延迟、乐观冲突、钱包未知 | `EnterpriseWorkbenchAcceptanceTest.java`（新建） |
| 7.2 | 跑全部三层测试套件：3001 Go 测试、8080 Java 测试、前端 Node 测试 + build | — |
| 7.3 | 确认所有命令 exit 0；无跨 Workspace 或审批绕过断言被接受 | — |
| 7.4 | 提交：`test: verify enterprise workbench acceptance` | — |

---

## 并行执行建议

```text
时间线 →

M0:
  3001:  Task 1 ──→ Task 2 ──→ Task 3 ──→ Task 3a
  8080:            Task 4 ──────────────→ Task 5
  前端:                                  Task 6 ──→ Task 7(E2E)

M1:
  8080:  Task 1 ──→ Task 2 ──→ Task 3
                 └──→ Task 4 ──→ Task 5
  前端:                          Task 6 ──→ Task 7(E2E)

M2:
  8080:  Task 1 ──→ Task 2
        Task 3 ────────────────→ Task 4
                                  Task 5 ──→ Task 6(docs) ──→ Task 7(acceptance)
  前端:                          Task 4
```

### 可并行组合

| 组合 | 任务 | 说明 |
|------|------|------|
| M0-A | Task 1 + Task 4 前半 | 3001 模型定义 + 8080 PermissionGrant record 可同步编写 |
| M0-B | Task 3 + Task 4 后半 | 3001 组织 API + 8080 WorkspaceContext 实现互不冲突 |
| M1-A | Task 2 + Task 4 | 预算服务 + 审批投影互不依赖 |
| M1-B | Task 3 + Task 5 | 采购集成 + 审批 API 可并行开发 |
| M2-A | Task 1 + Task 3 | 导出状态机 + 审计索引完全独立 |
| M2-B | Task 4 + Task 5 | 前端页面 + 迁移脚本互不冲突 |

---

## 关键风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| 3001 billing-summary 端点未在 M0 实现 | 企业概览页余额/AI 用量卡片无数据源 | 已在 Task 3a 补充，M0 内完成 |
| 8080 旧企业表有未知引用 | 迁移时遗漏导致业务断裂 | M0/M1 期间并行盘点，`rg` 全库搜索 `enterprises`/`enterprise_members` 引用 |
| 资产 Outbox 事件模型与交易不一致 | 审批投影器消费逻辑复杂 | 设计 Section 8.3 已定义统一字段，Task 4 按统一契约实现 |
| 前端三份计划独立测试文件 | M0/M1/M2 测试覆盖有交集或遗漏 | 命名规范 `enterprise-{domain}-state.test.js`，M2 Task 7 全量回归 |
| 设计 Phase 1 迁移映射在 M0 无任务 | M2 迁移缺少输入 | M0/M1 期间安排专项盘点（见下方前置任务） |

---

## 跨里程碑前置任务

以下任务不在三个计划的 Task 列表中，但必须在对应里程碑完成前执行：

| 前置任务 | 负责 | 截止 | 说明 |
|----------|------|------|------|
| 8080 本地企业数据盘点 | 后端 | M0 结束前 | 扫描 `enterprises`/`enterprise_members` 表及所有外键引用，生成迁移映射草稿 |
| 旧权限码→新权限码映射表 | 后端 | M0 结束前 | `can_*`/`ent_admin`/`dept_head` → 新权限码 + 数据范围的对照表 |
| 3001 余额/AI 用量查询接口确认 | 3001 | M0 Task 3a | 确认 3001 现有余额/用量模型能否直接支持 billing-summary 端点 |
| 前端通用组件提取 | 前端 | M1 开始前 | `ApprovalDetailDrawer` 需支持三类审批的专属证据槽位 |
