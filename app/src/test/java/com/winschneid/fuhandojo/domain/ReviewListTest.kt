package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Question
import com.winschneid.fuhandojo.domain.model.QuizLevel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReviewListTest {

    private fun question(han: Int) = Question.LimitName(han, listOf("満貫", "跳満", "倍満", "役満"), 0, "解説")

    @Test
    fun `間違えた問題を加え、同じ問題は連続正解を0に戻して1つにまとめる`() {
        var items = ReviewList.afterMistake(emptyList(), QuizLevel.LIMIT_NAMES, question(5), now = 1)
        items = ReviewList.afterMistake(items, QuizLevel.LIMIT_NAMES, question(6), now = 2)
        items = items.map { it.copy(streak = 1) }
        // 選択肢の並びが違っても、同じ翻数を問う問題は同じ問題として扱う
        val same = question(5).copy(choices = listOf("役満", "倍満", "跳満", "満貫"), answerIndex = 3)
        items = ReviewList.afterMistake(items, QuizLevel.LIMIT_NAMES, same, now = 3)
        assertEquals(2, items.size)
        val five = items.single { it.question.reviewKey() == question(5).reviewKey() }
        assertEquals(0, five.streak)
        assertEquals(3L, five.addedAt)
    }

    @Test
    fun `時間をおいて2回続けて正解すると外れ、間違えると0に戻る`() {
        val hour = 60 * 60 * 1000L
        val start = ReviewList.afterMistake(emptyList(), QuizLevel.LIMIT_NAMES, question(5), now = 0)
        val key = start.single().key
        val correct = listOf(ReviewAnswer(key, correct = true))

        val (once, summary1) = ReviewList.afterReview(start, correct, now = 1 * hour)
        assertEquals(1, once.single().streak)
        assertEquals(ReviewSummary(cleared = 0, remaining = 1, almostCleared = 1), summary1)

        // 直後にもう一度正解しても、時間をおいていないので数えない
        val (soon, summary2) = ReviewList.afterReview(once, correct, now = 2 * hour)
        assertEquals(1, soon.single().streak)
        assertEquals(ReviewSummary(cleared = 0, remaining = 1, almostCleared = 1), summary2)

        // 間違えると0に戻り、時間の制限もなくなる
        val (missed, _) = ReviewList.afterReview(soon, listOf(ReviewAnswer(key, correct = false)), now = 3 * hour)
        assertEquals(0, missed.single().streak)
        val (again, _) = ReviewList.afterReview(missed, correct, now = 4 * hour)
        assertEquals(1, again.single().streak)

        // 前回の正解から12時間以上あけて正解すると覚えた問題になる
        val later = 4 * hour + ReviewList.COOLDOWN_MILLIS
        val (cleared, summary3) = ReviewList.afterReview(again, correct, now = later)
        assertTrue(cleared.isEmpty())
        assertEquals(ReviewSummary(cleared = 1, remaining = 0, almostCleared = 0), summary3)
    }

    @Test
    fun `時間をおいて数えられる問題、まだ正解していない問題、古い問題の順に出す`() {
        val now = 100 * 60 * 60 * 1000L
        val items = listOf(
            ReviewItem(QuizLevel.LIMIT_NAMES, question(5), streak = 1, addedAt = 1, lastCorrectAt = 0),
            ReviewItem(QuizLevel.LIMIT_NAMES, question(6), streak = 0, addedAt = 3),
            ReviewItem(QuizLevel.LIMIT_NAMES, question(7), streak = 0, addedAt = 2),
            // 正解したばかりなので最後
            ReviewItem(QuizLevel.LIMIT_NAMES, question(8), streak = 1, addedAt = 0, lastCorrectAt = now - 1),
        )
        assertEquals(listOf(7, 6, 5, 8), ReviewList.nextSession(items, now).map { (it.question as Question.LimitName).han })
        assertEquals(2, ReviewList.nextSession(items, now, size = 2).size)
    }

    @Test
    fun `上限を超えたら古いものから消す`() {
        var items = emptyList<ReviewItem>()
        repeat(ReviewList.MAX_ITEMS + 5) { i ->
            val q = Question.Points(
                hand = com.winschneid.fuhandojo.domain.model.Hand(
                    com.winschneid.fuhandojo.domain.model.Seat.NON_DEALER,
                    com.winschneid.fuhandojo.domain.model.WinMethod.RON,
                    fu = 30,
                    han = 1 + i,
                ),
                showFu = true,
                choices = listOf("a", "b", "c", "d"),
                answerIndex = 0,
                explanation = "",
            )
            items = ReviewList.afterMistake(items, QuizLevel.RON_ALL_FU, q, now = i.toLong())
        }
        assertEquals(ReviewList.MAX_ITEMS, items.size)
        assertEquals(5L, items.minOf { it.addedAt })
    }

    @Test
    fun `どの級・段の問題も保存して読み戻すと同じになる`() {
        val json = Json { ignoreUnknownKeys = true }
        QuizLevel.entries.forEach { level ->
            val items = QuizGenerator.generate(level, Random(11)).mapIndexed { i, q ->
                ReviewItem(level, q, streak = i % 2, addedAt = i.toLong())
            }
            val restored = json.decodeFromString<List<ReviewItem>>(json.encodeToString(items))
            assertEquals("$level", items, restored)
        }
    }
}
