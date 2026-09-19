# Deploying the Bookmark sync backend to MilesWeb shared hosting

This guide walks through putting the `backend/` folder from this repository
live at **`api.bookmark.slowatcoding.com`**, on your MilesWeb shared hosting
account. It assumes **zero prior backend/server experience** — every step
says exactly which screen to click through in cPanel.

You will need:
- Your MilesWeb cPanel login (URL is usually something like
  `https://yourdomain.com:2083`, or a link from your MilesWeb welcome email —
  if you're not sure, MilesWeb support can send it to you).
- The `backend/` folder from this project, on your computer.
- About 45–60 minutes the first time through.

Work through the sections in order — each one depends on the last.

---

## 1. Create the subdomain

1. Log in to cPanel.
2. Find **Domains** (older cPanel themes call this **Subdomains**) under the
   "Domains" section of the dashboard.
3. Click **Create A New Domain** (or **Create a Subdomain**).
4. For the domain/subdomain name, enter: `api.bookmark`
   - cPanel will show it combining with your main domain into
     `api.bookmark.slowatcoding.com` — confirm that's what's shown.
5. For the **Document Root**, cPanel will suggest something like
   `public_html/api.bookmark.slowatcoding.com`. Leave the suggested value, or
   change it to `api_bookmark` for a shorter path — just remember whatever you
   pick, you'll need it in step 3.
6. Click **Submit** / **Create**.

You now have an (empty) subdomain and a folder for it. Nothing is live yet.

---

## 2. Create the MySQL database

1. In cPanel, find **MySQL® Databases** under "Databases".
2. Under **Create New Database**, enter a name, e.g. `bookmark`. cPanel will
   prefix it automatically with your account name, giving you something like
   `youruser_bookmark` — **write this full name down**, you'll need it soon.
3. Click **Create Database**.
4. Scroll to **MySQL Users → Add New User**. Enter a username (e.g.
   `bookmark_api`) and click **Password Generator** to get a strong password —
   **copy this password somewhere safe right now**, cPanel won't show it again.
   cPanel will prefix the username too, giving you e.g. `youruser_bookmark_api`.
5. Click **Create User**.
6. Scroll to **Add User To Database**, choose the user and database you just
   created, click **Add**.
7. On the next screen ("Manage User Privileges"), check **ALL PRIVILEGES**,
   then click **Make Changes**.

You should now have written down three things:
- Database name (e.g. `youruser_bookmark`)
- Database username (e.g. `youruser_bookmark_api`)
- Database password

### Import the schema

1. Back on the cPanel dashboard, find **phpMyAdmin** under "Databases".
2. In the left sidebar, click your new database (`youruser_bookmark`).
3. Click the **Import** tab along the top.
4. Click **Choose File**, and select `backend/sql/schema.sql` from this
   project on your computer.
5. Scroll down and click **Go**.
6. You should see a green success message and, in the left sidebar under your
   database, six new tables: `users`, `auth_tokens`, `categories`, `bookmarks`,
   `password_resets`, `rate_limit_events`. If you see a red error instead,
   the most common cause is importing into the wrong database — check step 2.

---

## 3. Upload the backend files

You can use cPanel's **File Manager** (no extra software) or an FTP client
like [FileZilla](https://filezilla-project.org) (easier for large uploads).
Either way, the destination is the document root folder you created in step 1
(e.g. `public_html/api.bookmark.slowatcoding.com`).

### Option A — File Manager (simplest)

1. On your computer, go into the `backend/` folder and select **everything
   inside it** (`config.php`, `bootstrap.php`, `api/`, `vendor/`, `uploads/`,
   `cron/`, `sql/`, everything) and compress it into a single `backend.zip`.
   Do **not** zip the `backend` folder itself — zip its *contents*, so that
   when it's extracted, `config.php` lands directly in the target folder, not
   inside a `backend/` subfolder.
2. In cPanel, open **File Manager**.
3. Navigate into your subdomain's document root folder (from step 1).
4. Click **Upload**, drag in `backend.zip`, and wait for it to finish.
5. Go back to the file listing, right-click `backend.zip`, choose **Extract**,
   confirm the destination is the current folder.
6. Once extracted, delete `backend.zip` (right-click → Delete) — no need to
   leave it taking up space.
7. Confirm you now see `config.php`, `bootstrap.php`, `api/`, `vendor/`,
   `uploads/`, `cron/`, `sql/` directly inside the document root folder (not
   nested inside another `backend` folder — if they are, cut/move them up one
   level and delete the empty `backend` folder).

### Option B — FTP (FileZilla)

1. In cPanel, find **FTP Accounts** and either use your main account's FTP
   login or create a dedicated FTP account scoped to the subdomain's folder.
2. In FileZilla, connect using the host, username, and password from cPanel
   (host is often just your domain name; port 21).
3. On the right-hand (remote) side, navigate to your subdomain's document
   root folder.
4. On the left-hand (local) side, navigate into your `backend/` folder.
5. Select everything inside `backend/` and drag it to the right-hand pane.
   Wait for every file to finish uploading (this can take a few minutes —
   there are a few dozen files including PHPMailer).

Either way, when you're done, `sql/` will also have been uploaded to the
live server — that's harmless (it's not reachable as a URL) but you can
delete it afterward if you'd rather not have `schema.sql` sitting there.

---

## 4. Fill in `config.php`

1. In cPanel's **File Manager**, navigate to your subdomain's folder and find
   `config.php`.
2. Right-click it and choose **Edit** (or **Code Editor**).
3. Fill in every line marked `// CHANGE ME`:

   ```php
   define('DB_HOST', 'localhost');                    // usually stays "localhost"
   define('DB_NAME', 'youruser_bookmark');             // from step 2
   define('DB_USER', 'youruser_bookmark_api');         // from step 2
   define('DB_PASS', 'the password you generated');    // from step 2

   define('APP_BASE_URL', 'https://api.bookmark.slowatcoding.com'); // no trailing slash
   ```

4. For the SMTP section, see **Section 6** below — come back and fill that
   in once you've created the mailbox there. For now you can leave the SMTP
   placeholders as-is; registration, login, and sync will all work without
   them. Only "forgot password" emails need SMTP configured.
5. Click **Save Changes**.

---

## 5. Select the PHP version

1. In cPanel, find **MultiPHP Manager** under "Software".
2. Find your subdomain (`api.bookmark.slowatcoding.com`) in the list.
3. Set its PHP version to **8.1** or higher (8.2/8.3 are fine too).
4. Click the checkmark/**Apply** to save.

If you skip this and the subdomain is left on an old PHP version (5.x/7.x),
every request will fail with a 500 error.

---

## 6. Confirm HTTPS (SSL)

MilesWeb includes free AutoSSL (Let's Encrypt) certificates.

1. In cPanel, find **SSL/TLS Status** under "Security".
2. Find `api.bookmark.slowatcoding.com` in the list. It usually gets a
   certificate automatically within a few minutes to a few hours of the
   subdomain being created.
3. If it's not yet issued, check the box next to it and click **Run AutoSSL**
   to trigger it manually, then wait a few minutes and refresh.
4. Once issued, visiting `https://api.bookmark.slowatcoding.com/` in a
   browser should show a padlock with no warning (the page itself will show
   a blank or error response — that's expected, there's no homepage, only
   API endpoints).

The backend refuses to serve non-HTTPS requests (except from `localhost`), so
this step is required before anything else will work from the app.

---

## 7. Set up the password-reset mailbox (SMTP)

This step is only needed for the "forgot password" email flow. You can skip
it initially and come back later — everything else (register, login, sync)
works without it.

1. In cPanel, find **Email Accounts** under "Email".
2. Click **Create**, and create an address like
   `noreply@slowatcoding.com` (it does **not** need to be on the
   `api.bookmark` subdomain — any mailbox on your main domain works).
3. Set a password for it (cPanel's generator is fine) and note it down.
4. Still in **Email Accounts**, find your new address and click
   **Connect Devices** (or **Set Up Mail Client**) — this screen shows the
   exact SMTP host, port, and encryption type MilesWeb wants you to use.
   It's usually something like:
   - Host: `mail.slowatcoding.com` (or a MilesWeb server name)
   - Port: `587` with **STARTTLS**, or `465` with **SSL**
5. Back in `config.php` (File Manager → Edit), fill in:

   ```php
   define('SMTP_HOST', 'mail.slowatcoding.com');   // from step 4
   define('SMTP_PORT', 587);                       // from step 4
   define('SMTP_SECURE', 'tls');                   // 'tls' for 587, 'ssl' for 465
   define('SMTP_USERNAME', 'noreply@slowatcoding.com');
   define('SMTP_PASSWORD', 'the mailbox password from step 3');
   define('SMTP_FROM_EMAIL', 'noreply@slowatcoding.com');
   define('SMTP_FROM_NAME', 'Bookmark App');
   ```

6. Save the file.

---

## 8. Set up the cron jobs

These two scripts just keep old rate-limit/password-reset rows from piling
up forever — harmless to skip initially, but good hygiene to add once
everything else works.

1. In cPanel, find **Cron Jobs** under "Advanced".
2. Under **Add New Cron Job**, choose **Once Per Day** (or set it manually to
   e.g. `0 3 * * *` for 3am daily).
3. For the command, enter (adjust the path to match where you uploaded the
   backend — check File Manager for the exact full path, usually shown at
   the top of the file listing):

   ```
   php /home/youruser/public_html/api.bookmark.slowatcoding.com/cron/prune_rate_limits.php
   ```

4. Click **Add New Cron Job**.
5. Repeat for `cron/prune_password_resets.php`.

---

## 9. Smoke-test every endpoint

Before touching the Android app, verify the backend works entirely on its
own using `curl`. You can run these from a Mac/Linux terminal, or from
Windows PowerShell (the syntax is the same for `curl` on modern Windows 10/11).

Replace `you@example.com` / `SomePassword123` with real test values.

**Register:**
```bash
curl -i -X POST https://api.bookmark.slowatcoding.com/api/register.php \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"SomePassword123","device_name":"curl test"}'
```
Expect `HTTP/1.1 201 Created` and a JSON body like
`{"user_id":1,"device_id":1,"token":"a1b2c3..."}`. **Copy the token** — every
call below needs it.

**Login (do this again anytime to get a fresh token):**
```bash
curl -i -X POST https://api.bookmark.slowatcoding.com/api/login.php \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"SomePassword123","device_name":"curl test 2"}'
```

**Pull (full sync, should return your seeded "Unsorted" category):**
```bash
TOKEN="paste your token here"
curl -i "https://api.bookmark.slowatcoding.com/api/sync/pull.php?since=0" \
  -H "Authorization: Bearer $TOKEN"
```
Expect a 200 with `"categories":{"upserts":[{"id":"unsorted", ...}], ...}`.

**Push (create a bookmark and a category):**
```bash
curl -i -X POST https://api.bookmark.slowatcoding.com/api/sync/push.php \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "categories": [{"id":"11111111-1111-1111-1111-111111111111","name":"Reading","color_hex":"#3366FF","icon_key":null,"is_default":false,"created_at":1700000000000,"updated_at":1700000000000}],
    "bookmarks": [{"id":"22222222-2222-2222-2222-222222222222","url":"https://example.com","original_url":"https://example.com","title":"Example","category_id":"11111111-1111-1111-1111-111111111111","created_at":1700000000000,"updated_at":1700000000000}]
  }'
```
Expect `"categories":{"applied":["1111..."],"rejected":[]}` and the same
shape for `"bookmarks"`.

**Pull again (confirm what you just pushed comes back):**
```bash
curl -i "https://api.bookmark.slowatcoding.com/api/sync/pull.php?since=0" \
  -H "Authorization: Bearer $TOKEN"
```
Your "Reading" category and "Example" bookmark should now be in the response.

**Upload a thumbnail** (use any small `.webp` file you have; if you don't
have one handy, this step can wait until you have a real one from the app):
```bash
curl -i -X POST https://api.bookmark.slowatcoding.com/api/thumbnails/upload.php \
  -H "Authorization: Bearer $TOKEN" \
  -F "bookmark_id=22222222-2222-2222-2222-222222222222" \
  -F "file=@/path/to/test.webp"
```
Expect `201` and `{"url":"https://api.bookmark.slowatcoding.com/uploads/thumbnails/1/22222222-....webp"}`.
Paste that URL into a browser — the image should load directly.

**Delete via push (confirm it shows up as a tombstone on the next pull):**
```bash
curl -i -X POST https://api.bookmark.slowatcoding.com/api/sync/push.php \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"deleted_bookmark_ids":[{"id":"22222222-2222-2222-2222-222222222222","deleted_at":1700000100000}]}'

curl -i "https://api.bookmark.slowatcoding.com/api/sync/pull.php?since=0" \
  -H "Authorization: Bearer $TOKEN"
```
The second call's `"bookmarks":{"deletes":[...]}` should now include that id.

**Forgot password** (only if you configured SMTP in step 7):
```bash
curl -i -X POST https://api.bookmark.slowatcoding.com/api/forgot_password.php \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com"}'
```
Expect `200 {"ok":true,"message":"..."}` and an email to arrive within a
minute or two with a reset link.

If every one of these matches, the backend is fully working and you can move
on to the app. If something doesn't match, see **Troubleshooting** below.

---

## 10. Point the app at your backend and build it

The Android app is already configured to talk to
`https://api.bookmark.slowatcoding.com/`. If you used a different domain,
find `API_BASE_URL` in the app's source
(`app/src/main/java/com/bookmark/core/network/ApiJson.kt`) and update it to
match your actual subdomain, then rebuild:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
```

Install the resulting APK (`app/build/outputs/apk/debug/app-debug.apk`) on
your device(s), open Settings → the new "Sign in to sync" row, and sign in
with the same account on each device you want to keep in sync.

---

## Troubleshooting

**Every request returns a generic 500 error.**
Check `error_log` — in cPanel's File Manager, look for an `error_log` file in
the subdomain's document root (PHP writes uncaught errors there). The most
common causes: wrong PHP version (see step 5), or wrong DB credentials in
`config.php` (see step 4).

**`401 UNAUTHORIZED` even with a token you just got from login.**
Some shared-hosting Apache/PHP-FPM configurations strip the `Authorization`
header before PHP ever sees it. The backend already has a fallback for this
(`X-Auth-Token` header, and `REDIRECT_HTTP_AUTHORIZATION`), but if you're
calling the API directly with a tool that only sets `Authorization`, try
adding `-H "X-Auth-Token: $TOKEN"` alongside it in your `curl` calls, or ask
MilesWeb support to confirm `Authorization` headers reach PHP-FPM on your
plan (this is a one-line config change on their end if not).

**`400` on every request, or a redirect loop.**
This means the HTTPS check thinks the request isn't secure. Confirm the SSL
certificate is issued (step 6) and that you're calling `https://`, not
`http://`.

**Import in phpMyAdmin fails or times out.**
`schema.sql` is small, so this almost always means you selected the wrong
database first, or your hosting plan's import size limit is unusually low —
try importing again after confirming step 2.2's database is selected in the
left sidebar before clicking Import.

**Password-reset emails never arrive.**
Double check the SMTP values in step 7 exactly match what cPanel's "Connect
Devices" screen shows for that mailbox — a wrong port/encryption combination
is the most common mistake (587 pairs with `tls`, 465 pairs with `ssl`).
Check the mailbox's own webmail (cPanel → Webmail) to see if MilesWeb
generated any bounce/error messages. As a fallback, everything else in the
app works without this — it only blocks the "forgot password" flow.

**A thumbnail uploads successfully but the returned URL 404s.**
Confirm `APP_BASE_URL` in `config.php` exactly matches your real subdomain
(no typo, correct `https://`, no trailing slash) — this value is what gets
stitched into every returned URL.
