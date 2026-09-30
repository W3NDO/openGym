package com.example.opengym_wear.presentation.api


import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.call.body
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import com.example.opengym_wear.presentation.data.model.WatchWorkout
import java.util.UUID


class OpenGymApi(
    private val baseUrl: String,
    private val token: String
) {
    private val apiUrl = baseUrl.trim().trimEnd('/')

    private val client = HttpClient(Android) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
            )
        }
    }

    suspend fun getState(): OpenGymResponse {
        return client.get("$apiUrl/api/data") {
            bearerAuth(token)
        }.body()
    }

    suspend fun redeemPairingCode(code: String): PairingResponse {
        return client.post("$apiUrl/api/pair/redeem") {
            contentType(ContentType.Application.Json)
            setBody(PairingCodeRequest(code.trim().uppercase()))
        }.body()
    }

    suspend fun finishWorkout(workout: WatchWorkout, completedSets: List<List<Boolean>>) {
        val workoutId = UUID.randomUUID().toString()
        val record = completedWorkoutJson(workout, completedSets, workoutId)

        repeat(2) { attempt ->
            val latest = client.get("$apiUrl/api/data") {
                bearerAuth(token)
            }.body<RawOpenGymResponse>()
            val state = latest.state ?: JsonObject(emptyMap())
            val workouts = (state["workouts"] as? JsonArray).orEmpty()
            if (workouts.any { (it as? JsonObject)?.get("id") == JsonPrimitive(workoutId) }) return

            val updatedState = JsonObject(state + ("workouts" to JsonArray(workouts + record)))
            val requestBody = buildJsonObject {
                put("state", updatedState)
                put("baseRev", latest.rev)
            }
            try {
                val response = client.put("$apiUrl/api/data") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
                if (response.status.isSuccess()) return
                if (response.status.value != 409 || attempt == 1) {
                    error("Server refused to save the workout (HTTP ${response.status.value}).")
                }
            } catch (exception: ClientRequestException) {
                if (exception.response.status.value != 409 || attempt == 1) throw exception
            }
        }
    }

    private fun completedWorkoutJson(
        workout: WatchWorkout,
        completedSets: List<List<Boolean>>,
        workoutId: String,
    ): JsonObject {
        val now = System.currentTimeMillis()
        val entries = workout.exercises.mapIndexedNotNull { index, exercise ->
            val done = completedSets.getOrElse(index) { emptyList() }.count { it }
            if (done == 0) return@mapIndexedNotNull null
            val target = buildJsonObject {
                put("id", exercise.id)
                put("sets", exercise.sets)
                exercise.min?.let { put("min", it) }
                exercise.speed?.let { put("speed", it) }
                exercise.mode?.let { put("mode", it) }
                exercise.restSeconds?.let { put("restSec", it) }
                exercise.warmupRestSeconds?.let { put("warmupRestSec", it) }
            }
            buildJsonObject {
                put("id", exercise.id)
                exercise.routineId?.let { put("rid", it) }
                put("target", target)
                put("topW", JsonNull)
                put("sets", JsonArray(List(exercise.sets) { setIndex ->
                    buildJsonObject {
                        put("done", completedSets.getOrElse(index) { emptyList() }.getOrElse(setIndex) { false })
                        exercise.min?.let { put("min", it) }
                        exercise.speed?.let { put("speed", it) }
                    }
                }))
            }
        }
        return buildJsonObject {
            put("id", workoutId)
            put("d", workout.date)
            put("start", workout.startedAt)
            put("end", now)
            put("routineIds", JsonArray(workout.routineIds.map(::JsonPrimitive)))
            workout.routineIds.firstOrNull()?.let { put("routineId", it) } ?: put("routineId", JsonNull)
            put("name", workout.name)
            put("bw", JsonNull)
            put("entries", JsonArray(entries))
            put("prs", JsonArray(emptyList()))
        }
    }

    fun close() = client.close()
}

@kotlinx.serialization.Serializable
private data class RawOpenGymResponse(
    val state: JsonObject? = null,
    val rev: Int = 0
)