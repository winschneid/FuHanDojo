package com.winschneid.fuhandojo.data

import android.util.Base64
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.winschneid.fuhandojo.domain.RecognitionResult
import com.winschneid.fuhandojo.domain.TileRecognition
import com.winschneid.fuhandojo.domain.repository.RecognitionException
import com.winschneid.fuhandojo.domain.repository.TileRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Claude の画像入力で牌を読み取る。返答は構造化出力（JSON スキーマ）で受け取る。
 * 安全分類器による拒否に備えて、サーバー側のフォールバック（fallbacks: "default"）を有効にしている。
 */
class ClaudeTileRecognizer @Inject constructor() : TileRecognizer {

    override suspend fun recognize(jpeg: ByteArray, apiKey: String): RecognitionResult = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        try {
            val image = ImageBlockParam.builder()
                .source(
                    Base64ImageSource.builder()
                        .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                        .data(Base64.encodeToString(jpeg, Base64.NO_WRAP))
                        .build(),
                )
                .build()
            val schema = JsonOutputFormat.Schema.builder().apply {
                TileRecognition.schema().forEach { (key, value) -> putAdditionalProperty(key, JsonValue.from(value)) }
            }.build()
            val params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(16_000L)
                .outputConfig(OutputConfig.builder().format(JsonOutputFormat.builder().schema(schema).build()).build())
                .addUserMessageOfBlockParams(
                    listOf(
                        ContentBlockParam.ofImage(image),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(TileRecognition.PROMPT).build()),
                    ),
                )
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build()

            val message = client.messages().create(params)
            when (message.stopReason().orElse(null)) {
                StopReason.REFUSAL -> throw RecognitionException("この画像は読み取れませんでした。別の写真で試してください")
                StopReason.MAX_TOKENS -> throw RecognitionException("読み取り結果が長すぎて途中で切れました。もう一度試してください")
                else -> Unit
            }
            val text = message.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")
            if (text.isBlank()) throw RecognitionException("読み取り結果が空でした。もう一度試してください")
            try {
                TileRecognition.parse(text)
            } catch (e: IllegalArgumentException) {
                throw RecognitionException("読み取り結果を解釈できませんでした。もう一度試してください", e)
            }
        } catch (e: UnauthorizedException) {
            throw RecognitionException("APIキーが正しくありません。設定を確かめてください", e)
        } catch (e: PermissionDeniedException) {
            throw RecognitionException("このAPIキーでは利用できません", e)
        } catch (e: RateLimitException) {
            throw RecognitionException("混み合っています。少し待ってから試してください", e)
        } catch (e: AnthropicServiceException) {
            throw RecognitionException("読み取りに失敗しました（${e.statusCode()}）", e)
        } catch (e: AnthropicIoException) {
            throw RecognitionException("通信できませんでした。ネットワークを確かめてください", e)
        } finally {
            client.close()
        }
    }

    private companion object {
        const val MODEL = "claude-opus-5-5"
    }
}
