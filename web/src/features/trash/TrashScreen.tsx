import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { deleteForever, emptyTrash, restoreFromTrash, useData } from '@/data/repo'
import { TRASH_RETENTION_DAYS, trashDaysLeft } from '@/data/trash'
import { Dialog, IconButton, PrimaryButton, ScreenHeader, TextButton, EmptyState } from '@/components/ui'
import { Thumbnail } from '@/components/Thumbnail'

/** Deleted bookmarks, kept for 30 days. Restore, delete for good, or empty. */
export function TrashScreen() {
  const navigate = useNavigate()
  const trashed = useData((s) => s.trashed)
  const [confirmEmpty, setConfirmEmpty] = useState(false)
  const now = Date.now()
  const items = [...trashed].sort((a, b) => (b.trashedAt ?? 0) - (a.trashedAt ?? 0))

  return (
    <div className="screen">
      <ScreenHeader title="trash" count={items.length}>
        <IconButton icon="arrowBack" label="Back to settings" onClick={() => navigate('/settings')} />
        {items.length > 0 ? <TextButton danger onClick={() => setConfirmEmpty(true)}>Empty</TextButton> : null}
      </ScreenHeader>
      <p className="t-row-sub muted" style={{ padding: '4px 26px 8px' }}>
        {`Deleted bookmarks stay here for ${TRASH_RETENTION_DAYS} days, then are removed for good.`}
      </p>

      {items.length === 0 ? (
        <EmptyState title="Trash is empty" body="Bookmarks you delete show up here so you can bring them back." />
      ) : (
        <div className="list-stack" style={{ padding: '4px 16px 24px' }}>
          {items.map((b) => {
            const left = trashDaysLeft(b.trashedAt ?? now, now)
            return (
              <div key={b.id} className="trash-row panel">
                <Thumbnail bookmark={b} size={56} radius={14} fontSize={20} />
                <div className="col" style={{ minWidth: 0, flex: 1 }}>
                  <div className="t-card-title clamp-2">{b.title}</div>
                  <div className="t-site muted clamp-1" style={{ marginTop: 3 }}>
                    {[b.siteName, left === 0 ? 'removed soon' : `${left} ${left === 1 ? 'day' : 'days'} left`].filter(Boolean).join(' · ')}
                  </div>
                  <div className="trash-actions">
                    <TextButton onClick={() => void restoreFromTrash(b.id)}>Restore</TextButton>
                    <TextButton danger onClick={() => void deleteForever([b.id])}>Delete forever</TextButton>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {confirmEmpty ? (
        <Dialog label="Empty Trash" onClose={() => setConfirmEmpty(false)}>
          <h2 className="t-sheet-title">Empty Trash?</h2>
          <p className="t-row-sub muted" style={{ margin: 0, fontSize: 14, lineHeight: '20px' }}>
            {`This permanently deletes ${items.length} ${items.length === 1 ? 'bookmark' : 'bookmarks'}. It can't be undone.`}
          </p>
          <div className="dialog-actions">
            <TextButton onClick={() => setConfirmEmpty(false)}>Cancel</TextButton>
            <PrimaryButton small style={{ background: 'var(--error)' }} onClick={() => { setConfirmEmpty(false); void emptyTrash() }}>Delete all</PrimaryButton>
          </div>
        </Dialog>
      ) : null}
    </div>
  )
}
