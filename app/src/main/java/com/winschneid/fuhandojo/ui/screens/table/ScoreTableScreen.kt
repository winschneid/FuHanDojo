package com.winschneid.fuhandojo.ui.screens.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.winschneid.fuhandojo.domain.ScoreTable
import com.winschneid.fuhandojo.domain.model.Seat
import com.winschneid.fuhandojo.domain.model.WinMethod
import com.winschneid.fuhandojo.ui.theme.FuHanDojoTheme

private val TABS = listOf("点数", "符")

/** 見えない改行位置。ここで折り返してよいことを示す */
private val ZERO_WIDTH_SPACE = Char(0x200B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreTableScreen(onNavigateBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("早見表") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    TABS.forEachIndexed { i, title ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                    }
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (tab == 0) PointsTab() else FuTab()
        }
    }
}

// ---- 点数タブ ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PointsTab() {
    var seat by rememberSaveable { mutableStateOf(Seat.NON_DEALER) }
    var method by rememberSaveable { mutableStateOf(WinMethod.RON) }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            Seat.entries.forEachIndexed { i, s ->
                SegmentedButton(
                    selected = seat == s,
                    onClick = { seat = s },
                    shape = SegmentedButtonDefaults.itemShape(i, Seat.entries.size),
                ) { Text(s.label) }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            WinMethod.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = method == m,
                    onClick = { method = m },
                    shape = SegmentedButtonDefaults.itemShape(i, WinMethod.entries.size),
                ) { Text(m.label) }
            }
        }
    }

    Text(
        text = when {
            method == WinMethod.RON -> "${seat.label}のロン: 放銃した人が支払う点数"
            seat == Seat.DEALER -> "親のツモ: 子それぞれの支払い（〇〇オール）"
            else -> "子のツモ: 子の支払い-親の支払い"
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )

    FuHanTable(ScoreTable.rows(seat, method))

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Box(
            Modifier
                .size(14.dp)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
        )
        Text(
            text = " 満貫（4翻40符以上・3翻70符以上）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = "—  ない組み合わせ（20符のロンなど）",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SectionTitle("満貫以上")
    TableFrame {
        ScoreTable.limitRows(seat, method).forEach { row ->
            TableRow {
                HeaderCell(row.limit.label, weight = 1f)
                BodyCell(row.limit.hanRange, weight = 1f)
                BodyCell(row.label, weight = 1.4f, bold = true)
            }
        }
    }
    Text(
        text = "13翻以上は数え役満（役満と同じ点数）。三倍満までとするルールもあります。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun FuHanTable(rows: List<ScoreTable.Row>) {
    TableFrame {
        TableRow {
            HeaderCell("", weight = 0.7f)
            ScoreTable.HANS.forEach { HeaderCell("${it}翻", weight = 1f) }
        }
        rows.forEach { row ->
            TableRow {
                HeaderCell("${row.fu}符", weight = 0.7f)
                row.cells.forEachIndexed { i, cell ->
                    val han = ScoreTable.HANS.first + i
                    BodyCell(
                        // 「2000-4000」が収まらないとき数字の途中ではなくハイフンの後ろで折り返すよう、改行位置を入れる
                        text = cell?.label?.replace("-", "-$ZERO_WIDTH_SPACE") ?: "—",
                        weight = 1f,
                        highlighted = cell?.isMangan == true,
                        muted = cell == null,
                        description = "${row.fu}符${han}翻 " + (cell?.label ?: "なし"),
                    )
                }
            }
        }
    }
}

// ---- 符タブ ----

@Composable
private fun FuTab() {
    SectionTitle("数え方", top = 0.dp)
    Guide(
        "1. 基本の20符（副底）から始める",
        "2. 和了り方・面子・雀頭・待ちの符を足す",
        "3. 10符単位に切り上げる（例: 32符 → 40符）",
    )

    SectionTitle("和了り方")
    TableFrame {
        TableRow { BodyCell("門前でロン", 2f); BodyCell("10符", 1f, bold = true) }
        TableRow { BodyCell("ツモ（平和ツモは付かない）", 2f); BodyCell("2符", 1f, bold = true) }
    }

    SectionTitle("面子")
    TableFrame {
        TableRow {
            HeaderCell("", 1f)
            HeaderCell("2〜8の数牌", 1f)
            HeaderCell("ヤオ九牌", 1f)
        }
        listOf(
            Triple("明刻（ポン）", 2, 4),
            Triple("暗刻", 4, 8),
            Triple("明槓", 8, 16),
            Triple("暗槓", 16, 32),
        ).forEach { (name, simple, terminal) ->
            TableRow {
                HeaderCell(name, 1f)
                BodyCell("${simple}符", 1f, bold = true)
                BodyCell("${terminal}符", 1f, bold = true)
            }
        }
        TableRow { HeaderCell("順子", 1f); BodyCell("0符", 2f) }
    }
    Guide(
        "ヤオ九牌は 1・9・字牌。",
        "ロンで完成した刻子（シャンポン待ちでロン）は明刻として数える。",
    )

    SectionTitle("雀頭")
    TableFrame {
        TableRow { BodyCell("三元牌（白・發・中）", 2f); BodyCell("2符", 1f, bold = true) }
        TableRow { BodyCell("自風・場風", 2f); BodyCell("2符", 1f, bold = true) }
        TableRow { BodyCell("それ以外", 2f); BodyCell("0符", 1f) }
    }
    Guide("自風と場風が同じ風（連風牌）は4符とするルールが多く、2符とするルールもあります。")

    SectionTitle("待ち")
    TableFrame {
        TableRow { BodyCell("カンチャン・ペンチャン・単騎", 2f); BodyCell("2符", 1f, bold = true) }
        TableRow { BodyCell("両面・シャンポン", 2f); BodyCell("0符", 1f) }
    }

    SectionTitle("決まった符になる手")
    TableFrame {
        TableRow { BodyCell("平和のツモ", 2f); BodyCell("20符", 1f, bold = true) }
        TableRow { BodyCell("平和のロン", 2f); BodyCell("30符", 1f, bold = true) }
        TableRow { BodyCell("七対子", 2f); BodyCell("25符", 1f, bold = true) }
        TableRow { BodyCell("鳴いた手で符がないロン", 2f); BodyCell("30符", 1f, bold = true) }
    }
}

// ---- 表の部品 ----

@Composable
private fun SectionTitle(text: String, top: Dp = 24.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = top, bottom = 8.dp),
    )
}

@Composable
private fun Guide(vararg lines: String) {
    Text(
        text = lines.joinToString("\n"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun TableFrame(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
    ) {
        content()
    }
}

@Composable
private fun TableRow(content: @Composable RowScope.() -> Unit) {
    // 文字が折り返したときも、行の中のマスの高さをそろえる
    Row(Modifier.height(IntrinsicSize.Min), content = content)
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Cell(
        text = text,
        weight = weight,
        background = MaterialTheme.colorScheme.surfaceContainerHigh,
        bold = true,
    )
}

@Composable
private fun RowScope.BodyCell(
    text: String,
    weight: Float,
    bold: Boolean = false,
    highlighted: Boolean = false,
    muted: Boolean = false,
    description: String? = null,
) {
    Cell(
        text = text,
        weight = weight,
        background = if (highlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        bold = bold,
        color = when {
            muted -> MaterialTheme.colorScheme.outline
            highlighted -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        description = description,
    )
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    background: Color,
    bold: Boolean,
    color: Color = MaterialTheme.colorScheme.onSurface,
    description: String? = null,
) {
    Surface(
        color = background,
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .heightIn(min = 36.dp)
                .padding(horizontal = 2.dp, vertical = 6.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                color = color,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1200)
@Composable
private fun ScoreTableScreenPreview() {
    FuHanDojoTheme {
        ScoreTableScreen(onNavigateBack = {})
    }
}
