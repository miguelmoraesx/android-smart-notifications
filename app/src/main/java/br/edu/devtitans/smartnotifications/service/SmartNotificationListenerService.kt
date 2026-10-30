package br.edu.devtitans.smartnotifications.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import br.edu.devtitans.smartnotifications.SmartNotificationsApplication

class SmartNotificationListenerService : NotificationListenerService() {

    private lateinit var notificationViewer: NotificationViewer

    override fun onCreate() {
        super.onCreate()
        Log.d("SmartNotifService", "Serviço de Notificações Criado")
        
        val app = application as SmartNotificationsApplication
        notificationViewer = app.notificationViewer
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("SmartNotifService", "Serviço de Notificações Conectado ao Android")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("SmartNotifService", "Serviço de Notificações Desconectado")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val appName = sbn.packageName

        // ANTI-LOOP: Se for do nosso próprio app E for a notificação de resumo, IGNORE-A!
        // Sem isso, nós pegamos o próprio resumo gerado, mandamos para a LLM, ela resume de novo e envia outra notificação ad infinitum.
        if (appName == packageName && notification.channelId == "smart_summary_channel") {
            Log.d("SmartNotifService", "Anti-loop: ignorando nossa própria notificação de resumo.")
            return
        }

        // Evita processar notificações do próprio sistema ou que não nos interessam tanto (ex: SystemUI)
        if (appName == "android" || appName == "com.android.systemui") return

        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        // Se a notificação for o nosso mock simulado, vamos usar o título extraído como se fosse o app real para a LLM não se perder
        val displayAppName = if (appName == packageName) "WhatsApp Simulado" else appName

        notificationViewer.onNotificationReceived(displayAppName, title, text)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Se quisermos fazer algo ao remover a notificação, seria aqui.
    }
}
