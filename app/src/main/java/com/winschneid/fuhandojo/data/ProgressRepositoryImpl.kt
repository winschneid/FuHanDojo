package com.winschneid.fuhandojo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.winschneid.fuhandojo.domain.TutorialStep
import com.winschneid.fuhandojo.domain.model.QuizLevel
import com.winschneid.fuhandojo.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProgressRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ProgressRepository {

    override fun bestScores(): Flow<Map<QuizLevel, Int>> = dataStore.data.map { prefs ->
        QuizLevel.entries.mapNotNull { level -> prefs[bestScoreKey(level)]?.let { level to it } }.toMap()
    }

    override suspend fun recordScore(level: QuizLevel, score: Int): Int? {
        var previous: Int? = null
        dataStore.edit { prefs ->
            val key = bestScoreKey(level)
            previous = prefs[key]
            if (score > (previous ?: -1)) prefs[key] = score
        }
        return previous
    }

    override fun completedTutorialSteps(): Flow<Set<TutorialStep>> = dataStore.data.map { prefs ->
        TutorialStep.entries.filter { prefs[tutorialKey(it)] == true }.toSet()
    }

    override suspend fun markTutorialStepCompleted(step: TutorialStep) {
        dataStore.edit { it[tutorialKey(step)] = true }
    }

    private fun tutorialKey(step: TutorialStep) = booleanPreferencesKey("tutorial_done_${step.name}")

    // enum の name をキーにするので、QuizLevel の名前を変えると記録が引き継がれない
    private fun bestScoreKey(level: QuizLevel) = intPreferencesKey("best_score_${level.name}")
}
