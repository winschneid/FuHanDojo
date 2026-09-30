package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.MeldKind
import com.winschneid.fuhandojo.domain.model.Suit
import com.winschneid.fuhandojo.domain.model.Tile
import com.winschneid.fuhandojo.domain.model.WinMethod
import com.winschneid.fuhandojo.domain.model.WinningHand
import com.winschneid.fuhandojo.domain.model.Yaku

/**
 * 手牌の形から付く役を調べる。
 *
 * アプリが翻数として数えるのは countedYaku の役だけなので、それ以外の役が付く形は
 * 点数を答える問題に出さない（翻数が実際より少なくなってしまうため）。役満の形は符を数えないので、
 * 手牌の問題すべてから除く。どちらも、面子の取り方（FuResult）ごとに調べる。
 */
object HandShapes {

    private val GREEN_TILES = setOf(
        Tile(Suit.SOU, 2), Tile(Suit.SOU, 3), Tile(Suit.SOU, 4), Tile(Suit.SOU, 6), Tile(Suit.SOU, 8),
        Tile(Suit.HONOR, 6),
    )

    /**
     * 形から確実に付き、アプリが翻数に数える役。門前の手はリーチしているものとする。
     * 場の状況で付く役（一発・海底・嶺上開花など）は付いていないものとする。
     */
    fun countedYaku(hand: WinningHand, result: FuResult): List<Yaku> = buildList {
        if (hand.isClosed) add(Yaku("リーチ", 1))
        if (hand.isClosed && hand.method == WinMethod.TSUMO) add(Yaku("ツモ", 1))
        if (result.isPinfu) add(Yaku("平和", 1))
        if (result.isChiitoitsu) add(Yaku("七対子", 2))
        if (hand.allTiles.none { it.isTerminalOrHonor }) add(Yaku("タンヤオ", 1))
        result.melds.filter { it.kind != MeldKind.SEQUENCE }.forEach { meld ->
            if (meld.tile.isDragon) add(Yaku("役牌 ${meld.tile.label}", 1))
            if (meld.tile.wind == hand.seatWind) add(Yaku("自風 ${meld.tile.label}", 1))
            if (meld.tile.wind == hand.roundWind) add(Yaku("場風 ${meld.tile.label}", 1))
        }
    }

    /** 役満の形（四暗刻・大三元・字一色・清老頭・緑一色・四喜和・四槓子・九蓮宝燈） */
    fun isYakumanShape(hand: WinningHand, result: FuResult): Boolean {
        val tiles = hand.allTiles
        if (tiles.all { it.isHonor }) return true
        if (tiles.all { !it.isHonor && it.isTerminalOrHonor }) return true
        if (tiles.all { it in GREEN_TILES }) return true
        if (isNineGates(hand)) return true
        if (result.isChiitoitsu) return false

        val sets = result.melds.filter { it.kind != MeldKind.SEQUENCE }
        // ロンで完成した刻子は明刻（open）になっているので、暗刻には数えない
        if (sets.count { !it.open } == 4) return true
        if (sets.count { it.kind == MeldKind.QUAD } == 4) return true
        if (sets.count { it.tile.isDragon } == 3) return true
        val windSets = sets.count { it.tile.wind != null }
        return windSets == 4 || (windSets == 3 && result.pair?.wind != null)
    }

    /**
     * countedYaku に入らない役が付く形か。
     * 対々和・三暗刻・三槓子・混一色・清一色・混老頭・小三元・一盃口・二盃口・三色同順・三色同刻・
     * 一気通貫・チャンタ・純チャンを調べる。
     */
    fun hasUncountedYaku(hand: WinningHand, result: FuResult): Boolean {
        val tiles = hand.allTiles
        // 数牌が1種類だけなら混一色か清一色
        if (tiles.filter { !it.isHonor }.map { it.suit }.toSet().size == 1) return true
        if (tiles.all { it.isTerminalOrHonor }) return true
        if (result.isChiitoitsu) return false

        val sets = result.melds.filter { it.kind != MeldKind.SEQUENCE }
        val sequences = result.melds.filter { it.kind == MeldKind.SEQUENCE }
        if (sets.size == 4) return true
        if (sets.count { !it.open } >= 3) return true
        if (sets.count { it.kind == MeldKind.QUAD } >= 3) return true
        if (sets.count { it.tile.isDragon } == 2 && result.pair?.isDragon == true) return true
        if (hand.isClosed && sequences.groupingBy { it.tile }.eachCount().values.any { it >= 2 }) return true
        if (sequences.groupBy { it.tile.number }.values.any { group -> group.map { it.tile.suit }.toSet().size == 3 }) {
            return true
        }
        val numberSets = sets.filter { !it.tile.isHonor }
        if (numberSets.groupBy { it.tile.number }.values.any { group -> group.map { it.tile.suit }.toSet().size == 3 }) {
            return true
        }
        if (sequences.groupBy { it.tile.suit }.values.any { group -> group.map { it.tile.number }.containsAll(listOf(1, 4, 7)) }) {
            return true
        }
        // すべての面子と雀頭にヤオ九牌があればチャンタか純チャン
        val pair = result.pair
        return pair != null && pair.isTerminalOrHonor && result.melds.all { meld -> meld.tiles.any { it.isTerminalOrHonor } }
    }

    /** 九蓮宝燈: 門前で1種類の数牌だけ、1112345678999 に同じ種類の1枚を足した形 */
    private fun isNineGates(hand: WinningHand): Boolean {
        if (hand.calledMelds.isNotEmpty()) return false
        val tiles = hand.allTiles
        if (tiles.any { it.isHonor } || tiles.map { it.suit }.toSet().size != 1) return false
        val counts = IntArray(10).also { c -> tiles.forEach { c[it.number]++ } }
        return counts[1] >= 3 && counts[9] >= 3 && (2..8).all { counts[it] >= 1 }
    }
}
