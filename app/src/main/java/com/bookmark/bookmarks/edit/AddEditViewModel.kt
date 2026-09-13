package com.bookmark.bookmarks.edit

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookmark.bookmarks.data.BookmarkRepository
import com.bookmark.bookmarks.data.SaveResult
import com.bookmark.categories.data.CategoryRepository
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.BookmarkLimits
import com.bookmark.core.model.Category
import com.bookmark.core.model.ManualField
import com.bookmark.core.ui.theme.CategorySwatchHex
import com.bookmark.core.util.TitleFallback
import com.bookmark.core.util.UrlNormalizer
import com.bookmark.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class AddEditUiState(
    val editingId: String? = null,
    val url: String = "",
    val title: String = "",
    val description: String = "",
    val categoryId: String = Category.UNSORTED_ID,
    val categories: List<Category> = emptyList(),
    val clipboardSuggestion: String? = null,
    val fetching: Boolean = false,
    val urlError: String? = null,
    /** Bits from [ManualField] for every field the user has touched. */
    val touchedFields: Int = ManualField.NONE,
    val duplicateOf: Bookmark? = null,
    val savedBookmark: Bookmark? = null,
) {
    val isEditing: Boolean get() = editingId != null

    /** Enabled as soon as the URL is syntactically valid; never gated on fetch state. */
    val canSave: Boolean get() = UrlNormalizer.isValid(url)

    val siteName: String? get() = TitleFallback.fromDomain(url)

    val previewTitle: String
        get() = title.ifBlank { TitleFallback.resolve(url = url.ifBlank { "" }) }
}

@HiltViewModel
class AddEditViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddEditUiState())
    val state: StateFlow<AddEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            categoryRepository.observeAll().collect { categories ->
                _state.update { it.copy(categories = categories) }
            }
        }
    }

    /** Opens the sheet for a new bookmark, defaulting to the last-used category. */
    fun startAdd() = viewModelScope.launch {
        val lastUsed = settingsRepository.preferences.first().lastUsedCategoryId
        // The last-used category may since have been deleted.
        val defaultId = lastUsed?.takeIf { categoryRepository.findById(it) != null }
            ?: categoryRepository.defaultCategoryId()
        _state.value = AddEditUiState(categoryId = defaultId, categories = _state.value.categories)
    }

    fun startEdit(bookmark: Bookmark) {
        _state.value = AddEditUiState(
            editingId = bookmark.id,
            url = bookmark.url,
            title = bookmark.title,
            description = bookmark.description.orEmpty(),
            categoryId = bookmark.categoryId,
            categories = _state.value.categories,
            touchedFields = bookmark.manualFields,
        )
    }

    fun reset() {
        _state.value = AddEditUiState(categories = _state.value.categories)
    }

    fun onUrlChange(value: String) {
        _state.update { it.copy(url = value, urlError = null, duplicateOf = null) }
    }

    fun onTitleChange(value: String) {
        _state.update {
            it.copy(
                title = value.take(BookmarkLimits.TITLE_MAX),
                touchedFields = ManualField.set(it.touchedFields, ManualField.TITLE),
            )
        }
    }

    fun onDescriptionChange(value: String) {
        _state.update {
            it.copy(
                description = value.take(BookmarkLimits.DESCRIPTION_MAX),
                touchedFields = ManualField.set(it.touchedFields, ManualField.DESCRIPTION),
            )
        }
    }

    fun onCategoryChange(categoryId: String) {
        _state.update { it.copy(categoryId = categoryId) }
    }

    /**
     * Offers a clipboard URL as a dismissible chip. Never pasted silently
     * (spec 5.2), and never offered for something already saved.
     */
    fun offerClipboard(text: String?) = viewModelScope.launch {
        val candidate = text?.trim().orEmpty()
        if (candidate.isEmpty() || !UrlNormalizer.isValid(candidate)) return@launch
        if (_state.value.url.isNotBlank() || _state.value.isEditing) return@launch
        if (bookmarkRepository.findByNormalizedUrl(candidate) != null) return@launch
        _state.update { it.copy(clipboardSuggestion = candidate) }
    }

    fun acceptClipboardSuggestion() {
        _state.update { it.copy(url = it.clipboardSuggestion.orEmpty(), clipboardSuggestion = null) }
    }

    fun dismissClipboardSuggestion() {
        _state.update { it.copy(clipboardSuggestion = null) }
    }

    fun createCategory(name: String) = viewModelScope.launch {
        val color = CategorySwatchHex[(_state.value.categories.size) % CategorySwatchHex.size]
        runCatching { categoryRepository.create(name, color) }
            .onSuccess { created -> _state.update { it.copy(categoryId = created.id) } }
    }

    fun save() = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) {
            _state.update { it.copy(urlError = "That doesn't look like a link") }
            return@launch
        }

        if (current.isEditing) {
            val existing = bookmarkRepository.findById(current.editingId!!) ?: return@launch
            bookmarkRepository.update(
                existing.copy(
                    title = current.title.ifBlank { existing.title },
                    description = current.description.ifBlank { null },
                    categoryId = current.categoryId,
                    manualFields = current.touchedFields,
                ),
            )
            settingsRepository.setLastUsedCategory(current.categoryId)
            _state.update { it.copy(savedBookmark = existing) }
            return@launch
        }

        when (val result = bookmarkRepository.save(
            rawUrl = current.url,
            categoryId = current.categoryId,
            title = current.title.ifBlank { null },
            description = current.description.ifBlank { null },
            manualFields = current.touchedFields,
        )) {
            is SaveResult.Saved -> {
                settingsRepository.setLastUsedCategory(current.categoryId)
                _state.update { it.copy(savedBookmark = result.bookmark) }
            }

            is SaveResult.Duplicate -> _state.update { it.copy(duplicateOf = result.existing) }
        }
    }

    fun consumeSaved() {
        _state.update { it.copy(savedBookmark = null) }
    }

    fun dismissDuplicate() {
        _state.update { it.copy(duplicateOf = null) }
    }
}
