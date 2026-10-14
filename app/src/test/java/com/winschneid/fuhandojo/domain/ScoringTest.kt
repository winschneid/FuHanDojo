package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Limit
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
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {

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

    private fun success(hand: WinningHand, context: WinContext = WinContext()): ScoreResult {
        val outcome = Scoring.score(hand, context)
        assertTrue("$outcome", outcome is ScoreOutcome.Success)
        return (outcome as ScoreOutcome.Success).result
    }

    private fun names(result: ScoreResult) = result.yaku.map { it.name }.toSet()

    @Test
    fun `リーチ・ツモ・平和・ドラ1は20符4翻で1300-2600`() {
        val result = success(
            hand(m(2, 3, 4) + p(5, 6, 7) + s(7, 8, 9) + s(3, 4) + m(5, 5), s(5)[0], TSUMO),
            WinContext(riichi = Riichi.RIICHI, dora = 1),
        )
        assertEquals(setOf("立直", "門前清自摸和", "平和", "ドラ1"), names(result))
        assertEquals(20, result.fu)
        assertEquals(4, result.han)
        assertEquals("1300-2600", result.payment.label)
    }

    @Test
    fun `喰いタンは鳴いて符がなくても30符1翻で1000点`() {
        val result = success(
            hand(p(4, 5, 6) + s(6, 7, 8) + s(3, 4) + p(8, 8), s(5)[0], RON, listOf(Meld(MeldKind.SEQUENCE, m(2)[0], true))),
        )
        assertEquals(setOf("断么九"), names(result))
        assertEquals(30, result.fu)
        assertEquals("1000", result.payment.label)
    }

    @Test
    fun `役がない手はドラがあっても和了れない`() {
        val outcome = Scoring.score(
            hand(p(4, 5, 6) + s(7, 8, 9) + s(3, 4) + p(5, 5), s(5)[0], RON, listOf(Meld(MeldKind.SEQUENCE, m(1)[0], true))),
            WinContext(dora = 3),
        )
        assertEquals(ScoreOutcome.NoYaku, outcome)
    }

    @Test
    fun `清一色と一気通貫で倍満`() {
        // 123 456 789 333萬 + 5萬単騎
        val result = success(hand(m(1, 2, 3, 4, 5, 6, 7, 8, 9, 3, 3, 3, 5), m(5)[0], RON))
        assertTrue(names(result).containsAll(listOf("清一色", "一気通貫")))
        assertEquals(Limit.BAIMAN, result.limit)
        assertEquals("16000", result.payment.label)
    }

    @Test
    fun `国士無双は役満`() {
        val result = success(hand(m(1, 9) + p(1, 9) + s(1, 9) + z(1, 2, 3, 4, 5, 6, 7), z(7)[0], RON))
        assertEquals(setOf("国士無双"), names(result))
        assertEquals(1, result.yakumanCount)
        assertEquals("32000", result.payment.label)
    }

    @Test
    fun `四暗刻のツモは親なら16000オール`() {
        val result = success(
            hand(m(2, 2, 2) + p(5, 5, 5) + s(8, 8, 8) + z(3, 3) + p(9, 9), z(3)[0], TSUMO, seat = Wind.EAST),
        )
        assertEquals(setOf("四暗刻"), names(result))
        assertEquals("16000オール", result.payment.label)
    }

    @Test
    fun `役満は複合する（大三元・字一色・四暗刻の単騎）`() {
        val result = success(hand(z(5, 5, 5, 6, 6, 6, 7, 7, 7, 1, 1, 1, 2), z(2)[0], RON))
        assertEquals(setOf("大三元", "字一色", "四暗刻"), names(result))
        assertEquals(3, result.yakumanCount)
        assertEquals("96000", result.payment.label)
    }

    @Test
    fun `リーチ・七対子・断么九は25符4翻で6400`() {
        val result = success(
            hand(m(2, 2, 4, 4, 6, 6) + p(3, 3, 5, 5) + s(7, 7) + s(8), s(8)[0], RON),
            WinContext(riichi = Riichi.RIICHI),
        )
        assertEquals(setOf("立直", "七対子", "断么九"), names(result))
        assertEquals(25, result.fu)
        assertEquals("6400", result.payment.label)
    }

    @Test
    fun `二盃口と七対子に取れる手は点数の高い二盃口を採用する`() {
        val result = success(hand(m(2, 2, 3, 3, 4, 4) + p(5, 5, 6, 6, 7, 7) + s(9), s(9)[0], RON))
        assertEquals(setOf("二盃口"), names(result))
        assertEquals(40, result.fu)
        assertEquals("5200", result.payment.label)
    }

    @Test
    fun `本場はロン1本につき300点を足す`() {
        // 三色同順（123）+ ペンチャン: 20 + 門前ロン10 + ペンチャン2 = 32 → 40符2翻 2600 + 2本場
        val result = success(
            hand(m(1, 2, 3) + p(1, 2, 3) + s(1, 2) + s(7, 8, 9) + p(5, 5), s(3)[0], RON),
            WinContext(honba = 2),
        )
        assertEquals(setOf("三色同順"), names(result))
        assertEquals(40, result.fu)
        assertEquals("3200", result.payment.label)
    }

    @Test
    fun `数え役満`() {
        val result = success(
            hand(m(1, 2, 3, 4, 5, 6, 7, 8, 9, 3, 3, 3, 5), m(5)[0], TSUMO),
            WinContext(riichi = Riichi.RIICHI, dora = 3),
        )
        // 立直1 + ツモ1 + 清一色6 + 一気通貫2 + ドラ3 = 13翻
        assertEquals(13, result.han)
        assertEquals(0, result.yakumanCount)
        assertEquals(Limit.YAKUMAN, result.limit)
        assertEquals("8000-16000", result.payment.label)
    }

    @Test
    fun `海底摸月・嶺上開花・槍槓・一発などの状況役`() {
        val haitei = success(
            hand(m(2, 3, 4) + p(5, 6, 7) + s(7, 8, 9) + s(3, 4) + m(5, 5), s(5)[0], TSUMO),
            WinContext(riichi = Riichi.DOUBLE, ippatsu = true, lastTile = true),
        )
        assertTrue(names(haitei).containsAll(listOf("ダブル立直", "一発", "海底摸月")))
        val rinshan = success(
            hand(m(2, 3, 4) + p(5, 6, 7) + s(3, 4) + m(5, 5), s(5)[0], TSUMO, listOf(Meld(MeldKind.QUAD, z(7)[0], true))),
            WinContext(rinshan = true),
        )
        assertTrue(names(rinshan).containsAll(listOf("嶺上開花", "役牌 中")))
    }

    @Test
    fun `ありえない入力`() {
        val open = hand(p(4, 5, 6) + s(6, 7, 8) + s(3, 4) + p(8, 8), s(5)[0], RON, listOf(Meld(MeldKind.SEQUENCE, m(2)[0], true)))
        assertTrue(Scoring.score(open, WinContext(riichi = Riichi.RIICHI)) is ScoreOutcome.Invalid)
        val closed = hand(m(2, 3, 4) + p(5, 6, 7) + s(7, 8, 9) + s(3, 4) + m(5, 5), s(5)[0], RON)
        assertTrue(Scoring.score(closed, WinContext(ippatsu = true)) is ScoreOutcome.Invalid)
        assertTrue(Scoring.score(closed, WinContext(rinshan = true)) is ScoreOutcome.Invalid)
        assertEquals(ScoreOutcome.Incomplete(14, 13), Scoring.score(closed.copy(concealed = closed.concealed.drop(1)), WinContext()))
        val notWinning = closed.copy(concealed = closed.concealed.dropLast(1) + z(1))
        assertEquals(ScoreOutcome.NotWinningShape, Scoring.score(notWinning, WinContext()))
    }
}
