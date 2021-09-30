package com.github.savan.touchlesskiosk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.github.savan.touchlesskiosk.utils.Logger
import com.github.savan.touchlesskiosk.webrtc.IRtcClient

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"

        private const val REQUEST_CODE_SCREEN_CAPTURE = 1009
    }

    private lateinit var connectionStatusView: TextView
    private lateinit var kioskStreamStatusView: TextView
    private lateinit var screenCaptureButton: Button
    private lateinit var activeCustomerView: TextView
    private lateinit var kioskRegistrationButton: Button

    private var touchlessKiosk: ITouchlessKiosk? = null

    private val streamListener = object: IStreamListener.Stub() {
        @SuppressLint("SetTextI18n")
        override fun onStatusChange(status: Int) {
            Logger.d(TAG, "onStatusChange, status: $status")
            kioskStreamStatusView.text = getString(R.string.kiosk_stream_status) +
                    getStreamStatusString(status)
        }

        private fun getStreamStatusString(status: Int): String {
            return when(status) {
                IStreamListener.STATUS_NOT_READY_TO_STREAM -> {
                    "Not ready to stream"
                }
                IStreamListener.STATUS_READY_TO_STREAM -> {
                    "Ready to stream"
                }
                IStreamListener.STATUS_STREAMING -> {
                    "Streaming"
                }
                else -> {
                    "Not ready to stream"
                }
            }
        }
    }

    private val kioskListener = object : IKioskListener.Stub() {
        override fun onKioskRegistered() {
            Logger.d(TAG, "onKioskRegistered")
            // TODO switch to UI thread
            /*Toast.makeText(this@MainActivity, "Kiosk registered with server",
                Toast.LENGTH_SHORT).show()*/
        }

        override fun onKioskUnregistered() {
            Logger.d(TAG, "onKioskUnregistered")
            /*Toast.makeText(this@MainActivity, "Kiosk unregistered with server",
                Toast.LENGTH_SHORT).show()*/
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectionStatusView = findViewById(R.id.connection_status)
        kioskStreamStatusView = findViewById(R.id.kiosk_stream_status)
        screenCaptureButton = findViewById(R.id.screen_capture_button)
        activeCustomerView = findViewById(R.id.active_customer)
        kioskRegistrationButton = findViewById(R.id.kiosk_reg_button)

        screenCaptureButton.setOnClickListener {
            toggleScreenCapture()
        }

        kioskRegistrationButton.setOnClickListener {
            touchlessKiosk?.registerKiosk()
        }
    }

    override fun onResume() {
        super.onResume()
        startTouchlessKioskService()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == REQUEST_CODE_SCREEN_CAPTURE && resultCode == Activity.RESULT_OK) {
            Logger.d(TAG, "onActivityResult, permission for screen capture granted")
            data?.let { touchlessKiosk?.setupStreaming(it) }
        }
    }

    private fun startTouchlessKioskService() {
        if(touchlessKiosk == null) {
            val intent = Intent(this, TouchlessKioskService::class.java)
            bindService(intent, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    touchlessKiosk = service as ITouchlessKiosk
                    touchlessKiosk?.registerStreamListener(streamListener)
                    touchlessKiosk?.registerKioskListener(kioskListener)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    touchlessKiosk = null
                }

            }, Context.BIND_AUTO_CREATE)
        }
    }

    private fun toggleScreenCapture() {
        Logger.d(TAG, "toggleScreenCapture")
        if(touchlessKiosk == null) {
            Logger.d(TAG, "toggleScreenCapture, touchlessKiosk is null")
            return
        }

        if(touchlessKiosk?.streamingStatus ==
            IRtcClient.IStreamListener.STATUS.NOT_READY_TO_STREAM.code) {
            Logger.d(TAG, "toggleScreenCapture, request permission for screen recording")
            val mediaProjection = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager
            val screenCaptureIntent = mediaProjection.createScreenCaptureIntent()
            startActivityForResult(screenCaptureIntent, REQUEST_CODE_SCREEN_CAPTURE)
        } else {
            Logger.d(TAG, "toggleScreenCapture, stop screen recording")
            touchlessKiosk?.teardownStreaming()
        }
    }
}