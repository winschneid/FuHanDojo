package com.winschneid.fuhandojo.ui.screens.calculator

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.winschneid.fuhandojo.domain.Explainer
import com.winschneid.fuhandojo.domain.FuQuizGenerator
import com.winschneid.fuhandojo.domain.Riichi
import com.winschneid.fuhandojo.domain.ScoreOutcome
import com.winschneid.fuhandojo.domain.ScoreResult
import com.winschneid.fuhandojo.domain.WinContext
import com.winschneid.fuhandojo.domain.model.Hand
import com.winschneid.fuhandojo.domain.model.Limit
import com.winschneid.fuhandojo.domain.model.Payment
import com.winschneid.fuhandojo.domain.model.Suit
import com.winschneid.fuhandojo.domain.model.Tile
import com.winschneid.fuhandojo.domain.model.WinMethod
import com.winschneid.fuhandojo.domain.model.Wind
import com.winschneid.fuhandojo.ui.components.MeldView
import com.winschneid.fuhandojo.ui.components.TileView

@Composable
fun CalculatorScreen(
    onNavigateBack: () -> Unit,
    viewModel: CalculatorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CalculatorContent(uiState = uiState, onAction = viewModel::onAction, onNavigateBack = onNavigateBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorContent(
    uiState: CalculatorUiState,
    onAction: (CalculatorAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("点数計算") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            PhotoSection(uiState, onAction)
            SectionTitle("手牌")
            HandSection(uiState, onAction)
            ResultSummary(uiState.outcome, uiState.input)
            SectionTitle("牌を入れる")
            ModeSelector(uiState, onAction)
            TileKeyboard(uiState, onAction)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { onAction(CalculatorAction.Backspace) }, modifier = Modifier.weight(1f)) {
                    Text("1枚消す")
                }
                OutlinedButton(onClick = { onAction(CalculatorAction.Clear) }, modifier = Modifier.weight(1f)) {
                    Text("全部消す")
                }
            }
            SectionTitle("条件")
            ConditionSection(uiState.input, onAction)
            SectionTitle("結果")
            ResultDetail(uiState.outcome, uiState.input)
            Text(
                text = "ルール: 喰いタンあり・切り上げ満貫なし・数え役満あり・役満の複合あり（ダブル役満なし）・" +
                    "連風牌の雀頭は4符。天和・地和・流し満貫・供託は扱いません。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

// ---- 手牌 ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandSection(uiState: CalculatorUiState, onAction: (CalculatorAction) -> Unit) {
    val input = uiState.input
    val concealed = input.tiles.dropLast(1).sorted()
    val winning = input.tiles.lastOrNull()
    Text(
        text = "手牌 ${input.tiles.size} / ${input.needed}枚（アガリ牌を含む）　牌を押すと消せます",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val tileWidth = min(30.dp, (maxWidth - 24.dp) / 14)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (input.tiles.isEmpty() && input.melds.isEmpty()) {
                Text("まだ牌がありません", style = MaterialTheme.typography.bodyMedium)
            }
            if (concealed.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        concealed.forEach { tile ->
                            TileView(
                                tile = tile,
                                width = tileWidth,
                                modifier = Modifier.clickable(role = Role.Button) {
                                    onAction(CalculatorAction.RemoveConcealed(tile))
                                },
                            )
                        }
                    }
                    Caption("手牌")
                }
            }
            if (winning != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TileView(
                        tile = winning,
                        width = tileWidth,
                        highlighted = true,
                        modifier = Modifier.clickable(role = Role.Button) { onAction(CalculatorAction.RemoveWinning) },
                    )
                    Caption("アガリ牌")
                }
            }
            input.melds.forEachIndexed { i, meld ->
                Column(
                    modifier = Modifier.clickable(role = Role.Button) { onAction(CalculatorAction.RemoveMeld(i)) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MeldView(meld, tileWidth, caption = meld.label)
                }
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** 手牌のすぐ下に出す結果の要約（入力しながら確かめられるように） */
@Composable
private fun ResultSummary(outcome: ScoreOutcome, input: CalcInput) {
    val (text, ok) = when (outcome) {
        is ScoreOutcome.Success -> "${paymentText(outcome.result.payment, input)}（${scoreTitle(outcome.result)}）" to true
        is ScoreOutcome.Incomplete -> statusText(outcome) to false
        ScoreOutcome.NotWinningShape -> "アガリの形になっていません" to false
        ScoreOutcome.NoYaku -> "役がありません" to false
        is ScoreOutcome.Invalid -> outcome.reason to false
    }
    Text(
        text = text,
        style = if (ok) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
        fontWeight = if (ok) FontWeight.Bold else FontWeight.Normal,
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

private fun statusText(outcome: ScoreOutcome.Incomplete): String =
    if (outcome.actual < outcome.needed) {
        "あと${outcome.needed - outcome.actual}枚入れてください"
    } else {
        "牌が${outcome.actual - outcome.needed}枚多すぎます"
    }

// ---- 牌の入力 ----

@Composable
private fun ModeSelector(uiState: CalculatorUiState, onAction: (CalculatorAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        EntryMode.entries.forEach { mode ->
            FilterChip(
                selected = uiState.mode == mode,
                onClick = { onAction(CalculatorAction.SetMode(mode)) },
                label = { Text(mode.label) },
                enabled = mode == EntryMode.HAND || uiState.canAddMeld(),
            )
        }
    }
    Text(
        text = if (uiState.mode != EntryMode.HAND || uiState.canAddMeld()) {
            uiState.mode.hint
        } else {
            uiState.mode.hint + "鳴いた面子を入れるときは、先に手牌を減らしてください。"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun TileKeyboard(uiState: CalculatorUiState, onAction: (CalculatorAction) -> Unit) {
    val rows = listOf(Suit.MAN, Suit.PIN, Suit.SOU).map { suit -> (1..9).map { Tile(suit, it) } } +
        listOf((1..7).map { Tile(Suit.HONOR, it) })
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 4.dp
        val tileWidth: Dp = min(40.dp, (maxWidth - gap * 8) / 9)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { tile ->
                        val enabled = uiState.canEnter(tile)
                        TileView(
                            tile = tile,
                            width = tileWidth,
                            modifier = Modifier
                                .alpha(if (enabled) 1f else 0.3f)
                                .clickable(enabled = enabled, role = Role.Button) { onAction(CalculatorAction.TapTile(tile)) },
                        )
                    }
                }
            }
        }
    }
}

// ---- 条件 ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionSection(input: CalcInput, onAction: (CalculatorAction) -> Unit) {
    val context = input.context
    fun update(transform: (WinContext) -> WinContext) = onAction(CalculatorAction.UpdateContext(transform))

    Segmented(
        label = "アガリ方",
        options = WinMethod.entries.map { it.label },
        selected = input.method.ordinal,
        onSelect = { onAction(CalculatorAction.SetMethod(WinMethod.entries[it])) },
    )
    Segmented(
        label = "場風",
        options = Wind.entries.map { it.label },
        selected = input.roundWind.ordinal,
        onSelect = { onAction(CalculatorAction.SetRoundWind(Wind.entries[it])) },
    )
    Segmented(
        label = "自風",
        options = Wind.entries.map { it.label + if (it == Wind.EAST) "（親）" else "" },
        selected = input.seatWind.ordinal,
        onSelect = { onAction(CalculatorAction.SetSeatWind(Wind.entries[it])) },
    )
    Segmented(
        label = if (input.isClosed) "リーチ" else "リーチ（鳴いているのでできません）",
        options = Riichi.entries.map { it.label },
        selected = context.riichi.ordinal,
        enabled = input.isClosed,
        onSelect = { index -> update { it.copy(riichi = Riichi.entries[index]) } },
    )
    // 槓をすると一発は消えるので、一発と嶺上開花はどちらか一方だけ
    CheckRow("一発", context.ippatsu, enabled = context.riichi != Riichi.NONE) { checked ->
        update { it.copy(ippatsu = checked, rinshan = it.rinshan && !checked) }
    }
    CheckRow(
        text = if (input.method == WinMethod.TSUMO) "海底摸月（最後の牌でツモ）" else "河底撈魚（最後の捨て牌でロン）",
        checked = context.lastTile,
    ) { checked -> update { it.copy(lastTile = checked, rinshan = it.rinshan && !checked, chankan = it.chankan && !checked) } }
    CheckRow(
        text = "嶺上開花（槓のあとのツモ）",
        checked = context.rinshan,
        enabled = input.method == WinMethod.TSUMO && input.hasQuad,
    ) { checked -> update { it.copy(rinshan = checked, lastTile = it.lastTile && !checked, ippatsu = it.ippatsu && !checked) } }
    CheckRow("槍槓（加槓した牌でロン）", context.chankan, enabled = input.method == WinMethod.RON) { checked ->
        update { it.copy(chankan = checked, lastTile = it.lastTile && !checked) }
    }
    Stepper("ドラ", context.dora) { value -> update { it.copy(dora = value) } }
    Stepper("赤ドラ（手牌の5の枚数まで）", context.akaDora, max = input.fiveCount) { value -> update { it.copy(akaDora = value) } }
    Stepper("裏ドラ", context.uraDora, enabled = context.riichi != Riichi.NONE) { value -> update { it.copy(uraDora = value) } }
    Stepper("本場", context.honba) { value -> update { it.copy(honba = value) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Segmented(
    label: String,
    options: List<String>,
    selected: Int,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = selected == i,
                onClick = { onSelect(i) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {},
            ) { Text(option, maxLines = 1) }
        }
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Checkbox) { onChange(!checked) }
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Stepper(label: String, value: Int, enabled: Boolean = true, max: Int = 20, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange(value - 1) }, enabled = enabled && value > 0) { Text("−") }
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        OutlinedButton(onClick = { onChange(value + 1) }, enabled = enabled && value < max) { Text("＋") }
    }
}

// ---- 結果 ----

private fun scoreTitle(result: ScoreResult): String = when {
    result.yakumanCount >= 2 -> "${result.yakumanCount}倍役満"
    result.yakumanCount == 1 -> "役満"
    result.limit == Limit.YAKUMAN -> "${result.han}翻 数え役満"
    result.limit != null -> "${result.han}翻 ${result.limit.label}"
    else -> "${result.fu}符${result.han}翻"
}

private fun paymentText(payment: Payment, input: CalcInput): String = when (payment) {
    is Payment.Ron -> "${payment.points}点"
    is Payment.NonDealerTsumo -> "${payment.label}（合計${payment.total}点）"
    is Payment.DealerTsumo -> "${payment.label}（合計${payment.total}点）"
}.let { if (input.context.honba > 0) "$it ※${input.context.honba}本場込み" else it }

@Composable
private fun ResultDetail(outcome: ScoreOutcome, input: CalcInput) {
    if (outcome !is ScoreOutcome.Success) {
        Text(
            text = when (outcome) {
                is ScoreOutcome.Incomplete -> statusText(outcome) +
                    "（手の中に${outcome.needed}枚。鳴いた面子1つにつき3枚少なくなります）"
                ScoreOutcome.NotWinningShape -> "面子4つと雀頭1つ（または七対子・国士無双）の形になっていません。"
                ScoreOutcome.NoYaku -> "役がないとアガれません。ドラは役ではないので、ドラだけではアガれません。"
                is ScoreOutcome.Invalid -> outcome.reason
                is ScoreOutcome.Success -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val result = outcome.result
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(paymentText(result.payment, input), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(scoreTitle(result), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            Text("役", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            result.yaku.forEach { yaku ->
                Row(Modifier.fillMaxWidth()) {
                    Text(yaku.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(if (result.yakumanCount > 0) "役満" else "${yaku.han}翻", style = MaterialTheme.typography.bodyLarge)
                }
            }
            val fuResult = result.fuResult
            if (result.yakumanCount == 0 && fuResult != null) {
                Text("符", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                Text(FuQuizGenerator.explainHand(fuResult), style = MaterialTheme.typography.bodyMedium)
                val hand = input.hand!!
                Text("点数", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                Text(Explainer.explain(Hand(hand.seat, hand.method, result.fu, result.han)), style = MaterialTheme.typography.bodyMedium)
            }
            if (input.context.honba > 0) {
                val perPayer = if (input.method == WinMethod.RON) "ロンは1本につき300点" else "ツモは支払う人それぞれ1本につき100点"
                Text(
                    text = "${input.context.honba}本場: $perPayer を足しています",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

// ---- 写真から読み取る ----

@Composable
private fun PhotoSection(uiState: CalculatorUiState, onAction: (CalculatorAction) -> Unit) {
    val context = LocalContext.current
    var showKeyDialog by rememberSaveable { mutableStateOf(false) }
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = photoUri
        if (saved && uri != null) onAction(CalculatorAction.RecognizePhoto(uri))
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAction(CalculatorAction.RecognizePhoto(uri))
    }
    val loading = uiState.recognition == RecognitionStatus.Loading

    Text("写真から読み取る（試験的）", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedButton(
            onClick = {
                if (!uiState.hasApiKey) {
                    showKeyDialog = true
                } else {
                    val uri = newPhotoUri(context)
                    photoUri = uri
                    takePicture.launch(uri)
                }
            },
            enabled = !loading,
            modifier = Modifier.weight(1f),
        ) { Text("カメラで撮る") }
        OutlinedButton(
            onClick = {
                if (!uiState.hasApiKey) {
                    showKeyDialog = true
                } else {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            },
            enabled = !loading,
            modifier = Modifier.weight(1f),
        ) { Text("画像を選ぶ") }
    }
    TextButton(onClick = { showKeyDialog = true }) {
        Text(if (uiState.hasApiKey) "APIキーを変更する" else "APIキーを設定する（読み取りに必要）")
    }

    when (val status = uiState.recognition) {
        RecognitionStatus.Idle -> Unit
        RecognitionStatus.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("読み取り中…（数十秒かかることがあります）", modifier = Modifier.padding(start = 8.dp))
        }
        is RecognitionStatus.Done -> MessageCard(
            title = "読み取った牌を入れました",
            lines = listOf("違う牌があれば、押して消してから入れ直してください。ロン・ツモや風などの条件は下で選んでください。") +
                status.warnings,
            isError = false,
            onDismiss = { onAction(CalculatorAction.DismissRecognition) },
        )
        is RecognitionStatus.Failed -> MessageCard(
            title = "読み取れませんでした",
            lines = listOf(status.message),
            isError = true,
            onDismiss = { onAction(CalculatorAction.DismissRecognition) },
        )
    }

    if (showKeyDialog) {
        ApiKeyDialog(
            hasKey = uiState.hasApiKey,
            onSave = { key ->
                onAction(CalculatorAction.SaveApiKey(key))
                showKeyDialog = false
            },
            onDismiss = { showKeyDialog = false },
        )
    }
}

/** カメラアプリに渡す保存先。読み取りが終われば不要なのでキャッシュに置き、毎回上書きする */
private fun newPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    val file = File(dir, "hand.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun MessageCard(title: String, lines: List<String>, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            lines.forEach { Text("・$it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("閉じる") }
        }
    }
}

@Composable
private fun ApiKeyDialog(hasKey: Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var key by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Claude APIキー") },
        text = {
            Column {
                Text(
                    text = "写真の読み取りには、Anthropic の Claude API を使います。console.anthropic.com で発行した" +
                        "自分のAPIキーを入力してください。\n\n" +
                        "・キーはこの端末の中だけに保存し、バックアップには含めません\n" +
                        "・読み取るたびに、写真が Anthropic に送られ、APIの利用料金がかかります",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(if (hasKey) "新しいAPIキー" else "APIキー") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key) }, enabled = key.isNotBlank()) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (hasKey) TextButton(onClick = { onSave("") }) { Text("削除") }
                TextButton(onClick = onDismiss) { Text("キャンセル") }
            }
        },
    )
}
