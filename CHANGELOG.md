# Changelog

## 3.1.0 — Unreleased

### Added
- **Copied-link chip on Home.** When you have copied a link, Home offers a one-tap "Save copied link?" chip (Android and web). It never saves on its own, is only read while the app is open, and can be turned off in Settings ("Suggest copied links").
- **Bookmarklet (web).** Settings has a draggable "Save to Bookmarks" button. Drag it to your bookmarks bar and click it on any page to open the add sheet with that page filled in.
- **Link previews on the web.** When you are signed in, links added on the web now get a title, description and thumbnail, read by your sync server (browsers can't read other sites). A Settings switch ("Fetch link previews") turns it off. **Requires the backend update below.**
- **Tags.** Add up to 10 tags (30 characters each) to any bookmark from the add/edit sheet, comma separated. Tags show in the bookmark details, are matched by search, and `#tag` in search lists bookmarks with exactly that tag. Tags sync between devices and are included in backups. **Requires the backend update below.**
- **Search chips and recent searches.** Search has quick chips (Pinned, No category, This week) that narrow results, or list everything they match when the box is empty. The last eight searches are remembered, shown when the box is empty, and can be cleared (Android and web).
- **Surprise me.** A button on Home opens a random bookmark from the older half of your collection, so older saves resurface (Android and web).
- **Unread dot.** New bookmarks show an accent dot until you open them. Opening a link clears it; "Mark as read/unread" is in the long-press menu, and an Unread chip filters Home (and Search). Unread state syncs. Existing bookmarks start as read. **Requires the backend update below.**
- **Trash.** Deleting a bookmark now moves it to Trash (Settings > Trash) instead of removing it. It stays for 30 days, can be restored or deleted for good, and Trash can be emptied. Trash syncs between devices and is included in backups. Saving a link that is in Trash brings it back. **Requires the backend update below.**
- **Select several bookmarks.** Choose "Select" in a bookmark's long-press menu, tap more bookmarks, then Pin, Move, Tag or Delete them in one go (deleted ones go to Trash with Undo). Android and web.
- **Check links.** Settings > Check links asks each saved site whether its page still exists. Bookmarks whose page is gone (404/410) show "Link may be broken" and can be listed with the Broken links chip in Search. Inconclusive answers (timeouts, blocked requests) are never reported as broken. On Android the phone checks directly; on the web the check runs through your sync server. Results stay on the device. **The web check requires the backend update below.**
- **Open from the long-press menu (Android).** The bookmark actions sheet now has an Open row, like the web app.
- **Home-screen widget (Android).** Add the "Bookmarks" widget to see your pinned and latest bookmarks, tap one to open it, or tap "Save copied link" to save whatever link you just copied.
- **Keyboard shortcuts list (web).** Press `?` anywhere on the desktop site, or use Settings > Keyboard shortcuts, to see every shortcut.
- **Import browser bookmarks.** Import a `bookmarks.html` exported from Chrome, Edge, Firefox or Safari from Settings (Android and web). Folders become categories, links outside a folder go to Unsorted, and links you already have are skipped. It can only be merged, never replace what you have.

### Fixed
- **Android sync no longer stalls on two categories with the same name.** If your account had two categories named the same (for example one made on the web and one on another phone), pulling the second evicted the first, orphaned its bookmarks and made every later sync fail. The second one now gets a local " (2)" suffix. Failed syncs are also logged now.

### Changed
- More tracking parameters are stripped when you save a link (Instagram `igsh`, Google `gbraid`/`wbraid`, HubSpot and Marketo tokens), so the same page saved from different sources is recognised as already saved.

### Backend
**Upgrade the server before releasing the apps.** Full checklist, smoke tests and rollback: `docs/SERVER_CHANGES_3.1.0.md`.
- Database, run once each in order in phpMyAdmin: `backend/sql/migrations/001_rate_limit_metadata.sql`, `002_bookmark_tags.sql` (adds `bookmarks.tags`), `003_bookmark_unread.sql` (adds `bookmarks.is_unread`), `004_bookmark_trash.sql` (adds `bookmarks.trashed_at`). Fresh installs get all of it from `schema.sql`.
- Updated: `backend/api/sync/push.php` and `backend/api/sync/pull.php` (the three new fields).
- New: `backend/api/metadata.php` (link previews), `backend/api/check_links.php` (link checks) and `backend/safe_fetch.php` (SSRF-protected outbound requests). These need the `curl` PHP extension and outbound HTTP/HTTPS from the hosting account.
- Keep your existing `config.php`. Two optional settings, `METADATA_RATE_PER_HOUR` and `LINK_CHECK_REQUESTS_PER_HOUR`, have built-in defaults.

## 3.0.0 — 2026-10-09

### Added
- **Web app.** Your bookmarks are now available in a browser at bookmark.slowatcoding.com, an installable PWA with the same look as the app and a desktop layout (sidebar, search box, keyboard shortcuts). Sign in with the same account to sync. The web app doesn't fetch link previews; bookmarks added there show a generated tile until the Android app fetches a thumbnail.

### Changed
- Repo layout: the Android project moved to `android/`; the web app is in `web/`; the backend stays in `backend/`.
- A thumbnail fetched on the phone for a bookmark the server has no thumbnail for (for example one added on the web) is now uploaded on the next sync, so it appears on every device.

### Backend
- `backend/bootstrap.php` and `backend/config.php` updated: the API now answers CORS preflight requests and allows the web app's origin. No database/schema changes. Upload the new `bootstrap.php` (keep your existing `config.php`; the code falls back to the production origin if the new `CORS_ALLOWED_ORIGINS` setting is absent). See `docs/MILESWEB_DEPLOYMENT_GUIDE.md` (sections 4 and 11).

## 2.0.1 — 2026-10-09

### Added
- **Update notifications.** When the app opens it checks GitHub for a newer release. If one is available, a popup shows the changelog with three choices: **Update** (opens the release page to download), **Not now** (asks again next time) and **Skip this version** (stays quiet until a newer one is out). If you're offline, nothing happens.

## 2.0.0 — 2026-10-09

### Changed
- **Slate redesign.** A new, calmer look replaces the translucent "Aurora" style: flat panels with hairline borders, a single accent colour, a docked bottom bar, and the Hanken Grotesk typeface. Light and dark themes both updated, along with the launcher icon (now with a themed monochrome version).
- **Simpler Home screen.** The overflow menu is gone. Sorting is a button under the category chips, and the list/grid switch sits beside it. Every previous option is still available.
- Grid cards no longer have a container; section labels are lowercase and easier to read.
- Segmented controls now behave as proper radio groups for screen readers.

### Fixed
- Sync no longer rejects bookmarks or categories whose title, description, site name or category name contain non-English characters or emoji (limits are now counted in characters, not bytes). **Requires the backend update below.**
- Rejected sync rows are now logged on the device to make future problems easier to diagnose.

### Backend
- `backend/api/sync/push.php` updated (multibyte-safe length validation). No database/schema changes. To deploy, upload the new `push.php` (or the full backend zip) over the existing files as described in `docs/MILESWEB_DEPLOYMENT_GUIDE.md`; keep your existing `config.php`, as the zip contains placeholders only.
