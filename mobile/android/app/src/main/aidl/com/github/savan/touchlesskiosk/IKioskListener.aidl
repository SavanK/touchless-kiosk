// IKioskListener.aidl
package com.github.savan.touchlesskiosk;

import com.github.savan.touchlesskiosk.webrtc.model.Connection;
import com.github.savan.touchlesskiosk.webrtc.model.Kiosk;

interface IKioskListener {
    void onKioskRegistered(in Kiosk kiosk);

    void onKioskUnregistered(in Kiosk kiosk);

    void onConnectionEstablished(in Connection connection);

    void onConnectionTeardown(in Connection connection);
}