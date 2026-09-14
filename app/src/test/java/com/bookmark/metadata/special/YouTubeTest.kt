package com.bookmark.metadata.special

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeTest {

    private val id = "dQw4w9WgXcQ"

    @Test
    fun `extracts the id from a watch url`() {
        assertEquals(id, YouTube.videoId("https://www.youtube.com/watch?v=$id"))
    }

    @Test
    fun `extracts the id when other query params surround it`() {
        assertEquals(id, YouTube.videoId("https://www.youtube.com/watch?list=PL123&v=$id&index=2"))
    }

    @Test
    fun `extracts the id from a short link`() {
        assertEquals(id, YouTube.videoId("https://youtu.be/$id"))
    }

    @Test
    fun `extracts the id from shorts, embed, live and v paths`() {
        assertEquals(id, YouTube.videoId("https://www.youtube.com/shorts/$id"))
        assertEquals(id, YouTube.videoId("https://www.youtube.com/embed/$id"))
        assertEquals(id, YouTube.videoId("https://www.youtube.com/live/$id"))
        assertEquals(id, YouTube.videoId("https://www.youtube.com/v/$id"))
    }

    @Test
    fun `handles the mobile, music and nocookie hosts`() {
        assertEquals(id, YouTube.videoId("https://m.youtube.com/watch?v=$id"))
        assertEquals(id, YouTube.videoId("https://music.youtube.com/watch?v=$id"))
        assertEquals(id, YouTube.videoId("https://www.youtube-nocookie.com/embed/$id"))
    }

    @Test
    fun `rejects a non-youtube host`() {
        assertNull(YouTube.videoId("https://vimeo.com/watch?v=$id"))
        // A lookalike domain must not be treated as YouTube.
        assertNull(YouTube.videoId("https://youtube.com.evil.example/watch?v=$id"))
    }

    @Test
    fun `rejects a malformed id`() {
        assertNull(YouTube.videoId("https://www.youtube.com/watch?v=tooshort"))
        assertNull(YouTube.videoId("https://www.youtube.com/watch?v=waaaaaaaaaytoolong"))
        assertNull(YouTube.videoId("https://www.youtube.com/watch"))
    }

    @Test
    fun `returns a channel page as not a video`() {
        assertNull(YouTube.videoId("https://www.youtube.com/@somechannel"))
        assertNull(YouTube.videoId("https://www.youtube.com/"))
    }

    @Test
    fun `offers maxres before hqdefault`() {
        assertEquals(
            listOf(
                "https://i.ytimg.com/vi/$id/maxresdefault.jpg",
                "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            ),
            YouTube.thumbnailCandidates(id),
        )
    }
}
