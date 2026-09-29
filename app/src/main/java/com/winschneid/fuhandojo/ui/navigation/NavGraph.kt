package com.winschneid.fuhandojo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.winschneid.fuhandojo.ui.screens.home.HomeScreen
import com.winschneid.fuhandojo.ui.screens.quiz.QuizScreen
import com.winschneid.fuhandojo.ui.screens.review.ReviewScreen
import com.winschneid.fuhandojo.ui.screens.table.ScoreTableScreen

/**
 * 遷移中（前面にない間）の操作は無視する。
 * 連打で同じ画面を重ねて開いたり、Home まで戻して空白画面になったりするのを防ぐ。
 */
private fun NavBackStackEntry.isResumed() = lifecycle.currentState == Lifecycle.State.RESUMED

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.Home.route) {
        composable(Routes.Home.route) { entry ->
            HomeScreen(
                onStartLevel = { level ->
                    if (entry.isResumed()) navController.navigate(Routes.Quiz.createRoute(level))
                },
                onOpenScoreTable = {
                    if (entry.isResumed()) navController.navigate(Routes.ScoreTable.route)
                },
                onStartReview = {
                    if (entry.isResumed()) navController.navigate(Routes.Review.route)
                },
            )
        }
        composable(
            route = Routes.Quiz.route,
            arguments = listOf(navArgument(Routes.Quiz.ARG_LEVEL) { type = NavType.StringType }),
        ) { entry ->
            QuizScreen(
                onNavigateBack = {
                    if (entry.isResumed()) navController.popBackStack()
                },
                onStartLevel = { level ->
                    if (entry.isResumed()) {
                        navController.navigate(Routes.Quiz.createRoute(level)) {
                            popUpTo(Routes.Home.route)
                        }
                    }
                },
            )
        }
        composable(Routes.ScoreTable.route) { entry ->
            ScoreTableScreen(
                onNavigateBack = {
                    if (entry.isResumed()) navController.popBackStack()
                },
            )
        }
        composable(Routes.Review.route) { entry ->
            ReviewScreen(
                onNavigateBack = {
                    if (entry.isResumed()) navController.popBackStack()
                },
            )
        }
    }
}
