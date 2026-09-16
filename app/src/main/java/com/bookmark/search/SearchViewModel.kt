package com.bookmark.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookmark.bookmarks.data.BookmarkRepository
import com.bookmark.categories.data.CategoryRepository
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.CategoryWithCount
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

private const val DEBOUNCE_MS = 150L

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val categoryRepository: CategoryRepository,
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

    fun selectCategory(categoryId: String?) {
        _state.update { it.copy(selectedCategoryId = categoryId) }
        // A chip tap is a discrete action, not free typing -- no debounce.
        scheduleSearch(immediate = true)
    }

    private fun scheduleSearch(immediate: Boolean = false) {
        searchJob?.cancel()
        val query = _state.value.query
        if (query.isBlank()) {
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
                results = bookmarkRepository.search(query, categoryId)
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
