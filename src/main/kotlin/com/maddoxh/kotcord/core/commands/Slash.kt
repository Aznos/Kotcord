package com.maddoxh.kotcord.core.commands

import com.maddoxh.kotcord.core.Bot

data class SlashOption(
    val name: String,
    val description: String,
    val type: Int,
    val required: Boolean
)

class SlashCommand internal constructor(
    val name: String,
    val description: String,
    val options: List<SlashOption>,
    private val actionBlock: suspend SlashContext.() -> Unit
) {
    internal suspend fun run(ctx: SlashContext) = actionBlock(ctx)
}

class SlashCommandBuilder internal constructor(
    private val name: String,
    private val description: String
) {
    private var action: suspend SlashContext.() -> Unit = {}
    private val options: MutableList<SlashOption> = mutableListOf()

    fun stringOption(name: String, description: String, required: Boolean = false) {
        require(name.matches(Regex("^[\\w-]{1,32}\$"))) { "Option name must be 1-32 chars" }
        options += SlashOption(name, description, type = 3, required = required)
    }

    fun action(block: suspend SlashContext.() -> Unit) {
        action = block
    }

    internal fun build(): SlashCommand = SlashCommand(name, description, options.toList(), action)
}

data class SlashContext(
    val bot: Bot,
    val interactionID: String,
    val interactionToken: String,
    val commandName: String,
    val username: String,
    private val optionValues: Map<String, String?>
) {
    suspend fun respond(content: String) {
        bot.interactionsCreateMessage(interactionID, interactionToken, content)
    }

    fun optionString(name: String): String? = optionValues[name]
}