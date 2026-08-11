# Task 10 Report — Static Acceptance Prototype Scene Assets

## Scope

- Updated `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`.
- Updated `tests/script-creation-prototype.test.cjs`.
- Kept the creative workflow at exactly eight stages. Scene assets are exposed as project-level shared infrastructure from the native workbench header and relevant stage content, not as a ninth stage.
- Did not modify native frontend or backend files.

## TDD evidence

### RED

Command:

```bash
node --test --test-name-pattern='scene asset|project scene asset' tests/script-creation-prototype.test.cjs
```

Observed before implementation: `0 pass / 4 fail`. The failures were the intended missing behaviors:

1. required scene-asset actions and four-layer labels were absent;
2. the model had no `sceneAssets` graph;
3. required action clicks opened no surface;
4. the Obsidian scene-asset index and master document did not exist.

### GREEN

The same focused command now passes `4 / 4`.

Full prototype suite:

```bash
node --test tests/script-creation-prototype.test.cjs
```

Result: `93 / 93` tests passed.

## Implemented behavior

- Added the `项目场景资产` entry while retaining the eight-stage rail and the existing contextual stage actions.
- Added executable demo surfaces for:
  - scene asset library and detail;
  - create scene master asset;
  - create scene variant;
  - convert a novel-analysis location into a scene asset;
  - bind a script scene instance to a master and variant;
  - inspect impact, refresh a STALE script binding, or keep a PINNED storyboard snapshot.
- Added the concrete linked demo:
  - `LOCATION-001` novel location;
  - `SCENE-ASSET-001` scene master;
  - `SCENE-VARIANT-001` variant;
  - `SCRIPT-SCENE-001` script scene instance;
  - immutable `SNAPSHOT-001` storyboard snapshot.
- Updating a scene master creates a new demo version, marks script instances `STALE`, and leaves the locked storyboard snapshot unchanged and `PINNED`.
- Script artifacts now include scene asset references; storyboard artifacts include locked scene snapshots.
- Added Obsidian demo output:
  - `04-场景资产/00-场景资产索引.md`;
  - `04-场景资产/SCENE-ASSET-001-林野出租屋.md`;
  - stable IDs, variants, script bindings, and locked snapshot references.
- Every newly added control opens a visible surface, updates current-page demo state, or presents actionable prerequisite guidance. Copy explicitly says the static demo does not persist to an external backend.

## Additional verification

- `git diff --check`: passed.
- Embedded controller extraction and `vm.Script` syntax validation: passed (`embedded JS syntax OK`).
- Local HTTP smoke at `http://127.0.0.1:62110/eight-stage-workbench.html`: `GET 200`, `HEAD 200`, and the served response contained the project scene asset entry.

## Known boundary

This is an acceptance prototype. State changes are intentionally limited to the current page session; no backend persistence is claimed. Native API persistence remains owned by the already implemented native workbench tasks.
