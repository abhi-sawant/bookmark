import { type Bookmark } from '@/data/models'
import { categoryOf, useData } from '@/data/repo'
import { copyLink, deleteWithUndo, openLink, shareLink, togglePin } from '@/app/actions'
import { formatDayMonth, formatDayMonthLong } from '@/app/format'
import { openEdit, useUi } from '@/app/uiStore'
import { Icon, type IconName } from '@/components/Icon'
import { Thumbnail } from '@/components/Thumbnail'
import { CategoryDot, IconButton, PrimaryButton, SecondaryButton, Sheet } from '@/components/ui'
import { parseCategoryColor } from '@/data/swatches'

const siteLine = (b: Bookmark, category: string) => `${b.siteName ? `${b.siteName} · ` : ''}${category}`

function ActionRow({ icon, label, onClick, danger }: { icon: IconName; label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button type="button" role="menuitem" className={`action-row ${danger ? 'danger' : ''}`} onClick={onClick}>
      <Icon name={icon} size={22} />
      <span>{label}</span>
    </button>
  )
}

/** Long-press / right-click actions. */
export function ContextSheet({ id }: { id: string }) {
  const set = useUi((s) => s.set)
  const b = useData((s) => s.bookmarks.find((x) => x.id === id))
  const categories = useData((s) => s.categories)
  const close = () => set('context', null)
  if (!b) return null
  const category = categoryOf(b, categories)
  const then = (fn: () => void | Promise<void>) => () => {
    close()
    void fn()
  }
  return (
    <Sheet onClose={close} label="Bookmark actions" noHandle={false}>
      <div className="ctx-head">
        <Thumbnail bookmark={b} size={48} radius={14} fontSize={17} />
        <div style={{ minWidth: 0 }}>
          <div className="t-card-title clamp-1">{b.title}</div>
          <div className="t-site muted clamp-1" style={{ marginTop: 3 }}>{siteLine(b, category.name)}</div>
        </div>
      </div>
      <div role="menu" style={{ margin: '0 calc(-1 * var(--gutter))' }}>
        <ActionRow icon="openInNew" label="Open" onClick={then(() => openLink(b))} />
        <ActionRow icon="edit" label="Edit" onClick={() => { close(); openEdit(b.id) }} />
        <ActionRow icon="viewList" label="Change category" onClick={() => { close(); openEdit(b.id) }} />
        <ActionRow icon="contentCopy" label="Copy link" onClick={then(() => copyLink(b))} />
        <ActionRow icon="share" label="Share" onClick={then(() => shareLink(b))} />
        <ActionRow icon="flag" label={b.isPinned ? 'Unpin' : 'Pin to top'} onClick={then(() => togglePin(b))} />
        <ActionRow icon="delete" label="Delete" danger onClick={then(() => deleteWithUndo(b))} />
      </div>
    </Sheet>
  )
}

/** Detail view: reached from "View bookmark" on the duplicate sheet. */
export function DetailSheet({ id }: { id: string }) {
  const set = useUi((s) => s.set)
  const b = useData((s) => s.bookmarks.find((x) => x.id === id))
  const categories = useData((s) => s.categories)
  const close = () => set('detail', null)
  if (!b) return null
  const category = categoryOf(b, categories)
  return (
    <Sheet onClose={close} label={b.title} noHandle>
      <div className="detail-hero">
        <Thumbnail bookmark={b} fontSize={56} style={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }} />
        <div className="detail-fade" />
      </div>
      <div className="detail-body">
        <h2 className="t-detail-title" style={{ margin: 0, overflowWrap: 'anywhere' }}>{b.title}</h2>
        <div className="t-caption muted detail-meta">
          <CategoryDot color={parseCategoryColor(category.colorHex)} large />
          <span>{`${siteLine(b, category.name)} · saved ${formatDayMonth(b.createdAt)}`}</span>
        </div>
        {b.description?.trim() ? <div className="t-card-desc" style={{ marginTop: 12 }}>{b.description}</div> : null}
        <div className="detail-actions">
          <PrimaryButton style={{ flex: 1, minHeight: 50 }} onClick={() => openLink(b)}>Open</PrimaryButton>
          <IconButton outlined icon="share" label="Share" onClick={() => void shareLink(b)} />
          <IconButton outlined icon="edit" label="Edit" onClick={() => { close(); openEdit(b.id) }} />
          <IconButton outlined icon="flag" label={b.isPinned ? 'Unpin' : 'Pin'} onClick={() => void togglePin(b)} style={b.isPinned ? { color: 'var(--accent)' } : undefined} />
        </div>
      </div>
    </Sheet>
  )
}

/** Shown over the Add sheet when the normalised URL is already saved. */
export function DuplicateSheet({ id }: { id: string }) {
  const set = useUi((s) => s.set)
  const b = useData((s) => s.bookmarks.find((x) => x.id === id))
  const categories = useData((s) => s.categories)
  const close = () => set('duplicate', null)
  if (!b) return null
  const category = categoryOf(b, categories)
  return (
    <Sheet onClose={close} label="Already saved">
      <h2 className="t-sheet-title sheet-title" style={{ marginBottom: 7 }}>{`Already saved in ${category.name}`}</h2>
      <div className="t-row-sub muted">{`You saved this link on ${formatDayMonthLong(b.createdAt)}. Nothing was duplicated.`}</div>
      <div className="preview-card panel" style={{ marginTop: 16 }}>
        <Thumbnail bookmark={b} size={76} radius={18} fontSize={26} />
        <div className="col">
          <div className="t-card-title clamp-2" style={{ fontSize: 14 }}>{b.title}</div>
          <div className="t-site muted clamp-1" style={{ marginTop: 2 }}>{siteLine(b, category.name)}</div>
        </div>
      </div>
      <div className="sheet-actions">
        <SecondaryButton onClick={close}>Close</SecondaryButton>
        <PrimaryButton
          onClick={() => {
            set('duplicate', null)
            set('addEdit', null)
            set('detail', b.id)
          }}
        >
          View bookmark
        </PrimaryButton>
      </div>
    </Sheet>
  )
}
