import type { Bookmark } from '@/data/models'

/**
 * "Surprise me": a random bookmark from the older half of the collection, so
 * the button resurfaces things you saved a while ago rather than yesterday's
 * link. Mirrors android/.../bookmarks/home/Surprise.kt.
 */
export function pickSurprise(list: Bookmark[], random: () => number = Math.random): Bookmark | null {
  if (list.length === 0) return null
  const oldestFirst = [...list].sort((a, b) => a.createdAt - b.createdAt)
  const pool = oldestFirst.slice(0, Math.max(1, Math.ceil(oldestFirst.length / 2)))
  return pool[Math.min(pool.length - 1, Math.floor(random() * pool.length))]
}
