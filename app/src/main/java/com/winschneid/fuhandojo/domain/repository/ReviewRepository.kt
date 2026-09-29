package com.winschneid.fuhandojo.domain.repository

import com.winschneid.fuhandojo.domain.ReviewAnswer
import com.winschneid.fuhandojo.domain.ReviewItem
import com.winschneid.fuhandojo.domain.ReviewSummary
import com.winschneid.fuhandojo.domain.model.Question
import com.winschneid.fuhandojo.domain.model.QuizLevel
import kotlinx.coroutines.flow.Flow

interface ReviewRepository {
    /** 復習リスト全体 */
    fun items(): Flow<List<ReviewItem>>

    /** 間違えた問題を復習リストに加える */
    suspend fun addMistake(level: QuizLevel, question: Question)

    /** 復習の結果を反映する */
    suspend fun recordReview(answers: List<ReviewAnswer>): ReviewSummary
}
