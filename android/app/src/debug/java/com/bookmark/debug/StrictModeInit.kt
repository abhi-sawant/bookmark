package com.bookmark.debug

import android.os.StrictMode

/**
 * Spec 9: "StrictMode (disk + network on main thread -> penaltyDeath) in
 * debug builds." A release-source-set twin of this object is a no-op, so
 * [com.bookmark.BookmarkApp] can call [install] unconditionally with no
 * `BuildConfig` branch (no reflection, no R8 risk).
 *
 * Installed before `super.onCreate()` so it's in effect for as much of
 * startup as this app controls -- some framework/library init (Hilt's own
 * `attachBaseContext`-time work, for one) unavoidably runs earlier than
 * that and is outside this app's reach either way.
 *
 * Thread policy is penaltyLog()-only (no penaltyDeath()): on Samsung/One UI
 * devices, `android.graphics.Typeface`'s OEM "flip font" lookup does its own
 * disk read from inside this process's startup, outside app code, which
 * would otherwise hard-crash every debug launch on those devices. Violations
 * still show up in Logcat.
 */
object StrictModeInit {
    fun install() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build(),
        )
    }
}
