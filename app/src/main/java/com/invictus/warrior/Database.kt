package com.invictus.warrior

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workout_sets")
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val day: String,
    val exercise: String,
    val setNumber: Int,
    val weightLb: Double,
    val reps: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pounds: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "recovery_entries")
data class RecoveryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sleepHours: Double,
    val fatigue: Int,
    val steps: Int,
    val pain: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_steps")
data class DailyStepRecord(
    @PrimaryKey val date: String,          // ISO "2026-10-26"
    val steps: Int,
    val source: String,                     // "Health Connect" | "Foreground sensor"
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: String,                        // Training plan day name
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val status: String = "active",          // active | paused | finished
    val notes: String = ""
)

@Dao
interface TrainingDao {
    // ── Workout sets ──
    @Query("SELECT * FROM workout_sets ORDER BY timestamp DESC, id DESC")
    fun allSets(): Flow<List<WorkoutSet>>

    @Insert suspend fun addSet(entry: WorkoutSet)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun setsForSession(sessionId: Long): List<WorkoutSet>

    // ── Weight entries ──
    @Query("SELECT * FROM weight_entries ORDER BY timestamp DESC")
    fun weights(): Flow<List<WeightEntry>>

    @Insert suspend fun addWeight(entry: WeightEntry)

    // ── Recovery entries ──
    @Query("SELECT * FROM recovery_entries ORDER BY timestamp DESC")
    fun recoveries(): Flow<List<RecoveryEntry>>

    @Insert suspend fun addRecovery(entry: RecoveryEntry)

    // ── Daily steps ──
    @Upsert suspend fun upsertSteps(record: DailyStepRecord)

    @Query("SELECT * FROM daily_steps WHERE date >= :since ORDER BY date ASC")
    suspend fun stepsSince(since: String): List<DailyStepRecord>

    @Query("SELECT * FROM daily_steps WHERE date = :date LIMIT 1")
    suspend fun stepsForDate(date: String): DailyStepRecord?

    // ── Workout sessions ──
    @Insert suspend fun insertSession(session: WorkoutSession): Long

    @Query("UPDATE workout_sessions SET finishedAt = :finishedAt, status = 'finished' WHERE id = :id")
    suspend fun finishSession(id: Long, finishedAt: Long = System.currentTimeMillis())

    @Query("UPDATE workout_sessions SET status = :status WHERE id = :id")
    suspend fun updateSessionStatus(id: Long, status: String)

    @Query("SELECT * FROM workout_sessions WHERE status IN ('active', 'paused') ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeSession(): WorkoutSession?

    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    fun allSessions(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC LIMIT 10")
    suspend fun recentSessions(): List<WorkoutSession>
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS daily_steps (
                date TEXT NOT NULL PRIMARY KEY,
                steps INTEGER NOT NULL,
                source TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS workout_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                day TEXT NOT NULL,
                startedAt INTEGER NOT NULL,
                finishedAt INTEGER,
                status TEXT NOT NULL,
                notes TEXT NOT NULL
            )
        """.trimIndent())
    }
}

@Database(
    entities = [WorkoutSet::class, WeightEntry::class, RecoveryEntry::class,
                DailyStepRecord::class, WorkoutSession::class],
    version = 2,
    exportSchema = false
)
abstract class InvictusDatabase : RoomDatabase() {
    abstract fun dao(): TrainingDao
    companion object {
        @Volatile private var instance: InvictusDatabase? = null
        fun get(context: Context): InvictusDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, InvictusDatabase::class.java, "invictus.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
