package com.winschneid.fuhandojo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.winschneid.fuhandojo.di.SecretsDataStore
import com.winschneid.fuhandojo.domain.repository.ApiKeyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * APIキーは進捗とは別のファイルに保存し、バックアップ（クラウド・機種変更）の対象から外している
 * （res/xml/backup_rules.xml、data_extraction_rules.xml）。
 */
class ApiKeyRepositoryImpl @Inject constructor(
    @SecretsDataStore private val dataStore: DataStore<Preferences>,
) : ApiKeyRepository {

    override fun apiKey(): Flow<String?> = dataStore.data.map { it[KEY]?.takeIf(String::isNotBlank) }

    override suspend fun setApiKey(key: String) {
        dataStore.edit { prefs ->
            val trimmed = key.trim()
            if (trimmed.isEmpty()) prefs.remove(KEY) else prefs[KEY] = trimmed
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("anthropic_api_key")
    }
}
