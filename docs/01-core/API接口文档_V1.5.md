# AI漫剧与视频内容工业化生产工作台 · API接口文档

> 基于《用户端产品功能设计.md》v0.6+《后端产品功能设计_V1.5.md》+《流程图文档.md》+《AI漫剧与视频内容工业化生产工作台 PRD V1.5》  
> 文档版本：v1.5  
> API版本：v1  
> 接口总数：260+ 个  
> 文档格式：OpenAPI 3.0 风格 Markdown  
> **V1.5 更新**：以 AI 视频工业化生产工作台为口径，补强画布节点、素材拖入、分镜解析、批量生图/视频、全能参考、多副本并行、资产历史、时间线合成、算力预估、Agent 会话与 Skill 执行接口。详见 Section 10 画布章节、Section 11 Agent章节及本节 V1.5 接口补强。

---

## 目录

- [1. API概述](#1-api概述)
- [2. 通用规范](#2-通用规范)
- [3. 认证接口（auth）](#3-认证接口auth)
- [4. 用户与账户接口（user）](#4-用户与账户接口user)
- [5. 企业管理接口（enterprise）](#5-企业管理接口enterprise)
- [6. 剧本生成接口（script-gen）](#6-剧本生成接口script-gen)
- [7. 剧本仓库接口（script-repo）](#7-剧本仓库接口script-repo)
- [8. 交易与支付接口（trade）](#8-交易与支付接口trade)
- [9. AI资产市场接口（asset-market）](#9-ai资产市场接口asset-market)
- [10. 画布视频工作台接口（canvas）](#10-画布视频工作台接口canvas)
- [🆕 11. Agent与Skill接口（agent）](#-11-agent与skill接口agent)
- [12. 生产SOP接口（sop）](#12-生产sop接口sop)
- [13. 通知消息接口（notify）](#13-通知消息接口notify)
- [14. 支付回调接口（webhook）](#14-支付回调接口webhook)
- [15. Open API接口（openapi）](#15-open-api接口openapi)
- [16. 通用数据模型](#16-通用数据模型)
- [17. 枚举字典](#17-枚举字典)
- [18. 错误码参考](#18-错误码参考)
- [附录A：接口版本矩阵](#附录a接口版本矩阵)

---

## 1. API概述

### 1.1 基础信息

| 项目 | 值 |
|------|-----|
| **Base URL (生产)** | `https://api.ai-comic-platform.com` |
| **Base URL (测试)** | `https://api-staging.ai-comic-platform.com` |
| **Base URL (开发)** | `http://localhost:8080` |
| **API版本** | `v1`（URL路径版本：`/api/v1/`） |
| **字符编码** | UTF-8 |
| **请求格式** | `application/json` |
| **响应格式** | `application/json` |
| **认证方式** | JWT Bearer Token（`Authorization: Bearer <token>`） |
| **时间格式** | ISO 8601（`2026-06-08T15:30:00+08:00`） |
| **日期格式** | `YYYY-MM-DD`（`2026-06-08`） |

### 1.2 服务路由

| 路由前缀 | 目标服务 | 认证 |
|----------|---------|:---:|
| `/api/v1/auth/*` | user-svc | 无 |
| `/api/v1/user/*` | user-svc | JWT |
| `/api/v1/enterprise/*` | user-svc | JWT + 企业角色 |
| `/api/v1/script/gen/*` | script-gen-svc | JWT |
| `/api/v1/script/repo/*` | script-repo-svc | JWT |
| `/api/v1/trade/*` | trade-svc | JWT |
| `/api/v1/asset/*` | asset-market-svc | JWT |
| `/api/v1/canvas/*` | canvas-svc | JWT |
| `/api/v1/storyboards/*` | canvas-svc / storyboard-svc | JWT |
| `/api/v1/generation/*` | generation-svc | JWT |
| `/api/v1/credits/*` | billing-svc | JWT |
| `/api/v1/agent/*` | agent-svc | JWT |
| `/api/v1/skills/*` | agent-svc | JWT |
| `/api/v1/sop/*` | sop-svc | JWT |
| `/api/v1/notify/*` | notify-svc | JWT |
| `/api/v1/callback/*` | trade-svc | 签名验证 |
| `/openapi/v1/*` | Open API BFF | API Key + HMAC签名 |

---

### 1.3 V1.5 工业化生产接口补强

V1.5 接口设计以“项目生产闭环”为主线，所有生成结果必须可追踪、可复用、可计费、可回写画布。

| 能力域 | 接口 | 方法 | 说明 |
|---|---|---|---|
| 画布节点 | `/api/v1/canvas/projects/{projectId}/nodes` | POST | 创建节点，支持双击坐标、左侧栏、右键、Agent 自动创建 |
| 画布节点 | `/api/v1/canvas/projects/{projectId}/nodes/{nodeId}` | PUT/PATCH | 更新节点位置、尺寸、名称、输入参数、输出结果、状态 |
| 画布节点 | `/api/v1/canvas/projects/{projectId}/nodes/positions` | PATCH | 批量更新节点坐标，支撑拖拽、框选、自动排版 |
| 画布连线 | `/api/v1/canvas/projects/{projectId}/nodes/connect` | POST | 创建节点连线，表达上下游依赖；后续可兼容 `/connections` 别名 |
| 画布分组 | `/api/v1/canvas/projects/{projectId}/groups` | POST | 创建节点组，用于整组执行和保存工作流模板 |
| 素材拖入 | `/api/v1/canvas/projects/{projectId}/assets/drop` | POST | 上传或引用本地素材，自动识别类型并创建节点 |
| 分镜 | `/api/v1/storyboards/{storyboardId}/parse` | POST | AI 拆分场次、镜头、台词、旁白、角色、场景、道具 |
| 分镜 | `/api/v1/storyboards/{storyboardId}/shots/{shotId}` | PATCH | 更新分镜行字段、Prompt、资产引用和状态 |
| 分镜生成 | `/api/v1/storyboards/{storyboardId}/generate-images` | POST | 批量分镜生图，结果回写分镜行和图片节点 |
| 视频生成 | `/api/v1/storyboards/{storyboardId}/generate-videos` | POST | 批量图生视频，结果回写分镜行和视频节点 |
| 全能参考 | `/api/v1/generation/video/reference` | POST | 多图、多视频、音频、文本共同参考生成视频 |
| 多副本 | `/api/v1/generation/variants` | POST | 创建 2 / 4 / 8 个候选版本并行生成 |
| 任务 | `/api/v1/generation/tasks/{taskId}` | GET | 查询任务进度、成本、错误、输出资产 |
| 任务 | `/api/v1/generation/tasks/{taskId}/cancel` | POST | 取消任务 |
| 任务 | `/api/v1/generation/tasks/{taskId}/retry` | POST | 重试任务 |
| 算力 | `/api/v1/credits/estimate` | POST | 预估算力消耗，Agent 执行前必须调用 |
| 资产 | `/api/v1/assets/history` | GET | 查询历史生成和手动资产 |
| 资产 | `/api/v1/assets/{assetId}/send-to-canvas` | POST | 将资产拖回画布并创建节点 |
| 时间线 | `/api/v1/canvas/projects/{projectId}/timeline/full` | GET/PUT | 获取或保存完整多轨时间线 |
| 导出 | `/api/v1/canvas/projects/{projectId}/export` | POST | 创建导出任务 |
| Agent | `/api/v1/agent/sessions` | POST | 创建 Agent 会话 |
| Agent | `/api/v1/agent/sessions/{sessionId}/messages` | POST | 发送自然语言任务，生成执行计划 |
| Skill | `/api/v1/skills/{skillId}/execute` | POST | 执行 Skill，结果回写画布和资产库 |

### 1.4 V1.5 任务状态要求

所有生图、生视频、TTS、BGM、音效、合成、导出、Agent/Skill 执行都必须进入统一任务模型。

| 字段 | 类型 | 说明 |
|---|---|---|
| `task_id` | string | 任务 ID |
| `project_id` | string | 项目 ID |
| `node_id` | string | 关联节点，可为空 |
| `shot_id` | string | 关联分镜，可为空 |
| `type` | string | image / video / audio / compose / export / agent / skill |
| `provider` | string | 服务商 |
| `model_id` | string | 模型 ID |
| `parameters` | object | 输入参数 |
| `status` | string | pending / running / succeeded / failed / canceled |
| `progress` | int | 进度 0-100 |
| `credit_cost` | int | 消耗积分 |
| `error_code` | string | 错误码 |
| `error_message` | string | 错误信息 |
| `output_assets` | array | 输出资产列表 |

### 1.5 V1.5 开发/测试可执行接口口径

本节作为前端、后端和测试的共同基线。若后续 OpenAPI 文件或代码实现与本节不一致，必须同步修订文档，不能只在代码里“约定俗成”。

#### 1.5.1 画布节点与连线基础接口

| 场景 | 方法 | 路径 | 前端触发 | 后端要求 | 测试重点 |
|---|---|---|---|---|---|
| 加载节点 | GET | `/api/v1/canvas/projects/{projectId}/nodes` | 进入画布、刷新、复制副本后重载 | 返回节点、端口、状态、输入输出、资产引用 | 刷新后节点和结果不丢 |
| 创建节点 | POST | `/api/v1/canvas/projects/{projectId}/nodes` | 双击空白画布、左侧栏添加、拖入素材、端口拉出下游 | 保存世界坐标、类型、尺寸、默认输入参数 | 坐标准确、自动选中、右侧面板联动 |
| 更新节点 | PUT/PATCH | `/api/v1/canvas/projects/{projectId}/nodes/{nodeId}` | 拖拽、改名、改参数、状态回写 | 只更新传入字段，保留已有输出和资产引用 | 部分字段更新不覆盖其他字段 |
| 删除节点 | DELETE | `/api/v1/canvas/projects/{projectId}/nodes/{nodeId}` | Delete 键、右键删除 | 删除节点并清理相关连线，必要时保留资产历史 | 删除后画布无孤儿连线 |
| 复制副本 | POST | `/api/v1/canvas/projects/{projectId}/nodes/{nodeId}/duplicate` | 右键“创建副本”、多副本抽卡 | 复制节点输入、输出和同源连线，生成新节点 ID | 副本位置偏移、连线关系正确 |
| 批量坐标 | PATCH | `/api/v1/canvas/projects/{projectId}/nodes/positions` | 多选拖拽、自动排版 | 批量保存坐标，失败时返回明细 | 大量节点拖拽后刷新坐标正确 |
| 创建连线 | POST | `/api/v1/canvas/projects/{projectId}/nodes/connect` | 从端口拖到端口或拖到空白新建下游 | 校验端口类型、节点归属、重复连线 | 不兼容端口不能连接 |
| 删除连线 | DELETE | `/api/v1/canvas/projects/{projectId}/connections/{connectionId}` | 选中连线删除、删除节点联动 | 删除指定连线，不影响节点本体 | 删除后拓扑和执行顺序更新 |
| 创建分组 | POST | `/api/v1/canvas/projects/{projectId}/groups` | 框选后打组、保存工作流模板 | 保存组范围、成员节点、组名称 | 解组、移动组、刷新后保持 |
| 应用预设动作 | POST | `/api/v1/canvas/projects/{projectId}/nodes/{nodeId}/preset-actions` | 点击文本节点“自己编写内容/文生视频/图片反推提示词/文字生音乐” | 切换编辑态或自动创建预设组、节点和连线 | 不执行 AI 任务，不直接扣积分 |

#### 1.5.2 节点生成动作统一接口流程

所有会消耗积分的节点动作必须执行同一流程：

```text
前端收集节点参数
→ POST /api/v1/credits/estimate
→ 用户确认积分消耗
→ 调用画布/分镜/生成接口创建任务
→ 返回 generation_task
→ 前端展示 pending/running
→ 后端执行 AI Router -> new-api
→ 成功后回写节点/分镜/资产库
→ 前端轮询或订阅任务状态并刷新结果
```

积分预估请求示例：

```json
{
  "project_id": "proj-uuid",
  "node_id": "node-uuid",
  "task_type": "image",
  "sub_type": "storyboard_image",
  "model_id": "gpt-image-1",
  "count": 4,
  "parameters": {
    "resolution": "1024x1024",
    "quality": "standard"
  }
}
```

任务创建成功后的统一返回字段：

```json
{
  "task_id": "task-uuid",
  "project_id": "proj-uuid",
  "node_id": "node-uuid",
  "shot_id": "shot-uuid",
  "type": "image",
  "sub_type": "storyboard_image",
  "status": "pending",
  "progress": 0,
  "provider": "new-api",
  "model_id": "gpt-image-1",
  "credit_cost": 40,
  "result_location": {
    "node_output": true,
    "asset_library": true,
    "storyboard_shot": true,
    "timeline": false
  }
}
```

#### 1.5.3 节点按钮与结果位置约定

| 节点 | 前端按钮/动作 | 扣费口径 | 后端任务 | 结果显示位置 |
|---|---|---|---|---|
| 脚本节点 | “AI拆分分镜” | 按 LLM Token 或镜头数预估 | `storyboard_parse` | 脚本全屏表格、分镜行、右侧任务日志 |
| 图片节点 | “生成图片”“重新生成”“生成多副本” | 按模型、尺寸、张数、多副本数预估 | `image` / `image_variants` | 图片节点预览、资产库、历史生成 |
| 视频节点 | “图生视频”“首尾帧生成”“全能参考生成” | 按模型、时长、分辨率、参考素材数预估 | `video` / `video_reference` | 视频节点播放器、资产库、时间线候选 |
| 音频节点 | “生成配音”“生成BGM”“生成音效” | 按字数、时长或模型单价预估 | `audio` | 音频节点波形、资产库、时间线音轨 |
| 合成节点 | “合成预览”“导出成片” | 按时长、分辨率、轨道数预估 | `compose` / `export` | 合成节点、导出中心、下载地址 |
| Agent/Skill | “执行计划”“运行Skill” | 先预估，执行前二次确认 | `agent` / `skill` | Agent会话、画布新增/更新节点、资产库 |

#### 1.5.4 LibTV式节点显示与生成器字段

节点接口必须返回足够的 UI 状态，支撑刷新后恢复“标题外置 + 卡片空态 + 尝试动作 + 底部生成器”的体验。后端只保存展示状态和参数，不绑定 DOM / SVG / Canvas / WebGL 渲染实现。

节点响应建议包含：

```json
{
  "id": "node_img_003",
  "type": "image",
  "label": "图片节点 3",
  "x": 1200,
  "y": 360,
  "width": 420,
  "height": 360,
  "status": "ready",
  "style_config": {
    "theme": "dark",
    "card_variant": "media_empty",
    "title_position": "outside_top",
    "icon": "image",
    "handles": ["left", "right"],
    "empty_actions": [
      { "key": "image_to_image", "label": "图生图", "icon": "upload" },
      { "key": "image_upscale", "label": "图片高清", "icon": "hd" }
    ]
  },
  "input_data": {
    "prompt": "",
    "generator": {
      "panel": "image",
      "model_id": "lib-image",
      "aspect_ratio": "auto",
      "quality": "standard",
      "resolution": "2K",
      "camera": "default",
      "panorama": false,
      "count": 1
    }
  },
  "output_data": {
    "main_asset_id": null,
    "candidate_asset_ids": []
  }
}
```

各节点的 `style_config.empty_actions` 与 `input_data.generator` 最低要求：

| 节点类型 | `empty_actions` | `generator.panel` | 必填生成器字段 |
|---|---|---|---|
| `text` | `manual_write`、`text_to_video`、`image_to_prompt`、`text_to_music` | `text` | `model_id`、`prompt`、`target_type` |
| `image` | `image_to_image`、`image_upscale` | `image` | `model_id`、`prompt`、`aspect_ratio`、`quality`、`resolution`、`count` |
| `video` | `start_end_frame_video`、`first_frame_video` | `video` | `mode`、`model_id`、`prompt`、`aspect_ratio`、`resolution`、`duration`、`motion`、`count` |
| `script` | `script_to_storyboard`、`character_to_storyboard` | `script` | `model_id`、`plot_prompt`、`parse_mode`、`target_shot_count` |

前端提交生成任务时，必须把当前生成器参数写入任务 `parameters`，并把用户点击的尝试动作写入 `sub_type`，便于产品解释、计费和测试断言。

#### 1.5.5 文本节点尝试动作接口

文本节点的四个空态动作不都等价于“生成任务”。其中“自己编写内容”是节点编辑态切换，不扣积分；“文生视频”“图片反推提示词”“文字生音乐”会自动创建预设组、节点和连线，但建组本身不扣积分，只有执行组或下游节点时才扣积分。

```
POST /api/v1/canvas/projects/{projectId}/nodes/{nodeId}/preset-actions
```

请求体：

```json
{
  "action_key": "text_to_video",
  "position": { "x": 1200, "y": 360 },
  "seed_content": "高级广告镜头，黑色背景中一款高端腕表悬浮出现...",
  "source_asset_id": "asset_img_001"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `action_key` | string | 是 | `manual_write` / `text_to_video` / `image_to_prompt` / `text_to_music`；前端可把“文本生音乐”作为“文字生音乐”的同义文案 |
| `position` | object | 否 | 自动成组时的组左上角世界坐标；不传则以当前节点附近自动排布 |
| `seed_content` | string | 否 | 文生视频、文字生音乐的初始文本；不传则使用当前文本节点内容 |
| `source_asset_id` | string | 否 | 图片反推提示词的初始图片资产；不传则创建空图片节点等待上传 |

响应示例：`manual_write`

```json
{
  "mode": "single_node",
  "node": {
    "id": "node_text_009",
    "type": "text",
    "label": "文本节点 9",
    "style_config": {
      "card_variant": "text_editor",
      "editor_toolbar": ["clear_style", "h1", "h2", "h3", "paragraph", "bold", "italic", "bullet_list", "ordered_list", "divider", "duplicate", "fullscreen"]
    },
    "input_data": {
      "editor": {
        "placeholder": "输入内容...",
        "format": "rich_text"
      }
    }
  }
}
```

响应示例：自动预设组

```json
{
  "mode": "preset_group",
  "group": {
    "id": "group_text_to_video_001",
    "label": "预设 - 文生视频",
    "preset_key": "text_to_video",
    "x": 900,
    "y": 240,
    "width": 1120,
    "height": 620,
    "toolbar_actions": ["group_color", "layout", "run_group", "add_to_toolbox", "convert_to_storyboard_group", "ungroup", "batch_download"],
    "disabled_actions": ["convert_to_storyboard_group"]
  },
  "nodes": [
    { "id": "node_text_010", "type": "text", "label": "文本节点 10" },
    { "id": "node_video_001", "type": "video", "label": "视频" }
  ],
  "edges": [
    { "source_node_id": "node_text_010", "source_port": "output", "target_node_id": "node_video_001", "target_port": "prompt" }
  ],
  "run_policy": {
    "auto_execute": false,
    "execute_entry": "run_group",
    "requires_credit_estimate": true
  }
}
```

预设动作创建规则：

| `action_key` | 展示结果 | 自动节点 | 自动连线 | 默认组名 |
|---|---|---|---|---|
| `manual_write` | 文本节点富文本编辑态 | 无新增 | 无新增 | 无 |
| `text_to_video` | 文本到视频预设组 | 文本节点、视频节点 | `text.output -> video.prompt` | `预设 - 文生视频` |
| `image_to_prompt` | 图片反推提示词预设组 | 图片节点、文本节点 | `image.asset -> text.prompt` | `预设 - 图片反推提示词` |
| `text_to_music` | 文字生音乐预设组 | 文本节点、音频节点 | `text.output -> audio.prompt` | `预设 - 文字生音乐` |

#### 1.5.6 LibTV PDF 融合增强接口矩阵

以下接口用于承接《LibTV使用指南.pdf》中确认的生产级功能。接口可按阶段实现，但路径、任务类型和结果回写位置需要保持稳定。

| 能力 | 方法 | 路径 | 说明 | 任务/结果 |
|---|---|---|---|---|
| 脚本三步门禁 | PATCH | `/api/v1/canvas/projects/{projectId}/script-nodes/{nodeId}/workflow-state` | 更新确认镜头、整理资产、合成提示词状态 | 回写 `input_data.workflow_state` |
| shot 行编辑 | PATCH | `/api/v1/canvas/projects/{projectId}/script-nodes/{nodeId}/shots/{shotId}` | 编辑、排序、新增、删除、颜色标记 shot | 回写 `storyboard_shots` |
| 资产卡片抽屉 | POST | `/api/v1/canvas/projects/{projectId}/script-nodes/{nodeId}/asset-cards` | 创建/上传/从画布选择角色、场景、道具资产卡 | 回写资产引用和自动连线 |
| 合成最终提示词 | POST | `/api/v1/canvas/projects/{projectId}/script-nodes/{nodeId}/compose-prompts` | 基于 shot、资产和风格合成最终提示词 | `storyboard_prompt_compose` |
| 生成器组 | POST | `/api/v1/canvas/projects/{projectId}/generator-groups` | 从脚本勾选行创建分镜图/视频生成器组 | 创建 group、nodes、edges |
| 分镜组 | POST | `/api/v1/canvas/projects/{projectId}/storyboard-groups` | 多图合并分镜组或普通组转分镜组 | 创建/更新 group |
| 分镜组布局 | PATCH | `/api/v1/canvas/projects/{projectId}/storyboard-groups/{groupId}` | 比例、行列、序号、转普通组、清空、解组 | 回写 group 布局 |
| 分镜组拼接 | POST | `/api/v1/canvas/projects/{projectId}/storyboard-groups/{groupId}/stitch` | 拼接 2K/4K 大图 | `image_stitch`，结果创建图片节点 |
| 图像全景 | POST | `/api/v1/canvas/projects/{projectId}/image-nodes/{nodeId}/panorama` | 文本/参考图生成 720 全景 | `image_panorama` |
| 全景截图 | POST | `/api/v1/canvas/projects/{projectId}/image-nodes/{nodeId}/panorama/screenshots` | 4/12 视角截图 | 创建图片节点和资产 |
| 多角度 | POST | `/api/v1/canvas/projects/{projectId}/image-nodes/{nodeId}/multi-angle` | 水平/垂直/景别多角度生成 | `image_multi_angle` |
| 打光 | POST | `/api/v1/canvas/projects/{projectId}/image-nodes/{nodeId}/lighting` | 光源角度、颜色、亮度、轮廓光 | `image_lighting` |
| 焦点编辑 | POST | `/api/v1/canvas/projects/{projectId}/image-nodes/{nodeId}/focus-edit` | 从画布图片提取元素组合生成 | `image_focus_edit` |
| 镜头聚焦 | POST | `/api/v1/canvas/projects/{projectId}/image-nodes/{nodeId}/shot-focus` | 框选细节生成特写分镜 | `image_shot_focus` |
| 摄像机控制 | PATCH | `/api/v1/canvas/projects/{projectId}/image-nodes/{nodeId}/camera-control` | 保存相机、镜头、焦距、光圈 | 写入 `input_data.generator.camera_control` |
| 视频高清 | POST | `/api/v1/canvas/projects/{projectId}/video-nodes/{nodeId}/upscale` | 放大 2/4/6 倍、帧率 30/60/90fps | `video_upscale` |
| 视频解析 | POST | `/api/v1/canvas/projects/{projectId}/video-nodes/{nodeId}/parse` | 分镜拆解为表格 | `video_parse`，可创建脚本节点 |
| 视频剪辑 | POST | `/api/v1/canvas/projects/{projectId}/video-nodes/{nodeId}/trim` | 入点/出点裁剪 | `video_trim` |
| 人声/背景声分离 | POST | `/api/v1/canvas/projects/{projectId}/video-nodes/{nodeId}/separate-audio` | 分离人声或背景声 | `audio_separate`，创建音频节点 |
| 视频合成 | POST | `/api/v1/canvas/projects/{projectId}/compose-nodes` | 视频/音频连线创建合成节点 | 创建 compose 节点 |
| 导演台 | POST/PUT | `/api/v1/canvas/projects/{projectId}/director-desk/{nodeId}` | 保存 3D 元素、摄像机、截图、全景 | 回写导演台节点 |
| 主体库 | POST | `/api/v1/subjects` | 从图片/视频创建主体，支持智能补全、音色绑定 | 创建 subject asset |
| 合规校验 | POST | `/api/v1/compliance/assets/verify` | 真人/素材合规校验 | 回写 `compliance_status` |
| 运镜预设 | GET/POST | `/api/v1/motion-presets` | 预设、收藏、自定义运镜 | 写入视频任务参数 |
| 音色克隆 | POST | `/api/v1/voice-clones` | 上传样本、生成音色、试听校验 | 创建 voice asset |

#### 1.5.7 画布状态机、事件同步与测试断言接口

前端画布可以使用 DOM/SVG、Canvas、WebGL 或混合渲染，但接口必须只表达业务状态，不暴露具体渲染实现。测试断言以接口字段为准。

**节点状态字段**

所有节点查询、创建、更新、任务回写接口都需要返回以下最低字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `status` | string | `unconfigured` / `ready` / `queued` / `generating` / `success` / `failed` / `cancelled` / `locked` |
| `ui_flags.is_stale` | boolean | 上游节点变更后，下游结果是否已过期 |
| `ui_flags.can_execute` | boolean | 当前节点参数是否满足执行条件 |
| `ui_flags.can_retry` | boolean | 最近一次失败任务是否允许重试 |
| `last_task_id` | string | 最近一次关联任务 ID |
| `last_error_code` | string | 最近一次失败错误码 |
| `last_error_message` | string | 面向用户可读的失败说明 |
| `output_revision` | int | 当前输出版本号，上游变更和下游过期判断使用 |
| `input_revision` | int | 当前输入版本号，参数变更时递增 |

节点状态流转必须遵守以下规则：

| 触发动作 | 允许前置状态 | 后置状态 | 说明 |
|---|---|---|---|
| 创建空节点 | 无 | `unconfigured` | 只创建节点，不扣积分 |
| 参数补齐 | `unconfigured` / `failed` / `success` | `ready` | 不创建任务 |
| 确认执行 | `ready` / `failed` / `success` | `queued` | 必须先完成积分预估和用户确认 |
| Worker 开始 | `queued` | `generating` | 写入 `last_task_id` 和开始时间 |
| 任务成功 | `generating` | `success` | 回写 `output_data`、资产和历史记录 |
| 任务失败 | `queued` / `generating` | `failed` | 写入错误码、错误信息和退费状态 |
| 用户取消 | `queued` / `generating` | `cancelled` | 取消后可重试 |
| 上游变更 | `success` | `success` + `ui_flags.is_stale=true` | 不自动重新扣费 |

**积分预估接口补充字段**

`POST /api/v1/credits/estimate` 响应需要支持前端提交按钮、确认弹窗和测试断言：

```json
{
  "estimate_id": "est_001",
  "estimated_cost": 120,
  "balance": 860,
  "can_execute": true,
  "blocking_reason": null,
  "result_location": {
    "node_id": "node_video_001",
    "node_output": true,
    "asset_library": true,
    "history": true,
    "timeline": false
  },
  "expires_at": 1717843800
}
```

执行类接口必须携带 `estimate_id`。后端需要校验预估是否过期、参数是否变化、余额是否仍足够；不通过时返回 `409 ESTIMATE_EXPIRED` 或 `402 INSUFFICIENT_CREDITS`。

**任务事件同步**

P0 可以使用轮询 `GET /api/v1/generation/tasks/{taskId}`；P1 可升级为项目级事件流。若实现事件流，路径固定为：

```
GET /api/v1/canvas/projects/{projectId}/events?cursor={cursor}
```

事件格式：

```json
{
  "event_id": "evt_001",
  "event_type": "task.completed",
  "project_id": "proj_001",
  "node_id": "node_img_003",
  "task_id": "task_001",
  "node_patch": {
    "status": "success",
    "output_data": {
      "main_asset_id": "asset_001",
      "candidate_asset_ids": ["asset_001"]
    },
    "ui_flags": {
      "is_stale": false,
      "can_retry": false
    }
  },
  "asset_patch": {
    "created_asset_ids": ["asset_001"]
  },
  "credit_patch": {
    "estimated_cost": 120,
    "actual_cost": 116,
    "refund": 4
  },
  "cursor": "next_cursor"
}
```

事件类型最低要求：

| `event_type` | 触发时机 | 前端动作 |
|---|---|---|
| `node.updated` | 节点坐标、参数、标题、分组变化 | 局部刷新节点和连线 |
| `edge.updated` | 连线创建、删除、端口变更 | 重算拓扑和下游过期状态 |
| `task.queued` | 任务创建并冻结积分 | 节点进入排队态 |
| `task.progress` | Worker 上报进度 | 更新进度条和耗时 |
| `task.completed` | 任务成功 | 回写结果、刷新资产库和历史记录 |
| `task.failed` | 任务失败 | 展示错误、退费状态和重试入口 |
| `credit.updated` | 预估、冻结、结算、退还 | 刷新余额和积分流水 |

**测试断言口径**

| 场景 | 必查接口/字段 |
|---|---|
| 刷新恢复 | `GET /nodes` 返回 `style_config`、`input_data`、`output_data`、`status`、`ui_flags` |
| 文本节点手写 | `preset-actions` 返回 `card_variant=text_editor`，不得创建 `generation_tasks` |
| 自动预设组 | `preset-actions` 返回 group、nodes、edges，`run_policy.requires_credit_estimate=true` |
| 余额不足 | `credits/estimate.can_execute=false`，提交按钮禁用且不创建任务 |
| 任务成功 | `generation/tasks/{taskId}` 或事件返回 `status=success`、资产 ID、实际扣费 |
| 任务失败 | 返回 `status=failed`、`last_error_code`、`refund` 或部分结算说明 |

---

## 2. 通用规范

### 2.1 统一响应格式

所有API返回统一JSON结构：

```json
{
  "code": 0,
  "message": "success",
  "data": { },
  "request_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": 1717843200
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | 业务错误码，`0` 表示成功 |
| `message` | string | 错误描述，成功时为 `"success"` |
| `data` | object/array/null | 响应数据，错误时为 `null` |
| `request_id` | string | 请求唯一标识（UUID），用于排查问题 |
| `timestamp` | int | 服务器响应时间戳（Unix秒） |

### 2.2 分页响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [ ],
    "pagination": {
      "page": 1,
      "page_size": 20,
      "total": 128,
      "total_pages": 7,
      "has_more": true
    }
  },
  "request_id": "...",
  "timestamp": 1717843200
}
```

**分页参数**：`?page=1&page_size=20`（page_size 范围 1-100，默认20）

### 2.3 请求头规范

| Header | 必需 | 说明 |
|--------|:---:|------|
| `Authorization` | 是* | `Bearer <jwt_token>` |
| `Content-Type` | 是 | `application/json` |
| `Accept` | 是 | `application/json` |
| `X-Request-ID` | 否 | 客户端生成的请求ID（推荐） |
| `X-Client-Version` | 否 | 客户端版本号 |
| `Accept-Language` | 否 | 语言偏好 `zh-CN` / `en`，默认 `zh-CN` |

> *认证接口（`/api/v1/auth/*`）和支付回调（`/api/v1/callback/*`）不需要 JWT Token。

### 2.4 JWT Token结构

```json
{
  "sub": "user_uuid",
  "uid": 12345,
  "type": "personal",
  "ent_id": null,
  "role": "creator_member",
  "permissions": ["can_generate_script", "can_purchase_script"],
  "iat": 1717843200,
  "exp": 1717929600
}
```

- **Access Token**：有效期 2小时
- **Refresh Token**：有效期 30天，存储在Redis

### 2.5 HTTP方法语义

| 方法 | 语义 | 幂等 |
|------|------|:---:|
| `GET` | 查询资源 | ✅ |
| `POST` | 创建资源 / 触发操作 | ❌ |
| `PUT` | 完整更新资源 | ✅ |
| `PATCH` | 部分更新资源（暂不使用） | ❌ |
| `DELETE` | 删除资源 | ✅ |

---

## 3. 认证接口（auth）

> 路由前缀：`/api/v1/auth` | 认证要求：无 | 限流：100次/分钟/IP

### 3.1 发送验证码

```
POST /api/v1/auth/send-code
```

**请求体**：
```json
{
  "target": "13800138000",
  "type": "sms",
  "scene": "register"
}
```

| 字段 | 类型 | 必需 | 说明 |
|------|------|:---:|------|
| `target` | string | ✅ | 手机号 或 邮箱地址 |
| `type` | string | ✅ | `sms` / `email` |
| `scene` | string | ✅ | `register` / `login` / `reset_password` / `bind` |

**成功响应** `200`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "expire_seconds": 300,
    "retry_after_seconds": 60
  }
}
```

**错误码**：`40001` 频率超限 | `40002` 发送失败 | `40003` 目标格式无效

---

### 3.2 用户注册

```
POST /api/v1/auth/register
```

**请求体**：
```json
{
  "account": "13800138000",
  "account_type": "phone",
  "password": "Abc@123456",
  "verify_code": "123456",
  "account_category": "personal",
  "nickname": "创作者小明"
}
```

| 字段 | 类型 | 必需 | 说明 |
|------|------|:---:|------|
| `account` | string | ✅ | 手机号 或 邮箱 |
| `account_type` | string | ✅ | `phone` / `email` |
| `password` | string | ✅ | 8-20位，含大小写字母+数字+特殊字符 |
| `verify_code` | string | ✅ | 6位验证码 |
| `account_category` | string | ✅ | `personal` / `enterprise` |
| `nickname` | string | ✅ | 2-20字符 |
| `invite_code` | string | ❌ | 邀请码 |

**成功响应** `201`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "user": {
      "uuid": "usr_a1b2c3d4e5f6",
      "nickname": "创作者小明",
      "account_type": "personal",
      "member_level": "free",
      "created_at": "2026-06-08T15:30:00+08:00"
    },
    "token": {
      "access_token": "eyJhbGciOi...",
      "refresh_token": "eyJhbGciOi...",
      "expires_in": 7200
    }
  }
}
```

**错误码**：`40004` 账号已存在 | `40005` 验证码错误/过期 | `40006` 密码格式不符 | `40007` 昵称已占用

---

### 3.3 账号密码登录

```
POST /api/v1/auth/login
```

**请求体**：
```json
{
  "account": "13800138000",
  "account_type": "phone",
  "password": "Abc@123456"
}
```

**成功响应** `200`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "user": {
      "uuid": "usr_a1b2c3d4e5f6",
      "nickname": "创作者小明",
      "account_type": "personal",
      "member_level": "free",
      "avatar_url": "https://cdn.example.com/avatars/default.png"
    },
    "token": {
      "access_token": "eyJhbGciOi...",
      "refresh_token": "eyJhbGciOi...",
      "expires_in": 7200
    }
  }
}
```

**错误码**：`40101` 账号或密码错误 | `40102` 账号已禁用 | `40103` 登录失败次数过多，请15分钟后重试

---

### 3.4 短信验证码登录

```
POST /api/v1/auth/login/sms
```

**请求体**：
```json
{
  "phone": "13800138000",
  "verify_code": "123456"
}
```

**成功响应** `200`：同 3.3

---

### 3.5 微信OAuth登录

```
POST /api/v1/auth/login/wechat
```

**请求体**：
```json
{
  "code": "021aBcDeFgHiJkLmNoPqRsTuVwXyZ",
  "state": "random_state_string"
}
```

**成功响应** `200`：同 3.3（新用户自动注册）

---

### 3.6 企业SSO登录 `V1.2`

```
POST /api/v1/auth/login/sso
```

**请求体**：
```json
{
  "enterprise_id": 100,
  "sso_token": "sso_token_from_idp"
}
```

**成功响应** `200`：同 3.3（含企业角色+权限）

---

### 3.7 刷新Token

```
POST /api/v1/auth/refresh-token
```

**请求体**：
```json
{
  "refresh_token": "eyJhbGciOi..."
}
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "access_token": "eyJhbGciOi...",
    "refresh_token": "eyJhbGciOi...",
    "expires_in": 7200
  }
}
```

---

### 3.8 登出

```
POST /api/v1/auth/logout
Authorization: Bearer <token>
```

**请求体**：无

**成功响应** `200`：Token被加入黑名单（Redis），Refresh Token失效。

---

## 4. 用户与账户接口（user）

> 路由前缀：`/api/v1/user` | 认证要求：JWT | 限流：1000次/分钟

### 4.1 获取个人信息

```
GET /api/v1/user/profile
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "uuid": "usr_a1b2c3d4e5f6",
    "nickname": "创作者小明",
    "avatar_url": "https://cdn.example.com/avatars/usr_001.png",
    "account_type": "personal",
    "phone": "138****8000",
    "email": "xiaoming@example.com",
    "wechat_bound": true,
    "real_name_status": "verified",
    "member_level": "creator",
    "member_expire_at": "2027-06-08T00:00:00+08:00",
    "status": "active",
    "stats": {
      "scripts_generated": 45,
      "videos_exported": 12,
      "scripts_in_repo": 8,
      "storage_used_mb": 256
    },
    "created_at": "2026-01-15T10:00:00+08:00",
    "last_login_at": "2026-06-08T09:30:00+08:00"
  }
}
```

---

### 4.2 更新个人信息

```
PUT /api/v1/user/profile
```

**请求体**：
```json
{
  "nickname": "创作者小明V2",
  "avatar_url": "https://cdn.example.com/avatars/usr_001_v2.png",
  "bio": "专注AI漫剧创作"
}
```

**成功响应** `200`：返回更新后的 `user` 对象。

---

### 4.3 实名认证

```
POST /api/v1/user/verify/real-name
```

**请求体**：
```json
{
  "real_name": "张三",
  "id_card_number": "110101199001011234",
  "id_card_front_url": "https://cdn.example.com/...",
  "id_card_back_url": "https://cdn.example.com/..."
}
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "real_name_status": "pending",
    "estimated_review_hours": 24
  }
}
```

---

### 4.4 获取会员状态 `V1.1`

```
GET /api/v1/user/membership
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "level": "creator",
    "expire_at": "2027-06-08T00:00:00+08:00",
    "auto_renew": true,
    "benefits": {
      "daily_gen_quota": -1,
      "repo_capacity": -1,
      "can_list_script": true,
      "export_no_watermark": true,
      "batch_generate": true,
      "max_resolution": "1080p",
      "export_queue_priority": "medium"
    },
    "usage_this_month": {
      "gen_count": 23,
      "export_count": 5,
      "storage_used_mb": 256
    }
  }
}
```

---

### 4.5 升级会员 `V1.1`

```
POST /api/v1/user/membership/upgrade
```

**请求体**：
```json
{
  "target_level": "creator",
  "plan": "yearly",
  "payment_method": "wechat"
}
```

| 字段 | 类型 | 必需 | 说明 |
|------|------|:---:|------|
| `target_level` | string | ✅ | `creator` / `enterprise` |
| `plan` | string | ✅ | `monthly` / `yearly` |
| `payment_method` | string | ✅ | `wechat` / `alipay` |

**成功响应** `200`：返回支付参数（调起支付客户端）。

---

### 4.6 API Key管理 `V1.2`

```
GET    /api/v1/user/api-keys           # 列表
POST   /api/v1/user/api-keys           # 创建
DELETE /api/v1/user/api-keys/:id        # 删除
```

**创建请求体**：
```json
{
  "name": "我的自动化脚本",
  "scopes": ["script:read", "canvas:write"],
  "ip_whitelist": ["1.2.3.4"]
}
```

**创建成功响应** `201`：
```json
{
  "code": 0,
  "data": {
    "id": 1,
    "name": "我的自动化脚本",
    "api_key": "ak-a1b2c3d4e5f6...",
    "scopes": ["script:read", "canvas:write"],
    "created_at": "2026-06-08T15:30:00+08:00"
  }
}
```

> ⚠️ `api_key` 仅在创建时返回一次，之后不可查看。

---

## 5. 企业管理接口（enterprise）

> 路由前缀：`/api/v1/enterprise` | 认证要求：JWT + 企业角色 | 限流：2000次/分钟

### 5.1 企业注册与认证 `V1.1`

```
POST /api/v1/enterprise/register
```

**请求体**：
```json
{
  "name": "XX文化传媒有限公司",
  "license_number": "91110108MA01XXXXXX",
  "license_image_url": "https://cdn.example.com/licenses/ent_001.png",
  "contact_name": "张三",
  "contact_phone": "13800138000",
  "member_plan": "10人版"
}
```

**成功响应** `201`：
```json
{
  "code": 0,
  "data": {
    "enterprise_id": 100,
    "name": "XX文化传媒有限公司",
    "verify_status": "pending",
    "estimated_review_hours": 72
  }
}
```

---

### 5.2 企业信息 CRUD `V1.1`

```
GET  /api/v1/enterprise/profile         # 获取企业信息
PUT  /api/v1/enterprise/profile         # 更新企业信息
```

---

### 5.3 成员管理 `V1.1`

```
GET    /api/v1/enterprise/members                  # 成员列表
POST   /api/v1/enterprise/members/invite            # 邀请成员
PUT    /api/v1/enterprise/members/:uid/role         # 设置角色权限
DELETE /api/v1/enterprise/members/:uid              # 移除成员
```

**邀请成员请求体**：
```json
{
  "targets": ["13800138001", "user@company.com"],
  "department": "内容一部",
  "role": "writer",
  "permissions": ["can_generate_script", "can_purchase_script"]
}
```

**设置角色权限请求体**：
```json
{
  "role": "dept_head",
  "permissions": [
    "can_generate_script", "can_purchase_script", "can_manage_assets",
    "can_approve_purchase", "can_view_analytics"
  ],
  "department": "内容一部",
  "purchase_budget_monthly": 5000,
  "purchase_budget_single": 500
}
```

---

### 5.4 企业仪表盘 `V1.1`

```
GET /api/v1/enterprise/dashboard
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "overview": {
      "member_count": 12,
      "scripts_generated_this_month": 45,
      "videos_exported_this_month": 28,
      "total_assets": 86
    },
    "financial": {
      "monthly_spending": 3280.00,
      "purchase_orders": 15,
      "pending_approvals": 3,
      "api_calls_this_month": 1280
    },
    "pending_items": [
      {
        "type": "purchase_request",
        "from_user": "张三",
        "script_title": "霸道总裁的替身新娘",
        "amount": 29.90,
        "created_at": "2026-06-08T10:00:00+08:00"
      }
    ],
    "recent_activity": [
      {
        "user": "王五",
        "action": "export_video",
        "target": "重生之商业帝国 第3集",
        "time": "2026-06-08T14:30:00+08:00"
      }
    ]
  }
}
```

---

## 6. 剧本生成接口（script-gen）

> 路由前缀：`/api/v1/script/gen` | 认证要求：JWT | 限流：50次/分钟（AI调用）

### 6.1 快速模式

```
POST /api/v1/script/gen/quick
```

> 快速模式返回的是“剧本资产初稿”，不是最终可生产稿。用户仍需在剧本资产工作台完成章节修订、故事圣经补全、分镜脚本Master确认后，才能锁稿送入画布。

**请求体**：
```json
{
  "idea": "一个外卖小哥其实是隐藏的豪门继承人",
  "tags": {
    "genre": "言情",
    "plot": ["重生", "先婚后爱"],
    "tone": ["甜宠", "打脸"],
    "setting": "现代"
  },
  "target_platform": "douyin",
  "episode_count": 40,
  "target_audience": "female",
  "reference_works": "类似于《霸道总裁xxx》的风格"
}
```

**成功响应** `200`（异步任务）：
```json
{
  "code": 0,
  "data": {
    "task_id": "gen_abc123",
    "status": "pending",
    "estimated_seconds": 120
  }
}
```

**任务完成后 `output_data` 建议结构**：
```json
{
  "title": "霸道总裁的替身新娘",
  "synopsis": "她是被家族抛弃的私生女...",
  "episodes": [
    {
      "episode_no": 1,
      "title": "命运的相遇",
      "content": "第1集正文...",
      "scenes": []
    }
  ],
  "story_bible": {
    "worldview": "现代豪门商业世界",
    "world_map": [],
    "characters": [],
    "missions": [],
    "rules": []
  },
  "outline_episodes": [],
  "storyboard_shots": [],
  "promotion_materials": {
    "titles": [],
    "hooks": [],
    "cover_copy": []
  }
}
```

---

### 6.2 分步生成（Step 1-6）

```
POST /api/v1/script/gen/topic          # Step1: 爆款选题
POST /api/v1/script/gen/synopsis       # Step2: 故事梗概
POST /api/v1/script/gen/outline        # Step3: 分集大纲
POST /api/v1/script/gen/episode        # Step4: 单集剧本
POST /api/v1/script/gen/storyboard     # Step5: 分镜脚本(A/B/C档)
POST /api/v1/script/gen/promotion      # Step6: 投流素材 (V1.2)
```

**Step1 请求示例**：
```json
{
  "idea": "一个外卖小哥其实是隐藏的豪门继承人",
  "tags": { "genre": "言情", "plot": ["重生"], "tone": ["甜宠"], "setting": "现代" },
  "target_platform": "douyin",
  "target_audience": "female"
}
```

**Step1 成功响应**：
```json
{
  "code": 0,
  "data": {
    "task_id": "gen_topic_xyz",
    "status": "pending"
  }
}
```

**Step5 (分镜) 请求体**：
```json
{
  "script_id": 12345,
  "source_version": "v1.0",
  "tier": "A",
  "target_duration_seconds": 180,
  "aspect_ratio": "9:16",
  "source": "script_master_version",
  "project_plugin_pack": {
    "characters": [
      { "character_id": "CH_LIN", "face_id": "FACE_LIN_V01", "costume_id": "CST_LIN_SUIT_V01" }
    ],
    "locations": [
      { "location_id": "LOC_OFFICE", "reference_url": "..." }
    ],
    "style_id": "STYLE_KMANGA"
  }
}
```

> Step2-6必须接收用户已修改后的上一阶段内容。例如用户修改了梗概或章节正文，后续大纲、分镜、投流都以修订版为准，不能仅以AI原始生成结果为准。

---

### 6.3 分镜升档 `V1.1`

```
POST /api/v1/script/gen/storyboard/upgrade
```

**请求体**：
```json
{
  "script_id": 12345,
  "from_tier": "A",
  "to_tier": "B",
  "focus_scenes": ["EP01_SC02", "EP01_SC05"]
}
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "task_id": "gen_upgrade_xyz",
    "from_tier": "A",
    "to_tier": "B",
    "status": "pending"
  }
}
```

---

### 6.4 查询任务状态

```
GET /api/v1/script/gen/task/:task_id
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "task_id": "gen_abc123",
    "gen_type": "quick",
    "status": "completed",
    "progress": 100,
    "result": {
      "script_id": 12345,
      "title": "霸道总裁的替身新娘",
      "synopsis": "她是被家族抛弃的私生女...",
      "episode_count": 40,
      "tags": {
        "genre": "言情",
        "plot": ["重生", "先婚后爱"],
        "tone": ["甜宠", "打脸"],
        "setting": "现代"
      }
    },
    "tokens_used": 45000,
    "duration_ms": 85000,
    "created_at": "2026-06-08T15:30:00+08:00",
    "completed_at": "2026-06-08T15:31:25+08:00"
  }
}
```

**任务状态枚举**：`pending` → `processing` → `completed` / `failed` / `cancelled`

---

### 6.5 生成历史

```
GET /api/v1/script/gen/tasks?page=1&page_size=20&gen_type=storyboard&status=completed
```

**查询参数**：`gen_type` | `status` | `date_from` | `date_to` | `page` | `page_size`

---

## 7. 剧本仓库接口（script-repo）

> 路由前缀：`/api/v1/script/repo` | 认证要求：JWT | 限流：500次/分钟

### 7.1 剧本CRUD

```
POST   /api/v1/script/repo/scripts             # 创建/保存
GET    /api/v1/script/repo/scripts              # 列表
GET    /api/v1/script/repo/scripts/:id          # 详情
PUT    /api/v1/script/repo/scripts/:id          # 更新
DELETE /api/v1/script/repo/scripts/:id          # 删除（软删除）
```

**创建/更新请求体**：
```json
{
  "project_id": "PROJ_DOMINEERING_PRESIDENT",
  "title": "霸道总裁的替身新娘",
  "synopsis": "她是被家族抛弃的私生女...",
  "episode_count": 40,
  "cover_image_url": "https://cdn.example.com/covers/script_001.png",
  "tags": {
    "genre": "言情",
    "plot": ["重生", "先婚后爱"],
    "tone": ["甜宠", "打脸"],
    "setting": "现代"
  },
  "content": {
    "episodes": [
      {
        "episode_id": "EP01",
        "episode_no": 1,
        "title": "命运的相遇",
        "content": "第1集正文...",
        "scenes": [
          {
            "scene_id": "EP01_SC01",
            "location": "总裁办公室",
            "time": "上午",
            "characters": ["林默", "苏小晚"],
            "content": "△ 苏小晚端着咖啡推门而入..."
          }
        ]
      }
    ]
  },
  "story_bible": {
    "worldview": "现代豪门商业世界",
    "world_map": [],
    "characters": [],
    "missions": [],
    "rules": []
  },
  "outline_episodes": [],
  "storyboard": {
    "version": "master",
    "tier": "A",
    "shots": []
  },
  "promotion_materials": {
    "titles": [],
    "hooks": [],
    "cover_copy": []
  },
  "sale_package": {
    "package_type": "production",
    "license_scope": "platform_internal",
    "price": 0
  }
}
```

> 创建/更新剧本必须保存完整剧本资产包，并生成或更新版本快照。版本快照至少包含章节、故事圣经、大纲、分镜Master、投流素材和标签，避免“保存仓库后只剩标题/梗概/标签”。

**列表查询参数**：
```
?page=1&page_size=20
&status=draft
&genre=言情
&plot=重生,先婚后爱
&tone=甜宠
&setting=现代
&sort=updated_at_desc
&keyword=总裁
```

---

### 7.2 标签管理

```
PUT /api/v1/script/repo/scripts/:id/tags
```

**请求体**：
```json
{
  "genre": "言情",
  "plot": ["重生", "先婚后爱", "打脸"],
  "tone": ["甜宠", "爽文"],
  "setting": "现代"
}
```

> 自动校验标签合法性（题材1个、情节≤3、情绪≤3、时空1个）

---

### 7.3 版本管理

```
GET  /api/v1/script/repo/scripts/:id/versions                    # 版本列表
POST /api/v1/script/repo/scripts/:id/versions                    # 创建新版本
POST /api/v1/script/repo/scripts/:id/versions/:vid/restore       # 还原版本
```

**创建版本请求体**：
```json
{
  "version": "v1.2",
  "change_summary": "第3集增加反转情节，调整第5集对白"
}
```

**版本详情**：
```json
{
  "id": 123,
  "script_id": 12345,
  "version": "v1.2",
  "change_summary": "第3集增加反转情节",
  "created_by": {
    "uuid": "usr_xxx",
    "nickname": "创作者小明"
  },
  "created_at": "2026-06-08T16:00:00+08:00"
}
```

---

### 7.4 剧本状态管理

```
PUT /api/v1/script/repo/scripts/:id/status
```

**请求体**：
```json
{
  "status": "pending_review"
}
```

**状态流转**：`draft` → `pending_review` → `listed` → `sold` → `delisted`

---

### 7.5 资产管理 `V1.0`

```
GET  /api/v1/script/repo/assets                              # 资产列表
POST /api/v1/script/repo/assets/character                    # 创建角色资产
POST /api/v1/script/repo/assets/scene                        # 创建场景资产
PUT  /api/v1/script/repo/assets/:type/:id/maturity            # 更新成熟度
PUT  /api/v1/script/repo/assets/:type/:id/lock               # 锁定资产 (V1.2)
```

**创建角色资产请求体**：
```json
{
  "asset_id": "CH_LIN",
  "name": "林默",
  "description": "28岁，林氏集团总裁，冷面霸道...",
  "face_id": "FACE_LIN_V01",
  "costume_id": "CST_LIN_SUIT_V01",
  "voice_id": "VOICE_LIN_V01",
  "maturity_level": "L2",
  "short_anchor": "冷峻面容，剑眉星目，黑色短发，身高185cm，穿定制深色西装",
  "long_anchor": "面部结构: 轮廓分明... 身型: 宽肩窄腰... 标志物: 左腕机械表...",
  "reference_image_urls": [
    "https://cdn.example.com/assets/characters/lin_ref_01.png"
  ],
  "consistency_prompt": "Chinese male CEO, sharp jawline, short black hair, tailored dark suit, cold expression",
  "seed_value": 42424242
}
```

---

### 7.6 连续性状态 `V1.2`

```
GET /api/v1/script/repo/continuity/:project_id          # 获取
PUT /api/v1/script/repo/continuity/:project_id          # 更新
```

---

## 8. 交易与支付接口（trade）

> 路由前缀：`/api/v1/trade` | 认证要求：JWT | 限流：200次/分钟

### 8.1 市场搜索 `V1.1`

```
GET /api/v1/trade/market/search
```

**请求参数**：
```
?keyword=霸道总裁
&genre=言情
&plot=重生,先婚后爱
&tone=甜宠
&setting=现代
&episode_count_min=20
&episode_count_max=80
&license_type=normal
&price_min=0
&price_max=50
&rating_min=4
&sort=sales_desc
&page=1&page_size=20
```

**响应 `data.items` 元素**：
```json
{
  "script_id": 12345,
  "title": "霸道总裁的替身新娘",
  "author": { "uuid": "usr_xxx", "nickname": "编剧小王" },
  "cover_image_url": "https://cdn.example.com/...",
  "tags": {
    "genre": "言情",
    "plot": ["重生", "先婚后爱"],
    "tone": ["甜宠", "打脸"],
    "setting": "现代"
  },
  "episode_count": 40,
  "total_words": 58000,
  "rating": 4.8,
  "review_count": 128,
  "sales_count": 128,
  "licenses": [
    { "type": "normal", "price": 29.90 },
    { "type": "exclusive", "price": 199.90 },
    { "type": "buyout", "price": 999.90 }
  ],
  "listed_at": "2026-05-01T00:00:00+08:00"
}
```

---

### 8.2 剧本详情与试读 `V1.1`

```
GET /api/v1/trade/market/scripts/:id              # 详情
GET /api/v1/trade/market/scripts/:id/preview      # 试读(前1-3集)
```

---

### 8.3 订单管理 `V1.1`

```
POST /api/v1/trade/orders                     # 创建订单
GET  /api/v1/trade/orders/:id                 # 订单详情
GET  /api/v1/trade/orders                     # 我的订单列表
POST /api/v1/trade/orders/:id/pay             # 发起支付
```

**创建订单请求体**：
```json
{
  "script_id": 12345,
  "license_type": "normal",
  "coupon_code": "SUMMER2026"
}
```

**订单详情响应**：
```json
{
  "order_no": "ORD20260608153000001",
  "script": { "id": 12345, "title": "霸道总裁的替身新娘" },
  "buyer": { "nickname": "创作者小明" },
  "seller": { "nickname": "编剧小王" },
  "license_type": "normal",
  "amount": 29.90,
  "platform_fee": 5.98,
  "seller_income": 23.92,
  "status": "pending",
  "expire_at": "2026-06-08T15:45:00+08:00",
  "created_at": "2026-06-08T15:30:00+08:00"
}
```

**发起支付响应**：
```json
{
  "code": 0,
  "data": {
    "payment_method": "wechat",
    "payment_params": {
      "appId": "wx1234567890",
      "timeStamp": "1717843200",
      "nonceStr": "abc123",
      "package": "prepay_id=wx1234567890",
      "signType": "RSA",
      "paySign": "..."
    }
  }
}
```

---

### 8.4 企业采购 `V1.1`

```
POST /api/v1/trade/enterprise/purchase-request              # 提交采购申请
PUT  /api/v1/trade/enterprise/purchase-request/:id/approve   # 审批
```

**采购申请请求体**：
```json
{
  "script_id": 12345,
  "license_type": "normal",
  "reason": "用于矩阵号@霸道剧场 第3季内容填充"
}
```

**审批请求体**：
```json
{
  "action": "approved",
  "note": "符合部门预算，同意采购"
}
```

---

### 8.5 售卖与收益 `V1.1`

```
GET  /api/v1/trade/sales                      # 我的售卖数据
GET  /api/v1/trade/earnings                   # 收益明细
POST /api/v1/trade/earnings/withdraw          # 提现申请
```

---

## 9. AI资产市场接口（asset-market）

> 路由前缀：`/api/v1/asset` | 认证要求：JWT | 限流：500次/分钟

### 9.1 市场浏览

```
GET /api/v1/asset/market/search?type=checkpoint&keyword=韩漫&sort=popular
GET /api/v1/asset/market/models                    # 风格模型列表
GET /api/v1/asset/market/models/:id                # 模型详情
GET /api/v1/asset/market/characters                # 角色资产列表
GET /api/v1/asset/market/scenes                    # 场景资产列表
GET /api/v1/asset/market/prompts                   # 提示词市场
GET /api/v1/asset/market/voices                    # 音色库
GET /api/v1/asset/market/sounds                    # 音效/BGM库
```

**风格模型响应元素**：
```json
{
  "id": 1,
  "asset_type": "checkpoint",
  "name": "韩漫风格 - 都市言情",
  "author": { "nickname": "AI视觉师" },
  "preview_urls": [
    "https://cdn.example.com/previews/model_001_01.png",
    "https://cdn.example.com/previews/model_001_02.png"
  ],
  "tags": ["韩漫", "都市", "言情", "甜宠"],
  "price": 9.90,
  "recommended_params": {
    "cfg_scale": 7,
    "sampler": "DPM++ 2M Karras",
    "steps": 30,
    "resolution": "1080x1920",
    "trigger_words": "korean manhwa style, soft shading"
  },
  "download_count": 2340,
  "use_count": 5600,
  "rating": 4.9,
  "created_at": "2026-03-01T00:00:00+08:00"
}
```

---

### 9.2 资产操作

```
POST /api/v1/asset/market/models/:id/apply        # 应用模型到画布
POST /api/v1/asset/market/assets/:id/download     # 下载资产
POST /api/v1/asset/market/assets/:id/favorite     # 收藏
POST /api/v1/asset/market/publish                 # 上架资产
PUT  /api/v1/asset/market/assets/:id              # 编辑资产
```

**上架资产请求体**：
```json
{
  "asset_type": "character",
  "name": "冷艳女总裁 - 李雪",
  "description": "适合都市言情/豪门类剧本的女主角色",
  "preview_urls": ["https://cdn.example.com/..."],
  "tags": ["女总裁", "冷艳", "都市", "豪门"],
  "price": 0,
  "metadata": {
    "face_id": "FACE_LIXUE_V01",
    "costume_ids": ["CST_LIXUE_OFFICE_V01", "CST_LIXUE_CASUAL_V01"],
    "consistency_prompt": "Chinese female CEO, cold beauty, long black hair, sharp eyes..."
  }
}
```

---

### 9.3 个人资产管理

```
GET /api/v1/asset/market/my/assets          # 我上架的
GET /api/v1/asset/market/my/favorites       # 我收藏的
GET /api/v1/asset/market/my/downloads       # 我下载的
```

---

## 10. 画布视频工作台接口（canvas）`V1.3`

> 路由前缀：`/api/v1/canvas` | 认证要求：JWT | 限流：300次/分钟  
> **V1.3 画布功能增强**：本章涵盖画布基础接口（V1.0-V1.2）与V1.3增强API（节点引擎、下游处理节点、去重、时间轴添加节点、合成变体、图像节点操作、视频节点操作、音频节点操作等）。详细V1.3画布API设计参见 **[《后端产品功能设计_V1.5.md》Section 9.1](../01-core/后端产品功能设计_V1.5.md)**。

### 10.1 画布项目

```
POST /api/v1/canvas/projects                       # 创建
GET  /api/v1/canvas/projects/:id                   # 获取（含完整状态）
PUT  /api/v1/canvas/projects/:id                   # 更新
POST /api/v1/canvas/projects/:id/import-script      # 从仓库导入剧本
```

**创建画布项目请求体**：
```json
{
  "script_id": 12345,
  "name": "霸道总裁的替身新娘 - 画布项目",
  "episode_index": 1,
  "style_config": {
    "style_id": "STYLE_KMANGA",
    "aspect_ratio": "9:16",
    "resolution": "1080p",
    "fps": 25
  }
}
```

**从剧本导入画布请求体**：
```json
{
  "script_id": 12345,
  "source_version": "v1.0",
  "coupling_mode": "semi",
  "shots": [
    {
      "shot_no": 1,
      "episode_no": 1,
      "scene": "总裁办公室",
      "content": "苏小晚端着咖啡推门而入",
      "camera": "中景",
      "duration_ms": 3000,
      "characters": ["苏小晚"]
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `script_id` | long | 剧本ID |
| `source_version` | string | 导入的剧本版本，默认当前版本 |
| `coupling_mode` | enum | `weak` / `semi` / `strong`，默认`semi` |
| `shots` | array | 从分镜脚本Master导入的镜头列表 |

> 导入成功后，画布保存生产快照。半耦合模式下，后续画布内修改不自动覆盖剧本分镜Master；强耦合模式下，同步必须经过用户确认。

**获取画布项目响应 `data`**：
```json
{
  "id": 1,
  "uuid": "canvas_a1b2c3",
  "name": "霸道总裁的替身新娘",
  "script_id": 12345,
  "episode_index": 1,
  "style_config": { },
  "canvas_state": {
    "version": 56,
    "shots": [
      {
        "shot_id": "EP01_SC01_SH001",
        "order": 1,
        "status": "completed",
        "keyframe_start": {
          "image_url": "https://cdn.example.com/frames/shot_001_start.png",
          "prompt": "man in suit at desk, looking up from documents..."
        },
        "keyframe_end": { "image_url": null },
        "camera_movement": "push",
        "duration_ms": 3000,
        "characters": ["CH_LIN"],
        "scenes": ["LOC_OFFICE"],
        "dialogue": {
          "character": "林默",
          "voice_id": "VOICE_LIN_V01",
          "text": "你之前在哪儿工作？",
          "emotion": "平静",
          "speed": 1.0
        },
        "inpaint_regions": [],
        "layer_state": {}
      }
    ],
    "timeline": {
      "video_track": [{"shot_id": "EP01_SC01_SH001", "start_ms": 0, "duration_ms": 3000}],
      "transition_track": [],
      "audio_track": [],
      "subtitle_track": [],
      "bgm_track": [],
      "sfx_track": []
    }
  },
  "status": "editing",
  "updated_at": "2026-06-08T15:30:00+08:00"
}
```

---

### 10.2 分镜管理

```
GET    /api/v1/canvas/projects/:id/shots                    # 分镜列表
POST   /api/v1/canvas/projects/:id/shots                    # 创建/导入
PUT    /api/v1/canvas/projects/:id/shots/:shot_id            # 更新
PUT    /api/v1/canvas/projects/:id/shots/reorder             # 排序
DELETE /api/v1/canvas/projects/:id/shots/:shot_id            # 删除
POST   /api/v1/canvas/projects/:id/shots/:shot_id/generate   # 生成画面
POST   /api/v1/canvas/projects/:id/shots/batch-generate      # 批量生成(V1.2)
```

**分镜排序请求体**：
```json
{
  "shot_ids": [
    "EP01_SC01_SH001",
    "EP01_SC01_SH003",
    "EP01_SC01_SH002"
  ]
}
```

---

### 10.3 关键帧编辑

```
PUT /api/v1/canvas/projects/:id/shots/:shot_id/keyframe
```

**请求体**：
```json
{
  "keyframe_start": {
    "image_url": "https://cdn.example.com/...",
    "prompt": "man in dark suit, seated at mahogany desk..."
  },
  "keyframe_end": {
    "prompt": "same man from closer angle, slight tilt..."
  },
  "camera_movement": "push",
  "motion_intensity": 0.6,
  "motion_curve": "ease_in_out"
}
```

---

### 10.4 画布编辑

```
POST /api/v1/canvas/projects/:id/shots/:shot_id/inpaint       # Inpaint重绘
POST /api/v1/canvas/projects/:id/shots/:shot_id/outpaint      # Outpaint扩图(V1.2)
```

**Inpaint请求体**：
```json
{
  "mask_region": {
    "x": 320, "y": 240, "width": 200, "height": 150,
    "mask_type": "brush"
  },
  "prompt": "fix the character's left hand position, natural pose",
  "strength": 0.75
}
```

---

### 10.5 时间轴

```
PUT  /api/v1/canvas/projects/:id/timeline                    # 更新时间轴
POST /api/v1/canvas/projects/:id/timeline/dub                # 生成配音
POST /api/v1/canvas/projects/:id/timeline/subtitle            # 生成字幕
```

**生成配音请求体**：
```json
{
  "shot_ids": ["EP01_SC01_SH001", "EP01_SC01_SH002"],
  "voice_config": {
    "CH_LIN": { "voice_id": "VOICE_LIN_V01", "emotion": "平静", "speed": 1.0 },
    "SU": { "voice_id": "VOICE_SU_V01", "emotion": "紧张", "speed": 1.1 }
  }
}
```

---

### 10.6 合成与导出

```
POST /api/v1/canvas/projects/:id/compose                     # 合成视频
GET  /api/v1/canvas/projects/:id/compose/:task_id            # 查询合成进度
POST /api/v1/canvas/projects/:id/export                      # 导出成片
GET  /api/v1/canvas/export/:task_id                          # 查询导出进度
GET  /api/v1/canvas/export/:task_id/download                 # 下载
```

**导出请求体**：
```json
{
  "aspect_ratio": "9:16",
  "resolution": "1080p",
  "format": "mp4",
  "codec": "h264",
  "quality": "high",
  "watermark": false,
  "episodes": [1, 2, 3]
}
```

**导出进度响应**：
```json
{
  "code": 0,
  "data": {
    "task_id": "export_xyz",
    "status": "processing",
    "progress": 65,
    "current_stage": "audio_mixing",
    "estimated_remaining_seconds": 45
  }
}
```

**导出完成响应**：
```json
{
  "code": 0,
  "data": {
    "task_id": "export_xyz",
    "status": "completed",
    "download_url": "https://cdn.example.com/exports/video_001.mp4?sign=...&expires=...",
    "file_info": {
      "file_name": "霸道总裁的替身新娘_第1-3集_1080p.mp4",
      "file_size_bytes": 125829120,
      "duration_seconds": 545,
      "resolution": "1080x1920",
      "codec": "h264",
      "bitrate_kbps": 4000
    },
    "expire_at": "2026-07-08T15:30:00+08:00"
  }
}
```

---

### 10.7 🆕 画布节点管理 `V1.2` — 对标 LibTV 无限画布

```
POST   /api/v1/canvas/projects/:id/nodes                       # 创建节点
GET    /api/v1/canvas/projects/:id/nodes                       # 获取所有节点
PUT    /api/v1/canvas/projects/:id/nodes/:nodeId               # 更新节点位置/属性
DELETE /api/v1/canvas/projects/:id/nodes/:nodeId               # 删除节点
POST   /api/v1/canvas/projects/:id/nodes/:nodeId/duplicate     # 复制节点(保留连线)
```

**创建节点请求体**：
```json
{
  "type": "script",
  "x": 60,
  "y": 40,
  "width": 420,
  "height": 360,
  "data": {
    "label": "脚本 · EP01 命运的相遇",
    "script_id": 12345,
    "episode_index": 1
  },
  "style_config": {
    "theme": "dark",
    "card_variant": "text_empty",
    "title_position": "outside_top",
    "icon": "script",
    "handles": ["left", "right"],
    "empty_actions": [
      { "key": "script_to_storyboard", "label": "剧本生成分镜脚本" },
      { "key": "character_to_storyboard", "label": "角色生成分镜脚本" }
    ]
  },
  "input_data": {
    "generator": {
      "panel": "script",
      "model_id": "gvlm-3.1",
      "plot_prompt": "",
      "parse_mode": "storyboard",
      "target_shot_count": 20
    }
  }
}
```

**节点类型**：`text` / `image` / `video` / `audio` / `script`

**获取所有节点响应**：
```json
{
  "code": 0,
  "data": {
    "nodes": [
      {
        "id": "node_abc123",
        "type": "script",
        "x": 60, "y": 40,
        "width": 340, "height": 280,
        "data": { "label": "脚本 · EP01", "shots": [...] },
        "connections": [
          { "target_node_id": "node_def456", "target_port": "in" }
        ]
      }
    ],
    "connections": [
      { "id": "conn_001", "source_node_id": "node_abc123", "target_node_id": "node_def456" }
    ]
  }
}
```

### 10.8 🆕 节点连线管理 `V1.2`

```
POST /api/v1/canvas/projects/:id/nodes/connect                  # 创建连线
DELETE /api/v1/canvas/projects/:id/connections/:connId          # 删除连线
POST /api/v1/canvas/projects/:id/nodes/group                    # 节点打组(Ctrl+G)
POST /api/v1/canvas/projects/:id/workflows                      # 保存为工作流模板
GET  /api/v1/canvas/projects/:id/workflows                      # 获取已保存工作流
POST /api/v1/canvas/projects/:id/workflows/:wfId/apply          # 应用工作流到画布
POST /api/v1/canvas/projects/:id/workflows/:wfId/execute-all    # 整组执行
```

**创建连线请求体**：
```json
{
  "source_node_id": "node_abc123",
  "source_port": "out",
  "target_node_id": "node_def456",
  "target_port": "in"
}
```

### 10.9 🆕 脚本节点·批量流水线 `V1.2`

```
POST /api/v1/canvas/projects/:id/nodes/:nodeId/script/generate-storyboard  # AI生成分镜脚本
POST /api/v1/canvas/projects/:id/nodes/:nodeId/script/batch-image          # 批量生成分镜图
POST /api/v1/canvas/projects/:id/nodes/:nodeId/script/batch-video          # 批量生成视频
PUT  /api/v1/canvas/projects/:id/nodes/:nodeId/script/cell                  # 修改分镜表格单元格
```

**生成分镜脚本请求体**：
```json
{
  "mode": "character",
  "character_images": ["https://cdn.example.com/chars/hero.png"],
  "prompt": "一个霸道总裁在办公室遇到神秘咖啡店员的故事",
  "style": "韩漫风格",
  "episode_count": 1
}
```

**批量生成分镜图请求体**：
```json
{
  "shot_ids": ["SH001", "SH002", "SH003"],
  "style_model": "STYLE_KMANGA",
  "image_model": "seedream_5.0",
  "aspect_ratio": "9:16",
  "resolution": "1080p",
  "batch_size": 3
}
```

### 10.10 🆕 Slash快捷命令 `V1.2`

```
POST /api/v1/canvas/projects/:id/slash/:command                  # 执行Slash命令
```

**支持的命令**：`nine-grid` / `four-grid` / `25-grid` / `character-3view` / `lighting-fix` / `director-desk` / `video-clip` / `split` / `stitch-2k` / `panorama-720` / `push-forward-3s` / `push-back-5s`

**九宫格请求体**：
```json
{
  "source_node_id": "node_img_001",
  "mode": "multi-camera",
  "style": "cinematic"
}
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "task_id": "slash_nine_grid_001",
    "status": "pending",
    "result_node_ids": ["node_grid_01", "node_grid_02", "..."]
  }
}
```

### 10.11 🆕 导演台·3D构图 `V1.2`

```
POST /api/v1/canvas/projects/:id/director-desk                   # 创建导演台节点
GET  /api/v1/canvas/projects/:id/director-desk/:deskId           # 获取导演台状态
PUT  /api/v1/canvas/projects/:id/director-desk/:deskId           # 更新3D场景
POST /api/v1/canvas/projects/:id/director-desk/:deskId/capture   # 多视角截图
```

**更新3D场景请求体**：
```json
{
  "objects": [
    { "type": "character", "model_id": "CH_LIN", "position": {"x": 0, "y": 0, "z": 2}, "rotation": {"y": 0} },
    { "type": "prop", "model_id": "PROP_DESK", "position": {"x": 0, "y": 0, "z": 0} }
  ],
  "camera": { "position": {"x": 3, "y": 1.5, "z": 5}, "look_at": {"x": 0, "y": 1, "z": 0} }
}
```

**多视角截图响应**：
```json
{
  "code": 0,
  "data": {
    "captures": [
      { "angle": "front", "image_url": "https://cdn.example.com/captures/desk_001_front.png" },
      { "angle": "side", "image_url": "https://cdn.example.com/captures/desk_001_side.png" },
      { "angle": "top", "image_url": "https://cdn.example.com/captures/desk_001_top.png" }
    ]
  }
}
```

### 10.12 🆕 时间轴多轨编辑器 `V1.2`

```
PUT  /api/v1/canvas/projects/:id/timeline/full                   # 全量更新时间轴(5轨道)
GET  /api/v1/canvas/projects/:id/timeline/full                   # 获取时间轴完整状态
POST /api/v1/canvas/projects/:id/timeline/clip                   # 裁取视频片段
POST /api/v1/canvas/projects/:id/timeline/splice                 # 拼接多片段
```

**全量更新时间轴请求体**：
```json
{
  "video_track": [
    { "shot_id": "SH001", "clip_start_ms": 0, "clip_end_ms": 3000, "order": 1 },
    { "shot_id": "SH002", "clip_start_ms": 500, "clip_end_ms": 5500, "order": 2 }
  ],
  "audio_track": [
    { "shot_id": "SH001", "voice_id": "VOICE_LIN_V01", "audio_url": "https://cdn.example.com/audio/dub_001.wav", "start_ms": 0 }
  ],
  "subtitle_track": [
    { "shot_id": "SH001", "text": "你之前在哪儿工作？", "start_ms": 0, "end_ms": 3000 }
  ],
  "bgm_track": [
    { "music_id": "MUS_SUSPENSE_V01", "start_ms": 0, "end_ms": 24000, "volume": 0.3, "loop": true }
  ],
  "sfx_track": [
    { "shot_id": "SH001", "sfx_id": "SFX_FOOTSTEP", "start_ms": 500 },
    { "shot_id": "SH004", "sfx_id": "SFX_DOOR_OPEN", "start_ms": 15000 }
  ]
}
```

### 10.13 🆕 多模态参考生视频 `V1.2` — 对标 Seedance 2.0

```
POST /api/v1/canvas/projects/:id/shots/:shotId/generate-multimodal  # 多模态参考生视频
```

**请求体**：
```json
{
  "model": "seedance_2.0",
  "references": [
    { "type": "image", "url": "https://cdn.example.com/refs/style.png", "usage": "@图片1 作为风格参考" },
    { "type": "video", "url": "https://cdn.example.com/refs/camera.mp4", "usage": "@视频1 参考镜头语言" },
    { "type": "audio", "url": "https://cdn.example.com/refs/bgm.mp3", "usage": "@音频1 用于配乐" }
  ],
  "prompt": "根据参考图的风格和参考视频的运镜方式，生成一段5秒的视频",
  "duration": 5,
  "aspect_ratio": "9:16",
  "audio_sync": true
}
```

---

## 🆕 10.14 Agent协作接口（agent）`V1.2` — 对标 ToonFlow

> 路由前缀：`/api/v1/agent` | 认证要求：JWT | 限流：100次/分钟

```
POST /api/v1/agent/orchestrate            # 启动Agent协作任务
GET  /api/v1/agent/task/:taskId           # 查询Agent任务状态
GET  /api/v1/agent/task/:taskId/review    # 获取监督层审阅结果
POST /api/v1/agent/task/:taskId/retry     # 监督层触发重生成
PUT  /api/v1/agent/config                 # 更新Agent配置
```

**启动Agent协作请求体**：
```json
{
  "intent": "将《霸道总裁的替身新娘》第1章改编为40集漫剧",
  "mode": "semi_auto",
  "config": {
    "script_agent": { "model": "claude-4", "style": "甜宠打脸" },
    "production_agent": { "max_concurrent": 5, "quality": "1080p" },
    "quality_agent": { "threshold": 0.7, "auto_retry": true }
  }
}
```

**监督层审阅结果响应**：
```json
{
  "code": 0,
  "data": {
    "task_id": "agent_task_001",
    "overall_score": 0.85,
    "passed": true,
    "issues": [
      { "shot_id": "EP01_SC03_SH008", "type": "continuity_break", "severity": "P0", "suggestion": "场景跳跃，增加转场镜头" }
    ],
    "reviewed_at": "2026-06-09T15:30:00+08:00"
  }
}
```

## 🆕 10.15 Agent记忆接口（agent/memory）`V1.2`

```
POST /api/v1/agent/memory/store          # 存储记忆
GET  /api/v1/agent/memory/recall         # 语义召回记忆(?query=角色林默&type=long_term)
GET  /api/v1/agent/memory/list           # 记忆列表(?project_id=xxx&type=short|long|project)
PUT  /api/v1/agent/memory/:id            # 编辑记忆
DELETE /api/v1/agent/memory/:id          # 删除记忆
POST /api/v1/agent/memory/export         # 导出项目记忆(JSON)
```

**存储记忆请求体**：
```json
{
  "project_id": "PROJ_DOMINEERING_PRESIDENT",
  "type": "long_term",
  "category": "character",
  "key": "林默_性格设定",
  "content": "林默：28岁，林氏集团总裁，冷面霸道，外表冷漠内心细腻...",
  "importance": 0.9,
  "tags": ["主角", "性格", "设定"]
}
```

**语义召回响应**：
```json
{
  "code": 0,
  "data": {
    "query": "角色林默",
    "results": [
      { "id": 1, "content": "林默：28岁...", "similarity": 0.95, "type": "long_term" },
      { "id": 2, "content": "林默习惯左手拿咖啡杯...", "similarity": 0.82, "type": "short_term" }
    ]
  }
}
```

## 🆕 10.16 Skill文件接口（skill）`V1.2`

```
GET    /api/v1/skill/files                    # Skill文件列表
GET    /api/v1/skill/files/:name              # 获取Skill内容
PUT    /api/v1/skill/files/:name              # 更新Skill (Markdown)
POST   /api/v1/skill/files/preview            # 预览Skill效果(渲染后Prompt)
GET    /api/v1/skill/templates                # Skill模板库
POST   /api/v1/skill/files/:name/rollback     # 回滚到历史版本
```

**更新Skill请求体**：
```json
{
  "content": "# ScriptAgent Skill\n\n## 创作风格\n- 甜宠打脸\n- 节奏快，每集至少1个反转\n\n## 对白偏好\n- 男主：简短有力\n- 女主：外柔内刚\n\n## 变量\n- genre: {{genre}}\n- character_count: {{character_count}}",
  "change_summary": "调整反转密度为每集1个"
}
```

## 🆕 10.17 供应商管理接口（setting）`V1.2`

```
GET    /api/v1/setting/providers                # 供应商列表
PUT    /api/v1/setting/providers/:id            # 更新供应商配置(TS代码)
POST   /api/v1/setting/providers/test           # 测试供应商连接
GET    /api/v1/setting/providers/:id/health     # 健康检查
POST   /api/v1/setting/providers/templates      # 从模板创建供应商
```

**更新供应商请求体**：
```json
{
  "name": "DeepSeek-V3",
  "type": "text",
  "endpoint": "https://api.deepseek.com/v1",
  "api_key": "sk-xxxx",
  "models": ["deepseek-chat", "deepseek-reasoner"],
  "priority": 1,
  "fallback_provider_id": "openai-gpt4"
}
```

## 🆕 10.18 事件图谱接口（script/event-graph）`V1.2`

```
POST /api/v1/script/event-graph/extract       # 提取章节事件图谱
GET  /api/v1/script/event-graph/:projectId    # 获取事件图谱
PUT  /api/v1/script/event-graph/:projectId    # 编辑事件图谱
GET  /api/v1/script/event-graph/:projectId/export   # 导出图谱(PNG/SVG)
```

**事件图谱响应**：
```json
{
  "code": 0,
  "data": {
    "project_id": "PROJ_DOMINEERING_PRESIDENT",
    "events": [
      { "id": "evt_001", "chapter": 1, "type": "character_intro", "title": "林默首次登场",
        "summary": "林默在总裁办公室首次亮相，展现冷面霸道气质",
        "characters": ["林默"], "importance": 0.95 },
      { "id": "evt_002", "chapter": 1, "type": "conflict", "title": "苏小晚受辱",
        "summary": "苏小晚在员工餐厅被同事当众嘲讽", "characters": ["苏小晚"], "importance": 0.8 }
    ],
    "edges": [
      { "source": "evt_001", "target": "evt_002", "relation": "causality", "label": "为后续英雄救美埋下伏笔" }
    ]
  }
}
```

---

## 🆕 11. Agent与Skill接口（agent）`V1.3`

> 路由前缀：`/api/v1/agent` | 认证要求：JWT | 限流：100次/分钟

### 11.1 启动Agent协作

```
POST /api/v1/agent/orchestrate
```

**请求体**：
```json
{
  "intent": "将《霸道总裁的替身新娘》第1章改编为40集漫剧",
  "mode": "semi_auto",
  "config": {
    "script_agent": { "model": "claude-4", "style": "甜宠打脸" },
    "production_agent": { "max_concurrent": 5, "quality": "1080p" },
    "quality_agent": { "threshold": 0.7, "auto_retry": true }
  }
}
```

**成功响应** `200`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "task_id": "agent_task_001",
    "status": "pending",
    "estimated_seconds": 300,
    "pipeline": ["script", "storyboard", "image_gen", "video_gen", "compose", "review"]
  }
}
```

---

### 11.2 查询Agent任务

```
GET /api/v1/agent/task/:id
```

**成功响应** `200`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "task_id": "agent_task_001",
    "status": "processing",
    "progress": 0.65,
    "current_stage": "image_gen",
    "stages": {
      "script": "completed",
      "storyboard": "completed",
      "image_gen": "processing",
      "video_gen": "pending",
      "compose": "pending",
      "review": "pending"
    },
    "result": null,
    "created_at": "2026-06-15T10:00:00+08:00",
    "updated_at": "2026-06-15T10:03:30+08:00"
  }
}
```

---

### 11.3 Skill管理

```
POST /api/v1/agent/skills        — 创建/注册Skill
GET  /api/v1/agent/skills        — Skill列表
POST /api/v1/agent/skills/:id/execute  — 执行Skill
```

**创建Skill请求体**：
```json
{
  "name": "script_agent_kmanhwa",
  "type": "script",
  "description": "韩漫风格剧本生成Skill，适用于甜宠打脸类都市言情",
  "content": "# ScriptAgent Skill\n\n## 创作风格\n- 甜宠打脸\n- 节奏快，每集至少1个反转\n\n## 对白偏好\n- 男主：简短有力\n- 女主：外柔内刚\n\n## 变量\n- genre: {{genre}}\n- character_count: {{character_count}}",
  "tags": ["韩漫", "甜宠", "都市言情"],
  "version": "1.0.0"
}
```

**成功响应** `201`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "skill_001",
    "name": "script_agent_kmanhwa",
    "type": "script",
    "version": "1.0.0",
    "created_at": "2026-06-15T10:00:00+08:00"
  }
}
```

**Skill列表响应** `200`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "id": "skill_001",
        "name": "script_agent_kmanhwa",
        "type": "script",
        "description": "韩漫风格剧本生成Skill",
        "version": "1.0.0",
        "usage_count": 128,
        "created_at": "2026-06-15T10:00:00+08:00"
      },
      {
        "id": "skill_002",
        "name": "quality_review_strict",
        "type": "quality",
        "description": "严格质检Skill，P0/P1问题零容忍",
        "version": "1.0.0",
        "usage_count": 56,
        "created_at": "2026-06-14T08:30:00+08:00"
      }
    ],
    "pagination": { "page": 1, "page_size": 20, "total": 8, "total_pages": 1, "has_more": false }
  }
}
```

**执行Skill请求体**：
```json
{
  "skill_id": "skill_001",
  "inputs": {
    "genre": "言情",
    "character_count": 6,
    "idea": "一个外卖小哥其实是隐藏的豪门继承人"
  },
  "project_id": "PROJ_DOMINEERING_PRESIDENT"
}
```

**成功响应** `200`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "execution_id": "exec_abc123",
    "task_id": "agent_task_002",
    "status": "pending"
  }
}
```

---

### 11.4 执行日志

```
GET /api/v1/agent/executions/:id
```

**成功响应** `200`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "execution_id": "exec_abc123",
    "skill_id": "skill_001",
    "task_id": "agent_task_002",
    "status": "completed",
    "inputs": {
      "genre": "言情",
      "character_count": 6,
      "idea": "一个外卖小哥其实是隐藏的豪门继承人"
    },
    "outputs": {
      "script_id": 12346,
      "title": "外卖小哥的豪门人生",
      "episode_count": 40
    },
    "logs": [
      { "timestamp": "2026-06-15T10:00:00+08:00", "level": "info", "message": "开始执行Skill script_agent_kmanhwa" },
      { "timestamp": "2026-06-15T10:00:05+08:00", "level": "info", "message": "生成选题完成，选择'豪门外卖'" },
      { "timestamp": "2026-06-15T10:02:30+08:00", "level": "info", "message": "剧本生成完成，共40集" }
    ],
    "duration_ms": 150000,
    "created_at": "2026-06-15T10:00:00+08:00",
    "completed_at": "2026-06-15T10:02:30+08:00"
  }
}
```

---

### 11.5 画布编排

```
POST /api/v1/agent/orchestrate/canvas
```

**请求体**：
```json
{
  "project_id": "PROJ_DOMINEERING_PRESIDENT",
  "episode_ids": ["EP01", "EP02", "EP03"],
  "pipeline": {
    "image_gen": {
      "model": "seedream_5.0",
      "style": "STYLE_KMANGA",
      "aspect_ratio": "9:16",
      "resolution": "1080p"
    },
    "video_gen": {
      "model": "seedance_2.0",
      "duration_per_shot": 5,
      "audio_sync": true
    },
    "compose": {
      "mode": "hybrid",
      "add_transitions": true,
      "add_bgm": true
    }
  },
  "auto_quality_check": true
}
```

**成功响应** `200`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "task_id": "agent_canvas_task_001",
    "status": "pending",
    "pipeline_stages": ["image_gen", "video_gen", "compose", "quality_check"],
    "estimated_seconds": 600
  }
}
```

---

### 11.6 可用工具

```
GET /api/v1/agent/tools
```

**成功响应** `200`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "tools": [
      {
        "name": "generate_script",
        "description": "生成漫剧剧本（快速模式/分步模式）",
        "category": "script",
        "parameters": {
          "idea": { "type": "string", "required": true, "description": "创作灵感/故事核心" },
          "tags": { "type": "object", "required": true, "description": "4轴标签" },
          "episode_count": { "type": "integer", "required": false, "default": 40 }
        }
      },
      {
        "name": "generate_storyboard",
        "description": "从剧本生成分镜脚本（A/B/C档可选）",
        "category": "storyboard",
        "parameters": {
          "script_id": { "type": "integer", "required": true },
          "tier": { "type": "string", "required": false, "default": "A", "enum": ["A", "B", "C"] }
        }
      },
      {
        "name": "generate_image",
        "description": "为分镜生成关键帧图像",
        "category": "image",
        "parameters": {
          "shot_id": { "type": "string", "required": true },
          "style_model": { "type": "string", "required": true },
          "prompt": { "type": "string", "required": true }
        }
      },
      {
        "name": "generate_video",
        "description": "从关键帧生成视频片段",
        "category": "video",
        "parameters": {
          "shot_id": { "type": "string", "required": true },
          "model": { "type": "string", "required": false, "default": "seedance_2.0" },
          "duration": { "type": "integer", "required": false, "default": 5 }
        }
      },
      {
        "name": "compose_video",
        "description": "合成最终视频（含配音/BGM/字幕/转场）",
        "category": "compose",
        "parameters": {
          "project_id": { "type": "string", "required": true },
          "mode": { "type": "string", "required": false, "default": "hybrid" }
        }
      },
      {
        "name": "quality_check",
        "description": "执行质量检查（SOP准入/画布质量巡检）",
        "category": "quality",
        "parameters": {
          "project_id": { "type": "string", "required": true },
          "check_type": { "type": "string", "required": false, "default": "canvas", "enum": ["pre_production", "canvas", "pre_export"] }
        }
      }
    ],
    "total": 6
  }
}
```

---

## 12. 生产SOP接口（sop）

> 路由前缀：`/api/v1/sop` | 认证要求：JWT（企业成员需对应权限） | 限流：500次/分钟

### 12.1 生产准入检查 `V1.1`

```
POST /api/v1/sop/check/production-readiness
```

**请求体**：
```json
{
  "project_id": "PROJ_DOMINEERING_PRESIDENT",
  "episode_id": "EP01"
}
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "overall": "yellow",
    "passed": 11,
    "failed": 2,
    "checks": [
      { "id": 1, "name": "剧情事实无偏移", "result": "pass" },
      { "id": 2, "name": "场景目标明确", "result": "pass" },
      { "id": 8, "name": "AI提示词不过长", "result": "fail", "detail": "Shot EP01_SC05_SH003 prompt exceeds 500 chars" }
    ],
    "recommendation": "2项未通过，可进入生产但需标记风险，生产前修正P1项"
  }
}
```

---

### 12.2 审计返工 `V1.1`

```
GET  /api/v1/sop/projects/:id/audit-list              # 审计列表
POST /api/v1/sop/projects/:id/audit                   # 提交审计
PUT  /api/v1/sop/projects/:id/audit/:audit_id         # 更新修复状态
```

**提交审计请求体**：
```json
{
  "shot_id": "EP01_SC03_SH008",
  "check_item": "场景连续性",
  "issue_type": "空间跳变",
  "severity": "P0",
  "quality_grade": "C",
  "description": "SH008角色在室内门口，SH009无转场出现在街道",
  "fix_suggestion": "在SH008-SH009之间增加转场镜头，或标注时间/空间变化",
  "responsible_role": "director"
}
```

---

### 12.3 版本管理 `V1.1`

```
GET  /api/v1/sop/versions/:project_id                    # 版本历史
POST /api/v1/sop/versions/:project_id/promote            # 版本升级
```

**版本升级请求体**：
```json
{
  "from_version": "V0.5",
  "to_version": "V0.8",
  "comment": "导演确认通过，所有镜头意图已审核"
}
```

---

### 12.4 资产锁定 `V1.2`

```
POST /api/v1/sop/assets/:type/:id/lock             # 锁定（L4）
POST /api/v1/sop/assets/:type/:id/unlock           # 解锁（触发审计）
```

---

### 12.5 AI失败管理 `V1.2`

```
POST /api/v1/sop/failure/record                    # 记录失败
GET  /api/v1/sop/failure/strategy                  # 推荐恢复策略
```

**查询恢复策略响应**：
```json
{
  "code": 0,
  "data": {
    "shot_id": "EP01_SC05_SH012",
    "failure_count": 3,
    "history": [
      { "attempt": 1, "action": "优化Prompt", "result": "failed" },
      { "attempt": 2, "action": "优化Prompt+参数", "result": "failed" }
    ],
    "recommended_action": "检查资产与参考图",
    "suggestions": [
      "强化 Face_ID: FACE_LIN_V01 的参考图",
      "减少动作复杂度：当前单镜包含3个动作"
    ]
  }
}
```

---

### 12.6 产能估算 `V1.2`

```
GET /api/v1/sop/projects/:id/capacity
```

---

### 12.7 🆕 画布质量巡检 `V1.3`

```
POST /api/v1/sop/canvas/:project_id/check-before-generate
POST /api/v1/sop/canvas/:project_id/check-before-video
POST /api/v1/sop/canvas/:project_id/check-before-compose
POST /api/v1/sop/canvas/:project_id/check-before-export
GET  /api/v1/sop/canvas/:project_id/risk-report
POST /api/v1/sop/canvas/:project_id/auto-fix
```

**check-before-generate 请求体**：
```json
{
  "project_id": "PROJ_DOMINEERING_PRESIDENT",
  "episode_ids": ["EP01", "EP02"],
  "check_items": ["asset_maturity", "prompt_length", "character_continuity", "scene_consistency"]
}
```

**成功响应** `200`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "project_id": "PROJ_DOMINEERING_PRESIDENT",
    "overall": "green",
    "passed": 12,
    "failed": 0,
    "warnings": 1,
    "checks": [
      { "item": "asset_maturity", "result": "pass", "detail": "所有资产≥L3" },
      { "item": "prompt_length", "result": "warn", "detail": "EP01_SC05_SH003 prompt 480chars，接近500上限" },
      { "item": "character_continuity", "result": "pass", "detail": "角色一致性检查通过" },
      { "item": "scene_consistency", "result": "pass", "detail": "场景连续性检查通过" }
    ],
    "recommendation": "可以进入图像生成阶段"
  }
}
```

**risk-report 响应** `200`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "project_id": "PROJ_DOMINEERING_PRESIDENT",
    "risk_level": "low",
    "overall_score": 0.88,
    "categories": {
      "asset_risk": { "score": 0.92, "level": "low", "issues": 0 },
      "continuity_risk": { "score": 0.85, "level": "medium", "issues": 2 },
      "quality_risk": { "score": 0.90, "level": "low", "issues": 1 },
      "pipeline_risk": { "score": 0.95, "level": "low", "issues": 0 }
    },
    "top_issues": [
      { "shot_id": "EP01_SC03_SH008", "type": "scene_jump", "severity": "P1", "description": "室内到室外无转场" },
      { "shot_id": "EP01_SC05_SH012", "type": "prompt_near_limit", "severity": "P2", "description": "Prompt接近字符上限" }
    ],
    "generated_at": "2026-06-15T14:00:00+08:00"
  }
}
```

**auto-fix 请求体**：
```json
{
  "project_id": "PROJ_DOMINEERING_PRESIDENT",
  "fix_targets": [
    { "shot_id": "EP01_SC03_SH008", "issue_type": "scene_jump", "auto_fix": true },
    { "shot_id": "EP01_SC05_SH012", "issue_type": "prompt_near_limit", "auto_fix": false }
  ]
}
```

**成功响应** `200`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "task_id": "autofix_task_001",
    "status": "pending",
    "fixes_applied": 1,
    "fixes_skipped": 1,
    "details": [
      { "shot_id": "EP01_SC03_SH008", "action": "insert_transition_shot", "status": "pending" },
      { "shot_id": "EP01_SC05_SH012", "action": "skipped", "reason": "auto_fix=false，需人工处理" }
    ]
  }
}
```

---

## 13. 通知消息接口（notify）

> 路由前缀：`/api/v1/notify` | 认证要求：JWT | 限流：500次/分钟

### 13.1 站内通知

```
GET /api/v1/notify/in-app                    # 列表
PUT /api/v1/notify/in-app/:id/read           # 标记已读
PUT /api/v1/notify/in-app/read-all           # 全部已读
```

**通知元素**：
```json
{
  "id": 1,
  "type": "script_generated",
  "title": "剧本生成完成",
  "content": "《霸道总裁的替身新娘》已生成完毕，点击查看",
  "target_url": "/scripts/12345",
  "is_read": false,
  "created_at": "2026-06-08T15:31:30+08:00"
}
```

---

### 13.2 通知偏好

```
GET  /api/v1/notify/preferences              # 获取
PUT  /api/v1/notify/preferences              # 更新
```

**更新请求体**：
```json
{
  "script_generated": { "in_app": true, "email": false, "push": true },
  "order_paid": { "in_app": true, "email": true, "sms": true },
  "export_completed": { "in_app": true, "email": true, "push": true },
  "audit_failed": { "in_app": true, "email": true }
}
```

---

## 14. 支付回调接口（webhook）

> 路由前缀：`/api/v1/callback` | 认证要求：签名验证 | 由支付渠道主动调用

### 14.1 微信支付回调

```
POST /api/v1/callback/wechat
```

**接收数据**（XML，由Gateway转为JSON）：
```json
{
  "id": "evt_abc123",
  "create_time": "2026-06-08T15:32:00+08:00",
  "resource_type": "encrypt-resource",
  "event_type": "TRANSACTION.SUCCESS",
  "resource": {
    "algorithm": "AEAD_AES_256_GCM",
    "ciphertext": "...",
    "associated_data": "",
    "nonce": "..."
  }
}
```

> 解密后验签 → 双查确认 → 更新订单状态 → 返回 `{"code": "SUCCESS"}`

### 14.2 支付宝回调

```
POST /api/v1/callback/alipay
```

> 同理，RSA2验签 → 查询确认 → 更新订单 → 返回 `success`

---

## 15. Open API接口（openapi）

> 路由前缀：`/openapi/v1` | 认证要求：API Key + HMAC-SHA256签名 | 限流：按套餐

### 15.1 认证方式

```
Authorization: Bearer <api_key>
X-Signature: t=1717843200,sign=abc123...
```

**签名算法**：`HMAC-SHA256(timestamp + method + path + body, api_secret)`

### 15.2 可用接口（企业版）

```
POST /openapi/v1/script/gen/quick              # 剧本生成
GET  /openapi/v1/script/repo/scripts/:id       # 获取剧本
POST /openapi/v1/canvas/projects               # 创建画布项目
POST /openapi/v1/canvas/projects/:id/export    # 导出
GET  /openapi/v1/canvas/export/:task_id        # 查询导出
```

---

## 16. 通用数据模型

### 16.1 用户对象

```json
{
  "uuid": "usr_a1b2c3d4e5f6",
  "nickname": "创作者小明",
  "avatar_url": "https://cdn.example.com/avatars/default.png",
  "account_type": "personal",
  "member_level": "free",
  "status": "active"
}
```

### 16.2 4轴标签对象

```json
{
  "genre": "言情",
  "plot": ["重生", "先婚后爱"],
  "tone": ["甜宠", "打脸"],
  "setting": "现代"
}
```

### 16.3 分镜卡片对象

```json
{
  "shot_id": "EP01_SC01_SH001",
  "order": 1,
  "status": "completed",
  "keyframe_start": {
    "image_url": "https://cdn.example.com/frames/shot_001_start.png",
    "prompt": "man in suit at desk..."
  },
  "keyframe_end": {
    "image_url": null,
    "prompt": null
  },
  "camera_movement": "push",
  "duration_ms": 3000,
  "characters": ["CH_LIN"],
  "scenes": ["LOC_OFFICE"],
  "dialogue": {
    "character": "林默",
    "voice_id": "VOICE_LIN_V01",
    "text": "你之前在哪儿工作？",
    "emotion": "平静",
    "speed": 1.0
  }
}
```

### 16.4 时间轴对象

```json
{
  "video_track": [
    { "shot_id": "EP01_SC01_SH001", "start_ms": 0, "duration_ms": 3000 }
  ],
  "transition_track": [
    { "from_shot_id": "EP01_SC01_SH001", "to_shot_id": "EP01_SC01_SH002", "type": "fade" }
  ],
  "audio_track": [
    { "shot_id": "EP01_SC01_SH001", "voice_id": "VOICE_LIN_V01", "audio_url": "https://cdn.example.com/audio/xxx.wav" }
  ],
  "subtitle_track": [
    { "shot_id": "EP01_SC01_SH001", "text": "你之前在哪儿工作？", "start_ms": 0, "end_ms": 3000 }
  ],
  "bgm_track": [
    { "music_id": "MUS_SUSPENSE_V01", "start_ms": 0, "end_ms": 30000, "volume": 0.3 }
  ],
  "sfx_track": [
    { "shot_id": "EP01_SC01_SH002", "sfx_id": "SFX_DOOR_KNOCK", "start_ms": 1500 }
  ]
}
```

---

## 17. 枚举字典

### 17.1 账户类型

| 值 | 说明 |
|------|------|
| `personal` | 个人创作者 |
| `enterprise` | 企业用户 |

### 17.2 会员等级

| 值 | 说明 |
|------|------|
| `free` | 免费用户 |
| `creator` | 创作者会员 |
| `enterprise` | 企业版 |

### 17.3 剧本状态

| 值 | 说明 |
|------|------|
| `draft` | 草稿 |
| `pending_review` | 待审核 |
| `listed` | 已上架 |
| `sold` | 已售出 |
| `delisted` | 已下架 |

### 17.4 授权类型

| 值 | 说明 | 价格区间 |
|------|------|------|
| `normal` | 普通授权 | ¥9.9-49.9 |
| `exclusive` | 独家授权 | ¥99.9-499.9 |
| `buyout` | 买断授权 | ¥499.9-2999.9 |

### 17.5 分镜档位

| 值 | 说明 | 版本号 |
|------|------|------|
| `A` | 快速创作档（编导速看） | V0.1→V0.5 |
| `B` | 导演确认档 | V0.8 |
| `C` | 生产交付档 | V1.0 |

### 17.6 资产成熟度

| 值 | 说明 |
|------|------|
| `L0` | 无资产（仅文字锚点） |
| `L1` | 有文字描述（临时ID） |
| `L2` | 有参考图/音频（候选ID） |
| `L3` | 已审核（可批量生产） |
| `L4` | 已锁定（不可随意修改） |

### 17.7 资产类型

| 值 | 说明 |
|------|------|
| `character` | 角色资产 |
| `scene` | 场景资产 |
| `prop` | 道具资产 |
| `voice` | 声音资产 |
| `style` | 风格模型 |
| `checkpoint` | Checkpoint底模 |
| `lora` | LoRA模型 |
| `style_pack` | 风格套餐 |
| `prompt` | 提示词 |
| `bgm` | 背景音乐 |
| `sfx` | 音效 |

### 17.8 AI任务状态

| 值 | 说明 |
|------|------|
| `pending` | 等待处理 |
| `processing` | 处理中 |
| `completed` | 已完成 |
| `failed` | 失败 |
| `cancelled` | 已取消 |

### 17.9 审计严重等级

| 值 | 说明 |
|------|------|
| `P0` | 剧情/连续性硬断裂，不得进入生产 |
| `P1` | 影响生产质量，生产前必须修正 |
| `P2` | 可优化问题，可进入生产但需记录 |
| `P3` | 轻微表达问题，后期优化 |

### 17.10 质量等级

| 值 | 标准 |
|------|------|
| `S` | 可直接生产 |
| `A` | 小修后生产 |
| `B` | 需导演复核 |
| `C` | 不建议生产 |
| `D` | 需要重写 |

### 17.11 画布合成模式

| 值 | 说明 |
|------|------|
| `static_kb` | 图文漫剧（静态图+Ken Burns运镜） |
| `img_to_video` | 图生视频 |
| `keyframe_video` | 首尾帧AI插值视频 |
| `hybrid` | 混合模式 |

### 17.12 导出画幅

| 值 | 分辨率 | 适用平台 |
|------|------|------|
| `9:16` | 720×1280 / 1080×1920 | 抖音/快手/TikTok |
| `16:9` | 1280×720 / 1920×1080 | B站/YouTube |
| `1:1` | 1080×1080 | 小红书/Instagram |

### 17.13 4轴标签 — 题材

| 值 |
|------|
| `言情` `现实情感` `悬疑` `惊悚` `科幻` `武侠` `脑洞` `太空歌剧` `赛博朋克` `游戏` `仙侠` `历史` |

### 17.14 4轴标签 — 情节（部分）

| 值 |
|------|
| `权谋` `重生` `穿越` `系统` `校园` `职场` `娱乐圈` `宫斗宅斗` `犯罪` `探险` `丧尸` `克苏鲁` `规则怪谈` `团宠` `囤物资` `先婚后爱` `追妻火葬场` `破镜重圆` `争霸` `听心声` `读心术` `倒计时文学` `日久生情` `一见钟情` `强取豪夺` `欢喜冤家` `出轨` `婚姻` `家庭` `无系统` |

### 17.15 4轴标签 — 情绪

| 值 |
|------|
| `纯爱` `HE` `BE` `甜宠` `虐恋` `暗恋` `先虐后甜` `沙雕` `爽文` `复仇` `反转` `逆袭` `励志` `烧脑` `热血` `求生` `打脸` `多视角反转` `治愈` `迪化` |

### 17.16 4轴标签 — 时空

| 值 |
|------|
| `古代` `现代` `未来` `架空` `民国` `五零年代` `六零年代` `七零年代` `八零年代` `兽世` |

---

## 18. 错误码参考

### 18.1 错误码结构

```
A B C D E
│ │ │ │ └── 具体错误序号 (0-9)
│ │ │ └──── 错误子类 (0-9)
│ │ └────── 错误大类 (0-9)
│ └──────── 服务编码:
│            0=网关, 1=user, 2=script-gen, 3=script-repo,
│            4=trade, 5=asset, 6=canvas, 7=sop, 8=notify, 9=system
└────────── 错误级别: 4=客户端错误, 5=服务端错误
```

### 18.2 通用错误码

| 错误码 | HTTP状态 | 说明 |
|--------|:---:|------|
| `0` | 200 | 成功 |
| `40001` | 429 | 请求频率超限 |
| `40002` | 400 | 参数校验失败 |
| `40003` | 401 | 未认证 |
| `40004` | 403 | 无权限 |
| `40005` | 404 | 资源不存在 |
| `40006` | 409 | 资源冲突 |
| `50001` | 500 | 服务器内部错误 |
| `50002` | 503 | 服务不可用 |
| `50003` | 504 | 上游服务超时 |

### 18.3 user-svc 错误码 (1xxxx)

| 错误码 | 说明 |
|--------|------|
| `41001` | 账号已存在 |
| `41002` | 验证码错误或过期 |
| `41003` | 密码格式不符 |
| `41004` | 账号或密码错误 |
| `41005` | 账号已禁用 |
| `41006` | 登录失败次数过多 |
| `41007` | Token过期 |
| `41008` | Token无效 |
| `41009` | 企业认证未通过 |
| `41010` | 成员数已达上限 |
| `41011` | 企业预算不足 |

### 18.4 script-gen-svc 错误码 (2xxxx)

| 错误码 | 说明 |
|--------|------|
| `42001` | 生成配额已用完 |
| `42002` | AI服务不可用 |
| `42003` | 生成任务超时 |
| `42004` | 输入内容不合规 |
| `42005` | 生成内容被安全拦截 |
| `42006` | Token预算不足 |

### 18.5 canvas-svc 错误码 (6xxxx)

| 错误码 | 说明 |
|--------|------|
| `46001` | 画布项目不存在 |
| `46002` | 分镜未完成，无法合成 |
| `46003` | 渲染失败 |
| `46004` | 导出队列已满 |
| `46005` | 无水印导出需要会员 |

### 18.6 trade-svc 错误码 (4xxxx)

| 错误码 | 说明 |
|--------|------|
| `44001` | 剧本已下架 |
| `44002` | 订单已过期 |
| `44003` | 支付失败 |
| `44004` | 已购买过该剧本 |
| `44005` | 余额不足 |
| `44006` | 提现金额低于最低限额 |

### 18.7 sop-svc 错误码 (7xxxx)

| 错误码 | 说明 |
|--------|------|
| `47001` | 生产准入未通过 |
| `47002` | 资产已锁定，无法修改 |
| `47003` | 版本冲突 |
| `47004` | AI失败次数超限 |

---

## 附录A：接口版本矩阵

| 版本 | 新增接口数 | 累计接口数 | 新增模块 |
|------|:---:|:---:|------|
| **V1.0** | 58 | 58 | auth(8) + user(5) + script-gen(9) + script-repo(14) + canvas(19) + notify(3) |
| **V1.1** | 42 | 100 | enterprise(9) + trade(12) + asset-market(15) + sop(6) |
| **V1.2** | 80 | 180 | SSO(1) + API Key(3) + batch-generate(1) + outpaint(1) + L4 lock(2) + continuity(2) + failure(2) + capacity(1) + promotion(1) + export advanced(4) + 🆕 nodes CRUD(5) + connections(3) + workflows(3) + script pipeline(4) + slash commands(1) + director-desk(3) + timeline full(4) + multimodal(1) + 🆕 agent orchestrate(5) + memory CRUD(5) + skill files(5) + providers(5) + event-graph(4) |
| **V1.3** | 45+ | 220+ | 🆕 独立Agent服务(7) + 画布增强API(12+) + 画布质量巡检(6) + canvas V1.3增强（node engine/downstream/duplicates/timeline-add-node/compose variants/image node ops/video node ops/audio node ops等） |

> **总计**：V1.0 = 58 | V1.1 = +42 | V1.2 = +80 | V1.3 = +45+ = **220+个API端点**

---

> **文档状态**：v1.3  
> **编写日期**：2026-06-15  
> **文档用途**：供前端开发、后端开发、测试工程师、第三方集成使用  
> **后续步骤**：生成 OpenAPI 3.0 YAML 文件 → 导入 Swagger/Apifox → Mock Server → 联调
