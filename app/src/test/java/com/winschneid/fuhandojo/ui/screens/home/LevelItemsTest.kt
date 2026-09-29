package com.winschneid.fuhandojo.ui.screens.home

import com.winschneid.fuhandojo.domain.model.QuizLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class LevelItemsTest {

    @Test
    fun `未挑戦なら最初の級だけ挑戦できる`() {
        val items = levelItemsOf(emptyMap())
        assertEquals(listOf(true) + List(QuizLevel.entries.size - 1) { false }, items.map { it.unlocked })
    }

    @Test
    fun `合格点に届いた級の次だけが解放される`() {
        val items = levelItemsOf(
            mapOf(
                QuizLevel.LIMIT_NAMES to QuizLevel.PASS_SCORE,
                QuizLevel.NON_DEALER_LIMIT_RON to QuizLevel.PASS_SCORE - 1,
            ),
        )
        assertEquals(listOf(true, true, false), items.take(3).map { it.unlocked })
        assertEquals(listOf(true, false, false), items.take(3).map { it.cleared })
    }
}
