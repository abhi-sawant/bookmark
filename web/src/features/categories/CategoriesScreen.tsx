import { useMemo, useRef, useState } from 'react'
import { bookmarkCountByCategory, reorderCategories, useData } from '@/data/repo'
import { parseCategoryColor } from '@/data/swatches'
import { useIsDesktop } from '@/app/hooks'
import { useUi } from '@/app/uiStore'
import { Icon } from '@/components/Icon'
import { CategoryDot, PrimaryButton, ScreenHeader } from '@/components/ui'

export function CategoriesScreen() {
  const categories = useData((s) => s.categories)
  const bookmarks = useData((s) => s.bookmarks)
  const set = useUi((s) => s.set)
  const desktop = useIsDesktop()
  const counts = useMemo(() => bookmarkCountByCategory(bookmarks, categories), [bookmarks, categories])

  const [draft, setDraft] = useState<string[] | null>(null)
  const [drag, setDrag] = useState<{ id: string; startIdx: number; dy: number } | null>(null)
  const rowH = useRef(0)
  const gesture = useRef<{ startY: number } | null>(null)

  const order = draft ?? categories.map((c) => c.id)
  const byId = new Map(categories.map((c) => [c.id, c]))

  function onPointerDown(e: React.PointerEvent<HTMLButtonElement>, id: string) {
    e.preventDefault()
    const row = (e.currentTarget.closest('.cat-row') as HTMLElement)
    rowH.current = row.getBoundingClientRect().height + 10 // row + gap
    e.currentTarget.setPointerCapture(e.pointerId)
    gesture.current = { startY: e.clientY }
    const startIdx = order.indexOf(id)
    setDraft(order)
    setDrag({ id, startIdx, dy: 0 })
    navigator.vibrate?.(10)
  }

  function onPointerMove(e: React.PointerEvent) {
    if (!drag || !gesture.current) return
    const dy = e.clientY - gesture.current.startY
    // The row follows the finger; it swaps places whenever its centre crosses a neighbour's.
    const target = Math.max(0, Math.min(order.length - 1, drag.startIdx + Math.round(dy / rowH.current)))
    const cur = (draft ?? order).indexOf(drag.id)
    if (target !== cur) {
      const next = [...(draft ?? order)]
      next.splice(cur, 1)
      next.splice(target, 0, drag.id)
      setDraft(next)
    }
    setDrag({ ...drag, dy })
  }

  async function onPointerUp() {
    if (!drag) return
    const final = draft ?? order
    setDrag(null)
    gesture.current = null
    if (final.join() !== categories.map((c) => c.id).join()) await reorderCategories(final)
    setDraft(null)
  }

  async function move(id: string, delta: number) {
    const i = order.indexOf(id)
    const j = i + delta
    if (j < 0 || j >= order.length) return
    const next = [...order]
    next.splice(i, 1)
    next.splice(j, 0, id)
    await reorderCategories(next)
  }

  return (
    <div className="screen" style={{ paddingBottom: 140 }}>
      <ScreenHeader title="categories" count={categories.length}>
        {desktop ? (
          <PrimaryButton small onClick={() => set('categoryDialog', {})}>
            <Icon name="add" size={20} /> New category
          </PrimaryButton>
        ) : null}
      </ScreenHeader>
      <div className="cat-list" onPointerMove={onPointerMove} onPointerUp={() => void onPointerUp()} onPointerCancel={() => void onPointerUp()}>
        {order.map((id, idx) => {
          const c = byId.get(id)
          if (!c) return null
          const dragging = drag?.id === id
          const comp = dragging ? drag.dy - (idx - drag.startIdx) * rowH.current : 0
          return (
            <div
              key={id}
              className={`cat-row panel ${dragging ? 'dragging' : ''}`}
              style={dragging ? { transform: `translateY(${comp}px)` } : undefined}
              onClick={() => !drag && set('categoryDialog', { id })}
            >
              <button
                type="button"
                className="drag-handle"
                aria-label={`Reorder ${c.name}`}
                onPointerDown={(e) => onPointerDown(e, id)}
                onClick={(e) => e.stopPropagation()}
                onKeyDown={(e) => {
                  if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
                    e.preventDefault()
                    void move(id, e.key === 'ArrowUp' ? -1 : 1)
                  }
                }}
              >
                <Icon name="dragHandle" size={20} />
              </button>
              <CategoryDot color={parseCategoryColor(c.colorHex)} large />
              <span className="t-row-title cat-name clamp-1">{c.name}</span>
              {c.isDefault ? <span className="default-pill t-counter">Default</span> : null}
              <span className="t-row-sub muted">{counts.get(c.id) ?? 0}</span>
            </div>
          )
        })}
      </div>
      {!desktop ? (
        <button type="button" className="fab extended" onClick={() => set('categoryDialog', {})}>
          <Icon name="add" size={20} /> New category
        </button>
      ) : null}
    </div>
  )
}
