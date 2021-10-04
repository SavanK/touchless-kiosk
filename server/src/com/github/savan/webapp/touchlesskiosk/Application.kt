package com.github.savan.webapp.touchlesskiosk

import com.github.savan.webapp.touchlesskiosk.model.Connection
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

private val registeredKiosks: MutableMap<Kiosk, WebSocketServerSession> = Collections.synchronizedMap(
    mutableMapOf<Kiosk, WebSocketServerSession>())
private val registeredCustomers: MutableMap<Customer, WebSocketServerSession> = Collections.synchronizedMap(
    mutableMapOf<Customer, WebSocketServerSession>())
private val activeConnections: MutableMap<Kiosk, Customer> = Collections.synchronizedMap(
    mutableMapOf<Kiosk, Customer>())

enum class KIOSKSTATUS(val text: String) {
    FREE("free"),
    BUSY("busy"),
    NOT_FOUND("not found")
}

fun getKioskStatus(kiosk: Kiosk): KIOSKSTATUS {
    return if(registeredKiosks.containsKey(kiosk)) {
        if(activeConnections.containsKey(kiosk)) KIOSKSTATUS.BUSY else KIOSKSTATUS.FREE
    } else KIOSKSTATUS.NOT_FOUND
}

fun getKioskSocket(connection: Connection): WebSocketServerSession? {
    return registeredKiosks[connection.kiosk]
}

fun getCustomerSocket(connection: Connection): WebSocketServerSession? {
    return registeredCustomers[connection.customer]
}

fun saveKioskSession(kiosk: Kiosk, session: WebSocketServerSession) {
    registeredKiosks[kiosk] = session
}

suspend fun discardKioskSession(kiosk: Kiosk) {
    registeredKiosks[kiosk]?.let { terminateWssSession(it) }
    registeredKiosks.remove(kiosk)
}

fun saveCustomerSession(customer: Customer, session: WebSocketServerSession) {
    registeredCustomers[customer] = session
}

suspend fun discardCustomerSession(customer: Customer) {
    registeredCustomers[customer]?.let { terminateWssSession(it) }
    registeredCustomers.remove(customer)
}

fun saveConnection(connection: Connection) {
    activeConnections[connection.kiosk] = connection.customer
}

fun breakConnection(connection: Connection) {
    if(activeConnections[connection.kiosk] == connection.customer)
        activeConnections.remove(connection.kiosk)
}

fun isValidConnection(connection: Connection): Boolean {
    return activeConnections[connection.kiosk] == connection.customer
}

suspend fun terminateWssSession(session: WebSocketServerSession) {
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

    routing {
        static("/static") {
            files("js")
        }
    }

    // init routes
    kioskRoutes()
    customerRoutes()
}