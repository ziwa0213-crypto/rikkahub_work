package me.rerere.rikkahub.service.pulse

import org.junit.Assert.*
import org.junit.Test
import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.datastore.PulseTask

class PulseClaimsTest {
    private var stored = PulseClaim()
    private var counter = 0
    private fun store() = PulseClaims({ stored }, { stored = it }, { "owner-${++counter}" })
    @Test fun `active lease blocks competing paths and different slots`() {
        val claims = store()
        assertNotNull(claims.claim("heartbeat:1", 100, false))
        assertNull(claims.claim("task:a:2", 101, false))
        assertNull(claims.claim("heartbeat:1", 101, false))
    }
    @Test fun `expired lease survives recreation and can be taken over`() {
        val original = store().claim("heartbeat:1", 100, false)!!
        val successor = store().claim("heartbeat:1", 100 + PulseClaims.LEASE_MS, false)!!
        assertNotEquals(original.owner, successor.owner)
    }
    @Test fun `stale owner cannot renew or finish successor`() {
        val claims = store()
        val original = claims.claim("heartbeat:1", 100, false)!!
        val successor = claims.claim("heartbeat:1", 100 + PulseClaims.LEASE_MS, false)!!
        assertFalse(claims.renew(original, 1000))
        assertFalse(claims.finish(original))
        assertEquals(successor, stored)
    }
    @Test fun `renewal is not a generation deadline`() {
        val claims = store()
        val claim = claims.claim("task:a:1", 100, false)!!
        repeat(100) { tick -> assertTrue(claims.renew(claim, 100 + tick * PulseClaims.RENEW_MS)) }
        assertFalse(stored.done)
        assertTrue(claims.finish(claim))
    }
    @Test fun `completed slot is skipped while next slot is accepted`() {
        val claims = store()
        assertTrue(claims.finish(claims.claim("heartbeat:1", 100, false)!!))
        assertNull(claims.claim("heartbeat:1", 10000, false))
        assertNotNull(claims.claim("heartbeat:2", 10000, false))
    }
    @Test fun `in process activity blocks expired recovery`() {
        assertNull(store().claim("heartbeat:1", 100, true))
    }

    @Test fun `heartbeat claim remains recoverable after its later start is persisted`() {
        val config = PulseConfig(enableHeartbeat = true, lastHeartbeatAt = 101L)
        val original = store().claim("heartbeat:100", 100, false)!!
        assertTrue(original.isPendingFor(config))
        assertNotNull(store().claim(original.key, original.until, false))
    }

    @Test fun `done or disabled heartbeats cannot keep a recovery alarm alive`() {
        val claim = PulseClaim(key = "heartbeat:100")
        assertFalse(claim.isPendingFor(PulseConfig(enableHeartbeat = false)))
        assertFalse(claim.copy(done = true).isPendingFor(PulseConfig(enableHeartbeat = true)))
        assertFalse(claim.copy(key = "heartbeat:invalid").isPendingFor(PulseConfig(enableHeartbeat = true)))
    }

    @Test fun `only an enabled unfinished task can be recovered`() {
        val task = PulseTask(id = "a", enabled = true, lastSlot = 100)
        val config = PulseConfig(enableScheduledTasks = true, tasks = listOf(task))
        assertTrue(PulseClaim(key = "task:a:101").isPendingFor(config))
        assertFalse(PulseClaim(key = "task:a:100").isPendingFor(config))
        assertFalse(PulseClaim(key = "task:deleted:101").isPendingFor(config))
        assertFalse(PulseClaim(key = "task:a:101").isPendingFor(config.copy(enableScheduledTasks = false)))
        assertFalse(PulseClaim(key = "task:a:101").isPendingFor(config.copy(tasks = listOf(task.copy(enabled = false)))))
        assertFalse(PulseClaim(key = "watcher:a:101").isPendingFor(config))
    }
}
