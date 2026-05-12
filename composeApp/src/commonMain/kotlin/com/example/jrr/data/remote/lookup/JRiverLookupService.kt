package com.example.jrr.data.remote.lookup

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import co.touchlab.kermit.Logger
import com.example.jrr.domain.model.LookupError
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

    suspend fun lookup(accessKey: String): Either<LookupError, LookupResponse> = either {
        logger.d { "Looking up Access Key: $accessKey" }
        val response = catch({
            httpClient.get("http://webplay.jriver.com/libraryserver/lookup") {
                parameter("id", accessKey)
            }
        }) { t -> raise(LookupError.Network("Lookup request failed: ${t.message}", t)) }

        ensure(response.status.isSuccess()) {
            logger.e { "Lookup failed with status: ${response.status}" }
            LookupError.NotFound("Lookup failed with status: ${response.status}")
        }

        val bodyString = catch({ response.body<String>() }) { t ->
            raise(LookupError.Network("Reading lookup body failed: ${t.message}", t))
        }
        logger.v { "Lookup response received: $bodyString" }
        catch({ xml.decodeFromString(LookupResponse.serializer(), bodyString) }) { t ->
            raise(LookupError.Parse("Lookup XML decode failed: ${t.message}", t))
        }
    }
}
