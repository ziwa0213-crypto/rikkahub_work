# RikkaHub 二次开发功能台账

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
