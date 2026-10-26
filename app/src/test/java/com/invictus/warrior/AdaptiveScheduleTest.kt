package com.invictus.warrior

import org.junit.Assert.*
import org.junit.Test

class AdaptiveScheduleTest {

    @Test
    fun `no recovery entry yields reduced session`() {
        val rec = AdaptiveSchedule.recommend("Monday", latestRecovery = null)
        assertTrue("Should be reduced when no check-in", rec.isReduced)
        assertTrue(rec.reason.contains("No recovery check-in"))
    }

    @Test
    fun `pain reported yields paused session`() {
        val entry = RecoveryEntry(sleepHours = 8.0, fatigue = 3, steps = 5000, pain = true)
        val rec = AdaptiveSchedule.recommend("Monday", entry)
        assertTrue(rec.isReduced)
        assertTrue(rec.day.exercises.isEmpty())
        assertTrue(rec.reason.contains("Pain"))
    }

    @Test
    fun `severe fatigue yields rest day`() {
        val entry = RecoveryEntry(sleepHours = 4.0, fatigue = 9, steps = 5000, pain = false)
        val rec = AdaptiveSchedule.recommend("Monday", entry)
        assertTrue(rec.isReduced)
        assertTrue(rec.day.exercises.isEmpty())
    }

    @Test
    fun `moderate fatigue yields reduced volume`() {
        val entry = RecoveryEntry(sleepHours = 5.5, fatigue = 7, steps = 5000, pain = false)
        val rec = AdaptiveSchedule.recommend("Monday", entry)
        assertTrue(rec.isReduced)
        // Exercises should still exist but with fewer sets
        assertTrue(rec.day.exercises.isNotEmpty())
        assertTrue(rec.day.title.contains("Reduced"))
    }

    @Test
    fun `good recovery yields full session`() {
        val entry = RecoveryEntry(sleepHours = 8.0, fatigue = 3, steps = 5000, pain = false)
        val rec = AdaptiveSchedule.recommend("Monday", entry)
        assertFalse(rec.isReduced)
        assertEquals("Full session.", rec.reason.substringAfterLast(". ").trim().ifEmpty { rec.reason })
    }

    @Test
    fun `rest day returns non-reduced with empty exercises`() {
        val entry = RecoveryEntry(sleepHours = 8.0, fatigue = 3, steps = 5000, pain = false)
        val rec = AdaptiveSchedule.recommend("Friday", entry)
        assertFalse(rec.isReduced)
        assertTrue(rec.day.exercises.isEmpty())
    }

    @Test
    fun `reduce cuts sets but preserves exercises`() {
        val day = PlannedDay("Test", listOf(
            PlannedExercise("A", 3, "8-10"),
            PlannedExercise("B", 2, "10-12")
        ))
        val reduced = AdaptiveSchedule.reduce(day)
        assertEquals(2, reduced.exercises.size)
        // 3 * 0.6 = 1.8 → 1, 2 * 0.6 = 1.2 → 1
        assertTrue(reduced.exercises.all { it.sets >= 1 })
        assertTrue(reduced.exercises[0].sets < day.exercises[0].sets)
    }

    @Test
    fun `unknown day returns rest recommendation`() {
        val rec = AdaptiveSchedule.recommend("NotADay", null)
        assertFalse(rec.isReduced)
        assertTrue(rec.day.exercises.isEmpty())
    }
}
