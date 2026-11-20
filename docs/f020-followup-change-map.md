# F020后续逐文件说明 — 3.3.0-work.1 / 207

日期：2026-11-18。相对首批`upstream-f020-release-20261117/SOURCE-MANIFEST.json`说明本次后续改动。
首批上游3.3.0合并及完整F020清单继续见`docs/f020-change-map.md`；不把首批改动重复列为本批新增。
表中`app/`、`llama-cpp/`均为仓库相对路径。

| 文件 | 改动与原因 |
| --- | --- |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LocalModelState.kt` | 新四态及WEIGHTS/CONTEXT/PROJECTOR，未加载专用异常，分离安装与加载。 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LlamaCppRuntime.kt` | 显式load/withReady，整轮mutex、生成计数、延后卸载finally兜底及前台取消；阶段/失败状态及融合内存诊断。 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LocalRuntimeNative.kt` | 可注入native边界，真实实现仍委托原JNI；供JVM验证释放与并发，不更改JNI契约。 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LocalMemoryGate.kt` | 新设备总内存只读警告、API34 advertisedMem保护、SwapTotal/VmSwap诊断与受限上下文余量。 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/ContextPlanner.kt` | 不再按availMem硬拒载，n_ctx地板512，增加默认0的cushion；空间不足错误与带默认值诊断参数。F16和默认16K保持。 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LlamaCppInstall.kt` | 下载前StatFs检查，实际长度预检；投影器首次关联标记只用于补默认模态。 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LocalModelDownload.kt` | Content-Length回调移到打开.part之前，避免空间不足仍创建临时文件。 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LlamaCppProvider.kt` | 改用withReady，停止能力写回；压缩专用严格预算适配器与模板后预检；释放blob前计真实用量，非流式也回传usage。普通聊天裁剪逻辑保持。 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LocalMediaDecoder.kt` | 按用户许可与引擎能力交集解码，冲突提示降级，不修改设置。 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LocalStream.kt` | 复用原累积文本，正常Finish前上报一次usage；取消无假结束，保留完整解析与无界缓冲。 |
| `app/src/main/java/me/rerere/rikkahub/data/ai/LocalModelSettings.kt` | 保留用户模态选择，仅初次增加投影器可补默认、移除投影器收回媒体。 |
| `app/src/main/java/me/rerere/rikkahub/data/ai/CompressionBudget.kt` | 新纯分档规则、字符兜底、工具输出副本裁剪、完整用户轮窗口及真实计数收窄辅助。 |
| `app/src/main/java/me/rerere/rikkahub/data/ai/ChatRequestFactory.kt` | 仅本地压缩请求关闭消息数裁剪、应用副本裁剪/输出上限及真实模板计数；普通/API请求默认路径不变。 |
| `app/src/main/java/me/rerere/rikkahub/data/ai/prompts/CompressPrompt.kt` | 仅可选追加长度限制，原七段模板逐字保留。 |
| `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` | 本地未加载交给胶囊；自动阈值优先真实用量、仅本地缺失时估算；复用原压缩路径，检查点定位实际窗口尾。 |
| `app/src/main/java/me/rerere/rikkahub/service/LocalModelIdleWatcher.kt` | 新后台30秒静默通知、用户请求/待卸载、前台撤销、脉搏保留与无通知权限守卫。 |
| `app/src/main/java/me/rerere/rikkahub/receiver/LocalModelUnloadReceiver.kt` | 非导出卸载动作接收器，持有goAsync至请求处理完毕。 |
| `app/src/main/AndroidManifest.xml` | 注册该非导出接收器。 |
| `app/src/main/java/me/rerere/rikkahub/RikkaHubApp.kt` | 独立LOW通知channel及进程前后台生命周期接线。 |
| `app/src/main/java/me/rerere/rikkahub/data/datastore/Settings.kt` | 唯一新增持久设置enableMemoryFusion=false。 |
| `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt` | 该布尔设置的DataStore key。 |
| `app/src/main/java/me/rerere/rikkahub/data/datastore/SettingsFields.kt` | 同一设置的统一读写/序列化登记。 |
| `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt` | 注册IdleWatcher，移除运行时能力探测对Settings的写入回调。 |
| `app/src/main/java/me/rerere/rikkahub/di/ViewModelModule.kt` | 加载协调器进程级单实例，聊天胶囊与设置详情共用。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/locallm/SettingLocalLlmViewModel.kt` | 显式加载/卸载及低内存确认；安装修复异常进入现有错误状态，加载不暴露下载的取消按钮。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/locallm/LocalLoadControls.kt` | 共用阶段秒数、失败映射和物理内存警告确认弹窗。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/locallm/LocalModelList.kt` | 收集共用状态，连接详情加载/卸载与警告。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/locallm/LocalModelSheet.kt` | 区分已安装/已加载，增加不可取消加载阶段与失败重试。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/locallm/ProviderConfigureLlamaCpp.kt` | “模型能力”进入模型标签，安装管理入口保留。 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/LocalModelCapsule.kt` | 新主题色、阶段/秒数/失败胶囊及淡出；Popup锚到输入框上方，点击命中区不脱离可见位置。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` | 普通和液态玻璃两条input路径接胶囊与确认；未Ready发送保留草稿。群组不加胶囊。 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt` | 只有本地长按增加models/focusModelId，API导航保持。 |
| `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | 供应商详情增加默认null的可选tab/focusModelId并透传。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt` | 本地配置/模型页签、指定表单只展开一次、按最新模型UUID保存；本地1–16 K输入，API预设逻辑保持。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt` | 模型服务与数据设置间增加实验性标准开关行，swap=0禁用，说明完整展示。 |
| `app/src/main/java/me/rerere/rikkahub/utils/ErrorText.kt` | 空间不足映射，并本地化need/free诊断。 |
| `app/src/main/res/values/strings.xml` | 新增31条英文加载、内存、通知和实验设置文案。 |
| `app/src/main/res/values-zh/strings.xml` | 同31条简体中文文案。 |
| `app/src/main/res/values-zh-rTW/strings.xml` | 同31条繁体中文文案。 |
| `app/build.gradle.kts` | 单ABI没有ABI filter时仍启用输出，修复测试资源链接“No non-density apk found”；版本207不变。 |
| `llama-cpp/src/test/java/me/rerere/llamacpp/LocalRuntimeStateTest.kt` | 新8项：未加载、三阶段、幂等、失败释放、编码器降级、异常/取消延后卸载、前台撤销、缺文件。 |
| `llama-cpp/src/test/java/me/rerere/llamacpp/LocalMemoryPolicyTest.kt` | 新3项物理门禁、融合/诊断及下载空间边界。 |
| `llama-cpp/src/test/java/me/rerere/llamacpp/ContextAndRequestTest.kt` | 原硬拒载断言改为需求规定的降至512，原轮次裁剪/工具映射用例保留。 |
| `llama-cpp/src/test/java/me/rerere/llamacpp/LocalStreamTest.kt` | 新整段raw输出计量一次/Finish前usage用例；原缓冲、取消和模板协议用例保留。 |
| `app/src/test/java/me/rerere/rikkahub/data/ai/LocalModelSettingsTest.kt` | 新用户模态保留/投影器增删用例。 |
| `app/src/test/java/me/rerere/rikkahub/data/ai/CompressionBudgetTest.kt` | 新9项分档、远端不限、完整工具轮切窗、旧checkpoint、不可变裁剪、真实预检收窄、连续检查点保留历史、模板追加及大型夹具。 |
| `app/src/test/java/me/rerere/rikkahub/data/datastore/SettingsFieldsTest.kt` | 新布尔设置往返保存覆盖。 |
| `README.md` | 显式加载、群组加载入口、短上下文与融合说明。 |
| `llama-cpp/README.md` | 加载生命周期与严格压缩、真实usage及源码依赖恢复说明。 |
| `CHANGES.md` | 同版本增加本批用户可见更新，修正旧能力自动覆盖说法。 |
| `FEATURES.md` | 新本批台账及上游重套边界。 |
| `AGENTS.md` | 按任务要求在末尾追加本批约定。 |
| `DELIVERY.md` | 当前批交付、验收证据、偏差、未做项；旧记录留作历史。 |
| `docs/f020-followup-change-map.md` | 本文件。 |

## 上游重套及回滚

优先逐块审查ChatPage两条input、ModelList长按、RouteActivity默认参数、Provider详情的本地特判、
Settings的单一新字段、ChatRequestFactory本地专用压缩及ChatService实际窗口尾。API分支不可用本地简化逻辑覆盖。
native和参考仓库指针不动，不修改F007/F009/F004、群组/脉搏调度及DB34。

回滚本后续批时以首批SOURCE-MANIFEST为界逐块反向撤销，不能reset整棵脏树或撤销3.3.0合并。
enableMemoryFusion旧值可保留为忽略字段；回滚压缩预算须同时检查调用参数和检查点定位。
保留所有原消息与权重数据；通知接收器/DI/Manifest/生命周期应同批撤销。

## 文档矛盾的处理

- “第二批”在本任务包指短上下文压缩；KV q8_0/默认4K等N1/N2事项仍未授权，保持F16/默认16K。
- §4.3示例maxTokens ?: params.maxTokens与非本地不限的红线冲突：使用显式maxTokens，非本地仍null。
- C5把16K写为.7，与档表和C9冲突：采用.75。
- 窗口估算只负责选初始前缀，模板、系统、工具及长度指令经native真实计数后可继续收窄；整轮放不下时明确TOO_LONG，不写错误摘要、不吞未压缩消息。没有增加另一套压缩器或自动连压。
- 通知按实际加载文件字节说明大小，不声称已测真实RAM占用或可释放量。APK不含权重。
- 源码ZIP的六个已跟踪libsimple/proot依赖须保留；仅排除生成的native so/缓存，而非一刀切排除所有.so。

## 未做及待验

未做KV量化、默认4K、预算模型深度重写、KV session存盘、群组胶囊、加载取消、百分比和后台自动卸载。
未连接设备/模拟器、下载模型或使用真实账号；加载/已加载/后台通知截图、真实150K对话压缩、
native prompt/completion用量和AutoCompress日志均待真机验收，不以JVM夹具冒充。
