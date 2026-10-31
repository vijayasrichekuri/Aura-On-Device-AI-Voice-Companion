package com.example.aura

import android.content.Context
import android.util.Log
import com.whispercpp.whisper.WhisperContext
import com.whispercpp.whisper.WhisperSegment
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class WhisperASR(
    private val context: Context
) {

    companion object {
        private const val TAG = "WhisperASR"
    }

    private var whisperContext: WhisperContext? = null

    // =====================================================
    // LOAD WHISPER MODEL
    // =====================================================

    fun loadModel() {

        if (whisperContext == null) {

            Log.d(
                TAG,
                "Loading Whisper model..."
            )

            whisperContext =
                WhisperContext.createContextFromAsset(
                    context.assets,
                    "models/ggml-tiny.en.bin"
                )

            Log.d(
                TAG,
                "Whisper model loaded successfully"
            )
        }
    }

    // =====================================================
    // NORMAL TRANSCRIPTION
    // =====================================================

    suspend fun transcribe(
        wavFile: File
    ): String {

        require(wavFile.exists()) {
            "WAV file does not exist: ${wavFile.absolutePath}"
        }

        require(wavFile.length() > 44) {
            "WAV file is empty or invalid"
        }

        loadModel()

        Log.d(
            TAG,
            "Starting transcription: ${wavFile.absolutePath}"
        )

        val audio =
            readWavAsFloatArray(wavFile)

        require(audio.isNotEmpty()) {
            "No PCM audio samples found"
        }

        Log.d(
            TAG,
            "Audio samples ready: ${audio.size}"
        )

        val startTime =
            System.currentTimeMillis()

        Log.d(
            TAG,
            "Starting Whisper inference..."
        )

        Log.d(
            TAG,
            "ABOUT TO CALL WHISPER CONTEXT: ${whisperContext?.javaClass?.name}"
        )

        val result =
            whisperContext?.transcribeData(
                audio,
                printTimestamp = true
            )
                ?: throw IllegalStateException(
                    "Whisper context is not initialized"
                )

        val endTime =
            System.currentTimeMillis()

        val inferenceTime =
            (endTime - startTime) / 1000.0

        Log.d(
            TAG,
            "Whisper inference completed"
        )

        Log.d(
            TAG,
            "Whisper inference time: $inferenceTime seconds"
        )

        Log.d(
            TAG,
            "Transcript length: ${result.length}"
        )

        return result
    }

    // =====================================================
    // TRANSCRIBE WITH SEGMENTS
    // =====================================================

    suspend fun transcribeSegments(
        wavFile: File
    ): List<WhisperSegment> {

        require(wavFile.exists()) {
            "WAV file does not exist: ${wavFile.absolutePath}"
        }

        require(wavFile.length() > 44) {
            "WAV file is empty or invalid"
        }

        loadModel()

        Log.d(
            TAG,
            "Starting segment transcription: ${wavFile.absolutePath}"
        )

        val audio =
            readWavAsFloatArray(wavFile)

        require(audio.isNotEmpty()) {
            "No PCM audio samples found"
        }

        Log.d(
            TAG,
            "Audio samples ready for segments: ${audio.size}"
        )

        val startTime =
            System.currentTimeMillis()

        Log.d(
            TAG,
            "Starting Whisper segment inference..."
        )

        val segments =
            whisperContext?.transcribeSegments(
                audio
            )
                ?: throw IllegalStateException(
                    "Whisper context is not initialized"
                )

        val endTime =
            System.currentTimeMillis()

        val inferenceTime =
            (endTime - startTime) / 1000.0

        Log.d(
            TAG,
            "Whisper segment inference completed"
        )

        Log.d(
            TAG,
            "Segment inference time: $inferenceTime seconds"
        )

        Log.d(
            TAG,
            "Received ${segments.size} Whisper segments"
        )

        segments.forEachIndexed { index, segment ->

            Log.d(
                TAG,
                "APP SEGMENT $index | " +
                        "start=${segment.start}s | " +
                        "end=${segment.end}s | " +
                        "text=${segment.text}"
            )
        }

        return segments
    }

    // =====================================================
    // RELEASE WHISPER
    // =====================================================

    suspend fun release() {

        try {

            whisperContext?.release()

            Log.d(
                TAG,
                "Whisper context released"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error releasing Whisper context",
                e
            )
        }

        whisperContext = null
    }

    // =====================================================
    // READ PCM WAV — OPTIMIZED
    // =====================================================

    private fun readWavAsFloatArray(
        file: File
    ): FloatArray {

        // Keep one copy of the WAV bytes.
        val bytes =
            file.readBytes()

        require(bytes.size > 44) {
            "Invalid WAV file: ${file.name}"
        }

        Log.d(
            TAG,
            "WAV file size: ${bytes.size} bytes"
        )

        // Read PCM directly from the original array.
        // Avoid creating a second array using copyOfRange().
        val pcmOffset = 44
        val pcmByteCount = bytes.size - pcmOffset

        require(pcmByteCount >= 2) {
            "PCM section is empty or invalid"
        }

        val buffer =
            ByteBuffer
                .wrap(
                    bytes,
                    pcmOffset,
                    pcmByteCount
                )
                .order(
                    ByteOrder.LITTLE_ENDIAN
                )

        val sampleCount =
            pcmByteCount / 2

        val samples =
            FloatArray(sampleCount)

        var peak = 0

        for (i in 0 until sampleCount) {

            val pcmSample =
                buffer.short.toInt()

            val absoluteValue =
                abs(pcmSample)

            if (absoluteValue > peak) {
                peak = absoluteValue
            }

            samples[i] =
                pcmSample / 32768.0f
        }

        Log.d(
            TAG,
            "PCM bytes: $pcmByteCount"
        )

        Log.d(
            TAG,
            "PCM samples: $sampleCount"
        )

        Log.d(
            TAG,
            "PCM peak: $peak"
        )

        Log.d(
            TAG,
            "First sample: ${samples.firstOrNull() ?: 0.0f}"
        )

        Log.d(
            TAG,
            "Last sample: ${samples.lastOrNull() ?: 0.0f}"
        )

        if (peak == 0) {

            Log.w(
                TAG,
                "WARNING: PCM audio is completely silent"
            )

        } else if (peak < 100) {

            Log.w(
                TAG,
                "WARNING: PCM audio amplitude is very low"
            )

        } else {

            Log.d(
                TAG,
                "PCM audio contains signal"
            )
        }

        return samples
    }
}