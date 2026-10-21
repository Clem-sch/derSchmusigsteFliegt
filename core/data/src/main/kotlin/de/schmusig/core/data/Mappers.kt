package de.schmusig.core.data

import de.schmusig.core.database.dao.CategoryCountRow
import de.schmusig.core.database.entity.AttemptEntity
import de.schmusig.core.database.entity.GameSessionEntity
import de.schmusig.core.database.entity.QuestionEntity
import de.schmusig.core.database.entity.QuestionStatsEntity
import de.schmusig.core.model.Attempt
import de.schmusig.core.model.CategoryCount
import de.schmusig.core.model.GameSession
import de.schmusig.core.model.Question
import de.schmusig.core.model.QuestionStats
import de.schmusig.core.model.SessionState
import de.schmusig.core.model.Verdict

internal fun QuestionEntity.toDomain() = Question(
    id = id,
    contentHash = contentHash,
    question = question,
    answer = answer,
    category = category,
    difficulty = difficulty,
    tags = tags,
    distractors = distractors,
    flagged = flagged,
    createdAt = createdAt,
    updatedAt = updatedAt,
    revision = revision,
    deletedAt = deletedAt,
)

internal fun Question.toEntity() = QuestionEntity(
    id = id,
    contentHash = contentHash,
    question = question,
    answer = answer,
    category = category,
    difficulty = difficulty,
    tags = tags,
    distractors = distractors,
    flagged = flagged,
    createdAt = createdAt,
    updatedAt = updatedAt,
    revision = revision,
    deletedAt = deletedAt,
)

internal fun QuestionStatsEntity.toDomain() = QuestionStats(
    questionId = questionId,
    timesPlayed = timesPlayed,
    timesCorrect = timesCorrect,
    lastPlayedAt = lastPlayedAt,
)

internal fun QuestionStats.toEntity() = QuestionStatsEntity(
    questionId = questionId,
    timesPlayed = timesPlayed,
    timesCorrect = timesCorrect,
    lastPlayedAt = lastPlayedAt,
)

internal fun CategoryCountRow.toDomain() = CategoryCount(category = category, count = count)

internal fun GameSessionEntity.toDomain() = GameSession(
    id = id,
    modeId = modeId,
    configJson = configJson,
    // Unbekannte Zustaende koennen nur aus einer fremden Datenbank kommen; sie als
    // abgebrochen zu lesen ist harmlos, ein Absturz waere es nicht.
    state = runCatching { SessionState.valueOf(state) }.getOrDefault(SessionState.ABANDONED),
    startedAt = startedAt,
    endedAt = endedAt,
)

internal fun GameSession.toEntity() = GameSessionEntity(
    id = id,
    modeId = modeId,
    configJson = configJson,
    state = state.name,
    startedAt = startedAt,
    endedAt = endedAt,
)

internal fun AttemptEntity.toDomain() = Attempt(
    id = id,
    sessionId = sessionId,
    questionId = questionId,
    position = position,
    verdict = runCatching { Verdict.valueOf(verdict) }.getOrDefault(Verdict.SKIPPED),
    answeredAt = answeredAt,
    durationMs = durationMs,
)

internal fun Attempt.toEntity() = AttemptEntity(
    id = id,
    sessionId = sessionId,
    questionId = questionId,
    position = position,
    verdict = verdict.name,
    answeredAt = answeredAt,
    durationMs = durationMs,
)
