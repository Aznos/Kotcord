package com.maddoxh.kotcord.core

import com.maddoxh.kotcord.core.events.BotReadyEvent
import com.maddoxh.kotcord.core.gateway.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class Bot private constructor(
    internal val config: Config
) {
    data class Config(
        var token: String = "",
        var intents: Int = 1
    )

    class Builder {
        internal val conf = Config()
        var token: String
            get() = conf.token
            set(value) { conf.token = value }
        var intents: Int
            get() = conf.intents
            set(value) { conf.intents = value }
    }

    companion object {
        operator fun invoke(builder: Builder.() -> Unit): Bot {
            val b = Builder().apply(builder)
            return Bot(b.conf)
        }
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val handlers: MutableMap<KClass<*>, MutableList<suspend (Any) -> Unit>> = ConcurrentHashMap()

    inline fun <reified T: Any> on(noinline handler: suspend (T) -> Unit) {
        val list = handlers.getOrPut(T::class) { mutableListOf() }

        @Suppress("UNCHECKED_CAST")
        list += handler as suspend (Any) -> Unit
    }

    suspend fun <T: Any> emit(event: T) {
        val list = handlers[event::class] ?: return
        for(h in list) {
            scope.launch {
                h(event as Any)
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(WebSockets)
        install(ContentNegotiation) { json() }
    }

    suspend fun sendMessage(channelID: String, content: String) {
        val url = "https://discord.com/api/v9/channels/$channelID/messages"
        client.post(url) {
            headers.append("Authorization", "Bot ${config.token}")
            headers.append("User-Agent", "DiscordBot (kotcord)")
            contentType(ContentType.Application.Json)
            setBody(CreateMessage(content))
        }
    }

    fun start() {
        runBlocking {
            require(config.token.isNotBlank()) { "Token must be provided" }
            val gatewayURL = "wss://gateway.discord.gg/?v=9&encoding=json"

            client.webSocket(gatewayURL) {
                var lastSeq: Int? = null
                var heartbeatIntervalMs: Long = 0
                var heartbeatJob: Job? = null

                suspend fun sendHeartbeat() {
                    val text = json.encodeToString(GatewayPayload.serializer(JsonElement.serializer()),
                        GatewayPayload(op = OpCodes.HEARTBEAT, d = lastSeq?.let { Json.encodeToJsonElement(it) } )
                    )

                    send(text)
                }

                println("WebSocket connected")

                for(frame in incoming) {
                    when(frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            val base = json.decodeFromString(
                                GatewayPayload.serializer(JsonElement.serializer()), text
                            )

                            if(base.s != null) lastSeq = base.s
                            when(base.op) {
                                OpCodes.HELLO -> {
                                    val hello = json.decodeFromJsonElement(Hello.serializer(), base.d!!)
                                    heartbeatIntervalMs = hello.heartbeat_interval
                                    println("Received HELLO, heartbeat interval = $heartbeatIntervalMs ms")

                                    heartbeatJob?.cancel()
                                    heartbeatJob = launch {
                                        delay(heartbeatIntervalMs)
                                        while(isActive) {
                                            sendHeartbeat()
                                            delay(heartbeatIntervalMs)
                                        }
                                    }

                                    val identify = Identify(
                                        token = config.token,
                                        intents = config.intents,
                                        properties = ConnectionProperties(
                                            os = "linux",
                                            browser = "kotcord",
                                            device = "kotcord"
                                        ),
                                        compress = false
                                    )

                                    val identText = json.encodeToString(
                                        GatewayPayload.serializer(JsonElement.serializer()),
                                        GatewayPayload(
                                            op = OpCodes.IDENTIFY,
                                            d = json.encodeToJsonElement(Identify.serializer(), identify)
                                        )
                                    )

                                    send(identText)
                                }

                                OpCodes.DISPATCH -> {
                                    when(base.t) {
                                        "READY" -> {
                                            val ready = json.decodeFromJsonElement(ReadyEvent.serializer(), base.d!!)
                                            emit(BotReadyEvent(ready.session_id))
                                        }

                                        else -> {}
                                    }
                                }

                                OpCodes.RECONNECT -> { break }
                                else -> {}
                            }
                        }

                        else -> {}
                    }
                }

                heartbeatJob?.cancel()
            }
        }
    }
}