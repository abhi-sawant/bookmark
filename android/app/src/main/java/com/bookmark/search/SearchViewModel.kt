package com.bookmark.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookmark.bookmarks.data.BookmarkRepository
import com.bookmark.categories.data.CategoryRepository
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.CategoryWithCount
import com.bookmark.core.model.SortOrder
import com.bookmark.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SearchUiState(
    val query: String = "",
    val selectedCategoryId: String? = null,
    val categories: List<CategoryWithCount> = emptyList(),
    val totalCount: Int = 0,
    val results: List<Bookmark> = emptyList(),
    val queryTerms: List<String> = emptyList(),
    val elapsedMs: Long? = null,
    val hasSearched: Boolean = false,
    val filters: SearchFilters = SearchFilters(),
    val recentSearches: List<String> = emptyList(),
)

private const val DEBOUNCE_MS = 150L

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            categoryRepository.observeAllWithCounts().collect { categories ->
                _state.update { it.copy(categories = categories, totalCount = categories.sumOf { c -> c.count }) }
            }
        }
    }

    init {
        viewModelScope.launch {
            settingsRepository.recentSearches.collect { recents ->
                _state.update { it.copy(recentSearches = recents) }
            }
        }
    }

    fun thumbnailFile(bookmark: Bookmark) =
        bookmark.thumbnailPath?.let { bookmarkRepository.thumbnailFile(it) }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        scheduleSearch()
    }

    fun clearQuery() {
        searchJob?.cancel()
        _state.update {
            it.copy(query = "", results = emptyList(), queryTerms = emptyList(), hasSearched = false, elapsedMs = null)
        }
    }

    fun setFilters(filters: SearchFilters) {
        _state.update { it.copy(filters = filters) }
        scheduleSearch(immediate = true)
    }

    /** The user submitted the query or opened a result from it: remember it. */
    fun commitQuery() {
        val query = _state.value.query
        if (query.isBlank()) return
        viewModelScope.launch { settingsRepository.addRecentSearch(query) }
    }

    fun useRecentSearch(query: String) {
        _state.update { it.copy(query = query) }
        scheduleSearch(immediate = true)
    }

    fun clearRecentSearches() {
        viewModelScope.launch { settingsRepository.clearRecentSearches() }
    }

    fun selectCategory(categoryId: String?) {
        _state.update { it.copy(selectedCategoryId = categoryId) }
        // A chip tap is a discrete action, not free typing -- no debounce.
        scheduleSearch(immediate = true)
    }

    private fun scheduleSearch(immediate: Boolean = false) {
        searchJob?.cancel()
        val query = _state.value.query
        if (query.isBlank() && !_state.value.filters.isActive) {
            _state.update {
                it.copy(results = emptyList(), queryTerms = emptyList(), hasSearched = false, elapsedMs = null)
            }
            return
        }
        searchJob = viewModelScope.launch {
            if (!immediate) delay(DEBOUNCE_MS)
            val categoryId = _state.value.selectedCategoryId
            var results: List<Bookmark> = emptyList()
            val elapsed = measureTimeMillis {
                val filters = _state.value.filters
                // With no text, an active chip alone browses everything it matches.
                val found = if (query.isBlank()) {
                    bookmarkRepository.observe(categoryId, SortOrder.NEWEST).first()
                } else {
                    bookmarkRepository.search(query, categoryId)
                }
                results = filters.apply(found)
            }
            _state.update {
                it.copy(
                    results = results,
                    queryTerms = queryTermsFor(query),
                    hasSearched = true,
                    elapsedMs = elapsed,
                )
            }
        }
    }
}
