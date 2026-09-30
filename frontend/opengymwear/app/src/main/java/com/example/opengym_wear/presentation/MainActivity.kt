/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.opengym_wear.presentation

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.opengym_wear.presentation.data.model.WatchExercise
import com.example.opengym_wear.presentation.data.model.WatchWorkout
import com.example.opengym_wear.presentation.api.OpenGymApi
import com.example.opengym_wear.presentation.data.ExerciseRepository
import com.example.opengym_wear.presentation.data.WorkoutRepository
import com.example.opengym_wear.presentation.theme.OpengymwearTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI
import java.time.LocalDate

private const val PREFS_NAME = "opengym_watch_session"
private const val PREF_BASE_URL = "base_url"
private const val PREF_TOKEN = "session_token"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(PREFS_NAME, 0)
    }
    var baseUrl by remember { mutableStateOf(preferences.getString(PREF_BASE_URL, "") ?: "") }
    var token by remember { mutableStateOf(preferences.getString(PREF_TOKEN, "") ?: "") }
    var pairingCode by remember { mutableStateOf("") }
    var pairingError by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isPairing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var workout by remember { mutableStateOf<WatchWorkout?>(null) }
    var profileTheme by remember { mutableStateOf("dark") }
    var profileAccent by remember { mutableStateOf("lime") }
    var isSavingWorkout by remember { mutableStateOf(false) }
    var workoutSaved by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    val api = remember(baseUrl, token) {
        baseUrl.takeIf { it.isNotBlank() }?.let { OpenGymApi(it, token) }
    }

    DisposableEffect(api) {
        onDispose { api?.close() }
    }

    LaunchedEffect(api, token, refreshKey) {
        if (api == null || token.isBlank()) return@LaunchedEffect
        isLoading = true
        loadError = null
        workout = null
        workoutSaved = false
        saveError = null
        try {
            val plan = WorkoutRepository(api, ExerciseRepository(context)).getWorkoutFor(LocalDate.now())
            workout = plan.workout
            profileTheme = plan.theme
            profileAccent = plan.accent
            if (workout == null) loadError = "No workout planned for today."
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            loadError = exception.localizedMessage ?: "Couldn't reach the server."
        } finally {
            isLoading = false
        }
    }

    OpengymwearTheme(
        theme = profileTheme,
        accent = profileAccent,
    ) {
        when {
            token.isBlank() -> PairingFace(
                baseUrl = baseUrl,
                pairingCode = pairingCode,
                isPairing = isPairing,
                error = pairingError,
                onBaseUrlChange = { baseUrl = it },
                onPairingCodeChange = { pairingCode = it },
                onConnect = { enteredUrl, enteredCode ->
                    val normalizedUrl = normalizeBaseUrl(enteredUrl)
                    if (normalizedUrl == null) {
                        pairingError = "Enter a valid server URL beginning with http:// or https://."
                    } else {
                        coroutineScope.launch {
                            isPairing = true
                            pairingError = null
                            val pairingApi = OpenGymApi(normalizedUrl, "")
                            try {
                                val result = pairingApi.redeemPairingCode(enteredCode)
                                preferences.edit()
                                    .putString(PREF_BASE_URL, normalizedUrl)
                                    .putString(PREF_TOKEN, result.token)
                                    .apply()
                                baseUrl = normalizedUrl
                                token = result.token
                                pairingCode = ""
                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (exception: Exception) {
                                pairingError = exception.localizedMessage ?: "Pairing failed. Check the code and server URL."
                            } finally {
                                pairingApi.close()
                                isPairing = false
                            }
                        }
                    }
                }
            )
            isLoading -> WorkoutStatusFace("Loading today's workout", "Connecting to openGym…")
            workout != null -> WorkoutFaces(
                workout = workout!!,
                isSaving = isSavingWorkout,
                isSaved = workoutSaved,
                saveError = saveError,
                onFinish = { sets ->
                    val currentWorkout = workout ?: return@WorkoutFaces
                    val currentApi = api ?: return@WorkoutFaces
                    coroutineScope.launch {
                        isSavingWorkout = true
                        saveError = null
                        try {
                            currentApi.finishWorkout(currentWorkout, sets)
                            workoutSaved = true
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            saveError = exception.localizedMessage ?: "Couldn't save workout."
                        } finally {
                            isSavingWorkout = false
                        }
                    }
                },
            )
            else -> WorkoutStatusFace(
                title = "Workout unavailable",
                message = loadError ?: "Couldn't load today's workout.",
                actionLabel = "Try again",
                onAction = { refreshKey += 1 },
                secondaryActionLabel = "Pair again",
                onSecondaryAction = {
                    preferences.edit().remove(PREF_TOKEN).apply()
                    token = ""
                    workout = null
                },
            )
        }
    }
}

@Composable
private fun WorkoutFaces(
    workout: WatchWorkout,
    isSaving: Boolean,
    isSaved: Boolean,
    saveError: String?,
    onFinish: (List<List<Boolean>>) -> Unit,
) {
    val context = LocalContext.current
    AppScaffold {
        val pagerState = rememberPagerState(pageCount = { 3 })
        val completedSets = remember(workout) {
            workout.exercises.map { exercise ->
                mutableStateListOf<Boolean>().apply {
                    repeat(exercise.sets) { add(false) }
                }
            }
        }
        val selectedExercise = remember(workout) { mutableIntStateOf(0) }
        val selectedSet = remember(workout) { mutableIntStateOf(0) }
        var restDeadline by remember(workout) { mutableStateOf<Long?>(null) }
        var restRemaining by remember(workout) { mutableIntStateOf(0) }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(restDeadline) {
            val deadline = restDeadline ?: return@LaunchedEffect
            while (true) {
                val remainingMillis = deadline - System.currentTimeMillis()
                if (remainingMillis <= 0) {
                    restRemaining = 0
                    restDeadline = null
                    val vibrator = context.getSystemService(Vibrator::class.java)
                    if (vibrator?.hasVibrator() == true) {
                        vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    break
                }
                restRemaining = ((remainingMillis + 999) / 1000).toInt()
                delay(minOf(remainingMillis, 1000L))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val faceTitle = when (page) {
                0 -> "TODAY'S PLAN"
                1 -> "SET COUNTER"
                else -> "SESSION"
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = faceTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                    Spacer(Modifier.height(4.dp))

                when (page) {
                    0 -> DayRoutineFace(workout, completedSets) { index ->
                        selectedExercise.intValue = index
                        selectedSet.intValue = completedSets[index].indexOfFirst { !it }.coerceAtLeast(0)
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    }
                    1 -> SetCounterFace(
                        workout = workout,
                        completedSets = completedSets,
                        selectedIndex = selectedExercise.intValue,
                        selectedSet = selectedSet.intValue,
                        restRemaining = restRemaining,
                        onSelectExercise = { selectedExercise.intValue = it },
                        onSelectSet = { selectedSet.intValue = it },
                        onCompleteSet = { exerciseIndex, setIndex ->
                            val exerciseSets = completedSets[exerciseIndex]
                            if (setIndex in exerciseSets.indices && !exerciseSets[setIndex]) {
                                exerciseSets[setIndex] = true
                                    val nextSet = exerciseSets.indices.firstOrNull { !exerciseSets[it] }
                                if (nextSet != null) {
                                    selectedSet.intValue = nextSet
                                    val exercise = workout.exercises[exerciseIndex]
                                    val restSeconds = (exercise.restSeconds?.takeIf { it > 0 }
                                        ?: workout.defaultRestSeconds).coerceAtLeast(0)
                                    restDeadline = if (restSeconds > 0) {
                                        System.currentTimeMillis() + restSeconds * 1000L
                                    } else null
                                    restRemaining = restSeconds
                                }
                            }
                        },
                        onPreviousSet = {
                            selectedSet.intValue = (selectedSet.intValue - 1).coerceAtLeast(0)
                        },
                        onNextSet = {
                            val sets = completedSets[selectedExercise.intValue]
                            selectedSet.intValue = (selectedSet.intValue + 1).coerceAtMost((sets.size - 1).coerceAtLeast(0))
                            restDeadline = null
                            restRemaining = 0
                        },
                    )
                    else -> WorkoutSummaryFace(
                        workout = workout,
                        completedSets = completedSets,
                        isSaving = isSaving,
                        isSaved = isSaved,
                        saveError = saveError,
                        onFinish = { onFinish(completedSets.map { sets -> sets.toList() }) },
                    ) {
                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
                    }
                }

                Spacer(Modifier.height(4.dp))
                PageIndicator(currentPage = page) { targetPage ->
                    coroutineScope.launch { pagerState.animateScrollToPage(targetPage) }
                }
            }
        }
    }
}

@Composable
private fun PairingFace(
    baseUrl: String,
    pairingCode: String,
    isPairing: Boolean,
    error: String?,
    onBaseUrlChange: (String) -> Unit,
    onPairingCodeChange: (String) -> Unit,
    onConnect: (String, String) -> Unit,
) {
    AppScaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("openGym", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Connect watch", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            PairingInput(
                label = "Server URL",
                placeholder = "https://gym.example.com",
                value = baseUrl,
                onValueChange = onBaseUrlChange,
            )
            Spacer(Modifier.height(8.dp))
            PairingInput(
                label = "Pairing code",
                placeholder = "Code from web app",
                value = pairingCode,
                onValueChange = onPairingCodeChange,
            )
            Text(
                "Create a code in Settings > Pair the mobile app.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error != null) {
                Text(
                    error,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onConnect(baseUrl, pairingCode) },
                enabled = !isPairing && baseUrl.isNotBlank() && pairingCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isPairing) "Connecting…" else "Connect")
            }
        }
    }
}

@Composable
private fun PairingInput(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(3.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun WorkoutStatusFace(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    AppScaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                Button(onClick = onSecondaryAction, modifier = Modifier.fillMaxWidth()) {
                    Text(secondaryActionLabel)
                }
            }
        }
    }
}

private fun normalizeBaseUrl(value: String): String? = runCatching {
    val uri = URI(value.trim().trimEnd('/'))
    if (uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
        uri.toString().trimEnd('/')
    } else {
        null
    }
}.getOrNull()

@Composable
private fun WorkoutPreview() {
    OpengymwearTheme {
        WorkoutFaces(
            workout = sampleWorkout(),
            isSaving = false,
            isSaved = false,
            saveError = null,
            onFinish = {},
        )
    }
}

@Composable
private fun ColumnScope.DayRoutineFace(
    workout: WatchWorkout,
    completedSets: List<List<Boolean>>,
    onExerciseClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
            Text(workout.name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Text(
            "${completedSets.sumOf { sets -> sets.count { it } }} / ${workout.exercises.sumOf { it.sets }} sets",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        if (workout.exercises.isEmpty()) {
            Text("No exercises planned", textAlign = TextAlign.Center)
        } else {
            workout.exercises.forEachIndexed { index, exercise ->
                Button(
                    onClick = { onExerciseClick(index) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(exercise.name, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Text(
                            "${completedSets[index].count { it }} / ${exercise.sets} sets",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SetCounterFace(
    workout: WatchWorkout,
    completedSets: List<List<Boolean>>,
    selectedIndex: Int,
    selectedSet: Int,
    restRemaining: Int,
    onSelectExercise: (Int) -> Unit,
    onSelectSet: (Int) -> Unit,
    onCompleteSet: (Int, Int) -> Unit,
    onPreviousSet: () -> Unit,
    onNextSet: () -> Unit,
) {
    if (workout.exercises.isEmpty()) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("No exercises planned", textAlign = TextAlign.Center)
        }
        return
    }

    val exercise = workout.exercises[selectedIndex.coerceIn(workout.exercises.indices)]
    val exerciseSets = completedSets[selectedIndex]
    val setIndex = selectedSet.coerceIn(exerciseSets.indices)
    val isCurrentSetDone = exerciseSets[setIndex]
    val done = exerciseSets.count { it }
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = {
                val nextExercise = (selectedIndex - 1 + workout.exercises.size) % workout.exercises.size
                onSelectExercise(nextExercise)
                onSelectSet(completedSets[nextExercise].indexOfFirst { !it }.coerceAtLeast(0))
            }) {
                Text("Exercise ‹")
            }
            Text(
                "${selectedIndex + 1} / ${workout.exercises.size}",
                style = MaterialTheme.typography.labelSmall,
            )
            Button(onClick = {
                val nextExercise = (selectedIndex + 1) % workout.exercises.size
                onSelectExercise(nextExercise)
                onSelectSet(completedSets[nextExercise].indexOfFirst { !it }.coerceAtLeast(0))
            }) {
                Text("Exercise ›")
            }
        }
        Text(
            exercise.name,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
        Text(
            exercise.target ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text("$done / ${exercise.sets}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("SETS", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        Text("Set ${setIndex + 1} of ${exercise.sets}", style = MaterialTheme.typography.labelSmall)
        if (restRemaining > 0) {
            Text(
                "Rest ${restRemaining / 60}:${(restRemaining % 60).toString().padStart(2, '0')}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onPreviousSet,
                enabled = setIndex > 0,
            ) {
                Text("←")
            }
            Button(
                onClick = { onCompleteSet(selectedIndex, setIndex) },
                enabled = !isCurrentSetDone,
            ) {
                Text("✓")
            }
            Button(
                onClick = onNextSet,
                enabled = setIndex < exercise.sets - 1,
            ) {
                Text("→")
            }
        }
    }
}

@Composable
private fun ColumnScope.WorkoutSummaryFace(
    workout: WatchWorkout,
    completedSets: List<List<Boolean>>,
    isSaving: Boolean,
    isSaved: Boolean,
    saveError: String?,
    onFinish: (List<List<Boolean>>) -> Unit,
    onBackToPlan: () -> Unit,
) {
    val totalSets = workout.exercises.sumOf { it.sets }
    val doneSets = completedSets.sumOf { sets -> sets.count { it } }
    var confirmFinish by remember(workout) { mutableStateOf(false) }
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(workout.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        Text("$doneSets / $totalSets", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("SETS COMPLETE", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        workout.exercises.forEachIndexed { index, exercise ->
            Text(
                "${completedSets[index].count { it }} / ${exercise.sets} · ${exercise.name}",
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (saveError != null) {
            Text(saveError, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
        }
        Button(
            onClick = {
                if (!isSaved && !isSaving) {
                    if (doneSets < totalSets && !confirmFinish) confirmFinish = true
                    else onFinish(completedSets.map { sets -> sets.toList() })
                }
            },
            enabled = !isSaving && !isSaved,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Text(when {
                isSaved -> "Workout saved"
                isSaving -> "Saving…"
                confirmFinish -> "Finish anyway?"
                else -> "Finish workout"
            })
        }
        Button(onClick = onBackToPlan, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Back to plan")
        }
    }
}

@Composable
private fun PageIndicator(currentPage: Int, onPageClick: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { page ->
            Spacer(
                modifier = Modifier
                    .size(if (page == currentPage) 8.dp else 6.dp)
                    .background(
                        color = if (page == currentPage) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = CircleShape,
                    )
                    .clickable { onPageClick(page) },
            )
        }
    }
}

private fun sampleWorkout() = WatchWorkout(
    id = "today",
    name = "Strength day",
    exercises = listOf(
        WatchExercise("squat", "Goblet squat", "Dumbbell", "8-10 reps", 3, null, null),
        WatchExercise("press", "Chest press", "Dumbbell", "8-12 reps", 3, null, null),
        WatchExercise("row", "Bent-over row", "Dumbbell", "10-12 reps", 3, null, null),
        WatchExercise("plank", "Plank", null, "30 sec", 3, 30, null),
    ),
    date = LocalDate.now().toString(),
    startedAt = System.currentTimeMillis(),
    routineIds = listOf("today"),
    theme = "dark",
    accent = "lime",
    defaultRestSeconds = 90,
)

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    WorkoutPreview()
}