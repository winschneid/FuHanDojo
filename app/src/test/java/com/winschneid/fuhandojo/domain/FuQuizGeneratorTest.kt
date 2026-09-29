package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.Course
import com.winschneid.fuhandojo.domain.model.Hand
import com.winschneid.fuhandojo.domain.model.Meld
import com.winschneid.fuhandojo.domain.model.MeldKind
import com.winschneid.fuhandojo.domain.model.Question
import com.winschneid.fuhandojo.domain.model.QuizLevel
import com.winschneid.fuhandojo.domain.model.Wait
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FuQuizGeneratorTest {

    private val fuLevels = QuizLevel.entries.filter { it.course == Course.FU }

    @Test
    fun `符計算編の問題は正解が計算と一致し、選択肢が重複しない`() {
        repeat(30) { seed ->
            fuLevels.forEach { level ->
                QuizGenerator.generate(level, Random(seed)).forEach { q ->
                    assertTrue("$level $q", q.choices.distinct().size == q.choices.size && q.choices.size >= 3)
                    when (q) {
                        is Question.MeldFu -> assertEquals("${q.meld.fu}符", q.answer)
                        is Question.PairFu ->
                            assertEquals("${FuCalculator.pairFu(q.pair, q.roundWind, q.seatWind)}符", q.answer)
                        is Question.WaitFu -> {
                            val tiles = q.shape + q.winningTile
                            val wait = when (q.shape.size) {
                                1 -> Wait.TANKI
                                4 -> Wait.SHANPON
                                else -> FuCalculator.waitOf(Meld(MeldKind.SEQUENCE, tiles.min(), false), q.winningTile)
                            }
                            assertTrue("$q", q.answer.startsWith(wait.label) && q.answer.endsWith("${wait.fu}符"))
                        }
                        is Question.HandFu -> {
                            assertTrue("$q", FuQuizGenerator.isUsable(q.hand))
                            val result = FuCalculator.calculate(q.hand)!!
                            if (q.asksPoints) {
                                val hand = Hand(q.hand.seat, q.hand.method, result.fu, q.han!!)
                                assertTrue("$q", ScoreCalculator.isValid(hand))
                                assertEquals(ScoreCalculator.payment(hand).label, q.answer)
                            } else {
                                assertEquals("${result.fu}符", q.answer)
                            }
                        }
                        else -> error("符計算編で想定外の問題: $q")
                    }
                }
            }
        }
    }

    @Test
    fun `生成した手牌は14枚の和了形で、どの牌も4枚以内、鳴いた手には役がある`() {
        repeat(300) { seed ->
            val hand = FuQuizGenerator.randomHand(Random(seed), method = null, allowChiitoitsu = true)
            val quadCount = hand.calledMelds.count { it.kind == MeldKind.QUAD }
            assertEquals("$hand", 14 + quadCount, hand.allTiles.size)
            assertTrue("$hand", hand.allTiles.groupingBy { it }.eachCount().values.all { it <= 4 })
            val result = FuCalculator.calculate(hand)!!
            if (!hand.isClosed) assertTrue("$hand", FuQuizGenerator.guaranteedHan(hand, result) > 0)
        }
    }

    @Test
    fun `鳴いた手・平和・七対子が偏らずに出る`() {
        val hands = (0 until 500).map { FuQuizGenerator.randomHand(Random(it), method = null, allowChiitoitsu = true) }
        val results = hands.map { FuCalculator.calculate(it)!! }
        val open = hands.count { !it.isClosed }
        val pinfu = results.count { it.isPinfu }
        val chiitoitsu = results.count { it.isChiitoitsu }
        assertTrue("鳴いた手 $open/500", open in 110..190)
        assertTrue("平和 $pinfu/500", pinfu in 60..140)
        assertTrue("七対子 $chiitoitsu/500", chiitoitsu in 25..75)
    }

    @Test
    fun `手牌の問題にはいろいろな符が出る`() {
        val fus = (0 until 200).map {
            FuCalculator.calculate(FuQuizGenerator.randomHand(Random(it), method = null, allowChiitoitsu = true))!!.fu
        }.toSet()
        assertTrue("$fus", fus.containsAll(listOf(20, 25, 30, 40, 50)))
    }
}
