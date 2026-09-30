# CHANGES · 相对上游的改动声明

> 本文件记录本 fork 相对 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) 的改动。
> **合规依据**：AGPL-3.0 §5a —— 修改版本应显著标注改动内容与日期。

## 基线

| 项目 | 值 |
| --- | --- |
| 上游仓库 | [https://github.com/rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) |
| 基准版本 | **2.5.2（versionCode 187）** |
| 基准 commit | `643191229dcbeb4972e3772986818605633ace92` |
| 上游协议 | **AGPL-3.0** |
| 本 fork 协议 | **AGPL-3.0**（与上游一致） |

## [v2.5.2-plus.1] — 2026-09-20

> 基于上游 `2.5.2`。本版为 fork 首个版本。

### 新增

- **日历删除工具 `calendar_delete`**
  - 之前只有 `calendar_query`（查）和 `calendar_create`（建），现在可以删除建错的日程。
  - 通过事件 ID 删除；ID 取自 `calendar_query` 返回的 `id`，或 `calendar_create` 返回的 `event_id`。
  - 复用已有的「日历」工具开关，不新增用户可见开关。
  - 需要系统日历写权限；删除操作每次都会请求用户确认。
  - 工具调用卡片显示为“删除事件：标题”，与创建事件卡片的显示方式保持一致。

- **助手记忆分组**
  - 支持私有、全局和自定义记忆分组。
  - 支持新建、改名和删除自定义分组。
  - 删除分组前显示受影响的记忆数和成员数。
  - 删除分组时，组内记忆回落到私有范围，不直接删除记忆内容。
  - 保留既有 `useGlobalMemory` 字段，并兼容已有全局记忆数据，无需数据库迁移。

### 修改

- 助手记忆页将原“全局记忆”开关改为“记忆分组”入口，以便选择私有、全局或自定义分组。
- 助手记忆页、助手详情、聊天服务和记忆工具等既有读写入口统一按记忆分组作用域访问数据。
- 未修改既有 `memory_tool` 的名称、参数或输出字段。
- 未修改既有 `calendar_query` 的输出字段：事件 ID 仍为 `id`。

### 包名 / 签名状态

- 当前 Debug 包名：`me.rerere.rikkahub.debug`。
- 当前 Debug 包使用 Debug 签名，可与官方包共存安装；当前未生成正式 Release 包。
- 后续正式 Release 计划使用包名：`me.rerere.rikkahub.plus`，并使用独立签名；该 Release 尚未签名或生成。
- 当前源码 namespace 保持为 `me.rerere.rikkahub`，不影响后续包名迁移安排。

### 借鉴来源声明

- `calendar_delete` 的实现思路参考 [YaeNovin/Rikkahub-Revised](https://github.com/YaeNovin/Rikkahub-Revised)（同为 AGPL-3.0，借鉴合法）。
- **但未照抄**：该 fork 同时把 `calendar_query` 的输出字段 `id` 改成了 `event_id`，属于对既有行为的破坏性改动；本 fork 未采纳。

## [F003] 更新源改造 — 2026-09-21

> 基于上游 `2.5.2` 和本 fork 已有改动；本次正式发布版本为 `2.5.3-work.1`（versionCode 188）。

### 修改

- 更新检查数据源改为本 fork 的 GitHub Release：`ziwa0213-crypto/rikkahub_work`。
- 点击更新卡片改为打开 GitHub 发布页，由用户自行下载并安装 APK；移除应用内下载逻辑和下载弹窗。
- 网络失败、404、限流和 JSON 解析失败不再展示错误卡片，统一静默处理。
- GitHub Release 的 `tag_name` 支持可选 `v` 前缀，比较时仍使用现有 SemVer 规则。

### 发布约定

- Release tag 使用不带 `v` 前缀的 SemVer，且核心版本号必须高于已安装版本，例如 `2.5.3-work.1`。
- GitHub 仓库必须保持公开，否则匿名更新检查会静默为无更新。
- 应用显示名称统一为 `RikkaHub Work`；Debug 与 Release 的包名仍分别为 `me.rerere.rikkahub.debug` 和 `me.rerere.rikkahub.plus`。

## [v2.5.3-work.1] — 2026-09-21

> F004 工具审批模式与 F003 更新源改造的正式发布版本，基于上游 `2.5.2` 和本 fork 已有改动。

### 新增

- 聊天输入栏新增按助手保存的三档工具审批模式：需要询问、部分询问、完全允许。
- 默认模式为“部分询问”，仅高风险改写、删除和执行操作需要确认；工具本身的审批判断在部分模式下仍会保留并使用 OR 叠加。
- 使用 HugeIcons 安全图标区分三档：`ShieldQuestionMark`、`ShieldCheck`、`ShieldOff`。
- 工具风险采用中央 L0-L3 分级；未知工具默认按 L3 处理。

### 有意行为变更

- `calendar_create` 在默认“部分询问”模式下不再二次确认；`ask_user` 始终由 HITL 流程拦截，不进入普通工具执行路径。
- 默认“部分询问”模式下，所有 `mcp__*` 工具以及 workspace 的 read/write/edit 工具按 L3 处理并要求确认；这些工具各自设置页的审批开关不能关闭这一层确认。`workspace_shell` 继续要求确认。
- “完全允许”会跳过应用内审批，包括工作区路径越界护栏，但不绕过 Android 系统权限，例如日历或存储授权。

### 包名 / 签名状态

- Debug 包名：`me.rerere.rikkahub.debug`。
- Release 包名：`me.rerere.rikkahub.plus`，使用独立签名。
- Debug APK 仅用于本地开发，不上传 GitHub。
- Release APK 使用独立签名，GitHub Release 资产名为 `RikkaHub.Work-{arm64-v8a,universal,x86_64}-release.apk`。
- 正式版本：`versionName=2.5.3-work.1`，`versionCode=188`。

## [F005] 关于页精简与品牌本地化 — 2026-09-22

### 品牌与关于页

- 应用名统一为 `RikkaHub Work`，使桌面、应用管理和应用内显示名称保持一致。
- 精简关于页：移除官网入口；GitHub 仓库与 License 入口改为指向本 fork `ziwa0213-crypto/rikkahub_work`。
- 将关于页中的“使用文档”调整为“官方使用文档”，明确该链接所指内容的来源。

### 社区与赞助入口

- 移除关于页中的 QQ、Discord 和赞助入口。
- 移除赞助提醒，以及不再使用的 Sponsor API、数据模型与相关资源。

### 语言与导出内容

- 删除日语、韩语和俄语资源目录（`values-ja`、`values-ko`、`values-ru`），保留英语、简体中文和繁体中文。
- Mermaid 图片与聊天图片导出时不再添加 `rikka-ai.com` 水印。
- 分享文案中的下载地址改为本 fork 的 GitHub Releases 页面。

## [v2.5.3-work.2] — 2026-09-22（已发布为 Pre-release）

> 本版本基于当前工作树，版本号为 `versionName=2.5.3-work.2`、`versionCode=189`。

### 新增

- 接入 `com.qmdeve.liquidglass:core:1.0.5` 依赖并新增“模糊 / 液态 / 兼容”设置入口；兼容模式使用原有 Haze 玻璃渲染，兼容开关值会被保留。
- 隐藏 Debug 页面新增隔离原型供 API 33+ 设备验证；真实液态折射尚未接入正式聊天输入栏，聊天输入栏仍使用 Haze 回退效果。

### 修改

- 保留旧的 `BackgroundEffectType.GLASS` 序列化值。读取旧数据时自动归一化为 `LIQUID + liquidCompatMode=true`，避免旧用户设置反序列化失败并保持原有玻璃观感。
- `minSdk` 保持 26；API 33 以下自动使用 Haze 兼容路径，不拒绝安装。
- 针对根 View 采样导致的 `RenderNode` 递归录制崩溃，禁用该路径；在隔离原型通过设备验证并补足渲染期异常保护前，液态选项继续回退到原有 Haze 玻璃渲染。

### Phase 0 验证状态

- 已完成 View 与 Compose 的编译互操作验证：`:app:compileDebugKotlin` 通过。
- 当前环境没有 Android 设备或模拟器，因此尚未确认根 View 采样是否存在自引用、聊天滚动帧率、发热和耗电表现；未伪造截图或运行结论。
- 已发现方案甲会触发自引用导致崩溃；当前已回退到 Haze 兼容渲染，独立采样容器仍待后续改造。
- 已在隐藏 Debug 页面加入方案乙隔离原型：采样源、`LiquidGlassView` 与输入控件位于真实 `FrameLayout` 的兄弟层；API 33 以下及初始化异常显示兼容表面。库内部异步 `pre-draw` 渲染异常不能被外层初始化 `try/catch` 捕获，仍需通过实机压力验证或增加库级保护。正式聊天输入栏仍使用 Haze，不视为 F006 已交付。

### 第三方组件

- `com.qmdeve.liquidglass:core:1.0.5` — MIT License, Copyright © 2025-2026 Donny Yale (QmDeve)。

## [v2.5.3-work.3] — 2026-09-23（已正式发布）

> 正式接入 F006。版本号 versionName=2.5.3-work.3、versionCode=190。

### 新增

- API 33 及以上设备在选择“液态”且关闭“兼容”后，聊天输入栏使用 AndroidLiquidGlassView 的真实折射与色散效果。
- 将液态玻璃库 1.0.5（MIT）本地化为独立 Android library module；采样内容、玻璃表面和可交互输入组合位于互不嵌套的兄弟层。
- 液态玻璃录制增加重入保护与 finally 收尾；初始化、尺寸变化、参数更新、绘制和异步 pre-draw 异常会记录日志并触发进程内熔断，回退到 Haze 兼容效果。

### 修改

- 正式聊天输入栏启用液态效果；模糊、兼容、API 33 以下和渲染失败时沿用原有 Haze 实现及参数。
- 保留旧的 glass 序列化值并将旧配置归一化为“液态 + 兼容”；默认值仍为“模糊”，minSdk=26 不变。
- 移除仅供 Phase 0 的隐藏 Debug 原型页面。

### 验证状态

- `:app:testDebugUnitTest`、`:app:compileReleaseKotlin` 和 `:app:assembleRelease` 均通过；Release APK 的包名、版本及 V2 签名已核验。
- 本版本仍需用户设备验证重复进入、旋转、切换会话、触摸输入、滚动流畅度、发热与耗电。本地构建或单元测试不能替代这些实机检查。

### 第三方组件

- liquidglass/ 基于 QmDeve/AndroidLiquidGlassView v1.0.5，保留上游 MIT 版权声明和许可证全文，Copyright © 2025-2026 Donny Yale (QmDeve)。

## [v2.5.3-work.4] — 2026-09-24

> F007 思考模式推理回传修复。版本号 `versionName=2.5.3-work.4`、`versionCode=191`。

### 修复

- 修复 DeepSeek 思考模式在工具调用或 `ask_user` 交互后继续生成时，因缺少历史 `reasoning_content` 而返回 HTTP 400 并中断对话的问题。
- 当 Chat Completions 请求实际携带工具，且服务商主机名或模型名属于 DeepSeek 方言时，即使关闭“回传历史思考过程”开关，也会自动完整回传历史推理内容。
- 非 DeepSeek 服务商、DeepSeek 无工具请求，以及用户已开启开关的既有行为保持不变。

### 设置提示

- 在“回传历史思考过程”设置下补充说明：部分服务商（如 DeepSeek）使用工具时会自动强制回传。

## [v2.5.5-work.1] — 2026-09-29（已发布）

> 基于上游 `2.5.5`，本次同时合并上游 `2.5.4` 的发布说明范围内修复；版本号 `versionName=2.5.5-work.1`、`versionCode=192`。

### 上游跟进

- 优化思考模式设置与通义千问思考强度调节。
- 修复 Gemini 使用部分工具、OpenAI 部分工具调用参数、复制代码块带出行号、思考步骤图标黑底和收藏页侧滑删除问题。
- 新增技能创建助手、技能搜索、数据恢复页和阿拉伯语 RTL 支持。
- 修复技能保存与 GitHub 导入损坏、设置读取失败重置、大文件编辑器切后台崩溃及 MCP 空请求头连接失败问题。
- 统一列表操作与拖拽排序，调整搜索服务选择器、供应商返回行为和输入框透明度。

### F008 加载动画个性化

- 加载动画改为按供应商独立保存，支持按渠道自动匹配、内置预设和自定义 GIF/图片或 URL。
- AUTO 模式优先使用供应商名称对应的品牌动画；无法识别时按 OpenAI、Google 或 Claude 供应商类型匹配。
- 新增 OpenAI 旋转、Claude 星芒、Gemini sparkle、DeepSeek 脉冲和通用品牌呼吸动画；自定义素材限制解码尺寸为 96px，保留动画内容原色。
- 供应商设置中加入可折叠的加载动画入口和实时预览；首次选择自定义素材时提示透明背景建议，并支持“不再提示”。
- 聊天等待回复和压缩上下文均使用当前供应商的加载动画。
- 移除全局“使用 App 图标样式加载动画”设置及兔子动画资源；保留旧字段和默认值以兼容已有设置数据，但不再读取该字段参与渲染。
- 同步上游新增的阿拉伯语资源与 RTL 支持，并清理 F005 已移除功能对应的阿拉伯语旧翻译；应用名和分享文案保持为 `RikkaHub Work` 及本 fork Releases 地址。

### 验证状态

- 已完成源码静态核对并补充加载动画解析、显式配置容错和序列化兼容测试。
- `./gradlew :app:testDebugUnitTest :ai:test` 通过。
- `./gradlew :app:assembleRelease` 通过，Release APK 已用本地独立签名生成；包名为 `me.rerere.rikkahub.plus`，版本为 `2.5.5-work.1`（versionCode 192）。
- 已发布至 GitHub Releases；未进行设备/虚拟机验证。

## [v2.5.5-work.2] — 2026-09-30（已发布）

> 基于 `2.5.5-work.1`，versionName=`2.5.5-work.2`，versionCode=`193`。

### 修复

- 修复切换聊天模型后加载动画滞后一轮才刷新的问题：现在按即将使用的模型解析供应商动画，不再读取上一轮已完成回答的模型。
- 移除流式生成期间对历史消息变化的无关动画重解析，避免加载动画因新消息到达而重新开始。

### 发布说明

- Release APK 使用包名 `me.rerere.rikkahub.plus` 和本 fork 的独立签名，可覆盖安装同包名的既有 Work 版本；升级前请备份应用数据。
- Release APK 资产名使用 `RikkaHub.Work-{arm64-v8a,universal,x86_64}-release.apk`。
- `./gradlew test` 与 `./gradlew :app:assembleRelease` 通过；维护者在设备上测试后反馈本次修复没有问题。

## 协议声明

本 fork 以 **AGPL-3.0** 授权，原始版权归 RikkaHub 作者所有。
完整的许可证全文见仓库根目录 [LICENSE](./LICENSE)。
