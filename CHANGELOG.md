# Changelog

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
