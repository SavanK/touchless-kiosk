package com.github.savan.touchlesskiosk.utils

import android.content.Context

object StorageUtils {
    private const val PREFERENCE_FILE = "com.github.savan.touchlesskiosk.PREFERENCES"

    fun get(context: Context, key: String): String? {
        return context.getSharedPreferences(PREFERENCE_FILE, Context.MODE_PRIVATE)
            .getString(key, null)
    }

    fun set(context: Context, key: String, value: String) {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE, Context.MODE_PRIVATE)
            ?: return
        with (sharedPref.edit()) {
            putString(key, value)
            apply()
        }
    }
}