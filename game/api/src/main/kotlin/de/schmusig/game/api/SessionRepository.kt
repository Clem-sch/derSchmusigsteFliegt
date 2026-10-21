package de.schmusig.game.api

import de.schmusig.core.data.AppClock
import de.schmusig.core.data.QuestionRepository
import de.schmusig.core.data.SessionDataSource
import de.schmusig.core.model.Attempt
import de.schmusig.core.model.GameSession
import de.schmusig.core.model.Question
import de.schmusig.core.model.SessionState
import de.schmusig.core.model.Verdict
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Runden und Wertungen aus Sicht eines Spielmodus.
 *
 * Jeder Swipe schreibt sofort eine Attempt-Zeile: der Session-Cache ist persistent, nicht
 * im RAM (8.2). Rotation, App-Kill oder leerer Akku kosten darum keine Runde.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val sessions: SessionDataSource,
    private val questions: QuestionRepository,
    private val clock: AppClock,
) {

    fun observeResumableSession(): Flow<GameSession?> = sessions.observeResumable()

    suspend fun resumableSession(): GameSession? = sessions.findResumable()

    suspend fun session(id: String): GameSession? = sessions.findSession(id)

    fun observeSession(id: String): Flow<GameSession?> = sessions.observeSession(id)

    /**
     * Legt die Runde an. Das passiert erst bei "Start" und nicht schon beim Vorladen -
     * sonst sammeln sich abgebrochene Setups als halbfertige Sessions an und tauchen in
     * der "Runde fortsetzen?"-Abfrage auf (8.1).
     */
    suspend fun startSession(modeId: String, configJson: String): GameSession {
        val session = GameSession(
            id = UUID.randomUUID().toString(),
            modeId = modeId,
            configJson = configJson,
            state = SessionState.RUNNING,
            startedAt = clock.now(),
            endedAt = null,
        )
        sessions.saveSession(session)
        return session
    }

    suspend fun recordAttempt(
        sessionId: String,
        questionId: String,
        verdict: Verdict,
        durationMs: Long,
    ): Attempt {
        val position = sessions.lastPositionFor(sessionId) + 1
        val attempt = Attempt(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            questionId = questionId,
            position = position,
            verdict = verdict,
            answeredAt = clock.now(),
            durationMs = durationMs,
        )
        sessions.recordAttempt(attempt)
        return attempt
    }

    suspend fun attempts(sessionId: String): List<Attempt> = sessions.attemptsFor(sessionId)

    fun observeAttempts(sessionId: String): Flow<List<Attempt>> =
        sessions.observeAttemptsFor(sessionId)

    suspend fun setVerdict(attemptId: String, verdict: Verdict) =
        sessions.updateVerdict(attemptId, verdict)

    suspend fun markReview(sessionId: String) =
        sessions.updateSessionState(sessionId, SessionState.REVIEW, endedAt = null)

    suspend fun abandon(sessionId: String) =
        sessions.updateSessionState(sessionId, SessionState.ABANDONED, endedAt = clock.now())

    /**
     * Runde abschliessen (8.4). Erst hier wird die Statistik fortgeschrieben, damit
     * Korrekturen in der Review-Liste tatsaechlich wirken.
     */
    suspend fun finishSession(sessionId: String) {
        val attempts = sessions.attemptsFor(sessionId)
        sessions.commitRoundStats(
            playedCorrect = RoundStats.playedCorrect(attempts),
            playedAt = clock.now(),
        )
        sessions.updateSessionState(sessionId, SessionState.FINISHED, endedAt = clock.now())
    }

    suspend fun questionsByIds(ids: List<String>): List<Question> = questions.loadByIds(ids)
}

/**
 * Die Rechenregel fuer den Statistik-Commit, als reine Funktion - so ist sie ohne
 * Datenbank testbar.
 */
object RoundStats {

    /**
     * Pro Frage: wurde sie gespielt, und zaehlt sie als richtig?
     *
     * Uebersprungene Fragen stehen gar nicht in der Map, sie zaehlen nicht als gespielt.
     * Kommt eine Frage mehrfach vor - uebersprungen und spaeter gewertet -, gilt die
     * letzte Wertung, und die Frage zaehlt trotzdem nur einmal als gespielt.
     */
    fun playedCorrect(attempts: List<Attempt>): Map<String, Int> =
        attempts
            .filter { it.verdict != Verdict.SKIPPED }
            .sortedBy { it.position }
            .associate { attempt ->
                attempt.questionId to if (attempt.verdict == Verdict.CORRECT) 1 else 0
            }
}
