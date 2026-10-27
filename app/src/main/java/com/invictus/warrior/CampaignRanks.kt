package com.invictus.warrior

/**
 * Imperator Campaign — Roman rank system and XP calculations.
 *
 * Enlisted ranks (Tiro → Optio) use XP thresholds.
 * Officer ranks (Centurion → Consul) require specific achievement gates.
 * Imperator requires 100% mandatory core milestone completion.
 *
 * Thresholds are draft values — not finalized. Editable without DB reset.
 */

data class RankDefinition(
    val name: String,
    val level: Int,              // 0–9
    val title: String,           // Display title
    val xpRequired: Int,         // Minimum XP to qualify
    val requiresGate: Boolean,   // Whether achievement gates must be met
    val gateDescription: String, // What gates are required (empty for XP-only ranks)
    val insignia: String         // Unicode/emoji insignia for display
)

object CampaignRanks {

    /** All ranks in order from recruit to commander. */
    val RANKS = listOf(
        RankDefinition("Tiro",         0, "Recruit",           0,      false, "", "⊹"),
        RankDefinition("Legionary",    1, "Soldier",           500,    false, "", "⚐"),
        RankDefinition("Decanus",      2, "Squad Leader",      2000,   false, "", "⚑"),
        RankDefinition("Optio",        3, "Officer Candidate", 5000,   false, "", "⚜"),
        RankDefinition("Centurion",    4, "Centurion",         10000,  true,
            "At least 1 goal achieved in 5+ domains", "♕"),
        RankDefinition("Primus Pilus", 5, "First Spear",       18000,  true,
            "At least 3 goals achieved in 5+ domains", "♛"),
        RankDefinition("Tribune",      6, "Tribune",           30000,  true,
            "At least 50% of core milestones achieved", "☨"),
        RankDefinition("Legate",       7, "Legate",            50000,  true,
            "At least 75% of core milestones achieved", "⛊"),
        RankDefinition("Consul",       8, "Consul",            75000,  true,
            "At least 90% of core milestones achieved", "⛨"),
        RankDefinition("Imperator",    9, "Imperator",         100000, true,
            "100% mandatory core milestones achieved", "♆")
    )

    /** XP awarded per milestone stage completion. */
    val MILESTONE_XP = mapOf(
        1 to 50,
        2 to 100,
        3 to 200,
        4 to 400
    )

    /** XP for completing a training session (daily cap applies). */
    const val TRAINING_SESSION_XP = 10

    /** Maximum training XP per 24-hour window (anti-farming). */
    const val DAILY_TRAINING_XP_CAP = 50

    /** XP for a self-assessment or capability check. */
    const val ASSESSMENT_XP = 25

    /** Domains used by the registry. */
    val DOMAINS = listOf(
        "Physique",
        "Strength",
        "Calisthenics & Acrobatics",
        "Combat Unarmed",
        "Combat Armed",
        "Speed & Endurance",
        "Aquatic",
        "Climbing & Wilderness",
        "Resilience & Versatility",
        "Prestige"
    )

    /** Domain abbreviation for compact display. */
    fun domainAbbrev(domain: String): String = when (domain) {
        "Physique" -> "PHY"
        "Strength" -> "STR"
        "Calisthenics & Acrobatics" -> "CAL"
        "Combat Unarmed" -> "CUB"
        "Combat Armed" -> "CAR"
        "Speed & Endurance" -> "SPD"
        "Aquatic" -> "AQU"
        "Climbing & Wilderness" -> "CLM"
        "Resilience & Versatility" -> "RES"
        "Prestige" -> "PRE"
        else -> domain.take(3).uppercase()
    }

    /** Look up rank by level. */
    fun rankAt(level: Int): RankDefinition = RANKS.getOrElse(level) { RANKS.last() }

    /** Determine the highest rank the user qualifies for based on XP and gates. */
    fun calculateEligibleRank(
        totalXP: Int,
        achievedGoalsByDomain: Map<String, Int>,
        achievedCoreMilestones: Int,
        totalCoreMilestones: Int
    ): RankDefinition {
        var eligible = RANKS[0] // Start as Tiro
        for (rank in RANKS) {
            if (totalXP < rank.xpRequired) break
            if (rank.requiresGate) {
                if (!meetsGate(rank, achievedGoalsByDomain, achievedCoreMilestones, totalCoreMilestones)) break
            }
            eligible = rank
        }
        return eligible
    }

    /** Check whether achievement gates are met for a specific rank. */
    private fun meetsGate(
        rank: RankDefinition,
        achievedGoalsByDomain: Map<String, Int>,
        achievedCoreMilestones: Int,
        totalCoreMilestones: Int
    ): Boolean {
        if (totalCoreMilestones == 0) return false
        val corePercent = achievedCoreMilestones.toDouble() / totalCoreMilestones
        val domainsWithGoals = achievedGoalsByDomain.count { it.value >= 1 }
        val domainsWithThreeGoals = achievedGoalsByDomain.count { it.value >= 3 }

        return when (rank.level) {
            4 -> domainsWithGoals >= 5                          // Centurion
            5 -> domainsWithThreeGoals >= 5                     // Primus Pilus
            6 -> corePercent >= 0.50                            // Tribune
            7 -> corePercent >= 0.75                            // Legate
            8 -> corePercent >= 0.90                            // Consul
            9 -> corePercent >= 1.0                             // Imperator
            else -> true
        }
    }

    /** Progress toward next rank as a fraction 0.0–1.0. */
    fun progressToNextRank(
        currentRankLevel: Int,
        totalXP: Int,
        achievedCoreMilestones: Int,
        totalCoreMilestones: Int
    ): Float {
        if (currentRankLevel >= 9) return 1f  // Already Imperator
        val next = RANKS[currentRankLevel + 1]
        val current = RANKS[currentRankLevel]

        // XP progress
        val xpRange = (next.xpRequired - current.xpRequired).coerceAtLeast(1)
        val xpProgress = ((totalXP - current.xpRequired).toFloat() / xpRange).coerceIn(0f, 1f)

        // For gated ranks, also factor in gate progress
        if (next.requiresGate && totalCoreMilestones > 0) {
            val corePercent = achievedCoreMilestones.toFloat() / totalCoreMilestones
            val requiredPercent = when (next.level) {
                6 -> 0.50f
                7 -> 0.75f
                8 -> 0.90f
                9 -> 1.0f
                else -> 0f
            }
            if (requiredPercent > 0f) {
                val gateProgress = (corePercent / requiredPercent).coerceIn(0f, 1f)
                return minOf(xpProgress, gateProgress) // Both must be met
            }
        }

        return xpProgress
    }

    /** Overall Imperator completion percentage (mandatory milestones only). */
    fun imperatorCompletion(achievedCoreMilestones: Int, totalCoreMilestones: Int): Float {
        if (totalCoreMilestones == 0) return 0f
        return (achievedCoreMilestones.toFloat() / totalCoreMilestones).coerceIn(0f, 1f)
    }
}
