package com.winschneid.fuhandojo.ui.screens.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.winschneid.fuhandojo.domain.QuizGenerator
import com.winschneid.fuhandojo.domain.model.Question
import com.winschneid.fuhandojo.domain.model.QuizLevel
import com.winschneid.fuhandojo.domain.model.Seat
import com.winschneid.fuhandojo.domain.model.WinMethod
import com.winschneid.fuhandojo.ui.theme.FuHanDojoTheme
import kotlin.random.Random

@Composable
fun QuizScreen(
    onNavigateBack: () -> Unit,
    onStartLevel: (QuizLevel) -> Unit,
    viewModel: QuizViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    QuizContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        onStartLevel = onStartLevel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizContent(
    uiState: QuizUiState,
    onAction: (QuizAction) -> Unit,
    onNavigateBack: () -> Unit,
    onStartLevel: (QuizLevel) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${uiState.level.rank} ${uiState.level.title}") },
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
            if (uiState.finished) {
                ResultSection(
                    uiState = uiState,
                    onAction = onAction,
                    onNavigateBack = onNavigateBack,
                    onStartLevel = onStartLevel,
                )
            } else {
                QuestionSection(uiState = uiState, onAction = onAction)
            }
        }
    }
}

@Composable
private fun QuestionSection(uiState: QuizUiState, onAction: (QuizAction) -> Unit) {
    val question = uiState.current
    val answered = uiState.selectedIndex != null

    Text(
        text = "${uiState.index + 1} / ${uiState.questions.size}",
        style = MaterialTheme.typography.labelLarge,
    )
    LinearProgressIndicator(
        progress = { (uiState.index + if (answered) 1 else 0).toFloat() / uiState.questions.size },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 24.dp),
    )

    Prompt(question)
    Spacer(Modifier.height(24.dp))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        question.choices.forEachIndexed { i, choice ->
            ChoiceButton(
                text = choice,
                state = when {
                    !answered -> ChoiceState.Idle
                    i == question.answerIndex -> ChoiceState.Correct
                    i == uiState.selectedIndex -> ChoiceState.Wrong
                    else -> ChoiceState.Other
                },
                onClick = { onAction(QuizAction.Select(i)) },
            )
        }
    }

    if (answered) {
        val correct = uiState.selectedIndex == question.answerIndex
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                // 正誤を読み上げで伝える
                .semantics { liveRegion = LiveRegionMode.Polite },
            colors = CardDefaults.cardColors(
                containerColor = if (correct) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = if (correct) "正解！" else "不正解… 正解は ${question.answer}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = question.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Button(
            onClick = { onAction(QuizAction.Next) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Text(if (uiState.isLast) "結果を見る" else "次へ")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Prompt(question: Question) {
    when (question) {
        is Question.LimitName -> Text(
            text = "${question.han}翻は？",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        is Question.Points -> Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val hand = question.hand
            // 狭い画面や大きな文字設定でもはみ出さないよう折り返す
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tag(hand.seat.label, emphasized = hand.seat == Seat.DEALER)
                Tag(hand.method.label)
                if (question.showFu) Tag("${hand.fu}符")
                Tag("${hand.han}翻")
            }
            Text(
                text = "何点？",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (hand.method == WinMethod.TSUMO) {
                Text(
                    text = if (hand.seat == Seat.DEALER) "子それぞれの支払い（〇〇オール）" else "子の支払い-親の支払い",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Tag(text: String, emphasized: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (emphasized) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

private enum class ChoiceState { Idle, Correct, Wrong, Other }

/** 「1000-2000」を「マイナス」と読み上げないよう、支払いの内訳として読ませる */
private fun spokenLabel(choice: String): String {
    val parts = choice.split("-")
    return if (parts.size == 2) "子${parts[0]}点、親${parts[1]}点" else choice
}

@Composable
private fun ChoiceButton(text: String, state: ChoiceState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (state) {
        ChoiceState.Correct -> colors.primaryContainer to colors.onPrimaryContainer
        ChoiceState.Wrong -> colors.errorContainer to colors.onErrorContainer
        ChoiceState.Other -> Color.Transparent to colors.onSurfaceVariant
        ChoiceState.Idle -> Color.Transparent to colors.onSurface
    }
    val result = when (state) {
        ChoiceState.Correct -> "、正解"
        ChoiceState.Wrong -> "、不正解"
        else -> ""
    }
    OutlinedButton(
        onClick = onClick,
        // 回答後も正誤の色を見せたいので enabled は使わず、2回目以降の選択は ViewModel 側で無視する
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clearAndSetSemantics { contentDescription = spokenLabel(text) + result },
        colors = ButtonDefaults.outlinedButtonColors(containerColor = container, contentColor = content),
    ) {
        // 正誤アイコンが付いても点数の文字が中央からずれないよう、アイコンは右端に重ねる
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleLarge)
            val icon = when (state) {
                ChoiceState.Correct -> Icons.Default.Check
                ChoiceState.Wrong -> Icons.Default.Close
                else -> null
            }
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ResultSection(
    uiState: QuizUiState,
    onAction: (QuizAction) -> Unit,
    onNavigateBack: () -> Unit,
    onStartLevel: (QuizLevel) -> Unit,
) {
    val nextLevel = uiState.nextLevel.takeIf { uiState.passed }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (uiState.passed) "合格" else "不合格",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = if (uiState.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            text = "${uiState.correctCount} / ${uiState.questions.size} 問正解",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (uiState.isNewRecord) {
            Text(
                text = "自己ベスト更新！",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            text = when {
                !uiState.passed -> "${QuizLevel.PASS_SCORE}問以上正解で合格です。解説を見ながらもう一度挑戦しましょう。"
                nextLevel != null -> "${nextLevel.rank}「${nextLevel.title}」に挑戦できるようになりました。"
                else -> "すべての級に合格しました。おめでとうございます！"
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        if (nextLevel != null) {
            Button(
                onClick = { onStartLevel(nextLevel) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
            ) {
                Text("${nextLevel.rank}に進む")
            }
            OutlinedButton(
                onClick = { onAction(QuizAction.Retry) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("もう一度")
            }
        } else {
            Button(
                onClick = { onAction(QuizAction.Retry) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
            ) {
                Text("もう一度")
            }
        }
        OutlinedButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("級の一覧へ")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun QuizContentPreview() {
    val level = QuizLevel.TSUMO_30_40
    FuHanDojoTheme {
        QuizContent(
            uiState = QuizUiState(
                level = level,
                questions = QuizGenerator.generate(level, Random(1)),
                index = 2,
                selectedIndex = 0,
                correctCount = 2,
            ),
            onAction = {},
            onNavigateBack = {},
            onStartLevel = {},
        )
    }
}
