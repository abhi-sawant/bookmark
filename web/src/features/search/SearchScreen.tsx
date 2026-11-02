import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useUi } from '@/app/uiStore'
import { NO_FILTERS } from '../home/selectors'
import { IconButton } from '@/components/ui'
import { Icon } from '@/components/Icon'
import { CategoryChips } from '../home/CategoryChips'
import { rememberSearch } from '@/app/recentSearches'
import { SearchFilterChips } from './SearchFilterChips'
import { SearchResults } from './SearchResults'

/** Phone search: a full screen with its own back arrow; every visit starts with an empty query. */
export function SearchScreen() {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const filter = useUi((s) => s.filterCategory)
  const setUi = useUi((s) => s.set)
  const input = useRef<HTMLInputElement>(null)
  useEffect(() => input.current?.focus(), [])
  // Chips are per visit, like the query.
  useEffect(() => () => useUi.getState().set('searchFilters', NO_FILTERS), [])

  return (
    <div className="screen search-screen">
      <div className="search-top">
        <IconButton icon="arrowBack" label="Back" onClick={() => navigate(-1)} />
        <div className="search-field panel">
          <input
            ref={input}
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && rememberSearch(query)}
            placeholder="Search bookmarks"
            aria-label="Search bookmarks"
            enterKeyHint="search"
            autoComplete="off"
          />
          {query ? (
            <button type="button" aria-label="Clear search" onClick={() => { setQuery(''); input.current?.focus() }}>
              <Icon name="closeFilled" size={18} />
            </button>
          ) : null}
        </div>
      </div>
      <div style={{ paddingBottom: 6 }}>
        <CategoryChips selected={filter} onSelect={(id) => setUi('filterCategory', id)} showCounts={false} />
      </div>
      <div style={{ paddingBottom: 10 }}>
        <SearchFilterChips />
      </div>
      <SearchResults query={query} categoryId={filter} onUseRecent={setQuery} />
    </div>
  )
}
