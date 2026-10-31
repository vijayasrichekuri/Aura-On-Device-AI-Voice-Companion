package com.example.aura

import android.annotation.SuppressLint
import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.content.pm.PackageManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider

import androidx.lifecycle.lifecycleScope

import com.example.aura.ui.theme.AuraTheme
import com.whispercpp.whisper.WhisperSegment

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin


// ==========================================================
// AURA DESIGN SYSTEM
// ==========================================================

private object AuraColors {
    val backgroundTop = Color(0xFF07111F)
    val backgroundMid = Color(0xFF0B1730)
    val backgroundGlow = Color(0xFF10162D)
    val backgroundBottom = Color(0xFF0A1020)

    val primary = Color(0xFF72E6FF)
    val lavender = Color(0xFFA98BFF)
    val textPrimary = Color(0xFFF3F7FF)
    val textSecondary = Color(0xFF9AAAC7)
    val textMuted = Color(0xFF8292B2)
    val transcriptText = Color(0xFFE5ECF8)

    val card = Color(0xFF101827)
    val cardElevatedTop = Color(0xE6152943)
    val cardElevatedBottom = Color(0xE6101B30)
    val cardBorder = Color(0x554B6D9C)
    val softBorder = Color(0x554F6B99)

    val primaryButton = Color(0xFF315B8C)
    val primaryButtonPressed = Color(0xFF284A70)
    val secondaryButton = Color(0xAA111E33)
    val secondaryButtonText = Color(0xFFB8C7DF)
    val moduleBackground = Color(0x99121F35)
    val moduleBorder = Color(0x443B557D)
}

// ==========================================================
// MANROPE FONT
// ==========================================================

private val AuraFont = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold)
)


// ==========================================================
// AURA MAIN ACTIVITY
// ==========================================================

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {

    // ======================================================
    // RECORDING VARIABLES
    // ======================================================

    private var recorder: MediaRecorder? = null

    private var outputFile: File? = null

    private var sessionId: String = ""

    private var startTime: Long = 0L

    private var isRecording by mutableStateOf(false)
    private var isPaused by mutableStateOf(false)

    private var statusMessage by mutableStateOf(
        "Ready to record"
    )

    // ======================================================
    // LIVE PERFORMANCE TIMERS
    // ======================================================

    private var recordingElapsedMs by mutableStateOf(0L)
    private var processingStage by mutableStateOf("")
    private var processingElapsedMs by mutableStateOf(0L)
    private var processingStageDurations by mutableStateOf<Map<String, Long>>(emptyMap())

    private var recordingTimerJob: Job? = null
    private var processingTimerJob: Job? = null
    private var processingStageStartedAt: Long = 0L
    private var recordingDurationMs: Long = 0L
    private var recordingActiveStartedAt: Long = 0L
    private var accumulatedRecordingMs: Long = 0L

    // Heavy local-AI processing is serialized, but every session is queued.
    private val processingMutex = Mutex()
    private val queuedSessions = ConcurrentHashMap.newKeySet<String>()

    // ======================================================
    // WHISPER TRANSCRIPT
    // ======================================================

    private var transcriptText by mutableStateOf(
        ""
    )

    private var fullTranscriptText by mutableStateOf(
        ""
    )

    // ======================================================
    // LOCAL AI SUMMARY
    // ======================================================

    private var summaryText by mutableStateOf(
        ""
    )

    // ======================================================
    // LOCAL AI INSIGHTS
    // ======================================================

    private var insightText by mutableStateOf(
        ""
    )

    private var actionItemsText by mutableStateOf("")
    private var eventsText by mutableStateOf("")

    // ======================================================
    // FIRST-LAUNCH WELCOME
    // ======================================================

    private var showWelcome by mutableStateOf(false)

    private var showProfileSetup by mutableStateOf(false)

    private var showHardwareCheck by mutableStateOf(false)

    private var showNotificationsSetup by mutableStateOf(false)

    private var profileName by mutableStateOf("")

    // ======================================================
    // AURA MEMORY + PLANNER
    // ======================================================

    private val auraStore by lazy { AuraStore(this) }

    private var showAuraHub by mutableStateOf(false)

    private var savedActions by mutableStateOf<List<AuraActionItem>>(emptyList())

    private var savedEvents by mutableStateOf<List<AuraEventItem>>(emptyList())

    private var savedMemories by mutableStateOf<List<AuraMemoryItem>>(emptyList())
    private var savedImportantDates by mutableStateOf<List<AuraImportantDate>>(emptyList())

    // Saved-conversation analysis status
    private var memoryAnalysisSession by mutableStateOf<String?>(null)
    private var memoryAnalysisAction by mutableStateOf("")

    // ======================================================
    // SEMANTIC MEMORY / RAG
    // ======================================================

    private var ragQuestion by mutableStateOf("")
    private var ragAnswer by mutableStateOf("")
    private var ragSources by mutableStateOf("")
    private var ragLoading by mutableStateOf(false)

    // ======================================================
    // VAD
    // ======================================================

    private var vadStatus by mutableStateOf(
        "VAD not started"
    )

    private val voiceActivityDetector =
        VoiceActivityDetector()

    // ======================================================
    // AUDIO PREPROCESSOR
    // ======================================================

    private val audioPreprocessor =
        AudioPreprocessor()

    // ======================================================
    // WHISPER ASR
    // ======================================================

    private lateinit var whisperASR: WhisperASR
    private val speakerDiarization by lazy { SpeakerDiarization(this) }

    // ======================================================
    // LOCAL LLM
    // ======================================================

    private lateinit var localLLM: LocalLLM

    // ======================================================
    // LOCAL EMBEDDING MODEL
    // ======================================================

    private lateinit var embeddingManager: EmbeddingManager
    private lateinit var vectorMemoryStore: VectorMemoryStore

    // ======================================================
    // REAL LOCAL VECTOR MEMORY INDEXING
    // ======================================================

    private suspend fun indexConversationInVectorMemory(session: String, transcript: String) {
        if (session.isBlank() || transcript.isBlank()) return

        startProcessingStage("Memory embeddings")
        try {
            val loaded = embeddingManager.load()
            if (!loaded) throw IllegalStateException("BGE embedding model failed to load")

            val chunks = createMemoryChunks(transcript)
            if (chunks.isEmpty()) throw IllegalStateException("No usable transcript chunks found")

            vectorMemoryStore.deleteSession(session)
            val storedChunks = mutableListOf<VectorMemoryStore.ChunkToStore>()

            chunks.forEachIndexed { index, chunkText ->
                val embedding = embeddingManager.embed(chunkText)
                if (embedding != null) {
                    storedChunks += VectorMemoryStore.ChunkToStore(
                        sessionId = session,
                        chunkIndex = index,
                        text = chunkText,
                        embedding = embedding
                    )
                } else {
                    Log.e("VectorMemory", "Failed to generate embedding for chunk $index")
                }
            }

            if (storedChunks.isNotEmpty()) {
                vectorMemoryStore.insertChunks(storedChunks)
            }
            finishProcessingStage("Memory embeddings")
            Log.d("VectorMemory", "Indexed $session: ${storedChunks.size} chunks")
        } catch (e: Exception) {
            failProcessingStage("Memory embeddings")
            Log.e("VectorMemory", "Indexing failed for $session", e)
        }
    }

    private fun createMemoryChunks(text: String): List<String> {

        val normalized = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()

        if (normalized.isBlank()) {
            return emptyList()
        }

        val paragraphs = normalized
            .split(Regex("\\n\\s*\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val sourceParts = if (paragraphs.isNotEmpty()) {
            paragraphs
        } else {
            listOf(normalized)
        }

        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        val maxCharacters = 700

        fun flush() {
            val value = current.toString().trim()
            if (value.isNotBlank()) {
                chunks += value
            }
            current.clear()
        }

        sourceParts.forEach { part ->

            if (part.length <= maxCharacters) {

                if (current.isNotEmpty() &&
                    current.length + 2 + part.length > maxCharacters
                ) {
                    flush()
                }

                if (current.isNotEmpty()) {
                    current.append("\n\n")
                }

                current.append(part)

            } else {

                if (current.isNotEmpty()) {
                    flush()
                }

                val sentences = part
                    .split(Regex("(?<=[.!?])\\s+"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                sentences.forEach { sentence ->

                    if (sentence.length > maxCharacters) {

                        var position = 0
                        while (position < sentence.length) {
                            val end =
                                (position + maxCharacters).coerceAtMost(sentence.length)

                            chunks += sentence.substring(position, end).trim()
                            position = end
                        }

                    } else if (current.isNotEmpty() &&
                        current.length + 1 + sentence.length > maxCharacters
                    ) {
                        flush()
                        current.append(sentence)
                    } else {

                        if (current.isNotEmpty()) {
                            current.append(" ")
                        }

                        current.append(sentence)
                    }
                }
            }
        }

        flush()

        return chunks.filter { it.length >= 10 }
    }

    // ======================================================
    // LOCAL LLM TEST
    // ======================================================

    private fun testLocalLLM() {

        statusMessage =
            "Loading Aura AI model..."

        lifecycleScope.launch(Dispatchers.Default) {

            try {

                Log.d(
                    "LocalLLM",
                    "Starting Gemma model test..."
                )

                val loaded =
                    localLLM.load()

                if (!loaded) {

                    runOnUiThread {

                        statusMessage =
                            "Local AI model failed to load"

                        Toast.makeText(
                            this@MainActivity,
                            "Gemma failed to load",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    return@launch
                }

                Log.d(
                    "LocalLLM",
                    "Gemma model loaded successfully"
                )

                runOnUiThread {
                    statusMessage =
                        "Gemma loaded. Running test..."
                }

                val response =
                    localLLM.generate(
                        prompt =
                        "Say hello to Aura in one short sentence.",
                        maxTokens = 40
                    )

                Log.d(
                    "LocalLLM",
                    "Gemma response: $response"
                )

                runOnUiThread {

                    statusMessage =
                        "Local AI test successful"

                    Toast.makeText(
                        this@MainActivity,
                        response,
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {

                Log.e(
                    "LocalLLM",
                    "Local AI test failed",
                    e
                )

                runOnUiThread {

                    statusMessage =
                        "Local AI test failed"

                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }


    // ======================================================
    // SAFE LONG-TRANSCRIPT HELPERS
    // ======================================================

    private fun splitTextForLLM(text: String, maxChars: Int = 4200): List<String> {
        val normalized = text.replace("\r\n", "\n").trim()
        if (normalized.length <= maxChars) return listOf(normalized)

        val parts = mutableListOf<String>()
        var position = 0
        while (position < normalized.length) {
            val end = (position + maxChars).coerceAtMost(normalized.length)
            var cut = end
            if (end < normalized.length) {
                val newline = normalized.lastIndexOf('\n', end)
                val sentence = normalized.lastIndexOf('.', end)
                val candidate = maxOf(newline, sentence)
                if (candidate > position + maxChars / 2) cut = candidate + 1
            }
            parts += normalized.substring(position, cut).trim()
            position = cut
        }
        return parts.filter { it.isNotBlank() }
    }

    private suspend fun generateChunkedText(
        transcript: String,
        instruction: String,
        maxTokensPerChunk: Int = 180,
        finalMaxTokens: Int = 256
    ): String {
        val chunks = splitTextForLLM(transcript)
        val partials = chunks.mapIndexed { index, chunk ->
            localLLM.generate(
                prompt = """
                    $instruction

                    This is transcript part ${index + 1} of ${chunks.size}.
                    Use ONLY information in this part. Do not invent facts.

                    Transcript part:
                    $chunk
                """.trimIndent(),
                maxTokens = maxTokensPerChunk
            ).trim()
        }.filter { it.isNotBlank() }

        if (partials.isEmpty()) return "No useful information extracted."
        if (partials.size == 1) return partials.first()

        val combined = partials.joinToString("\n\n--- PARTIAL RESULT ---\n\n")
            .take(7000)

        return localLLM.generate(
            prompt = """
                Combine the partial results below into one concise final answer.
                Remove repetition. Keep only information supported by the partial results.
                Do not invent anything.

                Partial results:
                $combined
            """.trimIndent(),
            maxTokens = finalMaxTokens
        ).trim()
    }

    // ======================================================
    // GENERATE LOCAL AI SUMMARY
    // ======================================================

    private fun generateSummary() {
        val transcript = fullTranscriptText.ifBlank { transcriptText }
        if (transcript.isBlank()) {
            Toast.makeText(this, "Please transcribe a recording first.", Toast.LENGTH_SHORT).show()
            return
        }

        statusMessage = "Generating local AI summary..."
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                if (!localLLM.load()) throw IllegalStateException("Gemma failed to load")
                val response = generateChunkedText(
                    transcript = transcript,
                    instruction = """
                        Summarize this conversation clearly and briefly.
                        Focus on the main topic, important points, decisions or conclusions,
                        and action items if explicitly mentioned.
                    """.trimIndent(),
                    maxTokensPerChunk = 180,
                    finalMaxTokens = 256
                )
                saveSessionCheckpoint(
                    session = sessionId,
                    transcript = transcript,
                    speakerTranscript = transcriptText,
                    summary = response,
                    audioPath = outputFile?.absolutePath ?: ""
                )
                runOnUiThread {
                    summaryText = response
                    statusMessage = "Local AI summary generated"
                }
            } catch (e: Exception) {
                Log.e("LocalLLM", "Summary generation failed", e)
                runOnUiThread {
                    statusMessage = "Summary generation failed"
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ======================================================
    // GENERATE LOCAL AI INSIGHTS
    // ======================================================

    private fun generateInsights() {
        val transcript = fullTranscriptText.ifBlank { transcriptText }
        if (transcript.isBlank()) {
            Toast.makeText(this, "Please transcribe a recording first.", Toast.LENGTH_SHORT).show()
            return
        }

        statusMessage = "Generating local AI insights..."
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                if (!localLLM.load()) throw IllegalStateException("Gemma failed to load")
                val response = generateChunkedText(
                    transcript = transcript,
                    instruction = """
                        Analyze this conversation and extract useful insights.
                        Use these sections: Key Topics, Decisions, Action Items,
                        Dates & Deadlines, Important Notes.
                        If a section has no information, say None mentioned.
                    """.trimIndent(),
                    maxTokensPerChunk = 220,
                    finalMaxTokens = 320
                )
                saveSessionCheckpoint(
                    session = sessionId,
                    transcript = transcript,
                    speakerTranscript = transcriptText,
                    summary = summaryText,
                    insights = response,
                    audioPath = outputFile?.absolutePath ?: ""
                )
                runOnUiThread {
                    insightText = response
                    statusMessage = "Local AI insights generated"
                }
            } catch (e: Exception) {
                Log.e("LocalLLM", "Insights generation failed", e)
                runOnUiThread {
                    statusMessage = "Insights generation failed"
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ======================================================
    // EXTRACT ACTION ITEMS + EVENTS
    // ======================================================

    private fun generateActionItemsAndEvents() {
        val transcript = fullTranscriptText.ifBlank { transcriptText }
        if (transcript.isBlank()) {
            Toast.makeText(this, "Please transcribe a recording first.", Toast.LENGTH_SHORT).show()
            return
        }

        statusMessage = "Extracting action items and events..."
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                if (!localLLM.load()) throw IllegalStateException("Gemma failed to load")
                val today = LocalDate.now()
                val chunks = splitTextForLLM(transcript)
                val allActions = mutableListOf<AuraActionItem>()
                val allEvents = mutableListOf<AuraEventItem>()

                chunks.forEachIndexed { chunkIndex, chunk ->
                    val prompt = """
                        You are Aura's strict information extractor.
                        Today is $today.
                        Extract EVERY explicitly stated action item and scheduled event from this transcript part.
                        Never guess, infer, or invent information. Preserve spoken dates, names and times.
                        Return ONLY valid JSON with exactly this structure:
                        {
                          "action_items": [
                            {"title":"exact task", "person":"name or", "due":"exact spoken due wording or", "due_date":"YYYY-MM-DD or"}
                          ],
                          "events": [
                            {"title":"event name", "date":"exact spoken date wording or", "date_iso":"YYYY-MM-DD or", "time":"exact spoken time or", "important":true}
                          ]
                        }
                        Rules:
                        - If a person/date/time is not explicitly stated, use an empty string.
                        - Calculate date_iso for clear relative dates using today.
                        - If there are no items, return empty arrays.

                        Transcript part ${chunkIndex + 1} of ${chunks.size}:
                        $chunk
                    """.trimIndent()

                    val response = localLLM.generate(prompt, maxTokens = 360)
                    val jsonText = response.substringAfter("{", "").substringBeforeLast("}", "")
                        .let { if (it.isNotBlank()) "{$it}" else "" }
                    if (jsonText.isBlank()) return@forEachIndexed

                    runCatching {
                        val json = org.json.JSONObject(jsonText)
                        val actions = json.optJSONArray("action_items")
                        val events = json.optJSONArray("events")

                        if (actions != null) {
                            for (i in 0 until actions.length()) {
                                val item = actions.optJSONObject(i) ?: continue
                                val title = item.optString("title").trim()
                                if (title.isBlank()) continue
                                val person = item.optString("person").trim()
                                val due = item.optString("due").trim()
                                val dueDate = item.optString("due_date").trim()
                                allActions += AuraActionItem(
                                    id = System.currentTimeMillis() + chunkIndex * 10000L + i,
                                    title = title,
                                    person = person,
                                    dueLabel = due,
                                    dueDateEpochDay = AuraDateParser.parseIsoOrLabel(dueDate, due, today)
                                )
                            }
                        }

                        if (events != null) {
                            for (i in 0 until events.length()) {
                                val item = events.optJSONObject(i) ?: continue
                                val title = item.optString("title").trim()
                                if (title.isBlank()) continue
                                val dateLabel = item.optString("date").trim()
                                val dateIso = item.optString("date_iso").trim()
                                val time = item.optString("time").trim()
                                allEvents += AuraEventItem(
                                    id = System.currentTimeMillis() + 100000L + chunkIndex * 10000L + i,
                                    title = title,
                                    dateLabel = dateLabel,
                                    dateEpochDay = AuraDateParser.parseIsoOrLabel(dateIso, dateLabel, today),
                                    timeLabel = time,
                                    important = item.optBoolean("important", false)
                                )
                            }
                        }
                    }.onFailure { Log.w("LocalLLM", "JSON parsing failed for chunk $chunkIndex", it) }
                }

                val uniqueActions = allActions.distinctBy {
                    "${it.title.lowercase(Locale.getDefault())}|${it.person.lowercase(Locale.getDefault())}|${it.dueLabel.lowercase(Locale.getDefault())}"
                }
                val uniqueEvents = allEvents.distinctBy {
                    "${it.title.lowercase(Locale.getDefault())}|${it.dateLabel.lowercase(Locale.getDefault())}|${it.timeLabel.lowercase(Locale.getDefault())}"
                }

                val extractedActions = uniqueActions.joinToString("\n") { item ->
                    buildString {
                        append("• ").append(item.title)
                        if (item.person.isNotBlank()) append(" — ").append(item.person)
                        if (item.dueLabel.isNotBlank()) append(" · Due: ").append(item.dueLabel)
                    }
                }.ifBlank { "None" }
                val extractedEvents = uniqueEvents.joinToString("\n") { item ->
                    buildString {
                        append("• ").append(item.title)
                        if (item.dateLabel.isNotBlank()) append(" · ").append(item.dateLabel)
                        if (item.timeLabel.isNotBlank()) append(" · ").append(item.timeLabel)
                    }
                }.ifBlank { "None" }

                withContext(Dispatchers.IO) {
                    auraStore.replaceSessionItems(
                        actions = uniqueActions,
                        events = uniqueEvents,
                        transcript = transcript,
                        summary = summaryText,
                        insights = insightText,
                        actionsText = extractedActions,
                        eventsText = extractedEvents,
                        audioPath = outputFile?.absolutePath ?: "",
                        sessionId = sessionId,
                        speakerTranscript = transcriptText
                    )
                }
                AuraReminderScheduler.scheduleAll(this@MainActivity, uniqueActions, uniqueEvents)

                runOnUiThread {
                    actionItemsText = extractedActions
                    eventsText = extractedEvents
                    savedActions = auraStore.getActions()
                    savedEvents = auraStore.getEvents()
                    savedMemories = auraStore.getMemories()
                    statusMessage = "Action items and events extracted"
                }
            } catch (e: Exception) {
                Log.e("LocalLLM", "Action/event extraction failed", e)
                runOnUiThread {
                    statusMessage = "Action/event extraction failed"
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ======================================================
    // SAVED CONVERSATION ANALYSIS
    // ======================================================

    private fun beginMemoryAnalysis(session: String, action: String): Boolean {
        if (memoryAnalysisSession != null) {
            Toast.makeText(this, "Aura is already processing another memory.", Toast.LENGTH_SHORT).show()
            return false
        }
        memoryAnalysisSession = session
        memoryAnalysisAction = action
        return true
    }

    private fun endMemoryAnalysis() {
        memoryAnalysisSession = null
        memoryAnalysisAction = ""
    }

    private fun analyzeSavedMemorySummary(memory: AuraMemoryItem) {
        val transcript = memory.transcript.ifBlank { memory.speakerTranscript }
        if (transcript.isBlank()) {
            Toast.makeText(this, "This conversation has no transcript yet.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!beginMemoryAnalysis(memory.sessionId, "SUMMARY")) return

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                if (!localLLM.load()) throw IllegalStateException("Gemma failed to load")
                val response = generateChunkedText(
                    transcript = transcript,
                    instruction = """
                        Summarize this saved conversation clearly and briefly.
                        Focus on the main topic, important points, decisions or conclusions,
                        and action items only when explicitly stated.
                        Do not invent facts.
                    """.trimIndent(),
                    maxTokensPerChunk = 180,
                    finalMaxTokens = 256
                )
                saveSessionCheckpoint(
                    session = memory.sessionId,
                    transcript = transcript,
                    speakerTranscript = memory.speakerTranscript.ifBlank { transcript },
                    summary = response,
                    insights = memory.insights,
                    actionsText = memory.actionsText,
                    eventsText = memory.eventsText,
                    audioPath = memory.audioPath
                )
                withContext(Dispatchers.Main) {
                    savedMemories = auraStore.getMemories()
                    endMemoryAnalysis()
                    statusMessage = "Summary generated for saved conversation"
                }
            } catch (e: Exception) {
                Log.e("MemoryAnalysis", "Saved summary failed for ${memory.sessionId}", e)
                withContext(Dispatchers.Main) {
                    endMemoryAnalysis()
                    Toast.makeText(this@MainActivity, "Summary failed: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun analyzeSavedMemoryInsights(memory: AuraMemoryItem) {
        val transcript = memory.transcript.ifBlank { memory.speakerTranscript }
        if (transcript.isBlank()) {
            Toast.makeText(this, "This conversation has no transcript yet.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!beginMemoryAnalysis(memory.sessionId, "AI INSIGHTS")) return

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                if (!localLLM.load()) throw IllegalStateException("Gemma failed to load")
                val response = generateChunkedText(
                    transcript = transcript,
                    instruction = """
                        Analyze this saved conversation and extract useful AI insights.
                        Use these sections: Key Topics, Decisions, Action Items,
                        Dates & Deadlines, Important Notes.
                        If a section has no information, say None mentioned.
                        Use only information in the transcript. Do not invent facts.
                    """.trimIndent(),
                    maxTokensPerChunk = 220,
                    finalMaxTokens = 320
                )
                saveSessionCheckpoint(
                    session = memory.sessionId,
                    transcript = transcript,
                    speakerTranscript = memory.speakerTranscript.ifBlank { transcript },
                    summary = memory.summary,
                    insights = response,
                    actionsText = memory.actionsText,
                    eventsText = memory.eventsText,
                    audioPath = memory.audioPath
                )
                withContext(Dispatchers.Main) {
                    savedMemories = auraStore.getMemories()
                    endMemoryAnalysis()
                    statusMessage = "AI insights generated for saved conversation"
                }
            } catch (e: Exception) {
                Log.e("MemoryAnalysis", "Saved insights failed for ${memory.sessionId}", e)
                withContext(Dispatchers.Main) {
                    endMemoryAnalysis()
                    Toast.makeText(this@MainActivity, "AI insights failed: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun analyzeSavedMemoryActionsEvents(memory: AuraMemoryItem) {
        val transcript = memory.transcript.ifBlank { memory.speakerTranscript }
        if (transcript.isBlank()) {
            Toast.makeText(this, "This conversation has no transcript yet.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!beginMemoryAnalysis(memory.sessionId, "ACTIONS & EVENTS")) return

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                if (!localLLM.load()) throw IllegalStateException("Gemma failed to load")
                val today = LocalDate.now()
                val chunks = splitTextForLLM(transcript)
                val allActions = mutableListOf<AuraActionItem>()
                val allEvents = mutableListOf<AuraEventItem>()

                chunks.forEachIndexed { chunkIndex, chunk ->
                    val prompt = """
                        You are Aura's strict information extractor.
                        Today is $today.
                        Extract EVERY explicitly stated action item and scheduled event from this transcript part.
                        Never guess, infer, or invent information. Preserve spoken dates, names and times.
                        Return ONLY valid JSON with exactly this structure:
                        {
                          "action_items": [
                            {"title":"exact task", "person":"name or", "due":"exact spoken due wording or", "due_date":"YYYY-MM-DD or"}
                          ],
                          "events": [
                            {"title":"event name", "date":"exact spoken date wording or", "date_iso":"YYYY-MM-DD or", "time":"exact spoken time or", "important":true}
                          ]
                        }
                        Rules:
                        - If a person/date/time is not explicitly stated, use an empty string.
                        - Calculate date_iso for clear relative dates using today.
                        - If there are no items, return empty arrays.

                        Transcript part ${chunkIndex + 1} of ${chunks.size}:
                        $chunk
                    """.trimIndent()

                    val response = localLLM.generate(prompt, maxTokens = 360)
                    val jsonText = response.substringAfter("{", "").substringBeforeLast("}", "")
                        .let { if (it.isNotBlank()) "{$it}" else "" }
                    if (jsonText.isBlank()) return@forEachIndexed

                    runCatching {
                        val json = org.json.JSONObject(jsonText)
                        val actions = json.optJSONArray("action_items")
                        val events = json.optJSONArray("events")

                        if (actions != null) {
                            for (i in 0 until actions.length()) {
                                val item = actions.optJSONObject(i) ?: continue
                                val title = item.optString("title").trim()
                                if (title.isBlank()) continue
                                val person = item.optString("person").trim()
                                val due = item.optString("due").trim()
                                val dueDate = item.optString("due_date").trim()
                                allActions += AuraActionItem(
                                    id = System.currentTimeMillis() + chunkIndex * 10000L + i,
                                    title = title,
                                    person = person,
                                    dueLabel = due,
                                    dueDateEpochDay = AuraDateParser.parseIsoOrLabel(dueDate, due, today)
                                )
                            }
                        }

                        if (events != null) {
                            for (i in 0 until events.length()) {
                                val item = events.optJSONObject(i) ?: continue
                                val title = item.optString("title").trim()
                                if (title.isBlank()) continue
                                val dateLabel = item.optString("date").trim()
                                val dateIso = item.optString("date_iso").trim()
                                val time = item.optString("time").trim()
                                allEvents += AuraEventItem(
                                    id = System.currentTimeMillis() + 100000L + chunkIndex * 10000L + i,
                                    title = title,
                                    dateLabel = dateLabel,
                                    dateEpochDay = AuraDateParser.parseIsoOrLabel(dateIso, dateLabel, today),
                                    timeLabel = time,
                                    important = item.optBoolean("important", false)
                                )
                            }
                        }
                    }.onFailure {
                        Log.w("MemoryAnalysis", "Saved actions/events JSON parsing failed for chunk $chunkIndex", it)
                    }
                }

                val uniqueActions = allActions.distinctBy {
                    "${it.title.lowercase(Locale.getDefault())}|${it.person.lowercase(Locale.getDefault())}|${it.dueLabel.lowercase(Locale.getDefault())}"
                }
                val uniqueEvents = allEvents.distinctBy {
                    "${it.title.lowercase(Locale.getDefault())}|${it.dateLabel.lowercase(Locale.getDefault())}|${it.timeLabel.lowercase(Locale.getDefault())}"
                }
                val extractedActions = uniqueActions.joinToString("\n") { item ->
                    buildString {
                        append("• ").append(item.title)
                        if (item.person.isNotBlank()) append(" — ").append(item.person)
                        if (item.dueLabel.isNotBlank()) append(" · Due: ").append(item.dueLabel)
                    }
                }.ifBlank { "None" }
                val extractedEvents = uniqueEvents.joinToString("\n") { item ->
                    buildString {
                        append("• ").append(item.title)
                        if (item.dateLabel.isNotBlank()) append(" · ").append(item.dateLabel)
                        if (item.timeLabel.isNotBlank()) append(" · ").append(item.timeLabel)
                    }
                }.ifBlank { "None" }

                withContext(Dispatchers.IO) {
                    auraStore.replaceSessionItems(
                        actions = uniqueActions,
                        events = uniqueEvents,
                        transcript = transcript,
                        summary = memory.summary,
                        insights = memory.insights,
                        actionsText = extractedActions,
                        eventsText = extractedEvents,
                        audioPath = memory.audioPath,
                        sessionId = memory.sessionId,
                        speakerTranscript = memory.speakerTranscript.ifBlank { transcript }
                    )
                }
                AuraReminderScheduler.scheduleAll(this@MainActivity, uniqueActions, uniqueEvents)

                withContext(Dispatchers.Main) {
                    savedActions = auraStore.getActions()
                    savedEvents = auraStore.getEvents()
                    savedMemories = auraStore.getMemories()
                    endMemoryAnalysis()
                    statusMessage = "Actions and events generated for saved conversation"
                }
            } catch (e: Exception) {
                Log.e("MemoryAnalysis", "Saved actions/events failed for ${memory.sessionId}", e)
                withContext(Dispatchers.Main) {
                    endMemoryAnalysis()
                    Toast.makeText(this@MainActivity, "Actions/events failed: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun analyzeSavedMemoryImportantDates(memory: AuraMemoryItem) {
        val transcript = memory.transcript.ifBlank { memory.speakerTranscript }
        if (transcript.isBlank()) {
            Toast.makeText(this, "This conversation has no transcript yet.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!beginMemoryAnalysis(memory.sessionId, "IMPORTANT DATES")) return

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                if (!localLLM.load()) throw IllegalStateException("Gemma failed to load")
                val today = LocalDate.now()
                val chunks = splitTextForLLM(transcript)
                val extracted = mutableListOf<AuraImportantDate>()

                chunks.forEachIndexed { chunkIndex, chunk ->
                    val prompt = """
                        You are Aura's strict date extractor.
                        Today is $today.
                        Extract EVERY explicitly stated important date from this transcript part.
                        Include deadlines, meetings, appointments, exams, submissions, payments,
                        birthdays, travel dates, launches, interviews, or other dates that matter.
                        Do not invent dates. Only use information supported by the transcript.
                        For relative dates such as tomorrow, calculate the ISO date using today.
                        Return ONLY valid JSON with exactly this structure:
                        {
                          "important_dates": [
                            {"date":"spoken date wording", "date_iso":"YYYY-MM-DD", "reason":"why this date is important"}
                          ]
                        }
                        If no important dates are present, return an empty array.

                        Transcript part ${chunkIndex + 1} of ${chunks.size}:
                        $chunk
                    """.trimIndent()

                    val response = localLLM.generate(prompt, maxTokens = 320)
                    val jsonText = response.substringAfter("{", "").substringBeforeLast("}", "")
                        .let { if (it.isNotBlank()) "{$it}" else "" }
                    if (jsonText.isBlank()) return@forEachIndexed

                    runCatching {
                        val json = org.json.JSONObject(jsonText)
                        val dates = json.optJSONArray("important_dates") ?: return@runCatching
                        for (i in 0 until dates.length()) {
                            val item = dates.optJSONObject(i) ?: continue
                            val dateLabel = item.optString("date").trim()
                            val dateIso = item.optString("date_iso").trim()
                            val reason = item.optString("reason").trim()
                            if (reason.isBlank()) continue
                            val epochDay = if (dateIso.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                runCatching { LocalDate.parse(dateIso).toEpochDay() }.getOrNull()
                            } else {
                                AuraDateParser.parseIsoOrLabel(dateIso, dateLabel, today)
                            }
                            if (epochDay != null) {
                                extracted += AuraImportantDate(
                                    id = System.currentTimeMillis() + chunkIndex * 10000L + i,
                                    sessionId = memory.sessionId,
                                    dateEpochDay = epochDay,
                                    dateLabel = dateLabel.ifBlank { LocalDate.ofEpochDay(epochDay).toString() },
                                    reason = reason
                                )
                            }
                        }
                    }.onFailure {
                        Log.w("MemoryAnalysis", "Important-date JSON parsing failed for chunk $chunkIndex", it)
                    }
                }

                val uniqueDates = extracted.distinctBy {
                    "${it.sessionId}|${it.dateEpochDay}|${it.reason.lowercase(Locale.getDefault())}"
                }
                auraStore.replaceSessionImportantDates(memory.sessionId, uniqueDates)

                withContext(Dispatchers.Main) {
                    savedImportantDates = auraStore.getImportantDates()
                    savedMemories = auraStore.getMemories()
                    endMemoryAnalysis()
                    statusMessage = if (uniqueDates.isEmpty()) {
                        "No important dates found in this conversation"
                    } else {
                        "${uniqueDates.size} important date(s) highlighted in Calendar"
                    }
                }
            } catch (e: Exception) {
                Log.e("MemoryAnalysis", "Saved important dates failed for ${memory.sessionId}", e)
                withContext(Dispatchers.Main) {
                    endMemoryAnalysis()
                    Toast.makeText(this@MainActivity, "Important dates failed: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ======================================================
    // ASK AURA MEMORY (LOCAL RAG)
    // ======================================================

    // ======================================================
// ASK AURA MEMORY (LOCAL RAG)
// ======================================================

    private fun askAuraMemory() {

        val question = ragQuestion.trim()

        // --------------------------------------------------
        // 1. Validate question
        // --------------------------------------------------

        if (question.isBlank()) {
            Toast.makeText(
                this,
                "Type a question about your past conversations.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // --------------------------------------------------
        // 2. Check whether semantic memory exists
        // --------------------------------------------------

        if (vectorMemoryStore.count() == 0) {
            ragAnswer =
                "No semantic memories are indexed yet. Record and transcribe a conversation first."
            ragSources = ""
            return
        }

        // --------------------------------------------------
        // 3. Show loading state
        // --------------------------------------------------

        ragLoading = true
        ragAnswer = ""
        ragSources = ""

        lifecycleScope.launch(Dispatchers.Default) {

            try {

                Log.d(
                    "RAG",
                    "========================================"
                )

                Log.d(
                    "RAG",
                    "Starting semantic memory search"
                )

                Log.d(
                    "RAG",
                    "Question: $question"
                )

                // --------------------------------------------------
                // 4. Load BGE embedding model
                // --------------------------------------------------

                val embeddingLoaded = embeddingManager.load()

                if (!embeddingLoaded) {

                    runOnUiThread {

                        ragLoading = false

                        ragAnswer =
                            "The local embedding model could not be loaded."

                        ragSources = ""
                    }

                    return@launch
                }

                // --------------------------------------------------
                // 5. Convert user question into embedding
                // --------------------------------------------------

                val queryEmbedding =
                    embeddingManager.embed(question)

                if (queryEmbedding == null) {

                    runOnUiThread {

                        ragLoading = false

                        ragAnswer =
                            "I could not create a search embedding for that question."

                        ragSources = ""
                    }

                    return@launch
                }

                Log.d(
                    "RAG",
                    "Query embedding dimensions: ${queryEmbedding.size}"
                )

                // --------------------------------------------------
                // 6. Search semantic memory
                //
                // Use top 3 instead of top 5.
                // This keeps the Gemma context focused.
                // --------------------------------------------------

                val results =
                    vectorMemoryStore.search(
                        queryEmbedding,
                        topK = 3
                    )

                if (results.isEmpty()) {

                    runOnUiThread {

                        ragLoading = false

                        ragAnswer =
                            "I couldn't find any relevant local memory for that question."

                        ragSources = ""
                    }

                    return@launch
                }

                // --------------------------------------------------
                // 7. Log retrieved memories
                // --------------------------------------------------

                Log.d(
                    "RAG",
                    "Retrieved ${results.size} memories"
                )

                results.forEachIndexed { index, result ->

                    Log.d(
                        "RAG",
                        "Memory ${index + 1}: " +
                                "similarity=${result.similarity}, " +
                                "session=${result.sessionId}, " +
                                "text=${result.text}"
                    )
                }

                val topSimilarity =
                    results.first().similarity

                Log.d(
                    "RAG",
                    "Top similarity: $topSimilarity"
                )

                // --------------------------------------------------
                // 8. Build focused context for Gemma
                // --------------------------------------------------

                val contextText =
                    results
                        .mapIndexed { index, result ->

                            """
                        MEMORY ${index + 1}
                        ${result.text}
                        """.trimIndent()

                        }
                        .joinToString("\n\n")

                Log.d(
                    "RAG",
                    "Context length: ${contextText.length} characters"
                )

                // --------------------------------------------------
                // 9. Load Gemma
                // --------------------------------------------------

                val llmLoaded =
                    localLLM.load()

                if (!llmLoaded) {

                    runOnUiThread {

                        ragLoading = false

                        ragAnswer =
                            "The local language model could not be loaded."

                        ragSources = ""
                    }

                    return@launch
                }

                // --------------------------------------------------
                // 10. Stronger grounded RAG prompt
                // --------------------------------------------------

                val prompt = """
                You are answering a question using Aura's private
                saved conversation memory.

                IMPORTANT RULES:

                1. Use ONLY the information contained in the
                   retrieved memories below.

                2. Do NOT use outside knowledge.

                3. Do NOT invent or assume any information.

                4. If the retrieved memory contains the answer,
                   answer the user's question directly and clearly.

                5. Do NOT say "I couldn't find that in your saved
                   conversations" when the retrieved memory contains
                   information that answers the question.

                6. Keep the answer concise.

                7. If the retrieved memories genuinely do not contain
                   enough information to answer the question, then say:

                "I couldn't find that in your saved conversations."

                USER QUESTION:
                $question

                RETRIEVED AURA MEMORIES:
                $contextText

                ANSWER:
            """.trimIndent()

                Log.d(
                    "RAG",
                    "Sending grounded prompt to Gemma"
                )

                // --------------------------------------------------
                // 11. Generate answer
                // --------------------------------------------------

                val response =
                    localLLM.generate(
                        prompt = prompt,
                        maxTokens = 160
                    ).trim()

                Log.d(
                    "RAG",
                    "Gemma response: $response"
                )

                // --------------------------------------------------
                // 12. Detect incorrect fallback
                //
                // If Gemma incorrectly says it could not find the
                // answer even though we have a strong retrieved
                // memory, use the strongest retrieved memory as
                // a reliable fallback.
                // --------------------------------------------------

                val fallbackPhrase =
                    "I couldn't find that in your saved conversations."

                val gemmaReturnedFallback =
                    response.equals(
                        fallbackPhrase,
                        ignoreCase = true
                    ) ||
                            response.contains(
                                fallbackPhrase,
                                ignoreCase = true
                            )

                val finalAnswer: String

                if (
                    gemmaReturnedFallback &&
                    topSimilarity >= 0.70f
                ) {

                    Log.w(
                        "RAG",
                        "Gemma returned fallback despite strong retrieval. " +
                                "Using top retrieved memory as fallback."
                    )

                    finalAnswer =
                        "Based on your saved memory:\n\n" +
                                results.first().text.trim()

                } else if (response.isBlank()) {

                    Log.w(
                        "RAG",
                        "Gemma returned an empty response. " +
                                "Using top retrieved memory."
                    )

                    finalAnswer =
                        "Based on your saved memory:\n\n" +
                                results.first().text.trim()

                } else {

                    finalAnswer = response
                }

                // --------------------------------------------------
                // 13. Build source display
                // --------------------------------------------------

                val sourceText =
                    results
                        .mapIndexed { index, result ->

                            val sessionLabel =
                                if (result.sessionId.length > 12) {
                                    result.sessionId.takeLast(12)
                                } else {
                                    result.sessionId
                                }

                            val similarity =
                                String.format(
                                    Locale.US,
                                    "%.2f",
                                    result.similarity
                                )

                            """
                        ${index + 1}. Memory $sessionLabel · similarity $similarity
                        ${result.text}
                        """.trimIndent()

                        }
                        .joinToString("\n\n")

                // --------------------------------------------------
                // 14. Log final result
                // --------------------------------------------------

                Log.d(
                    "RAG",
                    "Final answer: $finalAnswer"
                )

                Log.d(
                    "RAG",
                    "Semantic search completed successfully"
                )

                Log.d(
                    "RAG",
                    "========================================"
                )

                // --------------------------------------------------
                // 15. Update UI
                // --------------------------------------------------

                runOnUiThread {

                    ragLoading = false

                    ragAnswer =
                        finalAnswer

                    ragSources =
                        sourceText
                }

            } catch (e: Exception) {

                Log.e(
                    "RAG",
                    "Semantic memory search failed",
                    e
                )

                runOnUiThread {

                    ragLoading = false

                    ragAnswer =
                        "Memory search failed: ${
                            e.message ?: "unknown error"
                        }"

                    ragSources = ""
                }
            }
        }
    }
    // ======================================================
    // MICROPHONE PERMISSION
    // ======================================================

    private val microphonePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {

                statusMessage =
                    "Microphone permission granted"

            } else {

                statusMessage =
                    "Microphone permission denied"
            }
        }


    // ======================================================
    // NOTIFICATIONS
    // ======================================================

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            getPreferences(MODE_PRIVATE)
                .edit()
                .putBoolean("notifications_enabled", granted)
                .apply()

            if (granted) {
                createNotificationChannel()
                showTestNotification()
            }

            showNotificationsSetup = false
        }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "aura_reminders",
                "Aura Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Aura reminders and events"
                enableVibration(true)
            }

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showTestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification =
            androidx.core.app.NotificationCompat.Builder(this, "aura_reminders")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Aura")
                .setContentText("Notifications are ready.")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 250, 120, 250))
                .build()

        val manager =
            androidx.core.app.NotificationManagerCompat.from(this)

        manager.notify(1001, notification)
    }

    // ======================================================
    // ON CREATE
    // ======================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        whisperASR =
            WhisperASR(this)

        localLLM =
            LocalLLM(this)

        embeddingManager =
            EmbeddingManager(this)

        vectorMemoryStore =
            VectorMemoryStore(this)

        val preferences =
            getPreferences(MODE_PRIVATE)

        showWelcome =
            !preferences.getBoolean("welcome_seen", false)

        showProfileSetup =
            !showWelcome &&
                    !preferences.getBoolean("profile_created", false)

        profileName =
            preferences.getString("profile_name", "") ?: ""

        createNotificationChannel()

        savedActions = auraStore.getActions()
        savedEvents = auraStore.getEvents()
        savedMemories = auraStore.getMemories()
        savedImportantDates = auraStore.getImportantDates()

        setContent {

            CompositionLocalProvider(
                LocalTextStyle provides
                        LocalTextStyle.current.copy(
                            fontFamily = AuraFont,
                            letterSpacing = 0.sp
                        )
            ) {
                AuraTheme {

                    if (showWelcome) {

                        AuraWelcomeScreen(
                            onGetStarted = {
                                getPreferences(MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("welcome_seen", true)
                                    .apply()

                                showWelcome = false
                                showProfileSetup = true
                            }
                        )

                    } else if (showProfileSetup) {

                        AuraProfileSetupScreen(
                            onProfileCreated = { name, email ->
                                profileName = name

                                getPreferences(MODE_PRIVATE)
                                    .edit()
                                    .putString("profile_name", name)
                                    .putString("profile_email", email)
                                    .putBoolean("profile_created", true)
                                    .apply()

                                showProfileSetup = false
                                showHardwareCheck = true
                            }
                        )

                    } else if (showHardwareCheck) {

                        AuraHardwareCheckScreen(
                            onContinue = {
                                showHardwareCheck = false
                                showNotificationsSetup = true
                            }
                        )

                    } else if (showNotificationsSetup) {

                        AuraNotificationsSetupScreen(
                            onAllow = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                } else {
                                    getPreferences(MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("notifications_enabled", true)
                                        .apply()
                                    createNotificationChannel()
                                    showTestNotification()
                                    showNotificationsSetup = false
                                }
                            },
                            onNotNow = {
                                getPreferences(MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("notifications_enabled", false)
                                    .apply()
                                showNotificationsSetup = false
                            }
                        )

                    } else if (showAuraHub) {

                        AuraHubScreen(
                            profileName = profileName,
                            actions = savedActions,
                            events = savedEvents,
                            memories = savedMemories,
                            importantDates = savedImportantDates,
                            ragQuestion = ragQuestion,
                            ragAnswer = ragAnswer,
                            ragSources = ragSources,
                            ragLoading = ragLoading,
                            onRagQuestionChange = { ragQuestion = it },
                            onAskAuraMemory = { askAuraMemory() },
                            onAnalyzeSavedSummary = { memory -> analyzeSavedMemorySummary(memory) },
                            onAnalyzeSavedInsights = { memory -> analyzeSavedMemoryInsights(memory) },
                            onAnalyzeSavedActionsEvents = { memory -> analyzeSavedMemoryActionsEvents(memory) },
                            onAnalyzeSavedImportantDates = { memory -> analyzeSavedMemoryImportantDates(memory) },
                            memoryAnalysisSession = memoryAnalysisSession,
                            memoryAnalysisAction = memoryAnalysisAction,
                            onBack = { showAuraHub = false },
                            onRefresh = {
                                savedActions = auraStore.getActions()
                                savedEvents = auraStore.getEvents()
                                savedMemories = auraStore.getMemories()
                                savedImportantDates = auraStore.getImportantDates()
                                Toast.makeText(this@MainActivity, "Aura data refreshed", Toast.LENGTH_SHORT).show()
                            },
                            onCompleteAction = { id ->
                                auraStore.setActionCompleted(id, true)
                                savedActions = auraStore.getActions()
                            },
                            onDeleteAction = { id ->
                                auraStore.deleteAction(id)
                                savedActions = auraStore.getActions()
                            },
                            onDeleteEvent = { id ->
                                auraStore.deleteEvent(id)
                                savedEvents = auraStore.getEvents()
                            },
                            onEnableNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    getPreferences(MODE_PRIVATE).edit().putBoolean("notifications_enabled", true).apply()
                                    showTestNotification()
                                }
                            },
                            onResetDemo = {
                                getPreferences(MODE_PRIVATE).edit()
                                    .putBoolean("welcome_seen", false)
                                    .putBoolean("profile_created", false)
                                    .remove("profile_name")
                                    .remove("profile_email")
                                    .apply()
                                recreate()
                            }
                        )

                    } else {

                        RecordingScreen(

                            profileName =
                            profileName,

                            isRecording =
                            isRecording,

                            isPaused =
                            isPaused,

                            statusMessage =
                            statusMessage,

                            recordingElapsedMs =
                            recordingElapsedMs,

                            processingStage =
                            processingStage,

                            processingElapsedMs =
                            processingElapsedMs,

                            processingStageDurations =
                            processingStageDurations,

                            vadStatus =
                            vadStatus,

                            transcriptText =
                            transcriptText,

                            summaryText =
                            summaryText,

                            insightText =
                            insightText,

                            actionItemsText =
                            actionItemsText,

                            eventsText =
                            eventsText,

                            onManualRecord = {

                                if (hasMicrophonePermission()) {

                                    startRecording()

                                } else {

                                    microphonePermissionLauncher.launch(
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                }
                            },

                            onManualStop = {

                                stopRecording()
                            },

                            onPauseRecording = {

                                pauseRecording()
                            },

                            onResumeRecording = {

                                resumeRecording()
                            },

                            onStartVAD = {

                                if (hasMicrophonePermission()) {

                                    startVAD()

                                } else {

                                    microphonePermissionLauncher.launch(
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                }
                            },

                            onStopVAD = {

                                stopVAD()
                            },

                            onConvertAudio = {

                                convertLatestRecording()
                            },

                            onShareAudio = {

                                shareLatestRecording()
                            },

                            onTestLLM = {

                                testLocalLLM()
                            },

                            onGenerateSummary = {

                                generateSummary()
                            },

                            onGenerateInsights = {

                                generateInsights()
                            },

                            onGenerateActionItemsAndEvents = {

                                generateActionItemsAndEvents()
                            },

                            onOpenAuraHub = {
                                savedActions = auraStore.getActions()
                                savedEvents = auraStore.getEvents()
                                savedMemories = auraStore.getMemories()
                                savedImportantDates = auraStore.getImportantDates()
                                showAuraHub = true
                            }
                        )
                    }
                }
            }
        }

    }


    // ======================================================
    // MICROPHONE PERMISSION
    // ======================================================

    private fun hasMicrophonePermission(): Boolean {

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }


    // ======================================================
    // START VAD
    // ======================================================

    private fun startVAD() {
        vadStatus = "Listening..."
        voiceActivityDetector.startListening(
            onSpeechDetected = {
                runOnUiThread {
                    vadStatus = "🗣️ Speech detected"
                    // VAD may start a new session when Aura is idle.
                    // It never interrupts or force-stops a manual session.
                    if (!isRecording && !isPaused) startRecording()
                }
            },
            onSilenceDetected = {
                runOnUiThread {
                    vadStatus = "🔇 Silence — session remains active"
                }
            }
        )
    }

    // ======================================================
    // STOP VAD
    // ======================================================

    private fun stopVAD() {

        voiceActivityDetector.stopListening()

        if (isRecording) {

            stopRecording()
        }

        vadStatus =
            "VAD stopped"
    }


    // ======================================================
    // START RECORDING
    // ======================================================

    private fun startRecording() {
        // If the current session is paused, resume it instead of creating a new session.
        if (isRecording && isPaused) {
            resumeRecording()
            return
        }
        if (isRecording) return

        transcriptText = ""
        fullTranscriptText = ""
        summaryText = ""
        insightText = ""
        actionItemsText = ""
        eventsText = ""

        val recordingsFolder = File(filesDir, "recordings")
        if (!recordingsFolder.exists()) recordingsFolder.mkdirs()

        sessionId = "Session_${System.currentTimeMillis()}"
        startTime = System.currentTimeMillis()
        recordingActiveStartedAt = startTime
        accumulatedRecordingMs = 0L
        recordingElapsedMs = 0L
        recordingDurationMs = 0L
        isPaused = false
        resetProcessingTiming()
        startRecordingTimer()

        outputFile = File(recordingsFolder, "$sessionId.m4a")

        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile!!.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            statusMessage = "Recording..."
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            outputFile?.delete()
            outputFile = null
            isRecording = false
            isPaused = false
            stopRecordingTimer()
            statusMessage = "Recording failed: ${e.message}"
            Log.e("MainActivity", "Failed to start recording", e)
        }
    }

    private fun pauseRecording() {
        if (!isRecording || isPaused) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                recorder?.pause()
                accumulatedRecordingMs +=
                    (System.currentTimeMillis() - recordingActiveStartedAt).coerceAtLeast(0L)
                isPaused = true
                statusMessage = "Recording paused"
                recordingElapsedMs = accumulatedRecordingMs
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to pause recording", e)
            statusMessage = "Pause failed: ${e.message}"
        }
    }

    private fun resumeRecording() {
        if (!isRecording || !isPaused) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                recorder?.resume()
                recordingActiveStartedAt = System.currentTimeMillis()
                isPaused = false
                statusMessage = "Recording..."
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to resume recording", e)
            statusMessage = "Resume failed: ${e.message}"
        }
    }

    // ======================================================
    // LIVE TIMER HELPERS
    // ======================================================

    private fun startRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = lifecycleScope.launch {
            while (isRecording) {
                recordingElapsedMs = if (isPaused) {
                    accumulatedRecordingMs
                } else {
                    accumulatedRecordingMs +
                            (System.currentTimeMillis() - recordingActiveStartedAt).coerceAtLeast(0L)
                }
                delay(250L)
            }
        }
    }

    private fun stopRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
    }

    private fun startProcessingStage(stage: String) {
        runOnUiThread {
            processingStage = stage
            processingElapsedMs = 0L
            processingStageStartedAt = System.currentTimeMillis()

            processingTimerJob?.cancel()
            processingTimerJob = lifecycleScope.launch {
                while (processingStage == stage) {
                    processingElapsedMs =
                        (System.currentTimeMillis() - processingStageStartedAt).coerceAtLeast(0L)
                    delay(250L)
                }
            }
        }
    }

    private fun finishProcessingStage(stage: String) {
        runOnUiThread {
            if (processingStage != stage) return@runOnUiThread

            val duration =
                (System.currentTimeMillis() - processingStageStartedAt).coerceAtLeast(0L)

            processingStageDurations =
                processingStageDurations.toMutableMap().apply {
                    this[stage] = duration
                }

            processingElapsedMs = duration
            processingStage = ""
            processingTimerJob?.cancel()
            processingTimerJob = null

            saveTimingData()
        }
    }

    private fun failProcessingStage(stage: String) {
        finishProcessingStage(stage)
    }

    private fun resetProcessingTiming() {
        processingTimerJob?.cancel()
        processingTimerJob = null
        processingStage = ""
        processingElapsedMs = 0L
        processingStageStartedAt = 0L
        processingStageDurations = emptyMap()
    }

    private fun saveTimingData() {
        if (sessionId.isBlank()) return

        val folder = File(filesDir, "conversations/$sessionId")
        if (!folder.exists()) folder.mkdirs()

        try {
            val json = org.json.JSONObject()
                .put("sessionId", sessionId)
                .put("recordingDurationMs", recordingDurationMs)
                .put("recordingDuration", formatAuraDuration(recordingDurationMs))
                .put("savedAt", System.currentTimeMillis())

            val stages = org.json.JSONObject()
            processingStageDurations.forEach { (name, duration) ->
                stages.put(name, duration)
            }

            json.put("processingStagesMs", stages)

            val totalProcessing =
                processingStageDurations.values.sum()

            json.put("totalProcessingDurationMs", totalProcessing)
            json.put("totalProcessingDuration", formatAuraDuration(totalProcessing))

            File(folder, "timings.json").writeText(json.toString(2))

        } catch (e: Exception) {
            Log.e("AuraTiming", "Failed to save timing data", e)
        }
    }


    private suspend fun saveSessionCheckpoint(
        session: String,
        transcript: String,
        speakerTranscript: String = "",
        summary: String = "",
        insights: String = "",
        actionsText: String = "",
        eventsText: String = "",
        audioPath: String = ""
    ) = withContext(Dispatchers.IO) {
        if (session.isBlank() || transcript.isBlank()) return@withContext
        auraStore.replaceSessionItems(
            actions = emptyList(),
            events = emptyList(),
            transcript = transcript,
            summary = summary,
            insights = insights,
            actionsText = actionsText,
            eventsText = eventsText,
            audioPath = audioPath,
            sessionId = session,
            speakerTranscript = speakerTranscript
        )
        if (session == sessionId) {
            val refreshed = auraStore.getMemories()
            runOnUiThread { savedMemories = refreshed }
        }
    }

    private fun conversationFolder(session: String) =
        File(filesDir, "conversations/$session")

    private fun sessionStatusFile(session: String) =
        File(conversationFolder(session), "recording.json")

    private fun updateSessionStatus(
        session: String,
        status: String,
        stage: String = "",
        error: String = ""
    ) {
        runCatching {
            val folder = conversationFolder(session)
            if (!folder.exists()) folder.mkdirs()
            val file = sessionStatusFile(session)
            val existing = if (file.exists()) {
                runCatching { org.json.JSONObject(file.readText()) }.getOrDefault(org.json.JSONObject())
            } else org.json.JSONObject()
            existing.put("sessionId", session)
                .put("status", status)
                .put("stage", stage)
                .put("updatedAt", System.currentTimeMillis())
            if (error.isBlank()) existing.remove("error") else existing.put("error", error.take(1000))
            file.writeText(existing.toString(2))
        }.onFailure { Log.e("AuraPipeline", "Failed to write session status", it) }
    }

    // ======================================================
    // STOP RECORDING
    // ======================================================

    private fun stopRecording() {
        if (!isRecording) return

        val session = sessionId
        val audioBeingRecorded = outputFile
        val endTime = System.currentTimeMillis()

        // Account for the final active interval, excluding paused time.
        if (!isPaused) {
            accumulatedRecordingMs +=
                (endTime - recordingActiveStartedAt).coerceAtLeast(0L)
        }

        var stoppedSuccessfully = false
        recorder?.let { activeRecorder ->
            try {
                activeRecorder.stop()
                stoppedSuccessfully = true
            } catch (e: RuntimeException) {
                Log.e("MainActivity", "Recorder stop failed", e)
            } finally {
                activeRecorder.release()
            }
        }
        recorder = null
        isRecording = false
        isPaused = false
        stopRecordingTimer()

        recordingDurationMs = accumulatedRecordingMs.coerceAtLeast(0L)
        recordingElapsedMs = recordingDurationMs

        if (!stoppedSuccessfully || audioBeingRecorded == null || !audioBeingRecorded.exists()) {
            statusMessage = "Recording could not be saved"
            audioBeingRecorded?.delete()
            return
        }

        val conversationFolder = conversationFolder(session)
        if (!conversationFolder.exists()) conversationFolder.mkdirs()
        val permanentAudio = File(conversationFolder, "audio.m4a")
        if (permanentAudio.exists()) permanentAudio.delete()

        val moved = audioBeingRecorded.renameTo(permanentAudio)
        outputFile = if (moved) permanentAudio else audioBeingRecorded

        val metadata = org.json.JSONObject()
            .put("sessionId", session)
            .put("startTime", startTime)
            .put("endTime", endTime)
            .put("durationMs", recordingDurationMs)
            .put("duration", formatAuraDuration(recordingDurationMs))
            .put("audioPath", outputFile!!.absolutePath)
            .put("status", "RECORDED")
            .put("stage", "queued")
            .put("createdAt", System.currentTimeMillis())
        runCatching { sessionStatusFile(session).writeText(metadata.toString(2)) }

        saveTimingDataForSession(session)
        statusMessage = "Session saved successfully"
    }

    private fun saveTimingDataForSession(session: String) {
        runCatching {
            val folder = conversationFolder(session)
            if (!folder.exists()) folder.mkdirs()
            val json = org.json.JSONObject()
                .put("sessionId", session)
                .put("recordingDurationMs", recordingDurationMs)
                .put("recordingDuration", formatAuraDuration(recordingDurationMs))
                .put("savedAt", System.currentTimeMillis())
            val stages = org.json.JSONObject()
            processingStageDurations.forEach { (name, duration) -> stages.put(name, duration) }
            json.put("processingStagesMs", stages)
                .put("totalProcessingDurationMs", processingStageDurations.values.sum())
                .put("totalProcessingDuration", formatAuraDuration(processingStageDurations.values.sum()))
            File(folder, "timings.json").writeText(json.toString(2))
        }.onFailure { Log.e("AuraTiming", "Failed to save timing data", it) }
    }

    private fun enqueueRecordingForProcessing(session: String) {
        if (session.isBlank() || !queuedSessions.add(session)) return
        lifecycleScope.launch(Dispatchers.Default) {
            processingMutex.withLock {
                try {
                    processRecording(session)
                } finally {
                    queuedSessions.remove(session)
                }
            }
        }
    }

    private fun processPendingRecordings() {
        val folder = File(filesDir, "conversations")
        val sessions = folder.listFiles()
            ?.filter { it.isDirectory && File(it, "audio.m4a").exists() }
            ?.sortedBy { it.lastModified() }
            ?: emptyList()
        sessions.forEach { sessionDir ->
            val status = runCatching {
                val f = File(sessionDir, "recording.json")
                if (!f.exists()) "" else org.json.JSONObject(f.readText()).optString("status")
            }.getOrDefault("")
            if (!status.equals("COMPLETE", ignoreCase = true)) {
                enqueueRecordingForProcessing(sessionDir.name)
            }
        }
    }

    private suspend fun processRecording(session: String) {
        val folder = conversationFolder(session)
        val m4a = File(folder, "audio.m4a")
        if (!m4a.exists()) return

        val savedStatus = runCatching {
            val f = sessionStatusFile(session)
            if (f.exists()) org.json.JSONObject(f.readText()).optString("status") else ""
        }.getOrDefault("")
        if (savedStatus.equals("COMPLETE", ignoreCase = true)) return

        updateSessionStatus(session, "PROCESSING", "audio_conversion")
        val wav = File(folder, "audio.wav")

        try {
            if (!wav.exists() || wav.length() <= 44) {
                startProcessingStage("Audio conversion")
                val success = withContext(Dispatchers.IO) {
                    audioPreprocessor.convertM4aToWav(m4a, wav)
                }
                if (!success) throw IllegalStateException("WAV conversion failed")
                finishProcessingStage("Audio conversion")
            }

            var whisperSegments: List<WhisperSegment> = emptyList()
            var rawTranscript = ""
            val existingMemory = auraStore.getMemories().firstOrNull { it.sessionId == session }

            val hasSpeakerCheckpoint = existingMemory?.speakerTranscript?.isNotBlank() == true &&
                    existingMemory.speakerTranscript != existingMemory.transcript

            if (hasSpeakerCheckpoint) {
                rawTranscript = existingMemory!!.transcript
                Log.d("AuraPipeline", "Speaker transcript checkpoint already exists for $session")
                if (session == sessionId) {
                    runOnUiThread {
                        fullTranscriptText = existingMemory.transcript
                        transcriptText = existingMemory.speakerTranscript
                    }
                }
                return
            } else if (existingMemory?.transcript?.isNotBlank() == true) {
                rawTranscript = existingMemory.transcript
                Log.d("AuraPipeline", "Transcript checkpoint already exists for $session; rerunning Whisper for speaker alignment")
            } else {
                updateSessionStatus(session, "PROCESSING", "transcription")
                startProcessingStage("Transcription")
                whisperSegments = whisperASR.transcribeSegments(wav)
                rawTranscript = whisperSegments.joinToString("\n") { it.text.trim() }.trim()
                if (rawTranscript.isBlank()) throw IllegalStateException("Whisper returned an empty transcript")
                finishProcessingStage("Transcription")

                // IMPORTANT: save before diarization. A slow/failed diarization run can no longer erase the memory.
                saveSessionCheckpoint(
                    session = session,
                    transcript = rawTranscript,
                    speakerTranscript = rawTranscript,
                    audioPath = m4a.absolutePath
                )
                updateSessionStatus(session, "TRANSCRIPT_SAVED", "memory_embeddings")

                indexConversationInVectorMemory(session, rawTranscript)
            }

            // Diarization is isolated from Whisper/memory persistence.
            updateSessionStatus(session, "PROCESSING", "speaker_diarization")
            startProcessingStage("Speaker diarization")
            val speakerSegments = speakerDiarization.processWav(wav)
            finishProcessingStage("Speaker diarization")

            if (whisperSegments.isEmpty()) {
                // If this session already had a transcript checkpoint, don't destroy it.
                // A future reprocess can perform full speaker alignment from a fresh Whisper run.
                updateSessionStatus(session, "TRANSCRIPT_SAVED", "speaker_diarization_complete")
                if (session == sessionId) {
                    runOnUiThread { statusMessage = "Transcript saved. Speaker diarization completed." }
                }
                return
            }

            val formattedTranscript = buildSpeakerTranscript(whisperSegments, speakerSegments)
            saveSessionCheckpoint(
                session = session,
                transcript = rawTranscript,
                speakerTranscript = formattedTranscript,
                audioPath = m4a.absolutePath
            )
            updateSessionStatus(session, "COMPLETE", "done")

            if (session == sessionId) {
                runOnUiThread {
                    fullTranscriptText = rawTranscript
                    transcriptText = formattedTranscript
                    statusMessage = "Conversation processed and saved to Memory"
                    savedMemories = auraStore.getMemories()
                }
            }
        } catch (e: Exception) {
            val stage = processingStage
            if (stage.isNotBlank()) failProcessingStage(stage)
            updateSessionStatus(session, "PARTIAL", stage, e.message ?: "Unknown error")
            Log.e("AuraPipeline", "Processing failed for $session", e)
            if (session == sessionId) {
                runOnUiThread {
                    statusMessage = "Processing paused at ${stage.ifBlank { "unknown stage" }}. Transcript/memory already saved when available."
                }
            }
        }
    }

    private fun convertLatestRecording() {
        val conversationsFolder = File(filesDir, "conversations")

        val latestSession = conversationsFolder
            .listFiles()
            ?.filter { sessionDir ->
                sessionDir.isDirectory &&
                        File(sessionDir, "audio.m4a").exists()
            }
            ?.maxByOrNull { sessionDir ->
                File(sessionDir, "audio.m4a").lastModified()
            }

        if (latestSession == null) {
            statusMessage = "No recording found"
            return
        }

        // Processing is manual again: only the latest saved recording is queued.
        transcriptText = ""
        fullTranscriptText = ""
        summaryText = ""
        insightText = ""
        actionItemsText = ""
        eventsText = ""
        resetProcessingTiming()
        sessionId = latestSession.name
        outputFile = File(latestSession, "audio.m4a")

        statusMessage = "Processing latest recording..."
        enqueueRecordingForProcessing(sessionId)
    }

    // ======================================================
    // FORMAT WHISPER SEGMENTS
    // ======================================================

    private fun buildSpeakerTranscript(
        whisperSegments: List<WhisperSegment>,
        speakerSegments: List<SpeakerSegment>
    ): String {

        if (whisperSegments.isEmpty()) {
            return "No speech segments detected."
        }

        if (speakerSegments.isEmpty()) {
            return buildString {
                append("WHISPER TRANSCRIPT\n")
                append("==================\n\n")

                whisperSegments.forEachIndexed { index, segment ->
                    append("Segment ${index + 1}\n")
                    append(
                        "[${formatTime(segment.start)} → ${formatTime(segment.end)}]\n"
                    )
                    append("${segment.text.trim()}\n\n")
                }

                append("⚠️ Speaker diarization returned no segments.")
            }
        }

        return buildString {
            append("AURA TRANSCRIPT\n")
            append("================\n\n")

            whisperSegments.forEach { whisperSegment ->

                val whisperStart = whisperSegment.start
                val whisperEnd = whisperSegment.end

                val overlapping = speakerSegments.filter { speakerSegment ->
                    val overlapStart = maxOf(whisperStart, speakerSegment.start)
                    val overlapEnd = minOf(whisperEnd, speakerSegment.end)
                    overlapEnd > overlapStart
                }

                val speaker: Int = if (overlapping.isEmpty()) {
                    -1
                } else {
                    // Calculate total overlap for every speaker.
                    val overlapBySpeaker = overlapping
                        .groupBy { it.speaker }
                        .mapValues { (_, segments) ->
                            segments.sumOf { speakerSegment ->
                                val overlapStart = maxOf(whisperStart, speakerSegment.start)
                                val overlapEnd = minOf(whisperEnd, speakerSegment.end)
                                (overlapEnd - overlapStart).toDouble()
                            }
                        }

                    // If the Whisper segment contains a speaker change,
                    // prefer the speaker active closest to the end.
                    // This handles short conversations where Whisper may
                    // combine two speakers into one text segment.
                    val speakerAtEnd = overlapping
                        .filter {
                            whisperEnd >= it.start &&
                                    whisperEnd <= it.end + 0.25f
                        }
                        .maxByOrNull { it.end }
                        ?.speaker

                    if (overlapBySpeaker.size > 1 && speakerAtEnd != null) {
                        speakerAtEnd
                    } else {
                        overlapBySpeaker.maxByOrNull { it.value }?.key ?: -1
                    }
                }

                append("Speaker $speaker\n")
                append(
                    "[${formatTime(whisperStart)} → ${formatTime(whisperEnd)}]\n"
                )
                append("${whisperSegment.text.trim()}\n\n")
            }
        }
    }

    // ======================================================
    // FORMAT TIME
    // ======================================================

    private fun formatTime(
        seconds: Float
    ): String {

        val totalSeconds =
            seconds.toInt()

        val minutes =
            totalSeconds / 60

        val remainingSeconds =
            totalSeconds % 60

        return String.format(
            "%02d:%02d",
            minutes,
            remainingSeconds
        )
    }


    // ======================================================
    // SHARE LATEST RECORDING
    // ======================================================

    private fun shareLatestRecording() {
        val conversationsFolder = File(filesDir, "conversations")
        val latestRecording = conversationsFolder.walkTopDown()
            .filter { it.isFile && it.name == "audio.m4a" }
            .maxByOrNull { it.lastModified() }

        if (latestRecording == null) {
            statusMessage = "No saved recording found"
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                latestRecording
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share recording"))
        } catch (e: Exception) {
            Log.e("MainActivity", "Unable to share recording", e)
            statusMessage = "Unable to share recording"
        }
    }

    // ======================================================
    // ACTIVITY DESTROYED
    // ======================================================

    override fun onDestroy() {

        voiceActivityDetector
            .stopListening()

        recorder?.release()

        recorder = null

        stopRecordingTimer()
        processingTimerJob?.cancel()
        processingTimerJob = null

        localLLM.release()
        speakerDiarization.release()

        embeddingManager.release()

        vectorMemoryStore.close()

        lifecycleScope.launch {

            try {

                whisperASR.release()

            } catch (e: Exception) {

                e.printStackTrace()
            }
        }

        super.onDestroy()
    }
}



// ==========================================================
// AURA FIRST-LAUNCH WELCOME
// ==========================================================

@Composable
private fun AuraWelcomeScreen(
    onGetStarted: () -> Unit
) {

    val background =
        Brush.verticalGradient(
            colors = listOf(
                AuraColors.backgroundTop,
                AuraColors.backgroundMid,
                AuraColors.backgroundBottom
            )
        )

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 28.dp, vertical = 32.dp)
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(42.dp))

            Text(
                text = "AURA",
                color = AuraColors.textPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "PRIVATE INTELLIGENCE",
                color = AuraColors.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp
            )

            Spacer(Modifier.height(58.dp))

            Box(
                modifier = Modifier
                    .size(148.dp)
                    .clip(RoundedCornerShape(74.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(
                                AuraColors.primary.copy(alpha = 0.28f),
                                AuraColors.lavender.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✦",
                    color = AuraColors.primary,
                    fontSize = 52.sp
                )
            }

            Spacer(Modifier.height(42.dp))

            Text(
                text = "Your private AI companion",
                color = AuraColors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Listen. Understand. Remember.",
                color = AuraColors.textSecondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            AuraPremiumCard(
                borderColor = AuraColors.softBorder
            ) {
                Column {
                    Text(
                        text = "PRIVACY FIRST",
                        color = AuraColors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = "Aura is designed to process your conversations locally on your device, keeping your personal information private.",
                        color = AuraColors.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onGetStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuraColors.primaryButton,
                    contentColor = AuraColors.textPrimary
                )
            ) {
                Text(
                    text = "GET STARTED",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "PRIVATE  •  ON-DEVICE  •  AURA",
                color = AuraColors.textMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
        }
    }
}


// ==========================================================
// AURA LOCAL PROFILE SETUP
// ==========================================================

@Composable
fun AuraProfileSetupScreen(
    onProfileCreated: (String, String) -> Unit
) {

    var name by androidx.compose.runtime.remember {
        mutableStateOf("")
    }

    var email by androidx.compose.runtime.remember {
        mutableStateOf("")
    }

    var errorMessage by androidx.compose.runtime.remember {
        mutableStateOf("")
    }

    val background =
        Brush.verticalGradient(
            colors = listOf(
                AuraColors.backgroundTop,
                AuraColors.backgroundMid,
                AuraColors.backgroundBottom
            )
        )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 28.dp, vertical = 32.dp)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(36.dp))

            Text(
                text = "AURA",
                color = AuraColors.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "CREATE YOUR PROFILE",
                color = AuraColors.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )

            Spacer(Modifier.height(38.dp))

            Text(
                text = "Let's personalize Aura",
                color = AuraColors.textPrimary,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Your profile is stored securely on this device.",
                color = AuraColors.textSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = name,
                textStyle = LocalTextStyle.current.copy(
                    color = AuraColors.textPrimary,
                    fontFamily = AuraFont,
                    fontSize = 14.sp
                ),
                onValueChange = {
                    name = it
                    errorMessage = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                placeholder = { Text("How should Aura call you?") },
                singleLine = true,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AuraColors.textPrimary,
                    unfocusedTextColor = AuraColors.textPrimary,
                    cursorColor = AuraColors.primary,
                    focusedBorderColor = AuraColors.primary,
                    unfocusedBorderColor = AuraColors.softBorder,
                    focusedLabelColor = AuraColors.primary,
                    unfocusedLabelColor = AuraColors.textSecondary
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = email,
                textStyle = LocalTextStyle.current.copy(
                    color = AuraColors.textPrimary,
                    fontFamily = AuraFont,
                    fontSize = 14.sp
                ),
                onValueChange = {
                    email = it
                    errorMessage = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email (Optional)") },
                placeholder = { Text("you@example.com") },
                singleLine = true,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AuraColors.textPrimary,
                    unfocusedTextColor = AuraColors.textPrimary,
                    cursorColor = AuraColors.primary,
                    focusedBorderColor = AuraColors.primary,
                    unfocusedBorderColor = AuraColors.softBorder,
                    focusedLabelColor = AuraColors.primary,
                    unfocusedLabelColor = AuraColors.textSecondary
                ),
                shape = RoundedCornerShape(16.dp)
            )

            if (errorMessage.isNotBlank()) {
                Spacer(Modifier.height(12.dp))

                Text(
                    text = errorMessage,
                    color = Color(0xFFFF8585),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(28.dp))

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val cleanName = name.trim()
                    val cleanEmail = email.trim()

                    when {
                        cleanName.isBlank() -> {
                            errorMessage = "Please enter your name."
                        }
                        cleanEmail.isNotBlank() &&
                                !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() -> {
                            errorMessage = "Please enter a valid email address or leave it blank."
                        }
                        else -> {
                            onProfileCreated(cleanName, cleanEmail)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuraColors.primaryButton,
                    contentColor = AuraColors.textPrimary
                )
            ) {
                Text(
                    text = "CONTINUE",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}


// ==========================================================
// AURA HARDWARE CHECK
// ==========================================================

@Composable
private fun AuraHardwareCheckScreen(
    onContinue: () -> Unit
) {

    var checking by androidx.compose.runtime.remember {
        mutableStateOf(true)
    }

    var checkAttempt by androidx.compose.runtime.remember {
        mutableStateOf(0)
    }

    androidx.compose.runtime.LaunchedEffect(checkAttempt) {
        checking = true
        delay(1800)
        checking = false
    }

    val background =
        Brush.verticalGradient(
            colors = listOf(
                AuraColors.backgroundTop,
                AuraColors.backgroundMid,
                AuraColors.backgroundBottom
            )
        )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 28.dp, vertical = 32.dp)
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(42.dp))

            Text(
                text = "AURA",
                color = AuraColors.textPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = if (checking) "CHECKING DEVICE" else "DEVICE NOT FOUND",
                color = AuraColors.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )

            Spacer(Modifier.height(70.dp))

            Text(
                text = if (checking) "Looking for your Aura device..." else "Your Aura device isn't connected.",
                color = AuraColors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(44.dp))
                    .background(AuraColors.card)
                    .border(1.dp, AuraColors.cardBorder, RoundedCornerShape(44.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (checking) "◉" else "○",
                    color = if (checking) AuraColors.primary else AuraColors.textMuted,
                    fontSize = 34.sp
                )
            }

            Spacer(Modifier.weight(1f))

            if (!checking) {
                Button(
                    onClick = {
                        checkAttempt++
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuraColors.primaryButton,
                        contentColor = AuraColors.textPrimary
                    )
                ) {
                    Text(
                        text = "TRY AGAIN",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }

                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuraColors.secondaryButton,
                    contentColor = AuraColors.secondaryButtonText
                )
            ) {
                Text(
                    text = if (checking) "SKIP FOR NOW" else "CONTINUE WITHOUT DEVICE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}


// ==========================================================
// AURA NOTIFICATIONS SETUP
// ==========================================================

@Composable
private fun AuraNotificationsSetupScreen(
    onAllow: () -> Unit,
    onNotNow: () -> Unit
) {

    val background =
        Brush.verticalGradient(
            colors = listOf(
                AuraColors.backgroundTop,
                AuraColors.backgroundMid,
                AuraColors.backgroundBottom
            )
        )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 28.dp, vertical = 32.dp)
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(42.dp))

            Text(
                text = "AURA",
                color = AuraColors.textPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "NOTIFICATIONS",
                color = AuraColors.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )

            Spacer(Modifier.height(76.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(AuraColors.card)
                    .border(1.dp, AuraColors.cardBorder, RoundedCornerShape(20.dp))
                    .padding(24.dp)
            ) {
                Text(
                    text = "Reminders",
                    color = AuraColors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Events",
                    color = AuraColors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Important moments",
                    color = AuraColors.textSecondary,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onAllow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuraColors.primaryButton,
                    contentColor = AuraColors.textPrimary
                )
            ) {
                Text(
                    text = "ALLOW",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onNotNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuraColors.secondaryButton,
                    contentColor = AuraColors.secondaryButtonText
                )
            ) {
                Text(
                    text = "NOT NOW",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}


// ==========================================================
// AURA RECORDING SCREEN — PREMIUM AURA CLOUD UI
// ==========================================================

private fun formatAuraDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) + 500L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
fun RecordingScreen(
    profileName: String,
    isRecording: Boolean,
    isPaused: Boolean,
    statusMessage: String,
    recordingElapsedMs: Long,
    processingStage: String,
    processingElapsedMs: Long,
    processingStageDurations: Map<String, Long>,
    vadStatus: String,
    transcriptText: String,
    summaryText: String,
    insightText: String,
    actionItemsText: String,
    eventsText: String,
    onManualRecord: () -> Unit,
    onManualStop: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStartVAD: () -> Unit,
    onStopVAD: () -> Unit,
    onConvertAudio: () -> Unit,
    onShareAudio: () -> Unit,
    onTestLLM: () -> Unit,
    onGenerateSummary: () -> Unit,
    onGenerateInsights: () -> Unit,
    onGenerateActionItemsAndEvents: () -> Unit,
    onOpenAuraHub: () -> Unit
) {

    val transition =
        rememberInfiniteTransition(
            label = "aura_cloud"
        )

    val cloudShift by transition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec =
        infiniteRepeatable(
            animation =
            tween(4200),
            repeatMode =
            RepeatMode.Reverse
        ),
        label = "cloud_shift"
    )

    val cloudScale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec =
        infiniteRepeatable(
            animation =
            tween(
                if (isRecording)
                    1200
                else
                    2600
            ),
            repeatMode =
            RepeatMode.Reverse
        ),
        label = "cloud_scale"
    )

    val glow by transition.animateFloat(
        initialValue = 0.18f,
        targetValue =
        if (isRecording)
            0.52f
        else
            0.34f,
        animationSpec =
        infiniteRepeatable(
            animation =
            tween(
                if (isRecording)
                    900
                else
                    2200
            ),
            repeatMode =
            RepeatMode.Reverse
        ),
        label = "cloud_glow"
    )

    val background =
        Brush.verticalGradient(
            colors =
            listOf(
                AuraColors.backgroundTop,
                AuraColors.backgroundMid,
                AuraColors.backgroundGlow,
                AuraColors.backgroundBottom
            )
        )

    val primary = AuraColors.primary
    val lavender = AuraColors.lavender
    val softWhite = AuraColors.textPrimary
    val secondary = AuraColors.textSecondary
    val panelBorder = AuraColors.softBorder


    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .background(background)
    ) {

        // --------------------------------------------------
        // FULL-SCREEN AURA CLOUD ATMOSPHERE
        // --------------------------------------------------

        Canvas(
            modifier =
            Modifier.fillMaxSize()
        ) {

            val cx =
                size.width / 2f

            val cy =
                size.height * 0.29f

            val s =
                cloudScale


            // Soft atmospheric glow

            drawCircle(
                brush =
                Brush.radialGradient(
                    colors =
                    listOf(
                        primary.copy(
                            alpha = glow
                        ),
                        lavender.copy(
                            alpha =
                            glow * 0.55f
                        ),
                        Color.Transparent
                    ),
                    center =
                    Offset(
                        cx,
                        cy
                    ),
                    radius =
                    size.minDimension *
                            0.42f
                ),
                radius =
                size.minDimension *
                        0.42f,
                center =
                Offset(
                    cx,
                    cy
                )
            )


            // Flowing translucent cloud layers

            for (i in 0..5) {

                val angle =
                    i * 1.05f

                val x =
                    cx +
                            cos(
                                angle +
                                        cloudShift *
                                        0.012f
                            ) *
                            size.minDimension *
                            (0.10f +
                                    i *
                                    0.018f)

                val y =
                    cy +
                            sin(
                                angle +
                                        cloudShift *
                                        0.009f
                            ) *
                            size.minDimension *
                            (0.055f +
                                    i *
                                    0.012f)

                val radius =
                    size.minDimension *
                            (0.115f +
                                    i *
                                    0.012f) *
                            s

                drawCircle(
                    brush =
                    Brush.radialGradient(
                        colors =
                        listOf(
                            if (i % 2 == 0)
                                primary.copy(
                                    alpha = 0.20f
                                )
                            else
                                lavender.copy(
                                    alpha = 0.18f
                                ),
                            Color.Transparent
                        ),
                        center =
                        Offset(
                            x,
                            y
                        ),
                        radius =
                        radius
                    ),
                    radius =
                    radius,
                    center =
                    Offset(
                        x,
                        y
                    )
                )
            }


            // Fine memory threads

            for (i in 0..7) {

                val startX =
                    cx -
                            size.minDimension *
                            0.20f +
                            i *
                            size.minDimension *
                            0.055f

                val wave =
                    sin(
                        cloudShift *
                                0.035f +
                                i
                    ) *
                            size.minDimension *
                            0.035f

                drawLine(
                    color =
                    if (i % 2 == 0)
                        primary.copy(
                            alpha = 0.20f
                        )
                    else
                        lavender.copy(
                            alpha = 0.16f
                        ),
                    start =
                    Offset(
                        startX,
                        cy -
                                size.minDimension *
                                0.10f +
                                wave
                    ),
                    end =
                    Offset(
                        startX +
                                size.minDimension *
                                0.08f,
                        cy +
                                size.minDimension *
                                0.12f -
                                wave
                    ),
                    strokeWidth =
                    1.5f
                )
            }


            // Small floating memory droplets

            for (i in 0..10) {

                val phase =
                    cloudShift *
                            0.018f +
                            i *
                            0.72f

                val x =
                    cx +
                            sin(
                                phase
                            ) *
                            size.minDimension *
                            (
                                    0.15f +
                                            (i % 3) *
                                            0.035f
                                    )

                val y =
                    cy +
                            cos(
                                phase * 0.8f
                            ) *
                            size.minDimension *
                            (
                                    0.14f +
                                            (i % 4) *
                                            0.025f
                                    )

                drawCircle(
                    color =
                    if (i % 2 == 0)
                        primary.copy(
                            alpha = 0.42f
                        )
                    else
                        lavender.copy(
                            alpha = 0.34f
                        ),
                    radius =
                    2.5f +
                            (i % 3),
                    center =
                    Offset(
                        x,
                        y
                    )
                )
            }


            // Listening ripple rings

            if (isRecording) {

                for (i in 1..3) {

                    drawCircle(
                        color =
                        primary.copy(
                            alpha =
                            0.12f / i
                        ),
                        radius =
                        size.minDimension *
                                (
                                        0.17f +
                                                i *
                                                0.055f
                                        ) *
                                s,
                        center =
                        Offset(
                            cx,
                            cy
                        ),
                        style =
                        Stroke(
                            width = 2f
                        )
                    )
                }
            }
        }


        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = 20.dp,
                    vertical = 18.dp
                ),
            horizontalAlignment =
            Alignment.CenterHorizontally
        ) {


            // ------------------------------------------------
            // HEADER
            // ------------------------------------------------

            Row(
                modifier =
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                Arrangement.SpaceBetween,
                verticalAlignment =
                Alignment.CenterVertically
            ) {

                Column {

                    Text(
                        text =
                        "AURA",
                        color =
                        softWhite,
                        fontSize =
                        28.sp,
                        fontWeight =
                        FontWeight.Bold
                    )

                    Text(
                        text =
                        "Memory • Intelligence • Privacy",
                        color =
                        secondary,
                        fontSize =
                        11.sp
                    )
                }


                Box(
                    modifier =
                    Modifier
                        .clip(
                            RoundedCornerShape(
                                50.dp
                            )
                        )
                        .background(
                            Color(0x331D3557)
                        )
                        .border(
                            1.dp,
                            Color(0x556C8BB8),
                            RoundedCornerShape(
                                50.dp
                            )
                        )
                        .padding(
                            horizontal = 13.dp,
                            vertical = 7.dp
                        )
                ) {

                    Text(
                        text =
                        "ON-DEVICE AI",
                        color =
                        primary,
                        fontSize =
                        9.sp,
                        fontWeight =
                        FontWeight.Bold
                    )
                }
            }


            Spacer(Modifier.height(12.dp))

            AuraSecondaryButton(
                text = "OPEN AURA PLAN • CALENDAR • MEMORY",
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenAuraHub
            )

            Spacer(
                Modifier.height(18.dp)
            )


            // ------------------------------------------------
            // GREETING
            // ------------------------------------------------

            Column(
                modifier =
                Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                    "Good evening, ${if (profileName.isBlank()) "there" else profileName} 👋",
                    color =
                    softWhite,
                    fontSize =
                    24.sp,
                    fontWeight =
                    FontWeight.SemiBold
                )

                Spacer(
                    Modifier.height(5.dp)
                )

                Text(
                    text =
                    "Turn fleeting conversations into intelligent memory.",
                    color =
                    secondary,
                    fontSize =
                    13.sp
                )
            }


            Spacer(
                Modifier.height(90.dp)
            )


            // ------------------------------------------------
            // AURA CORE LABEL
            // ------------------------------------------------

            Text(
                text =
                when {

                    isRecording ->
                        "AURA IS LISTENING"

                    statusMessage.contains(
                        "Whisper",
                        ignoreCase = true
                    ) ->
                        "MEMORY RECONSTRUCTED"

                    statusMessage.contains(
                        "Converting",
                        ignoreCase = true
                    ) ->
                        "UNDERSTANDING AUDIO"

                    else ->
                        "AURA CLOUD"
                },
                color =
                primary,
                fontSize =
                10.sp,
                fontWeight =
                FontWeight.Bold,
                letterSpacing =
                1.7.sp
            )


            Spacer(
                Modifier.height(5.dp)
            )


            Text(
                text =
                when {

                    isRecording ->
                        "Your voice is becoming structured memory"

                    statusMessage.contains(
                        "failed",
                        ignoreCase = true
                    ) ->
                        "Aura needs another attempt"

                    statusMessage.contains(
                        "Whisper",
                        ignoreCase = true
                    ) ->
                        "Your conversation is ready to explore"

                    else ->
                        "A calm space for your conversations"
                },
                color =
                softWhite,
                fontSize =
                14.sp,
                textAlign =
                TextAlign.Center
            )


            Spacer(
                Modifier.height(18.dp)
            )


            // ------------------------------------------------
            // RECORD BUTTON
            // ------------------------------------------------

            Button(
                onClick = {

                    if (isRecording) {

                        onManualStop()

                    } else {

                        onManualRecord()
                    }
                },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .shadow(
                        18.dp,
                        RoundedCornerShape(
                            22.dp
                        )
                    ),
                shape =
                RoundedCornerShape(
                    22.dp
                ),
                colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                    if (isRecording)
                        AuraColors.primaryButtonPressed
                    else
                        AuraColors.primaryButton,
                    contentColor =
                    Color.White
                )
            ) {

                Text(
                    text = when {
                        !isRecording -> "START LISTENING"
                        isPaused -> "RESUME LISTENING"
                        else -> "PAUSE LISTENING"
                    },
                    fontSize =
                    14.sp,
                    fontWeight =
                    FontWeight.Bold
                )
            }

            if (isRecording) {
                Spacer(Modifier.height(8.dp))
                AuraSecondaryButton(
                    text = "STOP & SAVE CONVERSATION",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onManualStop
                )
            }

            // ------------------------------------------------
            // LIVE RECORDING TIMER
            // ------------------------------------------------

            if (isRecording || recordingElapsedMs > 0L) {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isRecording) "RECORDING TIME" else "RECORDING DURATION",
                        color = AuraColors.textMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatAuraDuration(recordingElapsedMs),
                        color = if (isRecording) primary else softWhite,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(
                Modifier.height(12.dp)
            )


            // ------------------------------------------------
            // SESSION STATUS
            // ------------------------------------------------

            AuraPremiumCard(
                borderColor =
                if (isRecording)
                    primary.copy(
                        alpha = 0.42f
                    )
                else
                    panelBorder
            ) {

                Column {

                    Text(
                        text =
                        "SESSION STATUS",
                        color =
                        primary,
                        fontSize =
                        9.sp,
                        fontWeight =
                        FontWeight.Bold,
                        letterSpacing =
                        1.5.sp
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        text =
                        statusMessage,
                        color =
                        softWhite,
                        fontSize =
                        15.sp,
                        fontWeight =
                        FontWeight.Medium
                    )

                    Spacer(
                        Modifier.height(5.dp)
                    )

                    Text(
                        text =
                        vadStatus,
                        color =
                        secondary,
                        fontSize =
                        12.sp
                    )
                }
            }

            // ------------------------------------------------
            // LIVE PROCESSING TIMERS
            // ------------------------------------------------

            if (processingStage.isNotBlank() || processingStageDurations.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                AuraPremiumCard(
                    borderColor = if (processingStage.isNotBlank()) primary.copy(alpha = 0.35f) else panelBorder
                ) {
                    Column {
                        Text(
                            text = "PROCESSING TIME",
                            color = primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(8.dp))

                        if (processingStage.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "●  $processingStage",
                                    color = softWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = formatAuraDuration(processingElapsedMs),
                                    color = primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        processingStageDurations.forEach { (stage, duration) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "✓  $stage",
                                    color = secondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = formatAuraDuration(duration),
                                    color = softWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (processingStageDurations.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Timings are saved with this conversation.",
                                color = AuraColors.textMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }


            Spacer(
                Modifier.height(12.dp)
            )


            // ------------------------------------------------
            // ACTIONS
            // ------------------------------------------------

            Row(
                modifier =
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                Arrangement.spacedBy(10.dp)
            ) {

                AuraActionButton(
                    text =
                    "✦  Transcribe",
                    modifier =
                    Modifier.weight(1f),
                    active =
                    transcriptText.isBlank(),
                    onClick =
                    onConvertAudio
                )

                AuraActionButton(
                    text =
                    "↗  Share",
                    modifier =
                    Modifier.weight(1f),
                    active =
                    false,
                    onClick =
                    onShareAudio
                )
            }


            Spacer(
                Modifier.height(10.dp)
            )


            Row(
                modifier =
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                Arrangement.spacedBy(10.dp)
            ) {

                AuraSecondaryButton(
                    text =
                    "Auto Listen",
                    modifier =
                    Modifier.weight(1f),
                    onClick =
                    onStartVAD
                )

                AuraSecondaryButton(
                    text =
                    "Stop Auto",
                    modifier =
                    Modifier.weight(1f),
                    onClick =
                    onStopVAD
                )
            }


            Spacer(
                Modifier.height(10.dp)
            )


            AuraSecondaryButton(
                text =
                "Test Local AI",
                modifier =
                Modifier.fillMaxWidth(),
                onClick =
                onTestLLM
            )


            // ------------------------------------------------
            // TRANSCRIPT
            // ------------------------------------------------

            if (transcriptText.isNotBlank()) {

                Spacer(
                    Modifier.height(24.dp)
                )


                Row(
                    modifier =
                    Modifier.fillMaxWidth(),
                    verticalAlignment =
                    Alignment.CenterVertically
                ) {

                    Text(
                        text =
                        "TRANSCRIPT",
                        color =
                        softWhite,
                        fontSize =
                        19.sp,
                        fontWeight =
                        FontWeight.Bold
                    )

                    Spacer(
                        Modifier.width(9.dp)
                    )

                    Box(
                        modifier =
                        Modifier
                            .clip(
                                RoundedCornerShape(
                                    50.dp
                                )
                            )
                            .background(
                                Color(0x3329D3EE)
                            )
                            .padding(
                                horizontal = 9.dp,
                                vertical = 5.dp
                            )
                    ) {

                        Text(
                            text =
                            "WHISPER",
                            color =
                            primary,
                            fontSize =
                            8.sp,
                            fontWeight =
                            FontWeight.Bold
                        )
                    }
                }


                Spacer(
                    Modifier.height(10.dp)
                )


                AuraPremiumCard(
                    borderColor =
                    AuraColors.cardBorder
                ) {

                    Column {

                        Text(
                            text =
                            "MEMORY SURFACED",
                            color =
                            lavender,
                            fontSize =
                            9.sp,
                            fontWeight =
                            FontWeight.Bold,
                            letterSpacing =
                            1.2.sp
                        )

                        Spacer(
                            Modifier.height(10.dp)
                        )

                        Text(
                            text =
                            transcriptText,
                            color =
                            AuraColors.transcriptText,
                            fontSize =
                            14.sp,
                            lineHeight =
                            22.sp
                        )
                    }
                }
            }


            Spacer(
                Modifier.height(24.dp)
            )


            // ------------------------------------------------
            // INTELLIGENCE MODULES
            // ------------------------------------------------

            // ------------------------------------------------
            // LOCAL AI SUMMARY
            // ------------------------------------------------

            if (transcriptText.isNotBlank()) {
                Spacer(Modifier.height(14.dp))

                AuraSecondaryButton(
                    text = "Generate Local AI Summary",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onGenerateSummary
                )

                if (summaryText.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AuraColors.card)
                            .border(
                                1.dp,
                                Color(0xFF263550),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "LOCAL AI SUMMARY",
                            color = AuraColors.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = summaryText,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // ------------------------------------------------
            // LOCAL AI INSIGHTS
            // ------------------------------------------------

            if (transcriptText.isNotBlank()) {
                Spacer(Modifier.height(14.dp))

                AuraSecondaryButton(
                    text = "Generate Local AI Insights",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onGenerateInsights
                )

                if (insightText.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AuraColors.card)
                            .border(
                                1.dp,
                                Color(0xFF263550),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "LOCAL AI INSIGHTS",
                            color = AuraColors.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = insightText,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // ------------------------------------------------
            // ACTION ITEMS + EVENTS
            // ------------------------------------------------

            if (transcriptText.isNotBlank()) {
                Spacer(Modifier.height(14.dp))

                AuraSecondaryButton(
                    text = "Extract Action Items & Events",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onGenerateActionItemsAndEvents
                )

                if (actionItemsText.isNotBlank() || eventsText.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AuraColors.card)
                            .border(1.dp, Color(0xFF263550), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text("ACTION ITEMS", color = AuraColors.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(actionItemsText, color = AuraColors.textPrimary, fontSize = 14.sp, lineHeight = 21.sp)
                        Spacer(Modifier.height(18.dp))
                        Text("EVENTS", color = AuraColors.lavender, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(eventsText, color = AuraColors.textPrimary, fontSize = 14.sp, lineHeight = 21.sp)
                    }
                }
            }

            // =================================================
            // AURA INTELLIGENCE
            // =================================================

            Text(
                text =
                "AURA INTELLIGENCE",
                modifier =
                Modifier.fillMaxWidth(),
                color =
                AuraColors.textMuted,
                fontSize =
                10.sp,
                fontWeight =
                FontWeight.Bold,
                letterSpacing =
                1.5.sp
            )


            Spacer(
                Modifier.height(9.dp)
            )


            Row(
                modifier =
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                Arrangement.spacedBy(9.dp)
            ) {

                AuraModuleCard(
                    title =
                    "Actions",
                    subtitle =
                    if (actionItemsText.isNotBlank()) "Ready" else "Extract",
                    modifier =
                    Modifier.weight(1f)
                )

                AuraModuleCard(
                    title =
                    "Events",
                    subtitle =
                    if (eventsText.isNotBlank()) "Ready" else "Extract",
                    modifier =
                    Modifier.weight(1f)
                )

                AuraModuleCard(
                    title =
                    "Insights",
                    subtitle =
                    if (insightText.isNotBlank()) "Ready" else "Local AI",
                    modifier =
                    Modifier.weight(1f)
                )
            }


            Spacer(
                Modifier.height(25.dp)
            )


            Text(
                text =
                "PRIVATE  •  ON-DEVICE  •  AURA",
                modifier =
                Modifier.fillMaxWidth(),
                color =
                Color(0xFF53627D),
                fontSize =
                9.sp,
                fontWeight =
                FontWeight.Bold,
                letterSpacing =
                1.4.sp,
                textAlign =
                TextAlign.Center
            )


            Spacer(
                Modifier.height(15.dp)
            )
        }
    }
}


// ==========================================================
// AURA LOCAL MEMORY + PLANNER MODELS
// ==========================================================

data class AuraActionItem(
    val id: Long,
    val title: String,
    val person: String = "",
    val dueLabel: String = "",
    val dueDateEpochDay: Long? = null,
    val completed: Boolean = false
)

data class AuraEventItem(
    val id: Long,
    val title: String,
    val dateLabel: String = "",
    val dateEpochDay: Long? = null,
    val timeLabel: String = "",
    val important: Boolean = false
)

data class AuraMemoryItem(
    val id: Long,
    val createdAt: Long,
    val title: String,
    val summary: String,
    val transcript: String,
    val speakerTranscript: String = "",
    val insights: String = "",
    val actionsText: String = "",
    val eventsText: String = "",
    val audioPath: String = "",
    val sessionId: String = ""
)

data class AuraImportantDate(
    val id: Long,
    val sessionId: String,
    val dateEpochDay: Long,
    val dateLabel: String,
    val reason: String
)

private object AuraDateParser {
    fun parseIsoOrLabel(iso: String, label: String, today: LocalDate): Long? {
        if (iso.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            return runCatching { LocalDate.parse(iso).toEpochDay() }.getOrNull()
        }
        val lower = label.lowercase(Locale.getDefault()).trim()
        if (lower.isBlank()) return null
        if (lower.contains("today")) return today.toEpochDay()
        if (lower.contains("tomorrow")) return today.plusDays(1).toEpochDay()
        val weekdays = mapOf(
            "monday" to DayOfWeek.MONDAY,
            "tuesday" to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY,
            "thursday" to DayOfWeek.THURSDAY,
            "friday" to DayOfWeek.FRIDAY,
            "saturday" to DayOfWeek.SATURDAY,
            "sunday" to DayOfWeek.SUNDAY
        )
        weekdays.entries.firstOrNull { lower.contains(it.key) }?.let { entry ->
            var d = today.plusDays(1)
            while (d.dayOfWeek != entry.value) d = d.plusDays(1)
            return d.toEpochDay()
        }
        return null
    }
}

private class AuraStore(private val context: android.content.Context) {
    private val prefs = context.getSharedPreferences("aura_memory_store", android.content.Context.MODE_PRIVATE)

    fun getActions(): List<AuraActionItem> = runCatching {
        val array = org.json.JSONArray(prefs.getString("actions", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            AuraActionItem(
                id = o.optLong("id"),
                title = o.optString("title"),
                person = o.optString("person"),
                dueLabel = o.optString("dueLabel"),
                dueDateEpochDay = if (o.has("dueDateEpochDay") && !o.isNull("dueDateEpochDay")) o.optLong("dueDateEpochDay") else null,
                completed = o.optBoolean("completed", false)
            )
        }
    }.getOrDefault(emptyList())

    fun getEvents(): List<AuraEventItem> = runCatching {
        val array = org.json.JSONArray(prefs.getString("events", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            AuraEventItem(
                id = o.optLong("id"),
                title = o.optString("title"),
                dateLabel = o.optString("dateLabel"),
                dateEpochDay = if (o.has("dateEpochDay") && !o.isNull("dateEpochDay")) o.optLong("dateEpochDay") else null,
                timeLabel = o.optString("timeLabel"),
                important = o.optBoolean("important", false)
            )
        }
    }.getOrDefault(emptyList())

    fun getMemories(): List<AuraMemoryItem> = runCatching {
        val array = org.json.JSONArray(prefs.getString("memories", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            AuraMemoryItem(
                id = o.optLong("id"),
                createdAt = o.optLong("createdAt"),
                title = o.optString("title"),
                summary = o.optString("summary"),
                transcript = o.optString("transcript"),
                speakerTranscript = o.optString("speakerTranscript"),
                insights = o.optString("insights"),
                actionsText = o.optString("actionsText"),
                eventsText = o.optString("eventsText"),
                audioPath = o.optString("audioPath"),
                sessionId = o.optString("sessionId")
            )
        }
    }.getOrDefault(emptyList())

    fun replaceSessionItems(
        actions: List<AuraActionItem>,
        events: List<AuraEventItem>,
        transcript: String,
        summary: String,
        insights: String = "",
        actionsText: String = "",
        eventsText: String = "",
        audioPath: String = "",
        sessionId: String = "",
        speakerTranscript: String = ""
    ) {
        val existingActions = getActions()
        val existingEvents = getEvents()
        val mergedActions = (existingActions + actions).distinctBy {
            "${it.title.lowercase(Locale.getDefault())}|${it.person.lowercase(Locale.getDefault())}|${it.dueLabel.lowercase(Locale.getDefault())}"
        }
        val mergedEvents = (existingEvents + events).distinctBy {
            "${it.title.lowercase(Locale.getDefault())}|${it.dateLabel.lowercase(Locale.getDefault())}|${it.timeLabel.lowercase(Locale.getDefault())}"
        }
        saveActions(mergedActions.takeLast(100))
        saveEvents(mergedEvents.takeLast(100))
        if (transcript.isNotBlank()) {
            val now = System.currentTimeMillis()
            val existing = getMemories().firstOrNull {
                (sessionId.isNotBlank() && it.sessionId == sessionId) ||
                        (sessionId.isBlank() && it.transcript == transcript)
            }
            val memory = AuraMemoryItem(
                id = existing?.id ?: now,
                createdAt = existing?.createdAt ?: now,
                title = existing?.title ?: events.firstOrNull()?.title ?: actions.firstOrNull()?.title ?: "Conversation memory",
                summary = summary.ifBlank { existing?.summary ?: "Conversation captured by Aura." },
                transcript = transcript,
                speakerTranscript = speakerTranscript.ifBlank { existing?.speakerTranscript ?: transcript },
                insights = insights.ifBlank { existing?.insights ?: "" },
                actionsText = actionsText.ifBlank { existing?.actionsText ?: "" },
                eventsText = eventsText.ifBlank { existing?.eventsText ?: "" },
                audioPath = audioPath.ifBlank { existing?.audioPath ?: "" },
                sessionId = sessionId.ifBlank { existing?.sessionId ?: "" }
            )
            val memories = getMemories().filter { it.id != memory.id }.takeLast(49)
            saveMemories(memories + memory)
            saveConversationFile(memory)
        }
    }

    private fun saveActions(items: List<AuraActionItem>) {
        val array = org.json.JSONArray()
        items.forEach { item ->
            array.put(org.json.JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("person", item.person)
                put("dueLabel", item.dueLabel)
                if (item.dueDateEpochDay == null) put("dueDateEpochDay", org.json.JSONObject.NULL) else put("dueDateEpochDay", item.dueDateEpochDay)
                put("completed", item.completed)
            })
        }
        prefs.edit().putString("actions", array.toString()).apply()
    }

    private fun saveEvents(items: List<AuraEventItem>) {
        val array = org.json.JSONArray()
        items.forEach { item ->
            array.put(org.json.JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("dateLabel", item.dateLabel)
                if (item.dateEpochDay == null) put("dateEpochDay", org.json.JSONObject.NULL) else put("dateEpochDay", item.dateEpochDay)
                put("timeLabel", item.timeLabel)
                put("important", item.important)
            })
        }
        prefs.edit().putString("events", array.toString()).apply()
    }

    fun getImportantDates(): List<AuraImportantDate> = runCatching {
        val array = org.json.JSONArray(prefs.getString("important_dates", "[]"))
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val session = o.optString("sessionId")
            val date = if (o.has("dateEpochDay") && !o.isNull("dateEpochDay")) o.optLong("dateEpochDay") else return@mapNotNull null
            AuraImportantDate(
                id = o.optLong("id"),
                sessionId = session,
                dateEpochDay = date,
                dateLabel = o.optString("dateLabel"),
                reason = o.optString("reason")
            )
        }
    }.getOrDefault(emptyList())

    fun replaceSessionImportantDates(sessionId: String, items: List<AuraImportantDate>) {
        if (sessionId.isBlank()) return
        val existing = getImportantDates().filter { it.sessionId != sessionId }
        val merged = (existing + items)
            .distinctBy { "${it.sessionId}|${it.dateEpochDay}|${it.reason.lowercase(Locale.getDefault())}" }
            .takeLast(200)
        saveImportantDates(merged)
        runCatching {
            val folder = File(context.filesDir, "conversations/$sessionId")
            if (!folder.exists()) folder.mkdirs()
            val array = org.json.JSONArray()
            items.forEach { item ->
                array.put(org.json.JSONObject().apply {
                    put("id", item.id)
                    put("sessionId", item.sessionId)
                    put("dateEpochDay", item.dateEpochDay)
                    put("dateLabel", item.dateLabel)
                    put("reason", item.reason)
                })
            }
            File(folder, "important_dates.json").writeText(array.toString(2))
        }.onFailure { Log.e("AuraStore", "Failed to save important dates file", it) }
    }

    private fun saveImportantDates(items: List<AuraImportantDate>) {
        val array = org.json.JSONArray()
        items.forEach { item ->
            array.put(org.json.JSONObject().apply {
                put("id", item.id)
                put("sessionId", item.sessionId)
                put("dateEpochDay", item.dateEpochDay)
                put("dateLabel", item.dateLabel)
                put("reason", item.reason)
            })
        }
        prefs.edit().putString("important_dates", array.toString()).apply()
    }

    private fun saveMemories(items: List<AuraMemoryItem>) {
        val array = org.json.JSONArray()
        items.forEach { item ->
            array.put(org.json.JSONObject().apply {
                put("id", item.id)
                put("createdAt", item.createdAt)
                put("title", item.title)
                put("summary", item.summary)
                put("transcript", item.transcript)
                put("speakerTranscript", item.speakerTranscript)
                put("insights", item.insights)
                put("actionsText", item.actionsText)
                put("eventsText", item.eventsText)
                put("audioPath", item.audioPath)
                put("sessionId", item.sessionId)
            })
        }
        prefs.edit().putString("memories", array.toString()).apply()
    }

    private fun saveConversationFile(memory: AuraMemoryItem) {
        if (memory.sessionId.isBlank()) return
        runCatching {
            val folder = File(context.filesDir, "conversations/${memory.sessionId}")
            if (!folder.exists()) folder.mkdirs()
            val json = org.json.JSONObject().apply {
                put("id", memory.id)
                put("sessionId", memory.sessionId)
                put("createdAt", memory.createdAt)
                put("title", memory.title)
                put("audioPath", memory.audioPath)
                put("transcript", memory.transcript)
                put("speakerTranscript", memory.speakerTranscript)
                put("summary", memory.summary)
                put("insights", memory.insights)
                put("actions", memory.actionsText)
                put("events", memory.eventsText)
            }
            File(folder, "conversation.json").writeText(json.toString(2))
        }.onFailure { Log.e("AuraStore", "Failed to save conversation file", it) }
    }

    fun setActionCompleted(id: Long, completed: Boolean) {
        saveActions(getActions().map { if (it.id == id) it.copy(completed = completed) else it })
    }

    fun deleteAction(id: Long) = saveActions(getActions().filterNot { it.id == id })
    fun deleteEvent(id: Long) = saveEvents(getEvents().filterNot { it.id == id })
}

// ==========================================================
// AURA LOCAL REMINDER SCHEDULER
// ==========================================================

private object AuraReminderScheduler {
    fun scheduleAll(context: android.content.Context, actions: List<AuraActionItem>, events: List<AuraEventItem>) {
        if (!context.getSharedPreferences("aura_memory_store", android.content.Context.MODE_PRIVATE)
                .getBoolean("notifications_enabled", false)) return

        actions.forEach { action ->
            action.dueDateEpochDay?.let { day ->
                schedule(context, action.id, LocalDate.ofEpochDay(day), 9, 0, "Reminder", action.title)
            }
        }
        events.forEach { event ->
            event.dateEpochDay?.let { day ->
                val time = parseTime(event.timeLabel) ?: LocalTime.of(9, 0)
                schedule(context, event.id + 500000, LocalDate.ofEpochDay(day), time.hour, time.minute, "Event", event.title)
            }
        }
    }

    private fun parseTime(text: String): LocalTime? {
        val match = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(AM|PM)?", RegexOption.IGNORE_CASE).find(text) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val ampm = match.groupValues[3].uppercase(Locale.getDefault())
        if (ampm == "PM" && hour < 12) hour += 12
        if (ampm == "AM" && hour == 12) hour = 0
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }

    private fun schedule(context: android.content.Context, id: Long, date: LocalDate, hour: Int, minute: Int, kind: String, title: String) {
        val trigger = date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (trigger <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, AuraReminderReceiver::class.java).apply {
            putExtra("kind", kind)
            putExtra("title", title)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, trigger, pending)
    }
}

// ==========================================================
// AURA HUB — TODAY / CALENDAR / ACTIONS / MEMORY
// ==========================================================

@Composable
private fun AuraHubScreen(
    profileName: String,
    actions: List<AuraActionItem>,
    events: List<AuraEventItem>,
    memories: List<AuraMemoryItem>,
    importantDates: List<AuraImportantDate>,
    ragQuestion: String,
    ragAnswer: String,
    ragSources: String,
    ragLoading: Boolean,
    onRagQuestionChange: (String) -> Unit,
    onAskAuraMemory: () -> Unit,
    onAnalyzeSavedSummary: (AuraMemoryItem) -> Unit,
    onAnalyzeSavedInsights: (AuraMemoryItem) -> Unit,
    onAnalyzeSavedActionsEvents: (AuraMemoryItem) -> Unit,
    onAnalyzeSavedImportantDates: (AuraMemoryItem) -> Unit,
    memoryAnalysisSession: String?,
    memoryAnalysisAction: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCompleteAction: (Long) -> Unit,
    onDeleteAction: (Long) -> Unit,
    onDeleteEvent: (Long) -> Unit,
    onEnableNotifications: () -> Unit,
    onResetDemo: () -> Unit
) {
    var tab by androidx.compose.runtime.remember { mutableStateOf(0) }
    var month by androidx.compose.runtime.remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by androidx.compose.runtime.remember { mutableStateOf<LocalDate?>(null) }
    var search by androidx.compose.runtime.remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(AuraColors.backgroundTop, AuraColors.backgroundMid, AuraColors.backgroundBottom))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AURA", color = AuraColors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AuraSecondaryButton("⟳  REFRESH", Modifier.width(118.dp), onRefresh)
                    AuraSecondaryButton("BACK", Modifier.width(80.dp), onBack)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("PLAN • MEMORY • CALENDAR", color = AuraColors.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Today", "Calendar", "Actions", "Events", "Memory", "Settings").forEachIndexed { index, label ->
                    AuraTabButton(label, index == tab, Modifier.weight(1f)) { tab = index }
                }
            }
            Spacer(Modifier.height(16.dp))

            when (tab) {
                0 -> AuraTodayContent(profileName, actions, events, memories)
                1 -> AuraCalendarContent(month, { month = it }, selectedDate, { selectedDate = it }, actions, events, importantDates)
                2 -> AuraActionsContent(actions, onCompleteAction, onDeleteAction)
                3 -> AuraEventsContent(events, onDeleteEvent)
                4 -> AuraMemoryContent(
                    memories = memories,
                    search = search,
                    onSearch = { search = it },
                    ragQuestion = ragQuestion,
                    ragAnswer = ragAnswer,
                    ragSources = ragSources,
                    ragLoading = ragLoading,
                    onRagQuestionChange = onRagQuestionChange,
                    onAskAuraMemory = onAskAuraMemory,
                    onAnalyzeSavedSummary = onAnalyzeSavedSummary,
                    onAnalyzeSavedInsights = onAnalyzeSavedInsights,
                    onAnalyzeSavedActionsEvents = onAnalyzeSavedActionsEvents,
                    onAnalyzeSavedImportantDates = onAnalyzeSavedImportantDates,
                    memoryAnalysisSession = memoryAnalysisSession,
                    memoryAnalysisAction = memoryAnalysisAction
                )
                5 -> AuraSettingsContent(onEnableNotifications, onResetDemo)
            }
        }
    }
}

@Composable
private fun AuraTabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AuraColors.primaryButton else AuraColors.secondaryButton,
            contentColor = AuraColors.textPrimary
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            color = AuraColors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun AuraTodayContent(profileName: String, actions: List<AuraActionItem>, events: List<AuraEventItem>, memories: List<AuraMemoryItem>) {
    val today = LocalDate.now()
    val todayActions = actions.filter { !it.completed && it.dueDateEpochDay == today.toEpochDay() }
    val todayEvents = events.filter { it.dateEpochDay == today.toEpochDay() }
    val upcoming = (actions.filter { !it.completed && (it.dueDateEpochDay ?: Long.MAX_VALUE) >= today.toEpochDay() }.map { "${it.dueLabel.ifBlank { "Upcoming" }} · ${it.title}" } +
            events.filter { (it.dateEpochDay ?: Long.MAX_VALUE) >= today.toEpochDay() }.map { "${it.dateLabel.ifBlank { "Upcoming" }} · ${it.title}" }).take(5)

    AuraHubSectionTitle("TODAY")
    Text("Good ${if (profileName.isBlank()) "day" else "day, $profileName"}.", color = AuraColors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    AuraInfoCard("TODAY", if (todayActions.isEmpty() && todayEvents.isEmpty()) "Nothing scheduled today." else "${todayActions.size} task(s) • ${todayEvents.size} event(s)")
    Spacer(Modifier.height(14.dp))
    AuraHubSectionTitle("COMING UP")
    if (upcoming.isEmpty()) AuraInfoCard("CLEAR", "No upcoming items yet.")
    upcoming.forEach { AuraInfoCard("UP NEXT", it) }
    Spacer(Modifier.height(14.dp))
    AuraHubSectionTitle("MEMORY")
    AuraInfoCard("LOCAL", "${memories.size} conversation memor${if (memories.size == 1) "y" else "ies"} saved on this device.")
}

@Composable
private fun AuraCalendarContent(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    selectedDate: LocalDate?,
    onSelect: (LocalDate?) -> Unit,
    actions: List<AuraActionItem>,
    events: List<AuraEventItem>,
    importantDates: List<AuraImportantDate>
) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        AuraSecondaryButton("‹", Modifier.width(58.dp)) { onMonth(month.minusMonths(1)) }
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())).uppercase(),
            color = AuraColors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        AuraSecondaryButton("›", Modifier.width(58.dp)) { onMonth(month.plusMonths(1)) }
    }

    Spacer(Modifier.height(12.dp))
    val weekdays = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(Modifier.fillMaxWidth()) {
        weekdays.forEach {
            Text(
                it,
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = AuraColors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    Spacer(Modifier.height(8.dp))

    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val total = firstOffset + month.lengthOfMonth()
    val rows = (total + 6) / 7
    repeat(rows) { row ->
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { col ->
                val index = row * 7 + col
                val day = index - firstOffset + 1
                if (day in 1..month.lengthOfMonth()) {
                    val date = month.atDay(day)
                    val hasAction = actions.any { it.dueDateEpochDay == date.toEpochDay() && !it.completed }
                    val hasEvent = events.any { it.dateEpochDay == date.toEpochDay() }
                    val importantDate = importantDates.firstOrNull { it.dateEpochDay == date.toEpochDay() }
                    val important = importantDate != null
                    val selected = selectedDate == date

                    Button(
                        onClick = { onSelect(if (selected) null else date) },
                        modifier = Modifier.weight(1f).padding(2.dp).height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                selected -> AuraColors.primaryButton
                                important -> AuraColors.cardElevatedTop
                                else -> AuraColors.secondaryButton
                            },
                            contentColor = AuraColors.textPrimary
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (important) "★ $day" else day.toString(),
                                color = if (important) AuraColors.primary else AuraColors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (hasAction) Box(Modifier.size(4.dp).clip(RoundedCornerShape(50)).background(AuraColors.primary))
                                if (hasEvent) Box(Modifier.size(4.dp).clip(RoundedCornerShape(50)).background(AuraColors.lavender))
                                if (important) Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(AuraColors.primary))
                            }
                        }
                    }
                } else Spacer(Modifier.weight(1f).height(50.dp))
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    AuraHubSectionTitle("IMPORTANT DATES")
    val monthImportantDates = importantDates
        .filter {
            val date = LocalDate.ofEpochDay(it.dateEpochDay)
            date.year == month.year && date.monthValue == month.monthValue
        }
        .sortedBy { it.dateEpochDay }

    if (monthImportantDates.isEmpty()) {
        AuraInfoCard("NONE", "No important dates have been highlighted in this month yet.")
    } else {
        monthImportantDates.forEach { item ->
            AuraInfoCard(
                "★ ${item.dateLabel.ifBlank { LocalDate.ofEpochDay(item.dateEpochDay).toString() }}",
                item.reason
            )
        }
    }

    selectedDate?.let { date ->
        Spacer(Modifier.height(8.dp))
        AuraHubSectionTitle(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())).uppercase())
        val dayActions = actions.filter { it.dueDateEpochDay == date.toEpochDay() }
        val dayEvents = events.filter { it.dateEpochDay == date.toEpochDay() }
        val dayImportantDates = importantDates.filter { it.dateEpochDay == date.toEpochDay() }

        dayImportantDates.forEach {
            AuraInfoCard("IMPORTANT DATE", "${it.dateLabel.ifBlank { date.toString() }}\n${it.reason}")
        }
        dayEvents.forEach {
            AuraInfoCard(
                if (it.important) "IMPORTANT EVENT" else "EVENT",
                "${it.title}${if (it.timeLabel.isNotBlank()) " · ${it.timeLabel}" else ""}"
            )
        }
        dayActions.forEach {
            AuraInfoCard(
                "ACTION",
                "${it.title}${if (it.person.isNotBlank()) " · ${it.person}" else ""}"
            )
        }
        if (dayActions.isEmpty() && dayEvents.isEmpty() && dayImportantDates.isEmpty()) {
            AuraInfoCard("CLEAR", "No saved items for this date.")
        }
    }
}

@Composable
private fun AuraActionsContent(actions: List<AuraActionItem>, onComplete: (Long) -> Unit, onDelete: (Long) -> Unit) {
    AuraHubSectionTitle("ACTION ITEMS")
    if (actions.isEmpty()) AuraInfoCard("EMPTY", "No action items saved yet.")
    actions.forEach { item ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AuraColors.card).border(1.dp, AuraColors.cardBorder, RoundedCornerShape(16.dp)).padding(14.dp)) {
            Text(if (item.completed) "✓ ${item.title}" else "☐ ${item.title}", color = AuraColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (item.person.isNotBlank()) Text("Person: ${item.person}", color = AuraColors.textSecondary, fontSize = 11.sp)
            if (item.dueLabel.isNotBlank()) Text("Due: ${item.dueLabel}", color = AuraColors.primary, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!item.completed) AuraSecondaryButton("DONE", Modifier.width(80.dp)) { onComplete(item.id) }
                Spacer(Modifier.width(6.dp))
                AuraSecondaryButton("DELETE", Modifier.width(80.dp)) { onDelete(item.id) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AuraEventsContent(events: List<AuraEventItem>, onDelete: (Long) -> Unit) {
    AuraHubSectionTitle("EVENTS")
    if (events.isEmpty()) AuraInfoCard("EMPTY", "No events saved yet.")
    events.sortedWith(compareBy({ it.dateEpochDay ?: Long.MAX_VALUE }, { it.timeLabel })).forEach { event ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AuraColors.card).border(1.dp, AuraColors.cardBorder, RoundedCornerShape(16.dp)).padding(14.dp)) {
            Text(if (event.important) "★ ${event.title}" else event.title, color = AuraColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (event.dateLabel.isNotBlank()) Text(event.dateLabel, color = AuraColors.lavender, fontSize = 11.sp)
            if (event.timeLabel.isNotBlank()) Text(event.timeLabel, color = AuraColors.textSecondary, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AuraSecondaryButton("DELETE", Modifier.width(80.dp)) { onDelete(event.id) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AuraSettingsContent(onEnableNotifications: () -> Unit, onResetDemo: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var enabled by androidx.compose.runtime.remember {
        mutableStateOf(context.getSharedPreferences("aura_memory_store", android.content.Context.MODE_PRIVATE).getBoolean("notifications_enabled", false))
    }
    AuraHubSectionTitle("SETTINGS")
    AuraInfoCard("PRIVACY", "Aura keeps your memories, actions and events on this device.")
    Spacer(Modifier.height(6.dp))
    AuraSecondaryButton(
        text = if (enabled) "NOTIFICATIONS • ON" else "NOTIFICATIONS • OFF",
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!enabled) {
            onEnableNotifications()
            enabled = true
        } else {
            enabled = false
            context.getSharedPreferences("aura_memory_store", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("notifications_enabled", false).apply()
        }
    }
    Spacer(Modifier.height(10.dp))
    AuraInfoCard("LOCAL AI", "Whisper and Gemma run locally on the device.")
    AuraInfoCard("HARDWARE", "Hardware connection can be added when the Aura device protocol is ready.")
    Spacer(Modifier.height(10.dp))
    AuraSecondaryButton(
        text = "RESET FOR DEMO",
        modifier = Modifier.fillMaxWidth()
    ) {
        onResetDemo()
    }
}

@Composable
private fun AuraMemoryContent(
    memories: List<AuraMemoryItem>,
    search: String,
    onSearch: (String) -> Unit,
    ragQuestion: String,
    ragAnswer: String,
    ragSources: String,
    ragLoading: Boolean,
    onRagQuestionChange: (String) -> Unit,
    onAskAuraMemory: () -> Unit,
    onAnalyzeSavedSummary: (AuraMemoryItem) -> Unit,
    onAnalyzeSavedInsights: (AuraMemoryItem) -> Unit,
    onAnalyzeSavedActionsEvents: (AuraMemoryItem) -> Unit,
    onAnalyzeSavedImportantDates: (AuraMemoryItem) -> Unit,
    memoryAnalysisSession: String?,
    memoryAnalysisAction: String
) {
    var selectedId by androidx.compose.runtime.remember { mutableStateOf<Long?>(null) }
    var playing by androidx.compose.runtime.remember { mutableStateOf(false) }
    var positionMs by androidx.compose.runtime.remember { mutableStateOf(0) }
    var durationMs by androidx.compose.runtime.remember { mutableStateOf(0) }
    var sliderValue by androidx.compose.runtime.remember { mutableStateOf(0f) }
    var isSeeking by androidx.compose.runtime.remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = androidx.compose.runtime.remember { mutableStateOf<MediaPlayer?>(null) }

    val selectedMemory = selectedId?.let { id -> memories.firstOrNull { it.id == id } }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            player.value?.release()
            player.value = null
        }
    }

    androidx.compose.runtime.LaunchedEffect(selectedId, selectedMemory?.audioPath) {
        player.value?.release()
        player.value = null
        playing = false
        positionMs = 0
        sliderValue = 0f
        durationMs = 0
        val path = selectedMemory?.audioPath ?: return@LaunchedEffect
        val file = File(path)
        if (file.exists()) {
            runCatching {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
                retriever.release()
            }
            runCatching {
                val preparedPlayer = MediaPlayer().apply {
                    setDataSource(context, Uri.fromFile(file))
                    setOnCompletionListener {
                        positionMs = durationMs
                        sliderValue = 1f
                        playing = false
                    }
                    prepare()
                }
                durationMs = preparedPlayer.duration.coerceAtLeast(0)
                player.value = preparedPlayer
            }.onFailure {
                Log.w("AuraMemory", "Could not prepare saved audio", it)
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(playing, selectedId) {
        while (playing) {
            val p = player.value ?: break
            runCatching {
                if (!isSeeking) {
                    positionMs = p.currentPosition.coerceAtLeast(0)
                    durationMs = p.duration.coerceAtLeast(0)
                    sliderValue = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
                }
            }
            kotlinx.coroutines.delay(200L)
        }
    }

    fun formatPlayerTime(ms: Int): String {
        val total = ms.coerceAtLeast(0) / 1000
        val minutes = total / 60
        val seconds = total % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    fun copyText(label: String, text: String) {
        val clean = text.trim()
        if (clean.isBlank()) {
            Toast.makeText(context, "$label is empty", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, clean))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    AuraHubSectionTitle(if (selectedMemory == null) "LOCAL MEMORY" else "CONVERSATION DETAILS")

    if (selectedMemory == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AuraColors.moduleBackground)
                .border(1.dp, AuraColors.moduleBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "ASK AURA MEMORY",
                color = AuraColors.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "Search past conversations by meaning and answer from retrieved local memory.",
                color = AuraColors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = ragQuestion,
                onValueChange = onRagQuestionChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = AuraColors.textPrimary,
                    fontFamily = AuraFont,
                    fontSize = 13.sp
                ),
                label = { Text("Ask about a past conversation") },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AuraColors.textPrimary,
                    unfocusedTextColor = AuraColors.textPrimary,
                    cursorColor = AuraColors.primary,
                    focusedBorderColor = AuraColors.primary,
                    unfocusedBorderColor = AuraColors.softBorder,
                    focusedLabelColor = AuraColors.primary,
                    unfocusedLabelColor = AuraColors.textSecondary
                )
            )
            Spacer(Modifier.height(8.dp))
            AuraSecondaryButton(
                text = if (ragLoading) "SEARCHING MEMORY..." else "ASK AURA",
                modifier = Modifier.fillMaxWidth(),
                onClick = onAskAuraMemory
            )
            if (ragAnswer.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                AuraInfoCard("ANSWER", ragAnswer)
            }
            if (ragSources.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                AuraInfoCard("RETRIEVED MEMORY", ragSources)
            }
        }

        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = AuraColors.textPrimary, fontFamily = AuraFont, fontSize = 13.sp),
            label = { Text("Search memory") },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = AuraColors.textPrimary,
                unfocusedTextColor = AuraColors.textPrimary,
                cursorColor = AuraColors.primary,
                focusedBorderColor = AuraColors.primary,
                unfocusedBorderColor = AuraColors.softBorder,
                focusedLabelColor = AuraColors.primary,
                unfocusedLabelColor = AuraColors.textSecondary
            )
        )
        Spacer(Modifier.height(12.dp))
        val filtered = memories
            .filter { search.isBlank() || it.title.contains(search, true) || it.summary.contains(search, true) || it.transcript.contains(search, true) }
            .reversed()
        if (memories.isEmpty()) AuraInfoCard("EMPTY", "No conversation memories saved yet.")
        else if (filtered.isEmpty()) AuraInfoCard("NO MATCH", "No local memories match \"$search\".")
        filtered.forEach { memory ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AuraColors.card)
                    .border(1.dp, AuraColors.cardBorder, RoundedCornerShape(16.dp)).padding(14.dp)
            ) {
                Text(memory.title, color = AuraColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(memory.summary, color = AuraColors.textSecondary, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 4)
                Spacer(Modifier.height(8.dp))
                AuraSecondaryButton("OPEN CONVERSATION", Modifier.fillMaxWidth()) {
                    selectedId = memory.id
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    } else {
        val memory = selectedMemory

        AuraSecondaryButton("← BACK TO MEMORIES", Modifier.fillMaxWidth()) {
            player.value?.release(); player.value = null; playing = false; selectedId = null
        }
        Spacer(Modifier.height(10.dp))
        AuraInfoCard("CONVERSATION", memory.title)

        // --------------------------------------------------
        // AUDIO PLAYER
        // --------------------------------------------------
        AuraHubSectionTitle("ORIGINAL AUDIO")
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AuraColors.card)
                .border(1.dp, AuraColors.cardBorder, RoundedCornerShape(16.dp)).padding(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatPlayerTime(positionMs), color = AuraColors.textSecondary, fontSize = 11.sp)
                Text(
                    if (durationMs > 0) formatPlayerTime(durationMs) else "--:--",
                    color = AuraColors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (durationMs > 0) {
                Slider(
                    value = sliderValue.coerceIn(0f, 1f),
                    onValueChange = {
                        isSeeking = true
                        sliderValue = it
                        positionMs = (it * durationMs.toFloat()).toInt()
                    },
                    onValueChangeFinished = {
                        player.value?.let { p ->
                            runCatching { p.seekTo(positionMs.coerceIn(0, durationMs.toInt())) }
                        }
                        isSeeking = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AuraSecondaryButton("↶ 10s", Modifier.weight(1f)) {
                    player.value?.let { p ->
                        val newPos = (p.currentPosition - 10_000).coerceAtLeast(0)
                        runCatching { p.seekTo(newPos) }
                        positionMs = newPos
                        if (durationMs > 0) sliderValue = newPos.toFloat() / durationMs.toFloat()
                    }
                }
                AuraSecondaryButton(
                    if (playing) "⏸ PAUSE" else "▶ PLAY",
                    Modifier.weight(1f)
                ) {
                    if (playing) {
                        player.value?.pause()
                        playing = false
                    } else {
                        val existingPlayer = player.value
                        if (existingPlayer != null) {
                            runCatching {
                                existingPlayer.start()
                                playing = true
                            }.onFailure {
                                existingPlayer.release()
                                player.value = null
                            }
                        }
                        if (player.value == null && memory.audioPath.isNotBlank()) {
                            val audioFile = File(memory.audioPath)
                            if (audioFile.exists()) {
                                runCatching {
                                    val newPlayer = MediaPlayer().apply {
                                        setDataSource(context, Uri.fromFile(audioFile))
                                        setOnCompletionListener {
                                            positionMs = durationMs
                                            sliderValue = 1f
                                            playing = false
                                            it.release()
                                            player.value = null
                                        }
                                        prepare()
                                    }
                                    durationMs = newPlayer.duration.coerceAtLeast(0)
                                    newPlayer.seekTo(positionMs.coerceIn(0, durationMs))
                                    newPlayer.start()
                                    player.value = newPlayer
                                    playing = true
                                }.onFailure {
                                    Toast.makeText(context, "Audio playback failed", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Saved audio not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                AuraSecondaryButton("10s ↷", Modifier.weight(1f)) {
                    player.value?.let { p ->
                        val max = if (durationMs > 0) durationMs else p.duration.coerceAtLeast(0)
                        val newPos = (p.currentPosition + 10_000).coerceAtMost(max)
                        runCatching { p.seekTo(newPos) }
                        positionMs = newPos
                        if (max > 0) sliderValue = newPos.toFloat() / max.toFloat()
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // --------------------------------------------------
        // SAVED CONVERSATION AI ACTIONS
        // --------------------------------------------------
        AuraHubSectionTitle("ANALYZE THIS CONVERSATION")
        Text(
            "Run these on this saved conversation whenever you need the result. Existing transcript and audio stay unchanged.",
            color = AuraColors.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        val busyForThisMemory = memoryAnalysisSession == memory.sessionId
        val actionLabel = memoryAnalysisAction

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuraSecondaryButton(
                if (busyForThisMemory && actionLabel == "SUMMARY") "SUMMARIZING..." else "SUMMARY",
                Modifier.weight(1f)
            ) {
                if (!busyForThisMemory) onAnalyzeSavedSummary(memory)
            }
            AuraSecondaryButton(
                if (busyForThisMemory && actionLabel == "AI INSIGHTS") "ANALYZING..." else "AI INSIGHTS",
                Modifier.weight(1f)
            ) {
                if (!busyForThisMemory) onAnalyzeSavedInsights(memory)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuraSecondaryButton(
                if (busyForThisMemory && actionLabel == "ACTIONS & EVENTS") "EXTRACTING..." else "ACTIONS & EVENTS",
                Modifier.weight(1f)
            ) {
                if (!busyForThisMemory) onAnalyzeSavedActionsEvents(memory)
            }
            AuraSecondaryButton(
                if (busyForThisMemory && actionLabel == "IMPORTANT DATES") "FINDING DATES..." else "IMPORTANT DATES",
                Modifier.weight(1f)
            ) {
                if (!busyForThisMemory) onAnalyzeSavedImportantDates(memory)
            }
        }

        Spacer(Modifier.height(12.dp))

        // --------------------------------------------------
        // COPYABLE CONTENT
        // --------------------------------------------------
        AuraHubSectionTitle("SAVED CONTENT")

        AuraInfoCard("FULL TRANSCRIPT", memory.transcript)
        AuraSecondaryButton("COPY FULL TRANSCRIPT", Modifier.fillMaxWidth()) {
            copyText("Full transcript", memory.transcript)
        }

        AuraInfoCard("SPEAKER TRANSCRIPT", memory.speakerTranscript.ifBlank { memory.transcript })
        AuraSecondaryButton("COPY SPEAKER TRANSCRIPT", Modifier.fillMaxWidth()) {
            copyText("Speaker transcript", memory.speakerTranscript.ifBlank { memory.transcript })
        }

        AuraInfoCard("SUMMARY", memory.summary)
        AuraSecondaryButton("COPY SUMMARY", Modifier.fillMaxWidth()) {
            copyText("Summary", memory.summary)
        }

        if (memory.insights.isNotBlank()) {
            AuraInfoCard("AI INSIGHTS", memory.insights)
            AuraSecondaryButton("COPY AI INSIGHTS", Modifier.fillMaxWidth()) {
                copyText("AI insights", memory.insights)
            }
        }
        if (memory.actionsText.isNotBlank()) {
            AuraInfoCard("ACTION ITEMS", memory.actionsText)
            AuraSecondaryButton("COPY ACTION ITEMS", Modifier.fillMaxWidth()) {
                copyText("Action items", memory.actionsText)
            }
        }
        if (memory.eventsText.isNotBlank()) {
            AuraInfoCard("EVENTS", memory.eventsText)
            AuraSecondaryButton("COPY EVENTS", Modifier.fillMaxWidth()) {
                copyText("Events", memory.eventsText)
            }
        }
    }
}

@Composable
private fun AuraHubSectionTitle(text: String) {
    Text(text, Modifier.fillMaxWidth(), color = AuraColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun AuraInfoCard(label: String, text: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AuraColors.card).border(1.dp, AuraColors.cardBorder, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(label, color = AuraColors.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(5.dp))
        Text(text, color = AuraColors.textPrimary, fontSize = 13.sp, lineHeight = 19.sp)
    }
    Spacer(Modifier.height(8.dp))
}

// ==========================================================
// PREMIUM CARD
// ==========================================================

@Composable
private fun AuraPremiumCard(
    borderColor: Color,
    content: @Composable () -> Unit
) {

    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape =
                RoundedCornerShape(
                    22.dp
                )
            )
            .clip(
                RoundedCornerShape(
                    22.dp
                )
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        AuraColors.cardElevatedTop,
                        AuraColors.cardElevatedBottom
                    )
                )
            )
            .border(
                1.dp,
                borderColor,
                RoundedCornerShape(
                    22.dp
                )
            )
            .padding(17.dp)
    ) {

        content()
    }
}


// ==========================================================
// PRIMARY ACTION BUTTON
// ==========================================================

@Composable
private fun AuraActionButton(
    text: String,
    modifier: Modifier,
    active: Boolean,
    onClick: () -> Unit
) {

    Button(
        onClick =
        onClick,
        modifier =
        modifier.height(52.dp),
        shape =
        RoundedCornerShape(
            18.dp
        ),
        colors =
        ButtonDefaults.buttonColors(
            containerColor =
            if (active)
                AuraColors.primaryButtonPressed
            else
                AuraColors.secondaryButton,
            contentColor =
            AuraColors.textPrimary
        )
    ) {

        Text(
            text =
            text,
            fontSize =
            12.sp,
            fontWeight =
            FontWeight.SemiBold
        )
    }
}


// ==========================================================
// SECONDARY BUTTON
// ==========================================================

@Composable
private fun AuraSecondaryButton(
    text: String,
    modifier: Modifier,
    onClick: () -> Unit
) {

    Button(
        onClick =
        onClick,
        modifier =
        modifier.height(46.dp),
        shape =
        RoundedCornerShape(
            16.dp
        ),
        colors =
        ButtonDefaults.buttonColors(
            containerColor =
            AuraColors.secondaryButton,
            contentColor =
            AuraColors.secondaryButtonText
        )
    ) {

        Text(
            text =
            text,
            fontSize =
            11.sp
        )
    }
}


// ==========================================================
// FUTURE MODULE CARD
// ==========================================================

@Composable
private fun AuraModuleCard(
    title: String,
    subtitle: String,
    modifier: Modifier
) {

    Box(
        modifier =
        modifier
            .height(82.dp)
            .clip(
                RoundedCornerShape(
                    18.dp
                )
            )
            .background(
                AuraColors.moduleBackground
            )
            .border(
                1.dp,
                AuraColors.moduleBorder,
                RoundedCornerShape(
                    18.dp
                )
            )
            .padding(11.dp)
    ) {

        Column {

            Text(
                text =
                title,
                color =
                Color(0xFFE2EAF7),
                fontSize =
                12.sp,
                fontWeight =
                FontWeight.SemiBold
            )

            Spacer(
                Modifier.height(5.dp)
            )

            Text(
                text =
                subtitle,
                color =
                Color(0xFF657691),
                fontSize =
                9.sp
            )

            Spacer(
                Modifier.height(7.dp)
            )

            Box(
                modifier =
                Modifier
                    .width(24.dp)
                    .height(2.dp)
                    .clip(
                        RoundedCornerShape(
                            10.dp
                        )
                    )
                    .background(
                        Color(0x667EE7FF)
                    )
            )
        }
    }
}
@SuppressLint("MissingPermission")
class AuraReminderReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(
        context: android.content.Context,
        intent: android.content.Intent
    ) {

        // Check notification permission before posting.
        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val kind =
            intent.getStringExtra("kind")
                ?: "Reminder"

        val title =
            intent.getStringExtra("title")
                ?: "You have an upcoming item."

        val channelId = "aura_reminders"

        val notificationManager =
            context.getSystemService(
                android.content.Context.NOTIFICATION_SERVICE
            ) as android.app.NotificationManager

        // Create Aura reminder notification channel.
        if (
            notificationManager.getNotificationChannel(
                channelId
            ) == null
        ) {

            val soundUri =
                android.media.RingtoneManager.getDefaultUri(
                    android.media.RingtoneManager.TYPE_NOTIFICATION
                )

            val channel =
                android.app.NotificationChannel(
                    channelId,
                    "Aura Reminders",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {

                    description =
                        "Aura reminders and important events"

                    enableVibration(true)

                    setSound(
                        soundUri,
                        null
                    )
                }

            notificationManager.createNotificationChannel(
                channel
            )
        }

        // Open Aura when the notification is tapped.
        val openIntent =
            android.content.Intent(
                context,
                MainActivity::class.java
            ).apply {

                flags =
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val pendingIntent =
            android.app.PendingIntent.getActivity(
                context,
                title.hashCode(),
                openIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
            )
        // Build notification.
        val notification =
            androidx.core.app.NotificationCompat.Builder(
                context,
                channelId
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentTitle(
                    "Aura • $kind"
                )
                .setContentText(
                    title
                )
                .setPriority(
                    androidx.core.app.NotificationCompat.PRIORITY_HIGH
                )
                .setAutoCancel(true)
                .setContentIntent(
                    pendingIntent
                )
                .setVibrate(
                    longArrayOf(
                        0,
                        250,
                        120,
                        250
                    )
                )
                .build()

        notificationManager.notify(
            title.hashCode(),
            notification
        )
    }
}



