# F022 / F021 文件映射与维护说明

日期：2026-11-20。版本 `3.3.0-work.1 / 207`，DB34。基线为本批开始时的 F020 补充八及桌面构建工具工作区，不能把所有相对 HEAD 的修改都归为本批。尚无本批提交；未自动构建 APK 或源码 ZIP。验证和维护者待验详见根 `DELIVERY.md`。

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
| 工作区渲染与资源边界 | `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/{WorkspaceMarkdownPreview,WorkspacePreviewInterceptor,WorkspacePreviewResources}.kt` | 同目录 `WorkspaceFileEditorPage.kt` |
| Markdown 图片目录上下文 | `app/src/main/java/me/rerere/rikkahub/ui/components/richtext/WorkspaceImagePath.kt` | 同目录 `Markdown.kt`；`app/src/main/java/me/rerere/rikkahub/ui/context/LocalWorkspace.kt` |
| WebView 与 MIME | `ui/src/main/java/me/rerere/ui/webview/MimeTypes.kt` | 同目录 `WebView.kt`、`WebViewLocalAssets.kt` |
| 回归测试 | `app/src/test/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspacePreviewResourcesTest.kt`；`app/src/test/java/me/rerere/rikkahub/ui/components/richtext/WorkspaceImagePathTest.kt`；`ui/src/test/java/me/rerere/ui/webview/MimeTypesTest.kt` | 无 |

### 下次同步上游

- FILES / LINUX 区域、文档父目录和虚拟源是一组上下文，避免本地资源重复拼接路径或切工作区后继续使用旧 client。`resolveFile` 只能解析现有文件：先在 IO 中解析当前文件，不能拿目录调用，也不能在主线程 `runBlocking`。
- WebView 的可选拦截接口默认 null，保留其他使用方行为；非当前虚拟源、GET / HEAD 之外的方法、非法路径或不存在的资源必须返回 404 而非 null，避免交给默认网络加载。
- canonical 边界、符号链接越界检查、禁 file/content 和外网、脚本默认关闭及开关重建须整体保留。单纯打开 JS 不能同时放开外部资源访问。
- Markdown 复用原组件，保留聊天绝对工作区图片与外链图片；不要把 HTML 的离线限制误施到原 Markdown 行为。
- 1 MiB 是预览渲染上限，不是文件写入上限。LINUX 原 512 KiB 读取保护保持；SSH 的本地关联资源未支持，不用 `resolveFile=null` 伪造本地路径。

## 公共文案、交付工具与台账

- 三语：`app/src/main/res/{values,values-zh,values-zh-rTW}/strings.xml`。F022 新增 8 key，F021 新增 4 key，各语言共 12；不新增其他语言。
- F022 key：`setting_page_built_in_tools_read_url`、`setting_page_built_in_tools_read_url_desc`、`chat_server_tool_web_search`、`chat_server_tool_read_url`、`assistant_page_local_tools_client_context_title`、`assistant_page_local_tools_client_context_desc`、`pulse_tool_client_context`、`pulse_tool_client_context_desc`。
- F021 key：`workspace_preview_too_large`、`workspace_preview_enable_scripts`、`workspace_preview_source`、`workspace_preview_preview`。
- 修改 `AGENTS.md`、`CHANGES.md`、`FEATURES.md`、`DELIVERY.md`、`tools/BUILDING.md`；新增本文。本机仓库外 `/home/li/桌面/构建 RikkaHub Work.sh` 仅补默认工具链环境，保留调用者显式值。
- `tools/package-delivery-source.mjs` 本批无需修改：已把根 DELIVERY 设为必需并核验实际归档内容。Release 和 Debug 共用该步骤。后续由维护者运行桌面 TUI，才会产生 APK 和源码 ZIP。

## 回滚边界

当前 HEAD 不包含此前 F020 工作区改动，没有可单独 revert 的本批 commit。对照本表逐块撤销本批调用点、工具协议及新增 key，确认无引用后移除本批新增文件；不要整文件 restore、整树 reset、取消既有 `.gitmodules` / llama.cpp 暂存或删除历史产物。

先处理 UI 与请求构造调用点，再撤销协议/配置声明，最后移除新文件和测试。保留旧配置可读能力；不要把撤销 ClientContext 变成删除或改名旧 localTools / Pulse 字段。原 F004/F007/F009、F020、原应用及关于页图标、数据库和模型权重均不在本批回滚范围内。
