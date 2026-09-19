package com.bookmark.categories.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookmark.categories.data.CategoryError
import com.bookmark.categories.data.CategoryException
import com.bookmark.categories.data.CategoryRepository
import com.bookmark.categories.data.DeleteStrategy
import com.bookmark.categories.data.DeletedCategory
import com.bookmark.core.model.Category
import com.bookmark.core.model.CategoryWithCount
import com.bookmark.core.ui.theme.CategorySwatchHex
import com.bookmark.share.DirectShareShortcuts
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class CategoriesUiState(
    val categories: List<CategoryWithCount> = emptyList(),
)

/** Which dialog, if any, is open over the Categories screen. */
sealed interface CategoryDialogState {
    data object None : CategoryDialogState
    data object Create : CategoryDialogState
    data class Edit(val category: Category) : CategoryDialogState
    data class Delete(val category: Category, val bookmarkCount: Int) : CategoryDialogState
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: CategoryRepository,
    private val shortcuts: DirectShareShortcuts,
) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> = repository.observeAllWithCounts()
        .map(::CategoriesUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    private val _dialog = MutableStateFlow<CategoryDialogState>(CategoryDialogState.None)
    val dialog: StateFlow<CategoryDialogState> = _dialog.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _undo = MutableStateFlow<DeletedCategory?>(null)
    val undo: StateFlow<DeletedCategory?> = _undo.asStateFlow()

    /** Local ordering while a drag is in flight; persisted on drag end. */
    private val _draftOrder = MutableStateFlow<List<CategoryWithCount>?>(null)
    val draftOrder: StateFlow<List<CategoryWithCount>?> = _draftOrder.asStateFlow()

    fun openCreate() {
        _dialog.value = CategoryDialogState.Create
    }

    fun openEdit(category: Category) {
        _dialog.value = CategoryDialogState.Edit(category)
    }

    fun openDelete(category: Category) = viewModelScope.launch {
        if (category.id == Category.UNSORTED_ID) {
            _error.value = "Unsorted can't be deleted — it's where bookmarks fall back to."
            return@launch
        }
        if (category.isDefault) {
            _error.value = "Make another category the default before deleting this one."
            return@launch
        }
        _dialog.value = CategoryDialogState.Delete(category, repository.countIn(category.id))
    }

    fun dismissDialog() {
        _dialog.value = CategoryDialogState.None
    }

    fun clearError() {
        _error.value = null
    }

    fun nextSwatch(): String = CategorySwatchHex[uiState.value.categories.size % CategorySwatchHex.size]

    fun create(name: String, colorHex: String, iconKey: String?) = viewModelScope.launch {
        try {
            repository.create(name, colorHex, iconKey)
            dismissDialog()
        } catch (e: CategoryException) {
            _error.value = e.error.message()
        }
    }

    fun update(category: Category, name: String, colorHex: String, iconKey: String?) =
        viewModelScope.launch {
            try {
                repository.rename(category, name, colorHex, iconKey)
                dismissDialog()
            } catch (e: CategoryException) {
                _error.value = e.error.message()
            }
        }

    fun setDefault(category: Category) = viewModelScope.launch {
        repository.setDefault(category.id)
    }

    fun delete(category: Category, strategy: DeleteStrategy) = viewModelScope.launch {
        try {
            _undo.value = repository.delete(category, strategy)
            dismissDialog()
            shortcuts.publish()
        } catch (e: CategoryException) {
            _error.value = e.error.message()
        }
    }

    fun undoDelete() = viewModelScope.launch {
        _undo.value?.let { repository.restore(it) }
        _undo.value = null
        shortcuts.publish()
    }

    /**
     * The Snackbar expired without an undo, so the delete is now final and the
     * sync tombstone(s) can be recorded. Mirrors `HomeViewModel.clearUndo()`.
     */
    fun clearUndo() {
        val pending = _undo.value ?: return
        _undo.value = null
        viewModelScope.launch { repository.discardDeleted(pending) }
    }

    /** Live reorder while dragging; nothing is written until [commitOrder]. */
    fun moveDraft(from: Int, to: Int) {
        val current = _draftOrder.value ?: uiState.value.categories
        if (from !in current.indices || to !in current.indices) return
        _draftOrder.value = current.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun commitOrder() = viewModelScope.launch {
        val order = _draftOrder.value ?: return@launch
        repository.reorder(order.map { it.category.id })
        _draftOrder.value = null
    }
}

private fun CategoryError.message(): String = when (this) {
    CategoryError.NameTaken -> "A category with that name already exists."
    CategoryError.NameEmpty -> "Give the category a name."
    CategoryError.CannotDeleteFallback -> "Unsorted can't be deleted."
    CategoryError.CannotDeleteDefault -> "Make another category the default first."
}
