import type { Bookmark, Category } from '@/data/models'
import { NO_FILTERS, addRecentSearch, searchBookmarks, sortBookmarks } from './selectors'

const mk = (o: Partial<Bookmark>): Bookmark => ({
  id: o.id ?? 'x', url: 'https://example.com', originalUrl: 'https://example.com', title: 'T', description: null, siteName: null,
  thumbnailUrl: null, thumbnailWidth: null, thumbnailHeight: null, accentColor: null, imageCandidates: [], tags: [], isUnread: false, categoryId: 'unsorted',
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

describe('tag search', () => {
  const list = [mk({ id: 'a', tags: ['kotlin', 'read later'] }), mk({ id: 'b', tags: ['kotlinx'], title: 'Other' }), mk({ id: 'c', title: 'plain' })]
  it('matches tags as search terms', () => {
    expect(searchBookmarks(list, 'kotlin', null).map((b) => b.id).sort()).toEqual(['a', 'b'])
  })
  it('#tag matches that exact tag only', () => {
    expect(searchBookmarks(list, '#kotlin', null).map((b) => b.id)).toEqual(['a'])
    expect(searchBookmarks(list, '#read later', null).map((b) => b.id)).toEqual(['a'])
    expect(searchBookmarks(list, '#', null)).toEqual([])
  })
})

describe('search filters', () => {
  const now = 100 * 24 * 60 * 60 * 1000
  const list = [
    mk({ id: 'pinned', isPinned: true, categoryId: 'work', createdAt: now }),
    mk({ id: 'unsorted', categoryId: 'unsorted', createdAt: now }),
    mk({ id: 'old', categoryId: 'work', createdAt: now - 8 * 24 * 60 * 60 * 1000 }),
  ]
  it('each chip narrows on its own, with no text', () => {
    expect(searchBookmarks(list, '', null, { ...NO_FILTERS, pinned: true }, now).map((b) => b.id)).toEqual(['pinned'])
    expect(searchBookmarks(list, '', null, { ...NO_FILTERS, unsorted: true }, now).map((b) => b.id)).toEqual(['unsorted'])
    expect(searchBookmarks(list, '', null, { ...NO_FILTERS, thisWeek: true }, now).map((b) => b.id).sort()).toEqual(['pinned', 'unsorted'])
  })
  it('the Unread chip keeps only unopened bookmarks', () => {
    const l = [mk({ id: 'u', isUnread: true }), mk({ id: 'r' })]
    expect(searchBookmarks(l, '', null, { ...NO_FILTERS, unread: true }).map((b) => b.id)).toEqual(['u'])
  })
  it('chips combine and an empty query with no chips finds nothing', () => {
    expect(searchBookmarks(list, '', null, { pinned: true, unsorted: true, thisWeek: false, unread: false }, now)).toEqual([])
    expect(searchBookmarks(list, '', null, NO_FILTERS, now)).toEqual([])
  })
  it('chips also narrow text results', () => {
    const texty = [mk({ id: 'a', title: 'react tips', isPinned: true }), mk({ id: 'b', title: 'react news' })]
    expect(searchBookmarks(texty, 'react', null, { ...NO_FILTERS, pinned: true }).map((b) => b.id)).toEqual(['a'])
  })
})

describe('recent searches', () => {
  it('keeps newest first, de-duplicated and capped at 8', () => {
    let r: string[] = []
    for (const q of ['a', 'b', 'A ']) r = addRecentSearch(r, q)
    expect(r).toEqual(['A', 'b'])
    for (let i = 1; i <= 20; i++) r = addRecentSearch(r, `q${i}`)
    expect(r).toHaveLength(8)
    expect(r[0]).toBe('q20')
    expect(addRecentSearch(r, '   ')).toBe(r)
  })
})
