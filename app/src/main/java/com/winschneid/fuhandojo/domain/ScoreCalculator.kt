package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Hand
import com.winschneid.fuhandojo.domain.model.Limit
import com.winschneid.fuhandojo.domain.model.Payment
import com.winschneid.fuhandojo.domain.model.Seat
import com.winschneid.fuhandojo.domain.model.WinMethod

/**
 * 符と翻から点数を求める。切り上げ満貫なし（30符4翻は 7700）の一般的なルール。
 */
object ScoreCalculator {

    /** 出題・計算の対象にする符（20符と25符以外は10符刻み） */
    val FU_VALUES = listOf(20, 25, 30, 40, 50, 60, 70, 80, 90, 100, 110)

    /** 符 × 2^(翻+2)。満貫の上限をかける前の値 */
    fun rawBasePoints(fu: Int, han: Int): Int = fu * (1 shl (han + 2))

    /** 満貫以上なら区分、そうでなければ null */
    fun limitOf(fu: Int, han: Int): Limit? =
        Limit.ofHan(han)
            ?: if (rawBasePoints(fu, han) >= Limit.MANGAN.basePoints) Limit.MANGAN else null

    fun basePoints(fu: Int, han: Int): Int =
        limitOf(fu, han)?.basePoints ?: rawBasePoints(fu, han)

    fun payment(hand: Hand): Payment {
        val base = basePoints(hand.fu, hand.han)
        return when (hand.method) {
            WinMethod.RON -> when (hand.seat) {
                Seat.NON_DEALER -> Payment.Ron(roundUp100(base * 4))
                Seat.DEALER -> Payment.Ron(roundUp100(base * 6))
            }
            WinMethod.TSUMO -> when (hand.seat) {
                Seat.NON_DEALER -> Payment.NonDealerTsumo(
                    fromNonDealer = roundUp100(base),
                    fromDealer = roundUp100(base * 2),
                )
                Seat.DEALER -> Payment.DealerTsumo(each = roundUp100(base * 2))
            }
        }
    }

    /**
     * 実際に和了り得る組み合わせか。
     * - 20符は平和ツモのみ（平和＋ツモで最低2翻）
     * - 25符は七対子のみ（最低2翻、ツモなら門前ツモが付いて最低3翻）
     */
    fun isValid(hand: Hand): Boolean {
        if (hand.fu !in FU_VALUES || hand.han < 1) return false
        return when (hand.fu) {
            20 -> hand.method == WinMethod.TSUMO && hand.han >= 2
            25 -> hand.han >= if (hand.method == WinMethod.TSUMO) 3 else 2
            else -> true
        }
    }

    fun roundUp100(points: Int): Int = (points + 99) / 100 * 100
}
