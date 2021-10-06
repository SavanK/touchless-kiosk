package com.github.savan.touchlesskiosk.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.github.savan.touchlesskiosk.utils.Logger
import com.github.savan.touchlesskiosk.webrtc.model.MouseEvent
import java.util.*

class InputInjectionService: AccessibilityService() {
    companion object {
        private const val TAG = "InputInjectionService"
        private const val ACTION_INJECT_MOUSE_EVENT =
            "com.github.savan.touchlesskiosk.input.InputInjectionService"
        private const val KEY_MOUSE_EVENT = "mouse_event"

        fun createInjectMouseEventIntent(context: Context, mouseEvent: MouseEvent): Intent {
            return Intent(context, InputInjectionService::class.java).apply {
                action = ACTION_INJECT_MOUSE_EVENT
                putExtra(KEY_MOUSE_EVENT, mouseEvent)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // do nothing
    }

    override fun onInterrupt() {
        // do nothing
    }

    private val displayMetrics = DisplayMetrics()

    override fun onCreate() {
        super.onCreate()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action == ACTION_INJECT_MOUSE_EVENT) {
            val mouseEvent = intent.getParcelableExtra<MouseEvent>(KEY_MOUSE_EVENT)
            mouseEvent?.let { injectMouseEvent(it) }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private val queue: Queue<MouseEvent> = ArrayDeque()
    private var isDown = false

    private fun injectMouseEvent(mouseEvent: MouseEvent) {
        Logger.d(TAG, "injectMouseEvent, mouseEvent: $mouseEvent")

        if(mouseEvent.mouseEventType == MouseEvent.MOUSE_DOWN) {
            isDown = true
            queue.clear()
        }

        if(isDown) {
            queue.offer(mouseEvent)
            if (mouseEvent.mouseEventType == MouseEvent.MOUSE_UP) {
                dispatchGesture(createGesture(), null, null)
                queue.clear()
                isDown = false
            }
        }
    }

    private fun createGesture(): GestureDescription {
        Logger.d(TAG, "createGesture, queueSize: ${queue.size}")

        val clickPath = Path()
        var mouseEvent = queue.poll()
        var x = mouseEvent.x * displayMetrics.widthPixels
        var y = mouseEvent.y * displayMetrics.heightPixels
        clickPath.moveTo(x, y)
        while(!queue.isEmpty()) {
            mouseEvent = queue.poll()
            if(mouseEvent.mouseEventType == MouseEvent.MOUSE_MOVE) {
                x = mouseEvent.x * displayMetrics.widthPixels
                y = mouseEvent.y * displayMetrics.heightPixels
                if (x > 0 && y > 0) {
                    clickPath.lineTo(x, y)
                }
            }
        }

        val willContinue = false
        val gestureStroke = StrokeDescription(clickPath, 0, 100, willContinue)

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(gestureStroke)

        return gestureBuilder.build()
    }
}