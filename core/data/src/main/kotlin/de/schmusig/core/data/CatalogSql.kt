package de.schmusig.core.data

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import de.schmusig.core.model.CatalogQuery
import de.schmusig.core.model.CatalogSort
import de.schmusig.core.model.QuestionSelection

/**
 * Baut die SQL fuer Katalogliste und Spielauswahl.
 *
 * Die Filter sind dynamisch (Kategorien, Schwierigkeiten, Flags, Sortierung), darum
 * Raw-Queries statt eines Dutzends `@Query`-Varianten. Alle Nutzereingaben gehen als
 * gebundene Argumente hinein, nie als String-Konkatenation; nur die Namen der
 * Sortierspalten stammen aus einem festen Enum.
 */
internal object CatalogSql {

    private const val LIVE = "deletedAt IS NULL"

    fun pagingQuery(query: CatalogQuery): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val where = whereClauses(query, args)
        val sql = buildString {
            append("SELECT * FROM questions WHERE ")
            append(where.joinToString(" AND "))
            append(" ORDER BY ")
            append(orderBy(query.sort))
        }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    fun countQuery(query: CatalogQuery): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val where = whereClauses(query, args)
        val sql = "SELECT COUNT(*) FROM questions WHERE " + where.joinToString(" AND ")
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    /** IDs der Fragen, die zu einer Spielauswahl passen (8.1). */
    fun selectionIdsQuery(selection: QuestionSelection): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val where = mutableListOf(LIVE)

        where += inClause("category", selection.categories.toList(), args)
        where += inClause("difficulty", selection.difficulties.toList(), args)

        selection.excludePlayedSince?.let { since ->
            where += "id NOT IN (SELECT questionId FROM question_stats " +
                "WHERE lastPlayedAt IS NOT NULL AND lastPlayedAt >= ?)"
            args += since.toEpochMilli()
        }

        val sql = "SELECT id FROM questions WHERE " + where.joinToString(" AND ")
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    fun selectionCountQuery(selection: QuestionSelection): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val where = mutableListOf(LIVE)

        where += inClause("category", selection.categories.toList(), args)
        where += inClause("difficulty", selection.difficulties.toList(), args)

        selection.excludePlayedSince?.let { since ->
            where += "id NOT IN (SELECT questionId FROM question_stats " +
                "WHERE lastPlayedAt IS NOT NULL AND lastPlayedAt >= ?)"
            args += since.toEpochMilli()
        }

        val sql = "SELECT COUNT(*) FROM questions WHERE " + where.joinToString(" AND ")
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private fun whereClauses(query: CatalogQuery, args: MutableList<Any>): List<String> {
        val where = mutableListOf(LIVE)

        FtsQuery.of(query.text)?.let { match ->
            where += "id IN (SELECT questionId FROM questions_fts WHERE questions_fts MATCH ?)"
            args += match
        }

        if (query.categories.isNotEmpty()) {
            where += inClause("category", query.categories.toList(), args)
        }
        if (query.difficulties.isNotEmpty()) {
            where += inClause("difficulty", query.difficulties.toList(), args)
        }
        if (query.onlyFlagged) {
            where += "flagged = 1"
        }
        if (query.onlyWithDistractors) {
            // Die Spalte haelt JSON; eine leere Liste steht als "[]" darin.
            where += "distractors IS NOT NULL AND distractors <> '[]' AND distractors <> ''"
        }
        return where
    }

    /** `IN (?, ?, ...)`; eine leere Liste trifft bewusst nichts. */
    private fun inClause(column: String, values: List<Any>, args: MutableList<Any>): String {
        if (values.isEmpty()) return "0"
        args.addAll(values)
        return "$column IN (" + values.joinToString(", ") { "?" } + ")"
    }

    private fun orderBy(sort: CatalogSort): String = when (sort) {
        CatalogSort.RECENTLY_UPDATED -> "updatedAt DESC"
        CatalogSort.CATEGORY -> "category COLLATE NOCASE ASC, question COLLATE NOCASE ASC"
        CatalogSort.DIFFICULTY -> "difficulty ASC, category COLLATE NOCASE ASC"
    }
}

/**
 * Uebersetzt die Eingabe des Suchfelds in einen FTS4-MATCH-Ausdruck.
 *
 * Die Eingabe ist Freitext, FTS4 hat aber eine eigene Syntax (`*`, `"`, `-`, `OR`,
 * `NEAR`). Ein unbedachtes Durchreichen fuehrt zu Syntaxfehlern mitten im Tippen, darum
 * werden Sonderzeichen entfernt und jedes Token mit `*` als Praefix-Suche verknuepft;
 * mehrere Token werden implizit mit UND verbunden.
 */
internal object FtsQuery {

    private val tokenSplitter = Regex("[^\\p{L}\\p{N}]+")

    fun of(text: String): String? {
        val tokens = tokens(text)
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }

    /** Die bereinigten Suchbegriffe - auch fuer das Hervorheben der Treffer (7.2). */
    fun tokens(text: String): List<String> =
        text.trim()
            .split(tokenSplitter)
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
}
