package com.invictus.warrior

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.Instant
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDate
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
        // First reading, new calendar day, or reboot establishes a new baseline.
        // Never attribute unobserved periods to this day.
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
}

@Composable
internal fun ActivityTrackingScreen(weightLb: Double) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("invictus_activity_settings", Context.MODE_PRIVATE) }
    var factor by remember { mutableFloatStateOf(prefs.getFloat("walking_factor", 0.8f).coerceIn(0.6f, 1.2f)) }
    var steps by remember { mutableIntStateOf(0) }
    var source by remember { mutableStateOf("Not connected") }
    var error by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    var granted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 29 || context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
    }
    val sensorTracker = remember(context) { ForegroundStepTracker(context) { reading ->
        // The sensor fallback is only displayed if Health Connect isn't connected.
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
            scope.launch { refreshHealthSteps(client, readStepsPermission, onSteps = { steps = it; source = "Health Connect" }, onError = { error = it }) }
        } else {
            error = "Step access not granted. Enable Health Connect permission to read all-day totals."
        }
    }
    val sensorPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    fun refresh() {
        if (client == null) return
        scope.launch {
            refreshing = true
            refreshHealthSteps(client, readStepsPermission,
                onSteps = { steps = it; source = "Health Connect"; error = "" },
                onError = { error = it })
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
            }
            if (event == Lifecycle.Event.ON_PAUSE) sensorTracker.stop()
        }
        lifecycle.lifecycle.addObserver(observer)
        if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if (client != null) refresh() else if (granted) sensorTracker.start()
        }
        onDispose { sensorTracker.stop(); lifecycle.lifecycle.removeObserver(observer) }
    }

    val active = ActivityCalories.estimate(steps, 183.0, weightLb, factor.toDouble())
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Movement", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("STEPS · TODAY", color = MaterialTheme.colorScheme.primary)
                Text(String.format(Locale.US, "%,d", steps), style = MaterialTheme.typography.headlineLarge)
                Text("Source: $source")
                if (client != null) {
                    Text("Health Connect collects available on-device steps while Invictus is closed. Open this screen to refresh the daily aggregate.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                val isGranted = runCatching { client.permissionController.getGrantedPermissions().contains(readStepsPermission) }.getOrDefault(false)
                                if (isGranted) refresh() else healthLauncher.launch(setOf(readStepsPermission))
                            }
                        }) { Text("Connect steps") }
                        OutlinedButton(onClick = { refresh() }, enabled = !refreshing) { Text(if (refreshing) "Loading" else "Refresh") }
                    }
                } else {
                    Text("Health Connect unavailable: this fallback measures steps only while Invictus is open. All-day tracking requires Health Connect on a supported device.")
                    if (!sensorTracker.supported) Text("This phone has no available step-counter sensor.")
                    else if (!granted) Button(onClick = { sensorPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) { Text("Allow step sensor") }
                }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ESTIMATED WALKING CALORIES · ABOVE REST", color = MaterialTheme.colorScheme.primary)
                Text("~$active kcal", style = MaterialTheme.typography.headlineMedium)
                Text("Indicative range: ${max(0, (active * 0.7).roundToInt())}–${(active * 1.3).roundToInt()} kcal")
                Text("Calories are modeled from steps, not measured. Flat-ground walking only. Do not double-count them with other workout totals.")
                Text("Personal calibration: ${(factor * 100).roundToInt()}%")
                Slider(value = factor, onValueChange = {
                    factor = it
                    prefs.edit().putFloat("walking_factor", it).apply()
                }, valueRange = 0.6f..1.2f, steps = 11)
                Text("80% is a conservative starting setting, not a universal correction. Calorie estimates require long-term calibration.")
            }
        }
        Text("Health Connect step counts may be batched, missing or delayed. Activity tracked by devices you aren't carrying cannot be inferred. This app is not a medical or laboratory-grade calorie monitor.", style = MaterialTheme.typography.bodySmall)
    }
}

private suspend fun refreshHealthSteps(
    client: HealthConnectClient?,
    permission: String,
    onSteps: (Int) -> Unit,
    onError: (String) -> Unit
) {
    if (client == null) return
    try {
        if (permission !in client.permissionController.getGrantedPermissions()) {
            onError("Health Connect step permission needed. Tap Connect steps.")
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
        onSteps((response[StepsRecord.COUNT_TOTAL] ?: 0L).coerceIn(0L, 200000L).toInt())
    } catch (exception: Exception) {
        onError("Couldn't read Health Connect steps: ${exception.message ?: "unknown error"}")
    }
}
