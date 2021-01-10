package com.natelincoln.bot.servers

class DnsService(
    private val config: DnsServiceConfig,
    private val netlify: Netlify
) {
    data class DnsServiceConfig(
        val zoneName: String,
        val recordHostname: String,
        val ttl: Long
    )

    private suspend fun getDnsZoneId(): String {
        return netlify.getDnsZones()
            .find { it.name == config.zoneName }?.id ?: throw IllegalStateException(
            "Cannot find DNS zone ${config.zoneName}"
        )
    }

    suspend fun getCurrentDnsRecord(zoneId: String? = null): Netlify.DnsRecord? {
        val resolvedZoneId = zoneId ?: getDnsZoneId()

        val records = netlify.getDnsRecords(resolvedZoneId)

        return records.find { it.hostname == config.recordHostname }
    }

    suspend fun updateDnsEntry(ipAddress: String) {
        val zoneId = getDnsZoneId()
        val existingRecord = getCurrentDnsRecord(zoneId)

        if (existingRecord != null) {
            netlify.deleteDnsRecord(zoneId, existingRecord.id)
        }

        netlify.createDnsRecord(
            zoneId, Netlify.CreateDnsRecordRequestBody(
                type = "A",
                hostname = config.recordHostname,
                ttl = config.ttl,
                value = ipAddress
            )
        )
    }
}