package com.bookmark.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    @Test fun newerPatchMinorMajor() {
        assertTrue(isNewer("2.0.1", "2.0.0"))
        assertTrue(isNewer("2.1.0", "2.0.9"))
        assertTrue(isNewer("3.0.0", "2.9.9"))
    }

    @Test fun comparesNumericallyNotLexically() = assertTrue(isNewer("2.10.0", "2.9.1"))

    @Test fun equalOrOlderIsNotNewer() {
        assertFalse(isNewer("2.0.0", "2.0.0"))
        assertFalse(isNewer("2.0", "2.0.0"))
        assertFalse(isNewer("1.9.0", "2.0.0"))
    }
}
