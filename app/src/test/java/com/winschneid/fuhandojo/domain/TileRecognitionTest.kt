package com.winschneid.fuhandojo.domain

import com.anthropic.core.JsonValue
import com.winschneid.fuhandojo.domain.model.Meld
import com.winschneid.fuhandojo.domain.model.MeldKind
import com.winschneid.fuhandojo.domain.model.Suit
import com.winschneid.fuhandojo.domain.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TileRecognitionTest {

    private fun m(n: Int) = Tile(Suit.MAN, n)
    private fun p(n: Int) = Tile(Suit.PIN, n)
    private fun s(n: Int) = Tile(Suit.SOU, n)
    private fun z(n: Int) = Tile(Suit.HONOR, n)

    @Test
    fun `手牌と離して置かれたアガリ牌を、そのままの順で読む`() {
        val result = TileRecognition.parse(
            """{"tiles":["2m","3m","4m","5p","6p","7p","7s","8s","9s","3s","4s","5m","5m","5s"],
               "melds":[],"winning_tile_separated":true,"notes":""}""",
        )
        assertEquals(listOf(m(2), m(3), m(4), p(5), p(6), p(7), s(7), s(8), s(9), s(3), s(4), m(5), m(5), s(5)), result.tiles)
        assertTrue(result.melds.isEmpty())
        assertEquals(0, result.akaDora)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `赤5は5として読み、赤ドラの枚数に数える`() {
        val result = TileRecognition.parse(
            """{"tiles":["0m","5p"],"melds":[{"type":"pon","tiles":["0s","5s","5s"]}],"winning_tile_separated":true,"notes":""}""",
        )
        assertEquals(listOf(m(5), p(5)), result.tiles)
        assertEquals(listOf(Meld(MeldKind.TRIPLET, s(5), open = true)), result.melds)
        assertEquals(2, result.akaDora)
    }

    @Test
    fun `鳴きの面子と暗槓を読む`() {
        val result = TileRecognition.parse(
            """{"tiles":["2p"],"melds":[
                 {"type":"chi","tiles":["4m","2m","3m"]},
                 {"type":"minkan","tiles":["7z","7z","7z","7z"]},
                 {"type":"ankan","tiles":["1p","1p"]},
                 {"type":"pon","tiles":["9s","9s","9s"]}],
               "winning_tile_separated":true,"notes":""}""",
        )
        assertEquals(
            listOf(
                Meld(MeldKind.SEQUENCE, m(2), open = true),
                Meld(MeldKind.QUAD, z(7), open = true),
                // 両端が伏せてあり、見える2枚だけ返ってきても暗槓として読む
                Meld(MeldKind.QUAD, p(1), open = false),
                Meld(MeldKind.TRIPLET, s(9), open = true),
            ),
            result.melds,
        )
    }

    @Test
    fun `形がおかしい面子は手牌に入れて知らせる`() {
        val result = TileRecognition.parse(
            """{"tiles":["1m"],"melds":[{"type":"chi","tiles":["1m","3m","5m"]}],"winning_tile_separated":true,"notes":""}""",
        )
        assertTrue(result.melds.isEmpty())
        assertEquals(listOf(m(1), m(1), m(3), m(5)), result.tiles)
        assertTrue(result.warnings.single().contains("形がおかしい"))
    }

    @Test
    fun `アガリ牌がわからないときと、気になる点のメモを知らせる`() {
        val result = TileRecognition.parse(
            """{"tiles":["1z","1z"],"melds":[],"winning_tile_separated":false,"notes":"右端の牌がぼやけています"}""",
        )
        assertEquals(2, result.warnings.size)
        assertTrue(result.warnings[0].contains("いちばん右"))
        assertEquals("右端の牌がぼやけています", result.warnings[1])
    }

    @Test
    fun `読めない牌は飛ばし、形式が違う JSON はエラーにする`() {
        val result = TileRecognition.parse("""{"tiles":["1m","8z"],"melds":[],"winning_tile_separated":true,"notes":""}""")
        assertEquals(listOf(m(1)), result.tiles)
        assertTrue(result.warnings.single().contains("8z"))
        assertThrows(IllegalArgumentException::class.java) { TileRecognition.parse("""{"hand": []}""") }
        assertThrows(IllegalArgumentException::class.java) { TileRecognition.parse("ここは牌の写真です") }
    }

    @Test
    fun `スキーマは構造化出力の条件を満たし、SDK の JSON に変換できる`() {
        val schema = TileRecognition.schema()
        assertEquals(false, schema["additionalProperties"])
        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as Map<String, Any>
        assertEquals(properties.keys, (schema["required"] as List<*>).toSet())
        assertEquals(37, TileRecognition.TILE_CODES.size)
        // SDK の JsonValue に入れても中身が変わらない
        val value = JsonValue.from(schema)
        assertTrue(value.toString().contains("winning_tile_separated"))
        assertTrue(value.toString().contains("0p"))
    }
}
