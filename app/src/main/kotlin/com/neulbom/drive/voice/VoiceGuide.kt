package com.neulbom.drive.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/** 말하기/멈추기. 화면 코드는 이 인터페이스만 쓴다(테스트에서 가짜로 바꿀 수 있게). */
interface Speaker {
    fun speak(text: String)
    fun stop()
}

/**
 * 선택형 음성 안내(기본 꺼짐). Android 기본 TTS로 화면 문장을 **읽기만** 한다 — 음성 입력·녹음은 하지 않는다.
 * 어르신이 듣기 편하도록 조금 천천히(0.9배) 읽고, 내비게이션 안내보다 앞서지 않도록 '도우미' 용도로 낸다.
 * TODO(PLEOS-6): 차량 플랫폼 TTS 사용이 권장되면 교체.
 */
class VoiceGuide(private val context: Context) : Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    override fun speak(text: String) {
        val engine = tts
        if (engine == null) {
            pending = text
            tts = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    tts?.language = Locale.KOREAN
                    tts?.setSpeechRate(0.9f)
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    pending?.let { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "neulbom") }
                    pending = null
                }
            }
            return
        }
        if (ready) engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "neulbom") else pending = text
    }

    override fun stop() {
        pending = null
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
