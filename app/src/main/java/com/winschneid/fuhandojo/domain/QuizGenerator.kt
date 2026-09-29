package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Course
import com.winschneid.fuhandojo.domain.model.Hand
import com.winschneid.fuhandojo.domain.model.Limit
import com.winschneid.fuhandojo.domain.model.Payment
import com.winschneid.fuhandojo.domain.model.Question
import com.winschneid.fuhandojo.domain.model.QuizLevel
import com.winschneid.fuhandojo.domain.model.Seat
import com.winschneid.fuhandojo.domain.model.WinMethod
import kotlin.random.Random

private const val CHOICE_COUNT = 4

/** 出題の元になる項目。選択肢や解説を付ける前の状態 */
private sealed interface QuizItem {
    data class LimitName(val han: Int) : QuizItem
    data class Points(val hand: Hand, val showFu: Boolean) : QuizItem
}

/**
 * 級ごとの問題を作る。同じ random（シード）からは同じ問題が作られるので、
 * シードを保存しておけば途中の問題を復元できる。
 */
object QuizGenerator {

    fun generate(
        level: QuizLevel,
        random: Random = Random.Default,
        count: Int = QuizLevel.QUESTION_COUNT,
    ): List<Question> {
        if (level.course == Course.FU) return FuQuizGenerator.generate(level, random, count)
        val picks = if (level == QuizLevel.MIXED) {
            // 範囲の広い級に偏らないよう、まず級を均等に選んでからその級の問題を選ぶ
            sampleRounds(MIXED_SOURCES, count, random) { 1 }
                .map { from -> sampleRounds(itemsOf(from), 1, random, ::weightOf).single() to itemsOf(from) }
        } else {
            val items = itemsOf(level)
            sampleRounds(items, count, random, ::weightOf).map { it to items }
        }
        return picks.map { (item, pool) ->
            when (item) {
                is QuizItem.LimitName -> limitNameQuestion(item.han, random)
                is QuizItem.Points -> pointsQuestion(item, pool, random)
            }
        }
    }

    /**
     * 重み付きで重複なしに選び、使い切ったら次の周に入る。
     * 周の変わり目でも直前と同じものは続けて選ばない。
     */
    private fun <T> sampleRounds(items: List<T>, count: Int, random: Random, weight: (T) -> Int): List<T> {
        val result = mutableListOf<T>()
        var remaining = items.toMutableList()
        while (result.size < count) {
            if (remaining.isEmpty()) remaining = items.toMutableList()
            val candidates = remaining.filter { it != result.lastOrNull() }.ifEmpty { remaining }
            var r = random.nextInt(candidates.sumOf(weight))
            val picked = candidates.first { r -= weight(it); r < 0 }
            remaining.remove(picked)
            result += picked
        }
        return result
    }

    /** 実戦でよく出る20〜40符は、70符以上などのまれな符より2倍出やすくする */
    private fun weightOf(item: QuizItem): Int =
        if (item is QuizItem.Points && item.showFu && item.hand.fu > 40) 1 else 2

    private fun limitNameQuestion(han: Int, random: Random): Question.LimitName {
        val answer = Limit.ofHan(han)!!
        val choices = (listOf(answer) + (Limit.entries - answer).shuffled(random).take(CHOICE_COUNT - 1))
            .sortedBy { it.ordinal }
        return Question.LimitName(
            han = han,
            choices = choices.map { it.label },
            answerIndex = choices.indexOf(answer),
            explanation = Explainer.explainLimitName(han),
        )
    }

    private fun pointsQuestion(item: QuizItem.Points, pool: List<QuizItem>, random: Random): Question.Points {
        val hand = item.hand
        val fallback = pool.filterIsInstance<QuizItem.Points>().map { it.hand }
        val (choices, answerIndex) = pointsChoices(hand, item.showFu, fallback, random)
        return Question.Points(
            hand = hand,
            showFu = item.showFu,
            choices = choices,
            answerIndex = answerIndex,
            explanation = Explainer.explain(hand),
        )
    }

    /**
     * 点数を答える問題の選択肢。fallback は近い点数だけで足りないときに使う候補（書式が同じものだけ使う）
     */
    internal fun pointsChoices(hand: Hand, showFu: Boolean, fallback: List<Hand>, random: Random): Pair<List<String>, Int> {
        val answer = ScoreCalculator.payment(hand)
        val sameFormat = { other: Hand -> other.seat == hand.seat && other.method == hand.method }

        // 満貫の正解がいつも最小の選択肢にならないよう、満貫に届かない30符4翻（子ロンなら7700）を必ず混ぜる
        val belowMangan = if (!showFu && Limit.ofHan(hand.han) == Limit.MANGAN) {
            listOf(ScoreCalculator.payment(hand.copy(fu = 30, han = 4)))
        } else {
            emptyList()
        }
        // 間違えやすい近くの点数（翻・符がひとつ違い、親子の取り違え）を優先して選択肢にする
        val near = neighborsOf(hand, showFu).filter(ScoreCalculator::isValid).map(ScoreCalculator::payment)
        val sameFormatFallback = fallback.filter(sameFormat).map(ScoreCalculator::payment)
        val distractors = (belowMangan + near.shuffled(random) + sameFormatFallback.shuffled(random))
            .distinctBy { it.label }
            .filter { it.label != answer.label }
            .take(CHOICE_COUNT - 1)

        val choices = (distractors + answer).sortedBy { it.total }
        return choices.map { it.label } to choices.indexOf(answer)
    }

    private fun neighborsOf(hand: Hand, showFu: Boolean): List<Hand> {
        // ツモは親と子で書式（オール / 子-親）が違い一目で誤答とわかるので、親子の取り違えはロンだけ
        val otherSeat = listOfNotNull(
            if (hand.method == WinMethod.RON) {
                hand.copy(seat = if (hand.seat == Seat.DEALER) Seat.NON_DEALER else Seat.DEALER)
            } else {
                null
            },
        )
        if (!showFu) {
            return LIMIT_HANS.map { hand.copy(han = it) } + otherSeat
        }
        val fuIndex = ScoreCalculator.FU_VALUES.indexOf(hand.fu)
        val nearFu = listOfNotNull(
            ScoreCalculator.FU_VALUES.getOrNull(fuIndex - 1),
            ScoreCalculator.FU_VALUES.getOrNull(fuIndex + 1),
        )
        return listOf(hand.copy(han = hand.han - 1), hand.copy(han = hand.han + 1)) +
            nearFu.map { hand.copy(fu = it) } +
            otherSeat
    }

    private val LIMIT_HANS = 5..13

    /** 1級（総合）の出題範囲。点数編のほかの級すべて */
    private val MIXED_SOURCES = QuizLevel.entries.filter { it.course == Course.POINTS && it != QuizLevel.MIXED }
    private val SEATS = Seat.entries

    private fun limitHands(seats: List<Seat>, method: WinMethod) =
        seats.flatMap { seat -> LIMIT_HANS.map { han -> Hand(seat, method, 30, han) } }
            .map { QuizItem.Points(it, showFu = false) }

    private fun fuHands(seats: List<Seat>, method: WinMethod, fus: List<Int>) =
        seats.flatMap { seat ->
            fus.flatMap { fu -> (1..4).map { han -> Hand(seat, method, fu, han) } }
        }
            .filter(ScoreCalculator::isValid)
            .map { QuizItem.Points(it, showFu = true) }

    private fun itemsOf(level: QuizLevel): List<QuizItem> = when (level) {
        QuizLevel.LIMIT_NAMES -> LIMIT_HANS.map { QuizItem.LimitName(it) }
        QuizLevel.NON_DEALER_LIMIT_RON -> limitHands(listOf(Seat.NON_DEALER), WinMethod.RON)
        QuizLevel.DEALER_LIMIT_RON -> limitHands(listOf(Seat.DEALER), WinMethod.RON)
        QuizLevel.LIMIT_TSUMO -> limitHands(SEATS, WinMethod.TSUMO)
        QuizLevel.NON_DEALER_RON_30_40 -> fuHands(listOf(Seat.NON_DEALER), WinMethod.RON, listOf(30, 40))
        QuizLevel.DEALER_RON_30_40 -> fuHands(listOf(Seat.DEALER), WinMethod.RON, listOf(30, 40))
        QuizLevel.RON_ALL_FU -> fuHands(SEATS, WinMethod.RON, ScoreCalculator.FU_VALUES)
        QuizLevel.TSUMO_30_40 -> fuHands(SEATS, WinMethod.TSUMO, listOf(30, 40))
        QuizLevel.TSUMO_ALL_FU -> fuHands(SEATS, WinMethod.TSUMO, ScoreCalculator.FU_VALUES)
        QuizLevel.MIXED -> MIXED_SOURCES.flatMap(::itemsOf)
        QuizLevel.MELD_FU, QuizLevel.PAIR_WAIT_FU, QuizLevel.HAND_FU_RON, QuizLevel.HAND_FU_TSUMO, QuizLevel.HAND_POINTS ->
            error("$level は FuQuizGenerator で出題する")
    }
}

/** 正解の求め方を初心者向けに説明する文章を作る */
object Explainer {

    private val LIMIT_GUIDE = listOf(
        "5翻 → 満貫",
        "6〜7翻 → 跳満",
        "8〜10翻 → 倍満",
        "11〜12翻 → 三倍満",
        "13翻以上 → 数え役満（役満と同じ点数）",
    ).joinToString("\n")

    fun explainLimitName(han: Int): String {
        val limit = Limit.ofHan(han)!!
        val name = if (limit == Limit.YAKUMAN) "数え役満（役満と同じ点数）" else limit.label
        return "${han}翻は$name。\n\n$LIMIT_GUIDE"
    }

    fun explain(hand: Hand): String {
        val fu = hand.fu
        val han = hand.han
        val raw = ScoreCalculator.rawBasePoints(fu, han)
        val base = ScoreCalculator.basePoints(fu, han)
        val limitByHan = Limit.ofHan(han)
        val multiplier = 1 shl (han + 2)
        val baseLine = when {
            limitByHan == Limit.YAKUMAN -> "${han}翻は数え役満（基本点 $base）"
            limitByHan != null -> "${han}翻は${limitByHan.label}（基本点 $base）"
            raw >= Limit.MANGAN.basePoints ->
                "基本点 = ${fu}符 × $multiplier（2の${han + 2}乗）= $raw\n→ 2000を超えるので満貫（基本点 2000）"
            else -> "基本点 = ${fu}符 × $multiplier（2の${han + 2}乗）= $raw"
        }
        val paymentLines = when (val payment = ScoreCalculator.payment(hand)) {
            is Payment.Ron -> {
                val times = if (hand.seat == Seat.DEALER) 6 else 4
                listOf("${hand.seat.label}のロン: 基本点 × $times = ${rounded(base * times)}")
            }
            is Payment.NonDealerTsumo -> listOf(
                "子2人がそれぞれ: 基本点 × 1 = ${rounded(base)}",
                "親が: 基本点 × 2 = ${rounded(base * 2)}",
                "→ ${payment.label}（合計 ${payment.total}）",
            )
            is Payment.DealerTsumo -> listOf(
                "子3人がそれぞれ: 基本点 × 2 = ${rounded(base * 2)}",
                "→ ${payment.label}（合計 ${payment.total}）",
            )
        }
        val ronHand = hand.copy(method = WinMethod.RON)
        val ron = ScoreCalculator.payment(ronHand)
        // 20符はロンが存在しないので比べない
        val tsumoNote = if (
            hand.method == WinMethod.TSUMO &&
            ScoreCalculator.isValid(ronHand) &&
            ron.total != ScoreCalculator.payment(hand).total
        ) {
            listOf("※支払う人ごとに100点単位に切り上げるので、ロン（${ron.total}）と合計が少し違います")
        } else {
            emptyList()
        }
        return (listOf(baseLine) + paymentLines + tsumoNote).joinToString("\n")
    }

    private fun rounded(points: Int): String {
        val up = ScoreCalculator.roundUp100(points)
        return if (up == points) "$points" else "$points → 切り上げて $up"
    }
}
