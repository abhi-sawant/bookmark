# Bookmarks — handover

**State:** M0–M2 complete. M3–M7 not started.
**Date:** 13 September 2026

This is the working document for picking the project up. It records what M0–M2
put in place, the seams M3+ extends, and the decisions taken along the way that
are not obvious from the code.

---

## 1. What exists

| Milestone | Status |
|---|---|
| M0 — skeleton, theme, navigation, Room schema + DAOs, seeded default category | Done |
| M1 — bookmark CRUD, Home list/grid, category CRUD + delete-reassign | Done |
| M2 — share target, URL extraction, quick-save sheet, Direct Share shortcuts | Done |
| M3 — metadata engine | **Stubbed** — interface and result types only |
| M4 — fallback system | **Partially done** — title chain, monogram tiles and the manual-field bitmask are in; the state machine has no engine driving it yet |
| M5 — search, sort/filter, settings, export/import | Sort and filter done; FTS table exists but no search UI; Settings is a shell |
| M6 — performance pass | Not started |
| M7 — polish | Not started |

### Two sources of truth

- [`bookmark-app-spec.md`](../bookmark-app-spec.md) — behaviour.
- Claude Design project `b1f3e50f`, file `Bookmarks App - 2a Screens.dc.html` — visuals, style direction **2a**.

**Where they disagree, the design wins on layout and the spec wins on behaviour.**
The design states this in its own note. Two concrete overrides, both already
implemented:

- A single-line 60dp header, not the spec's large collapsing top app bar.
- A three-destination bottom bar (Home · Categories · Settings), not FAB-only
  navigation. The FAB sits above the bar on Home.

The sibling file `Bookmarks Home - Styles.dc.html` is a superseded exploration
of directions 1a/1b/1c/2a/2c — do not implement from it. `android-frame.jsx` is
a canvas-only device bezel and `support.js` is the Claude Design template
runtime; neither has anything to port.

---

## 2. Toolchain — read this before touching the build

The dependency versions here are **not freely upgradable**. Three constraints
interlock, and the comments in `settings.gradle.kts` and
`gradle/libs.versions.toml` record them at the point of use.

**AGP 9 provides Kotlin itself.** The `org.jetbrains.kotlin.android` plugin is
rejected — AGP 9.4.0 has built-in Kotlin support, enabled by default via
`android.builtInKotlin`. It bundles **KGP 2.2.10**, and that is the Kotlin
version the app compiles with.

**The built-in Kotlin version cannot simply be raised.** Putting KGP 2.3.20 on
the settings buildscript classpath does move it, but KGP 2.3.x references
`com.android.build.gradle.api.BaseVariant`, which AGP 9 removed, and the build
fails at plugin-apply time. This was tried and reverted.

**That caps the libraries.** Kotlin 2.2.10's compiler reads class metadata up to
version 2.3.0, so any dependency built with Kotlin 2.4 is unusable. This bit
exactly once: **Coil is pinned to 3.4.0** because 3.5.0+ ships Kotlin 2.4
metadata. `./gradlew :app:dependencyInsight --dependency kotlin-stdlib
--configuration debugCompileClasspath` is how to find the offender next time —
anything resolving `kotlin-stdlib` above 2.3.x is a problem.

**KSP is pinned to `2.2.10-2.0.2`**, which must match the Kotlin version exactly.
Room and Hilt both go through KSP.

Two further build notes:

- `android.disallowKotlinSourceSets=false` in `gradle.properties` is required:
  KSP 2.0.x registers its generated sources through the `kotlin.sourceSets` DSL,
  which built-in Kotlin rejects by default. Remove it once KSP registers sources
  via `android.sourceSets`.
- AGP 9 removed `isMinifyEnabled` and `proguardFiles`. Release shrinking is
  `buildTypes { release { optimization { enable = true } } }` and keep rules live
  in `app/src/main/keepRules/`. Baseline profiles moved to
  `buildType.baselineProfile { }` — relevant to M6.

### Known compiler bug

Kotlin 2.2.10's IR const evaluator crashes on `UInt` conversions in
const-evaluable positions (`InterpreterMethodNotFoundError: Unknown function:
toUInt(kotlin.Int)`). `DomainColor` hits this and uses signed `Int` FNV-1a
instead, which wraps identically. Avoid `UInt` arithmetic until Kotlin moves on.

---

## 3. Schema decisions worth knowing

Schema version 1, exported to `app/schemas/` — **commit every exported schema**;
migrations are reviewed against them.

### `Unsorted` has a fixed id, and that is deliberate

`BookmarkEntity.categoryId` declares `ON DELETE SET DEFAULT`. SQLite can only
`SET DEFAULT` to a literal on the column, but the spec's default category is a
runtime UUID — those are incompatible. The resolution: the seeded fallback
category has the **constant** id `"unsorted"` (`Category.UNSORTED_ID`), so the
DDL is legal:

```sql
`categoryId` TEXT NOT NULL DEFAULT 'unsorted',
FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON DELETE SET DEFAULT
```

This row can never be deleted. `CategoryRepository.delete` throws
`CategoryException(CannotDeleteFallback)` for it.

### `isDefault` means something different from the fallback

Spec §4.2 ("exactly one row true", seeded on Unsorted) and §5.4 ("the user must
first mark another as default") contradict each other if `isDefault` is also the
FK fallback. They were split:

- **`Unsorted`** (fixed id) is the permanent *fallback*. Undeletable.
- **`isDefault`** marks the *default category for new saves*, and the user is
  free to move it. This is what Settings §5.6's "default category for shares"
  needs, and what the design's `Default` badge shows.

### The FK is a safety net, not the mechanism

`CategoryRepository.delete` always reassigns explicitly and transactionally,
according to the strategy the user picked in the dialog. `ON DELETE SET DEFAULT`
only catches writes that bypass the repository. There is an instrumented test
for exactly that path (`theForeignKeyFallbackCatchesADeleteThatSkipsTheRepository`).

### FTS4 exists already

`bookmarks_fts` is content-backed on `bookmarks` and created in schema v1, even
though search lands in M5 — so no migration is needed later. Room generates the
sync triggers; `DatabaseTest` verifies insert and delete stay in sync.
`BookmarkDao.search` and `BookmarkRepository.search` are written and tested;
M5 only needs the screen.

---

## 4. The seams M3+ extends

### Metadata engine → `metadata/MetadataFetcher.kt`

```kotlin
interface MetadataFetcher {
    suspend fun fetch(url: String): MetadataResult
}
```

`NoOpMetadataFetcher` returns `MetadataResult.Pending`, bound in
`MetadataModule`. **M3 swaps that one `@Binds` and no screen changes.** Every
card currently falls through the title chain and the monogram tile — which is
precisely the fallback state the design draws, so the UI is already correct.

Already defined and ready to use: `PageMetadata`, `MetadataResult`
(`Success`/`Partial`/`Fallback`/`Failed`/`Pending` with a `state` mapping),
`FailureCause`, and `FailureCause.userMessage()` carrying the spec §8.6 strings.

### What M3 must honour

- **`manualFields`** — the bitmask in `core/model/Models.kt` (`TITLE=1`,
  `DESCRIPTION=2`, `THUMBNAIL=4`). Any bit set means the user edited that field
  and the worker must skip it. The add/edit and quick-save sheets already set
  these bits as the user types.
- **The thumbnail contract** — `BookmarkRepository.thumbnailDir()` is
  `filesDir/thumbnails/`, files are named `{bookmarkId}.webp`, and
  `thumbnailPath` stores the relative name.
  `BookmarkRepository.sweepOrphanThumbnails()` already runs on app start and
  `delete()` already removes the file with the row.
- **The privacy toggle** — `UserPreferences.fetchPreviewsAutomatically` is
  wired to the Settings switch and defaults on. With it off, the app must be
  100% network-silent (spec §11).
- **Backup exclusion** — `thumbnails/` and `favicons/` are already excluded from
  Auto Backup in `backup_rules.xml` / `data_extraction_rules.xml`.

Build the engine as a self-contained module with no Android UI dependencies
(spec §7), and **build the fixture suite first** — 30–40 saved HTML heads from
real sites. Spec §12 flags M3/M4 as the milestones likely to overrun, and
parsing real-world HTML is where the surprises live. The fixtures make the
parser testable without the network.

The HTTP policy (§7.2), parsing precedence (§7.3), thumbnail pipeline (§7.4) and
special cases (§7.5) are specified in enough detail to implement directly. The
two-User-Agent strategy matters more than anything else in that section: desktop
Chrome first, `facebookexternalhit/1.1` on 403/429 or when no OG tags are
present, never a third.

### Retry queue

`BookmarkApp` already implements `Configuration.Provider` with `HiltWorkerFactory`,
and `hilt-work` is on the classpath. `BookmarkDao.findByStates` exists to feed
both the retry queue and Settings' "Refresh all metadata". Spec §8.5 has the
policy: unique work per bookmark id, `ExistingWorkPolicy.KEEP`,
`NetworkType.CONNECTED`, exponential backoff from 30s, max 3 attempts, then
terminal `FAILED` with no further automatic retries.

### Search (M5)

Everything below the UI is done. `BookmarkRepository.search` applies a prefix
match to every term so results appear while typing. The screen needs: full-screen
presentation, keyboard up on open, 150ms debounce, matched-term highlighting,
and category filtering from within search. `SearchRoute` is already declared in
`navigation/Routes.kt`; `HomeScreen`'s `onSearch` is a no-op waiting for it.

### Settings (M5)

`SettingsScreen` has the full layout with the design's grouping and ordering —
Backup first, as spec §5.6 asks, since with no backend export is the only
disaster-recovery path. Appearance and the privacy toggle are wired. Export,
import, refresh-all and clear-thumbnails are rendered **disabled rather than
hidden**, so the shape of the screen is already right and M5 only fills them in.

---

## 5. Decisions taken on the spec's open questions (§14)

Settled with the spec's own leanings, confirmed by the user:

1. **Duplicate URLs** — surface the existing bookmark. The unique index rejects
   the re-save, `BookmarkRepository.save` returns `SaveResult.Duplicate`, and
   `DuplicateBookmarkSheet` shows the design's "Already saved in …" sheet with
   *Refresh preview* / *View bookmark*. `Refresh preview` is currently a no-op
   and becomes real in M3.
2. **Deleted bookmarks** — hard delete with Snackbar undo, not a soft-delete
   trash. `BookmarkRepository.restore` re-inserts verbatim.
3. **Thumbnail cache ceiling** — manual only, via Settings. No LRU eviction.
4. **Category limit** — unlimited. The filter row scrolls.
5. **`og:image` picker** — kept in the design, so keep it. The `Thumbnail ▾`
   control in the add sheet is present but disabled until M3 supplies candidates;
   `PageMetadata.imageCandidates` holds up to five.

---

## 6. Deliberate shortcuts, to revisit

- **`UrlExtractor` does not use `android.util.Patterns.WEB_URL`**, which the spec
  names. It uses an equivalent pure-Kotlin regex so the extraction rules — the
  part with real-world surprises — are unit-testable on the JVM without
  Robolectric. It adds a `looksLikeHost` guard because the permissive pattern
  otherwise matches version strings like `v2.14` and decimals like `3.14159`;
  both are covered by tests.
- **Navigation is `when (selectedTab)`, not a `NavHost`.** Type-safe
  `@Serializable` routes are declared in `navigation/Routes.kt` and
  kotlinx-serialization is on the classpath, but with three top-level
  destinations and every detail surface being a `ModalBottomSheet`, a NavHost
  earns nothing yet. Introduce it when Search arrives, since that is the first
  real destination. Sheets should stay sheet-state-driven — it keeps the M7
  shared-element transition available.
- **`UrlNormalizer.registrableDomain` uses a bundled list of two-part public
  suffixes**, not the full Public Suffix List. The PSL is ~230KB and the only
  consumers are a display label and a colour hash, where an occasional wrong
  split is harmless. Revisit only if it starts mattering.
- **The `outline` colour role is off-spec-M3 on purpose.** The design uses
  `#C3CFCB` for field and chip borders, which is closer to M3's `outlineVariant`.
  The design's value was kept, because matching the mockups matters more than
  matching Material's semantics here.
- **Dark theme colours are derived, not designed.** The design file only
  specifies the light palette (plus the `#0D1513` dark device surface). The dark
  scheme in `Color.kt` is a reasonable M3 companion for the same seed, but it has
  not been reviewed against a mockup. Worth a design pass in M7.
- **Category icons are placeholder glyphs.** `iconKey` persists, and the dialog
  offers four keys plus None, but they render as letters rather than the bundled
  Material icon set spec §4.2 describes.
- **`DirectShareShortcuts` publishes on app start and after each save.** It does
  not yet react to category deletion, so a stale shortcut can linger until the
  next save.

---

## 7. Verification

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest          # 35 tests
./gradlew :app:connectedDebugAndroidTest  # 9 tests, needs a device
./gradlew :app:assembleDebug
```

Unit tests cover `UrlNormalizer`, `TitleFallback`, `UrlExtractor` and
`DomainColor`, including both worked examples from the spec
(`…/how-to-build-an-app-1234` → "How To Build An App";
`news.ycombinator.com` → "Hacker News").

Instrumented tests cover the seeded category, the unique-URL constraint,
case-insensitive category names, both category-delete strategies, the FK
fallback, FTS search and FTS delete-sync, and the movable default flag.

**There is no emulator configured on this machine** — no system images are
installed and no AVD exists. The instrumented tests above were run against a
connected physical device. To create an emulator instead:

```bash
sdkmanager "system-images;android-37;google_apis;arm64-v8a"
avdmanager create avd -n bookmark -k "system-images;android-37;google_apis;arm64-v8a"
```

### Driving the share target without another app

```bash
adb shell am start -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT \
  "Check this out https://increment.com/apis/design-of-everyday-apis" \
  -n com.bookmark/.share.QuickSaveActivity
```

Vary it with two URLs in the text to exercise "Other links in this share", and
with no URL at all to exercise the inline-error path.

### What was verified on a real device

Every screen was driven on a physical device (Xiaomi 2201117TI, Android 16)
rather than an emulator. Confirmed working end to end: the share target with
multi-URL extraction and `EXTRA_SUBJECT` as the title; save; the staggered grid
and the compact list; category filtering with live counts; sort and view-mode
switching; the context sheet; edit with the category picker; delete with
Snackbar undo; category create, edit, delete-with-reassign, and drag-reorder;
Settings including theme, dynamic colour and the privacy toggle; and both light
and dark themes with the design palette (dynamic colour off).

Driving it found **ten** defects that the build, the tests and lint had all
missed. All are fixed, and each fix carries a comment at the point of the fix:

1. The bottom bar was drawn under the system navigation bar.
2. `TitleFallback` titled `news.ycombinator.com/item?id=…` as "Item" -- a bare
   router segment is noise, so aggregator segments now fall through to the host.
3. The "Preview pending" pill wrapped and clipped when the site line was long.
4. The context sheet opened half-expanded and clipped itself: Pin and Delete
   were literally unreachable.
5. **Category delete had no entry point at all.** `onLongPressDelete` was
   declared and passed but never attached to a gesture, so a named M1
   deliverable was dead code. Delete now lives in the edit dialog, which is
   where the design implies it (the row shows no delete affordance).
6. Drag-to-reorder never fired: the long-press detector sat on the LazyColumn,
   whose own scroll gesture sees pointer events first. Moved to the handle.
7. Then it fired but never persisted -- `pointerInput` was keyed on the row
   index, so reordering restarted the handler mid-gesture and `onDragEnd` never
   ran. The index is read through `rememberUpdatedState` instead.
8. Status bar and navigation bar icons followed the *system* theme, not the
   app's, so they were unreadable whenever the two disagreed.
9. Cards used `surfaceContainerLowest`, which in M3 dark is darker than the
   surface -- cards receded instead of lifting. Now an explicit `cardSurface`
   token.
10. Minor: the overflow menu anchored to the screen edge; the delete dialog said
    "1 bookmarks are"; an empty Add sheet rendered a meaningless "?" preview.

Two things worth knowing that are not bugs:

- Reading the clipboard for the suggestion chip trips Android 12+'s
  "pasted from your clipboard" system toast on every Add. Unavoidable while the
  feature exists; if it grates, the toggle is to drop the suggestion chip.
- `run-as` is blocked by SELinux on this device (MIUI, targetSdk 37), so the
  on-device database cannot be inspected over adb. Use an emulator for that.

Still unverified: text scaling to 200%, TalkBack traversal, and predictive back
(all M7), and the true-black OLED variant.

### The offline check that matters### The offline check that matters

Turn on airplane mode, save a link, and confirm it persists instantly with a
`Preview pending` pill and a monogram tile, and that browsing, filtering and
sorting stay fully functional. That is design principles 1 and 4, and it is the
thing most likely to regress once M3 introduces real network calls.
