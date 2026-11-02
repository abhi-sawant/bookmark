import { normalizeTags } from '@/core/util/tags'
import { trashCutoff } from './trash'
import { create } from 'zustand'
import { normalize } from '@/core/util/urlNormalizer'
import { fromDomain, resolve as resolveTitle } from '@/core/util/titleFallback'
import { getDb } from './db'
import {
  type Bookmark,
  BookmarkLimits,
  CATEGORY_NAME_MAX,
  type Category,
  ManualField,
  type ThumbnailChoice,
  type Tombstone,
  UNSORTED_ID,
  newId,
} from './models'
import { CATEGORY_SWATCHES } from './swatches'

// ---------------------------------------------------------------------------
// In-memory mirror. The database is the source of truth; every write goes to
// IndexedDB first and then updates this store, which the UI subscribes to.
// ---------------------------------------------------------------------------

interface DataState {
  loaded: boolean
  /** Live bookmarks only -- every screen reads this. Trashed rows are in [trashed]. */
  bookmarks: Bookmark[]
  trashed: Bookmark[]
  categories: Category[]
}

export const useData = create<DataState>(() => ({ loaded: false, bookmarks: [], trashed: [], categories: [] }))

/** Live and trashed rows together, for sync and backup. */
export const allBookmarks = (): Bookmark[] => {
  const s = useData.getState()
  return [...s.bookmarks, ...s.trashed]
}

export const findBookmark = (id: string): Bookmark | undefined => allBookmarks().find((b) => b.id === id)

const sortCategories = (cs: Category[]) =>
  [...cs].sort((a, b) => a.sortOrder - b.sortOrder || a.createdAt - b.createdAt)

/** Listeners told about local changes (the sync scheduler). */
const changeListeners = new Set<() => void>()
export function onLocalChange(fn: () => void): () => void {
  changeListeners.add(fn)
  return () => changeListeners.delete(fn)
}
const notifyChanged = () => changeListeners.forEach((fn) => fn())

async function putBookmark(b: Bookmark): Promise<void> {
  await (await getDb()).put('bookmarks', b)
  useData.setState((s) => {
    const upsert = (list: Bookmark[]) => {
      const i = list.findIndex((x) => x.id === b.id)
      const next = list.slice()
      if (i >= 0) next[i] = b
      else next.push(b)
      return next
    }
    const without = (list: Bookmark[]) => (list.some((x) => x.id === b.id) ? list.filter((x) => x.id !== b.id) : list)
    return b.trashedAt !== null
      ? { bookmarks: without(s.bookmarks), trashed: upsert(s.trashed) }
      : { bookmarks: upsert(s.bookmarks), trashed: without(s.trashed) }
  })
}

async function putCategory(c: Category): Promise<void> {
  await (await getDb()).put('categories', c)
  useData.setState((s) => {
    const rest = s.categories.filter((x) => x.id !== c.id)
    return { categories: sortCategories([...rest, c]) }
  })
}

async function removeBookmarkRows(ids: string[]): Promise<void> {
  const db = await getDb()
  const tx = db.transaction('bookmarks', 'readwrite')
  await Promise.all([...ids.map((id) => tx.store.delete(id)), tx.done])
  const gone = new Set(ids)
  useData.setState((s) => ({ bookmarks: s.bookmarks.filter((b) => !gone.has(b.id)), trashed: s.trashed.filter((b) => !gone.has(b.id)) }))
}

async function removeCategoryRow(id: string): Promise<void> {
  await (await getDb()).delete('categories', id)
  useData.setState((s) => ({ categories: s.categories.filter((c) => c.id !== id) }))
}

// ---------------------------------------------------------------------------
// Startup
// ---------------------------------------------------------------------------

export function newUnsorted(now = Date.now()): Category {
  return {
    id: UNSORTED_ID,
    name: 'Unsorted',
    colorHex: '#8F96C4',
    iconKey: null,
    sortOrder: 0,
    isDefault: true,
    createdAt: now,
    updatedAt: now,
    syncedUpdatedAt: null,
  }
}

export async function initData(): Promise<void> {
  const db = await getDb()
  let categories = await db.getAll('categories')
  if (!categories.some((c) => c.id === UNSORTED_ID)) {
    const unsorted = newUnsorted()
    await db.put('categories', unsorted)
    categories = [unsorted, ...categories]
  }
  const bookmarks = await db.getAll('bookmarks')
  // Rows saved before tags existed have no `tags` field.
  const rows = bookmarks.map((b) => ({ ...b, tags: b.tags ?? [], isUnread: b.isUnread ?? false, trashedAt: b.trashedAt ?? null }))
  useData.setState({
    loaded: true,
    bookmarks: rows.filter((b) => b.trashedAt === null),
    trashed: rows.filter((b) => b.trashedAt !== null),
    categories: sortCategories(categories),
  })
  await purgeExpiredTrash()
}

/** Wipes every local row and re-seeds Unsorted (sign-out / account switch). */
export async function clearAllData(): Promise<void> {
  const db = await getDb()
  const tx = db.transaction(['bookmarks', 'categories', 'tombstones', 'meta'], 'readwrite')
  await Promise.all([
    tx.objectStore('bookmarks').clear(),
    tx.objectStore('categories').clear(),
    tx.objectStore('tombstones').clear(),
    tx.objectStore('meta').delete('sync_cursor'),
    tx.objectStore('meta').delete('sync_last_synced_at'),
    tx.done,
  ])
  pending.clear()
  const unsorted = newUnsorted()
  await db.put('categories', unsorted)
  useData.setState({ bookmarks: [], categories: [unsorted] })
}

// ---------------------------------------------------------------------------
// Lookups
// ---------------------------------------------------------------------------

export const getCategory = (id: string): Category | undefined =>
  useData.getState().categories.find((c) => c.id === id)

/** The category a bookmark shows under; an orphaned id falls back to Unsorted. */
export function categoryOf(b: Bookmark, categories = useData.getState().categories): Category {
  return (
    categories.find((c) => c.id === b.categoryId) ??
    categories.find((c) => c.id === UNSORTED_ID) ??
    newUnsorted(0)
  )
}

export function defaultCategoryId(): string {
  return useData.getState().categories.find((c) => c.isDefault)?.id ?? UNSORTED_ID
}

/** A live bookmark with this URL (trashed ones do not count; see [saveBookmark]). */
export const findByUrl = (normalizedUrl: string): Bookmark | undefined =>
  useData.getState().bookmarks.find((b) => b.url === normalizedUrl)

// ---------------------------------------------------------------------------
// Bookmarks
// ---------------------------------------------------------------------------

export interface SaveInput {
  rawUrl: string
  title?: string
  description?: string
  categoryId: string
  tags?: string[]
  /** The Android share flow's subject; the PWA share target passes the shared title here. */
  sharedSubject?: string | null
  thumbnail?: ThumbnailChoice | null
}

export type SaveResult = { kind: 'saved'; bookmark: Bookmark } | { kind: 'duplicate'; existing: Bookmark }

export async function saveBookmark(input: SaveInput): Promise<SaveResult> {
  const url = normalize(input.rawUrl)
  const existing = findByUrl(url)
  if (existing) return { kind: 'duplicate', existing }
  // Saving a link that is in Trash brings it back instead of creating a second row.
  const inTrash = useData.getState().trashed.find((b) => b.url === url)
  if (inTrash) {
    const restored = { ...inTrash, trashedAt: null, updatedAt: Date.now() }
    await putBookmark(restored)
    notifyChanged()
    return { kind: 'saved', bookmark: restored }
  }

  const now = Date.now()
  const typedTitle = input.title?.trim() ?? ''
  const typedDescription = input.description?.trim() ?? ''
  const title = resolveTitle({ fetchedTitle: typedTitle, sharedSubject: input.sharedSubject, url }).slice(
    0,
    BookmarkLimits.title,
  )
  let manualFields = 0
  if (typedTitle) manualFields |= ManualField.TITLE
  if (typedDescription) manualFields |= ManualField.DESCRIPTION
  if (input.thumbnail) manualFields |= ManualField.THUMBNAIL

  const bookmark: Bookmark = {
    id: newId(),
    url,
    originalUrl: input.rawUrl.trim().slice(0, BookmarkLimits.url),
    title,
    description: typedDescription ? typedDescription.slice(0, BookmarkLimits.description) : null,
    siteName: fromDomain(url)?.slice(0, BookmarkLimits.siteName) ?? null,
    thumbnailUrl: null,
    thumbnailWidth: input.thumbnail?.width ?? null,
    thumbnailHeight: input.thumbnail?.height ?? null,
    accentColor: input.thumbnail?.accentColor ?? null,
    imageCandidates: [],
    tags: normalizeTags(input.tags ?? []),
    // New saves start unread; opening the link clears it.
    isUnread: true,
    trashedAt: null,
    categoryId: input.categoryId,
    manualFields,
    isPinned: false,
    createdAt: now,
    updatedAt: now,
    syncedUpdatedAt: null,
    pendingThumbnail: input.thumbnail?.blob ?? null,
  }
  await putBookmark(bookmark)
  notifyChanged()
  return { kind: 'saved', bookmark }
}

export interface EditInput {
  title: string
  description: string
  categoryId: string
  tags: string[]
  /** Whether the user edited the field in the sheet; touching a field locks it (Android parity). */
  titleEdited: boolean
  descriptionEdited: boolean
  /** undefined: unchanged; null: remove; a choice: replace. */
  thumbnail?: ThumbnailChoice | null
}

export async function updateBookmark(id: string, edit: EditInput): Promise<void> {
  const current = useData.getState().bookmarks.find((b) => b.id === id)
  if (!current) return
  // A blank title keeps the old one.
  const title = edit.title.trim() ? edit.title.trim().slice(0, BookmarkLimits.title) : current.title
  const description = edit.description.trim() ? edit.description.trim().slice(0, BookmarkLimits.description) : null
  let manualFields = current.manualFields
  if (edit.titleEdited) manualFields |= ManualField.TITLE
  if (edit.descriptionEdited) manualFields |= ManualField.DESCRIPTION

  const next: Bookmark = { ...current, title, description, categoryId: edit.categoryId, tags: normalizeTags(edit.tags), updatedAt: Date.now() }
  if (edit.thumbnail !== undefined) {
    manualFields |= ManualField.THUMBNAIL
    next.pendingThumbnail = edit.thumbnail?.blob ?? null
    next.thumbnailUrl = null
    next.thumbnailWidth = edit.thumbnail?.width ?? null
    next.thumbnailHeight = edit.thumbnail?.height ?? null
    next.accentColor = edit.thumbnail?.accentColor ?? null
  }
  next.manualFields = manualFields
  await putBookmark(next)
  notifyChanged()
}

export async function setUnread(id: string, unread: boolean): Promise<void> {
  const b = useData.getState().bookmarks.find((x) => x.id === id)
  if (!b || b.isUnread === unread) return
  await putBookmark({ ...b, isUnread: unread, updatedAt: Date.now() })
  notifyChanged()
}

export async function setPinned(id: string, pinned: boolean): Promise<void> {
  const b = useData.getState().bookmarks.find((x) => x.id === id)
  if (!b) return
  await putBookmark({ ...b, isPinned: pinned, updatedAt: Date.now() })
  notifyChanged()
}

// ---------------------------------------------------------------------------
// Bulk edits (selection mode). One sync notification for the whole batch.
// ---------------------------------------------------------------------------

async function updateMany(ids: string[], change: (b: Bookmark) => Bookmark): Promise<Bookmark[]> {
  const wanted = new Set(ids)
  const now = Date.now()
  const before = useData.getState().bookmarks.filter((b) => wanted.has(b.id))
  for (const b of before) await putBookmark({ ...change(b), updatedAt: now })
  if (before.length > 0) notifyChanged()
  return before
}

export const setPinnedMany = (ids: string[], pinned: boolean) => updateMany(ids, (b) => ({ ...b, isPinned: pinned }))

export const moveMany = (ids: string[], categoryId: string) => updateMany(ids, (b) => ({ ...b, categoryId }))

/** Adds [tags] to each bookmark, keeping its existing tags (normalised, capped at 10). */
export const addTagsMany = (ids: string[], tags: string[]) =>
  updateMany(ids, (b) => ({ ...b, tags: normalizeTags([...b.tags, ...tags]) }))

/** Moves every bookmark in [ids] to Trash; the returned function restores exactly those. */
export async function trashMany(ids: string[]): Promise<{ count: number; undo: () => Promise<void> }> {
  const moved = await updateMany(ids, (b) => ({ ...b, trashedAt: Date.now() }))
  return {
    count: moved.length,
    undo: async () => {
      for (const b of moved) await restoreFromTrash(b.id)
    },
  }
}

/** Applies fetched preview fields and marks the row dirty so they sync to the other devices. */
export async function applyFetchedMetadata(id: string, patch: Partial<Bookmark>): Promise<void> {
  const b = useData.getState().bookmarks.find((x) => x.id === id)
  if (!b) return
  await putBookmark({ ...b, ...patch, updatedAt: Date.now() })
  notifyChanged()
}

/** Local-only dimension/accent update after a thumbnail upload succeeds. Does not dirty the row. */
export async function patchBookmarkLocal(id: string, patch: Partial<Bookmark>): Promise<void> {
  const b = useData.getState().bookmarks.find((x) => x.id === id)
  if (b) await putBookmark({ ...b, ...patch })
}

// ---------------------------------------------------------------------------
// Deletion with undo. The tombstone (what tells the server) is written only
// when the undo window closes, so an undone delete leaves no trace.
// ---------------------------------------------------------------------------

export interface Undoable {
  /** Puts everything back. No-op once committed. */
  undo(): Promise<void>
  /** Closes the undo window and records tombstones. Safe to call twice. */
  commit(): Promise<void>
}

const pending = new Set<Undoable>()

export async function flushPendingDeletes(): Promise<void> {
  await Promise.all([...pending].map((u) => u.commit()))
}

function makeUndoable(restore: () => Promise<void>, tombstones: Tombstone[]): Undoable {
  let done = false
  const u: Undoable = {
    async undo() {
      if (done) return
      done = true
      pending.delete(u)
      await restore()
      notifyChanged()
    },
    async commit() {
      if (done) return
      done = true
      pending.delete(u)
      const db = await getDb()
      const tx = db.transaction('tombstones', 'readwrite')
      await Promise.all([...tombstones.map((t) => tx.store.put(t)), tx.done])
      notifyChanged()
    },
  }
  pending.add(u)
  return u
}

/**
 * Moves a bookmark to Trash (a soft delete that syncs); [Undoable.undo] puts it
 * back. Nothing is final until it is deleted from Trash or the 30 days pass.
 */
export async function deleteBookmark(id: string): Promise<{ bookmark: Bookmark; undoable: Undoable } | null> {
  const b = useData.getState().bookmarks.find((x) => x.id === id)
  if (!b) return null
  await putBookmark({ ...b, trashedAt: Date.now(), updatedAt: Date.now() })
  notifyChanged()
  const undoable: Undoable = {
    undo: () => restoreFromTrash(id),
    commit: async () => {},
  }
  return { bookmark: b, undoable }
}

export async function restoreFromTrash(id: string): Promise<void> {
  const b = useData.getState().trashed.find((x) => x.id === id)
  if (!b) return
  await putBookmark({ ...b, trashedAt: null, updatedAt: Date.now() })
  notifyChanged()
}

/** Removes trashed bookmarks for good and records tombstones so the server drops them too. */
export async function deleteForever(ids: string[]): Promise<void> {
  const trashedIds = new Set(useData.getState().trashed.map((b) => b.id))
  const targets = ids.filter((id) => trashedIds.has(id))
  if (targets.length === 0) return
  const db = await getDb()
  const tx = db.transaction('tombstones', 'readwrite')
  const now = Date.now()
  await Promise.all([...targets.map((id) => tx.store.put({ id, kind: 'bookmark' as const, deletedAt: now })), tx.done])
  await removeBookmarkRows(targets)
  notifyChanged()
}

export const emptyTrash = (): Promise<void> => deleteForever(useData.getState().trashed.map((b) => b.id))

/** On startup: anything trashed more than the retention period ago goes for good. */
export async function purgeExpiredTrash(now = Date.now()): Promise<void> {
  const cutoff = trashCutoff(now)
  await deleteForever(useData.getState().trashed.filter((b) => (b.trashedAt ?? now) < cutoff).map((b) => b.id))
}

// ---------------------------------------------------------------------------
// Categories
// ---------------------------------------------------------------------------

export type CategoryError =
  | 'BLANK_NAME'
  | 'DUPLICATE_NAME'
  | 'CANNOT_DELETE_FALLBACK'
  | 'CANNOT_DELETE_DEFAULT'

export type CategoryResult = { ok: true; category: Category } | { ok: false; error: CategoryError }

const nameTaken = (name: string, exceptId?: string) =>
  useData.getState().categories.some((c) => c.id !== exceptId && c.name.toLowerCase() === name.toLowerCase())

export async function createCategory(
  name: string,
  colorHex?: string,
  iconKey: string | null = null,
): Promise<CategoryResult> {
  const trimmed = name.trim().slice(0, CATEGORY_NAME_MAX)
  if (!trimmed) return { ok: false, error: 'BLANK_NAME' }
  if (nameTaken(trimmed)) return { ok: false, error: 'DUPLICATE_NAME' }
  const { categories } = useData.getState()
  const now = Date.now()
  const category: Category = {
    id: newId(),
    name: trimmed,
    colorHex: colorHex ?? CATEGORY_SWATCHES[categories.length % CATEGORY_SWATCHES.length],
    iconKey,
    sortOrder: Math.max(-1, ...categories.map((c) => c.sortOrder)) + 1,
    isDefault: false,
    createdAt: now,
    updatedAt: now,
    syncedUpdatedAt: null,
  }
  await putCategory(category)
  notifyChanged()
  return { ok: true, category }
}

export async function updateCategory(
  id: string,
  name: string,
  colorHex: string,
  iconKey: string | null,
): Promise<CategoryResult> {
  const current = getCategory(id)
  if (!current) return { ok: false, error: 'BLANK_NAME' }
  const trimmed = name.trim().slice(0, CATEGORY_NAME_MAX)
  if (!trimmed) return { ok: false, error: 'BLANK_NAME' }
  if (nameTaken(trimmed, id)) return { ok: false, error: 'DUPLICATE_NAME' }
  const next = { ...current, name: trimmed, colorHex, iconKey, updatedAt: Date.now() }
  await putCategory(next)
  notifyChanged()
  return { ok: true, category: next }
}

/** Persists a new order; device-local, so rows are not marked dirty. */
export async function reorderCategories(orderedIds: string[]): Promise<void> {
  const db = await getDb()
  const byId = new Map(useData.getState().categories.map((c) => [c.id, c]))
  const updated: Category[] = []
  orderedIds.forEach((id, index) => {
    const c = byId.get(id)
    if (c) updated.push({ ...c, sortOrder: index })
  })
  const tx = db.transaction('categories', 'readwrite')
  await Promise.all([...updated.map((c) => tx.store.put(c)), tx.done])
  useData.setState({ categories: sortCategories(updated) })
}

export async function setDefaultCategory(id: string): Promise<void> {
  const now = Date.now()
  for (const c of useData.getState().categories) {
    if (c.id === id && !c.isDefault) await putCategory({ ...c, isDefault: true, updatedAt: now })
    else if (c.id !== id && c.isDefault) await putCategory({ ...c, isDefault: false, updatedAt: now })
  }
  notifyChanged()
}

export type DeleteStrategy = { kind: 'move'; targetId: string } | { kind: 'deleteBookmarks' }

export async function deleteCategory(
  id: string,
  strategy: DeleteStrategy,
): Promise<{ ok: true; category: Category; undoable: Undoable } | { ok: false; error: CategoryError }> {
  const category = getCategory(id)
  if (!category) return { ok: false, error: 'BLANK_NAME' }
  if (id === UNSORTED_ID) return { ok: false, error: 'CANNOT_DELETE_FALLBACK' }
  if (category.isDefault) return { ok: false, error: 'CANNOT_DELETE_DEFAULT' }

  const now = Date.now()
  // Trashed bookmarks count too: they must not be left pointing at a deleted category.
  const members = allBookmarks().filter((b) => b.categoryId === id)
  const tombstones: Tombstone[] = [{ id, kind: 'category', deletedAt: now }]

  if (strategy.kind === 'move') {
    for (const b of members) await putBookmark({ ...b, categoryId: strategy.targetId, updatedAt: now })
  } else {
    await removeBookmarkRows(members.map((b) => b.id))
    members.forEach((b) => tombstones.push({ id: b.id, kind: 'bookmark', deletedAt: now }))
  }
  await removeCategoryRow(id)

  const undoable = makeUndoable(async () => {
    await putCategory(category)
    for (const b of members) await putBookmark(b)
  }, tombstones)
  return { ok: true, category, undoable }
}

export function bookmarkCountByCategory(
  bookmarks: Bookmark[] = useData.getState().bookmarks,
  categories: Category[] = useData.getState().categories,
): Map<string, number> {
  const counts = new Map<string, number>()
  const known = new Set(categories.map((c) => c.id))
  for (const b of bookmarks) {
    const key = known.has(b.categoryId) ? b.categoryId : UNSORTED_ID
    counts.set(key, (counts.get(key) ?? 0) + 1)
  }
  return counts
}

// ---------------------------------------------------------------------------
// Internals the sync engine needs
// ---------------------------------------------------------------------------

export const internal = { putBookmark, putCategory, removeBookmarkRows, removeCategoryRow, notifyChanged }
