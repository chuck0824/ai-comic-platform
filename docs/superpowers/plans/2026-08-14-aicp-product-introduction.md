# AICP 产品介绍文档实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于 AICP 两个已登录本地控制台的真实页面，制作并验证一份面向意向客户与投资人的中文 Word 产品介绍文档。

**Architecture:** 浏览器只负责读取当前产品状态和采集截图；仓库权威文档负责核验产品口径；独立 Python 构建器负责生成 DOCX；文档技能提供的渲染器负责逐页视觉验收。业务代码不做任何修改。

**Tech Stack:** Codex Browser、Python 3、python-docx、Pillow、LibreOffice/Poppler 渲染链路。

## Global Constraints

- 最终仅交付 `.docx`。
- 文档面向意向客户与投资人，采用客户价值与投资逻辑双线叙事。
- 使用 10–12 张真实页面截图，禁止暴露 API Key、Token、个人联系方式等敏感信息。
- 已上线能力、当前架构与规划能力必须明确区分。
- 不得声称 8080 与 3001 的账号已经完全统一。
- 不编造客户数量、收入、市场规模、效率提升比例或经营数据。
- 不修改业务代码，不提交 Git。

---

### Task 1: 采集页面事实与核心截图

**Files:**
- Create: `artifacts/aicp-product-introduction/screenshots/*.png`
- Create: `artifacts/aicp-product-introduction/page-notes.md`

**Interfaces:**
- Consumes: 已登录的 `http://localhost:8080/` 与 `http://localhost:3001/` 浏览器会话。
- Produces: 经过敏感信息检查的 PNG 截图和页面能力摘要。

- [ ] **Step 1: 浏览 8080 核心页面**

依次打开首页、剧本创作、画布项目、剧本仓库、资产生成、Agent、SOP 和企业中心，读取可见 DOM，记录页面标题、主要能力和当前数据状态。

- [ ] **Step 2: 浏览 3001 核心页面**

依次打开数据看板、概览、渠道、模型、API 密钥、使用日志和任务日志，读取可见 DOM，识别可能包含密钥或个人信息的区域。

- [ ] **Step 3: 截取并保存候选图片**

统一使用桌面宽屏视口；截图突出页面标题、导航与核心工作区。遇到敏感字段则放弃该页或在 DOCX 入稿前裁切/遮挡。

- [ ] **Step 4: 建立截图清单**

在 `page-notes.md` 记录截图文件、URL、页面标题、可支持的产品结论、敏感信息检查结果和是否入稿。

### Task 2: 核验产品口径并完成文案

**Files:**
- Create: `artifacts/aicp-product-introduction/content-outline.md`

**Interfaces:**
- Consumes: `docs/01-core/*.md`、设计规格、`page-notes.md`。
- Produces: 18 个章节的标题、正文、截图说明和现状/规划标签。

- [ ] **Step 1: 提取权威产品定位**

核验产品一句话、双端职责、生产闭环、Agent/SOP、企业治理和模型网关口径。

- [ ] **Step 2: 编写客户价值叙事**

围绕效率、质量、成本、协作、资产与交付编写可验证的定性价值，不使用未经证实的百分比。

- [ ] **Step 3: 编写投资价值叙事**

围绕平台收入结构、规模化路径、流程数据沉淀、模型与内容双边协同及企业级治理形成投资逻辑，不编写虚构财务数据。

- [ ] **Step 4: 执行事实边界检查**

搜索并修正“账号已完全统一”“客户数量”“收入”“市场规模”“效率提升百分比”等无证据陈述；规划项统一标注“规划能力”或“演进方向”。

### Task 3: 生成专业 Word 文档

**Files:**
- Create: `artifacts/aicp-product-introduction/build_aicp_intro.py`
- Create: `artifacts/AICP产品介绍_客户与投资人版_2026-08-14.docx`

**Interfaces:**
- Consumes: `content-outline.md` 与选定截图。
- Produces: A4 纵向、深蓝科技商务风、15–18 页的 Word 文档。

- [ ] **Step 1: 配置页面与样式系统**

设置 A4、页边距、标题层级、中文字体、深蓝/青紫配色、页眉页脚、页码、表格边距和图片宽度。

- [ ] **Step 2: 构建封面与执行摘要**

使用纯排版与抽象生产链图形构建封面；执行摘要突出定位、目标客户、核心价值和双平台构成。

- [ ] **Step 3: 构建主体章节**

按设计规格写入行业挑战、双平台架构、生产闭环、核心功能、客户价值、竞争力、商业与投资价值、合作方式和结语。

- [ ] **Step 4: 插入截图与图注**

每张截图配页面名称、能力说明和业务价值解读；保持图片比例，不拉伸，不让图注跨页。

- [ ] **Step 5: 保存正式 DOCX**

输出到 `artifacts/AICP产品介绍_客户与投资人版_2026-08-14.docx`，不在交付目录保留临时 PDF 或渲染图片。

### Task 4: 渲染、视觉检查与修订

**Files:**
- Create (temporary): `artifacts/aicp-product-introduction/rendered/page-*.png`
- Modify: `artifacts/aicp-product-introduction/build_aicp_intro.py`
- Modify: `artifacts/AICP产品介绍_客户与投资人版_2026-08-14.docx`

**Interfaces:**
- Consumes: 首版 DOCX。
- Produces: 逐页检查无缺陷的最终 DOCX。

- [ ] **Step 1: 使用规范渲染器生成页面 PNG**

运行：

```bash
env TMPDIR=/private/tmp <bundled-python> /Users/apple/.codex/plugins/cache/openai-primary-runtime/documents/26.812.11052/skills/documents/render_docx.py artifacts/AICP产品介绍_客户与投资人版_2026-08-14.docx --output_dir artifacts/aicp-product-introduction/rendered
```

预期：每一页均生成 `page-<N>.png`，退出码为 0。

- [ ] **Step 2: 生成页面总览图并逐页检查**

检查文本与截图裁切、重叠、字体替换、异常空白、图注分页、页眉页脚和页面密度。

- [ ] **Step 3: 修复所有发现的问题并重新渲染**

每次修订后重新运行完整渲染，直至代表性页面和页面总览均无可见缺陷。

- [ ] **Step 4: 执行内容与结构验收**

检查 DOCX 可打开、图片数量为 10–12、无敏感文本、无占位符、无错误账号统一表述，并核对最终文件大小与页数。

### Task 5: 清理并交付

**Files:**
- Keep: `artifacts/AICP产品介绍_客户与投资人版_2026-08-14.docx`
- Keep: `docs/superpowers/specs/2026-08-13-aicp-product-introduction-design.md`
- Keep: `docs/superpowers/plans/2026-08-14-aicp-product-introduction.md`

**Interfaces:**
- Consumes: 已验证的最终 DOCX。
- Produces: 可点击下载的 Word 交付链接和简短交付说明。

- [ ] **Step 1: 移除临时渲染文件**

仅清理本任务生成的 `artifacts/aicp-product-introduction/rendered/` 临时页面，保留构建脚本、页面记录和正式 Word。

- [ ] **Step 2: 运行最终验证**

执行文件存在性、ZIP 完整性、正文关键字、图片计数、敏感文本和占位符扫描；所有检查必须在交付前以退出码 0 完成。

- [ ] **Step 3: 交付 Word 文档**

在最终回复中提供正式 DOCX 的绝对路径链接，并说明文档面向客户与投资人、包含真实页面截图且已经过逐页渲染检查。

