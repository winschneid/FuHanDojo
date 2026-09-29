package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Meld
import com.winschneid.fuhandojo.domain.model.MeldKind
import com.winschneid.fuhandojo.domain.model.Tile
import com.winschneid.fuhandojo.domain.model.Wait
import com.winschneid.fuhandojo.domain.model.WinMethod
import com.winschneid.fuhandojo.domain.model.WinningHand
import com.winschneid.fuhandojo.domain.model.Wind

/** 符の内訳の1行 */
data class FuItem(val label: String, val fu: Int)

/**
 * 符計算の結果。
 * @param raw 切り上げ前の合計。平和ツモや鳴いた手の30符など、例外の場合はその値
 * @param fu 10符単位に切り上げた符（七対子は25符）
 */
data class FuResult(
    val items: List<FuItem>,
    val raw: Int,
    val fu: Int,
    val isPinfu: Boolean,
    val isChiitoitsu: Boolean,
    /** 手の中で完成した刻子（ロンで完成したものは明刻になっている）を含む、すべての面子 */
    val melds: List<Meld>,
    val pair: Tile?,
    val wait: Wait,
    /** 和了牌で完成した面子の melds での位置。雀頭（単騎）や七対子なら null */
    val winningMeldIndex: Int? = null,
)

object FuCalculator {

    /** 符が一番高くなる取り方で計算する。和了形になっていなければ null */
    fun calculate(hand: WinningHand): FuResult? =
        interpretations(hand).maxWithOrNull(compareBy<FuResult>({ it.fu }, { it.raw }))

    /** 和了形としてありうる取り方すべて（面子の分け方 × 和了牌がどこを完成させたか） */
    fun interpretations(hand: WinningHand): List<FuResult> {
        val tiles = hand.concealed + hand.winningTile
        val results = mutableListOf<FuResult>()
        if (hand.calledMelds.isEmpty() && isChiitoitsu(tiles)) results += chiitoitsu(hand)
        if (tiles.size != 14 - hand.calledMelds.size * 3) return results
        for ((pair, sets) in decompose(tiles)) {
            if (pair == hand.winningTile) results += standard(hand, pair, sets, winningSet = null, wait = Wait.TANKI)
            sets.forEachIndexed { i, set ->
                if (hand.winningTile in set.tiles) {
                    results += standard(hand, pair, sets, winningSet = i, wait = waitOf(set, hand.winningTile))
                }
            }
        }
        return results
    }

    /** 雀頭の符。三元牌・自風・場風が2符ずつ（連風牌は4符） */
    fun pairFu(pair: Tile, roundWind: Wind, seatWind: Wind): Int {
        var fu = if (pair.isDragon) 2 else 0
        if (pair.wind == seatWind) fu += 2
        if (pair.wind == roundWind) fu += 2
        return fu
    }

    fun waitOf(set: Meld, winningTile: Tile): Wait = when (set.kind) {
        MeldKind.SEQUENCE -> when (winningTile.number - set.tile.number) {
            1 -> Wait.KANCHAN
            0 -> if (set.tile.number == 7) Wait.PENCHAN else Wait.RYANMEN
            else -> if (set.tile.number == 1) Wait.PENCHAN else Wait.RYANMEN
        }
        else -> Wait.SHANPON
    }

    private fun standard(
        hand: WinningHand,
        pair: Tile,
        concealedSets: List<Meld>,
        winningSet: Int?,
        wait: Wait,
    ): FuResult {
        val ron = hand.method == WinMethod.RON
        // ロンで完成した刻子は、他家の牌で完成したので明刻として数える
        val sets = concealedSets.mapIndexed { i, set ->
            if (ron && i == winningSet && set.kind == MeldKind.TRIPLET) set.copy(open = true) else set
        }
        val melds = sets + hand.calledMelds
        val pairFu = pairFu(pair, hand.roundWind, hand.seatWind)
        val isPinfu = hand.isClosed && melds.all { it.kind == MeldKind.SEQUENCE } && pairFu == 0 && wait == Wait.RYANMEN

        val items = mutableListOf(FuItem("副底", 20))
        if (isPinfu) {
            return if (ron) {
                items += FuItem("門前ロン", 10)
                FuResult(items, 30, 30, isPinfu = true, isChiitoitsu = false, melds, pair, wait, winningSet)
            } else {
                items += FuItem("平和ツモ（ツモの2符は付かない）", 0)
                FuResult(items, 20, 20, isPinfu = true, isChiitoitsu = false, melds, pair, wait, winningSet)
            }
        }
        if (hand.isClosed && ron) items += FuItem("門前ロン", 10)
        if (!ron) items += FuItem("ツモ", 2)
        melds.filter { it.fu > 0 }.forEach { meld ->
            val kind = when (meld.kind) {
                MeldKind.TRIPLET -> if (meld.open) "明刻" else "暗刻"
                else -> if (meld.open) "明槓" else "暗槓"
            }
            val suffix = if (meld.tile.isTerminalOrHonor) "・ヤオ九牌" else ""
            val ronNote = if (meld.kind == MeldKind.TRIPLET && meld.open && meld !in hand.calledMelds) "（ロンで完成）" else ""
            items += FuItem("$kind$suffix ${meld.tile.label}$ronNote", meld.fu)
        }
        if (pairFu > 0) items += FuItem("雀頭 ${pair.label}（役牌）", pairFu)
        if (wait.fu > 0) items += FuItem("${wait.label}待ち", wait.fu)

        val raw = items.sumOf { it.fu }
        // 鳴いた手で副底しかないロンは、20符ではなく30符として扱う
        val fu = if (!hand.isClosed && ron && raw == 20) 30 else roundUp10(raw)
        return FuResult(items, raw, fu, isPinfu = false, isChiitoitsu = false, melds, pair, wait, winningSet)
    }

    private fun chiitoitsu(hand: WinningHand) = FuResult(
        items = listOf(FuItem("七対子（符は25符で固定）", 25)),
        raw = 25,
        fu = 25,
        isPinfu = false,
        isChiitoitsu = true,
        melds = emptyList(),
        pair = null,
        wait = Wait.TANKI,
    )

    private fun isChiitoitsu(tiles: List<Tile>): Boolean {
        val counts = tiles.groupingBy { it }.eachCount()
        return tiles.size == 14 && counts.size == 7 && counts.values.all { it == 2 }
    }

    /** 雀頭1つと面子に分ける方法をすべて返す */
    fun decompose(tiles: List<Tile>): List<Pair<Tile, List<Meld>>> {
        val counts = IntArray(34).also { c -> tiles.forEach { c[it.index]++ } }
        val results = mutableListOf<Pair<Tile, List<Meld>>>()
        for (p in 0 until 34) {
            if (counts[p] < 2) continue
            counts[p] -= 2
            findSets(counts).forEach { results += Tile.fromIndex(p) to it }
            counts[p] += 2
        }
        return results.distinctBy { (pair, sets) -> pair to sets.sortedBy { it.tile.index * 2 + it.kind.ordinal } }
    }

    private fun findSets(counts: IntArray): List<List<Meld>> {
        val i = counts.indexOfFirst { it > 0 }
        if (i < 0) return listOf(emptyList())
        val tile = Tile.fromIndex(i)
        val results = mutableListOf<List<Meld>>()
        if (counts[i] >= 3) {
            counts[i] -= 3
            findSets(counts).forEach { results += listOf(Meld(MeldKind.TRIPLET, tile, open = false)) + it }
            counts[i] += 3
        }
        if (!tile.isHonor && tile.number <= 7 && counts[i + 1] > 0 && counts[i + 2] > 0) {
            counts[i]--; counts[i + 1]--; counts[i + 2]--
            findSets(counts).forEach { results += listOf(Meld(MeldKind.SEQUENCE, tile, open = false)) + it }
            counts[i]++; counts[i + 1]++; counts[i + 2]++
        }
        return results
    }

    private fun roundUp10(fu: Int) = (fu + 9) / 10 * 10
}
