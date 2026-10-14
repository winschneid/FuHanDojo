package com.winschneid.fuhandojo.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.winschneid.fuhandojo.data.ApiKeyRepositoryImpl
import com.winschneid.fuhandojo.data.ClaudeTileRecognizer
import com.winschneid.fuhandojo.data.ProgressRepositoryImpl
import com.winschneid.fuhandojo.data.ReviewRepositoryImpl
import com.winschneid.fuhandojo.domain.repository.ApiKeyRepository
import com.winschneid.fuhandojo.domain.repository.ProgressRepository
import com.winschneid.fuhandojo.domain.repository.ReviewRepository
import com.winschneid.fuhandojo.domain.repository.TileRecognizer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** APIキーなど、バックアップに含めない値を保存する DataStore */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SecretsDataStore

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds
    @Singleton
    abstract fun bindReviewRepository(impl: ReviewRepositoryImpl): ReviewRepository

    @Binds
    @Singleton
    abstract fun bindApiKeyRepository(impl: ApiKeyRepositoryImpl): ApiKeyRepository

    @Binds
    abstract fun bindTileRecognizer(impl: ClaudeTileRecognizer): TileRecognizer

    companion object {
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                // ファイルが壊れていても起動できるよう、進捗を空にして読み直す
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            ) { context.preferencesDataStoreFile("progress") }

        @Provides
        @Singleton
        @SecretsDataStore
        fun provideSecretsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            ) { context.preferencesDataStoreFile("secrets") }
    }
}
