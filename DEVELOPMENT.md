# 开发进度 / Development Status

> 最后更新：**2026-11-20**
> 本文件**只记录当前正在开发的版本**：`3.3.0-work.2`（尚未发布）。功能说明见 [README.md](./README.md)，版本变更见 [CHANGES.md](./CHANGES.md)。
> 上一版 `3.2.0-work.3` / 206 已发布，其内容见 Releases 页。

---

## 0. 一句话状态

**上游 3.3.0、F021 工作区文件预览、F022 DeepSeek 服务端工具 / 客户端元信息及 F023 永久移除本地模型已完成。Debug / Release Kotlin 编译和应用 / AI / UI 的 JVM 检查通过，本批 APK 由维护者构建；尚未发布，真实接口和设备交互仍待验。**

- 版本号：`3.3.0-work.2` / versionCode **208**（按维护者本轮指定）
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
- 2026-11-20 后修正：HTML / SVG 恢复改动前的 WebView 预览，删除新增资源拦截与脚本开关，不再映射同目录本地资源；沿用旧脚本行为，不新增网络加载限制
- Markdown 超过 1 MiB 时只看源码；HTML / SVG 不套用该阈值。FILES 仍可编辑保存，LINUX 保留只读及原有 512 KiB 读取保护；SSH 暂不支持本地关联资源预览

### 1.3 DeepSeek 服务端工具与客户端元信息（F022）｜ 已完成

- DeepSeek 方言的 Chat Completions 接入 `web_search` / `read_url` 声明、流式状态、来源、错误与工具签名保存及后续回传；客户端不伪执行服务端工具，F007 的 reasoning 回传守卫保持
- 新增独立的内置“读取网页”开关；Responses / Claude 工具声明按各自协议处理，其他供应商保留原内置搜索行为
- “客户端元信息”为本地选项，不注册成可执行工具：DeepSeek 按每条 user 消息自身日期生成 `latest_reminder`，其他协议进入 system，包含日期、星期、语言和平台
- 新助手与脉搏默认开启元信息；旧配置不静默开启，群组保留自身工具列表
- 未新增 `safety_check`、`title` 或文件输入。本节不涉及上游已合并的 TTS/ASR；真实 DeepSeek 多步搜索、抓取与签名回传仍待账号验证

### 1.4 本地模型（F023）｜ 已永久移除

维护者决定：“安卓跑本地模型太慢，用户体验差；低参数量化模型效果也差。”因此 F020 已从当前源码移除，不再只是隐藏入口。

- 删除本地供应商、推理模块 / 子模块、JNI / Vulkan、加载通知、内存融合 / GPU 开关、提示预算降级和专用界面 / 文案；普通 API 继续使用完整 system、工具和原全量压缩流程
- 旧完整 Settings JSON 和 DataStore 独立 providers 字段共用解码兼容器，只过滤 `local_llamacpp`，保留云端配置和 API Key；其他未知类型和损坏数据仍报错，不重置全部设置
- 全局聊天模型失效时回落到现有第一个模型；助手、组员、脉搏的失效可空引用清除。其他五个非空专用模型槽位不被替换，旧磁盘开关键仅忽略，不主动清盘
- 已下载 GGUF 文件不删除，也不提供清理入口。历史代码恢复、完整文件清单和兼容边界见 [F023 记录](./docs/44-记录-本地模型下线（F023）.md)

旧版源码及交付记录作为历史保留；后续合并上游不得按旧 F020 文档重新接入本地推理。

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
| 本次 F023 编译 | Debug / Release Kotlin 编译通过；先完成本地类型过滤验证，再删除类型和模块 |
| 本次 F023 JVM | 应用 / AI / UI 共 205 套件、1,424 项：1,423 通过、1 项缺语料跳过；AI / UI 在最终调用复用报告，不是全模块重跑 |
| 旧配置兼容 | 5 项专项通过，包含真实 JVM Preferences DataStore 文件的读写 / 重开、云端凭据保留、失效引用清理及旧开关键忽略；不代表 Android 覆盖安装已验 |
| API 行为回归 | 完整 system / 记忆 / 工具提示与 AgentLoop 工具注入回归通过，其他 API 协议及视频测试通过 |
| APK / 源码归档 | F023未执行assemble；随后按维护者要求归档work.2源码，根含DELIVERY、排除个人构建工具。旧版三个Release生成于F023前，不能用于验证本批 |
| 三语 key 一致性 | 通过，English / 简中 / 繁中各 2,616 条；各删除 103 条本地模型专用文案 |
| 完整 Lint | 未通过，不计入通过项 |
| 真机 / 真实接口 | UI、覆盖安装及 DeepSeek 服务端工具真实调用与签名回传仍待验 |

> 本文件不把编译与 JVM 测试当成真机通过。检查命令、日志与报告见 [DELIVERY.md](./DELIVERY.md)；本批未生成 APK，旧配置覆盖安装、各模式云端对话及文件预览仍需维护者装机验收。

---

## 4. 下一步

1. 维护者通过个人构建入口生成 `3.3.0-work.2 / 208` APK，再补充覆盖安装、真机与真实 DeepSeek 接口验收；当前源码按本轮要求独立归档
2. 维护者推送当前源码并发布 `3.3.0-work.2`
3. 等待角色板块实施指令，先出界面预览

---

## English Summary

**`3.3.0-work.2` (versionCode 208) is in development; the previous `3.2.0-work.3` (206) is the current release.**
Database moves from 33 to 34 and cannot be rolled back.

**Upstream 3.3.0 is already merged** — DeepSeek speech, DeepSeek history import, paragraph-split bubbles,
shared-storage workspaces, Vertex Interactions, a safe mode that trims oversized settings, and a connection-test
dialog. Every existing fork feature is preserved.

The following are also implemented:

- **Workspace file previews (F021)** — rendered Markdown with a source toggle, relative images and the existing
  1 MiB limit. HTML / SVG have reverted to the original WebView preview without the added resource interceptor
  or script toggle. Markdown external images retain their existing network behavior. FILES saving and
  read-only LINUX limits remain.
- **DeepSeek server-side tools (F022)** — `web_search` / `read_url`, a separate Read URL switch, streaming
  status, sources, errors, and persisted signatures replayed on subsequent turns.
- **Client context (F022)** — optional date, weekday, language and platform metadata. DeepSeek uses each user
  message's own date; other protocols use system context. Defaults apply to new assistants and Pulse only.

F023 passed Debug / Release Kotlin compilation and application / AI / UI JVM checks: 205 suites,
1,423 passed tests and one skipped for a missing fixture, with AI / UI reports reused in the final invocation.
Five compatibility tests cover legacy provider decoding, real JVM Preferences DataStore read / save / reopen,
cloud credential preservation and invalid model references. No APK was built for F023; the maintainer builds it.
A source ZIP was subsequently requested for manual Git synchronization. Earlier Release APKs predate F023.
Full Lint has not passed; real-service and device
checks remain pending. The three language files now contain 2,616 keys each, after removing 103 per language.

**Scheduled after that:** a fourth section, **Characters** — role-play as a first-class entity, separate from
assistants, with its own conversations, SillyTavern card import, and a shared workspace selected by checkbox
rather than owned by one mode.

**On-device models are permanently removed (F023).** Android inference speed and small quantized model
quality did not meet usability requirements. The provider type, inference module and dedicated UI are removed.
Compatibility decoding filters only the retired provider before sealed-class decoding; cloud configuration and
API keys remain intact. Downloaded GGUF files are untouched. Historical recovery and future merge boundaries
are documented in the F023 record; old F020 integration instructions no longer apply.
