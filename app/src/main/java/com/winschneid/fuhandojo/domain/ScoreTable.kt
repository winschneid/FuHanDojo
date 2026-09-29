package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Hand
import com.winschneid.fuhandojo.domain.model.Limit
import com.winschneid.fuhandojo.domain.model.Payment
import com.winschneid.fuhandojo.domain.model.Seat
import com.winschneid.fuhandojo.domain.model.WinMethod

/** 点数早見表のデータ。値はクイズと同じ ScoreCalculator から作る */
object ScoreTable {

    val HANS = 1..4

    /** 表の1マス。isMangan は符と翻から満貫になるマス */
    data class Cell(val label: String, val isMangan: Boolean)

    /** 符ごとの1行。存在しない組み合わせ（20符のロンなど）は null */
    data class Row(val fu: Int, val cells: List<Cell?>)

    data class LimitRow(val limit: Limit, val label: String)

    fun rows(seat: Seat, method: WinMethod): List<Row> = ScoreCalculator.FU_VALUES.map { fu ->
        Row(
            fu = fu,
            cells = HANS.map { han ->
                val hand = Hand(seat, method, fu, han)
                if (!ScoreCalculator.isValid(hand)) {
                    null
                } else {
                    Cell(cellLabel(ScoreCalculator.payment(hand)), ScoreCalculator.limitOf(fu, han) != null)
                }
            },
        )
    }

    fun limitRows(seat: Seat, method: WinMethod): List<LimitRow> = Limit.entries.map { limit ->
        LimitRow(limit, ScoreCalculator.payment(Hand(seat, method, 30, limit.minHan)).label)
    }

    /** 表のマスは幅が狭いので、親のツモは「オール」を省いて数字だけにする（見出しで説明する） */
    private fun cellLabel(payment: Payment): String = when (payment) {
        is Payment.DealerTsumo -> "${payment.each}"
        else -> payment.label
    }
}
