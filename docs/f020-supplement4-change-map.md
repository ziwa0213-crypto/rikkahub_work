# F020 补充四实施映射（2026-11-18）

版本3.3.0-work.1 / 207、DB34，承接补充三的未提交增量。
需求来自补充四00/01；随包02-AGENTS与本地改前完全相同。
维护者最新要求为Release ARM64，覆盖附件Debug示例，不自动commit/tag/push/发布。

## 实施与差异

- 新建默认8192只在withLocalModels创建Model时生效，原Model默认和已安装对象原样保留，无迁移。
- buildBudgetedSystemPrompt保存原system拼接顺序与换行；非本地不估算schema。仅本地字符/3超过输入55%丢schema及所有工具prompt，剩余超过75%丢记忆两段。人格、脉搏约束、原历史和存储不被截断；现有Provider整轮历史裁剪不变。
- 估算包含工具schema（避免tool.systemPrompt很短却工具定义巨大）。优先用同名Ready模型的实际窗口，因为训练上限可能小于用户设置；未Ready仅用设置与既有上限，不加载或重载。
- 实际StepRequest定义在ai/agent而非任务书所写文件，Provider只收到params；AgentLoop又会用request.tools覆盖params.tools。故省略决定放在TextGenerationParams的@Transient localPromptDrops，默认空，不进入持久化/JSON；AgentLoop仅LlamaCppLocal+TOOLS时保留空定义。执行/审批恢复表不改，API仍原样注入request.tools。
- Provider两条省略提示并入现有媒体notice前缀，流式和非流式一致。失败才调用原生countTokens计system文本、同一schema JSON及history请求JSON；它们不含完整模板的边界开销，故显示“约”，不要求相加等于真实输入token。取消不吞、计数失败显示?；真实fit/usage仍由countApplied掌管。
- 已有local_llm_context_hint位于三语2652附近，且模型详情使用它；更新旧key并在输入框复用。实际新增3个key、更新1个，而非任务书F1所写新增4个；不留重复key或旧“按可用内存缩窗”说明。
- Runtime线程cores.coerceIn(2,6)、日志含threads，batch/ubatch512不改。参考固定rikkahub-agent a88f5854的ContextPlanner分请求预算及Provider系统计数职责，不引入其Q8、懒加载、内存缩窗或第二引擎。Kai/PalmClaw及脉搏调度保持。

## 验证边界与回滚

- 离线验证新8K/旧16K和4K保留、工具和记忆阶梯、schema占用、Ready实际窗口、API原system字节与无schema估算、AgentLoop流式/非流式不恢复工具、metadata不序列化、失败分解/未知值/取消及线程数。
- 三语XML、占位符、原21应用图标、DB34、固定native子模块及签名/ARM64/非debuggable作为出包核验。未运行ADB/模拟器/真实GGUF，不标手机A1–E1或tok/s通过。
- 本批为“让它能说话”的提示适配修补，极长必需人格或用户消息仍可明确TOO_LONG；没有假装任意4K请求都能放下。
- 回滚只相对上一份SOURCE-MANIFEST反向本批文件中的具体块；先移除Factory本地预算与AgentLoop例外，再移除瞬态metadata/notice与诊断，共享调用点同时处理。默认8K可改回16K但不能改写已保存模型。不得整树reset/整文件还原覆盖补充三、F020或上游合并。
- 新产物只进artifacts/3.3.0-work.1/f020-supplement4-release-20261118。源码根含DELIVERY，保权限/软链和六个原有jniLibs依赖；排除APK/artifacts/缓存/权重/签名密钥/参考仓库与实际llama.cpp，保留固定指针及恢复步骤。

## 逐文件清单（相对补充三源码包）

| 文件 | 本批变化 |
| --- | --- |
| `AGENTS.md` | 当前规则与优先级 |
| `CHANGES.md` | 用户可见更新 |
| `DELIVERY.md` | 本批交付与验收边界 |
| `FEATURES.md` | 实施台账与回滚边界 |
| `ai/src/main/java/me/rerere/ai/agent/Agent.kt` | 说明本地省略的例外 |
| `ai/src/main/java/me/rerere/ai/agent/AgentLoop.kt` | 防止重新注入已省略工具 |
| `ai/src/main/java/me/rerere/ai/provider/Provider.kt` | 瞬态省略标志 |
| `ai/src/test/java/me/rerere/ai/agent/AgentLoopTest.kt` | 流式/非流式/非本地/序列化回归 |
| `app/src/main/java/me/rerere/rikkahub/data/ai/ChatRequestFactory.kt` | 按预算接线 |
| `app/build.gradle.kts` | JVM测试复用llama模块已有org.json版本，不进生产包 |
| `app/src/main/java/me/rerere/rikkahub/data/ai/LocalModelSettings.kt` | 新模型默认8K |
| `app/src/main/java/me/rerere/rikkahub/data/ai/LocalPromptBudget.kt` | 新增本地降级决策 |
| `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt` | 新notice本地化 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt` | 本地上下文建议 |
| `app/src/main/java/me/rerere/rikkahub/utils/ErrorText.kt` | 五段分解与未知? |
| `app/src/main/res/values/strings.xml` | 英文三增一改 |
| `app/src/main/res/values-zh/strings.xml` | 简中三增一改 |
| `app/src/main/res/values-zh-rTW/strings.xml` | 繁中三增一改 |
| `app/src/test/java/me/rerere/rikkahub/data/ai/LocalModelSettingsTest.kt` | 新建与旧设置保留 |
| `app/src/test/java/me/rerere/rikkahub/data/ai/LocalPromptBudgetTest.kt` | 新增阶梯和API字节回归 |
| `app/src/test/java/me/rerere/rikkahub/utils/ErrorTextTest.kt` | 三语分解与兼容兜底 |
| `docs/f020-supplement4-change-map.md` | 本清单 |
| `llama-cpp/README.md` | 预算与真实计数边界 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/ChatRequestMapper.kt` | 复用同一schema JSON |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LlamaCppProvider.kt` | 实际窗口、notice、失败诊断 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LlamaCppRuntime.kt` | 线程与日志 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LocalPromptDiagnostics.kt` | 新增失败分解及notice映射 |
| `llama-cpp/src/test/java/me/rerere/llamacpp/LocalPromptDiagnosticsTest.kt` | 新增分解/取消/notice/线程回归 |
