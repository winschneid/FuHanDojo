package com.winschneid.fuhandojo.domain

import com.winschneid.fuhandojo.domain.model.MeldKind
import com.winschneid.fuhandojo.domain.model.Question
import com.winschneid.fuhandojo.domain.model.Wait
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TutorialTest {

    private fun questions(step: TutorialStep, seed: Int) = Tutorial.questions(step, Random(seed))

    @Test
    fun `どのステップも4問で、選択肢に正解がちょうど1つある`() {
        repeat(100) { seed ->
            TutorialStep.entries.forEach { step ->
                val qs = questions(step, seed)
                assertEquals(Tutorial.QUESTIONS_PER_STEP, qs.size)
                qs.forEach { q ->
                    assertEquals("$step $q", q.choices.size, q.choices.distinct().size)
                    assertTrue("$step $q", q.answerIndex in q.choices.indices)
                }
            }
        }
    }

    @Test
    fun `順子と刻子のステップは2〜8の数牌だけで、順子・ポン・暗刻がそろう`() {
        repeat(100) { seed ->
            val melds = questions(TutorialStep.SIMPLE_MELDS, seed).map { it as Question.MeldFu }
            melds.forEach { q ->
                assertTrue("$q", q.meld.tiles.none { it.isTerminalOrHonor })
                assertEquals("$q", "${q.meld.fu}符", q.answer)
                assertEquals(listOf("0符", "2符", "4符"), q.choices)
            }
            val kinds = melds.map { it.meld.kind to (it.meld.kind == MeldKind.TRIPLET && it.meld.open) }
            assertTrue(melds.any { it.meld.kind == MeldKind.SEQUENCE })
            assertTrue(melds.any { it.meld.kind == MeldKind.TRIPLET && it.meld.open })
            assertTrue("$kinds", melds.any { it.meld.kind == MeldKind.TRIPLET && !it.meld.open })
        }
    }

    @Test
    fun `ヤオ九牌のステップは1・9・字牌の刻子だけで、明刻と暗刻がそろう`() {
        repeat(100) { seed ->
            val melds = questions(TutorialStep.TERMINAL_MELDS, seed).map { it as Question.MeldFu }
            melds.forEach { q ->
                assertTrue("$q", q.meld.kind == MeldKind.TRIPLET && q.meld.tile.isTerminalOrHonor)
                assertEquals("$q", "${q.meld.fu}符", q.answer)
            }
            assertEquals(setOf(4, 8), melds.map { it.meld.fu }.toSet())
            assertTrue(melds.any { it.meld.tile.isHonor } && melds.any { !it.meld.tile.isHonor })
        }
    }

    @Test
    fun `槓子のステップは明槓・暗槓と中張牌・ヤオ九牌の4通り`() {
        repeat(100) { seed ->
            val melds = questions(TutorialStep.QUADS, seed).map { it as Question.MeldFu }
            melds.forEach { q -> assertEquals("$q", "${q.meld.fu}符", q.answer) }
            assertTrue(melds.all { it.meld.kind == MeldKind.QUAD })
            assertEquals(listOf(8, 16, 16, 32), melds.map { it.meld.fu }.sorted())
        }
    }

    @Test
    fun `雀頭のステップは三元牌・自風・場風・役牌でない牌を1問ずつ`() {
        repeat(100) { seed ->
            val pairs = questions(TutorialStep.PAIRS, seed).map { it as Question.PairFu }
            pairs.forEach { q ->
                // 連風牌（場風かつ自風）は出さない
                assertTrue("$q", !(q.pair.wind != null && q.pair.wind == q.roundWind && q.pair.wind == q.seatWind))
                assertEquals("$q", "${FuCalculator.pairFu(q.pair, q.roundWind, q.seatWind)}符", q.answer)
            }
            assertTrue(pairs.any { it.pair.isDragon })
            assertTrue(pairs.any { it.pair.wind != null && it.pair.wind == it.seatWind })
            assertTrue(pairs.any { it.pair.wind != null && it.pair.wind == it.roundWind })
            assertEquals(listOf("0符", "2符", "2符", "2符"), pairs.map { it.answer }.sorted())
        }
    }

    @Test
    fun `待ちのステップはカンチャン・ペンチャン・単騎がかならず出る`() {
        repeat(100) { seed ->
            val waits = questions(TutorialStep.WAITS, seed).map { it as Question.WaitFu }
            val names = waits.map { it.answer.substringBefore("・") }
            assertTrue("$names", names.containsAll(listOf(Wait.KANCHAN.label, Wait.PENCHAN.label, Wait.TANKI.label)))
        }
    }

    @Test
    fun `例は文字どおりの符になっている`() {
        // 例のキャプションの符を、牌の並びから計算した符と照らし合わせる
        val meldFu = mapOf(
            "順子 0符" to 0, "ポン 2符" to 2, "暗刻 4符" to 4, "中のポン 4符" to 4, "1萬の暗刻 8符" to 8,
            "明槓 8符" to 8, "暗槓 16符" to 16, "東の暗槓 32符" to 32,
        )
        TutorialStep.entries.flatMap(Tutorial::examples).forEach { example ->
            assertTrue(example.caption, example.caption.endsWith("符"))
            meldFu[example.caption]?.let { expected ->
                val tile = example.tiles.first()
                val kind = when {
                    example.tiles.size == 4 -> MeldKind.QUAD
                    example.tiles.distinct().size == 1 -> MeldKind.TRIPLET
                    else -> MeldKind.SEQUENCE
                }
                val open = example.caption.contains("ポン") || example.caption.contains("明槓")
                assertEquals(example.caption, expected, com.winschneid.fuhandojo.domain.model.Meld(kind, tile, open).fu)
            }
        }
    }
}
