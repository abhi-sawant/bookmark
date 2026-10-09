import { useState } from 'react'
import { type Category, CATEGORY_NAME_MAX, UNSORTED_ID } from '@/data/models'
import { bookmarkCountByCategory, createCategory, deleteCategory, getCategory, updateCategory, useData } from '@/data/repo'
import { CATEGORY_ICON_KEYS, CATEGORY_SWATCHES, parseCategoryColor } from '@/data/swatches'
import { plural } from '@/app/format'
import { useUi } from '@/app/uiStore'
import { Icon, type IconName } from '@/components/Icon'
import { CategoryDot, Dialog, Field, PrimaryButton, TextButton, useSnackbar } from '@/components/ui'

const ICONS: Record<(typeof CATEGORY_ICON_KEYS)[number], IconName> = { play: 'playCircle', edit: 'edit', star: 'star', home: 'home' }

const ERRORS = {
  BLANK_NAME: 'Give the category a name.',
  DUPLICATE_NAME: 'A category with that name already exists.',
  CANNOT_DELETE_FALLBACK: "Unsorted can't be deleted — it's where bookmarks fall back to.",
  CANNOT_DELETE_DEFAULT: 'Make another category the default before deleting this one.',
} as const

export function CategoryEditDialog({ id }: { id?: string }) {
  const set = useUi((s) => s.set)
  const toast = useSnackbar((s) => s.show)
  const categories = useData((s) => s.categories)
  const existing: Category | undefined = id ? getCategory(id) : undefined
  const [name, setName] = useState(existing?.name ?? '')
  const [color, setColor] = useState(() => (existing ? parseCategoryColor(existing.colorHex) : CATEGORY_SWATCHES[categories.length % 8]))
  const [icon, setIcon] = useState<string | null>(existing?.iconKey ?? null)
  const close = () => set('categoryDialog', null)
  const deletable = !!existing && existing.id !== UNSORTED_ID && !existing.isDefault

  async function save() {
    const res = existing ? await updateCategory(existing.id, name, color, icon) : await createCategory(name, color, icon)
    if (res.ok) close()
    else toast(ERRORS[res.error])
  }

  return (
    <Dialog onClose={close} label={existing ? 'Edit category' : 'New category'}>
      <h2 className="t-sheet-title">{existing ? 'Edit category' : 'New category'}</h2>
      <Field label="Name" value={name} onChange={setName} max={CATEGORY_NAME_MAX} counter autoFocus onEnter={() => void save()} />
      <div className="t-field-label muted" style={{ marginTop: 18, marginBottom: 8 }}>Colour</div>
      <div className="swatches" role="radiogroup" aria-label="Colour">
        {CATEGORY_SWATCHES.map((hex, i) => (
          <button key={hex} type="button" role="radio" aria-checked={color.toLowerCase() === hex.toLowerCase()} aria-label={`Colour ${i + 1}`} className="swatch" style={{ background: hex }} onClick={() => setColor(hex)} />
        ))}
      </div>
      <div className="t-field-label muted" style={{ marginTop: 18, marginBottom: 8 }}>Icon (optional)</div>
      <div className="icon-tiles" role="radiogroup" aria-label="Icon">
        {CATEGORY_ICON_KEYS.map((k) => (
          <button key={k} type="button" role="radio" aria-checked={icon === k} aria-label={k} className="icon-tile" onClick={() => setIcon(icon === k ? null : k)}>
            <Icon name={ICONS[k]} size={22} />
          </button>
        ))}
        <button type="button" role="radio" aria-checked={icon === null} aria-label="No icon" className="icon-tile" onClick={() => setIcon(null)}>
          <Icon name="block" size={22} />
        </button>
      </div>
      <div className="dialog-actions">
        {deletable ? (
          <>
            <TextButton danger onClick={() => { close(); set('deleteCategoryId', existing!.id) }}>Delete</TextButton>
            <span className="spacer" />
          </>
        ) : null}
        <TextButton onClick={close}>Cancel</TextButton>
        <PrimaryButton small disabled={!name.trim()} onClick={() => void save()}>{existing ? 'Save' : 'Create'}</PrimaryButton>
      </div>
    </Dialog>
  )
}

export function DeleteCategoryDialog({ id }: { id: string }) {
  const set = useUi((s) => s.set)
  const toast = useSnackbar((s) => s.show)
  const categories = useData((s) => s.categories)
  const category = getCategory(id)
  const count = bookmarkCountByCategory().get(id) ?? 0
  const others = categories.filter((c) => c.id !== id)
  const [mode, setMode] = useState<'move' | 'delete'>('move')
  const [target, setTarget] = useState(() => (others.some((c) => c.id === UNSORTED_ID) ? UNSORTED_ID : (others[0]?.id ?? UNSORTED_ID)))
  const close = () => set('deleteCategoryId', null)
  if (!category) return null

  async function confirm() {
    const res = await deleteCategory(id, mode === 'move' ? { kind: 'move', targetId: target } : { kind: 'deleteBookmarks' })
    close()
    if (!res.ok) return toast(ERRORS[res.error])
    toast(`Deleted “${res.category.name}”`, {
      action: { label: 'Undo', run: () => void res.undoable.undo() },
      onTimeout: () => void res.undoable.commit(),
    })
  }

  const body = count === 0 ? 'This category is empty.' : `${plural(count, 'bookmark')} ${count === 1 ? 'is' : 'are'} in this category. Choose what happens to ${count === 1 ? 'it' : 'them'}.`
  return (
    <Dialog onClose={close} label="Delete category">
      <h2 className="t-sheet-title">{`Delete “${category.name}”?`}</h2>
      <p className="t-row-title muted" style={{ margin: '0 0 14px', fontWeight: 600 }}>{body}</p>
      {count > 0 ? (
        <div role="radiogroup" aria-label="What happens to the bookmarks" className="delete-options">
          <div className={`option-card ${mode === 'move' ? 'selected' : ''}`} role="radio" aria-checked={mode === 'move'} tabIndex={0} onClick={() => setMode('move')} onKeyDown={(e) => (e.key === ' ' || e.key === 'Enter') && setMode('move')}>
            <div className="t-row-title">Move bookmarks to</div>
            <label className="move-select" onClick={(e) => e.stopPropagation()}>
              <CategoryDot color={parseCategoryColor(getCategory(target)?.colorHex ?? '#8F96C4')} />
              <select value={target} onChange={(e) => { setTarget(e.target.value); setMode('move') }} aria-label="Move bookmarks to">
                {others.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </label>
          </div>
          <div className={`option-card ${mode === 'delete' ? 'selected' : ''}`} role="radio" aria-checked={mode === 'delete'} tabIndex={0} onClick={() => setMode('delete')} onKeyDown={(e) => (e.key === ' ' || e.key === 'Enter') && setMode('delete')}>
            <div className="t-row-title">{count === 1 ? 'Delete category and its bookmark' : `Delete category and its ${count} bookmarks`}</div>
          </div>
        </div>
      ) : null}
      <div className="dialog-actions">
        <TextButton onClick={close}>Cancel</TextButton>
        <PrimaryButton small onClick={() => void confirm()}>Delete</PrimaryButton>
      </div>
    </Dialog>
  )
}
