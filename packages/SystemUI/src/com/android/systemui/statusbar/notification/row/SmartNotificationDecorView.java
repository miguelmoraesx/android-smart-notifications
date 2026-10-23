/**
 * Devtitans - SmartNotification - João Heringer
 */

package com.android.systemui.statusbar.notification.row;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.android.systemui.R;

public class SmartNotificationDecorView extends StackScrollerDecorView {

    public SmartNotificationDecorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Force the decor view and its content to be visible by default
//        setVisible(true /* nowVisible */, false /* animate */);
    }

    @Override
    protected View findContentView() {
        return findViewById(R.id.smart_notification_content);
    }

    @Override
    protected View findSecondaryView() {
        return null;
    }
}