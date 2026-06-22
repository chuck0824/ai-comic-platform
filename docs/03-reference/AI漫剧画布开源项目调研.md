# AI 漫剧画布开源项目调研

整理日期：2026-06-20  
检索范围：GitHub 公开仓库，重点筛选 AI 漫画/漫剧生成、浏览器画布、HTML Canvas、Fabric.js、Konva、React/Vue 画布编辑器等方向。

## 结论摘要

目前没有找到一个成熟度很高、专门面向“AI 漫剧画布”、并且完整采用 HTML5 Canvas/Fabric/Konva 的开源项目。更现实的组合方案是：

1. 用 [panel-craft](https://github.com/8bitbyadog/panel-craft) 参考 AI 素材生成 + Konva 画布编辑骨架。
2. 用 [wakuwaku](https://github.com/izag8216/wakuwaku) 参考纯 HTML + Canvas/Fabric.js 的漫画分格编辑能力。
3. 用 [ai-comic-factory](https://github.com/jbilcke-hf/ai-comic-factory) 或 [LocalMiniDrama](https://github.com/xuanyustudio/LocalMiniDrama) 参考 AI 漫画/漫剧生成工作流。

如果要快速做自己的 AI 漫剧画布，优先路线是：`panel-craft` 的 Konva 画布交互 + `wakuwaku` 的漫画分格/导出 + 自有 AI 生图/分镜服务。

## 推荐优先级

| 优先级 | 项目 | 推荐原因 | 主要风险 |
| --- | --- | --- | --- |
| P0 | [panel-craft](https://github.com/8bitbyadog/panel-craft) | 最接近“AI + Canvas 漫画编辑器”：React + Konva，支持拖拽、缩放、旋转、AI 生成角色/道具/背景 | 原型级，星标少，需要补工程化和产品功能 |
| P0 | [wakuwaku](https://github.com/izag8216/wakuwaku) | 纯 `index.html` + `<canvas>` + Fabric.js，漫画分格编辑清晰 | 没有 AI，需要自己接生成流程 |
| P1 | [ai-comic-factory](https://github.com/jbilcke-hf/ai-comic-factory) | AI 漫画生成逻辑强，LLM + SDXL，Apache-2.0 | 画布编辑不是核心，页面排版更多是 HTML/CSS |
| P1 | [react-komik](https://github.com/sonnylazuardi/react-komik) | 早期 React + Fabric.js 漫画条编辑器，MIT | 依赖非常旧，只适合参考思路 |
| P2 | [writecomics-web](https://github.com/hovboard-create/writecomics-web) | Next.js + Konva.js，浏览器漫画创作方向很贴 | 未看到明确 license，复用需谨慎 |

## 核心候选项目

### 1. panel-craft

仓库：[8bitbyadog/panel-craft](https://github.com/8bitbyadog/panel-craft)  
许可证：MIT  
技术栈：Vite、React、TypeScript、Konva、react-konva、Hugging Face API  
匹配度：高

项目描述是“React-based comic tool with AI-generated characters, props, & backgrounds”。它的 `package.json` 明确包含：

- `konva`
- `react-konva`
- `react-draggable`
- `react-rnd`
- `openai`
- `axios`

关键画布组件在 `src/components/SceneCanvas.tsx`，使用 `Stage`、`Layer`、`Image`、`Transformer`、`Group` 等 `react-konva` 组件。它已经具备画布对象选择、拖拽、缩放、旋转、层级排序和背景图渲染等基础能力。

AI 生成部分在 `src/services/api.ts`，主要调用 Hugging Face 推理 API 生成角色、道具、背景，再把生成结果转成 base64 图片放回画布。

适合参考：

- Canvas 场景编辑器骨架
- 角色/道具/背景作为图层对象的管理方式
- AI 生成图片进入画布的流程
- 单面板漫画/分镜编辑体验

不足：

- 项目较小，工程成熟度一般
- 需要补项目管理、素材库、分镜列表、导出、多人协作、版本保存等能力
- 生成能力依赖外部 API，需要替换成自己的模型服务或中转服务

### 2. wakuwaku

仓库：[izag8216/wakuwaku](https://github.com/izag8216/wakuwaku)  
许可证：MIT  
技术栈：HTML、Canvas、Fabric.js、jsPDF  
匹配度：高，但不含 AI

这是一个漫画分格排版 WYSIWYG 编辑器。入口就是 `index.html`，页面里直接有：

- `<canvas id="canvas-container"></canvas>`
- Fabric.js CDN
- jsPDF CDN
- `src/canvas.js`
- `src/panel.js`
- `src/templates.js`
- `src/exporter.js`

适合参考：

- 纯 HTML + Canvas 的最小实现方式
- 漫画分格创建、拖动、排版
- PDF 导出
- 无框架或轻框架下的画布模块拆分

不足：

- 没有 AI 生成能力
- 更偏漫画分格排版，不是完整 AI 漫剧工作台
- UI 和状态管理需要按当前产品重做

### 3. ai-comic-factory

仓库：[jbilcke-hf/ai-comic-factory](https://github.com/jbilcke-hf/ai-comic-factory)  
许可证：Apache-2.0  
技术栈：Next.js、React、LLM、SDXL、Hugging Face、Konva、react-konva、html2canvas  
匹配度：中高

这是较知名的 AI 漫画生成项目，定位是用 LLM + SDXL 生成漫画分镜。它的依赖里包含 `konva`、`react-konva`、`html2canvas`，也有 `src/lib/loadImageToCanvas.ts` 这类 Canvas 图片处理工具。

不过，从已查看的页面组件看，核心漫画页和气泡更偏 HTML/CSS 组件排版，而不是完整的 Fabric/Konva 拖拽画布编辑器。

适合参考：

- AI 漫画生成流程
- 多 panel/page 的数据组织
- LLM 生成分镜 prompt
- SDXL 生成漫画画面
- 气泡、字幕、caption 的交互方式

不足：

- 不适合作为完整画布编辑器直接复用
- 需要二次设计“编辑器”能力

### 4. react-komik

仓库：[sonnylazuardi/react-komik](https://github.com/sonnylazuardi/react-komik)  
许可证：MIT  
技术栈：React、Fabric.js  
匹配度：中

这是早期 React 漫画条编辑器，仓库描述明确写着 “ReactJS based comic strip creator using fabric.js canvas rendering”。`package.json` 里使用 `fabric-webpack`。

适合参考：

- Fabric.js 做漫画条编辑器的早期实现
- 漫画元素在 Canvas 中的组织方式
- React 与 Fabric.js 的结合思路

不足：

- 技术栈非常旧：React 0.14、Webpack 1
- 不建议直接 fork 当底座
- 没有 AI 生成流程

### 5. writecomics-web

仓库：[hovboard-create/writecomics-web](https://github.com/hovboard-create/writecomics-web)  
许可证：未明确识别  
技术栈：Next.js、Konva.js、Turso、Vercel Blob  
匹配度：中

仓库描述是“Browser-based comic creator. Drag characters, add speech bubbles, save & share. Next.js + Konva.js + Turso + Vercel Blob.” 功能方向与漫画创作画布很贴。

适合参考：

- 角色拖拽
- 气泡编辑
- 保存与分享
- Next.js + Konva 的应用结构

不足：

- GitHub 元数据里没有明确 license
- 星标少，需要自行评估代码质量
- 如需商用或深度复用，必须先确认授权

## AI 漫剧/短剧流程参考项目

这些项目很像“AI 漫剧平台”，但它们的画布通常是工作流节点、DOM 白板或普通页面，不是 HTML5 Canvas/Fabric/Konva 编辑器。

| 项目 | 许可证 | 技术栈/方向 | 适合参考 | 不适合作为 Canvas 底座的原因 |
| --- | --- | --- | --- | --- |
| [LocalMiniDrama](https://github.com/xuanyustudio/LocalMiniDrama) | MIT | Vue 3、Vue Flow、本地 AI 短剧/漫剧生成 | 本地从故事到分镜到视频的流水线 | 前端是 Vue Flow 工作流画布，不是 HTML5 Canvas |
| [wind-comic](https://github.com/ChrisChen667788/wind-comic) | MIT | Next.js、React、AI Agent、视频生成 | 多 Agent 漫剧/短剧生产链路 | 画布偏 `@xyflow/react` 工作流，不是漫画编辑 Canvas |
| [AIComicBuilder](https://github.com/LingyiChen-AI/AIComicBuilder) | Apache-2.0 | Next.js、AI 动画漫画生成 | 剧本转动画漫画、角色设计、视频合成 | 未看到 Fabric/Konva/Canvas 画布依赖 |
| [printfilm](https://github.com/yuanzhongqiao/printfilm) | 未明确识别 | Electron、Vite、React、AI 漫剧工场 | AI 漫剧工作台产品结构 | 未看到 Canvas/Fabric/Konva 依赖，且授权不明确 |
| [DirectorFrame-AI](https://github.com/dafenq/DirectorFrame-AI) | package 标注 `UNLICENSED` | Electron、导演 DSL、Prompt Compiler、镜头卡片 | 漫剧分镜生产概念、镜头语言组织 | 主要是 DOM 卡片无限画布，不是 Canvas API；授权风险高 |
| [AI-CanvasPro](https://github.com/MMKJ555-PNG/AI-CanvasPro) | 未明确识别 | Tauri、React、`@xyflow/react` | 桌面端 AI 漫剧应用结构 | 名字像 Canvas，但实际更偏 XYFlow 节点画布 |
| [TapCanvas](https://github.com/anymouschina/TapCanvas) | MIT | 通用沉浸式画布、Agent 工具链 | 通用无限画布、Agent 调用画布能力 | 不是漫剧专用，仓库较大，集成成本高 |

## 技术路线建议

### 路线 A：最快搭出 AI 漫剧画布原型

适合目标：先跑通“AI 生成素材 -> 拖入画布 -> 调整分镜 -> 导出”的 MVP。

建议组合：

- 画布底座：`panel-craft`
- 分格/导出：`wakuwaku`
- AI 生成：自有 API、ComfyUI、Stable Diffusion、GPT Image、Seedance 工作流等

优点：

- 上手最快
- Canvas 技术路线清晰
- 能快速验证编辑器体验

缺点：

- 需要自己补工程化
- 多分镜、多页面、素材资产管理要重写

### 路线 B：AI 生成优先，画布编辑后补

适合目标：先做“剧本 -> 分镜 -> 图片/视频”的生产流水线。

建议组合：

- AI 漫画生成参考：`ai-comic-factory`
- 本地漫剧流程参考：`LocalMiniDrama`
- 画布编辑层后续接入 `Konva` 或 `Fabric.js`

优点：

- 更快验证 AI 生产效果
- 适合先做批量生成、任务队列、提示词编译、角色一致性

缺点：

- 画布交互体验要后补
- 用户手动精修能力一开始会弱

### 路线 C：产品级桌面工作台

适合目标：做本地/桌面端 AI 漫剧生产软件。

建议参考：

- `printfilm`
- `DirectorFrame-AI`
- `AI-CanvasPro`
- `LocalMiniDrama`

优点：

- 更贴近漫剧生产工作台
- 可参考项目、素材、任务、镜头卡片、生成记录等模块

缺点：

- 很多项目授权不明确
- 多数不是 HTML5 Canvas 编辑器
- 桌面端集成成本更高

## 选型建议

如果当前目标是“找一个开源的 AI 漫剧画布，基于 HTML + Canvas 做二开”，推荐顺序如下：

1. 先读 `panel-craft`：确认 Konva 编辑器如何组织元素、背景、拖拽、缩放、旋转。
2. 再读 `wakuwaku`：确认漫画分格、页面模板、PDF 导出的实现。
3. 再读 `ai-comic-factory`：参考 AI 漫画分镜生成、prompt 组织、多 panel 数据结构。
4. 不建议直接基于 `react-komik` 启动新项目，但可以借鉴 Fabric.js 思路。
5. 对没有明确 license 的项目，只做产品和交互参考，不直接复制代码。

## 法律与复用风险

| 风险项 | 说明 | 建议 |
| --- | --- | --- |
| 未明确 license | `writecomics-web`、`printfilm`、`AI-CanvasPro` 等未看到明确开源许可证 | 不直接复制代码，只参考思路 |
| `UNLICENSED` | `DirectorFrame-AI` 的 package 标注 `UNLICENSED` | 不做代码复用 |
| 原型项目质量 | `panel-craft`、`wakuwaku` 星标较少或项目较新 | 只抽核心思路，重写工程底座 |
| 老旧依赖 | `react-komik` 使用 React 0.14、Webpack 1 | 不直接 fork，迁移到现代 React/Vite/Next |

## 最终推荐

如果只能选一个项目先看，选 [panel-craft](https://github.com/8bitbyadog/panel-craft)。它虽然小，但最接近“AI 漫剧画布”的技术闭环：AI 生成素材、Canvas 画布、图片图层、拖拽、缩放、旋转。

如果要做一个更稳的工程方案，建议不要直接 fork 单个项目，而是拆成三层：

| 层 | 推荐参考 | 自研重点 |
| --- | --- | --- |
| AI 生成层 | `ai-comic-factory`、`LocalMiniDrama` | 剧本拆分、分镜 prompt、角色一致性、任务队列 |
| 画布编辑层 | `panel-craft`、`wakuwaku` | Konva/Fabric 对象模型、分格、素材拖拽、变换、撤销重做 |
| 产品工作台层 | `wind-comic`、`printfilm`、`DirectorFrame-AI` | 项目管理、素材库、生成记录、导出、批处理 |

