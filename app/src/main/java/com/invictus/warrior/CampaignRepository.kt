package com.invictus.warrior

import android.content.Context
import org.json.JSONObject

/**
 * Imperator Campaign Repository — handles registry import and campaign operations.
 *
 * On first launch: reads registry.json from assets, inserts goal/milestone definitions,
 * initializes progress records with status "unassessed", and records the registry version.
 * Subsequent launches skip re-import if the version is already recorded.
 */
object CampaignRepository {

    /**
     * Import registry data from assets if not already imported.
     * Safe to call on every launch — checks registry_versions before importing.
     */
    suspend fun ensureRegistryImported(context: Context, campaignDao: CampaignDao) {
        // Check if already imported
        val json = loadRegistryJson(context) ?: return
        val version = json.optString("version", "1.0-draft")
        if (campaignDao.hasRegistryVersion(version) > 0) return

        // Parse goals
        val goalsArray = json.getJSONArray("terminal_goals")
        val goals = mutableListOf<GoalDefinition>()
        val goalProgressList = mutableListOf<GoalProgress>()

        for (i in 0 until goalsArray.length()) {
            val g = goalsArray.getJSONObject(i)
            goals.add(GoalDefinition(
                id = g.getString("id"),
                domain = g.getString("domain"),
                goal = g.getString("goal"),
                tier = g.getString("tier"),
                verification = g.optString("verification", ""),
                sourceInterview = g.optString("source_interview", ""),
                provenance = g.optString("provenance", ""),
                registryVersion = g.optString("version", version)
            ))
            goalProgressList.add(GoalProgress(
                goalId = g.getString("id"),
                status = "unassessed"
            ))
        }

        // Parse milestones
        val milestonesArray = json.getJSONArray("milestones")
        val milestones = mutableListOf<MilestoneDefinition>()
        val milestoneProgressList = mutableListOf<MilestoneProgress>()

        for (i in 0 until milestonesArray.length()) {
            val m = milestonesArray.getJSONObject(i)
            milestones.add(MilestoneDefinition(
                id = m.getString("id"),
                goalId = m.getString("goal_id"),
                domain = m.optString("domain", ""),
                goal = m.optString("goal", ""),
                stage = m.getInt("stage"),
                stageLabel = m.getString("stage_label"),
                category = m.optString("category", "core"),
                verification = m.optString("verification", ""),
                sourceInterview = m.optString("source_interview", ""),
                registryVersion = m.optString("version", version)
            ))
            milestoneProgressList.add(MilestoneProgress(
                milestoneId = m.getString("id"),
                goalId = m.getString("goal_id"),
                status = "unassessed"
            ))
        }

        // Insert in order: definitions first, then progress
        campaignDao.insertGoals(goals)
        campaignDao.insertMilestones(milestones)
        campaignDao.insertGoalProgress(goalProgressList)
        campaignDao.insertMilestoneProgress(milestoneProgressList)

        // Record the version so we don't re-import
        campaignDao.insertRegistryVersion(RegistryVersion(
            version = version,
            goalCount = goals.size,
            milestoneCount = milestones.size,
            frozen = true
        ))

        // Grant Tiro rank if no rank exists yet
        val currentRank = campaignDao.currentRank()
        if (currentRank == null) {
            campaignDao.insertRankGrant(RankGrant(
                rankName = "Tiro",
                rankLevel = 0,
                xpAtGrant = 0,
                notes = "Campaign initialized — the path begins"
            ))
        }
    }

    /**
     * Award XP for completing a milestone, with duplicate checking.
     * Returns the XP awarded (0 if already awarded).
     */
    suspend fun awardMilestoneXP(
        campaignDao: CampaignDao,
        milestoneId: String,
        stage: Int
    ): Int {
        // Check if XP already awarded for this milestone
        if (campaignDao.xpAlreadyAwarded(milestoneId, "milestone") > 0) return 0

        val xp = CampaignRanks.MILESTONE_XP[stage] ?: 50
        campaignDao.insertXPEvent(XPEvent(
            source = "milestone",
            sourceId = milestoneId,
            xpAwarded = xp,
            description = "Completed milestone $milestoneId (Stage $stage)"
        ))
        return xp
    }

    /**
     * Award XP for a training session, respecting the daily cap.
     * Returns XP actually awarded (0 if cap reached).
     */
    suspend fun awardTrainingXP(
        campaignDao: CampaignDao,
        sessionId: Long
    ): Int {
        val sourceId = "session-$sessionId"
        if (campaignDao.xpAlreadyAwarded(sourceId, "training") > 0) return 0

        // Check daily cap
        val dayStart = System.currentTimeMillis() - 86_400_000L
        val dailyTotal = campaignDao.trainingXPSince(dayStart)
        if (dailyTotal >= CampaignRanks.DAILY_TRAINING_XP_CAP) return 0

        val xp = minOf(
            CampaignRanks.TRAINING_SESSION_XP,
            CampaignRanks.DAILY_TRAINING_XP_CAP - dailyTotal
        )
        if (xp <= 0) return 0

        campaignDao.insertXPEvent(XPEvent(
            source = "training",
            sourceId = sourceId,
            xpAwarded = xp,
            description = "Training session completed"
        ))
        return xp
    }

    /**
     * Check if user qualifies for a rank promotion and grant it.
     * Returns the new rank if promoted, null otherwise.
     */
    suspend fun checkAndGrantPromotion(campaignDao: CampaignDao): RankGrant? {
        val currentRank = campaignDao.currentRank()
        val currentLevel = currentRank?.rankLevel ?: 0
        if (currentLevel >= 9) return null // Already Imperator

        val totalXP = campaignDao.totalXP()
        val achievedCore = campaignDao.achievedCoreMilestoneCount()
        val totalCore = campaignDao.totalCoreMilestoneCount()

        // Build domain-goal achievement map
        val allGoalProgress = campaignDao.allGoalProgressSync()
        val achievedGoalIds = allGoalProgress.filter { it.status == "achieved" }.map { it.goalId }.toSet()

        // We need goal definitions to map goalId → domain
        // Use a simplified approach: count from milestone progress by domain
        val domainGoalCounts = mutableMapOf<String, Int>()
        val allMilestoneProgress = campaignDao.allMilestoneProgressSync()
        // Group milestones by goalId and check if all 4 stages are achieved
        // (But for now, use goal_progress directly — goals are 'achieved' when user marks them)
        // We need the goal definitions — fetch via a different approach
        // For simplicity: we'll count from goal progress joined with goal definitions
        // This requires a more complex query, but for now we approximate:
        for (gp in allGoalProgress) {
            if (gp.status == "achieved") {
                // We need to look up the domain for this goal
                val goalDef = campaignDao.goal(gp.goalId)
                if (goalDef != null) {
                    domainGoalCounts[goalDef.domain] = (domainGoalCounts[goalDef.domain] ?: 0) + 1
                }
            }
        }

        val eligible = CampaignRanks.calculateEligibleRank(
            totalXP, domainGoalCounts, achievedCore, totalCore
        )

        if (eligible.level > currentLevel) {
            val grant = RankGrant(
                rankName = eligible.name,
                rankLevel = eligible.level,
                xpAtGrant = totalXP,
                notes = "Promoted to ${eligible.name} — ${eligible.title}"
            )
            campaignDao.insertRankGrant(grant)
            return grant
        }

        return null
    }

    private fun loadRegistryJson(context: Context): JSONObject? {
        return try {
            val text = context.assets.open("registry.json")
                .bufferedReader()
                .use { it.readText() }
            JSONObject(text)
        } catch (e: Exception) {
            null
        }
    }
}
