package com.neulbom.drive.vehicle

import com.neulbom.drive.core.time.Clock
import com.neulbom.drive.vehicle.api.UnavailableNaviAdapter
import com.neulbom.drive.vehicle.api.UnavailableSpeechAdapter
import com.neulbom.drive.vehicle.pleos.PleosVehicleAdapter
import kotlinx.coroutines.CoroutineScope

/**
 * pleos 플래버: 공식 SDK 검증 전까지 PleosVehicleAdapter는 안전 기본값(UNKNOWN)만 낸다.
 * TODO(PLEOS-6): Navi/Speech 어댑터는 API·권한 확인 후 교체.
 */
object VehicleModule {
    @Suppress("UNUSED_PARAMETER")
    fun create(clock: Clock, scope: CoroutineScope): VehicleBundle {
        val pleos = PleosVehicleAdapter(clock)
        return VehicleBundle(pleos, pleos, UnavailableNaviAdapter, UnavailableSpeechAdapter, simulator = null, sourceLabel = "Pleos (연동 준비 중)")
    }
}
