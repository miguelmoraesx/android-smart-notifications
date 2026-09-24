package com.android.systemui.statusbar.notification.collection;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import javax.inject.Inject;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;

@SysUISingleton
public class CustomNotificationHandler implements NotifCollectionListener {

    @Inject
    public CustomNotificationHandler() {

    }

    @Override
    public void onEntryAdded(NotificationEntry entry) {
        StatusBarNotification sbn = entry.getSbn();
        
        String packageName = sbn.getPackageName();
        String title = sbn.getNotification().extras.getString("android.title");
        Log.d("CustomNotificationHandler", "Nova notificação de: " + packageName + ", título: " + title);

    }

    @Override
    public void onEntryUpdated(NotificationEntry entry) {

    }

    @Override
    public void onEntryRemoved(NotificationEntry entry, int reason) {

    }
}