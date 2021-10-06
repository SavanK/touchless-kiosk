package com.github.savan.touchlesskiosk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.text.TextUtils.SimpleStringSplitter
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.savan.touchlesskiosk.input.InputInjectionService
import com.github.savan.touchlesskiosk.utils.Logger
import com.github.savan.touchlesskiosk.webrtc.IRtcClient
import com.github.savan.touchlesskiosk.webrtc.model.Connection
import com.github.savan.touchlesskiosk.webrtc.model.Kiosk


class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"

        private const val REQUEST_CODE_SCREEN_CAPTURE = 1009
        private const val REQUEST_CODE_DRAW_OVER_OTHER_APPS = 1010
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
            kioskStreamStatusView.text = getString(R.string.kiosk_id) +
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
        override fun onKioskRegistered(kiosk: Kiosk) {
            Logger.d(TAG, "onKioskRegistered, kiosk: $kiosk")
            runOnUiThread {
                Toast.makeText(this@MainActivity,
                "Kiosk registered with server", Toast.LENGTH_SHORT).show()
                kioskStreamStatusView.text = getString(R.string.kiosk_id) + kiosk.kioskId
            }
        }

        override fun onKioskUnregistered(kiosk: Kiosk) {
            Logger.d(TAG, "onKioskUnregistered, kiosk: $kiosk")
            runOnUiThread {
                Toast.makeText(this@MainActivity,
                "Kiosk unregistered with server", Toast.LENGTH_SHORT).show()
                kioskStreamStatusView.text = getString(R.string.kiosk_id) + "null"
            }
        }

        @SuppressLint("SetTextI18n")
        override fun onConnectionEstablished(connection: Connection) {
            Logger.d(TAG, "onConnectionEstablished, connection: $connection")
            runOnUiThread {
                connectionStatusView.text = getString(R.string.connection_status) + "CONNECTED"
                activeCustomerView.text = getString(R.string.active_customer) +
                        connection.customer.customerId
            }
        }

        @SuppressLint("SetTextI18n")
        override fun onConnectionTeardown(connection: Connection) {
            Logger.d(TAG, "onConnectionTeardown, connection: $connection")
            runOnUiThread {
                connectionStatusView.text = getString(R.string.connection_status) + "NOT_CONNECTED"
                activeCustomerView.text = getString(R.string.active_customer) + "null"
            }
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
        if(!canDrawOverlays()) {
            requestDrawOverOtherAppsPermission()
        } else if(!isInputInjectionServiceEnabled()) {
            requestInputInjectionServiceToBeEnabled()
        }
        startTouchlessKioskService()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == Activity.RESULT_OK) {
            when(requestCode) {
                REQUEST_CODE_SCREEN_CAPTURE -> {
                    Logger.d(TAG, "onActivityResult, permission for screen capture granted")
                    data?.let { touchlessKiosk?.setupStreaming(it) }
                }
                REQUEST_CODE_DRAW_OVER_OTHER_APPS -> {
                    Logger.d(TAG, "onActivityResult, permission for draw over other apps granted")
                }
            }
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

    private fun requestDrawOverOtherAppsPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, REQUEST_CODE_DRAW_OVER_OTHER_APPS)
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
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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

    private fun isInputInjectionServiceEnabled(): Boolean {
        var accessibilityEnabled = 0
        val service = packageName + "/" + InputInjectionService::class.java.canonicalName
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
            Logger.d(TAG, "accessibilityEnabled = $accessibilityEnabled")
        } catch (e: SettingNotFoundException) {
            Logger.e(
                TAG, "Error finding setting, default accessibility to not found: "
                        + e.message
            )
        }
        val mStringColonSplitter = SimpleStringSplitter(':')

        if (accessibilityEnabled == 1) {
            Logger.d(TAG, "***ACCESSIBILITY IS ENABLED*** -----------------")
            val settingValue = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    Logger.d(
                        TAG,
                        "-------------- > accessibilityService :: $accessibilityService $service"
                    )
                    if (accessibilityService.equals(service, ignoreCase = true)) {
                        Logger.d(
                            TAG,
                            "We've found the correct setting - accessibility is switched on!"
                        )
                        return true
                    }
                }
            }
        } else {
            Logger.d(TAG, "***ACCESSIBILITY IS DISABLED***")
        }

        return false
    }

    private fun requestInputInjectionServiceToBeEnabled() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

}