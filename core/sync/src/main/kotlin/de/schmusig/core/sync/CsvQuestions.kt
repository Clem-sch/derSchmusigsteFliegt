package de.schmusig.core.sync

import de.schmusig.core.model.AppError
import de.schmusig.core.model.Outcome

/**
 * Komfort-Import aus einer Tabelle (Abschnitt 6.7).
 *
 * Ausdruecklich **kein** Abgleichsformat: keine IDs, keine Zeitstempel, keine Tombstones.
 * Darum erzeugt der Parser nur [QuestionDto]-Zeilen und schiebt sie durch denselben
 * MERGE-Pfad wie alles andere - jede Zeile ist fuer die Engine damit eine neue Frage,
 * deren Dubletten ueber den contentHash auffallen.
 */
object CsvQuestions {

    private val expectedHeader = listOf("question", "answer", "category", "difficulty", "tags")

    fun parse(raw: String): Outcome<List<QuestionDto>> {
        val rows = splitRows(raw).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return Outcome.Failure(AppError.Format("leere Datei"))

        val header = rows.first().map { it.trim().lowercase() }
        val hasHeader = header.take(2) == expectedHeader.take(2)
        if (!hasHeader) {
            return Outcome.Failure(
                AppError.Format("Kopfzeile erwartet: ${expectedHeader.joinToString(",")}"),
            )
        }

        val columnIndex = expectedHeader.associateWith { header.indexOf(it) }
        val questionColumn = columnIndex.getValue("question")
        val answerColumn = columnIndex.getValue("answer")

        val parsed = rows.drop(1).mapNotNull { row ->
            val question = row.getOrNull(questionColumn)?.trim().orEmpty()
            val answer = row.getOrNull(answerColumn)?.trim().orEmpty()
            if (question.isEmpty() && answer.isEmpty()) return@mapNotNull null

            val tagsRaw = columnIndex.getValue("tags")
                .takeIf { it >= 0 }
                ?.let { row.getOrNull(it) }
                .orEmpty()

            QuestionDto(
                question = question,
                answer = answer,
                category = columnIndex.getValue("category")
                    .takeIf { it >= 0 }
                    ?.let { row.getOrNull(it)?.trim() }
                    ?.takeIf { it.isNotEmpty() },
                difficulty = columnIndex.getValue("difficulty")
                    .takeIf { it >= 0 }
                    ?.let { row.getOrNull(it)?.trim() }
                    ?.takeIf { it.isNotEmpty() }
                    ?.toIntOrNull(),
                tags = tagsRaw.split(';', '|')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
            )
        }

        if (parsed.isEmpty()) return Outcome.Failure(AppError.Format("keine Datenzeilen"))
        return Outcome.Success(parsed)
    }

    /**
     * Minimaler CSV-Leser mit Anfuehrungszeichen und doppelten Anfuehrungszeichen als
     * Escape. Trennzeichen ist Komma oder Semikolon - Tabellenprogramme in deutscher
     * Konfiguration schreiben Semikolon.
     */
    private fun splitRows(raw: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        val delimiter = detectDelimiter(raw)

        fun endField() {
            fields.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            rows.add(fields)
            fields = mutableListOf()
        }

        while (index < raw.length) {
            val char = raw[index]
            when {
                inQuotes && char == '"' && raw.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }

                char == '"' -> inQuotes = !inQuotes
                !inQuotes && char == delimiter -> endField()
                !inQuotes && char == '\n' -> endRow()
                !inQuotes && char == '\r' -> Unit
                else -> field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) endRow()
        return rows
    }

    private fun detectDelimiter(raw: String): Char {
        val firstLine = raw.lineSequence().firstOrNull().orEmpty()
        return if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
    }
}
