package com.moifinansy.ui.lock

import com.moifinansy.data.FakeSettings
import com.moifinansy.domain.security.PinHasher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class LockManagerTest {
    private class MutableClock(var now: Long) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }

    @Test
    fun `locks at start and after background timeout`() = runTest {
        val settings = FakeSettings()
        val salt = PinHasher.newSalt()
        settings.setPin(PinHasher.hash("1234", salt), salt)
        settings.setLockAfterMinutes(1)
        val clock = MutableClock(0)
        val lock = LockManager(settings, clock)

        lock.onForeground()
        assertTrue(lock.locked.value)
        assertFalse(lock.tryPin("0000"))
        assertTrue(lock.tryPin("1234"))
        assertFalse(lock.locked.value)

        lock.onBackground()
        clock.now += 30_000
        lock.onForeground()
        assertFalse(lock.locked.value)

        lock.onBackground()
        clock.now += 61_000
        lock.onForeground()
        assertTrue(lock.locked.value)
    }

    @Test
    fun `without pin never locks`() = runTest {
        val lock = LockManager(FakeSettings(), Clock.systemUTC())
        lock.onForeground()
        assertFalse(lock.locked.value)
    }
}
