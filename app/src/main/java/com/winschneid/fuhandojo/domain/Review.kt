package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Question
import com.winschneid.fuhandojo.domain.model.QuizLevel
import kotlinx.serialization.Serializable

/**
 * 復習リストの1問。
 * @param streak 復習で続けて正解した回数。REVIEW_STREAK_TO_CLEAR に届くとリストから外れる
 * @param addedAt 最後に間違えた時刻（古いものから復習する）
 * @param lastCorrectAt 最後に連続正解として数えた時刻。間違えると null に戻る
 */
@Serializable
data class ReviewItem(
    val level: QuizLevel,
    val question: Question,
    val streak: Int = 0,
    val addedAt: Long,
    val lastCorrectAt: Long? = null,
) {
    val key: String get() = question.reviewKey()

    /** 前回の正解から時間がたっておらず、今正解しても連続正解として数えない */
    fun isCoolingDown(now: Long): Boolean =
        lastCorrectAt != null && now - lastCorrectAt < ReviewList.COOLDOWN_MILLIS
}

/** 復習で答えた1問の結果 */
data class ReviewAnswer(val key: String, val correct: Boolean)

/**
 * 復習を終えたあとの集計。
 * @param almostCleared あと1回の正解で覚えた問題になるもの
 */
data class ReviewSummary(val cleared: Int, val remaining: Int, val almostCleared: Int = 0)

object ReviewList {
    /** 1回の正解はまぐれもあるので、続けて2回正解したら覚えたことにする */
    const val REVIEW_STREAK_TO_CLEAR = 2

    /**
     * 連続正解として数えるのに必要な、前回の正解からの間隔。
     * 直後にもう一度正解しても覚えたとは言えないので、時間をおいて思い出せたときだけ数える。
     */
    const val COOLDOWN_HOURS = 12
    const val COOLDOWN_MILLIS = COOLDOWN_HOURS * 60 * 60 * 1000L

    /** 1回の復習で出す問題数 */
    const val SESSION_SIZE = 10

    /** 保存しておく上限。超えたら古いものから消す */
    const val MAX_ITEMS = 200

    /** 間違えた問題を加える。同じ問題がすでにあれば、連続正解を0に戻して最後に間違えた時刻を更新する */
    fun afterMistake(items: List<ReviewItem>, level: QuizLevel, question: Question, now: Long): List<ReviewItem> {
        val key = question.reviewKey()
        val existing = items.find { it.key == key }
        val updated = if (existing != null) {
            items.map { if (it.key == key) it.copy(streak = 0, addedAt = now, lastCorrectAt = null) else it }
        } else {
            items + ReviewItem(level, question, streak = 0, addedAt = now)
        }
        return updated.sortedBy { it.addedAt }.takeLast(MAX_ITEMS)
    }

    /**
     * 復習の結果を反映する。
     * 正解なら連続正解を増やし（前回の正解から時間がたっていなければ増やさない）、届いたら外す。不正解なら0に戻す。
     */
    fun afterReview(
        items: List<ReviewItem>,
        answers: List<ReviewAnswer>,
        now: Long,
    ): Pair<List<ReviewItem>, ReviewSummary> {
        val results = answers.associate { it.key to it.correct }
        var cleared = 0
        val updated = items.mapNotNull { item ->
            when (results[item.key]) {
                null -> item
                false -> item.copy(streak = 0, lastCorrectAt = null)
                true -> when {
                    item.isCoolingDown(now) -> item
                    item.streak + 1 >= REVIEW_STREAK_TO_CLEAR -> {
                        cleared++
                        null
                    }
                    else -> item.copy(streak = item.streak + 1, lastCorrectAt = now)
                }
            }
        }
        val almost = updated.count { it.streak == REVIEW_STREAK_TO_CLEAR - 1 }
        return updated to ReviewSummary(cleared = cleared, remaining = updated.size, almostCleared = almost)
    }

    /**
     * 次に復習する問題。時間をおいて数えられるものを先に、その中ではまだ正解していないもの、古いものから出す。
     */
    fun nextSession(items: List<ReviewItem>, now: Long, size: Int = SESSION_SIZE): List<ReviewItem> =
        items.sortedWith(compareBy<ReviewItem>({ it.isCoolingDown(now) }, { it.streak }, { it.addedAt })).take(size)
}

/**
 * 同じ問題かどうかを見分けるキー。選択肢の並びや解説の文面ではなく、問われている中身で決める。
 */
fun Question.reviewKey(): String = when (this) {
    is Question.LimitName -> "LimitName:$han"
    is Question.Points -> "Points:$hand:$showFu"
    is Question.MeldFu -> "MeldFu:$meld"
    is Question.PairFu -> "PairFu:$pair:$roundWind:$seatWind"
    is Question.WaitFu -> "WaitFu:$shape:$winningTile"
    is Question.FuSum -> "FuSum:$hand"
    is Question.HandFu -> "HandFu:$hand:$han:${groups != null}"
}
