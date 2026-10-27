package com.invictus.warrior

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {

    // ── Goal definitions (seed data) ────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGoals(goals: List<GoalDefinition>)

    @Query("SELECT * FROM goal_definitions ORDER BY id ASC")
    fun allGoals(): Flow<List<GoalDefinition>>

    @Query("SELECT * FROM goal_definitions WHERE id = :id")
    suspend fun goal(id: String): GoalDefinition?

    @Query("SELECT * FROM goal_definitions WHERE domain = :domain ORDER BY id ASC")
    fun goalsByDomain(domain: String): Flow<List<GoalDefinition>>

    @Query("SELECT DISTINCT domain FROM goal_definitions ORDER BY domain ASC")
    suspend fun allDomains(): List<String>

    @Query("SELECT COUNT(*) FROM goal_definitions")
    suspend fun goalCount(): Int

    // ── Milestone definitions (seed data) ───────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMilestones(milestones: List<MilestoneDefinition>)

    @Query("SELECT * FROM milestone_definitions WHERE goalId = :goalId ORDER BY stage ASC")
    fun milestonesForGoal(goalId: String): Flow<List<MilestoneDefinition>>

    @Query("SELECT * FROM milestone_definitions WHERE goalId = :goalId ORDER BY stage ASC")
    suspend fun milestonesForGoalSync(goalId: String): List<MilestoneDefinition>

    @Query("SELECT COUNT(*) FROM milestone_definitions")
    suspend fun milestoneCount(): Int

    @Query("SELECT * FROM milestone_definitions WHERE domain = :domain ORDER BY goalId ASC, stage ASC")
    suspend fun milestonesByDomain(domain: String): List<MilestoneDefinition>

    // ── Goal progress ───────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGoalProgress(progress: List<GoalProgress>)

    @Query("SELECT * FROM goal_progress WHERE goalId = :goalId")
    suspend fun goalProgress(goalId: String): GoalProgress?

    @Query("SELECT * FROM goal_progress")
    fun allGoalProgress(): Flow<List<GoalProgress>>

    @Query("SELECT * FROM goal_progress")
    suspend fun allGoalProgressSync(): List<GoalProgress>

    @Query("""
        UPDATE goal_progress
        SET status = :status, updatedAt = :now, achievedAt = CASE WHEN :status = 'achieved' THEN :now ELSE achievedAt END
        WHERE goalId = :goalId
    """)
    suspend fun updateGoalStatus(goalId: String, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE goal_progress SET notes = :notes, updatedAt = :now WHERE goalId = :goalId")
    suspend fun updateGoalNotes(goalId: String, notes: String, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM goal_progress WHERE status = 'achieved'")
    suspend fun achievedGoalCount(): Int

    // ── Milestone progress ──────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMilestoneProgress(progress: List<MilestoneProgress>)

    @Query("SELECT * FROM milestone_progress WHERE goalId = :goalId ORDER BY milestoneId ASC")
    fun milestoneProgressForGoal(goalId: String): Flow<List<MilestoneProgress>>

    @Query("SELECT * FROM milestone_progress WHERE goalId = :goalId ORDER BY milestoneId ASC")
    suspend fun milestoneProgressForGoalSync(goalId: String): List<MilestoneProgress>

    @Query("SELECT * FROM milestone_progress")
    fun allMilestoneProgress(): Flow<List<MilestoneProgress>>

    @Query("SELECT * FROM milestone_progress")
    suspend fun allMilestoneProgressSync(): List<MilestoneProgress>

    @Query("""
        UPDATE milestone_progress
        SET status = :status, evidenceNotes = :evidence, updatedAt = :now,
            achievedAt = CASE WHEN :status = 'achieved' THEN :now ELSE achievedAt END
        WHERE milestoneId = :milestoneId
    """)
    suspend fun updateMilestoneStatus(
        milestoneId: String,
        status: String,
        evidence: String = "",
        now: Long = System.currentTimeMillis()
    )

    @Query("SELECT COUNT(*) FROM milestone_progress WHERE status = 'achieved'")
    suspend fun achievedMilestoneCount(): Int

    @Query("SELECT COUNT(*) FROM milestone_progress WHERE status = 'achieved' AND goalId IN (SELECT id FROM goal_definitions WHERE tier IN ('core', 'core-proposed'))")
    suspend fun achievedCoreMilestoneCount(): Int

    @Query("SELECT COUNT(*) FROM milestone_definitions WHERE category IN ('core', 'core-proposed')")
    suspend fun totalCoreMilestoneCount(): Int

    // ── Domain progress summary ─────────────────────────────────

    @Query("""
        SELECT gd.domain,
               COUNT(mp.milestoneId) as total,
               SUM(CASE WHEN mp.status = 'achieved' THEN 1 ELSE 0 END) as achieved
        FROM milestone_definitions md
        JOIN milestone_progress mp ON md.id = mp.milestoneId
        JOIN goal_definitions gd ON md.goalId = gd.id
        GROUP BY gd.domain
        ORDER BY gd.domain ASC
    """)
    suspend fun domainProgressSummary(): List<DomainProgressRow>

    // ── XP events ───────────────────────────────────────────────

    @Insert suspend fun insertXPEvent(event: XPEvent): Long

    @Query("SELECT COALESCE(SUM(xpAwarded), 0) FROM xp_events")
    suspend fun totalXP(): Int

    @Query("SELECT COALESCE(SUM(xpAwarded), 0) FROM xp_events")
    fun totalXPFlow(): Flow<Int>

    @Query("SELECT * FROM xp_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentXPEvents(limit: Int = 20): List<XPEvent>

    @Query("SELECT COUNT(*) FROM xp_events WHERE source = 'training' AND timestamp > :since")
    suspend fun trainingXPCountSince(since: Long): Int

    @Query("SELECT COALESCE(SUM(xpAwarded), 0) FROM xp_events WHERE source = 'training' AND timestamp > :since")
    suspend fun trainingXPSince(since: Long): Int

    // Anti-farming: check if XP already awarded for a specific source
    @Query("SELECT COUNT(*) FROM xp_events WHERE sourceId = :sourceId AND source = :source")
    suspend fun xpAlreadyAwarded(sourceId: String, source: String): Int

    // ── Rank grants ─────────────────────────────────────────────

    @Insert suspend fun insertRankGrant(grant: RankGrant): Long

    @Query("SELECT * FROM rank_grants ORDER BY rankLevel DESC LIMIT 1")
    suspend fun currentRank(): RankGrant?

    @Query("SELECT * FROM rank_grants ORDER BY rankLevel DESC LIMIT 1")
    fun currentRankFlow(): Flow<RankGrant?>

    @Query("SELECT * FROM rank_grants ORDER BY grantedAt ASC")
    fun rankHistory(): Flow<List<RankGrant>>

    // ── Registry versions ───────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRegistryVersion(version: RegistryVersion)

    @Query("SELECT * FROM registry_versions ORDER BY importedAt DESC")
    suspend fun registryVersions(): List<RegistryVersion>

    @Query("SELECT COUNT(*) FROM registry_versions WHERE version = :version")
    suspend fun hasRegistryVersion(version: String): Int
}

/** Row type for domain progress summary query. */
data class DomainProgressRow(
    val domain: String,
    val total: Int,
    val achieved: Int
)
