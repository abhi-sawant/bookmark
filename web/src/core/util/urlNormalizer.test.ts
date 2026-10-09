import { host, isValid, normalize, registrableDomain } from './urlNormalizer'

describe('UrlNormalizer', () => {
  it('adds https when no scheme is present', () => {
    expect(normalize('example.com')).toBe('https://example.com')
  })
  it('lowercases scheme and host but leaves the path alone', () => {
    expect(normalize('HTTPS://EXAMPLE.COM/Some/Path')).toBe('https://example.com/Some/Path')
  })
  it('strips default ports only', () => {
    expect(normalize('https://example.com:443')).toBe('https://example.com')
    expect(normalize('http://example.com:80')).toBe('http://example.com')
    expect(normalize('https://example.com:8443')).toBe('https://example.com:8443')
  })
  it('strips the fragment', () => {
    expect(normalize('https://example.com/post#section-2')).toBe('https://example.com/post')
  })
  it('keeps the fragment on hosts that route on it', () => {
    expect(normalize('https://groups.google.com/forum#!topic/abc')).toBe('https://groups.google.com/forum#!topic/abc')
  })
  it('strips tracking parameters', () => {
    expect(normalize('https://example.com/a?utm_source=x&utm_medium=y&fbclid=z&gclid=q')).toBe('https://example.com/a')
  })
  it('preserves parameters that carry the content', () => {
    expect(normalize('https://youtube.com/watch?v=dQw4w9WgXcQ&si=trackingtoken')).toBe('https://youtube.com/watch?v=dQw4w9WgXcQ')
    expect(normalize('https://blog.example.com/?p=1234&utm_campaign=spring')).toBe('https://blog.example.com/?p=1234')
  })
  it('strips a trailing slash only on a bare host', () => {
    expect(normalize('https://example.com/')).toBe('https://example.com')
    expect(normalize('https://example.com/blog/')).toBe('https://example.com/blog')
  })
  it('never throws on input it cannot parse', () => {
    expect(normalize('not a url at all')).toBe('not a url at all')
    expect(normalize('   ')).toBe('')
  })
  it('matches java.net.URI quirks the Kotlin code depends on', () => {
    expect(normalize('https://exa_mple.com')).toBe('https://exa_mple.com') // registry authority: no host
    expect(normalize('https://example.com/a b')).toBe('https://example.com/a b') // illegal space
    expect(normalize('https://user:pw@Example.com/x')).toBe('https://example.com/x') // userinfo dropped
    expect(normalize('https://example.com?q=1')).toBe('https://example.com/?q=1')
    expect(normalize('https://example.com:abc')).toBe('https://example.com:abc')
  })
  it('validity is syntactic only', () => {
    expect(isValid('example.com')).toBe(true)
    expect(isValid('https://example.com/a/b?c=d')).toBe(true)
    expect(isValid('hello world')).toBe(false)
    expect(isValid('')).toBe(false)
    expect(isValid('ftp://example.com')).toBe(false)
  })
  it('registrable domain handles multi-part suffixes', () => {
    expect(registrableDomain('https://news.ycombinator.com')).toBe('ycombinator.com')
    expect(registrableDomain('https://www.bbc.co.uk/news')).toBe('bbc.co.uk')
    expect(registrableDomain('https://example.com')).toBe('example.com')
  })
  it('host drops the www prefix', () => {
    expect(host('https://www.example.com/x')).toBe('example.com')
  })
})
