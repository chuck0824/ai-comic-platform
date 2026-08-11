# Task 12 Report — Non-Browser Verification

## Status

PARTIAL — non-browser verification executed; repository-wide backend and frontend suites retain acknowledged baseline failures. Browser acceptance remains owned by the parent Task 12 flow.

## Scope

Executed only the non-browser plan steps at HEAD `06d2909`:

- backend `mvn test`;
- frontend `npm test`;
- frontend `npm run build`;
- root `node --test tests/*.cjs`;
- `git status --short`, `git diff --check`, and recent log inspection.

No product or Task 11 documentation file was modified. Vite build artifacts were restored/cleaned after verification because the configured output directory is the tracked backend static directory.

## Results

| Check | Result | Evidence |
|---|---|---|
| Root CJS suite | PASS | 103 tests, 103 pass, 0 fail |
| Frontend build | PASS | Vite transformed 2,001 modules; build completed in 6.64s |
| Frontend test suite | BASELINE FAIL | 223 tests: 222 pass, 1 fail |
| Backend Maven suite | BASELINE FAIL | 353 tests: 3 failures, 66 errors, 0 skipped; Maven exit 1 |
| Git hygiene | PASS | final `git status --short` empty; `git diff --check` exits 0 |

## Frontend baseline failure

Only `tests/agent-config-state.test.js` fails:

```text
new agent wizard starts by selecting a blueprint
expected identity: null
actual identity: { name: '', description: '' }
```

This is the same acknowledged pre-existing failure recorded in Tasks 9–11. It does not import or exercise the script workbench, scene-asset, candidate, Obsidian, or Task 11 document files.

## Backend baseline failures

Surefire produced 105 XML suite reports, totaling 353 tests. Failure classes group into the existing full-suite isolation/schema baseline:

1. Shared H2 database/context reinitialization attempts to insert `dev-admin-001` again, causing a unique-key violation. This prevents later application contexts from loading and cascades into enterprise, trade, generation-decision, project scene-asset, and storyboard-snapshot suites.
2. `AssetMarketLifecycleE2ETest` queries `canvas_projects.canvas_mode`, but the shared H2 schema used by that suite does not expose `CANVAS_MODE`.
3. SOP service tests call code paths that now require `SecurityUtil.requireCurrentUserId()` without installing an authenticated test context. They produce three assertion failures and associated “未登录或Token已过期” errors.
4. Spring's application-context failure threshold turns the first context initialization failure into additional skipped-load errors in later suites.

Failing suite totals from current Surefire reports:

- `EnterpriseGovernanceSchemaTest`: 6 errors;
- SOP nested suites: 3 failures and 5 errors;
- `ProjectSceneAssetLifecycleE2ETest`: 17 errors after context load failure;
- `StoryboardSceneAssetSnapshotE2ETest`: 17 errors after context load failure;
- `GenerationJobDecisionE2ETest`: 4 errors after context load failure;
- trade schema/service suites: 16 errors;
- `AssetMarketLifecycleE2ETest`: 1 schema error.

These causes match the baseline already recorded before Task 12. The Task 1–9 reports contain isolated green evidence for scene lifecycle, snapshot, generation decisions, downstream contexts and frontend workbench contracts. This verification did not modify tests or implementation to mask the full-suite baseline.

## Git evidence

Recent task commits at verification time:

```text
06d2909 docs: synchronize script workbench scene assets
4448ed0 fix: complete scene asset prototype interactions
e2c1e48 feat: add scene assets to workbench prototype
54420db fix: surface generation decision conflicts
6ea44b1 fix: unify generation version visibility
7011661 fix: isolate generation candidate adoption
9b881fc fix: persist generation result decisions
1524bb1 fix: complete native workbench integration
48f2b1c fix: harden native script workbench integration
dd679e5 docs: finalize task 9 verification report
83711b6 feat: merge eight stage workbench into script gen
488aaaa fix: align asset application wire and migration contracts
```

## Conclusion

The root contract/prototype/document suite and production frontend build are green. The full repository cannot be reported as globally green because of the explicit backend and frontend baseline failures above. No new Task 12 non-browser defect was isolated, so no source fix was attempted. Browser route acceptance and final reviewer adjudication remain pending.

## Browser acceptance follow-up — model provenance and rail labels (2026-08-11)

### Defects reproduced

The live creation-settings stage claimed `3001 模型已连接`, `3001 平台`, and 3001 usage settlement for the response from `GET /api/v1/ai/models`. Source tracing established that this endpoint reads the classpath `ai-models.yml` through `AiModelRegistry`; it is not a remote 3001 catalog, and its credit estimate is not authoritative 3001 accounting. The workflow rail also used the non-authoritative labels `结构化剧本` and `审阅与修订`.

Both regressions were first locked with failing tests:

- a local or provenance-free catalog must not be promoted to remote 3001 mode;
- remote catalog provenance and authoritative 3001 billing provenance are evaluated independently;
- the eight stage keys remain stable while labels must be `结构化文字剧本` and `审核修订`.

### Implemented boundary

- `AiModelController` now declares `catalog_provenance=local_registry`, `billing_provenance=local_estimate`, and `accounting_authoritative=false`.
- The frontend defaults unspecified provenance to local, not remote. Only explicit `3001`/`remote_3001` catalog provenance shows the 3001 connection badge.
- A remote catalog shows 3001 settlement language only when `billing_provenance` is a supported 3001 value **and** `accounting_authoritative === true`; omission defaults to `false`. All other estimates are labelled `仅供参考，非 3001 账务结算`.
- Empty or failed catalogs use the explicit built-in demo fallback without implying that a 3001 connection was attempted.
- Every catalog refresh rebinds the selected ID to the newly normalized model object and replaces or re-fetches its estimate, so persisted remote metadata cannot survive a same-ID switch to the local catalog.
- Rail stage keys and routes were not changed. Rail labels, transition guidance, and structured-script generation subtask copy consistently use `结构化文字剧本` / `审核修订`; focused tests reject the legacy aliases.

No 3001 remote adapter or accounting integration was fabricated by this fix.

### Fresh verification

| Check | Result | Evidence |
|---|---|---|
| Backend focused contract | PASS | `mvn -Dtest=AiModelControllerTest test`: 1 test, 0 failures/errors |
| Frontend focused + SFC contracts | PASS | 38 tests, 38 pass, 0 fail |
| Frontend production build | PASS | Vite transformed 2,001 modules; build completed in 5.93s |
| Diff hygiene | PASS | generated static build artifacts restored/removed; `git diff --check` clean |

The verification above proves the source and build contracts. A running 8080 process must be restarted or redeployed from the new build before the live browser can display the corrected labels.
