package com.invictus.warrior

import androidx.room.*

/**
 * Imperator Campaign Registry — Room entities.
 *
 * Seed data (GoalDefinition, MilestoneDefinition) is imported once from
 * assets/registry.json and treated as read-only reference.
 * Progress entities (GoalProgress, MilestoneProgress, XPEvent, RankGrant)
 * capture the user's campaign state.
 */

// ── Registry seed data (read-only after import) ─────────────────

@Entity(tableName = "goal_definitions")
data class GoalDefinition(
    @PrimaryKey val id: String,              // "INV-0001"
    val domain: String,                       // "Physique", "Strength", etc.
    val goal: String,                         // Full goal description
    val tier: String,                         // "core", "core-proposed", "maintenance", "prestige"
    val verification: String,                 // How to verify completion
    val sourceInterview: String,              // Source reference ("I", "III", etc.)
    val provenance: String,                   // "user-selected goal", etc.
    val registryVersion: String               // "1.0-draft"
)

@Entity(
    tableName = "milestone_definitions",
    foreignKeys = [ForeignKey(
        entity = GoalDefinition::class,
        parentColumns = ["id"],
        childColumns = ["goalId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("goalId")]
)
data class MilestoneDefinition(
    @PrimaryKey val id: String,              // "INV-0001-M1"
    val goalId: String,                       // FK to GoalDefinition
    val domain: String,                       // Denormalized for query convenience
    val goal: String,                         // Denormalized goal text
    val stage: Int,                           // 1–4
    val stageLabel: String,                   // Stage description
    val category: String,                     // "core", "core-proposed", "maintenance", "prestige"
    val verification: String,                 // How to verify this milestone
    val sourceInterview: String,
    val registryVersion: String
)

// ── User progress ───────────────────────────────────────────────

/**
 * Status values:
 *   unassessed   — not yet evaluated (default from import)
 *   not_started  — user has acknowledged goal, no work done
 *   in_progress  — user is actively working toward this
 *   achieved     — user has completed this goal/milestone
 *   needs_verification — user claims done, awaiting evidence/review
 *   waived       — skipped (waived core does NOT count for Imperator)
 */

@Entity(
    tableName = "goal_progress",
    foreignKeys = [ForeignKey(
        entity = GoalDefinition::class,
        parentColumns = ["id"],
        childColumns = ["goalId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class GoalProgress(
    @PrimaryKey val goalId: String,
    val status: String = "unassessed",
    val currentCapabilityVerified: Boolean = false,
    val lastAssessedAt: Long? = null,
    val achievedAt: Long? = null,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "milestone_progress",
    foreignKeys = [ForeignKey(
        entity = MilestoneDefinition::class,
        parentColumns = ["id"],
        childColumns = ["milestoneId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("goalId")]
)
data class MilestoneProgress(
    @PrimaryKey val milestoneId: String,
    val goalId: String,                       // Denormalized for easy queries
    val status: String = "unassessed",
    val evidenceNotes: String = "",
    val achievedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

// ── XP system ───────────────────────────────────────────────────

@Entity(
    tableName = "xp_events",
    indices = [Index("sourceId"), Index("timestamp")]
)
data class XPEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,                       // "milestone", "training", "assessment", "manual"
    val sourceId: String? = null,             // milestone ID, session ID, etc.
    val xpAwarded: Int,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

// ── Rank system ─────────────────────────────────────────────────

@Entity(tableName = "rank_grants")
data class RankGrant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rankName: String,                     // "Tiro", "Legionary", etc.
    val rankLevel: Int,                       // 0–9
    val grantedAt: Long = System.currentTimeMillis(),
    val xpAtGrant: Int,
    val notes: String = ""
)

// ── Registry version tracking ───────────────────────────────────

@Entity(tableName = "registry_versions")
data class RegistryVersion(
    @PrimaryKey val version: String,          // "1.0-draft"
    val importedAt: Long = System.currentTimeMillis(),
    val goalCount: Int,
    val milestoneCount: Int,
    val frozen: Boolean = true                // Original requirements cannot be changed
)
