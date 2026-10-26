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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val background = Color(0xFF130708)
private val panel = Color(0xFF261214)
private val panelSoft = Color(0xFF34181B)
private val gold = Color(0xFFD7B875)
private val pale = Color(0xFFF4EEE8)
private val muted = Color(0xFFD2C2B4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = InvictusDatabase.get(this).dao()
        setContent { InvictusApp(dao) }
    }
}

@Composable
private fun InvictusApp(dao: TrainingDao) {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1500)
        showSplash = false
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = gold,
            background = background,
            surface = panel,
            onBackground = pale,
            onSurface = pale,
            secondary = gold
        )
    ) {
        Crossfade(
            targetState = showSplash,
            animationSpec = tween(durationMillis = 700),
            label = "invictus_startup_fade"
        ) { splash ->
            if (splash) {
                InvictusSplashScreen()
            } else {
                InvictusMainScaffold(dao)
            }
        }
    }
}

@Composable
private fun InvictusSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF4A1014)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            LaurelWreathLogo(
                modifier = Modifier.size(180.dp),
                color = gold
            )
            Text(
                "INVICTUS",
                color = gold,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Text(
                "BY WILL AND WRATH",
                color = pale,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp
            )
        }
    }
}

@Composable
private fun LaurelWreathLogo(
    modifier: Modifier = Modifier,
    color: Color = gold
) {
    Canvas(modifier = modifier) {
        val minDim = min(size.width, size.height)
        val strokeWidth = minDim * 0.025f
        val arcSize = Size(minDim * 0.72f, minDim * 0.72f)
        val topLeft = Offset(
            (size.width - arcSize.width) / 2f,
            (size.height - arcSize.height) / 2f
        )

        drawArc(
            color = color,
            startAngle = 132f,
            sweepAngle = 108f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = 300f,
            sweepAngle = 108f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minDim * 0.31f
        val leafWidth = minDim * 0.11f
        val leafHeight = minDim * 0.045f

        fun drawLeaf(angleDeg: Float, outwardTilt: Float) {
            val angleRad = angleDeg * PI.toFloat() / 180f
            val x = center.x + radius * cos(angleRad)
            val y = center.y + radius * sin(angleRad)
            rotate(degrees = angleDeg + outwardTilt, pivot = Offset(x, y)) {
                drawOval(
                    color = color,
                    topLeft = Offset(x - leafWidth / 2f, y - leafHeight / 2f),
                    size = Size(leafWidth, leafHeight)
                )
            }
        }

        val leftAngles = listOf(141f, 154f, 168f, 183f, 198f, 213f, 227f)
        val rightAngles = listOf(399f, 386f, 372f, 357f, 342f, 327f, 313f)

        leftAngles.forEach { drawLeaf(it, 42f) }
        rightAngles.forEach { drawLeaf(it, -42f) }

        // subtle lower tie between branches
        drawLine(
            color = color,
            start = Offset(center.x - minDim * 0.07f, center.y + minDim * 0.30f),
            end = Offset(center.x + minDim * 0.07f, center.y + minDim * 0.30f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun InvictusMainScaffold(dao: TrainingDao) {
    val sets by dao.allSets().collectAsStateWithLifecycle(initialValue = emptyList())
    val weights by dao.weights().collectAsStateWithLifecycle(initialValue = emptyList())
    val recoveries by dao.recoveries().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var selectedDay by remember { mutableStateOf(TrainingPlan.today()) }
    val sessionId = remember { System.currentTimeMillis() }

    Scaffold(
        containerColor = background,
        bottomBar = {
            NavigationBar(containerColor = panel) {
                listOf("Command", "Forge", "Records", "Recovery", "Movement").forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Text(listOf("⌂", "+", "▤", "♡", "↟")[index], fontSize = 21.sp) },
                        label = { Text(title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = gold,
                            selectedTextColor = gold,
                            indicatorColor = panelSoft,
                            unselectedIconColor = muted,
                            unselectedTextColor = muted
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("INVICTUS", color = gold, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text("v0.1.2", color = muted)
            }
            when (tab) {
                0 -> Dashboard(sets, weights, recoveries, onTrain = { tab = 1 })
                1 -> Forge(
                    selectedDay,
                    onDay = { selectedDay = it },
                    sets = sets,
                    save = { exercise, number, weight, reps ->
                        scope.launch {
                            dao.addSet(
                                WorkoutSet(
                                    sessionId = sessionId,
                                    day = selectedDay,
                                    exercise = exercise,
                                    setNumber = number,
                                    weightLb = weight,
                                    reps = reps
                                )
                            )
                        }
                    }
                )

                2 -> Records(
                    sets,
                    weights,
                    onAddWeight = { value -> scope.launch { dao.addWeight(WeightEntry(pounds = value)) } },
                    onDeleteSet = { id -> scope.launch { dao.deleteSet(id) } }
                )

                4 -> ActivityTrackingScreen(weights.firstOrNull()?.pounds ?: 287.0)
                3 -> Recovery(
                    recoveries,
                    onSave = { h, f, s, p ->
                        scope.launch { dao.addRecovery(RecoveryEntry(sleepHours = h, fatigue = f, steps = s, pain = p)) }
                    }
                )
            }
        }
    }
}

@Composable
private fun Header(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(description, color = muted, fontSize = 13.sp)
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = gold, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun Dashboard(sets: List<WorkoutSet>, weights: List<WeightEntry>, recoveries: List<RecoveryEntry>, onTrain: () -> Unit) {
    Header("Command", "Your training, today")
    InfoCard("CAMPAIGN 01 · ${TrainingPlan.phase().uppercase()}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${weights.firstOrNull()?.pounds ?: 287.0} lb", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("Aim: muscular & capable", color = muted, fontSize = 12.sp)
        }
        Text("Starting weight 287 lb · Outcome judged by physique and performance", fontSize = 12.sp)
    }
    val day = TrainingPlan.today()
    val planned = TrainingPlan.days.getValue(day)
    InfoCard("TODAY · $day") {
        Text(planned.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(if (planned.exercises.isEmpty()) "Work-shift activity counts. Prioritize recovery." else "${planned.exercises.size} planned movements", color = muted)
        Button(onClick = onTrain, enabled = planned.exercises.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = background)) {
            Text("Open training")
        }
    }
    InfoCard("CURRENT STATUS") {
        Text("Logged sets: ${sets.size}")
        Text("Recorded workouts: ${sets.map { it.sessionId }.distinct().size}")
        Text("Latest recovery: ${recoveries.firstOrNull()?.let { RecoveryAdvice.suggest(it.sleepHours, it.fatigue, it.steps, it.pain) } ?: "Not entered"}")
    }
    Text("No AI, internet, account, or health permissions are required in this build.", color = muted, fontSize = 12.sp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Forge(day: String, onDay: (String) -> Unit, sets: List<WorkoutSet>, save: (String, Int, Double, Int) -> Unit) {
    Header("Forge", "Log a real workout; nothing is auto-completed")
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = day,
            onValueChange = {},
            readOnly = true,
            label = { Text("Training day") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TrainingPlan.days.keys.forEach { key ->
                DropdownMenuItem(text = { Text(key) }, onClick = { onDay(key); expanded = false })
            }
        }
    }
    val plan = TrainingPlan.days.getValue(day)
    Text(plan.title, color = gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    if (plan.exercises.isEmpty()) Text("Recovery and occupational activity day. No formal session scheduled.")
    plan.exercises.forEach { exercise ->
        key(day, exercise.name) {
            ExerciseLogger(exercise, sets.filter { it.exercise == exercise.name }, save)
        }
    }
}

@Composable
private fun ExerciseLogger(exercise: PlannedExercise, previous: List<WorkoutSet>, save: (String, Int, Double, Int) -> Unit) {
    val last = previous.firstOrNull()
    var weight by remember(exercise.name) { mutableStateOf(last?.weightLb?.toString() ?: "") }
    var reps by remember(exercise.name) { mutableStateOf("") }
    var setNumber by remember(exercise.name) { mutableIntStateOf(1) }
    var feedback by remember(exercise.name) { mutableStateOf("") }
    InfoCard(exercise.name) {
        Text("Plan: ${exercise.sets} sets · ${exercise.repRange} reps/time", color = muted, fontSize = 13.sp)
        if (last != null) Text("Last saved: ${last.weightLb} lb × ${last.reps}", fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                weight,
                { weight = it },
                label = { Text("lb") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                reps,
                { reps = it },
                label = { Text("Reps / min") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Button(
            onClick = {
                val w = weight.toDoubleOrNull()
                val r = reps.toIntOrNull()
                if (w != null && w >= 0 && r != null && r > 0) {
                    save(exercise.name, setNumber, w, r)
                    setNumber++
                    feedback = "Set saved locally"
                    reps = ""
                } else feedback = "Enter valid weight (0 for bodyweight) and reps."
            },
            colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = background)
        ) { Text("Save set $setNumber") }
        if (feedback.isNotEmpty()) Text(feedback, fontSize = 12.sp)
    }
}

private fun displayDate(epoch: Long): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epoch))

@Composable
private fun Records(sets: List<WorkoutSet>, weights: List<WeightEntry>, onAddWeight: (Double) -> Unit, onDeleteSet: (Long) -> Unit) {
    Header("Records", "Local workout history and body weight")
    var weight by remember { mutableStateOf("") }
    InfoCard("BODY WEIGHT") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                weight,
                { weight = it },
                label = { Text("Weight (lb)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { weight.toDoubleOrNull()?.takeIf { it in 50.0..800.0 }?.let { onAddWeight(it); weight = "" } }, colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = background)) {
                Text("Add")
            }
        }
        weights.take(5).forEach { Text("${it.pounds} lb · ${displayDate(it.timestamp)}", fontSize = 12.sp) }
        if (weights.isEmpty()) Text("No entries yet.", color = muted)
    }
    InfoCard("RECENT SETS · ${sets.size} TOTAL") {
        if (sets.isEmpty()) Text("Log your first set in Forge.", color = muted)
        sets.take(30).forEach { item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${item.exercise}: ${item.weightLb} lb × ${item.reps}", fontSize = 13.sp)
                    Text("${item.day} · ${displayDate(item.timestamp)}", color = muted, fontSize = 11.sp)
                }
                TextButton(onClick = { onDeleteSet(item.id) }) { Text("Delete", color = gold) }
            }
            HorizontalDivider(color = panelSoft)
        }
    }
}

@Composable
private fun Recovery(recoveries: List<RecoveryEntry>, onSave: (Double, Int, Int, Boolean) -> Unit) {
    Header("Recovery", "Simple check-in, not medical assessment")
    var sleep by remember { mutableStateOf("7") }
    var fatigue by remember { mutableFloatStateOf(4f) }
    var steps by remember { mutableStateOf("5000") }
    var pain by remember { mutableStateOf(false) }
    val h = sleep.toDoubleOrNull() ?: 0.0
    val s = steps.toIntOrNull() ?: 0
    InfoCard("TODAY'S CHECK-IN") {
        OutlinedTextField(
            sleep,
            { sleep = it },
            label = { Text("Sleep hours") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Text("Fatigue: ${fatigue.toInt()} / 10")
        Slider(value = fatigue, onValueChange = { fatigue = it }, valueRange = 1f..10f, steps = 8)
        OutlinedTextField(
            steps,
            { steps = it },
            label = { Text("Previous-day steps") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = pain, onCheckedChange = { pain = it })
            Text("Pain or concerning symptoms")
        }
        Text(RecoveryAdvice.suggest(h, fatigue.toInt(), s, pain), color = gold)
        Button(onClick = { if (h in 0.0..24.0 && s in 0..100000) onSave(h, fatigue.toInt(), s, pain) }, colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = background)) {
            Text("Save check-in")
        }
    }
    InfoCard("RECENT CHECK-INS") {
        if (recoveries.isEmpty()) Text("No check-ins yet.", color = muted)
        recoveries.take(5).forEach { Text("${displayDate(it.timestamp)} · ${it.sleepHours} h sleep · fatigue ${it.fatigue}/10", fontSize = 12.sp) }
    }
}
