package com.github.savan.touchlesskiosk.utils

import android.util.Log

object Logger {
    private const val APP_TAG = "TouchlessKiosk:"

    fun d(tag: String, message: String) {
        Log.d(APP_TAG + tag, message)
    }

    fun e(tag: String, message: String) {
        Log.e(APP_TAG + tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(APP_TAG + tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(APP_TAG + tag, message)
    }
}