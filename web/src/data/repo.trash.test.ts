import { getDb, resetDbForTests } from '@/data/db'
import { UNSORTED_ID } from '@/data/models'
import { clearAllData, deleteBookmark, deleteForever, emptyTrash, initData, purgeExpiredTrash, restoreFromTrash, saveBookmark, useData } from '@/data/repo'
import { TRASH_RETENTION_DAYS } from '@/data/trash'

const DAY = 24 * 60 * 60 * 1000

describe('trash', () => {
  beforeEach(async () => {
    await resetDbForTests()
    await clearAllData()
    await initData()
  })

  it('saving a link that is in Trash restores it instead of creating a second row', async () => {
    const first = await saveBookmark({ rawUrl: 'https://example.com/a', categoryId: UNSORTED_ID })
    if (first.kind !== 'saved') throw new Error()
    await deleteBookmark(first.bookmark.id)
    const again = await saveBookmark({ rawUrl: 'https://example.com/a', categoryId: UNSORTED_ID })
    expect(again.kind).toBe('saved')
    expect(useData.getState().bookmarks.map((b) => b.id)).toEqual([first.bookmark.id])
    expect(useData.getState().trashed).toHaveLength(0)
  })

  it('restoreFromTrash puts it back and marks it dirty', async () => {
    const r = await saveBookmark({ rawUrl: 'https://example.com/b', categoryId: UNSORTED_ID })
    if (r.kind !== 'saved') throw new Error()
    await deleteBookmark(r.bookmark.id)
    await restoreFromTrash(r.bookmark.id)
    const b = useData.getState().bookmarks[0]
    expect(b.trashedAt).toBeNull()
    expect(b.updatedAt).toBeGreaterThanOrEqual(r.bookmark.updatedAt)
  })

  it('emptyTrash removes every trashed row and records tombstones', async () => {
    for (const p of ['x', 'y']) {
      const r = await saveBookmark({ rawUrl: `https://example.com/${p}`, categoryId: UNSORTED_ID })
      if (r.kind !== 'saved') throw new Error()
      await deleteBookmark(r.bookmark.id)
    }
    await emptyTrash()
    expect(useData.getState().trashed).toHaveLength(0)
    expect(await (await getDb()).getAll('tombstones')).toHaveLength(2)
  })

  it('purgeExpiredTrash only removes bookmarks trashed more than 30 days ago', async () => {
    const now = Date.now()
    const old = await saveBookmark({ rawUrl: 'https://example.com/old', categoryId: UNSORTED_ID })
    const fresh = await saveBookmark({ rawUrl: 'https://example.com/fresh', categoryId: UNSORTED_ID })
    if (old.kind !== 'saved' || fresh.kind !== 'saved') throw new Error()
    await deleteBookmark(old.bookmark.id)
    await deleteBookmark(fresh.bookmark.id)
    // Age the first one past the retention period.
    useData.setState((s) => ({ trashed: s.trashed.map((b) => (b.id === old.bookmark.id ? { ...b, trashedAt: now - (TRASH_RETENTION_DAYS + 1) * DAY } : b)) }))
    await purgeExpiredTrash(now)
    expect(useData.getState().trashed.map((b) => b.id)).toEqual([fresh.bookmark.id])
  })

  it('deleteForever ignores ids that are not in Trash', async () => {
    const r = await saveBookmark({ rawUrl: 'https://example.com/live', categoryId: UNSORTED_ID })
    if (r.kind !== 'saved') throw new Error()
    await deleteForever([r.bookmark.id])
    expect(useData.getState().bookmarks).toHaveLength(1)
  })
})
