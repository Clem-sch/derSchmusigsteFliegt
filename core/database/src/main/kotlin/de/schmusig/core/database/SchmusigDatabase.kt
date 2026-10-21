package de.schmusig.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.schmusig.core.database.dao.QuestionDao
import de.schmusig.core.database.dao.SessionDao
import de.schmusig.core.database.dao.StatsDao
import de.schmusig.core.database.entity.AttemptEntity
import de.schmusig.core.database.entity.GameSessionEntity
import de.schmusig.core.database.entity.QuestionEntity
import de.schmusig.core.database.entity.QuestionFtsEntity
import de.schmusig.core.database.entity.QuestionStatsEntity

@Database(
    entities = [
        QuestionEntity::class,
        QuestionFtsEntity::class,
        QuestionStatsEntity::class,
        GameSessionEntity::class,
        AttemptEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(SchmusigConverters::class)
abstract class SchmusigDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun statsDao(): StatsDao
    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "schmusig.db"
    }
}
