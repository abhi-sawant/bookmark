package com.bookmark.bookmarks.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookmark.bookmarks.data.BookmarkRepository
import com.bookmark.categories.data.CategoryRepository
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.Category
import com.bookmark.core.model.CategoryWithCount
import com.bookmark.core.model.SortOrder
import com.bookmark.core.model.ViewMode
import com.bookmark.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class HomeUiState(
    val bookmarks: List<Bookmark> = emptyList(),
    val categories: List<CategoryWithCount> = emptyList(),
    val categoriesById: Map<String, Category> = emptyMap(),
    val selectedCategoryId: String? = null,
    val totalCount: Int = 0,
    val viewMode: ViewMode = ViewMode.GRID,
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && totalCount == 0
}

/** A bookmark the user just deleted, held so the Snackbar can put it back. */
data class PendingUndo(val bookmark: Bookmark)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val selectedCategoryId = MutableStateFlow<String?>(null)

    private val _undo = MutableStateFlow<PendingUndo?>(null)
    val undo: StateFlow<PendingUndo?> = _undo

    @OptIn(ExperimentalCoroutinesApi::class)
    private val bookmarks = combine(
        selectedCategoryId,
        settingsRepository.preferences.map { it.sortOrder },
    ) { categoryId, sort -> categoryId to sort }
        .flatMapLatest { (categoryId, sort) -> bookmarkRepository.observe(categoryId, sort) }

    val uiState: StateFlow<HomeUiState> = combine(
        bookmarks,
        categoryRepository.observeAllWithCounts(),
        bookmarkRepository.observeTotalCount(),
        selectedCategoryId,
        settingsRepository.preferences,
    ) { items, categories, total, categoryId, preferences ->
        HomeUiState(
            bookmarks = items,
            categories = categories,
            categoriesById = categories.associate { it.category.id to it.category },
            selectedCategoryId = categoryId,
            totalCount = total,
            viewMode = preferences.viewMode,
            sortOrder = preferences.sortOrder,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectCategory(categoryId: String?) {
        selectedCategoryId.value = categoryId
    }

    fun setViewMode(mode: ViewMode) = viewModelScope.launch {
        settingsRepository.setViewMode(mode)
    }

    fun setSortOrder(order: SortOrder) = viewModelScope.launch {
        settingsRepository.setSortOrder(order)
    }

    fun togglePin(bookmark: Bookmark) = viewModelScope.launch {
        bookmarkRepository.setPinned(bookmark.id, !bookmark.isPinned)
    }

    fun changeCategory(bookmark: Bookmark, categoryId: String) = viewModelScope.launch {
        bookmarkRepository.setCategory(bookmark.id, categoryId)
    }

    /** Hard delete with Snackbar undo (spec 14 Q2). */
    fun delete(bookmark: Bookmark) = viewModelScope.launch {
        bookmarkRepository.delete(bookmark)
        _undo.value = PendingUndo(bookmark)
    }

    fun undoDelete() = viewModelScope.launch {
        _undo.value?.let { bookmarkRepository.restore(it.bookmark) }
        _undo.value = null
    }

    /**
     * The Snackbar expired without an undo, so the delete is now final and the
     * thumbnail file can go. `delete` deliberately leaves the file behind so
     * that undo restores a complete bookmark rather than a monogram tile.
     */
    fun clearUndo() {
        val pending = _undo.value ?: return
        _undo.value = null
        viewModelScope.launch { bookmarkRepository.discardDeleted(pending.bookmark) }
    }

    fun thumbnailFile(bookmark: Bookmark): File? =
        bookmark.thumbnailPath?.let { bookmarkRepository.thumbnailFile(it) }

    /** "Retry fetch" -- the detail sheet's FAILED card, or the FALLBACK context-sheet row. */
    fun retryFetch(bookmark: Bookmark) {
        bookmarkRepository.requestManualFetch(bookmark.id)
    }
}
