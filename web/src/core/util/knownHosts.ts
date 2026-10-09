import { registrableDomain } from './urlNormalizer'

/**
 * Display names for hosts whose domain does not read as their name --
 * `news.ycombinator.com` is "Hacker News", not "Ycombinator". Port of
 * android/.../core/util/KnownHosts.kt; keep the two tables in step.
 */
const NAMES: Record<string, string> = {
  'ycombinator.com': 'Hacker News',
  'news.ycombinator.com': 'Hacker News',
  'github.com': 'GitHub',
  'gitlab.com': 'GitLab',
  'stackoverflow.com': 'Stack Overflow',
  'stackexchange.com': 'Stack Exchange',
  'developer.android.com': 'Android Developers',
  'developer.mozilla.org': 'MDN Web Docs',
  'youtube.com': 'YouTube',
  'youtu.be': 'YouTube',
  'vimeo.com': 'Vimeo',
  'twitter.com': 'Twitter',
  'x.com': 'X',
  'reddit.com': 'Reddit',
  'medium.com': 'Medium',
  'substack.com': 'Substack',
  'wikipedia.org': 'Wikipedia',
  'arxiv.org': 'arXiv',
  'nytimes.com': 'The New York Times',
  'theguardian.com': 'The Guardian',
  'bbc.co.uk': 'BBC',
  'bbc.com': 'BBC',
  'washingtonpost.com': 'The Washington Post',
  'theverge.com': 'The Verge',
  'arstechnica.com': 'Ars Technica',
  'wired.com': 'WIRED',
  'techcrunch.com': 'TechCrunch',
  'economist.com': 'The Economist',
  'ft.com': 'Financial Times',
  'bloomberg.com': 'Bloomberg',
  'reuters.com': 'Reuters',
  'npr.org': 'NPR',
  'seriouseats.com': 'Serious Eats',
  'bonappetit.com': 'Bon Appétit',
  'nrk.no': 'NRK',
  'are.na': 'Are.na',
  'behance.net': 'Behance',
  'dribbble.com': 'Dribbble',
  'figma.com': 'Figma',
  'notion.so': 'Notion',
  'linear.app': 'Linear',
  'linkedin.com': 'LinkedIn',
  'instagram.com': 'Instagram',
  'facebook.com': 'Facebook',
  'threads.net': 'Threads',
  'bsky.app': 'Bluesky',
  'mastodon.social': 'Mastodon',
  'spotify.com': 'Spotify',
  'open.spotify.com': 'Spotify',
  'soundcloud.com': 'SoundCloud',
  'apple.com': 'Apple',
  'google.com': 'Google',
  'microsoft.com': 'Microsoft',
  'amazon.com': 'Amazon',
  'goodreads.com': 'Goodreads',
  'imdb.com': 'IMDb',
  'ogp.me': 'Open Graph Protocol',
  'web.dev': 'web.dev',
  'css-tricks.com': 'CSS-Tricks',
  'smashingmagazine.com': 'Smashing Magazine',
  'kotlinlang.org': 'Kotlin',
  'android.com': 'Android',
}

const own = (key: string): string | undefined =>
  Object.prototype.hasOwnProperty.call(NAMES, key) ? NAMES[key] : undefined

/** The curated display name for a host, or null to fall through. */
export function displayName(host: string | null | undefined): string | null {
  if (!host || !host.trim()) return null
  const normalized = host.toLowerCase().replace(/^www\./, '')
  const direct = own(normalized)
  if (direct !== undefined) return direct
  const registrable = registrableDomain(`https://${normalized}`)
  return (registrable && own(registrable)) ?? null
}
