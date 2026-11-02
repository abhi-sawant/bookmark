import { describe, expect, it } from 'vitest'
import { API_BASE } from '@/api/client'
import type { MetadataResponse } from '@/api/dto'
import { type Bookmark, ManualField } from './models'
import { buildMetadataPatch } from './enrich'

const base: Bookmark = {
  id: 'b1', url: 'https://example.com/a', originalUrl: 'https://example.com/a', title: 'example.com',
  description: null, siteName: 'Example', thumbnailUrl: null, thumbnailWidth: null, thumbnailHeight: null,
  accentColor: null, imageCandidates: [], categoryId: 'unsorted', manualFields: 0, isPinned: false,
  createdAt: 1, updatedAt: 1, syncedUpdatedAt: null,
}
const res: MetadataResponse = {
  title: 'A Page', description: 'About it', site_name: 'Example Site', image_url: 'https://example.com/i.jpg',
  final_url: 'https://example.com/a', thumbnail_url: `${API_BASE}/uploads/thumbnails/1/b1.webp`,
  thumbnail_width: 640, thumbnail_height: 360,
}

describe('buildMetadataPatch', () => {
  it('fills title, description, site name and thumbnail on a fresh bookmark', () => {
    expect(buildMetadataPatch(base, res)).toEqual({
      title: 'A Page', description: 'About it', siteName: 'Example Site',
      thumbnailUrl: res.thumbnail_url, thumbnailWidth: 640, thumbnailHeight: 360,
    })
  })
  it('never overwrites fields the user set', () => {
    const manual = { ...base, title: 'Mine', manualFields: ManualField.TITLE | ManualField.DESCRIPTION | ManualField.THUMBNAIL }
    expect(buildMetadataPatch(manual, res)).toEqual({ siteName: 'Example Site' })
  })
  it('ignores thumbnails that are not on our API host', () => {
    const patch = buildMetadataPatch(base, { ...res, thumbnail_url: 'https://evil.example/x.webp' })
    expect(patch).not.toHaveProperty('thumbnailUrl')
  })
  it('returns null when nothing changes', () => {
    const same = { ...base, title: 'A Page', description: 'About it', siteName: 'Example Site', thumbnailUrl: res.thumbnail_url }
    expect(buildMetadataPatch(same, res)).toBeNull()
  })
})
