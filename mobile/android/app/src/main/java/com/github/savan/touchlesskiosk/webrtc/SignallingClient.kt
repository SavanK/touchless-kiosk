package com.github.savan.touchlesskiosk.webrtc

import android.content.Context
import com.github.savan.touchlesskiosk.utils.Logger
import com.github.savan.touchlesskiosk.utils.StorageUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.websocket.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.lang.ref.WeakReference
import java.util.*
import kotlin.reflect.KClass

@ExperimentalCoroutinesApi
class SignallingClient : ISignallingClient, CoroutineScope {
    companion object {
        private const val TAG = "SignallingClient"

        private const val SUCCESS = "success"
        private const val FAILURE = "failure"

        private const val REQUEST_REGISTER_KIOSK = "register_kiosk"
        private const val REQUEST_CONNECT_KIOSK = "connect_kiosk"
        private const val REQUEST_DISCONNECT_KIOSK = "disconnect_kiosk"
        private const val REQUEST_WEB_RTC_TRANSPORT = "web_rtc_transport"

        private const val KEY_SIGNAL_HOST = "signal_host_address"
        private const val KEY_KIOSK_ID = "kiosk_id"

        private const val DEFAULT_HOST = "safe-kiosk.herokuapp.com"
    }

    data class Connection(val customer: Customer, val kiosk: Kiosk)
    data class Customer(val customerId: String)
    data class Kiosk(val kioskId: String)
    data class Request(val requestId: String, val payload: String, val webRtcPayload: String)
    data class Response(
            val requestId: String, val result: String, val message: String, val payload: String,
            val webRtcPayload: String
    )
    // The Android SDK classes [SessionDescription] and [IceCandidate] are not matching the web SDK class structure
    // Hence the remapping
    data class SessionDescriptionWeb(val sdp: String, val type: String)
    data class IceCandidateWeb(val type: String, val sdpMLineIndex: Int, val sdpMid: String, val candidate: String)

    private lateinit var context: WeakReference<Context>
    private var registered = false
    private var signalListener: ISignallingClient.ISignalListener? = null
    private var activeConnection: Connection? = null
    private val job = Job()
    private val gson = Gson()

    override val coroutineContext = Dispatchers.IO + job

    private val client = HttpClient(CIO) {
        install(WebSockets)
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }

    @ExperimentalCoroutinesApi
    private val sendChannel = ConflatedBroadcastChannel<String>()

    override fun initialize(c: Context, listener: ISignallingClient.ISignalListener) {
        context = WeakReference(c)
        signalListener = listener
        connectToServer()
    }

    @ExperimentalCoroutinesApi
    override fun registerKiosk() {
        if(!registered) {
            // Send my kiosk info to register with the server
            context.get()?.let {
                if (StorageUtils.get(it, KEY_KIOSK_ID).isNullOrEmpty()) {
                    StorageUtils.set(it, KEY_KIOSK_ID, UUID.randomUUID().toString())
                }

                StorageUtils.get(it, KEY_KIOSK_ID)?.let {
                    val myKiosk = Kiosk(it)
                    Logger.d(TAG, "registerKiosk, registering $myKiosk with server")
                    val request = Request(REQUEST_REGISTER_KIOSK, toJsonString(myKiosk), "")
                    send(request)
                }
            }
        }
    }

    override fun unregisterKiosk() {
        // TODO build unregister kiosk flow
    }

    override fun sendWebRtcPayRequest(webRtcPayload: Any) {
        sendWebRtc(webRtcPayload)
    }

    override fun destroy() {
        client.close()
        job.complete()

        registered = false
        activeConnection = null
    }

    @ExperimentalCoroutinesApi
    private fun connectToServer() = launch {
        val host = context.get()?.let { StorageUtils.get(it, KEY_SIGNAL_HOST)} ?: DEFAULT_HOST
        Logger.d(TAG, "connectToServer, host:$host")
        client.wss(host = host,
                path = "/registerkiosk") {
            signalListener?.onConnectionEstablished()
            val sendData = sendChannel.openSubscription()
            try {
                while (true) {
                    sendData.poll()?.let {
                        Logger.d(TAG, "Sending: $it")
                        outgoing.send(Frame.Text(it))
                    }

                    incoming.poll()?.let { data ->
                        if (data is Frame.Text) {
                            when(getClassOfObject(data.readText())) {
                                Request::class -> {
                                    val request = gson.fromJson<Request>(data.readText(), Request::class.java)
                                    Logger.d(TAG, "Request: $request")
                                    when(request.requestId) {
                                        REQUEST_CONNECT_KIOSK -> {
                                            val connection = gson.fromJson<Connection>(
                                                    request.payload,
                                                    Connection::class.java
                                            )

                                            activeConnection = connection

                                            send(Response(REQUEST_CONNECT_KIOSK, SUCCESS,
                                                    "", toJsonString(connection), ""))

                                            // Start screen recording now
                                            withContext(Dispatchers.Main) {
                                                signalListener?.onConnectionRequestReceived()
                                            }
                                        }
                                        REQUEST_DISCONNECT_KIOSK -> {
                                            val connection = gson.fromJson<Connection>(
                                                    request.payload,
                                                    Connection::class.java
                                            )

                                            activeConnection = null

                                            // Stop screen recording now
                                            withContext(Dispatchers.Main) {
                                                signalListener?.onDisconnectionRequestReceived()
                                            }

                                            send(Response(REQUEST_DISCONNECT_KIOSK, SUCCESS,
                                                    "", toJsonString(connection), ""))
                                        }
                                    }
                                }
                                Response::class -> {
                                    val response = gson.fromJson<Response>(data.readText(), Response::class.java)
                                    Logger.d(TAG, "Response: $response")
                                    when(response.requestId) {
                                        REQUEST_REGISTER_KIOSK -> {
                                            registered = SUCCESS.compareTo(response.result, true) == 0
                                            if(registered) {
                                                signalListener?.onKioskRegistered()
                                            }
                                            Logger.d(TAG, "registerKiosk, registration result: $registered")
                                        }
                                        REQUEST_WEB_RTC_TRANSPORT -> {
                                            val connection = gson.fromJson<Connection>(
                                                    response.payload,
                                                    Connection::class.java
                                            )
                                            if(connection == activeConnection) {
                                                val webRtcPayload = gson.fromJson(
                                                        response.webRtcPayload,
                                                        JsonObject::class.java
                                                )
                                                withContext(Dispatchers.Main) {
                                                    webRtcPayload?.let {
                                                        if (webRtcPayload.has("candidate")) {
                                                            val iceCandidateWeb = gson.fromJson(webRtcPayload, IceCandidateWeb::class.java)
                                                            signalListener?.onIceCandidateReceived(
                                                                    IceCandidate(iceCandidateWeb.sdpMid,
                                                                            iceCandidateWeb.sdpMLineIndex,
                                                                            iceCandidateWeb.candidate))
                                                        } else if (webRtcPayload.has("type") && webRtcPayload.get("type").asString == "answer") {
                                                            // Do extra conversion
                                                            val answer = gson.fromJson(webRtcPayload, SessionDescriptionWeb::class.java)
                                                            signalListener?.onAnswerReceived(SessionDescription(
                                                                    when (answer.type) {
                                                                        "answer" -> SessionDescription.Type.ANSWER
                                                                        "pranswer" -> SessionDescription.Type.PRANSWER
                                                                        "offer" -> SessionDescription.Type.OFFER
                                                                        else -> SessionDescription.Type.ANSWER
                                                                    }, answer.sdp))
                                                        } else {
                                                            // do nothing
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "registerKiosk, exception: ${e.printStackTrace()}")
            }
        }
    }

    @ExperimentalCoroutinesApi
    private fun send(data: Any) = runBlocking {
        sendChannel.send(toJsonString(data))
    }

    @ExperimentalCoroutinesApi
    private fun sendWebRtc(webRtcPayload: Any) = runBlocking {
        activeConnection?.let {
            send(Request(REQUEST_WEB_RTC_TRANSPORT, toJsonString(it),
                    toJsonString(webRtcPayload)))
        }
    }

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
}