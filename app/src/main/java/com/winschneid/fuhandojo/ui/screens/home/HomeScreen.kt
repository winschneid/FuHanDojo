package com.winschneid.fuhandojo.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.winschneid.fuhandojo.domain.ReviewList
import com.winschneid.fuhandojo.domain.model.QuizLevel
import com.winschneid.fuhandojo.ui.theme.FuHanDojoTheme

@Composable
fun HomeScreen(
    onStartLevel: (QuizLevel) -> Unit,
    onOpenScoreTable: () -> Unit,
    onStartReview: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        uiState = uiState,
        onStartLevel = onStartLevel,
        onOpenScoreTable = onOpenScoreTable,
        onStartReview = onStartReview,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onStartLevel: (QuizLevel) -> Unit,
    onOpenScoreTable: () -> Unit,
    onStartReview: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("符ハン道場") },
                actions = {
                    TextButton(onClick = onOpenScoreTable) { Text("早見表") }
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "${QuizLevel.QUESTION_COUNT}問中${QuizLevel.PASS_SCORE}問正解で合格。合格すると次の級・段に挑戦できます。点数編と符計算編は、どちらからでも始められます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item(key = "review") {
                ReviewCard(count = uiState.reviewCount, onClick = onStartReview)
            }
            uiState.levels.groupBy { it.level.course }.forEach { (course, levels) ->
                item(key = course.name) {
                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(levels, key = { it.level.name }) { item ->
                    LevelCard(item = item, onClick = { onStartLevel(item.level) })
                }
            }
        }
    }
}

@Composable
private fun LevelCard(item: LevelItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = item.unlocked,
        modifier = Modifier.fillMaxWidth(),
        colors = if (item.cleared) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (item.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(item.level.rank, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                Text(item.level.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = item.level.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.unlocked) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
                )
            }
            when {
                !item.unlocked -> Icon(Icons.Default.Lock, contentDescription = "未解放")
                item.bestScore != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (item.cleared) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "合格",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = "最高 ${item.bestScore}/${QuizLevel.QUESTION_COUNT}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    FuHanDojoTheme {
        HomeContent(
            uiState = HomeUiState(
                levels = levelItemsOf(mapOf(QuizLevel.LIMIT_NAMES to 10, QuizLevel.NON_DEALER_LIMIT_RON to 6)),
                reviewCount = 3,
                isLoading = false,
            ),
            onStartLevel = {},
            onOpenScoreTable = {},
            onStartReview = {},
        )
    }
}

@Composable
private fun ReviewCard(count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (count > 0) "復習 ${count}問" else "復習",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (count > 0) {
                        "間違えた問題を解き直す。時間をおいて${ReviewList.REVIEW_STREAK_TO_CLEAR}回続けて正解すると覚えた問題になります"
                    } else {
                        "間違えた問題はここにたまります"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onClick,
                enabled = count > 0,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Text("復習する")
            }
        }
    }
}
