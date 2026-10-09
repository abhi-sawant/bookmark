/**
 * URL normalisation. Port of android/.../core/util/UrlNormalizer.kt.
 *
 * The normalised form is what gets stored and what duplicates are detected on,
 * so it has to match Android byte for byte -- both apps push to the same
 * account. That is why this does NOT use `new URL()`: it normalises differently
 * (adds a trailing `/`, punycodes, percent-encodes). Instead [parseUri]
 * reproduces the parts of `java.net.URI` the Kotlin code relies on.
 */

const TRACKING_PARAMS = new Set([
  'fbclid', 'gclid', 'msclkid', 'igshid', 'mc_eid', 'mc_cid',
  'ref_src', 'ref_url', 'si', '_ga', 'yclid', 'dclid', 'twclid',
])
const TRACKING_PREFIXES = ['utm_']

/** Hosts that route on the fragment, where stripping `#...` loses the destination. */
const HASH_ROUTING_HOSTS = new Set([
  'groups.google.com',
  'mail.google.com',
  'drive.google.com',
  'docs.google.com',
  'web.archive.org',
])

const DEFAULT_PORTS: Record<string, number> = { http: 80, https: 443 }

const SCHEME_REGEX = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//

const TWO_PART_SUFFIXES = new Set([
  'co.uk', 'org.uk', 'ac.uk', 'gov.uk', 'me.uk', 'net.uk', 'sch.uk',
  'com.au', 'net.au', 'org.au', 'edu.au', 'gov.au',
  'co.jp', 'or.jp', 'ne.jp', 'ac.jp', 'go.jp',
  'co.nz', 'net.nz', 'org.nz', 'govt.nz',
  'co.in', 'net.in', 'org.in', 'gov.in', 'ac.in',
  'com.br', 'net.br', 'org.br', 'gov.br',
  'co.za', 'org.za', 'net.za',
  'com.cn', 'net.cn', 'org.cn', 'gov.cn',
  'com.sg', 'com.hk', 'com.tw', 'com.mx', 'com.ar', 'com.tr',
  'co.kr', 'or.kr', 'ne.kr',
  'github.io', 'gitlab.io', 'pages.dev', 'vercel.app', 'netlify.app',
])

// ---------------------------------------------------------------------------
// java.net.URI emulation (hierarchical `scheme://authority/path?query#fragment`)
// ---------------------------------------------------------------------------

interface ParsedUri {
  scheme: string
  /** null when the authority is absent or registry-based (java's `uri.host == null`). */
  host: string | null
  /** -1 when absent. */
  port: number
  rawPath: string
  rawQuery: string | null
  rawFragment: string | null
}

class UriSyntaxError extends Error {}

/** Characters java.net.URI rejects everywhere (besides spaces/controls). */
const ILLEGAL_ASCII = new Set(['"', '<', '>', '\\', '^', '`', '{', '|', '}'])

/** Checks [s] is made of legal URI characters and well-formed `%XX` escapes. */
function checkChars(s: string, extraIllegal = ''): void {
  for (let i = 0; i < s.length; i++) {
    const c = s[i]
    const code = s.charCodeAt(i)
    if (c === '%') {
      if (!/^[0-9a-fA-F]{2}$/.test(s.slice(i + 1, i + 3))) throw new UriSyntaxError()
      i += 2
    } else if (code <= 0x20 || code === 0x7f) {
      throw new UriSyntaxError()
    } else if (code < 0x80) {
      if (ILLEGAL_ASCII.has(c) || extraIllegal.includes(c)) throw new UriSyntaxError()
    } else if (code <= 0x9f || /\p{Z}/u.test(c)) {
      // "other" characters are legal unless they are controls or spaces.
      throw new UriSyntaxError()
    }
  }
}

const HOSTNAME_LABEL = /^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?$/
const IPV4 = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/

/** RFC 2396 hostname (final label must start with a letter) or IPv4; null if neither. */
function parseServerHost(h: string): string | null {
  if (h.startsWith('[') && h.endsWith(']')) {
    return /^\[[0-9a-fA-F:.]+\]$/.test(h) ? h : null
  }
  const ipv4 = IPV4.exec(h)
  if (ipv4) return ipv4.slice(1).every((g) => Number(g) <= 255) ? h : null
  const body = h.endsWith('.') ? h.slice(0, -1) : h
  if (body === '') return null
  const labels = body.split('.')
  if (!labels.every((l) => HOSTNAME_LABEL.test(l))) return null
  return /^[A-Za-z]/.test(labels[labels.length - 1]) ? h : null
}

function parseAuthority(authority: string): { host: string | null; port: number } {
  if (authority === '') return { host: null, port: -1 }
  // Server-based: [userinfo@]host[:port]
  let rest = authority
  const at = rest.lastIndexOf('@')
  let userinfoOk = true
  if (at >= 0) {
    userinfoOk = /^(?:[A-Za-z0-9\-_.!~*'();:&=+$,]|%[0-9a-fA-F]{2}|[^\x00-\x7f])*$/.test(rest.slice(0, at))
    rest = rest.slice(at + 1)
  }
  if (userinfoOk) {
    let hostPart = rest
    let portPart = ''
    let hasPort = false
    if (rest.startsWith('[')) {
      const close = rest.indexOf(']')
      if (close >= 0) {
        hostPart = rest.slice(0, close + 1)
        const tail = rest.slice(close + 1)
        if (tail === '') {
          // no port
        } else if (tail.startsWith(':')) {
          hasPort = true
          portPart = tail.slice(1)
        } else {
          hostPart = '' // malformed
        }
      }
    } else {
      const colon = rest.lastIndexOf(':')
      if (colon >= 0) {
        hostPart = rest.slice(0, colon)
        portPart = rest.slice(colon + 1)
        hasPort = true
      }
    }
    const host = parseServerHost(hostPart)
    if (host !== null && (!hasPort || /^\d*$/.test(portPart))) {
      const port = hasPort && portPart !== '' ? Number(portPart) : -1
      if (Number.isSafeInteger(port) && port <= 2147483647) return { host, port }
    }
  }
  // Registry-based authority: legal, but java reports no host.
  if (!/^(?:[A-Za-z0-9\-_.!~*'()$,;:@&=+]|%[0-9a-fA-F]{2}|[^\x00-\x7f])*$/.test(authority)) {
    throw new UriSyntaxError()
  }
  return { host: null, port: -1 }
}

function parseUri(input: string): ParsedUri {
  checkChars(input, '') // path-only illegal chars are re-checked per component below
  const m = /^([a-zA-Z][a-zA-Z0-9+.-]*):\/\//.exec(input)
  if (!m) throw new UriSyntaxError()
  const scheme = m[1]
  let rest = input.slice(m[0].length)

  let rawFragment: string | null = null
  const hash = rest.indexOf('#')
  if (hash >= 0) {
    rawFragment = rest.slice(hash + 1)
    rest = rest.slice(0, hash)
    if (rawFragment.includes('#')) throw new UriSyntaxError()
  }
  let rawQuery: string | null = null
  const q = rest.indexOf('?')
  if (q >= 0) {
    rawQuery = rest.slice(q + 1)
    rest = rest.slice(0, q)
  }
  const slash = rest.indexOf('/')
  const authority = slash >= 0 ? rest.slice(0, slash) : rest
  const rawPath = slash >= 0 ? rest.slice(slash) : ''

  // `[` and `]` are legal in a query/fragment but not in a path.
  if (/[[\]]/.test(rawPath)) throw new UriSyntaxError()

  const { host, port } = parseAuthority(authority)
  return { scheme, host, port, rawPath, rawQuery, rawFragment }
}

function tryParse(input: string): ParsedUri | null {
  try {
    return parseUri(input)
  } catch (e) {
    if (e instanceof UriSyntaxError) return null
    throw e
  }
}

const withScheme = (s: string): string => (SCHEME_REGEX.test(s) ? s : `https://${s}`)

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

function stripTracking(rawQuery: string | null): string | null {
  if (rawQuery === null || rawQuery === '') return rawQuery
  const kept = rawQuery.split('&').filter((pair) => {
    if (pair === '') return false
    const eq = pair.indexOf('=')
    const key = (eq >= 0 ? pair.slice(0, eq) : pair).toLowerCase()
    return !TRACKING_PARAMS.has(key) && !TRACKING_PREFIXES.some((p) => key.startsWith(p))
  })
  const joined = kept.join('&')
  return joined === '' ? null : joined
}

/**
 * Returns the normalised URL, or the trimmed input unchanged if it cannot be
 * parsed. Never throws -- a bookmark is always saveable.
 */
export function normalize(input: string): string {
  const trimmed = input.trim()
  if (trimmed === '') return trimmed

  const uri = tryParse(withScheme(trimmed))
  if (!uri || uri.host === null) return trimmed

  const scheme = uri.scheme.toLowerCase()
  const host = uri.host.toLowerCase()
  const port = uri.port === -1 || DEFAULT_PORTS[scheme] === uri.port ? -1 : uri.port
  const query = stripTracking(uri.rawQuery)
  const path = uri.rawPath
  const fragment = HASH_ROUTING_HOSTS.has(host) ? uri.rawFragment : null
  const hasQuery = query !== null && query !== ''

  let normalizedPath: string
  if (path === '' || path === '/') {
    normalizedPath = hasQuery ? '/' : ''
  } else {
    const trimmedEnd = path.replace(/\/+$/, '')
    normalizedPath = trimmedEnd === '' ? path : trimmedEnd
  }

  let out = `${scheme}://${host}`
  if (port !== -1) out += `:${port}`
  out += normalizedPath
  if (hasQuery) out += `?${query}`
  if (fragment) out += `#${fragment}`
  return out
}

/** Syntactic validity only -- enough to enable Save. */
export function isValid(input: string): boolean {
  const trimmed = input.trim()
  if (trimmed === '') return false
  const uri = tryParse(withScheme(trimmed))
  if (!uri) return false
  const host = uri.host
  const scheme = uri.scheme.toLowerCase()
  return (
    (scheme === 'http' || scheme === 'https') &&
    !!host &&
    host.trim() !== '' &&
    host.includes('.') &&
    !host.startsWith('.') &&
    !host.endsWith('.')
  )
}

/** Host with any `www.` prefix removed, or null when the URL will not parse. */
export function host(url: string): string | null {
  const uri = tryParse(withScheme(url))
  if (!uri || uri.host === null) return null
  return uri.host.toLowerCase().replace(/^www\./, '')
}

/** `news.ycombinator.com` -> `ycombinator.com`; `www.bbc.co.uk` -> `bbc.co.uk`. */
export function registrableDomain(url: string): string | null {
  const h = host(url)
  if (h === null) return null
  const labels = h.split('.')
  if (labels.length <= 2) return h
  const lastTwo = labels.slice(-2).join('.')
  return TWO_PART_SUFFIXES.has(lastTwo) && labels.length >= 3 ? labels.slice(-3).join('.') : lastTwo
}
