# 资产生成历史工作台完善设计

日期：2026-07-04  
状态：待用户审阅  
范围：用户端“资产生成历史”、生成任务、Workspace 资产库、画布复用与资产市场发布衔接

## 1. 结论

将现有“资产生成历史”和“任务监控”合并为统一的资产工作台。工作台按“Workspace → 剧本/内容项目 → 资产业务分类”组织数据，在同一视图呈现排队、生成中、失败、取消和成功资产，并提供整理、复用、发布和回收能力。

本方案不继续扩建 `platform_assets`。项目已经存在 `workspace_assets + asset_versions` 资产市场模型，因此生成资产将迁入该模型，形成唯一资产本体：

- `generation_tasks` 记录生成过程、成本、状态和重试链；
- `workspace_assets` 保存资产归属和可变元数据；
- `asset_versions` 保存不可变文件版本与生成快照；
- `platform_assets` 仅在兼容期保留，迁移完成后退役。

## 2. 当前缺口

现有实现不能形成真实生产闭环：

1. `AssetHistory.vue` 一次性加载全部资产，仅在浏览器内搜索和过滤，没有分页、复合筛选、错误态或批量能力。
2. 收藏仅执行 `asset.favorite = !asset.favorite`，刷新即丢失。
3. `POST /assets/{assetId}/send-to-canvas` 只返回成功文案，没有创建画布节点。
4. 页面只用缩略图模拟预览，视频和音频没有对应播放器。
5. `TaskMonitor.vue` 读取 `gen_tasks`，资产历史读取 `generation_tasks`，形成两套任务口径。
6. `GenerationExecutor` 可能创建没有 `file_url` 的空资产，并在无法确定所有者时回退到用户 `1`。
7. 历史查询只按 `owner_user_id` 隔离，未完整纳入 Workspace、项目权限和团队可见性。
8. `platform_assets` 与 `workspace_assets + asset_versions` 重复表达资产本体，发布市场需要跨模型转换。
9. H2、MySQL、实体类中的生成变体和资产字段存在不一致。
10. Workspace ID 同时出现 `personal_1`、`personal:1`、`ent:` 和 `enterprise:` 等格式，存在隔离失效风险。

## 3. 已确认的产品决策

1. 资产历史包含任务状态，独立“任务监控”入口合并到工作台。
2. 提供“我的资产 / 团队资产”双视图，并严格按当前 Workspace 隔离。
3. 个人资产可直接进入市场发布流程；团队资产复用现有发布申请和审批流程。
4. 删除采用 30 天回收站，可恢复；画布已有引用不被破坏。
5. 主页面采用资产工作台布局：左侧项目树，中部分类与记录，右侧详情抽屉。
6. 资产首先按剧本/内容项目分库，再按角色、场景、道具、分镜、配音、音乐和其他分类。
7. 没有关联项目的内容进入“未归档资产”，支持批量归档。
8. `media_type` 表示 image/video/audio/data/other；`asset_type` 表示业务分类，两者不得混用。

## 4. 目标与非目标

### 4.1 目标

- 跑通“生成任务 → 文件校验 → 资产入库 → 节点回写 → 项目分类 → 复用/发布”的可靠闭环。
- 让用户可以按剧本项目定位资产，避免不同剧本资产混在一起。
- 统一任务与资产查询口径，消除独立任务监控页面。
- 支持个人和团队资产，并保证 Workspace 隔离。
- 提供真实画布放置、持久收藏、批量整理、签名下载、市场发布和回收站。
- 让迁移、灰度、监控和回滚具备明确门槛。

### 4.2 非目标

本期不建设：

- 复杂多级版本树；
- 版权合同和授权结算；
- 自动内容审核；
- 文件指纹自动去重；
- 跨项目依赖图；
- 绕过 30 天策略的普通永久删除入口。

## 5. 信息架构与页面规格

主路由为 `/asset-history`。页面筛选状态写入 URL，使刷新、分享链接和浏览器前进后退可以恢复状态。

### 5.1 页面区域

| 区域 | 展示内容 | 行为 |
|---|---|---|
| 顶部范围栏 | 我的资产、团队资产、全局搜索、视图切换、批量模式 | 切换 Workspace 或 scope 时取消旧请求、清空选择并重新加载 |
| 项目树 | 项目名、封面、题材、资产数、进行中数、失败数 | 提供未归档、收藏、已发布、回收站特殊集合 |
| 资产分类 | 全部、角色、场景、道具、分镜、配音、音乐、其他 | 使用 `asset_type`，不使用媒体类型替代 |
| 筛选栏 | 关键词、状态、媒体、模型、创建人、时间、标签、排序 | 300ms 防抖，服务端查询，一键清空 |
| 记录区 | 网格/列表、总数、分页、每页 24/48/96、选择数 | 请求竞态保护；区分无数据、无匹配、无权限和加载失败 |
| 详情抽屉 | 概览、生成参数、来源与引用、活动记录 | URL 写入记录标识，关闭后保留列表位置 |

URL 参数为：`scope`、`project_uuid`、`collection`、`asset_type`、`status`、`media_type`、`model_id`、`created_by`、`from`、`to`、`tags`、`keyword`、`sort`、`page`、`page_size`、`view`、`record_kind`、`record_uuid`。

### 5.2 记录卡片

| 状态 | 必须展示 | 可用操作 |
|---|---|---|
| pending/running | 任务名、分类、模型、创建人、排队位置或进度、已耗时、预计积分 | 取消、查看参数 |
| failed/canceled | 失败阶段、错误码、用户可读原因、失败时间 | 重试、复制参数、移入回收站；取消记录默认折叠 |
| succeeded asset | 媒体预览、名称、分类、媒体类型、尺寸/时长、模型、创建人、时间、收藏和发布状态 | 预览、下载、发送画布、再次生成、发布、移动、删除 |

所有操作由后端 `allowed_actions` 决定，前端不得根据角色名称自行推断权限。

### 5.3 详情字段

- 概览：asset/task UUID、名称、项目、业务分类、媒体类型、格式、大小、宽高、时长、创建人、创建/更新时间、标签、收藏、发布状态。
- 生成参数：provider、model_id、prompt、negative_prompt、seed、分辨率、帧率、时长、参考资产和折叠后的原始参数。
- 来源与引用：来源画布、节点、分镜、原任务、重试来源和画布引用列表。
- 活动记录：创建、重命名、移动、收藏、放置画布、发布、删除和恢复。

### 5.4 核心操作

| 操作 | 输入 | 成功结果 | 禁止条件 |
|---|---|---|---|
| 发送到画布 | 目标项目、目标画布、放置方式 | 返回节点 UUID 和跳转地址 | 回收站、文件缺失、无目标权限 |
| 再次生成 | 原参数，可修改模型/Prompt | 新任务出现在同项目同分类顶部 | 原参数不可读、模型下线且未重新选择 |
| 移动/归档 | 目标项目、目标分类 | 更新归属并刷新项目计数 | 无目标项目编辑权限 |
| 发布市场 | 标题、简介、标签、许可、预览 | 个人创建 listing；团队创建审批单 | 文件缺失、已删除、已有待审批 |
| 删除 | 确认并展示引用数 | 进入回收站并返回到期时间 | 无权限、发布处理中 |

同一 Workspace 可批量移动、改分类、加/删标签、删除、恢复和下载，单批最多 100 项。批量操作逐项鉴权和提交，返回成功与失败明细。发布市场不做批量操作。

## 6. 前端组件

| 文件/组件 | 职责 |
|---|---|
| `views/generation/AssetHistory.vue` | 页面壳和子组件编排，不直接拼业务查询 |
| `views/asset-history/useAssetWorkbench.js` | URL 状态、请求序列、分页、选中项和 Workspace reset |
| `views/asset-history/assetHistoryState.js` | 枚举、query/state 转换和卡片纯投影 |
| `AssetProjectTree.vue` | 项目和特殊集合计数 |
| `AssetCategoryTabs.vue` | 业务分类切换 |
| `AssetFilterBar.vue` | 八类筛选和排序 |
| `AssetRecordGrid.vue` | 骨架、空态、错误态、分页和列表布局 |
| `AssetRecordCard.vue` | 任务/资产状态卡片和快捷操作 |
| `AssetMediaPreview.vue` | 图片、视频、音频预览和文件缺失态 |
| `AssetDetailDrawer.vue` | 四个详情页签 |
| `AssetBatchBar.vue` | 批量命令和部分失败结果 |
| `CanvasTargetDialog.vue` | 目标画布、放置方式和幂等命令 |
| `AssetPublishDialog.vue` | 个人发布和团队申请 |
| `AssetTrashPanel.vue` | 删除时间、到期时间、引用数和恢复 |
| `api/assetHistory.js` | 集中声明工作台 API |

`TaskMonitor.vue` 的菜单入口删除，旧 `/task-monitor` 重定向到 `/asset-history?status=pending,running,failed`。兼容期结束后删除页面文件。

## 7. 后端组件

| 文件/服务 | 职责和事务边界 |
|---|---|
| `asset/controller/AssetWorkbenchController.java` | 三个查询端点、DTO 校验和 WorkspaceContext |
| `asset/controller/AssetCommandController.java` | 编辑、收藏、移动、批量、删除/恢复、下载和发布 |
| `asset/dto/AssetWorkbenchRequests.java` | 请求 DTO 与 Bean Validation |
| `asset/dto/AssetWorkbenchViews.java` | snake_case 视图 DTO、RecordSummary/Detail/Facets |
| `AssetHistoryQueryService` | 任务/资产统一投影、分页、facets 和 allowed_actions；只读事务 |
| `AssetCommandService` | 编辑、移动、标签、收藏、批量和活动日志 |
| `AssetLifecycleService` | ACTIVE/ARCHIVED/TRASHED 状态机和恢复 |
| `CanvasPlacementService` | 目标鉴权、媒体到节点映射、幂等创建和引用记录 |
| `AssetPublicationAdapter` | 复用现有发布和审批服务，不复制市场逻辑 |
| `GenerationSettlementService` | 输出校验、资产/版本、节点回写、任务终态和 outbox |
| `AssetMigrationJob` | 每批 500 条、checkpoint、可重入和核对报告 |
| `AssetPurgeScheduler` | 按 `purge_at` 小批清理；先清存储，再清主记录 |
| `AssetMetrics` | API、入库、补偿、迁移、权限和幂等指标 |

Controller 不直接暴露实体 Map。查询和命令分离，查询服务不得夹带修复写入。

## 8. 数据模型

### 8.1 模型收敛

目标模型：

```text
generation_tasks 1 ── 0..n asset_versions n ── 1 workspace_assets
                                           │
                                           ├── workspace_asset_favorites
                                           ├── asset_activity_logs
                                           └── canvas_asset_placements
```

`platform_assets` 迁移到 `workspace_assets + asset_versions`，新工作台不读取旧表。

### 8.2 workspace_assets

保留现有 `id`、`uuid`、`workspace_id`、`workspace_type`、`creator_user_id`、`name`、`description`、`tags`、`access_scope`、`source_type`、`source_listing_id`、`source_version_id`、`current_version_id`、`status`、`row_version` 和审计字段。

新增或改变：

| 字段 | 类型/约束 | 用途 |
|---|---|---|
| `content_project_id` | BIGINT NULL | 剧本/内容项目目录；NULL 表示未归档 |
| `source_canvas_project_id` | BIGINT NULL | 首次产出画布，不作为目录主键 |
| `source_node_id` | BIGINT NULL | 来源节点 |
| `source_task_id` | BIGINT NULL | 来源任务 |
| `asset_type` | VARCHAR(32) NOT NULL | CHECKPOINT/LORA/STYLE_PACK/PROMPT/CHARACTER/SCENE/PROP/STORYBOARD/VOICE/MUSIC/OTHER |
| `media_type` | VARCHAR(16) NOT NULL | image/video/audio/data/other |
| `status` | VARCHAR(16) NOT NULL | ACTIVE/ARCHIVED/TRASHED |
| `deleted_at` | DATETIME NULL | 进入回收站时间 |
| `deleted_by` | BIGINT NULL | 删除人 |
| `purge_at` | DATETIME NULL | 到期清理时间 |
| `purge_blocked_reason` | VARCHAR(64) NULL | 存在画布引用时记录延迟清理原因 |
| `legacy_platform_asset_id` | BIGINT NULL UNIQUE | 兼容期迁移映射 |

将 `workspace_type` 和 `asset_type` 从 MySQL ENUM 改为 VARCHAR，避免分类扩展要求表级 ENUM 变更。`tags` 规范为非空 JSON 数组。

### 8.3 asset_versions

资产版本不可修改，只能新增。

| 字段 | 类型/约束 | 用途 |
|---|---|---|
| `asset_id`、`version_number` | BIGINT/INT NOT NULL | 唯一约束 `(asset_id, version_number)` |
| `source_task_id` | BIGINT NULL | 生成来源 |
| `storage_provider` | VARCHAR(24) | 存储供应方 |
| `storage_bucket` | VARCHAR(128) | 存储桶 |
| `storage_key` | VARCHAR(768) NOT NULL | 持久存储定位 |
| `mime_type` | VARCHAR(128) | 内容类型 |
| `file_size` | BIGINT | 文件大小 |
| `checksum` | VARCHAR(128) | 校验值 |
| `width`、`height`、`duration_ms` | INT NULL | 媒体元数据 |
| `preview_url`、`thumbnail_ref` | VARCHAR | 兼容预览和内部缩略图 |
| `generation_snapshot` | JSON NULL | provider/model/prompt/parameters 快照 |
| `metadata` | JSON NOT NULL | 低频扩展元数据 |

短时签名下载 URL 不入库。旧 `content_ref` 在完成 `storage_key` 回填后标记弃用，但不在同一发布中删除。

### 8.4 generation_tasks

新增：`workspace_id VARCHAR(64) NOT NULL`、`created_by BIGINT NOT NULL`、`content_project_id BIGINT NULL`、`asset_type VARCHAR(32) NOT NULL DEFAULT 'OTHER'`、`retry_of_task_id BIGINT NULL`、`idempotency_key VARCHAR(64) NULL` 和 `request_id VARCHAR(64) NULL`。

现有 `project_id` 在本期继续表示来源画布项目，避免同时改动所有 Mapper。后续单独重命名为 `source_canvas_project_id`。

状态为 `succeeded` 时必须至少存在一个有效 `asset_version`。资产登记失败时任务不得先标记为成功。

数据库沿用现有小写任务状态 `pending/running/succeeded/failed/canceled`；API 也返回小写状态。本文其他位置出现的大写状态只表示概念名称，不引入第二套持久化枚举。

### 8.5 新表

- `workspace_asset_favorites(user_id, workspace_id, asset_id, created_at)`，唯一约束 `(user_id, workspace_id, asset_id)`。现有 `asset_favorites` 继续表示市场 listing 收藏。
- `asset_activity_logs(workspace_id, asset_id, actor_user_id, action, before_data, after_data, request_id, created_at)`，只追加。
- `canvas_asset_placements(workspace_id, asset_id, asset_version_id, canvas_project_id, node_id, placed_by, idempotency_key, released_at, created_at)`，唯一约束 `(workspace_id, idempotency_key)`。`released_at IS NULL` 表示仍有有效画布引用。
- `asset_command_idempotencies(workspace_id, user_id, idempotency_key, command_type, request_hash, response_code, response_body, expires_at)`，唯一约束 `(workspace_id, user_id, idempotency_key)`。

### 8.6 索引

- `workspace_assets(workspace_id, content_project_id, status, updated_at DESC)`
- `workspace_assets(workspace_id, creator_user_id, status, created_at DESC)`
- `workspace_assets(workspace_id, asset_type, status, created_at DESC)`
- `workspace_assets(workspace_id, source_task_id)`
- `generation_tasks(workspace_id, content_project_id, status, created_at DESC)`
- `generation_tasks(workspace_id, created_by, created_at DESC)`
- `generation_tasks(retry_of_task_id)`
- 唯一索引 `generation_tasks(workspace_id, idempotency_key)`
- 唯一索引 `asset_versions(asset_id, version_number)`
- `asset_versions(source_task_id)` 和非唯一 `asset_versions(checksum)`

## 9. API 契约

统一前缀 `/api/v1`，所有接口要求 JWT 和 `X-Workspace-Id`。API 边界使用 UUID，不暴露数据库自增 ID。该模块 DTO 使用 snake_case，采用 DTO 级 Jackson 命名策略，不修改全局策略。

### 9.1 查询

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/assets/workbench/projects` | 项目和特殊集合计数 |
| GET | `/assets/history/records` | 统一任务/资产分页查询 |
| GET | `/assets/history/records/{recordKind}/{recordUuid}` | 任务或资产详情 |

列表查询支持 `scope`、`project_uuid`、`collection`、`record_kind`、`asset_type`、`status`、`media_type`、`model_id`、`created_by`、`created_from/to`、`tags`、`keyword`、`sort`、`page` 和 `page_size`。

- `page` 默认 1，最小 1；`page_size` 为 24/48/96，最大 100。
- `sort` 白名单：`created_at:desc`、`created_at:asc`、`updated_at:desc`、`name:asc`。
- `keyword` 最长 100；日期范围最长 366 天；标签最多 10 个并采用 AND 匹配。
- 响应使用 `PageResult`，并增加与 pagination 同级的 `facets`。

`RecordSummary` 包含记录身份、项目、分类、媒体信息、任务状态、模型、错误摘要、创建人、时间、收藏、发布、删除和 `allowed_actions`。

### 9.2 资产命令

| 方法 | 路径 | 关键输入 |
|---|---|---|
| PATCH | `/assets/{assetUuid}` | name、asset_type、tags；`If-Match` |
| PUT/DELETE | `/assets/{assetUuid}/favorite` | 幂等收藏/取消 |
| POST | `/assets/{assetUuid}/move` | target_project_uuid、target_asset_type；`If-Match` |
| POST | `/assets/batch` | 1–100 个 UUID、operation、payload |
| DELETE | `/assets/{assetUuid}` | `If-Match`，软删除 |
| POST | `/assets/{assetUuid}/restore` | `If-Match` |
| GET | `/assets/{assetUuid}/download-url` | disposition |
| POST | `/assets/{assetUuid}/regenerate` | model、prompt、parameters_patch、幂等键 |
| POST | `/assets/{assetUuid}/publish` | 标题、简介、标签、许可和幂等键 |

修改类请求使用 `If-Match: "{row_version}"`，响应返回 `ETag`。缺失返回 428，版本冲突返回 409/48004。

### 9.3 发送画布

`POST /assets/{assetUuid}/send-to-canvas`

请求字段：`target_project_uuid`、`target_canvas_uuid`、`placement=viewport_center|auto|absolute`、可选 x/y。`absolute` 时 x/y 必填。

创建命令使用请求头 `Idempotency-Key`。同键同载荷重放原响应，同键不同载荷返回 409/48013。成功返回 `placement_id`、`node_uuid`、`redirect_url` 和 `replayed`。

旧接口缺少目标画布时必须返回 422 和迁移提示，不得继续返回假成功。

### 9.4 任务命令

- `POST /generation/tasks/{taskUuid}/cancel` 仅允许 pending/running。
- `POST /generation/tasks/{taskUuid}/retry` 仅允许 failed/canceled；创建新任务并写 `retry_of_task_id`。
- `GET /generation/tasks/{taskUuid}` 必须校验 Workspace 和项目权限。

## 10. 错误与状态

### 10.1 业务错误

| 等级 | 业务码 | HTTP | 处理 |
|---|---|---|---|
| E1 参数 | 40002、48010、48014 | 400/422 | 定位字段，用户修正，不自动重试 |
| E2 冲突 | 48004、48006、48009、48013、46021 | 409 | 返回当前状态/版本 |
| E3 权限 | 40003、48001、48002 | 401/403/404 | 跨 Workspace 始终表现为 404 |
| E4 依赖 | 50002、50003、48015 | 503/504 | 只对幂等操作退避重试 |
| E5 一致性 | 48016、48017 | 409/500 | 不标成功，进入补偿 |
| E6 迁移 | MIG-001~004 | 内部 | 单项隔离并暂停异常批次 |

新增业务码：

- 48008 ASSET_FILE_MISSING
- 48009 ASSET_LIFECYCLE_CONFLICT
- 48010 ASSET_CATEGORY_INVALID
- 48011 ASSET_PURGED
- 48012 ASSET_BATCH_LIMIT
- 48013 ASSET_IDEMPOTENCY_CONFLICT
- 48014 ASSET_CANVAS_TARGET_INVALID
- 48015 ASSET_DOWNLOAD_SIGN_FAILED
- 48016 ASSET_SETTLEMENT_FAILED
- 48017 ASSET_COMPENSATION_EXHAUSTED
- 46020 GENERATION_TASK_NOT_FOUND
- 46021 GENERATION_TASK_STATE_CONFLICT

同步更新 `GlobalExceptionHandler` 的 HTTP 映射。

### 10.2 运维严重度

- SEV0：确认的数据泄漏、跨 Workspace 错归或不可逆误删。立即停写/隔离，5 分钟内响应。
- SEV1：成功无资产、补偿耗尽、迁移差异或高比例 5xx。15 分钟内响应。
- SEV2：持续性能/SLO 下降或依赖错误率升高。30 分钟内响应。
- SEV3：参数错误率和前端体验指标偏高。工作时间处理。

### 10.3 重试与补偿

- 模型生成失败默认只允许用户手动重试，避免重复计费。
- 数据库死锁最多自动重试 3 次。
- 下载签名最多即时重试 1 次。
- 资产登记补偿按 1 分钟、5 分钟、30 分钟、2 小时重试；仍失败进入人工补偿队列。
- 每次补偿复用同一业务幂等键。
- 死信记录 Workspace、任务、资产、阶段、最后错误、重试次数和建议动作。

## 11. 关键数据流

```text
创建 generation_task
  → 写入 Workspace、项目、分类和创建人
  → pending → running
  → 模型/存储生成输出
  → 校验文件存在、校验值和媒体元数据
  → 同一业务事务写 workspace_asset + asset_version + 活动日志
  → 回写画布节点/分镜
  → 发布 outbox 事件
  → 最后将任务标记为 succeeded
```

跨对象存储和数据库无法使用单事务时，通过 outbox/补偿保证最终一致：文件已上传但数据库失败时重试登记或清理；数据库成功但节点回写失败时重试回写，页面显示“结果入库中”而非成功。

## 12. 迁移与兼容

1. 统一 Workspace ID 来源。使用账户中心返回的真实 ID；开发环境个人默认使用 `personal_{userId}`。停止 schema 中的 `personal:`/`ent:` 拼接。
2. 执行加法迁移，为新模型增加 nullable 字段、索引和新表；H2/MySQL 同步。
3. 分批将 `platform_assets` 转为 `workspace_assets + asset_versions`，保留原 UUID 和 `legacy_platform_asset_id`。
4. 通过 `canvas_projects.content_project_id` 映射内容项目，无法映射的资产进入未归档，不静默猜测。
5. 分类优先读取明确字段，否则按 `generation_tasks.sub_type` 映射；无法识别设为 OTHER 并记录异常。
6. `platform_assets.favorite=1` 迁为创建者的 `workspace_asset_favorites`。市场 `asset_favorites` 不改。
7. 在特性开关下双写 canonical 模型和 `platform_assets`，影子核对 UUID、数量、Workspace、项目和文件引用。
8. 新工作台切 canonical 读，旧接口继续读旧表；核对稳定后停止旧写。
9. 旧 `GET /assets/history` 保留至少 2 个版本且不少于 60 天，返回 `Deprecation`、`Sunset` 和 `Link` 头。
10. 旧调用连续 14 天为 0 后，先删除代码和 Mapper，下一发布再备份并删表。

迁移必须输出总资产数、按 Workspace/项目/分类计数、文件可用率、成功任务关联率、收藏数和孤儿记录核对报告。未解释差异必须为 0。

## 13. 测试门禁

| 门禁 | 阈值 | 阻断条件 |
|---|---|---|
| 编译/静态检查 | 前后端 build、lint、契约检查全部通过 | 任何新增错误 |
| 新代码覆盖率 | 行 ≥85%，分支 ≥80%；权限、生命周期、结算分支 100% | 低于任一阈值 |
| API 契约 | 每端点至少成功、校验、权限、冲突各一例 | DTO/OpenAPI/前端不一致 |
| 隔离安全 | 个人↔个人、个人↔企业、企业 A↔B 全操作矩阵通过 | 任何越权可见或可操作 |
| 核心 E2E | 12 条主场景连续 3 轮 100% 通过，flaky=0 | 任一失败或需人工刷新 |
| 迁移 | 10k 脱敏样本和全量预演；重复 3 次结果一致 | 任一未解释差异 |
| 性能 | 50k/Workspace、500k 总量；常用 SQL 无全表扫描 | 超过 SLO 或生成吞吐下降 >5% |
| 回滚 | 6 个能力开关逐项回滚；旧读恢复 ≤5 分钟 | 任一无法无损切回 |

12 条 E2E 场景为：项目分类、未归档、生成成功入库、失败重试链、取消任务、收藏持久化、批量部分失败、画布幂等、个人发布、团队审批、删除恢复和跨 Workspace 隔离。

## 14. 指标与告警

| 指标 | 目标 | 告警 |
|---|---|---|
| `asset_success_without_version_total` | 0 | 5 分钟增量 >0：SEV1 |
| `workspace_isolation_violation_total` | 0 | 任一确认事件：SEV0 |
| `asset_settlement_success_rate` | ≥99.95%/30 天 | 15 分钟 <99.5%：SEV1；1 小时 <99.9%：SEV2 |
| `asset_compensation_oldest_seconds` | <900 秒 | >900 秒或 backlog >10：SEV2；>3600 秒：SEV1 |
| `asset_api_availability` | ≥99.9%/月 | 5xx >1%/5m：SEV2；>5%/5m：SEV1 |
| records latency | p95 ≤500ms，p99 ≤1s | p95 超 500ms/10m：SEV2；p99 超 2s/5m：SEV1 |
| detail latency | p95 ≤300ms | p95 超 500ms/10m：SEV2 |
| command latency | p95 ≤800ms | p95 超 1.2s/10m：SEV2 |
| `idempotency_duplicate_side_effect_total` | 0 | 任一事件：SEV1 |
| `migration_unexplained_diff_total` | 0 | >0：暂停迁移并 SEV1 |
| `frontend_asset_error_rate` | <0.5%/15m | ≥0.5%：SEV3；≥2%：SEV2 |
| `asset_first_usable_ms` | p75 ≤1.5s | p75 >2.5s/30m：SEV3 |

监控标签包含 request_id、workspace_id、user_id、project_uuid、task_uuid、asset_uuid、operation、status、error_code、provider 和 model_id。

## 15. 阶段依赖与灰度

### 15.1 阶段

| 阶段 | 内容 | 退出门槛 | 回滚点 |
|---|---|---|---|
| M0 基线审计 | 表、接口、Workspace ID、数据量和空资产报告 | 差异清单确认，样本可复现 | 无数据写入 |
| M1 Schema/契约 | 加法迁移、索引、Workspace 统一、DTO/ErrorCode | H2/MySQL 迁移通过，旧功能无回归 | 关闭新字段使用 |
| M2 Canonical 双写 | 结算服务、asset/version、补偿、影子核对 | 连续 7 天成功无资产=0，双写差异=0 | 停 canonical 写，保留旧写 |
| M3 API | 查询和命令接口 | 契约、安全和性能门禁通过 | API 切旧读 |
| M4 工作台 | 项目分类、任务卡、详情、收藏、重试和画布 | E2E 通过，小流量无 SEV0/1/2 | 路由切旧页面 |
| M5 管理闭环 | 批量、未归档、下载、回收、日志和发布 | 生命周期、审批和批量验收通过 | 逐能力关闭 |
| M6 退役 | 全量迁移、停旧写、弃用和删旧模型 | 旧调用 14 天为 0，兼容 ≥60 天，备份可恢复 | 删表前切回；删表后从备份恢复 |

依赖关系：M1 依赖 M0；M2 和 M3 可在 M1 后并行；M4 同时依赖 M2 连续稳定 7 天和 M3 门禁通过；M5 依赖 M3，并在 M4 主链路稳定后开启；M6 依赖 M2–M5 指标全部达标。

### 15.2 特性开关

| 开关 | 启用阶段 | 灰度 | 关闭效果 |
|---|---|---|---|
| `asset.canonical.write` | M2 | 内部 → 5% → 25% → 50% → 100% | 停新模型写，旧写继续 |
| `asset.canonical.read` | M3/M4 | 内部 → 10% → 50% → 100% | records 切兼容读 |
| `asset.workbench.ui` | M4 | 员工 → 创作者 5% → 25% → 100% | 路由切旧页面 |
| `asset.lifecycle.manage` | M5 | 内部 → 企业白名单 → 全量 | 隐藏删除/恢复/批量 |
| `asset.market.publish` | M5 | 个人直发 → 团队申请 → 全量 | 隐藏发布入口 |
| `asset.legacy.write` | M2–M6 | M6 最后关闭 | 停止旧表写入 |

低于 25% 的每档至少观察 24 小时，25%–50% 至少观察 48 小时，100% 前至少覆盖一个峰值窗口。任一 SEV0/1 自动停止扩量并回退最近开关。

## 16. 最终验收标准

1. 不同剧本/项目资产零混入；无归属资产只出现在未归档。
2. 跨 Workspace 查询、详情、下载、移动、批量、画布和发布均不可枚举、不可操作。
3. 所有 succeeded 任务都关联至少一个可用 `asset_version`。
4. 收藏刷新后保持，回收站 30 天内可恢复，已有画布节点不因源资产进入回收站而失效。
5. 相同幂等键重复发送画布 10 次只创建一个节点。
6. 个人资产可以发布，团队资产必须走现有审批，重复待审批请求被拒绝。
7. 50k/Workspace、500k 总量下查询达到性能指标且无全表扫描。
8. H2 与 MySQL schema、实体和迁移一致。
9. 旧任务监控路由正确重定向，旧资产接口在兼容期提供弃用头和调用量监控。
10. M0–M6 每阶段都有可验证退出门槛和已演练回滚点。

回收站到期清理必须检查 `canvas_asset_placements`。没有有效引用时删除对象存储文件和主记录；存在有效引用时资产继续从工作台隐藏，但保留最小资产/版本记录和文件，写入 `purge_blocked_reason=ACTIVE_CANVAS_PLACEMENT`，直到引用释放后再执行物理清理。该规则保证“删除资产”不会破坏已经存在的画布节点。
