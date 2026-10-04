package com.winschneid.fuhandojo.domain.repository

import com.winschneid.fuhandojo.domain.TutorialStep
import com.winschneid.fuhandojo.domain.model.QuizLevel
import kotlinx.coroutines.flow.Flow

interface ProgressRepository {
    /** 級ごとの最高正解数。まだ挑戦していない級は含まない */
    fun bestScores(): Flow<Map<QuizLevel, Int>>

    /**
     * 最高正解数を上回ったときだけ保存する。
     * @return 保存前の最高正解数（初挑戦なら null）
     */
    suspend fun recordScore(level: QuizLevel, score: Int): Int?

    /** 入門で完了したステップ */
    fun completedTutorialSteps(): Flow<Set<TutorialStep>>

    suspend fun markTutorialStepCompleted(step: TutorialStep)
}
