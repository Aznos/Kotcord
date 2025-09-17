package com.maddoxh.kotcord.core.commands

import com.maddoxh.kotcord.core.Bot

class SlashCommand internal constructor(
    val name: String,
    val description: String,
    private val actionBlock: suspend SlashContext.() -> Unit
) {
    internal suspend fun run(ctx: SlashContext) = actionBlock(ctx)
}

class SlashCommandBuilder internal constructor(
    private val name: String,
    private val description: String
) {
    private var action: suspend SlashContext.() -> Unit = {}

    fun action(block: suspend SlashContext.() -> Unit) {
        action = block
    }

    internal fun build(): SlashCommand = SlashCommand(name, description, action)
}

data class SlashContext(
    val bot: Bot,
    val interactionID: String,
    val interactionToken: String,
    val commandName: String,
    val username: String
) {
    suspend fun respond(content: String) {
        bot.interactionsCreateMessage(interactionID, interactionToken, content)
    }
}