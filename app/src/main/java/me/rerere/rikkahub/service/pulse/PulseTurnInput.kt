package me.rerere.rikkahub.service.pulse

import me.rerere.ai.ui.UIMessage

/** Each automatic turn ends with a request-only user trigger, without rewriting the saved history. */
internal data class PulseTurnInput(val messages: List<UIMessage>, val systemInstruction: String?)

/** Fixed framework guidance for automatic and manual Pulse turns, separate from user-editable prompts. */
internal const val PULSE_TURN_CONSTRAINT = """## 时间与等待

当前这一轮是一次性执行的，没有"等一会儿再看"的能力。

不要用 shell 的 sleep、until、while 循环等阻塞命令去等一个将来的时间点：它会长时间占住这一轮，而且会和你等的那件事本身撞车。

需要稍后确认某件事，就再记一条计划任务（schedule_task），到点后调度器会重新唤醒你，那时再检查。

shell 用来执行即时操作；对于已经运行中的进程，允许用短 sleep 轮询状态或输出，但不要借此等待将来的时间点。"""

internal fun pulseTurnInput(history: List<UIMessage>, instruction: String): PulseTurnInput =
    PulseTurnInput(
        messages = history + UIMessage.user(instruction).copy(isSynthetic = true),
        systemInstruction = PULSE_TURN_CONSTRAINT,
    )
