package com.example.opengym_wear.presentation.api

import kotlinx.serialization.Serializable

@Serializable
data class OpenGymResponse(
    val state: OpenGymState,
    val rev: Int
)

@Serializable
data class OpenGymState(
    val routines: List<RoutineDto>,
    val workouts: List<WorkoutDto>,
    val dayPlan: Map<String, String>
)

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
    val speed: Int? = null,
    val mode: String? = null
)

@Serializable
data class WorkoutDto(
    val id: String,
    val d: String,
    val start: Long,
    val end: Long,
    val routineId: String,
    val name: String,
    val bw: Double? = null,
    val entries: List<WorkoutEntryDto>
)

@Serializable
data class WorkoutEntryDto(
    val id: String,
    val sets: List<WorkoutSetDto>,
    val topW: Double? = null,
    val target: WorkoutTargetDto? = null
)

@Serializable
data class WorkoutSetDto(
    val min: Int? = null,
    val speed: Int? = null,
    val done: Boolean = false
)

@Serializable
data class WorkoutTargetDto(
    val id: String,
    val sets: Int,
    val min: Int? = null,
    val speed: Int? = null
)
