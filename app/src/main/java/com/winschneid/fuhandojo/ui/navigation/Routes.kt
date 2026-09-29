package com.winschneid.fuhandojo.ui.navigation

import com.winschneid.fuhandojo.domain.model.QuizLevel

sealed class Routes(val route: String) {
    data object Home : Routes("home")
    data object Quiz : Routes("quiz/{level}") {
        const val ARG_LEVEL = "level"
        fun createRoute(level: QuizLevel) = "quiz/${level.name}"
    }
    data object ScoreTable : Routes("score_table")
}
