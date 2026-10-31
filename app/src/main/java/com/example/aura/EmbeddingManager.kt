package com.example.aura

import android.content.Context
import android.util.Log
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import java.io.File

/**
 * Local semantic embedding engine for Aura memory.
 *
 * Uses:
 * BGE-small-en-v1.5 GGUF
 *      ↓
 * llama.cpp Android
 *      ↓
 * 384-dimensional embedding vector
 *
 * No cloud/API required.
 */
class EmbeddingManager(private val context: Context) {

    companion object {
        private const val TAG = "EmbeddingManager"

        private const val MODEL_NAME =
            "bge-small-en-v1.5-q4_k_m.gguf"
    }

    private var model: LlamaModel? = null

    /**
     * Copies the embedding model from assets into the app's
     * private storage and loads it with llama.cpp.
     */
    suspend fun load(): Boolean {
        if (model != null) {
            Log.d(TAG, "Embedding model already loaded")
            return true
        }

        return try {
            val modelDir = File(context.filesDir, "models")

            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            val modelFile = File(modelDir, MODEL_NAME)

            // Copy model from assets only once.
            if (!modelFile.exists()) {
                Log.d(TAG, "Copying embedding model from assets...")

                context.assets
                    .open("models/$MODEL_NAME")
                    .use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                Log.d(
                    TAG,
                    "Embedding model copied: ${modelFile.length()} bytes"
                )
            }

            Log.d(TAG, "Loading embedding model...")

            model = Llama.loadModel(
                modelPath = modelFile.absolutePath,
                config = LlamaConfig(
                    contextSize = 512,
                    threads = 4
                )
            )

            val loaded = model != null

            Log.d(TAG, "Embedding model loaded: $loaded")

            loaded

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to load embedding model",
                e
            )

            model = null
            false
        }
    }

    /**
     * Converts text into a semantic embedding vector.
     */
    suspend fun embed(text: String): FloatArray? {

        val loadedModel = model
            ?: run {
                Log.e(TAG, "Embedding model is not loaded")
                return null
            }

        if (text.isBlank()) {
            Log.w(TAG, "Cannot embed empty text")
            return null
        }

        return try {

            val vector = Llama.embed(
                loadedModel,
                text.trim()
            )

            Log.d(
                TAG,
                "Embedding generated. Dimensions: ${vector.size}"
            )

            vector

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Embedding generation failed",
                e
            )

            null
        }
    }

    /**
     * Releases the native embedding model.
     */
    fun release() {

        model?.let {
            try {
                Llama.releaseModel(it)
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Error releasing embedding model",
                    e
                )
            }
        }

        model = null

        Log.d(TAG, "Embedding model released")
    }

    fun isLoaded(): Boolean {
        return model != null
    }
}