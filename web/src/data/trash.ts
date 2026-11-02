/** How long a bookmark stays in Trash before it is removed for good. Mirrors Android's TrashPolicy. */
export const TRASH_RETENTION_DAYS = 30
const DAY_MS = 24 * 60 * 60 * 1000

/** Bookmarks trashed before this instant are due for permanent deletion. */
export const trashCutoff = (now: number): number => now - TRASH_RETENTION_DAYS * DAY_MS

/** Whole days left before [trashedAt] expires, never negative. */
export function trashDaysLeft(trashedAt: number, now: number): number {
  const remaining = trashedAt + TRASH_RETENTION_DAYS * DAY_MS - now
  return remaining <= 0 ? 0 : Math.ceil(remaining / DAY_MS)
}
