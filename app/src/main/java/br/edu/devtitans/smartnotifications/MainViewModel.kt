package br.edu.devtitans.smartnotifications

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.edu.devtitans.smartnotifications.ai.LLMManager
import br.edu.devtitans.smartnotifications.ai.LocalModelStore
import br.edu.devtitans.smartnotifications.ai.PromptBuilder
import br.edu.devtitans.smartnotifications.ai.SummaryGenerator
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val modelStatus: String = "Nenhum modelo carregado.",
    val modelReady: Boolean = false,
    val isModelOperationRunning: Boolean = false,
    val isGenerating: Boolean = false,
    val summary: String = "",
    val errorMessage: String? = null,
    val hasStoredModel: Boolean = false,
    val lastInferenceDurationMs: Long? = null,
) {
    val isBusy: Boolean get() = isModelOperationRunning || isGenerating
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val modelStore = LocalModelStore(application)
    private val llmManager = LLMManager(application)
    private val summaryGenerator = SummaryGenerator(llmManager, PromptBuilder())
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val installedModelAtStartup = modelStore.getInstalledModel()
    private var generationJob: Job? = null

    private val mutableUiState = MutableStateFlow(
        MainUiState(hasStoredModel = installedModelAtStartup != null),
    )
    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()

    init {
        installedModelAtStartup?.let(::initializeModel)
    }

    fun importAndInitializeModel(uri: Uri) {
        if (mutableUiState.value.isBusy) return

        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                modelStatus = "Copiando o modelo para o armazenamento privado…",
                modelReady = false,
                isModelOperationRunning = true,
                errorMessage = null,
            )

            try {
                val modelFile = modelStore.import(uri)
                loadModel(modelFile)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                showModelError(throwable)
            }
        }
    }

    fun summarize(sourceText: String) {
        if (mutableUiState.value.isBusy) return
        if (!mutableUiState.value.modelReady) {
            mutableUiState.value = mutableUiState.value.copy(
                errorMessage = "Selecione e carregue um modelo antes de gerar o resumo.",
            )
            return
        }
        if (sourceText.isBlank()) {
            mutableUiState.value = mutableUiState.value.copy(
                errorMessage = "Informe algum texto para resumir.",
            )
            return
        }

        generationJob = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            mutableUiState.value = mutableUiState.value.copy(
                isGenerating = true,
                summary = "",
                errorMessage = null,
                lastInferenceDurationMs = null,
            )

            try {
                summaryGenerator.generateSummary(sourceText).collect { partialSummary ->
                    mutableUiState.value = mutableUiState.value.copy(summary = partialSummary)
                }
            } catch (cancellation: CancellationException) {
                mutableUiState.value = mutableUiState.value.copy(
                    errorMessage = "Geração cancelada.",
                )
                throw cancellation
            } catch (throwable: Throwable) {
                mutableUiState.value = mutableUiState.value.copy(
                    errorMessage = throwable.readableMessage("Falha durante a inferência"),
                )
            } finally {
                mutableUiState.value = mutableUiState.value.copy(
                    isGenerating = false,
                    lastInferenceDurationMs = SystemClock.elapsedRealtime() - startedAt,
                )
                generationJob = null
            }
        }
    }

    fun cancelSummary() {
        generationJob?.cancel()
    }

    fun unloadModel() {
        if (mutableUiState.value.isBusy || !mutableUiState.value.modelReady) return

        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                modelStatus = "Liberando memória do modelo…",
                isModelOperationRunning = true,
                errorMessage = null,
            )
            try {
                llmManager.close()
                mutableUiState.value = mutableUiState.value.copy(
                    modelStatus = "Modelo descarregado da memória. O arquivo continua salvo.",
                    modelReady = false,
                    isModelOperationRunning = false,
                    hasStoredModel = modelStore.getInstalledModel() != null,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                showModelError(throwable)
            }
        }
    }

    fun reloadStoredModel() {
        if (mutableUiState.value.isBusy || mutableUiState.value.modelReady) return
        val modelFile = modelStore.getInstalledModel()
        if (modelFile == null) {
            mutableUiState.value = mutableUiState.value.copy(
                hasStoredModel = false,
                errorMessage = "Nenhum modelo salvo foi encontrado.",
            )
            return
        }
        initializeModel(modelFile)
    }

    private fun initializeModel(modelFile: File) {
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                modelStatus = "Inicializando LiteRT-LM…",
                modelReady = false,
                isModelOperationRunning = true,
                errorMessage = null,
            )
            try {
                loadModel(modelFile)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                showModelError(throwable)
            }
        }
    }

    private suspend fun loadModel(modelFile: File) {
        mutableUiState.value = mutableUiState.value.copy(
            modelStatus = "Carregando ${modelFile.name}…",
            isModelOperationRunning = true,
        )
        llmManager.initialize(modelFile)
        mutableUiState.value = mutableUiState.value.copy(
            modelStatus = "Modelo pronto (${modelFile.length().toMegabytes()} MB, CPU com ${llmManager.cpuThreadCount} threads).",
            modelReady = true,
            isModelOperationRunning = false,
            errorMessage = null,
            hasStoredModel = true,
        )
    }

    private fun showModelError(throwable: Throwable) {
        mutableUiState.value = mutableUiState.value.copy(
            modelStatus = "Modelo não carregado.",
            modelReady = false,
            isModelOperationRunning = false,
            errorMessage = throwable.readableMessage("Falha ao carregar o modelo"),
            hasStoredModel = modelStore.getInstalledModel() != null,
        )
    }

    override fun onCleared() {
        // viewModelScope cancellation first unwinds an active Conversation; native engine cleanup
        // is then serialized by LLMManager on this short-lived cleanup scope.
        closeScope.launch {
            try {
                llmManager.close()
            } finally {
                closeScope.cancel()
            }
        }
    }
}

private fun Throwable.readableMessage(prefix: String): String =
    "$prefix: ${message ?: javaClass.simpleName}"

private fun Long.toMegabytes(): Long = this / (1024L * 1024L)
