/**
 * Pulls URLs out of shared text. Port of android/.../share/UrlExtractor.kt.
 * Used by the PWA share target and by the Add sheet when text is pasted.
 */

const WEB_URL = new RegExp(
  String.raw`(?:(?:https?|ftp)://)?` +
    String.raw`(?:[\w-]+(?::[^\s@]*)?@)?` +
    String.raw`(?:[\w-]+\.)+[a-zA-Z]{2,}` +
    String.raw`(?::\d{1,5})?` +
    String.raw`(?:/[^\s<>"'\)\]]*)?`,
  'gi',
)

/** Trailing punctuation that is almost always sentence, not URL. */
const TRAILING_JUNK = new Set(['.', ',', ';', ':', '!', '?', ')', ']', '}', '\u201d', '"', "'"])

export interface ExtractedShare {
  /** First match, or null when the text held no URL at all. */
  primaryUrl: string | null
  /** Further matches. */
  otherUrls: string[]
  /** The shared title, held as a fallback title candidate. */
  subjectTitle: string | null
  /** The text as shared. */
  rawText: string
}

function trimJunk(s: string): string {
  let end = s.length
  while (end > 0 && TRAILING_JUNK.has(s[end - 1])) end--
  return s.slice(0, end)
}

/** Guards against decimals and version strings ("v1.2", "3.14") matching as hosts. */
function looksLikeHost(candidate: string): boolean {
  const schemeAt = candidate.indexOf('://')
  const withoutScheme = schemeAt >= 0 ? candidate.slice(schemeAt + 3) : candidate
  let host = withoutScheme.split('/')[0].split(':')[0]
  host = host.slice(host.lastIndexOf('@') + 1)
  const labels = host.split('.')
  if (labels.length < 2) return false
  const tld = labels[labels.length - 1]
  return tld.length >= 2 && /^\p{L}+$/u.test(tld) && labels.every((l) => l !== '')
}

export function extract(text: string | null | undefined, subject?: string | null): ExtractedShare {
  const raw = (text ?? '').trim()
  const found = Array.from(raw.matchAll(WEB_URL), (m) => trimJunk(m[0]))
    .filter((u) => u.trim() !== '' && looksLikeHost(u))
  const matches = [...new Set(found)]

  const trimmedSubject = subject?.trim()
  const subjectTitle =
    trimmedSubject && !matches.some((u) => u.toLowerCase() === trimmedSubject.toLowerCase())
      ? trimmedSubject
      : null

  return {
    primaryUrl: matches[0] ?? null,
    otherUrls: matches.slice(1),
    subjectTitle,
    rawText: raw,
  }
}
