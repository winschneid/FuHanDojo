package com.winschneid.fuhandojo.ui.screens.tutorial

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.winschneid.fuhandojo.domain.Tutorial
import com.winschneid.fuhandojo.domain.TutorialStep
import com.winschneid.fuhandojo.domain.model.Question
import com.winschneid.fuhandojo.domain.repository.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

enum class TutorialPhase {
    /** ステップの一覧 */
    LIST,

    /** ルールの説明 */
    INTRO,

    /** 確認の問題 */
    QUIZ,

    /** ステップの結果 */
    DONE,
}

data class TutorialUiState(
    val phase: TutorialPhase = TutorialPhase.LIST,
    val completed: Set<TutorialStep> = emptySet(),
    val step: TutorialStep = TutorialStep.entries.first(),
    val questions: List<Question> = emptyList(),
    val index: Int = 0,
    /** 選んだ選択肢。null の間は未回答 */
    val selectedIndex: Int? = null,
    val correctCount: Int = 0,
) {
    val current: Question get() = questions[index]
    val isLast: Boolean get() = index == questions.lastIndex
    val passed: Boolean get() = correctCount >= Tutorial.PASS_SCORE
    val nextStep: TutorialStep? get() = TutorialStep.entries.getOrNull(step.ordinal + 1)
}

sealed interface TutorialAction {
    data class OpenStep(val step: TutorialStep) : TutorialAction
    data object StartQuestions : TutorialAction
    data class Select(val choiceIndex: Int) : TutorialAction
    data object Next : TutorialAction
    data object BackToList : TutorialAction
}

@HiltViewModel
class TutorialViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    // プロセスが終了しても続きから再開できるよう、問題はシードから作り直し、進み具合は SavedStateHandle に残す
    private val state = MutableStateFlow(restore())

    val uiState = combine(state, progressRepository.completedTutorialSteps()) { state, completed ->
        state.copy(completed = completed)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = state.value,
    )

    fun onAction(action: TutorialAction) {
        val current = state.value
        when (action) {
            is TutorialAction.OpenStep -> setState(TutorialUiState(phase = TutorialPhase.INTRO, step = action.step))
            TutorialAction.StartQuestions -> {
                val seed = Random.nextLong()
                savedStateHandle[KEY_SEED] = seed
                setState(
                    TutorialUiState(
                        phase = TutorialPhase.QUIZ,
                        step = current.step,
                        questions = Tutorial.questions(current.step, Random(seed)),
                    ),
                )
            }
            is TutorialAction.Select -> {
                if (current.phase != TutorialPhase.QUIZ || current.selectedIndex != null) return
                val correct = action.choiceIndex == current.current.answerIndex
                setState(
                    current.copy(
                        selectedIndex = action.choiceIndex,
                        correctCount = current.correctCount + if (correct) 1 else 0,
                    ),
                )
            }
            TutorialAction.Next -> {
                if (current.phase != TutorialPhase.QUIZ || current.selectedIndex == null) return
                if (current.isLast) {
                    setState(current.copy(phase = TutorialPhase.DONE))
                    if (current.passed) {
                        viewModelScope.launch { progressRepository.markTutorialStepCompleted(current.step) }
                    }
                } else {
                    setState(current.copy(index = current.index + 1, selectedIndex = null))
                }
            }
            TutorialAction.BackToList -> setState(TutorialUiState())
        }
    }

    private fun setState(newState: TutorialUiState) {
        state.value = newState
        savedStateHandle[KEY_PHASE] = newState.phase.name
        savedStateHandle[KEY_STEP] = newState.step.name
        savedStateHandle[KEY_INDEX] = newState.index
        savedStateHandle[KEY_SELECTED] = newState.selectedIndex
        savedStateHandle[KEY_CORRECT] = newState.correctCount
    }

    private fun restore(): TutorialUiState {
        val phase = savedStateHandle.get<String>(KEY_PHASE)?.let(TutorialPhase::valueOf) ?: return TutorialUiState()
        val step = savedStateHandle.get<String>(KEY_STEP)?.let(TutorialStep::valueOf) ?: return TutorialUiState()
        val seed = savedStateHandle.get<Long>(KEY_SEED)
        val questions = if (seed != null) Tutorial.questions(step, Random(seed)) else emptyList()
        // 問題を作り直せないときは、そのステップの説明から始める
        if (phase >= TutorialPhase.QUIZ && questions.isEmpty()) return TutorialUiState(phase = TutorialPhase.INTRO, step = step)
        return TutorialUiState(
            phase = phase,
            step = step,
            questions = if (phase >= TutorialPhase.QUIZ) questions else emptyList(),
            index = savedStateHandle[KEY_INDEX] ?: 0,
            selectedIndex = savedStateHandle[KEY_SELECTED],
            correctCount = savedStateHandle[KEY_CORRECT] ?: 0,
        )
    }

    private companion object {
        const val KEY_PHASE = "tutorial_phase"
        const val KEY_STEP = "tutorial_step"
        const val KEY_SEED = "tutorial_seed"
        const val KEY_INDEX = "tutorial_index"
        const val KEY_SELECTED = "tutorial_selected"
        const val KEY_CORRECT = "tutorial_correct"
    }
}
