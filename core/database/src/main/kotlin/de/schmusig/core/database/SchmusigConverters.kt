package de.schmusig.core.database

import androidx.room.TypeConverter
import java.time.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Zeitstempel liegen als Millisekunden seit Epoch in der Datenbank (intern immer UTC,
 * Abschnitt 12). Listen als JSON-Spalte via TypeConverter (Abschnitt 5.1).
 */
class SchmusigConverters {

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun stringListToJson(value: List<String>): String =
        json.encodeToString(stringListSerializer, value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        if (value.isBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString(stringListSerializer, value) }
                .getOrDefault(emptyList())
        }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val stringListSerializer = ListSerializer(String.serializer())
    }
}
