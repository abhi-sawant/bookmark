# Server changes for 3.1.0

Everything the backend and database need for the 3.1.0 apps. Read this top to
bottom once, then follow the checklist in section 8. It assumes the 3.0.0
backend from `MILESWEB_DEPLOYMENT_GUIDE.md` is already running.

> **The PHP in this release was written without a PHP interpreter on the
> development machine**, so it has been read carefully but not executed. Run the
> syntax check in section 8, step 4, and the smoke tests in section 7 before you
> tell anyone to update the apps.

---

## 1. What changed, in one table

| Area | Change | Needed by |
|---|---|---|
| Database | `rate_limit_events.action` accepts a new value `metadata` | link previews, link checks (web) |
| Database | New column `bookmarks.tags` (TEXT, NULL) | Tags |
| Database | New column `bookmarks.is_unread` (TINYINT, default 0) | Unread dot |
| Database | New column `bookmarks.trashed_at` (DATETIME(6), NULL) | Trash |
| Sync API | `sync/push.php` and `sync/pull.php` read and write the three new columns | Tags, Unread, Trash |
| New endpoint | `api/metadata.php` | Link previews for links added on the web |
| New endpoint | `api/check_links.php` | "Check links" on the web |
| New library | `safe_fetch.php` (SSRF-protected outbound HTTP, used by both new endpoints) | the two endpoints above |
| Config | Two optional keys, `METADATA_RATE_PER_HOUR` and `LINK_CHECK_REQUESTS_PER_HOUR` | optional |

No tables are added or dropped, and **nothing about 3.0.0 breaks**: every new
column has a default, and the old apps ignore fields they do not know.

Features that need **no** server change: tracking-parameter cleanup, the
copied-link chip, the bookmarklet, search chips and recent searches, "Surprise
me", bulk select, the Open row, the Android widget, the shortcuts list, and
browser-bookmarks import. The Android "Check links" runs on the phone.

---

## 2. Deploy order matters

1. **Back up the database** (phpMyAdmin > Export > Quick > SQL).
2. **Run the SQL migrations** (section 3). The new `push.php` names the new
   columns, so uploading it before the SQL makes every sync fail with a 500.
3. **Upload the PHP files** (section 4).
4. Run the **smoke tests** (section 7).
5. Only then release the 3.1.0 apps.

Shipping the apps first is the one order to avoid. A 3.1.0 app talking to a
3.0.0 server works, but the server drops tags, unread state and Trash, and the
next pull would write empty values over them on that device.

---

## 3. Database migrations

Run them **once each, in order**, on an existing database. A fresh install does
not need them: `backend/sql/schema.sql` already contains every change.

How to run one: cPanel > phpMyAdmin > click your database in the left sidebar >
**SQL** tab > paste the file's contents > **Go**. (Or use the **Import** tab with
the file, as in the original guide.)

### 001 — `backend/sql/migrations/001_rate_limit_metadata.sql`

```sql
ALTER TABLE rate_limit_events
    MODIFY action ENUM('register', 'login', 'forgot_password', 'metadata') NOT NULL;
```

Lets the new endpoints record their rate-limit attempts. Safe to run twice.

### 002 — `backend/sql/migrations/002_bookmark_tags.sql`

```sql
ALTER TABLE bookmarks ADD COLUMN tags TEXT NULL AFTER is_pinned;
```

Tags are stored as one string, one tag per line (the same trick as
`image_candidates`). Up to 10 tags, 30 characters each; the server checks this.

### 003 — `backend/sql/migrations/003_bookmark_unread.sql`

```sql
ALTER TABLE bookmarks ADD COLUMN is_unread TINYINT(1) NOT NULL DEFAULT 0 AFTER tags;
```

Existing bookmarks become "read" (0), so nobody sees a library full of dots.

### 004 — `backend/sql/migrations/004_bookmark_trash.sql`

```sql
ALTER TABLE bookmarks ADD COLUMN trashed_at DATETIME(6) NULL AFTER is_unread;
```

`trashed_at` set means "in Trash" (a soft delete that syncs); NULL means live.
It is separate from `deleted_at`, which stays the permanent delete (tombstone).
The apps remove trashed bookmarks themselves after 30 days, so the server needs
no purge job.

### Checking it worked

```sql
SHOW COLUMNS FROM bookmarks;            -- must list tags, is_unread, trashed_at
SHOW COLUMNS FROM rate_limit_events;    -- action must list 'metadata'
```

If a migration says `Duplicate column name`, that migration was already applied;
carry on with the next one.

### Rolling the database back

Only if you must, and only **after** reverting the PHP files and the apps:

```sql
ALTER TABLE bookmarks DROP COLUMN trashed_at;
ALTER TABLE bookmarks DROP COLUMN is_unread;
ALTER TABLE bookmarks DROP COLUMN tags;
```

This permanently discards every tag, unread flag and trashed state. Trashed
bookmarks would reappear as normal ones. Restore the backup from step 1 instead
if you can.

---

## 4. Files to upload

Upload over the existing files, keeping the same folder layout. **Do not
overwrite your own `config.php`** (see section 5).

| File | Status |
|---|---|
| `backend/api/sync/push.php` | changed |
| `backend/api/sync/pull.php` | changed |
| `backend/api/metadata.php` | **new** |
| `backend/api/check_links.php` | **new** |
| `backend/safe_fetch.php` | **new** (sits next to `auth.php`, not inside `api/`) |
| `backend/sql/schema.sql` | changed (only needed for fresh installs; keep a copy for reference) |
| `backend/sql/migrations/*.sql` | **new** (not served; only run in phpMyAdmin) |
| `backend/config.php` | template gained a commented example only: do not upload over yours |

Unchanged and not needed: `bootstrap.php`, `auth.php`, `db.php`, `response.php`,
`validate.php`, the cron scripts, `vendor/`, and the thumbnail upload endpoint.
No change to `.htaccess` or CORS is required: both new endpoints are plain JSON
`POST`s from the already-allowed web origin.

---

## 5. Configuration (all optional)

Your deployed `config.php` keeps working unchanged. To tune the limits, add:

```php
// Link previews per account per hour (api/metadata.php). Default 60.
define('METADATA_RATE_PER_HOUR', 60);

// "Check links" requests per account per hour (api/check_links.php).
// One request checks up to 10 links. Default 30.
define('LINK_CHECK_REQUESTS_PER_HOUR', 30);
```

Both fall back to the defaults above when absent (`defined()` checks in the PHP).

---

## 6. Hosting requirements for the new endpoints

The two new endpoints make **outbound** requests, which the rest of the backend
never did. Check these on the MilesWeb plan (cPanel > **Select PHP Version** >
**Extensions**, or a temporary `phpinfo()` page you delete afterwards):

| Needed | Why | If missing |
|---|---|---|
| PHP 8.1+ (already required) | | |
| `curl` extension | fetching pages | previews and link checks return an error; everything else works |
| `mbstring`, `dom` / `libxml` | reading page titles and descriptions | previews return empty fields |
| Outbound HTTP/HTTPS on ports 80 and 443 | reaching other sites | some shared hosts firewall outbound traffic; ask support to allow it |
| DNS lookups from PHP (`dns_get_record`) | resolving a page's host safely | the endpoints return "could not be resolved" |
| `gd` with WebP support (`imagewebp`) | **optional**: storing a downscaled thumbnail | previews still return title and description, just no thumbnail; the web app shows its monogram tile |

The thumbnails folder must stay writable by PHP: `backend/uploads/thumbnails/`
(it already is, from 3.0.0).

### How the endpoints stay safe

The server fetches addresses that users choose, so `safe_fetch.php` refuses
anything that could reach your own network: only `http`/`https` on ports 80 and
443; no credentials in URLs; the host is looked up first and **every** address
it resolves to must be public (loopback, private ranges, link-local including
the cloud metadata address, carrier-grade NAT and IPv6 equivalents are all
rejected); the checked address is pinned for the connection so it cannot change
between the check and the request; redirects are followed by hand, at most 3,
with each hop checked again; 4 s connect and 8 s total timeouts; HTML capped at
1 MB and images at 3 MB. Both endpoints require a signed-in account and are
rate limited per account.

---

## 7. API reference and smoke tests

Replace `$TOKEN` with a token from `login.php`, as in section 9 of the original
guide.

### `POST /api/metadata.php`: read a page's preview

```bash
curl -i -X POST https://api.bookmark.slowatcoding.com/api/metadata.php \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"url":"https://example.com","bookmark_id":"test-123"}'
```

Expect `200` and:

```json
{ "title": "Example Domain", "description": null, "site_name": null,
  "image_url": null, "final_url": "https://example.com",
  "thumbnail_url": null, "thumbnail_width": null, "thumbnail_height": null }
```

`bookmark_id` is optional; with it, and when GD supports WebP, a thumbnail is
stored at `uploads/thumbnails/{user}/{bookmark_id}.webp` and its URL is returned.

| Status | Code | Meaning |
|---|---|---|
| 422 | `VALIDATION_FAILED` | missing/invalid `url` or `bookmark_id` |
| 429 | `RATE_LIMITED` | over `METADATA_RATE_PER_HOUR` |
| 502 | `FETCH_FAILED` | the page could not be fetched or answered with an error |
| 401 / 405 | | not signed in / not a POST |

**Prove the safety checks work.** Each of these must be refused with
`502 FETCH_FAILED` (never fetched):

```bash
for u in http://127.0.0.1/ http://localhost/ http://169.254.169.254/latest/meta-data/ \
         http://10.0.0.1/ "http://[::1]/" http://example.com:8080/ ftp://example.com/ \
         "http://user:pass@example.com/"; do
  echo "== $u"
  curl -s -X POST https://api.bookmark.slowatcoding.com/api/metadata.php \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "{\"url\":\"$u\"}"; echo
done
```

### `POST /api/check_links.php`: do these pages still exist?

```bash
curl -i -X POST https://api.bookmark.slowatcoding.com/api/check_links.php \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"urls":["https://example.com","https://example.com/this-page-does-not-exist-xyz"]}'
```

Expect `200`:

```json
{ "results": [
  { "url": "https://example.com", "status": "ok", "http_status": 200 },
  { "url": "https://example.com/this-page-does-not-exist-xyz", "status": "broken", "http_status": 404 }
] }
```

`status` is `ok` (2xx/3xx), `broken` (404, 410, or the host name does not exist)
or `error` (anything inconclusive: timeouts, 403/429/5xx, a refused address).
`error` is never shown to users as broken. One request takes 1 to 10 URLs
(`422` otherwise) and stops starting new checks after about 22 seconds.

### Sync: the three new fields

Push a bookmark carrying them, then pull it back:

```bash
curl -s -X POST https://api.bookmark.slowatcoding.com/api/sync/push.php \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"categories":[{"id":"unsorted","name":"Unsorted","color_hex":"#8F96C4","is_default":true,
       "created_at":1700000000000,"updated_at":1700000000000}],
       "bookmarks":[{"id":"smoke-1","url":"https://example.com/s","original_url":"https://example.com/s",
       "title":"Smoke","category_id":"unsorted","tags":["a","b"],"is_unread":true,
       "trashed_at":1700000000000,"created_at":1700000000000,"updated_at":1700000000001}]}'
curl -s "https://api.bookmark.slowatcoding.com/api/sync/pull.php?since=0" -H "Authorization: Bearer $TOKEN"
```

The pulled `smoke-1` must show `"tags":["a","b"]`, `"is_unread":true` and
`"trashed_at":1700000000000`. Remove the test row afterwards with
`DELETE FROM bookmarks WHERE id = 'smoke-1';` in phpMyAdmin.

Wire format of the new bookmark fields (JSON, snake_case, on both `push.php`
request items and `pull.php` upserts):

| Field | Type | Rules |
|---|---|---|
| `tags` | array of strings | at most 10; each 1 to 30 characters, no line breaks; absent = none |
| `is_unread` | boolean | absent = false |
| `trashed_at` | integer epoch milliseconds, or `null` | `null`/absent = not in Trash |

A bookmark that fails these checks is rejected as `validation_failed` (the rest
of the batch still applies), exactly like the existing fields.

---

## 8. Checklist

1. [ ] Export a backup of the database.
2. [ ] phpMyAdmin: run `001`, `002`, `003`, `004` in order; confirm with the two `SHOW COLUMNS` queries.
3. [ ] Upload the changed and new files from section 4 (not your `config.php`).
4. [ ] Syntax-check what you uploaded (cPanel Terminal, or ask MilesWeb support). Every line must print `No syntax errors detected`:
   ```bash
   cd /home/youruser/public_html/api.bookmark.slowatcoding.com
   for f in safe_fetch.php api/metadata.php api/check_links.php api/sync/push.php api/sync/pull.php; do php -l "$f"; done
   ```
5. [ ] Check the hosting requirements in section 6 (especially `curl`).
6. [ ] Run the smoke tests in section 7, including the refused-address list.
7. [ ] Optional: add the two config keys from section 5.
8. [ ] Leave the cron jobs as they are (`prune_rate_limits.php` already removes `metadata` rows after 7 days).
9. [ ] Release the 3.1.0 apps.

## 9. Things to know

- **Thumbnails are not deleted with their bookmark** (true since 3.0.0 and still true): neither a permanent delete nor emptying Trash removes `uploads/thumbnails/{user}/{id}.webp`. It is harmless but accumulates; a periodic cleanup of files with no matching bookmark row would reclaim the space.
- **Old apps are safe.** A 3.0.0 app pushing a bookmark does not send the new fields, so the server stores `tags = NULL`, `is_unread = 0` and `trashed_at = NULL`, and the old app cannot see them. If someone edits the same bookmark in an old app and a new app, last write wins as it always has, so tags added on the new app can be overwritten by an edit from the old one.
- **Trash retention is enforced by the apps**, not the server: each app permanently deletes (with a tombstone) anything trashed more than 30 days ago when it starts.
- **Android "Check links" results and the web equivalent stay on the device**; the server never stores them.
