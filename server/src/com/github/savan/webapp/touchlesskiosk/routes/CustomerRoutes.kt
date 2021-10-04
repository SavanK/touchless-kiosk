package com.github.savan.webapp.touchlesskiosk.routes

import com.github.savan.webapp.touchlesskiosk.activeConnections
import com.github.savan.webapp.touchlesskiosk.activeCustomers
import com.github.savan.webapp.touchlesskiosk.model.*
import com.github.savan.webapp.touchlesskiosk.registeredKiosks
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.html.*
import java.lang.Exception

fun Application.customerRoutes() {
    routing {
        customerWss()
        customerHttp()
    }
}

fun Route.customerHttp() {
    get("/connect") {
        val kioskId = call.request.queryParameters["kiosk"]
        if (!kioskId.isNullOrEmpty()) {
            call.respondHtml {
                body {
                    div {
                        p {
                            +"Kiosk: "
                            b {
                                +kioskId
                            }
                        }
                        button {
                            id = "connect"
                            +"Connect"
                        }
                        button {
                            id = "disconnect"
                            +"Disconnect"
                        }
                    }
                    video {
                        id = "remoteVideo"
                        autoPlay = true
                    }
                    script {
                        src = "../static/main.js"
                    }
                }
            }
        }
    }
}

@UseExperimental(KtorExperimentalAPI::class)
fun Route.customerWss() {
    suspend fun terminateWssSession(session: WebSocketServerSession) {
        session.flush()
        session.close(null)
    }

    suspend fun requestKioskConnection(session: WebSocketServerSession, request: Request) {
        println("/customer, request to connect: $request")
        val connection = getObject<Connection>(request.payload)
        if (!activeConnections.containsKey(connection.kiosk)) {
            println("/customer, kiosk is free")
            // this kiosk is free, no active connections
            // initiate connection to kiosk
            if(registeredKiosks[connection.kiosk] != null) {
                println("/customer, connecting to kiosk")
                // save customer session info
                activeCustomers[connection.customer] = session
                // send connection request to kiosk
                registeredKiosks[connection.kiosk]?.send(
                    Request(
                        REQUEST_CONNECT_KIOSK,
                        connection.toJsonString(),
                        ""
                    ).toJsonString()
                )
                // Now wait for response on the kiosk connection
            } else {
                println("/customer, kiosk not found")
                session.send(
                    Response(
                        REQUEST_CONNECT_KIOSK,
                        FAILURE,
                        "Kiosk not found",
                        "",
                        ""
                    ).toJsonString()
                )
                terminateWssSession(session)
            }
        } else {
            // this kiosk is being used by someone else, refuse new connection
            println("/customer, kiosk is being used by another customer")
            session.send(
                Response(
                    REQUEST_CONNECT_KIOSK,
                    FAILURE,
                    "Kiosk is being used by another customer",
                    "",
                    ""
                ).toJsonString()
            )
            terminateWssSession(session)
        }
    }

    suspend fun requestKioskDisconnection(session: WebSocketServerSession, request: Request) {
        println("/customer, request to disconnect: $request")
        val connection = getObject<Connection>(request.payload)
        if (activeConnections.containsKey(connection.kiosk) &&
            activeConnections[connection.kiosk] == connection.customer
        ) {
            println("/customer, assigned kiosk found")
            // this kiosk is being currently assigned to this customer
            // initiate disconnection to kiosk
            if(registeredKiosks[connection.kiosk] != null) {
                println("/customer, connecting to kiosk")
                // send disconnection request to kiosk
                registeredKiosks[connection.kiosk]?.send(
                    Request(
                        REQUEST_DISCONNECT_KIOSK,
                        connection.toJsonString(),
                        ""
                    ).toJsonString()
                )
                // Now wait for response on the kiosk connection
            } else {
                println("/customer, kiosk not found")
                session.send(
                    Response(
                        REQUEST_DISCONNECT_KIOSK,
                        FAILURE,
                        "Kiosk not found",
                        "",
                        ""
                    ).toJsonString()
                )
                terminateWssSession(session)
            }
        } else {
            // this kiosk is being used by someone else, refuse teardown
            println("/customer, kiosk is being used by another customer")
            session.send(
                Response(
                    REQUEST_DISCONNECT_KIOSK,
                    FAILURE,
                    "Kiosk is being used by another customer",
                    "",
                    ""
                ).toJsonString()
            )
            terminateWssSession(session)
        }
    }

    suspend fun relayWebRtcRequestToKiosk(request: Request) {
        println("/customer, request web rtc transport: $request")
        val connection = getObject<Connection>(request.payload)
        if (activeConnections.containsKey(connection.kiosk) &&
            activeConnections[connection.kiosk] == connection.customer
        ) {
            println("/customer, assigned kiosk found")

            registeredKiosks[connection.kiosk]?.send(request.toJsonString())
        }
    }

    suspend fun relayWebRtcResponseToKiosk(response: Response) {
        // response for connect request
        println("/customer, request webrtc transport: $response")
        val connection = getObject<Connection>(response.payload)
        registeredKiosks[connection.kiosk]?.send(response.toJsonString())
    }

    webSocket(path = "/customer") {
        try {
            for (data in incoming) {
                if (data is Frame.Text) {
                    val text = data.readText()
                    println("/customer, text: $text")
                    when(getClassOfObject(text)) {
                        Request::class -> {
                            val request = getObject<Request>(text)
                            when (request.requestId) {
                                REQUEST_CONNECT_KIOSK -> {
                                    requestKioskConnection(this, request)
                                }
                                REQUEST_DISCONNECT_KIOSK -> {
                                    requestKioskDisconnection(this, request)
                                }
                                REQUEST_WEB_RTC_TRANSPORT -> {
                                    relayWebRtcRequestToKiosk(request)
                                }
                            }
                        }
                        Response::class -> {
                            val response = getObject<Response>(text)
                            when (response.requestId) {
                                REQUEST_WEB_RTC_TRANSPORT -> {
                                    relayWebRtcResponseToKiosk(response)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("/customer: ${e.printStackTrace()}")
        }
    }
}