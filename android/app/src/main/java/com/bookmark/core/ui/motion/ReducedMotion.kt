package com.bookmark.core.ui.motion

import android.database.ContentObserver
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the system-wide "Remove animations" accessibility setting is on
 * (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`). Unlike View-system
 * animators, Compose's animation APIs (`animate*AsState`, `AnimatedVisibility`,
 * shared-element `BoundsTransform`) do not consult this automatically -- any
 * custom motion (spec 10: "Reduced-motion setting respected") must check it
 * explicitly and collapse to an instant change or a fast crossfade.
 */
@Composable
fun rememberReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    var scale by remember {
        mutableFloatStateOf(
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        )
    }
    DisposableEffect(context) {
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                scale = Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            }
        }
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return scale == 0f
}
