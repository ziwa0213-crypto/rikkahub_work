# F020 change map — 3.3.0-work.1 / 207

日期：2026-11-17。F020基线655f8517c596be797eb081e9d1556404e82c6923。
以下列出本批相对该合并提交的全部变更路径，不含被忽略的artifacts生成物。
上游标记按固定3.3.0提交eb33917e的树确认；“上游文件”下次合并必须逐块重套本地接线。
新增本地模块与fork自有文件仍需随产品保留，不用上游整文件覆盖。

| 实际路径 | 归属 / 重套说明 |
| --- | --- |
| `.gitmodules` | 新增或fork自有文件：随本批保留 |
| `AGENTS.md` | 上游文件：需重套本批增量 |
| `CHANGES.md` | 新增或fork自有文件：随本批保留 |
| `DELIVERY.md` | 新增或fork自有文件：随本批保留 |
| `FEATURES.md` | 新增或fork自有文件：随本批保留 |
| `README.md` | 上游文件：需重套本批增量 |
| `ai/src/main/java/me/rerere/ai/provider/Model.kt` | 上游文件：需重套本批增量 |
| `ai/src/main/java/me/rerere/ai/provider/ProviderManager.kt` | 上游文件：需重套本批增量 |
| `ai/src/main/java/me/rerere/ai/provider/ProviderQuota.kt` | 上游文件：需重套本批增量 |
| `ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt` | 上游文件：需重套本批增量 |
| `ai/src/main/java/me/rerere/ai/provider/VideoInput.kt` | 新增或fork自有文件：随本批保留 |
| `ai/src/main/java/me/rerere/ai/provider/providers/ProviderMessageUtils.kt` | 上游文件：需重套本批增量 |
| `ai/src/test/java/me/rerere/ai/provider/ProviderSettingForkCompatibilityTest.kt` | 新增或fork自有文件：随本批保留 |
| `ai/src/test/java/me/rerere/ai/provider/VideoInputTest.kt` | 新增或fork自有文件：随本批保留 |
| `app/build.gradle.kts` | 上游文件：需重套本批增量 |
| `app/src/main/java/me/rerere/rikkahub/RikkaHubApp.kt` | 上游文件：需重套本批增量 |
| `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | 上游文件：需重套本批增量 |
| `app/src/main/java/me/rerere/rikkahub/data/ai/LocalModelSettings.kt` | 新增或fork自有文件：随本批保留 |
| `app/src/main/java/me/rerere/rikkahub/data/datastore/DefaultProviders.kt` | 上游文件：需重套本批增量 |
| `app/src/main/java/me/rerere/rikkahub/data/sync/importer/ChatboxImporter.kt` | 上游文件：需重套本批增量 |
| `app/src/main/java/me/rerere/rikkahub/data/sync/importer/CherryStudioProviderImporter.kt` | 上游文件：需重套本批增量 |
| `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt` | 上游文件：需重套本批增量 |
| `app/src/main/java/me/rerere/rikkahub/di/ViewModelModule.kt` | 上游文件：需重套本批增量 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/FilesPicker.kt` | 上游文件：需重套本批增量 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt` | 上游文件：需重套本批增量 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ProviderQuotaText.kt` | 上游文件：需重套本批增量 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ui/LoadingAnimation.kt` | 新增或fork自有文件：随本批保留 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt` | 上游文件：需重套本批增量 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderPage.kt` | 上游文件：需重套本批增量 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/ProviderConfigure.kt` | 上游文件：需重套本批增量 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/locallm/LocalModelList.kt` | 新增或fork自有文件：随本批保留 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/locallm/LocalModelSheet.kt` | 新增或fork自有文件：随本批保留 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/locallm/ProviderConfigureLlamaCpp.kt` | 新增或fork自有文件：随本批保留 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/locallm/SettingLocalLlmViewModel.kt` | 新增或fork自有文件：随本批保留 |
| `app/src/main/java/me/rerere/rikkahub/utils/ErrorText.kt` | 新增或fork自有文件：随本批保留 |
| `app/src/main/res/values-zh-rTW/strings.xml` | 上游文件：需重套本批增量 |
| `app/src/main/res/values-zh/strings.xml` | 上游文件：需重套本批增量 |
| `app/src/main/res/values/strings.xml` | 上游文件：需重套本批增量 |
| `app/src/test/java/me/rerere/rikkahub/data/ai/LocalModelSettingsTest.kt` | 新增或fork自有文件：随本批保留 |
| `docs/f020-change-map.md` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/.gitignore` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/LICENSE.llama.cpp` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/README.md` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/build.gradle.kts` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/consumer-rules.pro` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/native/llama.cpp` | 新增Git子模块：固定v1.0.0精确指针，不打包实际内容 |
| `llama-cpp/native/llama.cpp.commit` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/AndroidManifest.xml` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/cpp/CMakeLists.txt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/cpp/jni_util.h` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/cpp/llama_jni.cpp` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/ChatDeltaTracker.kt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/ChatRequestMapper.kt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/ContextPlanner.kt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/GgufModelInfo.kt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LlamaCppCatalog.kt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LlamaCppInstall.kt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LlamaCppJni.kt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LlamaCppProvider.kt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LlamaCppRuntime.kt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LocalMediaDecoder.kt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LocalModelDownload.kt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LocalModelStore.kt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LocalStream.kt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/test/java/me/rerere/llamacpp/ContextAndRequestTest.kt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/test/java/me/rerere/llamacpp/LocalFilesTest.kt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/test/java/me/rerere/llamacpp/LocalModelDownloadTest.kt` | 新增或fork自有文件：随本批保留 |
| `llama-cpp/src/test/java/me/rerere/llamacpp/LocalStreamTest.kt` | 新增或fork自有文件：随本批保留 |
| `settings.gradle.kts` | 上游文件：需重套本批增量 |

共69条路径（包括子模块gitlink）；文件内容与源码ZIP集合分别通过SOURCE-MANIFEST/VERIFICATION核验。

## 重套要点

- ProviderSetting/DefaultProviders/DI：local_llamacpp固定UUID、新字段默认值、注册与copyProvider齐全，不进入Types，不扩展API额度或内置搜索。
- ProviderMessageUtils：只把既有PartGroup/groupPartsByToolBoundary公开给模块复用，原逻辑不变。
- FilesPicker/VideoInput/ModelList：AUDIO/VIDEO仅新增枚举末尾，本地模型能力分支不扩散到原API协议。
- 供应商页/路由/Koin：本地专用配置与列表，隐藏API页签/测试/分享，仍保留原启用/保存和错误显示。
- Settings模型同步：只管理本地条目，保留UUID、原API设置；删除后清理引用并使用原会话模型回退。
- 流式：每片段整段parse，Partial与终局区分；checked trySend与UNLIMITED一起保留，停止不发正常Finish；工具/思考模板标记须保留。
- native：C++异常不越过JNI；parser票据生命周期、媒体token预算、批量prefill、UTF-8与stop前缀处理、cancel/free锁不可拆散。
- 构建：rikkahub.abi只控制单次交付，不删除默认双架构支持；NDK27.2、共享STL和16KB对齐保持。
- 资源：三份各57key，固定原桌面/About图标；其他上游资源照常同步。

## 回滚与保数据

F020尚无独立功能提交，不提供虚构revert SHA。以基线逐文件审阅反向补丁，不整树reset。
已有local_llamacpp设置必须继续可反序列化；保留类型兼容壳和默认值，不能用删除类型作为产品降级。
Room34及33历史schema不回退。已安装权重不通过源码回滚静默删除。

