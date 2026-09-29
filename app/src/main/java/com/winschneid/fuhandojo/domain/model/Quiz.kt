package com.winschneid.fuhandojo.domain.model

enum class Course(val title: String) {
    POINTS("点数編"),
    FU("符計算編"),
}

/**
 * 道場の級と段。編（course）ごとに、上から順に合格すると次が解放される。
 * 名前は進捗の保存キーに使うので変えない（並び順は変えてよい）。
 */
enum class QuizLevel(val course: Course, val rank: String, val title: String, val description: String) {
    LIMIT_NAMES(Course.POINTS, "10級", "満貫以上の名前", "翻数から満貫・跳満・倍満・三倍満・役満を答える"),
    NON_DEALER_LIMIT_RON(Course.POINTS, "9級", "子の満貫以上", "子がロンしたときの満貫〜役満の点数"),
    DEALER_LIMIT_RON(Course.POINTS, "8級", "親の満貫以上", "親がロンしたときの満貫〜役満の点数"),
    LIMIT_TSUMO(Course.POINTS, "7級", "満貫以上のツモ", "子と親がツモしたときの満貫〜役満の支払い"),
    NON_DEALER_RON_30_40(Course.POINTS, "6級", "子のロン 30符・40符", "よく出る30符・40符の1〜4翻"),
    DEALER_RON_30_40(Course.POINTS, "5級", "親のロン 30符・40符", "親は子の1.5倍が目安"),
    RON_ALL_FU(Course.POINTS, "4級", "ロン 全符", "25符〜110符の子と親のロン"),
    TSUMO_30_40(Course.POINTS, "3級", "ツモ 30符・40符", "子と親のツモの支払い"),
    TSUMO_ALL_FU(Course.POINTS, "2級", "ツモ 全符", "20符〜110符の子と親のツモ"),
    MIXED(Course.POINTS, "1級", "総合", "点数編の全範囲から出題"),
    MELD_FU(Course.FU, "初段", "面子の符", "明刻・暗刻・明槓・暗槓。ヤオ九牌は2倍"),
    PAIR_WAIT_FU(Course.FU, "二段", "雀頭と待ちの符", "役牌の雀頭と、嵌張・辺張・単騎の待ち"),
    FU_SUM(Course.FU, "三段", "符の足し算と例外", "部品の符を足して切り上げる。平和・七対子・鳴いた手の30符"),
    SPLIT_HAND_FU(Course.FU, "四段", "区切った手牌の符", "面子ごとに区切った手牌を見て符を数える"),
    HAND_FU_RON(Course.FU, "五段", "手牌の符（ロン）", "手牌を自分で面子に分けて符を数える"),
    HAND_FU_TSUMO(Course.FU, "六段", "手牌の符（ツモ）", "ツモの手牌。平和ツモ・七対子も"),
    HAND_POINTS(Course.FU, "七段", "手牌から点数", "符を数えて翻と合わせ、点数まで出す（点数編の知識も使う）"),
    ;

    /** 同じ編のひとつ前の級・段。編の最初なら null */
    fun previous(): QuizLevel? = entries.getOrNull(ordinal - 1)?.takeIf { it.course == course }

    /** 同じ編のひとつ後の級・段。編の最後なら null */
    fun next(): QuizLevel? = entries.getOrNull(ordinal + 1)?.takeIf { it.course == course }

    companion object {
        const val QUESTION_COUNT = 10
        const val PASS_SCORE = 8
    }
}

/**
 * 区切った手牌の1グループ（面子・雀頭・七対子の対子）。
 * winning は和了牌で完成したグループ、caption は下に添える説明（鳴いた面子の種類など）。
 */
data class TileGroupInfo(
    val tiles: List<Tile>,
    val winning: Boolean,
    val caption: String?,
    val faceDownEnds: Boolean = false,
)

/** 択一問題 */
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

    /** 面子1つの符を答える */
    data class MeldFu(
        val meld: Meld,
        override val choices: List<String>,
        override val answerIndex: Int,
        override val explanation: String,
    ) : Question

    /** 雀頭の符を答える */
    data class PairFu(
        val pair: Tile,
        val roundWind: Wind,
        val seatWind: Wind,
        override val choices: List<String>,
        override val answerIndex: Int,
        override val explanation: String,
    ) : Question

    /** 待ちの形と符を答える。shape は和了牌を除いた待ちの部分 */
    data class WaitFu(
        val shape: List<Tile>,
        val winningTile: Tile,
        override val choices: List<String>,
        override val answerIndex: Int,
        override val explanation: String,
    ) : Question

    /**
     * 牌を見せずに、和了の条件と面子・雀頭・待ちを文字で示して、合計の符を答える。
     * 部品ごとの符の足し算と切り上げ、平和・七対子などの例外を練習する。
     */
    data class FuSum(
        /** 条件の元になった手（画面には出さない） */
        val hand: WinningHand,
        val conditions: List<String>,
        override val choices: List<String>,
        override val answerIndex: Int,
        override val explanation: String,
    ) : Question

    /**
     * 手牌全体の符を答える。han があるときは、その翻数（役とドラの合計として問題で指定）で点数を答える。
     * 役の判定はアプリで網羅していないので、翻の内訳は見せず条件として与える。
     * groups があるときは、手牌を面子・雀頭ごとに区切って見せる。
     */
    data class HandFu(
        val hand: WinningHand,
        val han: Int?,
        val groups: List<TileGroupInfo>? = null,
        override val choices: List<String>,
        override val answerIndex: Int,
        override val explanation: String,
    ) : Question {
        val asksPoints: Boolean get() = han != null
    }
}
