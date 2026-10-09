import { useSession } from '@/account/session'
import type { BookmarkDto, CategoryDto, PullResponse, PushRequest, PushResponse } from '@/api/dto'
import { resetDbForTests } from '@/data/db'
import { UNSORTED_ID } from '@/data/models'
import { clearAllData, createCategory, deleteBookmark, deleteCategory, initData, saveBookmark, useData } from '@/data/repo'
import { pull, push, syncNow } from './engine'

/** A tiny in-memory server with the real LWW / soft-delete rules of push.php and pull.php. */
class FakeServer {
  cats = new Map<string, CategoryDto & { deleted_at?: number }>()
  books = new Map<string, BookmarkDto & { deleted_at?: number }>()
  pushes: PushRequest[] = []
  pageLimit = 500
  unauthorized = false

  handle(url: URL, init?: RequestInit): Response {
    if (this.unauthorized) return json({ error: { code: 'UNAUTHORIZED', message: 'no' } }, 401)
    if (url.pathname.endsWith('/sync/push.php')) return json(this.push(JSON.parse(init!.body as string)))
    if (url.pathname.endsWith('/sync/pull.php')) return json(this.pull(Number(url.searchParams.get('since'))))
    return json({ error: { code: 'NOT_FOUND', message: '' } }, 404)
  }

  push(req: PushRequest): PushResponse {
    this.pushes.push(req)
    const out: PushResponse = { categories: { applied: [], rejected: [] }, bookmarks: { applied: [], rejected: [] } }
    for (const c of req.categories) {
      const e = this.cats.get(c.id)
      if (!e || c.updated_at > e.updated_at) {
        this.cats.set(c.id, { ...c, deleted_at: e?.deleted_at })
        out.categories.applied.push(c.id)
      } else out.categories.rejected.push({ id: c.id, reason: 'stale' })
    }
    for (const b of req.bookmarks) {
      const e = this.books.get(b.id)
      if (!this.cats.has(b.category_id)) {
        out.bookmarks.rejected.push({ id: b.id, reason: 'unknown_category' })
      } else if (!e || b.updated_at > e.updated_at) {
        this.books.set(b.id, { ...b, deleted_at: e?.deleted_at })
        out.bookmarks.applied.push(b.id)
      } else out.bookmarks.rejected.push({ id: b.id, reason: 'stale' })
    }
    for (const d of req.deleted_category_ids) {
      const e = this.cats.get(d.id)
      if (e) this.cats.set(d.id, { ...e, deleted_at: d.deleted_at, updated_at: d.deleted_at })
      out.categories.applied.push(d.id)
    }
    for (const d of req.deleted_bookmark_ids) {
      const e = this.books.get(d.id)
      if (e) this.books.set(d.id, { ...e, deleted_at: d.deleted_at, updated_at: d.deleted_at })
      out.bookmarks.applied.push(d.id)
    }
    return out
  }

  pull(since: number): PullResponse {
    const sorted = <T extends { updated_at: number }>(m: Map<string, T>) =>
      [...m.values()].filter((r) => r.updated_at >= since).sort((a, b) => a.updated_at - b.updated_at).slice(0, this.pageLimit)
    const cats = sorted(this.cats)
    const books = sorted(this.books)
    const del = (r: { id: string; deleted_at?: number }) => ({ id: r.id, deleted_at: r.deleted_at! })
    return {
      has_more: cats.length === this.pageLimit || books.length === this.pageLimit,
      next_since: Math.max(since, ...cats.map((c) => c.updated_at), ...books.map((b) => b.updated_at)),
      categories: { upserts: cats.filter((c) => c.deleted_at === undefined), deletes: cats.filter((c) => c.deleted_at !== undefined).map(del) },
      bookmarks: { upserts: books.filter((b) => b.deleted_at === undefined), deletes: books.filter((b) => b.deleted_at !== undefined).map(del) },
    }
  }
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

let server: FakeServer

const serverBookmark = (over: Partial<BookmarkDto>): BookmarkDto => ({
  id: 'b-remote', url: 'https://remote.example', original_url: 'https://remote.example', title: 'Remote',
  description: null, site_name: null, thumbnail_url: null, thumbnail_width: null, thumbnail_height: null,
  accent_color: null, image_candidates: [], category_id: UNSORTED_ID, manual_fields: 0, is_pinned: false,
  created_at: 1000, updated_at: 1000, ...over,
})

beforeEach(async () => {
  await resetDbForTests()
  await initData()
  server = new FakeServer()
  server.cats.set(UNSORTED_ID, { id: UNSORTED_ID, name: 'Unsorted', color_hex: '#9E9E9E', icon_key: null, is_default: false, created_at: 1, updated_at: 1 })
  vi.stubGlobal('fetch', async (input: URL | string, init?: RequestInit) => server.handle(new URL(String(input)), init))
  useSession.getState().setSession({ token: 't', email: 'a@b.c', deviceId: 1 })
})

afterEach(() => {
  vi.unstubAllGlobals()
  useSession.getState().clear()
})

describe('sync engine', () => {
  it('pushes a new bookmark and marks it synced', async () => {
    const res = await saveBookmark({ rawUrl: 'https://example.com/a', categoryId: UNSORTED_ID })
    expect(res.kind).toBe('saved')
    await push()
    expect(server.books.size).toBe(1)
    const local = useData.getState().bookmarks[0]
    expect(local.syncedUpdatedAt).toBe(local.updatedAt)
    const sent = server.pushes.flatMap((p) => p.bookmarks)[0]
    expect(sent.thumbnail_url).toBeNull()
    expect(sent.manual_fields).toBe(0) // no thumbnail bit, so Android is free to fetch one
  })

  it('pushes new categories before the bookmarks that use them', async () => {
    const cat = await createCategory('Reading')
    if (!cat.ok) throw new Error()
    await saveBookmark({ rawUrl: 'https://example.com/b', categoryId: cat.category.id })
    await push()
    expect(server.books.size).toBe(1)
    expect(server.cats.has(cat.category.id)).toBe(true)
  })

  it('pulls server rows and keeps them out of the dirty set', async () => {
    server.books.set('b-remote', serverBookmark({}))
    await pull()
    const b = useData.getState().bookmarks.find((x) => x.id === 'b-remote')!
    expect(b.title).toBe('Remote')
    expect(b.syncedUpdatedAt).toBe(1000)
  })

  it('ignores thumbnail urls that are not on the API host', async () => {
    server.books.set('b-remote', serverBookmark({ thumbnail_url: 'https://evil.example/x.webp' }))
    await pull()
    expect(useData.getState().bookmarks[0].thumbnailUrl).toBeNull()
  })

  it('does not overwrite a newer unsynced local edit', async () => {
    server.books.set('b-remote', serverBookmark({ updated_at: 1000 }))
    await pull()
    const b = useData.getState().bookmarks[0]
    await (await import('@/data/repo')).internal.putBookmark({ ...b, title: 'Edited here', updatedAt: 5000 })
    await pull() // cursor is 1000 so the row comes back
    expect(useData.getState().bookmarks[0].title).toBe('Edited here')
  })

  it('removes bookmarks the server deleted', async () => {
    server.books.set('b-remote', serverBookmark({}))
    await pull()
    server.books.set('b-remote', { ...serverBookmark({}), updated_at: 2000, deleted_at: 2000 })
    await pull()
    expect(useData.getState().bookmarks).toHaveLength(0)
  })

  it('moves bookmarks of a server-deleted category to Unsorted and re-pushes them', async () => {
    server.cats.set('c1', { id: 'c1', name: 'Dev', color_hex: '#38BDF8', icon_key: null, is_default: false, created_at: 10, updated_at: 10 })
    server.books.set('b1', serverBookmark({ id: 'b1', category_id: 'c1', updated_at: 20 }))
    await pull()
    server.cats.set('c1', { ...server.cats.get('c1')!, deleted_at: 3000, updated_at: 3000 })
    await pull()
    const b = useData.getState().bookmarks.find((x) => x.id === 'b1')!
    expect(b.categoryId).toBe(UNSORTED_ID)
    expect(useData.getState().categories.some((c) => c.id === 'c1')).toBe(false)
    await push()
    expect(server.books.get('b1')!.category_id).toBe(UNSORTED_ID)
  })

  it('sends a delete only after the undo window closes', async () => {
    await saveBookmark({ rawUrl: 'https://example.com/c', categoryId: UNSORTED_ID })
    await push()
    const id = useData.getState().bookmarks[0].id
    const del = await deleteBookmark(id)
    await push()
    expect(server.books.get(id)!.deleted_at).toBeUndefined() // still undoable
    await del!.undoable.undo()
    expect(useData.getState().bookmarks).toHaveLength(1)

    const del2 = await deleteBookmark(id)
    await del2!.undoable.commit()
    await push()
    expect(server.books.get(id)!.deleted_at).toBeDefined()
  })

  it('deleting a category with its bookmarks tombstones both', async () => {
    const cat = await createCategory('Temp')
    if (!cat.ok) throw new Error()
    await saveBookmark({ rawUrl: 'https://example.com/d', categoryId: cat.category.id })
    await push()
    const r = await deleteCategory(cat.category.id, { kind: 'deleteBookmarks' })
    if (!r.ok) throw new Error()
    await r.undoable.commit()
    await push()
    expect(server.cats.get(cat.category.id)!.deleted_at).toBeDefined()
    expect([...server.books.values()][0].deleted_at).toBeDefined()
  })

  it('stops paging when the cursor cannot advance', async () => {
    for (let i = 0; i < 4; i++) server.books.set(`b${i}`, serverBookmark({ id: `b${i}`, url: `https://x${i}.example`, updated_at: 500 }))
    server.pageLimit = 2 // two full pages of identical timestamps would loop forever
    await pull()
    expect(useData.getState().bookmarks.length).toBe(2)
  })

  it('chunks pushes at 500 rows', async () => {
    const { internal } = await import('@/data/repo')
    await clearAllData()
    const rows = Array.from({ length: 1100 }, (_, i) => ({
      id: `n${i}`, url: `https://n${i}.example`, originalUrl: `https://n${i}.example`, title: `T${i}`, description: null,
      siteName: null, thumbnailUrl: null, thumbnailWidth: null, thumbnailHeight: null, accentColor: null,
      imageCandidates: [], categoryId: UNSORTED_ID, manualFields: 0, isPinned: false, createdAt: 1, updatedAt: 2, syncedUpdatedAt: null,
    }))
    for (const r of rows) await internal.putBookmark(r)
    await push()
    const sizes = server.pushes.map((p) => p.bookmarks.length).filter(Boolean)
    expect(Math.max(...sizes)).toBeLessThanOrEqual(500)
    expect(server.books.size).toBe(1100)
  })

  it('a 401 ends the session', async () => {
    server.unauthorized = true
    await syncNow()
    expect(useSession.getState().token).toBeNull()
    expect(useSession.getState().expired).toBe(true)
  })
})
