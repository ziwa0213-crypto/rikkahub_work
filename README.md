<div align="center">
  <img src="docs/icon.png" alt="App 图标" width="100" />
  <h1>RikkaHub Work</h1>

一个基于 RikkaHub 的原生 Android LLM 聊天客户端，支持切换不同的供应商进行对话 🤖💬

[English](README_EN.md) | 简体中文
</div>

> ✅ **当前发布：[`3.1.1-work.1`](https://github.com/ziwa0213-crypto/rikkahub_work/releases/latest)**（versionCode 200，2026-10-31）—— 已合并上游 3.1.1。
> ⚠️ **升级前请先备份**：本次数据库从 32 升到 33，升级后无法回退。详见下文「安装与升级」。

<div align="center">
  <img src="docs/img/chat.png" alt="聊天界面" width="150" />
  <img src="docs/img/desktop.png" alt="模型选择" width="450" />
</div>

## 📌 关于本 fork

RikkaHub Work 是 [RikkaHub](https://github.com/rikkahub/rikkahub) 的**个人 fork**，以自用为主，顺手分享。它跟随上游发版，并在此基础上追加了一批功能与修复。

- **不是 RikkaHub 官方版本。** 本仓库的一切都建立在上游工作之上；本 fork 的问题请提到**本仓库**，与上游无关。
- **版本号规则**：`{上游版本}-work.{n}`，例如 `3.1.1-work.1`。每次跟进新的上游版本时，`n` 归 1。
- **语言**：仅保留 English、简体中文、繁體中文。
- 构建产物通过本仓库的 GitHub Releases 分发。

## 📥 安装与升级

到本仓库的 **Releases** 页面下载 APK。

- **包名**：`me.rerere.rikkahub.plus` —— 与上游的 `me.rerere.rikkahub` 不同，所以**本 fork 可以和官方 RikkaHub 共存**。
- **升级本 fork**：直接覆盖安装即可。但签名必须一致、且 versionCode 不能倒退，否则系统会拒绝安装。
- **从其它 fork 或官方版转过来**：需要先卸载。签名不同，不卸载装不上。
- 提供三种构建：`arm64-v8a`、`x86_64` 和 `universal`，多数手机选 `arm64-v8a`。安装包约 16 MB（`universal` 约 19 MB）。

> [!WARNING]
> **数据库只升不降。** 本版把数据库从 32 升到 33，装回任何旧版本都会因 Room 降级而无法启动，只能清数据重来。
> **升级前请先在「设置 → 备份」做一次完整备份（勾选包含数据库）**，并保留一份当前版本的 APK。设置、对话和 API Key 都在应用私有目录里，卸载即丢失。

## ✨ 本 fork 增加的功能

### 🗂 记忆

- **记忆分组** —— 让多个助手共享同一组记忆，同时保留私有记忆与内置全局记忆。删除分组时，组内记忆回落到私有范围，不会直接删除内容。
- **记忆迁移多选** —— 复制助手、或切换助手的记忆归属时，可以逐条勾选要带走哪些记忆，并在「复制 / 移动」之间选择。移动共享记忆前会提示影响范围。
- **聊天 ⇄ 群聊双向导入** —— 群聊当前会话可以从任意助手私有、全局或记忆分组导入记忆；助手也可以按「群名 · 会话标题」从群聊导入。默认复制，来源列表按真实作用域去重。

### 🛠 工具与审批

- **三档工具审批** —— 每个助手独立保存：**需要询问**（每次确认）、**部分询问**（仅高风险确认，默认）、**完全允许**（不确认）。风险按 L0~L3 分级，未登记工具默认按最高风险处理。群聊侧在群组配置里改档位。
- **日历删除** —— 新增 `calendar_delete`，删除建错的日程；删除操作始终需要确认。
- **内置浏览器（凭据隔离）** —— 遇到需要登录、验证码或动态渲染的页面时，由 AI 请求打开浏览器，**用户亲自登录**，AI 只读取本机过滤后的内容。详见下节。

### 🌐 内置浏览器（F013）

- 固定 **8 个工具**：打开页面、当前网址、获取文本、列举可操作项、点击、输入、滚动、声明完成。**不提供**任意 JavaScript、原始 DOM、Cookie、无头模式、截图或 OCR。
- **三层防护**：页面文本在返回给 AI 前，于本机过滤疑似 API Key、JWT、长令牌和通过 Luhn 校验的卡号；支付、删除、授权、发布、转账、密码框和文件操作**硬拒**，无法通过审批绕过；过滤默认开启，关闭需要二次确认。
- 点击/输入使用一次性元素 ID，页面变化后旧 ID 直接拒绝。
- 浏览器底部有可展开的**迷你对话面板**，绑定原会话，支持流式回复、工具审批、停止与错误卡片。
- 支持**手机 / 电脑**两种显示模式，切换时在同一个 WebView 上重新加载，不清除登录状态。
- **独立的设置入口** —— 设置 → 模型与服务 → 内置浏览器：直接打开到 `about:blank`，先登录再交给 AI；可一键清除浏览数据（cookies、本地存储、缓存）。
- **逐项开关与超时** —— 8 个工具可单独启用或禁用；敏感内容过滤默认开启（关闭需二次确认）；手机 / 电脑模式；单工具超时（10 秒 ~ 10 分钟）与单任务超时（1 ~ 60 分钟）。
- 群组模式下不提供浏览器。

### 👥 群组模式（F012）

- 抽屉顶部可在**聊天 / 群组**之间切换，两种模式各自保留列表、草稿和配置；**重启后保持上次的模式**。
- **独立组员库**：名称、头像、模型、提示词、流式输出、标签、数值参数（温度 / Top P / 上下文条数 / 最大 Token），支持长按拖拽排序。
- **群组配置**：群名、头像、标签、组员增删与优先级、MCP、本地工具、扩展（提示词注入 / 世界书 / 技能）、群组记忆、自定义请求、背景（图片 / 渐变 / 透明度）、压缩设置、群级思考等级。
- **群聊生成**：按顺序串行发言；支持多会话、完整群复制、副群主分派、@点名、消息双向引用。
- **群内昵称与头像**：按群隔离，历史气泡实时解析，不重写历史。
- **群聊压缩**：手动压缩 + 分层摘要 + 可编辑提示词 + 默认开启的自动提醒（需用户确认后才消耗 Token）。
- **工作区归属**：工作区按「聊天 / 群组 / 未归属」过滤，新建时归属当前模式；从文件选择器、扩展页进入工作区管理时同样按当前模式过滤。

### 🔌 供应商与搜索

- **DeepSeek 网页版（免费）** —— 用网页登录态而不是 API Key 驱动对话。内置只读工具白名单、请求串行闸门与可调节流，切断文件写入、Shell、代码执行、技能和 MCP 等能力，降低账号风控风险。
  - **手动登录**：粘贴 DevTools 的 `Copy as cURL` 或请求头文本即可自动解析出 `authorization`、`cookie` 与指纹请求头，不用再区分 Token 与 Cookie；原始的 Token / Cookie 单字段填写保留在「高级」折叠区。
  - 粘贴的内容只留在当前页面内存里，不写进设置，也不会记录凭证明文。
- **搜索服务商**：在上游基础上新增
  - **随想搜索**（`/v1/web_search`，固定 0.06 元/次，失败不扣费）
  - **DeepSeek 官方搜索与抓取**（`/v1/tools/search` + `/v1/tools/fetch`，0.005 元/次，带专供模型阅读的长摘要）
  - **AnySearch**（可匿名使用；带 Key 免费 1000 次/天，同时提供正文抓取）
- **思考模式推理回传修复** —— DeepSeek 思考模式在工具调用后继续生成时，自动完整回传历史 `reasoning_content`，修复 HTTP 400 中断。

### 🎨 界面

- **按供应商的加载动画** —— 每个供应商可独立选择：按品牌自动匹配、内置预设、自定义 GIF / 图片。内置 OpenAI 旋转、Claude 星芒、Gemini sparkle、DeepSeek 脉冲与通用呼吸动画。
- **液态玻璃** —— Android 13+ 在聊天输入栏使用真实折射与色散；低版本或渲染异常自动回退到模糊玻璃，不会崩溃。
- **品牌与关于页** —— 应用名统一为 `RikkaHub Work`；关于页只保留本仓库链接；移除赞助、QQ / Discord 入口与导出水印。
- **群组界面** —— 群组聊天页透明顶栏、群组抽屉日期分组与置顶、组员与群组拖拽排序。
- **设置页行结构统一** —— 本 fork 自己写的页面（浏览器、DeepSeek 网页版、群组配置等）补齐行首图标、去掉重复的分组标题、开关统一改用卡片行。

### 🔄 更新与稳定性

- **fork 自有更新检查** —— 只读取本仓库的 GitHub Releases；发现新版本后跳转发布页，由用户自行下载安装。
- **后台挂起修复** —— 生成期间持有 `PARTIAL_WAKE_LOCK`，并加入 20 分钟硬截止（每 5 秒检测一次）；请求日志改用活动耗时，并单独标注设备休眠时长，不再把冻结时间算成网络耗时。
- 生成超时和「因设备休眠中断」都有明确提示，先落错误卡片再取消任务。
- **启动竞态修复** —— 修复「上次退出时是群组模式，重启却回到聊天模式」：设置与占位会话都是异步加载，旧代码在首帧就按默认值渲染并写回设置，把用户的真实选择覆盖掉了。

## ⚠️ 已知限制

| 项 | 说明 |
|---|---|
| DeepSeek 网页版登录页可能白屏 | 应用内 WebView 打开登录页时可能空白（UA / 指纹层面，未定位）。界面会给出诊断提示，建议改用系统浏览器 / Via 登录后，用「粘贴 cURL」导入 |
| 浏览器电脑模式只是显示伪装 | 同一内核渲染，`navigator.platform` 等仍可被探测；已经 302 跳到移动站的页面救不回来 |
| 液态玻璃需要 Android 13 及以上 | 低版本自动用模糊玻璃，不报错，也不抬高安装门槛 |
| 数据库只升不降 | 当前版本 33，装回旧版本读不了数据，只能清数据 |

## 📦 上游继承的功能

> 以下能力来自上游，本 fork 原样保留。

- 🎨 Material You 设计语言与 🌙 深色模式
- 🧠 记忆双板块：逐条记忆 + 文件式记忆
- 🎬 媒体创作：图片与视频生成
- 📦 工作区：基于 proot 的本地 Linux 智能体环境，或通过 **SSH 连接远程机器**；内置**终端**页面
- 🪟 后台生成悬浮窗（生成时可切走，回来接着聊）
- 🔄 多供应商支持：自定义 API / URL / 模型（所有 OpenAI、Google、Anthropic 兼容接口）
- 🖼️ 多模态输入（图片、文本文档、PDF、Docx）
- 🖥️ Web 访问，多平台使用
- 🛠️ MCP 支持
- 📝 Markdown 渲染（代码高亮、LaTeX 公式、表格、Mermaid）
- 📊 图表工具 `chart_display`（折线 / 柱状 / 散点，只读、不额外审批）
- 🪾 消息分支
- 🔍 搜索能力（Exa、Tavily、智谱、LinkUp、Brave、Perplexity 等）
- 🧩 提示词变量（模型名、时间等）
- 🤳 供应商二维码导出与导入
- 🤖 智能体定制
- 📝 AI 翻译
- 🌐 自定义 HTTP 请求头与请求体
- 💌 SillyTavern 角色卡导入
- ✂️ 图片裁剪、压缩上下文步进器、工具调用耗时显示

## 🧭 版本与包名

| 项目 | 值 |
|---|---|
| Release 包名 | `me.rerere.rikkahub.plus` |
| Debug 包名 | `me.rerere.rikkahub.debug` |
| 版本号 | `{上游版本}-work.{n}` |
| 当前发布 | `3.1.1-work.1`（versionCode 200，2026-10-31） |
| 数据库版本 | 33（只升不降） |
| 许可证 | AGPL-3.0 |

> 开发过程、各阶段进度与缺陷根因见 [DEVELOPMENT.md](./DEVELOPMENT.md)。

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

出包任务：上游把分发渠道拆成了两个 flavor，GitHub 版要打 **`assembleGithubRelease`**（本 fork 只构建 `github` 渠道）。

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
- [Termux terminal-emulator](https://github.com/termux/termux-app)（Apache-2.0，终端内核）

如果这个项目对你有帮助，也请给**上游**点个 Star ⭐

## 📄 许可证

本 fork 以 **AGPL-3.0** 授权，原始版权归 RikkaHub 作者所有。
完整许可证见仓库根目录 [LICENSE](./LICENSE)。
