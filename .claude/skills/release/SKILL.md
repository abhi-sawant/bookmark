---
name: release
description: Build a Bookmark release folder (signed APK, web zip, backend zip when changed, checksums, release notes) for a given version.
disable-model-invocation: true
argument-hint: <version, e.g. 2.1.0>
---

Create the release `$ARGUMENTS` for Bookmark. Work from the repo root. The Android project is in `android/` (run Gradle from there), the web app in `web/` (pnpm), the PHP API in `backend/`. The app version lives in `android/app/build.gradle.kts` (`versionName` and `versionCode`); there is no `VERSION` file. The web app carries the same version in `web/package.json`.

## 0. Validate

- `$ARGUMENTS` must be SemVer `X.Y.Z` (strip a leading `v`). If missing or malformed, ask for it and stop.
- It must be greater than the current `versionName`, unless it already equals it (bump done by hand). If `releases/vX.Y.Z/` already exists, ask before overwriting.
- Confirm `android/keystore.properties` exists and the `storeFile` it points to exists, otherwise the APK would be unsigned: stop and tell the user. Never print the passwords.

## 1. Work out what changed

Take the latest changes in the working tree and since the last tag/release: `git status`, `git diff` and `git log` since the last tag (tags are bare `X.Y.Z`; fall back to the last 20 commits if none). Read the diffs of user-visible changes (features, UI, fixes, sync behaviour) across `android/`, `web/` and `backend/`; ignore pure refactors and docs unless they matter to users or self-hosters. Uncommitted changes are part of this release; do not commit them. Note whether `backend/` changed, including `backend/sql/schema.sql` (a schema change needs a migration note for the host).

## 2. Bump and document

- In `android/app/build.gradle.kts` set `versionName = "X.Y.Z"` and increment `versionCode` by 1.
- Set `"version"` in `web/package.json` to `X.Y.Z` (keep the two in step; leave the lockfile alone).
- Add a `## X.Y.Z — <today's date, YYYY-MM-DD>` section at the top of `CHANGELOG.md`, above the previous release (replace an `## Unreleased` section if one exists, folding its entries in), using `### Added / Changed / Fixed` as needed. Write for users, in the existing voice, and say which platform a change affects (Android, web, or both). Call out backend/schema changes and any required server steps from `docs/MILESWEB_DEPLOYMENT_GUIDE.md`.

## 3. Verify and build

Run, stopping on any failure and reporting it:

```bash
(cd web && pnpm install --frozen-lockfile && pnpm test && pnpm build)
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
(cd android && ./gradlew :app:testDebugUnitTest :app:assembleRelease)
```

Do not run the macrobenchmark or androidTest tasks (they need a device); mention in the summary if instrumented tests changed and were therefore not run.

## 4. Assemble `releases/vX.Y.Z/` (gitignored; add `/releases` to `.gitignore` if it isn't there)

- `bookmark-X.Y.Z.apk`: copy `android/app/build/outputs/apk/release/app-release.apk`.
- `bookmark-web-X.Y.Z.zip`: always included. Zip the *contents* of `web/dist/` (files at the zip root, including the hidden `.htaccess`, no `.DS_Store`): `cd web/dist && zip -qr ../../releases/vX.Y.Z/bookmark-web-X.Y.Z.zip . -x '.DS_Store'`. Confirm `.htaccess` and `index.html` are at the zip root (`unzip -l`).
- `bookmark-backend-X.Y.Z.zip`: only if `backend/` changed since the last tag, or on a first release. Zip the *contents* of `backend/` (files at the zip root, not the folder itself, per the deployment guide), excluding `.DS_Store`: `cd backend && zip -qr ../releases/vX.Y.Z/bookmark-backend-X.Y.Z.zip . -x '.DS_Store'`. `config.php` is placeholders only; confirm it still contains no real credentials before zipping.
- `SHA256SUMS.txt`: `shasum -a 256` of every artifact in the folder, run inside the folder, two-space separator, bare filenames.
- `RELEASE_NOTES.md`: model it on the previous `releases/v*/RELEASE_NOTES.md` — title `# Bookmark X.Y.Z — <headline>`, plain-language sections for what's new (grouped by Android / web / backend where it helps), a Downloads table listing every artifact (APK, web zip, backend zip if included) with a one-line "how to install/deploy" each (web zip and backend zip point at `docs/MILESWEB_DEPLOYMENT_GUIDE.md`, sections 11 and 3), and the APK signing certificate line. Read the cert with `~/Library/Android/sdk/build-tools/<latest>/apksigner verify --print-certs <apk>` (use the newest build-tools installed; `JAVA_HOME` as above) and format the SHA-256 as colon-separated lowercase hex. If a previous `releases/v*/RELEASE_NOTES.md` exists, the cert must equal it; if not, warn loudly because Android will refuse the update. Also check `versionName`/`versionCode` in the APK match via `aapt2 dump badging` or the build's `output-metadata.json`.

## 5. Report

Summarise: version and versionCode, folder contents with sizes, the cert check, and the changelog entry. Do NOT commit, tag, push, or create a GitHub release; remind the user of the remaining steps (commit the bump, tag `X.Y.Z`, attach the artifacts to a GitHub Release using the CHANGELOG section as notes, upload `web/dist` contents to the web docroot, and deploy the backend zip if it was included).
