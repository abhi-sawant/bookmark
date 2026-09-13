package com.bookmark.share

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookmark.bookmarks.data.BookmarkRepository
import com.bookmark.bookmarks.data.SaveResult
import com.bookmark.categories.data.CategoryRepository
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.BookmarkLimits
import com.bookmark.core.model.Category
import com.bookmark.core.model.CategoryWithCount
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
data class QuickSaveUiState(
    val url: String = "",
    val title: String = "",
    val otherUrls: List<String> = emptyList(),
    val categories: List<CategoryWithCount> = emptyList(),
    val selectedCategoryId: String = Category.UNSORTED_ID,
    val fetching: Boolean = false,
    val urlError: String? = null,
    val titleTouched: Boolean = false,
    val duplicateOf: Bookmark? = null,
    val savedBookmark: Bookmark? = null,
    val ready: Boolean = false,
) {
    val canSave: Boolean get() = UrlNormalizer.isValid(url)
    val siteName: String? get() = TitleFallback.fromDomain(url)
}

@HiltViewModel
class QuickSaveViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val directShareShortcuts: DirectShareShortcuts,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickSaveUiState())
    val state: StateFlow<QuickSaveUiState> = _state.asStateFlow()

    /** Whatever the share carried; the sheet is already on screen by now. */
    private var sharedSubject: String? = null

    /**
     * Seeds state from the incoming intent. Category data loads separately so
     * the sheet can render from the URL alone inside the 300ms budget (spec 6.3).
     */
    fun start(extracted: UrlExtractor.ExtractedShare, presetCategoryId: String?) {
        sharedSubject = extracted.subjectTitle
        val url = extracted.primaryUrl ?: extracted.rawText
        _state.update {
            it.copy(
                url = url,
                title = TitleFallback.resolve(
                    sharedSubject = extracted.subjectTitle,
                    url = url,
                ).take(BookmarkLimits.TITLE_MAX),
                otherUrls = extracted.otherUrls,
                urlError = if (extracted.hasUrl) null else "No link found in what was shared",
            )
        }

        viewModelScope.launch {
            val categories = categoryRepository.observeAllWithCounts().first()
            val lastUsed = settingsRepository.preferences.first().lastUsedCategoryId
            val resolved = presetCategoryId
                ?: lastUsed?.takeIf { id -> categories.any { it.category.id == id } }
                ?: categoryRepository.defaultCategoryId()
            _state.update {
                it.copy(categories = categories, selectedCategoryId = resolved, ready = true)
            }
        }
    }

    fun onUrlChange(value: String) {
        _state.update { it.copy(url = value, urlError = null) }
    }

    fun onTitleChange(value: String) {
        _state.update {
            it.copy(title = value.take(BookmarkLimits.TITLE_MAX), titleTouched = true)
        }
    }

    fun onCategoryChange(id: String) {
        _state.update { it.copy(selectedCategoryId = id) }
    }

    fun useOtherUrl(url: String) {
        _state.update { current ->
            current.copy(
                url = url,
                otherUrls = (current.otherUrls - url) + current.url,
                title = if (current.titleTouched) {
                    current.title
                } else {
                    TitleFallback.resolve(sharedSubject = sharedSubject, url = url)
                },
                urlError = null,
            )
        }
    }

    fun createCategory(name: String) = viewModelScope.launch {
        val color = CategorySwatchHex[_state.value.categories.size % CategorySwatchHex.size]
        runCatching { categoryRepository.create(name, color) }.onSuccess { created ->
            _state.update {
                it.copy(
                    categories = categoryRepository.observeAllWithCounts().first(),
                    selectedCategoryId = created.id,
                )
            }
        }
    }

    fun save() = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) {
            _state.update { it.copy(urlError = "That doesn't look like a link") }
            return@launch
        }

        val result = bookmarkRepository.save(
            rawUrl = current.url,
            categoryId = current.selectedCategoryId,
            title = current.title.ifBlank { null },
            sharedSubject = sharedSubject,
            manualFields = if (current.titleTouched) ManualField.TITLE else ManualField.NONE,
        )

        when (result) {
            is SaveResult.Saved -> {
                settingsRepository.setLastUsedCategory(current.selectedCategoryId)
                directShareShortcuts.publish()
                _state.update { it.copy(savedBookmark = result.bookmark) }
            }

            is SaveResult.Duplicate -> _state.update { it.copy(duplicateOf = result.existing) }
        }
    }

    /**
     * One-tap Direct Share: saves straight into the named category without ever
     * showing the sheet (spec 6.3). Returns the category name for the toast.
     */
    suspend fun saveDirect(
        extracted: UrlExtractor.ExtractedShare,
        categoryId: String,
    ): Pair<SaveResult, String?> {
        val url = extracted.primaryUrl ?: extracted.rawText
        val result = bookmarkRepository.save(
            rawUrl = url,
            categoryId = categoryId,
            sharedSubject = extracted.subjectTitle,
        )
        if (result is SaveResult.Saved) {
            settingsRepository.setLastUsedCategory(categoryId)
        }
        return result to categoryRepository.findById(categoryId)?.name
    }

    fun dismissDuplicate() {
        _state.update { it.copy(duplicateOf = null) }
    }
}
