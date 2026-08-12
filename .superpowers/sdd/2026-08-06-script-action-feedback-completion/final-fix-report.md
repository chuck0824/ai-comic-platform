# Final Fix Report — Script Action Feedback Completion

- Date: 2026-08-06 (Asia/Shanghai)
- Workspace: `/Users/apple/Desktop/漫剧/平台/.worktrees/script-action-feedback`
- Branch: `codex/script-action-feedback-completion`
- Final-fix base: `5134a72`
- Implementation and executable tests: `88d545a` (`fix: close final script workflow gaps`)
- PRD and behavior evidence: `aa636dd` (`docs: sync final workflow evidence`)
- This report is committed in the final report-only commit recorded in the parent handoff.

## Result

All seven Important findings from the final branch review are addressed. The latest complete suite passes **89 / 89** with no skipped or failed tests. Targeted verification also passed in the Codex in-app Browser against the worktree-backed preview on port `62096`; the page console returned no error or warning entries.

## Finding-to-fix map

### 1. Stage confirmation guards

- Added one pure `evaluateStageConfirmation` / `beginStageConfirmation` path used by every stage footer and the final storyboard lock.
- Stages 0–7 now enforce their actual readiness requirements before creating a transition task. Source confirmation requires non-empty source text, `CONFIRMED` state, and a saved source artifact; adaptation also requires a selected hook and saved rule; review also rejects unresolved `HIGH`/`BLOCKER` issues.
- Blocked paths leave tasks, action results, billing entries, and artifacts exactly unchanged.

### 2. Review revision content

- Review issues now carry real `sceneId` and `blockId` locators.
- A local revision writes to that exact `scriptState.scenes` block, creates new script and review versions, preserves both old snapshots, appends a revision record, and marks only still-dependent downstream content stale.
- Missing or stale locators return actionable guidance with zero mutation.

### 3. 3001 pricing and settlement

- Pricing normalization now preserves the complete response snapshot: pricing version, vendors, group ratios, usable groups, supported endpoints, auto groups, and the selected model's metadata, endpoint, fixed/ratio/cache/image/audio fields, billing mode, and expression.
- Estimation follows the inspected 3001 fixed-price and cache-aware ratio formulas, including positive quota rounding. Demo models remain 0 points.
- When no same-source estimate endpoint is available, the result explicitly identifies `local-fallback-3001-snapshot`; it is not presented as a server quote.
- `SUCCEEDED` settles exactly once before user decision. The result then moves independently from `PENDING` to `ACCEPTED` or `DISCARDED`; discard retains the successful task, immutable pricing snapshot, billing entry, and revisitable result without changing the artifact.
- `99-生成与计费记录.md` now mirrors the immutable pricing snapshot. Falling back to demo models clears stale remote pricing metadata.

### 4. Archive read-only and recovery

- Source, upload/file, config, model, generation decision, version restore, retry, export, delivery, and stage-confirmation mutations are covered by the archive write guard.
- Preview, version/review diff, task inspection, filtering, selection, and storyboard view toggles remain available without writes.
- The workspace shows a visible `归档只读模式` banner and `aria-readonly=true` while archived.
- Project detail no longer offers a fake resume action. Only the separate confirmed restore action unarchives the project, and it records a `SUCCEEDED` manual task/result with 0 points.

### 5. Obsidian links and history

- `depends_on`, `affects`, project index, and impact log links resolve stable artifact IDs to real Markdown paths with aliases.
- Vault output derives chapter files/index, character index, source history, other artifact version files, and `91-版本历史.md` from actual data and snapshots.
- Version history and comparison surfaces read current artifact data plus `artifact.history`; restore creates a new version and never deletes history.
- Source Markdown preview renders from a cloned transient state and cannot create `SOURCE-001` or another persistent record.

### 6. Remaining visible actions

- Adaptation-rule save immediately versions `04-改编方案.md` and creates a zero-point manual result.
- Review diff is now a read-only inspection action and never creates an artifact version.
- Version restore, failed generation retry, and explicit static file-selection simulation create durable, honest results.
- Actual generation tasks retain the points and pricing snapshot needed by retry; the original failed task remains intact and the retry records `retryOf`.
- Script/storyboard episode selectors no longer fake EP02+ visual switches; unavailable episodes return `EPISODE_NOT_AVAILABLE` guidance. The static file picker explicitly states that it cannot invoke or mutate external system state.

### 7. Executable DOM evidence

- Added a minimal executable DOM harness that runs the complete inline script in a VM and triggers the actual delegated document click listener.
- Six DOM cases cover stage guard zero-writes, targeted review revision, success-time generation settlement plus discard, archive/restore, preview/Vault data, and the remaining rule/restore/retry/file/episode actions.
- This is executable interaction evidence, not a source-string proxy. The in-app Browser adds visible-state verification; exact in-memory counter assertions remain in the DOM harness.

## TDD evidence

The final dispatch followed RED → GREEN for each behavior group. Representative focused runs:

| Behavior | RED evidence | GREEN evidence |
|---|---|---|
| Stage footer guard | 2 / 2 failed before model API | 2 / 2 passed |
| Targeted review revision | 2 / 2 failed before locator writeback | 2 / 2 passed |
| Pricing and settlement core | 4 expected failures before implementation | all focused cases passed |
| Archive preview/recovery | 3 expected failures before implementation | all focused cases passed |
| Obsidian links/history | 3 expected failures before implementation | all focused cases passed |
| Remaining visible actions | 5 / 5 failed before implementation | 5 / 5 passed |
| Delegated-click DOM harness | 6 / 6 failed while the controller was intentionally unexposed | 6 / 6 passed after exposing the test boundary |
| Review diff read-only | 1 / 1 failed because it was still classified as a write | 1 / 1 passed |
| Settlement UI labels | 1 / 1 failed before explicit status/source/decision copy | 1 / 1 passed |
| Complete pricing/Vault snapshot | 2 / 2 failed before preservation/mirroring | 2 / 2 passed |
| Matrix current-contract correction | 1 / 1 failed on the superseded rule/diff rows | 1 / 1 passed |
| PRD final correction contract | 1 / 1 failed before PRD sync | 1 / 1 passed |
| Fallback metadata clearing | 1 / 1 failed before stale remote metadata was cleared | 1 / 1 passed |
| Review stale ordering | 1 / 1 failed because the concurrently updated review was stale | 1 / 1 passed |
| Real generation retry snapshot | 1 / 1 failed before task snapshot retention | 1 / 1 passed |
| Visible archive mode | 1 / 1 failed before the read-only banner | 1 / 1 passed |

## Final verification

```text
$ node --test tests/script-creation-prototype.test.cjs
tests 89
pass 89
fail 0
skipped 0
```

Additional checks:

- `git diff --check`: pass.
- Preview URL: `http://127.0.0.1:62096/eight-stage-workbench.html`.
- Preview process: PID `26955`, current working directory is this worktree.
- `curl` confirmed both `八阶段剧本创作工作台` and `WorkflowPrototypeHarness` from the served file.
- In-app Browser visible checks: stage guidance, exact review locator/result, explicit `SUCCEEDED / SETTLED / PENDING`, retained `DISCARDED` task, archive write guidance, separate restore confirmation, and recorded restore result.
- In-app Browser page console: `[]` for error/warn logs.
- Chrome and standalone Playwright were not used.

## Evidence boundary and concerns

- The existing `final-action-results.png` remains the screenshot for the earlier complete 45-row in-app Browser pass. The final fix round adds targeted fresh in-app Browser evidence and executable DOM/state evidence; it does not mislabel the older screenshot as a new capture.
- This remains a static prototype: model generation, system file selection, export, and canvas handoff intentionally do not create external effects. The UI now states these boundaries explicitly.
- No known functional concern remains within the requested static-prototype scope.
