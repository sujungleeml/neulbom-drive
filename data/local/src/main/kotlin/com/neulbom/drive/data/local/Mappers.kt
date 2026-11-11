package com.neulbom.drive.data.local

import com.neulbom.drive.domain.consent.ConsentRecord
import com.neulbom.drive.domain.consent.ConsentType
import java.time.Instant

internal fun Instant.ms() = toEpochMilli()
internal fun Long.instant(): Instant = Instant.ofEpochMilli(this)


internal fun ConsentRecord.toEntity() = DriverConsentEntity(type = type.name, granted = granted, policyVersion = policyVersion, changedAt = changedAt.ms())
/** 이전 버전의 동의 목적(예: USAGE_ANALYTICS)은 무시한다. */
internal fun DriverConsentEntity.toDomainOrNull(): ConsentRecord? =
    ConsentType.entries.firstOrNull { it.name == type }?.let { ConsentRecord(it, granted, policyVersion, changedAt.instant()) }
