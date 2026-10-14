package com.winschneid.fuhandojo.ui.screens.calculator

import androidx.lifecycle.SavedStateHandle
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.winschneid.fuhandojo.data.ImagePreparer
import com.winschneid.fuhandojo.domain.Riichi
import com.winschneid.fuhandojo.domain.ScoreOutcome
import com.winschneid.fuhandojo.domain.Scoring
import com.winschneid.fuhandojo.domain.WinContext
import com.winschneid.fuhandojo.domain.model.Meld
import com.winschneid.fuhandojo.domain.model.MeldKind
import com.winschneid.fuhandojo.domain.model.Tile
import com.winschneid.fuhandojo.domain.model.WinMethod
import com.winschneid.fuhandojo.domain.model.WinningHand
import com.winschneid.fuhandojo.domain.model.Wind
import com.winschneid.fuhandojo.domain.repository.ApiKeyRepository
import com.winschneid.fuhandojo.domain.repository.RecognitionException
import com.winschneid.fuhandojo.domain.repository.TileRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject

/**
 * 点数計算の入力。tiles は手の中の牌で、最後の1枚がアガリ牌。
 */
@Serializable
data class CalcInput(
    val tiles: List<Tile> = emptyList(),
    val melds: List<Meld> = emptyList(),
    val method: WinMethod = WinMethod.RON,
    val roundWind: Wind = Wind.EAST,
    val seatWind: Wind = Wind.SOUTH,
    val context: WinContext = WinContext(),
) {
    /** 手の中に必要な枚数（アガリ牌を含む） */
    val needed: Int get() = 14 - melds.size * 3

    val isClosed: Boolean get() = melds.none { it.open }
    val hasQuad: Boolean get() = melds.any { it.kind == MeldKind.QUAD }

    /** 手牌（鳴いた面子を含む）にある数牌の5の枚数。赤ドラの上限になる */
    val fiveCount: Int get() = (tiles + melds.flatMap { it.tiles }).count { !it.isHonor && it.number == 5 }

    val hand: WinningHand?
        get() = if (tiles.isEmpty()) null else WinningHand(tiles.dropLast(1).sorted(), melds, tiles.last(), method, roundWind, seatWind)

    fun countOf(tile: Tile): Int = tiles.count { it == tile } + melds.sumOf { meld -> meld.tiles.count { it == tile } }

    /** 条件どうしの矛盾をなくす（鳴いたらリーチを外す、ロンなら嶺上開花を外す、など） */
    fun normalized(): CalcInput {
        var c = context
        if (!isClosed) c = c.copy(riichi = Riichi.NONE)
        if (c.riichi == Riichi.NONE) c = c.copy(ippatsu = false, uraDora = 0)
        c = if (method == WinMethod.RON) c.copy(rinshan = false) else c.copy(chankan = false)
        if (!hasQuad) c = c.copy(rinshan = false)
        // 赤ドラは手牌にある5の枚数まで
        c = c.copy(akaDora = c.akaDora.coerceAtMost(fiveCount))
        return copy(context = c)
    }
}

/** キーボードで牌を押したときに何を入れるか */
enum class EntryMode(val label: String, val hint: String) {
    HAND("手牌", "押した牌が手牌に入ります。最後に入れた牌がアガリ牌です。"),
    CHI("チー", "鳴いた順子のいちばん小さい牌を押してください（例: 345なら3）。"),
    PON("ポン", "ポンした牌を押してください。"),
    OPEN_KAN("明槓", "明槓（大明槓・加槓）した牌を押してください。"),
    CLOSED_KAN("暗槓", "暗槓した牌を押してください。暗槓は門前のままです。"),
}

/** 写真からの読み取りの状態 */
sealed interface RecognitionStatus {
    data object Idle : RecognitionStatus
    data object Loading : RecognitionStatus

    /** 読み取って入力に反映した。warnings は確かめてほしい点 */
    data class Done(val warnings: List<String>) : RecognitionStatus
    data class Failed(val message: String) : RecognitionStatus
}

data class CalculatorUiState(
    val input: CalcInput = CalcInput(),
    val mode: EntryMode = EntryMode.HAND,
    val outcome: ScoreOutcome = ScoreOutcome.Incomplete(14, 0),
    val recognition: RecognitionStatus = RecognitionStatus.Idle,
    val hasApiKey: Boolean = false,
) {
    /** 今のモードでその牌を入れられるか */
    fun canEnter(tile: Tile): Boolean {
        val input = input
        return when (mode) {
            EntryMode.HAND -> input.tiles.size < input.needed && input.countOf(tile) < 4
            EntryMode.CHI -> {
                if (tile.isHonor || tile.number > 7 || !canAddMeld()) return false
                Meld(MeldKind.SEQUENCE, tile, open = true).tiles.all { input.countOf(it) < 4 }
            }
            EntryMode.PON -> canAddMeld() && input.countOf(tile) <= 1
            EntryMode.OPEN_KAN, EntryMode.CLOSED_KAN -> canAddMeld() && input.countOf(tile) == 0
        }
    }

    /** 面子を増やしても、すでに入れた手牌が多すぎないか */
    fun canAddMeld(): Boolean = input.melds.size < 4 && input.tiles.size <= input.needed - 3
}

sealed interface CalculatorAction {
    data class TapTile(val tile: Tile) : CalculatorAction
    data class RemoveConcealed(val tile: Tile) : CalculatorAction
    data object RemoveWinning : CalculatorAction
    data class RemoveMeld(val index: Int) : CalculatorAction
    data class SetMode(val mode: EntryMode) : CalculatorAction
    data object Backspace : CalculatorAction
    data object Clear : CalculatorAction
    data class SetMethod(val method: WinMethod) : CalculatorAction
    data class SetRoundWind(val wind: Wind) : CalculatorAction
    data class SetSeatWind(val wind: Wind) : CalculatorAction
    data class UpdateContext(val transform: (WinContext) -> WinContext) : CalculatorAction
    data class SetTiles(val tiles: List<Tile>, val melds: List<Meld>) : CalculatorAction
    data class RecognizePhoto(val uri: Uri) : CalculatorAction
    data class SaveApiKey(val key: String) : CalculatorAction
    data object DismissRecognition : CalculatorAction
}

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val recognizer: TileRecognizer,
    private val apiKeyRepository: ApiKeyRepository,
    private val imagePreparer: ImagePreparer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(stateOf(restore(), EntryMode.HAND))
    val uiState = _uiState.asStateFlow()

    private var apiKey: String? = null

    init {
        viewModelScope.launch {
            apiKeyRepository.apiKey().collect { key ->
                apiKey = key
                _uiState.update { it.copy(hasApiKey = key != null) }
            }
        }
    }

    fun onAction(action: CalculatorAction) {
        val state = _uiState.value
        val input = state.input
        when (action) {
            is CalculatorAction.TapTile -> {
                if (!state.canEnter(action.tile)) return
                val tile = action.tile
                val newInput = when (state.mode) {
                    EntryMode.HAND -> input.copy(tiles = input.tiles + tile)
                    EntryMode.CHI -> input.copy(melds = input.melds + Meld(MeldKind.SEQUENCE, tile, open = true))
                    EntryMode.PON -> input.copy(melds = input.melds + Meld(MeldKind.TRIPLET, tile, open = true))
                    EntryMode.OPEN_KAN -> input.copy(melds = input.melds + Meld(MeldKind.QUAD, tile, open = true))
                    EntryMode.CLOSED_KAN -> input.copy(melds = input.melds + Meld(MeldKind.QUAD, tile, open = false))
                }
                // 面子を1つ入れたら手牌の入力に戻る
                update(newInput, EntryMode.HAND)
            }
            is CalculatorAction.RemoveConcealed -> {
                val concealed = input.tiles.dropLast(1).toMutableList()
                if (!concealed.remove(action.tile)) return
                update(input.copy(tiles = concealed + input.tiles.last()), state.mode)
            }
            CalculatorAction.RemoveWinning, CalculatorAction.Backspace ->
                if (input.tiles.isNotEmpty()) update(input.copy(tiles = input.tiles.dropLast(1)), state.mode)
            is CalculatorAction.RemoveMeld ->
                update(input.copy(melds = input.melds.filterIndexed { i, _ -> i != action.index }), state.mode)
            is CalculatorAction.SetMode -> update(input, action.mode)
            CalculatorAction.Clear -> update(input.copy(tiles = emptyList(), melds = emptyList()), EntryMode.HAND)
            is CalculatorAction.SetMethod -> update(input.copy(method = action.method), state.mode)
            is CalculatorAction.SetRoundWind -> update(input.copy(roundWind = action.wind), state.mode)
            is CalculatorAction.SetSeatWind -> update(input.copy(seatWind = action.wind), state.mode)
            is CalculatorAction.UpdateContext -> update(input.copy(context = action.transform(input.context)), state.mode)
            is CalculatorAction.SetTiles -> update(input.copy(tiles = action.tiles, melds = action.melds), EntryMode.HAND)
            is CalculatorAction.RecognizePhoto -> recognize(action.uri)
            is CalculatorAction.SaveApiKey -> viewModelScope.launch { apiKeyRepository.setApiKey(action.key) }
            CalculatorAction.DismissRecognition -> _uiState.update { it.copy(recognition = RecognitionStatus.Idle) }
        }
    }

    /** 写真を縮めて読み取りに送り、結果を手牌の入力に反映する（違う牌はそのまま手で直せる） */
    private fun recognize(uri: Uri) {
        val key = apiKey
        if (key == null) {
            _uiState.update { it.copy(recognition = RecognitionStatus.Failed("先にAPIキーを設定してください")) }
            return
        }
        if (_uiState.value.recognition == RecognitionStatus.Loading) return
        _uiState.update { it.copy(recognition = RecognitionStatus.Loading) }
        viewModelScope.launch {
            val status = try {
                val result = recognizer.recognize(imagePreparer.prepare(uri), key)
                val input = _uiState.value.input
                update(
                    input.copy(tiles = result.tiles, melds = result.melds, context = input.context.copy(akaDora = result.akaDora)),
                    EntryMode.HAND,
                )
                RecognitionStatus.Done(result.warnings)
            } catch (e: RecognitionException) {
                RecognitionStatus.Failed(e.message ?: "読み取りに失敗しました")
            } catch (e: IOException) {
                RecognitionStatus.Failed("画像を読み込めませんでした")
            }
            _uiState.update { it.copy(recognition = status) }
        }
    }

    private fun update(input: CalcInput, mode: EntryMode) {
        val normalized = input.normalized()
        val current = _uiState.value
        _uiState.value = stateOf(normalized, mode).copy(recognition = current.recognition, hasApiKey = current.hasApiKey)
        savedStateHandle[KEY_INPUT] = json.encodeToString(normalized)
    }

    private fun restore(): CalcInput {
        val text = savedStateHandle.get<String>(KEY_INPUT) ?: return CalcInput()
        return try {
            json.decodeFromString<CalcInput>(text)
        } catch (e: SerializationException) {
            CalcInput()
        } catch (e: IllegalArgumentException) {
            CalcInput()
        }
    }

    private fun stateOf(input: CalcInput, mode: EntryMode): CalculatorUiState {
        val hand = input.hand
        val outcome = if (hand == null) {
            ScoreOutcome.Incomplete(input.needed, 0)
        } else {
            Scoring.score(hand, input.context)
        }
        return CalculatorUiState(input = input, mode = mode, outcome = outcome)
    }

    private companion object {
        const val KEY_INPUT = "calc_input"
        val json = Json { ignoreUnknownKeys = true }
    }
}
