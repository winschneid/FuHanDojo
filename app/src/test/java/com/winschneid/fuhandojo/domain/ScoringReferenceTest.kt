package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Meld
import com.winschneid.fuhandojo.domain.model.MeldKind
import com.winschneid.fuhandojo.domain.model.Suit
import com.winschneid.fuhandojo.domain.model.Tile
import com.winschneid.fuhandojo.domain.model.WinMethod
import com.winschneid.fuhandojo.domain.model.WinMethod.RON
import com.winschneid.fuhandojo.domain.model.WinMethod.TSUMO
import com.winschneid.fuhandojo.domain.model.WinningHand
import com.winschneid.fuhandojo.domain.model.Wind
import com.winschneid.fuhandojo.domain.model.Wind.EAST
import com.winschneid.fuhandojo.domain.model.Wind.NORTH
import com.winschneid.fuhandojo.domain.model.Wind.SOUTH
import com.winschneid.fuhandojo.domain.model.Wind.WEST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * アプリの実装とは別に書いた検算プログラムで確かめ、手計算でも確認した手の点数。
 * 牌は「123m78m123p」のように、数字のあとに m（萬）p（筒）s（索）z（字牌: 1東 2南 3西 4北 5白 6發 7中）を書く。
 */
class ScoringReferenceTest {

    private fun tiles(text: String): List<Tile> {
        val result = mutableListOf<Tile>()
        val digits = StringBuilder()
        for (c in text) {
            if (c.isDigit()) {
                digits.append(c)
                continue
            }
            val suit = when (c) {
                'm' -> Suit.MAN
                'p' -> Suit.PIN
                's' -> Suit.SOU
                'z' -> Suit.HONOR
                else -> error("不明な文字: $c")
            }
            digits.forEach { result += Tile(suit, it.digitToInt()) }
            digits.clear()
        }
        return result
    }

    private fun tile(text: String) = tiles(text).single()
    private fun chi(t: String) = Meld(MeldKind.SEQUENCE, tile(t), open = true)
    private fun pon(t: String) = Meld(MeldKind.TRIPLET, tile(t), open = true)
    private fun minkan(t: String) = Meld(MeldKind.QUAD, tile(t), open = true)
    private fun ankan(t: String) = Meld(MeldKind.QUAD, tile(t), open = false)

    private fun hand(
        concealed: String,
        win: String,
        method: WinMethod,
        melds: List<Meld> = emptyList(),
        round: Wind = EAST,
        seat: Wind = SOUTH,
    ) = WinningHand(tiles(concealed).sorted(), melds, tile(win), method, round, seat)

    /** 役の名前（ドラを除く）、符（役満は検査しない）、支払いを確かめる */
    private fun check(
        id: String,
        hand: WinningHand,
        yaku: Set<String>,
        payment: String,
        fu: Int? = null,
        han: Int? = null,
        context: WinContext = WinContext(),
    ) {
        val outcome = Scoring.score(hand, context)
        val result = (outcome as? ScoreOutcome.Success)?.result ?: error("$id: $outcome")
        val names = result.yaku.map { it.name }.filterNot { it.startsWith("ドラ") || it.startsWith("赤ドラ") || it.startsWith("裏ドラ") }
        assertEquals("$id 役", yaku, names.toSet())
        assertEquals("$id 支払い", payment, result.payment.label)
        fu?.let { assertEquals("$id 符", it, result.fu) }
        han?.let { assertEquals("$id 翻", it, result.han) }
    }

    @Test
    fun `役満`() {
        check("B01", hand("111z222z333z44z23m", "1m", RON), setOf("小四喜"), "32000")
        check("B02", hand("111z222z333z5p", "5p", RON, listOf(pon("4z"))), setOf("大四喜"), "32000")
        check("B03", hand("111z222z333z444z5p", "5p", TSUMO), setOf("四暗刻", "大四喜"), "16000-32000")
        check("B04", hand("111z222z333z444z5z", "5z", TSUMO, seat = WEST), setOf("四暗刻", "大四喜", "字一色"), "24000-48000")
        check("B05", hand("111m999m99s11s", "1s", RON, listOf(pon("9p"))), setOf("清老頭"), "32000")
        check("B06", hand("1112345678999m", "5m", TSUMO), setOf("九蓮宝燈"), "8000-16000")
        check("B07", hand("1123456789999m", "1m", RON, seat = EAST), setOf("九蓮宝燈"), "48000")
        check("B25", hand("19m19p19s1234567z", "1m", RON), setOf("国士無双"), "32000")
        check("B26", hand("119m19p19s123456z", "7z", TSUMO, seat = EAST), setOf("国士無双"), "16000オール")
        check("B39", hand("555z666z777z11z22z", "2z", RON), setOf("大三元", "字一色"), "64000")
        check("B40", hand("555z666z777z11z22z", "2z", TSUMO), setOf("四暗刻", "大三元", "字一色"), "24000-48000")
        check("B41", hand("223344s666s888s4s", "4s", RON), setOf("緑一色"), "32000")
        // 同じ点数になる数え役満の取り方より、本物の役満を採用する
        check(
            "B38",
            hand("1112233344555m", "2m", TSUMO, round = SOUTH, seat = NORTH),
            setOf("四暗刻"),
            "8000-16000",
            context = WinContext(riichi = Riichi.DOUBLE, ippatsu = true, dora = 3, uraDora = 1),
        )
    }

    @Test
    fun `チャンタ・三色・一気通貫・平和の取り方`() {
        check("B08", hand("123m78m123p999s99p", "9m", RON), setOf("純全帯么九"), "5200", fu = 40)
        check("B09", hand("123m123p123s789s9p", "9p", RON), setOf("三色同順", "純全帯么九"), "8000", fu = 40, han = 5)
        check("B10", hand("789m111p999s1s", "1s", RON, listOf(chi("1m"))), setOf("純全帯么九"), "2600", fu = 40)
        check("B11", hand("234m678m234p34s55p", "2s", RON), setOf("平和", "断么九", "三色同順"), "7700", fu = 30)
        check("B12", hand("234p234s789p9m", "9m", RON, listOf(chi("2m"))), setOf("三色同順"), "1000", fu = 30)
        check("B13", hand("23456789p567s22m", "1p", RON), setOf("平和", "一気通貫"), "3900", fu = 30)
        check("B14", hand("23456789p567s22m", "4p", RON), setOf("平和"), "1000", fu = 30)
        check("B15", hand("1234p6789p234s55m", "5p", RON), setOf("一気通貫"), "2600", fu = 40)
        check("B16", hand("123p456p78p55m", "9p", RON, listOf(chi("3s"))), setOf("一気通貫"), "1000", fu = 30)
        check("B19", hand("123m789p123s555z9m", "9m", RON), setOf("役牌 白", "混全帯么九"), "5200", fu = 40)
    }

    @Test
    fun `刻子の役と暗刻の数え方`() {
        check("B17", hand("111m999p55z99s", "9s", RON, listOf(pon("1s")), round = SOUTH, seat = WEST), setOf("対々和", "混老頭"), "8000", fu = 50)
        check("B18", hand("1199m1199p11s11z5z", "5z", RON), setOf("七対子", "混老頭"), "6400", fu = 25)
        // ロンで完成した刻子は暗刻に数えないので三暗刻にならない
        check("B20", hand("222m555p77s88s345m", "7s", RON), setOf("断么九"), "1300", fu = 40)
        check("B21", hand("222m555p77s88s345m", "7s", TSUMO), setOf("門前清自摸和", "断么九", "三暗刻"), "2000-4000", fu = 40)
        // アガリ牌を順子に取れば 333 は暗刻のまま
        check("B22", hand("33345m777p222s88s", "3m", RON), setOf("断么九", "三暗刻"), "6400", fu = 50)
        check("B37", hand("222m444p666s88m99p", "9p", RON), setOf("対々和", "三暗刻"), "8000", fu = 50)
        check(
            "B45",
            hand("2m", "2m", RON, listOf(ankan("1p"), minkan("5s"), minkan("9m"), pon("7z"))),
            setOf("役牌 中", "対々和", "三槓子"),
            "8000",
            fu = 90,
            han = 5,
        )
        check("B42", hand("666z77z234m78p", "9p", RON, listOf(pon("5z"))), setOf("役牌 發", "役牌 白", "小三元"), "8000", fu = 40)
        check("B47", hand("111z234m567p678s9p", "9p", RON, seat = EAST), setOf("自風 東", "場風 東"), "3900", fu = 40)
    }

    @Test
    fun `二盃口と七対子`() {
        check("B23", hand("112233m7788p9p55s", "9p", RON), setOf("平和", "二盃口"), "7700", fu = 30)
        check("B24", hand("223344m667788p5s", "5s", RON), setOf("断么九", "二盃口"), "8000", fu = 40)
        // 鳴いていると一盃口は付かない
        check("B44", hand("334455p678s2m", "2m", RON, listOf(chi("3s"))), setOf("断么九"), "1000", fu = 30)
        check("B46", hand("3334m567p234s678s", "4m", RON), setOf("断么九"), "1300", fu = 40)
    }

    @Test
    fun `状況役と符`() {
        check(
            "B27",
            hand("567p345s678s8p", "8p", TSUMO, listOf(chi("2m"))),
            setOf("海底摸月", "断么九"),
            "500-1000",
            fu = 30,
            context = WinContext(lastTile = true),
        )
        check(
            "B28",
            hand("567p345s67s88p", "8s", RON, listOf(chi("2m"))),
            setOf("河底撈魚", "断么九"),
            "2000",
            fu = 30,
            context = WinContext(lastTile = true),
        )
        // 連風牌の雀頭は4符
        check("B29", hand("57p678s11z", "6p", RON, listOf(pon("5z"), pon("3m")), seat = EAST), setOf("役牌 白"), "2000", fu = 40)
        check(
            "B30",
            hand("567p345s67s88p", "8s", RON, listOf(chi("2m"))),
            setOf("槍槓", "断么九"),
            "2000",
            fu = 30,
            context = WinContext(chankan = true),
        )
        check(
            "B31",
            hand("345m678s45p99m", "6p", TSUMO, listOf(ankan("2p"))),
            setOf("門前清自摸和", "嶺上開花"),
            "700-1300",
            fu = 40,
            context = WinContext(rinshan = true),
        )
        // 嶺上開花でもツモの2符は付く
        check(
            "B32",
            hand("234p567p35s88s", "4s", TSUMO, listOf(minkan("3m"))),
            setOf("嶺上開花", "断么九"),
            "700-1300",
            fu = 40,
            context = WinContext(rinshan = true),
        )
        check("B33", hand("234m567p34s678s99p", "5s", TSUMO), setOf("門前清自摸和", "平和"), "400-700", fu = 20)
        check("B34", hand("234m567p34s678s99p", "5s", TSUMO, seat = EAST), setOf("門前清自摸和", "平和"), "700オール", fu = 20)
        check(
            "B36",
            hand("234m567p34s678s55z", "5s", RON),
            setOf("立直"),
            "1300",
            fu = 40,
            context = WinContext(riichi = Riichi.RIICHI),
        )
        check(
            "B43",
            hand("1234567895577m", "7m", TSUMO),
            setOf("立直", "門前清自摸和", "清一色", "一気通貫"),
            "8000-16000",
            han = 13,
            context = WinContext(riichi = Riichi.RIICHI, dora = 3),
        )
    }

    @Test
    fun `本場と役なし`() {
        check("B48", hand("234p234s789p9m", "9m", RON, listOf(chi("2m"))), setOf("三色同順"), "1600", context = WinContext(honba = 2))
        check("B49", hand("234m567p34s678s99p", "5s", TSUMO), setOf("門前清自摸和", "平和"), "700-1000", context = WinContext(honba = 3))
        // 雀頭が役牌なので平和にならず、役がない
        assertEquals(ScoreOutcome.NoYaku, Scoring.score(hand("234m567p34s678s55z", "5s", RON), WinContext()))
        assertEquals(ScoreOutcome.NoYaku, Scoring.score(hand("234m567p34s678s55z", "5s", RON), WinContext(dora = 3, akaDora = 1)))
    }

    @Test
    fun `ありえない状況の組み合わせ`() {
        val rinshan = hand("345m678s45p99m", "6p", TSUMO, listOf(ankan("2p")))
        // 槓をすると一発は消える
        assertTrue(Scoring.score(rinshan, WinContext(riichi = Riichi.RIICHI, ippatsu = true, rinshan = true)) is ScoreOutcome.Invalid)
        // 槍槓の牌を自分が持っていることはない
        val withSameTile = hand("2m2m3m3m1p1p2p2p2s1z1z7z7z", "2s", RON)
        assertTrue(Scoring.score(withSameTile, WinContext(chankan = true)) is ScoreOutcome.Invalid)
        // 5のない手に赤ドラはない
        val noFive = hand("234m678p34s678s99p", "2s", RON)
        assertTrue(Scoring.score(noFive, WinContext(riichi = Riichi.RIICHI, akaDora = 1)) is ScoreOutcome.Invalid)
    }
}
