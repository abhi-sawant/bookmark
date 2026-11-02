import { API_BASE } from '@/api/client'
import type { MetadataResponse } from '@/api/dto'
import { MetadataApi } from '@/api/endpoints'
import { useSession } from '@/account/session'
import { usePrefs } from '@/app/prefs'
import { type Bookmark, BookmarkLimits, ManualField } from './models'
import { applyFetchedMetadata, useData } from './repo'

/**
 * What a server preview changes on [b]. Fields the user typed or picked
 * (ManualField) are never overwritten. Null when nothing would change.
 */
export function buildMetadataPatch(b: Bookmark, res: MetadataResponse): Partial<Bookmark> | null {
  const patch: Partial<Bookmark> = {}
  if (!(b.manualFields & ManualField.TITLE) && res.title) {
    const title = res.title.slice(0, BookmarkLimits.title)
    if (title !== b.title) patch.title = title
  }
  if (!(b.manualFields & ManualField.DESCRIPTION) && res.description) {
    const description = res.description.slice(0, BookmarkLimits.description)
    if (description !== b.description) patch.description = description
  }
  if (res.site_name) {
    const siteName = res.site_name.slice(0, BookmarkLimits.siteName)
    if (siteName !== b.siteName) patch.siteName = siteName
  }
  if (
    !(b.manualFields & ManualField.THUMBNAIL) &&
    !b.thumbnailUrl &&
    !b.pendingThumbnail &&
    res.thumbnail_url &&
    res.thumbnail_url.startsWith(`${API_BASE}/`)
  ) {
    patch.thumbnailUrl = res.thumbnail_url
    patch.thumbnailWidth = res.thumbnail_width
    patch.thumbnailHeight = res.thumbnail_height
  }
  return Object.keys(patch).length > 0 ? patch : null
}

/**
 * Best-effort preview for a bookmark added on the web: signed in and enabled in
 * Settings, otherwise a no-op. Any failure is silent -- the generated tile stays.
 */
export async function enrichBookmark(id: string): Promise<void> {
  if (!useSession.getState().token || !usePrefs.getState().serverPreviews) return
  const b = useData.getState().bookmarks.find((x) => x.id === id)
  if (!b) return
  try {
    const res = await MetadataApi.fetch(b.url, b.id)
    // Re-read: the user may have edited the row while the request ran.
    const latest = useData.getState().bookmarks.find((x) => x.id === id)
    if (!latest) return
    const patch = buildMetadataPatch(latest, res)
    if (patch) await applyFetchedMetadata(id, patch)
  } catch {
    // offline, rate limited, blocked site...: keep the tile
  }
}
