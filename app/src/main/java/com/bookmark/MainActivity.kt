package com.bookmark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookmark.benchmark.seedBenchmarkBookmarksIfRequested
import com.bookmark.bookmarks.data.BookmarkDao
import com.bookmark.core.model.UserPreferences
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = settingsRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var bookmarkDao: BookmarkDao

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // M6 scroll benchmark only -- a no-op unless this exact extra is
        // present and positive, which only ScrollBenchmark's own
        // startActivityAndWait() ever sends (see BenchmarkSeed.kt).
        val seedCount = intent.getIntExtra(EXTRA_BENCHMARK_SEED_COUNT, 0)
        if (seedCount > 0) {
            runBlocking { seedBenchmarkBookmarksIfRequested(bookmarkDao, seedCount) }
        }
        setContent {
            val viewModel: MainViewModel = viewModel()
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            BookmarkTheme(
                themeMode = preferences.themeMode,
                dynamicColor = preferences.dynamicColor,
                trueBlack = preferences.trueBlack,
            ) {
                // Exposes Modifier.testTag() values as UiAutomator-visible
                // resource-ids (M6 macrobenchmark selectors); harmless for
                // real users -- TalkBack reads contentDescription, not this.
                Box(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    com.bookmark.navigation.BookmarkNavHost()
                }
            }
        }
    }

    companion object {
        const val EXTRA_BENCHMARK_SEED_COUNT = "com.bookmark.EXTRA_BENCHMARK_SEED_COUNT"
    }
}
