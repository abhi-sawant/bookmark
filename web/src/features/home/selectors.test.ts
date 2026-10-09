import type { Bookmark, Category } from '@/data/models'
import { searchBookmarks, sortBookmarks } from './selectors'

const mk = (o: Partial<Bookmark>): Bookmark => ({
  id: o.id ?? 'x', url: 'https://example.com', originalUrl: 'https://example.com', title: 'T', description: null, siteName: null,
  thumbnailUrl: null, thumbnailWidth: null, thumbnailHeight: null, accentColor: null, imageCandidates: [], categoryId: 'unsorted',
  manualFields: 0, isPinned: false, createdAt: 1, updatedAt: 1, syncedUpdatedAt: null, ...o,
})
const cat = (id: string, sortOrder: number): Category => ({ id, name: id, colorHex: '#000000', iconKey: null, sortOrder, isDefault: false, createdAt: 1, updatedAt: 1, syncedUpdatedAt: null })

describe('sortBookmarks', () => {
  const list = [mk({ id: 'a', title: 'banana', createdAt: 1 }), mk({ id: 'b', title: 'Apple', createdAt: 3 }), mk({ id: 'c', title: 'cherry', createdAt: 2, isPinned: true })]
  it('pinned always float to the top', () => {
    expect(sortBookmarks(list, 'NEWEST', []).map((b) => b.id)).toEqual(['c', 'b', 'a'])
    expect(sortBookmarks(list, 'OLDEST', []).map((b) => b.id)).toEqual(['c', 'a', 'b'])
  })
  it('title sort is case-insensitive', () => {
    expect(sortBookmarks(list, 'TITLE_AZ', []).map((b) => b.id)).toEqual(['c', 'b', 'a'])
  })
  it('category sort uses category order then newest', () => {
    const l = [mk({ id: '1', categoryId: 'z', createdAt: 5 }), mk({ id: '2', categoryId: 'y', createdAt: 1 }), mk({ id: '3', categoryId: 'y', createdAt: 2 })]
    expect(sortBookmarks(l, 'CATEGORY', [cat('y', 0), cat('z', 1)]).map((b) => b.id)).toEqual(['3', '2', '1'])
  })
})

describe('searchBookmarks', () => {
  const list = [
    mk({ id: '1', title: 'HTML metadata guide', url: 'https://a.dev/x' }),
    mk({ id: '2', title: 'Room FTS4', description: 'metadata in practice', categoryId: 'dev' }),
    mk({ id: '3', title: 'Unrelated', siteName: 'Example' }),
  ]
  it('prefix matches every term (AND) across fields', () => {
    expect(searchBookmarks(list, 'meta guide', null).map((b) => b.id)).toEqual(['1'])
    expect(searchBookmarks(list, 'meta', null).map((b) => b.id).sort()).toEqual(['1', '2'])
  })
  it('does not match mid-word', () => {
    expect(searchBookmarks(list, 'data', null)).toEqual([])
  })
  it('filters by category', () => {
    expect(searchBookmarks(list, 'meta', 'dev').map((b) => b.id)).toEqual(['2'])
  })
  it('empty query returns nothing', () => {
    expect(searchBookmarks(list, '   ', null)).toEqual([])
  })
})
