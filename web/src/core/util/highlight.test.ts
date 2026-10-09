import { findHighlightRanges, queryTermsFor } from './highlight'

const r = (start: number, end: number) => ({ start, end })

describe('highlight', () => {
  it('single term highlights every matching word', () => {
    expect(findHighlightRanges('Everything about HTML metadata', ['metadata'])).toEqual([r(22, 29)])
  })
  it('matching is case-insensitive', () => {
    expect(findHighlightRanges('HTML Metadata Guide', ['html'])).toEqual([r(0, 3)])
  })
  it('prefix matches like FTS4 term*', () => {
    expect(findHighlightRanges('Everything about HTML metadata', ['meta'])).toEqual([r(22, 29)])
  })
  it('multiple terms all highlight', () => {
    expect(findHighlightRanges('Room FTS4 and metadata in practice', ['fts4', 'metadata'])).toEqual([r(5, 8), r(14, 21)])
  })
  it('no match / empty terms', () => {
    expect(findHighlightRanges('Nothing relevant here', ['zzz'])).toEqual([])
    expect(findHighlightRanges('Some title', [])).toEqual([])
  })
  it('does not highlight mid-word', () => {
    expect(findHighlightRanges('mathHTMLish is one word', ['html'])).toEqual([])
  })
  it('queryTermsFor strips quotes and splits on whitespace', () => {
    expect(queryTermsFor('"meta"  data')).toEqual(['meta', 'data'])
    expect(queryTermsFor('  html   ')).toEqual(['html'])
  })
})
