import { strFromU8, strToU8, unzipSync, zipSync } from 'fflate'
import { normalize } from '@/core/util/urlNormalizer'
import { getDb } from './db'
import { type Bookmark, type Category, type Tombstone, UNSORTED_ID } from './models'
import { internal, useData } from './repo'
import { normalizeCategoryHex } from './swatches'

/** Same camelCase zip as Android's backup, so a backup made on either opens on the other. */
export const BACKUP_FORMAT_VERSION = 1

interface BackupBookmark {
  id: string
  url: string
  originalUrl: string
  title: string
  description?: string | null
  siteName?: string | null
  thumbnailPath?: string | null
  accentColor?: number | null
  thumbnailWidth?: number | null
  thumbnailHeight?: number | null
  imageCandidates?: string[]
  categoryId: string
  metadataState?: string
  failureCause?: string | null
  fetchAttempts?: number
  lastFetchAt?: number | null
  manualFields?: number
  isPinned?: boolean
  createdAt: number
  updatedAt: number
}

interface BackupCategory {
  id: string
  name: string
  colorHex: string
  iconKey?: string | null
  sortOrder?: number
  isDefault?: boolean
  createdAt: number
}

export class BackupFormatError extends Error {}

export function buildBackup(bookmarks: Bookmark[], categories: Category[], now = Date.now()): Uint8Array {
  const b: BackupBookmark[] = bookmarks.map((x) => ({
    id: x.id,
    url: x.url,
    originalUrl: x.originalUrl,
    title: x.title,
    description: x.description,
    siteName: x.siteName,
    thumbnailPath: null, // the remote thumbnail lives on the server; Android re-downloads it on sync
    accentColor: x.accentColor,
    thumbnailWidth: x.thumbnailWidth,
    thumbnailHeight: x.thumbnailHeight,
    imageCandidates: x.imageCandidates,
    categoryId: x.categoryId,
    metadataState: x.thumbnailUrl ? 'SUCCESS' : 'PENDING',
    failureCause: null,
    fetchAttempts: 0,
    lastFetchAt: null,
    manualFields: x.manualFields,
    isPinned: x.isPinned,
    createdAt: x.createdAt,
    updatedAt: x.updatedAt,
  }))
  const c: BackupCategory[] = categories.map((x) => ({
    id: x.id,
    name: x.name,
    colorHex: x.colorHex,
    iconKey: x.iconKey,
    sortOrder: x.sortOrder,
    isDefault: x.isDefault,
    createdAt: x.createdAt,
  }))
  const manifest = { formatVersion: BACKUP_FORMAT_VERSION, exportedAt: now, bookmarkCount: b.length, categoryCount: c.length }
  return zipSync({
    'manifest.json': strToU8(JSON.stringify(manifest)),
    'bookmarks.json': strToU8(JSON.stringify(b)),
    'categories.json': strToU8(JSON.stringify(c)),
  })
}

export interface ParsedBackup {
  bookmarks: BackupBookmark[]
  categories: BackupCategory[]
  files: Record<string, Uint8Array>
}

export function parseBackup(bytes: Uint8Array): ParsedBackup {
  let files: Record<string, Uint8Array>
  try {
    files = unzipSync(bytes)
  } catch {
    throw new BackupFormatError("This doesn't look like a Bookmarks backup.")
  }
  if (!files['bookmarks.json'] || !files['categories.json']) throw new BackupFormatError("This doesn't look like a Bookmarks backup.")
  try {
    const bookmarks = JSON.parse(strFromU8(files['bookmarks.json'])) as BackupBookmark[]
    const categories = JSON.parse(strFromU8(files['categories.json'])) as BackupCategory[]
    if (!Array.isArray(bookmarks) || !Array.isArray(categories)) throw new Error()
    return { bookmarks, categories, files }
  } catch {
    throw new BackupFormatError("This doesn't look like a Bookmarks backup.")
  }
}

export interface ImportPreview {
  total: number
  newCount: number
  alreadySaved: number
  categories: number
}

export function previewImport(parsed: ParsedBackup, existing: Bookmark[] = useData.getState().bookmarks): ImportPreview {
  const have = new Set(existing.map((b) => normalize(b.url)))
  const alreadySaved = parsed.bookmarks.filter((b) => have.has(normalize(b.url))).length
  return { total: parsed.bookmarks.length, newCount: parsed.bookmarks.length - alreadySaved, alreadySaved, categories: parsed.categories.length }
}

function thumbnailBlob(parsed: ParsedBackup, path: string | null | undefined): Blob | null {
  if (!path) return null
  const bytes = parsed.files[`thumbnails/${path}`]
  // Only WebP within the server's 3 MB cap can be uploaded.
  if (!bytes || bytes.byteLength > 3 * 1024 * 1024 || bytes.byteLength < 12) return null
  const isWebp = strFromU8(bytes.subarray(0, 4)) === 'RIFF' && strFromU8(bytes.subarray(8, 12)) === 'WEBP'
  return isWebp ? new Blob([bytes.slice().buffer], { type: 'image/webp' }) : null
}

export async function importBackup(parsed: ParsedBackup, mode: 'MERGE' | 'REPLACE'): Promise<void> {
  const db = await getDb()
  const now = Date.now()
  const state = useData.getState()
  const tombstones: Tombstone[] = []

  if (mode === 'REPLACE') {
    // Tell the server about everything the replace removes, so it does not come back on the next sync.
    const fileBookmarkIds = new Set(parsed.bookmarks.map((b) => b.id))
    const fileCategoryIds = new Set(parsed.categories.map((c) => c.id))
    for (const b of state.bookmarks) if (!fileBookmarkIds.has(b.id)) tombstones.push({ id: b.id, kind: 'bookmark', deletedAt: now })
    for (const c of state.categories) if (c.id !== UNSORTED_ID && !fileCategoryIds.has(c.id)) tombstones.push({ id: c.id, kind: 'category', deletedAt: now })
    await internal.removeBookmarkRows(state.bookmarks.map((b) => b.id))
    for (const c of state.categories) if (c.id !== UNSORTED_ID) await internal.removeCategoryRow(c.id)
  }

  // Categories: keep ids; a different id with the same name maps onto the existing category.
  const remap = new Map<string, string>()
  const current = () => useData.getState().categories
  for (const c of parsed.categories) {
    const byId = current().find((x) => x.id === c.id)
    if (byId) {
      if (mode === 'REPLACE') await internal.putCategory({ ...byId, name: c.name, colorHex: normalizeCategoryHex(c.colorHex), iconKey: c.iconKey ?? null, isDefault: !!c.isDefault, sortOrder: c.sortOrder ?? byId.sortOrder, updatedAt: now, syncedUpdatedAt: null })
      continue
    }
    const byName = current().find((x) => x.name.toLowerCase() === c.name.toLowerCase())
    if (byName) {
      remap.set(c.id, byName.id)
      continue
    }
    await internal.putCategory({
      id: c.id,
      name: c.name,
      colorHex: normalizeCategoryHex(c.colorHex),
      iconKey: c.iconKey ?? null,
      sortOrder: c.sortOrder ?? Math.max(-1, ...current().map((x) => x.sortOrder)) + 1,
      isDefault: false,
      createdAt: c.createdAt,
      updatedAt: now,
      syncedUpdatedAt: null,
    })
  }

  const have = new Set(useData.getState().bookmarks.map((b) => normalize(b.url)))
  const known = new Set(current().map((c) => c.id))
  for (const b of parsed.bookmarks) {
    const url = normalize(b.url)
    if (have.has(url)) continue
    have.add(url)
    const categoryId = remap.get(b.categoryId) ?? b.categoryId
    await internal.putBookmark({
      id: b.id,
      url,
      originalUrl: b.originalUrl || b.url,
      title: b.title,
      description: b.description ?? null,
      siteName: b.siteName ?? null,
      thumbnailUrl: null,
      thumbnailWidth: b.thumbnailWidth ?? null,
      thumbnailHeight: b.thumbnailHeight ?? null,
      accentColor: b.accentColor ?? null,
      imageCandidates: b.imageCandidates ?? [],
      categoryId: known.has(categoryId) ? categoryId : UNSORTED_ID,
      manualFields: b.manualFields ?? 0,
      isPinned: !!b.isPinned,
      createdAt: b.createdAt,
      updatedAt: b.updatedAt,
      syncedUpdatedAt: null, // dirty: pushes on the next sync
      pendingThumbnail: thumbnailBlob(parsed, b.thumbnailPath),
    })
  }

  if (tombstones.length) {
    const tx = db.transaction('tombstones', 'readwrite')
    await Promise.all([...tombstones.map((t) => tx.store.put(t)), tx.done])
  }
  internal.notifyChanged()
}

export function backupFileName(d = new Date()): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `bookmarks-${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}.zip`
}

export function downloadBlob(bytes: Uint8Array, name: string): void {
  const url = URL.createObjectURL(new Blob([bytes.slice().buffer], { type: 'application/zip' }))
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
