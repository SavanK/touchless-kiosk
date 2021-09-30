package com.samsung.webapp.touchlesskiosk

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.application.*
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.gson.gson
import io.ktor.html.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.websocket.WebSocketServerSession
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.html.*
import java.lang.Exception
import java.time.Duration
import java.util.*
import kotlin.reflect.KClass

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

data class Connection(val customer: Customer, val kiosk: Kiosk)
data class Customer(val customerId: String)
data class Kiosk(val kioskId: String)
data class Request(val requestId: String, val payload: String, val webRtcPayload: String)
data class Response(
    val requestId: String, val result: String, val message: String, val payload: String,
    val webRtcPayload: String
)

private const val SUCCESS = "success"
private const val FAILURE = "failure"

private const val REQUEST_REGISTER_KIOSK = "register_kiosk"
private const val REQUEST_CONNECT_KIOSK = "connect_kiosk"
private const val REQUEST_DISCONNECT_KIOSK = "disconnect_kiosk"
private const val REQUEST_WEB_RTC_TRANSPORT = "web_rtc_transport"

val gson = Gson()

private fun getClassOfObject(data: String): KClass<*> {
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

private fun toJsonString(obj: Any): String {
    return gson.toJson(obj).toString()
}

@UseExperimental(KtorExperimentalAPI::class)
private suspend fun terminateWssSession(session: WebSocketServerSession) {
    session.flush()
    session.close(null)
}

@UseExperimental(KtorExperimentalAPI::class)
@ExperimentalCoroutinesApi
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module() {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }

    install(CallLogging)

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(ContentNegotiation) {
        gson {
        }
    }

    val registeredKiosks = Collections.synchronizedMap(mutableMapOf<Kiosk, WebSocketServerSession>())
    val activeCustomers = Collections.synchronizedMap(mutableMapOf<Customer, WebSocketServerSession>())
    val activeConnections = Collections.synchronizedMap(mutableMapOf<Kiosk, Customer>())

    routing {
        static("/static") {
           files("js")
        }

        get("/connect") {
            val kioskId = call.request.queryParameters["kiosk"]
            if (!kioskId.isNullOrEmpty()) {
                call.respondHtml {
                    body {
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
                                    val request = gson.fromJson<Request>(text, Request::class.java)
                                    when (request.requestId) {
                                        REQUEST_CONNECT_KIOSK -> {
                                            println("/connectkiosk, request to connect: $request")
                                            val connection = gson.fromJson<Connection>(
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
                                                        toJsonString(
                                                            Request(
                                                                REQUEST_CONNECT_KIOSK,
                                                                toJsonString(connection),
                                                                ""
                                                            )
                                                        )
                                                    )
                                                    // Now wait for response on the kiosk connection
                                                } else {
                                                    println("/connectkiosk, kiosk not found")
                                                    send(
                                                        toJsonString(
                                                            Response(
                                                                REQUEST_CONNECT_KIOSK,
                                                                FAILURE,
                                                                "Kiosk not found",
                                                                "",
                                                                ""
                                                            )
                                                        )
                                                    )
                                                    terminateWssSession(this)
                                                }
                                            } else {
                                                // this kiosk is being used by someone else, refuse new connection
                                                println("/connectkiosk, kiosk is being used by another customer")
                                                send(
                                                    toJsonString(
                                                        Response(
                                                            REQUEST_CONNECT_KIOSK,
                                                            FAILURE,
                                                            "Kiosk is being used by another customer",
                                                            "",
                                                            ""
                                                        )
                                                    )
                                                )
                                                terminateWssSession(this)
                                            }
                                        }
                                        REQUEST_DISCONNECT_KIOSK -> {
                                            println("/connectkiosk, request to disconnect: $request")
                                            val connection = gson.fromJson<Connection>(
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
                                                        toJsonString(
                                                            Request(
                                                                REQUEST_DISCONNECT_KIOSK,
                                                                toJsonString(connection),
                                                                ""
                                                            )
                                                        )
                                                    )
                                                    // Now wait for response on the kiosk connection
                                                } else {
                                                    println("/connectkiosk, kiosk not found")
                                                    send(
                                                        toJsonString(
                                                            Response(
                                                                REQUEST_DISCONNECT_KIOSK,
                                                                FAILURE,
                                                                "Kiosk not found",
                                                                "",
                                                                ""
                                                            )
                                                        )
                                                    )
                                                    terminateWssSession(this)
                                                }
                                            } else {
                                                // this kiosk is being used by someone else, refuse teardown
                                                println("/connectkiosk, kiosk is being used by another customer")
                                                send(
                                                    toJsonString(
                                                        Response(
                                                            REQUEST_DISCONNECT_KIOSK,
                                                            FAILURE,
                                                            "Kiosk is being used by another customer",
                                                            "",
                                                            ""
                                                        )
                                                    )
                                                )
                                                terminateWssSession(this)
                                            }
                                        }
                                        REQUEST_WEB_RTC_TRANSPORT -> {
                                            println("/connectkiosk, request web rtc transport: $request")
                                            val connection = gson.fromJson<Connection>(
                                                request.payload,
                                                Connection::class.java
                                            )
                                            if (activeConnections.containsKey(connection.kiosk) &&
                                                activeConnections[connection.kiosk] == connection.customer
                                            ) {
                                                println("/connectkiosk, assigned kiosk found")

                                                registeredKiosks[connection.kiosk]?.send(toJsonString(request))
                                            }
                                        }
                                    }
                                }
                                Response::class -> {
                                    val response = gson.fromJson<Response>(text, Response::class.java)
                                    when (response.requestId) {
                                        REQUEST_WEB_RTC_TRANSPORT -> {
                                            // response for connect request
                                            println("/connectkiosk, request webrtc transport: $response")
                                            val connection =
                                                gson.fromJson<Connection>(response.payload, Connection::class.java)
                                            registeredKiosks[connection.kiosk]?.send(toJsonString(response))
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
        webSocket(path = "/registerkiosk") {
            try {
                //while (true) {
                    for (data in incoming) {
                        if (data is Frame.Text) {
                            val text = data.readText()
                            //println("/registerkiosk, text: $text")
                            when(getClassOfObject(text)) {
                                Request::class -> {
                                    val request = gson.fromJson<Request>(text, Request::class.java)
                                    when (request.requestId) {
                                        REQUEST_REGISTER_KIOSK -> {
                                            // request to register new kiosk
                                            println("/registerkiosk, request to register: $request")
                                            val kiosk = gson.fromJson<Kiosk>(request.payload, Kiosk::class.java)
                                            registeredKiosks[kiosk] = this
                                            println("/registerkiosk, kiosk: $kiosk registered")
                                            send(toJsonString(Response(REQUEST_REGISTER_KIOSK, SUCCESS, "", "", "")))
                                        }
                                        REQUEST_WEB_RTC_TRANSPORT -> {
                                            println("/registerkiosk, request web rtc transport: $request")
                                            val connection = gson.fromJson<Connection>(
                                                request.payload,
                                                Connection::class.java
                                            )
                                            if (activeConnections.containsKey(connection.kiosk) &&
                                                activeConnections[connection.kiosk] == connection.customer) {
                                                println("/registerkiosk, assigned kiosk found")

                                                activeCustomers[connection.customer]?.send(toJsonString(request))
                                            }
                                        }
                                    }
                                }
                                Response::class -> {
                                    println("/registerkiosk, type response")
                                    val response = gson.fromJson<Response>(text, Response::class.java)
                                    when (response.requestId) {
                                        REQUEST_CONNECT_KIOSK -> {
                                            // response for connect request
                                            println("/registerkiosk, response for connection: $response")
                                            val connection =
                                                gson.fromJson<Connection>(response.payload, Connection::class.java)
                                            if (response.result == SUCCESS) {
                                                activeConnections[connection.kiosk] = connection.customer
                                                activeCustomers[connection.customer]?.send(
                                                    toJsonString(
                                                        Response(
                                                            REQUEST_CONNECT_KIOSK,
                                                            SUCCESS,
                                                            "",
                                                            toJsonString(connection),
                                                            ""
                                                        )
                                                    )
                                                )
                                            } else {
                                                activeCustomers[connection.customer]?.send(
                                                    toJsonString(
                                                        Response(
                                                            REQUEST_CONNECT_KIOSK,
                                                            FAILURE,
                                                            "Kiosk rejected, unknown error",
                                                            "",
                                                            ""
                                                        )
                                                    )
                                                )
                                                activeCustomers[connection.customer]?.let { terminateWssSession(it) }
                                            }
                                        }
                                        REQUEST_DISCONNECT_KIOSK -> {
                                            // response for connect request
                                            println("/registerkiosk, response for disconnection: $response")
                                            val connection =
                                                gson.fromJson<Connection>(response.payload, Connection::class.java)
                                            if (response.result == SUCCESS) {
                                                activeConnections.remove(connection.kiosk)
                                                activeCustomers[connection.customer]?.send(
                                                    toJsonString(
                                                        Response(
                                                            REQUEST_DISCONNECT_KIOSK,
                                                            SUCCESS,
                                                            "",
                                                            toJsonString(connection),
                                                            ""
                                                        )
                                                    )
                                                )
                                                activeCustomers[connection.customer]?.let { terminateWssSession(it) }
                                            } else {
                                                activeConnections.remove(connection.kiosk)
                                                activeCustomers[connection.customer]?.send(
                                                    toJsonString(
                                                        Response(
                                                            REQUEST_DISCONNECT_KIOSK,
                                                            FAILURE,
                                                            "Kiosk rejected, unknown error",
                                                            "",
                                                            ""
                                                        )
                                                    )
                                                )
                                            }
                                            activeCustomers[connection.customer]?.let { terminateWssSession(it) }
                                        }
                                        REQUEST_WEB_RTC_TRANSPORT -> {
                                            // response for connect request
                                            println("/registerkiosk, request webrtc transport: $response")
                                            val connection =
                                                gson.fromJson<Connection>(response.payload, Connection::class.java)
                                            activeCustomers[connection.customer]?.send(toJsonString(response))
                                        }
                                    }
                                }
                            }
                        }
                    }
                //}
            } catch (e: Exception) {
                println("/registerkiosk: ${e.printStackTrace()}")
            }
        }
    }
}

