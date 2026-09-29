package com.winschneid.fuhandojo.domain.model

import kotlinx.serialization.Serializable

enum class Seat(val label: String) {
    NON_DEALER("子"),
    DEALER("親"),
}

enum class WinMethod(val label: String) {
    RON("ロン"),
    TSUMO("ツモ"),
}

/** 点数を決める和了の条件（誰が・どう和了ったか・符・翻） */
@Serializable
data class Hand(
    val seat: Seat,
    val method: WinMethod,
    val fu: Int,
    val han: Int,
)

/**
 * 満貫以上の区分。basePoints は符に関係なく固定される基本点。
 * minHan と hanRange は翻数だけで決まる範囲（満貫は4翻以下でも符によってなる）。
 */
enum class Limit(val label: String, val basePoints: Int, val minHan: Int, val hanRange: String) {
    MANGAN("満貫", 2000, 5, "5翻"),
    HANEMAN("跳満", 3000, 6, "6〜7翻"),
    BAIMAN("倍満", 4000, 8, "8〜10翻"),
    SANBAIMAN("三倍満", 6000, 11, "11〜12翻"),
    YAKUMAN("役満", 8000, 13, "13翻〜"),
    ;

    companion object {
        /** 翻数だけで決まる区分。4翻以下は符によるので null（数え役満あり） */
        fun ofHan(han: Int): Limit? = when {
            han >= 13 -> YAKUMAN
            han >= 11 -> SANBAIMAN
            han >= 8 -> BAIMAN
            han >= 6 -> HANEMAN
            han == 5 -> MANGAN
            else -> null
        }
    }
}

/** 和了者が受け取る点数の内訳 */
sealed interface Payment {
    /** 和了者が受け取る合計（供託・積み棒を除く） */
    val total: Int

    /** 点数申告の書き方（例: 3900 / 1000-2000 / 2000オール） */
    val label: String

    data class Ron(val points: Int) : Payment {
        override val total get() = points
        override val label get() = "$points"
    }

    /** 子のツモ。子2人と親1人がそれぞれ支払う */
    data class NonDealerTsumo(val fromNonDealer: Int, val fromDealer: Int) : Payment {
        override val total get() = fromNonDealer * 2 + fromDealer
        override val label get() = "$fromNonDealer-$fromDealer"
    }

    /** 親のツモ。子3人が同じ額を支払う */
    data class DealerTsumo(val each: Int) : Payment {
        override val total get() = each * 3
        override val label get() = "${each}オール"
    }
}
