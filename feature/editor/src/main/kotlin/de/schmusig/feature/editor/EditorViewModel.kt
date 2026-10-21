package de.schmusig.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.schmusig.core.data.DeletedQuestionBus
import de.schmusig.core.data.QuestionRepository
import de.schmusig.core.model.AppError
import de.schmusig.core.model.Outcome
import de.schmusig.core.model.Question
import de.schmusig.core.model.QuestionDraft
import de.schmusig.core.sync.DistractorRowError
import de.schmusig.core.sync.DistractorRules
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class DuplicateWarning(val existingQuestion: String)

data class EditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val question: String = "",
    val answer: String = "",
    val category: String = "",
    val difficulty: Int = DEFAULT_DIFFICULTY,
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val distractors: List<String> = emptyList(),
    val distractorErrors: List<DistractorRowError?> = emptyList(),
    val distractorsExpanded: Boolean = false,
    val flagged: Boolean = false,
    val categorySuggestions: List<String> = emptyList(),
    val duplicateWarning: DuplicateWarning? = null,
    val invalidField: AppError.Validation.Field? = null,
    val saving: Boolean = false,
    val questionMissing: Boolean = false,
) {
    val canAddDistractor: Boolean get() = distractors.size < Question.MAX_DISTRACTORS

    /** Die Kopfzeile des eingeklappten Abschnitts zeigt die Anzahl (7.3). */
    val distractorCount: Int get() = distractors.count { it.isNotBlank() }

    companion object {
        const val DEFAULT_DIFFICULTY = 2
    }
}

sealed interface EditorEvent {
    data object Saved : EditorEvent
    data object Deleted : EditorEvent
    data object SaveFailed : EditorEvent
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: QuestionRepository,
    private val deletedQuestionBus: DeletedQuestionBus,
) : ViewModel() {

    private val questionId: String? = savedStateHandle
        .get<String>(ARG_QUESTION_ID)
        ?.takeIf { it.isNotBlank() && it != NEW_QUESTION }

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = eventChannel.receiveAsFlow()

    /** Der Stand vor dem Bearbeiten - fuer die Dubletten-Pruefung und das Loeschen. */
    private var loaded: Question? = null

    init {
        viewModelScope.launch {
            val suggestions = repository.observeCategories().first()
            val existing = questionId?.let { repository.findById(it) }
            loaded = existing

            if (questionId != null && existing == null) {
                _uiState.value = EditorUiState(loading = false, questionMissing = true)
                return@launch
            }

            _uiState.value = EditorUiState(
                loading = false,
                isNew = existing == null,
                question = existing?.question.orEmpty(),
                answer = existing?.answer.orEmpty(),
                category = existing?.category.orEmpty(),
                difficulty = existing?.difficulty ?: EditorUiState.DEFAULT_DIFFICULTY,
                tags = existing?.tags.orEmpty(),
                distractors = existing?.distractors.orEmpty(),
                distractorErrors = List(existing?.distractors?.size ?: 0) { null },
                // Standardmaessig zu - der Abschnitt darf das Anlegen einer normalen
                // Frage nicht verlangsamen (7.3).
                distractorsExpanded = false,
                flagged = existing?.flagged ?: false,
                categorySuggestions = suggestions,
            )
        }
    }

    fun onQuestionChange(value: String) = update {
        it.copy(question = value, invalidField = null)
    }

    fun onAnswerChange(value: String) = update { state ->
        // Die Antwort entscheidet, welche Falschantwort ungueltig ist - also neu pruefen.
        state.copy(
            answer = value,
            invalidField = null,
            distractorErrors = DistractorRules.validateRows(state.distractors, value),
        )
    }

    fun onCategoryChange(value: String) = update {
        it.copy(category = value, invalidField = null)
    }

    fun onDifficultyChange(value: Int) = update { it.copy(difficulty = value) }

    fun onFlaggedChange(value: Boolean) = update { it.copy(flagged = value) }

    fun onTagInputChange(value: String) = update { it.copy(tagInput = value) }

    fun onTagCommit() = update { state ->
        val tag = state.tagInput.trim().lowercase()
        if (tag.isEmpty() || tag in state.tags) {
            state.copy(tagInput = "")
        } else {
            state.copy(tags = state.tags + tag, tagInput = "")
        }
    }

    fun onTagRemove(tag: String) = update { it.copy(tags = it.tags - tag) }

    fun onDistractorsExpandedChange(expanded: Boolean) = update {
        it.copy(distractorsExpanded = expanded)
    }

    fun onDistractorAdd() = update { state ->
        if (!state.canAddDistractor) {
            state
        } else {
            val rows = state.distractors + ""
            state.copy(
                distractors = rows,
                distractorErrors = DistractorRules.validateRows(rows, state.answer),
            )
        }
    }

    fun onDistractorChange(index: Int, value: String) = update { state ->
        val rows = state.distractors.toMutableList()
        if (index !in rows.indices) return@update state
        rows[index] = value
        state.copy(
            distractors = rows,
            distractorErrors = DistractorRules.validateRows(rows, state.answer),
        )
    }

    fun onDistractorRemove(index: Int) = update { state ->
        val rows = state.distractors.toMutableList()
        if (index !in rows.indices) return@update state
        rows.removeAt(index)
        state.copy(
            distractors = rows,
            distractorErrors = DistractorRules.validateRows(rows, state.answer),
        )
    }

    fun onDuplicateWarningDismiss() = update { it.copy(duplicateWarning = null) }

    fun onSave() {
        val state = _uiState.value
        if (state.saving) return

        viewModelScope.launch {
            _uiState.value = state.copy(saving = true)

            // Dubletten-Warnung nur, wenn der Inhalt neu oder geaendert ist und ein
            // *anderer* Datensatz denselben Hash hat.
            val duplicate = repository.findDuplicate(state.question, state.answer)
            if (duplicate != null && duplicate.id != loaded?.id) {
                _uiState.value = state.copy(
                    saving = false,
                    duplicateWarning = DuplicateWarning(duplicate.question),
                )
                return@launch
            }
            persist(state)
        }
    }

    /** "Trotzdem speichern" aus dem Dubletten-Dialog (7.3). */
    fun onSaveAnyway() {
        val state = _uiState.value.copy(duplicateWarning = null)
        viewModelScope.launch {
            _uiState.value = state.copy(saving = true)
            persist(state)
        }
    }

    fun onDelete() {
        val previous = loaded ?: return
        viewModelScope.launch {
            when (repository.softDelete(previous.id)) {
                is Outcome.Success -> {
                    deletedQuestionBus.publish(previous)
                    eventChannel.send(EditorEvent.Deleted)
                }

                is Outcome.Failure -> eventChannel.send(EditorEvent.SaveFailed)
            }
        }
    }

    private suspend fun persist(state: EditorUiState) {
        val draft = QuestionDraft(
            id = loaded?.id,
            question = state.question,
            answer = state.answer,
            category = state.category,
            difficulty = state.difficulty,
            tags = state.tags,
            distractors = state.distractors,
            flagged = state.flagged,
        )
        when (val result = repository.save(draft)) {
            is Outcome.Success -> {
                loaded = result.value
                _uiState.value = state.copy(saving = false)
                eventChannel.send(EditorEvent.Saved)
            }

            is Outcome.Failure -> {
                val error = result.error
                _uiState.value = state.copy(
                    saving = false,
                    invalidField = (error as? AppError.Validation)?.field,
                )
                if (error !is AppError.Validation) {
                    eventChannel.send(EditorEvent.SaveFailed)
                }
            }
        }
    }

    private inline fun update(block: (EditorUiState) -> EditorUiState) {
        _uiState.value = block(_uiState.value)
    }

    companion object {
        const val ARG_QUESTION_ID = "questionId"

        /** Routen-Argument fuer "neue Frage" - eine echte ID gibt es dann noch nicht. */
        const val NEW_QUESTION = "neu"
    }
}
