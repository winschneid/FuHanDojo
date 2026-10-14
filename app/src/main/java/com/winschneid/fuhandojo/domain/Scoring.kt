package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Limit
import com.winschneid.fuhandojo.domain.model.MeldKind
import com.winschneid.fuhandojo.domain.model.Payment
import com.winschneid.fuhandojo.domain.model.Suit
import com.winschneid.fuhandojo.domain.model.Tile
import com.winschneid.fuhandojo.domain.model.WinMethod
import com.winschneid.fuhandojo.domain.model.WinningHand
import com.winschneid.fuhandojo.domain.model.Yaku
import kotlinx.serialization.Serializable

@Serializable
enum class Riichi(val label: String) {
    NONE("なし"),
    RIICHI("リーチ"),
    DOUBLE("ダブルリーチ"),
}

/**
 * 手牌の形からはわからない、和了の状況。
 * @param lastTile ツモなら海底摸月、ロンなら河底撈魚
 * @param dora 表ドラの枚数（ドラ表示牌から数えた分）
 */
@Serializable
data class WinContext(
    val riichi: Riichi = Riichi.NONE,
    val ippatsu: Boolean = false,
    val lastTile: Boolean = false,
    val rinshan: Boolean = false,
    val chankan: Boolean = false,
    val dora: Int = 0,
    val akaDora: Int = 0,
    val uraDora: Int = 0,
    val honba: Int = 0,
)

/**
 * 点数計算の結果。
 * @param yaku 役とドラの内訳（役満のときは役満の役だけ）
 * @param yakumanCount 役満の倍数。役満でなければ0（数え役満も0で、limit が役満になる）
 * @param fuResult 採用した面子の取り方の符。国士無双は面子に分けないので null
 */
data class ScoreResult(
    val yaku: List<Yaku>,
    val yakumanCount: Int,
    val han: Int,
    val fu: Int,
    val fuResult: FuResult?,
    val limit: Limit?,
    val base: Int,
    val payment: Payment,
)

sealed interface ScoreOutcome {
    data class Success(val result: ScoreResult) : ScoreOutcome

    /** 牌の数が足りない・多い */
    data class Incomplete(val needed: Int, val actual: Int) : ScoreOutcome

    /** 枚数は合っているが和了の形になっていない */
    data object NotWinningShape : ScoreOutcome

    /** 和了の形だが役がない（ドラだけでは和了れない） */
    data object NoYaku : ScoreOutcome

    /** 入力の組み合わせがありえない（同じ牌が5枚以上、鳴いているのにリーチなど） */
    data class Invalid(val reason: String) : ScoreOutcome
}

/**
 * 全役を判定して点数を出す。
 *
 * ルール: 喰いタンあり、役満の複合あり（ダブル役満なし）、数え役満あり、切り上げ満貫なし、
 * 連風牌の雀頭は4符、嶺上開花のツモ符あり。天和・地和・流し満貫などは扱わない。
 */
object Scoring {

    private val GREEN_TILES = setOf(
        Tile(Suit.SOU, 2), Tile(Suit.SOU, 3), Tile(Suit.SOU, 4), Tile(Suit.SOU, 6), Tile(Suit.SOU, 8),
        Tile(Suit.HONOR, 6),
    )

    fun score(hand: WinningHand, context: WinContext): ScoreOutcome {
        val needed = 14 - hand.calledMelds.size * 3
        val actual = hand.concealed.size + 1
        if (actual != needed) return ScoreOutcome.Incomplete(needed, actual)
        validate(hand, context)?.let { return ScoreOutcome.Invalid(it) }

        val candidates = mutableListOf<ScoreResult>()
        var hasShape = false
        if (isThirteenOrphans(hand)) {
            hasShape = true
            candidates += yakumanResult(hand, context, listOf(Yaku("国士無双", 13)), fuResult = null)
        }
        for (reading in FuCalculator.interpretations(hand)) {
            hasShape = true
            evaluate(hand, context, reading)?.let { candidates += it }
        }
        if (!hasShape) return ScoreOutcome.NotWinningShape
        // 取り方が複数あるときは、点数がいちばん高いものを採用する。同じ点数なら役満（数え役満より本物の役満）、
        // 次に翻、符の高いもの
        val best = candidates.maxWithOrNull(
            compareBy<ScoreResult>({ it.payment.total }, { it.yakumanCount }, { it.han }, { it.fu }),
        )
            ?: return ScoreOutcome.NoYaku
        return ScoreOutcome.Success(best)
    }

    private fun validate(hand: WinningHand, context: WinContext): String? {
        if (hand.allTiles.groupingBy { it }.eachCount().values.any { it > 4 }) return "同じ牌が5枚以上あります"
        if (hand.calledMelds.size > 4) return "鳴いた面子は4つまでです"
        if (context.riichi != Riichi.NONE && !hand.isClosed) return "鳴いているとリーチできません"
        if (context.ippatsu && context.riichi == Riichi.NONE) return "一発はリーチしたときだけです"
        if (context.uraDora > 0 && context.riichi == Riichi.NONE) return "裏ドラはリーチしたときだけです"
        if (context.rinshan && hand.method != WinMethod.TSUMO) return "嶺上開花はツモのときだけです"
        if (context.rinshan && hand.calledMelds.none { it.kind == MeldKind.QUAD }) return "嶺上開花は槓をしたときだけです"
        if (context.chankan && hand.method != WinMethod.RON) return "槍槓はロンのときだけです"
        if (context.rinshan && context.lastTile) return "嶺上開花と海底摸月は同時に付きません"
        if (context.chankan && context.lastTile) return "槍槓と河底撈魚は同時に付きません"
        if (context.ippatsu && context.rinshan) return "嶺上開花のときは一発は付きません（槓をすると一発は消えます）"
        // 槍槓は相手が4枚目を加槓した牌なので、その牌を自分は1枚も持っていない
        if (context.chankan && (hand.concealed + hand.calledMelds.flatMap { it.tiles }).any { it == hand.winningTile }) {
            return "槍槓のアガリ牌と同じ牌は、自分の手にはないはずです"
        }
        val fives = hand.allTiles.count { !it.isHonor && it.number == 5 }
        if (context.akaDora > fives) return "赤ドラの枚数が、手牌の5の枚数より多いです"
        if (listOf(context.dora, context.akaDora, context.uraDora, context.honba).any { it < 0 }) return "枚数がマイナスです"
        return null
    }

    /** 1つの面子の取り方で点数を出す。役がなければ null */
    private fun evaluate(hand: WinningHand, context: WinContext, reading: FuResult): ScoreResult? {
        val yakuman = yakumanOf(hand, reading)
        if (yakuman.isNotEmpty()) return yakumanResult(hand, context, yakuman, reading)

        val yaku = regularYaku(hand, context, reading)
        if (yaku.isEmpty()) return null
        val withDora = yaku + doraOf(context)
        val han = withDora.sumOf { it.han }
        val limit = ScoreCalculator.limitOf(reading.fu, han)
        val base = ScoreCalculator.basePoints(reading.fu, han)
        return ScoreResult(
            yaku = withDora,
            yakumanCount = 0,
            han = han,
            fu = reading.fu,
            fuResult = reading,
            limit = limit,
            base = base,
            payment = ScoreCalculator.paymentForBase(base, hand.seat, hand.method, context.honba),
        )
    }

    private fun yakumanResult(hand: WinningHand, context: WinContext, yakuman: List<Yaku>, fuResult: FuResult?): ScoreResult {
        val base = Limit.YAKUMAN.basePoints * yakuman.size
        return ScoreResult(
            yaku = yakuman,
            yakumanCount = yakuman.size,
            han = yakuman.sumOf { it.han },
            // 役満は符を数えない
            fu = 0,
            fuResult = fuResult,
            limit = Limit.YAKUMAN,
            base = base,
            payment = ScoreCalculator.paymentForBase(base, hand.seat, hand.method, context.honba),
        )
    }

    private fun doraOf(context: WinContext): List<Yaku> = listOfNotNull(
        context.dora.takeIf { it > 0 }?.let { Yaku("ドラ$it", it) },
        context.akaDora.takeIf { it > 0 }?.let { Yaku("赤ドラ$it", it) },
        context.uraDora.takeIf { it > 0 }?.let { Yaku("裏ドラ$it", it) },
    )

    private fun isThirteenOrphans(hand: WinningHand): Boolean {
        if (hand.calledMelds.isNotEmpty()) return false
        val tiles = hand.concealed + hand.winningTile
        return tiles.size == 14 && tiles.all { it.isTerminalOrHonor } && tiles.toSet().size == 13
    }

    /** 役満（13翻として扱い、複数あれば倍数になる） */
    private fun yakumanOf(hand: WinningHand, reading: FuResult): List<Yaku> = buildList {
        val tiles = hand.allTiles
        if (tiles.all { it.isHonor }) add(Yaku("字一色", 13))
        if (tiles.all { it in GREEN_TILES }) add(Yaku("緑一色", 13))
        if (tiles.all { !it.isHonor && it.isTerminalOrHonor }) add(Yaku("清老頭", 13))
        if (isNineGates(hand)) add(Yaku("九蓮宝燈", 13))
        if (reading.isChiitoitsu) return@buildList
        val sets = reading.melds.filter { it.kind != MeldKind.SEQUENCE }
        // ロンで完成した刻子は明刻になっているので、暗刻に数えない
        if (sets.count { !it.open } == 4) add(Yaku("四暗刻", 13))
        if (sets.count { it.kind == MeldKind.QUAD } == 4) add(Yaku("四槓子", 13))
        if (sets.count { it.tile.isDragon } == 3) add(Yaku("大三元", 13))
        val windSets = sets.count { it.tile.wind != null }
        if (windSets == 4) add(Yaku("大四喜", 13))
        if (windSets == 3 && reading.pair?.wind != null) add(Yaku("小四喜", 13))
    }

    private fun isNineGates(hand: WinningHand): Boolean {
        if (hand.calledMelds.isNotEmpty()) return false
        val tiles = hand.allTiles
        if (tiles.any { it.isHonor } || tiles.map { it.suit }.toSet().size != 1) return false
        val counts = IntArray(10).also { c -> tiles.forEach { c[it.number]++ } }
        return counts[1] >= 3 && counts[9] >= 3 && (2..8).all { counts[it] >= 1 }
    }

    /** 役満以外の役。鳴くと翻が下がる役は open で下げる */
    private fun regularYaku(hand: WinningHand, context: WinContext, reading: FuResult): List<Yaku> = buildList {
        val closed = hand.isClosed
        val tsumo = hand.method == WinMethod.TSUMO
        val tiles = hand.allTiles
        fun add(name: String, closedHan: Int, openHan: Int = closedHan) {
            val han = if (closed) closedHan else openHan
            if (han > 0) add(Yaku(name, han))
        }

        when (context.riichi) {
            Riichi.RIICHI -> add("立直", 1, 0)
            Riichi.DOUBLE -> add("ダブル立直", 2, 0)
            Riichi.NONE -> Unit
        }
        if (context.ippatsu) add("一発", 1, 0)
        if (closed && tsumo) add("門前清自摸和", 1, 0)
        if (reading.isPinfu) add("平和", 1, 0)
        if (tiles.none { it.isTerminalOrHonor }) add("断么九", 1)
        if (context.lastTile) add(if (tsumo) "海底摸月" else "河底撈魚", 1)
        if (context.rinshan) add("嶺上開花", 1)
        if (context.chankan) add("槍槓", 1)

        val numberSuits = tiles.filter { !it.isHonor }.map { it.suit }.toSet()
        val hasHonor = tiles.any { it.isHonor }
        val allTerminalOrHonor = tiles.all { it.isTerminalOrHonor }

        if (reading.isChiitoitsu) {
            add("七対子", 2, 0)
            if (allTerminalOrHonor) add("混老頭", 2)
            if (numberSuits.size == 1) if (hasHonor) add("混一色", 3, 2) else add("清一色", 6, 5)
            return@buildList
        }

        val melds = reading.melds
        val sets = melds.filter { it.kind != MeldKind.SEQUENCE }
        val sequences = melds.filter { it.kind == MeldKind.SEQUENCE }
        val pair = reading.pair!!

        sets.forEach { meld ->
            if (meld.tile.isDragon) add("役牌 ${meld.tile.label}", 1)
            if (meld.tile.wind == hand.seatWind) add("自風 ${meld.tile.label}", 1)
            if (meld.tile.wind == hand.roundWind) add("場風 ${meld.tile.label}", 1)
        }
        if (closed) {
            val identicalPairs = sequences.groupingBy { it.tile }.eachCount().values.sumOf { it / 2 }
            when (identicalPairs) {
                2 -> add("二盃口", 3, 0)
                1 -> add("一盃口", 1, 0)
            }
        }
        if (sequences.groupBy { it.tile.number }.values.any { g -> g.map { it.tile.suit }.toSet().size == 3 }) {
            add("三色同順", 2, 1)
        }
        if (sequences.groupBy { it.tile.suit }.values.any { g -> g.map { it.tile.number }.containsAll(listOf(1, 4, 7)) }) {
            add("一気通貫", 2, 1)
        }
        val everyGroupHasTerminal = pair.isTerminalOrHonor && melds.all { m -> m.tiles.any { it.isTerminalOrHonor } }
        if (everyGroupHasTerminal && sequences.isNotEmpty()) {
            if (hasHonor) add("混全帯么九", 2, 1) else add("純全帯么九", 3, 2)
        }
        if (sets.size == 4) add("対々和", 2)
        if (sets.count { !it.open } == 3) add("三暗刻", 2)
        val numberSets = sets.filter { !it.tile.isHonor }
        if (numberSets.groupBy { it.tile.number }.values.any { g -> g.map { it.tile.suit }.toSet().size == 3 }) {
            add("三色同刻", 2)
        }
        if (sets.count { it.kind == MeldKind.QUAD } == 3) add("三槓子", 2)
        if (sets.count { it.tile.isDragon } == 2 && pair.isDragon) add("小三元", 2)
        if (allTerminalOrHonor) add("混老頭", 2)
        if (numberSuits.size == 1) if (hasHonor) add("混一色", 3, 2) else add("清一色", 6, 5)
    }
}
