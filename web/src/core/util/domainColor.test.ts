import { PALETTE_SIZE, indexFor } from './domainColor'

describe('DomainColor', () => {
  it('the same domain always gets the same swatch', () => {
    expect(indexFor('https://example.com/a')).toBe(indexFor('https://example.com/completely/different/path'))
  })
  it('subdomains collapse onto the registrable domain', () => {
    expect(indexFor('https://ycombinator.com')).toBe(indexFor('https://news.ycombinator.com/item?id=1'))
  })
  it('always lands inside the palette', () => {
    for (const h of ['https://a.com', 'https://b.org', 'https://ogp.me', 'https://medium.com', 'https://developer.android.com', 'https://seriouseats.com', 'https://are.na', 'not a url', '']) {
      const i = indexFor(h)
      expect(i).toBeGreaterThanOrEqual(0)
      expect(i).toBeLessThan(PALETTE_SIZE)
    }
  })
  it('spreads across the palette rather than clustering', () => {
    const used = new Set(Array.from({ length: 200 }, (_, i) => indexFor(`https://site${i + 1}.example`)))
    expect(used.size).toBe(PALETTE_SIZE)
  })
})
