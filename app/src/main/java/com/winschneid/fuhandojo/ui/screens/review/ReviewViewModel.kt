package com.winschneid.fuhandojo.ui.screens.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.winschneid.fuhandojo.domain.ReviewAnswer
import com.winschneid.fuhandojo.domain.ReviewItem
import com.winschneid.fuhandojo.domain.ReviewList
import com.winschneid.fuhandojo.domain.ReviewSummary
import com.winschneid.fuhandojo.domain.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val isLoading: Boolean = true,
    val items: List<ReviewItem> = emptyList(),
    val index: Int = 0,
    /** 選んだ選択肢。null の間は未回答 */
    val selectedIndex: Int? = null,
    /** 答えた問題の正誤（items の順） */
    val answers: List<Boolean> = emptyList(),
    val finished: Boolean = false,
    /** 復習の結果を保存し終えたら入る */
    val summary: ReviewSummary? = null,
    /** この画面でまだ答えていない問題が残っていて、続けて復習できる */
    val canContinue: Boolean = false,
) {
    val current: ReviewItem get() = items[index]
    val isLast: Boolean get() = index == items.lastIndex
    val correctCount: Int get() = answers.count { it }
}

sealed interface ReviewAction {
    data class Select(val choiceIndex: Int) : ReviewAction
    data object Next : ReviewAction
    data object Restart : ReviewAction
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ReviewRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState = _uiState.asStateFlow()

    // 直前に正解した問題がすぐまた出て、数分で「2回続けて正解」にならないよう、
    // この画面で答えた問題は「続けて復習」では出さない
    private val answeredKeys = mutableSetOf<String>()

    init {
        load(restore = true)
    }

    fun onAction(action: ReviewAction) {
        val state = _uiState.value
        when (action) {
            is ReviewAction.Select -> {
                if (state.isLoading || state.finished || state.selectedIndex != null) return
                val correct = action.choiceIndex == state.current.question.answerIndex
                setState(state.copy(selectedIndex = action.choiceIndex, answers = state.answers + correct))
            }
            ReviewAction.Next -> {
                if (state.finished || state.selectedIndex == null) return
                if (state.isLast) finish(state) else setState(state.copy(index = state.index + 1, selectedIndex = null))
            }
            ReviewAction.Restart -> {
                savedStateHandle.remove<ArrayList<String>>(KEY_KEYS)
                _uiState.value = ReviewUiState()
                load(restore = false)
            }
        }
    }

    /**
     * 復習する問題を読み込む。プロセスが終了して作り直されたときは、同じ問題の続きから再開する。
     * 結果はまとめて最後に保存するので、途中の問題は復習リストに残ったままになっている。
     */
    private fun load(restore: Boolean) {
        viewModelScope.launch {
            val all = repository.items().first()
            val savedKeys = if (restore) savedStateHandle.get<ArrayList<String>>(KEY_KEYS) else null
            val restored = savedKeys?.mapNotNull { key -> all.find { it.key == key } }
            if (restored != null && restored.size == savedKeys.size && restored.isNotEmpty()) {
                setState(
                    ReviewUiState(
                        isLoading = false,
                        items = restored,
                        index = savedStateHandle[KEY_INDEX] ?: 0,
                        selectedIndex = savedStateHandle[KEY_SELECTED],
                        answers = savedStateHandle.get<BooleanArray>(KEY_ANSWERS)?.toList().orEmpty(),
                    ),
                )
            } else {
                val items = ReviewList.nextSession(all.filter { it.key !in answeredKeys }, System.currentTimeMillis())
                savedStateHandle[KEY_KEYS] = ArrayList(items.map { it.key })
                setState(ReviewUiState(isLoading = false, items = items))
            }
        }
    }

    private fun finish(state: ReviewUiState) {
        // 先に finished にして、連打で二重に記録しないようにする
        setState(state.copy(finished = true))
        viewModelScope.launch {
            val answers = state.items.zip(state.answers) { item, correct -> ReviewAnswer(item.key, correct) }
            val summary = repository.recordReview(answers)
            answeredKeys += state.items.map { it.key }
            val canContinue = repository.items().first().any { it.key !in answeredKeys }
            savedStateHandle.remove<ArrayList<String>>(KEY_KEYS)
            if (_uiState.value.finished) _uiState.value = _uiState.value.copy(summary = summary, canContinue = canContinue)
        }
    }

    private fun setState(state: ReviewUiState) {
        _uiState.value = state
        savedStateHandle[KEY_INDEX] = state.index
        savedStateHandle[KEY_SELECTED] = state.selectedIndex
        savedStateHandle[KEY_ANSWERS] = state.answers.toBooleanArray()
    }

    private companion object {
        const val KEY_KEYS = "review_keys"
        const val KEY_INDEX = "review_index"
        const val KEY_SELECTED = "review_selected"
        const val KEY_ANSWERS = "review_answers"
    }
}
