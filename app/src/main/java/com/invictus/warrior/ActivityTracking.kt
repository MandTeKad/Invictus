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
    val prefs = remember { context.getSharedPreferences("invictus_activity_settings", Context.MODE_PRIVATE) }
    var factor by remember { mutableFloatStateOf(prefs.getFloat("walking_factor", 0.8f).coerceIn(0.6f, 1.2f)) }
    var steps by remember { mutableIntStateOf(0) }
    val tracker = remember(context) { ForegroundStepTracker(context) { steps = it } }
    var granted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 29 || context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    DisposableEffect(tracker, granted, lifecycle) {
        steps = tracker.current()
        val observer = LifecycleEventObserver { _, event ->
            if (granted && event == Lifecycle.Event.ON_RESUME) tracker.start()
            if (event == Lifecycle.Event.ON_PAUSE) tracker.stop()
        }
        lifecycle.lifecycle.addObserver(observer)
        if (granted && lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) tracker.start()
        onDispose { tracker.stop(); lifecycle.lifecycle.removeObserver(observer) }
    }

    val active = ActivityCalories.estimate(steps, 183.0, weightLb, factor.toDouble())
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Movement", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PHONE-SENSOR STEPS · TODAY", color = MaterialTheme.colorScheme.primary)
                Text(String.format(Locale.US, "%,d", steps), style = MaterialTheme.typography.headlineLarge)
                when {
                    !tracker.supported -> Text("This phone does not expose a step-counter sensor.")
                    !granted -> Button(onClick = { permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) { Text("Allow step tracking") }
                    else -> Text("Partial count: recorded while Invictus is open and active. Steps while the app is closed are not included.")
                }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ESTIMATED WALKING CALORIES · ABOVE REST", color = MaterialTheme.colorScheme.primary)
                Text("~$active kcal", style = MaterialTheme.typography.headlineMedium)
                Text("Indicative range: ${max(0,(active*0.7).roundToInt())}–${(active*1.3).roundToInt()} kcal")
                Text("These are estimates, not measured calories. Flat-ground walking only. Never add this figure to another app's activity total without checking for overlap.")
                Text("Personal calibration: ${(factor * 100).roundToInt()}%")
                Slider(value = factor, onValueChange = {
                    factor = it
                    prefs.edit().putFloat("walking_factor", it).apply()
                }, valueRange = 0.6f..1.2f, steps = 11)
                Text("Starts conservatively at 80%. This is NOT a proven universal 20% correction. Later versions will calibrate against food intake and multiweek weight trends, with uncertainty and manual review.")
            }
        }
        Text("This screen is an initial foreground sensor implementation. Planned: Health Connect daily aggregates for more complete steps, duplicate-source prevention, and calibrated total daily energy expenditure. No medical or calorie accuracy guarantee.", style = MaterialTheme.typography.bodySmall)
    }
}
