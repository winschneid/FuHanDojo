package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Meld
import com.winschneid.fuhandojo.domain.model.MeldKind
import com.winschneid.fuhandojo.domain.model.Question
import com.winschneid.fuhandojo.domain.model.Suit
import com.winschneid.fuhandojo.domain.model.Tile
import com.winschneid.fuhandojo.domain.model.Wait
import com.winschneid.fuhandojo.domain.model.Wind
import kotlin.random.Random

/**
 * 符の入門。ルールを1つずつ説明し、そのルールだけを使う簡単な問題ですぐ確かめる。
 * 名前は完了の記録の保存キーに使うので変えない。
 */
enum class TutorialStep(val title: String, val rule: String) {
    SIMPLE_MELDS(
        "順子と刻子",
        "順子（3枚の連番）は0符。\n" +
            "刻子（同じ牌3枚）には符が付く。\n" +
            "・鳴いた刻子（明刻・ポン）: 2符\n" +
            "・手の中でそろえた刻子（暗刻）: 4符\n" +
            "※2〜8の数牌のとき",
    ),
    TERMINAL_MELDS(
        "1・9・字牌は2倍",
        "1・9・字牌をヤオ九牌という。\n" +
            "ヤオ九牌の刻子は2倍になる。\n" +
            "・明刻: 2符 → 4符\n" +
            "・暗刻: 4符 → 8符",
    ),
    QUADS(
        "槓子は刻子の4倍",
        "槓子（同じ牌4枚）は刻子の4倍。\n" +
            "・明槓: 8符（ヤオ九牌なら16符）\n" +
            "・暗槓: 16符（ヤオ九牌なら32符）\n" +
            "暗槓は両端を伏せて置く。",
    ),
    PAIRS(
        "雀頭の符",
        "雀頭（同じ牌2枚）は、役牌のときだけ2符。\n" +
            "・三元牌（白・發・中）\n" +
            "・自風（自分の風。南家なら南）\n" +
            "・場風（東場なら東）\n" +
            "それ以外は0符。",
    ),
    WAITS(
        "待ちの符",
        "アガリ牌を待つ形によって2符付く。\n" +
            "・カンチャン（間の1枚を待つ）: 2符\n" +
            "・ペンチャン（12で3、89で7を待つ）: 2符\n" +
            "・単騎（雀頭の1枚を待つ）: 2符\n" +
            "・両面・シャンポン: 0符",
    ),
}

/**
 * ルールの説明に添える牌の例。
 * winningTile があるときは、tiles のあとに「＋アガリ牌」として並べる（待ちの例）。
 */
data class TutorialExample(
    val tiles: List<Tile>,
    val caption: String,
    val faceDownEnds: Boolean = false,
    val winningTile: Tile? = null,
)

object Tutorial {
    const val QUESTIONS_PER_STEP = 4
    const val PASS_SCORE = 3

    private val NUMBER_SUITS = listOf(Suit.MAN, Suit.PIN, Suit.SOU)

    fun examples(step: TutorialStep): List<TutorialExample> {
        val man = { n: Int -> Tile(Suit.MAN, n) }
        val pin = { n: Int -> Tile(Suit.PIN, n) }
        val sou = { n: Int -> Tile(Suit.SOU, n) }
        val east = Tile(Suit.HONOR, 1)
        val chun = Tile(Suit.HONOR, 7)
        return when (step) {
            TutorialStep.SIMPLE_MELDS -> listOf(
                TutorialExample(listOf(man(4), man(5), man(6)), "順子 0符"),
                TutorialExample(List(3) { pin(5) }, "ポン 2符"),
                TutorialExample(List(3) { pin(5) }, "暗刻 4符"),
            )
            TutorialStep.TERMINAL_MELDS -> listOf(
                TutorialExample(List(3) { chun }, "中のポン 4符"),
                TutorialExample(List(3) { man(1) }, "1萬の暗刻 8符"),
            )
            TutorialStep.QUADS -> listOf(
                TutorialExample(List(4) { sou(5) }, "明槓 8符"),
                TutorialExample(List(4) { sou(5) }, "暗槓 16符", faceDownEnds = true),
                TutorialExample(List(4) { east }, "東の暗槓 32符", faceDownEnds = true),
            )
            TutorialStep.PAIRS -> listOf(
                TutorialExample(List(2) { chun }, "中 2符"),
                TutorialExample(List(2) { pin(5) }, "5筒 0符"),
            )
            TutorialStep.WAITS -> listOf(
                TutorialExample(listOf(man(2), man(4)), "カンチャン 2符", winningTile = man(3)),
                TutorialExample(listOf(man(3), man(4)), "両面 0符", winningTile = man(2)),
            )
        }
    }

    /** そのステップのルールだけを使う問題。どのパターンも1回は出るように並べる */
    fun questions(step: TutorialStep, random: Random): List<Question> = when (step) {
        TutorialStep.SIMPLE_MELDS -> {
            val patterns = listOf(MeldKind.SEQUENCE, MeldKind.TRIPLET, MeldKind.TRIPLET)
            val opens = listOf(random.nextBoolean(), true, false)
            val melds = patterns.zip(opens) { kind, open -> simpleMeld(kind, open, random) } +
                simpleMeld(listOf(MeldKind.SEQUENCE, MeldKind.TRIPLET).random(random), random.nextBoolean(), random)
            melds.shuffled(random).map { FuQuizGenerator.meldQuestionFor(it, listOf(0, 2, 4), random, guide = null) }
        }
        TutorialStep.TERMINAL_MELDS -> listOf(
            Meld(MeldKind.TRIPLET, honor(random), open = true),
            Meld(MeldKind.TRIPLET, honor(random), open = false),
            Meld(MeldKind.TRIPLET, terminal(random), open = true),
            Meld(MeldKind.TRIPLET, terminal(random), open = false),
        ).shuffled(random).map { FuQuizGenerator.meldQuestionFor(it, listOf(2, 4, 8), random, guide = null) }
        TutorialStep.QUADS -> listOf(
            Meld(MeldKind.QUAD, simple(random), open = true),
            Meld(MeldKind.QUAD, simple(random), open = false),
            Meld(MeldKind.QUAD, yaochu(random), open = true),
            Meld(MeldKind.QUAD, yaochu(random), open = false),
        ).shuffled(random).map { FuQuizGenerator.meldQuestionFor(it, listOf(4, 8, 16, 32), random, guide = null) }
        TutorialStep.PAIRS -> pairQuestions(random)
        TutorialStep.WAITS -> (listOf(Wait.KANCHAN, Wait.PENCHAN, Wait.TANKI) + listOf(Wait.RYANMEN, Wait.SHANPON).random(random))
            .shuffled(random)
            .map { FuQuizGenerator.waitQuestion(random, it) }
    }

    /** 三元牌・自風・場風・役牌でない牌の雀頭を1問ずつ。連風牌（場風かつ自風）は出さない */
    private fun pairQuestions(random: Random): List<Question> {
        val choices = listOf("0符", "2符")
        fun question(pair: Tile, round: Wind, seat: Wind) =
            FuQuizGenerator.pairQuestionFor(pair, round, seat, choices, guide = null)

        val round = listOf(Wind.EAST, Wind.SOUTH).random(random)
        val otherSeat = { Wind.entries.filter { it != round }.random(random) }
        val dragon = question(Tile(Suit.HONOR, random.nextInt(5, 8)), round, Wind.entries.random(random))
        val seatWind = otherSeat().let { seat -> question(Tile.wind(seat), round, seat) }
        val roundWind = question(Tile.wind(round), round, otherSeat())
        val plain = otherSeat().let { seat ->
            // 役牌でない牌: 数牌か、場風でも自風でもない風牌
            val guest = Wind.entries.filter { it != round && it != seat }
            val tile = if (random.nextBoolean()) Tile(NUMBER_SUITS.random(random), random.nextInt(1, 10)) else Tile.wind(guest.random(random))
            question(tile, round, seat)
        }
        return listOf(dragon, seatWind, roundWind, plain).shuffled(random)
    }

    private fun simpleMeld(kind: MeldKind, open: Boolean, random: Random): Meld =
        if (kind == MeldKind.SEQUENCE) {
            Meld(kind, Tile(NUMBER_SUITS.random(random), random.nextInt(2, 7)), open)
        } else {
            Meld(kind, simple(random), open)
        }

    private fun simple(random: Random) = Tile(NUMBER_SUITS.random(random), random.nextInt(2, 9))
    private fun terminal(random: Random) = Tile(NUMBER_SUITS.random(random), listOf(1, 9).random(random))
    private fun honor(random: Random) = Tile(Suit.HONOR, random.nextInt(1, 8))
    private fun yaochu(random: Random) = if (random.nextBoolean()) terminal(random) else honor(random)
}
