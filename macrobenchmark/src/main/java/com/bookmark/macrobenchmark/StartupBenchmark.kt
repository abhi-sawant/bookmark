package com.bookmark.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Measures cold start against spec 9's <500ms target, and quantifies the
 * baseline-profile win on this specific device by comparing [CompilationMode]s.
 * [CompilationMode.None] is the "no baseline profile" baseline; the
 * partial/full modes show what the profile buys once it's generated and
 * committed (`generateBaselineProfileGenerator` must have run at least once).
 */
@LargeTest
@RunWith(Parameterized::class)
class StartupBenchmark(private val compilationMode: CompilationMode) {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startup() = benchmarkRule.measureRepeated(
        packageName = "com.bookmark",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = compilationMode,
    ) {
        pressHome()
        startActivityAndWait()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "compilation={0}")
        fun parameters() = listOf(
            CompilationMode.None(),
            CompilationMode.Partial(),
            CompilationMode.Full(),
        )
    }
}
