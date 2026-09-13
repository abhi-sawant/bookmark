package com.bookmark.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookmark.bookmarks.detail.BookmarkContextSheet
import com.bookmark.bookmarks.detail.BookmarkDetailSheet
import com.bookmark.bookmarks.detail.DuplicateBookmarkSheet
import com.bookmark.bookmarks.edit.AddEditBookmarkSheet
import com.bookmark.bookmarks.edit.AddEditViewModel
import com.bookmark.bookmarks.home.HomeScreen
import com.bookmark.bookmarks.home.HomeViewModel
import com.bookmark.categories.ui.CategoriesScreen
import com.bookmark.categories.ui.CategoriesViewModel
import com.bookmark.categories.ui.CategoryDialogState
import com.bookmark.categories.ui.CategoryEditDialog
import com.bookmark.categories.ui.DeleteCategoryDialog
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.Category
import com.bookmark.core.ui.components.BookmarkBottomBar
import com.bookmark.core.ui.components.BottomDestination
import com.bookmark.core.util.LinkActions
import com.bookmark.metadata.FailureCause
import com.bookmark.metadata.userMessage
import com.bookmark.settings.SettingsScreen
import com.bookmark.settings.SettingsViewModel

/** Which sheet, if any, is open over the current screen. */
private sealed interface SheetState {
    data object None : SheetState
    data object AddEdit : SheetState
    data class Context(val bookmark: Bookmark) : SheetState
    data class Detail(val bookmark: Bookmark) : SheetState
}

@Composable
fun BookmarkNavHost() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(TopLevelDestination.HOME) }
    var sheet by remember { mutableStateOf<SheetState>(SheetState.None) }

    val homeViewModel: HomeViewModel = hiltViewModel()
    val addEditViewModel: AddEditViewModel = hiltViewModel()
    val categoriesViewModel: CategoriesViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val addEditState by addEditViewModel.state.collectAsStateWithLifecycle()
    val categoriesState by categoriesViewModel.uiState.collectAsStateWithLifecycle()
    val categoryDialog by categoriesViewModel.dialog.collectAsStateWithLifecycle()
    val categoryDraftOrder by categoriesViewModel.draftOrder.collectAsStateWithLifecycle()
    val categoryError by categoriesViewModel.error.collectAsStateWithLifecycle()
    val categoryUndo by categoriesViewModel.undo.collectAsStateWithLifecycle()
    val bookmarkUndo by homeViewModel.undo.collectAsStateWithLifecycle()
    val preferences by settingsViewModel.preferences.collectAsStateWithLifecycle()

    // A deleted bookmark is held until the Snackbar resolves (spec 14 Q2).
    LaunchedEffect(bookmarkUndo) {
        val pending = bookmarkUndo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Deleted “${pending.bookmark.title.take(30)}”",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            homeViewModel.undoDelete()
        } else {
            homeViewModel.clearUndo()
        }
    }

    LaunchedEffect(categoryUndo) {
        val pending = categoryUndo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Deleted “${pending.category.name}”",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            categoriesViewModel.undoDelete()
        } else {
            categoriesViewModel.clearUndo()
        }
    }

    LaunchedEffect(categoryError) {
        val message = categoryError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        categoriesViewModel.clearError()
    }

    // Saving closes the sheet; the bookmark is already persisted by this point.
    LaunchedEffect(addEditState.savedBookmark) {
        if (addEditState.savedBookmark != null) {
            addEditViewModel.consumeSaved()
            addEditViewModel.reset()
            sheet = SheetState.None
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BookmarkBottomBar(
                destinations = TopLevelDestination.entries.map {
                    BottomDestination(it.label, it.icon)
                },
                selectedIndex = TopLevelDestination.entries.indexOf(selectedTab),
                onSelect = { index -> selectedTab = TopLevelDestination.entries[index] },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                TopLevelDestination.HOME -> HomeScreen(
                    state = homeState,
                    thumbnailFor = homeViewModel::thumbnailFile,
                    onSelectCategory = homeViewModel::selectCategory,
                    onSetViewMode = homeViewModel::setViewMode,
                    onSetSortOrder = homeViewModel::setSortOrder,
                    onOpenBookmark = { LinkActions.open(context, it.url) },
                    onBookmarkLongPress = { sheet = SheetState.Context(it) },
                    onSearch = { /* Search screen arrives in M5 */ },
                    onAdd = {
                        addEditViewModel.startAdd()
                        addEditViewModel.offerClipboard(LinkActions.clipboardUrl(context))
                        sheet = SheetState.AddEdit
                    },
                )

                TopLevelDestination.CATEGORIES -> CategoriesScreen(
                    categories = categoryDraftOrder ?: categoriesState.categories,
                    onSearch = { },
                    onOverflow = { },
                    onCreate = categoriesViewModel::openCreate,
                    onEdit = categoriesViewModel::openEdit,
                    onMove = categoriesViewModel::moveDraft,
                    onMoveCommitted = categoriesViewModel::commitOrder,
                )

                TopLevelDestination.SETTINGS -> SettingsScreen(
                    preferences = preferences,
                    onThemeModeChange = settingsViewModel::setThemeMode,
                    onDynamicColorChange = settingsViewModel::setDynamicColor,
                    onTrueBlackChange = settingsViewModel::setTrueBlack,
                    onFetchPreviewsChange = settingsViewModel::setFetchPreviews,
                    onSearch = { },
                    onOverflow = { },
                )
            }
        }
    }

    when (val current = sheet) {
        SheetState.None -> Unit

        SheetState.AddEdit -> AddEditBookmarkSheet(
            state = addEditState,
            onDismiss = {
                addEditViewModel.reset()
                sheet = SheetState.None
            },
            onUrlChange = addEditViewModel::onUrlChange,
            onTitleChange = addEditViewModel::onTitleChange,
            onDescriptionChange = addEditViewModel::onDescriptionChange,
            onCategoryChange = addEditViewModel::onCategoryChange,
            onCreateCategory = addEditViewModel::createCategory,
            onAcceptClipboard = addEditViewModel::acceptClipboardSuggestion,
            onDismissClipboard = addEditViewModel::dismissClipboardSuggestion,
            onSave = addEditViewModel::save,
        )

        is SheetState.Context -> BookmarkContextSheet(
            bookmark = current.bookmark,
            category = homeState.categoriesById[current.bookmark.categoryId],
            thumbnailFile = homeViewModel.thumbnailFile(current.bookmark),
            onDismiss = { sheet = SheetState.None },
            onEdit = {
                addEditViewModel.startEdit(current.bookmark)
                sheet = SheetState.AddEdit
            },
            onChangeCategory = {
                addEditViewModel.startEdit(current.bookmark)
                sheet = SheetState.AddEdit
            },
            onCopyLink = {
                LinkActions.copy(context, current.bookmark.url)
                sheet = SheetState.None
            },
            onShare = {
                LinkActions.share(context, current.bookmark.url, current.bookmark.title)
                sheet = SheetState.None
            },
            onTogglePin = {
                homeViewModel.togglePin(current.bookmark)
                sheet = SheetState.None
            },
            onDelete = {
                homeViewModel.delete(current.bookmark)
                sheet = SheetState.None
            },
        )

        is SheetState.Detail -> BookmarkDetailSheet(
            bookmark = current.bookmark,
            category = homeState.categoriesById[current.bookmark.categoryId],
            thumbnailFile = homeViewModel.thumbnailFile(current.bookmark),
            // Until M3 records a cause, a hard failure reads as unreachable.
            failureMessage = FailureCause.UNREACHABLE.userMessage(),
            onDismiss = { sheet = SheetState.None },
            onOpen = { LinkActions.open(context, current.bookmark.url) },
            onShare = { LinkActions.share(context, current.bookmark.url, current.bookmark.title) },
            onEdit = {
                addEditViewModel.startEdit(current.bookmark)
                sheet = SheetState.AddEdit
            },
            onTogglePin = { homeViewModel.togglePin(current.bookmark) },
            onRetryFetch = { /* Enqueues a refresh once M3 lands */ },
        )
    }

    addEditState.duplicateOf?.let { existing ->
        DuplicateBookmarkSheet(
            existing = existing,
            category = homeState.categoriesById[existing.categoryId],
            thumbnailFile = homeViewModel.thumbnailFile(existing),
            onDismiss = addEditViewModel::dismissDuplicate,
            onRefreshPreview = addEditViewModel::dismissDuplicate,
            onViewBookmark = {
                addEditViewModel.dismissDuplicate()
                addEditViewModel.reset()
                sheet = SheetState.Detail(existing)
            },
        )
    }

    when (val dialog = categoryDialog) {
        CategoryDialogState.None -> Unit

        CategoryDialogState.Create -> CategoryEditDialog(
            existing = null,
            defaultColorHex = categoriesViewModel.nextSwatch(),
            onDismiss = categoriesViewModel::dismissDialog,
            onConfirm = categoriesViewModel::create,
        )

        is CategoryDialogState.Edit -> CategoryEditDialog(
            existing = dialog.category,
            defaultColorHex = dialog.category.colorHex,
            onDismiss = categoriesViewModel::dismissDialog,
            onConfirm = { name, color, icon ->
                categoriesViewModel.update(dialog.category, name, color, icon)
            },
            onDelete = if (
                dialog.category.id != Category.UNSORTED_ID && !dialog.category.isDefault
            ) {
                { categoriesViewModel.openDelete(dialog.category) }
            } else {
                null
            },
        )

        is CategoryDialogState.Delete -> DeleteCategoryDialog(
            category = dialog.category,
            bookmarkCount = dialog.bookmarkCount,
            otherCategories = categoriesState.categories.filter {
                it.category.id != dialog.category.id
            },
            onDismiss = categoriesViewModel::dismissDialog,
            onConfirm = { strategy -> categoriesViewModel.delete(dialog.category, strategy) },
        )
    }
}
