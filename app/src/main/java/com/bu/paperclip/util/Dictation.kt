package com.bu.paperclip.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Continuous dictation with the phone's default speech service, like the keyboard microphone: words show up
 * while you speak, pauses don't end it, and it stops only on [stop]. On Android 13+ it asks for a segmented
 * session, which keeps listening across pauses where the service supports it. Elsewhere each recognition
 * session ends at a pause, so the next one starts right away. Must be used from the main thread.
 */
class Dictation(
    private val ctx: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onError: (Int) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    // True between startListening and the session's last callback; late callbacks from a finished session are ignored.
    private var inSession = false
    private var failures = 0
    private var lastSpeech = 0L

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        // Hints only; many services keep their own pause length.
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Segments arrive in onSegmentResults and the session lasts this long, whatever the pauses.
                // Google's service reads this length as an Int and ignores the segmented session for a Long.
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, SESSION_MS)
            }
        }

    val isActive: Boolean get() = active

    fun start() {
        if (active) return
        active = true
        failures = 0
        lastSpeech = SystemClock.elapsedRealtime()
        onListening(true)
        listen(fresh = true)
    }

    /** Stops listening; whatever was said last still arrives through onFinal. */
    fun stop() {
        if (!active) return
        active = false
        main.removeCallbacksAndMessages(null)
        recognizer?.stopListening()
        // Some services never answer after stopListening; don't leave the microphone looking busy.
        main.postDelayed({ if (!active) finish() }, 1500)
    }

    fun release() {
        active = false
        main.removeCallbacksAndMessages(null)
        finish()
    }

    private fun finish() {
        inSession = false
        recognizer?.destroy()
        recognizer = null
        onPartial("")
        onListening(false)
    }

    private fun heard() {
        failures = 0
        lastSpeech = SystemClock.elapsedRealtime()
    }

    /** The session ended on its own: start the next one, unless nobody has spoken for a long while. */
    private fun next(fresh: Boolean, delayMs: Long = 0) {
        if (!active) return finish()
        if (SystemClock.elapsedRealtime() - lastSpeech > IDLE_MS) {
            active = false
            return finish()
        }
        main.postDelayed({ if (active) listen(fresh) }, delayMs)
    }

    private fun listen(fresh: Boolean) {
        // Reusing the recognizer avoids the "busy" errors some phones give right after destroy; a fresh one
        // is only made after such an error.
        val r = recognizer?.takeIf { !fresh } ?: run {
            recognizer?.destroy()
            SpeechRecognizer.createSpeechRecognizer(ctx).also { it.setRecognitionListener(listener); recognizer = it }
        }
        inSession = true
        r.startListening(intent)
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(b: Bundle?) {
            val said = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (said.isNotBlank()) heard()
            onPartial(said)
        }

        override fun onSegmentResults(segmentResults: Bundle) {
            deliver(segmentResults)
        }

        override fun onEndOfSegmentedSession() {
            if (!inSession) return
            inSession = false
            next(fresh = false)
        }

        override fun onResults(b: Bundle?) {
            if (!inSession) return
            inSession = false
            deliver(b)
            next(fresh = false)
        }

        override fun onError(error: Int) {
            if (!inSession) return
            inSession = false
            val pause = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            val busy = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT ||
                error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
            when {
                !active -> finish()
                pause -> next(fresh = false)
                busy && failures < MAX_FAILURES -> { failures++; next(fresh = true, delayMs = 300L * failures) }
                else -> { active = false; finish(); onError(error) }
            }
        }

        override fun onBeginningOfSpeech() = heard()
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun deliver(b: Bundle?) {
        onPartial("")
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            heard()
            onFinal(it)
        }
    }

    private companion object {
        const val SESSION_MS = 60 * 1000
        const val IDLE_MS = 60 * 1000L
        const val MAX_FAILURES = 6
    }
}
