package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Meld
import com.winschneid.fuhandojo.domain.model.MeldKind
import com.winschneid.fuhandojo.domain.model.Suit
import com.winschneid.fuhandojo.domain.model.Tile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 画像認識が返す JSON（スキーマは TileRecognition.schema） */
@Serializable
data class RecognizedHand(
    val tiles: List<String>,
    val melds: List<RecognizedMeld> = emptyList(),
    @SerialName("winning_tile_separated") val winningTileSeparated: Boolean = false,
    val notes: String = "",
)

@Serializable
data class RecognizedMeld(val type: String, val tiles: List<String>)

/**
 * 読み取り結果をアプリの牌に直したもの。tiles は手の中の牌で、最後の1枚がアガリ牌。
 * @param warnings 確かめてほしい点（アガリ牌がわからなかった、面子の形がおかしい、など）
 */
data class RecognitionResult(
    val tiles: List<Tile>,
    val melds: List<Meld>,
    val akaDora: Int,
    val warnings: List<String>,
)

/**
 * 写真から牌を読み取るための指示と、返ってきた JSON の解釈。
 * 牌は「数字＋種類」で表す（m 萬子、p 筒子、s 索子、z 字牌 1東 2南 3西 4北 5白 6發 7中、0m/0p/0s は赤5）。
 */
object TileRecognition {

    val TILE_CODES: List<String> =
        listOf("m", "p", "s").flatMap { suit -> (1..9).map { "$it$suit" } } +
            (1..7).map { "${it}z" } +
            listOf("0m", "0p", "0s")

    val MELD_TYPES = listOf("chi", "pon", "minkan", "ankan")

    val PROMPT = """
        This photo shows a Japanese riichi mahjong winning hand. Read every tile in the player's hand and return it as JSON.

        Tile notation: a digit followed by a suit letter.
        - m = characters (manzu, 萬), p = circles (pinzu, 筒), s = bamboo (souzu, 索)
        - z = honors: 1z East 東, 2z South 南, 3z West 西, 4z North 北, 5z White dragon 白 (blank or framed tile), 6z Green dragon 發, 7z Red dragon 中
        - A red five (aka dora) is 0m, 0p or 0s.

        Rules:
        - "tiles": the concealed tiles standing in the hand, from left to right. Do not include tiles that belong to called melds.
        - If one tile is set slightly apart from the rest (usually on the right), it is the winning tile: put it LAST in "tiles" and set "winning_tile_separated" to true. Otherwise set it to false.
        - "melds": called melds and concealed kans, usually placed to the right with one tile turned sideways.
          "chi" = called run, "pon" = called triplet, "minkan" = open kan (4 tiles, one sideways), "ankan" = concealed kan (4 tiles, often with the two end tiles face down; still list the tile it shows).
        - List exactly the tiles you see. Do not add or drop tiles to make the hand look complete.
        - Ignore discards, dora indicators, sticks and anything that is not part of this hand.
        - "notes": in Japanese, briefly mention anything you are unsure about (blurred, hidden or ambiguous tiles). Empty string if nothing.
    """.trimIndent()

    /** 構造化出力（output_config.format）に渡す JSON スキーマ */
    fun schema(): Map<String, Any> {
        val tileList = mapOf("type" to "array", "items" to mapOf("type" to "string", "enum" to TILE_CODES))
        val meld = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "type" to mapOf("type" to "string", "enum" to MELD_TYPES),
                "tiles" to tileList,
            ),
            "required" to listOf("type", "tiles"),
            "additionalProperties" to false,
        )
        return mapOf(
            "type" to "object",
            "properties" to mapOf(
                "tiles" to tileList,
                "melds" to mapOf("type" to "array", "items" to meld),
                "winning_tile_separated" to mapOf("type" to "boolean"),
                "notes" to mapOf("type" to "string"),
            ),
            "required" to listOf("tiles", "melds", "winning_tile_separated", "notes"),
            "additionalProperties" to false,
        )
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** 返ってきた JSON をアプリの牌と面子に直す。読めない JSON なら IllegalArgumentException */
    fun parse(text: String): RecognitionResult {
        val hand = try {
            json.decodeFromString<RecognizedHand>(text)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("読み取り結果の形式が正しくありません", e)
        }
        val warnings = mutableListOf<String>()
        var aka = 0
        fun toTile(code: String): Tile? {
            if (code !in TILE_CODES) {
                warnings += "読めない牌（$code）を飛ばしました"
                return null
            }
            val number = code[0].digitToInt()
            val suit = when (code[1]) {
                'm' -> Suit.MAN
                'p' -> Suit.PIN
                's' -> Suit.SOU
                else -> Suit.HONOR
            }
            if (number == 0) {
                aka++
                return Tile(suit, 5)
            }
            return Tile(suit, number)
        }

        val concealed = hand.tiles.mapNotNull(::toTile).toMutableList()
        val melds = mutableListOf<Meld>()
        hand.melds.forEach { recognized ->
            val tiles = recognized.tiles.mapNotNull(::toTile)
            val meld = toMeld(recognized.type, tiles)
            if (meld != null) {
                melds += meld
            } else {
                // 面子として読めなければ、手牌に入れて直してもらう
                warnings += "鳴きの面子（${recognized.type}: ${tiles.joinToString("") { it.label }}）の形がおかしいので、手牌に入れました"
                concealed += tiles
            }
        }
        if (!hand.winningTileSeparated && concealed.isNotEmpty()) {
            warnings += "アガリ牌がわからなかったので、いちばん右の牌をアガリ牌にしました。違うときは直してください"
        }
        if (hand.notes.isNotBlank()) warnings += hand.notes
        return RecognitionResult(concealed, melds, aka, warnings)
    }

    private fun toMeld(type: String, tiles: List<Tile>): Meld? {
        if (tiles.isEmpty()) return null
        val sorted = tiles.sorted()
        val first = sorted.first()
        val same = sorted.all { it == first }
        return when (type) {
            "chi" -> {
                val run = sorted.size == 3 && !first.isHonor && sorted.map { it.suit }.toSet().size == 1 &&
                    sorted.map { it.number } == listOf(first.number, first.number + 1, first.number + 2)
                if (run) Meld(MeldKind.SEQUENCE, first, open = true) else null
            }
            "pon" -> if (sorted.size == 3 && same) Meld(MeldKind.TRIPLET, first, open = true) else null
            // 暗槓は両端が伏せてあり、見える2枚だけ返ってくることもある
            "minkan" -> if (sorted.size in 3..4 && same) Meld(MeldKind.QUAD, first, open = true) else null
            "ankan" -> if (sorted.size in 1..4 && same) Meld(MeldKind.QUAD, first, open = false) else null
            else -> null
        }
    }
}
