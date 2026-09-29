package com.winschneid.fuhandojo.domain.model

/** 道場の級。上から順に合格すると次の級が解放される */
enum class QuizLevel(val rank: String, val title: String, val description: String) {
    LIMIT_NAMES("10級", "満貫以上の名前", "翻数から満貫・跳満・倍満・三倍満・役満を答える"),
    NON_DEALER_LIMIT_RON("9級", "子の満貫以上", "子がロンしたときの満貫〜役満の点数"),
    DEALER_LIMIT_RON("8級", "親の満貫以上", "親がロンしたときの満貫〜役満の点数"),
    LIMIT_TSUMO("7級", "満貫以上のツモ", "子と親がツモしたときの満貫〜役満の支払い"),
    NON_DEALER_RON_30_40("6級", "子のロン 30符・40符", "よく出る30符・40符の1〜4翻"),
    DEALER_RON_30_40("5級", "親のロン 30符・40符", "親は子の1.5倍が目安"),
    RON_ALL_FU("4級", "ロン 全符", "25符〜110符の子と親のロン"),
    TSUMO_30_40("3級", "ツモ 30符・40符", "子と親のツモの支払い"),
    TSUMO_ALL_FU("2級", "ツモ 全符", "20符〜110符の子と親のツモ"),
    MIXED("1級", "総合", "これまでの全範囲から出題"),
    ;

    companion object {
        const val QUESTION_COUNT = 10
        const val PASS_SCORE = 8
    }
}

/** 4択の問題 */
sealed interface Question {
    val choices: List<String>
    val answerIndex: Int
    val explanation: String

    val answer: String get() = choices[answerIndex]

    /** 翻数から満貫・跳満などの名前を答える */
    data class LimitName(
        val han: Int,
        override val choices: List<String>,
        override val answerIndex: Int,
        override val explanation: String,
    ) : Question

    /** 和了の条件から点数を答える。showFu が false のときは満貫以上で符を問わない */
    data class Points(
        val hand: Hand,
        val showFu: Boolean,
        override val choices: List<String>,
        override val answerIndex: Int,
        override val explanation: String,
    ) : Question
}
