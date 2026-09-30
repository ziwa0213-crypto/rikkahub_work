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
- **状态**：✅ 已修复；`:app:testDebugUnitTest` 与 `:app:assembleRelease` 通过；A/C 实机验收待维护者验证
- **版本**：`2.5.5-work.2` / `versionCode=193`
- **改动文件**：
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt`
  - `app/build.gradle.kts`
  - `CHANGES.md`
  - `AGENTS.md`
- **行为**：`currentProvider` 依据 `assistant.chatModelId ?: settings.chatModelId` 解析；移除历史消息列表作为 `remember` key，流式回复到达时不再重复解析动画
- **验证**：单元测试与 Release 构建通过；三个 APK 的包名均为 `me.rerere.rikkahub.plus`、版本均为 `2.5.5-work.2 (193)`，V2 签名证书 SHA-256 为 `671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b`
- **commit**：待提交
- **回滚方式**：`git revert <F008 补充三 commit>`
- **日期**：2026-09-30

## [发布收尾] 2.5.5-work.1 元数据与品牌拼写
- **需求**：修正 work.1 源码 tag、Release 资产名、README 英文标题和历史资产名说明
- **状态**：本地源码已修正；GitHub tag 与资产重命名待远程操作
- **改动文件**：`README.md`、`CHANGES.md`、`AGENTS.md`
- **commit**：与 F008 补充三的源码提交一并记录
- **日期**：2026-09-30
