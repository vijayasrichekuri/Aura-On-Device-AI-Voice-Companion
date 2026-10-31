package com.example.aura

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class AudioPreprocessor {

    companion object {
        private const val TARGET_SAMPLE_RATE = 16000
        private const val TARGET_CHANNELS = 1
        private const val BITS_PER_SAMPLE = 16

        private const val NOISE_THRESHOLD = 500

        private const val LOW_CUTOFF_HZ = 80.0
        private const val HIGH_CUTOFF_HZ = 7000.0

        // 95% of maximum 16-bit PCM amplitude
        private const val NORMALIZATION_TARGET = 0.95
    }

    fun convertM4aToWav(
        inputFile: File,
        outputFile: File
    ): Boolean {

        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null

        try {

            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            var audioTrack = -1

            for (i in 0 until extractor.trackCount) {

                val format =
                    extractor.getTrackFormat(i)

                val mime =
                    format.getString(
                        MediaFormat.KEY_MIME
                    )

                if (mime?.startsWith("audio/") == true) {

                    audioTrack = i
                    break
                }
            }

            if (audioTrack == -1) {
                return false
            }

            extractor.selectTrack(audioTrack)

            val inputFormat =
                extractor.getTrackFormat(audioTrack)

            val mime =
                inputFormat.getString(
                    MediaFormat.KEY_MIME
                ) ?: return false

            val sourceSampleRate =
                if (
                    inputFormat.containsKey(
                        MediaFormat.KEY_SAMPLE_RATE
                    )
                ) {
                    inputFormat.getInteger(
                        MediaFormat.KEY_SAMPLE_RATE
                    )
                } else {
                    TARGET_SAMPLE_RATE
                }

            val sourceChannels =
                if (
                    inputFormat.containsKey(
                        MediaFormat.KEY_CHANNEL_COUNT
                    )
                ) {
                    inputFormat.getInteger(
                        MediaFormat.KEY_CHANNEL_COUNT
                    )
                } else {
                    1
                }

            codec =
                MediaCodec.createDecoderByType(mime)

            codec.configure(
                inputFormat,
                null,
                null,
                0
            )

            codec.start()

            val decodedPcm =
                ByteArrayOutputStream()

            val bufferInfo =
                MediaCodec.BufferInfo()

            var inputFinished = false
            var outputFinished = false

            // ==========================================
            // DECODE M4A → PCM
            // ==========================================

            while (!outputFinished) {

                if (!inputFinished) {

                    val inputIndex =
                        codec.dequeueInputBuffer(10000)

                    if (inputIndex >= 0) {

                        val inputBuffer =
                            codec.getInputBuffer(
                                inputIndex
                            )

                        if (inputBuffer != null) {

                            val sampleSize =
                                extractor.readSampleData(
                                    inputBuffer,
                                    0
                                )

                            if (sampleSize < 0) {

                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )

                                inputFinished = true

                            } else {

                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime,
                                    0
                                )

                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex =
                    codec.dequeueOutputBuffer(
                        bufferInfo,
                        10000
                    )

                if (outputIndex >= 0) {

                    val outputBuffer =
                        codec.getOutputBuffer(
                            outputIndex
                        )

                    if (
                        outputBuffer != null &&
                        bufferInfo.size > 0
                    ) {

                        outputBuffer.position(
                            bufferInfo.offset
                        )

                        outputBuffer.limit(
                            bufferInfo.offset +
                                    bufferInfo.size
                        )

                        val pcmData =
                            ByteArray(bufferInfo.size)

                        outputBuffer.get(pcmData)

                        decodedPcm.write(
                            pcmData
                        )
                    }

                    codec.releaseOutputBuffer(
                        outputIndex,
                        false
                    )

                    if (
                        bufferInfo.flags and
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        != 0
                    ) {

                        outputFinished = true
                    }
                }
            }

            val sourcePcm =
                decodedPcm.toByteArray()

            if (sourcePcm.isEmpty()) {
                return false
            }

            // ==========================================
            // STEREO → MONO
            // ==========================================

            val monoSamples =
                convertToMono(
                    sourcePcm,
                    sourceChannels
                )

            // ==========================================
            // RESAMPLE → 16 kHz
            // ==========================================

            val resampledSamples =
                resample(
                    monoSamples,
                    sourceSampleRate,
                    TARGET_SAMPLE_RATE
                )

            // ==========================================
            // NOISE REDUCTION
            // ==========================================

            val noiseReducedSamples =
                reduceNoise(
                    resampledSamples
                )

            // ==========================================
            // FILTERING
            // ==========================================

            val filteredSamples =
                applyBandPassFilter(
                    noiseReducedSamples,
                    TARGET_SAMPLE_RATE
                )

            // ==========================================
            // NORMALIZATION
            // ==========================================

            val normalizedSamples =
                normalizeAudio(
                    filteredSamples
                )

            // ==========================================
            // SHORT → BYTE
            // ==========================================

            val outputPcm =
                shortArrayToByteArray(
                    normalizedSamples
                )

            // ==========================================
            // WRITE WAV
            // ==========================================

            writeWavFile(
                outputFile,
                outputPcm,
                TARGET_SAMPLE_RATE,
                TARGET_CHANNELS,
                BITS_PER_SAMPLE
            )

            return true

        } catch (e: Exception) {

            e.printStackTrace()

            return false

        } finally {

            try {
                codec?.stop()
            } catch (_: Exception) {
            }

            try {
                codec?.release()
            } catch (_: Exception) {
            }

            extractor?.release()
        }
    }

    // ==========================================
    // NOISE REDUCTION
    // ==========================================

    private fun reduceNoise(
        samples: ShortArray
    ): ShortArray {

        val cleaned =
            ShortArray(samples.size)

        for (i in samples.indices) {

            val sample =
                samples[i].toInt()

            val amplitude =
                abs(sample)

            cleaned[i] =
                if (amplitude < NOISE_THRESHOLD) {
                    0
                } else {
                    samples[i]
                }
        }

        return cleaned
    }

    // ==========================================
    // BAND-PASS FILTER
    // ==========================================

    private fun applyBandPassFilter(
        samples: ShortArray,
        sampleRate: Int
    ): ShortArray {

        if (samples.isEmpty()) {
            return ShortArray(0)
        }

        val output =
            DoubleArray(samples.size)

        // High-pass
        val highPassAlpha =
            calculateHighPassAlpha(
                LOW_CUTOFF_HZ,
                sampleRate
            )

        var previousInput = 0.0
        var previousOutput = 0.0

        for (i in samples.indices) {

            val input =
                samples[i].toDouble()

            val filtered =
                highPassAlpha *
                        (
                                previousOutput +
                                        input -
                                        previousInput
                                )

            output[i] =
                filtered

            previousInput =
                input

            previousOutput =
                filtered
        }

        // Low-pass
        val lowPassAlpha =
            calculateLowPassAlpha(
                HIGH_CUTOFF_HZ,
                sampleRate
            )

        var lowPassOutput =
            output[0]

        for (i in output.indices) {

            lowPassOutput +=
                lowPassAlpha *
                        (
                                output[i] -
                                        lowPassOutput
                                )

            output[i] =
                lowPassOutput
        }

        val result =
            ShortArray(output.size)

        for (i in output.indices) {

            result[i] =
                output[i]
                    .toInt()
                    .coerceIn(
                        Short.MIN_VALUE.toInt(),
                        Short.MAX_VALUE.toInt()
                    )
                    .toShort()
        }

        return result
    }

    private fun calculateHighPassAlpha(
        cutoff: Double,
        sampleRate: Int
    ): Double {

        val rc =
            1.0 /
                    (2.0 * Math.PI * cutoff)

        val dt =
            1.0 / sampleRate

        return rc / (rc + dt)
    }

    private fun calculateLowPassAlpha(
        cutoff: Double,
        sampleRate: Int
    ): Double {

        val rc =
            1.0 /
                    (2.0 * Math.PI * cutoff)

        val dt =
            1.0 / sampleRate

        return dt / (rc + dt)
    }

    // ==========================================
    // NORMALIZATION
    // ==========================================

    private fun normalizeAudio(
        samples: ShortArray
    ): ShortArray {

        if (samples.isEmpty()) {
            return ShortArray(0)
        }

        var peak =
            0

        // Find maximum amplitude
        for (sample in samples) {

            val amplitude =
                abs(sample.toInt())

            if (amplitude > peak) {
                peak = amplitude
            }
        }

        // Avoid division by zero
        if (peak == 0) {
            return samples
        }

        val targetPeak =
            Short.MAX_VALUE *
                    NORMALIZATION_TARGET

        val gain =
            targetPeak / peak.toDouble()

        val normalized =
            ShortArray(samples.size)

        for (i in samples.indices) {

            val value =
                samples[i].toDouble() *
                        gain

            normalized[i] =
                value
                    .toInt()
                    .coerceIn(
                        Short.MIN_VALUE.toInt(),
                        Short.MAX_VALUE.toInt()
                    )
                    .toShort()
        }

        return normalized
    }

    // ==========================================
    // STEREO → MONO
    // ==========================================

    private fun convertToMono(
        pcmBytes: ByteArray,
        channels: Int
    ): ShortArray {

        val buffer =
            ByteBuffer
                .wrap(pcmBytes)
                .order(
                    ByteOrder.LITTLE_ENDIAN
                )

        val totalSamples =
            pcmBytes.size / 2

        if (channels <= 1) {

            val mono =
                ShortArray(totalSamples)

            for (i in mono.indices) {

                mono[i] =
                    buffer.short
            }

            return mono
        }

        val frameCount =
            totalSamples / channels

        val mono =
            ShortArray(frameCount)

        for (frame in 0 until frameCount) {

            var sum = 0

            for (channel in 0 until channels) {

                sum +=
                    buffer.short.toInt()
            }

            mono[frame] =
                (
                        sum / channels
                        ).coerceIn(
                        Short.MIN_VALUE.toInt(),
                        Short.MAX_VALUE.toInt()
                    ).toShort()
        }

        return mono
    }

    // ==========================================
    // RESAMPLING
    // ==========================================

    private fun resample(
        input: ShortArray,
        sourceRate: Int,
        targetRate: Int
    ): ShortArray {

        if (sourceRate == targetRate) {
            return input
        }

        if (input.isEmpty()) {
            return ShortArray(0)
        }

        val outputLength =
            (
                    input.size.toLong() *
                            targetRate /
                            sourceRate
                    ).toInt()

        val output =
            ShortArray(outputLength)

        val ratio =
            sourceRate.toDouble() /
                    targetRate.toDouble()

        for (i in output.indices) {

            val sourcePosition =
                i * ratio

            val leftIndex =
                sourcePosition.toInt()

            val rightIndex =
                (leftIndex + 1)
                    .coerceAtMost(
                        input.lastIndex
                    )

            val fraction =
                sourcePosition -
                        leftIndex

            val sample =
                input[leftIndex] *
                        (1.0 - fraction) +
                        input[rightIndex] *
                        fraction

            output[i] =
                sample
                    .toInt()
                    .coerceIn(
                        Short.MIN_VALUE.toInt(),
                        Short.MAX_VALUE.toInt()
                    )
                    .toShort()
        }

        return output
    }

    // ==========================================
    // SHORT → BYTE
    // ==========================================

    private fun shortArrayToByteArray(
        samples: ShortArray
    ): ByteArray {

        val buffer =
            ByteBuffer
                .allocate(
                    samples.size * 2
                )
                .order(
                    ByteOrder.LITTLE_ENDIAN
                )

        for (sample in samples) {

            buffer.putShort(sample)
        }

        return buffer.array()
    }

    // ==========================================
    // WRITE WAV
    // ==========================================

    private fun writeWavFile(
        file: File,
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {

        FileOutputStream(file).use {

            val dataSize =
                pcmData.size.toLong()

            val byteRate =
                sampleRate *
                        channels *
                        bitsPerSample / 8

            val blockAlign =
                channels *
                        bitsPerSample / 8

            val header =
                ByteBuffer
                    .allocate(44)
                    .order(
                        ByteOrder.LITTLE_ENDIAN
                    )

            header.put(
                "RIFF".toByteArray()
            )

            header.putInt(
                (36 + dataSize).toInt()
            )

            header.put(
                "WAVE".toByteArray()
            )

            header.put(
                "fmt ".toByteArray()
            )

            header.putInt(16)

            header.putShort(1)

            header.putShort(
                channels.toShort()
            )

            header.putInt(sampleRate)

            header.putInt(byteRate)

            header.putShort(
                blockAlign.toShort()
            )

            header.putShort(
                bitsPerSample.toShort()
            )

            header.put(
                "data".toByteArray()
            )

            header.putInt(
                dataSize.toInt()
            )

            it.write(
                header.array()
            )

            it.write(
                pcmData
            )
        }
    }
}