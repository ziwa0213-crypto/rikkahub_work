# F020 补充三实施映射（2026-11-18）

版本3.3.0-work.1 / 207，DB34，承接655f8517上的未提交F020增量。
以维护者20261118补充三包为当前规格；本轮无commit/tag/push/发布。

| 事项 | 主要落点 | 验证边界 |
| --- | --- | --- |
| A 胶囊 | LocalModelCapsule / ChatPage | 去Popup、唯一body接线、输入高度动态定位；编译可核，抽屉/IME/玻璃点击待手机 |
| B 预算 | ContextPlanner / Runtime / Provider / GgufModelInfo / ErrorText | 低可用内存不缩窗、逐层KV数组、输出预算、分配失败及真实数字JVM覆盖 |
| C 模板 | Model / JNI / Runtime / LocalStream / LocalChatTemplateEditor / LocalModelSheet | 默认字段兼容、UTF8覆盖、无模板警告、正常空输出/工具only/取消离线覆盖；真实模型待验 |
| D 视频 | VideoInput / ChatRequestFactory / ErrorText | 请求副本保元数据与其他part，OpenAI实际序列化无video_url，Google/Local留原消息；无联网重试 |
| E 文件 | ChatAttachmentRouter / WorkspaceAttachment / DocumentAsPromptTransformer / Picker / WorkspaceDocumentChip | 类型路由、2GiB、取消、metadata、备份过滤离线覆盖；真实SAF与大文件UI待手机 |
| F/G 清理 | SettingProviderDetailPage / ModelList / FilesPicker / 三语strings | 旧VIDEO枚举/消息保留，仅移除旧能力开关与tag；长按默认配置，手动编辑仍保留 |

## 相对任务书的必要适配

- 任务书R14/§10为Debug，维护者最新指令改为ARM64 Release，按新指令交付。全模块test与内部Debug已完成，最终仍需Release编译/签名/压缩核验；正式版覆盖与旧数据尚未实机验证。
- 去Popup后modifier放AnimatedVisibility根，以便位置、可见布局及点击区一致；保留玻璃前景输入框底部Box，它是ChatInput的布局宿主，不重复放胶囊。
- 原withoutVideoParts已经用占位文本而非直接丢弃；保留原函数，新增withoutVideoPartsAsText使用[视频]。不改ChatCompletionsAPI.videoContent或Google编码。
- 公共llama模型信息只报告第0层KV头，GGUF接口不暴露数组；追加有界metadata扫描，防止仅改规划器仍拿不到逐层值。不读取tensor/权重，不改固定llama.cpp源码。
- 暂用固定llama.cpp自带ChatML、Llama3与Gemma Jinja；来源与许可记录在LlamaCppChatTemplates和LICENSE.llama.cpp。没有擅自预设LFM模板。
- 实际导航为Screen.AssistantBasic/GroupConfig/PulseSettings，沿用现有工作区绑定入口；实际文件条目字段为sizeBytes。SSH的resolveFile返回null，改用支持两后端的fileSize核验存在。
- 利用Document既有metadata保留workspace_id和workspace_size，不增加Room字段。旧无metadata的工作区Document回落当前工作区。
- 为取消删除残片，额外对FilesManager、本地WorkspaceFileSystem、SSH导入补最小失败清理；仅删本次新目标，不删旧同名文件。SSH连接已断开时删除只能尽力，真实断网场景待验。
- 已有工作区编辑页是有大小限制的文本编辑器，视频/zip点击按任务书进入该页，文件不保证可播放或解压；大文件可能显示无法预览，可复制路径供工作区工具处理。未新增播放器/导出流程。
- 新Model.chatTemplate默认null，旧VIDEO枚举保留；Provider详情兼容参数focusModelId保留但不再自动展开。
- 内存融合三语说明同步当前预算逻辑：仅参与内存余量估算，不再声称会自动扩大上下文；没有改融合字段或设置默认值。

## 保留与回滚

- 不改变工作区临时目录清理、正式签名、原应用与关于页图标、三语、数据库迁移、F004/F007/F009边界。
- 本轮产物在artifacts/3.3.0-work.1/f020-supplement3-release-20261118，旧交付保留；最终不交Debug安装包。
- 回滚只能反向本补充相对上一份SOURCE-MANIFEST的增量；不能还原整文件覆盖此前F020、上游3.3.0合并或用户修改。JNI签名和Kotlin接口须一起回滚。
- 精简源码含源码所需六个Git跟踪的原生依赖、固定子模块指针与恢复说明；排除实际llama.cpp checkout、APK、缓存、权重、凭据与参考仓库。
- 所有测试为JVM/本机环回SSH与编译证据，未运行ADB/模拟器/真实权重；逐项设备待验见根DELIVERY。

## 逐文件增量清单（相对20261118前批源码）

| 文件 | 状态 |
| --- | --- |
| `app/src/main/java/me/rerere/rikkahub/data/files/ChatAttachmentRouter.kt` | 新增 |
| `app/src/main/java/me/rerere/rikkahub/data/files/WorkspaceAttachment.kt` | 新增 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/message/WorkspaceDocumentChip.kt` | 新增 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/locallm/LocalChatTemplateEditor.kt` | 新增 |
| `app/src/test/java/me/rerere/rikkahub/data/files/ChatAttachmentRouterTest.kt` | 新增 |
| `app/src/test/java/me/rerere/rikkahub/data/files/WorkspaceAttachmentTest.kt` | 新增 |
| `docs/f020-supplement3-change-map.md` | 新增 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LlamaCppChatTemplates.kt` | 新增 |
| `AGENTS.md` | 修改 |
| `CHANGES.md` | 修改 |
| `DELIVERY.md` | 修改 |
| `FEATURES.md` | 修改 |
| `ai/src/main/java/me/rerere/ai/provider/Model.kt` | 修改 |
| `ai/src/main/java/me/rerere/ai/provider/VideoInput.kt` | 修改 |
| `ai/src/test/java/me/rerere/ai/provider/ProviderSettingForkCompatibilityTest.kt` | 修改 |
| `ai/src/test/java/me/rerere/ai/provider/VideoInputTest.kt` | 修改 |
| `ai/src/test/java/me/rerere/ai/provider/providers/openai/ChatCompletionsVideoTest.kt` | 修改 |
| `app/src/main/java/me/rerere/rikkahub/data/ai/ChatRequestFactory.kt` | 修改 |
| `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/DocumentAsPromptTransformer.kt` | 修改 |
| `app/src/main/java/me/rerere/rikkahub/data/files/FilesManager.kt` | 修改 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/AttachmentChips.kt` | 修改 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatAttachmentPicker.kt` | 修改 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/FilesPicker.kt` | 修改 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/LocalModelCapsule.kt` | 修改 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt` | 修改 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt` | 修改 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantBasicPage.kt` | 修改 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` | 修改 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt` | 修改 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/locallm/LocalModelList.kt` | 修改 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/locallm/LocalModelSheet.kt` | 修改 |
| `app/src/main/java/me/rerere/rikkahub/utils/ErrorText.kt` | 修改 |
| `app/src/main/res/values-zh-rTW/strings.xml` | 修改 |
| `app/src/main/res/values-zh/strings.xml` | 修改 |
| `app/src/main/res/values/strings.xml` | 修改 |
| `app/src/test/java/me/rerere/rikkahub/utils/ErrorTextTest.kt` | 修改 |
| `llama-cpp/README.md` | 修改 |
| `llama-cpp/src/main/cpp/llama_jni.cpp` | 修改 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/ContextPlanner.kt` | 修改 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/GgufModelInfo.kt` | 修改 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LlamaCppJni.kt` | 修改 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LlamaCppProvider.kt` | 修改 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LlamaCppRuntime.kt` | 修改 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LocalModelState.kt` | 修改 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LocalRuntimeNative.kt` | 修改 |
| `llama-cpp/src/main/java/me/rerere/llamacpp/LocalStream.kt` | 修改 |
| `llama-cpp/src/test/java/me/rerere/llamacpp/ContextAndRequestTest.kt` | 修改 |
| `llama-cpp/src/test/java/me/rerere/llamacpp/LocalFilesTest.kt` | 修改 |
| `llama-cpp/src/test/java/me/rerere/llamacpp/LocalRuntimeStateTest.kt` | 修改 |
| `llama-cpp/src/test/java/me/rerere/llamacpp/LocalStreamTest.kt` | 修改 |
| `workspace/src/main/java/me/rerere/workspace/WorkspaceFileSystem.kt` | 修改 |
| `workspace/src/main/java/me/rerere/workspace/ssh/SshWorkspaceBackend.kt` | 修改 |
| `workspace/src/test/java/me/rerere/workspace/LocalWorkspaceBackendTest.kt` | 修改 |
| `workspace/src/test/java/me/rerere/workspace/ssh/SshWorkspaceBackendTest.kt` | 修改 |
