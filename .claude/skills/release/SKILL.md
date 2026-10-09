---
name: release
description: Build a Bookmark release folder (signed APK, backend zip, checksums, release notes) for a given version.
disable-model-invocation: true
argument-hint: <version, e.g. 1.1.0>
---

Create the release `$ARGUMENTS` for Bookmark. Work from the repo root. The version lives in `app/build.gradle.kts` (`versionName` and `versionCode`); there is no `VERSION` file.

## 0. Validate

- `$ARGUMENTS` must be SemVer `X.Y.Z` (strip a leading `v`). If missing or malformed, ask for it and stop.
- It must be greater than the current `versionName`, unless it already equals it (bump done by hand). If `releases/vX.Y.Z/` already exists, ask before overwriting.
- Confirm `keystore.properties` exists and the `storeFile` it points to exists, otherwise the APK would be unsigned: stop and tell the user. Never print the passwords.

## 1. Work out what changed

Run `git status`, `git diff` and `git log` since the last tag (tags are bare `X.Y.Z`; fall back to the last 20 commits if none). Read the diffs of user-visible changes (features, UI, fixes, sync behaviour); ignore pure refactors unless they matter to users. Uncommitted changes are part of this release; do not commit them. Note whether `backend/` changed, including `backend/sql/schema.sql` (a schema change needs a migration note for the host).

## 2. Bump and document

- In `app/build.gradle.kts` set `versionName = "X.Y.Z"` and increment `versionCode` by 1.
- Add a `## X.Y.Z — <today's date, YYYY-MM-DD>` section at the top of `CHANGELOG.md` (create it with a `# Changelog` heading if missing), using `### Added / Changed / Fixed` as needed. Write for users. Call out backend/schema changes and any required server steps from `docs/MILESWEB_DEPLOYMENT_GUIDE.md`.

## 3. Verify and build

Stop on any failure and report it:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testReleaseUnitTest :app:assembleRelease
```

Do not run the macrobenchmark or androidTest tasks (they need a device).

## 4. Assemble `releases/vX.Y.Z/` (add `/releases` to `.gitignore` if it isn't there)

- `bookmark-X.Y.Z.apk`: copy `app/build/outputs/apk/release/app-release.apk`.
- `bookmark-backend-X.Y.Z.zip`: only if `backend/` changed since the last tag, or on a first release. Zip the *contents* of `backend/` (files at the zip root, not the folder itself, per the deployment guide), excluding `.DS_Store`: `cd backend && zip -qr ../releases/vX.Y.Z/bookmark-backend-X.Y.Z.zip . -x '.DS_Store'`. `config.php` is placeholders only; confirm it still contains no real credentials before zipping.
- `SHA256SUMS.txt`: `shasum -a 256` of the artifacts, run inside the folder, two-space separator, bare filenames.
- `RELEASE_NOTES.md`: title `# Bookmark X.Y.Z — <headline>`, plain-language sections for what's new, a Downloads table, and the APK signing certificate line. Read the cert with `~/Library/Android/sdk/build-tools/36.0.0/apksigner verify --print-certs <apk>` (use the newest build-tools installed; `JAVA_HOME` as above) and format the SHA-256 as colon-separated lowercase hex. If a previous `releases/v*/RELEASE_NOTES.md` exists, the cert must equal it; if not, warn loudly because Android will refuse the update. Also check `versionName`/`versionCode` in the APK match via `aapt2 dump badging` or the build's `output-metadata.json`.

## 5. Report

Summarise: version and versionCode, folder contents with sizes, the cert check, and the changelog entry. Do NOT commit, tag, push, or create a GitHub release; remind the user of the remaining steps (commit the bump, tag `X.Y.Z`, upload the APK, and deploy the backend zip if it was included).
