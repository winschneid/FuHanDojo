package com.winschneid.fuhandojo.domain.repository

import kotlinx.coroutines.flow.Flow

/** 写真の読み取りに使う Claude API のキー。端末の中だけに保存する */
interface ApiKeyRepository {
    fun apiKey(): Flow<String?>

    /** 空文字なら削除する */
    suspend fun setApiKey(key: String)
}
