# New API — AI 大模型网关与管理平台 产品功能全景文档

> **产品定位**：新一代大模型网关与 AI 资产管理系统，面向企业与开发者提供统一的 AI API 接入、多模型管理、额度分配、用量统计、成本核算一站式解决方案。
> **产品版本**：New API (基于 one-api 的下一代演进版本) | **官网文档**：https://docs.newapi.pro
> **技术栈**：Go 1.22+ / Gin / GORM（后端）+ React 19 / TypeScript / Rsbuild / Base UI / Tailwind CSS（前端）
> **部署端口**：3000（默认）/ 当前实例运行于 `localhost:3001`
> **数据库兼容**：SQLite / MySQL / PostgreSQL 三数据库全兼容
> **文档日期**：2026-07-05

---

## 目录

1. [产品概览](#1-产品概览)
2. [Chat — 对话与调试](#2-chat--对话与调试)
3. [General — 通用功能](#3-general--通用功能)
4. [Personal — 个人中心](#4-personal--个人中心)
5. [Admin — 管理后台](#5-admin--管理后台)
6. [System Settings — 系统设置](#6-system-settings--系统设置)
7. [用户认证与安全](#7-用户认证与安全)
8. [计费与支付系统](#8-计费与支付系统)
9. [上游厂商适配](#9-上游厂商适配)
10. [API 格式支持](#10-api-格式支持)
11. [部署与运维](#11-部署与运维)
12. [附录：完整路由表](#12-附录完整路由表)

---

## 1. 产品概览

### 1.1 产品定位

**New API** 是一个面向企业和开发者的 **AI API 统一网关**。它在前端提供类 OpenAI 的 API 接口，在后端聚合 40+ 家 AI 厂商（OpenAI、Claude、Gemini、Azure、AWS Bedrock、DeepSeek 等），支持统一鉴权、额度分配、用量统计、成本核算、多租户管理。

### 1.2 核心价值主张

| 维度 | 能力 |
|------|------|
| **统一接入** | 一个 API Key 调用 40+ 家 AI 模型，无需分别对接 |
| **智能路由** | 渠道加权随机、失败自动重试、用户级别模型限流 |
| **格式转换** | OpenAI ⇄ Claude ⇄ Gemini 请求/响应自动互转 |
| **成本管控** | 按量/按次成本核算，支持缓存命中计费、灵活计费策略 |
| **多租户管理** | 用户分组、令牌权限、模型限制、组织级额度分配 |
| **安全合规** | JWT、WebAuthn/Passkeys、OAuth 多平台、2FA、内容安全 |

### 1.3 技术架构

```
Client (API Consumer)
        │
        ▼
   New API Gateway (Go)
        │
        ├── Auth Layer (JWT / API Key / OAuth)
        ├── Rate Limit / Quota
        ├── Router (weighted random / affinity)
        ├── Format Adapter (OpenAI ⇄ Claude ⇄ Gemini)
        │
        ▼
   Upstream Providers (40+)
   OpenAI │ Claude │ Gemini │ DeepSeek │ Azure │ AWS Bedrock │ ...
```

### 1.4 角色体系

| 角色 | 权限范围 |
|------|----------|
| **Guest** | 仅公开页面（定价、排行） |
| **普通用户 (User)** | Chat、Playground、API Keys、用量日志、钱包、个人中心 |
| **管理员 (Admin)** | 渠道管理、模型管理、用户管理、兑换码、订阅、系统设置 |
| **超级管理员 (Super Admin)** | 全部管理员权限 + 系统信息（运行时指标） |

### 1.5 多语言支持

| 语言 | 覆盖范围 |
|------|----------|
| 🇨🇳 简体中文 | 全量（默认 fallback） |
| 🇺🇸 English | 全量（base locale） |
| 🇫🇷 Français | 前端 UI |
| 🇯🇵 日本語 | 前端 UI |
| 🇷🇺 Русский | 前端 UI |
| 🇻🇳 Tiếng Việt | 前端 UI |

---

## 2. Chat — 对话与调试

### 2.1 Playground（/playground）

> API 交互式调试台，对标 OpenAI Playground

| 功能 | 说明 |
|------|------|
| **模型选择** | 从已配置的模型列表中选择 |
| **参数调节** | temperature、top_p、max_tokens 等实时调参 |
| **消息构建** | 多轮对话消息（system / user / assistant）自由编辑 |
| **实时流式** | 支持 SSE Streaming 响应 |
| **请求/响应查看** | 原始 JSON 请求体和响应体展示 |
| **代码生成** | 根据当前参数生成 cURL / Python / Node.js 调用代码 |

### 2.2 Chat Presets（聊天预设）

> 侧边栏动态加载的对话预设入口

- 预定义对话场景快捷入口
- 由后端/管理员动态配置
- 每个 Preset 可预设：模型、system prompt、参数

### 2.3 Chat Session（/chat/$chatId）

- 独立的多轮对话会话页面
- 消息持久化存储
- 支持分享链接

### 2.4 Chat to Link（/chat2link）

- 将对话内容转为可分享的链接

---

## 3. General — 通用功能

### 3.1 Overview（/dashboard/overview）

> 系统总览仪表盘

| 指标卡片 | 说明 |
|----------|------|
| 今日调用量 | API 调用总次数 |
| 今日 Token 消耗 | 输入/输出 Token 统计 |
| 活跃用户数 | 今日有调用的用户数 |
| 渠道健康度 | 各渠道可用性概览 |
| 费用趋势 | 按时间维度的费用曲线图 |
| 模型用量排行 | 各模型调用量 Top N |

### 3.2 Dashboard（/dashboard/models）

> 数据看板

| 功能 | 说明 |
|------|------|
| 模型维度统计 | 按模型查看调用量/Token/费用 |
| 用户维度统计 | 按用户查看用量排行 |
| 时间范围筛选 | 今日/近7天/近30天/自定义范围 |
| 图表可视化 | ECharts 驱动的折线图、柱状图、饼图 |
| 数据导出 | 支持导出 CSV/Excel |

### 3.3 API Keys（/keys）

> API 令牌管理，这是面向开发者的核心功能

| 功能 | 说明 |
|------|------|
| **创建令牌** | 生成新的 API Key（sk- 前缀） |
| **令牌列表** | 查看所有令牌及其状态（启用/禁用） |
| **额度设置** | 设置令牌的调用额度上限（无限额/固定额度） |
| **过期时间** | 设置令牌有效期 |
| **模型限制** | 限制该令牌可调用的模型列表 |
| **IP 白名单** | 限制允许调用的 IP 范围 |
| **分组管理** | 将令牌分配到分组，继承分组配置 |
| **复制/重置** | 复制 Key 或重置（重新生成） |
| **用量统计** | 每个令牌的累计调用量与费用 |

### 3.4 Usage Logs（/usage-logs/common）

> 通用用量日志（聊天、嵌入、音频等）

| 功能 | 说明 |
|------|------|
| **全量日志列表** | 所有 API 调用记录 |
| **筛选维度** | 令牌、模型、用户、时间范围、成功/失败 |
| **消耗详情** | 每次调用的 Prompt Token、Completion Token、费用 |
| **缓存命中标识** | 标注缓存命中的调用及其折扣费用 |
| **日志搜索** | 按请求内容关键词搜索 |
| **数据导出** | CSV/Excel 导出 |

### 3.5 Task Logs（/usage-logs/task）

> 异步任务日志（Midjourney 绘图、视频生成等）

| 功能 | 说明 |
|------|------|
| 任务状态追踪 | 提交中 → 处理中 → 已完成 / 失败 |
| 结果预览 | 图片/视频缩略图预览 |
| 错误信息 | 失败任务的错误详情 |

### 3.6 Drawing Logs（/usage-logs/drawing）

> Midjourney / 图片生成日志（独立视图）

- 按图片维度浏览生成记录
- 图片预览网格
- 支持下载原图

---

## 4. Personal — 个人中心

### 4.1 Wallet（/wallet）

> 用户钱包与充值

| 功能 | 说明 |
|------|------|
| **余额展示** | 当前账户余额 |
| **充值** | 选择金额或自定义金额充值 |
| **支付方式** | Stripe / Creem / Waffo Pancake / 易支付 |
| **交易记录** | 充值记录与消费明细列表 |
| **赠送额度** | 系统赠送的免费额度展示 |

### 4.2 Profile（/profile）

> 用户个人信息管理

| 功能 | 说明 |
|------|------|
| **基本信息** | 用户名、邮箱、手机号 |
| **头像** | 自定义头像设置 |
| **密码修改** | 修改登录密码 |
| **双因素认证 (2FA)** | 启用/禁用 TOTP 二次验证 |
| **Passkeys / WebAuthn** | 注册/管理安全密钥（指纹、Face ID、硬件 Key） |
| **OAuth 绑定** | 绑定/解绑第三方登录（GitHub、Discord、LinuxDO、Telegram、OIDC） |
| **API Key 用量** | 个人 API Key 使用概况 |
| **侧边栏设置** | 自定义侧边栏模块可见性（用户级） |

---

## 5. Admin — 管理后台

> 以下模块需 **Admin** 及以上角色方可访问

### 5.1 Channels（/channels）

> **渠道管理 — 系统的核心功能**，管理对接的上游 AI 厂商渠道

| 功能 | 说明 |
|------|------|
| **渠道列表** | 所有上游渠道的表格视图 |
| **新增渠道** | 添加新的厂商渠道 |
| **渠道类型** | 40+ 厂商可选（OpenAI、Claude、Gemini、DeepSeek、Azure、AWS、Ollama 等） |
| **API Key 配置** | 填写上游厂商的 API Key/Secret |
| **模型映射** | 配置该渠道支持的模型列表 |
| **负载均衡** | 同模型多渠道时设置权重（加权随机路由） |
| **优先级** | 设置渠道优先级 |
| **健康检查** | 手动/自动测试渠道连通性 |
| **自动重试** | 失败自动切换到备用渠道 |
| **渠道分组** | 将渠道分配到标签组，按组管理 |
| **渠道关联** | 将模型与渠道关联（多对多） |
| **批量操作** | 批量启停、批量更新模型列表 |
| **亲和性缓存** | 同一会话内优先复用同一渠道 |

### 5.2 Models（/models）

> **模型管理**，管理平台暴露给用户的所有模型

| 子页面 | 路由 | 功能 |
|--------|------|------|
| **Model Metadata** | `/models/metadata` | 模型元信息管理（名称、类型、描述、标签） |
| **Model Deployments** | `/models/deployments` | 模型部署配置（关联渠道、价格设置） |

**模型元信息管理：**

| 功能 | 说明 |
|------|------|
| 模型列表 | 所有已注册模型 |
| 新增模型 | 注册新模型（名称、标签、icon、描述） |
| 模型类型 | text / image / audio / video / embedding / rerank |
| 模型归属 (Owned By) | 标注模型的厂商/组织 |
| 排序/隐藏 | 控制模型在前端的展示顺序与可见性 |
| 标签系统 | 如 `gpt-5`、`reasoning`、`vision` 等 |

**模型部署配置：**

| 功能 | 说明 |
|------|------|
| 关联渠道 | 选择哪些渠道可以提供该模型 |
| 定价设置 | 输入价格 / 输出价格 / 缓存命中价格 / 图片价格等 |
| 倍率配置 | 基于分组或用户设置价格倍率（等比缩放） |
| 计费表达式 | 高级场景：按条件动态计费（如 token 分级、时段折扣） |

### 5.3 Users（/users）

> **用户管理**

| 功能 | 说明 |
|------|------|
| **用户列表** | 表格展示所有注册用户 |
| **搜索筛选** | 按用户名、邮箱、手机号搜索 |
| **新增用户** | 管理员手动创建用户 |
| **角色分配** | 设置用户角色（普通/管理员/超级管理员） |
| **额度管理** | 为用户分配调用额度 |
| **分组管理** | 将用户分配到分组 |
| **状态管理** | 启用/禁用用户账户 |
| **详情查看** | 查看用户用量、令牌、充值记录 |

### 5.4 Redemption Codes（/redemption-codes）

> **兑换码/充值码管理**

| 功能 | 说明 |
|------|------|
| **批量生成** | 指定数量、金额、有效期，批量生成兑换码 |
| **兑换码列表** | 查看所有兑换码状态（未使用/已使用/过期） |
| **导出** | 导出兑换码为 CSV/TXT |
| **使用记录** | 查看每个兑换码的使用者和时间 |

### 5.5 Subscriptions（/subscriptions）

> **订阅计划管理**

| 功能 | 说明 |
|------|------|
| **订阅计划列表** | 查看所有订阅计划 |
| **创建计划** | 定义周期、价格、赠送额度、可调用模型 |
| **用户订阅管理** | 查看用户当前的订阅状态 |
| **订阅支付** | 集成 Stripe / Creem / 易支付 / Waffo Pancake |
| **自动续费** | 订阅到期自动扣款续期 |
| **订阅统计** | 当前订阅用户数、收入统计 |

### 5.6 System Info（/system-info）

> **系统运行时信息**，仅 **Super Admin** 可访问

| 功能 | 说明 |
|------|------|
| **系统版本** | 当前运行的 New API 版本号 |
| **运行时间** | 系统启动时间与持续运行时长 |
| **Go 运行时** | Goroutine 数量、内存使用、GC 次数 |
| **数据库信息** | 数据库类型、连接数 |
| **性能指标** | QPS、平均响应延迟、错误率 |
| **渠道概览** | 各渠道的成功率、响应时间 |
| **日志监控** | 最近错误日志摘要 |

### 5.7 Rankings（/rankings）

> 用户用量排行榜（公开页面）

- 调用量排行
- Token 消耗排行
- 费用消耗排行

---

## 6. System Settings — 系统设置

> 通过侧边栏“钻入式”导航进入，Admin 角色可访问

### 6.1 Site & Branding（/system-settings/site）

| 设置项 | 说明 |
|--------|------|
| **站点名称** | 平台标题、首页标语 |
| **Logo** | 自定义 Logo 和 Favicon |
| **首页公告** | 公告栏内容（支持 Markdown） |
| **页脚信息** | 版权信息、备案号 |
| **默认语言** | 系统默认语言设置 |
| **价格页展示** | 控制是否展示定价页面 |
| **注册控制** | 开放注册 / 邀请制 / 关闭注册 |
| **通知配置** | 邮件通知、系统通知开关 |

### 6.2 Authentication（/system-settings/auth）

| 设置项 | 说明 |
|--------|------|
| **OAuth 配置** | GitHub / Discord / LinuxDO / Telegram / OIDC / 自定义 OAuth 的 Client ID / Secret |
| **邮箱验证** | 注册/找回密码时是否要求邮箱验证 |
| **短信配置** | 短信验证码服务商配置 |
| **密码策略** | 最小长度、复杂度要求 |
| **会话管理** | Token 过期时间、刷新策略 |
| **黑名单管理** | IP 黑名单地址配置（支持 iptables/netcat 格式） |

### 6.3 Billing & Payment（/system-settings/billing）

| 设置项 | 说明 |
|--------|------|
| **支付渠道** | Stripe / Creem / Waffo Pancake / 易支付 配置 |
| **支付密钥** | API Key / Webhook Secret / 回调地址 |
| **货币单位** | USD / CNY / EUR 等 |
| **最低充值** | 单次最低充值金额 |
| **赠送配置** | 新用户注册赠送额度、充值赠送比例 |
| **计费表达式** | 高级自定义计费逻辑配置 |
| **出账/入账记录** | 财务流水日志 |

### 6.4 Models & Routing（/system-settings/models）

| 设置项 | 说明 |
|--------|------|
| **模型同步** | 从上游渠道自动拉取模型列表 |
| **模型分组** | 默认模型分组管理 |
| **倍率配置** | 全局价格倍率设置 |
| **路由策略** | 加权随机 / 优先级 / 亲和性 等路由策略参数 |
| **格式转换** | 启用/禁用 OpenAI ⇄ Claude ⇄ Gemini 格式互转 |
| **Reasoning Effort 映射** | 模型思考力度的名称映射配置 |
| **缺失模型处理** | 当客户端请求未注册模型时的处理策略 |

### 6.5 Security & Limits（/system-settings/security）

| 设置项 | 说明 |
|--------|------|
| **全局限流** | QPS / RPM / TPM 全局限流配置 |
| **用户级限流** | 单用户 QPS / 日调用上限 |
| **内容安全** | 敏感词过滤、合规检查配置 |
| **速率限制** | 按令牌、IP、用户的精细速率限制 |
| **安全告警** | 异常调用模式告警阈值 |
| **数据保留** | 日志保留天数和清理策略 |

### 6.6 Console Content（/system-settings/content）

| 设置项 | 说明 |
|--------|------|
| **首页内容** | 首页介绍性内容（Markdown） |
| **定价页面** | 公开定价页面的模型定价展示 |
| **使用条款** | 用户协议内容编辑 |
| **隐私政策** | 隐私政策内容编辑 |
| **关于页面** | About 页内容 |

### 6.7 Operations（/system-settings/operations）

| 设置项 | 说明 |
|--------|------|
| **系统任务** | 定时任务管理（日志清理、数据同步等） |
| **数据库维护** | 数据库迁移、优化操作 |
| **缓存管理** | Redis 缓存刷新、内存缓存清理 |
| **数据迁移** | 控制台数据迁移工具 |
| **性能指标** | 性能监控采集配置 |
| **Uptime Kuma 集成** | 服务可用性监控接入 |
| **横幅管理** | 通知横幅配置（系统维护提示等） |

---

## 7. 用户认证与安全

### 7.1 注册/登录

| 方式 | 说明 |
|------|------|
| **密码登录** | 用户名/邮箱 + 密码 |
| **短信验证码登录** | 手机号 + SMS 验证码 |
| **OAuth 登录** | GitHub / Discord / LinuxDO / Telegram / OIDC |
| **WebAuthn / Passkeys** | 指纹、Face ID、硬件安全密钥 |
| **双因素认证 (2FA)** | TOTP 验证器二次验证 |

### 7.2 页面路由

| 路由 | 说明 |
|------|------|
| `/sign-in` | 登录页 |
| `/sign-up` | 注册页 |
| `/register` | 邀请注册（带邀请码） |
| `/forgot-password` | 忘记密码 |
| `/reset` | 重置密码 |
| `/otp` | 一次性密码验证（如邮箱验证） |
| `/oauth/:provider` | OAuth 回调处理 |
| `/setup` | 系统初始化向导（首次部署时） |

### 7.3 令牌安全机制

| 机制 | 说明 |
|------|------|
| JWT Token | 用户会话 Token（access + refresh 双 Token） |
| API Key | sk- 前缀的 API 调用密钥 |
| IP 白名单 | API Key 级别的 IP 访问控制 |
| 安全验证 | 可疑活动二次验证 |

---

## 8. 计费与支付系统

### 8.1 计费模式

| 模式 | 说明 |
|------|------|
| **按量计费** | 按实际消耗的 Prompt Token / Completion Token 计费 |
| **按次计费** | 如图片生成按张计费 |
| **订阅制** | 按月/年订阅，含固定额度 |
| **缓存命中折扣** | 命中缓存的请求按折扣价计费（如 50% off） |
| **倍率制** | 基于分组或用户的倍率系数调整价格 |

### 8.2 支付渠道

| 渠道 | 说明 |
|------|------|
| **Stripe** | 国际信用卡支付 |
| **Creem** | 新兴支付平台 |
| **易支付 (ePay)** | 国内支付（微信/支付宝扫码） |
| **Waffo Pancake** | 虚拟卡支付平台 |

### 8.3 计费表达式系统

> 高级特性：通过自定义表达式实现灵活计费

- 支持 Token 数量分级定价
- 支持时段折扣
- 支持模型维度的差异化定价
- 表达式版本管理与热更新

### 8.4 支付合规

- 支付 Webhook 签名验证
- 订单幂等性保证
- 支付状态回调处理
- 退款/争议处理

---

## 9. 上游厂商适配

### 9.1 已支持厂商（40+）

| 厂商 | Adapter | 类型 |
|------|---------|------|
| OpenAI | `openai` | LLM |
| Azure OpenAI | `azure` | LLM |
| Claude (Anthropic) | `claude` | LLM |
| Google Gemini | `gemini` | LLM |
| AWS Bedrock | `aws` | LLM |
| DeepSeek | `deepseek` | LLM |
| 百度文心 | `baidu` / `baidu_v2` | LLM |
| 阿里通义千问 | `ali` | LLM |
| 讯飞星火 | `xunfei` | LLM |
| 智谱 GLM | `zhipu` | LLM |
| 腾讯混元 | `tencent` | LLM |
| 字节豆包 | `volcengine` | LLM |
| 月之暗面 Moonshot | `moonshot` | LLM |
| 零一万物 | `lingyiwanwu` | LLM |
| MiniMax | `minimax` | LLM |
| Mistral | `mistral` | LLM |
| Cohere | `cohere` | LLM / Rerank |
| Jina | `jina` | Embedding / Rerank |
| Perplexity | `perplexity` | LLM |
| xAI (Grok) | `xai` | LLM |
| Ollama | `ollama` | 本地 LLM |
| Xinference | `xinference` | 本地 LLM |
| OpenRouter | `openrouter` | LLM 聚合 |
| Cloudflare | `cloudflare` | LLM |
| Replicate | `replicate` | 图像/视频 |
| Stable Diffusion / Dify | `dify` | 工作流 |
| SiliconFlow | `siliconflow` | LLM |
| Codex | `codex` | 代码 |
| Moka AI | `mokaai` | LLM |
| 即梦 Jimeng | `jimeng` | 图像/视频 |
| Coze | `coze` | 机器人 |
| 360 智脑 | `ai360` | LLM |
| Palm 2 | `palm` | LLM |
| Vertex AI | `vertex` | LLM |
| 自定义渠道 | `advancedcustom` | 泛化适配 |

### 9.2 渠道核心功能

| 功能 | 说明 |
|------|------|
| **健康检查** | 手动/定时测试渠道连通性与模型可用性 |
| **自动重试** | 调用失败自动切换备用渠道 |
| **负载均衡** | 加权随机分配请求到多个同模型渠道 |
| **亲和性路由** | 同一会话优先使用同一渠道（减少重复请求成本） |
| **上游状态更新** | 根据上游响应自动更新渠道状态 |

---

## 10. API 格式支持

### 10.1 支持的 API 格式

| 格式 | 说明 |
|------|------|
| **OpenAI Chat Completions** | `/v1/chat/completions` — 主流格式 |
| **OpenAI Responses** | `/v1/responses` — 新版 Responses API |
| **OpenAI Realtime** | WebSocket 实时语音/视频对话 |
| **Claude Messages** | Anthropic 原生 Messages 格式 |
| **Google Gemini** | Gemini 原生 generateContent 格式 |
| **OpenAI Images** | DALL·E 图片生成 |
| **OpenAI Audio** | TTS 文字转语音 / STT 语音转文字 |
| **OpenAI Embeddings** | 文本向量化 |
| **OpenAI Video** | 视频生成 |
| **Rerank** | Cohere、Jina 重排序 |

### 10.2 格式转换能力

| 转换 | 状态 |
|------|------|
| OpenAI Compatible ⇄ Claude Messages | ✅ 已支持 |
| OpenAI Compatible → Google Gemini | ✅ 已支持 |
| Google Gemini → OpenAI Compatible | ⚠️ 仅文本，暂不支持函数调用 |
| OpenAI Compatible ⇄ OpenAI Responses | 🚧 开发中 |
| 思考内容 → 普通内容 | ✅ 已支持 |

### 10.3 Reasoning Effort 支持

支持通过模型名称后缀控制推理力度：

| 系列 | 示例 |
|------|------|
| **OpenAI** | `o3-mini-high`、`gpt-5-low/medium/high` |
| **Claude** | `claude-3-7-sonnet-20250219-thinking` |
| **Gemini** | `gemini-2.5-flash-thinking`、`gemini-2.5-pro-thinking-128`、`-low/-medium/-high` 后缀 |

---

## 11. 部署与运维

### 11.1 部署方式

| 方式 | 说明 |
|------|------|
| **Docker Compose** | 推荐方式，一键部署 |
| **Docker 单容器** | `docker run calciumion/new-api:latest` |
| **二进制部署** | 直接从 Release 下载可执行文件 |
| **Kubernetes** | 社区 Helm Chart |

### 11.2 数据库支持

| 数据库 | 适用场景 |
|--------|----------|
| **SQLite** | 开发环境 / 轻量部署（默认） |
| **MySQL** | 生产环境（5.7.8+） |
| **PostgreSQL** | 生产环境（9.6+） |

### 11.3 运维特性

| 特性 | 说明 |
|------|------|
| **环境变量配置** | 所有配置均可通过环境变量注入 |
| **Redis 缓存** | 令牌缓存、模型列表缓存、渠道亲和性缓存 |
| **日志系统** | 支持 ClickHouse 日志存储（大数据量场景） |
| **性能指标** | 内置性能监控端点 |
| **Uptime Kuma 集成** | 外部可用性监控 |
| **优雅关闭** | 信号处理与连接排空 |
| **Telegram Bot** | 通过 Telegram Bot 查询用量/余额 |

---

## 12. 附录：完整路由表

### 12.1 前端路由（React）

**公开路由：**

| 路由 | 页面 | 说明 |
|------|------|------|
| `/` | 首页 | 着陆页 |
| `/sign-in` | 登录 | 密码/短信/OAuth 登录 |
| `/sign-up` | 注册 | 新用户注册 |
| `/register` | 邀请注册 | 带邀请码注册 |
| `/forgot-password` | 忘记密码 | 找回密码流程 |
| `/reset` | 重置密码 | 密码重置 |
| `/otp` | OTP 验证 | 一次性密码验证 |
| `/oauth/:provider` | OAuth 回调 | 第三方登录回调 |
| `/setup` | 初始化向导 | 首次部署配置 |
| `/pricing` | 定价页 | 模型定价公开展示 |
| `/pricing/:modelId` | 模型定价详情 | 单个模型定价 |
| `/rankings` | 排行榜 | 用户用量排行 |
| `/about` | 关于 | 关于页面 |
| `/privacy-policy` | 隐私政策 | 隐私政策 |
| `/user-agreement` | 用户协议 | 使用条款 |
| `/console/log` | 控制台日志 | 后端控制台视图 |
| `/console/topup` | 控制台充值 | 后端充值入口 |

**需认证路由（_authenticated）：**

| 路由 | 页面 | 角色 |
|------|------|------|
| `/playground` | API Playground | User+ |
| `/chat/:chatId` | 对话详情 | User+ |
| `/chat2link` | 分享对话 | User+ |
| `/dashboard/overview` | 系统总览 | User+ |
| `/dashboard/models` | 模型看板 | User+ |
| `/keys` | API Key 管理 | User+ |
| `/usage-logs/common` | 通用用量日志 | User+ |
| `/usage-logs/task` | 任务日志 | User+ |
| `/usage-logs/drawing` | 绘图日志 | User+ |
| `/wallet` | 钱包充值 | User+ |
| `/profile` | 个人中心 | User+ |
| `/channels` | 渠道管理 | Admin+ |
| `/models/metadata` | 模型元信息 | Admin+ |
| `/models/deployments` | 模型部署 | Admin+ |
| `/users` | 用户管理 | Admin+ |
| `/redemption-codes` | 兑换码管理 | Admin+ |
| `/subscriptions` | 订阅管理 | Admin+ |
| `/system-info` | 系统信息 | Super Admin |
| `/system-settings` | 系统设置入口 | Admin+ |
| `/system-settings/site` | 站点与品牌 | Admin+ |
| `/system-settings/auth` | 认证配置 | Admin+ |
| `/system-settings/billing` | 计费与支付 | Admin+ |
| `/system-settings/models` | 模型与路由 | Admin+ |
| `/system-settings/security` | 安全与限流 | Admin+ |
| `/system-settings/content` | 控制台内容 | Admin+ |
| `/system-settings/operations` | 运维管理 | Admin+ |

**错误页面：**

| 路由 | 说明 |
|------|------|
| `/errors/401` | 未授权（401） |
| `/errors/403` | 禁止访问（403） |
| `/errors/404` | 未找到（404） |
| `/errors/500` | 服务器错误（500） |
| `/errors/503` | 服务不可用（503） |

### 12.2 后端 API 模块（Go Controllers）

| 控制器文件 | 业务域 |
|-----------|--------|
| `channel.go` | 渠道 CRUD、测试、状态管理 |
| `channel-billing.go` | 渠道计费配置 |
| `channel-test.go` | 渠道连通性测试 |
| `channel_upstream_update.go` | 上游模型列表更新 |
| `channel_affinity_cache.go` | 渠道亲和性缓存 |
| `model.go` | 模型管理 |
| `model_meta.go` | 模型元信息 |
| `model_sync.go` | 模型同步 |
| `token.go` | API 令牌管理 |
| `user.go` | 用户管理 |
| `oauth.go` | OAuth 认证 |
| `custom_oauth.go` | 自定义 OAuth 提供商 |
| `passkey.go` | WebAuthn/Passkeys |
| `twofa.go` | 双因素认证 |
| `secure_verification.go` | 安全验证 |
| `relay.go` | API 请求转发/代理 |
| `log.go` | 用量日志 |
| `usedata.go` | 使用数据统计 |
| `billing.go` | 计费系统 |
| `pricing.go` | 定价管理 |
| `ratio_config.go` | 倍率配置 |
| `ratio_sync.go` | 倍率同步 |
| `topup.go` | 充值（通用） |
| `topup_stripe.go` | Stripe 充值 |
| `topup_creem.go` | Creem 充值 |
| `topup_waffo.go` / `topup_waffo_pancake.go` | Waffo Pancake 充值 |
| `subscription.go` | 订阅管理 |
| `subscription_payment_*.go` | 各渠道订阅支付 |
| `redemption.go` | 兑换码 |
| `payment_compliance.go` | 支付合规 |
| `payment_webhook_availability.go` | 支付 Webhook 可用性 |
| `playground.go` | Playground |
| `option.go` | 系统选项设置 |
| `setup.go` | 系统初始化 |
| `group.go` | 用户分组 |
| `prefill_group.go` | 预设分组 |
| `checkin.go` | 签到奖励 |
| `audit.go` | 审计日志 |
| `system_info.go` | 系统运行时信息 |
| `system_task.go` | 定时任务 |
| `system_task_handlers.go` | 任务处理器 |
| `midjourney.go` | Midjourney 代理 |
| `image.go` | 图片代理 |
| `video_proxy.go` | 视频代理 |
| `video_proxy_gemini.go` | Gemini 视频代理 |
| `task.go` / `task_video.go` | 异步任务 & 视频任务 |
| `performance.go` / `perf_metrics.go` | 性能监控 |
| `rankings.go` | 排行榜 |
| `telegram.go` | Telegram Bot |
| `wechat.go` | 微信集成 |
| `console_migrate.go` | 控制台数据迁移 |
| `deployment.go` | 部署配置 |
| `missing_models.go` | 缺失模型处理 |
| `codex_usage.go` | Codex 用量统计 |
| `model_owned_by_test.go` | 模型归属校验 |
| `return_path.go` | 回源路径配置 |
| `uptime_kuma.go` | Uptime Kuma 集成 |
| `vendor_meta.go` | 厂商元数据 |
| `misc.go` | 杂项/工具端点 |
| `aicp_wallet.go` | AICP 钱包集成 |
| `aicp_workspace.go` | AICP 工作区集成 |

---

> **文档说明**：本文档基于 `http://localhost:3001`（New API 网关）前端 React 路由、后端 Go 控制器、README 功能列表、以及侧边栏导航配置全面分析整理。该服务与 `localhost:8080`（AICP 漫剧生产平台，Vue 前端 + Spring Boot 后端）是完全独立的两个系统。3001 是通用的 AI API 网关系统，8080 是垂直行业的漫剧内容生产系统。两者通过 AICP Wallet/Workspace 集成模块互通。
>
> **姊妹文档**：[产品功能文档-8080-AICP漫剧生产工作台.md](产品功能文档.md)（此前误写入该文件，将以本 3001 文档为准重新整理）
