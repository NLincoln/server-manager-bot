package com.natelincoln.bot.servers

import com.fasterxml.jackson.databind.DeserializationFeature
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import discord4j.core.DiscordClient
import discord4j.core.event.domain.message.MessageCreateEvent
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext
import org.apache.http.message.BasicHeader
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.ec2.model.*
import java.lang.IllegalStateException
import java.net.InetAddress


class ServerService(
    private val client: Ec2AsyncClient,
    private val dnsService: DnsService
) {
    private suspend fun getServer(): Instance {
        val request = DescribeInstancesRequest.builder()
            .filters(
                Filter.builder()
                    .name("tag:game")
                    .values("minecraft")
                    .build()
            )
            .build()

        val response = client.describeInstances(request).await()

        return response.reservations().first().instances().first()
    }

    suspend fun startServer(): InstanceStateChange {
        val server = getServer()
        val request = StartInstancesRequest.builder()
            .instanceIds(server.instanceId())
            .build()

        val response = client.startInstances(request).await()

        while (getServer().publicIpAddress() == null) {
            delay(1_000)
        }

        dnsService.updateDnsEntry(getServer().publicIpAddress())

        return response.startingInstances().first()
    }

    suspend fun stopServer(): InstanceStateChange {
        val server = getServer()
        val request = StopInstancesRequest.builder()
            .instanceIds(server.instanceId())
            .build()

        val response = client.stopInstances(request).await()
        return response.stoppingInstances().first()
    }

    suspend fun getStatus(): Instance {
        return getServer()
    }
}

class DnsService(
    private val config: DnsServiceConfig,
    private val httpClient: HttpClient
) {
    data class DnsServiceConfig(
        val zoneName: String,
        val recordHostname: String,
        val ttl: Long
    )

    data class SparseDnsZone(
        val id: String,
        val name: String
    )

    data class DnsRecord(
        val id: String,
        val hostname: String,
        val type: String,
        val value: String,
        val ttl: Long,
    )

    data class CreateDnsRecordRequestBody(
        val type: String,
        val hostname: String,
        val ttl: Long,
        val value: String
    )

    private suspend fun getDnsZoneId(): String {
        val request = httpClient.get<List<SparseDnsZone>>("https://api.netlify.com/api/v1/dns_zones")

        return request.find { it.name == config.zoneName }?.id ?: throw IllegalStateException(
            "Cannot find DNS zone ${config.zoneName}"
        )
    }

    suspend fun getCurrentDnsRecord(zoneId: String? = null): DnsRecord? {
        val resolvedZoneId = zoneId ?: getDnsZoneId()

        val records = httpClient.get<List<DnsRecord>>("https://api.netlify.com/api/v1/dns_zones/${resolvedZoneId}/dns_records")

        return records.find { it.hostname == config.recordHostname }
    }

    suspend fun updateDnsEntry(ipAddress: String) {
        val zoneId = getDnsZoneId()
        val existingRecord = getCurrentDnsRecord(zoneId)

        if (existingRecord != null) {
            httpClient.delete<Unit>("https://api.netlify.com/api/v1/dns_zones/${zoneId}/dns_records/${existingRecord.id}")
        }

        httpClient.post<Unit> {
            url("https://api.netlify.com/api/v1/dns_zones/${zoneId}/dns_records")
            contentType(ContentType.Application.Json)
            body = CreateDnsRecordRequestBody(
                type = "A",
                hostname = config.recordHostname,
                ttl = config.ttl,
                value = ipAddress
            )
        }
    }
}

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
    val config = ConfigFactory.defaultApplication()

    val ec2Client = Ec2AsyncClient.builder()
        .region(Region.US_EAST_2)
        .credentialsProvider {
            object : AwsCredentials {
                override fun accessKeyId() = config.getString("aws.access-key-id")
                override fun secretAccessKey() = config.getString("aws.secret-access-key")
            }
        }
        .build()

    val netlifyClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        engine {
            customizeClient {
                setDefaultHeaders(listOf(BasicHeader("Authorization", "Bearer ${config.getString("netlify.token")}")))
            }
        }
    }

    val dnsService = DnsService(
        DnsService.DnsServiceConfig(
            zoneName = config.getString("netlify.dns-zone-name"),
            recordHostname = config.getString("netlify.dns-record-hostname"),
            ttl = config.getLong("dns-ttl")
        ),
        netlifyClient
    )

    startBot(config, ServerService(ec2Client, dnsService), dnsService)
}
