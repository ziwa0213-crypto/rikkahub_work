# 上游 3.1.2 合并复核与 F015/F018 修正

日期：2026-11-09（Asia/Shanghai）。目标：`3.1.2-work.1 / 201`。维护者指定的 201 覆盖任务包建议的 202。本批交付 ARM64 GitHub Debug APK 与精简源码 ZIP；最终验证、文件路径及设备待验项见根 `DELIVERY.md`。

## 固定基线

- 合并前 F015 提交：`4b336d85`。
- 上游标签 `3.1.2`：`fe5a28f9a25fb6267065881d7738dd909ad9e51e`。
- 已有合并提交：`18c372d58f77da7c8a1c1f59042993e01be2bd5b`，父提交依次为上述本地基线和上游提交。
- `git diff 4b336d85..18c372d5 --shortstat`：340 个文件，新增 35,535 行、删除 38,687 行。包含旧 highlight 的移除及 material-color-utilities 内联，不能将行数理解为全部是新增业务代码。
- F015 实施后修正与 F018 是合并后的未提交增量。本轮收尾没有再次 commit、tag、push 或发布。

## 上游变动与保留边界

接受 bettertext 渲染、公式/删除线改进、ConversationInbox 与 Notice、聊天列表拆分、浮动顶栏与消息概览、SSH 跳板机、后台 Shell、世界书排序、导出文件、模型多关键词搜索及余额请求方式等更新。具体用户可见条目见 `CHANGES.md` 本版章节。

保留群聊、脉搏、F001–F017 与三语增量。群消息 `annotations` 从旧 MessageQueue 搬到 ConversationInbox 的消息结构、enqueue 参数和赋值路径，不能只保留生成端。F006 输入框和顶栏左右胶囊继续共用原生液态宿主、最多三个稳定区域，透明区域为 GONE；未恢复 hazeGlass/旧兼容档。仅桌面和关于页应用身份图标固定，其余图标随上游同步。

数据库仍为 33，`33.json` 不变。仅英文、简中和繁中字符串，缺失繁中增量已补。原应用身份资源以 `643191229dcbeb4972e3772986818605633ace92` 的 21 个 launcher 文件为基准逐字节核对。

## 冲突路径与处理

用 `git merge-tree --write-tree --name-only --messages 4b336d85 fe5a28f9` 重现合并关系，只用于复核冲突，不改当前 index 或工作区。下表共 29 个路径；combined diff 中其他手工整合文件不算额外真实冲突。

| 路径（相对仓库） | 处理 |
| --- | --- |
| `README.md` | 合并结构更新，保留 Work 品牌和 fork 指向。 |
| `README_ZH_CN.md`、`README_ZH_TW.md` | 保留 fork 已删除状态。 |
| `ai/src/main/java/me/rerere/ai/ui/Message.kt` | 接受上游 Notice 等消息结构，保留群聊 annotations 类型和序列化契约。 |
| `app/build.gradle.kts` | 接上游依赖/渠道变化，版本按维护者指定，Debug 限 ARM64。 |
| `app/src/main/java/me/rerere/rikkahub/data/ai/ChatRequestFactory.kt` | 接上游请求接口，保留群聊/脉搏上下文与工具边界。 |
| `app/src/main/java/me/rerere/rikkahub/data/ai/tools/ChatToolFactory.kt` | 接后台 Shell 工具注册，保留 F004/F009 和按模式过滤。 |
| `app/src/main/java/me/rerere/rikkahub/data/repository/WorkspaceRepository.kt` | 接上游远程/后台工作区接口，保留 F017 安装和归属语义。 |
| `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt`、`ViewModelModule.kt` | 更新上游 DI 签名，保留 fork 服务和页面依赖。 |
| `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` | 使用新收件箱/生成接口，保留群聊、脉搏及无应用层 watchdog 的生命周期。 |
| `app/src/main/java/me/rerere/rikkahub/service/MessageQueue.kt` | 接受上游删除，群聊 annotations 接线迁到 ConversationInbox。 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt` | 接新输入接口，保留群聊模式隔离和群级思考/审批接线。 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInputHaze.kt` | 兼容上游 containerColor/shape 参数，原生液态仍交宿主绘制。 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/CompressContextDialog.kt` | 接上游数字输入框，保留 fork 初值和三参确认接口。 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt` | 接新渲染器，保留群成员归属、群引用及 URL 引用计数。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt` | 接上游列表拆分，保留群消息/审批/错误展示接线。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` | 接浮动顶栏和新列表接口，保留三模式与 F006 三个玻璃区域。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceDetailVM.kt`、`WorkspaceVM.kt` | 接上游工作区接口，保留 rootfs 安装/回滚及所有者过滤。 |
| `app/src/main/res/values-{ar,de,ja,ko-rKR,ru}/strings.xml`（5 个） | 保留删除状态，不恢复其他应用语言。 |
| `app/src/main/res/values/strings.xml`、`values-zh/strings.xml`、`values-zh-rTW/strings.xml`（3 个） | 增量保留双方 key，并补齐繁中。 |
| `material3/material-color-utilities~4b336d85` | 旧 submodule 与上游目录冲突，采用上游内联源码。 |

## 任务书与实际源码的差异

1. **init 方向**：`Settings.dummy().init == true` 是尚未加载；`SettingsStore.awaitLoaded()` 等待 `!init`。因此不照抄任务书 §2/§4 的反向条件。服务仅在加载完成且开关关闭时停止，Runtime 同时观察 init 与 pulse，加载完成后响应真实设置。
2. **设置持久化**：F018 不能仅追加 Settings 字段；还需要 PreferencesStore 的独立 key 和 SettingsFields 映射，已一起接入。回归覆盖默认 32、0 和 7 的跨重启读回。
3. **工作区重名**：不会删除或覆盖非脉搏工作区。先恢复已有脉搏绑定/归属，确需创建且 Pulse 名已被占用时使用英文后缀。共享 Mutex 防止脉搏内部并发重复创建。
4. **工作区能力**：未装 Linux 的本地脉搏工作区可使用文件工具；Shell/命令监视器继续要求 READY。Linux 安装由详情页主动触发，不自动下载。
5. **峰谷星期与缓存**：按任务书示例，星期取调用当日；跨午夜覆盖该日的早段和晚段。输入总量先扣缓存再分别计价，避免缓存输入重复计算。未覆盖时段不计费，并明确是最近最多 200 条记录的估算。

F015 涉及的实现已再次参照固定 Kai/PalmClaw 源码，采用范围及许可证边界见 `f015-keepalive-reference-review.md`，未把参考仓库复制进产品源码包。

## 后续同步与回滚

下次合并必须逐项重套 annotations、三模式请求/工具过滤、F006 区域宿主、工作区安装/审批和 SettingsFields 接线；不可只以编译通过判断群聊归属或持久化正确。

若需撤回本轮未提交修正，先保存当前完整 diff 和新增文件，再逐块反向审核 `git diff 18c372d5 -- <相关文件>`；不要整文件恢复覆盖合并成果，不删除磁盘 `tool_approvals` 列或新增的兼容持久化字段。若要撤回已存在的合并提交，应另开维护任务评估两父关系和已交付数据，不能直接 reset 当前工作区。
