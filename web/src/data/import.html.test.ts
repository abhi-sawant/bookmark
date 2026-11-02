import { resetDbForTests } from '@/data/db'
import { BackupFormatError, importBackup, parseBackup, previewImport } from '@/data/backup'
import { clearAllData, initData, saveBookmark, useData } from '@/data/repo'
import { UNSORTED_ID } from '@/data/models'

const HTML = `<!DOCTYPE NETSCAPE-Bookmark-file-1>
<DL><p>
  <DT><H3>Reading</H3>
  <DL><p>
    <DT><A HREF="https://example.com/a">A</A>
    <DT><A HREF="https://example.com/b">B</A>
  </DL><p>
  <DT><A HREF="https://example.com/c">C</A>
</DL><p>`

const bytes = (s: string) => new TextEncoder().encode(s)

describe('importing a browser bookmarks file', () => {
  beforeEach(async () => {
    await resetDbForTests()
    await clearAllData()
    await initData()
  })

  it('parseBackup accepts the HTML export and rejects one with no web links', () => {
    expect(parseBackup(bytes(HTML)).source).toBe('html')
    expect(() => parseBackup(bytes('<!DOCTYPE NETSCAPE-Bookmark-file-1><DL><p></DL>'))).toThrow(BackupFormatError)
  })

  it('merges new links, creating the folder category and skipping links already saved', async () => {
    await saveBookmark({ rawUrl: 'https://example.com/a', categoryId: UNSORTED_ID })
    const parsed = parseBackup(bytes(HTML))
    expect(previewImport(parsed)).toMatchObject({ total: 3, newCount: 2, alreadySaved: 1, categories: 1 })

    await importBackup(parsed, 'MERGE')
    const state = useData.getState()
    expect(state.bookmarks.map((b) => b.url).sort()).toEqual(['https://example.com/a', 'https://example.com/b', 'https://example.com/c'])
    const reading = state.categories.find((c) => c.name === 'Reading')!
    expect(state.bookmarks.find((b) => b.url === 'https://example.com/b')!.categoryId).toBe(reading.id)
    expect(state.bookmarks.find((b) => b.url === 'https://example.com/c')!.categoryId).toBe(UNSORTED_ID)
  })
})
