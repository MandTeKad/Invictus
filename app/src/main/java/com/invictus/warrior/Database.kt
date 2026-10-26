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
    val timestamp: Long = System.currentTimeMillis(),
    // v3 fields — nullable for backward compatibility with existing records
    val measurementType: String = "WEIGHT_REPS",   // MeasurementType.name
    val holdSeconds: Int? = null,                   // For HOLD_DURATION
    val durationMinutes: Double? = null,            // For DURATION_DISTANCE
    val distanceKm: Double? = null,                 // For DURATION_DISTANCE
    val assistanceLb: Double? = null,               // For ASSISTED_WEIGHT (negative = band assist)
    val skillLevel: String? = null,                 // For SKILL_LEVEL (progression level name)
    val perceivedEffort: Int? = null,               // RPE 1–10
    val techniqueNotes: String? = null,             // Free-text form notes
    val exerciseVariation: String? = null,          // e.g. "Wide grip", "Close stance"
    val trainingMode: String? = null                // TrainingMode.name at time of logging
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
    val notes: String = "",
    val trainingMode: String = "HYBRID",    // v3: TrainingMode.name
    val equipmentProfile: String = ""       // v3: equipment profile name
)

// ── v3 new entities ─────────────────────────────────────────────

@Entity(tableName = "equipment_profiles")
data class SavedEquipmentProfile(
    @PrimaryKey val name: String,           // "TREX Full Gym", "Home Bodyweight", etc.
    val description: String = "",
    val equipmentJson: String = "[]",       // JSON array of Equipment.name strings
    val isPreset: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "calisthenics_progress")
data class CalisthenicsProgressRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val movementFamily: String,             // e.g. "Push-up", "Pull-up"
    val exerciseName: String,               // Current exercise in progression
    val level: Int,                         // Progression level number
    val bestSets: Int = 0,
    val bestReps: Int = 0,
    val bestHoldSeconds: Int = 0,
    val assessedReady: Boolean = false,     // Met assessment criteria
    val advancedByUser: Boolean = false,    // User chose to advance (never auto)
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_preferences")
data class UserPreference(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
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

    // ── Equipment profiles ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEquipmentProfile(profile: SavedEquipmentProfile)

    @Query("SELECT * FROM equipment_profiles ORDER BY name ASC")
    fun allEquipmentProfiles(): Flow<List<SavedEquipmentProfile>>

    @Query("SELECT * FROM equipment_profiles WHERE name = :name LIMIT 1")
    suspend fun equipmentProfile(name: String): SavedEquipmentProfile?

    @Delete suspend fun deleteEquipmentProfile(profile: SavedEquipmentProfile)

    // ── Calisthenics progress ──
    @Insert suspend fun insertProgress(record: CalisthenicsProgressRecord): Long

    @Query("UPDATE calisthenics_progress SET bestSets = :sets, bestReps = :reps, bestHoldSeconds = :holdSec, assessedReady = :ready, updatedAt = :now WHERE id = :id")
    suspend fun updateProgress(id: Long, sets: Int, reps: Int, holdSec: Int, ready: Boolean, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM calisthenics_progress WHERE movementFamily = :family ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestProgress(family: String): CalisthenicsProgressRecord?

    @Query("SELECT * FROM calisthenics_progress ORDER BY updatedAt DESC")
    fun allProgress(): Flow<List<CalisthenicsProgressRecord>>

    // ── User preferences ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreference(pref: UserPreference)

    @Query("SELECT value FROM user_preferences WHERE `key` = :key LIMIT 1")
    suspend fun preference(key: String): String?

    @Query("SELECT * FROM user_preferences")
    fun allPreferences(): Flow<List<UserPreference>>
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

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        // Expand workout_sets with new measurement columns
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN measurementType TEXT NOT NULL DEFAULT 'WEIGHT_REPS'")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN holdSeconds INTEGER")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN durationMinutes REAL")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN distanceKm REAL")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN assistanceLb REAL")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN skillLevel TEXT")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN perceivedEffort INTEGER")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN techniqueNotes TEXT")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN exerciseVariation TEXT")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN trainingMode TEXT")

        // Expand workout_sessions with mode and equipment
        db.execSQL("ALTER TABLE workout_sessions ADD COLUMN trainingMode TEXT NOT NULL DEFAULT 'HYBRID'")
        db.execSQL("ALTER TABLE workout_sessions ADD COLUMN equipmentProfile TEXT NOT NULL DEFAULT ''")

        // New tables
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS equipment_profiles (
                name TEXT NOT NULL PRIMARY KEY,
                description TEXT NOT NULL DEFAULT '',
                equipmentJson TEXT NOT NULL DEFAULT '[]',
                isPreset INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS calisthenics_progress (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                movementFamily TEXT NOT NULL,
                exerciseName TEXT NOT NULL,
                level INTEGER NOT NULL,
                bestSets INTEGER NOT NULL DEFAULT 0,
                bestReps INTEGER NOT NULL DEFAULT 0,
                bestHoldSeconds INTEGER NOT NULL DEFAULT 0,
                assessedReady INTEGER NOT NULL DEFAULT 0,
                advancedByUser INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS user_preferences (
                `key` TEXT NOT NULL PRIMARY KEY,
                value TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

@Database(
    entities = [WorkoutSet::class, WeightEntry::class, RecoveryEntry::class,
                DailyStepRecord::class, WorkoutSession::class,
                SavedEquipmentProfile::class, CalisthenicsProgressRecord::class,
                UserPreference::class],
    version = 3,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
