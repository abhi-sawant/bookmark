/** Fallback category every account has. It can never be deleted. */
export const UNSORTED_ID = 'unsorted'

/** Bit flags in Bookmark.manualFields: a set bit locks that field against auto-overwrite. */
export const ManualField = { TITLE: 1, DESCRIPTION: 2, THUMBNAIL: 4 } as const

export const BookmarkLimits = { title: 200, description: 500, siteName: 60, url: 2048 } as const
export const CATEGORY_NAME_MAX = 40

export interface Bookmark {
  id: string
  /** Normalised URL (UrlNormalizer.normalize). Duplicates are detected on it. */
  url: string
  originalUrl: string
  title: string
  description: string | null
  siteName: string | null
  /** Absolute URL on the API host, as returned by the thumbnail upload endpoint. */
  thumbnailUrl: string | null
  thumbnailWidth: number | null
  thumbnailHeight: number | null
  /** Signed 32-bit ARGB, as stored by Android. */
  accentColor: number | null
  imageCandidates: string[]
  categoryId: string
  manualFields: number
  isPinned: boolean
  createdAt: number
  updatedAt: number
  /** updatedAt value last confirmed by the server, or null if never pushed. */
  syncedUpdatedAt: number | null
  /** A thumbnail picked on this device that has not been uploaded yet. */
  pendingThumbnail?: Blob | null
}

export interface Category {
  id: string
  name: string
  colorHex: string
  iconKey: string | null
  /** Device-local ordering; never synced. */
  sortOrder: number
  isDefault: boolean
  createdAt: number
  updatedAt: number
  syncedUpdatedAt: number | null
}

export interface Tombstone {
  id: string
  kind: 'bookmark' | 'category'
  deletedAt: number
}

/** A thumbnail picked on this device: WebP bytes plus what the grid needs to lay it out. */
export interface ThumbnailChoice {
  blob: Blob
  width: number
  height: number
  /** Average colour as a signed 32-bit ARGB int (Android's accentColor). */
  accentColor: number
}

export type SortOrder = 'NEWEST' | 'OLDEST' | 'TITLE_AZ' | 'CATEGORY'
export type ViewMode = 'GRID' | 'LIST'
export type ThemeMode = 'SYSTEM' | 'LIGHT' | 'DARK'

/** A row is dirty when it has never been pushed or changed since. */
export const isDirty = (r: { updatedAt: number; syncedUpdatedAt: number | null }): boolean =>
  r.syncedUpdatedAt === null || r.updatedAt > r.syncedUpdatedAt

export function newId(): string {
  return crypto.randomUUID()
}
