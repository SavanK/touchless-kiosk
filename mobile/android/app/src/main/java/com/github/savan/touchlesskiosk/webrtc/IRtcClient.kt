package com.github.savan.touchlesskiosk.webrtc

import android.content.Intent
import org.webrtc.IceCandidate
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

interface IRtcClient {
    interface IStreamListener {
        enum class STATUS(val code: Int) {
            READY_TO_STREAM(0),
            STREAMING(1),
            NOT_READY_TO_STREAM(-1);
        }

        fun onStatusChange(streamingStatus: STATUS)
    }

    fun registerStreamListener(listener: IStreamListener)

    fun unregisterStreamListener(listener: IStreamListener)

    fun setupStreaming(data: Intent)

    fun teardownStreaming()

    fun startStreaming(sdpObserver: SdpObserver)

    fun onRemoteSessionReceived(sessionDescription: SessionDescription)

    fun addIceCandidate(iceCandidate: IceCandidate?)

    fun stopStreaming()

    fun getStreamingStatus(): IStreamListener.STATUS
}