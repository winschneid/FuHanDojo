package com.winschneid.fuhandojo.ui.screens.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.winschneid.fuhandojo.domain.ReviewList
import com.winschneid.fuhandojo.ui.screens.quiz.QuestionSection

@Composable
fun ReviewScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReviewContent(uiState = uiState, onAction = viewModel::onAction, onNavigateBack = onNavigateBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewContent(
    uiState: ReviewUiState,
    onAction: (ReviewAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("復習") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            when {
                uiState.finished -> ReviewResult(uiState, onAction, onNavigateBack)
                uiState.items.isEmpty() -> EmptyReview(onNavigateBack)
                else -> {
                    val item = uiState.current
                    val remaining = ReviewList.REVIEW_STREAK_TO_CLEAR - item.streak
                    val progress = if (item.isCoolingDown(System.currentTimeMillis())) {
                        "前回の正解から時間をおくと数えます"
                    } else {
                        "あと${remaining}回正解で覚えた問題に"
                    }
                    QuestionSection(
                        question = item.question,
                        index = uiState.index,
                        total = uiState.items.size,
                        selectedIndex = uiState.selectedIndex,
                        isLast = uiState.isLast,
                        onSelect = { onAction(ReviewAction.Select(it)) },
                        onNext = { onAction(ReviewAction.Next) },
                        header = "${item.level.rank}「${item.level.title}」・$progress",
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyReview(onNavigateBack: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "復習する問題はありません",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            text = "級・段のクイズで間違えた問題がここにたまります。",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        OutlinedButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
        ) {
            Text("級の一覧へ")
        }
    }
}

@Composable
private fun ReviewResult(uiState: ReviewUiState, onAction: (ReviewAction) -> Unit, onNavigateBack: () -> Unit) {
    val summary = uiState.summary
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${uiState.correctCount} / ${uiState.items.size} 問正解",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 32.dp),
        )
        if (summary == null) {
            CircularProgressIndicator(Modifier.padding(top = 24.dp))
            return@Column
        }
        if (summary.cleared > 0) {
            Text(
                text = "覚えた問題: ${summary.cleared}問",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
        if (summary.almostCleared > 0) {
            Text(
                text = "あと1回正解で覚える問題: ${summary.almostCleared}問",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = if (summary.cleared > 0) 8.dp else 24.dp),
            )
        }
        Text(
            text = when {
                summary.remaining == 0 -> "復習する問題はすべて覚えました！"
                uiState.canContinue -> "残りの復習: ${summary.remaining}問"
                else -> "残りの復習: ${summary.remaining}問（時間をおいて、また復習しましょう）"
            },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "${ReviewList.COOLDOWN_HOURS}時間以上あけて${ReviewList.REVIEW_STREAK_TO_CLEAR}回続けて正解すると、覚えた問題として復習リストから外れます。間違えると回数は0に戻ります。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        if (uiState.canContinue) {
            Button(
                onClick = { onAction(ReviewAction.Restart) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
            ) {
                Text("続けて復習")
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
