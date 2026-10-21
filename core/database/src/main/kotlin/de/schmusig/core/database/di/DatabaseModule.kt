package de.schmusig.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.schmusig.core.database.SchmusigDatabase
import de.schmusig.core.database.dao.QuestionDao
import de.schmusig.core.database.dao.SessionDao
import de.schmusig.core.database.dao.StatsDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SchmusigDatabase =
        Room.databaseBuilder(context, SchmusigDatabase::class.java, SchmusigDatabase.NAME)
            // Foreign Keys werden gebraucht: Attempts haengen per ON DELETE CASCADE an
            // ihrer Session.
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides
    fun provideQuestionDao(database: SchmusigDatabase): QuestionDao = database.questionDao()

    @Provides
    fun provideStatsDao(database: SchmusigDatabase): StatsDao = database.statsDao()

    @Provides
    fun provideSessionDao(database: SchmusigDatabase): SessionDao = database.sessionDao()
}
