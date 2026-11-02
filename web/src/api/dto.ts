// Wire formats of the sync API (snake_case, epoch-millisecond timestamps).

/** POST metadata.php: what the server read from the page. Every field can be null. */
export interface MetadataResponse {
  title: string | null
  description: string | null
  site_name: string | null
  image_url: string | null
  final_url: string
  /** Set only when a bookmark_id was sent and the server stored a thumbnail for it. */
  thumbnail_url: string | null
  thumbnail_width: number | null
  thumbnail_height: number | null
}

export interface CategoryDto {
  id: string
  name: string
  color_hex: string
  icon_key: string | null
  is_default: boolean
  created_at: number
  updated_at: number
}

export interface BookmarkDto {
  id: string
  url: string
  original_url: string
  title: string
  description: string | null
  site_name: string | null
  thumbnail_url: string | null
  thumbnail_width: number | null
  thumbnail_height: number | null
  accent_color: number | null
  image_candidates: string[]
  /** Absent when talking to a server that predates tags. */
  tags?: string[]
  category_id: string
  manual_fields: number
  is_pinned: boolean
  created_at: number
  updated_at: number
}

export interface DeleteDto {
  id: string
  deleted_at: number
}

export interface PullResponse {
  has_more: boolean
  next_since: number
  categories: { upserts: CategoryDto[]; deletes: DeleteDto[] }
  bookmarks: { upserts: BookmarkDto[]; deletes: DeleteDto[] }
}

export interface PushRequest {
  categories: CategoryDto[]
  bookmarks: BookmarkDto[]
  deleted_category_ids: DeleteDto[]
  deleted_bookmark_ids: DeleteDto[]
}

export interface Rejection {
  id: string | null
  reason: string
}

export interface PushResponse {
  categories: { applied: string[]; rejected: Rejection[] }
  bookmarks: { applied: string[]; rejected: Rejection[] }
}

export interface AuthResponse {
  user_id: number
  device_id: number
  token: string
}

export interface DeviceDto {
  device_id: number
  device_name: string
  created_at: number
  last_used_at: number | null
  is_current: boolean
}
