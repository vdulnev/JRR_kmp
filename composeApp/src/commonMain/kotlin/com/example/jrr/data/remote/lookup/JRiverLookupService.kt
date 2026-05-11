package com.example.jrr.data.remote.lookup

import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import nl.adaptivity.xmlutil.serialization.XML
import org.koin.core.annotation.Single

@Single
class JRiverLookupService(
    private val httpClient: HttpClient,
    private val xml: XML
) {
    private val logger = Logger.withTag("JRiverLookupService")

    suspend fun lookup(accessKey: String): Result<LookupResponse> = runCatching {
        logger.d { "Looking up Access Key: $accessKey" }
        val response = httpClient.get("http://webplay.jriver.com/libraryserver/lookup") {
            parameter("id", accessKey)
        }
        
        if (response.status.isSuccess()) {
            val bodyString = response.body<String>()
            logger.v { "Lookup response received: $bodyString" }
            xml.decodeFromString(LookupResponse.serializer(), bodyString)
        } else {
            logger.e { "Lookup failed with status: ${response.status}" }
            throw Exception("Lookup failed with status: ${response.status}")
        }
    }
}
