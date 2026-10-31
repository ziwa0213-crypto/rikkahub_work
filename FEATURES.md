# RikkaHub 二次开发功能台账

## [阶段三⑥三 启动竞态修复] 3.1.0-work.1 / 199（2026-10-30，本地开发，未发布）

- **设置加载闸门**：`RouteActivity.AppRoutes()` 在 `Settings.init == true`（`Settings.dummy()` 占位值）时只渲染空容器并提前返回，禁止在真实设置读盘前创建 `rememberNavBackStack`。
- **会话加载闸门**：`ChatVM` 暴露 `conversationLoaded`，由初始化协程的 `finally` 置为完成；`ChatPage` 的模式同步同时要求会话已加载且 `setting.init == false`，不再用占位会话的当前助手误判群聊类型。
- **`colors` 纠正**：恢复 `CardGroup.switchItem(colors = ...)` 和 `SettingProviderDetailPage` 的统一开关行，撤销阶段三⑥二的误删/裸 `Switch` 回退。
- **边界**：不改 `Settings.init`、`Settings.uiMode`、会话初始化签名、生成链路、数据库、schema、依赖或语言资源；版本保持 `3.1.0-work.1 / 199`。设备上的连续冷启动验收待维护者执行。

## [阶段三⑥二 模式与会话对齐] 3.1.0-work.1 / 199（2026-10-30，本地开发，未发布）

- **模式与会话对齐**：`RouteActivity` 在群组模式冷启动时复用默认组最后一个群聊会话；无组或组内无会话时进入群组列表，不创建空群聊。`ChatPage` 打开会话后按 `conversation.isGroupChat()` 幂等同步 `Settings.uiMode`，覆盖列表、通知、搜索、收藏和深链进入会话的路径；聊天模式的 `create_new_conversation_on_start` 语义不变。
- **DeepSeek UA**：恢复 WebView UA 的 `; wv`、`Version/4.0 ` 后处理，并同时记录 raw/effective UA；诊断条移至顶部。未改输入修复脚本、请求拦截或 token 解析。
- **CardGroup 参数清理**：删除 `switchItem` 的死 `colors` 参数；实际需要颜色定制的内置工具行改用 `item` 保持原视觉效果。
- **边界**：版本保持 `3.1.0-work.1 / 199`，数据库版本 32、schema、依赖和三语资源不变。设备验收、DeepSeek 实机登录和冷启动行为待维护者安装 APK 验证。

## [阶段三⑥修正单] 3.1.0-work.1 / 199（2026-10-29，本地开发，未发布）

- **P0 模式持久化**：删除聊天页按当前会话类型强制写回 `uiMode` 的路径，抽屉读取用户实际选择的模式；保留抽屉中用户主动切换聊天/群组模式的写入。群组压缩提示改用 `state.group == null` 判据，避免移除强制写回后提示永不触发。
- **P0 液态玻璃竞态**：在 vendored `liquidglass/.../LiquidGlassView.java` 的 `updateConfig()` 中捕获当前 `LiquidGlass` 实例，并在异步回调执行前确认字段仍指向同一实例，避免页面 detach 后延迟回调访问空对象。未改 `ensureGlass`、`removeGlass` 或渲染管线。
- **P0 DeepSeek 网页登录诊断**：增加 UA/WebView 包、控制台、页面完成探针和资源错误日志；页面完成后记录 URL、标题和正文长度。白屏且尚未捕获登录态时显示诊断条，并提供“用系统浏览器打开”出口；本批后续修正已恢复 UA 后处理，仍未改请求拦截或输入修复脚本。
- **P1 UI 一致性**：为有把握的设置页、DeepSeek 配置页、浏览器页、群组配置页和群组抽屉行补齐语义图标；将标准卡片开关行统一到 `switchItem`；浏览器首个分组标题改为“操作 / Actions”，避免与页面标题重复。纯说明行、对话框内控件和定制布局中的裸 `Switch` 保留并列入存疑清单。
- **语言与数据边界**：仅保留 `values`、`values-zh`、`values-zh-rTW`，三份各 2,025 条；不改版本号 `3.1.0-work.1 / 199`、数据库版本 32 或 schema。
- **上游升级需重做清单**：上游更新时必须重新套用 `liquidglass/src/main/java/com/qmdeve/liquidglass/widget/LiquidGlassView.java` 的 `updateConfig()` 实例快照与身份守卫，并重新核对 `ChatPage.kt`、`ChatDrawer.kt`、`GroupVM.kt`、`DeepSeekWebLogin.kt`、三份 strings 及本批 UI 行级调整。`DEVELOPMENT.md` 不在当前仓库，详细交付边界记录在本批根 `DELIVERY.md`。
- **验证与交付**：`:app:compileDebugKotlin`、`:app:testDebugUnitTest` 已通过；本批再执行一次 `:app:assembleDebug`。仅生成 ARM64 Debug APK 和不含 APK 的完整源码 ZIP，不提交、不打 tag、不推送、不发布、不操作设备或真实账号。

## [阶段三④ 记忆选择器与引用计数] 3.1.0-work.1 / 199（2026-10-28，本地开发，未发布）

- **依据与范围**：`RikkaHub Work-阶段三④记忆选择器与引用计数-20261028.zip`。恢复两份已停放的选择器，接入复制助手、切换记忆归属、助手记忆导入和群聊会话记忆导入；修复 URL 引用展示计数。
- **记忆语义**：助手复制默认不迁移行式记忆，弹框可逐条选择；文件式 Markdown 仍整桶复制。切换归属时可复制/移动勾选的行式记忆，允许空选择并先迁移、后更新归属。助手页来源含全局、记忆分组、助手及有效群聊会话；群聊会话页排除自身来源。共享的全局/分组来源移动时提示受影响的助手数。
- **整桶迁移**：`moveAllRowMemories` 保持既有方法名，扩展为行式记忆与 Markdown 文件一起搬移；目标文件先 upsert，成功后删除来源文件。`g:` 只提取为常量，scope 字符串兼容原存储。
- **引用展示**：`ChatMessage.kt` 仅修改两处判据/计数：没有 `UrlCitation` 不渲染引用面板，计数只统计 `UrlCitation`；保留 GroupSender、GroupQuote 的原有渲染。
- **改动文件**：`MemoryPickerDialog.kt`、`MemorySourcePickerDialog.kt`、`MemoryRepository.kt`、`AssistantVM.kt`、`AssistantPage.kt`、`AssistantDetailVM.kt`、`AssistantMemoryPage.kt`、`GroupMemoryPage.kt`、`ChatMessage.kt`、`AGENTS.md`、`CHANGES.md`、本台账及 `DELIVERY.md`。无新增资源 key、依赖、数据字段或 schema。
- **验证**：`:app:compileDebugKotlin`、`:app:compileDebugUnitTestKotlin` 和 App JVM 测试结果见本批 `DELIVERY.md`；不以源码编译或 JVM 测试代替维护者实机验收。
- **交付**：一次 ARM64 Debug APK 单独提供，源码 ZIP 内含根目录 `DELIVERY.md`、排除 APK；源码归档保留 Unix 符号链接并设置全部文件名 UTF-8 标志。APK 与 ZIP 位于 `artifacts/3.1.0-work.1/stage3-04-memory-picker-20261028/`。未操作设备、未提交、未打标签、未推送/上传。
- **提交与回滚**：本批尚未提交；只反向应用本批产品文件的增量，不整文件恢复或覆盖上游合并与前三包修改。引用修复可单独反向两处；恢复选择器时仅撤销本批文件归位和调用接线。
- **上游重套**：新架构下重新适配 `RowMemory`、分离的 `MemoryEntityDAO`/`MemoryDAO` 和 `me.rerere.ui.toast.ToastType`；逐项核对 `g:` scope 不变、空选择不迁移、先迁移后改归属、文件仅整桶操作，以及引用数仅计 URL。

## [阶段三③ UI 修正] 3.1.0-work.1 / 199（2026-10-28，本地开发，未发布）

- **依据与范围**：维护者提供的《阶段三③ UI 修正》任务包。本批只修正压缩对话框、聊天抽屉模式胶囊和工作区卡片 UI；不改数据模型、生成逻辑或群聊压缩调用链。
- **改动文件**：`CompressContextDialog.kt`、`ChatDrawer.kt`、`WorkspacePage.kt`；删除两个空目录 `_parked/ui-pages-group/detail` 和 `_parked/ui-pages-group/components`。三份语言资源未改。
- **压缩对话框**：删除目标 token 选择控件，继续通过 `initialTargetTokens` 将群组配置值传给三参回调；保留 `FilesPicker`、`ChatPage`、`GroupVM`、`ChatService`、`Group.compressTargetTokens` 和群组设置页原调用/配置。最近消息数改用减号、数字和加号调节并限制最小值为0。
- **抽屉与工作区**：模式胶囊移至昵称行末尾；普通聊天模式的文件夹与排序逻辑保持。工作区卡片合并为单一标题、三行信息及一个“重命名 / 设置归属 / 删除”菜单。
- **实际验证**：`:app:compileDebugKotlin` 和 `:app:assembleDebug` 通过。ARM64 Debug APK 包名为 `me.rerere.rikkahub.debug`，版本 `3.1.0-work.1 / 199`，仅 `arm64-v8a`，SHA-256 为 `d211000b72911759adb9d1e6c7b00ea535f2ecb01206c3f21f823ed4812c4242`。`./gradlew test` 在既有 `MemoryImportSourcesTest.kt` 测试源码编译阶段失败，因其引用当前源码不存在的 `chatMemorySources`；本批未改测试或记忆接口。静态边界检查通过，未做设备验收。
- **交付**：`artifacts/3.1.0-work.1/stage3-03-ui-fix-20261028/`，目录内提供 ARM64 Debug APK 和源码 ZIP；源码 ZIP 根目录含 `DELIVERY.md`，不含 APK。
- **提交与回滚**：尚未提交；不执行 commit/tag/push/上传。回滚时只反向应用本批三个 Kotlin 文件的增量修改并恢复两个空目录；不要整文件恢复覆盖阶段三群聊或上游合并内容。
- **上游重套**：遇到对应文件冲突时重新套用对话框控件移除/减加控件、抽屉 Row 中 ModeSwitcher 定位和工作区卡片单标题/单菜单结构；逐项确认群聊 `targetTokens` 传递及三份 strings 保持原样。

## [阶段三② 群组配置页] 3.1.0-work.1 / 199（2026-10-27，本地开发，未发布）

- **依据与范围**：维护者提供的 RikkaHub Work-阶段三②群组配置页-20261027.zip；七个文件复位，包含重写 GroupMemoryPage。配置页及 MCP/本地工具/扩展/请求/记忆五个子页接回，群聊、抽屉、模式切换和导入选择器仍留后续阶段。
- **入口与资源**：设置 → 通用在组员之后追加群组设置，使用已核实可用的 HugeIcons.UserMultiple；七条新 entry 沿用 Nav3 的 key.groupId / key.conversationId，Screen 定义不改。三语仅新增 setting_page_groups_desc，各2022条。
- **记忆页**：复用 ItemizedMemoryTab，仅对共享函数做 private → internal；CRUD 全部调用当前 MemoryRepository 行式接口和两参 scopeOfChatGroup。页面独立提供选择态顶栏、全选/取消、返回清选和批量删除确认，复用 assistant_page_memory_delete_selected_confirm 文案，失败保留确认状态并提示错误；不触碰 memory_file、不留导入空按钮。
- **必要适配**：ToastType、SkillManager/SkillMetadata、FormItem、OutlinedNumberInput 均按当前模块导入；三个扩展 Tab 改用 extension_selector_tab_*。任务书低估了文件数，且 ItemizedMemoryTab 的选择态操作实际在原助手页外层，需要在新群组页补齐，不能只复制参考片段。
- **改动文件**：group/GroupConfigPage、components/GroupCompressionSettings、detail 下 GroupMcpPage/GroupLocalToolPage/GroupExtensionsPage/GroupRequestPage/GroupMemoryPage；RouteActivity、SettingPage、AssistantMemoryPage（仅一个可见性词）、三份 strings 和三份台账。
- **保护与验收口径**：Settings/群组模型/Room32、SettingsNormalization 临时 CHAT、ChatPage/ChatService/ChatToolFactory/ChatDrawer/GroupChatContext、F006 和原应用图标保持本批前哈希；三个旧测试不改或屏蔽。自检按本批基线比较，DAO 合法的 getMemoriesOfAssistantFlow 不作为页面旧 API 残留删除。无 github flavor 的当前3.1.0工程仍用 assembleDebug，不引入3.1.1构建配置。
- **交付**：目录 artifacts/3.1.0-work.1/stage3-02-group-config/，一次 ARM64 Debug 和完整源码 ZIP（内含 APK、DELIVERY.md 及 APK 校验值）。所有 ZIP 条目显式标 UTF-8，软链保持 Unix 链接属性；源码 ZIP 的最终哈希放包外，避免循环包含。V13～V22 由维护者实机验证，不操作设备或真实账号。
- **回滚与上游重套**：尚无本批 commit，不提交/tag/push/上传。仅对照本目录 before 与 PRODUCT.diff 逐项反向应用七个移动和页面/路由/资源增量，不能整文件恢复既有合并内容或覆盖 index。上游共享需重套路由、设置入口和 ItemizedMemoryTab 可见性；SettingsNormalization 到包③才解除。

## [阶段三① 群组地基] 3.1.0-work.1 / 199（2026-10-27，本地开发，未发布）

- **依据**：维护者新版 `RikkaHub Work-阶段三①群组地基-20261027(1).zip`；补充一优先于主任务书。只恢复八个基础文件，剩余十一份 parked 留待包②～④；GroupChatContext 保持原样。
- **接线**：注册六参数 GroupVM，复用该文件唯一的 rememberGroupVM；仅增加 Screen.GroupMembers entry。设置 → 通用用 CardGroupIcon 显示组员入口，三语各加一条 setting_page_group_members_desc，均为2021条。GroupPage仅参与编译，不开放群组/配置入口。
- **记忆边界**：MemoryRepository补 GROUP_CHAT_MEMORY_PREFIX、两个scopeOfChatGroup重载、scopeOf(memoryGroupId)和moveAllRowMemories；移动全部行式条目明确传ids=null。GroupVM两处move、三处copy、五处delete完成适配，copyFiles/deleteFilesOfAssistant仅管文件，故与行式调用成对；复制失败的两处清理独立捕获两库异常，保留回滚。
- **改动文件**：group/GroupVM、GroupListLayout、GroupMemberPage、GroupPage及components下GroupComponents、GroupDialogs、GroupConfigDialogs、GroupBackground；MemoryRepository、ViewModelModule、RouteActivity、SettingPage、三份strings、GroupMemoryCompatibilityTest及三份台账。
- **源码差异补充**：任务书称ToastType可原样使用，实际已迁往me.rerere.ui.toast，两个列表页按当前源码更正import。旧NewMemberDialog没有头像编辑入口，复用现有UIAvatar.onUpdate补齐V7要求；无新资源/依赖。新增四项记忆回归覆盖作用域兼容、全量移动与隔离、空/同作用域和两库成对复制删除。
- **保护范围**：Settings/群组模型/Room32、ChatService/ChatToolFactory/ChatDrawer/ChatPage、F006渲染及暂时CHAT归一化不变；三个旧测试不改、不屏蔽；保留原应用身份图标/原Debug证书，仅三语。
- **实际验证**：App主源码编译通过，AI45套件/347项、App专项13套件/121项通过（原117+新增4），均0失败/错误/跳过。两份Android迁移测试源码编译通过、未运行设备。全量App测试编译实际剩两文件40条诊断：MemoryImportSourcesTest9条、GroupConversationListTest31条，均依赖后续parked组件；GroupTest不再报编译诊断，不能据此称其测试已运行。三个原文件无改动。
- **交付范围**：本批统一位于 `artifacts/3.1.0-work.1/stage3-01-group-foundation-20261027/`，只出一次ARM64 Debug和完整源码ZIP。不Release、不commit/tag/push/上传、不操作设备或真实账号。V3～V12需维护者实机验证；实际编译/测试/哈希记录在本批DELIVERY中。
- **APK核验**：唯一Debug构建1分25秒成功，87,868,689 bytes，SHA-256为f492d9c9ba24ee61a15cc06065deaf4c77194a0180f287af563c0f7d511790f2。包名me.rerere.rikkahub.debug、版本199/work.1、原证书/身份图标、三语、ARM64-only、WASM、群组类及CRC均通过，61个其他历史安装包未变。八文件恢复、七处接线/API/资源编辑、一份新测试及三份台账之外的2083个原位源码哈希未变，Git index/HEAD/MERGE_HEAD未变；完整源码/清单随本批交付。
- **回滚与重套**：尚无本轮commit。仅反向应用本批PRODUCT.diff（包含八个移动及增量适配），保留既有合并内容与index；下次同步重套组员唯一入口、模块组件import、记忆两库成对操作与scope纯函数，不整文件恢复。临时CHAT归一化到包③再解除。

## [群组模式残留根因修正] 3.1.0-work.1 / 199（2026-10-27，本地修正，未发布）

- **四个产品文件**：`ChatPage.kt` 移除原生玻璃的 `uiMode == CHAT` 前置条件；`SettingPreferencesUIPage.kt` 移除气泡开关外的模式条件；两处无用UiMode import删除。`SettingsNormalization.kt` 在withValuesInRange暂时强制CHAT并标注阶段三删除TODO；`SettingPreferencesGeneralPage.kt` 的active状态复用shouldUseNativeLiquidGlass。
- **兼容边界**：Settings.uiMode声明、类型、默认值、序列化及群组数据不变；normalized末尾的normalizeGroupData不覆盖uiMode。保留ChatInput遮罩、LiquidGlassView尺寸刷新、宿主折射/日志、四项玻璃判据、三层宿主与GONE、熔断和Haze回退；19个parked及三份旧阻塞测试不动。仅英文/简中/繁中，保留原桌面和关于页应用图标及Debug签名。
- **阶段三必须完成**：①删除强制CHAT归一化；②按当时设计评估ChatPage/设置页模式逻辑；③恢复19个parked文件、rememberGroupVM、8条路由、抽屉/内容入口；④重接ModeSwitcher；⑤修复MemoryImportSourcesTest、GroupTest、GroupConversationListTest。此处只是恢复清单，本批不实施。
- **交付与验证**：`artifacts/3.1.0-work.1/stage2-glass-group-mode-root-cause-20261026/`，一次ARM64 Debug及完整工作区源码ZIP。App Kotlin编译通过；AI45套件/347项与App专项12套件/117项实际执行通过，0失败/错误/跳过。5项交付回归夹具检验旧GROUP归一化、群数据/供应商/气泡偏好保留、JSON与Preferences兼容、重复归一化及玻璃判据；既有设置字段/存储测试也通过。两份Android迁移测试仅独立编译；全量App旧测试本批不复跑，既有阻塞仍未处理。设备交互、尺寸日志与视觉效果未验证，不能把配置active文案写成实测通过。
- **包体结果**：唯一Debug构建48秒成功，89,171,690 bytes，SHA-256 `51ffe4119175f1a16c9851f80e009d868d6f19802de1a32eca08d3e0df16e09e`。199/work.1、debug包名、原证书、原应用图标、三语、ARM64-only、WASM/CRC均核验。23个其他旧APK哈希/时间不变，19个parked和Git合并状态不变；源码归档与逐文件校验记录随本批SOURCE-VERIFICATION和DELIVERY提供，不重复出包。
- **提交/回滚/上游重套**：无本批commit，既有merge仍未提交；修改前七文件在本目录before/。只反向应用四处产品增量（可用 `git apply -R <本批产品差异文件>`，先 `git apply --check -R`），不整文件checkout/reset、不覆盖index；前两轮玻璃修复保留。上游同步重套此四处并保持阶段三TODO，群组恢复时一并清理临时归一化。不Release、不commit/tag/push/上传，不操作设备或真实账号。

## [F006 玻璃尺寸与折射参数修正] 3.1.0-work.1 / 199（2026-10-26，本地修正，未发布）

- **`liquidglass/.../LiquidGlassView.java`**：`onSizeChanged` 对有效尺寸调用 `updateConfig()`，替代销毁/重建玻璃；运行时配置尺寸无效时直接返回，不再回退到屏幕尺寸。`ensureGlassInternal()` 构造期的初始化尺寸兜底保持不变。新增 `refreshConfig()` 和 `debugConfigSize()` 供宿主刷新与诊断。
- **`LiquidGlassInputBackground.kt`**：更新 bounds 后，根据控件高度设置折射高度/偏移并设 `blurRadius=0f`；使玻璃可见后立即刷新，并于下一次绘制前再刷新、记录 `bounds/glass/conf/boundsW/boundsH`。配置尺寸预期应与实际玻璃 view 尺寸一致；未运行设备，本批不能填写实测值。
- **保护范围**：不改 `ChatInput.kt`（含刚交付的导航栏遮罩）、`shouldUseNativeLiquidGlass()`、玻璃 source/glass/input 层序、GONE行为、熔断与 Haze 回退。版本保持 `3.1.0-work.1 / 199`，无新资源/设置，仍仅英文/简中/繁中，19个parked和三份旧阻塞测试不变。
- **验证与交付**：目录 `artifacts/3.1.0-work.1/stage2-glass-size-refraction-fix-20261026/`；App Kotlin主源码编译通过，AI45套件/347项与App专项9套件/93项通过，0失败/错误/跳过；一次ARM64 Debug及完整源码ZIP校验通过。全量App测试本批未复跑，已知三个旧测试阻塞文件不修改/屏蔽。包体、哈希和验证细节见本批 `DELIVERY.md`。V2～V9为维护者设备验收项，自动编译/静态核对不替代 `conf`/`glass` 运行日志与视觉实测。
- **回滚/上游升级需重做**：逐文件反向应用这两个产品文件的本批增量，不恢复整文件覆盖遮罩分支或fork组件。上游升级时重套 `LiquidGlassView.java` 的尺寸变更刷新、运行时0尺寸保护、`refreshConfig/debugConfigSize`，及宿主的高度相关折射参数/日志；不改构造期 fallback。快照、diff及Git状态证据在本目录，禁止覆盖index或重置merge。

## [F006 玻璃底部遮罩修正] 3.1.0-work.1 / 199（2026-10-26，本地修正，未发布）

- **唯一产品改动**：`app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt` 的外层遮罩按 `forceLiquidGlass` 分支。玻璃模式仅遮住系统导航栏高度，其他区域不绘制遮罩；非玻璃原渐变代码逐字保留，仅增加嵌套缩进。保留 `onSizeChanged`、输入参数、haze、圆角、bounds回调与布局。
- **与附件的编译适配**：`WindowInsets.navigationBars` 与 `ime` 一样先在可组合函数中取得，再在 `drawWithCache` 读取高度，避免在非可组合绘制回调中调用可组合 getter；新增 `navigationBars`、`Offset`、`Size` 三个 import。不改玻璃判据/宿主/库/熔断、诊断UI、记忆或F009，不新增文案/设置/语言，19个parked和三个旧阻塞测试保留。
- **验证与交付**：目录 `artifacts/3.1.0-work.1/stage2-glass-scrim-fix-20261026/`；当前编译/测试与最终APK结果如下，完整源码及SHA-256清单随本批交付。全量App已知三个旧测试文件仍阻塞，本轮不修改/停用，不把专项通过称全量通过。V3～V9需维护者实机验证，V3/V4须配置背景图或启用渐变；设置诊断不证明宿主已实际渲染。
- **回滚与上游升级需重做清单**：本轮无独立commit SHA；仅反向应用本分支、导航栏Insets捕获及三个import，勿整文件覆盖已有fork输入框修改。上游升级必须重套 `ChatInput.kt` 的遮罩分支，并核对 `forceLiquidGlass` 接线和系统Insets；保留上游非玻璃渐变。修改前四文件与全量源码哈希保存在 `before/` / `BEFORE-SOURCE.json`，差异与原分支逐字核对见 `ChatInput.diff` / `NON-GLASS-BEFORE.txt` / `NON-GLASS-AFTER.txt`，不得覆盖merge/index。
- **本轮测试结果**：App主源码编译及AI测试合计1分4秒成功（147任务，4执行/143复用）；AI45套件/347项实际重跑通过。App专项9套件/93项实际运行9秒通过，0失败、错误或跳过，使用当前生产类；另独立编译两份Android迁移测试源码，未运行Android。全量App测试本轮未复跑，其三个旧阻塞测试文件哈希不变。
- **最终APK**：唯一 `:app:assembleDebug` 1分50秒成功（300任务，5执行/295复用）；89,171,690 bytes，SHA-256 `0647430915af240f1c530f9c4047f29605b31fddc75ef32b63f81f87b4ee67f9`。版本199/work.1、`me.rerere.rikkahub.debug`、RikkaHub Work名称、SDK26/37、ARM64-only、原Debug证书、三语、WASM及CRC均通过；21个图标源文件匹配原基准、20张包内PNG像素及adaptive引用核验通过。21个其他既有APK哈希/修改时间不变，产品仅ChatInput有变化；源码ZIP及校验值见本批DELIVERY，不再重复构建。

## [记忆页 UI 回归与玻璃诊断] 3.1.0-work.1 / 199（2026-10-24，本地修正，未发布）

- **页面变化**：`AssistantMemoryPage.kt`仅改普通行式卡片的主题底色、内容最多5行及省略号，并对齐删除按钮缩进；选中高亮、8dp间距、选择/确认/删除逻辑、输入及全文编辑和做梦Tab保持原样。`SettingPreferencesGeneralPage.kt`只订阅 `LiquidGlassCircuitBreaker.unavailable`，在已开启且选中LIQUID时显示SDK/熔断状态，不增加开关或重试按钮。
- **资源**：`values-zh-rTW/strings.xml`补9条工具核准、3条压缩、1条发布页译文；三语各加active/failed/low_api诊断文案。基线英文/简中2017条、繁中2004条；本批最终每份2020条且key集合一致，已有条目值保持不变。
- **范围核验**：仅上述5个产品文件及三份台账允许变化；不改数据模型、DAO/Repository/Room32、选择删除、文件式记忆、玻璃宿主/判据/库/熔断、F009及3个旧测试。19个parked文件、图标、版本及Git合并状态保留。诊断active分支仅说明配置/SDK/熔断判据，不证明屏幕像素已呈现玻璃。
- **交付/验证**：目录 `artifacts/3.1.0-work.1/stage2-memory-ui-glass-diagnostic-20261024/`，只出一次ARM64 Debug和完整源码ZIP。编译、AI347项/App专项93项及最终包体验证结果随本批交付说明记录；V1～V8/V10均需维护者实机验收，不以静态核对冒称通过。全量App旧测试上批存在3文件/73条编译诊断，本批不修改或屏蔽，也不宣称全量通过。
- **本轮已执行**：App主源码编译21秒成功；AI45套件/347项、App专项9套件/93项均实际重新执行，0失败/错误/跳过；两份迁移Android测试源码独立编译通过、未运行设备。普通全量App测试本批未复跑，旧阻塞源码不变；初次辅助init脚本因included build没有ai子项目而失败，已加空项目守卫后成功，失败/成功日志分开保留。
- **最终APK**：本批唯一ARM64 Debug构建8秒成功，89,171,690 bytes；SHA-256 `faa3df5d76f81602a1c97bbde2c7244673574e32b94bc26699ee27f544867690`。199/work.1、debug包名、应用名、ARM64-only、原Debug证书、三语、WASM、CRC与原应用图标均通过；20个其他既有APK的哈希及修改时间不变。完整源码及校验值随本批 `DELIVERY.md` / `SHA256SUMS` 提供，Git仍未提交，不继续重复构建。
- **恢复/上游重套**：本批未提交，无独立commit SHA；修改前8份文件及源码哈希位于本目录 `before/`、`BEFORE-SOURCE.json`。仅反向应用卡片颜色/摘要、诊断显示及新增字符串，不能整文件覆盖此前F016/F006修复或改写merge/index。后续上游同步重套这两处页面和三语；临时诊断定位后可单独移除，不影响渲染实现。

## [阶段二遗留三项修正] 3.1.0-work.1 / 199（2026-10-24，本地验证，未发布）

- **F016 多选删除**：`AssistantMemoryPage.kt` 将选择态操作提升到真正顶栏；长按进入选择态，选中条目高亮并显示 Checkbox，支持全选/取消全选、关闭和批量删除。空选自动退出，系统返回先退出选择态，普通点击继续编辑。`AssistantDetailVM` 在删除成功后才退出选择态，Repository 对空 ID 列表短路；DAO 继续只删除当前 scope 的 `MemoryEntity`，不碰 `memory_file`。
- **F009 登录页**：`DeepSeekWebLogin.kt` 新增 Loading/Done/Failed 状态、主框架网络/HTTP 错误、错误码/原因/重试 UI 与代理提示；失败状态不会被后续 `onPageFinished` 清掉。`shouldInterceptRequest`、凭证捕获与数字输入修复脚本保持原逻辑，UA 清除 `; wv` 与 `Version/4.0`；新增五条英文/简中/繁中资源。
- **F009 JSON 安全解析**：新增 `DeepSeekWebJson.kt` 的 `objOrNull()` / `parseDeepSeekResponse()`；PoW 与 session 不再对 `JsonNull` 强取 `jsonObject`，顶层/`biz_code` 错误转为可读异常。非 JSON 不回显响应体，错误码只接收整数；涉及凭据或长密钥片段的服务端说明整体省略，避免部分脱敏遗漏 Cookie/Bearer 值。不改请求地址、请求头、PoW 求解算法、SSE 与供应商配置字段。
- **改动文件**：App 的 `AssistantMemoryPage.kt`、`AssistantDetailVM.kt`、`MemoryRepository.kt`、三语 `strings.xml`、`MemoryRepositoryTest.kt`；AI 的 `deepseekweb/DeepSeekWebJson.kt`、`DeepSeekWebPoW.kt`、`DeepSeekWebProvider.kt`、`DeepSeekWebJsonTest.kt`；三份台账。
- **验证**：App 主源码编译通过，AI 45 套件/347 项与 App 专项 9 套件/93 项全部通过；专项直接使用当前生产类，不修改或屏蔽普通测试集合。两份 Android 迁移测试源码独立编译通过。全量 App 单测编译仍受 `MemoryImportSourcesTest` / `GroupTest` / `GroupConversationListTest` 阻断，73 条诊断、用例未执行，不称全量通过。WebView 断网/重试、真实登录、快速/思考对话、玻璃效果和覆盖安装仍由维护者验收。
- **交付与边界**：本批只生成一次 ARM64 Debug 和完整工作区源码 ZIP，目录 `artifacts/3.1.0-work.1/stage2-F006-F016-fix-20261024/`；源码含未提交文件、子模块与19个 `_parked/` 文件，排除 Git、构建缓存、artifacts、本地配置和密钥。构建与 SHA-256 结果记录在该目录的交付说明。本批无 Release、commit/tag/push 或账号/设备操作，合并仍未提交。
- **最终 APK 核验**：`:app:assembleDebug` 成功（20 秒）；APK 为 `RikkaHub.Work-3.1.0-work.1-arm64-v8a-debug.apk`，87,868,689 bytes，SHA-256 `8c446134c8d7b2937b276610d3c9e1bbf14a056ef4f32ba10424e52639c338b6`。版本199/work.1、`me.rerere.rikkahub.debug`、RikkaHub Work名称、SDK26/37、ARM64-only、三语、WASM、CRC、原Debug证书及原应用图标均通过。19个其他既有APK的哈希和修改时间、19个parked文件、Git HEAD/MERGE_HEAD/index均未改变；详见本批 `APK-VERIFICATION.json`、`SCOPE-VERIFICATION.json` 与 `DELIVERY.md`。
- **回滚与重套**：仅反向应用本轮 UI、JSON 解析、Repository/VM 和测试增量，不整文件恢复覆盖 F006/F016/F009。下次合并重套主框架失败态保护、JSON 类型检查及完整错误脱敏、选择顶栏和按 scope 的删除成功回调；保留两库边界。无本轮独立 commit SHA，禁止整树 reset/checkout 或覆盖 index。

## [阶段二 F006 / F016] 3.1.0-work.1 / 199（2026-10-23，本地测试，未发布）

- **执行顺序**：先按 01 恢复玻璃并独立编译（`01-compile.log`），再按 02 恢复行式记忆。维护者保留 work.1/199，不采纳包内 work.2/200；启用记忆即注册 `memory_tool`，覆盖旧版“非空才注册”和“两者都空不注册”的冲突条款。DeepSeek Web 仍拒绝该工具。
- **F006 文件**：恢复 `liquidglass/` 源码与 MIT LICENSE、settings/app Gradle 接线、`LiquidGlassInputBackground`、`liquid_glass_ids.xml`；修改 `ChatPage`、`ChatInput`、`ChatInputHaze`、`SettingPreferencesGeneralPage`、`Settings` 归一化及三语。聊天采样源不包含玻璃或输入控件；仅 LIQUID/BLUR 两档，旧 GLASS 视作 LIQUID；保留兼容字段但不读取它决定渲染，API<33/故障自动回退模糊，故障熔断。
- **F016 数据/注入文件**：新增 `MemoryEntity`、`MemoryEntityDAO`、`RowMemory`、`Migration_31_32`、Room `32.json`、`RowMemoryTools`；修改 `AppDatabase`/Factory、DAO/Repository DI、`MemoryRepository`、`ChatRequestFactory`/`ChatToolFactory`、`MemoryPrompts`。作用域仅复用 `assistant.memoryId / memoryScope`，行式修改/删除限定作用域；复制助手同步复制两套存储，不借机恢复 F010 多选 UI。
- **严格边界**：`RowMemoryTools` 无文件库写入；原 `MemoryTools.kt` 六个文件工具不改契约。`MemoryConsolidator` 只多读行式及传入 `<itemized_memory>`（200条）供判重，不写行式，不改变调度/工作流；未实施可选的 row-memory 转录标记。主对话行式100条上限不删数据，迁移历史 `[migrated]` 不重复作为 profile 正文注入，文件本体仍保留。31/32 的 `memory_file` schema逐字相同。
- **F016 UI 文件**：`AssistantMemoryPage` 三 Tab、行式 CRUD/批量删除、设置分组选择、关闭时禁用两页而不删数据；`AssistantDetailVM` 行式 flow/操作及分组管理、错误状态展示；`AssistantVM` 同步复制两库。删除分组仍沿用合并前确认后删除组内记忆并将助手归私有的实际行为，不以早期过期台账里的“自动搬回私有”改变数据语义。群组页及19个 `_parked/` 文件不动。
- **F009 补充**：`DeepSeekWebToolPolicy` 只读放行 `memory_read / memory_list`；四个文件写入名显式标为 blocked，确保伪调用进入应用拒绝链，而非只在工具清单中消失。`memory_tool` 仍整项拒绝，不新增 solution 跳转；对应 AI parser/生成守卫测试已更新。
- **验证证据**：目录 `artifacts/3.1.0-work.1/stage2-F006-F016-20261021/`。App Kotlin编译成功；AI44类/337项全绿；本地诊断任务9类/92项定向JVM全绿，使用当前生产类和同版本Compose编译插件，不排除、停用或修改普通测试集合。全量测试真实结果仍在 `06-all-tests.log`：App旧 `MemoryImportSourcesTest` / `GroupTest` / `GroupConversationListTest` 编译失败，故不是全仓通过。
- **迁移证据**：`Migration_31_32_Test` 与 `ForkMigration_26_31_Test` 独立编译通过；普通 androidTest 仍受旧 GroupStorage/MessageNodeStats/GroupLayout 测试阻断。`MigrationSqliteFixture` 通过动态数据库适配调用真实迁移，3场景13断言验证空库、多scope/其他文件与元数据保留、幂等和写失败回滚；它是SQLite/JDBC，不替代Android/Room schema校验、真实DB26→32与覆盖安装。
- **最终Debug交付**：`:app:assembleDebug` 成功（1分53秒），唯一ARM64 APK为上述目录的 `RikkaHub.Work-3.1.0-work.1-arm64-v8a-debug.apk`，87,868,689 bytes；SHA-256 `989541dc4f155c8531e01960e23de42bd685491ceed4161b2494e8198da3e923`。包内199/work.1、`me.rerere.rikkahub.debug`、RikkaHub Work名称、SDK26/37、三语、WASM、CRC、V2原Debug证书和应用图标均核验。旧v3交付包与16个其他输出APK未改；无Release/源码ZIP/commit/tag/push/上传，设备未操作。详情在 `DELIVERY.md`、`APK-VERIFICATION.json`、`SCOPE-VERIFICATION.json`。
- **待维护者验收**：玻璃切换/进出/低版本回退；首次记住、删除后的后续对话、手动整理不重复（V1/V2）；三Tab与分组切换；备份恢复两表。未访问设备/模拟器、真实账号或模型，没有截图或真实对话成功证明。第三阶段群组、F010/F014迁移选择器、输入框/浏览器等fork UI仍未完成。
- **恢复与上游重套**：本批源码未提交，无独立commit SHA。修改前快照在本目录 `before/source/`；F006完成后的增量清单在 `after-01-source/`，F016后的在 `after-02-source/`，最终源码哈希及清单在 `after-final-source/`。按清单逐文件反向应用，禁止整树reset/覆盖merge/index；已迁移为DB32的数据不能直接用只认识DB31的旧代码降级，不删数据库或用户文件。升级时重套上述接线、Room手写迁移/两库DI、prompt/tool分工、三Tab及DeepSeek边界。

## [构建约定] 固定最初应用图标（2026-10-21）

- 后续Debug/Release仅保留桌面/系统应用图标及关于页那一个应用标志的最初图像，不再跟随上游替换这两处；规则落在AGENTS的R9，构建前必须核验。其他图标、界面和功能仍按既有合并范围正常同步，不冻结整个关于页或全部图标。
- 基准为`643191229dcbeb4972e3772986818605633ace92`中的`app/src/main/res/mipmap-*/ic_launcher*`共21文件（20个PNG及1个adaptive XML），已核实与合并前备份`12a6ed24356889a024eca2190c208294d6a38b65`完全一致。不能仅恢复普通图标而遗漏前景/背景/单色图标。源码核实：`SettingAboutPage.kt`以`R.mipmap.ic_launcher`读取同一套图标资源，因此两处共用图像；关于页布局/交互/动画、供应商品牌图标、工具图标等不在固定范围，照常同步。
- 2026-10-23 阶段二构建前已仅恢复20张PNG，adaptive XML原本未变；21源文件逐一匹配基准，APK内20张PNG像素及adaptive背景/前景/单色引用均通过。版本不变、无提交/上传；既有v3 Debug保留原样，不把旧包误称为已恢复图标。
- 本条无独立commit SHA；撤销本次记录只需反向应用三份台账的本条增量，不回退整个合并工作区。后续上游同步时重套图标保留规则。

## [合并上游 3.1.0 / 编译修复 v3] 3.1.0-work.1 / 199（2026-10-21，进行中）

- **附件与实际范围**：应用 `RikkaHub Work-3.1.0编译修复v3-20261021.zip`（141,044 bytes，SHA-256 `04bc2894807740fbe9784b71ec79fcaea4142de67d078948ba61371ab94b0aa7`）的36个Kotlin文件，初次全部逐字匹配；22个相对v2有变化，14个原本一致。原包仍有12条诊断/7文件，再显式补漏9文件，最终相对v2共26个源码/资源文件有变化，不能把补漏后的36文件仍称为全部匹配原包。
- **本批变化文件**：app下 `ChatInputHaze`、`CompressContextDialog`、`FilesPicker`、`ProviderQuotaText`、`ToolApprovalPicker`、`ChatMessage`、`LoadingAnimation`、`AssistantPage`、`AssistantVM`、`AssistantLocalToolPage`、`ChatList`、`ExtensionsPage`、`WorkspaceDetailPage`、`WorkspacePage`、`SettingAboutPage`、`SettingPage`、`SettingPreferencesGeneralPage`、`SettingPreferencesUIPage`、`SettingSearchDetailPage`、`ProviderConfigure`、`ProviderConfigureDeepSeekWeb`、`StatsVM`；app三语 `strings.xml`；`ui/src/main/java/me/rerere/ui/webview/WebView.kt`。完整相对路径/hash见本批VERIFICATION/PACKAGE/FOLLOWUP-FILES。
- **附件后补漏**：ToolApprovalPicker、ChatList、WorkspacePage、SettingPage、ProviderConfigureDeepSeekWeb删除已迁移至`:ui`的旧包import；SettingPreferencesUIPage的透明度Slider调用改为既有`formItem`，不改CardGroup组件；三语各新增唯一`chat_page_compress_target_tokens`。九文件before/after hash单列，补漏前内容保存在`after-package/`和`before/`。
- **上游升级需重套**：`:ui` 的 WebViewState 八处 `internal set` 改为公开 `set`，允许`:app`原浏览器页面更新状态。其他组件只改调用方import，不修改`:ui/components/`实现。LIQUID暂并入GLASS/Haze分支，保留序列化字段但不是F006完整重放；AssistantVM.copyAssistant恢复上游`copyMemories: Boolean`文件记忆复制，F010多选仍待后续适配。
- **生产代码验证**：原包编译失败12条/7文件（1分46秒）；九文件补漏后`:app:compileDebugKotlin`成功（4分33秒，128任务：9执行/119复用），带位置错误0、仍有弃用警告。诊断沿革918→93→12→0，仅代表主程序编译收口。
- **单测真实结果**：`:ai:test :app:testDebugUnitTest --continue`的续跑52秒失败于`:app:compileDebugUnitTestKotlin`，233任务中1执行/232复用；AI为UP-TO-DATE，不能称重新执行337项。App测试源码4文件82诊断：GroupConversationListTest31、MemoryImportSourcesTest24、GroupTest18、BackgroundEffectTest9。涉及parked群页、旧记忆scope、selectedGroup及旧原生玻璃函数；App用例未执行，不删/停用测试、不为凑绿恢复有损兼容层，留阶段二/三一起适配。
- **保留边界**：19个parked文件及GroupChatContext内容不变，Git未解决路径和Kotlin冲突标记均为0。与v2相比核心AI/数据/服务/Settings/数据库/API配置不变，F007、F004 HITL/OR、F009只读/常驻错误卡片、watchdog及旧偏好字段不动；app/ui/search仅三语。群组页面、记忆分组/迁移、fork输入框/浏览器入口仍不完整，不得当成整版完成。
- **产物与日志**：`artifacts/3.1.0-work.1/compile-fix-v3-20261021/`保存原包编译、补漏成功日志、App测试失败日志、精确应用/最终范围核验和恢复材料。`:app:assembleDebug`成功（9分31秒，282任务：70执行/212复用），只输出`RikkaHub.Work-3.1.0-work.1-arm64-v8a-debug.apk`，83,563,821 bytes。包名/版本、ARM64-only、debuggable、三语、WASM源码hash、ZIP CRC及沿用的Debug证书均已核验；三个旧Release的hash/大小/时间未变。无新源码ZIP、设备/真实账号操作；旧源码ZIP不是本批源码。Debug与Release包名不同，不能用这个包直接验证现有Release数据，DB26→31与保数据验收仍待同包名旧Debug数据或专门测试副本验证。
- **commit/恢复**：无本批SHA；HEAD仍`12a6ed24356889a024eca2190c208294d6a38b65`，MERGE_HEAD仍固定上游3.1.0的`eeef60b22ba24383daf51f3c6c2e5b75bbab6bba`。不commit/tag/push/上传；按本批`before/`逐文件反向应用，若只撤销九处补漏则对照`after-package/`，不整体reset/checkout、不覆盖原index、不直接移回parked旧页掩盖新接口缺口。

## [合并上游 3.1.0 / 冲突修复 v2] 3.1.0-work.1 / 199（2026-10-21，进行中）

- **范围**：按维护者新版执行指令应用 v2 包的 18 个 Kotlin 文件，逐一 SHA-256 匹配；其中 ChatDrawer / ChatPage / ChatInput / AssistantDetailVM / SettingPreferencesGeneralPage / AssistantMemoryPage 逐字匹配上游 3.1.0 标签。其余为 RouteActivity、BrowserController、ViewModelModule、CompressContextDialog、AssistantPage、BackgroundPicker、BrowserMiniPanel、BrowserPage、WorkspacePage、SettingBrowserCookiesPage、SettingBrowserPage、SettingProviderLoadingAnimation。
- **可恢复暂存**：17 个群组文件由 ui/pages/group/ 移至 `_parked/ui-pages-group/`，原 components/、detail/ 层级保留；MemoryPickerDialog / MemorySourcePickerDialog 移至 `_parked/ui-components/`。19 个文件全部按移动前哈希核验、未删内容。GroupChatContext 原地且内容不变；群组路由定义、数据、核心服务、Room 与配置字段不动。包内示例只匹配顶层 *.kt 会遗漏子目录，实际以其明确列出的17文件为准。
- **底层边界**：相对 v1 的全源码哈希清单核对，仅18覆盖文件、19原路径移动及 CHANGES/FEATURES/AGENTS 三份台账变化；无其他新增源码。SettingsStore 仍为 lambda 更新，MemoryRepository 未新增 row-memory 伪兼容 API；旧液态/动画字段、F007、F004 HITL/OR、F009拒绝和常驻错误卡片、watchdog 与 API 配置均未由 v2 改动。三语言资源集合不变。
- **真实验证**：`:app:compileDebugKotlin --console=plain --no-configuration-cache --max-workers=4` 失败，1分26秒；KSP已完成。918诊断/46文件 → **93诊断/23文件**，Syntax error 110→0；达到200以内的回传目标，但不是可运行版本。128任务中2项执行、126项复用，不冒称重跑基础模块测试。App单测/设备/真实账号/API未执行。
- **剩余问题**：WebViewState 跨模块 internal 可见性；旧 Select/ToggleSurface/Switch 等 import；工作区/搜索页重复声明；LIQUID、DeepSeekWeb、新供应商与群注解的 when 适配；助手页 AssistantMemory 残留、AssistantVM copyMemories 未定义；压缩回调/资源缺口。当前仅定位，未在附件外猜修。
- **日志**：`artifacts/3.1.0-work.1/conflict-fix-v2-20261021/` 保存完整 build-v2.log、93行 errors.log、ERROR-FILES.tsv、APP-COMPILE-ERRORS.json、PACKAGE.json、PARKED-FILES.json、VERIFICATION.json、BUILD-REPORT.md 与移动/覆盖前41文件备份。上一批日志和旧源码ZIP保留，不能当成当前v2源码包。
- **恢复边界**：暂无本批 commit SHA；HEAD 和 MERGE_HEAD 不变，无 commit/tag/push 或新 APK/源码ZIP。移动前内容位于本批 before/，逐文件审核后反向应用覆盖；按 PARKED-FILES.json 一一反向 `git mv -- <to> <from>` 可移回旧页，必须先完成文件记忆/接口适配再加入编译。before-git-index 仅留作取证，不整树覆盖index、不 reset/checkout、不整包解压恢复。群组及记忆UI、审批入口和浏览器入口重放仍待阶段二/三指令。

## [合并上游 3.1.0 / 冲突修复] 3.1.0-work.1 / 199（2026-10-21，进行中）

- 应用 `RikkaHub Work-3.1.0冲突修复-20261021.zip` 的 28 个文件，逐文件哈希与附件一致。包外只补齐 `AssistantPage.kt` / `AssistantBasicPage.kt` 的 import 冲突；不自行猜测修复编译后的接口缺口。
- 移除清单：`SponsorAPI.kt`、`SettingDonatePage.kt`、`:ui` 中的 `DiscordIcon.kt` / `TencentQQIcon.kt`、`LiquidGlassInputBackground.kt`、`README_ZH_CN.md` / `README_ZH_TW.md` 和 `liquidglass/`。模块连同旧 build 目录移至 `artifacts/3.1.0-work.1/conflict-fix-20261021/removed-liquidglass/`，可恢复；其余被移除文件保留在此前源码快照中。清理已为空的八个非支持语言目录，只保留英文、简中、繁中。
- 不得连带删除：`DisplaySetting.liquidCompatMode` / `useAppIconStyleLoadingIndicator`、F007、F004 HITL/OR、后台前台服务与空闲 watchdog、F009 能力拒绝及常驻错误卡片。功能性 API `api.rikka-ai.com` 不变。
- 五个上游占位文件：`ChatPage.kt` / `ChatInput.kt` / `ChatDrawer.kt` / `FilesPicker.kt` / `AssistantMemoryPage.kt`。群组模式、浏览器面板及记忆分组/迁移 UI 尚待阶段二/三；保留附件要求的阶段边界，不恢复旧整页来掩盖接口问题。
- 实际编译/测试日志及剩余报错保存在 `artifacts/3.1.0-work.1/conflict-fix-20261021/`。当前无本批 commit SHA，不执行 commit/tag/push、不生成 APK、不操作设备。冲突清零不是阶段一验收通过。
- 验证结果：ai/ui/search/mediagen 编译通过；AI Debug 单测本次实际执行 44 套件/337 项，0失败/错误/跳过。App 编译失败，918 条带位置的诊断涉及46文件，其中110条 Syntax error（含级联，不是独立缺陷数）；App单测因编译失败未执行。附件“干净上游占位/能编译”的预期与实际混合代码不符，具体证据和完整日志见本批 BUILD-REPORT.md。未擅自修复附件之外的接口缺口。
- 回滚边界：此前 `upstream-3.1.0-stage1-source-snapshot-20261021-100005` 源码 ZIP 含原冲突态文件；按本批文件逐项反向应用，不整体 reset/checkout，不覆盖其余数据层合并。`liquidglass/` 可从上述备份路径恢复；源码 ZIP 不含 Git index，不能把解压当成自动恢复 merge 状态。

## [F013 补充五至七] 2.5.6-work.5 / 198（2026-10-20，本地交付批次）

- 版本基于未公开的 work.4 开发包递增为 `2.5.6-work.5 / 198`；保留工作区中 F013/F014、DeepSeek/AnySearch 与后台修复全部增量。只构建三个签名 Release 与完整源码，不生成 Debug、不执行 commit/tag/push、不上传、不操作设备或真实账号。
- 补充五：`SettingBrowserPage.kt` 新增浏览器工具、敏感过滤、默认显示模式和超时设置；`RouteActivity.kt` / `SettingPage.kt` 路由与入口；`PreferencesStore.kt` 用独立 Preferences 键持久化浏览器开关及超时，避免旧 Settings 快照覆盖站点列表；三语资源一致。
- 补充六：`Settings.browserVisitedHosts` 是唯一新增序列化字段，默认空列表；host 归一化过滤 localhost/IP、合并 www、最多 200 条、5 秒同 host 写入节流。`BrowserDataCleaner.kt` 只读取 Cookie 名称，不暴露值；页面加载完成、浏览器离开、单站和全站清理都 flush。单站删除对 https/http、www、host-only/domain 及 HttpOnly Cookie 尝试设置过期，仍受 WebView 未暴露 Path/Domain 的限制。全站清理保留 host 历史及供应商凭证。
- 补充七：`BrowserController.kt` 将初始缩放归零，并提供只修改 viewport/root 宽度的桌面脚本；`BrowserPage.kt` 仅桌面模式加载后和 300ms 后注入，模式切换重新 load 当前 URL。`BrowserMiniPanel.kt` 收起为 44dp 右下角球，错误保持原卡片并用状态点提醒；保留展开布局，standalone 不渲染面板。
- 测试与实现落点：`BrowserSitesTest` 覆盖 host 去敏、站点归一化、Cookie 名称解析和节流；`BrowserContractTest` 覆盖 URL、UA、风险级别和固定脚本参数。补充五到七还包含 Android WebView/设备级行为，不能由 JVM 用例声称已验收。三语言资源仅 `values`、`values-zh`、`values-zh-rTW`。
- 验证记录：`./gradlew test :app:compileReleaseKotlin --no-daemon --console=plain --max-workers=2` 通过，131 套件 / 870 项、0失败/错误/跳过；离线浏览器脚本夹具 8 项通过。`:app:assembleRelease` 成功且 Release Lint Vital 通过。实际 Android 界面、Cookie 写盘/清除、桌面站点布局及悬浮球遮挡尚待维护者验证。源码保持未提交；没有本批 commit SHA。若未来单独提交，可按文件增量回退，保留 work.4 功能和 Settings 默认值/旧数据兼容。
- 交付目录：`artifacts/2.5.6-work.5/F013-browser-cookies-desktop-release-20261020/`。文件为 `RikkaHub.Work.2.5.6-work.5-{arm64-v8a,x86_64,universal}-release.apk` 和 `RikkaHub.Work.2.5.6-work.5-source.zip`。APK 大小依次 39,486,056 / 40,152,546 / 49,406,154 字节；包名 `me.rerere.rikkahub.plus`、versionCode 198、非 debuggable；三个包证书 SHA-256 均为 `671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b`，ABI、三语、WASM 均核验。源码 ZIP 含 1924 文件及子模块、逐文件匹配工作区；源码 SHA-256 和 APK 校验值见同目录 `SHA256SUMS` / `SOURCE-VERIFICATION.json`。Debug APK 未重建且 SHA-256 未变化。源码包排除 `.git`、构建缓存、artifacts、本地配置与密钥。

## [F013 / F014] 2.5.6-work.4 / 197（2026-10-15，本地开发）

- 基准 `b2625af5d968b5691030b00407075eff9f79b365`，保留全部 work.3 功能；本批不提交、不打标签、不推送，不访问真实账号或设备。仅三架构 Release，尚未发布。
- F013 新增 `browser/{BrowserController,BrowserJsBridge,CredentialFilter,BrowserHighRisk,ReadabilityRunner}.kt`、`data/ai/tools/local/BrowserTools.kt`、`ui/pages/browser/{BrowserPage,BrowserMiniPanel}.kt` 和两份 Readability 资产/许可证。固定脚本不暴露任意 JS；按对话隔离；过滤在工具返回和手动投喂边界执行；JS/Kotlin 双层高危拒绝；不接受审批覆盖硬拒。
- 上游升级需重套：`RouteActivity` 的 Screen.Browser 与 backStack 后事件收集、`AppEvent.OpenBrowser`、`AppModule` 注入、`LocalToolOption`/`LocalTools` 装配、`ChatToolFactory` 的 conversationId 默认参数、`ToolRiskTiers` 的八条显式等级、`ChatService` 单聊传参与群组排除、`AssistantLocalToolContent(showBrowser)`、`GroupLocalToolPage(showBrowser=false)`、PreferencesStore 过滤偏好及带 false 默认值的 browserDesktopMode 读写、三份语言资源。既有 F004 OR/HITL 和 F009 只读守卫不变。
- 浏览器专用 WebView 宿主复用 WebViewState，不修改官方 WebViewPage/WebViewContentCache，也不沿共享 WebView 的固定 URL 更新路径反复跳回初始地址。每次调用执行应用固定函数，不常驻公开 JS bridge；模式切换同实例 reload。迷你面板复用 ChatService、Markdown、思考/工具和错误卡片，渲染偏好仅局部覆盖，不写全局配置。
- F014 新增 `ui/components/ui/MemorySourcePickerDialog.kt`，在同文件共用 UI 导入编排及去重 helper；修改 AssistantMemoryPage / GroupMemoryPage 的导入入口。数据层零改动。任务书示例的 repository.getAllMemories 实际不存在，直接注入既有 MemoryDAO 一次查询；反向来源标题用一次 getGroupConversations(ids).first() 获取，不做 N 次查询。
- F014 上游重套：AssistantMemoryPage 的回调参数和并列导入按钮、三份资源；群页/新组件为 fork 独有。原 MemoryPickerDialog、MemoryGroupSelector、AssistantDetailVM、MemoryRepository、MemoryDAO、Room26 均保持不变。
- 自动验证包含 CredentialFilter / BrowserHighRisk、URL/UA/八工具分级/脚本参数，以及记忆来源去重/空桶/会话级作用域测试。实际结果和手机待验清单随本批交付记录；不能把 JVM 或脚本夹具写成真实 WebView/供应商实测。
- 提交定位：当前未提交，无本批 commit hash。后续取得独立提交后用 `git revert <F013或F014提交SHA>` 回退；当前按以上文件逐项反向应用工作区增量，不整文件 checkout/reset，不回退 work.3。为兼容已存设置，browserDesktopMode 新字段/default 建议保留并停用入口，不能因回退破坏既有字段。


## [F013 搜索补充 / 后台修复] 2.5.6-work.4 / 197（2026-10-19，本地 Release 已构建，尚未发布）

- 基于上方尚未交付的 F013/F014 增量追加；不提交/打标签/推送/上传，不访问真实服务商、账号、设备或模拟器。统一交付三架构签名 Release 和完整源码。
- DeepSeek：SearchService.kt 追加 DeepSeekOptions（serial=deepseek）、getService 和 TYPES；新 DeepSeekSearchService.kt、DeepSeekSearchServiceTest.kt，设置编辑器和 app/search 三语资源。Search/Fetch 同类型，summary 优先，need_content=false，250ms 请求起始节流。
- AnySearch：随后在上述三个注册点追加 AnySearchOptions（serial=anysearch）；新 AnySearchService.kt、AnySearchServiceTest.kt，设置编辑器和三语资源。匿名无 Authorization，带 Key 不降级，结果数 1～10，Extract 取正文。
- 源码核实偏差：原 TYPES 实际为 20 项（含 Custom JS），不是任务书所写 19 项；保留原有 20 项并按 DeepSeek、AnySearch 顺序追加。AnySearch Description 属 search 模块，官网文案放该模块而非 app，避免反向依赖；英文描述改为无撇号的同义表述。新响应解析不保留可能带 Key 的原始异常；取消异常继续传播。
- 后台改动文件：AndroidManifest.xml、ChatGenerationForegroundService.kt、ChatService.kt、RequestLoggingInterceptor.kt、LogPage.kt、common/android/Logging.kt；新增 service/GenerationWatchdog.kt、common/android/RequestTiming.kt 及对应测试。25 分钟非引用计数 WakeLock 随任务续租/释放；20 分钟 elapsedRealtime watchdog 仍是原 Job 子协程，先报错再取消；请求活动耗时/休眠时长分开记录。
- 验证：全模块 test 成功（5分29秒），130 套件 / 865 项，0 失败/错误/跳过。新测试增加32项：DeepSeek10、AnySearch9、SearchTools2、watchdog4、日志4、时钟3；改动模块实际执行，未变报告可能 UP-TO-DATE。设备/真实搜索/锁屏与前台锁验收仍待维护者完成。
- 交付：`:app:assembleRelease` 成功（19分35秒），Release Lint Vital 通过；三个 APK 已按 197/work.4、包名、非 debuggable、ABI、三语、WASM 及 work.3 证书核验。完整源码 ZIP 含 1918 个文件并逐文件匹配当前工作区；产物位于 `artifacts/2.5.6-work.4/F013-F014-search-background-release-20261019/`，尚未上传 GitHub。
- 上游重套：SearchService 的两类及注册、SettingSearchDetailPage 的两编辑器、三语；共享生成入口 watchdog、前台服务锁生命周期、manifest、日志模型/拦截器/显示及对应新文件。保留 DEFAULT、原服务商与 await，GenerationLoop、provider、原 timeout、Room26 不改。
- commit：当前未提交，无本批 SHA。独立提交后可 git revert 对应提交；现在仅逐项反向应用增量，不整文件 checkout/reset，不覆盖此前 F013/F014 或 work.3。新增持久化类型已保存后不得改 SerialName。

## 本次发布状态摘要（2026-10-14）

| 项目 | 当前状态 |
| --- | --- |
| F006 液态玻璃 | ✅ 方案乙修复；维护者确认2026-10-12崩溃回归/性能/兜底三组实机压测通过，原Phase 0复核项已归档 |
| F012 群组与DeepSeek续答修正 | ✅ 完整实现并随2.5.6-work.3/196正式发布；自动检查通过，设备/真实供应商验收边界保留 |

## [2.5.6-work.3] 正式发布与源码同步（2026-10-14）

- **发布事实**：维护者已发布Release，2026-10-14 10:11北京时间，非草稿/非预发布；三个公开APK的名称、大小、SHA-256均与本地签名包一致。当前源码提交包含完整F012、共用Rootfs回退、群内昵称/头像、DeepSeek Chat Completions/Responses推理回传修正。
- **源码基准**：本次提交父节点为远端master `6b7f8ee7f94d1f23267bf53105d885c74b5ab968`，其README修改已快进保留；版本为196/work.3。下方各批“未提交/不推送/待发布”是开发或打包时的历史，本节覆盖当前交付范围。
- **提交定位/回滚**：完整源码提交标题为 `feat: complete F012 groups and DeepSeek continuation fixes (2.5.6-work.3)`；同步后的SHA以 `git rev-parse '2.5.6-work.3^{commit}'` 或同标题提交为准。撤回整个F012版本使用 `git revert <本次源码提交SHA>`，只撤回部分功能仍须按各批增量和持久化兼容边界操作；不删除数据库或用户文件，不整树reset。
- **台账同步**：CHANGES已记录全部最终行为和正式发布，AGENTS更新当前版本及下一版≥197；本节及F006同步维护者2026-10-12实机反馈。压测清单/开发文档本身不入仓库，未补造设备型号、帧率或功耗测量值。
- **同步范围**：完整源码与三份台账；README沿用远端已更新版本，APK不重传，artifacts/密钥/凭据/缓存不提交。tag原指旧fedf4d44（work.2），只对2.5.6-work.3用明确旧值保护更新，master保持快进。
- **提交前检查**：全模块 `./gradlew test --offline --no-daemon --console=plain --max-workers=2` 成功（38秒），286项任务均UP-TO-DATE，复用121套件/818项全绿报告，0失败/错误/跳过，未重执行测试或生成Debug包。功能代码和Room26 schema逐文件SHA-256与Release构建基准一致；仅三份台账及远端两份README变化，包体/Debug输出未变。`git diff --check`通过，artifacts/签名和本地配置不入提交。

## [2.5.6-work.3] 三架构签名 Release 本地交付（2026-10-14）

> 以下为源码同步前的本地打包记录；维护者随后已发布，当前状态以上方正式发布段为准。

- **授权/状态**：维护者要求生成ARM64/x86_64/Universal三个Release与完整更新日志，不生成Debug、暂不上传源码；三个本地Release已生成并核验，未发布/未提交/未打tag/未push，无设备或真实账号操作。覆盖历史各批Debug-only限制，不改功能设计。
- **版本/签名**：2.5.6-work.3 / 196，me.rerere.rikkahub.plus，使用已配置Release签名；现有work.2(195) APK已核实，最终三个包逐项对照证书，不能使用Debug证书。
- **代码基准**：与最终F012-workspace-rootfs-rollback-debug完整源码1887文件哈希一致；本批仅同步AGENTS/CHANGES/FEATURES和发布说明，无功能代码变化。下方分期历史记录保留，最终行为为完整F012+群内身份+独立环境回退+DeepSeek双协议修正。
- **目录/资产**：artifacts/2.5.6-work.3/F012-release-20261014/，RikkaHub.Work.2.5.6-work.3-{arm64-v8a,universal,x86_64}-release.apk；完整用户更新日志RELEASE-NOTES.md，构建/测试/包体/哈希证据同目录。
- **验证边界**：只构建Release并验证正式变体/签名与包体，保留已有818项测试和设备待验边界；既有Lint44错误/249警告/3提示不因打包标通过。DeepSeek进一步挂起排查不扩展实现，不能承诺所有端点已修复。
- **最终验证**：`:app:assembleRelease`成功（11分54秒），Release Lint Vital通过。三个APK的196/work.3、包名、三语应用名、ABI、非debuggable、WASM、ZIP CRC、V2签名及work.2证书一致全部核验；分别39,335,821/49,255,923/40,002,311字节（ARM64/Universal/x86_64）。
- **测试任务说明**：工程没有testReleaseUnitTest，选择失败日志保留；改跑testDebugUnitTest，仅测试代码、不产Debug APK。功能源码未变，本轮44秒任务检查复用121套件/818项全绿报告，0失败/错误/跳过；不能写成Release变体测试或全部新执行。原Debug APK时间与哈希不变，1887源文件仅三份台账变化，未生成源码ZIP。SHA256SUMS及DELIVERY同目录，R8-MAPPING仅供本地排错。
- **回滚/上游**：本次只修改三份台账，不含独立功能提交；按本批文档增量反向修改即可，功能代码及上游重套清单沿用前批。GitHub发版后才能更新“已发布”和下一版约束，禁止现在伪造发布状态。

## [F012 群内昵称与头像] 与工作区回退同批（2026-10-13）

- **版本/范围**：`2.5.6-work.3 / 196`，基于 `F012-workspace-group-only-debug` 的未提交工作区；与共用 Rootfs 回退一起输出到 `F012-workspace-rootfs-rollback-debug/`，名称带 `rootfs-rollback-group-profile`。只 ARM64 Debug/完整源码，不 Release/commit/tag/push、不操作设备/真实账号/API。
- **模型/匹配**：Group.kt 的 GroupMemberRef 加 nickname=""/avatar=null 并 trim；GroupChat.kt 末尾加 memberRefOf 两个重载与显示解析，toAssistant 使用群内覆盖，extractMentions 可选 aliases 默认空保持旧行为，speakingMembers/resolveDispatch 传群内别名。复制、排序、归一化沿原 copy 保留字段，不改全局 GroupMember 或持久化层。
- **生成/历史显示**：ChatService 用群内名生成 framePrompt、临时助手、通知及新 GroupSender。源码 withGroupSender 优先本名，所以仅以临时 member.copy(name=群内名) 传入，函数本体/签名及 withGroupSpeakerPrefixes/withSpeakerPrefix/stripSelfSpeakerPrefix 完全保留。ChatPage 的 chatContent 只渲染单聊，故在 AnimatedContent 的共同 body 用新 GroupChatContext 提供 group/null；不新增布局。ChatMessage 气泡及工具提问卡实时解析当前群覆盖，不写历史；清空昵称回落本名，群/引用已失效保留注解历史名。
- **UI**：GroupConfigPage 成员行解析群内名/头像；GroupConfigDialogs 昵称草稿+保存，头像用现有 UIAvatar 即时提交；最新 ref/本地头像状态避免头像回调或保存覆盖新头像，未保存昵称不会被头像选择提前提交。ChatPage 的 @ 候选用临时名字快照，不修改组员库或 GroupMentionCompletionProvider。三语仅新增2个 hint key，group_next_phase 保留。
- **测试/申报**：新增 GroupMemberProfileTest 覆盖老 Settings JSON/新字段序列化、群间隔离、历史回落/失效引用、消息保存昵称与 reasoning parts 顺序、别名长名/同名/空名、enabled/副群主调度、排序/复制/归一化保留。上游共享文件实际申报为 ChatMessage.kt、ChatPage.kt、ChatService.kt；新 GroupChatContext.kt 与 fork Group.kt/GroupChat.kt/GroupConfigPage.kt/GroupConfigDialogs.kt 同步维护，不因任务书的“上游共享”称谓而误认为它们已在 HEAD 跟踪。
- **偏差与回退**：源码只有2个实际需要 aliases 的 extractMentions 调用，非任务书所述3处；历史清空昵称时不能沿用注解旧名，因此显示层额外回落当前全局名。本批未提交，未来独立提交后 `git revert <本批独立提交 hash>`；当前只逐项反向应用 profile 差异，保留工作区回退及其余 F012，新增持久化字段建议保留并停用 UI，不能整文件 reset/checkout。
- **验收边界**：JVM/编译与手机操作分开，逐项19条验收写本批 DELIVERY。手机昵称输入、头像裁剪、跨群显示、长按/工具提问、单聊像素及覆盖安装待维护者执行；不把单测或设备测试代码编译写成已装机验证。
- **本批自动验证**：最终全模块 Debug JVM 为121套件/818项，零失败、零错误、零跳过；ARM64 Debug 与设备测试代码编译通过（3分9秒）。新增群内身份7项/独立工作区6项；取消安装测试仅使用127.0.0.1夹具，无外部服务/真实账号。对照前批1888个文件，24改/3增/4删，当前完整源码1887文件；其余文件哈希一致。APK 的2.5.6-work.3/196、Debug包名、ARM64-only、相同Debug签名及WASM已核验，ZIP和Lint证据随本批交付，手机验收未执行。
- **最终Lint/封装**：44 errors / 249 warnings / 3 hints（8分17秒），全仓未绿；44个既有错误与前批逐项一致，无新增错误。唯一新增警告为按任务书要求保留的 group_next_phase 未使用，不删文案/加baseline/屏蔽。完整源码1887文件（含子模块385文件）与当前工作区逐个哈希一致，ZIP CRC/gradlew执行位通过；源码内台账更新后重新校验与封存，设备未执行。

## [F012 二期-F 回退] 移除共用 Rootfs、恢复详情页安装（2026-10-13）

- **基准/产物**：基于 `F012-workspace-group-only-debug` 的完整源码增量，保持 `2.5.6-work.3 / 196`、HEAD `f79210c1`；仅 ARM64 Debug 与完整源码，目录 `artifacts/2.5.6-work.3/F012-workspace-rootfs-rollback-debug/`。下方共享及列表安装章节是历史，当前行为以本节为准。不 Release/commit/tag/push，不操作设备或真实账号/API。
- **环境回退**：删除 WorkspaceRootfsRegistry 和 WorkspaceManager 的 rootfsOverride，linuxDir 恢复自身 root/linux；撤除 RepositoryModule 注册/注入、共享创建参数/快捷方法、DAO 的 detach/事务删除以及终端共享判断。Repository 保留生命周期锁、取消恢复、目录/文件工具、setOwner 和删除时清理助手/群组绑定。
- **旧数据**：checkIntegrity 开头将 sharedRootfsFrom 非 null 的记录清为 null、状态 DISABLED，再读列表做原完整性检查，第二次启动不会再修改这些记录。不删私有文件/源环境，不改 owner/绑定；原完整性检查仍将实际缺失物理目录的工作区标为 BROKEN。数据库26、26.json、WorkspaceEntity 所有字段/default 保持。
- **UI**：WorkspaceCreateButton/WorkspacePage 去掉复用选择和来源，create 恢复 name/owner；所有归属详情显示 Shell 卡。RootfsProgress/InstallRootfsDialog/默认 URL 原样搬回 WorkspaceDetailPage private，删除 WorkspaceInstall。WorkspaceVM/列表撤除安装状态、方法、菜单、进度和失败对话框，保留保存错误与归属流程。WorkspaceDetailVM、兼容/审批/文件/顶栏/settingsError 不动。
- **归属保留**：WorkspaceOwnerScope.includes() 和 RouteActivity/FilesPicker/GroupConfigPage/ExtensionsPage 的 scope 接线完全保留。选择器每行增加状态与归属拼接，复用 owner 三个 key；绑定不改归属。三语各删5个 workspace_shared_* 和 workspace_detail_shared_environment，不新增 key/语言。
- **删除文件**：app/data/repository/WorkspaceRootfsRegistry.kt、app/ui/pages/extensions/workspace/WorkspaceInstall.kt、app/test/data/repository/WorkspaceSharingTest.kt、workspace/test/SharedRootfsTest.kt。新增 WorkspaceBasicTest 覆盖独立创建、归属校验、旧共享一次性清理及保留文件/绑定/源、独立安装路径、删除解绑和并发同名创建。
- **任务书偏差**：GroupStorageTest 的共享删除测试直接调用被删除 DAO 方法，因此仅此用例更新为纯删除互不影响；其余存储测试与 WorkspaceMigrationTest 完全不变。生产 sharedRootfsFrom 仅在实体字段和历史数据清理出现，不保留共享机制。
- **上游升级需重做清单**：RouteActivity.kt、WorkspacePage.kt、WorkspaceVM.kt、WorkspaceDetailPage.kt、WorkspaceSelectSheet.kt、FilesPicker.kt、GroupConfigPage.kt、ExtensionsPage.kt、WorkspaceCreateButton.kt、WorkspaceRepository.kt、WorkspaceDAO.kt、WorkspaceTerminalSessionManager.kt、RepositoryModule.kt、workspace/WorkspaceManager.kt。Manager/DAO/DI 的共享增量已撤回，后续仍保留独立环境、归属和清理规则；原 F012/DeepSeek 其他共享改动名单继续有效。
- **commit/回滚**：本批未提交，不能 git revert 当前 HEAD。今后独立提交后可 `git revert <本批独立提交 hash>`，当前仅对照上一批源码 ZIP 逐项反向应用本批差异并恢复4个移除文件，不整文件 checkout/reset。代码回退不会恢复已清除的共享列/状态，恢复旧共用行为需应用启动前另有数据库备份；不能擅自猜测旧来源或批量重绑。私有文件和源 Linux 环境未删除。
- **验证边界**：JVM、Debug 构建及设备测试代码编译结果随本批交付说明记录；手机覆盖安装、实际下载/PRoot 和界面交互由维护者验收，未执行不标通过。

## [F012 F-1 补充二·修订版] 群组安装入口与管理页过滤（2026-10-13）

- **版本/范围**：`2.5.6-work.3 / 196`，HEAD `f79210c1`，基于前批 `F012-workspace-owner-debug` 完整工作区增量修改。以补充二第2版“群组专属”修订为准，取代尚未交付的全模式迁移。仅 ARM64 Debug 和完整源码，目录 `artifacts/2.5.6-work.3/F012-workspace-group-only-debug/`；保留前批，不 Release/commit/tag/push，不操作设备或真实账号/API。
- **路由/过滤**：RouteActivity 的 Screen.Workspaces 改为默认 scope=null 的可序列化数据类，entry 传 scope。FilesPicker 按 groupMode 传 chat/group，GroupConfigPage 传 group，ExtensionsPage 不传。WorkspacePage 在页面按 owner=null 或 owner=scope 过滤，全量 VM flow 不变。新建 initialOwner 跟随 scope；名称去重、复用源及来源名称使用全量列表，避免过滤后重复名称或跨归属来源失踪。WorkspaceOwnerScope/两处选择器过滤保持原样。
- **安装**：WorkspaceInstall.kt 原样承接 RootfsProgress、InstallRootfsDialog、DEFAULT_ROOTFS_URL，仅改 internal，两处共用。WorkspaceVM 承接 Pair<workspaceId, progress>、错误及方法，仅接收群组独立工作区；compareAndSet 预留单安装槽，关闭对应终端，取消重抛、finally 清槽。菜单仅 owner=group && sharedRootfsFrom=null 显示安装/重装，顺序重命名→设置归属→安装→删除；安装时全部列表安装项置灰，对应卡片显示进度，失败弹窗在管理页。
- **聊天/未归属**：WorkspaceDetailPage 只在 owner=group 隐藏整张启用 Shell 卡；chat/null 保留原卡、按钮、进度、对话框和失败弹窗，WorkspaceDetailVM 安装四件套与前批等价恢复保留。信息卡仍显示共用环境；settingsError、兼容模式、审批、文件页与顶栏不改。既有非群共享记录仍显示来源并禁止独立安装，不迁移、不解除绑定。
- **共享群组限定**：Repository.create 只恢复 group-only require，其他 F-2 来源检查及 createGroupWorkspaceFor 不改。WorkspaceCreateButton 仅 GROUP 显示/预选来源；管理页新建只有 owner=group 显示/预选，切回 chat/null 清源、提交也传 null。手动选择保护保留；未归属或聊天创建独立环境。setOwner 仍只改 owner/updatedAt，不改既有共享引用、文件或绑定。
- **改动与上游重套**：RouteActivity.kt、FilesPicker.kt、GroupConfigPage.kt、ExtensionsPage.kt、WorkspacePage.kt、WorkspaceVM.kt、WorkspaceDetailPage.kt、WorkspaceCreateButton.kt、WorkspaceRepository.kt 及新 WorkspaceInstall.kt；保留 WorkspaceDetailVM、WorkspaceOwnerScope、WorkspaceSelectSheet、AssistantBasicPage 的前批实现。WorkspaceRouteTest 验证含 scope 恢复、无参数默认全部和返回来源范围；WorkspaceSharingTest 改为群组共享/非群拒绝，并新增旧非群共享数据保留回归。Room26、schema、Registry/Manager、DeepSeek、审批、版本和三语资源均不改。最终自动检查及手机验收边界见本批 DELIVERY/报告。
- **自动验证**：121 个 Debug JVM 套件共 817 项全部通过，较前批新增两项路由恢复/返回测试和一项旧非群共享记录兼容测试。ARM64 Debug 构建及设备测试代码编译通过；APK 为 RikkaHub Work / me.rerere.rikkahub.debug / 2.5.6-work.3 / 196，ARM64-only、WASM 及与前批相同的 V2 Debug 签名核验通过。对照前批 1886 文件仅 13 项修改、2 项新增，无删除；详情 VM 完全一致、Repository 仅加回群组共享 require，其他源码与三语保留。设备测试未执行，Lint 结果单独记录。
- **最终 Lint**：44 errors / 248 warnings / 3 hints，与上一交付相同；按规则、说明、文件和列号对照全部错误，无新增错误。全仓未通过，不增加 baseline 或关闭检查。最终分析耗时13分54秒，原始报告/日志和 LINT-COMPARISON.json 随本批交付；设备、实际下载安装和供应商验收未执行。
- **commit/回滚**：本批未提交。对照前批 workspace-owner ZIP 逐项反向应用上述增量，将共用安装组件搬回详情，移除新文件与路由测试；保留既有功能、三语、schema26 和数据，不能整文件恢复到 HEAD。回滚到前批会重新允许非群共享创建，须明确这一行为差异。未来独立提交后可 git revert <本批 commit>，当前无 hash。

## [F012 F-1 补充] 工作区归属入口与默认共用环境（2026-10-13）

- **版本/范围**：`2.5.6-work.3 / 196`，HEAD `f79210c1`，在前批 DeepSeek/F-1/F-2 工作区上增量修改。仅 ARM64 Debug 与完整源码，目录 `artifacts/2.5.6-work.3/F012-workspace-owner-debug/`；不 Release/commit/tag/push，不操作设备或真实账号/API。
- **归属**：WorkspacePage 卡片菜单新增设置归属，顺序为重命名→设置归属→删除。管理页新建默认未归属，可选聊天/群组。WorkspaceRepository.setOwner 只改 owner/updatedAt，绑定及文件保留；已绑定实例切换归属时显示说明。WorkspaceVM 增加 create 参数与 setOwner、保存忙碌/错误状态，失败不关闭弹窗。选择器过滤、owner nullable 默认与 Room26/schema 不改。
- **共享**：删除 create 中限 group 的单条 require，其余 F-2 源有效性校验保留。管理页/助手页/聊天及群组 sheet 新建均默认首个 READY 独立源，可显式选独立；选完后列表更新不覆盖选择。无 READY 源默认独立，显式源失效时阻止保存并提示。WorkspaceDetailPage 在共享实例完全隐藏启用 Shell 卡，将共享来源/代价放到工作区信息；非共享安装、重装及进度保留。
- **三语**：“未归属”去括号，新增 workspace_set_owner、workspace_page_owner、workspace_owner_binding_notice、workspace_detail_shared_environment。绑定说明在任务书正文有要求但 key 表未列出，单独提供三语 key；未新增其他语言或依赖。
- **改动/上游重套**：WorkspacePage.kt、WorkspaceVM.kt、WorkspaceDetailPage.kt、WorkspaceRepository.kt 是共享文件；fork 自有 WorkspaceCreateButton.kt、WorkspaceSharingTest.kt、三份 strings.xml 与三份台账/约定同步。测试覆盖各归属共享创建、独立环境、修改归属后选择器与既有绑定/文件/环境保留，以及放宽归属后无效源仍拒绝。最终构建/测试/lint与手机待验边界见本批交付说明。
- **自动验证**：814 项 JVM 测试、120 套件零失败/错误/跳过，较前批增加 3 项；ARM64 Debug 构建、设备测试代码编译及 APK 版本/三语/同签名核验通过。对照前批 1886 项源码仅本批 12 文件变化，未删除文件，schema/owner 默认/选择器过滤及原 DeepSeek/审批不变。设备与真实供应商未测试，Lint 最终结果独立记录。
- **最终 Lint**：44 errors / 248 warnings / 3 hints，与前批错误规则、说明和位置指纹完全一致，无新增错误，全仓未通过；不加 baseline/屏蔽。最终组合命令 12 分 4 秒，仅 lintDebug 失败，测试/构建/设备测试编译成功，原始报告与日志随本批交付。
- **commit/回滚**：未提交。对照 `F012-DeepSeek-F-debug/*-source.zip` 逐项反向应用本补充，不整文件恢复丢失前批；持久化 owner/shared_rootfs_from 和 schema26 保留。将共享再次限制为群组前须处理本批已创建的聊天/未归属共享实例，不能盲目添加旧 require。

## [DeepSeek 修正 / F012 F-1 / F-2] 推理回传、群工具与共享环境（2026-10-13）

- **版本与交付**：`2.5.6-work.3 / 196`，HEAD `f79210c1`，保留全部未提交的一期、A/B/C/D/C+1/E 改动。维护者要求“一次性交付”，覆盖随包 F-1/F-2 分包建议；全部完成后只生成一个 ARM64 Debug APK 和完整工作区源码 ZIP，目录 `artifacts/2.5.6-work.3/F012-DeepSeek-F-debug/`。不 Release、commit/tag/push，不操作设备或真实账号/API。
- **DeepSeek**：共享 `ChatCompletionsAPI` / `ResponseAPI` 修工具续答缺失或空 reasoning；DeepSeek host/model + 实际请求含 tools + 思考启用时补空 `reasoning_content` / 明文 reasoning item。显式自定义 `thinking.type` 优先于能力/思考等级；没有自定义时要求 REASONING 能力和非 OFF，满足任务书的“非思考不变”。F007 历史回传保留；DeepSeek 禁密文，OpenAI 密文优先行为不改；不新增群模式判断，不重排群前缀 parts。
- **F-1 数据与工具**：Group 追加 workspaceId=null、enableRecentChatsReference=true、enableWebSearch=true、enableTimeReminder=false、timeReminderIntervalMinutes=60，toAssistant 完整转发且明确两个会话级覆盖开关为 false。新增 Assistant 属性反射守卫。ChatToolFactory 追加默认 assistant.id 的 conversationScopeAssistantId，群显式传 null；ConversationTools 接可空 ID，recent_chats 在 SQL LIMIT 前排除群聊，既有 FTS 群过滤保留。群轮次的工具与 GenerationLoop 均传当前会话 cwd；记忆仍走 `grp:<groupId>:<conversationId>`，审批与 F009 只读限制保持。
- **Room**：WorkspaceEntity 追加带 SQL NULL/default-null 的 owner 与 shared_rootfs_from，数据库 25→26 自动迁移并导出 26.json；F-2 不再升级 schema。旧工作区 owner=null、两种选择器都可见；新建聊天/群组工作区分别 owner=chat/group；管理页新建保持未归属。
- **F-1 界面/绑定**：复用 WorkspaceSelectSheet、助手页 Select 和三语；新增 WorkspaceCreateButton 复用现成弹窗，支持重名/忙碌/错误处理。群设置有工作区、搜索、聊天引用、时间提醒及正整数间隔。附件菜单的群工作区/CWD与文件补全单独接线，群绑定不修改单聊助手。CWD 只更新数据库对应列并核查群占位助手，切换绑定清理群内会话 CWD；不会把轻量会话写回覆盖消息。删除工作区清理助手与群绑定，启动按有效 ID 清洗群绑定；normalizeGroupData 保持纯函数、未传 ID 集时保留绑定。
- **F-2**：WorkspaceManager 仅新增可选 rootfsOverride 与 linuxDir 解析；注册表只依赖 baseDir、AtomicReference 映射 source ID→source root，拒绝失效源、自引用、循环/链式共享、非法源路径。群新建可选 READY 独立源，立刻共享、不调用下载/解压；只创建自身 files/tmp，落库后刷新解析表，不生成私有 Linux 环境。共享工作区隐藏安装按钮，repository 强制拒绝安装。管理/详情显示来源；Python/pip 与 rootfs 内 `/tmp`、`/var/tmp` 共享，`files/`（`/workspace`）和 PRoot 临时目录各自独立，未新增全局 `/tmp` bindMount。
- **F-2 生命周期加固**：create/share/install/delete 和文件/命令操作复用生命周期锁；数据查询/流刷新解析表防冷启动漏初始化。源删除在 Room 事务内清空依赖的 sharedRootfsFrom、标 BROKEN，保留群私有文件与绑定；删群只删除自己的物理 root，绝不沿 linuxDir 删源。无效共享不能静默退回私有 Linux。安装取消恢复原状态。内置终端不再硬编码自己的 Linux 路径，统一用 manager；源失效/重装时关闭相关终端。SAF 仅创建 files/，即使共享环境失效仍可管理私有文件。启动清理经 repository 初始化映射，未改变共享 `/tmp` 的设计。
- **实现差异/上游重套**：本地助手页实际用 Select，保留它并新增创建入口；既有 WorkspaceSelectSheet 原无新建入口，补齐。内置终端硬编码目录不在任务书估算中，但必须修正才能实际使用共享环境。新增事务 DAO 清洗与可测试的 Settings 更新回调，Koin 使用具名参数避免构造重载歧义。上游重套清单包括两条 OpenAI 协议构造、Group/GroupChat、ChatToolFactory/ConversationTools/ChatService、ConversationDAO/Repository 的查询与 CWD 单列、WorkspaceEntity/AppDatabase/schema、WorkspaceDAO/Repository/Registry、RepositoryModule/AppModule/RikkaHubApp、WorkspaceManager、两种选择器/创建、FilesPicker/ChatPage、WorkspaceDetailVM/DetailPage/Page/TerminalSession/TerminalSessionManager、WorkspaceDocumentsProvider 与三语。
- **验证与边界**：JVM 覆盖双协议/双流式/两种说话人消息、多轮/并发工具、空/缺推理、其他厂商/无工具/关闭思考隔离、自定义 thinking；共享测试走实际 repository 和临时磁盘，核查离线建立、ID与root不同、文件隔离、包共享、禁止安装、删群保源并跑本机 shell 命令、删源 BROKEN/保文件、冷缓存与并发重名。新增 Room 迁移、recent 查询/CWD与源删除事务设备用例仅编译，手机 PRoot/pip、覆盖安装和供应商端点仍待维护者实测。最终统计与 lint 对照记本批交付说明，不用编译冒充实机验收。
- **自动测试**：最终全模块 Debug JVM 报告 811 项、120 套件（相对前批增加 18 项），零失败/错误/跳过；ARM64 Debug 构建、设备测试代码编译及 APK 版本/三语/同签名核验通过。离线执行 Room 生成的 25→26 SQL 保留八张表旧记录；15→26 仅核查结构迁移链与空消息夹具，不代替手工工具消息转换或设备迁移实测。
- **最终静态检查**：完整 Lint 44 errors / 248 warnings / 3 hints，与前批错误规则、说明及位置指纹相同，无新增错误；全仓 Lint 未通过，未新增 baseline/屏蔽规则。最终组合命令耗时 16 分 2 秒，仅 `:app:lintDebug` 失败；测试、APK 构建和设备测试代码编译均通过，原始日志及对照报告保存本批交付目录。
- **回滚**：未提交，commit hash 待授权。按前批 `F012-BCD-C1-E-debug/*-source.zip` 逐文件反向应用本批，保留已有全部 F012 与 F001–F011；持久化新增字段/default 与 schema26 留存，先退 UI/工具/共享接线，不删除已存数据、不做数据库降级。需旧版安装时先自行备份，不保证 schema26 可被旧 APK 读取。未来独立提交后可 revert 该提交，禁止 reset/整文件 checkout。

## [F012 BCD 修正 / C+1 / E] 会话列表、副群主调度与上下文压缩（2026-10-12）

- **版本**：`2.5.6-work.3 / 196`，HEAD `f79210c1`，基于上一批未提交的 B/C/D 工作区。仅 ARM64 Debug 和完整源码；本轮无 commit/tag/push/Release/设备操作。
- **BCD 修正文件**：`GroupDrawer.kt` / `ChatDrawer.kt` 改为当前群会话和独立群选择器；`GroupVM.kt` / `GroupChat.kt` 默认会话名与编号；`ConversationDAO.kt` / `ConversationRepository.kt` 新增标题单列更新、复用置顶单列更新，消息永不由轻量列表对象写回。`GroupChatContent.kt` 删除重复弹窗；`GroupComponents.kt` / `GroupDialogs.kt` / `GroupMemberPage.kt` 修必填文案和胶囊宽度；`ChatMessage.kt` 群模式整行手势、0.7 阻尼、110dp 上限，单聊不变。
- **Responses 修正**：`ai/.../openai/ResponseAPI.kt` 传 capability 到消息构造，DeepSeek 明文历史和 include 守卫、空 reasoning 过滤。`ResponseApiRequestMessageTest.kt` 回归明密文同时存在与空 reasoning；OpenAI 加密路径保留，`ChatCompletionsAPI.kt` 未改。真实端点是否返回明文需维护者验证，不声称已联网通过。
- **C+1 文件**：`Group.kt` +默认 false 的 enableDeputyDispatch；`GroupChat.kt` 可选 deputyFirst、复用 extractMentions 的姓名/标签解析和点名权重排序；`GroupGenerationRound.kt` 升级 GroupTurn、DISPATCH/SPEAK/SUMMARY，quote 缓存键为 turn 下标，dispatch 一次重排未执行后缀避免重复。`ChatService.kt` 接提示词/正文解析/审批恢复 indexOfLast，并在重新生成/进程重建时复用已保存分派正文。`GroupConfigPage.kt` 改 Switch。新测试 `GroupDispatchTest.kt`，扩展 `GroupGenerationRoundTest.kt`。
- **E 文件**：`ai/ui/Message.kt` 独立 summaryAsTextWithSender，不改 summaryAsText；`ContextUsage.kt` 用量/阈值/20% 迟滞/分层纯函数；`CompressPrompt.kt`、`PreferencesStore.kt` 新增 groupCompressPrompt 默认值和 DataStore key/读写。`Group.kt` 追加自动提醒、阈值、条数、保留、目标、上限和分层字段，全部带默认值、非 Transient。
- **E 接线**：`ChatService.kt` compressConversation 追加默认 null 的 formatter/builder，分层不切块，单层沿用旧切块；群压缩预留 session job 防止并发覆盖，整轮成功才更新 completedGroupRounds。`GroupVM.kt` 汇合完整会话/设置与任务完成状态，共用 ContextUsage、group-only 空闲提醒、完整数据库加载、失败错误卡片和完成提示。`FilesPicker.kt` / `ChatPage.kt` 独立放行群压缩，其他助手/会话修改仍由原守卫拦截。`CompressContextDialog.kt` 默认隐藏的阈值和默认参数；新 `GroupCompressionSettings.kt` 完整群级设置及提示词编辑，`GroupChatContent.kt` 用户确认弹窗和完成 Toast。新增 `GroupCompressionTest.kt`。
- **三语/测试**：仅 values / values-zh / values-zh-rTW；资源新增分派、总结、提醒和压缩设置。抽屉测试从群列表改为 `GroupConversationListTest.kt`；`GroupStorageTest.kt` 新增标题/置顶不删消息验证，仅编译设备代码，未运行设备测试。验证细节见本批交付说明。
- **源码与任务书差异**：示例直接追加点名成员会重复发言，以验收顺序重排；Model 没有 contextLength，使用群级设置或 128,000；applyPlaceholders 使用单花括号；压缩后保留消息的旧 usage 不可用于新摘要上下文；触发采用服务完成计数+VM状态，未增加 AppEvent 子类。
- **提交/回滚**：尚未提交，commit hash 待维护者授权后补。回滚须逐文件与上一批 `F012-phase-BCD-debug/*-source.zip` 对照恢复本轮变更，保留旧工作区的 A/B/C/D 与全部一期修正；新增序列化字段保留默认值、只退 UI/接线，不删除已落盘字段。未来独立提交后使用该提交的 `git revert <commit>`；禁止用 reset/checkout 清空未提交工作区。
- **上游重套边界**：保留全部一期/A/B/C/D 补丁；另需重套 Responses capability、Message 新方法、compressConversation 默认钩子与群 guard、PreferencesStore 群提示词读写、FilesPicker/CompressContextDialog 默认群参数、ChatPage 群压缩接线。不得覆盖 F007 或放开 allowConversationActions。

## [F012 二期 B/C/D] 群组复制、审批、副群主、标签与引用（2026-10-10）

- **版本/基线**：`2.5.6-work.3 / 196`，HEAD `f79210c1`，以二期 A 修正二后的未提交工作区为基础，保留一期修正①至⑬及二期 A 两份修正。维护者要求 B/C/D 全部修改完成后统一出包，覆盖任务包的分段交付建议。
- **B 复制**：GroupVM/GroupPage 接完整克隆与进度/完成提示；GroupChat.copyForGroup 重建群会话/节点 ID，保留消息 ID、所有分支/选择索引、时间与标题；复用 ChatService.copyWithForkedFileUrl 物理复制本地文件、背景与嵌套工具输出附件。该函数仅可见性改变，实际为成员扩展；新增 Context 注入，复用 MemoryRepository.copyMemories 复制会话和遗留群桶，不开放 DAO。失败回滚新群/会话/记忆/附件，不改源群。组员仍是共享库快照引用，副群主 ID 可直接复制。
- **B 审批/提示词**：Group.toolApprovalMode 以 Partial 默认值持久化；toAssistant 传群档位；ChatInput/ChatPage 复用盾牌三档，群模式不回退写聊天助手，无群隐藏。ToolApprovalPicker/ToolRiskTiers/GenerationLoop 不变，Partial 保留 OR 升级，ask_user 所有档位仍确认。三语框架提示词追加有理由的反驳，保留原占位符及前缀说明。
- **C 副群主**：Group.deputyMemberId 默认 null，normalizeGroupData 清理已删除/移出成员；GroupConfigPage 菜单与徽章标记群内唯一副群主。GroupChat.enabledMembers/speakingMembers 保持 priority 后把已启用副群主放最后，ChatService 仅该成员追加汇总提示。只生成一次，不隐藏组员发言。
- **C 标签与 @**：Settings.groupTags、DataStore key/读取/持久化纯新增，旧群标签有意清空并需重打；assistantTags 的字段与清洗不变。GroupComponents 改独立池并复用 TagsInput；GroupDialogs/GroupConfigDialogs/GroupPage/GroupConfigPage/GroupMemberPage/ChatDrawer/GroupChatContent 接保存回调。新建/编辑组员强制有效标签，复制无标签旧组员先打开编辑；群标签非必填，组员库有可关闭说明。GroupMentionCompletionProvider + ChatCompletionProvider 可选 mentionName + ChatInputState.insertMention 在光标处插入；长名优先、支持中文/空格/同名，只选择本群启用成员，无有效提及时按正常順序。
- **D 引用/提问**：UIMessageAnnotation 新增默认 preview 的 GroupQuote，StreamTraceReplayTest 补 exhaustive 分支；GroupChat 提供最近十条有正文消息的可引用清单、宽容标记解析与合法 ID 验证。GroupGenerationRound 缓存每个成员的清单防审批恢复漂移。ChatService/MessageQueue 默认 annotations 参数让用户引用随排队/编辑保留；群编辑按原消息 ID 保留发言人/旧引用，不走单聊用户预处理。GroupSpeakerTransformer 请求前缀并入首个现有 Text，不新增/重排 parts；工具参数和 reasoning 不改。无合法 ID 保留原文且不挂引用，合法时仅首个目标生效。
- **D UI**：ChatPage 使用 remember(mode/group/conversation) 的引用态，GroupChatContent/ChatList 透传；ChatMessage 仅群消息支持向外滑动、阻尼/阈值/回弹，保留边缘抽屉与点击编辑，引用块在现有正文气泡内。ChatInput 的单行可关闭引用条位于附件后/输入行前，默认 null 的 inputFocusRequester 仅群模式使用，引用/头像提及时返回输入焦点；组员头像长按与 @ 补全共用插入函数。UIAvatar 新增默认 null 的同层长按路径，默认 Surface 点击不变；ChatMessageTools 的 ask_user 卡片标注发起成员，HITL 不改。
- **测试**：GroupToolApprovalTest、GroupPhaseBCDTest、GroupQuoteTest、GroupMentionCompletionProviderTest、GroupGenerationRoundTest、MemoryRepositoryTest 覆盖旧 JSON/隔离/顺序/克隆全分支/引用 ID 与 parts 顺序/排队/审批恢复。GroupStorageTest 增加真实数据库克隆独立性与引用持久化，GroupMentionInputTest 覆盖光标/选区（仅编译、不运行设备）；GroupTest/GroupLayoutTest 的标签夹具改独立池。全模块 JVM、APK、Lint 量测与设备待验收另记本批 DELIVERY.md，不把设备编译当实机通过。
- **最终核验（2026-10-11）**：115个 Debug JVM 测试套件共782项全部通过（新增28项），最终源码的 ARM64 Debug 构建及设备测试代码编译完成；APK 版本/包名/应用名/架构/三语/同一 V2 Debug 签名已核验。完整 Lint 重跑2分30秒，44个错误、242个警告、3个提示；错误规则、说明、文件和片段与二期 A 修正二一致，无新增错误，全仓 Lint 未通过。不新增 baseline/屏蔽规则、不操作设备；首次新增的资源访问、Context 保存和英文复数问题已修正。
- **产物**：`artifacts/2.5.6-work.3/F012-phase-BCD-debug/`，只生成最终 ARM64 Debug APK 与完整工作区/子模块源码 ZIP，不覆盖历史批次，不含签名/密钥/缓存/产物。不构建 Release，不提交/推送/tag，不操作设备或真实账号/API。
- **commit/回滚/升级**：本批未提交；未来独立提交后 `git revert <F012-phase-BCD commit>`。当前只反向应用上述 B/C/D 增量和测试/文档，不能整文件回退而丢失一期/A。降级前备份带新 annotation 的历史与独立标签配置；上游升级重套上述共享接口/持久化/渲染接线，保留原聊天默认路径、风险分级、reasoning 顺序和三语限制。不做权重/并行/内部消息/副群主自动调度/新审批状态机。

## [F012 二期 A 修正单二] reasoning 回传与说话人前缀（2026-10-10）

- **基线/版本**：修正单一交付后的未提交工作区；保持 `2.5.6-work.3 / 196`、HEAD `f79210c1`，仅 ARM64 Debug 与完整源码。
- **改动范围**：`GroupChat.kt` 前缀合并到首个已有 Text、新增 stripSelfSpeakerPrefix；`ChatService.kt` 只对 firstResponseIndex 起的当前群员回复调用；三语 strings.xml 修改原 group_chat_frame_prompt；既有 GroupChatTest 补回归。不改 Responses/Chat Completions API、GenerationLoop、工具契约、Room 或序列化结构，不新增源码文件/依赖/资源 key。
- **顺序保护**：不新增前置/后置 part，不重排；Reasoning/Tool-only 保持原样，metadata 不变。已有 Text 在 Reasoning 前的少见消息也不重排，遵守“保持原顺序”的核心红线，而非改变供应商原始结构。
- **剥离边界/文档差异**：严格只检查首个 Text，不照示例搜索后续段落，避免删掉正文中间的名字。只处理 assistant、自身字面名字、匹配的方括号/全角括号与可选冒号；保留用户、他人、reasoning、工具及其他元数据。流式 buffer 累积但仍可能只有部分名字，完整标注收到后清理；不把累积流等同于无闪烁，不改上游缓冲或完成时机。
- **回归覆盖**：Reasoning/Text/Tool 顺序与元数据、无 Text、多 Text、原始 Text-before-Reasoning、无 sender/用户；两种括号/空白/冒号、特殊字符/首尾空格名字、非 assistant、他人和中间标签、后续段落、无文本/空名字、默认名、流式累积/原 buffer 不变、非流式和序列化。编译、测试、lint、APK/源码核验与实机待验收项分别记录于本批 DELIVERY.md，不将自动测试记为设备通过。
- **自动验证**：全模块 Debug JVM 报告 754 项、GroupChatTest 29 项，零失败/错误/跳过，新增 15 项；Debug 构建和设备测试代码编译成功（1 分 51 秒）。APK 版本/名称/包名/ARM64-only/三语/Debug V2 同证书/WASM 已核验。相对上批 1868 项源码，仅本单六个代码/测试/语言文件及两份台账变化，无删除文件或新增 key；三语占位符和 git diff --check 通过。设备和真实供应商未测试。
- **lint**：首轮退出 143，daemon 日志显示客户端断开，未产生新报告；`--max-workers=2` 完整重跑 8 分 6 秒，退出 1。最终 44 errors / 242 warnings / 3 hints，全部错误规则/说明/文件/片段/位置与上一批完全一致，无新增错误；全仓 lint 不标通过，不新增 baseline 或屏蔽。
- **交付/回滚/升级**：新目录 `artifacts/2.5.6-work.3/F012-phase-2A-fix2-debug/`，保留旧批次。不构建 Release、不 commit/tag/push、不操作设备/API。回滚只反向应用本批六个代码/测试/语言文件与台账增量，不整文件恢复覆盖一期和修正单一；升级时保留请求前缀不重排、群聊限定剥离与三语说明。

## [F012 二期 A 修正单] 对话崩溃与群聊气泡（2026-10-09）

- **基线/版本**：已交付二期 A 未提交工作区；HEAD 仍为 `f79210c1`，版本保持 `2.5.6-work.3 / 196`，仅 ARM64 Debug。
- **生产代码**：仅修改 `ChatService.kt`、`TemplateTransformer.kt`、`ChatMessage.kt`、`SettingPreferencesUIPage.kt`。群聊移除 templateTransformer，单聊保留；模板获取失败返回原消息；群员正文强制气泡，头像 28dp；全局显示设置仅在 UiMode.CHAT 展示助手气泡开关。
- **实现差异**：气泡正文在私有 MessagePartsBlock 中，复用 ChatMessage 已解析的 groupSender 计算标记并传入，不重复查注解。设置页复用自身已订阅的 Settings，不引入另一份 LocalSettings。TemplateTransformer 的原渲染逻辑提为同文件 internal applyMessageTemplate，沿用 TimeReminderTransformer 的可离线测试写法，模板变量和时间逻辑不变。
- **回归测试**：在既有 GroupChatTest 中补充模板空 Reader 复现及原样返回、自定义模板/角色和元数据、synthetic 消息及工具参数不变、默认模板内容不变四项；不新增源码文件、依赖或资源 key。编译与测试结果将在本批交付说明记录，未经设备运行不标实机通过。
- **自动验证**：针对性 GroupChatTest 14 项和全模块 Debug JVM 报告 739 项全部通过，零失败/错误/跳过；ARM64 Debug 构建和设备测试代码编译成功。APK 的版本/包名/应用名/三语/ARM64-only/Debug V2 同证书已核验，设备及供应商实测未运行。新增测试仅在 JVM 初始化日志时使用已有 NOP provider 并恢复系统属性，不修改 Android 应用的日志配置。
- **lint**：重新完成完整 `:app:lintDebug`（7 分 36 秒），仍为 44 个错误、242 个警告、3 个提示；44 个错误内容及位置与上批完全一致，无新增错误。全仓 lint 未通过，不增加 baseline 或禁用检查；中断前的未完成检查不算最终结果。
- **边界/交付**：不改 Settings/DisplaySetting/Room 结构、持久化默认值、generateText/createTools 签名或前三语。保留全部一期及二期 A 成果；产物与说明位于 `artifacts/2.5.6-work.3/F012-phase-2A-fix-debug/`，不覆盖上一批。
- **commit/回滚/上游同步**：未提交、未打标签、未推送；未来独立提交后可 `git revert <F012-phase-2A-fix commit>`，未提交时只反向应用本修正的四文件及测试/文档增量，不能整文件还原丢失二期 A。升级上游需重套群聊转换器装配、模板加载回退、GroupSender 气泡/头像和设置模式条件；不恢复群聊模板装配。

## [F012 二期 A] 群聊生成与消息归属（2026-10-09）

- **基线/版本**：一期及修正①至⑬的未提交工作区；HEAD 仍为 `f79210c1`，`2.5.6-work.3 / 196`、`me.rerere.rikkahub.debug`。
- **实施**：复用 ChatService/GenerationLoop/消息队列/前台服务/审批，按启用成员 priority 串行生成；GroupGenerationRound 保存当前轮的成员和配置快照，审批后继续同一成员。新增空回复节点避免多成员回复合并；GroupSender 注解写入消息 JSON，GroupSpeakerTransformer 只改变模型请求中的发言前缀。
- **会话**：Group 追加 enableMemory=true、systemPrompt=""、conversationIds=[]；空草稿首次发送才落库。活动级 GroupVM 管理按群选择的会话、创建/切换/删除/分叉、停止/编辑/重生/审批与队列。Room schema、Conversation 和 UIMessage 字段不改，不加依赖。
- **记忆**：`grp:<groupId>:<conversationId>` 隔离读取/写入/管理，旧 `grp:<groupId>` 通过 MemoryDAO 的事务移动到首个会话，保留行 ID。ChatToolFactory 增加默认 null 的 memoryScope，群聊编辑/删除核查范围；F002/F010 的原有范围和调用不变。
- **过滤**：MessageFtsManager 删除旧索引后跳过群聊，搜索再加排除条件；ConversationRepository 在标题搜索、分页及置顶结果中过滤固定群聊占位助手；统计页会话计数/日期和消息数/token/热力图排除群聊。getAll/getAllIds 和全库备份不做过滤。删除群聊附件前保留其他消息的文件引用。
- **资源**：组员数值/模型/流式参数和群级 reasoning 映射临时 Assistant，群工具/技能/MCP/注入/请求参数使用原链。McpManager 新增可选 Assistant 参数，默认继续取原单聊助手，群聊显式传入；不把临时 Assistant 写入 Settings。
- **新增核心文件**：`data/model/GroupChat.kt`、`service/GroupGenerationRound.kt`、`data/ai/transformers/GroupSpeakerTransformer.kt`；测试 `GroupChatTest.kt`、`GroupGenerationRoundTest.kt`。
- **修改范围**：二期新增共享改动实际涉及 `ai/.../UIMessageAnnotation.kt`，以及 app 的 `ChatService.kt`、`ChatToolFactory.kt`、`McpManager.kt`、`MemoryRepository.kt`、`MemoryDAO.kt`、`ConversationRepository.kt`、`MessageFtsManager.kt`、`MessageNodeDAO.kt` 的统计扩展、`StatsVM.kt`、`ChatMessage.kt`、`ChatList.kt`、`ChatPage.kt`、`ChatInput.kt` 和 `RouteActivity.kt`。另修改群组模型/VM/聊天页/配置页/记忆页/复制逻辑及三语。保留一期四处共享组件例外；实际范围超过任务书估算的“11 处”，原因是修正组员 MCP 选择、群聊统计和原输入栏仍写到单聊配置的路径，不修改 GenerationLoop 或 Room schema。
- **边界**：只实现二期 A，未做话语权权重、子群、标签路由、主动发言、参考消息及新状态机。群聊 Partial 审批，不新增群级审批配置；隐藏单聊搜索/快捷消息/审批入口，语音模式和压缩给出未支持提示。三语资源，不创建其他语言。
- **验证/交付**：本地自动检查与设备待验收项记录于 `artifacts/2.5.6-work.3/F012-phase-2A-debug/DELIVERY.md`；只交付 ARM64 Debug、含未提交文件和 material-color-utilities 子模块的源码 ZIP，不含凭据或缓存。设备测试只编译，未运行，不使用账号/API 实测。
- **自动测试结果**：735 项全模块 Debug JVM 测试零失败/错误/跳过，二期新增 20 项；GroupChatTest 覆盖序列化、参数映射、上下文限 1、stream/nonstream 消息节点/归属；GroupGenerationRoundTest 覆盖串行/可见前文、审批位置与快照、取消/失败/重生；MemoryRepositoryTest 覆盖迁移保留 ID、会话隔离和跨桶 ID 拒绝。设备数据库 5 项新增用例只编译；ARM64 APK 版本、包名、应用名、三语及与上批一致的 Debug V2 签名核验通过。完整设备验收待维护者，未生成虚假截图。
- **最终 lint**：44 个错误、242 个警告、3 个提示，与上一批数量一致；44 个错误的规则、说明、文件及代码片段一致，仅 ChatList 一处行号从 218 到 219。新增未使用文案警告已清理，三语 group key 各 82 项且集合一致；未新增 baseline 或屏蔽规则。含 lint 的最终组合命令因这些既有错误失败，编译与 JVM 测试分别完成，不将整条命令记为通过。
- **commit/回滚**：本批未提交、未创建 tag、未推送；最终独立提交后可 `git revert <F012-phase-2A commit>`。未提交时仅反向应用二期增量，不能整文件回退丢掉一期①至⑬；回滚前备份新增群聊会话和记忆，旧 APK 不识别新消息注解，不保证降级兼容。

## [F012] 群组功能第一期 UI
- **需求**：保留原有风格，在抽屉手动切换聊天/群组，支持独立组员及群组配置；本期不接群聊生成。
- **基线/版本**：`f79210c1`（`2.5.6-work.2 / 195`）；本地 Debug 为 `2.5.6-work.3 / 196`、包名 `me.rerere.rikkahub.debug`。
- **状态**：UI 与持久化已实施，头像点击/返回原会话补充修正已重新编译并打包；全模块 Debug 单元测试及 Debug 打包通过，设备验收与风格对照截图待维护者。
- **新增文件**：`data/model/Group.kt`、`GroupMember.kt`；`ui/components/group/ModeSwitcher.kt`；`ui/pages/group/GroupVM.kt`、`GroupListLayout.kt`、`GroupMemberPage.kt`、`GroupPage.kt`、`GroupConfigPage.kt`、`GroupChatContent.kt` 及 `components/` 下的共享头像/标签、抽屉列表/选择器、群组背景和创建/参数弹窗；`detail/` 下五个群组设置页。
- **修改文件**：`PreferencesStore.kt`（仅新增默认字段及读写/清洗）、`ViewModelModule.kt`、`RouteActivity.kt`、`ChatDrawer.kt`、`ChatPage.kt`（群组占位分支与关闭键盘）、`NavContext.kt`（返回既有聊天页）、`app/build.gradle.kts`、`workspace/build.gradle.kts`（Debug 仅 ARM64）、三份 `strings.xml`、`AGENTS.md`、`CHANGES.md`、`FEATURES.md`。
- **测试**：修正后 Debug 测试报告共 700 项，零失败、零错误；`GroupTest.kt` 的 12 项覆盖旧设置默认值、JSON 往返/旧凭证保留、独立快照、标签/组员失效引用、去重、删除隔离、排序优先级与话语权边界、按 ID 上下移动/失效 ID/连续移动恢复；`NavigatorGroupTest.kt` 的 3 项覆盖返回原聊天/最近聊天、不存在聊天时不改栈。
- **lint**：本期新增的资源读取与活动引用问题已修正；第二轮仍有 44 个错误、245 个警告及 3 个提示，错误片段均存在于基线 `f79210c1`。全仓 lint 未通过，不新增 baseline 或禁用检查来掩盖。
- **兼容**：不修改 `Assistant`、`Conversation`、`UIMessage`、Room schema、工具或生成链；不增加依赖；仅三语。群内优先级/话语权及资源选项只保存配置，不执行发言策略；群组记忆可在设置页增删改，生成时读取留待第二期。选中群组暂存在活动级 ViewModel，重启后默认选库列表首个群组，模式本身持久化。
- **修正单（2026-10-06）**：`GroupComponents.kt` 的标签由 LazyRow 改普通 Row，移除 `CardGroup` 标签说明槽的 SubcomposeLayout；`GroupDialogs.kt`、`GroupConfigDialogs.kt`、`GroupDrawer.kt` 的弹窗内列表/标签使用限高滚动 Column/Row。`GroupConfigPage.kt` 把唯一长按拖拽手势挂在 `CardGroup.item.modifier`，取消嵌套图标手势及重复动画，直接使用持久化成员列表并等待每次保存完成。`Group.kt` 新增按 ID 移动函数，不改变任何序列化字段。
- **修正验证**：全模块 Debug 单元测试共 700 项零失败、零错误，`GroupTest.kt` 新增 3 项按 ID 移动/边界/连续移动与 JSON 恢复测试。新增 `app/src/androidTest/.../ui/pages/group/GroupLayoutTest.kt` 的 2 项设备端 Compose 回归测试已编译；不连接设备执行，创建和拖拽实机验收仍待维护者。修复包与源码以 `F012-fix` 后缀另行交付，保留旧包。
- **修正 lint/静态核查**：仍有 44 个既有错误、246 个警告、3 个提示，F012 群组文件无 lint 命中；解析全项目 432 个 Kotlin 文件和 312 个内容槽，未发现其他直接嵌套 ListItem/Lazy 组合。辅助组件的独立弹窗及固定纯文本预览经人工核对，无关功能不修改。
- **交付物**：后续仅交付 `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`，Debug 原生编译及 APK 输出均限定 ARM64，正式版配置不变。提供完整工作区源码 ZIP（含 F012 未提交文件与子模块，不含凭据、构建缓存或 APK）；首轮三种 Debug APK 已核验，未构建 Release、未操作设备、未上传源码或 APK。
- **commit**：未提交（基线 `f79210c1`）；等待用户确认，不创建 tag/push。
- **回滚方式**：未来独立提交后 `git revert <F012 commit>`；提交前只反向应用 F012 增量并移除本条列出的新增文件，不整文件覆盖、不撤销 F001–F011。
- **上游同步**：按块重套 DataStore 默认字段/持久化/清洗、三条路由/Koin 注册、抽屉群组分支及 ChatPage 占位入口；保留原聊天渲染和 F003/F004/F006–F011。
- **日期**：2026-10-06

### F012 修正单②③④⑤⑥⑦（2026-10-07）
- **改动**：模式切换动画；组员四项数值参数；两个库列表拖拽；五个群组资源页；真实输入框、独立草稿和发送拦截；群组图片/渐变/透明度背景与旧文件清理。
- **数据/文件**：`GroupMember.kt` 追加四个带默认值的参数；`Group.kt` 追加七个资源字段和三个背景字段，不修改归一化逻辑；`GroupVM.kt` 增加草稿/按 ID 重排/背景清理及 FilesManager 注入；`MemoryRepository.kt` 只追加 `GROUP_CHAT_MEMORY_PREFIX` 与 `scopeOfChatGroup()`，不改既有函数或 DAO。
- **页面/路由**：`GroupMcpPage.kt`、`GroupLocalToolPage.kt`、`GroupExtensionsPage.kt`、`GroupMemoryPage.kt`、`GroupRequestPage.kt` 与对应 `Screen`/entry；`GroupBackground.kt`；`GroupChatPlaceholder.kt` 改名 `GroupChatContent.kt`；`GroupConfigPage.kt` 增加背景和五入口卡片；`GroupMemberPage.kt`、`GroupPage.kt`、`GroupListLayout.kt`、`GroupDialogs.kt`、`GroupDrawer.kt`、`ModeSwitcher.kt`、`ChatDrawer.kt`、`ChatPage.kt`；仅三份资源新增三条群组专用文案。
- **行为变更**：新建群组的组员顺序跟随组员库，不按创建时间；群组输入框的搜索/审批/快捷消息仍沿用当前聊天配置（已知一期取舍）。思考等级自⑩改为群组级、模型选择器自⑬隐藏；群组资源尚不参与生成。
- **上游升级需重套的两处复用改动**：`AssistantLocalToolPage.kt` 的 `AssistantLocalToolContent` 从 private 改 internal；`BackgroundPicker.kt` 增加默认 `label`/`description`，助手调用点和选择流程不改。模式动画使用 `ContentTransform.using(null)` 关闭 sizeTransform，因为当前 Compose 的 AnimatedContent 没有独立的 sizeTransform 命名参数。
- **验证**：`./gradlew test :app:assembleDebug :app:compileDebugAndroidTestKotlin --offline --no-daemon --console=plain` 通过；全模块 Debug 报告共 706 项，零失败、零错误、零跳过。`GroupTest` 共 16 项，本轮新增 4 项；`MemoryRepositoryTest` 新增 2 项，覆盖旧数据默认值、资源与数值持久化、快照不复制数值、库顺序与群内顺序独立、默认选首组，以及群聊/助手/F010 记忆范围与 CRUD 隔离。3 项群组 Compose 设备测试编译通过但未运行。APK 已核验版本/包名/ARM64-only/三语资源及 V2 Debug 签名；实机效果由维护者验收。
- **本轮 lint**：最终 `:app:lintDebug` 仍未通过，44 个错误、247 个警告、3 个提示；44 个错误的上下文片段均存在于基线 `f79210c1`，群组相关文件无 lint 命中。没有新增 baseline 或关闭检查。
- **产物**：`artifacts/2.5.6-work.3/F012-fixes-2-7-debug/`，仅 ARM64 Debug 与完整源码；保留前两批产物。
- **提交/回滚**：未提交、未推送、未创建 tag；只反向应用②至⑦增量并移除新增 detail/background 文件，不能整文件恢复覆盖一期/①或 F001–F011。未来独立提交后 `git revert <修正单②至⑦ commit>`。

### F012 修正单⑧（2026-10-07）
- **改动文件**：`ui/pages/group/GroupChatContent.kt` 仅一行，将群组顶栏颜色改为 `TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)`，与聊天模式一致；另同步 `CHANGES.md` 和本台账。
- **范围**：不改共享 `CustomColors.topBarColors`、设置页、聊天模式、背景层或 hazeSource；不新增依赖、资源、模型字段或滚动行为，保留②至⑦。
- **验证/交付**：`./gradlew test :app:assembleDebug :app:compileDebugAndroidTestKotlin --offline --no-daemon --console=plain` 通过，Debug 报告 706 项零失败/错误/跳过；APK 版本、包名、应用名、ARM64-only、三语资源与 V2 签名已核验。与上批源码的 1861 项文件比对仅一行功能代码和两份台账变化。保持 `2.5.6-work.3 / 196`，独立归档 `artifacts/2.5.6-work.3/F012-fix-8-debug/`，保留旧批次。本轮未重跑全仓 lint；明暗主题、图片/渐变顶栏和玻璃实机验证由维护者执行，不制作虚假的截图/录像。
- **回滚/上游同步**：只把此处顶栏颜色还原为 `CustomColors.topBarColors`；不得整文件恢复覆盖前期改动。升级上游时保留这一行与聊天模式同款的透明配置。未提交、未创建标签或上传。

### F012 修正单⑨（2026-10-07）
- **改动文件**：`ui/components/group/ModeSwitcher.kt` 统一胶囊底色为 `surfaceContainerHigh`，删除 `animateColorAsState` 调用及 import；保留 `getValue`、菜单当前项勾选、模式图标/文字与交互。同步 `CHANGES.md` 和本台账。
- **范围**：覆盖修正单② §2.3 的模式胶囊配色；其余抽屉/页面动画及⑧透明顶栏保留。FolderChip、AssistantItem、GroupDrawer 的真实选中高亮未改，不新增依赖、资源或持久化字段。
- **验证/交付**：`./gradlew test :app:assembleDebug :app:compileDebugAndroidTestKotlin --offline --no-daemon --console=plain` 通过，706 项 Debug 测试零失败/错误/跳过；包内版本、包名、应用名、ARM64-only、三语资源和 V2 签名已核验。相对⑧的完整源码仅 ModeSwitcher 与 CHANGES/FEATURES 改变。版本仍为 `2.5.6-work.3 / 196`；归档 `artifacts/2.5.6-work.3/F012-fix-9-debug/`，保留旧包。未执行设备测试/截图，未重跑全仓 lint，实机明暗主题验收由维护者执行。
- **回滚/上游同步**：仅反向应用 ModeSwitcher 的本轮配色增量，不整文件覆盖一期或修正单②至⑧。未来升级时保持胶囊两种模式同底色、菜单勾选及其他组件的真实选中态。未提交、未上传或创建标签。

### F012 修正单⑩（2026-10-07）

- **范围**：以维护者更正的“思考预算改为群组级并由输入框调节”任务书为准，不采用旧版“下移”表述。仍为 `2.5.6-work.3 / 196`，不新增按钮或调整既有灯泡的位置/外观；群聊生成仍留第二期。
- **改动文件**：`GroupDialogs.kt` 仅删除思考预算项及 import；`Group.kt` 末尾追加非 Transient 的 `reasoningLevel = AUTO`；`GroupMember.kt` 仅加兼容注释，字段及 `importSnapshot` 完全保留；`ChatInput.kt` 新增两个默认 null 的可选覆盖参数，灯泡只调整读写目标；`ChatPage.kt` 按当前群组接线；`GroupTest.kt` 补充兼容、持久化和隔离测试。同步 `AGENTS.md`、`CHANGES.md` 与本台账。
- **行为/兼容**：旧群组默认 AUTO，无需迁移。群组模式无条件显示灯泡，不使用助手/组员模型能力判断；更新仅写 `settings.groups`，不写 `settings.assistants` 或成员旧字段。无群组时显示 AUTO 并复用创建提示。聊天模式不传覆盖参数，保持原模型能力守卫、助手读写和 onlyIcon 外观。
- **快照口径更正**：从助手导入的当前有效行为特征只剩 `streamOutput`；兼容代码仍复制旧 `reasoningLevel`，但不会覆盖群组等级或作为成员思考配置生效。模型、工具审批、快捷消息仍为当前助手配置的已知取舍，思考等级不再属于该取舍。
- **上游升级需重套的三处共享组件改动**：① `AssistantLocalToolPage.kt` 的 `AssistantLocalToolContent` private -> internal（④）；② `BackgroundPicker.kt` 两个带默认值的文案参数（⑦）；③ `ChatInput.kt` 的 `reasoningLevelOverride` / `onUpdateReasoningLevelOverride` 及灯泡读写分支（⑩）。③是本轮新增例外，升级上游需连同 Group 默认字段和 ChatPage 接线按块保留，不修改 ReasoningButton/ReasoningPicker。
- **验证**：`./gradlew test :app:assembleDebug :app:compileDebugAndroidTestKotlin --offline --no-daemon --console=plain` 通过，709 项 Debug JVM 测试零失败/错误/跳过；GroupTest 为 19 项，新增 3 项并增强旧数据默认值断言。APK 版本、包名、应用名、ARM64-only、三语与 V2 Debug 签名已核验；设备测试仅编译，未运行，退出重进及共享灯泡 UI 回归待维护者。
- **lint**：本轮 `:app:lintDebug` 仍有 44 个错误、247 个警告、3 个提示；对比上批 XML 的全部错误属性及文件位置完全一致。群组文件及 ChatInput/ChatPage 无命中，全仓 lint 未通过，不新增 baseline 或禁用检查。
- **交付/状态**：`artifacts/2.5.6-work.3/F012-fix-10-debug/`，只交付 ARM64 Debug 和包含未提交 F012/子模块的完整源码。保留旧批次；未构建 Release、未操作设备、未提交/推送/打标签。
- **回滚**：仅反向应用⑩在上述五个源码文件及测试的增量，移除 Group 新字段会丢失群组思考配置，应先备份；不得整文件覆盖①至⑨或 F001–F011。未来独立提交后 `git revert <修正单⑩ commit>`，当前无提交 hash。

### F012 修正单⑪至⑬（2026-10-09）

- **基线/范围**：基于已交付的 fix-10 工作区继续，不重做①至⑩；仍为 `2.5.6-work.3 / 196`、`me.rerere.rikkahub.debug`，仅 ARM64 Debug 与完整源码。
- **⑪数据与抽屉**：`Group.kt` 仅追加默认 false 的 isPinned，保留 reasoningLevel 及所有既有字段。`GroupDrawer.kt` 增加纯渲染列表构建器，置顶优先、创建时间倒序、按本地日期分组；新 GroupChatItem 采用原聊天单行胶囊样式，四项长按操作通过回调传出。GroupPicker 不改，抽屉无重排/写回；GroupPage 仅在复制时设置 isPinned=false，原拖拽不改。
- **⑪操作接线**：`ChatDrawer.kt` 接置顶/重命名/配置导航/确认删除，更新复用 GroupVM，背景清理沿用 deleteGroup；`GroupDialogs.kt` 只追加普通 AlertDialog 重命名，无 Lazy 布局，其余组员/群组表单不动。
- **⑫顶栏**：`GroupChatContent.kt` 的 actions 增加新建与更多菜单，菜单两项为记忆二级页面、会话列表占位提示；无群组时记忆判空。原设置按钮、透明颜色、背景/玻璃路径、设置页记忆入口、GroupMemoryPage 和记忆范围均不改；群聊会话、生成及对话级记忆仍第二期接入。
- **⑬输入框**：`ChatInput.kt` 新增默认 false 的 groupMode，群组不调用 ModelSelectorButton；灯泡在群组模式只看 override 是否非 null、聊天模式仍按原模型能力守卫。`ChatPage.kt` 传 isGroupMode，删除无群组时 AUTO 回退，回调只更新当前存在的群组。搜索/审批/快捷消息、ReasoningPicker、ModelList、selectedGroup 回退行为不改。
- **三语**：三份 strings.xml 各追加 5 个⑪ key、2 个⑫ key，既有文案值保持；不添加依赖、语言或 Room 迁移，不改 GenerationLoop/ChatService/工具契约。
- **上游升级需重套四处共享组件**：① AssistantLocalToolContent private -> internal（④）；② BackgroundPicker 的默认 label/description（⑦）；③ ChatInput 的两个思考覆盖参数（⑩）和默认 groupMode、显示条件/模型分支（⑬），同时保留 ChatPage 接线；④ ConversationList.kt 的 DateHeaderItem/PinnedHeader internal 与 PinnedHeader 默认 label（⑪）。ConversationList/ConversationListItem/ConversationItem 本身保持不变。
- **AGENTS**：将任务包 04-AGENTS 的⑪至⑬补充段并入仓库根文件，保留已有上游与本地约定，最新段取代旧“唯二/第三处共享例外”与空群组灯泡口径。
- **测试**：GroupTest 补充置顶默认值、置顶与思考/资源一起持久化的回归；新增 GroupChatListTest 覆盖显示排序不改保存顺序、置顶/取消置顶、仅一个置顶头、日期与时区/跨年边界、空列表及同时间稳定排序。设备交互、截图及录屏待维护者验收，不操作设备。
- **构建与核验**：`./gradlew test :app:assembleDebug :app:compileDebugAndroidTestKotlin --offline --no-daemon --console=plain` 通过；715 项 Debug JVM 测试零失败/错误/跳过，GroupTest 20 项、GroupChatListTest 5 项，新增 6 项。APK 包内版本、应用名、Debug 包名、ARM64-only、三语和 V2 Debug 签名已核验；设备测试仅编译。逐文件检查确认 ChatInput 的搜索/审批/快捷消息、ChatPage 的聊天顶栏/玻璃/草稿路径及 GroupPage 拖拽逻辑未扩大修改。
- **交付/提交**：`artifacts/2.5.6-work.3/F012-fixes-11-13-debug/`，完整工作区含未提交 F012 与子模块，排除凭据/缓存/构建输出，旧批次保留；未构建 Release、未提交/推送/打标签，基线 HEAD 仍 f79210c1。
- **本轮 lint**：执行两轮，新增置顶头的 ModifierParameter 警告已通过保留 modifier 为首个可选参数修正；第二轮为 44 个错误、242 个警告、3 个提示。全部错误属性及文件位置与 fix-10 一致，群组文件、ChatInput/ChatPage、ConversationList 无命中；ChatDrawer 仅有既存文件夹删除文案错误。全仓 lint 未通过，不新增 baseline 或关闭检查。调整后 Debug 打包、715 项 JVM 测试及设备测试代码编译再次完成。
- **回滚**：仅反向应用⑪至⑬增量，不能整文件恢复覆盖①至⑩。移除 isPinned 会丢失群组置顶配置，先备份。未来独立提交后 git revert <修正单⑪至⑬ commit>，当前无提交 hash。

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
- **状态**：✅ 方案乙修复并随2.5.3-work.3（versionCode190）正式接入；维护者确认2026-10-12实机压测通过（崩溃回归/性能/兜底），原Phase 0原型复核项已归档
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
- **实现与验证**：已将聊天内容采样源、液态玻璃和输入控件分置于独立兄弟层；采样源不包含玻璃自身。库内加入录制防重入、finally 收尾和异步渲染异常回调；失败时进程内熔断并回退 Haze。API 33 以下仍走 Haze，默认仍为模糊。`:app:testDebugUnitTest`、`:app:compileReleaseKotlin` 和 `:app:assembleRelease` 均通过；Release APK包名、版本与V2签名已核验。初次交付只确认原型观感，压测当时待验；后续实机结果如下，不以本地构建替代设备测试。
- **实机压测（2026-10-12，2026-10-14补录）**：维护者提供的《本次推送清单·2.5.6-work.3》确认A崩溃回归（进出聊天页/旋转/切换会话各20次）、C性能（帧率/发热/耗电）、D兜底（旧glass数据不崩、默认模糊）三组均通过。原Phase 0原型复核项已归档，修正此前“未做压测”的过期记录。本轮未操作设备，未取得原始性能数值，不外推到全部设备或F012交互验收。
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

## [F010] 记忆迁移多选
- **需求**：复制助手和切换记忆归属时支持按条目选择记忆，并支持复制或移动
- **状态**：源码已实施，`./gradlew test` 与签名 Release 构建已通过；未上传 GitHub，待用户确认
- **版本**：`2.5.6-work.2` / `versionCode=195`
- **改动文件**：
  - `app/src/main/java/me/rerere/rikkahub/data/db/dao/MemoryDAO.kt`
  - `app/src/main/java/me/rerere/rikkahub/data/repository/MemoryRepository.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/components/ui/MemoryPickerDialog.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/AssistantPage.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/AssistantVM.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantDetailVM.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryPage.kt`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-zh/strings.xml`
  - `app/src/main/res/values-zh-rTW/strings.xml`
  - `app/src/test/java/me/rerere/rikkahub/data/repository/MemoryRepositoryTest.kt`
- **副作用**：移动共享记忆会影响使用同一记忆桶的其他助手，界面会提前警告且该操作不可撤销
- **测试**：覆盖复制、移动、空选择、同桶短路和跨桶 ID 过滤，使用假数据，不记录真实记忆内容
- **commit**：未提交
- **回滚方式**：独立提交后使用 `git revert <F010 commit>`；在提交前仅反向应用本次 F010 增量 diff，不恢复整文件覆盖既有 F009 改动
- **日期**：2026-10-06

## [F010 补充] DeepSeek Web 登录界面适配
- **需求**：修复 DeepSeek 网页版登录区域在移动屏幕上的布局挤压和数字输入光标位置问题
- **状态**：已随 `2.5.6-work.2` 源码实施，签名 Release 构建已通过；未上传 GitHub，待用户确认
- **改动文件**：`app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/ProviderConfigureDeepSeekWeb.kt`、`app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/DeepSeekWebLogin.kt`
- **行为**：登录和退出按钮纵向全宽排列；登录对话框适配移动屏幕；数字输入保持光标在内容末尾；连通性测试按单行显示发送按钮和右侧测试结果
- **补充修正**：连通性测试成功时只显示右侧完整的“连接成功”，失败时显示错误信息，移除延迟显示，避免小屏幕省略结果
- **commit**：未提交
- **回滚方式**：独立提交后使用 `git revert <DeepSeek Web UI commit>`；保留 F009 登录凭证捕获逻辑
- **日期**：2026-10-06

## [F011] 随想搜索服务商
- **需求**：新增内置随想搜索服务商，仅保留全网 Web 搜索
- **状态**：源码已实施，保持 `2.5.6-work.2` / `versionCode=195`；待本轮测试和签名 Release 构建完成后再确认，未上传 GitHub
- **改动文件**：`search/src/main/java/me/rerere/search/SearchService.kt`、`search/src/main/java/me/rerere/search/SuiXiangSearchService.kt`、`search/src/test/java/me/rerere/search/SuiXiangSearchServiceTest.kt`、`app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingSearchDetailPage.kt`、三份 App 与搜索模块语言文件、`CHANGES.md`、`FEATURES.md`
- **范围**：只保留 `@SerialName("suixiang")` 的“随想搜索”，固定调用 `/v1/web_search`；不实现 X 搜索，因为实测只返回无意义标题和空摘要，且目标用户可能无法访问 X
- **协议**：请求体使用官方 `query` 字段；结果数默认 5，服务层和 UI 均限制 1～20；`snippet` 为空仍保留结果，缺 URL 的结果过滤，`scrapingParameters` 返回 null，错误文本不含 API Key
- **兼容与 UI**：旧 `suixiang` JSON 中的 `mode` 字段由 `ignoreUnknownKeys` 忽略；设置页只显示 API Key、结果数和服务地址，不显示搜索模式、X 高级参数或取消入口；资源只保留英文、简体中文和繁体中文
- **测试**：覆盖官方 `query` 请求字段、Web 端点、结果数 20 上限、空 snippet、缺 URL、缺少 API Key、API Key 脱敏、旧 `mode` 字段兼容和默认结果数；本轮未使用真实 API Key 联网验证，未操作设备，未验证覆盖安装路径
- **commit**：未提交，未执行 tag/push
- **回滚方式**：删除 F011 新增服务/配置/文案/测试，并反向应用本次连通性 UI 修正；不得恢复整文件覆盖 F010 或 F009 改动
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
