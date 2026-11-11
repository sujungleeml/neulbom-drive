package com.neulbom.drive.domain.driving

import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.core.model.SignalSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleCodecTest {
    @Test fun roundTripsInChunks() {
        val samples = (0 until 4500).map { SignalSample(if (it % 2 == 0) SignalId.SPEED else SignalId.LONG_ACCEL, it * 100L, it * 0.125 - 3.5) }
        val chunks = SampleCodec.encodeChunks(samples, chunkSize = 2000)
        assertEquals(3, chunks.size)
        assertTrue(chunks.all { it.length < 200_000 })
        assertEquals(samples, chunks.flatMap { SampleCodec.decode(it) })
    }

    @Test fun emptyListEncodesToNoChunks() {
        assertTrue(SampleCodec.encodeChunks(emptyList()).isEmpty())
    }
}
