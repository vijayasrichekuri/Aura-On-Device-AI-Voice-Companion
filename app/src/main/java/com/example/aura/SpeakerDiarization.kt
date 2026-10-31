
package com.example.aura

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig

class SpeakerDiarization(
    private val context: Context
) {

    companion object {
        private const val TAG = "SpeakerDiarization"

        private const val PYANNOTE_MODEL =
            "models/diarization/model.onnx"

        private const val EMBEDDING_MODEL =
            "models/diarization/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"

        private const val NUM_THREADS = 2
        private const val NUM_CLUSTERS = 2
        private const val CLUSTER_THRESHOLD = 0.65f
        private const val MIN_DURATION_ON = 0.3f
        private const val MIN_DURATION_OFF = 0.5f

        private const val SAME_SPEAKER_MERGE_GAP = 0.75f
        private const val ISOLATED_FLIP_MAX_DURATION = 1.0f
        private const val ISOLATED_FLIP_MAX_GAP = 1.5f
    }

    private var diarization: OfflineSpeakerDiarization? = null

    /**
     * Initialize the Sherpa-ONNX speaker diarization engine.
     */
    fun loadModel() {

        if (diarization != null) {
            Log.d(TAG, "Diarization model already loaded")
            return
        }

        try {
            Log.d(TAG, "======================================")
            Log.d(TAG, "Initializing speaker diarization")
            Log.d(TAG, "======================================")

            val pyannoteConfig =
                OfflineSpeakerSegmentationPyannoteModelConfig(
                    PYANNOTE_MODEL
                )

            val segmentationConfig =
                OfflineSpeakerSegmentationModelConfig(
                    pyannoteConfig,
                    NUM_THREADS,
                    false,
                    "cpu"
                )

            val embeddingConfig =
                SpeakerEmbeddingExtractorConfig(
                    EMBEDDING_MODEL,
                    NUM_THREADS,
                    false,
                    "cpu"
                )

            val clusteringConfig =
                FastClusteringConfig(
                    NUM_CLUSTERS,
                    CLUSTER_THRESHOLD
                )

            val config =
                OfflineSpeakerDiarizationConfig(
                    segmentationConfig,
                    embeddingConfig,
                    clusteringConfig,
                    MIN_DURATION_ON,
                    MIN_DURATION_OFF
                )

            diarization =
                OfflineSpeakerDiarization(
                    context.assets,
                    config
                )

            Log.d(
                TAG,
                "Speaker diarization initialized successfully"
            )

            Log.d(
                TAG,
                "Expected sample rate: ${diarization?.sampleRate()}"
            )

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to initialize speaker diarization",
                e
            )

            diarization = null
            throw e
        }
    }

    /**
     * Process 16 kHz mono Float32 PCM audio.
     */
    fun process(
        audio: FloatArray
    ): List<SpeakerSegment> {

        require(audio.isNotEmpty()) {
            "Cannot diarize empty audio"
        }

        loadModel()

        val engine =
            diarization
                ?: throw IllegalStateException(
                    "Diarization engine is not initialized"
                )

        Log.d(TAG, "======================================")
        Log.d(TAG, "Starting speaker diarization")
        Log.d(TAG, "Audio samples: ${audio.size}")
        Log.d(TAG, "Audio duration: ${audio.size / 16000.0}s")
        Log.d(TAG, "Sample rate: ${engine.sampleRate()}")
        Log.d(TAG, "======================================")

        val startTime = System.currentTimeMillis()

        val segments = engine.process(audio)

        val endTime = System.currentTimeMillis()
        val processingTime = (endTime - startTime) / 1000.0

        Log.d(
            TAG,
            "Diarization completed in $processingTime seconds"
        )

        Log.d(
            TAG,
            "Received ${segments.size} speaker segments"
        )

        val rawResult =
            segments
                .map { segment ->
                    SpeakerSegment(
                        start = segment.start,
                        end = segment.end,
                        speaker = segment.speaker
                    )
                }
                .sortedBy { it.start }

        Log.d(TAG, "----- RAW DIARIZATION SEGMENTS -----")

        rawResult.forEachIndexed { index, segment ->
            Log.d(
                TAG,
                "RAW SEGMENT $index | " +
                        "start=${segment.start}s | " +
                        "end=${segment.end}s | " +
                        "speaker=${segment.speaker}"
            )
        }

        val result = stabilizeSegments(rawResult)

        Log.d(TAG, "----- STABILIZED DIARIZATION SEGMENTS -----")

        result.forEachIndexed { index, segment ->
            Log.d(
                TAG,
                "STABLE SEGMENT $index | " +
                        "start=${segment.start}s | " +
                        "end=${segment.end}s | " +
                        "speaker=${segment.speaker}"
            )
        }

        return result
    }

    /**
     * Conservatively stabilize nearby speaker segments.
     */
    private fun stabilizeSegments(
        input: List<SpeakerSegment>
    ): List<SpeakerSegment> {

        if (input.isEmpty()) {
            return emptyList()
        }

        val sorted = input.sortedBy { it.start }

        // Pass 1: merge nearby segments assigned to the same speaker.
        val merged = mutableListOf<SpeakerSegment>()

        for (segment in sorted) {

            if (merged.isEmpty()) {
                merged.add(segment)
                continue
            }

            val previous = merged.last()
            val gap = segment.start - previous.end

            if (
                previous.speaker == segment.speaker &&
                gap >= 0f &&
                gap <= SAME_SPEAKER_MERGE_GAP
            ) {
                merged[merged.lastIndex] =
                    SpeakerSegment(
                        start = previous.start,
                        end = maxOf(previous.end, segment.end),
                        speaker = previous.speaker
                    )
            } else {
                merged.add(segment)
            }
        }

        // Pass 2: correct a short isolated A-B-A label flip.
        val stabilized = merged.toMutableList()

        for (i in 1 until stabilized.lastIndex) {

            val previous = stabilized[i - 1]
            val current = stabilized[i]
            val next = stabilized[i + 1]

            val currentDuration = current.end - current.start
            val gapBefore = current.start - previous.end
            val gapAfter = next.start - current.end

            val isolatedFlip =
                previous.speaker == next.speaker &&
                        current.speaker != previous.speaker &&
                        currentDuration <= ISOLATED_FLIP_MAX_DURATION &&
                        gapBefore <= ISOLATED_FLIP_MAX_GAP &&
                        gapAfter <= ISOLATED_FLIP_MAX_GAP

            if (isolatedFlip) {

                Log.d(
                    TAG,
                    "STABILIZATION: A-B-A flip detected; " +
                            "changing speaker ${current.speaker} -> " +
                            "${previous.speaker}"
                )

                stabilized[i] =
                    current.copy(
                        speaker = previous.speaker
                    )
            }
        }

        // Pass 3: merge same-speaker segments after stabilization.
        val finalResult = mutableListOf<SpeakerSegment>()

        for (segment in stabilized) {

            if (finalResult.isEmpty()) {
                finalResult.add(segment)
                continue
            }

            val previous = finalResult.last()
            val gap = segment.start - previous.end

            if (
                previous.speaker == segment.speaker &&
                gap >= 0f &&
                gap <= SAME_SPEAKER_MERGE_GAP
            ) {
                finalResult[finalResult.lastIndex] =
                    SpeakerSegment(
                        start = previous.start,
                        end = maxOf(previous.end, segment.end),
                        speaker = previous.speaker
                    )
            } else {
                finalResult.add(segment)
            }
        }

        return finalResult
    }

    /**
     * Process an existing WAV file.
     */
    fun processWav(
        wavFile: java.io.File
    ): List<SpeakerSegment> {

        require(wavFile.exists()) {
            "WAV file does not exist: ${wavFile.absolutePath}"
        }

        require(wavFile.length() > 44) {
            "WAV file is empty or invalid"
        }

        Log.d(
            TAG,
            "Processing WAV file: ${wavFile.absolutePath}"
        )

        val audio = readWavAsFloatArray(wavFile)

        return process(audio)
    }

    /**
     * Release the native Sherpa diarization engine.
     */
    fun release() {

        try {
            diarization?.release()

            Log.d(
                TAG,
                "Diarization engine released"
            )

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error releasing diarization engine",
                e
            )
        }

        diarization = null
    }

    /**
     * Read PCM WAV data without creating an additional PCM byte array.
     */
    private fun readWavAsFloatArray(
        file: java.io.File
    ): FloatArray {

        val bytes = file.readBytes()

        require(bytes.size > 44) {
            "Invalid WAV file: ${file.name}"
        }

        // Read PCM from the original byte array instead of copyOfRange().
        val pcmOffset = 44
        val pcmByteCount = bytes.size - pcmOffset

        require(pcmByteCount >= 2) {
            "WAV contains no PCM audio"
        }

        val sampleCount = pcmByteCount / 2

        val buffer =
            java.nio.ByteBuffer
                .wrap(
                    bytes,
                    pcmOffset,
                    pcmByteCount
                )
                .order(
                    java.nio.ByteOrder.LITTLE_ENDIAN
                )

        val samples = FloatArray(sampleCount)

        for (i in 0 until sampleCount) {
            val sample = buffer.short.toInt()
            samples[i] = sample / 32768.0f
        }

        Log.d(
            TAG,
            "WAV converted to Float32"
        )

        Log.d(
            TAG,
            "Samples: ${samples.size}"
        )

        Log.d(
            TAG,
            "Duration: ${samples.size / 16000.0}s"
        )

        return samples
    }
}

/**
 * Aura's application-level speaker segment.
 */
data class SpeakerSegment(
    val start: Float,
    val end: Float,
    val speaker: Int
)
