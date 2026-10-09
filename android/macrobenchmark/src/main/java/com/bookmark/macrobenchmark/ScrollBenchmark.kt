package com.bookmark.macrobenchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 9: "zero jank frames over a 500-item scroll." Runs against
 * `benchmarkRelease` (R8-shrunk, non-debuggable -- see app/build.gradle.kts),
 * which is the only build type where `EXTRA_BENCHMARK_SEED_COUNT` does
 * anything (BenchmarkSeed.kt), so the 500 items exist without depending on
 * manually populated data or a live network fetch.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollHomeGrid() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            val intent = Intent(Intent.ACTION_MAIN)
                .setClassName(PACKAGE_NAME, "$PACKAGE_NAME.MainActivity")
                .putExtra("com.bookmark.EXTRA_BENCHMARK_SEED_COUNT", SEED_COUNT)
            startActivityAndWait(intent)
        },
    ) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val grid = device.wait(Until.findObject(By.res(PACKAGE_NAME, "home_grid")), TIMEOUT_MS)
            ?: device.wait(Until.findObject(By.res(PACKAGE_NAME, "home_list")), TIMEOUT_MS)
        requireNotNull(grid) { "Home grid/list never appeared -- seeding likely failed" }

        repeat(10) {
            grid.fling(Direction.DOWN)
        }
        repeat(10) {
            grid.fling(Direction.UP)
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.bookmark"
        const val SEED_COUNT = 500
        const val TIMEOUT_MS = 30_000L
    }
}
