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
        val response = httpClient.get("$baseUrl/MCWS/v1/$endpoint") {
            params.forEach { (key, value) ->
                parameter(key, value)
            }
            if (token != null) {
                parameter("Token", token)
            }
        }
        
        if (!response.status.isSuccess()) {
            throw Exception("MCWS request failed: ${response.status}")
        }
        
        return response.body()
    }
}
