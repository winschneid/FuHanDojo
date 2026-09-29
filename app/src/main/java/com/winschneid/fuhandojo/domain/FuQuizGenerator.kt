package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Hand
import com.winschneid.fuhandojo.domain.model.Meld
import com.winschneid.fuhandojo.domain.model.MeldKind
import com.winschneid.fuhandojo.domain.model.Question
import com.winschneid.fuhandojo.domain.model.QuizLevel
import com.winschneid.fuhandojo.domain.model.Suit
import com.winschneid.fuhandojo.domain.model.Tile
import com.winschneid.fuhandojo.domain.model.TileGroupInfo
import com.winschneid.fuhandojo.domain.model.Wait
import com.winschneid.fuhandojo.domain.model.WinMethod
import com.winschneid.fuhandojo.domain.model.WinningHand
import com.winschneid.fuhandojo.domain.model.Wind
import kotlin.random.Random

/** 符計算編（段）の問題を作る */
object FuQuizGenerator {

    private val MELD_FU_VALUES = listOf(0, 2, 4, 8, 16, 32)
    private val NUMBER_SUITS = listOf(Suit.MAN, Suit.PIN, Suit.SOU)

    private const val MELD_GUIDE = "刻子: 明刻 2符 / 暗刻 4符\n槓子: 明槓 8符 / 暗槓 16符\nヤオ九牌（1・9・字牌）はそれぞれ2倍。順子は0符"
    private const val PAIR_GUIDE = "雀頭の符: 三元牌（白・發・中）、自風、場風は2符。それ以外は0符"
    private const val WAIT_GUIDE = "待ちの符: 嵌張・辺張・単騎は2符。両面・双碰は0符"
    private const val PINFU_SHAPE = "平和の形（門前で順子だけ、役牌でない雀頭、両面待ち）"

    fun generate(level: QuizLevel, random: Random, count: Int): List<Question> = List(count) {
        when (level) {
            QuizLevel.MELD_FU -> meldQuestion(random)
            QuizLevel.PAIR_WAIT_FU -> if (random.nextBoolean()) pairQuestion(random) else waitQuestion(random)
            QuizLevel.FU_SUM -> fuSumQuestion(randomHand(random, null, allowChiitoitsu = true), random)
            QuizLevel.SPLIT_HAND_FU ->
                handFuQuestion(randomHand(random, null, allowChiitoitsu = true), random, split = true)
            QuizLevel.HAND_FU_RON -> handFuQuestion(randomHand(random, WinMethod.RON, allowChiitoitsu = false), random)
            QuizLevel.HAND_FU_TSUMO -> handFuQuestion(randomHand(random, WinMethod.TSUMO, allowChiitoitsu = true), random)
            QuizLevel.HAND_POINTS -> handPointsQuestion(randomHand(random, null, allowChiitoitsu = true), random)
            else -> error("$level は点数編の級です")
        }
    }

    // ---- 初段: 面子の符 ----

    private fun meldQuestion(random: Random): Question.MeldFu {
        // 順子（0符）ばかりにならないよう、刻子・槓子を多めに出す
        val kind = when (random.nextInt(7)) {
            0 -> MeldKind.SEQUENCE
            in 1..3 -> MeldKind.TRIPLET
            else -> MeldKind.QUAD
        }
        val open = random.nextBoolean()
        val tile = if (kind == MeldKind.SEQUENCE) {
            Tile(NUMBER_SUITS.random(random), random.nextInt(1, 8))
        } else {
            when (random.nextInt(3)) {
                0 -> Tile(NUMBER_SUITS.random(random), random.nextInt(2, 9))
                1 -> Tile(NUMBER_SUITS.random(random), listOf(1, 9).random(random))
                else -> Tile(Suit.HONOR, random.nextInt(1, 8))
            }
        }
        val meld = Meld(kind, tile, open)
        val (choices, answerIndex) = fuChoices(meld.fu, MELD_FU_VALUES, random)
        return Question.MeldFu(meld, choices, answerIndex, explainMeld(meld) + "\n\n" + MELD_GUIDE)
    }

    private fun explainMeld(meld: Meld): String {
        if (meld.kind == MeldKind.SEQUENCE) return "順子は鳴いても鳴かなくても0符。"
        val base = meld.fu / if (meld.tile.isTerminalOrHonor) 2 else 1
        val baseText = "${meld.label}は${base}符。"
        return if (meld.tile.isTerminalOrHonor) {
            baseText + "${meld.tile.label}はヤオ九牌なので2倍で${meld.fu}符。"
        } else {
            baseText + "${meld.tile.label}は2〜8の数牌（中張牌）なのでそのまま。"
        }
    }

    // ---- 二段: 雀頭と待ちの符 ----

    private fun pairQuestion(random: Random): Question.PairFu {
        while (true) {
            val pair = when (random.nextInt(5)) {
                0, 1 -> Tile(Suit.HONOR, random.nextInt(1, 5))
                2, 3 -> Tile(Suit.HONOR, random.nextInt(5, 8))
                else -> Tile(NUMBER_SUITS.random(random), random.nextInt(1, 10))
            }
            val roundWind = listOf(Wind.EAST, Wind.SOUTH).random(random)
            val seatWind = Wind.entries.random(random)
            // 連風牌（場風かつ自風）の雀頭は2符か4符かがルールで分かれるので出さない
            if (pair.wind != null && pair.wind == roundWind && pair.wind == seatWind) continue
            val fu = FuCalculator.pairFu(pair, roundWind, seatWind)
            val choices = listOf("0符", "2符", "4符")
            val reason = when {
                pair.isDragon -> "三元牌なので2符。"
                pair.wind == seatWind -> "自風（${seatWind.label}家）なので2符。"
                pair.wind == roundWind -> "場風（${roundWind.label}場）なので2符。"
                pair.isHonor -> "場風でも自風でもない風牌なので0符。"
                else -> "数牌なので0符。"
            }
            return Question.PairFu(
                pair = pair,
                roundWind = roundWind,
                seatWind = seatWind,
                choices = choices,
                answerIndex = choices.indexOf("${fu}符"),
                explanation = "${pair.label}の雀頭は$reason\n\n$PAIR_GUIDE",
            )
        }
    }

    private fun waitQuestion(random: Random): Question.WaitFu {
        val wait = Wait.entries.random(random)
        val suit = NUMBER_SUITS.random(random)
        val (shape, winningTile) = when (wait) {
            Wait.RYANMEN -> {
                val a = random.nextInt(2, 8)
                listOf(Tile(suit, a), Tile(suit, a + 1)) to Tile(suit, if (random.nextBoolean()) a - 1 else a + 2)
            }
            Wait.KANCHAN -> {
                val a = random.nextInt(1, 8)
                listOf(Tile(suit, a), Tile(suit, a + 2)) to Tile(suit, a + 1)
            }
            Wait.PENCHAN -> if (random.nextBoolean()) {
                listOf(Tile(suit, 1), Tile(suit, 2)) to Tile(suit, 3)
            } else {
                listOf(Tile(suit, 8), Tile(suit, 9)) to Tile(suit, 7)
            }
            Wait.TANKI -> {
                val tile = Tile.ALL.random(random)
                listOf(tile) to tile
            }
            Wait.SHANPON -> {
                val (x, y) = Tile.ALL.shuffled(random).take(2)
                listOf(x, x, y, y).sorted() to x
            }
        }
        val options = (listOf(wait) + (Wait.entries - wait).shuffled(random).take(3)).sortedBy { it.ordinal }
        val description = when (wait) {
            Wait.RYANMEN -> "2枚並びの両側のどちらかを待つ形"
            Wait.KANCHAN -> "間の1枚を待つ形"
            Wait.PENCHAN -> "12で3、89で7だけを待つ形"
            Wait.TANKI -> "雀頭になる1枚を待つ形"
            Wait.SHANPON -> "対子2組のどちらかが刻子になるのを待つ形"
        }
        return Question.WaitFu(
            shape = shape,
            winningTile = winningTile,
            choices = options.map { waitChoice(it) },
            answerIndex = options.indexOf(wait),
            explanation = "${wait.label}待ち（$description）は${wait.fu}符。\n\n$WAIT_GUIDE",
        )
    }

    private fun waitChoice(wait: Wait) = "${wait.label}・${wait.fu}符"

    // ---- 三段: 符の足し算と例外 ----

    private fun fuSumQuestion(hand: WinningHand, random: Random): Question.FuSum {
        val result = FuCalculator.calculate(hand)!!
        val method = if (hand.method == WinMethod.RON) "ロン" else "ツモ"
        val conditions = buildList {
            add("${hand.roundWind.label}場・${hand.seatWind.label}家")
            add(if (hand.isClosed) "門前で$method" else "鳴いて$method")
            if (result.isChiitoitsu) {
                add("七対子")
            } else {
                result.melds.forEach { add(meldCondition(it, hand)) }
                add("雀頭 ${result.pair!!.label}")
                add("${result.wait.label}待ち")
            }
        }
        val (choices, answerIndex) = fuChoices(result.fu, ScoreCalculator.FU_VALUES, random)
        return Question.FuSum(hand, conditions, choices, answerIndex, explainHand(result))
    }

    /** 面子を牌の絵なしで説明する（例: 暗刻 中 / チー 456筒 / ロンで完成した刻子 7索） */
    private fun meldCondition(meld: Meld, hand: WinningHand): String {
        val tile = meld.tile
        return when (meld.kind) {
            MeldKind.SEQUENCE ->
                "${meld.label} ${tile.number}${tile.number + 1}${tile.number + 2}${tile.suit.label}"
            MeldKind.TRIPLET -> when {
                !meld.open -> "暗刻 ${tile.label}"
                meld in hand.calledMelds -> "ポン ${tile.label}"
                else -> "ロンで完成した刻子 ${tile.label}"
            }
            MeldKind.QUAD -> "${meld.label} ${tile.label}"
        }
    }

    // ---- 四段〜七段: 手牌の符 ----

    private fun handFuQuestion(hand: WinningHand, random: Random, split: Boolean = false): Question.HandFu {
        val result = FuCalculator.calculate(hand)!!
        val (choices, answerIndex) = fuChoices(result.fu, ScoreCalculator.FU_VALUES, random)
        return Question.HandFu(
            hand = hand,
            han = null,
            groups = if (split) groupsOf(hand, result) else null,
            choices = choices,
            answerIndex = answerIndex,
            explanation = explainHand(result),
        )
    }

    /** 手牌を面子・雀頭ごとに区切る。和了牌で完成したグループに印を付ける */
    fun groupsOf(hand: WinningHand, result: FuResult): List<TileGroupInfo> {
        val method = if (hand.method == WinMethod.RON) "ロン" else "ツモ"
        if (result.isChiitoitsu) {
            return (hand.concealed + hand.winningTile).distinct().sorted().map { tile ->
                val winning = tile == hand.winningTile
                TileGroupInfo(listOf(tile, tile), winning, caption = if (winning) method else null)
            }
        }
        // melds は手の中で完成した面子が先、鳴いた面子と暗槓が後ろに並んでいる
        val concealedCount = result.melds.size - hand.calledMelds.size
        val concealed = (0 until concealedCount).map { i ->
            val winning = i == result.winningMeldIndex
            TileGroupInfo(result.melds[i].tiles, winning, caption = if (winning) method else "手の中")
        }
        val tanki = result.winningMeldIndex == null
        val pair = TileGroupInfo(
            tiles = listOf(result.pair!!, result.pair),
            winning = tanki,
            caption = if (tanki) "雀頭・$method" else "雀頭",
        )
        val called = hand.calledMelds.map { meld ->
            TileGroupInfo(meld.tiles, winning = false, caption = meld.label, faceDownEnds = meld.kind == MeldKind.QUAD && !meld.open)
        }
        return concealed + pair + called
    }

    private fun handPointsQuestion(hand: WinningHand, random: Random): Question.HandFu {
        val result = FuCalculator.calculate(hand)!!
        // 役は網羅して判定していないので、翻数は問題の条件として与える。
        // 少なくとも確実に付く役の分はあり、満貫未満の計算を練習できるよう4翻までにする
        val minHan = guaranteedHan(hand, result).coerceAtLeast(1)
        val han = if (minHan >= 4) minHan else random.nextInt(minHan, 5)
        val points = Hand(hand.seat, hand.method, result.fu, han)
        val fallback = ScoreCalculator.FU_VALUES.flatMap { fu -> (1..4).map { points.copy(fu = fu, han = it) } }
            .filter(ScoreCalculator::isValid)
        val (choices, answerIndex) = QuizGenerator.pointsChoices(points, showFu = true, fallback, random)
        return Question.HandFu(
            hand = hand,
            han = han,
            choices = choices,
            answerIndex = answerIndex,
            explanation = explainHand(result) + "\n\n" + "${result.fu}符${han}翻（翻数は問題の指定）\n" + Explainer.explain(points),
        )
    }

    private fun explainHand(result: FuResult): String {
        val lines = result.items.map { "${it.label}: ${it.fu}符" }
        val total = when {
            result.isChiitoitsu -> "→ 七対子は25符"
            result.isPinfu && result.fu == 20 -> "→ $PINFU_SHAPE なので、ツモでも2符は付かず20符"
            result.isPinfu -> "→ $PINFU_SHAPE のロンは30符"
            result.raw == 20 && result.fu == 30 -> "合計 20符 → 鳴いた手のロンで符が無いときは30符"
            result.raw == result.fu -> "合計 ${result.fu}符"
            else -> "合計 ${result.raw}符 → 切り上げて ${result.fu}符"
        }
        val notes = when {
            result.isChiitoitsu -> emptyList()
            result.isPinfu -> listOf("（${result.wait.label}待ちとして数える）")
            else -> listOf(
                "（${result.wait.label}待ちとして数える）",
                "ここにない順子・役牌でない雀頭・両面や双碰の待ちは0符",
            )
        }
        return (lines + total + notes).joinToString("\n")
    }

    /**
     * 形から確実に付く翻（リーチ・門前ツモ・平和・七対子・役牌・タンヤオ）の合計。門前の手はリーチしているものとする。
     * ほかの役は判定しないので、実際の翻数の下限として使う。
     */
    fun guaranteedHan(hand: WinningHand, result: FuResult): Int {
        var han = 0
        if (hand.isClosed) han++
        if (hand.isClosed && hand.method == WinMethod.TSUMO) han++
        if (result.isPinfu) han++
        if (result.isChiitoitsu) han += 2
        result.melds.filter { it.kind != MeldKind.SEQUENCE }.forEach { meld ->
            if (meld.tile.isDragon) han++
            if (meld.tile.wind == hand.seatWind) han++
            if (meld.tile.wind == hand.roundWind) han++
        }
        if (hand.allTiles.none { it.isTerminalOrHonor }) han++
        return han
    }

    /** 正解の符に近い値を誤答にした選択肢 */
    private fun fuChoices(answer: Int, values: List<Int>, random: Random): Pair<List<String>, Int> {
        val distractors = (values - answer).sortedBy { kotlin.math.abs(it - answer) }.take(4).shuffled(random).take(3)
        val choices = (distractors + answer).sorted()
        return choices.map { "${it}符" } to choices.indexOf(answer)
    }

    // ---- 手牌の生成 ----

    /** 出題する手の種類。捨てられやすい種類が減らないよう、先に種類を決めてから作る */
    private enum class HandType(val weight: Int) {
        CLOSED(40),
        PINFU(20),
        OPEN(30),
        CHIITOITSU(10),
    }

    /** 和了形として曖昧さがなく、役がある手を作る。method が null ならロンとツモを半々に */
    fun randomHand(random: Random, method: WinMethod?, allowChiitoitsu: Boolean): WinningHand {
        val types = HandType.entries.filter { allowChiitoitsu || it != HandType.CHIITOITSU }
        var r = random.nextInt(types.sumOf { it.weight })
        val type = types.first { r -= it.weight; r < 0 }
        repeat(20_000) {
            val m = method ?: if (random.nextBoolean()) WinMethod.RON else WinMethod.TSUMO
            val hand = when (type) {
                HandType.CHIITOITSU -> randomChiitoitsu(random, m)
                HandType.PINFU -> randomStandardHand(random, m, openHand = false, sequencesOnly = true)
                HandType.OPEN -> randomStandardHand(random, m, openHand = true, sequencesOnly = false)
                HandType.CLOSED -> randomStandardHand(random, m, openHand = false, sequencesOnly = false)
            }
            if (hand != null && isUsable(hand) && matches(type, hand)) return hand
        }
        error("出題できる手牌を作れませんでした")
    }

    private fun matches(type: HandType, hand: WinningHand): Boolean {
        val result = FuCalculator.calculate(hand) ?: return false
        return when (type) {
            HandType.CLOSED -> hand.isClosed && !result.isPinfu && !result.isChiitoitsu
            HandType.PINFU -> result.isPinfu
            HandType.OPEN -> !hand.isClosed
            HandType.CHIITOITSU -> result.isChiitoitsu
        }
    }

    private fun randomStandardHand(
        random: Random,
        method: WinMethod,
        openHand: Boolean,
        sequencesOnly: Boolean,
    ): WinningHand? {
        val counts = IntArray(34)
        fun take(tiles: List<Tile>): Boolean {
            if (tiles.groupingBy { it }.eachCount().any { (t, n) -> counts[t.index] + n > 4 }) return false
            tiles.forEach { counts[it.index]++ }
            return true
        }

        val concealedSets = mutableListOf<Meld>()
        val calledMelds = mutableListOf<Meld>()
        while (concealedSets.size + calledMelds.size < 4) {
            val kind = when {
                sequencesOnly -> MeldKind.SEQUENCE
                else -> when (random.nextInt(10)) {
                    in 0..5 -> MeldKind.SEQUENCE
                    in 6..8 -> MeldKind.TRIPLET
                    else -> MeldKind.QUAD
                }
            }
            val tile = if (kind == MeldKind.SEQUENCE) {
                Tile(NUMBER_SUITS.random(random), random.nextInt(1, 8))
            } else {
                Tile.ALL.random(random)
            }
            // 槓子は必ず手の外に出す（門前なら暗槓）。鳴いた手では面子の半分ほどを鳴く
            val called = kind == MeldKind.QUAD || (openHand && random.nextBoolean())
            val meld = Meld(kind, tile, open = called && openHand && (kind != MeldKind.QUAD || random.nextInt(3) > 0))
            if (!take(meld.tiles)) continue
            if (called) calledMelds += meld else concealedSets += meld
        }
        if (openHand && calledMelds.none { it.open }) return null

        val pair = Tile.ALL.shuffled(random).firstOrNull { counts[it.index] <= 2 } ?: return null
        take(listOf(pair, pair))

        // 和了牌は手の中の面子か雀頭のどこか
        val components = concealedSets.map { it.tiles } + listOf(listOf(pair, pair))
        val winningTile = components.random(random).random(random)
        val concealed = (concealedSets.flatMap { it.tiles } + pair + pair).toMutableList()
        concealed.remove(winningTile)
        return WinningHand(
            concealed = concealed.sorted(),
            calledMelds = calledMelds,
            winningTile = winningTile,
            method = method,
            roundWind = listOf(Wind.EAST, Wind.SOUTH).random(random),
            seatWind = Wind.entries.random(random),
        )
    }

    private fun randomChiitoitsu(random: Random, method: WinMethod): WinningHand {
        val pairs = Tile.ALL.shuffled(random).take(7)
        val winningTile = pairs.random(random)
        val concealed = pairs.flatMap { listOf(it, it) }.toMutableList()
        concealed.remove(winningTile)
        return WinningHand(
            concealed = concealed.sorted(),
            calledMelds = emptyList(),
            winningTile = winningTile,
            method = method,
            roundWind = listOf(Wind.EAST, Wind.SOUTH).random(random),
            seatWind = Wind.entries.random(random),
        )
    }

    /**
     * 出題に使える手か。
     * - どう取っても符と形（平和・七対子）が変わらない（取り方で答えが割れない）
     * - 110符以内で、連風牌の雀頭（ルールで2符か4符か分かれる）を含まない
     * - 役がある（門前はリーチ、鳴いた手は役牌かタンヤオ）
     */
    fun isUsable(hand: WinningHand): Boolean {
        if (hand.allTiles.groupingBy { it }.eachCount().values.any { it > 4 }) return false
        val all = FuCalculator.interpretations(hand)
        if (all.isEmpty()) return false
        val first = all.first()
        if (all.any { it.raw != first.raw || it.fu != first.fu || it.isPinfu != first.isPinfu || it.isChiitoitsu != first.isChiitoitsu }) {
            return false
        }
        if (first.fu !in ScoreCalculator.FU_VALUES) return false
        val pair = first.pair
        if (pair != null && pair.wind != null && pair.wind == hand.roundWind && pair.wind == hand.seatWind) return false
        if (hand.isClosed) return true
        return guaranteedHan(hand, first) > 0
    }
}
