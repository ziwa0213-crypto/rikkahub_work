# Repository Guidelines

## Project Overview

RikkaHub is a native Android LLM chat client that supports switching between different AI providers
for conversations.
Built with Jetpack Compose, Kotlin, and follows Material Design 3 principles.

## Build, Test, and Development Commands

```bash
./gradlew assembleDebug          # 构建 Debug APK
./gradlew test                   # 运行所有模块的 JVM 单元测试
./gradlew lint                   # 运行 Android Lint
```

## Module Structure

- **app**: Main application module with UI, ViewModels, and core logic
- **ai**: AI SDK abstraction layer for different providers (OpenAI, Google, Anthropic)
- **common**: Common utilities and extensions
- **document**: Document parsing module for handling PDF, DOCX, PPTX, and EPUB files
- **highlight**: Code syntax highlighting implementation
- **material3**: Material color utility extensions used by the app UI
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
  update time, pin status, chat suggestions, optional conversation-level system prompt, and prompt injection bindings. (
  app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

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
- 🔴 **versionCode 必须大于已发布的最大值**（当前已发到 **191**，故本次为 **192**；上游源码里写的 `190` 直接拿来用会导致降级拒装）。

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

---

## 五、当前状态

> 最后更新：**2026-09-28**

- **上游基线**：**2.5.5**（2026-09-27 21:44 北京发布）；上一版 2.5.4 我们**未单独出包**，改为两版一次合并
- **已交付功能**：F001 日历删除 / F002 记忆分组 / F003 更新源改造 / F004 工具审批模式 / F005 关于页精简与品牌本地化 / F006 液态玻璃 / F007 思考模式推理回传修复
- **已发布版本**：`2.5.3-work.1`(188) → `.2`(189, 预发布) → `.3`(190) → **`.4`(191，已发布，源码 tag `dd8e25e67` 已同步）**
- **🔴 下一步（按序执行）**：
  1. **合并上游 2.5.4 + 2.5.5（一次做完）** → 🔴 **权威指引**：`发布准备/上游合并指引·2.5.4+2.5.5.md`
     - 真冲突仅 **12 个文件**，其中 3 个需人工判断：`ChatInput.kt` / `PreferencesStore.kt` / `libs.versions.toml`
     - F007 补丁**已实测可干净套到 2.5.5**（`f007_on_2.5.4.patch`，无需重做）
     - ⚠️ **最大风险**：`haze 2.0.0-rc02 → 2.0.0`，影响 F006 的「模糊 / 兼容」两档 → **必须实机看效果**
     - ⚠️ 11 个 F005 已删文件（赞助/QQ/Discord/ja/ko-rKR/ru）上游仍保留 → **合并后必须重新删除**
  2. **实施 F008 加载动画个性化**（在 **2.5.5 基线**上做；坐标未漂移）—— 🔴 **必须同时提供三份文件**：
     `任务书/任务书·F008 加载动画个性化.md`
     + `任务书/任务书·F008 补充·旧加载动画下线与入口迁移.md`（删全局开关 UI + 删兔子）
     + `任务书/任务书·F008 补充二·入口落点与匹配链.md`（🔴 **入口落点修正为 `ProviderConfigure.kt`；匹配改为品牌优先；入口为可折叠行**）
     优先级：**主任务书 < 补充 < 补充二**。只读主任务书会做出「保留兔子 + 落点错误」的实现。
  3. **发 `2.5.5-work.1` / versionCode `192`** —— 发布时**不勾 Pre-release**
- **已知待办**：
  - `CONTRIBUTING.md` 曾被改名为畸形文件名（全角括号、丢扩展名），需按上游删除
  - 三个语言目录（ja / ko-rKR / ru）已删除，上游升级会重新带出，需重删
  - 🆕 上游新增的 `values-ar`（阿拉伯语）**不要删** —— F005 只删日/韩/俄
  - 尚无自有 CI，APK 靠手工出包

---

## 六、文档来源（⚠️ 开发文档**不随本仓库分发**）

**本项目文档由维护者按需提供**，仓库内不保证存在。若你（AI 助手）发现缺少下列任一文档，**请向维护者索取最新版，不要凭猜测动手**。

⚠️ 尤其注意：**任务书会持续修订**（同一份可能已迭代十几版）。**务必确认拿到的是最新版**，旧版可能导致前功尽弃。

| 文档 | 用途 |
|---|---|
| 《RikkaHub 二次开发章程》 | **主文档，动手前必读** |
| 《任务书·F00x …》 | 各功能任务书（独立成篇、自包含、可直接交给 AI 执行） |
| 《CHANGES》 | 用户可见变更记录（AGPL 合规）—— **权威副本在仓库根目录 `CHANGES.md`**，每次发版必须更新 |
| 《FEATURES》 | 功能台账（改动文件 / commit / 回滚）—— **仓库根目录有简版；完整版（含汇总表与「上游升级需重做清单」）由维护者另行提供** |
| 《00-摸底-代码地图与编译环境》 | 代码在哪、怎么编译、有哪些坑 |
| 《02-架构解析-代码结构与工程约定》 | **★★ 改代码前必读** |
| 《上游版本跟踪·\<版本\>》 | 上游发版后看这份：更新了什么、要跟进什么。**当前 = `docs/上游版本跟踪·2.5.5.md`**（覆盖 2.5.4 + 2.5.5） |
| 《上游合并指引·\<版本\>》 | **🔴 合并上游的可执行指引**（含命令与验收）。**当前 = `发布准备/上游合并指引·2.5.4+2.5.5.md`**；旧的 `…·2.5.4.md` 已标「已取代」，但保留着 F007 补丁的设计依据 |
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
| 加载动画（F008） | `app/.../ui/components/ui/RabbitLoading.kt`（🔴 F008 将删除它，改为新建 `LoadingAnimation.kt`） |
| 供应商配置组件（F008 入口落点） | `app/.../ui/pages/setting/components/ProviderConfigure.kt`（**3 个界面共用**） |
| 供应商内置清单 / `builtIn` | `app/.../data/datastore/DefaultProviders.kt` |
| 助手数据模型 | `app/.../data/model/Assistant.kt` |
| 品牌图标识别 / 本地图标 | `app/.../utils/AIIconMatcher.kt`、`app/src/main/assets/icons/` |

<!-- ==== END rikkahub_work fork section ==== -->
