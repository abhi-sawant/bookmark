import { useMemo } from 'react'
import { queryTermsFor } from '@/core/util/highlight'
import { categoryOf, useData } from '@/data/repo'
import { useDebounced } from '@/app/hooks'
import { SearchResultRow } from '@/components/BookmarkCards'
import { useBookmarkHandlers } from '../home/useBookmarkActions'
import { searchBookmarks } from '../home/selectors'

export function SearchResults({ query, categoryId }: { query: string; categoryId: string | null }) {
  const bookmarks = useData((s) => s.bookmarks)
  const categories = useData((s) => s.categories)
  const debounced = useDebounced(query, 150)
  const { onOpen, onActions } = useBookmarkHandlers()
  const trimmed = debounced.trim()

  const { results, ms } = useMemo(() => {
    const t0 = performance.now()
    const results = searchBookmarks(bookmarks, debounced, categoryId)
    return { results, ms: Math.max(1, Math.round(performance.now() - t0)) }
  }, [bookmarks, debounced, categoryId])
  const terms = useMemo(() => queryTermsFor(debounced), [debounced])

  if (!trimmed) {
    return (
      <div className="empty">
        <h2 className="t-empty-title" style={{ margin: 0 }}>Search your bookmarks</h2>
        <p>Titles, sites and descriptions &mdash; as you type.</p>
      </div>
    )
  }
  if (results.length === 0) {
    return (
      <div className="empty">
        <h2 className="t-empty-title" style={{ margin: 0 }}>No matches</h2>
        <p>Try a different word, or clear the category filter.</p>
      </div>
    )
  }
  return (
    <>
      <div className="t-caption muted" style={{ padding: '4px 20px' }} aria-live="polite">{`${results.length} ${results.length === 1 ? 'result' : 'results'} · ${ms}ms`}</div>
      <div className="list-stack" style={{ padding: '4px 16px 24px' }}>
        {results.map((b) => (
          <SearchResultRow key={b.id} bookmark={b} category={categoryOf(b, categories)} terms={terms} onOpen={onOpen} onActions={onActions} />
        ))}
      </div>
    </>
  )
}
