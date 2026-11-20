# 开发进度 / Development Status

> 最后更新：**2026-11-20**
> 本文件**只记录当前正在开发的版本**：`3.3.0-work.1`（尚未发布）。功能说明见 [README.md](./README.md)，版本变更见 [CHANGES.md](./CHANGES.md)。
> 上一版 `3.2.0-work.3` / 206 已发布，其内容见 Releases 页。

---

## 0. 一句话状态

**上游 3.3.0、F021 工作区文件预览与 F022 DeepSeek 服务端工具 / 客户端元信息已完成，并已构建三个签名 Release；尚未发布，真实接口和设备交互仍待验。**

- 版本号：`3.3.0-work.1` / versionCode **207**（未发布前不升）
- 数据库：**33 → 34**（装上之后不可回退，先备份）
- 包名：`me.rerere.rikkahub.plus`

---

## 1. 这一版包含什么

### 1.1 合并上游 3.3.0 ｜ 已完成

| 变化 | 说明 |
|---|---|
| DeepSeek 语音 | 语音识别与合成本地接入，可直接对话 |
| DeepSeek 记录导入 | 可把 DeepSeek 的聊天记录导进来 |
| 气泡分段 | 助手回复按段落拆分渲染 |
| 工作区共享存储 | 外部存储挂载到工作区 |
| Vertex Interactions | 新的 Google 接口形态 |
| 安全模式 | 清理超大设置，避免设置撑爆 |
| 连接测试弹窗 | 供应商连通性测试结果集中展示 |
| Phone Use | 跟进上游：不再操作微信 |

合并时保留本 fork 的全部既有能力（F004 审批、F007 reasoning 回传、F009 网页版边界、F012 群组、F013 浏览器、F015 脉搏、F017 工作区、F019 闹钟）。

### 1.2 工作区文件预览（F021）｜ 已完成

- Markdown 复用现有 `MarkdownBlock`，默认渲染并可切回源码；相对图片按文档目录和实际 FILES / LINUX 区域解析，Markdown 外链图片保持原行为，可能联网
- HTML / SVG 通过虚拟 HTTPS 源读取当前文档目录内资源，禁止路径与符号链接越界、file/content 访问及外网加载；HTML 脚本默认关闭，可手动开启
- FILES 超过 1 MiB 时只看源码，仍可编辑保存；LINUX 保留只读及原有 512 KiB 读取保护；SSH 暂不支持本地关联资源预览

### 1.3 DeepSeek 服务端工具与客户端元信息（F022）｜ 已完成

- DeepSeek 方言的 Chat Completions 接入 `web_search` / `read_url` 声明、流式状态、来源、错误与工具签名保存及后续回传；客户端不伪执行服务端工具，F007 的 reasoning 回传守卫保持
- 新增独立的内置“读取网页”开关；Responses / Claude 工具声明按各自协议处理，其他供应商保留原内置搜索行为
- “客户端元信息”为本地选项，不注册成可执行工具：DeepSeek 按每条 user 消息自身日期生成 `latest_reminder`，其他协议进入 system，包含日期、星期、语言和平台
- 新助手与脉搏默认开启元信息；旧配置不静默开启，群组保留自身工具列表
- 未新增 `safety_check`、`title` 或文件输入。本节不涉及上游已合并的 TTS/ASR；真实 DeepSeek 多步搜索、抓取与签名回传仍待账号验证

### 1.4 本地模型 ｜ 已实现，本版不提供入口

> 这一区块**无限期停滞**，具体大规模更新的时间待定。

在手机上跑 GGUF（llama.cpp）这条路走过一遍：代码是完整的，CPU 路径也能正常出字。问题在 GPU——开启后部分机型在提交阶段就被驱动回收（`ErrorDeviceLost`），一个字都产不出来；而只用 CPU 的话速度只有个位数 token/s，离日常可用还差得远。

所以这一版**不开放**：界面不提供任何入口，已绑定到本地模型的助手、群组成员、脉搏会回落到默认模型；已下载的模型文件保留，代码也留在仓库里，等将来再评估。

为什么不删干净：本地模型在设置数据里是一个供应商类型，直接删掉会让老用户的设置文件解析失败，连带丢掉 API Key、对话和记忆。所以只在界面层面收起来，数据层保持原样。

---

## 2. 已排期：第四板块「角色」

现在应用里有三个板块：**工作**（原「聊天」）、**群组**、**脉搏**。计划新增第四个 **角色**，专门承载娱乐向的角色扮演。

顺序调整为：工作 → 角色 → 群组 → 脉搏。

- **角色是独立实体**，不占用助手列表，也不复用助手的设置；助手列表里不会出现角色
- 角色有自己的会话列表；已有助手的会话可以按用户勾选迁移过来，逐个选「复制」还是「移动」
- **工作区从「归属一个模式」改成「勾选」**：同一个工作区可以同时被工作、角色、群组选中；脉搏的工作区固定，不参与
- 角色**不保留按条记忆**，改用文件形式的记忆，另外可以只读引用工作侧的指定记忆
- **兼容酒馆（SillyTavern）角色卡**：导入 PNG/JSON，字段映射、示例对话、世界书、正则、宏引擎
- 针对角色扮演里常见的「AI 味」（例如「我接住了」「我得先明说」这种在演助理而不是在演角色），从提示词、示例对话、深度注入、正则清洗几层一起压

设计已闭合，代码一行没动。开工第一步是先出界面预览确认样子。

---

## 3. 验证状态

| 项 | 状态 |
|---|---|
| 编译 / JVM 单测 | 本机检查通过：1,908 项，1,906 通过、2 项缺语料跳过；包含缓存复用，不是全部重跑 |
| APK 构建 | 维护者自行构建通过：ARM64 / x86_64 / Universal 三个签名 Release；签名、版本、ABI、CRC 核验通过 |
| 三语 key 一致性 | 通过，English / 简中 / 繁中各 2,720 条 |
| 完整 Lint | 未通过，不计入通过项 |
| 真机 / 真实接口 | UI、覆盖安装及 DeepSeek 服务端工具真实调用与签名回传仍待验 |

> 本文件不把编译与 JVM 测试当成真机通过。构建与检查证据见 [DELIVERY.md](./DELIVERY.md)；本次远端文档合并只修正文档，未重新构建 APK。

---

## 4. 下一步

1. 维护者补充真机与真实 DeepSeek 接口验收
2. 维护者推送当前源码并发布 `3.3.0-work.1`
3. 等待角色板块实施指令，先出界面预览

---

## English Summary

**`3.3.0-work.1` (versionCode 207) is in development; the previous `3.2.0-work.3` (206) is the current release.**
Database moves from 33 to 34 and cannot be rolled back.

**Upstream 3.3.0 is already merged** — DeepSeek speech, DeepSeek history import, paragraph-split bubbles,
shared-storage workspaces, Vertex Interactions, a safe mode that trims oversized settings, and a connection-test
dialog. Every existing fork feature is preserved.

The following are also implemented:

- **Workspace file previews (F021)** — rendered Markdown with a source toggle; HTML / SVG resources restricted
  to the document directory, external network access blocked, and HTML scripts disabled by default. Markdown
  external images retain their existing network behavior. FILES saving and read-only LINUX limits remain.
- **DeepSeek server-side tools (F022)** — `web_search` / `read_url`, a separate Read URL switch, streaming
  status, sources, errors, and persisted signatures replayed on subsequent turns.
- **Client context (F022)** — optional date, weekday, language and platform metadata. DeepSeek uses each user
  message's own date; other protocols use system context. Defaults apply to new assistants and Pulse only.

Three signed Release variants (ARM64, x86_64, Universal) have been built by the maintainer. JVM reports contain
1,906 passed tests and two skipped for missing fixtures, including cached results. Full Lint has not passed;
real-service and device checks remain pending. This documentation merge did not rebuild the APKs.

**Scheduled after that:** a fourth section, **Characters** — role-play as a first-class entity, separate from
assistants, with its own conversations, SillyTavern card import, and a shared workspace selected by checkbox
rather than owned by one mode.

**A note on on-device models:** this area is **on indefinite hold**, with no timeline for larger updates.
llama.cpp support was built and does work on CPU, but the GPU backend is not stable on phones
(`ErrorDeviceLost` before the first token), and CPU-only speed is well short of usable. This version therefore
ships **no entry point** for it; downloaded model files are kept and the code stays in the repository for a later
attempt. It is hidden rather than deleted because it is a provider type in the settings data — removing the type
would break deserialization for existing users and lose API keys, conversations and memories.
