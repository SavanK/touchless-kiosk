package com.github.savan.touchlesskiosk.webrtc

import android.content.Context
import com.github.savan.touchlesskiosk.webrtc.model.Connection
import com.github.savan.touchlesskiosk.webrtc.model.Kiosk
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

interface ISignallingClient {
    interface ISignalListener {
        fun onKioskRegistered(kiosk: Kiosk)

        fun onKioskUnregistered(kiosk: Kiosk)

        fun onConnectionEstablished(connection: Connection)

        fun onConnectionTeardown(connection: Connection)

        fun onOfferReceived(description: SessionDescription)

        fun onAnswerReceived(description: SessionDescription)

        fun onIceCandidateReceived(iceCandidate: IceCandidate)
    }

    fun initialize(c: Context, listener: ISignalListener)

    fun registerKiosk()

    fun unregisterKiosk()

    fun sendWebRtcPayRequest(webRtcPayload: Any)

    fun destroy()
}