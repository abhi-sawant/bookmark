import { useEffect, useMemo, useRef, useState } from 'react'
import { isValid, normalize, host as urlHost } from '@/core/util/urlNormalizer'
import { fromDomain, resolve as resolveTitle } from '@/core/util/titleFallback'
import { extract } from '@/core/util/urlExtractor'
import { fileToThumbnail, ThumbnailError } from '@/core/image'
import { type ThumbnailChoice, BookmarkLimits, CATEGORY_NAME_MAX, UNSORTED_ID } from '@/data/models'
import { createCategory, defaultCategoryId, findByUrl, saveBookmark, updateBookmark, useData } from '@/data/repo'
import { parseCategoryColor } from '@/data/swatches'
import { usePrefs } from '@/app/prefs'
import { useUi, type AddEditState } from '@/app/uiStore'
import { Icon } from '@/components/Icon'
import { MonogramTile } from '@/components/MonogramTile'
import { Thumbnail } from '@/components/Thumbnail'
import { CategoryChip, Field, Menu, PrimaryButton, SecondaryButton, Sheet, TextButton, useSnackbar } from '@/components/ui'

export function AddEditSheet({ state }: { state: AddEditState }) {
  const close = () => useUi.getState().set('addEdit', null)
  const categories = useData((s) => s.categories)
  const editing = useData((s) => (state.editId ? s.bookmarks.find((b) => b.id === state.editId) : undefined))
  const lastUsed = usePrefs((s) => s.lastUsedCategory)
  const setPref = usePrefs((s) => s.set)
  const toast = useSnackbar((s) => s.show)

  const [url, setUrl] = useState(editing?.originalUrl ?? state.prefill?.url ?? '')
  const [title, setTitle] = useState(editing?.title ?? state.prefill?.title ?? '')
  const [description, setDescription] = useState(editing?.description ?? '')
  const [titleEdited, setTitleEdited] = useState(false)
  const [descriptionEdited, setDescriptionEdited] = useState(false)
  const [categoryId, setCategoryId] = useState(() => {
    if (editing) return editing.categoryId
    if (lastUsed && categories.some((c) => c.id === lastUsed)) return lastUsed
    return categories.some((c) => c.id === defaultCategoryId()) ? defaultCategoryId() : UNSORTED_ID
  })
  const [creating, setCreating] = useState(false)
  const [newName, setNewName] = useState('')
  const [thumb, setThumb] = useState<ThumbnailChoice | null | undefined>(undefined) // undefined = unchanged
  const [saving, setSaving] = useState(false)
  const [thumbMenu, setThumbMenu] = useState(false)
  const thumbBtn = useRef<HTMLButtonElement>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  const [clip, setClip] = useState<string | null>(null)
  const [clipDismissed, setClipDismissed] = useState(false)

  const urlInvalid = url.trim() !== '' && !isValid(url)
  const canSave = editing ? true : isValid(url)

  // Offer the clipboard link only when the browser already lets us read it without a prompt.
  useEffect(() => {
    if (editing || url) return
    let cancelled = false
    void (async () => {
      try {
        const perm = await navigator.permissions.query({ name: 'clipboard-read' as PermissionName })
        if (perm.state !== 'granted') return
        const text = await navigator.clipboard.readText()
        const found = extract(text).primaryUrl
        if (!cancelled && found && isValid(found) && !findByUrl(normalize(found))) setClip(found)
      } catch {
        // not supported / not allowed: the Paste button still works on tap
      }
    })()
    return () => {
      cancelled = true
    }
  }, [editing, url])

  async function pasteFromClipboard() {
    try {
      const found = extract(await navigator.clipboard.readText())
      if (found.primaryUrl) {
        setUrl(found.primaryUrl)
        if (found.subjectTitle && !title) setTitle(found.subjectTitle)
      } else toast('No link on the clipboard')
    } catch {
      toast("Couldn't read the clipboard")
    }
  }

  function handleUrlChange(v: string) {
    // Pasting chatty text (e.g. "Check this https://…") keeps just the link.
    const found = v.includes(' ') ? extract(v).primaryUrl : null
    setUrl(found ?? v)
  }

  async function pickFile(file: File | undefined) {
    if (!file) return
    try {
      setThumb(await fileToThumbnail(file))
    } catch (e) {
      toast(e instanceof ThumbnailError ? e.message : "Couldn't use that image")
    }
  }

  const previewUrl = normalize(url)
  const previewTitle = useMemo(
    () => (url.trim() ? resolveTitle({ fetchedTitle: title, url: previewUrl }) : ''),
    [title, url, previewUrl],
  )
  const previewSite = url.trim() ? (fromDomain(previewUrl) ?? urlHost(previewUrl) ?? '') : ''
  const hasThumb = thumb !== undefined ? thumb !== null : !!(editing?.thumbnailUrl || editing?.pendingThumbnail)

  async function onCreateCategory() {
    const res = await createCategory(newName)
    if (res.ok) {
      setCategoryId(res.category.id)
      setCreating(false)
      setNewName('')
    } else toast(res.error === 'DUPLICATE_NAME' ? 'A category with that name already exists.' : 'Give the category a name.')
  }

  async function save() {
    if (saving || !canSave) return
    setSaving(true)
    try {
      if (editing) {
        await updateBookmark(editing.id, { title, description, categoryId, titleEdited, descriptionEdited, thumbnail: thumb })
        close()
        return
      }
      const res = await saveBookmark({ rawUrl: url, title, description, categoryId, thumbnail: thumb })
      if (res.kind === 'duplicate') {
        useUi.getState().set('duplicate', res.existing.id)
        return
      }
      setPref('lastUsedCategory', categoryId)
      navigator.vibrate?.(10)
      close()
    } finally {
      setSaving(false)
    }
  }

  const showClipChip = !editing && !url && !clipDismissed

  return (
    <Sheet onClose={close} label={editing ? 'Edit bookmark' : 'Add bookmark'}>
      <h2 className="t-sheet-title sheet-title">{editing ? 'Edit bookmark' : 'Add bookmark'}</h2>

      <Field
        label="URL"
        value={url}
        onChange={handleUrlChange}
        placeholder="https://"
        type="url"
        inputMode="url"
        autoFocus={!editing}
        name="url"
        onEnter={() => void save()}
      />
      {urlInvalid ? <div className="field-error">That doesn&apos;t look like a link</div> : null}

      {showClipChip ? (
        <div className="clip-chip panel" style={{ marginTop: 9 }}>
          {clip ? (
            <button type="button" className="clip-label t-chip" onClick={() => { setUrl(clip); setClip(null) }}>
              {`From clipboard · ${clip.replace(/^[a-z]+:\/\//i, '').slice(0, 28)}…`}
            </button>
          ) : (
            <button type="button" className="clip-label t-chip" onClick={() => void pasteFromClipboard()}>
              <Icon name="contentPaste" size={16} /> Paste from clipboard
            </button>
          )}
          {clip ? (
            <button type="button" aria-label="Dismiss clipboard suggestion" className="clip-x" onClick={() => { setClip(null); setClipDismissed(true) }}>
              <Icon name="close" size={18} />
            </button>
          ) : null}
        </div>
      ) : null}

      {url.trim() ? (
        <div className="preview-card panel" style={{ marginTop: 16 }}>
          {editing ? (
            <Thumbnail bookmark={thumb !== undefined ? { ...editing, thumbnailUrl: null, pendingThumbnail: thumb?.blob ?? null } : editing} size={76} radius={18} fontSize={26} />
          ) : (
            <div className="thumb" style={{ width: 76, height: 76, borderRadius: 18 }}>
              <MonogramTile url={previewUrl} accentColor={thumb?.accentColor} fontSize={26} />
              {thumb ? <ThumbPreview blob={thumb.blob} /> : null}
            </div>
          )}
          <div className="col">
            <div className="t-card-title clamp-2" style={{ fontSize: 14 }}>{previewTitle}</div>
            {description.trim() ? <div className="t-card-desc muted clamp-2">{description}</div> : null}
            {previewSite ? <div className="t-site muted clamp-1" style={{ marginTop: 2 }}>{previewSite}</div> : null}
          </div>
        </div>
      ) : null}

      <div style={{ marginTop: 14 }}>
        <Field label="Title" value={title} onChange={(v) => { setTitle(v); setTitleEdited(true) }} max={BookmarkLimits.title} counter name="title" />
      </div>
      <div style={{ marginTop: 10 }}>
        <Field label="Description" value={description} onChange={(v) => { setDescription(v); setDescriptionEdited(true) }} max={BookmarkLimits.description} counter multiline rows={3} name="description" />
      </div>

      <div style={{ marginTop: 18 }}>
        <div className="t-field-label muted" style={{ padding: '0 0 8px 4px' }}>Category</div>
        <div className="chip-flow" role="radiogroup" aria-label="Category">
          {categories.map((c) => (
            <CategoryChip key={c.id} role="radio" label={c.name} color={parseCategoryColor(c.colorHex)} selected={categoryId === c.id} onClick={() => setCategoryId(c.id)} />
          ))}
          <button type="button" className={`chip chip-new ${creating ? 'active' : ''}`} onClick={() => setCreating((v) => !v)} aria-expanded={creating}>
            <Icon name="add" size={18} /> New
          </button>
        </div>
        {creating ? (
          <div style={{ marginTop: 10 }}>
            <Field label="New category name" value={newName} onChange={setNewName} max={CATEGORY_NAME_MAX} counter autoFocus onEnter={() => void onCreateCategory()} />
            <div className="dialog-actions" style={{ marginTop: 10 }}>
              <TextButton onClick={() => { setCreating(false); setNewName('') }}>Cancel</TextButton>
              <PrimaryButton small disabled={!newName.trim()} onClick={() => void onCreateCategory()}>Create &amp; select</PrimaryButton>
            </div>
          </div>
        ) : null}
      </div>

      <div style={{ marginTop: 14 }}>
        <button ref={thumbBtn} type="button" className="btn btn-secondary btn-block thumb-btn" onClick={() => setThumbMenu(true)} aria-haspopup="menu">
          <span>Thumbnail</span> <span aria-hidden="true">&#9662;</span>
        </button>
        {thumbMenu ? (
          <Menu anchor={thumbBtn.current} onClose={() => setThumbMenu(false)}>
            <button type="button" role="menuitem" onClick={() => { setThumbMenu(false); fileInput.current?.click() }}>
              <Icon name="image" size={20} /> Pick from device
            </button>
            <button type="button" role="menuitem" disabled={!hasThumb} onClick={() => { setThumb(null); setThumbMenu(false) }}>
              <Icon name="delete" size={20} /> Remove
            </button>
          </Menu>
        ) : null}
        <input ref={fileInput} type="file" accept="image/*" hidden onChange={(e) => { void pickFile(e.target.files?.[0]); e.target.value = '' }} />
        {!editing ? <div className="t-row-sub muted" style={{ marginTop: 8 }}>Link previews aren&apos;t fetched on the web. Open the Android app after syncing to fetch one.</div> : null}
      </div>

      <div className="sheet-actions">
        <SecondaryButton onClick={close}>Cancel</SecondaryButton>
        <PrimaryButton disabled={!canSave || saving} onClick={() => void save()}>Save</PrimaryButton>
      </div>
    </Sheet>
  )
}

function ThumbPreview({ blob }: { blob: Blob }) {
  const [src, setSrc] = useState<string>()
  useEffect(() => {
    const u = URL.createObjectURL(blob)
    setSrc(u)
    return () => URL.revokeObjectURL(u)
  }, [blob])
  return src ? <img src={src} alt="" style={{ position: 'absolute', inset: 0 }} /> : null
}
