# Script Creation V7.1 M0 API Contract

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

## Database Tables
- `content_projects` — root project object
- `project_members` — role-based access control
- `project_parameter_versions` — immutable parameter history
- `content_units` — stable content units (episodes, chapters)
- `content_versions` — named versions and autosave drafts
- `artifact_dependencies` — inter-artifact dependency tracking
- `content_generation_jobs` — idempotent AI generation jobs
- `outbox_events` — transaction-bound event persistence

## Verification
```bash
cd aicp-backend && mvn test          # 21 tests pass
cd aicp-frontend && node --test tests/content-project-workflow.test.js  # 5 tests pass
cd aicp-frontend && npm run build    # Production build succeeds
```
