package com.github.savan.touchlesskiosk.webrtc

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Point
import android.media.projection.MediaProjection
import android.view.WindowManager
import com.github.savan.touchlesskiosk.utils.Logger
import org.webrtc.*

class RtcClient(val context: Application, observer: PeerConnection.Observer): IRtcClient {

    companion object {
        private const val TAG = "RtcClient"

        private const val SCREEN_RESOLUTION_SCALE = 2

        private const val LOCAL_TRACK_ID = "local_track"
        private const val LOCAL_STREAM_ID = "local_track"
    }

    private val rootEglBase: EglBase = EglBase.create()

    init {
        initPeerConnectionFactory(context)
    }

    private val iceServer = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                    .createIceServer()
    )

    private val peerConnectionFactory by lazy { buildPeerConnectionFactory() }
    private var videoCapturer: ScreenCapturerAndroid? = null
    private var videoStream: MediaStream? = null
    private var videoTrack: VideoTrack? = null
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(true) }
    private val peerConnection by lazy { buildPeerConnection(observer) }
    private var streamStatus: IRtcClient.IStreamListener.STATUS =
        IRtcClient.IStreamListener.STATUS.NOT_READY_TO_STREAM
    private val streamListeners: MutableSet<IRtcClient.IStreamListener> = mutableSetOf()

    private fun initPeerConnectionFactory(context: Application) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory
                .builder()
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
                .createPeerConnectionFactory()
    }

    private fun buildPeerConnection(observer: PeerConnection.Observer) = peerConnectionFactory.createPeerConnection(
            iceServer,
            observer
    )

    override fun registerStreamListener(listener: IRtcClient.IStreamListener) {
        Logger.d(TAG, "registerStreamListener")
        streamListeners.add(listener)
    }

    override fun unregisterStreamListener(listener: IRtcClient.IStreamListener) {
        Logger.d(TAG, "unregisterStreamListener")
        streamListeners.remove(listener)
    }

    override fun setupStreaming(data: Intent) {
        Logger.d(TAG, "setupStreaming, streamStatus: $streamStatus")
        if(streamStatus == IRtcClient.IStreamListener.STATUS.NOT_READY_TO_STREAM) {
            videoCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
                override fun onStop() {
                    Logger.d(TAG, "setupStreaming, user revoked permission")
                }
            })

            videoCapturer?.initialize(
                SurfaceTextureHelper.create(
                    Thread.currentThread().name, rootEglBase.eglBaseContext,
                    true
                ),
                context,
                localVideoSource.capturerObserver
            )
            val windowMeasurement = getWindowMeasurement()
            videoCapturer?.startCapture(
                windowMeasurement.first / SCREEN_RESOLUTION_SCALE,
                windowMeasurement.second / SCREEN_RESOLUTION_SCALE, 30
            )
            videoTrack = peerConnectionFactory.createVideoTrack(
                LOCAL_TRACK_ID,
                localVideoSource
            )
            videoTrack?.setEnabled(true)
            videoStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID)
            videoStream?.addTrack(videoTrack)

            streamStatus = IRtcClient.IStreamListener.STATUS.READY_TO_STREAM
        }
    }

    override fun startStreaming(sdpObserver: SdpObserver) {
        Logger.d(TAG, "startStreaming, streamStatus: $streamStatus")
        if(streamStatus == IRtcClient.IStreamListener.STATUS.READY_TO_STREAM) {
            peerConnection?.addStream(videoStream)
            peerConnection?.call(sdpObserver)
            streamStatus = IRtcClient.IStreamListener.STATUS.STREAMING
        }
    }

    override fun teardownStreaming() {
        Logger.d(TAG, "teardownStreaming, streamStatus: $streamStatus")
        stopStreaming()
        if(streamStatus == IRtcClient.IStreamListener.STATUS.READY_TO_STREAM) {
            videoTrack?.setEnabled(false)
            videoStream?.removeTrack(videoTrack)

            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null

            streamStatus = IRtcClient.IStreamListener.STATUS.NOT_READY_TO_STREAM
        }
    }

    override fun stopStreaming() {
        Logger.d(TAG, "stopStreaming, streamStatus: $streamStatus")
        if(streamStatus == IRtcClient.IStreamListener.STATUS.STREAMING) {
            peerConnection?.removeStream(videoStream)

            streamStatus = IRtcClient.IStreamListener.STATUS.NOT_READY_TO_STREAM
        }
    }

    override fun getStreamingStatus(): IRtcClient.IStreamListener.STATUS {
        return streamStatus
    }

    private fun getWindowMeasurement(): Pair<Int, Int> {
        // display metrics
        val density = Resources.getSystem().displayMetrics.densityDpi
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay

        val windowSize = Point()
        display.getRealSize(windowSize)

        val width = windowSize.x
        val height = windowSize.y

        return Pair(width, height)
    }

    private fun PeerConnection.call(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        createOffer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {

                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                    }

                    override fun onSetSuccess() {
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                    }

                    override fun onCreateFailure(p0: String?) {
                    }
                }, desc)
                sdpObserver.onCreateSuccess(desc)
            }
        }, constraints)
    }

    override fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetFailure(p0: String?) {
            }

            override fun onSetSuccess() {
            }

            override fun onCreateSuccess(p0: SessionDescription?) {
            }

            override fun onCreateFailure(p0: String?) {
            }
        }, sessionDescription)
    }

    override fun addIceCandidate(iceCandidate: IceCandidate?) {
        peerConnection?.addIceCandidate(iceCandidate)
    }
}