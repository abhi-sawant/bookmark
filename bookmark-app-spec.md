# Bookmark App — Technical Specification

**Platform:** Android (Native, Kotlin + Jetpack Compose)
**Scope:** Personal side project, single developer
**Backend:** None. Zero server components.
**Version:** 1.0 (draft)
**Date:** September 2026

---

## 1. Overview

A local-first Android app for saving links. The user shares a URL from any app, the app fetches the page's title, description and preview image the way WhatsApp or Slack do, stores everything on-device, and shows it in a fast, browsable list organised by category.

The defining constraint is that **no server of mine sits between the user and the web**. Metadata is fetched by the device directly from the target site. Everything the user creates — bookmarks, categories, downloaded thumbnails — lives in app-private storage and is fully readable with the network off.

### 1.1 Design principles

1. **Saving never blocks.** A bookmark is persisted the instant the user taps Save, regardless of fetch state. Metadata arrives later, asynchronously, or never.
2. **Degrade, don't fail.** Every field has a defined fallback chain that ends in something displayable. There is no "couldn't load" empty state on a saved bookmark.
3. **The user's edit always wins.** Any manually-edited field is locked against future automatic overwrites.
4. **Offline is the default state, not an error state.** No spinners, banners, or disabled controls just because the device is offline.

---

## 2. Terminology: what "fully offline" means here

This is worth stating precisely, because the requirement "fully offline" and the requirement "fetch link previews" are in tension.

| Capability | Requires network? |
|---|---|
| Viewing, searching, filtering all saved bookmarks | No |
| Viewing thumbnails of saved bookmarks | No — images are downloaded and stored locally |
| Creating, editing, deleting bookmarks and categories | No |
| Saving a URL from the share sheet | No |
| Export / import backup | No |
| **Fetching metadata for a newly added URL** | **Yes** — the only network-dependent operation |
| Opening the saved link in a browser | Yes (the browser's problem, not ours) |

So: the app has **no backend**, **no account**, **no sync**, **no analytics**, and **no telemetry**. The only outbound traffic the app ever generates is a direct request to a URL the user explicitly saved. When the device is offline, that one operation is queued and the app remains fully functional.

`INTERNET` is the only permission requested. No storage, camera, contacts, or location permissions.

---

## 3. Technical stack

| Concern | Choice | Note |
|---|---|---|
| Language | Kotlin 2.x | |
| UI | Jetpack Compose, Material 3 | Expressive components where the Compose BOM supports them |
| Architecture | MVVM + unidirectional data flow | `StateFlow`-driven, single state object per screen |
| DI | Hilt | Manual DI is also fine at this size; Hilt for the WorkManager integration |
| Persistence | Room + Room FTS4 | Single source of truth |
| Preferences | DataStore (Proto or Preferences) | Theme, default view, last-used category |
| Networking | OkHttp | Redirect + timeout + interceptor control is required here |
| HTML parsing | Jsoup | Parse-only; Jsoup is not used for fetching |
| Image loading | Coil 3 | Memory + disk cache |
| Background work | WorkManager | Metadata retry queue |
| Navigation | Navigation Compose (type-safe routes) | |
| Min / target SDK | 26 / latest stable (36) | 26 gives adaptive icons and modern notification APIs |

Single Gradle module, package-by-feature (`bookmarks/`, `categories/`, `metadata/`, `share/`, `settings/`, `core/`). Multi-module is not worth the build-config overhead at this scale.

---

## 4. Data model

### 4.1 `bookmarks`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT (UUID) | PK |
| `url` | TEXT | Normalised (§7.1). Unique index |
| `originalUrl` | TEXT | Exactly as shared, before normalisation |
| `title` | TEXT | Never null — falls back per §8.2 |
| `description` | TEXT? | Nullable |
| `siteName` | TEXT? | e.g. "GitHub" |
| `thumbnailPath` | TEXT? | Relative path inside `filesDir/thumbnails/` |
| `faviconPath` | TEXT? | Relative path inside `filesDir/favicons/` |
| `accentColor` | INTEGER? | Dominant colour extracted from thumbnail, for placeholders |
| `categoryId` | TEXT | FK → `categories.id`, `ON DELETE SET DEFAULT`. Indexed |
| `metadataState` | TEXT | Enum, §8.1 |
| `fetchAttempts` | INTEGER | Default 0 |
| `lastFetchAt` | INTEGER? | Epoch millis |
| `manualFields` | INTEGER | Bitmask: TITLE=1, DESCRIPTION=2, THUMBNAIL=4 |
| `isPinned` | INTEGER | Boolean |
| `createdAt` | INTEGER | Indexed (default sort) |
| `updatedAt` | INTEGER | |

**`manualFields`** is the mechanism behind principle #3. When the user edits the title, bit 1 is set; the metadata worker then skips the title on any subsequent fetch or refresh.

### 4.2 `categories`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT (UUID) | PK |
| `name` | TEXT | Unique, case-insensitive, max 40 chars |
| `colorHex` | TEXT | User-picked or auto-assigned from a preset palette |
| `iconKey` | TEXT? | Key into a bundled Material icon set |
| `sortOrder` | INTEGER | Manual drag-reorder |
| `isDefault` | INTEGER | Exactly one row true |
| `createdAt` | INTEGER | |

A seeded **"Unsorted"** category (`isDefault = true`) is created on first run. It cannot be deleted or renamed to collide with another category. Every bookmark has exactly one category — there is no null state, which removes a whole class of empty-state handling from the UI.

> Tags (many-to-many) are deliberately out of scope for v1. See §13.

### 4.3 `bookmarks_fts`

Room FTS4 virtual table, content-backed by `bookmarks`, indexing `title`, `description`, `siteName`, `url`. Gives sub-10ms substring search at any realistic library size without loading rows into memory.

---

## 5. Screens

### 5.1 Home

- Top app bar (large, collapsing on scroll) with search icon and overflow.
- Horizontally scrolling category filter chips, with an "All" chip pinned first and a live count badge.
- Content: switchable **grid** (2-column staggered, image-forward) and **list** (compact row: thumbnail 64dp, title, site name). Preference persisted.
- Sort: newest, oldest, title A–Z, category. Persisted.
- Item tap → open URL in Custom Tab. Long-press → context sheet (Edit, Change category, Copy link, Share, Pin, Delete).
- FAB → Add bookmark sheet.
- Empty state: friendly illustration + "Share a link to this app from anywhere" with a hint about the share sheet.

### 5.2 Add / Edit bookmark (bottom sheet)

Layout, top to bottom:

1. **URL field** — auto-filled from clipboard if the clipboard holds a URL not already saved (shown as a dismissible suggestion chip, never silently pasted).
2. **Live preview card** — renders exactly as the list item will. Shows a shimmer while fetching, the fallback tile on failure.
3. **Title** — text field, pre-filled from fetch, max 200 chars.
4. **Description** — multi-line, 3 lines visible, max 500 chars.
5. **Thumbnail** — tappable; options are *Pick from device*, *Choose another image found on page* (if the parser found several), *Remove*, *Retry fetch*.
6. **Category** — dropdown with inline "＋ New category" that creates without leaving the sheet.
7. **Save** — enabled as soon as the URL is syntactically valid. Never disabled by fetch state.

Fetching starts on URL-field debounce (600ms after typing stops) and again on paste. Fields the user has already touched are not overwritten when results arrive.

### 5.3 Quick-save sheet (share target)

A separate, translucent, no-history activity — this is the hot path and gets its own optimised surface. See §6.

### 5.4 Categories

- Reorderable list (drag handle), each row showing colour dot, name, bookmark count.
- Create / edit dialog: name, colour swatch picker, optional icon.
- Delete flow presents a mandatory choice: **Move bookmarks to another category** (picker) or **Delete category and its bookmarks** (destructive, typed confirmation not required but a Snackbar Undo is).
- Deleting the default category is blocked; the user must first mark another as default.

### 5.5 Search

Full-screen, opens with keyboard up. FTS-backed, results debounced 150ms, matched terms highlighted. Filterable by category from within search.

### 5.6 Settings

- Theme: System / Light / Dark. Dynamic colour toggle (Android 12+).
- Default view (grid/list), default category for shares.
- **Export backup** → single `.zip` via SAF containing `bookmarks.json`, `categories.json`, and a `thumbnails/` folder.
- **Import backup** → merge or replace, with a preview of the counts before committing.
- **Refresh all metadata** → enqueues a batched refresh for bookmarks with `metadataState` in (`FAILED_RETRYABLE`, `FALLBACK`), respecting manual-field locks.
- Storage usage: thumbnail cache size + "Clear thumbnails" (re-fetchable, non-destructive to bookmarks).
- About / open-source licences.

Because there's no backend, **export is the only disaster-recovery path and should be prominent**, not buried. Android Auto Backup is also enabled for the Room DB, with `filesDir/thumbnails/` excluded to stay under the 25MB quota.

---

## 6. Share target

### 6.1 Manifest

```xml
<activity
    android:name=".share.QuickSaveActivity"
    android:theme="@style/Theme.App.Transparent"
    android:excludeFromRecents="true"
    android:noHistory="true"
    android:launchMode="singleTask"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="http" />
        <data android:scheme="https" />
    </intent-filter>
</activity>
```

`ACTION_PROCESS_TEXT` is also registered so a URL selected inside any text can be saved from the text-selection toolbar.

### 6.2 URL extraction

Shared text is rarely a bare URL. WhatsApp sends `"Check this out https://example.com/x"`; YouTube sends the video title plus the link plus a promo line. Extraction rules:

1. Run `android.util.Patterns.WEB_URL` over `EXTRA_TEXT`, take the **first** match.
2. If `EXTRA_SUBJECT` exists and no title has been fetched yet, hold it as a fallback title candidate — many apps put the page title there.
3. If zero URLs are found, open the sheet with the raw text in the URL field and an inline error, rather than showing a toast and dying.
4. If multiple URLs are found, use the first but expose the others in an "Other links in this share" expandable row.

### 6.3 Behaviour

The quick-save sheet must feel instant — under **300ms** from tap to visible sheet. It renders immediately with just the URL and a shimmering preview; metadata streams in.

Layout is a condensed version of §5.2: preview card, title (editable), category chips (last-used category pre-selected), **Save** and **More options** (expands to the full sheet).

A one-tap variant is also supported: **Direct Share shortcuts** via `ShortcutManagerCompat` publish the four most-used categories to the system share sheet, so "Share → Bookmarks: Reading" saves straight into that category with a confirmation Snackbar and no sheet at all.

---

## 7. Metadata fetch engine

This is the most involved part of the app and should be built as a self-contained, unit-testable module with no Android UI dependencies (`metadata/`), exposing:

```kotlin
suspend fun fetch(url: String): MetadataResult
```

### 7.1 URL normalisation

Applied before dedupe check and before fetch:

- Add `https://` if no scheme is present.
- Lowercase scheme and host; strip default ports.
- Strip fragment (`#...`) unless the host is known to use hash routing.
- Strip tracking params: `utm_*`, `fbclid`, `gclid`, `msclkid`, `igshid`, `mc_eid`, `ref_src`, `si` (YouTube/Spotify share token).
- Preserve everything else — stripping `?v=` on YouTube or `?p=` on WordPress would break the link.
- Strip trailing slash only on bare-host URLs.

The normalised URL is what gets stored and what the unique index dedupes on. The original is kept for debugging and for the (rare) case where normalisation breaks a link.

### 7.2 HTTP request policy

| Setting | Value | Why |
|---|---|---|
| Connect timeout | 5s | |
| Read timeout | 8s | |
| Total budget | 10s hard ceiling | Prevents a hung fetch pinning a Worker |
| Redirects | Follow, max 5 | Shortened links are the norm |
| Method | `GET`, ranged | |
| `Range` header | `bytes=0-524287` | 512KB is far past `</head>` on any sane page |
| Early abort | Stop reading at `</head>` | Typically ends the request in under 30KB |
| `Accept` | `text/html,application/xhtml+xml` | |
| `Accept-Language` | Device locale, then `en;q=0.8` | |
| Content-Type gate | Abort parse unless `text/html` or `application/xhtml+xml` | §7.5 handles the others |
| Cleartext | Blocked by default (`usesCleartextTraffic=false`) | `http://` links are upgraded to `https://` and fall back to fallback-metadata if that fails |

**User-Agent** matters more than anything else here. Many sites serve Open Graph tags only to recognised crawlers, and others block unknown agents outright. Strategy:

1. First attempt: a current desktop Chrome UA string.
2. If the response is 403/429, or the HTML contains no OG/Twitter tags at all, retry once with `facebookexternalhit/1.1` — the agent most sites whitelist specifically to make link previews work.
3. Never attempt a third UA. Two requests is the ceiling per fetch.

### 7.3 Parsing precedence

Parsed with Jsoup from the downloaded head. Relative image URLs are resolved against `<base href>` if present, otherwise the final (post-redirect) URL.

**Title**
1. `og:title`
2. `twitter:title`
3. JSON-LD `headline` or `name`
4. `<title>`, with a trailing site-name suffix stripped (` | Site`, ` - Site`, ` — Site`) when the suffix matches `og:site_name` or the host
5. → fallback chain (§8.2)

**Description**
1. `og:description`
2. `twitter:description`
3. `<meta name="description">`
4. JSON-LD `description`
5. → null (description is allowed to be absent; it is not an error)

**Image**
1. `og:image:secure_url`, then `og:image` (collect **all** of them — pages often list several; keep up to 5 for the "choose another image" picker)
2. `twitter:image`
3. JSON-LD `image`
4. `<link rel="apple-touch-icon">` (largest `sizes`)
5. Largest `<link rel="icon">`
6. → generated fallback tile (§8.3)

**Site name**: `og:site_name` → JSON-LD `publisher.name` → registrable domain.

**Favicon**: `<link rel="icon">` (largest) → `/favicon.ico` at the root. Note that third-party favicon services (Google's S2, DuckDuckGo's icons endpoint) are explicitly **not** used — they would leak every saved URL to a third party and violate the zero-backend principle.

All extracted strings are HTML-entity-decoded, whitespace-collapsed, trimmed, and length-capped (title 200, description 500, siteName 60).

### 7.4 Thumbnail processing

1. Download candidate image with the same timeout policy, cap 10MB.
2. Reject if: content-type isn't `image/*`, decode fails, or either dimension is under 64px (these are almost always tracking pixels or logos masquerading as `og:image`).
3. Try the next candidate in the list on rejection; then fall through to the generated tile.
4. Downscale so the longest edge is ≤ 1080px, preserving aspect ratio.
5. Encode WebP at quality 80 — typically 40–90KB per thumbnail. A 2,000-bookmark library lands around 120MB worst case.
6. Write to `filesDir/thumbnails/{bookmarkId}.webp`.
7. Extract dominant colour via `androidx.palette` and store as `accentColor` for placeholder tinting.

Thumbnail files are deleted alongside their bookmark. An orphan-sweep runs on app start (cheap: list dir, diff against DB ids) to catch files left by crashes.

### 7.5 Special cases

| Case | Handling |
|---|---|
| YouTube | Extract video id; use `https://i.ytimg.com/vi/{id}/maxresdefault.jpg`, falling back to `hqdefault.jpg` (which always exists). Avoids the heavy YouTube page load entirely |
| Direct image URL | Content-type is `image/*` → the URL *is* the thumbnail; title = filename |
| PDF | Content-type is `application/pdf` → title = decoded filename, description = null, thumbnail = document-type tile. No PDF rendering in v1 |
| Sites that hard-block bots (X, Instagram, some news paywalls) | Expected to fail. They fall straight to §8 — this is normal behaviour, not a bug to chase |
| SPA with no server-rendered head | No JS execution. Falls back. Rendering JS would require a WebView and is explicitly rejected for v1 (slow, memory-heavy, and a fingerprinting surface) |
| Non-UTF-8 encodings | Honour `Content-Type` charset, then `<meta charset>`, then default UTF-8 |

---

## 8. Graceful fallback

### 8.1 State machine

```
                 ┌─────────┐
  save ────────▶ │ PENDING │ (offline, or queued)
                 └────┬────┘
                      │ network available
                      ▼
                 ┌──────────┐
                 │ FETCHING │
                 └────┬─────┘
        ┌─────────────┼──────────────┬──────────────┐
        ▼             ▼              ▼              ▼
   ┌─────────┐  ┌──────────┐  ┌────────────┐  ┌──────────┐
   │ SUCCESS │  │ PARTIAL  │  │  FALLBACK  │  │  FAILED  │
   └─────────┘  └──────────┘  └────────────┘  └────┬─────┘
                                                   │ retry ≤ 3
                                                   └──▶ PENDING

  any state ──user edits field──▶ MANUAL (per-field, via manualFields bitmask)
```

| State | Meaning | UI treatment |
|---|---|---|
| `PENDING` | Queued; device offline or worker not yet run | Subtle "Preview pending" pill on the card |
| `FETCHING` | In flight | Shimmer on the image area only; text already shows fallbacks |
| `SUCCESS` | Title + image both resolved | Normal |
| `PARTIAL` | Some fields resolved (e.g. title but no image) | Normal — resolved fields shown, unresolved use fallbacks. **No error indicator** |
| `FALLBACK` | Page reachable but yielded nothing usable | Normal, generated tile. Retry available in the overflow menu only |
| `FAILED` | Network error, timeout, 4xx/5xx, DNS failure | Normal card + small retry affordance on the detail sheet |

Crucially, **`PARTIAL` and `FALLBACK` are not error states from the user's perspective**. The card looks complete. Only an explicit hard failure surfaces a retry affordance, and even then it's never a blocking dialog or a red banner.

### 8.2 Title fallback chain

Applied in order until one produces a non-empty string:

1. Fetched title (§7.3)
2. `EXTRA_SUBJECT` from the share intent, if present and not identical to the URL
3. **Derived from the URL path** — take the last meaningful path segment, drop the file extension and any trailing id, replace `-`/`_` with spaces, title-case:
   `example.com/blog/how-to-build-an-app-1234` → *"How To Build An App"*
4. Registrable domain, title-cased: `news.ycombinator.com` → *"Hacker News"* (a small bundled map of ~50 common hosts) or *"Ycombinator"*
5. The raw URL (guaranteed non-empty, so the chain always terminates)

### 8.3 Thumbnail fallback

A **generated monogram tile** rendered in Compose, not a bundled static asset:

- Background: a deterministic colour derived from `hash(registrableDomain)` mapped into a curated palette that satisfies contrast requirements against both themes. The same site always gets the same colour, which makes the grid scannable even when every tile is a fallback.
- Foreground: the site's favicon if one was retrieved; otherwise the first one or two letters of the domain in the app's display typeface.
- Content-type variants: distinct glyph treatments for PDF, direct image, and video links.

This is rendered at display time from `url` + `accentColor`, so it costs no storage and never needs a fetch.

### 8.4 Description fallback

None. An absent description simply collapses the layout — no placeholder text, no "No description available". The card is designed to look correct with zero, one, or two lines of description.

### 8.5 Retry policy

WorkManager, unique work per bookmark id, `ExistingWorkPolicy.KEEP`:

- Constraint: `NetworkType.CONNECTED`.
- Backoff: exponential, 30s base, max 3 automatic attempts.
- After 3 failures → terminal `FAILED`. No further automatic retries ever; only a manual retry or a "Refresh all" from Settings.
- Batched refresh (Settings → Refresh all) runs with a concurrency limit of 4 and a per-host politeness delay of 1s.

### 8.6 Failure taxonomy and messaging

| Cause | State | User-visible text |
|---|---|---|
| No network at save time | `PENDING` | "Preview pending" |
| DNS / connection refused | `FAILED` | "Couldn't reach this site" |
| Timeout | `FAILED` | "This site took too long to respond" |
| 401 / 403 / 429 | `FALLBACK` | "This site doesn't share previews" |
| 404 / 410 | `FAILED` | "This page wasn't found" |
| 5xx | `FAILED` | "This site is having problems" |
| Valid HTML, no usable tags | `FALLBACK` | *(nothing — silent)* |
| Image download failed only | `PARTIAL` | *(nothing — silent)* |

Messages appear only in the bookmark detail sheet, never as toasts or Snackbars during the save flow.

---

## 9. Performance targets

"Fast & responsive" gets concrete numbers, measured on a mid-range device (Pixel 6a class, release build with R8):

| Metric | Target |
|---|---|
| Cold start to first frame | < 500ms |
| Share-sheet tap → quick-save sheet visible | < 300ms |
| Home list scroll | Zero jank frames over a 500-item scroll |
| Search keystroke → results | < 50ms |
| Frame budget | 16.6ms (60Hz), 8.3ms where the panel allows |
| APK size | < 8MB |

**Tactics**

- **Baseline Profile** generated via the Macrobenchmark module — typically 20–30% off cold start for a Compose app, and the single highest-leverage item on this list.
- Lazy layouts with stable `key` and `contentType`. `LazyVerticalStaggeredGrid` for grid view.
- **Paging 3** from Room, page size 40, only once libraries exceed ~1,000 items. Below that, a plain `Flow<List<Bookmark>>` is faster and simpler.
- Coil configured with a 25% memory-cache share and a 250MB disk cache; `placeholderMemoryCacheKey` for grid → detail transitions.
- All DB access via suspend/Flow off the main thread. Indices on `categoryId`, `createdAt`, `url`.
- Compose hygiene: immutable state classes, `@Stable`/`@Immutable` annotations, lambda references hoisted, `derivedStateOf` for scroll-derived values. Verify with Layout Inspector recomposition counts, not by intuition.
- StrictMode (disk + network on main thread → `penaltyDeath`) in debug builds.
- Metadata fetching is strictly off the UI path — a `FETCHING` state must never block a frame.

---

## 10. Visual design

"Modern looking" translated into specifics:

- **Material 3**, using Expressive components (loading indicators, button groups, motion physics) where the Compose BOM in use supports them.
- **Dynamic colour** on Android 12+, defaulting on. A hand-picked seed palette for Android 11 and below and for users who disable it.
- **Edge-to-edge** throughout, with proper `WindowInsets` handling — no content under the status bar, no double padding.
- **Predictive back** enabled, with proper back-progress animation on sheets and detail screens.
- **Shared-element transitions** between grid item and detail sheet (thumbnail morphs into the hero image).
- Motion follows M3 spec easings; nothing ad-hoc. Reduced-motion setting respected.
- Haptics on save, delete, and drag-reorder pickup.
- Typography scale with one display face for headers and the system face for body — avoids the flat all-Roboto default look without hurting readability.
- Full dark theme with true-black option for OLED.
- Content-forward cards: image dominant, title at 2-line clamp, description at 2-line clamp, category shown as a small coloured dot rather than a text chip (cheaper visually, and the colour does the work).

**Accessibility**: minimum 48dp touch targets, content descriptions on every image and icon button, TalkBack traversal verified on Home and the quick-save sheet, text scaling to 200% without clipping, colour never the sole carrier of meaning (category dots pair with names in the filter row).

---

## 11. Privacy

Worth writing down even for a personal project, because the behaviour is non-obvious:

- All data is stored in app-private storage. Nothing leaves the device except the metadata request itself.
- **Fetching metadata makes a direct request to the saved site.** That site sees the device IP and User-Agent. This should be stated in a one-line note in Settings, alongside a toggle: **"Fetch link previews automatically"** (default on). With it off, the app is 100% network-silent and all bookmarks use fallbacks until the user taps fetch manually.
- No third-party favicon or preview services, for the same reason.
- No crash reporting SDK in v1. If one is added later, it must be opt-in.

---

## 12. Milestones

| # | Deliverable |
|---|---|
| M0 | Project skeleton, theme, navigation, Room schema + DAOs, seeded default category |
| M1 | Bookmark CRUD, Home list/grid, category CRUD + delete-reassign flow |
| M2 | Share target, URL extraction, quick-save sheet, Direct Share shortcuts |
| M3 | Metadata engine: fetch, parse, thumbnail pipeline, WorkManager retry |
| M4 | Full fallback system, state machine, generated tiles, manual-field locking |
| M5 | Search (FTS), sort/filter, settings, export/import |
| M6 | Performance pass: baseline profile, macrobenchmarks, recomposition audit |
| M7 | Polish: transitions, haptics, accessibility audit, empty/error states |

M3 and M4 are the ones likely to overrun — parsing real-world HTML is where the surprises live. Budget generously there and build a fixture-based test suite (30–40 saved HTML heads from real sites) early, since it makes the parser testable without the network.

---

## 13. Out of scope for v1

Sync or cloud backup · accounts · tags (many-to-many) · nested categories · full-article archiving / read-later text extraction · browser extension · WebView-based JS rendering · widgets · Wear OS · iOS · duplicate-URL merging UI · bulk operations / multi-select · notes on bookmarks.

---

## 14. Open questions

1. **Duplicate URLs** — the unique index will reject a re-save. Should the app silently surface the existing bookmark ("Already saved in Reading"), or offer to update its metadata? Surfacing seems right; confirm before M2.
2. **Deleted bookmarks** — hard delete with Snackbar undo, or a soft-delete trash with a 30-day sweep? Snackbar undo is simpler and probably sufficient.
3. **Thumbnail cache ceiling** — should there be an automatic LRU eviction above, say, 300MB, given thumbnails are re-fetchable? Or leave it fully manual in Settings?
4. **Category limit** — cap at some number (20?) to keep the filter chip row usable, or allow unlimited with a "More" overflow?
5. **`og:image` picker** — is exposing multiple candidate images worth the UI cost, or is first-valid-wins enough for v1?
