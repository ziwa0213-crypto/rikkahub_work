<div align="center">
  <img src="docs/icon.png" alt="App 图标" width="100" />
  <h1>RikkaHub Work</h1>

一个基于 RikkaHub 的原生 Android LLM 聊天客户端，支持切换不同的供应商进行对话 🤖💬

[English](README_EN.md) | 简体中文
</div>

<div align="center">
  <img src="docs/img/chat.png" alt="聊天界面" width="150" />
  <img src="docs/img/desktop.png" alt="模型选择" width="450" />
</div>

## 📌 关于本 fork

RikkaHub Work 是 [RikkaHub](https://github.com/rikkahub/rikkahub) 的**个人 fork**，以自用为主，顺手分享。它跟随上游发版，并在此基础上追加了一批功能与修复。

- **不是 RikkaHub 官方版本。** 本仓库的一切都建立在上游工作之上；本 fork 的问题请提到**本仓库**，与上游无关。
- **版本号规则**：`{上游版本}-work.{n}`，例如 `2.5.6-work.3`。每次跟进新的上游版本时，`n` 归 1。
- **语言**：仅保留 English、简体中文、繁體中文。
- 构建产物通过本仓库的 GitHub Releases 分发。

## 📥 安装与升级

到本仓库的 **Releases** 页面下载 APK。

- **包名**：`me.rerere.rikkahub.plus` —— 与上游的 `me.rerere.rikkahub` 不同，所以**本 fork 可以和官方 RikkaHub 共存**。
- **升级本 fork**：直接覆盖安装即可。但签名必须一致、且 versionCode 不能倒退，否则系统会拒绝安装。
- **从其它 fork 或官方版转过来**：需要先卸载。签名不同，不卸载装不上。
- 提供三种构建：`arm64-v8a`、`x86_64` 和 `universal`，多数手机选 `arm64-v8a`。

> [!WARNING]
> 升级前务必备份应用数据。设置、对话和 API Key 都在应用私有目录里，卸载即丢失。

## ✨ 本 fork 增加的功能

### 🗂 记忆

- **记忆分组** —— 让多个助手共享同一组记忆，同时保留私有记忆与内置全局记忆。删除分组时，组内记忆回落到私有范围，不会直接删除内容。
- **记忆迁移多选** —— 复制助手、或切换助手的记忆归属时，可以逐条勾选要带走哪些记忆，并在「复制 / 移动」之间选择。移动共享记忆前会提示影响范围。
- **聊天 ⇄ 群聊双向导入** —— 群聊当前会话可以从任意助手私有、全局或记忆分组导入记忆；助手也可以按「群名 · 会话标题」从群聊导入。默认复制，来源列表按真实作用域去重。

### 🛠 工具与审批

- **三档工具审批** —— 每个助手独立保存：**需要询问**（每次确认）、**部分询问**（仅高风险确认，默认）、**完全允许**（不确认）。风险按 L0~L3 分级，未登记工具默认按最高风险处理。
- **日历删除** —— 新增 `calendar_delete`，删除建错的日程；删除操作始终需要确认。
- **内置浏览器（凭据隔离）** —— 遇到需要登录、验证码或动态渲染的页面时，由 AI 请求打开浏览器，**用户亲自登录**，AI 只读取本机过滤后的内容。详见下节。
- **图表工具** —— `chart_display` 支持折线图、柱状图、散点图，只读、不额外审批。

### 🌐 内置浏览器（F013）

- 固定 **8 个工具**：打开页面、当前网址、获取文本、列举可操作项、点击、输入、滚动、声明完成。**不提供**任意 JavaScript、原始 DOM、Cookie、无头模式、截图或 OCR。
- **三层防护**：页面文本在返回给 AI 前，于本机过滤疑似 API Key、JWT、长令牌和通过 Luhn 校验的卡号；支付、删除、授权、发布、转账、密码框和文件操作**硬拒**，无法通过审批绕过；过滤默认开启，关闭需要二次确认。
- 点击/输入使用一次性元素 ID，页面变化后旧 ID 直接拒绝。
- 浏览器底部有可展开的**迷你对话面板**，绑定原会话，支持流式回复、工具审批、停止与错误卡片。
- 支持**手机 / 电脑**两种显示模式，切换时在同一个 WebView 上重新加载，不清除登录状态。
- 群组模式下不提供浏览器。

### 👥 群组模式（F012）

- 抽屉顶部可在**聊天 / 群组**之间切换，两种模式各自保留列表、草稿和配置。
- **独立组员库**：名称、头像、模型、提示词、流式输出、标签、数值参数（温度 / Top P / 上下文条数 / 最大 Token），支持长按拖拽排序。
- **群组配置**：群名、头像、标签、组员增删与优先级、MCP、本地工具、扩展（提示词注入 / 世界书 / 技能）、群组记忆、自定义请求、背景（图片 / 渐变 / 透明度）、压缩设置、群级思考等级。
- **群聊生成**：按顺序串行发言；支持多会话、完整群复制、副群主分派、@点名、消息双向引用。
- **群内昵称与头像**：按群隔离，历史气泡实时解析，不重写历史。
- **群聊压缩**：手动压缩 + 分层摘要 + 可编辑提示词 + 默认开启的自动提醒（需用户确认后才消耗 Token）。
- **工作区归属**：工作区可按「聊天 / 群组 / 未归属」过滤，新建时可设置归属；群组使用独立工作区。

### 🔌 供应商与搜索

- **DeepSeek 网页版（免费）** —— 用网页登录态而不是 API Key 驱动对话。内置只读工具白名单、请求串行闸门与可调节流，切断文件写入、Shell、代码执行、技能和 MCP 等能力，降低账号风控风险。
- **搜索服务商**：在上游基础上新增
  - **随想搜索**（`/v1/web_search`，固定 0.06 元/次，失败不扣费）
  - **DeepSeek 官方搜索与抓取**（`/v1/tools/search` + `/v1/tools/fetch`，0.005 元/次，长摘要专供模型阅读）
  - **AnySearch**（可匿名使用；带 Key 免费 1000 次/天，同时提供正文抓取）
- **思考模式推理回传修复** —— DeepSeek 思考模式在工具调用后继续生成时，自动完整回传历史 `reasoning_content`，修复 HTTP 400 中断。

### 🎨 界面

- **按供应商的加载动画** —— 每个供应商可独立选择：按品牌自动匹配、内置预设、自定义 GIF / 图片。内置 OpenAI 旋转、Claude 星芒、Gemini sparkle、DeepSeek 脉冲与通用呼吸动画。
- **液态玻璃** —— Android 13+ 在聊天输入栏使用真实折射与色散；低版本或渲染异常自动回退到模糊玻璃，不会崩溃。
- **品牌与关于页** —— 应用名统一为 `RikkaHub Work`；关于页只保留本仓库链接；移除赞助、QQ / Discord 入口与导出水印。
- **群组界面** —— 群组聊天页透明顶栏、群组抽屉日期分组与置顶、组员与群组拖拽排序。

### 🔄 更新与稳定性

- **fork 自有更新检查** —— 只读取本仓库的 GitHub Releases；发现新版本后跳转发布页，由用户自行下载安装。
- **后台挂起修复** —— 生成期间持有 `PARTIAL_WAKE_LOCK`，并加入 20 分钟硬截止（每 5 秒用包含休眠的时钟检测）；请求日志改用活动耗时，并单独标注设备休眠时长，不再把冻结时间算成网络耗时。
- 生成超时和「因设备休眠中断」都有明确提示，先落错误卡片再取消任务。

## 🚧 开发中

下面这些**已实现但尚未发布**，或**已有明确方案、正在实施**。只列有方案的东西，不做空头承诺。

### 🧭 内置浏览器的设置入口与工具开关

当前浏览器只能由 AI 请求唤起。下一版补上「用户自己打开」：

- **设置 → 模型与服务 → 内置浏览器**，直接启动到 `about:blank`，先登录再交给 AI —— 不用再绕到助手详情页。
- 独立的浏览器设置页：打开浏览器 / 清除浏览数据（cookies、本地存储、缓存）/ **8 个工具逐个开关** / 敏感内容过滤 / 手机·电脑模式 / 两个超时上限（单工具 10~600 秒、单任务 1~60 分钟）。
- 从设置打开的浏览器是**独立会话**：不新建聊天、不显示 AI 面板；登录态保留，之后 AI 接管仍然可用。

### 🫀 脉搏模式（PULSE）

第三个模式，与「聊天」「群组」并列。AI 不必等你开口：

- **心跳** —— 按设定间隔自检，有事才来找你，无事保持静默。
- **计划任务** —— 到点自动执行；错过的任务补跑一次。
- **监视器** —— 定时检查你关心的外部状态，只在**发生变化**时推送，没有变化不调用模型。
- **记忆晋升** —— 反复命中的记忆提升为长期提示词。
- 保活按「申请 + 引导 + 降级」三层设计：前台服务、Alarm + WorkManager、电池优化 / 自启动 / 精确闹钟引导。

### 🧰 更多本地工具

评估中：工作区文件检索（grep / find）、设备状态、天气、系统页面跳转、分享、联系人、最近媒体。
优先做只读、免 Key、不依赖 Linux 环境的。

### 🧠 记忆系统增强

记忆分类、强化计数与晋升（把反复用到的记忆提升为长期提示词），方案已有、待立项。

## 📦 上游继承的功能

> 以下能力来自上游，本 fork 原样保留。

- 🎨 Material You 设计语言与 🌙 深色模式
- 📦 工作区：基于 proot 的 Linux 智能体环境
- 🔄 多供应商支持：自定义 API / URL / 模型（所有 OpenAI、Google、Anthropic 兼容接口）
- 🖼️ 多模态输入（图片、文本文档、PDF、Docx）
- 🖥️ Web 访问，多平台使用
- 🛠️ MCP 支持
- 📝 Markdown 渲染（代码高亮、LaTeX 公式、表格、Mermaid）
- 🪾 消息分支
- 🔍 搜索能力（Exa、Tavily、智谱、LinkUp、Brave、Perplexity 等）
- 🧩 提示词变量（模型名、时间等）
- 🤳 供应商二维码导出与导入
- 🤖 智能体定制
- 🧠 类 ChatGPT 记忆
- 📝 AI 翻译
- 🌐 自定义 HTTP 请求头与请求体
- 💌 SillyTavern 角色卡导入

## 🧭 版本与包名

| 项目 | 值 |
|---|---|
| Release 包名 | `me.rerere.rikkahub.plus` |
| Debug 包名 | `me.rerere.rikkahub.debug` |
| 版本号 | `{上游版本}-work.{n}` |
| 当前发布 | `2.5.6-work.3`（versionCode 196） |
| 工作区中的下一版 | `2.5.6-work.4`（versionCode 197，尚未发布） |
| 许可证 | AGPL-3.0 |

## 🛠 构建

使用 [Android Studio](https://developer.android.com/studio) 构建。技术栈：

- [Kotlin](https://kotlinlang.org/)（开发语言）
- [Koin](https://insert-koin.io/)（依赖注入）
- [Jetpack Compose](https://developer.android.com/jetpack/compose)（UI 框架）
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)（偏好设置）
- [Room](https://developer.android.com/training/data-storage/room)（数据库）
- [Coil](https://coil-kt.github.io/coil/)（图片加载）
- [Material You](https://m3.material.io/)（UI 设计）
- [Navigation 3](https://developer.android.com/guide/navigation/navigation-3)（导航）
- [OkHttp](https://square.github.io/okhttp/)（HTTP 客户端）
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)（JSON 序列化）

> [!TIP]
> 构建前需要在 `app` 目录放置 `google-services.json`。

> [!IMPORTANT]
> 本 fork 不接受上游的翻译类 PR；语言只保留 English、简体中文、繁體中文。

## 🙏 致谢

本项目的基础工作全部归功于 [RikkaHub](https://github.com/rikkahub/rikkahub) 及其贡献者。没有上游的工作，就不会有这个 fork。

用到的第三方组件：

- [AndroidLiquidGlassView](https://github.com/QmDeve/AndroidLiquidGlassView)（MIT，© Donny Yale / QmDeve）
- [Mozilla Readability](https://github.com/mozilla/readability)（Apache-2.0，用于正文提取）
- [Haze](https://github.com/chrisbanes/haze)（Apache-2.0，玻璃模糊回退）

如果这个项目对你有帮助，也请给**上游**点个 Star ⭐

## 📄 许可证

本 fork 以 **AGPL-3.0** 授权，原始版权归 RikkaHub 作者所有。
完整许可证见仓库根目录 [LICENSE](./LICENSE)。
