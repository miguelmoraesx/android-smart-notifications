package br.edu.devtitans.smartnotifications.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
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

    suspend fun initialize(modelFile: File) {
        operationMutex.withLock {
            withContext(Dispatchers.Default) {
                require(modelFile.isFile && modelFile.canRead()) {
                    "Modelo não encontrado ou sem permissão de leitura: ${modelFile.absolutePath}"
                }
                require(modelFile.extension.equals("litertlm", ignoreCase = true)) {
                    "O modelo precisa estar no formato .litertlm."
                }

                engine?.let { current ->
                    if (current.isInitialized()) current.close()
                }
                engine = null

                // CPU is the compatibility-first backend for this PoC. A future device profile can
                // inject GPU/NPU selection without changing SummaryGenerator or NotificationViewer.
                val newEngine = Engine(
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = cacheDirectory.absolutePath,
                    ),
                )

                // This is the potentially expensive native model load and must stay off the UI.
                try {
                    newEngine.initialize()
                    engine = newEngine
                } catch (throwable: Throwable) {
                    if (newEngine.isInitialized()) runCatching { newEngine.close() }
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
                        topK = 40,
                        topP = 0.9,
                        temperature = 0.2,
                    ),
                    maxOutputToken = 256,
                ),
            )

            val accumulatedResponse = StringBuilder()
            try {
                // Real on-device inference. Each Message is a streamed fragment from LiteRT-LM.
                conversation.sendMessageAsync(prompt).collect { message ->
                    accumulatedResponse.append(message.toString())
                    emit(accumulatedResponse.toString())
                }

                check(accumulatedResponse.isNotBlank()) {
                    "O modelo concluiu a inferência sem produzir texto."
                }
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
                engine?.let { current ->
                    if (current.isInitialized()) current.close()
                }
                engine = null
            }
        }
    }
}
