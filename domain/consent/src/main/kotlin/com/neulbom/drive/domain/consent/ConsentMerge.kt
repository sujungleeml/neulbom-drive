package com.neulbom.drive.domain.consent

/**
 * 차량과 웹 양쪽에서 바뀔 수 있는 동의를 맞추는 규칙: **유형별로 가장 늦게 바뀐 값이 이긴다.**
 * 철회도 같은 규칙을 따르되, 차량은 적용(철회)을 먼저 하고 서버로 올린다.
 */
object ConsentMerge {
    /** 원격 기록 중 로컬 최신보다 늦고 값이 다른 것(차량에 적용할 것). */
    fun toApply(local: Map<ConsentType, ConsentRecord>, remote: List<ConsentRecord>): List<ConsentRecord> =
        latest(remote).values.filter { r ->
            val l = local[r.type]
            (l == null || r.changedAt.isAfter(l.changedAt)) && l?.granted != r.granted
        }

    /** 로컬 최신 중 원격 최신보다 늦은 것(서버에 올릴 것). */
    fun toPush(local: Map<ConsentType, ConsentRecord>, remote: List<ConsentRecord>): List<ConsentRecord> {
        val r = latest(remote)
        return local.values.filter { l -> r[l.type]?.changedAt?.isBefore(l.changedAt) ?: true }
    }

    fun latest(records: List<ConsentRecord>): Map<ConsentType, ConsentRecord> =
        records.groupBy { it.type }.mapValues { (_, v) -> v.maxBy { it.changedAt } }
}
