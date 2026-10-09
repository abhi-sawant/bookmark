import { type Bookmark, type Category, type SortOrder } from '@/data/models'
import { queryTermsFor } from '@/core/util/highlight'

export const SORT_LABELS: Record<SortOrder, { short: string; menu: string }> = {
  NEWEST: { short: 'Newest', menu: 'Sort: Newest' },
  OLDEST: { short: 'Oldest', menu: 'Sort: Oldest' },
  TITLE_AZ: { short: 'A–Z', menu: 'Sort: Title A–Z' },
  CATEGORY: { short: 'Category', menu: 'Sort: Category' },
}

/** Pinned first, then the chosen order (matches BookmarkDao's four ORDER BY clauses). */
export function sortBookmarks(list: Bookmark[], order: SortOrder, categories: Category[]): Bookmark[] {
  const catOrder = new Map(categories.map((c) => [c.id, c.sortOrder]))
  const pinned = (b: Bookmark) => (b.isPinned ? 0 : 1)
  const collator = new Intl.Collator(undefined, { sensitivity: 'base' })
  return [...list].sort((a, b) => {
    const p = pinned(a) - pinned(b)
    if (p) return p
    switch (order) {
      case 'OLDEST':
        return a.createdAt - b.createdAt
      case 'TITLE_AZ':
        return collator.compare(a.title, b.title)
      case 'CATEGORY':
        return (catOrder.get(a.categoryId) ?? 0) - (catOrder.get(b.categoryId) ?? 0) || b.createdAt - a.createdAt
      default:
        return b.createdAt - a.createdAt
    }
  })
}

const TOKEN_SPLIT = /[^\p{L}\p{N}]+/u

/** FTS4 "simple" tokenizer: lowercase, split on anything that is not a letter or digit. */
const tokens = (s: string | null | undefined): string[] =>
  s ? s.toLowerCase().split(TOKEN_SPLIT).filter(Boolean) : []

/** Every query term must prefix-match some token of title, description, site name or url. */
export function searchBookmarks(list: Bookmark[], query: string, categoryId: string | null): Bookmark[] {
  const terms = queryTermsFor(query).flatMap((t) => tokens(t))
  if (terms.length === 0) return []
  const hits = list.filter((b) => {
    if (categoryId && b.categoryId !== categoryId) return false
    const toks = [...tokens(b.title), ...tokens(b.description), ...tokens(b.siteName), ...tokens(b.url)]
    return terms.every((t) => toks.some((k) => k.startsWith(t)))
  })
  // Pinned first, then newest. No relevance ranking, like Android.
  return hits.sort((a, b) => Number(b.isPinned) - Number(a.isPinned) || b.createdAt - a.createdAt)
}
