import { extract } from './urlExtractor'

describe('UrlExtractor', () => {
  it('takes the first url out of chatty shared text', () => {
    const r = extract('Check this out https://example.com/x')
    expect(r.primaryUrl).toBe('https://example.com/x')
    expect(r.otherUrls).toEqual([])
  })
  it('handles the YouTube shape of title plus link plus promo', () => {
    const r = extract('Rust on Android: what changed\nhttps://youtu.be/abc123\n\nWatch more at https://youtube.com')
    expect(r.primaryUrl).toBe('https://youtu.be/abc123')
    expect(r.otherUrls).toEqual(['https://youtube.com'])
  })
  it('exposes the remaining links rather than dropping them', () => {
    const r = extract('one https://a.com two https://b.com three https://c.com')
    expect(r.primaryUrl).toBe('https://a.com')
    expect(r.otherUrls).toEqual(['https://b.com', 'https://c.com'])
  })
  it('keeps the raw text when there is no url', () => {
    const r = extract('just some words here')
    expect(r.primaryUrl).toBeNull()
    expect(r.rawText).toBe('just some words here')
  })
  it('holds the subject as a title candidate', () => {
    expect(extract('https://example.com/x', 'The design of everyday APIs').subjectTitle).toBe('The design of everyday APIs')
  })
  it('drops a subject that is just the url', () => {
    expect(extract('https://example.com/x', 'https://example.com/x').subjectTitle).toBeNull()
  })
  it('trims sentence punctuation off the end of a url', () => {
    expect(extract('See https://example.com/x.').primaryUrl).toBe('https://example.com/x')
    expect(extract('(https://example.com)').primaryUrl).toBe('https://example.com')
  })
  it('does not mistake version numbers for hosts', () => {
    expect(extract('upgraded to v2.14 today').primaryUrl).toBeNull()
    expect(extract('pi is roughly 3.14159').primaryUrl).toBeNull()
  })
  it('finds a bare domain with no scheme', () => {
    expect(extract('go to example.com/page').primaryUrl).toBe('example.com/page')
  })
  it('deduplicates a link repeated in the same share', () => {
    const r = extract('https://a.com and again https://a.com')
    expect(r.primaryUrl).toBe('https://a.com')
    expect(r.otherUrls).toEqual([])
  })
})
