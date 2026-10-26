package com.invictus.warrior

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// Raw step counter is cumulative since device boot. This small local collector
// counts only observations acquired while Invictus is in the foreground.
// It does not claim to track complete daily steps in the background.
private class ForegroundStepTracker(context: Context, onUpdate: (Int) -> Unit) : SensorEventListener {
    private val prefs = context.getSharedPreferences("invictus_steps", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val emit = onUpdate
    private var awaitingBaseline = true
    val supported: Boolean get() = sensor != null

    fun current(): Int = prefs.getInt("steps_${LocalDate.now()}", 0)
    fun start() { awaitingBaseline = true; sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) } }
    fun stop() { manager.unregisterListener(this) }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onSensorChanged(event: SensorEvent) {
        val raw = event.values.firstOrNull()?.toLong() ?: return
        val day = LocalDate.now().toString()
        val oldDay = prefs.getString("last_day", null)
        val oldRaw = prefs.getLong("last_raw", -1L)
        val oldSteps = prefs.getInt("steps_$day", 0)
        val increment = if (!awaitingBaseline && oldDay == day && oldRaw >= 0 && raw >= oldRaw) {
            (raw - oldRaw).coerceAtMost(100000L).toInt()
        } else 0
        val updated = (oldSteps + increment).coerceAtMost(100000)
        prefs.edit().putString("last_day", day).putLong("last_raw", raw)
            .putInt("steps_$day", updated).apply()
        awaitingBaseline = false
        emit(updated)
    }
}

internal object ActivityCalories {
    /** Conservative, net-above-rest approximation for LEVEL walking only.
     * Distance inferred from height * 0.415m/step; 0.45 kcal/kg/km is adjustable.
     * Not valid for estimating full daily TDEE, hills, loads, or lifting.
     */
    fun estimate(steps: Int, heightCm: Double, weightLb: Double, factor: Double): Int {
        val distanceKm = steps * (heightCm / 100.0) * 0.415 / 1000.0
        val weightKg = weightLb * 0.45359237
        return (distanceKm * weightKg * 0.45 * factor).roundToInt().coerceAtLeast(0)
    }

    fun distanceKm(steps: Int, heightCm: Double): Double =
        steps * (heightCm / 100.0) * 0.415 / 1000.0

    fun restingCalories(weightLb: Double, heightCm: Double, ageDays: Int = 9490): Int {
        // Mifflin-St Jeor for males (approximate, user can calibrate)
        val weightKg = weightLb * 0.45359237
        val ageYears = ageDays / 365
        return (10 * weightKg + 6.25 * heightCm - 5 * ageYears + 5).roundToInt()
    }
}

@Composable
internal fun ActivityTrackingScreen(weightLb: Double, dao: TrainingDao) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("invictus_activity_settings", Context.MODE_PRIVATE) }
    var factor by remember { mutableFloatStateOf(prefs.getFloat("walking_factor", 0.8f).coerceIn(0.6f, 1.2f)) }
    var steps by remember { mutableIntStateOf(0) }
    var source by remember { mutableStateOf("Not connected") }
    var error by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    var stepHistory by remember { mutableStateOf<List<DailyStepRecord>>(emptyList()) }
    var granted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 29 || context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
    }
    val sensorTracker = remember(context) { ForegroundStepTracker(context) { reading ->
        if (source != "Health Connect") steps = reading
    } }
    val healthAvailable = remember(context) {
        runCatching { HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE }
            .getOrDefault(false)
    }
    val client = remember(context, healthAvailable) {
        if (healthAvailable) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null
    }
    val readStepsPermission = remember { HealthPermission.getReadPermission(StepsRecord::class) }
    val healthLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { permissions ->
        if (readStepsPermission in permissions) {
            scope.launch {
                refreshHealthSteps(client, readStepsPermission, dao,
                    onSteps = { steps = it; source = "Health Connect" },
                    onError = { error = it })
                loadHistory(dao) { stepHistory = it }
            }
        } else {
            error = "Step access not granted. Enable Health Connect permission to read all-day totals."
        }
    }
    val sensorPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    fun refresh() {
        scope.launch {
            refreshing = true
            if (client != null) {
                refreshHealthSteps(client, readStepsPermission, dao,
                    onSteps = { steps = it; source = "Health Connect"; error = "" },
                    onError = { error = it })
            } else {
                // Persist sensor steps
                val today = LocalDate.now().toString()
                val sensorSteps = sensorTracker.current()
                dao.upsertSteps(DailyStepRecord(today, sensorSteps, "Foreground sensor"))
            }
            loadHistory(dao) { stepHistory = it }
            refreshing = false
        }
    }

    DisposableEffect(sensorTracker, granted, lifecycle, client) {
        if (client == null) {
            steps = sensorTracker.current()
            source = "Foreground sensor (partial)"
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (client != null) refresh() else if (granted) sensorTracker.start()
                scope.launch { loadHistory(dao) { stepHistory = it } }
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                sensorTracker.stop()
                // Persist current sensor count on pause
                scope.launch {
                    val today = LocalDate.now().toString()
                    val current = sensorTracker.current()
                    if (current > 0) {
                        dao.upsertSteps(DailyStepRecord(today, current, source))
                    }
                }
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if (client != null) refresh() else if (granted) sensorTracker.start()
            scope.launch { loadHistory(dao) { stepHistory = it } }
        }
        onDispose { sensorTracker.stop(); lifecycle.lifecycle.removeObserver(observer) }
    }

    val active = ActivityCalories.estimate(steps, 183.0, weightLb, factor.toDouble())
    val distanceKm = ActivityCalories.distanceKm(steps, 183.0)
    val distanceMi = distanceKm * 0.621371
    val resting = ActivityCalories.restingCalories(weightLb, 183.0)
    val totalEstimate = resting + active
    val lowRange = max(0, (active * 0.7).roundToInt())
    val highRange = (active * 1.3).roundToInt()

    Column(verticalArrangement = Arrangement.spacedBy(Inv.CardGap), modifier = Modifier.fillMaxWidth()) {
        ScreenTitle("Movement", "Daily activity and step tracking")

        // ── Steps card ──
        InvCard(goldBorder = true) {
            CardLabel("Steps · Today")
            BigValue(String.format(Locale.US, "%,d", steps))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Source: $source", color = Inv.TextTertiary, fontSize = 12.sp)
                Text(
                    String.format(Locale.US, "%.1f mi", distanceMi),
                    color = Inv.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (client != null) {
                Text(
                    "Health Connect collects on-device steps while Invictus is closed.",
                    color = Inv.TextTertiary, fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InvButton(text = "Connect", onClick = {
                        scope.launch {
                            val isGranted = runCatching {
                                client.permissionController.getGrantedPermissions().contains(readStepsPermission)
                            }.getOrDefault(false)
                            if (isGranted) {
                                refresh()
                            } else {
                                try {
                                    healthLauncher.launch(setOf(readStepsPermission))
                                } catch (e: Exception) {
                                    // Fallback: open Health Connect app directly
                                    error = "Could not open permission dialog. Opening Health Connect settings…"
                                    try {
                                        val intent = Intent("androidx.health.ACTION_MANAGE_HEALTH_PERMISSIONS")
                                            .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                                Uri.parse("market://details?id=com.google.android.apps.healthdata")))
                                        } catch (_: Exception) {
                                            error = "Install or open Health Connect from the Play Store, then grant Invictus step access."
                                        }
                                    }
                                }
                            }
                        }
                    })
                    InvOutlinedButton(
                        text = if (refreshing) "Loading…" else "Refresh",
                        onClick = { refresh() },
                        enabled = !refreshing
                    )
                }
            } else {
                Text(
                    "Health Connect unavailable. Fallback measures steps only while Invictus is open.",
                    color = Inv.TextTertiary, fontSize = 12.sp
                )
                if (!sensorTracker.supported) {
                    Text("No step-counter sensor on this device.", color = Inv.Error, fontSize = 12.sp)
                } else if (!granted) {
                    InvButton(text = "Allow step sensor", onClick = {
                        sensorPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    })
                }
            }
            if (error.isNotEmpty()) {
                Text(error, color = Inv.Error, fontSize = 12.sp)
            }
        }

        // ── 7-Day Step History ──
        if (stepHistory.isNotEmpty()) {
            InvCard {
                CardLabel("7-Day Steps")
                StepHistoryChart(stepHistory)
            }
        }

        // ── Calorie estimates ──
        InvCard {
            CardLabel("Estimated Calories")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Resting", color = Inv.TextTertiary, fontSize = 12.sp)
                    Text("~$resting", color = Inv.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Active (walking)", color = Inv.TextTertiary, fontSize = 12.sp)
                    Text("~$active", color = Inv.Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("$lowRange–$highRange", color = Inv.TextTertiary, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total", color = Inv.TextTertiary, fontSize = 12.sp)
                    Text("~$totalEstimate", color = Inv.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                "Flat-ground walking only. Do not double-count with workout totals.",
                color = Inv.TextTertiary, fontSize = 11.sp
            )
        }

        // ── Calibration ──
        InvCard {
            CardLabel("Personal Calibration")
            Text(
                "Factor: ${(factor * 100).roundToInt()}%",
                color = Inv.TextSecondary, fontSize = 14.sp
            )
            Slider(
                value = factor,
                onValueChange = {
                    factor = it
                    prefs.edit().putFloat("walking_factor", it).apply()
                },
                valueRange = 0.6f..1.2f,
                steps = 11,
                colors = SliderDefaults.colors(
                    thumbColor = Inv.Gold,
                    activeTrackColor = Inv.Gold,
                    inactiveTrackColor = Inv.Border
                )
            )
            Text(
                "80% is a conservative starting point. Adjust based on actual measurements over time.",
                color = Inv.TextTertiary, fontSize = 11.sp
            )
        }

        Text(
            "Step counts may be batched, delayed, or incomplete. This is not a medical-grade monitor.",
            color = Inv.TextTertiary, fontSize = 11.sp
        )
    }
}

@Composable
private fun StepHistoryChart(history: List<DailyStepRecord>) {
    val maxSteps = (history.maxOfOrNull { it.steps } ?: 1).coerceAtLeast(1000)
    val gold = Inv.Gold
    val border = Inv.Border
    val textColor = Inv.TextTertiary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        val barCount = history.size
        if (barCount == 0) return@Canvas
        val spacing = 8.dp.toPx()
        val labelHeight = 24.dp.toPx()
        val chartHeight = size.height - labelHeight
        val barWidth = (size.width - spacing * (barCount + 1)) / barCount

        history.forEachIndexed { index, record ->
            val x = spacing + index * (barWidth + spacing)
            val fraction = (record.steps.toFloat() / maxSteps).coerceIn(0f, 1f)
            val barHeight = (chartHeight * fraction).coerceAtLeast(2.dp.toPx())

            // Bar
            drawRoundRect(
                color = gold.copy(alpha = if (index == history.lastIndex) 1f else 0.6f),
                topLeft = Offset(x, chartHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )

            // Day label
            val date = try { LocalDate.parse(record.date) } catch (_: Exception) { null }
            val label = date?.dayOfWeek?.getDisplayName(TextStyle.SHORT, Locale.US)?.take(2) ?: ""
            val paint = android.graphics.Paint().apply {
                color = textColor.hashCode()
                textSize = 10.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x + barWidth / 2,
                size.height - 2.dp.toPx(),
                paint
            )

            // Step count on top of bar (only for today)
            if (index == history.lastIndex) {
                val countPaint = android.graphics.Paint().apply {
                    color = gold.hashCode()
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    String.format(Locale.US, "%,d", record.steps),
                    x + barWidth / 2,
                    chartHeight - barHeight - 4.dp.toPx(),
                    countPaint
                )
            }
        }
    }
}

private suspend fun loadHistory(dao: TrainingDao, onResult: (List<DailyStepRecord>) -> Unit) {
    val since = LocalDate.now().minusDays(6).toString()
    onResult(dao.stepsSince(since))
}

private suspend fun refreshHealthSteps(
    client: HealthConnectClient?,
    permission: String,
    dao: TrainingDao,
    onSteps: (Int) -> Unit,
    onError: (String) -> Unit
) {
    if (client == null) return
    try {
        if (permission !in client.permissionController.getGrantedPermissions()) {
            onError("Health Connect step permission needed. Tap Connect.")
            return
        }
        val today = LocalDate.now()
        val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = Instant.now()
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        val count = (response[StepsRecord.COUNT_TOTAL] ?: 0L).coerceIn(0L, 200000L).toInt()
        onSteps(count)
        // Persist to Room
        dao.upsertSteps(DailyStepRecord(today.toString(), count, "Health Connect"))
    } catch (exception: Exception) {
        onError("Couldn't read Health Connect steps: ${exception.message ?: "unknown error"}")
    }
}
