package com.winschneid.fuhandojo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.winschneid.fuhandojo.domain.ReviewAnswer
import com.winschneid.fuhandojo.domain.ReviewSummary
import com.winschneid.fuhandojo.domain.model.Question
import com.winschneid.fuhandojo.domain.model.QuizLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * メモリ上の DataStore。ファイル版は Windows の JVM では上書きの名前変更に失敗するため（Android では起きない）、
 * テストではこちらを使う。
 */
private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(state.value).also { state.value = it } }
}

class ReviewRepositoryImplTest {

    private val dataStore = InMemoryPreferencesDataStore()
    private val repository = ReviewRepositoryImpl(dataStore)

    private val question = Question.LimitName(8, listOf("満貫", "跳満", "倍満", "役満"), 2, "8翻は倍満")

    @Test
    fun `間違えた問題を保存し、復習の結果を反映する`() = runBlocking {
        repository.addMistake(QuizLevel.LIMIT_NAMES, question)
        val saved = repository.items().first()
        assertEquals(listOf(question), saved.map { it.question })

        val key = saved.single().key
        assertEquals(ReviewSummary(0, 1, 1), repository.recordReview(listOf(ReviewAnswer(key, true))))
        // すぐにもう一度正解しても、時間をおいていないので覚えた問題にはならない
        assertEquals(ReviewSummary(0, 1, 1), repository.recordReview(listOf(ReviewAnswer(key, true))))
        assertEquals(1, repository.items().first().single().streak)
        // 間違えると0に戻る
        assertEquals(ReviewSummary(0, 1, 0), repository.recordReview(listOf(ReviewAnswer(key, false))))
    }

    @Test
    fun `保存形式が読めないときは空として扱う`() = runBlocking {
        dataStore.edit { it[stringPreferencesKey("review_items")] = "{壊れたデータ" }
        assertTrue(repository.items().first().isEmpty())
        // 壊れていても、そのあと間違えた問題は保存できる
        repository.addMistake(QuizLevel.LIMIT_NAMES, question)
        assertEquals(1, repository.items().first().size)
    }
}
