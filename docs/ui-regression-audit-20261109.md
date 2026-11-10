# 页面入口与控件回归检查（2026-11-09）

版本固定为 `3.1.2-work.1 / 201`，基于合并提交 `18c372d5` 及当前未提交改动。检查针对维护者报告的输入框消失和顶栏玻璃问题，并扩展到合并后的页面、按钮和功能接线。没有操作 Android 设备、模拟器或真实账号。

## 范围与方法

- 对全部 298 份 UI Kotlin 文件扫描空点击回调、恒定隐藏、模式条件和待办标记；其中页面文件 156 份。这是模式扫描，不能证明每个控件都经过交互验收。
- 对照 `RouteActivity.kt` 的 69 个 Screen 定义和 Nav3 entry：69 项全部注册，无缺失、无重复。Pulse 与 ShareHandler 的入口在 Activity 的启动/通知/分享 Intent 分支，不能因其他页面不直接引用而误判为孤立路由。
- 对照上游合并影响的 56 个 UI 路径，重点逐段复核 ChatPage、ChatInput、ChatList 拆分、GroupChatContent、消息操作/审批、FilesPicker、消息队列、模型选择、供应商/搜索配置、导出、历史多选、浏览器面板与悬浮窗。LaTeX 旧文件的删除对应 bettertext 渲染替换，非公式功能被删除。
- 复核普通聊天/群聊/脉搏分流、设置入口与路由、记忆首次写入的工具注册、DeepSeek Web 权限过滤、ask_user HITL、群聊 browser 过滤和错误卡片产生/展示链。

## 已确认并修正

| 问题 | 修正 | 位置 |
| --- | --- | --- |
| 动态 addView 的玻璃在输入控件上方，输入区只剩边框 | 每次添加区域后将前景 ComposeView bringToFront | LiquidGlassInputBackground.kt |
| 顶栏按钮位于采样背景中，会被玻璃再次折射、模糊和覆盖 | 公开 sourceContent/inputContent 参数保持不变；内部前景层全屏透明，容纳真实顶栏及底部输入；背景 Scaffold 只保留实测等高占位，按钮只组合一次 | LiquidGlassInputBackground.kt、ChatPage.kt、GroupChatContent.kt |
| 透明胶囊未记录 bounds；后续只有透明度变化时可能一直没有玻璃 | 即使透明也缓存布局，背景淡入时即可上报；销毁时清理 bounds | ChatTopBarCapsule.kt |
| 原生液态不可用时仍传 LIQUID 给直接 return 的 Haze 分支 | 对渲染使用 BLUR 副本，不写回用户配置；输入框和顶栏统一回退 | ChatInputHaze.kt、ChatInput.kt、ChatTopBarCapsule.kt |
| 顶栏描边明显、群聊和脉搏没有胶囊玻璃 | 原生顶栏无 1dp 描边；第一轮起点为 0.08 tint / 8dp blur / 0.22 refraction，第二轮按新修正单回调为 0.16 / 2dp / 0.60；群聊右侧三个按钮共用一个胶囊，脉搏保留原操作回调 | ChatTopBarCapsule.kt、ChatPage.kt、GroupChatContent.kt |
| 透明度动画导致每帧重复配置或登记诊断 | 按参数/布局变化设置及 refreshConfig，诊断仅变化后登记；alpha≤0.01 removeView；任一区域熔断隐藏全部玻璃 | LiquidGlassInputBackground.kt |
| 群聊菜单显示收藏却没有实际处理 | 接入当前会话 ChatVM 的既有收藏存储与 session 更新；切换会话未对齐时不传回调；公共列表保留 nullable 回调语义 | ChatPage.kt、GroupChatContent.kt、ChatList.kt |
| 群组已不存在时仍出现无效思考预算按钮 | 仅在当前群存在时启用 reasoningAvailable；模型选择仍按群聊规则隐藏 | ChatPage.kt |
| 音量键翻页长期捕获初始设置及初始遮挡高度 | 用 LocalActivity 和 rememberUpdatedState 读取当前开关、比例和 padding；可见高度最低为 0，避免键盘弹出后翻页方向反转 | ChatList.kt |
| 附件菜单的分段数量漏掉扩展行 | 固定行数由 1 改为 2；工作区缓存同时跟随 ownerScope 更新 | FilesPicker.kt |
| 多条长错误信息可能挤出复制/关闭等控件 | 错误列表限高并滚动；按 error.id 保持卡片身份；去掉群聊重复外层滚动 | ErrorCard.kt、GroupChatContent.kt |

原生玻璃每模式最多三个区域：输入、左胶囊、右胶囊。大屏没有左菜单时少一个。输入区域保持 tint 0.18、blur 0、refraction 1；折射高度/偏移分别按 8–28dp、6–22dp 限幅后缩放。没有修改 `liquidglass/` 渲染库。

文档示例的 `drawBorder` 没有添加为无消费者的 region 字段：实际描边由 Compose Surface 控制，已在原生顶栏分支关闭；模糊档仍保留描边。顶栏参数是待实机调节的起点，并非视觉验收结论。

前景根 Box 没有 clickable、pointerInput 或填色，空白区不主动命中指针处理器。另核对本机 Compose AndroidComposeView 字节码：dispatchTouchEvent 根据 PointerInputEventProcessor 的 dispatched 位返回是否处理事件。源码层面支持空白处继续交给下方消息视图；设备触摸、键盘及无障碍行为仍需实测。

## 确认保留的行为

- 群聊禁用消息概览滑动，脉搏禁用概览手势；两者并不是点击回调丢失。脉搏右上角原有概览开关、状态行进入设置的回调保留。
- 四处空 onClick：关于页版本行靠长按进入 Debug；PromptPage 的关键词 Chip 和 TagList 的标签 Chip 通过尾部按钮删除；CardGroup 中是 @Preview 示例。没有把这四处改造成新功能。
- 消息菜单的导出/分享进入多选，确认后仍弹出 ChatExportSheet；历史页单条删除改为上游的长按/多选删除，非入口丢失。
- ConversationInbox 保存并传递 GroupSender annotations，已有回归测试覆盖排队分发；群聊昵称/头像在 ChatMessage 读取同一 annotation，设备展示仍未验。
- 审批卡、ask_user 的提问/回答回调、非自动消失的 DeepSeek 能力错误均仍接通。没有新增 SwitchToApiProvider，也未删除既有 CheckFastModelSettings。
- 记忆开关启用即注册文件式和行式工具，空库可首次写入；DeepSeek 默认只读及自定义开关、群聊浏览器禁用、脉搏工具白名单保持。
- F007 reasoning_content 条件、F008 当前模型加载动画、随想/DeepSeek/AnySearch 搜索注册、工作区归属与安装入口、F018 确认后持久化均保留。
- 原应用身份图标、三语、数据库33、既有序列化字段和后台保活无生成时限约定保持。

## 验证与边界

- 本轮执行 `./gradlew test :app:assembleGithubDebug --offline --console=plain`，初轮 51 秒成功；修正新增高度状态的两条装箱提示后最终复验 17 秒成功。App 两渠道各 120 套件/828 项；去除 Play 重复渠道后全工程 237 套件/1596 项，1595 通过、1 项已有语料缺失跳过，0 失败/错误。其他模块的 UP-TO-DATE 结果明确为复用。
- 新增液态不可用时回退模糊且不改存储设置的回归。布局层级、按钮点击和渲染效果不能由这个 JVM 测试替代。
- 最终 Lint 用时 3分47秒，仍未通过：60 errors、322 warnings、4 hints；相比上批 61 个错误减少一个。60 个触发表达式均存在于合并提交，未新增屏蔽或 baseline；表达式对照不等同于另跑一轮基线 Lint。版本/ABI/签名、原图标/三语/schema、源码 ZIP 和 SHA-256 的本批最终核验见根 DELIVERY.md 与新交付目录；不使用旧 APK 代表本轮修改。

## 设备待验

同日第二轮按《顶栏玻璃强度回调》仅调整四处顶栏参数，以及将 Surface color 的 enableBlurEffect 判断统一到 effectiveDisplaySetting；描边判断、输入参数、宿主层级与区域定义不变。新 Debug 构建与 App GitHub 828 项单测通过；本轮未重跑全工程测试或 Lint，上述全工程与 Lint 数字仍属于第一轮。第二轮验收须先滚动列表：最顶部的胶囊透明、无玻璃属于预期，不能据此认定效果丢失。无真实设备诊断日志或视觉验收结论。

1. 液态/模糊/关闭三档，聊天/群聊/脉搏、大屏/小屏：顶栏、文字输入、加号、发送、停止、队列编辑/删除、思考和审批按钮完整可见且可点击。
2. native 前景的空白区能滚动、长按和点击消息；键盘弹出/收起、旋转、切换会话、打开抽屉后区域不偏移；顶栏不会被自身玻璃模糊。
3. 顶栏滚动淡入淡出、每模式最多三个区域，透明时停止录制；API<33 或熔断后全部退模糊。API<31 的系统模糊能力差异沿用既有实现。
4. 群聊收藏能添加/取消并在收藏页查看；群聊消息昵称/头像与排队归属、引用、@、压缩、附件和工作区隔离正常。
5. 连续多条/长错误可滚动并复制、关闭；DeepSeek 能力拒绝不跳转、不自动消失；ask_user 审批始终可见和回答。
6. 导出中文名文件、公式/删除线/代码、概览手势、浏览器浮窗移动后命中、后台任务输出/停止、真实搜索和登录继续按上批设备清单测试。
7. 脉搏服务重建、配置重排、1分钟心跳、空历史轮次、通知时间、峰谷保存和 rootfs 安装/回滚仍需设备及账号验证。

本次结果是有明确覆盖范围的源码检查及自动化验证，不能据此宣称没有其他 UI 或设备问题。
