package com.example.aura

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs

class VoiceActivityDetector {

    private var audioRecord: AudioRecord? = null
    private var isListening = false

    // Audio configuration
    private val sampleRate = 16000

    // Initial speech detection threshold
    private val speechThreshold = 1000

    // Speech must continue this long before a session starts
    private val minimumSpeechDurationMs = 800L

    // Silence for this long ends the session
    private val sessionEndSilenceMs = 8000L

    fun startListening(
        onSpeechDetected: () -> Unit,
        onSilenceDetected: () -> Unit
    ) {

        if (isListening) {
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (bufferSize <= 0) {
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()

        isListening = true

        Thread {

            val buffer = ShortArray(bufferSize)

            var speechStartTime = 0L
            var lastSpeechTime = 0L

            var sessionStarted = false
            var silenceReported = false

            while (isListening) {

                val samplesRead = audioRecord?.read(
                    buffer,
                    0,
                    buffer.size
                ) ?: 0

                if (samplesRead <= 0) {
                    continue
                }

                // Calculate average audio energy
                var sum = 0L

                for (i in 0 until samplesRead) {
                    sum += abs(buffer[i].toInt())
                }

                val averageLevel =
                    sum.toDouble() / samplesRead

                val currentTime =
                    System.currentTimeMillis()

                val speechDetected =
                    averageLevel > speechThreshold

                // --------------------------------
                // SPEECH DETECTED
                // --------------------------------

                if (speechDetected) {

                    lastSpeechTime = currentTime

                    if (speechStartTime == 0L) {
                        speechStartTime = currentTime
                    }

                    val speechDuration =
                        currentTime - speechStartTime

                    if (
                        !sessionStarted &&
                        speechDuration >= minimumSpeechDurationMs
                    ) {

                        sessionStarted = true
                        silenceReported = false

                        onSpeechDetected()
                    }

                }

                // --------------------------------
                // SILENCE DETECTED
                // --------------------------------

                else {

                    if (sessionStarted) {

                        val silenceDuration =
                            currentTime - lastSpeechTime

                        if (
                            silenceDuration >=
                            sessionEndSilenceMs &&
                            !silenceReported
                        ) {

                            silenceReported = true
                            sessionStarted = false

                            speechStartTime = 0L
                            lastSpeechTime = 0L

                            onSilenceDetected()
                        }

                    } else {

                        speechStartTime = 0L
                    }
                }
            }

        }.start()
    }

    fun stopListening() {

        isListening = false

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // Ignore stop errors
        }

        audioRecord?.release()

        audioRecord = null
    }
}