# F015 包 0：后台保活参考复核

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

- 移除旧 20 分钟 watchdog、设备休眠取消、进展时间记录及它们的检查 timer。
- 不增加原任务包提议的 5 分钟空闲 / 2 小时累计清醒自动停止。
- 电源锁的 5 分钟是单次资源租约，不是生成上限；只要任务仍活跃便续租。
- 保留界面耗时显示、手动停止、正常完成、真实异常和 Android 要求的服务停止处理。

## 回归与交付

`GenerationWakeLockRenewalTest` 覆盖：立即获取、超过旧时限继续续租、无任务不启动、重复启动保持同一期限、停止后晚到回调无效、活动状态变空、停止发生在回调内、幂等重入、空闲后启动、频繁并发状态更新、回调内停止后重启。

编译与 JVM 测试验证调度逻辑，不能代替 Android 系统服务、实际电源锁状态、息屏和厂商省电行为的设备验证。最终结果和可安装 APK 路径见根 `DELIVERY.md`；本批不提交、推送或发布。
