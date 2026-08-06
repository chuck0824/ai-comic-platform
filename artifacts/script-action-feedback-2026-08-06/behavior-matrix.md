# Script Action Feedback Browser Behavior Matrix

- Date: 2026-08-06 (Asia/Shanghai)
- Browser: Codex in-app Browser (the user-selected browser)
- Preview: `http://localhost:62096/`
- Served source: `/Users/apple/Desktop/漫剧/平台/.worktrees/script-action-feedback/.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Preview process: PID `60573` (Browser run used this worktree-backed process)
- Result: **45 / 45 PASS**
- Console after the complete run: **0 errors, 0 warnings** (`tab.dev.logs`)
- Final visible result: `final-action-results.png` (ARCHIVED result, task `task-39`, 0 points)

The persistent-result column records the browser-visible Markdown path/version/task/points outcome or the explicitly verified zero-write outcome. Toast was never accepted as the sole success signal.

| # | Stage | Action | Prerequisite | Browser-visible click result | Persistent result | Status |
|---:|---|---|---|---|---|---|
| 1 | 小说分析 | 编辑梗概 | 分析阶段可编辑 | 编辑弹层打开 | `03-小说分析/故事梗概.md` V1; `task-1`; SUCCEEDED; 0 积分 | PASS |
| 2 | 小说分析 | 新增事件 | 分析阶段可编辑 | 事件编辑弹层打开 | `03-小说分析/主要事件.md` V1; `task-2`; SUCCEEDED; 0 积分 | PASS |
| 3 | 小说分析 | 人物详情 | 人物库可用 | 人物列表与人物编辑弹层打开 | `03-小说分析/人物/CHAR-001-林野.md` V1; `task-3`; SUCCEEDED; 0 积分 | PASS |
| 4 | 小说分析 | 编辑世界观 | 分析阶段可编辑 | 世界观编辑弹层打开 | `03-小说分析/世界观.md` V1; `task-4`; SUCCEEDED; 0 积分 | PASS |
| 5 | 改编方案 | 重新生成当前产物 (blocked) | 尚无已保存改编产物 | `action-guidance`: 请先保存当前阶段产物 | 0 任务 / 0 积分 / 0 版本写入 | PASS |
| 6 | 改编方案 | 选择高压开场 | 候选开场存在 | 选择弹层打开，HP2 呈选中态 | `04-改编方案.md` V1; `task-5`; SUCCEEDED; 0 积分 | PASS |
| 7 | 改编方案 | 新增改编规则 | 改编阶段可编辑 | 规则编辑弹层打开 | 规则计数从 1 变为 2，确认时与方案一起写入 | PASS |
| 8 | 改编方案 | 确认改编方案 | 已选开场且存在规则 | 持久结果弹层打开 | `04-改编方案.md` V2; `task-6`; SUCCEEDED; 0 积分 | PASS |
| 9 | 改编方案 | 重新生成当前产物 (allowed) | 改编产物已保存 | 配置 → 进度 → 前后差异 → 采纳 | 新版本; `task-7`; 0 积分; 任务中心可回访 | PASS |
| 10 | 结构化文字剧本 | 打开单集结构 | 已有分集结构 | EP-001 结构编辑弹层打开 | `05-分集结构/EP-001-单集结构.md` V1; `task-9`; 0 积分 | PASS |
| 11 | 结构化文字剧本 | 新增节拍 | 当前集可编辑 | 节拍编辑弹层打开 | 结构 Markdown V2; `task-10`; SUCCEEDED; 0 积分 | PASS |
| 12 | 结构化文字剧本 | 重新生成节拍 | 已有节拍及结构产物 | 共享生成进度与节拍差异 | 采纳后新结构版本、任务、0 积分 | PASS |
| 13 | 结构化文字剧本 | 重新生成当前产物 | 结构产物已保存 | 配置 → 进度 → 差异 → 采纳 | 新结构版本、任务、0 积分 | PASS |
| 14 | 剧本正文 | AI 编辑 / 续写 (blocked) | 未选正文块 | `action-guidance` 指明需先选择正文块，4 个块获可见聚焦 | 0 任务 / 0 积分 / 0 版本写入 | PASS |
| 15 | 剧本正文 | 续写选中段落 | 已选正文块 | 生成进度与前后差异 | 采纳后正文新版本、任务、0 积分；下游过期 | PASS |
| 16 | 剧本正文 | 增强冲突 | 已选正文块 | 生成进度与前后差异 | 采纳后正文新版本、任务、0 积分；下游过期 | PASS |
| 17 | 剧本正文 | 精简对白 | 已选正文块 | 生成进度与前后差异 | 采纳后正文新版本、任务、0 积分；下游过期 | PASS |
| 18 | 剧本正文 | 改写语气 | 已选正文块 | 生成进度与前后差异 | 采纳后正文新版本、任务、0 积分；下游过期 | PASS |
| 19 | 剧本正文 | 检查角色一致性 | 已选正文块 | 生成进度与前后差异 | 采纳后正文新版本、任务、0 积分；下游过期 | PASS |
| 20 | 剧本正文 | 新增场景 | 正文阶段可编辑 | 场景编辑弹层打开 | `SCENE-002`; 正文 V7; `task-18`; SUCCEEDED; 0 积分 | PASS |
| 21 | 剧本正文 | 新增正文块 | 已有可编辑场景 | 正文块编辑弹层打开 | `BLOCK-005`; 正文 V8; `task-19`; SUCCEEDED; 0 积分 | PASS |
| 22 | 剧本正文 | 运行正文检查 | 已有正文 | 检查弹层显示 0 阻断 / 2 建议 | 检查结果关联正文新版本及可回访任务 | PASS |
| 23 | 剧本正文 | 导出正文 | 正文产物已保存 | 范围/格式/Markdown 预览后完成 | Markdown 导出; 正文 V9; `task-22`; SUCCEEDED; 0 积分 | PASS |
| 24 | 剧本正文 | 重新生成当前产物 | 正文产物已保存 | 配置 → 进度 → 差异 → 采纳 | 正文新版本、任务、0 积分；审核/分镜过期 | PASS |
| 25 | 审核修订 | 审核通过本集 (blocked) | 存在未解决 HIGH/BLOCKER | `action-guidance`; 2 个阻断项获可见聚焦 | 0 审核通过记录 / 0 版本写入 | PASS |
| 26 | 审核修订 | 筛选问题 | 已有审核问题 | 筛选弹层可选 BLOCKING + OPEN，列表保留该状态 | 当前审核视图持有筛选状态 | PASS |
| 27 | 审核修订 | 保存局部修订 | 已选审核问题 | 修订编辑弹层打开 | 剧本 V11 + 审核 V1; `task-24`; SUCCEEDED; 0 积分 | PASS |
| 28 | 审核修订 | 对比修订前后 | 已选审核问题 | 修订前/后并列弹层打开 | 审核记录创建新版本与可回访结果 | PASS |
| 29 | 审核修订 | 审核通过本集 (allowed) | HIGH/BLOCKER 均已解决 | 确认弹层显示可通过 | `07-审核修订/EP-001-审核记录.md` V5; `task-28`; SUCCEEDED; 0 积分 | PASS |
| 30 | 审核修订 | 重新生成当前产物 | 审核产物已保存 | 配置 → 进度 → 差异 → 采纳 | 审核新版本、任务、0 积分 | PASS |
| 31 | 文字分镜 | 空历史撤销 (blocked) | 分镜历史为空 | `action-guidance`: 当前没有可撤销操作 | 0 版本 / 0 任务写入 | PASS |
| 32 | 文字分镜 | 拆分镜头 (blocked) | 未选镜头 | `action-guidance` 引导选择镜头 | 0 版本 / 0 任务写入 | PASS |
| 33 | 文字分镜 | 合并镜头 (blocked) | 未选镜头 | `action-guidance` 引导选择镜头，3 行获可见聚焦 | 0 版本 / 0 任务写入 | PASS |
| 34 | 文字分镜 | 拆分镜头 (allowed) | 已选 `SHOT-001` | 结果显示 3 → 4 镜头 | 分镜 V1; 可撤销历史; `task-31`; SUCCEEDED; 0 积分 | PASS |
| 35 | 文字分镜 | 合并镜头 (allowed) | 已选镜头且总数≥2 | 结果显示 4 → 3 镜头 | 分镜 V2; 可撤销历史; `task-32`; SUCCEEDED; 0 积分 | PASS |
| 36 | 文字分镜 | 新增镜头 | 项目未归档 | 镜头编辑弹层打开，结果显示 4 镜头 | 分镜 V3; `task-33`; SUCCEEDED; 0 积分 | PASS |
| 37 | 文字分镜 | 切换卡片/表格 | 无业务前置 | 表格切换为 4 张可选镜头卡片 | 仅本地视图状态；0 任务 / 0 版本写入 | PASS |
| 38 | 文字分镜 | 连续性检查 | 已有分镜 | 角色、场景/道具、轴线三项通过 | 检查结果关联分镜新版本及可回访任务 | PASS |
| 39 | 文字分镜 | 重新生成当前产物 | 分镜产物已保存 | 配置 → 进度 → 差异 → 采纳 | 分镜新版本、任务、0 积分 | PASS |
| 40 | 文字分镜 / 交付 | 配置导出（用户原文“配置导图”） | 项目未归档 | 范围/版本/格式 → `export-result` | Obsidian Vault; 13 文件; `task-36`; 0 积分; 包路径可见 | PASS |
| 41 | 全局交付 | 导出创作包 | 项目未归档 | 全局入口打开配置并显示 `export-result` | JSON / 全部有效产物; 13 文件; `task-37`; 0 积分 | PASS |
| 42 | 文字分镜 / 交付 | 创建画布项目 | 项目未归档且已有分镜 | 交接确认 → `canvas-result` | 画布项目 ID; 4 镜头; 3 类资产; `task-38`; 0 积分 | PASS |
| 43 | 文字分镜 / 交付 | 完成并归档 | 项目未归档 | 确认 → `archive-result` | ARCHIVED / 只读; `task-39`; 0 积分; 产物/版本/任务/导出全保留 | PASS |
| 44 | 归档只读 | 归档后写操作（新增镜头） | 项目已 ARCHIVED | `PROJECT_ARCHIVED` guidance 说明只读与恢复路径 | 0 任务 / 0 版本 / 0 交付写入 | PASS |
| 45 | 归档只读 | 归档后切换分镜视图 | 本地查看动作 | 卡片仍可切回表格 | 仅本地视图状态；0 写入 | PASS |

## Dual-path assertions

- AI edit: unselected block produced guidance and zero writes; the selected-block path produced generation diff, acceptance, new versions and recorded tasks.
- Review approval: unresolved `HIGH` + `BLOCKER` produced guidance and two visible focus targets; after both were resolved, approval persisted V5 / `task-28`.
- Split shot: no selected shot produced guidance and zero writes; selected `SHOT-001` produced V1 / `task-31` and four shots.
- Merge shot: no selected shot produced guidance and zero writes; the selected-shot path produced V2 / `task-32` and three shots.

## Runtime evidence

- `http://localhost:62096/` rendered the title **八阶段剧本创作工作台** from the current worktree.
- Browser console: `ERRORS=[]`, `WARNINGS=[]` after all 45 behavior rows.
- Screenshot was captured through the in-app Browser at the visible `archive-result` overlay and then opened from disk to verify that the PNG is readable and shows ARCHIVED, `task-39`, 0 points and retained records.
