package com.bookmark.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.bookmark.bookmarks.data.BookmarkRepository
import com.bookmark.bookmarks.data.SaveResult
import com.bookmark.categories.data.CategoryRepository
import com.bookmark.core.util.LinkActions
import com.bookmark.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The widget's "Save copied link" button. Android only lets a *focused* app
 * read the clipboard, so the button opens this invisible activity, reads the
 * clipboard once it has window focus, saves into the last-used category, shows
 * a Toast and finishes.
 */
@AndroidEntryPoint
class WidgetSaveActivity : ComponentActivity() {

    @Inject lateinit var bookmarkRepository: BookmarkRepository
    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true

        val url = LinkActions.clipboardUrl(this)
        if (url == null) {
            Toast.makeText(this, "Copy a link first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        lifecycleScope.launch {
            val lastUsed = settingsRepository.preferences.first().lastUsedCategoryId
            val categoryId = lastUsed?.takeIf { categoryRepository.findById(it) != null }
                ?: categoryRepository.defaultCategoryId()
            val message = when (bookmarkRepository.save(rawUrl = url, categoryId = categoryId)) {
                is SaveResult.Saved -> "Saved"
                is SaveResult.Duplicate -> "Already saved"
            }
            Toast.makeText(this@WidgetSaveActivity, message, Toast.LENGTH_SHORT).show()
            BookmarkWidget.update(this@WidgetSaveActivity)
            finish()
        }
    }
}
