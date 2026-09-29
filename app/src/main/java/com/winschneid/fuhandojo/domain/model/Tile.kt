package com.winschneid.fuhandojo.domain.model

import kotlinx.serialization.Serializable

enum class Suit(val label: String) {
    MAN("萬"),
    PIN("筒"),
    SOU("索"),
    HONOR(""),
}

/**
 * 麻雀牌。字牌の number は 1〜7 が 東・南・西・北・白・發・中。
 */
@Serializable
data class Tile(val suit: Suit, val number: Int) : Comparable<Tile> {

    init {
        require(if (suit == Suit.HONOR) number in 1..7 else number in 1..9) { "不正な牌: $suit $number" }
    }

    val isHonor: Boolean get() = suit == Suit.HONOR
    val isDragon: Boolean get() = isHonor && number >= 5
    val wind: Wind? get() = if (isHonor && number <= 4) Wind.entries[number - 1] else null

    /** ヤオ九牌（1・9・字牌） */
    val isTerminalOrHonor: Boolean get() = isHonor || number == 1 || number == 9

    /** 0〜33 の通し番号。牌の枚数を数えるときに使う */
    val index: Int get() = suit.ordinal * 9 + number - 1

    val label: String get() = if (isHonor) HONOR_LABELS[number - 1] else "$number${suit.label}"

    /** 同じ種類で数字が n 大きい牌。範囲外や字牌なら null */
    fun plus(n: Int): Tile? =
        if (isHonor || number + n !in 1..9) null else Tile(suit, number + n)

    override fun compareTo(other: Tile): Int = index.compareTo(other.index)

    override fun toString(): String = label

    companion object {
        private val HONOR_LABELS = listOf("東", "南", "西", "北", "白", "發", "中")

        fun fromIndex(index: Int): Tile =
            if (index >= 27) Tile(Suit.HONOR, index - 27 + 1) else Tile(Suit.entries[index / 9], index % 9 + 1)

        fun wind(wind: Wind) = Tile(Suit.HONOR, wind.ordinal + 1)

        val ALL: List<Tile> = (0 until 34).map(::fromIndex)
    }
}

enum class Wind(val label: String) {
    EAST("東"),
    SOUTH("南"),
    WEST("西"),
    NORTH("北"),
}

enum class MeldKind { SEQUENCE, TRIPLET, QUAD }

/**
 * 面子。tile は順子なら一番小さい牌、刻子・槓子ならその牌。
 * open は鳴いた（ポン・チー・明槓）かどうか。暗槓は open = false で手の外に出ている。
 */
@Serializable
data class Meld(val kind: MeldKind, val tile: Tile, val open: Boolean) {

    init {
        if (kind == MeldKind.SEQUENCE) require(!tile.isHonor && tile.number <= 7) { "不正な順子: $tile" }
    }

    val tiles: List<Tile>
        get() = when (kind) {
            MeldKind.SEQUENCE -> listOf(tile, tile.plus(1)!!, tile.plus(2)!!)
            MeldKind.TRIPLET -> List(3) { tile }
            MeldKind.QUAD -> List(4) { tile }
        }

    /** 牌姿の呼び方（チー・ポン・明槓・暗槓・暗刻・順子） */
    val label: String
        get() = when (kind) {
            MeldKind.SEQUENCE -> if (open) "チー" else "順子"
            MeldKind.TRIPLET -> if (open) "ポン" else "暗刻"
            MeldKind.QUAD -> if (open) "明槓" else "暗槓"
        }

    /** 面子の符。順子は0、刻子は明2・暗4、槓子は明8・暗16、ヤオ九牌ならさらに2倍 */
    val fu: Int
        get() {
            val base = when (kind) {
                MeldKind.SEQUENCE -> return 0
                MeldKind.TRIPLET -> if (open) 2 else 4
                MeldKind.QUAD -> if (open) 8 else 16
            }
            return if (tile.isTerminalOrHonor) base * 2 else base
        }
}

enum class Wait(val label: String, val fu: Int) {
    RYANMEN("両面", 0),
    KANCHAN("嵌張", 2),
    PENCHAN("辺張", 2),
    TANKI("単騎", 2),
    SHANPON("双碰", 0),
}

/**
 * 和了った手。
 * concealed は手の中の牌（和了牌を除く）、calledMelds は鳴いた面子と暗槓。
 */
@Serializable
data class WinningHand(
    val concealed: List<Tile>,
    val calledMelds: List<Meld>,
    val winningTile: Tile,
    val method: WinMethod,
    val roundWind: Wind,
    val seatWind: Wind,
) {
    /** 門前（鳴いていない。暗槓は門前のまま） */
    val isClosed: Boolean get() = calledMelds.none { it.open }

    val seat: Seat get() = if (seatWind == Wind.EAST) Seat.DEALER else Seat.NON_DEALER

    val allTiles: List<Tile> get() = concealed + winningTile + calledMelds.flatMap { it.tiles }
}
