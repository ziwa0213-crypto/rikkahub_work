# 开发进度 / Development Status

> 最后更新：**2026-11-18**
> 本文件**只记录当前正在开发的版本**：`3.3.0-work.1`（尚未发布）。功能说明见 [README.md](./README.md)，版本变更见 [CHANGES.md](./CHANGES.md)。
> 上一版 `3.2.0-work.3` / 206 已发布，其内容见 Releases 页。

---

## 0. 一句话状态

**上游 3.3.0 已合并完毕，本地模型（llama.cpp）已完成并出过包；现在在做第三批修复，修完才发版。**

- 版本号：`3.3.0-work.1` / versionCode **207**（未发布前不升）
- 数据库：**33 → 34**（装上之后不可回退，先备份）
- 包名：`me.rerere.rikkahub.plus`

---

## 1. 这一版包含什么

### 1.1 合并上游 3.3.0

| 变化 | 说明 |
|---|---|
| DeepSeek 语音 | 语音识别与合成本地接入，可直接对话 |
| DeepSeek 记录导入 | 可把 DeepSeek 的聊天记录导进来 |
| 气泡分段 | 助手回复按段落拆分渲染 |
| 工作区共享存储 | 外部存储挂载到工作区 |
| Vertex Interactions | 新的 Google 接口形态 |
| 安全模式 | 清理超大设置，避免设置撑爆 |
| 连接测试弹窗 | 供应商连通性测试结果集中展示 |
| Phone Use | 跟进上游：不再操作微信 |

合并时保留本 fork 的全部既有能力（F004 审批、F007 reasoning 回传、F009 网页版边界、F012 群组、F013 浏览器、F015 脉搏、F017 工作区、F019 闹钟）。

### 1.2 本地模型（llama.cpp）

在手机上直接跑 GGUF，不联网、不消耗 API 额度。

- **内置 5 个模型**（按体积升序）：LFM3 1.7B / Phi-4 mini / Qwen3.5 4B / Gemma 4 E2B / Qwen3.5 9B；也支持从文件导入。
- **视觉编码器**单独下载，模型声明支持时可读图片 / 音频 / 视频（视频抽帧，最多 16 帧）。
- **显式加载**：不再"选完就能用"，聊天页输入框上方有悬浮胶囊（颜色跟随主题），点一下加载，显示权重 / 上下文 / 视觉编码器三个阶段与秒数；失败可看详情并重试。
- **后台提醒**：退到后台 30 秒后发一条静默通知，**只有你点「卸载」才卸**；正在生成时排队到本轮结束；回到前台自动撤销。
- **短上下文压缩**：按窗口分档限制压缩请求的输入与摘要长度（4K→1.5K/300、8K→3K/500、16K→6K/800、更大→12K/1200），阈值 .65/.70/.75/.80。
- **用量上报**：每个生成轮次回报真实输入/输出 token，供上下文显示与自动压缩使用（此前本地模型的自动压缩从不触发）。

---

## 2. 第三批：正在修的问题

以下全部来自 2026-11-18 的实机测试，规范见 `任务书/F020 本地模型/补充三·回归修复与工作区上传.md`。

| # | 问题 | 状态 |
|---|---|---|
| A | **加载胶囊盖在所有页面上** —— 用了 `Popup`（独立窗口），侧边抽屉的遮罩、进入统计页都盖不住它 | 待实施 |
| B | **「你好」就报上下文过长** —— 输入预算只有 `contextSize − 2048`，4K 窗口下只剩 2048，系统提示 + 一个工具 schema 就吃满；且 `nCtx` 被按 `availMem` 压到 512 | 待实施 |
| C | **LFM3 加载成功但不出字** —— 该 GGUF 里没有 `tokenizer.chat_template`，llama.cpp 回落内置 ChatML，模型不认，立刻 EOS | 待实施 |
| D | **视频直传报 422** —— 非 Google 端点不接受 `video_url`（`unknown variant`） | 待实施 |
| E | **视频 / zip 传不上** —— 只有"没封面的缩略图"；改为存进工作区，消息里放文件链接 | 待实施 |
| F | **模型「视频」开关多余** —— 视频改走工作区后这个开关没有意义，删掉并把内置工具说明改回原文案 | 待实施 |
| G | **长按本地模型直达表单多余** —— 本地供应商已有「配置 / 模型」两个页签，改回普通入口 | 待实施 |

### 2.1 为什么 LFM3 不出字（已定性的根因）

用 Range 读取 `LFM3-1.7B-Uncensored-AD-Q6_K.gguf` 的元数据：`general.architecture = lfm3`（架构是认的），但**没有 `tokenizer.chat_template`**。GGUF 的键按字典序排列，它若存在必然排在 `tokenizer.ggml.model` 之前——不在。源头 `LiquidAI/LFM3-1.7B-Base` 的 `tokenizer_config.json` 里 `chat_template` 也是 `null`。

于是请求被按 ChatML 渲染，LFM3 看到的是不认识的格式，直接结束。修法是让本地模型支持**手动指定对话模板**（自动 / 内置预设 / 自定义 Jinja），并在"生成了零个字符"时明确报错，而不是静默空回复。

### 2.2 为什么 16 GB 机只能开 512 上下文

`ContextPlanner` 用 `availMem * 0.55 − fileBytes − 512 MB` 估算，而 `fileBytes` 是 **mmap** 的权重（`llama_jni.cpp` 明确 `LLAMA_LOAD_MODE_MMAP`），干净页可被内核回收，不该按常驻内存全额扣。2.85 GB 的 Phi-4 mini 一扣，"可用"直接归零，上下文落到地板 512——于是第一条消息就放不下。

修法：`nCtx` 只由「模型训练上限 × 用户设置 × 16384」决定；`availMem` 只用来做提示；`outputBudget` 封顶到窗口的 1/4。

---

## 3. 验证状态

| 项 | 状态 |
|---|---|
| 编译 / JVM 单测 | 通过（Codex 本机执行，含 llama-cpp 与 app 模块） |
| APK 构建 | 通过，ARM64 签名 Release，约 21.7 MB |
| 三语 key 一致性 | 通过（各 2,683 条） |
| 真机 | **未系统性跑完**。已确认：加载/卸载可用、胶囊可点、门禁不再硬拒；未确认：通知动作、16 GB 下的真实表现、150K 对话压缩、用量日志 |

> 本文件不把编译与 JVM 测试当成真机通过。

---

## 4. 下一步

1. 实施第三批 A~G，出一版 Debug 自用包，先验 A / B / C 三项
2. 本地模型**默认上下文降到 4096**（当前 16384，对 16 GB 机偏大）
3. N1：KV 量化（q8_0）与内存预算模型
4. 评估 Vulkan 后端（不用换模型文件，同一份 gguf；判定点见 `docs/40`）
5. N22：LiteRT-LM 作为第二引擎（GPU delegate，模型是另一套 `.litertlm`）

---

## English Summary

**`3.3.0-work.1` (versionCode 207) is in development; the previous `3.2.0-work.3` (206) is the current release.**
Database moves from 33 to 34 and cannot be rolled back.

This version merges **upstream 3.3.0** (DeepSeek speech, DeepSeek history import, paragraph-split bubbles,
shared-storage workspaces, Vertex Interactions, a safe mode that trims oversized settings, connection-test
dialog) and adds **on-device models** running on llama.cpp: five built-in GGUF models (1.7B–9B), file import,
separate vision encoders, image/audio/video input, **explicit loading** behind a capsule above the input bar,
a 30-second background reminder with a user-triggered unload, window-tiered compression budgets, and real token
usage reporting.

**In progress:** a third batch fixing what testing turned up — the loading capsule floating above drawers and
other pages, an input budget that made even "hi" overflow on a 4K window, LFM3 producing no output because its
GGUF has no chat template (llama.cpp falls back to ChatML), `422 unknown variant video_url` from non-Google
endpoints, and video/zip uploads that should go to the workspace instead of the chat bubble.

**Next:** ship the fixes, lower the default local context to 4K, KV quantization, then evaluate a Vulkan backend
(same GGUF files) and LiteRT-LM as a second engine.
