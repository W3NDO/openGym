package com.example.opengym_wear.presentation.api


import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation


class OpenGymApi(
    private val baseUrl: String,
    private val token: String
) {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }
    }

    suspend fun getState(): OpenGymResponse {
        return client.get("$baseUrl/api/data") {
            bearerAuth(token)
        }.body()
    }
}