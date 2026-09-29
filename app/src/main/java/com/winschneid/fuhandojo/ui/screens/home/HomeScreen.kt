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
import com.winschneid.fuhandojo.domain.model.QuizLevel
import com.winschneid.fuhandojo.ui.theme.FuHanDojoTheme

@Composable
fun HomeScreen(
    onStartLevel: (QuizLevel) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(uiState = uiState, onStartLevel = onStartLevel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onStartLevel: (QuizLevel) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("符ハン道場") }) },
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
                    text = "${QuizLevel.QUESTION_COUNT}問中${QuizLevel.PASS_SCORE}問正解で合格。合格すると次の級に挑戦できます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(uiState.levels, key = { it.level.name }) { item ->
                LevelCard(item = item, onClick = { onStartLevel(item.level) })
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
                isLoading = false,
            ),
            onStartLevel = {},
        )
    }
}
