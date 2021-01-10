package com.natelincoln.bot.servers

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.apache.http.message.BasicHeader

class Netlify(private val config: NetlifyConfig) {
    private val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        engine {
            customizeClient {
                setDefaultHeaders(listOf(BasicHeader("Authorization", "Bearer ${config.token}")))
            }
        }
    }

    data class NetlifyConfig(
        val token: String
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

    suspend fun getDnsZones(): List<SparseDnsZone> {
        return httpClient.get("https://api.netlify.com/api/v1/dns_zones")
    }

    suspend fun getDnsRecords(zoneId: String): List<DnsRecord> =
        httpClient.get("https://api.netlify.com/api/v1/dns_zones/${zoneId}/dns_records")

    suspend fun deleteDnsRecord(zoneId: String, recordId: String): Unit =
        httpClient.delete("https://api.netlify.com/api/v1/dns_zones/${zoneId}/dns_records/${recordId}")

    suspend fun createDnsRecord(zoneId: String, body: CreateDnsRecordRequestBody): Unit {
        httpClient.post<Unit> {
            url("https://api.netlify.com/api/v1/dns_zones/${zoneId}/dns_records")
            contentType(ContentType.Application.Json)
            this.body = body
        }
    }
}