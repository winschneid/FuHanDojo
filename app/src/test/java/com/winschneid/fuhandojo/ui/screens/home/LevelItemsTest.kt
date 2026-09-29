package com.winschneid.fuhandojo.ui.screens.home

import com.winschneid.fuhandojo.domain.model.Course
import com.winschneid.fuhandojo.domain.model.QuizLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LevelItemsTest {

    @Test
    fun `未挑戦なら各編の最初の級・段だけ挑戦できる`() {
        val unlocked = levelItemsOf(emptyMap()).filter { it.unlocked }.map { it.level }
        assertEquals(listOf(QuizLevel.LIMIT_NAMES, QuizLevel.MELD_FU), unlocked)
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

    @Test
    fun `1級に合格しても符計算編の次の段は解放されない`() {
        val items = levelItemsOf(mapOf(QuizLevel.MIXED to QuizLevel.QUESTION_COUNT))
        assertEquals(false, items.single { it.level == QuizLevel.PAIR_WAIT_FU }.unlocked)
    }

    @Test
    fun `次の級・段は同じ編の中だけ`() {
        assertNull(QuizLevel.MIXED.next())
        assertNull(QuizLevel.MELD_FU.previous())
        assertEquals(QuizLevel.FU_SUM, QuizLevel.PAIR_WAIT_FU.next())
        Course.entries.forEach { course ->
            val levels = QuizLevel.entries.filter { it.course == course }
            assertEquals(levels.drop(1), levels.dropLast(1).map { it.next() })
        }
    }
}
