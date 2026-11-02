import { resetDbForTests } from '@/data/db'
import { UNSORTED_ID } from '@/data/models'
import { addTagsMany, clearAllData, createCategory, initData, moveMany, saveBookmark, setPinnedMany, trashMany, useData } from '@/data/repo'

async function seed(...paths: string[]): Promise<string[]> {
  const ids: string[] = []
  for (const p of paths) {
    const r = await saveBookmark({ rawUrl: `https://example.com/${p}`, categoryId: UNSORTED_ID })
    if (r.kind !== 'saved') throw new Error('seed failed')
    ids.push(r.bookmark.id)
  }
  return ids
}

describe('bulk edits', () => {
  beforeEach(async () => {
    await resetDbForTests()
    await clearAllData()
    await initData()
  })

  it('pins only the selected bookmarks and marks them dirty', async () => {
    const [a, b] = await seed('a', 'b')
    await setPinnedMany([a], true)
    const rows = useData.getState().bookmarks
    expect(rows.find((x) => x.id === a)!.isPinned).toBe(true)
    expect(rows.find((x) => x.id === b)!.isPinned).toBe(false)
    expect(rows.find((x) => x.id === a)!.updatedAt).toBeGreaterThan(rows.find((x) => x.id === a)!.syncedUpdatedAt ?? 0)
  })

  it('moves the selection to a category', async () => {
    const [a, b] = await seed('a', 'b')
    const cat = await createCategory('Work')
    if (!cat.ok) throw new Error()
    await moveMany([a, b], cat.category.id)
    expect(useData.getState().bookmarks.every((x) => x.categoryId === cat.category.id)).toBe(true)
  })

  it('adds tags to each bookmark without losing its own', async () => {
    const [a, b] = await seed('a', 'b')
    await addTagsMany([a], ['x'])
    await addTagsMany([a, b], ['Y', 'x'])
    const rows = useData.getState().bookmarks
    expect(rows.find((r) => r.id === a)!.tags).toEqual(['x', 'y'])
    expect(rows.find((r) => r.id === b)!.tags).toEqual(['y', 'x'])
  })

  it('trashes the selection and undo restores exactly those', async () => {
    const [a, b, c] = await seed('a', 'b', 'c')
    const { count, undo } = await trashMany([a, b])
    expect(count).toBe(2)
    expect(useData.getState().bookmarks.map((x) => x.id)).toEqual([c])
    await undo()
    expect(useData.getState().bookmarks).toHaveLength(3)
    expect(useData.getState().trashed).toHaveLength(0)
  })
})
