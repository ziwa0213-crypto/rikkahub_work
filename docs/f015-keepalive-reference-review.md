# F015 包 0：后台保活参考复核

## 2026-11-10：主界面用量与等待约束

实施前直接复核Kai固定commit `ed6a3e0c48e10de9e2852e939fa4531975352bff` 的
`composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/HeartbeatPromptBuilder.kt`、
`data/TaskScheduler.kt` 的runHeartbeat段和Android的 `tools/ProcessManager.kt`，以及PalmClaw固定commit
`75a42aeda0e580ebad0de4a38c00c94146148366` 的 `heartbeat/HeartbeatService.kt` 全文和
`tools/CronTools.kt` 的任务创建/执行边界。两个参考仓库继续保留，未fetch或替换版本。

沿用具体触发放user、调度负责将来唤起、运行中进程可独立检查状态的职责边界。
本项目仅新增脉搏框架system提示，不移植参考调度器、cron内部创建限制、进程管理器或执行时限；
没有复制参考源码。维护者授权补齐手动脉搏路径，普通聊天/群聊不注入此约束。
短sleep轮询已运行进程明确允许，不给全局shell加过滤器；提示不等于保证模型不会违规。
UI仅以compact控制主界面展示，抽屉不变；截图和实际工具调用待维护者安装验收。

## 2026-11-10：脉搏做梦与循环任务说明

实施前阅读 Kai 固定 commit `ed6a3e0c48e10de9e2852e939fa4531975352bff` 的
`composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/MemoryStore.kt` 全文，
并复核 PalmClaw 固定 commit `75a42aeda0e580ebad0de4a38c00c94146148366` 的
`app/src/main/java/com/palmclaw/agent/MemoryConsolidator.kt` 全文及
`app/src/main/java/com/palmclaw/memory/MemoryStore.kt` 的会话进度读写部分。本地参考仓库继续保留，未拉取或替换版本。

采用 Kai 的数据层原子更新、明确来源/键作用域，以及 PalmClaw 的每会话进度、受限批次和失败不推进原则。
本项目复用现有 MemoryConsolidationScheduler、MemoryRepository 和时间戳进度，不复制参考源码、全局记忆正文或消息数量进度，
尤其不按消息总数截断对话。脉搏通过 facade 加入整理器专用解析，只使用 __pulse__ 桶；普通助手和群组不变。
自动回复仅在成功保存后交给现有调度器，临时触发仍不落库，待审批不当作完成轮次。

任务循环后端已具备，此轮严格只补本项目 schedule_task 的英文 description；不移植两仓调度系统、任务UI或时间上限。
自动整理默认关闭、DeepSeek Web记忆写入拒绝、保活和审批规则均沿用现有实现。离线测试不代表实际模型或设备截图已验收。

## 2026-11-10：闹钟独立开关、脉搏记忆与媒体输入

实施前重新直接阅读固定版本 Kai 的 `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/tools/SetAlarmTool.kt`
及 `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/MemoryStore.kt`，并阅读 PalmClaw 的
`app/src/main/java/com/palmclaw/tools/MemoryTools.kt`（工具/引擎/作用域与显式失败部分）。本机参考目录继续保留。
沿用 Kai 系统 Clock Intent 创建方式，新增开关只影响本项目注册/过滤；不改系统闹钟实现。
记忆借鉴通过数据层处理作用域/写入、界面只发明确操作的边界；本项目继续复用 MemoryRepository 的 __pulse__ 桶、
MemoryFilePage 的编辑入口，不复制 Kai 的 SettingsJsonList 或 PalmClaw 的全局正文记忆存储。
视频按本项目现有 UIMessagePart/编码器/请求工厂扩展，没有移植两仓的网络协议或猜测模型能力。
没有下载或替换参考仓库版本、复制参考源码、操作真实闹钟/模型账号或把离线夹具称为设备证据。

## 2026-11-10：F019 顶栏玻璃概率性偏移修正

本修正不改变闹钟，也不新增参考代码。依据维护者提供的修正单，将 `ChatTopBarCapsule` 与
`ChatInput` 的 bounds 统一为 `positionInRoot()`，并让 `LiquidGlassInputBackground` 只使用
宿主内部坐标和 padding。这样避免 Compose `AndroidView.update` 与 `onGloballyPositioned`
跨帧混用窗口绝对坐标。没有使用状态栏固定偏移、布局监听兜底或固定尺寸；设备截图和 logcat
仍待维护者安装修正版后验收。

## 2026-11-10：F019 闹钟工具与顶栏玻璃

闹钟实现前直接阅读 Kai 固定 commit `ed6a3e0c48e10de9e2852e939fa4531975352bff` 的
`composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/tools/SetAlarmTool.kt`：采用系统
`AlarmClock.ACTION_SET_ALARM` / `ACTION_SET_TIMER` 与对应 extras，只把系统 Intent 作为创建请求，
不自建 AlarmManager 记录、不做列表/编辑/删除。本项目按当前前台 Activity 启动 Intent，并额外做
严格整数/范围校验、后台拒绝和异常脱敏；没有照搬 Kai 的宽松数值转换或“已设置”成功文案。

PalmClaw 没有同名闹钟工具；本轮复核其固定 commit `75a42aeda0e580ebad0de4a38c00c94146148366`
的 `runtime/alwayson/AndroidProcessingPowerLease.kt`、`AlwaysOnGatewayService.kt`、
`tools/AndroidToolSupport.kt` 和 `ui/common/AndroidUiHelpers.kt`，只采用“由运行时/当前前台上下文
负责执行、失败显式返回”的边界。未复制源码，也未将后台心跳伪装为可启动系统 Clock。

顶栏玻璃修正继续沿用两仓“执行所有者与 UI 生命周期分离、由真实运行时状态驱动”的设计核对方式；
产品侧只改 `ChatTopBarCapsule` 的真实内容测量与已有窗口坐标换算，保留 `LiquidGlassRegion`
参数，不加入固定屏幕偏移。没有设备 logcat 或截图，玻璃尺寸/坐标根因只能作为静态修正，不能声称已实机确认。

## 2026-11-10：抽屉状态卡与白名单

改动前实际阅读固定版本 Kai 的 `ui/chat/composables/HeartbeatBanner.kt`、
`ui/settings/HeartbeatSection.kt`（心跳日志展示段）、`data/ScheduledTask.kt`，
以及 PalmClaw 的 `ui/settings/AutomationSettingsPage.kt`（Cron任务与日志分开、心跳设置）和
`heartbeat/HeartbeatService.kt`（已存下一次触发时间）。两仓 HEAD 与下表固定 commit 相同。
借鉴状态/任务/日志分离、显示已计算触发时间、主题色与明确事件回调的思路；
本项目仅改 Compose 展示与 whitelist 的纯列表变换，不复制参考源码/数据模型，
不重新解析 cron、不引入调度器、自动权限或参考项目的后台策略。
保留本项目输入式间隔、最新配置原子变换、通知内存边界与既有审批。
这次 UI 修正的五状态文字与白名单回归在根 DELIVERY 记录；没有设备截图或真实通知验证。

## 2026-11-10：心跳指令位置与摘要修正

改动前再次直接阅读上表固定版本的 Kai `data/TaskScheduler.kt:149-235` 和
`data/RemoteDataRepository.kt:2148-2168`：自动轮次以临时 USER prompt 开始，
使用 localHistory 和明确的 heartbeat conversation override，不把触发写入当前聊天。
同时阅读 PalmClaw `heartbeat/HeartbeatService.kt` 的 triggerNow 与
`runtime/GatewayRuntime.kt` 的 decideHeartbeat：人格在 system、具体检查任务在 user。
本项目复用现有 PulseTurnInput / ChatService 临时历史路径，保留已有完整历史，
把每轮指令放末尾 synthetic user，不移植它们的数据层或更宽松的静默判定。

摘要只恢复未涉及感知工具的轮次首行；短信发现用扫描后的数量、打码前去重人数。
不复制 Kai 的短信正文/通知预览，也不移植持久化收件箱、通知发送或参考项目的超时策略。
参考仓库留在产品目录外且固定版本不变；此项是源码核对，不代表模型或设备实测。

## 2026-11-10：包 11、12 感知层补充

实施前直接阅读上表所指固定 commit 的 Kai：
`notifications/KaiNotificationListenerService.kt`、`sms/SmsReader.android.kt`、
`sms/SmsSender.android.kt`（androidMain 下对应目录），以及 commonMain 的
`sms/SmsPoller.kt`、`data/TaskScheduler.kt`。同时核对 PalmClaw 的
`runtime/automation/AutomationRuntimeSchedulers.kt`、`heartbeat/HeartbeatService.kt`
和 `runtime/GatewayRuntime.kt` 内部心跳入口。参考仓库仍保留在产品目录外，未更新其版本。

- 采用 Kai 的自身/系统 UI/常驻过滤、首次轮询种水位跳过历史、每批上限和独立 lastAttempt 时间思路；本项目再以升序 ID 处理，避免超出一批时丢失中间消息，并用初始化标志区分空收件箱。
- 不采用 Kai 的持久化通知/SMS Store；遵从本任务的通知仅内存、白名单外只计数、验证码整条屏蔽和号码打码。没有复制参考仓库文件或代码。
- 采用 PalmClaw 的统一 runtime 调度、功能独立开关和处理入口思路；复用本项目唯一 PulseRuntime、claim/租约及 AppScope，不新增页面持有的轮询 Job、网关或独立定时器。
- 本项目审批实现以现有 Agent/ChatService 为准：AwaitingApproval 保留卡片，恢复走原工具流程并加副作用回执。send_sms 即使 AllowAll 也要确认；不照搬任何自动发送或默认放行行为。
- 用户先前取消的生成超时、休眠取消、空闲停止与累计上限均不恢复。参考复核不等于 Android 通知、权限或短信实机验收。

日期：2026-11-03。目标版本：`3.1.1-work.3 / 201`，仅 ARM64 Debug。

本次按维护者要求拉取 Kai 和 PalmClaw，用固定源码版本复核后台保活。两份参考仓库位于产品仓库外，不打入 APK 或产品源码 ZIP。

## 固定参考

| 仓库 | commit | 本地路径 | 许可证文件 |
| --- | --- | --- | --- |
| https://github.com/SimonSchubert/Kai | `ed6a3e0c48e10de9e2852e939fa4531975352bff` | `/home/li/CDX/rikkahub-references/F015/Kai` | `LICENSE.txt`：Apache-2.0 |
| https://github.com/ModalityDance/PalmClaw | `75a42aeda0e580ebad0de4a38c00c94146148366` | `/home/li/CDX/rikkahub-references/F015/PalmClaw` | `LICENSE`：AGPL-3.0；另有商业授权文件 |

Kai 的首次克隆中断后重新获取了固定 commit，并以稀疏检出保留 Kotlin 源码、构建定义、Manifest 和许可证；未下载与复核无关的全部站点媒体。PalmClaw 为浅克隆。产品不依赖这两个本机目录。

## 核对结论与采用范围

### Kai

- `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/DaemonService.kt`：前台服务只维持进程，实际调度由独立对象负责；服务销毁不把 UI 当作任务所有者。
- `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/TaskScheduler.kt`：独立 `SupervisorJob`/scope，`start()` 检查现有活动 Job，避免重复循环；取消异常继续抛出。
- 我们的 `ChatService.launchGenerationJob` 已运行在 `AppScope`，保留原 Job 和 `finally` 释放路径；不改成跟随页面的 scope，也不增加第二个任务所有者。借鉴幂等启动，让重复开始/结束其他任务不重建电源锁续租循环。
- Kai 的 `START_STICKY`、定时任务、邮件/SMS/心跳等不是本包范围，未移植。

### PalmClaw

- `app/src/main/java/com/palmclaw/runtime/alwayson/AndroidProcessingPowerLease.kt`：仅在实际处理时持有非引用计数电源锁；状态更新幂等，先获取再续租，停止时取消回调并释放。释放和获取分别保护异常。
- `app/src/main/java/com/palmclaw/runtime/AlwaysOnGatewayService.kt`：以正在处理的会话集合驱动持锁，销毁时注销监听并关闭电源锁。
- `RuntimeForegroundLifecycleCoordinator.kt` 及测试：异步转换使用最新目标状态，防止旧转换覆盖新状态。我们现有前台服务状态与回调均在主线程，不额外移植其 Mutex；保留停止标志、回调身份校验及按 generationId 计数。
- 我们保留 5 分钟有界锁租约 / 60 秒续租；首次启动立即获取，后续相同活动状态不重置时间。释放旧锁异常不跳过获取；获取失败仍保留后续重试。无活动任务、销毁或系统停止时清理回调并释放。
- PalmClaw 的 `specialUse`、常驻网关、自动恢复与权限引导不适用于这批生成期保活，未移植。Manifest 的 `dataSync` 及系统 `onTimeout` 处理保持原有语义。

本次为既有实现的独立增量调整，参考上述思路，没有把两个仓库的源码文件复制进产品。

## 阶段 B / 心跳与监视器调度补充复核（2026-11-06）

阶段 A+B 收尾时再次直接检查同一固定 commit 中与心跳/自动化相关的实现：

- Kai `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/TaskScheduler.kt`：`start()` 使用独立长生命周期 scope 且幂等；轮询中的任务和 heartbeat 通过同一调度器分派，执行结果通过对应会话路径处理。我们的实现继续让 `PulseRuntime` 独立于 Compose 页面，并通过现有 `ChatService.runPulseTurn` 临时执行，不复制 Kai 的数据模型或邮箱/SMS能力。
- PalmClaw `app/src/main/java/com/palmclaw/runtime/GatewayRuntimeSupervisor.kt` 与 `GatewayRuntime.kt`：自动化入口经过统一 runtime ownership/supervisor；心跳记录触发时间后读取配置并执行内部轮次。我们的 Android 工程采用已有 `PulseClaims` 租约和唯一 runtime 调度入口做进程间去重，同时保留本项目“未回复用户消息则跳过且不更新真实心跳时间”的修订语义，不照搬其在模型决策之前写 `lastTriggeredAt` 的语义。
- Kai 与 PalmClaw 的任务/心跳执行都与界面生命周期分离。我们继续依赖 `AppScope`、前台服务与 `PulseRuntime` 的现有职责边界；没有新增页面持有后台 Job，也未将参考项目的自动恢复/常驻网关移植到 F015。

本补充为源码阅读与设计交叉核对，不代表引用或复制其产品源码；固定 commit 与许可证信息见上表。阶段 B 实际实现和自动验证见根 `DELIVERY.md`。

## 维护者明确覆盖任务包的规则

### 2026-11-09：3.1.2 / 实施后修正复核

再次阅读固定Kai的DaemonService、TaskScheduler，以及固定PalmClaw的GatewayRuntimeSupervisor/GatewayRuntime心跳入口。保持调度所有者与页面/前台通知分离，心跳在开始时记锚点、唤醒只判间隔；不复制它们的业务模型、自动恢复策略或网关。

本项目设置加载语义以SettingsStore源码为准：dummy.init=true，awaitLoaded等待false。本轮保留这个方向，监听init及pulse配置，防止加载完成事件被distinctUntilChanged吞掉。新工作区只创建文件区，安装继续由现有详情页明确触发；定价UI复用本项目CardGroup、主题颜色及输入组件，参考项目不作为价格数据来源。

- 移除旧 20 分钟 watchdog、设备休眠取消、进展时间记录及它们的检查 timer。
- 不增加原任务包提议的 5 分钟空闲 / 2 小时累计清醒自动停止。
- 电源锁的 5 分钟是单次资源租约，不是生成上限；只要任务仍活跃便续租。
- 保留界面耗时显示、手动停止、正常完成、真实异常和 Android 要求的服务停止处理。

## 回归与交付

`GenerationWakeLockRenewalTest` 覆盖：立即获取、超过旧时限继续续租、无任务不启动、重复启动保持同一期限、停止后晚到回调无效、活动状态变空、停止发生在回调内、幂等重入、空闲后启动、频繁并发状态更新、回调内停止后重启。

编译与 JVM 测试验证调度逻辑，不能代替 Android 系统服务、实际电源锁状态、息屏和厂商省电行为的设备验证。最终结果和可安装 APK 路径见根 `DELIVERY.md`；本批不提交、推送或发布。
