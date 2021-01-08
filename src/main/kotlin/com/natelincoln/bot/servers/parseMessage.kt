package com.natelincoln.bot.servers

sealed class Message

data class StartServer(
    val untilTime: String?
) : Message()

object StopServer : Message()

object GetStatus : Message()

data class UnknownMessage(
    val parts: List<String>
) : Message()

private val WHITESPACE_RE by lazy { Regex("\\s+") }


fun parseMessage(message: String): Message {
    val parts = message.trim().split(WHITESPACE_RE)

    return when (parts[0]) {
        "status" -> GetStatus
        "start" -> StartServer(null)
        "stop" -> StopServer
        else -> UnknownMessage(parts)
    }
}
