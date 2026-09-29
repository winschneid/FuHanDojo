package com.winschneid.fuhandojo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.winschneid.fuhandojo.domain.model.Meld
import com.winschneid.fuhandojo.domain.model.MeldKind
import com.winschneid.fuhandojo.domain.model.Suit
import com.winschneid.fuhandojo.domain.model.Tile
import com.winschneid.fuhandojo.domain.model.TileGroupInfo
import com.winschneid.fuhandojo.domain.model.WinMethod
import com.winschneid.fuhandojo.domain.model.WinningHand
import com.winschneid.fuhandojo.domain.model.Wind
import com.winschneid.fuhandojo.ui.theme.FuHanDojoTheme

// 牌の色はテーマ（ダークモード）によらず、実物の牌に近い色で固定する
private val TileFace = Color(0xFFFAF7EE)
private val TileEdge = Color(0xFFB9AE8C)
private val TileBack = Color(0xFF1B6B4A)
private val ManColor = Color(0xFFB3261E)
private val PinColor = Color(0xFF1558A8)
private val SouColor = Color(0xFF2E7D32)
private val HonorColor = Color(0xFF202020)
private val WinHighlight = Color(0xFFE0A100)
private val GROUP_GAP = 8.dp

private fun Tile.color(): Color = when {
    suit == Suit.MAN -> ManColor
    suit == Suit.PIN -> PinColor
    suit == Suit.SOU -> SouColor
    number == 6 -> SouColor // 發
    number == 7 -> ManColor // 中
    else -> HonorColor
}

/** 牌1枚。faceDown は暗槓の両端のように伏せた牌 */
@Composable
fun TileView(
    tile: Tile,
    width: Dp,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    faceDown: Boolean = false,
) {
    val height = width * 1.36f
    // 牌は絵として扱うので、文字の大きさ設定では拡大しない（Dp から換算した sp は文字倍率を打ち消す）
    val density = LocalDensity.current
    val bigSize = with(density) { (width * 0.56f).toSp() }
    val smallSize = with(density) { (width * 0.4f).toSp() }
    Surface(
        modifier = modifier
            .size(width, height)
            .clearAndSetSemantics { contentDescription = if (faceDown) "伏せた牌" else tile.label },
        shape = RoundedCornerShape(width * 0.14f),
        color = if (faceDown) TileBack else TileFace,
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, if (highlighted) WinHighlight else TileEdge),
    ) {
        if (faceDown) return@Surface
        Box(contentAlignment = Alignment.Center) {
            when {
                tile.isHonor && tile.number == 5 -> Box(
                    // 白は枠だけの牌
                    Modifier
                        .size(width * 0.55f, height * 0.6f)
                        .border(1.5.dp, PinColor.copy(alpha = 0.6f), RoundedCornerShape(width * 0.08f)),
                )
                tile.isHonor -> Text(
                    text = tile.label,
                    color = tile.color(),
                    fontSize = bigSize,
                    fontWeight = FontWeight.Bold,
                )
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${tile.number}",
                        color = tile.color(),
                        fontSize = bigSize,
                        fontWeight = FontWeight.Bold,
                        lineHeight = bigSize,
                    )
                    Text(
                        text = tile.suit.label,
                        color = tile.color(),
                        fontSize = smallSize,
                        lineHeight = smallSize,
                    )
                }
            }
        }
    }
}

/** 牌を横に並べ、下に小さく説明を付ける */
@Composable
fun TileGroup(
    tiles: List<Tile>,
    tileWidth: Dp,
    modifier: Modifier = Modifier,
    caption: String? = null,
    highlightLast: Boolean = false,
    highlightIndex: Int? = null,
    faceDownIndices: Set<Int> = emptySet(),
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            tiles.forEachIndexed { i, tile ->
                TileView(
                    tile = tile,
                    width = tileWidth,
                    highlighted = (highlightLast && i == tiles.lastIndex) || i == highlightIndex,
                    faceDown = i in faceDownIndices,
                )
            }
        }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 暗槓は両端を伏せて見せる */
@Composable
fun MeldView(meld: Meld, tileWidth: Dp, caption: String? = meld.label) {
    TileGroup(
        tiles = meld.tiles,
        tileWidth = tileWidth,
        caption = caption,
        faceDownIndices = if (meld.kind == MeldKind.QUAD && !meld.open) setOf(0, 3) else emptySet(),
    )
}

/**
 * 和了った手全体。手の中の牌、和了牌（枠を強調）、鳴いた面子と暗槓の順に並べる。
 * 手牌と和了牌が1行に収まるよう幅から牌の大きさを決め、鳴いた面子は入り切らなければ次の行に回す。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HandView(hand: WinningHand, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val firstRowCount = hand.concealed.size + 1
        // 牌どうしの1dp、手牌と和了牌の間の余白の分を引いてから割る
        val tileWidth = min(34.dp, (maxWidth - GROUP_GAP * 2 - 1.dp * firstRowCount) / firstRowCount)
            .coerceAtLeast(20.dp)
        FlowRow(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(GROUP_GAP, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TileGroup(hand.concealed, tileWidth, caption = "手牌")
            TileGroup(
                tiles = listOf(hand.winningTile),
                tileWidth = tileWidth,
                caption = if (hand.method == WinMethod.RON) "ロン" else "ツモ",
                highlightLast = true,
            )
            hand.calledMelds.forEach { meld ->
                MeldView(meld, tileWidth, caption = meld.label)
            }
        }
    }
}

/**
 * 面子・雀頭ごとに区切った手牌。和了牌で完成したグループでは和了牌の枠を強調する。
 * 区切りの分だけ横幅を使うので、牌を小さくしすぎず、入り切らなければ折り返す。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitHandView(groups: List<TileGroupInfo>, winningTile: Tile, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val tileCount = groups.sumOf { it.tiles.size }
        val tileWidth = ((maxWidth - GROUP_GAP * groups.size - 1.dp * tileCount) / tileCount)
            .coerceIn(22.dp, 34.dp)
        FlowRow(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(GROUP_GAP, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { group ->
                TileGroup(
                    tiles = group.tiles,
                    tileWidth = tileWidth,
                    caption = group.caption,
                    highlightIndex = if (group.winning) group.tiles.lastIndexOf(winningTile) else null,
                    faceDownIndices = if (group.faceDownEnds) setOf(0, group.tiles.lastIndex) else emptySet(),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun HandViewPreview() {
    val m = { n: Int -> Tile(Suit.MAN, n) }
    val p = { n: Int -> Tile(Suit.PIN, n) }
    FuHanDojoTheme {
        Column(Modifier.padding(16.dp)) {
            HandView(
                WinningHand(
                    concealed = listOf(m(2), m(3), m(4), p(5), p(5), p(5), p(7), p(9), Tile(Suit.HONOR, 5), Tile(Suit.HONOR, 5)),
                    calledMelds = listOf(Meld(MeldKind.TRIPLET, Tile(Suit.HONOR, 7), open = true)),
                    winningTile = p(8),
                    method = WinMethod.RON,
                    roundWind = Wind.EAST,
                    seatWind = Wind.SOUTH,
                ),
            )
        }
    }
}
