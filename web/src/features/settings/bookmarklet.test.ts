import { describe, expect, it } from 'vitest'
import { buildBookmarklet } from './bookmarklet'

describe('buildBookmarklet', () => {
  it('opens the add route of the given origin with url and title', () => {
    const js = buildBookmarklet('https://bookmark.example.com/')
    expect(js.startsWith('javascript:')).toBe(true)
    expect(js).toContain('"https://bookmark.example.com/add"')
    expect(js).toContain('encodeURIComponent(location.href)')
    expect(js).toContain('encodeURIComponent(document.title)')
  })
  it('contains no raw spaces that a bookmarks bar could mangle', () => {
    expect(buildBookmarklet('http://localhost:5173')).not.toMatch(/\s/)
  })
})
