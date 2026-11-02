import { useState } from 'react'
import { addTagsMany, moveMany, setPinnedMany, trashMany, useData } from '@/data/repo'
import { parseTags, TAG_INPUT_MAX } from '@/core/util/tags'
import { clearSelect, useUi } from '@/app/uiStore'
import { Dialog, Field, PrimaryButton, TextButton, useSnackbar } from '@/components/ui'

/** Bottom action bar while bulk-selecting: Pin, Move, Tag, Delete (to Trash), Cancel. */
export function SelectionBar() {
  const ids = useUi((s) => s.selectedIds)
  const categories = useData((s) => s.categories)
  const bookmarks = useData((s) => s.bookmarks)
  const toast = useSnackbar((s) => s.show)
  const [dialog, setDialog] = useState<'move' | 'tag' | null>(null)
  const [tagText, setTagText] = useState('')
  const n = ids.length
  const allPinned = n > 0 && bookmarks.filter((b) => ids.includes(b.id)).every((b) => b.isPinned)

  async function remove() {
    const { count, undo } = await trashMany(ids)
    clearSelect()
    toast(`Moved ${count} ${count === 1 ? 'bookmark' : 'bookmarks'} to Trash`, { action: { label: 'Undo', run: () => void undo() } })
  }

  return (
    <>
      <div className="selection-bar" role="toolbar" aria-label="Selected bookmarks">
        <span className="count t-row-title">{n === 0 ? 'Select bookmarks' : `${n} selected`}</span>
        <TextButton disabled={n === 0} onClick={() => { void setPinnedMany(ids, !allPinned); clearSelect() }}>{allPinned ? 'Unpin' : 'Pin'}</TextButton>
        <TextButton disabled={n === 0} onClick={() => setDialog('move')}>Move</TextButton>
        <TextButton disabled={n === 0} onClick={() => { setTagText(''); setDialog('tag') }}>Tag</TextButton>
        <TextButton danger disabled={n === 0} onClick={() => void remove()}>Delete</TextButton>
        <TextButton onClick={clearSelect}>Cancel</TextButton>
      </div>

      {dialog === 'move' ? (
        <Dialog label="Move to category" onClose={() => setDialog(null)}>
          <h2 className="t-sheet-title">Move to…</h2>
          <div role="menu" style={{ display: 'flex', flexDirection: 'column', margin: '8px 0' }}>
            {categories.map((c) => (
              <button key={c.id} type="button" role="menuitem" className="recent-item" onClick={() => { void moveMany(ids, c.id); setDialog(null); clearSelect() }}>
                {c.name}
              </button>
            ))}
          </div>
          <div className="dialog-actions">
            <TextButton onClick={() => setDialog(null)}>Cancel</TextButton>
          </div>
        </Dialog>
      ) : null}

      {dialog === 'tag' ? (
        <Dialog label="Add tags" onClose={() => setDialog(null)}>
          <h2 className="t-sheet-title">Add tags</h2>
          <Field label="Tags (comma separated)" value={tagText} onChange={setTagText} max={TAG_INPUT_MAX} name="bulk-tags" />
          <div className="dialog-actions">
            <TextButton onClick={() => setDialog(null)}>Cancel</TextButton>
            <PrimaryButton small disabled={parseTags(tagText).length === 0} onClick={() => { void addTagsMany(ids, parseTags(tagText)); setDialog(null); clearSelect() }}>Add</PrimaryButton>
          </div>
        </Dialog>
      ) : null}
    </>
  )
}
