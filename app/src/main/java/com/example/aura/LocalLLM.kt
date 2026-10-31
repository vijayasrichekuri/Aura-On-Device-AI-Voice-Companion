package com.example.aura

import android.content.Context
import android.util.Log
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class LocalLLM(private val context: Context) {

    private var model: LlamaModel? = null
    private val loadMutex = Mutex()

    private val modelName = "gemma-3-1b-it-Q4_K_M.gguf"

    suspend fun load(): Boolean = loadMutex.withLock {
        if (model != null) return@withLock true

        try {
            val modelDir = File(context.filesDir, "models")

            if (!modelDir.exists() && !modelDir.mkdirs()) {
                throw IOException("Could not create model directory")
            }

            val modelFile = File(modelDir, modelName)

            // Reuse an existing complete model.
            // Download only if it is missing or clearly incomplete.
            if (!modelFile.isFile ||
                modelFile.length() < MIN_MODEL_BYTES
            ) {
                downloadModel(modelFile)
            }

            model = Llama.loadModel(
                modelPath = modelFile.absolutePath,
                config = LlamaConfig(
                    contextSize = 2048,
                    threads = 4
                )
            )

            model != null

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemma failed to load", e)
            model = null
            false
        }
    }

    private suspend fun downloadModel(modelFile: File) {
        withContext(Dispatchers.IO) {
            val tempFile = File(
                modelFile.parentFile,
                "$modelName.part"
            )

            tempFile.delete()

            val connection =
                (URL(MODEL_URL).openConnection() as HttpURLConnection)

            try {
                connection.connectTimeout = 30_000
                connection.readTimeout = 120_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty(
                    "User-Agent",
                    "Aura-Android"
                )

                val responseCode = connection.responseCode

                if (responseCode !in 200..299) {
                    throw IOException(
                        "Model download failed: HTTP $responseCode"
                    )
                }

                val expectedLength = connection.contentLengthLong
                var totalBytes = 0L
                var lastLoggedBytes = 0L

                val digest = MessageDigest.getInstance("SHA-256")

                connection.inputStream.buffered().use { input ->
                    FileOutputStream(tempFile).buffered().use { output ->
                        val buffer = ByteArray(1024 * 1024)

                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break

                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            totalBytes += count

                            if (totalBytes - lastLoggedBytes >=
                                50_000_000L
                            ) {
                                Log.i(
                                    TAG,
                                    "Downloaded ${totalBytes / 1_000_000} MB"
                                )
                                lastLoggedBytes = totalBytes
                            }
                        }
                    }
                }

                if (expectedLength > 0 &&
                    totalBytes != expectedLength
                ) {
                    throw IOException("Incomplete model download")
                }

                if (totalBytes < MIN_MODEL_BYTES) {
                    throw IOException("Downloaded model is too small")
                }

                val actualHash = digest.digest().joinToString("") {
                    "%02x".format(it.toInt() and 0xff)
                }

                if (!actualHash.equals(EXPECTED_SHA256, ignoreCase = true)) {
                    throw IOException("Model checksum verification failed")
                }

                // Replace the previous file only after download validation.
                if (modelFile.exists() && !modelFile.delete()) {
                    throw IOException("Could not replace old model file")
                }

                if (!tempFile.renameTo(modelFile)) {
                    throw IOException("Could not save downloaded model")
                }

                Log.i(TAG, "Gemma downloaded and verified successfully")

            } catch (e: Exception) {
                tempFile.delete()
                throw e
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun generate(
        prompt: String,
        systemPrompt: String =
            "You are Aura, a private on-device AI assistant.",
        maxTokens: Int = 256
    ): String {
        val loadedModel = model
            ?: return "Model is not loaded."

        return try {
            val result = Llama.complete(
                loadedModel,
                prompt = prompt,
                systemPrompt = systemPrompt,
                maxTokens = maxTokens
            )

            result.text

        } catch (e: Exception) {
            Log.e(TAG, "Gemma generation failed", e)
            "Unable to generate response."
        }
    }

    fun release() {
        model?.let {
            Llama.releaseModel(it)
        }
        model = null
    }

    companion object {
        private const val TAG = "LocalLLM"

        private const val MIN_MODEL_BYTES = 700_000_000L

        private const val MODEL_URL =
            "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/fcabf35e5e079ecc0c182a8ff08bbe60a56c3594/gemma-3-1b-it-Q4_K_M.gguf"

        private const val EXPECTED_SHA256 =
            "e73922c3d7837fd384e77f7c4a173c0ae45d924988fbb22003dbb029f4884dbe"
    }
}