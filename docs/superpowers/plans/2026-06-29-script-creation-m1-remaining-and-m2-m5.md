# Script Creation V7 — M1收尾 + M2–M5 实施计划

> 日期：2026-06-29
> 前置：M0 底座已完成 21 后端 + 5 前端测试通过
> 当前进度：M1 核心已交付（生成执行器 + 三Agent审校 + 前端生成集成），剩余 4 项待完成
>
> **2026-06-30 更新**：代码审查修复完成（提交 `25c8a39`）：
> - 10 Entity @TableName 与 DDL 对齐、5 Controller 接入 ProjectAccessService 鉴权
> - ContentBatchController 幂等键改为确定性键、StoryboardService 竞态条件修复
> - 新建 AiResponseParser 消除 200 行重复代码，注入全部 7 个 Service
> - parseJson 错误日志、next_promise 钩子补全、getNextSort 真实查询
> - compareSnapshots 扩展为 4 维度对比、String.formatted→String.format
> - 32 测试通过。整体完成度约63%，M2-M5 核心业务逻辑待实现

---

## 一、M1 剩余任务（4项，预计 2–3 天）

### M1-R1: A-tier 分镜（场景卡片 + Beat + 轻量主分镜）

**PRD 参考**：12.1 A/B/C 分镜 `F-02-80`

**目标**：用户在 destination 阶段选择"制作分镜"后，生成 A-tier 分镜并导入概念验证画布。

**文件**：

| 类型 | 文件 | 说明 |
|------|------|------|
| 新建 | `service/StoryboardService.java` | A-tier 分镜生成：场景戏剧目标卡、Beat、镜头/时长预算 |
| 新建 | `entity/StoryboardMaster.java` | 分镜 Master 实体（`cp_storyboard_masters` 表） |
| 新建 | `entity/StoryboardScene.java` | 场景卡片实体（`cp_storyboard_scenes` 表） |
| 新建 | `entity/StoryboardShot.java` | 镜头实体（`cp_storyboard_shots` 表） |
| 新建 | `mapper/ContentStoryboardMasterMapper.java` | |
| 新建 | `mapper/ContentStoryboardSceneMapper.java` | |
| 新建 | `mapper/ContentStoryboardShotMapper.java` | |
| 新建 | `controller/ContentStoryboardController.java` | `POST /api/v1/content-projects/{id}/storyboard/generate` 等 |
| 修改 | `db/schema-h2.sql` | 3 张分镜表（cp_ 前缀） |
| 修改 | `db/schema-mysql.sql` | 3 张分镜表（cp_ 前缀） |
| 修改 | `db/schema.sql` | 3 张分镜表（cp_ 前缀） |
| 新建 | `前端: views/content-project/StoryboardPanel.vue` | 分镜面板：场景导航、卡片视图、镜头列表 |
| 新建 | `前端: views/content-project/components/ShotCard.vue` | 单镜头卡片组件 |
| 修改 | `前端: ContentProjectWorkspace.vue` | 集成 storyboard stage |

**数据表**：

```sql
-- 实际表名使用 cp_ 前缀以区别于 canvas 模块的 storyboard_shots
CREATE TABLE IF NOT EXISTS cp_storyboard_masters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    content_unit_id BIGINT NOT NULL,
    tier VARCHAR(10) NOT NULL DEFAULT 'A',
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    total_shots INT DEFAULT 0,
    estimated_duration_sec INT DEFAULT 0,
    source_version_id BIGINT NOT NULL,
    locked_by BIGINT,
    locked_at TIMESTAMP,
    revision INT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cp_storyboard_scenes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    master_id BIGINT NOT NULL,
    scene_no INT NOT NULL,
    dramatic_goal TEXT,
    beat_description TEXT,
    location_id BIGINT,
    character_ids TEXT,
    duration_sec INT DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cp_sb_scene UNIQUE (master_id, scene_no)
);

CREATE TABLE IF NOT EXISTS cp_storyboard_shots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    scene_id BIGINT NOT NULL,
    master_id BIGINT NOT NULL,
    shot_no INT NOT NULL,
    shot_type VARCHAR(30),
    duration_sec INT DEFAULT 0,
    description TEXT,
    camera_action TEXT,
    dialogue_ref TEXT,
    visual_ref_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'draft',
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cp_sb_shot UNIQUE (master_id, shot_no)
);
```

**步骤**：

- [ ] **Step 1**: 添加 3 张分镜表到 schema 文件
- [ ] **Step 2**: 创建实体 + Mapper
- [ ] **Step 3**: 实现 `StoryboardService.generateATier(projectId, unitId)` — 调用 AiRouter 生成场景卡片+镜头
- [ ] **Step 4**: 实现场景导航、镜头增删改排 API
- [ ] **Step 5**: 实现 A-tier 分镜 JSON Schema 校验
- [ ] **Step 6**: 添加 `POST /content-projects/{id}/storyboard/generate` + CRUD 路由
- [ ] **Step 7**: 创建前端分镜面板（卡片视图 + 镜头列表）
- [ ] **Step 8**: 创建画布导入入口（概念验证，非批量生产）
- [ ] **Step 9**: 编写 StoryboardServiceTest
- [ ] **Step 10**: 端到端测试：选择分镜→生成 A-tier→进入画布

---

### M1-R2: 上传支持（TXT/DOCX → import_review）

**PRD 参考**：6.3 上传文稿 `F-02-00B`

**目标**：用户在创建时选择 uploaded 来源后，上传 TXT/DOCX → 异步解析 → 拆分确认 → 进入 import_review。

**文件**：

| 类型 | 文件 | 说明 |
|------|------|------|
| 新建 | `service/ContentUploadService.java` | 上传、安全扫描、异步解析、AI 提取 |
| 新建 | `entity/UploadFile.java` | 上传文件实体 |
| 新建 | `mapper/UploadFileMapper.java` | |
| 新建 | `controller/ContentUploadController.java` | 上传/解析/状态查询 |
| 修改 | `db/schema-h2.sql` | `content_upload_files` 表 |
| 修改 | `ContentProjectController.java` | 上传项目创建路由 |
| 新建 | `前端: views/content-project/UploadFlow.vue` | 上传流程页面 |
| 修改 | `前端: ContentProjectCreate.vue` | 启用上传方式卡片 |
| 添加依赖 | `pom.xml` | Apache POI (DOCX 解析)、Apache Tika (文本提取) |

**步骤**：

- [ ] **Step 1**: 添加 `content_upload_files` 表
- [ ] **Step 2**: 添加 Apache POI + Tika 依赖
- [ ] **Step 3**: 实现 `ContentUploadService` — 文件上传、TXT 直读、DOCX 解析、安全扫描（敏感词过滤）、异步任务状态
- [ ] **Step 4**: 实现 AI 提取：调用 AiRouter 从原文提取人物/关系/地点/章节
- [ ] **Step 5**: 实现用户拆分/合并/重排章节 API
- [ ] **Step 6**: 实现确认后自动创建 content_units + versions
- [ ] **Step 7**: 添加上传路由：`POST /api/v1/content-projects/upload`
- [ ] **Step 8**: 前端上传流程页面（拖拽上传、进度、预览、拆分界面）
- [ ] **Step 9**: 启用创建页面的"上传"方式卡片
- [ ] **Step 10**: 测试：上传→解析→AI提取→确认→进入 import_review

---

### M1-R3: 结构化输出校验（JSON Schema + 1次修复重试）

**PRD 参考**：10.3 结构化输出 `F-02-62`

**目标**：每个生成任务类型对应版本化 JSON Schema，AI 输出先校验，失败则修复重试一次。

**文件**：

| 类型 | 文件 | 说明 |
|------|------|------|
| 新建 | `schemas/content-generation-schemas/` | 各任务类型 JSON Schema 文件 |
| 新建 | `service/SchemaValidationService.java` | Schema 校验 + 修复重试 |
| 修改 | `ContentGenerationExecutor.java` | 集成校验流程 |
| 新建 | `test/.../SchemaValidationServiceTest.java` | |

**步骤**：

- [ ] **Step 1**: 为 `characters_generate`, `synopsis_generate`, `outline_generate`, `content_generate`, `review_generate`, `storyboard_a_generate` 各写 JSON Schema
- [ ] **Step 2**: 实现 `SchemaValidationService`：加载 Schema → 校验 → 失败则构造修复 prompt → 重试一次 → 仍失败则 job 标记 `failed`
- [ ] **Step 3**: 在 `ContentGenerationExecutor.execute()` 中集成校验流程
- [ ] **Step 4**: 禁止 mock 数据标记任务成功
- [ ] **Step 5**: 编写测试

---

### M1-R4: 两条 E2E 路径端到端验证

**目标**：确保 "跳过分镜→完成内容" 和 "选择分镜→进入画布" 两条路径完整可用。

**步骤**：

- [ ] **Step 1**: 编写 `ContentProjectM1IntegrationTest.java` — 覆盖完整短剧流程
- [ ] **Step 2**: 路径 A：创建→种子→角色→梗概→大纲→正文→审核→destination→skip 分镜→验证项目状态
- [ ] **Step 3**: 路径 B：同上到 destination→选择分镜→生成 A-tier→验证分镜已创建→导入画布概念验证
- [ ] **Step 4**: 验证刷新不丢稿、恢复正确阶段
- [ ] **Step 5**: 浏览器手动测试

---

## 二、M2 短剧完整版（预计 5–7 天）

### M2-1: 多集批量生成

**PRD 参考**：8.1 短剧快速创作、9.1 编辑器

- **批量生成**：一次性为 N 集创建 generation jobs，异步并行执行，前端展示进度
- **单集编辑器增强**：场景头、动作、对白、旁白、心理活动的结构化格式
- **AI 续写/润色/扩写/压缩**：在编辑器中选中文本触发
- **格式校验**：异常定位到行/段落
- **字数/时长/台词占比**：实时统计
- **新增/删除/拖拽重排**：稳定 ID 不随排序变化

### M2-2: 钩子系统

**PRD 参考**：9.2 钩子系统 `F-02-51`

- 每单元记录：`previous_promise`, `promise_payoff`, `opening_hook`, `mid_escalation`, `payoff_or_reversal`, `closing_hook`, `next_promise`
- 钩子强度评分 0.0–1.0
- 锁定字段（被下游依赖引用的钩子不可修改）

### M2-3: 连续性快照

**PRD 参考**：9.3 连续性 `F-02-52`

- 每单元快照：角色位置、受伤/状态、关系变化、道具位置、伏笔状态
- 连续性检查：AI 对比相邻单元快照，定位矛盾

### M2-4: 改编脚本 + 宣发物料

**PRD 参考**：11.1 改编 `F-02-70`、11.2 宣发 `F-02-71`

- 改编脚本：绑定源内容版本、逐集编辑、版本化、审核锁定
- 宣发：标题、封面文案、3秒钩子、切片脚本、评论引导、CTA

### M2-5: 项目列表完整恢复

- 从任意状态恢复项目
- 20/40/60/80 集关键用例测试
- 批量任务中断后可恢复

---

## 三、M3 长篇（预计 5–7 天）

| 模块 | 说明 |
|------|------|
| 角色关系 | 深层关系建模、动机、长期目标、知识边界、外貌、对白风格 |
| 情节任务 | 主线任务、阶段目标、障碍、代价、支线、角色专属任务、伏笔 |
| 总纲/卷/章 | 核心主线、阶段发展、卷级钩子、卷尾悬念、角色变化 |
| 地点 L0/L1 | L0=AI提取地点卡片、L1=区域层级/距离/交通/势力范围 |
| 时间线 | 事件时间线表、跨章节时间校验 |
| 伏笔表 | 伏笔埋设/回收状态追踪 |
| 长上下文 | 100章项目分页恢复、连续性跨章检查 |

---

## 四、M4 TVC（预计 4–6 天）

| 模块 | 说明 |
|------|------|
| 需求简报 | 品牌/产品信息、目标受众、预算 |
| 品牌/产品事实 | 必须表达/禁止表达、claims 证据状态 |
| 创意策略 | 3-5 创意角度、开场钩子、价值主张、多平台差异化 |
| 概念脚本 | 问题→产品介入→利益证明→CTA |
| 分秒脚本 | 时间码、画面、动作、旁白/对白、字幕、音乐/SFX、产品露出 |
| 多时长/多平台 | 15s/30s/60s 版本、横版/竖版 |
| TVC 三Agent | Hook Agent + Creative/Conversion Editor Agent + Director/Production Agent |

---

## 五、M5 生产级画布（预计 7–10 天）

| 模块 | 说明 |
|------|------|
| B/C-tier 分镜 | B-tier 导演意图/关系调度/声画关系、C-tier 抽卡表/视频表/失败策略 |
| 插件包 L0-L4 | 全套资产成熟度体系 |
| 生产门槛 | 源锁定、C-tier 锁定、插件包版本化、核心资产 L3+、成本估算+二次确认 |
| ProductionAgent | 调用画布 API 创建/连接/执行节点 |
| QualityAgent | 绑定质量问题到节点和资产版本、不自动采用 |
| Sync/Diff | 半耦合默认、强耦合自动计算 diff、上下游冲突处理 |
| Export Manifest | 采用资产打包导出 |

---

## 六、执行顺序建议

```
M1-R1 (A-tier分镜) ──┬── M1-R4 (E2E验证) ──→ M1 完成
M1-R2 (上传支持)  ──┤
M1-R3 (结构化校验) ──┘

M2-1 (多集批量) → M2-2 (钩子) → M2-3 (连续性) → M2-4 (改编/宣发) → M2-5 (恢复)

M3 (长篇) → M4 (TVC) → M5 (生产画布)
```

---

## 七、验证命令

```bash
# 每个 task 完成后运行
cd aicp-backend && mvn test          # 后端全部测试
cd aicp-frontend && npm run build    # 前端生产构建
node --test aicp-frontend/tests/*.test.js  # 前端单元测试
```
