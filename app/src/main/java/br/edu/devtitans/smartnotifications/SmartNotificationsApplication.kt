package br.edu.devtitans.smartnotifications

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import br.edu.devtitans.smartnotifications.ai.LLMManager
import br.edu.devtitans.smartnotifications.ai.LocalModelStore
import br.edu.devtitans.smartnotifications.ai.PromptBuilder
import br.edu.devtitans.smartnotifications.ai.SummaryGenerator
import br.edu.devtitans.smartnotifications.service.NotificationViewer

class SmartNotificationsApplication : Application() {

    lateinit var localModelStore: LocalModelStore
        private set

    lateinit var llmManager: LLMManager
        private set

    lateinit var summaryGenerator: SummaryGenerator
        private set
        
    lateinit var notificationViewer: NotificationViewer
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        localModelStore = LocalModelStore(this)
        llmManager = LLMManager(this)
        summaryGenerator = SummaryGenerator(llmManager, PromptBuilder())
        notificationViewer = NotificationViewer(this, summaryGenerator)
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val summaryChannel = NotificationChannel(
            "smart_summary_channel", 
            "Resumos Inteligentes", 
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificações geradas pela IA contendo o resumo das suas conversas."
        }
        
        val mockChannel = NotificationChannel(
            "mock_messages_channel", 
            "Mensagens Simuladas (Testes)", 
            NotificationManager.IMPORTANCE_LOW // IMPORTANCE_LOW garante que a notificação chegue silenciosa
        ).apply {
            description = "Notificações falsas de teste exibidas silenciosamente."
        }
        
        notificationManager.createNotificationChannel(summaryChannel)
        notificationManager.createNotificationChannel(mockChannel)
    }
}
