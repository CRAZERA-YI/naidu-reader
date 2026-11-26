package com.lingoreader.app.data.sm2

import androidx.compose.runtime.Immutable
import com.lingoreader.app.data.model.DailyStudyStat
import com.lingoreader.app.data.model.VocabWord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Immutable
data class HeatmapDay(
    val dateKey: String,
    val dayOfMonth: Int,
    val reviewedCount: Int,
    val addedCount: Int
) {
    val totalActivity: Int get() = reviewedCount + addedCount
}

@Immutable
data class ForecastDay(
    val label: String,
    val dueCount: Int
)

@Immutable
data class StudyAnalytics(
    val todayReviewed: Int,
    val todayRemembered: Int,
    val todayAdded: Int,
    val streakDays: Int,
    val totalReviewedAllTime: Int,
    val newCount: Int,
    val learningCount: Int,
    val familiarCount: Int,
    val masteredCount: Int,
    val stubbornCount: Int,
    val heatmap28Days: List<HeatmapDay>,
    val forecast7Days: List<ForecastDay>
)

object StudyStatsCalculator {

    private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE

    fun todayKey(zoneId: ZoneId = ZoneId.systemDefault()): String {
        return LocalDate.now(zoneId).format(DATE_FMT)
    }

    fun compute(
        words: List<VocabWord>,
        logs: List<DailyStudyStat>,
        today: LocalDate = LocalDate.now(),
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): StudyAnalytics {
        val logMap = logs.associateBy { it.dateKey }
        val todayStr = today.format(DATE_FMT)
        val todayStat = logMap[todayStr]

        // Compute consecutive study streak (includes today if active, or up to yesterday if not reviewed today yet)
        var streak = 0
        var cursor = if ((todayStat?.reviewedCount ?: 0) > 0 || (todayStat?.addedCount ?: 0) > 0) {
            today
        } else {
            today.minusDays(1)
        }
        while (true) {
            val s = logMap[cursor.format(DATE_FMT)]
            if (s != null && (s.reviewedCount > 0 || s.addedCount > 0)) {
                streak++
                cursor = cursor.minusDays(1)
            } else {
                break
            }
        }

        // Last 28 days heatmap (oldest -> today)
        val heatmap = (27 downTo 0).map { offset ->
            val d = today.minusDays(offset.toLong())
            val key = d.format(DATE_FMT)
            val stat = logMap[key]
            HeatmapDay(
                dateKey = key,
                dayOfMonth = d.dayOfMonth,
                reviewedCount = stat?.reviewedCount ?: 0,
                addedCount = stat?.addedCount ?: 0
            )
        }

        // Next 7 days review forecast
        val endOfTodayMillis = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
        val forecast = (0..6).map { offset ->
            val label = when (offset) {
                0 -> "今天"
                1 -> "明天"
                else -> "+${offset}天"
            }
            val count = if (offset == 0) {
                words.count { it.nextReviewAt <= maxOf(nowMillis, endOfTodayMillis) }
            } else {
                val start = today.plusDays(offset.toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val end = today.plusDays((offset + 1).toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli()
                words.count { it.nextReviewAt in start until end }
            }
            ForecastDay(label = label, dueCount = count)
        }

        var newCnt = 0
        var learnCnt = 0
        var famCnt = 0
        var mastCnt = 0
        var stubbornCnt = 0
        for (w in words) {
            when (w.masteryLevel) {
                0 -> newCnt++
                1 -> learnCnt++
                2 -> famCnt++
                else -> mastCnt++
            }
            if (w.isStubborn) stubbornCnt++
        }

        return StudyAnalytics(
            todayReviewed = todayStat?.reviewedCount ?: 0,
            todayRemembered = todayStat?.rememberedCount ?: 0,
            todayAdded = todayStat?.addedCount ?: 0,
            streakDays = streak,
            totalReviewedAllTime = logs.sumOf { it.reviewedCount },
            newCount = newCnt,
            learningCount = learnCnt,
            familiarCount = famCnt,
            masteredCount = mastCnt,
            stubbornCount = stubbornCnt,
            heatmap28Days = heatmap,
            forecast7Days = forecast
        )
    }

    fun formatNextReviewCountdown(nextReviewAt: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diff = nextReviewAt - nowMillis
        if (diff <= 0L) return "现在待复习"
        val minutes = diff / (60L * 1000L)
        val hours = diff / (60L * 60L * 1000L)
        val days = diff / (24L * 60L * 60L * 1000L)
        return when {
            minutes < 60 -> "${minutes.coerceAtLeast(1)} 分钟后复习"
            hours < 24 -> "${hours} 小时后复习"
            days < 30 -> "${days} 天后复习"
            else -> {
                val d = Instant.ofEpochMilli(nextReviewAt).atZone(ZoneId.systemDefault()).toLocalDate()
                "${d.monthValue}月${d.dayOfMonth}日 复习"
            }
        }
    }
}
