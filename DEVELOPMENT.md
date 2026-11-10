# 开发进度 / Development Status

> 最后更新：**2026-11-10**
> 本文件记录**正在开发中的工作**。功能说明见 [README.md](./README.md)，版本变更见 [CHANGES.md](./CHANGES.md)。

---

## English Summary

**`3.1.2-work.1` (versionCode 202, database 33) shipped on 10 November 2026**, merging upstream
**3.1.2** and finishing **Pulse mode** — the third mode next to Chat and Groups.

**Pulse mode is done.** An assistant that runs on its own schedule: heartbeat self-checks,
scheduled tasks (one-off, cron, or every heartbeat), change-only watchers, optional notification
and SMS sensing, its own memory bucket with a "dreaming" consolidation step, its own workspace,
and layered keep-alive so Android does not freeze it. It never messages you unless something
actually needs your attention.

The upstream merge brought a new text-rendering library replacing the `highlight` module, a
rewritten message inbox, a split chat list with swipe-to-preview, a floating top-bar capsule, and
background workspace commands.

**Next:** nothing scheduled. Optional items are listed in §6.

## 1. 当前状态

**已发布**：**`3.1.2-work.1`** / versionCode **202**（2026-11-10），数据库 **33**。
上一版 `3.1.1-work.2`（201）于 2026-11-03 发布。

阶段 0~4（合并 / 数据 / 功能回归 / 入口与群组 / 收尾）已全部完成，**脉搏模式（F015）已收尾**。

---

## 2. 这一版做了什么

### 2.1 合并上游 3.1.2

| 变化 | 说明 |
|---|---|
| 文本渲染换库 | 上游删掉 `highlight` 模块，改用 `bettertext`；代码块高亮、表格、公式的入口都跟着变 |
| 消息队列重写 | `MessageQueue` → `ConversationInbox`，新增 `UIMessagePart.Notice` 类型 |
| 聊天列表拆分 | `ChatList.kt` 拆成 5 个文件，加入左滑概览 |
| 顶栏浮动胶囊 | 顶栏按钮改成浮动胶囊，与输入框共用玻璃背景 |
| 工作区后台命令 | 新增 `run_in_background` 与两个配套工具，长命令不再阻塞一轮 |

冲突处理：22 个真冲突逐个手改；`generatePdf` 等被上游删掉的调用点同步清理；语言仍只保留三份。

### 2.2 脉搏模式收尾（六批）

| 批次 | 内容 |
|---|---|
| 感知层 | 阅读通知、短信（轮询、白名单号码、验证码先屏蔽再截断） |
| 心跳 | 指令位置修正（从 system 中间挪到消息末尾）；只有本轮真的读过通知 / 短信时才把摘要脱敏 |
| 抽屉 | 状态卡改成两行 + 待执行列表；通知白名单加全选 / 反选；主界面只保留用量一行 |
| 工具 | 闹钟 `set_alarm`（只创建）；脉搏独立开关；禁止用 `sleep` 等将来的时间点 |
| 记忆 | 顶栏记忆入口 + 记忆页（记忆 / 做梦两页签）+ 自动整理开关 |
| 多模态 | 会话内工作区图片可解析；视频输入按模型能力开放 |

### 2.3 其他修复

- **顶栏液态玻璃概率性偏移** —— 原来在 applyChanges 阶段读 `getLocationInWindow()`，拿到的是上一帧的位置，与本帧的 bounds 跨帧比较。改成 `positionInRoot()` + 只扣宿主 padding，彻底不依赖窗口坐标。
- **会话内图片不显示** —— `LocalWorkspaceId` 这个 CompositionLocal 全项目从没 provide 过，工作区路径永远解析不出来；`UIMessagePart.Image` 也没走解析。两处一起补。
- **峰谷计价跨天算错** —— 抽屉的「今日」会把跨天的日志也算进去，改成只统计当天。

---

## 3. 脉搏模式：能力与边界

**它是什么**：第三个模式。不等你开口，按时间自己运转，有事才来找你。

**三层机制**：

- **心跳** —— 固定间隔（默认 30 分钟）自检。没有新情况就安静返回，不产生消息。
- **计划任务** —— 到点执行。支持一次性、循环（五字段 cron）、随心跳三种；**任务由 AI 创建**，用户只说话。
- **监视器** —— 本地定时轮询（电池 / 网址 / 命令），**本地 diff，没变化不叫模型**。

**它有什么**：独立会话与记忆桶（`__pulse__`）、独立工作区（可装 Linux 跑脚本）、自己的工具开关、通知与短信感知、成本显示、保活四层。

**边界**：不能写别人的记忆桶；群聊和聊天也写不了它的。豆包这类第三方网页通道不开放记忆写入（F009 的既有约束）。

---

## 4. 缺陷与修复（值得记下来的）

### 4.1 心跳提示词「不生效」

现象：改了心跳提示词，脉搏仍然只做一半的事。

原因：指令被拼进 system 的**第二段**，后面压着约 4000 字记忆。`deepseek-flash` 读到的是后半段，只执行了「任务 2」。

修法：自动轮的指令**始终**作为合成用户消息放在 messages 末尾，`systemInstruction` 置空。

### 4.2 感知摘要误判敏感

现象：只要启用过通知 / 短信，所有轮次的报告都被当成敏感内容，固定写成一句话、不写报告文件。

原因：判据是「开关开没开」，不是「本轮真读没读」。

修法：看本轮是否出现 `read_notifications` / `read_sms` / `send_sms` 的工具调用，或者事件类型是 `sms`。

### 4.3 模态模型用 `sleep` 等任务

现象：16:58 建了 17:00 的任务，模型写 `sleep 150` 去等，整轮被占住，还会和那个任务本身撞车。

原因：它没有「等待」这个原语，唯一能耗时的是 shell。

修法：每轮在 system 里注入一条框架约束 —— 要稍后确认就再记一条任务，不要用 shell 等将来的时间点。

### 4.4 顶栏玻璃概率性偏移（详见 §2.3）

重启后恢复正常、不是每次都偏 —— 典型的跨帧读坐标。三条说明：尺寸一直是对的，所以不是布局问题；只在宿主发生位移的那一帧出错；错完没有重组来纠正，就一直错到页面重建。

---

## 5. 已知限制（有意保留，不是缺陷）

- **视频走内联 base64** —— 几 MB 的短视频没问题，十几 MB 大概率被服务端拒。这是协议限制，不是能绕的。
- **脉搏不做历史截断** —— 用户明确要求保留完整历史，用来区分「新老主播」这类状态。心跳密集时单轮上下文会涨。
- **厂商后台管理引导未做** —— 部分 ROM 需要用户自己去设置里开白名单，应用只给电池优化和自启动引导。
- **邮件感知未做** —— 三个账号实测 44 天没成功过一次，暂时不做。
- **记忆跨模式可读不可写** —— 有意为之，避免互相污染。

---

## 6. 下一步

暂无排期。可选方向：

- 脉搏的上下文压缩（心跳密集场景）
- 监视器的更多数据源
- 群组模式的语音（目前明确关闭）

---

## 7. 发布节奏

- 版本号 `{上游版本}-work.{n}`，跟进新上游时 `n` 归 1。
- 不跟进就不动版本号。
- 每次发版 versionCode +1，**不复用**。
- tag 与 versionName 逐字相同，**不带 `v`**；Release **绝不勾 Pre-release**（勾了应用内更新检查会 404 静默失效）。

---

## 8. 历史里程碑

| 版本 | versionCode | 内容 |
|---|---|---|
| `3.1.2-work.1` | 202 | 合并上游 3.1.2（bettertext / ConversationInbox / 聊天列表拆分 / 浮动胶囊 / 后台命令）；**脉搏模式完成**（心跳、计划任务、监视器、通知与短信感知、独立记忆与做梦、闹钟、保活）；顶栏玻璃坐标修正、会话内图片修复、视频输入 |
| `3.1.1-work.2` | 201 | 工作区 Linux 环境改造：12 个发行版 × 3 档、SHA-256 校验、镜像回退、装失败恢复原环境 |
| `3.1.1-work.1` | 200 | 合并上游 3.1.1（SSH 远程工作区、终端、双渠道构建、包体 47→16 MB）；阶段三收尾；DeepSeek 网页版粘贴登录；数据库 33 |
