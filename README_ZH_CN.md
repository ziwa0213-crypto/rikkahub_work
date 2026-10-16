<div align="center">
  <img src="docs/icon.png" alt="App 图标" width="100" />
  <h1>RikkaHub Work</h1>

一个基于 RikkaHub 的原生 Android LLM 聊天客户端，支持切换不同的供应商进行聊天的 fork 🤖💬

[English](README.md) | 简体中文
</div>

<div align="center">
  <img src="docs/img/chat.png" alt="Chat Interface" width="150" />
  <img src="docs/img/desktop.png" alt="Models Picker" width="450" />
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

## 🔀 与上游的区别

### 🗂 记忆

- **记忆分组** —— 让多个助手共享同一组记忆，同时保留私有记忆与内置全局记忆。
- **记忆迁移多选** —— 复制助手或切换记忆归属时，可按条目勾选并选择复制或移动。

### 🛠 工具与审批

- **工具审批模式** —— 按助手决定工具调用前是否确认：始终询问 / 仅高风险询问 / 无需确认。
- **日历删除工具** —— 可删除建错的日历事件。

### 🤖 供应商

- **DeepSeek 网页版（免费）** —— 用网页登录态而非 API Key 驱动的内置供应商，只读工具 + 三层防护。
- **随想搜索服务商** —— 新增一个内置的全网搜索服务商。
- **DeepSeek 思考模式修复** —— 使用工具时强制回传历史 `reasoning_content`，修复 HTTP 400。

### 🎨 界面

- **加载动画个性化** —— 按供应商独立保存加载动画，按品牌自动匹配，支持自定义 GIF / 图片。
- **液态玻璃效果** —— 聊天输入栏的折射与色散（Android 13+），低版本自动回退。
- **群组功能** —— 与「聊天」并列的第三种模式。从抽屉切换，含独立的组员库、群组配置、群组级工作区，以及群内昵称 / 头像。

### ⚙️ 其他

- **更新源改造** —— 更新检查只读取本 fork 的 GitHub Releases，由用户自行下载安装。
- **关于页与品牌本地化** —— 应用名为 `RikkaHub Work`，移除赞助与推广入口，导出内容无水印。

## 🚧 开发中

以下功能已实现或处于设计阶段，但**尚未发布**，将在后续版本中推出。

### 🔐 凭据隔离浏览器

围绕一个原则构建的内置浏览器：**你的登录凭据绝不进入模型厂商。**

- **由你**完成登录、过验证码和危险操作 —— AI 只能*看到*页面内容。
- 🔴 **不提供 `eval_js`。** AI 只能调用一小组白名单 JS 函数（`readText`、`click`、`typeInto`…），且只能传普通参数。
- 🔴 **高危元素硬性拒绝** —— 支付、删除、授权、发布、密码框。AI 一旦尝试，操作会失败并告知用户。
- ⭐ **本地过滤** —— Token、卡号、JWT 和密码框内容在**离开进程之前**就被替换为 `[已过滤:…]`。
- **电脑模式 / 手机模式切换器** —— 工具栏上的两档分段控件。切换**绝不重建 WebView**，登录态不会丢。
- ⛔ 不做：无头模式（过不了人机验证）、完整 DOM 访问、任意 JS 执行。

### 🧠 群组记忆迁移（聊天 ⇄ 群组）

在聊天助手与群组之间**双向**迁移记忆，支持按条目勾选。

- **导入到群组** —— 在群组记忆页，从任意助手（私有 / 全局 / 任意记忆分组）导入。
- **导入到助手** —— 在助手记忆页，从任意群聊会话导入。
- 可逐条勾选，并选择**复制**或**移动**。

### 🫀 脉搏模式 *（设计阶段）*

与「聊天」「群组」并列的第三种模式：助手**自己运转**，有事才来找你。包含计划任务、周期性自检，以及可选的感知层（通知 / 短信 / 邮件）。

### 🧠 记忆系统升级 *（设计阶段）*

让记忆能够*成长*：分类、强化计数，以及把经过反复验证的条目晋升为长期准则。

## ✨ 功能特色

> 以下为上游原有功能。本 fork 追加的部分见上方的「与上游的区别」。

- 🎨 现代化安卓APP设计（Material You / 预测性返回）和 🌙 暗色模式
- 📦 工作区：基于 proot 的 Linux 智能体环境
- 🔄 多种类型的供应商支持，自定义 API / URL / 模型（目前支持 OpenAI、Google、Anthropic）
- 🖼️ 多模态输入支持（图片、文本文档、PDF、Docx）
- 🖥️ Web多端访问支持
- 🛠️ MCP 支持
- 📝 Markdown 渲染（支持代码高亮、数学公式、表格、Mermaid）
- 🪾 消息分支
- 🔍 搜索功能（Exa、Tavily、Zhipu、LinkUp、Brave、Perplexity、..）
- 🧩 Prompt 变量（模型名称、时间等）
- 🤳 二维码导出和导入提供商
- 🤖 智能体自定义
- 🧠 类ChatGPT记忆功能
- 📝 AI翻译
- 🌐 自定义HTTP请求头和请求体
- 💌 Silly Tavern 角色卡导入

## 🛠 贡献

本项目使用 [Android Studio](https://developer.android.com/studio) 开发。

技术栈文档：

- [Kotlin](https://kotlinlang.org/) (开发语言)
- [Koin](https://insert-koin.io/) (依赖注入)
- [Jetpack Compose](https://developer.android.com/jetpack/compose) (UI 框架)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore?hl=zh-cn#preferences-datastore) (
  偏好数据存储)
- [Room](https://developer.android.com/training/data-storage/room) (数据库)
- [Coil](https://coil-kt.github.io/coil/) (图片加载)
- [Material You](https://m3.material.io/) (UI 设计)
- [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) (导航)
- [Okhttp](https://square.github.io/okhttp/) (HTTP 客户端)
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) (Json序列化)

> [!TIP]
> 你需要在 `app` 文件夹下添加 `google-services.json` 文件才能构建应用。

> [!IMPORTANT]
> 以下PR将被拒绝：
> 1. 添加新语言，因为添加新语言会增加后续本地化的工作量
> 2. AI生成的大规模重构和更改

## 🙏 致谢

本项目的基础全部来自 [RikkaHub](https://github.com/rikkahub/rikkahub) 及其贡献者，没有他们的工作就不会有这个 fork。

如果喜欢这个项目，也请给**上游**点个 Star ⭐

## 📄 许可证

本项目基于 [GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0) 开源，继承自 RikkaHub。
