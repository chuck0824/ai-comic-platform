# AICP R2-A 剧本八阶段事实链修复设计

> 文档类型：R2-A 实施规格  
> 状态：已完成产品确认，待书面审阅  
> 日期：2026-08-17  
> 作者：待补  
> 审阅人：待补  
> 关联里程碑：待补  
> 上游基线：`docs/剧本创作模块_场景资产与八阶段融合说明.md`（权威八阶段口径）、R1 任务真值冻结契约（待补真实路径）  
> 核心目标：让八阶段成果、门禁、保存、恢复、候选采用和锁定状态由服务端事实驱动，不再依赖前端内存状态或项目恢复位置推断  
> 明确排除：A/B/C 专业分镜、镜头拆分合并、镜头资产快照、批量生成、画布能力、剧本交易、多轨剪辑  
> 实施节奏建议：本期交付偏满，建议拆分为 R2-A.1（事实链/门禁/流转）、R2-A.2（保存与 Fork/过期）、R2-A.3（AI 候选与交接快照）三个子期，避免开关一开全链路未齐。

## 1. 背景与问题

当前八阶段工作台已经具备页面、内容单元、草稿、内容版本、生成候选和项目恢复位置，但事实分散：

- 前端 `completedStages` 只存在于页面状态中，刷新后需重新推断。
- `content_projects.last_stage_key` 同时承担导航恢复和完成进度语义，无法证明阶段为何完成。
- `ContentUnit` 保存内容，缺少阶段使用的上游版本、门禁证据和过期原因。
- 自动保存、阶段推进和恢复位置更新由前端串联，任一步失败都可能造成页面与服务端漂移。
- 编辑已完成的上游阶段时，下游成果无法被精确标记为待确认或必须重做。
- 局部 AI 改写虽有候选概念，但选区、输入版本、revision、差异和采用之间缺少统一的冲突保护。

R2-A 新增阶段检查点事实模型和服务端阶段编排器，同时复用现有 ContentProject、ContentUnit、ContentVersion、ContentGenerationJob（剧本域生成任务，accept/discard）和审核能力。

> 命名说明：剧本工作台现网的 AI 生成任务为 `ContentGenerationJob`（`com.aicp.module.contentproject`）；`GenerationTask` 属画布/通用生成域（`com.aicp.module.generation`）。本文统一使用 `ContentGenerationJob`，避免实现时接错模块。

## 2. 范围与边界

### 2.1 本期交付

1. 八阶段检查点、阶段状态和输入快照。
2. 服务端阶段门禁与原子流转。
3. 草稿自动保存、恢复和 revision 冲突处理。
4. 已完成阶段重新编辑时自动 Fork 新草稿。
5. 上游变化后的下游过期传播与人工处理。
6. AI 局部改写的真实任务、服务端差异、候选采用和过期保护。
7. 审核通过、版本锁定和不可变保护。
8. 文字分镜成果到 R2-B 的可靠交接快照。
9. 历史项目保守迁移、灰度开关和回滚路径。

### 2.2 旧 API 取舍

- `PUT /api/v1/content-projects/{id}/resume-position`、`GET /api/v1/content-projects/{id}/workflow` 在灰度期保留，标记为 deprecated；R2-A.3 结束后列表页 workflow 标签改读检查点投影。
- 前端 `transitionGuard()`、`ProjectWorkflowService` 旧轨推断在开关关闭时仍为权威；开关开启后只用于恢复位置，不参与完成判定。

### 2.3 本期不做

- 不实现专业分镜 A/B/C 档位。
- 不实现镜头拆分、合并、重排和资产包冻结。
- 不实现分镜批量生图、视频、候选比较和批量采用。
- 不修改画布节点、模板、搜索、资产面板和工作流。
- 不实现剧本交易、支付、充值、卖家或采购能力；相关入口继续隐藏，代码和历史数据保留。
- 不实现多轨时间轴、复杂合成和 3D 导演台扩建。

## 3. 设计原则

1. `ContentVersion` 保存内容，`ContentStageCheckpoint` 保存阶段事实，两者不得混用。
2. `last_stage_key` 只表示用户上次停留位置，不能证明阶段已经完成。
3. 前端只能展示服务端检查点投影，不能自行写入完成状态。
4. 阶段流转前由服务端重新读取实际成果、版本、任务和审核证据。
5. 已采用或锁定版本不可原地覆盖；重新编辑必须 Fork 新草稿。
6. 上游修改不删除下游成果，只改变其可信状态并保存影响原因。
7. AI 结果先成为候选，用户采用后才能改变草稿。
8. revision 冲突禁止最后写入覆盖和模糊自动合并。
9. 所有确认、Fork、采用和过期处理都需要幂等键。

## 4. 领域模型

### 4.1 ContentStageCheckpoint

每个项目、每个阶段唯一一条检查点记录。

| 字段 | 类型 | 约束与语义 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `project_id` | BIGINT | 非空，所属 ContentProject |
| `stage_key` | VARCHAR(50) | 非空，八阶段固定枚举（见 §4.5） |
| `state` | VARCHAR(32) | 非空，阶段事实状态 |
| `primary_artifact_type` | VARCHAR(50) | PARAMETER_VERSION、UPLOAD_IMPORT、CONTENT_VERSION、REVIEW_SNAPSHOT、STORYBOARD_HANDOFF |
| `primary_artifact_id` | BIGINT | 当前主要成果 ID，可空 |
| `adopted_content_version_id` | BIGINT | 当前采用的 ContentVersion，可空 |
| `input_snapshot_json` | JSON/TEXT | 进入阶段时使用的上游事实引用 |
| `input_snapshot_hash` | CHAR(64) | 规范化输入快照 SHA-256 |
| `gate_result_json` | JSON/TEXT | 服务端门禁结果、阻断项、警告和证据 |
| `stale_reason_json` | JSON/TEXT | **多来源原因列表** `reasons[]`，每条含 `triggerStage`、`causeForkId`、`impactLevel`、`status`、`capturedAt`；支持叠加，不清零 |
| `revision` | INT | 非空，默认 0，阶段级乐观锁 |
| `completed_at` | DATETIME | COMPLETED/LOCKED 时写入 |
| `updated_by` | BIGINT | 最近操作人 |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |

唯一约束：`(project_id, stage_key)`。

### 4.2 阶段状态

| 状态 | 说明 | 是否允许进入下游 |
|---|---|---|
| `NOT_STARTED` | 尚未开始 | 否 |
| `IN_PROGRESS` | 有草稿或正在修订 | 否 |
| `BLOCKED` | 门禁存在阻断项 | 否 |
| `COMPLETED` | 当前成果已采用且门禁通过 | 是 |
| `POSSIBLY_STALE` | 上游变化，成果可能仍可用，等待确认 | 否 |
| `REGEN_REQUIRED` | 上游关键事实变化，必须修订或重新生成 | 否 |
| `LOCKED` | 成果已审核锁定，只能 Fork | 是 |

合法主状态转换：

```text
NOT_STARTED → IN_PROGRESS
IN_PROGRESS → BLOCKED / COMPLETED / LOCKED
BLOCKED → IN_PROGRESS
COMPLETED → IN_PROGRESS          # Fork 新草稿，保留原采用版本
LOCKED → IN_PROGRESS             # Fork 新草稿，锁定版本保持不可变
COMPLETED / LOCKED → POSSIBLY_STALE / REGEN_REQUIRED
POSSIBLY_STALE → COMPLETED / IN_PROGRESS / REGEN_REQUIRED
REGEN_REQUIRED → IN_PROGRESS
```

终态版本仍由 ContentVersion 状态表达；检查点状态不替代内容版本状态。

### 4.3 输入快照

`input_snapshot_json` 使用版本化结构，首版 `schemaVersion=1`：

```json
{
  "schemaVersion": 1,
  "projectParameterVersionId": 12,
  "upstreamStages": [
    {
      "stageKey": "adaptation",
      "checkpointRevision": 4,
      "artifactType": "CONTENT_VERSION",
      "artifactId": 301,
      "contentHash": "sha256"
    }
  ],
  "settingVersions": [41, 52],
  "capturedAt": "2026-08-17T10:00:00+08:00"
}
```

服务端对 JSON 键排序并计算 SHA-256。生成、审核、完成阶段和交接只能引用已持久化输入快照。

### 4.4 现有对象职责

| 对象 | 本期职责 |
|---|---|
| ContentProject | 项目、创作模式和用户恢复位置 |
| ContentUnit | 阶段内容容器和当前采用版本指针 |
| ContentVersion | 草稿、候选、采用、审核和锁定的实际内容 |
| ContentStageCheckpoint | 阶段状态、门禁、上游输入和过期原因 |
| `ContentGenerationJob` | AI 执行过程、费用、取消、重试和 accept/discard |
| ReviewReport/Issue | 审核问题和通过证据 |
| StoryboardVersion | 文字分镜成果；专业分镜深化由 R2-B 负责 |

### 4.5 stage_key 权威枚举与旧轨映射

八阶段权威 `stage_key`（与前端 `scriptWorkbenchModel.js` 一致）：

| 序 | stage_key | 旧轨 key（`ProjectWorkflowService` / 建项默认） |
|---|---|---|
| 1 | `creation_settings` | `story_seed` |
| 2 | `novel_upload` | `import_review` |
| 3 | `novel_analysis` | `characters` / `synopsis` |
| 4 | `adaptation` | — |
| 5 | `structured_script` | `outline` |
| 6 | `script_body` | `content` |
| 7 | `review_revision` | `review` |
| 8 | `text_storyboard` | `destination` / `storyboard` |

迁移与运行时统一通过 `LEGACY_STAGE_MAP` 归一化到新八键；建项默认值需同步改为 `creation_settings`。

### 4.6 三类成果快照最小 schema

`UploadImportReceipt`、`ReviewSnapshot`、`StoryboardHandoffSnapshot` 本期最小必填：

| 快照 | 必填字段 | 存储位置 |
|---|---|---|
| `UploadImportReceipt` | `sourceFileRef`、`parseStatus`、`confirmedAt`、`contentUnitId` | 上传表 + 检查点 `primary_artifact_id` 引用 |
| `ReviewSnapshot` | `reviewedContentVersionId`、`openBlockers`、`passedAt`、`reviewerId` | 审核表 + 检查点 `gate_result_json` |
| `StoryboardHandoffSnapshot` | `reviewedScriptBodyVersionId`（**必引用审核通过正文版本**）、`continuityCheckResult`、`sceneCount`、`capturedAt` | 独立 handoff 表或 `StoryboardVersion` 扩展字段 |

### 4.7 三级 revision 职责

| revision | 持有对象 | 递增时机 | 冲突错误码 |
|---|---|---|---|
| `project_revision` | ContentProject | 项目结构、恢复位置、阶段流转 | `STAGE_TRANSITION_INVALID` |
| `checkpoint_revision` | ContentStageCheckpoint | 阶段状态、门禁证据、过期原因变化 | `STAGE_REVISION_CONFLICT` |
| `content_unit_revision` | ContentUnit | 草稿内容保存、采用版本切换 | `EDIT_CONFLICT` |

自动保存只递增 `content_unit_revision`，不触碰 `checkpoint_revision`；阶段流转事务同时校验三者，任一不符按上表返回对应错误码。

## 5. 阶段编排

### 5.1 ContentStageOrchestrator

新增服务端编排器，负责：

- 读取项目和阶段检查点并校验权限。
- 校验项目、内容单元和检查点 revision。
- 调用对应 `StageGateEvaluator`。
- 从当前草稿创建不可变采用版本，或引用已有有效成果。
- 冻结输入快照和门禁证据。
- 原子完成来源阶段、开放目标阶段并更新恢复位置。
- 传播下游过期状态。
- 写入审计事件和 Outbox。

编排器不直接生成 AI 内容；AI 生成仍使用 `ContentGenerationJob` 和内容生成候选服务。

### 5.1.1 Outbox / 审计事件清单

编排器在事务内写入以下事件（最小 payload）：

| 事件名 | 触发 | 关键字段 |
|---|---|---|
| `StageCompleted` | 来源阶段进入 COMPLETED/LOCKED | `projectId`、`stageKey`、`adoptedVersionId`、`inputSnapshotHash`；`COMPLETE_HANDOFF` 时另含 `handoffSnapshotId`、`reviewedScriptBodyVersionId` |
| `StoryboardHandoffCaptured` | 文字分镜交接快照落库 | `projectId`、`handoffSnapshotId`、`reviewedScriptBodyVersionId`、`checkpointId` |
| `StageForked` | 已完成阶段重新编辑 | `projectId`、`stageKey`、`baseVersionId`、`newDraftId` |
| `StageMarkedStale` | 下游被传播过期 | `projectId`、`stageKey`、`triggerStage`、`causeForkId`、`impactLevel` |
| `StalenessResolved` | 用户处理过期 | `projectId`、`stageKey`、`action`、`operatorId` |
| `GateBlocked` | 门禁阻断 | `projectId`、`stageKey`、`blockers[]`、`warnings[]` |

### 5.2 阶段完成事务

一次确认进入下一阶段的事务顺序：

1. 校验操作者项目编辑权限。
2. 按 `project_id + stage_key` 锁定来源和目标检查点。
3. 比较检查点 revision、内容单元 revision 和项目 revision。
4. 读取当前草稿、采用版本、生成候选、审核和上传证据。
5. 运行服务端门禁。
6. 门禁阻断时写入 BLOCKED 投影并返回结构化问题，不完成阶段。
7. 门禁通过时创建或采用不可变成果版本。
8. 保存规范化输入快照与门禁证据。
9. 来源检查点进入 COMPLETED 或 LOCKED。
10. 目标检查点从 NOT_STARTED 进入 IN_PROGRESS。
11. 更新 `last_stage_key` 作为导航恢复位置。
12. 写审计/Outbox 事件。
13. 提交事务并返回八阶段最新投影。

任一步失败均回滚采用版本、检查点、恢复位置和事件。

### 5.3 八阶段成果与门禁

| 阶段 | 主要成果 | 服务端阻断条件（可计算） |
|---|---|---|
| 创作设置 | ProjectParameterVersion | `contentType`/`genre`/`episodeCount`/`duration`/`aspectRatio`/`styleId` 任一为空；`availableModelId` 为空；预算 `budgetEstimate` 不可执行 |
| 小说上传 | UploadImportReceipt | `sourceFileRef` 为空或 `parseStatus != CONFIRMED`；`contentUnitId` 缺失 |
| 小说分析 | ContentVersion + 已确认实体 | `entities.mainCharacters` 为空；`entities.coreScenes` 为空；`entities.events` 为空；`entities.worldview` 为空；存在 `Issue.severity=BLOCKER` 且 `status=OPEN` |
| 改编方案 | 已采用 ContentVersion + Hook | `episodeGoals` 为空；`mainConflict` 为空；`endingHook` 为空；`confirmedAt` 为空 |
| 结构化剧本 | ContentVersion | `episodes[].scenes[].beats` 为空；`targetDuration` 偏差超阈值且未确认 `WARNING:DURATION_VARIANCE` |
| 剧本正文 | ContentVersion | `scenes[].body` 为空；`saveStatus != SYNCED`；`assetRefs[]` 存在失效引用 |
| 审核修订 | ReviewSnapshot + 审核通过正文版本 | 存在 `Issue.severity=BLOCKER` 且 `status=OPEN`；`reviewedContentVersionId != currentScriptBodyVersionId` |
| 文字分镜 | StoryboardHandoffSnapshot | `continuityCheckResult != PASS`；`reviewedScriptBodyVersionId` 未引用审核通过版本；`sceneCount == 0` |

警告不会自动阻断，但用户继续时必须提交警告确认并进入门禁证据。

## 6. API 契约

### 6.1 查询阶段检查点

```http
GET /api/v1/content-projects/{projectId}/stage-checkpoints
```

返回八个阶段的状态、revision、主要成果、采用版本、门禁摘要、过期原因和允许操作。

响应体（八阶段投影，前端唯一数据源）：

```json
{
  "projectId": 12,
  "projectRevision": 18,
  "stages": [
    {
      "stageKey": "creation_settings",
      "state": "COMPLETED",
      "checkpointRevision": 3,
      "primaryArtifactType": "PARAMETER_VERSION",
      "primaryArtifactId": 41,
      "adoptedContentVersionId": null,
      "gateSummary": { "blockers": [], "warnings": [] },
      "staleReasons": [],
      "allowedActions": ["view", "fork"]
    }
  ],
  "lastStageKey": "script_body",
  "stageTruthEnabled": true,
  "latestStoryboardHandoff": null
}
```

灰度开启后，投影可附带 `latestStoryboardHandoff`（最新交接快照摘要，尚未交接时为 `null`）。

### 6.1.1 查询文字分镜交接快照（R2-B）

```http
GET /api/v1/content-projects/{projectId}/storyboard-handoff
```

返回该项目最新 `StoryboardHandoffSnapshot`；尚未 `COMPLETE_HANDOFF` 时返回资源不存在。最小字段：`id`、`reviewedScriptBodyVersionId`、`continuityCheckResult`、`sceneCount`、`contentHash`、`capturedAt`、`payload`。

### 6.2 预检阶段门禁

```http
GET /api/v1/content-projects/{projectId}/stages/{stageKey}/gate
```

预检仅返回当前证据，不改变状态。正式流转时必须重新执行门禁。

响应体：

```json
{
  "stageKey": "script_body",
  "blockers": [],
  "warnings": [{ "code": "DURATION_VARIANCE", "message": "目标时长偏差 18%" }],
  "evidence": { "adoptedVersionId": 301, "inputSnapshotHash": "sha256" }
}
```

### 6.3 原子阶段流转

```http
POST /api/v1/content-projects/{projectId}/stage-transitions
Idempotency-Key: stage-transition-uuid
```

```json
{
  "source_stage_key": "script_body",
  "target_stage_key": "review_revision",
  "project_revision": 18,
  "source_checkpoint_revision": 4,
  "target_checkpoint_revision": 0,
  "content_unit_id": 81,
  "content_unit_revision": 12,
  "warning_acknowledgements": ["DURATION_VARIANCE"]
}
```

最终阶段使用显式动作 `"action": "COMPLETE_HANDOFF"`（`target_stage_key` 省略或为 null），完成文字分镜并生成 R2-B 交接快照。

### 6.4 Fork 已完成阶段

```http
POST /api/v1/content-projects/{projectId}/stages/{stageKey}/fork
Idempotency-Key: stage-fork-uuid
```

请求体：

```json
{
  "checkpoint_revision": 4,
  "base_adopted_content_version_id": 301,
  "project_revision": 18
}
```

成功后创建新草稿、保留原采用版本、将当前阶段置为 IN_PROGRESS，并把下游置为 POSSIBLY_STALE。响应体返回更新后的八阶段投影（同 §6.1）。

### 6.5 处理过期状态

```http
POST /api/v1/content-projects/{projectId}/stages/{stageKey}/staleness-resolution
Idempotency-Key: stale-resolution-uuid
```

请求体：

```json
{
  "action": "CONFIRM_CURRENT",
  "checkpoint_revision": 5,
  "reason_filter": { "cause_fork_id": 901 },
  "note": "文案微调不影响下游结构"
}
```

允许动作：

- `CONFIRM_CURRENT`：用户确认旧成果继续有效，记录说明和操作者。
- `CREATE_DRAFT`：基于旧成果创建新草稿。
- `REGENERATE`：创建引用新输入快照的 `ContentGenerationJob`。
- `DISCARD_FORK`：放弃未采用的新草稿；仅清理由该 Fork 引起且未被其他变化覆盖的 POSSIBLY_STALE 标记（按 `reason_filter.cause_fork_id` 精确匹配，不误清其它来源）。

### 6.6 幂等键落库

- 存储表：`idempotency_records`，键为 `(user_id, idempotency_key)`，含 `request_hash`、`response_payload`、`created_at`。
- TTL：30 天；命中同键时比对 `request_hash`，一致则重放 `response_payload`，不一致返回 `IDEMPOTENCY_PAYLOAD_CONFLICT`。
- 适用范围：`stage-transitions`、`fork`、`staleness-resolution`、AI 改写采用。

## 7. 上游变化与过期传播

### 7.1 传播时机

用户首次持久化已完成阶段的新 Fork 时立即执行保守传播：

- 当前阶段进入 IN_PROGRESS，但仍保留 `adopted_content_version_id` 作为稳定基线。
- 所有直接或间接下游阶段保留成果并进入 POSSIBLY_STALE。
- 每个下游检查点写入触发阶段、基准版本、新草稿 ID 和传播时间。

### 7.2 新版本采用后的影响计算

采用新版本后比较旧、新内容哈希和结构化影响摘要：

- 仅文案表达变化且结构引用未变：允许下游保持 POSSIBLY_STALE，由用户确认。
- 场、角色、场景、关键事件、集目标或因果结构变化：相关下游升级为 REGEN_REQUIRED。
- 无内容变化：清除由本次 Fork 单独引发的过期标记。
- 同时存在其他上游变化：不得错误清除其他过期原因。

系统不自动覆盖或删除下游版本。

### 7.3 多来源过期叠加清除算法

`stale_reasons[]` 为叠加列表，清除时按 `cause_fork_id` 精确匹配：

```text
function resolveStaleness(checkpoint, action, reasonFilter):
  if action == DISCARD_FORK:
    # 只移除指定 causeForkId 贡献的原因，保留其它来源
    checkpoint.staleReasons = checkpoint.staleReasons
      .filter(r -> r.causeForkId != reasonFilter.causeForkId)
    # 若该下游已无任何原因，状态回退到 COMPLETED/LOCKED
    if checkpoint.staleReasons.isEmpty():
      checkpoint.state = checkpoint.wasLocked ? LOCKED : COMPLETED
  else if action == CONFIRM_CURRENT:
    # 标记匹配原因为 RESOLVED，不清除其它
    for r in checkpoint.staleReasons where r.causeForkId == reasonFilter.causeForkId:
      r.status = RESOLVED
  # 任何情况下不得删除其它 causeForkId 的原因
```

`STALENESS_REASON_MISMATCH` 在 `reasonFilter.causeForkId` 不存在或已被其它 action 处理时返回。

## 8. 自动保存与冲突处理

### 8.1 保存队列

- 每个 ContentUnit 独立维护串行保存队列。
- 停止输入 2 秒、切换内容单元、离开路由和 Cmd/Ctrl+S 触发保存。
- 后一个保存必须使用前一个成功响应返回的 revision。
- 保存失败后保留本地内容并停止显示“已保存”。
- 页面离开时如仍有未同步内容，显示明确确认。

### 8.2 冲突响应

`409 EDIT_CONFLICT` 返回：

```json
{
  "code": "EDIT_CONFLICT",
  "server_revision": 13,
  "server_draft_id": 901,
  "base_revision": 12,
  "server_content_hash": "sha256",
  "comparison_url": "/api/v1/content-units/81/conflicts?base_revision=12"
}
```

前端冲突面板提供加载远端、保留本地另存草稿、查看三方差异和手工合并。系统不自动执行模糊合并。

## 9. 局部 AI 改写

### 9.1 请求与候选

请求必须包含：内容单元、基准内容版本、revision、内容哈希、选区起止、选中文本哈希、操作类型、模型和幂等键。

流程：

```text
选择正文
→ 服务端校验 revision/hash/选区
→ 创建 ContentGenerationJob
→ 生成 ContentVersion candidate
→ 服务端生成 before/after 与结构化 Patch
→ 用户全部采用、部分采用或放弃
→ 采用时再次校验 revision/hash
→ Patch 应用到当前新草稿
→ 自动保存并递增 revision
```

### 9.2 Patch 约束

Patch 操作固定为：

```json
{
  "startOffset": 120,
  "endOffset": 188,
  "expectedTextHash": "sha256",
  "replacement": "替换后的正文",
  "reason": "增强冲突"
}
```

- Patch 必须按位置升序、互不重叠。
- 应用时从文本尾部向前执行，避免偏移漂移。
- 任一 expectedTextHash 不匹配则整个采用失败。
- 生成期间正文变化时，候选进入 STALE，不允许直接采用。
- AI 失败、取消、放弃和过期均不得修改正文或当前采用版本。

### 9.3 撤销与恢复

- 普通编辑使用当前编辑会话命令栈。
- 一次 AI 采用作为一个可撤销命令；撤销后产生新的草稿 revision。
- 页面重载或跨版本后不承诺无限撤销，使用版本恢复创建新草稿。
- 撤销不删除 `ContentGenerationJob`、候选或审计记录。

## 10. 前端状态与交互

### 10.1 阶段导航

左侧阶段导航只读取检查点：

- IN_PROGRESS：编辑中。
- BLOCKED：显示阻断数量。
- COMPLETED：成果已采用。
- POSSIBLY_STALE：黄色“需确认”。
- REGEN_REQUIRED：红色“需要重做”。
- LOCKED：只读锁定。

未进入阶段不能通过 URL query 绕过门禁。已完成阶段可以查看；开始编辑时自动调用 Fork。

### 10.2 保存状态

```text
未修改 → 有未保存修改 → 保存中 → 已保存
                         └→ 保存失败，可重试
                         └→ revision 冲突
```

保存成功只以服务端响应为准。错误提示保持可见，直到用户重试成功、解决冲突或明确放弃本地修改。

### 10.3 阶段确认

进入下一阶段前展示：

- 本阶段当前成果和采用版本。
- 使用的上游版本。
- 阻断项、警告和警告确认。
- 预计会被标记过期的下游成果。
- 保存状态和当前 revision。

收到阶段流转成功响应后，前端才更新导航和路由。

## 11. 历史数据迁移

### 11.1 保守迁移规则

- 不删除现有 ContentUnit、ContentVersion、上传文件、审核数据或恢复位置。
- `last_stage_key` 只能用于标记用户可能编辑到的位置。
- 旧轨 `stage_key`（`story_seed`、`import_review`、`outline`、`destination` 等）必须经 `LEGACY_STAGE_MAP` 归一化为新八键后再建检查点；建项默认值同步改为 `creation_settings`。
- 有草稿但无有效采用成果：IN_PROGRESS。
- 有有效采用版本且门禁证据可以重建：COMPLETED。
- 审核通过且版本不可变：LOCKED。
- 证据不足、输入版本无法确认或上下游不一致：POSSIBLY_STALE。
- 不从前端缓存、静态演示状态、Mock 任务或占位内容迁移完成状态。

### 11.2 初始化顺序

1. 建表和索引。
2. 为每个活跃 ContentProject 创建八条检查点。
3. 按阶段顺序重建证据和输入快照。
4. 无法重建时保守标记，不阻断数据库迁移。
5. 输出迁移统计：项目数、完成、进行中、过期和异常数量。

## 12. 灰度、兼容与回滚

- 现有内容单元、版本、上传、生成和审核接口继续保留。
- 新增 `scriptStageTruthEnabled` 前后端一致功能开关。
- 开关关闭时：完成判定走前端 `transitionGuard` + `ProjectWorkflowService` 旧轨推断，检查点表只写不读。
- 开关开启后：阶段完成只读取检查点；旧 `last_stage_key` 继续用于恢复位置，不参与完成判定。
- 灰度期间记录旧推断结果与新检查点投影差异到 `stage_truth_diff_log`，但不让旧结果覆盖新事实。
- 列表页 workflow 标签在 R2-A.3 结束后改读检查点投影；此前仍读 `ProjectWorkflowService`。
- 回滚时关闭新编排入口并恢复旧页面读取，不删除检查点或新内容版本。
- 数据库 undo 只允许在尚未产生生产检查点数据的受控环境使用；生产回滚不删除业务记录。

## 13. 错误码

| 错误码 | HTTP | 说明 |
|---|---:|---|
| `STAGE_NOT_FOUND` | 404 | 项目阶段不存在 |
| `STAGE_GATE_BLOCKED` | 422 | 门禁存在阻断项 |
| `STAGE_TRANSITION_INVALID` | 409 | 来源、目标或状态不允许流转 |
| `STAGE_REVISION_CONFLICT` | 409 | 检查点 revision 已变化 |
| `EDIT_CONFLICT` | 409 | 内容单元 revision 已变化 |
| `ARTIFACT_NOT_PERSISTED` | 422 | 阶段成果尚未可靠保存 |
| `ADOPTED_VERSION_IMMUTABLE` | 409 | 尝试覆盖采用或锁定版本 |
| `AI_CANDIDATE_STALE` | 409 | AI 候选的基准内容已经变化 |
| `IDEMPOTENCY_PAYLOAD_CONFLICT` | 409 | 相同幂等键对应不同请求 |
| `STALENESS_REASON_MISMATCH` | 409 | 尝试清除已被其他变化叠加的过期原因 |

## 14. 测试策略

### 14.1 后端

- 阶段状态机合法与非法转换测试。
- 八个 StageGateEvaluator 的阻断、警告和通过测试。
- 阶段完成事务回滚测试。
- 检查点、项目和内容 revision 并发冲突测试。
- 相同幂等键重复提交与 payload 冲突测试。
- Fork、放弃 Fork 和多来源过期原因叠加测试。
- AI 候选生成、部分采用、过期和撤销测试。
- 跨项目、跨工作区访问隔离测试。
- 历史迁移保守判断和重复迁移测试。

### 14.2 前端

- 检查点到阶段导航状态的纯函数测试。
- 串行自动保存和乱序响应保护测试。
- 保存失败常驻提示和离开页面保护测试。
- revision 冲突三方差异操作测试。
- 已完成阶段首次编辑自动 Fork 测试。
- AI before/after、部分采用、候选过期和撤销测试。
- URL 越级访问阻断测试。

### 14.3 黄金路径

1. 新建专业创作项目并完成八阶段。
2. 每个阶段刷新页面并重新登录，状态与成果保持一致。
3. 在两个浏览器同时编辑正文，后提交者收到冲突且本地正文不丢失。
4. 修改已完成改编方案，下游保留并标记 POSSIBLY_STALE。
5. 采用结构变化后的方案，相关下游升级为 REGEN_REQUIRED。
6. AI 改写成功后查看差异并部分采用；失败、取消和过期均不改变正文。
7. 审核通过形成不可变版本，再编辑自动 Fork。
8. 完成文字分镜并生成引用审核通过正文版本的 R2-B 交接快照。
9. 重复提交阶段确认，不产生重复版本或事件。
10. 核心测试在干净数据库连续运行三次通过。
11. 旧轨项目（`story_seed`/`outline`/`destination` 等）迁移后，检查点状态与原推断结果一致或更保守。
12. 多来源 Fork 叠加时，`DISCARD_FORK` 只清除指定 fork 的过期原因，其它来源保留。

## 15. 验收指标

| 指标 | 门槛 |
|---|---:|
| 阶段保存与刷新恢复一致率 | 100% |
| revision 冲突识别率 | 100% |
| 阶段失败后错误推进次数 | 0 |
| 锁定版本原地覆盖次数 | 0 |
| AI 失败或过期修改正文次数 | 0 |
| 重复确认产生重复版本次数 | 0 |
| 上游变化后下游成果丢失次数 | 0 |
| 下游过期原因可追溯率 | 100% |
| R2-B 交接快照版本引用正确率 | 100% |

## 16. 交付完成定义

R2-A 只有在以下条件全部满足时完成：

1. 八阶段完成状态来自服务端检查点，不来自前端内存或恢复位置推断。
2. 阶段确认、成果采用、输入快照、恢复位置和事件能够原子收敛。
3. 保存失败、revision 冲突和门禁阻断均不会推进阶段。
4. 已完成或锁定成果重新编辑时自动 Fork，历史版本保持不可变。
5. 上游变化能够保留下游成果并准确表达过期原因。
6. 局部 AI 改写始终经过真实任务、候选、差异和用户采用。
7. 文字分镜产生可供 R2-B 使用的可靠交接快照。
8. 剧本交易继续隐藏，专业分镜、画布和多轨剪辑未被带入本期。
9. 后端、前端和黄金路径测试通过，核心套件连续三次稳定。
