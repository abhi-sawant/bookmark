package com.bookmark.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookmark.bookmarks.data.BookmarkRepository
import com.bookmark.core.model.Bookmark
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
) : ViewModel() {

    /** Newest trashed first; null until the first emission so the screen can tell "loading" from "empty". */
    val items: StateFlow<List<Bookmark>?> = bookmarkRepository.observeTrashed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(bookmark: Bookmark) {
        viewModelScope.launch { bookmarkRepository.restoreFromTrash(bookmark.id) }
    }

    fun deleteForever(bookmark: Bookmark) {
        viewModelScope.launch { bookmarkRepository.deletePermanently(bookmark) }
    }

    fun emptyTrash() {
        viewModelScope.launch { bookmarkRepository.emptyTrash() }
    }
}
