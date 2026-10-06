# RikkaHub 二次开发功能台账

## [F001] 日历删除工具
- **需求**：用户希望能删除建错的日历事件
- **状态**：✅ 已交付
- **量级**：A 类（改工具）
- **改动文件**：
  - app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/CalendarTool.kt
  - app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/LocalTools.kt
  - app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/BuiltinToolUIs.kt
  - app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/ToolUI.kt
  - app/src/main/res/values*/strings.xml
  - app/src/test/java/me/rerere/rikkahub/data/ai/tools/local/CalendarToolTest.kt
  - app/src/test/java/me/rerere/rikkahub/ui/components/message/tools/ToolUIRegistryTest.kt
- **commit**：4e3bcb1（核心功能）及本条所在的 UI 补充提交（哈希见 git log -1）
- **回滚方式**：git revert <F001 commit>
- **副作用**：无；复用现有 Calendar 开关、写权限和工具审批机制；保留 calendar_query 的 id 输出
- **测试**：事件 ID 校验与工具 UI 注册测试 3/3 通过；Debug APK 构建及签名校验通过
- **交付物**：rikkahub-F001-debug.apk
- **日期**：2026-09-20

## [F002] 记忆分组
- **需求**：让多个指定助手共享同一组记忆，同时保留私有记忆与内置全局记忆
- **状态**：✅ 已交付
- **量级**：B 类（数据接线 + UI）
- **改动文件**：
  - app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt
  - app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt
  - app/src/main/java/me/rerere/rikkahub/data/repository/MemoryRepository.kt
  - app/src/main/java/me/rerere/rikkahub/data/ai/tools/ChatToolFactory.kt
  - app/src/main/java/me/rerere/rikkahub/service/ChatService.kt
  - app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/AssistantVM.kt
  - app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantDetailVM.kt
  - app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryPage.kt
  - app/src/main/java/me/rerere/rikkahub/ui/components/ui/MemoryGroupSelector.kt
  - app/src/main/res/values*/strings.xml
  - app/src/test/java/me/rerere/rikkahub/data/repository/MemoryRepositoryTest.kt
- **commit**：本条所在提交（哈希见 git log -1）
- **回滚方式**：git revert <F002 commit>
- **副作用**：不迁移或复制既有记忆；删除分组会永久删除该组记忆，成员助手回落为私有记忆
- **测试**：记忆范围与旧数据兼容测试 4/4 通过；Debug APK 构建通过
- **交付物**：rikkahub-F002-debug.apk
- **日期**：2026-09-20

## [F003] 更新源改造
- **需求**：更新检查只读取本 fork 的 GitHub Release，并让用户自行下载和安装
- **状态**：✅ 已实现，待用户确认 APK 后提交
- **量级**：A 类（更新逻辑 + UI）
- **改动文件**：
  - app/src/main/java/me/rerere/rikkahub/utils/UpdateChecker.kt
  - app/src/main/java/me/rerere/rikkahub/ui/components/ui/UpdateCard.kt
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-zh/strings.xml
  - app/src/test/java/me/rerere/rikkahub/utils/UpdateCheckerTest.kt
- **commit**：待用户确认 APK 后提交
- **回滚方式**：`git revert <F003 commit>`
- **行为**：数据源为 `ziwa0213-crypto/rikkahub_work` 的 GitHub Release；点击更新卡片打开发布页；网络失败、404、限流和解析失败均静默；不再由应用内 `DownloadManager` 下载 APK
- **版本约定**：Release tag 使用不带 `v` 的 SemVer，且核心版本号必须高于已安装版本
- **测试**：GitHub Release JSON 映射、`v` 前缀剥离和可选字段解析测试
- **日期**：2026-09-21

## [F004] 工具审批模式
- **需求**：在聊天输入栏按助手选择工具调用前的审批模式
- **状态**：✅ 已实现，待用户确认 APK 后上传
- **改动文件**：
  - app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt
  - app/src/main/java/me/rerere/rikkahub/data/ai/tools/ToolRiskTiers.kt
  - app/src/main/java/me/rerere/rikkahub/data/ai/tools/ChatToolFactory.kt
  - app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/CalendarTool.kt
  - app/src/main/java/me/rerere/rikkahub/ui/components/ai/ToolApprovalPicker.kt
  - app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-zh/strings.xml
  - app/src/test/java/me/rerere/rikkahub/data/ai/tools/ToolRiskTiersTest.kt
- **回滚方式**：`git revert <F004 commit>`
- **测试**：风险分级、动态 action、MCP/workspace 默认审批、三档覆盖及 `ask_user` HITL 白名单测试
- **日期**：2026-09-21

## [F006] 液态玻璃效果接入
- **需求**：为聊天输入栏接入 `AndroidLiquidGlassView` 的折射与色散效果，并保留 Android 13 以下用户的兼容路径
- **状态**：🛠️ 正式接入及本地 Release 构建已完成，2.5.3-work.3（versionCode 190）；实机验证待用户，未上传 GitHub
- **量级**：B 类（依赖接入 + 设置模型 + Compose/View 互操作）
- **改动文件**：
  - `gradle/libs.versions.toml`
  - `app/build.gradle.kts`
  - `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/components/ai/LiquidGlassInputBackground.kt`
  - `liquidglass/`（v1.0.5 MIT 库本地模块；增加录制防重入及渲染失败回调）
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/debug/DebugPage.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPreferencesGeneralPage.kt`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-zh/strings.xml`
  - `app/src/main/res/values-zh-rTW/strings.xml`
  - `app/src/test/java/me/rerere/rikkahub/data/datastore/BackgroundEffectTest.kt`
  - `docs/07-液态玻璃库接入预研.md`
- **版本**：`versionName=2.5.3-work.3`，`versionCode=190`
- **兼容行为**：`minSdk=26` 保持不变；API 33 以下走 Haze 兼容渲染；旧 `glass` 数据归一化为液态兼容模式
- **实现与验证**：已将聊天内容采样源、液态玻璃和输入控件分置于独立兄弟层；采样源不包含玻璃自身。库内加入录制防重入、finally 收尾和异步渲染异常回调；失败时进程内熔断并回退 Haze。API 33 以下仍走 Haze，默认仍为模糊。`:app:testDebugUnitTest`、`:app:compileReleaseKotlin` 和 `:app:assembleRelease` 均通过；Release APK 包名、版本与 V2 签名已核验。用户确认隔离原型观感良好，但正式聊天页的重复进出、旋转、切换会话、输入触摸、滚动性能、发热和耗电仍需设备验证
- **第三方组件**：`com.qmdeve.liquidglass:core:1.0.5`，MIT License，Copyright © 2025-2026 Donny Yale (QmDeve)
- **commit**：尚未提交；改动仍在工作树
- **回滚方式**：提交前按审核后的 F006 文件清单回退；形成专属提交后使用 `git revert <F006 commit>`
- **APK**：本轮只生成本地 Release APK，不上传 GitHub；设备验证结果由用户确认
- **上游升级需重做清单**：重新核对 `ChatInput.kt`、`PreferencesStore.kt`、`SettingPreferencesGeneralPage.kt`、三份保留语言资源及 `libs.versions.toml` 的冲突；重新执行编译、单元测试和 API 33+ 实机验证
- **日期**：2026-09-23

## [上游 2.5.4 + 2.5.5] 合并跟进
- **状态**：✅ 已合并到当前分支，待本地全量测试与 APK 构建
- **基线**：上游 2.5.5；本 fork 版本 `2.5.5-work.1` / `versionCode=192`
- **来源**：上游 2.5.4、2.5.5 官方发布说明；未额外引入发布说明之外的上游功能范围
- **commit**：`616fbb66`（merge: sync upstream 2.5.4 and 2.5.5）
- **回滚方式**：`git revert -m 1 616fbb66`（仅在明确需要撤回整次上游合并时使用）
- **主要跟进**：思考模式与工具调用修复、技能创建与搜索、数据恢复、阿拉伯语 RTL、设置/大文件/MCP 稳定性修复，以及列表、供应商页和输入框 UI 调整

## [F008] 加载动画个性化
- **需求**：按供应商保存加载动画，自动匹配渠道品牌，并支持自定义 GIF/图片或 URL
- **状态**：✅ 已实现并随 `2.5.5-work.1` 发布；未进行设备/虚拟机验证
- **量级**：B 类（数据模型 + Compose UI + Coil 渲染）
- **改动文件**：
  - `ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt`
  - `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/components/ui/LoadingAnimation.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/SettingProviderLoadingAnimation.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/ProviderConfigure.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/components/ai/CompressContextDialog.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/components/ai/FilesPicker.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPreferencesGeneralPage.kt`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-zh/strings.xml`
  - `app/src/main/res/values-zh-rTW/strings.xml`
  - `app/src/test/java/me/rerere/rikkahub/ui/components/ui/LoadingAnimationTest.kt`
  - 删除 `app/src/main/java/me/rerere/rikkahub/ui/components/ui/RabbitLoading.kt`
  - 删除 `app/src/main/res/drawable/rabbit.xml`
- **行为**：AUTO 按品牌优先，识别不到时按具体供应商类型；PRESET 提供转圈、四类专属品牌和通用呼吸；CUSTOM 支持本地图片/GIF 与 URL，并用全局一次性提示记录“不再提示”
- **兼容**：新增 `ProviderSetting.loadingAnimation` 与 `Settings.loadingAnimationHintDismissed` 均有默认值；旧 `useAppIconStyleLoadingIndicator` 字段原样保留但不再参与渲染；`provider=null` 回退到 Material 转圈
- **测试**：新增自动匹配、渠道回退、显式配置容错、序列化与旧数据默认值测试；`./gradlew :app:testDebugUnitTest :ai:test` 已通过
- **commit**：`4f521c6e`（F008 与上游 2.5.5 同步）
- **回滚方式**：`git revert 4f521c6e`（该提交还包含上游同步文档，撤销前先核对影响）
- **日期**：2026-09-29

## [F008 补充三] 加载动画跟随当前聊天模型
- **需求**：切换助手模型后，加载动画立即切换到接下来将使用的供应商，不再滞后一轮
- **状态**：✅ 已修复并随 `2.5.5-work.2` 发布；`:app:testDebugUnitTest`、全模块 `test` 与 `:app:assembleRelease` 通过；维护者反馈设备验证无问题
- **版本**：`2.5.5-work.2` / `versionCode=193`
- **改动文件**：
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt`
  - `app/build.gradle.kts`
  - `CHANGES.md`
  - `AGENTS.md`
- **行为**：`currentProvider` 依据 `assistant.chatModelId ?: settings.chatModelId` 解析；移除历史消息列表作为 `remember` key，流式回复到达时不再重复解析动画
- **验证**：单元测试与 Release 构建通过；三个 APK 的包名均为 `me.rerere.rikkahub.plus`、版本均为 `2.5.5-work.2 (193)`，V2 签名证书 SHA-256 为 `671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b`
- **commit**：`a3a004ea`
- **回滚方式**：`git revert a3a004ea`
- **日期**：2026-09-30

## [发布收尾] 2.5.5-work.1 元数据与品牌拼写
- **需求**：修正 work.1 源码 tag、Release 资产名、README 英文标题和历史资产名说明
- **状态**：✅ GitHub `2.5.5-work.1` tag 已指向版本 192 的提交，三个 Release 资产已更名；README 和变更说明已同步
- **改动文件**：`README.md`、`CHANGES.md`、`AGENTS.md`
- **commit**：`a3a004ea`
- **日期**：2026-09-30

## [F009] DeepSeek Web 内置供应商
- **需求**：提供受约束的 DeepSeek Web 浏览器会话供应商，支持思考流、PoW 和有限工具调用
- **状态**：✅ 已实现并完成本地测试与 Release 构建；APK 尚未上传 GitHub，未进行设备验证
- **版本**：`2.5.6-work.1` / `versionCode=194`
- **改动文件**：
  - `ai/src/main/java/me/rerere/ai/provider/providers/deepseekweb/`
  - `ai/src/main/assets/deepseek_sha3.wasm`
  - `ai/src/test/java/me/rerere/ai/provider/providers/deepseekweb/DeepSeekWebSSETest.kt`
  - `ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt`
  - `app/src/main/java/me/rerere/rikkahub/data/datastore/DefaultProviders.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/ProviderConfigureDeepSeekWeb.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/DeepSeekWebLogin.kt`
- **行为**：WebView 登录捕获或手动 Token/Cookie；PoW；SSE 文本/思考流；请求串行和节流；工具白名单、每轮单工具和 F004 审批兼容
- **限制**：仅提供工作区文件读取；不提供文件写入、日历新建/删除、记忆修改、剪贴板写入、Shell、代码执行、技能和 MCP；账号限流或封禁风险由用户自行承担
- **修复**：SSE `response/status` 与工具内容的字符串 `v` 不再触发 JSON 对象强制转换崩溃
- **测试**：DeepSeek SSE、PoW、能力预检、只读工具边界和伪工具输出清理测试通过；`:ai:test`、`:app:testDebugUnitTest`、Debug 编译和 `:app:assembleRelease` 通过
- **日期**：2026-10-03

## [上游 2.5.6 合并] 2026-10-03
- **状态**：✅ 已合并到 `merge/upstream-2.5.6-f009`，Release APK 已构建，未上传 GitHub
- **保留**：F002-F008、F007 reasoning 修复、F008 加载动画、F006 液态玻璃、F005 去赞助/去水印/品牌本地化
- **跟进**：`chart_display`、`customHeaders`、Google Interactions API、MCP `0.15.0-rikka.2`、对话导出增强和图片生成入口
- **特例**：保留复制记忆底层 DAO/Repository/VM 能力，不显示上游复制记忆 UI，等待 F010
- **语言**：全工程资源仅保留英文、简体中文、繁体中文
- **验证**：`:ai:test`、`:app:testDebugUnitTest`、`:ai:compileDebugKotlin`、`:app:compileDebugKotlin` 和 `:app:assembleRelease` 通过
- **日期**：2026-10-03

## [F009 补充一] 能力拒绝显式化
- **需求**：让 DeepSeek Web 对禁止的本机项目能力明确报错，不把拒绝混入普通 AI 文本
- **状态**：✅ 已实现并完成本地测试与 Release 构建，当前版本 `2.5.6-work.1` / `versionCode=194`，未上传 GitHub、未进行设备验证
- **行为**：明确要求文件落盘、项目修改、命令/代码执行、创建 App 等操作时显示不可自动消失的红色错误卡片；纯代码问答、脚本示例和架构讨论放行
- **工具边界**：受限工具继续不下发到 DeepSeek Web prompt；模型幻觉输出未知工具时记录日志、阻止执行并显示被拦截工具名称
- **出口**：错误卡片不提供供应商跳转链接，仅保留错误说明、复制和关闭；上游已有的快速模型设置检查入口保持不变
- **设置页**：DeepSeek Web 配置页新增“使用范围限制（只读）”区块，列出禁用与可用能力
- **改动文件**：
  - `ai/src/main/java/me/rerere/ai/provider/providers/deepseekweb/DeepSeekWebGuard.kt`
  - `ai/src/main/java/me/rerere/ai/provider/providers/deepseekweb/DeepSeekWebTools.kt`
  - `ai/src/main/java/me/rerere/ai/provider/providers/deepseekweb/DeepSeekWebSSE.kt`
  - `ai/src/main/java/me/rerere/ai/provider/providers/deepseekweb/DeepSeekWebProvider.kt`
  - `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/components/ui/ErrorCard.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/ProviderConfigureDeepSeekWeb.kt`
  - `app/src/main/res/values*/strings.xml`
- **测试**：补充预检正/反例、只读白名单、剪贴板 action 收窄、被禁工具调用和伪工具输出清理测试；`:ai:test`、`:app:testDebugUnitTest`、`:app:compileDebugKotlin`、`:app:assembleRelease` 通过
- **日期**：2026-10-03

## [F009 补充二] DeepSeek Web 只读供应商
- **需求**：将 DeepSeek Web 固定为只读供应商，防止不可信模型修改本地文件、日历、记忆或剪贴板
- **状态**：✅ 已实现并完成本地测试与 Release 构建，当前版本 `2.5.6-work.1` / `versionCode=194`，未上传 GitHub、未进行设备验证
- **允许**：工作区文件读取、日历查询、时间、屏幕使用、历史对话查询、联网搜索、网页抓取、询问用户、语音和剪贴板读取
- **禁止**：日历新建/删除、记忆修改、文件写入/编辑、剪贴板写入、Shell、代码执行、技能和 MCP
- **应用层保证**：被禁工具不进入 DeepSeek Web prompt；clipboard 仅暴露 `action=read`；模型伪造的被禁工具 JSON 会被移除并转为不可自动消失的错误卡片
- **错误卡片**：不新增 `ChatErrorSolution.SwitchToApiProvider`，不提供任何供应商跳转链接；既有 `CheckFastModelSettings` 保持不变
- **配置页**：新增只读说明及“读取到的文件内容会发送到网页端”的风险提示
- **改动文件**：
  - `ai/src/main/java/me/rerere/ai/provider/providers/deepseekweb/DeepSeekWebTools.kt`
  - `app/src/main/java/me/rerere/rikkahub/data/ai/DeepSeekWebOutputGuard.kt`
  - `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/components/ui/ErrorCard.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/ProviderConfigureDeepSeekWeb.kt`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-zh/strings.xml`
  - `app/src/main/res/values-zh-rTW/strings.xml`
- **日期**：2026-10-03

## [F009 补充三] 预检漏报与 DSML 格式检测
- **需求**：修复能力拒绝不弹卡片的触发缺口，检测模型输出的 DSML 调用格式，并说明图片接入现状
- **状态**：全模块 `test` 与包含混合 DSML 修复、语言过滤的最终 Release 构建已通过；三种 APK 的版本、包名、签名、语言与 WASM 已核验；未上传 GitHub、未操作设备
- **版本**：`2.5.6-work.1` / `versionCode=194`，不递增
- **源码核实**：卡片构造、挂载、`autoDismiss` 与既有快速模型 solution 未被删除；缺口是预检未覆盖部分请求、DSML 未被检测
- **行为**：聊天服务在工具装配与生成前做 DeepSeek Web 专用预检，覆盖 Skill、文件写入、日历/记忆/剪贴板修改及 MCP 等明确请求；能力拒绝与格式异常使用持久、无链接的错误卡片，普通错误仍按原逻辑处理
- **DSML**：被禁/未知调用剥离并报错；白名单调用保留并报格式未识别，本次不执行；混合块仅剥离被禁 invoke；SSE 缓冲跨片段标记及正文，避免重复输出；现有下发协议仍为 JSON
- **只读边界**：解析与输出检测共用 11 项白名单，剪贴板仅 `read`；输出检测只看最新助手消息，支持相邻 Text 拼接，不扫描历史消息
- **图片阶段记录**：首次构建仅加 OCR 配置提示；后续网页图片上传代码已接入（见下面独立条目），共享 `OcrTransformer` 未改，线上仍待实测
- **语言打包**：`resourceConfigurations` 保留英文、简体中文、繁体中文及其必要默认回退资源，过滤第三方依赖携带的其他语言；不改第三方源码
- **改动文件**：
  - `ai/src/main/java/me/rerere/ai/provider/providers/deepseekweb/DeepSeekWebGuard.kt`
  - `ai/src/main/java/me/rerere/ai/provider/providers/deepseekweb/DeepSeekWebToolPolicy.kt`
  - `ai/src/main/java/me/rerere/ai/provider/providers/deepseekweb/DeepSeekWebTools.kt`
  - `ai/src/main/java/me/rerere/ai/provider/providers/deepseekweb/DeepSeekWebSSE.kt`
  - `app/src/main/java/me/rerere/rikkahub/data/ai/DeepSeekWebOutputGuard.kt`
  - `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/ProviderConfigureDeepSeekWeb.kt`
  - `app/build.gradle.kts`（依赖语言资源打包过滤，不改变版本号）
  - `app/src/main/res/values/strings.xml`、`app/src/main/res/values-zh/strings.xml`、`app/src/main/res/values-zh-rTW/strings.xml`
  - AI 模块 `DeepSeekWebGuardTest.kt`、`DeepSeekWebToolsTest.kt`、`DeepSeekWebSSETest.kt`
  - App 模块 `DeepSeekWebOutputGuardTest.kt`、`service/DeepSeekWebChatErrorTest.kt`
  - `CHANGES.md`、`FEATURES.md`、`AGENTS.md`
- **测试**：定向编译与单元测试、全模块 `test`、`:app:assembleRelease` 均通过；DeepSeek 相关 33 项单元测试零失败，覆盖五次 Skill 请求的独立卡片、无链接/持久属性、普通供应商和错误回归、JSON/DSML 混合块与剪贴板边界
- **产物核验**：arm64-v8a / universal / x86_64 的包名均为 `me.rerere.rikkahub.plus`，版本 `2.5.6-work.1` / `194`；均使用现有 Release 签名并包含 `assets/deepseek_sha3.wasm`；语言仅为默认、英文、中文及 CN/TW 区域资源
- **待验证**：实机卡片与复制/关闭交互；网页原生图片上传；JSON 与 DSML 协议成功率对比
- **commit**：未提交，本次未执行 commit/tag/push
- **回滚策略**：仅反向应用本补充三的增量 diff，保留补充一/二的拦截、错误发送、挂载及自动消失判断；独立提交后可用 `git revert <补充三提交>`，不可整文件恢复而覆盖之前改动
- **上游同步**：重新套用 provider 专用预检、共享只读策略与 DSML 检测；不改 `GenerationLoop.kt`、`OcrTransformer` 或持久化模型
- **日期**：2026-10-06

## [F009 补充四] 工具调用协议对齐
- **需求**：对齐提示词输出格式、模型文本调用与真正的工具执行/拒绝链；只支持 JSON，不新增 DSML 执行兼容
- **状态**：全模块 `test` 与签名 Release 构建通过；DeepSeek Web 相关 75 项测试零失败；三种 APK 的版本、包名、签名、三语与 WASM 已核验；实机测试由维护者完成
- **版本**：`2.5.6-work.1` / `versionCode=194`，不变
- **提示词**：只读定位与白名单不变；在工具清单前追加输出结构和无参数/有参数示例，不加围栏、不加解释；仅下发已启用的只读工具
- **解析**：结构化 JSON 校验，支持嵌套/转义、围栏和前后文字；只处理首个调用，其余剥离并记日志；调用前完成协议解析，再检查只读与当前启用工具
- **执行与拒绝**：正常调用转换成原有 `ToolCallStart/Delta/End`，保留审批/HITL；被禁或未启用调用不输出工具事件，保留周围正文，再通过 provider 关闭流抛出能力拒绝，走现有持久无链接错误卡片
- **兜底**：畸形/未知 JSON 不执行且保留原文，日志与应用层格式卡片不静默；已解析的调用不重复扫描；DSML 保留补充三的检测行为
- **结果回传**：只读工具的文本/图片结果传回网页上下文；图片复用现有上传与引用；被禁历史调用/输出不恢复
- **改动文件**：`DeepSeekWebTools.kt`、新增 `DeepSeekWebToolProtocol.kt`、`DeepSeekWebToolPolicy.kt`（仅追加被禁名称判据，不改白名单）、`DeepSeekWebSSE.kt`、`DeepSeekWebProvider.kt`、`DeepSeekWebImages.kt`、`DeepSeekWebOutputGuard.kt`；协议/SSE/提示词/图片/错误分类相关测试；`CHANGES.md`、`FEATURES.md`
- **边界**：源码现有读取工具不能列目录，A1 改为指定文件读取的离线闭环验证，不改工具契约、不新增 shell 能力；`GenerationLoop`、共享 OCR、持久化与普通供应商不改；继续只保留三语
- **副作用**：遇到大括号/代码围栏时，其后文本缓冲到本轮结束以免调用 JSON 提前泄漏；纯聊天无此额外缓冲；未知工具示例会保留并提示格式未生效，不执行
- **待验证**：A1 指定文件读取结果、A2 日历新建拒绝、A3 Skill 拒绝、A4 时间、A5 日历查询的真实模型/设备表现；错误卡片复制/关闭；截图；未使用账号联网测试，未操作模拟器
- **验证记录**：`docs/references/f009-supplement4-verification.md`（包含请求提示词前后对比、关键改动与 A1-A13 离线/实机边界）
- **产物**：`app/build/outputs/apk/f009-protocol-20261006/RikkaHub.Work-2.5.6-work.1-{arm64-v8a,universal,x86_64}-release.apk`；保留上一轮图片接入 APK，仅本地 Release，不上传 GitHub
- **commit**：未提交，未执行 commit/tag/push
- **回滚策略**：只反向应用本补充的增量 diff，保留图片接入和补充一/二/三安全与错误链；独立提交后 `git revert <协议对齐提交>`，不得整文件恢复覆盖先前修改
- **上游同步**：重新套用 DeepSeek Web 专用输出协议、JSON/SSE 转换与只读结果回传；不修改共享生成路径
- **日期**：2026-10-06

## [F009 图片上传] 网页文件上传与图片引用
- **需求**：先接入并构建本地安装包，由维护者验证快速/思考模式识图；不使用账号联网验证
- **状态**：定向编译与单元测试、全模块 `test`、签名 `:app:assembleRelease` 已通过；三种 APK 的版本、包名、签名、WASM 和三语资源已核验；线上识图待维护者测试
- **版本**：`2.5.6-work.1` / `versionCode=194`，保持不变
- **行为**：读取本地/内联图片，复用现有编码器；上传目标单独申请 PoW，使用 multipart 获得文件 ID；聊天通过 `ref_file_ids` 引用，提示词使用对应编号；两种模式都支持
- **边界**：单次最多 24 份去重 URI 后的图片附件（含历史上下文），每张编码后不超过 10 MB；同 URI/相同内容去重；不抓取远程 URL、不跨请求缓存文件 ID；失败直接终止本次生成并发持久无链接卡片，不静默假装识图
- **旧配置**：仅在 DeepSeek Web 分支补齐两个已知模型的 IMAGE 输入，保留其 UUID、名称、自定义设置以及供应商凭证；无数据模型字段增删/改名/默认值变化，无供应商删除重建要求
- **改动文件**：
  - `ai/src/main/java/me/rerere/ai/provider/providers/deepseekweb/DeepSeekWebImages.kt`（新增）
  - `ai/src/main/java/me/rerere/ai/provider/providers/deepseekweb/DeepSeekWebModels.kt`（新增）
  - 同目录 `DeepSeekWebPoW.kt`、`DeepSeekWebProvider.kt`、`DeepSeekWebTools.kt`
  - `app/src/main/java/me/rerere/rikkahub/data/datastore/DefaultProviders.kt`、`PreferencesStore.kt`（仅 DeepSeek Web 归一化分支）
  - `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/ProviderConfigureDeepSeekWeb.kt`
  - 英文/简中/繁中 `strings.xml`
  - AI 模块 `DeepSeekWebImagesTest.kt`、`DeepSeekWebModelsTest.kt`、`DeepSeekWebPoWTest.kt`
  - App 模块 `DefaultProvidersTest.kt`、`DeepSeekWebChatErrorTest.kt`
  - `CHANGES.md`、`FEATURES.md`
- **验证**：DeepSeek 相关 49 项单元测试零失败，默认供应商另增一项图片声明测试；覆盖实际上传代码的拦截器响应、multipart 字段/后缀、目标 PoW、重复图片、限制边界、上传失败中止、JSON 形态/业务码/缺失 ID、取消、两种模式的 completion 请求体与默认模型声明；不连接 DeepSeek 服务
- **产物**：`app/build/outputs/apk/f009-images-20261006/RikkaHub.Work-2.5.6-work.1-{arm64-v8a,universal,x86_64}-release.apk`，仅 Release，新建独立目录保留上一轮安装包
- **参考来源**：[cv-superding/dsh-deepseek-web-login](https://github.com/cv-superding/dsh-deepseek-web-login)（Apache-2.0）的 `src/webapi.ts`，提交 `b84f0a1228c3a8031e7177f49ce09b52d761da11`；协议参考，本地 Kotlin 实现
- **待验证**：实机上传返回与快速/思考识图；旧配置升级后不走 OCR；其他 API 图片输入回归
- **commit**：未提交，未执行 commit/tag/push
- **回滚策略**：只反向应用本图片接入的增量 diff，保留补充一/二/三安全与报错链路；独立提交后可用 `git revert <图片接入提交>`，不得整文件恢复覆盖存量修改
- **上游同步**：保留 provider 专用图片上传和目标 PoW、统一图片模型声明、DeepSeek Web 旧配置补齐；共享 OCR 和 `GenerationLoop` 不改
- **日期**：2026-10-06
