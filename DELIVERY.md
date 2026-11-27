# 2026-11-26：角色设置页入口精简

保持 **3.3.1-work.2 / versionCode 211 / DB34**。本节覆盖补充四中保留正文模式行与顶栏世界书按钮的旧口径。

- 产品仅修改`app/src/main/java/me/rerere/rikkahub/ui/pages/character/CharacterPage.kt`：顶栏「世界书」替换为「转移助手」，导航至`Screen.CharacterTransfer`；删除正文完整模式切换/转移Row及其后空行；只删除ModeSwitcher、orderedModes、selectedGroup三个import。
- 与本轮改前快照逐行比对，差异严格限于上述三项。其余import和代码保持原样，包括UiMode/Conversation、孤儿自动清理、搜索、标签、角色卡片/菜单/拖拽、删除确认、新建/导入及开场白；三份strings.xml、app/build.gradle.kts和DB文件的SHA-256核验全部一致。另更新CHANGES/FEATURES和本DELIVERY。
- 正文顺序为可选错误提示、搜索框、可选标签行、角色列表；只改变入口布局，不修改转移流程。

验证命令：

```bash
bash ./gradlew :app:compileGithubDebugKotlin \
  --no-daemon --no-parallel --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process --console=plain --stacktrace
```

结果：**BUILD SUCCESSFUL in 1m**，138个任务中2执行、136个UP-TO-DATE；App Kotlin编译实际执行，本次日志未出现warning或unused import警告。`git diff --check`通过。本次仅编译与静态复核，未重跑JVM测试/Lint，不沿用下方旧测试统计作为本轮结果，未进行设备或视觉验收。

证据目录：`artifacts/3.3.1-work.2/f024-role-toolbar-20261126-6TdlsQ/`，包含`compile.log`、`CharacterPage.before.kt`与`protected-before.sha256`。

手机待验：顶栏显示返回箭头、「转移助手」与加号，点击转移进入原页面；大标题下没有角色模式胶囊；搜索、标签筛选、角色卡片、菜单和拖拽仍需装包确认。维护者自行构建APK/源码ZIP，本轮未生成安装包或归档，未提交/推送/打标签。

回滚只逆向本节三个局部变更；同步上游时重套相同三处，勿整文件覆盖前批角色功能。

---

# 2026-11-26：F024 第二期补充四·角色设置整页对齐助手页

保持 **3.3.1-work.2 / versionCode 211 / DB34**。参照当前`AssistantPage.kt`完成角色设置页搜索、标签筛选、卡片、菜单及长按排序；不修改助手页或其他模式。本轮只修改源码并做编译/App JVM与静态复核，未生成APK/源码ZIP，未提交、推送、打标签、操作设备或真实用户数据；维护者自行构建，根DELIVERY仍由既有源码打包器强制包含，个人脚本仍排除。

## 逐文件改动（仅四个产品文件）

| 文件（相对仓库根目录） | 原 → 新 |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/pages/character/CharacterPage.kt` | 原LazyColumn中的模式/转移行移到固定Column；新增助手页同款胶囊TextField、自由标签FilterChip行及过滤状态；顶栏添加改为加号，原创建/导入下拉与世界书保留。原CardGroup角色项换为CharacterItem：20dp卡片、48dp头像、强调标题、启用记忆时的文件数、最多两个标签和+N；整卡点击详情，菜单仅新聊天/删除。角色项接入ReorderableItem/longPressReorder，过滤期间禁用，按最新设置持久化顺序。列表末尾原孤儿历史、原自动清理与新聊天/创建/导入/开场白实现保持。 |
| `app/src/main/res/values/strings.xml` | 仅增加character_page_search_placeholder = Search characters。 |
| `app/src/main/res/values-zh/strings.xml` | 仅增加character_page_search_placeholder = 搜索角色。 |
| `app/src/main/res/values-zh-rTW/strings.xml` | 仅增加character_page_search_placeholder = 搜尋角色。 |

另更新AGENTS/CHANGES/FEATURES及本DELIVERY台账；不新增产品文件、依赖或数据字段。记忆数复用`assistant_page_memory_count`，来自该角色`memory.getFilesFlow(character.id.toString())`的文件数，不是逐条记忆数；无角色复制、全局标签表或heroAnimation。

## 状态与删除边界

- 名称包含匹配忽略大小写；标签从现有角色tags聚合、去空白项、去重，选中多个取OR，名称与标签条件取AND。没有筛选时可长按拖拽，有任一筛选时手势和排序回调双重禁用。模式/转移行不再占拖拽索引，孤儿历史仍在末尾且不包ReorderableItem。
- 新列表删除确认继续走`cleanupCharacterConversations`，先通过现有ChatService停止/清理会话，再Repository清持久化历史，全部成功才移除角色并清当前选中ID。**不添加deletedCharacterIds**。失败显示原message、角色保留，取消继续传播；多会话中已完成的删除不能回滚，保留补充三的备份警示。
- 原`LaunchedEffect(settings.deletedCharacterIds)`与改前**逐字节一致**（SHA-256 `9ca83045564f283322672b7f284867892c1ec0224133837e29a8616aed2f3cc8`）；孤儿历史区只因外层布局缩进，不改内容或交互。startChat/createCharacter/importLauncher与备用开场白sheet也逐字核对一致。
- 必要适配：拖拽按from/to的稳定key在最新Settings内定位，避免用示例旧列表快照覆盖并发导入/编辑/删除；已消失标签与选中集合取交集，避免删掉最后一个带标签角色后残留不可见、无法解除的筛选。文件记忆Flow按角色remember，避免单纯重组反复重订阅；按当前助手页consumeWindowInsets，避免再叠加Scaffold底部Insets。都只在本角色页内生效。

## 任务书自查数字修正与输出

任务包有三处旧计数/描述不能照抄：

1. 三语改前实际各**2867**，本批各+1后为**2868**，不是包内2866→2867；原有文案逐字保持。
2. 本页清理函数应有**2处调用**：原孤儿自动清理、新菜单删除，而不是§6写的1处；不能为了凑1删掉任一流程。
3. deletedCharacterIds本来还被**必须保留的孤儿历史区**引用，不可能“只在原LaunchedEffect里”；本批确认新菜单没有添加该字段写入，全部引用仅在原清理与原兜底两块内。

等价rg自查的实际输出（路径缩写仅用于阅读，均为app/src下真实文件）：

```text
rg -c character_page_search_placeholder 三语strings.xml
values/strings.xml:1
values-zh/strings.xml:1
values-zh-rTW/strings.xml:1

rg -c assistant_page_memory_count 三语strings.xml
values/strings.xml:1
values-zh/strings.xml:1
values-zh-rTW/strings.xml:1

rg -n cleanupCharacterConversations CharacterPage.kt
92:            cleanupCharacterConversations(
331:                        cleanupCharacterConversations(

rg -n deletedCharacterIds CharacterPage.kt
88:    LaunchedEffect(settings.deletedCharacterIds) {
89:        val pending = settings.deletedCharacterIds
97:                    store.update { it.copy(deletedCharacterIds = it.deletedCharacterIds - pending) }
306:                if (settings.deletedCharacterIds.isNotEmpty()) item {
307:                    val history by produceState(emptyList<me.rerere.rikkahub.data.model.Conversation>(), settings.deletedCharacterIds) {
308:                        value = settings.deletedCharacterIds.flatMap { id ->

rg -n heroAnimation CharacterPage.kt
无匹配（0）

三语string key：2867→2868，各只增加character_page_search_placeholder
xmllint：三份通过；无重复key，三语名称集合一致
git diff --check：通过
受保护源码/资源文件SHA-256：1941个，差异0
版本3.3.1-work.2 / 211、DB34：未变
```

## 编译/App JVM与证据

```bash
bash ./gradlew :app:compileGithubDebugKotlin :app:testGithubDebugUnitTest \
  --no-daemon --no-parallel --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process --console=plain --stacktrace
```

- 本轮编译与完整App JVM：**BUILD SUCCESSFUL，1分22秒**，241个任务中15执行、226个UP-TO-DATE。`app:compileGithubDebugKotlin`及`app:testGithubDebugUnitTest`实际执行；未变依赖模块复用结果，不称全仓所有模块JVM或Release构建通过。
- **160套件 / 1089项：1088通过，1跳过，0失败，0错误。** 唯一跳过仍为DeepSeekImporterTest的可选真实归档用例（未提供DEEPSEEK_IMPORT_TEST_ARCHIVE），未拿用户数据补语料。本批未新增模仿UI实现的JVM用例；既有清理屏障11项全部通过，但不能把它当作新增页面菜单、搜索或拖拽的设备验证。
- 最后源码再核对时发现整理import误删了仍被startChat使用的Conversation导入，`final-source-recheck.log`记录该单处编译失败（35秒，测试未执行）；恢复原import后使用同一命令重新验证，**最终BUILD SUCCESSFUL，1分1秒，241任务中6执行/235个UP-TO-DATE**，见`final-source-fixed.log`。最终App编译和测试均实际执行，上述160套件统计/XML已按这次成功日志重新收集，不用失败调用后的旧报告冒充最终结果。
- 静态确认1941个受保护文件哈希不变，含AssistantPage、ChatDrawer、CharacterPicker、SettingPage、CharacterDetailPage、全数据/服务/生成/schema和原身份图标；`verify-source.mjs`直接比对本批before，不能用含前批改动的HEAD diff代替本批边界。
- **未执行Lint、instrumentation、模拟器或真机。** 前次Lint未通过状态未被本轮覆盖；没有执行真实Room/FTS/附件删除、搜索触摸、拖拽手感或截图对照，不宣称视觉/安装验收完成。

证据目录：`artifacts/3.3.1-work.2/f024-role-page-20261126-iBUfqf/`。含`compile-app-jvm.log`、中间失败的`final-source-recheck.log`、修复后成功的`final-source-fixed.log`、最终`TEST-SUMMARY.json`和160份`TEST-REPORTS/`快照，`resources-check.json`、`source-check.json`、`protected-before.json`/`protected-check.json`及可重跑的`verify-source.mjs`；`before/`保留本批四产品文件和四台账改前快照，均不进入源码包。

## V-U / V-F / V-R验收范围

| 条目 | 已核对与待验 |
|---|---|
| V-U1～U3 | 使用助手页相同圆角、间距、头像、强调字号、surfaceBright/无边框搜索及加号；世界书保留。静态接线完成，两页并排截图、深浅色/小屏/大字体和键盘布局由维护者装包验收。 |
| V-F1～F3 | 名称/标签过滤和记忆文件Flow、最多两标签/+N已接线；实时过滤、选取消、中文输入及记忆数显示待真机，JVM不证明Compose渲染。 |
| V-F4～F7 | 整卡详情、两菜单项、原开场白分支及共用删除流程静态核对；实际点击、SAF/备用开场白、三个会话连带删除及其他角色不受影响待设备验收。 |
| V-F8～F9 | ReorderableItem、稳定key和最新Settings写回、过滤双重禁用已接线；长按拖拽、重进后顺序、筛选期间不拖动及孤儿历史不参与需真机。 |
| V-R1～R3 | 模式/转移路由与创建/导入/开场白原实现不变，外层布局位置按要求调整；实际模式跳转、导入Toast/跳详情及内嵌世界书由维护者回归。 |
| V-R4～R5 | Kotlin/App JVM通过，三语实际各2867→2868，无新增失败。 |

回滚仅逆向本批CharacterPage局部和三语各1key，再对应台账；不得整文件覆盖补充一～三或其他已有dirty改动。后续同步上游保留角色专属模式/世界书/转移、新聊天及共用清理，只重套本页对齐层。本轮无新提交hash、APK或ZIP。

---

# 2026-11-26：F024 第二期补充三·角色设置与连带删除

保持 **3.3.1-work.2 / versionCode 211 / DB34**。在补充二基础上完成本包的名称对齐、重复入口移除，以及删除角色连带清理会话和旧孤儿会话自动清理；本节覆盖下方“保留弹层末尾角色列表入口、不清理孤儿会话”的历史口径。维护者自行构建APK/源码ZIP；本轮未执行assemble、归档、提交、推送、标签或设备命令，未操作真实用户数据。

**升级前请备份需要保留的旧角色历史。** 进入角色设置页会自动清理`deletedCharacterIds`对应的旧会话；删除角色的确认文案也明确提示会话一并删除、无法恢复。多会话清理不是整批数据库事务：中途失败时，已经删除的会话不会回滚，但角色/整批待清理ID保留，供下次重试。代码回滚不能恢复已经删除的数据。

## 逐文件改动（相对本批改前，不覆盖前批成果）

| 文件（相对仓库根目录） | 原 → 新 |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/pages/character/CharacterPicker.kt` | 弹层标题由character_list_title改为character_settings_title；删除末尾整个重复列表item。头像、选中高亮、角色选择、编辑铅笔、弹层尺寸及编辑使用的scope/nav保持。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/character/CharacterPage.kt` | 大标题改为角色设置；新增一个按deletedCharacterIds启动的LaunchedEffect，先清理捕获的全部owner，成功后在最新设置中减掉该批ID。失败使用已有message区域显示、保留全部ID；取消继续传播。原孤儿历史区块、ModeSwitcher、世界书/添加/转移及原交互保持。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatDrawer.kt` | 本批仅改角色设置DrawerAction的一处文案引用，其他模式及底部按钮不变。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/character/CharacterDetailPage.kt` | 删除确认后先清理该角色会话，成功才执行原角色移除/待删ID添加/当前角色选择清除，并返回上页；失败显示已有error、不移除角色、不导航。 |
| `app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt` | 在原deleteConversation后新增deleteConversationsOfOwner：按owner取当前快照，重载完整会话，复用原删除方法；重载时若已换owner则跳过。不改原删除、DAO、SQL或schema。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/character/CharacterConversationCleanup.kt` | 新增两入口共用的串行清理屏障：先清内存会话，再查持久化历史，全部成功才提交设置；检查取消，错误继续向调用方传播，无重试循环或新状态。 |
| `app/src/main/res/values/strings.xml` | −character_list_title、+character_settings_title（Character Settings），删除说明改为会话连带删除且不可恢复。 |
| `app/src/main/res/values-zh/strings.xml` | 同上；标题“角色设置”，删除说明“该角色的所有会话将一并删除，无法恢复。” |
| `app/src/main/res/values-zh-rTW/strings.xml` | 同上；标题“角色設定”，删除说明“該角色的所有會話將一併刪除，無法復原。” |
| `app/src/test/java/me/rerere/rikkahub/ui/pages/character/CharacterConversationCleanupTest.kt` | 新增11项JVM用例，直接调用上述生产辅助函数验证顺序、等待、失败、取消及待清理ID提交边界。 |

产品修改8个已有文件、新增1个辅助文件，另新增1个测试文件，更新AGENTS/CHANGES/FEATURES及本DELIVERY。不新增依赖、持久化字段或数据库迁移。

### 对任务示例的必要适配

- 本仓以ConversationSession内存状态为权威，不能只删库后允许后台生成重新落盘。两个入口均先复用**现有**`ChatService.deleteConversationsOfAssistant`，停掉/等待生成并替换内存会话，再调用新增Repository方法作持久化扫尾；未修改ChatService、生成链路或会话锁实现。
- 删除异常不能吞掉后继续移除角色，但也不能让未处理的页面协程异常导致崩溃。因此两入口保留取消传播，普通异常显示在原错误区域；辅助函数不捕获异常，完成回调不执行。
- 保留`Settings.deletedCharacterIds`字段、序列化、原写入和SettingsNormalization；成功只减捕获的pending，不清处理中新增的其他ID。失败不自循环重试，下次进入页面或待清理集合变化时再启动。
- **任务书2865是旧计数，补充二后实际为2867。** 本批三语均2867→2867，只新增1key、删除1key、修改1条说明，未为凑数删除其他文案。

## 编译和App JVM回归

实际命令（通过bash调用现有Gradle wrapper）：

```bash
bash ./gradlew :app:compileGithubDebugKotlin :app:testGithubDebugUnitTest \
  --no-daemon --no-parallel --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process --console=plain --stacktrace
```

- **BUILD SUCCESSFUL，1分41秒**；241个任务中16个执行、225个UP-TO-DATE。App Kotlin编译与`testGithubDebugUnitTest`实际执行，依赖模块复用未变的构建结果；不是完整Release构建或全仓所有模块JVM测试。
- **160套件 / 1089项：1088通过，1跳过，0失败，0错误。** 唯一跳过为DeepSeekImporterTest的可选真实归档用例，未设置`DEEPSEEK_IMPORT_TEST_ARCHIVE`；未使用用户数据补测试语料。
- 新增CharacterConversationCleanupTest的**11项全部通过**：顺序与其他owner不受回调影响、最后一项完成前不提交、内存清理失败不继续、部分失败保留整批ID、不自行重试、下次进入重试、保留新加ID、取消传播/步骤间取消检查、设置写入失败、输入owner快照隔离。其中“不自行重试”与“下次进入重试”分别包含在部分失败和重进用例内。
- 编译仅有CharacterPicker沿用ListItem重载的1条弃用警告，无编译错误。未为清理警告改AssistantPicker或共享组件。
- **这些是生产顺序辅助函数的回调式JVM测试，不是实际Room/FTS/聊天附件集成测试。** 没有据此宣称真实磁盘文件、索引、用户界面或设备验收通过。

证据目录：`artifacts/3.3.1-work.2/f024-role-cleanup-20261126-awX9Fm/`。

- `compile-app-jvm.log`：本轮成功日志与任务状态。
- `TEST-SUMMARY.json`、`TEST-REPORTS/app/testGithubDebugUnitTest/`：160份JUnit XML快照、计数、跳过原因；避免后续构建覆盖证据。
- `resource-check.json`、`protected-before.json`、`protected-check.json`、`static-checks.md`：三语/边界检查；369个受保护文件SHA-256改前后相同。
- `before/`：本批8个已有产品文件的改前快照，只用于本机差异核对，不进入源码包。

## 任务书§6自查结果

以下使用等价rg精确匹配核对，0结果按“无匹配”计，不当成检查失败：

```text
character_list_title，app/src：0
character_settings_title，三语strings.xml：3（每份1）
Screen.CharacterList，CharacterPicker.kt：0
deleteConversationsOfOwner，app/src：3
  ConversationRepository.kt:399 / CharacterDetailPage.kt:161 / CharacterPage.kt:71
character_delete_explanation，三语strings.xml：每份1
strings key总数：values 2867→2867；values-zh 2867→2867；values-zh-rTW 2867→2867
XML可解析、无重复key、三语名称集合一致；git diff --check：通过
版本3.3.1-work.2 / 211、DB34：未变
受保护文件：369个，差异0
```

## 验收范围与待验

| 编号 | 自动证据与设备待验 |
|---|---|
| V-A1～A5 | 静态确认标题、末尾item删除和原卡片/选择/编辑结构保持；弹层布局、触摸、角色切换及与助手弹层并排对比待真机。 |
| V-B1～B4 | 三处标题引用、三语和旧key零引用已核对；工作助手组件与设置页未变。实际各模式显示待真机。 |
| V-C1～C4 | 三语确认文案和按owner清理接线已核对，顺序/失败保护的11项JVM通过；真实三会话删除、其他角色/助手隔离待设备或Room集成验收。 |
| V-C5 | 复用原deleteConversation的FTS/收藏/节点/文件路径，未写新SQL；真实搜索无残留、文件处理和崩溃回归待验。既有deleteChatFiles是best effort：不检查file.delete返回值、文件索引异步清理，不能宣称所有物理文件删除已获验证，本批不改此共享例程。 |
| V-C6～C7 | pending整批成功后才提交、部分失败和取消保留ID、下次重试已由辅助函数JVM覆盖；页面退出重进、实际旧数据清理和错误区显示待真机。原历史兜底区块未变。 |
| V-R1 | Kotlin编译和完整App JVM通过，1089项无失败。 |
| V-R2 | 未改角色切换、导入导出、世界书/正则的实现；相关既有App JVM回归通过，实际页面操作仍待维护者验收。 |

未执行Lint、instrumentation、模拟器或真机；前次Lint未通过的状态不被本轮覆盖。数据结构、SettingsNormalization、DAO、schema、完整ChatService/会话服务、生成/宏/正则、AssistantPicker、SettingPage、RouteActivity、群组/脉搏专属源码、Manifest、版本文件及原应用身份图标相对本批改前均未变。

根DELIVERY继续由既有个人打包器强制纳入下次源码包，个人工具仍排除；本批未修改脚本，也未生成APK或ZIP。回滚仅逆向本批的小块改动和新增辅助/测试，保留前批所有dirty改动；之后同步上游时重套名称与清理入口，不用旧整文件覆盖，且不要将代码回滚误当成数据恢复。

---

# 2026-11-26：F024 第二期补充二·角色入口与选择器对齐

工程保持 **3.3.1-work.2 / versionCode 211 / DB34**，以维护者当前源码叠加补充包；下方210是前批历史记录。本轮只修改UI入口、角色选择器和三语文案，完成编译、完整App JVM回归及静态核对。APK与源码ZIP由维护者自行构建；本轮未执行assemble、归档、提交、推送、标签或设备命令。

## 逐文件改动

| 文件（相对仓库根目录） | 原 → 新 |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/pages/character/CharacterPicker.kt` | 新建215行角色专用组件。沿用AssistantPicker的NavigationDrawerItem、LookTop、名称/头像、ModalBottomSheet、Card/ListItem、尺寸及主题配色；只列settings.characters，当前角色高亮，行尾可编辑，末尾可进角色列表。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatDrawer.kt` | roleMode的居中文字按钮/DropdownMenu → CharacterPicker。删除旧characterPicker状态，保留selectedCharacter及其他菜单import；切换角色仍先更新ROLE/characterId，再进入该角色最近会话或新会话。点头像进当前角色详情，无角色时进列表。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt` | 身份入口的CHAT或ROLE共用助手列表 → ROLE显示角色并进CharacterList，CHAT显示助手并进Assistant；角色全局正则由所有模式可见 → 仅ROLE。两个共用服务的CHAT或ROLE条件逐字保持。 |
| `app/src/main/res/values/strings.xml` | 新增Characters标题及角色卡/世界书/正则管理说明两个key。 |
| `app/src/main/res/values-zh/strings.xml` | 新增「角色」「管理角色卡、世界书与正则脚本」两个key。 |
| `app/src/main/res/values-zh-rTW/strings.xml` | 新增「角色」「管理角色卡、世界書與正規表示式腳本」两个key。 |

产品共新增1文件、修改5文件；另外更新AGENTS、CHANGES、FEATURES和本DELIVERY台账。AssistantPicker、路由、数据层、宏/正则/生成链路、schema、原身份图标及群组/脉搏专属源码相对本轮改前哈希不变；不修已删除角色的孤儿会话。

## 编译与JVM回归

```bash
./gradlew :app:compileGithubDebugKotlin :app:testGithubDebugUnitTest \
  --no-daemon --no-parallel --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process --console=plain --stacktrace
```

- **BUILD SUCCESSFUL，1分33秒**；245个任务中16个执行、229个UP-TO-DATE。App Kotlin及完整`testGithubDebugUnitTest`本轮实际执行，未变化的依赖模块复用结果；不称为全仓所有模块JVM或完整Release构建。
- **159套件 / 1078项：1077通过，1跳过，0失败，0错误。** 唯一跳过为DeepSeekImporterTest的可选真实归档用例，未设置`DEEPSEEK_IMPORT_TEST_ARCHIVE`；未复制用户聊天数据作为测试语料。本批纯UI入口变更没有新增模仿实现的JVM测试。
- 新组件沿用现有ListItem调用形式，编译有两条该重载弃用警告，无编译错误。没有为清警告重构原AssistantPicker或共用Material组件。
- 静态核对：369个受保护文件的SHA-256与改前一致，三语XML可解析且名称集合一致，无重复；每份2865→2867，只新增指定两个key，旧文案未修改。版本211/DB34、`git diff --check`通过。
- 未执行Lint、instrumentation、模拟器或真机；既有Lint未通过状态不被本轮结果覆盖。视觉、触摸命中、弹层编辑跳转及最近会话切换仍需维护者装包验收。

本批证据目录：`artifacts/3.3.1-work.2/f024-role-entry-20261126-ryjXeU/`，含`compile-app-jvm.log`、`TEST-SUMMARY.json`、159份`TEST-REPORTS/`快照、`resource-check.json`、`protected-before.json`及`protected-check.json`。`before/`保存本轮修改前的两个共享UI文件和三份资源，供精确对照，不进入源码ZIP。

## 任务书§6自查与示例差异

1. 助手列表的精确导航`navigate(Screen.Assistant)`共**2处**：SettingPage的CHAT分支、ChatDrawer的工作模式else分支；角色分支均进入CharacterList。`Screen.Assistant`路由注册保留，AssistantDetail/AssistantMemory等不是助手列表导航，不能按前缀误删。
2. ChatDrawer内`CharacterPicker`匹配**2处**（import+调用），旧小写`characterPicker`匹配**0处**；DropdownMenu、DropdownMenuItem、TextButton的import及其他用法保留。
3. 三份资源中精确`name="setting_page_character"`各**1处**，精确`name="setting_page_character_desc"`各**1处**。任务书的宽泛`grep -c "setting_page_character"`实际应各为**2**，不能为了示例写的1删掉任一key；资源总数各恰好+2。
4. 角色弹层额外沿用当前AssistantPicker的Hidden/Expanded状态配置，编辑/列表导航前先hide再dismiss；这只是适配本仓现有弹层行为，不新增间距、字号、配色或数据状态。

## V1～V10验收边界

| 编号 | 本轮证据与待验 |
|---|---|
| V1～V4 | 静态确认ROLE角色入口与CHAT助手入口互斥，GROUP原群成员/群组入口保留；各模式实际显示和点击由维护者验收。 |
| V5 | 正则全局入口仅ROLE且仍使用无characterId的路由；四模式显隐待真机。 |
| V6～V7 | 已接入图标/名称/头像及角色底部弹层，头像、编辑、列表入口有对应路由；对齐、触摸及弹层动画待真机。 |
| V8 | 工作助手选择器及群组/脉搏渲染分支改前后不变；装包做四模式回归。 |
| V9 | ROLE/characterId写入等待与最近会话导航逻辑保持，selectedCharacter未改；实际切换与会话选择待真机。 |
| V10 | Kotlin编译及完整App JVM通过，上述1078项无新增失败；未进行APK或设备验证。 |

根DELIVERY继续由既有个人打包器强制纳入源码包，个人构建/上传工具仍排除，本轮未改脚本。回滚仅逆向本补充的UI小块、两个key×三语和新组件，保留全部前批角色核心、闹钟、附件及Shizuku改动；未创建本批提交，不提供整文件恢复命令。

---

# 2026-11-25：F024 第二期角色转正与酒馆核心

保持 **3.3.1-work.1 / versionCode 210 / DB34**。按第二期主任务书、六处校正、包04～07及最新变量通道补充完成本批实现。叠加前批闹钟、坏附件及Shizuku改动；没有回退或重写这些成果。APK / 源码ZIP继续由维护者自行构建，本轮不执行assemble、归档、提交、推送、标签或设备命令。

## 完成内容

- 角色转正为常驻板块，移除实验开关及长按调试读取；旧字段保留兼容备份。首次选择默认工作/角色，先加载设置、同步uiMode，再开放首屏；模式条按默认排序，修改默认值不切当前页。外部通知/分享目标优先且只解析一次，老用户转移提示排在默认选择之后，列表保留手动转移。
- 世界书仅用于角色会话；工作/群组/脉搏不显示、不注入，旧绑定数据不删。完成ST字段、会话隔离的关键词/递归/概率/分组/预算/粘性/冷却/outlet扫描及保角色深度注入；真实用户消息ID决定轮号，工具步骤和合成消息不重复推进。
- 按保留花灯参考移植宏和正则语义，接入角色/人设/历史环境、输入/输出/编辑/显示与角色/全局脚本。消息列表改为读取角色所属配置；一般正则不会在请求/显示重复替换，显示宏不写变量存储。
- 宏变量采用App级单消费者和按scope共享store/锁；快照、唤醒、屏障分离，latest/inFlight/written同锁接线，flush逐scope确认、注册即检查、成功清错。失败保留快照不自唤醒，下次offer/flush才重试；正常、错误、取消及输入保存/无回复发送、编辑、压缩路径均在finally确认。最新补充要求的五项JVM测试全部通过，不保证flush前强杀不丢。
- V2/V3 PNG/JSON角色卡与内嵌书/正则导入导出；保留原PNG、未知扩展/原条目与数组顺序。原始快照超过256KiB明确提示降级；元数据解压有16MiB上限，不影响之前不限大小的角色转移附件复制。
- 角色卡四分区、开场白/预设消息/深度提示/预算、世界书与五分区条目编辑器、角色/全局正则管理、SAF导入导出均已接入本仓UI。三语各新增121项，共2865项，名称集合一致、无重复。

## 验证结果

按三批分别编译/JVM：批1成功4分29秒，批2成功1分57秒，批3修正卡往返测试后成功3分15秒。最终完整App JVM回归及Kotlin编译 **BUILD SUCCESSFUL，55秒**；241个任务中6个执行、235个UP-TO-DATE。`app:testGithubDebugUnitTest`本轮实际执行，依赖模块编译复用缓存，不能称全仓所有模块JVM全跑。

- **159套件 / 1078项：1077通过，1跳过，0失败，0错误。** 唯一跳过为DeepSeekImporterTest的真实归档可选测试：未设置`DEEPSEEK_IMPORT_TEST_ARCHIVE`，没有为测试复制用户聊天数据。
- 本批直接相关10套件63项全部通过，涵盖宏、变量通道、扫描器、插入器、Codec、管线、模式顺序、SettingsFields、角色归属与会话配置。未删除/忽略四项旧世界书断言所在测试，只按工作模式不再注入的新规则更新预期，并保留旧ID不删除检查。
- 静态核对通过：旧角色实验门控读取为0；参考宏七文件及正则引擎除末尾换行外一致；三个原上游Transformer、Room schema和原身份图标相对HEAD无差异；版本210/DB34、三语集合及`git diff --check`通过。个人源码打包器继续要求根`DELIVERY.md`，个人工具不入源码包。
- **未执行全仓Lint、instrumentation、模拟器或真机。** 既有Lint未通过状态未被本次编译覆盖。没有真实SAF文件选择、PNG外部兼容性、通知首帧、360dp/大字体、主题对比、备份恢复或进程强杀验收。

证据目录：`artifacts/f024-phase2-20261125-Jb56XS/`。

| 文件 | 用途 |
|---|---|
| `batch1.log`、`batch2.log`、`batch3-recheck.log` | 三批编译/定向JVM成功日志。 |
| `final-app-jvm-recheck2.log` | 最后一次源码修正后的编译及完整App JVM成功日志。 |
| `TEST-REPORTS/`、`TEST-SUMMARY.json` | 最终159份JUnit XML及统计，防止后续构建覆盖。 |
| `static-checks.md` | 版本、三语、受保护代码及参考一致性检查结果。 |

实现落点、花灯/Plus固定提交、逐文件移植差异、任务示例的必要调整，以及 **V-A1～A21 / V-G / V-W / V-C1～C19 / V-D / V-R / V-U / V-X逐项验收**见 [F024第二期实施记录](docs/f024-phase2-implementation.md)。其中明确区分JVM通过、静态接线与手机待验，不能将本轮自动检查当作完整用户体验验收。版本与原应用图标保持，下一次维护者打源码包时根DELIVERY和此记录一起包含。

---

# 2026-11-25：个人构建/上传脚本防止终端输出误入源码

保持 **3.3.1-work.1 / versionCode 210 / DB34**。本轮只改维护者个人脚本和说明，不改变应用代码、构建版本或Git历史，不生成工程APK/源码ZIP、不提交/推送。个人脚本继续排除源码归档。

- 根目录 `fer and mode switching"`（15,915 bytes）和 `tatus --short`（10,814 bytes）已被HEAD跟踪，内容分别为less帮助文本和带颜色的Git差异输出，不属于项目源码。两文件已移至 `artifacts/terminal-output-cleanup-20261125-emHGQP/`，可恢复；原SHA-256与移后相同，工作区留下正常删除差异，下次源码提交会移除它们，不自动重写历史。
- `tools/package-delivery-source.mjs`增加共用源码路径预检；按NUL分隔读取Git路径，不依赖空格/换行拆分。检查已跟踪但未修改文件和未忽略新文件，拦截已确认两文件及根目录中带命令选项、控制字符、双引号/重定向等shell片段的可疑名称，逐条JSON转义显示。只阻止并提示核对，不自动删除文件；文件名从不作为shell代码执行。
- Release/Debug在Gradle之前检查并保存失败日志，源码上传在认证及自动提交之前检查；直接打包也在创建归档目录/ZIP之前检查，避免绕开菜单后混入。正常中文、空格、单引号名称和项目工具照常归档。
- 更新个人 `tools/BUILDING.md` 及两份现有离线测试。**34项离线场景全部通过**（构建脚本24项、上传脚本10项），Bash/Node语法检查、实仓路径预检及 `git diff --check` 通过。覆盖误入文件在已跟踪/未跟踪状态下均拦截、不执行Gradle/认证/自动提交/上传、不修改源文件、特殊名称不被当作命令、移出后恢复检查，并复核原版本输入、三Release/单Debug、报告、日志、同步冲突及PAT脱敏流程。
- 测试仅创建隔离模拟APK/ZIP和本地模拟Git远端，不构建真实Android、不连接真实GitHub、不操作原仓库提交或标签。原仓库HEAD仍为f71dba5a且index未暂存；两个异常文件删除留给维护者的下一次正常源码提交。

核验/恢复目录：`artifacts/terminal-output-cleanup-20261125-emHGQP/`。脚本日志分别为 `build-script-tests.log`、`publish-script-tests.log`；清理前实仓预检正确拒绝两个已跟踪文件，移出后实仓预检通过。

---

# 2026-11-25：脉搏 Shizuku 仅限 Doze 白名单（补充二）

保持 **3.3.1-work.1 / versionCode 210 / DB34**，叠加本页后两批后台闹钟及角色坏附件修正。维护者自行构建 APK / 源码 ZIP；本轮只实现、编译、定向 JVM 及静态检查，不执行 assemble、归档、提交、推送、打标签或设备命令。

## 完成内容

- 后台运行页新增持久化开关 `keepAliveShizuku=true`，原后台总开关仍默认关闭；未安装/运行/授权Shizuku时静默降级。状态区分未检测到、待授权、已授权，授权只由用户点击；拒绝/允许结果、binder断开/恢复和白名单完成均刷新现有revision，不轮询设置页。
- stock / Shizuku+ Drop-In 与“主版+Compat Hub”通过官方SDK接入；只有主版且没有stock包时提示兼容中心并提供打开Shizuku+入口。没有Shizuku时不引导安装，不自实现binder。
- ON_START等待设置加载后，重查前台状态及两个开关，尝试 `cmd deviceidle whitelist +本包名`。未就绪不消耗尝试机会；就绪后每进程最多尝试一次，包括已有豁免或失败。完成后刷新电池优化行；首次授权后下次回前台触发，不另设授权触发入口。
- 按最新补充删除 `PulseShizuku.startDaemon`、命令辅助类中的服务命令及 `pulseBackgroundStartDenied`，并删除对应两项测试。`PulseRuntime.startDaemon()`恢复原样：启动失败仅打日志；Runtime唯一Shizuku接入点为ON_START。本补充没有新增源码文件、触发源、调度锁、权限或导出组件。
- 固定api/provider **13.0.0**；仅查询两个包并增加受 `INTERACT_ACROSS_USERS_FULL` 保护的ShizukuProvider。不新增Android系统uses-permission，不改变minSdk。SDK合并的 `moe.shizuku.manager.permission.API_V23` 是Shizuku自定义授权，不能宣称最终Manifest完全没有新增权限条目。
- 白名单命令在IO执行，双输出流并行以8KiB缓冲丢弃，避免顺序读取死锁或无限积累内存；结束/失败/取消均销毁进程并关流。保留该命令独立10秒超时，取消继续传播；不恢复聊天/脉搏的生成超时，不增加shell循环或自动安装。
- 三语各保留7key，只改 `pulse_shizuku_ready`；成功文案要求两个开关均开启且系统实际报告电池优化豁免。已授权但未豁免或开关关闭时复用既有“已允许”，不假称白名单写入成功。其余6key、连接/授权流程和兼容中心提示不改。
- 原四层保活、服务与调度/Claims代码、原应用图标、版本/数据库及前两批修正保留。Kai / PalmClaw改前参考与采用边界见 `docs/f015-keepalive-reference-review.md`。

## 任务书的限制与必要差异

1. **V5改为普通shell手动启动预期失败**：`PulseDaemonService`、`PulseAlarmReceiver`、`PulseBootReceiver`仍为`exported=false`。普通shell不能启动这些私有组件；应用不再发出服务启动命令，不增加外部唤醒能力。root/system组件权限例外不属于本轮功能承诺。尚未操作设备，`not exported`仍是待采集的预期报错，不冒充实测。
2. 本桥接存在于应用进程内，不能在自身已被杀、没有既有唤醒入口时自行执行“复活”。原Alarm/Worker/开机/前台入口照常；OriginOS等私有冻结未必遵循标准Doze白名单，不保证进程因此存活更久。导出服务并用DUMP保护、打开RouteActivity均为未来待评估方案，本轮未实现。
3. 示例先后readBytes并不能防双流堵塞；改为并发有界内存排空。Remote waitFor是阻塞Binder事务，改用SDK显式alive检查和协程等待，以便取消时先清理进程。
4. 示例失败后不标记dozeAttempted会导致每次进前台都重试，与“每进程一次”冲突；实际就绪并通过开关门槛后原子占一次机会，失败也不自动重试。下一进程重新计算，不持久化此标记。
5. 附件V0关于本机Drop-In版是任务材料中的说明，本轮没有adb核实其包名、安装形态、真实权限或命令结果，不作为已验证证据。白名单命令也不假定必定成功，仍保留异常、非零退出和超时降级；设置页以真实系统豁免状态为准。

## 验证

- 本补充Kotlin编译及定向JVM **BUILD SUCCESSFUL**，**25套件 / 162项全部通过**，0失败、0错误、0跳过。耗时1分35秒，241个任务中17个执行、224个UP-TO-DATE。覆盖现有桥接13项，以及原配置、Claims、cron/心跳间隔、任务、感知、通知、工具策略与电源锁续租回归；未新增测试文件。

  ```bash
  ./gradlew :app:compileGithubDebugKotlin :app:testGithubDebugUnitTest \
    --tests '*Pulse*Test' \
    --tests 'me.rerere.rikkahub.service.GenerationWakeLockRenewalTest' \
    --console=plain --no-parallel --max-workers=1
  ```

- 本补充证据目录 `artifacts/3.3.1-work.1/pulse-shizuku-boundary-20261125-C18trg/`：`compile-tests.log`为成功日志，`TEST-REPORTS/`为25份JUnit XML快照，`TEST-SUMMARY.json`汇总数量及未执行范围，`static-checks.log`为最终静态检查。初版历史证据仍保留在 `pulse-shizuku-20261125-aDb6wQ/`，此前25套件164项包含现在已删除的两项服务测试，不能当作本补充的最终结果。
- 保留桥接测试覆盖：未就绪/开关关闭零命令、不消耗未就绪尝试、执行前重查、已有豁免、失败不重试、并发前台信号只尝试一次、双管各256KiB输出、非零退出、普通超时/取消、流获取/读取失败，以及两条管道保持打开时取消/超时均释放资源。JVM模拟不代表真实Shizuku Binder/Android管道验证。
- 静态确认原服务/调度/Claims与改前SHA256一致，两个receiver实现在未变的PulseScheduler.kt内；AlarmRequest、FilesManager和CharacterTransferVM的上一批哈希保持。Runtime相对HEAD只多ON_START白名单接线，startDaemon逐字恢复；桥接/helper中startDaemon与start-foreground-service零命中。三语XML均可解析、各2744个名称且集合一致/无重复，各含7条Shizuku文案，其余6条逐项不变。实际合并Manifest核对Provider权限、按变体替换authorities（本次Debug为`me.rerere.rikkahub.debug.shizuku`）、自定义API_V23授权、原服务及receiver未导出、版本210和minSdk26；`git diff --check`通过。
- 未运行全量JVM、全仓Lint、instrumentation、模拟器、ADB或真机；既有全仓Lint未通过状态保持，不以本轮编译代替手机验收。

## 待维护者真机验收

- **V0–V3**：分别验证stock/Drop-In、主版+Hub、只有主版和完全未安装/未运行；确认对应状态、打开入口及点击授权/拒绝后的即时刷新，未安装不弹安装引导。
- **V4（待验）**：两个开关均开、Shizuku已授权后重新进前台，记录 `dumpsys deviceidle whitelist` 中实际包名及电池优化行；命令失败后本进程不反复执行，修复授权/系统状态后重启应用再测。另观察OriginOS冻结，不能仅凭白名单存在推断长期保活成功。
- **V5（待验，预期拒绝）**：普通shell手动执行 `am start-foreground-service -n <实际包名>/.service.PulseDaemonService`，记录完整 `Permission Denial ... not exported` 输出。预期失败为正确结果；本应用不会执行该命令，不为此改导出属性。
- **V5b（待验）**：先记录实际 `isIgnoringBatteryOptimizations` 状态，再手动执行 `cmd deviceidle whitelist +<实际包名>`，记录命令输出、白名单条目及再次进前台后的系统状态/电池优化行变化。若此前已豁免，应记录前后均true，不虚构false→true。
- **V6 / V9**：分别关闭Shizuku开关和后台总开关，再进前台或触发原启动入口，不执行命令；Shizuku行仍可见。已写入的系统白名单不会因关开关自动移除。
- **V7**：停掉/重启Shizuku，设置页不重开即可更新状态；无崩溃、Toast或聊天错误卡。
- **V8 / V10**：原Alarm、Worker、重启重排、前台自愈和通知回归；三语言界面；上一批闹钟、角色转移继续按下节验收。

---

# 2026-11-25：角色转移坏附件与移动原因修正

工程版本保持 **3.3.1-work.1 / versionCode 210 / DB34**。本批依据「角色转移附件修复」包两份文档，叠加本地现有改动；上一批后台闹钟修改保留。APK / 源码 ZIP 由维护者自行构建，本轮不生成安装包或归档、不提交、推送或打标签。

## 完成内容

- 角色复制会话遇到缺失、权限失效或读取失败的源附件时，保留原 URL 与完整消息，继续复制其他附件及转移记忆。只清理该坏附件的不完整目标文件，不因它删除已复制成功的其他文件。
- 同一会话中重复出现的附件（含工具结果内嵌附件）只复制/统计一次；不同新会话分别复制到各自目录。只有复制成功的新 URL 才进入整批失败时的清理集合，原始 URL 不能进入该集合。
- 目标打开/写入失败（例如磁盘满）与源读取失败分开处理；前者继续报错并回滚本次复制文件，不触碰原会话和原附件。数据库批量插入事务及其失败清理保持。
- 转移成功后通过现有应用提示显示缺失或不可读附件数量，说明消息及原链接保留、附件可能为空；不把“不可读”断言为文件一定已删除。
- 第一步助手对话框解释“内置默认助手 / 群组成员”不能移动的原因；记忆页分别解释“全局 / 其他助手共用 / 角色只读引用”。移动条件完全保留；未改记忆迁移、消息复制字段语义、版本/数据库或实验开关。
- 英文、简中、繁中各新增5条原因文案和1条完成提示，删除旧笼统提示；含上一批闹钟资源后，三份资源名称集合一致，各2737项，无重复。

## 与任务包的必要差异

- §1改法A示例用runCatching吞掉全部复制异常，但V4要求磁盘满等失败仍清理整批副本。因此只容忍源端无法读取，目标写入和取消异常继续传播；新增小型流复制辅助函数用于可靠区分及JVM验证，不给普通聊天复制套用容错。
- 包内完成提示只提“原文件已不存在”，实际也可能权限失效/读取失败；三语提示同时说明这两类原因。附件无大小阈值，未增加新依赖、数据表或持久设置。

## 验证

- Kotlin编译与定向JVM检查 **BUILD SUCCESSFUL**，耗时1分11秒；245个任务中16个执行、229个UP-TO-DATE。命令如下，日志：`artifacts/3.3.1-work.1/character-attachment-fix-20261125-ZJ2hxs/compile-tests.log`。

  ```bash
  ./gradlew :app:compileGithubDebugKotlin :app:testGithubDebugUnitTest \
    --tests 'me.rerere.rikkahub.data.files.CharacterAttachmentCopyTest' \
    --tests 'me.rerere.rikkahub.ui.pages.character.CharacterTransferAttachmentsTest' \
    --tests 'me.rerere.rikkahub.ui.pages.character.CharacterTransferSelectionTest' \
    --tests 'me.rerere.rikkahub.data.model.CharacterScopeTest' \
    --tests 'me.rerere.rikkahub.data.repository.MemoryRepositoryTest' \
    --tests 'me.rerere.rikkahub.data.datastore.CharacterSettingsTest' \
    --tests 'me.rerere.rikkahub.data.files.ChatAttachmentRouterTest' \
    --console=plain --no-parallel --max-workers=1
  ```

- 共 **7套件 / 59项全部通过**，0失败、0错误、0跳过。对应9 / 3 / 9 / 9 / 21 / 4 / 4项，覆盖真实临时文件源缺失、源不可读/中途读取失败、目标打开/写入/关闭失败、取消传播、正常512KiB以上文件不截断、嵌套附件与重复URL、清理集合不含原始文件、五种移动限制及原有迁移/隔离规则。JUnit XML快照保存在同批`TEST-REPORTS/`；JVM目标流故障模拟不等于手机磁盘满实测。
- 静态核对三语XML可解析、名称集合一致且无重复；transferMemory、duplicateForCharacter、MemoryRepository及版本文件与改前一致，`git diff --check`通过。
- 未运行全量JVM、Lint、Android instrumentation、模拟器或真机。原始死链会话截图、附件正常显示、触摸提示和真实磁盘满回滚仍待维护者设备验收；本轮离线检查不代表这些项目实测通过。既有全仓Lint未通过状态不变。

## 待维护者真机验收

1. 使用原含死链附件的助手重新复制转移：可以完成，消息保留、坏附件占位、正常附件可显示；出现数量提示。
2. 内置默认助手与群组成员的移动按钮仍禁用，对话框分别展示原因；普通助手仍可选移动。
3. 全局、助手共用、角色引用三种记忆分别显示对应原因；私有记忆可选移动，全选/反选与源桶保护保持。
4. 目标存储不足时转移报错、本次副本被清理，原会话及原文件保持；正常附件大文件不因人为阈值被拒绝。
5. 记忆复制/移动、重试转移和角色模式切换照常；后台闹钟仍按下一节单独验收。

---

# 2026-11-25：系统闹钟后台调用修正

工程版本保持 **3.3.1-work.1 / versionCode 210 / DB34**。按维护者说明接受任务包旧基线差异，改前源码为 `f71dba5a`。本轮只修改源码、自动检查及交付说明，APK / 源码 ZIP 由维护者自行构建。

## 完成内容

- set_alarm不再强制前台，LocalTools注入Context后使用applicationContext + FLAG_ACTIVITY_NEW_TASK发送原ACTION_SET_ALARM / ACTION_SET_TIMER，参数和EXTRA_SKIP_UI保持。删除旧闹钟专用Activity跟踪文件及Application注册；其余Activity生命周期与脉搏调度不改。
- Manifest新增SYSTEM_ALERT_WINDOW；设置 → 通用设置新增「允许后台设置闹钟」，显示当前悬浮窗权限，点击进入带package的系统授权页，返回后刷新。授权状态不写Settings，不自动请求权限。
- 普通闹钟在发送前后读取nextAlarmClock，中间仅协程delay(600)，不阻塞线程。只有两次读取成功、时间发生变化且匹配请求时分时verified=true；否则不伪称保存成功。缺少后台启动豁免且无法确认时返回success=false/error_code=background_blocked；已获豁免或前台且无法确认时为request_sent/verified=false，明确提示去系统时钟核实。
- 倒计时不读nextAlarmClock，也不借已有闹钟给verified=true。启动异常脱敏、取消继续传播；不新增闹钟存储、自己的响铃服务或开机重排，不改L2_CREATE、闹钟独立开关或DeepSeek工具限制。
- 三语各新增3key、更新原有2条前台限制文案；仅values / values-zh / values-zh-rTW。Kai/PalmClaw固定参考已先复核，具体路径及采用边界见docs/f015-keepalive-reference-review.md。

## 与任务包的必要差异

- 包内209/b03cd5b2不覆盖当前210及已合并的3.3.1/角色修正；本轮不改版本号。
- §2.2同时要求“无变化判拦截”和“无法判断仍request_sent”，又允许after非空当作验证，这会误判原有更早闹钟、相同闹钟与倒计时。实际采用上面的保守证据口径；不承诺nextAlarmClock能查询时钟数据库或确认倒计时。
- 删除专用Activity跟踪后，原启动action、全部extras和返回字段保留，并按应用Context要求增加NEW_TASK及verified；不把“原前台路径逐字不变”理解为保留已作废的Activity依赖。
- 仅悬浮窗授权无法证明厂商ROM允许后台弹出；有权限却没有证据时明确返回无法确认，不能由本轮离线测试宣称V2/V7通过。

## 验证

- Kotlin 编译及定向 JVM 检查通过，执行命令如下：

  ```bash
  ./gradlew :app:compileGithubDebugKotlin :app:testGithubDebugUnitTest \
    --tests 'me.rerere.rikkahub.data.ai.tools.local.AlarmToolTest' \
    --tests 'me.rerere.rikkahub.data.ai.tools.local.AlarmRegistrationTest' \
    --tests 'me.rerere.rikkahub.data.ai.tools.ToolRiskTiersTest' \
    --tests 'me.rerere.rikkahub.data.ai.tools.pulse.PulseToolPolicyTest' \
    --console=plain --no-parallel --max-workers=1
  ```

- 最终共 **4 套件 / 35 项全部通过**，0 失败、0 错误、0 跳过：AlarmToolTest 15 项、AlarmRegistrationTest 2 项、ToolRiskTiersTest 7 项、PulseToolPolicyTest 11 项。覆盖后台请求与拒绝、已有/相同闹钟和倒计时不误判、读取失败与取消传播，以及原有工具注册、审批级别和脉搏开关策略。
- 首次编译及检查耗时 2 分 37 秒；补充“悬浮窗已授权但系统仍抛出 SecurityException”的回归后，最终检查耗时 16 秒，241 个任务中 6 个执行、235 个为 UP-TO-DATE，复用配置缓存。最终日志：`artifacts/3.3.1-work.1/alarm-background-20261125/compile-tests-final.log`；四份 JUnit XML 快照保存在同目录的 `TEST-REPORTS/`。
- `git diff --check` 通过；三语各新增 3 个 key，旧 Activity 跟踪器及强制前台文案已清理。版本、数据库、图标和原有审批/脉搏运行逻辑未改。
- 未运行全量 JVM、Lint、Android instrumentation、模拟器、真机或真实闹钟/倒计时及响铃；未生成 APK / 源码 ZIP，未提交、推送或打标签。既有全仓 Lint 未通过结论不被本轮覆盖；编译与 JVM 结果不代表厂商后台权限及真实响铃已验收。

## 待维护者真机验收

1. 前台无悬浮窗权限仍可请求7:30闹钟/倒计时；原label、时分/秒数、跳过UI策略不变，返回request_sent和verified。
2. 脉搏后台分别关闭/开启悬浮窗权限，检查返回JSON及系统时钟实际列表，缺少后台权限时有background_blocked且不崩溃。
3. 已有更早闹钟、新设相同闹钟、倒计时不得误标verified=true；verified=false不等于已证明设置失败。
4. iQOO / OriginOS分别记录“仅开悬浮窗”和“再开后台弹出界面”的实际结果；当前未取得设备结论或截图，V7待验。
5. 从授权页返回或撤销权限，通用设置行状态及时刷新；四种模式的闹钟注册/审批和角色功能照常。

---

# 2026-11-24 F024：角色记忆转移与模式切换修正

工程版本保持 **3.3.1-work.1 / versionCode 210 / DB34**。本轮只改源码与文档；APK / 源码 ZIP 仍由维护者自行构建，本轮没有生成交付包。

## 修改内容

- 记忆转移页复制/移动选项的整行均可点击；新增“全选 / 反选”，同时作用于记忆文件和逐条记忆。全局或被其他助手/角色共享的记忆不可移动时，继续保护源数据并解释原因。
- 迁移专用文件写入与 profile 追加不再受常规 AI 记忆写入 48 KiB 预算限制。路径规范化、写锁、源版本检查、目标版本冲突检测及普通写入限制均保留。
- 对话附件迁移不设文件大小阈值；失败时不静默吞掉异常，转移页可看到文件名及可读原因。修正迁移附件数据库索引，使 relativePath 与 upload/<conversationId>/... 实际磁盘路径一致，同时保留 Upload 分类。
- ChatPage 模式同步 effect 移除 settings.uiMode 触发键，防止用户选角色后，导航中的旧工作会话抢先把设置写回工作模式。新会话加载及其类型变化仍会触发正确模式同步。

## 本轮验证

- ./gradlew :app:compileGithubDebugKotlin :app:testGithubDebugUnitTest --tests 'me.rerere.rikkahub.data.repository.MemoryRepositoryTest' --tests 'me.rerere.rikkahub.ui.pages.character.CharacterTransferSelectionTest' --tests 'me.rerere.rikkahub.data.model.CharacterScopeTest' --tests 'me.rerere.rikkahub.data.datastore.CharacterSettingsTest' --console=plain：BUILD SUCCESSFUL。
- 定向 JVM 共 4 套件 / 38 项：MemoryRepositoryTest 21 项、CharacterTransferSelectionTest 4 项、CharacterScopeTest 9 项、CharacterSettingsTest 4 项；0 失败、0 错误、0 跳过。覆盖迁移大文件写入/追加与版本冲突、全选/反选、共享范围移动保护和会话模式映射。
- Kotlin 主源码与测试源码编译通过；仅见既有 currentWindowDpSize 弃用警告。git diff --check 对本批文件通过。
- 未运行全量 JVM、Lint、Android instrumentation、模拟器或真机；附件权限/超大附件实际复制、选项触摸命中、全选反选交互和角色模式切换需维护者安装自构建包验证。
- 不生成 APK / ZIP，不改变版本/数据库，不提交、推送或打标签。工作区还包含原有上游合并及 F024 改动，均保留。

---

# 2026-11-24 F024：角色转移长按入口更正

工程版本保持 **3.3.1-work.1 / versionCode 210 / DB34**。本轮只修改源码及说明，APK / 源码 ZIP 由维护者自行构建；先前已生成的包不包含本次改动。

## 完成内容

- 在任何模式下进入「设置 → 实验性功能」，角色板块已启用时，长按「启用角色板块」整行即可打开现有助手转移页。标题、图标及右侧开关区域由整行统一处理长按；短按继续切换开关，长按不切换。
- 调试入口允许已完成、已拒绝迁移的用户重新进入，不重置 `characterMigrationState`、`characterMigrationPrompted` 或版本标记；进入页面本身不转移数据，仍需手动选择助手及转移选项。
- 已撤销角色模式胶囊上的长按回调及两处接线。应用启动仍不自动弹窗，首次手动启用角色时的老用户提示规则保持。
- 共享 `CardGroup` 只为传入长按回调的条目启用组合手势；其他开关继续原实现。没有新增资源、依赖、数据库或版本变更。
- `CHANGES.md` / `FEATURES.md` 已同步记录；本说明随维护者下次源码归档收录。

## 本轮验证

- `./gradlew :app:compileGithubDebugKotlin :app:testGithubDebugUnitTest --tests me.rerere.rikkahub.data.datastore.CharacterSettingsTest --console=plain`：**BUILD SUCCESSFUL**，耗时 2 分 2 秒；既有迁移规则测试 4 项通过，0 失败 / 错误 / 跳过。编译仍有既有 Material3 弃用等警告。
- `git diff --check`：通过；静态核对 `ModeSwitcher` 及其调用方已无 `onRoleLongClick`，设置项的长按仅判断角色实验开关，不判断当前模式。
- 真机触摸未验证，不将编译或 JVM 规则测试视为长按操作实测。
- 待维护者检查：聊天/工作、角色、群组、脉搏四种模式下均能通过该设置项长按打开转移页；右侧开关长按不误关闭，短按可正常关闭；关闭时不触发调试入口；完成或拒绝过迁移后可再次手动进入；模式胶囊只打开模式菜单。
- 本轮不生成 APK / ZIP，不提交、推送或打标签；未重跑全量 Lint，上一轮 Lint 未通过的状态不因此变更。

---

# 2026-11-23 F024：角色实验开关与迁移触发修正

当前工程版本为 **3.3.1-work.1 / versionCode 210**，数据库保持 **34**。
本轮只修正角色实验开关与迁移触发时机，不生成 APK 或源码 ZIP，不提交、推送或打标签。

## 行为修正

- `experimentalCharacterMode` 继续默认关闭；应用启动只按设置归一化隐藏的角色模式，不再自动检查或弹出角色迁移对话框。
- 迁移入口改为设置页中用户手动打开角色实验开关时触发。
- 仅老用户触发迁移：设置已加载、`launchCount >= 2`、`characterMigrationState == NOT_ASKED`、`characterMigrationPrompted == false` 且 `lastRunVersionCode == 0`。新安装用户手动打开也不会弹迁移框。
- 新安装首个启动在启动次数写入后记录当前 `versionCode`；升级用户继续保留零标记。这样新用户即使第二次或更晚才手动开启角色功能，也不会被误判为老用户。
- 群组模式世界书继续永久禁用：扩展页不显示、选择器不接收群组世界书更新回调、请求侧不注入；`Group.lorebookIds` 字段保留以兼容旧数据。

## 验证

- `git diff --check`：通过。
- `./gradlew :app:compileGithubDebugKotlin :app:testGithubDebugUnitTest --console=plain`：**BUILD SUCCESSFUL**。
- 追加验证：新安装标记与升级用户零标记单测通过；新用户延后手动开启不会触发迁移，老用户仍按启动次数条件触发。
- 完整 `testGithubDebugUnitTest`：通过；本轮未运行 ADB、模拟器或真机 UI 验收。
- 编译仍有既有 Navigation 3 opt-in unresolved 警告，无 Kotlin 编译错误。

---

# 2026-11-21 N27：三模式界面显隐

交付版本为 **3.3.0-work.3 / versionCode 209 / DB34**。N27 基于 **3.3.0-work.2 / 208**，按工作、群组和脉搏模式调整界面入口的显隐。

## 已完成

- `ChatDrawer.kt`：移除三种模式共有的图像生成菜单；群组模式隐藏历史记录/媒体创作/AI 翻译，并将底部入口改为“组员”和“群组设置”；脉搏模式隐藏收藏；工作模式保留助手入口。
- `SettingPage.kt`：按 `settings.uiMode` 控制助手、组员、群组、提供商、内置浏览器、语音服务、媒体和 Web 服务器的显隐。工作模式保留完整模型/服务入口；群组和脉搏按矩阵隐藏对应入口；权限警告区既有 `Screen.SettingProvider` 未改。
- `ExtensionsPage.kt`：快捷消息和提示词仅工作模式显示；工作区在脉搏模式隐藏；技能三种模式均保留。
- `ChatInput.kt`：快捷消息图标仅在工作模式且存在快捷消息时显示，群组/脉搏模式隐藏。
- 保持 `UiMode.CHAT`、`UiMode.GROUP`、`UiMode.PULSE` 原名，未修改路由、数据模型、数据库和业务服务。

## 源码归档

- 本批 ZIP 收录 N27、209 版号，以及 GitHub `master` 提交 `b86c6048` 的 `README.md`、`README_EN.md` 和 `DEVELOPMENT.md`。
- ZIP 根目录收录本说明，保留项目的 Gradle、CI 和原有 JNI 依赖。归档规则排除个人构建工具、APK、构建缓存和签名材料。

## 检查结果

- `git diff --check`：通过。
- `./gradlew :app:compileGithubDebugKotlin --no-daemon --no-parallel --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process --console=plain`：**BUILD SUCCESSFUL**，138 个 actionable tasks 中 2 个执行、136 个为 `UP-TO-DATE`。
- 编译仅有既有 Navigation 3 opt-in 标记 unresolved 警告；没有 Kotlin 编译错误。
- 静态检索确认 `ChatDrawer.kt` 已无 `showMenuPopup`、`HugeIcons.Sparkles`、`Screen.ImageGen` 残留。
- 维护者此前构建的 209 版 Release 中，JVM 单元测试 1817 项通过、2 项跳过。APK 签名、应用 ID、版本和 ABI 校验通过。Android Lint 未通过，诊断见对应构建的 `lint.log`。

## 待维护者验收

- N27 要求的工作/群组/脉搏三模式设置页和侧边栏共六张截图未执行；本机没有运行 ADB、模拟器或真机 UI 验收。
- 安装测试时核对三种模式切换、各入口显隐，以及群组“组员”和“群组设置”的跳转。

---

# 2026-11-21 本机构建菜单：显示上次产物目录

按维护者要求修改桌面 `/home/li/桌面/构建/RikkaHub Work/` 的个人构建入口。
应用版本保持 **3.3.0-work.2 / 208**、DB34；本轮只修改本机脚本、快捷方式、工具检查及说明，
不改应用功能，不执行真实 Android 构建，不生成新的交付 APK / 源码 ZIP，不 commit / push / tag。
下方11月20日源码ZIP仍为此前快照，不包含本次DELIVERY记录；个人构建工具仍不进入源码包。

## 完成内容

- 构建类型选择第一页分开显示 **Release（正式版）**、**Debug（调试版）**最近一次构建的完整产物文件夹路径；APK、源码、测试报告与日志沿用同一目录。
- 显示目录状态：构建完成（Lint未通过单独标注）、构建失败、尚未完成或已中断、目录已不存在；没有记录时明确显示。失败目录只表示日志和可能的中间产物，不标作完整交付。
- 新构建创建产物目录后原子写入 `.gradle/delivery-last-release-output.txt` 或 `.gradle/delivery-last-debug-output.txt`；两种类型分别保存，重开仍可查看，取消类型/版本选择不覆盖旧记录。状态目录沿用源码归档排除规则。
- 首次升级会按目录名中的构建时间扫描现有 `artifacts/<版本>/manual-release-*` / `manual-debug-*`，跨版本找到各自最近一次；不使用目录mtime，不把source-for-github归档当作APK构建。
- 首版加入 `--scrolltext` 后，whiptail 把初始焦点放在路径说明上，方向键和回车无法直接选择构建类型。去掉该参数后，首页打开即可用方向键选择并按 Enter 进入版本页。终端不足 80 列或 24 行时使用数字菜单，首页同样显示两类产物目录。
- 修复桌面 `.desktop` 中已失效的旧Exec路径，改为当前 `/home/li/桌面/构建/RikkaHub Work/构建 RikkaHub Work.sh`。原 `.sh` 的构建调用保持。

## 检查与文件范围

- `bash -n tools/build-delivery.sh`、`node --check tools/test-build-delivery.mjs` 通过。
- `node tools/test-build-delivery.mjs` **19 组离线流程通过**，覆盖构建矩阵、版本记忆、取消和失败、历史路径，以及焦点回归和小窗口数字菜单。Gradle 和 SDK 使用临时工程模拟。
- 用实际 whiptail 通过桌面 `.sh` 入口验证。在 **120×32** 和 **80×24** 的终端中直接按 Enter 可进入 Release 版本页，按下键再按 Enter 可进入 Debug 版本页。在 **55×15** 的终端中输入 `2` 可进入 Debug 版本页。各组都在版本页取消，核对了两类目录的显示及版本预填。
- 实际自动识别的Release为 `artifacts/3.3.0-work.2/manual-release-20261120-183629-WaJ05T/`，Debug为 `artifacts/3.3.0-work.1/manual-debug-20261120-163130-u78gmd/`；这是已有产物，不是本轮重新构建。
- 修改文件：本机 `tools/build-delivery.sh`、`tools/test-build-delivery.mjs`、`tools/BUILDING.md`、桌面目录内 `构建 RikkaHub Work.desktop` 及本DELIVERY。前三个继续被本机忽略并排除源码包；快捷方式在仓库外，DELIVERY随下次源码归档收录。
- 应用编译/单测、真机及真实API不因本机菜单变动重新执行；此前F023的1423通过/1缺语料跳过、完整Lint未通过和设备待验结论仍按原记录。

---

# 2026-11-20 源码交付：3.3.0-work.2 / 208

维护者本轮明确要求“打包源码，推送命令，我推送”。本次仅归档当前源码并提供手动命令，
不重新构建 APK，不执行 commit、merge、push、tag 或发布。版本 **3.3.0-work.2 / 208**，数据库仍为 **34**。
此节覆盖下方F023代码实施阶段的“未生成源码ZIP”状态；日常由维护者自行构建的长期方式保持。

## 本次归档

- 目录：`artifacts/3.3.0-work.2/source-for-github-20261120-dRMFeo/`；文件：`RikkaHub.Work-3.3.0-work.2-source.zip`。
- 收录当前工作区，含F023永久移除本地模型、旧Settings/真实DataStore兼容、失效模型引用清理，以及F021 HTML/SVG预览回退、F022 DeepSeek服务端工具/客户端元信息和既有上游3.3.0合并。
- 源码根含本DELIVERY，包含F023新增兼容器、测试、下线记录；保留Unix权限、软链接、UTF-8路径、Gradle wrapper/CI/项目工具和六个原有必要jniLibs。不恢复已删除的llama-cpp模块或.gitmodules。
- 排除个人build-delivery及配套检查/归档/报告脚本、BUILDING.md和两份历史个人工具，共八个路径；保留本机文件。APK、缓存、artifacts、凭据、签名私钥、GGUF权重和实际Git历史不入ZIP。
- 使用既有个人归档工具核对文件集合、逐文件SHA-256、权限/软链和CRC；最终大小、哈希与核验结果写包外 `SOURCE-MANIFEST.json`、`SOURCE-VERIFICATION.json` 和 `SHA256SUMS`，避免源码包自身哈希自引用。

## Git与验证状态

- 打包前只读fetch origin/master，确认本地HEAD和origin/master同为 `be5ed028`，没有未解决合并项。当前分支为 `merge/upstream-3.3.0`，origin使用SSH地址 `git@github.com:ziwa0213-crypto/rikkahub_work.git`。
- F023产品代码仍为未提交增量。本次不自动暂存或提交，保留既有index；之前移除gitlink的暂存项仍在。维护者应在 `/home/li/CDX/rikkahub` 执行提交与普通推送命令，无需解压ZIP来推送，不强推master。
- 只补充本节及AGENTS/FEATURES/DEVELOPMENT的归档状态；不再改产品代码、版本或个人脚本。既有Debug/Release Kotlin编译及205套件中1423项通过/1缺语料跳过的结果沿用，不重复执行未变产品测试。
- 完整Lint未通过、旧配置覆盖安装/云端各模式对话/预览及真实DeepSeek接口仍待验，详见下方F023记录。本轮源码归档校验不代替Android构建、真机或真实API验证。
- 后续维护者推送前仍先fetch并普通merge最新origin/master；若出现冲突，命令链会停止，需解决后再推送。归档是本次工作区快照，后续merge产生的新源码应以Git仓库为准。

---

# 2026-11-20 F023：永久移除本地模型

当前版本为 **3.3.0-work.2 / 208**，按维护者直接指定覆盖任务包中的 work.1 / 207；数据库保持 **34**。
依据 `RikkaHub Work-F023移除本地模型-20261120.zip` 的两份文档实施。维护者自行构建 APK / 源码 ZIP 的长期约定保持，
本批只完成代码、编译 / 单测检查和交付文档，没有 assemble、APK / ZIP 交付、commit、tag、push 或发布。
本节为当前状态，下方 F020、F021 及历史产物说明不代表本次仍保留本地模型或已经生成新 APK。

## 完成内容与范围

- 维护者决定：“安卓跑本地模型太慢，用户体验差；低参数量化模型效果也差。”F020 已永久移除，不再是隐藏入口或无限期停滞。
- 删除 llama-cpp 模块 **40 个普通文件及 1 个 gitlink**、app 本地专用主代码 **14 个文件**、专用测试 **4 个文件**及 `.gitmodules`，合计 **59 个普通文件 + 1 个 gitlink**。同步删除模块注册 / 依赖、供应商类型 / ID / 默认项、JNI / Vulkan、启动修复、DI、通知 / receiver、路由、预算提示和本地 UI 接线。混合测试保留非本地用例。
- 完整删除与修改清单、新增文件、任务包差异及恢复说明见 [本地模型下线记录](./docs/44-记录-本地模型下线（F023）.md)。任务包 app 专用列表实际有13项而不是12项；另删本地压缩专用 `CompressionBudget.kt`，主代码合计14项。
- 新增 `ProviderListCompatSerializer`，先过滤 `local_llamacpp` 再解码正常供应商。完整 Settings JSON 和 `SettingsFields.PROVIDERS` 独立 DataStore 字段共用该兼容器；不能只加字段注解。仅识别已移除类型，其他未知类型或损坏数据仍报错，不重置全部设置。
- Settings 只删除 `enableMemoryFusion` / `enableLocalGpu`；旧磁盘键保留但不识别。删除仅本地使用的 `Model.chatTemplate`，旧 JSON 字段仍可按原 unknown-key 策略忽略。VIDEO 能力枚举、历史视频消息和其他模型字段保留。
- 全局聊天模型失效时回落到现有第一个模型；没有模型则保留非空 UUID，由既有查找失败路径处理。助手、组员、脉搏的失效可空引用清除，快速 / 翻译 / OCR / 压缩 / 图像生成五个非空槽位不替换。任务包 B2“清空全局槽位”与 §4.3 矛盾，按 §4.3 及实际 UUID 类型处理。
- 普通 API 恢复统一完整 system / 记忆 / 工具，AgentLoop 保留所有注册工具；压缩使用既定完整输入、`maxTokens = null` 和全局阈值，不再按本地短窗口裁剪、估算或限制输出长度。取消及原始历史消息保留。
- F021 Markdown、已回退的 HTML / SVG、F022 ClientContext / DeepSeek 服务端工具、工作区上传、Google 视频及其他 API 协议保持。没有修改本批前 `docs/f021-f022-change-map.md` 的既有增量。
- 英文 / 简中 / 繁中各删除 **103** 个本地专用 key，实测各 **2719 → 2616**；XML 可解析、无重复、集合一致，没有增加其他语言。原 launcher / About 身份资源、DB schema、既有 libsimple / proot native 依赖保持。
- README 中英文、DEVELOPMENT、FEATURES、CHANGES、AGENTS 和本文件已同步当前版本与永久下线状态；旧 F020 文档只留作历史，上游合并不得重新套回其接线。

## 编译与测试证据

使用本机 JDK21，沿用工程 `org.gradle.jvmargs=-Xmx8g`，没有用小堆覆盖工程配置。最终检查命令：

```bash
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew \
  :app:compileGithubDebugKotlin :app:testGithubDebugUnitTest \
  :ai:testDebugUnitTest :ui:testDebugUnitTest :app:compileGithubReleaseKotlin \
  --no-daemon --no-parallel --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process --console=plain
```

| 阶段 | 结果与日志 |
| --- | --- |
| 删除类型前的兼容门禁 | 成功，1分1秒；当时的3项专项全部通过。`/tmp/rikkahub-f023-compat-first-20261120.log` |
| 删除类型后的编译定位 | 按计划暴露剩余旧类型引用并逐项修复；临时失败日志 `/tmp/rikkahub-f023-deletion-check-20261120.log` 不代表最终结果 |
| 首次综合检查 | Debug 主代码编译通过，新增测试的 PulseConfig 包名错误导致测试源码编译失败；已修复。日志 `/tmp/rikkahub-f023-check-20261120.log` |
| 修复后综合检查 | **成功，2分钟**，390个任务：23执行、367 up-to-date；Debug / Release Kotlin 及三个模块测试均通过。`/tmp/rikkahub-f023-check-final-20261120.log` |
| 最终源码检查 | ChatService 最后精简阈值方法签名后再确认，**成功，32秒**，386个任务：6执行、380 up-to-date。App Debug 编译 / 测试实际执行，AI / UI 测试与 Release 编译复用已成功报告。`/tmp/rikkahub-f023-verification-20261120.log` |

最终选定的测试报告共 **205 套件、1424 项：1423 通过、1 跳过、0 失败 / 错误**，不是所有模块的完整测试矩阵，也不能与历史统计相加：

| 模块 / 任务 | 套件 | 项数 | 通过 | 跳过 |
| --- | --- | --- | --- | --- |
| app / testGithubDebugUnitTest | 144 | 979 | 978 | 1 |
| ai / testDebugUnitTest | 58 | 439 | 439 | 0 |
| ui / testDebugUnitTest | 3 | 6 | 6 | 0 |

- `ProviderListCompatibilityTest` **5项**全部通过：完整备份 JSON 过滤和云端配置 / API Key 保留；真实 JVM Preferences DataStore 临时文件加载、归一化、保存后重开；旧磁盘开关键仍在且不影响加载；未知 / 损坏配置仍报错；有效引用不变及无模型时不伪造 UUID。没有读取真实用户配置。
- `ApiSystemPromptTest` **1项**通过：多种云端协议在小 contextLength 与大记忆下仍保留完整 system 字节及工具提示；工具提示只构造一次。AgentLoop 流式 / 非流式工具注入、视频路由及原供应商配置回归均通过。
- 唯一跳过为 `DeepSeekImporterTest` 的真实归档导入用例，因未提供所需真实语料；没有用新 Ignore 或删断言规避失败。仅本地专用测试按本次明确移除要求删除，API system 回归保留在新测试文件。
- JUnit XML：`app/build/test-results/testGithubDebugUnitTest/`、`ai/build/test-results/testDebugUnitTest/`、`ui/build/test-results/testDebugUnitTest/`。这些是本机检查证据，不进入源码包。
- XML / 三语集合、差异空白、受保护文件 blob、模块 / 类型残留核对通过。`git submodule status` 为空、仓库无 `llama-cpp/`。生产源码中只保留兼容器的旧类型常量，兼容测试中的旧字段字符串按验收需要保留；Ollama 搜索、品牌动画等无关逻辑保留。
- 本批未运行完整 Lint；此前完整 Lint 未通过的结论不变。既有 deprecated / opt-in 等编译警告未归零，不把 Kotlin 编译或 JVM 测试说成整版 / 真机验收通过。

## 用户数据与可恢复删除

本次没有操作用户 GGUF，也没有新增清理 UI。基线 LlamaCppInstall 的实际路径为
`File(context.filesDir, "models/llamacpp")`，即应用私有目录。Android 主用户常见路径示例（未连接设备确认安装位置）：

```text
/data/user/0/me.rerere.rikkahub.plus/files/models/llamacpp/
/data/user/0/me.rerere.rikkahub.debug/files/models/llamacpp/
```

任务书中以下外部目录为旧口径，不是当前基线代码的下载路径；同样未操作：

```text
Android/data/me.rerere.rikkahub.plus/files/local_models/
Android/data/me.rerere.rikkahub.debug/files/local_models/
```

删除前本地推理模块及其构建缓存已整体移动到仓库外保存：

```text
/home/li/CDX/rikkahub-f023-retired-20261120-XsGiQU/llama-cpp
```

Git 历史 `be5ed028` 亦可恢复全部删除前跟踪源码；原子模块 Git 历史保留。恢复研究应在独立分支逐块取回，
不整树 reset、不回退 DB34。已经归一化并保存的本地供应商 / 选择不会自动恢复，需要旧备份或重新配置。
本批唯一新增暂存操作为移除旧 gitlink；其余代码及文档仍为工作区增量，没有执行提交或覆盖维护者的既有更改。

## 个人构建与维护者待验

- 个人归档 / 构建工具已适配模块移除：取消对旧 .gitmodules / llama README / 指针文件的必需检查；继续保留根 DELIVERY、原有必要 jniLibs，排除个人构建工具、权重、凭据与缓存。桌面入口和 Release 三变体 / Debug 单 ARM64 规则保持。
- `tools/build-delivery.sh`、`tools/package-delivery-source.mjs`、`tools/test-build-delivery.mjs`、`tools/BUILDING.md` 仅留本机，不进源码 ZIP。**14组离线工具流程通过**；临时假工程内的模拟 APK / ZIP 不代表 Android 构建或本批交付包。
- 维护者构建时使用 **3.3.0-work.2 / 208**。根 DELIVERY 的源码归档必含校验仍在；本批未生成实际 APK 或源码 ZIP，旧 work.1 产物不含 F023，不能直接用于本批验收。
- 未运行 ADB / 模拟器 / 真机，也未使用真实 API Key。需装机验证：旧配置覆盖安装不崩、其他供应商与凭据保留、本地供应商与入口消失、失效引用正确回落；聊天 / 群组 / 脉搏云端各跑一轮；Markdown、HTML / SVG、工作区上传、Google 视频与 DeepSeek 搜索 / 读链接 / 元信息正常；旧 GGUF 文件仍在。

---

# 2026-11-20 后修正：回退 HTML 预览

版本保持 **3.3.0-work.1 / 207**，DB34；依据 `RikkaHub Work-F021回退HTML预览-20261120.zip` 的两份文档实施。沿用维护者自行构建约定，本轮不生成 APK / 源码 ZIP，不推送、打标签或发布。

## 原因与处理

- 维护者实测 HTML 打开失败并显示 `ERR_HTTP_RESPONSE_CODE_FAILURE`。任务包将原因归于新增网络加载限制阻止 data 主文档，以及拦截器对未识别的主文档请求返回 404。本轮撤销这两条路径，不宣称已在本机重现该 Android WebView 故障。
- 删除 `WorkspacePreviewInterceptor.kt`、`WorkspacePreviewResources.kt`、`WorkspacePreviewResourcesTest.kt` 三个文件；Git 基线 `532dee9e` 保留原内容，可按需恢复。
- `WorkspaceFileEditorPage.kt` 的 HTML / SVG 恢复原 `loadDataWithBaseURL` + 固定虚拟源，不传资源拦截器，不加网络加载限制；移除脚本开关，脚本行为沿用旧 WebView。预览函数逐字比对与改动前 `655f8517` 一致。接受同目录 CSS / 图片等本地资源不再映射的回退，不承诺离线隔离或脚本默认关闭。
- 加载、源码/预览切换、保存时仅对 Markdown 计算 1 MiB；原大小常量移到编辑页，值不变。Markdown 渲染器、图片目录上下文、聊天共用的绝对路径 / http(s) 图片行为及三个保留文案未改。FILES 可保存、LINUX 只读及原 512 KiB 读取保护保持。
- 保留公共 MIME 工具与测试、WebView 可选 `requestInterceptor`、`remember(state)` 与更新 client；这些文件逐字未改，可选接口仅有内部转发，当前无外部调用方。F022、版本、DB34、原应用身份图标和个人构建工具不改。
- 三语各只删除脚本开关一条。XML 实测各 **2720→2719**，集合一致、无重复。任务书写“各2717”是计数误差；不额外删除两个无关 key 凑数。

## 本批检查

命令（省略本机工具链路径）：

```bash
./gradlew :app:compileGithubReleaseKotlin \
  :app:testGithubDebugUnitTest --tests me.rerere.rikkahub.ui.components.richtext.WorkspaceImagePathTest \
  :ui:testDebugUnitTest --tests me.rerere.ui.webview.MimeTypesTest \
  -Prikkahub.abi=arm64-v8a --no-daemon --no-parallel --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process --console=plain
```

- **成功，32 秒**；409 个任务，31 执行、378 up-to-date。Release Kotlin 编译与 App Debug 测试源码编译通过；图片路径 **2 项**、MIME **1 项**全部本次执行通过，无失败/错误/跳过。没有运行 assemble、R8、签名或 APK 打包，不把编译成功说成新 APK 交付。
- 日志：`/tmp/rikkahub-f021-html-rollback-20261120-E6m6yW.log`。JUnit XML 位于 `app/build/test-results/testGithubDebugUnitTest/TEST-me.rerere.rikkahub.ui.components.richtext.WorkspaceImagePathTest.xml` 和 `ui/build/test-results/testDebugUnitTest/TEST-me.rerere.ui.webview.MimeTypesTest.xml`；均为本机检查证据，不进入源码包。
- 差异检查确认 Markdown 核心文件、共用图片处理、ui WebView / MIME、AI / F022、仓库读写与版本配置相对基线无修改；源码扫描旧预览类、网络加载限制与已删脚本 key 均为零引用。XML 解析、三语集合 / 唯一性和 `git diff --check` 通过。
- 本批未重跑全量 JVM / Lint；此前完整 Lint 未通过的结论不变。编译有既有 ExperimentalNavigation3Api opt-in 警告，不影响本次成功结果。

## 维护者待验与源码状态

- 重新构建安装后检查 `.html` / `.svg` 实际显示、HTML 顶栏无脚本按钮、大 HTML 不触发 Markdown 大小提示；Markdown 渲染、相对图片、3 MiB Markdown 只源码和聊天工作区图片保持。未运行 ADB、模拟器或真机，这些视觉与行为不标通过。
- 旧的三个 Release APK 和源码 ZIP 都生成于本次回退前，不包含本修正。后续由维护者桌面构建流程生成新 APK / ZIP，源码包继续包含本 DELIVERY 并排除个人构建脚本；本轮没有修改归档工具。
- 本轮未执行 git commit。实施检查期间检测到本地 HEAD 已新增 `be5ed028`，包含回退代码及部分台账；保留该提交，后续 README / DEVELOPMENT / 本文件与映射修正为工作区增量。没有撤销外部操作或擅自重写提交。

---

# 前次源码合并 — 远端文档更新（2026-11-20）

- 维护者手动合并 origin/master 时 README.md 发生冲突；本次以本地 6f0d5e2a 与远端 8a5eca08 为父提交完成文档合并，保留远端中英文 README 的四板块结构、角色规划及 DEVELOPMENT 开发说明。
- 按当前源码同步 3.3.0-work.1 / 207、DB34、F021 / F022 已完成、本地 AI 模型无限期停滞与入口隐藏；更正旧群组自动压缩、视频输入及 20 分钟生成截止等过时描述。
- 本轮只改 README.md、README_EN.md、DEVELOPMENT.md 与本文件，不改产品源码或私人工具，不重新构建、生成 ZIP、推送、打标签或发布。检查限于冲突标记、差异空白、暂存范围与合并父提交；既有测试和设备 / 真实接口待验结论不变。
- 下方前次源码包 source-for-github-20261120-c2N82u 是本次远端文档合并前快照；不宣称其已包含本次文档更改。维护者推送以合并后的 Git HEAD 为准。

---

# 前次源码交付 — 排除个人构建工具（2026-11-20）

- 维护者本次要求“打包源码，我推送”，并明确个人构建脚本不进入源码包。版本保持 **3.3.0-work.1 / 207**，DB34；本轮仅生成源码 ZIP，不重新构建 APK、不提交、不打标签、不推送。日常自行构建的长期约定不变。
- 交付目录：`artifacts/3.3.0-work.1/source-for-github-20261120-c2N82u/`；文件名：`RikkaHub.Work-3.3.0-work.1-source.zip`。根含本文件，集合、逐文件 SHA-256、权限、软链、UTF-8 路径及 CRC 核验结果见包外 SOURCE-MANIFEST.json / SOURCE-VERIFICATION.json / SHA256SUMS，避免源码包自身哈希自引用。
- 本机归档工具固定排除以下八个路径：`tools/build-delivery.sh`、`tools/collect-delivery-apks.mjs`、`tools/package-delivery-source.mjs`、`tools/summarize-delivery-tests.mjs`、`tools/test-build-delivery.mjs`、`tools/BUILDING.md`、`tools/package-local-release.mjs`、`tools/summarize-jvm-tests.mjs`。文件仍留在本机；桌面入口可继续使用。历史章节提及这些私人工具，不代表本源码包附带它们。
- 六个新增个人工具文件已加入本机 `.git/info/exclude`，避免普通 git add 误收；两份已跟踪历史工具仅从 ZIP 排除，不改 index。Gradle wrapper、工程构建配置、CI、翻译和其他验证工具照常保留，没有整目录排除 tools。
- 源码包含上游 3.3.0 合并及后续 F020 / F021 / F022 增量。本地 AI 模型按维护者决定无限期停滞，入口已隐藏；相关源码、配置兼容和 native 模块保留，不删除已有权重。ZIP 排除 GGUF、APK、缓存、凭据、实际 llama.cpp 子模块内容；保留固定指针、许可、恢复说明和原有必要 native 依赖。
- 本次只修改个人归档规则、对应离线检查及交付台账，产品源码和版本未变。14 组离线流程通过，覆盖已跟踪与未跟踪个人脚本均不进包，以及项目 wrapper / CI / 验证工具保留；Bash / Node 语法与 git diff --check 通过。这些检查不是 Android 编译或真机验收。
- 维护者上次自行构建目录为 `artifacts/3.3.0-work.1/manual-release-20261120-171724-35TsOr/`：三个 Release APK 及原源码 ZIP 已生成，日志总耗时37秒（含缓存复用）；APK签名/版本/ABI/CRC通过，JVM报告1906通过、2缺语料跳过。完整Lint未通过，真实接口与设备交互仍待验。旧源码 ZIP 包含个人构建工具，本次发布源码应选用本节的新包；旧产物保留不覆盖。

---

# 前次构建修复 — Release 打包堆内存（2026-11-20）

- 依据维护者本次实际失败日志 `artifacts/3.3.0-work.1/manual-release-20261120-170643-DoC4Ud/build.log`，失败任务为 `:app:packageGithubRelease`：`ApkFlinger` 压缩时经 `Compressor.deflate` 抛 `OutOfMemoryError: Java heap space`，不是单元测试断言失败。该次 4分47秒失败，无完整交付；原失败目录保留。
- 根因：`tools/build-delivery.sh` 用 `-Dorg.gradle.jvmargs=-Xmx2560m ...` 覆盖了工程 `gradle.properties` 已有的 `-Xmx8g`。本次移除这个脚本覆盖，构建和后续 Lint 均沿用工程/用户 Gradle 配置；单 worker、串行、三 Release / 单 ARM64 Debug、完整版本输入与记忆、源码含 DELIVERY 保持。
- 验证：Bash / Node 语法及 `git diff --check` 通过；14 组离线脚本回归通过，另增加实际 Gradle 参数不得覆盖 `org.gradle.jvmargs` 的断言。使用临时 init 脚本运行 `gradlew help` 实测 `Runtime.maxMemory() = 8589934592`，即 **8 GiB**，10秒成功；配置检查日志在 `/tmp/rikkahub-heap-check-Xidlij/gradle-help.log`。该检查没有运行 APK 打包，不等于三包已构建成功。
- 本批修改 `tools/build-delivery.sh`、`tools/test-build-delivery.mjs` 及 AGENTS / CHANGES / FEATURES / tools/BUILDING / 本文件。实际工程仍为 `3.3.0-work.1 / 207`，没有生成新交付 APK / ZIP，没有提交或推送。由维护者重新启动原桌面入口重试 Release；无需因本次失败删除源码或历史交付。

---

# 前次工具更新 — 构建版本名/内部版本号输入与记忆（2026-11-20）

- 桌面入口保持 `/home/li/桌面/构建 RikkaHub Work.desktop`，仍调用仓库 `tools/build-delivery.sh`。选择 Release / Debug 后弹出版本输入框，填写 `x.x.x-work.x/xxx`，例如 `3.3.0-work.1/207`，斜杠前后分别是版本名与内部版本号；确认后才构建。没有 whiptail 时使用终端输入；格式或内部版本号范围（1~2100000000）不正确要求重填，Esc / 取消 / 输入结束则退出构建。
- 首次预填工程现有两项版本；后续预填上次确认的整对值，Release / Debug 共用 `.gradle/delivery-last-version.txt`，状态不进入源码 ZIP。旧记录只含版本名时自动补工程当前内部版本号。确认后即保存，即使后续构建失败也可在下次沿用；取消不覆盖原状态。
- 确认后在构建锁内检查 `versionName` / `versionCode` 声明各唯一，一次原子更新两个字段，保留其他内容与权限；不额外自动递增。APK 内两项版本及 ZIP 内工程版本一致，产物路径使用所选版本名。末尾摘要同时列出实际版本与记忆文件路径。
- Release 固定三个 GitHub APK，Debug 仅 ARM64；两种都生成根含本文件的源码 ZIP。原测试/检查、日志、计时及归档校验保留。详细操作见 `tools/BUILDING.md`。
- 修改文件：`tools/build-delivery.sh`、`tools/test-build-delivery.mjs`、`tools/BUILDING.md`、`AGENTS.md`、`CHANGES.md`、`FEATURES.md`、本文件。没有修改实际工程当前版本，仍为 `3.3.0-work.1 / 207`；没有自动构建应用 APK 或交付源码 ZIP，没有 commit / push / 发布。
- 验证：Bash / Node 语法、`git diff --check` 通过；`node tools/test-build-delivery.mjs` 的 **14 组离线流程**通过，覆盖原构建矩阵、Lint/构建失败、完整版本输入/整数范围、整对预填与跨类型记忆、旧记录兼容、取消、坏状态回落、重复声明不部分更新、APK/源码版本一致性及 TTY 输入框分支。模拟包只在临时测试工程中产生，不代表 Android 编译或可安装 APK；TTY 分支采用系统伪终端及模拟 whiptail 验证调用/预填/取消，不冒充用户屏幕实测。
- 回滚仅逐块撤销本次输入、记忆和版本更新及对应测试/文档；保留原打包流程与此前 F020/F021/F022 工作区内容。无独立提交可 revert，不整文件恢复共享脚本或 app/build.gradle.kts。

---

# 前一批代码交付 — F022 DeepSeek 对话工具、客户端元信息与 F021 工作区预览

日期：2026-11-20，Asia/Shanghai。版本保持 **3.3.0-work.1 / 207**，数据库保持 **34**。依据本批任务包先完成 F022 并单独编译，再完成 F021 并单独编译，最后执行全模块 JVM 测试和 Release Kotlin 编译。代码仍为未提交的工作区增量；没有本批 commit、tag、推送或发布。

**长期交付方式已改为维护者自行构建 APK 与源码 ZIP。** 代理完成代码、检查和根 `DELIVERY.md`；本轮未执行 APK 打包、未生成源码 ZIP。下方历史章节的 APK/ZIP 交付描述仅代表各历史批次，不代表本轮已出包。

## 本批完成

### F022：DeepSeek 对话工具与客户端元信息

- 新增独立的内置“读取网页”开关。DeepSeek 方言的 Chat Completions 请求支持服务端 `web_search` / `read_url` 声明、流式状态与结束结果展示；来源和网页地址可展开查看，失败状态显示错误。工具声明与本地 function 工具合并，不在本机伪执行服务端工具。
- 完整保存服务端工具调用及签名，后续轮次逐步回传；仅含服务端工具的 assistant 消息也保留。缺少开始事件时补齐卡片标题所需的开始状态；最终快照补齐签名，重复结束事件不重复创建卡片。跨到非 DeepSeek 协议时不回传该专用数据。
- Responses / Claude 的工具声明按各自协议处理；其他供应商保留原内置搜索行为，不被增加 DeepSeek 的 `read_url` 声明。F007 原有 `hasTools` 与 `reasoning_content` 回传守卫保持。
- 新增“客户端元信息”本地选项，但不注册成可执行 Tool。DeepSeek Chat Completions 为各 user 消息按自身时间生成 `latest_reminder`；其他协议通过 system 注入语言、日期等元信息。助手、群组的既有本地工具配置入口和脉搏独立开关接入共用请求构造。
- 新建助手、脉搏配置默认开启元信息；旧配置缺少新增字段或原 `localTools` 字段时按旧行为读取，不被静默开启。已有工具列表原样保留；保存时显式编码相关字段，保证再次读取不改变选择。新群组的原空工具列表保持。

### F021：工作区文件预览

- `.md` / `.markdown` 默认使用现有 Markdown 组件渲染，并可切回源码。HTML、SVG 保留预览与源码切换；HTML 新增默认关闭的“启用脚本”开关。
- Markdown 相对图片按当前文档目录及实际 FILES / LINUX 区域解析；聊天中的绝对工作区图片、HTTP(S)、data 和协议相对外链图片保留原行为。
- HTML / SVG 使用固定虚拟 HTTPS 源；图片、CSS、JS 只映射到当前文档目录以内的本地文件。校验方法、scheme、host、port、解码后的路径及 canonical 边界，拒绝目录遍历和符号链接越界；无法识别的资源返回真实 404，不转交默认网络加载。
- WebView 禁止 file/content 访问与外网加载，默认禁脚本；切换脚本选项时重建预览实例。WebView 资源拦截接口为可选参数，默认不影响其他使用方；状态变化时更新对应 client，避免沿用旧工作区拦截器。
- 超过 1 MiB 的文件禁用渲染，提示查看源码；编辑后切换预览及保存时重新检查 UTF-8 大小。FILES 仍可编辑保存，LINUX 仍只读。

## 自动验证与证据

| 阶段 | 命令（省略共用环境及 Gradle 参数） | 结果 |
| --- | --- | --- |
| F022 独立检查 | `:app:compileGithubDebugKotlin :ai:testDebugUnitTest :app:testGithubDebugUnitTest` | 成功，约 18 秒；203 套件 / 1,443 项，1,442 通过、1 跳过。AI 测试在本批此前调用真实执行通过，最终调用复用报告。 |
| F021 独立检查 | `:app:compileGithubDebugKotlin :app:testGithubDebugUnitTest :ui:testDebugUnitTest` | 成功，约 41 秒；150 套件 / 1,013 项，1,012 通过、1 跳过。App / UI 测试本次执行。 |
| 最终整体验证 | `test :app:compileGithubReleaseKotlin` | 成功，2 分 25 秒；284 个去重套件 / 1,908 项，**1,906 通过、2 跳过、0 失败/错误**。503 个任务：42 执行、461 up-to-date。 |

总数按 GitHub App 和其他模块报告汇总，排除重复 Play App 报告；不将三批有重叠的测试数字相加。最终 App GitHub / Play、llama-cpp、search 测试执行，其余包含 Gradle 缓存复用，不声称 1,908 项全部重新执行。

- 本批新增 24 项：`DeepSeekConversationToolsTest` 12 项、`ClientContextTest` 4 项、`WorkspacePreviewResourcesTest` 5 项、`WorkspaceImagePathTest` 2 项、`MimeTypesTest` 1 项。既有 F007 `ChatCompletionsDeepSeekReasoningTest` 8 项通过。
- 两项跳过分别为 `DeepSeekImporterTest` 的真实归档用例、`PdfCorpusTest` 的本地语料用例，均因既有测试语料缺失；不算通过。
- 三批原始日志：`/tmp/rikkahub-f022-check-20261120.log`、`/tmp/rikkahub-f021-check-20261120.log`、`/tmp/rikkahub-f021-f022-final-check-20261120.log`。JUnit XML 快照和汇总在仓库 `.gradle/f021-f022-checks-20261120/{f022,f021,final}/`，最终汇总为 `final/TEST-RESULTS.json`。这些本机检查证据不进入源码包；`/tmp` 与 `.gradle` 可能被后续清理。
- 仅 English / 简中 / 繁中三语；本批新增 12 个 key × 3，三份现有 key 集合均为 2,720 条，无重复。原 21 个应用身份图标与固定基准 `643191229dcbeb4972e3772986818605633ace92` 一致；版本、DB34、依赖和 llama.cpp pin 未改。
- `git diff --check`、桌面入口 Bash 语法、构建工具 Node 语法检查通过。本轮未运行全量 Lint，不宣称 Lint 通过；Release Kotlin 编译成功不等于本轮已完成 R8、签名或 APK 校验。

## 与任务示例的差异及现有边界

- `WorkspaceRepository.resolveFile()` 实际仅返回文件，不能照抄任务示例把目录传进去。因此在 IO 协程中解析当前文件并取父目录，虚拟源固定为 `https://workspace-preview.invalid/preview/`，资源路径只拼接一次；没有在主线程使用 `runBlocking`。
- 保留 LINUX 区原有 512 KiB 文本读取保护。因此大于该限制的 LINUX 文件仍可能显示原读取错误；FILES 文件超过 1 MiB 时才适用“可看源码、禁渲染”。本批不扩大 rootfs 读取权限。
- SSH 工作区没有本地 `resolveFile`，HTML 本地关联资源暂不预览；正文仍可按既有入口读取，不把远程路径当本地文件。
- HTML / SVG 预览完全离线；Markdown 外链图片按任务要求保持原 Coil 行为，可能联网，两者不可混为同一隔离范围。
- 服务端工具调用按协议保存签名，但不存储 step 级思考文本；相关回传 `reasoning_content` 采用空字符串，不伪造未提供的思考内容。真实 DeepSeek 多步搜索/抓取及签名验收仍需账号验证。
- 为同时满足“新配置默认开、旧配置保持原选择”，增加配置 JSON 兼容转换器；没有改变旧工具序列化名、数据库或工具权限。客户端元信息仅在请求构造期注入，不增加审批对象或更改心跳调度/超时。

## 维护者构建与待验

桌面入口：`/home/li/桌面/构建 RikkaHub Work.desktop`；也可运行：

```bash
bash '/home/li/桌面/构建 RikkaHub Work.sh'
```

菜单 Release 固定生成 ARM64 / x86_64 / Universal 三个 GitHub APK；Debug 仅 ARM64，两者均生成源码 ZIP。两条路径共用 `tools/package-delivery-source.mjs`：**根 `DELIVERY.md` 为必需文件，归档后逐文件校验内容、权限、软链与 CRC**。缺少该文件时停止，不会交付一个不含说明的源码包。版本由工程读取；完整用法见 `tools/BUILDING.md`。

桌面包装器在调用者未显式设置时补齐本机 JDK21、实际 NDK27 和 Vulkan 工具路径。直接调用仓库脚本或重跑上述检查时，本机对应环境为：

```bash
export LLAMA_NDK_PATH=/opt/android-sdk/ndk/27.2.12479018
export LLAMA_GLSLC=/home/li/.local/share/rikkahub-toolchain/vulkan-20261118/glslc
export LLAMA_VULKAN_INCLUDE_DIR=/home/li/.local/share/rikkahub-toolchain/vulkan-20261118/root/usr/include
export LLAMA_SPIRV_HEADERS_DIR=/home/li/.local/share/rikkahub-toolchain/vulkan-20261118/root/usr/share/cmake/SPIRV-Headers
```

本轮检查另使用 `-Prikkahub.abi=arm64-v8a --no-daemon --no-parallel --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx2560m -Dfile.encoding=UTF-8' --console=plain`。**Release 菜单构建三包时不要附加单 ABI 限制**；该菜单沿用工程双架构及 Universal 设置。

| 待真机/真实服务项目 | 验证重点 |
| --- | --- |
| DeepSeek 服务端工具 | 单搜索、单抓取、组合多步、普通 function 混用、连续追问签名回传、失败及来源卡片、切换非 DeepSeek 模型。离线夹具不证明真实接口可用。 |
| 元信息与旧数据覆盖安装 | 新助手/脉搏默认开启；旧配置不自动开启；开关保存重启；普通、群组、手动/自动脉搏请求中的语言与逐消息日期。 |
| Markdown / HTML / SVG | 实际屏幕渲染、源码往返、FILES 保存、LINUX 只读及大小边界；同目录相对图片/CSS/JS、路径越界/符号链接拒绝。 |
| WebView 隔离与开关 | 真机观察外网/file/content 请求被阻止；脚本默认关及开关重建；切工作区/文件后不读取旧目录。Markdown 外链图片仍可能联网。 |
| 自行构建后的安装包 | R8、正式签名、三 Release ABI / 单 ARM64 Debug、安装/升级、图标与 UI 交互由实际构建和维护者设备验收；本轮没有 APK 可供核验。 |

## 文件映射、上游重套与回滚

本批全部新增/修改文件按职责列在 `docs/f021-f022-change-map.md`。与已有 F020 修改重叠的 `ChatRequestFactory`、`Assistant`、`PulseConfig`、Markdown、供应商协议与三语文件只增加本批内容。**不得按当前 HEAD 整文件回退**，HEAD 尚未包含既有 F020 工作区增量。

重套上游时需一起保留：服务端工具协议枚举/签名存储/请求回放/流式终局、请求期元信息及旧 JSON 兼容器、工作区 Markdown 目录上下文、HTML 虚拟源拦截与 WebView client 更新。回滚仅逐块反向撤销这些本批改动，保留已有 F004/F007/F009、F020、原图标及其他工作区能力；具体边界见映射文档。Kai / PalmClaw 参考仓库仍在产品仓库外，本批仅参考日期/请求上下文组织，不移植其调度或超时。

---

# 上次工具交付 — 桌面构建 TUI（2026-11-20）

- 桌面入口：`/home/li/桌面/构建 RikkaHub Work.desktop`，调用同目录 `.sh`，再调用仓库 `tools/build-delivery.sh`。启动器格式、可执行权限及桌面信任属性已核对；终端中可看到 Release / Debug 菜单，Esc 能正常退出。
- Release 生成 ARM64 / x86_64 / Universal 三个正式签名 GitHub APK；Debug 只生成 ARM64 GitHub APK；两者均生成根含本文件的精简源码 ZIP。版本自动读取工程，仍为 `3.3.0-work.1 / 207`。
- 实时输出日志；末尾列出 APK/源码完整路径、测试统计/跳过说明、JUnit XML 快照、核验报告、Lint 结果和各阶段耗时，另存 `BUILD-SUMMARY.txt`。每次用独立 artifacts 目录，不覆盖旧交付。编译/测试/归档失败停止；完整 Lint 未通过时明确标注并保留已生成 APK，不宣称所有检查通过。
- 工具检查：Bash/Node 语法、桌面启动器格式通过；`node tools/test-build-delivery.mjs` 的 Release 三包、Debug 单包、Lint 失败、构建失败四种隔离模拟流程全部通过。已用现有 ARM64 Debug 包验证包名/版本/ABI/签名校验器，并用临时源码归档验证 CRC、逐文件内容、权限与软链。
- 测试汇总演练读取上一批真实 Gradle 日志和既有报告，正确统计 279 套件 / 1884 项（1882 通过、2 跳过）；这是报告工具验证，不是本轮重新执行这些用例。本轮未重新编译 Android 应用、未生成用于安装的新 APK，未操作设备或推送源码。完整用法见 `tools/BUILDING.md`。

---

# 上次应用交付 — F020补充八：隐藏本地模型入口

日期：2026-11-20，Asia/Shanghai。版本保持 **3.3.0-work.1 / 207**，DB34。本批依据《补充八·隐藏本地模型入口》。按维护者后续要求，交付 **ARM64 正式签名 Release APK + 精简源码 ZIP（根含本文件）**；不生成 Debug，不提交、不打 tag、不推送或发布。交付目录：`artifacts/3.3.0-work.1/f020-supplement8-release-20261120/`。历史交付均保留。

## 本批完成

- 新增 `Settings.uiProviders` 作为 UI 唯一可见供应商列表，过滤 `ProviderSetting.LlamaCppLocal`。设置页供应商列表、拖拽排序、聊天/助手/翻译/设置模型/群组/脉搏/图像生成模型选择器均改用该视图；隐藏供应商后的拖拽按 provider id 映射回完整列表，避免索引错位。
- Web UI 的 SSE 设置快照同样过滤本地模型，避免网页端继续展示或选择 `LlamaCppLocal`。
- 复核设置页不存在“实验性功能”组或本地模型入口；聊天页不再引用本地模型加载胶囊、提示预算条、内存确认弹框或发送拦截。相关本地模型实现文件仍保留，未删除运行时、Koin 注册、数据字段或 native 模块。
- 启动时等待真实设置加载完成，调用 `migrateAwayFromLocalModels()`：七个全局模型槽位指向第一个可用云端模型；助手、群组成员和脉搏中的本地模型引用清空；没有可回退云端模型时保持原设置；迁移不修改 provider、模型列表或已下载 GGUF 文件，并且幂等。
- 保留 `ProviderSetting.LlamaCppLocal`、`llama-cpp`、`LlamaCppInstall.repair()`、本地模型索引与已有本地模型 UI 文件，便于后续恢复；不清理 `.gguf` 文件，不新增恢复开关，不新增语言资源。原应用图标和版本号保持不变。

## 验证

- 补充八定向命令成功：
  `:llama-cpp:testDebugUnitTest :app:testGithubDebugUnitTest`，266 个任务成功，耗时约 2 分 23 秒；覆盖补充八新增的引用迁移测试以及已有 F020/F009 回归测试。
- 迁移测试覆盖：七个全局槽位、助手/群组成员/脉搏引用清空、provider 与模型保留、连续执行幂等、无本地模型返回同一对象、无云端回退模型保持同一对象。
- 最终轻量编译成功：`:app:compileGithubDebugKotlin :app:compileGithubDebugUnitTestKotlin`，230 个任务成功，耗时约 2 分 6 秒。构建过程中 Web UI 资源重新生成成功；仅有既有 deprecated/opt-in 警告，无编译错误。
- 本次完整 `test :app:assembleGithubRelease -Prikkahub.abi=arm64-v8a` 成功，耗时 5 分 40 秒；Gradle 报告共 279 个去重测试套件、1,884 项，1,882 通过、2 项因缺本地语料跳过、0 失败。GitHub/Play 重复 App 测试按套件去重；部分未变模块使用 Gradle `UP-TO-DATE` 报告。
- Release APK 为 **31,911,382 bytes**；包名 `me.rerere.rikkahub.plus`、版本 `3.3.0-work.1 / 207`、原正式证书 SHA-256 `671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b`、非 debuggable、仅 `arm64-v8a`；APK ZIP CRC 通过、未包含 GGUF 权重。应用图标核验沿用原始 20 个 PNG 像素及自适应图层对照。
- 源码 ZIP 排除 APK、构建缓存、本机配置与签名凭据；保留 `.gitmodules` 和固定 llama.cpp 子模块恢复说明/commit 文件、必要预编译依赖、权限与软链，归档根含 `DELIVERY.md`。归档清单、CRC、源码内容 SHA-256 与 APK 校验值记录在交付目录的审计 JSON / `SHA256SUMS`。

### 补充八静态验收实际数字

| 项目 | 实际结果 |
| --- | --- |
| H1 | UI 中有 **19 处** `settings.providers` 命中（跨 14 个文件）；**0 处**作为模型选择器参数。剩余用于按 ID 查找/解析或展示模型与供应商信息，以及供应商排序/推荐 ID 的完整数据写回。 |
| H2 | `SettingsExt.kt` 中 `uiProviders` 定义计数为 **1**。 |
| H3 | `SettingPage.kt` 中 `local_llm` 命中 **0**。 |
| H4 | `SettingPage.kt` 中 `experimentalSettings` 命中 **0**。 |
| H5 | 聊天页目录中 `LocalModelCapsule` / `LocalMemoryConfirmation` / `LocalPromptNoticeBar` 命中 **0**。 |
| H6 | `EventsRoutes.kt` 中 `uiProviders` 命中 **2 行**：1 行 import、1 行快照过滤使用；实际过滤点为 1 处。 |
| H7 | `values`、`values-zh`、`values-zh-rTW` 各 **2708** 个 `<string>`；补充八未新增文案；没有 `values-ar`。 |
| H8 | Kotlin 编译与定向 JVM 测试通过；本批不生成 APK，因此不宣称 H9-H16 的真机验收。 |

## 尚待维护者实测

H9-H16（设置页实际隐藏、供应商拖拽、各模型选择器、覆盖安装迁移、网页端列表、GGUF 文件保留和前后台稳定性）仍需维护者安装本次 APK 后在设备上验证。构建与 JVM 测试不能替代真机 UI、网页端和文件系统验收。

## 本批边界

- 不改版本：仍为 `3.3.0-work.1 / 207`。
- 不删除本地模型代码、Koin 注册、provider 配置、模型文件或 `.gguf`；不改 `llama-cpp/build.gradle.kts`、native CMake 和三语言资源集合。
- 本批只生成一个 ARM64 Release APK 和一个源码 ZIP；不生成 Debug，不提交、打 tag、推送或发布。当前源码工作树保留此前 F020 与上游合并改动。

# 历史交付 — F020补充五/六：提示进UI与Vulkan GPU

日期：2026-11-18，Asia/Shanghai。保持 **3.3.0-work.1 / 207**，DB34。依据补充五与补充六；补充六独立覆盖补充四中“本轮不做GPU”的范围。
交付仍按维护者最新要求：**ARM64正式签名GitHub Release APK + 精简源码ZIP（根含本文件）**，不生成Debug。独立目录 `artifacts/3.3.0-work.1/f020-supplement5-6-release-20261118/`；旧批次保留，不commit/tag/push/发布，不操作手机、真实账号或下载权重。

## 本批完成

- 本地提示预算省略工具/记忆时，ChatService按会话提供进程内状态，ChatPage在输入区上方显示同一条可关闭提示；双提示用“·”连接。关闭后本进程同会话不再显示，切换会话仍保留，重启自然清除。提示不进入StreamChunk、消息、数据库或后续请求。媒体降级正文行为不变。
- llama.cpp模板未切出思考标记时，Kotlin对累积解析结果补识别`<think>`/`<thinking>`；保留其余JSON字段，避开原生reasoning和Markdown行内/围栏代码，未闭合标签可随流式输入增量处理。
- 新增持久设置 `enableLocalGpu=false`。设置→实验性功能可显式启用Vulkan。CPU请求明确`n_gpu_layers=0`并不选GPU；启用时只选择Vulkan GPU/iGPU设备并请求99层。模型权重或上下文分配失败时释放GPU状态并回退CPU一次，失败/取消均不改设置。模型详情显示回退说明。
- Android 构建通过 Vulkan 实例函数表调用原先直连的 3 处设备特性查询。需要的查询入口缺失时，后端无法注册，随后由现有加载流程回退 CPU。JNI 只在显式 GPU 加载时注册后端。APK 保持 minSdk 26，llama.cpp 固定提交和子模块工作区完整保留。
- 增加Vulkan设备信息及generation结束耗时、token/s诊断。具体GPU后端/性能由Android设备日志证明，编译通过不代表GPU推理已在设备验证。

## 工具链与本机配置

- 固定`llama.cpp` `b1b770d90d94cad9e9ea5b993bdbc383a522fb28` (`v1.0.0`)核实有`GGML_VULKAN`、Vulkan CMake `glslc` component及`SPIRV-Headers::SPIRV-Headers`目标；未修改子模块。
- 主机依赖：`glslc`、Vulkan开发头及`spirv-headers`（Ubuntu可安装`glslc glslang-tools libvulkan-dev spirv-headers`）。NDK提供Android Vulkan loader和平台头，但不包含Vulkan-Hpp。shader在主机构建时编译；依赖不捆进源码ZIP。
- 实际构建使用 JDK 21、NDK 27.2.12479018、CMake 3.22.1、Vulkan headers 1.4.341 和 `glslc`/shaderc `2026.1-1`。本地额外解包的 `glslang-tools` 为 `16.2.0-2`，shader 生成器实际调用的是 `glslc`。ARM64 的 CMake 使用主机编译器和 Ninja 生成着色器。本机路径见下方构建命令。

## 验证与限制

- 定向`:llama-cpp:testDebugUnitTest :app:testGithubDebugUnitTest`通过（约3分26秒），覆盖回退状态机、提示dismiss隔离、think标签/原生reasoning/代码示例/逐字符流式。结果见`targeted-tests.log`。
- `test :app:assembleGithubRelease` 成功，耗时 9 分 20 秒，见 `release-build-vulkan-dispatch.log`。JVM 报告去重后共 279 套件、1,872 项，1,870 通过，DeepSeek 导入和 PDF 各 1 项缺本地语料跳过。最终调用复用了最新测试报告。
- **G1 通过。** 签名 ARM64 Release APK 为 **31,913,762 bytes（30.44 MiB）**，比补充四增加 **10,225,237 bytes（9.75 MiB）**。其中 `libggml-vulkan.so` 在 APK 中压缩后为 **10,221,450 bytes**，解压后为 **33,271,000 bytes**。
- APK 核验确认包名 `me.rerere.rikkahub.plus`、原正式签名、207、非 debuggable、仅 `arm64-v8a`。ZIP CRC、对齐、8 个推理库的 16 KiB ELF 对齐及 21 个 JNI 出口均通过。Vulkan 库的直接 Vulkan 导入均存在于 API 26 NDK stub 中。桌面和关于页共用的原图标通过资源、像素和自适应图层检查。英、简中、繁中三份文案各 2,706 个字符串，DB34。
- 源码 ZIP 根目录含 `DELIVERY.md`，保留原权限、软链和 6 个原有预编译依赖。相对补充四源码清单，8 新增、27 修改、0 删除。包外 `SOURCE-VERIFICATION.json`、`SUPPLEMENT5-6-DELTA.json` 和 `SHA256SUMS` 记录归档核验、增量与校验值。
- **真机待验。** A1–A6 的输入区提示、B1–B5 的思考区显示，以及 G2–G8 的 CPU/GPU 加载、回退和内存表现，均由维护者安装此 APK 验证。G3 LFM3、G4 Qwen3.5 4B、G5 Qwen3.5 9B 的 tok/s 待实测。

## 回滚边界

只撤销本批新增/修改的变更块及三语言GPU文案、`enableLocalGpu`字段/key；保留F020既有本地模型、提示预算、媒体、工作区及压缩代码。禁止整文件覆盖`ChatService`、`ChatPage`、`Settings`、Runtime、JNI或CMake。逐文件影响见`docs/f020-supplement5-6-change-map.md`。

## 补充五/六构建命令

```bash
f020_vk=/home/li/.local/share/rikkahub-toolchain/vulkan-20261118
LLAMA_NDK_PATH=/opt/android-sdk/ndk/27.2.12479018 \
LLAMA_GLSLC="$f020_vk/glslc" \
LLAMA_VULKAN_INCLUDE_DIR="$f020_vk/root/usr/include" \
LLAMA_SPIRV_HEADERS_DIR="$f020_vk/root/usr/share/cmake/SPIRV-Headers" \
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
./gradlew test :app:assembleGithubRelease -Prikkahub.abi=arm64-v8a \
  --no-daemon --no-parallel --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process \
  '-Dorg.gradle.jvmargs=-Xmx2560m -Dfile.encoding=UTF-8' --console=plain
```

其他机器先按 `llama-cpp/README.md` 恢复固定子模块，再用本机工具链路径或 CMake 自动发现的依赖构建。

# 历史交付 — F020补充四：本地模型提示预算

日期：2026-11-18，Asia/Shanghai。保持 **3.3.0-work.1 / 207**，DB34。
已阅读补充四00/01，随包02-AGENTS与改前本地一致；本批优先于下方补充三默认16K口径。
按维护者最新明确要求，交 **ARM64正式签名GitHub Release APK + 精简源码ZIP**，覆盖附件Debug示例；不生成Debug安装包。
独立目录：`artifacts/3.3.0-work.1/f020-supplement4-release-20261118/`。旧交付保留，没有commit/tag/push/发布或设备操作。

## 本批完成

- 新导入本地模型默认8192，已有4K/16K等上下文和UUID保持；不改既有Settings/Model/Assistant字段，无Room迁移。
- 请求组装增加本地提示预算阶梯：字符/3估算完整system和工具schema，超过输入55%先省略工具定义和所有工具systemPrompt（含技能列表）；剩余仍超过75%再省略两段记忆。必需人格、原历史及持久设置不被裁剪。
- 使用同名Ready模型真实窗口；API分支不估算schema，保留原system字节和工具/记忆。原Provider按完整用户轮裁历史、压缩完整输入、模板真实计数及Usage保持。
- 省略决定以TextGenerationParams的瞬态metadata传递，AgentLoop仅本地TOOLS省略时不重新注入定义；原执行工具/审批恢复表保留。Provider把两条省略说明与媒体notice合并为一个前缀，非流式也可见。
- TOO_LONG失败才计原始system/schema/history，带nCtx/input与三个大致分解；未知为?，取消继续传播。分解不是模板精确总数，不写入usage。
- CPU线程改为核心数限制在2..6，日志有threads；batch/ubatch512、CPU/F16及固定native源码不变。上下文输入框追加建议，模型详情复用更新后的context_hint。

本批是“让它能说话”的可用性修补，不是已验证的性能优化。真实tok/s、首token时间、KV内存压力与UI需维护者实测；很长的必需提示词或用户输入仍可能明确报放不下。

## 任务书的必要适配

- 真实StepRequest在ai/agent而非ChatRequestFactory文件；Provider不接收StepRequest，而且AgentLoop会覆盖params.tools。故只新增默认空的@Transient localPromptDrops，不新增持久化字段或跨模块反向依赖，补本地专用守卫。API路径保留原工具注入。
- 估算包括同一Mapper生成的工具schema，并优先读取Ready实际窗口，避免训练上限低于已保存窗口时误估。不加载/重载模型。
- 已有local_llm_context_hint，更新并共用；三语实际新增3key、更新1key，而非验收F1所写新增4key。首次检查发现重复定义后已修正，保留失败日志，不把它当成功证据。
- 错误分解占位符用字符串以支持?，已兼容旧的仅nCtx或无detail错误。组件为原始文本/JSON token估算，不能相加当模板或媒体真实输入计数。
- 参考项目外rikkahub-agent固定a88f5854的分请求预算和系统计数职责；不照搬其Q8/内存缩窗/懒加载。本批无脉搏调度改动，Kai/PalmClaw保留。

## 验证与验收

- 最终定向JVM成功1分5秒，覆盖AI/llama-cpp/App。之前资源重复、JVM的org.json实现缺失和≈字段解析问题均已修正，失败日志保留；App只增加与llama模块相同版本的测试依赖，不加入生产APK。
- 完整test及ARM64 Release一次调用成功 **7分54秒**，941任务中86执行、855 UP-TO-DATE；含R8、资源压缩、lintVital和正式签名，不生成Debug安装包。
- 去重 **277套件 / 1862项：1860通过、0失败、0错误、2项既有缺语料跳过**。GitHub/Play重复只计一次；定向成功报告和未变模块有UP-TO-DATE复用，不宣称全部重新执行，证据见TEST-RESULTS.json与release-build.log。
- 最终APK **21,688,525 bytes（21.69 MB / 20.68 MiB）**；包名me.rerere.rikkahub.plus，3.3.0-work.1/207，非debuggable，只有arm64-v8a。原正式证书SHA256为671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b。
- APK SHA256：`71d614e586480b362558fb39033b665d960044e2eab830bda2d0a521a6897023`。21源码身份图标及APK20PNG逐像素、adaptive三层引用、三语2703key、固定子模块/DB34、WASM/浏览器资产、CRC、七个推理so的16KiB ELF和21个JNI出口核验通过。
- 源码相对补充三 **5新增 / 23修改 / 0删除**，逐文件映射见docs/f020-supplement4-change-map.md；其余API供应商、工具工厂、持久字段、脉搏调度及native实现保持。归档CRC、逐文件哈希、权限/软链和SHA256记包外JSON及SHA256SUMS。

所有JVM用例、编译、APK内容检查均不等于真实GGUF或手机验收。

| 验收 | 本批离线验证范围 | 维护者仍需实测 |
| --- | --- | --- |
| A1/A2 | 新模型8K、已有16K/4K对象与UUID不变 | 覆盖安装和设置页实际显示 |
| B1/B2 | 4K小system保工具，长system/schema省略工具与技能prompt且不裁人格 | 真实4K“你好”出字、提示展示与工具调用 |
| B3/B4 | 巨大记忆按顺序省略两段，8K正常保留；无工具不误报省略工具 | 真实8K与记忆/工具交互 |
| B5 | API原system字节与工具列表、无schema估算；AgentLoop仍原注入 | 真实API回归，未操作账号 |
| C1 | 失败分解、未知?、旧错误兜底、取消不吞；正常无附加诊断计数 | 真实模型过长输入的卡片 |
| D1 | 8核心→6线程规则及日志接线，native批次不变 | 设备日志与前后prefill/tok/s对比 |
| E1/F1 | 建议文案接线、三语3增1改、占位符一致 | 手机布局换行与可读性 |

未用ADB、模拟器、真实账号或GGUF，未下载模型权重，未生成验收截图。

## 源码与复现

- 根含本DELIVERY、AGENTS/CHANGES/FEATURES及docs/f020-supplement4-change-map.md。完整逐文件变化相对补充三SOURCE-MANIFEST核对，无整文件回退此前工作。
- 固定llama.cpp为b1b770d90d94cad9e9ea5b993bdbc383a522fb28；源码包保.gitmodules、native/llama.cpp.commit、README恢复方式及原六个Git跟踪jniLibs依赖，保留软链与权限。
- 排除APK/artifacts/构建缓存/本机配置/签名私钥/权重/参考仓库与实际llama.cpp checkout；解压后先补齐固定子模块和本机工具链，正式签名由维护者本地配置提供。
- ZIP自身大小/哈希记包外SOURCE-VERIFICATION.json及SHA256SUMS，避免自引用。回滚按本批相对文件映射逐块反向撤销，不reset、不改原index、不覆盖前批F020与上游合并。

---

# 历史交付 — F020补充三：回归修复与工作区上传

日期：2026-11-18，Asia/Shanghai。版本保持 **3.3.0-work.1 / 207**，数据库仍为34。
依据维护者本轮附件 `RikkaHub Work-F020补充三-回归修复与工作区上传-20261118.zip`，已完整阅读00/01及随包AGENTS；随包AGENTS与改前根文件字节一致，保留历史并追加当前规则。
维护者最新明确改为 **ARM64 GitHub Release APK + 精简源码ZIP**，覆盖附件R14/§10的Debug交付口径；版本不变。
独立目录：`artifacts/3.3.0-work.1/f020-supplement3-release-20261118/`。旧APK及源码包保留，不自动commit/tag/push/发布。

## 本批完成内容

- A：加载胶囊去掉Popup，在ChatPage body中只放一处，按inputHeightPx加8dp定位；普通和液态玻璃宿主沿用同一个协调器。保留失败弹层、动画及输入框底部对齐。
- B：当前可用内存不再夹制nCtx，遵循训练/用户/16K上限及既有512地板。热页按权重1/4估算，KV支持逐层数组；输出封顶窗口1/4。分配失败报MEMORY，TOO_LONG带真实nCtx/input并本地化显示窗口值。
- B配套修正文案：内存融合只参与余量估算，不会自动扩大上下文；三语说明与当前实现一致，融合字段及默认值不改。
- C：Model新增chatTemplate=null；JNI支持UTF8模板覆盖和模板存在探测。配置有自动、ChatML/Llama3/Gemma预设、自定义；无内置模板时提示。正常终局无正文和有效工具调用显式EMPTY_OUTPUT，取消不误报；无界流式、逐token重解析、Usage和工具序列保留。未内置LFM模板。
- D：仅Google与本地模型保留直接视频历史；本地新上传还要求VIDEO输入模态。其余供应商在请求副本中转[视频]，保留其他part及消息元数据，不修改原消息、不自动重发。video_url拒绝响应使用三语提示。
- E：普通聊天、脉搏、群组及外部分享入口接入路由；图片/音频/可解析文档仍为聊天附件，其他文件存绑定工作区FILES/uploads/日期。2GiB实际读取上限、重名保旧、超过200MiB进度可取消；无工作区先提示绑定，不落临时目录，保留文字草稿。
- E：Document.url以/开头表示FILES相对路径，metadata保存大小与所属工作区。chip显示文件名/大小并支持打开、复制路径，缺文件显示错误态；模型仅收到路径元数据，不读二进制全文。不改聊天备份边界，删除消息引用不删除工作区文件。
- F/G：去旧VIDEO能力开关与tag但保留枚举/视频消息兼容；内置工具说明恢复Gemini官方API口径。长按本地模型返回供应商默认配置，不自动弹表单，手动模型编辑与旧导航字段保留。

## 验证与证据

- A/B独立检查成功（1分5秒）；C的JNI Debug库、llama-cpp JVM及App Kotlin检查成功（2分11秒），尚不等于加载真实模型成功。
- 四模块定向回归成功（2分13秒）：AI、llama-cpp、workspace及App；本地与本机环回SSH失败上传测试确认只删除新残片、同名旧文件保留。先前旧OpenAI视频用例按本次直传规则修订，未改变Google编码器。
- 切换交付前全模块test及内部Debug构建成功（4分22秒，449任务、47执行、402 UP-TO-DATE）；Debug不作为本轮交付。最终Release和产物核验结果在下方追加，机器可读证据置于同一交付目录。测试存在UP-TO-DATE复用，GitHub/Play相同App用例去重，不宣称每个变体均重新执行。
- 最终全模块报告去重为275套件1846项，1844通过、0失败、0错误、2项既有缺语料跳过；详见TEST-RESULTS.json。当前四模块定向用例先执行通过，完整test复用这些报告，另执行Play及搜索用例；该数量不是设备测试数。
- 测试覆盖：低availMem仍保持用户窗口、混合层KV GGUF夹具及截断安全、输出预算、context=0、模板默认/UTF8透传、工具only/空输出/取消、请求视频降级、类型路由及providerOverwrite、虚拟2GiB读取边界、取消检查、路径元数据与聊天备份排除、三语报错。
- 构建环境为JDK21、NDK27.2.12479018、CMake3.22.1，CPU推理与F16保持；只指定arm64-v8a。固定llama.cpp为b1b770d90d94cad9e9ea5b993bdbc383a522fb28，未改子模块源码；参考rikkahub-agent为a88f5854b4d2e2706ea2ae8304a7d66a3f9f2bbc。
- 最终Release构建成功（5分41秒；842任务中68执行、774 UP-TO-DATE），包含App JVM资源回归、R8、lintVital、资源压缩及正式签名。此前因修正文案主动终止的构建保留为release-before-copy-fix.log，不作为成功证据；成功日志为release-build.log。
- 最终APK为 **21,683,929 bytes（21.68 MB / 20.68 MiB）**，包名me.rerere.rikkahub.plus、版本3.3.0-work.1/207、非debuggable、仅arm64-v8a。原正式证书SHA256为671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b。
- APK SHA256为 `1df3ddf9f2b65a3210348616929b94d6b39b95b862b5c4fa30cf62a1e1acbd23`。七个推理so的16KiB ELF对齐、21个JNI出口、20张PNG逐像素及adaptive图标引用、WASM/浏览器资源、三语2700key和CRC核验通过。R8保留LlamaCppJni原类名，未把JVM假接口当成native实测。
- 源码相对上一批增加8项、修改46项，无删除；逐文件映射与其余文件保持情况见docs/f020-supplement3-change-map.md及包外SUPPLEMENT3-DELTA.json。最终源码包大小与SHA256仅写包外，根DELIVERY已纳入压缩包。

## 逐项验收边界

| 验收项 | 已有源码/离线证据 | 未执行的设备验收 |
| --- | --- | --- |
| A1–A5 | 页面内唯一胶囊、动态高度及原点击modifier，普通/玻璃分支编译 | 抽屉遮罩、导航、IME多行、可见位置与点击命中、玻璃层级 |
| B1–B5 | 16K/4K计划、低内存不夹窗、4K输出1024、实际nCtx错误与逐层KV数组测试 | 手机“你好+工具”真实回复、内存挤压、native实际分配与LFM3 KV日志 |
| C1–C5 | null兼容/自定义模板透传、无模板状态、正常空/有效工具/取消测试；JNI构建 | 真实GGUF模板渲染、LFM3详情警告和实际输出、设备停止生成 |
| D1–D4 | OpenAI实际序列化无video_url、源消息不变、Google/Local不降级 | 真实Google请求、旧会话继续聊及本地视频抽帧 |
| E1–E3 | FILES/日期路由、同名后缀、旧文件保留，本机本地/SSH导入用例 | SAF选真实mp4/zip后chip与编辑器导航、实际工作区shell |
| E4–E5 | 无工作区在读流前拒绝；已知大小及实际读取均有2GiB限制 | 设备未绑定提示与草稿、2.1GiB真实源文件 |
| E6–E7 | 只注入路径/大小、metadata固定归属；缺文件错误态与取消不吞 | 模型根据路径回答、删除工作区后点旧消息 |
| E8–E9 | IO流、200MiB进度阈值、取消清残片、保原图/音频/解析器分支 | 大文件进度/取消、SSH真实断网残片清理及手机图片/音频/pdf回归 |
| F1–F4 | 去开关/标签、旧VIDEO仍可反序列化、三语key集合一致 | 正式版带旧数据覆盖升级与旧配置实际显示 |
| G1–G4 | 长按默认供应商路由、手动模型卡打开及tab兼容保留 | 手机长按本地/API、页签切换与手动编辑 |

未使用ADB、模拟器、真实账号或模型权重，未下载权重，未生成验收截图。
最终Release沿用正式包名 `me.rerere.rikkahub.plus` 及原签名；实际覆盖安装和旧数据验收仍待维护者在设备上执行。

## 任务书与现有接口差异

- 任务书所述withoutVideoParts实际已有占位文本，本轮保留原压缩语义，另加[视频]请求扩展。Google/Local编码不改，未删除旧VIDEO枚举或Video消息。
- GGUF数组无法从只报告第0层的public模型信息接口取得；增加有界metadata扫描，不读tensor。模板预设来自固定llama.cpp的MIT源码，机制先行，没有猜测LFM专用模板。
- 额外共享改动仅限FilesManager与本地/SSH导入的失败收尾，避免取消留下残片；cleanupTempDirs未改。SSH已断线时远端删除只能尽力，不宣称故障网络已验证。
- 工作区存在检查使用同时支持SSH的fileSize；导航采用现有AssistantBasic/GroupConfig/PulseSettings。视频/zip按要求进入既有文本编辑器，不能保证能播放或解压，大文件可能提示无法预览；可复制路径交给工作区工具处理。
- 保存workspace_id/size使用Document既有metadata，无Room迁移。为无MIME的外部file URI补文件名类型推断；切换所属工作区取消旧上传，避免结果进入错误草稿。

## 源码与复现

- 精简源码根含本DELIVERY、AGENTS、CHANGES、FEATURES、docs/f020-supplement3-change-map.md及构建脚本；保留软链、权限、UTF8文件名，以及源码原有六个Git跟踪的libsimple/proot依赖。
- 排除APK/artifacts/构建缓存/本机配置/签名私钥/真实权重/参考仓库/实际llama.cpp目录；含.gitmodules、固定commit及llama-cpp/README恢复步骤，复现前需补齐子模块与本机工具链。
- 文件集合、CRC、逐文件哈希、权限、原21图标、三语、DB与native出口等记录在包外JSON；最终ZIP自身大小/哈希只写包外SOURCE-VERIFICATION.json和SHA256SUMS，避免自引用。
- 不重置或清理用户工作区、不修改既有index。只可按本补充的相对清单反向回滚，不能覆盖整个文件丢掉前批F020与上游合并。

---

# 历史交付 — F020显式加载与短上下文压缩，3.3.0-work.1 / 207

日期：2026-11-18，Asia/Shanghai。按维护者要求保持同版本207，仍为DB34。
本批以`RikkaHub Work-F020后续-显式加载与短上下文压缩-20261117.zip`为准，
顺序为01显式加载先完成并通过Release Kotlin编译，再实施02短上下文压缩，最后统一构建。
交付目录：`artifacts/3.3.0-work.1/f020-followup-release-20261118/`。
一个ARM64签名GitHub Release APK及精简源码ZIP，源码根含本文件。下方11月17日首批与更早交付均为历史。
未自动提交、推送、创建标签或发布；旧APK/源码ZIP保留。

## 本批已实现

- 明确Idle/Loading/Ready/Failed及三加载阶段；普通/液态玻璃输入区的单聊、脉搏共用加载胶囊，带秒数、失败详情、重试和Ready淡出。API/群组不显示本地胶囊。
- 模型详情共用加载/卸载状态；生成路径只withReady，不懒加载/自动重载。未加载发送不清草稿，不显示误导性的网络错误。群组须先从模型详情手动加载。
- 后台30秒仅静默通知，点击才卸载；生成中请求延后，异常/取消也在finally消费；返回前台撤销待卸载。脉搏任务会用当前本地模型时建议保留，无卸载按钮。
- 长按本地模型直达模型配置表单，只展开一次；API长按保持原行为。1–16 K输入，非法值不写回。用户模态许可不再被冷启动/生成覆盖，实际解码取配置与引擎交集。
- 设备总内存不足只警告可继续；availMem只用于上下文规划、地板512。下载检查模型+投影器及256MiB余量，实际Content-Length在.part创建前再检验。
- 唯一新增持久字段enableMemoryFusion=false，设备级；系统swap存在时可选增加min(SwapTotal/8,1GiB)的上下文余量。门禁不包含swap；默认上下文16384和F16 KV不改。Fusion/每轮nCtx/VmSwap诊断接入。
- 复用原insertCompressionCheckpoint。本地按4K/8K/16K/更大分输入1500/3000/6000/12000、输出300/500/800/1200和阈值.65/.70/.75/.80。按完整用户轮选前缀，旧checkpoint计入，被替代历史不重复计入；一次只压一段。
- 本地工具长输出仅裁请求副本；压缩模板后复核真实token（含系统、工具定义与摘要指令），可继续收窄至上一完整轮。压缩适配器禁止正常聊天的自动裁剪，放不下一轮就明确TOO_LONG、不写检查点；所有未处理历史保持。检查点在实际windowEnd-1之后。
- 每正常生成轮在release(applied)前计native输入/原始完整输出，Finish前发Usage。非流式返回同一usage；保持工具轮各自计量及原merge/sum。仅本地缺usage时字符估算用于自动触发，不写入精确用量或UI。

## 构建、测试与包外证据

- 01的App Release Kotlin编译及llama-cpp JVM通过后才改02。首次App JVM资源阶段发现单ABI输出被过滤为空的“No non-density apk found”，已修复输出过滤条件；不是模型运行失败。
- 两批针对性`:app:compileGithubReleaseKotlin :llama-cpp:testDebugUnitTest :app:testGithubDebugUnitTest`成功，3分32秒。最终完整test及Release结果见本节后续核验与同目录`release-build.log`、`TEST-RESULTS.json`。
- 完整test报告去重273套件1828项，1826通过、0失败、0错误、2项缺外部语料跳过；GitHub/Play重复用例只计一次，未变模块存在UP-TO-DATE复用，不宣称全部重新执行。跳过为既有DeepSeek导出附件与PDF语料用例。
- Runtime的8项fake-native测试涵盖三阶段/幂等、未加载、失败释放、projector文本降级、异常/取消待卸载、前台撤销及缺文件；物理内存/融合/空间3项，压缩预算/窗口/检查点/模板9项。它们不加载真实权重，不是手机性能或native推理证据。
- 保留原流式每token整段partial解析、最终权威解析、无界缓冲和检查trySend；新增工具输出计量一次、Finish前usage测试。原模板/工具/取消/文件/HTTP下载/设置兼容测试继续运行。
- 构建使用JDK21、NDK27.2.12479018、CMake3.22.1、`-Prikkahub.abi=arm64-v8a`；完整命令与日志在产物目录。`:llama-cpp:assembleDebug`仅为库AAR，不生成Debug安装包。
- 真正APK检查包含207版本、非debuggable、原正式证书、ARM64库、原launcher/About身份资源、三语、WASM及CRC；native ELF 16KB对齐与JNI导出仍逐项核对，见最终JSON。
- 最终完整test与Release一次调用成功，17分16秒，957任务中70执行、887 UP-TO-DATE。APK为**21,668,645 bytes（21.67 MB / 20.66 MiB）**，比11月17日首批增加34,880 bytes；versionName/Code确认为3.3.0-work.1/207，原正式证书SHA256为671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b，非debuggable、仅ARM64。
- APK SHA256：`4f60e084c3c8e5f07d9b2ff76a5c5932d64a2cf64f60c9e7b94f2e9cc0fcba32`。20张launcher PNG逐像素、adaptive三层引用及源码21身份资源通过；三语各2683 key，新增31条和占位符一致，七个推理so的16KB ELF与20个JNI出口通过，WASM/浏览器资源与源码一致、CRC通过。
- 精简源码最终字节数、文件集合和SHA256以同目录`SOURCE-VERIFICATION.json`、`SOURCE-MANIFEST.json`、`FOLLOWUP-DELTA.json`及`SHA256SUMS`为准；源码中包含本DELIVERY，避免把ZIP自身哈希写入ZIP造成自引用。

## 验收状态

| 任务书项目 | 已有离线证据 | 仍待手机/真实模型验证 |
| --- | --- | --- |
| 01 A1–A9 | 双input路径、主题色、三阶段秒数、失败映射、共用单例、未Ready发送与淡出均已接线并编译 | 胶囊实际位置/点击/主题/IME、加载失败详情与正常回复；截图未生成 |
| 01 B1–B7 | 30秒仅提醒、权限检查、脉搏保留；推理锁及finally/前台撤销JVM验证 | ROM前后台生命周期、通知动作/时序、权限关闭及真native释放 |
| 01 D1–D10 | 默认null导航参数、按模型定位只消费一次、本地K输入/API原预设；模态保留及投影器增删JVM验证 | 长按打开、旋转/保存、冷启动及不支持媒体提示 |
| 01 回归C1–C4 | projector阶段、删除与推理锁、群组无胶囊；完整JVM报告见包外JSON | 真图片/音频/视频、删除及群聊实际发送 |
| 01 F1–F11 | 总内存警告纯规则、API34守卫、512下限、融合余量/默认false持久化与三语说明检查 | 真实8/16GB设备、内存压力、swap/速度、无swap控件与诊断日志 |
| 01 G1–G3 | .part前两次空间预检、NO_SPACE本地化；原下载/取消和空间规则JVM | 手机磁盘写满、文件清理、恢复下载 |
| 02 C1–C3 | 大型历史夹具选前缀、严格输入检查、连续检查点原节点全保留JVM | 150K真实对话+16K权重成功压缩与后续聊天日志 |
| 02 C4 | API仍全量、maxTokens=null、全局阈值；无新裁剪/估算/长度指令 | 同一真实API请求/缓存对比 |
| 02 C5/C9 | 16K采用.75，真实usage优先，缺失仅本地估算并日志；同一自动压缩入口 | 连聊到约12K触发日志与实际单次压缩效果 |
| 02 C6/C7 | 8000字工具结果前800/后400、标记和原件不变；4档+非本地规则JVM通过 | 模型摘要可读性 |
| 02 C8 | native计数→Usage→现有StreamChunkHandler/UI链路编译，Usage先于Finish夹具通过 | 真promptTokens/completionTokens日志、三轮上下文显示 |

**未连接ADB/模拟器/手机、下载GGUF权重或使用真实账号。没有伪造任务书要求的加载/通知截图、150K压缩、用量和AutoCompress日志。**
日志代码已接入，但真实记录必须由维护者安装APK、加载模型后取得。

## 非本地路径自查与任务书偏差

- buildCompressPrompt的原文本与七段结构不改，maxCharacters=null不追加；API的source仍原messages.withoutVideoParts，buildStep默认限制与变更前相同。params.copy(maxTokens=null)语义保留，不采用任务书会回落助手上限的示例。
- API自动阈值仍取全局设置，usage=null仍不触发，绝不加入估算。普通/API推理、F004审批、F007 reasoning、F009边界、群组/脉搏调度及Room34未改。
- 16K阈值按档表及C9的.75，忽略C5矛盾的.7。估算窗口后追加真实模板预检及严格压缩适配器，是为了防止Provider原本的trim静默裁掉待压缩历史；不是新增压缩系统。
- 胶囊用非focusable Popup保持可见位置与实际点击区域一致；两条宿主都接入，不占消息布局。视觉与输入法行为仍待验。
- 通知大小明确为“已加载模型文件约X GB”，不冒称测得真实RAM或必可释放量。
- 本地供应商此前仅配置页而无模型tab，本批按任务目标补CONFIG/MODELS；无API页签变化。本地模型安装集合仍由安装器管理，参数编辑按最新UUID合并。
- 改设置影响下次显式load；已Ready须先卸载再加载应用新上下文/融合配置。极低内存降至512后，提示词与请求输出上限仍可能放不下，明确报错，不保证任何设备都可推理。

## 未做项、参考、归档与回滚

- 本批不做KV q8_0、默认4K、预算模型深度重写、KV session存盘、LiteRT、群组胶囊、加载取消/百分比或后台自动卸载。
- 参考项目外`rikkahub-agent`固定a88f5854的Runtime锁/取消/释放、Provider与native接口；llama.cpp仍v1.0.0/b1b770d9，无子模块源码变化。Kai/PalmClaw保留，未改脉搏调度。
- 逐文件及上游重套清单见`docs/f020-followup-change-map.md`。回滚以11月17日首批manifest为边界逐块撤销，不重置脏树、不撤销3.3.0合并、数据库或原图标，不删除用户权重/历史。
- 新源码ZIP排除.git、artifacts、缓存、生成native输出、权重、APK及凭据；保留两个软链/权限、根DELIVERY和原Git跟踪的六个libsimple/proot依赖，纠正首批“一刀切排除.so”。这些既有依赖字节与HEAD核对，不是新构建产物。
- llama.cpp实际子模块内容仍按约定不打包，保留.gitmodules、commit pin、MIT许可及README中的精确恢复命令。源码目录的实际集合/哈希/权限/UTF-8/CRC和产物SHA256均记包外JSON，避免ZIP自身哈希自引用。

# 历史首批交付 — 上游3.3.0 + F020本地模型，3.3.0-work.1 / 207

日期：2026-11-17，Asia/Shanghai。维护者最终指定合并后直接实施F020，
统一交付一个ARM64 GitHub Release APK与精简源码ZIP；合并阶段没有生成APK。
最终版本固定207，不采用早先建议的work.2/208，也不采用旧任务书的206/DB33。
产物目录：`artifacts/3.3.0-work.1/upstream-f020-release-20261117/`。
本节为当前交付记录，下方各版本均为历史。

## 基线、范围与参考

- fork合并前：31f3e849f349ba2bea4d302390b633c1a2f01f34；上游3.3.0：eb33917e483aae1427db9cb10232642a640d2986。
  独立合并提交：655f8517c596be797eb081e9d1556404e82c6923。F020当前为该提交上的工作区增量，未自动commit/push/tag/发布。
- 本地模型参考：保留在项目外的ExTV/rikkahub-agent，a88f5854b4d2e2706ea2ae8304a7d66a3f9f2bbc（v2.5.1-agent.1）。
  先读其JNI、Runtime、Provider、DeltaTracker、请求映射、上下文规划，再适配本工程sealed StreamChunk及固定native接口；不覆盖现有API供应商实现。
- llama.cpp：`llama-cpp/native/llama.cpp`，v1.0.0，**b1b770d90d94cad9e9ea5b993bdbc383a522fb28**。
  未修改子模块源码。仅CPU，关闭GPU/OpenCL、ffmpeg、server及subprocess；使用mtmd和Android媒体解码。
- 完整上游3.3.0功能随合并接入：DeepSeek ASR/TTS、DeepSeek记录导入、气泡段落拆分、工作区存储挂载、Vertex Interactions、安全模式和连接测试弹窗。
  群组自动压缩统一全局入口，移除七个重复自动配置字段；保留手动压缩、分层摘要及可编辑提示词。Phone Use同步禁止操作微信。
- DB34保留15表14索引；工作区14列，owner/shared_rootfs_from与fork行式记忆表保留，33.json字节不变。
  三语各2652个string key（F020各新增57），原21个launcher/About应用身份资源逐文件匹配固定基准。F007/F009、群组、脉搏、Rootfs及玻璃入口保留。

## 构建与测试证据

- 合并阶段完整test成功，1分50秒；没有中间APK。
- F020首次最小native原型构建成功45秒；完整JNI/模块与App Release Kotlin编译成功2分46秒。
  这些是不同阶段的增量构建耗时，不是删除全部缓存后的冷构建耗时，也不包含真实模型加载。
- 最终完整 `./gradlew test --no-parallel --max-workers=1 --console=plain` 成功，1分25秒，356任务中12执行、344 UP-TO-DATE。
  去重270套件1806项：**1804通过、0失败、0错误、2跳过**。App962项、AI426项，其他模块合并统计；GitHub与Play重复用例只计一次。
  跳过的是缺真实导出附件的DeepSeekImporterTest和缺PDF语料的PdfCorpusTest，均保留原Assume条件，未删改测试凑绿。
- llama-cpp模块20项全部通过：流式缓冲与取消6、上下文及工具回放6、GGUF与目录4、真实loopback HTTP下载/取消4。
  最终test复用前次通过的模块报告，不能称为全部1806项重新执行；详细任务状态/报告时间见本目录TEST-RESULTS.json。
- Release命令：

```bash
LLAMA_NDK_PATH=/opt/android-sdk/ndk/27.2.12479018 \
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
./gradlew :llama-cpp:assembleDebug :app:assembleGithubRelease \
  -Prikkahub.abi=arm64-v8a --no-daemon --no-parallel --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process \
  '-Dorg.gradle.jvmargs=-Xmx2560m -Dfile.encoding=UTF-8' --console=plain
```

其中assembleDebug只生成本地模型库AAR/so，不生成Debug APK；最终安装包仅GitHub Release。
最终构建耗时、包体与签名数据在本节末尾的“最终产物核验”及包外JSON补记。

## F020逐项验收

“静态/JVM通过”仅说明代码、编译或离线协议证据；没有ADB、模拟器、手机截图、真实账号或权重推理。
所有真实交互/性能/识别效果仍待维护者安装本批APK验证。

| 编号 | 已有证据与当前状态 |
| --- | --- |
| A1 | 本地模型模块AAR/native编译通过；先前ARM64和x86_64均编译，本次最终仅ARM64。 |
| A2 | 最终Release构建成功，R8、资源优化、lintVital、签名及打包全部通过；见release-build.log。 |
| A3 | APK内JNI及llama/ggml/mtmd动态库逐项检查，结果见APK-VERIFICATION.json。 |
| A4 | 与206 ARM64 APK实测文件字节对比，见最终产物核验；不把任务书4~8MB预估当实测。 |
| A5 | APK条目检查不含GGUF，源码ZIP同样排除模型权重。 |
| B1 | 目录单测确认五个模型按主权重体积升序，列表接线静态通过；页面待实机。 |
| B2 | 详情约屏幕66%高度、六格规格，4B主权重3.46GB+编码器0.68GB；视觉待实机。 |
| B3 | 下载与真实Content-Length进度、成对完成后写索引有实现；loopback传输通过，真实大文件安装待验。 |
| B4 | 下载IO/5xx最多两次重试、4xx不重试及本地化失败已检查；真实断网操作待验。 |
| B5 | OpenDocument复制、GGUF magic/架构提示、同名后缀及原子改名有实现与文件测试；系统文件选择待验。 |
| B6 | runtime锁内删除、索引清理及Settings模型/收藏/选择回退有实现和设置测试；实机文件删除待验。 |
| B7 | 启动修复移除缺失主模型索引，清除自身目录的.part及无索引残留；真实重启待验。 |
| C1 | 512片段慢消费者测试无丢字、无空正文增量；真实native逐字输出待验。 |
| C2 | 阻塞生成取消及不伪造Finish有JVM测试；真实native停止延迟待验。 |
| C3 | 保留system与最后user、按完整旧用户轮次裁剪、真实模板/媒体token复核有实现；长对话测试通过，真实模型待验。 |
| C4 | 模型切换/串行锁/native句柄生命周期静态复核；真实两模型切换待验。 |
| C5 | 五轮稳定性、温度/速度/内存：未实机，不能判通过。 |
| D1 | RGBA图片→mtmd通道及能力映射编译通过；看图识别效果未测。 |
| D2 | 纯文本模型不支持图片时移除附件并输出提示，继续正文；真机待验。 |
| D3 | mmproj缺失/加载失败保留主模型并降级、显示失败提示；真机待验。 |
| D4 | 多图片按parts顺序传递，每张有独立占位/bitmap；两图内容识别未测。 |
| D5 | Android音频解码/单声道/重采样，最多60秒并提示截断；Gemma音频识别与编码耗时未测。 |
| D6 | Android视频1fps最多16帧、无音轨/ffmpeg；Qwen视频内容识别未测。 |
| D7 | 不支持音频时提示并继续文本；纯文本模型真机交互待验。 |
| E1 | 三语XML、57新增key及旧key完整性检查通过；实际三语排版待验。 |
| E2 | 上部滚动、底部固定及能力标签FlowRow已接线；小屏/大字体/横屏待验。 |
| E3 | 安装后禁用“已安装”按钮并保留删除确认；实际点击待验。 |
| E4 | 本地配置仅通用启用/名称/下载前缀/列表入口，隐藏API模型与高级页签及连接测试/分享；实机待验。 |
| F1 | metadata内存规划、85%预检及本地化不足提示有测试；16GB设备加载9B未测，无法保证不会被ROM杀进程。 |
| F2 | 30分钟加载标记，下次启动提示可重试、不禁用模型；真实杀进程/崩溃未测。 |
| F3 | 复用原生成/保活机制，没有增加生成超时；后台切换实机待验。 |
| F4 | load/template/generate/delete共用Mutex，取消按请求owner限制；真实标题+建议并发待验。 |
| G1 | 原API类型复制/序列化、额度与视频回归通过；未使用真实API账号。 |
| G2 | 本地Provider进入既有群聊链路，工具StreamChunk有离线测试；群内真实工具调用待验。 |
| G2b | 两轮交错工具调用/结果请求映射测试通过，完整保留工具边界；真实模型连续工具轮待验。 |
| G2c | 本地Provider进入既有脉搏链路、不扩展审批豁免；带工具心跳未实机。 |
| G3 | 脉搏沿用标准ProviderManager选择本地类型；真实心跳未测。 |
| G4 | Provider序列化/恢复旧设置与安装条目同步有JVM测试；备份只含设置和原附件目录，不含models权重，恢复后缺权重清理选择并需重新安装；设备恢复未测。 |

## 任务书适配、边界及后续重套

1. 以用户最终207/DB34为准。上游合并单的工作区13列、26新增key分别是数量误差，实际14列、25上游key；没有为凑数改schema或文案。
2. llama.cpp v1.0.0的PEG parser没有参考版的JSON序列化API，改用native内存中的不可变parser票据。
   应用只原样传递applied模板blob；每token仍整段解析，最终解析权威，结束时释放票据。保留模板声明的特殊token，避免工具/思考标记丢失。
3. mtmd JNI与主JNI集中在同一C++文件，使用同一个上下文/取消标记；视频走Android解码器和v1.0.0的video source协议，不引入ffmpeg。
   媒体token化或prefill编码失败在输出前退回纯文本；取消不触发正常完成事件。
4. 新增Modality.AUDIO/VIDEO在枚举末尾；未给Model增加字段/新ModelAbility，也不改变其他供应商的音视频判据。工具请求分组仅把原PartGroup/扩展公开复用。
5. 原设置归一化不会清理删除模型的全部收藏/助手/群组/脉搏引用，新增withLocalModels只处理本地供应商并保留既有UUID/API配置。
   上游会话固定模型失效时，沿用getChatModelOf既有的助手模型回退，不重写会话历史。
6. 安装所有文件完成才入索引；下载回调在取消后完全停止写入才删除.part。启动仅清理本应用私有模型目录内未索引GGUF/part，不遍历用户目录。
7. ChannelWatermark按参考版处理append-only限制：中途reset抑制，终局修正保留内容；遇到解析器终局改口仍可能出现可见重复，这是参考策略的已知取舍，不承诺任意模型模板永无重复。
8. SIGSEGV无法被JVM catch拦下；加载中断标记只是提示，不是兼容性判定。不下载权重、不实际推理，模型目录与文案依任务包固定内容，不宣称五模型均已实测可用。

上游冲突重套重点：ProviderSetting/ProviderManager/ProviderQuota/VideoInput/ProviderMessageUtils、
DefaultProviders/DataSourceModule/ViewModelModule/RikkaHubApp/RouteActivity、
ProviderConfigure与两处供应商设置页、FilesPicker/ModelList/额度和加载动画、导入器与三语资源、settings.gradle/app构建接线。
**完整逐文件列表与上游标记见 `docs/f020-change-map.md`**，不是仅列这几处作为全部改动。
未改脉搏调度/工具策略实现，不引入Kai/PalmClaw以外的新脉搏设计；此前两仓继续保留在项目外。

## 源码归档与复建

- 精简源码ZIP根包含本DELIVERY，排除.git、artifacts、构建缓存、.cxx、.so、APK、模型权重、local.properties、签名私钥及参考克隆。
- 按任务书排除llama-cpp/native/llama.cpp实际内容；ZIP无法编码Git gitlink，以.gitmodules、native/llama.cpp.commit与README记录精确指针及恢复命令。
- Git checkout用git submodule update --init --recursive；直接解压源码ZIP则先按llama-cpp/README.md克隆并checkout固定hash，再配置自己的SDK/Release签名。
  不提供本机私钥；签名一致性只对交付APK校验。
- ZIP文件集合、逐文件哈希、UTF-8文件名、Unix权限、符号链接及CRC分别核验，SHA256SUMS放包外，避免自引用。
- 未操作设备/模拟器/真实账号，没有远端push、tag、GitHub发布或源码上传。

## 最终产物核验（2026-11-17）

- 最终Release构建成功，**19分7秒**，632任务中125执行、507 UP-TO-DATE。该耗时包含最终ARM64模块AAR/native复核、Release Kotlin/R8/Lint/签名与打包，不是冷缓存全量耗时。
- APK：`RikkaHub.Work-3.3.0-work.1-arm64-v8a-release.apk`，**21,633,765 bytes（20.63 MiB / 21.63 MB）**。
  206基线ARM64为16,466,553 bytes（15.70 MiB），净增5,167,212 bytes（4.93 MiB / 5.17 MB），落在任务书4–8MB预估范围。
- 包名me.rerere.rikkahub.plus；3.3.0-work.1 / 207；非debuggable；仅ARM64；无GGUF。
  正式证书SHA256为671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b，与旧Release相同。
- JNI/llama/llama-common/ggml/mtmd共七个本地推理动态库存在、ELF LOAD均16KB对齐；20个Kotlin声明的JNI导出全部存在。
  R8保留JNI类及TokenSink/onToken名称，APK资源仅默认英文/简中/繁中。
- APK中原20张身份PNG逐像素匹配，adaptive图标继续引用原三层；源21资源也匹配固定基准。
  deepseek_sha3.wasm与浏览器Readability脚本逐字节一致，ZIP CRC通过。
- APK SHA256：`0d493661e786c7afc918fe48c248ce06adee813251ac064ce663b406561f2b16`。
  本目录提供APK-VERIFICATION、STATIC-VERIFICATION、ICON-VERIFICATION、TEST-RESULTS及构建日志。
- 精简源码集合1871项，根含DELIVERY，保留两个软链与Unix权限；不包含llama.cpp实际子模块内容，需按模块README恢复精确版本后构建。
  ZIP最终大小、哈希及逐项验证由SOURCE-VERIFICATION.json与SHA256SUMS记录；不把本ZIP最终哈希写回ZIP内部。
- 不生成Debug安装包；未安装设备或实际下载/运行模型。维护者重点验收B/C/D/E/F及群聊/脉搏工具场景，未实测项目不标为通过。

# 源码同步准备 — 3.2.0-work.3 / 206

日期：2026-11-17，Asia/Shanghai。维护者要求“整理源码，给我命令，我来推送”。
本轮整理本地提交与精简源码ZIP，远端推送、标签对齐和GitHub发布由维护者执行。
下面构建批次“不commit”等内容是历史记录，当前同步状态以本节及git log为准。

- 源码范围：静默心跳整条匹配、压缩请求去视频、五协议流式/非流式报错本地化、206版本及配套测试和文档。保留此前完整上游3.2.0及fork功能，数据库33。
- 本轮不改产品：与silent-video-errors-release-20261116的已核验1822项源码逐一比对，仅AGENTS/FEATURES/本文件补记同步状态，其余1819项保持；不重新构建APK或重复执行未变的产品测试。既有1747项中1746通过、1项PDF语料跳过及最终构建缓存复用的说明继续适用。
- 本地分支merge/upstream-3.2.0，父提交为11441abe5ad9261bb07d14ee60614e70a9af4967（work.2/205）；实际新提交SHA记录于本批SYNC-VERIFICATION.json，避免提交内容自引用。提交包含本轮全部29个改动路径，不含APK、缓存或密钥。
- 已fetch并只读核对远端：origin/master和3.2.0-work.3标签都仍为11441abe5ad9261bb07d14ee60614e70a9af4967，源码实际为work.2/205。先普通推送新HEAD到master，成功后使用限定该标签旧值的force-with-lease对齐work.3。master不强推，work.2及其他标签不改；旧值变化时租约检查应拒绝覆盖，需重新核对。
- origin继续使用git@github.com:ziwa0213-crypto/rikkahub_work.git。提供的推送命令以&&串联，master推送失败即停止，不执行后面的标签对齐。
- 新交付目录：artifacts/3.2.0-work.3/source-push-preparation-20261117/。源码ZIP根含DELIVERY，保留1822项、两个符号链接和Unix权限；排除.git、artifacts、APK、构建缓存、本机配置、签名私钥和两份参考仓库。文件集合、逐文件哈希、权限、UTF-8文件名、CRC及提交树一致性分别记录在SOURCE-MANIFEST.json、SOURCE-VERIFICATION.json和SYNC-VERIFICATION.json；最终ZIP哈希放包外SHA256SUMS。
- 原三个206正式签名Release及旧源码ZIP均保持哈希不变，仍位于silent-video-errors-release-20261116目录。未运行设备、模拟器、ADB或真实模型；D/V/E交互验收仍待维护者完成。本轮没有push、移动本地/远端标签、创建或更新GitHub Release。

# 静默心跳、压缩去视频与聊天报错本地化 — 3.2.0-work.3 / 206

实施日期：2026-11-16；构建、核验与交付：2026-11-17，Asia/Shanghai。依据新任务包《静默视频与报错中文化》00–03实施；
04-AGENTS与改前根AGENTS字节一致，未用附件覆盖项目记录。基线11441abe5ad9261bb07d14ee60614e70a9af4967，
保留完整上游3.2.0合并，数据库33。本轮未另合并上游、自动提交、操作标签、推送或发布。
交付目录：artifacts/3.2.0-work.3/silent-video-errors-release-20261116/。以下旧批次章节仅作历史。

## 实施范围与任务书校准

- 心跳：isSilentPulse整条白名单，先trim再剥指定尾部标点再trim；不会把“无异常，但…”或多行提醒当静默。
  Runtime保持heartbeat、非待审批及静默开关守卫，静默活动仍OK；任务/监视器/短信均不进入此静默分支。
  DEFAULT_HEARTBEAT_PROMPT只增加不得用中文词替代标记的一句；老用户已保存的自定义文本不迁移。
- 视频：withoutVideoParts只用于requestCompressionSummary的buildStep输入，纯复制，原消息与其他附件不改。
  群组压缩仍为纯文本；正常聊天的视频能力校验/编码、历史显示与自动压缩路由保持原样。
- 错误：五处非流式HTTP及message-is-null改结构化类型，保持原message；ErrorText按类型选择资源，
  HTTP原body仅在显示时trim并取400字符，原异常挂cause。未知类型原样透出，取消提前返回；
  原addGenerationError视频分支、ChatError结构、卡片布局/链接与重试策略均不改。四处模型/供应商缺失与未知兜底本地化。
- 任务书漏列五个流式onFailure，实际默认聊天经此路径：非2xx也转为结构化HTTP错误，原解析/网络失败挂cause；
  没有HTTP响应或正常event-stream的网络失败保留原类型；2xx却明确声明HTML/JSON而非SSE的响应转为结构化解析异常，
  保留原诊断及cause，覆盖E3默认流式路径。Google失败body只读取一次。增加五协议实际入口的离线拦截夹具。
- 任务书示例称HEARTBEAT_OK为“四个字符”不准确，未照抄；也没有宣称中文标记会作为报告，因为新白名单正会静默它。
  Video单V11“401旧文案保持不变”与同包错误本地化冲突，按后者验收：HTTP状态/原body保留，用户说明使用对应语言。
  原nullable body插值为null时会显示字符串null；使用toString而非orEmpty保留该边界的原诊断。
- 三语各新增13key；原身份图标、F003、F004、F007、F009、F012、F013与DB33保留。
  Kai/PalmClaw阅读范围见参考台账。没有真实账号、设备、模拟器或ADB操作，以下设备项均待维护者验收。

## D1–D7 心跳验收

| 编号 | 离线证据范围 | 设备/真实模型结果 |
| --- | --- | --- |
| D1 | 无属于整条白名单；Runtime保留不落对话及OK活动分支 | **待实机**：心跳回无，不新增对话消息，活动OK |
| D2 | 原HEARTBEAT_OK及大小写/空白/标点回归单测 | 待实机：原标记正常静默 |
| D3 | 实际报告、带转折及多行文本均不静默；通知开关路径未改 | 待实机：报告保存及通知开关效果 |
| D4 | heartbeatSilentWhenNoContent=false仍调用persistPulseTurn，静态复核 | 待实机：关闭静默后保存无 |
| D5 | heartbeat守卫保持，任务轮不受白名单影响 | **待实机**：计划任务回无正常保存 |
| D6 | PulseScheduleTest新增中英文/标点/误吞保护用例并保留原调度测试 | 19项单测通过；设备交互仍待验 |
| D7 | Runtime记录静默OK活动路径保持 | **待实机**：连续三次不累积无，最近活动有三条OK |

## V1–V11 视频压缩验收

| 编号 | 离线证据范围 | 设备/真实模型结果 |
| --- | --- | --- |
| V1 | requestCompressionSummary唯一入口先剔视频再buildStep | **待实机**：原报422对话压缩成功 |
| V2 | 实际ChatCompletions序列化夹具不含video_url/base64视频，仍保留图片 | **待实机**：原约16MB请求实际缩小；不将离线小夹具当16MB实测 |
| V3 | 纯函数测试原消息/parts/视频URL未变，无数据库写入 | **待实机**：压缩后原视频仍可播放 |
| V4 | 原正常聊天流式/非流式video_url序列化回归测试保留 | 待实机：正常发视频 |
| V5 | 群组压缩仍仅用文本prompt，未加视频过滤 | 待实机：群组压缩 |
| V6 | 手动、自动与中途压缩复用原generateCompressionSummary链路 | 待实机：自动触发压缩 |
| V7 | 新VideoInputTest六项，不可变历史/仅视频/自定义占位/混合附件 | 6项单测及实际请求序列化回归通过；真实模型仍待验 |
| V8 | 无视频消息保持同一实例 | 待实机：普通文本压缩 |
| V9 | ErrorText复用视频三类文案；原生成专用错误卡片不动 | **待实机**：关闭视频能力后发视频显示中文 |
| V10 | 真实请求序列化夹具使用无VIDEO能力模型，去视频后可构造请求 | 待实机：关闭视频能力后含视频历史仍可压缩 |
| V11 | 状态和原body保留，401用户文案按本包第三单本地化；未知异常不泛化 | 待实机：其他错误按新本地化预期展示 |

## E1–E10 报错本地化验收

| 编号 | 离线证据范围 | 设备/真实模型结果 |
| --- | --- | --- |
| E1 | 401实际五协议流式/非流式离线入口及中文资源映射 | **待实机**：错误Key卡片，截图/实际完整文本尚无 |
| E2 | UnknownHost/Connect/NoRoute等类型映射已有网络文案 | **待实机**：飞行模式错误卡片 |
| E3 | SerializationException及ProviderResponseException映射，五协议实际流式入口200 HTML夹具 | 待实机：200格式不兼容端点 |
| E4 | Factory无模型/供应商四处使用资源 | 待实机：新装未选模型 |
| E5 | 压缩剔视频及通用视频错误映射已接入 | 待实机：关闭视频能力仍压缩成功；未知异常仍原样显示，不承诺任意错误都必为中文 |
| E6 | 500/503/599参数化资源及503实际协议失败入口 | 待实机：服务端错误卡片 |
| E7 | EOF/Socket/IOException含子类映射连接中断 | **待实机**：生成途中关闭代理 |
| E8 | IllegalStateException/未知IllegalArgumentException保持null映射，不按字符串猜类型 | 待实机：原文保留 |
| E9 | 测试直接读取三份真实XML，检查英/繁401及简中各类错误 | 待实机：英文/繁体界面卡片 |
| E10 | ErrorTextTest九项及结构化异常/真实协议入口夹具 | 9项映射测试及五协议离线入口测试通过；设备卡片仍待验 |

E1离线预期文本（不是设备截图）：API 密钥无效或已过期，请检查供应商设置，空行后原响应body，最长400字符。

## 上游升级需重套

1. ai/provider/VideoInput.kt：扩展及占位，保留原校验；app/data/ai/ChatRequestFactory.kt：仅压缩输入过滤及四处资源。
2. ai/provider/ProviderHttpException.kt：结构化异常和流式HTTP转换；providers/openai/ChatCompletionsAPI.kt、ResponseAPI.kt、
   providers/claude/ClaudeProvider.kt、providers/google/GoogleProvider.kt、InteractionsAPI.kt：五处HTTP、响应缺message和五个流式出口。
3. app/utils/ErrorText.kt与service/ChatService.kt的addError映射；ui/components/ui/ErrorCard.kt两处未知兜底；三份strings各13key。
4. service/pulse/PulseSchedule.kt整条白名单、data/datastore/PulseConfig.kt默认指令提醒，以及本批六份测试与参考台账。

回滚以git diff 11441abe -- <对应路径>逐块反向撤销；不要整文件回退丢弃原DeepSeek推理、能力拒绝、压缩、视频或其他fork功能。
版本递增单独评估；没有数据迁移。实际完整改动列表将在本目录SOURCE-AUDIT.json记录。

## 本批构建、签名与归档

源码范围审计已通过：相对205清单1815项，22项修改、1793项保持，新增7项（含仅用于JVM的Log适配）；无删除、无额外语言资源或索引/HEAD变化。
三份语言文件各新增13key且原文案逐字保持；原视频校验/异常、生成视频卡片/能力拒绝分支、PulseRuntime与版本文件改动范围均已核对。

- 成功命令：`./gradlew test :app:assembleGithubRelease --no-daemon --no-parallel --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx2560m -Dfile.encoding=UTF-8' --offline --console=plain`。2026-11-17完成，用时12分40秒，877项任务中59项执行、818项UP-TO-DATE；Release R8、资源优化、lintVital、签名与打包成功。成功证据为本目录build-combined.log，没有构建Debug APK。
- 测试报告按任务图去重为258个套件、1747项：1746通过、0失败、0错误、1项既有PdfCorpusTest.extractCorpus缺语料跳过。AI的415项及App GitHub的943项已在本批此前调用执行通过；最终成功调用的测试任务均为UP-TO-DATE，其余未变模块也复用报告，不宣称1747项全部重新执行。Play重复报告不重复计数；本工程未注册Release变体单测，不能将Debug JVM单测称为Release设备测试。
- 本批相关六套件共52项通过：VideoInputTest 6、ProviderHttpExceptionTest 5、ProviderHttpFailuresTest 2、ChatCompletionsVideoTest 11、PulseScheduleTest 19、ErrorTextTest 9。协议入口夹具逐项覆盖五种协议的401/503及流式200 HTML；并非真实联网或模型验证。
- 三个APK均核验206/3.2.0-work.3、包名me.rerere.rikkahub.plus、应用名RikkaHub Work、非debuggable，沿用正式签名且v2校验通过；证书SHA256为671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b。各自ABI、仅三语、原21身份图标源文件及20张PNG像素、deepseek_sha3.wasm与ZIP CRC通过。
- 精简源码保留1822项、2个符号链接、Unix权限及根DELIVERY；排除.git、APK、构建缓存、本机配置、签名私钥、artifacts和两份参考仓库。SOURCE-MANIFEST.json与SOURCE-VERIFICATION.json记录逐文件内容/哈希、文件集合、权限、UTF-8名称及CRC核验；最终ZIP哈希放包外SHA256SUMS，避免自引用。
- 第一轮协议入口测试遇到Android Log未mock，新增ai/src/test/java/android/util/Log.java只适配JVM日志；第二轮修正夹具响应体为现有stringSafe支持的真实网络类型。生产日志、stringSafe、重试策略与断言未为此放宽。后续内存压力、旧进程锁与中断尝试均分别保留build-attempt1至6日志；它们不替代最终成功日志，也不作为整版通过证据。
- 自动验证明细为本目录TEST-RESULTS.json、APK-VERIFICATION.json与SOURCE-AUDIT.json。未操作设备、模拟器、ADB、真实账号或远端；上述D/V/E实际交互与模型验收仍待维护者完成。旧批次记录如下，仅作历史。

| 交付文件 | 字节数 | MiB |
| --- | ---: | ---: |
| RikkaHub.Work-3.2.0-work.3-arm64-v8a-release.apk | 16466553 | 15.70 |
| RikkaHub.Work-3.2.0-work.3-x86_64-release.apk | 16565478 | 15.80 |
| RikkaHub.Work-3.2.0-work.3-universal-release.apk | 19297657 | 18.40 |
| RikkaHub.Work-3.2.0-work.3-source.zip | 见SOURCE-VERIFICATION.json | 见SOURCE-VERIFICATION.json |

# 源码同步准备 — 3.2.0-work.2 / 205

日期：2026-11-13。维护者要求“整理源码，给我命令，我来推送”。本轮准备本地提交与精简源码ZIP，
推送和标签对齐由维护者执行。下面“不commit”等内容是构建阶段的历史记录，当前状态以本节与git log为准。

- 范围：3.2.0-work.2/205的心跳提示词读取闭环、原有写入/权限保护及配套测试/台账。产品与heartbeat-prompt-read-release-20261113的已验证205清单一致，仅AGENTS、FEATURES、DELIVERY三份文档补记同步状态；无新功能改动，不重跑已通过且无变化的产品测试，也不重新构建APK。
- 当前本地分支merge/upstream-3.2.0；基线/父提交为152ad5208706f1261de5a688d40b5d8b848b069a（3.2.0-work.1/204）。本地新提交的实际SHA记录于交付目录SYNC-VERIFICATION.json，完整上游3.2.0与此前fork功能保留。
- 本轮fetch并读取远端：origin/master=152ad5208706f1261de5a688d40b5d8b848b069a，远端3.2.0-work.2标签也仍指向它，其源码实际为204/work.1。须先普通推送新HEAD到master，再用限定旧值的force-with-lease对齐这个标签；不强推master，不改work.1。
- origin为git@github.com:ziwa0213-crypto/rikkahub_work.git，继续使用SSH。提供的命令以&&连接，master失败时不会继续更新标签；标签旧值变化时租约检查会拒绝覆盖，需重新核对。
- 新源码与记录：artifacts/3.2.0-work.2/source-push-preparation-20261113/。ZIP保留1815项源码、2个软链、Unix权限与根DELIVERY，不含.git、APK、构建缓存、本机配置、签名私钥、artifacts或两份参考仓库。源清单、CRC、逐文件内容和提交树一致性记录随包外提供。
- 已有三个205签名Release保持在heartbeat-prompt-read-release-20261113目录，本轮校验其SHA256未变，不重复构建。原测试统计1719通过/1项既有PDF跳过（App930项本轮执行、其他模块复用报告），C1/C4及其他设备/真实模型验收仍待维护者完成。
- 本轮没有push、修改远端标签、发布Release、操作设备或调用真实模型；旧交付保留。最终ZIP SHA256放包外SHA256SUMS，避免自引用。

# 心跳提示词读取闭环 — 3.2.0-work.2 / 205（本地 Release）

日期：2026-11-13。依据本轮附带的《00-先读我（提示词读取闭环）》及《10-修正单·心跳提示词读取闭环》。
交付目录：`artifacts/3.2.0-work.2/heartbeat-prompt-read-release-20261113/`。
增量基线：`152ad5208706f1261de5a688d40b5d8b848b069a`，已包含上游3.2.0及此前全部fork修改。
本轮只读核实远端master、3.2.0-work.1标签均为此提交，work.1/204为正式Release；
附件要求下版code至少205，依仓库同上游版本内work序号递增规则采用work.2/205。数据库仍33。
本轮不commit/tag/push/发布，不操作设备、模拟器或真实模型账号。下方旧批次状态保留作历史。

## 改动范围与边界

两个功能文件：

1. `app/src/main/java/me/rerere/rikkahub/data/ai/tools/pulse/PulsePromptTool.kt`
2. `app/src/main/java/me/rerere/rikkahub/data/ai/tools/pulse/PulseToolPolicy.kt`

另更新`app/build.gradle.kts`版本、两份现有工具测试、AGENTS/CHANGES/FEATURES/本文件及参考台账。
不创建新工具、设置、语言key或数据字段。未提供独立02-AGENTS附件，本轮保留根文件并追加当前规则。

- read必须显式传，参数prompt不再schema必填；省略action仍按edit，写逻辑保持原样，包括trim、非空/4000字符和主/子开关校验。
- 读取当前SettingsStore的heartbeatPrompt，空白时回落DEFAULT_HEARTBEAT_PROMPT；返回content/is_default/length。显式存入默认全文时is_default=false，只有空白存储才标默认回落。
- 不使用当轮synthetic user、任务/监视器/SMS描述或人格推测原文。原样返回全文，包括空格换行与超过工具写上限的手动保存内容；不写设置、不新增日志或已读状态。
- 源码实际还有写操作回执层，附件示例未涉及：只给本工具read增加只读例外，避免读取被当成写入去重或回放。edit/rewrite与其他写工具原守卫不变，schedule_task list原例外保持。
- read执行时按与文本同一份最新pulse快照重查主开关，使已注册工具也不能越过随后关闭的开关。Factory和needsApproval不改。允许重写只控制rewrite，read/edit不依赖子开关。
- 写前read是模型工具描述要求，不是强制已读状态或事务；不宣称能保证模型原文复述或一定遵循工具调用顺序。
- UI确认保存、人格、默认提示词、设置/DB33、供应商限制、审批、通知/调度/保活、原21身份图标和仅三语资源均保持基线。

## C1–C8逐条验收记录

下表自动化证据只覆盖代码和工具执行契约；真实模型对话、页面操作和覆盖安装均未执行。
**C1和C4尚未完成实机验收，不能标记为通过。**

| 编号 | 自动化验证范围 | 设备/真实模型结果 |
| --- | --- | --- |
| C1 | 真实SettingsStore工具read返回完整原文、长度和默认标志，不读人格 | **待实机**：在脉搏问当前心跳提示词，核对先read再原文复述 |
| C2 | 同一工具实例跨多次SettingsStore更新立即读取新值，不使用旧注册快照 | 待实机：手动编辑并确认后再问 |
| C3 | 空串/全空白回落默认、is_default=true；显式存入默认全文则false；读取不写回 | 待实机：清空确认并询问，核对模型说明默认回落 |
| C4 | 取值只来自SettingsStore；Policy在PulseToolContext下重复read不进入写回执 | **待实机**：计划任务轮询问，核对工具结果是心跳原文而非任务内容 |
| C5 | read→默认edit→read→授权rewrite→read回环、人格不变，写入仍校验长度/非空 | 待实机：核对模型先read后写并能再读到新值 |
| C6 | Policy主开关关闭移除整个工具；关闭后旧工具实例执行read/写均拒绝 | 待实机：关闭主开关后检查实际发给模型的工具清单 |
| C7 | 子开关关闭时read/edit可用，rewrite拒绝；主开关不能被子开关越过 | 待实机：分别调用三个动作 |
| C8 | 省略action并传prompt仍按edit；省略prompt时写入拒绝；其他工具默认动作未放宽 | 待实机：检查省略action调用结果 |

## 构建与归档

- 实际成功命令：`./gradlew test :app:assembleGithubRelease --no-daemon --no-parallel --offline --console=plain`，用时3分21秒，876项Gradle任务中60项执行、816项UP-TO-DATE。主代码编译、Release R8、资源优化、lintVital及打包通过，没有生成Debug APK。
- 首次调用复用的旧Gradle守护进程无法启动ninja子进程，报`Failed to exec spawn helper`；ninja单独运行正常。换用新的一次性构建进程后成功，未修改产品或工具链规避测试，首轮失败日志保留为`build-attempt1.log`，成功日志为`build-combined.log`。
- 254个唯一套件1720项：1719通过、0失败、0错误、1项既有PdfCorpusTest.extractCorpus缺语料跳过。本轮App GitHub的930项重新执行，Play变体亦成功但不重复计数；其余模块UP-TO-DATE报告复用，不宣称全部重跑。新增3个测试，两份工具/策略套件合计14项均本轮通过，包含真实PreferenceDataStore工具执行与回执守卫检查。
- 源码审计以已发布204清单核对1815项：本轮只改列明的10个文件，其余1805项保持一致。原写分支逐字相同，Policy仅增加read动作/只读例外，版本文件只有204→205及work.1→work.2；Factory、PulseConfig、编辑UI、PulseTurnInput、PulseRuntime及其余功能不变。Git HEAD和index保持基线，无自动提交。
- 三个APK均为205/3.2.0-work.2，包名`me.rerere.rikkahub.plus`、应用名`RikkaHub Work`，非debuggable；沿用原正式签名，v2校验通过，证书SHA256为`671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b`。ABI、仅三语、原21身份图标源文件/20张PNG像素、deepseek_sha3.wasm和ZIP CRC均已核验。
- 精简源码ZIP保留1815项源码、2个符号链接、Unix权限及根DELIVERY；不含APK、.git、构建缓存、本机配置、签名私钥或两份参考仓库。文件清单/逐文件哈希、压缩包CRC与本地一致性分别记录于SOURCE-MANIFEST.json、SOURCE-VERIFICATION.json；最终ZIP哈希放包外SHA256SUMS，避免归档自引用。旧交付保持。
- 自动验证记录为本目录TEST-RESULTS.json、SOURCE-AUDIT.json和APK-VERIFICATION.json。Kai/PalmClaw参考边界记docs/f015-keepalive-reference-review.md。**未操作设备/真实模型，C1/C4必须实机验证的要求尚未完成**；其余交互/模型验收也保留待验，不把工具单测称为整版验收完成。

| 交付文件 | 字节数 | MiB |
| --- | ---: | ---: |
| RikkaHub.Work-3.2.0-work.2-arm64-v8a-release.apk | 16463737 | 15.70 |
| RikkaHub.Work-3.2.0-work.2-x86_64-release.apk | 16562666 | 15.80 |
| RikkaHub.Work-3.2.0-work.2-universal-release.apk | 19294845 | 18.40 |
| RikkaHub.Work-3.2.0-work.2-source.zip | 见SOURCE-VERIFICATION.json | 约24.6 |

# 源码同步准备 — 3.2.0-work.1 / 204

日期：2026-11-13，Asia/Shanghai。维护者要求“整理源码，给我命令，我来推送”。
本轮整理本地合并提交并提供推送命令，不自动推送、改远端标签或发布Release。
下面“未提交”等描述是当时构建阶段的历史记录；当前同步状态以本节和本地git log为准。

- 范围：完整上游3.2.0合并、脉搏八项、三处补充修订及204提示词编辑修复，全部保留；版本3.2.0-work.1/204，数据库33。
- 产品代码与pulse-prompt-editor-release-20261113的204源码清单逐文件一致，仅AGENTS、FEATURES、DELIVERY三份文档补记同步状态。本轮未修改功能或重新构建；此前测试、签名APK校验及设备待验记录继续适用。
- 在merge/upstream-3.2.0上完成本地合并提交，父提交为原fork 8faae5d67042527355aebcfd1c62a0ae1c7f937d和上游e033a47d395fe57d16db2144069c3abdb7117547。实际新提交SHA在交付目录SYNC-VERIFICATION.json记录，避免在提交自身内容中自引用。
- 准备时已读取远端：master=8faae5d67042527355aebcfd1c62a0ae1c7f937d，3.2.0-work.1标签也指向该旧提交，版本实际为3.1.2-work.1/202。先普通推送master，再用针对该标签、校验明确旧值的force-with-lease对齐；不强推master。
- origin为git@github.com:ziwa0213-crypto/rikkahub_work.git，使用已有SSH访问方式。推送仍由维护者执行，本轮未联网调用模型或操作设备。
- 新源码ZIP与核验材料位于artifacts/3.2.0-work.1/source-push-preparation-20261113/。保留1815项源码、2个符号链接、根DELIVERY及文件权限，排除.git、APK、构建缓存、本机配置、签名私钥和参考仓库。旧APK和旧源码ZIP保持不变。
- 不为源码同步重复构建APK；已验证的204三个Release仍在pulse-prompt-editor-release-20261113目录。最终SHA、源码文件及提交树一致性详见新目录审计记录。

# 脉搏提示词编辑体验 — 3.2.0-work.1 / 204（本地 Release）

日期：2026-11-13，Asia/Shanghai。依据《08-修正单·脉搏提示词编辑体验（20261113）》实施。
交付目录：`artifacts/3.2.0-work.1/pulse-prompt-editor-release-20261113/`。
增量基线为上一批203的`SOURCE-MANIFEST.json`及源码ZIP；本批仍包含此前合并的完整上游3.2.0与脉搏八项。
当前分支`merge/upstream-3.2.0`，HEAD=`8faae5d6`，MERGE_HEAD=`e033a47d395fe57d16db2144069c3abdb7117547`。
保留未提交合并，没有commit/tag/push/发布。以下203及更早章节均为历史交付，不能替代本批204的验证记录。

## 本次改动

- 人格、心跳提示词共用本地草稿编辑器，显示原始存储值；清空保持为空，可保存空文本。输入不再逐字符写DataStore，确认后才更新对应字段。
- 恢复默认只更改草稿，也需确认；左侧恢复默认、右侧确认，无改动时确认禁用。退出页面未保存的修改放弃，字数显示草稿长度。
- 保留运行时`heartbeatPrompt.ifBlank { DEFAULT_HEARTBEAT_PROMPT }`、PulseConfig字段和默认值、两份默认提示词及AI的update_heartbeat_prompt工具。接受外部stored变动时remember(stored)重置草稿，不新增合并逻辑。
- 版本名保持3.2.0-work.1，versionCode203→204；数据库仍33，只新增一个确认按钮key的三语值。

产品改动实际为4个文件（任务单写“3个”但列的是1+3）：

1. `app/src/main/java/me/rerere/rikkahub/ui/pages/pulse/PulseSettingsPage.kt`
2. `app/src/main/res/values/strings.xml`
3. `app/src/main/res/values-zh/strings.xml`
4. `app/src/main/res/values-zh-rTW/strings.xml`

另改版本文件`app/build.gradle.kts`，并同步AGENTS、CHANGES、FEATURES、本文件与参考台账。
增量文件清单与保护项哈希核对记录在`SOURCE-AUDIT.json`；所有非本批文件按203源码清单核验。

## A1–A10逐条结果

下表“已落实”仅指源码检查及编译证据，不代表已经在手机上操作。没有设备或真实模型调用。

| 编号 | 已落实的源码行为及证据 | 设备/真实模型待验 |
| --- | --- | --- |
| A1 | 心跳调用传原始config.heartbeatPrompt，草稿由stored初始化，不加UI默认替换 | 进入心跳Tab核对当前内容 |
| A2 | onValueChange只设置draft，空字符串无回填分支 | 全选删除后输入框持续为空 |
| A3 | 文本框直接接收新draft，不拼接默认提示词 | 删空后用实际输入法粘贴，确认只有粘贴内容 |
| A4 | 输入不调用update；仅确认的onSave更新设置，remember不持久化草稿 | 修改后不确认退出，再进仍为旧值 |
| A5 | 确认将完整draft交给已有SettingsStore.update，只改对应字段 | 确认后退出/重进/重启核对持久化 |
| A6 | enabled=dirty，dirty严格比较draft与stored | 未改动时按钮置灰，改动后可点 |
| A7 | 恢复默认回调只有draft=defaultValue，没有设置写入 | 恢复后不确认退出仍是旧值，确认后才生效 |
| A8 | 人格与心跳复用同一PulsePrompt，均传原始存储值与各自默认值 | 人格页重复A1–A7 |
| A9 | PulseRuntime的默认心跳兜底与PulseConfig逐字匹配203基线，没有改变运行时路径 | 清空并确认后真实触发一次心跳，核对仍带默认指令 |
| A10 | AI工具未改，页面仍收集最新settings；stored变化时重置草稿，重进读取最新值 | AI成功修改后重新进入设置核对内容 |

**未做真机粘贴验证**；未操作剪贴板、输入法、设备/模拟器、真实账号或真实心跳。
本轮没有新增单测，不以静态检查代替A1–A10的实际交互与模型验证。

## 构建与归档

- 验证命令：`./gradlew test :app:assembleGithubRelease --no-parallel --offline --console=plain`。
- 本次构建成功，用时4分21秒，876项Gradle任务中86项执行、790项UP-TO-DATE；Release Kotlin、R8、资源优化、lintVital与打包完成。日志为本目录`build-combined.log`，没有复用203的构建结论。
- 现有测试任务图254套件1717项：1716通过、0失败、0错误、1项既有PdfCorpusTest.extractCorpus缺语料跳过。本轮App GitHub的927项实际执行，Play变体也执行成功但重复报告不重复计数；其他模块UP-TO-DATE报告复用，未宣称全部重新执行。明细见`TEST-RESULTS.json`。
- 源码静态审计44项通过：相对203只有前述10文件变化，其余1805个源码文件保持一致；三语各2557个唯一且相同key，运行时、配置、两份默认提示词、AI工具与数据库逐字保持基线。审计不代替设备交互测试。
- 交付三个签名GitHub Release APK：ARM64、x86_64、Universal。包名me.rerere.rikkahub.plus、应用名RikkaHub Work，沿用既有正式签名，不交Debug APK。
- 精简源码ZIP根含本文件，排除.git、APK、build/缓存、本机配置、私钥及参考仓库；保存文件清单、符号链接、权限和CRC校验。APK与ZIP的体积、版本、签名、ABI及SHA256写入本目录验证JSON和SHA256SUMS。
- Kai SoulEditor与PalmClaw HeartbeatEditorSheet在改前已阅读，具体采用与未采用范围记`docs/f015-keepalive-reference-review.md`；本机参考仓库继续保留。

| 交付APK | 字节数 | MiB |
| --- | ---: | ---: |
| RikkaHub.Work-3.2.0-work.1-arm64-v8a-release.apk | 16463641 | 15.70 |
| RikkaHub.Work-3.2.0-work.1-x86_64-release.apk | 16562566 | 15.80 |
| RikkaHub.Work-3.2.0-work.1-universal-release.apk | 19294749 | 18.40 |

三个APK均核验204/3.2.0-work.1、原包名/应用名、非debuggable、签名v2、各自ABI、三语、原图标像素、WASM和ZIP CRC。
证书SHA256为`671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b`。
归档包含1815项源码（含2个符号链接及根DELIVERY），最终ZIP SHA见包外`SHA256SUMS`，避免归档自引用。

# 合并上游3.2.0与脉搏八项修复 — 3.2.0-work.1 / 203（本地 Release）

日期：2026-11-13，Asia/Shanghai。依据维护者提供的20261112任务包和20261113三处修订；后者优先。
交付目录：`artifacts/3.2.0-work.1/upstream-pulse-release-20261113/`。
基线：`8faae5d6`；上游3.2.0：`e033a47d395fe57d16db2144069c3abdb7117547`。
分支：`merge/upstream-3.2.0`。冲突已消解并登记，保留未提交合并状态；本批没有commit/tag/push/发布。
下面旧批次章节作为历史记录保留，其中旧版本、旧HEAD和旧交付要求不代表本批。

## 交付内容

- `RikkaHub.Work-3.2.0-work.1-arm64-v8a-release.apk`
- `RikkaHub.Work-3.2.0-work.1-x86_64-release.apk`
- `RikkaHub.Work-3.2.0-work.1-universal-release.apk`
- `RikkaHub.Work-3.2.0-work.1-source.zip`：工作区实际源码，含本文件，不含.git、APK、构建缓存、签名私钥、本机配置或参考仓库。
- `APK-VERIFICATION.json`、`TEST-RESULTS.json`、`SOURCE-AUDIT.json`、`SOURCE-MANIFEST.json`、`SOURCE-VERIFICATION.json`、`SHA256SUMS`。
- `CHANGED-FILES.txt`记录本批相对基线各文件增删行数；`SOURCE-CHANGES.patch`供审核，完整源码ZIP为主要交付。
- `build-stage1.log`、`build-combined.log`和浏览器离线脚本验收记录；完整用户可读更新说明在根`CHANGES.md`首节。

三个APK沿用原正式证书，包名`me.rerere.rikkahub.plus`、应用名RikkaHub Work，非debuggable。
证书SHA256：`671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b`。
Release优化已开启，实际任务包含R8和资源优化。Universal仅包含本项目支持的ARM64/x86_64，不含ARM32。
体积与文件SHA以本目录验证JSON和SHA256SUMS为准，不手填尚未产生的归档hash。

## 顺序与验证结果

1. 在新分支合并上游3.2.0，解决15个内容冲突与5个不支持语言的删除冲突；保留上游其它自动合并结果。
2. 第一阶段先运行`:app:compileGithubReleaseKotlin test --no-parallel --offline`，编译及完整测试成功后才开始脉搏八项修复；没有构建中间测试APK。
3. 第二阶段编译/专项回归通过后，运行`./gradlew test :app:assembleGithubRelease --no-parallel --offline --console=plain`，全量测试、lintVital、Release打包成功。
4. 最后静态复核清理两处整点兼容入口对旧字段的引用，保持对外小时参数语义但立即转换分钟；重新构建期间发现既有浏览器测试的并发夹具写入问题，已修复为临时文件加原子替换，并再次完整验证成功。
5. 最终任务图254套件、1717项，1716通过、0失败、0错误，1项既有`PdfCorpusTest.extractCorpus`缺语料跳过。GitHub App测试927项本轮执行，Play测试也执行成功但不重复计数；其他模块UP-TO-DATE报告复用，未宣称全部重新执行。
6. 三个APK逐一校验签名、版本、包名、Release标志、ABI、语言、原图标像素、WASM内容和ZIP CRC；源码ZIP逐文件校验内容、权限、符号链接、UTF-8文件名、清单与CRC。

本轮没有连接设备/模拟器、没有登录真实模型账号、没有触发真实心跳/立即运行、短信或日历。
未跑真机心跳间隔验证，也没有用手动立即运行冒充长时间验证。APK/UI/通知行为待维护者安装验收。

## 上游合并接缝与验收

| 项目 | 已落实及自动证据 | 尚需实机/真实模型验证 |
| --- | --- | --- |
| 上游构建/测试A1–A4 | Release编译、完整test与最终三个APK成功；203/3.2.0-work.1，源码仅三语 | 安装/启动 |
| F007与新推理格式 | 保留DeepSeek强制reasoning_content与上游OpenRouter/Mistral回传；Claude/Mistral及视频回归通过 | 真实供应商多轮请求 |
| F009/F004/F012/F013 | 输出防护、工具能力过滤、原审批、群组/浏览器接线保留；相关JVM测试通过 | 真实对话/群组/浏览器/审批 |
| 自动压缩C1–C3 | 上游阈值默认关闭、80%、5%步进；保留中途压缩和先压缩再inbox。群组按实际模型/成员/记忆桶，脉搏接受并保存后复用同一入口 | 三模式阈值触发、真实usage、检查点与排队行为 |
| 上下文token来源 | AgentLoop保留最后一次usage；普通/群组共用collector，临时脉搏保留Finished消息后按ID保存；getChatModelOf模式解析回归通过 | 供应商实际是否返回usage；无usage时不会猜测token |
| Phone Use C4–C5 | 聊天助手页保留权限引导；群组/脉搏隐藏且Factory双供应商类型过滤；PhoneUsePolicyTest/上游工具回归通过 | 无障碍授权、操作手机及不同ROM表现 |
| 模型刷新/快捷消息/地图C6–C8 | 上游源码完整合并，Release编译通过；快捷消息短按追加换行、长按替换 | 实际点击、长对话滑动 |
| 老数据D1–D2 | DB33无迁移，Settings原68字段与PulseConfig原52字段名称/类型保留，PulseTask逐字未改 | 使用原数据覆盖安装 |

自动脉搏的回复最初只在临时历史中；因此它的完成回调不压缩尚未保存的旧历史，而是在persistPulseTurn成功保存后
调用同一个launchAutoCompressIfNeeded。静默未保存结果不触发压缩，不新建独立脉搏压缩器或消息条数阈值。
群组现有专属历史装配/成员提示保留，上游压缩请求追加当前组员和grp记忆作用域，避免误用当前普通助手配置。

## 脉搏八项逐条验收

| 修正 | 落点和自动证据 | 设备待验步骤 |
| --- | --- | --- |
| 1 检查点导致永久暂缓 | PulseSchedule取最后一个非checkpoint消息；PulseScheduleTest覆盖仅检查点、回复后检查点、真实user尾部/审批；暂缓文案区分待回复/待审批 | 保留0/8条分别压缩，之后心跳及短信轮次仍可执行；手动聊天回归 |
| 2 schedule_task CRUD | PulseTaskRepository最新SettingsStore.update部分更新，工具默认create且追加list/update/delete，list提示词最多200字符；时间改动清lastSlot，限额只约束create。测试覆盖CRUD、历史/时区保留、并发字段保留和旧用法 | AI列出ID、改时间/内容、暂停/恢复/删除；运行中删除/改期后不复活/覆盖 |
| 3 分钟时段输入 | 保留旧小时字段，新增默认-1分钟字段；有效分钟访问器用于运行时/状态/下次心跳。测试覆盖旧JSON、输入解析、分钟边界、全天及跨午夜 | 输入08:30/22:45、简写8/830；非法25:00不覆盖原值，失焦/Done保存，重进/重启保持 |
| 4 任务只读详情 | 单条AlertDialog、选择复制完整prompt、滚动Column；包含状态/类型/执行时间/最近结果，旧数据空prompt显示“—”，不含输入框/保存，原开关整行切换保留 | 长提示词滚动、复制、关闭；查看不同任务不串内容 |
| 5 网页版不能自动执行 | PulseProviderPolicy检查模型归属和覆盖类型；选择器过滤、归一化只清脉搏显式选择。runDue和请求前兜底；webBlockedUntil按普通心跳间隔记录SKIPPED，不发请求/短重试/消费任务。测试覆盖API名称不误伤、全局选择/供应商不变 | 全局设网页版且脉搏继承，见警示和SKIPPED；切回API后正常；无联网请求/日志风暴 |
| 6 脉搏不显示悬浮窗 | getConversationJobsForFloating专用流同时观察任务和会话类型；原getConversationJobs保持全量。测试覆盖混合任务/运行中类型变化 | 脉搏后台无悬浮窗，聊天/群组正常；混合运行只显示正常会话 |
| 7 调整/重写心跳提示词 | PulsePromptTool仅写heartbeatPrompt，空或>4000拒绝；最新开关二次校验，只豁免本工具二次审批，回执守卫保留。PulsePromptToolTest/Policy/Config覆盖动作默认和开关存储 | 开关切换、执行修改后下次心跳采用；人格与默认恢复保留 |
| 8 静默运行通知 | 新pulse_run独立MIN渠道且静默；WakeLock续租不变。普通任务优先，ChatNotificationManager更新/结束两入口脉搏提前return；报告通知保持 | 不出现重复生成进度/完成/失败通知；原聊天/群组通知与脉搏报告正常；检查ROM最低通知展示 |

### 20261113三处修订

- 不恢复生成看门狗、空闲超时、时长上限或休眠取消。静态扫描未新增相关生成计时逻辑；原WakeLock租约更新实现逐字保留。Android系统自身的服务超时回调属于既有系统生命周期处理，不是本轮加入的生成时限。
- H10：关闭“调整”保留“重写”的存储值、隐藏其UI、运行时禁用动作；重新打开恢复原值。PulseConfigTest覆盖序列化/归一化，PulsePromptToolTest覆盖执行时读最新开关。
- H9：仅update_heartbeat_prompt省略action默认edit；clipboard_tool/memory_tool仍需显式action，PulseToolPolicyTest覆盖正反例，未扩大默认行为。

## 自查、边界与参考

- SOURCE-AUDIT记录47项源码契约检查：三语各2556个唯一且相同key、数据库/版本、既有字段兼容、检查点USER角色、无冲突标记、F003更新地址、api.rikka-ai.com、F007、审批与通知/过滤接线。grep字符串只能证明接线存在，不能替代运行验证。
- 按任务书grep口径：pulse_turn_skipped_approval三语各1处；pulse_task_view/prompt_label/state_*三语各4行；有效分钟访问器引用14行；PulseConfig之外旧activeHoursStart/End引用0行；自改提示词开关声明2行。有效分钟访问器沿用本批实现名effectiveStartMinute/effectiveEndMinute，对应任务书activeStartTotalMinutes/activeEndTotalMinutes。网页版判断集中为PulseProviderPolicy的类型检查，UI/归一化/运行时调用该策略，不能仅凭某个文件没有类型名断定缺少过滤。
- 源码原21份launcher身份资源与既有基线`643191229dcbeb4972e3772986818605633ace92`逐字一致；APK内PNG按像素匹配，关于页继续引用原图标。上游Phone Use等普通功能图标照常合并。
- Settings新增上游enableAutoCompress/autoCompressThreshold，PulseConfig新增4个有默认值字段；未删除或改类型，PulseTask未增加字段。仅支持values/values-zh/values-zh-rTW，i18n目标同步裁剪。
- BrowserContractTest原子发布离线夹具，修复GitHub/Play并行写同名文件的测试竞态；tools/verify-browser-js.mjs的8项DOM夹具验证通过。此为离线JavaScript语义校验，非真实WebView/站点测试。失败与最终成功构建日志均留在交付目录。
- 修改前已直接复核本机Kai固定ed6a3e0c48e10de9e2852e939fa4531975352bff的TaskScheduler/HeartbeatPromptBuilder与PalmClaw固定75a42aeda0e580ebad0de4a38c00c94146148366的HeartbeatService/CronTools。采用临时历史、最新存储部分更新与调度/通知分工，未复制源码，未把参考仓库打入ZIP；具体采用边界见docs/f015-keepalive-reference-review.md。
- 不宣称已验证实际账号/模型行为、覆盖安装、手机无障碍、ROM通知隐藏、后台6小时保活或真实心跳间隔；这些仍需维护者用三个Release包验收。

## 审核与回退

维护者尚未要求提交或推送。Git保留MERGE_HEAD并已登记解决冲突，可先审核暂存差异再自行提交合并。
源码ZIP是可审核快照，不含Git历史和本机签名私钥；复现签名构建需维护者自己的local.properties/正式密钥。
回退应以基线8faae5d6和本批差异逐块评估，不整文件覆盖共享ChatService/请求工厂，不为回退删除已持久化兼容字段。
历史产物保留，交付目录与旧3.1.2批次分离。没有上传GitHub或修改现有Release。

---

# F015 主界面用量与等待约束 — 3.1.2-work.1 / 202（本地 Release）

日期：2026-11-10，Asia/Shanghai。依据维护者提供的用量/等待补丁两文档。
维护者指定versionCode202，并明确授权补齐手动与自动脉搏的请求装配；覆盖附件R2禁止修改
ChatRequestFactory、R6不改版本号。沿用三个签名Release APK与源码ZIP交付要求，覆盖附件不附APK。
分支master、HEAD `18c372d58f77da7c8a1c1f59042993e01be2bd5b`；未暂存、提交、tag、推送或发布。

### 改动和范围差异

- 主界面PulseStatusRow传compact=true；只留下用量，保留点击进入Screen.PulseSettings(1)。
  抽屉不传compact，完整状态与待执行区不变，状态/sub/upcoming计算保留原处。
- 自动触发通过PulseTurnInput.systemInstruction带固定约束，历史和末尾synthetic user保留。
  原补丁忽略了手动发送走createContext空参数的路径，经明确授权在请求工厂给脉搏补默认约束。
  手动/重新生成/审批恢复复用该入口；普通聊天和群聊null参数不变，ChatService未修改。
- 保留“不要等待未来时刻”的整段system说明，最后一句按补丁R4改为明确允许短sleep轮询已运行进程。
  不改DEFAULT_HEARTBEAT_PROMPT、用户人格、workspace_shell描述、工具参数、调度器或生成时限。
  此为提示约束，未增加执行层命令硬拦截，不能保证模型一定遵从。
- 实施前已核对固定Kai/PalmClaw的心跳提示、调度及进程状态职责；路径/版本和采用范围见参考台账。

### A/B验收

| 项 | 源码/自动证据与设备边界 |
| --- | --- |
| A1 | 主界面compact=true只渲染用量；实际主界面截图未获取，待维护者安装。 |
| A2 | 原点击回调仍进入脉搏心跳设置；实际点击待验。 |
| A3 | ChatDrawer与前批逐字一致，默认compact=false保留完整状态、下次与任务；设备展示待验。 |
| A4 | 用量渲染在enableHeartbeat判断之外，关闭心跳仍保留；设备待验。 |
| B1 | 自动序列化夹具包含完整“## 时间与等待”，手动请求工厂使用相同固定约束；离线system片段随包，实际网络请求未采集。 |
| B2 | 提示引导schedule_task后结束，不用sleep等未来时刻；未调用真实模型，无真实工具调用记录，不用手造JSON代替。 |
| B3 | 普通聊天参数null及system字节一致回归；真实请求待维护者确认。 |
| B4 | 群聊参数null及system字节一致回归；真实请求待维护者确认。 |
| B5 | 明确允许短sleep轮询运行中进程，原shell工具/执行器逐字不变；真实模型与脚本轮询待验。 |

### 交付与验证

产物目录：`artifacts/3.1.2-work.1/f015-usage-wait-release-20261110/`。
三个APK为ARM64、x86_64、Universal，使用既有正式签名，应用名RikkaHub Work、包名me.rerere.rikkahub.plus。
源码ZIP根含最新本文件，排除APK、artifacts、缓存、凭据与参考仓库；旧交付不覆盖。
构建 `./gradlew test :app:assembleGithubRelease --offline --console=plain` 成功，2分43秒，
876项任务（62执行、814复用）。Release R8与Lint Vital通过；未运行完整Lint。
JVM247套件1670项：1669通过、1项既有PDF跳过、0失败/错误；普通模块复用本次任务图中的已有结果，
App GitHub测试重新执行，Play重复变体不重复计数。专项PulseTurnInputTest4项与
ChatRequestFactoryPulseInstructionTest6项均通过，本批新增4项路径/隔离回归。

| APK | bytes | SHA-256 |
| --- | ---: | --- |
| ARM64 | 16,388,984 | `3c015230afda2884b66a9276be8edf59f969a137397154da00890e5472d17239` |
| x86_64 | 16,487,913 | `bde997e6b4ce579f0abedab1965d43ea238be546f562330b805e2307c1b1880d` |
| Universal | 19,220,092 | `e40b341d729cc163e3f326b6d7752bf01b65d893515a3537b88e54e42da42526` |

三个APK非debuggable、versionCode202、包名/显示名/ABI、ZIP CRC、DeepSeek WASM、原20张图标PNG像素
均通过，21个身份图标源码匹配固定基准。沿用原V2证书，证书SHA-256
`671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b`。
仅输出Release APK，没有执行Debug APK打包任务；Debug JVM测试不等于生成Debug安装包。

对照前批1794项源码清单，仅12个既有文件变化，无新增源码文件；其余源码、测试与软链接保持，
包括ChatDrawer、ChatService、全局shell、任务调度、用户人格与默认心跳提示。
ChatPage仅compact参数、build.gradle.kts仅versionCode变化，请求工厂仅授权的脉搏默认值补齐；
逐字核验通过。三语各2506条且key集合相同、资源值未修改；HEAD和index未变，diff检查通过。
前批三个APK与源码SHA256SUMS复验通过，旧交付未覆盖。

`B1-OFFLINE-REQUEST.json` 是实际生产ChatCompletions序列化器离线夹具，
`B1-OFFLINE-SYSTEM.txt` 保留完整约束，二者明确标为离线而非网络抓包。
`ACCEPTANCE.json` 记录A1～A4/B1～B5边界；没有B2真实模型工具调用记录。
源码ZIP仍含1794项（1792普通文件、2软链接），打包脚本逐项校验集合、内容哈希、权限、UTF-8与CRC；
大小/哈希在外部SOURCE-VERIFICATION.json与SHA256SUMS记录，避免循环嵌入根文档。
其余证据为TEST-RESULTS.json、APK-VERIFICATION.json、SOURCE-AUDIT.json、SOURCE-MANIFEST.json和构建日志。
未运行Android设备/模拟器、真实账号或模型，未取得A1截图/B2真实工具日志；本地验证不能代替这些验收。

---

# 上一批：F015 脉搏做梦与循环任务说明 — 3.1.2-work.1 / 201（历史 Release）

交付日期：2026-11-10（Asia/Shanghai）。依据 `RikkaHub Work-F015脉搏做梦-20261110.zip`。
维护者已授权开始，并沿用“三个 Release APK 和源码”要求，覆盖附件“不附 APK”。版本、分支和
HEAD 保持 `3.1.2-work.1 / 201`、master、`18c372d58f77da7c8a1c1f59042993e01be2bd5b`。
未暂存、提交、打 tag、推送或发布，未操作 Android 设备、模拟器或真实模型账号。

### §1～§5 实施对照

| 章节 | 结果 |
| --- | --- |
| [x] §1 现状/复用 | 沿用现有 `MemoryRepository`、`Screen.PulseMemory` 和 `Screen.MemoryFile`，未重复添加入口。任务书的 `MemoryFileRepository` 在本项目不存在。 |
| [x] §2.1 开关 | `PulseConfig.autoConsolidateMemory=false` 持久化并映射到 facade；只改脉搏自己的字段，未放入助手库。 |
| [x] §2.2 共享组件 | `AssistantMemoryPage.MemoryConsolidationSection` 仅 private→internal，函数体与前批源码逐字一致；已登记 FEATURES 上游重套清单。 |
| [x] §2.3 界面 | 记忆/做梦分段页签；文件列表、空态和新建/编辑路由保留，新建只在记忆页；做梦页可滚动，复用状态/立即整理/重试和独立自动开关，最新状态更新只改 pulse。`organize` 非 suspend，直接调用。 |
| [x] §3 三语 | 仅新增四个指定 key，英文、简中、繁中各一份；无其他语言和依赖。 |
| [x] §4 循环后端 | PulseTask、PulseCron、PulseTaskRepository、PulseRuntime 和 PulseSettingsPage 与前批相同，原 once/cron/heartbeat 行为不动。 |
| [x] §5 模型说明 | PulseTaskTool 仅替换 description，补五字段cron及每日/工作日/每小时/每30分钟示例，parameters/execute 逐字保留。未新增任务创建或编辑入口。 |

### 经授权补齐的后台遗漏

任务书认为“传 __pulse__ 就能整理”，但实际 worker 只枚举 `settings.assistants`，脉搏不在其中；
原轮次算法也只按用户消息切分，而自动脉搏只保存助手回复。实施前已说明差异并获维护者授权补齐。

- `MemoryConsolidator.memoryConsolidationAssistants` 只在整理器内解析 Pulse facade；发现、待整理查询、手动/自动执行和对话工具均使用同一范围。脉搏只能使用 `PULSE_ASSISTANT_ID` 的对话与 `__pulse__` 记忆桶；普通助手、全局和群组作用域保持原样。
- 脉搏专用轮次视图支持首条/连续自动助手回复，也保留手动用户轮次的锚点。消息原位置、内容、ID和时间均不变，没有添加临时用户指令、截断或回滚历史。仍按时间戳推进，初次最多回看12轮、每批最多12轮，后续积压逐批处理。
- 待审批轮次不触发整理，也不让后面的并发消息越过它推进进度；已放弃的旧用户输入或通知不会卡住后续完成轮次。普通聊天轮次算法的默认行为不变。
- `PulseMemoryCompletion.persistPulseResponse` 按响应ID合并最新会话，成功 flush 后才通知；磁盘失败不通知，重试不重复插入，也不覆盖并发用户消息。重复通知由整理器读取原有时间进度去重。过期 incarnation、错误会话或非助手结果拒绝写入；静默未保存输出没有经过此入口。
- ChatService 保留生成供应商的 DeepSeek Web 守卫；整理器仍明确拒绝 DeepSeek Web `memory_write`，没有放开 F009 能力、工具审批或短信逐次确认。

自动开关默认关闭。开启后沿用原整理调度器的空闲2分钟/累计6轮/离开会话或应用等机制，
不是每轮立即另起一次模型请求；这些是记忆整理批处理策略，不是生成时限。关闭自动仍可手动整理。
已有记忆关闭时禁用自动开关但保留其存储值，不删除记忆。

### 参考与验收

实施前已直接阅读 Kai `MemoryStore.kt` 和 PalmClaw `agent/MemoryConsolidator.kt`、
`memory/MemoryStore.kt` 的会话进度部分；固定 commit、实际路径和采用范围见
`docs/f015-keepalive-reference-review.md` 本批记录。未复制源码或移植参考仓库的全局正文、
消息数量进度/截断或时间上限；继续复用本项目整理器和存储。两仓保留在产品目录外。

| 验收 | 证据/边界 |
| --- | --- |
| V1 | `./gradlew test :app:assembleGithubRelease --offline --console=plain` 成功，Release Lint Vital/R8通过；247套件1666项，1665通过、1项既有PDF跳过，0失败/错误。 |
| V2 | 两页签及只在文件页显示新建的源码已接线；设备截图待维护者安装验收。 |
| V3 | 共享状态/待整理轮数/立即整理/失败重试原组件接线完成；调度状态回归通过，设备截图和真实模型整理待验。 |
| V4 | 回归覆盖脉搏独立桶/其他桶隔离、自动/手动、纯自动与混合历史、审批、重启/分批及成功保存后通知；真实文件内容整理和模型调用待维护者验收。 |
| V5 | 前批源码逐文件校验确认任务页及后端未变；未增加新建/编辑入口。 |
| V6 | 描述已包含每日09:00的 `when=cron`、`cron="0 9 * * *"` 示例；没有运行真实模型，截图和实际工具参数原文待验，不用手造JSON冒充模型输出。 |
| V7 | 描述已包含工作日 `0 9 * * 1-5`，原Cron解析测试继续参与；实际自然语言决策待验。 |
| V8 | 描述保留 once/execute_at 和有效未来区间；原任务仓库校验不变，真实“明天下午3点”模型决策待验。 |
| V9 | 旧字段、序列化名/默认值、任务/cron/调度完全保留，新开关旧数据默认false；JSON/Preferences和实际DataStore重启回归通过。真实覆盖安装待验。 |

V2/V3/V6 的三张截图及 V6 真实工具调用参数均未生成：本轮没有设备或真实模型运行，
不将纯函数测试、源码审查或离线文字当成这些验收证据。

### 构建与交付

本批目录：`artifacts/3.1.2-work.1/f015-dreaming-release-20261110/`。旧交付保留。

- `RikkaHub.Work-3.1.2-work.1-arm64-v8a-release.apk`
- `RikkaHub.Work-3.1.2-work.1-x86_64-release.apk`
- `RikkaHub.Work-3.1.2-work.1-universal-release.apk`
- `RikkaHub.Work-3.1.2-work.1-source.zip`（根含本文件，排除 .git、APK、构建缓存、参考仓库与本地凭据）

最终组合构建成功（1分22秒，876项任务：37执行、839复用），且对应最终源码。
JVM汇总247套件/1666项：1665通过、1项既有PDF跳过，0失败/错误；不重复计算Play变体。
App GitHub的129套件/888项实际重新执行全部通过，AI等未变模块的UP-TO-DATE报告如实复用，
不是1666项全部重新运行。新加13项回归（轮次/范围6、调度隔离2、保存接线3、配置/磁盘各1）；
五个相关套件共70项通过。未删除、停用或忽略既有测试。

| APK | bytes | MiB | SHA-256 |
| --- | ---: | ---: | --- |
| ARM64 | 16,386,696 | 15.63 | `2402620f4979cfef81e28418fbbd1aa60c50b15f2aeb4ef153c8c9db5d90d1d3` |
| x86_64 | 16,485,625 | 15.72 | `9d3e3c2c67cd1e7fd82768c6f7c612d91f858e32c61def14f039c84251cf4a52` |
| Universal | 19,217,804 | 18.33 | `de137ed092c7089d4eacd62234973959c27f6e23baffb602599989e5b47dddd1` |

三个APK沿用原V2正式证书，证书SHA-256为
`671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b`；
包名 `me.rerere.rikkahub.plus`、显示名 `RikkaHub Work`、版本/ABI、非debuggable、
ZIP CRC、DeepSeek WASM和原20张图标PNG像素均核验通过；21个身份图标源文件匹配固定基准。
三语各2506条且key集合相同；只有四个指定新key，其他翻译值保留。

对照前批1792项源码清单，本批仅修改19个既有文件、新增2个文件；其他旧增量、软链接、
任务页/cron/任务仓库/运行时、数据库、依赖、原图标及PulseToolPolicy保持。
共享整理组件仅改可见性、工具仅改description，均通过与前批ZIP逐字比较。
HEAD与Git index未变，`git diff --check`通过，前批三个APK和源码ZIP的SHA256SUMS复验通过。

新源码ZIP共1794项（1792普通文件、2软链接），根含最新本文件，逐项核对文件集合、内容哈希、
权限、UTF-8标志和CRC；不打包APK、构建缓存、参考仓库、凭据或签名私钥。
压缩包自身大小/哈希保存在外部 `SOURCE-VERIFICATION.json` 与 `SHA256SUMS`，避免循环写入根文档。
其他证据为 `TEST-RESULTS.json`、`APK-VERIFICATION.json`、`SOURCE-AUDIT.json`、`SOURCE-MANIFEST.json`
以及构建日志。最初一次漏import导致的编译失败保留在 `build-attempt1.log`，已修正；
最终日志是 `build-combined.log`，并非用旧APK或前一轮测试冒充最终验证。

仅输出Release APK，`test`使用Debug JVM变体不等于输出Debug安装包；Release无独立注册的JVM测试变体。
全仓完整Lint及设备行为未测试，Release Lint Vital已通过。V2/V3/V6截图、真实模型工具参数、
实际记忆内容与覆盖安装仍由维护者验收，不将编译/JVM通过等同于实机验收完成。

---

# 上一批：F015 闹钟、记忆与图片/视频输入 — 3.1.2-work.1 / 201（历史 Release）

交付日期：2026-11-10（Asia/Shanghai）。依据 `RikkaHub Work-F015闹钟记忆图片与视频-20261110.zip`，维护者已授权开始。版本保持 `3.1.2-work.1 / 201`，沿用“三个 Release APK 和源码”的明确要求。当前分支 master，HEAD 保持 `18c372d58f77da7c8a1c1f59042993e01be2bd5b`；未提交、暂存、打 tag、推送或发布，未操作设备、模拟器或真实账号。

### 六项实施及文件

下表路径相对项目根目录；既有 F015/F018、闹钟、白名单与顶栏 root 坐标修正继续保留。

| 项目 | 完成内容 | 文件 |
| --- | --- | --- |
| [x] §1 闹钟独立开关 | `enableAlarm=false` 持久化，位于安排日程之后；工具注册与最终过滤均读取它，信息开关不再决定闹钟。旧配置默认关闭。 | `data/datastore/PulseConfig.kt`、`data/model/PulseChat.kt`、`data/ai/tools/pulse/PulseToolPolicy.kt`、`ui/pages/pulse/PulseSettingsPage.kt` |
| [x] §2 抽屉空任务 | 心跳开启时始终展示待执行区域；空列表显示“没有待执行任务”，非空仍最多三条及剩余数量。 | `ui/pages/pulse/PulseStatusRow.kt` |
| [x] §3 今日统计 | 抽屉固定今日文案和两位费用小数；峰谷模式先按本地日界筛选日志，避免把跨天近期数据标成今日。设置页近期统计保持原状。 | `ui/pages/pulse/PulseStatusRow.kt`、`data/pulse/PulseCost.kt` |
| [x] §4 脉搏记忆 | 顶栏记忆按钮在预览按钮左边；独立记忆列表只读取 `__pulse__` 桶，复用既有 Markdown 新建/编辑页，空列表提供说明。 | `ui/pages/pulse/PulseMemoryPage.kt`（新增）、`ui/pages/chat/ChatPage.kt`、`RouteActivity.kt` |
| [x] §5 工作区图片 | 单聊/脉搏从会话有效助手提供工作区，群聊从当前群提供；图片消息与 Markdown 共用既有路径解析。 | `ui/pages/chat/ChatPage.kt`、`ui/components/message/ChatMessage.kt`、`ui/components/richtext/Markdown.kt` |
| [x] §6 视频输入 | VIDEO 仅追加在能力枚举末尾；内置工具页增加默认关闭的视频开关。非 Google 按开关显示视频，Google 原视频/音频保持；Chat Completions 用户和助手内容序列化为 `video_url`，流式/非流式共用路径。能力关闭、未接协议或编码失败均明确报错。 | `ai/.../provider/Model.kt`、`VideoInput.kt`（新增）、`providers/openai/ChatCompletionsAPI.kt`、`util/FileEncoder.kt`；App 的 `SettingProviderDetailPage.kt`、`FilesPicker.kt`、`ModelList.kt`、`ChatRequestFactory.kt`、`ChatService.kt` |

App 表中未带前缀的 Kotlin 文件位于 `app/src/main/java/me/rerere/rikkahub/`；AI 文件位于 `ai/src/main/java/me/rerere/ai/`。三语文案同步 `app/src/main/res/{values,values-zh,values-zh-rTW}/strings.xml`。

### 源码核对与任务书适配

- 任务书只改策略不足以控制注册，本轮同时更正 `PulseConfig.toAssistant()`。`writes` 集合、set_alarm 参数/前台限制/回执、工具审批链不改。
- 实际类为 `MemoryRepository`，导航为 Nav3 `entry<Screen.PulseMemory>`；不使用任务书的不存在类名/旧导航示例，不依赖 `AssistantDetailVM`。既有记忆作用域及 DB33 未改，聊天/群组仍不能写入脉搏桶。
- 群聊有独立宿主，两个页面分支均提供 `LocalWorkspaceId`；单聊使用会话快照解析后的有效助手。路径解析函数体不改，只放宽内部可见性；网络、file/data 图片和路径安全检查保持原状。
- 峰谷费用来自最多 200 条保留日志，本轮只取当天部分，因此仍是保留样本的估算；不承诺超过日志保留上限时的完整日账单。日界按时区计算，包含夏令时的 25 小时日回归。
- `Video.encodeBase64()` 实际返回 `Result<String>`。失败显式抛脱敏的 `VideoInputException`，不会按任务书例子变成空文本。补齐实际用户序列化、助手内容过滤和纯文本快捷路径，避免视频在进入 JSON 前被丢弃。
- `video_url` 是部分供应商的扩展能力；显式勾选并不保证所有 OpenAI 兼容模型都支持。当前非 Google 只接 Chat Completions；Responses、Claude 等未接协议会明确报错，Google 沿原生路径。没有增加其他供应商的音频能力。
- 改动前已直接阅读保留的固定版本 Kai `SetAlarmTool.kt` / `MemoryStore.kt` 与 PalmClaw `MemoryTools.kt`；参考系统创建请求、数据作用域和显式失败边界，不复制源码或移植其存储设计。具体 commit 与路径见 `docs/f015-keepalive-reference-review.md`。

### V1–V16 验收

| 编号 | 自动检查与实际边界 |
| --- | --- |
| V1 | `test :app:assembleGithubRelease --offline --console=plain` 成功；246 套件共 1,653 项，1,652 通过、1 项既有 PDF 语料测试跳过，0 失败/错误。App GitHub 的 128 套件/875 项本次执行；其余 778 项报告由 Gradle 复用（含本批先前已通过的 AI 389 项），Play 重复用例未重复计数。 |
| V2 | 静态确认独立开关在日程之后、默认 false、旧信息组说明移除；设备页面截图待维护者提供。 |
| V3 | 新增注册与策略回归覆盖独立开/关、信息开关无关联、供应商过滤优先及副作用回执不变；真实模型可见工具清单未联网验收。 |
| V4 | 静态确认开启心跳且无任务时仍显示区域和三语占位；设备截图待验。 |
| V5 | 原待执行排序、最多三条、数量与下一次展示保留，既有纯函数测试继续参与；真实双任务抽屉待验。 |
| V6 | 新增当天/前后日边界、跨夏令时及普通计价回归；抽屉使用今日模式。峰谷来自当天保留日志，视觉待验。 |
| V7 | 顶栏记忆与预览两个按钮接线保留，新增 Nav3 路由；设备截图待验。 |
| V8 | 静态确认 `getFilesFlow(__pulse__)` 及新建/编辑参数，既有作用域测试继续参与；设备新建/编辑/隔离交互待验。 |
| V9 | 两处工作区 provider、Markdown 与消息图片共同解析已接线；真实工作区文件渲染待验。 |
| V10 | 解析函数体未变，非绝对工作区地址保持原 URL；真实上传图片视觉待验。 |
| V11 | 新开关只增删 VIDEO，不改 TOOL/REASONING；旧数据默认无 VIDEO；三语说明更新。设备截图待验。 |
| V12 | 静态确认显式 VIDEO 或 Google 时提供视频入口；设备截图待验。 |
| V13 | 静态确认非 Google 且无 VIDEO 时不显示入口；请求校验另有关闭能力拒绝回归。 |
| V14 | Google 原生视频/音频判断保持，校验器测试覆盖无新标记仍可用；未做 Google 真实请求。 |
| V15 | 新离线回归通过实际请求构造器检查流式/非流式、文字+视频、纯视频用户/助手及失败反馈；合成请求夹具见下。未上传真实视频，未验证 Qwen/GLM 对内容的描述能力。 |
| V16 | 回归覆盖旧能力名称 JSON、缺失 VIDEO、追加枚举与新字段 JSON/DataStore 往返；真实覆盖安装待验。 |

V2/V4/V7/V11/V12 五张截图均未生成：本轮没有操作 Android 设备或模拟器，也没有用文字夹具冒充截图。

### 视频请求证据

`ChatCompletionsVideoTest` 直接调用真实请求构造器生成以下合成结构（离线、无账号、非网络抓包，`AAAA` 仅为测试编码数据，不是可播放视频）：

```json
{"model":"video-test","messages":[{"role":"user","content":[{"type":"video_url","video_url":{"url":"data:video/mp4;base64,AAAA"}}]}]}
```

本目录 `VIDEO-OFFLINE-FIXTURE.json` 保存测试输出的完整请求结构和来源说明。测试同时检查文字/视频顺序、不吞纯视频，以及错误不泄露附件地址或编码内容。

### 构建与交付

最新目录：`artifacts/3.1.2-work.1/f015-alarm-memory-media-release-20261110/`。旧目录原样保留，旧 APK 不含本批六项改动。最终组合构建成功，876 个任务中 26 项执行、850 项复用；本批先前已完成 AI/Main/UI 编译与 R8，修正旧闹钟断言后再次通过完整任务图。

| APK | bytes | 大小（MiB） |
| --- | ---: | ---: |
| `RikkaHub.Work-3.1.2-work.1-arm64-v8a-release.apk` | 16,383,640 | 15.62 |
| `RikkaHub.Work-3.1.2-work.1-x86_64-release.apk` | 16,482,565 | 15.72 |
| `RikkaHub.Work-3.1.2-work.1-universal-release.apk` | 19,214,748 | 18.32 |

- `RikkaHub.Work-3.1.2-work.1-source.zip`（根含本文件，包含当前未提交源码；排除 .git、构建缓存、APK、参考仓库及本机凭据）

三个 APK 的正式证书 V2 签名、包名 `me.rerere.rikkahub.plus`、显示名 `RikkaHub Work`、版本、ABI、非 debuggable、ZIP CRC、原应用图标像素及 DeepSeek WASM 均已核验。R8/资源收缩及 Release Lint Vital 通过；没有执行全仓完整 Lint。三语各 2,502 条字符串且 key 集合相同；21 个身份图标源文件匹配固定基准。

对照上一批 1,789 项源码清单，本批仅变更 30 个既有文件、增加 3 个文件；其他原有未提交源码、测试及软链接保持。新增文件为 VideoInput、视频请求测试与 PulseMemoryPage。策略文件仅拆分闹钟开关，writes/审批守卫不变；图片解析函数体不变；上一批玻璃 root 坐标和全尺寸宿主保持。HEAD 及 Git index 均未改变，`git diff --check` 通过。

源码 ZIP 共 1,792 项，逐文件校验集合、内容、软链接、权限、UTF-8 与 CRC；具体大小与校验值写在外部 JSON，避免把压缩包自身哈希循环写入根文档。证据分别保存在 `TEST-RESULTS.json`、`APK-VERIFICATION.json`、`SOURCE-AUDIT.json`、`SOURCE-MANIFEST.json`、`SOURCE-VERIFICATION.json` 和 `SHA256SUMS`。保留 DB33、依赖、F004/F009 限制、短信每次确认、心跳间隔及无生成时限约定。

---

# 上一批：F019 顶栏玻璃概率性偏移 — 3.1.2-work.1 / 201（历史 Release）

交付日期：2026-11-10（Asia/Shanghai）。采用 `02-修正单·顶栏玻璃概率性偏移（20261110）.md` 与 `00-先读我（闹钟与顶栏玻璃）.md`，覆盖旧 F019 §2 的 A/B 尺寸假设。版本保持 `3.1.2-work.1 / 201`。维护者此前明确要求“仍交三个 Release APK 和源码”，本轮继续生效，覆盖附件“不附 APK”建议。

用户反馈尺寸正确、偶发位置偏移且重启恢复；本轮按修正单统一坐标，消除跨帧读取窗口位置的依赖。此为源码修复结论，未用设备验证概率性偏移已消失。当前 master HEAD 仍为 `18c372d58f77da7c8a1c1f59042993e01be2bd5b`，未提交、打 tag、推送或发布。已有 F015/F018、感知层、闹钟与白名单增量全部保留。

### 三处修改

- [x] `ui/components/ai/ChatTopBarCapsule.kt`：`positionInWindow()` 改为 `positionInRoot()`，顶栏玻璃 bounds 来自前景 Compose root；撤销上一批新增的 `wrapContentSize` 与 Row 测量，恢复原 Box 测量，不再按尺寸故障修正。
- [x] `ui/components/ai/ChatInput.kt`：输入框 bounds 同步改为 `positionInRoot()`，避免顶栏与输入框坐标口径分裂。
- [x] `ui/components/ai/LiquidGlassInputBackground.kt`：删除 `getLocationInWindow()` 和跨帧宿主窗口坐标相减，改为只扣除 `host.paddingLeft` / `host.paddingTop`；诊断日志改为 `root=... hostPadding=... local=...`。

### 静态验收

| 检查 | 实际结果 |
| --- | --- |
| `positionInWindow`（目标目录完整词） | 0 行 |
| `positionInRoot`（目标目录完整词） | 4 行：两个 import、两个调用 |
| `positionInRoot()`（只数调用） | 2 处：顶栏、输入框各 1 处 |
| `getLocationInWindow`（目标目录完整词） | 0 行 |
| 宿主布局 | `sourceView` 与 `inputView` 均为 `MATCH_PARENT`，未设置 padding；宿主 `clipToPadding=false` |
| 版本 | `3.1.2-work.1 / 201`，未修改 |
| `git diff --check` | 通过 |

完整词检索的实际命中如下（使用 `rg -n -g '*.kt'`，未使用不支持的 `rg --include`）：

```text
ChatInput.kt:75:import androidx.compose.ui.layout.positionInRoot
ChatInput.kt:276:                        val position = coordinates.positionInRoot()
ChatTopBarCapsule.kt:25:import androidx.compose.ui.layout.positionInRoot
ChatTopBarCapsule.kt:61:                val position = coordinates.positionInRoot()
```

### G1–G8 验收与设备边界

| 编号 | 结果/证据 |
| --- | --- |
| G1 | `test :app:assembleGithubRelease --offline --console=plain` 成功，耗时 2 分 46 秒；245 套件共 1,639 项，1,638 通过、1 项既有 PDF 语料测试跳过，0 失败/错误。App GitHub 的 128 套件/871 项本轮实际执行，其余模块 768 项报告由 Gradle 复用；Play 重复用例未重复计数。 |
| G2 | 未运行设备，未取得液态脉搏顶栏截图。 |
| G3 | 静态核对聊天/群组的顶栏与输入框均位于同一个全尺寸前景 ComposeView，回调口径一致；实机对齐待验。 |
| G4 | 未进行 20 次进出，不能宣称“20/20 对齐”。 |
| G5 | 未进行真实键盘弹收验证。 |
| G6 | 非液态不创建原生玻璃区域；原尺寸测量恢复，Haze 样式及按钮接线未改；视觉待验。 |
| G7 | 未运行 adb/logcat，无真实 bounds 日志；源码宿主 padding 为 0，不当成设备测量。日志将输出 `region=... bounds=... root=... hostPadding=... local=...`。 |
| G8 | 调用数 0 / 2 / 0；完整词数 0 / 4 / 0（包含 import），与任务书命令的计数笔误明确区分。 |

没有操作 Android 设备、模拟器、真实闹钟、通知或短信。系统 Clock 的创建请求与失败反馈沿用上一批，参考 Kai/PalmClaw 的核对记录保持；本轮没有新增闹钟代码或复制参考源码。顶栏四处参数仍为 `0.60 / 0.16 / 2dp`，输入参数、配置、数据库、依赖、三语及原应用图标均保持。没有硬编码状态栏偏移、增加布局监听或固定 48dp 尺寸。

### 最新交付物

最新目录为 `artifacts/3.1.2-work.1/f019-root-coordinates-release-20261110/`，此前目录原样保留，旧 APK 不含本轮坐标修正。

| APK | 大小（bytes） | 架构 |
| --- | ---: | --- |
| `RikkaHub.Work-3.1.2-work.1-arm64-v8a-release.apk` | 16,374,764 | arm64-v8a |
| `RikkaHub.Work-3.1.2-work.1-x86_64-release.apk` | 16,473,689 | x86_64 |
| `RikkaHub.Work-3.1.2-work.1-universal-release.apk` | 19,205,872 | arm64-v8a + x86_64 |

- `RikkaHub.Work-3.1.2-work.1-source.zip`（根含本文件；完整当前源码，排除 .git、APK/build/cache、参考仓库与本机签名配置）

三个 APK 均通过正式证书 V2 校验、包名/显示名/版本、ABI、非 debuggable、ZIP CRC、原应用图标像素及 DeepSeek WASM 校验。R8/资源收缩及 Release Lint Vital 通过，本轮未运行全仓完整 Lint。三语资源各 2,491 条且键集合一致，原始 21 个身份图标源文件均匹配基准；HEAD 和 Git index 保持不变。

具体大小、签名、ABI、图标/WASM、测试汇总、逐文件来源与 SHA-256 见同目录 `APK-VERIFICATION.json`、`TEST-RESULTS.json`、`ROOT-COORDINATE-AUDIT.json`、`SOURCE-VERIFICATION.json` 和 `SHA256SUMS`。对照上一批 1,789 项源码清单，仅三个产品文件及五份相关文档变化，其余源码及测试保持一致；源码 ZIP 逐文件校验内容、软链接、权限、UTF-8 与 CRC。

---

# 上一批：F015 抽屉状态卡与白名单 + F019 闹钟与顶栏玻璃（历史 Release）

交付日期：2026-11-10（Asia/Shanghai）。本批同时依据 `RikkaHub Work-F015抽屉状态卡与白名单-20261110.zip` 与 `RikkaHub Work-F019闹钟与顶栏玻璃-20261110.zip`。维护者明确要求仍交三个 Release APK 和源码 ZIP，覆盖任务包“不附带 APK”的建议；版本保持 `3.1.2-work.1 / 201`。当前 `master` HEAD 为 `18c372d58f77da7c8a1c1f59042993e01be2bd5b`，本批改动仍未提交、打 tag、推送或发布。

## 本批实现

### F015：抽屉状态卡与通知白名单

- `service/pulse/PulseSchedule.kt` 新增只读 `PulseUpcoming` 与 `pulseUpcomingTasks`：只展示启用任务，读取已有 `dueAt`/`nextAt`，不解析 cron、不改变真实调度；具体时间升序，随心跳任务排最后，空标题回退 prompt 前 20 个字符，过期任务不被静默丢弃。
- `ui/pages/pulse/PulseStatusRow.kt` 将状态、下一次触发/待执行数量、今日用量分行展示，最多列出 3 个待执行任务并显示剩余数量；`ui/pages/chat/ChatDrawer.kt` 移除重复的最近 5 条心跳日志，设置页运行日志保持不变。
- `ui/pages/pulse/PulseSensingSettings.kt` 增加通知白名单全选、反选和“已选 N / 共 M”；批量更新始终读取最新配置，只翻转当前已安装列表，保留列表外包名和原顺序；加载中或空列表禁用批量按钮。
- 新增 `PulseUpcomingTest` 与 `NotificationWhitelistSelectionTest`，覆盖启用过滤、ONCE/CRON 排序、心跳末尾、空标题、全选、反选、列表外包名保留和不改其他配置字段。

### F019：系统闹钟与顶栏玻璃

- 新增 `set_alarm` 本地工具，参数为 `hour`、`minute`、`label`、`duration_seconds`。倒计时优先；否则必须同时提供合法的小时和分钟；只接受整数，不猜测当前时间。倒计时范围为 1–86400 秒，遵循系统 Clock 的 24 小时上限。
- 闹钟只通过 `AlarmClock.ACTION_SET_ALARM` / `ACTION_SET_TIMER` 发起系统请求，不自建闹钟记录，不提供查询、修改或删除。工具仅在当前前台 Activity 存在时执行；后台、无系统 Clock、`SecurityException` 或 `ActivityNotFoundException` 均返回 `success:false`。成功只表示请求已发送为 `request_sent`，提示维护者到系统时钟核实，不宣称系统已持久化。
- 闹钟选项追加到 `LocalToolOption` 末尾，接入助手本地工具页、脉搏信息工具组、风险等级与副作用回执保护；旧序列化名称保持兼容。新增 `AlarmToolTest`、`AlarmRegistrationTest` 和相关策略回归。
- `ChatTopBarCapsule.kt` 的玻璃 bounds 改在实际按钮内容 Row 上测量，并对外层使用 wrap-content；`LiquidGlassInputBackground.kt` 统一日志标签为 `LiquidGlass` 并保留窗口到宿主的坐标换算。聊天、脉搏、群组按钮均保留，既有顶栏参数 `refraction=0.60`、`tint=0.16`、`blur=2dp` 未改变，没有加入固定偏移或全局固定尺寸。

### 逐文件索引

| 文件 | 改动 |
| --- | --- |
| `service/pulse/PulseSchedule.kt` | 只读待执行项目构造与排序。 |
| `ui/pages/pulse/PulseStatusRow.kt` | 状态卡分行、待执行列表及剩余数。 |
| `ui/pages/chat/ChatDrawer.kt` | 删除重复的心跳日志列表。 |
| `ui/pages/pulse/PulseSensingSettings.kt`、`NotificationWhitelistSelection.kt` | 全选/反选 UI、最新状态变换、列表外保留和计数。 |
| `data/ai/tools/local/AlarmRequest.kt`、`AlarmTool.kt` | 参数解析、系统闹钟请求和如实失败/成功回执。 |
| `utils/AlarmForegroundActivity.kt`、`RikkaHubApp.kt` | 跟踪即时 resumed/focused Activity，应用启动时注册生命周期回调。 |
| `AndroidManifest.xml` | 声明系统闹钟请求权限；未请求运行时权限。 |
| `data/ai/tools/local/LocalToolOption.kt`、`LocalTools.kt` | 在末尾增加稳定序列化选项并按需创建工具。 |
| `data/model/PulseChat.kt`、`ui/pages/assistant/detail/AssistantLocalToolPage.kt`、`ui/pages/pulse/PulseSettingsPage.kt` | 闹钟随脉搏信息组开放、助手本地工具页可选，并说明前台限制。 |
| `data/ai/tools/ToolRiskTiers.kt`、`data/ai/tools/pulse/PulseToolPolicy.kt` | 接入 L2 创建风险、信息开关及副作用回执保护；DeepSeek Web 限制保持。 |
| `ui/components/ai/ChatTopBarCapsule.kt`、`LiquidGlassInputBackground.kt` | 按实际内容测量玻璃 bounds，保留原有坐标换算并整理诊断 tag。 |
| 三个 `values*/strings.xml` | 添加 F015 白名单/状态卡与 F019 闹钟三语文案。 |
| `PulseUpcomingTest.kt`、`NotificationWhitelistSelectionTest.kt` | F015 纯函数回归。 |
| `AlarmToolTest.kt`、`AlarmRegistrationTest.kt`、`PulseToolPolicyTest.kt`、`ToolRiskTiersTest.kt` | 参数边界、旧序列化兼容、前后台行为与风险策略回归。 |
| `AGENTS.md`、`CHANGES.md`、`FEATURES.md`、`docs/f015-keepalive-reference-review.md` | 更新本批约定、变更声明、功能台账与参考核对。 |

## 参考仓库边界

闹钟实现前阅读 Kai 固定 commit `ed6a3e0c48e10de9e2852e939fa4531975352bff` 的 `SetAlarmTool.kt`，采用其系统 Clock Intent 入口，但没有复制其宽松数值转换或“已设置”成功断言。PalmClaw 固定 commit `75a42aeda0e580ebad0de4a38c00c94146148366` 没有同名闹钟模块，本批核对其前台运行上下文、统一 runtime 入口和失败显式反馈边界；没有复制源码。完整记录见 `docs/f015-keepalive-reference-review.md`，参考仓库仍在 `/home/li/CDX/rikkahub-references/F015/`，不进入 APK 或源码 ZIP。

## 验收结果

### F015 V1–V14

| 编号 | 结果 | 证据/限制 |
| --- | --- | --- |
| V1 | 通过 | 合并构建与全部 JVM 测试成功。 |
| V2 | 通过 | `PulseUpcomingTest` 覆盖启用过滤、ONCE/CRON 时间排序、心跳末尾和空标题回退。 |
| V3 | 通过静态/JVM | 有任务时渲染状态、下一次/数量、今日用量和最多 3 条列表；设备视觉待验。 |
| V4 | 通过静态/JVM | 无任务时不渲染待执行分隔线和列表；设备视觉待验。 |
| V5 | 通过静态/JVM | 活跃时段外保留任务数量并隐藏具体下一次文本；与任务书“明天时间”的文字示例存在显示策略差异，原因是当前状态组件只使用已有 `next` 文本，未新增调度计算。 |
| V6 | 通过静态 | 失败状态去除重复时间参数；设备视觉待验。 |
| V7 | 通过静态 | 未开启心跳时隐藏待执行区域。 |
| V8 | 通过源码检查 | `ChatDrawer.kt` 不再渲染 `heartbeatLog.take(5)`；设置页运行日志未删除。 |
| V9 | 通过 JVM | 全选只添加当前列表包名，测试覆盖 5/5。 |
| V10 | 通过 JVM | 反选只翻转当前列表，测试覆盖 3 个已选变为其余项。 |
| V11 | 通过 JVM | 列表外已选包名保留。 |
| V12 | 通过静态 | `apps == null` 或空列表时按钮禁用。 |
| V13 | 通过 | `values`、`values-zh`、`values-zh-rTW` 键集合一致。 |
| V14 | 未做设备验证 | 通知读取主流程未改；没有授予通知权限或读取真实通知。 |

任务书要求的五种状态文字已由资源/组合逻辑核对：有任务、无任务、活跃时段外、上次失败、未开启心跳。由于本轮没有设备或设计稿 HTML，交付的是源码逻辑证据，不把离线文字夹具称作截图。

### F019 V1–V12

| 编号 | 结果 | 证据/限制 |
| --- | --- | --- |
| V1 | 通过 | `./gradlew test :app:assembleGithubRelease --offline --console=plain` 成功，包含 Release 编译、R8、lint vital 与 JVM 测试。 |
| V2 | 未做设备验证 | 离线测试验证了 07:30 请求 JSON 和 Intent 分支；未启动真实系统 Clock。 |
| V3 | 通过静态/JVM | 离线测试验证 `duration_seconds` 走 timer 分支；真实 Clock 待维护者验证。 |
| V4 | 通过 JVM | 缺少 hour/minute 或 duration 返回失败，不猜时间。 |
| V5 | 通过 JVM | 后台分支不调用 launch，返回“请在应用前台设置”。 |
| V6 | 通过静态 | 助手本地工具页出现“闹钟”开关。 |
| V7 | 通过静态 | 脉搏信息工具开关接入闹钟。 |
| V8 | 通过 JVM | 旧本地工具序列化名称回归通过，Alarm 只追加在末尾。 |
| V9 | 未做设备验证 | 顶栏 bounds 修正已编译，未获得任务书要求的液态模式截图。 |
| V10 | 未做设备验证 | 聊天/群组调用保持原有按钮接线，未进行实机渲染回归。 |
| V11 | 通过静态 | 非液态绘制分支未改。 |
| V12 | 未做设备验证 | 日志标签已统一为 `LiquidGlass`，但未运行 `adb logcat -s LiquidGlass`，没有可提供的真实 bounds 行。 |

离线 fake-launch 回调产生的闹钟/倒计时 JSON 夹具保存在 `ALARM-OFFLINE-FIXTURE.json`。它只证明参数到请求回执的纯逻辑，不证明 Android Activity 启动成功、系统时钟保存闹钟或 ROM 行为。

明确偏差：任务书示例使用 `FLAG_ACTIVITY_NEW_TASK`，本实现从真实前台 Activity 调用，因此没有添加该 flag；任务书写“无需权限”，本实现保留普通 `com.android.alarm.permission.SET_ALARM` Manifest 声明，未请求运行时权限，具体 ROM 行为仍需设备核对；任务书示例返回“已设置”，本实现改为更保真的 `request_sent`，避免把系统 Clock 的持久化结果说成已确认。

## 构建与完整性

- 版本：`3.1.2-work.1`，`versionCode 201`；包名 `me.rerere.rikkahub.plus`；显示名 `RikkaHub Work`。
- Release APK 使用既有正式证书 `671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b`，均通过 APK V2 签名校验且不可调试；R8/资源收缩已执行。
- 三语资源各 2,491 个，键集合一致；只保留 English、简体中文、繁体中文资源约定；原始 21 个 launcher/About 身份图标均与基线 commit `643191229dcbeb4972e3772986818605633ace92` 字节一致。
- 构建日志：`artifacts/3.1.2-work.1/f015-f019-release-20261110/build-combined.log`。
- 机器可复核报告：`APK-VERIFICATION.json`、`TEST-RESULTS.json`、`SOURCE-AUDIT.json`、`SHA256SUMS`，均位于同一 artifact 目录。没有运行 adb、模拟器、设备或真实账号，也没有上传 GitHub。

## 本批交付物

目录：`artifacts/3.1.2-work.1/f015-f019-release-20261110/`

| 文件 | bytes | SHA-256 |
| --- | ---: | --- |
| `RikkaHub.Work-3.1.2-work.1-arm64-v8a-release.apk` | 16,375,188 | `85a5220dcd9241e4ade6d5a94376e264b693a03776bf8452552997c6a937bde4` |
| `RikkaHub.Work-3.1.2-work.1-x86_64-release.apk` | 16,474,113 | `c3f916346807cedf9859ef88bd16258b64f11661823b9d5f8e167f38002cfe5f` |
| `RikkaHub.Work-3.1.2-work.1-universal-release.apk` | 19,206,292 | `418df887459985fc305d88995cbe7ef21953404b6cd0269d5dd2a1779f7e6ed7` |
| `RikkaHub.Work-3.1.2-work.1-source.zip` | 由打包脚本生成 | 见同目录 `SHA256SUMS` |

源码 ZIP 包含当前完整源码、未提交增量、新增测试、文档和根 `DELIVERY.md`；排除 `.git`、build/cache、APK、交付目录、参考仓库、本机配置、签名密钥和其他凭据。打包脚本会逐文件核对内容、软链接、权限和 CRC。

---

# F015 心跳指令位置与感知摘要 — 3.1.2-work.1 / 201（GitHub Release）

交付日期：2026-11-10（Asia/Shanghai）。依据 `RikkaHub Work-F015心跳指令位置与感知摘要-20261110.zip` 中两份说明。维护者明确要求“这一次以同版本号出 Release”，覆盖附件的 Debug/不附 APK 建议；版本保持 `3.1.2-work.1 / 201`。本地 `master` 仍为 `18c372d58f77da7c8a1c1f59042993e01be2bd5b` 加此前未提交增量，未提交、打 tag、推送或发布。

## 本批改动与源码坐标

以下路径中的 `app/...` 表示 `app/src/main/java/me/rerere/rikkahub/`；测试路径中的 `test/...` 表示 `app/src/test/java/me/rerere/rikkahub/`。均为本批最终行号。

| 文件 | 行号 | 改动 |
| --- | --- | --- |
| `app/.../service/pulse/PulseTurnInput.kt` | 5、8 | 空/非空历史都在末尾追加临时 synthetic user，内容是本轮完整指令；systemInstruction 恒为 null，原历史不改写。 |
| `app/.../service/pulse/PulseSensingPrivacy.kt` | 5、8 | 去掉 config 参数，只看短信事件及实际 `read_notifications/read_sms/send_sms` 工具历史；开关开启不再把所有成功轮次判为感知。 |
| `app/.../service/pulse/PulseRuntime.kt` | 208、228、244、307 | 短信发现生成只含条数/人数的 summaryHint，仅用于展示、不进请求；两处感知判定适配新签名；待审批优先于摘要，普通轮次用首行。 |
| `app/.../service/pulse/PulseRuntime.kt` | 239、330 | 错误兜底仍防止感知异常原文进入日志/报告；失败轮次可能已经读到数据却未能返回工具历史，开始及失败时的开关仅在失败分支用于保守脱敏，中途关闭也不丢失保护。 |
| `app/.../data/pulse/SmsReader.kt` | 11、14、54 | 临时 SmsDiscovery 加 senderCount；归一化原号码先去重计数，再只返回打码地址。保留 ID/address 投影、50 条扫描、水位和白名单规则。 |
| `test/.../service/pulse/PulseTurnInputTest.kt` | 24、35、48、58 | 空历史、既有历史、末尾用户文本保护，以及长指令的真实 Chat Completions 协议序列化回归。 |
| `test/.../service/pulse/PulseSensingFlowTest.kt` | 27 | 去掉 config 实参；覆盖 SMS 事件、三种感知工具及关闭开关后的历史保护；无工具与普通工具不误判。 |
| `test/.../data/pulse/SmsPrivacyTest.kt` | 7 | 新增发件人掩码碰撞、格式归一化、重复、白名单、空结果与扫描水位回归。 |
| `app/src/main/res/values/strings.xml` | 2472 | 英文两个摘要 key。 |
| `app/src/main/res/values-zh/strings.xml` | 2474 | 简中两个摘要 key。 |
| `app/src/main/res/values-zh-rTW/strings.xml` | 2473 | 繁中两个摘要 key。 |
| `AGENTS.md`、`CHANGES.md`、`FEATURES.md`、本文件 | 顶部 | 记录本批范围、改动、Release 交付、回滚/重套及设备待验。 |
| `docs/f015-keepalive-reference-review.md` | 顶部 | 记录本轮实际查阅的 Kai/PalmClaw 模块与采用边界。 |

**手动触发自查：已确认没有把按钮文案写入会话的路径。** `PulseSettingsPage.kt:187` 仅调用 `runtime.trigger(manual = true)`；“立即检查一次”只出现在资源 `pulse_run_now`，聊天页正常 sendMessage 与它无关。本项不改 UI/发送逻辑。用户自己输入“执行一次心跳”作为聊天内容的路径不删除。

`ChatService.runPulseTurn/persistPulseTurn`、`ChatRequestFactory`、`appendPulseInstruction` 及 `ChatRequestFactoryPulseInstructionTest` 均与本批开始前一致。持久化仍只接受 assistant response 并按 ID 合并，不写本轮临时 user；未截断历史、未放宽 `isSilentPulse`。PulseConfig、版本、依赖、数据库33/schema、原21个应用身份图标均不改。

## 与任务书写法的偏差及理由

1. 输出三个 Release APK，而非附件建议的 Debug/不附 APK：以维护者最新明确要求为准。没有生成新的 Debug APK。
2. §2 未列出的产品文件仅增加 `SmsReader.kt`：现有 discovery.senders 已打码，不同真实号码可能映射成同一掩码，直接 distinct 会编造偏小的人数。本批仅增加临时计数，不增持久化字段或额外数据存储。对应新增 `SmsPrivacyTest`，不是修改旧工具契约。
3. 短信摘要为“发现 N 条新短信（M 位发件人）”，去掉“已读取”；轮询只取元数据，不能保证模型随后调用 read_sms，更不能保证通过验证码过滤。普通感知提示也使用“已处理通知或短信轮次”，不声称读取两者。
4. 感知失败仍保守保护原始异常和报告，因为抛错时不能取得本轮临时 response 的工具历史；记录开始时及失败时的开关，只用于失败兜底，避免运行中关闭开关时失去保护。正常活动/报告是否脱敏已完全不看开关，符合修正目标。
5. 增加必要的五份台账/参考记录及产物校验脚本。未修改 UI、审批、供应商限制、发送工具或 keep-alive；未恢复任何生成/休眠/空闲超时或累计上限。
6. 任务书要求真实设备请求日志与模型行为证据。本批没有设备、真实账号或短信操作授权，因此仅交付明确标记的离线协议序列化证据；**没有把它冒充设备日志或将 V4～V12 全标通过**。真实模型是否遵守长提示词仍待安装验证。

## 参考复核

实际查阅 Kai 固定 commit `ed6a3e0c48e10de9e2852e939fa4531975352bff` 的 `TaskScheduler.kt:149-235`、`RemoteDataRepository.kt:2148-2168`，以及 PalmClaw 固定 commit `75a42aeda0e580ebad0de4a38c00c94146148366` 的 `HeartbeatService.kt` 和 `GatewayRuntime.kt` 的 decideHeartbeat。采用具体任务用临时 user prompt、人格独立 system、执行不归页面生命周期的思路；保留本项目的完整历史、严格静默与副作用审批，不复制参考源码或收件箱。两个参考仓库仍保留于 `/home/li/CDX/rikkahub-references/F015/`，不打进产品源码 ZIP。

## 自动验证

- `./gradlew test :app:assembleGithubRelease --offline --console=plain`：最终**成功，1分45秒**；872 项任务，35 执行、837 复用。R8、资源收缩和 `:app:lintVitalGithubRelease` 通过。只构建 GitHub Release，未构建 Play AAB 或 Debug APK。首次完整构建3分42秒、876项任务；补上运行中关闭开关的失败脱敏快照后重新校验，初版包可恢复地归档于本批 `intermediate/before-failure-snapshot/`，不是最终交付包。
- 按该任务图汇总 XML，去除 Play 重复与过时报告：**241 套件 / 1,620 项，1,619 通过、1 跳过、0 失败/错误**。App GitHub 124 套件 / 852 项，本轮执行通过；Play 重复渠道也执行通过但不重复计数。唯一跳过为缺语料的既有 `PdfCorpusTest.extractCorpus`；其他模块有 UP-TO-DATE 报告复用，不说每项均重新运行。当前未注册 App Release 变体 JVM 测试，不能将 Debug 变体单测称为 Release 运行测试。
- 全量 Lint 本批未重跑，不能称全量 Lint 通过。上一批 Debug 全量报告仍有 60 既有错误、324 警告、4 提示；本批 Release 必要 Lint 通过与该完整报告是不同范围。没有添加 suppression 或 baseline。
- 三语各 2,480 个 string key、键集合一致；最终 APK locale 只有默认/英文/中文变体。三个 ABI 集合、包名、应用名、版本、非 debuggable、原 Release 证书、WASM、ZIP CRC 均已核验。
- 21 个桌面/关于页身份源文件逐字节匹配基准 `643191229dcbeb4972e3772986818605633ace92`；三个最终 APK 中的20张身份 PNG均与原资源逐像素匹配（资源路径即使缩短也按像素核验）。
- `git diff --check` 通过；本批 `BEFORE-SOURCE.json` 与 `SOURCE-AUDIT.json` 对照本轮范围，确认既有 ChatService/ChatRequestFactory/PulseConfig/build.gradle 和 Git HEAD/index 不变。既有未提交 F015/F018、感知、玻璃/UI 等增量保留。

## 请求与摘要证据（离线夹具，不是设备实测）

`REQUEST-FIXTURE.json` 来自本轮实际执行的 `PulseTurnInputTest` 输出：使用真实 Chat Completions 序列化器，构造了人格/记忆/工作区说明、旧 HEARTBEAT_OK 历史和约2,000字符的新指令。末尾 role/content 的完整 JSON 已保存，未联网、不含 Key。

```json
{
  "messages": [
    {"role":"system","content":"Persona\n<memory_system>Memory instructions</memory_system>\n<workspace>Workspace instructions</workspace>"},
    {"role":"assistant","content":"HEARTBEAT_OK"},
    {"role":"user","content":"[HEARTBEAT] Check notifications, SMS, TODO and streams.\n...完整长指令见本批 REQUEST-FIXTURE.json..."}
  ]
}
```

上面为节选，省略号不是实发内容。真实运行还保留原有 runtime 附加的当前时间 Context；没有改掉这段有用上下文。配置中的具体自定义指令不会再经 pulseInstruction 复制进 system。

`SUMMARY-FIXTURE.json` 为三语资源的**自造数字示例**：简中“发现 3 条新短信（2 位发件人）”；繁中“發現 3 則新簡訊（2 位寄件人）”；英文“3 new messages from 2 senders discovered”。数字计数实现的回归已执行，但这不是收到真实短信后的截图。无感知工具历史的轮次恢复 result 首行；已有感知工具历史仍可能被模型转述，继续保守脱敏，不能为了摘要好看泄露内容。

## V1～V12 验收状态

| 项 | 本批结果 |
| --- | --- |
| V1 编译与全部 JVM 单测 | Release 编译/R8成功；JVM 无失败，1项既有缺语料跳过，不能写“零跳过”。 |
| V2 PulseTurnInputTest | 4项通过，包含更新后的非空历史用例及长指令协议序列化。 |
| V3 PulseSensingFlowTest | 4项通过，已移除所有旧 config 实参。 |
| V4 真实心跳 system 不含用户指令 | 源码与离线序列化证据通过；维护者设备“开发者→请求日志”待验。 |
| V5 同一请求末条 user 为完整指令 | 离线实际协议 JSON 通过；设备实际请求日志待验。 |
| V6 真正调用通知/短信工具 | 未调用真实模型/通知/短信，待维护者验证开启工具后的行为。 |
| V7 无事只输出 HEARTBEAT_OK | 严格判定回归通过；模型实际输出待验。 |
| V8 有事按自定义分支格式输出 | 未操作模型账号，待验。不能保证所有模型只因位置调整就完全遵从。 |
| V9 最近活动不再因开关开启全变通用文案 | 成功摘要分支与感知判据回归通过；抽屉显示和真实内容待验。历史有实际感知工具仍保守保护。 |
| V10 短信数字摘要 | 准确计数/掩码碰撞回归及三语离线样例通过；真实轮询 UI 待验。文字不谎称已读取正文。 |
| V11 日志/通知/报告不含感知正文/号码/验证码 | 本批静态/夹具检查通过，感知报告仍禁写，失败保守脱敏；未读取真实设备文件，不宣称已对全部真实日志完成泄漏验证。 |
| V12 静默心跳不新增消息 | 原严格判定和只保存response的路径保留；实际设备数据库/会话数量待验。 |

## 交付物

新目录：`artifacts/3.1.2-work.1/f015-heartbeat-summary-release-20261110/`。旧 Debug/Release 交付目录未覆盖。

| 文件 | bytes | MiB | SHA-256 |
| --- | ---: | ---: | --- |
| `RikkaHub.Work-3.1.2-work.1-arm64-v8a-release.apk` | 16,370,668 | 15.61 | `1729c8dc4f027039514de0203461e2bf5888d301696f11f64ad54b80a6167137` |
| `RikkaHub.Work-3.1.2-work.1-x86_64-release.apk` | 16,469,597 | 15.71 | `d86df76a0bb6e940a97ca3f39954910bb55cb18349a55e947a06650535a36c6a` |
| `RikkaHub.Work-3.1.2-work.1-universal-release.apk` | 19,201,776 | 18.31 | `2f32b29ca33c74d71d33fcd97110dee82033b77bc7c4476e2378c52a3f7ff47f` |

包名 `me.rerere.rikkahub.plus`，显示名 `RikkaHub Work`；签名证书 SHA-256 为既有 `671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b`，三个包均通过 V2 签名校验且不可调试。体积小于 Debug 来自本次实际完成的 Release R8/资源收缩，不是漏出 unsigned 包；完整 ABI、WASM、原身份图标已检查。

源码 ZIP：`RikkaHub.Work-3.1.2-work.1-source.zip`，包含当前完整源码、已有未提交增量、新测试、跟踪文档、子模块文件及根 DELIVERY，排除 APK、产物、build/cache、.git、参考仓库、本机配置和签名密钥。最终大小/文件数/哈希在包外 `SOURCE-VERIFICATION.json`、`SOURCE-MANIFEST.json`、`SHA256SUMS` 记录，避免源码包内循环写自身哈希。保留 UTF-8 文件名、可执行位与软链，逐文件与工作区核对。

本批没有运行 adb、模拟器、设备或真实账号，没有读取通知/收发短信、授予权限或上传 GitHub。维护者安装后优先验证真实请求末尾指令、通知/短信调用、分支格式、静默不新增消息及抽屉摘要。

---

# F015 包 11、12 感知层 — 3.1.2-work.1 / 201（ARM64 GitHub Debug）

交付日期：2026-11-10。依据维护者提供的 `RikkaHub Work-F015包11与12感知层-20261110.zip` 三份文档实施。版本保持 `3.1.2-work.1 / 201`；本批只提供 ARM64 GitHub Debug APK 与精简源码 ZIP，不 Release、提交、tag、推送或发布，不操作设备、真实账号、通知或短信。

## 本批功能

- 脉搏设置「工具」Tab 在既有工具组后增加读取通知、读取短信和发送短信。三个开关默认关闭、相互独立，不受普通信息工具开关控制；普通聊天和群聊不注册这三个工具。DeepSeek Web 继续先经过原供应商限制，脉搏策略不能恢复供应商禁止的工具。
- 开启读取通知立即跳系统通知使用权；可选择允许读取内容的应用，带图标、勾选、已选置顶和即时保存。其他应用只统计条数；保留时长用 1–72 小时输入框（默认 6），内存队列默认 100 条、配置范围 20–500。排除自身、系统 UI、常驻和组摘要通知；关闭/退出进程清空，撤销白名单即移除已有正文，重新勾选不能恢复。
- `read_notifications` 默认 10、最多 20 条，可按包名或应用名筛选；只给白名单内容，其他只计数。标题 80、正文 200 字符，完整 JSON（含转义和元数据）不超过 4,000 字符。没有自动向每次心跳添加“读通知”指令；TOOLS 模板仅预留注释，且不覆盖已有文件。
- 开启读取短信单独请求 READ_SMS；按需读取最近短信，可只看系统未读、按号码白名单筛选，不改变系统已读状态。默认 10、最多 20 条；验证码规则对完整正文先检查，命中整条不返回，再对普通短信截断到 2,000 字并标记；个人号码中间四位打码，白名单明确选择的号码除外。
- 可选新短信轮询默认关闭，分钟输入框范围 5–240、默认 15。首次种水位跳过历史，后续升序分批（每批最多扫描 50 条），只查询 ID 和发件人、不查询正文。仅告知模型条数和发件人，内容必须另调读取工具。首次空收件箱也会记初始化状态，避免跳过下一条新短信；接口不可用不误记为空箱。
- 轮询复用唯一 PulseRuntime 的第五路候选，早退、取消和重排条件均包含两项轮询开关；锚点来自上次检查时间，无关调度不会反复推迟。仅开短信轮询仍可运行，后台需开启保活，并受活跃时段、系统省电和权限限制。
- 进程重启后，任何尚有效的共享租约均安排到期唤醒，短信 claim 即使不参与业务重放也不例外；到期后按当前水位和开关重新计算下一次检查，避免漏排或重复投喂。
- 发送短信默认关闭，开启单独请求 SEND_SMS；每次发送必须人工确认，包括 AllowAll。卡片显示收件人和正文，200 字以内完整显示，较长可展开，详情页可滚动选择。执行时再查开关、权限、号码及 1–1,000 字范围，默认 SIM 自动分片；`sent:true` 只表示提交系统，不代表送达，可能产生运营商费用。
- 临时自动轮次接受 AwaitingApproval，保存待审批消息；等待期间不启动新自动轮次。用户确认/拒绝后继续现有 ChatService 审批与工具流程；恢复用回复节点标识绑定副作用回执，防止相同轮次重试重复提交。待审批状态不是成功发送。
- 权限拒绝后保留已开启状态；仍显示未授权与二次入口，返回只刷新状态、不反复跳转。数字和号码输入使用独立编辑草稿，保存配置不吞逗号或重置正在编辑的文本。

## 隐私说明与明确限制

- 没有新建持久化通知/短信收件箱；通知缓冲仅内存，轮询持久化的只有水位及初始化/检查时间等运行元数据。验证码规则是关键词加 4–8 位数字，存在误伤或漏判，不能保证识别所有非标准验证码。
- **工具结果会发送至模型供应商并保存在原会话历史中**，发送审批的号码/正文同样属于会话内容；可能被搜索、导出或备份。设置页已明确说明，不宣称“绝不落盘”或“备份不包含”。没有新增短信数据库、FTS 表或备份管线。
- 涉及感知开关、感知工具历史或短信发现事件的自动轮次，活动/运行摘要使用通用文字、不生成正文报告；关闭开关后已有感知工具历史仍触发保护。脉搏普通续写和审批续写的系统通知也使用通用预览。用户主动调用已有写文件等工具仍受既有开关和审批规则约束。
- 本期不做实时 SMS 广播、MMS、双卡选择、送达回执、通知点击/回复、无障碍或全应用枚举；没有 RECEIVE_SMS 或 QUERY_ALL_PACKAGES。短信发送可能被运营商/ROM 异步拒绝，系统接受提交不能作为送达证据。
- 数据库和 schema 仍为 33；配置新字段带默认值，原字段保留。原 21 个桌面/关于页身份图标保持，其他图标正常；三语均为 2,478 个字符串、键集合一致。

## 包 11 改动文件

- 新建：`app/.../service/pulse/PulseNotificationInbox.kt`、`PulseNotificationListenerService.kt`、`NotificationPermission.kt`；`app/.../data/ai/tools/pulse/ReadNotificationsTool.kt`。
- 共享接线：`AndroidManifest.xml`、`PulseConfig.kt`、`SettingsNormalization.kt`、`ChatToolFactory.kt`、`PulseToolPolicy.kt`、`ToolRiskTiers.kt`、`AppModule.kt`、`PulseRuntime.kt`、`PulseWorkspaceProvisioner.kt`、`PulseSettingsPage.kt` 与新建 `PulseSensingSettings.kt`、三语 strings。
- 测试：新建 `service/pulse/PulseNotificationBufferTest.kt`，修改设置、策略与配置测试。

## 包 12 改动文件

- 新建：`app/.../data/pulse/SmsPrivacy.kt`、`SmsReader.kt`、`SmsSender.kt`、`data/ai/tools/pulse/SmsTools.kt`、`service/pulse/PulseSmsSchedule.kt`、`PulseSensingPrivacy.kt`、`ui/components/message/tools/SmsToolUI.kt`。
- 共享接线：上述包 11 共享文件，加 `PulseRuntimeState.kt`、`PulseTurnResult.kt`、`PulseEffects.kt`、`ChatService.kt`、`ChatNotificationManager.kt`、`ToolUI.kt`；三个工具不参与工具输出自动存文件的截断流程，避免大批短信工具结果另落文件。
- 测试：新建 `data/pulse/SmsPrivacyTest.kt`、`service/pulse/PulseSensingFlowTest.kt`；修改 `ToolRiskTiersTest.kt`、`PulseToolPolicyTest.kt`、`SettingsStoreTest.kt`、`PulseConfigTest.kt`。其中验证码正例 12、反例 12 均通过。
- 台账：`AGENTS.md`、`CHANGES.md`、`FEATURES.md`、本文件和 `docs/f015-keepalive-reference-review.md`；未覆盖此前未提交的玻璃、F015/F018 与 UI 增量。

`app/...` 均指 `app/src/main/java/me/rerere/rikkahub/`；测试位于 `app/src/test/java/me/rerere/rikkahub/`。

## 参考、测试与静态核对

- 已直接阅读保留在产品目录外的 Kai 固定 commit `ed6a3e0c48e10de9e2852e939fa4531975352bff` 的通知监听、短信读取/发送/轮询及 TaskScheduler，以及 PalmClaw 固定 commit `75a42aeda0e580ebad0de4a38c00c94146148366` 的自动化调度/心跳内部入口。借鉴边界和调度思路，没有复制参考源码或移植其持久化收件箱。
- `./gradlew test :app:assembleGithubDebug --offline --console=plain` 成功。最终租约修正后的核验为 31 秒，414 项任务（25 执行、389 复用）；App GitHub/Play 单测本轮执行，其余模块复用已有通过报告。
- 按本批任务图汇总 XML，去除 Play 重复与过时任务报告：241 套件、1,617 项测试，**1,616 通过、1 跳过、0 失败/错误**。App GitHub 渠道 124 套件、849 项均通过。唯一跳过是既有 `PdfCorpusTest.extractCorpus`，本机无 PDF 语料；复用报告不代表每次重复执行所有用例。
- Lint 已执行，**全量 Lint 未通过**：此前已有 60 个错误，本次对照旧报告未新增错误；不是以抑制或基线绕过。最终数量、逐项差异和完整日志在本批 `LINT-COMPARISON.json`、`lint.log` 与 `lint-results-githubDebug.*`。Compose 修饰器工厂提示来自此前阶段 C；SharedPreferences 同步 commit 提示保留，因为副作用回执必须检查写盘结果。
- `git diff --check` 通过；Manifest 单一 queries、系统权限保护 listener、READ_SMS/SEND_SMS、无 RECEIVE_SMS/QUERY_ALL_PACKAGES 已核对，并检查最终 APK 权限。
- 命令速查完整输出在本批 `QUICKCHECKS.txt`；`SENSING-AUDIT.json` 确认三个策略/风险注册，轮询投影仅 ID/address、升序最多 50，感知文件没有正文日志调用。调度中 `smsPollingEnabled()` 统一封装 read+poll 条件，覆盖 runDue 早退、dispatch、cancel、候选时间；不是机械重复三次字面量判断。

## 产物

目录：`artifacts/3.1.2-work.1/f015-sensing-11-12-debug-20261110/`。

- APK：`RikkaHub.Work-3.1.2-work.1-arm64-v8a-debug.apk`，66,444,010 bytes（63.37 MiB），包名 `me.rerere.rikkahub.debug`，显示名 `RikkaHub Work`，版本 `3.1.2-work.1 / 201`，仅 arm64-v8a、可调试。SHA-256：`9beddd62096e3aab78081ed31b8e65e67147817ba224d1c32b135630d19992d8`。
- APK ZIP CRC、v2 签名、原 21 图标源文件、20 张 APK 图标 PNG 与此前已验包比对、DeepSeek wasm 完整性均通过。证书 SHA-256：`853f0d3247da0e4aa363e821042b5cc43f253e6069f778b56c09bbd538d2588d`。
- 源码：`RikkaHub.Work-3.1.2-work.1-source.zip`，包含当前完整源代码、新增测试、跟踪文档、子模块和根 DELIVERY；排除 APK、构建/缓存、.git、参考仓库、本机配置与签名密钥。最终条目数、大小、hash、UTF-8 文件名/权限及逐文件一致性，以 `SOURCE-VERIFICATION.json`、`SOURCE-MANIFEST.json` 与 `SHA256SUMS` 为准，避免在归档内循环写自己的 hash。

## 待维护者实机验证

1. 脉搏配置 → 工具：三开关初始为关；开启哪个只请求对应权限，拒绝不回弹。系统页返回状态自动刷新，不反复跳转；应用选择器图标/勾选和号码多项输入可用。
2. 读取通知只返回所选应用内容，白名单外只计数；撤销后重新勾选不能恢复旧内容。自身/常驻/组摘要不入队，关闭后清空。
3. 普通短信与未读筛选、号码白名单、长正文截断；用自造验证码验证整条屏蔽（不要把真实验证码当测试数据）。特殊验证码格式仍可能漏判。
4. 只开短信轮询和保活、关闭心跳/任务：首次不报告旧短信，后续新短信只告知数量和发件人；息屏唤醒/活跃时段边界由实际 ROM 验证。
5. 默认审批档及完全允许档请求发送：均先显示收件人/正文，可展开长正文；拒绝不发送，等待时不启动自动新轮次。主动授权发送后核对运营商实际结果及分片，勿以 sent:true 当送达证明。
6. 普通聊天/群聊没有这些工具；DeepSeek Web 不自动获得新能力；通知、运行记录和自动报告不泄露感知正文。覆盖安装、进程重启和通知监听绑定仍需验证。

**本批没有运行 adb、模拟器或真机，也没有读真实通知、收发短信或实际授予权限。** 编译、JVM 测试与静态分析不能代替这些设备验收。

---

# F015 阶段 C 收尾 — 3.1.2-work.1 / 201（ARM64 GitHub Debug）

交付日期：2026-11-10。采用新版任务包 `RikkaHub Work-F015阶段C收尾-20261109(1).zip`，完成阶段 C「好看档」剩余的扩展管理收窄与动效打磨；版本保持 `3.1.2-work.1 / 201`，不构建 Release，不提交、打 tag、推送或发布。

## 改动

- 扩展选择器增加可选 Tab 白名单；脉搏仅留「快捷消息」「技能」，普通聊天四项及群组三项行为不变。脉搏选择通过既有 assistant facade 写入 `PulseConfig`，不修改普通助手配置。
- 脉搏抽屉底部提供「脉搏配置、运行日志、收藏、统计、设置」五项；配置入口落默认页，状态栏仍进入心跳页。移除中间重复的设置和日志按钮，增加最多五条最近心跳行。
- 心跳摘要为空时显示结果状态；`FAILED` 与 `FAILED:...` 均使用错误语义色。状态文本淡入淡出、不位移、不循环；今日 token 与金额数字按动画值格式化，金额显示两位小数。
- 已有带 key 的活动记录、运行记录、心跳、任务和监视器动态列表使用 `animateItem()`；未给静态行增加动画。无新增 strings、权限、依赖或 Room schema。
- 脉搏页面硬编码颜色与散值字号扫描结果为 0；资源仍只有英文、简中、繁中（`values-night` 是夜间主题资源）。

## 参考与测试

- 脉搏相关新增遵循既有独立 runtime/配置边界；检查 Kai 固定版本的 `HeartbeatSection`/心跳设置组织，以及 PalmClaw 固定版本的统一 runtime 心跳入口。未复制参考仓库代码；固定版本、许可证及更广的采用范围见 `docs/f015-keepalive-reference-review.md`。
- 执行 `./gradlew test :app:assembleGithubDebug --offline --console=plain` 成功；保留的最后一次增量核验日志为 `BUILD SUCCESSFUL in 1s`，414 项任务，10 项执行、404 项复用。按本轮任务图汇总 XML 报告，去除 Play/GitHub 重复及旧无渠道 App 报告后为 238 个测试套件、1,601 项：1,600 通过、1 项既有 PDF 语料测试因本机缺少语料而跳过，0 失败、0 错误。App GitHub 渠道 121 套件、833 项通过；复用的报告不表示本次重新执行了每个用例。
- `git diff --check` 通过；状态筛选测试覆盖普通聊天/群组/脉搏，持久化测试覆盖脉搏技能与快捷消息选择；测试未代替视觉/设备验收。

## 产物

目录：`artifacts/3.1.2-work.1/f015-stage-c-finish-debug-20261110/`。

- APK：`RikkaHub.Work-3.1.2-work.1-arm64-v8a-debug.apk`，65,188,413 bytes；包名 `me.rerere.rikkahub.debug`，显示名 `RikkaHub Work`，`3.1.2-work.1 / 201`，仅 `arm64-v8a`，可调试。SHA-256：`325c74c3dd88b7959514d476d684bdd977f392c1e4777c10a03c973979d4e5bd`。APK v2 签名与 ZIP CRC 通过；证书 SHA-256：`853f0d3247da0e4aa363e821042b5cc43f253e6069f778b56c09bbd538d2588d`。桌面/关于页身份图标资源与上一批 APK 完全一致。
- 源码 ZIP：`RikkaHub.Work-3.1.2-work.1-source.zip`，包含本地当前源码、测试、文档及根 `DELIVERY.md`；排除 APK、`.git`、构建/缓存、密钥和本机配置。最终大小、条目数和 SHA-256 以本批 `SHA256SUMS` 为准。
- 本批未运行 Android 设备/模拟器或 adb，未录制任务书要求的 10 秒设备屏幕；状态淡变、抽屉五按钮布局及列表插入效果待维护者安装此 APK 后验收。

---

# 顶栏玻璃强度回调 — 3.1.2-work.1 / 201（第二轮 ARM64 GitHub Debug）

交付日期：2026-11-09（Asia/Shanghai）。依据维护者新发的《给Codex的说明·顶栏玻璃强度回调》及《修正单·顶栏玻璃强度回调》继续本轮 UI 修正；版本仍为 `3.1.2-work.1 / 201`，基于 `master` / `18c372d58f77da7c8a1c1f59042993e01be2bd5b` 与此前未提交增量。上一轮 APK/源码保持原目录，新产物放独立批次。

## 本批改动（两个产品文件）

- `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` 第 535、540、1019、1023 行：groupTopStart/groupTopEnd/topStart/topEnd 四处参数从折射 0.22 / 着色 0.08 / 模糊 8dp 回调为 **0.60 / 0.16 / 2dp**。普通聊天与脉搏共用后两处，群聊使用前两处。
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatTopBarCapsule.kt` 第 92 行：Surface 的透明色判断使用 `effectiveDisplaySetting.enableBlurEffect`，与 Haze 使用同一份有效配置；第 90 行的 `forceLiquidGlass` 时 `border=null` 保持。
- 输入区域两处初始化与默认折射 1.0 / 着色 0.18 / 模糊 0 保持；没有修改 LiquidGlassRegion 字段、宿主层级、最多三个实例、透明门槛、熔断或回退逻辑。源码快照对比确认除这两个产品文件和本批文档外无新增修改，保留此前 F015/F018 及系统排查成果。

`rg -n 'refractionScale = 0.60f' app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` 输出：

```text
535:                                refractionScale = 0.60f, tintAlpha = 0.16f, blurRadiusDp = 2f))
540:                                refractionScale = 0.60f, tintAlpha = 0.16f, blurRadiusDp = 2f))
1019:                        refractionScale = 0.60f, tintAlpha = 0.16f, blurRadiusDp = 2f))
1023:                        refractionScale = 0.60f, tintAlpha = 0.16f, blurRadiusDp = 2f))
```

## 验证结果

- 本轮执行 `./gradlew :app:testGithubDebugUnitTest :app:assembleGithubDebug --offline --console=plain`，`BUILD SUCCESSFUL in 17s`；325 项任务，18 个执行、307 个复用。App GitHub：120 套件、828 项，全部通过，无失败/错误/跳过；未为三个常量新增镜像式测试。
- 本轮未重跑全工程测试和 Lint。交付目录 TEST-RESULTS 标明其他模块/Play 复用上一轮报告，不冒称全工程重新执行；`previous-lint.log` 与 `previous-lint-results-*` 是上一轮 60 errors / 322 warnings / 4 hints 的历史报告，本轮没有新的 Lint 通过结论。
- `git diff --check` 通过；四处新参数检索命中，Surface/Haze 有效配置一致；输入文件与原生宿主本轮未变。三语各 2,444 key、原始21个身份资源、APK内20张PNG、数据库33/schema、WASM与上一轮约束均核验保持。
- APK 的包名、名称、版本、ARM64、可调试标记、v2签名与 ZIP CRC 已核验。没有运行设备、模拟器、真实账号或 adb。

## 产物

新目录：`artifacts/3.1.2-work.1/topbar-glass-strength-debug-20261109/`。

- `RikkaHub.Work-3.1.2-work.1-arm64-v8a-debug.apk`：63,911,256 bytes（约 60.95 MiB）；`me.rerere.rikkahub.debug`、`RikkaHub Work`、`3.1.2-work.1 / 201`，仅 ARM64。SHA-256：`06f052c8da04eed84f9c042233cc7abcf263b06e59d267beaf0f4da2bae71553`。
- Debug 证书 SHA-256 仍为 `853f0d3247da0e4aa363e821042b5cc43f253e6069f778b56c09bbd538d2588d`，v2 校验通过。
- `RikkaHub.Work-3.1.2-work.1-source.zip` 包含当前未提交源码、根 `DELIVERY.md` 和检查记录；排除 `.git`、构建/缓存、APK/交付目录、参考仓库、密钥/签名与本机配置，保留 UTF-8 文件名、可执行权限与符号链接。逐文件 SHA/CRC、条目数与大小见 SOURCE-VERIFICATION/SOURCE-MANIFEST，最终产物哈希见 SHA256SUMS。
- 本目录附当前 `build-test.log`、APK-VERIFICATION、SOURCE-AUDIT、UI-ENTRY-AUDIT，以及明确标注复用范围的测试/历史 Lint 记录。

## 安装验收

**先把消息列表往上滚一点，再看顶栏玻璃。** 顶栏仅在 `listScrolledUnderTopBar` 后显示背景；位于列表最顶部时透明、没有玻璃属于既有预期。验证聊天/群聊/脉搏滚动后的折射与背后文字、无硬描边、模糊档原描边、输入框强度不变。若滚动后仍不可见，才按新修正单的诊断日志/透明 Surface/层级顺序定位；本轮没有把未执行的真机诊断或临时 tint=1 实验写成通过。

本批没有 Release、提交、标签、推送或发布。视觉强度仍待维护者安装确认。

---

# 玻璃与页面入口回归修正 — 3.1.2-work.1 / 201（ARM64 GitHub Debug）

交付日期：2026-11-09（Asia/Shanghai）。版本仍为维护者指定的 `3.1.2-work.1 / 201`；基于 `master` 合并提交 `18c372d58f77da7c8a1c1f59042993e01be2bd5b` 与既有 F015/F018 未提交改动。本批完成顶栏玻璃/输入框修正，并按维护者要求系统排查按钮、入口和功能接线；旧交付目录保留，以下是新的构建产物。

## 本批修正

- 原生玻璃动态添加后将输入前景置顶，修复输入框被遮挡；真实顶栏和输入框置于透明前景，消息背景仅留等高顶栏占位，避免玻璃采样和覆盖按钮自身。
- 普通聊天、群聊、脉搏统一顶栏玻璃。每模式最多输入/左胶囊/右胶囊三个区域；群聊设置、新建和更多共用右胶囊。顶栏取消原生描边并降低折射，输入玻璃保留原参数；脉搏原有操作回调保留。
- 透明时仍缓存胶囊位置，淡入后能正确建立玻璃；参数/布局仅变化时刷新，透明时移除区域，熔断隐藏全部玻璃。原生不可用时使用 BLUR 渲染副本，不修改用户保存的 LIQUID 配置。
- 群聊收藏接通当前会话的实际存储；切换会话尚未对齐时隐藏无效回调；群组不存在时不显示无效思考预算操作。
- 音量键翻页读取实时开关、比例和遮挡高度，键盘弹出后可见高度最低为 0，避免方向反转。
- 修正附件菜单固定行计数，工作区缓存随归属更新；长错误列表限高并可滚动，卡片按 ID 保持身份，复制/关闭及不自动消失的能力拒绝行为保留。

## 检查覆盖与保留项

完整记录见 `docs/ui-regression-audit-20261109.md`。模式扫描覆盖 298 份 UI Kotlin（其中页面 156 份），并对照上游合并影响的 56 个 UI 路径重点检查；69 个 Screen 全部有 Nav3 entry，无缺失或重复。四处空 onClick 经核对为长按/尾部删除/@Preview 的有意行为，未随意改造成新功能。

重点核对三种聊天模式、发送/停止/队列、消息操作/导出、附件、模型与供应商、搜索、群组、历史、浏览器与浮窗、工作区和错误/审批链。保留 ask_user HITL、DeepSeek 默认只读及自定义权限、群聊 browser 过滤、空记忆首次写入、工作区归属、F018 确认持久化及后台保活；未恢复应用层生成超时或休眠取消。

原始 launcher/about 身份图标、英文/简中/繁中和数据库33保持；不把模式扫描、源码接线核对或 JVM 测试当作全部控件的设备交互验收。

## 自动验证

- 最终 `./gradlew test :app:assembleGithubDebug --offline --console=plain`：`BUILD SUCCESSFUL in 17s`，414 个任务，23 个执行、391 个复用；日志为本目录 `build-test.log`。
- App GitHub/Play 各 120 套件、828 项测试，全部通过。去除 Play 重复渠道后全工程 237 套件、1,596 项：1,595 通过、1 项已有语料缺失跳过，0 失败/错误。其他模块的 UP-TO-DATE 结果明确为复用；新增回归检查原生液态不可用时回退模糊且不改变存储设置。
- `git diff --check` 通过；三语各 2,444 个字符串 key 且一致，无其他语言字符串目录。21 个原始身份源资源逐字节一致，APK 中 20 张 PNG 解码后像素一致。数据库33/schema保持、DeepSeek WASM 与源码一致。
- 最终 `:app:lintGithubDebug` 仍为 `BUILD FAILED in 3m 47s`：60 errors、322 warnings、4 hints。相比上批 errors 61→60；剩余为 58 条 LocalContextGetResourceValueCall、1 条 ContextCastToActivity、1 条 NonObservableLocale。60 条触发表达式均存在于合并提交 `18c372d5`，未新增 baseline 或屏蔽；这是表达式对照，并非对合并提交另跑 Lint。构建和单测通过不代表 Lint 通过。

## 本批产物

新目录：`artifacts/3.1.2-work.1/glass-ui-regression-debug-20261109/`。

- `RikkaHub.Work-3.1.2-work.1-arm64-v8a-debug.apk`：63,911,255 bytes（约 60.95 MiB），包名 `me.rerere.rikkahub.debug`，应用名 `RikkaHub Work`，`3.1.2-work.1 / 201`，可调试，仅 ARM64。
- APK SHA-256：`8b94be04fa94d3f58cb92ebdab08b4741af895877077b42ec7a8d17a4879976b`。v2 签名通过，Debug 证书 SHA-256 `853f0d3247da0e4aa363e821042b5cc43f253e6069f778b56c09bbd538d2588d`，与前批一致。
- `RikkaHub.Work-3.1.2-work.1-source.zip`：包含当前未提交源码、新增文件、本批检查报告和根 `DELIVERY.md`；排除 `.git`、构建/缓存、APK、交付目录、参考仓库、签名及本机配置，保留可执行权限/符号链接/UTF-8 文件名。最终大小、条目和逐文件校验见 `SOURCE-VERIFICATION.json`、`SOURCE-MANIFEST.json`，两产物哈希见 `SHA256SUMS`。
- 本目录另含 `APK-VERIFICATION.json`、`SOURCE-AUDIT.json`、`UI-ENTRY-AUDIT.json`、`TEST-RESULTS.json`、`LINT-BASELINE-COMPARISON.json` 和构建/Lint 日志；APK 不混进源码 ZIP。

## 设备待验与边界

本轮未运行 Android 设备/模拟器或真实账号，尚需安装验证：三模式在液态/模糊/关闭下的按钮和输入、前景空白触摸透传、消息滚动/长按、键盘/旋转/抽屉后的定位、玻璃淡入淡出与性能、群聊收藏、错误卡片和审批、浏览器拖动后的点击命中，以及前批 F015/rootfs 的系统行为。细项见本批检查报告。

本批未构建 Release、未提交、打 tag、推送或发布。不得据此宣称不存在其他 UI 或设备问题。

---

# 上游 3.1.2 / F015 修正 / F018 — 3.1.2-work.1 / 201（ARM64 GitHub Debug）

交付日期：2026-11-09（Asia/Shanghai）。本批采用维护者指定的 `3.1.2-work.1 / 201`，覆盖任务包建议的 202。当前 `master` 合并提交为 `18c372d58f77da7c8a1c1f59042993e01be2bd5b`；其父为 F015 基线 `4b336d85` 与上游 3.1.2 `fe5a28f9a25fb6267065881d7738dd909ad9e51e`。F015/F018 修正为该提交之后的未提交增量。

## 完成范围

- 合并上游 3.1.2 的消息渲染与公式/删除线改进、新收件箱、消息概览、浮动顶栏、远程跳板机与后台 Shell、世界书排序、导出文件、模型搜索及余额查询等更新，保留 Work 既有群聊/脉搏与 F001–F017。完整变更已写在根 `CHANGES.md`，合并统计及 29 个冲突路径的处理记录见 `docs/merge-3.1.2-review.md`。
- F006 顶栏左右胶囊与输入框共用原生液态宿主，最多三个稳定区域，透明时停止录制；保留原模糊回退与熔断。桌面/系统应用图标、关于页应用标志继续使用最初版本，其余图标正常同步。
- F015 八项修正：主/锁屏常驻通知时间戳、正确的设置加载保护及加载后配置响应、空历史 synthetic 触发消息、幂等恢复/自动创建英文 Pulse 文件工作区、详情页主动安装 Linux、统一三档工具审批、1–240 分钟心跳数字输入及成本提示、五 Tab 与独立峰谷计价。监视器仍为 10–240 分钟，不恢复应用层生成超时或休眠取消。
- 峰谷模式默认关闭，每段独立星期、时间、输入/输出/缓存单价和倍率；重叠禁止保存、相接允许、未覆盖仅提示。按调用当日星期匹配跨午夜的早/晚段。峰谷估算使用最近最多 200 条运行日志，普通计价仍用今日汇总；普通输入先扣缓存，避免重复计算。两套配置独立持久化。
- F018：普通聊天记住上次确认的压缩保留条数，包括 0；默认 32，取消不写回，确认后先保存再压缩，失败仍记住。新增 Settings 字段、PreferencesStore 键和 SettingsFields 映射一并接通；群聊保留原有独立配置。

## 与任务书不同之处

源码中 `Settings.dummy().init == true` 表示尚未加载，`awaitLoaded()` 等待 false。任务书对 §2/§4 的解释方向相反；本批保留正确的停止条件，Runtime 同时监听 init 和 pulse，在加载完成后启停服务、重排与刷新。未照抄会导致占位阶段误停的条件。

为保护已有工作区，自动创建前先恢复绑定或复用 owner=pulse；Pulse 名被其他工作区占用时使用英文后缀，未删除或覆盖其他工作区。工作区仅创建文件区及补缺模板，不自动安装 Linux；安装入口复用既有详情页的选择、校验及回滚流程。

F015 涉及的实现再次参考 Kai `ed6a3e0c48e10de9e2852e939fa4531975352bff` 与 PalmClaw `75a42aeda0e580ebad0de4a38c00c94146148366` 的生命周期和心跳入口；采用范围记录在 `docs/f015-keepalive-reference-review.md`。未把参考仓库源码复制进产品包。

## 自动验证

- 最终命令：`./gradlew test :app:assembleGithubDebug --offline --console=plain`。结果 `BUILD SUCCESSFUL in 1m 21s`，418 个任务，28 个执行、390 个复用；完整日志为交付目录 `build-test.log`。
- 去除 App Play/GitHub 重复渠道后：237 个套件、1,595 项测试，0 失败、0 错误、1 跳过，即 1,594 项通过。App GitHub 为 120 套件/827 项，全通过；Play 同样 120/827。唯一跳过为已有 `document.PdfCorpusTest.extractCorpus` 缺少本地语料触发 Assumption，未新增 Ignore。没有测试的模块不虚计；过时 App `testDebugUnitTest` 报告不计。完整分模块结果见 `TEST-RESULTS.json`，Gradle 复用结果未冒称全部重跑。
- 新回归覆盖空历史临时消息与不重复 system 注入、峰谷跨午夜/相接/星期/全天/编辑冲突/时间输入/周覆盖、缓存计价及普通价格保持、压缩条数默认 32 与 0/7 跨重启、两套计价配置跨重启保存。
- `git diff --check` 通过；三语 XML 合法、各 2,444 个字符串 key 且完全一致；仅保留英文、简中、繁中。21 个应用身份源资源逐字节匹配初始 commit `643191229dcbeb4972e3772986818605633ace92`，APK 内 20 张 PNG 解码后像素一致。关于页通过 AppLogo 读取同一 launcher 资源。
- 数据库版本仍为 33，`33.json` 与 `4b336d85` 完全相同；保留 `tool_approvals` 字段/列、越界写保护、ask_user HITL、F009 权限边界和 ConversationInbox annotations 参数/赋值。

### Lint 限制（未通过）

`./gradlew :app:lintGithubDebug --offline --console=plain` 结果 `BUILD FAILED in 3m 30s`，61 errors、322 warnings、4 hints。已修正本轮涉及的三处资源/Locale 问题。剩余 errors 为 58 条 LocalContextGetResourceValueCall、2 条 ContextCastToActivity、1 条 NonObservableLocale。

逐条对照后，61 条触发表达式均存在于合并提交 `18c372d5`；这是源码表达式对照结果，没有在该提交另起一轮 Lint，也没有添加 baseline 或屏蔽规则。完整报告及 `LINT-BASELINE-COMPARISON.json` 随交付保存。Debug 编译/打包与 JVM 测试成功不代表 Lint 通过。

## 产物与校验

交付目录：`artifacts/3.1.2-work.1/upstream-3.1.2-F015-F018-debug-20261109/`。

- `RikkaHub.Work-3.1.2-work.1-arm64-v8a-debug.apk`：62,861,338 bytes（约 59.95 MiB）；包名 `me.rerere.rikkahub.debug`，名称 `RikkaHub Work`，版本 `3.1.2-work.1 / 201`，可调试，仅 `arm64-v8a`。
- APK SHA-256：`2b8331a7c073b81899dc25cee6fd1d3dfbfad580a02884c3a8c9ec135df8233b`。v2 签名通过；Debug 证书 SHA-256 `853f0d3247da0e4aa363e821042b5cc43f253e6069f778b56c09bbd538d2588d`，与此前 Debug 一致。ZIP CRC 通过，DeepSeek WASM 与源码一致，未包含 rootfs 压缩包或签名密钥。
- `RikkaHub.Work-3.1.2-work.1-source.zip`：包含当前未提交源码、新增文件和根 `DELIVERY.md`；排除 `.git`、构建/缓存、APK、交付目录、参考仓库、签名及本机配置。保留可执行权限和符号链接，所有条目显式标 UTF-8。
- 源码 ZIP 的条目数、大小、逐文件哈希和校验结果见外部 `SOURCE-MANIFEST.json`、`SOURCE-VERIFICATION.json`；最终两产物 SHA-256 见外部 `SHA256SUMS`，避免把源码 ZIP 自身哈希循环写进 ZIP。另提供 `APK-VERIFICATION.json`、`SOURCE-AUDIT.json`、测试/Lint 日志和合并统计。

## 设备待验

本轮未操作 Android 设备/模拟器或真实账号，以下由维护者安装 APK 验收：

1. 冷启动/服务重建保持正确状态，开关关闭后能停止；两处常驻通知显示更新时间，修改配置后任务按新配置重排。
2. 新建空历史脉搏会话可运行心跳与计划任务；临时触发消息不进入历史，无重复指令；未回复用户消息的跳过和间隔语义保持。
3. 文件区自动出现且名称为英文 Pulse，反复进出不重复创建，已有模板不覆盖；未装 Linux 时文件可用，安装/命令监视器及失败回滚走原流程。
4. 三档审批、越界写和 ask_user 正常，DeepSeek Web 默认只读及自定义工具开关不被绕过；后台 Shell 输出和停止任务符合风险级。
5. 五个设置 Tab、1 分钟间隔输入、峰谷时段编辑/冲突/切换存储，以及压缩确认/取消/失败后的 0/7 条持久化。
6. 群聊消息归属、排队输入、压缩与普通聊天隔离正常；bettertext 长消息、公式与顶栏概览可用。
7. 三个液态区域的对齐、淡出、长列表性能和后台息屏保活；JVM 与静态检查不能替代这些设备行为。

本批收尾未构建 Release、未再次提交、打 tag、推送或发布；没有进行设备、真实登录态或供应商联机操作。

---

# F015 阶段 B / F009 补充六 — 3.1.1-work.3 / 201（ARM64 GitHub Debug）

交付日期：2026-11-06（Asia/Shanghai）。源码基线：`master` / `a5f2a4cb9f12bf06174cbc0b40702ba8d6f68d9c`；本批未提交。

## 本批范围

- 完成 F015 阶段 A+B（包 10/7/8/9，并含心跳提示词、常驻通知自证及心跳恢复/未回复跳过修正）和 F009 补充六。版本保持 `3.1.1-work.3 / versionCode 201`。
- F015 包 10 提供 `owner=pulse` 独立工作区、F017 安装流程接入、六个不覆盖已有文件的简中模板，以及按工作区就绪状态开放的文件/命令能力。
- 包 7 提供 BATTERY、URL、COMMAND 监视器；每轮最多采样 5 个、间隔限制 10–240 分钟；首次采样只建立基线，内容无变化不调用模型。URL 响应上限 256 KiB，命令输出保留上限 64 KiB，失败记录并尝试产生失败报告。工作区未就绪时 COMMAND 监视器禁用。
- 包 8 提供独立脉搏通知、失败通知、最多保留 5 个自有命名 TXT 报告及最近 20 条活动。聊天生成失败通知由现有独立失败事件分支处理，不依赖成功消息预览。
- 包 9 展示脉搏会话当日 token 用量、用户填写的人民币单价/倍率估算与最多 200 条运行日志。未改 UsageRecorder 或 Room schema，不记录运行日志正文。供应商对缓存 token 口径不同，估算按 `max(promptTokens-cachedTokens, 0)` 计普通输入，缓存部分按缓存单价单列，并标记为估算。
- F009 DeepSeek Web 增加默认关闭的自定义工具总开关及九个独立权限项；开启前需在当前语言逐字输入风险确认。工具注册、MCP 前缀、clipboard action、协议/输出验证、本地预检和提示词都按权限生效；总开关关闭时保持原只读能力。
- 保留仅英文、简体中文、繁体中文三种应用语言；保留维护者指定的最初应用图标。未构建 Release，未提交、打 tag、推送或发布。

## Kai / PalmClaw 参考

本批复核 Kai `ed6a3e0c48e10de9e2852e939fa4531975352bff` 与 PalmClaw `75a42aeda0e580ebad0de4a38c00c94146148366` 的调度、临时会话、claim/租约与生命周期边界；保留 Kai 式临时轮次和独立调度结构、PalmClaw 式单一 claim/租约保护。本批不复制其源码文件，也不移植不适用于本应用的常驻网关或自动恢复方案。完整文件坐标和采用边界见 `docs/f015-keepalive-reference-review.md`。

## 验证

- 命令：`./gradlew :ai:testDebugUnitTest :app:testGithubDebugUnitTest :workspace:testDebugUnitTest :common:testDebugUnitTest :app:assembleGithubDebug --offline --no-daemon --max-workers=4 --console=plain`
- 结果：`BUILD SUCCESSFUL`，耗时 1 分 42 秒；360 项任务中 18 项执行、342 项为 up-to-date。AI、Workspace、Common 测试任务复用 Gradle 已有结果；App GitHub Debug 单测与目标变体构建成功。
- JUnit 报告：AI 47 套件/362 项，App GitHub Debug 117/800，Workspace 9/72，Common 3/9；合计 176 套件、1,243 项，0 失败、0 错误、0 跳过。
- `git diff --check` 通过。三语字符串 key 集各 2,384 项且完全一致；资源目录仅 `values`、`values-zh`、`values-zh-rTW`（`values-night` 仅为主题限定资源）。
- APK 经 Android SDK 工具核对：包名 `me.rerere.rikkahub.debug`，版本 `3.1.1-work.3 / 201`，仅 `arm64-v8a`，62,469,585 bytes；APK Signature Scheme v2 验证通过，证书 SHA-256 `853f0d3247da0e4aa363e821042b5cc43f253e6069f778b56c09bbd538d2588d`；ZIP CRC 与 DeepSeek WASM 条目检查通过。SHA-256：`3e790640f2fe94857daa48912bbbafd9e3809a85743b14fdb3df83d22169c4e0`。
- 监视器失败路径、工具权限隔离、报告保留规则和源码打包排除项已做静态检查。未执行 Android 真机/模拟器验收；后台保活、系统通知、工作区安装、UI 与供应商联机行为需要维护者安装后验证。

## 交付文件

交付目录：`artifacts/3.1.1-work.3/F015-stage-B-F009-supplement6-debug-20261106/`。

- `RikkaHub.Work-3.1.1-work.3-F015-stage-B-F009-supplement6-arm64-v8a-debug.apk`
- `RikkaHub.Work-3.1.1-work.3-F015-stage-B-F009-supplement6-source.zip`（含根 `DELIVERY.md`；排除 `.git`、构建缓存、APK、签名/本机配置及交付目录）
- 源码 ZIP 含 2,226 个文件，展开约 73.4 MB；`SHA256SUMS`、`SOURCE-MANIFEST.json`、`SOURCE-VERIFICATION.json`、`APK-VERIFICATION.json`、`TEST-RESULTS.json` 均在交付目录外部，避免源码 ZIP 自引用。源码 ZIP CRC、UTF-8 文件名、逐文件哈希及敏感密钥模式扫描均通过。

未执行设备操作、commit、tag、push 或 GitHub 发布。

---

# F015 包 0 参考复核交付 — 3.1.1-work.3 / 201（Debug）

日期：2026-11-03（Asia/Shanghai）。基线：master `a5f2a4cb`；本批未提交。

按维护者要求拉取并参考 Kai / PalmClaw，在包 0 初版基础上继续完善。仍只交付 ARM64 Debug，不构建 Release，不提交/tag/push/发布，不操作设备或账号。

## 相对初版的补强

- 生成首次开始立即获取电源锁；只要仍有活动生成，重复开始/结束其他任务不重建回调、不推迟下一次续租。
- 空闲结束后再次开始可立即获取新租约；旧回调在停止、重启后均不能复活旧循环。
- 将旧锁释放和新租约获取分别处理异常，释放失败也会继续尝试获取，获取失败不终止后续续租重试。
- 保留包 0 已完成的全部应用层生成 watchdog 移除、原 Job/finally 归属和 UI 耗时显示。没有新增 5 分钟空闲、2 小时累计运行或休眠自动停止限制。
- 保留 Android 原有 `dataSync` 服务类型和系统停止处理；未移植参考仓库的常驻网关、心跳调度或自动恢复。5 分钟仅为单次 WakeLock 租约，续租间隔 60 秒，不是生成上限。

参考的固定 commit、具体源码路径和采用范围见 `docs/f015-keepalive-reference-review.md`：Kai `ed6a3e0c48e10de9e2852e939fa4531975352bff`；PalmClaw `75a42aeda0e580ebad0de4a38c00c94146148366`。两份参考仓库位于 `/home/li/CDX/rikkahub-references/F015/`，不包含在产品源码 ZIP 内。

## 本批验证与产物

- 执行 `:app:testGithubDebugUnitTest :workspace:testDebugUnitTest :common:testDebugUnitTest :app:assembleGithubDebug --offline --no-daemon --console=plain --max-workers=4`，`BUILD SUCCESSFUL in 1m 13s`，351 个任务，19 个执行、332 个复用。
- App 102 个套件 / 719 个用例，本轮实际执行；Workspace 9 / 72、Common 3 / 9 复用已通过报告。共 800 个用例，0 失败、0 错误、0 跳过。续租专项由 7 项增加为 11 项，覆盖即时获取、保持期限、频繁多任务更新、空闲后启动、晚到回调及回调内停止重启。
- 静态边界审计通过：生成链路无旧 watchdog/进展 touch/应用层时限；common、数据库、三语资源、应用身份图标、F017 均未改动；三份字符串各 2,224 项。`git diff --check` 通过。
- Debug 包名仍为 `me.rerere.rikkahub.debug`，版本 `3.1.1-work.3 / 201`，仅 `arm64-v8a`，大小 60,817,397 字节；v2 签名与 ZIP CRC 通过，DeepSeek WASM 存在，旧 watchdog 类缺席。SHA-256 为 `b2582530610c2e9caaf27beb71bd50681a7819e6964b6ef1cb522627af7585dd`。原始应用图标与 Debug 签名保持；证书 SHA-256 为 `853f0d3247da0e4aa363e821042b5cc43f253e6069f778b56c09bbd538d2588d`。

交付目录：`artifacts/3.1.1-work.3/F015-package0-reference-review-debug-20261103/`。

- APK：`RikkaHub.Work-3.1.1-work.3-F015-keepalive-arm64-v8a-debug.apk`。
- 源码：`RikkaHub.Work-3.1.1-work.3-F015-keepalive-source.zip`，包含根 `DELIVERY.md` 和参考复核说明；排除 `.git`、APK、参考仓库、构建/缓存、签名和本地配置。
- 证据：`build.log`、`TEST-RESULTS.json`、`APK-VERIFICATION.json`、`SOURCE-AUDIT.json`、`SOURCE-MANIFEST.json`、`SOURCE-VERIFICATION.json`、`SHA256SUMS`。

实机未测。请验证长时间生成/息屏后继续、多任务结束一条不影响另一条、结束全部任务后锁释放、空闲后再次生成；JVM 测试不替代系统 WakeLock 与厂商省电行为的设备验收。

---

# F015 包 0 初版记录 — 3.1.1-work.3 / 201（历史 Debug）

日期：2026-11-03（Asia/Shanghai）。基线：master `a5f2a4cb`（F017 已提交）；本批暂未提交。

## 执行依据与覆盖关系

任务包：`RikkaHub Work-F015包0-后台保活收尾-20261103.zip`。维护者审阅后明确取消全部应用层生成超时及其检查 timer，覆盖附件的 5 分钟空闲 / 2 小时清醒限制方案；随后授权开始。任务包的旧版本基线和单独 commit 要求不覆盖维护者指定的 Debug 阶段与手动推送安排。

本批 versionName 为 3.1.1-work.3，Debug 阶段 versionCode 暂沿用 201；只交付 ARM64 Debug APK 和含本文件的源码 ZIP。正式发布前再确定递增 versionCode。本批不构建 Release、不自行 commit/tag/push、不操作设备或真实账号，不扩展到 F015 包 1 或包 4。

## 改动范围

- `ChatService.kt`：删除创建 watchdog、GenerationActivity 和消息进展 touch；保持 launchGenerationJob 签名，生成 block 在原 Job 内直接执行，finally 继续释放前台服务。UI 耗时和 common/RequestTiming 不变。
- 删除 `GenerationWatchdog.kt` 与只验证其旧语义的 `GenerationWatchdogTest.kt`；不存在 20 分钟 / 5 分钟 / 2 小时应用层自动停止规则，也没有为这些规则运行的检查定时器。保留历史语言 key，但不再在生成链路引用。
- `ChatGenerationForegroundService.kt` 与新增 `GenerationWakeLockRenewal.kt`：5 分钟锁租约、60 秒调度续租；只有前台且有活动生成才续租。重复启动只保留一个回调；旧回调不能复活停止的循环。最后任务结束、onDestroy、onTimeout、stopService 清理续租并释放锁。Android 系统服务停止回调仍正常执行，不承诺绕过系统限制。
- 新增续租调度 JVM 测试，覆盖长时间运行、无活动任务、重复启动、过时回调、停止和回调内重启。原 6 个 watchdog 测试随废弃功能移除，不以继续断言错误行为维持测试数量。
- 同步 `AGENTS.md`、`CHANGES.md`、`FEATURES.md` 与本交付记录；仅改版本名，三语资源、图标、数据库、F017、审批与 DeepSeek 只读边界保持不变。

## 验证与交付

- `:app:testGithubDebugUnitTest :workspace:testDebugUnitTest :common:testDebugUnitTest :app:assembleGithubDebug --offline --no-daemon --max-workers=4` 构建成功，耗时 7 分 44 秒；356 个任务，101 个执行、255 个复用。仅构建 GitHub ARM64 Debug，没有构建 Release。
- App：102 个套件 / 715 个用例；Workspace：9 个套件 / 72 个用例；Common：3 个套件 / 9 个用例。合计 796 个用例，0 失败、0 错误、0 跳过。App 用例从 714 变为 715（删除废弃 watchdog 的 6 个测试，新增续租的 7 个测试）；Workspace/Common 结果由 Gradle 复用已通过报告，App 本轮实际执行。
- 已核验编译后的服务包含停止状态保护；APK 中含 GenerationWakeLockRenewal，不含 GenerationWatchdog 或 GenerationActivity。源代码检查无 watchdog/进展 touch/应用层时限残留，`git diff --check` 通过。
- APK 包名 `me.rerere.rikkahub.debug`、版本 `3.1.1-work.3 / 201`、应用名 `RikkaHub Work`、仅 `arm64-v8a`、可调试；APK v2 签名与 ZIP CRC 校验通过。Debug 证书 SHA-256 为 `853f0d3247da0e4aa363e821042b5cc43f253e6069f778b56c09bbd538d2588d`，与历史阶段三 Debug 相同。
- APK 大小 60,269,220 字节；SHA-256 为 `1457f5833e48e2c7239095ba8d92422470f69e2fd099aa999362ee248ca54355`。DeepSeek WASM 存在，未包含 rootfs 压缩包或 keystore；原 launcher 源资源对照初始基线一致。英文、简中、繁中各 2,224 条字符串保持不变，没有新增语言。
- 没有进行 Android 真机/模拟器验证；不能将编译或 JVM 测试当作息屏保活、系统 WakeLock 续租和视觉效果实测通过。

交付目录：`artifacts/3.1.1-work.3/F015-package0-debug-20261103/`。

- APK：`RikkaHub.Work-3.1.1-work.3-F015-package0-arm64-v8a-debug.apk`
- 源码：`RikkaHub.Work-3.1.1-work.3-F015-package0-source.zip`，含根 `DELIVERY.md`，排除 APK、构建/缓存、`.git`、签名文件和本机配置。
- 证据：`build.log`、`TEST-RESULTS.json`、`APK-VERIFICATION.json`、`SOURCE-AUDIT.json`、`SOURCE-MANIFEST.json`、`SOURCE-VERIFICATION.json`、`SHA256SUMS`；打包后核验 ZIP CRC、逐文件内容和外部 SHA-256 清单，不把源码 ZIP 自身哈希写入 ZIP。

## 实机待验（本轮未执行）

1. 正常发送、流式返回、正常结束与手动停止均正常；并行两条生成时，结束其中一条不停止另一条的保活。
2. 长任务超过旧 20 分钟及息屏 40 分钟后，不出现由应用 watchdog 产生的停止卡片。真实连接断开仍按原错误流程呈现。
3. 生成期间用 `adb shell dumpsys power` 查 `rikkahub:generation`；隔 60～90 秒再次采样检查锁仍有效，连续观察超过 5 分钟。若系统 dump 未展示租约截止时刻，不能仅凭 tag 存在就认定续租时刻滚动。
4. 结束全部任务、停止或销毁服务后再次检查：该锁释放，随后没有续租恢复。实际系统服务超时仍遵循 Android 生命周期。

---

# F017 交付记录 — 3.1.1-work.2 / 201

实施日期：2026-11-02；交付核验日期：2026-11-03（Asia/Shanghai）。源码基线：master `4a346a03`；本批变更未提交。

依据最新 `RikkaHub Work-F017工作区Rootfs改造-20261102(1).zip`（v2.3）实施。维护者指定 Release，故不生成任务书中的 Debug 包。交付 ARM64、x86_64、Universal 三个签名 GitHub Release APK，以及含本文件的精简源码 ZIP。

交付目录：`artifacts/3.1.1-work.2/F017-rootfs-release-20261102/`。

改动：工作区发行版/档位选择、固定索引与离线缓存、多地址校验回退、架构和桌面检查、可靠替换/回滚、自定义链接和本地包安装、镜像标识展示、三语文案。完整条目见本批 CHANGES（本文件随源码包交付）。

自动检查：P1～P4 的工作区 JVM 测试和 App Kotlin 编译均通过；最终工作区 9 个测试套件、72 个用例，App 102 个测试套件、714 个用例，均为 0 失败、0 错误、0 跳过。新增用例验证坏包失败后旧环境仍为 READY、终端可用且文件保留，空环境仍为 BROKEN。GitHub Release Kotlin 编译通过。最终 Release 构建及包体验证结果见下方交付记录。

真实包检查：Ubuntu 26.04、Debian 13、Alpine 3.24 的 ARM64 base 压缩包均与索引 SHA-256 一致，并通过 Linux 主机上的实际安装器拷贝、解包、ELF 检查、配置与镜像标识读取；详细耗时和未验清单见 `docs/f017-rootfs-validation.md`。本次索引没有 amd64 镜像，x86_64 APK 会显示对应镜像暂不可用，不能将 ARM64 镜像安装到 x86_64。

未进行：设备/模拟器操作、Android PRoot 中的 apt/apk 实测、真实 UI 截图和覆盖安装；没有提交、标签、推送或 GitHub 发布。真实包在 Linux 主机上校验/解包也不代表在 Android 上运行通过。

源码 ZIP 排除 `.git`（包括子模块指针）、构建目录、缓存、APK、镜像包、签名与本机配置；保留源码、资源、子模块源文件、构建脚本与本文件。最终 SHA-256 清单放在交付目录外层，避免源码 ZIP 自引用。

## 最终构建与包体验证（2026-11-03）

- `:workspace:testDebugUnitTest :app:testGithubDebugUnitTest :app:assembleGithubRelease`：`BUILD SUCCESSFUL in 12m 1s`，836 个任务，28 个执行、808 个复用。测试复用已通过结果，Release Lint、代码/资源压缩和签名均通过。日志：交付目录 `release-build-20261103.log`。
- 三个 APK 均核验为 `me.rerere.rikkahub.plus`、`3.1.1-work.2 / 201`、不可调试；APK v2 签名及 ZIP CRC 通过。签名证书 SHA-256 为 `671f265609eb23c6c84abbfc0342b4e11dd8ba20db9714bc475c536d8e2a6d6b`，与已有 Release 相同。
- 语言仅默认英文、简体中文和繁体中文；DeepSeek WASM 存在，未打入 Rootfs 压缩包、keystore 或本机配置。桌面与关于页的应用图标仍使用最初版本。

| APK | 字节数 | 架构 |
| --- | ---: | --- |
| `RikkaHub.Work-3.1.1-work.2-arm64-v8a-release.apk` | 16,133,140 | arm64-v8a |
| `RikkaHub.Work-3.1.1-work.2-x86_64-release.apk` | 16,232,069 | x86_64 |
| `RikkaHub.Work-3.1.1-work.2-universal-release.apk` | 18,964,248 | arm64-v8a + x86_64 |

源码文件名：`RikkaHub.Work-3.1.1-work.2-F017-source.zip`。逐文件清单、源码校验报告和 APK 校验报告分别为 `SOURCE-MANIFEST.json`、`SOURCE-VERIFICATION.json`、`APK-VERIFICATION.json`；最终哈希以交付目录 `SHA256SUMS` 为准。

---

# 阶段三⑥交付记录（历史）

# 阶段三⑥三交付记录

日期：2026-10-30（Asia/Shanghai）  
版本：`3.1.0-work.1` / `versionCode 199`  
基线：阶段三⑥二交付；分支 `merge/upstream-2.5.6-f009`，HEAD `12a6ed24`（本批未提交）  
交付范围：ARM64 Debug APK + 不含 APK 的完整源码 ZIP；未发布、未推送、未打 tag、未操作设备或真实账号。

## 本批改动

| 文件 | 改动 | 理由 |
|---|---|---|
| `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | `AppRoutes()` 在 `settings.init` 占位设置期间只渲染空容器并提前返回；真实设置加载后才创建起始页和 `rememberNavBackStack` | 防止首帧读取 `Settings.dummy()` 的默认 `CHAT`，把已持久化的 `GROUP` 模式固定成聊天入口 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt` | 新增 `conversationLoaded: StateFlow<Boolean>`；会话初始化用 `try/finally` 标记完成 | 区分占位会话与真实加载完成的会话，即使初始化异常也不让状态永久卡住 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` | 模式对齐 effect 增加 `conversationLoaded` 和 `setting.init` 守卫 | 防止占位会话的当前助手被误判为普通聊天，从而覆盖持久化群组模式 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ui/CardGroup.kt` | 恢复 `switchItem` 的 `colors: ListItemColors?` 形参和向 `item` 的转发 | 该参数存在真实调用，上一批删除是错误判断 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt` | 恢复 `switchItem(... colors = itemColors, ...)`，移除本批临时裸 `Switch` 适配 | 保持设置页标准开关行的一致交互与视觉样式 |

## 红线核对

- `settings.init == true` 时不执行设置写入，也不创建导航栈；`Settings.init`、`Settings.uiMode` 类型/默认值/序列化字段未改。
- `conversationLoaded == false` 时不使用 `conversation.isGroupChat()` 做模式写回；仍无无条件 `UiMode.CHAT` / `UiMode.GROUP` 常量写入。
- 群组模式冷启动仍不创建空会话；没有可复用会话时进入 `Screen.Groups`。
- 未改 `ChatService`、`ChatToolFactory`、生成轮次逻辑、数据库、schema、依赖、语言 key 或版本号。
- 三语资源仍只有 `values`、`values-zh`、`values-zh-rTW`，每份 2,025 条；颜色 539、字号 37。

## 构建与测试

- `./gradlew :app:compileDebugKotlin --console=plain --no-configuration-cache`：通过；仅保留既有弃用和实验 API 警告。
- `./gradlew :app:testDebugUnitTest --console=plain --no-configuration-cache`：`BUILD SUCCESSFUL`；92 个测试套件、659 个用例，0 失败、0 错误、0 跳过。
- `./gradlew :app:assembleDebug --console=plain --no-configuration-cache`：`BUILD SUCCESSFUL`，24 秒；ARM64 Debug APK 已重新生成。
- APK：`artifacts/3.1.0-work.1/stage3-06-03-startup-race-20261030/RikkaHub.Work-3.1.0-work.1-stage3-06-03-startup-race-20261030-arm64-v8a-debug.apk`。
- APK 核验：applicationId `me.rerere.rikkahub.debug`；versionName `3.1.0-work.1`；versionCode `199`；仅 `arm64-v8a`；APK Signature Scheme v2 和 ZIP 完整性通过；大小 `87,868,689` 字节；SHA-256 `e59a5912f584868a241a6e19b866d6aa5d4efca3ec0710481fcbb62030f87612`。

## 源码包

- 源码 ZIP：`artifacts/3.1.0-work.1/stage3-06-03-startup-race-20261030/RikkaHub.Work-3.1.0-work.1-stage3-06-03-startup-race-20261030-source.zip`。
- ZIP 不含 APK、`artifacts/`、`build/`、本地配置、签名文件或密钥；含根 `DELIVERY.md`，保留 `.claude/skills` 与 `web-ui/AGENTS.md` 两个 Unix 符号链接，并设置 UTF-8 文件名标志。
- 本轮未执行 `git commit`、`git tag`、`git push`、GitHub 发布、ADB 安装或模拟器/真机操作。M1-M7 连续冷启动、群组模式持久化和消息归属仍待维护者安装 APK 后验证。

# 阶段三⑥二交付记录

日期：2026-10-30（Asia/Shanghai）  
版本：`3.1.0-work.1` / `versionCode 199`  
基线：阶段三⑥交付；分支 `merge/upstream-2.5.6-f009`，HEAD `12a6ed24`（本批未提交）  
交付范围：ARM64 Debug APK + 不含 APK 的完整源码 ZIP；未发布、未推送、未打 tag、未操作设备或真实账号。

## 本批改动

本修正单要求约 4 个文件，但当前源码实际存在一处 `switchItem(colors = ...)` 调用；为删除共享组件死参数而保持该调用的颜色效果，实际改动 5 个产品文件。

| 文件 | 改动 | 理由 |
|---|---|---|
| `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | 群组模式冷启动时取默认组最后一个群聊会话；无组或无会话时进入 `Screen.Groups`；聊天模式保留 `create_new_conversation_on_start` 原逻辑 | 模式决定冷启动入口，且群组模式不得自动创建空群聊 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` | 在渲染分流前增加幂等 `LaunchedEffect`，按 `conversation.isGroupChat()` 将 `Settings.uiMode` 同步为 `GROUP` 或 `CHAT` | 打开列表、通知、搜索、收藏或深链会话时，抽屉模式与实际会话类型一致 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/DeepSeekWebLogin.kt` | 恢复去除 `; wv` 与 `Version/4.0 ` 的 UA 后处理；同时记录 `UA raw`、`UA effective` 和 WebView 包；诊断条由底部移到顶部 | 恢复基线浏览器 UA，保留可比较的诊断数据并避免遮挡登录页底部 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ui/CardGroup.kt` | 删除 `CardGroupScope.switchItem` 的 `colors` 形参及转发 | 清理未由该共享开关 API 承担的死参数 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt` | 内置工具开关行改用 `item(colors = ...) + Switch` | 当前代码该处确实需要自定义背景色，保留原视觉而不复活共享死参数 |

## 红线核对

- `ChatPage` 没有无条件写入 `UiMode.CHAT` 或 `UiMode.GROUP`；只按当前会话类型且仅在不一致时同步。
- 群组模式冷启动不调用会话创建逻辑；没有可复用群聊时只进入群组列表。
- `Settings.uiMode` 类型、默认值、序列化字段和 `UI_MODE` key 未改；`ChatVM.updateSettings`、`GroupVM` 相关签名未改。
- 未在生成链路、`ChatService` 或 `ChatToolFactory` 增加模式判断；未新增依赖、语言 key、数据库字段、schema 或版本号。
- DeepSeek 的输入修复脚本、请求拦截和 token 解析未改；本批明确恢复了 UA 后处理，纠正上一批仅保留原始 UA 的记录。
- 语言目录仍只有 `values`、`values-zh`、`values-zh-rTW`，每份 2,025 条。

## 构建与测试

- `./gradlew :app:compileDebugKotlin --console=plain`：通过；仅保留既有弃用和实验 API 警告。
- `./gradlew :app:testDebugUnitTest --console=plain`：`BUILD SUCCESSFUL`；92 个测试套件、659 个用例，0 失败、0 错误、0 跳过。
- `./gradlew :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`，43 秒；ARM64 Debug APK 已重新生成。
- Debug APK：`artifacts/3.1.0-work.1/stage3-06-02-mode-session-ua-20261030/RikkaHub.Work-3.1.0-work.1-stage3-06-02-mode-session-ua-20261030-arm64-v8a-debug.apk`。
- APK 核验：applicationId `me.rerere.rikkahub.debug`；versionName `3.1.0-work.1`；versionCode `199`；仅 `arm64-v8a`；APK Signature Scheme v2 通过；ZIP 完整性通过；大小 `87,868,689` 字节；SHA-256 `10d0dd9535e8d619d77957d5b0ef1b0c428ba882d6c436ab6e75d589352ca6cf`。
- 静态计数：`Color(0x` 为 539，`fontSize =` 为 37，三份 strings 各 2,025；与阶段三⑥基线一致。`git diff --check` 仍只命中此前存在的 `Settings.kt:241` 文件末尾空行，本批未改该文件。

## 源码包

- 源码 ZIP：`artifacts/3.1.0-work.1/stage3-06-02-mode-session-ua-20261030/RikkaHub.Work-3.1.0-work.1-stage3-06-02-mode-session-ua-20261030-source.zip`。
- ZIP 不含 APK、`artifacts/`、`build/`、本地配置、签名文件或密钥；含根 `DELIVERY.md`，保留 `.claude/skills` 与 `web-ui/AGENTS.md` 两个 Unix 符号链接，并设置 UTF-8 文件名标志。
- 封装脚本、逐文件清单、凭据扫描结果和 APK/源码 SHA-256 清单均在同目录；源码 ZIP 最终哈希不回写到 ZIP，避免自引用。
- 本轮未执行 `git commit`、`git tag`、`git push`、GitHub 发布、ADB 安装或模拟器/真机操作。冷启动模式、群组会话跟随、DeepSeek 登录和诊断日志仍待维护者安装 APK 后实测。

日期：2026-10-29（Asia/Shanghai）  
版本：`3.1.0-work.1` / `versionCode 199`  
分支：`merge/upstream-2.5.6-f009`，HEAD `12a6ed24`（本批未提交）  
交付范围：ARM64 Debug APK + 不含 APK 的完整源码 ZIP；未发布、未推送、未打 tag、未操作设备或真实账号。

## 交付边界

- 本批没有修改版本号、包名、数据库版本或 schema；数据库仍为 32。
- Debug 变体的 applicationId 为 `me.rerere.rikkahub.debug`，只构建 `arm64-v8a`。没有构建 Release，也没有使用 Release 签名。
- 源码 ZIP 不含 `APK`、`artifacts/`、`build/`、`local.properties`、签名文件或密钥；保留 `.claude/skills` 与 `web-ui/AGENTS.md` 两个符号链接，并强制 ZIP 文件名使用 UTF-8 标志。
- 应用桌面图标与关于页图标仍与基线 `643191229dcbeb4972e3772986818605633ace92` 一致；其他上游图标和页面资源照常保留/同步。
- 仓库没有 `DEVELOPMENT.md`，按任务包要求没有创建替代文件；LiquidGlass 上游重套说明已登记到 `FEATURES.md`，本批偏差和证据在本文件。

## P0-1 模式持久化

- `ChatPage.kt` 不再在打开会话时按会话类型强制写回 `settings.uiMode`；`selectGroup` / `selectConversation` 仍保留。
- `ChatDrawer.kt` 的抽屉模式来自 `settings.uiMode`；用户在抽屉中主动切换聊天/群组模式的两个写入点仍保留。
- `GroupVM.kt` 的自动压缩提示判据改为 `state.group == null`，避免移除强制写回后群聊提示永不触发。
- 设备验收项 A1-A6（杀进程重开、通知进入群聊、群内压缩提示、群内新建会话）未在本机执行，待维护者安装 APK 验证。

## P0-2 液态玻璃崩溃

- `liquidglass/src/main/java/com/qmdeve/liquidglass/widget/LiquidGlassView.java` 的 `updateConfig()` 捕获当前 `LiquidGlass impl`，延迟回调执行前检查 `glass == impl`，避免 detach 后字段已清空导致 `updateParameters()` 空指针。
- 只改了 `updateConfig()` 的异步回调保护；`ensureGlass`、`removeGlass`、尺寸计算和渲染管线未改。
- 该修改属于 vendored 上游库补丁，已在 `FEATURES.md` 的“上游升级需重做清单”登记；建议后续向 QmDeve/AndroidLiquidGlassView 提交 issue。
- 设备验收项 B1-B3（反复进出页面、同时切换附件/审批/记忆面板、视觉效果不变）未执行，待维护者验证。

## P0-3 DeepSeek 网页登录诊断

- `DeepSeekWebLogin.kt` 增加 UA、WebView package、`onConsoleMessage`、`onPageFinished` 的 `href/title/body length` 探针，以及主框架资源错误/HTTP 错误日志。
- 页面完成但尚未捕获 token 时显示诊断条，展示本地化提示和 `probe` 结果；“用系统浏览器打开”通过 `ACTION_VIEW` 打开 `https://chat.deepseek.com`。
- 保留“手动粘贴 Token”路径；上一批保留原始 UA，本批已恢复去除 WebView 标记的 UA 后处理；未拦截/伪造请求、未改 `DEEPSEEK_INPUT_FIX_SCRIPT`。
- 新增三语言各 3 个 key：`deepseek_web_login_open_browser`、`deepseek_web_login_manual_hint`、`deepseek_web_login_diag`。
- 设备验收项 C1-C4 及任务包要求的四项回传材料（logcat、白屏截图、系统浏览器结果、`example.com` 对照）均待维护者提供；本轮没有联网登录或操作真实账号。

## P1 UI 一致性审计

本批只做行级外观统一，不改主题、颜色方案、字号或页面布局结构。所有有把握的变更均属于任务包 §5.3 允许范围。

### 逐条改动表

| 文件:行 | 现状 | 改成 | 理由 |
|---|---|---|---|
| `SettingBrowserPage.kt:126,132` | 清除数据、Cookies 行无前导图标 | 分别加入删除、Cookies 图标 | A：可操作的标准卡片行 |
| `SettingBrowserPage.kt:161-189` | 浏览器工具行使用裸 `Switch`，缺少语义图标 | 使用 `switchItem`，按工具名称映射图标 | A/C：统一标准开关行和点击区 |
| `SettingBrowserPage.kt:195-247` | 过滤、设备模式、超时等标准开关/操作行样式不一致 | 使用 `switchItem`/语义图标 | A/C：与同页 CardGroup 行一致 |
| `ProviderConfigureDeepSeekWeb.kt:67,72,105,118,129,139,162,175` | 状态、登录、凭据、开关、节流和测试行缺少图标或使用裸 `Switch` | 加入语义图标；启用行改 `switchItem` | A/C：配置页标准行统一 |
| `SettingBrowserCookiesPage.kt:123,172,197` | Cookies 手动操作、站点访问和清理行缺少图标 | 加入 Cookies、站点、删除图标 | A：可操作行统一 |
| `GroupConfigPage.kt:119-129,176-226` | 标签、策略、群聊设置、搜索/最近对话/时间提醒等行缺少图标或开关形式不统一 | 加入语义图标；标准开关改 `switchItem` | A/C：群组配置页与相邻设置页一致 |
| `AssistantLocalToolPage.kt:149,233` | 工具审批/浏览器开关为裸 `Switch` 或缺图标 | 标准行改 `switchItem` 并加入图标 | A/C：标准卡片行统一 |
| `AssistantMemoryPage.kt:552,612,864,938` | 记忆分组、时间提醒、文件和整理状态行缺图标 | 加入记忆、时钟、文件等语义图标 | A：可操作/信息入口行统一 |
| `SettingPreferencesUIPage.kt:147,260` | 助手气泡裸 `Switch`、自定义字体入口无图标 | 改 `switchItem` 并加入消息/文件图标 | A/C：标准显示设置行统一 |
| `SettingProviderDetailPage.kt`、`SettingWebPage.kt` | 标准卡片开关使用裸 `Switch` | 改为 `switchItem` | C：属于标准卡片行 |
| 三份 `strings.xml` 的 `browser_group_actions` | 分组标题与页面标题都显示“浏览器” | 英文 `Actions`，简中/繁中 `操作` | B：消除重复标题，不新增 key |

### 明确保留的存疑项

- `GroupDrawer.kt` 的 `item(key = "header")` 是抽屉结构性 header，不是设置项；不添加无意义图标。
- `SettingBrowserPage.kt` 的工具说明、分类标题和底部说明行是纯文字/结构行；`SettingBrowserCookiesPage.kt` 的说明行同理，不加图标。
- `ProviderConfigureDeepSeekWeb.kt` 的能力说明、图片提示和页脚是纯说明块；不加图标，不把说明伪装成可点击项。

剩余直接使用 M3 `Switch` 的文件逐项判断如下：

| 文件:行 | 判定 | 原因 |
|---|---|---|
| `AssistantBasicPage.kt:265,308` | 保留 | 参数 `formItem` 下方联动显示输入控件，不是单一开关行 |
| `AssistantPromptPage.kt:550,699` | 保留 | 提示词编辑自定义表单 |
| `ConversationExport.kt:145,204` | 保留 | 导出对话框中的 `ListItem` |
| `PromptPage.kt:454,885,1039,1128,1138,1149` | 保留 | 提示词编辑器的自定义 `FormItem` |
| `GroupConfigPage.kt:141,273` | 保留 | 背景设置卡片的自定义表单、组员卡片行 |
| `GroupCompressionSettings.kt:31,44,61` | 保留 | 压缩设置面板内联 `Row`，伴随数值输入与滑块 |
| `GroupConfigDialogs.kt:101` | 保留 | 组员编辑对话框的 `FormItem` |
| `GroupDialogs.kt:160,262` | 保留 | 组员编辑表单/参数控件 |
| `LogPage.kt:189` | 保留 | 请求记录专用卡片布局 |
| `SettingMcpDetailPage.kt:351,712,727` | 保留 | MCP 表单及工具行中的紧凑多开关布局 |
| `SettingSearchDetailPage.kt:847` | 保留 | 搜索供应商 `FormItem` |
| `QuotaQueryOption.kt:67` | 保留 | 展开按钮与开关并列的额度查询自定义行 |

本批转换为 `switchItem` 的标准卡片行位于 `AssistantLocalToolPage.kt`、`SettingBrowserPage.kt`、`SettingPreferencesUIPage.kt`、`SettingProviderDetailPage.kt`、`SettingWebPage.kt`、`ProviderConfigureDeepSeekWeb.kt`。

### 审计脚本输出

以下 A 脚本与任务包相同：`item(` 开始的 9 行内没有 `leadingContent` 即计数。改前读取阶段三⑤源码 ZIP，改后读取当前工作树。

```text
BEFORE (stage3-05 source snapshot)
  1  ui/pages/assistant/detail/AssistantBasicPage.kt
  1  ui/pages/assistant/detail/AssistantLocalToolPage.kt
  1  ui/pages/backup/tabs/ReminderTab.kt
  1  ui/pages/chat/ConversationList.kt
  1  ui/pages/group/components/GroupDrawer.kt
  1  ui/pages/mediacreation/MediaCreationComposer.kt
  1  ui/pages/setting/SettingFilesPage.kt
  1  ui/pages/setting/SettingMediaPage.kt
  1  ui/pages/setting/SettingModelPage.kt
  1  ui/pages/setting/SettingModelPromptPage.kt
  1  ui/pages/setting/SettingPreferencesNotificationPage.kt
  1  ui/pages/setting/SettingPreferencesThemePage.kt
  1  ui/pages/setting/SettingProviderDetailPage.kt
  1  ui/pages/setting/SettingSearchPage.kt
  1  ui/pages/setting/components/ASRProviderConfigure.kt
  1  ui/pages/setting/components/MediaGenerationProviderConfigure.kt
  1  ui/pages/setting/components/ProviderConfigure.kt
  2  ui/pages/extensions/skills/SkillsPage.kt
  2  ui/pages/setting/SettingPreferencesNetworkPage.kt
  2  ui/pages/setting/SettingPreferencesUIPage.kt
  2  ui/pages/setting/SettingSearchDetailPage.kt
  3  ui/pages/chat/ChatList.kt
  3  ui/pages/memory/MemoryFilePage.kt
  4  ui/pages/assistant/detail/AssistantMemoryPage.kt
  4  ui/pages/backup/tabs/ImportExportTab.kt
  4  ui/pages/group/GroupConfigPage.kt
  4  ui/pages/setting/SettingThemePage.kt
  4  ui/pages/translator/TranslatorPage.kt
  5  ui/pages/setting/SettingBrowserCookiesPage.kt
  5  ui/pages/setting/SettingPage.kt
  9  ui/pages/setting/SettingWebPage.kt
 10  ui/pages/setting/SettingBrowserPage.kt
 13  ui/pages/setting/components/ProviderConfigureDeepSeekWeb.kt
TOTAL 93

AFTER (current tree)
  1  ui/pages/assistant/detail/AssistantBasicPage.kt
  1  ui/pages/backup/tabs/ReminderTab.kt
  1  ui/pages/chat/ConversationList.kt
  1  ui/pages/group/components/GroupDrawer.kt
  1  ui/pages/mediacreation/MediaCreationComposer.kt
  1  ui/pages/setting/SettingBrowserCookiesPage.kt
  1  ui/pages/setting/SettingFilesPage.kt
  1  ui/pages/setting/SettingMediaPage.kt
  1  ui/pages/setting/SettingModelPage.kt
  1  ui/pages/setting/SettingModelPromptPage.kt
  1  ui/pages/setting/SettingPreferencesNotificationPage.kt
  1  ui/pages/setting/SettingPreferencesThemePage.kt
  1  ui/pages/setting/SettingSearchPage.kt
  1  ui/pages/setting/components/ASRProviderConfigure.kt
  1  ui/pages/setting/components/MediaGenerationProviderConfigure.kt
  1  ui/pages/setting/components/ProviderConfigure.kt
  2  ui/pages/extensions/skills/SkillsPage.kt
  2  ui/pages/setting/SettingPreferencesNetworkPage.kt
  2  ui/pages/setting/SettingSearchDetailPage.kt
  3  ui/pages/chat/ChatList.kt
  3  ui/pages/memory/MemoryFilePage.kt
  3  ui/pages/setting/SettingBrowserPage.kt
  4  ui/pages/backup/tabs/ImportExportTab.kt
  4  ui/pages/setting/SettingThemePage.kt
  4  ui/pages/translator/TranslatorPage.kt
  5  ui/pages/setting/SettingPage.kt
  5  ui/pages/setting/components/ProviderConfigureDeepSeekWeb.kt
  7  ui/pages/setting/SettingWebPage.kt
TOTAL 60
```

补充基线核对：

- `grep -rn "Color(0x" app/src/main/java/me/rerere/rikkahub/ui | wc -l`：539；阶段三⑤源码快照同为 539，本批没有改颜色。任务包 §5.1 所列 543 与可复现的本地基线不一致，未为追数字批量修改。
- `grep -rn "fontSize =" app/src/main/java/me/rerere/rikkahub/ui | wc -l`：37，保持不变。
- 三份资源文件各 2,025 条；资源目录只有 `values`、`values-zh`、`values-zh-rTW`。
- 按任务书的字面子串 `Switch(` 搜索，阶段三⑤快照为 20 个文件、当前为 14 个文件；按实际 M3 标识符 `\bSwitch\(` 过滤后分别为 18 和 12。转换集中于标准卡片行，剩余 12 个直接使用文件均列在上表或属于定制布局。

## 构建与测试

- `./gradlew :app:compileDebugKotlin --console=plain --no-configuration-cache --max-workers=4`：成功；任务结果主要为 `UP-TO-DATE`。
- `./gradlew :app:testDebugUnitTest --console=plain --no-configuration-cache --max-workers=4`：`BUILD SUCCESSFUL`；92 个测试套件、659 个用例，0 失败、0 错误、0 跳过。Gradle 本次复用已完成的测试任务，JUnit XML 已核对计数。
- `./gradlew :app:assembleDebug --console=plain --no-configuration-cache --max-workers=4`：`BUILD SUCCESSFUL`，1 分 26 秒，300 个 actionable tasks，7 个执行、293 个 `UP-TO-DATE`。
- APK：`artifacts/3.1.0-work.1/stage3-06-fixes-20261029/RikkaHub.Work-3.1.0-work.1-arm64-v8a-debug.apk`。
- APK 核验：applicationId `me.rerere.rikkahub.debug`；versionName `3.1.0-work.1`；versionCode `199`；ABI 仅 `arm64-v8a`；V2 Debug 签名验证通过；ZIP 完整性通过；大小 `87,868,689` 字节；SHA-256 `d7b736b5f7f96bffbf5a8656c2ddcd43daa6e9c5260205bfc5037cfb76a8d43d`。

## 源码包与状态

- 源码 ZIP：`artifacts/3.1.0-work.1/stage3-06-fixes-20261029/RikkaHub.Work-3.1.0-work.1-stage3-06-fixes-source.zip`。APK 不在 ZIP 内；源码 ZIP 的 SHA-256 和大小写在同目录 `SHA256SUMS`，不把校验值回写进 ZIP，避免自引用。
- 封装脚本：`artifacts/3.1.0-work.1/stage3-06-fixes-20261029/package-source.py`；同时生成 `SOURCE-MANIFEST.json`、`SOURCE-VERIFICATION.json` 和 `SHA256SUMS`。
- 当前 `git status --short`：651 个状态条目，其中暂存区 591、工作树未暂存 139、未跟踪 31；这些包含此前上游合并和阶段三①至⑤累积改动，未清理、回滚或覆盖。未提交任何本批 commit。
- 本轮未执行 `git commit`、`git tag`、`git push`、GitHub 发布、ADB 安装或模拟器/真机操作。

---

# 阶段三⑤交付记录

日期：2026-10-29（Asia/Shanghai）  
版本：`3.1.0-work.1` / `versionCode 199`  
分支：`merge/upstream-2.5.6-f009`，HEAD `12a6ed24`  
交付范围：ARM64 Debug APK + 不含 APK 的完整源码 ZIP；未发布、未推送。

## 本批变更

- **A3 审批三档**：在聊天与群聊输入栏横向工具区的 Reasoning 按钮后加入 `ToolApprovalButton`，不受 `groupMode` 限制；群聊回调同时持久化 `toolApprovalMode`。`ChatInput` 函数签名未变。
- **B1 浏览器图标**：设置页“内置浏览器”行改用 `CardGroupIcon`，与同页分组图标一致。
- **W 工作区审批**：移除工作区详情页四项逐工具审批开关，以及对应 ViewModel、Repository、工具构造函数中的覆盖值解析链路。保留 `WorkspaceEntity.toolApprovals` Room 字段、数据库 schema、`pathOutsideWritableRoots` 写入/编辑路径护栏，以及四项 `L3_REWRITE` 风险登记。旧字段值保留但不再影响行为。
- 无新增语言 key、依赖或数据库迁移；版本号和数据库版本不变。

本批源码范围为 7 个 Kotlin 文件：`ChatInput.kt`、`ChatPage.kt`、`SettingPage.kt`、`WorkspaceDetailPage.kt`、`WorkspaceDetailVM.kt`、`WorkspaceRepository.kt`、`WorkspaceTools.kt`。相对上一批阶段三④修正单源码 ZIP，这 7 个文件均有变化；本批另外更新根 `CHANGES.md`、`AGENTS.md` 并新增本文件。

## 构建与测试

- `./gradlew :app:compileDebugKotlin --console=plain`：成功；任务由 Gradle 判为 `UP-TO-DATE`。此前本批源码编译也已通过。
- `./gradlew :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`，1 分 44 秒，5 个任务执行、291 个任务为 `UP-TO-DATE`。
- `./gradlew :app:testDebugUnitTest --console=plain`：`BUILD SUCCESSFUL`；Gradle 复用最新测试结果。JUnit XML 时间为 2026-10-29 12:31（本地时间），共 **92 套件 / 659 用例，0 失败、0 错误、0 跳过**。
- `git diff --check` 对本批 7 个源码文件及交付文档通过；全仓检查只提示既有 `Settings.kt` 第 241 行文件末尾空行。该文件与阶段三④源码包逐字节一致，本批未改。
- APK：`artifacts/3.1.0-work.1/stage3-05-style-interaction-20261029/RikkaHub.Work-3.1.0-work.1-arm64-v8a-debug.apk`。
- APK 核验：applicationId `me.rerere.rikkahub.debug`；versionName `3.1.0-work.1`；versionCode `199`；仅 `arm64-v8a`；V2 Debug 签名验证通过；ZIP 完整性通过。大小 `87,868,689` 字节，SHA-256 `d3ccb1ad3a7e19e6da3e2d770cee367b6c26801b3623922d329a5efef540fcf0`。
- 未安装或运行模拟器/设备；输入栏切档与群聊持久化、工作区审批行为、界面布局仍需维护者安装 APK 实测。

## 五项核对

| 编号 | 结果 |
|---|---|
| B2 设置分组标题 | 通过：4 个 `CardGroup` 均有标题。 |
| B3 `PagerTabs.kt` | 无法完成任务书要求的上游 3.1.1 逐字节比较：本地没有 3.1.1 tag/ref 或对应源码。当前文件 SHA-256 为 `596d98f40e4d4896f8cab483c0f69d8a9070e0db3702fa4a28503788c5244d09`，与阶段三③源码包一致。未改该文件。 |
| C1 可点击 Surface | 与任务书预期不符：`WorkspacePage.kt` 的 workspace 卡片使用 `Surface(onClick = ...)`；`WorkspaceDetailPage.kt` 的文件行在 `Surface` 修饰符上使用 `combinedClickable`。按要求仅记录、未改。 |
| C2 `ItemActionMenu` | 命中 **22 个** Kotlin 源码文件，包含群组 4 页；任务书预期为 19 个。未改。 |
| C4 空态 | 记忆页三处 `ExtensionEmptyState` 存在；组员、群组、群聊页面使用 `GroupEmptyState`。通过静态核对。 |

## 阶段三收尾自检

| # | 判据 | 结果 |
|---|---|---|
| 1 | 建组员、建群、配置组员、多轮顺序发言及群聊设置入口 | 代码阶段已完成；完整交互需维护者实机验收。 |
| 2 | `ModeSwitcher` 聊天/群组切换 | 接线已在阶段三③恢复；实际交互待实机验收。 |
| 3 | 审批三档在聊天与群聊切换并生效 | 本批已接线；聊天及群聊行为待实机验收。 |
| 4 | 记忆复制、迁移和导入来源选择 | 阶段三④代码与测试已完成；设备验收待维护者。 |
| 5 | 设置页图标一致 | B1 已改；静态检查通过，视觉效果待设备验收。 |
| 6 | 三个旧测试编译并运行 | App 测试全套 92/659 通过；旧测试包含在测试结果中。 |
| 7 | 三语 key 数一致 | `values`、`values-zh`、`values-zh-rTW` 各 2,022。 |
| 8 | 编译与单测 | Debug 编译/构建通过；92 套件 / 659 用例全绿。 |
| 9 | 无 `_parked/` 残留 | 空的 `_parked/` 目录已移除；当前无该目录。 |
| 10 | versionCode 199 / DB 32 | 通过；`app/build.gradle.kts` 与 `AppDatabase.kt` 核对一致。 |
| 11 | 工作区逐项审批已移除、行为边界保留 | UI 与逐项解析链路已移除；Room 字段、四项 L3 和路径越界护栏保留。 |

## 源码包与工作树

- 源码 ZIP：`artifacts/3.1.0-work.1/stage3-05-style-interaction-20261029/RikkaHub.Work-3.1.0-work.1-stage3-05-style-interaction-source.zip`。
- ZIP 不含 APK、`artifacts/`、`build/`、本地配置或密钥；含根 `DELIVERY.md`，保留 `.claude/skills -> ../.agents/skills`、`web-ui/AGENTS.md -> CLAUDE.md` 两个符号链接，并设置 UTF-8 文件名标志。APK 与 ZIP 的 SHA-256 清单在同目录 `SHA256SUMS`。
- 仓库缺少任务包提到的 `DEVELOPMENT.md` 和 `docs/26-清单-阶段3待补UI与入口.md`；按任务范围未新建或修改替代文档。
- 交付时 `git status --short` 共 650 行：619 个已跟踪变更项、31 个未跟踪项；619 项带暂存区变化，136 项带工作树变化。大部分属于此前上游合并、阶段二及阶段三①至④的累积工作；本批相对阶段三④源码 ZIP 的产品差异仅为上列 7 个源码文件。未清理、回滚或覆盖其他改动，工作树仍未提交。
- 设备/视觉验证由维护者安装本批 ARM64 Debug APK 完成。本轮没有提交、打 tag、push、发布或操作设备/真实账号。
