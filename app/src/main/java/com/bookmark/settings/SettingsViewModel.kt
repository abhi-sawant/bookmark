package com.bookmark.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookmark.account.AccountRepository
import com.bookmark.account.data.AuthState
import com.bookmark.backup.BackupRepository
import com.bookmark.backup.ExportResult
import com.bookmark.backup.ImportCommitResult
import com.bookmark.backup.ImportMode
import com.bookmark.backup.ImportPreview
import com.bookmark.backup.ImportPreviewResult
import com.bookmark.bookmarks.data.BookmarkRepository
import com.bookmark.categories.data.CategoryRepository
import com.bookmark.core.model.ThemeMode
import com.bookmark.core.model.UserPreferences
import com.bookmark.metadata.work.MetadataEnqueuer
import com.bookmark.metadata.work.RefreshAllWorker
import com.bookmark.sync.data.SyncStateStore
import com.bookmark.sync.work.SyncEnqueuer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything Settings shows a live number for. */
data class BackupSummary(
    val bookmarkCount: Int = 0,
    val categoryCount: Int = 0,
    val eligibleRefreshCount: Int = 0,
    val thumbnailBytes: Long = 0L,
) {
    /** Thumbnails dominate a real backup's size -- the JSON itself is a rough per-row estimate. */
    val estimatedZipBytes: Long get() = thumbnailBytes + bookmarkCount * 400L + categoryCount * 120L
}

/** Which sheet, if any, the "Import backup" row has open. */
sealed interface ImportSheetState {
    data object None : ImportSheetState
    data class Preview(val source: Uri, val preview: ImportPreview, val mode: ImportMode) : ImportSheetState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val metadataEnqueuer: MetadataEnqueuer,
    private val bookmarkRepository: BookmarkRepository,
    private val categoryRepository: CategoryRepository,
    private val backupRepository: BackupRepository,
    private val accountRepository: AccountRepository,
    private val syncStateStore: SyncStateStore,
    private val syncEnqueuer: SyncEnqueuer,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = settingsRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    val authState: StateFlow<AuthState> = accountRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.SignedOut)

    val lastSyncedAt: StateFlow<Long?> = syncStateStore.lastSyncedAtFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // countByStates/thumbnailBytes are one-shot suspend reads, not Flows, so
    // they're refreshed explicitly (on init, and after any action that could
    // change either number) into this half of the summary, then combined
    // with the two genuinely reactive counts below.
    private val oneShotCounts = MutableStateFlow(0 to 0L) // eligibleRefreshCount to thumbnailBytes

    val summary: StateFlow<BackupSummary> = combine(
        bookmarkRepository.observeTotalCount(),
        categoryRepository.observeAll(),
        oneShotCounts,
    ) { bookmarkCount, categories, (eligible, thumbnailBytes) ->
        BackupSummary(bookmarkCount, categories.size, eligible, thumbnailBytes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupSummary())

    private val _importSheetState = MutableStateFlow<ImportSheetState>(ImportSheetState.None)
    val importSheetState: StateFlow<ImportSheetState> = _importSheetState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refreshOneShotCounts()
    }

    private fun refreshOneShotCounts() {
        viewModelScope.launch {
            oneShotCounts.value =
                bookmarkRepository.countByStates(RefreshAllWorker.ELIGIBLE) to bookmarkRepository.thumbnailBytes()
        }
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settingsRepository.setThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDynamicColor(enabled)
    }

    fun setTrueBlack(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setTrueBlack(enabled)
    }

    fun setFetchPreviews(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setFetchPreviews(enabled)
        // Spec 11 promises the app is *100% network-silent* with this off, not
        // merely that it stops scheduling new work -- anything already queued
        // would otherwise still run the next time there is a network.
        if (!enabled) metadataEnqueuer.cancelAllAutomatic()
    }

    /** Settings, "Refresh all metadata" -- the engine has existed since M3. */
    fun refreshAll() = metadataEnqueuer.enqueueRefreshAll()

    /** Settings, "Clear thumbnails" -- files go, bookmarks stay (spec 5.6). */
    fun clearThumbnails() = viewModelScope.launch {
        bookmarkRepository.clearThumbnails()
        refreshOneShotCounts()
    }

    /** [uri] is already picked -- the SAF launcher lives in `BookmarkNavHost`. */
    fun onExportUriPicked(uri: Uri) = viewModelScope.launch {
        _message.value = when (val result = backupRepository.export(uri)) {
            is ExportResult.Success -> {
                val bookmarks = plural(result.bookmarkCount, "bookmark")
                val categories = plural(result.categoryCount, "category", "categories")
                "Exported $bookmarks and $categories."
            }
            ExportResult.Failure -> "Couldn't export a backup."
        }
    }

    fun onImportUriPicked(uri: Uri) = viewModelScope.launch {
        when (val result = backupRepository.previewImport(uri)) {
            is ImportPreviewResult.Success ->
                _importSheetState.value = ImportSheetState.Preview(uri, result.preview, ImportMode.MERGE)
            is ImportPreviewResult.Failure -> _message.value = result.message
        }
    }

    fun setImportMode(mode: ImportMode) {
        val current = _importSheetState.value
        if (current is ImportSheetState.Preview) {
            _importSheetState.value = current.copy(mode = mode)
        }
    }

    fun dismissImportSheet() {
        _importSheetState.value = ImportSheetState.None
    }

    fun confirmImport() {
        val current = _importSheetState.value
        if (current !is ImportSheetState.Preview) return
        viewModelScope.launch {
            val result = backupRepository.commitImport(current.source, current.preview.parsed, current.mode)
            _importSheetState.value = ImportSheetState.None
            _message.value = when (result) {
                ImportCommitResult.Success -> "Import complete."
                is ImportCommitResult.Failure -> result.message
            }
            refreshOneShotCounts()
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Settings, "Sync now" -- enqueued rather than run inline; [lastSyncedAt] reflects the outcome. */
    fun syncNow() = syncEnqueuer.syncNow()

    fun signOut() = viewModelScope.launch {
        accountRepository.logout()
    }
}

private fun plural(count: Int, singular: String, plural: String = "${singular}s") =
    "$count ${if (count == 1) singular else plural}"
