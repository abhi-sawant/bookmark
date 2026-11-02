import { CATEGORY_NAME_MAX, BookmarkLimits, UNSORTED_ID } from './models'
import { isValid, host as urlHost, normalize } from '@/core/util/urlNormalizer'
import { CATEGORY_SWATCHES } from './swatches'
import type { BackupBookmark, BackupCategory, ParsedBackup } from './backup'

/** Folders browsers put everything under; links directly in one of these go to Unsorted. */
const ROOT_FOLDERS = new Set([
  'bookmarks bar',
  'bookmarks toolbar',
  'bookmarks menu',
  'other bookmarks',
  'mobile bookmarks',
  'favorites',
  'favorites bar',
  'bookmarks',
])

/** True when [text] looks like a browser's exported bookmarks file (the Netscape format). */
export const looksLikeBookmarksHtml = (text: string): boolean => /netscape-bookmark-file|<dt>\s*<a\s/i.test(text.slice(0, 4096))

function headingOf(dl: Element): Element | null {
  const prev = dl.previousElementSibling
  if (prev?.tagName === 'H3') return prev
  const parent = dl.parentElement
  if (parent?.tagName === 'DT') return parent.querySelector(':scope > h3')
  return null
}

/** The name of the nearest enclosing folder, or null for a root folder or no folder. */
function folderOf(a: Element): string | null {
  for (let dl = a.closest('dl'); dl; dl = dl.parentElement?.closest('dl') ?? null) {
    const name = headingOf(dl)?.textContent?.trim()
    if (name) return ROOT_FOLDERS.has(name.toLowerCase()) ? null : name.slice(0, CATEGORY_NAME_MAX)
  }
  return null
}

/**
 * Reads a browser bookmarks export (Chrome, Edge, Firefox, Safari all write the
 * Netscape format) into the same shape as a backup, so the existing preview and
 * merge import handle it. Each non-root folder becomes a category (matched by
 * name against existing ones on import); everything else lands in Unsorted.
 * Non-web links and repeats are dropped.
 */
export function parseBookmarksHtml(
  html: string,
  opts: { now?: number; newId?: () => string } = {},
): ParsedBackup {
  const now = opts.now ?? Date.now()
  const newId = opts.newId ?? (() => crypto.randomUUID())
  const doc = new DOMParser().parseFromString(html, 'text/html')

  const categories: BackupCategory[] = []
  const categoryByName = new Map<string, string>()
  const bookmarks: BackupBookmark[] = []
  const seen = new Set<string>()

  for (const a of Array.from(doc.querySelectorAll('a[href]'))) {
    const href = a.getAttribute('href')?.trim() ?? ''
    if (!/^https?:\/\//i.test(href) || !isValid(href)) continue
    const url = normalize(href)
    if (seen.has(url)) continue
    seen.add(url)

    let categoryId = UNSORTED_ID
    const folder = folderOf(a)
    if (folder) {
      const key = folder.toLowerCase()
      let id = categoryByName.get(key)
      if (!id) {
        id = newId()
        categoryByName.set(key, id)
        categories.push({
          id,
          name: folder,
          colorHex: CATEGORY_SWATCHES[categories.length % CATEGORY_SWATCHES.length],
          sortOrder: categories.length,
          isDefault: false,
          createdAt: now,
        })
      }
      categoryId = id
    }

    const addDate = Number(a.getAttribute('add_date'))
    const created = Number.isFinite(addDate) && addDate > 0 ? addDate * 1000 : now
    bookmarks.push({
      id: newId(),
      url,
      originalUrl: href.slice(0, BookmarkLimits.url),
      title: (a.textContent?.trim() || urlHost(url) || url).slice(0, BookmarkLimits.title),
      categoryId,
      createdAt: Math.min(created, now),
      updatedAt: now,
    })
  }
  return { bookmarks, categories, files: {}, source: 'html' }
}
