# AI漫剧与视频内容工业化生产工作台 · 后端产品功能设计母版

> **⚠️ 此文档已废弃，不再作为开发依据。当前后端事实源请使用：[`../01-core/后端产品功能设计_V1.5.md`](../01-core/后端产品功能设计_V1.5.md)。本文件仅保留供架构参考。**


> 基于《用户端产品功能设计.md》v0.5映射的后端架构与服务设计  
> 覆盖版本：V1.0 → V1.1 → V1.2 → V1.3 → V1.5  
> 文档性质：后端功能规格说明书（Backend Feature Spec）  
> 🆕 V1.3 增强内容：agent-svc 独立服务、画布节点引擎增强、SOP画布质检联动、模型Adapter标准接口、多副本并行生成、全能参考视频引擎。详见《后端产品功能设计_V1.5.md》
> 🆕 V1.5 修订提示：后端最终口径以《后端产品功能设计_V1.5.md》的 V1.5 修订版为准，产品定位升级为 AI 漫剧与视频内容工业化生产工作台，后端必须支撑项目、画布、节点、连线、分镜、资产、批量生成、时间线、导出、工作流、Agent/Skill 的完整生产闭环。

## V1.5 修订摘要

本母版保留原第一周期服务拆分，V1.5 在 `canvas-svc`、`generation-svc`、`asset-svc`、`agent-svc`、`billing-svc` 上追加以下强约束：

| 能力 | 后端要求 |
|---|---|
| 画布节点 | `canvas_nodes`、`canvas_edges`、节点坐标、节点状态、输入/输出数据必须持久化 |
| 分镜生产 | `storyboard_shots` 必须承载镜头编号、场次、景别、运镜、角色、场景资产、图片/视频 Prompt 与生成状态 |
| 生成任务 | `generation_tasks` 统一承载图片、视频、音频、合成、导出、Agent/Skill 执行任务，记录模型、参数、进度、成本、错误和输出资产 |
| 资产沉淀 | 所有生成结果自动进入资产库，支持历史检索、拖回画布、来源节点追溯 |
| Agent/Skill | Agent 必须通过平台 Tool 创建节点、连线、调用生成、保存资产和回写画布，不允许只返回聊天文本 |
| 商业化 | 生成前支持算力预估，生成后记录 `credit_cost`，为会员、算力包、模板/Skill/资产交易提供计费基础 |

### V1.5 开发引用规则

本文件是后端母版，用于理解服务拆分和长期架构；当前开发、联调和测试以以下文档为准：

| 场景 | 事实源 |
|---|---|
| 画布节点、连线、任务、资产、积分的详细后端规格 | `后端产品功能设计_V1.5.md` |
| HTTP 路径、请求字段、响应字段、错误码 | `API接口文档.md` |
| AI Router、new-api、模型供应商和生成任务闭环 | `new-api对接技术规划_V1.0.md` |
| 产品范围、节点行为、页面验收 | `用户端PRD.md`、`用户端产品功能设计.md` |

当前 P0 开发必须遵循以下硬约束：

| 约束 | 要求 |
|---|---|
| 连线接口 | 创建连线主路径使用 `/api/v1/canvas/projects/{projectId}/nodes/connect` |
| 任务入口 | 画布生成动作统一创建 `generation_tasks`，不得前端直连模型供应商 |
| 积分 | AI 动作必须先预估，再确认，再执行；后续补齐冻结、结算、退还 |
| 结果 | 生成结果必须回写节点和资产库，分镜相关任务还要回写分镜行 |
| 测试 | 每个 P0 节点至少覆盖成功、失败、余额不足、刷新恢复、重复提交五类用例 |

---

## 目录

1. [总体架构设计](#1-总体架构设计)
2. [微服务拆分与职责](#2-微服务拆分与职责)
3. [API网关设计](#3-api网关设计)
4. [服务一：用户与账户服务](#4-服务一用户与账户服务)
5. [服务二：剧本生成服务](#5-服务二剧本生成服务)
6. [服务三：剧本仓库服务](#6-服务三剧本仓库服务)
7. [服务四：交易与支付服务](#7-服务四交易与支付服务)
8. [服务五：AI资产市场服务](#8-服务五ai资产市场服务)
9. [服务六：画布与视频工作台服务](#9-服务六画布与视频工作台服务)
10. [服务七：工业化生产SOP服务](#10-服务七工业化生产sop服务)
11. [服务八：通知与消息服务](#11-服务八通知与消息服务)
12. [AI/ML推理编排层](#12-aiml推理编排层)
13. [存储架构设计](#13-存储架构设计)
14. [异步任务与消息队列](#14-异步任务与消息队列)
15. [多租户架构](#15-多租户架构)
16. [安全与合规](#16-安全与合规)
17. [监控与可观测性](#17-监控与可观测性)
18. [部署架构与基础设施](#18-部署架构与基础设施)
19. [版本迭代计划（后端）](#19-版本迭代计划后端)
20. [附录：核心数据模型ER图](#20-附录核心数据模型er图)

---

## 1. 总体架构设计

### 1.1 架构选型

```
┌─────────────────────────────────────────────────────────────┐
│                      客户端层                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────────┐ │
│  │ Web (Vue)│  │ 企业后台  │  │ API Client  │               │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──────┬──────┘ │
│       └──────────────┴─────────────┴───────────────┘        │
├─────────────────────────────────────────────────────────────┤
│                      CDN / WAF / DDoS                       │
├─────────────────────────────────────────────────────────────┤
│                   🆕 API Gateway (Kong / APISIX)             │
│        ┌──────────────┬──────────────┬──────────────┐       │
│        │ 认证/鉴权    │ 限流/熔断    │ 路由/负载    │       │
│        └──────────────┴──────────────┴──────────────┘       │
├─────────────────────────────────────────────────────────────┤
│                      BFF Layer (Backend For Frontend)        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────────┐  │
│  │ Web BFF  │  │ Admin    │  │ Open API   │               │  │
│  │          │  │ BFF      │  │ BFF        │               │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──────┬─────┘  │
├───────┴──────────────┴─────────────┴───────────────┴────────┤
│                    核心微服务层                               │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────────────┐   │
│  │用户账户 │ │剧本生成 │ │剧本仓库 │ │交易与支付       │   │
│  │Service  │ │Service  │ │Service  │ │Service          │   │
│  └────┬────┘ └────┬────┘ └────┬────┘ └───────┬─────────┘   │
│  ┌────┴────┐ ┌────┴────┐ ┌────┴────┐ ┌───────┴─────────┐   │
│  │AI资产   │ │画布视频 │ │生产SOP  │ │通知与消息       │   │
│  │市场     │ │工作台   │ │Service  │ │Service          │   │
│  │Service  │ │Service  │ │         │ │                 │   │
│  └────┬────┘ └────┬────┘ └────┬────┘ └───────┬─────────┘   │
├───────┴──────────────┴─────────────┴───────────────┴────────┤
│                   AI/ML 推理编排层                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐   │
│  │LLM推理   │ │图像生成  │ │视频生成  │ │TTS/ASR      │   │
│  │(GPT/DS)  │ │(SD/Flux) │ │(Kling/   │ │(火山/阿里)  │   │
│  │          │ │          │ │Seedance) │ │             │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘   │
├─────────────────────────────────────────────────────────────┤
│                   中间件与基础设施                            │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐   │
│  │MySQL │ │Redis │ │  ES  │ │ OSS  │ │  MQ  │ │ K8s  │   │
│  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘ └──────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 技术栈推荐

| 层级 | 技术选型 | 说明 |
|------|---------|------|
| **API网关** | Kong / APISIX | 统一入口、认证、限流、路由 |
| **BFF** | Node.js (Express/Fastify) 或 Go (Gin) | 前端适配层，聚合后端服务 |
| **微服务** | Go (Kratos) 或 Java (Spring Cloud) | 核心业务服务 |
| **服务通信** | gRPC (内部) + REST (对外) | 同步调用 |
| **消息队列** | RocketMQ / Kafka | 异步任务、事件驱动 |
| **数据库** | MySQL 8.0 (主库) + Redis (缓存) + Elasticsearch (搜索) | 持久化 + 缓存 + 全文检索 |
| **对象存储** | 阿里云OSS / AWS S3 / MinIO | 图片/视频/模型文件存储 |
| **容器编排** | Kubernetes (K8s) | 服务部署与弹性伸缩 |
| **服务网格** | Istio (可选) | 高级流量管理 |
| **监控** | Prometheus + Grafana + Jaeger | 指标+链路追踪 |
| **日志** | ELK (Elasticsearch + Logstash + Kibana) 或 Loki | 集中日志 |
| **CI/CD** | GitLab CI / GitHub Actions / ArgoCD | 持续交付 |

---

## 2. 微服务拆分与职责

### 2.1 服务矩阵

| 服务名 | 服务ID | 职责 | 核心领域 | 优先级 |
|--------|--------|------|---------|:---:|
| **用户与账户服务** | `user-svc` | 注册/登录/认证/个人中心/企业中心/权限 | 用户、企业、角色权限 | P0 |
| **剧本生成服务** | `script-gen-svc` | AI剧本生成编排、6步向导、A/B/C三档分镜 | 剧本、分镜、AI编排 | P0 |
| **剧本仓库服务** | `script-repo-svc` | 剧本CRUD、4轴标签、版本管理、L0-L4资产 | 剧本、标签、版本、资产 | P0 |
| **交易与支付服务** | `trade-svc` | 剧本交易市场、授权管理、支付、订单 | 交易、支付、授权 | P1 |
| **AI资产市场服务** | `asset-market-svc` | 风格模型市场、角色/场景资产、提示词市场 | AI模型、资产、社区 | P1 |
| **画布视频工作台服务** | `canvas-svc` | 画布编排、分镜管理、时间轴、视频合成、导出、节点引擎、资产联动 | 画布、分镜、视频、导出、节点 | P0 |
| **工业化生产SOP服务** | `sop-svc` | 生产准入、审计返工、版本锁定、产能估算、画布质检联动 | SOP、审计、权限 | P1 |
| **通知与消息服务** | `notify-svc` | 站内通知、邮件、短信、浏览器推送 | 通知、消息 | P0 |
| 🆕 **Agent与Skill服务** | `agent-svc` | AI导演、Skill执行、任务编排、第三方Agent接入、Tool Router、画布回写 | Agent、Skill、OpenAPI、自动化 | P1 |

### 2.2 服务间调用关系

```
                    ┌──────────────┐
                    │  API Gateway │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │user-svc  │ │script-   │ │canvas-svc│
        │(用户认证)│ │gen-svc   │ │(画布)    │
        └────┬─────┘ └────┬─────┘ └────┬─────┘
             │            │            │
             │     ┌──────┼──────┐     │
             ▼     ▼      ▼      ▼     ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │script-   │ │asset-    │ │trade-svc │
        │repo-svc  │ │market-svc│ │(交易)    │
        └────┬─────┘ └──────────┘ └────┬─────┘
             │                         │
             └─────────┬───────────────┘
                       ▼
                ┌──────────┐    ┌──────────┐
                │sop-svc   │    │agent-svc │
                │(生产SOP) │    │(Agent)   │
                └──────────┘    └──────────┘
```

### 2.3 事件驱动通信

各服务通过MQ发布领域事件，解耦核心流程：

| 事件 | 发布方 | 消费方 |
|------|--------|--------|
| `UserRegistered` | user-svc | notify-svc（发欢迎通知） |
| `ScriptGenerated` | script-gen-svc | script-repo-svc（自动入库）、notify-svc |
| `ScriptListed` | script-repo-svc | trade-svc（上架）、notify-svc |
| `OrderPaid` | trade-svc | script-repo-svc（剧本权限转移）、notify-svc |
| `AssetPublished` | asset-market-svc | canvas-svc（缓存更新）、notify-svc |
| `CanvasExportCompleted` | canvas-svc | notify-svc（导出完成通知） |
| `AuditFailed` | sop-svc | notify-svc（审计失败通知） |
| `AssetLocked` | script-repo-svc | sop-svc（审计触发）、canvas-svc |
| 🆕 `AgentTaskCreated` | agent-svc | canvas-svc、script-gen-svc、notify-svc |
| 🆕 `AgentTaskCompleted` | agent-svc | canvas-svc、asset-market-svc、notify-svc |
| 🆕 `SkillPublished` | agent-svc | notify-svc、asset-market-svc |
| 🆕 `CanvasNodeGenerated` | canvas-svc | script-repo-svc、asset-market-svc、sop-svc |

---

## 3. API网关设计

### 3.1 路由策略

| 路由前缀 | 目标服务 | 认证要求 | 限流策略 |
|----------|---------|:---:|------|
| `/api/v1/auth/*` | user-svc | 无（登录/注册） | 100次/分钟/IP |
| `/api/v1/user/*` | user-svc | JWT Token | 1000次/分钟 |
| `/api/v1/enterprise/*` | user-svc | JWT + 企业角色 | 2000次/分钟 |
| `/api/v1/script/gen/*` | script-gen-svc | JWT Token | 50次/分钟（AI调用） |
| `/api/v1/script/repo/*` | script-repo-svc | JWT Token | 500次/分钟 |
| `/api/v1/trade/*` | trade-svc | JWT Token | 200次/分钟 |
| `/api/v1/asset/*` | asset-market-svc | JWT Token | 500次/分钟 |
| `/api/v1/canvas/*` | canvas-svc | JWT Token | 300次/分钟 |
| `/api/v1/sop/*` | sop-svc | JWT + 企业角色 | 500次/分钟 |
| `/api/v1/notify/*` | notify-svc | JWT Token | 500次/分钟 |
| 🆕 `/api/v1/agent/*` | agent-svc | JWT Token | 100次/分钟（Agent调用） |
| `/openapi/v1/*` | Open API BFF | API Key + 签名 | 按套餐 |
| `/admin/api/*` | Admin BFF | JWT + 管理员 | 1000次/分钟 |

### 3.2 统一认证流程

```
客户端请求
    → API Gateway 提取 Token
        → 调用 user-svc 验证 Token
            → 解析用户ID + 角色 + 权限列表
                → 注入 Header：X-User-ID, X-User-Role, X-Permissions
                    → 转发到目标微服务
```

### 3.3 API规范（OpenAPI 3.0）

所有API遵循统一规范：

- **请求格式**：JSON（GET参数用Query String）
- **响应格式**：
```json
{
  "code": 0,
  "message": "success",
  "data": { },
  "request_id": "uuid",
  "timestamp": 1717843200
}
```
- **错误码**：统一5位错误码体系（见附录）
- **分页**：`?page=1&page_size=20`，响应含 `total`、`has_more`
- **版本控制**：URL路径版本 `/api/v1/`、`/api/v2/`

---

## 4. 服务一：用户与账户服务

> **服务ID**：`user-svc`  
> **优先级**：P0  
> **职责**：用户注册/登录/认证、个人中心、企业中心、角色权限管理

### 4.1 核心API

| 方法 | 路径 | 说明 | 版本 |
|------|------|------|:---:|
| POST | `/api/v1/auth/register` | 用户注册（手机/邮箱） | V1.0 |
| POST | `/api/v1/auth/login` | 账号密码登录 | V1.0 |
| POST | `/api/v1/auth/login/sms` | 短信验证码登录 | V1.0 |
| POST | `/api/v1/auth/login/wechat` | 微信OAuth登录 | V1.0 |
| POST | `/api/v1/auth/login/sso` | 企业SSO登录 | V1.2 |
| POST | `/api/v1/auth/send-code` | 发送验证码（短信/邮箱） | V1.0 |
| POST | `/api/v1/auth/refresh-token` | 刷新Token | V1.0 |
| POST | `/api/v1/auth/logout` | 登出 | V1.0 |
| GET | `/api/v1/user/profile` | 获取个人信息 | V1.0 |
| PUT | `/api/v1/user/profile` | 更新个人信息 | V1.0 |
| POST | `/api/v1/user/verify/real-name` | 实名认证 | V1.0 |
| GET | `/api/v1/user/membership` | 获取会员状态 | V1.1 |
| POST | `/api/v1/user/membership/upgrade` | 升级会员 | V1.1 |
| GET | `/api/v1/user/api-keys` | 管理API Key | V1.2 |
| POST | `/api/v1/user/api-keys` | 创建API Key | V1.2 |
| DELETE | `/api/v1/user/api-keys/:id` | 删除API Key | V1.2 |
| 🆕 POST | `/api/v1/enterprise/register` | 企业注册+认证提交 | V1.1 |
| 🆕 GET | `/api/v1/enterprise/profile` | 企业信息 | V1.1 |
| 🆕 PUT | `/api/v1/enterprise/profile` | 更新企业信息 | V1.1 |
| 🆕 GET | `/api/v1/enterprise/members` | 企业成员列表 | V1.1 |
| 🆕 POST | `/api/v1/enterprise/members/invite` | 邀请成员 | V1.1 |
| 🆕 PUT | `/api/v1/enterprise/members/:uid/role` | 设置成员角色权限 | V1.1 |
| 🆕 DELETE | `/api/v1/enterprise/members/:uid` | 移除成员 | V1.1 |
| 🆕 GET | `/api/v1/enterprise/dashboard` | 企业仪表盘数据 | V1.1 |

### 4.2 数据模型

#### 用户表 `users`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 用户ID |
| `uuid` | VARCHAR(36) UK | 对外暴露UUID |
| `phone` | VARCHAR(20) UK | 手机号（加密存储） |
| `email` | VARCHAR(255) UK | 邮箱（加密存储） |
| `wechat_openid` | VARCHAR(128) UK | 微信OpenID |
| `password_hash` | VARCHAR(255) | 密码哈希 |
| `nickname` | VARCHAR(100) | 昵称 |
| `avatar_url` | VARCHAR(500) | 头像URL |
| `account_type` | ENUM('personal','enterprise') | 账户类型 |
| `real_name_status` | ENUM('unverified','pending','verified') | 实名状态 |
| `member_level` | ENUM('free','creator','enterprise') | 会员等级 |
| `member_expire_at` | DATETIME | 会员到期时间 |
| `status` | ENUM('active','disabled','deleted') | 账户状态 |
| `last_login_at` | DATETIME | 最后登录时间 |
| `last_login_ip` | VARCHAR(45) | 最后登录IP |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |

#### 企业表 `enterprises`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 企业ID |
| `owner_user_id` | BIGINT FK | 企业管理员 |
| `name` | VARCHAR(200) | 企业名称 |
| `license_number` | VARCHAR(100) | 营业执照号 |
| `license_image_url` | VARCHAR(500) | 营业执照图片 |
| `verify_status` | ENUM('unverified','pending','verified','rejected') | 认证状态 |
| `member_limit` | INT | 成员上限 |
| `created_at` | DATETIME | 创建时间 |

#### 企业成员表 `enterprise_members`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | — |
| `enterprise_id` | BIGINT FK | 企业ID |
| `user_id` | BIGINT FK | 用户ID |
| `role` | VARCHAR(50) | 角色（admin/dept_head/writer/artist/editor/reviewer） |
| `permissions` | JSON | 11项细粒度权限 |
| `department` | VARCHAR(100) | 所属部门 |
| `status` | ENUM('pending','active','disabled') | 状态 |
| `joined_at` | DATETIME | 加入时间 |

### 4.3 认证流程

**JWT Token设计**：
```json
{
  "sub": "user_uuid",
  "uid": 12345,
  "type": "personal|enterprise",
  "ent_id": 100,        // 企业用户携带
  "role": "admin",
  "permissions": ["can_generate_script", "can_purchase_script", ...],
  "iat": 1717843200,
  "exp": 1717929600
}
```

**Token刷新策略**：
- Access Token：有效期2小时
- Refresh Token：有效期30天，存储在Redis中
- 企业SSO Token：与企业IdP同步过期

### 4.4 权限模型（RBAC）

```
角色(Role)
  ├── 平台角色：super_admin / operator / content_reviewer
  ├── 个人角色：free_user / creator_member
  └── 企业角色：ent_admin / dept_head / writer / artist / editor / reviewer

权限(Permission) — 11项企业细粒度权限
  ├── can_generate_script
  ├── can_purchase_script
  ├── can_purchase_asset
  ├── can_generate_video
  ├── can_export_no_watermark
  ├── can_manage_assets
  ├── can_approve_purchase
  ├── can_approve_export
  ├── can_manage_members
  ├── can_access_api
  └── can_view_analytics
```

---

## 5. 服务二：剧本生成服务

> **服务ID**：`script-gen-svc`  
> **优先级**：P0  
> **职责**：AI剧本生成编排、6步向导引擎、A/B/C三档分镜生成、与LLM/图像模型交互

### 5.1 核心API

| 方法 | 路径 | 说明 | 版本 |
|------|------|------|:---:|
| POST | `/api/v1/script/gen/topic` | Step1: 爆款选题生成 | V1.0 |
| POST | `/api/v1/script/gen/synopsis` | Step2: 故事梗概生成 | V1.0 |
| POST | `/api/v1/script/gen/outline` | Step3: 分集大纲生成 | V1.0 |
| POST | `/api/v1/script/gen/episode` | Step4: 单集剧本生成 | V1.0 |
| POST | `/api/v1/script/gen/storyboard` | Step5: 分镜脚本生成（A/B/C档） | V1.0 |
| POST | `/api/v1/script/gen/promotion` | Step6: 投流素材生成 | V1.2 |
| POST | `/api/v1/script/gen/quick` | 快速模式（一步到位） | V1.0 |
| POST | `/api/v1/script/gen/storyboard/upgrade` | 分镜升档（A→B→C） | V1.1 |
| GET | `/api/v1/script/gen/task/:task_id` | 查询生成任务状态 | V1.0 |
| GET | `/api/v1/script/gen/tasks` | 生成历史列表 | V1.0 |

### 5.2 生成任务编排引擎

```
用户请求 → 参数校验 → 创建生成任务(状态:pending)
    → 推入任务队列
        → Worker消费 → 调用AI推理层
            ├── Step1: LLM → 选题方向
            ├── Step2: LLM → 故事梗概
            ├── Step3: LLM → 分集大纲
            ├── Step4: LLM → 单集剧本
            ├── Step5: LLM → A档分镜 / B档导演意图 / C档生产表
            └── Step6: LLM → 投流素材
        → 更新任务状态(completed/failed)
            → 发布 ScriptGenerated 事件
```

### 5.3 AI编排配置

| 步骤 | AI模型 | 输入 | 输出 | 平均耗时 | 并发限制 |
|------|--------|------|------|:---:|:---:|
| Step1 选题 | LLM (DeepSeek/GPT-4) | 创意+标签+平台 | 3-5个选题方案 | 5-10s | 20 |
| Step2 梗概 | LLM (DeepSeek/GPT-4) | 选题+标签 | 500字梗概+世界观 | 10-15s | 20 |
| Step3 大纲 | LLM (DeepSeek/GPT-4) | 梗概+集数 | 分集大纲 | 15-30s | 15 |
| Step4 剧本 | LLM (Claude/GPT-4) | 大纲+角色+标签 | 单集完整剧本 | 20-60s | 10 |
| Step5 A档 | LLM (Claude/GPT-4) | 剧本 | A档分镜表 | 15-30s | 10 |
| Step5 B档 | LLM (Claude/GPT-4) | A档分镜 | B档导演确认表 | 15-30s | 10 |
| Step5 C档 | LLM (Claude) | B档分镜+资产ID | C档生产三表 | 20-40s | 5 |
| Step6 投流 | LLM (DeepSeek/GPT-4) | 剧本+题材 | 标题/封面/钩子 | 10-20s | 20 |

### 5.4 Prompt模板管理

| 功能 | 说明 |
|------|------|
| **Prompt模板库** | 每步骤预置3-5个Prompt模板（按题材/风格/平台变体） |
| **标签化参数注入** | 用户选择的4轴标签自动注入Prompt |
| **A/B测试** | 支持Prompt变体对比，记录生成质量 |
| **用户Prompt记录** | 保存每次生成的完整Prompt，支持回溯和复用 |
| **Prompt长度优化** | 自动截断过长的Prompt，保护Token预算 |

### 5.5 数据模型

#### 生成任务表 `gen_tasks`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 任务ID |
| `user_id` | BIGINT FK | 用户ID |
| `project_id` | BIGINT FK | 所属项目（可选） |
| `gen_type` | ENUM('topic','synopsis','outline','episode','storyboard','promotion','quick') | 生成类型 |
| `storyboard_tier` | ENUM('A','B','C') | 分镜档位 |
| `input_params` | JSON | 输入参数（创意/标签/平台/风格） |
| `output_data` | JSON | 生成结果 |
| `prompt_used` | TEXT | 使用的Prompt |
| `model_used` | VARCHAR(100) | 使用的模型 |
| `status` | ENUM('pending','processing','completed','failed','cancelled') | — |
| `tokens_used` | INT | 消耗Token数 |
| `duration_ms` | INT | 耗时(ms) |
| `error_msg` | TEXT | 错误信息 |
| `created_at` | DATETIME | — |
| `updated_at` | DATETIME | — |

---

## 6. 服务三：剧本仓库服务

> **服务ID**：`script-repo-svc`  
> **优先级**：P0  
> **职责**：剧本CRUD、4轴标签分类、版本管理、L0-L4资产成熟度追踪、连续性状态管理

### 6.1 核心API

| 方法 | 路径 | 说明 | 版本 |
|------|------|------|:---:|
| POST | `/api/v1/script/repo/scripts` | 创建/保存剧本 | V1.0 |
| GET | `/api/v1/script/repo/scripts` | 剧本列表（支持标签筛选） | V1.0 |
| GET | `/api/v1/script/repo/scripts/:id` | 剧本详情 | V1.0 |
| PUT | `/api/v1/script/repo/scripts/:id` | 更新剧本 | V1.0 |
| DELETE | `/api/v1/script/repo/scripts/:id` | 删除剧本（软删除） | V1.0 |
| PUT | `/api/v1/script/repo/scripts/:id/tags` | 更新4轴标签 | V1.0 |
| GET | `/api/v1/script/repo/scripts/:id/versions` | 版本历史 | V1.0 |
| POST | `/api/v1/script/repo/scripts/:id/versions` | 创建新版本 | V1.0 |
| POST | `/api/v1/script/repo/scripts/:id/versions/:vid/restore` | 还原版本 | V1.0 |
| PUT | `/api/v1/script/repo/scripts/:id/status` | 更新状态（草稿→审核→上架） | V1.0 |
| 🆕 GET | `/api/v1/script/repo/assets` | 资产列表（角色/场景/道具） | V1.0 |
| 🆕 POST | `/api/v1/script/repo/assets/character` | 创建角色资产 | V1.0 |
| 🆕 POST | `/api/v1/script/repo/assets/scene` | 创建场景资产 | V1.0 |
| 🆕 PUT | `/api/v1/script/repo/assets/:type/:id/maturity` | 更新资产成熟度L0-L4 | V1.0 |
| 🆕 PUT | `/api/v1/script/repo/assets/:type/:id/lock` | 锁定资产（L4） | V1.2 |
| 🆕 GET | `/api/v1/script/repo/continuity/:project_id` | 获取连续性状态表 | V1.2 |
| 🆕 PUT | `/api/v1/script/repo/continuity/:project_id` | 更新连续性状态 | V1.2 |

### 6.2 数据模型

#### 剧本表 `scripts`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 剧本ID |
| `uuid` | VARCHAR(36) UK | 对外UUID |
| `project_id` | VARCHAR(50) | 项目ID |
| `title` | VARCHAR(200) | 剧本名称 |
| `author_user_id` | BIGINT FK | 作者ID |
| `owner_user_id` | BIGINT FK | 当前拥有者（购买后变更） |
| `owner_type` | ENUM('personal','enterprise') | 拥有者类型 |
| `enterprise_id` | BIGINT FK | 所属企业 |
| `episode_count` | INT | 总集数 |
| `completed_episodes` | INT | 已完成集数 |
| `total_words` | INT | 总字数 |
| `cover_image_url` | VARCHAR(500) | 封面图 |
| `synopsis` | TEXT | 故事梗概 |
| 🆕 `genre_tag` | VARCHAR(50) | 题材标签（4轴之一） |
| 🆕 `plot_tags` | JSON | 情节标签（最多3） |
| 🆕 `tone_tags` | JSON | 情绪标签（最多3） |
| 🆕 `setting_tag` | VARCHAR(50) | 时空标签（4轴之一） |
| `source` | ENUM('ai_generated','purchased','uploaded') | 剧本来源 |
| `status` | ENUM('draft','pending_review','listed','sold','delisted') | 状态 |
| `current_version` | VARCHAR(20) | 当前版本号 |
| `created_at` | DATETIME | — |
| `updated_at` | DATETIME | — |

#### 剧本版本表 `script_versions`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | — |
| `script_id` | BIGINT FK | 剧本ID |
| `version` | VARCHAR(20) | 版本号（v1.0, v1.1, ...） |
| `content` | LONGTEXT | 完整剧本内容（含分镜数据） |
| `change_summary` | VARCHAR(500) | 变更说明 |
| `created_by` | BIGINT FK | 创建人 |
| `created_at` | DATETIME | — |

#### 🆕 资产表 `assets`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | — |
| `asset_id` | VARCHAR(50) UK | 统一ID（CH_xxx / LOC_xxx / PROP_xxx） |
| `asset_type` | ENUM('character','scene','prop','voice','style') | — |
| `name` | VARCHAR(200) | 资产名称 |
| `owner_user_id` | BIGINT FK | 所有者 |
| `enterprise_id` | BIGINT FK | 所属企业（企业共享资产） |
| `maturity_level` | ENUM('L0','L1','L2','L3','L4') | 成熟度 |
| `is_locked` | BOOLEAN | 是否锁定 |
| `face_id` | VARCHAR(50) | 角色资产—面部ID |
| `costume_id` | VARCHAR(50) | 角色资产—服装ID |
| `voice_id` | VARCHAR(50) | 角色资产—声音ID |
| `location_id` | VARCHAR(50) | 场景资产—场景ID |
| `description` | TEXT | 文字锚点描述 |
| `reference_image_urls` | JSON | 参考图URL列表 |
| `consistency_prompt` | TEXT | 一致性提示词 |
| `seed_value` | BIGINT | 固定种子值 |
| `metadata` | JSON | 扩展元数据 |
| `is_public` | BOOLEAN | 是否公开（可上架到资产市场） |
| `created_at` | DATETIME | — |
| `updated_at` | DATETIME | — |

#### 🆕 连续性状态表 `continuity_states`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | — |
| `project_id` | VARCHAR(50) | 项目ID |
| `episode_id` | VARCHAR(20) | 集ID |
| `state_type` | ENUM('character','relation','prop','foreshadow','info','voice','scene','asset') | — |
| `target_id` | VARCHAR(100) | 对象ID |
| `start_state` | TEXT | 开始状态 |
| `end_state` | TEXT | 结束状态 |
| `must_inherit` | BOOLEAN | 是否必须继承 |
| `risk` | VARCHAR(200) | 风险标记 |
| `created_at` | DATETIME | — |

---

## 7. 服务四：交易与支付服务

> **服务ID**：`trade-svc`  
> **优先级**：P1  
> **职责**：剧本交易市场、三级授权管理、支付集成、订单管理、收益结算

### 7.1 核心API

| 方法 | 路径 | 说明 | 版本 |
|------|------|------|:---:|
| GET | `/api/v1/trade/market/search` | 市场搜索（4轴标签+全文） | V1.1 |
| GET | `/api/v1/trade/market/scripts/:id` | 剧本详情（含试读） | V1.1 |
| GET | `/api/v1/trade/market/scripts/:id/preview` | 试读剧本（前1-3集） | V1.1 |
| POST | `/api/v1/trade/orders` | 创建订单 | V1.1 |
| GET | `/api/v1/trade/orders/:id` | 订单详情 | V1.1 |
| POST | `/api/v1/trade/orders/:id/pay` | 发起支付 | V1.1 |
| GET | `/api/v1/trade/orders` | 我的订单列表 | V1.1 |
| 🆕 POST | `/api/v1/trade/enterprise/purchase-request` | 企业成员采购申请 | V1.1 |
| 🆕 PUT | `/api/v1/trade/enterprise/purchase-request/:id/approve` | 审批采购申请 | V1.1 |
| GET | `/api/v1/trade/sales` | 我的售卖数据 | V1.1 |
| GET | `/api/v1/trade/earnings` | 收益明细 | V1.1 |
| POST | `/api/v1/trade/earnings/withdraw` | 提现申请 | V1.1 |

### 7.2 数据模型

#### 订单表 `orders`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 订单ID |
| `order_no` | VARCHAR(32) UK | 订单号 |
| `buyer_user_id` | BIGINT FK | 买家 |
| `buyer_enterprise_id` | BIGINT FK | 买家所属企业 |
| `seller_user_id` | BIGINT FK | 卖家 |
| `script_id` | BIGINT FK | 剧本ID |
| `license_type` | ENUM('normal','exclusive','buyout') | 授权类型 |
| `amount` | DECIMAL(10,2) | 订单金额 |
| `platform_fee` | DECIMAL(10,2) | 平台手续费 |
| `seller_income` | DECIMAL(10,2) | 卖家收入 |
| `status` | ENUM('pending','paid','completed','refunded','cancelled') | — |
| `payment_method` | ENUM('wechat','alipay') | — |
| `paid_at` | DATETIME | — |
| `created_at` | DATETIME | — |

#### 🆕 企业采购申请表 `enterprise_purchase_requests`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | — |
| `enterprise_id` | BIGINT FK | — |
| `requester_user_id` | BIGINT FK | 申请人 |
| `script_id` | BIGINT FK | 剧本ID |
| `license_type` | ENUM | 授权类型 |
| `amount` | DECIMAL(10,2) | 金额 |
| `budget_remaining` | DECIMAL(10,2) | 本次采购后剩余预算 |
| `status` | ENUM('pending','approved','rejected','cancelled') | — |
| `approver_user_id` | BIGINT FK | 审批人 |
| `approval_note` | VARCHAR(500) | 审批意见 |
| `created_at` | DATETIME | — |

### 7.3 支付流程

```
用户下单 → 创建订单(status:pending, 15分钟过期)
    → 调用支付渠道（微信/支付宝）统一下单
        → 用户支付
            → 支付回调(webhook) → 验签
                → 更新订单(status:paid)
                → 剧本所有权转移（调用 script-repo-svc）
                → 发布 OrderPaid 事件
                → 卖家收入计入余额
```

### 7.4 搜索引擎（Elasticsearch）

交易市场搜索由ES提供：

**索引 `script_market`**：
```json
{
  "script_id": 12345,
  "title": "霸道总裁的替身新娘",
  "author_name": "编剧小王",
  "genre_tag": "言情",
  "plot_tags": ["重生", "先婚后爱"],
  "tone_tags": ["甜宠", "爽文"],
  "setting_tag": "现代",
  "episode_count": 40,
  "license_types": ["normal", "exclusive", "buyout"],
  "price_normal": 29.9,
  "price_exclusive": 199.9,
  "price_buyout": 999.9,
  "rating": 4.8,
  "sales_count": 128,
  "status": "listed",
  "created_at": "2026-06-01T00:00:00Z"
}
```

---

## 8. 服务五：AI资产市场服务

> **服务ID**：`asset-market-svc`  
> **优先级**：P1  
> **职责**：风格模型市场、角色/场景资产上架、提示词市场、音色/BGM/音效库

### 8.1 核心API

| 方法 | 路径 | 说明 | 版本 |
|------|------|------|:---:|
| GET | `/api/v1/asset/market/search` | 资产搜索（分类/标签） | V1.1 |
| GET | `/api/v1/asset/market/models` | 风格模型列表 | V1.1 |
| GET | `/api/v1/asset/market/models/:id` | 模型详情 | V1.1 |
| POST | `/api/v1/asset/market/models/:id/apply` | 应用模型到画布 | V1.1 |
| GET | `/api/v1/asset/market/characters` | 角色资产列表 | V1.1 |
| GET | `/api/v1/asset/market/scenes` | 场景资产列表 | V1.1 |
| GET | `/api/v1/asset/market/prompts` | 提示词市场列表 | V1.1 |
| GET | `/api/v1/asset/market/voices` | 音色库列表 | V1.1 |
| GET | `/api/v1/asset/market/sounds` | 音效/BGM库列表 | V1.1 |
| POST | `/api/v1/asset/market/publish` | 上架资产 | V1.1 |
| PUT | `/api/v1/asset/market/assets/:id` | 编辑资产信息 | V1.1 |
| POST | `/api/v1/asset/market/assets/:id/download` | 下载资产 | V1.1 |
| POST | `/api/v1/asset/market/assets/:id/favorite` | 收藏资产 | V1.1 |
| GET | `/api/v1/asset/market/my/assets` | 我的资产（已上架） | V1.1 |
| GET | `/api/v1/asset/market/my/favorites` | 我的收藏 | V1.1 |
| GET | `/api/v1/asset/market/my/downloads` | 我的下载记录 | V1.1 |

### 8.2 数据模型

#### 市场资产表 `market_assets`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | — |
| `asset_type` | ENUM('checkpoint','lora','style_pack','character','scene','prompt','voice','bgm','sfx') | — |
| `name` | VARCHAR(200) | — |
| `description` | TEXT | — |
| `author_user_id` | BIGINT FK | 发布者 |
| `preview_urls` | JSON | 预览图/音频 |
| `tags` | JSON | 分类标签 |
| `price` | DECIMAL(10,2) | 价格（0=免费） |
| `download_count` | INT | 下载次数 |
| `use_count` | INT | 使用次数 |
| `rating` | DECIMAL(2,1) | 评分 |
| `status` | ENUM('pending','published','delisted') | — |
| `metadata` | JSON | 资产元数据（模型参数/触发词/推荐设置） |
| `created_at` | DATETIME | — |

### 8.3 文件存储策略

| 资产类型 | 文件格式 | 存储路径 | CDN |
|------|---------|---------|:---:|
| Checkpoint模型 | `.safetensors` / `.ckpt` | `/models/checkpoints/{id}/` | ✅ |
| LoRA模型 | `.safetensors` | `/models/lora/{id}/` | ✅ |
| 角色/场景图 | `.png` / `.webp` | `/assets/images/{type}/{id}/` | ✅ |
| 提示词 | `.json` | 存数据库，无需文件 | — |
| 音色配置文件 | `.json` + `.wav`(参考) | `/assets/voices/{id}/` | ✅ |
| BGM/音效 | `.mp3` / `.wav` | `/assets/audio/{type}/{id}/` | ✅ |

---

## 9. 服务六：画布与视频工作台服务（V1.3增强版）

> **服务ID**：`canvas-svc`  
> **优先级**：P0  
> **职责**：画布状态管理、分镜编排、关键帧生成、时间轴合成、视频渲染、成片导出  
> 🆕 **V1.3增强**：无限画布节点引擎、脚本节点流水线、图片/视频/音频节点增强、全能参考视频、多副本并行生成、资产双向联动、工作流模板、AI导演Agent联动。详见《后端产品功能设计_V1.5.md》第9章

### 9.1 核心API

| 方法 | 路径 | 说明 | 版本 |
|------|------|------|:---:|
| POST | `/api/v1/canvas/projects` | 创建画布项目 | V1.0 |
| GET | `/api/v1/canvas/projects/:id` | 获取画布项目（含完整状态） | V1.0 |
| PUT | `/api/v1/canvas/projects/:id` | 更新画布项目 | V1.0 |
| POST | `/api/v1/canvas/projects/:id/import-script` | 从仓库导入剧本 | V1.0 |
| GET | `/api/v1/canvas/projects/:id/shots` | 分镜卡片列表 | V1.0 |
| POST | `/api/v1/canvas/projects/:id/shots` | 创建/导入分镜 | V1.0 |
| PUT | `/api/v1/canvas/projects/:id/shots/:shot_id` | 更新分镜卡片 | V1.0 |
| PUT | `/api/v1/canvas/projects/:id/shots/reorder` | 分镜卡片排序 | V1.0 |
| DELETE | `/api/v1/canvas/projects/:id/shots/:shot_id` | 删除分镜 | V1.0 |
| POST | `/api/v1/canvas/projects/:id/shots/:shot_id/generate` | 生成当前分镜画面 | V1.0 |
| POST | `/api/v1/canvas/projects/:id/shots/batch-generate` | 批量生成分镜 | V1.2 |
| PUT | `/api/v1/canvas/projects/:id/shots/:shot_id/keyframe` | 更新关键帧（首帧/尾帧） | V1.0 |
| POST | `/api/v1/canvas/projects/:id/shots/:shot_id/inpaint` | 画布内Inpaint重绘 | V1.0 |
| POST | `/api/v1/canvas/projects/:id/shots/:shot_id/outpaint` | 画布内Outpaint扩图 | V1.2 |
| PUT | `/api/v1/canvas/projects/:id/timeline` | 更新时间轴 | V1.0 |
| POST | `/api/v1/canvas/projects/:id/timeline/dub` | 生成配音 | V1.0 |
| POST | `/api/v1/canvas/projects/:id/timeline/subtitle` | 生成字幕 | V1.0 |
| POST | `/api/v1/canvas/projects/:id/compose` | 合成视频 | V1.0 |
| GET | `/api/v1/canvas/projects/:id/compose/:task_id` | 查询合成进度 | V1.0 |
| POST | `/api/v1/canvas/projects/:id/export` | 导出成片 | V1.0 |
| GET | `/api/v1/canvas/export/:task_id` | 查询导出进度 | V1.0 |
| GET | `/api/v1/canvas/export/:task_id/download` | 下载导出文件 | V1.0 |

### 9.2 画布状态管理

画布项目是一个复杂的实时协作状态，采用 **CRDT (Conflict-free Replicated Data Type)** 数据结构保证多端同步。

#### 画布项目数据模型 `canvas_projects`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | — |
| `uuid` | VARCHAR(36) UK | — |
| `user_id` | BIGINT FK | — |
| `script_id` | BIGINT FK | 来源剧本 |
| `name` | VARCHAR(200) | 项目名称 |
| `canvas_state` | JSON (LONGTEXT) | 完整画布状态快照 |
| `thumbnail_url` | VARCHAR(500) | 缩略图 |
| `status` | ENUM('editing','rendering','completed') | — |
| `episode_index` | INT | 当前编辑的集数 |
| `created_at` | DATETIME | — |
| `updated_at` | DATETIME | — |

**`canvas_state` JSON结构**：
```json
{
  "version": 123,
  "shots": [
    {
      "shot_id": "EP01_SC01_SH001",
      "order": 1,
      "status": "completed",
      "keyframe_start": { "image_url": "...", "prompt": "..." },
      "keyframe_end": { "image_url": null, "prompt": "..." },
      "camera_movement": "push",
      "duration_ms": 3000,
      "characters": ["CH_LIN"],
      "scenes": ["LOC_OFFICE"],
      "dialogue": { "character": "林默", "text": "..." },
      "inpaint_regions": [],
      "layer_state": { "background": {}, "character": {}, "foreground": {} }
    }
  ],
  "timeline": {
    "video_track": [{"shot_id": "...", "start_ms": 0, "duration_ms": 3000}],
    "transition_track": [],
    "audio_track": [{"shot_id": "...", "voice_id": "...", "audio_url": "..."}],
    "subtitle_track": [{"shot_id": "...", "text": "...", "start_ms": 0, "end_ms": 3000}],
    "bgm_track": [{"music_id": "...", "start_ms": 0, "volume": 0.3}],
    "sfx_track": [{"shot_id": "...", "sfx_id": "...", "start_ms": 1500}]
  },
  "style_config": {
    "style_id": "STYLE_KMANGA",
    "resolution": "1080p",
    "aspect_ratio": "16:9",
    "fps": 25
  }
}
```

### 9.3 视频合成管线

```
用户触发合成
    → 收集所有分镜画面（已生成的关键帧图片）
    → 收集所有配音音频
    → 收集所有字幕数据
    → 收集BGM和音效
    → 提交合成任务到渲染队列
        → 渲染引擎（FFmpeg / 自研合成引擎）
            ├── 按时间轴拼接画面序列
            ├── 叠加运镜效果（Ken Burns / 推拉摇移）
            ├── 叠加转场
            ├── 混音（配音+BGM+音效）
            ├── 叠加字幕
            └── 编码输出MP4
        → 上传到OSS
        → 发布 CanvasExportCompleted 事件
```

### 9.4 导出规格矩阵

| 画幅 | 分辨率 | 码率 | 编码 | 适用平台 |
|------|--------|------|------|---------|
| 9:16 | 720×1280 | 2Mbps | H.264 | 抖音/快手(标准) |
| 9:16 | 1080×1920 | 4Mbps | H.264 | 抖音/快手(高清) |
| 16:9 | 1280×720 | 2Mbps | H.264 | B站/YouTube(标准) |
| 16:9 | 1920×1080 | 6Mbps | H.265 | B站/YouTube(高清) |
| 1:1 | 1080×1080 | 3Mbps | H.264 | 小红书/Instagram |

### 9.5 🆕 画布节点引擎 `V1.2` — 对标 LibTV 无限画布

#### 9.5.1 节点CRUD

| 方法 | 路径 | 说明 | 版本 |
|------|------|------|:---:|
| POST | `/api/v1/canvas/projects/:id/nodes` | 创建节点 | V1.2 |
| GET | `/api/v1/canvas/projects/:id/nodes` | 获取所有节点+连线 | V1.2 |
| PUT | `/api/v1/canvas/projects/:id/nodes/:nodeId` | 更新节点 | V1.2 |
| DELETE | `/api/v1/canvas/projects/:id/nodes/:nodeId` | 删除节点 | V1.2 |

#### 9.5.2 节点数据模型

**节点表 `canvas_nodes`**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | VARCHAR(50) PK | 节点ID |
| `project_id` | VARCHAR(50) FK | 所属画布项目 |
| `type` | ENUM('text','image','video','audio','script') | 节点类型 |
| `label` | VARCHAR(200) | 节点标题 |
| `x` | INT | X坐标 |
| `y` | INT | Y坐标 |
| `width` | INT | 宽度 |
| `height` | INT | 高度 |
| `data` | JSON | 节点数据(分镜表/生成参数/提示词等) |
| `style_config` | JSON | 样式配置 |
| `created_at` | DATETIME | — |
| `updated_at` | DATETIME | — |

**连线表 `canvas_edges`（历史兼容名：`canvas_connections`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | VARCHAR(50) PK | 连线ID |
| `project_id` | VARCHAR(50) FK | 所属画布项目 |
| `source_node_id` | VARCHAR(50) | 源节点 |
| `source_port` | ENUM('out') | 源端口 |
| `target_node_id` | VARCHAR(50) | 目标节点 |
| `target_port` | ENUM('in') | 目标端口 |
| `metadata` | JSON | 连线元数据 |

#### 9.5.3 工作流引擎

**工作流模板表 `canvas_workflows`**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | VARCHAR(50) PK | 工作流ID |
| `user_id` | BIGINT | 创建者 |
| `name` | VARCHAR(200) | 工作流名称 |
| `nodes_snapshot` | JSON | 节点快照 |
| `connections_snapshot` | JSON | 连线快照 |
| `created_at` | DATETIME | — |

**工作流执行逻辑**：
```
用户触发「整组执行」→ 拓扑排序节点 → 按依赖顺序依次执行
  → 上游节点完成 → 自动触发下游节点
  → 全部完成 → 通知用户
  → 部分失败 → 标记失败节点 + 继续执行无依赖节点
```

### 9.6 🆕 脚本节点流水线 `V1.2`

| 步骤 | AI模型 | 输入 | 输出 | 并发限制 |
|------|--------|------|------|:---:|
| 剧本→分镜脚本 | LLM (Claude/GPT-4) | 剧本+角色图+风格参考 | 分镜表(镜号/景别/画面/对白) | 10 |
| 分镜→批量生图 | Seedream 5.0 / Qwen Image | 分镜表+风格模型+Prompt | 分镜图像 | 20 |
| 分镜图→批量生视频 | Seedance 2.0 / Kling 3.0 | 分镜图+运镜参数+配音 | 视频片段 | 10 |
| TTS配音 | Minimax 2.8 | 台词文本+音色ID | 配音音频 | 30 |

### 9.7 🆕 导演台·3D构图服务 `V1.2`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/canvas/projects/:id/director-desk` | 创建导演台 |
| PUT | `/api/v1/canvas/projects/:id/director-desk/:deskId` | 更新3D场景 |
| POST | `/api/v1/canvas/projects/:id/director-desk/:deskId/capture` | 多视角截图 |

**3D场景数据模型**：
```json
{
  "scene": {
    "objects": [{ "type": "character|prop|building", "model_id": "...", "transform": {...} }],
    "camera": { "position": {...}, "look_at": {...}, "fov": 60 },
    "lighting": { "type": "three-point|natural|cinematic", "intensity": 1.0 }
  }
}
```

### 9.8 🆕 多模态参考引擎 `V1.2`

**支持的视频模型及能力矩阵**：

| 模型 | 文生视频 | 图生视频 | 首尾帧 | 多模态参考 | 音画同步 | 时长 |
|------|:---:|:---:|:---:|:---:|:---:|:---:|
| Seedance 2.0 🔥 | ✅ | ✅ | ✅ | ✅ (12文件) | ✅ | 2-10s |
| Kling 3.0 | ✅ | ✅ | ✅ | — | ✅ | 5-10s |
| HappyHorse 1.0 | ✅ | ✅ | — | ✅ (9图) | ✅ | 3-15s |
| Wan 2.6 | ✅ | ✅ | — | — | ✅ | 2-8s |
| Shot V2 | ✅ | ✅ | ✅ | ✅ | — | 2-8s |
| Video 3.1 | ✅ | ✅ | ✅ | — | ✅ | 2-10s |

**多模态路由策略**：
```
用户请求 → 分析参考文件组合
  → 仅图片 → Seedance 2.0 (首尾帧模式) / Kling 3.0
  → 图片+视频+音频 → Seedance 2.0 (全能参考模式)
  → 视频编辑 → HappyHorse 1.0
  → 首尾帧+Prompt → Kling 3.0 / Seedance 2.0
  → 模型不可用 → 降级到可用模型 + 通知用户
```

---

## 🆕 9.9 Agent协作服务 `V1.2` — 对标 ToonFlow 三层Agent

> **服务ID**：`agent-svc` | **优先级**：P1 | **职责**：Agent编排、任务分发、记忆管理、Skill配置

### 9.9.1 三层Agent数据流

```
用户意图 → ScriptAgent(决策层)
  → 拆解任务 → 发送到消息总线(Redis Streams)
    → ProductionAgent(执行层) 消费任务
      → 生成结果 → QualityAgent(监督层) 审阅
        → 通过 ✅ → 通知用户
        → 未通过 ❌ → 反馈ProductionAgent重生成
```

### 9.9.2 核心API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/agent/orchestrate` | 启动Agent协作 |
| GET | `/api/v1/agent/task/:id` | 查询任务状态 |
| GET | `/api/v1/agent/task/:id/review` | 监督层审阅结果 |
| POST | `/api/v1/agent/task/:id/retry` | 触发重生成 |
| PUT | `/api/v1/agent/config` | Agent配置 |

### 9.9.3 记忆存储架构

| 存储层 | 技术 | 用途 |
|------|------|------|
| **短期记忆** | Redis (Hash, TTL=会话时长) | 当前会话上下文 |
| **长期摘要** | SQLite/MySQL + ONNX向量索引 | 跨会话语义检索 |
| **项目记忆** | MySQL JSON字段 | 角色卡/世界观/伏笔表 |
| **全局记忆** | MySQL + Redis缓存 | 用户偏好/历史决策 |

### 9.9.4 Skill引擎

```
Skill Markdown文件 → 解析器(YAML frontmatter + Markdown body)
  → 变量注入引擎 → 渲染最终Prompt
    → 发送给LLM → 记录使用的Skill版本
```

### 🆕 9.9.5 V1.3增强：Agent与画布联动

V1.3 中 `agent-svc` 升级为独立服务，并与 `canvas-svc` 深度联动：

| Agent行为 | canvas-svc落盘行为 |
|---|---|
| 生成剧本结构 | 创建 text/script 节点 |
| 拆解分镜 | 更新 script 节点分镜表 |
| 提取角色 | 创建 character 节点并写入资产库 |
| 生成角色图 | 创建 image 节点并绑定 character_asset_id |
| 生成分镜图 | 创建 image 节点，与 script row 关联 |
| 生成视频 | 创建 video 节点，与 image 节点连线 |
| 合成成片 | 创建 compose 节点和 export task |
| 执行失败 | 更新节点状态为 failed |

**Skill调用画布工具**：

| Tool | 对应canvas-svc接口 |
|---|---|
| `canvas.create_node` | `POST /nodes` |
| `canvas.update_node` | `PUT /nodes/:nodeId` |
| `canvas.create_edge` | `POST /nodes/connect` |
| `canvas.run_node` | `POST /nodes/:nodeId/generate` |
| `canvas.save_asset` | `POST /nodes/:nodeId/save-asset` |
| `canvas.export` | `POST /export` |

**V1.3新增API**：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/agent/skills` | 创建/注册Skill |
| GET | `/api/v1/agent/skills` | Skill列表 |
| POST | `/api/v1/agent/skills/:id/execute` | 执行Skill |
| GET | `/api/v1/agent/executions/:id` | 查询执行日志 |
| GET | `/api/v1/agent/tools` | 获取可用Tool列表 |
| POST | `/api/v1/agent/orchestrate/canvas` | Agent编排画布任务 |

---

## 10. 服务七：工业化生产SOP服务（V1.3增强版）

> **服务ID**：`sop-svc`  
> **优先级**：P1  
> **职责**：生产准入检查、审计返工管理、版本锁定、产能估算、AI失败恢复  
> 🆕 **V1.3增强**：画布生产准入联动、生成前/视频前/合成前/导出前四级检查、画布节点质检、Agent批量执行前检查。详见《后端产品功能设计_V1.5.md》10.5-10.6节

### 10.1 核心API

| 方法 | 路径 | 说明 | 版本 |
|------|------|------|:---:|
| POST | `/api/v1/sop/check/production-readiness` | 生产准入13项检查 | V1.1 |
| GET | `/api/v1/sop/projects/:id/audit-list` | 审计返工列表 | V1.1 |
| POST | `/api/v1/sop/projects/:id/audit` | 提交审计结果 | V1.1 |
| PUT | `/api/v1/sop/projects/:id/audit/:audit_id` | 更新修复状态 | V1.1 |
| POST | `/api/v1/sop/assets/:type/:id/lock` | 锁定资产（L4） | V1.2 |
| POST | `/api/v1/sop/assets/:type/:id/unlock` | 解锁资产（触发审计） | V1.2 |
| GET | `/api/v1/sop/projects/:id/capacity` | 产能估算 | V1.2 |
| POST | `/api/v1/sop/failure/record` | 记录AI失败 | V1.2 |
| GET | `/api/v1/sop/failure/strategy` | 推荐失败恢复策略 | V1.2 |
| GET | `/api/v1/sop/versions/:project_id` | 项目版本历史 | V1.1 |
| POST | `/api/v1/sop/versions/:project_id/promote` | 版本升级（V0.1→V0.5→V1.0） | V1.1 |

### 10.2 生产准入检查引擎

```
POST /check/production-readiness
  → 加载项目当前状态
  → 逐项检查13项规则：
      1. 剧情事实是否偏移 → 对比当前版本与锁定版本
      2. 场景目标是否明确 → 检查 scene_objectives 字段
      3. Beat是否完整 → 检查 beat_table 完整性
      4. 人物关系变化是否明确 → 检查 relationship_changes
      5. 关键对白是否锁定 → 检查 dialogue_lock_status
      6. 资产ID是否完整 → 扫描所有 Character_ID/Location_ID/Voice_ID
      7. 高风险镜头是否标记 → 统计 D/E级镜头数
      8. AI提示词是否过 → 检查 prompt_length
      9. D/E级镜头是否拆分 → 检查 shot_split_strategy
      10. AI抽卡表与AI视频表是否区分 → 检查 C档表 完整性
      11. Voice_ID是否明确 → 扫描出声角色
      12. 配音字幕表是否就绪 → 检查 dub_subtitle_table
      13. 上一章状态是否继承 → 检查 continuity_state
  → 返回检查报告：{ pass: bool, failures: [...], warnings: [...] }
```

### 10.3 数据模型

#### 审计记录表 `audit_records`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | — |
| `project_id` | VARCHAR(50) | — |
| `shot_id` | VARCHAR(50) | — |
| `check_item` | VARCHAR(200) | 检查项 |
| `issue_type` | VARCHAR(100) | 问题类型 |
| `severity` | ENUM('P0','P1','P2','P3') | 严重等级 |
| `quality_grade` | ENUM('S','A','B','C','D') | 质量等级 |
| `description` | TEXT | 问题描述 |
| `fix_suggestion` | TEXT | 修复建议 |
| `responsible_role` | VARCHAR(50) | 责任岗位 |
| `fix_status` | ENUM('unfixed','fixed','reviewing','passed') | 修复状态 |
| `created_by` | BIGINT | 提交人 |
| `created_at` | DATETIME | — |

#### AI失败记录表 `ai_failure_logs`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | — |
| `project_id` | VARCHAR(50) | — |
| `shot_id` | VARCHAR(50) | — |
| `task_type` | ENUM('image_gen','video_gen','tts','compose') | — |
| `failure_reason` | VARCHAR(200) | 失败原因分类 |
| `retry_count` | INT | 当前已重试次数 |
| `recovery_action` | VARCHAR(200) | 采取的恢复策略 |
| `is_resolved` | BOOLEAN | — |
| `created_at` | DATETIME | — |

### 10.4 AI失败自动恢复状态机

```
[首次失败] → retry_count=1 → 优化Prompt参数 → 重试
[二次失败] → retry_count=2 → 检查并强化资产/参考图 → 重试
[三次失败] → retry_count=3 → 反推分镜是否过载 → 人工介入
[四次失败] → retry_count=4 → 拆镜策略 → 重试
[五次失败] → retry_count≥5 → 标记为不可自动恢复 → 通知责任人
```

### 🆕 10.5 画布生产准入联动 `V1.3`

V1.3 中 SOP 服务直接参与画布节点生产流程：

| 触发点 | 检查内容 |
|---|---|
| 脚本节点批量生图前 | 分镜表字段完整性、角色/场景/道具资产绑定 |
| 图片节点转视频前 | 首帧质量、角色一致性、场景一致性 |
| 视频节点加入时间线前 | 视频时长、画幅、清晰度、动作连续性 |
| 合成节点导出前 | 视频轨、音频轨、字幕轨、BGM完整性 |
| L4资产解锁后 | 是否影响后续镜头一致性 |
| Agent批量执行前 | 是否存在高成本、高风险、缺资产任务 |

**V1.3新增画布质检API**：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/sop/canvas/:project_id/check-before-generate` | 生成前检查 |
| POST | `/api/v1/sop/canvas/:project_id/check-before-video` | 图生视频前检查 |
| POST | `/api/v1/sop/canvas/:project_id/check-before-compose` | 合成前检查 |
| POST | `/api/v1/sop/canvas/:project_id/check-before-export` | 导出前检查 |
| GET | `/api/v1/sop/canvas/:project_id/risk-report` | 获取画布风险报告 |

---

## 11. 服务八：通知与消息服务

> **服务ID**：`notify-svc`  
> **优先级**：P0  
> **职责**：站内通知、邮件、短信、浏览器推送、消息偏好管理

### 11.1 核心API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/notify/in-app` | 站内通知列表 |
| PUT | `/api/v1/notify/in-app/:id/read` | 标记已读 |
| PUT | `/api/v1/notify/in-app/read-all` | 全部已读 |
| GET | `/api/v1/notify/preferences` | 通知偏好 |
| PUT | `/api/v1/notify/preferences` | 更新通知偏好 |

### 11.2 通知渠道与触发

| 事件 | 站内 | 邮件 | 短信 | 浏览器推送 |
|------|:---:|:---:|:---:|:---:|
| `ScriptGenerated`（剧本生成完成） | ✅ | — | — | ✅ |
| `OrderPaid`（购买成功） | ✅ | ✅ | ✅ | — |
| `CanvasExportCompleted`（导出完成） | ✅ | ✅ | — | ✅ |
| `AuditFailed`（审计失败） | ✅ | ✅ | — | — |
| `EnterpriseMemberInvited`（企业邀请） | ✅ | ✅ | ✅ | — |
| `PurchaseRequestApproved`（采购审批） | ✅ | — | — | ✅ |
| `AssetPublished`（资产上架审核） | ✅ | ✅ | — | — |

---

## 12. AI/ML推理编排层（V1.3增强版）

### 12.1 推理网关

```
                    ┌──────────────────┐
                    │   AI Router       │
                    │  (推理路由网关)    │
                    └────────┬─────────┘
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ LLM Pool     │    │ Image Gen    │    │ Video Gen    │
│              │    │ Pool         │    │ Pool         │
│ DeepSeek     │    │ SD/Flux      │    │ Kling/       │
│ GPT-4/Claude │    │ Midjourney   │    │ Seedance     │
│ 文心/通义    │    │ DALLE        │    │ Runway/Pika  │
└──────────────┘    └──────────────┘    └──────────────┘
        │                    │                    │
        ▼                    ▼                    ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ TTS Pool     │    │ ASR Pool     │    │ Safety Check │
│ 火山/阿里    │    │ 语音识别     │    │ 内容安全审核  │
└──────────────┘    └──────────────┘    └──────────────┘
```

### 12.2 推理调度策略

| 模型类型 | 提供商 | 优先级策略 | 并发控制 | 超时 |
|---------|--------|----------|:---:|:---:|
| LLM | DeepSeek (主力) / GPT-4 (高质) / Claude (分镜) | 低价优先 + 质量降级 | 全局50并发 | 120s |
| 图像生成 | SD/Flux (自建) / Midjourney (高品质) | 自建优先 + 按需升级 | 自建100并发 | 60s |
| 视频生成 | Kling / Seedance / Runway | 质量优先 + 成本控制 | 20并发 | 300s |
| TTS | 火山引擎 / 阿里云 | 低价优先 | 50并发 | 30s |

### 12.3 Token预算与成本控制

| 用户层级 | 单次Token上限 | 月Token总额 | 图像生成次数/月 | 视频生成秒数/月 |
|---------|:---:|:---:|:---:|:---:|
| 免费用户 | 8K | 100K | 30次 | — |
| 创作者会员 | 32K | 1M | 300次 | 600s |
| 企业版(5人) | 32K | 5M | 1500次 | 3000s |

### 12.4 模型版本管理

| 功能 | 说明 |
|------|------|
| **模型注册** | 注册模型名称、版本、提供商、能力标签 |
| **A/B路由** | 按比例将请求路由到不同模型版本对比效果 |
| **降级策略** | 主模型不可用时自动切换到备用模型 |
| **成本追踪** | 每次推理记录Token消耗和费用 |
| **Prompt版本** | Prompt模板版本管理，支持回滚 |

### 🆕 12.5 画布模型Adapter `V1.3`

V1.3 中画布视频生成通过统一 `Adapter` 接入不同模型能力：

- **Adapter标准接口**：`authenticate` → `uploadFile` → `estimateCost` → `createTask` → `queryTask` → `downloadResult`
- **模型能力注册表** `ai_model_capabilities`：记录每个模型的文生图/图生图/文生视频/图生视频/首尾帧/全能参考/音画同步等能力
- **画布推理路由**：按节点类型与输入组合 → 查询模型能力 → 按质量/成本/可用性排序 → 预估成本 → 用户确认 → 执行
- **画布任务队列优先级**：文本解析(P0) > 视频生成(P0)/合成(P0)/Agent(P0) > 图片生成(P1)/TTS(P1) > 多副本视频(P1)

详见《后端产品功能设计_V1.5.md》12.5-12.6节。

---

## 13. 存储架构设计

### 13.1 存储分层

```
┌─────────────────────────────────────────────────┐
│  热数据层（Redis Cluster）                        │
│  · Session/Token · 实时画布状态 · API限流计数器  │
│  · 热门搜索结果缓存 · 用户权限缓存               │
├─────────────────────────────────────────────────┤
│  温数据层（MySQL 8.0 主库集群）                    │
│  · 用户/企业/订单/剧本/资产/标签/SOP审计         │
│  · 读写分离：1写 + 2读                           │
├─────────────────────────────────────────────────┤
│  搜索引擎（Elasticsearch）                        │
│  · 剧本市场全文检索 · 资产搜索 · 日志检索        │
├─────────────────────────────────────────────────┤
│  冷数据层（对象存储 OSS/S3）                      │
│  · 图片（角色/场景/分镜关键帧）                   │
│  · 视频（成片/预览）                              │
│  · 音频（配音/BGM/音效）                          │
│  · AI模型文件（Checkpoint/LoRA）                  │
│  · 导出文件                                      │
├─────────────────────────────────────────────────┤
│  归档层（低频存储）                                │
│  · 30天前的导出文件 · 已删除数据的30天保留        │
└─────────────────────────────────────────────────┘
```

### 13.2 数据库分库分表策略

| 表 | 分片键 | 分片数 | 说明 |
|------|--------|:---:|------|
| `users` / `enterprises` | `id` | 不分片 | 核心表，数据量可控 |
| `scripts` | `user_id` | 4 | 按用户哈希 |
| `script_versions` | `script_id` | 8 | 按剧本哈希 |
| `assets` | `owner_user_id` | 4 | 按所有者哈希 |
| `gen_tasks` | `user_id` | 8 | 高频写入 |
| `orders` | `buyer_user_id` | 4 | 按买家哈希 |
| `canvas_projects` | `user_id` | 4 | 大JSON字段 |
| `audit_records` | `project_id` | 8 | 审计记录 |
| `ai_failure_logs` | `created_at` | 按月分区 | 时序数据 |

### 13.3 CDN策略

| 内容类型 | CDN缓存时间 | 说明 |
|---------|:---:|------|
| 静态资源（JS/CSS） | 7天 | 版本化URL |
| 图片（角色/场景/关键帧） | 30天 | 不常变化 |
| 视频成片 | 90天 | 用户下载 |
| 模型文件 | 30天 | 版本化发布 |
| BGM/音效 | 30天 | 免版权素材 |

---

## 14. 异步任务与消息队列

### 14.1 消息队列拓扑

```
┌───────────────────────────────────────────────┐
│               RocketMQ / Kafka                │
│                                               │
│  ┌─────────┐  ┌─────────┐  ┌─────────────┐  │
│  │ AI生成   │  │ 视频合成 │  │ 通知发送    │  │
│  │ Topic   │  │ Topic   │  │ Topic       │  │
│  └────┬────┘  └────┬────┘  └──────┬──────┘  │
│       │            │              │          │
│  ┌────┴────┐  ┌────┴────┐  ┌─────┴───────┐  │
│  │ 事件     │  │ 导出    │  │ 数据同步    │  │
│  │ Topic   │  │ Topic   │  │ Topic       │  │
│  └─────────┘  └─────────┘  └─────────────┘  │
└───────────────────────────────────────────────┘
```

### 14.2 异步任务清单

| 任务 | 触发方式 | 消费者 | 优先级 | 超时 |
|------|---------|--------|:---:|:---:|
| AI剧本生成 | 用户请求→MQ | `script-gen-worker` | 高 | 120s |
| AI图像生成 | 画布请求→MQ | `image-gen-worker` | 高 | 60s |
| AI视频生成 | 画布请求→MQ | `video-gen-worker` | 中 | 300s |
| 视频合成渲染 | 画布合成→MQ | `render-worker` | 中 | 600s |
| TTS配音生成 | 画布请求→MQ | `tts-worker` | 高 | 30s |
| 字幕自动打轴 | ASR完成→MQ | `subtitle-worker` | 中 | 30s |
| 导出文件打包 | 用户请求→MQ | `export-worker` | 低 | 300s |
| ES索引同步 | 数据变更事件 | `index-sync-worker` | 低 | 10s |
| 缩略图生成 | 图片上传事件 | `thumbnail-worker` | 低 | 10s |
| 通知发送 | 业务事件→MQ | `notify-worker` | 中 | 10s |

### 14.3 任务状态追踪

所有异步任务统一通过 `async_tasks` 表追踪：

| 字段 | 说明 |
|------|------|
| `task_id` | 任务UUID |
| `task_type` | 任务类型 |
| `user_id` | 用户ID |
| `status` | pending/processing/completed/failed/cancelled |
| `progress` | 进度百分比 |
| `input_params` | 输入参数 |
| `output_result` | 输出结果 |
| `error_message` | 错误信息 |
| `retry_count` | 重试次数 |
| `created_at` | 创建时间 |
| `completed_at` | 完成时间 |

---

## 15. 多租户架构

### 15.1 租户隔离策略

| 数据层级 | 隔离方式 | 说明 |
|---------|---------|------|
| **个人用户数据** | `owner_user_id` 字段隔离 | 剧本、资产、画布项目 |
| **企业共享数据** | `enterprise_id` 字段隔离 | 企业资产库、企业剧本 |
| **公开市场数据** | 全局共享 | 剧本市场、资产市场 |
| **数据库** | 共享数据库 + 字段隔离 | 第一周期不拆库 |

### 15.2 企业数据权限

```
企业管理员 (ent_admin)
    ├── 可查看/管理企业内所有数据
    ├── 可设定成员的预算和权限
    └── 可审批采购和导出

部门负责人 (dept_head)
    ├── 可查看/管理部门内所有数据
    └── 可审批部门内采购（金额≤设定上限）

普通成员 (writer/artist/editor)
    ├── 仅查看/操作自己的数据
    └── 部分操作需审批（采购/导出/资产修改）

跨企业隔离
    ├── 企业A成员无法访问企业B的任何数据
    └── 企业资产库仅企业内部可见
```

---

## 16. 安全与合规

### 16.1 安全措施矩阵

| 安全层面 | 措施 |
|---------|------|
| **传输安全** | 全站HTTPS、API签名验证、防重放攻击（Nonce+Timestamp） |
| **认证安全** | JWT + MFA、密码bcrypt哈希、登录失败锁定、异地登录检测 |
| **数据安全** | 敏感字段加密（手机/邮箱AES-256）、数据库TLS连接、备份加密 |
| **AI安全** | 用户输入敏感词过滤、AI生成内容安全扫描（色情/暴力/政治）、生成记录留痕 |
| **内容合规** | 剧本/资产上架前机审+人审、AI生成内容标识、投诉下架通道 |
| **API安全** | API Key签名验证、调用频率限制、IP白名单（企业版） |
| **企业安全** | 企业SSO支持、成员操作日志审计、数据导出权限管控 |

### 16.2 数据保留与删除

| 数据类型 | 保留策略 | 删除方式 |
|---------|---------|---------|
| 用户账号 | 注销后保留30天 | 30天后物理删除 |
| 剧本/资产 | 用户主动删除后保留30天 | 回收站机制 |
| 订单/交易记录 | 保留5年（财务合规） | — |
| AI生成记录 | 保留1年 | 滚动删除 |
| 导出文件 | 保留30天 | 自动清理 |
| 操作日志 | 保留6个月 | 按月归档 |

---

## 17. 监控与可观测性

### 17.1 四层监控体系

```
┌─────────────────────────────────────────┐
│  业务监控                                │
│  · 注册转化率 · 生成成功率 · 付费转化率  │
│  · 各模块使用量 · 活跃用户数             │
├─────────────────────────────────────────┤
│  应用监控                                │
│  · API QPS/延迟/错误率                  │
│  · 服务健康状态 · 依赖调用链             │
├─────────────────────────────────────────┤
│  基础设施监控                             │
│  · CPU/内存/磁盘/网络 · K8s Pod状态     │
│  · MySQL慢查询 · Redis命中率            │
├─────────────────────────────────────────┤
│  AI推理监控                              │
│  · 模型延迟 · Token消耗 · 生成成功率    │
│  · 模型降级触发次数 · 成本统计          │
└─────────────────────────────────────────┘
```

### 17.2 告警规则

| 告警项 | 阈值 | 级别 | 通知方式 |
|--------|------|:---:|------|
| API错误率 > 5% | 连续5分钟 | P0 | 电话+IM |
| AI生成成功率 < 80% | 连续10分钟 | P1 | IM+邮件 |
| 支付回调延迟 > 5分钟 | 任意 | P0 | 电话+IM |
| MySQL主库CPU > 80% | 连续5分钟 | P1 | IM |
| Redis内存 > 80% | 连续5分钟 | P2 | 邮件 |
| 导出队列积压 > 100 | 连续10分钟 | P2 | IM |

---

## 18. 部署架构与基础设施

### 18.1 K8s部署拓扑

```
┌─────────────────────────────────────────────────┐
│                 Kubernetes Cluster               │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │  Ingress Controller (Nginx/Traefik)      │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  ┌──────────┐ ┌──────────┐ ┌──────────────┐   │
│  │ Gateway  │ │ BFF Pods │ │ Service Pods  │   │
│  │ Pods (2) │ │ (3)      │ │ (per svc 2-5) │   │
│  └──────────┘ └──────────┘ └──────────────┘   │
│                                                 │
│  ┌──────────┐ ┌──────────┐ ┌──────────────┐   │
│  │ Worker   │ │ CronJob  │ │ AI推理Worker  │   │
│  │ Pods     │ │ Pods     │ │ Pods(自建GPU) │   │
│  └──────────┘ └──────────┘ └──────────────┘   │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │  StatefulSet (MySQL/Redis/ES/MQ)         │   │
│  │  · 生产环境：云服务托管                  │   │
│  │  · 测试环境：K8s内StatefulSet            │   │
│  └─────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
```

### 18.2 环境规划

| 环境 | 用途 | 规格 | 数据 |
|------|------|------|------|
| **dev** | 开发联调 | 单副本 | Mock数据 |
| **staging** | 预发测试 | 2副本 | 脱敏生产数据 |
| **production** | 生产环境 | 3-5副本 | 生产数据 |
| **dr** | 灾备环境 | 最小规格 | 准实时同步 |

### 18.3 CI/CD流水线

```
代码提交 → 单元测试 → 构建镜像 → 推送镜像仓库
    → 部署Staging → 自动化回归测试
        → 人工审批 → 灰度发布(10%→50%→100%)
            → 生产健康检查 → 全量上线
```

---

## 19. 版本迭代计划（后端）

### 19.1 V1.0 — 后端MVP

| 服务 | 范围 |
|------|------|
| **user-svc** | 注册(手机/邮箱/微信)、登录、JWT认证、个人中心、基础权限 |
| **script-gen-svc** | LLM编排、快速模式、Step1-5(A档)、异步任务队列 |
| **script-repo-svc** | 剧本CRUD、4轴标签、基础版本管理、L0-L2资产 |
| **canvas-svc** | 画布CRUD、分镜卡片管理、首帧生成、时间轴、图文合成、MP4导出 |
| **notify-svc** | 站内通知、浏览器推送 |
| **基础设施** | MySQL主从、Redis集群、OSS、MQ、K8s集群、API Gateway、CI/CD |

**不包含**：交易、资产市场、企业中心、B/C档分镜、生产SOP、ES搜索

### 19.2 V1.1 — 交易+资产+企业

| 新增/升级 | 范围 |
|----------|------|
| **trade-svc** | 市场搜索(ES)、订单、支付(微信/支付宝)、三级授权 |
| **asset-market-svc** | 模型/资产/提示词/音色市场、上架审核 |
| **user-svc 升级** | 企业注册认证、组织管理、成员权限、采购审批、企业工作台 |
| **script-gen-svc 升级** | B档分镜生成、分镜升档API |
| **sop-svc** | 生产准入13项检查、P0-P3审计返工、版本锁定 |
| **notify-svc 升级** | 邮件+短信通知 |

### 19.3 V1.2 — 完整生产体系

| 新增/升级 | 范围 |
|----------|------|
| **canvas-svc 升级** | Outpaint、图生视频、首尾帧视频、批量生成、多集Tab |
| **script-gen-svc 升级** | C档生产三表、Camera Lock、动作链表 |
| **asset-market-svc 升级** | 角色LoRA市场（使用预训练模型，训练功能第二周期） |
| **user-svc 升级** | 企业SSO、11项细粒度权限、企业数据报表、企业API管理 |
| **sop-svc 升级** | 产能估算、AI失败自动恢复状态机、连续性状态自动追踪 |
| **基础设施升级** | GPU节点(自建推理)、HPA弹性伸缩、灾备环境 |

### 🆕 19.4 V1.3 — AI视频创作自由画布增强版

| 模块 | 范围 | 优先级 |
|---|---|:---:|
| **无限画布** | 节点坐标、缩放、拖拽、框选、连线、分组、自动排版、快照、操作日志 | P0 |
| **节点体系** | text/script/image/video/audio/character/scene/prop/compose/workflow/agent 11种节点 | P0 |
| **脚本节点** | 剧本导入、分镜表解析、Prompt生成、批量生图、批量生视频、资产提取 | P0 |
| **图片节点** | 文生图、图生图、高清放大、扩图、局部重绘、抠图、镜头聚焦、多角度、打光 | P0 |
| **视频节点** | 图生视频、文生视频、首尾帧、全能参考、视频续写、视频高清、视频解析 | P0 |
| **多副本生成** | 同参数多Seed/多模型并行抽卡、设为主结果 | P1 |
| **资产联动** | 节点保存资产、资产拖入画布、角色/场景/道具L0-L4成熟度双向绑定 | P0 |
| **时间线合成** | 视频轨、音频轨、字幕轨、转场、合成节点、多规格导出 | P0 |
| **工作流模板** | 打组、保存、复用、整组执行、失败节点处理 | P1 |
| **agent-svc独立** | AI导演、Skill调用、Tool Router、执行日志、画布节点回写 | P1 |
| **模型Adapter** | 第三方模型统一接入、模型能力注册、费用预估、降级路由 | P1 |
| **SOP联动增强** | 生成前/视频前/合成前/导出前四级检查、画布节点质检、失败恢复增强 | P1 |
| **算力管理** | 任务费用预估、用户确认、余额校验、成本追踪 | P0 |

---

## 20. 附录：核心数据模型ER图

```
┌──────────┐       ┌──────────────┐       ┌──────────────┐
│  users   │───┬───│ enterprise_  │───┬───│ enterprise_  │
│          │   │   │ members      │   │   │ purchase_    │
│  id (PK) │   │   │              │   │   │ requests     │
│  uuid    │   │   │ ent_id (FK)  │   │   │              │
│  phone   │   │   │ user_id (FK) │   │   │ ent_id (FK)  │
│  email   │   │   │ role         │   │   │ requester(FK)│
│  ...     │   │   │ permissions  │   │   │ script_id(FK)│
└────┬─────┘   │   └──────────────┘   │   │ amount       │
     │         │                      │   └──────────────┘
     │         │   ┌──────────────┐   │
     │         ├───│ enterprises  │───┤
     │         │   │              │   │
     │         │   │ id (PK)      │   │
     │         │   │ owner_id(FK) │   │
     │         │   │ name         │   │
     │         │   │ verify_status│   │
     │         │   └──────────────┘   │
     │         │                      │
┌────┴─────┐  │   ┌──────────────┐   │   ┌──────────────┐
│ scripts  │  │   │ script_      │   │   │ gen_tasks    │
│          │  │   │ versions     │   │   │              │
│ id (PK)  │  │   │              │   │   │ id (PK)      │
│ author   │  │   │ script_id(FK)│   │   │ user_id (FK) │
│  _id (FK)│  │   │ version      │   │   │ gen_type     │
│ owner    │  │   │ content      │   │   │ input_params │
│  _id (FK)│  │   │ change_      │   │   │ output_data  │
│ ent_id   │  │   │   summary    │   │   │ status       │
│  (FK)    │  │   └──────────────┘   │   └──────────────┘
│ tags(JSON│  │                      │
│ status   │  │   ┌──────────────┐   │   ┌──────────────┐
└────┬─────┘  │   │ assets       │   │   │ orders       │
     │        │   │              │   │   │              │
     │        │   │ id (PK)      │   │   │ id (PK)      │
     │        │   │ asset_id(UK) │   │   │ buyer_id(FK) │
     │        │   │ owner_id(FK) │   │   │ seller_id(FK)│
     │        │   │ ent_id (FK)  │   │   │ script_id(FK)│
     │        │   │ maturity     │   │   │ license_type │
     │        │   │ is_locked    │   │   │ amount       │
     │        │   │ ...          │   │   │ status       │
     │        │   └──────┬───────┘   │   └──────────────┘
     │        │          │           │
     │        │   ┌──────┴───────┐   │   ┌──────────────┐
     │        │   │ market_      │   │   │ canvas_      │
     │        └───│ assets       │   └───│ projects     │
     │            │              │       │              │
     │            │ id (PK)      │       │ id (PK)      │
     │            │ asset_type   │       │ user_id (FK) │
     │            │ author_id(FK)│       │ script_id(FK)│
     │            │ price        │       │ canvas_state │
     │            │ ...          │       │ status       │
     │            └──────────────┘       └──────┬───────┘
     │                                          │
     │   ┌──────────────┐       ┌──────────────┤
     │   │ audit_records│       │ continuity_  │
     │   │              │       │ states       │
     │   │ id (PK)      │       │              │
     │   │ project_id   │       │ project_id   │
     │   │ shot_id      │       │ state_type   │
     │   │ severity     │       │ target_id    │
     │   │ fix_status   │       │ end_state    │
     │   └──────────────┘       └──────────────┘
     │
     │   ┌──────────────┐       ┌──────────────┐
     └───│ ai_failure_  │       │ notify_      │
         │ logs         │       │ records      │
         │ id (PK)      │       │ id (PK)      │
         │ shot_id      │       │ user_id (FK) │
         │ retry_count  │       │ channel      │
         │ is_resolved  │       │ is_read      │
         └──────────────┘       └──────────────┘
```

---

> **文档状态**：v0.1 后端初稿  
> **编写日期**：2026-06-08  
> **文档定位**：与前端产品功能设计 v0.5 对应的后端架构与服务设计  
> **后续步骤**：API详细规格(OpenAPI 3.0)、数据字典、接口Mock、技术选型评审、开发排期
