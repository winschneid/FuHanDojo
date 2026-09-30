package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Hand
import com.winschneid.fuhandojo.domain.model.Seat
import com.winschneid.fuhandojo.domain.model.Seat.DEALER
import com.winschneid.fuhandojo.domain.model.Seat.NON_DEALER
import com.winschneid.fuhandojo.domain.model.WinMethod
import com.winschneid.fuhandojo.domain.model.WinMethod.RON
import com.winschneid.fuhandojo.domain.model.WinMethod.TSUMO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreCalculatorTest {

    private fun label(seat: Seat, method: WinMethod, fu: Int, han: Int) =
        ScoreCalculator.payment(Hand(seat, method, fu, han)).label

    @Test
    fun `子のロンは早見表どおり`() {
        val expected = mapOf(
            (30 to 1) to "1000", (30 to 2) to "2000", (30 to 3) to "3900", (30 to 4) to "7700",
            (40 to 1) to "1300", (40 to 2) to "2600", (40 to 3) to "5200", (40 to 4) to "8000",
            (25 to 2) to "1600", (25 to 3) to "3200", (25 to 4) to "6400",
            (60 to 3) to "7700", (70 to 3) to "8000", (110 to 1) to "3600",
        )
        expected.forEach { (fuHan, points) ->
            assertEquals("${fuHan.first}符${fuHan.second}翻", points, label(NON_DEALER, RON, fuHan.first, fuHan.second))
        }
    }

    @Test
    fun `親のロンは早見表どおり`() {
        val expected = mapOf(
            (30 to 1) to "1500", (30 to 2) to "2900", (30 to 3) to "5800", (30 to 4) to "11600",
            (40 to 1) to "2000", (40 to 3) to "7700", (40 to 4) to "12000",
            (25 to 2) to "2400", (25 to 4) to "9600",
        )
        expected.forEach { (fuHan, points) ->
            assertEquals("${fuHan.first}符${fuHan.second}翻", points, label(DEALER, RON, fuHan.first, fuHan.second))
        }
    }

    @Test
    fun `子のツモは子と親の支払いに分かれる`() {
        val expected = mapOf(
            (30 to 1) to "300-500", (30 to 2) to "500-1000", (30 to 3) to "1000-2000", (30 to 4) to "2000-3900",
            (20 to 2) to "400-700", (20 to 3) to "700-1300", (20 to 4) to "1300-2600",
            (25 to 3) to "800-1600", (40 to 1) to "400-700",
        )
        expected.forEach { (fuHan, points) ->
            assertEquals("${fuHan.first}符${fuHan.second}翻", points, label(NON_DEALER, TSUMO, fuHan.first, fuHan.second))
        }
    }

    @Test
    fun `親のツモはオール`() {
        val expected = mapOf(
            (30 to 1) to "500オール", (30 to 4) to "3900オール",
            (20 to 2) to "700オール", (20 to 4) to "2600オール", (25 to 3) to "1600オール",
        )
        expected.forEach { (fuHan, points) ->
            assertEquals("${fuHan.first}符${fuHan.second}翻", points, label(DEALER, TSUMO, fuHan.first, fuHan.second))
        }
    }

    @Test
    fun `満貫以上は符に関係なく決まる`() {
        val hans = listOf(5, 6, 8, 11, 13)
        assertEquals(listOf("8000", "12000", "16000", "24000", "32000"), hans.map { label(NON_DEALER, RON, 30, it) })
        assertEquals(listOf("12000", "18000", "24000", "36000", "48000"), hans.map { label(DEALER, RON, 30, it) })
        assertEquals(
            listOf("2000-4000", "3000-6000", "4000-8000", "6000-12000", "8000-16000"),
            hans.map { label(NON_DEALER, TSUMO, 30, it) },
        )
        assertEquals(
            listOf("4000オール", "6000オール", "8000オール", "12000オール", "16000オール"),
            hans.map { label(DEALER, TSUMO, 30, it) },
        )
        assertEquals("12000", label(NON_DEALER, RON, 110, 7))
    }

    @Test
    fun `20符はツモの2翻以上、25符はロン2翻・ツモ3翻以上だけ有効`() {
        assertFalse(ScoreCalculator.isValid(Hand(NON_DEALER, RON, 20, 2)))
        assertFalse(ScoreCalculator.isValid(Hand(NON_DEALER, TSUMO, 20, 1)))
        assertTrue(ScoreCalculator.isValid(Hand(NON_DEALER, TSUMO, 20, 2)))
        assertFalse(ScoreCalculator.isValid(Hand(NON_DEALER, RON, 25, 1)))
        assertTrue(ScoreCalculator.isValid(Hand(NON_DEALER, RON, 25, 2)))
        assertFalse(ScoreCalculator.isValid(Hand(DEALER, TSUMO, 25, 2)))
        assertTrue(ScoreCalculator.isValid(Hand(DEALER, TSUMO, 25, 3)))
        assertFalse(ScoreCalculator.isValid(Hand(NON_DEALER, RON, 35, 1)))
        assertFalse(ScoreCalculator.isValid(Hand(NON_DEALER, RON, 30, 0)))
        // 110符は三暗刻か三槓子が付くので1翻では起こらない
        assertFalse(ScoreCalculator.isValid(Hand(NON_DEALER, RON, 110, 1)))
        assertTrue(ScoreCalculator.isValid(Hand(NON_DEALER, RON, 110, 2)))
    }
}
