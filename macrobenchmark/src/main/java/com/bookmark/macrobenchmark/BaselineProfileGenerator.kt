package com.bookmark.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates `app/src/release/generated/baselineProfiles/baseline-prof.txt`
 * by walking the app's critical user journeys once, with tracing on, so the
 * profile installer can AOT-compile the hot path instead of waiting for the
 * platform's own JIT sampling (spec 9's single highest-leverage item).
 *
 * The journey is intentionally tolerant of an empty or already-populated
 * database on the generating device -- it is not an assertion of behaviour
 * (the unit/instrumented suites already cover that), just a walk through the
 * screens real usage hits most: Home, a scroll, the detail sheet, and Search.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(packageName = PACKAGE_NAME) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        startActivityAndWait()

        // Home: either view mode may be showing depending on device state.
        val list = device.wait(Until.findObject(By.res(PACKAGE_NAME, "home_list")), TIMEOUT_MS)
        val grid = list ?: device.wait(Until.findObject(By.res(PACKAGE_NAME, "home_grid")), TIMEOUT_MS)
        grid?.let { container ->
            container.fling(androidx.test.uiautomator.Direction.DOWN)
            container.fling(androidx.test.uiautomator.Direction.UP)
        }

        // Detail sheet: open the first item, if any exist, then back out.
        device.findObject(By.res(PACKAGE_NAME, "bookmark_item"))?.let { item ->
            item.click()
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
        }

        // Search: open it, type a query, then back out to Home.
        device.findObject(By.res(PACKAGE_NAME, "search_button"))?.let { searchButton ->
            searchButton.click()
            device.wait(Until.findObject(By.res(PACKAGE_NAME, "search_field")), TIMEOUT_MS)
                ?.let { field ->
                    field.click()
                    field.text = "book"
                    device.waitForIdle()
                }
            device.pressBack()
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.bookmark"
        const val TIMEOUT_MS = 5_000L
    }
}
