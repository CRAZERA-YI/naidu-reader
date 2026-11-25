package com.lingoreader.app

import com.lingoreader.app.data.model.DailyStudyStat
import com.lingoreader.app.data.model.ReviewRating
import com.lingoreader.app.data.model.VocabWord
import com.lingoreader.app.data.sm2.Sm2Scheduler
import com.lingoreader.app.data.sm2.StudyStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class Sm2SchedulerTest {

    @Test
    fun goodProgressionFollowsSm2Intervals() {
        val now = 1_000_000L
        var w = VocabWord(word = "serendipity", nextReviewAt = now)

        w = Sm2Scheduler.schedule(w, ReviewRating.GOOD, now)
        assertEquals(1, w.repetitions)
        assertEquals(1, w.intervalDays)

        w = Sm2Scheduler.schedule(w, ReviewRating.GOOD, now)
        assertEquals(2, w.repetitions)
        assertEquals(6, w.intervalDays)

        w = Sm2Scheduler.schedule(w, ReviewRating.GOOD, now)
        assertEquals(3, w.repetitions)
        assertTrue(w.intervalDays >= 14)
        assertTrue(w.isMastered)
    }

    @Test
    fun againResetsRepetitionsAndIncrementsLapseCount() {
        val now = 2_000_000L
        val learned = VocabWord(word = "ephemeral", repetitions = 3, intervalDays = 18, easeFactor = 2.5, lapseCount = 1)
        val reset = Sm2Scheduler.schedule(learned, ReviewRating.AGAIN, now)

        assertEquals(0, reset.repetitions)
        assertEquals(0, reset.intervalDays)
        assertEquals(2, reset.lapseCount)
        assertTrue(reset.isStubborn)
        assertEquals(now + 10L * 60L * 1000L, reset.nextReviewAt)
        assertEquals(2.3, reset.easeFactor, 0.001)
    }

    @Test
    fun easeFactorNeverDropsBelowFloor() {
        var w = VocabWord(word = "abstruse", easeFactor = 1.4)
        repeat(5) {
            w = Sm2Scheduler.schedule(w, ReviewRating.AGAIN, 0L)
        }
        assertEquals(1.3, w.easeFactor, 0.0001)
    }

    @Test
    fun studyStatsCalculatorComputesStreakHeatmapAndForecast() {
        val today = LocalDate.of(2026, 11, 25)
        val zone = ZoneOffset.UTC
        val nowMillis = today.atStartOfDay(zone).toInstant().toEpochMilli() + 3600_000L
        val dayMs = 24L * 60L * 60L * 1000L

        val words = listOf(
            VocabWord(id = 1, word = "alpha", repetitions = 0, nextReviewAt = nowMillis - 1000L),
            VocabWord(id = 2, word = "beta", repetitions = 1, intervalDays = 1, lapseCount = 2, nextReviewAt = nowMillis + dayMs),
            VocabWord(id = 3, word = "gamma", repetitions = 3, intervalDays = 20, nextReviewAt = nowMillis + 20 * dayMs)
        )
        val logs = listOf(
            DailyStudyStat("2026-11-25", reviewedCount = 12, rememberedCount = 10, addedCount = 3),
            DailyStudyStat("2026-11-24", reviewedCount = 8, rememberedCount = 7, addedCount = 1),
            DailyStudyStat("2026-11-23", reviewedCount = 5, rememberedCount = 5, addedCount = 0),
            DailyStudyStat("2026-11-21", reviewedCount = 9, rememberedCount = 8, addedCount = 2)
        )

        val stats = StudyStatsCalculator.compute(words, logs, today, nowMillis, zone)
        assertEquals(3, stats.streakDays)
        assertEquals(12, stats.todayReviewed)
        assertEquals(3, stats.todayAdded)
        assertEquals(1, stats.newCount)
        assertEquals(1, stats.learningCount)
        assertEquals(1, stats.masteredCount)
        assertEquals(1, stats.stubbornCount)
        assertEquals(28, stats.heatmap28Days.size)
        assertEquals(7, stats.forecast7Days.size)
        assertEquals(1, stats.forecast7Days[0].dueCount)
        assertEquals(1, stats.forecast7Days[1].dueCount)
    }
}
