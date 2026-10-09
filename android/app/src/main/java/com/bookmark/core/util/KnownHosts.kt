package com.bookmark.core.util

/**
 * Display names for hosts whose domain does not read as their name --
 * `news.ycombinator.com` is "Hacker News", not "Ycombinator" (spec 8.2 step 4).
 *
 * Deliberately small and hand-kept. Anything absent falls through to the
 * title-cased domain, which is a perfectly good label for most sites.
 */
object KnownHosts {

    private val NAMES = mapOf(
        "ycombinator.com" to "Hacker News",
        "news.ycombinator.com" to "Hacker News",
        "github.com" to "GitHub",
        "gitlab.com" to "GitLab",
        "stackoverflow.com" to "Stack Overflow",
        "stackexchange.com" to "Stack Exchange",
        "developer.android.com" to "Android Developers",
        "developer.mozilla.org" to "MDN Web Docs",
        "youtube.com" to "YouTube",
        "youtu.be" to "YouTube",
        "vimeo.com" to "Vimeo",
        "twitter.com" to "Twitter",
        "x.com" to "X",
        "reddit.com" to "Reddit",
        "medium.com" to "Medium",
        "substack.com" to "Substack",
        "wikipedia.org" to "Wikipedia",
        "arxiv.org" to "arXiv",
        "nytimes.com" to "The New York Times",
        "theguardian.com" to "The Guardian",
        "bbc.co.uk" to "BBC",
        "bbc.com" to "BBC",
        "washingtonpost.com" to "The Washington Post",
        "theverge.com" to "The Verge",
        "arstechnica.com" to "Ars Technica",
        "wired.com" to "WIRED",
        "techcrunch.com" to "TechCrunch",
        "economist.com" to "The Economist",
        "ft.com" to "Financial Times",
        "bloomberg.com" to "Bloomberg",
        "reuters.com" to "Reuters",
        "npr.org" to "NPR",
        "seriouseats.com" to "Serious Eats",
        "bonappetit.com" to "Bon Appétit",
        "nrk.no" to "NRK",
        "are.na" to "Are.na",
        "behance.net" to "Behance",
        "dribbble.com" to "Dribbble",
        "figma.com" to "Figma",
        "notion.so" to "Notion",
        "linear.app" to "Linear",
        "linkedin.com" to "LinkedIn",
        "instagram.com" to "Instagram",
        "facebook.com" to "Facebook",
        "threads.net" to "Threads",
        "bsky.app" to "Bluesky",
        "mastodon.social" to "Mastodon",
        "spotify.com" to "Spotify",
        "open.spotify.com" to "Spotify",
        "soundcloud.com" to "SoundCloud",
        "apple.com" to "Apple",
        "google.com" to "Google",
        "microsoft.com" to "Microsoft",
        "amazon.com" to "Amazon",
        "goodreads.com" to "Goodreads",
        "imdb.com" to "IMDb",
        "ogp.me" to "Open Graph Protocol",
        "web.dev" to "web.dev",
        "css-tricks.com" to "CSS-Tricks",
        "smashingmagazine.com" to "Smashing Magazine",
        "kotlinlang.org" to "Kotlin",
        "android.com" to "Android",
    )

    /** The curated display name for a host, or null to fall through. */
    fun displayName(host: String?): String? {
        if (host.isNullOrBlank()) return null
        val normalized = host.lowercase().removePrefix("www.")
        return NAMES[normalized]
            ?: NAMES[UrlNormalizer.registrableDomain("https://$normalized")]
    }
}
