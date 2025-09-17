package com.maddoxh.kotcord

import com.maddoxh.kotcord.core.Bot
import com.maddoxh.kotcord.core.events.BotReadyEvent
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking

fun main() {
    val dotenv = dotenv()
    val token = dotenv["DISCORD_TOKEN"] ?: error("No DISCORD_TOKEN env variable set")
    val channelID = dotenv["CHANNEL_ID"] ?: error("No CHANNEL_ID env variable set")
    val guildID = dotenv["GUILD_ID"]

    val bot = Bot {
        this.token = token
        intents = 1
        defaultGuildID = guildID
    }

    bot.slash("hello", "say hi") {
        stringOption("name", "Who to greet", required = false)
        action {
            val who = optionString("name") ?: username
            respond("Hey ${who}!")
        }
    }

    bot.on<BotReadyEvent> { ready ->
        runBlocking {
            bot.sendMessage(channelID, "Hello from Kotcord!")
        }
    }

    bot.start()
}