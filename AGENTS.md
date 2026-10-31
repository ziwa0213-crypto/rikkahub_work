# Repository Guidelines

## Project Overview

RikkaHub is a native Android LLM chat client that supports switching between different AI providers
for conversations.
Built with Jetpack Compose, Kotlin, and follows Material 3 Expressive (MD3 Expressive) design language.

## Design Language

The UI follows Material 3 Expressive. New and reworked screens should match it rather than the older baseline
Material 3 look (cards with dividers, plain forms).

- The app theme is `MaterialExpressiveTheme` (app/src/main/java/me/rerere/rikkahub/ui/theme/Theme.kt); prefer the
  expressive variants of components, shapes (`MaterialShapes`), emphasized typography (e.g. `titleSmallEmphasized`)
  and motion from `androidx.compose.material3` over hand-rolled equivalents.
- Settings-style and form pages use segmented groups via `CardGroup` (
  app/src/main/java/me/rerere/rikkahub/ui/components/ui/CardGroup.kt) instead of a card with dividers.
- Favor larger corner radii, filled/tonal containers, and whole-row click targets.

## Build, Test, and Development Commands

```bash
./gradlew assembleDebug          # 构建 Debug APK
./gradlew test                   # 运行所有模块的 JVM 单元测试
./gradlew lint                   # 运行 Android Lint
./gradlew spotlessApply          # 用 ktlint 格式化相对 origin/master 改动过的 Kotlin 文件
./gradlew spotlessCheck          # 只检查格式不改文件
```

## Module Structure

- **app**: Main application module with UI, ViewModels, and core logic
- **ai**: AI SDK abstraction layer for different providers (OpenAI, Google, Anthropic)
- **common**: Common utilities and extensions
- **document**: Document parsing module for handling PDF, DOCX, PPTX, and EPUB files
- **highlight**: Code syntax highlighting implementation
- **material3**: Material color utility extensions used by the app UI
- **ui**: Reusable Compose UI components that do not depend on app logic
- **search**: Search functionality SDK for multiple providers (Exa, Tavily, Zhipu, Bing, Brave, SearXNG, and others)
- **speech**: Speech module for TTS and ASR implementations
- **web**: Embedded web server module that provides Ktor server startup function and hosts static frontend build files (
  built from web-ui/ React project)
- **workspace**: Sandboxed per-workspace file system and shell execution environment exposed to the AI as tools.

## Concepts

- **Assistant**: An assistant configuration with system prompts, model parameters, and conversation isolation. Each
  assistant maintains its own settings including temperature, context size, custom headers, tools, memory options, regex
  transformations, and prompt injections (mode/lorebook). Assistants provide isolated chat environments with specific
  behaviors and capabilities. (app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt)

- **Conversation**: A persistent conversation thread between the user and an assistant. Each conversation maintains a
  list of MessageNodes in a tree structure to support message branching, along with metadata like title, creation time,
  update time, pin status, chat suggestions, optional conversation-level system prompt, and prompt injection bindings.
  Once a conversation is persisted it also holds a `ConversationConfig` snapshot (chat model, reasoning level, search,
  MCP servers, workspace, skills) taken from the assistant; from then on chat-page changes to those settings stay on
  the conversation, and code should read them through `Settings.getAssistantOf(conversation)` /
  `Settings.getChatModelOf(conversation)` instead of the assistant directly. Once loaded, the in-memory
  `ConversationSession` is the single source of truth: change a conversation through `ChatService` (which applies
  `session.update { latest -> ... }` and then `flush()`), never by saving a whole `Conversation` snapshot or one read
  back from the database; deleting also goes through `ChatService` so a running generation cannot re-insert it. (
  app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt,
  app/src/main/java/me/rerere/rikkahub/data/model/ConversationConfig.kt,
  app/src/main/java/me/rerere/rikkahub/service/ConversationSession.kt)

- **UIMessage**: A platform-agnostic message abstraction that encapsulates chat messages with different types of content
  parts (text, images, documents, reasoning, tool calls/results, etc.). Each message has a role (USER, ASSISTANT,
  SYSTEM, TOOL), creation timestamp, model ID, token usage information, and optional annotations. UIMessages support
  streaming updates through chunk merging. (ai/src/main/java/me/rerere/ai/ui/Message.kt)

- **MessageNode**: A container holding one or more UIMessages to implement message branching functionality. Each node
  maintains a list of alternative messages and tracks which message is currently selected (selectIndex). This enables
  users to regenerate responses and switch between different conversation branches, creating a tree-like conversation
  structure. (app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **Message Transformer**: A pipeline mechanism for transforming messages before sending to AI providers (
  InputMessageTransformer) or after receiving responses (OutputMessageTransformer). Transformers can modify message
  content, add metadata, apply templates, handle special tags, convert formats, and perform OCR. Common transformers
  include:
  - TemplateTransformer: Apply Pebble templates to user messages with variables like time/date
  - ThinkTagTransformer: Extract `<think>` tags and convert to reasoning parts
  - RegexOutputTransformer: Apply regex replacements to assistant responses
  - DocumentAsPromptTransformer: Convert document attachments to text prompts
  - Base64ImageToLocalFileTransformer: Convert base64 images to local file references
  - OcrTransformer: Perform OCR on images to extract text

  Output transformers support `visualTransform()` for UI display during streaming and `onGenerationFinish()` for final
  processing after generation completes.
  (app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt)

## Internationalization

- String resources are usually located in `app/src/main/res/values*/strings.xml`; feature modules such as `search`
  may also maintain their own `values*/strings.xml`
- Use `stringResource(R.string.key_name)` in Compose
- Page-specific strings should use page prefix (e.g., `setting_page_`)
- If the user does not explicitly request localization, prioritize implementing functionality without considering
  localization. (e.g `Text("Hello world")`)
- For `locale-tui` operations, use the `locale-tui-localization` skill.

<!-- ============================================================
     ⬇️ 以下为 rikkahub_work（个人 fork）追加的约定，上游原文到此为止。
     合并上游新版本时：若上游更新了上方内容 → 取上游版本；
     下方区块（BEGIN..END 之间）永远保留我们的版本。
     若上游新增了 AGENTS.md 段落 → 追加到 END 标记之后。
     ============================================================ -->

<!-- ==== BEGIN rikkahub_work fork section ==== -->

# RikkaHub Work — 本 fork 的开发约定

> ⚠️ **上方是上游原文，以下才是本 fork 的规则。冲突时以本区块为准。**
> 维护约定：**每次开工前先读本文件**；每次发版后回来更新「当前状态」段。

---

## 一、项目定位（先理解这个，再动代码）

- 这是 **`rikkahub/rikkahub` 的个人非官方 fork**，仓库 `ziwa0213-crypto/rikkahub_work`（公开）。
- 包名 **`me.rerere.rikkahub.plus`**，独立签名，**可与官方版共存安装**。
- 许可证 **AGPL-3.0** → **任何修改版本都必须显著标注改动**。`CHANGES.md` 是**硬性合规要求**，不是可选动作。
- **上游不收新功能 PR** → 定位是**长期自维护 fork**。因此一切改动都要满足三条：
  1. **尽量小**；2. **可回滚**；3. **下次同步上游时容易重新套用**。
- 🔴 **这是人类工程师写的成熟工程**（1384 文件 / 13 模块 / 204 个测试 / Koin 依赖注入 / Room 迁移）。
  **顺着它的设计走** —— 不重写、不"顺手优化"无关代码、不引入与之格格不入的新范式。
  能复用现成设施就复用（例：品牌图标识别已有 `computeAIIconByName()` 覆盖 60+ 品牌，`assets/icons/` 已有 54 个 SVG）。

---

## 二、🔴 铁律（违反其中任何一条 = 事故级后果，动手前逐条自检）

### R1 — 数据模型字段：不可删、不可改名、不可改类型、不可改默认值

涉及 `ProviderSetting` 及其子类、`PreferencesStore` 内的枚举与字段、`Settings` 全部字段。

**原因**：这些用 kotlinx.serialization 存在 DataStore 里。老用户的持久化数据**遇到缺失字段会反序列化失败 → `Settings` 崩溃 → 用户全部配置与 API Key 丢失**。

- ✅ 新增字段 → **必须带默认值**。这是安全的，老数据零迁移。
- ✅ 废弃的枚举值 / 字段 → **保留但 UI 不显示**（例：`BackgroundEffectType.GLASS` 就是这样保留的）。
- ❌ 绝不给既有字段改 `@SerialName`。
- ❌ 绝不把 `Boolean` 字段改成 `enum`（老数据是 `true`/`false`，改成枚举必崩）。
- ⚠️ **不要盲目照抄同文件里的 `@Transient`**：`ProviderSetting` 里 `builtIn` / `description` / `shortDescription` 带 `@Transient` 是**故意不序列化**的；你新加的配置字段**不能**跟着加，否则配置永远不落盘。

### R2 — 不得改动既有工具的参数 / 输出契约

例：`calendar_query` 的输出字段是 `id`，**不许**"顺手统一"成 `event_id`（上游两个工具本就不一致，那是既有事实，不去"修"）。

### R3 — `api.rikka-ai.com` **绝对不能改**

它是 `RikkaHubAPI` 的 **baseUrl（功能性 API）**，**不是**品牌残留。
（`updates.rikka-ai.com` 是更新检查，已由 F003 改造；`docs.rikka-ai.com` 是"官方使用文档"跳转，保留。）

### R4 — 更新检查相关（F003 定型）

- 检查本仓 `github.com/ziwa0213-crypto/rikkahub_work/releases/latest`，**只读** `tag_name` / `body` / `published_at`。
- 点击卡片 → `CustomTabsIntent` 跳**发布页**；**不做应用内下载/安装**；**网络失败必须静默**（不弹错误卡片）。
- 🔴 **`/releases/latest` 只返回非 prerelease 的 Release** → 发布时**绝不勾 Pre-release**（一勾就静默失效 404）。

### R5 — F007 补丁必须保留

`ChatCompletionsAPI.kt` 里 DeepSeek 方言强制回传 `reasoning_content` 的逻辑**是必要的、上游至今未修**（上游 issue #1587 仍 Open）。

- `ProviderSetting.includeHistoryReasoning` 的**字段名与默认值不得改动**。
- `providerSetting.includeHistoryReasoning || (isDeepSeekDialect(host, modelId) && hasTools)` 这个表达式**不得简化为无条件强制**（部分 OpenAI 兼容端点收到 `reasoning_content` 会报错）。
- `hasTools` 的判据**必须与装配 `tools` 的守卫逐字一致**。

### R6 — F004 审批：`ask_user` 的 `needsApproval` 恒为 `true`

`AskUserTool.kt` 的 `execute` 实现**本身就是 `error("...should be handled by HITL flow")`** —— 它根本不走普通执行路径。

- 它靠 `needsApproval = true` 被拦在 `Pending`，再由 UI 的 HITL 流程接管。
- 🔴 **任何审批档位（含"完全允许"）都不得把它改成 `false`** —— 改了就是一问就崩。
- 实现上要有 **HITL 白名单**，优先级高于所有档位。

### R7 — Compose 渲染：不要用 `CompositingStrategy.Offscreen` + `BlendMode.Clear/DstOut`

**上游 2.5.4 修「思考步骤图标黑块」的改动，正是移除 `CompositingStrategy.Offscreen` + `BlendMode.Clear`。** 上游注释原文：

> 离屏层过大时部分设备会退化，节点区域被清成黑块。

- 需要"挖洞/环形遮罩"时 → 用 `Modifier.clip(GenericShape)` 内圈**反向缠绕**（NonZero 规则形成真正的洞）。
- ⚠️ `minSdk = 26`，但 `Modifier.blur` 依赖 `RenderEffect`，**API 31 以下静默失效**。**不要为效果去抬高 minSdk**，接受差异即可。

### R8 — 审批模式的升级方向不可逆

`Partial` 档的合成规则是 **OR（只能升级、不能降级）**：
`tool.needsApproval(args) || requiresApprovalInPartial(name, args)`

某些工具按**参数**决定要不要确认（如 `clipboard_tool` 的 `action`、`memory_tool` 的 `action`），覆盖它会削弱既有安全逻辑。

- **"降级"（让某工具不再确认）必须改在源头**（工具自己的 `needsApproval`），**不能在模式层做**。
- **未登记的工具默认落 L3（需要确认）** —— 漏登记若默认放行 = 静默的安全事故。

### R9 — 仅桌面应用图标与关于页应用标志固定，其余照常同步

- 维护者于2026-10-21澄清：Debug/Release仅固定两处的应用身份图标——桌面/系统显示的应用图标，以及关于页展示的那一个应用标志，均保留最开始的版本。**不是停止同步全部图标或上游更新**；其他图标、界面和功能仍按既有合并范围与fork约定正常同步。
- 不可变图标基准：`643191229dcbeb4972e3772986818605633ace92`中的`app/src/main/res/mipmap-*/ic_launcher*`，共21文件（五种密度各4个PNG及adaptive XML）。已逐文件核实与合并前备份`backup/pre-3.1.0-work1-20261021`（`12a6ed24356889a024eca2190c208294d6a38b65`）一致。
- 合并上游时，仅保留这套应用图标的普通、foreground/background、monochrome及adaptive配置，不只保留其中某个PNG；构建前对照基准逐文件核验。当前`SettingAboutPage.kt`通过`R.mipmap.ic_launcher`读取同一套资源，须保持关于页应用标志与桌面图标一致；若上游改成独立资源，也只固定该应用标志的图像，不冻结整个关于页。
- **继续正常同步**其他图标（供应商品牌、工具、导航等）、加载动画和界面/功能更新；不因本条回退关于页的布局、交互或动画，也不扩大为全局图标资源目录禁更。
- **落实状态（2026-10-23）**：阶段二构建前仅恢复20张PNG，adaptive XML原本未变；21个源文件匹配基准，最终APK的20张PNG像素及adaptive三层引用均已核验。既有v3 Debug未改，不能把该旧包误称为已恢复图标；其他图标/页面不受此回退影响。

---

## 三、版本号规则（权威文件：见「文档来源」中的《版本号规则》）

**命名 = `上游版本号-work.N`**（例：`2.5.5-work.1`）

| 场景 | 做法 |
|---|---|
| 同一上游版本内又发一版 | N 递增（work.1 → work.2） |
| 跟进到新的上游版本 | **N 归 1** |
| 不跟进该上游版本 | **不动 versionName**，仅在发 Release 时递增 |
| 后缀 | 固定 `work`（**不是** `plus`；仓库早已改名 `rikkahub_work`） |

- **`versionCode` 与 versionName 解耦**：它是严格递增计数器，**每次发版 +1**。
  🔴 不要让它"编码" versionName（如 2.5.5 → 250500）—— N 递增时核心号不变，会卡死。
- **实际写在哪**：`app/build.gradle.kts` 第 29–30 行。
- **Release tag** = versionName 且**不带 `v`**（如 `2.5.5-work.1`）。
  🔴 **绝不能拿 versionCode 数字当 tag** —— 比较器把 `192` 解析成 core `[192]`，而 `2.5.5-work.1` 是 `[2,5,5]`，`192 > 2` → **永远误报有更新**。
- 🔴 **tag 的核心号必须大于已装 versionName 的核心号**（例：已装 2.5.5 时，发 `2.5.4-work.5` 永不提示更新）。
- 🔴 **versionCode 必须大于已发布的最大值**（目前已发布到 **196** = `2.5.6-work.3`；`2.5.6-work.4 / 197` 为本地批次，未发布；当前本地目标为 `2.5.6-work.5 / 198`。若 work.5 正式发布，下一正式版至少为 **199**）。

---

## 四、改动纪律

1. **提一个做一个**，需求驱动，不做"顺便的改进"。
2. 每处改动都要能回答：**怎么回滚？下次同步上游时怎么重新套用？**
3. **我们的功能必须同时提供简体中文 / 英文 / 繁体中文三份字符串**
   （上游 AGENTS.md 写的是"未明确要求就不做本地化"——**那条不适用于我们的功能**，我们三个 `strings.xml` 都要改）。
4. **两处台账必须同步更新**：
   - `CHANGES.md` —— **用户可见的行为变更必须写明**（AGPL 合规）。若该版本确实无行为变更，才可写"无"。
   - `FEATURES.md` —— 记录改动文件、commit hash、回滚命令。
5. **同一文件的多处编辑必须串行**（并行编辑会互相覆盖，这是踩过的坑）。
6. 提交前跑 **`./gradlew test`**；改数据模型/持久化时尤其不能跳过。
7. **撤销/删除前列清“要删”和“不得连带删除”**，并核对上下游调用链。删除 F009 供应商跳转链接时，必须保留能力拦截、`ChatError` 构造与发送、`ErrorCardsDisplay` 挂载、`autoDismiss` 字段与判断，以及上游 `CheckFastModelSettings` 分支；不得把“删除入口”理解为“删除报错功能”。

---

## 五、当前状态

> 最后更新：**2026-10-27**（当前合并目标及限制以末尾阶段三②段为准；下方2.5.6及早期合并/发布记录保留作历史）

- **当前开发目标**：`3.1.0-work.1 / 199`，固定上游3.1.0标签的合并尚未提交。阶段二修正和阶段三①组员库保留；阶段三②恢复群组列表、配置及五个子页，群聊界面仍待包③。继续暂时归一为CHAT，到包③解除。每批交付结果以末尾最新段与对应DELIVERY为准；全量App旧测试、玻璃实机和真实登录仍有未验项目。不Release、不commit/tag/push/上传、不操作设备或真实账号。

- **上游基线**：**2.5.6**（2026-10-01 17:55 北京发布）；2.5.4 / 2.5.5 我们**未单独出包**，改为两版一次合并（见 `发布准备/上游合并指引·2.5.4+2.5.5.md`）
- **已交付功能**：F001 日历删除 / F002 记忆分组 / F003 更新源改造 / F004 工具审批模式 / F005 关于页精简与品牌本地化 / F006 液态玻璃 / F007 思考模式推理回传修复 / F008 加载动画个性化 / F009 DeepSeek Web / F010 记忆迁移多选 / F011 随想搜索。F012 一期及二期 A/B/C/D/C+1/E/F、共用 Rootfs 回退及群内昵称/头像已实现；2026-10-14 维护者已发布三个签名 Release。F013/F014、DeepSeek/AnySearch 搜索、后台生成超时与 F013 补充五至七已并入本地 work.5 批次；设备、真实站点和真实搜索验收仍由维护者执行，不能因编译或打包标为实测通过。
- **F006 验证状态**：方案乙修复后，维护者提供的《本次推送清单·2.5.6-work.3》确认2026-10-12实机压测通过：A崩溃回归（进出聊天页/旋转/切换会话各20次）、C性能（帧率/发热/耗电）、D兜底（旧glass配置不崩、默认模糊）。原Phase 0原型复核项已归档；这是维护者反馈，本轮未操作设备，未提供可复用的帧率/功耗原始测量值。
- **已发布版本**：`2.5.3-work.1`(188) → `.2`(189, 预发布) → `.3`(190) → `.4`(191) → `2.5.5-work.1`(192) → `2.5.5-work.2`(193) → `2.5.6-work.1`(194) → `2.5.6-work.2`(195) → **`2.5.6-work.3`(196)**（2026-10-14 10:11北京时间正式发布，GitHub API已核实非草稿、非预发布，三个资产名/大小/SHA-256与本地交付一致）
  · `2.5.6-work.3 / 196` 源码同步记录（历史）：该源码提交以远端master `6b7f8ee7` 为父提交并保留README更新；当前已发布状态仍以此版本为准。
- **当前本地待发布包**：`2.5.6-work.5 / 198`（work.4/197 未发布）。本地三个签名 Release APK 与 1924 文件源码 ZIP 已核验，位于 `artifacts/2.5.6-work.5/F013-browser-cookies-desktop-release-20261020/`；不生成 Debug、不提交、不打标签、不推送或上传 GitHub。公开发布状态仍为 `2.5.6-work.3 / 196`。
- **当前推进**（2026-09-30）：
  1. ✅ **已合并上游 2.5.4 + 2.5.5**，并保留 F007、F005 删除项。后续语言按维护者决定仅英文/简中/繁中，阿拉伯语不保留。详情仍见：`发布准备/上游合并指引·2.5.4+2.5.5.md`
     - 真冲突仅 **12 个文件**，其中 3 个需人工判断：`ChatInput.kt` / `PreferencesStore.kt` / `libs.versions.toml`
     - F007 补丁**已实测可干净套到 2.5.5**（`f007_on_2.5.4.patch`，无需重做）
     - ⚠️ **最大风险**：`haze 2.0.0-rc02 → 2.0.0`，影响 F006 的「模糊 / 兼容」两档 → **必须实机看效果**
     - ⚠️ 11 个 F005 已删文件（赞助/QQ/Discord/ja/ko-rKR/ru）上游仍保留 → **合并后必须重新删除**
  2. ✅ **已在 2.5.5 基线上实施 F008**：按供应商配置、品牌优先匹配、可折叠入口、旧兔子与全局开关 UI 下线。依据：
     `任务书/任务书·F008 加载动画个性化.md`
     + `任务书/任务书·F008 补充·旧加载动画下线与入口迁移.md`（删全局开关 UI + 删兔子）
     + `任务书/任务书·F008 补充二·入口落点与匹配链.md`（🔴 **入口落点修正为 `ProviderConfigure.kt`；匹配改为品牌优先；入口为可折叠行**）
     优先级：**主任务书 < 补充 < 补充二**。只读主任务书会做出「保留兔子 + 落点错误」的实现。
     - ✅ **2026-09-30 已修正并发布**：缺陷（切换模型后动画滞后一轮）随 **`2.5.5-work.2`** 修好
       （commit `a3a004ea2`「fix: refresh loading animation provider on model switch」）。
       依据 **`任务书/任务书·F008 补充三·加载动画刷新时机修正.md`**（🔴🔴 优先级最高）。
       · **根因**：`ChatList.kt` 的 `currentProvider` **优先取历史消息的 `modelId`** → 那是「**上一轮已经回答过的**模型」。
         该动画渲染在 `if (loading)` 分支内，此时本轮回答尚未落库、没有 modelId → 只能从历史取 → 必然滞后一轮。
       · **正确口径（务必逐字照做）**：`assistant.chatModelId ?: settings.chatModelId`，
         **必须与真正发起生成处 `ChatService.kt:596-598` 一致**。
       · ⚠️ **这是 F008 主任务书 §5.4① 自己写错的口径**（原文写 `lastOrNull()?.currentMessage?.modelId ?: assistant?.chatModelId`），
         Codex 是忠实照做的 → **补充三只覆盖主任务书 §5.4①，其余三份不变**。
       · 🔴 **rebase/升级上游时必须保持这个口径**，不要被历史写法带回去。
  3. ✅ **已发布 `2.5.5-work.1`（versionCode 192，2026-09-29 16:49 北京）**，且已远程核验 APK 包内版本、包名与 F001~F008 全部功能标记。
- **已知待办**：
  - ✅ **发版收尾与重新发布 —— 已全部完成（2026-09-30 实测核验通过）**
    · 发布 **`2.5.5-work.2` / versionCode 193**；tag == master HEAD（`08fd0bdbe…`）—— **那个犯了两次的坑没再犯**
    · 旧的错 tag 也一起修了：`tag 2.5.5-work.1` → `1707fe2a0…`（= 192 / `2.5.5-work.1`）
    · 资产名正确（`RikkaHub.Work-*`）；`/releases/latest` → work.2（未勾 Pre-release）
    · `README.md` L3 = `RikkaHub Work`；`CHANGES.md` 资产名描述已更新，且有 `[v2.5.5-work.2]` 段
    · APK 核验：`package=me.rerere.rikkahub.plus` / `193` / `2.5.5-work.2`；`RabbitLoading`=0；R1 字段保留
    · ⏳ 唯一遗留（可选门面）：3 个旧 Release 标题 `Rikkahub Work 2.5.3-work.{1,2,3}` 仍小写 `h`
  - ✅ **F008 补充三（加载动画刷新时机修正）—— 已随 `2.5.5-work.2` 发布**（commit `a3a004ea2`）
  - ✅ **已完成（2026-10-03 至 2026-10-06）：合并上游 2.5.6 + F009 全部实施并推送**
    · **主文档**：`发布准备/上游合并指引·2.5.6.md`（三方比对实测：真冲突 **17** 个）
    · **配套**：`发布准备/给Codex的说明·2.5.6合并与F009.md`
    · **版本号**：**`2.5.6-work.1` / versionCode `194`**（🔴 只需 > 193；跟进 2.5.6 → N 归 1，
      ⚠️ 原计划的 `2.5.5-work.3` 已作废）
    · 🔴 **开工前先提交 F009 的本地改动**（`git checkout -b codex/f009-deepseek-web` 或 `git stash`）——
      F009 代码已写入本地但**未推送**，GitHub master 仍是 `08fd0bdbe…`（不含 F009）
    · 🔴 **F009 受 2.5.6 影响**：`ProviderSetting` 新增了 `customHeaders` 抽象成员 →
      新子类 `DeepSeekWeb` 必须同时实现 `customHeaders` 与 `loadingAnimation`
    · 🔴 **`Export.kt` 被上游改名**为 `ConversationExport.kt`（我们 F005 去水印要重新施加，搜 `rikka-ai.com`）
    · 🔴 **一个特例：`AssistantPage.kt` 故意不取上游** —— 上游 2.5.6 给它加了「复制记忆」UI（43 行），
      我们**保留 2.5.5 原版**（= 不显示该 UI）。理由：该功能将被 F010 取代，先不露头。
      ⚠️ **该文件从此与上游分叉**（已记入「上游升级需重做清单」）→ 详见合并指引 §4.4
    · 其余所有文件遵循「**能取上游就取上游**」
    · 参考：`发布准备/上游合并指引·2.5.4+2.5.5.md`（上一轮，存量参考）
  - 📋 **F009 DeepSeekWeb 内置供应商**（2026-09-30 立项，**已在动工**）
    · **任务书**：`任务书/任务书·F009 DeepSeekWeb内置供应商.md`（40K，自包含）
    · **设计文档**：`docs/11-设计-DeepSeekWeb内置供应商.md`（51K，13 项决策 D1~D13）
    · **视觉稿**：`预览/DeepSeekWeb-供应商界面预览.html`（37K，v2）
    · 🔴 **第 0 期验证**：`第0期验证-DeepSeekWeb可行性.md`（13K，**不随包分发**）
      → 三项：PoW 算对 / SSE 解析 / **全程 OkHttp 能否通**；**不通过就停**
    · **交付包**：`/workspace/Codex任务包-F009-20260930.zip`（3 文件 / 32.8K）
    · **抓仓库（3 个，含原插件）**：本 fork / `rikkahub/rikkahub` / **`cv-superding/dsh-deepseek-web-login`**
    · 量级 **C**（约 1500~2500 行，8 新建 + 约 20 修改）
  - ✅ **F010 记忆迁移多选已实施（2026-10-06）**：复制助手和切换记忆归属共用勾选式选择器；支持复制/移动、全选/反选、共享记忆移动警告；新助手记忆固定为私有
  - ✅ **DeepSeek Web 登录 UI 已修正（2026-10-06）**：登录区域纵向全宽排列，移动端 WebView 对话框适配，数字输入光标保持在末尾
  - 贡献说明临时文件（`CONTRIBUTING（不适用）md`）按维护者决定**不纳入仓库** → 该待办已关闭
  - 项目仅保留三种语言：`values`(en) / `values-zh` / `values-zh-rTW`；上游带来的 `values-ar`、`values-ja`、`values-ko-rKR`、`values-ru` 一律删除，新增文案只落这三份
  - 尚无自有 CI，APK 靠手工出包

---

## 六、文档来源（⚠️ 开发文档**不随本仓库分发**）

**本项目文档由维护者按需提供**，仓库内不保证存在。若你（AI 助手）发现缺少下列任一文档，**请向维护者索取最新版，不要凭猜测动手**。

⚠️ 尤其注意：**任务书会持续修订**（同一份可能已迭代十几版）。**务必确认拿到的是最新版**，旧版可能导致前功尽弃。

| 文档 | 用途 |
|---|---|
| 《RikkaHub 二次开发章程》 | **主文档，动手前必读** |
| 《任务书·F00x …》 | 各功能任务书（独立成篇、自包含、可直接交给 AI 执行） |
| 《任务书·F00x 补充…》 | **同号补充任务书，优先级高于主任务书**（主任务书可能被后续补充推翻）。⚠️ F008 有**三份**补充：`补充`（删兔子+删全局开关）、`补充二`（入口落点+匹配链）、**`补充三`（2026-09-30，加载动画刷新时机修正，最新）** |
| 《CHANGES》 | 用户可见变更记录（AGPL 合规）—— **权威副本在仓库根目录 `CHANGES.md`**，每次发版必须更新 |
| 《FEATURES》 | 功能台账（改动文件 / commit / 回滚）—— **仓库根目录有简版；完整版（含汇总表与「上游升级需重做清单」）由维护者另行提供** |
| 《00-摸底-代码地图与编译环境》 | 代码在哪、怎么编译、有哪些坑 |
| 《02-架构解析-代码结构与工程约定》 | **★★ 改代码前必读** |
| 《上游版本跟踪·\<版本\>》 | 上游发版后看这份：更新了什么、要跟进什么。**当前 = `docs/上游版本跟踪·2.5.5.md`**（覆盖 2.5.4 + 2.5.5）；**2.5.6 的跟踪 = 见 `发布准备/上游合并指引·2.5.6.md` §2** |
| 《上游合并指引·\<版本\>》 | **🔴 合并上游的可执行指引**（含命令与验收）。**当前 = `发布准备/上游合并指引·2.5.6.md`**；上一轮 `…·2.5.4+2.5.5.md` 与更早的 `…·2.5.4.md` 保留作历史参考 |
| 《版本号规则》 | 版本号权威规则 |
| 《F008-品牌加载动画预览.html》 | F008 的**视觉基准**（用浏览器打开；**不打包进 APK**） |

---

## 七、给 AI 助手的协作要求

1. **先读文档，再写代码**。写之前先确认"上游是怎么做的"，优先复用。
2. **执行任务书时逐条对照**，任务书里的"红线/验收"段是硬约束，不是建议。
3. **任务书与源码不符时**：以源码为准，但**必须明确告诉维护者哪里不符**，不要静默按源码改。
4. **不确定就停下来问**，不要猜。猜错在数据模型上是灾难性的（见 R1）。
5. **量级大的改动先做最小原型验证**再全量实现（例：F006 的 Compose 互操作，靠 Phase 0 原型才发现可行方案）。
6. **说"改好了"之前先确认真的重新编译装机了** —— 曾出现过"改了源码但没重出包"导致误判已修复的情况。

---

## 八、关键源码坐标速查

| 主题 | 路径（`app/src/main/java/me/rerere/rikkahub/` 或 `ai/src/main/java/me/rerere/ai/`） |
|---|---|
| 供应商设置模型（R1 重点） | `ai/.../ai/provider/ProviderSetting.kt` |
| 全局偏好（DataStore / 枚举 / R1 重点） | `app/.../data/datastore/PreferencesStore.kt` |
| 工具汇总与审批装配（F004） | `app/.../data/ai/tools/ChatToolFactory.kt` |
| `ask_user` HITL（R6） | `app/.../data/ai/tools/local/AskUserTool.kt` |
| 工具风险分级（F004） | `app/.../data/ai/tools/ToolRiskTiers.kt` |
| Chat Completions 请求装配（F007 / R5） | `ai/.../ai/provider/providers/openai/ChatCompletionsAPI.kt` |
| 更新检查（F003 / R4） | `app/.../utils/UpdateChecker.kt` |
| 聊天输入栏（F004 审批入口 / F006 玻璃） | `app/.../ui/components/ai/ChatInput.kt` |
| 加载动画（F008） | `app/.../ui/components/ui/LoadingAnimation.kt`（解析链 + 动画实现） |
| 加载动画的 provider 口径（F008 补充三） | 🔴 `app/.../ui/pages/chat/ChatList.kt` 的 `currentProvider` —— **必须 = `assistant.chatModelId ?: settings.chatModelId`**，与真正发起生成处 `app/.../service/ChatService.kt:596-598` 一致；❌ 不得用历史消息的 `modelId`（会导致「切模型后动画滞后一轮」） |
| 供应商配置组件（F008 入口落点） | `app/.../ui/pages/setting/components/ProviderConfigure.kt`（**3 个界面共用**） |
| 供应商内置清单 / `builtIn` | `app/.../data/datastore/DefaultProviders.kt` |
| 助手数据模型 | `app/.../data/model/Assistant.kt` |
| 品牌图标识别 / 本地图标 | `app/.../utils/AIIconMatcher.kt`、`app/src/main/assets/icons/` |
| 对话导出（F005 去水印） | 🔴 `app/.../ui/pages/chat/ConversationExport.kt`（**上游 2.5.6 从 `Export.kt` 改名而来**）—— 搜 `rikka-ai.com` 确认水印已删 |
| MCP 工具 schema（上游 2.5.6 重构） | `app/.../data/ai/mcp/McpToolSchema.kt`；`McpOAuthCoordinator.kt`（`localhost` 回调） |
| 图表工具（上游 2.5.6 新增） | `app/.../data/ai/tools/local/ChartDisplayTool.kt` + `app/.../ui/components/charts/` |
| 记忆读取范围（F002） | `app/.../data/repository/MemoryRepository.kt` 的 `scopeOf(assistant)`（写入侧在 `ChatToolFactory.kt`，读取侧在 `ChatService.kt`） |

<!-- ==== END rikkahub_work fork section ==== -->

## F012 第一期补充约定（2026-10-06）

- 基线为 `2.5.6-work.2 / 195`（`f79210c1`），开发目标为 `2.5.6-work.3 / 196`；本轮只生成 Debug，不构建 Release、不推送、不创建标签。
- Debug 仅编译和输出 `arm64-v8a`；不输出 x86_64 或 Universal Debug APK，正式版架构配置保持不变。
- APK、源码归档、校验值和交付说明统一放在仓库根目录 `artifacts/<versionName>/<批次>/`，不直接散放在下载目录。不同批次分目录保留，`artifacts/` 不提交 Git、不纳入源码压缩包。
- 只实施模式切换、组员/群组管理、群组配置和占位聊天页，不接群聊生成、消息归属、存储或记忆机制。
- 新页面复用 `AssistantPage` / `AssistantDetailPage` 骨架与标准组件，颜色使用 `CustomColors` 和 `MaterialTheme.colorScheme`。
- `Settings` 仅追加带默认值的 `uiMode`、`groupMembers`、`groups`，同步 DataStore 读写与无效标签/组员引用清理；不迁移 Room。
- 从助手导入组员是名称/模型/提示词/流式/思考预算的独立快照，不存助手引用。
- 不修改 `Conversation`、`UIMessage`、工具契约、F003/F004/F007/F008/F009 的既有逻辑；群组路径与原聊天路径隔离。
- 只使用英文、简体中文、繁体中文；不添加依赖、硬编码颜色或离屏混合模式。

## F012 修正单②至⑦补充约定（2026-10-07）

- 当前仍为 `2.5.6-work.3 / 196`，以本地未提交工作区为准；仅 ARM64 Debug、完整源码，不构建 Release、不执行 commit/tag/push、不操作设备。
- 原一期中“按创建时间排序”和“只读输入栏”的范围被②至⑦取代：库列表顺序可拖拽并保留，群内 priority 独立；使用真实 ChatInput 但发送只提示并清空，独立 GroupVM 草稿，不接群聊生成。
- Group 的资源设置归属群组、数值参数归属 GroupMember，所有新增字段带默认值；助手快照仍只复制原五项，不复制数值调参。
- 群聊记忆范围必须为 `grp:`，不可与 F010 的 `g:` 或助手范围混用；本期仅在群组设置页管理，生成读取后续接入。
- 复用 MCP/本地工具的临时 Assistant 只当组件参数，绝不能写入 settings.assistants。
- 群组背景用独立 GroupBackground，保留 hazeSource 和同一 LiquidGlassChatHost/熔断路径，不改 AssistantBackground。
- 唯二共享组件例外：AssistantLocalToolContent private -> internal；BackgroundPicker 添加默认 label/description。其余 ChatInput/玻璃宿主/Background/MeshGradient/McpPicker/ExtensionContent/PropertyEditor/CardGroup/ReorderableDrag 不改。
- 群组输入栏展示/调整当前聊天助手模型等是一期已知取舍，必须在 CHANGES 和交付说明记录；实际群聊生成与资源接线仍留第二期。
- 上传文档 AGENTS 中的旧发布状态与阿拉伯语保留描述不覆盖当前工作区约定；本项目始终只保留 values、values-zh、values-zh-rTW。

## F012 修正单⑩补充约定（2026-10-07，优先于上述旧口径）

- 以维护者更正的“思考预算改为群组级并由输入框调节”任务书为准，作废“下移”说法；仍为 `2.5.6-work.3 / 196`，仅 ARM64 Debug 与完整源码，不构建 Release、不提交/推送/打标签、不操作设备。
- 思考等级归属 Group，追加带 AUTO 默认值的 reasoningLevel，复用现有输入框灯泡读写当前群组；不得新增 UI、移动按钮或修改 ReasoningButton/ReasoningPicker。群组模式无条件显示，聊天模式保持原模型能力守卫与助手读写。
- 允许第三处共享组件例外：ChatInput 追加默认 null 的 reasoningLevelOverride / onUpdateReasoningLevelOverride，并仅覆盖灯泡读写。上游升级需同时重套④的 AssistantLocalToolContent 可见性、⑦的 BackgroundPicker 默认文案参数与⑩的 ChatInput 覆盖接口/ChatPage 接线；取代旧“唯二例外/ChatInput 不得改”口径。
- GroupMember 的旧 reasoningLevel 和 importSnapshot 逻辑完全保留，仅加注释；从助手导入的当前有效行为特征只剩 streamOutput，旧思考值保留兼容但不生效，不自动带入 Group。
- 从已知取舍中移除思考等级；模型、工具审批、快捷消息仍沿用当前聊天助手配置，本单不处理。无群组调节时复用创建提示，不修改助手；群聊请求/生成接线留第二期。
- 保留②至⑦的输入栏/独立草稿/玻璃路径、⑧透明顶栏及⑨胶囊同底色；不新增依赖或资源 key，不改 GenerationLoop/ChatService/Room，仍只保留三语。无设备授权时不能以编译或静态核对替代实机截图/录屏验收。

## F012 修正单⑪至⑬补充约定（2026-10-07，优先于上述旧口径）

- 仍为 `2.5.6-work.3 / 196`，仅 ARM64 Debug 与完整源码，不构建 Release、不提交/推送/打标签、不操作设备。**⑧⑨⑩ 已完成并核对通过，本轮只做⑪⑫⑬**，不得回退任何一项。
- **⑪ 群聊列表对齐聊天记录**：抽屉 `GroupChatList` 改为「日期分组头 + 置顶分组头 + 单行胶囊（`RoundedCornerShape(50f)`、`padding(horizontal = 12.dp, vertical = 6.dp)`）+ 长按菜单（置顶/取消置顶、重命名、编辑群组、删除）」，`Group` 追加 `isPinned: Boolean = false`，日期用 `createdAt`。
  🔴 **抽屉排序（`isPinned` 降序 → `createdAt` 降序）只用于渲染，绝不写回 `settings.groups`**；拖拽排序仍归群组设置页 `GroupPage` 独有，抽屉内不提供拖拽。
  分组头**复用而非复制**：`ConversationList.kt` 的 `DateHeaderItem`（:178）、`PinnedHeader`（:199）由 `private` 提为 `internal`，`PinnedHeader` 追加带默认值的 `label` 参数（原调用点零改动）。`ConversationItem` / `ConversationList` / `ConversationListItem` 一律不改。
- **⑫ 群聊顶栏按钮组**：`GroupChatContent.kt` 的 `actions` 改为「`设置(Settings03)` + `新建(MessageAdd01)` + `⋮(MoreVertical)`」，`⋮` 内为「`记忆(Brain02)` → `Screen.GroupMemory` **二级页面**」与「`会话列表(LeftToRightListBullet)` → 占位提示」。仅新增 `group_new_chat` / `group_chat_options` 两个 key；复用 `assistant_page_tab_memory`、`more_options`、`group_next_phase`，**值不得修改**。群组设置页既有记忆入口保留。群为 `null` 时点记忆不得崩溃（须 `group?.let`）。
- **⑬ 修正⑩的两个实机缺陷**（维护者实测）：
  ① `ChatPage.kt` 的 `group?.reasoningLevel ?: ReasoningLevel.AUTO` 回退，导致无群时灯泡仍显示、且值恒回弹「自动」→ 去掉回退值，无群时 `override` 为 `null`；
  ② `ChatInput.kt` 的 `ModelSelectorButton` 在群组模式无条件渲染且读写当前助手模型 → 改为 `if (!groupMode)` 不渲染。
  为此 `ChatInput` 追加 `groupMode: Boolean = false`；`showReasoningButton` **分模式**：`groupMode` 为 `true` 时只看 `reasoningLevelOverride != null`（**完全不再看 model**），`false` 时保持原 `model?.abilities?.contains(ModelAbility.REASONING) == true`。
  搜索、工具审批、快捷消息本轮不动，仍属⑤的已知取舍。
- 🔴 **上游升级需重做清单更新为四处**（**取代旧「唯二例外 / ChatInput 不得改 / 第三处例外」口径**）：
  ① `AssistantLocalToolPage.kt:76` 的 `AssistantLocalToolContent` `private` → `internal`（④）
  ② `BackgroundPicker.kt` 的 `label` / `description` 默认参数（⑦）
  ③ `ChatInput.kt` 的 `reasoningLevelOverride` / `onUpdateReasoningLevelOverride`（⑩）**+ `groupMode`（⑬）+ 灯泡显示条件分模式、模型选择器 `if (!groupMode)` 分支（⑩⑬）**
  ④ `ConversationList.kt:178/199` 的 `DateHeaderItem`、`PinnedHeader` `private` → `internal`（⑪）
  另 `ChatPage.kt` 被⑩与⑬共同修改（`ChatInput(...)` 调用块），属同一范围的需重做项。
- 保留③至⑩的既有成果：独立 `GroupVM` 草稿、输入栏动画与液态玻璃路径、⑧透明顶栏、⑨胶囊同底色、⑩的 `Group.reasoningLevel` 与 `GroupMember.reasoningLevel` 兼容保留。**不回退任何一项。**
- 语言仍只保留 `values` / `values-zh` / `values-zh-rTW`；不新增依赖、不改 `GenerationLoop`/`ChatService`/`Room`/`Settings` 既有字段；群聊生成与记忆读取接线仍留第二期。

## F012 二期 A 当前约定（2026-10-09，优先于上述一期状态）

- 当前开发版 `2.5.6-work.3 / 196`，上游基线 2.5.6；HEAD 为 `f79210c1`，一期修正①至⑬及二期 A 仍未提交。只交付 ARM64 Debug 与完整源码，不构建 Release、不 commit/tag/push、不操作模拟器/设备、不使用真实账号/API 验证。
- 群聊已接入生成与消息持久化，旧段落里的“占位/第二期未接入”是一期历史。只做二期 A：按 enabled 和 priority 严格串行，成员可见前面的发言；不使用 talkativeness/标签策略，不做子群、权重、引用、主动发言、独立状态机或新审批配置。
- 固定占位助手 UUID 为 `8d78b652-5242-4f1b-9c03-a7712328b5c4`，绝不能 random；临时 Assistant 仅作为请求参数，不能写入 settings.assistants。Group 新字段 enableMemory=true、systemPrompt=""、conversationIds=[] 必须保留默认值及序列化。
- 记忆是会话级 `grp:<groupId>:<conversationId>`；旧一参 `grp:<groupId>` 保留，并事务迁移到首个会话，保留 ID。记忆入口在当前会话顶栏，不在群组设置页；工具写入检查 scope，不得修改其他桶。
- 群聊进全库备份，但不进普通列表/置顶/标题或全文搜索/FTS/统计。FTS 采用任务书方案乙，在索引入口跳过，搜索再过滤旧项；统计扩展函数加可选排除参数，不改 Room schema 或旧 DAO 签名。删除群须停止生成并清理关联会话和记忆，复制群不复制 conversationIds。
- GroupSender 保存 memberId/memberName；头像来自组员库，成员已删除时回退头像但保留历史名。请求前缀只通过 GroupSpeakerTransformer 添加，正文/工具参数不改。生成、队列、前台通知、Partial 审批和 ask_user 均复用原设施，GenerationLoop 未修改。
- 上游升级需重套的一期四处组件例外仍有效；二期共享改动的实际名单见 FEATURES 的二期 A 章节，已超过原估算 11 处，不以数字掩盖修改范围。尤其保留 MCP 可选 Assistant 参数、群统计排除、GroupSender 引用分类，以及 ChatInput 群模式隔离；单聊默认参数与行为不变。
- 保留⑧透明顶栏、⑨胶囊同底色、⑩群级思考、⑪列表、⑫顶栏三按钮、⑬隐藏群模型选择。输入栏现隐藏属于单聊的搜索/审批/快捷消息入口，避免误改助手；群聊语音和压缩尚未实现。
- 只保留英文/简中/繁中，不新增依赖、硬编码颜色或离屏混合。台账与交付说明区分自动检查和设备验收，后者未经执行不得标通过。

## F012 二期 B/C/D 当前约定（2026-10-10，优先于上述二期 A 范围）

- 依据维护者授权的 `Codex任务包-F012二期BCD-20261010.zip`，仍保持 `2.5.6-work.3 / 196`，仅 ARM64 Debug 和完整工作区源码；不构建 Release、不 commit/tag/push、不操作设备或真实账号/API。
- 维护者最新指示为“BCD 改完再生成 APK 和源码”，覆盖任务包的中途出包建议。完成 B/C/D 的全部实现和自动检查后，统一交付一批产物，不中途等待设备检查；设备验收由维护者执行，未经实测不能标通过。
- `Group.toolApprovalMode` 默认 Partial、非 Transient；toAssistant 传入群级档位，ChatInput/ChatPage 复用群级思考覆盖参数模式。无群隐藏审批按钮，群模式回调缺失不得退回修改聊天助手。既有 ToolApprovalButton、风险分级、Partial 的 OR 升级与 ask_user 恒确认不变。
- B 完整复制重新生成群/会话/MessageNode 的 ID，保留 UIMessage ID、分支选择、会话标题及时间，物理复制所有分支附件（含工具结果嵌套附件）、本地背景和会话/遗留群记忆。失败清理新群记录、会话、记忆及复制文件，不改源群。copyWithForkedFileUrl 仅 private → internal，函数体不变；它实际是 ChatService 的成员扩展，通过 with(chatService) 调用，不是任务书所写的顶层函数。复用既有 copyMemories，不暴露 DAO；GroupVM 注入 Context，不改基类。
- C 的 groupTags 从空开始，旧群组/组员标签需要重新选择，组员库显示可关闭提示；assistantTags 及聊天助手不变。新建/编辑组员标签必填、群标签非必填。副群主在已启用成员中最后总结一次，用户 @ 仅限定已启用的本群成员；全部提及均无效时回退普通顺序。不引入内部消息、副群主 @ 调度、权重、并行或新状态机。
- D 以滑动引用补充为准：组员气泡向左、用户气泡向右；引用态用按模式/群/会话键控的 remember，非 rememberSaveable。长按组员头像和输入 @ 共用 insertMention，光标在插入文本后。UIAvatar 追加默认 null 的 onLongClick，仅群头像启用同层长按，普通头像保持原 Surface 点击路径，避免任务书外层长按被内部点击吞掉。
- GroupQuote 随消息持久化，在既有内存队列中保留（不新增队列持久化）。可引用清单为当前历史最近十条有正文的用户/组员消息，单成员审批恢复保持同一清单；解析首个现有 Text 的合法标记，只认清单内 ID。无合法 ID 保留原文、不挂伪引用，有合法 ID 则清理识别到的标记并只挂首个合法目标。请求侧引用和说话人前缀合并到首个现有 Text，绝不新增/重排 parts，保留 A 修正二的 reasoning 顺序及自身前缀剥离。ask_user 显示提问组员，不改既有 HITL/审批设施。
- 上游升级除 A/一期例外，还需重套 ChatInput/ChatPage 群审批、引用及补全接线，PreferencesStore 的 groupTags 读写，UIMessageAnnotation 的 GroupQuote，MessageQueue 默认 annotations，ChatInputState/ChatCompletionProvider 提及接口，ChatMessage/ChatMessageTools/UIAvatar 的群模式 UI 分支；具体文件及回滚边界见 FEATURES。
- 只保留 values / values-zh / values-zh-rTW，保留一期修正①至⑬、二期 A 及两份修正成果。上传 AGENTS 的旧版本状态、阿拉伯语描述不能覆盖本地约定；本段合并新增规则，不整文件替换已有 AGENTS。

## F012 BCD 修正 / C+1 / E 当前约定（2026-10-12，优先于上述旧范围）

- 当前仍为 `2.5.6-work.3 / 196`，仅 ARM64 Debug 与完整工作区源码，目录 `artifacts/2.5.6-work.3/F012-BCD-C1-E-debug/`。维护者已授权实施整个新包，统一完成再交付；不 Release/commit/tag/push，不操作设备/模拟器或真实账号/API，不覆盖旧批次。
- 抽屉群选择器与当前群会话列表分工；会话重命名/置顶只改对应列，严禁拿缺 messageNodes 的轻量列表对象 updateConversation。默认新对话自动编号，删除只针对选中群会话；顶栏重复会话弹窗移除。
- C+1 enableDeputyDispatch 默认 false。开时先分派，有效点名按 talkativeness 降序，其他组员保留 priority，最后副群主总结；无有效点名不追加总结，不重复普通成员。用户 @筛选保留；关闭时副群主仍最后普通发言一次。复用 extractMentions，成员名优先标签名，忽略禁用/非本群/自己，dispatch 只一次、SUMMARY 不再解析 @。
- GroupTurn 区分分派/发言/总结，quote 缓存按 turn 下标；审批保留 round 当前 index，重建恢复用 indexOfLast 并参考本轮已保存分派正文，不增加内部消息/role/annotation。任务书追加重复成员的示例由验收顺序覆盖。
- Responses API 保留；DeepSeek capability 禁加密请求、回传已有 reasoning_text，旧加密元数据不阻止明文；OpenAI 加密行为和 F007 原 ChatCompletionsAPI 不改。只密文无明文的真实响应未经联网验证，不宣称已解决服务端所有情形。
- 群压缩只开独立入口，不放开 allowConversationActions。summaryAsText 不改，另加说话人格式化；摘要仍 UIMessage.user，近期 message ID/注解保留。分层默认开：群主/副群主详细、其他成员按名简要并保头尾；独立 groupCompressPrompt 使用现有 {key}，完整 DataStore 读写与默认值兼容。
- 自动压缩提醒默认 true，仅群模式；整轮成功完成且 session 无任务/待审批工具后检查 token 或消息阈值。到阈值先弹窗，确认才调模型，暂不后 20% 迟滞。进度显示与判断共用 ContextUsage，无机器人图标；聊天模式消息计数/压缩行为不变。手动框阈值30%～95%，拖完立即保存，聊天模式隐藏。
- Model 无独立 contextLength 字段：群上限>0 时使用，否则128,000；输入用量先 promptTokens+cachedTokens，无有效真实用量时启发式。压缩后的旧保留消息 usage 失效，通过消息时间边界回退估算。不可把估算当真实完整请求 token，也不改 Room schema。
- 群压缩先加载 getConversationById 完整节点并校验当前会话快照，预留原 session job 防并发保存；任务失败不写摘要。所有新序列化字段均有默认值，三语、旧组件例外和一期/A/B/C/D成果必须保留。自动检查不替代维护者的设备/覆盖安装/供应商验收。

## DeepSeek 修正 / F012 F 当前约定（2026-10-13，优先于上述旧范围）

- 维护者已授权整个 `Codex任务包-20261012-DeepSeek修正与F012F.zip`，并要求“一次性交付”：DeepSeek 修正、F-1、F-2 全部完成后统一交付，覆盖包内分阶段出包要求。保持 `2.5.6-work.3 / 196`、ARM64 Debug、完整工作区源码；目录 `artifacts/2.5.6-work.3/F012-DeepSeek-F-debug/`。不 Release/commit/tag/push，不操作设备或真实账号/API，不覆盖前批。
- DeepSeek 修复只落共享 ChatCompletionsAPI/ResponseAPI。host/model 方言+含工具+思考开启时，续答空/缺 reasoning 也必须回传；显式 `thinking.type` 覆盖能力/等级默认。不对其他厂商、无工具、关闭思考强制补空；保留 F007、OpenAI 密文路径、群前缀 mapIndexed 的原 parts 顺序，不加群模式判定。
- Group 新默认 workspaceId=null、enableRecentChatsReference=true、enableWebSearch=true、enableTimeReminder=false、timeReminderIntervalMinutes=60 全部持久化；toAssistant 传完整群资源配置，两个会话级覆盖开关明确 false。Assistant 属性反射测试必须随上游新增字段更新。群工具不新增白名单，沿原 createTools；审批、ask_user 恒确认及 F009 网页只读限制不变。
- createTools 默认 conversationScopeAssistantId=assistant.id，群显式 null 查询全部单聊；SQL 先排除群占位助手再 LIMIT，既有 FTS 群过滤不回退。群工具和 GenerationLoop 都传会话 cwd；附件菜单绑定/目录和文件补全使用群 workspace，不动单聊助手。群 CWD 只更新对应数据库列，不用轻量会话覆盖消息；切换工作区清理群内会话 CWD。
- WorkspaceEntity owner/shared_rootfs_from 默认 NULL，Room25→26 自动加列且导出26.json；F-2 不再改 schema。旧 owner=null 两边可见，聊天/群组新建分别 chat/group，管理页默认null。删除同步清理助手/群绑定；normalizeGroupData 纯函数，未传有效工作区集合时保留绑定，启动显式传 ID 集清洗。
- WorkspaceManager 上游模块仅加 rootfsOverride 默认null 与 linuxDir 一行，null行为不变。Registry 只拿baseDir不反向注入manager，按 source.id 找 source.root，拒绝失效/自引用/链式/非法路径；每次实际访问前刷新。复用READY独立source，不下载rootfs、不复制环境，文件和PRoot临时目录独立；Python/pip、/tmp、/var/tmp共用，明确展示、不擅自增加/tmp挂载。
- 共享工作区隐藏安装入口且 repository require 拒绝；删群只删自身物理root，绝不沿linuxDir删源。删源事务清空依赖 sharedRootfsFrom、标BROKEN、保留私有文件；不静默创建替代环境。创建/安装/删除及文件/命令操作锁住生命周期，查询/流保持可响应。内置终端也通过manager解析，源失效/重装时关闭相关session；SAF只创建files。启动清理须先刷新registry。
- 新字段/default/schema不能为回滚而删除。未提交时逐文件对照前批源码反向应用当前功能，保留一期/A/B/C/D/C+1/E；schema26数据降级不保证。上游重套清单及已验证/设备待验边界记 FEATURES 与本批 DELIVERY，不能将设备测试编译或本机shell夹具写成Android/供应商实测。

## F012 F-1 归属入口与共享环境补充（2026-10-13，覆盖上述共享限群设计）

- 依据新版“工作区归属入口与文案(1)”补充，仍为 `2.5.6-work.3 / 196`。仅交付 ARM64 Debug 与完整源码，目录 `artifacts/2.5.6-work.3/F012-workspace-owner-debug/`，保留所有前批成果，不 Release/commit/tag/push，不操作设备或真实账号/API。
- 管理页卡片菜单在重命名和删除之间新增设置归属。setOwner 仅更新 owner/updatedAt，不清理群或助手绑定；已绑定且改归属时显示说明。null 的过滤逻辑仍是两边可见，持久化字段与 Room26/schema 不改。
- 管理页新建增加默认未归属的归属选择，以及环境选择。管理页、助手页、聊天/群组 sheet 均默认预选首个 READY 独立源；手动选独立或其他源后不得被列表更新覆盖。无源默认独立。放宽共享限制只删除 require(owner == "group")，保留源存在、非共享、路径与 READY/rootfs 检查。
- 共享实例整张“启用 Shell”卡隐藏；来源/代价移到“工作区信息”的“共用环境”。非共享保留安装/重装/进度及原文案；数据层禁止共享实例独立安装继续有效。
- 新建/归属保存失败保留对话框并展示错误，防重复提交；复用 Select/AlertDialog，不新增依赖。三语“未归属”去括号，新增归属标题、入口、绑定说明与共享信息标题。
- 上游共享需重套：WorkspacePage.kt、WorkspaceVM.kt、WorkspaceDetailPage.kt、WorkspaceRepository.kt；fork 自有 WorkspaceCreateButton.kt 也保留默认源/手动选择保护，三语资源同步。回滚逐文件反向应用本批，保留旧 F012 和 Room26，不整文件 checkout。

## F012 F-1 补充二 当前约定（2026-10-13，群组专属修订版，优先于上述旧范围）

- 以“RikkaHub Work-F-1补充二修订（群组专属）-20261013.zip”的第2版为准，覆盖旧补充一及补充二的全模式安装迁移和共享放宽方案。版本保持 `2.5.6-work.3 / 196`；仅 ARM64 Debug 与完整工作区源码，目录 `artifacts/2.5.6-work.3/F012-workspace-group-only-debug/`，不覆盖前批，不 Release/commit/tag/push，不操作设备或真实账号/API。
- WorkspaceOwnerScope.includes() 保持 `owner == null || owner == value` 原样，未归属仍两边可见。Screen.Workspaces 为 data class、scope 默认 null，FilesPicker 按 groupMode 传 chat/group，GroupConfigPage 传 group，ExtensionsPage 不传；页面过滤但 VM 仍给全量 flow。新建默认归属=scope，全部视图默认 null；名称重复检查、环境来源及来源名称继续使用全量列表。
- 安装入口只对 owner=group 搬迁：详情页整卡不渲染，管理页菜单仅 owner=group 且 sharedRootfsFrom=null 显示安装/重装，顺序重命名→设置归属→安装→删除。聊天/未归属继续详情页安装，WorkspaceDetailVM 原安装状态/方法、进度、对话框与失败弹窗保留。WorkspaceVM 承接群列表的 workspaceId+progress/error，预留单槽防并发、先关对应终端、取消异常重抛、finally 清进度；列表安装期间全部安装项置灰。
- RootfsProgress、InstallRootfsDialog、DEFAULT_ROOTFS_URL 原样搬到 WorkspaceInstall.kt，仅提升 internal，详情和列表共用一份。共用环境信息在详情信息卡；settingsError、兼容模式、审批、文件页、顶栏及下载文案/URL 保持。已有非群共享记录不删除、不重置环境，仍禁止独立安装。
- 共享创建恢复 `require(owner == "group") { "Only group workspaces can share rootfs" }`，只加回这一行，来源存在/非共享/合法路径/READY 校验和 createGroupWorkspaceFor 不改。WorkspaceCreateButton 仅 GROUP 显示/预选复用源；管理页仅归属=群组显示/预选。切回 chat/null 清空源，提交也明确传 null；手动选择后列表更新不覆盖。setOwner 仍只改归属/时间，不解绑、不清共享列；历史共享记录继续读取兼容。
- 不改 WorkspaceOwnerScope、Registry/Manager、owner 类型/default、Room26/schema、版本、DeepSeek/工具审批或三语现有值。本批无新依赖/语言/key；shared_environment 已在前批提供，不重复新增。
- 上游重套本轮 RouteActivity、FilesPicker、GroupConfigPage、ExtensionsPage、WorkspacePage、WorkspaceVM、WorkspaceDetailPage、WorkspaceCreateButton、WorkspaceRepository 的增量，并带新 WorkspaceInstall 与路由/共享测试；WorkspaceDetailVM 与前批完全等价保留。前批 WorkspaceOwnerScope、WorkspaceSelectSheet、AssistantBasicPage 补丁仍保留。对照前批源码逐项反向回滚，不整文件 checkout/reset，不删除持久化字段；自动检查和手机验收分开记录。

## F012 二期-F 回退单 当前约定（2026-10-13，优先于上述所有旧范围）

- 本轮以“RikkaHub Work-回退共用Rootfs与安装入口-20261013.zip”为准。保持 `2.5.6-work.3 / 196`、ARM64 Debug、完整工作区源码；目录 `artifacts/2.5.6-work.3/F012-workspace-rootfs-rollback-debug/`，不覆盖前批。不 Release/commit/tag/push，不操作设备或真实账号/API。
- 共用 Rootfs UI 与机制全部移除：删除 WorkspaceRootfsRegistry、rootfsOverride、注入、共享创建/校验/DAO/终端分支，删除复用选择器、来源说明及三语六个 key。新建均为独立、未安装工作区。删除 WorkspaceSharingTest/SharedRootfsTest，使用 WorkspaceBasicTest 保证基础行为。
- 数据库字段及列保留，WorkspaceEntity 和 Room26/schema 不变。checkIntegrity 开头把历史 sharedRootfsFrom 非 null 的记录清为 null、shellStatus=DISABLED，再读全表做原完整性检查；保留归属、绑定、私有文件和原源环境，需要用户自行重新安装。清理按记录只执行一次，不删除文件。
- 安装统一恢复到详情页：三个组件原样搬回 private 后删除 WorkspaceInstall.kt；所有归属均显示“启用 Shell”。列表移除安装项/进度/对话框与 VM 安装状态。WorkspaceDetailVM 完全不动，settingsError、兼容模式、工具审批、文件页及顶栏保留。
- 保留 WorkspaceOwnerScope.includes()、Screen.Workspaces(scope) 与三个导航点、按来源过滤、设置归属、新建归属及“未归属”去括号。WorkspaceSelectSheet 每行显示“Shell 状态 · 归属”，只复用已有 owner key；绑定不改变归属。
- 源码核实偏差：GroupStorageTest 仍调用删除后的 deleteAndDetachDependents，无法原样保留并编译；只将这一项更新为删除选中工作区不影响其他记录，其余群存储测试和 WorkspaceMigrationTest 不动。生产 sharedRootfsFrom 引用仅保留实体字段及启动清理。
- CHANGES 必须显著记录移除“复用已有 Linux 环境（共用 Rootfs）”；FEATURES 记录文件清单/回退边界。DeepSeek、F009 只读、F004 审批、F012 其他阶段、F006/F008 不改；只保留三语，不新增字符串或依赖。
- 上游重套清单：RouteActivity、WorkspacePage、WorkspaceVM、WorkspaceDetailPage、WorkspaceSelectSheet、FilesPicker、GroupConfigPage、ExtensionsPage、WorkspaceCreateButton、WorkspaceRepository、WorkspaceDAO、WorkspaceTerminalSessionManager、RepositoryModule、workspace/WorkspaceManager。逐项对照前批源码反向应用，不整文件 checkout/reset；已清理的数据不会通过代码回退自动恢复。

## F012 群内昵称与群内头像 当前约定（2026-10-13，与回退单同批交付）

- 本轮还包含“RikkaHub Work-群内昵称与头像-20261013.zip”，与上一回退单一起生成 `2.5.6-work.3 / 196` ARM64 Debug 和完整源码，交付目录沿用 `F012-workspace-rootfs-rollback-debug/`。不 Release/commit/tag/push、不操作设备或真实账号/API。
- GroupMemberRef 仅追加带默认值的 nickname=""、avatar=null；withOrderedMembers trim 昵称，其他字段及默认值不变。GroupMember、GroupVM、PreferencesStore、数据库及 Room26/schema 完全不动。
- GroupChat 的 memberRefOf(Uuid/String)、displayNameOf/displayAvatarOf 解析群内覆盖；CompositionLocal 只承载显示，按当前渲染 mode 提供 group/null。历史气泡不改注解或数据库，实时读取昵称/头像；清空昵称回落当前本名，成员/群引用失效时保留历史名及默认头像。工具提问卡、长按 @、成员列表和输入 @ 候选同步。
- task 源码偏差：chatContent 实为单聊专用，提供点放到 AnimatedContent 的共同 body；withGroupSender 会优先 member.name，因此调用前用临时 member.copy(name=群内名)，函数体/签名不改；resolveDispatch 实际只有一个 extractMentions 调用，连同 speakingMembers 均传别名。生成 framePrompt/通知与 toAssistant 使用群内名，原前缀及 parts 顺序完全不改。
- 编辑头像立即提交并保留昵称草稿，昵称仅保存时 trim 并提交；保存带最新头像避免异步写回旧值，不额外添加头像清除入口。已有 UIAvatar 选择器与保存/移除按钮复用，旧 group_next_phase 保留。
- 三语各新增仅 group_nickname_hint/group_avatar_hint；不建其他语言。GroupMemberProfileTest 检查旧 JSON、群间隔离、清空回落、消息昵称/parts 保留、别名点名与调度、复制/排序/归一化保留。实际点击/截图/覆盖安装仍由维护者验收。
- 上游重套申报：ChatMessage.kt、ChatPage.kt、ChatService.kt；fork 文件 Group.kt、GroupChat.kt、GroupConfigPage.kt、GroupConfigDialogs.kt 和新 GroupChatContext.kt 随源码保留。CHANGES/FEATURES 与交付逐项验收说明同批同步。
- 本批验证：121套件/818项Debug JVM测试全绿，ARM64 Debug及androidTest代码编译通过；新增profile7项、独立工作区6项。仅安装取消夹具使用127.0.0.1，不用外部服务/真实账号。相对前批24改/3增/4删，其他源码哈希一致；版本/包名/ARM64-only/前批Debug签名/WASM均通过。设备交互、覆盖安装与真实PRoot/供应商仍待维护者验收，最终Lint报告随交付。
- 最终Lint为44既有错误/249警告/3提示：错误与前批完全一致，无新增错误；仅group_next_phase按任务书保留变为未使用警告。没有baseline或suppress。完整源码1887文件含子模块385文件，ZIP哈希/CRC/gradlew执行位通过；产物与说明在本批artifacts目录，前批保留。

## 2.5.6-work.3 Release 本地交付（2026-10-14，覆盖历史 Debug-only 打包范围）

> 以下记录本轮最初仅打包的授权与结果；维护者随后已发布APK并授权源码同步，当前范围以末尾“正式发布与源码同步”段为准。

- 维护者明确要求只生成三个 Release 包和完整 GitHub 更新日志，暂不上传源码。允许使用已配置的本地 Release 签名；不生成 Debug、不 commit/tag/push、不建 Release、不操作设备或真实供应商。
- 版本固定 2.5.6-work.3 / 196，Release 包名 me.rerere.rikkahub.plus；沿用已发布 work.2 的 Release 证书。保留 Debug 包名和配置，不拿 Debug 证书给正式版签名。版本无需再次递增；发布成功后下一正式版 versionCode 至少197。
- 三包命名 RikkaHub.Work.2.5.6-work.3-{arm64-v8a,universal,x86_64}-release.apk，目录 artifacts/2.5.6-work.3/F012-release-20261014/。构建用 :app:assembleRelease，不用 buildAll 或 assembleDebug。
- 功能源码与最终回退/群内身份 Debug 批次1887文件核对相同，本次只改发布台账及说明；完整更新日志按work.2到本版最终行为整理，不把历史共享Rootfs或占位功能写成当前能力。
- DeepSeek 双协议推理回传修正已在源码，须如实说明；进一步挂起的排查不新增实现，不声称所有端点问题已实测解决。未经设备/账号验证不得删除待验状态，不把本地出包标为已发布。
- 最终签名、包名/版本、三个ABI、非debuggable、三语、WASM、测试和哈希核验随本批交付，发布日志不使用“见某文件”替代具体改动。上传/标签/远程状态留待维护者另行授权。
- 本轮 `:app:assembleRelease` 成功（11分54秒），Release Lint Vital通过；三个APK已核验196/work.3、包名、各语言应用名、ABI、三语、WASM、ZIP CRC、非debuggable及与work.2一致的V2签名。原Debug APK哈希/时间不变，未新建源码ZIP。
- 工程未注册 `testReleaseUnitTest`；只运行 `testDebugUnitTest`，不打Debug包。功能代码未变，Gradle复用121套件/818项全绿报告（44秒任务检查、全部UP-TO-DATE）；不称为新执行818项或Release变体测试。全仓Lint44既有错误/249警告/3提示仍保留，设备、覆盖安装和真实供应商验收未执行。

## 2.5.6-work.3 正式发布与源码同步（2026-10-14，覆盖历史禁止推送范围）

- 维护者已明确授权补齐发布/F006压测文档、提交完整源码、推送origin/master及将远端2.5.6-work.3标签重指向本次源码提交。保留远端README，不上传开发文档、凭据、keystore、缓存或artifacts，不重复上传APK，不启动F013。
- 版本仍为2.5.6-work.3/196，已公开Release的三个APK哈希与本地一致；错误旧tag指向fedf4d44（195/work.2），纠正时用明确旧值的force-with-lease，只更新该标签。master只允许快进推送，最终tag须与master源码HEAD一致。
- 提交前运行全模块JVM测试；功能代码及Room26 schema须与Release构建基准一致，允许台账及远端README变化。F006实机结论仅同步维护者反馈，不替其他功能填写手机验收通过。下一正式版versionCode至少197。

## F013 / F014 当前约定（2026-10-15，覆盖旧打包范围）

- 维护者要求 `2.5.6-work.4 / 197`，仅 ARM64、x86_64、Universal 三个签名 Release 与完整源码，不生成 Debug、不 commit/tag/push、不操作设备/真实账号。产物放 artifacts/2.5.6-work.4/ 的独立批次目录。
- F013 主任务书与补充一执行；补充二及维护者澄清优先：browser_open=L3_REWRITE，只有它可拉起页面，其他未打开返 NO_BROWSER。助手工具开关追加第九项，GroupLocalToolPage 传 showBrowser=false，ChatService 群轮次排除 browser_ 前缀，不改 Group 数据模型。
- 仅八个固定浏览器工具，无任意 JS/cookie/raw DOM/截图/无头；过滤必须在离开进程前，包括手动投喂、标题/URL/操作标签。密码/隐藏/脚本/表单值不提取，高危 JS+Kotlin 硬拒，无放行开关。只一个可见 WebView、按来源对话隔离，切模式同实例 reload，不清存储。启发式限制诚实提示。
- F014 数据层零改动，复用原 F010 picker/migrateMemories，默认 COPY，空选择不迁移。群目标用两参 scopeOfChatGroup，来源按 scope 去重，一次 DAO.getAllMemories 统计；不改原 picker/selector/VM/DAO/Repository/Room26。
- 仍仅三语、无新依赖、无离屏挖洞；新增单测和全模块 test，Release Lint Vital 与签名/版本/ABI 核验。设备登录态、审批/键盘与迁移交互由维护者测试，不能标为自动验证通过。完整改动与上游重套清单记 CHANGES/FEATURES，保留 Readability Apache-2.0 署名。

## F013 搜索补充与后台修复（2026-10-19，当前批次）

- 当前仍为 2.5.6-work.4 / 197；和 F013/F014 一起交付 ARM64、x86_64、Universal 签名 Release 与完整源码。目录 artifacts/2.5.6-work.4/F013-F014-search-background-release-20261019/。不生成 Debug，不 commit/tag/push/上传，不操作设备或真实账号/API。
- 按 DeepSeek → AnySearch 顺序追加搜索类型、TYPES、getService 和设置编辑器；DEFAULT 仍是 Bing，原20项不重排。两者均为单个类型提供 search/scrape，Key 不进URL或错误，匿名 AnySearch 不带Authorization。新类型所有参数有默认值，serial deepseek/anysearch 不得改。
- 后台只在共享生成漏斗/前台服务/请求日志修复：25分钟 PARTIAL_WAKE_LOCK 随任务续租、最后任务释放；原 Job 的子watchdog用elapsedRealtime作20分钟截止，先addError再cancel。keepAliveInBackground=false、GenerationLoop、provider与搜索timeout/await均不改。
- 日志 durationMs 用uptimeMillis，suspendedMs默认0，两处显示复用三语休眠格式。全模块JVM测试130套件/865项全绿，不替代真实搜索、锁屏、厂商冻结、登录态、覆盖安装和设备交互验收。
- 本地 Release 已构建完成：`:app:assembleRelease` 成功且 Release Lint Vital 通过；三个签名 APK 与 1918 文件源码 ZIP 已在上述 artifacts 目录完成校验。当前仍不提交、不打标签、不推送、不上传。

## F013 补充五至七（2026-10-20，覆盖此前本地打包号）

- 当前本地版本 `2.5.6-work.5 / 198`；`2.5.6-work.4 / 197` 未公开，合并其工作区增量。交付 ARM64、x86_64、Universal 签名 Release 与完整源码，目录 `artifacts/2.5.6-work.5/F013-browser-cookies-desktop-release-20261020/`；不生成 Debug，不提交/打标签/推送/上传，不操作设备、真实账号或外部搜索服务。
- 补充五设置页提供浏览器工具开关、过滤、默认模式与超时；群组浏览器隔离/隐藏规则仍保留，审批风险级不变。
- 补充六只在 Settings 增加带默认值的 `browserVisitedHosts`；仅记录归一化 host，最多 200 条并节流，禁止保留完整 URL。Cookie UI 不显示值；全量确认文案明确网页登录态和 DeepSeek 供应商凭证分离。WebView 无法枚举 Path/Domain，单站清除只承诺尽力而为；不得读取 WebView Cookie 数据库，也不改变 acceptCookie 配置。
- 补充七桌面 viewport JS 仅改 viewport 元素及根元素宽度，桌面模式加载完成后运行并延迟 300ms 补一次，手机模式不注入；不改 CredentialFilter/请求权限。聊天面板折叠为 ≤48dp 右下角球，错误状态点提示，展开布局保留；standalone 不显示。
- 全模块 `test` 与 Release Kotlin 编译已通过（131套件/870项，0失败/错误/跳过）；`:app:assembleRelease` 与 Release Lint Vital 也通过。本机未运行设备/真实站点验收，Cookie 重启持久性与单站实际删除、桌面布局及遮挡仍由维护者验证。资源只允许英文/简中/繁中，不新增依赖。
- 三个签名 Release 与源码 ZIP 已在该目录完成校验；ZIP 含 1924 个文件（包含子模块），APK 为 `me.rerere.rikkahub.plus / 2.5.6-work.5 / 198`，SHA-256 清单与包体验证文件随包保存。Debug APK 未生成/修改。源码目前未提交、无本批 SHA；维持工作区变更。将来上游重套需关注 PreferencesStore 的独立站点记录键、BrowserDataCleaner 与 Cookies 页面、BrowserDesktopViewport、浏览器宿主生命周期和迷你面板布局。回退不能删除已持久化 Settings 字段或覆盖此前 F013/F014、搜索与后台修复。

## 合并上游 3.1.0 冲突修复（2026-10-21，覆盖历史本地版本口径）

- 当前目标 `3.1.0-work.1 / 199`，上游底本固定为 `3.1.0` 标签，包含 `3.0.0` 累计更新。Git merge 仍未提交；不生成中间 APK，不提交/打标签/推送，不操作设备或真实账号。
- 按维护者新提供的冲突修复包应用 28 个文件，移除清单中的 7 个文件和旧 `liquidglass/` 模块；模块备份位于 `artifacts/3.1.0-work.1/conflict-fix-20261021/removed-liquidglass/`。包内漏了 `AssistantPage.kt` / `AssistantBasicPage.kt`，本批只合并其 import、不改页面逻辑。
- 五个文件暂取包内上游占位：`ChatPage` / `ChatInput` / `ChatDrawer` / `FilesPicker` / `AssistantMemoryPage`。群组、浏览器面板、记忆分组/迁移 UI 当前不完整；不能标为阶段一或整版完成，更不能打正式包交付。
- 本轮只做冲突消解、编译与测试定位并完整回传报错，不自行补齐缺失接口或重放五个页面。记忆扩展及 UI 重放等待后续指令。
- 保留旧序列化字段、三语、品牌图标、F007、ask_user 恒确认、空闲 watchdog 与 F009 拒绝链；不得因删旧背景或 sponsor 连带删除这些设施。完整日志及剩余错误归档到本批 artifacts 目录。
- 本批结果：28 文件逐一匹配附件；Git 未解决项和 Kotlin 冲突标记均为0。ai/ui/search/mediagen 编译通过，AI Debug测试44套件/337项通过；App编译失败（918诊断/46文件，含110条语法级联），App测试未执行。占位文件仍残留旧代码，并非干净上游版本；按任务书停在完整报错回传，不以冲突清零冒称已能运行。后续先读本批 BUILD-REPORT.md 与原始日志，再按维护者指令修复。

## 合并上游 3.1.0 冲突修复 v2（2026-10-21，覆盖上一段临时页面状态）

- 当前仍为 `3.1.0-work.1 / 199`，merge未提交。仅应用v2的18个Kotlin文件、可恢复暂存旧UI及编译回传；本轮不出任何APK或源码ZIP，不提交/打标签/推送，不操作设备或真实账号。顶部“不出包”覆盖附件末尾“零错后assembleDebug装机”的条件建议，维护者仍要求最终统一只出一次包。
- ChatDrawer / ChatPage / ChatInput / AssistantDetailVM / SettingPreferencesGeneralPage / AssistantMemoryPage 本次均逐字匹配3.1.0标签，已不再是v1的残缺混合版；18文件全部哈希匹配v2附件。不要把占位页面当成完整fork功能已恢复。
- **19文件只移动、不删除**：17个群组文件移至 `_parked/ui-pages-group/`，保留components/detail层级；两个旧记忆选择器移至 `_parked/ui-components/`。`GroupChatContext.kt` 原地原内容保留。RouteActivity去群组注册、保留Screen.Group*定义，DI去GroupVM；数据/服务/Room不变。以后不得删除_parked，须按文件记忆和新接口重写后恢复。
- 本次App Kotlin编译：**93条带位置诊断/23文件，语法错误0**，较v1的918/46与110条语法诊断明显下降，达到“200以内”目标，**仍未通过编译**。全日志、errors.log、前10文件及所有文件统计在 `artifacts/3.1.0-work.1/conflict-fix-v2-20261021/`；本轮未重跑AI单测，App测试未执行，不以旧测试结果冒称整版验收通过。
- 六个上游页仍需fork类型适配；其他剩余问题包括WebViewState跨模块internal访问、旧组件import、工作区/搜索重复声明、助手页旧记忆类型残留等。本轮按指令只回传，不扩展修复范围。阶段二记忆、阶段三完整UI和最终统一出包仍待指令。
- v2逐文件范围检查确认核心AI/数据/服务/Settings及API配置不变；不得恢复SettingsStore快照update、不得给MemoryRepository补有损row-memory兼容层、不得删改liquidCompatMode/useAppIconStyleLoadingIndicator。保留三语、品牌/F007/F004/F009/watchdog。覆盖与移动前41文件和原index取证在本批before/与before-git-index，禁止用它们整树回滚或覆盖merge/index。

## 合并上游 3.1.0 编译修复 v3（2026-10-21，覆盖历史临时不出包口径）

- 当前仍为 `3.1.0-work.1 / 199`，合并固定在3.1.0标签，不追新master。维护者最新指示“如果出包，就出debug，继续”：仅本轮一次ARM64 Debug、包名`me.rerere.rikkahub.debug`，不Release、不使用Release签名、不新建源码ZIP、不commit/tag/push/上传，不操作设备或真实账号；不能用buildAll。
- v3初次36个Kotlin文件全部匹配附件（22变化/14原本一致），仍剩12条编译诊断；明确告知后追加9文件同范围补漏（旧组件import、formItem、三语目标token资源），最终相对v2变化26个源码/资源文件。精确附件应用和最终补漏核验分开保存，不声称最终36文件仍全部等于原包。
- `:app:compileDebugKotlin`成功（4分33秒，带位置错误0、仍有弃用警告）。AI测试复用通过结果；App测试在`:app:compileDebugUnitTestKotlin`失败，4文件82诊断（MemoryImportSourcesTest/BackgroundEffectTest/GroupTest/GroupConversationListTest），用例未执行。不得删除/停用旧测试或补有损row-memory兼容API来凑绿，待对应功能适配后恢复全量测试。
- **升级重套新增项**：`ui/.../webview/WebView.kt`的WebViewState八个setter公开可写；不要被下次上游覆盖回internal。`:ui/components/`实现不改。旧LIQUID暂使用上游GLASS/Haze，复制助手暂使用上游Boolean文件记忆复制；完整F006/F010及群组/记忆/输入框/浏览器入口尚未恢复。
- v3核验19个parked文件、GroupChatContext、核心AI/数据/服务/Settings/数据库与API配置均较v2未变；F007、F004 HITL/OR、F009拒绝及常驻错误、watchdog/旧偏好字段保留，app/ui/search仅values/values-zh/values-zh-rTW。编译成功不等于启动、保数据迁移或阶段一/二/三验收完成。
- 日志/恢复材料位于`artifacts/3.1.0-work.1/compile-fix-v3-20261021/`；源码本批未提交，逐文件对照before/反向恢复，单独撤补漏用after-package/。不得整树reset/checkout/覆盖index，也不得直接移回parked旧页以消除旧测试报错。全仓既有Settings.kt末尾空行提示与本批范围检查应分开报告。
- 本轮唯一ARM64 Debug已构建并核验（9分31秒，83,563,821 bytes），交付文件为上述目录中的`RikkaHub.Work-3.1.0-work.1-arm64-v8a-debug.apk`；原Debug证书未变、三语/WASM/ZIP/版本/包名/ABI通过，三个旧Release未改。该Debug包与Release独立数据目录，不能把Debug安装成功当成Release升级数据验收；启动和真实DB26→31仍未设备验证。后续不要未经新指令再重复出包。

## 阶段二 F006 / F016（2026-10-23，覆盖上述临时玻璃及记忆UI状态）

- 保持 `3.1.0-work.1 / 199`，按01→02依次实施，01完成已独立编译。当前授权合并两项仅出一次ARM64 Debug到 `artifacts/3.1.0-work.1/stage2-F006-F016-20261021/`；不Release、不新源码ZIP、不提交/标签/上传，不操作设备、模拟器、真实账号。不要因包内旧号改成work.2/200。
- F006恢复qmdeve及隔离宿主，UI仅玻璃/模糊；旧GLASS枚举与liquidCompatMode保留，后者不控制渲染。API<33及熔断回退Haze模糊；采样源、玻璃、输入为兄弟层，群组页仍parked。只保留应用身份图标的原图，不回退其他上游图标。
- F016新增Room32行式表及手写31→32迁移，memory_file schema和原工具契约不变，迁移只改profile的stated标记且保留文件/时间，重复执行不重复。`memory_tool`只写行式；整理器与文件工具只写文件库。整理器必须只读行式最多200条用于判重，不实施可选转录标记。主对话行式最多展示100条不删数据，迁移历史仍可在做梦页查看。
- 以维护者新版§5及直接选择为准：**enableMemory即注册memory_tool，空库也注册**；DeepSeek Web仍拒绝整项，只放行文件read/list并显式拒绝四个文件写入，沿用原无solution链接错误卡片。scope仅复用assistant.memoryId/memoryScope，编辑删除限定scope。两Tab共用分组，关闭记忆禁用内容页但不删除存量。
- App编译通过，AI337项、定向JVM92项均绿；本地真实迁移函数SQLite/JDBC夹具3场景13断言通过，但不是Android/Room验证。全量App测试仍因MemoryImportSourcesTest/GroupTest/GroupConversationListTest编译失败，普通androidTest也有旧接口阻断，均未删除/屏蔽；两份新迁移测试已独立编译，未运行设备。
- CHANGES/FEATURES显著记录本批；最终包体校验与待设备清单放交付说明。19个parked文件不恢复、不改GroupChatContext/群组模型；F010/F014迁移多选、输入框审批/浏览器等第三阶段UI依然待做，不把本批编译或出包称为整版合并完成。
- 本批唯一ARM64 Debug已构建成功（1分53秒，87,868,689 bytes），位于上述目录的`RikkaHub.Work-3.1.0-work.1-arm64-v8a-debug.apk`；199/work.1、debug包名、原Debug证书、三语、ARM64-only、WASM、ZIP CRC及原应用图标均核验。旧v3交付包及16个其他输出APK的哈希/时间不变；不再重复构建，下一步仅维护者设备验收与后续阶段指令。Debug独立数据目录，不能据其安装成功声称Release覆盖升级通过。

## 阶段二遗留三项修正（2026-10-24）

- 保持 `3.1.0-work.1 / 199`，仅补 F016 多选删除与 F009 登录页/JSON 崩溃修复；不改变版本号，不构建 Release，不提交、打标签、推送或上传 GitHub，不操作设备、模拟器、真实账号或联网登录。
- F016 选择态必须由记忆行长按进入；顶栏显示数量并提供全选/取消全选、关闭、批量删除；选中行高亮。删除只调用当前 scope 的 `MemoryEntity` 批量 SQL，不触碰 `memory_file`；普通点击继续编辑。
- F009 登录页必须保留原 `shouldInterceptRequest` 凭证捕获逻辑；仅新增主框架 Loading/Done/Failed、网络/HTTP 错误、重试、代理提示与去 `; wv` UA。DeepSeek PoW/session JSON 只允许安全 `JsonObject` 解包，`JsonNull` 转为可读错误，不回显响应体或凭据；不改请求路径、请求头、PoW 算法和 SSE 解析。
- 资源目录继续只保留 `values`、`values-zh`、`values-zh-rTW`。验证需区分 JVM/编译证据和设备/真实登录证据；未进行设备、WebView 网络、真实账号或快速/思考模式真实对话验收。
- 本批交付前只允许一次 `:app:assembleDebug`，构建输出限定 ARM64；源码 ZIP 排除 `.git`、构建缓存、`artifacts`、本地配置和密钥。不要整树 `reset/checkout`，不要覆盖 Git index，不要删除 `_parked/`。
- 当前验证：主源码编译通过，AI 45套件/347项与App专项9套件/93项全部通过；两份迁移Android测试源码独立编译通过。全量App测试仍停在三个旧测试文件的73条编译诊断，未运行用例。日志保存在 `artifacts/3.1.0-work.1/stage2-F006-F016-fix-20261024/`；不得把专项通过写成全量或设备验收通过。
- 本批唯一ARM64 Debug已完成（20秒，87,868,689 bytes），APK的199/work.1、debug包名、原Debug证书、三语、WASM、CRC与原应用图标已核验；19个其他旧APK和19个parked文件未改，HEAD/MERGE_HEAD/index不变。源码ZIP收尾只更新台账和交付材料，不再重复构建；后续仍需维护者设备验收及第三阶段UI指令。

## 记忆页 UI 回归与玻璃诊断（2026-10-24，覆盖前批已交付状态）

- 本轮仍为 `3.1.0-work.1 / 199`，产品只改 `AssistantMemoryPage.kt`、`SettingPreferencesGeneralPage.kt`及三份strings；保持普通卡片可辨识、列表五行省略、全文编辑与多选删除原逻辑。繁中补13条，诊断三语各加3条，最终每份2020条，既有翻译值不变。
- 玻璃仅加可见状态，不改 `shouldUseNativeLiquidGlass`、`LiquidGlassChatHost`、熔断器或库内渲染，不加重试/开关。active文案依据SDK/配置/熔断判断，不当作设备视觉实测。暂不处理玻璃本身的失败原因。
- 只在新目录 `artifacts/3.1.0-work.1/stage2-memory-ui-glass-diagnostic-20261024/`统一出一次ARM64 Debug与完整源码；此前各包保持原样。不Release、不commit/tag/push/上传、不操作设备/模拟器或真实账号。
- V1～V8/V10须由维护者实机验证；编译和AI347/App专项93的当前结果单独记录，3个旧测试文件不删除/注释/Ignore，不把上批日志当本轮重跑。第三阶段群组、迁移选择器、审批/浏览器入口等仍不在本单范围。
- 本轮App主源码编译通过，AI45套件/347项和App专项9套件/93项均已实际重新执行通过；两份迁移Android测试源码编译通过、未运行Android。全量App本批未复跑，已知阻塞的三份旧测试源码保持不变，不宣称整版验收通过。
- 本轮唯一ARM64 Debug已完成（8秒，89,171,690 bytes），199/work.1、原Debug证书/图标、三语/ABI/WASM/CRC均核验，20个其他旧APK不变。源码ZIP随本批交付，不再重复出包；下一步由维护者完成V1～V8/V10实机验收并回传玻璃诊断文字。

## F006 玻璃底部遮罩修正（2026-10-26，覆盖前批只做诊断的范围）

- 仍保持 `3.1.0-work.1 / 199`，产品只改 `ChatInput.kt` 的遮罩分支及必要import/Insets捕获；`forceLiquidGlass` 为true时仅遮系统导航栏高度，输入框背后不画渐变，false时原渐变逐字保留（仅分支缩进变化）。参数、haze、圆角、尺寸/bounds回调及布局不动。
- 不改 `shouldUseNativeLiquidGlass`、`LiquidGlassChatHost`、`liquidglass/`、熔断器或诊断状态；无新字符串、开关或语言。19个parked及三份旧编译失败测试不删除、注释或Ignore。上游升级需重套 `ChatInput.kt` 的遮罩分支及导航栏Insets捕获，保留非玻璃原渐变。
- 只在 `artifacts/3.1.0-work.1/stage2-glass-scrim-fix-20261026/` 出一次ARM64 Debug与完整源码ZIP；原Debug证书和应用图标保留。不Release、不commit/tag/push/上传，不操作设备/模拟器、真实账号或联网登录；不执行附件中下一轮的tint参数试验。
- 全量App旧测试本轮不复跑/屏蔽，不宣称整版验收完成。V3～V9均需维护者实机验证，V3/V4务必先设背景图或启用渐变；纯色背景细节有限。active诊断仅是配置/SDK/熔断判据，不是宿主挂载或屏幕效果的测量证据。
- 本轮App主源码编译通过，AI45套件/347项与App专项9套件/93项均实际运行通过，0失败/错误/跳过；两份Android迁移测试源码独立编译通过、未运行设备。范围核验仅ChatInput及三份台账变化，其余2097个源码文件、19个parked和Git index/HEAD/MERGE_HEAD保持不变。
- 本轮唯一ARM64 Debug已完成（1分50秒，89,171,690 bytes），版本199/work.1、原Debug证书/图标、三语/ABI/WASM/CRC均通过核验，21个其他既有APK未变。源码ZIP随本批交付，不再重复构建；后续由维护者进行有背景图/渐变条件下的V3～V9设备验收。

## F006 玻璃尺寸与折射参数修正（2026-10-26，承接遮罩修正，覆盖前批“只做遮罩”的范围）

- 版本保持 `3.1.0-work.1 / 199`。本批仅改 `liquidglass/.../LiquidGlassView.java` 与 `LiquidGlassInputBackground.kt`：视图尺寸改变刷新配置，运行时宽/高无效不再写屏幕尺寸；构造期初始化 fallback 保留。宿主 bounds 后刷新配置并记录 `bounds/glass/conf/boundsW/boundsH`；折射高度/偏移按输入栏高度缩放，玻璃档设 blur radius 0（库setter限幅到0.01，shader路径不会应用Gaussian blur）。
- 保持 `ChatInput.kt` 遮罩分支、玻璃判据、三层宿主和GONE策略、熔断、Haze回退、版本、三语、19个parked及旧阻塞测试。日志只在运行设备的下一次绘制前产生；未执行 adb、未获得真实 `conf/glass` 数值。未执行修正单下一轮参数实验。
- 本批唯一ARM64 Debug与完整源码ZIP位于 `artifacts/3.1.0-work.1/stage2-glass-size-refraction-fix-20261026/`；不Release、不commit/tag/push/上传、不操作设备或真实账号。源码与APK校验结果以本目录DELIVERY为准。
- App Kotlin主源码编译通过；AI45套件/347项与App专项9套件/93项通过，均0失败/错误/跳过。唯一ARM64 Debug和完整源码ZIP已核验，详细结果见本批 `DELIVERY.md`。全量App旧测试本批未复跑/屏蔽，三个已知阻塞测试文件保持不变；不宣称整版验收通过。V2～V9仍需维护者实机验证，并记录实际尺寸日志。

## 群组模式残留根因修正（2026-10-27，覆盖旧模式门控）

- 使用维护者重新提供的ZIP，作废的两份独立修正单不再作为执行依据。保持 `3.1.0-work.1 / 199`；仅四个产品文件：ChatPage/SettingPreferencesUIPage去掉模式限制，SettingsNormalization暂时强制CHAT（含阶段三移除TODO），SettingPreferencesGeneralPage复用玻璃判据显示active。
- 从本批起uiMode读盘/更新归一化固定CHAT，群组模式在阶段三恢复。保留Settings字段/枚举/default、群组/组员/标签/工作区绑定数据；不动19个parked、ChatMessage群上下文及死代码组件。ChatInput遮罩、LiquidGlassView尺寸刷新、宿主参数与日志、四项玻璃判据/三层结构/GONE、熔断和Haze回退保持原样。
- 阶段三清单：①删强制CHAT归一化；②按当时设计重新评估页面模式逻辑；③恢复19个parked文件、rememberGroupVM、8条路由、抽屉/内容入口；④重接ModeSwitcher；⑤修复MemoryImportSourcesTest/GroupTest/GroupConversationListTest三份旧测试。本批只记录，不提前恢复。
- 新目录 `artifacts/3.1.0-work.1/stage2-glass-group-mode-root-cause-20261026/`统一交付一次ARM64 Debug及完整源码ZIP，保留原证书/应用身份图标/三语。App编译、AI45套件/347项和App专项12套件/117项均通过；专项含5项模式归一化回归及原设置字段/存储测试。两份迁移Android测试仅源码编译通过。唯一Debug构建48秒成功，89,171,690 bytes，版本/签名/图标/ABI/三语/WASM/CRC核验通过，23个其他旧APK不变。源码逐文件校验随本批交付；不再重复构建。不以设置诊断替代气泡开关交互、bounds/glass/conf运行日志和视觉验证。全量App旧测试本批未复跑/屏蔽，仍有既有阻塞；不Release、不commit/tag/push/上传、不操作设备或真实账号。

## 阶段三① 群组地基（2026-10-27，采用新版任务包及补充一）

- 源码核对补差：ToastType实际位于me.rerere.ui.toast，GroupMemberPage/GroupPage相应更新import；旧NewMemberDialog缺少头像编辑，复用UIAvatar.onUpdate补齐任务书V7。无共享组件改动或新增依赖。
- 保持 `3.1.0-work.1 / 199`。恢复八个基础文件：GroupVM、GroupListLayout、GroupMemberPage、GroupPage、GroupComponents、GroupDialogs、GroupConfigDialogs、GroupBackground；其余十一份parked留待后续。只接GroupMembers路由与设置通用组员入口，GroupPage虽编译但不开放群组/配置；唯一rememberGroupVM复用原定义，DI使用六个原构造参数。
- MemoryRepository补群聊前缀、两个scopeOfChatGroup重载与scopeOf(memoryGroupId)纯函数，严格兼容grp:<gid>、grp:<gid>:<cid>与g:<id>。moveAllRowMemories传ids=null、move=true，不改migrateRowMemories签名。GroupVM两处移动、三处复制、五处删除适配；复制/删除各自同时处理行式与文件式，失败回滚独立尝试两库清理。
- SettingsNormalization强制CHAT及TODO保持一个字不变，包③再解除；不改Settings/Group/GroupMember/GroupMemberRef与Room32，不碰ChatService/ChatToolFactory/ChatDrawer/ChatPage/GroupChatContext或F006。旧三份测试不删、不注释、不Ignore；实际诊断如实报告，不为对齐数量改测试。
- 三语各加唯一组员入口说明key，均2021条；保留原桌面/关于页应用身份图标与Debug证书。仅一次ARM64 Debug与完整源码ZIP，放在artifacts/3.1.0-work.1/stage3-01-group-foundation-20261027/，不Release、不commit/tag/push/上传、不操作设备或真实账号。沿用AI347/App117测试基线并新增群组记忆兼容回归，实际结果与SHA-256详见本批DELIVERY；V3～V12均需维护者实机验证。
- 本批App主源码编译通过，AI45套件/347项与App专项13套件/121项（原117+新增4）全部通过，无失败/错误/跳过；两份Android迁移测试仅源码编译。全量App编译实际剩MemoryImportSourcesTest9条和GroupConversationListTest31条，共两文件40条诊断；GroupTest已无编译诊断，但全量测试尚未执行。不得继续引用此前“三文件73条”作为本轮实测，不为对齐任务书预期制造错误或删测试。
- 本批唯一ARM64 Debug已完成（1分25秒，87,868,689 bytes），原证书/应用身份图标、版本/包名/应用名、三语/ABI/WASM/群组类/CRC核验通过，61个其他历史安装包未变。完整源码与本批DELIVERY/清单一并交付，不再重复构建；Git合并状态和index保持原样。下一步为包②，包③才解除临时CHAT；不得将源码恢复/专项通过宣称为V3～V12实机验收通过。

## 阶段三 · 当前约定（2026-10-28，优先于上述所有阶段三旧口径）

**阶段三 = 把 2.5.6 的群组 / 审批 / 记忆迁移 / 浏览器 UI 在 3.1.0 架构上重建**，分 5 包：

| 包 | 内容 | 状态 |
|---|---|---|
| ① 群组地基 | `MemoryRepository` 3 个 scope 纯函数 + 8 文件归位 + 设置页「组员」 | ✅ 已核对 |
| ② 群组配置页 | 6 文件归位 + `GroupMemoryPage` 重写 + 7 路由 + 设置页「群组设置」 | ✅ 已核对 |
| ③ 群聊 | `Group.toInputAssistant()` + `ChatInput.groupMode` + `ChatPage` 分流 + `GroupChatContent` + 抽屉 `ModeSwitcher` | ✅ 已核对（含 UI 修正 §03） |
| **④ 记忆选择器** | `MemoryPickerDialog` + `MemorySourcePickerDialog` + 3 处接线 + 修 2 份旧测试 + **引用计数 2 行** | ✅ 已完成 |
| ⑤ 样式收尾 | `ChatPage.kt`（已 1,064 行）拆分、余下样式与文案 | ⏳ |

### 本包（④）当前口径

- 基线 **`3.1.0-work.1` / versionCode 199**（Debug，不发布）
- 🔴 **`AssistantMemory` 类型已不存在** → 用 `RowMemory`（行式）/ `MemoryFile`（文件式）。`RouteActivity.Screen.AssistantMemory` 是同名导航路由，别混
- 🔴 **`MemoryDAO` = 文件式，`MemoryEntityDAO` = 行式**（3.1.0 拆成两个 DAO）
- 🔴 `g:` 前缀只能收敛成常量，**产出字符串必须逐字节不变**（否则老分组记忆全部失联）
- 🔴 `memoryIds` / `ids` 空集合 = **一条都不搬**，不等于全选
- 🔴 切归属顺序：**先 `migrateRowMemories`、后切 settings**
- 🔴 文件式记忆只能**整桶**复制/搬（`copyFiles` / `deleteFilesOfAssistant`），不做按条
- **零新增语言 key**（`memory_picker_*` 14 条 + `memory_import_*` 7 条三份全在）
- 不删 / 不注释 / 不 `@Ignore` 任何测试，不改断言

### 3.1.0 迁移后踩过的三个坑（别再犯）

1. **`FormItem` / `SuggestionChip` 等**：3.1.0 把一批组件迁到 `:ui` 模块，原来靠 `rikkahub.ui.components.ui.*` 通配 import 拿到的符号**全部要显式 import**
2. **`ToastType` 在 `me.rerere.ui.toast`**，不是 `com.dokar.sonner`
3. **`ChatInput` 的群聊支持在合并时丢过一次**：`groupMode` / `reasoningAvailable` 两个可选参数必须存在（包③ 已补回）

### 发版

- `3.1.0-work.1` **不单独发布**
- 阶段三 5 包全完成后 → 合并上游 **3.1.1** → 发 **`3.1.1-work.1` / versionCode 200**

## 阶段三② 群组配置页（2026-10-27，承接阶段三①）

- 保持 3.1.0-work.1 / 199；只恢复 GroupConfigPage、GroupCompressionSettings 和五个 detail 页，共七个文件，其中 GroupMemoryPage 按 F016 行式接口重写。余下 GroupChatContent、GroupDrawer、MemoryPickerDialog、MemorySourcePickerDialog 四份 parked 保留。
- 新增七条 Nav3 entry，复用 key 参数，不改 Screen 定义；设置通用追加群组设置，HugeIcons.UserMultiple 已在本地依赖核实存在。三语各新增唯一 setting_page_groups_desc，均2022条，无其他语言或依赖。
- ItemizedMemoryTab 只改 private → internal，函数体不动；原助手页的多选工具栏不在 Tab 中，因此新群组页单独补齐全选/取消、返回清选与批量删除，确认正文复用 assistant_page_memory_delete_selected_confirm。群会话校验及 scopeOfChatGroup 两参隔离保留，不做导入、不改文件记忆。
- ToastType、SkillManager/SkillMetadata、FormItem 和 OutlinedNumberInput 均按共享模块实际路径导入；扩展使用三个 extension_selector_tab_*。首轮编译的数值输入错误是 import 偏移，只适配恢复文件，不改组件实现。
- Settings/Group/GroupMember/GroupMemberRef/Room32、SettingsNormalization 的 CHAT 和TODO、ChatPage/ChatService/ChatToolFactory/ChatDrawer/GroupChatContext、F006/图标/Debug证书及三份旧测试保持本批前状态。校验受保护文件用 before 哈希，不能把已存在的merge变更误判为本批改动；DAO合法旧方法名不删。
- 只在 artifacts/3.1.0-work.1/stage3-02-group-config/ 统一出一次ARM64 Debug及完整源码ZIP；ZIP包含APK/DELIVERY/APK哈希，显式UTF-8文件名并保留软链。源码ZIP最终哈希在包外记录，不循环嵌入。不Release、不commit/tag/push/上传，不操作设备或真实账号。V13～V22仍需维护者实机验证；“开始聊天”本批仅选择群和返回，群聊及模式切换到包③恢复。

## 阶段三④记忆选择器交付（2026-10-28）

- 保持 `3.1.0-work.1 / 199`，仅交付 ARM64 Debug APK 和完整源码 ZIP；APK 单独放在本批 artifact 目录，源码 ZIP 根目录包含 `DELIVERY.md`，不把 APK 塞进源码 ZIP。
- 恢复行式记忆多选与导入 UI，接入助手复制、记忆归属切换、助手记忆页和群聊会话记忆页；助手复制仍强制私有桶，空选择不复制条目。Markdown 文件记忆只整桶复制/迁移。
- 群组旧桶迁移同时搬行式记忆和 Markdown 文件；`g:` scope 值不变。引用胶囊只在含 `UrlCitation` 时显示，数量也只统计 URL 引用。
- 测试适配：`autoConsolidateMemory` 不是 Group/GroupMember 配置项，按默认值策略列为 intentionally unset；旧群桶整桶迁移测试按本包新增的“文件随行记忆迁移”语义更新对应断言。其余测试断言不改。
- 设备交互与视觉验收由维护者安装本批 APK 后完成；不 Release、不 commit/tag/push/上传，不操作设备或真实账号。语言资源只保留 `values`、`values-zh`、`values-zh-rTW`。

## 当前批次（2026-10-30）

**阶段三⑥三修正单：启动竞态与标准开关回滚**

| 项 | 内容 |
|---|---|
| **设置占位保护** | `RouteActivity.AppRoutes()` 在 `settings.init` 时提前返回，不能在 `Settings.dummy()` 阶段创建起始页面或导航栈；真实设置加载后才按 `uiMode` 选择聊天/群组入口。 |
| **会话占位保护** | `ChatVM` 的 `conversationLoaded` 只有 `initializeConversation` 完成（包括异常 finally）后才为 true；`ChatPage` 仅在 `conversationLoaded && !setting.init` 时按真实会话类型同步 `uiMode`。 |
| **标准开关** | 恢复 `CardGroupScope.switchItem` 的 `colors` 参数和设置页 `switchItem(colors = itemColors)` 调用；不要把有效的统一行改回裸 `Switch`。 |

- 红线：占位设置期间不写任何设置；未初始化会话期间不使用 `assistantId` / `isGroupChat()` 回写模式；不恢复无条件 `UiMode` 常量写入。
- 版本保持 `3.1.0-work.1 / 199`，数据库仍为 32；本批不新增依赖、语言 key 或 schema，不提交/tag/push/发布，不操作设备或真实账号。
- `:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleDebug` 已通过；本批 APK、源码 ZIP 和 SHA-256 记录追加到根 `DELIVERY.md`。

## 前一批（2026-10-30）

**阶段三⑥二修正单：模式与会话对齐、DeepSeek UA 恢复与死参数清理**

| 项 | 内容 |
|---|---|
| **模式与会话对齐** | `RouteActivity` 群组模式冷启动只复用默认组最后一个群聊会话；无可复用会话进入 `Screen.Groups`，不得创建空群聊。`ChatPage` 只在目标类型变化时按 `conversation.isGroupChat()` 同步 `Settings.uiMode`，不得写回无条件常量。 |
| **DeepSeek UA** | 恢复 `settings.userAgentString` 对 `; wv` 与 `Version/4.0 ` 的清洗，同时记录 `UA raw` / `UA effective` / WebView 包；输入修复脚本、请求拦截和 token 解析不变。诊断条移至顶部。 |
| **CardGroup** | 删除 `CardGroupScope.switchItem` 的 `colors` 参数；实际需要自定义 `ListItemColors` 的内置工具行直接使用 `item`，保持原颜色。 |

- 版本保持 `3.1.0-work.1 / 199`，数据库仍为 32；三语资源仍仅 `values` / `values-zh` / `values-zh-rTW`，各 2,025 条。
- 本批不新增依赖、不改 schema、不提交、不打 tag、不推送、不发布、不操作设备或真实账号；只生成 ARM64 Debug APK 和不含 APK 的完整源码 ZIP。
- `:app:compileDebugKotlin` 已通过；测试、APK 和源码包的最终校验记录追加到本批 `DELIVERY.md`。

## 前一批（2026-10-29）

**阶段三⑥修正单：模式持久化、玻璃崩溃、DeepSeek 登录诊断与 UI 一致性审计**

| 项 | 内容 |
|---|---|
| **P0-1 模式持久化** | 删除按会话类型强制写回 `uiMode` 的路径；抽屉读取 `settings.uiMode`；保留 `ChatDrawer.kt` 中用户主动切换模式的写入；`GroupVM.kt` 改用 `state.group == null` 作为自动压缩提示判据 |
| **P0-2 玻璃崩溃** | `LiquidGlassView.updateConfig()` 捕获当前 `LiquidGlass` 实例，并在延迟回调中校验实例身份；不得改 `ensureGlass`、`removeGlass` 或渲染管线 |
| **P0-3 DeepSeek 登录白屏** | 只加 UA/WebView 包、控制台、页面完成探针和错误日志，以及白屏诊断条与系统浏览器出口；前一批保持原始 UA，后一批已恢复 UA 后处理；不拦请求、不改输入修复脚本 |
| **P1 UI 审计** | 只做有把握的行图标、分组标题和标准卡片 `switchItem` 统一；纯说明行、对话框/定制布局中的裸 `Switch` 记入 `DELIVERY.md` 存疑清单，不为追审计数字擅自改动 |

- 版本保持 `3.1.0-work.1 / 199`，数据库仍为 32；三语资源仍仅 `values` / `values-zh` / `values-zh-rTW`，各 2,025 条。
- `:app:compileDebugKotlin` 与 `:app:testDebugUnitTest` 已通过；本批再执行一次 `:app:assembleDebug`。只交付 ARM64 Debug APK 和不含 APK 的完整源码 ZIP。
- 不提交、不打 tag、不推送、不发布、不操作设备或真实账号。设备交互、DeepSeek 登录白屏诊断、系统浏览器出口及视觉一致性由维护者安装 APK 后验证。
- `liquidglass/.../LiquidGlassView.java` 的 fork 补丁已登记到 `FEATURES.md` 的上游升级需重做清单；仓库没有 `DEVELOPMENT.md`，不创建替代文件。
