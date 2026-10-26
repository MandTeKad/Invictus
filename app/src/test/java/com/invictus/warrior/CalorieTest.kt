package com.invictus.warrior

import org.junit.Assert.*
import org.junit.Test

class CalorieTest {

    @Test
    fun `zero steps produces zero calories`() {
        val result = ActivityCalories.estimate(0, 183.0, 287.0, 0.8)
        assertEquals(0, result)
    }

    @Test
    fun `positive steps produce positive calories`() {
        val result = ActivityCalories.estimate(5000, 183.0, 287.0, 0.8)
        assertTrue("Expected positive calories, got $result", result > 0)
    }

    @Test
    fun `higher factor produces more calories`() {
        val low = ActivityCalories.estimate(10000, 183.0, 287.0, 0.6)
        val high = ActivityCalories.estimate(10000, 183.0, 287.0, 1.2)
        assertTrue("High factor ($high) should exceed low factor ($low)", high > low)
    }

    @Test
    fun `heavier person burns more calories`() {
        val light = ActivityCalories.estimate(10000, 183.0, 180.0, 0.8)
        val heavy = ActivityCalories.estimate(10000, 183.0, 300.0, 0.8)
        assertTrue("Heavier ($heavy) should exceed lighter ($light)", heavy > light)
    }

    @Test
    fun `negative steps clamped to zero`() {
        val result = ActivityCalories.estimate(-100, 183.0, 287.0, 0.8)
        assertEquals(0, result)
    }

    @Test
    fun `distance estimate is reasonable`() {
        // 10000 steps at 183cm height ≈ 7.6 km
        val dist = ActivityCalories.distanceKm(10000, 183.0)
        assertTrue("Distance $dist should be between 5 and 10 km", dist in 5.0..10.0)
    }

    @Test
    fun `resting calories in plausible range`() {
        val resting = ActivityCalories.restingCalories(287.0, 183.0)
        // For a large male, resting should be ~2000-2800 kcal
        assertTrue("Resting $resting should be between 1500 and 3500", resting in 1500..3500)
    }
}
