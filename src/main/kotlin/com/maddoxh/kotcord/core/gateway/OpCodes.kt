package com.maddoxh.kotcord.core.gateway

object OpCodes {
    const val DISPATCH = 0
    const val HEARTBEAT = 1
    const val IDENTIFY = 2
    const val RESUME = 6
    const val RECONNECT = 7
    const val INVALID_SESSION = 9
    const val HELLO = 10
    const val HEARTBEAT_ACK = 11
}