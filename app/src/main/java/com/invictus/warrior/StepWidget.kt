package com.invictus.warrior

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Invictus Step Counter Widget — full dashboard widget for the home screen.
 * Renders a circular progress ring, weekly bar chart, distance and calorie stats
 * as a bitmap (Glance doesn't support Canvas composables).
 *
 * Data source: Room daily_steps table.
 * Updates: system interval (~30 min) + explicit refresh on step changes.
 */
class StepWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        val bitmap = renderWidgetBitmap(data)
        provideContent {
            StepWidgetContent(bitmap)
        }
    }

    companion object {
        private const val STEP_GOAL = 10000
        private const val HEIGHT_CM = 183.0  // 6 ft

        data class WidgetData(
            val todaySteps: Int,
            val goal: Int = STEP_GOAL,
            val weekHistory: List<Pair<String, Int>>,  // dayLabel to steps
            val distanceMi: Double,
            val calories: Int
        )

        suspend fun loadWidgetData(context: Context): WidgetData {
            val today = LocalDate.now()
            val todaySteps: Int
            val weekHistory = mutableListOf<Pair<String, Int>>()

            try {
                val db = InvictusDatabase.get(context)
                val dao = db.dao()
                val record = dao.stepsForDate(today.toString())
                todaySteps = record?.steps ?: 0

                // Load last 7 days
                val since = today.minusDays(6).toString()
                val records = dao.stepsSince(since)
                val recordMap = records.associate { it.date to it.steps }

                for (i in 6 downTo 0) {
                    val date = today.minusDays(i.toLong())
                    val dayLabel = date.dayOfWeek
                        .getDisplayName(TextStyle.SHORT, Locale.US)
                        .take(1)
                    val steps = recordMap[date.toString()] ?: 0
                    weekHistory.add(dayLabel to steps)
                }
            } catch (_: Exception) {
                // Fallback
                val prefs = context.getSharedPreferences("invictus_steps", Context.MODE_PRIVATE)
                val fallback = prefs.getInt("steps_${today}", 0)
                return WidgetData(
                    todaySteps = fallback,
                    weekHistory = listOf("M" to 0, "T" to 0, "W" to 0, "T" to 0, "F" to 0, "S" to 0, "S" to fallback),
                    distanceMi = ActivityCalories.distanceKm(fallback, HEIGHT_CM) * 0.621371,
                    calories = ActivityCalories.estimate(fallback, HEIGHT_CM, 287.0, 0.8)
                )
            }

            return WidgetData(
                todaySteps = todaySteps,
                weekHistory = weekHistory,
                distanceMi = ActivityCalories.distanceKm(todaySteps, HEIGHT_CM) * 0.621371,
                calories = ActivityCalories.estimate(todaySteps, HEIGHT_CM, 287.0, 0.8)
            )
        }

        fun renderWidgetBitmap(data: WidgetData): Bitmap {
            val w = 720
            val h = 340
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val gold = 0xFFD4AF37.toInt()
            val goldDim = 0xFF8B7328.toInt()
            val bg = 0xFF0D0D0D.toInt()

            val textPrimary = 0xFFEEEEEE.toInt()
            val textSecondary = 0xFF999999.toInt()
            val ringTrack = 0xFF2A2A2A.toInt()

            // Background
            canvas.drawColor(bg)

            // ── Left: circular progress ring ──
            val ringCx = 145f
            val ringCy = h / 2f
            val ringRadius = 115f
            val ringStroke = 18f

            // Track
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ringTrack; style = Paint.Style.STROKE; strokeWidth = ringStroke; strokeCap = Paint.Cap.ROUND
            }
            canvas.drawCircle(ringCx, ringCy, ringRadius, trackPaint)

            // Progress arc
            val progress = (data.todaySteps.toFloat() / data.goal).coerceIn(0f, 1f)
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = gold; style = Paint.Style.STROKE; strokeWidth = ringStroke; strokeCap = Paint.Cap.ROUND
            }
            val arcRect = RectF(
                ringCx - ringRadius, ringCy - ringRadius,
                ringCx + ringRadius, ringCy + ringRadius
            )
            canvas.drawArc(arcRect, -90f, 360f * progress, false, arcPaint)

            // Step count text inside ring
            val stepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textPrimary; textSize = 48f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                String.format(Locale.US, "%,d", data.todaySteps),
                ringCx, ringCy + 8f, stepPaint
            )

            // "steps" label
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textSecondary; textSize = 22f; textAlign = Paint.Align.CENTER
            }
            canvas.drawText("steps", ringCx, ringCy + 38f, labelPaint)

            // Walking icon (simple text glyph)
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = gold; textSize = 28f; textAlign = Paint.Align.CENTER
            }
            canvas.drawText("🏛", ringCx, ringCy - 36f, iconPaint)

            // ── Right side ──
            val rightX = 310f
            val rightW = w - rightX - 16f

            // ── Weekly bar chart ──
            val chartTop = 24f
            val chartBottom = 180f
            val chartHeight = chartBottom - chartTop
            val barCount = data.weekHistory.size
            val barSpacing = 10f
            val barWidth = (rightW - barSpacing * (barCount + 1)) / barCount
            val maxSteps = (data.weekHistory.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1000)

            // Day label paint
            val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textSecondary; textSize = 20f; textAlign = Paint.Align.CENTER
            }
            val todayDayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = gold; textSize = 20f; textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            // Goal line
            val goalFraction = (data.goal.toFloat() / maxSteps.coerceAtLeast(data.goal)).coerceIn(0f, 1f)
            val goalY = chartBottom - chartHeight * goalFraction
            val goalLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = goldDim; style = Paint.Style.STROKE; strokeWidth = 2f
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 6f), 0f)
            }
            canvas.drawLine(rightX, goalY, rightX + rightW, goalY, goalLinePaint)

            data.weekHistory.forEachIndexed { index, (dayLabel, steps) ->
                val x = rightX + barSpacing + index * (barWidth + barSpacing)
                val fraction = (steps.toFloat() / maxSteps.coerceAtLeast(data.goal)).coerceIn(0f, 1f)
                val barH = (chartHeight * fraction).coerceAtLeast(6f)
                val isToday = index == barCount - 1

                // Bar
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isToday) gold else if (steps > 0) goldDim else ringTrack
                }
                val barRect = RectF(x, chartBottom - barH, x + barWidth, chartBottom)
                canvas.drawRoundRect(barRect, 6f, 6f, barPaint)

                // Day label
                canvas.drawText(
                    dayLabel,
                    x + barWidth / 2,
                    chartBottom + 24f,
                    if (isToday) todayDayPaint else dayPaint
                )
            }

            // ── Stats row ──
            val statsY = 250f
            val statLabelY = statsY + 28f
            val colWidth = rightW / 3f

            // Distance
            val statValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textPrimary; textSize = 34f; textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val statLabelPaintSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textSecondary; textSize = 18f; textAlign = Paint.Align.CENTER
            }
            val statIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = gold; textSize = 18f; textAlign = Paint.Align.CENTER
            }

            // Stat 1: Distance
            val stat1X = rightX + colWidth * 0.5f
            canvas.drawText("📍", stat1X, statsY - 24f, statIconPaint)
            canvas.drawText(
                String.format(Locale.US, "%.1f", data.distanceMi),
                stat1X, statsY + 4f, statValuePaint
            )
            canvas.drawText("mile", stat1X, statLabelY + 4f, statLabelPaintSmall)

            // Stat 2: Calories
            val stat2X = rightX + colWidth * 1.5f
            canvas.drawText("🔥", stat2X, statsY - 24f, statIconPaint)
            canvas.drawText(
                "${data.calories}",
                stat2X, statsY + 4f, statValuePaint
            )
            canvas.drawText("Kcal", stat2X, statLabelY + 4f, statLabelPaintSmall)

            // Stat 3: Goal %
            val stat3X = rightX + colWidth * 2.5f
            val pct = ((data.todaySteps.toFloat() / data.goal) * 100).roundToInt()
            canvas.drawText("⚔", stat3X, statsY - 24f, statIconPaint)
            canvas.drawText(
                "$pct%",
                stat3X, statsY + 4f, statValuePaint
            )
            canvas.drawText("goal", stat3X, statLabelY + 4f, statLabelPaintSmall)

            // Thin separator line above stats
            val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ringTrack; strokeWidth = 2f
            }
            canvas.drawLine(rightX + 10f, statsY - 42f, rightX + rightW - 10f, statsY - 42f, sepPaint)

            return bitmap
        }

        /** Call from anywhere to force-refresh all widget instances. */
        suspend fun refreshAll(context: Context) {
            StepWidget().updateAll(context)
        }
    }
}

@Composable
private fun StepWidgetContent(bitmap: Bitmap) {
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = "Invictus step tracker",
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(0.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentScale = ContentScale.FillBounds
    )
}

/**
 * Receiver that tells the system which GlanceAppWidget to instantiate.
 * Starts the background step-sync worker when the first widget is placed
 * and cancels it when the last widget is removed.
 */
class StepWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StepWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        StepSyncWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        StepSyncWorker.cancel(context)
    }
}
