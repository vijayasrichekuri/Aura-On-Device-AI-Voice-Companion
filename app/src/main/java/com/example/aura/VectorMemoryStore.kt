package com.example.aura

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Local vector store for Aura conversation chunks.
 * Embeddings are stored as FloatArray BLOBs and searched with cosine similarity.
 */
class VectorMemoryStore(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    data class ChunkToStore(
        val sessionId: String,
        val chunkIndex: Int,
        val text: String,
        val embedding: FloatArray,
        val speaker: String = "",
        val startMs: Long = 0L,
        val endMs: Long = 0L,
        val createdAt: Long = System.currentTimeMillis()
    )

    data class SearchResult(
        val id: Long,
        val sessionId: String,
        val chunkIndex: Int,
        val text: String,
        val speaker: String,
        val startMs: Long,
        val endMs: Long,
        val createdAt: Long,
        val similarity: Float
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE memory_chunks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                chunk_index INTEGER NOT NULL,
                text TEXT NOT NULL,
                speaker TEXT DEFAULT '',
                start_ms INTEGER DEFAULT 0,
                end_ms INTEGER DEFAULT 0,
                created_at INTEGER NOT NULL,
                embedding BLOB NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_memory_chunks_session ON memory_chunks(session_id)")
        db.execSQL("CREATE INDEX idx_memory_chunks_created ON memory_chunks(created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS memory_chunks")
        onCreate(db)
    }

    fun insertChunk(chunk: ChunkToStore): Long {
        return writableDatabase.insertOrThrow(
            "memory_chunks",
            null,
            android.content.ContentValues().apply {
                put("session_id", chunk.sessionId)
                put("chunk_index", chunk.chunkIndex)
                put("text", chunk.text)
                put("speaker", chunk.speaker)
                put("start_ms", chunk.startMs)
                put("end_ms", chunk.endMs)
                put("created_at", chunk.createdAt)
                put("embedding", floatArrayToBytes(chunk.embedding))
            }
        )
    }

    fun insertChunks(chunks: List<ChunkToStore>) {
        if (chunks.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            chunks.forEach { insertChunk(it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteSession(sessionId: String) {
        writableDatabase.delete(
            "memory_chunks",
            "session_id = ?",
            arrayOf(sessionId)
        )
    }

    fun isSessionIndexed(sessionId: String): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM memory_chunks WHERE session_id = ? LIMIT 1",
            arrayOf(sessionId)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM memory_chunks", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /**
     * Returns the top-K chunks by cosine similarity to the supplied query vector.
     */
    fun search(queryEmbedding: FloatArray, topK: Int = 5): List<SearchResult> {
        if (queryEmbedding.isEmpty() || topK <= 0) return emptyList()

        val results = mutableListOf<SearchResult>()
        readableDatabase.rawQuery(
            """
            SELECT id, session_id, chunk_index, text, speaker,
                   start_ms, end_ms, created_at, embedding
            FROM memory_chunks
            """.trimIndent(),
            null
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val sessionIndex = cursor.getColumnIndexOrThrow("session_id")
            val chunkIndex = cursor.getColumnIndexOrThrow("chunk_index")
            val textIndex = cursor.getColumnIndexOrThrow("text")
            val speakerIndex = cursor.getColumnIndexOrThrow("speaker")
            val startIndex = cursor.getColumnIndexOrThrow("start_ms")
            val endIndex = cursor.getColumnIndexOrThrow("end_ms")
            val createdIndex = cursor.getColumnIndexOrThrow("created_at")
            val embeddingIndex = cursor.getColumnIndexOrThrow("embedding")

            while (cursor.moveToNext()) {
                val storedEmbedding = bytesToFloatArray(cursor.getBlob(embeddingIndex))
                if (storedEmbedding.isEmpty()) continue

                val similarity = cosineSimilarity(queryEmbedding, storedEmbedding)

                results += SearchResult(
                    id = cursor.getLong(idIndex),
                    sessionId = cursor.getString(sessionIndex),
                    chunkIndex = cursor.getInt(chunkIndex),
                    text = cursor.getString(textIndex),
                    speaker = cursor.getString(speakerIndex) ?: "",
                    startMs = cursor.getLong(startIndex),
                    endMs = cursor.getLong(endIndex),
                    createdAt = cursor.getLong(createdIndex),
                    similarity = similarity
                )
            }
        }

        return results
            .sortedByDescending { it.similarity }
            .take(topK)
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            val av = a[i].toDouble()
            val bv = b[i].toDouble()
            dot += av * bv
            normA += av * av
            normB += bv * bv
        }

        val denominator = sqrt(normA) * sqrt(normB)
        if (denominator == 0.0) return 0f
        return (dot / denominator).toFloat()
    }

    private fun floatArrayToBytes(values: FloatArray): ByteArray {
        val buffer = ByteBuffer
            .allocate(values.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(buffer::putFloat)
        return buffer.array()
    }

    private fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        if (bytes.isEmpty() || bytes.size % 4 != 0) return FloatArray(0)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val result = FloatArray(bytes.size / 4)
        for (i in result.indices) {
            result[i] = buffer.float
        }
        return result
    }

    companion object {
        private const val DATABASE_NAME = "aura_vector_memory.db"
        private const val DATABASE_VERSION = 1
    }
}
