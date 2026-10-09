import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useUi } from '@/app/uiStore'
import { IconButton } from '@/components/ui'
import { Icon } from '@/components/Icon'
import { CategoryChips } from '../home/CategoryChips'
import { SearchResults } from './SearchResults'

/** Phone search: a full screen with its own back arrow; every visit starts with an empty query. */
export function SearchScreen() {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const filter = useUi((s) => s.filterCategory)
  const setUi = useUi((s) => s.set)
  const input = useRef<HTMLInputElement>(null)
  useEffect(() => input.current?.focus(), [])

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
      <div style={{ paddingBottom: 10 }}>
        <CategoryChips selected={filter} onSelect={(id) => setUi('filterCategory', id)} showCounts={false} />
      </div>
      <SearchResults query={query} categoryId={filter} />
    </div>
  )
}
