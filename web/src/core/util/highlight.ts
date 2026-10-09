/**
 * Search term + highlight helpers. Port of android/.../search/HighlightSpans.kt,
 * and the terms double as the web search matcher (token-prefix AND).
 */

export function queryTermsFor(query: string): string[] {
  return query
    .trim()
    .split(/\s+/)
    .map((t) => t.replace(/"/g, ''))
    .filter((t) => t.trim() !== '')
}

export interface Range {
  start: number
  /** Inclusive, matching Kotlin's IntRange. */
  end: number
}

/** Whole `\w+` words starting with any term, case-insensitively. */
export function findHighlightRanges(text: string, terms: string[]): Range[] {
  const lower = terms.map((t) => t.toLowerCase()).filter((t) => t.trim() !== '')
  if (lower.length === 0) return []
  const ranges: Range[] = []
  for (const m of text.matchAll(/\w+/g)) {
    const word = m[0].toLowerCase()
    if (lower.some((t) => word.startsWith(t))) {
      ranges.push({ start: m.index, end: m.index + m[0].length - 1 })
    }
  }
  return ranges
}
