package com.github.savan.touchlesskiosk.webrtc

import android.content.Context
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

interface ISignallingClient {
    interface ISignalListener {
        fun onKioskRegistered()

        fun onKioskUnregistered()

        fun onConnectionRequestReceived()

        fun onConnectionEstablished()

        fun onDisconnectionRequestReceived()

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