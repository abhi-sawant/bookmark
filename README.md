# Bookmark – Local-First Link Saving for Android

A fast, privacy-first Android app for saving and organizing bookmarks. Share a URL from any app, and Bookmark instantly stores it locally with automatically fetched metadata (title, description, thumbnail). Everything lives on your device—there's no account, no sync, and no telemetry.

## Overview

Bookmark solves a simple problem elegantly: you see a link you want to keep, you share it to Bookmark, and seconds later it's organized, searchable, and ready to view offline. Built with modern Android best practices (Kotlin, Jetpack Compose, Material 3), it's designed to be fast, accessible, and respectful of your privacy.

### Key Principles

1. **Saving never blocks** — bookmarks are persisted instantly; metadata streams in asynchronously
2. **Graceful degradation** — every field has a fallback; no "couldn't load" errors
3. **Your edits always win** — manually-edited fields are protected from automatic overwrites
4. **Offline first** — full functionality without network; metadata fetching is the only network-dependent operation

## Features

### Core
- **Instant sharing** — receive URLs from Android's share sheet (< 300ms to visible UI)
- **Quick-save** — save with auto-detected title, description, and thumbnail in a single tap
- **Organization** — create custom categories with colors and icons; reorder by dragging
- **Smart search** — full-text search across titles, descriptions, URLs, and site names (< 50ms results)
- **Flexible viewing** — toggle between 2-column staggered grid (image-forward) and compact list (content-forward)
- **Sorting & filtering** — sort by date (newest/oldest), title, or category; filter by category in real time
- **Link context** — view and edit title, description, thumbnails; change category; pin favorites

### Metadata & Fallbacks
- **Automatic preview fetching** — extracts Open Graph, Twitter, and JSON-LD metadata directly from target sites
- **Smart thumbnail selection** — downloads and caches the best candidate image; falls back to generated tiles
- **Generated fallback tiles** — deterministic color + first-letter monogram for pages without images
- **Retry system** — automatic exponential-backoff retries (3 max) with manual retry available
- **Content-aware handling** — special cases for YouTube (thumbnail from i.ytimg), direct images, PDFs

### Data Management
- **Export/import** — full backup as ZIP with bookmarks, categories, and thumbnails
- **Auto Backup** — Android's Auto Backup integration for database recovery
- **Offline browsing** — view all bookmarks, search, and edit categories without network
- **Storage management** — see cache size; clear thumbnails (re-fetchable)

### Privacy & Accessibility
- **Zero tracking** — no analytics, no crash reporting, no cloud backend
- **App-private storage** — all data in device-private storage, never accessible to other apps
- **Minimal permissions** — only `INTERNET` (optional, can be toggled off for 100% network silence)
- **Material 3 themes** — automatic dark mode, dynamic color on Android 12+, true-black OLED option
- **Accessible** — 48dp touch targets, TalkBack support, text scaling to 200%, proper contrast ratios

## Technology Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin 2.x |
| **UI Framework** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM + unidirectional data flow (StateFlow) |
| **Dependency Injection** | Hilt |
| **Persistence** | Room (SQLite) + FTS4 (full-text search) |
| **Preferences** | DataStore (Proto) |
| **Networking** | OkHttp |
| **HTML Parsing** | Jsoup |
| **Image Loading** | Coil 3 (with memory + disk cache) |
| **Background Work** | WorkManager |
| **Navigation** | Navigation Compose (type-safe routes) |
| **Min/Target SDK** | 33 / 37 |

## Getting Started

### Prerequisites
- Android Studio 2024.1 or later
- Kotlin 2.x
- Gradle 8.4+
- JDK 17+

### Building

1. **Clone and open in Android Studio**
   ```bash
   git clone <repository-url>
   cd Bookmark
   ```

2. **Configure signing (release builds)**
   - Create `keystore.properties` in the project root:
     ```properties
     storeFile=keystore/release.keystore
     storePassword=<your-keystore-password>
     keyAlias=<your-key-alias>
     keyPassword=<your-key-password>
     ```

3. **Build**
   ```bash
   # Debug
   ./gradlew app:assembleDebug

   # Release
   ./gradlew app:assembleRelease
   ```

4. **Run**
   ```bash
   ./gradlew app:installDebug
   ```

## Project Structure

```
app/
  src/
    main/
      java/com/bookmark/
        bookmarks/           # Bookmark CRUD, list, grid, detail views
        categories/          # Category management, delete flow
        metadata/            # Metadata fetch engine (testable, no Android UI deps)
        share/               # Quick-save sheet, share target logic
        search/              # FTS-backed full-text search
        settings/            # Theme, export/import, storage management
        core/                # Database, repositories, theme (core/ui/theme), shared components
        MainActivity.kt      # Main entry point
      res/                   # Resources, icons, strings
    test/                    # Unit tests
    androidTest/             # Integration tests
macrobenchmark/              # Performance benchmarks
```

**Package-by-feature** structure keeps code co-located and reduces coupling.

## Architecture

### Data Flow
- **Single state per screen** — each UI layer manages one `StateFlow<ScreenState>`
- **Unidirectional** — UI events → ViewModel → Repository → Database
- **Reactive** — UI recomposes on `StateFlow` changes

### Persistence
- **Room DAOs** for CRUD operations
- **Room FTS4** virtual table for fast substring search
- **DataStore** for user preferences (theme, default view, category order)

### Background Work
- **WorkManager** for metadata fetching with network constraints and exponential backoff
- **Unique work per bookmark** — retries are idempotent and self-contained
- **Max 3 automatic retries** — manual retry always available in the UI

### Metadata Engine
- Self-contained module with no Android UI dependencies
- Testable with fixture-based test suite (real-world HTML heads)
- **URL normalization** — strips tracking params, fragments, default ports
- **Smart parsing** — OG tags → Twitter card → JSON-LD → fallbacks
- **Image download pipeline** — size validation, aspect ratio check, WebP encoding at quality 80

## Performance Targets

All measured on mid-range hardware (Pixel 6a class, release build):

| Metric | Target |
|--------|--------|
| Cold start → first frame | < 500ms |
| Share sheet tap → quick-save visible | < 300ms |
| 500-item list scroll | Zero jank frames |
| Search keystroke → results | < 50ms |
| Frame budget | 16.6ms (60Hz) |
| APK size | < 8MB |

### Optimization Tactics
- **Baseline Profile** (20–30% cold-start improvement)
- **Lazy layouts** with stable `key` and `contentType`
- **Paging 3** from Room (for libraries > 1000 items)
- **Coil cache** — 25% memory share, 250MB disk
- **All DB access off main thread** — suspend functions & Flow
- **Compose stability** — `@Stable` annotations, immutable state, no-op recompositions
- **StrictMode** in debug builds (disk + network on main thread → `penaltyDeath`)

## Design

### Visual Language
- **Slate** design system: flat cool-neutral surfaces, hairline borders, one green accent (see [DESIGN.md](DESIGN.md))
- **Hanken Grotesk** throughout, lowercase screen titles and section labels
- **Material 3** under the hood, themed through `core/ui/theme`
- **Dynamic color** (Android 12+) re-tints the accent from the wallpaper; Slate palette otherwise
- **Edge-to-edge** UI with proper inset handling
- **Predictive back** with progress animation on sheets
- **Shared-element transitions** between grid and detail
- **Haptics** on save, delete, and drag-reorder
- **Light, dark and true-black** themes sharing one palette
- **Content-forward cards** — image dominant, title/description 2-line clamp

### Accessibility
- Minimum 48dp touch targets
- Content descriptions on all images and icon buttons
- TalkBack traversal verified on Home and quick-save sheet
- Text scaling to 200% without clipping
- Color never the sole carrier of meaning

## Privacy

All data is stored in app-private storage. The app makes **zero outbound requests** except for one-time metadata fetches of URLs the user explicitly saved.

- **No backend** — no account, no sync, no data collection
- **No third-party services** — no favicon providers, no crash reporting (in v1)
- **No analytics** — zero telemetry
- **Direct site requests** — metadata fetches go directly to the target site (that site sees your IP and UA)
- **Privacy toggle** — Settings → "Fetch link previews automatically" (default on); turn off for 100% network silence

## Screens & Flows

### Home
- Single-line header: lowercase title, live count, search
- Category filter chips (All + live counts)
- Sort button and a list/grid toggle in one control row
- 4-way sort (date, title, category)
- Long-press for context actions (edit, change category, copy, share, pin, delete)
- Empty state with share hint

### Quick-Save Sheet
- Prefilled URL (from share intent, or suggestion chip if clipboard holds a new URL)
- Live preview card showing how the bookmark will appear
- Title, description, category fields with smart defaults
- One-tap "Save" or expand to "More options" for full editing
- Metadata streams in while the sheet is open

### Add / Edit Bookmark
- Full editing: URL, title (200 char), description (500 char)
- Thumbnail picker with retry and "choose another image from page"
- Category selection with inline "＋ New category"
- Save always enabled (never blocked by fetch state)

### Search
- Full-text search with term highlighting
- Filter by category
- Results < 50ms (FTS4-backed)

### Categories
- Drag-to-reorder list
- Edit: name, color, optional icon
- Delete flow: reassign bookmarks or delete all
- Bookmark count badges

### Settings
- Theme (System/Light/Dark) + dynamic color toggle
- Default view preference (grid/list)
- Default save category
- Export/import backmarks as ZIP
- Refresh all metadata (batched, 4 concurrent, 1s inter-host delay)
- Storage management (cache size, clear thumbnails)
- About / licenses

## Development

### Running Tests
```bash
# Unit tests
./gradlew app:testDebugUnitTest

# Integration tests
./gradlew app:connectedAndroidTest
```

### Compose Metrics
To generate recomposition metrics:
```bash
./gradlew app:assembleRelease -PcomposeCompilerReports=true
# Reports in: build/compose_metrics/ and build/compose_reports/
```

### Baseline Profile
```bash
./gradlew baselineProfileGenerateBaselineProfile
```

## Known Limitations & Out of Scope (v1)

- **No sync** — bookmarks live on one device
- **No tags** — only categories
- **No nested categories**
- **No full-article archiving** / read-later text extraction
- **No browser extension**
- **No JavaScript rendering** for SPAs
- **No widgets** / Wear OS
- **No iOS**

## Future Roadmap

- **Multi-device sync** — CRDT-based or Firebase Realtime DB
- **Tags** — many-to-many bookmark-to-tag relationship
- **Trash bin** — soft delete with 30-day auto-sweep
- **Batch operations** — multi-select, bulk move/delete
- **Notes on bookmarks** — per-bookmark user annotations
- **Widgets** — at-a-glance pinned bookmarks
- **Wear OS** — quick-save from smartwatch

## Contributing

This is a personal project, but issues and suggestions are welcome. Please use the issue tracker for bugs, feature requests, and design feedback.

## License

[License TBA]

## Acknowledgments

- **Material Design 3** for the design system
- **Jetpack Compose** for the modern UI toolkit
- **Room** for local-first persistence
- **Jsoup** for HTML parsing
- **OkHttp** for robust networking
