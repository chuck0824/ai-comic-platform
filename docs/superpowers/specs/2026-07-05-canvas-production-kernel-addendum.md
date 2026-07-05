# Canvas 生产内核设计补充与缺陷修复

> 日期：2026-07-05
> 状态：补充设计，待评审
> 前置文档：`2026-07-05-canvas-production-kernel-completion-design.md`
> 冲突规则：本文件是对前置设计的权威补充；覆盖 R0–R4 五份实施计划中的未决问题

## 1. 概述

本文件针对六份文档的跨文档审查中发现的 **25 个缺陷**按优先级给出完整修复方案。每个缺陷给出：现状、影响、决策和实施指引。

---

## 2. P0 修复

### 2.1 端口方向协议（缺陷 #5、#6）

**现状**：端口 payload 类型列表存在，但未定义输入/输出方向，也未定义端口到语义角色（identity、scene、composition 等）的映射。

**决策**：每个端口定义必须包含方向；同类型多条输入连线时必须通过 `role` 区分。

```text
端口定义（修订后）：
port_key | payload_type   | direction | allowed_roles
---------|----------------|-----------|---------------------------
text_out | text           | OUTPUT    | ["prompt", "dialogue", "description"]
shot     | shot           | OUTPUT    | ["shot_identity"]
image_ref| image_ref      | OUTPUT    | ["identity", "scene", "prop", "character"]
image_ref| image_ref      | INPUT     | ["identity", "scene", "composition", "style_ref"]
motion_ref|motion_ref     | OUTPUT    | ["motion_source"]
motion_ref|motion_ref     | INPUT     | ["motion_reference"]
camera_ref|camera_ref     | OUTPUT    | ["camera_source"]
camera_ref|camera_ref     | INPUT     | ["camera_reference"]
audio_ref| audio_ref      | OUTPUT    | ["audio_source"]
audio_ref| audio_ref      | INPUT     | ["audio_timing", "audio_reference"]
director_package|director_package|OUTPUT|["director_output"]
director_package|director_package|INPUT |["director_input"]
video_candidate|video_candidate|OUTPUT  |["candidate_output"]
quality_report|quality_report|OUTPUT   |["quality_output"]
```

**连线规则**：
1. `direction` 必须 OUTPUT → INPUT。
2. 同节点同 port_key 最多允许一条同 role 连线。
3. 多输入连线必须指定不同 role。
4. 同 port_key 可以同时有 OUTPUT 和 INPUT 定义（例如 `image_ref` 既是 image 节点的输出、也是 video 节点的输入）。

**实施**：
- R1 `CanvasPortRegistry` 的 `PortDefinition` 增加 `direction` 和 `allowedRoles` 字段。
- 连线创建时校验 role 唯一性：`(target_node_id, target_port, role)` 唯一约束。
- R3 `CapabilityCompiler` 直接消费 role 生成 `SemanticReference`，不再需要启发式推断。

---

### 2.2 质量问题自动检测源（缺陷 #7）

**现状**：R4 定义了质量报告的 7 个维度和策略，但未说明质量问题由谁、何时、如何产生。

**决策**：质量问题有四个来源，按优先级覆盖：

```text
来源                    | 触发时机            | 覆盖维度
------------------------|---------------------|--------------------------
生成模型返回的元数据     | 候选生成完成回调     | identity, action, physics
Blender 预演差异分析     | 预演渲染完成回调     | composition, camera, continuity
自动化规则引擎           | 候选/预演写入后      | audio_timing, continuity, physics
人工审查录入             | 任何时间             | 全部七个维度
```

**自动化规则引擎**的最小规则集：

```text
规则 ID           | 维度         | 条件
------------------|-------------|------------------------------------------
AUTO_DUR_MISMATCH | continuity  | 候选时长与 ShotWorkUnit.target_duration_ms 偏差 > 500ms
AUTO_ASPECT_MISMATCH| composition| 候选画幅与 ShotWorkUnit.aspect_ratio 不一致
AUTO_FACE_COUNT    | identity    | 检测人脸数 < 预期角色数（含主要角色缺失）
AUTO_MOTION_BLUR   | action      | 运动模糊面积 > 15% 帧面积 或 连续 3 帧模糊
AUTO_SILENCE_GAP   | audio_timing| 音频区间存在 > 200ms 静音段且不在节拍边界
AUTO_CAMERA_JITTER | camera      | 连续帧间相机位移 > 镜头参数允许的抖动阈值
AUTO_FLICKER       | physics     | 相邻帧亮度波动 > 8% 且非灯光变化段
```

**实施**：
- R4 增加 `QualityDetectionService` 和 `AutoQualityRuleEngine`。
- 生成任务回调后自动触发规则引擎；Blender 回调后触发差异分析。
- 人工录入通过 `POST /api/v1/canvas/candidates/{candidateId}/quality-issues`。
- 多次来源对同一时间区间的报告合并，不重复计数。

---

### 2.3 Webhook 回调签名验证（缺陷 #14）

**现状**：R3 的 `acceptCallback(String taskUuid, String manifestHash, BlenderResult result)` 无签名机制。

**决策**：所有外部 Worker 回调必须携带 HMAC-SHA256 签名。

```text
回调签名协议：
1. 调度时为每个任务生成 callback_secret（32 字节随机，不存日志）。
2. 回调 Header：
   X-Callback-Signature: t=1700000000,v1=HMAC-SHA256(callback_secret, "{taskUuid}\n{manifestHash}\n{timestamp}")
3. 服务端校验：
   - timestamp 不超过当前时间 ±5 分钟
   - HMAC 恒定时间比较
   - 签名错误返回 401，不泄露任务是否存在
4. 校验通过后方可进入 acceptCallback 业务逻辑。
```

**实施**：
- R3 的 `DirectorPreviewService.create()` 生成 `callback_secret`，仅存储 SHA-256 哈希。
- `DirectorRevisionController` 增加 `CallbackSignatureFilter`。
- Blender Worker 在 manifest 中接收 `{taskUuid, callbackUrl, callbackSecret}`，不写日志。

---

### 2.4 FCPXML 与 EDL 输出规范（缺陷 #8、#9）

**现状**：R4 只笼统提到生成 EDL/FCPXML，无具体 schema 定义。

**决策**：

**FCPXML 版本**：输出 **FCPXML 1.9**（Final Cut Pro 10.6+ 兼容），结构如下：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE fcpxml>
<fcpxml version="1.9">
  <resources>
    <asset id="r1" name="SHOT_001_identity" src="media/SHOT_001.mp4"
           start="0s" duration="120000/24000s" format="r2" />
    <format id="r2" name="FFVideoFormat1080p24" width="1920" height="1080" />
  </resources>
  <library>
    <event name="CanvasDelivery">
      <project name="Delivery_v1">
        <sequence format="r2" duration="360000/24000s" tcStart="0s" tcFormat="NDF">
          <spine>
            <asset-clip ref="r1" offset="0s" name="SHOT_001"
                        start="0s" duration="120000/24000s" />
            <asset-clip ref="r3" offset="120000/24000s" name="SHOT_002"
                        start="0s" duration="120000/24000s" />
          </spine>
        </sequence>
      </project>
    </event>
  </library>
</fcpxml>
```

映射规则：
- 每个 `ShotAdoption` → 一个 `asset-clip`，按 `sort_order` 排列。
- 媒体路径使用相对路径 `media/SHOT_{n}.{ext}`。
- 源时间码从 `src_tc` 属性透传（如果 ShotWorkUnit 有定义）。
- 不支持变速或多机位同步——这些超出 Canvas 边界，由外部后期完成。

**EDL 版本**：输出 **CMX3600 EDL**：

```text
TITLE: Canvas Delivery v1
FCM: NON-DROP FRAME
001  SHOT_001  V    C        00:00:00:00 00:00:05:00 00:00:00:00 00:00:05:00
* FROM CLIP NAME: SHOT_001_identity.mp4
* COMMENT: adopted by user_7 at 2026-07-05T10:00:00Z
002  SHOT_002  V    C        00:00:05:00 00:00:10:00 00:00:05:00 00:00:10:00
* FROM CLIP NAME: SHOT_002_identity.mp4
```

**EDL 能力边界声明**（写入交付清单的 README）：
> EDL 仅记录镜头顺序和入出点。变速、转场、多轨音频混音、复合镜头和调色信息不在 EDL 中保留。完整创作意图请参考导演台 revision 和 FCPXML。

**实施**：
- R4 的 `FcpxmlWriter` 增加格式版本常量和 schema 校验。
- R4 的 `EdlWriter` 增加 CMX3600 格式头和 `FROM CLIP NAME` 注释行。
- 交付 ZIP 中增加 `README.txt`（含能力边界声明）、`manifest.json`、`timeline.edl`、`timeline.fcpxml`。

---

## 3. P1 修复

### 3.1 Undo/Redo 命令栈（缺陷 #10）

**现状**：R2 有自动保存和乐观锁，无本地编辑历史。

**决策**：在导演台前端状态中实现命令模式撤销栈。

```text
命令粒度：
- ADD_OBJECT / REMOVE_OBJECT
- TRANSFORM（position/rotation/scale 的 before/after 快照）
- CHANGE_KEYFRAME（单个关键帧的值变更）
- CHANGE_PROPERTY（对象属性单字段）
- ADD_TRACK / REMOVE_TRACK
- APPLY_PRESET（整体快照）

约束：
- 最大 100 步，超出丢弃最旧的。
- 自动保存和冻结操作清空 dirty 标记但不重置 undo 栈。
- 从远端加载草稿后清空本地 undo 栈。
- undo 操作本身进入 redo 栈。
- 连续同类型 TRANSFORM 操作在 500ms 内合并为一步（避免拖拽产生几百步）。
```

**实施**：
- R2 增加 `director/state/undoStack.js`：`push(command)`, `undo()`, `redo()`, `clear()`, `canUndo`, `canRedo`。
- 所有 `DirectorDocument` 变更通过命令执行，不直接 mutate。
- 快捷键 Ctrl+Z / Ctrl+Shift+Z（Mac: ⌘Z / ⌘⇧Z）。

---

### 3.2 生成任务重试策略（缺陷 #15）

**现状**：R3 有 `TaskAttempt` 但不定义重试规则。

**决策**：

```text
可重试错误码：
  PROVIDER_TIMEOUT, PROVIDER_500, RATE_LIMITED, NETWORK_ERROR,
  WORKER_OOM, WORKER_TIMEOUT, ASSET_DOWNLOAD_FAILURE

不可重试错误码：
  CONTENT_SAFETY_REJECT, INVALID_PROMPT, ASSET_LICENSE_DENIED,
  PORTRAIT_UNAUTHORIZED, PROVIDER_400, PROVIDER_401, PROVIDER_403,
  OUTPUT_VALIDATION_FAILED（连续 3 次同一 task）

重试参数：
  - 最大自动重试：3 次
  - 退避：指数退避，基数 2s，上限 60s（2s → 4s → 8s → 16s → 32s → 60s）
  - 用户手动重试：不限制次数，但每次创建新 attempt
  - 总超时（含重试）：15 分钟
  - 超时后标记 TERMINATED，退还预估积分

幂等：
  - 相同 idempotency_key 的重试返回已有 attempt
  - 不同 idempotency_key 创建新 attempt
```

**实施**：
- R3 `GenerationExecutor` 增加 `RetryPolicy` 配置。
- `GenerationTask` 增加 `max_attempts` 和 `retry_strategy` 列。
- 结算服务区分“用户取消退还”和“系统超时退还”。

---

### 3.3 数据保留与清理策略（缺陷 #23）

**现状**：全链路无数据清理策略，Storage 成本无限增长。

**决策**：

```text
资源类型              | 保留期       | 清理动作
----------------------|-------------|------------------------------------------
探索画布试生成候选     | 30 天       | 软删除候选和关联资源；画布保留
失败 TaskAttempt 日志  | 90 天       | 删除脱敏诊断；保留错误码统计
Blender Worker 临时目录 | 任务完成后 1h | 删除全部临时文件
成功候选资产           | 永久         | 画布删除时级联标记删除
DirectorDraft 历史版本 | 永久         | 冻结产生 revision 后保留最近 5 个草案快照
Delivery Package ZIP   | 30 天       | 过期后清理；manifest 元数据保留
已删除 Canvas 项目     | 30 天软删除  | 30 天后硬删除级联数据
```

级联删除规则（画布项目删除时）：
- 相关 ShotWorkUnit → 级联删除
- DirectorScene/Draft/Revision → 级联删除（资产引用保留）
- GenerationTask/Candidate/QualityReport → Candidate 输出资产仅当无其他引用时删除
- ShotAdoption → 保留审计日志
- DeliveryManifest → 如果已交付给外部后期，保留最后 manifest 快照和审计记录

**实施**：
- R4 增加 `DataRetentionScheduler`（定时任务）。
- 各表增加 `deleted_at` 软删除列和 `retention_policy` 字段。
- 资产服务增加引用计数，删除前校验无其他画布引用。

---

### 3.4 内容安全审核（缺陷 #22）

**现状**：R3 Seedance Gate 提到“内容安全”，但 Pipeline 中无审核关卡。

**决策**：在候选资产写入后、候选进入可用列表前，增加安全审核 Gate。

```text
审核流程：
1. 候选资产写入完成 → 触发安全审核任务。
2. 安全审核类型：
   - 图片/视频帧 → NSFW 检测、暴力检测、真人肖像授权校验
   - 音频 → 语音内容审核（如果包含对白轨道）
3. 审核结果：
   - PASS → 候选标记 available，通知用户
   - FLAGGED → 候选标记 review，人工审核队列
   - REJECTED → 候选标记 rejected，不展示给用户，记录审计
4. 超时兜底：审核 60s 未完成 → 标记 review，可查看但生成时显示“审核中”提示
```

**实施**：
- R3 增加 `ContentSafetyService`，对接现有内容安全基础设施。
- `GenerationCandidate` 增加 `safety_status` 列：`PENDING | PASS | FLAGGED | REJECTED`。
- 前端候选列表过滤 `safety_status = REJECTED` 的条目。
- 失败回调增加 `safety_reject` 错误码，不退积分（安全违规）。

---

### 3.5 ShotWorkUnit 更新 API（缺陷 #4）

**现状**：R1 只有 `createUnit`，缺少更新端点。

**决策**：增加 PATCH 端点，仅允许修改有限字段。

```text
PATCH /api/v1/canvas/projects/{projectId}/shot-units/{unitId}

可修改字段：
  - target_duration_ms（仅当无进行中生成任务）
  - fps（仅当无进行中生成任务）
  - aspect_ratio（仅当无进行中生成任务）
  - source_shot_id + source_shot_revision（仅 PRODUCTION 模式）
  - sort_order

不可修改：
  - mode（EXPLORATION → PRODUCTION 通过升级 API）
  - project_id（不可转移）

并发控制：携带 If-Match（row_version），冲突返回 409。
```

**实施**：
- R1 `CanvasKernelController` 增加 PATCH 端点。
- `CanvasKernelService` 增加 `updateUnit` 方法，校验无进行中任务。

---

### 3.6 Adapter 版本共存（缺陷 #16）

**现状**：R3 只有 `adapter_version` 字段但无版本共存策略。

**决策**：

```text
版本共存规则：
1. 同一 profile（如 seedance-2.0）可以有多个 adapter_version（seedance-v1, seedance-v2）。
2. adapter_version 与 profile 在 AdapterRegistry 中注册为独立 Bean。
3. 新提交的任务总是使用最新非弃用 adapter_version。
4. 已创建但未执行的任务仍使用冻结时的 adapter_version。
5. adapter_version 弃用规则：
   - 标记 deprecated：新任务不再使用，已有任务继续执行
   - 标记 retired：至少一个发布周期后，所有关联任务已完成/过期
   - 标记 removed：清理代码，旧请求快照保留但标记 adapter 不可用

6. 跨版本 adapter 的输出兼容性：
   - 相同 profile + 相同输入 → 输出应语义等价
   - 不保证哈希一致
   - 质量报告维度不变
```

**实施**：
- R3 增加 `AdapterRegistry`，管理 profile ↔ adapter_version 映射和生命周期。
- `ModelCapabilityProfile` 增加 `deprecated_adapter_versions` 和 `current_adapter_version`。
- 提交时总是使用 `current` 版本；查询时按快照中记录的版本反序列化。

---

## 4. P2 修复

### 4.1 多机位支持（缺陷 #12）

**现状**：DirectorDocument 只建模单个相机。

**决策**：在 R2 中增加 Camera 对象类型和机位切换。

```text
DirectorDocument 修订：
- cameras: CameraDefinition[]  // 至少一个，最大 8 个
- active_camera_id: string    // 当前编辑/预览机位

CameraDefinition：
  id, name, focal_length_mm, sensor_width_mm, aperture,
  near_clip, far_clip, aspect_ratio_override

机位切换：
- 导演台视口下拉切换 active camera
- 时间线显示 active camera 的关键帧
- 冻结时所有 camera 定义和关键帧都进入 revision
- 预演渲染使用 active camera 或用户选择
```

**实施**：
- R2 `DirectorDocument` 增加 `cameras` 和 `active_camera_id`。
- `DirectorViewport.vue` 增加机位选择器。
- 切换机位时保持场景对象不变，仅更换视口相机。

---

### 4.2 GLB 资产预算阈值（缺陷 #13）

**现状**：R2 提到限制但无数值。

**决策**：

```text
桌面端预算（默认）：
  - 最大三角形数（单个 GLB）：500,000
  - 最大三角形数（场景总计）：2,000,000
  - 最大独立对象数（场景总计）：500
  - 最大纹理分辨率（单张）：4096×4096
  - 最大纹理总内存（估算）：512 MB
  - 最大 GLB 文件大小：200 MB

移动端预算（可选，后续版本）：
  - 最大三角形数（总计）：500,000
  - 最大纹理分辨率：2048×2048
  - 最大 GLB 文件大小：50 MB

违反行为：
  - 加载前警告并阻止超过预算的 GLB
  - 场景总计超预算时阻止添加新对象
  - 不自动减面或压缩——由资产服务在导入时提供优化版本
```

**实施**：
- R2 `threeSceneController` 增加 `checkAssetBudget(glb)` 前置校验。
- 加载 GLB 后累计 `totalTriangles` 和 `totalObjects`，超限阻止。
- 前端展示当前使用量/上限。

---

### 4.3 SLO 与服务指标（缺陷 #21）

**现状**：R4 有产品指标但无服务级别目标。

**决策**：

```text
SLO 定义：
指标                         | 目标        | 测量窗口
-----------------------------|------------|----------
Canvas API P95 延迟（读）     | < 500ms    | 7 天滚动
Canvas API P95 延迟（写）     | < 2s       | 7 天滚动
生成任务提交成功率            | > 99.5%    | 30 天滚动
Blender 预演可用性            | > 99%      | 30 天滚动
导演台草稿保存成功率          | > 99.9%    | 7 天滚动
交付包生成成功率              | > 99%      | 30 天滚动
资产签名 URL 生成成功率       | > 99.95%   | 7 天滚动

告警阈值：
- 错误率超过 SLO 的 2× → 页面告警
- 错误率超过 SLO 的 5× → 电话告警
- P95 延迟超过目标 3× → 页面告警

错误预算：
- 按 30 天窗口计算
- 月度错误预算耗尽 → 冻结新功能发布，只修稳定性
- 在运维面板中可视化展示
```

**实施**：
- 所有 API 端点增加 `Timer.Sample` 记录。
- 任务事件中心的 `task_lifecycle_event` 和 `generation_task_attempt` 表作为可用性数据源。
- R4 Task 7 的 metric 采集并入 Prometheus/Grafana。

---

### 4.4 生成队列优先级（缺陷 #17）

**现状**：所有生成任务无优先级区分。

**决策**：

```text
优先级定义：
P0_HIGH   | 正式生产镜头正式采用后的重新生成（手动触发）
P1_NORMAL | 正式生产镜头首次生成
P2_LOW    | 探索画布试生成
P3_BATCH  | Blender 预演渲染

调度规则：
- 同一用户最多 2 个并发 P0/P1 任务
- P3 不占用 P0/P1 的并发槽位
- 优先级内 FIFO
- P0/P1 可以抢占 P3（Worker 收到信号后保存进度退出）
```

**实施**：
- `GenerationTask` 增加 `priority` 列。
- 任务队列按优先级分桶。
- Blender Worker 支持 SIGUSR1 优雅退出。

---

## 5. P3 修复

### 5.1 导演台快捷键体系（缺陷 #24）

```text
变换：
  W / G          选择移动工具
  E / R          选择旋转工具
  R / S          选择缩放工具
  X / Y / Z      锁定变换轴
  Shift+X/Y/Z    排除该轴

导航：
  MMB 拖拽       旋转视图
  Shift+MMB      平移视图
  Scroll         缩放视图
  F              聚焦选中对象
  Ctrl+0         切换到 active camera 视角
  `               切换导演/机位视角

时间线：
  Space           播放/暂停
  ← / →          前一帧/后一帧
  Shift+←/→     跳至前一个/后一个关键帧
  Home / End     跳至开始/结束
  I              在当前帧插入关键帧（仅选中轨道的已修改属性）

通用：
  Ctrl+Z          撤销
  Ctrl+Shift+Z    重做
  Ctrl+S          手动保存
  Delete          删除选中对象或关键帧
  Esc             取消选择/退出工具
```

**实施**：R2 `DirectorWorkspace.vue` 增加 `useDirectorHotkeys.js`，注册全局和视口专用快捷键。

---

### 5.2 返回导航状态保持（缺陷 #25）

**现状**：浮动编辑器跳转导演台后返回时的状态恢复未定义。

**决策**：

```text
导航状态协议：
1. 从 Canvas 浮动编辑器点击“打开导演台”：
   - 路由：/canvas/:projectId/shot-units/:unitId/director
   - 浏览器 history push（不 replace）
2. 导演台点击“返回 Canvas”：
   - history.back()
   - Canvas 页面从缓存恢复：展开的 ShotWorkUnit、选中的节点、浮动编辑器的页签
3. Canvas 页面 onActivated 时：
   - 如果有 active_shot_unit_id 在路由 query → 展开该 ShotWorkUnit
   - 如果有 active_node_id → 选中并打开浮动编辑器
   - 浮动编辑器页签恢复到最后使用的 tab
4. 状态存储：sessionStorage['canvas_ui_state'] = { activeShotUnitId, activeNodeId, activeTab, scrollPosition }
```

**实施**：
- R2 `DirectorWorkspace.vue` 的返回按钮使用 `router.back()`。
- Canvas.vue 增加 `useCanvasUIState` 读写 sessionStorage。
- 导演台路由进入前写入当前 UI 状态。

---

### 5.3 黄金场景扩展（缺陷 #19）

在原有 6 个黄金场景基础上增加边界场景：

```text
G7：零时长动作片段 → 验证拒绝
G8：1fps 极限帧率 → 验证通过、无帧索引越界
G9：120fps 极限帧率 → 验证通过、时间轴正确
G10：多角色同时重叠动作（≥3 个角色同时 ≥2 个动作片段）→ 验证重叠警告
G11：持续时间不被帧率整除（6000ms @ 24fps = 144 帧，最后一帧时间 = 5958.33ms）
G12：相机 LookAt 目标对象被删除 → 验证错误
G13：材质预设所有参数设为极值（强度 0/10000，色温 1000K/40000K）→ 验证拒绝或钳位
G14：空场景冻结（只有默认相机没有其他对象）→ 验证警告
```

---

### 5.4 杂项修正

**缺陷 #1 — MigrationReport 双形态**：
- `CanvasMigrationViews.java` 中的 `MigrationReport` record 重命名为 `MigrationAuditView`（R0 只读）。
- R1 的 `CanvasMigrationReport` 实体重命名为 `CanvasMigrationRecord`（持久化）。
- 避免同名不同语义。

**缺陷 #2 — 旧连线迁移默认值**：
- R1 单画布升级时，旧连线批量设置 `port_contract_version = 'legacy'`、`status = 'NEEDS_CONFIRMATION'`。
- 用户确认后生成新 `port_contract_version = 'canvas-ports-v1'`、`status = 'ACTIVE'`。

**缺陷 #3 — GenerationVariant 废弃时间线**：
- R1 上线后 → `GenerationVariant` 标记 deprecated，新代码禁止写入。
- R3 上线后 → 提供只读投影 View，底层表保留。
- R4 上线后一个发布周期 → 旧 compose/export API 移除，`GenerationVariant` 表归档。

**缺陷 #11 — Three.js 版本**：
- 实施时以 `npm view three versions` 确认存在的精确版本。
- 如 `0.166.1` 不存在，使用 `0.166.0` 并在 R2 文档中修正。

**缺陷 #18 — 性能测试**：
- R3 G2 压力测试量化目标：Blender Worker 单任务 P95 < 5min（纯场景，无角色动作）、P95 < 15min（含 2 角色动作片段）。
- Canvas 视口性能基准：100 节点时帧率 ≥ 30fps，500 节点时帧率 ≥ 15fps。
- API 并发：200 并发读 + 50 并发写下 P95 读延迟不超 SLO。

**缺陷 #20 — Blender 版本兼容**：
- R3 固定 Blender 4.2 LTS。
- 升级到下一个 LTS（4.x+1）需重新跑全部黄金场景并通过 G2 Gate。
- 并发运行两个 Worker 版本过渡，R3 中不要求。

---

## 6. 文档修订清单

| 文档 | 修订内容 |
|------|---------|
| completion-design | 第 4.3 节增加端口方向和角色定义；第 8 节增加生成队列优先级 |
| R0 plan | MigrationIssue/MigrationReport 重命名为 AuditView；旧连线迁移默认值 |
| R1 plan | PortDefinition 增加 direction/allowedRoles；增加 ShotWorkUnit PATCH API |
| R2 plan | 增加 undoStack.js、useDirectorHotkeys.js；DirectorDocument 增加多相机；GLB 预算阈值；useCanvasUIState |
| R3 plan | 增加 CallbackSignatureFilter、RetryPolicy、ContentSafetyService、AdapterRegistry；优先级队列 |
| R4 plan | 增加 QualityDetectionService、AutoQualityRuleEngine；FCPXML 1.9 完整 schema；EDL CMX3600 格式；DataRetentionScheduler；SLO 定义；黄金场景 G7–G14；内容安全审核 |

---

## 7. 分阶段影响

```text
R0：缺陷 #1 #2 → 命名修正和迁移默认值，不影响工期。
R1：缺陷 #5 #6 #4 → 端口方向/角色、ShotWorkUnit 更新 API，+1–2 天。
R2：缺陷 #10 #12 #13 #24 #25 → Undo/Redo、多机位、预算、快捷键、导航，+3–4 天。
R3：缺陷 #14 #15 #16 #17 #22 → 回调安全、重试、adapter 版本、优先级、内容安全，+3–5 天。
R4：缺陷 #7 #8 #9 #21 #23 #18 #19 #20 → 质量检测、交换格式、SLO、清理、性能、黄金场景，+4–6 天。
```

总计增量约 **11–17 天**，主要影响 R2–R4。

---

## 8. 验收补充

在原有 17 项验收标准基础上增加：

```text
18. 端口方向错误的连线在创建时被拒绝，错误码 46031 且消息包含方向和角色信息。
19. 所有 Blender/Seedance 回调必须携带有效 HMAC 签名，签名缺失或错误时返回 401。
20. FCPXML 输出通过 XML schema 校验并被 Final Cut Pro 10.6+ 成功导入。
21. EDL 输出附带 README 声明能力边界。
22. 质量报告在候选生成完成后 30s 内自动触发检测。
23. 安全审核 REJECTED 的候选不出现在用户候选列表中。
24. 探索画布未绑定分镜时“正式采用”按钮不可交互且有说明文字。
25. 导演台支持至少 50 步撤销/重做，拖拽操作在 500ms 内合并。
26. Canvas 视口 100 节点时渲染帧率 ≥ 30fps。
```

---

> **下一步**：本补充文档评审通过后，按上表逐文件修订 R0–R4 五份实施计划，然后进入 R0 实施。
