import { create } from 'zustand'
import { useSession } from '@/account/session'
import { API_BASE, ApiError, NetworkError } from '@/api/client'
import type { BookmarkDto, CategoryDto, PushRequest, PushResponse, PullResponse } from '@/api/dto'
import { SyncApi } from '@/api/endpoints'
import { getDb, getMeta, setMeta } from '@/data/db'
import { type Bookmark, type Category, UNSORTED_ID, isDirty } from '@/data/models'
import { flushPendingDeletes, internal, onLocalChange, useData } from '@/data/repo'

const PUSH_CHUNK = 500
const DEBOUNCE_MS = 15_000
const FOCUS_THROTTLE_MS = 30_000

// ---------------------------------------------------------------------------
// Status for the Settings screen
// ---------------------------------------------------------------------------

interface SyncStatus {
  syncing: boolean
  lastSyncedAt: number | null
  error: string | null
}
export const useSyncStatus = create<SyncStatus>(() => ({ syncing: false, lastSyncedAt: null, error: null }))

export async function loadSyncStatus(): Promise<void> {
  useSyncStatus.setState({ lastSyncedAt: (await getMeta<number>('sync_last_synced_at')) ?? null })
}

// ---------------------------------------------------------------------------
// DTO mapping
// ---------------------------------------------------------------------------

export const categoryToDto = (c: Category): CategoryDto => ({
  id: c.id,
  name: c.name,
  color_hex: c.colorHex,
  icon_key: c.iconKey,
  is_default: c.isDefault,
  created_at: c.createdAt,
  updated_at: c.updatedAt,
})

export const bookmarkToDto = (b: Bookmark): BookmarkDto => ({
  id: b.id,
  url: b.url,
  original_url: b.originalUrl,
  title: b.title,
  description: b.description,
  site_name: b.siteName,
  thumbnail_url: b.thumbnailUrl,
  thumbnail_width: b.thumbnailWidth,
  thumbnail_height: b.thumbnailHeight,
  accent_color: b.accentColor,
  image_candidates: b.imageCandidates,
  category_id: b.categoryId,
  manual_fields: b.manualFields,
  is_pinned: b.isPinned,
  created_at: b.createdAt,
  updated_at: b.updatedAt,
})

/** Only URLs on our own API host are ever treated as thumbnails (never hot-link or fetch elsewhere). */
const isBackendUrl = (u: string | null): u is string => u !== null && u.startsWith(`${API_BASE}/`)

const bookmarkFromDto = (d: BookmarkDto): Bookmark => ({
  id: d.id,
  url: d.url,
  originalUrl: d.original_url,
  title: d.title,
  description: d.description,
  siteName: d.site_name,
  thumbnailUrl: isBackendUrl(d.thumbnail_url) ? d.thumbnail_url : null,
  thumbnailWidth: d.thumbnail_width,
  thumbnailHeight: d.thumbnail_height,
  accentColor: d.accent_color,
  imageCandidates: d.image_candidates ?? [],
  categoryId: d.category_id,
  manualFields: d.manual_fields,
  isPinned: d.is_pinned,
  createdAt: d.created_at,
  updatedAt: d.updated_at,
  syncedUpdatedAt: d.updated_at,
  pendingThumbnail: null,
})

// ---------------------------------------------------------------------------
// Push
// ---------------------------------------------------------------------------

const chunk = <T>(xs: T[], n: number): T[][] => {
  const out: T[][] = []
  for (let i = 0; i < xs.length; i += n) out.push(xs.slice(i, i + n))
  return out
}

const EMPTY_PUSH: PushRequest = { categories: [], bookmarks: [], deleted_category_ids: [], deleted_bookmark_ids: [] }

async function markCategoriesSynced(sent: Map<string, number>, res: PushResponse) {
  const rejected = new Set(res.categories.rejected.map((r) => r.id))
  for (const [id, updatedAt] of sent) {
    if (rejected.has(id)) continue
    const c = useData.getState().categories.find((x) => x.id === id)
    // Skip if the user edited the row while the request was in flight: it stays dirty.
    if (c && c.updatedAt === updatedAt) await internal.putCategory({ ...c, syncedUpdatedAt: updatedAt })
  }
  logRejections('category', res.categories.rejected)
}

async function markBookmarksSynced(sent: Map<string, number>, res: PushResponse) {
  const rejected = new Set(res.bookmarks.rejected.map((r) => r.id))
  for (const [id, updatedAt] of sent) {
    if (rejected.has(id)) continue
    const b = useData.getState().bookmarks.find((x) => x.id === id)
    if (b && b.updatedAt === updatedAt) await internal.putBookmark({ ...b, syncedUpdatedAt: updatedAt })
  }
  logRejections('bookmark', res.bookmarks.rejected)
}

function logRejections(kind: string, rejected: { id: string | null; reason: string }[]) {
  for (const r of rejected) console.warn(`[sync] server rejected ${kind} ${r.id}: ${r.reason}`)
}

/** Uploads a thumbnail picked on this device, returning the row with its remote URL. */
async function ensureThumbnailUploaded(b: Bookmark): Promise<Bookmark> {
  if (!b.pendingThumbnail || b.thumbnailUrl) return b
  try {
    const { url } = await SyncApi.uploadThumbnail(b.id, b.pendingThumbnail)
    if (!isBackendUrl(url)) return b
    const uploaded = { ...b, thumbnailUrl: url, pendingThumbnail: null }
    await internal.putBookmark(uploaded)
    return uploaded
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) throw e
    // A failed upload is retried next cycle: the blob stays on the row.
    console.warn('[sync] thumbnail upload failed', e)
    return b
  }
}

export async function push(): Promise<void> {
  const db = await getDb()
  const { categories, bookmarks } = useData.getState()
  const dirtyCategories = categories.filter(isDirty)
  const dirtyBookmarks = bookmarks.filter(isDirty)
  const tombstones = await db.getAll('tombstones')

  // Categories first so a bookmark pointing at a new category is accepted.
  for (const part of chunk(dirtyCategories, PUSH_CHUNK)) {
    const res = await SyncApi.push({ ...EMPTY_PUSH, categories: part.map(categoryToDto) })
    await markCategoriesSynced(new Map(part.map((c) => [c.id, c.updatedAt])), res)
  }

  for (const part of chunk(dirtyBookmarks, PUSH_CHUNK)) {
    const prepared: Bookmark[] = []
    for (const b of part) prepared.push(await ensureThumbnailUploaded(b))
    const res = await SyncApi.push({ ...EMPTY_PUSH, bookmarks: prepared.map(bookmarkToDto) })
    await markBookmarksSynced(new Map(prepared.map((b) => [b.id, b.updatedAt])), res)
  }

  // Deletions last: moved bookmarks were already upserted above.
  const bookmarkTombs = tombstones.filter((t) => t.kind === 'bookmark')
  const categoryTombs = tombstones.filter((t) => t.kind === 'category')
  const clearTombstones = async (ids: string[]) => {
    const tx = db.transaction('tombstones', 'readwrite')
    await Promise.all([...ids.map((id) => tx.store.delete(id)), tx.done])
  }
  for (const part of chunk(bookmarkTombs, PUSH_CHUNK)) {
    const res = await SyncApi.push({
      ...EMPTY_PUSH,
      deleted_bookmark_ids: part.map((t) => ({ id: t.id, deleted_at: t.deletedAt })),
    })
    logRejections('bookmark delete', res.bookmarks.rejected)
    // A "stale" rejection means the server row changed after our delete, so pull will
    // bring it back; retrying the delete forever would never succeed.
    await clearTombstones(part.map((t) => t.id))
  }
  for (const part of chunk(categoryTombs, PUSH_CHUNK)) {
    const res = await SyncApi.push({
      ...EMPTY_PUSH,
      deleted_category_ids: part.map((t) => ({ id: t.id, deleted_at: t.deletedAt })),
    })
    logRejections('category delete', res.categories.rejected)
    await clearTombstones(part.map((t) => t.id))
  }
}

// ---------------------------------------------------------------------------
// Pull
// ---------------------------------------------------------------------------

export async function applyPullPage(page: PullResponse): Promise<void> {
  // 1. Categories (so bookmarks below can resolve their category).
  for (const dto of page.categories.upserts) {
    const local = useData.getState().categories.find((c) => c.id === dto.id)
    if (local && isDirty(local) && local.updatedAt > dto.updated_at) continue // local edit is newer
    const sortOrder = local?.sortOrder ?? Math.max(-1, ...useData.getState().categories.map((c) => c.sortOrder)) + 1
    if (dto.is_default) {
      // Only one default at a time locally; the server does not enforce it.
      for (const other of useData.getState().categories) {
        if (other.id !== dto.id && other.isDefault) await internal.putCategory({ ...other, isDefault: false })
      }
    }
    await internal.putCategory({
      id: dto.id,
      name: dto.name,
      colorHex: dto.color_hex,
      iconKey: dto.icon_key,
      sortOrder,
      isDefault: dto.is_default,
      createdAt: dto.created_at,
      updatedAt: dto.updated_at,
      syncedUpdatedAt: dto.updated_at,
    })
  }

  // 2. Bookmarks.
  for (const dto of page.bookmarks.upserts) {
    const local = useData.getState().bookmarks.find((b) => b.id === dto.id)
    if (local && isDirty(local) && local.updatedAt > dto.updated_at) continue
    const incoming = bookmarkFromDto(dto)
    // Keep a thumbnail picked here that has not been uploaded yet.
    if (local?.pendingThumbnail && !incoming.thumbnailUrl) incoming.pendingThumbnail = local.pendingThumbnail
    await internal.putBookmark(incoming)
  }

  // 3. Category deletes: orphans go to Unsorted and are pushed on the next cycle.
  for (const del of page.categories.deletes) {
    if (del.id === UNSORTED_ID) continue
    const now = Date.now()
    for (const b of useData.getState().bookmarks.filter((x) => x.categoryId === del.id)) {
      await internal.putBookmark({ ...b, categoryId: UNSORTED_ID, updatedAt: now })
    }
    await internal.removeCategoryRow(del.id)
  }

  // 4. Bookmark deletes.
  const gone = page.bookmarks.deletes.map((d) => d.id)
  if (gone.length) await internal.removeBookmarkRows(gone)
}

export async function pull(): Promise<void> {
  let cursor = (await getMeta<number>('sync_cursor')) ?? 0
  for (;;) {
    const page = await SyncApi.pull(cursor)
    await applyPullPage(page)
    // The server's `since` is inclusive, and many rows can share one millisecond, so an
    // unchanged cursor with has_more would loop forever; stop instead.
    const advanced = page.next_since > cursor
    cursor = Math.max(cursor, page.next_since)
    await setMeta('sync_cursor', cursor)
    if (!page.has_more || !advanced) break
  }
}

// ---------------------------------------------------------------------------
// One cycle + scheduling
// ---------------------------------------------------------------------------

let inFlight: Promise<void> | null = null
let rerun = false

export function syncNow(): Promise<void> {
  if (!useSession.getState().token) return Promise.resolve()
  if (inFlight) {
    rerun = true // changes made mid-sync get their own pass
    return inFlight
  }
  inFlight = (async () => {
    useSyncStatus.setState({ syncing: true })
    try {
      do {
        rerun = false
        await push()
        await pull()
      } while (rerun)
      const now = Date.now()
      await setMeta('sync_last_synced_at', now)
      useSyncStatus.setState({ lastSyncedAt: now, error: null })
    } catch (e) {
      const message =
        e instanceof ApiError || e instanceof NetworkError ? e.message : "Couldn't sync. We'll try again shortly."
      if (!(e instanceof ApiError && e.status === 401)) console.warn('[sync] failed', e)
      useSyncStatus.setState({ error: e instanceof ApiError && e.status === 401 ? null : message })
    } finally {
      useSyncStatus.setState({ syncing: false })
      inFlight = null
    }
  })()
  return inFlight
}

/** Wires up the triggers: local edits (debounced), tab focus, coming back online, sign-in. */
export function startSyncScheduler(): () => void {
  let timer: ReturnType<typeof setTimeout> | undefined
  let lastRun = 0
  const run = () => {
    lastRun = Date.now()
    void syncNow()
  }
  const offChange = onLocalChange(() => {
    if (!useSession.getState().token) return
    clearTimeout(timer)
    timer = setTimeout(run, DEBOUNCE_MS)
  })
  const onVisible = () => {
    if (document.visibilityState === 'visible' && Date.now() - lastRun > FOCUS_THROTTLE_MS) run()
  }
  const onPageHide = () => void flushPendingDeletes()
  document.addEventListener('visibilitychange', onVisible)
  window.addEventListener('online', run)
  window.addEventListener('pagehide', onPageHide)
  const offSession = useSession.subscribe((s, prev) => {
    if (s.token && s.token !== prev.token) run()
  })
  if (useSession.getState().token) run()

  return () => {
    clearTimeout(timer)
    offChange()
    offSession()
    document.removeEventListener('visibilitychange', onVisible)
    window.removeEventListener('online', run)
    window.removeEventListener('pagehide', onPageHide)
  }
}
