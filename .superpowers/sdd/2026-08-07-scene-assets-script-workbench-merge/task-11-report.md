# Task 11 Report — PRD、Obsidian、模型积分与场景资产文档同步

## Status

DONE

## 交付范围

- 更新 `漫剧视频创作平台_PRD.md`。
- 创建 `剧本创作页面逻辑盘点与补充清单.md`。
- 更新 Obsidian/模型/积分设计、创作圣经设计和创作/仓库流转设计。
- 创建开发说明 `docs/剧本创作模块_场景资产与八阶段融合说明.md`。
- 创建文档覆盖测试 `tests/script-workbench-docs.test.cjs`。

六份文档已统一为同一产品合同：`/script-gen` 四入口启动台、`/script-gen/:projectId/workspace` 生产原生工作台；创作设置至文字分镜的八阶段；场景母资产、场景变体、剧本场景实例、分镜场景快照四层模型；V17 迁移事实；Obsidian 稳定链接；生成候选隔离与服务端优先采纳/放弃。3001 权威模型目录和积分预冻结/结算/退款被明确标记为 P0 延期目标，不再与当前本地 registry/BFF/估算混写。

## TDD 证据

### RED

命令：

```bash
node --test tests/script-workbench-docs.test.cjs
```

初始结果：`0 / 7` 通过。七项失败分别证明：目标文档缺失、八阶段/场景四层不一致、路由与静态边界未统一、演示模型零积分条件不严、候选生命周期/V17/API/操作追溯缺失、异常页面状态未覆盖。

### GREEN

初次交付同一命令结果为 `7 / 7` 通过。

### Review fix 1 RED / GREEN

针对第一轮审查提出的五项事实偏差，先新增四组契约断言；第一次执行为 `6 / 10` 通过、`4 / 10` 失败，分别锁定：当前实现与目标计费合同未分离、API 状态枚举错误、场景变体/快照/归档合同不符、组件/V16/错误码来源不准确。完成六文档修订后，同一命令为 `10 / 10` 通过。

第二轮审查先增加两组直接读取 `GenerationJobView`、`generationResultPersistence.js`、`ContentGenerationJobService`、V16/V17 SQL 的交叉约束；RED 为 `10 / 12`，捕获生成任务响应虚构 diff、前后端冲突码混写及 V16/V17 DDL 语法混写。修订融合说明后 GREEN 为 `12 / 12`。最终覆盖：

1. 六文档八阶段顺序与场景四层；
2. 生产原生与静态演示边界；
3. 当前模型/估算兼容层与 3001 P0 账务目标的边界；
4. API `pending/processing/completed/failed/cancelled`、UI `queued/running` 映射及 candidate/accepted/discarded；
5. V17、API、Obsidian、STALE/PINNED 与部署回滚；
6. 全部可见业务动作的前置、结果、Markdown、影响、API 和测试追溯；
7. 加载、空、错误、归档、停用、过期、锁定、权限与重试状态；
8. 变体操作 append 资产版本、仅新增其他变体可语义等价保持 CURRENT；
9. `scene_variant_version`、快照 JSON 与 storyboard version 锁定语义；
10. 真实组件、V16 五列/两个索引、错误码来源和“完成并归档”的实际行为；
11. `GenerationJobView` 真实字段、无 diff，以及前端 `IN_FLIGHT_CONFLICT` 与后端 `PARAM_INVALID`/`EDIT_CONFLICT` 的边界；
12. V16 保留 `IF NOT EXISTS` 的兼容风险、V17 普通 DDL 事实。

## doc-review 九项核查报告

**初查与审查问题：9 类（高 5 / 中 3 / 低 1）；修改后未留未解释的口径冲突。**

| # | 类别 | 初查问题 | 修订结果 | 严重 |
|---|---|---|---|---|
| 1 | 前后对照 | 六文档把当前本地模型/估算与 3001 权威目录、结算目标混成已实现能力 | 六文档增加“当前实现 / 目标合同（P0 延期）”，本地值明确不可作账务证据 | 高 |
| 2 | 逻辑去重 | 路由、场景、模型与积分规则散落；API 和 UI 状态枚举重复且矛盾 | 以融合说明为开发事实源；统一 API `pending/processing` 与 UI `queued/running` 映射 | 高 |
| 3 | 基础错误 | 组件名、`scene_variant_version_id`、V16 内容、生成响应 diff 及“项目归档”写错 | 改为真实组件/字段/DTO；V16 五列/两个索引；仅锁分镜 | 高 |
| 4 | 用词 | completed、accepted、完成、锁定、归档及账务“实际积分”混写 | 区分候选终态、结果决定、本地完成、项目生命周期和 3001 结算事实 | 高 |
| 5 | 表述余地 | 静态演示、本地估算和 P0 目标被写成生产完成；仅版本号变化就断言 STALE | 明确证据边界；补充 semantic equivalent，只有真实语义变化才使未锁消费者过期 | 高 |
| 6 | 结构层级 | 缺“当前/目标”判断层，错误码未标来源 | 各摘要加入实施边界；融合说明按路由→数据→API→追溯→部署组织并新增来源列 | 中 |
| 7 | 描述准确性 | 快照被描述为独立 fingerprint/锁定列，变体操作被描述为不增母资产版本；V16/V17 语法和冲突码来源混写 | 按 DTO/Service/SQL 改为快照 JSON、storyboard version 锁定、变体 append；区分前端 guard 与后端错误；记录 V16 方言风险 | 中 |
| 8 | 敏感信息 | 模型来源描述可能诱导浏览器直连管理端或把本地记录当真实流水 | 保留同源 BFF、无密钥规则；明确权威账务只能来自 3001 | 中 |
| 9 | 确认落实 | 两轮审查事实未被源码交叉测试约束 | 共新增六组防回归测试，其中两组直接读取 DTO、前后端服务与迁移 SQL | 低 |

## 实现事实与延期边界

- 当前生产原生：八阶段装配、项目场景资产、稳定脚本场景 target key、分镜快照、V17、生成 candidate 隔离与服务端优先决定；任务 API 为 `pending → processing → completed | failed | cancelled`。
- 当前兼容层：`/api/v1/ai/models`、本地 registry 与 `/api/v1/credits/estimate`/执行器估算支撑 UI；不代表 3001 权威模型目录或账务流水。
- 当前静态演示：场景四层交互、当前页 Obsidian 预览和操作样例；不声称写入生产后端。
- 当前“完成并归档”：锁定 storyboard version，并记录本地八阶段完成；不归档 content project。
- P0 延期：3001 权威模型目录、预冻结/结算/退款，全部 Obsidian 文件真实落盘，以及尚无安全模型合同时的阶段生成适配。适配缺失时原生页面明确失败，不合成成功。

## 验证结果

- `node --test tests/script-workbench-docs.test.cjs`：`12 / 12` 通过。
- `node --test tests/script-creation-prototype.test.cjs`：`96 / 96` 通过。
- Markdown 结构脚本：六份文档标题层级、本地链接和表格列数全部通过。
- 术语脚本：六份文档均覆盖八阶段与场景四层。
- 矛盾扫描：未发现“六步主流程”“九阶段主流程”、旧 `scene_variant_version_id`/`SceneAssetDrawer.vue`、生成 API 使用 queued/running、storyboard lock + project archive 或项目切换 ARCHIVED 的肯定式表述。
- `git diff --check`：通过。

## 说明

PRD 保留阶段流转页面的 `QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELED` 与生成进度 UI 的 queued/running 显示映射；生产内容生成 API 以 `pending/processing/completed/failed/cancelled` 及 candidate disposition 为事实，两者已明确区分。
