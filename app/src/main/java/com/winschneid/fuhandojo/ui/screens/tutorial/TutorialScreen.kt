package com.winschneid.fuhandojo.ui.screens.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.winschneid.fuhandojo.domain.Tutorial
import com.winschneid.fuhandojo.domain.TutorialExample
import com.winschneid.fuhandojo.domain.TutorialStep
import com.winschneid.fuhandojo.domain.model.QuizLevel
import com.winschneid.fuhandojo.ui.components.TileGroup
import com.winschneid.fuhandojo.ui.screens.quiz.QuestionSection
import com.winschneid.fuhandojo.ui.theme.FuHanDojoTheme

@Composable
fun TutorialScreen(
    onNavigateBack: () -> Unit,
    onStartLevel: (QuizLevel) -> Unit,
    viewModel: TutorialViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TutorialContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        onStartLevel = onStartLevel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialContent(
    uiState: TutorialUiState,
    onAction: (TutorialAction) -> Unit,
    onNavigateBack: () -> Unit,
    onStartLevel: (QuizLevel) -> Unit,
) {
    val inStep = uiState.phase != TutorialPhase.LIST
    // ステップの途中で戻ると、画面を閉じずにステップの一覧へ戻る
    BackHandler(enabled = inStep) { onAction(TutorialAction.BackToList) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // ステップ名は「1・9・字牌は2倍」のように数字で始まることがあるので、番号とは分けて本文に出す
                    Text(if (inStep) "ステップ${uiState.step.ordinal + 1} / ${TutorialStep.entries.size}" else "符の入門")
                },
                navigationIcon = {
                    IconButton(onClick = { if (inStep) onAction(TutorialAction.BackToList) else onNavigateBack() }) {
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
            when (uiState.phase) {
                TutorialPhase.LIST -> StepList(uiState.completed, onAction)
                TutorialPhase.INTRO -> StepIntro(uiState.step, onAction)
                TutorialPhase.QUIZ -> QuestionSection(
                    question = uiState.current,
                    index = uiState.index,
                    total = uiState.questions.size,
                    selectedIndex = uiState.selectedIndex,
                    isLast = uiState.isLast,
                    onSelect = { onAction(TutorialAction.Select(it)) },
                    onNext = { onAction(TutorialAction.Next) },
                    header = "「${uiState.step.title}」",
                )
                TutorialPhase.DONE -> StepResult(uiState, onAction, onStartLevel)
            }
        }
    }
}

@Composable
private fun StepList(completed: Set<TutorialStep>, onAction: (TutorialAction) -> Unit) {
    Text(
        text = "符の数え方の基本を、ルール1つずつ覚えます。各ステップは説明のあとに${Tutorial.QUESTIONS_PER_STEP}問" +
            "（${Tutorial.PASS_SCORE}問正解で完了）。どのステップからでも始められます。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TutorialStep.entries.forEach { step ->
            val done = step in completed
            Card(
                onClick = { onAction(TutorialAction.OpenStep(step)) },
                modifier = Modifier.fillMaxWidth(),
                colors = if (done) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${step.ordinal + 1}", fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                    )
                    if (done) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "完了", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepIntro(step: TutorialStep, onAction: (TutorialAction) -> Unit) {
    Text(
        text = step.title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = step.rule,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
        )
    }
    Text(
        text = "例",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Tutorial.examples(step).forEach { ExampleView(it) }
    }
    Button(
        onClick = { onAction(TutorialAction.StartQuestions) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
    ) {
        Text("問題に挑戦（${Tutorial.QUESTIONS_PER_STEP}問）")
    }
}

@Composable
private fun ExampleView(example: TutorialExample) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileGroup(
                tiles = example.tiles,
                tileWidth = 34.dp,
                faceDownIndices = if (example.faceDownEnds) setOf(0, example.tiles.lastIndex) else emptySet(),
            )
            example.winningTile?.let { win ->
                Text("＋", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp))
                TileGroup(tiles = listOf(win), tileWidth = 34.dp, highlightLast = true)
            }
        }
        Text(
            text = example.caption,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun StepResult(
    uiState: TutorialUiState,
    onAction: (TutorialAction) -> Unit,
    onStartLevel: (QuizLevel) -> Unit,
) {
    val next = uiState.nextStep
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${uiState.correctCount} / ${uiState.questions.size} 問正解",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            text = when {
                !uiState.passed -> "${Tutorial.PASS_SCORE}問正解で完了です。説明を見直して、もう一度やってみましょう。"
                next != null -> "「${uiState.step.title}」完了！"
                else -> "入門はここまで。面子・雀頭・待ちの符を覚えたら、初段から問題に挑戦しましょう。"
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        when {
            !uiState.passed -> {
                Button(
                    onClick = { onAction(TutorialAction.OpenStep(uiState.step)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                ) {
                    Text("説明を見直す")
                }
                OutlinedButton(
                    onClick = { onAction(TutorialAction.StartQuestions) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text("もう一度")
                }
            }
            next != null -> Button(
                onClick = { onAction(TutorialAction.OpenStep(next)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
            ) {
                Text("次のステップ「${next.title}」へ")
            }
            else -> Button(
                onClick = { onStartLevel(QuizLevel.MELD_FU) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
            ) {
                Text("初段に挑戦する")
            }
        }
        OutlinedButton(
            onClick = { onAction(TutorialAction.BackToList) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("ステップ一覧へ")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StepIntroPreview() {
    FuHanDojoTheme {
        TutorialContent(
            uiState = TutorialUiState(phase = TutorialPhase.INTRO, step = TutorialStep.QUADS),
            onAction = {},
            onNavigateBack = {},
            onStartLevel = {},
        )
    }
}
