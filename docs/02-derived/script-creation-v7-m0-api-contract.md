# Script Creation V7.2 API Contract

> **更新（2026-07-04）**：后续 M2-M5 API 规划详见 `docs/superpowers/plans/`。
> 
> **更新（2026-07-05）** `[superpowers 更新 V1.8]`：Canvas 生产内核 API 详见 `docs/superpowers/specs/2026-07-05-canvas-production-kernel-completion-design.md` Section 11。本文件为脚本创作 API 契约，Canvas 域 API 以该设计为准。
> 
> **已关联 API 域**：
> - 创作圣经（`/api/v1/creative-bible`）→ `docs/superpowers/specs/2026-07-02-script-creation-creative-bible-design.md`
> - Agent 会话（`/api/v1/agent/sessions`）→ `docs/superpowers/specs/2026-07-02-agent-session-completion-design.md`
> - 交易市场（`/api/v1/trade`）→ `docs/superpowers/specs/2026-07-02-script-trading-market-completion-design.md`
> - 专业分镜（`/api/v1/storyboards`）→ `docs/superpowers/specs/2026-06-30-storyboard-professional-editor-redesign.md`
> 
> **2026-07-04 新增 API 域** `[superpowers 更新 V1.7]`：
> - 统一任务事件中心（`/api/v1/task-center`、`/api/v1/ops/task-center`）→ `2026-07-04-unified-task-event-center-design.md`
> - 资产工作台（`/api/v1/assets/workbench`、`/api/v1/assets/history`）→ `2026-07-04-asset-generation-history-workbench-design.md`
> - Agent 配置中心（`/api/v1/agent/blueprints`、`/api/v1/agent/definitions`、`/api/v1/agent/versions`）→ `2026-07-04-user-configurable-agent-center-design.md`
> - 企业工作台扩展（`/api/v1/enterprise/**` BFF→3001）→ `2026-07-04-enterprise-workbench-completion-design.md`
> - 独立空白画布（`POST /api/v1/canvas/projects` 放行 nullable）→ `2026-07-04-standalone-blank-canvas-design.md`
> - 生产 SOP 完成（`/api/v1/sop/**` 扩展）→ `2026-07-04-production-sop-completion-design.md`

## Implemented Endpoints

### Content Projects
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/content-projects` | Create project |
| GET | `/api/v1/content-projects` | List user's projects (cursor pagination) |
| GET | `/api/v1/content-projects/{id}` | Get project detail |
| PATCH | `/api/v1/content-projects/{id}` | Update project name (optimistic lock) |
| PUT | `/api/v1/content-projects/{id}/resume-position` | Save workflow resume position |

### Members
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/content-projects/{id}/members` | List members |
| POST | `/api/v1/content-projects/{id}/members` | Add member |
| PATCH | `/api/v1/content-projects/{id}/members/{memberId}` | Update member role |
| DELETE | `/api/v1/content-projects/{id}/members/{memberId}` | Remove member |

### Workflow
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/content-projects/{id}/workflow` | Get adaptive workflow stages |
| POST | `/api/v1/content-projects/{id}/parameter-versions` | Append immutable parameter version |
| GET | `/api/v1/content-projects/{id}/parameter-versions` | List parameter versions |
| PUT | `/api/v1/content-projects/{id}/storyboard-intent` | Set storyboard intent (skipped/requested) |

### Content Units
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/content-projects/{projectId}/content-units` | List project units |
| POST | `/api/v1/content-projects/{projectId}/content-units` | Create content unit |
| GET | `/api/v1/content-units/{id}/draft` | Get unit draft |
| PUT | `/api/v1/content-units/{id}/draft` | Autosave draft (revision check) |
| GET | `/api/v1/content-units/{id}/versions` | List named versions |
| POST | `/api/v1/content-units/{id}/versions` | Create named version |
| POST | `/api/v1/content-units/{id}/versions/{versionId}/restore` | Restore version as new draft |

### Generation Jobs
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/generation-jobs` | Create idempotent generation job |
| GET | `/api/v1/generation-jobs/{id}` | Get job status |
| POST | `/api/v1/generation-jobs/{id}/cancel` | Cancel pending job |

### M1: Storyboard (A/B/C-tier)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/content-projects/{projectId}/storyboard/generate` | Generate A-tier storyboard |
| GET | `/api/v1/content-projects/{projectId}/storyboard` | List storyboard masters |
| GET | `/api/v1/content-projects/{projectId}/storyboard/{masterId}` | Get master detail |
| GET | `/api/v1/content-projects/{projectId}/storyboard/{masterId}/scenes` | List scenes |
| GET | `/api/v1/content-projects/{projectId}/storyboard/{masterId}/shots` | List shots |
| POST | `/api/v1/content-projects/{projectId}/storyboard/{masterId}/lock` | Lock master |
| POST | `/api/v1/content-projects/{projectId}/storyboard/{masterId}/upgrade-b` | Upgrade A→B tier |
| POST | `/api/v1/content-projects/{projectId}/storyboard/{masterId}/upgrade-c` | Upgrade B→C tier |

### M1: Upload
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/content-projects/upload` | Upload TXT/DOCX file |
| GET | `/api/v1/content-projects/upload/{uploadId}` | Get upload status |
| POST | `/api/v1/content-projects/upload/{uploadId}/ai-extract` | AI extract characters/plot/locations |
| POST | `/api/v1/content-projects/upload/{uploadId}/confirm` | Confirm import → create units |

### M2: Batch / Hooks / Continuity / Adaptation / Promotion
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/content-projects/{projectId}/batch-generate` | Batch generate for multiple units |
| POST | `/api/v1/content-projects/{projectId}/generate-hooks` | Generate hooks for all episodes |
| GET | `/api/v1/content-projects/{projectId}/hook-summary` | Hook summary with average scores |
| GET | `/api/v1/content-projects/{projectId}/units/{unitId}/hooks` | Get hooks for a unit |
| POST | `/api/v1/content-projects/{projectId}/capture-snapshots` | Capture continuity snapshots |
| GET | `/api/v1/content-projects/{projectId}/continuity-conflicts` | Check continuity conflicts |
| POST | `/api/v1/content-projects/{projectId}/adapt` | Create adaptation from source |
| POST | `/api/v1/content-projects/{projectId}/promote` | Generate promotional materials |

### M3: Worldbuilding
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/content-projects/{projectId}/world/characters` | Create character |
| POST | `/api/v1/content-projects/{projectId}/world/characters/ai-generate` | AI generate character |
| GET | `/api/v1/content-projects/{projectId}/world/characters` | List characters |
| POST | `/api/v1/content-projects/{projectId}/world/tasks` | Create plot task |
| POST | `/api/v1/content-projects/{projectId}/world/tasks/ai-generate` | AI generate tasks |
| GET | `/api/v1/content-projects/{projectId}/world/tasks` | List tasks |
| POST | `/api/v1/content-projects/{projectId}/world/volumes` | Create volume |
| POST | `/api/v1/content-projects/{projectId}/world/volumes/ai-generate` | AI generate volumes |
| GET | `/api/v1/content-projects/{projectId}/world/volumes` | List volumes |
| POST | `/api/v1/content-projects/{projectId}/world/locations` | Create location |
| POST | `/api/v1/content-projects/{projectId}/world/locations/ai-extract` | AI extract locations |
| GET | `/api/v1/content-projects/{projectId}/world/locations` | List locations |
| POST | `/api/v1/content-projects/{projectId}/world/timeline/ai-generate` | AI generate timeline |
| GET | `/api/v1/content-projects/{projectId}/world/summary` | World summary counts |

### M4: TVC
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/content-projects/{projectId}/tvc/brief` | Create TVC brief |
| GET | `/api/v1/content-projects/{projectId}/tvc/brief` | Get TVC brief |
| POST | `/api/v1/content-projects/{projectId}/tvc/facts/ai-extract` | AI extract brand facts |
| GET | `/api/v1/content-projects/{projectId}/tvc/facts` | List brand facts |
| POST | `/api/v1/content-projects/{projectId}/tvc/strategies/ai-generate` | AI generate creative strategies |
| GET | `/api/v1/content-projects/{projectId}/tvc/strategies` | List strategies |
| POST | `/api/v1/content-projects/{projectId}/tvc/scripts/generate` | Generate TVC script |
| POST | `/api/v1/content-projects/{projectId}/tvc/scripts/multi-platform` | Multi-platform scripts |
| GET | `/api/v1/content-projects/{projectId}/tvc/scripts` | List TVC scripts |

### M5: Production Canvas
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/content-projects/{projectId}/production/import-to-canvas` | Import storyboard to canvas |
| POST | `/api/v1/content-projects/{projectId}/production/batch-generate-images` | Batch generate images |
| POST | `/api/v1/content-projects/{projectId}/production/quality-check` | Quality check |
| POST | `/api/v1/content-projects/{projectId}/production/adopt-nodes` | Adopt canvas nodes |
| POST | `/api/v1/content-projects/{projectId}/production/sync-diff` | Sync diff storyboard↔canvas |
| POST | `/api/v1/content-projects/{projectId}/production/export-manifest` | Export manifest |

### Legacy
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/content-projects/backfill-legacy` | Backfill legacy scripts to projects |

## Enums

### CreationMode: `short_drama`, `long_form`, `tvc`
### SourceMode: `ai_manual`, `uploaded`
### StoryboardIntent: `not_decided`, `skipped`, `requested`, `in_progress`, `completed`
### ContentStatus: `draft`, `reviewing`, `needs_revision`, `approved`, `locked`
### ProductionStatus: `not_started`, `preflight`, `canvas_ready`, `generating`, `quality_review`, `deliverable`
### MarketStatus: `private`, `pending_review`, `listed`, `sold`, `delisted`
### MemberRole: `owner`, `editor`, `reviewer`, `producer`, `viewer`

## Error Codes
| Code | Name | HTTP Status |
|------|------|-------------|
| 43001 | PROJECT_NOT_FOUND | 404 |
| 43002 | PROJECT_ACCESS_DENIED | 403 |
| 43003 | EDIT_CONFLICT | 409 |
| 43004 | WORKFLOW_STAGE_LOCKED | 409 |
| 43005 | ARTIFACT_LOCKED | 409 |
| 43006 | DEPENDENCY_STALE | 409 |
| 43007 | IDEMPOTENCY_CONFLICT | 409 |
| 43008 | SCHEMA_VALIDATION_FAILED | 422 |

## Database Tables
**M0 Foundation:**
- `content_projects` — root project object
- `project_members` — role-based access control
- `project_parameter_versions` — immutable parameter history
- `content_units` — stable content units (episodes, chapters)
- `content_versions` — named versions and autosave drafts
- `artifact_dependencies` — inter-artifact dependency tracking
- `content_generation_jobs` — idempotent AI generation jobs
- `outbox_events` — transaction-bound event persistence

**M1 Storyboard + Upload:**
- `cp_storyboard_masters` — A/B/C-tier storyboard masters
- `cp_storyboard_scenes` — scenes within a master
- `cp_storyboard_shots` — individual shots within a scene
- `content_upload_files` — uploaded TXT/DOCX source files

**M2 Hooks + Continuity:**
- `content_unit_hooks` — per-unit 7-type hook analysis
- `continuity_snapshots` — per-unit continuity state cache

**M3 Long-form Worldbuilding:**
- `character_profiles` — character deep profiles
- `plot_tasks` — plot tasks with stage goals
- `volume_outlines` — volume/chapter outlines
- `world_locations` — L0/L1 location hierarchy
- `story_timeline` — event timeline
- `foreshadowing_items` — foreshadowing planting/payoff

**M4 TVC:**
- `tvc_briefs` — commercial briefs
- `brand_facts` — brand/product fact extraction
- `creative_strategies` — creative strategy angles
- `tvc_scripts` — timecoded commercial scripts

**Key Services:**
- `AiResponseParser` — shared AI response parsing (extractText, parseJson with error logging, ellipsis, sha256)
- `ProjectAccessService` — role-based project access control (VIEW/EDIT_CONTENT/REVIEW/MANAGE_MEMBERS/DELETE_PROJECT)
- `SchemaValidationService` — JSON Schema validation with one repair retry

## Verification
```bash
cd aicp-backend && mvn test          # 32 tests pass
cd aicp-frontend && npm run build    # Production build succeeds
```
