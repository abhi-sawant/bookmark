// Wire formats of the sync API (snake_case, epoch-millisecond timestamps).

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
