package com.github.savan.webapp.touchlesskiosk.model

import com.google.gson.Gson

const val REQUEST_CONNECT_KIOSK = "connect_kiosk"
const val REQUEST_DISCONNECT_KIOSK = "disconnect_kiosk"
const val REQUEST_REGISTER_KIOSK = "register_kiosk"
const val REQUEST_WEB_RTC_TRANSPORT = "web_rtc_transport"
const val REQUEST_MOUSE_EVENT = "mouse_event"

const val SUCCESS = "success"
const val FAILURE = "failure"

abstract class JsonParcelable {
    companion object {
        private val gson = Gson()
    }

    fun toJsonString(): String {
        return gson.toJson(this).toString()
    }
}

data class Connection(val customer: Customer, val kiosk: Kiosk): JsonParcelable()
data class Customer(val customerId: String): JsonParcelable()
data class Kiosk(val kioskId: String): JsonParcelable()
data class Request(val requestId: String, val payload: String, val webRtcPayload: String, val mouseEventPayload: String):
    JsonParcelable()
data class Response(val requestId: String, val result: String, val message: String, val payload: String,
                    val webRtcPayload: String): JsonParcelable()