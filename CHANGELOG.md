# Changelog

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
