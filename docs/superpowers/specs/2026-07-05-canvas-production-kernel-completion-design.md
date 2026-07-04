# Canvas 生产内核完善与兼容迁移设计

> 日期：2026-07-05
> 状态：设计评审已确认，待实施计划
> 适用范围：8080 用户端 Canvas、六类节点、导演台、生成任务、Blender 预演、Seedance 适配、质量与交付
> 前置设计：`2026-07-05-canvas-director-seedance-blender-design.md`
> 冲突规则：本文件是对前置设计和当前产品实现的权威修订；两者冲突时以本文件为准

## 1. 结论

采用“新生产内核 + 旧数据只读兼容 + 分阶段灰度切换”的绞杀迁移方案，不原地一次性重写，也不长期维护独立 Canvas V2。

确认的产品边界：

1. Canvas 不做多轨剪辑、转场、最终混音或成片合成。
2. Canvas 的交付终点是镜头采用版本、交付清单、素材包、校验和及外部后期交换文件。
3. 空白画布可进行探索、使用导演台和试生成；正式采用、跨镜头质检及交付前必须绑定内容项目和分镜 revision。
4. 每个镜头工作单元共享一个导演台版本流，多个视频节点可以复用同一冻结 revision。
5. 节点类型严格保持文本、图片、视频、音频、脚本、导演台六类。
6. 分镜、候选、质检、任务、采用和交付清单是领域资源，不增加为画布节点。
7. 本期增加实用增强包：机位、动作、灯光和材质预设，导演包自动检查，以及 EDL/FCPXML 外部剪辑交接。
8. 不增加骨骼关节级编辑、完整材质节点、Blender 工程同步、多人实时同屏编辑或 Canvas 内成片合成。

## 2. 当前产品审计

### 2.1 已具备的基础

- 画布已有节点创建、拖拽、连线、缩放、复制、分组和自动保存基础。
- 已存在文本、图片、视频、音频、脚本和导演台的用户入口。
- 已有浮动编辑器、分镜编辑器、Workspace 资产选择、生成历史和任务入口。
- 后端已有 `canvas_projects`、`canvas_nodes`、`canvas_edges`、`generation_tasks`、`generation_variants`、资产和结算基础。
- 已有统一任务事件中心、Workspace 权限和 3001 钱包/积分方向的设计基础。
- 当前导演台已经具备对象、相机、截图和保存的交互原型，可以作为迁移输入。

### 2.2 当前必须修复的问题

| 优先级 | 问题 | 影响 |
| --- | --- | --- |
| P0 | 画布项目中心在 Workspace 或账户依赖异常时长期停留在骨架屏 | 用户无法判断是空数据、无权限还是系统失败 |
| P0 | 前端使用 `director`，后端创建导演台时写入 `reference`，旧 schema 也未统一包含导演台 | 节点读取、迁移和连接校验不可靠 |
| P0 | 当前仍展示视频剪辑、七轨时间线、音频截取/变速和“合成导出” | 与外部后期边界冲突，形成假承诺 |
| P0 | 分享、通知、批量操作和部分资产能力仍以占位提示暴露 | 用户无法区分真实能力和演示入口 |
| P0 | 部分生成路径硬编码 `seedance-2.0` / `seedream-5.0`，本地模式直接模拟成功 | 用户选择、模型可用性、费用和结果不可审计 |
| P0 | 导演台上传、截图和 AI 导入接口仍含 mock URL 或同步假成功 | 无法进入真实生产链路 |
| P1 | `Canvas.vue` 约 3194 行，同时承担工作区、节点、导演台、资产、生成和时间线 | 修改风险高，模块难以独立测试 |
| P1 | 导演状态主要混存在节点 JSON 中，没有草稿、冻结 revision 和乐观锁事实模型 | 在途任务可能被后续编辑污染 |
| P1 | 当前 `GenerationVariant` 只表达弱候选语义，正式采用和任务快照不足 | 无法形成镜头级追溯和交付 |
| P1 | 现有状态值存在大小写和命名差异，重试、取消、结算语义不统一 | UI、任务中心和业务域可能显示不同状态 |

### 2.3 已核验的外部能力

ByteDance Seed 官方公开说明确认 Seedance 2.0 支持文本、图片、视频和音频混合输入，最多同时输入 9 张图片、3 段视频和 3 段音频，并支持 15 秒高质量多镜头音视频输出：<https://seed.bytedance.com/en/blog/seedance-2-0-official-launch>。

该页面是公开产品能力说明，不等于本项目已获得稳定 API、回调、配额、定价和内容安全合同。因此 R3 启动前必须通过供应商 API Gate，不能依据营销页面直接实现生产适配器。

## 3. 冲突决策

| 冲突 | 旧口径 | 最终决策 |
| --- | --- | --- |
| Canvas 是否负责成片 | 旧页面和部分文档保留合成导出 | 删除成片合成，只保留镜头素材交付 |
| 空白画布是否可生产 | 有的设计禁止无归属画布，有的已支持独立画布 | 采用探索/正式生产双模式 |
| 导演台归属 | 节点、视频节点和 shot 三种口径并存 | 一个 ShotWorkUnit 共享一个 DirectorScene 版本流 |
| 导演台是否是 Gate | 有的流程将导演台视为固定阶段 | 导演台始终可选，不是普通生成 Gate |
| 正式采用事实源 | 节点采用和镜头采用 API 并存 | `ShotAdoption` 是唯一正式采用事实源 |
| 候选是否是节点 | 当前画布倾向继续创建下游结果节点 | 候选是节点内资源，不增加节点类型 |
| 模型路由放在哪里 | 新建完整 `modelrouting` 域可能与现有生成/AiRouter 重复 | Canvas 侧做能力编译和适配；现有 AiRouter/new-api 负责供应商调用 |
| 快照是否保存状态 | 原设计在不可变请求快照中包含可变状态 | 快照不可变；执行状态只保存在 Task/Attempt |
| Blender 坐标 | 原设计笼统写 Web 与 Blender 都使用 Y-up | 领域协议 Y-up；Blender Worker 显式做 Y-up/Z-up 转换 |

## 4. 核心领域模型

```text
CanvasProject
├── mode: EXPLORATION | PRODUCTION
├── ShotWorkUnit[]
│   ├── provisional_shot_id 或 source_shot_id + source_shot_revision
│   ├── CanvasNode[]
│   ├── DirectorScene（可选）
│   │   ├── DirectorDraft（可变）
│   │   └── DirectorRevision[]（不可变）
│   ├── GenerationRequestSnapshot[]（不可变）
│   ├── GenerationTask / TaskAttempt[]
│   ├── GenerationCandidate[]
│   ├── QualityReport[]
│   └── ShotAdoption（正式采用唯一事实源）
└── DeliveryManifest[]
    └── DeliveryManifestItem[]
```

### 4.1 CanvasProject 模式

`EXPLORATION`：

- 允许只填写名称创建空白画布。
- 允许创建六类节点、连接资产、使用导演台和提交试生成。
- 候选明确标记为探索结果。
- 不允许正式采用、跨镜头连续性质检、发布交付清单或企业审批交付。

`PRODUCTION`：

- 必须绑定内容项目、生产单元、内容版本和分镜版本。
- 每个正式 ShotWorkUnit 必须绑定 `source_shot_id + source_shot_revision`。
- 可执行正式采用、质量 Gate、交付和企业审批。

探索画布转正式生产时创建绑定 revision，不覆盖原探索数据。无法映射到正式 shot 的工作单元保留为探索分支。

### 4.2 ShotWorkUnit

ShotWorkUnit 是画布内的镜头生产容器，不是新节点类型。

它拥有：

- 镜头身份、画幅、FPS 和基础时长；
- 六类节点及连接；
- 输入齐套、费用确认、生成完成、质检完成、正式采用五个 Gate；
- 可选 DirectorScene；
- 候选、质量报告和正式采用；
- 交付清单引用。

导演台不是第六个 Gate。未使用导演台的镜头可以完成全部生产 Gate。

### 4.3 节点与端口

节点类型：

```text
text | image | video | audio | script | director
```

首期端口契约：

```text
text | shot | character | scene | prop | image_ref |
motion_ref | camera_ref | audio_ref | director_package |
video_candidate | quality_report
```

`shot`、`video_candidate` 和 `quality_report` 是端口载荷类型，不代表新增节点类型。

端口兼容规则由版本化 registry 统一提供。创建连线时校验，读取旧连线时投影。无法推断的旧连线标记 `NEEDS_CONFIRMATION`，不静默删除或自动执行。

### 4.4 DirectorScene 版本流

一个 ShotWorkUnit 最多拥有一个逻辑 DirectorScene。多个视频节点可以引用不同 DirectorRevision，也可以复用同一 revision。

- `DirectorDraft`：唯一可编辑草稿，使用 `row_version` 乐观锁。
- `DirectorRevision`：冻结产生，不可修改，保存规范化 JSON、哈希、资产版本引用和创建人。
- 预演、生成和质检均创建独立 Task，不改变 revision 的不可变性。
- 从冻结 revision 继续编辑时派生新草稿。

### 4.5 候选与采用

- `GenerationCandidate` 记录请求快照、TaskAttempt、输出资产版本、模型、seed、实际成本和质量报告。
- 节点可以保存当前候选选择，用于局部编辑体验。
- 只有 `ShotAdoption` 能将候选设为正式镜头采用版本。
- 新的正式采用创建新 adoption revision；不删除旧候选和历史采用记录。

## 5. 数据所有权

| 领域 | 唯一事实源 | 不承担 |
| --- | --- | --- |
| 内容/分镜 | 内容项目和专业分镜系统 | 画布节点执行状态 |
| Canvas | 项目模式、ShotWorkUnit、节点、连接和生产 Gate | 媒体文件和供应商任务执行 |
| 导演台 | 草稿、冻结 revision、空间和时间控制 | 模型计费、正式采用 |
| 资产服务 | 媒体、3D 模型、资产版本、许可和文件生命周期 | 镜头站位和导演时间线 |
| 生成域 | 请求快照、任务、Attempt、候选和结算关联 | 改写内容或导演意图 |
| 任务事件中心 | 跨域查询投影、SLA、告警和操作路由 | 直接修改业务状态或余额 |
| 质量域 | 质量报告和问题定位 | 自动正式采用或自动插入导演台 |
| 交付域 | ShotAdoption、DeliveryManifest 和外部交换文件 | Canvas 内成片合成 |

## 6. Canvas 工作台设计

### 6.1 页面结构

```text
顶部：返回项目中心｜探索/正式生产标识｜自动保存｜任务摘要｜交付清单
左侧：六类节点｜项目资产｜生成历史
中部：Canvas 视口｜ShotWorkUnit 折叠生产组｜节点与类型化连线
节点侧：自适应浮动编辑器
辅助层：任务详情、候选对比、质量问题和交付抽屉
```

浮动编辑器统一四个页签：

```text
内容/参数｜参考素材｜候选结果｜任务记录
```

脚本和导演台只显示摘要及“打开专业编辑器”。

### 6.2 删除、隐藏和替换

| 当前入口 | 处理 |
| --- | --- |
| 七轨时间线 | 删除 |
| 视频剪辑、视频合成 | 删除 |
| 音频截取、音频变速 | 删除 |
| 合成导出 | 替换为交付清单/素材包 |
| 分享、通知、批量操作占位 | 接真实服务前隐藏 |
| 本地模拟生成成功 | 删除，开发环境使用明确标识的 Provider Sandbox |
| 视频高清、解析、字幕、音视频分离 | 保留为异步派生任务 |

### 6.3 项目中心降级

- 骨架屏只用于短暂加载，不得成为终态。
- 请求达到前端超时阈值后展示明确错误、重试和诊断 ID。
- 账户中心不可用但本地身份仍有效时，可展示最近项目缓存和当前草稿只读态。
- 降级态禁止新建、迁移、分享和跨 Workspace 操作。
- 恢复后重新校验 Workspace 权限，再开放写操作。

### 6.4 前端模块边界

```text
src/views/canvas/
├── workspace/          # 页面壳、视口、选择、缩放和快捷键
├── node-registry/      # 六类节点定义和卡片
├── ports/              # 端口 registry、兼容规则和连线反馈
├── shot-units/         # 镜头生产组和五个 Gate
├── director/           # 导演台入口、路由和摘要
├── generation/         # 能力预览、确认、候选和任务关联
├── quality/            # 质量报告和问题跳转
├── delivery/           # 正式采用、清单、ZIP、EDL/FCPXML
└── legacy-adapter/     # 旧画布只读投影和升级入口
```

`Canvas.vue` 只保留路由上下文、页面骨架和模块协调。Three.js 生命周期、导演状态和生成编排不得继续留在根组件。

## 7. 导演台设计修订

### 7.1 布局

```text
顶部：返回 Canvas｜SHOT｜时长/FPS｜草稿状态｜冻结版本｜生成预演
左侧：场景树、资产版本、分组、显示和锁定
中部：Three.js 视口、导演/机位视角、Gizmo、安全框
右侧：对象属性、动作片段、摄影参数、连续性和控制包检查
底部：单镜头时间线
```

### 7.2 坐标与旋转协议

- 领域协议使用右手坐标、Y-up、米制：`RH_Y_UP_METERS`。
- Three.js 直接使用该协议。
- Blender 原生 Z-up；Worker 入口将领域坐标转换为 Blender 坐标，输出清单和相机数据时转换回领域坐标。
- 持久化旋转真值使用归一化 Quaternion。
- 界面可以显示和编辑 Euler，但必须声明旋转顺序并在保存前转换为 Quaternion。
- 黄金场景测试覆盖对象位置、朝向、相机焦段、LookAt、动画和往返转换误差。

### 7.3 时间协议

- 时间区间使用半开区间 `[0, duration_ms)`。
- `frame_count = ceil(duration_ms / 1000 * fps)`。
- 有效帧索引为 `0..frame_count-1`。
- 关键帧保存 `time_ms` 和派生 `frame_index`；发生舍入时以 `time_ms` 为事实值。
- Blender 渲染后按统一时间基校验并精确截取，输出媒体时长不得因尾帧定义产生一帧偏差。

### 7.4 时间线轨道

- 对象变换轨；
- 角色动作片段轨；
- 摄影机变换轨；
- 镜头参数轨；
- 对白/节拍轨；
- 连续性标记轨。

动作首期只使用版本化动作片段，不开放逐骨骼关键帧。对象和相机插值支持保持、线性、缓入缓出和 Cubic Bézier。

### 7.5 本期实用增强包

1. 机位和镜头运动预设：景别、焦段、机位高度、推拉摇移和跟拍。
2. 角色动作片段预设：站立、行走、奔跑、坐下、转身、指向和基础交互。
3. 受控灯光/材质预设：只暴露强度、色温、方向、粗糙度和预设选择，不开放节点图。
4. 冻结前自动检查：资产缺失、动作重叠、时间越界、越轴风险、相机穿模、参考职责冲突和模型能力超限。

自动检查可以阻止冻结或给出警告，但不得自动修改导演意图。

## 8. 模型路由与生成闭环

```text
节点意图
→ CapabilityRequest
→ 能力编译
→ AiRouter/new-api 供应商路由
→ ModelAdapter 适配预览
→ 用户确认模型与费用
→ GenerationRequestSnapshot
→ GenerationTask / TaskAttempt
→ GenerationCandidate
→ QualityReport
→ ShotAdoption
→ DeliveryManifest
```

### 8.1 责任边界

- Canvas 侧能力编译器将节点、资产和导演语义转换为模型无关 `CapabilityRequest`。
- 现有 AiRouter/new-api 负责可用供应商、模型实例和调用通道。
- ModelAdapter 负责版本化能力限制、素材职责分配、Prompt 引用、参数转换和响应规范化。
- 任务和费用状态进入现有生成域和任务事件中心，不在 Canvas 新建第二套任务状态机。

### 8.2 预览和确认

提交前必须展示：

- 推荐模型和原因；
- 质量、速度和成本档位；
- 实际输入资产版本与参考职责；
- 适配器版本和被裁剪的素材；
- 预计积分和可能的替代模型；
- 模型限制和内容安全提示。

用户确认后冻结请求快照。确认后模型不可用时，必须重新预览并再次确认，禁止静默切换模型或费用。

### 8.3 Seedance Adapter Gate

生产启用前必须验证：

1. 可用 API 与鉴权方式；
2. 实际模型 ID、版本和区域；
3. 输入上传、URL、大小、格式、时长和画幅限制；
4. 9 图/3 视频/3 音频是否适用于所接 API；
5. 异步任务、回调、轮询、取消和幂等语义；
6. 计费、失败扣费、部分输出和退款规则；
7. 内容安全、真人肖像、日志和数据保留要求；
8. 限流、并发、SLA 和灾备方式。

Gate 未通过时只能使用明确标识的 Provider Sandbox，不能把模拟成功展示为真实结果。

## 9. Blender Worker

### 9.1 输入和输出

输入：

- 不可变 DirectorRevision；
- 资产版本和短期签名下载地址；
- Blender 镜像/模板版本；
- 草稿或标准预演渲染预设。

输出：

- 相机运动预演；
- 角色动作/交互参考；
- 首帧、尾帧和关键节拍帧；
- 相机路径和镜头参数；
- 资产装配清单、转换摘要、日志和校验和。

### 9.2 执行约束

- 每个任务在隔离临时目录运行。
- 镜像固定 Blender、插件、字体、FFmpeg 和模板版本。
- Worker 不持久保存业务凭证，日志不记录签名 URL 和敏感 Prompt。
- 下载文件校验 MIME、大小、哈希和许可状态。
- 任务配置 CPU/GPU、内存、磁盘、超时和最大输出预算。
- 相同 revision、模板和资产版本的幂等重试必须保持结构一致。
- 失败保留脱敏诊断摘要，临时文件按策略清理。

## 10. 数据模型

### 10.1 复用并增强

- `canvas_projects`：增加模式、schema 版本、迁移状态和最近稳定快照引用。
- `canvas_nodes`：增加 ShotWorkUnit、节点 schema 版本和模型策略引用。
- `canvas_edges`：增加端口契约版本、迁移状态和校验结果。
- `generation_tasks`：继续作为任务事实源，补齐请求快照、Workspace、创建人、幂等、重试链和结算引用。
- 资产表：继续保存媒体和版本，不复制文件所有权到 Canvas。

### 10.2 新增表

```text
canvas_shot_units
director_scenes
director_drafts
director_revisions
director_revision_assets
generation_request_snapshots
generation_task_attempts
generation_candidates
canvas_quality_reports / canvas_quality_issues
shot_adoptions
delivery_manifests
delivery_manifest_items
canvas_migration_reports
```

`GenerationVariant` 保留为旧接口兼容投影；新链路写 `GenerationCandidate`，不得长期双写两套候选事实。

## 11. API 收敛

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/v1/canvas/nodes/{nodeId}/model-requests/preview` | 能力、路由、素材职责和费用预览 |
| POST | `/api/v1/canvas/nodes/{nodeId}/model-requests` | 冻结请求快照并创建任务 |
| GET | `/api/v1/canvas/nodes/{nodeId}/candidates` | 节点候选列表和当前选择 |
| PUT | `/api/v1/canvas/nodes/{nodeId}/candidate-selection` | 更新节点局部候选选择 |
| GET | `/api/v1/canvas/projects/{projectId}/shot-units/{unitId}/director-scene` | 读取导演草稿和版本摘要 |
| PUT | `/api/v1/canvas/projects/{projectId}/shot-units/{unitId}/director-scene/draft` | 乐观锁保存导演草稿 |
| POST | `/api/v1/canvas/projects/{projectId}/shot-units/{unitId}/director-scene/validate` | 校验草稿 |
| POST | `/api/v1/canvas/projects/{projectId}/shot-units/{unitId}/director-scene/revisions` | 冻结不可变 revision |
| POST | `/api/v1/director-revisions/{revisionId}/preview-renders` | 创建 Blender 预演任务 |
| POST | `/api/v1/director-revisions/{revisionId}/model-requests/preview` | 预览导演包的模型适配与费用 |
| POST | `/api/v1/director-revisions/{revisionId}/model-requests` | 创建导演包生成任务 |
| POST | `/api/v1/canvas/projects/{projectId}/shot-units/{unitId}/adoptions` | 创建正式采用 revision |
| POST | `/api/v1/canvas/projects/{projectId}/delivery-manifests` | 固化交付清单 |
| POST | `/api/v1/delivery-manifests/{manifestId}/packages` | 创建 ZIP/EDL/FCPXML 异步任务 |
| GET | `/api/v1/canvas/projects/{projectId}/migration-report` | 读取旧画布迁移报告 |
| POST | `/api/v1/canvas/projects/{projectId}/upgrade` | 在确认后执行单画布升级 |

规则：

- 所有写接口携带 `Idempotency-Key`。
- 草稿更新携带 `If-Match`，冲突返回 409 和差异摘要。
- 正式采用只允许 ShotWorkUnit API；节点 API 不能改变正式采用。
- 所有异步操作返回统一任务引用并进入任务事件中心。

## 12. 旧画布兼容迁移

### 12.1 迁移原则

- 新链路单写，旧链路只读兼容。
- 先影子投影，再按单画布事务升级。
- 升级前生成完整备份快照和迁移报告。
- 歧义数据必须人工确认，不静默转换或删除。
- 升级后不重新写旧事实表；回滚依靠备份和兼容投影，不恢复双写。

### 12.2 四步流程

1. 盘点：扫描节点、连线、导演 JSON、任务和资产引用，生成分类报告。
2. 影子投影：用新 ViewModel 读取旧数据，与旧页面摘要和节点计数对比，不修改数据。
3. 单画布升级：备份、事务写入 ShotWorkUnit、端口契约、导演版本和候选映射。
4. 灰度切换：按 Workspace/Canvas 开启新内核；旧 API 转只读并记录调用。

### 12.3 歧义处理

- 带导演数据的旧 `reference` 映射为 `director`。
- 其他 `reference` 根据媒体类型和用途进入迁移向导；无法判断时标记 `NEEDS_CONFIRMATION`。
- `out → in` 通用连线按源节点、目标节点和已有元数据推断端口。
- 无法推断的连线保持可见但禁止执行，用户确认后生成新连接 revision。
- 旧 compose/export 任务保留审计读取，不迁移为新的 DeliveryManifest。

## 13. 错误、降级和安全

| 场景 | 行为 |
| --- | --- |
| Workspace/账户依赖失败 | 最近项目和草稿只读；禁止新建、迁移和跨空间写操作 |
| WebGL 不可用 | 可查看版本、历史预演和检查报告；禁用 3D 编辑 |
| 资产版本失效 | 阻止冻结或生成，定位具体资产和连接 |
| 上游版本变化 | 标记输入过期，不自动覆盖或重生成 |
| 模型确认后失效 | 重新预览并再次确认模型和费用 |
| 部分候选失败 | 保留成功候选，按实际规则结算/退还 |
| Blender 失败 | 保留 revision 和诊断，可幂等重试，不影响草稿 |
| 乐观锁冲突 | 不覆盖远端，提供加载远端、保留本地副本或另存 revision |
| 质检高风险 | 按策略阻止正式采用；授权用户填写原因后强制采用并审计 |

安全要求：

- 所有资源按 Workspace、项目和角色鉴权。
- 资产下载使用短期签名 URL。
- 真人肖像和声音参考记录授权状态。
- Worker 运行在隔离环境并限制网络出口。
- 日志、任务事件和质量报告不得泄露签名 URL、凭证或未脱敏敏感 Prompt。
- 导演包、请求快照、采用和交付操作保留完整审计。

## 14. 分阶段交付

### R0：稳定与止损

- 项目中心加载错误和只读降级。
- 统一 `director` 节点类型并生成旧 `reference` 盘点报告。
- 隐藏假入口，移除剪辑、音频处理和合成入口。
- 建立 Feature Flag、迁移报告和新旧 API 契约测试。

验收：当前用户能够明确区分成功、空数据、无权限和依赖失败；页面不再出现无限骨架屏和假成功。

### R1：可迁移生产内核

- 探索/正式生产双模式。
- ShotWorkUnit、类型化端口和连接迁移。
- 请求快照、候选、节点选择、ShotAdoption 基础模型。
- 任务事件中心关联。
- 拆分 Canvas 根组件。
- 单画布备份、影子读、升级和灰度。

验收：普通镜头无需导演台即可从输入、费用确认、生成走到候选；旧画布无静默数据损失。

### R2：真实导演台

- Three.js、GLB、场景树、TransformControls 和机位视角。
- 单镜头时间线、对象/相机关键帧和动作片段。
- DirectorDraft、自动保存、乐观锁和 DirectorRevision。
- 机位、动作、灯光和材质预设。
- 冻结前自动检查。

验收：相同 revision 重开后场景和时间线一致；冻结后编辑不会改变在途任务。

### R3：模型与 Blender

- Seedance 供应商 Gate。
- CapabilityRequest、路由预览和 Adapter 框架。
- Blender Worker、坐标转换、Eevee 预演和 FFmpeg 输出。
- Seedance Adapter、素材职责预览、费用确认和请求快照。
- 任务、Attempt、结算和失败补偿。

验收：普通和导演两条链路均产生真实可追溯候选；Web 与 Blender 黄金场景一致。

### R4：质量与交付

- 质量问题时间区间和来源定位。
- 严重度策略、人工强制采用和审计。
- ShotAdoption、DeliveryManifest、ZIP 和校验和。
- EDL/FCPXML 与素材路径映射。
- 产品指标和质量基线。

验收：外部剪辑工具可以导入交换文件并定位对应素材；Canvas 内没有成片编辑入口。

## 15. 阶段 Gate

### G0：Seedance API

真实 API、配额、回调、取消、幂等、计费和内容安全合同全部验证后才能进入生产适配。

### G1：资产和许可

GLB 归一化、签名 URL、版本哈希、真人肖像/声音授权和许可审计必须可用。

### G2：Blender Worker

镜像版本、资源配额、并发、超时、清理、成本和故障恢复压测通过。

### G3：费用闭环

积分预估、冻结、实扣、部分退还、释放、补偿和对账闭环通过。

任一 Gate 未通过时，不得以 mock 或 Sandbox 结果冒充生产成功。

## 16. 测试策略

### 16.1 单元和 schema 测试

- 节点类型和端口兼容矩阵；
- 时间、帧和半开区间换算；
- Quaternion/Euler 和 Y-up/Z-up 往返转换；
- 关键帧插值、动作重叠和混合；
- DirectorRevision 和请求快照哈希稳定性；
- 模型能力裁剪、素材排序和 Prompt 引用；
- 采用 revision 和交付清单一致性。

### 16.2 契约和集成测试

- 新旧 Canvas 读模型对比；
- 单画布迁移、幂等升级和失败回滚；
- 普通节点 → 预览 → 确认 → Task → Candidate；
- DirectorRevision → Blender → Adapter → Candidate；
- 任务失败、部分候选、取消、重试和结算补偿；
- ShotAdoption → DeliveryManifest → ZIP/EDL/FCPXML；
- Workspace 隔离、签名 URL 和授权检查。

### 16.3 浏览器与视觉测试

- 项目中心成功、空态、无权限、超时和只读降级；
- 节点创建、类型化连线、浮动编辑器和过期传播；
- 不同画幅下安全框和机位预览；
- WebGL 降级、刷新恢复和乐观锁冲突；
- 外部后期入口只生成交换文件，不出现成片编辑器。

### 16.4 黄金场景

至少覆盖双人对话、角色入画、跟拍、推近、群众阵列和道具交互。每个场景固定资产、DirectorRevision、Blender 镜像和预期相机/时长摘要，用于 Web/Blender 一致性回归。

## 17. 验收标准

### 17.1 数据与迁移

- 所有旧画布都进入“可自动升级、需人工确认、无法升级”三类之一。
- 不允许存在未分类画布。
- 自动升级不静默丢失节点、连线、导演数据、候选或资产引用。
- 相同幂等键不会重复升级、生成、渲染、采用或扣费。
- 冻结 revision 和请求快照的哈希在相同输入下稳定。

### 17.2 产品与功能

- 空白画布可以探索和试生成。
- 正式采用和交付前必须绑定内容项目与分镜 revision。
- 普通镜头无需导演台即可完成生产。
- 多个视频节点可以复用同一 DirectorRevision。
- 系统不得自动插入、创建或建议导演台。
- Canvas 不提供多轨剪辑、最终混音和成片合成。
- EDL/FCPXML 和素材路径映射可以被目标外部后期工具导入。

### 17.3 可靠性

- 并发草稿修改不会静默覆盖。
- 账户中心、资产、Blender 或模型依赖失败时，用户草稿不丢失。
- 资产失效能定位具体版本和连接。
- 部分候选失败不会丢失成功候选。
- 费用预估、冻结、实扣、退还和补偿可审计。

### 17.4 产品指标

- 首次生成可采用率；
- 单个采用镜头平均生成次数和积分成本；
- 普通镜头从输入完成到提交生成的中位耗时；
- 导演纠偏镜头从打开导演台到重新提交的中位耗时；
- 质量定位后的返工成功率；
- 多角色镜头无效抽卡次数相对基线至少降低 30%。

## 18. 发布、回滚与清理

Feature Flag：

```text
CANVAS_KERNEL_V2
TYPED_PORTS
DIRECTOR_V2
MODEL_ADAPTER_V2
QUALITY_DELIVERY_V2
```

- 按 Workspace 和 Canvas 灰度。
- 每个阶段先内部项目，再小规模真实项目，再扩大范围。
- 升级后新链路单写；回滚使用备份快照和兼容读投影。
- 旧 API 转只读后记录调用方，直到调用量归零。
- 连续两个发布周期完成迁移审计且无回滚需求后，才删除旧 compose/export API、旧导演 mock、`VideoComposeTimeline` 和兼容投影。

## 19. 实施计划拆分要求

本设计必须拆成 R0、R1、R2、R3、R4 五份独立实施计划。每份计划必须包含：

- schema 和回滚脚本；
- 后端 API 与契约测试；
- 前端交互和浏览器 E2E；
- Feature Flag 与灰度步骤；
- 数据迁移或兼容策略；
- 监控、告警和验收证据；
- 不影响当前工作区其他未提交功能的文件范围。

不得将五个阶段合并成一次性大改。
