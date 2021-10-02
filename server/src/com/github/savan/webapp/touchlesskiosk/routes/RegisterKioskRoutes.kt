package com.github.savan.webapp.touchlesskiosk.routes

import com.github.savan.webapp.touchlesskiosk.activeConnections
import com.github.savan.webapp.touchlesskiosk.activeCustomers
import com.github.savan.webapp.touchlesskiosk.model.*
import com.github.savan.webapp.touchlesskiosk.registeredKiosks
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.async
import java.lang.Exception

fun Application.registerKioskRoutes() {
    routing {
        registerKiosk()
    }
}

@UseExperimental(KtorExperimentalAPI::class)
fun Route.registerKiosk() {
    fun terminateWssSession(session: WebSocketServerSession) {
        application.async {
            session.flush()
            session.close(null)
        }
    }

    webSocket(path = "/registerkiosk") {
        try {
            for (data in incoming) {
                if (data is Frame.Text) {
                    val text = data.readText()
                    //println("/registerkiosk, text: $text")
                    when(getClassOfObject(text)) {
                        Request::class -> {
                            val request = getGson().fromJson<Request>(text, Request::class.java)
                            when (request.requestId) {
                                REQUEST_REGISTER_KIOSK -> {
                                    // request to register new kiosk
                                    println("/registerkiosk, request to register: $request")
                                    val kiosk = getGson().fromJson<Kiosk>(request.payload, Kiosk::class.java)
                                    registeredKiosks[kiosk] = this
                                    println("/registerkiosk, kiosk: $kiosk registered")
                                    send(
                                        Response(
                                            REQUEST_REGISTER_KIOSK,
                                            SUCCESS,
                                            "",
                                            "",
                                            ""
                                        ).toJsonString())
                                }
                                REQUEST_WEB_RTC_TRANSPORT -> {
                                    println("/registerkiosk, request web rtc transport: $request")
                                    val connection = getGson().fromJson<Connection>(
                                        request.payload,
                                        Connection::class.java
                                    )
                                    if (activeConnections.containsKey(connection.kiosk) &&
                                        activeConnections[connection.kiosk] == connection.customer) {
                                        println("/registerkiosk, assigned kiosk found")

                                        activeCustomers[connection.customer]?.send(request.toJsonString())
                                    }
                                }
                            }
                        }
                        Response::class -> {
                            println("/registerkiosk, type response")
                            val response = getGson().fromJson<Response>(text, Response::class.java)
                            when (response.requestId) {
                                REQUEST_CONNECT_KIOSK -> {
                                    // response for connect request
                                    println("/registerkiosk, response for connection: $response")
                                    val connection =
                                        getGson().fromJson<Connection>(response.payload, Connection::class.java)
                                    if (response.result == SUCCESS) {
                                        activeConnections[connection.kiosk] = connection.customer
                                        activeCustomers[connection.customer]?.send(
                                            Response(
                                                REQUEST_CONNECT_KIOSK,
                                                SUCCESS,
                                                "",
                                                connection.toJsonString(),
                                                ""
                                            ).toJsonString()
                                        )
                                    } else {
                                        activeCustomers[connection.customer]?.send(
                                            Response(
                                                REQUEST_CONNECT_KIOSK,
                                                FAILURE,
                                                "Kiosk rejected, unknown error",
                                                "",
                                                ""
                                            ).toJsonString()
                                        )
                                        activeCustomers[connection.customer]?.let {
                                            terminateWssSession(it)
                                            activeCustomers.remove(connection.customer)
                                        }
                                    }
                                }
                                REQUEST_DISCONNECT_KIOSK -> {
                                    // response for connect request
                                    println("/registerkiosk, response for disconnection: $response")
                                    val connection =
                                        getGson().fromJson<Connection>(response.payload, Connection::class.java)
                                    if (response.result == SUCCESS) {
                                        activeConnections.remove(connection.kiosk)
                                        activeCustomers[connection.customer]?.send(
                                            Response(
                                                REQUEST_DISCONNECT_KIOSK,
                                                SUCCESS,
                                                "",
                                                connection.toJsonString(),
                                                ""
                                            ).toJsonString()
                                        )
                                        activeCustomers[connection.customer]?.let {
                                            terminateWssSession(it)
                                            activeCustomers.remove(connection.customer)
                                        }
                                    } else {
                                        activeConnections.remove(connection.kiosk)
                                        activeCustomers[connection.customer]?.send(
                                            Response(
                                                REQUEST_DISCONNECT_KIOSK,
                                                FAILURE,
                                                "Kiosk rejected, unknown error",
                                                "",
                                                ""
                                            ).toJsonString()
                                        )
                                    }
                                    activeCustomers[connection.customer]?.let {
                                        terminateWssSession(it)
                                        activeCustomers.remove(connection.customer)
                                    }
                                }
                                REQUEST_WEB_RTC_TRANSPORT -> {
                                    // response for connect request
                                    println("/registerkiosk, request webrtc transport: $response")
                                    val connection =
                                        getGson().fromJson<Connection>(response.payload, Connection::class.java)
                                    activeCustomers[connection.customer]?.send(response.toJsonString())
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("/registerkiosk: ${e.printStackTrace()}")
        }
    }
}