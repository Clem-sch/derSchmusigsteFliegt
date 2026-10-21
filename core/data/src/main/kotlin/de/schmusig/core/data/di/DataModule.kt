package de.schmusig.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.schmusig.core.data.AppClock
import de.schmusig.core.data.OfflineQuestionRepository
import de.schmusig.core.data.OfflineSyncRepository
import de.schmusig.core.data.QuestionRepository
import de.schmusig.core.data.RoomSessionDataSource
import de.schmusig.core.data.SessionDataSource
import de.schmusig.core.data.SyncRepository
import de.schmusig.core.data.SystemAppClock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    @Singleton
    fun bindQuestionRepository(impl: OfflineQuestionRepository): QuestionRepository

    @Binds
    @Singleton
    fun bindSyncRepository(impl: OfflineSyncRepository): SyncRepository

    @Binds
    @Singleton
    fun bindSessionDataSource(impl: RoomSessionDataSource): SessionDataSource

    @Binds
    @Singleton
    fun bindClock(impl: SystemAppClock): AppClock
}
