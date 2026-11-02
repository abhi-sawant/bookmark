import { describe, expect, it } from 'vitest'
import { UNSORTED_ID } from './models'
import { looksLikeBookmarksHtml, parseBookmarksHtml } from './bookmarksHtml'

const SAMPLE = `<!DOCTYPE NETSCAPE-Bookmark-file-1>
<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
<TITLE>Bookmarks</TITLE>
<H1>Bookmarks</H1>
<DL><p>
    <DT><H3 ADD_DATE="1700000000" PERSONAL_TOOLBAR_FOLDER="true">Bookmarks bar</H3>
    <DL><p>
        <DT><A HREF="https://example.com/top?utm_source=x" ADD_DATE="1700000100">Top level</A>
        <DT><H3 ADD_DATE="1700000200">Recipes</H3>
        <DL><p>
            <DT><A HREF="https://example.com/pasta" ADD_DATE="1700000300">Pasta</A>
            <DT><A HREF="https://example.com/pasta/" ADD_DATE="1700000301">Pasta again</A>
            <DT><H3>Desserts &amp; sweets</H3>
            <DL><p>
                <DT><A HREF="https://example.com/cake">Cake</A>
            </DL><p>
        </DL><p>
        <DT><A HREF="javascript:alert(1)">Bookmarklet</A>
        <DT><A HREF="ftp://example.com/file">FTP</A>
    </DL><p>
    <DT><H3>Other bookmarks</H3>
    <DL><p>
        <DT><A HREF="https://example.org/other">Other</A>
    </DL><p>
</DL><p>`

let n = 0
const opts = { now: 2_000_000_000_000, newId: () => `id${++n}` }

describe('parseBookmarksHtml', () => {
  const parsed = parseBookmarksHtml(SAMPLE, opts)

  it('recognises the format', () => {
    expect(looksLikeBookmarksHtml(SAMPLE)).toBe(true)
    expect(looksLikeBookmarksHtml('{"not": "html"}')).toBe(false)
  })

  it('keeps web links only, de-duplicated by normalised URL', () => {
    expect(parsed.bookmarks.map((b) => b.url).sort()).toEqual([
      'https://example.com/cake',
      'https://example.com/pasta',
      'https://example.com/top',
      'https://example.org/other',
    ])
  })

  it('turns folders into categories and root folders into Unsorted', () => {
    const names = parsed.categories.map((c) => c.name).sort()
    expect(names).toEqual(['Desserts & sweets', 'Recipes'])
    const byUrl = new Map(parsed.bookmarks.map((b) => [b.url, b]))
    expect(byUrl.get('https://example.com/top')!.categoryId).toBe(UNSORTED_ID)
    expect(byUrl.get('https://example.org/other')!.categoryId).toBe(UNSORTED_ID)
    const recipes = parsed.categories.find((c) => c.name === 'Recipes')!.id
    const desserts = parsed.categories.find((c) => c.name === 'Desserts & sweets')!.id
    expect(byUrl.get('https://example.com/pasta')!.categoryId).toBe(recipes)
    expect(byUrl.get('https://example.com/cake')!.categoryId).toBe(desserts)
  })

  it('uses ADD_DATE as the saved date and the link text as the title', () => {
    const pasta = parsed.bookmarks.find((b) => b.url === 'https://example.com/pasta')!
    expect(pasta.createdAt).toBe(1700000300 * 1000)
    expect(pasta.title).toBe('Pasta')
  })

  it('is marked as a browser import so it can only be merged', () => {
    expect(parsed.source).toBe('html')
  })
})
