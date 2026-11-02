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
import com.bookmark.core.util.Tags
import com.bookmark.core.util.UrlNormalizer
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
import kotlinx.coroutines.flow.first
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
    val unreadOnly: Boolean = false,
    /** Bulk-select mode: tapping toggles instead of opening. */
    val selecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && totalCount == 0
}

/** Bookmarks the user just moved to Trash, held so the Snackbar can put them back. */
data class PendingUndo(val bookmarks: List<Bookmark>) {
    val bookmark: Bookmark get() = bookmarks.first()
}

private data class Controls(
    val categoryId: String?,
    val unreadOnly: Boolean,
    val selectedIds: Set<String>?,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val unreadOnly = MutableStateFlow(false)

    /** Null when not selecting; otherwise the selected bookmark ids. */
    private val selection = MutableStateFlow<Set<String>?>(null)

    private val _undo = MutableStateFlow<PendingUndo?>(null)
    val undo: StateFlow<PendingUndo?> = _undo

    private val _clipboardSuggestion = MutableStateFlow<String?>(null)

    /** A copied link offered as a one-tap save chip, or null. */
    val clipboardSuggestion: StateFlow<String?> = _clipboardSuggestion

    /** The last link the user dismissed or saved, so the chip does not come straight back. */
    private var handledClipboardUrl: String? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val bookmarks = combine(
        selectedCategoryId,
        settingsRepository.preferences.map { it.sortOrder },
    ) { categoryId, sort -> categoryId to sort }
        .flatMapLatest { (categoryId, sort) -> bookmarkRepository.observe(categoryId, sort) }
        .combine(unreadOnly) { items, onlyUnread -> if (onlyUnread) items.filter { it.isUnread } else items }

    val uiState: StateFlow<HomeUiState> = combine(
        bookmarks,
        categoryRepository.observeAllWithCounts(),
        bookmarkRepository.observeTotalCount(),
        combine(selectedCategoryId, unreadOnly, selection, ::Controls),
        settingsRepository.preferences,
    ) { items, categories, total, controls, preferences ->
        HomeUiState(
            bookmarks = items,
            categories = categories,
            categoriesById = categories.associate { it.category.id to it.category },
            selectedCategoryId = controls.categoryId,
            totalCount = total,
            viewMode = preferences.viewMode,
            unreadOnly = controls.unreadOnly,
            selecting = controls.selectedIds != null,
            selectedIds = controls.selectedIds.orEmpty(),
            sortOrder = preferences.sortOrder,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * Offers [text] as a save chip when it is a valid link that is not already
     * saved and was not already handled. Never saves on its own.
     */
    fun offerClipboard(text: String?) = viewModelScope.launch {
        val candidate = text?.trim().orEmpty()
        val enabled = settingsRepository.preferences.first().showClipboardChip
        if (!enabled || candidate.isEmpty() || !UrlNormalizer.isValid(candidate)) {
            _clipboardSuggestion.value = null
            return@launch
        }
        val normalized = UrlNormalizer.normalize(candidate)
        if (normalized == handledClipboardUrl || bookmarkRepository.findByNormalizedUrl(candidate) != null) {
            _clipboardSuggestion.value = null
            return@launch
        }
        _clipboardSuggestion.value = candidate
    }

    fun dismissClipboardSuggestion() {
        _clipboardSuggestion.value?.let { handledClipboardUrl = UrlNormalizer.normalize(it) }
        _clipboardSuggestion.value = null
    }

    /** One-tap save into the last used category (or Unsorted). */
    fun saveClipboardSuggestion() = viewModelScope.launch {
        val url = _clipboardSuggestion.value ?: return@launch
        handledClipboardUrl = UrlNormalizer.normalize(url)
        _clipboardSuggestion.value = null
        val lastUsed = settingsRepository.preferences.first().lastUsedCategoryId
        val categoryId = lastUsed?.takeIf { uiState.value.categoriesById.containsKey(it) } ?: Category.UNSORTED_ID
        bookmarkRepository.save(rawUrl = url, categoryId = categoryId)
    }

    fun selectCategory(categoryId: String?) {
        selectedCategoryId.value = categoryId
    }

    fun setViewMode(mode: ViewMode) = viewModelScope.launch {
        settingsRepository.setViewMode(mode)
    }

    fun setSortOrder(order: SortOrder) = viewModelScope.launch {
        settingsRepository.setSortOrder(order)
    }

    // ---- Bulk select ----

    fun startSelection(bookmark: Bookmark) {
        selection.value = setOf(bookmark.id)
    }

    fun toggleSelected(bookmark: Bookmark) {
        val current = selection.value ?: return
        selection.value = if (bookmark.id in current) current - bookmark.id else current + bookmark.id
    }

    fun clearSelection() {
        selection.value = null
    }

    private fun selectedBookmarks(): List<Bookmark> {
        val ids = selection.value.orEmpty()
        return uiState.value.bookmarks.filter { it.id in ids }
    }

    fun pinSelected() = viewModelScope.launch {
        val chosen = selectedBookmarks()
        // If everything selected is already pinned the action unpins, like a toggle.
        bookmarkRepository.setPinnedAll(chosen.map { it.id }, pinned = !chosen.all { it.isPinned })
        selection.value = null
    }

    fun moveSelected(categoryId: String) = viewModelScope.launch {
        bookmarkRepository.setCategoryAll(selection.value.orEmpty(), categoryId)
        selection.value = null
    }

    fun tagSelected(text: String) = viewModelScope.launch {
        val tags = Tags.parse(text)
        if (tags.isNotEmpty()) bookmarkRepository.addTagsAll(selection.value.orEmpty(), tags)
        selection.value = null
    }

    fun trashSelected() = viewModelScope.launch {
        val chosen = selectedBookmarks()
        if (chosen.isEmpty()) return@launch
        bookmarkRepository.trashAll(chosen.map { it.id })
        _undo.value = PendingUndo(chosen)
        selection.value = null
    }

    fun markOpened(bookmark: Bookmark) = viewModelScope.launch {
        bookmarkRepository.markOpened(bookmark)
    }

    fun setUnread(bookmark: Bookmark, unread: Boolean) = viewModelScope.launch {
        bookmarkRepository.setUnread(bookmark.id, unread)
    }

    fun setUnreadOnly(enabled: Boolean) {
        unreadOnly.value = enabled
    }

    fun togglePin(bookmark: Bookmark) = viewModelScope.launch {
        bookmarkRepository.setPinned(bookmark.id, !bookmark.isPinned)
    }

    fun changeCategory(bookmark: Bookmark, categoryId: String) = viewModelScope.launch {
        bookmarkRepository.setCategory(bookmark.id, categoryId)
    }

    /** Moves to Trash, with a Snackbar undo. It can also be restored from Settings > Trash. */
    fun delete(bookmark: Bookmark) = viewModelScope.launch {
        bookmarkRepository.trash(bookmark.id)
        _undo.value = PendingUndo(listOf(bookmark))
    }

    fun undoDelete() = viewModelScope.launch {
        _undo.value?.let { bookmarkRepository.restoreAllFromTrash(it.bookmarks.map(Bookmark::id)) }
        _undo.value = null
    }

    /** The Snackbar expired without an undo: the bookmark simply stays in Trash. */
    fun clearUndo() {
        _undo.value = null
    }

    fun thumbnailFile(bookmark: Bookmark): File? =
        bookmark.thumbnailPath?.let { bookmarkRepository.thumbnailFile(it) }

    /** "Retry fetch" -- the detail sheet's FAILED card, or the FALLBACK context-sheet row. */
    fun retryFetch(bookmark: Bookmark) {
        bookmarkRepository.requestManualFetch(bookmark.id)
    }
}
