package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Hand
import com.winschneid.fuhandojo.domain.model.Limit
import com.winschneid.fuhandojo.domain.model.Question
import com.winschneid.fuhandojo.domain.model.QuizLevel
import com.winschneid.fuhandojo.domain.model.Seat
import com.winschneid.fuhandojo.domain.model.WinMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizGeneratorTest {

    @Test
    fun `どの級でも4つの異なる選択肢を持つ問題を10問作り、正解が点数計算と一致する`() {
        repeat(50) { seed ->
            QuizLevel.entries.forEach { level ->
                val questions = QuizGenerator.generate(level, Random(seed))
                assertEquals(QuizLevel.QUESTION_COUNT, questions.size)
                questions.forEach { q ->
                    assertEquals("$level $q", 4, q.choices.distinct().size)
                    when (q) {
                        is Question.LimitName -> assertEquals(Limit.ofHan(q.han)!!.label, q.answer)
                        is Question.Points -> {
                            assertTrue(ScoreCalculator.isValid(q.hand))
                            assertEquals(ScoreCalculator.payment(q.hand).label, q.answer)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `同じシードからは同じ問題が作られる`() {
        QuizLevel.entries.forEach { level ->
            assertEquals(QuizGenerator.generate(level, Random(42)), QuizGenerator.generate(level, Random(42)))
        }
    }

    @Test
    fun `ツモの選択肢はすべて正解と同じ書式になる`() {
        val questions = QuizGenerator.generate(QuizLevel.TSUMO_ALL_FU, Random(0), count = 100)
        questions.filterIsInstance<Question.Points>().forEach { q ->
            val isDealer = q.hand.seat == Seat.DEALER
            q.choices.forEach { choice ->
                assertEquals("$q", isDealer, choice.endsWith("オール"))
            }
        }
    }

    @Test
    fun `範囲が狭い級では一巡するまで同じ問題を出さず、続けて同じ問題も出さない`() {
        repeat(50) { seed ->
            val questions = QuizGenerator.generate(QuizLevel.LIMIT_NAMES, Random(seed), count = 30)
            val hans = questions.map { (it as Question.LimitName).han }
            assertEquals(9, hans.take(9).distinct().size)
            hans.zipWithNext().forEach { (a, b) -> assertNotEquals("seed=$seed $hans", a, b) }
        }
    }

    @Test
    fun `満貫の問題で正解がいつも最小の選択肢にはならない`() {
        val manganQuestions = (0 until 50).flatMap { QuizGenerator.generate(QuizLevel.NON_DEALER_LIMIT_RON, Random(it)) }
            .filterIsInstance<Question.Points>()
            .filter { it.hand.han == 5 }
        assertTrue(manganQuestions.isNotEmpty())
        assertTrue(manganQuestions.all { "7700" in it.choices })
    }

    @Test
    fun `ツモの解説では支払う人ごとの切り上げを説明する`() {
        val text = Explainer.explain(Hand(Seat.NON_DEALER, WinMethod.TSUMO, 30, 4))
        assertTrue(text, "子2人がそれぞれ: 基本点 × 1 = 1920 → 切り上げて 2000" in text)
        assertTrue(text, "親が: 基本点 × 2 = 3840 → 切り上げて 3900" in text)
        assertTrue(text, "ロン（7700）" in text)
        // 20符はロンが無いので比べない
        assertTrue("ロン" !in Explainer.explain(Hand(Seat.NON_DEALER, WinMethod.TSUMO, 20, 2)))
    }
}
