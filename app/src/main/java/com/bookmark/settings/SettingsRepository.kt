package com.bookmark.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bookmark.core.model.SortOrder
import com.bookmark.core.model.ThemeMode
import com.bookmark.core.model.UserPreferences
import com.bookmark.core.model.ViewMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val trueBlack = booleanPreferencesKey("true_black")
        val viewMode = stringPreferencesKey("view_mode")
        val sortOrder = stringPreferencesKey("sort_order")
        val lastUsedCategory = stringPreferencesKey("last_used_category")
        val fetchPreviews = booleanPreferencesKey("fetch_previews")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.themeMode]?.toEnum(ThemeMode.entries) ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[Keys.dynamicColor] ?: false,
            trueBlack = prefs[Keys.trueBlack] ?: false,
            viewMode = prefs[Keys.viewMode]?.toEnum(ViewMode.entries) ?: ViewMode.GRID,
            sortOrder = prefs[Keys.sortOrder]?.toEnum(SortOrder.entries) ?: SortOrder.NEWEST,
            lastUsedCategoryId = prefs[Keys.lastUsedCategory],
            fetchPreviewsAutomatically = prefs[Keys.fetchPreviews] ?: true,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = put(Keys.themeMode, mode.name)
    suspend fun setDynamicColor(enabled: Boolean) = put(Keys.dynamicColor, enabled)
    suspend fun setTrueBlack(enabled: Boolean) = put(Keys.trueBlack, enabled)
    suspend fun setViewMode(mode: ViewMode) = put(Keys.viewMode, mode.name)
    suspend fun setSortOrder(order: SortOrder) = put(Keys.sortOrder, order.name)
    suspend fun setLastUsedCategory(id: String) = put(Keys.lastUsedCategory, id)
    suspend fun setFetchPreviews(enabled: Boolean) = put(Keys.fetchPreviews, enabled)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    private fun <T : Enum<T>> String.toEnum(values: List<T>): T? =
        values.firstOrNull { it.name == this }
}
