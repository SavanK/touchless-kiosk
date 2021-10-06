package com.github.savan.touchlesskiosk

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.github.savan.touchlesskiosk.input.InputInjectionService
import com.github.savan.touchlesskiosk.utils.Logger
import com.github.savan.touchlesskiosk.utils.QRCodeUtils
import com.github.savan.touchlesskiosk.webrtc.*
import com.github.savan.touchlesskiosk.webrtc.model.Connection
import com.github.savan.touchlesskiosk.webrtc.model.Kiosk
import com.github.savan.touchlesskiosk.webrtc.model.MouseEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription


class TouchlessKioskService: Service() {
    companion object {
        private const val TAG = "TouchlessKioskService"

        private const val NOTIFICATION_ID = 1337
        private const val NOTIFICATION_CHANNEL_ID = "com.github.savan.touchlesskiosk"
        private const val NOTIFICATION_CHANNEL_NAME = "com.github.savan.touchlesskiosk"

        private const val QR_CODE_WIDTH = 200
        private const val QR_CODE_HEIGHT = 200
    }

    private val kioskListeners: MutableSet<IKioskListener> = mutableSetOf()
    private val streamListeners: MutableSet<IStreamListener> = mutableSetOf()
    private var rtcClient: IRtcClient? = null
    @ExperimentalCoroutinesApi
    private var signallingClient: SignallingClient? = null

    private var qrCodeOverlay: View? = null
    private var qrCodeView: ImageView? = null
    private lateinit var uiHandler: Handler

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

        uiHandler = UiHandler(this)

        setupQrCodeView()
        initialize()
        goForeground()
    }

    private fun setupQrCodeView() {
        if(canDrawOverlays()) {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            qrCodeOverlay = LayoutInflater.from(this).inflate(R.layout.floating_qr_code, null)

            //Add the view to the window.
            val windowParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }

            qrCodeView = qrCodeOverlay?.findViewById(R.id.qrCodeView)
            windowManager.addView(qrCodeOverlay, windowParams)
            qrCodeOverlay?.visibility = View.GONE
        }
    }

    private fun goForeground() {
        // create notification
        val notification = getNotification(getString(R.string.notification_msg_kiosk) + "null")
        postNotification(notification)
        startForeground(NOTIFICATION_ID, notification)
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
        override fun onKioskRegistered(kiosk: Kiosk) {
            Logger.d(TAG, "signalListener, onKioskRegistered")
            // update notification
            val text = StringBuilder()
            text.apply {
                append(getString(R.string.notification_msg_kiosk))
                append(kiosk.kioskId)
            }
            val notification = getNotification(text.toString())
            postNotification(notification)

            uiHandler.sendEmptyMessage(UiHandler.MSG_SHOW_QR_CODE)

            // inform listeners
            kioskListeners.forEach { it.onKioskRegistered(kiosk) }
        }

        override fun onKioskUnregistered(kiosk: Kiosk) {
            Logger.d(TAG, "signalListener, onKioskUnregistered")
            // update notification
            val text = StringBuilder()
            text.apply {
                append(getString(R.string.notification_msg_kiosk))
                append("null")
            }
            val notification = getNotification(text.toString())
            postNotification(notification)

            uiHandler.sendEmptyMessage(UiHandler.MSG_HIDE_QR_CODE)

            // inform listeners
            kioskListeners.forEach { it.onKioskUnregistered(kiosk) }
        }

        override fun onConnectionEstablished(connection: Connection) {
            Logger.d(TAG, "signalListener, onConnectionEstablished")
            // start streaming screen
            rtcClient?.startStreaming(sdpObserver)
            // update notification
            val text = StringBuilder().apply {
                append(getString(R.string.notification_msg_kiosk))
                append(connection.kiosk.kioskId)
                append(" ")
                append(getString(R.string.notification_msg_customer))
                append(connection.customer.customerId)
            }
            val notification = getNotification(text.toString())
            postNotification(notification)

            uiHandler.sendEmptyMessage(UiHandler.MSG_HIDE_QR_CODE)

            // inform listeners
            kioskListeners.forEach { it.onConnectionEstablished(connection) }
        }

        override fun onConnectionTeardown(connection: Connection) {
            Logger.d(TAG, "signalListener, onConnectionTeardown")
            // stop streaming screen
            rtcClient?.stopStreaming()
            // update notification
            val text = StringBuilder()
            text.apply {
                append(getString(R.string.notification_msg_kiosk))
                append(connection.kiosk.kioskId)
            }
            val notification = getNotification(text.toString())
            postNotification(notification)

            uiHandler.sendEmptyMessage(UiHandler.MSG_SHOW_QR_CODE)

            // inform listeners
            kioskListeners.forEach { it.onConnectionTeardown(connection) }
        }

        override fun onOfferReceived(description: SessionDescription) {
            // Do nothing
            Logger.d(TAG, "signalListener, onOfferReceived")
        }

        override fun onAnswerReceived(description: SessionDescription) {
            rtcClient?.onRemoteSessionReceived(description)
            Logger.d(TAG, "signalListener, onAnswerReceived")
        }

        override fun onIceCandidateReceived(iceCandidate: IceCandidate) {
            Logger.d(TAG, "signalListener, onIceCandidateReceived")
            rtcClient?.addIceCandidate(iceCandidate)
        }

        override fun onMouseEventReceived(mouseEvent: MouseEvent) {
            Logger.d(TAG, "signalListener, onMouseEventReceived")

            uiHandler.sendMessage(
                Message.obtain(uiHandler, UiHandler.MSG_INJECT_MOUSE_EVENT,
                    0, 0, mouseEvent)
            )
        }
    }

    @ExperimentalCoroutinesApi
    private fun showQrCode() {
        // draw QR code overlay
        signallingClient?.getRegisteredKiosk()?.kioskId?.let { kioskId ->
            val url = "https://" + SignallingClient.HOST + "/connect?kiosk=" + kioskId
            val qrCodeBitmap = QRCodeUtils.generateQRCode(url, QR_CODE_WIDTH, QR_CODE_HEIGHT)

            if(qrCodeOverlay == null) {
                setupQrCodeView()
            }

            qrCodeView?.setImageBitmap(qrCodeBitmap)
            qrCodeOverlay?.visibility = View.VISIBLE
        }
    }

    private fun hideQrCode() {
        // hide QR code overlay
        qrCodeOverlay?.visibility = View.GONE
    }

    private fun injectMouseEvent(mouseEvent: MouseEvent) {
        val intent = InputInjectionService.createInjectMouseEventIntent(this, mouseEvent)
        startService(intent)
    }

    private class UiHandler(val touchlessKioskService: TouchlessKioskService):
        Handler(touchlessKioskService.mainLooper) {
        companion object {
            const val MSG_SHOW_QR_CODE = 1001;
            const val MSG_HIDE_QR_CODE = 1002;
            const val MSG_INJECT_MOUSE_EVENT = 1003;
        }

        @ExperimentalCoroutinesApi
        override fun handleMessage(msg: Message) {
            when(msg.what) {
                MSG_SHOW_QR_CODE -> {
                    touchlessKioskService.showQrCode()
                }
                MSG_HIDE_QR_CODE -> {
                    touchlessKioskService.hideQrCode()
                }
                MSG_INJECT_MOUSE_EVENT -> {
                    touchlessKioskService.injectMouseEvent(msg.obj as MouseEvent)
                }
            }
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

    private fun postNotification(notification: Notification) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun getNotification(text: String): Notification {
        createNotificationChannel()
        return createNotification(this, text)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(context: Context, text: String): Notification {
        // TODO update notification to have it clickable to disconnect and stop service
        val builder = NotificationCompat.Builder(
            context,
            NOTIFICATION_CHANNEL_ID
        ).apply {
            setSmallIcon(R.mipmap.ic_launcher)
            setContentTitle(getString(R.string.notification_title))
            setContentText(text)
            setStyle(NotificationCompat.BigTextStyle().bigText(text))
            setOngoing(true)
            setCategory(Notification.CATEGORY_SERVICE)
            priority = Notification.PRIORITY_LOW
            setShowWhen(true)
        }
        return builder.build()
    }

    private fun canDrawOverlays(): Boolean {
        if (Settings.canDrawOverlays(this)) return true
        try {
            val mgr = getSystemService(WINDOW_SERVICE) as WindowManager
                ?: return false
            //getSystemService might return null
            val viewToAdd = View(this)
            val params = WindowManager.LayoutParams(
                0,
                0,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSPARENT
            )
            viewToAdd.layoutParams = params
            mgr.addView(viewToAdd, params)
            mgr.removeView(viewToAdd)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}