// ITouchlessKiosk.aidl
package com.github.savan.touchlesskiosk;

import android.view.MotionEvent;
import com.github.savan.touchlesskiosk.IStreamListener;
import com.github.savan.touchlesskiosk.IKioskListener;
import android.content.Intent;

interface ITouchlessKiosk {
    void registerKioskListener(in IKioskListener listener);

    void registerKiosk();

    void unregisterKiosk();

    void registerStreamListener(in IStreamListener listener);

    void setupStreaming(in Intent data);

    void teardownStreaming();

    int getStreamingStatus();

    boolean injectMotionEvents(in MotionEvent[] pointerEvents);
}