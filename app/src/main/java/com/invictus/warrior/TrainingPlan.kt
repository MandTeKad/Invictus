package com.invictus.warrior

import java.time.DayOfWeek
import java.time.LocalDate

// Rep ranges are program targets, not claims of measured present ability.
data class PlannedExercise(val name: String, val sets: Int, val repRange: String)
data class PlannedDay(val title: String, val exercises: List<PlannedExercise>)

object TrainingPlan {
    val days = linkedMapOf(
        "Monday" to PlannedDay("Upper Strength", listOf(
            PlannedExercise("Bench Press",3,"5–6"), PlannedExercise("Chest-Supported Row",3,"6–10"),
            PlannedExercise("Incline Machine Press",2,"8–12"), PlannedExercise("Lat Pulldown",3,"8–12"),
            PlannedExercise("Lateral Raise",3,"12–15"), PlannedExercise("Farmer Carry",3,"20–30 m"))),
        "Tuesday" to PlannedDay("Striking & Conditioning", listOf(
            PlannedExercise("Southpaw Shadowboxing",3,"2 min"), PlannedExercise("Footwork",3,"3 min"),
            PlannedExercise("Controlled Kicking Practice",3,"5 min"), PlannedExercise("Elliptical",1,"15–25 min"))),
        "Wednesday" to PlannedDay("Lower & Functional", listOf(
            PlannedExercise("Leg Press / Squat",3,"6–10"), PlannedExercise("Romanian Deadlift",3,"8"),
            PlannedExercise("Seated Leg Curl",3,"10–12"), PlannedExercise("Supported Step-up",2,"8/leg"),
            PlannedExercise("Assisted Pull-up / Pulldown",3,"8–10"), PlannedExercise("Suitcase Carry",3,"20 m/side"))),
        "Thursday" to PlannedDay("Hypertrophy & Mobility", listOf(
            PlannedExercise("Machine Chest Press",3,"8–12"), PlannedExercise("Seated Cable Row",3,"10–12"),
            PlannedExercise("Seated Shoulder Press",2,"8–12"), PlannedExercise("Lateral Raise",3,"12–20"),
            PlannedExercise("Cable Curl",2,"10–15"), PlannedExercise("Triceps Pressdown",2,"10–15"))),
        "Friday" to PlannedDay("Work & Recovery", emptyList()),
        "Saturday" to PlannedDay("Active Workday", emptyList()),
        "Sunday" to PlannedDay("Active Workday", emptyList())
    )
    fun today(): String = LocalDate.now().dayOfWeek.let { day ->
        when(day) {
            DayOfWeek.MONDAY -> "Monday"; DayOfWeek.TUESDAY -> "Tuesday";
            DayOfWeek.WEDNESDAY -> "Wednesday"; DayOfWeek.THURSDAY -> "Thursday";
            DayOfWeek.FRIDAY -> "Friday"; DayOfWeek.SATURDAY -> "Saturday";
            DayOfWeek.SUNDAY -> "Sunday"
        }
    }
    fun phase(date: LocalDate = LocalDate.now()): String {
        val start = LocalDate.of(2026, 10, 26)
        val week = (java.time.temporal.ChronoUnit.DAYS.between(start, date).coerceAtLeast(0) / 7 + 1).toInt()
        return when(week) {
            in 1..4 -> "Reactivation"
            in 5..16 -> "Foundation"
            in 17..28 -> "Strength & Skill"
            in 29..40 -> "Athletic Development"
            else -> "Consolidation"
        }
    }
}

object RecoveryAdvice {
    fun suggest(hours: Double, fatigue: Int, steps: Int, pain: Boolean): String = when {
        pain -> "Pause painful movements and assess symptoms. This is not medical clearance."
        hours < 5 || fatigue >= 9 -> "Recovery day or light technique only. Avoid maximal efforts."
        hours < 6 || fatigue >= 7 || steps >= 20000 -> "Use a reduced workout: fewer sets and lower intensity."
        else -> "Planned session is reasonable if warm-ups feel normal."
    }
}
