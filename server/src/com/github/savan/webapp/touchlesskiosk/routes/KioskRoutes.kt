package com.github.savan.webapp.touchlesskiosk.routes

import com.github.savan.webapp.touchlesskiosk.*
import com.github.savan.webapp.touchlesskiosk.model.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.websocket.*
import java.lang.Exception

fun Application.kioskRoutes() {
    routing {
        kioskWss()
    }
}

@UseExperimental(KtorExperimentalAPI::class)
fun Route.kioskWss() {
    suspend fun terminateWssSession(session: WebSocketServerSession) {
        session.flush()
        session.close(null)
    }

    suspend fun registerKiosk(session: WebSocketServerSession, request: Request) {
        // request to register new kiosk
        println("/kiosk, request to register: $request")
        val kiosk = getObject<Kiosk>(request.payload)
        saveKioskSession(kiosk, session)
        println("/kiosk, kiosk: $kiosk registered")
        session.send(
            Response(REQUEST_REGISTER_KIOSK, SUCCESS, "", "", "").toJsonString()
        )
    }

    suspend fun relayWebRtcRequestToCustomer(request: Request) {
        println("/kiosk, request web rtc transport: $request")
        val connection = getObject<Connection>(request.payload)
        getCustomerSocket(connection)?.send(request.toJsonString())
    }

    suspend fun processConnectKioskResponse(response: Response) {
        // response for connect request
        println("/kiosk, response for connection: $response")
        val connection = getObject<Connection>(response.payload)
        if (response.result == SUCCESS) {
            saveConnection(connection)
            getCustomerSocket(connection)?.send(
                Response(REQUEST_CONNECT_KIOSK, SUCCESS, "", connection.toJsonString(), "")
                    .toJsonString()
            )
        } else {
            getCustomerSocket(connection)?.send(
                Response(REQUEST_CONNECT_KIOSK, FAILURE, "Kiosk rejected, unknown error", "", "")
                    .toJsonString()
            )
            discardCustomerSession(connection.customer)
        }
    }

    suspend fun processDisconnectKioskResponse(response: Response) {
        // response for connect request
        println("/kiosk, response for disconnection: $response")
        val connection = getObject<Connection>(response.payload)
        getCustomerSocket(connection)?.send(
            Response(REQUEST_DISCONNECT_KIOSK, SUCCESS, "", connection.toJsonString(), "")
                .toJsonString()
        )

        breakConnection(connection)
        discardCustomerSession(connection.customer)
    }

    suspend fun relayWebRtcResponseToCustomer(response: Response) {
        // response for connect request
        println("/kiosk, request webrtc transport: $response")
        val connection = getObject<Connection>(response.payload)
        getCustomerSocket(connection)?.send(response.toJsonString())
    }

    webSocket(path = "/kiosk") {
        try {
            for (data in incoming) {
                if (data is Frame.Text) {
                    val text = data.readText()
                    when(getClassOfObject(text)) {
                        Request::class -> {
                            val request = getObject<Request>(text)
                            when (request.requestId) {
                                REQUEST_REGISTER_KIOSK -> {
                                    registerKiosk(this, request)
                                }
                                REQUEST_WEB_RTC_TRANSPORT -> {
                                    relayWebRtcRequestToCustomer(request)
                                }
                            }
                        }
                        Response::class -> {
                            println("/kiosk, type response")
                            val response = getObject<Response>(text)
                            when (response.requestId) {
                                REQUEST_CONNECT_KIOSK -> {
                                    processConnectKioskResponse(response)
                                }
                                REQUEST_DISCONNECT_KIOSK -> {
                                    processDisconnectKioskResponse(response)
                                }
                                REQUEST_WEB_RTC_TRANSPORT -> {
                                    relayWebRtcResponseToCustomer(response)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("/kiosk: ${e.printStackTrace()}")
        }
    }
}