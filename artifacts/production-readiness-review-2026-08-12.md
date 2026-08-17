# AICP 平台生产部署就绪度审查报告

> 审查日期：2026-08-12  
> 审查范围：全模块（aicp-backend、aicp-frontend、new-api、基础设施）  
> 目标：评估是否可以部署到云服务器供外部用户使用  
> 答复文档：[`production-readiness-review-response-2026-08-17.md`](./production-readiness-review-response-2026-08-17.md)（严重度校准、内测/公网门禁与行动项）

---

## 一、总体评估

**🔴 当前不可部署到生产环境。** 需要在部署前修复 10 个关键问题。

代码质量和架构设计整体良好——安全配置分层清晰、JWT/SSO 实现健壮、错误处理完善。但**基础设施和安全配置**方面存在多项阻塞性缺陷，主要集中在：容器化缺失、密钥管理不当、TLS/HTTPS 缺失、以及前端凭据泄露。

---

## 二、各模块评分

| 模块 | 代码质量 | 安全性 | 可部署性 | 综合 |
|------|:--:|:--:|:--:|:--:|
| aicp-backend (Spring Boot) | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ | ⚠️ |
| aicp-frontend (Vue 3) | ⭐⭐⭐ | ⭐⭐ | ⭐⭐ | ⚠️ |
| new-api (Go 网关) | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⚠️ |
| 基础设施/DevOps | ⭐⭐ | ⭐ | ⭐ | 🔴 |

---

## 三、关键问题（Must Fix Before Deploy）

### CRITICAL-1 🔴 核心服务无 Docker 镜像
**影响模块**: aicp-backend, aicp-frontend
- `aicp-backend/` 没有 Dockerfile，现有 `docker-compose.yml` 只包含 MySQL/Redis/MinIO 基础设施，不包含应用服务
- `aicp-frontend/` 完全没有 Docker 化
- 只有 `new-api/` 和 `workers/blender/` 有生产级 Dockerfile
- **后果**: 无法在任何容器编排平台（K8s、ECS 等）上部署

**修复**: 为 aicp-backend 创建多阶段 Dockerfile（Maven 构建 + JRE 运行），为 aicp-frontend 创建构建+nginx 的 Dockerfile

### CRITICAL-2 🔴 无反向代理 / 无 HTTPS
**影响模块**: 基础设施
- 所有服务直接暴露 HTTP 端口（3000、3001、8080、5173）
- 仓库中无 nginx、Caddy 或 Traefik 配置
- 无 SSL/TLS 终止、无边缘限流、无静态资源缓存策略

**修复**: 添加 nginx/Caddy 反向代理配置，配置 Let's Encrypt 证书自动续签

### CRITICAL-3 🔴 SSO 桥密钥未配置 — 跨服务认证将静默失败
**影响模块**: new-api
- `new-api/.env` 中 `AICP_SERVICE_SECRET=dev-secret-key`（硬编码开发值）
- `SESSION_SECRET` 完全未设置 → 每次重启会话全部失效，多节点部署完全不可用
- `AICP_JWT_SECRET` 未设置 → 回退到自动生成的 UUID，与 aicp-backend 的 JWT_SECRET 永远不匹配 → **整个 SSO 桥静默失败**，用户无法登录
- `AICP_JWT_SECRET` 必须与 aicp-backend 的 `JWT_SECRET` 完全一致

**修复**: 在所有 `.env` / 环境变量中设置强随机值的 `SESSION_SECRET`、`AICP_JWT_SECRET`、`AICP_SERVICE_SECRET`

### CRITICAL-4 🔴 前端 Token 存 localStorage — XSS 可导致账户接管
**影响模块**: aicp-frontend
- `access_token` 和 `refresh_token` 都存储在 `localStorage`，通过 `Authorization: Bearer` 头附加
- 任何 XSS 漏洞（SPA 生态中很常见：Element Plus、ECharts、Three.js、Vue Flow 均有历史 XSS CVE）可直接读取并外泄 Token
- refresh_token 有效期长达 30 天，攻击者可长期维持访问

**修复**: refresh_token 改为 httpOnly + Secure + SameSite=Strict Cookie（由后端设置），access_token 仅保存在 Pinia 内存中

### CRITICAL-5 🔴 生产环境前端暴露开发凭据
**影响模块**: aicp-frontend
- [Login.vue](aicp-frontend/src/views/Login.vue) 第 31/53 行在任何环境（含生产）都渲染开发提示："开发环境可直接用验证码 **123456**"、"开发账号：**admin** / **admin123**"
- [Login.vue](aicp-frontend/src/views/Login.vue) 第 119-127 行预填表单字段：手机号 `13800000001`、账号 `admin`、密码 `admin123`
- 未使用 `import.meta.env.DEV` 做环境隔离

**修复**: 添加 `v-if="import.meta.env.DEV"` 条件渲染，生产构建时完全移除

### CRITICAL-6 🔴 无统一生产编排
**影响模块**: 基础设施
- 不存在顶层 `docker-compose.prod.yml` 定义完整技术栈
- 无 Kubernetes manifests / Helm chart
- 每个服务的 compose 文件假设独立运行，互不感知

**修复**: 创建统一的生产部署编排（docker-compose 或 K8s）

### CRITICAL-7 🔴 硬编码默认密码
**影响模块**: 基础设施/new-api
- `aicp-backend/docker-compose.yml`: `MYSQL_ROOT_PASSWORD: root123`、`MYSQL_PASSWORD: aicp123`、`MINIO_ROOT_PASSWORD: minioadmin123`
- `new-api/docker-compose.yml`: Redis 密码 `123456` 硬编码
- 无强制覆盖机制，直接部署即使用弱密码

**修复**: 全部改为 `${VAR}` 环境变量引用，不设默认值，启动时校验必填变量

### CRITICAL-8 🔴 `one-api.db` (692KB SQLite) 已提交到 Git
**影响模块**: 基础设施
- 二进制数据库文件跟踪在仓库根目录——生产数据泄露风险 + 合并冲突
- `.gitignore` 未覆盖根目录的 `one-api.db`

**修复**: `git rm --cached one-api.db`，添加到 `.gitignore`

### CRITICAL-9 🔴 aicp-backend Flyway 依赖缺失
**影响模块**: aicp-backend
- `db/migration/` 有 17 个版本化 SQL 文件，但 `pom.xml` 未声明 Flyway 依赖
- 生产 profile 无 `spring.flyway.*` 配置
- **数据库 Schema 漂移无法控制**，部署时可能遗漏迁移

**修复**: 添加 `flyway-core` + `flyway-mysql` 依赖，在 prod profile 启用自动迁移

### CRITICAL-10 🔴 限流为内存级实现 — 多实例不生效
**影响模块**: aicp-backend
- [RateLimitFilter.java](aicp-backend/src/main/java/com/aicp/common/config/RateLimitFilter.java) 使用 `ConcurrentHashMap` 而非 Redis
- 代码注释自行承认"生产环境建议替换为 Redis 方案"
- 多 Pod 部署场景下，攻击者可通过分散请求绕过限流

**修复**: 迁移到 Redis 限流（bucket4j-redis 或 Lua 脚本方案），或使用入口层限流（nginx/K8s ingress）

---

## 四、重要问题（Should Fix Before Deploy）

| # | 模块 | 问题 | 影响 |
|---|------|------|------|
| I-1 | backend | 数据库连接无 TLS — 生产 DB URL 缺少 `useSSL=true&requireSSL=true` | 云数据库（RDS 等）凭证明文传输 |
| I-2 | backend | 无显式优雅关闭配置 — AI 生成请求可能耗时数分钟，默认 30s 超时不足 | 滚动更新时中断正在执行的生成任务 |
| I-3 | backend | Redis 无高可用配置 — prod profile 无 sentinel/cluster 支持 | 单点故障影响 Token 黑名单、刷新、会话 |
| I-4 | frontend | 无生产 API 基础 URL 环境变量 — `request.js` 硬编码 `baseURL: '/api/v1'` | 独立部署（CDN/不同子域）时 API 请求失败 |
| I-5 | frontend | SPA 回退配置缺失 — `createWebHistory()` 需要服务器配置 | 直接访问/刷新子路由返回 404 |
| I-6 | frontend | SSO redirect 清理不完整 — 未拦截 `javascript:`、`data:`、反斜杠前缀 URL | 反射型 XSS 风险 |
| I-7 | frontend | 登录表单无客户端校验 — 任何格式的手机号/密码直接提交 | 用户体验差、后端负载增加 |
| I-8 | frontend | `build.outDir` 将前端构建产物绑死后端源码树 | 无法独立部署到 CDN/S3 |
| I-9 | infra | 前端构建产物（161 文件）提交到 `aicp-backend/.../static/` | Code review 噪音、合并冲突、维护负担 |
| I-10 | infra | 无数据库备份策略 — 无备份脚本/定时任务/文档 | 数据丢失无恢复手段 |
| I-11 | infra | 无集中日志/监控/告警 — 无 Loki/Prometheus/Grafana 集成 | 生产故障无法及时发现和排查 |
| I-12 | infra | `new-api/Dockerfile` 运行时以 root 用户运行 | 容器逃逸后直接获得宿主机 root |
| I-13 | infra | 无容器资源限制 — compose 文件无 `mem_limit`/`cpus` | 失控进程耗尽宿主机资源 |
| I-14 | infra | new-api 无优雅关闭 — `main.go:213` 直接 `server.Run()` 无信号处理 | 容器停止时可能中断正在处理的请求 |
| I-15 | new-api | `new-api/.env` 已提交到 Git — 包含 `AICP_SERVICE_SECRET`（虽然是开发值） | 密钥管理反模式，生产环境易误操作 |
| I-16 | backend | `loginByPassword()` 是 stub → 调用时 NPE | 确认该接口未被暴露或实现它 |

---

## 五、次要问题（Nice to Have）

<details>
<summary>展开查看 15 个次要问题</summary>

| # | 模块 | 问题 |
|---|------|------|
| M-1 | backend | HealthController 默认 URL fallback 指向 3001 而非 3000 |
| M-2 | backend | 生产日志非 JSON 格式 — 容器化环境建议结构化日志 |
| M-3 | backend | 微信登录是 mock 实现 — 如预期生产使用需实现 |
| M-4 | backend | docker-compose Redis 无密码（开发环境也应模拟生产配置） |
| M-5 | frontend | 无显式 `sourcemap: false` 配置（依赖 Vite 默认值） |
| M-6 | frontend | 无 Content-Security-Policy 头配置 |
| M-7 | frontend | `.env.example` 太稀疏 — 只记录了一个变量 |
| M-8 | frontend | 登录页已登录用户重定向使用 `next('/dashboard')` 而非 `replace: true` |
| M-9 | frontend | workspace 初始化逻辑在 4 个位置重复实现 |
| M-10 | frontend | "企业SSO登录 →"按钮无点击处理函数 |
| M-11 | infra | systemd service 文件含未解析占位符 |
| M-12 | infra | `dev-up.sh` Redis 以 daemonize 方式启动，崩溃后无人重启 |
| M-13 | infra | `new-api/Dockerfile.dev` 未固定 base image digest |
| M-14 | infra | aicp-backend/aicp-frontend 缺少 `.dockerignore` |
| M-15 | infra | `new-api/makefile` 混用 `docker-compose` (v1) 和 `docker compose` (v2) |

</details>

---

## 六、做得好的地方

- ✅ **Spring Security 纵深防御**: SecurityConfig 实现 CSP、XSS 保护、防点击劫持、权限策略等完整安全头
- ✅ **JWT 实现健壮**: SSO ticket 一次性使用（Redis SetNX 防重放）、UID 使用字符串避免精度丢失、Token 黑名单
- ✅ **登录暴力破解防护**: Redis 限流、5 次失败锁定 15 分钟
- ✅ **生产配置干净隔离**: Prod profile 所有密钥通过环境变量注入、无硬编码默认值
- ✅ **健康检查完善**: 独立的 liveness/readiness 探针、DB+Redis+new-api 依赖检查
- ✅ **错误处理全面**: 90+ 业务错误码、统一异常处理、catch-all 不泄露堆栈
- ✅ **new-api 多层限流**: 全局限流 → 关键接口限流 → 按模型限流 → 按用户搜索限流，Redis 实现，Redis 故障时 fail-open
- ✅ **new-api Dockerfile 生产级**: 多阶段构建（Bun 前端 + Go 静态编译 + Debian slim）、base image digest 固定
- ✅ **服务间认证设计良好**: HMAC-SHA256 + 时间戳偏差保护 + body hash + 幂等键支持
- ✅ **前端 Token 刷新队列化**: 并发 401 只发一次刷新请求，其余请求排队等待
- ✅ **前端全局错误边界**: `app.config.errorHandler` 防止白屏崩溃并显示用户通知
- ✅ **路由级代码分割**: 所有路由使用 `import()` 动态加载
- ✅ **Schema 迁移向后兼容**: `ADD COLUMN IF NOT EXISTS`、可空列、每迁移配 undo 文件

---

## 七、部署前优先级路线图

### 阶段一：阻塞项（必须先做）
```
□ 为 aicp-backend/aicp-frontend 创建 Dockerfile
□ 添加 nginx/Caddy 反向代理 + Let's Encrypt HTTPS
□ 配置所有 SSO 密钥（SESSION_SECRET、AICP_JWT_SECRET、AICP_SERVICE_SECRET）
□ 前端 Token 改为 httpOnly Cookie + 内存存储
□ 前端条件渲染移除生产环境开发凭据
□ 创建统一 docker-compose.prod.yml
□ 移除硬编码密码 → ${VAR} 引用
□ git rm --cached one-api.db → .gitignore
□ 添加 Flyway 依赖并启用 prod 自动迁移
□ 迁移限流到 Redis
```

### 阶段二：上线前加强（强烈建议）
```
□ DB 连接启用 TLS
□ 显式配置优雅关闭超时
□ Redis 配置 sentinel 高可用
□ SPA 回退 nginx 配置
□ SSO redirect 安全加固
□ 前端表单校验
□ 静态资源构建产物移除 → 由 CI/CD 构建
□ 数据库备份自动化
□ 集中日志 + 监控 + 告警
□ 容器非 root 用户运行
```

### 阶段三：迭代优化
```
□ JSON 结构化日志
□ CSP 头配置
□ 完善 .env.example 文档
□ 编写 DEPLOY.md 部署文档
□ K8s manifests / Helm chart
```

---

## 八、结论

| 维度 | 评估 |
|------|------|
| **代码质量** | 良好 — 安全架构、错误处理、SSO 设计均达到生产标准 |
| **独立运行** | ✅ 开发环境各模块可独立启动并完成功能验证 |
| **生产可部署性** | 🔴 **阻塞** — 核心服务未容器化、无 HTTPS、密钥未配置 |
| **安全性（面向外部用户）** | 🔴 **阻塞** — 凭据泄露、Token 存储不安全、硬编码密码 |
| **运维就绪度** | 🔴 **阻塞** — 无备份、无监控、无编排、无 TLS |

**预计修复工作量**: 阶段一约 3-5 个工作日，阶段二约 2-3 个工作日。

**最终判定**: 当前版本**不建议**直接部署到云服务器供外部用户使用。需先完成阶段一（10 个 Critical 项）后方可进行灰度部署，阶段二（16 个 Important 项）完成后方可正式面向外部用户开放。
