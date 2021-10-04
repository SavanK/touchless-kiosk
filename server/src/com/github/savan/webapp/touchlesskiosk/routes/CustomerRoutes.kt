package com.github.savan.webapp.touchlesskiosk.routes

import com.github.savan.webapp.touchlesskiosk.*
import com.github.savan.webapp.touchlesskiosk.model.*
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
    suspend fun requestKioskConnection(session: WebSocketServerSession, request: Request) {
        println("/customer, request to connect: $request")
        val connection = getObject<Connection>(request.payload)

        when(val kioskStatus = getKioskStatus(connection.kiosk)) {
            KIOSKSTATUS.FREE -> {
                println("/customer, connecting to kiosk")
                // save customer session info
                saveCustomerSession(connection.customer, session)
                // send connection request to kiosk
                getKioskSocket(connection)?.send(
                    Request(REQUEST_CONNECT_KIOSK, connection.toJsonString(), "").toJsonString()
                )
                // Now wait for response on the kiosk connection
            }
            KIOSKSTATUS.BUSY, KIOSKSTATUS.NOT_FOUND -> {
                println("/customer, ${kioskStatus.text}")
                session.send(
                    Response(REQUEST_CONNECT_KIOSK, FAILURE, kioskStatus.text, "", "")
                        .toJsonString()
                )
                terminateWssSession(session)
            }
        }
    }

    suspend fun requestKioskDisconnection(session: WebSocketServerSession, request: Request) {
        println("/customer, request to disconnect: $request")
        val connection = getObject<Connection>(request.payload)
        when(val kioskStatus = getKioskStatus(connection.kiosk)) {
            KIOSKSTATUS.BUSY -> {
                if(isValidConnection(connection)) {
                    // kiosk assigned to this customer
                    println("/customer, disconnecting from kiosk")
                    // send disconnection request to kiosk
                    getKioskSocket(connection)?.send(
                        Request(
                            REQUEST_DISCONNECT_KIOSK,
                            connection.toJsonString(),
                            ""
                        ).toJsonString()
                    )
                    // Now wait for response on the kiosk connection
                } else {
                    // kiosk assigned to a different customer
                    println("/customer, kiosk assigned to a different customer")
                    session.send(
                        Response(REQUEST_DISCONNECT_KIOSK, FAILURE, kioskStatus.text, "", "")
                            .toJsonString()
                    )
                    terminateWssSession(session)
                }
            }
            KIOSKSTATUS.FREE, KIOSKSTATUS.NOT_FOUND -> {
                println("/customer, ${kioskStatus.text}")
                session.send(
                    Response(REQUEST_DISCONNECT_KIOSK, FAILURE, kioskStatus.text, "", "")
                        .toJsonString()
                )
                terminateWssSession(session)
            }
        }
    }

    suspend fun relayWebRtcRequestToKiosk(request: Request) {
        println("/customer, relayWebRtcRequestToKiosk: $request")
        val connection = getObject<Connection>(request.payload)
        getKioskSocket(connection)?.send(request.toJsonString())
    }

    suspend fun relayWebRtcResponseToKiosk(response: Response) {
        println("/customer, relayWebRtcResponseToKiosk: $response")
        val connection = getObject<Connection>(response.payload)
        getKioskSocket(connection)?.send(response.toJsonString())
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