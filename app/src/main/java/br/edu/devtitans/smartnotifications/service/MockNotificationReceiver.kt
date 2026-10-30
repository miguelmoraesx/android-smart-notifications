package br.edu.devtitans.smartnotifications.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import br.edu.devtitans.smartnotifications.R

class MockNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appName = intent.getStringExtra("app") ?: "WhatsApp"
        val title = intent.getStringExtra("title") ?: "Mock"
        val text = intent.getStringExtra("text") ?: "Mock Text"
        
        // Em vez de injetar direto no código, disparamos uma notificação silenciosa real!
        // Assim, ela aparece na tela para você ver, e o Listener do Android a capta de verdade.
        val builder = NotificationCompat.Builder(context, "mock_messages_channel")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("[$appName] $title")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW) // IMPORTANTE: Silenciosa
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // O Id precisa ser dinâmico para as notificações não sobrescreverem umas às outras antes da leitura
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
