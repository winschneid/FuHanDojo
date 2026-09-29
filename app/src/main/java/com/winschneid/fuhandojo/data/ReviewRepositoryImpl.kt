package com.winschneid.fuhandojo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.winschneid.fuhandojo.domain.ReviewAnswer
import com.winschneid.fuhandojo.domain.ReviewItem
import com.winschneid.fuhandojo.domain.ReviewList
import com.winschneid.fuhandojo.domain.ReviewSummary
import com.winschneid.fuhandojo.domain.model.Question
import com.winschneid.fuhandojo.domain.model.QuizLevel
import com.winschneid.fuhandojo.domain.repository.ReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ReviewRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReviewRepository {

    override fun items(): Flow<List<ReviewItem>> = dataStore.data.map { decode(it[KEY]) }

    override suspend fun addMistake(level: QuizLevel, question: Question) {
        dataStore.edit { prefs ->
            prefs[KEY] = encode(ReviewList.afterMistake(decode(prefs[KEY]), level, question, System.currentTimeMillis()))
        }
    }

    override suspend fun recordReview(answers: List<ReviewAnswer>): ReviewSummary {
        var summary = ReviewSummary(cleared = 0, remaining = 0)
        dataStore.edit { prefs ->
            val (items, result) = ReviewList.afterReview(decode(prefs[KEY]), answers, System.currentTimeMillis())
            prefs[KEY] = encode(items)
            summary = result
        }
        return summary
    }

    private fun encode(items: List<ReviewItem>): String = json.encodeToString(items)

    // 保存形式が読めなくなっても（アプリの更新で問題の形が変わったなど）アプリが止まらないよう、空として扱う
    private fun decode(text: String?): List<ReviewItem> {
        if (text.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<ReviewItem>>(text)
        } catch (e: SerializationException) {
            emptyList()
        } catch (e: IllegalArgumentException) {
            emptyList()
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("review_items")
        val json = Json { ignoreUnknownKeys = true }
    }
}
