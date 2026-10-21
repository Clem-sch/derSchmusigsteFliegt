package de.schmusig.core.data

import de.schmusig.core.database.dao.SessionDao
import de.schmusig.core.database.dao.StatsDao
import de.schmusig.core.model.Attempt
import de.schmusig.core.model.GameSession
import de.schmusig.core.model.SessionState
import de.schmusig.core.model.Verdict
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persistenz fuer Runden und Wertungen.
 *
 * Die Spiellogik liegt in `:game:api`; hier steht nur das Speichern. So kennt kein
 * Spielmodul Room, und die Datenschicht kennt keinen Spielmodus.
 */
interface SessionDataSource {

    fun observeResumable(): Flow<GameSession?>

    suspend fun findResumable(): GameSession?

    suspend fun findSession(id: String): GameSession?

    fun observeSession(id: String): Flow<GameSession?>

    suspend fun saveSession(session: GameSession)

    suspend fun updateSessionState(id: String, state: SessionState, endedAt: Instant?)

    suspend fun deleteSession(id: String)

    suspend fun recordAttempt(attempt: Attempt)

    suspend fun attemptsFor(sessionId: String): List<Attempt>

    fun observeAttemptsFor(sessionId: String): Flow<List<Attempt>>

    suspend fun updateVerdict(attemptId: String, verdict: Verdict)

    suspend fun lastPositionFor(sessionId: String): Int

    /**
     * Statistik der Runde fortschreiben (8.4). Der Aufrufer liefert pro Frage, ob sie als
     * richtig gewertet wurde - uebersprungene Fragen stehen gar nicht in der Map, sie
     * zaehlen nicht als gespielt.
     */
    suspend fun commitRoundStats(playedCorrect: Map<String, Int>, playedAt: Instant)
}

@Singleton
class RoomSessionDataSource @Inject constructor(
    private val sessionDao: SessionDao,
    private val statsDao: StatsDao,
) : SessionDataSource {

    override fun observeResumable(): Flow<GameSession?> =
        sessionDao.observeResumable().map { it?.toDomain() }

    override suspend fun findResumable(): GameSession? =
        sessionDao.findResumable()?.toDomain()

    override suspend fun findSession(id: String): GameSession? =
        sessionDao.findSession(id)?.toDomain()

    override fun observeSession(id: String): Flow<GameSession?> =
        sessionDao.observeSession(id).map { it?.toDomain() }

    override suspend fun saveSession(session: GameSession) =
        sessionDao.upsertSession(session.toEntity())

    override suspend fun updateSessionState(id: String, state: SessionState, endedAt: Instant?) =
        sessionDao.updateSessionState(id, state.name, endedAt)

    override suspend fun deleteSession(id: String) = sessionDao.deleteSession(id)

    override suspend fun recordAttempt(attempt: Attempt) =
        sessionDao.upsertAttempt(attempt.toEntity())

    override suspend fun attemptsFor(sessionId: String): List<Attempt> =
        sessionDao.attemptsFor(sessionId).map { it.toDomain() }

    override fun observeAttemptsFor(sessionId: String): Flow<List<Attempt>> =
        sessionDao.observeAttemptsFor(sessionId).map { attempts -> attempts.map { it.toDomain() } }

    override suspend fun updateVerdict(attemptId: String, verdict: Verdict) =
        sessionDao.updateVerdict(attemptId, verdict.name)

    override suspend fun lastPositionFor(sessionId: String): Int =
        sessionDao.lastPositionFor(sessionId)

    override suspend fun commitRoundStats(playedCorrect: Map<String, Int>, playedAt: Instant) =
        statsDao.applyRoundResult(playedCorrect, playedAt)
}
