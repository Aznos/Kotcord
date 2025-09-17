package com.maddoxh.kotcord.core

class Bot(
    var token: String = "",
    var intents: Int = 1
) {
    init {
        println("Bot initialized with token: $token and intents: $intents")
    }
}