import { displayName } from './knownHosts'
import { host as urlHost, registrableDomain } from './urlNormalizer'

/**
 * The title fallback chain. Port of android/.../core/util/TitleFallback.kt.
 * The last step is the raw URL, so the chain always terminates and a saved
 * bookmark always has a title.
 */

const EXTENSIONS = new Set(['html', 'htm', 'php', 'asp', 'aspx', 'jsp', 'md', 'txt', 'pdf', 'amp'])

const NOISE_SEGMENTS = new Set([
  'index', 'default', 'home', 'post', 'posts', 'article', 'articles',
  'blog', 'p', 's', 'a', 'en', 'amp', 'view', 'watch', 'story',
  'item', 'items', 'comments', 'status', 'video', 'photo', 'thread',
])

const TRAILING_ID = /[-_][0-9a-f]{4,}$/i
const SCHEME = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//

/** Title-case the first character only, like Kotlin's replaceFirstChar. */
function capitalizeFirst(word: string): string {
  if (word === '') return word
  const [first] = Array.from(word)
  return /\p{Ll}/u.test(first) ? first.toUpperCase() + word.slice(first.length) : word
}

/** java.net.URLDecoder.decode: '+' is a space; a malformed escape throws. */
function urlDecode(raw: string): string {
  const withSpaces = raw.replace(/\+/g, ' ')
  if (/%(?![0-9a-fA-F]{2})/.test(withSpaces)) throw new Error('malformed escape')
  const bytes: number[] = []
  const out: string[] = []
  const flush = () => {
    if (bytes.length) {
      out.push(new TextDecoder('utf-8').decode(new Uint8Array(bytes)))
      bytes.length = 0
    }
  }
  for (let i = 0; i < withSpaces.length; i++) {
    if (withSpaces[i] === '%') {
      bytes.push(parseInt(withSpaces.slice(i + 1, i + 3), 16))
      i += 2
    } else {
      flush()
      out.push(withSpaces[i])
    }
  }
  flush()
  return out.join('')
}

function rawPathOf(url: string): string | null {
  const s = SCHEME.test(url) ? url : `https://${url}`
  // Only the path matters here, so a lightweight split is enough; callers that
  // need strict validation go through UrlNormalizer.
  const afterScheme = s.replace(SCHEME, '')
  const noFrag = afterScheme.split('#')[0].split('?')[0]
  const slash = noFrag.indexOf('/')
  if (/[\s<>"\\^`{|}]/.test(s)) return null
  return slash >= 0 ? noFrag.slice(slash) : ''
}

/** `example.com/blog/how-to-build-an-app-1234` -> "How To Build An App". */
export function fromPath(url: string): string | null {
  const path = rawPathOf(url)
  if (path === null) return null

  const segments = path.split('/').filter((s) => s.trim() !== '')
  let words: string[] | null = null
  for (const raw of segments.reverse()) {
    let decoded: string
    try {
      decoded = urlDecode(raw)
    } catch {
      decoded = raw
    }
    const dot = decoded.lastIndexOf('.')
    const ext = dot >= 0 ? decoded.slice(dot + 1).toLowerCase() : ''
    const withoutExtension = EXTENSIONS.has(ext) ? decoded.slice(0, dot) : decoded
    const withoutId = withoutExtension.replace(TRAILING_ID, '')
    const candidate = withoutId
      .split(/[-_+]/)
      .filter((w) => w.trim() !== '')
      .filter((w) => !/^\p{Nd}+$/u.test(w))
    if (candidate.length === 0) continue
    if (candidate.length === 1 && NOISE_SEGMENTS.has(candidate[0].toLowerCase())) continue
    if (candidate.length === 1 && candidate[0].length <= 2) continue
    words = candidate
    break
  }
  if (!words) return null
  const title = words.map(capitalizeFirst).join(' ')
  return title.trim() === '' ? null : title
}

/** `news.ycombinator.com` -> "Hacker News", `example.com` -> "Example". */
export function fromDomain(url: string): string | null {
  const h = urlHost(url)
  if (h === null) return null
  const known = displayName(h)
  if (known) return known
  const registrable = registrableDomain(url) ?? h
  const name = registrable.split('.')[0]
  return name.trim() === '' ? null : capitalizeFirst(name)
}

export interface ResolveArgs {
  fetchedTitle?: string | null
  sharedSubject?: string | null
  url: string
}

export function resolve({ fetchedTitle, sharedSubject, url }: ResolveArgs): string {
  const fetched = fetchedTitle?.trim()
  if (fetched) return fetched

  const subject = sharedSubject?.trim()
  if (subject && subject !== url && subject.toLowerCase() !== url.trim().toLowerCase()) return subject

  return fromPath(url) ?? fromDomain(url) ?? url
}

/** The one or two letters shown on a generated monogram tile. */
export function monogram(url: string): string {
  const h = urlHost(url)
  const source = displayName(h) ?? registrableDomain(url)?.split('.')[0] ?? h
  if (!source) return '?'
  const words = source.split(/[ \-.]/).filter((w) => w.trim() !== '')
  const first = (w: string) => Array.from(w)[0]
  if (words.length >= 2) return (first(words[0]) + first(words[1])).toUpperCase()
  if (words.length === 1 && words[0].length >= 2) return Array.from(words[0]).slice(0, 2).join('').toUpperCase()
  if (words.length === 1) return first(words[0]).toUpperCase()
  return '?'
}
