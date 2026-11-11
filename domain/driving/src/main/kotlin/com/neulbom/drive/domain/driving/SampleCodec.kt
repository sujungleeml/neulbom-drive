package com.neulbom.drive.domain.driving

import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.core.model.SignalSample

/** 표본 목록 ↔ 문자열. 로컬 DB 한 행이 커지지 않도록 묶음 단위로 나눈다. 형식: `신호순번,t,값;…` */
object SampleCodec {
    fun encodeChunks(samples: List<SignalSample>, chunkSize: Int = 2000): List<String> =
        samples.chunked(chunkSize).map { chunk ->
            chunk.joinToString(";") { "${it.signal.ordinal},${it.tMonoMs},${it.value}" }
        }

    fun decode(text: String): List<SignalSample> =
        if (text.isEmpty()) emptyList()
        else text.split(';').map { part ->
            val (s, t, v) = part.split(',')
            SignalSample(SignalId.entries[s.toInt()], t.toLong(), v.toDouble())
        }
}
