import { type ReactNode, useMemo } from 'react'
import { indexFor } from '@/core/util/domainColor'
import { type Bookmark, type Category } from '@/data/models'
import { useElementWidth } from '@/app/hooks'
import { CategoryDot } from './ui'
import { Icon } from './Icon'
import { Thumbnail } from './Thumbnail'
import { useLongPress } from './useLongPress'
import { argbToHex } from './MonogramTile'
import { findHighlightRanges } from '@/core/util/highlight'

/** Heights of the monogram tile in the grid, picked by domain so a run of fallbacks staggers. */
const FALLBACK_HEIGHTS = [96, 104, 110, 120, 132, 148, 158, 176]

const ratioOf = (b: Bookmark): number | null =>
  (b.thumbnailUrl || b.pendingThumbnail) && b.thumbnailWidth && b.thumbnailHeight
    ? Math.min(2, Math.max(0.5, b.thumbnailWidth / b.thumbnailHeight))
    : null

function thumbHeight(b: Bookmark, width: number): number {
  const r = ratioOf(b)
  return r ? width / r : FALLBACK_HEIGHTS[indexFor(b.url)]
}

interface CardProps {
  bookmark: Bookmark
  category: Category
  onOpen: (b: Bookmark) => void
  onActions: (b: Bookmark, at: { clientX: number; clientY: number }) => void
  /** Rendered height of the thumbnail when it has no intrinsic ratio. */
  fixedHeight?: number
}

function useCardGestures(b: Bookmark, onOpen: CardProps['onOpen'], onActions: CardProps['onActions']) {
  const lp = useLongPress((at) => onActions(b, at))
  return {
    ...lp.props,
    onClick(e: React.MouseEvent) {
      if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey) return // let the browser handle modified clicks
      e.preventDefault()
      if (lp.consumedClick()) return
      onOpen(b)
    },
    onKeyDown(e: React.KeyboardEvent) {
      if (e.key === 'ContextMenu' || (e.shiftKey && e.key === 'F10')) {
        e.preventDefault()
        const r = (e.currentTarget as HTMLElement).getBoundingClientRect()
        onActions(b, { clientX: r.left + 24, clientY: r.top + 24 })
      }
    },
  }
}

export function BookmarkGridCard({ bookmark: b, category, onOpen, onActions, fixedHeight }: CardProps) {
  const gestures = useCardGestures(b, onOpen, onActions)
  const ratio = ratioOf(b)
  return (
    <a
      href={b.url}
      target="_blank"
      rel="noopener noreferrer"
      className="grid-card"
      aria-label={`Open ${b.title}`}
      draggable={false}
      {...gestures}
    >
      <Thumbnail
        bookmark={b}
        fontSize={34}
        style={ratio ? { aspectRatio: String(ratio) } : { height: fixedHeight ?? FALLBACK_HEIGHTS[indexFor(b.url)] }}
      />
      {b.isPinned ? (
        <span className="pin-badge" title="Pinned">
          <Icon name="flag" size={16} />
        </span>
      ) : null}
      <div className="text">
        <div className="t-card-title clamp-2">{b.title}</div>
        {b.description?.trim() ? <div className="t-card-desc desc clamp-2">{b.description}</div> : null}
        <div className="t-site meta">
          <CategoryDot color={category.colorHex} />
          <span className="clamp-1">{b.siteName ?? category.name}</span>
        </div>
      </div>
    </a>
  )
}

export function BookmarkListRow({ bookmark: b, category, onOpen, onActions }: CardProps) {
  const gestures = useCardGestures(b, onOpen, onActions)
  return (
    <a href={b.url} target="_blank" rel="noopener noreferrer" className="list-row" aria-label={`Open ${b.title}`} draggable={false} {...gestures}>
      <div style={{ position: 'relative', flex: 'none' }}>
        <Thumbnail bookmark={b} size={64} radius={14} fontSize={24} />
        {b.isPinned ? (
          <span className="pin-badge sm" title="Pinned">
            <Icon name="flag" size={12} />
          </span>
        ) : null}
      </div>
      <div className="col">
        <div className="t-card-title clamp-2">{b.title}</div>
        <div className="t-site meta">
          <CategoryDot color={category.colorHex} />
          <span className="site clamp-1">{`${b.siteName ?? ''}${b.siteName ? ' · ' : ''}${category.name}`}</span>
        </div>
      </div>
    </a>
  )
}

/** Search result row: smaller thumbnail, highlighted title, no description or badges. */
export function SearchResultRow({ bookmark: b, category, terms, onOpen, onActions }: CardProps & { terms: string[] }) {
  const gestures = useCardGestures(b, onOpen, onActions)
  return (
    <a href={b.url} target="_blank" rel="noopener noreferrer" className="list-row search-row" aria-label={`Open ${b.title}`} draggable={false} {...gestures}>
      <Thumbnail bookmark={b} size={56} radius={14} fontSize={22} />
      <div className="col">
        <div className="t-card-title clamp-2">
          <Highlighted text={b.title} terms={terms} />
        </div>
        <div className="t-site meta" style={{ marginTop: 4 }}>
          <span className="clamp-1">{`${b.siteName ?? ''}${b.siteName ? ' · ' : ''}${category.name}`}</span>
        </div>
      </div>
    </a>
  )
}

export function Highlighted({ text, terms }: { text: string; terms: string[] }) {
  const parts = useMemo(() => {
    const ranges = findHighlightRanges(text, terms)
    const out: ReactNode[] = []
    let at = 0
    ranges.forEach((r, i) => {
      if (r.start > at) out.push(text.slice(at, r.start))
      out.push(
        <mark key={i} style={{ background: 'color-mix(in srgb, var(--accent) 28%, transparent)', color: 'inherit', borderRadius: 3 }}>
          {text.slice(r.start, r.end + 1)}
        </mark>,
      )
      at = r.end + 1
    })
    if (at < text.length) out.push(text.slice(at))
    return out
  }, [text, terms])
  return <>{parts}</>
}

// ---------------------------------------------------------------------------
// Staggered grid: items go to the currently shortest column, like Compose's
// LazyVerticalStaggeredGrid. Heights are estimated so columns balance without measuring.
// ---------------------------------------------------------------------------

export function columnsFor(width: number): number {
  if (width < 640) return 2
  return Math.max(2, Math.min(6, Math.floor((width + 12) / (210 + 12))))
}

export function MasonryGrid({ items, renderItem }: { items: Bookmark[]; renderItem: (b: Bookmark, fixedHeight: number) => ReactNode }) {
  const [ref, width] = useElementWidth<HTMLDivElement>()
  const cols = columnsFor(width)
  const columns = useMemo(() => {
    const colWidth = width > 0 ? (width - (cols - 1) * 12) / cols : 160
    const heights = new Array<number>(cols).fill(0)
    const out: { item: Bookmark; fixed: number }[][] = Array.from({ length: cols }, () => [])
    for (const item of items) {
      let shortest = 0
      for (let c = 1; c < cols; c++) if (heights[c] < heights[shortest]) shortest = c
      const th = thumbHeight(item, colWidth)
      const textLines = Math.min(2, Math.ceil((item.title.length * 7.6) / colWidth)) + (item.description?.trim() ? Math.min(2, Math.ceil((item.description.length * 6.6) / colWidth)) : 0)
      out[shortest].push({ item, fixed: FALLBACK_HEIGHTS[indexFor(item.url)] })
      heights[shortest] += th + 10 + textLines * 18 + 28 + 12
    }
    return out
  }, [items, cols, width])

  return (
    <div ref={ref} className="masonry" style={{ gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))` }}>
      {columns.map((col, i) => (
        <div key={i} className="masonry-col">
          {col.map(({ item, fixed }) => (
            <div key={item.id}>{renderItem(item, fixed)}</div>
          ))}
        </div>
      ))}
    </div>
  )
}

export { argbToHex }
