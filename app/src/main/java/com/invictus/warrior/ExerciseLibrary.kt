package com.invictus.warrior

/**
 * Exercise Library — definitions, movement patterns, training modes,
 * equipment profiles, and substitution logic.
 *
 * Exercise definitions are independent of logged sessions.
 * Historical variations are preserved when a user advances.
 */

// ── Training modes ──────────────────────────────────────────────

enum class TrainingMode(val label: String) {
    WEIGHTS("Weights"),
    CALISTHENICS("Calisthenics"),
    HYBRID("Hybrid");

    companion object {
        val DEFAULT = HYBRID
        fun fromString(s: String): TrainingMode =
            entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: DEFAULT
    }
}

// ── Movement patterns ───────────────────────────────────────────

enum class MovementPattern(val label: String) {
    HORIZONTAL_PUSH("Horizontal Push"),
    HORIZONTAL_PULL("Horizontal Pull"),
    VERTICAL_PUSH("Vertical Push"),
    VERTICAL_PULL("Vertical Pull"),
    SQUAT("Squat"),
    HINGE("Hinge"),
    LUNGE("Lunge / Unilateral Leg"),
    CARRY("Carry"),
    CORE("Core / Anti-Rotation"),
    ISOLATION_UPPER("Isolation — Upper"),
    ISOLATION_LOWER("Isolation — Lower"),
    CONDITIONING("Conditioning"),
    SKILL("Skill / Movement Practice")
}

// ── Measurement types ───────────────────────────────────────────

enum class MeasurementType(val label: String) {
    WEIGHT_REPS("Weight + Reps"),
    REPS_ONLY("Reps Only"),
    HOLD_DURATION("Hold Duration"),
    DURATION_DISTANCE("Duration + Distance"),
    ASSISTED_WEIGHT("Assisted / Added Weight"),
    SKILL_LEVEL("Skill Level");

    companion object {
        fun fromString(s: String): MeasurementType =
            entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: WEIGHT_REPS
    }
}

// ── Equipment ───────────────────────────────────────────────────

enum class Equipment(val label: String) {
    BARBELL("Barbell"),
    DUMBBELL("Dumbbell"),
    CABLE("Cable Machine"),
    MACHINE("Machine"),
    PULL_UP_BAR("Pull-up Bar"),
    DIP_STATION("Dip Station"),
    RINGS("Gymnastics Rings"),
    BENCH("Bench"),
    BODYWEIGHT("Bodyweight"),
    KETTLEBELL("Kettlebell"),
    BAND("Resistance Band"),
    FLOOR("Floor Space"),
    NONE("No Equipment")
}

data class EquipmentProfile(
    val name: String,
    val description: String,
    val available: Set<Equipment>,
    val notes: String = ""
) {
    companion object {
        val TREX_FULL_GYM = EquipmentProfile(
            "TREX Full Gym",
            "Full commercial gym: barbells, dumbbells, cables, machines, pull-up bars, dip stations",
            Equipment.entries.toSet() - Equipment.RINGS,
            "All standard gym equipment available"
        )
        val HOME_BODYWEIGHT = EquipmentProfile(
            "Home Bodyweight",
            "Bodyweight training at home with floor space",
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR, Equipment.NONE),
            "No fixed equipment. Pull-up bar or bands recommended additions."
        )
        val OUTDOOR_SPACE = EquipmentProfile(
            "Outdoor Space",
            "Outdoor area — uneven terrain, limited equipment",
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR, Equipment.NONE),
            "User's space is ~15×15 ft of uneven grass. Do not recommend advanced inversions or acrobatics here."
        )
        val CUSTOM = EquipmentProfile(
            "Custom",
            "User-selected equipment",
            emptySet()
        )

        val PRESETS = listOf(TREX_FULL_GYM, HOME_BODYWEIGHT, OUTDOOR_SPACE)
    }
}

// ── Exercise definition ─────────────────────────────────────────

data class ExerciseDefinition(
    val name: String,
    val pattern: MovementPattern,
    val primaryMeasurement: MeasurementType,
    val equipment: Set<Equipment>,
    val modes: Set<TrainingMode>,
    val difficulty: Int,           // 1 (beginner) to 5 (advanced)
    val description: String = "",
    val safetyNotes: String = "",
    val isBodyweight: Boolean = false
)

// ── Substitution ────────────────────────────────────────────────

data class Substitution(
    val original: String,
    val replacement: String,
    val reason: String,
    val equivalenceNote: String    // Always honest about mechanical differences
)

object ExerciseLibrary {

    val exercises: List<ExerciseDefinition> = buildList {
        // ── Horizontal Push ──
        add(ExerciseDefinition("Wall Push-up", MovementPattern.HORIZONTAL_PUSH, MeasurementType.REPS_ONLY,
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 1,
            "Hands on wall, full arm extension", isBodyweight = true))
        add(ExerciseDefinition("Incline Push-up", MovementPattern.HORIZONTAL_PUSH, MeasurementType.REPS_ONLY,
            setOf(Equipment.BODYWEIGHT, Equipment.BENCH), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 1,
            "Hands on elevated surface (bench, step, etc.)", isBodyweight = true))
        add(ExerciseDefinition("Bench Press", MovementPattern.HORIZONTAL_PUSH, MeasurementType.WEIGHT_REPS,
            setOf(Equipment.BARBELL, Equipment.BENCH), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 2,
            "Barbell flat bench press", "Use spotter or safety pins for heavy sets"))
        add(ExerciseDefinition("Incline Machine Press", MovementPattern.HORIZONTAL_PUSH, MeasurementType.WEIGHT_REPS,
            setOf(Equipment.MACHINE), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 1,
            "Machine-guided incline press"))
        add(ExerciseDefinition("Machine Chest Press", MovementPattern.HORIZONTAL_PUSH, MeasurementType.WEIGHT_REPS,
            setOf(Equipment.MACHINE), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 1))
        add(ExerciseDefinition("Dumbbell Bench Press", MovementPattern.HORIZONTAL_PUSH, MeasurementType.WEIGHT_REPS,
            setOf(Equipment.DUMBBELL, Equipment.BENCH), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 2))
        add(ExerciseDefinition("Push-up", MovementPattern.HORIZONTAL_PUSH, MeasurementType.REPS_ONLY,
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 1,
            "Standard push-up", isBodyweight = true))
        add(ExerciseDefinition("Diamond Push-up", MovementPattern.HORIZONTAL_PUSH, MeasurementType.REPS_ONLY,
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 2,
            isBodyweight = true))
        add(ExerciseDefinition("Archer Push-up", MovementPattern.HORIZONTAL_PUSH, MeasurementType.REPS_ONLY,
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 3,
            isBodyweight = true))
        add(ExerciseDefinition("Decline Push-up", MovementPattern.HORIZONTAL_PUSH, MeasurementType.REPS_ONLY,
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 2,
            isBodyweight = true))
        add(ExerciseDefinition("Dip", MovementPattern.HORIZONTAL_PUSH, MeasurementType.REPS_ONLY,
            setOf(Equipment.DIP_STATION), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 2,
            isBodyweight = true))
        add(ExerciseDefinition("Bench Dip", MovementPattern.HORIZONTAL_PUSH, MeasurementType.REPS_ONLY,
            setOf(Equipment.BENCH, Equipment.BODYWEIGHT), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 1,
            isBodyweight = true))

        // ── Horizontal Pull ──
        add(ExerciseDefinition("Chest-Supported Row", MovementPattern.HORIZONTAL_PULL, MeasurementType.WEIGHT_REPS,
            setOf(Equipment.DUMBBELL, Equipment.BENCH), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 2))
        add(ExerciseDefinition("Seated Cable Row", MovementPattern.HORIZONTAL_PULL, MeasurementType.WEIGHT_REPS,
            setOf(Equipment.CABLE), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 1))
        add(ExerciseDefinition("Inverted Row", MovementPattern.HORIZONTAL_PULL, MeasurementType.REPS_ONLY,
            setOf(Equipment.PULL_UP_BAR, Equipment.BODYWEIGHT), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 1,
            "Bodyweight row under a bar or rings", isBodyweight = true))
        add(ExerciseDefinition("Band-Assisted Row", MovementPattern.HORIZONTAL_PULL, MeasurementType.REPS_ONLY,
            setOf(Equipment.BAND), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 1))

        // ── Vertical Push ──
        add(ExerciseDefinition("Seated Shoulder Press", MovementPattern.VERTICAL_PUSH, MeasurementType.WEIGHT_REPS,
            setOf(Equipment.DUMBBELL, Equipment.BENCH), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 2))
        add(ExerciseDefinition("Pike Push-up", MovementPattern.VERTICAL_PUSH, MeasurementType.REPS_ONLY,
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 2,
            "Feet elevated for more shoulder emphasis", isBodyweight = true))
        add(ExerciseDefinition("Wall Handstand Hold", MovementPattern.VERTICAL_PUSH, MeasurementType.HOLD_DURATION,
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 3,
            "Chest-to-wall handstand hold for time", "Practice on flat, solid surface only. Not recommended on uneven ground.",
            isBodyweight = true))

        // ── Vertical Pull ──
        add(ExerciseDefinition("Lat Pulldown", MovementPattern.VERTICAL_PULL, MeasurementType.WEIGHT_REPS,
            setOf(Equipment.CABLE), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 1))
        add(ExerciseDefinition("Assisted Pull-up", MovementPattern.VERTICAL_PULL, MeasurementType.ASSISTED_WEIGHT,
            setOf(Equipment.PULL_UP_BAR, Equipment.BAND), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 2,
            "Band or machine-assisted pull-up", isBodyweight = true))
        add(ExerciseDefinition("Pull-up", MovementPattern.VERTICAL_PULL, MeasurementType.REPS_ONLY,
            setOf(Equipment.PULL_UP_BAR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 3,
            isBodyweight = true))
        add(ExerciseDefinition("Chin-up", MovementPattern.VERTICAL_PULL, MeasurementType.REPS_ONLY,
            setOf(Equipment.PULL_UP_BAR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 3,
            isBodyweight = true))
        add(ExerciseDefinition("Dead Hang", MovementPattern.VERTICAL_PULL, MeasurementType.HOLD_DURATION,
            setOf(Equipment.PULL_UP_BAR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 1,
            isBodyweight = true))

        // ── Squat ──
        add(ExerciseDefinition("Leg Press", MovementPattern.SQUAT, MeasurementType.WEIGHT_REPS,
            setOf(Equipment.MACHINE), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 1))
        add(ExerciseDefinition("Barbell Squat", MovementPattern.SQUAT, MeasurementType.WEIGHT_REPS,
            setOf(Equipment.BARBELL), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 3,
            safetyNotes = "Use squat rack with safety pins"))
        add(ExerciseDefinition("Bodyweight Squat", MovementPattern.SQUAT, MeasurementType.REPS_ONLY,
            setOf(Equipment.BODYWEIGHT), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 1,
            isBodyweight = true))
        add(ExerciseDefinition("Pistol Squat", MovementPattern.SQUAT, MeasurementType.REPS_ONLY,
            setOf(Equipment.BODYWEIGHT), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 4,
            "Single-leg squat. Significant balance and mobility required.", isBodyweight = true))
        add(ExerciseDefinition("Assisted Pistol Squat", MovementPattern.SQUAT, MeasurementType.REPS_ONLY,
            setOf(Equipment.BODYWEIGHT, Equipment.BAND), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 3,
            "Hold a doorframe or band for balance", isBodyweight = true))

        // ── Hinge ──
        add(ExerciseDefinition("Romanian Deadlift", MovementPattern.HINGE, MeasurementType.WEIGHT_REPS,
            setOf(Equipment.BARBELL), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 2))
        add(ExerciseDefinition("Seated Leg Curl", MovementPattern.HINGE, MeasurementType.WEIGHT_REPS,
            setOf(Equipment.MACHINE), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 1))
        add(ExerciseDefinition("Nordic Curl", MovementPattern.HINGE, MeasurementType.REPS_ONLY,
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 4,
            "Eccentric hamstring curl. Very demanding.", isBodyweight = true))
        add(ExerciseDefinition("Glute Bridge", MovementPattern.HINGE, MeasurementType.REPS_ONLY,
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 1,
            isBodyweight = true))
        add(ExerciseDefinition("Single-Leg Glute Bridge", MovementPattern.HINGE, MeasurementType.REPS_ONLY,
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 2,
            isBodyweight = true))

        // ── Lunge / Unilateral ──
        add(ExerciseDefinition("Supported Step-up", MovementPattern.LUNGE, MeasurementType.WEIGHT_REPS,
            setOf(Equipment.BENCH, Equipment.DUMBBELL), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 2))
        add(ExerciseDefinition("Walking Lunge", MovementPattern.LUNGE, MeasurementType.REPS_ONLY,
            setOf(Equipment.BODYWEIGHT), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 1,
            isBodyweight = true))
        add(ExerciseDefinition("Bulgarian Split Squat", MovementPattern.LUNGE, MeasurementType.REPS_ONLY,
            setOf(Equipment.BODYWEIGHT, Equipment.BENCH), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 3,
            "Rear foot elevated split squat", isBodyweight = true))
        add(ExerciseDefinition("Shrimp Squat", MovementPattern.LUNGE, MeasurementType.REPS_ONLY,
            setOf(Equipment.BODYWEIGHT), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 4,
            "Advanced unilateral bodyweight squat variant", isBodyweight = true))

        // ── Carry ──
        add(ExerciseDefinition("Farmer Carry", MovementPattern.CARRY, MeasurementType.DURATION_DISTANCE,
            setOf(Equipment.DUMBBELL), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 1))
        add(ExerciseDefinition("Suitcase Carry", MovementPattern.CARRY, MeasurementType.DURATION_DISTANCE,
            setOf(Equipment.DUMBBELL, Equipment.KETTLEBELL), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 2))

        // ── Core ──
        add(ExerciseDefinition("Plank", MovementPattern.CORE, MeasurementType.HOLD_DURATION,
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 1,
            isBodyweight = true))
        add(ExerciseDefinition("Side Plank", MovementPattern.CORE, MeasurementType.HOLD_DURATION,
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 1,
            isBodyweight = true))
        add(ExerciseDefinition("Hollow Body Hold", MovementPattern.CORE, MeasurementType.HOLD_DURATION,
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 2,
            isBodyweight = true))
        add(ExerciseDefinition("L-Sit", MovementPattern.CORE, MeasurementType.HOLD_DURATION,
            setOf(Equipment.DIP_STATION, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 4,
            "Legs straight, held parallel to ground", isBodyweight = true))
        add(ExerciseDefinition("Tuck L-Sit", MovementPattern.CORE, MeasurementType.HOLD_DURATION,
            setOf(Equipment.FLOOR, Equipment.BODYWEIGHT), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 2,
            "Knees tucked toward chest variant", isBodyweight = true))
        add(ExerciseDefinition("Hanging Knee Raise", MovementPattern.CORE, MeasurementType.REPS_ONLY,
            setOf(Equipment.PULL_UP_BAR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 2,
            isBodyweight = true))
        add(ExerciseDefinition("Hanging Leg Raise", MovementPattern.CORE, MeasurementType.REPS_ONLY,
            setOf(Equipment.PULL_UP_BAR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 3,
            isBodyweight = true))

        // ── Isolation — Upper ──
        add(ExerciseDefinition("Lateral Raise", MovementPattern.ISOLATION_UPPER, MeasurementType.WEIGHT_REPS,
            setOf(Equipment.DUMBBELL), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 1))
        add(ExerciseDefinition("Cable Curl", MovementPattern.ISOLATION_UPPER, MeasurementType.WEIGHT_REPS,
            setOf(Equipment.CABLE), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 1))
        add(ExerciseDefinition("Triceps Pressdown", MovementPattern.ISOLATION_UPPER, MeasurementType.WEIGHT_REPS,
            setOf(Equipment.CABLE), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 1))

        // ── Conditioning ──
        add(ExerciseDefinition("Elliptical", MovementPattern.CONDITIONING, MeasurementType.DURATION_DISTANCE,
            setOf(Equipment.MACHINE), setOf(TrainingMode.WEIGHTS, TrainingMode.HYBRID), 1))
        add(ExerciseDefinition("Southpaw Shadowboxing", MovementPattern.CONDITIONING, MeasurementType.HOLD_DURATION,
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 2,
            isBodyweight = true))
        add(ExerciseDefinition("Footwork", MovementPattern.CONDITIONING, MeasurementType.HOLD_DURATION,
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 2,
            isBodyweight = true))
        add(ExerciseDefinition("Controlled Kicking Practice", MovementPattern.CONDITIONING, MeasurementType.HOLD_DURATION,
            setOf(Equipment.BODYWEIGHT, Equipment.FLOOR), setOf(TrainingMode.CALISTHENICS, TrainingMode.HYBRID), 2,
            isBodyweight = true))
    }

    private val byName: Map<String, ExerciseDefinition> = exercises.associateBy { it.name }
    private val byPattern: Map<MovementPattern, List<ExerciseDefinition>> = exercises.groupBy { it.pattern }

    fun find(name: String): ExerciseDefinition? =
        byName[name]
            ?: name.split(" / ", " / ").firstNotNullOfOrNull { part -> byName[part.trim()] }

    fun forPattern(pattern: MovementPattern): List<ExerciseDefinition> =
        byPattern[pattern] ?: emptyList()

    fun forMode(mode: TrainingMode): List<ExerciseDefinition> =
        exercises.filter { mode in it.modes }

    fun forEquipment(profile: EquipmentProfile): List<ExerciseDefinition> =
        exercises.filter { def -> def.equipment.any { it in profile.available } }

    /**
     * Find substitutions for an exercise given mode and equipment constraints.
     * Returns alternatives in the same movement pattern, sorted by difficulty.
     * Explicitly notes mechanical differences — never claims equivalence.
     */
    fun findSubstitutions(
        exerciseName: String,
        mode: TrainingMode,
        profile: EquipmentProfile
    ): List<Substitution> {
        val original = find(exerciseName) ?: return emptyList()
        return forPattern(original.pattern)
            .filter { it.name != exerciseName }
            .filter { mode in it.modes || mode == TrainingMode.HYBRID }
            .filter { def -> def.equipment.any { it in profile.available } }
            .sortedBy { it.difficulty }
            .map { alt ->
                Substitution(
                    original = exerciseName,
                    replacement = alt.name,
                    reason = "Same movement pattern (${original.pattern.label})",
                    equivalenceNote = buildEquivalenceNote(original, alt)
                )
            }
    }

    private fun buildEquivalenceNote(from: ExerciseDefinition, to: ExerciseDefinition): String {
        val notes = mutableListOf<String>()
        if (from.isBodyweight != to.isBodyweight)
            notes += if (to.isBodyweight) "bodyweight alternative — load progressed differently"
            else "weighted alternative — allows fine load increments"
        if (from.difficulty != to.difficulty)
            notes += if (to.difficulty > from.difficulty) "more difficult variant"
            else "easier variant — good regression"
        if (from.primaryMeasurement != to.primaryMeasurement)
            notes += "different tracking: ${to.primaryMeasurement.label}"
        return if (notes.isEmpty()) "Similar movement pattern; not mechanically identical."
        else notes.joinToString(". ") + "."
    }
}
