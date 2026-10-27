package com.invictus.warrior

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.LocalDate
import java.util.Locale

/**
 * Invictus Step Counter Widget — home screen widget showing today's steps.
 * Uses Room daily_steps table as the source of truth and updates via the
 * system widget update interval (~30 min) plus explicit updates when the
 * app writes new step data.
 */
class StepWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val steps = readTodaySteps(context)
        provideContent {
            StepWidgetContent(steps)
        }
    }

    companion object {
        /** Read today's step count from Room via a direct query (widget runs off-main). */
        suspend fun readTodaySteps(context: Context): Int {
            return try {
                val db = InvictusDatabase.get(context)
                val record = db.dao().stepsForDate(LocalDate.now().toString())
                record?.steps ?: 0
            } catch (_: Exception) {
                // Fallback: read from SharedPreferences (foreground sensor cache)
                val prefs = context.getSharedPreferences("invictus_steps", Context.MODE_PRIVATE)
                prefs.getInt("steps_${LocalDate.now()}", 0)
            }
        }

        /** Call from anywhere to force-refresh all widget instances. */
        suspend fun refreshAll(context: Context) {
            StepWidget().updateAll(context)
        }
    }
}

@Composable
private fun StepWidgetContent(steps: Int) {
    val goldColor = ColorProvider(Color(0xFFD4AF37))
    val bgColor = ColorProvider(Color(0xFF0D0D0D))
    val secondaryText = ColorProvider(Color(0xFF999999))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "⚔",
                style = TextStyle(fontSize = 22.sp, color = goldColor)
            )
            Spacer(modifier = GlanceModifier.width(10.dp))
            Column {
                Text(
                    text = String.format(Locale.US, "%,d", steps),
                    style = TextStyle(
                        color = goldColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "STEPS TODAY",
                    style = TextStyle(
                        color = secondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                )
            }
        }
    }
}

/**
 * Receiver that tells the system which GlanceAppWidget to instantiate.
 * Registered in AndroidManifest.xml.
 */
class StepWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StepWidget()
}
