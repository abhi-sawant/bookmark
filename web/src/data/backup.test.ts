import { resetDbForTests } from './db'
import { BackupFormatError, buildBackup, importBackup, parseBackup, previewImport } from './backup'
import { UNSORTED_ID } from './models'
import { clearAllData, createCategory, initData, saveBookmark, useData } from './repo'

beforeEach(async () => {
  await resetDbForTests()
  await initData()
})

describe('backup', () => {
  it('round-trips through the zip format', async () => {
    const cat = await createCategory('Reading')
    if (!cat.ok) throw new Error()
    await saveBookmark({ rawUrl: 'https://example.com/a', title: 'A', categoryId: cat.category.id })
    await saveBookmark({ rawUrl: 'https://example.com/b', categoryId: UNSORTED_ID })
    const { bookmarks, categories } = useData.getState()
    const zip = buildBackup(bookmarks, categories)

    await clearAllData()
    const parsed = parseBackup(zip)
    expect(previewImport(parsed)).toMatchObject({ total: 2, newCount: 2, alreadySaved: 0 })
    await importBackup(parsed, 'MERGE')
    expect(useData.getState().bookmarks.map((b) => b.url).sort()).toEqual(['https://example.com/a', 'https://example.com/b'])
    expect(useData.getState().categories.some((c) => c.name === 'Reading')).toBe(true)
    // Imported rows are dirty so they push on the next sync.
    expect(useData.getState().bookmarks.every((b) => b.syncedUpdatedAt === null)).toBe(true)
  })

  it('merge skips links that are already saved', async () => {
    await saveBookmark({ rawUrl: 'https://example.com/a', categoryId: UNSORTED_ID })
    const zip = buildBackup(useData.getState().bookmarks, useData.getState().categories)
    const parsed = parseBackup(zip)
    expect(previewImport(parsed).alreadySaved).toBe(1)
    await importBackup(parsed, 'MERGE')
    expect(useData.getState().bookmarks).toHaveLength(1)
  })

  it('rejects files that are not backups', () => {
    expect(() => parseBackup(new Uint8Array([1, 2, 3]))).toThrow(BackupFormatError)
  })
})
