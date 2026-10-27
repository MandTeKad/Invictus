package com.invictus.warrior

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = InvictusDatabase.get(this)
        val dao = db.dao()
        val campaignDao = db.campaignDao()

        // Import registry data on first launch (non-blocking)
        val appContext = applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            CampaignRepository.ensureRegistryImported(appContext, campaignDao)
        }

        // Ensure background step-sync worker is running (keeps widget fresh)
        StepSyncWorker.schedule(appContext)

        setContent { InvictusApp(dao, campaignDao) }
    }
}

@Composable
private fun InvictusApp(dao: TrainingDao, campaignDao: CampaignDao) {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1500)
        showSplash = false
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Inv.Gold,
            background = Inv.Background,
            surface = Inv.Card,
            onBackground = Inv.TextPrimary,
            onSurface = Inv.TextPrimary,
            secondary = Inv.Gold
        )
    ) {
        Crossfade(
            targetState = showSplash,
            animationSpec = tween(durationMillis = 700),
            label = "invictus_startup_fade"
        ) { splash ->
            if (splash) InvictusSplashScreen()
            else InvictusMainScaffold(dao, campaignDao)
        }
    }
}

/* ── Splash ─────────────────────────────────────────────────── */

@Composable
private fun InvictusSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Inv.NavBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            LaurelWreathLogo(modifier = Modifier.size(180.dp), color = Inv.Gold)
            Text(
                "INVICTUS",
                color = Inv.Gold,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Text(
                "BY WILL AND WRATH",
                color = Inv.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp
            )
        }
    }
}

@Composable
private fun LaurelWreathLogo(modifier: Modifier = Modifier, color: Color = Inv.Gold) {
    Canvas(modifier = modifier) {
        val minDim = min(size.width, size.height)
        val strokeWidth = minDim * 0.025f
        val arcSize = Size(minDim * 0.72f, minDim * 0.72f)
        val topLeft = Offset((size.width - arcSize.width) / 2f, (size.height - arcSize.height) / 2f)

        drawArc(color, 132f, 108f, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        drawArc(color, 300f, 108f, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))

        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minDim * 0.31f
        val leafW = minDim * 0.11f
        val leafH = minDim * 0.045f

        fun drawLeaf(angleDeg: Float, tilt: Float) {
            val rad = angleDeg * PI.toFloat() / 180f
            val x = center.x + radius * cos(rad)
            val y = center.y + radius * sin(rad)
            rotate(angleDeg + tilt, Offset(x, y)) {
                drawOval(color, Offset(x - leafW / 2f, y - leafH / 2f), Size(leafW, leafH))
            }
        }

        listOf(141f, 154f, 168f, 183f, 198f, 213f, 227f).forEach { drawLeaf(it, 42f) }
        listOf(399f, 386f, 372f, 357f, 342f, 327f, 313f).forEach { drawLeaf(it, -42f) }

        drawLine(color,
            Offset(center.x - minDim * 0.07f, center.y + minDim * 0.30f),
            Offset(center.x + minDim * 0.07f, center.y + minDim * 0.30f),
            strokeWidth, StrokeCap.Round)
    }
}

/* ── Main scaffold ──────────────────────────────────────────── */

@Composable
private fun InvictusMainScaffold(dao: TrainingDao, campaignDao: CampaignDao) {
    val sets by dao.allSets().collectAsStateWithLifecycle(initialValue = emptyList())
    val weights by dao.weights().collectAsStateWithLifecycle(initialValue = emptyList())
    val recoveries by dao.recoveries().collectAsStateWithLifecycle(initialValue = emptyList())
    val sessions by dao.allSessions().collectAsStateWithLifecycle(initialValue = emptyList())

    // Campaign data
    val goals by campaignDao.allGoals().collectAsStateWithLifecycle(initialValue = emptyList())
    val goalProgress by campaignDao.allGoalProgress().collectAsStateWithLifecycle(initialValue = emptyList())
    val milestoneProgress by campaignDao.allMilestoneProgress().collectAsStateWithLifecycle(initialValue = emptyList())
    val currentRank by campaignDao.currentRankFlow().collectAsStateWithLifecycle(initialValue = null)
    val totalXP by campaignDao.totalXPFlow().collectAsStateWithLifecycle(initialValue = 0)

    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var selectedDay by remember { mutableStateOf(TrainingPlan.today()) }

    // Campaign navigation: null = dashboard, non-null = goal detail
    var selectedGoalId by remember { mutableStateOf<String?>(null) }

    // Active workout session
    var activeSession by remember { mutableStateOf<WorkoutSession?>(null) }
    LaunchedEffect(Unit) { activeSession = dao.activeSession() }

    Scaffold(
        containerColor = Inv.Background,
        bottomBar = {
            NavigationBar(
                containerColor = Inv.NavBackground,
                tonalElevation = 0.dp
            ) {
                val labels = listOf("Command", "Campaign", "Forge", "Records", "Recovery")
                val icons = listOf("⌂", "♆", "⚔", "☰", "♡")
                labels.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = {
                            tab = index
                            if (index != 1) selectedGoalId = null // Reset campaign navigation
                        },
                        icon = { Text(icons[index], fontSize = 21.sp) },
                        label = { Text(title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Inv.Gold,
                            selectedTextColor = Inv.Gold,
                            indicatorColor = Inv.NavIndicator,
                            unselectedIconColor = Inv.TextTertiary,
                            unselectedTextColor = Inv.TextTertiary
                        )
                    )
                }
            }
        }
    ) { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Inv.ScreenPad, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(Inv.CardGap)
        ) {
            // Brand header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("INVICTUS", color = Inv.Gold, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text("v0.2.2", color = Inv.TextTertiary, fontSize = 12.sp)
            }

            when (tab) {
                0 -> CommandScreen(sets, weights, recoveries, sessions, onTrain = { tab = 2 }, onSteps = { tab = 5 })
                1 -> {
                    if (selectedGoalId != null) {
                        GoalDetailScreen(
                            goalId = selectedGoalId!!,
                            campaignDao = campaignDao,
                            scope = scope,
                            onBack = { selectedGoalId = null }
                        )
                    } else {
                        CampaignScreen(
                            goals = goals,
                            goalProgress = goalProgress,
                            milestoneProgress = milestoneProgress,
                            currentRank = currentRank,
                            totalXP = totalXP,
                            campaignDao = campaignDao,
                            scope = scope,
                            onGoalSelected = { selectedGoalId = it }
                        )
                    }
                }
                2 -> ForgeScreen(
                    selectedDay = selectedDay,
                    onDay = { selectedDay = it },
                    sets = sets,
                    dao = dao,
                    activeSession = activeSession,
                    onSessionChange = { activeSession = it },
                    scope = scope
                )
                3 -> RecordsScreen(sets, weights, dao, scope)
                4 -> RecoveryScreen(recoveries, dao, scope)
                5 -> ActivityTrackingScreen(weights.firstOrNull()?.pounds ?: 287.0, dao)
            }
        }
    }
}

/* ── Command (Dashboard) ────────────────────────────────────── */

@Composable
private fun CommandScreen(
    sets: List<WorkoutSet>,
    weights: List<WeightEntry>,
    recoveries: List<RecoveryEntry>,
    sessions: List<WorkoutSession>,
    onTrain: () -> Unit,
    onSteps: () -> Unit
) {
    ScreenTitle("Command", "Your training, today")

    val latestRecovery = recoveries.firstOrNull()
    val recommendation = AdaptiveSchedule.recommend(
        TrainingPlan.today(),
        latestRecovery
    )

    InvCard(goldBorder = true) {
        CardLabel("Campaign 01 · ${TrainingPlan.phase().uppercase()}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${weights.firstOrNull()?.pounds ?: 287.0} lb",
                color = Inv.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text("Aim: muscular & capable", color = Inv.TextTertiary, fontSize = 12.sp)
        }
        Text(
            "Starting weight 287 lb · Outcome judged by physique and performance",
            color = Inv.TextSecondary, fontSize = 12.sp
        )
    }

    InvCard(elevated = true) {
        CardLabel("Today · ${TrainingPlan.today()}")
        Text(
            recommendation.day.title,
            color = Inv.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold
        )
        if (recommendation.isReduced) {
            Text(recommendation.reason, color = Inv.GoldMuted, fontSize = 13.sp)
        }
        Text(
            if (recommendation.day.exercises.isEmpty()) "Work-shift activity. Prioritize recovery."
            else "${recommendation.day.exercises.size} planned movements",
            color = Inv.TextSecondary, fontSize = 13.sp
        )
        InvButton(
            text = "Open training",
            onClick = onTrain,
            enabled = recommendation.day.exercises.isNotEmpty()
        )
    }

    InvCard {
        CardLabel("Current Status")
        StatusRow("Logged sets", sets.size.toString())
        StatusRow("Completed sessions", sessions.count { it.status == "finished" }.toString())
        val lastSession = sessions.firstOrNull { it.status == "finished" }
        if (lastSession != null) {
            StatusRow("Last mode", lastSession.trainingMode.ifEmpty { "HYBRID" })
            if (lastSession.equipmentProfile.isNotEmpty()) {
                StatusRow("Last equipment", lastSession.equipmentProfile)
            }
        }
        StatusRow("Recovery", latestRecovery?.let {
            RecoveryAdvice.suggest(it.sleepHours, it.fatigue, it.steps, it.pain)
        } ?: "Not entered")
    }

    // Weight trend chart
    if (weights.size >= 2) {
        InvCard {
            CardLabel("Weight Trend")
            WeightTrendChart(weights.take(14).reversed())
        }
    }

    InvCard {
        CardLabel("Daily Movement")
        Text("Track steps via Health Connect or foreground sensor",
            color = Inv.TextSecondary, fontSize = 13.sp)
        InvOutlinedButton(text = "Open Step Tracker", onClick = onSteps)
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Inv.TextSecondary, fontSize = 13.sp)
        Text(value, color = Inv.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/* ── Weight trend chart ─────────────────────────────────────── */

@Composable
private fun WeightTrendChart(entries: List<WeightEntry>) {
    if (entries.size < 2) return
    val gold = Inv.Gold
    val border = Inv.Border
    val textTertiary = Inv.TextTertiary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val minW = entries.minOf { it.pounds }.toFloat() - 2f
        val maxW = entries.maxOf { it.pounds }.toFloat() + 2f
        val range = (maxW - minW).coerceAtLeast(1f)
        val padBottom = 20.dp.toPx()
        val chartH = size.height - padBottom
        val stepX = size.width / (entries.size - 1).coerceAtLeast(1)

        // Grid lines
        val gridCount = 3
        for (i in 0..gridCount) {
            val y = chartH * i / gridCount
            drawLine(border.copy(alpha = 0.3f), Offset(0f, y), Offset(size.width, y), 1f)
        }

        // Line path
        val path = Path()
        entries.forEachIndexed { index, entry ->
            val x = index * stepX
            val y = chartH - ((entry.pounds.toFloat() - minW) / range) * chartH
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, gold, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        // Dots
        entries.forEachIndexed { index, entry ->
            val x = index * stepX
            val y = chartH - ((entry.pounds.toFloat() - minW) / range) * chartH
            drawCircle(gold, 3.dp.toPx(), Offset(x, y))
        }

        // Min/max labels
        val paint = android.graphics.Paint().apply {
            color = textTertiary.hashCode()
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            String.format(Locale.US, "%.0f", maxW - 2),
            4.dp.toPx(), 14.dp.toPx(), paint
        )
        drawContext.canvas.nativeCanvas.drawText(
            String.format(Locale.US, "%.0f", minW + 2),
            4.dp.toPx(), chartH - 4.dp.toPx(), paint
        )
    }
}

/* ── Forge (Workout) ────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForgeScreen(
    selectedDay: String,
    onDay: (String) -> Unit,
    sets: List<WorkoutSet>,
    dao: TrainingDao,
    activeSession: WorkoutSession?,
    onSessionChange: (WorkoutSession?) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    ScreenTitle("Forge", "Log a real workout — nothing is auto-completed")

    // Training mode and equipment profile state
    var trainingMode by remember { mutableStateOf(TrainingMode.DEFAULT) }
    var equipmentProfile by remember { mutableStateOf(EquipmentProfile.TREX_FULL_GYM) }

    // Mode selector
    InvCard {
        CardLabel("Training Mode")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrainingMode.entries.forEach { mode ->
                val selected = trainingMode == mode
                if (selected) {
                    InvButton(
                        text = mode.label,
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    InvOutlinedButton(
                        text = mode.label,
                        onClick = { trainingMode = mode },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Equipment profile selector
        var eqExpanded by remember { mutableStateOf(false) }
        Spacer(Modifier.height(6.dp))
        ExposedDropdownMenuBox(expanded = eqExpanded, onExpandedChange = { eqExpanded = it }) {
            InvField(
                value = equipmentProfile.name,
                onValueChange = {},
                label = "Equipment",
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = eqExpanded,
                onDismissRequest = { eqExpanded = false },
                containerColor = Inv.CardElevated
            ) {
                EquipmentProfile.PRESETS.forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(preset.name, color = Inv.TextPrimary, fontSize = 14.sp)
                                Text(preset.description, color = Inv.TextTertiary, fontSize = 11.sp)
                            }
                        },
                        onClick = { equipmentProfile = preset; eqExpanded = false }
                    )
                }
            }
        }
        if (equipmentProfile.notes.isNotEmpty()) {
            Text(equipmentProfile.notes, color = Inv.TextTertiary, fontSize = 11.sp)
        }
    }

    // Session controls
    InvCard(goldBorder = activeSession != null) {
        CardLabel(if (activeSession != null) "Session Active" else "Session")
        if (activeSession != null) {
            val elapsed = (System.currentTimeMillis() - activeSession.startedAt) / 60000
            Text(
                "${activeSession.day} · ${elapsed} min · ${trainingMode.label}",
                color = Inv.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium
            )
            Text(
                "Status: ${activeSession.status.replaceFirstChar { it.uppercase() }}",
                color = Inv.TextSecondary, fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (activeSession.status == "active") {
                    InvOutlinedButton(text = "Pause", onClick = {
                        scope.launch {
                            dao.updateSessionStatus(activeSession.id, "paused")
                            onSessionChange(activeSession.copy(status = "paused"))
                        }
                    })
                } else if (activeSession.status == "paused") {
                    InvButton(text = "Resume", onClick = {
                        scope.launch {
                            dao.updateSessionStatus(activeSession.id, "active")
                            onSessionChange(activeSession.copy(status = "active"))
                        }
                    })
                }
                InvButton(text = "Finish Session", onClick = {
                    scope.launch {
                        dao.finishSession(activeSession.id)
                        onSessionChange(null)
                    }
                })
            }
        } else {
            Text("No active session", color = Inv.TextTertiary, fontSize = 13.sp)
            InvButton(text = "Start Session", onClick = {
                scope.launch {
                    val id = dao.insertSession(WorkoutSession(
                        day = selectedDay,
                        trainingMode = trainingMode.name,
                        equipmentProfile = equipmentProfile.name
                    ))
                    onSessionChange(WorkoutSession(
                        id = id, day = selectedDay,
                        trainingMode = trainingMode.name,
                        equipmentProfile = equipmentProfile.name
                    ))
                }
            })
        }
    }

    // Day selector
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        InvField(
            value = selectedDay,
            onValueChange = {},
            label = "Training day",
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Inv.CardElevated
        ) {
            TrainingPlan.days.keys.forEach { key ->
                DropdownMenuItem(
                    text = { Text(key, color = Inv.TextPrimary) },
                    onClick = { onDay(key); expanded = false }
                )
            }
        }
    }

    val plan = TrainingPlan.days.getValue(selectedDay)
    val modeExercises = TrainingPlan.exercisesForMode(selectedDay, trainingMode, equipmentProfile)
    Text(plan.title, color = Inv.Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)

    if (plan.exercises.isEmpty()) {
        Text(
            "Recovery and occupational activity day. No formal session scheduled.",
            color = Inv.TextSecondary, fontSize = 14.sp
        )
    } else if (modeExercises.isEmpty()) {
        Text(
            "No exercises available for ${trainingMode.label} mode with ${equipmentProfile.name}. Try changing mode or equipment.",
            color = Inv.TextSecondary, fontSize = 14.sp
        )
    }

    // Rest timer
    var restSeconds by remember { mutableIntStateOf(0) }
    var restRunning by remember { mutableStateOf(false) }
    LaunchedEffect(restRunning) {
        if (restRunning) {
            while (restSeconds > 0) {
                delay(1000)
                restSeconds--
            }
            restRunning = false
        }
    }

    if (restRunning || restSeconds > 0) {
        InvCard(elevated = true) {
            CardLabel("Rest Timer")
            val minutes = restSeconds / 60
            val secs = restSeconds % 60
            BigValue(String.format("%d:%02d", minutes, secs))
            if (!restRunning) {
                Text("Rest complete!", color = Inv.Success, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    // Exercise loggers
    val sessionId = activeSession?.id ?: System.currentTimeMillis()
    modeExercises.forEach { exercise ->
        key(selectedDay, exercise.name, trainingMode) {
            ExerciseLogger(
                exercise = exercise,
                previous = sets.filter { it.exercise == exercise.name },
                sessionId = sessionId,
                dao = dao,
                scope = scope,
                trainingMode = trainingMode,
                equipmentProfile = equipmentProfile,
                onSetSaved = {
                    restSeconds = 90
                    restRunning = true
                }
            )
        }
    }
}

@Composable
private fun ExerciseLogger(
    exercise: PlannedExercise,
    previous: List<WorkoutSet>,
    sessionId: Long,
    dao: TrainingDao,
    scope: kotlinx.coroutines.CoroutineScope,
    trainingMode: TrainingMode,
    equipmentProfile: EquipmentProfile,
    onSetSaved: () -> Unit
) {
    val def = ExerciseLibrary.find(exercise.name)
    val measureType = def?.primaryMeasurement ?: MeasurementType.WEIGHT_REPS
    val last = previous.firstOrNull()

    var weight by remember(exercise.name) { mutableStateOf(last?.weightLb?.toString() ?: "") }
    var reps by remember(exercise.name) { mutableStateOf("") }
    var holdSec by remember(exercise.name) { mutableStateOf("") }
    var duration by remember(exercise.name) { mutableStateOf("") }
    var distance by remember(exercise.name) { mutableStateOf("") }
    var assistLb by remember(exercise.name) { mutableStateOf("") }
    var effort by remember(exercise.name) { mutableStateOf("") }
    var notes by remember(exercise.name) { mutableStateOf("") }
    var setNumber by remember(exercise.name) { mutableIntStateOf(1) }
    var feedback by remember(exercise.name) { mutableStateOf("") }
    var showSubstitutions by remember(exercise.name) { mutableStateOf(false) }

    // Progression suggestion (not auto-applied)
    val suggestion = remember(last, exercise.name) {
        val calisthenics = CalisthenicsProgression.suggestProgression(exercise.name)
        if (calisthenics != null && !calisthenics.isMaxLevel) {
            val next = calisthenics.nextLevel
            if (next != null) "Progression available: ${next.name} (${next.assessmentCriteria})" else null
        } else if (last != null && measureType == MeasurementType.WEIGHT_REPS &&
            last.reps >= (exercise.repRange.split("–", "–").lastOrNull()?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 999)) {
            "Consider increasing weight by 2.5–5 lb next session (suggestion only)."
        } else null
    }

    InvCard {
        CardLabel(exercise.name)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Plan: ${exercise.sets} sets · ${exercise.repRange}",
                color = Inv.TextTertiary, fontSize = 13.sp
            )
            Text(measureType.label, color = Inv.TextTertiary, fontSize = 11.sp)
        }
        if (def?.isBodyweight == true) {
            Text("Bodyweight", color = Inv.GoldMuted, fontSize = 11.sp)
        }
        if (last != null) {
            val prevText = when (MeasurementType.fromString(last.measurementType)) {
                MeasurementType.WEIGHT_REPS -> "Previous: ${last.weightLb} lb × ${last.reps}"
                MeasurementType.REPS_ONLY -> "Previous: ${last.reps} reps"
                MeasurementType.HOLD_DURATION -> "Previous: ${last.holdSeconds ?: 0}s hold"
                MeasurementType.DURATION_DISTANCE -> "Previous: ${last.durationMinutes ?: 0} min · ${last.distanceKm ?: 0} km"
                MeasurementType.ASSISTED_WEIGHT -> "Previous: ${last.reps} reps (${last.assistanceLb ?: 0} lb assist)"
                MeasurementType.SKILL_LEVEL -> "Previous: ${last.skillLevel ?: "—"}"
            }
            Text(prevText, color = Inv.TextSecondary, fontSize = 12.sp)
        }
        if (suggestion != null) {
            Text(suggestion, color = Inv.GoldHighlight, fontSize = 12.sp)
        }
        if (def?.safetyNotes?.isNotEmpty() == true) {
            Text("⚠ ${def.safetyNotes}", color = Inv.GoldMuted, fontSize = 11.sp)
        }

        // Measurement inputs based on type
        when (measureType) {
            MeasurementType.WEIGHT_REPS -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InvField(value = weight, onValueChange = { weight = it }, label = "lb",
                        modifier = Modifier.weight(1f), decimal = true)
                    InvField(value = reps, onValueChange = { reps = it }, label = "Reps",
                        modifier = Modifier.weight(1f), numeric = true)
                }
            }
            MeasurementType.REPS_ONLY -> {
                InvField(value = reps, onValueChange = { reps = it }, label = "Reps",
                    modifier = Modifier.fillMaxWidth(), numeric = true)
            }
            MeasurementType.HOLD_DURATION -> {
                InvField(value = holdSec, onValueChange = { holdSec = it }, label = "Seconds",
                    modifier = Modifier.fillMaxWidth(), numeric = true)
            }
            MeasurementType.DURATION_DISTANCE -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InvField(value = duration, onValueChange = { duration = it }, label = "Minutes",
                        modifier = Modifier.weight(1f), decimal = true)
                    InvField(value = distance, onValueChange = { distance = it }, label = "km",
                        modifier = Modifier.weight(1f), decimal = true)
                }
            }
            MeasurementType.ASSISTED_WEIGHT -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InvField(value = reps, onValueChange = { reps = it }, label = "Reps",
                        modifier = Modifier.weight(1f), numeric = true)
                    InvField(value = assistLb, onValueChange = { assistLb = it }, label = "Assist lb",
                        modifier = Modifier.weight(1f), decimal = true)
                }
            }
            MeasurementType.SKILL_LEVEL -> {
                InvField(value = notes, onValueChange = { notes = it }, label = "Skill / technique notes",
                    modifier = Modifier.fillMaxWidth())
            }
        }

        // Optional RPE field
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InvField(value = effort, onValueChange = { effort = it }, label = "RPE (1–10)",
                modifier = Modifier.weight(1f), numeric = true)
            if (measureType != MeasurementType.SKILL_LEVEL) {
                InvField(value = notes, onValueChange = { notes = it }, label = "Notes",
                    modifier = Modifier.weight(2f))
            }
        }

        InvButton(
            text = "Save set $setNumber",
            onClick = {
                val valid = when (measureType) {
                    MeasurementType.WEIGHT_REPS -> {
                        weight.toDoubleOrNull() != null && (reps.toIntOrNull() ?: 0) > 0
                    }
                    MeasurementType.REPS_ONLY -> (reps.toIntOrNull() ?: 0) > 0
                    MeasurementType.HOLD_DURATION -> (holdSec.toIntOrNull() ?: 0) > 0
                    MeasurementType.DURATION_DISTANCE -> (duration.toDoubleOrNull() ?: 0.0) > 0
                    MeasurementType.ASSISTED_WEIGHT -> (reps.toIntOrNull() ?: 0) > 0
                    MeasurementType.SKILL_LEVEL -> notes.isNotBlank()
                }
                if (valid) {
                    scope.launch {
                        dao.addSet(WorkoutSet(
                            sessionId = sessionId,
                            day = TrainingPlan.today(),
                            exercise = exercise.name,
                            setNumber = setNumber,
                            weightLb = weight.toDoubleOrNull() ?: 0.0,
                            reps = reps.toIntOrNull() ?: 0,
                            measurementType = measureType.name,
                            holdSeconds = holdSec.toIntOrNull(),
                            durationMinutes = duration.toDoubleOrNull(),
                            distanceKm = distance.toDoubleOrNull(),
                            assistanceLb = assistLb.toDoubleOrNull(),
                            perceivedEffort = effort.toIntOrNull()?.coerceIn(1, 10),
                            techniqueNotes = notes.ifBlank { null },
                            trainingMode = trainingMode.name
                        ))
                    }
                    setNumber++
                    feedback = "Set saved"
                    reps = ""; holdSec = ""; duration = ""; distance = ""; effort = ""; notes = ""
                    onSetSaved()
                } else {
                    feedback = when (measureType) {
                        MeasurementType.WEIGHT_REPS -> "Enter valid weight and reps."
                        MeasurementType.REPS_ONLY -> "Enter number of reps."
                        MeasurementType.HOLD_DURATION -> "Enter hold time in seconds."
                        MeasurementType.DURATION_DISTANCE -> "Enter duration in minutes."
                        MeasurementType.ASSISTED_WEIGHT -> "Enter reps performed."
                        MeasurementType.SKILL_LEVEL -> "Enter technique notes."
                    }
                }
            }
        )
        if (feedback.isNotEmpty()) {
            Text(
                feedback,
                color = if (feedback.startsWith("Set")) Inv.Success else Inv.Error,
                fontSize = 12.sp
            )
        }

        // Substitution toggle
        if (def != null) {
            TextButton(onClick = { showSubstitutions = !showSubstitutions }) {
                Text(
                    if (showSubstitutions) "Hide alternatives" else "Show alternatives",
                    color = Inv.GoldMuted, fontSize = 12.sp
                )
            }
            if (showSubstitutions) {
                val subs = ExerciseLibrary.findSubstitutions(exercise.name, trainingMode, equipmentProfile)
                if (subs.isEmpty()) {
                    Text("No alternatives available for current mode and equipment.",
                        color = Inv.TextTertiary, fontSize = 12.sp)
                } else {
                    subs.take(4).forEach { sub ->
                        Column(Modifier.padding(vertical = 2.dp)) {
                            Text("→ ${sub.replacement}", color = Inv.TextPrimary, fontSize = 13.sp)
                            Text(sub.equivalenceNote, color = Inv.TextTertiary, fontSize = 11.sp)
                        }
                    }
                    Text(
                        "Alternatives share a movement pattern. They are not mechanically identical.",
                        color = Inv.TextTertiary, fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/* ── Records ────────────────────────────────────────────────── */

@Composable
private fun RecordsScreen(
    sets: List<WorkoutSet>,
    weights: List<WeightEntry>,
    dao: TrainingDao,
    scope: kotlinx.coroutines.CoroutineScope
) {
    ScreenTitle("Records", "Local workout history and body weight")

    // Add weight
    var weight by remember { mutableStateOf("") }
    InvCard {
        CardLabel("Body Weight")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            InvField(
                value = weight,
                onValueChange = { weight = it },
                label = "Weight (lb)",
                modifier = Modifier.weight(1f),
                decimal = true
            )
            InvButton(text = "Add", onClick = {
                weight.toDoubleOrNull()?.takeIf { it in 50.0..800.0 }?.let {
                    scope.launch { dao.addWeight(WeightEntry(pounds = it)) }
                    weight = ""
                }
            })
        }
        weights.take(5).forEach {
            Text(
                "${it.pounds} lb · ${displayDate(it.timestamp)}",
                color = Inv.TextSecondary, fontSize = 12.sp
            )
        }
        if (weights.isEmpty()) Text("No entries yet.", color = Inv.TextTertiary, fontSize = 13.sp)
    }

    // Weight trend
    if (weights.size >= 2) {
        InvCard {
            CardLabel("Weight Trend")
            WeightTrendChart(weights.take(14).reversed())
        }
    }

    // Recent sets
    InvCard {
        CardLabel("Recent Sets · ${sets.size} Total")
        if (sets.isEmpty()) {
            Text("Log your first set in Forge.", color = Inv.TextTertiary, fontSize = 13.sp)
        }
        sets.take(30).forEach { item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val detail = when (MeasurementType.fromString(item.measurementType)) {
                        MeasurementType.WEIGHT_REPS -> "${item.weightLb} lb × ${item.reps}"
                        MeasurementType.REPS_ONLY -> "${item.reps} reps"
                        MeasurementType.HOLD_DURATION -> "${item.holdSeconds ?: 0}s hold"
                        MeasurementType.DURATION_DISTANCE -> "${item.durationMinutes ?: 0} min · ${item.distanceKm ?: 0} km"
                        MeasurementType.ASSISTED_WEIGHT -> "${item.reps} reps (${item.assistanceLb ?: 0} lb assist)"
                        MeasurementType.SKILL_LEVEL -> item.skillLevel ?: "—"
                    }
                    Text(
                        "${item.exercise}: $detail",
                        color = Inv.TextPrimary, fontSize = 13.sp
                    )
                    val modeTag = item.trainingMode?.let { " · $it" } ?: ""
                    val rpeTag = item.perceivedEffort?.let { " · RPE $it" } ?: ""
                    Text(
                        "${item.day} · ${displayDate(item.timestamp)}$modeTag$rpeTag",
                        color = Inv.TextTertiary, fontSize = 11.sp
                    )
                }
                TextButton(onClick = { scope.launch { dao.deleteSet(item.id) } }) {
                    Text("Delete", color = Inv.GoldMuted, fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = Inv.Border.copy(alpha = 0.4f))
        }
    }
}

/* ── Recovery ───────────────────────────────────────────────── */

@Composable
private fun RecoveryScreen(
    recoveries: List<RecoveryEntry>,
    dao: TrainingDao,
    scope: kotlinx.coroutines.CoroutineScope
) {
    ScreenTitle("Recovery", "Simple check-in — not medical assessment")

    var sleep by remember { mutableStateOf("7") }
    var fatigue by remember { mutableFloatStateOf(4f) }
    var steps by remember { mutableStateOf("5000") }
    var pain by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val h = sleep.toDoubleOrNull() ?: 0.0
    val s = steps.toIntOrNull() ?: 0

    InvCard(goldBorder = true) {
        CardLabel("Today's Check-In")
        InvField(
            value = sleep,
            onValueChange = { sleep = it },
            label = "Sleep hours",
            modifier = Modifier.fillMaxWidth(),
            decimal = true
        )
        Column {
            Text("Fatigue: ${fatigue.toInt()} / 10", color = Inv.TextSecondary, fontSize = 13.sp)
            Slider(
                value = fatigue,
                onValueChange = { fatigue = it },
                valueRange = 1f..10f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = Inv.Gold,
                    activeTrackColor = Inv.Gold,
                    inactiveTrackColor = Inv.Border
                )
            )
        }
        InvField(
            value = steps,
            onValueChange = { steps = it },
            label = "Previous-day steps",
            modifier = Modifier.fillMaxWidth(),
            numeric = true
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = pain,
                onCheckedChange = { pain = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = Inv.Gold,
                    uncheckedColor = Inv.Border,
                    checkmarkColor = Inv.DeepBackground
                )
            )
            Text("Pain or concerning symptoms", color = Inv.TextSecondary, fontSize = 14.sp)
        }
        Text(
            RecoveryAdvice.suggest(h, fatigue.toInt(), s, pain),
            color = Inv.Gold, fontSize = 14.sp, fontWeight = FontWeight.Medium
        )

        // Show adaptive schedule recommendation
        val recommendation = AdaptiveSchedule.recommend(
            TrainingPlan.today(),
            if (saved) recoveries.firstOrNull() else null
        )
        if (recommendation.isReduced) {
            Text(
                "Schedule: ${recommendation.reason}",
                color = Inv.GoldMuted, fontSize = 12.sp
            )
        }

        InvButton(
            text = "Save check-in",
            onClick = {
                if (h in 0.0..24.0 && s in 0..100000) {
                    scope.launch {
                        dao.addRecovery(RecoveryEntry(sleepHours = h, fatigue = fatigue.toInt(), steps = s, pain = pain))
                    }
                    saved = true
                }
            }
        )
    }

    InvCard {
        CardLabel("Recent Check-Ins")
        if (recoveries.isEmpty()) {
            Text("No check-ins yet.", color = Inv.TextTertiary, fontSize = 13.sp)
        }
        recoveries.take(5).forEach {
            Text(
                "${displayDate(it.timestamp)} · ${it.sleepHours}h sleep · fatigue ${it.fatigue}/10",
                color = Inv.TextSecondary, fontSize = 12.sp
            )
        }
    }
}

/* ── Utilities ──────────────────────────────────────────────── */

private fun displayDate(epoch: Long): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epoch))
