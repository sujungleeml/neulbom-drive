package com.neulbom.drive.core.id

import java.util.UUID
import kotlin.random.Random

fun interface IdGenerator {
    fun newId(): String
}

object RandomUuidGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}

/** 시드 고정 UUID 생성기 — 같은 시드면 같은 ID 순서를 낸다(재현성, T05). */
class SeededUuidGenerator(seed: Long) : IdGenerator {
    private val random = Random(seed)
    override fun newId(): String = UUID(random.nextLong(), random.nextLong()).toString()
}
