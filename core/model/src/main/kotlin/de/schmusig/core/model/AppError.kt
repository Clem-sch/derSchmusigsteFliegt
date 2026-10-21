package de.schmusig.core.model

/**
 * Fehler, die ueber Schichtgrenzen gehen duerfen (Abschnitt 12: keine Exceptions ueber
 * Schichtgrenzen). Repository und Sync geben `Result<T, AppError>`-artige Typen zurueck.
 */
sealed interface AppError {
    /** Pflichtfeld leer oder Wert ausserhalb des erlaubten Bereichs. */
    data class Validation(val field: Field, val detail: String? = null) : AppError {
        enum class Field { QUESTION, ANSWER, CATEGORY, DIFFICULTY, DISTRACTORS }
    }

    /** Datensatz existiert nicht (mehr). */
    data class NotFound(val id: String) : AppError

    /** Datei konnte nicht gelesen oder geschrieben werden. */
    data class Io(val detail: String?) : AppError

    /** Datei ist kein gueltiges Austauschformat. */
    data class Format(val detail: String?) : AppError

    /** Dateiformat ist neuer als diese App-Version (6.1). */
    data class UnsupportedSchema(val found: Int, val supported: Int) : AppError

    /** Unerwarteter Fehler in der Persistenz. */
    data class Storage(val detail: String?) : AppError
}

/**
 * Schmaler Result-Typ, damit die Schichtgrenzen ohne Exceptions auskommen und der
 * Fehlerfall im Typ sichtbar bleibt.
 */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>

    val valueOrNull: T? get() = (this as? Success)?.value
    val errorOrNull: AppError? get() = (this as? Failure)?.error
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}
