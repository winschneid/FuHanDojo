package com.winschneid.fuhandojo.ui.screens.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.winschneid.fuhandojo.domain.QuizGenerator
import com.winschneid.fuhandojo.domain.model.Question
import com.winschneid.fuhandojo.domain.model.QuizLevel
import com.winschneid.fuhandojo.domain.repository.ProgressRepository
import com.winschneid.fuhandojo.domain.repository.ReviewRepository
import com.winschneid.fuhandojo.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

data class QuizUiState(
    val level: QuizLevel,
    val questions: List<Question>,
    val index: Int = 0,
    /** 選んだ選択肢。null の間は未回答 */
    val selectedIndex: Int? = null,
    val correctCount: Int = 0,
    val finished: Boolean = false,
    val isNewRecord: Boolean = false,
) {
    val current: Question get() = questions[index]
    val isLast: Boolean get() = index == questions.lastIndex
    val passed: Boolean get() = correctCount >= QuizLevel.PASS_SCORE
    val nextLevel: QuizLevel? get() = level.next()
}

sealed interface QuizAction {
    data class Select(val choiceIndex: Int) : QuizAction
    data object Next : QuizAction
    data object Retry : QuizAction
}

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val progressRepository: ProgressRepository,
    private val reviewRepository: ReviewRepository,
) : ViewModel() {

    private val level = QuizLevel.valueOf(checkNotNull(savedStateHandle[Routes.Quiz.ARG_LEVEL]))

    // プロセスが終了しても続きから再開できるよう、問題はシードから作り直し、進み具合は SavedStateHandle に残す
    private val _uiState = MutableStateFlow(restoreSession())
    val uiState = _uiState.asStateFlow()

    fun onAction(action: QuizAction) {
        val state = _uiState.value
        when (action) {
            is QuizAction.Select -> {
                if (state.finished || state.selectedIndex != null) return
                val correct = action.choiceIndex == state.current.answerIndex
                // 途中でやめても残るよう、間違えたその場で復習リストに加える
                if (!correct) {
                    val question = state.current
                    viewModelScope.launch { reviewRepository.addMistake(level, question) }
                }
                setState(
                    state.copy(
                        selectedIndex = action.choiceIndex,
                        correctCount = state.correctCount + if (correct) 1 else 0,
                    ),
                )
            }
            QuizAction.Next -> {
                if (state.finished || state.selectedIndex == null) return
                if (state.isLast) finish(state) else setState(state.copy(index = state.index + 1, selectedIndex = null))
            }
            QuizAction.Retry -> setState(newSession())
        }
    }

    private fun finish(state: QuizUiState) {
        // 先に finished にして、連打で二重に記録しないようにする
        setState(state.copy(finished = true))
        viewModelScope.launch {
            val previousBest = progressRepository.recordScore(level, state.correctCount)
            // 保存中に「もう一度」を押して新しいセッションに移っていたら何もしない
            if (previousBest != null && state.correctCount > previousBest && _uiState.value.finished) {
                setState(_uiState.value.copy(isNewRecord = true))
            }
        }
    }

    private fun setState(state: QuizUiState) {
        _uiState.value = state
        persist(state)
    }

    private fun persist(state: QuizUiState) {
        savedStateHandle[KEY_INDEX] = state.index
        savedStateHandle[KEY_SELECTED] = state.selectedIndex
        savedStateHandle[KEY_CORRECT] = state.correctCount
        savedStateHandle[KEY_FINISHED] = state.finished
        savedStateHandle[KEY_NEW_RECORD] = state.isNewRecord
    }

    private fun restoreSession(): QuizUiState {
        val seed = savedStateHandle.get<Long>(KEY_SEED) ?: return newSession()
        return QuizUiState(
            level = level,
            questions = QuizGenerator.generate(level, Random(seed)),
            index = savedStateHandle[KEY_INDEX] ?: 0,
            selectedIndex = savedStateHandle[KEY_SELECTED],
            correctCount = savedStateHandle[KEY_CORRECT] ?: 0,
            finished = savedStateHandle[KEY_FINISHED] ?: false,
            isNewRecord = savedStateHandle[KEY_NEW_RECORD] ?: false,
        )
    }

    private fun newSession(): QuizUiState {
        val seed = Random.nextLong()
        savedStateHandle[KEY_SEED] = seed
        return QuizUiState(level = level, questions = QuizGenerator.generate(level, Random(seed))).also(::persist)
    }

    private companion object {
        const val KEY_SEED = "seed"
        const val KEY_INDEX = "index"
        const val KEY_SELECTED = "selected"
        const val KEY_CORRECT = "correct"
        const val KEY_FINISHED = "finished"
        const val KEY_NEW_RECORD = "new_record"
    }
}
