package com.bookmark.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookmark.MainActivity
import com.bookmark.bookmarks.data.SaveResult
import com.bookmark.core.model.UserPreferences
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The share target (spec 6). Transparent, no-history, single-task: this is the
 * hot path and gets its own surface rather than routing through MainActivity.
 *
 * Extraction happens synchronously in [onCreate] so the sheet can render from
 * the URL alone; categories and metadata stream in afterwards (spec 6.3).
 */
@AndroidEntryPoint
class QuickSaveActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val extracted = extractFromIntent(intent)
        val presetCategoryId = intent.getStringExtra(DirectShareShortcuts.EXTRA_CATEGORY_ID)

        // A Direct Share shortcut names its category up front, so there is
        // nothing to ask: save straight in and confirm (spec 6.3).
        if (presetCategoryId != null && extracted.hasUrl) {
            saveDirectly(extracted, presetCategoryId)
            return
        }

        setContent {
            val viewModel: QuickSaveViewModel = viewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val preferences by settingsRepository.preferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())
            val haptics = LocalHapticFeedback.current

            LaunchedEffect(Unit) { viewModel.start(extracted, presetCategoryId) }

            LaunchedEffect(state.savedBookmark) {
                if (state.savedBookmark != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    Toast.makeText(this@QuickSaveActivity, "Saved", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

            BookmarkTheme(
                themeMode = preferences.themeMode,
                dynamicColor = preferences.dynamicColor,
                trueBlack = preferences.trueBlack,
            ) {
                QuickSaveSheet(
                    state = state,
                    onDismiss = ::finish,
                    onUrlChange = viewModel::onUrlChange,
                    onTitleChange = viewModel::onTitleChange,
                    onCategoryChange = viewModel::onCategoryChange,
                    onUseOtherUrl = viewModel::useOtherUrl,
                    onCreateCategory = viewModel::createCategory,
                    onSave = viewModel::save,
                    onMoreOptions = {
                        // Hand off to the full sheet in the main app.
                        startActivity(
                            Intent(this@QuickSaveActivity, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                        finish()
                    },
                )

                state.duplicateOf?.let { existing ->
                    com.bookmark.bookmarks.detail.DuplicateBookmarkSheet(
                        existing = existing,
                        category = null,
                        thumbnailPath = null,
                        onDismiss = viewModel::dismissDuplicate,
                        onRefreshPreview = viewModel::dismissDuplicate,
                        onViewBookmark = {
                            startActivity(
                                Intent(this@QuickSaveActivity, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                            finish()
                        },
                    )
                }
            }
        }
    }

    private fun saveDirectly(extracted: UrlExtractor.ExtractedShare, categoryId: String) {
        lifecycleScope.launch {
            val viewModel: QuickSaveViewModel by viewModels()
            val (result, categoryName) = viewModel.saveDirect(extracted, categoryId)
            val message = when (result) {
                is SaveResult.Saved -> "Saved to ${categoryName ?: "Bookmarks"}"
                is SaveResult.Duplicate -> "Already saved"
            }
            Toast.makeText(this@QuickSaveActivity, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * Handles all three registered entry points: a shared string, a view intent
     * on an http(s) URL, and a URL selected inside any text.
     */
    private fun extractFromIntent(intent: Intent): UrlExtractor.ExtractedShare = when (intent.action) {
        Intent.ACTION_SEND -> UrlExtractor.extract(
            text = intent.getStringExtra(Intent.EXTRA_TEXT),
            subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
        )

        Intent.ACTION_VIEW -> UrlExtractor.extract(text = intent.dataString)

        Intent.ACTION_PROCESS_TEXT -> UrlExtractor.extract(
            text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString(),
        )

        else -> UrlExtractor.extract(text = null)
    }
}
