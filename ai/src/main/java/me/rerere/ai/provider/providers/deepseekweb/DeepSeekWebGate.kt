package me.rerere.ai.provider.providers.deepseekweb

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Process-wide serial gate for the web endpoint. */
internal class DeepSeekWebGate {
    private val mutex = Mutex()

    suspend fun <T> withPermit(minMs: Int, maxMs: Int, block: suspend () -> T): T = mutex.withLock {
        val min = minMs.coerceIn(1_000, 15_000).toLong()
        val max = maxMs.coerceIn(min.toInt(), 15_000).toLong()
        delay(Random.nextLong(min, max + 1))
        block()
    }
}
