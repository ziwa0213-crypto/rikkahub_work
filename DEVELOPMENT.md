# 开发进度 / Development Status

> 最后更新：**2026-11-20**
> 本文件**只记录当前正在开发的版本**：`3.3.0-work.1`（尚未发布）。功能说明见 [README.md](./README.md)，版本变更见 [CHANGES.md](./CHANGES.md)。
> 上一版 `3.2.0-work.3` / 206 已发布，其内容见 Releases 页。

---

## 0. 一句话状态

**上游 3.3.0 已合并完毕；这一版还要加两件东西——工作区文件预览、DeepSeek 服务端搜索——做完一起发版。**

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

### 1.2 工作区文件预览 ｜ 未开始

现在工作区里点开文件，Markdown 和 HTML 都按纯文本显示。这一版改成：

- Markdown 走 BetterText 渲染，标题、列表、代码块、表格正常显示
- HTML 走工作区文件拦截器，脚本默认关闭

### 1.3 DeepSeek 服务端搜索 ｜ 未开始

DeepSeek 官方在 Chat Completions 里提供服务器端执行的 `web_search` 与 `read_url`：请求里声明要用，服务端自己搜索、自己读页面，把结果和引用一起返回，客户端不需要多跑一轮工具。

我们缺的只是「怎么发、怎么解析、下一轮怎么原样回传」——消息模型、引用卡片、Claude 通道的同类解析都已经有了。**不加新开关**，复用模型自带的「联网搜索」与「URL 上下文」。

`safety_check`、`title`、TTS/ASR 不做；文件输入待定。

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
| 编译 / JVM 单测 | 通过（Codex 本机执行） |
| APK 构建 | 通过，ARM64 签名 Release |
| 三语 key 一致性 | 通过 |
| 真机 | 上游合并后的包尚未系统性跑真机；发版前需补一轮 |

> 本文件不把编译与 JVM 测试当成真机通过。

---

## 4. 下一步

1. 做工作区文件预览
2. 做 DeepSeek 服务端搜索
3. 发 `3.3.0-work.1`
4. 开工角色板块（先出界面预览）

---

## English Summary

**`3.3.0-work.1` (versionCode 207) is in development; the previous `3.2.0-work.3` (206) is the current release.**
Database moves from 33 to 34 and cannot be rolled back.

**Upstream 3.3.0 is already merged** — DeepSeek speech, DeepSeek history import, paragraph-split bubbles,
shared-storage workspaces, Vertex Interactions, a safe mode that trims oversized settings, and a connection-test
dialog. Every existing fork feature is preserved.

Two more pieces are planned for this version:

- **Workspace file preview** — Markdown rendered instead of plain text, and HTML served through a workspace
  interceptor with scripts disabled by default.
- **DeepSeek server-side search** — declare `web_search` / `read_url` on the request and the API runs them,
  returning results and citations in the same turn. The message model, citation cards and the equivalent Claude
  parsing already exist; only the request, parsing and replay paths are new. No new toggle: it reuses the
  model's built-in "web search" and "URL context" switches.

**Scheduled after that:** a fourth section, **Characters** — role-play as a first-class entity, separate from
assistants, with its own conversations, SillyTavern card import, and a shared workspace selected by checkbox
rather than owned by one mode.

**A note on on-device models:** this area is **on indefinite hold**, with no timeline for larger updates.
llama.cpp support was built and does work on CPU, but the GPU backend is not stable on phones
(`ErrorDeviceLost` before the first token), and CPU-only speed is well short of usable. This version therefore
ships **no entry point** for it; downloaded model files are kept and the code stays in the repository for a later
attempt. It is hidden rather than deleted because it is a provider type in the settings data — removing the type
would break deserialization for existing users and lose API keys, conversations and memories.
