package com.maddoxh.kotcord

import com.maddoxh.kotcord.core.Bot
import com.maddoxh.kotcord.core.events.BotReadyEvent
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking

fun main() {
    val dotenv = dotenv()
    val token = dotenv["DISCORD_TOKEN"] ?: error("No DISCORD_TOKEN env variable set")
    val channelID = dotenv["CHANNEL_ID"] ?: error("No CHANNEL_ID env variable set")

    val bot = Bot {
        this.token = token
        intents = 1
    }

    bot.on<BotReadyEvent> { ready ->
        println("Bot ready - session=${ready.sessionID}")

        runBlocking {
            bot.sendMessage(channelID, "Hello from Kotcord!")
        }
    }

    bot.start()
}