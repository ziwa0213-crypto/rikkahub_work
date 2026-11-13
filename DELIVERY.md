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
