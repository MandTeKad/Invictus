package com.invictus.warrior

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically reads today's steps from Health Connect,
 * persists to Room, and refreshes the home screen widget.
 *
 * Runs every 15 minutes (WorkManager minimum) so the widget stays current
 * even when the app is not in the foreground.
 */
class StepSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            syncSteps(applicationContext)
            Result.success()
        } catch (_: Exception) {
            // Still refresh widget with whatever Room already has
            try { StepWidget.refreshAll(applicationContext) } catch (_: Exception) {}
            Result.success()
        }
    }

    private suspend fun syncSteps(context: Context) {
        // Check Health Connect availability
        val sdkStatus = runCatching {
            HealthConnectClient.getSdkStatus(context)
        }.getOrNull()

        if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            // No Health Connect — just refresh widget with existing Room data
            StepWidget.refreshAll(context)
            return
        }

        val client = runCatching {
            HealthConnectClient.getOrCreate(context)
        }.getOrNull()

        if (client == null) {
            StepWidget.refreshAll(context)
            return
        }

        // Check if we have permission (granted previously in-app)
        val readPerm = HealthPermission.getReadPermission(StepsRecord::class)
        val granted = runCatching {
            client.permissionController.getGrantedPermissions()
        }.getOrNull() ?: emptySet()

        if (readPerm !in granted) {
            // No permission — refresh widget with whatever Room has
            StepWidget.refreshAll(context)
            return
        }

        // Read today's steps from Health Connect
        val today = LocalDate.now()
        val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = Instant.now()

        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        val hcCount = (response[StepsRecord.COUNT_TOTAL] ?: 0L).coerceIn(0L, 200000L).toInt()

        // Keep the higher of Health Connect vs what's already in Room (manual pedometer entry)
        val dao = InvictusDatabase.get(context).dao()
        val existing = dao.stepsForDate(today.toString())?.steps ?: 0
        val count = maxOf(hcCount, existing)
        val src = if (hcCount >= existing) "Health Connect" else (dao.stepsForDate(today.toString())?.source ?: "Health Connect")
        dao.upsertSteps(
            DailyStepRecord(today.toString(), count, src)
        )

        // Refresh the widget
        StepWidget.refreshAll(context)
    }

    companion object {
        private const val WORK_NAME = "invictus_step_sync"

        /** Schedule the periodic sync. Call once at app start or widget enable. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StepSyncWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Cancel the periodic sync (e.g. if all widgets removed). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
