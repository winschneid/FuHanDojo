package com.winschneid.fuhandojo.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.winschneid.fuhandojo.domain.model.QuizLevel
import com.winschneid.fuhandojo.domain.repository.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class LevelItem(
    val level: QuizLevel,
    val bestScore: Int?,
    val unlocked: Boolean,
) {
    val cleared: Boolean get() = (bestScore ?: 0) >= QuizLevel.PASS_SCORE
}

data class HomeUiState(
    val levels: List<LevelItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    progressRepository: ProgressRepository,
) : ViewModel() {

    val uiState = progressRepository.bestScores().map { bestScores ->
        HomeUiState(levels = levelItemsOf(bestScores), isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}

/** 編ごとに、最初の級・段と、ひとつ前に合格した級・段だけ挑戦できる */
internal fun levelItemsOf(bestScores: Map<QuizLevel, Int>): List<LevelItem> =
    QuizLevel.entries.map { level ->
        val previous = level.previous()
        LevelItem(
            level = level,
            bestScore = bestScores[level],
            unlocked = previous == null || (bestScores[previous] ?: 0) >= QuizLevel.PASS_SCORE,
        )
    }
