# Script Action Feedback Browser Behavior Matrix

- Date: 2026-08-06 (Asia/Shanghai)
- Browser: Codex in-app Browser (the user-selected browser)
- Preview: `http://localhost:62096/`
- Served source: `/Users/apple/Desktop/漫剧/平台/.worktrees/script-action-feedback/.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Original preview process: PID `60573` (the complete 45-row Browser run)
- Current preview process: PID `26955` from the same worktree; kept on port `62096`
- Original matrix result: **45 / 45 PASS**
- Console after the complete run: **0 errors, 0 warnings** (`tab.dev.logs`)
- Final visible result: `final-action-results.png` (ARCHIVED result, task `task-39`, 0 points)
- PNG signature: `89504e470d0a1a0a`; IHDR: `1280×720`
- Evidence boundary: the table retains the original complete 45-row Browser run and its screenshot. The final Important-fix round was verified separately with the Codex in-app Browser plus an executable delegated-click DOM harness; no Chrome or standalone Playwright substitution was used. The supplement below identifies current behavior that supersedes an original row.

The persistent-result column records the browser-visible Markdown path/version/task/points outcome or the explicitly verified zero-write outcome. Toast was never accepted as the sole success signal.

Exact sequential IDs that were not printed inside an individual generation-result modal were reconciled from adjacent Browser-observed anchors and the append-only model sequence: structure generation ended at `task-12`, the next visible manual result was scene `task-18`, so the five accepted AI edits are `task-13` through `task-17` and V2 through V6; the same reconciliation is used only where surrounding visible task/version anchors make the value deterministic.

| # | Stage | Action | Prerequisite | Browser-visible click result | Persistent result | Status |
|---:|---|---|---|---|---|---|
| 1 | 小说分析 | 编辑梗概 | 分析阶段可编辑 | 编辑弹层打开 | `03-小说分析/故事梗概.md` V1; `task-1`; SUCCEEDED; 0 积分 | PASS |
| 2 | 小说分析 | 新增事件 | 分析阶段可编辑 | 事件编辑弹层打开 | `03-小说分析/主要事件.md` V1; `task-2`; SUCCEEDED; 0 积分 | PASS |
| 3 | 小说分析 | 人物详情 | 人物库可用 | 人物列表与人物编辑弹层打开 | `03-小说分析/人物/CHAR-001-林野.md` V1; `task-3`; SUCCEEDED; 0 积分 | PASS |
| 4 | 小说分析 | 编辑世界观 | 分析阶段可编辑 | 世界观编辑弹层打开 | `03-小说分析/世界观.md` V1; `task-4`; SUCCEEDED; 0 积分 | PASS |
| 5 | 改编方案 | 重新生成当前产物 (blocked) | 尚无已保存改编产物 | 上轮 in-app Browser 实际点击 `action-guidance`: 请先保存当前阶段产物 | 可执行 WorkflowModel：before tasks=0, results=0, billing=0, artifacts=0; after blocked tasks=0, results=0, billing=0, artifacts=0 | PASS |
| 6 | 改编方案 | 选择高压开场 | 候选开场存在 | 选择弹层打开，HP2 呈选中态 | `04-改编方案.md` V1; `task-5`; SUCCEEDED; 0 积分 | PASS |
| 7 | 改编方案 | 新增改编规则 | 改编阶段可编辑 | 规则编辑弹层打开；final DOM harness 点击保存后打开持久结果 | fresh-state evidence: `04-改编方案.md`; artifactId=`ADAPT-001`; V2; `task-1`; SUCCEEDED; 0 积分 | PASS |
| 8 | 改编方案 | 确认改编方案 | 已选开场且存在规则 | 持久结果弹层打开 | `04-改编方案.md` V2; `task-6`; SUCCEEDED; 0 积分 | PASS |
| 9 | 改编方案 | 重新生成当前产物 (allowed) | 改编产物已保存 | 上轮 in-app Browser 实际点击：配置 → 进度 → 前后差异 → 采纳 | `04-改编方案.md`; artifactId=`ADAPT-001`; V3; `task-7`; 0 积分; after allowed tasks=1, results=1, billing=1, artifacts=1 | PASS |
| 10 | 结构化文字剧本 | 打开单集结构 | 已有分集结构 | EP-001 结构编辑弹层打开 | `05-分集结构/EP-001-单集结构.md` V1; `task-9`; 0 积分 | PASS |
| 11 | 结构化文字剧本 | 新增节拍 | 当前集可编辑 | 节拍编辑弹层打开 | `05-分集结构/EP-001-单集结构.md`; artifactId=`EP-001-STRUCTURE`; V2; `task-10`; SUCCEEDED; 0 积分 | PASS |
| 12 | 结构化文字剧本 | 重新生成节拍 | 已有节拍及结构产物 | 共享生成进度与节拍差异 | `05-分集结构/EP-001-单集结构.md`; artifactId=`EP-001-STRUCTURE`; V3; `task-11`; 0 积分 | PASS |
| 13 | 结构化文字剧本 | 重新生成当前产物 (blocked + allowed) | blocked 无产物；remediation 保存结构；allowed 结构产物存在 | blocked: 本修复轮 IAB finalize 后无法重连，由可执行 `evaluateActionPrecondition` 得 `STAGE_ARTIFACT_REQUIRED`；allowed: 上轮 in-app Browser 实际点击配置 → 进度 → 差异 → 采纳 | before tasks=0, results=0, billing=0, artifacts=0; after blocked tasks=0, results=0, billing=0, artifacts=0; after allowed tasks=1, results=1, billing=1, artifacts=1; `05-分集结构/EP-001-单集结构.md`; artifactId=`EP-001-STRUCTURE`; V4; `task-12`; 0 积分 | PASS |
| 14 | 剧本正文 | AI 编辑 / 续写选中段落 (blocked) | 未选正文块 | `action-guidance` 指明需先选择正文块，4 个块获可见聚焦 | 0 任务 / 0 积分 / 0 版本写入 | PASS |
| 15 | 剧本正文 | AI 编辑 / 续写选中段落 (allowed) | 已选正文块 | 生成进度与前后差异 | `06-剧本正文/EP-001-剧本正文.md`; artifactId=`EP-001-SCRIPT`; V2; `task-13`; 0 积分；下游过期 | PASS |
| 16 | 剧本正文 | 增强冲突 | 已选正文块 | 生成进度与前后差异 | `06-剧本正文/EP-001-剧本正文.md`; artifactId=`EP-001-SCRIPT`; V3; `task-14`; 0 积分；下游过期 | PASS |
| 17 | 剧本正文 | 精简对白 | 已选正文块 | 生成进度与前后差异 | `06-剧本正文/EP-001-剧本正文.md`; artifactId=`EP-001-SCRIPT`; V4; `task-15`; 0 积分；下游过期 | PASS |
| 18 | 剧本正文 | 改写语气 | 已选正文块 | 生成进度与前后差异 | `06-剧本正文/EP-001-剧本正文.md`; artifactId=`EP-001-SCRIPT`; V5; `task-16`; 0 积分；下游过期 | PASS |
| 19 | 剧本正文 | 检查角色一致性 | 已选正文块 | 生成进度与前后差异 | `06-剧本正文/EP-001-剧本正文.md`; artifactId=`EP-001-SCRIPT`; V6; `task-17`; 0 积分；下游过期 | PASS |
| 20 | 剧本正文 | 新增场景 | 正文阶段可编辑 | 场景编辑弹层打开 | `06-剧本正文/EP-001-剧本正文.md`; artifactId=`EP-001-SCRIPT`; `SCENE-002`; V7; `task-18`; SUCCEEDED; 0 积分 | PASS |
| 21 | 剧本正文 | 新增正文块 | 已有可编辑场景 | 正文块编辑弹层打开 | `06-剧本正文/EP-001-剧本正文.md`; artifactId=`EP-001-SCRIPT`; `BLOCK-005`; V8; `task-19`; SUCCEEDED; 0 积分 | PASS |
| 22 | 剧本正文 | 运行正文检查 | 已有正文 | 检查弹层显示 0 阻断 / 2 建议 | `06-剧本正文/EP-001-剧本正文.md`; artifactId=`EP-001-SCRIPT`; V9; `task-20`; 0 积分 | PASS |
| 23 | 剧本正文 | 导出正文 | 正文产物已保存 | 范围/格式/Markdown 预览后完成 | `06-剧本正文/EP-001-剧本正文.md`; artifactId=`EP-001-SCRIPT`; V9; `task-22`; Markdown; SUCCEEDED; 0 积分 | PASS |
| 24 | 剧本正文 | 重新生成当前产物 (blocked + allowed) | blocked 无产物；remediation 保存正文；allowed 正文产物存在 | blocked: 本修复轮 IAB finalize 后无法重连，由可执行 `evaluateActionPrecondition` 得 `STAGE_ARTIFACT_REQUIRED`；allowed: 上轮 in-app Browser 实际点击配置 → 进度 → 差异 → 采纳 | before tasks=0, results=0, billing=0, artifacts=0; after blocked tasks=0, results=0, billing=0, artifacts=0; after allowed tasks=1, results=1, billing=1, artifacts=1; `06-剧本正文/EP-001-剧本正文.md`; artifactId=`EP-001-SCRIPT`; V10; `task-23`; 0 积分；审核/分镜过期 | PASS |
| 25 | 审核修订 | 审核通过本集 (blocked) | 存在未解决 HIGH/BLOCKER | `action-guidance`; 2 个阻断项获可见聚焦 | 0 审核通过记录 / 0 版本写入 | PASS |
| 26 | 审核修订 | 筛选问题 | 已有审核问题 | 筛选弹层可选 BLOCKING + OPEN，列表保留该状态 | 当前审核视图持有筛选状态 | PASS |
| 27 | 审核修订 | 保存局部修订 | 已选审核问题 | 修订编辑弹层打开 | `07-审核修订/EP-001-审核记录.md`; artifactId=`EP-001-REVIEW`; 剧本 V11 + 审核 V1; `task-24`; SUCCEEDED; 0 积分 | PASS |
| 28 | 审核修订 | 对比修订前后 | 已选审核问题 | 修订前/后并列弹层直接读取审核记录；归档只读仍可查看 | 0 任务 / 0 计费 / 0 版本写入；原 Browser 写入行为已被 final fix 取代 | PASS |
| 29 | 审核修订 | 审核通过本集 (allowed) | HIGH/BLOCKER 均已解决 | 确认弹层显示可通过 | `07-审核修订/EP-001-审核记录.md` V5; `task-28`; SUCCEEDED; 0 积分 | PASS |
| 30 | 审核修订 | 重新生成当前产物 (blocked + allowed) | blocked 无产物；remediation 保存审核记录；allowed 审核产物存在 | blocked: 本修复轮 IAB finalize 后无法重连，由可执行 `evaluateActionPrecondition` 得 `STAGE_ARTIFACT_REQUIRED`；allowed: 上轮 in-app Browser 实际点击配置 → 进度 → 差异 → 采纳 | before tasks=0, results=0, billing=0, artifacts=0; after blocked tasks=0, results=0, billing=0, artifacts=0; after allowed tasks=1, results=1, billing=1, artifacts=1; `07-审核修订/EP-001-审核记录.md`; artifactId=`EP-001-REVIEW`; V6; `task-29`; 0 积分 | PASS |
| 31 | 文字分镜 | 空历史撤销 (blocked) | 分镜历史为空 | `action-guidance`: 当前没有可撤销操作 | 0 版本 / 0 任务写入 | PASS |
| 32 | 文字分镜 | 拆分镜头 (blocked) | 未选镜头 | `action-guidance` 引导选择镜头 | 0 版本 / 0 任务写入 | PASS |
| 33 | 文字分镜 | 合并镜头 (blocked) | 未选镜头 | `action-guidance` 引导选择镜头，3 行获可见聚焦 | 0 版本 / 0 任务写入 | PASS |
| 34 | 文字分镜 | 拆分镜头 (allowed) | 已选 `SHOT-001` | 结果显示 3 → 4 镜头 | `08-文字分镜/EP-001-文字分镜.md`; artifactId=`EP-001-STORYBOARD`; V1; 可撤销历史; `task-31`; SUCCEEDED; 0 积分 | PASS |
| 35 | 文字分镜 | 合并镜头 (allowed) | 已选镜头且总数≥2 | 结果显示 4 → 3 镜头 | `08-文字分镜/EP-001-文字分镜.md`; artifactId=`EP-001-STORYBOARD`; V2; 可撤销历史; `task-32`; SUCCEEDED; 0 积分 | PASS |
| 36 | 文字分镜 | 新增镜头 | 项目未归档 | 镜头编辑弹层打开，结果显示 4 镜头 | `08-文字分镜/EP-001-文字分镜.md`; artifactId=`EP-001-STORYBOARD`; V3; `task-33`; SUCCEEDED; 0 积分 | PASS |
| 37 | 文字分镜 | 切换卡片/表格 | 无业务前置 | 表格切换为 4 张可选镜头卡片 | 仅本地视图状态；0 任务 / 0 版本写入 | PASS |
| 38 | 文字分镜 | 连续性检查 | 已有分镜 | 角色、场景/道具、轴线三项通过 | `08-文字分镜/EP-001-文字分镜.md`; artifactId=`EP-001-STORYBOARD`; V4; `task-34`; 0 积分 | PASS |
| 39 | 文字分镜 | 重新生成当前产物 (blocked + allowed) | blocked 无产物；remediation 保存分镜；allowed 分镜产物存在 | blocked: 本修复轮 IAB finalize 后无法重连，由可执行 `evaluateActionPrecondition` 得 `STAGE_ARTIFACT_REQUIRED`；allowed: 上轮 in-app Browser 实际点击配置 → 进度 → 差异 → 采纳 | before tasks=0, results=0, billing=0, artifacts=0; after blocked tasks=0, results=0, billing=0, artifacts=0; after allowed tasks=1, results=1, billing=1, artifacts=1; `08-文字分镜/EP-001-文字分镜.md`; artifactId=`EP-001-STORYBOARD`; V5; `task-35`; 0 积分 | PASS |
| 40 | 文字分镜 / 交付 | 配置导出（用户原文“配置导图”） | 项目未归档 | 范围/版本/格式 → `export-result` | packagePath=`重生后我在三集内揭开命运系统_创作包_当前已确认版本_Obsidian Vault.zip`; 13 文件; `task-36`; 0 积分 | PASS |
| 41 | 全局交付 | 导出创作包 | 项目未归档 | 全局入口打开配置并显示 `export-result` | packagePath=`重生后我在三集内揭开命运系统_创作包_当前已确认版本_JSON.zip`; 13 文件; `task-37`; 0 积分 | PASS |
| 42 | 文字分镜 / 交付 | 创建画布项目 | 项目未归档且已有分镜 | 交接确认 → `canvas-result` | canvasProjectId=`CANVAS-DEMO-278041`; 4 镜头; 3 类资产; `task-38`; 0 积分 | PASS |
| 43 | 文字分镜 / 交付 | 完成并归档 | 项目未归档 | 确认 → `archive-result` | archiveTaskId=`task-39` (UI 未显示独立 archive entity ID); ARCHIVED / 只读; 0 积分; 产物/版本/任务/导出全保留 | PASS |
| 44 | 归档只读 | 归档后写操作（新增镜头） | 项目已 ARCHIVED | `PROJECT_ARCHIVED` guidance 说明只读与恢复路径 | 0 任务 / 0 版本 / 0 交付写入 | PASS |
| 45 | 归档只读 | 归档后切换分镜视图 | 本地查看动作 | 卡片仍可切回表格 | 仅本地视图状态；0 写入 | PASS |

## Dual-path assertions

- AI edit: unselected block produced guidance and zero writes; the selected-block path produced generation diff, acceptance, new versions and recorded tasks.
- Review approval: unresolved `HIGH` + `BLOCKER` produced guidance and two visible focus targets; after both were resolved, approval persisted V5 / `task-28`.
- Split shot: no selected shot produced guidance and zero writes; selected `SHOT-001` produced V1 / `task-31` and four shots.
- Merge shot: no selected shot produced guidance and zero writes; the selected-shot path produced V2 / `task-32` and three shots.
- Current-artifact regeneration: the executable model contract covers adaptation, structure, body, review and storyboard with the same guard/remediation/accept sequence. Each fresh state remains `tasks=0, results=0, billing=0, artifacts=0` after blocked evaluation; after remediation and accepted generation it is `tasks=1, results=1, billing=1, artifacts=1`, V2 / `task-1` / 0 points. Rows 13, 24, 30 and 39 pair this counter evidence with the prior IAB allowed-path observation instead of claiming a new Browser click.

## Runtime evidence

- `http://localhost:62096/` rendered the title **八阶段剧本创作工作台** from the current worktree.
- Browser console: `ERRORS=[]`, `WARNINGS=[]` after all 45 behavior rows.
- Screenshot was captured through the in-app Browser at the visible `archive-result` overlay and then opened from disk to verify that the PNG is readable and shows ARCHIVED, `task-39`, 0 points and retained records.

## Final Important-fix supplement

The original screenshot remains evidence for the complete 45-row pass. The following targeted checks were performed against the final implementation; exact mutation counters come from the executable Node/DOM harness because the Browser page scope is used only for visible-state inspection.

| Issue | Final behavior | Executable evidence | In-app Browser evidence |
|---|---|---|---|
| Stage confirmation | All eight footers call one confirmation guard; invalid confirmation creates no task, result, billing row, or artifact | Pure model tests enumerate stages 0–7; DOM click asserts exact zero counters | Stage 7 review footer opened “请先保存当前阶段产物” instead of starting a transition |
| Review revision | Issue carries `sceneId`/`blockId`; save changes that exact script block and versions script plus review artifacts with history | Pure model and DOM click tests assert `SCENE-001 / BLOCK-001`, before/after data, and both histories | Saved result displayed the exact locator, old/new text, script V2, review V2, `task-1`, 0 points |
| 3001 pricing and settlement | Fixed/ratio/cache pricing, complete model/group/vendor/endpoint snapshot, success-time idempotent settlement, independent accept/discard decision | Pricing, snapshot, Vault ledger, success/discard, and accept-idempotence tests | Result showed `SUCCEEDED`, `SETTLED`, `PENDING`, estimate source and points; after discard task center retained `SUCCEEDED` plus `DISCARDED` |
| Archive and recovery | Preview/history/view toggles stay read-only; every persistent mutation is blocked; recovery requires confirmation and creates a 0-point result | Pure guards plus DOM archive/recovery click sequence | Archived save opened `PROJECT_ARCHIVED` guidance; project detail showed read-only; confirmed restore produced `task-3`, SUCCEEDED, 0 points |
| Obsidian data | Links resolve real paths/aliases; chapter, character, source-version, version index and diff read actual artifact snapshots | Vault/link/history tests plus DOM Vault preview | Targeted review result displayed the real `07-审核修订/EP-001-审核记录.md` path |
| Remaining visible actions | Adaptation-rule save, version restore, failed-task retry, explicit file simulation, and unavailable episode guidance all have honest results | Pure action tests and one delegated-click DOM sequence | Static file picker copy explicitly states no system/external file effect |
| Browser boundary | Browser interactions are real IAB clicks; exact state assertions use the full inline script and actual delegated document handler in a VM DOM | Six DOM interaction cases pass | Final targeted run console: `[]` for error/warn logs |
