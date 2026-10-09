import { useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { type SortOrder } from '@/data/models'
import { categoryOf, useData } from '@/data/repo'
import { openAdd, useUi } from '@/app/uiStore'
import { usePrefs } from '@/app/prefs'
import { useIsDesktop } from '@/app/hooks'
import { BookmarkGridCard, BookmarkListRow, MasonryGrid } from '@/components/BookmarkCards'
import { Icon } from '@/components/Icon'
import { EmptyState, IconButton, Menu, PrimaryButton, ScreenHeader } from '@/components/ui'
import { SearchResults } from '../search/SearchResults'
import { DesktopSearchBox } from '../search/DesktopSearchBox'
import { CategoryChips } from './CategoryChips'
import { SORT_LABELS, sortBookmarks } from './selectors'
import { useBookmarkHandlers } from './useBookmarkActions'

export function HomeScreen() {
  const bookmarks = useData((s) => s.bookmarks)
  const categories = useData((s) => s.categories)
  const loaded = useData((s) => s.loaded)
  const filter = useUi((s) => s.filterCategory)
  const setUi = useUi((s) => s.set)
  const query = useUi((s) => s.searchQuery)
  const { viewMode, sortOrder, set: setPref } = usePrefs()
  const desktop = useIsDesktop()
  const navigate = useNavigate()
  const { onOpen, onActions } = useBookmarkHandlers()
  const [sortOpen, setSortOpen] = useState(false)
  const sortBtn = useRef<HTMLButtonElement>(null)

  const visible = useMemo(() => {
    const inCat = filter ? bookmarks.filter((b) => b.categoryId === filter) : bookmarks
    return sortBookmarks(inCat, sortOrder, categories)
  }, [bookmarks, categories, filter, sortOrder])

  const isEmpty = loaded && bookmarks.length === 0
  const searching = desktop && query.trim() !== ''

  return (
    <div className="screen">
      <ScreenHeader title="bookmarks" count={bookmarks.length}>
        {desktop ? (
          <>
            <DesktopSearchBox />
            <PrimaryButton small onClick={() => openAdd()}>
              <Icon name="add" size={20} /> Add bookmark
            </PrimaryButton>
          </>
        ) : (
          <IconButton icon="search" label="Search" disabled={bookmarks.length === 0} onClick={() => navigate('/search')} />
        )}
      </ScreenHeader>

      {isEmpty ? (
        <EmptyState
          title="Nothing saved yet"
          body={desktop ? 'Press N, or add your first link. Anything you save on your phone shows up here once you sign in.' : 'Add your first link, or share one to Bookmarks from your browser.'}
          action={<PrimaryButton onClick={() => openAdd()}>Paste a link</PrimaryButton>}
        />
      ) : (
        <>
          <CategoryChips selected={filter} onSelect={(id) => setUi('filterCategory', id)} />
          {searching ? (
            <div style={{ paddingTop: 14 }}>
              <SearchResults query={query} categoryId={filter} />
            </div>
          ) : (
            <>
              <div className="controls-row">
                <button ref={sortBtn} type="button" className="sort-btn" onClick={() => setSortOpen(true)} aria-haspopup="menu" aria-expanded={sortOpen}>
                  <Icon name="swapVert" size={16} />
                  <span>{SORT_LABELS[sortOrder].short}</span>
                </button>
                <div className="view-toggle" role="radiogroup" aria-label="Layout">
                  <button type="button" role="radio" aria-checked={viewMode === 'LIST'} aria-label="List" onClick={() => setPref('viewMode', 'LIST')}>
                    <Icon name="viewList" size={18} />
                  </button>
                  <button type="button" role="radio" aria-checked={viewMode === 'GRID'} aria-label="Grid" onClick={() => setPref('viewMode', 'GRID')}>
                    <Icon name="gridView" size={18} />
                  </button>
                </div>
              </div>
              {sortOpen ? (
                <Menu anchor={sortBtn.current} onClose={() => setSortOpen(false)}>
                  {(Object.keys(SORT_LABELS) as SortOrder[]).map((o) => (
                    <button key={o} type="button" role="menuitemradio" aria-checked={sortOrder === o} onClick={() => { setPref('sortOrder', o); setSortOpen(false) }}>
                      {SORT_LABELS[o].menu}
                    </button>
                  ))}
                </Menu>
              ) : null}

              {viewMode === 'GRID' ? (
                <div className="list-scroll">
                  <MasonryGrid
                    items={visible}
                    renderItem={(b, fixed) => (
                      <BookmarkGridCard bookmark={b} category={categoryOf(b, categories)} fixedHeight={fixed} onOpen={onOpen} onActions={onActions} />
                    )}
                  />
                </div>
              ) : (
                <div className="list-stack list-scroll">
                  {visible.map((b) => (
                    <BookmarkListRow key={b.id} bookmark={b} category={categoryOf(b, categories)} onOpen={onOpen} onActions={onActions} />
                  ))}
                </div>
              )}
            </>
          )}
        </>
      )}

      {!isEmpty && !desktop ? (
        <button type="button" className="fab" aria-label="Add bookmark" onClick={() => openAdd()}>
          <Icon name="add" size={26} />
        </button>
      ) : null}
    </div>
  )
}
