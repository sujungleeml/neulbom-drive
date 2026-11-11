package com.neulbom.drive.core

import com.neulbom.drive.core.id.SeededUuidGenerator
import com.neulbom.drive.core.time.FakeClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreTest {
    @Test
    fun seededIdsAreReproducible() {
        val a = SeededUuidGenerator(42)
        val b = SeededUuidGenerator(42)
        assertEquals(List(5) { a.newId() }, List(5) { b.newId() })
    }

    @Test
    fun fakeClockAdvancesBothTimelines() {
        val clock = FakeClock()
        val start = clock.now()
        clock.advanceMs(1500)
        assertEquals(1500, clock.now().toEpochMilli() - start.toEpochMilli())
        assertEquals(1500, clock.monotonicMs())
    }


}

class RobustStatsTest {
    @Test
    fun medianAndMad() {
        val v = listOf(1.0, 2.0, 3.0, 4.0, 100.0)
        assertEquals(3.0, com.neulbom.drive.core.stats.RobustStats.median(v))
        assertEquals(1.0, com.neulbom.drive.core.stats.RobustStats.mad(v))
        assertEquals(2.5, com.neulbom.drive.core.stats.RobustStats.median(listOf(4.0, 1.0, 2.0, 3.0)))
    }

    @Test
    fun robustZUsesScaledMadAndEpsilon() {
        val z = com.neulbom.drive.core.stats.RobustStats.robustZ(5.0, 3.0, 1.0, 0.0)
        assertEquals(2.0 / 1.4826, z, 1e-9)
        assertEquals(2.0 / 0.5, com.neulbom.drive.core.stats.RobustStats.robustZ(5.0, 3.0, 0.0, 0.5), 1e-9)
    }
}
