package com.neulbom.drive.domain.cognitive.protocol

import com.neulbom.drive.domain.cognitive.ProtocolRegistry
import com.neulbom.drive.domain.cognitive.ProvisionalProtocols
import com.neulbom.drive.domain.cognitive.ResponseCapture
import com.neulbom.drive.domain.cognitive.ProtocolStatus
import com.neulbom.drive.domain.cognitive.TaskId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskProtocolTest {
    @Test fun allProvisionalProtocolsAreMarkedProvisional() {
        assertTrue(ProvisionalProtocols.ALL.all { it.status == ProtocolStatus.PROVISIONAL })
        assertEquals(TaskId.entries.toSet(), ProvisionalProtocols.ALL.map { it.taskId }.toSet())
        assertEquals(listOf(TaskId.VISUAL_ATTENTION, TaskId.GO_NO_GO, TaskId.TASK_SWITCHING), ProvisionalProtocols.ALL.map { it.taskId })
    }

    @Test fun targetDurationsFollowSpecHypothesis() {
        assertEquals(listOf(55, 65, 60), ProvisionalProtocols.ALL.map { it.targetDurationSec })
        assertEquals(0.75, ProvisionalProtocols.GO_NO_GO.goRatio)
        assertEquals(600L, ProvisionalProtocols.GO_NO_GO.displayDurationMs)
    }

    @Test fun visualAttentionV3UsesVectorArrowStimulusSet() {
        // 화살표 크기·모양이 바뀌어 과제·자극 세트 버전을 함께 올렸다(v2 기록과 비교하지 않음).
        val va = ProvisionalProtocols.VISUAL_ATTENTION
        assertEquals(3, va.taskVersion)
        assertEquals("arrows-lr" to 2, va.stimulusSetId to va.stimulusSetVersion)
        assertEquals(2, ProvisionalProtocols.TASK_SWITCHING.taskVersion)
    }

    @Test fun registryKeepsEveryVersion() {
        // 과거 기록은 그 기록이 쓰인 버전의 규칙으로 다시 계산한다.
        val va2 = ProtocolRegistry.find(TaskId.VISUAL_ATTENTION, 2)!!
        val va3 = ProtocolRegistry.find(TaskId.VISUAL_ATTENTION, 3)!!
        assertEquals(1 to 2, va2.stimulusSetVersion to va3.stimulusSetVersion)
        assertEquals(ResponseCapture.RELEASE, ProtocolRegistry.find(TaskId.GO_NO_GO, 1)!!.responseCapture)
        assertEquals(ResponseCapture.TOUCH_DOWN, ProtocolRegistry.find(TaskId.GO_NO_GO, 2)!!.responseCapture)
        assertEquals(null, ProtocolRegistry.find(TaskId.TASK_SWITCHING, 3))
        ProvisionalProtocols.ALL.forEach { assertEquals(it, ProtocolRegistry.find(it.taskId, it.taskVersion)) }
    }

    @Test fun goNoGoV3ChangesOnlyTheStimulusSet() {
        // 자극 크기·색만 바뀌었으므로 과제·자극 세트 버전을 올리고 타이밍·시행 수·비율은 그대로 둔다.
        val v3 = ProtocolRegistry.find(TaskId.GO_NO_GO, 3)!!
        val v2 = ProtocolRegistry.find(TaskId.GO_NO_GO, 2)!!
        assertEquals(3 to 2, v3.taskVersion to v3.stimulusSetVersion)
        assertEquals(2 to 1, v2.taskVersion to v2.stimulusSetVersion)
        assertEquals(v2.copy(taskVersion = 3, stimulusSetVersion = 2), v3)
        assertEquals(500L to 800L, v2.displayDurationMs to v2.responseWindowMs)
    }

    @Test fun goNoGoV4SlowsOnlyTheTiming() {
        // PO A안: 표시 600ms, 간격 800~1200ms, 응답 제한 900ms. 자극·시행 수·Go 비율은 v3와 같다.
        val v4 = ProvisionalProtocols.GO_NO_GO
        val v3 = ProtocolRegistry.find(TaskId.GO_NO_GO, 3)!!
        assertEquals(4, v4.taskVersion)
        assertEquals(v3.copy(taskVersion = 4, displayDurationMs = 600, isiMinMs = 800, isiMaxMs = 1200, responseWindowMs = 900, targetDurationSec = 65), v4)
        assertEquals(v4, ProtocolRegistry.find(TaskId.GO_NO_GO, 4))
        assertEquals(1, ProtocolRegistry.ALL.count { it.taskId == TaskId.GO_NO_GO && it.taskVersion == 3 })
    }
}
