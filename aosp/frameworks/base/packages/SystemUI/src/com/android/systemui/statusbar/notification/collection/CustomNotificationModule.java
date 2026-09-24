package com.android.systemui.statusbar.notification.collection;
import com.android.systemui.CoreStartable;
import com.android.systemui.statusbar.notification.collection.NotifCollection;
import javax.inject.Inject;
import com.android.systemui.dagger.SysUISingleton;
import dagger.Binds;
import dagger.Module;   
import dagger.multibindings.IntoMap;
import dagger.multibindings.ClassKey;

@SysUISingleton
public class CustomNotificationModule implements CoreStartable {

    private final NotifCollection mNotifCollection;
    private final CustomNotificationHandler mCustomHandler;

    @Inject
    public CustomNotificationModule(
            NotifCollection notifCollection,
            CustomNotificationHandler customHandler) {
        mNotifCollection = notifCollection;
        mCustomHandler = customHandler;
    }

    @Override
    public void start() {
        // Ponto direto de injeção: registra seu handler no NotifCollection
        // Ele receberá o evento exatamente no mesmo momento em que o pipeline do SystemUI processa.
        mNotifCollection.addCollectionListener(mCustomHandler);
    }
    @Module
    public interface StartModule {
        @Binds
        @IntoMap
        @ClassKey(CustomNotificationModule.class)
        CoreStartable bindCustomNotificationModule(CustomNotificationModule service);
    }
}