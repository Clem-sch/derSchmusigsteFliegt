package de.schmusig.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import de.schmusig.core.data.DeletedQuestionBus
import de.schmusig.core.data.QuestionRepository
import de.schmusig.core.model.CatalogQuery
import de.schmusig.core.model.CatalogSort
import de.schmusig.core.model.CategoryCount
import de.schmusig.core.model.Question
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CatalogUiState(
    val searchText: String = "",
    val query: CatalogQuery = CatalogQuery(),
    val categories: List<CategoryCount> = emptyList(),
    val filtersExpanded: Boolean = false,
    val matchCount: Int = 0,
    val totalCount: Int = 0,
) {
    val isCatalogEmpty: Boolean get() = totalCount == 0
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: QuestionRepository,
    private val deletedQuestionBus: DeletedQuestionBus,
) : ViewModel() {

    private val searchText = MutableStateFlow("")
    private val filters = MutableStateFlow(CatalogQuery())
    private val filtersExpanded = MutableStateFlow(false)

    /**
     * Der Suchtext wird entprellt (7.2: ~250 ms), die Filter nicht - ein Chip-Tap soll
     * sofort wirken.
     */
    private val effectiveQuery: StateFlow<CatalogQuery> = combine(
        searchText.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged(),
        filters,
    ) { text, filter ->
        filter.copy(text = text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CatalogQuery())

    val questions: Flow<PagingData<Question>> = effectiveQuery
        .flatMapLatest { query -> repository.pagedQuestions(query) }
        .cachedIn(viewModelScope)

    val uiState: StateFlow<CatalogUiState> = combine(
        searchText,
        filters,
        filtersExpanded,
        repository.observeCategoryCounts(),
        effectiveQuery.flatMapLatest { query -> repository.observeMatchCount(query) },
    ) { text, filter, expanded, categories, matchCount ->
        CatalogUiState(
            searchText = text,
            query = filter.copy(text = text),
            categories = categories,
            filtersExpanded = expanded,
            matchCount = matchCount,
            totalCount = categories.sumOf { it.count },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = CatalogUiState(),
    )

    /** Die Frage, zu der die Liste eine Undo-Snackbar zeigen soll (7.2). */
    val pendingDeletion: StateFlow<Question?> = deletedQuestionBus.pending

    fun onSearchTextChange(value: String) {
        searchText.value = value
    }

    fun onFiltersExpandedChange(expanded: Boolean) {
        filtersExpanded.value = expanded
    }

    fun onCategoryToggle(category: String) = updateFilters { filter ->
        filter.copy(
            categories = if (category in filter.categories) {
                filter.categories - category
            } else {
                filter.categories + category
            },
        )
    }

    fun onDifficultyToggle(difficulty: Int) = updateFilters { filter ->
        filter.copy(
            difficulties = if (difficulty in filter.difficulties) {
                filter.difficulties - difficulty
            } else {
                filter.difficulties + difficulty
            },
        )
    }

    fun onFlaggedToggle() = updateFilters { it.copy(onlyFlagged = !it.onlyFlagged) }

    fun onWithDistractorsToggle() = updateFilters {
        it.copy(onlyWithDistractors = !it.onlyWithDistractors)
    }

    fun onSortChange(sort: CatalogSort) = updateFilters { it.copy(sort = sort) }

    fun onFiltersReset() = updateFilters { CatalogQuery(sort = it.sort) }

    /** Wird aufgerufen, sobald die Snackbar angezeigt wurde - sie soll nur einmal kommen. */
    fun onDeletionShown() {
        deletedQuestionBus.clear()
    }

    fun onUndoDelete(question: Question) {
        viewModelScope.launch {
            deletedQuestionBus.clear()
            repository.undoSoftDelete(question)
        }
    }

    private inline fun updateFilters(block: (CatalogQuery) -> CatalogQuery) {
        filters.value = block(filters.value)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
