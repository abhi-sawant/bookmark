/**
 * Tag rules. Port of android/.../core/util/Tags.kt -- keep the two identical,
 * both apps push to the same account.
 */
export const TAG_MAX_COUNT = 10
export const TAG_MAX_LENGTH = 30
/** Room in an input field for the maximum tags plus separators. */
export const TAG_INPUT_MAX = 400

/** Trimmed, `#`-less, lower-case, single-spaced, de-duplicated and capped, in input order. */
export function normalizeTags(raw: Iterable<string>): string[] {
  const out: string[] = []
  for (const r of raw) {
    const tag = r
      .trim()
      .replace(/^#+/, '')
      .replace(/\s+/g, ' ')
      .toLowerCase()
      .slice(0, TAG_MAX_LENGTH)
      .trim()
    if (tag && !out.includes(tag)) out.push(tag)
    if (out.length === TAG_MAX_COUNT) break
  }
  return out
}

/** Splits free text on commas and new lines, then normalises it. */
export const parseTags = (input: string): string[] => normalizeTags(input.split(/[,\n]/))

/** If [query] is a `#tag` search, the normalised tag; otherwise null. */
export function tagQuery(query: string): string | null {
  const q = query.trim()
  if (!q.startsWith('#')) return null
  return normalizeTags([q])[0] ?? null
}
