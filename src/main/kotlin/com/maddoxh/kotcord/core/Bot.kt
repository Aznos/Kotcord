package com.maddoxh.kotcord.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class Bot(
    var token: String = "",
    var intents: Int = 1
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val handlers: MutableMap<KClass<*>, MutableList<suspend (Any) -> Unit>> = ConcurrentHashMap()

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

    fun start() {
        println("Bot started with token: $token and intents: $intents")
    }
}