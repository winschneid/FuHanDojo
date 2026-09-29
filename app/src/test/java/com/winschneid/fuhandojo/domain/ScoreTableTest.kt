package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Seat
import com.winschneid.fuhandojo.domain.model.WinMethod
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreTableTest {

    private fun row(seat: Seat, method: WinMethod, fu: Int) =
        ScoreTable.rows(seat, method).single { it.fu == fu }.cells

    private fun labels(seat: Seat, method: WinMethod, fu: Int) = row(seat, method, fu).map { it?.label }

    @Test
    fun `子のロンの行`() {
        assertEquals(listOf(null, null, null, null), labels(Seat.NON_DEALER, WinMethod.RON, 20))
        assertEquals(listOf(null, "1600", "3200", "6400"), labels(Seat.NON_DEALER, WinMethod.RON, 25))
        assertEquals(listOf("1000", "2000", "3900", "7700"), labels(Seat.NON_DEALER, WinMethod.RON, 30))
        assertEquals(listOf("1300", "2600", "5200", "8000"), labels(Seat.NON_DEALER, WinMethod.RON, 40))
    }

    @Test
    fun `満貫になるマスだけ色を付ける`() {
        assertEquals(listOf(false, false, false, false), row(Seat.NON_DEALER, WinMethod.RON, 30).map { it?.isMangan })
        assertEquals(listOf(false, false, false, true), row(Seat.NON_DEALER, WinMethod.RON, 40).map { it?.isMangan })
        assertEquals(listOf(false, false, false, true), row(Seat.NON_DEALER, WinMethod.RON, 60).map { it?.isMangan })
        assertEquals(listOf(false, false, true, true), row(Seat.NON_DEALER, WinMethod.RON, 70).map { it?.isMangan })
    }

    @Test
    fun `親のツモはオールを省いて数字だけ`() {
        assertEquals(listOf(null, "700", "1300", "2600"), labels(Seat.DEALER, WinMethod.TSUMO, 20))
        assertEquals(listOf("500", "1000", "2000", "3900"), labels(Seat.DEALER, WinMethod.TSUMO, 30))
    }

    @Test
    fun `子のツモの25符は3翻から`() {
        assertEquals(listOf(null, null, "800-1600", "1600-3200"), labels(Seat.NON_DEALER, WinMethod.TSUMO, 25))
        assertEquals(listOf("300-500", "500-1000", "1000-2000", "2000-3900"), labels(Seat.NON_DEALER, WinMethod.TSUMO, 30))
    }

    @Test
    fun `満貫以上の表`() {
        assertEquals(
            listOf("8000", "12000", "16000", "24000", "32000"),
            ScoreTable.limitRows(Seat.NON_DEALER, WinMethod.RON).map { it.label },
        )
        assertEquals(
            listOf("4000オール", "6000オール", "8000オール", "12000オール", "16000オール"),
            ScoreTable.limitRows(Seat.DEALER, WinMethod.TSUMO).map { it.label },
        )
    }
}
