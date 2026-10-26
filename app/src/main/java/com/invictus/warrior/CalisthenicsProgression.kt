package com.invictus.warrior

/**
 * Calisthenics Progression System — structured progression trees
 * for bodyweight movement families.
 *
 * Progression suggestions are NEVER auto-applied.
 * Prerequisites must be met before advancing.
 * Advanced skills (handstands, muscle-ups) require qualified coaching
 * and suitable facilities — the app flags this, it does not replace instruction.
 */

// ── Progression level ───────────────────────────────────────────

data class ProgressionLevel(
    val name: String,
    val exerciseName: String,          // Must match ExerciseLibrary name
    val level: Int,                    // 1 = most basic regression
    val prerequisites: List<String>,   // Readable prerequisite descriptions
    val assessmentCriteria: String,    // What proves readiness to advance
    val regressionName: String?,       // Previous level exercise name (null for level 1)
    val progressionName: String?,      // Next level exercise name (null for final)
    val coachingRequired: Boolean = false,
    val facilityNote: String = ""
)

// ── Movement family ─────────────────────────────────────────────

data class MovementFamily(
    val name: String,
    val description: String,
    val levels: List<ProgressionLevel>,
    val safetyNotes: String = ""
) {
    fun currentLevel(completedExercise: String): ProgressionLevel? =
        levels.firstOrNull { it.exerciseName == completedExercise }

    fun nextLevel(completedExercise: String): ProgressionLevel? {
        val current = currentLevel(completedExercise) ?: return levels.firstOrNull()
        return current.progressionName?.let { next ->
            levels.firstOrNull { it.exerciseName == next }
        }
    }

    fun regressionOf(exerciseName: String): ProgressionLevel? {
        val current = levels.firstOrNull { it.exerciseName == exerciseName } ?: return null
        return current.regressionName?.let { prev ->
            levels.firstOrNull { it.exerciseName == prev }
        }
    }
}

// ── Progression trees ───────────────────────────────────────────

object CalisthenicsProgression {

    val pushUpFamily = MovementFamily(
        name = "Push-up",
        description = "Horizontal pushing progression from wall to one-arm",
        levels = listOf(
            ProgressionLevel(
                name = "Wall Push-up", exerciseName = "Wall Push-up", level = 1,
                prerequisites = emptyList(),
                assessmentCriteria = "3 sets of 15 with controlled tempo",
                regressionName = null, progressionName = "Incline Push-up"
            ),
            ProgressionLevel(
                name = "Incline Push-up", exerciseName = "Incline Push-up", level = 2,
                prerequisites = listOf("Wall Push-up: 3×15 controlled"),
                assessmentCriteria = "3 sets of 12 on knee-height surface",
                regressionName = "Wall Push-up", progressionName = "Push-up"
            ),
            ProgressionLevel(
                name = "Standard Push-up", exerciseName = "Push-up", level = 3,
                prerequisites = listOf("Incline Push-up: 3×12 controlled"),
                assessmentCriteria = "3 sets of 15 with full ROM, no sagging",
                regressionName = "Incline Push-up", progressionName = "Diamond Push-up"
            ),
            ProgressionLevel(
                name = "Diamond Push-up", exerciseName = "Diamond Push-up", level = 4,
                prerequisites = listOf("Push-up: 3×15 strict form"),
                assessmentCriteria = "3 sets of 12 with hands touching",
                regressionName = "Push-up", progressionName = "Decline Push-up"
            ),
            ProgressionLevel(
                name = "Decline Push-up", exerciseName = "Decline Push-up", level = 5,
                prerequisites = listOf("Diamond Push-up: 3×12"),
                assessmentCriteria = "3 sets of 12 feet elevated 18+ inches",
                regressionName = "Diamond Push-up", progressionName = "Archer Push-up"
            ),
            ProgressionLevel(
                name = "Archer Push-up", exerciseName = "Archer Push-up", level = 6,
                prerequisites = listOf("Decline Push-up: 3×12", "Diamond Push-up: 3×12"),
                assessmentCriteria = "3 sets of 8 each side, controlled eccentric",
                regressionName = "Decline Push-up", progressionName = null
            )
        )
    )

    val pullUpFamily = MovementFamily(
        name = "Pull-up",
        description = "Vertical pulling progression from dead hang to strict pull-ups",
        levels = listOf(
            ProgressionLevel(
                name = "Dead Hang", exerciseName = "Dead Hang", level = 1,
                prerequisites = listOf("Access to a pull-up bar"),
                assessmentCriteria = "30-second hang with relaxed shoulders packed",
                regressionName = null, progressionName = "Assisted Pull-up"
            ),
            ProgressionLevel(
                name = "Assisted Pull-up", exerciseName = "Assisted Pull-up", level = 2,
                prerequisites = listOf("Dead Hang: 30 seconds"),
                assessmentCriteria = "3 sets of 8 with band or machine assist reduced to ~30% bodyweight",
                regressionName = "Dead Hang", progressionName = "Chin-up"
            ),
            ProgressionLevel(
                name = "Chin-up", exerciseName = "Chin-up", level = 3,
                prerequisites = listOf("Assisted Pull-up: 3×8 with light assist"),
                assessmentCriteria = "3 sets of 5 strict chin-ups (supinated grip)",
                regressionName = "Assisted Pull-up", progressionName = "Pull-up"
            ),
            ProgressionLevel(
                name = "Pull-up", exerciseName = "Pull-up", level = 4,
                prerequisites = listOf("Chin-up: 3×5 strict"),
                assessmentCriteria = "3 sets of 5 strict pull-ups (pronated grip, chin over bar)",
                regressionName = "Chin-up", progressionName = null
            )
        ),
        safetyNotes = "Avoid kipping or swinging. Full dead-hang start, controlled descent."
    )

    val dipFamily = MovementFamily(
        name = "Dip",
        description = "Pushing depression progression from bench dips to parallel bar dips",
        levels = listOf(
            ProgressionLevel(
                name = "Bench Dip", exerciseName = "Bench Dip", level = 1,
                prerequisites = emptyList(),
                assessmentCriteria = "3 sets of 15, feet flat on floor, 90° elbow bend",
                regressionName = null, progressionName = "Dip"
            ),
            ProgressionLevel(
                name = "Parallel Bar Dip", exerciseName = "Dip", level = 2,
                prerequisites = listOf("Bench Dip: 3×15 controlled"),
                assessmentCriteria = "3 sets of 8, elbows to 90°, no forward lean past 30°",
                regressionName = "Bench Dip", progressionName = null
            )
        ),
        safetyNotes = "Do not descend past 90° elbow angle without adequate shoulder mobility."
    )

    val squatFamily = MovementFamily(
        name = "Squat",
        description = "Lower body squatting progression from bodyweight to single-leg",
        levels = listOf(
            ProgressionLevel(
                name = "Bodyweight Squat", exerciseName = "Bodyweight Squat", level = 1,
                prerequisites = emptyList(),
                assessmentCriteria = "3 sets of 20, hips below knees, heels down",
                regressionName = null, progressionName = "Assisted Pistol Squat"
            ),
            ProgressionLevel(
                name = "Assisted Pistol Squat", exerciseName = "Assisted Pistol Squat", level = 2,
                prerequisites = listOf("Bodyweight Squat: 3×20 full depth"),
                assessmentCriteria = "3 sets of 5 each leg, holding support, controlled descent",
                regressionName = "Bodyweight Squat", progressionName = "Pistol Squat"
            ),
            ProgressionLevel(
                name = "Pistol Squat", exerciseName = "Pistol Squat", level = 3,
                prerequisites = listOf("Assisted Pistol Squat: 3×5 each leg with light assist"),
                assessmentCriteria = "3 sets of 3 each leg, full depth, no assist",
                regressionName = "Assisted Pistol Squat", progressionName = null
            )
        ),
        safetyNotes = "Pistol squats require significant ankle and hip mobility. Do not force depth."
    )

    val unilateralLegFamily = MovementFamily(
        name = "Unilateral Leg",
        description = "Single-leg strength and balance progression",
        levels = listOf(
            ProgressionLevel(
                name = "Walking Lunge", exerciseName = "Walking Lunge", level = 1,
                prerequisites = listOf("Bodyweight Squat: 3×15"),
                assessmentCriteria = "3 sets of 10 each leg, knee tracks over toes",
                regressionName = null, progressionName = "Bulgarian Split Squat"
            ),
            ProgressionLevel(
                name = "Bulgarian Split Squat", exerciseName = "Bulgarian Split Squat", level = 2,
                prerequisites = listOf("Walking Lunge: 3×10 each leg"),
                assessmentCriteria = "3 sets of 8 each leg, rear foot elevated, full ROM",
                regressionName = "Walking Lunge", progressionName = "Shrimp Squat"
            ),
            ProgressionLevel(
                name = "Shrimp Squat", exerciseName = "Shrimp Squat", level = 3,
                prerequisites = listOf("Bulgarian Split Squat: 3×8 each leg"),
                assessmentCriteria = "3 sets of 5 each leg, knee to floor, no assist",
                regressionName = "Bulgarian Split Squat", progressionName = null
            )
        )
    )

    val plankFamily = MovementFamily(
        name = "Plank",
        description = "Isometric core stability progression",
        levels = listOf(
            ProgressionLevel(
                name = "Plank", exerciseName = "Plank", level = 1,
                prerequisites = emptyList(),
                assessmentCriteria = "60-second hold, neutral spine, no hip sag",
                regressionName = null, progressionName = "Side Plank"
            ),
            ProgressionLevel(
                name = "Side Plank", exerciseName = "Side Plank", level = 2,
                prerequisites = listOf("Plank: 60 seconds"),
                assessmentCriteria = "45 seconds each side, hips level",
                regressionName = "Plank", progressionName = "Hollow Body Hold"
            ),
            ProgressionLevel(
                name = "Hollow Body Hold", exerciseName = "Hollow Body Hold", level = 3,
                prerequisites = listOf("Plank: 60 seconds", "Side Plank: 45 seconds"),
                assessmentCriteria = "30-second hold, lower back pressed to floor, legs 6 inches off ground",
                regressionName = "Side Plank", progressionName = null
            )
        )
    )

    val lSitFamily = MovementFamily(
        name = "L-Sit",
        description = "Compression and core strength progression toward full L-sit",
        levels = listOf(
            ProgressionLevel(
                name = "Tuck L-Sit", exerciseName = "Tuck L-Sit", level = 1,
                prerequisites = listOf("Plank: 60 seconds"),
                assessmentCriteria = "15-second hold on floor or parallettes, hips off ground",
                regressionName = null, progressionName = "Hanging Knee Raise"
            ),
            ProgressionLevel(
                name = "Hanging Knee Raise", exerciseName = "Hanging Knee Raise", level = 2,
                prerequisites = listOf("Tuck L-Sit: 15 seconds", "Dead Hang: 30 seconds"),
                assessmentCriteria = "3 sets of 10, knees to chest, controlled descent",
                regressionName = "Tuck L-Sit", progressionName = "Hanging Leg Raise"
            ),
            ProgressionLevel(
                name = "Hanging Leg Raise", exerciseName = "Hanging Leg Raise", level = 3,
                prerequisites = listOf("Hanging Knee Raise: 3×10"),
                assessmentCriteria = "3 sets of 8, legs straight, toes to bar",
                regressionName = "Hanging Knee Raise", progressionName = "L-Sit"
            ),
            ProgressionLevel(
                name = "Full L-Sit", exerciseName = "L-Sit", level = 4,
                prerequisites = listOf("Hanging Leg Raise: 3×8", "Tuck L-Sit: 15 seconds"),
                assessmentCriteria = "10-second hold, legs parallel to ground, on parallettes or dip bars",
                regressionName = "Hanging Leg Raise", progressionName = null
            )
        ),
        safetyNotes = "L-sits require significant hip flexor and core strength. Do not rush progression."
    )

    val handstandFamily = MovementFamily(
        name = "Handstand",
        description = "Inverted pressing and balance progression. Advanced levels require coaching.",
        levels = listOf(
            ProgressionLevel(
                name = "Pike Push-up", exerciseName = "Pike Push-up", level = 1,
                prerequisites = listOf("Push-up: 3×15", "Plank: 60 seconds"),
                assessmentCriteria = "3 sets of 10, hips high, head touches ground",
                regressionName = null, progressionName = "Wall Handstand Hold"
            ),
            ProgressionLevel(
                name = "Wall Handstand Hold", exerciseName = "Wall Handstand Hold", level = 2,
                prerequisites = listOf("Pike Push-up: 3×10"),
                assessmentCriteria = "30-second chest-to-wall hold, arms locked, body straight",
                regressionName = "Pike Push-up", progressionName = null,
                coachingRequired = true,
                facilityNote = "Requires flat, solid surface and clear wall. " +
                        "Not safe on uneven grass or outdoor terrain. " +
                        "Consider qualified instruction for freestanding handstand work."
            )
        ),
        safetyNotes = "Handstand training beyond wall-supported holds requires qualified coaching " +
                "and appropriate facilities. The app does not prescribe freestanding handstand " +
                "progressions — seek in-person instruction."
    )

    // ── All families ────────────────────────────────────────────

    val allFamilies: List<MovementFamily> = listOf(
        pushUpFamily, pullUpFamily, dipFamily, squatFamily,
        unilateralLegFamily, plankFamily, lSitFamily, handstandFamily
    )

    private val familyByName: Map<String, MovementFamily> =
        allFamilies.associateBy { it.name }

    fun family(name: String): MovementFamily? = familyByName[name]

    /**
     * Given a user's current exercise, find which family it belongs to
     * and what the next step would be.
     *
     * Returns null if exercise is not in any progression tree.
     * Progression is SUGGESTED, never auto-applied.
     */
    fun suggestProgression(currentExercise: String): ProgressionSuggestion? {
        for (f in allFamilies) {
            val level = f.currentLevel(currentExercise)
            if (level != null) {
                val next = f.nextLevel(currentExercise)
                return ProgressionSuggestion(
                    family = f.name,
                    currentLevel = level,
                    nextLevel = next,
                    isMaxLevel = next == null
                )
            }
        }
        return null
    }

    /**
     * Assess readiness for a progression level based on logged performance.
     * Returns a human-readable assessment — does NOT auto-advance.
     */
    fun assessReadiness(
        exerciseName: String,
        bestSets: Int,
        bestReps: Int,
        bestHoldSeconds: Int = 0
    ): ReadinessAssessment {
        val suggestion = suggestProgression(exerciseName) ?: return ReadinessAssessment(
            exercise = exerciseName,
            ready = false,
            message = "Exercise not in a tracked progression tree."
        )

        if (suggestion.isMaxLevel) return ReadinessAssessment(
            exercise = exerciseName,
            ready = false,
            message = "Already at the most advanced level in the ${suggestion.family} family. " +
                    "Continue building volume and quality."
        )

        val next = suggestion.nextLevel ?: return ReadinessAssessment(
            exercise = exerciseName,
            ready = false,
            message = "No further progressions defined."
        )

        // Parse assessment criteria for rough numeric check
        // This is conservative — human judgment should supplement this
        val criteria = suggestion.currentLevel.assessmentCriteria
        val ready = when {
            criteria.contains("sets of") -> {
                val setsMatch = Regex("(\\d+)\\s*sets?").find(criteria)
                val repsMatch = Regex("of\\s*(\\d+)").find(criteria)
                val targetSets = setsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 3
                val targetReps = repsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 10
                bestSets >= targetSets && bestReps >= targetReps
            }
            criteria.contains("second") -> {
                val secMatch = Regex("(\\d+)[-–]?second").find(criteria)
                val targetSec = secMatch?.groupValues?.get(1)?.toIntOrNull() ?: 30
                bestHoldSeconds >= targetSec
            }
            else -> false // Conservative: don't claim readiness if we can't verify
        }

        val coachingNote = if (next.coachingRequired)
            " This progression recommends qualified coaching." else ""
        val facilityNote = if (next.facilityNote.isNotEmpty())
            " Note: ${next.facilityNote}" else ""

        return ReadinessAssessment(
            exercise = exerciseName,
            ready = ready,
            message = if (ready)
                "Assessment criteria met for ${suggestion.currentLevel.name}. " +
                        "Consider trying ${next.name} when you feel prepared.$coachingNote$facilityNote"
            else
                "Continue working on ${suggestion.currentLevel.name}. " +
                        "Target: ${criteria}.$coachingNote"
        )
    }

    /**
     * Get a beginner-appropriate starting point for each family
     * based on user's described ability level.
     */
    fun beginnerStartingPoints(): Map<String, ProgressionLevel> =
        allFamilies.associate { family ->
            family.name to family.levels.first()
        }
}

data class ProgressionSuggestion(
    val family: String,
    val currentLevel: ProgressionLevel,
    val nextLevel: ProgressionLevel?,
    val isMaxLevel: Boolean
)

data class ReadinessAssessment(
    val exercise: String,
    val ready: Boolean,
    val message: String
)
