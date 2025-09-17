package com.maddoxh.kotcord

import com.maddoxh.kotcord.core.Bot
import com.maddoxh.kotcord.core.events.BotReadyEvent

fun main() {
    val bot = Bot("123456")

    bot.on<BotReadyEvent> {
        println("Bot is ready!")
    }

    bot.start()
}