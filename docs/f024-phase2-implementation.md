# F024 第二期实施与验收记录

日期：2026-11-25。工程：3.3.1-work.1 / versionCode 210 / Room 34。改前HEAD为f71dba5a，工作区包含先前闹钟、角色转移坏附件、Shizuku及个人脚本调整；本批叠加，不回退这些修改，不将其全部算作本期新增。

实施依据为第二期主任务书、六处校正、包04/05/06/07及维护者最后的「变量通道的状态接线与重试节制」。最新补充覆盖失败后立即signal的旧示例。日常交付方式继续为维护者自行构建；本轮无APK、ZIP、提交、推送、标签或设备操作。

## 参考来源与移植差异

主参考为花灯 `MiaoWuNYA/rikkahub-sillytavern-android`，固定提交 `c988e47d72230b5d18423879d58a11298b841668`；本机只读副本为 `/home/li/CDX/rikkahub-references/F024/rikkahub-sillytavern-android`。补充参考为 `heikeyangle-code/rikkahub-plus`，固定提交 `a2cab03cc27641dff8888590f2397f8bf2304d97`，副本位于相邻 `rikkahub-plus`。两仓保留，均不放入产品源码包。沿用本仓AGPL-3.0声明，改动日期与范围同时记入CHANGES.md。

下表的行号均指固定花灯提交中的原文件，目标前缀为 `app/src/main/java/me/rerere/rikkahub/`。

| 参考文件 | 目标与差异 |
|---|---|
| `data/st/macro/MacroTypes.kt`、`MacroEngine.kt`、`MacroDefinitions.kt`、`MacroVariables.kt`、`MacroRegistry.kt`、`MacroHash.kt`、`StRandom.kt` | 同路径搬运；全文去除尾部换行后比较一致。没有改宏列表、求值顺序或数值/数组语义，不声称字节完全相同。 |
| `data/st/regex/RegexScriptEngine.kt` | 同路径搬运；仅尾部换行不同，匹配/替换/trim/placement语义原样。 |
| `data/st/regex/RegexScript.kt` | 删除原28～29行的预设来源注释和`sourcePresetGroup`，末尾换行不同，其余保留。 |
| `data/st/regex/RegexScriptSources.kt` | 替换原1～142行的预设/Assistant来源合并；本仓按角色先、全局后合并，排除禁用/空模式。原143行起的`parseRegexScript`函数保留原样。 |
| `data/model/TavernCard.kt` | 搬到`data/st/import/TavernCard.kt`，改package为反引号转义的`import`包；模型字段不改名。追加原Assistant.kt第703～722行的`DelayUntilRecursionSerializer`及所需JSON导入。 |
| Plus角色/世界书组织 | 仅补充设计参考，未复制其Compose页面、启动门控或世界书业务模型。 |

新增应用适配包括ScopeVariableWriter、MacroStoreRegistry、CharacterMacroEnvironment、扫描器、插入器、Codec与本仓CardGroup页面；它们不是对参考求值引擎的改写。Kai/PalmClaw继续保留用于原脉搏/闹钟板块，本期不改其调度设计。

## A～H 实施落点

| 任务 | 实施 |
|---|---|
| A 转正与A0默认模式 | 角色常驻、旧实验字段只序列化；首次强制选择工作/角色、模式条排序；设置只改默认值；先设置加载、同步uiMode，再开放首屏。外部目标解析一次并优先，迁移弹窗随后出现。 |
| B 数据与兼容 | Character追加卡快照、深度、正则和预算；Settings追加五个字段及独立持久键；RegexInjection追加ST字段/原条目JSON。继续使用Lorebook，不改Room34。 |
| C 宏与变量 | 原引擎、真实角色/人设/历史环境；全局和会话store共用锁/实例；应用级单消费者与逐scope屏障。工具步骤不重复执行相同源文本的变量副作用。 |
| D 正则 | 角色先于全局；保留USER_INPUT/AI_OUTPUT和markdownOnly/promptOnly/runOnEdit/substitute/depth。输入/输出/编辑/显示各走相应路径，显示只用变量快照，不触发写盘。 |
| E 世界书扫描 | 关键词、四种选择逻辑、常驻、递归0+3层、延迟、额外六类字段、概率、分组、优先级/字符预算、outlet、sticky/cooldown。同轮冻结，按真实用户消息ID推进，会话间隔离。 |
| F 请求接线 | 世界书只用于角色会话；历史在裁剪前捕获。原applyInjections处理系统前后，独立insertKeepingRole处理TOP/BOTTOM/深度；角色深度提示基于世界书插入后的列表。 |
| G 卡导入导出 | V2/V3 JSON、PNG chara/ccv3、tEXt与压缩/未压缩iTXt，CRC/边界/IEND检查，V3优先而无效时回退V2；内嵌书独立存储，原图字节保留。JSON/PNG导出合并未知扩展及原始条目。 |
| H UI与三语 | 角色卡四分区、开场白/预设消息/深度提示/预算、世界书列表与五分区条目编辑器、角色/全局正则管理、SAF导入导出、开场白选择。121个新增资源×三语言；无参考Compose代码。 |

## 变量通道的最终协议

`offer`在同锁内同时替换scope快照和latestVersion；消费者同锁取走快照并登记inFlightVersion，不存在快照已消失而两个版本都旧的窗口。每条写完均回调结果，成功推进writtenVersion并清lastError，inFlight回落到written。屏障目标为每scope的max(latestVersion,inFlightVersion)，捕获与注册在同锁内，注册即判断，已经落盘直接成功。

失败仅恢复没有被新版本替代的快照并记错，不自唤醒。下一次offer/flush重试；已经排队的旧唤醒不形成错误循环。新屏障区分注册前历史错误与本次重试错误，重试成功可正常返回。未增加sleep、轮询、同步重试或消费者while-retry。通道只合并唤醒，跨scope数据和屏障不会被覆盖。

消费者属于App级SupervisorJob+IO；正常、取消、错误的runTurn最终均flush；压缩、输入预处理/无回复发送、编辑也在finally补齐。写入完成以SettingsStore.update返回为准，MacroFlush日志只报告结果，不靠等待固定秒数。强杀发生在flush返回前，仍允许未落盘数据丢失，不能宣传瞬间强杀不丢。

## 相对任务示例的必要差异

1. 空findRegex草稿保留在设置中并补稳定ID；新建默认禁用。按原清洗示例立即删除空模式会导致用户刚清空输入框时整条脚本消失。执行来源合并和引擎都排除空模式，空草稿不会匹配文本；新增StPipelineTest验证。
2. 使用标准`java.util.Base64`而非Android专用Base64，minSdk26已支持，Codec因此能直接跑纯JVM测试；无新依赖。
3. 原始卡JSON快照上限256KiB，超过后清快照并显式提示，只保证映射已知字段；PNG文本解压上限16MiB只约束元数据，不是角色转移附件大小限制。坏数据不崩溃。
4. 失败重试选择「下次offer/flush触发」，不实现可选延迟退避；最新补充允许此方案。全局单调序号受同锁保护，不额外引入AtomicLong。逐scope状态、屏障和更新顺序均已接线。
5. 正则只对相应阶段执行一次；已持久化的一般替换不会在请求和静态显示时重复应用。工具步骤的宏缓存按稳定源文本/位置，而非每次新建的合成消息ID。
6. 四个ConversationConfigTest旧断言期望工作助手继承世界书，按转正规则更新；保留配置快照、注入及旧ID不删除等断言，没有删除或忽略这些测试。补齐ChatList角色配置读取，工作/群组原有查找分支保留。

## 自动验证与证据

证据目录：`artifacts/f024-phase2-20261125-Jb56XS/`。按批次先编译再定向JVM：第一批`batch1.log`成功4分29秒；第二批`batch2.log`成功1分57秒；第三批初次发现空comment往返默认值断言差异，修正后`batch3-recheck.log`成功3分15秒。最终全App回归首次发现上述四个旧世界书预期，修正后通过；补齐输入/编辑finally后，以`final-app-jvm-recheck2.log`及TEST-REPORTS为最终准。

命令（编译和JVM，不生成APK）：

```bash
./gradlew :app:compileGithubDebugKotlin :app:testGithubDebugUnitTest \
  --console=plain --max-workers=1 -Prikkahub.abi=arm64-v8a
```

新增测试：ScopeVariableWriterTest（含最新补充要求的全部五项）、CharacterMacroTest、WorldbookScannerTest、StInjectionInserterTest、CharacterCardCodecTest、StPipelineTest、ModeOrderTest；SettingsFieldsTest和CharacterScopeTest扩展兼容/归属断言。最终55秒成功，159套件1078项中1077通过、1项缺真实DeepSeek归档跳过，0失败/错误；相关10套件63项全通过。XML快照在TEST-REPORTS，统计在TEST-SUMMARY.json；依赖模块编译按Gradle复用缓存，未声称跑过全仓所有模块的JVM。

静态核对：旧实验门控读取为0；原PromptInjectionTransformer/StylePackTransformer/RegexOutputTransformer与HEAD相同；schema和原身份图标无本批差异；版本210/DB34；三语各2865项、集合相等无重复。个人源码打包器要求根DELIVERY存在，脚本不入包。未运行全仓Lint、instrumentation、模拟器或真机，既有Lint问题不能据此标记修复。

## 逐项验收

「JVM通过」仅表示表中离线算法/模型测试通过；「静态接线」只检查源码和编译，不等于手机效果通过。所有标记待实机的项目由维护者装包后填写，真实角色卡生态、SAF权限、通知导航与进程强杀均未实测。

### 转正、默认模式及启动

| 项目 | 当前证据与剩余验收 |
|---|---|
| V-A1 | 静态接线：四模式常驻、移除开关；全新安装待实机。 |
| V-A2 | JVM通过：旧false不折回ROLE；升级可见性待实机。 |
| V-A3 | JVM通过工作会话无书，UI入口静态移除；请求抓包待实机。 |
| V-A4 | JVM通过群组不注入；群组/脉搏入口与请求过滤静态接线，待实机。 |
| V-A5 | 静态确认实验区只保留DeepSeek，页面待实机。 |
| V-A6 | 模式/数据回归JVM通过；DeepSeek开关与真实备份恢复待实机。 |
| V-A7 | settings.init提前返回、默认选择不可取消静态接线；首次启动待实机。 |
| V-A8 | 默认角色排序JVM通过，首屏创建前同步模式；无工作页闪现待实机。 |
| V-A9 | 默认工作路径静态接线，老用户提示在后；待实机。 |
| V-A10 | defaultModeChosen持久键与映射JVM通过；重启不重弹待实机。 |
| V-A11 | 排序与当前uiMode独立JVM通过；设置触摸/即时展示待实机。 |
| V-A12 | 首装版本标记与迁移门槛JVM回归通过；新装不弹待实机。 |
| V-A13 | 默认选择和老用户迁移共用when，旧迁移回归通过；升级弹窗顺序待实机。 |
| V-A14 | launchMode不读上次uiMode，JVM通过；冷启动角色待实机。 |
| V-A15 | 同上，默认工作/上次脉搏待实机。 |
| V-A16 | 外部目标优先静态接线；通知点开指定会话待实机。 |
| V-A17 | await设置流反映角色后才建导航；冷启动胶囊待实机。 |
| V-A18 | 工作会话反推CHAT静态接线；通知导航待实机。 |
| V-A19 | 群会话反推GROUP静态接线；群通知待实机。 |
| V-A20 | uiMode写入/等待在startScreen之前；首帧待实机录像。 |
| V-A21 | 首次确认后才开放角色页，外部目标解析不会重建媒体会话；首帧待实机。 |

### 角色卡

| 项目 | 当前证据与剩余验收 |
|---|---|
| V-G1 | JVM通过V2/内嵌书映射；真实V2 PNG及文件选择待实机。 |
| V-G2 | JVM通过V3优先、扩展及无效V3回退；真实卡待实机。 |
| V-G3 | JVM通过JSON解析/映射；SAF入口待实机。 |
| V-G4 | JVM通过坏数据、非卡及CRC拒绝；错误Toast待实机。 |
| V-G5 | JVM通过已知字段、内嵌条目及未知值往返；真实卡互导待实机。 |
| V-G5b | JVM递归比较未知对象/数组/布尔/null，并检查正则编辑后未知元数据；大样本待验。 |
| V-G5c | JVM通过超256KiB清快照并标记降级；提示展示待实机，不承诺无损。 |
| V-G5d | JVM通过assets/source等原数组内容顺序；用户实际卡待实机。 |
| V-G6 | JVM通过PNG读回、原IDAT字节不变；真实图片解码与外部酒馆导入待实机。 |

### 世界书与深度注入

| 项目 | 当前证据与剩余验收 |
|---|---|
| V-W1 | JVM通过关键词/选择/全词与未命中；模型请求待实机。 |
| V-W2 | 常驻分支静态接线；实际常驻书待实机。 |
| V-W3 | JVM通过初始+三层递归及第三层延迟门；真实请求待实机。 |
| V-W4 | JVM通过excludeRecursion；待实机。 |
| V-W5 | JVM通过preventRecursion；待实机。 |
| V-W6 | JVM通过0%不命中及默认100%路径；概率观察待实机。 |
| V-W7 | JVM通过第1轮命中、2/3/4轮粘性、5轮结束。 |
| V-W7b | JVM连续观察8轮，粘性不续期。 |
| V-W8 | JVM通过第2/3轮冷却、第4轮可触发。 |
| V-W8b | JVM通过未命中不设冷却、随后立即命中。 |
| V-W8c | JVM通过不同会话独立。 |
| V-W8d | JVM同轮三次调用结果不变。 |
| V-W8e | JVM覆盖含端点的冷却边界。 |
| V-W8f | JVM组合sticky=3/cooldown=2不续期；实际同轮命中行为待实机。 |
| V-W9 | JVM通过优先级、字符预算和ignoreBudget。 |
| V-W9b | JVM通过裁剪后真实用户ID仍使轮号1～6推进。 |
| V-W9c | JVM同轮工具往返不加轮；真实工具待实机。 |
| V-W9d | JVM合成user不改轮号。 |
| V-W10 | JVM通过outlet收集，宏接线静态复核；完整模型请求待实机。 |
| V-W11 | JVM通过禁用书完全排除。 |
| V-W12 | JVM通过atDepth system保角色。 |
| V-W13 | 保角色插入器静态接线，assistant深度提示已JVM覆盖；世界书assistant请求待实机。 |
| V-D1 | JVM通过system深度/最终列表定位；模型请求待实机。 |
| V-D2 | JVM通过user/assistant角色插入；待实机。 |
| V-D3 | JVM通过空深度提示无插入。 |

### 宏、变量和写盘屏障

| 项目 | 当前证据与剩余验收 |
|---|---|
| V-C1 | JVM通过char昵称、人设user及替换环境。 |
| V-C2 | 原参考随机实现不改，JVM验证候选集合；随机分布不作统计保证。 |
| V-C3 | JVM通过set/get与fake store落盘；真实DataStore重启待实机。 |
| V-C4 | JVM通过未知宏原样保留。 |
| V-C5 | 世界书扩展使用同一宏环境静态接线；真实模型待实机。 |
| V-C6 | JVM通过共享store原子inc及1.5→2.5；100并发inc无丢更新。 |
| V-C7 | 轮末finally确认update返回静态接线；看到MacroFlush成功后强杀/重启待实机。 |
| V-C8 | 未flush前强杀允许丢失；强杀无脏数据尚待实机，不作保证。 |
| V-C9 | JVM通过同scope并发inc、工具步骤不重复宏副作用。 |
| V-C10 | JVM通过全局/会话scope不互相覆盖。 |
| V-C11 | Registry按scope隔离静态接线，跨scope屏障JVM通过；两真实会话待实机。 |
| V-C12 | JVM通过取消后仍flush；手机停止后强杀待实机。 |
| V-C13 | JVM通过store失败返回/保留快照，异常finally静态接线；生成报错后重启待实机。 |
| V-C14 | JVM阻塞会话写盘，已完成全局写不能提前放行。 |
| V-C15 | JVM同scope旧快照合并保留最新值；单消费者保证顺序。 |
| V-C16 | JVM持续磁盘错误返回failure，快照保留，外部flush触发重试。 |
| V-C17 | JVM同scope同实例同锁，100并发inc结果正确。 |
| V-C18 | JVM取消生成协程不取消App消费者，已offer内容flush成功。 |
| V-C19 | JVM失败后新offer重试成功清lastError，后续flush成功。 |
| 最新补充① | flushWaitsForInFlight：JVM通过，写盘闸门未释放不返回。 |
| 最新补充② | offerUpdatesLatestVersion：JVM通过，不预先唤醒也能flush到最新版本。 |
| 最新补充③ | inFlightClearedAfterWrite：JVM通过，写前登记、完成后回落written。 |
| 最新补充④ | noRetryStormOnPersistentFailure：JVM通过，无新触发调用次数不增长；下一flush仅重试一次。 |
| 最新补充⑤ | retryOnNextOffer：JVM通过，新值落盘且清错。 |

### 正则、UI与回归

| 项目 | 当前证据与剩余验收 |
|---|---|
| V-R1 | JVM通过AI输出替换；气泡显示待实机。 |
| V-R2 | JVM通过markdownOnly不进prompt；实际显示待实机。 |
| V-R3 | JVM通过promptOnly只改请求副本、存储原文不变。 |
| V-R4 | JVM通过消息深度过滤及编辑开关。 |
| V-R5 | JVM通过角色先于全局串联。 |
| V-U1 | 四CardGroup静态接线/编译通过；触摸、长文本、折叠待实机。 |
| V-U2 | 五分区与position条件静态接线；底部面板待实机。 |
| V-U3 | 无硬编码颜色、用主题组件；深浅/壁纸对比度待实机。 |
| V-U4 | 可滚动表单无固定横向堆叠；360dp和大字体待实机，未做截图验收。 |
| V-U5 | 三语各2865项名称集合一致、无重复，只保留三种语言；翻译视觉待实机。 |
| V-X1 | 身份/模型/记忆归属JVM回归通过；真实角色聊天、工具、工作区待实机。 |
| V-X2 | 全App JVM回归；三模式真实聊天、脉搏与群组未实机验收。 |
| V-X3 | SettingsFields映射和序列化覆盖通过；真实备份导出/恢复整链待实机。 |
| V-X4 | 旧字段缺省和Settings映射JVM通过；安装升级/恢复真实旧备份待实机。 |

## 维护与回滚

本批未提交。回滚应仅逆向本批转正、ST数据/管线、页面和三语增量，保留之前闹钟/附件/Shizuku差异；不整文件checkout共享Settings/ChatService/RouteActivity/三语，不降低数据库版本，不删除已存角色/世界书/变量。将来独立提交后才可按明确提交revert。源码打包包含本记录及根DELIVERY，个人构建脚本仍排除。
