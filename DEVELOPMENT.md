# 开发进度 / Development Status

> 最后更新：**2026-11-20**
> 当前发布：**`3.3.0-work.2`**（versionCode **208**，2026-11-20）。当前没有正在开发的版本。
> 功能说明见 [README.md](./README.md)，版本变更见 GitHub Releases。本文件不把编译与 JVM 测试当成真机通过。

---

## 0. 一句话状态

**`3.3.0-work.1`（207）与 `3.3.0-work.2`（208）都已发布。** 这一轮做完了四件事：合并上游 3.3.0、DeepSeek 官方工具与客户端元信息、工作区文件预览（HTML 部分回退）、移除本地 AI 模型。

- 版本号：`3.3.0-work.2` / versionCode **208**
- 数据库：**34**（只升不降）
- 包名：`me.rerere.rikkahub.plus`
- 下一阶段：第四板块「角色」，先出界面预览

---

## 1. 这一版做了什么

### 1.1 合并上游 3.3.0 ｜ 已完成

| 变化 | 说明 |
|---|---|
| DeepSeek 语音 | 语音识别与合成本地接入，可直接对话 |
| DeepSeek 记录导入 | 可把 DeepSeek 当前分支的聊天记录导进来 |
| 气泡分段 | 助手回复按段落拆成一个气泡一段 |
| 工作区共享存储 | 外部存储挂载到工作区 |
| Vertex Interactions | 服务账号支持新的 Google 接口形态 |
| 安全模式 | 清理超大设置数据，避免设置撑爆 |
| 连接测试弹窗 | 供应商连通性测试结果集中展示 |
| Phone Use | 跟进上游：后台生成期间用悬浮窗保持亮屏；微信加入禁止操作列表 |

合并时保留本 fork 的全部既有能力（F004 审批、F007 reasoning 回传、F009 网页版边界、F012 群组、F013 浏览器、F015 脉搏、F017 工作区、F019 闹钟）。

### 1.2 DeepSeek 官方工具与客户端元信息 ｜ 已完成

DeepSeek 的 Chat Completions 支持在请求里声明服务端工具：

- **联网搜索**与**读取网页**由服务端执行，把结果、来源链接和引用一起返回，客户端不需要额外跑一轮工具；执行状态、结果卡片、失败原因都会显示
- 服务端工具调用与签名会被保存，并在后续轮次回传，多轮工具调用不会断链
- 同一个开关下开思考模式，就是「边推理边搜」
- 工具列表里新增独立的「读取网页」开关；搜索不新增开关，复用原有的「启动模型内置搜索」

**客户端元信息**是把日期、星期、界面语言、运行平台告诉模型。做法上分两条路径：

- DeepSeek 的 Chat Completions → 逐条附在该条用户消息之前，日期取这条消息自己的时间
- 其余协议（包括 DeepSeek 的 Responses / Messages）→ 拼一行进系统提示

`location` 留空，不申请定位权限；`platform` 固定 `App`。新建助手与脉搏默认开启，已有配置保留原选择，群组继续用自身工具配置。

### 1.3 工作区文件预览 ｜ 已完成（HTML 部分回退）

- **Markdown**：默认显示渲染效果，可切回源码；相对图片按当前文档所在目录解析，FILES 与 LINUX 两个区域都支持；外链图片沿用原有行为
- **HTML / SVG**：维持原有的网页预览方式，同目录的图片 / CSS / JS 不再自动映射
- FILES 保持可编辑保存，LINUX 保持只读及原有的 512 KiB 读取保护；超过 1 MiB 的文件只查看源码，不渲染（HTML / SVG 不套用这个阈值）

**中间走过一次弯路，值得记下来。** 最初给 HTML 做了一个资源拦截器，想让 HTML 也能加载同目录图片和样式，顺带把网络加载关掉。实机上直接失败：`net::ERR_HTTP_RESPONSE_CODE_FAILURE`。原因有两层叠加——把网络加载关掉之后，`loadDataWithBaseURL` 的 `data:` 主文档一起被拦；而拦截器又对所有请求返回内容，主文档本身也被替换成了 404。

结论是整体回退 HTML / SVG 那部分，只保留 Markdown。删掉了三个拦截器文件，`WebViewState.requestInterceptor` 留作空扩展点。**教训：WebView 的主文档与子资源不是同一类请求，拦截器不能一刀切。**

### 1.4 工作区上传与附件 ｜ 已完成

视频、压缩包和其他二进制文件不再编码进请求体：

- 上传到工作区的 `FILES/uploads/日期/` 目录，保留原文件名，同名加后缀
- 单文件上限 2 GiB；超过 200 MiB 显示进度并可取消；失败会清理残片
- 消息里显示名称与大小，可打开文件或复制路径；模型拿到的是路径和元信息，不读二进制全文
- 未绑定工作区时提示绑定，并保留已输入的文字草稿
- 文件随工作区保存，不纳入聊天附件备份

### 1.5 群组与兼容调整 ｜ 已完成

- 群组自动压缩统一改用全局开关与阈值，移除群组里重复的那套自动提醒与配置；手动压缩、分层摘要与提示词编辑保留
- 数据库升级到 **34**，保留本分支的记忆表、工作区归属等结构
- 继续保留 DeepSeek 网页版、群组、脉搏、Rootfs、玻璃界面等既有功能
- 继续只保留英文、简体中文、繁体中文

### 1.6 本地 AI 模型 ｜ 已移除

在手机上跑 GGUF（llama.cpp）这条路完整走过一遍，最后决定终止：

- **CPU**：能出字，但只有个位数 token/s，达不到日常可用
- **GPU**：开启 Vulkan 后部分机型在提交阶段就被驱动回收（`ErrorDeviceLost`），一个字都产不出来
- **模型质量**：低参数量化模型在理解、工具调用和长上下文上的表现，跟云端 API 差了不止一档

`3.3.0-work.2` 起正式移除：本地模型供应商、`llama-cpp` 推理模块、CPU / Vulkan 后端、模型加载与卸载通知、GPU 与内存融合开关、本地提示预算，以及相关界面与专用文案。

**旧数据怎么处理**：本地模型在设置数据里是一个供应商类型，直接删类型会让老用户的设置文件解析失败，连带丢掉 API Key、对话和记忆。所以先加了一层读取过滤——读到已移除的供应商就跳过，其余配置照常保留。助手、群组成员、脉搏里指向本地模型的引用会被清理，全局聊天模型失效时回落到现有模型。

**已下载的 GGUF 文件不自动删除，也不提供清理入口**，但新版不再加载。

---

## 2. 已排期：第四板块「角色」

现在应用里有三个板块：**工作**（原「聊天」）、**群组**、**脉搏**。计划新增第四个 **角色**，专门承载娱乐向的角色扮演，包括酒馆（SillyTavern）那类玩法。

顺序调整为：工作 → 角色 → 群组 → 脉搏。

- **角色是独立实体**，不占用助手列表，也不复用助手的设置；助手列表里不会出现角色
- 角色有自己的会话列表；已有助手的会话可以按用户勾选迁移过来，逐个选「复制」还是「移动」
- **工作区从「归属一个模式」改成「勾选」**：同一个工作区可以同时被工作、角色、群组选中；脉搏的工作区固定，不参与
- 角色**不保留按条记忆**，改用文件形式的记忆，另外可以只读引用工作侧的指定记忆
- **兼容酒馆角色卡**：导入 PNG / JSON，字段映射、示例对话、世界书、正则、宏引擎
- 针对角色扮演里常见的「AI 味」——「我接住了」「我得先明说」这类在演助理不在演角色，以及网文腔的「低沉沙哑的嗓音」「深邃的眼眸」——从提示词、示例对话、深度注入、正则清洗几层一起压

设计已闭合，代码一行没动。开工第一步是先出界面预览确认样子。

---

## 3. 验证状态

| 项 | 状态 |
|---|---|
| 编译 / JVM 单测 | 通过（Codex 本机执行，1423 项通过、1 项跳过） |
| APK 构建 | 通过，ARM64 签名 Release |
| 三语 key 一致性 | 通过，English / 简中 / 繁中各 2,616 条（本地模型专用文案各删 103 条） |
| 旧配置兼容 | 5 项专项通过（旧供应商解码、真实 DataStore 读写重开、云端凭据保留、失效引用清理、旧开关键忽略） |
| 完整 Lint | 未通过，不计入通过项 |
| 真机 / 真实接口 | `3.3.0-work.1` / `3.3.0-work.2` 的覆盖安装、界面交互、DeepSeek 服务端工具真实调用与签名回传仍待验证 |

---

## 4. 下一步

1. 角色板块：先出界面预览，确认后再写任务书
2. 角色板块开工前，把工作区归属改成勾选制
3. 继续跟进上游版本

---

## English Summary

**`3.3.0-work.2` (versionCode 208, 2026-11-20) is the current release; `3.3.0-work.1` (207) shipped the same day.**
The database is now **34** and cannot be rolled back.

**What this round delivered**

- **Upstream 3.3.0 merged** — DeepSeek speech, DeepSeek history import, paragraph-split bubbles, shared-storage workspaces, Vertex Interactions, a safe mode that trims oversized settings, a connection-test dialog, and the upstream Phone Use changes. Every existing fork feature is preserved.
- **DeepSeek server-side tools** — declare web search and URL reading on the request and the API runs them, returning results, sources and citations in the same turn. Tool calls and signatures are stored and replayed. Under the same switch, thinking mode searches while it reasons.
- **Client metadata** — date, weekday, interface language and platform. DeepSeek gets it per user message with that message's own date; other protocols get it in the system prompt. New assistants and Pulse start with it on.
- **Workspace file preview** — Markdown renders with a source toggle and relative images resolve against the document's directory. An early attempt to intercept HTML resources failed on device (`ERR_HTTP_RESPONSE_CODE_FAILURE`) and was rolled back: HTML / SVG keep the original web preview and no longer map sibling assets.
- **Workspace uploads** — videos, archives and other binaries go to `FILES/uploads/<date>/` and reach the model as a path rather than Base64, with a 2 GiB per-file cap and progress above 200 MiB.
- **Group compression** — now shares the global automatic-compression switch and threshold.

**On-device AI models have been removed.** llama.cpp ran end to end: CPU produced tokens but only at single-digit tokens/s, and the Vulkan backend hit `ErrorDeviceLost` before the first token on affected devices; small quantized models also fell well short of cloud quality. `3.3.0-work.2` removes the local provider, the inference module, both backends and all related UI. Existing settings are read through a compatibility filter so other providers and API keys survive; downloaded GGUF files are left on disk but are no longer loaded.

**Scheduled next:** the fourth mode, **Characters** — role-play as a first-class entity, separate from assistants, with its own conversations, SillyTavern card import, file-based memory, a workspace selected by checkbox rather than owned by one mode, and a multi-layer pass against role-play "assistant voice". Design is closed; the first step is a UI preview.
