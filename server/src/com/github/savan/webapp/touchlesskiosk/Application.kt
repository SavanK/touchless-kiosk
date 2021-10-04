package com.github.savan.webapp.touchlesskiosk

import com.github.savan.webapp.touchlesskiosk.model.Customer
import com.github.savan.webapp.touchlesskiosk.model.Kiosk
import com.github.savan.webapp.touchlesskiosk.routes.customerRoutes
import com.github.savan.webapp.touchlesskiosk.routes.kioskRoutes
import io.ktor.application.*
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.gson.gson
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.websocket.WebSocketServerSession
import io.ktor.websocket.WebSockets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Duration
import java.util.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

val registeredKiosks: MutableMap<Kiosk, WebSocketServerSession> = Collections.synchronizedMap(
    mutableMapOf<Kiosk, WebSocketServerSession>())
val activeCustomers: MutableMap<Customer, WebSocketServerSession> = Collections.synchronizedMap(
    mutableMapOf<Customer, WebSocketServerSession>())
val activeConnections: MutableMap<Kiosk, Customer> = Collections.synchronizedMap(
    mutableMapOf<Kiosk, Customer>())

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

    routing {
        static("/static") {
            files("js")
        }
    }

    // init routes
    kioskRoutes()
    customerRoutes()
}