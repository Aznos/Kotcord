package com.maddoxh.kotcord.core.gateway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GatewayPayload<T>(
    val op: Int,
    val d: T? = null,
    val s: Int? = null,
    val t: String? = null
)

@Serializable
data class Hello(
    val heartbeat_interval: Long
)

@Serializable
data class Identify(
    val token: String,
    val intents: Int,
    val properties: ConnectionProperties,
    val compress: Boolean = false
)

@Serializable
data class ConnectionProperties(
    @SerialName("\$os") val os: String,
    @SerialName("\$browser") val browser: String,
    @SerialName("\$device") val device: String
)

@Serializable
data class ReadyEvent(
    val session_id: String
)

@Serializable
data class CreateMessage(
    val content: String
)

@Serializable
data class ApplicationInfo(
    val id: String
)

@Serializable
data class InteractionCreate(
    val id: String,
    val application_id: String,
    val type: Int,
    val data: InteractionData? = null,
    val token: String,
    val member: GuildMember? = null,
    val user: User? = null
)

@Serializable
data class InteractionData(
    val id: String,
    val name: String,
    val options: List<ApplicationCommandInteractionDataOption>? = null
)

@Serializable
data class ApplicationCommandInteractionDataOption(
    val name: String,
    val type: Int,
    val value: JsonElement? = null,
    val options: List<ApplicationCommandInteractionDataOption>? = null
)

@Serializable
data class GuildMember(
    val user: User? = null
)

@Serializable
data class User(
    val id: String,
    val username: String,
    val discriminator: String? = null,
    val global_name: String? = null
)

@Serializable
data class InteractionCallback(
    val type: Int,
    val data: InteractionMessageData? = null
)

@Serializable
data class InteractionMessageData(
    val content: String
)

@Serializable
data class ApplicationCommandCreate(
    val name: String,
    val description: String,
    val type: Int = 1,
    val options: List<ApplicationCommandOption>? = null
)

@Serializable
data class ApplicationCommandOption(
    val type: Int,
    val name: String,
    val description: String,
    val required: Boolean = false
)