import { monogram, resolve } from './titleFallback'

describe('TitleFallback', () => {
  it('a fetched title wins outright', () => {
    expect(resolve({ fetchedTitle: 'The design of everyday APIs', sharedSubject: 'Something else', url: 'https://increment.com/apis/design' })).toBe('The design of everyday APIs')
  })
  it('falls back to the share subject', () => {
    expect(resolve({ sharedSubject: 'Shared page title', url: 'https://example.com/x' })).toBe('Shared page title')
  })
  it('ignores a subject that is just the url again', () => {
    const url = 'https://example.com/some-article'
    expect(resolve({ sharedSubject: url, url })).toBe('Some Article')
  })
  it('derives a title from the path, dropping a trailing id', () => {
    expect(resolve({ url: 'https://example.com/blog/how-to-build-an-app-1234' })).toBe('How To Build An App')
  })
  it('skips noise segments when walking the path backwards', () => {
    expect(resolve({ url: 'https://example.com/offline-first-compose/index.html' })).toBe('Offline First Compose')
  })
  it('falls back to a curated host name', () => {
    expect(resolve({ url: 'https://news.ycombinator.com' })).toBe('Hacker News')
  })
  it('falls back to the title-cased domain when the host is unknown', () => {
    expect(resolve({ url: 'https://kevincox.ca' })).toBe('Kevincox')
  })
  it('the chain always terminates in something non-empty', () => {
    expect(resolve({ url: 'https://a.io' }).length).toBeGreaterThan(0)
  })
  it('monogram takes initials from a curated name', () => {
    expect(monogram('https://news.ycombinator.com')).toBe('HN')
    expect(monogram('https://kevincox.ca').slice(0, 2)).toBe('KE')
  })
})
