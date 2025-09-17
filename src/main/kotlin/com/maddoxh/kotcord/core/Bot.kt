package com.maddoxh.kotcord.core

import com.maddoxh.kotcord.core.commands.SlashCommand
import com.maddoxh.kotcord.core.commands.SlashCommandBuilder
import com.maddoxh.kotcord.core.commands.SlashContext
import com.maddoxh.kotcord.core.events.BotReadyEvent
import com.maddoxh.kotcord.core.gateway.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class Bot private constructor(
    internal val config: Config
) {
    data class Config(
        var token: String = "",
        var intents: Int = 1,
        var defaultGuildID: String? = null
    )

    class Builder {
        internal val conf = Config()
        var token: String
            get() = conf.token
            set(value) { conf.token = value }
        var intents: Int
            get() = conf.intents
            set(value) { conf.intents = value }
        var defaultGuildID: String?
            get() = conf.defaultGuildID
            set(value) { conf.defaultGuildID = value }
    }

    companion object {
        operator fun invoke(builder: Builder.() -> Unit): Bot {
            val b = Builder().apply(builder)
            return Bot(b.conf)
        }
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val handlers: MutableMap<KClass<*>, MutableList<suspend (Any) -> Unit>> = ConcurrentHashMap()

    private fun HttpRequestBuilder.auth() {
        headers.append("Authorization", "Bot ${config.token}")
        headers.append("User-Agent", "DiscordBot (kotcord)")
    }

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
            auth()
            contentType(ContentType.Application.Json)
            setBody(CreateMessage(content))
        }
    }

    private val slashCommands: MutableMap<String, SlashCommand> = ConcurrentHashMap()

    fun slash(name: String, description: String, builder: SlashCommandBuilder.() -> Unit) {
        require(name.matches(Regex("^[\\w-]{1,32}\$"))) {
            "Slash command name must be 1-32 chars, lowercase, numbers, -, _"
        }

        val cmd = SlashCommandBuilder(name, description).apply(builder).build()
        slashCommands[name] = cmd
    }

    private suspend fun upsertSlashCommands() {
        if(slashCommands.isEmpty()) return

        val appID = getApplicationID()
        val body = slashCommands.values.map {
            ApplicationCommandCreate(
                name = it.name,
                description = it.description,
                type = 1,
                options = it.options.map { opt ->
                    ApplicationCommandOption(
                        type = opt.type,
                        name = opt.name,
                        description = opt.description,
                        required = opt.required
                    )
                }.ifEmpty { null }
            )
        }

        val guildID = config.defaultGuildID
        val base = if(guildID != null) {
            "https://discord.com/api/v9/applications/$appID/guilds/$guildID/commands"
        } else {
            "https://discord.com/api/v9/applications/$appID/commands"
        }

        client.put(base) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    private suspend fun getApplicationID(): String {
        val resp = client.get("https://discord.com/api/v9/oauth2/applications/@me") {
            auth()
        }

        return json.decodeFromString(ApplicationInfo.serializer(), resp.bodyAsText()).id
    }

    internal suspend fun interactionsCreateMessage(interactionID: String, interactionToken: String, content: String) {
        val url = "https://discord.com/api/v9/interactions/$interactionID/$interactionToken/callback"
        client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(InteractionCallback(type = 4, data = InteractionMessageData(content)))
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

                                            scope.launch {
                                                try {
                                                    upsertSlashCommands()
                                                } catch(t: Throwable) {
                                                    println("Failed to upsert slash commands: ${t.message}")
                                                }
                                            }
                                        }

                                        "INTERACTION_CREATE" -> {
                                            val ev = json.decodeFromJsonElement(InteractionCreate.serializer(), base.d!!)
                                            if(ev.type == 2 && ev.data != null) {
                                                val name = ev.data.name
                                                val cmd = slashCommands[name]
                                                if(cmd == null) {
                                                    break
                                                }

                                                val userName = ev.member?.user?.global_name
                                                    ?: ev.member?.user?.username
                                                    ?: ev.user?.global_name
                                                    ?: ev.user?.username
                                                    ?: "there"

                                                val optMap: Map<String, String?> =
                                                    ev.data.options?.associate { o ->
                                                        val v = if(o.type == 3) o.value?.jsonPrimitive?.content else null
                                                        o.name to v
                                                    } ?: emptyMap()

                                                scope.launch {
                                                    try {
                                                        cmd.run(SlashContext(
                                                            bot = this@Bot,
                                                            interactionID = ev.id,
                                                            interactionToken = ev.token,
                                                            commandName = name,
                                                            username = userName,
                                                            optionValues = optMap
                                                        ))
                                                    } catch(t: Throwable) {
                                                        runCatching {
                                                            interactionsCreateMessage(ev.id, ev.token, "Oops: ${t.message}")
                                                        }
                                                    }
                                                }
                                            }
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