# 分镜专业编辑器重构设计

日期：2026-06-30  
状态：设计已确认，待规格审阅  
实施性质：破坏性重构，无对外兼容负担

## 1. 背景

当前仓库存在两套割裂的分镜实现：

1. 旧版 `Storyboard.vue` 基本只读，表格、卡片、时间轴、节奏分析、升档和批量生图等按钮多数为占位提示。
2. 新版内容项目工作台中的 `StoryboardPanel.vue` 仅支持 A 档生成、查看和锁定。
3. 后端 `cp_storyboard_*` 已包含部分 A/B/C 字段与升档方法，但缺少镜头编辑、独立版本、差异比较、连续性检查、XLSX 往返、专业导出和可靠的资源归属校验。
4. 现有升档直接修改同一个 Master 的档位和镜头字段，无法保留 A/B/C 各档版本，也无法稳定回退。
5. 旧画布分镜表与内容项目分镜表采用不同模型，继续补丁式开发会扩大双轨成本。

项目尚未对外使用，因此本次不建设兼容层，不保留旧接口和旧页面，直接以新领域模型替换现有分镜实现。

## 2. 参考工作簿结论

参考文件：`第一章分镜头脚本_13维细化版_人物三视图优化终版.xlsx`。

解析结果：

- 7 张工作表；
- 6 个场景；
- 45 个镜头；
- 总时长 119.5 秒；
- 主分镜表包含 13 个镜头维度。

13 个镜头维度为：

1. 镜号；
2. 时长；
3. 景别；
4. 画面描述；
5. 光影氛围；
6. 角色动作；
7. 情绪；
8. 对白；
9. 场景标签；
10. 音效；
11. 参考；
12. 分镜提示词；
13. 视频动作提示词。

其余 6 张工作表承载：

- 情绪强度总览；
- 提示词模板；
- 类型修订规则；
- 设定一致性问题；
- 人物三视图视觉规范；
- 三视图与镜头绑定。

系统必须完整表达这些数据，但不照搬 Excel 的 13 列横向交互。主编辑器采用核心表格与右侧分组检查器，XLSX 保持 7 表双向往返。

## 3. 目标

1. 建立单一、可版本化、可审核、可生产的分镜领域。
2. 提供独立全屏专业编辑器，支持表格、卡片和时间轴视图。
3. 完整支持 13 维镜头编辑和 6 类专业辅助数据。
4. 支持镜头增删改排、批量修改、撤销重做和可靠自动保存。
5. 支持草稿、审核、锁定、复制和版本差异。
6. A→B→C 升档创建派生版本，永久保留原档版本。
7. 支持参考工作簿 7 张表的导入、校验、差异预览、应用、导出和再次导入。
8. 支持情绪节奏、连续性、视觉规范、提示词和生产准入检查。
9. 支持 PDF、XLSX 和画布不可变生产快照。
10. 消除跨项目资源 ID 越权和锁定版本被修改的风险。

## 4. 非目标

1. 不保留旧 `Storyboard.vue`、旧 `/storyboards` 接口或 `cp_storyboard_*` 兼容适配。
2. 不让画布直接读写分镜领域的可变镜头表。
3. 不让 AI 未经用户确认覆盖人工编辑内容。
4. 不让分镜修改反向覆盖源正文。
5. 本次不拆成独立部署的微服务；只保证领域、接口、表、任务和事件边界可独立拆分。
6. 不迁移无价值的开发期分镜数据；开发环境允许清表并重建。

## 5. 架构边界

### 5.1 部署与领域

分镜领域继续运行在当前 Spring Boot 应用中，但使用独立包、表、服务接口和事件：

```text
com.aicp.module.storyboard
├── controller
├── application
├── domain
├── infrastructure
├── importexport
├── generation
└── review
```

内容项目领域只负责：

- 提供项目、正文版本、项目设定和权限上下文；
- 展示分镜状态和入口；
- 接收分镜状态事件更新流程进度。

分镜领域负责：

- Master、版本、场景、镜头；
- 13 维字段与专业辅助模块；
- AI 生成、局部优化和升档；
- 审核、连续性与生产准入；
- XLSX/PDF 导入导出；
- 画布生产快照。

### 5.2 前端路由

独立编辑器路由：

```text
/content-projects/:projectId/storyboards/:storyboardId
```

内容项目工作台只展示：

- 最近分镜版本；
- 档位、状态、镜头数和问题数；
- 生成分镜；
- 进入专业编辑器；
- 查看最近任务。

旧 `/storyboard/:scriptId` 路由删除。

### 5.3 画布边界

- 锁定 A/B 档可以创建概念验证快照。
- 锁定 C 档且生产准入通过后可以创建批量生产快照。
- 快照包含分镜版本 ID、镜头数据、项目参数版本、人物视觉规范、提示词模板、风险和创建时间。
- 快照创建后不可变；画布修改不自动回写分镜。

## 6. 页面设计

### 6.1 顶部栏

展示：

- 项目、内容单元和分镜名称；
- 当前档位与版本号；
- 保存状态；
- 导入 XLSX；
- 导出；
- 版本历史；
- 升档；
- 提交审核或锁定；
- 创建画布快照。

### 6.2 专业模块导航

顶部模块：

1. 镜头编辑；
2. 情绪节奏；
3. 提示词模板；
4. 类型修订；
5. 设定一致性；
6. 人物视觉规范；
7. 版本与审核。

### 6.3 三栏主工作区

左侧为场景导航：

- 搜索镜号或场景；
- 显示场景镜头数、总时长和情绪强度；
- 支持场景增删、复制、重排和折叠；
- 显示场景问题数。

中间为镜头工作区：

- 编导视图；
- 生产视图；
- 提示词视图；
- 卡片视图；
- 时间轴视图；
- 字段预设；
- 镜头增删、复制、拆分、合并、排序；
- 多选和批量编辑。

右侧为镜头检查器：

- 内容：画面描述、对白、场景标签；
- 导演：景别、光影、角色动作、情绪、参考；
- 声音：音效、对白和配音信息；
- 提示词：图片和视频动作提示词；
- 连续性：人物视觉锁、场景、道具、服装和待处理问题。

### 6.4 保存与离开保护

- 单字段修改进入 800ms 防抖的串行保存队列。
- 写请求携带版本 `revision` 和客户端操作 ID。
- 页面显示等待保存、保存中、已保存、保存失败和版本冲突。
- 保存失败或存在未提交操作时阻止离开。
- 冲突时保留本地草稿，展示服务器最新值和字段差异。
- 撤销/重做只作用于当前草稿版本；刷新后可从操作日志恢复最近未提交状态。

## 7. 领域模型

### 7.1 `storyboards`

分镜资产根对象：

- `id` / `uuid`；
- `project_id`；
- `content_unit_id`；
- `source_content_version_id`；
- `title`；
- `current_draft_version_id`；
- `current_locked_version_id`；
- `production_status`；
- `created_by`；
- 创建、更新时间。

一个内容单元可以有多个分镜 Master，但同一来源版本和用途的默认 Master 必须唯一。

### 7.2 `storyboard_versions`

- `id` / `uuid`；
- `storyboard_id`；
- `parent_version_id`；
- `source_content_version_id`；
- `tier`：A、B、C；
- `version_no`：档位内递增；
- `status`：draft、reviewing、locked、superseded；
- `revision`；
- `schema_version`；
- `total_scenes`；
- `total_shots`；
- `total_duration_ms`；
- `created_from`：manual、ai、import、fork、upgrade；
- `locked_by` / `locked_at`；
- 创建、更新时间。

### 7.3 `storyboard_scenes`

- `id`；
- `version_id`；
- `scene_key`，版本派生时保持稳定；
- `scene_no`；
- `title`；
- `dramatic_goal`；
- `beat_description`；
- `location_ref_id`；
- `duration_ms`；
- `emotion_label` / `emotion_intensity`；
- `sort_order`。

### 7.4 `storyboard_shots`

- `id` / `uuid`；
- `version_id`；
- `scene_id`；
- `shot_key`，版本派生和镜号重排时保持稳定；
- `shot_code`，例如 `S01-C04`；
- `duration_ms`；
- `shot_size`；
- `visual_description`；
- `lighting_atmosphere`；
- `character_action`；
- `emotion_description`；
- `dialogue_text`；
- `scene_tags_json`；
- `sound_effect`；
- `reference_text`；
- `image_prompt`；
- `video_motion_prompt`；
- `status`：draft、confirmed、needs_review；
- `sort_order`；
- 创建、更新时间。

13 维中的可筛选枚举使用规范字段；标签和复杂引用使用结构化关联或受控 JSON。长提示词单独按需加载，列表接口返回摘要。

### 7.5 专业辅助表

- `storyboard_emotion_segments`：情绪类型、镜号范围、强度、表达手段；
- `storyboard_prompt_templates`：模板编号、情绪名、镜头范围、图片提示词、视频提示词；
- `storyboard_creative_rules`：类型修订和设定一致性规则；
- `storyboard_character_visuals`：人物核心识别、日常造型、任务造型、表演锚点、提示词锁；
- `storyboard_shot_visual_bindings`：镜头与人物视觉规范绑定、防漂移要求；
- `storyboard_review_issues`：检查类型、级别、镜头、证据、建议、状态和处理记录；
- `storyboard_jobs`：生成、升档、检查、导入、导出和快照任务；
- `storyboard_audit_logs`：关键操作前后值与操作者。

所有专业辅助表必须包含 `version_id`，保证版本快照完整可复现。

## 8. 版本生命周期

### 8.1 草稿与快照

- 草稿自动保存，但自动保存不增加版本号。
- 用户执行“创建版本”时复制当前草稿形成新的草稿版本。
- 用户提交审核后状态变为 `reviewing`。
- 审核问题关闭且准入规则满足后才能锁定。
- 锁定版本不可编辑、不可删除，只能读取、导出、派生或标记为被新版本替代。
- 修改锁定版本必须“复制为新草稿”。

### 8.2 A/B/C 派生

- A 档锁定后可派生 B 档草稿。
- B 档锁定后可派生 C 档草稿。
- 派生版本复制场景和镜头稳定键，并记录 `parent_version_id`。
- AI 仅补充目标档位字段，不能覆盖人工锁定字段。
- 原 A/B 档永久保留，可比较、回退和重新派生。

### 8.3 档位准入

A 档：

- 场景目标和节拍；
- 基础镜头和时长；
- 13 维中的编导必需字段；
- 可用于概念验证。

B 档：

- 导演意图；
- 动作动机；
- 关系调度；
- 信息差；
- 声画关系；
- 剪辑点；
- 可用于导演确认和概念画布。

C 档：

- 图片生成表；
- 视频生成表；
- 配音字幕表；
- 失败策略；
- 资产和人物视觉绑定；
- 生产检查通过后可批量生产。

## 9. XLSX 双向往返

### 9.1 导入流程

1. 上传文件并创建导入任务；
2. 识别模板版本和工作表；
3. 映射列名、枚举和镜号；
4. 校验 7 张表之间的镜号、人物、模板和规则引用；
5. 标准化时长、景别、情绪强度和标签；
6. 生成新增、修改、删除和冲突差异预览；
7. 用户确认；
8. 在单个事务中创建新的草稿版本；
9. 保存源文件哈希、模板版本和导入审计。

预检失败时不得写入业务表。错误报告必须包含：

- 工作表；
- 单元格；
- 原值；
- 错误代码；
- 中文说明；
- 修复建议。

### 9.2 导出流程

支持：

- 7 表完整 XLSX；
- 导演审阅 PDF；
- C 档生产交付 XLSX。

完整 XLSX 必须：

- 保持 7 张表的顺序和字段语义；
- 匹配参考文件的表头、冻结窗格、筛选、列宽和基础样式；
- 写入隐藏的 `schema_version`、`storyboard_id`、`version_id`、`scene_key` 和 `shot_key`；
- 再次导入时按稳定 ID 精确匹配；
- 对外展示表中不暴露内部数据库主键。

### 9.3 往返验收

参考文件首次导入后必须得到：

- 6 场；
- 45 镜；
- 119.5 秒；
- 13 个主字段；
- 6 类辅助数据。

导出后再次导入，稳定键、字段语义、场景归属、镜头顺序、时长和引用关系必须一致。样式只要求达到定义的模板基线，不要求逐像素复刻 Excel 渲染差异。

## 10. 功能闭环

### 10.1 镜头编辑

- 新增、删除、复制；
- 拆分、合并；
- 场景内和跨场景拖拽；
- 自动重排展示镜号；
- 多选和批量字段修改；
- 字段级撤销重做；
- 镜头确认和退回待检查。

### 10.2 AI 能力

- 从指定正文版本生成 A 档；
- 选中镜头局部重写；
- 优化图片或视频提示词；
- 生成情绪区间和节拍建议；
- 连续性候选问题；
- 派生 B/C 档。

AI 结果必须采用候选补丁或新派生草稿。用户确认前不覆盖人工字段，不修改锁定版本。

### 10.3 专业检查

- 情绪强度和节奏断层；
- 景别连续重复；
- 镜头时长异常；
- 对白与时长不匹配；
- 人物脸型、发型、服装、鞋和道具漂移；
- 场景、时间、光线和道具连续性；
- 图片和视频提示词缺失；
- C 档生产字段缺失；
- 资产未绑定或失败策略缺失。

检查结果形成可关闭、忽略或指派的审核问题，所有处理保留审计。

## 11. API 草案

### 11.1 Master 与版本

```text
POST   /content-projects/{projectId}/storyboards
GET    /content-projects/{projectId}/storyboards
GET    /content-projects/{projectId}/storyboards/{storyboardId}
GET    /content-projects/{projectId}/storyboards/{storyboardId}/versions
POST   /content-projects/{projectId}/storyboards/{storyboardId}/versions
GET    /content-projects/{projectId}/storyboards/{storyboardId}/versions/{versionId}
GET    /content-projects/{projectId}/storyboards/{storyboardId}/versions/{versionId}/diff
POST   /content-projects/{projectId}/storyboards/{storyboardId}/versions/{versionId}/submit-review
POST   /content-projects/{projectId}/storyboards/{storyboardId}/versions/{versionId}/lock
POST   /content-projects/{projectId}/storyboards/{storyboardId}/versions/{versionId}/fork
POST   /content-projects/{projectId}/storyboards/{storyboardId}/versions/{versionId}/upgrade
```

### 11.2 场景与镜头

```text
GET    /.../versions/{versionId}/scenes
POST   /.../versions/{versionId}/scenes
PATCH  /.../versions/{versionId}/scenes/{sceneId}
DELETE /.../versions/{versionId}/scenes/{sceneId}
POST   /.../versions/{versionId}/scenes/reorder

GET    /.../versions/{versionId}/shots
POST   /.../versions/{versionId}/shots
PATCH  /.../versions/{versionId}/shots/{shotId}
DELETE /.../versions/{versionId}/shots/{shotId}
POST   /.../versions/{versionId}/shots/batch
POST   /.../versions/{versionId}/shots/reorder
POST   /.../versions/{versionId}/shots/{shotId}/split
POST   /.../versions/{versionId}/shots/merge
```

列表接口支持按场景、状态、镜号、角色、标签、问题类型分页筛选。长提示词通过详情或专用字段参数加载。

### 11.3 专业模块、任务和文件

```text
GET/PUT /.../versions/{versionId}/emotion-segments
GET/PUT /.../versions/{versionId}/prompt-templates
GET/PUT /.../versions/{versionId}/creative-rules
GET/PUT /.../versions/{versionId}/character-visuals
GET/PUT /.../versions/{versionId}/visual-bindings
GET/PUT /.../versions/{versionId}/review-issues

POST /.../versions/{versionId}/jobs/generate
POST /.../versions/{versionId}/jobs/check
POST /.../versions/{versionId}/jobs/import
POST /.../versions/{versionId}/jobs/export
POST /.../versions/{versionId}/jobs/canvas-snapshot
GET  /storyboard-jobs/{jobId}
GET  /storyboard-jobs/{jobId}/events
```

## 12. 并发、幂等与任务

### 12.1 并发

- 每个写请求携带 `revision`。
- 修订号不匹配时返回 `409 STORYBOARD_REVISION_CONFLICT`。
- 响应包含服务器最新修订、冲突字段和最新值。
- 前端保留本地修改，允许逐字段采用本地值、服务器值或手动合并。

### 12.2 幂等

以下操作必须携带幂等键：

- AI 生成；
- 派生升档；
- 导入应用；
- 锁定版本；
- 导出；
- 创建画布快照。

同一幂等键重复提交返回首次结果，不重复创建版本、文件或任务。

### 12.3 异步任务

统一 `storyboard_jobs` 状态：

```text
queued → running → succeeded
                 ↘ failed
                 ↘ partial
                 ↘ cancelled
```

任务通过 SSE 推送进度，前端在 SSE 不可用时降级轮询。任务必须记录输入版本、模型、Prompt 版本、文件哈希、费用估算、输出和错误。

## 13. 错误处理

关键错误码：

- `STORYBOARD_NOT_FOUND`；
- `STORYBOARD_VERSION_LOCKED`；
- `STORYBOARD_REVISION_CONFLICT`；
- `SOURCE_CONTENT_VERSION_STALE`；
- `INVALID_TIER_TRANSITION`；
- `REVIEW_ISSUES_UNRESOLVED`；
- `PRODUCTION_GATE_FAILED`；
- `XLSX_TEMPLATE_UNSUPPORTED`；
- `XLSX_VALIDATION_FAILED`；
- `JOB_ALREADY_RUNNING`；
- `JOB_PARTIAL_FAILURE`；
- `CANVAS_SNAPSHOT_ALREADY_EXISTS`。

错误响应必须包含稳定错误码、中文消息、可恢复动作和关联资源。批量操作按原子性分两类：

- 重排、锁定、导入应用和版本派生必须全成全败；
- 批量 AI 处理和文件生成允许部分成功，但必须逐镜头报告结果，且不得将部分结果自动标记为完整通过。

## 14. 权限与安全

- 所有 Master、Version、Scene、Shot 和专业模块查询同时限定 `project_id` 和上级资源 ID。
- 不接受仅凭子资源 ID 的读写。
- 查看需要项目 VIEW 权限。
- 编辑、导入和 AI 操作需要 EDIT_CONTENT 权限。
- 锁定、生产准入和画布快照需要 PRODUCE 或项目 Owner 权限。
- 导出文件使用短期签名地址，不返回本地路径。
- 审计日志记录锁定、解锁提议、导入应用、升档、批量编辑和快照创建。

## 15. 测试与验收

### 15.1 单元测试

- 13 维字段和枚举；
- 镜号生成与稳定键；
- 场景和镜头重排；
- 版本状态机；
- 档位准入；
- XLSX 列映射和标准化。

### 15.2 服务集成测试

- 项目权限隔离；
- 乐观锁冲突；
- 幂等重复提交；
- 锁定不可变；
- 派生版本稳定键；
- 导入事务回滚；
- 异步任务状态；
- 画布生产准入。

### 15.3 前端测试

- 自动保存串行队列，服务器最终值等于最后一次用户修改；
- 撤销重做；
- 多视图数据同步；
- 批量编辑和重排；
- 保存失败和冲突恢复；
- 锁定只读；
- SSE 断线降级轮询。

### 15.4 XLSX 黄金样例

使用参考工作簿验证：

- 7 张工作表识别正确；
- 6 场、45 镜和 119.5 秒正确；
- 所有 13 维非空数据进入对应字段；
- 情绪、模板、规则、人物规范和镜头绑定正确；
- 导出再导入后业务语义与稳定引用一致；
- 导入错误可定位到单元格。

### 15.5 E2E

```text
锁定正文版本
→ 生成 A 档草稿
→ 人工编辑与批量调整
→ 情绪/连续性/提示词检查
→ 处理审核问题
→ 锁定 A 档
→ 派生并锁定 B 档
→ 派生 C 档并通过生产准入
→ 导出 7 表 XLSX / PDF
→ 创建画布生产快照
```

### 15.6 性能与视觉基线

- 单版本支持 2,000 镜头分页编辑；
- 200 镜头批量保存保持单事务一致性；
- 长提示词按需加载；
- 1366×768 可完成主编辑流程；
- XLSX/PDF 不截断关键字段；
- 锁定、冲突、失败、待检查状态可明确识别。

## 16. 删除与替换范围

实施时删除或替换：

- `aicp-frontend/src/views/Storyboard.vue`；
- `aicp-frontend/src/views/content-project/components/StoryboardPanel.vue` 的编辑职责；
- `aicp-frontend/src/api/storyboard.js` 旧接口；
- `ContentStoryboardController`；
- 现有 `StoryboardService`；
- `cp_storyboard_masters`；
- `cp_storyboard_scenes`；
- `cp_storyboard_shots`；
- 画布侧旧分镜表与新领域重叠的写入入口。

内容项目工作台保留轻量入口组件，但只消费新分镜领域的摘要 API。

## 17. 分期建议

虽然目标是完整专业版，实施仍按可独立验收的纵向切片推进：

1. 新领域基础：Master、Version、Scene、Shot、权限、状态机和独立路由；
2. 13 维专业编辑器：自动保存、镜头 CRUD、排序、批量编辑和多视图；
3. 版本审核：快照、差异、审核问题、锁定和派生升档；
4. 6 类专业模块：情绪、模板、规则、一致性和人物视觉；
5. XLSX/PDF：预检、差异、事务导入、7 表导出和黄金样例；
6. AI 与检查：生成、局部优化、连续性、生产准入；
7. 画布快照、性能、安全和完整 E2E。

每个切片必须同时交付前端、后端、数据表和测试，不保留只有按钮没有业务闭环的占位功能。
