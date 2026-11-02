import { LinkCheckApi } from '@/api/endpoints'
import { useSession } from '@/account/session'
import { type Bookmark, type LinkStatus } from './models'
import { patchBookmarkLocal, useData } from './repo'

/** The server checks at most this many links per request. */
export const LINK_CHECK_BATCH = 10

export function chunk<T>(items: T[], size: number): T[][] {
  const out: T[][] = []
  for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size))
  return out
}

export interface LinkCheckSummary {
  checked: number
  broken: number
  unreachable: number
}

export function summarizeLinkChecks(list: Bookmark[]): LinkCheckSummary {
  let checked = 0
  let broken = 0
  let unreachable = 0
  for (const b of list) {
    if (!b.linkStatus) continue
    checked++
    if (b.linkStatus === 'broken') broken++
    else if (b.linkStatus === 'error') unreachable++
  }
  return { checked, broken, unreachable }
}

/**
 * Checks every live bookmark through the sync server, 10 at a time, and stores
 * each result on the row (device-local; it does not sync). Needs a signed-in
 * session. Returns false if it stopped early (offline, rate limited, ...).
 */
export async function checkLinks(onProgress?: (done: number, total: number) => void): Promise<boolean> {
  if (!useSession.getState().token) return false
  const targets = useData.getState().bookmarks.map((b) => ({ id: b.id, url: b.url }))
  let done = 0
  onProgress?.(0, targets.length)
  for (const batch of chunk(targets, LINK_CHECK_BATCH)) {
    try {
      const { results } = await LinkCheckApi.check(batch.map((t) => t.url))
      for (let i = 0; i < batch.length; i++) {
        const r = results[i]
        if (r) await patchBookmarkLocal(batch[i].id, { linkStatus: r.status as LinkStatus, linkCheckedAt: Date.now() })
      }
    } catch {
      return false
    }
    done += batch.length
    onProgress?.(done, targets.length)
  }
  return true
}
