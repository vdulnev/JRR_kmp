package com.example.jrr.data.remote.mcws

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class McwsApi(
    private val httpClient: HttpClient
) {
    suspend fun get(
        baseUrl: String,
        endpoint: String,
        params: Map<String, String> = emptyMap(),
        token: String? = null
    ): String {
        val url = "$baseUrl/MCWS/v1/$endpoint"
        println("McwsApi: Requesting URL: $url")
        
        val response = httpClient.get(url) {
            params.forEach { (key, value) ->
                parameter(key, value)
            }
            if (token != null) {
                parameter("Token", token)
            }
        }
        
        if (!response.status.isSuccess()) {
            println("McwsApi: ERROR - Request failed with status ${response.status} for URL $url")
            throw Exception("MCWS request failed: ${response.status}")
        }
        
        return response.body()
    }
}
