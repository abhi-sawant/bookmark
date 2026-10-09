package com.bookmark.bookmarks.edit

import android.content.Context
import android.net.Uri
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
import com.bookmark.metadata.MetadataFetcher
import com.bookmark.metadata.MetadataResult
import com.bookmark.metadata.image.ThumbnailPipeline
import com.bookmark.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The user's own thumbnail decision, overriding whatever the engine finds (spec 5.2 item 5). */
sealed interface ThumbnailChoice {
    /** No manual choice made -- the background pipeline picks as usual. */
    data object Auto : ThumbnailChoice
    data class Candidate(val url: String) : ThumbnailChoice
    data class Local(val uri: Uri) : ThumbnailChoice
    data object Removed : ThumbnailChoice
}

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
    /** From the live fetch (or seeded from an existing bookmark on edit). */
    val fetchedSiteName: String? = null,
    /** Up to five image URLs the live fetch found, feeding the Thumbnail picker. */
    val imageCandidates: List<String> = emptyList(),
    val thumbnailChoice: ThumbnailChoice = ThumbnailChoice.Auto,
) {
    val isEditing: Boolean get() = editingId != null

    /** Enabled as soon as the URL is syntactically valid; never gated on fetch state. */
    val canSave: Boolean get() = UrlNormalizer.isValid(url)

    val siteName: String? get() = fetchedSiteName ?: TitleFallback.fromDomain(url)

    val previewTitle: String
        get() = title.ifBlank { TitleFallback.resolve(url = url.ifBlank { "" }) }

    /** What the live-preview card's thumbnail should show, before anything is saved. */
    val previewThumbnailModel: Any?
        get() = when (val choice = thumbnailChoice) {
            is ThumbnailChoice.Local -> choice.uri
            is ThumbnailChoice.Candidate -> choice.url
            ThumbnailChoice.Removed -> null
            ThumbnailChoice.Auto -> imageCandidates.firstOrNull()
        }
}

@HiltViewModel
class AddEditViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val metadataFetcher: MetadataFetcher,
    private val thumbnailPipeline: ThumbnailPipeline,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(AddEditUiState())
    val state: StateFlow<AddEditUiState> = _state.asStateFlow()

    /** Cancelled and relaunched on every URL change -- the debounce (spec 5.2). */
    private var livePreviewJob: Job? = null

    init {
        viewModelScope.launch {
            categoryRepository.observeAll().collect { categories ->
                _state.update { it.copy(categories = categories) }
            }
        }
    }

    /** Opens the sheet for a new bookmark, defaulting to the last-used category. */
    fun startAdd() = viewModelScope.launch {
        livePreviewJob?.cancel()
        val lastUsed = settingsRepository.preferences.first().lastUsedCategoryId
        // The last-used category may since have been deleted.
        val defaultId = lastUsed?.takeIf { categoryRepository.findById(it) != null }
            ?: categoryRepository.defaultCategoryId()
        _state.value = AddEditUiState(categoryId = defaultId, categories = _state.value.categories)
    }

    fun startEdit(bookmark: Bookmark) {
        livePreviewJob?.cancel()
        _state.value = AddEditUiState(
            editingId = bookmark.id,
            url = bookmark.url,
            title = bookmark.title,
            description = bookmark.description.orEmpty(),
            categoryId = bookmark.categoryId,
            categories = _state.value.categories,
            touchedFields = bookmark.manualFields,
            fetchedSiteName = bookmark.siteName,
            imageCandidates = bookmark.imageCandidates,
        )
    }

    fun reset() {
        livePreviewJob?.cancel()
        _state.value = AddEditUiState(categories = _state.value.categories)
    }

    fun onUrlChange(value: String) {
        _state.update { it.copy(url = value, urlError = null, duplicateOf = null) }
        scheduleLivePreviewFetch()
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
        // A paste starts the fetch too, not just typing (spec 5.2).
        scheduleLivePreviewFetch()
    }

    fun dismissClipboardSuggestion() {
        _state.update { it.copy(clipboardSuggestion = null) }
    }

    fun createCategory(name: String) = viewModelScope.launch {
        val color = CategorySwatchHex[(_state.value.categories.size) % CategorySwatchHex.size]
        runCatching { categoryRepository.create(name, color) }
            .onSuccess { created -> _state.update { it.copy(categoryId = created.id) } }
    }

    /**
     * Debounced 600ms after the URL stops changing (spec 5.2). Calls the pure
     * [MetadataFetcher] directly rather than going through WorkManager: there is
     * no bookmark id yet to enqueue against, and this result is preview-only --
     * the real persisted fetch runs again after save via [BookmarkRepository.save]'s
     * automatic enqueue, exactly as it does today.
     */
    private fun scheduleLivePreviewFetch(immediate: Boolean = false) {
        livePreviewJob?.cancel()
        val url = _state.value.url
        if (!UrlNormalizer.isValid(url)) {
            _state.update { it.copy(fetching = false) }
            return
        }
        livePreviewJob = viewModelScope.launch {
            if (!immediate) delay(LIVE_PREVIEW_DEBOUNCE_MS)
            _state.update { it.copy(fetching = true) }
            applyLivePreview(metadataFetcher.fetch(url))
        }
    }

    /** "Retry fetch" in the Thumbnail picker -- the same fetch, just not debounced. */
    fun retryLivePreview() = scheduleLivePreviewFetch(immediate = true)

    private fun applyLivePreview(result: MetadataResult) {
        val metadata = when (result) {
            is MetadataResult.Success -> result.metadata
            is MetadataResult.Partial -> result.metadata
            // Fallback/Failed/Pending carry no metadata; the existing fallback
            // chain in previewTitle/siteName already covers the display.
            else -> null
        }
        _state.update { current ->
            current.copy(
                fetching = false,
                title = metadata?.title?.takeIf {
                    it.isNotBlank() && !ManualField.isSet(current.touchedFields, ManualField.TITLE)
                } ?: current.title,
                description = metadata?.description?.takeIf {
                    !ManualField.isSet(current.touchedFields, ManualField.DESCRIPTION)
                } ?: current.description,
                fetchedSiteName = metadata?.siteName ?: current.fetchedSiteName,
                // Never lock-gated, same as the persisted applyMetadata SQL: always fresh.
                imageCandidates = metadata?.imageCandidates ?: current.imageCandidates,
            )
        }
    }

    /** "Choose another image found on page" in the Thumbnail picker. */
    fun selectThumbnailCandidate(url: String) {
        _state.update {
            it.copy(
                thumbnailChoice = ThumbnailChoice.Candidate(url),
                touchedFields = ManualField.set(it.touchedFields, ManualField.THUMBNAIL),
            )
        }
    }

    /** "Pick from device" in the Thumbnail picker. */
    fun pickLocalThumbnail(uri: Uri) {
        _state.update {
            it.copy(
                thumbnailChoice = ThumbnailChoice.Local(uri),
                touchedFields = ManualField.set(it.touchedFields, ManualField.THUMBNAIL),
            )
        }
    }

    /** "Remove" in the Thumbnail picker. */
    fun removeThumbnail() {
        _state.update {
            it.copy(
                thumbnailChoice = ThumbnailChoice.Removed,
                touchedFields = ManualField.set(it.touchedFields, ManualField.THUMBNAIL),
            )
        }
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
            applyThumbnailChoice(current.editingId, current.thumbnailChoice)
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
                applyThumbnailChoice(result.bookmark.id, current.thumbnailChoice)
                settingsRepository.setLastUsedCategory(current.categoryId)
                _state.update { it.copy(savedBookmark = result.bookmark) }
            }

            is SaveResult.Duplicate -> _state.update { it.copy(duplicateOf = result.existing) }
        }
    }

    /**
     * A bookmark's thumbnail file is named `{bookmarkId}.webp`, so it cannot be
     * written before the row exists -- this always runs right after the insert
     * or update above, and only when the user actually made a choice. `Auto`
     * leaves the existing background-pipeline behaviour untouched.
     */
    private suspend fun applyThumbnailChoice(bookmarkId: String, choice: ThumbnailChoice) {
        when (choice) {
            ThumbnailChoice.Auto -> Unit

            is ThumbnailChoice.Candidate -> bookmarkRepository.applyManualThumbnail(
                bookmarkId,
                thumbnailPipeline.store(
                    candidates = listOf(choice.url),
                    bookmarkId = bookmarkId,
                    directory = bookmarkRepository.thumbnailDir(),
                ),
            )

            is ThumbnailChoice.Local -> bookmarkRepository.applyManualThumbnail(
                bookmarkId,
                thumbnailPipeline.storeFromUri(
                    context = context,
                    uri = choice.uri,
                    bookmarkId = bookmarkId,
                    directory = bookmarkRepository.thumbnailDir(),
                ),
            )

            ThumbnailChoice.Removed -> bookmarkRepository.applyManualThumbnail(bookmarkId, null)
        }
    }

    fun consumeSaved() {
        _state.update { it.copy(savedBookmark = null) }
    }

    fun dismissDuplicate() {
        _state.update { it.copy(duplicateOf = null) }
    }

    /** "Refresh preview" on the duplicate sheet -- a manual retry on the existing row. */
    fun refreshDuplicatePreview() {
        _state.value.duplicateOf?.let { bookmarkRepository.requestManualFetch(it.id) }
        dismissDuplicate()
    }

    private companion object {
        const val LIVE_PREVIEW_DEBOUNCE_MS = 600L
    }
}
