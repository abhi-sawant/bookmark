package com.bookmark.linkcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkStatusRulesTest {
    @Test
    fun `success and redirect codes are ok`() {
        for (code in listOf(200, 204, 301, 302, 399)) assertEquals(LinkStatus.OK, LinkStatusRules.fromHttpCode(code))
    }

    @Test
    fun `only 404 and 410 are broken`() {
        assertEquals(LinkStatus.BROKEN, LinkStatusRules.fromHttpCode(404))
        assertEquals(LinkStatus.BROKEN, LinkStatusRules.fromHttpCode(410))
        for (code in listOf(400, 401, 403, 429, 500, 503)) assertEquals(LinkStatus.ERROR, LinkStatusRules.fromHttpCode(code))
    }

    @Test
    fun `head is retried with get unless it already gave an answer`() {
        assertTrue(LinkStatusRules.headNeedsGetRetry(405))
        assertTrue(LinkStatusRules.headNeedsGetRetry(403))
        assertTrue(LinkStatusRules.headNeedsGetRetry(500))
        assertFalse(LinkStatusRules.headNeedsGetRetry(200))
        assertFalse(LinkStatusRules.headNeedsGetRetry(404))
        assertFalse(LinkStatusRules.headNeedsGetRetry(410))
    }
}
