package com.example.opengym_wear.presentation.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class OpenGymResponse(
    val state: OpenGymState? = null,
    val rev: Int = 0
)

@Serializable
data class OpenGymState(
    val routines: List<RoutineDto> = emptyList(),
    val workouts: List<JsonElement> = emptyList(),
    val week: Map<String, JsonElement> = emptyMap(),
    val dayPlan: Map<String, String?> = emptyMap(),
    val theme: String = "dark",
    val accent: String = "lime",
    val restSec: Int = 90
)

@Serializable
data class PairingCodeRequest(val code: String)

@Serializable
data class PairingResponse(val token: String)

@Serializable
data class ExerciseDto(
    val n: String,
    val bp: String? = null,
    val eq: String? = null,
    val tg: String? = null,
    val mg: String? = null,
    val sm: List<String> = emptyList(),
    val st: List<String> = emptyList(),
    val img: String? = null,
    val gif: String? = null
)

@Serializable
data class RoutineDto(
    val id: String,
    val name: String,
    val emoji: String? = null,
    val ex: List<RoutineExerciseDto>
)

@Serializable
data class RoutineExerciseDto(
    val id: String,
    val sets: Int,
    val min: Int? = null,
    val speed: Double? = null,
    val mode: String? = null,
    val restSec: Int? = null,
    val warmupRestSec: Int? = null
)
