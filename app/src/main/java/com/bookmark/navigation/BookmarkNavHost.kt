package com.bookmark.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bookmark.backup.ImportPreviewSheet
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
import com.bookmark.core.model.MetadataState
import com.bookmark.core.ui.components.BookmarkBottomBar
import com.bookmark.core.ui.components.BottomDestination
import com.bookmark.core.ui.motion.rememberReducedMotionEnabled
import com.bookmark.core.util.LinkActions
import com.bookmark.metadata.FailureCause
import com.bookmark.metadata.userMessage
import com.bookmark.search.SearchScreen
import com.bookmark.search.SearchViewModel
import com.bookmark.settings.ImportSheetState
import com.bookmark.settings.SettingsScreen
import com.bookmark.settings.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which sheet, if any, is open over the current screen. */
private sealed interface SheetState {
    data object None : SheetState
    data object AddEdit : SheetState
    data class Context(val bookmark: Bookmark) : SheetState
    data class Detail(val bookmark: Bookmark) : SheetState
}

/** [TopLevelDestination] <-> the route object driving [NavHost]. */
private fun routeFor(destination: TopLevelDestination): Any = when (destination) {
    TopLevelDestination.HOME -> HomeRoute
    TopLevelDestination.CATEGORIES -> CategoriesRoute
    TopLevelDestination.SETTINGS -> SettingsRoute
}

/** The suggested filename for "Export backup", e.g. `bookmarks-2026-09-14.zip`. */
private fun backupDateStamp(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BookmarkNavHost() {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    // Computed once here (not per-card) since it registers a ContentObserver;
    // threaded down to both the grid and the detail sheet's shared element.
    val reducedMotion = rememberReducedMotionEnabled()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    var sheet by remember { mutableStateOf<SheetState>(SheetState.None) }

    val homeViewModel: HomeViewModel = hiltViewModel()
    val addEditViewModel: AddEditViewModel = hiltViewModel()
    val categoriesViewModel: CategoriesViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val backStackEntry by navController.currentBackStackEntryAsState()
    // Route-checking by KClass (NavDestination.hasRoute) is the more idiomatic
    // API, but the destination's `route` string is already the route class's
    // qualified name (how composable<T>() registers it), and comparing that
    // directly sidesteps an overload-resolution ambiguity between the
    // KClass-based extension and the older String-based member function.
    val currentRoute = backStackEntry?.destination?.route
    val currentTopLevel = when (currentRoute) {
        CategoriesRoute::class.qualifiedName -> TopLevelDestination.CATEGORIES
        SettingsRoute::class.qualifiedName -> TopLevelDestination.SETTINGS
        else -> TopLevelDestination.HOME
    }
    // Search is a full-screen destination pushed on top of Home, not one of
    // the three tabs -- the bottom bar has nothing sensible to highlight
    // while it's open, so it hides instead (matches the design's full-screen
    // search mock, which shows no bottom bar).
    val onSearchRoute = currentRoute == SearchRoute::class.qualifiedName

    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val addEditState by addEditViewModel.state.collectAsStateWithLifecycle()
    val categoriesState by categoriesViewModel.uiState.collectAsStateWithLifecycle()
    val categoryDialog by categoriesViewModel.dialog.collectAsStateWithLifecycle()
    val categoryDraftOrder by categoriesViewModel.draftOrder.collectAsStateWithLifecycle()
    val categoryError by categoriesViewModel.error.collectAsStateWithLifecycle()
    val categoryUndo by categoriesViewModel.undo.collectAsStateWithLifecycle()
    val bookmarkUndo by homeViewModel.undo.collectAsStateWithLifecycle()
    val preferences by settingsViewModel.preferences.collectAsStateWithLifecycle()
    val settingsSummary by settingsViewModel.summary.collectAsStateWithLifecycle()
    val importSheetState by settingsViewModel.importSheetState.collectAsStateWithLifecycle()
    val backupMessage by settingsViewModel.message.collectAsStateWithLifecycle()

    // SAF pickers: the launcher must live on an Activity-scoped Compose host,
    // which a plain ViewModel cannot be -- it hands the resulting Uri straight
    // to the ViewModel once the user has actually picked a location/file.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(settingsViewModel::onExportUriPicked) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(settingsViewModel::onImportUriPicked) }

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

    LaunchedEffect(backupMessage) {
        val message = backupMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        settingsViewModel.clearMessage()
    }

    // Saving closes the sheet; the bookmark is already persisted by this point.
    LaunchedEffect(addEditState.savedBookmark) {
        if (addEditState.savedBookmark != null) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            addEditViewModel.consumeSaved()
            addEditViewModel.reset()
            sheet = SheetState.None
        }
    }

    // The grid card and the detail sheet's hero image are both composed
    // simultaneously (the sheet overlays the grid, it doesn't replace it),
    // which is exactly what SharedTransitionLayout needs -- sheets were kept
    // sheet-state-driven rather than real nav destinations specifically to
    // keep this available (see HANDOVER.md). Wraps everything below so the
    // scope is available to both the grid (inside NavHost) and the sheets
    // (in the `when` blocks after it).
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        val sharedTransitionScope = this
        val openDetailBookmarkId = (sheet as? SheetState.Detail)?.bookmark?.id

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!onSearchRoute) {
                BookmarkBottomBar(
                    destinations = TopLevelDestination.entries.map {
                        BottomDestination(it.label, it.icon)
                    },
                    selectedIndex = TopLevelDestination.entries.indexOf(currentTopLevel),
                    onSelect = { index ->
                        val target = routeFor(TopLevelDestination.entries[index])
                        navController.navigate(target) {
                            // Keeps the back stack flat -- switching tabs
                            // never grows a Home -> Categories -> Settings
                            // chain the system back button would have to
                            // unwind one tap at a time.
                            popUpTo(HomeRoute) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // No animated transition between destinations (tabs, Search) --
            // the switch is instant rather than a slide/fade.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    state = homeState,
                    sharedTransitionScope = sharedTransitionScope,
                    openDetailBookmarkId = openDetailBookmarkId,
                    reducedMotion = reducedMotion,
                    thumbnailFor = { homeViewModel.thumbnailFile(it)?.absolutePath },
                    onSelectCategory = homeViewModel::selectCategory,
                    onSetViewMode = homeViewModel::setViewMode,
                    onSetSortOrder = homeViewModel::setSortOrder,
                    onOpenBookmark = {
                        // A FAILED bookmark has nothing reliable to open -- show
                        // the cause and a retry affordance instead (spec 8.1).
                        if (it.metadataState == MetadataState.FAILED) {
                            sheet = SheetState.Detail(it)
                        } else {
                            LinkActions.open(context, it.url)
                        }
                    },
                    onBookmarkLongPress = { sheet = SheetState.Context(it) },
                    onSearch = { navController.navigate(SearchRoute) },
                    onAdd = {
                        addEditViewModel.startAdd()
                        addEditViewModel.offerClipboard(LinkActions.clipboardUrl(context))
                        sheet = SheetState.AddEdit
                    },
                )
            }

            composable<CategoriesRoute> {
                CategoriesScreen(
                    categories = categoryDraftOrder ?: categoriesState.categories,
                    onSearch = { },
                    onOverflow = { },
                    onCreate = categoriesViewModel::openCreate,
                    onEdit = categoriesViewModel::openEdit,
                    onMove = categoriesViewModel::moveDraft,
                    onMoveCommitted = categoriesViewModel::commitOrder,
                )
            }

            composable<SettingsRoute> {
                SettingsScreen(
                    preferences = preferences,
                    summary = settingsSummary,
                    onThemeModeChange = settingsViewModel::setThemeMode,
                    onDynamicColorChange = settingsViewModel::setDynamicColor,
                    onTrueBlackChange = settingsViewModel::setTrueBlack,
                    onFetchPreviewsChange = settingsViewModel::setFetchPreviews,
                    onRefreshAll = settingsViewModel::refreshAll,
                    onClearThumbnails = settingsViewModel::clearThumbnails,
                    onExportBackup = { exportLauncher.launch("bookmarks-${backupDateStamp()}.zip") },
                    onImportBackup = { importLauncher.launch(arrayOf("application/zip")) },
                    onSearch = { },
                    onOverflow = { },
                )
            }

            composable<SearchRoute> {
                // Scoped to this back-stack entry (not hoisted like the four
                // tab view models above) so it resets -- a fresh, empty query
                // -- every time Search is reopened from Home.
                val searchViewModel: SearchViewModel = hiltViewModel()
                val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
                SearchScreen(
                    state = searchState,
                    thumbnailFor = { searchViewModel.thumbnailFile(it)?.absolutePath },
                    onQueryChange = searchViewModel::onQueryChange,
                    onClearQuery = searchViewModel::clearQuery,
                    onSelectCategory = searchViewModel::selectCategory,
                    onBack = { navController.popBackStack() },
                    onOpenBookmark = {
                        if (it.metadataState == MetadataState.FAILED) {
                            sheet = SheetState.Detail(it)
                        } else {
                            LinkActions.open(context, it.url)
                        }
                    },
                    onBookmarkLongPress = { sheet = SheetState.Context(it) },
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
            onSelectThumbnailCandidate = addEditViewModel::selectThumbnailCandidate,
            onPickLocalThumbnail = addEditViewModel::pickLocalThumbnail,
            onRemoveThumbnail = addEditViewModel::removeThumbnail,
            onRetryLivePreview = addEditViewModel::retryLivePreview,
            onSave = addEditViewModel::save,
        )

        is SheetState.Context -> BookmarkContextSheet(
            bookmark = current.bookmark,
            category = homeState.categoriesById[current.bookmark.categoryId],
            thumbnailPath = homeViewModel.thumbnailFile(current.bookmark)?.absolutePath,
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
            onRetryFetch = if (current.bookmark.metadataState == MetadataState.FALLBACK) {
                {
                    homeViewModel.retryFetch(current.bookmark)
                    sheet = SheetState.None
                }
            } else {
                null
            },
            onDelete = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                homeViewModel.delete(current.bookmark)
                sheet = SheetState.None
            },
        )

        is SheetState.Detail -> BookmarkDetailSheet(
            bookmark = current.bookmark,
            category = homeState.categoriesById[current.bookmark.categoryId],
            thumbnailPath = homeViewModel.thumbnailFile(current.bookmark)?.absolutePath,
            sharedTransitionScope = sharedTransitionScope,
            reducedMotion = reducedMotion,
            failureMessage = current.bookmark.failureCause
                ?.let { runCatching { FailureCause.valueOf(it) }.getOrNull() }
                .userMessage(),
            onDismiss = { sheet = SheetState.None },
            onOpen = { LinkActions.open(context, current.bookmark.url) },
            onShare = { LinkActions.share(context, current.bookmark.url, current.bookmark.title) },
            onEdit = {
                addEditViewModel.startEdit(current.bookmark)
                sheet = SheetState.AddEdit
            },
            onTogglePin = { homeViewModel.togglePin(current.bookmark) },
            onRetryFetch = { homeViewModel.retryFetch(current.bookmark) },
        )
    }

    addEditState.duplicateOf?.let { existing ->
        DuplicateBookmarkSheet(
            existing = existing,
            category = homeState.categoriesById[existing.categoryId],
            thumbnailPath = homeViewModel.thumbnailFile(existing)?.absolutePath,
            onDismiss = addEditViewModel::dismissDuplicate,
            onRefreshPreview = addEditViewModel::refreshDuplicatePreview,
            onViewBookmark = {
                addEditViewModel.dismissDuplicate()
                addEditViewModel.reset()
                sheet = SheetState.Detail(existing)
            },
        )
    }

    (importSheetState as? ImportSheetState.Preview)?.let { preview ->
        ImportPreviewSheet(
            preview = preview.preview,
            selectedMode = preview.mode,
            onModeChange = settingsViewModel::setImportMode,
            onDismiss = settingsViewModel::dismissImportSheet,
            onImport = settingsViewModel::confirmImport,
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
}
