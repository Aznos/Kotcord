package com.maddoxh.kotcord.core.gateway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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