package com.github.savan.webapp.touchlesskiosk.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlin.reflect.KClass

private val gson = Gson()

fun getGson(): Gson {
    return gson
}

fun getClassOfObject(data: String): KClass<*> {
    val jsonObject = gson.fromJson(data, JsonObject::class.java)
    if(jsonObject.has("requestId") && jsonObject.has("payload")
        && jsonObject.has("webRtcPayload")) {
        return if(jsonObject.has("result") && jsonObject.has("message")) {
            Response::class
        } else {
            Request::class
        }
    }
    return Any::class
}

fun getConnection(request: Request): Connection {
    return gson.fromJson<Connection>(request.payload, Connection::class.java)
}
