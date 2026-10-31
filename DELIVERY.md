# 阶段三⑥交付记录

# 阶段三⑥三交付记录

日期：2026-10-30（Asia/Shanghai）  
版本：`3.1.0-work.1` / `versionCode 199`  
基线：阶段三⑥二交付；分支 `merge/upstream-2.5.6-f009`，HEAD `12a6ed24`（本批未提交）  
交付范围：ARM64 Debug APK + 不含 APK 的完整源码 ZIP；未发布、未推送、未打 tag、未操作设备或真实账号。

## 本批改动

| 文件 | 改动 | 理由 |
|---|---|---|
| `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | `AppRoutes()` 在 `settings.init` 占位设置期间只渲染空容器并提前返回；真实设置加载后才创建起始页和 `rememberNavBackStack` | 防止首帧读取 `Settings.dummy()` 的默认 `CHAT`，把已持久化的 `GROUP` 模式固定成聊天入口 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt` | 新增 `conversationLoaded: StateFlow<Boolean>`；会话初始化用 `try/finally` 标记完成 | 区分占位会话与真实加载完成的会话，即使初始化异常也不让状态永久卡住 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` | 模式对齐 effect 增加 `conversationLoaded` 和 `setting.init` 守卫 | 防止占位会话的当前助手被误判为普通聊天，从而覆盖持久化群组模式 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ui/CardGroup.kt` | 恢复 `switchItem` 的 `colors: ListItemColors?` 形参和向 `item` 的转发 | 该参数存在真实调用，上一批删除是错误判断 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt` | 恢复 `switchItem(... colors = itemColors, ...)`，移除本批临时裸 `Switch` 适配 | 保持设置页标准开关行的一致交互与视觉样式 |

## 红线核对

- `settings.init == true` 时不执行设置写入，也不创建导航栈；`Settings.init`、`Settings.uiMode` 类型/默认值/序列化字段未改。
- `conversationLoaded == false` 时不使用 `conversation.isGroupChat()` 做模式写回；仍无无条件 `UiMode.CHAT` / `UiMode.GROUP` 常量写入。
- 群组模式冷启动仍不创建空会话；没有可复用会话时进入 `Screen.Groups`。
- 未改 `ChatService`、`ChatToolFactory`、生成轮次逻辑、数据库、schema、依赖、语言 key 或版本号。
- 三语资源仍只有 `values`、`values-zh`、`values-zh-rTW`，每份 2,025 条；颜色 539、字号 37。

## 构建与测试

- `./gradlew :app:compileDebugKotlin --console=plain --no-configuration-cache`：通过；仅保留既有弃用和实验 API 警告。
- `./gradlew :app:testDebugUnitTest --console=plain --no-configuration-cache`：`BUILD SUCCESSFUL`；92 个测试套件、659 个用例，0 失败、0 错误、0 跳过。
- `./gradlew :app:assembleDebug --console=plain --no-configuration-cache`：`BUILD SUCCESSFUL`，24 秒；ARM64 Debug APK 已重新生成。
- APK：`artifacts/3.1.0-work.1/stage3-06-03-startup-race-20261030/RikkaHub.Work-3.1.0-work.1-stage3-06-03-startup-race-20261030-arm64-v8a-debug.apk`。
- APK 核验：applicationId `me.rerere.rikkahub.debug`；versionName `3.1.0-work.1`；versionCode `199`；仅 `arm64-v8a`；APK Signature Scheme v2 和 ZIP 完整性通过；大小 `87,868,689` 字节；SHA-256 `e59a5912f584868a241a6e19b866d6aa5d4efca3ec0710481fcbb62030f87612`。

## 源码包

- 源码 ZIP：`artifacts/3.1.0-work.1/stage3-06-03-startup-race-20261030/RikkaHub.Work-3.1.0-work.1-stage3-06-03-startup-race-20261030-source.zip`。
- ZIP 不含 APK、`artifacts/`、`build/`、本地配置、签名文件或密钥；含根 `DELIVERY.md`，保留 `.claude/skills` 与 `web-ui/AGENTS.md` 两个 Unix 符号链接，并设置 UTF-8 文件名标志。
- 本轮未执行 `git commit`、`git tag`、`git push`、GitHub 发布、ADB 安装或模拟器/真机操作。M1-M7 连续冷启动、群组模式持久化和消息归属仍待维护者安装 APK 后验证。

# 阶段三⑥二交付记录

日期：2026-10-30（Asia/Shanghai）  
版本：`3.1.0-work.1` / `versionCode 199`  
基线：阶段三⑥交付；分支 `merge/upstream-2.5.6-f009`，HEAD `12a6ed24`（本批未提交）  
交付范围：ARM64 Debug APK + 不含 APK 的完整源码 ZIP；未发布、未推送、未打 tag、未操作设备或真实账号。

## 本批改动

本修正单要求约 4 个文件，但当前源码实际存在一处 `switchItem(colors = ...)` 调用；为删除共享组件死参数而保持该调用的颜色效果，实际改动 5 个产品文件。

| 文件 | 改动 | 理由 |
|---|---|---|
| `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | 群组模式冷启动时取默认组最后一个群聊会话；无组或无会话时进入 `Screen.Groups`；聊天模式保留 `create_new_conversation_on_start` 原逻辑 | 模式决定冷启动入口，且群组模式不得自动创建空群聊 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` | 在渲染分流前增加幂等 `LaunchedEffect`，按 `conversation.isGroupChat()` 将 `Settings.uiMode` 同步为 `GROUP` 或 `CHAT` | 打开列表、通知、搜索、收藏或深链会话时，抽屉模式与实际会话类型一致 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/DeepSeekWebLogin.kt` | 恢复去除 `; wv` 与 `Version/4.0 ` 的 UA 后处理；同时记录 `UA raw`、`UA effective` 和 WebView 包；诊断条由底部移到顶部 | 恢复基线浏览器 UA，保留可比较的诊断数据并避免遮挡登录页底部 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ui/CardGroup.kt` | 删除 `CardGroupScope.switchItem` 的 `colors` 形参及转发 | 清理未由该共享开关 API 承担的死参数 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt` | 内置工具开关行改用 `item(colors = ...) + Switch` | 当前代码该处确实需要自定义背景色，保留原视觉而不复活共享死参数 |

## 红线核对

- `ChatPage` 没有无条件写入 `UiMode.CHAT` 或 `UiMode.GROUP`；只按当前会话类型且仅在不一致时同步。
- 群组模式冷启动不调用会话创建逻辑；没有可复用群聊时只进入群组列表。
- `Settings.uiMode` 类型、默认值、序列化字段和 `UI_MODE` key 未改；`ChatVM.updateSettings`、`GroupVM` 相关签名未改。
- 未在生成链路、`ChatService` 或 `ChatToolFactory` 增加模式判断；未新增依赖、语言 key、数据库字段、schema 或版本号。
- DeepSeek 的输入修复脚本、请求拦截和 token 解析未改；本批明确恢复了 UA 后处理，纠正上一批仅保留原始 UA 的记录。
- 语言目录仍只有 `values`、`values-zh`、`values-zh-rTW`，每份 2,025 条。

## 构建与测试

- `./gradlew :app:compileDebugKotlin --console=plain`：通过；仅保留既有弃用和实验 API 警告。
- `./gradlew :app:testDebugUnitTest --console=plain`：`BUILD SUCCESSFUL`；92 个测试套件、659 个用例，0 失败、0 错误、0 跳过。
- `./gradlew :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`，43 秒；ARM64 Debug APK 已重新生成。
- Debug APK：`artifacts/3.1.0-work.1/stage3-06-02-mode-session-ua-20261030/RikkaHub.Work-3.1.0-work.1-stage3-06-02-mode-session-ua-20261030-arm64-v8a-debug.apk`。
- APK 核验：applicationId `me.rerere.rikkahub.debug`；versionName `3.1.0-work.1`；versionCode `199`；仅 `arm64-v8a`；APK Signature Scheme v2 通过；ZIP 完整性通过；大小 `87,868,689` 字节；SHA-256 `10d0dd9535e8d619d77957d5b0ef1b0c428ba882d6c436ab6e75d589352ca6cf`。
- 静态计数：`Color(0x` 为 539，`fontSize =` 为 37，三份 strings 各 2,025；与阶段三⑥基线一致。`git diff --check` 仍只命中此前存在的 `Settings.kt:241` 文件末尾空行，本批未改该文件。

## 源码包

- 源码 ZIP：`artifacts/3.1.0-work.1/stage3-06-02-mode-session-ua-20261030/RikkaHub.Work-3.1.0-work.1-stage3-06-02-mode-session-ua-20261030-source.zip`。
- ZIP 不含 APK、`artifacts/`、`build/`、本地配置、签名文件或密钥；含根 `DELIVERY.md`，保留 `.claude/skills` 与 `web-ui/AGENTS.md` 两个 Unix 符号链接，并设置 UTF-8 文件名标志。
- 封装脚本、逐文件清单、凭据扫描结果和 APK/源码 SHA-256 清单均在同目录；源码 ZIP 最终哈希不回写到 ZIP，避免自引用。
- 本轮未执行 `git commit`、`git tag`、`git push`、GitHub 发布、ADB 安装或模拟器/真机操作。冷启动模式、群组会话跟随、DeepSeek 登录和诊断日志仍待维护者安装 APK 后实测。

日期：2026-10-29（Asia/Shanghai）  
版本：`3.1.0-work.1` / `versionCode 199`  
分支：`merge/upstream-2.5.6-f009`，HEAD `12a6ed24`（本批未提交）  
交付范围：ARM64 Debug APK + 不含 APK 的完整源码 ZIP；未发布、未推送、未打 tag、未操作设备或真实账号。

## 交付边界

- 本批没有修改版本号、包名、数据库版本或 schema；数据库仍为 32。
- Debug 变体的 applicationId 为 `me.rerere.rikkahub.debug`，只构建 `arm64-v8a`。没有构建 Release，也没有使用 Release 签名。
- 源码 ZIP 不含 `APK`、`artifacts/`、`build/`、`local.properties`、签名文件或密钥；保留 `.claude/skills` 与 `web-ui/AGENTS.md` 两个符号链接，并强制 ZIP 文件名使用 UTF-8 标志。
- 应用桌面图标与关于页图标仍与基线 `643191229dcbeb4972e3772986818605633ace92` 一致；其他上游图标和页面资源照常保留/同步。
- 仓库没有 `DEVELOPMENT.md`，按任务包要求没有创建替代文件；LiquidGlass 上游重套说明已登记到 `FEATURES.md`，本批偏差和证据在本文件。

## P0-1 模式持久化

- `ChatPage.kt` 不再在打开会话时按会话类型强制写回 `settings.uiMode`；`selectGroup` / `selectConversation` 仍保留。
- `ChatDrawer.kt` 的抽屉模式来自 `settings.uiMode`；用户在抽屉中主动切换聊天/群组模式的两个写入点仍保留。
- `GroupVM.kt` 的自动压缩提示判据改为 `state.group == null`，避免移除强制写回后群聊提示永不触发。
- 设备验收项 A1-A6（杀进程重开、通知进入群聊、群内压缩提示、群内新建会话）未在本机执行，待维护者安装 APK 验证。

## P0-2 液态玻璃崩溃

- `liquidglass/src/main/java/com/qmdeve/liquidglass/widget/LiquidGlassView.java` 的 `updateConfig()` 捕获当前 `LiquidGlass impl`，延迟回调执行前检查 `glass == impl`，避免 detach 后字段已清空导致 `updateParameters()` 空指针。
- 只改了 `updateConfig()` 的异步回调保护；`ensureGlass`、`removeGlass`、尺寸计算和渲染管线未改。
- 该修改属于 vendored 上游库补丁，已在 `FEATURES.md` 的“上游升级需重做清单”登记；建议后续向 QmDeve/AndroidLiquidGlassView 提交 issue。
- 设备验收项 B1-B3（反复进出页面、同时切换附件/审批/记忆面板、视觉效果不变）未执行，待维护者验证。

## P0-3 DeepSeek 网页登录诊断

- `DeepSeekWebLogin.kt` 增加 UA、WebView package、`onConsoleMessage`、`onPageFinished` 的 `href/title/body length` 探针，以及主框架资源错误/HTTP 错误日志。
- 页面完成但尚未捕获 token 时显示诊断条，展示本地化提示和 `probe` 结果；“用系统浏览器打开”通过 `ACTION_VIEW` 打开 `https://chat.deepseek.com`。
- 保留“手动粘贴 Token”路径；上一批保留原始 UA，本批已恢复去除 WebView 标记的 UA 后处理；未拦截/伪造请求、未改 `DEEPSEEK_INPUT_FIX_SCRIPT`。
- 新增三语言各 3 个 key：`deepseek_web_login_open_browser`、`deepseek_web_login_manual_hint`、`deepseek_web_login_diag`。
- 设备验收项 C1-C4 及任务包要求的四项回传材料（logcat、白屏截图、系统浏览器结果、`example.com` 对照）均待维护者提供；本轮没有联网登录或操作真实账号。

## P1 UI 一致性审计

本批只做行级外观统一，不改主题、颜色方案、字号或页面布局结构。所有有把握的变更均属于任务包 §5.3 允许范围。

### 逐条改动表

| 文件:行 | 现状 | 改成 | 理由 |
|---|---|---|---|
| `SettingBrowserPage.kt:126,132` | 清除数据、Cookies 行无前导图标 | 分别加入删除、Cookies 图标 | A：可操作的标准卡片行 |
| `SettingBrowserPage.kt:161-189` | 浏览器工具行使用裸 `Switch`，缺少语义图标 | 使用 `switchItem`，按工具名称映射图标 | A/C：统一标准开关行和点击区 |
| `SettingBrowserPage.kt:195-247` | 过滤、设备模式、超时等标准开关/操作行样式不一致 | 使用 `switchItem`/语义图标 | A/C：与同页 CardGroup 行一致 |
| `ProviderConfigureDeepSeekWeb.kt:67,72,105,118,129,139,162,175` | 状态、登录、凭据、开关、节流和测试行缺少图标或使用裸 `Switch` | 加入语义图标；启用行改 `switchItem` | A/C：配置页标准行统一 |
| `SettingBrowserCookiesPage.kt:123,172,197` | Cookies 手动操作、站点访问和清理行缺少图标 | 加入 Cookies、站点、删除图标 | A：可操作行统一 |
| `GroupConfigPage.kt:119-129,176-226` | 标签、策略、群聊设置、搜索/最近对话/时间提醒等行缺少图标或开关形式不统一 | 加入语义图标；标准开关改 `switchItem` | A/C：群组配置页与相邻设置页一致 |
| `AssistantLocalToolPage.kt:149,233` | 工具审批/浏览器开关为裸 `Switch` 或缺图标 | 标准行改 `switchItem` 并加入图标 | A/C：标准卡片行统一 |
| `AssistantMemoryPage.kt:552,612,864,938` | 记忆分组、时间提醒、文件和整理状态行缺图标 | 加入记忆、时钟、文件等语义图标 | A：可操作/信息入口行统一 |
| `SettingPreferencesUIPage.kt:147,260` | 助手气泡裸 `Switch`、自定义字体入口无图标 | 改 `switchItem` 并加入消息/文件图标 | A/C：标准显示设置行统一 |
| `SettingProviderDetailPage.kt`、`SettingWebPage.kt` | 标准卡片开关使用裸 `Switch` | 改为 `switchItem` | C：属于标准卡片行 |
| 三份 `strings.xml` 的 `browser_group_actions` | 分组标题与页面标题都显示“浏览器” | 英文 `Actions`，简中/繁中 `操作` | B：消除重复标题，不新增 key |

### 明确保留的存疑项

- `GroupDrawer.kt` 的 `item(key = "header")` 是抽屉结构性 header，不是设置项；不添加无意义图标。
- `SettingBrowserPage.kt` 的工具说明、分类标题和底部说明行是纯文字/结构行；`SettingBrowserCookiesPage.kt` 的说明行同理，不加图标。
- `ProviderConfigureDeepSeekWeb.kt` 的能力说明、图片提示和页脚是纯说明块；不加图标，不把说明伪装成可点击项。

剩余直接使用 M3 `Switch` 的文件逐项判断如下：

| 文件:行 | 判定 | 原因 |
|---|---|---|
| `AssistantBasicPage.kt:265,308` | 保留 | 参数 `formItem` 下方联动显示输入控件，不是单一开关行 |
| `AssistantPromptPage.kt:550,699` | 保留 | 提示词编辑自定义表单 |
| `ConversationExport.kt:145,204` | 保留 | 导出对话框中的 `ListItem` |
| `PromptPage.kt:454,885,1039,1128,1138,1149` | 保留 | 提示词编辑器的自定义 `FormItem` |
| `GroupConfigPage.kt:141,273` | 保留 | 背景设置卡片的自定义表单、组员卡片行 |
| `GroupCompressionSettings.kt:31,44,61` | 保留 | 压缩设置面板内联 `Row`，伴随数值输入与滑块 |
| `GroupConfigDialogs.kt:101` | 保留 | 组员编辑对话框的 `FormItem` |
| `GroupDialogs.kt:160,262` | 保留 | 组员编辑表单/参数控件 |
| `LogPage.kt:189` | 保留 | 请求记录专用卡片布局 |
| `SettingMcpDetailPage.kt:351,712,727` | 保留 | MCP 表单及工具行中的紧凑多开关布局 |
| `SettingSearchDetailPage.kt:847` | 保留 | 搜索供应商 `FormItem` |
| `QuotaQueryOption.kt:67` | 保留 | 展开按钮与开关并列的额度查询自定义行 |

本批转换为 `switchItem` 的标准卡片行位于 `AssistantLocalToolPage.kt`、`SettingBrowserPage.kt`、`SettingPreferencesUIPage.kt`、`SettingProviderDetailPage.kt`、`SettingWebPage.kt`、`ProviderConfigureDeepSeekWeb.kt`。

### 审计脚本输出

以下 A 脚本与任务包相同：`item(` 开始的 9 行内没有 `leadingContent` 即计数。改前读取阶段三⑤源码 ZIP，改后读取当前工作树。

```text
BEFORE (stage3-05 source snapshot)
  1  ui/pages/assistant/detail/AssistantBasicPage.kt
  1  ui/pages/assistant/detail/AssistantLocalToolPage.kt
  1  ui/pages/backup/tabs/ReminderTab.kt
  1  ui/pages/chat/ConversationList.kt
  1  ui/pages/group/components/GroupDrawer.kt
  1  ui/pages/mediacreation/MediaCreationComposer.kt
  1  ui/pages/setting/SettingFilesPage.kt
  1  ui/pages/setting/SettingMediaPage.kt
  1  ui/pages/setting/SettingModelPage.kt
  1  ui/pages/setting/SettingModelPromptPage.kt
  1  ui/pages/setting/SettingPreferencesNotificationPage.kt
  1  ui/pages/setting/SettingPreferencesThemePage.kt
  1  ui/pages/setting/SettingProviderDetailPage.kt
  1  ui/pages/setting/SettingSearchPage.kt
  1  ui/pages/setting/components/ASRProviderConfigure.kt
  1  ui/pages/setting/components/MediaGenerationProviderConfigure.kt
  1  ui/pages/setting/components/ProviderConfigure.kt
  2  ui/pages/extensions/skills/SkillsPage.kt
  2  ui/pages/setting/SettingPreferencesNetworkPage.kt
  2  ui/pages/setting/SettingPreferencesUIPage.kt
  2  ui/pages/setting/SettingSearchDetailPage.kt
  3  ui/pages/chat/ChatList.kt
  3  ui/pages/memory/MemoryFilePage.kt
  4  ui/pages/assistant/detail/AssistantMemoryPage.kt
  4  ui/pages/backup/tabs/ImportExportTab.kt
  4  ui/pages/group/GroupConfigPage.kt
  4  ui/pages/setting/SettingThemePage.kt
  4  ui/pages/translator/TranslatorPage.kt
  5  ui/pages/setting/SettingBrowserCookiesPage.kt
  5  ui/pages/setting/SettingPage.kt
  9  ui/pages/setting/SettingWebPage.kt
 10  ui/pages/setting/SettingBrowserPage.kt
 13  ui/pages/setting/components/ProviderConfigureDeepSeekWeb.kt
TOTAL 93

AFTER (current tree)
  1  ui/pages/assistant/detail/AssistantBasicPage.kt
  1  ui/pages/backup/tabs/ReminderTab.kt
  1  ui/pages/chat/ConversationList.kt
  1  ui/pages/group/components/GroupDrawer.kt
  1  ui/pages/mediacreation/MediaCreationComposer.kt
  1  ui/pages/setting/SettingBrowserCookiesPage.kt
  1  ui/pages/setting/SettingFilesPage.kt
  1  ui/pages/setting/SettingMediaPage.kt
  1  ui/pages/setting/SettingModelPage.kt
  1  ui/pages/setting/SettingModelPromptPage.kt
  1  ui/pages/setting/SettingPreferencesNotificationPage.kt
  1  ui/pages/setting/SettingPreferencesThemePage.kt
  1  ui/pages/setting/SettingSearchPage.kt
  1  ui/pages/setting/components/ASRProviderConfigure.kt
  1  ui/pages/setting/components/MediaGenerationProviderConfigure.kt
  1  ui/pages/setting/components/ProviderConfigure.kt
  2  ui/pages/extensions/skills/SkillsPage.kt
  2  ui/pages/setting/SettingPreferencesNetworkPage.kt
  2  ui/pages/setting/SettingSearchDetailPage.kt
  3  ui/pages/chat/ChatList.kt
  3  ui/pages/memory/MemoryFilePage.kt
  3  ui/pages/setting/SettingBrowserPage.kt
  4  ui/pages/backup/tabs/ImportExportTab.kt
  4  ui/pages/setting/SettingThemePage.kt
  4  ui/pages/translator/TranslatorPage.kt
  5  ui/pages/setting/SettingPage.kt
  5  ui/pages/setting/components/ProviderConfigureDeepSeekWeb.kt
  7  ui/pages/setting/SettingWebPage.kt
TOTAL 60
```

补充基线核对：

- `grep -rn "Color(0x" app/src/main/java/me/rerere/rikkahub/ui | wc -l`：539；阶段三⑤源码快照同为 539，本批没有改颜色。任务包 §5.1 所列 543 与可复现的本地基线不一致，未为追数字批量修改。
- `grep -rn "fontSize =" app/src/main/java/me/rerere/rikkahub/ui | wc -l`：37，保持不变。
- 三份资源文件各 2,025 条；资源目录只有 `values`、`values-zh`、`values-zh-rTW`。
- 按任务书的字面子串 `Switch(` 搜索，阶段三⑤快照为 20 个文件、当前为 14 个文件；按实际 M3 标识符 `\bSwitch\(` 过滤后分别为 18 和 12。转换集中于标准卡片行，剩余 12 个直接使用文件均列在上表或属于定制布局。

## 构建与测试

- `./gradlew :app:compileDebugKotlin --console=plain --no-configuration-cache --max-workers=4`：成功；任务结果主要为 `UP-TO-DATE`。
- `./gradlew :app:testDebugUnitTest --console=plain --no-configuration-cache --max-workers=4`：`BUILD SUCCESSFUL`；92 个测试套件、659 个用例，0 失败、0 错误、0 跳过。Gradle 本次复用已完成的测试任务，JUnit XML 已核对计数。
- `./gradlew :app:assembleDebug --console=plain --no-configuration-cache --max-workers=4`：`BUILD SUCCESSFUL`，1 分 26 秒，300 个 actionable tasks，7 个执行、293 个 `UP-TO-DATE`。
- APK：`artifacts/3.1.0-work.1/stage3-06-fixes-20261029/RikkaHub.Work-3.1.0-work.1-arm64-v8a-debug.apk`。
- APK 核验：applicationId `me.rerere.rikkahub.debug`；versionName `3.1.0-work.1`；versionCode `199`；ABI 仅 `arm64-v8a`；V2 Debug 签名验证通过；ZIP 完整性通过；大小 `87,868,689` 字节；SHA-256 `d7b736b5f7f96bffbf5a8656c2ddcd43daa6e9c5260205bfc5037cfb76a8d43d`。

## 源码包与状态

- 源码 ZIP：`artifacts/3.1.0-work.1/stage3-06-fixes-20261029/RikkaHub.Work-3.1.0-work.1-stage3-06-fixes-source.zip`。APK 不在 ZIP 内；源码 ZIP 的 SHA-256 和大小写在同目录 `SHA256SUMS`，不把校验值回写进 ZIP，避免自引用。
- 封装脚本：`artifacts/3.1.0-work.1/stage3-06-fixes-20261029/package-source.py`；同时生成 `SOURCE-MANIFEST.json`、`SOURCE-VERIFICATION.json` 和 `SHA256SUMS`。
- 当前 `git status --short`：651 个状态条目，其中暂存区 591、工作树未暂存 139、未跟踪 31；这些包含此前上游合并和阶段三①至⑤累积改动，未清理、回滚或覆盖。未提交任何本批 commit。
- 本轮未执行 `git commit`、`git tag`、`git push`、GitHub 发布、ADB 安装或模拟器/真机操作。

---

# 阶段三⑤交付记录

日期：2026-10-29（Asia/Shanghai）  
版本：`3.1.0-work.1` / `versionCode 199`  
分支：`merge/upstream-2.5.6-f009`，HEAD `12a6ed24`  
交付范围：ARM64 Debug APK + 不含 APK 的完整源码 ZIP；未发布、未推送。

## 本批变更

- **A3 审批三档**：在聊天与群聊输入栏横向工具区的 Reasoning 按钮后加入 `ToolApprovalButton`，不受 `groupMode` 限制；群聊回调同时持久化 `toolApprovalMode`。`ChatInput` 函数签名未变。
- **B1 浏览器图标**：设置页“内置浏览器”行改用 `CardGroupIcon`，与同页分组图标一致。
- **W 工作区审批**：移除工作区详情页四项逐工具审批开关，以及对应 ViewModel、Repository、工具构造函数中的覆盖值解析链路。保留 `WorkspaceEntity.toolApprovals` Room 字段、数据库 schema、`pathOutsideWritableRoots` 写入/编辑路径护栏，以及四项 `L3_REWRITE` 风险登记。旧字段值保留但不再影响行为。
- 无新增语言 key、依赖或数据库迁移；版本号和数据库版本不变。

本批源码范围为 7 个 Kotlin 文件：`ChatInput.kt`、`ChatPage.kt`、`SettingPage.kt`、`WorkspaceDetailPage.kt`、`WorkspaceDetailVM.kt`、`WorkspaceRepository.kt`、`WorkspaceTools.kt`。相对上一批阶段三④修正单源码 ZIP，这 7 个文件均有变化；本批另外更新根 `CHANGES.md`、`AGENTS.md` 并新增本文件。

## 构建与测试

- `./gradlew :app:compileDebugKotlin --console=plain`：成功；任务由 Gradle 判为 `UP-TO-DATE`。此前本批源码编译也已通过。
- `./gradlew :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`，1 分 44 秒，5 个任务执行、291 个任务为 `UP-TO-DATE`。
- `./gradlew :app:testDebugUnitTest --console=plain`：`BUILD SUCCESSFUL`；Gradle 复用最新测试结果。JUnit XML 时间为 2026-10-29 12:31（本地时间），共 **92 套件 / 659 用例，0 失败、0 错误、0 跳过**。
- `git diff --check` 对本批 7 个源码文件及交付文档通过；全仓检查只提示既有 `Settings.kt` 第 241 行文件末尾空行。该文件与阶段三④源码包逐字节一致，本批未改。
- APK：`artifacts/3.1.0-work.1/stage3-05-style-interaction-20261029/RikkaHub.Work-3.1.0-work.1-arm64-v8a-debug.apk`。
- APK 核验：applicationId `me.rerere.rikkahub.debug`；versionName `3.1.0-work.1`；versionCode `199`；仅 `arm64-v8a`；V2 Debug 签名验证通过；ZIP 完整性通过。大小 `87,868,689` 字节，SHA-256 `d3ccb1ad3a7e19e6da3e2d770cee367b6c26801b3623922d329a5efef540fcf0`。
- 未安装或运行模拟器/设备；输入栏切档与群聊持久化、工作区审批行为、界面布局仍需维护者安装 APK 实测。

## 五项核对

| 编号 | 结果 |
|---|---|
| B2 设置分组标题 | 通过：4 个 `CardGroup` 均有标题。 |
| B3 `PagerTabs.kt` | 无法完成任务书要求的上游 3.1.1 逐字节比较：本地没有 3.1.1 tag/ref 或对应源码。当前文件 SHA-256 为 `596d98f40e4d4896f8cab483c0f69d8a9070e0db3702fa4a28503788c5244d09`，与阶段三③源码包一致。未改该文件。 |
| C1 可点击 Surface | 与任务书预期不符：`WorkspacePage.kt` 的 workspace 卡片使用 `Surface(onClick = ...)`；`WorkspaceDetailPage.kt` 的文件行在 `Surface` 修饰符上使用 `combinedClickable`。按要求仅记录、未改。 |
| C2 `ItemActionMenu` | 命中 **22 个** Kotlin 源码文件，包含群组 4 页；任务书预期为 19 个。未改。 |
| C4 空态 | 记忆页三处 `ExtensionEmptyState` 存在；组员、群组、群聊页面使用 `GroupEmptyState`。通过静态核对。 |

## 阶段三收尾自检

| # | 判据 | 结果 |
|---|---|---|
| 1 | 建组员、建群、配置组员、多轮顺序发言及群聊设置入口 | 代码阶段已完成；完整交互需维护者实机验收。 |
| 2 | `ModeSwitcher` 聊天/群组切换 | 接线已在阶段三③恢复；实际交互待实机验收。 |
| 3 | 审批三档在聊天与群聊切换并生效 | 本批已接线；聊天及群聊行为待实机验收。 |
| 4 | 记忆复制、迁移和导入来源选择 | 阶段三④代码与测试已完成；设备验收待维护者。 |
| 5 | 设置页图标一致 | B1 已改；静态检查通过，视觉效果待设备验收。 |
| 6 | 三个旧测试编译并运行 | App 测试全套 92/659 通过；旧测试包含在测试结果中。 |
| 7 | 三语 key 数一致 | `values`、`values-zh`、`values-zh-rTW` 各 2,022。 |
| 8 | 编译与单测 | Debug 编译/构建通过；92 套件 / 659 用例全绿。 |
| 9 | 无 `_parked/` 残留 | 空的 `_parked/` 目录已移除；当前无该目录。 |
| 10 | versionCode 199 / DB 32 | 通过；`app/build.gradle.kts` 与 `AppDatabase.kt` 核对一致。 |
| 11 | 工作区逐项审批已移除、行为边界保留 | UI 与逐项解析链路已移除；Room 字段、四项 L3 和路径越界护栏保留。 |

## 源码包与工作树

- 源码 ZIP：`artifacts/3.1.0-work.1/stage3-05-style-interaction-20261029/RikkaHub.Work-3.1.0-work.1-stage3-05-style-interaction-source.zip`。
- ZIP 不含 APK、`artifacts/`、`build/`、本地配置或密钥；含根 `DELIVERY.md`，保留 `.claude/skills -> ../.agents/skills`、`web-ui/AGENTS.md -> CLAUDE.md` 两个符号链接，并设置 UTF-8 文件名标志。APK 与 ZIP 的 SHA-256 清单在同目录 `SHA256SUMS`。
- 仓库缺少任务包提到的 `DEVELOPMENT.md` 和 `docs/26-清单-阶段3待补UI与入口.md`；按任务范围未新建或修改替代文档。
- 交付时 `git status --short` 共 650 行：619 个已跟踪变更项、31 个未跟踪项；619 项带暂存区变化，136 项带工作树变化。大部分属于此前上游合并、阶段二及阶段三①至④的累积工作；本批相对阶段三④源码 ZIP 的产品差异仅为上列 7 个源码文件。未清理、回滚或覆盖其他改动，工作树仍未提交。
- 设备/视觉验证由维护者安装本批 ARM64 Debug APK 完成。本轮没有提交、打 tag、push、发布或操作设备/真实账号。
