package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Meld
import com.winschneid.fuhandojo.domain.model.MeldKind
import com.winschneid.fuhandojo.domain.model.Suit
import com.winschneid.fuhandojo.domain.model.Tile
import com.winschneid.fuhandojo.domain.model.Wait
import com.winschneid.fuhandojo.domain.model.WinMethod
import com.winschneid.fuhandojo.domain.model.WinMethod.RON
import com.winschneid.fuhandojo.domain.model.WinMethod.TSUMO
import com.winschneid.fuhandojo.domain.model.WinningHand
import com.winschneid.fuhandojo.domain.model.Wind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuCalculatorTest {

    private fun m(vararg n: Int) = n.map { Tile(Suit.MAN, it) }
    private fun p(vararg n: Int) = n.map { Tile(Suit.PIN, it) }
    private fun s(vararg n: Int) = n.map { Tile(Suit.SOU, it) }
    private fun z(vararg n: Int) = n.map { Tile(Suit.HONOR, it) }

    private fun hand(
        concealed: List<Tile>,
        win: Tile,
        method: WinMethod,
        called: List<Meld> = emptyList(),
        round: Wind = Wind.EAST,
        seat: Wind = Wind.SOUTH,
    ) = WinningHand(concealed.sorted(), called, win, method, round, seat)

    private fun fu(hand: WinningHand) = FuCalculator.calculate(hand)!!

    // 234萬 567筒 789索 34索 55萬 で 5索待ち（両面）
    private val pinfuShape = m(2, 3, 4) + p(5, 6, 7) + s(7, 8, 9) + s(3, 4) + m(5, 5)

    @Test
    fun `平和ロンは30符、平和ツモは20符`() {
        val ron = fu(hand(pinfuShape, s(5)[0], RON))
        assertEquals(30, ron.fu)
        assertTrue(ron.isPinfu)
        val tsumo = fu(hand(pinfuShape, s(5)[0], TSUMO))
        assertEquals(20, tsumo.fu)
        assertTrue(tsumo.isPinfu)
    }

    @Test
    fun `嵌張ロンは 20 + 門前ロン10 + 嵌張2 = 32 → 40符`() {
        val result = fu(hand(m(2, 3, 4) + p(5, 6, 7) + s(7, 8, 9) + s(3, 5) + m(5, 5), s(4)[0], RON))
        assertEquals(Wait.KANCHAN, result.wait)
        assertEquals(32, result.raw)
        assertEquals(40, result.fu)
        assertFalse(result.isPinfu)
    }

    @Test
    fun `辺張は 12で3 と 89で7`() {
        assertEquals(Wait.PENCHAN, FuCalculator.waitOf(Meld(MeldKind.SEQUENCE, m(1)[0], false), m(3)[0]))
        assertEquals(Wait.PENCHAN, FuCalculator.waitOf(Meld(MeldKind.SEQUENCE, m(7)[0], false), m(7)[0]))
        assertEquals(Wait.RYANMEN, FuCalculator.waitOf(Meld(MeldKind.SEQUENCE, m(2)[0], false), m(2)[0]))
        assertEquals(Wait.RYANMEN, FuCalculator.waitOf(Meld(MeldKind.SEQUENCE, m(7)[0], false), m(9)[0]))
    }

    @Test
    fun `中をポンした手のロンは 20 + 明刻ヤオ九牌4 = 24 → 30符`() {
        val result = fu(
            hand(
                concealed = m(2, 3, 4) + p(5, 6, 7) + s(3, 4) + m(5, 5),
                win = s(5)[0],
                method = RON,
                called = listOf(Meld(MeldKind.TRIPLET, z(7)[0], open = true)),
            ),
        )
        assertEquals(24, result.raw)
        assertEquals(30, result.fu)
    }

    @Test
    fun `鳴いた手で符が無いロンは30符`() {
        val result = fu(
            hand(
                concealed = p(4, 5, 6) + s(6, 7, 8) + s(3, 4) + p(8, 8),
                win = s(5)[0],
                method = RON,
                called = listOf(Meld(MeldKind.SEQUENCE, m(2)[0], open = true)),
            ),
        )
        assertEquals(20, result.raw)
        assertEquals(30, result.fu)
        assertFalse(result.isPinfu)
    }

    @Test
    fun `双碰のロンで完成した刻子は明刻、ツモなら暗刻`() {
        val concealed = m(2, 3, 4) + m(6, 7, 8) + s(4, 5, 6) + p(9, 9) + s(2, 2)
        val ron = fu(hand(concealed, s(2)[0], RON))
        assertEquals(Wait.SHANPON, ron.wait)
        assertEquals(32, ron.raw) // 20 + 門前ロン10 + 明刻2
        assertEquals(40, ron.fu)
        val tsumo = fu(hand(concealed, s(2)[0], TSUMO))
        assertEquals(26, tsumo.raw) // 20 + ツモ2 + 暗刻4
        assertEquals(30, tsumo.fu)
    }

    @Test
    fun `1萬の暗槓は32符`() {
        val result = fu(
            hand(
                concealed = p(2, 3, 4) + p(5, 6, 7) + s(3, 4) + s(7, 7),
                win = s(5)[0],
                method = RON,
                called = listOf(Meld(MeldKind.QUAD, m(1)[0], open = false)),
            ),
        )
        assertEquals(62, result.raw) // 20 + 門前ロン10 + 暗槓ヤオ九牌32
        assertEquals(70, result.fu)
    }

    @Test
    fun `単騎待ちと暗刻`() {
        // 123萬 456萬 789筒 222索 西 で西単騎（南家・東場なので西は役牌ではない）
        val result = fu(hand(m(1, 2, 3) + m(4, 5, 6) + p(7, 8, 9) + s(2, 2, 2) + z(3), z(3)[0], RON))
        assertEquals(Wait.TANKI, result.wait)
        assertEquals(36, result.raw) // 20 + 10 + 暗刻4 + 単騎2
        assertEquals(40, result.fu)
    }

    @Test
    fun `役牌の雀頭は2符、連風牌は4符`() {
        assertEquals(2, FuCalculator.pairFu(z(7)[0], Wind.EAST, Wind.SOUTH))
        assertEquals(2, FuCalculator.pairFu(z(2)[0], Wind.EAST, Wind.SOUTH))
        assertEquals(2, FuCalculator.pairFu(z(1)[0], Wind.EAST, Wind.SOUTH))
        assertEquals(0, FuCalculator.pairFu(z(3)[0], Wind.EAST, Wind.SOUTH))
        assertEquals(4, FuCalculator.pairFu(z(1)[0], Wind.EAST, Wind.EAST))
        assertEquals(0, FuCalculator.pairFu(m(5)[0], Wind.EAST, Wind.SOUTH))
    }

    @Test
    fun `七対子は25符`() {
        val pairs = m(1, 1, 9, 9) + p(3, 3, 7, 7) + s(2, 2, 5, 5) + z(6)
        val result = fu(hand(pairs, z(6)[0], TSUMO))
        assertTrue(result.isChiitoitsu)
        assertEquals(25, result.fu)
    }

    @Test
    fun `面子の分け方が複数ある形はすべて列挙する`() {
        // 111222333萬 は 刻子3つ とも 順子3つ とも取れる
        val decompositions = FuCalculator.decompose(m(1, 1, 1, 2, 2, 2, 3, 3, 3) + p(5, 5, 6, 7, 8))
        assertEquals(2, decompositions.size)
    }

    @Test
    fun `面子の符は明刻2・暗刻4・明槓8・暗槓16、ヤオ九牌は2倍`() {
        val five = m(5)[0]
        val nine = m(9)[0]
        assertEquals(2, Meld(MeldKind.TRIPLET, five, open = true).fu)
        assertEquals(4, Meld(MeldKind.TRIPLET, five, open = false).fu)
        assertEquals(8, Meld(MeldKind.QUAD, five, open = true).fu)
        assertEquals(16, Meld(MeldKind.QUAD, five, open = false).fu)
        assertEquals(4, Meld(MeldKind.TRIPLET, nine, open = true).fu)
        assertEquals(32, Meld(MeldKind.QUAD, z(1)[0], open = false).fu)
        assertEquals(0, Meld(MeldKind.SEQUENCE, m(1)[0], open = false).fu)
    }
}
