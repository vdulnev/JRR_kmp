package com.example.jrr.data.remote.lookup

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
    suspend fun lookup(accessKey: String): Result<LookupResponse> = runCatching {
        val response = httpClient.get("http://webplay.jriver.com/libraryserver/lookup") {
            parameter("id", accessKey)
        }
        
        if (response.status.isSuccess()) {
            val bodyString = response.body<String>()
            xml.decodeFromString(LookupResponse.serializer(), bodyString)
        } else {
            throw Exception("Lookup failed with status: ${response.status}")
        }
    }
}
