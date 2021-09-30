package com.github.savan.touchlesskiosk

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.MotionEvent
import com.github.savan.touchlesskiosk.utils.Logger
import com.github.savan.touchlesskiosk.utils.NotificationUtils
import com.github.savan.touchlesskiosk.webrtc.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.util.*

class TouchlessKioskService: Service() {
    companion object {
        private const val TAG = "ScreenCaptureService"
    }

    private val kioskListeners: MutableSet<IKioskListener> = mutableSetOf()
    private val streamListeners: MutableSet<IStreamListener> = mutableSetOf()
    private var rtcClient: IRtcClient? = null
    @ExperimentalCoroutinesApi
    private var signallingClient: SignallingClient? = null

    @ExperimentalCoroutinesApi
    private val sdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(sdp: SessionDescription?) {
            super.onCreateSuccess(sdp)
            Logger.d(TAG, "sdpObserver, onCreateSuccess, local sdp: $sdp")
            sdp?.let {
                val sdpWeb = SignallingClient.SessionDescriptionWeb(it.description, when(it.type) {
                    SessionDescription.Type.ANSWER -> "answer"
                    SessionDescription.Type.OFFER -> "offer"
                    SessionDescription.Type.PRANSWER -> "pranswer"
                    else -> ""
                })
                signallingClient?.sendWebRtcPayRequest(sdpWeb)
            }
        }
    }

    @ExperimentalCoroutinesApi
    override fun onCreate() {
        Logger.d(TAG, "onCreate")
        super.onCreate()
        initialize()
        goForeground()
    }

    private fun goForeground() {
        // create notification
        val uuid = UUID.randomUUID()
        val notification = NotificationUtils.getNotification(this, uuid.toString())
        startForeground(notification.first!!, notification.second)
    }

    @ExperimentalCoroutinesApi
    private fun initialize() {
        Logger.d(TAG, "initialize")
        signallingClient = SignallingClient()
        signallingClient?.initialize(this.application, signalListener)

        rtcClient = RtcClient(
            application,
            object : PeerConnectionObserver() {
                override fun onIceCandidate(iceCandidate: IceCandidate?) {
                    super.onIceCandidate(iceCandidate)
                    Logger.d(TAG, "onIceCandidate, iceCandidate: $iceCandidate")
                    iceCandidate?.let {
                        val webIceCandidate = SignallingClient.IceCandidateWeb("candidate",
                            iceCandidate.sdpMLineIndex, iceCandidate.sdpMid, iceCandidate.sdp)
                        signallingClient?.sendWebRtcPayRequest(webIceCandidate)
                    }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    Logger.d(TAG, "startScreenSharing, onConnectionChange: $newState")
                    super.onConnectionChange(newState)
                }
            }
        )

        rtcClient?.registerStreamListener(object: IRtcClient.IStreamListener {
            override fun onStatusChange(streamingStatus: IRtcClient.IStreamListener.STATUS) {
                Logger.d(TAG, "onStatusChange, streamingStatus: $streamingStatus")
                for (listener in streamListeners) {
                    listener.onStatusChange(convertStreamingStatusToInt(streamingStatus))
                }
            }
        })
    }

    private class TouchlessKiosk(private val touchlessKioskService: TouchlessKioskService):
        ITouchlessKiosk.Stub() {
        override fun registerKioskListener(listener: IKioskListener?) {
            listener?.let { touchlessKioskService.registerKioskListener(it) }
        }

        @ExperimentalCoroutinesApi
        override fun registerKiosk() {
            touchlessKioskService.registerKiosk()
        }

        override fun unregisterKiosk() {
            touchlessKioskService.unregisterKiosk()
        }

        override fun registerStreamListener(listener: IStreamListener?) {
            listener?.let { touchlessKioskService.registerStreamListener(it) }
        }

        @ExperimentalCoroutinesApi
        override fun setupStreaming(data: Intent) {
            touchlessKioskService.setupStreaming(data)
        }

        override fun teardownStreaming() {
            touchlessKioskService.teardownStreaming()
        }

        override fun getStreamingStatus(): Int {
            return touchlessKioskService.getStreamingStatus()
        }

        override fun injectMotionEvents(pointerEvents: Array<out MotionEvent>?): Boolean {
            return false
        }
    }

    private fun registerKioskListener(listener: IKioskListener) {
        Logger.d(TAG, "registerKioskListener")
        kioskListeners.add(listener)
    }

    private fun registerStreamListener(listener: IStreamListener) {
        Logger.d(TAG, "registerStreamListener")
        streamListeners.add(listener)
    }

    @ExperimentalCoroutinesApi
    private fun registerKiosk() {
        Logger.d(TAG, "registerKiosk")
        signallingClient?.registerKiosk()
    }

    private fun unregisterKiosk() {
        Logger.d(TAG, "unregisterKiosk")
        // TODO add unregister api both client and server side
    }

    private fun setupStreaming(data: Intent) {
        Logger.d(TAG, "setupStreaming")
        rtcClient?.setupStreaming(data)
    }

    private fun teardownStreaming() {
        Logger.d(TAG, "teardownStreaming")
        rtcClient?.teardownStreaming()
    }

    private fun getStreamingStatus(): Int {
        Logger.d(TAG, "getStreamingStatus")
        return convertStreamingStatusToInt(rtcClient?.getStreamingStatus())
    }

    private fun convertStreamingStatusToInt(status: IRtcClient.IStreamListener.STATUS?): Int {
        return status?.code?: IRtcClient.IStreamListener.STATUS.NOT_READY_TO_STREAM.code
    }

    @ExperimentalCoroutinesApi
    private val signalListener = object : ISignallingClient.ISignalListener {
        override fun onKioskRegistered() {
            Logger.d(TAG, "onKioskRegistered")
            kioskListeners.forEach { it.onKioskRegistered() }
        }

        override fun onKioskUnregistered() {
            Logger.d(TAG, "onKioskUnregistered")
            kioskListeners.forEach { it.onKioskUnregistered() }
        }

        override fun onConnectionRequestReceived() {
            Logger.d(TAG, "SignallingClientListener, onConnectionRequestReceived")
            rtcClient?.startStreaming(sdpObserver)
        }

        override fun onConnectionEstablished() {
            Logger.d(TAG, "SignallingClientListener, onConnectionEstablished")
        }

        override fun onDisconnectionRequestReceived() {
            Logger.d(TAG, "SignallingClientListener, onDisconnectionRequestReceived")
            rtcClient?.stopStreaming()
        }

        override fun onOfferReceived(description: SessionDescription) {
            // Do nothing
            Logger.d(TAG, "SignallingClientListener, onOfferReceived")
        }

        override fun onAnswerReceived(description: SessionDescription) {
            rtcClient?.onRemoteSessionReceived(description)
            Logger.d(TAG, "SignallingClientListener, onAnswerReceived")
        }

        override fun onIceCandidateReceived(iceCandidate: IceCandidate) {
            Logger.d(TAG, "SignallingClientListener, onIceCandidateReceived")
            rtcClient?.addIceCandidate(iceCandidate)
        }
    }

    @ExperimentalCoroutinesApi
    override fun onDestroy() {
        signallingClient?.destroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return TouchlessKiosk(this)
    }
}