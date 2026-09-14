# Bookmarks — handover

**State:** M0–M4 complete. M5–M7 not started.
**Date:** 14 September 2026

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
| M3 — metadata engine | Done |
| M4 — fallback system | Done |
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

## 4. The engine, and the seams M4+ extends

### Metadata engine — built in M3

`metadata/` is a package, not a Gradle module. Spec §7 asks for something
"self-contained with no Android UI dependencies" and §3 rules out multi-module
at this scale; those are not in conflict — §7 means package discipline.
Everything under `parse/`, `special/` and `image/ThumbnailPolicy` is pure JVM.

```
metadata/
  MetadataFetcher.kt         interface + result types (unchanged from M0)
  DefaultMetadataFetcher.kt  fetch → parse → classify; bound in NetworkModule.kt
  NetworkModule.kt           the one OkHttpClient, and the @Binds M3 swapped
  http/    HtmlFetcher, RequestUrls, FetchOutcome
  parse/   MetadataParser, JsonLd, TitleSuffix
  special/ YouTube
  image/   ThumbnailPolicy (pure), ThumbnailPipeline (Android)
  work/    MetadataEnqueuer, MetadataWorker, RefreshAllWorker
```

Things worth knowing that the code does not say on its own:

- **Redirects are followed by hand.** OkHttp's limit is hardcoded at 20 and the
  spec caps it at 5. The manual loop is also how the post-redirect URL becomes
  available for resolving relative images, and how each hop gets upgraded off
  cleartext — `usesCleartextTraffic="false"` means an `http://` hop fails at the
  socket, so upgrading only the URL the user typed is not enough.
- **`RequestUrls` is separate from `HtmlFetcher`** so the scheme and redirect
  arithmetic is testable without a socket. `HtmlFetcher` takes an
  `upgradeCleartext` flag purely so MockWebServer, which speaks http, can drive
  the rest of the policy. It is a secondary constructor rather than a default
  argument: **Dagger does not read Kotlin default values**, and a defaulted
  `Boolean` asks it for a `Boolean` binding that nothing provides.
- **Suffix stripping runs on every title source, not just `<title>`.** Spec §7.3
  attaches it to step 4 on the assumption that the social tags omit it. The
  fixtures disagree — MDN and Wikipedia both ship the full "Title | Site" string
  in `og:title`. `TitleSuffix` only strips a tail that demonstrably names the
  site, so running it more often costs nothing.
- **`MetadataParser` falls back to `TitleFallback.fromDomain`, not the bare
  registrable domain**, for `siteName`. Using the raw domain regressed `ogp.me`
  from "Open Graph Protocol" to "ogp.me" on the first fetch — caught by driving
  the app, not by the tests.
- **YouTube still loads the watch page.** Spec §7.5 says to skip it entirely,
  but `/watch` is a noise segment, so skipping it titles every video "YouTube".
  The `i.ytimg.com` URLs are instead *prepended* to the candidates, so the
  thumbnail is guaranteed whether or not the page parses — and §7.2's early
  abort at `</head>` means the page read is tens of KB, not the multi-megabyte
  document the spec was avoiding.
- **The manual-field lock is enforced in SQL**, in `BookmarkDao.applyMetadata`.
  A read-check-write in Kotlin has a real race: the user can edit the title in
  the sheet between the read and the write. Doing the `manualFields & n` check
  inside the `UPDATE` makes the lock atomic with the write, which is what makes
  design principle 3 actually hold. `updatedAt` is deliberately not bumped — a
  background fetch is not a user edit.
- **`delete()` no longer removes the thumbnail file.** It cannot: `restore()`
  re-inserts the row with the same `thumbnailPath`, so deleting the file made
  undo silently downgrade the bookmark to a monogram tile. The file is now
  reclaimed by `HomeViewModel.clearUndo()` when the Snackbar resolves, and by
  `sweepOrphanThumbnails()` on next launch if the process dies first.
- **Thumbnail filenames stay flat** (`{bookmarkId}.webp`). `sweepOrphanThumbnails()`
  diffs `file.name` against `dao.allThumbnailPaths()`, so any nested scheme would
  make every file look orphaned and delete the lot. Commented at both ends.
- **`ThumbnailPolicy` is split out from `ThumbnailPipeline`** because Robolectric's
  Bitmap shadows are fakes and cannot validate real decoding or WebP encoding.
  The arithmetic and the accept/reject rules are plain-JUnit tested; the platform
  calls are covered on a device.
- **The privacy toggle is enforced in three places**, and needs all three:
  `MetadataEnqueuer` does not enqueue, `MetadataWorker` re-checks on entry (the
  toggle can flip in between), and turning it off cancels work already queued.
  Spec §11 promises the app is *100% network-silent*, not merely that it stops
  scheduling. A **manual** fetch deliberately ignores the toggle — §11 says
  bookmarks use fallbacks "until the user taps fetch manually".
- **`RefreshAllWorker` includes `PENDING`** in its eligible states, which spec
  §5.6 does not name. Without it, a bookmark saved while previews were off would
  sit in `PENDING` forever with no route back — the spec did not consider that
  combination.
- **No favicons.** The design uses a category-colour dot in all seven contexts
  and never a site icon, so the parser does not extract one and nothing writes
  `filesDir/favicons/`. `faviconPath` remains in the schema, unused.

### Schema v2

`failureCause`, `thumbnailWidth`, `thumbnailHeight`, `imageCandidates`, plus an
index on `metadataState`. All additive and nullable, so `MIGRATION_1_2` is four
`ALTER TABLE`s and a `CREATE INDEX`; the FTS table is untouched. `2.json` is
committed and `MigrationTest` validates against it.

`app/build.gradle.kts` adds the schema directory as an **androidTest asset
source** — `MigrationTestHelper` loads the exported JSON from the test APK's
assets, and without that every migration test fails with `FileNotFoundException`.

The thumbnail dimensions exist so `BookmarkGridCard` can lay a card out at the
image's real aspect ratio. Before M3 it used a domain-hash height even when a
real image existed, which cropped every thumbnail into the wrong box.

### The fixture corpus

`app/src/test/resources/fixtures/` holds 41 saved `<head>` blocks: 28 fetched
from real sites, 13 hand-written for edge cases the wild does not reliably
serve (base href, ISO-8859-1, malformed JSON-LD, `@graph`, seven `og:image`
tags, over-long fields, icons-only). Inline CSS and non-JSON-LD script bodies
were stripped — 2.4MB down to 356KB — which changes nothing the parser reads.

Regenerating them is a deliberate act, not a build step: they are the record of
what the web looked like, and a test that silently re-fetches is a test that
cannot fail.

### M4 — fallback system UI

The engine-side work (state machine, retry policy, thumbnail pipeline) was
already in place from M3; M4 was entirely UI, wiring existing plumbing that
had no caller yet.

- **Live preview calls `MetadataFetcher.fetch(url)` directly**, not through
  WorkManager. `AddEditViewModel`/`QuickSaveViewModel` debounce URL changes
  600ms (paste included -- accepting the clipboard chip runs the same
  debounced call) and apply the result to in-memory state only, respecting
  the same `touchedFields`/`titleTouched` locks used at save time. There is
  no bookmark id yet to enqueue a worker against, and `MetadataFetcher` was
  already built self-contained for exactly this (spec 7). The real,
  persisted fetch still runs after save via `BookmarkRepository.save`'s
  existing automatic enqueue -- the live preview never writes to the DB.
- **`ThumbnailSurface`/`PreviewCard` gained a `previewModel: Any?` param**
  (a Coil model: a remote candidate URL, or a local `content://` `Uri` from
  the picker) for exactly this pre-save case, ordered between the stored
  local file and the monogram fallback.
- **The Thumbnail picker's "choose another image" gates on
  `imageCandidates.size > 1`**, per spec 5.2 item 5's "if the parser found
  several" -- worth remembering if a similar list-picker is added elsewhere,
  since it is easy to instead gate on "list not empty" and show a
  single-item "choose among 1" menu, which is what the first pass here did
  before catching it on-device.
- **`ThumbnailPipeline.attempt` was split into `attempt`/`encode`** so
  "Pick from device" (`storeFromUri`) can share the decode/scale/WebP/accent
  -color logic without duplicating it. The 10MB `MAX_DOWNLOAD_BYTES` cap
  stays on the network path only -- `storeFromUri` reads the picked `Uri`
  uncapped, since that limit exists to bound an *untrusted remote*
  candidate (spec 7.4), not a file the user just chose, and a modern phone
  photo routinely exceeds 10MB.
- **A manual thumbnail choice (candidate switch / device pick / remove) is
  applied in a follow-up write** (`BookmarkRepository.applyManualThumbnail`
  → `BookmarkDao.setManualThumbnail`) right after the row is inserted or
  updated, never before -- the thumbnail file is named `{bookmarkId}.webp`,
  which does not exist pre-save. This sets `ManualField.THUMBNAIL`
  unconditionally, which is what makes `applyMetadata`'s existing CASE guard
  (already there since M3, just never triggered by anything) leave the
  choice alone on every subsequent automatic fetch.
- **A tap on a `FAILED` bookmark opens the detail sheet instead of the
  browser** (`BookmarkNavHost`'s `onOpenBookmark`); every other state still
  opens the link directly. This is also the only route to the detail sheet
  from Home -- there was previously no way to reach it except via "View
  bookmark" on the duplicate sheet.
- **The context sheet's "Retry fetch" row is conditional on `FALLBACK`
  specifically**, not `FAILED` -- per spec 8.1 the two states' retry
  affordances are deliberately in different places (`FALLBACK`: overflow
  menu only; `FAILED`: detail sheet card only), and showing it in both
  would contradict "`FALLBACK` is not an error state."

### What was verified on a real device for M4

Same device (Xiaomi 2201117TI, Android 16). Confirmed working end to end:

- Typing a real URL into the Add sheet populated title, description, site
  name and a live thumbnail (loaded straight off the network) within the
  debounce window, without saving anything.
- The Thumbnail picker's "choose another image" section correctly stayed
  hidden for a single-candidate page (`ogp.me`) after the size-gating fix
  above.
- "Remove" swapped the preview to the monogram tile, and the monogram
  survived the automatic background fetch after save (the manual-field
  lock held).
- "Pick from device" launched the real Android Photo Picker, previewed the
  picked image pre-save, and the picked image survived save *and* the
  automatic background fetch afterward -- the riskiest new path, since it
  is the one path with no prior plumbing at all (no `Uri`/`ContentResolver`
  use anywhere else in the app).
- "Refresh preview" on the duplicate sheet and "Retry fetch" on the detail
  sheet both actually enqueue `MetadataWorker` now (confirmed via
  `WM-WorkerWrapper` logcat lines), not just dismiss the sheet.
- A bookmark against a non-resolving host reached terminal `FAILED` after
  three automatic attempts, tapping its card opened the detail sheet (not
  the browser) with the real cause-specific message, and its context
  sheet -- unlike a `FALLBACK` bookmark's -- correctly showed no "Retry
  fetch" row.

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
./gradlew :app:testDebugUnitTest          # 135 tests
./gradlew :app:connectedDebugAndroidTest  # 20 tests, needs a device
./gradlew :app:assembleDebug
```

Unit tests cover `UrlNormalizer`, `TitleFallback`, `UrlExtractor` and
`DomainColor`, including both worked examples from the spec
(`…/how-to-build-an-app-1234` → "How To Build An App";
`news.ycombinator.com` → "Hacker News"), plus everything M3 added: the parser
against all 41 fixtures, `TitleSuffix`, `JsonLd`, `YouTube`, `ThumbnailPolicy`,
`RequestUrls`, and the whole spec §7.2 request policy over MockWebServer —
redirect cap, both UA switches, the two-attempt ceiling, `Range`, the
content-type gate, early abort at `</head>` (including mixed case and an
unterminated head), and the §8.6 status mapping.

Instrumented tests cover the seeded category, the unique-URL constraint,
case-insensitive category names, both category-delete strategies, the FK
fallback, FTS search and FTS delete-sync, the movable default flag, the 1→2
migration validated against the committed schemas (rows, the manual-field mask
and FTS all survive), and the manual-field lock in `applyMetadata` — one test
per bit plus the combined case.

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

### What was verified on a real device for M3

Same device (Xiaomi 2201117TI, Android 16). Eight links shared in via the Direct
Share path — which saves with no sheet, so it drives the flow without blind
taps. Confirmed: real titles, descriptions, site names and thumbnails, with the
staggered grid laying cards out at each image's real aspect ratio; `| MDN` and
`| Kotlin Documentation` suffixes stripped; x.com served Open Graph tags to the
crawler UA, so the two-UA strategy is doing real work; an unreachable host
retried three times and went terminal, dropping the "Preview pending" pill.

The three checks that matter most all pass:

- **Offline.** Airplane mode, save a link: persisted instantly with a monogram
  tile and a "Preview pending" pill, browsing and filtering fully functional.
  Network back on, and the queued fetch drained into a real preview.
- **Privacy.** With "Fetch link previews automatically" off, a save made no
  request at all and stayed `PENDING`.
- **Undo.** `delete()` keeps the thumbnail file, so undo restores the bookmark
  intact rather than downgrading it to a monogram.

Driving it found two defects the tests missed: the `siteName` regression on
known hosts described above, and the `PENDING` rows stranded by the privacy
toggle. Both are fixed.

One cosmetic issue was noted and left for M7's dark-theme pass: the `DesignSwitch`
off-state knob renders near-black on the dark surface and is hard to see. The
design only specifies the light palette, which is exactly the gap M7 covers.

Two things worth knowing that are not bugs:

- Reading the clipboard for the suggestion chip trips Android 12+'s
  "pasted from your clipboard" system toast on every Add. Unavoidable while the
  feature exists; if it grates, the toggle is to drop the suggestion chip.
- `run-as` is blocked by SELinux on this device (MIUI, targetSdk 37), so the
  on-device database cannot be inspected over adb. Use an emulator for that.

Still unverified: text scaling to 200%, TalkBack traversal, and predictive back
(all M7), and the true-black OLED variant.

### The offline check that matters

Turn on airplane mode, save a link, and confirm it persists instantly with a
`Preview pending` pill and a monogram tile, and that browsing, filtering and
sorting stay fully functional. That is design principles 1 and 4, and it is the
thing most likely to regress once M3 introduces real network calls.
