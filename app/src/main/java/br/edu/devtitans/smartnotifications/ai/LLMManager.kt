package br.edu.devtitans.smartnotifications.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.NoRepeatNgramConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Abstraction that keeps the summary use case independent from LiteRT-LM. */
fun interface TextGenerator {
    fun generate(prompt: String): Flow<String>
}

/**
 * Owns the real LiteRT-LM engine and serializes access to its native resources.
 *
 * The selected `.litertlm` model is loaded in [initialize]. Inference happens in [generate]
 * through the official asynchronous Conversation Flow API. Both paths run off the main thread.
 */
class LLMManager(context: Context) : TextGenerator {
    private val cacheDirectory = File(context.cacheDir, "litertlm").apply { mkdirs() }
    private val operationMutex = Mutex()

    @Volatile
    private var engine: Engine? = null

    val isInitialized: Boolean
        get() = engine?.isInitialized() == true

    val cpuThreadCount: Int = Runtime.getRuntime()
        .availableProcessors()
        .coerceIn(1, MAX_CPU_THREADS)

    suspend fun initialize(modelFile: File) {
        operationMutex.withLock {
            withContext(Dispatchers.Default) {
                require(modelFile.isFile && modelFile.canRead()) {
                    "Modelo não encontrado ou sem permissão de leitura: ${modelFile.absolutePath}"
                }
                require(modelFile.extension.equals("litertlm", ignoreCase = true)) {
                    "O modelo precisa estar no formato .litertlm."
                }

                engine?.let { current -> runCatching { current.close() } }
                engine = null

                // CPU is the compatibility-first backend for this PoC. A future device profile can
                // inject GPU/NPU selection without changing SummaryGenerator or NotificationViewer.
                val newEngine = Engine(
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        // Capping native workers avoids one worker/scratch allocation per host core
                        // on large emulators and keeps other Android work responsive.
                        backend = Backend.CPU(threadCount = cpuThreadCount),
                        // The prompt is bounded to 1,600 chars and summaries to 48 tokens. A
                        // 768-token context is enough for this task without an oversized KV cache.
                        maxNumTokens = 768,
                        cacheDir = cacheDirectory.absolutePath,
                    ),
                )

                // This is the potentially expensive native model load and must stay off the UI.
                try {
                    newEngine.initialize()
                    engine = newEngine
                } catch (throwable: Throwable) {
                    // A failed native initialization can still own partially allocated buffers.
                    runCatching { newEngine.close() }
                    throw throwable
                }
            }
        }
    }

    override fun generate(prompt: String): Flow<String> = flow {
        require(prompt.isNotBlank()) { "O prompt não pode estar vazio." }

        operationMutex.withLock {
            val activeEngine = engine?.takeIf { it.isInitialized() }
                ?: error("O modelo ainda não foi inicializado.")

            val conversation = activeEngine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = 20,
                        topP = 0.8,
                        temperature = 0.1,
                    ),
                    // Dataset targets are at most 30 words. A hard generation bound avoids long,
                    // repetitive answers and cuts latency/KV-cache growth on constrained devices.
                    maxOutputToken = 48,
                ),
            )

            val accumulatedResponse = StringBuilder()
            var lastEmittedResponse = ""
            try {
                // Real on-device inference. Each Message is a streamed fragment from LiteRT-LM.
                conversation.sendMessageAsync(
                    text = prompt,
                    noRepeatNgramConfig = NoRepeatNgramConfig(noRepeatNgramSize = 4),
                ).collect { message ->
                    accumulatedResponse.append(message.toString())
                    // Updating StateFlow/TextView for every token repeatedly copies the complete
                    // response. Small batches keep streaming responsive without quadratic churn.
                    if (accumulatedResponse.length - lastEmittedResponse.length >= EMIT_BATCH_CHARS) {
                        lastEmittedResponse = accumulatedResponse.toString()
                        emit(lastEmittedResponse)
                    }
                }

                check(accumulatedResponse.isNotBlank()) {
                    "O modelo concluiu a inferência sem produzir texto."
                }

                val finalResponse = accumulatedResponse.toString().sanitizeSummary()
                if (finalResponse != lastEmittedResponse) emit(finalResponse)
            } finally {
                if (!currentCoroutineContext().isActive) {
                    runCatching { conversation.cancelProcess() }
                }
                runCatching { conversation.close() }
            }
        }
    }.flowOn(Dispatchers.Default)

    suspend fun close() {
        operationMutex.withLock {
            withContext(Dispatchers.Default) {
                engine?.let { current -> runCatching { current.close() } }
                engine = null
            }
        }
    }

    private companion object {
        const val EMIT_BATCH_CHARS = 32
        const val MAX_CPU_THREADS = 4
    }
}

internal fun String.sanitizeSummary(): String {
    val firstParagraph = trim().split(Regex("\\n\\s*\\n"), limit = 2).firstOrNull().orEmpty()
    return firstParagraph
        .lineSequence()
        .joinToString(" ") { line -> line.trim().trimStart('-', '*', '•').trim() }
        .replace(Regex("^(?:resumo|summary)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
        .replace("**", "")
        .trim()
}
