package com.bookmark.share

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.bookmark.R
import com.bookmark.categories.data.CategoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes the most-used categories to the system share sheet (spec 6.3), so
 * "Share -> Bookmarks: Reading" saves straight into that category with a
 * confirmation Snackbar and no sheet at all.
 */
@Singleton
class DirectShareShortcuts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val categoryRepository: CategoryRepository,
) {

    suspend fun publish() {
        val categories = runCatching { categoryRepository.mostUsed(MAX_TARGETS) }
            .getOrDefault(emptyList())
        if (categories.isEmpty()) return

        val shortcuts = categories.map { withCount ->
            val category = withCount.category
            ShortcutInfoCompat.Builder(context, "category_${category.id}")
                .setShortLabel(category.name)
                .setLongLabel(context.getString(R.string.share_into_category, category.name))
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setCategories(setOf(SHARE_CATEGORY))
                .setLongLived(true)
                .setIntent(
                    Intent(context, QuickSaveActivity::class.java).apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(EXTRA_CATEGORY_ID, category.id)
                    },
                )
                .build()
        }

        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    companion object {
        const val SHARE_CATEGORY = "com.bookmark.share.CATEGORY_SAVE_TO_BOOKMARK"
        const val EXTRA_CATEGORY_ID = "com.bookmark.share.EXTRA_CATEGORY_ID"
        private const val MAX_TARGETS = 4
    }
}
