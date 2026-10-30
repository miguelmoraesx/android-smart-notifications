package br.edu.devtitans.smartnotifications.service

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.edu.devtitans.smartnotifications.R
import br.edu.devtitans.smartnotifications.ai.SummaryGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Agrupa notificações recebidas e chama o gerador de resumos (LLM) após um tempo de silêncio (debounce).
 */
class NotificationViewer(
    private val context: Context,
    private val summaryGenerator: SummaryGenerator
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var debounceJob: Job? = null
    
    private val pendingTexts = mutableListOf<String>()
    
    // UI ou loggers podem observar as atualizações neste StateFlow
    private val _latestSummary = MutableStateFlow("")
    val latestSummary: StateFlow<String> = _latestSummary.asStateFlow()

    fun onNotificationReceived(appName: String, title: String?, text: String?) {
        val content = text?.trim()
        val titleText = title?.trim()
        if (content.isNullOrEmpty() && titleText.isNullOrEmpty()) return
        
        val formattedNotification = "[$appName] ${titleText ?: ""}: ${content ?: ""}"
        
        synchronized(pendingTexts) {
            pendingTexts.add(formattedNotification)
            
            Log.d("NotificationViewer", "Notificação capturada: $formattedNotification")
            
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(5000)
                processPendingNotifications()
            }
        }
    }

    private suspend fun processPendingNotifications() {
        val textsToProcess = synchronized(pendingTexts) {
            val copy = pendingTexts.toList()
            pendingTexts.clear()
            copy
        }
        
        if (textsToProcess.isEmpty()) return
        
        val combinedText = textsToProcess.joinToString(separator = "\n")
        Log.d("NotificationViewer", "Iniciando geração de resumo para lote de ${textsToProcess.size} mensagens...")
        
        try {
            var finalSummary = ""
            summaryGenerator.generateSummary(combinedText).collect { partial ->
                finalSummary = partial
                _latestSummary.value = "Processando LLM...\n$partial"
            }
            Log.i("NotificationViewer", "=== RESUMO FINALIZADO ===\n$finalSummary\n=========================")
            _latestSummary.value = "Lote de ${textsToProcess.size} notificações:\n$finalSummary"
            
            // Disparar a notificação real para a barra de status do Android
            showSystemNotification(textsToProcess.size, finalSummary)
            
        } catch (e: Exception) {
            Log.e("NotificationViewer", "Erro ao gerar resumo", e)
            _latestSummary.value = "Erro ao processar notificações."
        }
    }
    
    private fun showSystemNotification(messageCount: Int, summary: String) {
        // Se a string final estiver muito limpa ou cortada, usamos ela direto.
        val safeSummary = summary.trim().ifEmpty { "Resumo não disponível." }
        
        val builder = NotificationCompat.Builder(context, "smart_summary_channel")
            .setSmallIcon(R.drawable.ic_launcher) 
            .setContentTitle("Resumo de $messageCount notificações")
            .setContentText(safeSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(safeSummary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(context)) {
                // notificationId fixo para substituir a notificação antiga
                notify(999, builder.build())
            }
        } catch (e: SecurityException) {
            Log.e("NotificationViewer", "Faltando permissão POST_NOTIFICATIONS (Android 13+)", e)
        }
    }
}
