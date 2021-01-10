package com.natelincoln.bot.servers

import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.ec2.model.*

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