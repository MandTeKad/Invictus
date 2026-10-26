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

@Dao
interface TrainingDao {
    @Query("SELECT * FROM workout_sets ORDER BY timestamp DESC, id DESC")
    fun allSets(): Flow<List<WorkoutSet>>
    @Insert suspend fun addSet(entry: WorkoutSet)
    @Query("DELETE FROM workout_sets WHERE id = :id") suspend fun deleteSet(id: Long)
    @Query("SELECT * FROM weight_entries ORDER BY timestamp DESC")
    fun weights(): Flow<List<WeightEntry>>
    @Insert suspend fun addWeight(entry: WeightEntry)
    @Query("SELECT * FROM recovery_entries ORDER BY timestamp DESC")
    fun recoveries(): Flow<List<RecoveryEntry>>
    @Insert suspend fun addRecovery(entry: RecoveryEntry)
}

@Database(
    entities = [WorkoutSet::class, WeightEntry::class, RecoveryEntry::class],
    version = 1,
    exportSchema = false
)
abstract class InvictusDatabase : RoomDatabase() {
    abstract fun dao(): TrainingDao
    companion object {
        @Volatile private var instance: InvictusDatabase? = null
        fun get(context: Context): InvictusDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, InvictusDatabase::class.java, "invictus.db"
            ).build().also { instance = it }
        }
    }
}
