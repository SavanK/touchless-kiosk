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

fun Application.connectKioskRoutes() {
    routing {
        connectKioskWss()
        connectKioskHttp()
    }
}

fun Route.connectKioskHttp() {
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
fun Route.connectKioskWss() {
    suspend fun terminateWssSession(session: WebSocketServerSession) {
        session.flush()
        session.close(null)
    }

    webSocket(path = "/connectkiosk") {
        //println("/connectkiosk")
        try {
            //while (true) {
            for (data in incoming) {
                if (data is Frame.Text) {
                    val text = data.readText()
                    println("/connectkiosk, text: $text")
                    when(getClassOfObject(text)) {
                        Request::class -> {
                            val request = getGson().fromJson<Request>(text, Request::class.java)
                            when (request.requestId) {
                                REQUEST_CONNECT_KIOSK -> {
                                    println("/connectkiosk, request to connect: $request")
                                    val connection = getGson().fromJson<Connection>(
                                        request.payload,
                                        Connection::class.java
                                    )
                                    if (!activeConnections.containsKey(connection.kiosk)) {
                                        println("/connectkiosk, kiosk is free")
                                        // this kiosk is free, no active connections
                                        // initiate connection to kiosk
                                        if(registeredKiosks[connection.kiosk] != null) {
                                            println("/connectkiosk, connecting to kiosk")
                                            // save customer session info
                                            activeCustomers[connection.customer] = this
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
                                            println("/connectkiosk, kiosk not found")
                                            send(
                                                Response(
                                                    REQUEST_CONNECT_KIOSK,
                                                    FAILURE,
                                                    "Kiosk not found",
                                                    "",
                                                    ""
                                                ).toJsonString()
                                            )
                                            terminateWssSession(this)
                                        }
                                    } else {
                                        // this kiosk is being used by someone else, refuse new connection
                                        println("/connectkiosk, kiosk is being used by another customer")
                                        send(
                                            Response(
                                                REQUEST_CONNECT_KIOSK,
                                                FAILURE,
                                                "Kiosk is being used by another customer",
                                                "",
                                                ""
                                            ).toJsonString()
                                        )
                                        terminateWssSession(this)
                                    }
                                }
                                REQUEST_DISCONNECT_KIOSK -> {
                                    println("/connectkiosk, request to disconnect: $request")
                                    val connection = getGson().fromJson<Connection>(
                                        request.payload,
                                        Connection::class.java
                                    )
                                    if (activeConnections.containsKey(connection.kiosk) &&
                                        activeConnections[connection.kiosk] == connection.customer
                                    ) {
                                        println("/connectkiosk, assigned kiosk found")
                                        // this kiosk is being currently assigned to this customer
                                        // initiate disconnection to kiosk
                                        if(registeredKiosks[connection.kiosk] != null) {
                                            println("/connectkiosk, connecting to kiosk")
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
                                            println("/connectkiosk, kiosk not found")
                                            send(
                                                Response(
                                                    REQUEST_DISCONNECT_KIOSK,
                                                    FAILURE,
                                                    "Kiosk not found",
                                                    "",
                                                    ""
                                                ).toJsonString()
                                            )
                                            terminateWssSession(this)
                                        }
                                    } else {
                                        // this kiosk is being used by someone else, refuse teardown
                                        println("/connectkiosk, kiosk is being used by another customer")
                                        send(
                                            Response(
                                                REQUEST_DISCONNECT_KIOSK,
                                                FAILURE,
                                                "Kiosk is being used by another customer",
                                                "",
                                                ""
                                            ).toJsonString()
                                        )
                                        terminateWssSession(this)
                                    }
                                }
                                REQUEST_WEB_RTC_TRANSPORT -> {
                                    println("/connectkiosk, request web rtc transport: $request")
                                    val connection = getGson().fromJson<Connection>(
                                        request.payload,
                                        Connection::class.java
                                    )
                                    if (activeConnections.containsKey(connection.kiosk) &&
                                        activeConnections[connection.kiosk] == connection.customer
                                    ) {
                                        println("/connectkiosk, assigned kiosk found")

                                        registeredKiosks[connection.kiosk]?.send(request.toJsonString())
                                    }
                                }
                            }
                        }
                        Response::class -> {
                            val response = getGson().fromJson<Response>(text, Response::class.java)
                            when (response.requestId) {
                                REQUEST_WEB_RTC_TRANSPORT -> {
                                    // response for connect request
                                    println("/connectkiosk, request webrtc transport: $response")
                                    val connection =
                                        getGson().fromJson<Connection>(response.payload, Connection::class.java)
                                    registeredKiosks[connection.kiosk]?.send(response.toJsonString())
                                }
                            }
                        }
                    }
                }
            }
            //}
        } catch (e: Exception) {
            println("/connectkiosk: ${e.printStackTrace()}")
        }
    }
}