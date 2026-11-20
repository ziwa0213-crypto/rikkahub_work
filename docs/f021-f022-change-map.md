# F022 / F021 文件映射与维护说明

日期：2026-11-20。版本 `3.3.0-work.1 / 207`，DB34。原 F022 / F021 已纳入 `6f0d5e2a`；本次 HTML / SVG 预览回退以 `532dee9e` 为基线，实施期间检测到回退源码已纳入本地提交 `be5ed028`，后续文档仍为工作区增量。代理未执行提交，不自动构建 APK 或源码 ZIP。下方 F021 记录更新后的状态，F022 保持原样；验证和维护者待验详见根 `DELIVERY.md`。

下表路径相对仓库根；“修改”仅指本批在原工作区内容上叠加的部分。

## F022

| 职责 | 新增 | 修改 |
| --- | --- | --- |
| 请求元信息与方言 | `ai/src/main/java/me/rerere/ai/provider/ClientContext.kt`；`ai/src/main/java/me/rerere/ai/provider/providers/DeepSeekDialect.kt` | `ai/src/main/java/me/rerere/ai/provider/{Model,Provider,ProviderSetting}.kt` |
| 服务端工具协议 | `ai/src/main/java/me/rerere/ai/provider/providers/openai/DeepSeekServerTools.kt` | 同目录 `ChatCompletionsAPI.kt`、`ChatCompletionsStreamDecoder.kt`；`ai/src/main/java/me/rerere/ai/provider/providers/openai/ResponseAPI.kt`；`ai/src/main/java/me/rerere/ai/provider/providers/claude/ClaudeProvider.kt`；`ai/src/main/java/me/rerere/ai/ui/MessageMetadata.kt` |
| 请求组装与配置兼容 | `app/src/main/java/me/rerere/rikkahub/data/ai/ClientContextFactory.kt`；`app/src/main/java/me/rerere/rikkahub/data/datastore/ClientContextCompatibility.kt` | `app/src/main/java/me/rerere/rikkahub/data/ai/ChatRequestFactory.kt`；`data/ai/tools/local/LocalToolOption.kt`；`data/datastore/PulseConfig.kt`；`data/model/{Assistant,PulseChat}.kt`（后四项均相对同一 `app/src/main/java/me/rerere/rikkahub/`） |
| 工具配置与展示 | 无 | `app/src/main/java/me/rerere/rikkahub/ui/components/ai/SearchPicker.kt`；`ui/components/message/ChatMessageTools.kt`；`ui/pages/assistant/detail/AssistantLocalToolPage.kt`；`ui/pages/pulse/PulseSettingsPage.kt`；`ui/pages/setting/SettingProviderDetailPage.kt`；`web/routes/SettingsRoutes.kt`（后五项相对同一 App Kotlin 根） |
| 回归测试 | `ai/src/test/java/me/rerere/ai/provider/providers/openai/DeepSeekConversationToolsTest.kt`；`app/src/test/java/me/rerere/rikkahub/data/ai/ClientContextTest.kt` | `ai/src/test/java/me/rerere/ai/provider/providers/openai/ChatCompletionsRequestMessageTest.kt`；`app/src/test/java/me/rerere/rikkahub/data/ai/tools/local/AlarmRegistrationTest.kt`；`app/src/test/java/me/rerere/rikkahub/data/model/PulseConfigTest.kt` |

### 下次同步上游

- ReadUrl 的序列化名和开关必须与三协议声明一起保留；不要在其他供应商请求中加入 DeepSeek 专用字段。
- server_tool_calls 要保留完整 JsonObject 和 signature，按历史步骤回传，不能只保存 UI 摘要。终局要先结清流式状态，再补权威服务端工具结果；卡片不得因重复终局而重复。
- `ChatCompletionsAPI` 的 F007 `hasTools` 与 reasoning 回传守卫保持；服务端 step 没有保存的思考内容按空字符串回传，不伪造。
- ClientContext 为请求期 transient 数据，不是 Tool。DeepSeek Chat Completions 的逐 user reminder 与其他协议的 system 注入应保持互斥。
- Assistant / PulseConfig 的新实例默认值、旧 JSON 兼容转换器与 EncodeDefault 必须整体保留。删掉兼容器会把缺字段的旧数据误判成新配置；删掉显式编码可能使新配置保存重读后关闭。

## F021

| 职责 | 新增 | 修改 |
| --- | --- | --- |
| 工作区渲染 | `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceMarkdownPreview.kt` | 同目录 `WorkspaceFileEditorPage.kt`；HTML / SVG 恢复原预览，1 MiB 限制仅作用于 Markdown |
| Markdown 图片目录上下文 | `app/src/main/java/me/rerere/rikkahub/ui/components/richtext/WorkspaceImagePath.kt` | 同目录 `Markdown.kt`；`app/src/main/java/me/rerere/rikkahub/ui/context/LocalWorkspace.kt` |
| WebView 与 MIME | `ui/src/main/java/me/rerere/ui/webview/MimeTypes.kt` | 同目录 `WebView.kt`、`WebViewLocalAssets.kt` |
| 回归测试 | `app/src/test/java/me/rerere/rikkahub/ui/components/richtext/WorkspaceImagePathTest.kt`；`ui/src/test/java/me/rerere/ui/webview/MimeTypesTest.kt` | 无 |

### 2026-11-20 后修正：HTML / SVG 回退

- HTML / SVG 预览回退到改动前的 `loadDataWithBaseURL` + 固定虚拟源，不做资源拦截；同目录 CSS / 图片等本地资源不再映射，脚本行为沿用原 WebView，没有“启用脚本”按钮。
- 删除原拦截器、资源解析器及其专用测试，共三个文件；移除新增网络加载限制，不移到其他位置。Markdown 大小常量移至编辑页，值仍为 `1024L * 1024`。
- `WebViewState.requestInterceptor` 作为可选扩展点保留，当前无调用方；`remember(state)`、更新 client、公共 MIME 工具与测试均不修改。
- Markdown 渲染器、图片目录上下文、聊天绝对路径 / http(s) 图片分支及三个保留文案逐字节不变。

### 下次同步上游

- Markdown 复用原组件，保留 `LocalWorkspaceBaseDir` / `LocalWorkspaceStorageArea` 与 `rememberWorkspaceImageUrl` 的接线、聊天绝对工作区图片及外链图片行为。
- 1 MiB 只限制 Markdown 预览，不是文件写入上限；HTML / SVG 不检查该上限。LINUX 原 512 KiB 读取保护保持，FILES 仍可保存。
- HTML / SVG 沿用旧预览；不要重新带入已撤销的资源映射、脚本按钮和网络加载限制。WebView 的可选扩展点及 MIME 工具保留即可。

## 公共文案、交付工具与台账

- 三语：`app/src/main/res/{values,values-zh,values-zh-rTW}/strings.xml`。F022 新增 8 key，F021 保留 3 key，各语言共 11；各语言总计 2,719 key，集合一致，不新增其他语言。
- F022 key：`setting_page_built_in_tools_read_url`、`setting_page_built_in_tools_read_url_desc`、`chat_server_tool_web_search`、`chat_server_tool_read_url`、`assistant_page_local_tools_client_context_title`、`assistant_page_local_tools_client_context_desc`、`pulse_tool_client_context`、`pulse_tool_client_context_desc`。
- F021 key：`workspace_preview_too_large`、`workspace_preview_source`、`workspace_preview_preview`。只移除原脚本开关一项；任务书写各 2,717 是计数误差，实际各 2,720 减一为 2,719。
- 修改 `AGENTS.md`、`CHANGES.md`、`FEATURES.md`、`DELIVERY.md`、`tools/BUILDING.md`；新增本文。本机仓库外 `/home/li/桌面/构建 RikkaHub Work.sh` 仅补默认工具链环境，保留调用者显式值。
- `tools/package-delivery-source.mjs` 本批无需修改：已把根 DELIVERY 设为必需并核验实际归档内容。Release 和 Debug 共用该步骤。后续由维护者运行桌面 TUI，才会产生 APK 和源码 ZIP。

## 回滚边界

当前 HEAD 已包含此前 F020 / F021 / F022，本次 HTML 回退源码已在 `be5ed028`；后续文档为未提交增量。对照本表逐块处理相应调用点与 key，确认引用后再调整对应文件；不要整文件 restore、整树 reset、改动既有 `.gitmodules` / llama.cpp 指针或删除历史产物。此次删除的三个文件可从基线 `532dee9e` 恢复，恢复时必须同时处理调用点，不能影响 Markdown 或 F022。

先处理 UI 与请求构造调用点，再撤销协议/配置声明，最后移除新文件和测试。保留旧配置可读能力；不要把撤销 ClientContext 变成删除或改名旧 localTools / Pulse 字段。原 F004/F007/F009、F020、原应用及关于页图标、数据库和模型权重均不在本批回滚范围内。
