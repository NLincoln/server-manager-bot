package com.natelincoln.bot.servers

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import discord4j.core.DiscordClient
import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import java.net.InetAddress


suspend fun startBot(config: Config, server: ServerService, dns: DnsService): Unit = coroutineScope {
    val client = DiscordClient.create(config.getString("discord.token"))
    val commandPrefix = config.getString("discord.command-prefix")
    client.withGateway { gateway ->
        mono {
            gateway.on(MessageCreateEvent::class.java)
                .asFlow()
                .filter { it.message.content.startsWith(commandPrefix) }
                .collect { event ->
                    val message = event.message
                    val channel = message.channel.awaitSingle()

                    val response = when (val content = parseMessage(message.content.removePrefix(commandPrefix))) {
                        is StartServer -> {
                            channel.createMessage("Starting Server. This may take some time.").awaitSingle()
                            server.startServer()
                            "Server is started!!!! Please allow 1-2 minutes for DNS to propogate & minecraft to start. Maybe drink some water while you wait?"
                        }
                        StopServer -> {
                            server.stopServer()
                            "Stopping Server!"
                        }
                        GetStatus -> {
                            val instance = server.getStatus()
                            val dnsRecord = dns.getCurrentDnsRecord()

                            val resolvedDns = withContext(Dispatchers.IO) {
                                @Suppress("BlockingMethodInNonBlockingContext")
                                InetAddress.getByName(config.getString("netlify.dns-record-hostname"))
                            }

                            """
                            Server is **${instance.state().nameAsString()}**
                            AWS has given it IP: `${instance.publicIpAddress()}`
                            Netlify DNS has `${dnsRecord?.value ?: "<Nothing>"}`
                            Resolved DNS from Nathan's computer is: `${resolvedDns?.hostAddress}`
                            """.trimIndent()
                        }
                        is UnknownMessage -> "I don't know what that means? Got [${content.parts.joinToString(",")}]"
                    }

                    channel.createMessage(response).awaitSingle()
                }
        }
    }.awaitSingle()
}

suspend fun main(): Unit = coroutineScope {
    val config = ConfigFactory.defaultApplication().resolve()

    val ec2Client = Ec2AsyncClient.builder()
        .region(Region.US_EAST_2)
        .credentialsProvider {
            object : AwsCredentials {
                override fun accessKeyId() = config.getString("aws.access-key-id")
                override fun secretAccessKey() = config.getString("aws.secret-access-key")
            }
        }
        .build()

    val dnsService = DnsService(
        DnsService.DnsServiceConfig(
            zoneName = config.getString("netlify.dns-zone-name"),
            recordHostname = config.getString("netlify.dns-record-hostname"),
            ttl = config.getLong("netlify.dns-ttl")
        ),
        Netlify(Netlify.NetlifyConfig(token = config.getString("netlify.token")))
    )

    startBot(config, ServerService(ec2Client, dnsService), dnsService)
}
