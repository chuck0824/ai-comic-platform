# AGENTS.md — AICP 仓库规范（Cursor / AI 助手）

本仓库是 **AI 漫剧与视频内容工业化生产工作台（AICP）** 单体多模块工程。改代码前先读本文件与 `docs/01-core/` 权威文档；子目录另有细则时以子目录为准。

## 模块地图

| 路径 | 端口 | 职责 |
|---|---|---|
| `aicp-frontend/` | 5173（Vite）/ 产品入口 8080 | Vue 3 漫剧工作台 SPA |
| `aicp-backend/` | 8080 | Spring Boot 业务 BFF / 生产域 |
| `new-api/` | API 3000；营销/控制台前端 3001 | Go AI 网关 + React 管理端 |
| `workers/blender/` | — | Blender 导出 worker |
| `docs/01-core/` | — | 产品/API/后端权威事实源 |
| `scripts/dev-up.sh` | — | 本地一键启动 |

## 本地启动

```bash
./scripts/dev-up.sh          # 启动
./scripts/dev-up.sh status   # 状态
./scripts/dev-up.sh stop     # 停止（保留 Redis）
```

常用地址：工作台 http://localhost:5173 ；模型控制台 http://localhost:3001 ；API http://localhost:8080 与 http://localhost:3000 。

- AICP dev：`POST /api/v1/auth/dev/init`（如 `admin` / `admin123`）
- new-api 首次需打开 `/setup` 创建管理员
- SSO / JWT 桥：`JWT_SECRET`（8080）须与 `AICP_JWT_SECRET`（3001）一致；服务间钱包还需 `AICP_SERVICE_SECRET`

## 双端账号（现状）

- 浏览器登录：**短时 SSO 票据**（8080↔3001）+ 3001 影子用户（`aicp_user_id`）
- 服务调用：8080 JWT → `AicpJwtAuth`；勿再写死 `localhost` 入口，用 `VITE_AICP_WORKBENCH_URL` / `VITE_NEW_API_PUBLIC_URL`
- 目标态（设计）：3001 为账号事实源；**尚未完成迁移**，勿在文案中声称「账号已完全统一」除非代码已落地

## 文档优先级

1. `docs/01-core/`（PRD、后端规格、API、new-api 对接）
2. 本文件与 `.cursor/rules/`
3. 子项目 `AGENTS.md`（如 `new-api/AGENTS.md`、`new-api/web/default/AGENTS.md`）
4. `docs/02-derived/`、`docs/03-reference/`、`docs/archive/`（冲突时以前者为准）

## 全局协作约定

- 回复用户默认使用**简体中文**
- 只改任务相关文件；不主动写无关 markdown；不主动 commit/push（用户明确要求除外）
- 禁止提交密钥、`.env`、本地 DB（如 `one-api.db`）、`.venv`、`.dev-logs/`
- 营销/UI 文案走 i18n；new-api default 前端细则见 `new-api/web/default/AGENTS.md`
- 不实现视频剪辑/多轨时间轴——产品定位是**生产工作台**，不是剪辑器

## Git 提交规范

- **语言**：提交说明必须使用**简体中文**
- **按功能拆分**：一次提交只覆盖一个功能/修复点；不要把无关改动塞进同一 commit
- **标题格式**：`类型: 简述目的`（一行，约 50 字内）
  - 常用类型：`feat` 新功能、`fix` 修复、`chore` 工程/脚本、`docs` 文档、`refactor` 重构、`test` 测试
- **正文（可选）**：1–3 句说明动机或影响面；写「为什么」，少写文件清单
- **示例**

```text
feat: 增加 8080 与 3001 短时 SSO 桥接

签发一次性票据换取对端会话，打通工作台与模型控制台登录。
```

```text
docs: 补充根目录 Cursor 与 AGENTS 规范
```

- 不把 `one-api.db`、`.env`、密钥、`.venv`、`.dev-logs/` 纳入提交

## Cursor Rules

项目级规则位于 [`.cursor/rules/`](.cursor/rules/)，按模块自动或始终生效。
