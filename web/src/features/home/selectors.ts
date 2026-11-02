import { UNSORTED_ID, type Bookmark, type Category, type SortOrder } from '@/data/models'
import { queryTermsFor } from '@/core/util/highlight'
import { tagQuery } from '@/core/util/tags'

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

/** Quick narrowing chips on Search. Mirrors Android's SearchFilters. */
export interface SearchFilters {
  pinned: boolean
  /** "No category": bookmarks still in Unsorted. */
  unsorted: boolean
  /** Saved in the last seven days. */
  thisWeek: boolean
}
export const NO_FILTERS: SearchFilters = { pinned: false, unsorted: false, thisWeek: false }
export const hasActiveFilters = (f: SearchFilters): boolean => f.pinned || f.unsorted || f.thisWeek
const WEEK_MS = 7 * 24 * 60 * 60 * 1000

export function applyFilters(list: Bookmark[], f: SearchFilters, now = Date.now()): Bookmark[] {
  if (!hasActiveFilters(f)) return list
  return list.filter(
    (b) => (!f.pinned || b.isPinned) && (!f.unsorted || b.categoryId === UNSORTED_ID) && (!f.thisWeek || b.createdAt >= now - WEEK_MS),
  )
}

const MAX_RECENT_SEARCHES = 8

/** The last few searches, newest first, case-insensitively de-duplicated. */
export function addRecentSearch(current: string[], query: string): string[] {
  const q = query.trim()
  if (!q) return current
  return [q, ...current.filter((r) => r.toLowerCase() !== q.toLowerCase())].slice(0, MAX_RECENT_SEARCHES)
}

/** Every query term must prefix-match some token of title, description, site name or url. */
export function searchBookmarks(
  list: Bookmark[],
  query: string,
  categoryId: string | null,
  filters: SearchFilters = NO_FILTERS,
  now = Date.now(),
): Bookmark[] {
  const hits = searchText(list, query, categoryId, hasActiveFilters(filters))
  return applyFilters(hits, filters, now)
}

function searchText(list: Bookmark[], query: string, categoryId: string | null, browseWhenEmpty: boolean): Bookmark[] {
  // With no text, an active chip alone browses everything it matches (pinned first, then newest).
  if (browseWhenEmpty && query.trim() === '') {
    return list
      .filter((b) => !categoryId || b.categoryId === categoryId)
      .sort((a, b) => Number(b.isPinned) - Number(a.isPinned) || b.createdAt - a.createdAt)
  }
  // "#tag" lists bookmarks carrying exactly that tag.
  const tag = tagQuery(query)
  if (query.trim().startsWith('#')) {
    if (!tag) return []
    return list
      .filter((b) => (!categoryId || b.categoryId === categoryId) && b.tags.includes(tag))
      .sort((a, b) => Number(b.isPinned) - Number(a.isPinned) || b.createdAt - a.createdAt)
  }
  const terms = queryTermsFor(query).flatMap((t) => tokens(t))
  if (terms.length === 0) return []
  const hits = list.filter((b) => {
    if (categoryId && b.categoryId !== categoryId) return false
    const toks = [...tokens(b.title), ...tokens(b.description), ...tokens(b.siteName), ...tokens(b.url), ...b.tags.flatMap(tokens)]
    return terms.every((t) => toks.some((k) => k.startsWith(t)))
  })
  // Pinned first, then newest. No relevance ranking, like Android.
  return hits.sort((a, b) => Number(b.isPinned) - Number(a.isPinned) || b.createdAt - a.createdAt)
}
