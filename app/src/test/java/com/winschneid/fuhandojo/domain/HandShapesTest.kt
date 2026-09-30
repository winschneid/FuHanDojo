package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Meld
import com.winschneid.fuhandojo.domain.model.MeldKind
import com.winschneid.fuhandojo.domain.model.Suit
import com.winschneid.fuhandojo.domain.model.Tile
import com.winschneid.fuhandojo.domain.model.WinMethod
import com.winschneid.fuhandojo.domain.model.WinMethod.RON
import com.winschneid.fuhandojo.domain.model.WinMethod.TSUMO
import com.winschneid.fuhandojo.domain.model.WinningHand
import com.winschneid.fuhandojo.domain.model.Wind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandShapesTest {

    private fun m(vararg n: Int) = n.map { Tile(Suit.MAN, it) }
    private fun p(vararg n: Int) = n.map { Tile(Suit.PIN, it) }
    private fun s(vararg n: Int) = n.map { Tile(Suit.SOU, it) }
    private fun z(vararg n: Int) = n.map { Tile(Suit.HONOR, it) }

    private fun hand(
        concealed: List<Tile>,
        win: Tile,
        method: WinMethod = RON,
        called: List<Meld> = emptyList(),
    ) = WinningHand(concealed.sorted(), called, win, method, Wind.EAST, Wind.SOUTH)

    private fun uncounted(hand: WinningHand) =
        FuCalculator.interpretations(hand).any { HandShapes.hasUncountedYaku(hand, it) }

    private fun yakuman(hand: WinningHand) =
        FuCalculator.interpretations(hand).any { HandShapes.isYakumanShape(hand, it) }

    private fun yakuNames(hand: WinningHand) =
        HandShapes.countedYaku(hand, FuCalculator.calculate(hand)!!).map { it.name }

    @Test
    fun `数える役だけの手は、ほかの役なしと判定する`() {
        // 234萬 567筒 789索 34索 55萬 + 5索（平和）
        val pinfu = hand(m(2, 3, 4) + p(5, 6, 7) + s(7, 8, 9) + s(3, 4) + m(5, 5), s(5)[0], TSUMO)
        assertFalse(uncounted(pinfu))
        assertFalse(yakuman(pinfu))
        assertEquals(listOf("リーチ", "ツモ", "平和"), yakuNames(pinfu))

        // 中をポンした手
        val yakuhai = hand(m(2, 3, 4) + p(5, 6, 7) + s(3, 4) + m(8, 8), s(5)[0], RON, listOf(Meld(MeldKind.TRIPLET, z(7)[0], true)))
        assertFalse(uncounted(yakuhai))
        assertEquals(listOf("役牌 中"), yakuNames(yakuhai))

        // タンヤオの鳴いた手
        val tanyao = hand(p(4, 5, 6) + s(6, 7, 8) + s(3, 4) + p(8, 8), s(5)[0], RON, listOf(Meld(MeldKind.SEQUENCE, m(2)[0], true)))
        assertFalse(uncounted(tanyao))
        assertEquals(listOf("タンヤオ"), yakuNames(tanyao))
    }

    @Test
    fun `三暗刻と対々和`() {
        // 222萬 555筒 888索 の暗刻3つ + 234索 + 西単騎
        assertTrue(uncounted(hand(m(2, 2, 2) + p(5, 5, 5) + s(8, 8, 8) + s(2, 3, 4) + z(3), z(3)[0])))
        // ロンで完成した刻子は暗刻に数えないので、暗刻2つ + 明刻1つは三暗刻ではない
        assertFalse(uncounted(hand(m(2, 2, 2) + p(5, 5, 5) + s(8, 8) + s(2, 3, 4) + p(9, 9), s(8)[0], RON)))
        // 同じ形でもツモなら暗刻3つ
        assertTrue(uncounted(hand(m(2, 2, 2) + p(5, 5, 5) + s(8, 8) + s(2, 3, 4) + p(9, 9), s(8)[0], TSUMO)))
        // 鳴いて刻子4つ
        val toitoi = hand(
            m(2, 2, 2) + z(3),
            z(3)[0],
            RON,
            listOf(
                Meld(MeldKind.TRIPLET, p(5)[0], true),
                Meld(MeldKind.TRIPLET, s(8)[0], true),
                Meld(MeldKind.TRIPLET, z(7)[0], true),
            ),
        )
        assertTrue(uncounted(toitoi))
    }

    @Test
    fun `染め手・一盃口・三色・一気通貫・チャンタ`() {
        // 混一色: 萬子と字牌だけ
        assertTrue(uncounted(hand(m(1, 2, 3) + m(4, 5, 6) + m(7, 8) + z(1, 1, 1) + z(3, 3), m(9)[0])))
        // 一盃口: 234萬が2組
        assertTrue(uncounted(hand(m(2, 2, 3, 3, 4, 4) + p(5, 6, 7) + s(3, 4) + p(9, 9), s(5)[0])))
        // 鳴いていれば一盃口は付かない
        assertFalse(
            uncounted(
                hand(m(2, 3, 4) + m(2, 3, 4) + s(3, 4) + p(8, 8), s(5)[0], RON, listOf(Meld(MeldKind.TRIPLET, z(7)[0], true))),
            ),
        )
        // 三色同順: 234 が萬・筒・索
        assertTrue(uncounted(hand(m(2, 3, 4) + p(2, 3, 4) + s(2, 3) + s(6, 7, 8) + p(9, 9), s(4)[0])))
        // 三色同刻: 5 の刻子が萬・筒・索
        assertTrue(
            uncounted(
                hand(
                    m(5, 5, 5) + s(2, 3) + z(3, 3),
                    s(4)[0],
                    RON,
                    listOf(Meld(MeldKind.TRIPLET, p(5)[0], true), Meld(MeldKind.TRIPLET, s(5)[0], true)),
                ),
            ),
        )
        // 一気通貫: 筒子の 123 456 789
        assertTrue(uncounted(hand(p(1, 2, 3) + p(4, 5, 6) + p(7, 8) + m(2, 3, 4) + s(5, 5), p(9)[0])))
        // チャンタ: すべての面子と雀頭にヤオ九牌
        assertTrue(uncounted(hand(m(1, 2, 3) + p(7, 8, 9) + s(1, 2) + z(1, 1, 1) + m(9, 9), s(3)[0])))
    }

    @Test
    fun `役満の形`() {
        // 四暗刻（ツモ）
        assertTrue(yakuman(hand(m(2, 2, 2) + p(5, 5, 5) + s(8, 8, 8) + z(3, 3) + p(9, 9), z(3)[0], TSUMO)))
        // 同じ形のロンは、ロンで完成した刻子が明刻なので四暗刻ではない（三暗刻・対々和）
        val ron = hand(m(2, 2, 2) + p(5, 5, 5) + s(8, 8, 8) + z(3, 3) + p(9, 9), z(3)[0], RON)
        assertFalse(yakuman(ron))
        assertTrue(uncounted(ron))
        // 四暗刻単騎はロンでも役満
        assertTrue(yakuman(hand(m(2, 2, 2) + p(5, 5, 5) + s(8, 8, 8) + z(3, 3, 3) + p(9), p(9)[0], RON)))
        // 大三元
        val daisangen = hand(
            m(2, 3, 4) + z(7, 7) + p(9, 9),
            z(7)[0],
            RON,
            listOf(Meld(MeldKind.TRIPLET, z(5)[0], true), Meld(MeldKind.TRIPLET, z(6)[0], true)),
        )
        assertTrue(yakuman(daisangen))
        // 九蓮宝燈
        assertTrue(yakuman(hand(m(1, 1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 9, 9), m(5)[0], TSUMO)))
        // 緑一色
        assertTrue(yakuman(hand(s(2, 3, 4) + s(2, 3, 4) + s(6, 6, 6) + s(8, 8) + z(6, 6), z(6)[0], RON)))
    }
}
