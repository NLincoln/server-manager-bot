package com.natelincoln.bot.servers

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class ParseMessageTest {
    @Test
    fun `parses status`() {
        parseMessage(" status") shouldBe GetStatus
    }

    @Test
    fun `parses start`() {
        parseMessage(" start") shouldBe StartServer(null)
        parseMessage(" start 12am") shouldBe StartServer("12am")
    }

    @Test
    fun `unknown message`() {
        parseMessage(" asdfasdfas") shouldBe UnknownMessage(listOf("asdfasdfas"))
    }
}