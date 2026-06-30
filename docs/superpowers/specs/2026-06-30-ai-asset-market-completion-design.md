# AI 资产市场完善设计

## 1. 背景与现状

当前 `http://localhost:8080/asset-market` 仅具备静态演示能力：

- 前端 `AssetMarket.vue` 使用四条硬编码风格模型数据，分类切换不会改变数据。
- “应用到画布”只显示成功提示，没有选择项目、写入项目配置或保存应用记录。
- 搜索、筛选、分页、详情、收藏、领取、Workspace 资产库、发布和企业审批均未形成可用流程。
- 后端 `AssetController` 只返回 Mock 风格模型；角色、场景、提示词、音色和声音接口返回空数组。
- 收藏、下载、上架、编辑和应用接口没有持久化，也没有服务层、权限校验和租户过滤。
- 数据库存在多套不一致的市场资产表定义，尚未形成可执行的领域模型。

本设计在现有 Vue 3 + Spring Boot 模块化单体中完成真实的免费资产闭环，不拆分微服务。

## 2. 已确认的产品决策

1. `8080` 是唯一的资产市场业务操作界面。用户不需要跳转到 `3001`。
2. `3001` 是用户、个人空间、企业空间、成员关系、角色和权限的事实源。`8080` 后端负责调用和映射这些权限。
3. 资产本体始终归属于一个个人或企业 Workspace，默认不公开。
4. 公共市场全平台可见，但只展示用户主动发布的市场 Listing；公开不改变资产所有权。
5. 个人资产由本人直接发布。企业资产必须由成员提交申请，并由企业管理员审核通过后发布。
6. 企业成员默认可以浏览本企业资产；使用、编辑、删除、提交公开和审批由 RBAC 分别控制。
7. 本期完成免费资产的发布、发现、领取授权、入库和应用闭环。付费交易只预留扩展字段和接口边界。
8. 本期完整支持风格模型、角色、场景和提示词。音色与 BGM 保留入口并显示“即将开放”。
9. 领取后资产先进入当前 Workspace 资产库；用户既可立即选择项目应用，也可稍后从画布资产面板调用。

## 3. 范围与非目标

### 3.1 本期范围

- 公共资产市场：搜索、分类、标签、排序、分页和详情。
- Workspace 资产库：我创建的、市场领取的、项目生成的和收藏的资产。
- 个人资产直接发布、撤下和归档。
- 企业资产发布申请、管理员审批、驳回和审计。
- 免费领取授权和 Workspace 隔离资产记录。
- 风格模型、角色、场景和提示词应用到项目。
- 权限映射、后端强制租户过滤、幂等、错误处理和审计。
- 加载态、骨架屏、空态、错误重试和权限受限状态。

### 3.2 非目标

- 付费下单、支付、退款、卖家分成和企业采购审批。
- 用户评分、评论、举报、版权仲裁和推荐算法。
- 音色试听、BGM 播放及其项目应用。
- 独立搜索引擎、独立资产市场微服务或跨地域 CDN 改造。
- 重构与资产市场无关的画布、剧本或账户中心功能。

## 4. 系统边界与租户上下文

### 4.1 单一操作入口

浏览器只访问 `8080` 的市场、资产库、发布、审批和画布页面。`8080` 后端作为业务 BFF：

1. 从认证会话取得当前 `user_id` 和 `workspace_id`。
2. 调用 `3001` 校验用户是否为该 Workspace 的有效成员，并读取权限集合。
3. 将 `3001` 权限映射为资产市场权限。
4. 向 Repository 注入经过校验的 `workspace_id`。
5. 执行业务操作并记录操作者、Workspace 和审计信息。

用户始终停留在 `8080`。个人或企业上下文由 `8080` 当前登录会话和组织上下文决定，不允许请求体自行指定可信 Workspace。

### 4.2 权限映射

| 8080 权限 | 能力 |
|---|---|
| `asset.view` | 浏览当前个人或企业 Workspace 资产 |
| `asset.use` | 领取公共资产并应用到项目 |
| `asset.manage` | 创建、编辑、更新版本和撤下资产 |
| `asset.publish.request` | 提交企业资产公开申请 |
| `asset.publish.approve` | 审批或驳回企业公开申请 |
| `asset.delete` | 归档资产 |

个人 Workspace 的所有者拥有上述个人资产操作能力，但不存在企业审批动作。企业权限由 `3001` 返回的成员角色和权限决定。前端按权限隐藏或禁用按钮，后端仍对每次操作执行最终校验。

### 4.3 强制隔离规则

- Workspace 私有查询必须包含服务端注入的 `workspace_id`。
- 客户端传入的 `workspace_id` 只可作为一致性提示，不能作为授权依据；与会话上下文不一致时返回 `403`。
- 对不存在或不属于当前 Workspace 的私有资源统一返回 `404`，避免 ID 枚举。
- 公共市场查询只读取状态为 `LISTED` 的 Listing 快照，不直接读取卖方私有资产。
- 公共 API 只暴露 `listing_id`；卖方私有 `asset_id` 不能作为跨租户访问入口。
- Repository 提供按 Workspace 查询的方法，不提供业务层可直接调用的无租户私有查询方法。

## 5. 页面信息架构

### 5.1 顶级频道

`/asset-market` 内提供以下频道：

1. **公共市场**：全平台已公开资产。
2. **我的资产 / 企业资产**：名称随当前 Workspace 类型变化。
3. **发布管理**：本人或当前企业资产的公开状态和历史。
4. **审批中心**：仅拥有 `asset.publish.approve` 权限时展示。

### 5.2 公共市场

- 搜索：资产名称、作者昵称和标签。
- 分类：全部、风格模型、角色、场景、提示词；音色和 BGM 显示“即将开放”且不可进入领取流程。
- 筛选：风格、题材、来源、是否已领取。
- 排序：综合、最新、热门、评分。评分字段本期只展示种子数据或平台值，不开放用户评分入口。
- 卡片：预览、名称、类型、作者、标签、使用量、免费状态、是否已领取。
- 分页：服务端分页，默认每页 20 条。
- 详情：多图预览、说明、版本、兼容信息、推荐参数、授权范围、作者、领取状态和操作按钮。

### 5.3 Workspace 资产库

- 视图：全部、我创建的、市场领取、项目生成、收藏。
- 状态：私有、企业可见、申请中、已公开、已撤下、已归档。
- 操作：查看、编辑、创建版本、应用项目、提交公开、撤下和归档。
- 企业成员默认可浏览企业资产；具体操作按钮由权限控制。

### 5.4 发布与审批

- 个人资产：本人填写公开信息并直接发布。
- 企业资产：成员提交申请；审批中心展示资产预览、来源、描述、授权声明和申请人。
- 管理员必须填写驳回原因；通过时自动创建或更新 Listing。
- 审批详情展示完整状态历史和操作人。

## 6. 领域模型

### 6.1 `workspace_assets`

Workspace 内的资产本体。

| 字段 | 说明 |
|---|---|
| `id`, `uuid` | 内部主键与稳定标识 |
| `workspace_id`, `workspace_type` | 所属个人或企业空间 |
| `creator_user_id` | 创建人 |
| `asset_type` | `checkpoint`、`lora`、`style_pack`、`character`、`scene`、`prompt` |
| `name`, `description`, `tags` | 基础信息 |
| `access_scope` | `PRIVATE` 或 `WORKSPACE`；公共可见性由 Listing 表示 |
| `source_type` | `CREATED`、`MARKET_CLAIMED`、`PROJECT_GENERATED`、`IMPORTED` |
| `source_listing_id`, `source_version_id` | 市场领取来源，可为空 |
| `current_version_id` | 当前版本 |
| `status` | `ACTIVE` 或 `ARCHIVED` |
| `row_version` | 乐观锁版本 |
| 审计字段 | 创建、更新人和时间 |

个人资产使用 `PRIVATE`。企业资产使用 `WORKSPACE`，企业成员可见。页面展示的“已公开”是根据有效 Listing 派生的状态，不在资产本体重复保存，避免双状态不一致。

### 6.2 `asset_versions`

保存不可变资产版本：`asset_id`、版本号、结构化 `metadata`、预览地址、内容引用、校验和、创建人和创建时间。Listing 和领取权益固定引用一个版本，后续源资产更新不会静默改变已领取内容。

### 6.3 `market_listings`

公共市场展示记录：

- `publisher_workspace_id`、`publisher_user_id`
- `source_asset_id`、`source_version_id`
- `public_snapshot`：名称、说明、标签、预览、作者展示信息和推荐参数快照
- `license_type`：本期固定为 `FREE`
- `price`：本期固定为 `0`，保留未来扩展
- `status`：`LISTED`、`UNLISTED`、`REMOVED`
- 使用量、平台评分和审计字段
- `row_version`：撤下与更新的并发控制

Listing 撤下后不再出现在公共搜索，但不删除历史领取权益。

### 6.4 `asset_entitlements`

记录一个 Workspace 对公开资产版本的使用权：`beneficiary_workspace_id`、`listing_id`、`source_version_id`、`grant_type=FREE_CLAIM`、领取人和领取时间。

唯一约束为 `(beneficiary_workspace_id, listing_id)`，保证重复领取幂等。

### 6.5 `asset_publish_requests`

企业发布申请：企业 `workspace_id`、资产和版本、申请人、审批人、状态、申请说明、驳回原因、审批时间和乐观锁版本。

状态为 `PENDING`、`APPROVED`、`REJECTED`、`CANCELLED`。同一资产版本最多存在一个有效的 `PENDING` 申请。

### 6.6 `asset_applications`

记录资产应用到项目的动作：`workspace_id`、`asset_id`、`asset_version_id`、`project_id`、目标类型和目标 ID、变更摘要、撤销所需的 `previous_state`、`applied_by`、`idempotency_key` 和时间。

唯一约束为 `(workspace_id, idempotency_key)`。

### 6.7 收藏

`asset_favorites` 同时保存 `user_id`、`workspace_id` 和 `listing_id`，唯一约束为三者组合。收藏是成员个人偏好，不自动共享给企业其他成员，但切换组织上下文后不会混入其他 Workspace 收藏。

## 7. 核心业务流程

### 7.1 个人发布

1. 校验资产属于当前个人 Workspace，操作者是所有者且资产状态有效。
2. 校验必填公开信息、预览、资产版本和授权声明。
3. 从指定版本生成不可变公共快照。
4. 创建或更新 `LISTED` Listing。
5. 写发布审计记录。

### 7.2 企业发布审批

1. 企业成员以 `asset.publish.request` 权限提交资产版本。
2. 创建 `PENDING` 申请；资产继续在企业内正常使用。
3. 企业管理员以 `asset.publish.approve` 权限查看并审批。
4. 通过时在同一事务中将申请设为 `APPROVED` 并创建或更新 Listing。
5. 驳回时保存明确原因；成员修改资产或公开信息后可重新提交。

审批、撤下和版本更新使用乐观锁。并发操作冲突返回 `409`，页面刷新最新状态后重试。

### 7.3 免费领取

1. 校验 Listing 仍为 `LISTED`，当前 Workspace 有 `asset.use` 权限。
2. 查找 `(workspace_id, listing_id)` 权益；存在时直接返回已有结果。
3. 在一个事务内创建权益，并在买方 Workspace 创建 `MARKET_CLAIMED` 资产记录。
4. 买方记录引用已领取的源版本和公开快照，不获得读取卖方私有资产的权限。
5. 返回买方 `workspace_asset_id`，供立即应用或资产库使用。

Listing 撤下后，已领取权益和买方资产继续有效；未领取用户不能新增领取。

### 7.4 应用到项目

所有类型先校验资产与项目属于同一 Workspace、用户拥有 `asset.use` 权限、资产版本有效且类型兼容，然后执行：

| 类型 | 应用动作 |
|---|---|
| 风格模型 | 将推荐模型和参数合并到项目 `style_config`，保存旧配置以支持撤销 |
| 角色 | 加入项目角色资产引用；画布可基于该引用创建角色节点 |
| 场景 | 加入项目场景资产引用；可绑定分镜或场景节点 |
| 提示词 | 复制到项目 Prompt 库；可插入指定文本或生成节点 |

应用成功后写 `asset_applications`，返回变更摘要和一次性 `undo_token`。撤销接口验证同一 Workspace、操作者权限和应用记录后恢复 `previous_state`；若目标已发生冲突变更，则返回 `409` 并要求用户确认。

## 8. API 设计

所有路径沿用 `/api/v1/asset` 前缀。Workspace 从服务端认证上下文注入。

### 8.1 公共市场

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/market/listings` | 搜索、分类、标签、排序和分页 |
| GET | `/market/listings/{listingId}` | 公开详情和当前 Workspace 领取状态 |
| POST | `/market/listings/{listingId}/claim` | 幂等免费领取 |
| PUT | `/market/listings/{listingId}/favorite` | 收藏 |
| DELETE | `/market/listings/{listingId}/favorite` | 取消收藏 |

### 8.2 Workspace 资产库

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/library` | 当前 Workspace 资产列表 |
| POST | `/library` | 创建或导入资产 |
| GET | `/library/{assetId}` | 当前 Workspace 资产详情 |
| PUT | `/library/{assetId}` | 编辑基础信息 |
| POST | `/library/{assetId}/versions` | 创建新版本 |
| POST | `/library/{assetId}/archive` | 归档 |
| POST | `/library/{assetId}/publish` | 个人资产直接发布 |
| POST | `/library/{assetId}/unlist` | 撤下 Listing |
| POST | `/library/{assetId}/applications` | 应用到项目 |
| POST | `/applications/{applicationId}/undo` | 撤销应用 |

应用请求包含 `project_id`、可选 `target_type`、可选 `target_id` 和必填 `idempotency_key`。

### 8.3 企业审批

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/library/{assetId}/publish-requests` | 提交企业发布申请 |
| GET | `/publish-requests` | 按状态分页查询当前企业申请 |
| GET | `/publish-requests/{requestId}` | 申请详情和审计历史 |
| POST | `/publish-requests/{requestId}/approve` | 管理员批准 |
| POST | `/publish-requests/{requestId}/reject` | 管理员驳回，原因必填 |
| POST | `/publish-requests/{requestId}/cancel` | 申请人撤回待审批申请 |

旧的 `/market/models`、`/characters`、`/scenes` 和 `/prompts` 在迁移期映射到新的 Listing 查询，前端切换完成后标记废弃，避免一次性破坏其他调用方。

## 9. 前端组件边界

- `AssetMarketPage`：频道、URL 查询状态和权限上下文。
- `PublicMarketPanel`：查询条件、分页和结果状态。
- `AssetFilterBar`：搜索、分类、标签和排序。
- `AssetCard`：统一展示与快捷动作。
- `AssetDetailDrawer`：预览、参数、授权、领取和应用入口。
- `WorkspaceAssetPanel`：资产库列表、来源和状态筛选。
- `AssetEditorDialog`：创建、编辑和公开信息校验。
- `PublishRequestPanel`：企业申请列表与状态。
- `PublishReviewDrawer`：管理员审批、驳回与审计。
- `ApplyAssetDialog`：选择项目和目标位置，展示变更摘要。

API 状态按组件作用域管理，避免把市场筛选、详情、资产库和审批状态集中到一个超大页面组件中。

## 10. 错误处理与一致性

| 状态 | 场景 | 前端行为 |
|---|---|---|
| `401` | 登录或 Token 过期 | 保留当前 URL，重新登录后恢复 |
| `403` | 缺少使用、管理、发布或审批权限 | 显示缺失权限，不伪造成功 |
| `404` | 私有资源不存在或不属于当前 Workspace | 统一不可用提示 |
| `409` | Listing 已撤下、重复待审、审批或版本冲突 | 刷新实体状态并提示重试 |
| `422` | 资产信息不完整或与项目不兼容 | 标出具体字段或兼容原因 |
| `503` | `3001` 权限服务不可用 | 公共只读浏览可继续；所有依赖权限的写操作明确阻止 |

免费领取的权益与买方资产在同一事务内创建或同时回滚。审批通过时申请状态与 Listing 在同一事务内更新。重复领取返回已有权益；重复应用由 `idempotency_key` 返回原结果。

## 11. 测试与验收

### 11.1 后端测试

- 使用两个个人 Workspace 和两个企业 Workspace 覆盖所有私有读取与写入，交叉访问全部失败。
- 篡改请求中的 `workspace_id` 不能读取或修改其他空间数据。
- 企业普通成员可浏览企业资产，但不能执行未授权的编辑、归档或审批。
- 只有具备 `asset.publish.approve` 权限的企业成员可以批准或驳回。
- 个人发布、企业申请、批准、驳回、撤回和撤下状态转换正确。
- 重复领取只生成一条权益和一条买方资产。
- 撤下 Listing 后旧权益可用，新领取失败。
- 领取和审批事务在异常时完整回滚。
- 四类资产应用到同 Workspace 项目成功，跨 Workspace 项目返回 `404`。
- 风格应用可撤销；并发修改产生冲突时不覆盖新配置。
- `3001` 不可用时写操作失败且不产生本地成功数据。

### 11.2 前端测试

- 分类、筛选、排序、URL 查询状态和分页正确。
- 骨架屏、空态、错误重试和无权限状态正确。
- 音色和 BGM 显示“即将开放”，不触发领取或应用请求。
- 个人与企业频道名称、按钮和审批入口按权限变化。
- 重复点击领取或应用不会生成重复请求结果。
- 详情、领取、资产入库和立即应用后的页面状态一致。

### 11.3 E2E 主链路

1. 个人用户创建资产 → 直接公开 → 另一 Workspace 搜索 → 免费领取 → 入库 → 应用到项目 → 撤销。
2. 企业成员创建资产 → 提交公开 → 管理员审批 → 公共可见 → 另一 Workspace 领取。
3. 企业成员越权审批失败；修改资源 ID 或 Workspace 参数不能越权。
4. Listing 撤下后公共列表消失，已领取 Workspace 仍可应用资产。

## 12. 数据迁移与兼容

1. 以本设计的 `workspace_assets`、`asset_versions`、`market_listings`、`asset_entitlements`、`asset_publish_requests` 和 `asset_applications` 为统一目标模型。
2. 对现有 `market_assets`、`asset_market_items` 等重复定义做一次性迁移映射；迁移完成后只保留一套运行时表。
3. 四条 Mock 风格模型转为平台种子 Workspace 的真实资产、版本和 Listing。
4. 为角色、场景和提示词补充可检索的种子数据，保证四个分类具备可验证内容。
5. 旧列表接口临时适配新查询；新前端完成后再移除旧接口。
6. 迁移脚本必须可重复执行，并通过记录数、唯一约束和公开状态核对。

## 13. 交付顺序

1. Workspace 权限上下文与 `3001` 权限映射。
2. 统一领域表、实体、Mapper、Repository 租户约束和迁移脚本。
3. 公共 Listing 查询与资产详情。
4. Workspace 资产库和四类资产数据结构。
5. 个人发布、企业申请和管理员审批。
6. 免费领取、权益和隔离资产副本。
7. 四类资产项目应用、应用记录与撤销。
8. 前端完整页面、状态处理和权限交互。
9. 租户安全测试、业务 E2E、文档和旧接口清理。

## 14. 成功标准

- 市场不再依赖硬编码数据或 Mock 成功响应。
- 公共资产是否可见完全由所有者发布状态决定。
- 企业资产未经管理员批准不会进入公共市场。
- 任意用户不能通过修改参数访问其他个人或企业的私有资产、权益或应用记录。
- 免费资产可从公共市场领取到当前 Workspace，并可真实应用到同 Workspace 项目。
- 风格模型、角色、场景和提示词四类主流程均通过自动化测试。
- 用户在 `8080` 内完成全部操作，权限由 `8080` 映射 `3001` 后端校验结果。
