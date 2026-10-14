package com.winschneid.fuhandojo.domain.repository

import com.winschneid.fuhandojo.domain.RecognitionResult

/** 写真から牌を読み取る */
interface TileRecognizer {
    /**
     * @param jpeg 縮小済みの JPEG 画像
     * @throws RecognitionException 読み取れなかったとき（理由は message に日本語で入る）
     */
    suspend fun recognize(jpeg: ByteArray, apiKey: String): RecognitionResult
}

class RecognitionException(message: String, cause: Throwable? = null) : Exception(message, cause)
