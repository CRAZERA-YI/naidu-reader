package com.lingoreader.app.data.sm2

import com.lingoreader.app.data.model.ReviewRating
import com.lingoreader.app.data.model.VocabWord
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * SM-2 Spaced Repetition Scheduler for vocabulary review.
 */
object Sm2Scheduler {
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val TEN_MIN_MS = 10L * 60L * 1000L

    fun schedule(
        word: VocabWord,
        rating: ReviewRating,
        nowMillis: Long = System.currentTimeMillis()
    ): VocabWord {
        val q = rating.quality
        var ef = word.easeFactor
        var reps = word.repetitions
        var intervalDays = word.intervalDays

        val nextReviewAt: Long

        if (rating == ReviewRating.AGAIN) {
            reps = 0
            intervalDays = 0
            ef = max(1.3, ef - 0.2)
            nextReviewAt = nowMillis + TEN_MIN_MS
        } else {
            // Update ease factor via SM-2 formula
            val delta = 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)
            ef = max(1.3, ef + delta)

            intervalDays = when (rating) {
                ReviewRating.HARD -> {
                    when {
                        reps == 0 -> 1
                        reps == 1 -> 2
                        else -> max(1, (intervalDays * 1.2).roundToInt())
                    }
                }
                ReviewRating.GOOD -> {
                    when (reps) {
                        0 -> 1
                        1 -> 6
                        else -> max(intervalDays + 1, (intervalDays * ef).roundToInt())
                    }
                }
                ReviewRating.EASY -> {
                    when (reps) {
                        0 -> 4
                        1 -> 10
                        else -> max(intervalDays + 2, (intervalDays * ef * 1.3).roundToInt())
                    }
                }
                ReviewRating.AGAIN -> 0
            }
            reps += 1
            nextReviewAt = nowMillis + intervalDays * DAY_MS
        }

        return word.copy(
            repetitions = reps,
            intervalDays = intervalDays,
            easeFactor = ef,
            nextReviewAt = nextReviewAt,
            reviewCount = word.reviewCount + 1,
            lapseCount = if (rating == ReviewRating.AGAIN) word.lapseCount + 1 else word.lapseCount,
            updatedAt = nowMillis
        )
    }

    fun previewIntervalLabel(word: VocabWord, rating: ReviewRating): String {
        if (rating == ReviewRating.AGAIN) return "10分钟"
        val simulated = schedule(word, rating, 0L)
        val days = simulated.intervalDays
        return when {
            days <= 1 -> "1天"
            days < 30 -> "${days}天"
            days < 365 -> "${(days / 30.0).roundToInt()}个月"
            else -> "${(days / 365.0).roundToInt()}年"
        }
    }
}
