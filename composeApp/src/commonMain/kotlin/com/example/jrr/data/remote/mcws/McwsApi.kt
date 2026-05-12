package com.example.jrr.data.remote.mcws

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import co.touchlab.kermit.Logger
import com.example.jrr.domain.model.McwsError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import org.koin.core.annotation.Single

@Single
class McwsApi(
    private val httpClient: HttpClient
) {
    private val logger = Logger.withTag("McwsApi")

    suspend fun get(
        baseUrl: String,
        endpoint: String,
        params: Map<String, String> = emptyMap(),
        token: String? = null
    ): Either<McwsError, String> = either {
        val url = "$baseUrl/MCWS/v1/$endpoint"
        logger.d { "Requesting URL: $url" }

        val response = catch({
            httpClient.get(url) {
                params.forEach { (key, value) -> parameter(key, value) }
                if (token != null) parameter("Token", token)
            }
        }) { t ->
            raise(McwsError.Network("GET $url failed: ${t.message}", t))
        }

        ensure(response.status.isSuccess()) {
            logger.e { "Request failed with status ${response.status} for URL $url" }
            McwsError.HttpStatus(response.status.value, "MCWS $endpoint -> ${response.status}")
        }

        catch({ response.body<String>() }) { t ->
            raise(McwsError.Network("Reading body from $url failed: ${t.message}", t))
        }
    }
}
